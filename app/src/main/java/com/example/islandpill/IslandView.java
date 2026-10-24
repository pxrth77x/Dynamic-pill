package com.example.islandpill;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The pill. fraction 0 = compact (art + bars), 1 = expanded media card. */
public class IslandView extends FrameLayout {
    public interface Host { void onTap(); void onPrev(); void onPlayPause(); void onNext(); }

    private final float d;
    private final GradientDrawable bg = new GradientDrawable();
    private final FrameLayout compact;
    private final LinearLayout card;
    private final ImageView miniArt, bigArt;
    private final Bars bars;
    private final Line line;
    private final TextView title, artist, elapsed, total, play;
    private float radius;
    private int tintDark = 0xFF1C1C1E;

    public IslandView(Context c, Host host) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        bg.setColors(new int[]{Color.BLACK, Color.BLACK});
        setBackground(bg);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setOnClickListener(v -> host.onTap());

        // compact layer
        compact = new FrameLayout(c);
        miniArt = new ImageView(c);
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        round(miniArt, dp(5));
        LayoutParams mp = new LayoutParams(dp(22), dp(22), Gravity.START | Gravity.CENTER_VERTICAL);
        mp.leftMargin = dp(10);
        compact.addView(miniArt, mp);
        bars = new Bars(c);
        LayoutParams bp = new LayoutParams(dp(24), dp(14), Gravity.END | Gravity.CENTER_VERTICAL);
        bp.rightMargin = dp(14);
        compact.addView(bars, bp);
        addView(compact, new LayoutParams(-1, -1));

        // expanded layer
        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(12));
        card.setVisibility(GONE);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        bigArt = new ImageView(c);
        bigArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bigArt.setBackgroundColor(0x33FFFFFF);
        round(bigArt, dp(10));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(56), dp(56));
        ap.rightMargin = dp(12);
        top.addView(bigArt, ap);

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        title = text(c, 16, Color.WHITE, true);
        artist = text(c, 14, 0xB3FFFFFF, false);
        texts.addView(title);
        texts.addView(artist);
        top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView note = text(c, 16, Color.WHITE, false);
        note.setText("\u266A");
        note.setGravity(Gravity.CENTER);
        note.setBackgroundColor(0x33FFFFFF);
        round(note, dp(10));
        top.addView(note, new LinearLayout.LayoutParams(dp(32), dp(32)));
        card.addView(top);

        line = new Line(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(4));
        lp.topMargin = dp(14);
        card.addView(line, lp);

        LinearLayout times = new LinearLayout(c);
        elapsed = text(c, 11, 0xB3FFFFFF, false);
        total = text(c, 11, 0xB3FFFFFF, false);
        total.setGravity(Gravity.END);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1f));
        times.addView(total, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(times);

        LinearLayout ctl = new LinearLayout(c);
        ctl.setGravity(Gravity.CENTER_VERTICAL);
        ctl.addView(btn(c, "\u21C4", 20, null));
        ctl.addView(btn(c, "\u23EE", 24, host::onPrev));
        play = btn(c, "\u23F8", 32, host::onPlayPause);
        ctl.addView(play);
        ctl.addView(btn(c, "\u23ED", 24, host::onNext));
        ctl.addView(btn(c, "\u21BB", 20, null));
        card.addView(ctl, new LinearLayout.LayoutParams(-1, 0, 1f));

        addView(card, new LayoutParams(dp(300), dp(172), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    }

    private int dp(float v) { return Math.round(v * d); }

    private TextView text(Context c, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView btn(Context c, String s, int sp, Runnable r) {
        TextView t = text(c, sp, Color.WHITE, false);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
        if (r != null) t.setOnClickListener(v -> r.run());
        else t.setAlpha(0.6f);
        return t;
    }

    private static void round(View v, float r) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
    }

    private static int blend(int a, int b, float t) {
        return Color.rgb(
            Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
            Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
            Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** Size of the expanded card layer, so it does not reflow while animating. */
    public void setExpandedSize(int w, int h) {
        LayoutParams p = (LayoutParams) card.getLayoutParams();
        p.width = w;
        p.height = h;
        card.setLayoutParams(p);
    }

    public void setFraction(float f, int compactHeightPx) {
        radius = compactHeightPx / 2f + (dp(30) - compactHeightPx / 2f) * f;
        bg.setColors(new int[]{blend(Color.BLACK, 0xFF2A2A2D, f), blend(Color.BLACK, tintDark, f)});
        compact.setAlpha(Math.max(0f, 1f - f * 2.2f));
        compact.setVisibility(f >= 0.5f ? GONE : VISIBLE);
        card.setAlpha(Math.max(0f, (f - 0.45f) / 0.55f));
        card.setVisibility(f <= 0.01f ? GONE : VISIBLE);
        invalidateOutline();
    }

    public void setMeta(String t, String a, Bitmap art, int tint) {
        title.setText(t == null ? "" : t);
        artist.setText(a == null ? "" : a);
        miniArt.setImageBitmap(art);
        bigArt.setImageBitmap(art);
        tintDark = blend(Color.BLACK, tint, 0.55f);
    }

    public void setPlaying(boolean p) {
        play.setText(p ? "\u23F8" : "\u25B6");
        bars.setActive(p);
    }

    public void setProgress(long pos, long dur) {
        line.set(dur > 0 ? Math.min(1f, pos / (float) dur) : 0f);
        elapsed.setText(fmt(pos));
        total.setText(fmt(dur));
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /** Equalizer-style bars next to the art. Animated while music plays. */
    static class Bars extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;

        Bars(Context c) { super(c); p.setColor(0xFFFF9F43); }

        void setActive(boolean a) { active = a; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float bw = w / 8f, gap = bw / 2f;
            long t = System.currentTimeMillis();
            for (int i = 0; i < 5; i++) {
                float f = active ? 0.25f + 0.75f * (float) Math.abs(Math.sin(t / 170.0 + i * 1.3)) : 0.25f;
                float bh = h * f, x = i * (bw + gap);
                cv.drawRoundRect(new RectF(x, (h - bh) / 2, x + bw, (h + bh) / 2), bw / 2, bw / 2, p);
            }
            if (active) postInvalidateOnAnimation();
        }
    }

    /** Thin progress line. */
    static class Line extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float frac;

        Line(Context c) { super(c); }

        void set(float f) { frac = f; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            p.setColor(0x4DFFFFFF);
            cv.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
            p.setColor(Color.WHITE);
            cv.drawRoundRect(new RectF(0, 0, w * frac, h), h / 2, h / 2, p);
        }
    }
}
