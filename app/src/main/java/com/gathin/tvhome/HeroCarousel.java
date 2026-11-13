package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A pill on Home that rotates through widgets: Now Playing (only while something plays), next prayer, and
 * weather with its animated sky. It turns by itself every few seconds; Left and Right turn it by hand.
 * It replaces the old widget shelf in the "Hero" home layout, and refreshes music every second.
 */
public class HeroCarousel extends LinearLayout {

    interface Host {
        void focusClock();
        void focusTopIcons();
        void focusDock();
        void accent(int color, boolean musicActive);
    }

    private static final long ROTATE_MS = 9_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Host host;
    private final float u;
    private Typeface face;
    private final FrameLayout card;
    private final LinearLayout dots;
    private final List<View> slides = new ArrayList<>();
    private int index = 0;
    private boolean active, musicActive, focusedNow;
    private int accent = 0xFF3A86FF;
    private int ticks;

    // Now Playing slide
    private View slideNow;
    private ImageView art;
    private TextView npTitle, npArtist, npButton;
    private Wave wave;
    private String lastTrack = "";
    // Prayer slide
    private View slidePrayer;
    private TextView prLabel, prName, prSub;
    // Weather slide
    private View slideWeather;
    private WeatherFx fx;
    private TextView wxTemp, wxCond, wxPlace, wxRange;

