package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The glass panel that opens when the clock is pressed: network, Bluetooth, inputs, screen off, volume, wallpaper
 * controls, the screensaver wallpaper, weather location and a way into every other setting.
 * Everything is sized from the screen width, so it looks the same on a 720p and a 1080p TV.
 */
final class GlassSettingsPanel {

    interface Host {
        void openWifi();
        void openBluetooth();
        void openInputs();
        void turnOff();
        void nextWallpaper();
        long wallpaperIntervalMs();
        void setWallpaperIntervalMs(long ms);
        void pickScreensaverWallpaper();
        void refreshLocationData();
        void preferences();
        void allSettings();
    }

    private static final long[] INTERVALS = {15_000L, 30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L, 0L};

    private final Activity a;
    private final Host host;
    private final float u;
    private final int screenW;
    private Typeface face;
    private Dialog dialog;

    private GlassSettingsPanel(Activity a, Host host) {
        this.a = a;
        this.host = host;
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        u = screenW / 1920f;
        try { face = a.getResources().getFont(R.font.outfit); } catch (Exception e) { face = Typeface.DEFAULT; }
    }

    static void show(Activity a, Host host) {
        new GlassSettingsPanel(a, host).open();
    }

    private int px(float units) { return Math.round(units * u); }

    private void open() {
        dialog = new Dialog(a);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(34), px(30), px(34), px(30));
        float d = a.getResources().getDisplayMetrics().density;
        panel.setBackground(ChombbFrostedGlass.fromActivity(a.getWindow().getDecorView(), panel, d, 30, 120));

        TextView title = text("Settings", 38, Color.WHITE, 600);
        panel.addView(title, lp(-2, -2, 0, 0, 0, 18));

        // Row 1: Wi-Fi and Bluetooth. Row 2: Inputs and Turn off.
        final Tile wifi = new Tile(R.drawable.chombb_quick_wifi, "Wi-Fi", networkLabel());
        wifi.setOnClickListener(v -> { dialog.dismiss(); host.openWifi(); });
        final Tile bt = new Tile(R.drawable.ic_bluetooth, "Bluetooth", bluetoothLabel());
        bt.setOnClickListener(v -> { dialog.dismiss(); host.openBluetooth(); });
        final Tile inputs = new Tile(R.drawable.ic_hdmi, "Inputs", "Pilih sumber");
        inputs.setOnClickListener(v -> { dialog.dismiss(); host.openInputs(); });
        final Tile off = new Tile(R.drawable.ic_power, "Turn off", "Screen off");
        off.setOnClickListener(v -> { dialog.dismiss(); host.turnOff(); });
        panel.addView(pair(wifi, bt));
        panel.addView(pair(inputs, off));

        panel.addView(new VolumeSlider(a), lp(-1, 76, 0, 18, 0, 0));

        TextView wh = text("WALLPAPER", 22, 0xCCFFFFFF, 600);
        wh.setLetterSpacing(0.12f);
        panel.addView(wh, lp(-2, -2, 6, 26, 0, 10));

