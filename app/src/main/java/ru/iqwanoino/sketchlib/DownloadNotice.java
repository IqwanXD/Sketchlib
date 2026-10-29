package ru.iqwanoino.sketchlib;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Notifikasi progres unduhan library: berjalan, selesai, atau gagal. */
final class DownloadNotice {

    static final int ID = 4101;
    private static final String CHANNEL = "download";
    static final String PERMISSION = "android.permission.POST_NOTIFICATIONS";

    private DownloadNotice() { }

    private static NotificationManager nm(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /** Notifikasi di Android 13+ hanya tampil bila izin diberikan dan notifikasi tidak dimatikan. */
    static boolean allowed(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return Build.VERSION.SDK_INT < 24 || nm(c).areNotificationsEnabled();
    }

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Unduhan library",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Progres unduhan library Sketchlib");
        nm(c).createNotificationChannel(ch);
    }

    private static Notification.Builder builder(Context c) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_download);
        b.setContentIntent(openApp(c));
        if (Build.VERSION.SDK_INT >= 26) b.setOnlyAlertOnce(true);
        return b;
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? 0x04000000 /* FLAG_IMMUTABLE */ : 0);
        return PendingIntent.getActivity(c, 0, i, flags);
    }

    /** Notifikasi berjalan. pct < 0 berarti ukuran belum diketahui (bar berputar). */
    static void progress(Context c, String title, String text, int pct) {
        ensureChannel(c);
        Notification.Builder b = builder(c)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true);
        if (pct < 0) {
            b.setProgress(100, 0, true);
        } else {
            b.setProgress(100, pct, false);
        }
        nm(c).notify(ID, b.build());
    }

    /** Notifikasi hasil akhir (selesai atau gagal). Bisa diketuk untuk membuka app. */
    static void finished(Context c, String title, String text) {
        ensureChannel(c);
        Notification n = builder(c)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build();
        nm(c).notify(ID, n);
    }

    static void cancel(Context c) {
        nm(c).cancel(ID);
    }
}
