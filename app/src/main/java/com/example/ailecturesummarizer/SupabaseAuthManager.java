package com.example.ailecturesummarizer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * SupabaseAuthManager — Thread-safe singleton for all Supabase auth operations.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * CONFIGURATION ← FILL IN YOUR OWN VALUES BEFORE USE
 * ─────────────────────────────────────────────────────────────────────────────
 * SUPABASE_URL : Your project URL from Supabase dashboard
 * e.g. "https://xyzcompany.supabase.co"
 * SUPABASE_ANON_KEY : Your project anon/public API key
 * GOOGLE_WEB_CLIENT_ID: OAuth 2.0 Web Client ID from Google Cloud Console
 * (must match the one used in your Supabase Google provider)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * All network calls run on OkHttp's background thread pool.
 * All callbacks are posted back to the Android Main Thread safely.
 */
public class SupabaseAuthManager {

    // ─── FILL IN YOUR OWN CREDENTIALS ──────────────────────────────────────
    public static final String SUPABASE_URL = "https://lumbpfcruwjqudtdbkrm.supabase.co";
    public static final String SUPABASE_ANON_KEY = "sb_publishable_mUkwvs-7BXlOQmPTFBcXVQ_VEqmbQz_";
    public static final String GOOGLE_WEB_CLIENT_ID = "857501676277-11d9j2nta56akq53l2tib35g67o3llq3.apps.googleusercontent.com";
    public static final String RESEND_API_KEY = "re_7vKNTChZ_D4J2W6xCMFsVWT81VFKqSqbN";
    public static final String NETLIFY_URL = "https://noaai.dpdns.org"; // Live domain — Cloudflare (noaai.dpdns.org)
    // ────────────────────────────────────────────────────────────────────────

    private static final String PREFS_NAME = "noa_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_LOGGED_IN = "logged_in";

    public static final String MSG_SIGNUP_INSTANT = "SIGNUP_INSTANT_LOGIN";
    public static final String MSG_SIGNUP_VERIFY  = "SIGNUP_VERIFICATION_PENDING";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // ─── Singleton ──────────────────────────────────────────────────────────

    private static volatile SupabaseAuthManager sInstance;

    private final OkHttpClient httpClient;
    private final Handler mainHandler;

