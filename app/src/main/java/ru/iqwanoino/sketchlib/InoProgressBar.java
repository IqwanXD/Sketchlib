package ru.iqwanoino.sketchlib;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

public class InoProgressBar extends View {

    /* ===== SIZE (dp) ===== */
    private static final float TRACK_HEIGHT_DP = 5f;
    private static final float GAP_DP = 4f;

    /* ===== DEFAULT COLOR ===== */
    private int trackActiveColor   = 0xFF0D47A1;
    private int trackInactiveColor = 0x4D0D47A1;

    /* ===== STATE ===== */
    private boolean indeterminate = false;
    private int max = 100;
    private int progress = 0;

    /* ===== ANIMATION ===== */
    private ValueAnimator animator;
    private float head = 0f;
    private float tail = 0f;
    private final PathInterpolator interpolator = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    /* ===== DRAW ===== */
    private float density;
    private float trackHeight;
    private float gap;
    private Paint activePaint;
    private Paint inactivePaint;

    public InoProgressBar(Context c) {
        super(c);
        init();
    }

    public InoProgressBar(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        trackHeight = TRACK_HEIGHT_DP * density;
        gap = GAP_DP * density;

        activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setColor(trackActiveColor);
        activePaint.setStyle(Paint.Style.FILL);

        inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactivePaint.setColor(trackInactiveColor);
        inactivePaint.setStyle(Paint.Style.FILL);

        setMinimumHeight(0);
    }

    /* ===================== API ===================== */

    public void setIndeterminate(boolean enable) {
        if (this.indeterminate == enable) return;

        this.indeterminate = enable;

        if (enable) {
            startAnim();
        } else {
            stopAnim();
        }
        invalidate();
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    public void setProgress(int value) {
        progress = Math.max(0, Math.min(value, max));
        invalidate();
    }

    public void setMax(int value) {
        max = Math.max(1, value);
        if (progress > max) progress = max;
        invalidate();
    }

    public void setTrackActiveColor(int color) {
        trackActiveColor = color;
        activePaint.setColor(color);
        invalidate();
    }

    public void setTrackInactiveColor(int color) {
        trackInactiveColor = color;
        inactivePaint.setColor(color);
        invalidate();
    }

    /* ===================== ANIM ===================== */

    private void startAnim() {
        stopAnim();

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            head = interpolator.getInterpolation(Math.min(v * 1.5f, 1f));
            tail = interpolator.getInterpolation(Math.max(0f, (v - 0.3f) * 1.42f));
            invalidate();
        });
        animator.start();
    }

    private void stopAnim() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    /* ===================== MEASURE ===================== */

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(
                MeasureSpec.getSize(w),
                (int) (trackHeight + getPaddingTop() + getPaddingBottom())
        );
    }

    /* ===================== DRAW ===================== */

    @Override
    protected void onDraw(Canvas c) {
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float cy = getHeight() / 2f;
        float r = trackHeight / 2f;

        float top = cy - r;
        float bottom = cy + r;

        if (indeterminate) {
            drawIndeterminate(c, left, right, top, bottom, r);
        } else {
            drawDeterminate(c, left, right, top, bottom, r);
        }
    }

    private void drawDeterminate(Canvas c, float l, float r,
                                 float t, float b, float rad) {

        float width = r - l;
        float p = (float) progress / max;
        float end = l + width * p;

        if (end > l) {
            c.drawRoundRect(
                    new RectF(l, t, end, b),
                    rad, rad, activePaint
            );
        }

        if (end + gap < r) {
            c.drawRoundRect(
                    new RectF(end + gap, t, r, b),
                    rad, rad, inactivePaint
            );
        }
    }

    private void drawIndeterminate(Canvas c, float l, float r,
                                   float t, float b, float rad) {

        float width = r - l;
        float start = l + width * tail;
        float end = l + width * head;

        if (start > l + gap) {
            c.drawRoundRect(
                    new RectF(l, t, start - gap, b),
                    rad, rad, inactivePaint
            );
        }

        if (end < r - gap) {
            c.drawRoundRect(
                    new RectF(end + gap, t, r, b),
                    rad, rad, inactivePaint
            );
        }

        if (end > start) {
            c.drawRoundRect(
                    new RectF(start, t, end, b),
                    rad, rad, activePaint
            );
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnim();
        super.onDetachedFromWindow();
    }
}