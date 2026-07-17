package com.example.ailecturesummarizer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * ResetPasswordActivity — handles the deep link {@code myapp://reset-password}
 * that Supabase inserts into the password-reset email.
 *
 * The deep link URL contains the JWT access_token in its FRAGMENT (after the '#'):
 *   myapp://reset-password#access_token=<JWT>&type=recovery&...
 *
 * This Activity:
 *  1. Extracts the access_token from the fragment
 *  2. Collects the new password from the user
 *  3. Sends an authenticated PUT to Supabase /auth/v1/user via SupabaseAuthManager
 *  4. On success → navigates to LoginActivity so the user can sign in
 */
public class ResetPasswordActivity extends AppCompatActivity {

    // Views
    private TextInputLayout   tilNewPassword, tilConfirmNewPassword;
    private TextInputEditText etNewPassword, etConfirmNewPassword;
    private MaterialButton    btnUpdatePassword;
    private ProgressBar       progressReset;
    private LinearLayout      bodyCard;

    // Extracted from deep link
    private String accessToken;

    // Auth
    private SupabaseAuthManager authManager;

    // Animations
    private Animation scaleDown, scaleUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        authManager = SupabaseAuthManager.getInstance();
        extractTokenFromDeepLink();
        bindViews();
        loadAnimations();
        attachListeners();
        runEntranceAnimation();

        // Guard: if no token was found in the deep link, the link is invalid/expired
        if (TextUtils.isEmpty(accessToken)) {
            Toast.makeText(this,
                    "Reset link is invalid or has expired. Please request a new one.",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, ForgotPasswordActivity.class));
            finish();
        }
    }

    // ─── Deep Link Token Extraction ──────────────────────────────────────────

    /**
     * Supabase encodes the access_token in the URI FRAGMENT (the part after '#'),
     * not as a query parameter. We parse it manually from the fragment string.
     *
     * Example fragment: access_token=eyJ...&token_type=bearer&type=recovery
     */
    private void extractTokenFromDeepLink() {
        Intent intent = getIntent();
        Uri    data   = intent != null ? intent.getData() : null;

        if (data == null) return;

        // The fragment contains key=value pairs separated by '&'
        String fragment = data.getFragment();
        if (fragment == null || fragment.isEmpty()) return;

        for (String part : fragment.split("&")) {
            if (part.startsWith("access_token=")) {
                accessToken = part.substring("access_token=".length());
                break;
            }
        }
    }

    // ─── View Binding ────────────────────────────────────────────────────────

    private void bindViews() {
        tilNewPassword        = findViewById(R.id.tilNewPassword);
        tilConfirmNewPassword = findViewById(R.id.tilConfirmNewPassword);
        etNewPassword         = findViewById(R.id.etNewPassword);
        etConfirmNewPassword  = findViewById(R.id.etConfirmNewPassword);
        btnUpdatePassword     = findViewById(R.id.btnUpdatePassword);
        progressReset         = findViewById(R.id.progressReset);
        bodyCard              = findViewById(R.id.bodyCard);
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
        if (btnUpdatePassword != null) {
            btnUpdatePassword.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    v.startAnimation(scaleDown);
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                        || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.startAnimation(scaleUp);
                }
                return false;
            });
            btnUpdatePassword.setOnClickListener(v -> handleUpdatePassword());
        }
    }

    // ─── Update Password ─────────────────────────────────────────────────────

    private void handleUpdatePassword() {
        if (tilNewPassword != null)        tilNewPassword.setError(null);
        if (tilConfirmNewPassword != null) tilConfirmNewPassword.setError(null);

        String newPassword     = getText(etNewPassword);
        String confirmPassword = getText(etConfirmNewPassword);

        if (TextUtils.isEmpty(newPassword)) {
            if (tilNewPassword != null) tilNewPassword.setError("New password is required");
            return;
        }
        if (newPassword.length() < 8) {
            if (tilNewPassword != null)
                tilNewPassword.setError("Password must be at least 8 characters");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            if (tilConfirmNewPassword != null)
                tilConfirmNewPassword.setError("Passwords do not match");
            return;
        }

        setLoading(true);

        authManager.updateUserPassword(accessToken, newPassword,
                new SupabaseAuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(String message) {
                        setLoading(false);
                        showMessage(message);
                        // Clear any old session since the user must log in fresh
                        authManager.clearSession(ResetPasswordActivity.this);
                        // Navigate to Login after a short delay
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> {
                                    Intent intent = new Intent(
                                            ResetPasswordActivity.this, LoginActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                }, 2000);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false);
                        showMessage(errorMessage);
                    }
                });
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        if (btnUpdatePassword != null) btnUpdatePassword.setEnabled(!loading);
        if (progressReset != null) {
            progressReset.setVisibility(loading
                    ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void showMessage(String msg) {
        android.view.View root = findViewById(android.R.id.content);
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
    }

    private String getText(TextInputEditText field) {
        return field != null && field.getText() != null
                ? field.getText().toString() : "";
    }
}
