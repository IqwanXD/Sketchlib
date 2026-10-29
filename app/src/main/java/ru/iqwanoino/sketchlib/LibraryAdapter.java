package ru.iqwanoino.sketchlib;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Adapter daftar local library: filter pencarian dan mode pilih banyak.
 * Pilihan disimpan per path file, jadi tetap benar walau daftar difilter.
 */
final class LibraryAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<Library> all = new ArrayList<>();
    private final List<Library> shown = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private boolean selectionMode;
    private String query = "";

    LibraryAdapter(Context ctx) {
        this.ctx = ctx;
    }

    void setItems(List<Library> list) {
        all.clear();
        all.addAll(list);
        // Buang pilihan untuk library yang sudah tidak ada.
        Set<String> paths = new HashSet<>();
        for (Library l : all) paths.add(l.file.getAbsolutePath());
        selected.retainAll(paths);
        applyFilter();
    }

    void setQuery(String q) {
        query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    private void applyFilter() {
        shown.clear();
        for (Library l : all) {
            if (query.isEmpty() || l.matches(query)) shown.add(l);
        }
        notifyDataSetChanged();
    }

    int allCount() { return all.size(); }

    // ----- mode pilih banyak -----

    boolean isSelectionMode() { return selectionMode; }

    void setSelectionMode(boolean on) {
        selectionMode = on;
        if (!on) selected.clear();
        notifyDataSetChanged();
    }

    boolean isSelected(Library lib) { return selected.contains(lib.file.getAbsolutePath()); }

    void toggle(Library lib) {
        String key = lib.file.getAbsolutePath();
        if (!selected.remove(key)) selected.add(key);
        notifyDataSetChanged();
    }

    int selectedCount() { return selected.size(); }

    /** Pilih semua yang tampil; jika semua sudah terpilih, kosongkan pilihan. */
    void toggleSelectAll() {
        boolean allSelected = !shown.isEmpty();
        for (Library l : shown) if (!isSelected(l)) { allSelected = false; break; }
        selected.clear();
        if (!allSelected) {
            for (Library l : shown) selected.add(l.file.getAbsolutePath());
        }
        notifyDataSetChanged();
    }

    List<Library> selectedItems() {
        List<Library> out = new ArrayList<>();
        for (Library l : all) if (selected.contains(l.file.getAbsolutePath())) out.add(l);
        return out;
    }

    // ----- Adapter -----

    @Override public int getCount() { return shown.size(); }
    @Override public Object getItem(int position) { return shown.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Library lib = shown.get(position);
        boolean sel = isSelected(lib);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(20), Ui.dp(12));
        // Ripple per baris, tanpa sudut membulat. Latar terpilih datar.
        row.setBackground(Ui.rowRipple(sel ? Theme.ROW_SEL : 0));
        Ui.pressFeedback(row);

        // Ikon kiri: huruf nama, atau checkbox saat mode pilih banyak.
        FrameLayout lead = new FrameLayout(ctx);
        TextView tile = letterTile(lib.name);
        MaterialCheckBox box = new MaterialCheckBox(ctx);
        box.setCheckedColor(Theme.PRIMARY);
        box.setUncheckedColor(Theme.OUTLINE);
        box.setCheckedImmediate(sel);
        // Non-aktif supaya sentuhan diteruskan ke baris (MaterialCheckBox selalu mengonsumsi sentuhan).
        box.setEnabled(false);
        lead.addView(tile, new FrameLayout.LayoutParams(Ui.dp(44), Ui.dp(44), Gravity.CENTER));
        lead.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        tile.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        box.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        row.addView(lead, Ui.lp(Ui.dp(44), Ui.dp(44)));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = Ui.lpWeight(0, -2, 1f);
        tp.leftMargin = Ui.dp(14);
        texts.addView(Ui.text(ctx, lib.name, 16, Theme.ON, true), Ui.lp(-2, -2));

        if (!lib.description.isEmpty()) {
            TextView desc = Ui.text(ctx, lib.description, 13, Theme.ON_VAR, false);
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(desc, Ui.lp(-2, -2));
        }

        String meta = (lib.version.isEmpty() ? "" : "v" + lib.version + " · ")
                + (lib.minSdk > 0 ? "API " + lib.minSdk + " · " : "")
                + lib.typeLabel() + " · " + lib.sizeKb() + " KB";
        texts.addView(Ui.text(ctx, meta, 12, Theme.ON_VAR, false), Ui.lp(-2, -2, 0, Ui.dp(4), 0, 0));
        row.addView(texts, tp);
        return row;
    }

    /** Lingkaran berisi huruf pertama nama library. */
    private TextView letterTile(String name) {
        String initial = name == null || name.isEmpty() ? "?"
                : name.substring(0, 1).toUpperCase(Locale.ROOT);
        TextView t = Ui.text(ctx, initial, 18, Theme.PRIMARY, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Theme.PRIM_CONT);
        t.setBackground(g);
        return t;
    }
}
