package com.example.ailecturesummarizer.model;

import java.util.List;

/**
 * Response model for POST /api/timestamps
 *
 * Backend response (success):
 * {
 *   "success": true,
 *   "video_id": "dQw4w9WgXcQ",
 *   "timestamps": [
 *     { "time": "00:00", "topic": "Introduction" },
 *     { "time": "02:30", "topic": "Main Concept" },
 *     ...
 *   ]
 * }
 *
 * Backend response (failure):
 * {
 *   "success": false,
 *   "video_id": "dQw4w9WgXcQ",
 *   "message": "error description"
 * }
 *
 * Note: The backend generates timestamps using Gemini AI,
 * requesting JSON format: [{"time": "MM:SS", "topic": "..."}]
 */
public class TimestampResponse {
    public boolean success;
    public String video_id;
    public List<TimestampItem> timestamps;
    public String message; // present on error responses

    public static class TimestampItem {
        public String time;  // Format: "MM:SS" e.g. "00:00", "02:30"
        public String topic; // Short topic description
    }
}