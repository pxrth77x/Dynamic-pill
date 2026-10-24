package com.example.islandpill;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import java.util.List;

public class IslandController implements IslandView.Host {
    public static final String ACTION = "com.example.islandpill.UPDATE";

    private final Context ctx;
    private final float density;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private IslandView view;
    private WindowManager.LayoutParams lp;
    private boolean attached, expanded;
    private float fraction;
    private ValueAnimator anim;
    private int wDp = 140, hDp = 34, topDp = 8;
    private int expW, expH;

    private MediaController ctl;
    private MediaController.Callback cb;

    private final Runnable autoCollapse = this::collapse;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateProgress();
            if (expanded) main.postDelayed(this, 400);
        }
    };

    public IslandController(Context ctx) {
        this.ctx = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    public void start() {
        try {
            XSharedPreferences p = new XSharedPreferences("com.example.islandpill", "settings");
            p.reload();
            wDp = p.getInt("w", wDp);
            hDp = p.getInt("h", hDp);
            topDp = p.getInt("top", topDp);
        } catch (Throwable t) {
            XposedBridge.log("IslandPill prefs: " + t);
        }
        main.post(this::init);
        registerReceiver();
    }

    private int px(int dp) { return Math.round(dp * density); }

    private void init() {
        try {
            wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
            expW = Math.min(screenW - px(24), px(420));
            expH = px(172);

            view = new IslandView(ctx, this);
            view.setExpandedSize(expW, expH);

            lp = new WindowManager.LayoutParams(
                px(wDp), px(hDp),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = px(topDp);
            lp.setTitle("IslandPill");
            view.setFraction(0f, px(hDp));

            MediaSessionManager msm =
                (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            msm.addOnActiveSessionsChangedListener(this::pick, null, main);
            pick(msm.getActiveSessions(null));
        } catch (Throwable t) {
            XposedBridge.log("IslandPill init failed: " + t);
        }
    }

    // ---- media sessions ----

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
            }
            if (best == null && !list.isEmpty()) best = list.get(0);
        }
        if (ctl != null && best != null && ctl.getSessionToken().equals(best.getSessionToken())) {
            refreshState();
            return;
        }
        if (ctl != null && cb != null) ctl.unregisterCallback(cb);
        ctl = best;
        if (ctl == null) { detach(); return; }
        cb = new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata m) { refreshMeta(); }
            @Override public void onPlaybackStateChanged(PlaybackState s) { refreshState(); }
            @Override public void onSessionDestroyed() {
                try {
                    MediaSessionManager msm =
                        (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
                    pick(msm.getActiveSessions(null));
                } catch (Throwable ignored) { detach(); }
            }
        };
        ctl.registerCallback(cb, main);
        refreshMeta();
        refreshState();
    }

    private void refreshMeta() {
        if (ctl == null || view == null) return;
        MediaMetadata m = ctl.getMetadata();
        if (m == null) return;
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null) t = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        int tint = 0xFF444444;
        if (art != null) {
            try { tint = Bitmap.createScaledBitmap(art, 1, 1, true).getPixel(0, 0) | 0xFF000000; }
            catch (Throwable ignored) {}
        }
        view.setMeta(t, a, art, tint);
        view.setFraction(fraction, px(hDp));
        updateProgress();
    }

    private void refreshState() {
        if (ctl == null || view == null) return;
        PlaybackState s = ctl.getPlaybackState();
        boolean visible = s != null && s.getState() != PlaybackState.STATE_NONE
            && s.getState() != PlaybackState.STATE_STOPPED;
        if (!visible) { detach(); return; }
        view.setPlaying(s.getState() == PlaybackState.STATE_PLAYING);
        attach();
        updateProgress();
    }

    private void updateProgress() {
        if (ctl == null || view == null) return;
        PlaybackState s = ctl.getPlaybackState();
        MediaMetadata m = ctl.getMetadata();
        if (s == null) return;
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime())
                * s.getPlaybackSpeed());
        }
        long dur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        view.setProgress(pos, dur);
    }

    // ---- window ----

    private void attach() {
        if (attached || view == null) return;
        try { apply(fraction); wm.addView(view, lp); attached = true; }
        catch (Throwable t) { XposedBridge.log("IslandPill attach: " + t); }
    }

    private void detach() {
        if (!attached) return;
        try { wm.removeView(view); } catch (Throwable ignored) {}
        attached = false;
        expanded = false;
        fraction = 0f;
        if (anim != null) anim.cancel();
        main.removeCallbacks(autoCollapse);
        main.removeCallbacks(ticker);
        view.setFraction(0f, px(hDp));
    }

    private void apply(float f) {
        lp.width = Math.round(px(wDp) + (expW - px(wDp)) * f);
        lp.height = Math.round(px(hDp) + (expH - px(hDp)) * f);
        view.setFraction(f, px(hDp));
        if (attached) {
            try { wm.updateViewLayout(view, lp); } catch (Throwable ignored) {}
        }
    }

    private void animateTo(float target) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(fraction, target);
        anim.setDuration(320);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(a -> { fraction = (float) a.getAnimatedValue(); apply(fraction); });
        anim.start();
    }

    private void expand() {
        expanded = true;
        animateTo(1f);
        updateProgress();
        main.removeCallbacks(ticker);
        main.post(ticker);
        resetAutoCollapse();
    }

    private void collapse() {
        expanded = false;
        main.removeCallbacks(autoCollapse);
        main.removeCallbacks(ticker);
        animateTo(0f);
    }

    private void resetAutoCollapse() {
        main.removeCallbacks(autoCollapse);
        main.postDelayed(autoCollapse, 6000);
    }

    // ---- IslandView.Host ----

    @Override public void onTap() { if (expanded) collapse(); else expand(); }

    @Override public void onPrev() {
        if (ctl != null) ctl.getTransportControls().skipToPrevious();
        resetAutoCollapse();
    }

    @Override public void onNext() {
        if (ctl != null) ctl.getTransportControls().skipToNext();
        resetAutoCollapse();
    }

    @Override public void onPlayPause() {
        if (ctl != null) {
            PlaybackState s = ctl.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                ctl.getTransportControls().pause();
            } else {
                ctl.getTransportControls().play();
            }
        }
        resetAutoCollapse();
    }

    // ---- settings broadcast ----

    private void registerReceiver() {
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                wDp = i.getIntExtra("w", wDp);
                hDp = i.getIntExtra("h", hDp);
                topDp = i.getIntExtra("top", topDp);
                if (lp != null && view != null) {
                    lp.y = px(topDp);
                    apply(fraction);
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        } else {
            ctx.registerReceiver(r, f);
        }
    }
}
