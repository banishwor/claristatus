package com.claristatus.app.data.model

/**
 * Defines the user's selected sub-interval for trimming in milliseconds.
 */
data class TrimRange(
    val startMs: Long = 0L,
    val endMs: Long = 0L
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)

    val durationSeconds: Float
        get() = durationMs / 1000.0f

    /**
     * Formats milliseconds into standard MM:SS.S display string.
     */
    fun formatTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val fraction = (ms % 1000) / 100
        return String.format("%02d:%02d.%d", minutes, seconds, fraction)
    }
}
