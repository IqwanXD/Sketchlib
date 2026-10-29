package ru.iqwanoino.sketchlib;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferensi aplikasi (SharedPreferences "weblab"). */
final class Prefs {
    private static SharedPreferences sp;

    /** Warna aksen awal: hijau, sama dengan gaya Music. */
    static final int DEFAULT_APP_COLOR = 0xFF8BC34A;

    private Prefs() { }

    static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("weblab", Context.MODE_PRIVATE);
    }

    // ----- tampilan -----

    /** Tema gelap (default). false = terang. */
    static boolean darkTheme() { return sp == null || sp.getBoolean("dark", true); }
    static void setDarkTheme(boolean on) { if (sp != null) sp.edit().putBoolean("dark", on).apply(); }

    /** Warna dari wallpaper (Material You, Android 12+). */
    static boolean dynamicColor() { return sp != null && sp.getBoolean("dynamic_color", false); }
    static void setDynamicColor(boolean on) { if (sp != null) sp.edit().putBoolean("dynamic_color", on).apply(); }

    static int appColor() { return sp == null ? DEFAULT_APP_COLOR : sp.getInt("app_color", DEFAULT_APP_COLOR); }
    static void setAppColor(int argb) { if (sp != null) sp.edit().putInt("app_color", argb).apply(); }

    // ----- editor -----

    static boolean lineNumbers() { return sp == null || sp.getBoolean("line_numbers", true); }
    static void setLineNumbers(boolean on) { if (sp != null) sp.edit().putBoolean("line_numbers", on).apply(); }

    static boolean highlightLine() { return sp == null || sp.getBoolean("highlight_line", true); }
    static void setHighlightLine(boolean on) { if (sp != null) sp.edit().putBoolean("highlight_line", on).apply(); }

    static boolean autoIndent() { return sp == null || sp.getBoolean("auto_indent", true); }
    static void setAutoIndent(boolean on) { if (sp != null) sp.edit().putBoolean("auto_indent", on).apply(); }

    static boolean wordWrap() { return sp == null || sp.getBoolean("word_wrap", true); }
    static void setWordWrap(boolean on) { if (sp != null) sp.edit().putBoolean("word_wrap", on).apply(); }

    /** Melengkapi tanda kurung, kurung siku, kurung kurawal, dan tanda kutip otomatis. */
    static boolean autoPair() { return sp == null || sp.getBoolean("auto_pair", true); }
    static void setAutoPair(boolean on) { if (sp != null) sp.edit().putBoolean("auto_pair", on).apply(); }

    /** Ukuran font editor dalam sp (10..22). */
    static int fontSize() { return sp == null ? 13 : Math.max(10, Math.min(22, sp.getInt("font_size", 13))); }
    static void setFontSize(int size) { if (sp != null) sp.edit().putInt("font_size", Math.max(10, Math.min(22, size))).apply(); }
}