    private SupabaseAuthManager() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .connectionSpecs(java.util.Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    Log.d("SupabaseNetworkTrace", "--> SENDING REQUEST: " + request.url());
                    Log.d("SupabaseNetworkTrace", "HEADERS: " + request.headers());
                    return chain.proceed(request);
                })
                .build();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Returns the singleton instance. Thread-safe via double-checked locking.
     */
    public static SupabaseAuthManager getInstance() {
        if (sInstance == null) {
            synchronized (SupabaseAuthManager.class) {
                if (sInstance == null) {
                    sInstance = new SupabaseAuthManager();
                }
            }
        }
        return sInstance;
    }

    // ─── Callback Interfaces ────────────────────────────────────────────────

    public interface AuthCallback {
        /** Called on the Main Thread when the operation succeeds. */
        void onSuccess(String message);

        /** Called on the Main Thread when the operation fails. */
        void onError(String errorMessage);
    }

    public interface ProfileCallback {
        /** Called on the Main Thread with the user's display name and avatar URL. */
        void onSuccess(String displayName, String avatarUrl);

        void onError(String errorMessage);
    }

    public interface ChatsCallback {
        void onSuccess(java.util.ArrayList<SupabaseChat> chats);

        void onError(String errorMessage);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. EMAIL SIGN-UP
    // POST /auth/v1/signup
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Registers a new user with Supabase. On success, handles either instant login
     * or verification pending state based on Supabase configuration.
     *
     * @param context  Application or Activity context for saving sessions
     * @param fullName The display name, stored in user_metadata.full_name
     * @param email    User's email address
     * @param password User's chosen password (min 6 chars recommended)
     * @param callback Delivers result to the calling Activity on the Main Thread
     */
    public void signUp(final Context context,
            final String fullName,
            final String email,
            final String password,
            final AuthCallback callback) {

        // Build JSON body
        JsonObject metadata = new JsonObject();
        metadata.addProperty("full_name", fullName);

        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("redirectTo", NETLIFY_URL);
        body.add("data", metadata); // user_metadata lives under "data" key

        String path = "/auth/v1/signup?redirect_to=" + android.net.Uri.encode(NETLIFY_URL);
        Request request = buildPostRequest(path, body.toString());

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliverError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    // Trigger Welcome Email via Resend
                    ResendEmailManager.getInstance().sendWelcomeEmail(email, fullName, null);

                    // Block automated home dashboard traversal by not signing in instantly/not delivering MSG_SIGNUP_INSTANT.
                    // Instead, show our custom 'Verify Your Email' dialog alert view immediately.
                    mainHandler.post(() -> {
                        try {
                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                                    .setTitle("Verify Your Email 🚀")
                                    .setMessage("Verification email sent! Please check your inbox and confirm your email before logging in.")
                                    .setPositiveButton("Open Mail App", (dialog, which) -> {
                                        Intent intent = new Intent(Intent.ACTION_MAIN);
                                        intent.addCategory(Intent.CATEGORY_APP_EMAIL);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        try {
                                            context.startActivity(intent);
                                        } catch (android.content.ActivityNotFoundException e) {
                                            android.widget.Toast.makeText(context, "No email app found.", android.widget.Toast.LENGTH_SHORT).show();
                                        }
                                        if (context instanceof android.app.Activity) {
                                            android.app.Activity activity = (android.app.Activity) context;
                                            activity.startActivity(new Intent(activity, LoginActivity.class));
                                            activity.finish();
                                        }
                                    })
                                    .setNegativeButton("Back to Login", (dialog, which) -> {
                                        if (context instanceof android.app.Activity) {
                                            android.app.Activity activity = (android.app.Activity) context;
                                            activity.startActivity(new Intent(activity, LoginActivity.class));
                                            activity.finish();
                                        }
                                    })
                                    .setOnCancelListener(dialog -> {
                                        if (context instanceof android.app.Activity) {
                                            android.app.Activity activity = (android.app.Activity) context;
                                            activity.startActivity(new Intent(activity, LoginActivity.class));
                                            activity.finish();
                                        }
                                    })
                                    .show();
                        } catch (Exception e) {
                            Log.e("SupabaseAuthManager", "Failed to show verification dialog", e);
                        }
                    });

                    deliverSuccess(callback, MSG_SIGNUP_VERIFY);
                } else {
                    deliverError(callback, extractErrorMessage(bodyStr, response.code()));
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. EMAIL LOGIN
    // POST /auth/v1/token?grant_type=password
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Authenticates an existing user via email + password.
     * On success, persists the JWT access_token and refresh_token in
     * SharedPreferences.
     *
     * @param context  Application or Activity context for SharedPreferences
     * @param email    User's email
     * @param password User's password
     * @param callback Delivers result to the calling Activity on the Main Thread
     */
    public void login(final Context context,
            final String email,
            final String password,
            final AuthCallback callback) {

        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        Request request = buildPostRequest("/auth/v1/token?grant_type=password", body.toString());

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliverError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(bodyStr).getAsJsonObject();
                        String accessToken = json.get("access_token").getAsString();
                        String refreshToken = json.has("refresh_token")
                                ? json.get("refresh_token").getAsString()
                                : "";

                        // Extract user info if available
                        String userEmail = email;
                        String userName = "";
                        String userId = "";
                        if (json.has("user") && !json.get("user").isJsonNull()) {
                            JsonObject user = json.getAsJsonObject("user");
                            if (user.has("id"))
                                userId = user.get("id").getAsString();
                            if (user.has("user_metadata") && !user.get("user_metadata").isJsonNull()) {
                                JsonObject meta = user.getAsJsonObject("user_metadata");
                                if (meta.has("full_name")) {
                                    userName = meta.get("full_name").getAsString();
                                }
                            }
                        }

                        saveSession(context, accessToken, refreshToken, userEmail, userName);
                        if (!userId.isEmpty())
                            saveUserId(context, userId);
                        deliverSuccess(callback, "Login successful");

                    } catch (Exception e) {
                        deliverError(callback, "Failed to parse server response.");
                    }
                } else {
                    int code = response.code();
                    if ((code == 400 || code == 401) && !bodyStr.contains("email_not_confirmed") && !bodyStr.contains("Email not confirmed")) {
                        deliverError(callback, "Incorrect email or password. Please try again.");
                    } else {
                        deliverError(callback, extractErrorMessage(bodyStr, code));
                    }
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. GOOGLE OAUTH — exchange Android ID Token for Supabase session
    // POST /auth/v1/token?grant_type=id_token
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Exchanges a Google ID Token (obtained via GoogleSignIn on Android) for a
     * Supabase session (JWT access_token). Persists tokens on success.
     *
     * @param context       Application or Activity context for SharedPreferences
     * @param googleIdToken The ID token string from Google Sign-In result
     * @param callback      Delivers result to the calling Activity on the Main
     *                      Thread
     */
    public void googleSignIn(final Context context,
            final String googleIdToken,
            final AuthCallback callback) {

        JsonObject body = new JsonObject();
        body.addProperty("provider", "google");
        body.addProperty("id_token", googleIdToken);

        Request request = buildPostRequest("/auth/v1/token?grant_type=id_token", body.toString());

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliverError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(bodyStr).getAsJsonObject();
                        String accessToken = json.get("access_token").getAsString();
                        String refreshToken = json.has("refresh_token")
                                ? json.get("refresh_token").getAsString()
                                : "";
                        String userEmail = "";
                        String userName = "";
                        String userId = "";

                        if (json.has("user") && !json.get("user").isJsonNull()) {
                            JsonObject user = json.getAsJsonObject("user");
                            if (user.has("id"))
                                userId = user.get("id").getAsString();
                            if (user.has("email"))
                                userEmail = user.get("email").getAsString();
                            if (user.has("user_metadata") && !user.get("user_metadata").isJsonNull()) {
                                JsonObject meta = user.getAsJsonObject("user_metadata");
                                if (meta.has("full_name"))
                                    userName = meta.get("full_name").getAsString();
                                else if (meta.has("name"))
                                    userName = meta.get("name").getAsString();
                            }
                        }

                        saveSession(context, accessToken, refreshToken, userEmail, userName);
                        if (!userId.isEmpty())
                            saveUserId(context, userId);
                        deliverSuccess(callback, "Google sign-in successful");

                    } catch (Exception e) {
                        deliverError(callback, "Failed to parse server response.");
                    }
                } else {
                    deliverError(callback, extractErrorMessage(bodyStr, response.code()));
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. PASSWORD RESET REQUEST (sends magic link email)
    // POST /auth/v1/recover
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Requests a password-reset link email from Supabase.
     * The email contains a link that will open the password reset page
     * at https://noaai.dpdns.org/reset/.
     *
     * @param email    The account email to send the reset link to
     * @param callback Delivers result to the calling Activity on the Main Thread
     */
    public void resetPasswordForEmail(final String email, final AuthCallback callback) {

        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("redirectTo", "https://noaai.dpdns.org/reset/");

        Request request = buildPostRequest("/auth/v1/recover", body.toString());

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliverError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResendEmailManager.getInstance().sendPasswordResetEmail(email, "https://noaai.dpdns.org/reset/", new ResendEmailManager.EmailCallback() {
                        @Override
                        public void onSuccess(String emailId) {
                            deliverSuccess(callback, "A password reset link has been sent to your email!");
                        }

                        @Override
                        public void onError(String errorMessage) {
                            deliverError(callback, errorMessage);
                        }
                    });
                } else {
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    deliverError(callback, extractErrorMessage(bodyStr, response.code()));
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. UPDATE PASSWORD (authenticated — uses JWT from deep link fragment)
    // PUT /auth/v1/user
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Updates the authenticated user's password. The {@code accessToken} must be
     * extracted from the deep link URL fragment (e.g., {@code #access_token=...})
     * when ResetPasswordActivity receives the {@code myapp://reset-password} deep
     * link.
     *
     * @param accessToken JWT from the deep link URL fragment
     * @param newPassword The user's chosen new password
     * @param callback    Delivers result on the Main Thread
     */
    public void updateUserPassword(final String accessToken,
            final String newPassword,
            final AuthCallback callback) {

        JsonObject body = new JsonObject();
        body.addProperty("password", newPassword);

        String url = SUPABASE_URL + "/auth/v1/user";

        Request request = new Request.Builder()
                .url(url)
                .put(RequestBody.create(body.toString(), JSON))
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliverError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    deliverSuccess(callback, "Password updated successfully! Please log in.");
                } else {
                    deliverError(callback, extractErrorMessage(bodyStr, response.code()));
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. LOG OUT USER (authenticated — uses saved access token)
    // POST /auth/v1/logout
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Logs out the user from Supabase asynchronously.
     * Deletes and clears the session token from SharedPreferences on success or
     * failure.
     * Also explicitly signs out from Google to clear account cache.
     *
     * @param context  Application or Activity context to clear SharedPreferences
     *                 session
     * @param callback Callback notified on the Main Thread
     */
    public void logOutUser(final Context context, final AuthCallback callback) {
        // 1. Sign out AND revoke Google access token — this forces the account chooser
        // to reappear on the next "Continue with Google" tap.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build();
        com.google.android.gms.auth.api.signin.GoogleSignInClient gClient = GoogleSignIn.getClient(context, gso);

        // Chain: signOut → revokeAccess, then clear Supabase session
        gClient.signOut().addOnCompleteListener(signOutTask -> {
            gClient.revokeAccess().addOnCompleteListener(revokeTask -> {
                Log.i("SupabaseAuthManager",
                        "Google sign-out + revoke complete (success=" + revokeTask.isSuccessful() + ")");
                clearSupabaseSession(context, callback);
            });
        });
    }

    /**
     * Internal: clears local session then calls the Supabase /auth/v1/logout
     * endpoint.
     */
    private void clearSupabaseSession(final Context context, final AuthCallback callback) {
        String token = getAccessToken(context);
        if (token == null || token.isEmpty()) {
            clearSession(context);
            deliverSuccess(callback, "Logged out successfully");
            return;
        }

        String url = SUPABASE_URL + "/auth/v1/logout";

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("{}", JSON))
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + token)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Clear local session regardless to prevent lockout
                clearSession(context);
                deliverSuccess(callback, "Logged out locally due to connection error");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // Always clear local session
                clearSession(context);
                if (response.isSuccessful() || response.code() == 401
                        || response.code() == 403 || response.code() == 400) {
                    deliverSuccess(callback, "Logged out successfully");
                } else {
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    deliverError(callback, extractErrorMessage(bodyStr, response.code()));
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. FETCH USER PROFILE (authenticated — uses saved access token)
    // GET /auth/v1/user
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Fetches the current user's profile from Supabase Auth.
     * Extracts full_name and avatar_url from user_metadata.
     *
     * @param context  Context for SharedPreferences token retrieval
     * @param callback Delivers (displayName, avatarUrl) on the Main Thread
     */
    public void fetchUserProfile(final Context context, final ProfileCallback callback) {
        String token = getAccessToken(context);
        if (token == null || token.isEmpty()) {
            mainHandler.post(() -> {
                if (callback != null)
                    callback.onError("Not logged in");
            });
            return;
        }

        String url = SUPABASE_URL + "/auth/v1/user";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliverProfileError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(bodyStr).getAsJsonObject();
                        String name = "";
                        String avatarUrl = "";
                        if (json.has("user_metadata") && !json.get("user_metadata").isJsonNull()) {
                            JsonObject meta = json.getAsJsonObject("user_metadata");
                            if (meta.has("full_name"))
                                name = meta.get("full_name").getAsString();
                            else if (meta.has("name"))
                                name = meta.get("name").getAsString();
                            if (meta.has("avatar_url"))
                                avatarUrl = meta.get("avatar_url").getAsString();
                        }
                        final String finalName = name;
                        final String finalAvatar = avatarUrl;
                        mainHandler.post(() -> {
                            if (callback != null)
                                callback.onSuccess(finalName, finalAvatar);
                        });
                    } catch (Exception e) {
                        deliverProfileError(callback, "Failed to parse profile.");
                    }
                } else {
                    deliverProfileError(callback, extractErrorMessage(bodyStr, response.code()));
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 8. UPDATE USER PROFILE (authenticated — PUT /auth/v1/user)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Updates the current user's display name and/or avatar URL in user_metadata.
     *
     * @param context     Context for SharedPreferences token retrieval
     * @param displayName New display name (pass null to leave unchanged)
     * @param avatarUrl   New avatar URL (pass null to leave unchanged)
     * @param callback    Delivers result on the Main Thread
     */
    public void updateUserProfile(final Context context,
            final String displayName,
            final String avatarUrl,
            final AuthCallback callback) {
        String token = getAccessToken(context);
        if (token == null || token.isEmpty()) {
            deliverError(callback, "Not logged in");
            return;
        }

        JsonObject metadata = new JsonObject();
        if (displayName != null)
            metadata.addProperty("full_name", displayName);
        if (avatarUrl != null)
            metadata.addProperty("avatar_url", avatarUrl);

        JsonObject body = new JsonObject();
        body.add("data", metadata);

        String url = SUPABASE_URL + "/auth/v1/user";
        Request request = new Request.Builder()
                .url(url)
                .put(RequestBody.create(body.toString(), JSON))
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + token)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliverError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    // Persist new values locally for fast UI access
                    if (displayName != null) {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit().putString(KEY_USER_NAME, displayName).apply();
                    }
                    if (avatarUrl != null) {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit().putString(KEY_AVATAR_URL, avatarUrl).apply();
                    }
                    deliverSuccess(callback, "Profile updated successfully");
                } else {
                    deliverError(callback, extractErrorMessage(bodyStr, response.code()));
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 9. FETCH CHATS (authenticated — GET from chats table via Supabase REST)
    // GET /rest/v1/chats?user_id=eq.<userId>&order=created_at.desc
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Fetches the current user's chat list from the Supabase `chats` table.
     * Requires the user_id to be stored in SharedPreferences (set during login).
     *
     * @param context  Context for SharedPreferences token + user_id retrieval
     * @param callback Delivers a JsonArray of chat objects on the Main Thread
     */
    public void fetchChats(final Context context, final ChatsCallback callback) {
        executeWithFreshToken(context, new TokenReceiver() {
            @Override
            public void onTokenReady(String freshToken) {
                if (freshToken == null || freshToken.isEmpty()) {
                    mainHandler.post(() -> {
                        if (callback != null)
                            callback.onError("Not logged in");
                    });
                    return;
                }

                String userId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_USER_ID, "");
                String url = SUPABASE_URL + "/rest/v1/chats?select=*&order=created_at.desc";
                if (!userId.isEmpty()) {
                    url = SUPABASE_URL + "/rest/v1/chats?select=*&user_id=eq." + userId + "&order=created_at.desc";
                }

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("apikey", SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + freshToken)
                        .addHeader("Accept", "application/json")
                        .build();

                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e("SupabaseAuthManager", "fetchChats network failure: " + e.getMessage());
                        mainHandler.post(() -> {
                            if (callback != null)
                                callback.onError("Network error: " + e.getMessage());
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String bodyStr = response.body() != null ? response.body().string() : "[]";
                        if (response.isSuccessful()) {
                            try {
                                com.google.gson.reflect.TypeToken<java.util.ArrayList<SupabaseChat>> typeToken = new com.google.gson.reflect.TypeToken<java.util.ArrayList<SupabaseChat>>() {
                                };
                                java.util.ArrayList<SupabaseChat> list = new com.google.gson.Gson().fromJson(bodyStr,
                                        typeToken.getType());
                                mainHandler.post(() -> {
                                    if (callback != null)
                                        callback.onSuccess(list);
                                });
                            } catch (Exception e) {
                                Log.e("SupabaseAuthManager", "fetchChats parse error: " + e.getMessage());
                                mainHandler.post(() -> {
                                    if (callback != null)
                                        callback.onError("Failed to parse chats.");
                                });
                            }
                        } else {
                            Log.e("SupabaseAuthManager",
                                    "fetchChats error HTTP status " + response.code() + ": " + bodyStr);
                            String err = extractErrorMessage(bodyStr, response.code());
                            mainHandler.post(() -> {
                                if (callback != null)
                                    callback.onError(err);
                            });
                        }
                    }
                });
            }
        });
    }

    /**
     * Deletes a chat session from the Supabase `chats` table by its UUID.
     *
     * @param context Context for SharedPreferences token retrieval
     * @param chatId  The specific chat ID (UUID) to delete
     * @param callback Delivers success/error callback on the Main Thread
     */
    public void deleteChat(final Context context, final String chatId, final AuthCallback callback) {
        executeWithFreshToken(context, new TokenReceiver() {
            @Override
            public void onTokenReady(String freshToken) {
                if (freshToken == null || freshToken.isEmpty()) {
                    deliverError(callback, "Not logged in");
                    return;
                }

                String url = SUPABASE_URL + "/rest/v1/chats?id=eq." + chatId;

                Request request = new Request.Builder()
                        .url(url)
                        .delete()
                        .addHeader("apikey", SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + freshToken)
                        .build();

                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e("SupabaseAuthManager", "deleteChat network failure: " + e.getMessage());
                        deliverError(callback, "Network error: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String bodyStr = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            deliverSuccess(callback, "Chat deleted successfully");
                        } else {
                            Log.e("SupabaseAuthManager",
                                    "deleteChat error HTTP status " + response.code() + ": " + bodyStr);
                            deliverError(callback, extractErrorMessage(bodyStr, response.code()));
                        }
                    }
                });
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 10. CREATE CHAT (authenticated — POST to chats table)
    // POST /rest/v1/chats
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a new chat entry in the `chats` table for the current user.
     *
     * @param context  Context for SharedPreferences token + user_id retrieval
     * @param title    Display title for this chat session
     * @param callback Delivers result on the Main Thread
     */
    public void createChat(final Context context,
            final String title,
            final AuthCallback callback) {
        executeWithFreshToken(context, new TokenReceiver() {
            @Override
            public void onTokenReady(String freshToken) {
                String userId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_USER_ID, null);

                if (freshToken == null || freshToken.isEmpty() || userId == null || userId.isEmpty()) {
                    deliverError(callback, "Not logged in");
                    return;
                }

                JsonObject body = new JsonObject();
                body.addProperty("user_id", userId);
                body.addProperty("title", title);

                String url = SUPABASE_URL + "/rest/v1/chats";
                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(body.toString(), JSON))
                        .addHeader("apikey", SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + freshToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .build();

                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e("SupabaseAuthManager", "createChat network failure: " + e.getMessage());
                        deliverError(callback, "Network error: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String bodyStr = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            try {
                                com.google.gson.JsonArray arr = JsonParser.parseString(bodyStr).getAsJsonArray();
                                if (arr.size() > 0) {
                                    JsonObject chatObj = arr.get(0).getAsJsonObject();
                                    String chatId = chatObj.has("id") ? chatObj.get("id").getAsString() : "";
                                    deliverSuccess(callback, chatId);
                                } else {
                                    deliverSuccess(callback, "Chat created");
                                }
                            } catch (Exception e) {
                                deliverSuccess(callback, "Chat created");
                            }
                        } else {
                            Log.e("SupabaseAuthManager",
                                    "createChat error HTTP status " + response.code() + ": " + bodyStr);
                            deliverError(callback, extractErrorMessage(bodyStr, response.code()));
                        }
                    }
                });
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 11. UPLOAD AVATAR TO STORAGE (authenticated — PUT
    // /storage/v1/object/avatars/[user_id].jpg)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Uploads user avatar image file to Supabase avatars storage bucket via PUT.
     * On success, constructs the public URL with a timestamp cache buster and
     * delivers it.
     */
    public void uploadAvatar(final Context context,
            final String filePath,
            final AuthCallback callback) {
        String token = getAccessToken(context);
        String userId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_USER_ID, null);

        if (token == null || token.isEmpty() || userId == null || userId.isEmpty()) {
            deliverError(callback, "Not logged in");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            deliverError(callback, "Local avatar file not found");
            return;
        }

        byte[] bytes;
        try {
            FileInputStream fis = new FileInputStream(file);
            bytes = new byte[(int) file.length()];
            int read = fis.read(bytes);
            fis.close();
        } catch (Exception e) {
            Log.e("SupabaseAuthManager", "Failed to read local avatar file: " + e.getMessage());
            deliverError(callback, "Failed to read file: " + e.getMessage());
            return;
        }

        String url = SUPABASE_URL + "/storage/v1/object/avatars/" + userId + ".jpg";

        RequestBody requestBody = RequestBody.create(bytes, MediaType.parse("image/jpeg"));

        Request request = new Request.Builder()
                .url(url)
                .put(requestBody)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "image/jpeg")
                .addHeader("x-upsert", "true")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("SupabaseAuthManager", "uploadAvatar network failure: " + e.getMessage());
                deliverError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    String publicUrl = SUPABASE_URL + "/storage/v1/object/public/avatars/" + userId + ".jpg?t="
                            + System.currentTimeMillis();
                    deliverSuccess(callback, publicUrl);
                } else {
                    Log.e("SupabaseAuthManager", "uploadAvatar error HTTP status " + response.code() + ": " + bodyStr);
                    deliverError(callback, "Upload failed with HTTP status code " + response.code());
                }
            }
        });
    }

    /**
     * Uploads user avatar image byte array directly to Supabase storage bucket via
     * PUT.
     * Path is defined dynamically (e.g. "userId/profile.webp").
     */
    public void uploadAvatarBytes(final Context context,
            final byte[] bytes,
            final String filename,
            final AuthCallback callback) {
        String token = getAccessToken(context);
        if (token == null || token.isEmpty()) {
            deliverError(callback, "Not logged in");
            return;
        }

        String url = SUPABASE_URL + "/storage/v1/object/avatars/" + filename;

        RequestBody requestBody = RequestBody.create(bytes, MediaType.parse("image/webp"));

        Request request = new Request.Builder()
                .url(url)
                .put(requestBody)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "image/webp")
                .addHeader("x-upsert", "true")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("SupabaseAuthManager", "uploadAvatarBytes network failure: " + e.getMessage());
                deliverError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    String publicUrl = SUPABASE_URL + "/storage/v1/object/public/avatars/" + filename + "?t="
                            + System.currentTimeMillis();
                    deliverSuccess(callback, publicUrl);
                } else {
                    Log.e("SupabaseAuthManager",
                            "uploadAvatarBytes error HTTP status " + response.code() + ": " + bodyStr);
                    deliverError(callback, "Upload failed with HTTP status code " + response.code());
                }
            }
        });
    }

    public static class SupabaseChat {
        public String id;
        public String title;
        public String user_id;
        public String created_at;
    }

    // ─── Token Expiration Interceptor and Silent Refresh Helpers ────────────

    private boolean isTokenNearExpiration(String token) {
        if (token == null || token.isEmpty())
            return true;
        String[] parts = token.split("\\.");
        if (parts.length < 2)
            return true;
        try {
            byte[] bytes = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE);
            String jsonStr = new String(bytes, "UTF-8");
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
            if (json.has("exp")) {
                long expSec = json.get("exp").getAsLong();
                long currentSec = System.currentTimeMillis() / 1000;
                // Refresh if token expires in less than 5 minutes (300 seconds)
                return (expSec - currentSec) < 300;
            }
        } catch (Exception e) {
            Log.e("SupabaseAuthManager", "Failed to decode JWT: " + e.getMessage());
        }
        return true;
    }

    public void refreshSessionToken(final Context context, final AuthCallback callback) {
        String refreshToken = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_REFRESH_TOKEN, null);
        if (refreshToken == null || refreshToken.isEmpty()) {
            if (callback != null)
                callback.onError("No refresh token stored");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refreshToken);

        Request request = buildPostRequest("/auth/v1/token?grant_type=refresh_token", body.toString());

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("SupabaseAuthManager", "Silent refresh network failure: " + e.getMessage());
                if (callback != null)
                    callback.onError("Network error during refresh: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(bodyStr).getAsJsonObject();
                        String accessToken = json.get("access_token").getAsString();
                        String newRefreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString()
                                : refreshToken;

                        // Maintain cached name & email
                        String userEmail = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .getString(KEY_USER_EMAIL, "");
                        String userName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .getString(KEY_USER_NAME, "");

                        saveSession(context, accessToken, newRefreshToken, userEmail, userName);
                        Log.i("SupabaseAuthManager", "Session token refreshed successfully");
                        if (callback != null)
                            callback.onSuccess(accessToken);
                    } catch (Exception e) {
                        Log.e("SupabaseAuthManager", "Failed to parse refresh response: " + e.getMessage());
                        if (callback != null)
                            callback.onError("Parse error during refresh");
                    }
                } else {
                    Log.e("SupabaseAuthManager",
                            "Silent refresh error HTTP status " + response.code() + ": " + bodyStr);
                    if (callback != null)
                        callback.onError("Refresh failed: status " + response.code());
                }
            }
        });
    }

    public void executeWithFreshToken(final Context context, final TokenReceiver receiver) {
        String token = getAccessToken(context);
        if (isTokenNearExpiration(token)) {
            refreshSessionToken(context, new AuthCallback() {
                @Override
                public void onSuccess(String newToken) {
                    receiver.onTokenReady(newToken);
                }

                @Override
                public void onError(String errorMessage) {
                    // Fallback to current token if refresh fails
                    receiver.onTokenReady(token);
                }
            });
        } else {
            receiver.onTokenReady(token);
        }
    }

    public interface TokenReceiver {
        void onTokenReady(String token);
    }

    // ─── Session Helpers ────────────────────────────────────────────────────

    /**
     * Saves the Supabase session tokens and user info into SharedPreferences.
     */
    public void saveSession(Context context,
            String accessToken,
            String refreshToken,
            String email,
            String name) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, name)
                .apply();
    }

    /**
     * Saves the user's Supabase UUID so REST queries can filter by user_id.
     * Call this after a successful login/Google sign-in once the user object is
     * parsed.
     */
    public void saveUserId(Context context, String userId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_USER_ID, userId)
                .apply();
    }

    /** Returns the locally cached user name, or empty string. */
    public String getCachedUserName(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_USER_NAME, "");
    }

    /** Returns the locally cached user email, or empty string. */
    public String getCachedUserEmail(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_USER_EMAIL, "");
    }

    /**
     * Returns the locally cached avatar URL (may be remote URL or local file URI),
     * or empty.
     */
    public String getCachedAvatarUrl(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_AVATAR_URL, "");
    }

    /**
     * Clears all session data (logout).
     */
    public void clearSession(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    /**
     * Returns the saved JWT access token, or null if not logged in.
     */
    public String getAccessToken(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACCESS_TOKEN, null);
    }

    /**
     * Returns true if there is a persisted access token (user is logged in).
     */
    public boolean isLoggedIn(Context context) {
        String token = getAccessToken(context);
        return token != null && !token.isEmpty();
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    /**
     * Builds a POST request to a Supabase auth endpoint with all required headers.
     * Both "apikey" and "Authorization: Bearer" are required by Supabase Auth for
     * unauthenticated calls (sign-up, sign-in, token exchange, password recovery).
     */
    private Request buildPostRequest(String path, String jsonBody) {
        return new Request.Builder()
                .url(SUPABASE_URL + path)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Attempts to parse a Supabase error body JSON to extract the "msg" or
     * "message" field.
     * Falls back to a generic HTTP error string if parsing fails.
     */
    private String extractErrorMessage(String body, int code) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("error_description"))
                return json.get("error_description").getAsString();
            if (json.has("msg"))
                return json.get("msg").getAsString();
            if (json.has("message"))
                return json.get("message").getAsString();
            if (json.has("error"))
                return json.get("error").getAsString();
        } catch (Exception ignored) {
            /* fall through — body is likely HTML (e.g. gateway error) */ }
        // For 5xx gateway errors, expose the raw body so devs can see what's happening
        if (code >= 500) {
            String truncated = body != null && body.length() > 200 ? body.substring(0, 200) + "..." : body;
            Log.e("SupabaseAuthManager", "HTTP " + code + " raw body: " + body);
            return "Server error " + code + ". Check your Supabase URL and API key. Details: " + truncated;
        }
        return "Error " + code + ": Request failed. Please try again.";
    }

    /** Posts a success callback to the Android Main Thread. */
    private void deliverSuccess(final AuthCallback callback, final String message) {
        mainHandler.post(() -> {
            if (callback != null)
                callback.onSuccess(message);
        });
    }

    /** Posts an error callback to the Android Main Thread. */
    private void deliverError(final AuthCallback callback, final String error) {
        mainHandler.post(() -> {
            if (callback != null)
                callback.onError(error);
        });
    }

    /** Posts a ProfileCallback error to the Main Thread. */
    private void deliverProfileError(final ProfileCallback callback, final String error) {
        mainHandler.post(() -> {
            if (callback != null)
                callback.onError(error);
        });
    }
}
