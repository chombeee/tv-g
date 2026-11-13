package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Animated weather backdrop for a card: drifting clouds, a glowing sun with turning rays, a moon and twinkling
 * stars at night, falling rain with lightning, fog banks and snow. Draws plain shapes (no bitmaps, no blur) at
 * about 25 frames a second, so it stays light on a slow TV CPU. It animates only while attached to a window.
 */
public class WeatherFx extends View {

    enum Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, STORM, SNOW }

    private Kind kind = Kind.PARTLY;
    private boolean day = true;
    private float radius;
    private final float density;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path tmp = new Path();
    private final Path tmp2 = new Path();
    private final RectF rect = new RectF();
    private Shader sky;

    private static final int DROPS = 80;
    private final float[] dx = new float[DROPS], dy = new float[DROPS], dv = new float[DROPS], dl = new float[DROPS];
    private static final int STARS = 28;
    private final float[] sx = new float[STARS], sy = new float[STARS], sp = new float[STARS];

    private boolean running;
    private final long t0 = SystemClock.uptimeMillis();

    public WeatherFx(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        Random r = new Random(7);
        for (int i = 0; i < DROPS; i++) {
            dx[i] = r.nextFloat();
            dy[i] = r.nextFloat();
            dv[i] = 0.7f + r.nextFloat() * 0.9f;
            dl[i] = 0.6f + r.nextFloat() * 0.8f;
        }
        for (int i = 0; i < STARS; i++) {
            sx[i] = r.nextFloat();
            sy[i] = r.nextFloat() * 0.7f;
            sp[i] = 1f + r.nextFloat() * 2.5f;
        }
        setWillNotDraw(false);
    }

    /** Corner radius in pixels, so the animation stays inside a rounded card. */
    public void setCornerRadius(float px) {
        radius = px;
        sky = null;
        invalidate();
    }

    /** Sets the weather from a WMO code (as Open-Meteo reports it) and whether the sun is up. */
    public void setWeather(int wmoCode, boolean isDay) {
        Kind k;
        if (wmoCode == 0 || wmoCode == 1) k = Kind.CLEAR;
        else if (wmoCode == 2) k = Kind.PARTLY;
        else if (wmoCode == 3) k = Kind.CLOUDY;
        else if (wmoCode == 45 || wmoCode == 48) k = Kind.FOG;
        else if (wmoCode >= 95) k = Kind.STORM;
        else if ((wmoCode >= 51 && wmoCode <= 67) || (wmoCode >= 80 && wmoCode <= 82)) k = Kind.RAIN;
        else if ((wmoCode >= 71 && wmoCode <= 77) || wmoCode == 85 || wmoCode == 86) k = Kind.SNOW;
        else k = Kind.CLOUDY;
        if (k != kind || isDay != day) {
            kind = k;
            day = isDay;
            sky = null;
            invalidate();
        }
    }

    /** True from 07:00 to 19:00 local time: a simple stand-in for sunrise and sunset in Malaysia. */
    static boolean isDaytime() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 7 && h < 19;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        running = visibility == VISIBLE && isAttachedToWindow();
        if (running) invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sky = null;
    }

    private void buildSky(int h) {
        int top, bottom;
        switch (kind) {
            case CLEAR:  top = day ? 0xFF2F86DB : 0xFF0A1030; bottom = day ? 0xFF8FCBF6 : 0xFF2A3A78; break;
            case PARTLY: top = day ? 0xFF4690D8 : 0xFF111A3A; bottom = day ? 0xFF9DCCEE : 0xFF33406F; break;
            case RAIN:   top = day ? 0xFF3E4D5E : 0xFF171E2B; bottom = day ? 0xFF72828F : 0xFF3A4556; break;
            case STORM:  top = day ? 0xFF2A3140 : 0xFF0F131C; bottom = day ? 0xFF535E6E : 0xFF2C3442; break;
            case FOG:    top = day ? 0xFF8A97A2 : 0xFF2C333D; bottom = day ? 0xFFBAC3CB : 0xFF4B5461; break;
            case SNOW:   top = day ? 0xFF7C93AA : 0xFF1E2A3C; bottom = day ? 0xFFC5D3E0 : 0xFF47566B; break;
            default:     top = day ? 0xFF6A7F94 : 0xFF1A2232; bottom = day ? 0xFFA3B3C0 : 0xFF3B465A; break;
        }
        sky = new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        if (sky == null) buildSky(h);

        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        paint.setShader(sky);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(rect, paint);
        paint.setShader(null);

        boolean clearish = kind == Kind.CLEAR || kind == Kind.PARTLY;
        if (clearish && !day) drawStars(canvas, w, h, t);
        if (clearish) {
            if (day) drawSun(canvas, w, h, t); else drawMoon(canvas, w, h);
        }
        switch (kind) {
            case CLEAR:  drawClouds(canvas, w, h, t, 1, 0.55f, false); break;
            case PARTLY: drawClouds(canvas, w, h, t, 3, 0.9f, false); break;
            case CLOUDY: drawClouds(canvas, w, h, t, 5, 0.95f, false); break;
            case FOG:    drawClouds(canvas, w, h, t, 3, 0.5f, false); drawFog(canvas, w, h, t); break;
            case RAIN:   drawClouds(canvas, w, h, t, 5, 0.95f, true); drawDrops(canvas, w, h, t, 50, false); break;
            case STORM:  drawClouds(canvas, w, h, t, 5, 1f, true); drawDrops(canvas, w, h, t, 80, false); drawLightning(canvas, w, h, t); break;
            case SNOW:   drawClouds(canvas, w, h, t, 4, 0.9f, false); drawDrops(canvas, w, h, t, 40, true); break;
        }
        canvas.restore();

        if (running) postInvalidateDelayed(40);
    }

    private void drawSun(Canvas c, int w, int h, float t) {
        float cx = w * 0.80f, cy = h * 0.30f, r = Math.min(w, h) * 0.13f;
        float pulse = 1f + 0.06f * (float) Math.sin(t * 1.4f);
        paint.setShader(new RadialGradient(cx, cy, r * 3.2f * pulse, 0x88FFE9A0, 0x00FFE9A0, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 3.2f * pulse, paint);
        paint.setShader(null);
        paint.setColor(0x55FFF3C4);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(2f, r * 0.12f));
        float spin = t * 6f;
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(spin + i * 30);
            float in = r * 1.35f, out = r * (i % 2 == 0 ? 1.9f : 1.65f);
            c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                    cx + (float) Math.cos(a) * out, cy + (float) Math.sin(a) * out, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFE27A);
        c.drawCircle(cx, cy, r, paint);
    }

    private void drawMoon(Canvas c, int w, int h) {
        float cx = w * 0.80f, cy = h * 0.28f, r = Math.min(w, h) * 0.11f;
        paint.setShader(new RadialGradient(cx, cy, r * 3f, 0x44DCE6FF, 0x00DCE6FF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 3f, paint);
        paint.setShader(null);
        tmp.reset();
        tmp.addCircle(cx, cy, r, Path.Direction.CW);
        tmp2.reset();
        tmp2.addCircle(cx + r * 0.55f, cy - r * 0.25f, r * 0.9f, Path.Direction.CW);
        tmp.op(tmp2, Path.Op.DIFFERENCE);
        paint.setColor(0xFFF4F1DE);
        paint.setStyle(Paint.Style.FILL);
        c.drawPath(tmp, paint);
    }

    private void drawStars(Canvas c, int w, int h, float t) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < STARS; i++) {
            float a = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * sp[i] + i * 1.7f));
            paint.setColor(Color.argb((int) (a * 230), 255, 255, 255));
            c.drawCircle(sx[i] * w, sy[i] * h, (i % 3 == 0 ? 1.6f : 1.0f) * density, paint);
        }
    }

    private void drawClouds(Canvas c, int w, int h, float t, int count, float alpha, boolean dark) {
        paint.setStyle(Paint.Style.FILL);
        int base = dark ? 0x6E7C8C : 0xFFFFFF;
        if (!day) base = dark ? 0x3A4455 : 0x8793AB;
        for (int i = 0; i < count; i++) {
            float scale = (0.55f + 0.2f * ((i * 37) % 5)) * Math.min(w, h) / 120f * 0.9f;
            float cw = 120f * scale;
            float speed = (6f + (i * 11) % 9) * (density * 0.5f);
            float x = ((i * 0.37f * w + t * speed) % (w + cw * 2)) - cw;
            float y = h * (0.12f + 0.55f * ((i * 0.381f) % 1f));
            int a = (int) (alpha * (i % 2 == 0 ? 205 : 150));
            paint.setColor((a << 24) | (base & 0xFFFFFF));
            c.drawCircle(x + cw * 0.28f, y, 26f * scale, paint);
            c.drawCircle(x + cw * 0.52f, y - 14f * scale, 34f * scale, paint);
            c.drawCircle(x + cw * 0.78f, y, 24f * scale, paint);
            rect.set(x + cw * 0.14f, y - 8f * scale, x + cw * 0.92f, y + 24f * scale);
            c.drawRoundRect(rect, 16f * scale, 16f * scale, paint);
        }
    }

    private void drawFog(Canvas c, int w, int h, float t) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) {
            float x = ((t * (8f + i * 5f) * density * 0.5f) % (w * 1.5f)) - w * 0.5f * (i + 1) * 0.4f;
            paint.setColor(0x33FFFFFF);
            rect.set(x - w * 0.2f, h * (0.35f + i * 0.2f), x + w * 1.0f, h * (0.35f + i * 0.2f) + h * 0.14f);
            c.drawRoundRect(rect, h * 0.07f, h * 0.07f, paint);
        }
    }

    private void drawDrops(Canvas c, int w, int h, float t, int count, boolean snow) {
        float slant = snow ? 0.12f : 0.22f;
        if (snow) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xE6FFFFFF);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(1.4f * density);
            paint.setColor(0xA0CFE6FF);
        }
        for (int i = 0; i < Math.min(count, DROPS); i++) {
            float speed = (snow ? 0.12f : 0.8f) * dv[i];
            float yy = ((dy[i] + t * speed) % 1.15f) - 0.1f;
            float y = yy * h;
            float x = (dx[i] * w + yy * h * slant + (snow ? 12f * density * (float) Math.sin(t + i) : 0f)) % w;
            if (snow) {
                c.drawCircle(x, y, (1.4f + dl[i]) * density, paint);
            } else {
                float len = (10f + 14f * dl[i]) * density;
                c.drawLine(x, y, x - len * slant, y + len, paint);
            }
        }
    }

    private void drawLightning(Canvas c, int w, int h, float t) {
        float ph = t % 6.5f;
        float a = 0f;
        if (ph < 0.12f) a = 0.55f * (1f - ph / 0.12f);
        else if (ph > 0.28f && ph < 0.36f) a = 0.35f * (1f - (ph - 0.28f) / 0.08f);
        if (a <= 0f) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (a * 255), 255, 255, 235));
        rect.set(0, 0, w, h);
        c.drawRect(rect, paint);
        if (ph < 0.12f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * density);
            paint.setColor(0xFFFFFBD0);
            float x = w * (0.25f + 0.4f * (((int) (t / 6.5f) * 0.37f) % 1f));
            tmp.reset();
            tmp.moveTo(x, h * 0.25f);
            tmp.lineTo(x - 8 * density, h * 0.48f);
            tmp.lineTo(x + 4 * density, h * 0.48f);
            tmp.lineTo(x - 10 * density, h * 0.78f);
            c.drawPath(tmp, paint);
        }
    }
}
