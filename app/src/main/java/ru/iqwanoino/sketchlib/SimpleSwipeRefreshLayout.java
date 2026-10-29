package ru.iqwanoino.sketchlib;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ListView;

public class SimpleSwipeRefreshLayout extends FrameLayout {

    public interface OnRefreshListener {
        void onRefresh();
    }

    private static final float DRAG_RATE = 0.21f;
    private static final int ANIM_DURATION = 250;
    private static final int DEFAULT_REFRESH_TRIGGER_DP = 72;

    public static final int DEFAULT = 1;
    public static final int LARGE = 0;

    private View targetView;
    private FrameLayout indicatorContainer;
    private InoLoadingIndicator loadingIndicator;

    private int touchSlop;
    private float initialDownY;
    private boolean isBeingDragged;
    private boolean refreshing;
    private OnRefreshListener listener;

    private int refreshTrigger;
    private int indicatorTotalSize;
    private int size = LARGE;

    private GradientDrawable circleBg;

    public SimpleSwipeRefreshLayout(Context context) {
        this(context, null);
    }

    public SimpleSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context ctx) {
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        float density = getResources().getDisplayMetrics().density;

        refreshTrigger = (int) (DEFAULT_REFRESH_TRIGGER_DP * density + 0.5f);
        int circleSize = (int) (40 * density);
        int indicatorSize = (int) (30 * density);
        indicatorTotalSize = circleSize;

        // 1. Kontainer melingkar (M3 Floating Surface)
        indicatorContainer = new FrameLayout(getContext());
        circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(Theme.PRIMARY);
        indicatorContainer.setBackground(circleBg);

        if (Build.VERSION.SDK_INT >= 21) {
            indicatorContainer.setElevation(6 * density);
        }

        // 2. Indikator morphing pengganti ProgressBar
        loadingIndicator = new InoLoadingIndicator(getContext());
        loadingIndicator.setColor(Theme.PRIM_CONT);

        FrameLayout.LayoutParams indicatorLp = new FrameLayout.LayoutParams(indicatorSize, indicatorSize);
        indicatorLp.gravity = Gravity.CENTER;
        indicatorContainer.addView(loadingIndicator, indicatorLp);

        LayoutParams containerLp = new LayoutParams(circleSize, circleSize);
        containerLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        containerLp.topMargin = -circleSize;
        addView(indicatorContainer, containerLp);

        indicatorContainer.setVisibility(View.GONE);
        indicatorContainer.setScaleX(0f);
        indicatorContainer.setScaleY(0f);

        setClipToPadding(false);
        setClipChildren(false);   // bayangan indikator di atas daftar tidak terpotong
        setWillNotDraw(false);
    }

    // --- KUSTOMISASI WARNA ---



    // --- UKURAN ---

    public void setSize(int size) {
        if (size != DEFAULT && size != LARGE) return;
        this.size = size;
        updatePresetSizes();
    }



    private void updatePresetSizes() {
        float density = getResources().getDisplayMetrics().density;
        int circleSize;
        int progressSize;

        if (size == LARGE) {
            circleSize = (int) (60 * density + 0.5f);
            progressSize = (int) (40 * density + 0.5f);
            refreshTrigger = (int) (120 * density + 0.5f);
        } else {
            circleSize = (int) (40 * density + 0.5f);
            progressSize = (int) (30 * density + 0.5f);
            refreshTrigger = (int) (DEFAULT_REFRESH_TRIGGER_DP * density + 0.5f);
        }

        applyCustomSizes(circleSize, progressSize, refreshTrigger);
    }

    private void applyCustomSizes(int circlePx, int progressPx, int triggerPx) {
        this.refreshTrigger = triggerPx;
        this.indicatorTotalSize = circlePx;

        ViewGroup.LayoutParams containerLp = indicatorContainer.getLayoutParams();
        if (containerLp != null) {
            containerLp.width = circlePx;
            containerLp.height = circlePx;
            if (containerLp instanceof LayoutParams) {
                ((LayoutParams) containerLp).topMargin = -circlePx;
            }
            indicatorContainer.setLayoutParams(containerLp);
        }

        ViewGroup.LayoutParams progressLp = loadingIndicator.getLayoutParams();
        if (progressLp != null) {
            progressLp.width = progressPx;
            progressLp.height = progressPx;
            loadingIndicator.setLayoutParams(progressLp);
        }

        if (!refreshing) {
            indicatorContainer.setTranslationY(0);
        } else {
            animateIndicatorTo(refreshTrigger);
        }
    }

    // --- END UKURAN ---

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() > 1) {
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch != indicatorContainer) {
                    targetView = ch;
                    break;
                }
            }
        } else if (getChildCount() == 1 && getChildAt(0) != indicatorContainer) {
            targetView = getChildAt(0);
        }
    }

    public void setTargetView(View v) {
        this.targetView = v;
        if (indicatorContainer != null) indicatorContainer.bringToFront();
    }

    public void setOnRefreshListener(OnRefreshListener l) {
        this.listener = l;
    }


    public void setRefreshing(boolean refreshing) {
        if (this.refreshing == refreshing) return;
        this.refreshing = refreshing;

        if (refreshing) {
            indicatorContainer.setVisibility(View.VISIBLE);
            indicatorContainer.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            animateIndicatorTo(refreshTrigger);
        } else {
            indicatorContainer.animate()
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(ANIM_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        indicatorContainer.setVisibility(View.GONE);
                        animateIndicatorTo(0);
                        indicatorContainer.animate().setListener(null);
                    }
                }).start();
        }
    }

    private void animateIndicatorTo(int toOffset) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(indicatorContainer, "translationY",
                indicatorContainer.getTranslationY(), toOffset);
        anim.setDuration(ANIM_DURATION);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled() || canChildScrollUp() || refreshing) {
            return false;
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialDownY = ev.getY();
                isBeingDragged = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float y = ev.getY();
                float yDiff = y - initialDownY;
                if (yDiff > touchSlop && !isBeingDragged) {
                    isBeingDragged = true;
                    indicatorContainer.setVisibility(View.VISIBLE);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isBeingDragged = false;
                break;
        }
        return isBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled() || canChildScrollUp() || refreshing) {
            return false;
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialDownY = ev.getY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float y = ev.getY();
                float dy = (y - initialDownY) * DRAG_RATE;
                if (dy < 0) return false;

                float maxDrag = refreshTrigger * 1.5f;
                if (dy > maxDrag) dy = maxDrag;

                moveIndicator((int) dy);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float y = ev.getY();
                float dy = (y - initialDownY) * DRAG_RATE;

                if (dy > refreshTrigger) {
                    setRefreshing(true);
                    if (listener != null) {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                listener.onRefresh();
                            }
                        }, 100);
                    }
                } else {
                    indicatorContainer.animate().scaleX(0f).scaleY(0f).setDuration(150)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                indicatorContainer.setVisibility(View.GONE);
                                moveIndicator(0);
                                indicatorContainer.animate().setListener(null);
                            }
                        }).start();
                }
                isBeingDragged = false;
                return true;
            }
        }
        return super.onTouchEvent(ev);
    }

    private void moveIndicator(int offset) {
        indicatorContainer.setTranslationY(offset);

        if (offset > 0) {
            float progress = (float) offset / refreshTrigger;
            float scale = Math.min(1.0f, progress);
            indicatorContainer.setScaleX(scale);
            indicatorContainer.setScaleY(scale);
        }
    }

    public boolean canChildScrollUp() {
        if (targetView == null) return true;
        if (targetView instanceof ListView) {
            ListView lv = (ListView) targetView;
            if (lv.getChildCount() == 0) return false;
            if (lv.getFirstVisiblePosition() > 0) return true;
            View firstChild = lv.getChildAt(0);
            if (firstChild == null) return false;
            return firstChild.getTop() < lv.getPaddingTop();
        } else {
            return targetView.canScrollVertically(-1);
        }
    }

    // ============================================================
    // InoLoadingIndicator (package-private, satu file)
    // ============================================================
    static class InoLoadingIndicator extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        private int color = Theme.PRIMARY;

        private float morph = 0f;
        private float baseRotation = 0f;
        private float rotationSpeedMultiplier = 0.70f;
        private float lastRotationValue = 0f;

        private int currentShapeIndex = 0;
        private int nextShapeIndex = 1;

        private ValueAnimator morphAnimator;
        private ValueAnimator rotationAnimator;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private Runnable nextShapeRunnable;

        private static final int POINTS = 64;
        private static final int SHAPE_COUNT = 7;

        private float[][] shapes;

        private static final int HOLD_TIME = 750;
        private static final int MORPH_TIME = 700;
        private static final int ROTATION_DURATION = 2500;

        private boolean isMorphing = false;
        private boolean isAnimationRunning = false;

        public InoLoadingIndicator(Context c) {
            super(c);
            init();
        }

        public InoLoadingIndicator(Context c, AttributeSet a) {
            super(c, a);
            init();
        }

        public InoLoadingIndicator(Context c, AttributeSet a, int s) {
            super(c, a, s);
            init();
        }

        private void init() {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            generateShapes();
            setupAnimators();
        }

        private void generateShapes() {
            shapes = new float[SHAPE_COUNT][POINTS];

            for (int i = 0; i < POINTS; i++) {
                float a = (float) (i * Math.PI * 2 / POINTS);

                shapes[0][i] = 1f + 0.11f * (float) Math.sin(a * 9);
                shapes[1][i] = 1f + 0.058f * (float) Math.sin(a * 10);
                shapes[2][i] = 1f + 0.048f * (float) Math.sin(a * 5);

                float stretch = 0.13f * (float) Math.cos(a * 2);
                shapes[3][i] = 1f - stretch;

                shapes[4][i] = 1f + 0.097f * (float) Math.sin(a * 8);

                float s = (float) Math.sin(a * 4);
                shapes[5][i] = 1f + 0.12f * s - 0.04f * s * s;

                float base = 1f + 0.090f * (float) Math.cos(a * 4);
                float verticalDrop = 0.16f * (float) Math.cos(a * 2);
                float pinch = 0.13f * (float) Math.cos(a * 2) * (float) Math.cos(a * 2);
                shapes[6][i] = base - verticalDrop - pinch;
            }
        }

        private void setupAnimators() {
            rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
            rotationAnimator.setDuration(ROTATION_DURATION);
            rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
            rotationAnimator.setInterpolator(new LinearInterpolator());
            rotationAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float value = (float) animation.getAnimatedValue();
                    float delta = value - lastRotationValue;
                    if (delta < 0) delta += 360f;

                    baseRotation += delta * rotationSpeedMultiplier;
                    lastRotationValue = value;
                    invalidate();
                }
            });

            nextShapeRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isMorphing && isAnimationRunning) {
                        startMorph();
                    }
                }
            };
        }

        private void startAnimation() {
            if (isAnimationRunning) return;
            isAnimationRunning = true;

            lastRotationValue = 0f;
            if (rotationAnimator != null && !rotationAnimator.isRunning()) {
                rotationAnimator.start();
            }

            handler.removeCallbacks(nextShapeRunnable);
            handler.postDelayed(nextShapeRunnable, HOLD_TIME);
        }

        private void stopAnimation() {
            isAnimationRunning = false;
            handler.removeCallbacksAndMessages(null);

            if (rotationAnimator != null) rotationAnimator.cancel();
            if (morphAnimator != null) morphAnimator.cancel();

            isMorphing = false;
            morph = 0f;
        }

        private void startMorph() {
            if (!isAnimationRunning) return;

            isMorphing = true;
            nextShapeIndex = (currentShapeIndex + 1) % SHAPE_COUNT;

            animateRotationSpeed(rotationSpeedMultiplier, 1.95f, 23);

            if (morphAnimator != null) morphAnimator.cancel();

            morphAnimator = ValueAnimator.ofFloat(0f, 1f);
            morphAnimator.setDuration(MORPH_TIME);
            morphAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

            morphAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    morph = (float) animation.getAnimatedValue();
                    invalidate();
                }
            });

            morphAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!isAnimationRunning) return;

                    currentShapeIndex = nextShapeIndex;
                    morph = 0f;
                    isMorphing = false;
                    invalidate();

                    animateRotationSpeed(rotationSpeedMultiplier, 0.70f, 45);
                    handler.postDelayed(nextShapeRunnable, HOLD_TIME);
                }
            });

            morphAnimator.start();
        }

        private void animateRotationSpeed(float from, float to, long duration) {
            ValueAnimator speedAnim = ValueAnimator.ofFloat(from, to);
            speedAnim.setDuration(duration);
            speedAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            speedAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    rotationSpeedMultiplier = (float) a.getAnimatedValue();
                }
            });
            speedAnim.start();
        }

        @Override
        protected void onVisibilityChanged(View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            if (visibility == VISIBLE) {
                startAnimation();
            } else {
                stopAnimation();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (getVisibility() == VISIBLE) {
                startAnimation();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            stopAnimation();
        }

        public void setColor(int c) {
            color = c;
            paint.setColor(c);
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int widthMode = MeasureSpec.getMode(w);
            int heightMode = MeasureSpec.getMode(h);

            int widthSize = MeasureSpec.getSize(w);
            int heightSize = MeasureSpec.getSize(h);

            int defaultSize = dp(30);

            int width = (widthMode == MeasureSpec.EXACTLY) ? widthSize : defaultSize;
            int height = (heightMode == MeasureSpec.EXACTLY) ? heightSize : defaultSize;

            int size = Math.min(width, height);
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();

            float cx = w / 2f;
            float cy = h / 2f;
            float radius = Math.min(w, h) * 0.35f;

            float[] currentShape = shapes[currentShapeIndex];
            float[] nextShape = shapes[nextShapeIndex];

            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(baseRotation);

            path.reset();

            for (int i = 0; i < POINTS; i++) {
                float r;
                if (isMorphing) {
                    r = currentShape[i] + (nextShape[i] - currentShape[i]) * morph;
                } else {
                    r = currentShape[i];
                }

                float ang = (float) (i * Math.PI * 2 / POINTS);
                float x = (float) Math.cos(ang) * radius * r;
                float y = (float) Math.sin(ang) * radius * r;

                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    float prevAng = (float) ((i - 1) * Math.PI * 2 / POINTS);
                    float prevR = isMorphing
                            ? (currentShape[i - 1] + (nextShape[i - 1] - currentShape[i - 1]) * morph)
                            : currentShape[i - 1];
                    float prevX = (float) Math.cos(prevAng) * radius * prevR;
                    float prevY = (float) Math.sin(prevAng) * radius * prevR;

                    float cx1 = prevX + (x - prevX) / 2f;
                    float cy1 = prevY + (y - prevY) / 2f;

                    path.quadTo(prevX, prevY, cx1, cy1);
                }
            }

            path.close();
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        private int dp(int v) {
            return (int) (v * getResources().getDisplayMetrics().density);
        }
    }
}