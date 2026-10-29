package ru.iqwanoino.sketchlib;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.provider.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Akses folder local library: /sdcard/.sketchware/libs/local_libs. */
final class LibraryStore {

    static final String REL_DIR = ".sketchware/libs/local_libs";
    static final int REQ_STORAGE = 2167;

    private LibraryStore() { }

    /** Sama dengan /sdcard/.sketchware/libs/local_libs. */
    static File dir() {
        return new File(Environment.getExternalStorageDirectory(), REL_DIR);
    }

    /** Semua isi local_libs, diurutkan berdasarkan nama. */
    static List<Library> loadAll() {
        List<Library> out = new ArrayList<>();
        File[] kids = dir().listFiles();
        if (kids == null) return out;
        Arrays.sort(kids, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File f : kids) {
            // Hanya folder yang dimuat sebagai library.
            if (f.isDirectory()) out.add(Library.from(f));
        }
        return out;
    }

    static boolean delete(Library lib) {
        return deleteRecursive(lib.file);
    }

    static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        return !f.exists() || f.delete();
    }

    static boolean hasStorageAccess(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE,
                Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED
                && c.checkPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED;
    }

    static void requestStorageAccess(Activity a) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (ActivityNotFoundException e) {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            a.requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }
}
