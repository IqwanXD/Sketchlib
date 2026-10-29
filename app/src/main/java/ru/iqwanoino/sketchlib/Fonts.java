package ru.iqwanoino.sketchlib;

import android.content.Context;
import android.graphics.Typeface;

/** Font aplikasi dari assets/fonts/sans.ttf (font asli Sketchlib). Tebal dibuat oleh sistem. */
final class Fonts {
    private static Typeface regular, bold;

    private Fonts() { }

    static Typeface regular(Context c) {
        load(c);
        return regular;
    }

    static Typeface bold(Context c) {
        load(c);
        return bold;
    }

    private static void load(Context c) {
        if (regular != null) return;
        Typeface t;
        try {
            t = Typeface.createFromAsset(c.getApplicationContext().getAssets(), "fonts/sans.ttf");
        } catch (Exception e) {
            t = Typeface.DEFAULT;
        }
        regular = t;
        bold = Typeface.create(t, Typeface.BOLD);
    }
}
