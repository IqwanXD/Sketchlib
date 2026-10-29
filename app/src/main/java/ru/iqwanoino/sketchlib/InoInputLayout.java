package ru.iqwanoino.sketchlib;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Region;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class InoInputLayout extends RelativeLayout {

	private static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (alpha << 24);
	}

	
	private EditText editText;
	private TextView labelTextView;
	private GradientDrawable borderDrawable;
	
	private float dp;
	
	// Default Warna ON (Fokus) dan OFF (Tidak Fokus)
	private int colorOn = M3.primary;   // aksen M3 saat fokus
	private int colorOff = M3.outline;  // outline M3 saat tidak fokus
	private int currentBorderColor = colorOff;
	
	// Variabel posisi Y animasi label
	private float labelInsideY;  
	private float labelAboveY;   
	private boolean isExpanded = false; 
	private boolean forceActive = false;     // tampilan "fokus" paksa (dipakai dropdown saat popup terbuka)
	private boolean labelAnimating = false;  // true selama label sedang beranimasi
	
	public InoInputLayout(Context context) {
		super(context);
		init(context);
	}
	
	public InoInputLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}
	
	private void init(Context context) {
		dp = context.getResources().getDisplayMetrics().density;
		
		int idEditTextPath = View.generateViewId();
		int idLabelPath = View.generateViewId();
		
		// 1. Setup EditText Default (Multiline & Wrap Content)
		editText = new EditText(context);
		editText.setId(idEditTextPath);
		editText.setTextSize(14);
		editText.setTextColor(M3.onSurface);
		editText.setTypeface(Fonts.regular(context));
		
		editText.setSingleLine(false);
		editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		editText.setGravity(Gravity.TOP | Gravity.START);
		
		editText.setBackgroundColor(Color.TRANSPARENT);
		tintCursor(editText);
		
		int verticalPadding = (int)(16 * dp);
		int horizontalPadding = (int)(16 * dp);
		editText.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
		
		RelativeLayout.LayoutParams pathParams = new RelativeLayout.LayoutParams(
		RelativeLayout.LayoutParams.MATCH_PARENT,
		RelativeLayout.LayoutParams.WRAP_CONTENT
		);
		editText.setMinHeight((int)(56 * dp));
		pathParams.setMargins(0, (int)(8 * dp), 0, (int)(16 * dp));
		editText.setLayoutParams(pathParams);
		
		// 2. Setup Background Border
		borderDrawable = new GradientDrawable();
		borderDrawable.setColor(Color.TRANSPARENT);
		borderDrawable.setCornerRadius(12 * dp);
		borderDrawable.setStroke((int)(1.5f * dp), colorOff);
		
		// 3. Setup Label (TextView)
		labelTextView = new TextView(context);
		labelTextView.setId(idLabelPath);
		labelTextView.setText("Isian");
		labelTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		labelTextView.setTextColor(colorOff);
		labelTextView.setTypeface(Fonts.regular(context));
		labelTextView.setBackgroundColor(Color.TRANSPARENT); 
		labelTextView.setPadding((int)(4 * dp), 0, (int)(4 * dp), 0);
		
		labelTextView.setPivotX(0f);
		labelTextView.setPivotY(0f);
		
		RelativeLayout.LayoutParams labelParams = new RelativeLayout.LayoutParams(
		RelativeLayout.LayoutParams.WRAP_CONTENT,
		RelativeLayout.LayoutParams.WRAP_CONTENT
		);
		labelParams.addRule(RelativeLayout.ALIGN_TOP, idEditTextPath);
		labelParams.addRule(RelativeLayout.ALIGN_START, idEditTextPath);
		labelParams.setMargins((int)(12 * dp), 0, 0, 0);
		labelTextView.setLayoutParams(labelParams);
		
		labelTextView.setVisibility(View.VISIBLE); 
		
		addView(editText);
		addView(labelTextView);
		
		// Listener Sensor Fokus & Perubahan Teks
		editText.setOnFocusChangeListener((v, hasFocus) -> updateLayoutState(hasFocus));
		editText.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
			@Override public void afterTextChanged(Editable s) {
				if (!editText.isFocused()) {
					updateLayoutState(false);
				}
			}
		});
	}
	
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		
		if (editText != null && labelTextView != null) {
			labelAboveY = -(labelTextView.getHeight() / 2f);
			
			// Jika satu baris ditaruh pas di tengah, jika banyak baris ditaruh di top padding
			if ((editText.getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
				labelInsideY = (editText.getHeight() - labelTextView.getHeight()) / 2f;
			} else {
				labelInsideY = editText.getPaddingTop();
			}
			
			// Sinkronkan posisi label setelah layout, supaya field yang sudah berisi teks
			// sejak awal (mis. form settings) langsung tampil dengan label di atas border.
			if (!labelAnimating) {
				labelTextView.setTranslationY(isExpanded ? labelAboveY : labelInsideY);
				float sc = isExpanded ? 0.85f : 1f;
				labelTextView.setScaleX(sc);
				labelTextView.setScaleY(sc);
			}
		}
	}
	
	private void updateLayoutState(boolean focused) {
		final boolean hasFocus = focused || forceActive;
		final boolean hasText = editText.getText().length() > 0;
		int targetColor = hasFocus ? colorOn : colorOff;
		final float targetStrokeWidth = hasFocus ? 2 * dp : 1.5f * dp;
		
		// Belum di-layout (mis. setText sebelum view ditempel): terapkan langsung tanpa animasi,
		// posisi label dirapikan oleh onLayout().
		if (labelTextView.getHeight() == 0) {
			currentBorderColor = targetColor;
			borderDrawable.setStroke((int) targetStrokeWidth, targetColor);
			labelTextView.setTextColor((hasFocus || hasText) ? targetColor : colorOff);
			isExpanded = hasFocus || hasText;
			invalidate();
			return;
		}
		
		ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), currentBorderColor, targetColor);
		colorAnimation.setDuration(150);
		colorAnimation.addUpdateListener(animator -> {
			currentBorderColor = (int) animator.getAnimatedValue();
			borderDrawable.setStroke((int) targetStrokeWidth, currentBorderColor);
			if (hasFocus || hasText) {
				labelTextView.setTextColor(currentBorderColor);
			} else {
				labelTextView.setTextColor(colorOff);
			}
			invalidate();
		});
		colorAnimation.start();
		
		if (hasFocus || hasText) {
			if (!isExpanded) {
				labelAnimating = true;
				labelTextView.animate()
				.translationY(labelAboveY)
				.scaleX(0.85f)
				.scaleY(0.85f)
				.setDuration(150)
				.withEndAction(() -> labelAnimating = false)
				.start();
				isExpanded = true;
			}
		} else {
			if (isExpanded) {
				labelAnimating = true;
				labelTextView.animate()
				.translationY(labelInsideY)
				.scaleX(1.0f)
				.scaleY(1.0f)
				.setDuration(150)
				.withEndAction(() -> labelAnimating = false)
				.start();
				isExpanded = false;
			}
		}
	}
	
	@Override
	protected void dispatchDraw(Canvas canvas) {
		if (editText != null) {
			borderDrawable.setBounds(
			editText.getLeft(),
			editText.getTop(),
			editText.getRight(),
			editText.getBottom()
			);
			
			if (labelTextView != null && isExpanded) {
				canvas.save();
				
				float labelLeft = labelTextView.getLeft() + labelTextView.getTranslationX();
				float scaledWidth = labelTextView.getWidth() * 0.85f;
				
				canvas.clipRect(
				labelLeft, 
				editText.getTop() - (4 * dp), 
				labelLeft + scaledWidth, 
				editText.getTop() + (4 * dp), 
				Region.Op.DIFFERENCE
				);
				
				borderDrawable.draw(canvas);
				canvas.restore();
			} else {
				borderDrawable.draw(canvas);
			}
		}
		
		super.dispatchDraw(canvas);
	}
	
	// ==========================================
	//            METODE SETTER & GETTER
	// ==========================================
	
	// Get komponen inti jika butuh memodifikasi properti bawaan dasar lainnya
	/**
	 * Kursor, pegangan seleksi, dan sorotan teks memakai warna aksen aplikasi (withAlpha(M3.primary, 0xFF)),
	 * bukan warna aksen sistem/dynamic color perangkat yang dipakai EditText secara default.
	 */
	public static void tintCursor(EditText e) {
		e.setHighlightColor(withAlpha(M3.primary, 0x59));
		if (Build.VERSION.SDK_INT < 29) return;   // API di bawah 29 tidak punya setter kursor/pegangan
		float dp = e.getResources().getDisplayMetrics().density;

		GradientDrawable cursor = new GradientDrawable();
		cursor.setColor(withAlpha(M3.primary, 0xFF));
		cursor.setCornerRadius(dp);
		cursor.setSize(Math.round(2 * dp), Math.round(22 * dp));
		e.setTextCursorDrawable(cursor);

		int d = Math.round(18 * dp);
		e.setTextSelectHandleLeft(handle(d));
		e.setTextSelectHandleRight(handle(d));
		e.setTextSelectHandle(handle(d));
	}

	private static GradientDrawable handle(int size) {
		GradientDrawable g = new GradientDrawable();
		g.setShape(GradientDrawable.OVAL);
		g.setColor(withAlpha(M3.primary, 0xFF));
		g.setSize(size, size);
		return g;
	}

	public EditText getEditText() { return editText; }
	public TextView getLabelTextView() { return labelTextView; }
	
	// Set Teks Label (Judul atas/Hint)
	public void setLabelText(String text) {
		labelTextView.setText(text);
		requestLayout();
		invalidate();
	}
	
	// Ambil & Set Input Teks Masukan Utama
	public String getText() {
		return editText.getText().toString();
	}
	
	public void setText(String text) {
		editText.setText(text);
		updateLayoutState(editText.isFocused());
	}
	
	// Mengatur Warna Tema (Fokus & Tidak Fokus)
	public void setColors(int colorOn, int colorOff) {
		this.colorOn = colorOn;
		this.colorOff = colorOff;
		this.currentBorderColor = editText.isFocused() ? colorOn : colorOff;
		updateLayoutState(editText.isFocused());
	}
	
	// Mengatur Warna Teks Masukan
	public void setTextColor(int color) {
		editText.setTextColor(color);
	}
	
	// Mengatur Radius Sudut Kotak Border (dalam satuan DP)
	public void setCornerRadius(float radiusInDp) {
		borderDrawable.setCornerRadius(radiusInDp * dp);
		invalidate();
	}
	
	// Mengatur Ukuran Ukuran Font Teks Masukan (dalam satuan SP)
	public void setTextSize(float sizeInSp) {
		editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeInSp);
	}
	
	// Mengatur Ukuran Ukuran Font Label Atas (dalam satuan SP)
	public void setLabelSize(float sizeInSp) {
		labelTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeInSp);
		requestLayout();
		invalidate();
	}
	
	// Mengatur Mode Satu Baris (Single Line) atau Banyak Baris (Multiline) via Boolean
	public void setIsMultiline(boolean multiline) {
		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) editText.getLayoutParams();
		if (multiline) {
			editText.setSingleLine(false);
			editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
			editText.setGravity(Gravity.TOP | Gravity.START);
			params.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
			editText.setPadding((int)(16 * dp), (int)(16 * dp), (int)(16 * dp), (int)(16 * dp));
		} else {
			editText.setSingleLine(true);
			editText.setInputType(InputType.TYPE_CLASS_TEXT);
			editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
			params.height = (int)(56 * dp); // Kunci tinggi konstan di 56dp jika satu baris
			editText.setPadding((int)(16 * dp), 0, (int)(16 * dp), 0);
		}
		editText.setLayoutParams(params);
		requestLayout();
		invalidate();
	}
	
	// Mengatur Jumlah Baris Statis / Tinggi Default Awal Kolom
	public void setLines(int lines) {
		editText.setLines(lines);
		requestLayout();
	}
	
	// Mengatur Batas Maksimal Baris Ketikan (Max Lines)
	public void setMaxLines(int maxLines) {
		editText.setMaxLines(maxLines);
		requestLayout();
	}
	
	/** Paksa tampilan "fokus" (border + label berwarna aksen) tanpa fokus asli, mis. saat popup dropdown terbuka. */
	public void setActive(boolean active) {
		forceActive = active;
		updateLayoutState(editText.isFocused());
	}
	
	/** Margin atas/bawah EditText di dalam layout (dp). Atas minimal ~8dp supaya label tidak terpotong. */
	public void setVerticalMargins(int topDp, int bottomDp) {
		RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) editText.getLayoutParams();
		p.setMargins(0, (int) (topDp * dp), 0, (int) (bottomDp * dp));
		editText.setLayoutParams(p);
		requestLayout();
	}
	
	/** Atur InputType (mode satu baris dipertahankan selama flag MULTI_LINE tidak dipakai). */
	public void setInputType(int type) {
		editText.setInputType(type);
		requestLayout();
	}
	
	public void setMinHeight(int minHeightInPx) {
		if (editText != null) {
			editText.setMinHeight(minHeightInPx);
			requestLayout();
		}
	}
	
}
