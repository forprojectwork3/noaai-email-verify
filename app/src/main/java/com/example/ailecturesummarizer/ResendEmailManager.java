package com.example.ailecturesummarizer;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonArray;
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
    private static final String FROM_EMAIL = "NOA AI <team@noaai.dpdns.org>";
    private static final String REPLY_TO_EMAIL = "team@noaai.dpdns.org";
    private static final String TEMPLATE_ID = "46e8f3f7-f012-4ffe-9813-738af58c93d9";
    private static final String CONFIRM_LINK = "https://noaai.dpdns.org/open/";
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
     * Sends a verification email to a new user via the Resend API.
     * Includes both clean HTML layout and plain-text fallback for optimal inbox placement.
     *
     * @param toEmail Recipient email address
     * @param userName Recipient display name
     * @param callback Delivers result to the calling Activity on the Main Thread
     */
    public void sendWelcomeEmail(String toEmail, String userName, String confirmationUrl, EmailCallback callback) {
        String finalUrl = (confirmationUrl != null && !confirmationUrl.isEmpty()) ? confirmationUrl : CONFIRM_LINK;
        String title = "Verify your NOA AI account";
        String bodyText = "Thank you for signing up for NOA AI. To complete your registration and activate your smart video study companion profile, please click the verification button below.";
        String buttonText = "Confirm your email";
        String htmlContent = getPremiumHtmlTemplate(title, bodyText, buttonText, finalUrl);
        String plainText = "Verify your NOA AI account by clicking the link below:\n" + finalUrl;

        JsonArray toArray = new JsonArray();
        toArray.add(toEmail);

        JsonObject body = new JsonObject();
        body.addProperty("from", FROM_EMAIL);
        body.addProperty("reply_to", REPLY_TO_EMAIL);
        body.add("to", toArray);
        body.addProperty("subject", title);
        body.addProperty("html", htmlContent);
        body.addProperty("text", plainText);

        dispatchEmail(body, callback);
    }

    /**
     * Sends a password reset email via the Resend API with high-deliverability headers.
     *
     * @param toEmail Recipient email address
     * @param recoveryUrl The generated recovery redirect URL
     * @param callback Delivers result on the Main Thread
     */
    public void sendPasswordResetEmail(String toEmail, String recoveryUrl, EmailCallback callback) {
        String finalUrl = (recoveryUrl != null && !recoveryUrl.isEmpty()) ? recoveryUrl : "https://noaai.dpdns.org/reset/";
        String title = "Reset your NOA AI password 🔐";
        String bodyText = "You are receiving this email because we received a password reset request for your account. Please click the button below to choose a new password.";
        String buttonText = "Reset Password";
        String htmlContent = getPremiumHtmlTemplate(title, bodyText, buttonText, finalUrl);
        String plainText = "Reset your NOA AI password: " + finalUrl;

        JsonArray toArray = new JsonArray();
        toArray.add(toEmail);

        JsonObject body = new JsonObject();
        body.addProperty("from", FROM_EMAIL);
        body.addProperty("reply_to", REPLY_TO_EMAIL);
        body.add("to", toArray);
        body.addProperty("subject", title);
        body.addProperty("html", htmlContent);
        body.addProperty("text", plainText);

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

    private String getPremiumHtmlTemplate(String title, String bodyText, String buttonText, String confirmLink) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "    <meta charset=\"UTF-8\">\n"
                + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "    <title>" + title + "</title>\n"
                + "</head>\n"
                + "<body style=\"margin: 0; padding: 0; background-color: #0b0a12; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\">\n"
                + "\n"
                + "    <table width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color: #0b0a12; padding: 40px 20px;\">\n"
                + "        <tr>\n"
                + "            <td align=\"center\">\n"
                + "                <table width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width: 500px; background-color: #131124; border-radius: 16px; border: 1px solid #251f47; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.5);\">\n"
                + "                    <!-- Header Accent Line -->\n"
                + "                    <tr>\n"
                + "                        <td style=\"height: 4px; background: linear-gradient(90deg, #bc13fe, #7b12ff);\"></td>\n"
                + "                    </tr>\n"
                + "                    \n"
                + "                    <!-- Content Area -->\n"
                + "                    <tr>\n"
                + "                        <td style=\"padding: 40px 30px;\">\n"
                + "                            <!-- Headline -->\n"
                + "                            <h1 style=\"color: #ffffff; font-size: 22px; font-weight: 700; margin-top: 0; margin-bottom: 20px; text-align: center; letter-spacing: -0.5px;\">\n"
                + "                                " + title + "\n"
                + "                            </h1>\n"
                + "\n"
                + "                            <!-- Body Text -->\n"
                + "                            <p style=\"color: #b3b0cb; font-size: 15px; line-height: 1.6; margin-top: 0; margin-bottom: 30px; text-align: center;\">\n"
                + "                                " + bodyText + "\n"
                + "                            </p>\n"
                + "\n"
                + "                            <!-- CTA Button -->\n"
                + "                            <table width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin-bottom: 30px;\">\n"
                + "                                <tr>\n"
                + "                                    <td align=\"center\">\n"
                + "                                        <a href=\"" + confirmLink + "\" style=\"display: inline-block; background-color: #bc13fe; background: linear-gradient(135deg, #bc13fe 0%, #7b12ff 100%); color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 8px; font-weight: bold; font-size: 15px; box-shadow: 0 4px 15px rgba(188, 19, 254, 0.4); transition: all 0.2s ease;\">\n"
                + "                                            " + buttonText + "\n"
                + "                                        </a>\n"
                + "                                    </td>\n"
                + "                                </tr>\n"
                + "                            </table>\n"
                + "\n"
                + "                            <!-- Features List -->\n"
                + "                            <table width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color: rgba(188, 19, 254, 0.05); border-left: 3px solid #bc13fe; border-radius: 4px; margin-bottom: 30px;\">\n"
                + "                                <tr>\n"
                + "                                    <td style=\"padding: 16px 20px;\">\n"
                + "                                        <ul style=\"list-style-type: none; padding: 0; margin: 0; color: #b3b0cb; font-size: 14px; line-height: 1.8;\">\n"
                + "                                            <li style=\"margin-bottom: 8px; padding-left: 0;\">• Paste any YouTube lecture URL</li>\n"
                + "                                            <li style=\"margin-bottom: 8px; padding-left: 0;\">• Generate instant video transcripts and summaries</li>\n"
                + "                                            <li style=\"margin-top: 0; padding-left: 0;\">• Navigate with precise, interactive timestamps</li>\n"
                + "                                        </ul>\n"
                + "                                    </td>\n"
                + "                                </tr>\n"
                + "                            </table>\n"
                + "\n"
                + "                            <!-- Signature -->\n"
                + "                            <p style=\"color: #b3b0cb; font-size: 14px; line-height: 1.5; margin-bottom: 0;\">\n"
                + "                                Happy studying!<br>\n"
                + "                                <strong style=\"color: #ffffff;\">The NOA AI Team</strong>\n"
                + "                            </p>\n"
                + "                        </td>\n"
                + "                    </tr>\n"
                + "\n"
                + "                    <!-- Footer Area -->\n"
                + "                    <tr>\n"
                + "                        <td style=\"background-color: rgba(0, 0, 0, 0.2); padding: 24px 30px; border-top: 1px solid #251f47; text-align: center;\">\n"
                + "                            <p style=\"color: #6b6585; font-size: 12px; line-height: 1.6; margin: 0;\">\n"
                + "                                For any questions or concerns, please visit our help center at <a href=\"https://noaai.netlify.app\" style=\"color: #bc13fe; text-decoration: none; font-weight: 500;\">noaai.netlify.app</a> or contact our support team.\n"
                + "                            </p>\n"
                + "                        </td>\n"
                + "                    </tr>\n"
                + "                </table>\n"
                + "            </td>\n"
                + "        </tr>\n"
                + "    </table>\n"
                + "\n"
                + "</body>\n"
                + "</html>";
    }
}