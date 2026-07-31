package com.example.ailecturesummarizer;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.imageview.ShapeableImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.example.ailecturesummarizer.adapter.HistoryAdapter;
import com.example.ailecturesummarizer.api.RetrofitClient;
import com.example.ailecturesummarizer.model.HistoryItem;
import com.example.ailecturesummarizer.model.SummaryResponse;
import com.example.ailecturesummarizer.model.TimestampResponse;
import com.example.ailecturesummarizer.model.TranscriptResponse;
import com.example.ailecturesummarizer.model.UrlRequest;
import com.example.ailecturesummarizer.database.NoaDatabaseHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * MainActivity — Core workspace for NOA AI Lecture Summarizer.
 *
 * Features:
 * • DrawerLayout sidebar with RecyclerView history
 * • YouTubePlayerView (pierfrancescosoffritti library) — embedded, no external
 * app launch
 * • TextWatcher + YouTube URL regex to unlock action buttons
 * • 4 action buttons (Summarize, Transcript, Timestamps, Chat with AI)
 * • Retrofit API calls to Flask backend endpoints
 * • SQLite caching for summaries
 *
 * Backend Endpoints Used:
 * • POST /api/summary → Summarize button
 * • POST /api/transcript → Transcript button
 * • POST /api/timestamps → Timestamps button
 * • Chat button opens ChatbotActivity (uses /api/summary internally)
 *
 * YouTube Player:
 * • Uses AndroidYouTubePlayer library
 * (com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0)
 * • Video loads INSIDE the app — does NOT open YouTube app or browser
 * • Lifecycle-aware (registered with getLifecycle())
 * • Supports play, pause, seek, fullscreen, portrait & landscape
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String YOUTUBE_BASE = "https://www.youtube.com";

    // ── Animation constants ──────────────────────────────────────────────────
    private static final float ALPHA_DISABLED = 0.4f;
    private static final float ALPHA_ENABLED = 1.0f;
    private static final long ALPHA_ANIM_MS = 350L;

    private static final float PRESS_SCALE = 0.96f;
    private static final float NORMAL_SCALE = 1.0f;
    private static final float BOUNCE_SCALE = 1.04f;
    private static final long PRESS_ANIM_MS = 80L;
    private static final long RELEASE_ANIM_MS = 300L;

    // ── UI References ────────────────────────────────────────────────────────
    private DrawerLayout drawerLayout;
    private YouTubePlayerView youTubePlayerView;
    private TextInputEditText etYoutubeUrl;
    private HistoryAdapter historyAdapter;
    private android.widget.LinearLayout llPlayerErrorOverlay;
    private TextView tvPlayerErrorOverlayMessage;

    private MaterialButton btnSummarize;
    private MaterialButton btnTranscript;
    private MaterialButton btnStudyPlan; // Timestamps
    private MaterialButton btnBriefNotes; // Chat with AI

    // Sidebar UI references
    private LinearLayout btnLogout; // LinearLayout acting as logout row in sidebar
    private LinearLayout llUserSection; // Bottom user profile section in sidebar
    private ShapeableImageView ivUserAvatar;
    private TextView tvNavHeaderName;
    private TextView tvNavHeaderEmail;

    // ── State ────────────────────────────────────────────────────────────────
    private YouTubePlayer activeYouTubePlayer;
    private String currentVideoId;
    private String currentUrl;
    private String currentVideoTitle = "";
    private String currentChatId = null;
    private boolean buttonsEnabled = false;
    private boolean playerReady = false;

    // ── Database & Threading ─────────────────────────────────────────────────
    private NoaDatabaseHelper dbHelper;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Dialog progressDialog;
    private android.widget.TextView progressMessageView;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = NoaDatabaseHelper.getInstance(this);

        bindViews();
        setupToolbar();
        setupDrawer();
        setupUserSession();
        setupYouTubePlayer();
        setupUrlInput();
        setupActionButtons();
        setupLogoutButton();

        // Load chat history immediately on first open
        loadChatHistory();

        // Handle back press: close drawer if open, otherwise go back
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                            drawerLayout.closeDrawer(GravityCompat.START);
                        } else {
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupUserSession();
        loadChatHistory();
    }

    // ── View binding ──────────────────────────────────────────────────────────
    private void bindViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        youTubePlayerView = findViewById(R.id.youTubePlayerView);
        etYoutubeUrl = findViewById(R.id.etYoutubeUrl);

        btnSummarize = findViewById(R.id.btnSummarize);
        btnTranscript = findViewById(R.id.btnTranscript);
        btnStudyPlan = findViewById(R.id.btnStudyPlan);
        btnBriefNotes = findViewById(R.id.btnBriefNotes);

        // Sidebar views
        btnLogout = findViewById(R.id.btnLogout);
        llUserSection = findViewById(R.id.llUserSection);
        ivUserAvatar = (ShapeableImageView) findViewById(R.id.ivUserAvatar);
        tvNavHeaderName = findViewById(R.id.tvNavHeaderName);
        tvNavHeaderEmail = findViewById(R.id.tvNavHeaderEmail);

        llPlayerErrorOverlay = findViewById(R.id.llPlayerErrorOverlay);
        tvPlayerErrorOverlayMessage = findViewById(R.id.tvPlayerErrorOverlayMessage);
    }

    // ── Toolbar ──────────────────────────────────────────────────────────────
    private void setupToolbar() {
        ImageButton btnMenu = findViewById(R.id.btnMenu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }
    }

    // ── Drawer & RecyclerView ────────────────────────────────────────────────
    private void setupDrawer() {
        RecyclerView rvHistory = findViewById(R.id.rvHistory);
        historyAdapter = new HistoryAdapter();
        if (rvHistory != null) {
            rvHistory.setLayoutManager(new LinearLayoutManager(this));
            rvHistory.setAdapter(historyAdapter);
        }

        historyAdapter.setOnItemClickListener(item -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            // Navigate to ChatbotActivity to restore this chat session
            // The chat title is used as the video title for display
            if (item.getChatId() != null && !item.getChatId().isEmpty()) {
                Intent chatIntent = new Intent(MainActivity.this, ChatbotActivity.class);
                chatIntent.putExtra(ChatbotActivity.EXTRA_VIDEO_URL, item.getUrl());
                chatIntent.putExtra(ChatbotActivity.EXTRA_VIDEO_TITLE, item.getTitle());
                chatIntent.putExtra("chat_id", item.getChatId());
                startActivity(chatIntent);
            }
        });

        historyAdapter.setOnDeleteClickListener((item, position) -> {
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Delete Chat")
                    .setMessage("Are you sure you want to delete this chat session?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        String chatId = item.getChatId();
                        if (chatId != null && !chatId.isEmpty()) {
                            SupabaseAuthManager.getInstance().deleteChat(MainActivity.this, chatId,
                                    new SupabaseAuthManager.AuthCallback() {
                                        @Override
                                        public void onSuccess(String message) {
                                            historyAdapter.removeItem(position);
                                            Toast.makeText(MainActivity.this, "Chat deleted", Toast.LENGTH_SHORT)
                                                    .show();
                                        }

                                        @Override
                                        public void onError(String errorMessage) {
                                            Toast.makeText(MainActivity.this, "Failed to delete chat: " + errorMessage,
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        } else {
                            // If it's a local session item, just remove it from UI
                            historyAdapter.removeItem(position);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // "+ New Chat" button — elastic touch feedback
        View btnNewChat = findViewById(R.id.btnNewChat);
        if (btnNewChat != null) {
            attachSidebarElasticTouch(btnNewChat, () -> {
                String defaultTitle = "New Chat Session";
                SupabaseAuthManager.getInstance().createChat(
                        MainActivity.this, defaultTitle,
                        new SupabaseAuthManager.AuthCallback() {
                            @Override
                            public void onSuccess(String chatId) {
                                currentChatId = chatId;
                                Toast.makeText(MainActivity.this, "Chat created successfully", Toast.LENGTH_SHORT)
                                        .show();
                                historyAdapter.addItem(new HistoryItem(defaultTitle, "", chatId));
                                loadChatHistory();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e(TAG, "createChat error: " + errorMessage);
                                Toast.makeText(MainActivity.this, "Failed to create chat: " + errorMessage,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            });
        }

        // Animate sidebar slide-in on open, refresh chats, stagger list items
        drawerLayout.addDrawerListener(
                new DrawerLayout.SimpleDrawerListener() {
                    @Override
                    public void onDrawerOpened(View drawerView) {
                        SidebarAnimations.fadeSlideIn(drawerView);
                        if (rvHistory != null) {
                            SidebarAnimations.staggeredRecyclerViewAnimation(rvHistory);
                        }
                        loadChatHistory();
                    }
                });
    }

    /** Fetches chat history from Supabase and refreshes the RecyclerView. */
    private void loadChatHistory() {
        SupabaseAuthManager.getInstance().fetchChats(this,
                new SupabaseAuthManager.ChatsCallback() {
                    @Override
                    public void onSuccess(java.util.ArrayList<SupabaseAuthManager.SupabaseChat> chats) {
                        java.util.ArrayList<HistoryItem> historyItems = new java.util.ArrayList<>();
                        for (SupabaseAuthManager.SupabaseChat chat : chats) {
                            String title = chat.title != null ? chat.title : "Untitled";
                            String id = chat.id != null ? chat.id : "";
                            historyItems.add(new HistoryItem(title, "", id));
                        }
                        historyAdapter.setItems(historyItems);
                        if (historyItems.isEmpty()) {
                            historyAdapter.addItem(
                                    new HistoryItem("No chats yet — tap + New Chat", "", ""));
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "fetchChats error: " + errorMessage);
                        Toast.makeText(MainActivity.this, "Failed to load chats: " + errorMessage, Toast.LENGTH_SHORT)
                                .show();
                    }
                });
    }

    // ── Set user session info in Sidebar ─────────────────────────────────────
    private void setupUserSession() {
        SupabaseAuthManager auth = SupabaseAuthManager.getInstance();

        // 1. Show cached values immediately (no network needed)
        String cachedName = auth.getCachedUserName(this);
        String cachedEmail = auth.getCachedUserEmail(this);
        String cachedAvatar = auth.getCachedAvatarUrl(this);

        if (tvNavHeaderName != null)
            tvNavHeaderName.setText(TextUtils.isEmpty(cachedName) ? "User" : cachedName);
        if (tvNavHeaderEmail != null)
            tvNavHeaderEmail.setText(TextUtils.isEmpty(cachedEmail) ? "" : cachedEmail);
        if (ivUserAvatar != null && !TextUtils.isEmpty(cachedAvatar))
            loadAvatarIntoView(cachedAvatar);

        // 2. Fetch live profile from Supabase (runs on OkHttp thread, posts back to UI)
        auth.fetchUserProfile(this, new SupabaseAuthManager.ProfileCallback() {
            @Override
            public void onSuccess(String displayName, String avatarUrl) {
                // Persist avatar URL to SharedPreferences so it survives logout/login
                if (!TextUtils.isEmpty(avatarUrl)) {
                    getSharedPreferences("noa_session", MODE_PRIVATE)
                            .edit().putString("avatar_url", avatarUrl).apply();
                }
                if (tvNavHeaderName != null && !TextUtils.isEmpty(displayName))
                    tvNavHeaderName.setText(displayName);
                if (ivUserAvatar != null && !TextUtils.isEmpty(avatarUrl))
                    loadAvatarIntoView(avatarUrl);
            }

            @Override
            public void onError(String err) {
                Log.w(TAG, "fetchUserProfile: " + err);
            }
        });

        // 3. Tap on user section → open ProfileActivity (with elastic touch feedback)
        if (llUserSection != null) {
            attachSidebarElasticTouch(llUserSection, () -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
            });
        }
    }

    /**
     * Loads an avatar into ivUserAvatar using Glide (supports http/https and
     * content URIs).
     * Falls back to an initials-based circular bitmap when the URL fails or is
     * empty.
     *
     * All heavy work is handled off the main thread by Glide or our
     * executorService.
     */
    private void loadAvatarIntoView(String uriString) {
        if (ivUserAvatar == null)
            return;

        if (!TextUtils.isEmpty(uriString) &&
                (uriString.startsWith("http://") || uriString.startsWith("https://"))) {
            // ── Remote URL: use Glide (loads async, transitions smoothly) ────
            String displayName = (tvNavHeaderName != null)
                    ? tvNavHeaderName.getText().toString()
                    : "";
            int sizePx = (int) (40 * getResources().getDisplayMetrics().density);
            Bitmap fallback = AvatarHelper.createInitialsBitmap(displayName, sizePx);

            Glide.with(this)
                    .load(uriString)
                    .circleCrop()
                    .placeholder(new android.graphics.drawable.BitmapDrawable(
                            getResources(), fallback))
                    .error(new android.graphics.drawable.BitmapDrawable(
                            getResources(), fallback))
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .into(ivUserAvatar);
        } else {
            // ── No remote URL: show initials bitmap generated off main thread ─
            String displayName = (tvNavHeaderName != null)
                    ? tvNavHeaderName.getText().toString()
                    : "";
            final int sizePx = (int) (40 * getResources().getDisplayMetrics().density);
            executorService.execute(() -> {
                Bitmap bmp = AvatarHelper.createInitialsBitmap(displayName, sizePx);
                mainHandler.post(() -> {
                    if (ivUserAvatar != null)
                        ivUserAvatar.setImageBitmap(bmp);
                });
            });
        }
    }

    /**
     * Attaches elastic scale press/release/cancel touch feedback to a sidebar view.
     * The onClick Runnable is fired on ACTION_UP when the view is enabled.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void attachSidebarElasticTouch(View view, Runnable onClick) {
        if (view == null)
            return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    SidebarAnimations.scalePress(v);
                    break;
                case MotionEvent.ACTION_UP:
                    SidebarAnimations.scaleRelease(v);
                    if (v.isEnabled())
                        onClick.run();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    SidebarAnimations.scaleCancel(v);
                    break;
            }
            return true;
        });
    }

    // ── YouTube Player ───────────────────────────────────────────────────────
    /**
     * Sets up the embedded YouTube player using the AndroidYouTubePlayer library.
     *
     * IMPORTANT: This player renders video INSIDE the app using a WebView-based
     * component. It does NOT open the YouTube app or any browser.
     *
     * The library is: com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0
     * It supports: play, pause, seek, fullscreen, portrait, landscape.
     *
     * KEY FIX: Uses manual initialization with IFramePlayerOptions to:
     * - Set origin to prevent embed error 152
     * - Disable related videos (rel=0)
     * - Enable the JS API for proper player control
     * - Prevent autoplay so user must tap to play
     *
     * Also fixes NestedScrollView touch conflict by intercepting touches on the
     * player card and delegating them to the YouTubePlayerView's internal WebView.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupYouTubePlayer() {
        // Register with lifecycle — handles onPause/onResume/onDestroy automatically
        getLifecycle().addObserver(youTubePlayerView);

        // ── Fix NestedScrollView touch conflict ──
        // When user touches the YouTube player card, tell the parent NestedScrollView
        // to NOT intercept touch events, so the player's internal WebView receives
        // them.
        View playerCard = findViewById(R.id.cardYouTubePlayer);
        if (playerCard != null) {
            playerCard.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Disable parent scroll interception when touching the player
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Re-enable parent scroll interception when touch ends
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                // Return false — let the touch propagate to the YouTubePlayerView inside
                return false;
            });
        }

        // ── IFrame Player Options ──
        // These options are critical for reliable embedded playback:
        // - origin: prevents embed error 152 ("playback on other websites has been
        // disabled") by setting a valid origin for the IFrame
        // - rel(0): prevents related videos from other channels at the end
        // - ivLoadPolicy(3): hides video annotations for a cleaner player
        // - ccLoadPolicy(0): don't force closed captions
        // - enablejsapi: allows the library to control the player via JavaScript
        IFramePlayerOptions iFrameOptions = new IFramePlayerOptions.Builder(MainActivity.this)
                .controls(1) // Show player controls (play, pause, seek, etc.)
                .rel(0) // No related videos from other channels
                .ivLoadPolicy(3) // Hide video annotations
                .ccLoadPolicy(0) // Don't force closed captions
                .build();

        // ── Manual Initialization with Listener + IFrame Options ──
        // We use initialize() instead of the automatic init (which we disabled in XML)
        // to pass custom IFramePlayerOptions for reliable embed playback.
        youTubePlayerView.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer youTubePlayer) {
                activeYouTubePlayer = youTubePlayer;
                playerReady = true;
                Log.d(TAG, "YouTube player is ready (manual init with IFramePlayerOptions)");

                // If a video was already entered before player was ready, cue it
                // (cueVideo shows thumbnail only — user taps to play)
                if (currentVideoId != null) {
                    youTubePlayer.cueVideo(currentVideoId, 0);
                }
            }

            @Override
            public void onError(@NonNull YouTubePlayer youTubePlayer,
                    @NonNull PlayerConstants.PlayerError error) {
                Log.e(TAG, "YouTube player error: " + error.name());
                String userMessage;
                switch (error) {
                    case VIDEO_NOT_FOUND:
                        userMessage = "This video was not found. Please check the URL.";
                        break;
                    case VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER:
                        userMessage = "This video cannot be played in an embedded player. " +
                                "The video owner has disabled embedding.";
                        break;
                    case INVALID_PARAMETER_IN_REQUEST:
                        userMessage = "Invalid video request. Please try a different URL.";
                        break;
                    default:
                        if (error.name().equals("ERROR_REQUEST_MISSING_HTTP_REFERER")) {
                            userMessage = "Missing HTTP referrer. Playback blocked by YouTube security policies.";
                        } else {
                            userMessage = "Video cannot be embedded or played inside the app. (Error: " + error.name()
                                    + ")";
                        }
                        break;
                }

                // Show error message overlay on the player card
                if (llPlayerErrorOverlay != null && tvPlayerErrorOverlayMessage != null) {
                    tvPlayerErrorOverlayMessage.setText(userMessage);
                    llPlayerErrorOverlay.setVisibility(View.VISIBLE);
                }

                Toast.makeText(MainActivity.this, userMessage, Toast.LENGTH_LONG).show();
            }
        }, iFrameOptions);
    }

    // ── URL Input + TextWatcher ──────────────────────────────────────────────
    /**
     * Watches the URL input field. When a valid YouTube URL is detected:
     * 1. Extracts the video ID
     * 2. Cues the video into the embedded player (shows thumbnail, no autoplay)
     * 3. Unlocks the action buttons
     * 4. Fetches video title via oEmbed API in background
     *
     * KEY FIX: Uses cueVideo() instead of loadVideo() to avoid autoplay issues
     * inside a NestedScrollView. The user taps the player to start playback.
     *
     * KEY FIX: Debounces the video loading with a 500ms delay so rapid keystrokes
     * don't cause rapid load/abort cycles that confuse the player.
     */
    private void setupUrlInput() {
        etYoutubeUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                String videoId = extractVideoId(input);

                if (videoId != null && !videoId.equals(currentVideoId)) {
                    currentVideoId = videoId;
                    currentUrl = input;
                    currentVideoTitle = "Video " + videoId;
                    unlockActionButtons();

                    // Hide the error overlay when loading a new valid video ID
                    if (llPlayerErrorOverlay != null) {
                        llPlayerErrorOverlay.setVisibility(View.GONE);
                    }

                    // Debounce: remove any pending load, wait 500ms before cueing.
                    // This prevents rapid load/abort cycles when user is pasting/typing.
                    final String idToLoad = videoId;
                    mainHandler.removeCallbacksAndMessages("cue_video");
                    mainHandler.postDelayed(() -> {
                        // Cue video INSIDE the app — shows thumbnail, user taps to play.
                        // cueVideo() is safer than loadVideo() in a NestedScrollView
                        // because it doesn't auto-play (which can cause embed errors).
                        if (playerReady && activeYouTubePlayer != null
                                && idToLoad.equals(currentVideoId)) {
                            activeYouTubePlayer.cueVideo(idToLoad, 0);
                        }
                    }, 500);

                    // Fetch real title in background
                    fetchVideoMetadata(videoId);

                } else if (videoId == null && buttonsEnabled) {
                    currentVideoId = null;
                    currentUrl = null;
                    currentVideoTitle = "";
                    lockActionButtons();

                    // Hide the error overlay
                    if (llPlayerErrorOverlay != null) {
                        llPlayerErrorOverlay.setVisibility(View.GONE);
                    }

                    // Stop any pending video cue
                    mainHandler.removeCallbacksAndMessages("cue_video");
                }
            }
        });
    }

    // ── Action Buttons ───────────────────────────────────────────────────────
    private void setupActionButtons() {
        setButtonsLocked(true);

        attachElasticTouch(btnSummarize, () -> onActionClicked("Summarize"));
        attachElasticTouch(btnTranscript, () -> onActionClicked("Transcript"));
        attachElasticTouch(btnStudyPlan, () -> onActionClicked("Timestamps"));
        attachElasticTouch(btnBriefNotes, () -> onActionClicked("Chat with AI"));
    }

    // ── Log Out Button ───────────────────────────────────────────────────────
    private void setupLogoutButton() {
        if (btnLogout != null) {
            attachSidebarElasticTouch(btnLogout, this::performLogout);
        }
    }

    /** Full logout: calls Supabase, clears session, navigates to LoginActivity. */
    private void performLogout() {
        showLoadingDialog("Logging out...");
        SupabaseAuthManager.getInstance().logOutUser(MainActivity.this,
                new SupabaseAuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(String message) {
                        dismissLoadingDialog();
                        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START))
                            drawerLayout.closeDrawer(GravityCompat.START);
                        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        androidx.core.app.ActivityOptionsCompat options = androidx.core.app.ActivityOptionsCompat
                                .makeCustomAnimation(
                                        MainActivity.this,
                                        android.R.anim.fade_in,
                                        android.R.anim.fade_out);
                        startActivity(intent, options.toBundle());
                        finish();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        dismissLoadingDialog();
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void onActionClicked(String action) {
        if (currentVideoId == null || currentUrl == null) {
            Toast.makeText(this, "Please enter a valid YouTube URL first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add to session history
        historyAdapter.addItem(new HistoryItem(
                action + " — " + currentVideoId,
                currentUrl,
                action));

        switch (action) {
            case "Summarize":
                handleSummarizeAction();
                break;
            case "Transcript":
                handleTranscriptAction();
                break;
            case "Timestamps":
                handleTimestampsAction();
                break;
            case "Chat with AI":
                handleChatAction();
                break;
        }
    }

    // ── Summarize → POST /api/summary ────────────────────────────────────────
    /**
     * Calls POST /api/summary with { "url": currentUrl }
     * Response: { "success": bool, "video_id": str, "summary": str }
     *
     * Also checks local SQLite cache first to avoid unnecessary network calls.
     */
    private void handleSummarizeAction() {
        final String videoId = currentVideoId;
        final String url = currentUrl;
        if (videoId == null || url == null)
            return;

        executorService.execute(() -> {
            // Check local SQLite cache first
            String cachedSummary = dbHelper.getSummary(videoId);
            if (cachedSummary != null) {
                mainHandler.post(() -> displayContent("Video Summary", cachedSummary));
                return;
            }

            mainHandler.post(() -> {
                showLoadingDialog("Generating Summary...\nThis may take up to 60 seconds.");
                RetrofitClient.getApiService()
                        .getSummary(new UrlRequest(url))
                        .enqueue(new Callback<SummaryResponse>() {
                            @Override
                            public void onResponse(@NonNull Call<SummaryResponse> call,
                                    @NonNull Response<SummaryResponse> response) {
                                dismissLoadingDialog();
                                if (response.isSuccessful() && response.body() != null) {
                                    SummaryResponse body = response.body();
                                    if (body.success && !TextUtils.isEmpty(body.summary)) {
                                        // Cache the result locally
                                        executorService.execute(() -> dbHelper.insertSummary(videoId, body.summary));
                                        displayContent("Video Summary", body.summary);
                                    } else {
                                        String errMsg = !TextUtils.isEmpty(body.message)
                                                ? body.message
                                                : "Summary generation failed";
                                        Toast.makeText(MainActivity.this, errMsg, Toast.LENGTH_LONG).show();
                                    }
                                } else {
                                    handleHttpError(response.code(), "summary");
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<SummaryResponse> call,
                                    @NonNull Throwable t) {
                                dismissLoadingDialog();
                                handleNetworkError(t);
                            }
                        });
            });
        });
    }

    // ── Transcript → POST /api/transcript ────────────────────────────────────
    /**
     * Calls POST /api/transcript with { "url": currentUrl }
     * Response: { "success": bool, "video_id": str, "transcript": str }
     */
    private void handleTranscriptAction() {
        final String url = currentUrl;
        if (url == null)
            return;

        showLoadingDialog("Fetching Transcript...");
        RetrofitClient.getApiService()
                .getTranscript(new UrlRequest(url))
                .enqueue(new Callback<TranscriptResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TranscriptResponse> call,
                            @NonNull Response<TranscriptResponse> response) {
                        dismissLoadingDialog();
                        if (response.isSuccessful() && response.body() != null) {
                            TranscriptResponse body = response.body();
                            if (body.success && !TextUtils.isEmpty(body.transcript)) {
                                displayContent("Lecture Transcript", body.transcript);
                            } else {
                                String errMsg = !TextUtils.isEmpty(body.message)
                                        ? body.message
                                        : "No transcript available for this video.";
                                Toast.makeText(MainActivity.this, errMsg, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            handleHttpError(response.code(), "transcript");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<TranscriptResponse> call,
                            @NonNull Throwable t) {
                        dismissLoadingDialog();
                        handleNetworkError(t);
                    }
                });
    }

    // ── Timestamps → POST /api/timestamps ────────────────────────────────────
    /**
     * Calls POST /api/timestamps with { "url": currentUrl }
     * Response: { "success": bool, "video_id": str,
     * "timestamps": [{"time": "MM:SS", "topic": "..."}] }
     */
    private void handleTimestampsAction() {
        final String url = currentUrl;
        if (url == null)
            return;

        showLoadingDialog("Extracting Timestamps...\nThis may take up to 60 seconds.");
        RetrofitClient.getApiService()
                .getTimestamps(new UrlRequest(url))
                .enqueue(new Callback<TimestampResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TimestampResponse> call,
                            @NonNull Response<TimestampResponse> response) {
                        dismissLoadingDialog();
                        if (response.isSuccessful() && response.body() != null) {
                            TimestampResponse body = response.body();
                            if (body.success && body.timestamps != null && !body.timestamps.isEmpty()) {
                                StringBuilder sb = new StringBuilder();
                                for (TimestampResponse.TimestampItem item : body.timestamps) {
                                    sb.append("<b>").append(item.time).append("</b>")
                                            .append("  —  ").append(item.topic)
                                            .append("<br/><br/>");
                                }
                                displayContent("Lecture Timestamps", sb.toString());
                            } else {
                                String errMsg = !TextUtils.isEmpty(body.message)
                                        ? body.message
                                        : "No timestamps generated.";
                                Toast.makeText(MainActivity.this, errMsg, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            handleHttpError(response.code(), "timestamps");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<TimestampResponse> call,
                            @NonNull Throwable t) {
                        dismissLoadingDialog();
                        handleNetworkError(t);
                    }
                });
    }

    // ── Chat → Opens ChatbotActivity ─────────────────────────────────────────
    /**
     * Passes the current YouTube URL and video title to ChatbotActivity.
     * ChatbotActivity uses POST /api/summary to answer user questions.
     */
    private void handleChatAction() {
        if (currentUrl == null)
            return;
        Intent intent = new Intent(this, ChatbotActivity.class);
        intent.putExtra(ChatbotActivity.EXTRA_VIDEO_URL, currentUrl);
        intent.putExtra(ChatbotActivity.EXTRA_VIDEO_TITLE, currentVideoTitle);
        startActivity(intent);
    }

    // ── Error Helpers ────────────────────────────────────────────────────────
    private void handleHttpError(int code, String feature) {
        String msg;
        switch (code) {
            case 400:
                msg = "Invalid YouTube URL sent to server.";
                break;
            case 404:
                msg = "API endpoint not found (HTTP 404). Check server.";
                break;
            case 405:
                msg = "Method not allowed (HTTP 405). Check server.";
                break;
            case 422:
                msg = "Unprocessable request (HTTP 422).";
                break;
            case 500:
                msg = "Server error (HTTP 500). Check backend logs.";
                break;
            default:
                msg = "HTTP error " + code + " for " + feature + ".";
                break;
        }
        Log.e(TAG, "HTTP error " + code + " for " + feature);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void handleNetworkError(Throwable t) {
        String raw = t.getMessage() != null ? t.getMessage() : t.toString();
        String msg;
        if (raw.contains("ECONNREFUSED") || raw.contains("Connection refused")) {
            msg = "Cannot reach server. Is the Flask backend running on port 5000?";
        } else if (raw.contains("timeout") || raw.contains("Timeout")) {
            msg = "Request timed out. The AI is taking too long — try again.";
        } else if (raw.contains("Unable to resolve host")) {
            msg = "Network error: Unable to reach server IP. Check your Wi-Fi.";
        } else {
            msg = "Network error: " + raw;
        }
        Log.e(TAG, "Network failure: " + raw);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // ── Metadata fetcher ─────────────────────────────────────────────────────
    private void fetchVideoMetadata(String videoId) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String oEmbedUrl = "https://www.youtube.com/oembed?format=json&url="
                        + YOUTUBE_BASE + "/watch?v=" + videoId;
                connection = (HttpURLConnection) new URL(oEmbedUrl).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setRequestMethod("GET");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        sb.append(line);
                    reader.close();

                    JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    String title = json.has("title") ? json.get("title").getAsString() : null;
                    if (!TextUtils.isEmpty(title)) {
                        currentVideoTitle = title;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not fetch video metadata: " + e.getMessage());
            } finally {
                if (connection != null)
                    connection.disconnect();
            }
        });
    }

    // ── Display Bottom Sheet ──────────────────────────────────────────────────
    private void displayContent(String titleText, String contentHtml) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_summary_bottom_sheet, null);

        TextView tvTitle = sheet.findViewById(R.id.tvSummaryTitle);
        TextView tvContent = sheet.findViewById(R.id.tvSummaryContent);

        if (tvTitle != null)
            tvTitle.setText(titleText);
        if (tvContent != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                tvContent.setText(android.text.Html.fromHtml(contentHtml,
                        android.text.Html.FROM_HTML_MODE_LEGACY));
            } else {
                // noinspection deprecation
                tvContent.setText(android.text.Html.fromHtml(contentHtml));
            }
        }

        dialog.setContentView(sheet);

        // Transparent parent for rounded corners
        View parent = (View) sheet.getParent();
        if (parent != null)
            parent.setBackgroundColor(Color.TRANSPARENT);

        dialog.show();
    }

    // ── Progress Dialog ───────────────────────────────────────────────────────
    private void showLoadingDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new Dialog(this);
            progressDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            progressDialog.setCancelable(false);

            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int pad = dpToPx(24);
            layout.setPadding(pad, pad, pad, pad);

            android.widget.ProgressBar pb = new android.widget.ProgressBar(this);
            pb.setIndeterminate(true);

            progressMessageView = new android.widget.TextView(this);
            progressMessageView.setTextColor(getResources().getColor(R.color.text_primary));
            progressMessageView.setTextSize(15);
            progressMessageView.setPadding(dpToPx(16), 0, 0, 0);

            layout.addView(pb);
            layout.addView(progressMessageView);
            progressDialog.setContentView(layout);

            if (progressDialog.getWindow() != null) {
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(getResources().getColor(R.color.surface));
                shape.setCornerRadius(dpToPx(16));
                progressDialog.getWindow().setBackgroundDrawable(shape);
            }
        }

        if (progressMessageView != null)
            progressMessageView.setText(message);
        if (!progressDialog.isShowing())
            progressDialog.show();
    }

    private void dismissLoadingDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ── Button Lock / Unlock ─────────────────────────────────────────────────
    private void unlockActionButtons() {
        if (buttonsEnabled)
            return;
        buttonsEnabled = true;
        animateButtonAlpha(ALPHA_DISABLED, ALPHA_ENABLED);
        setButtonsLocked(false);
    }

    private void lockActionButtons() {
        if (!buttonsEnabled)
            return;
        buttonsEnabled = false;
        animateButtonAlpha(ALPHA_ENABLED, ALPHA_DISABLED);
        setButtonsLocked(true);
    }

    private void setButtonsLocked(boolean locked) {
        boolean enabled = !locked;
        btnSummarize.setEnabled(enabled);
        btnTranscript.setEnabled(enabled);
        btnStudyPlan.setEnabled(enabled);
        btnBriefNotes.setEnabled(enabled);
    }

    private void animateButtonAlpha(float from, float to) {
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(ALPHA_ANIM_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(anim -> {
            float alpha = (float) anim.getAnimatedValue();
            btnSummarize.setAlpha(alpha);
            btnTranscript.setAlpha(alpha);
            btnStudyPlan.setAlpha(alpha);
            btnBriefNotes.setAlpha(alpha);
        });
        animator.start();
    }

    // ── Elastic Touch Micro-Interaction ──────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private void attachElasticTouch(View view, Runnable onClick) {
        if (view == null)
            return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
                            .setDuration(PRESS_ANIM_MS)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(BOUNCE_SCALE).scaleY(BOUNCE_SCALE)
                            .setDuration(RELEASE_ANIM_MS / 2)
                            .setInterpolator(new OvershootInterpolator(3f))
                            .withEndAction(() -> v.animate().scaleX(NORMAL_SCALE).scaleY(NORMAL_SCALE)
                                    .setDuration(RELEASE_ANIM_MS / 2)
                                    .setInterpolator(new DecelerateInterpolator()).start())
                            .start();
                    if (v.isEnabled())
                        onClick.run();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(NORMAL_SCALE).scaleY(NORMAL_SCALE)
                            .setDuration(PRESS_ANIM_MS).start();
                    break;
            }
            return true;
        });
    }

    // ── YouTube ID Extraction ────────────────────────────────────────────────
    /**
     * Extracts the 11-character YouTube video ID from various URL formats:
     * - https://www.youtube.com/watch?v=VIDEO_ID
     * - https://youtu.be/VIDEO_ID
     * - https://www.youtube.com/shorts/VIDEO_ID
     * - https://www.youtube.com/embed/VIDEO_ID
     * - https://www.youtube.com/live/VIDEO_ID
     * - Raw 11-character ID
     */
    public static String extractVideoId(String youtubeUrl) {
        if (TextUtils.isEmpty(youtubeUrl))
            return null;

        String trimmed = youtubeUrl.trim();

        // Plain 11-char video ID
        if (trimmed.matches("[a-zA-Z0-9_-]{11}"))
            return trimmed;

        // Standard URL patterns
        String pattern = "(?<=watch\\?v=|/videos/|/embed/|/shorts/|/live/|youtu\\.be/)[a-zA-Z0-9_-]{11}";
        @SuppressWarnings("RegExpRedundantEscape")
        Matcher m = Pattern.compile(pattern).matcher(trimmed);
        if (m.find())
            return m.group();

        // Fallback
        m = Pattern.compile("(?:v=|/)([a-zA-Z0-9_-]{11})").matcher(trimmed);
        if (m.find())
            return m.group(1);

        return null;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
