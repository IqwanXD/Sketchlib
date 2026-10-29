package ru.iqwanoino.sketchlib;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.Collections;

/**
 * Drawer mode PUSH (sama persis dengan DrawerLayoutView di MainActivity):
 * konten terdorong ke kanan + mengecil + sudut membulat, drawer paralaks halus,
 * drag realtime, swipe dari tepi kiri, fling berdasarkan velocity, dan animasi
 * yang bisa "ditangkap" jari. Tanpa AndroidX.
 */
public class PushDrawerLayout extends FrameLayout {

    public interface Listener {
        /** Dipanggil sekali saat drawer mulai terbuka (dari posisi tertutup). */
        void onDrawerOpening();
    }

    private static final float PARALLAX = 0.25f;
    private static final float SHRINK = 0.06f;
    private static final int EDGE_DP = 40;

    private FrameLayout contentHolder;
    private View scrim, drawerV;
    private final int drawerWidth, slop, edge;
    private final float density;

    private float offset = 0f;
    private boolean targetOpen;
    private float downX, downY, lastX;
    private int activeId = -1;
    private boolean dragging, candidate, layered;
    private boolean grabbed, grabMoved;
    private float resumeTarget;
    private VelocityTracker vt;
    private ValueAnimator anim;
    private Listener listener;

