package ru.iqwanoino.sketchlib;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * BottomSheet download library. Pengguna menempel implementasi seperti di Gradle
 * (group:artifact:versi). Setelah Download ditekan, progres tampil di dialog M3 dan notifikasi.
 */
final class DownloadSheet {

    private static final int DEFAULT_SDK = 21;

    private DownloadSheet() { }

    static void show(final Activity a, final Runnable onDone) {
        final Sheet s = new Sheet(a, true);
        LinearLayout p = s.panel;
        final int[] sdk = {DEFAULT_SDK};

        p.addView(Ui.text(a, "Download library", Ui.TITLE_SP, Theme.ON, true),
                Ui.lp(-2, -2, 0, 0, 0, Ui.dp(8)));
        p.addView(Ui.text(a, "Tempel implementasi seperti di Gradle, contoh "
                        + "com.github.bumptech.glide:glide:4.16.0",
                14, Theme.ON_VAR, false), Ui.lp(-1, -2, 0, 0, 0, Ui.dp(16)));

        final InoInputLayout input = new InoInputLayout(a);
        input.setLabelText("Implementasi library");
        input.setIsMultiline(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        p.addView(input, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(12)));

        // Pilihan minimum API: field tidak bisa diketik, membuka popup saat diketuk.
        final InoInputLayout sdkField = new InoInputLayout(a);
        sdkField.setLabelText("Versi API minimum");
        sdkField.setIsMultiline(false);
        sdkField.setText(SdkPopup.label(sdk[0]));
        EditText sdkEt = sdkField.getEditText();
        sdkEt.setFocusable(false);
        sdkEt.setFocusableInTouchMode(false);
        sdkEt.setCursorVisible(false);
        android.graphics.drawable.Drawable arrow =
                Ui.tinted(a, R.drawable.ic_arrow_drop_down, Theme.ON_VAR);
        sdkEt.setCompoundDrawablesWithIntrinsicBounds(null, null, arrow, null);
        View.OnClickListener openSdk = v -> SdkPopup.show(a, sdkField, sdk[0], picked -> {
            sdk[0] = picked;
            sdkField.setText(SdkPopup.label(picked));
        });
        sdkField.setOnClickListener(openSdk);
        sdkEt.setOnClickListener(openSdk);
        p.addView(sdkField, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(12)));

        // Checkbox: ikut unduh dependensi (transitive). Baris seluruhnya bisa diketuk.
        final MaterialCheckBox transitive = new MaterialCheckBox(a);
        transitive.setCheckedColor(Theme.PRIMARY);
        transitive.setUncheckedColor(Theme.OUTLINE);
        transitive.setCheckedImmediate(true);
        transitive.setEnabled(false); // sentuhan diteruskan ke baris

        LinearLayout check = new LinearLayout(a);
        check.setOrientation(LinearLayout.HORIZONTAL);
        check.setGravity(Gravity.CENTER_VERTICAL);
        check.setPadding(0, Ui.dp(8), 0, Ui.dp(8));
        // Ripple membulat radius 16dp: content null, hanya efek ripple yang dipotong bentuk ini.
        check.setBackground(new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE),
                null, Ui.rect(0xFFFFFFFF, 16)));
        check.setClickable(true);
        check.setOnClickListener(v -> transitive.setChecked(!transitive.isChecked()));
        check.addView(transitive, Ui.lp(-2, -2));

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(12), 0, 0, 0);
        texts.addView(Ui.text(a, "Sertakan library transitive", 16, Theme.ON, false), Ui.lp(-2, -2));
        texts.addView(Ui.text(a, "Ikut unduh dependensi yang dibutuhkan library ini",
                13, Theme.ON_VAR, false), Ui.lp(-2, -2));
        check.addView(texts, Ui.lpWeight(0, -2, 1f));
        p.addView(check, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(16)));

        final TextView download = Ui.button(a, "Download", Theme.PRIMARY, Theme.ON_PRIMARY, null);
        p.addView(download, Ui.lp(-1, Ui.dp(56)));

        download.setOnClickListener(v -> {
            String raw = input.getText().trim();
            if (Downloader.parse(raw) == null) {
                M3.toast(a, Downloader.FORMAT_HINT);
                return;
            }
            if (!LibraryStore.hasStorageAccess(a)) {
                AppSheets.storage(a);
                return;
            }
            s.dismiss();
            startDownload(a, raw, sdk[0], transitive.isChecked(), onDone);
        });
        s.show();
    }

    /** Dialog progres + notifikasi, lalu pemasangan. Dibatalkan lewat tombol Batal di dialog. */
    private static void startDownload(final Activity a, String raw, int minSdk, boolean transitive,
                                      final Runnable onDone) {
        final Context app = a.getApplicationContext();
        Downloader.Coord c = Downloader.parse(raw);
        final String label = c.artifact + "-" + c.version;
        final Downloader.Job job = new Downloader.Job();

        final DownloadDialog dialog = new DownloadDialog(a, label, job::cancel);
        dialog.show();
        DownloadNotice.progress(app, "Mengunduh " + label, "Menghubungkan…", -1);

        Downloader.start(app, raw, minSdk, transitive, job, new Downloader.Listener() {
            @Override
            public void onStatus(String text) {
                dialog.setStatus(text);
                DownloadNotice.progress(app, "Mengunduh " + label, text, -1);
            }

            @Override
            public void onProgress(long done, long total) {
                dialog.setProgress(done, total);
                int pct = total > 0 ? (int) (done * 100 / total) : -1;
                String text = pct >= 0 ? pct + "% · " + (done / 1048576) + " MB" : "Mengunduh…";
                DownloadNotice.progress(app, "Mengunduh " + label, text, pct);
            }

            @Override
            public void onSuccess(String name) {
                dialog.hide();
                DownloadNotice.cancel(app); // notifikasi dihapus begitu sudah terunduh
                M3.toast(a, name + " terpasang");
                onDone.run();
            }

            @Override
            public void onFailure(String reason) {
                dialog.hide();
                if (Downloader.CANCELLED.equals(reason)) {
                    DownloadNotice.cancel(app);
                    M3.toast(a, "Download dibatalkan");
                } else {
                    DownloadNotice.finished(app, "Gagal mengunduh " + label, reason);
                    M3.toast(a, "Gagal: " + reason);
                }
            }
        });
    }
}
