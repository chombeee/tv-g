package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** The soft coloured shape in the top-right corner behind the carousel; it takes the colour of the album art. */
public class HeroBlob extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int color = 0xFF2E7D32;
    private ObjectAnimator breathe;

    public HeroBlob(Context c) {
        super(c);
        setAlpha(0f);
        setVisibility(GONE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        path.reset();
        path.moveTo(w * 0.22f, 0);
        path.lineTo(w, 0);
        path.lineTo(w, h * 0.80f);
        path.cubicTo(w * 0.88f, h * 1.02f, w * 0.62f, h * 0.98f, w * 0.55f, h * 0.78f);
        path.cubicTo(w * 0.47f, h * 0.60f, w * 0.20f, h * 0.64f, w * 0.06f, h * 0.46f);
        path.cubicTo(w * -0.02f, h * 0.34f, w * 0.20f, h * 0.22f, w * 0.16f, h * 0.10f);
        path.cubicTo(w * 0.15f, h * 0.04f, w * 0.20f, 0f, w * 0.22f, 0f);
        path.close();
        setPivotX(w);
        setPivotY(0);
        rebuildShader();
    }

    private void rebuildShader() {
        int h = getHeight();
        if (h <= 0) return;
        int light = blend(color, 0xFFFFFFFF, 0.18f);
        paint.setShader(new LinearGradient(0, 0, 0, h, light, color, Shader.TileMode.CLAMP));
        invalidate();
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Fades the shape in (or out) and moves its colour to {@code target}. */
    public void setAccent(int target, boolean show) {
        if (show) {
            if (getVisibility() != VISIBLE) {
                setVisibility(VISIBLE);
            }
            animate().alpha(0.92f).setDuration(600).start();
            startBreathing();
            final int from = color;
            if (from != target) {
                ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), from, target);
                va.setDuration(700);
                va.addUpdateListener(a -> { color = (Integer) a.getAnimatedValue(); rebuildShader(); });
                va.start();
            }
        } else {
            animate().alpha(0f).setDuration(600).withEndAction(() -> {
                setVisibility(GONE);
                stopBreathing();
            }).start();
        }
    }

    private void startBreathing() {
        if (breathe != null) return;
        breathe = ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.05f);
        breathe.setDuration(7000);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.setRepeatMode(ValueAnimator.REVERSE);
        breathe.setInterpolator(new LinearInterpolator());
        breathe.start();
    }

    private void stopBreathing() {
        if (breathe != null) { breathe.cancel(); breathe = null; }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopBreathing();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawPath(path, paint);
    }
}
