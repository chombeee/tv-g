package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.Log;

/**
 * System screensaver. It shows the same StandbyView as Home's own idle screen: the chosen wallpaper with the
 * clock, weather and Now Playing cards. After two hours the TV's screen is turned off.
 */
public class ClockDream extends DreamService {

    private static final String TAG = "TvHome.ClockDream";
    private static final long CHECK_MS = 60_000L;
    private static final long DREAM_SLEEP_TIMEOUT_MS = 2L * 60 * 60 * 1000L; // 2 hours

    private final Handler handler = new Handler(Looper.getMainLooper());
    private StandbyView view;
    private long dreamStartTime = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (dreamStartTime > 0 && android.os.SystemClock.elapsedRealtime() - dreamStartTime >= DREAM_SLEEP_TIMEOUT_MS) {
                Log.i(TAG, "Screensaver ran for 2 hours: turning off TV display");
                HomeKeyService.sleepScreen();
                try {
                    Runtime.getRuntime().exec("input keyevent 223");
                } catch (Exception ignored) {
                }
                finish();
                return;
            }
            handler.postDelayed(this, CHECK_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        view = new StandbyView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreamStartTime = android.os.SystemClock.elapsedRealtime();
        view.setAlpha(0f);
        view.onShow();
        view.animate().alpha(1f).setDuration(1200).start();
        handler.postDelayed(tick, CHECK_MS);
    }

    @Override
    public void onDreamingStopped() {
        dreamStartTime = 0;
        handler.removeCallbacks(tick);
        if (view != null) view.onHide();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dreamStartTime = 0;
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
