package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import java.util.List;

/** Finds the media session worth showing: one that is playing, else the first. Needs Notification Access. */
final class NowPlaying {
    private NowPlaying() { }

    static MediaController active(Context c) {
        try {
            MediaSessionManager mgr = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (mgr == null) return null;
            List<MediaController> sessions = mgr.getActiveSessions(new ComponentName(c, ChombbMediaListener.class));
            if (sessions == null || sessions.isEmpty()) return null;
            for (MediaController m : sessions) {
                PlaybackState s = m.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return m;
            }
            return sessions.get(0);
        } catch (Exception e) {      // SecurityException when Notification Access is not granted
            return null;
        }
    }

    /** Current position in ms, moved on from the last report while playing. */
    static long position(MediaController m) {
        PlaybackState s = m.getPlaybackState();
        if (s == null) return 0;
        long p = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            p += Math.round((android.os.SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        return Math.max(0, p);
    }
}
