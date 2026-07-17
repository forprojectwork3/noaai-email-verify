package com.example.ailecturesummarizer.model;

/**
 * Response model for POST /api/summary
 *
 * Backend response (success):
 * {
 *   "success": true,
 *   "video_id": "dQw4w9WgXcQ",
 *   "summary": "## Lecture Notes\n\n- Point 1\n- Point 2..."
 * }
 *
 * Backend response (failure):
 * {
 *   "success": false,
 *   "video_id": "dQw4w9WgXcQ",
 *   "message": "error description"
 * }
 */
public class SummaryResponse {
    public boolean success;
    public String video_id;
    public String summary;
    public String message; // present on error responses
}