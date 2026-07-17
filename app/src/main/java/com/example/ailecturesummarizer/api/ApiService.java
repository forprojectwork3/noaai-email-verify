package com.example.ailecturesummarizer.api;

import com.example.ailecturesummarizer.model.AnalyzeResponse;
import com.example.ailecturesummarizer.model.SummaryResponse;
import com.example.ailecturesummarizer.model.TimestampResponse;
import com.example.ailecturesummarizer.model.TranscriptResponse;
import com.example.ailecturesummarizer.model.UrlRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Retrofit API interface matching all endpoints in backend/app.py.
 *
 * Base URL: http://<LAN_IP>:5000/  (set in RetrofitClient)
 *
 * Endpoints:
 *   GET  /               - Health check
 *   POST /api/transcript - Get full transcript text
 *   POST /api/summary    - Get AI-generated lecture notes/summary
 *   POST /api/timestamps - Get AI-generated topic timestamps
 *   POST /api/analyze    - Combined: summary + timestamps in one call
 */
public interface ApiService {

    /** Health check — confirms the Flask server is running. */
    @GET("/")
    Call<Void> healthCheck();

    /**
     * POST /api/transcript
     * Body: { "url": "<youtube_url>" }
     * Response: { "success": bool, "video_id": str, "transcript": str, "raw_transcript": [...] }
     */
    @POST("api/transcript")
    Call<TranscriptResponse> getTranscript(@Body UrlRequest request);

    /**
     * POST /api/summary
     * Body: { "url": "<youtube_url>" }
     * Response: { "success": bool, "video_id": str, "summary": str }
     */
    @POST("api/summary")
    Call<SummaryResponse> getSummary(@Body UrlRequest request);

    /**
     * POST /api/timestamps
     * Body: { "url": "<youtube_url>" }
     * Response: { "success": bool, "video_id": str, "timestamps": [{"time": str, "topic": str}] }
     */
    @POST("api/timestamps")
    Call<TimestampResponse> getTimestamps(@Body UrlRequest request);

    /**
     * POST /api/analyze
     * Body: { "url": "<youtube_url>" }
     * Response: { "success": bool, "video_id": str, "summary": str,
     *             "timestamps": [...], "summary_error": str, "timestamps_error": str }
     */
    @POST("api/analyze")
    Call<AnalyzeResponse> getAnalyze(@Body UrlRequest request);
}