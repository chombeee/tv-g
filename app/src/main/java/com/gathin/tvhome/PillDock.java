package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hero home layout's dock: one row of glass pills, one for every app on the box, that scrolls sideways.
 * The focused pill grows and its name shows large above the row. It reads the same tile list as the classic grid
 * (so the order, hidden apps and pinned favourites stay the same) and launches through the same code.
 */
public class PillDock extends LinearLayout {

    interface Host {
        void focusTop();
        void appInfo(int tilePosition);
    }

    private final AppAdapter adapter;
    private final Host host;
    private final float u;
    private Typeface face;
    private final TextView name;
    private final HorizontalScrollView scroller;
    private final LinearLayout row;
    private final List<View> pills = new ArrayList<>();
    private final List<Integer> positions = new ArrayList<>();
    private int lastIndex = 0;

    PillDock(Context c, AppAdapter adapter, Host host) {
        super(c);
        this.adapter = adapter;
        this.host = host;
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        u = dm.widthPixels / 1920f;
        try { face = c.getResources().getFont(R.font.outfit); } catch (Exception e) { face = Typeface.DEFAULT; }
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        name = new TextView(c);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, 64 * u);
        name.setTextColor(0xFFFFFFFF);
        name.setTypeface(face);
        name.setIncludeFontPadding(false);
        name.setSingleLine(true);
        name.setShadowLayer(px(10), 0, px(3), 0x88000000);
        try { name.setFontVariationSettings("'wght' 700"); } catch (Exception ignored) { }
        LayoutParams np = new LayoutParams(-1, -2);
        np.leftMargin = px(110);
        np.bottomMargin = px(10);
        addView(name, np);

        scroller = new HorizontalScrollView(c);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setSmoothScrollingEnabled(true);
        scroller.setPadding(0, px(26), 0, px(26));
        row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(px(110), 0, px(110), 0);
        scroller.addView(row, new FrameLayout.LayoutParams(-2, -2));
        addView(scroller, new LayoutParams(-1, -2));
    }

    private int px(float units) { return Math.round(units * u); }

    /** Rebuilds the pills from the adapter's tiles: every plain app, in the grid's order. */
    void refresh() {
        String focusedKey = null;
        View f = findFocus();
        if (f != null && f.getTag() instanceof Integer) focusedKey = adapter.keyAt((Integer) f.getTag());
        row.removeAllViews();
        pills.clear();
        positions.clear();
        for (int i = 0; i < adapter.tileCount(); i++) {
            AppAdapter.Entry e = adapter.tileAt(i);
            if (e.kind != AppAdapter.Entry.APP) continue;
            View pill = makePill(e, i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(250), px(128));
            lp.rightMargin = px(26);
            row.addView(pill, lp);
            pills.add(pill);
            positions.add(i);
        }
        lastIndex = Math.max(0, Math.min(lastIndex, pills.size() - 1));
        if (focusedKey != null) {
            int pos = adapter.positionOf(focusedKey);
            int idx = positions.indexOf(pos);
            if (idx >= 0) pills.get(idx).requestFocus();
        } else {
            name.setText("");
        }
    }

    boolean focusCurrent() {
        if (pills.isEmpty()) return false;
        View v = pills.get(Math.max(0, Math.min(lastIndex, pills.size() - 1)));
        return v.requestFocus();
    }

    private Drawable copy(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable(getResources()).mutate() : d;
    }

    private View makePill(AppAdapter.Entry e, final int tilePos) {
        Context c = getContext();
        final FrameLayout pill = new FrameLayout(c);
        pill.setTag(tilePos);
        pill.setFocusable(true);
        pill.setClickable(true);
        pill.setClipChildren(true);
        final int h = px(128);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(h / 2f);
        bg.setColor(0x66263248);
        pill.setBackground(bg);
        pill.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), v.getHeight() / 2f);
            }
        });
        pill.setClipToOutline(true);

        Drawable art = copy(e.banner);
        if (art != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(art);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pill.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            View shade = new View(c);
            shade.setBackgroundColor(0x2E000000);
            pill.addView(shade, new FrameLayout.LayoutParams(-1, -1));
            pill.setTag(R.id.card, shade);
        } else {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(HORIZONTAL);
            col.setGravity(Gravity.CENTER);
            col.setPadding(px(24), 0, px(24), 0);
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(copy(e.icon));
            col.addView(icon, new LinearLayout.LayoutParams(px(64), px(64)));
            TextView t = new TextView(c);
            t.setText(e.label);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * u);
            t.setTypeface(face);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.leftMargin = px(14);
            col.addView(t, tp);
            pill.addView(col, new FrameLayout.LayoutParams(-1, -1));
        }

        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(h / 2f);
        ring.setColor(0x00000000);
        ring.setStroke(Math.max(2, px(3)), 0x00FFFFFF);
        pill.setForeground(ring);

        final String label = e.label;
        pill.setOnFocusChangeListener((v, focused) -> {
            ring.setStroke(Math.max(2, px(4)), focused ? 0xFFFFFFFF : 0x00FFFFFF);
            v.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f).setDuration(140).start();
            v.setElevation(focused ? px(12) : 0f);
            Object shade = v.getTag(R.id.card);
            if (shade instanceof View) ((View) shade).animate().alpha(focused ? 0f : 1f).setDuration(140).start();
            if (focused) {
                name.setText(label);
                int idx = pills.indexOf(v);
                if (idx >= 0) lastIndex = idx;
            }
        });
        pill.setOnClickListener(v -> adapter.launchAt(tilePos));
        pill.setOnLongClickListener(v -> { host.appInfo(tilePos); return true; });
        pill.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { host.focusTop(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            return false;
        });
        return pill;
    }
}
