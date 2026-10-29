package ru.iqwanoino.sketchlib;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;

/**
 * Popup pilihan minimum API. Container membulat, item terpilih berlatar datar
 * dan selebar popup (tanpa sudut membulat dan tanpa jarak dari tepi).
 */
final class SdkPopup {

    private static final int MIN = 21, MAX = 34;
    private static final int ROW_DP = 48, VISIBLE_ROWS = 6;

    interface Pick {
        void onPick(int sdk);
    }

    private SdkPopup() { }

    static String label(int sdk) {
        String[] names = {"Android 5.0", "Android 5.1", "Android 6.0", "Android 7.0", "Android 7.1",
                "Android 8.0", "Android 8.1", "Android 9", "Android 10", "Android 11",
                "Android 12", "Android 12L", "Android 13", "Android 14"};
        int i = sdk - MIN;
        return sdk + " (" + (i >= 0 && i < names.length ? names[i] : "") + ")";
    }

    static void show(final Activity a, final View anchor, final int selected, final Pick pick) {
        final PopupWindow pw = new PopupWindow(a);

        final ListView lv = new ListView(a);
        lv.setDividerHeight(0);
        lv.setSelector(new ColorDrawable(0x00000000));
        lv.setVerticalScrollBarEnabled(false);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return MAX - MIN + 1; }
            @Override public Object getItem(int position) { return position; }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                int sdk = MIN + position;
                boolean sel = sdk == selected;
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(ROW_DP));
                row.setPadding(Ui.dp(20), 0, Ui.dp(16), 0);
                // Latar datar, tanpa sudut membulat, selebar item.
                row.setBackground(Ui.rowRipple(sel ? Theme.ROW_SEL : 0));
                Ui.pressFeedback(row);
                row.addView(Ui.text(a, label(sdk), 16, sel ? Theme.PRIMARY : Theme.ON, sel),
                        Ui.lpWeight(0, -2, 1f));
                if (sel) {
                    ImageView check = Ui.icon(a, R.drawable.ic_check, Theme.PRIMARY);
                    row.addView(check, Ui.lp(Ui.dp(20), Ui.dp(20)));
                }
                return row;
            }
        });
        lv.setOnItemClickListener((p, v, pos, id) -> {
            pw.dismiss();
            pick.onPick(MIN + pos);
        });

        // Tanpa elevation. Latar dibuat lebih gelap dari bottom sheet agar kontrasnya terlihat.
        FrameLayout box = new FrameLayout(a);
        box.setBackground(Ui.rect(M3.mix(Theme.SURFACE_HIGH, 0xFF000000, 0.22f), 16));
        Ui.round(box, 16);
        box.addView(lv, new FrameLayout.LayoutParams(-1, -1));

        int rows = Math.min(MAX - MIN + 1, VISIBLE_ROWS);
        pw.setContentView(box);
        pw.setWidth(anchor.getWidth());
        pw.setHeight(Ui.dp(ROW_DP * rows));
        pw.setOutsideTouchable(true);
        pw.setFocusable(true);
        pw.setBackgroundDrawable(new ColorDrawable(0x00000000));
        pw.showAsDropDown(anchor, 0, Ui.dp(4));

        // Gulir supaya item terpilih terlihat.
        final int index = Math.max(0, selected - MIN);
        lv.post(() -> lv.setSelection(index));
    }
}
