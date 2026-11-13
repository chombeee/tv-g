package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.util.ArrayList;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;

/** Optional remote-friendly widget surface. No privileged app-stopping or false lyrics sync. */
final class ChombbWidgets {
    private static final String PREF = "chombb_widgets";
    private static volatile String lyricKey = "";
    private static volatile String lyricText = "";
    private static volatile boolean lyricsLoading;
    private static final Handler ui = new Handler(Looper.getMainLooper());
    static final String[] NAMES = {"Prayer Times", "Now Playing", "Live Lyrics", "Calendar", "Recent Apps"};
    static boolean enabled(Context c, int i) { return c.getSharedPreferences(PREF,0).getBoolean("widget_"+i,false); }
    static void toggle(Context c,int i) { c.getSharedPreferences(PREF,0).edit().putBoolean("widget_"+i,!enabled(c,i)).apply(); }
    static int width(Context c) {return c.getSharedPreferences(PREF,0).getInt("width",280);}
    static void changeWidth(Context c) {int n=width(c);c.getSharedPreferences(PREF,0).edit().putInt("width",n==280?340:n==340?400:280).apply();}
    static int order(Context c,int i){return c.getSharedPreferences(PREF,0).getInt("order_"+i,i);}
    static void move(Context c,int i,boolean up){
        int position=order(c,i),target=position+(up?-1:1);
        if(target<0 || target>=NAMES.length)return;
        SharedPreferences.Editor e=c.getSharedPreferences(PREF,0).edit();
        for(int j=0;j<NAMES.length;j++)if(j!=i && order(c,j)==target)e.putInt("order_"+j,position);
        e.putInt("order_"+i,target).apply();
    }
    static void configure(Activity a,Runnable refreshed) {
        String[] opts = new String[NAMES.length];
        for(int i=0;i<opts.length;i++) opts[i]=(enabled(a,i)?"✓  ":"＋  ")+NAMES[i];
        new AlertDialog.Builder(a).setTitle("Add to Home · Widgets")
          .setItems(opts,(d,index)->{toggle(a,index); refreshed.run(); configure(a,refreshed);})
          .setPositiveButton("Done",(d,w)->{}).show();
    }
    static TextView tile(Activity a,int i) {
        TextView v=new TextView(a);
        v.setTextColor(Color.WHITE); v.setTextSize(15); v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(a,18),dp(a,10),dp(a,18),dp(a,10));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xBB18283A);bg.setCornerRadius(dp(a,18));bg.setStroke(dp(a,1),0x4488BBDD);v.setBackground(bg);
        v.setFocusable(true);
        v.setOnFocusChangeListener((view,focused)->v.setScaleX(focused?1.03f:1f));
        v.setOnClickListener(view->open(a,i));
        return v;
    }
    static void update(Activity a,TextView v,int i) {
        if(i==0) v.setText("PRAYER TIMES\n"+prayerCountdown(a));
        else if(i==1) {
            MediaController m=media(a);
            MediaMetadata md=m==null?null:m.getMetadata();
            String title=md==null?"No active playback":md.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist=md==null?"":md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            v.setText("NOW PLAYING  ▶ / ❚❚\n"+(title==null?"No active playback":title)+(artist==null?"":" · "+artist));
        } else if(i==2) {
            MediaController m=media(a); MediaMetadata md=m==null?null:m.getMetadata();
            String title=md==null?null:md.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist=md==null?null:md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if(title==null || title.isEmpty()) v.setText("LIVE LYRICS\nPlay music to load lyrics");
            else {
                requestLyrics(title,artist==null?"":artist);
                v.setText("LIVE LYRICS\n"+title+"\n"+currentLyric(m));
            }
        } else if(i==3) {
            v.setText("CALENDAR\n"+monthGrid(Calendar.getInstance()));
        } else {
            List<RecentAppItem> apps=RecentAppsManager.getRecentApps(a);
            v.setText("RECENT APPS\n"+apps.size()+" recent · Open list");
        }
    }
    private static MediaController media(Context c) {
        try {
            MediaSessionManager mgr=(MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> sessions=mgr.getActiveSessions(new android.content.ComponentName(c,ChombbMediaListener.class));
            return sessions.isEmpty()?null:sessions.get(0);
        } catch(Exception e) {return null;}
    }
    private static void open(Activity a,int i) {
        if(i==4) {a.startActivity(new Intent(a,RecentAppsActivity.class));return;}
        if(i==1) {
            MediaController m=media(a);
            if(m==null){Toast.makeText(a,"Allow Notification Access for media controls",Toast.LENGTH_LONG).show();return;}
            PlaybackState state=m.getPlaybackState();
            if(state!=null && state.getState()==PlaybackState.STATE_PLAYING)m.getTransportControls().pause();
            else m.getTransportControls().play();return;
        }
        if(i==2){new AlertDialog.Builder(a).setTitle("Live Lyrics")
          .setMessage(lyricText.isEmpty()?"Lyrics unavailable or still loading. Requires internet and an active media session.":lyricText)
          .setPositiveButton("OK",null).show();return;}
        if(i==0){new AlertDialog.Builder(a).setTitle("Prayer Times").setMessage(PrayerTimes.getFormatted(a)).setPositiveButton("OK",null).show();return;}
        Calendar cal=Calendar.getInstance();new AlertDialog.Builder(a).setTitle("Calendar")
          .setMessage(monthGrid(cal))
          .setPositiveButton("OK",null).show();
    }
    private static String prayerCountdown(Context c) {
        return PrayerTimes.getNextPrayerCountdown(c);
    }
    private static String monthGrid(Calendar date) {
        Calendar first=(Calendar)date.clone();first.set(Calendar.DAY_OF_MONTH,1);
        int offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7;
        int count=first.getActualMaximum(Calendar.DAY_OF_MONTH);
        StringBuilder out=new StringBuilder(new SimpleDateFormat("MMMM yyyy",Locale.getDefault()).format(date.getTime()));
        out.append("\nMo Tu We Th Fr Sa Su");
        for(int i=0;i<offset;i++)out.append("   ");
        for(int day=1;day<=count;day++){
            int col=(offset+day-1)%7;
            if(col==0)out.append("\n");
            if(day<10)out.append(' ');
            out.append(day);
            if(col!=6)out.append(' ');
        }
        return out.toString();
    }
    private static void requestLyrics(String title,String artist) {
        final String key=title+"|"+artist;
        if(key.equals(lyricKey) || lyricsLoading)return;
        lyricKey=key;lyricText="";lyricsLoading=true;
        new Thread(()->{
            String result="";HttpURLConnection conn=null;
            try {
                String q="https://lrclib.net/api/search?track_name="+URLEncoder.encode(title,"UTF-8")
                    +"&artist_name="+URLEncoder.encode(artist,"UTF-8");
                conn=(HttpURLConnection)new URL(q).openConnection();
                conn.setConnectTimeout(5000);conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent","GlassTV-CHOMBB/2.8.8");
                if(conn.getResponseCode()==200){
                    BufferedReader reader=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
                    StringBuilder json=new StringBuilder();String line;
                    while((line=reader.readLine())!=null && json.length()<100000)json.append(line);
                    reader.close();
                    org.json.JSONArray items=new org.json.JSONArray(json.toString());
                    if(items.length()>0){JSONObject obj=items.getJSONObject(0);
                        result=obj.optString("syncedLyrics","");
                        if(result.isEmpty())result=obj.optString("plainLyrics","");}
                }
            }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}
            final String lyrics=result;
            ui.post(()->{if(key.equals(lyricKey)){lyricText=lyrics;lyricsLoading=false;}});
        },"chombb-lyrics").start();
    }
    private static String currentLyric(MediaController controller) {
        if(lyricsLoading)return "Loading lyrics…";
        if(lyricText.isEmpty())return "Lyrics not available";
        PlaybackState state=controller==null?null:controller.getPlaybackState();
        if(state==null || !lyricText.contains("["))return lyricText.length()>160?lyricText.substring(0,160):lyricText;
        long position=state.getPosition();
        if(state.getState()==PlaybackState.STATE_PLAYING)
            position+=Math.round((android.os.SystemClock.elapsedRealtime()-state.getLastPositionUpdateTime())*state.getPlaybackSpeed());
        String active="";
        for(String line:lyricText.split("\n")){
            if(!line.startsWith("[") || line.length()<10)continue;
            int end=line.indexOf(']');if(end<0)continue;
            try {String[] parts=line.substring(1,end).split(":");
                long at=Long.parseLong(parts[0])*60000+Math.round(Double.parseDouble(parts[1])*1000);
                if(at<=position)active=line.substring(end+1).trim();else break;
            }catch(Exception ignored){}
        }
        return active.isEmpty()?"♪":active;
    }
    private static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+0.5f);}
}
