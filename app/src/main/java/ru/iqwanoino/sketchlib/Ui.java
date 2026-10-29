package ru.iqwanoino.sketchlib;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Helper pembuat view tanpa XML layout, plus pengaturan edge-to-edge. */
final class Ui {
    /** Ukuran seragam untuk semua judul: top bar, sheet, dan judul Settings (34sp, sama dengan Weblab). */
    static final float TITLE_SP = 34f;

    static float density = 1f;
    /** Inset sistem terakhir (status bar atas, navigasi bawah), diisi dari WindowInsets. */
    static int insetTop, insetBottom;

    private Ui() { }

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static GradientDrawable rect(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    static GradientDrawable topRounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        float r = dp(radiusDp);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return g;
    }

    /** Ripple. bg = 0 berarti latar transparan. */
    static RippleDrawable ripple(int bg, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE),
                bg == 0 ? null : rect(bg, radiusDp),
                rect(0xFFFFFFFF, radiusDp));
    }

    /**
     * Ripple per item (background baris). fill = latar datar baris (0 = transparan).
     * Maskernya persegi selebar baris, jadi ripple tidak keluar dari itemnya.
     */
    static RippleDrawable rowRipple(int fill) {
        return new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE),
                fill == 0 ? null : new ColorDrawable(fill),
                new ColorDrawable(0xFFFFFFFF));
    }

    /**
     * Memberi ripple pada baris yang tidak clickable: sentuhan tetap diteruskan ke ListView
     * (klik item tetap jalan), tapi status pressed dan titik sentuh ripple ikut diatur.
     */
    static void pressFeedback(final View row) {
        row.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    if (v.getBackground() != null) v.getBackground().setHotspot(e.getX(), e.getY());
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    static void round(View v, final float radiusDp) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(radiusDp));
            }
        });
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Fonts.bold(c) : Fonts.regular(c));
        return t;
    }

    /** Drawable dari res/drawable, diwarnai lewat color filter (copy supaya tidak saling mengubah). */
    static Drawable tinted(Context c, int res, int color) {
        Drawable d = c.getResources().getDrawable(res).mutate();
        d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        return d;
    }

    static ImageView icon(Context c, int res, int color) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageDrawable(tinted(c, res, color));
        return iv;
    }

    /** Tombol ikon dengan ripple. sizeDp = area sentuh. */
    static ImageView iconButton(Context c, int res, int color, int sizeDp, Runnable action) {
        ImageView iv = new ImageView(c);
        int pad = dp(sizeDp * 0.24f);
        iv.setPadding(pad, pad, pad, pad);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageDrawable(tinted(c, res, color));
        iv.setBackground(ripple(0, sizeDp / 2f));
        iv.setClickable(true);
        if (action != null) iv.setOnClickListener(v -> action.run());
        return iv;
    }

    static TextView button(Context c, String label, int bg, int fg, Runnable action) {
        TextView t = text(c, label, 16, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(ripple(bg, 26));
        t.setClickable(true);
        t.setOnClickListener(v -> action.run());
        return t;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lpWeight(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    static String time(int ms) {
        int s = Math.max(0, ms) / 1000;
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.US, "%d:%02d", m, sec);
    }

    /** Gambar sampai ke belakang status bar dan navigasi. Ikon bar mengikuti tema (M3.styleBars). */
    static void setupEdge(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(0x00000000);
        w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Transparan: konten (drawer & pemutar) tergambar sampai ke belakang navigation bar.
        w.setNavigationBarColor(0x00000000);
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Theme.BG));
        cutoutShortEdges(w);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        M3.styleBars(w);
    }

    /** Efek fade di tepi atas dan bawah list/grid saat di-scroll. */
    static void fade(android.widget.AbsListView v) {
        v.setVerticalFadingEdgeEnabled(true);
        v.setFadingEdgeLength(dp(56));
    }

    /** Warna navigation bar: sama dengan latar pemutar agar mini bar menyatu dengan bar bawah. */
    /**
     * Terapkan font sans.ttf ke semua TextView dan EditText di dalam view, termasuk
     * yang dibuat tanpa Ui.text. Gaya tebal tetap dipertahankan.
     */
    static void applyFont(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            boolean bold = t.getTypeface() != null && t.getTypeface().getStyle() == Typeface.BOLD;
            t.setTypeface(bold ? Fonts.bold(v.getContext()) : Fonts.regular(v.getContext()));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyFont(g.getChildAt(i));
        }
    }

    static void setNav(Activity a, int color) {
        a.getWindow().setNavigationBarColor(color);
    }

    /** Konten boleh tampil di area poni/lubang kamera, sama seperti Pinpic. */
    static void cutoutShortEdges(Window w) {
        if (Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
    }

    /** Baca inset sistem (dipanggil dari listener insets). */
    static void readInsets(WindowInsets ins) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i = ins.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            insetTop = i.top;
            insetBottom = i.bottom;
        } else {
            insetTop = ins.getSystemWindowInsetTop();
            insetBottom = ins.getSystemWindowInsetBottom();
        }
    }

    /**
     * Ganti tampilan tanpa memuat ulang activity: foto layar lama ditaruh di atas, lalu
     * dipudarkan sementara {@code change} membangun ulang view dengan tema baru.
     */
    static void crossfade(Activity a, Runnable change, boolean fade) {
        ViewGroup content = a.findViewById(android.R.id.content);
        View decor = a.getWindow().getDecorView();
        Bitmap snap = null;
        if (fade && decor.getWidth() > 0 && decor.getHeight() > 0) {
            try {
                snap = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
                decor.draw(new Canvas(snap));
            } catch (Throwable t) {
                snap = null;
            }
        }
        change.run();
        if (snap == null || content == null) return;
        final ImageView over = new ImageView(a);
        over.setImageBitmap(snap);
        over.setScaleType(ImageView.ScaleType.FIT_XY);
        content.addView(over, new ViewGroup.LayoutParams(-1, -1));
        over.animate().alpha(0f).setDuration(280).withEndAction(() -> {
            if (over.getParent() instanceof ViewGroup) ((ViewGroup) over.getParent()).removeView(over);
        }).start();
    }
}
