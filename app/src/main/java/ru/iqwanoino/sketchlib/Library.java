package ru.iqwanoino.sketchlib;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Locale;

import org.json.JSONObject;

/**
 * Satu local library yang ada di folder local_libs. Bisa berupa folder (berisi
 * lib.json opsional) atau file tunggal seperti .jar.
 */
final class Library {

    final File file;
    final String name;
    final String version;
    final String description;
    final boolean isDir;
    final long sizeBytes;
    /** Minimum API yang dipilih saat download (0 = tidak diketahui). */
    final int minSdk;

    private Library(File file, String name, String version, String description,
                    boolean isDir, long sizeBytes, int minSdk) {
        this.file = file;
        this.name = name;
        this.version = version;
        this.description = description;
        this.isDir = isDir;
        this.sizeBytes = sizeBytes;
        this.minSdk = minSdk;
    }

    static Library from(File f) {
        boolean isDir = f.isDirectory();
        String name = f.getName();
        String version = "";
        String description = "";
        int minSdk = 0;
        if (isDir) {
            File info = new File(f, "sketchlib.json");
            if (info.isFile()) {
                try {
                    minSdk = new JSONObject(readAll(info)).optInt("min_sdk", 0);
                } catch (Exception ignored) {
                    // file info rusak: abaikan
                }
            }
            File meta = new File(f, "lib.json");
            if (meta.isFile()) {
                try {
                    JSONObject o = new JSONObject(readAll(meta));
                    name = o.optString("name", name);
                    version = o.optString("version", "");
                    description = o.optString("description", "");
                } catch (Exception ignored) {
                    // lib.json rusak: pakai nama folder saja
                }
            }
        } else {
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
        }
        return new Library(f, name, version, description, isDir, sizeOf(f), minSdk);
    }

    /** q harus sudah huruf kecil. */
    boolean matches(String q) {
        return name.toLowerCase(Locale.ROOT).contains(q)
                || version.toLowerCase(Locale.ROOT).contains(q)
                || description.toLowerCase(Locale.ROOT).contains(q);
    }

    String typeLabel() {
        if (isDir) return "folder";
        return file.getName().toLowerCase(Locale.ROOT).endsWith(".jar") ? "jar" : "file";
    }

    long sizeKb() {
        return Math.max(1, sizeBytes / 1024);
    }

    private static String readAll(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static long sizeOf(File f) {
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) total += sizeOf(k);
        return total;
    }
}
