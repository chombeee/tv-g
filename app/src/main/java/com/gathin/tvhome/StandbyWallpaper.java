package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Which picture sits behind the screensaver. Chosen in the settings panel, separate from the Home wallpaper. */
final class StandbyWallpaper {
    static final String PREFS = "standby_prefs";
    static final String HOME = "home", FILE = "file", SLIDESHOW = "slideshow", DARK = "dark";

    private StandbyWallpaper() { }

    static String mode(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", HOME); }

    static String file(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("file", ""); }

    static void set(Context c, String mode, String path) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", mode);
        if (path != null) e.putString("file", path);
        e.apply();
    }

    /** The picture Home is showing right now (MainActivity records it). */
    static File homeCurrent(Context c) {
        String p = c.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).getString("current_path", "");
        File f = new File(p);
        return !p.isEmpty() && f.isFile() ? f : null;
    }

    /** Uploaded pictures first (newest first), then the downloaded ones. */
    static List<File> candidates(Context c) {
        List<File> out = new ArrayList<>();
        File[] up = new File(c.getFilesDir(), "custom_wallpapers").listFiles(
                (d, n) -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"));
        if (up != null) {
            Arrays.sort(up, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            Collections.addAll(out, up);
        }
        try {
            for (File f : OnlineWallpapers.list(c)) if (!out.contains(f)) out.add(f);
        } catch (Exception ignored) { }
        return out;
    }

    static String describe(Context c) {
        switch (mode(c)) {
            case FILE: return new File(file(c)).getName();
            case SLIDESHOW: return "Slideshow";
            case DARK: return "Gelap";
            default: return "Ikut Home";
        }
    }
}
