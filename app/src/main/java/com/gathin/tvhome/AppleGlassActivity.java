package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Isolated experimental UI. It never mutates Glass Classic views or configuration. */
public class AppleGlassActivity extends Activity {
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable panel(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke); return d;
    }
    private TextView text(String str, int size, int color) {
        TextView v = new TextView(this); v.setText(str); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private void chooseStyle() {
        new AlertDialog.Builder(this).setTitle("Launcher Style")
            .setItems(new String[]{"Glass Classic (Original)", "Apple Glass"}, (dialog, which) -> {
                getSharedPreferences("launcher_appearance", MODE_PRIVATE).edit()
                    .putString("launcher_style", which == 0 ? "classic" : "apple").apply();
                if (which == 0) { startActivity(new Intent(this, MainActivity.class)); finish(); }
            }).setNegativeButton("Cancel", null).show();
    }
    private TextView sidebarItem(String label, Runnable action) {
        TextView item = text(label, 18, Color.WHITE);
        item.setFocusable(true); item.setClickable(true);
        item.setPadding(dp(19), dp(13), dp(12), dp(13));
        item.setBackground(panel(0x334B5976, 0x557F9FC5, 17));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.bottomMargin=dp(12); item.setLayoutParams(lp);
        item.setOnClickListener(v -> action.run());
        item.setOnFocusChangeListener((v, focus) -> {
            item.setBackground(panel(focus ? 0xAAFFFFFF : 0x334B5976,
                    focus ? Color.WHITE : 0x557F9FC5, 17));
            item.setTextColor(focus ? 0xFF17203B : Color.WHITE);
            item.setScaleX(focus ? 1.04f : 1f); item.setScaleY(focus ? 1.04f : 1f);
        });
        return item;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setPadding(dp(34), dp(24), dp(34), dp(24));
        shell.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF090F21,0xFF172A45,0xFF241E39}));
        setContentView(shell);
        LinearLayout sidebar=new LinearLayout(this); sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(20),dp(20),dp(20),dp(20));
        sidebar.setBackground(panel(0x703F526E,0x66FFFFFF,26));
        shell.addView(sidebar,new LinearLayout.LayoutParams(dp(235),-1));
        TextView brand=text("CHOMBB",24,Color.WHITE);brand.setTypeface(null,Typeface.BOLD);
        sidebar.addView(brand,new LinearLayout.LayoutParams(-1,dp(64)));
        sidebar.addView(sidebarItem("⌂   Home", () -> {}));
        sidebar.addView(sidebarItem("▦   All Apps", () -> { View list=findViewById(30001); if(list!=null)list.requestFocus(); }));
        sidebar.addView(sidebarItem("☁   Weather", () -> startActivity(new Intent(this, WeatherActivity.class))));
        sidebar.addView(sidebarItem("⚙   Launcher Style",this::chooseStyle));
        TextView note=text("APPLE GLASS  ·  PREVIEW",11,0xFFB9CCE8);
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,0,1); sidebar.addView(new View(this),nlp);
        sidebar.addView(note);
        LinearLayout main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(32),dp(8),dp(8),dp(8));
        shell.addView(main,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        main.addView(bar,new LinearLayout.LayoutParams(-1,dp(75)));
        TextView hello=text("Good evening  ·  Welcome home",23,Color.WHITE);
        hello.setTypeface(null,Typeface.BOLD);bar.addView(hello,new LinearLayout.LayoutParams(0,-1,1));
        TextClock clock=new TextClock(this);clock.setFormat12Hour("h:mm a");clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);clock.setTextSize(30);bar.addView(clock);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(30),dp(20),dp(30),dp(18));
        hero.setBackground(panel(0x66637DA4,0x99B4C9EA,22));
        LinearLayout.LayoutParams hlp=new LinearLayout.LayoutParams(-1,dp(136));hlp.bottomMargin=dp(22);
        main.addView(hero,hlp);
        TextView title=text("Your entertainment, beautifully arranged.",23,Color.WHITE);
        title.setTypeface(null,Typeface.BOLD);hero.addView(title);
        TextView subtitle=text("Apple Glass  ·  Browse your installed TV apps below",15,0xFFE0EAF9);
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);sl.topMargin=dp(12);hero.addView(subtitle,sl);
        TextView heading=text("YOUR APPS",18,Color.WHITE);heading.setTypeface(null,Typeface.BOLD);
        main.addView(heading,new LinearLayout.LayoutParams(-1,dp(48)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);
        main.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        list.setId(30001);scroll.addView(list);
        Intent query=new Intent(Intent.ACTION_MAIN);query.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(query,0);
        if(apps.isEmpty()){query.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            query.addCategory(Intent.CATEGORY_LAUNCHER);apps=getPackageManager().queryIntentActivities(query,0);}
        Collections.sort(apps,Comparator.comparing(a -> a.loadLabel(getPackageManager()).toString().toLowerCase()));
        LinearLayout row=null;int counter=0;
        for(ResolveInfo app: apps) {
            if(app.activityInfo.packageName.equals(getPackageName())) continue;
            if(counter%4==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(105));rp.bottomMargin=dp(12);list.addView(row,rp);}
            LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);tile.setFocusable(true);tile.setClickable(true);
            tile.setBackground(panel(0x66536685,0x6689A9CD,18));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-1,1);tp.rightMargin=dp(12);row.addView(tile,tp);
            ImageView icon=new ImageView(this);
            try {icon.setImageDrawable(app.loadIcon(getPackageManager()));}catch(Exception ignored){}
            tile.addView(icon,new LinearLayout.LayoutParams(dp(43),dp(43)));
            TextView name=text(app.loadLabel(getPackageManager()).toString(),13,Color.WHITE);
            name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(35));
            name.setPadding(dp(5),0,dp(5),0);tile.addView(name,np);
            final ResolveInfo target=app;
            tile.setOnClickListener(v -> {try {Intent i=new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                i.setClassName(target.activityInfo.packageName,target.activityInfo.name);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);
            }catch(Exception e){Intent fallback=getPackageManager().getLaunchIntentForPackage(target.activityInfo.packageName);
                if(fallback!=null)startActivity(fallback);}});
            tile.setOnFocusChangeListener((v,focused)->{
                tile.setBackground(panel(focused?0xDDFFFFFF:0x66536685,focused?Color.WHITE:0x6689A9CD,18));
                name.setTextColor(focused?0xFF182847:Color.WHITE);
                tile.setScaleX(focused?1.05f:1f);tile.setScaleY(focused?1.05f:1f);
            });
            if(counter==0)tile.requestFocus();counter++;
        }
    }
    @Override public void onBackPressed() { chooseStyle(); }
}
