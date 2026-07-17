package com.example.ailecturesummarizer;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * ForgotPasswordActivity — collects user's email and calls Supabase /auth/v1/recover
 * to send a password-reset magic link that deep-links to ResetPasswordActivity via
 * the custom scheme: {@code myapp://reset-password}.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    // Views
    private TextInputLayout   tilForgotEmail;
    private TextInputEditText etForgotEmail;
    private MaterialButton    btnSendResetLink;
    private TextView          tvBackToLogin;
    private ProgressBar       progressForgot;
    private LinearLayout      bodyCard;

    // Auth
    private SupabaseAuthManager authManager;

    // Animations
    private Animation scaleDown, scaleUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        authManager = SupabaseAuthManager.getInstance();
        bindViews();
        loadAnimations();
        attachListeners();
        runEntranceAnimation();
    }

    // ─── View Binding ────────────────────────────────────────────────────────

    private void bindViews() {
        tilForgotEmail  = findViewById(R.id.tilForgotEmail);
        etForgotEmail   = findViewById(R.id.etForgotEmail);
        btnSendResetLink = findViewById(R.id.btnSendResetLink);
        tvBackToLogin   = findViewById(R.id.tvBackToLogin);
        progressForgot  = findViewById(R.id.progressForgot);
        bodyCard        = findViewById(R.id.bodyCard);

        // Handle the header back arrow
        android.widget.ImageView ivBack = findViewById(R.id.ivBack);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }
    }

    // ─── Animations ─────────────────────────────────────────────────────────

    private void loadAnimations() {
        scaleDown = AnimationUtils.loadAnimation(this, R.anim.btn_scale_down);
        scaleUp   = AnimationUtils.loadAnimation(this, R.anim.btn_scale_up);
    }

    private void runEntranceAnimation() {
        if (bodyCard == null) return;
        bodyCard.setAlpha(0f);
        bodyCard.setTranslationY(60f);
        bodyCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .setStartDelay(160)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    // ─── Listeners ───────────────────────────────────────────────────────────

    private void attachListeners() {
        if (btnSendResetLink != null) {
            btnSendResetLink.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    v.startAnimation(scaleDown);
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                        || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.startAnimation(scaleUp);
                }
                return false;
            });
            btnSendResetLink.setOnClickListener(v -> handleSendResetLink());
        }

        if (tvBackToLogin != null) {
            tvBackToLogin.setOnClickListener(v -> {
                startActivity(new Intent(ForgotPasswordActivity.this, LoginActivity.class));
                finish();
            });
        }
    }

    private void handleSendResetLink() {
        if (tilForgotEmail != null) tilForgotEmail.setError(null);

        String email = etForgotEmail != null && etForgotEmail.getText() != null
                ? etForgotEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            if (tilForgotEmail != null) tilForgotEmail.setError("Email is required");
            return;
        }

        setLoading(true);

        try {
            authManager.resetPasswordForEmail(email, new SupabaseAuthManager.AuthCallback() {
                @Override
                public void onSuccess(String message) {
                    setLoading(false);
                    if (tilForgotEmail != null) {
                        tilForgotEmail.setError(null);
                    }
                    showMessage("A password reset link has been sent to your email!");
                    // Delay then go back to Login
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(() -> {
                                startActivity(new Intent(ForgotPasswordActivity.this, LoginActivity.class));
                                finish();
                            }, 2500);
                }

                @Override
                public void onError(String errorMessage) {
                    setLoading(false);
                    showMessage(errorMessage);
                }
            });
        } catch (Exception e) {
            setLoading(false);
            showMessage("An error occurred while processing the request. Please try again.");
            android.util.Log.e("ForgotPassword", "Exception triggering password reset recovery", e);
        }
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        if (btnSendResetLink != null) btnSendResetLink.setEnabled(!loading);
        if (progressForgot != null) {
            progressForgot.setVisibility(loading
                    ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void showMessage(String msg) {
        android.view.View root = findViewById(android.R.id.content);
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
    }
}
