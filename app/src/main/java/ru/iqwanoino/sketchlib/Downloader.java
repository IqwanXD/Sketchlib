package ru.iqwanoino.sketchlib;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Ambil library dari repositori Maven memakai koordinat seperti di Gradle
 * (group:artifact:versi). Repositori dicoba berurutan: Google Maven, Maven Central, JitPack.
 * AAR diekstrak jadi folder di local_libs, JAR disalin apa adanya. Dengan mode transitive,
 * dependensi compile/runtime dari POM ikut diunduh (BFS, satu versi per artefak: yang paling dekat).
 * Callback di main thread.
 */
final class Downloader {

    /** Status dan progres pemasangan. */
    interface Listener {
        void onStatus(String text);
        /** done dan total dalam byte. total <= 0 bila ukuran belum diketahui. */
        void onProgress(long done, long total);
        void onSuccess(String name);
        /** reason = CANCELLED bila dibatalkan pengguna. */
        void onFailure(String reason);
    }

    static final String FORMAT_HINT =
            "Format: group:artifact:versi, contoh com.github.bumptech.glide:glide:4.16.0";
    static final String CANCELLED = "Dibatalkan";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Batas kirim progres saat ukuran tidak diketahui, supaya tidak membanjiri main thread. */
    private static final long STEP_BYTES = 128 * 1024;
    private static final int MAX_PARENT_DEPTH = 6;

