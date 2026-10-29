package ru.iqwanoino.sketchlib;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.TextView;

/** Token warna + bentuk Material 3 (Expressive) tema gelap, tanpa library. */
public final class M3 {
    public static int primary, onPrimary, primaryContainer, onPrimaryContainer;
    public static int secondaryContainer, onSecondaryContainer;
    public static int background, surfaceContainerHigh, onSurface, onSurfaceVariant, outline;
    public static int scrim = 0x99000000, inverseSurface, inverseOnSurface;
    private static float density = 1f;
    private static boolean inited;

    /** Tema yang sedang dipakai: gelap atau terang (dari Prefs). */
    public static boolean dark = true;

    /**
     * Kunci tema yang sedang terpasang. Activity yang sudah tampil membandingkannya dengan
     * Prefs saat kembali (onResume), lalu membangun ulang diri bila berbeda.
     */
    public static String paletteKey = "";

    /** Palet dihitung ulang setiap kali sebuah activity dibuat, jadi selalu sesuai Prefs terbaru. */
    public static void init(Context c) {
        Prefs.init(c);
        density = c.getResources().getDisplayMetrics().density;
        dark = Prefs.darkTheme();
        boolean dynamic = Build.VERSION.SDK_INT >= 31 && Prefs.dynamicColor();
        int seed = Prefs.appColor();
        if (dynamic) {
            if (dark) dynamicDark(c); else dynamicLight(c);
        } else if (seed != 0) {
            derived(seed, dark);
        } else {
            if (dark) baselineDark(); else baselineLight();
        }
        inverseSurface = onSurface;
        inverseOnSurface = background;
        paletteKey = dark + "|" + dynamic + "|" + seed;
        inited = true;
    }

    /** Material You (gelap): warna sistem Android 12+. */
    private static void dynamicDark(Context c) {
        primary = c.getColor(android.R.color.system_accent1_200);
        onPrimary = c.getColor(android.R.color.system_accent1_800);
        primaryContainer = c.getColor(android.R.color.system_accent1_700);
        onPrimaryContainer = c.getColor(android.R.color.system_accent1_100);
        secondaryContainer = c.getColor(android.R.color.system_accent2_700);
        onSecondaryContainer = c.getColor(android.R.color.system_accent2_100);
        background = mix(c.getColor(android.R.color.system_neutral1_900), Color.BLACK, 0.35f);
        surfaceContainerHigh = c.getColor(android.R.color.system_neutral1_800);
        onSurface = c.getColor(android.R.color.system_neutral1_100);
        onSurfaceVariant = c.getColor(android.R.color.system_neutral2_200);
        outline = c.getColor(android.R.color.system_neutral2_400);
    }

    /** Material You (terang): warna sistem Android 12+. */
    private static void dynamicLight(Context c) {
        primary = c.getColor(android.R.color.system_accent1_600);
        onPrimary = c.getColor(android.R.color.system_accent1_0);
        primaryContainer = c.getColor(android.R.color.system_accent1_100);
        onPrimaryContainer = c.getColor(android.R.color.system_accent1_900);
        secondaryContainer = c.getColor(android.R.color.system_accent2_100);
        onSecondaryContainer = c.getColor(android.R.color.system_accent2_900);
        background = c.getColor(android.R.color.system_neutral1_10);
        surfaceContainerHigh = c.getColor(android.R.color.system_neutral1_100);
        onSurface = c.getColor(android.R.color.system_neutral1_900);
        onSurfaceVariant = c.getColor(android.R.color.system_neutral2_700);
        outline = c.getColor(android.R.color.system_neutral2_500);
    }

    /** Palet statis bawaan (gelap): sama seperti sebelumnya. */
    private static void baselineDark() {
        primary = 0xFFD0BCFF;
        onPrimary = 0xFF381E72;
        primaryContainer = 0xFF4F378B;
        onPrimaryContainer = 0xFFEADDFF;
        secondaryContainer = 0xFF4A4458;
        onSecondaryContainer = 0xFFE8DEF8;
        background = 0xFF0F0D13;
        surfaceContainerHigh = 0xFF2B2930;
        onSurface = 0xFFE6E0E9;
        onSurfaceVariant = 0xFFCAC4D0;
        outline = 0xFF938F99;
    }

    /** Palet statis bawaan (terang): token M3 baseline. */
    private static void baselineLight() {
        primary = 0xFF6750A4;
        onPrimary = 0xFFFFFFFF;
        primaryContainer = 0xFFEADDFF;
        onPrimaryContainer = 0xFF21005D;
        secondaryContainer = 0xFFE8DEF8;
        onSecondaryContainer = 0xFF1D192B;
        background = 0xFFFEF7FF;
        surfaceContainerHigh = 0xFFECE6F0;
        onSurface = 0xFF1D1B20;
        onSurfaceVariant = 0xFF49454F;
        outline = 0xFF79747E;
    }

