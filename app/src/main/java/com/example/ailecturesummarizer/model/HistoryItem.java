package com.example.ailecturesummarizer.model;

/**
 * POJO representing a single history entry (chat session) in the sidebar.
 *
 * Fields:
 *  title   — display title of the chat (e.g. "New Chat Session" or video title)
 *  url     — associated YouTube URL, if any (may be empty for plain chats)
 *  chatId  — the Supabase `chats.id` UUID for this session (used for navigation/restore)
 */
public class HistoryItem {

    private final String title;
    private final String url;
    private final String chatId; // Supabase chat row UUID

    public HistoryItem(String title, String url, String chatId) {
        this.title  = title;
        this.url    = url;
        this.chatId = chatId;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    /**
     * Returns the Supabase chat ID (UUID) for this history entry.
     * Previously named getAction() — renamed to getChatId() for semantic clarity.
     */
    public String getChatId() {
        return chatId;
    }

    /**
     * Legacy alias for getChatId() — kept for any callers that used getAction().
     * @deprecated Use getChatId() instead.
     */
    @Deprecated
    public String getAction() {
        return chatId;
    }
}