    private static final Pattern PART = Pattern.compile("[A-Za-z0-9_.\\-]+");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_.+\\-]+");
    /** Awalan opsional seperti di Gradle: implementation 'g:a:v' atau implementation("g:a:v"). */
    private static final Pattern PREFIX =
            Pattern.compile("^(implementation|api|compileOnly|compile)(\\s+|\\s*\\(\\s*)");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

    private static final class Repo {
        final String name;
        final String base;

        Repo(String name, String base) {
            this.name = name;
            this.base = base;
        }
    }

    /** Urutan pencarian. Yang pertama menang bila versi ada di lebih dari satu repositori. */
    private static final Repo[] REPOS = {
            new Repo("Google Maven", "https://dl.google.com/dl/android/maven2/"),
            new Repo("Maven Central", "https://repo1.maven.org/maven2/"),
            new Repo("JitPack", "https://jitpack.io/"),
    };

    /** Koordinat library yang sudah diurai. */
    static final class Coord {
        final String group;
        final String artifact;
        final String version;

        private Coord(String group, String artifact, String version) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
        }

        String key() {
            return group + ":" + artifact;
        }

        /** Awalan berkas tanpa ekstensi, contoh base/com/x/glide/4.16.0/glide-4.16.0. */
        private String path(Repo r) {
            return r.base + group.replace('.', '/') + "/" + artifact + "/" + version + "/"
                    + artifact + "-" + version;
        }
    }

    /** Satu pemasangan yang sedang berjalan. Bisa dibatalkan dari UI. */
    static final class Job {
        private volatile boolean cancelled;

        void cancel() {
            cancelled = true;
        }

        boolean isCancelled() {
            return cancelled;
        }
    }

    /** Hasil pencarian satu koordinat: repositori, jenis artefak (aar/jar/null), dan teks POM. */
    private static final class Found {
        Repo repo;
        String ext;
        String pom;
    }

    /** Data POM yang sudah digabung dengan parent-nya. */
    private static final class Pom {
        final Map<String, String> props = new HashMap<>();
        final Map<String, String> managed = new HashMap<>();
        /** Setiap entri: group, artifact, version, scope, optional. */
        final List<String[]> deps = new ArrayList<>();
    }

    private Downloader() { }

    /** Urai teks dari kolom input. Mengembalikan null bila format tidak valid. */
    static Coord parse(String input) {
        if (input == null) return null;
        String s = PREFIX.matcher(input.trim()).replaceFirst("");
        // Buang tanda kutip dan kurung dari gaya Gradle. Spasi di dalam tetap ditolak oleh PART/VERSION.
        s = s.replaceAll("[\"'()]", "").trim();
        String[] p = s.split(":", -1);
        if (p.length != 3) return null;
        if (!valid(p[0], p[1], p[2])) return null;
        return new Coord(p[0], p[1], p[2]);
    }

    private static boolean valid(String g, String a, String v) {
        return PART.matcher(g).matches() && PART.matcher(a).matches() && VERSION.matcher(v).matches();
    }

    private static void ui(Runnable r) {
        MAIN.post(r);
    }

    /**
     * Pasang library dari koordinat ke local_libs. Dengan transitive = true, dependensi juga
     * dipasang. Batalkan lewat job. Callback di main thread.
     */
    static void start(Context ctx, final String input, final int minSdk, final boolean transitive,
                      final Job job, final Listener l) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            File tmp = new File(app.getCacheDir(), "download.part");
            try {
                final Coord root = parse(input);
                if (root == null) throw new IOException(FORMAT_HINT);

                List<Coord> queue = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                queue.add(root);
                seen.add(root.key());

                String rootFolder = null;
                int installed = 0;
                while (!queue.isEmpty()) {
                    checkCancel(job);
                    final Coord c = queue.remove(0);
                    final boolean isRoot = c == root;
                    ui(() -> l.onStatus("Mencari " + c.artifact + "-" + c.version + "…"));

                    Found f = locate(c, transitive);
                    if (f == null) {
                        if (isRoot) {
                            throw new IOException("tidak ditemukan di Google Maven, Maven Central, atau JitPack");
                        }
                        continue; // dependensi yang tidak ada di repo dilewati
                    }

                    if (transitive && f.pom != null) {
                        Pom pom = loadPom(c.group, c.artifact, c.version, f.pom, f.repo, 0);
                        for (Coord d : dependenciesOf(pom)) {
                            if (seen.add(d.key())) queue.add(d);
                        }
                    }

                    if (f.ext == null) {
                        if (isRoot) throw new IOException("library ini hanya POM, tidak ada AAR/JAR");
                        continue;
                    }

                    String folder = install(c, f, tmp, job, l, minSdk);
                    installed++;
                    if (isRoot) rootFolder = folder;
                }

                final String summary = rootFolder
                        + (installed > 1 ? " + " + (installed - 1) + " dependensi" : "");
                ui(() -> l.onSuccess(summary));
            } catch (Exception e) {
                tmp.delete();
                final String msg = job.isCancelled() ? CANCELLED
                        : e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                ui(() -> l.onFailure(msg));
            }
        }).start();
    }

    /** Unduh satu artefak lalu pasang ke local_libs. Mengembalikan nama folder hasil. */
    private static String install(Coord c, Found f, File tmp, Job job, Listener l, int minSdk)
            throws IOException {
        final String url = c.path(f.repo) + "." + f.ext;
        final String repoName = f.repo.name;
        final String folder = c.artifact + "-" + c.version;
        final String label = folder;
        ui(() -> l.onStatus("Mengunduh " + label + " dari " + repoName + "…"));
        download(url, tmp, job, l);
        checkCancel(job);

        File dir = LibraryStore.dir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("tidak bisa membuat folder local_libs");
        }
        if (f.ext.equals("aar")) {
            File target = new File(dir, folder);
            LibraryStore.deleteRecursive(target);
            if (!target.mkdirs() && !target.isDirectory()) {
                throw new IOException("tidak bisa membuat folder library");
            }
            unzip(tmp, target);
            writeInfo(target, c, repoName, minSdk);
        } else {
            copy(tmp, new File(dir, folder + ".jar"));
        }
        tmp.delete();
        return folder;
    }

    private static void checkCancel(Job job) throws IOException {
        if (job.isCancelled()) throw new IOException(CANCELLED);
    }

    /**
     * Cari koordinat di tiap repositori. Repositori pertama yang punya POM atau artefak dipakai.
     * POM hanya diambil bila wantPom (mode transitive).
     */
    private static Found locate(Coord c, boolean wantPom) {
        for (Repo r : REPOS) {
            String base = c.path(r);
            String pom = wantPom ? fetchOrNull(base + ".pom") : null;
            String ext = exists(base + ".aar") ? "aar" : exists(base + ".jar") ? "jar" : null;
            if (pom != null || ext != null) {
                Found f = new Found();
                f.repo = r;
                f.ext = ext;
                f.pom = pom;
                return f;
            }
        }
        return null;
    }

    /** Berkas ada bila balasan 2xx. HEAD dicoba dulu; bila server menolaknya, pakai GET. */
    private static boolean exists(String url) {
        HttpURLConnection c = null;
        try {
            c = open(url, "HEAD");
            int code = c.getResponseCode();
            if (code == 405 || code == 501) {
                c.disconnect();
                c = open(url, "GET");
                code = c.getResponseCode();
            }
            return code / 100 == 2;
        } catch (IOException e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Sketchlib");
        return c;
    }

    private static String fetchOrNull(String url) {
        try {
            return fetchText(url);
        } catch (IOException e) {
            return null;
        }
    }

    private static String fetchText(String url) throws IOException {
        HttpURLConnection c = open(url, "GET");
        try {
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    /** POM sebuah parent, dicari di repositori yang sama dulu, lalu di semua repositori. */
    private static String fetchPom(String g, String a, String v, Repo prefer) {
        String rel = g.replace('.', '/') + "/" + a + "/" + v + "/" + a + "-" + v + ".pom";
        if (prefer != null) {
            String txt = fetchOrNull(prefer.base + rel);
            if (txt != null) return txt;
        }
        for (Repo r : REPOS) {
            String txt = fetchOrNull(r.base + rel);
            if (txt != null) return txt;
        }
        return null;
    }

    // ----- POM -----

    /** Baca POM beserta parent-nya (rekursif). Properti dan dependencyManagement digabung. */
    private static Pom loadPom(String g, String a, String v, String xml, Repo repo, int depth) {
        Pom out = new Pom();
        if (xml == null || depth > MAX_PARENT_DEPTH) return out;

        Element root;
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            root = doc.getDocumentElement();
        } catch (Exception e) {
            return out;
        }

        Element parent = child(root, "parent");
        if (parent != null) {
            String pg = text(parent, "groupId");
            String pa = text(parent, "artifactId");
            String pv = text(parent, "version");
            if (pg != null && pa != null && pv != null) {
                Pom pp = loadPom(pg, pa, pv, fetchPom(pg, pa, pv, repo), repo, depth + 1);
                out.props.putAll(pp.props);
                out.managed.putAll(pp.managed);
                out.deps.addAll(pp.deps);
            }
        }

        // Properti bawaan dan milik POM ini menimpa yang dari parent.
        out.props.put("project.groupId", g);
        out.props.put("project.artifactId", a);
        out.props.put("project.version", v);
        out.props.put("pom.version", v);
        Element props = child(root, "properties");
        if (props != null) {
            for (Element e : elements(props)) out.props.put(e.getNodeName(), e.getTextContent().trim());
        }

        Element dm = child(root, "dependencyManagement");
        if (dm != null && child(dm, "dependencies") != null) {
            for (Element d : elements(child(dm, "dependencies"))) {
                String key = text(d, "groupId") + ":" + text(d, "artifactId");
                String ver = text(d, "version");
                if (ver != null && !"import".equals(text(d, "scope"))) out.managed.put(key, ver);
            }
        }

        Element deps = child(root, "dependencies");
        if (deps != null) {
            for (Element d : elements(deps)) {
                out.deps.add(new String[]{
                        text(d, "groupId"), text(d, "artifactId"), text(d, "version"),
                        text(d, "scope"), text(d, "optional")});
            }
        }
        return out;
    }

    /** Dependensi compile/runtime yang tidak optional, dengan properti dan versi sudah diisi. */
    private static List<Coord> dependenciesOf(Pom pom) {
        List<Coord> out = new ArrayList<>();
        for (String[] d : pom.deps) {
            String g = d[0], a = d[1], v = d[2], scope = d[3], optional = d[4];
            if (g == null || a == null) continue;
            if ("true".equalsIgnoreCase(optional)) continue;
            if (scope != null && !scope.isEmpty()
                    && !"compile".equals(scope) && !"runtime".equals(scope)) {
                continue; // test, provided, system, import
            }
            g = subst(g, pom.props);
            a = subst(a, pom.props);
            if (v == null || v.isEmpty()) v = pom.managed.get(g + ":" + a);
            if (v == null) continue;
            v = subst(v, pom.props);
            if (valid(g, a, v)) out.add(new Coord(g, a, v));
        }
        return out;
    }

    /** Ganti ${nama} dengan nilai properti. Placeholder yang tidak dikenal dibiarkan. */
    private static String subst(String s, Map<String, String> props) {
        String out = s;
        for (int i = 0; i < 4 && out.contains("${"); i++) {
            Matcher m = PLACEHOLDER.matcher(out);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String val = props.get(m.group(1));
                m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : m.group(0)));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return out;
    }

    private static Element child(Element e, String name) {
        if (e == null) return null;
        for (Element c : elements(e)) {
            if (name.equals(c.getNodeName())) return c;
        }
        return null;
    }

    private static String text(Element e, String name) {
        Element c = child(e, name);
        if (c == null) return null;
        String t = c.getTextContent().trim();
        return t.isEmpty() ? null : t;
    }

    private static List<Element> elements(Element e) {
        List<Element> out = new ArrayList<>();
        if (e == null) return out;
        NodeList nl = e.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) out.add((Element) n);
        }
        return out;
    }

    // ----- unduh & simpan -----

    private static void download(String url, File dst, Job job, final Listener l) throws IOException {
        HttpURLConnection conn = open(url, "GET");
        try {
            int code = conn.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code);

            final long total = conn.getContentLength();
            long done = 0;
            long lastSent = -STEP_BYTES;
            int lastPct = -2;
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    checkCancel(job);
                    out.write(buf, 0, n);
                    done += n;
                    int pct = total > 0 ? (int) (done * 100 / total) : -1;
                    if (pct != lastPct || done - lastSent >= STEP_BYTES) {
                        lastPct = pct;
                        lastSent = done;
                        final long d = done;
                        ui(() -> l.onProgress(d, total));
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    /** Simpan data library (lib.json untuk daftar) dan asal unduhan (sketchlib.json). */
    private static void writeInfo(File dir, Coord c, String repo, int minSdk) throws IOException {
        try {
            JSONObject meta = new JSONObject();
            meta.put("name", c.artifact);
            meta.put("version", c.version);
            meta.put("description", c.group + " · " + repo);
            writeJson(new File(dir, "lib.json"), meta);

            JSONObject info = new JSONObject();
            info.put("source", c.group + ":" + c.artifact + ":" + c.version);
            info.put("repo", repo);
            info.put("min_sdk", minSdk);
            writeJson(new File(dir, "sketchlib.json"), info);
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    private static void writeJson(File f, JSONObject o) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(o.toString().getBytes("UTF-8"));
        }
    }

    private static void unzip(File zip, File dest) throws IOException {
        String base = dest.getCanonicalPath() + File.separator;
        byte[] buf = new byte[8192];
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                File f = new File(dest, e.getName());
                // Cegah zip-slip: entri tidak boleh keluar dari folder tujuan.
                if (!f.getCanonicalPath().startsWith(base)) {
                    throw new IOException("path di dalam aar tidak aman: " + e.getName());
                }
                if (e.isDirectory()) {
                    f.mkdirs();
                    continue;
                }
                File parent = f.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    int n;
                    while ((n = zin.read(buf)) != -1) out.write(buf, 0, n);
                }
            }
        }
    }

    private static void copy(File src, File dst) throws IOException {
        byte[] buf = new byte[8192];
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }
}