    /**
     * Palet dari satu warna aksen pilihan pengguna. Nada dihitung dari hue dan saturasi warna itu
     * (kecerahan warna pilihan diabaikan), lalu dibuat untuk tema gelap atau terang.
     */
    private static void derived(int seed, boolean dark) {
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        float v = hsv[2], sv = hsv[1];
        float l = v * (1f - sv / 2f);                       // HSV -> HSL
        float sl = (l <= 0f || l >= 1f) ? 0f : (v - l) / Math.min(l, 1f - l);
        float h = hsv[0];
        float s = sl < 0.08f ? 0.04f : Math.max(0.3f, Math.min(sl, 0.85f));
        float ns = 0.08f;                                   // rona netral yang ikut hue

        if (dark) {
            primary = hsl(h, s, 0.82f);
            onPrimary = hsl(h, s, 0.22f);
            primaryContainer = hsl(h, s, 0.30f);
            onPrimaryContainer = hsl(h, s, 0.90f);
            secondaryContainer = hsl(h, s * 0.4f, 0.26f);
            onSecondaryContainer = hsl(h, s * 0.4f, 0.90f);
            background = hsl(h, ns, 0.07f);
            surfaceContainerHigh = hsl(h, ns, 0.15f);
            onSurface = hsl(h, 0.06f, 0.90f);
            onSurfaceVariant = hsl(h, 0.08f, 0.80f);
            outline = hsl(h, 0.08f, 0.56f);
        } else {
            primary = hsl(h, s, 0.40f);
            onPrimary = hsl(h, s, 1.00f);
            primaryContainer = hsl(h, s, 0.88f);
            onPrimaryContainer = hsl(h, s, 0.18f);
            secondaryContainer = hsl(h, s * 0.4f, 0.90f);
            onSecondaryContainer = hsl(h, s * 0.4f, 0.18f);
            background = hsl(h, ns * 0.5f, 0.98f);
            surfaceContainerHigh = hsl(h, ns * 0.6f, 0.92f);
            onSurface = hsl(h, 0.06f, 0.12f);
            onSurfaceVariant = hsl(h, 0.08f, 0.30f);
            outline = hsl(h, 0.08f, 0.50f);
        }
    }

    /** HSL (h 0..360, s dan l 0..1) ke warna ARGB opak. */
    private static int hsl(float h, float s, float l) {
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float hp = ((h % 360f) + 360f) % 360f / 60f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float r, g, b;
        if (hp < 1f)      { r = c; g = x; b = 0; }
        else if (hp < 2f) { r = x; g = c; b = 0; }
        else if (hp < 3f) { r = 0; g = c; b = x; }
        else if (hp < 4f) { r = 0; g = x; b = c; }
        else if (hp < 5f) { r = x; g = 0; b = c; }
        else              { r = c; g = 0; b = x; }
        float m = l - c / 2f;
        return Color.rgb(Math.round((r + m) * 255f), Math.round((g + m) * 255f), Math.round((b + m) * 255f));
    }

    /** Warna dengan alpha tertentu (0..1), dipakai untuk garis pemisah dan ripple. */
    public static int alpha(int color, float a) {
        return (Math.round(a * 255f) << 24) | (color & 0x00FFFFFF);
    }

    /** Ripple netral: putih di tema gelap, hitam di tema terang. */
    public static int rippleColor() {
        return dark ? 0x33FFFFFF : 0x33000000;
    }

    /**
     * Ikon bar status dan navigasi mengikuti tema: gelap di atas latar terang, terang di atas gelap.
     * Warna bar itu sendiri diatur oleh activity masing-masing.
     */
    public static void styleBars(Window w) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getDecorView().getWindowInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(dark ? 0 : light, light);
            }
            return;
        }
        View d = w.getDecorView();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        int f = d.getSystemUiVisibility();
        d.setSystemUiVisibility(dark ? f & ~light : f | light);
    }

    public static int dp(float v) { return Math.round(v * density); }

    public static int mix(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) * (1 - t) + Color.red(b) * t),
                (int) (Color.green(a) * (1 - t) + Color.green(b) * t),
                (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    public static GradientDrawable shape(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static GradientDrawable shape(int color, float tl, float tr, float br, float bl) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadii(new float[]{dp(tl), dp(tl), dp(tr), dp(tr), dp(br), dp(br), dp(bl), dp(bl)});
        return g;
    }

    public static Drawable ripple(int fill, float radiusDp) {
        double lum = 0.299 * Color.red(fill) + 0.587 * Color.green(fill) + 0.114 * Color.blue(fill);
        int rc = Color.alpha(fill) == 0 ? rippleColor()
                : (lum > 140 ? 0x33000000 : 0x33FFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(rc), shape(fill, radiusDp), shape(Color.WHITE, radiusDp));
    }

    public static void round(View v, final float rDp) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(rDp));
            }
        });
        v.setClipToOutline(true);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        // DEFAULT_BOLD/DEFAULT (bukan Typeface.create) supaya gaya tebal tetap terbawa oleh FontManager.
        t.setTypeface(bold ? Fonts.bold(c) : Fonts.regular(c));
        return t;
    }

    /**
     * Jarak toast dari bawah layar (dp). Toast sistem sudah otomatis naik di atas navigation bar,
     * jadi posisi ini sama dengan snackbar "Gambar dihapus" (100dp di atas navigasi).
     */
    private static final int TOAST_BOTTOM_DP = 100;

    /**
     * Toast bawaan Android (mengambang, muncul di atas navigasi) dengan tampilan custom bergaya M3:
     * kartu inverse surface, sudut membulat, teks inverse on surface. Dipakai di semua activity.
     */
    public static void toast(Context c, CharSequence msg) {
        if (!inited) init(c);
        Context app = c.getApplicationContext();

        TextView t = new TextView(app);
        t.setText(msg);
        t.setTextSize(14);
        t.setTextColor(inverseOnSurface);
        t.setTypeface(Fonts.regular(app));
        t.setPadding(dp(20), dp(14), dp(20), dp(14));
        t.setBackground(shape(inverseSurface, 16));
        t.setElevation(dp(10));

        android.widget.Toast toast = new android.widget.Toast(app);
        toast.setView(t);
        toast.setDuration(android.widget.Toast.LENGTH_SHORT);
        toast.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL, 0, dp(TOAST_BOTTOM_DP));
        toast.show();
    }
}