    public PushDrawerLayout(Context c, int drawerWidthPx) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        drawerWidth = drawerWidthPx;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        edge = dp(EDGE_DP);
    }

    public void setListener(Listener l) { listener = l; }

    public void setViews(View content, View drawerView) {
        removeAllViews();

        drawerV = drawerView;
        drawerV.setClickable(true);
        addView(drawerV, new LayoutParams(drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT));

        contentHolder = new FrameLayout(getContext());
        contentHolder.addView(content, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim = new View(getContext());
        scrim.setBackgroundColor(Color.BLACK);
        scrim.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { close(); }
        });
        contentHolder.addView(scrim, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 21) {
            contentHolder.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(28) * offset);
                }
            });
        }
        addView(contentHolder, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setOffset(0f);
    }

    public boolean isOpen() { return offset > 0f; }

    private void setOffset(float o) {
        float prev = offset;
        offset = Math.max(0f, Math.min(1f, o));

        float sc = 1f - SHRINK * offset;
        contentHolder.setPivotX(0f);
        contentHolder.setPivotY(contentHolder.getHeight() / 2f);
        contentHolder.setScaleX(sc);
        contentHolder.setScaleY(sc);
        contentHolder.setTranslationX(drawerWidth * offset);
        if (Build.VERSION.SDK_INT >= 21 && (offset > 0f || prev > 0f)) {
            contentHolder.setClipToOutline(offset > 0f);
            contentHolder.invalidateOutline();
            // Bayangan tipis: konten terasa menumpuk di atas drawer
            contentHolder.setElevation(dp(14) * offset);
        }

        drawerV.setTranslationX(-drawerWidth * PARALLAX * (1f - offset));
        drawerV.setVisibility(offset > 0f ? View.VISIBLE : View.INVISIBLE);

        scrim.setAlpha(offset * 0.35f);
        scrim.setVisibility(offset > 0f ? View.VISIBLE : View.GONE);

        if (prev == 0f && offset > 0f && listener != null) listener.onDrawerOpening();
    }

    public void open() { animateTo(1f, 0f); }
    public void close() { animateTo(0f, 0f); }
    /** Toggle berdasarkan TUJUAN animasi, bukan posisi saat ini, supaya spam tombol konsisten. */
    public void toggle() { animateTo(targetOpen ? 0f : 1f, 0f); }

    // ----- animasi -----

    private void startMotion() {
        if (!layered) {
            layered = true;
            contentHolder.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }

    private void endMotion() {
        if (layered) {
            layered = false;
            contentHolder.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    private void cancelAnim() {
        if (anim != null) {
            ValueAnimator a = anim;
            anim = null;
            a.cancel();
        }
    }

    private void animateTo(final float target, float vx) {
        cancelAnim();
        targetOpen = target > 0.5f;
        float dist = Math.abs(target - offset);
        if (dist < 0.001f) {
            setOffset(target);
            endMotion();
            return;
        }
        startMotion();

        long dur = 120 + (long) (200 * dist);
        if (vx != 0f && (vx > 0f) == (target > offset)) {
            long byVelocity = (long) (dist * drawerWidth / Math.abs(vx) * 1000f * 2f);
            dur = Math.max(110, Math.min(dur, byVelocity));
        }

        final ValueAnimator a = ValueAnimator.ofFloat(offset, target);
        a.setDuration(dur);
        a.setInterpolator(new DecelerateInterpolator(1.4f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator v) {
                setOffset((Float) v.getAnimatedValue());
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;
            @Override public void onAnimationCancel(Animator an) { canceled = true; }
            @Override public void onAnimationEnd(Animator an) {
                if (canceled) return;
                setOffset(target);
                if (anim == a) {
                    anim = null;
                    endMotion();
                }
            }
        });
        anim = a;
        a.start();
    }

    // ----- sentuhan -----

    private void recycleTracker() {
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
    }

    private boolean tryStartDrag(MotionEvent ev) {
        int i = ev.findPointerIndex(activeId);
        if (i < 0) return false;
        float x = ev.getX(i), y = ev.getY(i);
        float dx = x - downX, dy = y - downY;
        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) && (offset > 0f || dx > 0f)) {
            dragging = true;
            lastX = x;
            startMotion();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        return false;
    }

    private void finishTouch() {
        if (dragging) {
            float vx = 0f;
            if (vt != null) {
                vt.computeCurrentVelocity(1000);
                vx = vt.getXVelocity();
            }
            boolean open;
            if (grabbed && !grabMoved) open = resumeTarget > 0.5f;
            else if (Math.abs(vx) > dp(400)) open = vx > 0f;
            else open = offset > 0.5f;
            animateTo(open ? 1f : 0f, vx);
        }
        dragging = false;
        candidate = false;
        grabbed = false;
        grabMoved = false;
        activeId = -1;
        recycleTracker();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            recycleTracker();
            vt = VelocityTracker.obtain();
            activeId = ev.getPointerId(0);
            downX = lastX = ev.getX();
            downY = ev.getY();
            dragging = false;
            grabbed = false;
            grabMoved = false;
            if (anim != null) {
                resumeTarget = targetOpen ? 1f : 0f;
                cancelAnim();
                dragging = true;
                grabbed = true;
                candidate = true;
                startMotion();
                vt.addMovement(ev);
                return true;
            }
            candidate = offset > 0f || downX <= edge;
        }
        if (vt != null) vt.addMovement(ev);
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (!dragging && candidate) tryStartDrag(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) {
                    candidate = false;
                    activeId = -1;
                    recycleTracker();
                }
                break;
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && candidate) tryStartDrag(ev);
                if (dragging) {
                    int i = ev.findPointerIndex(activeId);
                    if (i < 0) break;
                    float x = ev.getX(i);
                    if (Math.abs(x - downX) > slop) grabMoved = true;
                    float dx = x - lastX;
                    lastX = x;
                    if (dx != 0f) setOffset(offset + dx / drawerWidth);
                }
                break;
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = ev.getActionIndex();
                if (ev.getPointerId(idx) == activeId) {
                    int ni = idx == 0 ? 1 : 0;
                    if (ni < ev.getPointerCount()) {
                        activeId = ev.getPointerId(ni);
                        lastX = ev.getX(ni);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishTouch();
                break;
        }
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (Build.VERSION.SDK_INT >= 29) {
            int len = dp(200);
            int top = Math.max(0, ((b - t) - len) / 2);
            setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, top, edge, top + len)));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAnim();
        endMotion();
        recycleTracker();
    }

    private int dp(float v) { return (int) (v * density + 0.5f); }
}
