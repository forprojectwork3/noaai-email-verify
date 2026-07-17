package com.example.ailecturesummarizer.model;

import java.util.List;

/**
 * Response model for POST /api/analyze
 *
 * This endpoint runs both summary and timestamps in a single call.
 *
 * Backend response:
 * {
 *   "success": true/false,          -- true only if BOTH summary AND timestamps succeeded
 *   "video_id": "dQw4w9WgXcQ",
 *   "summary": "## Lecture Notes...", -- null if summary failed
 *   "timestamps": [...],             -- null if timestamps failed
 *   "summary_error": "error msg",    -- null if summary succeeded
 *   "timestamps_error": "error msg"  -- null if timestamps succeeded
 * }
 */
public class AnalyzeResponse {
    public boolean success;
    public String video_id;
    public String summary;
    public List<TimestampItem> timestamps;
    public String summary_error;
    public String timestamps_error;

    public static class TimestampItem {
        public String time;  // "MM:SS"
        public String topic;
    }
}