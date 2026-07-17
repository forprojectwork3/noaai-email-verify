package com.example.ailecturesummarizer;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class  ProfileActivity extends AppCompatActivity {

    private ImageView ivProfilePicture;
    private EditText etProfileUsername;
    private MaterialButton btnSaveProfile;
    private MaterialButton btnLogoutProfile;

    private String selectedImageUriStr = null;
    private ProgressDialog progressDialog;

    // Modern Activity Result API image picker
    private final ActivityResultLauncher<String> selectImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    handleSelectedImage(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        etProfileUsername = findViewById(R.id.etProfileUsername);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnLogoutProfile = findViewById(R.id.btnLogoutProfile);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadUserData();

        // Setup Photo Picker Click
        findViewById(R.id.cardProfileAvatar).setOnClickListener(v -> selectImageLauncher.launch("image/*"));

        // Setup Save Changes
        btnSaveProfile.setOnClickListener(v -> saveProfileChanges());

        // Setup Logout Button
        btnLogoutProfile.setOnClickListener(v -> performLogout());
    }

    private void loadUserData() {
        SharedPreferences prefs = getSharedPreferences("noa_session", MODE_PRIVATE);
        String name = prefs.getString("user_name", "");
        String avatarUrl = prefs.getString("avatar_url", "");

        etProfileUsername.setText(name);

        if (!TextUtils.isEmpty(avatarUrl)) {
            selectedImageUriStr = avatarUrl;
            Glide.with(this)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(ivProfilePicture);
        } else {
            ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }

    private void handleSelectedImage(Uri uri) {
        showLoading("Processing image...");
        // Copy image to internal storage to avoid permission loss after app restarts
        new Thread(() -> {
            String path = copyImageToInternalStorage(uri);
            runOnUiThread(() -> {
                hideLoading();
                if (path != null) {
                    selectedImageUriStr = Uri.fromFile(new File(path)).toString();
                    Glide.with(ProfileActivity.this)
                            .load(selectedImageUriStr)
                            .circleCrop()
                            .into(ivProfilePicture);
                } else {
                    Toast.makeText(this, "Failed to load selected image", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private String copyImageToInternalStorage(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;
            File file = new File(getFilesDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
            try (OutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                return file.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveProfileChanges() {
        String newUsername = etProfileUsername.getText().toString().trim();
        if (TextUtils.isEmpty(newUsername)) {
            etProfileUsername.setError("Username cannot be empty");
            return;
        }

        showLoading("Saving changes...");

        if (selectedImageUriStr != null && selectedImageUriStr.startsWith("file:/")) {
            // Downscale and compress to WebP in a background worker thread
            new Thread(() -> {
                try {
                    Uri uri = Uri.parse(selectedImageUriStr);
                    String filePath = uri.getPath();
                    if (filePath == null) {
                        runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(ProfileActivity.this, "Invalid image file path", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    // Decode file into Bitmap
                    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                    android.graphics.Bitmap original = android.graphics.BitmapFactory.decodeFile(filePath, options);
                    if (original == null) {
                        runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(ProfileActivity.this, "Failed to decode chosen image", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    // Downscale client-side to maximum bounds of 2048px
                    int w = original.getWidth();
                    int h = original.getHeight();
                    if (w > 2048 || h > 2048) {
                        float ratio = (float) w / (float) h;
                        if (w > h) {
                            w = 2048;
                            h = Math.round(w / ratio);
                        } else {
                            h = 2048;
                            w = Math.round(h * ratio);
                        }
                        android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(original, w, h, true);
                        original.recycle();
                        original = scaled;
                    }

                    // Compress Bitmap to WebP byte stream (lossy, quality 85)
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        original.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 85, baos);
                    } else {
                        original.compress(android.graphics.Bitmap.CompressFormat.WEBP, 85, baos);
                    }
                    byte[] webpBytes = baos.toByteArray();
                    original.recycle();

                    // Get User UUID for target storage path /avatars/[user_id]/profile.webp
                    String userId = getSharedPreferences("noa_session", MODE_PRIVATE).getString("user_id", null);
                    if (userId == null || userId.isEmpty()) {
                        runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(ProfileActivity.this, "User session expired or not found", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    String filename = userId + "/profile.webp";

                    // Upload via SupabaseAuthManager
                    SupabaseAuthManager.getInstance().uploadAvatarBytes(ProfileActivity.this, webpBytes, filename, new SupabaseAuthManager.AuthCallback() {
                        @Override
                        public void onSuccess(String publicUrl) {
                            // Update user metadata in cloud and local cache
                            SupabaseAuthManager.getInstance().updateUserProfile(ProfileActivity.this, newUsername, publicUrl, new SupabaseAuthManager.AuthCallback() {
                                @Override
                                public void onSuccess(String message) {
                                    hideLoading();
                                    Toast.makeText(ProfileActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                                    Intent resultIntent = new Intent();
                                    setResult(RESULT_OK, resultIntent);
                                    finish();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    hideLoading();
                                    Toast.makeText(ProfileActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                        @Override
                        public void onError(String errorMessage) {
                            hideLoading();
                            Toast.makeText(ProfileActivity.this, "Upload failed: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(ProfileActivity.this, "Error processing image: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
            return;
        }

        // Just update username if no new image selected
        SupabaseAuthManager.getInstance().updateUserProfile(this, newUsername, selectedImageUriStr, new SupabaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                hideLoading();
                Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                Intent resultIntent = new Intent();
                setResult(RESULT_OK, resultIntent);
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                hideLoading();
                Toast.makeText(ProfileActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void performLogout() {
        showLoading("Logging out...");
        SupabaseAuthManager.getInstance().logOutUser(this, new SupabaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                hideLoading();
                Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                hideLoading();
                Toast.makeText(ProfileActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    private void hideLoading() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
