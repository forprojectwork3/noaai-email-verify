package com.example.ailecturesummarizer;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
 * RegisterActivity — creates a new Supabase account via email/password or Google OAuth.
 *
 * On successful sign-up: a verification email is sent — user is shown a toast/snackbar
 * and routed back to LoginActivity to sign in after confirming their email.
 */
public class RegisterActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    // Views
    private TextInputLayout    tilRegName, tilRegEmail, tilRegPassword, tilRegConfirmPassword;
    private TextInputEditText  etRegName, etRegEmail, etRegPassword, etRegConfirmPassword;
    private MaterialButton     btnRegister;
    private LinearLayout       btnGoogleSignIn;
    private TextView           tvGoToLogin;
    private ProgressBar        progressRegister;
    private LinearLayout       bodyCard;

    // Auth
    private SupabaseAuthManager authManager;
    private GoogleSignInClient  googleSignInClient;

    // Animations
    private Animation scaleDown, scaleUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authManager = SupabaseAuthManager.getInstance();
        bindViews();
        configureGoogleSignIn();
        loadAnimations();
        styleNavigationLinks();
        attachListeners();
        runEntranceAnimation();
    }

    // ─── View Binding ────────────────────────────────────────────────────────

    private void bindViews() {
        tilRegName            = findViewById(R.id.tilRegName);
        tilRegEmail           = findViewById(R.id.tilRegEmail);
        tilRegPassword        = findViewById(R.id.tilRegPassword);
        tilRegConfirmPassword = findViewById(R.id.tilRegConfirmPassword);
        etRegName             = findViewById(R.id.etRegName);
        etRegEmail            = findViewById(R.id.etRegEmail);
        etRegPassword         = findViewById(R.id.etRegPassword);
        etRegConfirmPassword  = findViewById(R.id.etRegConfirmPassword);
        btnRegister           = findViewById(R.id.btnRegister);
        btnGoogleSignIn       = findViewById(R.id.btnGoogleSignIn);
        tvGoToLogin           = findViewById(R.id.tvGoToLogin);
        progressRegister      = findViewById(R.id.progressRegister);
        bodyCard              = findViewById(R.id.bodyCard);
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
        if (tvGoToLogin != null) {
            SpannableString ss = new SpannableString("Already have an account? Login");
            int start = ss.toString().indexOf("Login");
            ss.setSpan(new ForegroundColorSpan(
                    ContextCompat.getColor(this, R.color.accent)), start, ss.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, ss.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvGoToLogin.setText(ss);
        }
    }

    // ─── Listeners ───────────────────────────────────────────────────────────

    private void attachListeners() {
        btnRegister.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.startAnimation(scaleDown);
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.startAnimation(scaleUp);
            }
            return false;
        });
        btnRegister.setOnClickListener(v -> handleSignUp());

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

        if (tvGoToLogin != null) {
            tvGoToLogin.setOnClickListener(v -> {
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
            });
        }
    }

    // ─── Email Sign-Up ───────────────────────────────────────────────────────

    private void handleSignUp() {
        clearErrors();

        String name     = getText(etRegName);
        String email    = getText(etRegEmail);
        String password = getText(etRegPassword);
        String confirm  = getText(etRegConfirmPassword);

        if (TextUtils.isEmpty(name)) {
            tilRegName.setError("Full name is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            tilRegEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            tilRegPassword.setError("Password is required");
            return;
        }
        if (password.length() < 6) {
            tilRegPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirm)) {
            tilRegConfirmPassword.setError("Passwords do not match");
            return;
        }

        setLoading(true);

        authManager.signUp(this, name, email, password, new SupabaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                setLoading(false);
                // Verification dialog is shown directly by SupabaseAuthManager.java
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                showMessage(errorMessage);
            }
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void showVerificationDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Verify Your Email 🚀")
                .setMessage("Verification email sent! Please check your inbox and confirm your email before logging in.")
                .setPositiveButton("Open Mail App", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_APP_EMAIL);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (android.content.ActivityNotFoundException e) {
                        android.widget.Toast.makeText(this, "No email app found.", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    navigateToLogin();
                })
                .setNegativeButton("Back to Login", (dialog, which) -> navigateToLogin())
                .setOnCancelListener(dialog -> navigateToLogin())
                .show();
    }

    private void navigateToLogin() {
        startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
        finish();
    }

    // ─── Google OAuth ────────────────────────────────────────────────────────

    private void launchGoogleSignIn() {
        startActivityForResult(googleSignInClient.getSignInIntent(), RC_GOOGLE_SIGN_IN);
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
                                    // Google sign-in creates + logs in the user in one step
                                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                    finish();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    setLoading(false);
                                    showMessage(errorMessage);
                                }
                            });
                } else {
                    showMessage("Google sign-in failed: no ID token received.");
                }
            } catch (ApiException e) {
                showMessage("Google sign-in error: " + e.getStatusCode());
            }
        }
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        btnRegister.setEnabled(!loading);
        if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(!loading);
        if (progressRegister != null) {
            progressRegister.setVisibility(loading
                    ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void clearErrors() {
        tilRegName.setError(null);
        tilRegEmail.setError(null);
        tilRegPassword.setError(null);
        tilRegConfirmPassword.setError(null);
    }

    private void showMessage(String msg) {
        android.view.View root = findViewById(android.R.id.content);
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
    }

    private String getText(TextInputEditText field) {
        return field != null && field.getText() != null
                ? field.getText().toString().trim() : "";
    }
}
