package ru.iqwanoino.sketchlib;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.PathInterpolator;
import android.widget.CompoundButton;

public class MaterialSwitch extends CompoundButton {
	
	private final float density;
	private final android.animation.ArgbEvaluator colorEval = new android.animation.ArgbEvaluator();
	
	private float progress = 0f;
	private ValueAnimator animator;
	
	private int switchWidth;
	private int switchHeight;
	
	private Paint trackPaint;
	private Paint thumbPaint;
	private TextPaint textPaint;
	
	/* COLORS */
	private int trackActiveColor   = 0xFF0D47A1; 
	private int trackInactiveColor = Color.TRANSPARENT;
	
	private int trackActiveStrokeColor   = 0xFF0D47A1;
	private int trackInactiveStrokeColor = 0xFF6E6E6E;
	
	private int thumbActiveColor   = 0xFFFFFFFF;
	private int thumbInactiveColor = 0xFF6E6E6E;
	
	/* ICON */
	private boolean showChecklist = false;
	private Drawable thumbIcon;
	private int thumbIconColor = 0xFF0D47A1;
	
	/* STATE FEATURE (RIPPLE REMOVED) */
	private boolean isPressed = false;
	private float pressProgress = 0f; 
	private ValueAnimator pressAnimator;
	
	public MaterialSwitch(Context c) {
		this(c, null);
	}
	
	public MaterialSwitch(Context c, AttributeSet a) {
		super(c, a);
		density = getResources().getDisplayMetrics().density;
		init();
	}
	
	private void init() {
		trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		textPaint  = new TextPaint(Paint.ANTI_ALIAS_FLAG);
		textPaint.setTypeface(Fonts.regular(getContext()));
		
		setButtonDrawable(null);
		// Mengeset background menjadi null agar tidak ada background ripple bawaan
		setBackground(null);
		
		progress = isChecked() ? 1f : 0f;
	}
	
	@Override
	public void setChecked(boolean checked) {
		if (checked == isChecked()) return;
		super.setChecked(checked);
		animateTo(checked ? 1f : 0f);
	}
	
	/**
	 * Set state tanpa animasi geser. Dipakai saat saklar baru dibuat (layar dibuka atau
	 * dibangun ulang karena ganti tema), supaya saklar yang sudah ON langsung tampil ON,
	 * bukan mulai dari OFF lalu bergeser ke ON.
	 */
	public void setCheckedImmediate(boolean checked) {
		if (animator != null) animator.cancel();
		super.setChecked(checked);
		progress = checked ? 1f : 0f;
		invalidate();
	}
	
	private void animateTo(float target) {
		if (animator != null) animator.cancel();
		
		animator = ValueAnimator.ofFloat(progress, target);
		animator.setDuration(420); 
		animator.setInterpolator(new PathInterpolator(0.25f, 1f, 0.2f, 1f));
		
		animator.addUpdateListener(v -> {
			progress = (float) v.getAnimatedValue();
			invalidate();
		});
		animator.start();
	}
	
	private void animatePress(float target) {
		if (pressAnimator != null) pressAnimator.cancel();
		pressAnimator = ValueAnimator.ofFloat(pressProgress, target);
		pressAnimator.setDuration(180); 
		pressAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
		pressAnimator.addUpdateListener(v -> {
			pressProgress = (float) v.getAnimatedValue();
			invalidate();
		});
		pressAnimator.start();
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (!isEnabled()) {
			return false;
		}
		
		switch (e.getAction()) {
			case MotionEvent.ACTION_DOWN:
				getParent().requestDisallowInterceptTouchEvent(true);
				isPressed = true;
				animatePress(1f);
				break;
				
			case MotionEvent.ACTION_UP:
				getParent().requestDisallowInterceptTouchEvent(false);
				if (isPressed) {
					isPressed = false;
					animatePress(0f);
					toggle();
				}
				break;
				
			case MotionEvent.ACTION_CANCEL:
				getParent().requestDisallowInterceptTouchEvent(false);
				isPressed = false;
				animatePress(0f);
				break;
		}
		
		super.onTouchEvent(e);
		return true;
	}
	
	@Override
	protected void onMeasure(int w, int h) {
		switchWidth  = (int) (52 * density);
		switchHeight = (int) (32 * density);
		
		textPaint.setTextSize(getTextSize());
		CharSequence text = getText();
		
		int width = switchWidth + getPaddingLeft() + getPaddingRight();
		if (!TextUtils.isEmpty(text)) {
			width += textPaint.measureText(text.toString()) + 12 * density;
		}
		
		int height = (int) (48 * density); 
		
		setMeasuredDimension(
			resolveSize(width, w),
			resolveSize(height, h)
		);
	}
	
