package com.example.ailecturesummarizer.model;

/**
 * Model representing a single chat message exchanged between the user and the AI bot.
 */
public class ChatMessage {

    public static final int TYPE_USER = 0;
    public static final int TYPE_BOT  = 1;

    private final String message;
    private final int type; // TYPE_USER or TYPE_BOT

    public ChatMessage(String message, int type) {
        this.message = message;
        this.type    = type;
    }

    public String getMessage() { return message; }
    public int    getType()    { return type; }
    public boolean isUser()    { return type == TYPE_USER; }
}
