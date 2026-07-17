package com.example.ailecturesummarizer;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ailecturesummarizer.adapter.ChatAdapter;
import com.example.ailecturesummarizer.api.RetrofitClient;
import com.example.ailecturesummarizer.model.ChatMessage;
import com.example.ailecturesummarizer.model.SummaryResponse;
import com.example.ailecturesummarizer.model.UrlRequest;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ChatbotActivity — Q&A screen for a YouTube lecture.
 *
 * How it works:
 *   1. User types a question about the lecture.
 *   2. The app calls POST /api/summary with the video URL.
 *   3. The returned AI-generated summary is displayed as the bot's answer.
 *
 * Note: The backend has no dedicated /api/chat endpoint.
 * Re-using /api/summary is the correct approach since it calls Gemini AI
 * to generate structured lecture notes from the transcript.
 *
 * Intent extras (required):
 *   EXTRA_VIDEO_URL   – the YouTube URL to query (must be a valid YouTube URL)
 *   EXTRA_VIDEO_TITLE – optional display title shown in the toolbar subtitle
 */
public class ChatbotActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URL   = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";

    private RecyclerView      rvChatMessages;
    private TextInputEditText etChatMessage;
    private ImageButton       btnSend;

    private ChatAdapter chatAdapter;
    private String      videoUrl = "";

    // Track the current in-flight API call to cancel on destroy
    private Call<SummaryResponse> currentCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.chatbotToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chat with Lecture");
        }

        // Read intent extras
        if (getIntent() != null) {
            videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);
            String title = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);
            if (getSupportActionBar() != null && !TextUtils.isEmpty(title)) {
                getSupportActionBar().setSubtitle(title);
            }
            if (videoUrl == null) videoUrl = "";
        }

        // Views
        rvChatMessages = findViewById(R.id.rvChatMessages);
        etChatMessage  = findViewById(R.id.etChatMessage);
        btnSend        = findViewById(R.id.btnSendChatMessage);

        // RecyclerView — stack from end so newest messages appear at bottom
        chatAdapter = new ChatAdapter();
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        rvChatMessages.setLayoutManager(llm);
        rvChatMessages.setAdapter(chatAdapter);

        btnSend.setOnClickListener(v -> handleSend());

        // Welcome message
        chatAdapter.addMessage(new ChatMessage(
                "Hello! Ask me anything about this lecture video.\n\n" +
                "I'll generate detailed lecture notes using AI.", ChatMessage.TYPE_BOT));
    }

    private void handleSend() {
        String question = etChatMessage.getText() != null
                ? etChatMessage.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(question)) {
            Toast.makeText(this, "Please type a message", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate we have a URL
        if (TextUtils.isEmpty(videoUrl)) {
            chatAdapter.addMessage(new ChatMessage(question, ChatMessage.TYPE_USER));
            chatAdapter.addMessage(new ChatMessage(
                    "⚠ No lecture video loaded. Please go back and enter a YouTube URL first.",
                    ChatMessage.TYPE_BOT));
            scrollToBottom();
            return;
        }

        // Show user message
        chatAdapter.addMessage(new ChatMessage(question, ChatMessage.TYPE_USER));
        etChatMessage.setText("");
        scrollToBottom();

        // Show typing indicator
        chatAdapter.addMessage(new ChatMessage("Thinking...", ChatMessage.TYPE_BOT));
        scrollToBottom();

        // Cancel any existing in-flight call
        if (currentCall != null) currentCall.cancel();

        // Call POST /api/summary — reuses the same Gemini AI summarization
        currentCall = RetrofitClient.getApiService().getSummary(new UrlRequest(videoUrl));
        currentCall.enqueue(new Callback<SummaryResponse>() {
            @Override
            public void onResponse(@NonNull Call<SummaryResponse> call,
                                   @NonNull Response<SummaryResponse> response) {
                removeTypingIndicator();

                if (response.isSuccessful() && response.body() != null) {
                    SummaryResponse body = response.body();
                    if (body.success && !TextUtils.isEmpty(body.summary)) {
                        chatAdapter.addMessage(new ChatMessage(body.summary, ChatMessage.TYPE_BOT));
                    } else {
                        String err = !TextUtils.isEmpty(body.message) ? body.message
                                : "Sorry, I couldn't generate notes for this video.";
                        chatAdapter.addMessage(new ChatMessage("⚠ " + err, ChatMessage.TYPE_BOT));
                    }
                } else {
                    chatAdapter.addMessage(new ChatMessage(
                            "⚠ Server error (HTTP " + response.code() + "). Please try again.",
                            ChatMessage.TYPE_BOT));
                }
                scrollToBottom();
            }

            @Override
            public void onFailure(@NonNull Call<SummaryResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return; // ignore canceled calls
                removeTypingIndicator();

                String raw = t.getMessage() != null ? t.getMessage() : t.toString();
                String msg;
                if (raw.contains("ECONNREFUSED") || raw.contains("Connection refused")) {
                    msg = "⚠ Cannot reach server. Is the Flask backend running?";
                } else if (raw.contains("timeout") || raw.contains("Timeout")) {
                    msg = "⚠ Request timed out. The AI is taking too long — try again.";
                } else {
                    msg = "⚠ Network error: " + raw;
                }
                chatAdapter.addMessage(new ChatMessage(msg, ChatMessage.TYPE_BOT));
                scrollToBottom();
            }
        });
    }

    private void removeTypingIndicator() {
        chatAdapter.removeLastMessage();
    }

    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            rvChatMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel any in-flight API call to prevent memory leaks
        if (currentCall != null) currentCall.cancel();
    }
}
