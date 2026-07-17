package com.example.ailecturesummarizer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * LoginActivity — authenticates via Supabase email/password or Google OAuth.
 *
 * Animations:
 *  - Body card fades in + slides up on launch
 *  - Buttons have scale-down/up press feedback
 *
 * On success: saves JWT token to SharedPreferences → navigates to MainActivity
 */
public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    // Views
    private TextInputLayout    tilEmail, tilPassword;
    private TextInputEditText  etEmail, etPassword;
    private MaterialButton     btnLogin;
    private LinearLayout       btnGoogleSignIn;
    private TextView           tvForgotPassword, tvGoToRegister;
    private ProgressBar        progressLogin;
    private LinearLayout       bodyCard;
    private FrameLayout        verificationOverlay;

    // Auth
    private SupabaseAuthManager authManager;
    private GoogleSignInClient  googleSignInClient;

    // Animations
    private Animation scaleDown, scaleUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Fast-path: already logged in ────────────────────────────────────
        authManager = SupabaseAuthManager.getInstance();
        if (authManager.isLoggedIn(this)) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);
        bindViews();
        configureGoogleSignIn();
        loadAnimations();
        styleNavigationLinks();
        attachListeners();
        runEntranceAnimation();

        handleDeepLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    private void handleDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;

        Uri data = intent.getData();
        String accessToken = extractToken(data, "access_token");
        String refreshToken = extractToken(data, "refresh_token");

        if (accessToken != null && !accessToken.isEmpty()) {
            showVerificationOverlay(true);
            
            // Finalize session natively
            authManager.saveSession(this, accessToken, refreshToken, "", "");
            
            // Fetch profile to complete the session context
            authManager.fetchUserProfile(this, new SupabaseAuthManager.ProfileCallback() {
                @Override
                public void onSuccess(String displayName, String avatarUrl) {
                    authManager.saveSession(LoginActivity.this, accessToken, refreshToken, 
                            authManager.getCachedUserEmail(LoginActivity.this), displayName);
                    showVerificationOverlay(false);
                    goToMain();
                }

                @Override
                public void onError(String errorMessage) {
                    showVerificationOverlay(false);
                    showError("Session validation failed: " + errorMessage);
                }
            });
        }
    }

    private String extractToken(Uri data, String key) {
        // Check query parameters first
        String token = data.getQueryParameter(key);
        if (token != null) return token;

        // Check fragment (Supabase often uses #access_token=...)
        String fragment = data.getFragment();
        if (fragment != null && fragment.contains(key + "=")) {
            String[] parts = fragment.split("&");
            for (String part : parts) {
                if (part.startsWith(key + "=")) {
                    return part.substring(key.length() + 1);
                }
            }
        }
        return null;
    }

    private void showVerificationOverlay(boolean show) {
        if (verificationOverlay != null) {
            verificationOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // ─── View Binding ────────────────────────────────────────────────────────

    private void bindViews() {
        tilEmail        = findViewById(R.id.tilEmail);
        tilPassword     = findViewById(R.id.tilPassword);
        etEmail         = findViewById(R.id.etEmail);
        etPassword      = findViewById(R.id.etPassword);
        btnLogin        = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvGoToRegister  = findViewById(R.id.tvGoToRegister);
        progressLogin   = findViewById(R.id.progressLogin);
        bodyCard        = findViewById(R.id.bodyCard);
        verificationOverlay = findViewById(R.id.verificationOverlay);
    }

    // ─── Google Sign-In Configuration ───────────────────────────────────────

    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(SupabaseAuthManager.GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
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
                .setDuration(480)
                .setStartDelay(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    // ─── Styled nav links ────────────────────────────────────────────────────

    private void styleNavigationLinks() {
        if (tvGoToRegister != null) {
            SpannableString ss = new SpannableString("Don't have an account? Sign Up");
            int start = ss.toString().indexOf("Sign Up");
            ss.setSpan(new ForegroundColorSpan(
                    ContextCompat.getColor(this, R.color.accent)), start, ss.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, ss.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvGoToRegister.setText(ss);
        }
    }

    // ─── Listeners ───────────────────────────────────────────────────────────

    private void attachListeners() {
        btnLogin.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.startAnimation(scaleDown);
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.startAnimation(scaleUp);
            }
            return false;
        });
        btnLogin.setOnClickListener(v -> handleEmailLogin());

        if (btnGoogleSignIn != null) {
            btnGoogleSignIn.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    v.startAnimation(scaleDown);
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                        || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.startAnimation(scaleUp);
                }
                return false;
            });
            btnGoogleSignIn.setOnClickListener(v -> launchGoogleSignIn());
        }

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v ->
                    startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
        }

        if (tvGoToRegister != null) {
            tvGoToRegister.setOnClickListener(v ->
                    startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        }
    }

    // ─── Email Login ─────────────────────────────────────────────────────────

    private void handleEmailLogin() {
        clearErrors();
        String email    = getText(etEmail);
        String password = getText(etPassword);

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            return;
        }

        setLoading(true);

        authManager.login(this, email, password, new SupabaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                setLoading(false);
                goToMain();
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                if (errorMessage != null && (errorMessage.contains("Email not confirmed")
                        || errorMessage.contains("email_not_confirmed")
                        || errorMessage.contains("Email not verified"))) {
                    new androidx.appcompat.app.AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Email Verification Required ✉️")
                            .setMessage("Please check your inbox and verify your email address before logging in.")
                            .setPositiveButton("Check Mail", (dialog, which) -> {
                                Intent intent = new Intent(Intent.ACTION_MAIN);
                                intent.addCategory(Intent.CATEGORY_APP_EMAIL);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                try {
                                    startActivity(intent);
                                } catch (android.content.ActivityNotFoundException e) {
                                    android.widget.Toast.makeText(LoginActivity.this, "No email app found.", android.widget.Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .create()
                            .show();
                } else if (errorMessage != null && (errorMessage.contains("Invalid login credentials")
                        || errorMessage.contains("invalid_grant")
                        || errorMessage.contains("invalid credentials")
                        || errorMessage.contains("Error 400")
                        || errorMessage.contains("Error 401"))) {
                    new androidx.appcompat.app.AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Authentication Failed")
                            .setMessage("Incorrect email or password. Please try again.")
                            .setPositiveButton("OK", null)
                            .create()
                            .show();
                } else {
                    showError(errorMessage);
                }
            }
        });
    }

    // ─── Google OAuth ────────────────────────────────────────────────────────

    private void launchGoogleSignIn() {
        // Sign out of the local client first so the account chooser is always shown
        // (prevents auto-reselect of the previously logged-in account)
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null && account.getIdToken() != null) {
                    setLoading(true);
                    authManager.googleSignIn(this, account.getIdToken(),
                            new SupabaseAuthManager.AuthCallback() {
                                @Override
                                public void onSuccess(String message) {
                                    setLoading(false);
                                    goToMain();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    setLoading(false);
                                    showError(errorMessage);
                                }
                            });
                } else {
                    showError("Google sign-in failed: no ID token received.");
                }
            } catch (ApiException e) {
                showError("Google sign-in error: " + e.getStatusCode());
            }
        }
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(!loading);
        if (progressLogin != null) {
            progressLogin.setVisibility(loading
                    ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tilPassword.setError(null);
    }

    private void showError(String msg) {
        android.view.View root = findViewById(android.R.id.content);
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
    }

    private String getText(TextInputEditText field) {
        return field != null && field.getText() != null
                ? field.getText().toString().trim() : "";
    }

    private void goToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
