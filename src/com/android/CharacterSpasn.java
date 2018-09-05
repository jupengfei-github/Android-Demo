package com.android;

import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.text.style.ReplacementSpan;
import android.text.style.ImageSpan;
import android.text.style.ForegroundColorSpan;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;

import android.content.Context;
import android.widget.TextView;
import android.util.Property;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

import android.annotation.ColorInt;
import android.text.TextPaint;

import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.view.animation.LinearInterpolator;

import android.graphics.drawable.Drawable;
import android.animation.FloatEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* package */ class FrameSpan extends ReplacementSpan {
	private int mWidth;

	@Override
	public void	draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
		canvas.drawText(text, start, end, x, y, paint);

		Paint.Style style = paint.getStyle();
		paint.setStyle(Paint.Style.STROKE);

		int color = paint.getColor();
		paint.setColor(Color.RED);

		canvas.drawRect(x, top, x + mWidth, bottom, paint);

		paint.setStyle(style);
		paint.setColor(color);
	}

	@Override
	public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
		mWidth = (int)paint.measureText(text, start, end);
		return mWidth;
	}
}

/* package */ class VerticalImageSpan extends ImageSpan {
	private int mWidth;
	private int mSelfWidth, mSelfHeight;

	public VerticalImageSpan (Context context, int resId) {
		super(context, resId);
	}

	@Override
	public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
		mWidth = (int)paint.measureText(text, start, end);

		Paint.FontMetricsInt fontMetrics = paint.getFontMetricsInt();
		int fontHeight = fontMetrics.bottom - fontMetrics.top;

		Drawable d = getDrawable();
		if (d == null)
			return mWidth;

		int width  = d.getIntrinsicWidth();
		int height = d.getIntrinsicHeight();

		mSelfWidth  = mWidth;
		mSelfHeight = (int)Math.max(height, fontHeight);

		if (fm != null) {
			int off = Math.abs(fontMetrics.ascent + fontMetrics.descent);
			fm.top = -(mSelfHeight/2 + 0);
			fm.bottom = (mSelfHeight/2 - 0);

			fm.ascent = fontMetrics.ascent;
			fm.descent = fontMetrics.descent;
		}

		return mSelfWidth;
	}

	@Override
	public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
		Drawable d = getDrawable();
		if (d != null) {
			d.setBounds((int)x, 0, (int)x + mSelfWidth, mSelfHeight);
			d.draw(canvas);
		}

		int offset = (mSelfWidth - mWidth)/2;
		canvas.drawText(text, start, end, x + offset, y, paint);
	}
}

/* package */ class MutableForegroundColorSpan extends CharacterStyle implements UpdateAppearance {
	private int mColor;

	public MutableForegroundColorSpan (int color) {
		mColor = color;
	}

	@ColorInt
	public int getForegroundColor () {
		return mColor;
	}

	public void setForegroundColor (@ColorInt int color) {
		mColor = color;
	}

	public int getAlpha () {
		return Color.alpha(mColor);
	}

	public void setAlpha (int alpha) {
		mColor = (mColor & 0x00FFFFFF) | (alpha << 24);
	}

	@Override
	public void updateDrawState (TextPaint paint) {
		paint.setColor(mColor);
	}
}

/* package */ class RainbowSpan extends CharacterStyle implements UpdateAppearance {
	private int[] mColors;
	private float mTranslateXPercent;

	public RainbowSpan () {
		mColors = new int[]{0xFFFF0000, 0xFFDEE000, 0xFF082322, 0xFF230000, 0xFF00FF00, 0xFF00FF23, 0xFF0000FF, 0xFF0023FF, 0xFF0032EF};
	}

	public void setTranslateXPercent (float translateX) {
		mTranslateXPercent = translateX;
	}

	public float getTranslateXPercent () {
		return mTranslateXPercent;
	}

	@Override
	public void updateDrawState (TextPaint paint) {
		paint.setStyle(Paint.Style.FILL);

		float width = paint.getTextSize() * mColors.length;
		Shader shader = new LinearGradient(0, 0, 0, width, mColors, null, Shader.TileMode.MIRROR);
		Matrix matrix = new Matrix();
		matrix.setRotate(90);
		matrix.postTranslate(width * mTranslateXPercent, 0);
		shader.setLocalMatrix(matrix);
		paint.setShader(shader);
	}
}

