package com.claristatus.app.data.model

import java.io.File

/**
 * Processing mode selected by the user for longer videos (> 30 seconds).
 */
enum class ProcessingMode(val title: String, val subtitle: String, val badge: String) {
    /**
     * Slices the video into consecutive 30-second clips, each in 1080p FHD.
     */
    SPLIT_STORIES(
        title = "Split into 30s Stories",
        subtitle = "Auto-chops into consecutive parts. Every part stays in crystal-clear 1080p FHD.",
        badge = "1080p FHD • Multi-Part"
    ),

    /**
     * Pick any single 30-second interval in 1080p FHD.
     */
    CUSTOM_TRIM_30S(
        title = "Custom 30s Trim",
        subtitle = "Scrub and pick the best 30-second highlight to export in 1080p FHD.",
        badge = "1080p FHD • Single Clip"
    ),

    /**
     * Single extended clip up to 60 seconds in 720p HD.
     */
    SINGLE_60S_HD(
        title = "Single 60s Clip",
        subtitle = "Export an extended clip up to 60 seconds in 720p HD status-safe mode.",
        badge = "720p HD • Up to 60s"
    )
}

/**
 * Represents a discrete segment when splitting a longer video into consecutive stories.
 */
data class SplitSegment(
    val partIndex: Int,
    val totalParts: Int,
    val trimRange: TrimRange,
    var outputFile: File? = null,
    var isCompleted: Boolean = false,
    val thumbnail: android.graphics.Bitmap? = null
) {
    val displayLabel: String
        get() = "Part $partIndex of $totalParts (${"%.1f".format(trimRange.durationSeconds)}s)"
}
