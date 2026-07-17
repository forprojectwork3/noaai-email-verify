package com.example.ailecturesummarizer;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ResendEmailManager handles sending transactional emails using the Resend REST
 * API via cloud-hosted templates. All HTML layout is managed in the Resend
 * Template dashboard — this class only supplies dynamic variable bindings.
 */
public class ResendEmailManager {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";
    private static final String FROM_EMAIL = "system@noaai.dpdns.org";
    private static final String TEMPLATE_ID = "46e8f3f7-f012-4ffe-9813-738af58c93d9";
    private static final String CONFIRM_LINK = "https://noaai.dpdns.org/open";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static volatile ResendEmailManager sInstance;
    private final OkHttpClient httpClient;
    private final Handler mainHandler;

    private ResendEmailManager() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static ResendEmailManager getInstance() {
        if (sInstance == null) {
            synchronized (ResendEmailManager.class) {
                if (sInstance == null) {
                    sInstance = new ResendEmailManager();
                }
            }
        }
        return sInstance;
    }

    public interface EmailCallback {
        void onSuccess(String emailId);

        void onError(String errorMessage);
    }

    /**
     * Sends a verification email to a new user via the Resend cloud template.
     * The HTML layout is fully managed in the Resend Template builder.
     * Dynamic variable bindings are injected here at send-time.
     *
     * @param toEmail  Recipient email address
     * @param userName Recipient display name injected directly into the template
     *                 variables
     * @param callback Delivers result to the calling Activity on the Main Thread
     */
    public void sendWelcomeEmail(String toEmail, String userName, EmailCallback callback) {
        // Resend relies on the "variables" block to map custom text to your template
        // dashboard parameters.
        JsonObject variables = new JsonObject();
        variables.addProperty("confirm_link", CONFIRM_LINK);
        variables.addProperty("user_name", userName != null ? userName : "User"); // In case you want to use the
                                                                                  // username in your custom HTML
                                                                                  // template!

        JsonObject body = new JsonObject();
        body.addProperty("from", FROM_EMAIL);
        body.addProperty("to", toEmail);
        body.addProperty("subject", "Verify your NOA AI account 🚀");
        body.addProperty("template_id", TEMPLATE_ID);
        body.add("variables", variables); // Swapped "bindings" to "variables" for Resend's API

        dispatchEmail(body, callback);
    }

    /**
     * Sends a password reset email via the Resend API with precisely specified bindings.
     *
     * @param toEmail      Recipient email address
     * @param recoveryUrl  The generated recovery redirect URL
     * @param callback     Delivers result on the Main Thread
     */
    public void sendPasswordResetEmail(String toEmail, String recoveryUrl, EmailCallback callback) {
        JsonObject bindings = new JsonObject();
        bindings.addProperty("confirm_link", recoveryUrl);

        JsonObject body = new JsonObject();
        body.addProperty("from", FROM_EMAIL);
        body.addProperty("to", toEmail);
        body.addProperty("subject", "Reset your NOA AI password 🔐");
        body.addProperty("template_id", "email-verification-noa-ai");
        body.add("bindings", bindings);

        dispatchEmail(body, callback);
    }

    private void dispatchEmail(JsonObject body, EmailCallback callback) {
        Request request = new Request.Builder()
                .url(RESEND_API_URL)
                .post(RequestBody.create(body.toString(), JSON))
                .addHeader("Authorization", "Bearer " + SupabaseAuthManager.RESEND_API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliverError(callback, "Failed to send email: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    Log.d("ResendEmailManager", "Email sent successfully: " + bodyStr);
                    deliverSuccess(callback, bodyStr);
                } else {
                    Log.e("ResendEmailManager", "Error sending email: " + bodyStr);
                    deliverError(callback, "Resend Error " + response.code() + ": " + bodyStr);
                }
            }
        });
    }

    private void deliverSuccess(EmailCallback callback, String id) {
        mainHandler.post(() -> {
            if (callback != null)
                callback.onSuccess(id);
        });
    }

    private void deliverError(EmailCallback callback, String error) {
        mainHandler.post(() -> {
            if (callback != null)
                callback.onError(error);
        });
    }
}