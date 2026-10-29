package ru.iqwanoino.sketchlib;

import android.app.Activity;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.List;

/**
 * BottomSheet modal. Isi ditambahkan ke {@link #panel}. Handle di atas bisa diseret
 * untuk menutup. Saat keyboard muncul, seluruh sheet digeser memakai translationY
 * (wrapper {@link #lift}), jadi tidak ada layout ulang per frame.
 */
final class Sheet extends FrameLayout {

    /** Sheet yang sedang tampil; dipakai tombol Back untuk menutupnya. */
    static Sheet current;

    final LinearLayout panel;
    private final FrameLayout lift;
    private final View scrim;
    private final boolean cancelable;
    private Runnable onDismiss;
    private boolean closing;
    private float downY;
    private int kbd;
    private boolean kbdUp;

    Sheet(Activity a, boolean cancelable) {
        super(a);
        this.cancelable = cancelable;
        setClickable(true);

        scrim = new View(a);
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> {
            if (cancelable) dismiss();
        });
        addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.topRounded(Theme.SURFACE_HIGH, 28));
        panel.setPadding(Ui.dp(24), 0, Ui.dp(24), Ui.dp(24) + Ui.insetBottom);
        panel.setElevation(Ui.dp(12));
        panel.setClickable(true);

        LinearLayout handleArea = new LinearLayout(a);
        handleArea.setGravity(Gravity.CENTER);
        handleArea.setPadding(0, Ui.dp(12), 0, Ui.dp(16));
        View handle = new View(a);
        handle.setBackground(Ui.rect(Theme.OUTLINE, 2));
        handleArea.addView(handle, new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(4)));
        handleArea.setOnTouchListener(this::onDrag);
        panel.addView(handleArea, new LinearLayout.LayoutParams(-1, -2));

        lift = new FrameLayout(a);
        lift.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        addView(lift, new FrameLayout.LayoutParams(-1, -1));

        if (Build.VERSION.SDK_INT >= 30) {
            setWindowInsetsAnimationCallback(new WindowInsetsAnimation.Callback(
                    WindowInsetsAnimation.Callback.DISPATCH_MODE_STOP) {
                @Override
                public WindowInsets onProgress(WindowInsets insets, List<WindowInsetsAnimation> running) {
                    applyKeyboard(keyboardOf(insets));
                    return insets;
                }
            });
        }
    }

    void setOnDismiss(Runnable r) {
        onDismiss = r;
    }

    private static int keyboardOf(WindowInsets ins) {
        if (Build.VERSION.SDK_INT < 30) return 0;
        int ime = ins.getInsets(WindowInsets.Type.ime()).bottom;
        int nav = ins.getInsets(WindowInsets.Type.systemBars()).bottom;
        return ime > nav ? ime : 0;
    }

    /** Non-animasi: keyboard berubah tanpa animasi (mis. Android 10 ke bawah). */
    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets ins) {
        applyKeyboard(Build.VERSION.SDK_INT >= 30 ? keyboardOf(ins)
                : Math.max(0, ins.getSystemWindowInsetBottom() - ins.getStableInsetBottom()));
        return super.onApplyWindowInsets(ins);
    }

    /** Geser sheet ke atas sebesar tinggi keyboard. Padding hanya diubah saat keadaan berubah. */
    private void applyKeyboard(int k) {
        if (k != kbd) {
            kbd = k;
            lift.setTranslationY(-k);
        }
        boolean up = k > 0;
        if (up != kbdUp) {
            kbdUp = up;
            panel.setPadding(Ui.dp(24), 0, Ui.dp(24), Ui.dp(24) + (up ? 0 : Ui.insetBottom));
        }
    }

    void show() {
        current = this;
        Ui.applyFont(panel);
        ViewGroup decor = ((Activity) getContext()).findViewById(android.R.id.content);
        decor.addView(this, new FrameLayout.LayoutParams(-1, -1));
        requestApplyInsets();
        panel.setTranslationY(Ui.dp(900));
        panel.animate().translationY(0).setDuration(320)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
        scrim.animate().alpha(1f).setDuration(260).start();
    }

    void dismiss() {
        if (closing) return;
        closing = true;
        if (current == this) current = null;
        panel.animate().translationY(panel.getHeight() + Ui.dp(40)).setDuration(220)
                .setInterpolator(new AccelerateInterpolator(1.2f))
                .withEndAction(() -> {
                    ViewGroup p = (ViewGroup) getParent();
                    if (p != null) p.removeView(this);
                    if (onDismiss != null) onDismiss.run();
                }).start();
        scrim.animate().alpha(0f).setDuration(200).start();
    }

    private boolean onDrag(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = Math.max(0f, e.getRawY() - downY);
                panel.setTranslationY(dy);
                scrim.setAlpha(Math.max(0f, 1f - dy / Math.max(1f, panel.getHeight())));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (cancelable && panel.getTranslationY() > panel.getHeight() * 0.25f) {
                    dismiss();
                } else {
                    panel.animate().translationY(0).setDuration(180).start();
                    scrim.animate().alpha(1f).setDuration(180).start();
                }
                return true;
            default:
                return false;
        }
    }
}