/* package */ class SpanGroup {
	private ArrayList<MutableForegroundColorSpan> mSpans = new ArrayList<MutableForegroundColorSpan>();
	private float mAlpha;

	private int[] maps = null;

	public void addSpan (MutableForegroundColorSpan span) {
		mSpans.add(span);
		span.setAlpha((int)(mAlpha * 255));
	}

	public float getAlpha () {
		return mAlpha;
	}

	public void init () {
		int size = mSpans.size();

		maps = new int[size];
		List<Integer> list = new ArrayList<Integer>();

		Random random = new Random();
		for (int i = 0; i < size; i++) {
			int rd;
			do { rd = random.nextInt(size * 10) % size; } while (list.contains(rd));

			maps[i] = rd;
			list.add(rd);
		}
	}

	public void setAlpha (float alpha) {
		mAlpha = alpha;

		int size = mSpans.size();
		float total = size * alpha;

		for (int index = 0; index < size; index++) {
			MutableForegroundColorSpan span = mSpans.get(maps[index]);

			if (total >= 1.0f) {
				span.setAlpha(255);
				total -= 1.0f;
			}
			else {
				span.setAlpha((int)(255 * total));
				total = 0;
			}
		}
	}
}

/* package */ class CharacterSpan {
	static MutableForegroundColorSpan initAnimatedForegroundColorSpan (TextView view, final int startColor, final int endColor) {
		final MutableForegroundColorSpan span = new MutableForegroundColorSpan(startColor);	

		Property<MutableForegroundColorSpan,Integer> property = new Property<MutableForegroundColorSpan,Integer>(Integer.class, "ForegroundColorSpan") {
			@Override
			public Integer get (MutableForegroundColorSpan span) {
				return span.getForegroundColor();
			}

			@Override
			public void set (MutableForegroundColorSpan span, Integer value) {
				span.setForegroundColor(value <= 5?  startColor : endColor);
				view.invalidate();
			}
		};

		ObjectAnimator animator = ObjectAnimator.ofInt(span, property, 0, 10);
		animator.setDuration(1000);
		animator.setRepeatCount(ValueAnimator.INFINITE);
		animator.setRepeatMode(ValueAnimator.REVERSE);
		animator.start();

		return span;
	}

	static RainbowSpan initAnimatedRainbowSpan (TextView view, float start, float end) {
		RainbowSpan span = new RainbowSpan();

		Property<RainbowSpan,Float> property = new Property<RainbowSpan,Float>(Float.class, "RainbowSpan") {
			@Override
			public Float get (RainbowSpan span) {
				return span.getTranslateXPercent();
			}

			@Override
			public void set (RainbowSpan span, Float value) {
				span.setTranslateXPercent(value);
				view.invalidate();
			}
		};

		ObjectAnimator animator = ObjectAnimator.ofFloat(span, property, start, end);
		animator.setInterpolator(new LinearInterpolator());
		animator.setEvaluator(new FloatEvaluator());
		animator.setDuration(1000 * 30);
		animator.setRepeatCount(ValueAnimator.INFINITE);
		animator.setRepeatMode(ValueAnimator.RESTART);
		animator.start();

		return span;
	}

	static MutableForegroundColorSpan initAlphaForgroundColorSpan (TextView view) {
		final MutableForegroundColorSpan span = new MutableForegroundColorSpan(Color.BLACK);	

		Property<MutableForegroundColorSpan,Integer> property = new Property<MutableForegroundColorSpan,Integer>(Integer.class, "ForegroundColorSpan") {
			@Override
			public Integer get (MutableForegroundColorSpan span) {
				return span.getAlpha();
			}

			@Override
			public void set (MutableForegroundColorSpan span, Integer value) {
				span.setAlpha(value);
				view.invalidate();
			}
		};

		ObjectAnimator animator = ObjectAnimator.ofInt(span, property, 0, 255);
		animator.setDuration(1000 * 10);
		animator.setRepeatCount(ValueAnimator.INFINITE);
		animator.setRepeatMode(ValueAnimator.REVERSE);
		animator.start();

		return span;
	}

	static void initSpanGroup (TextView view, List<MutableForegroundColorSpan> objs) {
		SpanGroup group = new SpanGroup();

		for (MutableForegroundColorSpan span : objs)
			group.addSpan(span);

		group.init();
		Property<SpanGroup, Float> property = new Property<SpanGroup, Float>(Float.class, "ForegroundColorSpan") {
			@Override
			public Float get (SpanGroup group) {
				return group.getAlpha();
			}

			@Override
			public void set (SpanGroup group, Float value) {
				group.setAlpha(value);
				view.invalidate();
			}
		};

		ObjectAnimator animator = ObjectAnimator.ofFloat(group, property, 0, 1.0f);
		animator.setDuration(1000 * 3);
		animator.setRepeatCount(ValueAnimator.INFINITE);
		animator.setRepeatMode(ValueAnimator.RESTART);
		animator.start();
	}
}
