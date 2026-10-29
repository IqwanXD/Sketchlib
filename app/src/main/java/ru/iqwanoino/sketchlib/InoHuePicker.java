package ru.iqwanoino.sketchlib;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class InoHuePicker extends View {
	
	private Paint paint;
	private Paint selectorPaint;
	private float centerX, centerY, radius;
	
	private float hue = 0f;
	private float saturation = 1f;
	
	private OnColorChangedListener listener;
	
	public InoHuePicker(Context c) {
		super(c);
		init();
	}
	
	public InoHuePicker(Context c, AttributeSet a) {
		super(c, a);
		init();
	}
	
	private void init() {
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		selectorPaint.setColor(Color.WHITE);
		selectorPaint.setStyle(Paint.Style.FILL);
		
		setLayerType(LAYER_TYPE_SOFTWARE, null);
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		centerX = w / 2f;
		centerY = h / 2f;
		radius = Math.min(w, h) / 2f - 20f;
		
		int[] colors = new int[13];
		float[] hsv = {0f, 1f, 1f};
		for (int i = 0; i < colors.length; i++) {
			hsv[0] = (i * 30) % 360;
			colors[i] = Color.HSVToColor(hsv);
		}
		Shader sweep = new SweepGradient(centerX, centerY, colors, null);
		Shader radial = new RadialGradient(centerX, centerY, radius, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP);
		
		ComposeShader composeShader = new ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER);
		paint.setShader(composeShader);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawCircle(centerX, centerY, radius, paint);
		
		float rad = (float) Math.toRadians(hue);
		float x = centerX + (float) Math.cos(rad) * (saturation * radius);
		float y = centerY + (float) Math.sin(rad) * (saturation * radius);
		
		canvas.drawCircle(x, y, 18f, selectorPaint);
		
		Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		strokePaint.setStyle(Paint.Style.STROKE);
		strokePaint.setStrokeWidth(3f);
		strokePaint.setColor(Color.parseColor("#757575"));
		canvas.drawCircle(x, y, 18f, strokePaint);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		float dx = e.getX() - centerX;
		float dy = e.getY() - centerY;
		float distance = (float) Math.sqrt(dx * dx + dy * dy);
		
		saturation = Math.max(0f, Math.min(distance / radius, 1f));
		
		hue = (float) Math.toDegrees(Math.atan2(dy, dx));
		if (hue < 0) {
			hue += 360f;
		}
		
		invalidate();
		
		if (listener != null) {
			listener.onColorChanged(hue, saturation);
		}
		
		return true;
	}
	
	public void setOnColorChangedListener(OnColorChangedListener l) {
		this.listener = l;
	}
	
	public interface OnColorChangedListener {
		void onColorChanged(float hue, float saturation);
	}
	
	public void setHueAndSaturation(float hue, float saturation) {
		this.hue = hue;
		this.saturation = Math.max(0f, Math.min(saturation, 1f));
		invalidate();
	}
}
