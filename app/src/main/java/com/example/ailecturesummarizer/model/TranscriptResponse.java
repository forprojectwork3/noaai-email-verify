package com.example.ailecturesummarizer.model;

/**
 * Response model for POST /api/transcript
 *
 * Backend response (success):
 * {
 *   "success": true,
 *   "video_id": "dQw4w9WgXcQ",
 *   "transcript": "full text of the lecture...",
 *   "raw_transcript": [{"text": "...", "start": 0.0, "duration": 5.0}, ...]
 * }
 *
 * Backend response (failure):
 * {
 *   "success": false,
 *   "message": "error description"
 * }
 */
public class TranscriptResponse {
    public boolean success;
    public String video_id;
    public String transcript;
    public String raw_transcript; // raw_transcript from backend is a list, kept as String for optional use
    public String message; // present on error responses
}