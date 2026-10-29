package ru.iqwanoino.sketchlib;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class InoSaturationValuePicker extends View {
	
	private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	
	private float hue = 0f;
	private float saturation = 1f;
	private float value = 1f;
	
	private float thumbRadius = 24f;
	private float barHeight = 30f;
	
	private OnValueModifierListener listener;
	
	public InoSaturationValuePicker(Context c) {
		super(c);
		init();
	}
	
	public InoSaturationValuePicker(Context c, AttributeSet a) {
		super(c, a);
		init();
	}
	
	private void init() {
		selectorPaint.setColor(Color.WHITE);
		strokePaint.setStyle(Paint.Style.STROKE);
		strokePaint.setStrokeWidth(3f);
		strokePaint.setColor(Color.parseColor("#BDBDBD"));
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		int w = getWidth();
		int h = getHeight();
		
		float padding = thumbRadius + 6f;
		float barLeft = padding;
		float barRight = w - padding;
		
		float centerY = h / 2f;
		float barTop = centerY - (barHeight / 2f);
		float barBottom = centerY + (barHeight / 2f);
		
		int pureColor = Color.HSVToColor(new float[]{hue, saturation, 1f});
		
		Shader valueShader = new LinearGradient(
			barLeft, 0, barRight, 0,
			Color.BLACK, pureColor,
			Shader.TileMode.CLAMP
		);
		paint.setShader(valueShader);
		
		RectF rect = new RectF(barLeft, barTop, barRight, barBottom);
		canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, paint);
		
		float selectorX = barLeft + (value * (barRight - barLeft));
		
		canvas.drawCircle(selectorX, centerY, thumbRadius, selectorPaint);
		canvas.drawCircle(selectorX, centerY, thumbRadius, strokePaint);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		int w = getWidth();
		float padding = thumbRadius + 6f;
		float barLeft = padding;
		float barRight = w - padding;
		
		float touchX = e.getX();
		
		value = (touchX - barLeft) / (barRight - barLeft);
		value = Math.max(0f, Math.min(value, 1f));
		
		invalidate();
		
		if (listener != null) {
			listener.onValueChanged(value);
		}
		
		return true;
	}
	
	public void setThumbRadius(float radiusInPx) {
		this.thumbRadius = radiusInPx;
		invalidate();
	}
	
	public void setBarHeight(float heightInPx) {
		this.barHeight = heightInPx;
		invalidate();
	}
	
	public void updateHueAndSaturation(float hue, float saturation) {
		this.hue = hue;
		this.saturation = saturation;
		invalidate();
	}
	
	public void setValue(float value) {
		this.value = Math.max(0f, Math.min(value, 1f));
		invalidate();
	}
	
	public void setOnValueModifierListener(OnValueModifierListener l) {
		this.listener = l;
	}
	
	public interface OnValueModifierListener {
		void onValueChanged(float value);
	}
}