        final Tile next = new Tile(R.drawable.ic_wallpaper, "Next photo", null);
        next.setOnClickListener(v -> host.nextWallpaper());
        final Tile every = new Tile(R.drawable.ic_move, "Change every", intervalLabel(host.wallpaperIntervalMs()));
        every.setOnClickListener(v -> {
            long cur = host.wallpaperIntervalMs();
            int idx = 0;
            for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == cur) idx = i;
            long nxt = INTERVALS[(idx + 1) % INTERVALS.length];
            host.setWallpaperIntervalMs(nxt);
            every.setSub(intervalLabel(nxt));
        });
        panel.addView(pair(next, every));

        final Tile saver = new Tile(R.drawable.chombb_quick_screensaver, "Screensaver wallpaper", StandbyWallpaper.describe(a));
        saver.setOnClickListener(v -> { dialog.dismiss(); host.pickScreensaverWallpaper(); });
        panel.addView(saver, lp(-1, -2, 0, 12, 0, 0));

        final Tile loc = new Tile(R.drawable.ic_info, "Lokasi cuaca & solat", locationLabel());
        loc.setOnClickListener(v -> {
            boolean on = !AutoLocation.isAuto(a);
            AutoLocation.setAuto(a, on);
            if (on) AutoLocation.refreshNow(a, host::refreshLocationData);
            loc.setSub(locationLabel());
        });
        panel.addView(loc, lp(-1, -2, 0, 12, 0, 0));

        final Tile prefs = new Tile(R.drawable.ic_settings, "Preferences", "Appearance, Home Layout, widget");
        prefs.setOnClickListener(v -> { dialog.dismiss(); host.preferences(); });
        panel.addView(prefs, lp(-1, -2, 0, 12, 0, 0));

        final Tile all = new Tile(R.drawable.ic_settings, "All settings", "Wi-Fi, bunyi, paparan, sistem");
        all.setOnClickListener(v -> { dialog.dismiss(); host.allSettings(); });
        panel.addView(all, lp(-1, -2, 0, 12, 0, 0));

        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            WindowManager.LayoutParams p = w.getAttributes();
            p.width = Math.round(screenW * 0.30f);
            p.height = -2;
            p.gravity = Gravity.TOP | Gravity.END;
            p.x = px(48);
            p.y = px(70);
            p.dimAmount = 0.12f;
            w.setAttributes(p);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        wifi.requestFocus();
    }

    private LinearLayout pair(View left, View right) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -2, 1f);
        l.rightMargin = px(7);
        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, -2, 1f);
        r.leftMargin = px(7);
        row.addView(left, l);
        row.addView(right, r);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = px(12);
        row.setLayoutParams(rp);
        return row;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w > 0 ? px(w) : w, h > 0 ? px(h) : h);
        p.setMargins(px(l), px(t), px(r), px(b));
        return p;
    }

    private TextView text(String s, float units, int color, int wght) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, units * u);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        try { t.setFontVariationSettings("'wght' " + wght); } catch (Exception ignored) { }
        return t;
    }

    // ---------------------------------------------------------------- labels

    private String intervalLabel(long ms) {
        if (ms <= 0) return "Off";
        if (ms < 60_000L) return (ms / 1000) + " seconds";
        if (ms < 3_600_000L) return (ms / 60_000L) + (ms == 60_000L ? " minute" : " minutes");
        return (ms / 3_600_000L) + " hour";
    }

    private String locationLabel() {
        return AutoLocation.isAuto(a) ? "Auto · " + AutoLocation.cityLabel(a)
                : "Manual · " + AppLocationManager.getLocationName(a);
    }

    private String bluetoothLabel() {
        try {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad == null) return "Tiada";
            return ad.isEnabled() ? "On" : "Off";
        } catch (Exception e) {
            return "Buka";
        }
    }

    private String networkLabel() {
        try {
            ConnectivityManager cm = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false, online = false;
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) return "Offline";
            if (ethernet) return "Ethernet";
            if (wifi) {
                WifiManager wm = (WifiManager) a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) return ssid;
                }
                return "Connected";
            }
        } catch (Exception ignored) { }
        return "Connected";
    }

    // ----------------------------------------------------------------- views

    /** A frosted rounded button: icon on the left, a title and a smaller line under it. Rings white when focused. */
    private final class Tile extends LinearLayout {
        private final TextView sub;
        private final GradientDrawable bg = new GradientDrawable();

        Tile(int iconRes, String title, String subtitle) {
            super(a);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(px(22), px(18), px(18), px(18));
            setFocusable(true);
            setClickable(true);
            bg.setCornerRadius(px(26));
            paint(false);
            setBackground(bg);

            ImageView icon = new ImageView(a);
            icon.setImageResource(iconRes);
            icon.setColorFilter(Color.WHITE);
            addView(icon, new LayoutParams(px(38), px(38)));
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(VERTICAL);
            TextView t = text(title, 27, Color.WHITE, 600);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(t);
            sub = text(subtitle == null ? "" : subtitle, 21, 0xB3FFFFFF, 400);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (subtitle == null) sub.setVisibility(GONE);
            col.addView(sub);
            LayoutParams cp = new LayoutParams(0, -2, 1f);
            cp.leftMargin = px(16);
            addView(col, cp);
            setOnFocusChangeListener((v, focused) -> paint(focused));
        }

        void setSub(String s) {
            sub.setText(s);
            sub.setVisibility(VISIBLE);
        }

        private void paint(boolean focused) {
            bg.setColor(focused ? 0x66FFFFFF : 0x33FFFFFF);
            bg.setStroke(Math.max(2, px(3)), focused ? 0xFFFFFFFF : 0x00FFFFFF);
        }
    }

    /** Volume as a pill: the filled part and a round knob with a speaker, the percentage on the right. */
    private final class VolumeSlider extends View {
        private final AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Drawable speaker;
        private boolean focused;

        VolumeSlider(Context c) {
            super(c);
            setFocusable(true);
            setClickable(true);
            speaker = c.getDrawable(R.drawable.ic_sound).mutate();
            speaker.setTint(0xFF202024);
            setOnFocusChangeListener((v, f) -> { focused = f; invalidate(); });
        }

        private int max() { return Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)); }

        private int cur() { return am.getStreamVolume(AudioManager.STREAM_MUSIC); }

        private void change(int delta) {
            int n = Math.max(0, Math.min(max(), cur() + delta));
            try { am.setStreamVolume(AudioManager.STREAM_MUSIC, n, 0); } catch (Exception ignored) { }
            invalidate();
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { change(-1); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { change(1); return true; }
            return super.onKeyDown(keyCode, e);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                float f = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, getWidth())));
                try { am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f * max()), 0); } catch (Exception ignored) { }
                invalidate();
                return true;
            }
            return super.onTouchEvent(e);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float rad = h / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x33FFFFFF);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, p);
            float frac = cur() / (float) max();
            p.setColor(focused ? 0xFFFFFFFF : 0xE6F2F2F5);
            r.set(0, 0, Math.max(h, w * frac), h);
            c.drawRoundRect(r, rad, rad, p);
            int s = Math.round(h * 0.46f), cx = Math.round(h * 0.5f), cy = h / 2;
            speaker.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
            speaker.draw(c);
            p.setColor(0xFF202024);
            p.setTextSize(h * 0.34f);
            p.setTypeface(face);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Math.round(frac * 100) + "%", w - h * 0.4f, cy + h * 0.12f, p);
            if (focused) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2, px(3)));
                p.setColor(0xFFFFFFFF);
                r.set(0, 0, w, h);
                r.inset(p.getStrokeWidth() / 2, p.getStrokeWidth() / 2);
                c.drawRoundRect(r, rad, rad, p);
            }
        }
    }
}
