package com.example.ailecturesummarizer;

import org.junit.Test;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import static org.junit.Assert.*;

public class ExampleUnitTest {

    @Test
    public void testResendApiKeyValidity() throws Exception {
        // 1. Load local.properties
        Properties props = new Properties();
        File propsFile = new File("../local.properties"); // Gradle runs tests from app/ directory
        if (!propsFile.exists()) {
            propsFile = new File("local.properties"); // Fallback if run from root
        }
        
        assertTrue("local.properties file must exist", propsFile.exists());
        try (FileInputStream fis = new FileInputStream(propsFile)) {
            props.load(fis);
        }
        
        String apiKey = props.getProperty("RESEND_API_KEY");
        assertNotNull("RESEND_API_KEY must be defined in local.properties", apiKey);
        assertFalse("RESEND_API_KEY must not be empty", apiKey.trim().isEmpty());
        
        // 2. Perform a test email dispatch using the API key
        OkHttpClient client = new OkHttpClient();
        
        // Construct the test payload
        String testHtml = "<!DOCTYPE html><html><body style=\"background-color:#0b0a12;color:#ffffff;font-family:sans-serif;padding:20px;\">"
                + "<div style=\"background-color:#131124;padding:30px;border-radius:12px;border:1px solid #251f47;max-width:400px;margin:0 auto;text-align:center;\">"
                + "<h1 style=\"color:#bc13fe;font-size:20px;\">Reset your NOA AI password 🔐</h1>"
                + "<p style=\"color:#b3b0cb;font-size:14px;\">This is a secure E2E unit test for your password recovery routing.</p>"
                + "<a href=\"https://noaai.dpdns.org/reset/\" style=\"display:inline-block;background:#bc13fe;color:white;padding:10px 20px;border-radius:6px;text-decoration:none;font-weight:bold;\">Reset Password</a>"
                + "</div></body></html>";

        JsonObject payloadObj = new JsonObject();
        payloadObj.addProperty("from", "system@noaai.dpdns.org");
        
        JsonArray toArray = new JsonArray();
        toArray.add("sudheerpullagura041@gmail.com");
        payloadObj.add("to", toArray);
        
        payloadObj.addProperty("subject", "Reset your NOA AI password (E2E Test) 🔐");
        payloadObj.addProperty("html", testHtml);
        
        String jsonPayload = payloadObj.toString();
                
        Request request = new Request.Builder()
                .url("https://api.resend.com/emails")
                .post(RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();
                
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            System.out.println("Resend API E2E Response Status: " + response.code());
            System.out.println("Resend API E2E Response Body: " + body);
            
            // Check if authorization and dispatch succeeded
            assertTrue("Resend API request failed with status: " + response.code() + " and body: " + body, response.isSuccessful());
        }
    }
}