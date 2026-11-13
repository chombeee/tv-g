package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The screensaver: the chosen wallpaper with three glass cards on it. Clock (date, time, next prayer, prayer
 * times, weather in one line), a weather card with an animated sky for where the TV is, and a Now Playing card
 * that appears only while something plays. Built in code so MainActivity (after the idle timeout) and the
 * system screensaver (ClockDream) show the same thing. Cards drift a few pixels each minute against burn-in.
 */
public class StandbyView extends FrameLayout {

    private static final long SLOW_MS = 30_000L;
    private static final long DRIFT_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final float u;                       // 1 unit = 1 px on a 1920-wide screen
    private final int screenW, screenH;
    private Typeface face;

    private ImageView wall;
    private View dim;
    private LinearLayout clockCard, rightColumn, playCard;
    private TextView nextLine, dateLine, hijriLine, tempLine, prayerLine;
    private WeatherFx fx;
    private TextView wxPlace, wxTemp, wxCond, wxExtra;
    private ImageView art;
    private TextView npTitle, npArtist, npState;
    private Bar bar;

    private boolean shown;
    private String shownWallKey = "";
    private int slideIndex;
    private String lastTrack = "";

    public StandbyView(Context c) {
        super(c);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        u = screenW / 1920f;
        try { face = c.getResources().getFont(R.font.outfit); } catch (Exception e) { face = Typeface.DEFAULT; }
        setBackgroundColor(Color.BLACK);
        setClickable(true);
        setFocusable(true);
        build(c);
    }

    // ---------------------------------------------------------------- layout

