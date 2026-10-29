package ru.iqwanoino.sketchlib;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.List;

/** Sheet informasi, izin penyimpanan, dan konfirmasi hapus library. */
final class AppSheets {

    private AppSheets() { }

    static void info(Activity a) {
        final Sheet s = new Sheet(a, true);
        LinearLayout p = s.panel;
        p.addView(Ui.text(a, "Informasi", Ui.TITLE_SP, Theme.ON, true),
                Ui.lp(-2, -2, 0, 0, 0, Ui.dp(12)));
        p.addView(Ui.text(a, "Sketchlib membantu kamu mengunduh dan mengelola local library "
                        + "untuk project Sketchware.\n\nLibrary disimpan di:\n/sdcard/"
                        + LibraryStore.REL_DIR,
                15, Theme.ON_VAR, false), Ui.lp(-1, -2, 0, 0, 0, Ui.dp(20)));
        p.addView(Ui.button(a, "Tutup", 0x33FFFFFF, Theme.ON, s::dismiss),
                Ui.lp(-1, Ui.dp(56)));
        s.show();
    }

    static final int REQ_NOTIFICATION = 2168;

    static void storage(final Activity a) {
        storage(a, null);
    }

    /** Sheet izin penyimpanan. next dijalankan setelah sheet ditutup (untuk merangkai sheet berikutnya). */
    static void storage(final Activity a, final Runnable next) {
        final Sheet s = new Sheet(a, true);
        if (next != null) s.setOnDismiss(next);
        LinearLayout p = s.panel;
        p.addView(Ui.text(a, "Izin penyimpanan", Ui.TITLE_SP, Theme.ON, true),
                Ui.lp(-2, -2, 0, 0, 0, Ui.dp(12)));
        p.addView(Ui.text(a, "Sketchlib perlu akses file untuk membaca dan memasang library di "
                        + "folder .sketchware. Tanpa izin ini, daftar library tidak bisa dimuat.",
                15, Theme.ON_VAR, false), Ui.lp(-1, -2, 0, 0, 0, Ui.dp(20)));
        p.addView(Ui.button(a, "Berikan izin", Theme.PRIMARY, Theme.ON_PRIMARY, () -> {
            s.dismiss();
            LibraryStore.requestStorageAccess(a);
        }), Ui.lp(-1, Ui.dp(56), 0, 0, 0, Ui.dp(8)));
        p.addView(Ui.button(a, "Nanti", Theme.SURFACE_HIGH, Theme.ON, s::dismiss),
                Ui.lp(-1, Ui.dp(56)));
        s.show();
    }

    /** Tampilkan sheet notifikasi hanya bila diperlukan (Android 13+) dan belum diizinkan. */
    static void notificationIfNeeded(Activity a) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (a.checkSelfPermission(DownloadNotice.PERMISSION) == PackageManager.PERMISSION_GRANTED) return;
        notification(a);
    }

    static void notification(final Activity a) {
        final Sheet s = new Sheet(a, true);
        LinearLayout p = s.panel;
        p.addView(Ui.text(a, "Izin notifikasi", Ui.TITLE_SP, Theme.ON, true),
                Ui.lp(-2, -2, 0, 0, 0, Ui.dp(12)));
        p.addView(Ui.text(a, "Sketchlib menampilkan progres unduhan di notifikasi, jadi kamu tetap "
                        + "bisa melihatnya saat unduhan berjalan di latar belakang.",
                15, Theme.ON_VAR, false), Ui.lp(-1, -2, 0, 0, 0, Ui.dp(20)));
        p.addView(Ui.button(a, "Berikan izin", Theme.PRIMARY, Theme.ON_PRIMARY, () -> {
            s.dismiss();
            if (Build.VERSION.SDK_INT >= 33) {
                a.requestPermissions(new String[]{DownloadNotice.PERMISSION}, REQ_NOTIFICATION);
            }
        }), Ui.lp(-1, Ui.dp(56), 0, 0, 0, Ui.dp(8)));
        p.addView(Ui.button(a, "Nanti", Theme.SURFACE_HIGH, Theme.ON, s::dismiss),
                Ui.lp(-1, Ui.dp(56)));
        s.show();
    }

    /**
     * Konfirmasi hapus: lingkaran merah berisi ikon tempat sampah, judul, penjelasan,
     * tombol Batal, dan tombol Hapus yang berwarna error.
     */
    static void confirmDelete(final Activity a, final List<Library> libs, final Runnable onDone) {
        final Sheet s = new Sheet(a, true);
        final LinearLayout p = s.panel;
        final int n = libs.size();

        FrameLayout badge = new FrameLayout(a);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Theme.ERROR_CONT);
        badge.setBackground(circle);
        ImageView icon = Ui.icon(a, R.drawable.ic_delete, Theme.ON_ERROR_CONT);
        badge.addView(icon, new FrameLayout.LayoutParams(Ui.dp(26), Ui.dp(26), Gravity.CENTER));
        LinearLayout.LayoutParams bl = Ui.lp(Ui.dp(64), Ui.dp(64));
        bl.gravity = Gravity.CENTER_HORIZONTAL;
        p.addView(badge, bl);

        String title = n == 1 ? "Hapus " + libs.get(0).name + "?" : "Hapus " + n + " library?";
        android.widget.TextView t = Ui.text(a, title, Ui.TITLE_SP, Theme.ON, true);
        t.setGravity(Gravity.CENTER);
        p.addView(t, Ui.lp(-1, -2, 0, Ui.dp(16), 0, Ui.dp(12)));

        android.widget.TextView desc = Ui.text(a,
                "Data tidak bisa dipulihkan setelah dihapus dari local_libs.",
                15, Theme.ON_VAR, false);
        desc.setGravity(Gravity.CENTER);
        p.addView(desc, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(24)));

        p.addView(Ui.button(a, "Batal", 0x33FFFFFF, Theme.ON, s::dismiss),
                Ui.lp(-1, Ui.dp(56), 0, 0, 0, Ui.dp(10)));
        p.addView(Ui.button(a, "Hapus", Theme.ERROR, Theme.ON_ERROR, () -> {
            int failed = 0;
            for (Library l : libs) if (!LibraryStore.delete(l)) failed++;
            s.dismiss();
            M3.toast(a, failed == 0 ? n + " library dihapus"
                    : failed + " library gagal dihapus");
            onDone.run();
        }), Ui.lp(-1, Ui.dp(56)));
        s.show();
    }
}
