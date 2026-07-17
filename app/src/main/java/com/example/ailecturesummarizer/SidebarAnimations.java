package com.example.ailecturesummarizer;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.recyclerview.widget.RecyclerView;

/**
 * SidebarAnimations — Premium micro-interaction utilities for the sidebar panel.
 *
 * Provides:
 *  • scalePress / scaleRelease   — Elastic touch feedback (press + bounce)
 *  • fadeSlideIn                 — Fade + vertical translate entrance animation
 *  • staggeredRecyclerViewAnim   — Staggered item entrance for RecyclerView
 */
public final class SidebarAnimations {

    // ── Constants ────────────────────────────────────────────────────────────
    private static final float PRESS_SCALE   = 0.96f;
    private static final float BOUNCE_SCALE  = 1.04f;
    private static final float NORMAL_SCALE  = 1.0f;

    private static final long PRESS_MS       = 80L;
    private static final long RELEASE_MS     = 300L;
    private static final long FADE_SLIDE_MS  = 220L;

    private SidebarAnimations() { /* utility class */ }

    // ── Press ────────────────────────────────────────────────────────────────
    /**
     * Animate view to 0.93× scale — call on ACTION_DOWN.
     */
    public static void scalePress(View view) {
        if (view == null) return;
        view.animate()
                .scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
                .setDuration(PRESS_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ── Release ──────────────────────────────────────────────────────────────
    /**
     * Animate view back to 1.0× with a subtle overshoot bounce — call on ACTION_UP.
     */
    public static void scaleRelease(View view) {
        if (view == null) return;
        view.animate()
                .scaleX(BOUNCE_SCALE).scaleY(BOUNCE_SCALE)
                .setDuration(RELEASE_MS / 2)
                .setInterpolator(new OvershootInterpolator(3f))
                .withEndAction(() ->
                        view.animate()
                                .scaleX(NORMAL_SCALE).scaleY(NORMAL_SCALE)
                                .setDuration(RELEASE_MS / 2)
                                .setInterpolator(new DecelerateInterpolator())
                                .start()
                ).start();
    }

    // ── Cancel ───────────────────────────────────────────────────────────────
    /**
     * Snap view back to 1.0× without bounce — call on ACTION_CANCEL.
     */
    public static void scaleCancel(View view) {
        if (view == null) return;
        view.animate()
                .scaleX(NORMAL_SCALE).scaleY(NORMAL_SCALE)
                .setDuration(PRESS_MS)
                .start();
    }

    // ── Fade + Slide-In ──────────────────────────────────────────────────────
    /**
     * Fades a view in from alpha=0 while sliding it up from +30dp.
     * Call when the sidebar drawer is opened.
     */
    public static void fadeSlideIn(View view) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(30f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(FADE_SLIDE_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ── Staggered RecyclerView Animation ────────────────────────────────────
    /**
     * Applies a staggered fade+slide entrance animation to RecyclerView items.
     * Each item enters 100 ms after the previous one.
     */
    public static void staggeredRecyclerViewAnimation(RecyclerView recyclerView) {
        if (recyclerView == null) return;
        android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(true);

        android.view.animation.AlphaAnimation alpha =
                new android.view.animation.AlphaAnimation(0f, 1f);
        alpha.setDuration(200);

        android.view.animation.TranslateAnimation slide =
                new android.view.animation.TranslateAnimation(0, 0, 40f, 0f);
        slide.setDuration(200);

        set.addAnimation(alpha);
        set.addAnimation(slide);
        set.setInterpolator(new DecelerateInterpolator());

        android.view.animation.LayoutAnimationController controller =
                new android.view.animation.LayoutAnimationController(set, 0.12f);

        recyclerView.setLayoutAnimation(controller);
        recyclerView.scheduleLayoutAnimation();
    }
}