    private void build(Context c) {
        wall = new ImageView(c);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(wall, new LayoutParams(-1, -1));
        dim = new View(c);
        addView(dim, new LayoutParams(-1, -1));

        // --- Clock card: light frosted glass, dark text
        clockCard = card(c, 0xE6FFFFFF, 0x66FFFFFF, 44);
        nextLine = text(c, "", 30, 0xFF3A3A3F, 500);
        dateLine = text(c, "", 36, 0xFF1C1C1E, 600);
        hijriLine = text(c, "", 26, 0xFF6B6B72, 400);
        LinearLayout timeRow = new LinearLayout(c);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.BOTTOM);
        TextClock time = new TextClock(c);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        style(time, 210, 0xFF111113, 500);
        time.setLetterSpacing(-0.03f);
        TextClock ampm = new TextClock(c);
        ampm.setFormat12Hour("a");
        ampm.setFormat24Hour("");
        style(ampm, 44, 0xFF6B6B72, 600);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.leftMargin = px(14);
        ap.bottomMargin = px(34);
        timeRow.addView(time);
        timeRow.addView(ampm, ap);
        TextClock date = new TextClock(c);       // keeps the date right across midnight without polling
        date.setFormat12Hour(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"));
        date.setFormat24Hour(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"));
        style(date, 36, 0xFF1C1C1E, 600);
        tempLine = text(c, "", 34, 0xFF2A2A2E, 500);
        prayerLine = text(c, "", 27, 0xFF55555C, 400);
        prayerLine.setLineSpacing(px(8), 1f);
        clockCard.addView(nextLine);
        clockCard.addView(date, margins(0, 10, 0, 0));
        clockCard.addView(timeRow, margins(0, -6, 0, 0));
        clockCard.addView(hijriLine, margins(0, 0, 0, 0));
        clockCard.addView(divider(c, 0x22000000), dividerParams());
        clockCard.addView(tempLine);
        clockCard.addView(prayerLine, margins(0, 14, 0, 0));
        LayoutParams cp = new LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        cp.leftMargin = Math.round(screenW * 0.06f);
        addView(clockCard, cp);

        // --- Right column: weather card and Now Playing
        rightColumn = new LinearLayout(c);
        rightColumn.setOrientation(LinearLayout.VERTICAL);
        FrameLayout wx = new FrameLayout(c);
        fx = new WeatherFx(c);
        fx.setCornerRadius(px(40));
        wx.addView(fx, new LayoutParams(-1, -1));
        LinearLayout wxText = new LinearLayout(c);
        wxText.setOrientation(LinearLayout.VERTICAL);
        wxText.setPadding(px(40), px(32), px(40), px(32));
        wxPlace = text(c, "", 26, 0xE6FFFFFF, 600);
        wxPlace.setLetterSpacing(0.12f);
        wxTemp = text(c, "", 150, Color.WHITE, 400);
        wxTemp.setShadowLayer(px(8), 0, px(2), 0x66000000);
        wxCond = text(c, "", 36, Color.WHITE, 500);
        wxExtra = text(c, "", 27, 0xDDFFFFFF, 400);
        wxText.addView(wxPlace);
        wxText.addView(wxTemp, margins(0, 4, 0, 0));
        wxText.addView(wxCond, margins(0, -8, 0, 0));
        wxText.addView(wxExtra, margins(0, 10, 0, 0));
        wx.addView(wxText, new LayoutParams(-1, -2, Gravity.BOTTOM));
        wx.setMinimumHeight(px(470));
        rightColumn.addView(wx, new LinearLayout.LayoutParams(px(560), px(470)));

        playCard = card(c, 0xB31C1E24, 0x33FFFFFF, 40);
        playCard.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setColor(0x33FFFFFF);
        artBg.setCornerRadius(px(18));
        art.setBackground(artBg);
        art.setClipToOutline(true);
        art.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(18));
            }
        });
        top.addView(art, new LinearLayout.LayoutParams(px(120), px(120)));
        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(c, "SEDANG DIMAINKAN", 21, 0x99FFFFFF, 600);
        label.setLetterSpacing(0.12f);
        npTitle = text(c, "", 32, Color.WHITE, 600);
        npTitle.setSingleLine(true);
        npTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        npArtist = text(c, "", 26, 0xCCFFFFFF, 400);
        npArtist.setSingleLine(true);
        npArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.addView(label);
        meta.addView(npTitle, margins(0, 4, 0, 0));
        meta.addView(npArtist);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1f);
        mp.leftMargin = px(24);
        top.addView(meta, mp);
        npState = text(c, "▶", 40, Color.WHITE, 500);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.leftMargin = px(14);
        top.addView(npState, sp);
        bar = new Bar(c);
        playCard.addView(top);
        playCard.addView(bar, margins(0, 22, 0, 0));
        playCard.setVisibility(GONE);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(560), -2);
        pl.topMargin = px(28);
        rightColumn.addView(playCard, pl);
        LayoutParams rp = new LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        rp.rightMargin = Math.round(screenW * 0.06f);
        addView(rightColumn, rp);
    }

    private LinearLayout card(Context c, int fill, int stroke, int radiusUnits) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radiusUnits));
        g.setStroke(Math.max(1, px(2)), stroke);
        l.setBackground(g);
        l.setPadding(px(48), px(40), px(56), px(44));
        return l;
    }

    private TextView text(Context c, String s, float units, int color, int wght) {
        TextView t = new TextView(c);
        t.setText(s);
        style(t, units, color, wght);
        return t;
    }

    private void style(TextView t, float units, int color, int wght) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, units * u);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        try { t.setFontVariationSettings("'wght' " + wght); } catch (Exception ignored) { }
    }

    private View divider(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        return v;
    }

    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(1, px(2)));
        p.topMargin = px(24);
        p.bottomMargin = px(22);
        return p;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(px(l), px(t), px(r), px(b));
        return p;
    }

    private int px(float units) { return Math.round(units * u); }

    // ------------------------------------------------------------- lifecycle

    /** Starts the clock, weather, music and wallpaper updates. The host fades the view in. */
    public void onShow() {
        shown = true;
        applyDim();
        loadWallpaper(true);
        refreshSlow();
        refreshMedia();
        WeatherAirQuality.syncIfDue(getContext(), this::refreshSlow);
        AutoLocation.refreshIfDue(getContext(), this::refreshSlow);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(slowTick, SLOW_MS);
        handler.postDelayed(mediaTick, 1000);
        handler.postDelayed(driftTick, DRIFT_MS);
        if (StandbyWallpaper.SLIDESHOW.equals(StandbyWallpaper.mode(getContext()))) {
            handler.postDelayed(slideTick, 60_000L);
        }
    }

    public void onHide() {
        shown = false;
        handler.removeCallbacksAndMessages(null);
        wall.setImageDrawable(null);
        shownWallKey = "";
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        decoder.shutdownNow();
        super.onDetachedFromWindow();
    }

    private final Runnable slowTick = new Runnable() {
        @Override public void run() {
            if (!shown) return;
            refreshSlow();
            applyDim();
            WeatherAirQuality.syncIfDue(getContext(), StandbyView.this::refreshSlow);
            AutoLocation.refreshIfDue(getContext(), StandbyView.this::refreshSlow);
            handler.postDelayed(this, SLOW_MS);
        }
    };

    private final Runnable mediaTick = new Runnable() {
        @Override public void run() {
            if (!shown) return;
            refreshMedia();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable slideTick = new Runnable() {
        @Override public void run() {
            if (!shown) return;
            slideIndex++;
            loadWallpaper(false);
            handler.postDelayed(this, 60_000L);
        }
    };

    private final Runnable driftTick = new Runnable() {
        @Override public void run() {
            if (!shown) return;
            float dx = px(14) * (2f * random.nextFloat() - 1f), dy = px(10) * (2f * random.nextFloat() - 1f);
            clockCard.animate().translationX(dx).translationY(dy).setDuration(1500).start();
            rightColumn.animate().translationX(-dx).translationY(-dy).setDuration(1500).start();
            handler.postDelayed(this, DRIFT_MS);
        }
    };

    // ---------------------------------------------------------------- content

    /** Darker after 22:00 so the TV is not a lamp in a bedroom. */
    private void applyDim() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        boolean night = h >= 22 || h < 6;
        dim.setBackgroundColor(night ? 0x99000000 : 0x2E000000);
        // Light cards would glare at night: use the dark card look then.
        setCardNight(night);
    }

    private boolean nightLook;

    private void setCardNight(boolean night) {
        if (night == nightLook) return;
        nightLook = night;
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(44));
        g.setColor(night ? 0xCC17181C : 0xE6FFFFFF);
        g.setStroke(Math.max(1, px(2)), night ? 0x33FFFFFF : 0x66FFFFFF);
        clockCard.setBackground(g);
        int main = night ? 0xFFF2F2F5 : 0xFF111113;
        int sub = night ? 0xFFB8B8C0 : 0xFF55555C;
        recolor(clockCard, main, sub);
    }

    private void recolor(ViewGroup g, int main, int sub) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof ViewGroup) recolor((ViewGroup) v, main, sub);
            else if (v instanceof TextView) {
                float size = ((TextView) v).getTextSize() / u;
                ((TextView) v).setTextColor(size >= 34 ? main : sub);
            }
        }
    }

    private void refreshSlow() {
        Context c = getContext();
        try {
            // Next prayer line: "Zohor 13:10 · dalam 1j 55m"
            long[] next = PrayerTimes.nextPrayer(c);
            long mins = Math.max(0, (next[1] - System.currentTimeMillis()) / 60000);
            String when = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(new java.util.Date(next[1]));
            nextLine.setText(PrayerTimes.nameOf((int) next[0]) + " " + when + "  ·  dalam "
                    + (mins >= 60 ? (mins / 60) + "j " : "") + (mins % 60) + "m");
            hijriLine.setText(PrayerTimes.getHijriDate());

            PrayerTimes.PrayerItem[] items = PrayerTimes.getPrayerItems(c);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            int shownCount = 0;
            for (int i = 0; i < items.length; i++) {
                if (i == 1) continue;                      // Syuruk is not a prayer time to list
                if (shownCount == 3) sb.append("\n");
                else if (shownCount > 0) sb.append("  ·  ");
                int start = sb.length();
                sb.append(items[i].name).append(' ').append(items[i].timeStr);
                if (items[i].isNext) {
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new ForegroundColorSpan(0xFF1F8A4C), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                shownCount++;
            }
            prayerLine.setText(sb);
        } catch (Exception ignored) { }

        try {
            WeatherAirQuality.FullWeather w = WeatherAirQuality.getFullWeather(c);
            tempLine.setText(w.icon + "  " + w.temp + "°   H:" + w.highTemp + "°  L:" + w.lowTemp + "°");
            String place = AutoLocation.isAuto(c) ? AutoLocation.cityLabel(c) : w.location;
            wxPlace.setText("📍 " + place.toUpperCase(Locale.ROOT));
            wxTemp.setText(w.temp + "°");
            wxCond.setText(w.condition);
            wxExtra.setText("H:" + w.highTemp + "°  L:" + w.lowTemp + "°   ·   Hujan " + w.rainChanceToday
                    + "%   ·   Lembap " + w.humidity + "%");
            fx.setWeather(WeatherAirQuality.currentCode(c), WeatherFx.isDaytime());
        } catch (Exception ignored) { }
    }

    private void refreshMedia() {
        MediaController m = NowPlaying.active(getContext());
        MediaMetadata md = m == null ? null : m.getMetadata();
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.isEmpty()) {
            if (playCard.getVisibility() == VISIBLE && playCard.animate() != null) {
                playCard.animate().alpha(0f).setDuration(400).withEndAction(() -> playCard.setVisibility(GONE)).start();
            }
            lastTrack = "";
            return;
        }
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
        }
        PlaybackState ps = m.getPlaybackState();
        boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
        npState.setText(playing ? "❚❚" : "▶");
        long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        bar.set(dur > 0 ? Math.min(1f, NowPlaying.position(m) / (float) dur) : -1f);
        if (playCard.getVisibility() != VISIBLE) {
            playCard.setAlpha(0f);
            playCard.setVisibility(VISIBLE);
            playCard.animate().alpha(1f).setDuration(500).start();
        }
    }

    // -------------------------------------------------------------- wallpaper

    private void loadWallpaper(boolean first) {
        final Context c = getContext();
        final String mode = StandbyWallpaper.mode(c);
        File pick = null;
        if (StandbyWallpaper.DARK.equals(mode)) {
            wall.setImageDrawable(null);
            shownWallKey = "dark";
            return;
        }
        if (StandbyWallpaper.FILE.equals(mode)) {
            File f = new File(StandbyWallpaper.file(c));
            if (f.isFile()) pick = f;
        } else if (StandbyWallpaper.SLIDESHOW.equals(mode)) {
            List<File> all = StandbyWallpaper.candidates(c);
            if (!all.isEmpty()) pick = all.get(Math.floorMod(slideIndex, all.size()));
        }
        if (pick == null) pick = StandbyWallpaper.homeCurrent(c);
        if (pick == null) {
            List<File> all = StandbyWallpaper.candidates(c);
            if (!all.isEmpty()) pick = all.get(0);
        }
        if (pick == null) return;
        final File file = pick;
        if (file.getPath().equals(shownWallKey)) return;
        decoder.execute(() -> {
            final Bitmap bmp = decode(file);
            if (bmp == null) return;
            handler.post(() -> {
                if (!shown && !first) return;
                Drawable old = wall.getDrawable();
                BitmapDrawable nd = new BitmapDrawable(getResources(), bmp);
                if (old != null && !first) {
                    TransitionDrawable t = new TransitionDrawable(new Drawable[]{old, nd});
                    t.setCrossFadeEnabled(true);
                    wall.setImageDrawable(t);
                    t.startTransition(1200);
                } else {
                    wall.setImageDrawable(nd);
                }
                shownWallKey = file.getPath();
            });
        });
    }

    private Bitmap decode(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= screenW && o.outHeight / (sample * 2) >= screenH) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** A thin rounded progress bar. A negative value means the track length is unknown and draws nothing. */
    private final class Bar extends View {
        private float frac = -1f;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(Context c) { super(c); }

        void set(float f) {
            if (Math.abs(f - frac) > 0.002f) { frac = f; invalidate(); }
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), px(8));
        }

        @Override protected void onDraw(Canvas c) {
            if (frac < 0) return;
            float rad = getHeight() / 2f;
            p.setColor(0x33FFFFFF);
            r.set(0, 0, getWidth(), getHeight());
            c.drawRoundRect(r, rad, rad, p);
            p.setColor(0xFFFFFFFF);
            r.set(0, 0, Math.max(getHeight(), getWidth() * frac), getHeight());
            c.drawRoundRect(r, rad, rad, p);
        }
    }
}
