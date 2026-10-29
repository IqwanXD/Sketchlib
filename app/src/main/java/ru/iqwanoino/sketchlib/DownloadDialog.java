package ru.iqwanoino.sketchlib;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Dialog M3 progres unduhan: judul, nama library, InoProgressBar, persentase dan ukuran,
 * lalu tombol Perkecil (dialog disembunyikan, unduhan lanjut di notifikasi) dan Batal.
 */
final class DownloadDialog {

    private final Activity act;
    private final FrameLayout root;
    private final InoProgressBar bar;
    private final TextView info;
    private final Runnable onCancel;
    private boolean shown;

    /** onCancel dipanggil saat tombol Batal ditekan. Dialog sudah disembunyikan sebelumnya. */
    DownloadDialog(Activity a, String subtitle, Runnable onCancel) {
        this.act = a;
        this.onCancel = onCancel;

        root = new FrameLayout(a);
        root.setClickable(true); // sentuhan tidak tembus ke layar di bawahnya

        View scrim = new View(a);
        scrim.setBackgroundColor(0x99000000);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(24), Ui.dp(24), Ui.dp(24), Ui.dp(16));
        card.setBackground(Ui.rect(Theme.SURFACE_HIGH, 28));
        card.setElevation(Ui.dp(6));
        card.setClickable(true);

        card.addView(Ui.text(a, "Mengunduh...", 24, Theme.ON, false), Ui.lp(-2, -2));
        card.addView(Ui.text(a, subtitle, 16, Theme.ON, false), Ui.lp(-2, -2, 0, Ui.dp(4), 0, Ui.dp(20)));

        bar = new InoProgressBar(a);
        bar.setTrackActiveColor(Theme.PRIMARY);
        bar.setTrackInactiveColor(M3.alpha(Theme.PRIMARY, 0.24f));
        bar.setIndeterminate(true);
        card.addView(bar, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(14)));

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        info = Ui.text(a, "Menghubungkan…", 14, Theme.ON_VAR, false);
        row.addView(info, Ui.lpWeight(0, -2, 1f));

        TextView minimize = Ui.button(a, "Perkecil", Theme.PRIM_CONT, Theme.ON, this::hide);
        minimize.setPadding(Ui.dp(16), 0, Ui.dp(16), 0);
        row.addView(minimize, Ui.lp(-2, Ui.dp(40)));

        TextView cancel = Ui.button(a, "Batal", 0, Theme.PRIMARY, () -> {
            hide();
            onCancel.run();
        });
        cancel.setPadding(Ui.dp(16), 0, Ui.dp(8), 0);
        row.addView(cancel, Ui.lp(-2, Ui.dp(40), Ui.dp(4), 0, 0, 0));

        card.addView(row, Ui.lp(-1, -2));

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        cardLp.setMargins(Ui.dp(24), 0, Ui.dp(24), 0);
        root.addView(card, cardLp);
    }

    /** Tampilkan dialog di atas activity dengan animasi fade. */
    void show() {
        if (shown) return;
        shown = true;
        ViewGroup decor = (ViewGroup) act.findViewById(android.R.id.content);
        if (root.getParent() instanceof ViewGroup) ((ViewGroup) root.getParent()).removeView(root);
        root.setAlpha(0f);
        decor.addView(root, new FrameLayout.LayoutParams(-1, -1));
        root.animate().alpha(1f).setDuration(200).start();
    }

    /** Sembunyikan dialog tanpa membatalkan unduhan. */
    void hide() {
        if (!shown) return;
        shown = false;
        root.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (root.getParent() instanceof ViewGroup) ((ViewGroup) root.getParent()).removeView(root);
        }).start();
    }

    void setStatus(String text) {
        if (!bar.isIndeterminate()) return;
        info.setText(text);
    }

    /** done dan total dalam byte. total <= 0 berarti ukuran belum diketahui. */
    void setProgress(long done, long total) {
        if (total > 0) {
            int pct = (int) (done * 100 / total);
            bar.setIndeterminate(false);
            bar.setProgress(pct);
            info.setText(pct + "% · " + mb(done) + " / " + mb(total));
        } else {
            bar.setIndeterminate(true);
            info.setText(mb(done));
        }
    }

    private static String mb(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }
}
