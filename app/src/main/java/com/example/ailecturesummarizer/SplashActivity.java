package com.example.ailecturesummarizer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;

/**
 * SplashActivity — entry point shown for ~2 seconds on app launch.
 *
 * Session check:
 *   - Reads the JWT access_token from SharedPreferences (key: "access_token")
 *   - If a token exists → user was previously logged in → go to MainActivity
 *   - Otherwise         → route to LoginActivity
 *
 * Uses SupabaseAuthManager.isLoggedIn() which checks the "access_token" key,
 * replacing the old boolean "logged_in" flag that worked with SQLite.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 2000L;
    private static final long FADE_DURATION_MS    = 700L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView ivLogo    = findViewById(R.id.ivSplashLogo);
        TextView  tvTitle   = findViewById(R.id.tvSplashTitle);
        TextView  tvTagline = findViewById(R.id.tvSplashTagline);

        // ── Staggered fade-in animations ──────────────────────────────────────
        if (ivLogo != null) {
            ivLogo.animate()
                    .alpha(1f)
                    .setDuration(FADE_DURATION_MS)
                    .setStartDelay(150)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        if (tvTitle != null) {
            tvTitle.animate()
                    .alpha(1f)
                    .setDuration(FADE_DURATION_MS)
                    .setStartDelay(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        if (tvTagline != null) {
            tvTagline.animate()
                    .alpha(1f)
                    .setDuration(FADE_DURATION_MS)
                    .setStartDelay(450)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Use SupabaseAuthManager to check for a persisted JWT access_token
            boolean loggedIn = SupabaseAuthManager.getInstance().isLoggedIn(SplashActivity.this);
            Class<?> destination = loggedIn ? MainActivity.class : LoginActivity.class;

            Intent intent = new Intent(SplashActivity.this, destination);

            // Crossfade transition
            ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                    SplashActivity.this,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );

            startActivity(intent, options.toBundle());
            finish();
        }, SPLASH_DURATION_MS);
    }
}
