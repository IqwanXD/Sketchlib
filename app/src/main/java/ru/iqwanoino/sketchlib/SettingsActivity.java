package ru.iqwanoino.sketchlib;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Pengaturan Sketchlib: tampilan (tema, dynamic color, warna aksen), informasi, dan tautan. */
public class SettingsActivity extends Activity {

    private static final String URL_DISCORD = "https://discord.gg/u7QPpQD7QC";
    private static final String URL_GITHUB = "https://github.com/IqwanXD/";
    private static final String URL_TELEGRAM = "https://t.me/IqwanoINO/";
    private static final String KEY_SCROLL = "scrollY";

    private interface Toggle { void onToggle(boolean on); }
    private interface Fmt { String format(int v); }
    private interface IntSink { void set(int v); }

    private static final float END_SCALE = 18f / 34f;   // judul 34sp mengecil jadi 18sp

    private FrameLayout root, header;
    private LinearLayout col;
    private ScrollView scroll;
    private int savedScroll;

    // judul collapse (gaya JetEditor): berubah mengikuti posisi scroll
    private int expandedH, collapsedH, maxScroll, lastY;
    private TextView title;
    private ImageView backBtn;
    private FrameLayout.LayoutParams backLp;

    // baris "Warna aksen" dan garis pemisahnya: disembunyikan langsung saat dynamic color aktif
    private View accentRowView, accentDividerView;

    private final Runnable rebuildRunnable = new Runnable() {
        @Override public void run() {
            if (isFinishing()) return;
            Theme.load(SettingsActivity.this);
            liveRebuild();
        }
    };

