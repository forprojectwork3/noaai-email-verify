package com.example.ailecturesummarizer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;

/**
 * AvatarHelper — Generates a circular initials-based avatar bitmap.
 *
 * Usage:
 *   Bitmap bmp = AvatarHelper.createInitialsBitmap("John Doe", 120);
 *   imageView.setImageBitmap(bmp);
 */
public final class AvatarHelper {

    /**
     * Palette of background colours that are pleasant on dark sidebars.
     * The colour is chosen deterministically from the user's name so it
     * stays consistent across sessions.
     */
    private static final int[] PALETTE = {
            0xFF5C6BC0, // indigo
            0xFF26A69A, // teal
            0xFFEF5350, // coral-red
            0xFF7E57C2, // deep purple
            0xFF26C6DA, // cyan
            0xFFFF7043, // deep orange
            0xFF66BB6A, // green
            0xFF8D6E63, // brown
    };

    private AvatarHelper() { /* utility class */ }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a square bitmap of {@code sizePx}×{@code sizePx} with a filled
     * coloured circle and up to two initials centred on it.
     *
     * @param name   Display name (e.g. "Jane Doe"). May be null/empty — falls back to "?".
     * @param sizePx Side length of the returned bitmap in pixels.
     * @return       Circular initials bitmap (ARGB_8888).
     */
    public static Bitmap createInitialsBitmap(String name, int sizePx) {
        String initials = extractInitials(name);
        int bgColor     = pickColor(name);

        Bitmap bmp    = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // ── Background circle ─────────────────────────────────────────────────
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(bgColor);
        float radius = sizePx / 2f;
        canvas.drawCircle(radius, radius, radius, bgPaint);

        // ── Text ──────────────────────────────────────────────────────────────
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(sizePx * 0.38f);   // ~38 % of diameter looks balanced
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Vertically centre using font metrics
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = radius - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(initials, radius, textY, textPaint);

        return bmp;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Extracts up to two uppercase initials from a display name. */
    static String extractInitials(String name) {
        if (TextUtils.isEmpty(name)) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        // First letter of first + first letter of last word
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    /** Picks a background colour deterministically based on the name string. */
    static int pickColor(String name) {
        if (TextUtils.isEmpty(name)) return PALETTE[0];
        int hash = 0;
        for (char c : name.toCharArray()) hash = 31 * hash + c;
        return PALETTE[Math.abs(hash) % PALETTE.length];
    }
}