    HeroCarousel(Context c, Host host) {
        super(c);
        this.host = host;
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        u = dm.widthPixels / 1920f;
        try { face = c.getResources().getFont(R.font.outfit); } catch (Exception e) { face = Typeface.DEFAULT; }
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        setGravity(Gravity.CENTER_HORIZONTAL);

        card = new FrameLayout(c);
        paintCard(false);
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(72));
            }
        });
        addView(card, new LayoutParams(px(760), px(158)));

        buildNow(c);
        buildPrayer(c);
        buildWeather(c);
        slides.add(slideNow);
        slides.add(slidePrayer);
        slides.add(slideWeather);
        for (View s : slides) {
            card.addView(s, new FrameLayout.LayoutParams(-1, -1));
            s.setAlpha(0f);
            s.setVisibility(GONE);
        }
        dots = new LinearLayout(c);
        dots.setOrientation(HORIZONTAL);
        LayoutParams dp = new LayoutParams(-2, -2);
        dp.topMargin = px(16);
        addView(dots, dp);

        setOnFocusChangeListener((v, f) -> { focusedNow = f; paintCard(f); });
        setOnClickListener(v -> activate());
        showSlide(firstAvailable(), false);
    }

    // ------------------------------------------------------------------ build

    private void buildNow(Context c) {
        FrameLayout f = new FrameLayout(c);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(28), px(20), px(30), px(20));
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ab = new GradientDrawable();
        ab.setColor(0x33FFFFFF);
        ab.setCornerRadius(px(18));
        art.setBackground(ab);
        art.setClipToOutline(true);
        art.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(18));
            }
        });
        row.addView(art, new LayoutParams(px(118), px(118)));
        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(VERTICAL);
        TextView label = tv(c, "NOW PLAYING", 21, 0xCCFFFFFF, 700);
        label.setLetterSpacing(0.14f);
        npTitle = tv(c, "", 33, Color.WHITE, 700);
        npTitle.setSingleLine(true);
        npTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        npArtist = tv(c, "", 25, 0xCCFFFFFF, 400);
        npArtist.setSingleLine(true);
        npArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        wave = new Wave(c);
        meta.addView(label);
        meta.addView(npTitle);
        meta.addView(npArtist);
        meta.addView(wave, new LayoutParams(-1, px(22)));
        LayoutParams mp = new LayoutParams(0, -2, 1f);
        mp.leftMargin = px(24);
        row.addView(meta, mp);
        npButton = tv(c, "❚❚", 34, 0xFF101010, 700);
        npButton.setGravity(Gravity.CENTER);
        LayoutParams bp = new LayoutParams(px(96), px(96));
        bp.leftMargin = px(20);
        row.addView(npButton, bp);
        f.addView(row, new FrameLayout.LayoutParams(-1, -1));
        slideNow = f;
    }

    private void buildPrayer(Context c) {
        FrameLayout f = new FrameLayout(c);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(34), px(20), px(34), px(20));
        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.ic_mosque);
        icon.setColorFilter(0xFF101010);
        icon.setPadding(px(22), px(22), px(22), px(22));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFFFFFFFF);
        icon.setBackground(bg);
        row.addView(icon, new LayoutParams(px(112), px(112)));
        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(VERTICAL);
        prLabel = tv(c, "SOLAT SETERUSNYA", 21, 0xCCFFFFFF, 700);
        prLabel.setLetterSpacing(0.14f);
        prName = tv(c, "", 46, Color.WHITE, 700);
        prSub = tv(c, "", 25, 0xCCFFFFFF, 400);
        meta.addView(prLabel);
        meta.addView(prName);
        meta.addView(prSub);
        LayoutParams mp = new LayoutParams(0, -2, 1f);
        mp.leftMargin = px(28);
        row.addView(meta, mp);
        f.addView(row, new FrameLayout.LayoutParams(-1, -1));
        slidePrayer = f;
    }

    private void buildWeather(Context c) {
        FrameLayout f = new FrameLayout(c);
        fx = new WeatherFx(c);
        fx.setCornerRadius(0);
        f.addView(fx, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(40), px(14), px(40), px(14));
        wxTemp = tv(c, "", 84, Color.WHITE, 500);
        wxTemp.setShadowLayer(px(6), 0, px(2), 0x66000000);
        row.addView(wxTemp);
        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(VERTICAL);
        wxCond = tv(c, "", 34, Color.WHITE, 600);
        wxPlace = tv(c, "", 23, 0xDDFFFFFF, 500);
        wxRange = tv(c, "", 23, 0xDDFFFFFF, 400);
        meta.addView(wxCond);
        meta.addView(wxPlace);
        meta.addView(wxRange);
        LayoutParams mp = new LayoutParams(0, -2, 1f);
        mp.leftMargin = px(28);
        row.addView(meta, mp);
        f.addView(row, new FrameLayout.LayoutParams(-1, -1));
        slideWeather = f;
    }

    private TextView tv(Context c, String s, float units, int color, int wght) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, units * u);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        try { t.setFontVariationSettings("'wght' " + wght); } catch (Exception ignored) { }
        return t;
    }

    private int px(float units) { return Math.round(units * u); }

    private void paintCard(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(72));
        g.setColor(0xE6111214);
        g.setStroke(Math.max(2, px(focused ? 4 : 2)), focused ? 0xFFFFFFFF : 0x33FFFFFF);
        card.setBackground(g);
    }

    // ------------------------------------------------------------- lifecycle

    /** Starts or stops the updates; called as Home comes to or leaves the front. */
    void setActive(boolean on) {
        if (on == active) return;
        active = on;
        handler.removeCallbacksAndMessages(null);
        if (on) {
            ticks = 0;
            updateSlow();
            updateMedia();
            handler.postDelayed(tick, 1000);
            handler.postDelayed(rotate, ROTATE_MS);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        active = false;
        super.onDetachedFromWindow();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active) return;
            updateMedia();
            if (++ticks % 15 == 0) updateSlow();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!active) return;
            step(1, true);
            handler.postDelayed(this, ROTATE_MS);
        }
    };

    private void restartRotation() {
        handler.removeCallbacks(rotate);
        if (active) handler.postDelayed(rotate, ROTATE_MS * 2);
    }

    // ---------------------------------------------------------------- slides

    private boolean available(int i) { return i != 0 || musicActive; }

    private int firstAvailable() { return available(0) ? 0 : 1; }

    private void step(int dir, boolean animate) {
        int n = slides.size();
        int i = index;
        for (int k = 0; k < n; k++) {
            i = (i + dir + n) % n;
            if (available(i)) break;
        }
        showSlide(i, animate);
    }

    private void showSlide(int i, boolean animate) {
        View next = slides.get(i);
        View prev = slides.get(index);
        boolean same = prev == next && next.getVisibility() == VISIBLE;
        float dir = i >= index ? 1f : -1f;
        index = i;
        if (!same) {
            if (prev != next) {
                prev.animate().cancel();
                if (animate) {
                    prev.animate().alpha(0f).translationX(-dir * px(36)).setDuration(350)
                            .withEndAction(() -> { if (slides.get(index) != prev) prev.setVisibility(GONE); }).start();
                } else {
                    prev.setAlpha(0f);
                    prev.setVisibility(GONE);
                }
            }
            next.animate().cancel();
            next.setVisibility(VISIBLE);
            if (animate) {
                next.setTranslationX(dir * px(36));
                next.setAlpha(0f);
                next.animate().alpha(1f).translationX(0f).setDuration(350).start();
            } else {
                next.setTranslationX(0f);
                next.setAlpha(1f);
            }
        }
        rebuildDots();
        if (next == slideNow || next == slidePrayer || next == slideWeather) updateSlow();
    }

    private void rebuildDots() {
        dots.removeAllViews();
        for (int i = 0; i < slides.size(); i++) {
            if (!available(i)) continue;
            View d = new View(getContext());
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == index ? 0xFFFFFFFF : 0x66FFFFFF);
            d.setBackground(g);
            LayoutParams p = new LayoutParams(px(i == index ? 16 : 11), px(i == index ? 16 : 11));
            p.leftMargin = px(7);
            p.rightMargin = px(7);
            dots.addView(d, p);
        }
    }

    private void updateSlow() {
        Context c = getContext();
        try {
            long[] next = PrayerTimes.nextPrayer(c);
            long mins = Math.max(0, (next[1] - System.currentTimeMillis()) / 60000);
            String when = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(new java.util.Date(next[1]));
            prName.setText(PrayerTimes.nameOf((int) next[0]) + "  " + when);
            prSub.setText("dalam " + (mins >= 60 ? (mins / 60) + "j " : "") + (mins % 60) + "m  ·  "
                    + PrayerTimes.getShortZoneName(c));
        } catch (Exception ignored) { }
        try {
            WeatherAirQuality.FullWeather w = WeatherAirQuality.getFullWeather(c);
            wxTemp.setText(w.temp + "°");
            wxCond.setText(w.condition);
            String place = AutoLocation.isAuto(c) ? AutoLocation.cityLabel(c) : w.location;
            wxPlace.setText("📍 " + place);
            wxRange.setText("H:" + w.highTemp + "°  L:" + w.lowTemp + "°  ·  Hujan " + w.rainChanceToday + "%");
            fx.setWeather(WeatherAirQuality.currentCode(c), WeatherFx.isDaytime());
        } catch (Exception ignored) { }
    }

    private void updateMedia() {
        MediaController m = NowPlaying.active(getContext());
        MediaMetadata md = m == null ? null : m.getMetadata();
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        boolean has = title != null && !title.isEmpty();
        if (has != musicActive) {
            musicActive = has;
            if (has) showSlide(0, true);                       // a track started: show it
            else if (index == 0) showSlide(1, true);           // it stopped: leave its slide
            else rebuildDots();
            if (!has) { lastTrack = ""; host.accent(accent, false); }
        }
        if (!has) return;
        String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
        String key = title + "|" + artist;
        if (!key.equals(lastTrack)) {
            lastTrack = key;
            npTitle.setText(title);
            npArtist.setText(artist == null ? "" : artist);
            Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            art.setImageBitmap(b);
            accent = b != null ? accentOf(b) : 0xFF3A86FF;
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(accent);
            npButton.setBackground(bg);
            npButton.setTextColor(luminance(accent) > 0.55f ? 0xFF101010 : 0xFFFFFFFF);
            host.accent(accent, true);
        }
        PlaybackState ps = m.getPlaybackState();
        boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
        npButton.setText(playing ? "❚❚" : "▶");
        long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        wave.set(dur > 0 ? Math.min(1f, NowPlaying.position(m) / (float) dur) : 0f, playing, accent);
    }

    /** Average colour of the art, pushed towards a vivid mid-tone so it reads as a colour, not as grey. */
    private static int accentOf(Bitmap b) {
        try {
            Bitmap one = Bitmap.createScaledBitmap(b, 1, 1, true);
            int px = one.getPixel(0, 0);
            float[] hsv = new float[3];
            Color.colorToHSV(px, hsv);
            hsv[1] = Math.max(hsv[1], 0.55f);
            hsv[2] = Math.max(0.55f, Math.min(0.85f, hsv[2] + 0.15f));
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return 0xFF3A86FF;
        }
    }

    private static float luminance(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }

    // ------------------------------------------------------------------ input

    private void activate() {
        if (index == 0) {
            MediaController m = NowPlaying.active(getContext());
            if (m == null) return;
            PlaybackState s = m.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) m.getTransportControls().pause();
            else m.getTransportControls().play();
        } else {
            step(1, true);
        }
        restartRotation();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (index == firstAvailable()) host.focusClock();
                else step(-1, true);
                restartRotation();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                step(1, true);
                restartRotation();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                host.focusDock();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                host.focusTopIcons();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                activate();
                return true;
            default:
                return super.onKeyDown(keyCode, e);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return super.onTouchEvent(e);
    }

    /** Progress line: the played part is a gentle wave while music plays, the rest a straight line. */
    private final class Wave extends View {
        private float frac;
        private boolean playing;
        private int color = Color.WHITE;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Wave(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(float f, boolean pl, int col) {
            frac = f;
            playing = pl;
            color = col;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0) return;
            float mid = h / 2f, x = Math.max(px(10), w * frac);
            p.setStrokeWidth(px(5));
            p.setColor(0x44FFFFFF);
            c.drawLine(x, mid, w - px(3), mid, p);
            p.setColor(color);
            path.reset();
            float phase = playing ? (System.currentTimeMillis() % 1600) / 1600f * (float) (Math.PI * 2) : 0f;
            float amp = playing ? px(5) : 0f;
            path.moveTo(px(3), mid);
            for (float xx = px(3); xx <= x; xx += px(4)) {
                path.lineTo(xx, mid + amp * (float) Math.sin(xx / px(11) - phase));
            }
            c.drawPath(path, p);
            if (playing) postInvalidateDelayed(60);
        }
    }
}
