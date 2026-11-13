package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Lightweight animated, non-interactive weather badge using existing Open-Meteo cache. */
public final class WeatherCompactView extends LinearLayout {
    private final WeatherSymbol symbol;
    private final TextView temperature;
    private final TextView description;

    public WeatherCompactView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        // The badge itself opens weather details; inner icon and labels stay non-focusable.
        setFocusable(true);
        setClickable(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        symbol = new WeatherSymbol(context);
        addView(symbol, new LayoutParams((int)(58*d), (int)(54*d)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        LayoutParams ll = new LayoutParams(-2, -2);
        ll.leftMargin = (int)(4*d);
        addView(labels, ll);
        temperature = makeText(context, 18, true);
        description = makeText(context, 10, false);
        labels.addView(temperature);
        labels.addView(description);
        update(null);
    }

    private TextView makeText(Context context, int sizeSp, boolean bold) {
        TextView v = new TextView(context);
        v.setTextSize(sizeSp);
        v.setTextColor(Color.WHITE);
        v.setSingleLine(true);
        v.setShadowLayer(2f, 0f, 1f, 0x99000000);
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setFocusable(false);
        return v;
    }

    void update(WeatherAirQuality.FullWeather weather) {
        boolean hasWeather = getContext().getSharedPreferences("weather_aqi_cache", Context.MODE_PRIVATE)
                .contains("weather_json");
        if (weather == null || !hasWeather) {
            temperature.setText("--°C");
            description.setText("Cuaca");
            symbol.setType(2);
            return;
        }
        temperature.setText(weather.temp + "°C");
        description.setText(weather.condition);
        // Match the Open-Meteo condition strings used by WeatherAirQuality.
        String c = weather.condition == null ? "" : weather.condition.toLowerCase(java.util.Locale.ROOT);
        if (c.contains("ribut") || c.contains("thunder")) symbol.setType(3);
        else if (c.contains("hujan") || c.contains("rain") || c.contains("drizzle")) symbol.setType(1);
        else if (c.contains("mendung") || c.contains("cloud") || c.contains("jerebu") || c.contains("kabus")) symbol.setType(2);
        else symbol.setType(0);
    }

    private static final class WeatherSymbol extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int type = 2; // 0 sun, 1 rain, 2 cloudy, 3 thunder
        private final float d;
        WeatherSymbol(Context context) {
            super(context);
            d = getResources().getDisplayMetrics().density;
            setFocusable(false);
            setClickable(false);
        }
        void setType(int value) { type = value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(getWidth() / 45f, getHeight() / 43f);
            float phase = (SystemClock.uptimeMillis() % 2600L) / 2600f;
            if (type == 0 || type == 2) {
                float cx = type == 0 ? 22 : 15;
                float cy = type == 0 ? 21 : 17;
                float pulse = (float)Math.sin(phase * Math.PI * 2) * 0.9f;
                p.setColor(0xFFFFD469);
                if (type == 0) {
                    p.setStrokeWidth(2.2f);
                    for (int j=0; j<8; j++) {
                        double a = j * Math.PI / 4.0;
                        canvas.drawLine(cx + (float)Math.cos(a)*13, cy+(float)Math.sin(a)*13,
                                cx+(float)Math.cos(a)*17, cy+(float)Math.sin(a)*17,p);
                    }
                    canvas.drawCircle(cx, cy, 9+pulse, p);
                } else canvas.drawCircle(cx,cy,8+pulse,p);
            }
            if (type != 0) {
                canvas.save();
                if (type == 2) canvas.translate((float)Math.sin(phase*Math.PI*2)*1.2f,0);
                p.setColor(0xFFBBD9ED);
                canvas.drawCircle(17,22,10,p);
                canvas.drawCircle(27,19,12,p);
                canvas.drawCircle(35,23,8,p);
                canvas.drawRoundRect(new RectF(9,22,41,30),6,6,p);
                canvas.restore();
            }
            if (type == 1) {
                p.setStrokeWidth(2.3f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(0xFF45BEFF);
                for (int i=0; i<3; i++) {
                    float y = 30 + ((phase*15 + i*4.2f)%12);
                    canvas.drawLine(17+i*8, y, 14+i*8, y+4,p);
                }
            } else if (type == 3) {
                p.setColor(0xFFFFE17B);
                float flicker = (phase > .12f && phase < .28f) || (phase > .48f && phase < .55f) ? 1f : .35f;
                p.setAlpha((int)(255*flicker));
                android.graphics.Path bolt = new android.graphics.Path();
                bolt.moveTo(26,28); bolt.lineTo(19,37); bolt.lineTo(25,36);
                bolt.lineTo(21,43); bolt.lineTo(33,32); bolt.lineTo(27,33); bolt.close();
                canvas.drawPath(bolt,p);
                p.setAlpha(255);
            }
            canvas.restore();
            if (isAttachedToWindow() && getVisibility() == VISIBLE) postInvalidateDelayed(33);
        }
    }
}