	@Override
	protected void onDraw(Canvas c) {
		float switchLeft;
		float textX;
		CharSequence text = getText();
		
		if ((getGravity() & 8388615) == 5) {
			switchLeft = getWidth() - getPaddingRight() - switchWidth;
			textX = switchLeft - (12 * density) - textPaint.measureText(text != null ? text.toString() : "");
		} else {
			textX = getPaddingLeft();
			switchLeft = getWidth() - getPaddingRight() - switchWidth;
		}
		
		/* TEXT */
		if (!TextUtils.isEmpty(text)) {
			textPaint.setColor(getCurrentTextColor());
			textPaint.setTextSize(getTextSize());
			Paint.FontMetrics fm = textPaint.getFontMetrics();
			c.drawText(
				text.toString(),
				textX,
				(getHeight() / 2f) - ((fm.ascent + fm.descent) / 2f),
				textPaint
			);
		}
		
		float stroke = 2f * density;
		float top = (getHeight() - switchHeight) / 2f;
		
		RectF track = new RectF(
			switchLeft,
			top,
			switchLeft + switchWidth,
			top + switchHeight
		);
		
		float radius = switchHeight / 2f;
		
		/* TRACK FILL */
		trackPaint.setStyle(Paint.Style.FILL);
		trackPaint.setColor((Integer) colorEval.evaluate(
			progress,
			trackInactiveColor,
			trackActiveColor
		));
		c.drawRoundRect(track, radius, radius, trackPaint);
		
		/* TRACK STROKE */
		trackPaint.setStyle(Paint.Style.STROKE);
		trackPaint.setStrokeWidth(stroke);
		trackPaint.setColor((Integer) colorEval.evaluate(
			progress,
			trackInactiveStrokeColor,
			trackActiveStrokeColor
		));
		RectF strokeRect = new RectF(track);
		strokeRect.inset(stroke / 2f, stroke / 2f);
		c.drawRoundRect(strokeRect, radius, radius, trackPaint);
		
		/* === RE-ENGINEERED DYNAMIC STRETCH THUMB === */
		float offRadius = 8f * density;
		float onRadius  = 11.5f * density; 
		float currentRadius = offRadius + (onRadius - offRadius) * progress;
		
		float pressExpand = 1f * density * pressProgress;
		currentRadius += pressExpand;
		
		float startX = switchLeft + 16 * density;
		float endX   = switchLeft + switchWidth - 16 * density;
		float cx     = startX + (endX - startX) * progress;
		
		float dynamicStretch = (float) Math.sin(progress * Math.PI) * 7.5f * density;
		
		float thumbTop    = (getHeight() / 2f) - currentRadius;
		float thumbBottom = (getHeight() / 2f) + currentRadius;
		float thumbLeft, thumbRight;
		
		if (animator != null && animator.isRunning()) {
			if (isChecked()) {
				thumbLeft  = cx - currentRadius - dynamicStretch;
				thumbRight = cx + currentRadius;
			} else {
				thumbLeft  = cx - currentRadius;
				thumbRight = cx + currentRadius + dynamicStretch;
			}
		} else {
			thumbLeft  = cx - currentRadius;
			thumbRight = cx + currentRadius;
		}
		
		RectF thumbRect = new RectF(thumbLeft, thumbTop, thumbRight, thumbBottom);
		
		thumbPaint.setStyle(Paint.Style.FILL);
		thumbPaint.setColor((Integer) colorEval.evaluate(
			progress,
			thumbInactiveColor,
			thumbActiveColor
		));
		c.drawRoundRect(thumbRect, currentRadius, currentRadius, thumbPaint);
		
		/* ICON */
		if (showChecklist && thumbIcon != null && progress > 0.0f) {
			int iconSize = (int) (15 * density);
			
			int l = (int) (cx - iconSize / 2f);
			int t = (int) (getHeight() / 2f - iconSize / 2f);
			
			thumbIcon.setBounds(l, t, l + iconSize, t + iconSize);
			thumbIcon.setColorFilter(thumbIconColor, PorterDuff.Mode.SRC_IN);
			
			thumbIcon.setAlpha((int) (progress * 255));
			thumbIcon.draw(c);
		}
	}
	
	/* PUBLIC API */
	public void showChecklist(boolean b) {
		showChecklist = b;
		invalidate();
	}
	
	public void setThumbIcon(Drawable d) {
		thumbIcon = d;
		invalidate();
	}
	
	public void setThumbIconColor(int c) {
		thumbIconColor = c;
		invalidate();
	}
	
	public void setActiveColor(int c) {
		trackActiveColor = c;
		invalidate();
	}
	
	public void setInactiveColor(int c) {
		trackInactiveColor = c;
		invalidate();
	}
	
	public void setStrokeActiveColor(int c) {
		trackActiveStrokeColor = c;
		invalidate();
	}
	
	public void setStrokeInactiveColor(int c) {
		trackInactiveStrokeColor = c;
		invalidate();
	}
	
	public void setThumbActiveColor(int c) {
		thumbActiveColor = c;
		invalidate();
	}
	
	public void setThumbInactiveColor(int c) {
		thumbInactiveColor = c;
		invalidate();
	}
}