    /** Bangun ulang tampilan dengan tema baru tanpa memuat ulang activity. */
    private void liveRebuild() {
        if (scroll != null) savedScroll = scroll.getScrollY();
        Ui.crossfade(this, () -> {
            Ui.setupEdge(this);
            build();
        }, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        Prefs.init(this);
        Theme.load(this);
        savedScroll = savedInstanceState == null ? 0 : savedInstanceState.getInt(KEY_SCROLL, 0);
        Ui.setupEdge(this);
        build();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_SCROLL, scroll.getScrollY());
    }

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        // Konten selalu cukup panjang untuk di-scroll sejauh maxScroll, supaya judul bisa collapse
        scroll.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int min = (bt - t) + maxScroll;
            if (col.getMinimumHeight() != min) col.setMinimumHeight(min);
        });
        scroll.getViewTreeObserver().addOnScrollChangedListener(() -> collapse(scroll.getScrollY()));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        header = buildHeader();
        root.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        populate();
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            Ui.readInsets(ins);
            applyInsets();
            return ins;
        });
        setContentView(root);
        applyInsets();
        scroll.post(() -> scroll.scrollTo(0, savedScroll));
    }

    /** Tinggi header dan posisi judul bergantung pada status bar, jadi dihitung ulang di sini. */
    private void applyInsets() {
        expandedH = Ui.insetTop + Ui.dp(176);
        collapsedH = Ui.insetTop + Ui.dp(56);
        maxScroll = expandedH - collapsedH;
        if (backLp != null) {
            backLp.topMargin = Ui.insetTop + (Ui.dp(56) - Ui.dp(48)) / 2;
            backBtn.setLayoutParams(backLp);
        }
        col.setPadding(Ui.dp(16), expandedH, Ui.dp(16), Ui.insetBottom + Ui.dp(24));
        collapse(lastY);
    }

    private FrameLayout buildHeader() {
        FrameLayout h = new FrameLayout(this);
        h.setBackgroundColor(Theme.BG);

        backBtn = Ui.iconButton(this, R.drawable.ic_back, Theme.ON, 46, this::finish);
        backLp = new FrameLayout.LayoutParams(Ui.dp(46), Ui.dp(46), Gravity.TOP | Gravity.START);
        backLp.leftMargin = Ui.dp(8);
        h.addView(backBtn, backLp);

        title = Ui.text(this, "Pengaturan", 34, Theme.ON, false);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tl.leftMargin = Ui.dp(16);
        h.addView(title, tl);

        // Font baru terpasang setelah layout pertama, tinggi judul bisa berubah: hitung ulang posisinya.
        h.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> collapse(lastY));
        return h;
    }

    /** Dipanggil setiap scroll. scrollY = jarak konten yang sudah di-scroll. */
    private void collapse(int scrollY) {
        if (header == null || title == null || maxScroll <= 0) return;
        lastY = scrollY;
        float p = Math.max(0f, Math.min(1f, scrollY / (float) maxScroll));

        int h = Math.max(collapsedH, expandedH - scrollY);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) header.getLayoutParams();
        if (lp.height != h) {
            lp.height = h;
            header.setLayoutParams(lp);
        }

        float th = title.getHeight();
        if (th == 0) {
            title.measure(0, 0);
            th = title.getMeasuredHeight();
        }
        float s = 1f + (END_SCALE - 1f) * p;
        float topExpanded = expandedH - Ui.dp(16) - th;                          // di bawah header
        float topCollapsed = Ui.insetTop + (Ui.dp(56) - th * END_SCALE) / 2f;   // sejajar tombol back

        title.setPivotX(0f);
        title.setPivotY(0f);
        title.setScaleX(s);
        title.setScaleY(s);
        title.setTranslationX(Ui.dp(48) * p);                                    // 16dp -> 64dp
        title.setTranslationY(topExpanded + (topCollapsed - topExpanded) * p);
    }

    // ------------------------------------------------------------ isi

    private void populate() {
        col.removeAllViews();

        addBlock(section("Tampilan"));
        final LinearLayout accent = accentRow();
        LinearLayout displayCard = card(
                switchRow("Tema gelap", "Gelap", "Terang", Theme.dark, true, null, on -> {
                    Theme.setDark(this, on);
                    scheduleRebuild();
                }),
                switchRow("Warna dinamis", "Mengikuti warna wallpaper", "Warna aksen pilihan",
                        Prefs.dynamicColor(), Build.VERSION.SDK_INT >= 31, "Butuh Android 12 ke atas", on -> {
                            Prefs.setDynamicColor(on);
                            setAccentVisible(!on);
                            scheduleRebuild();
                        }),
                accent);
        addBlock(displayCard);
        accentRowView = accent;
        int idx = displayCard.indexOfChild(accent);
        accentDividerView = idx > 0 ? displayCard.getChildAt(idx - 1) : null;
        setAccentVisible(!(Prefs.dynamicColor() && Build.VERSION.SDK_INT >= 31));

        addBlock(section("Informasi"));
        addBlock(card(
                infoRow(R.drawable.ic_info, "Versi", versionName()),
                infoRow(R.drawable.ic_save, "Lokasi library", "/sdcard/.sketchware/libs/local_libs")));

        addBlock(section("Tautan terkait"));
        addBlock(card(
                actionRow(R.drawable.ic_discord, "Discord", "Ngobrol bareng komunitas", v -> openUrl(URL_DISCORD)),
                actionRow(R.drawable.ic_code, "GitHub", "Kode sumber dan rilis", v -> openUrl(URL_GITHUB)),
                actionRow(R.drawable.ic_telegram, "Telegram", "Kabar dan pembaruan", v -> openUrl(URL_TELEGRAM))));
    }

    /** Bangun ulang langsung di frame berikutnya (tanpa jeda). Beruntun cepat tetap digabung. */
    private void scheduleRebuild() {
        root.removeCallbacks(rebuildRunnable);
        root.post(rebuildRunnable);
    }

    /** Tampilkan atau sembunyikan baris warna aksen beserta garis pemisahnya (tanpa animasi). */
    private void setAccentVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        if (accentRowView != null) accentRowView.setVisibility(vis);
        if (accentDividerView != null) accentDividerView.setVisibility(vis);
    }

    private void addBlock(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(8);
        col.addView(v, lp);
    }

    private TextView section(String label) {
        TextView t = M3.text(this, label, 14, M3.primary, true);
        t.setPadding(Ui.dp(8), Ui.dp(20), Ui.dp(8), Ui.dp(4));
        return t;
    }

    /** Kartu berisi baris-baris, dipisahkan garis tipis. */
    private LinearLayout card(View... rows) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(M3.shape(M3.surfaceContainerHigh, 20));
        c.setClipToOutline(true);
        for (int i = 0; i < rows.length; i++) {
            c.addView(rows[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i < rows.length - 1) c.addView(divider(), dividerParams());
        }
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(M3.alpha(M3.outline, 0.4f));
        return v;
    }

    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(1));
        lp.setMargins(Ui.dp(16), 0, Ui.dp(16), 0);
        return lp;
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(M3.rippleColor()), null,
                new ColorDrawable(Color.WHITE)));
        row.setClickable(true);
        return row;
    }

    /** Baris dengan MaterialSwitch. Saklar hanya tampilan; barisnya yang menangani ketukan. */
    private LinearLayout switchRow(String label, final String onText, final String offText, boolean on,
                                   boolean enabled, String disabledText, final Toggle toggle) {
        final LinearLayout row = baseRow();
        final TextView sub = M3.text(this, "", 13, M3.onSurfaceVariant, false);
        final MaterialSwitch sw = new MaterialSwitch(this);
        sw.setEnabled(false);
        sw.setFocusable(false);
        sw.setActiveColor(M3.primary);
        sw.setInactiveColor(Color.TRANSPARENT);
        sw.setStrokeActiveColor(M3.primary);
        sw.setStrokeInactiveColor(M3.outline);
        sw.setThumbActiveColor(M3.onPrimary);
        sw.setThumbInactiveColor(M3.outline);
        sw.setCheckedImmediate(on);
        sub.setText(!enabled ? disabledText : (on ? onText : offText));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(M3.text(this, label, 16, M3.onSurface, false));
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(sw, new LinearLayout.LayoutParams(-2, -2));

        if (enabled) {
            row.setOnClickListener(v -> {
                boolean next = !sw.isChecked();
                sw.setChecked(next);
                sub.setText(next ? onText : offText);
                toggle.onToggle(next);
            });
        } else {
            row.setClickable(false);
            row.setBackground(null);
            row.setAlpha(0.38f);
        }
        return row;
    }

    /** Baris "Warna aksen": ketuk untuk membuka pemilih warna. */
    private LinearLayout accentRow() {
        final LinearLayout row = baseRow();
        final int saved = Prefs.appColor();
        final TextView sub = M3.text(this,
                saved == Prefs.DEFAULT_APP_COLOR ? "Hijau bawaan" : hex(saved), 13, M3.onSurfaceVariant, false);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(M3.text(this, "Warna aksen", 16, M3.onSurface, false));
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(saved);
        View dotView = new View(this);
        dotView.setBackground(dot);
        row.addView(dotView, new LinearLayout.LayoutParams(Ui.dp(28), Ui.dp(28)));

        row.setOnClickListener(v -> ColorPicker.show(this, saved, color -> {
            Prefs.setAppColor(color);
            scheduleRebuild();
        }));
        return row;
    }

    private View actionRow(int icon, String label, String sub, View.OnClickListener click) {
        LinearLayout row = baseRow();
        row.addView(icon(icon), new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(M3.text(this, label, 16, M3.onSurface, false));
        texts.addView(M3.text(this, sub, 13, M3.onSurfaceVariant, false));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(20);
        row.addView(texts, tl);
        row.setOnClickListener(click);
        return row;
    }

    private View infoRow(int icon, String label, String value) {
        LinearLayout row = baseRow();
        row.setClickable(false);
        row.addView(icon(icon), new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(M3.text(this, label, 16, M3.onSurface, false));
        texts.addView(M3.text(this, value, 13, M3.onSurfaceVariant, false));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(20);
        row.addView(texts, tl);
        return row;
    }

    private ImageView icon(int type) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Ui.tinted(this, type, M3.onSurfaceVariant));
        return iv;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            M3.toast(this, "Tidak bisa membuka tautan");
        }
    }

    private static String hex(int argb) {
        return String.format("#%06X", 0xFFFFFF & argb);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "-";
        }
    }
}
