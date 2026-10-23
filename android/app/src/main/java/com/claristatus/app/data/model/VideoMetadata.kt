package com.claristatus.app.data.model

import android.net.Uri

/**
 * Encapsulates source video properties extracted from MediaMetadataRetriever / MediaExtractor.
 */
data class VideoMetadata(
    val uri: Uri? = null,
    val durationMs: Long = 0L,
    val rawWidth: Int = 0,
    val rawHeight: Int = 0,
    val rotationDegrees: Int = 0,
    val bitrateBps: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val frameRate: Float = 30.0f,
    val mimeType: String? = null
) {
    /**
     * Display width accounting for video rotation metadata (e.g. 90 or 270 degrees on mobile cameras).
     */
    val displayWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) rawHeight else rawWidth

    /**
     * Display height accounting for video rotation metadata.
     */
    val displayHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) rawWidth else rawHeight

    /**
     * Aspect ratio (width / height).
     */
    val aspectRatio: Float
        get() = if (displayHeight > 0) displayWidth.toFloat() / displayHeight.toFloat() else 1.0f

    /**
     * Whether the video is in portrait orientation (height > width).
     */
    val isPortrait: Boolean
        get() = displayHeight > displayWidth
}
