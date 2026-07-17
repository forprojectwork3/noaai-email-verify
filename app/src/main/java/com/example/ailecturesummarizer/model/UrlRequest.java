package com.example.ailecturesummarizer.model;

/**
 * Request body model for all API endpoints.
 *
 * Maps to: { "url": "<youtube_url>" }
 *
 * Used by:
 *   POST /api/transcript
 *   POST /api/summary
 *   POST /api/timestamps
 *   POST /api/analyze
 */
public class UrlRequest {
    // Gson serializes field name "url" → JSON key "url" — matches backend expectation
    public final String url;

    public UrlRequest(String url) {
        this.url = url;
    }
}