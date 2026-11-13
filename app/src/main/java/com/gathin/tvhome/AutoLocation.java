package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Works out where the TV is from its public IP (a TV has no GPS), so weather and prayer times follow it
 * when it moves house. City-level accuracy at best: some ISPs route through a city nearby.
 * A location picked by hand (AppLocationManager.setCustomLocation) turns this off until it is switched on again.
 */
final class AutoLocation {
    private static final String TAG = "TvHome.AutoLoc";
    private static final String KEY_AUTO = "auto_location";
    private static final String KEY_LAST = "auto_last_check";
    private static final String KEY_CITY = "auto_city";
    private static final long EVERY_MS = 6L * 60 * 60 * 1000;
    private static volatile boolean running;

    private AutoLocation() { }

    static boolean isAuto(Context c) {
        return c.getSharedPreferences(AppLocationManager.PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, true);
    }

    static void setAuto(Context c, boolean on) {
        c.getSharedPreferences(AppLocationManager.PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO, on).apply();
    }

    /** City found by the last automatic lookup, or the saved location's name. */
    static String cityLabel(Context c) {
        SharedPreferences p = c.getSharedPreferences(AppLocationManager.PREFS_NAME, Context.MODE_PRIVATE);
        String city = p.getString(KEY_CITY, "");
        return city.isEmpty() ? AppLocationManager.getLocationName(c) : city;
    }

    static void refreshIfDue(Context c, Runnable onChanged) { refresh(c, false, onChanged); }

    static void refreshNow(Context c, Runnable onChanged) { refresh(c, true, onChanged); }

    private static void refresh(Context context, boolean force, Runnable onChanged) {
        final Context c = context.getApplicationContext();
        if (!force && !isAuto(c)) return;
        final SharedPreferences p = c.getSharedPreferences(AppLocationManager.PREFS_NAME, Context.MODE_PRIVATE);
        if (!force && System.currentTimeMillis() - p.getLong(KEY_LAST, 0) < EVERY_MS) return;
        if (running) return;
        running = true;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(
                        "https://ipwho.is/?fields=success,latitude,longitude,city,region,country_code").openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "GlassTV/2.9");
                if (conn.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null && sb.length() < 8000) sb.append(line);
                }
                JSONObject o = new JSONObject(sb.toString());
                if (!o.optBoolean("success", false)) return;
                double lat = o.getDouble("latitude");
                double lon = o.getDouble("longitude");
                String city = o.optString("city", "");
                boolean malaysia = "MY".equalsIgnoreCase(o.optString("country_code", ""));
                p.edit().putLong(KEY_LAST, System.currentTimeMillis()).putString(KEY_CITY, city).apply();

                double moved = Math.abs(lat - AppLocationManager.getLatitude(c))
                        + Math.abs(lon - AppLocationManager.getLongitude(c));
                if (!force && moved < 0.08 && p.contains(AppLocationManager.KEY_LATITUDE)) return;

                // Weather uses the exact point; air quality and prayer zone need the nearest Malaysian station.
                AppLocationManager.Station near = malaysia ? AppLocationManager.findNearestStation(lat, lon) : null;
                SharedPreferences.Editor e = p.edit()
                        .putString(AppLocationManager.KEY_LATITUDE, String.valueOf(lat))
                        .putString(AppLocationManager.KEY_LONGITUDE, String.valueOf(lon));
                if (near != null) {
                    e.putString(AppLocationManager.KEY_STATION_ID, near.id)
                            .putString(AppLocationManager.KEY_LOCATION_NAME, near.name)
                            .putString(AppLocationManager.KEY_STATION_PLACE, near.place);
                }
                e.putBoolean(KEY_AUTO, true).apply();
                Log.i(TAG, "Location from IP: " + city + " [" + lat + ", " + lon + "] station="
                        + (near != null ? near.id : "-"));
                WeatherAirQuality.sync(c, null);
                PrayerTimes.sync(c, null);
                if (onChanged != null) new Handler(Looper.getMainLooper()).post(onChanged);
            } catch (Exception ex) {
                Log.w(TAG, "IP lookup failed: " + ex.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
                running = false;
            }
        }, "auto-location").start();
    }
}
