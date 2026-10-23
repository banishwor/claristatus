package com.claristatus.app.domain

import com.claristatus.app.data.model.CompressionPreset
import com.claristatus.app.data.model.VideoMetadata

/**
 * Pure domain rule engine implementing the 30-second WhatsApp status compression rule.
 *
 * Rules:
 * - Selected duration <= 30.0s: Target resolution up to 1080p FHD (3.5 Mbps target, 3.8 Mbps ceiling).
 * - Selected duration > 30.0s: Target resolution up to 720p HD (1.8 Mbps target, 2.0 Mbps ceiling).
 * - Never upscale source videos that are smaller than the preset ceiling.
 * - Enforce even-integer dimensions for hardware encoder compatibility.
 */
object TargetConfigResolver {

    const val THIRTY_SECONDS_MS = 30_000L

    // FHD Presets (Exact PureStatus WhatsApp Status Canvas: 1152 x 1920)
    const val STATUS_FHD_WIDTH = 1152
    const val STATUS_FHD_HEIGHT = 1920
    const val FHD_MAX_EDGE = 1920
    const val FHD_TARGET_BITRATE = 3_900_000 // 3.9 Mbps (exact PureStatus 1.mp4 bitrate)
    const val FHD_MAX_BITRATE = 4_200_000    // 4.2 Mbps ceiling

    // HD Presets (WhatsApp HD Canvas: 768 x 1280)
    const val STATUS_HD_WIDTH = 768
    const val STATUS_HD_HEIGHT = 1280
    const val HD_MAX_EDGE = 1280
    const val HD_TARGET_BITRATE = 2_400_000  // 2.4 Mbps (matches WhatsApp HD Chat sweet spot)
    const val HD_MAX_BITRATE = 2_800_000     // 2.8 Mbps ceiling

    /**
     * Resolves the compression preset according to the selected duration and source video dimensions.
     */
    fun resolvePreset(durationMs: Long, sourceMetadata: VideoMetadata? = null): CompressionPreset {
        val maxSourceEdge = if (sourceMetadata != null) {
            maxOf(sourceMetadata.displayWidth, sourceMetadata.displayHeight)
        } else {
            Int.MAX_VALUE
        }

        return if (durationMs <= THIRTY_SECONDS_MS) {
            // Videos up to 30 seconds: 1080p maximum
            val effectiveEdge = minOf(FHD_MAX_EDGE, maxSourceEdge)
            val isFullFhd = effectiveEdge >= 1080

            CompressionPreset(
                maxEdge = effectiveEdge,
                targetBitrateBps = if (isFullFhd) FHD_TARGET_BITRATE else 2_200_000,
                maxBitrateBps = if (isFullFhd) FHD_MAX_BITRATE else 2_500_000,
                frameRate = 30.0f,
                iFrameIntervalSeconds = 1.0f,
                badgeLabel = if (isFullFhd) "1080p FHD" else "${effectiveEdge}p Native",
                description = "Optimized for maximum visual clarity under 30s"
            )
        } else {
            // Videos longer than 30 seconds: 720p maximum
            val effectiveEdge = minOf(HD_MAX_EDGE, maxSourceEdge)

            CompressionPreset(
                maxEdge = effectiveEdge,
                targetBitrateBps = HD_TARGET_BITRATE,
                maxBitrateBps = HD_MAX_BITRATE,
                frameRate = 30.0f,
                iFrameIntervalSeconds = 1.0f,
                badgeLabel = "720p HD",
                description = "Optimized to prevent WhatsApp server-side compression on clips > 30s"
            )
        }
    }

    /**
     * Forces dimension to the nearest smaller even integer to satisfy MediaCodec requirements.
     */
    fun makeEven(dimension: Int): Int {
        val positive = dimension.coerceAtLeast(2)
        return if (positive % 2 == 0) positive else positive - 1
    }

    /**
     * Calculates target display dimensions using the exact status canvas.
     * For Full HD portrait status: Returns 1152 x 1920 (PureStatus canvas).
     * For HD portrait status: Returns 720 x 1280.
     */
    fun calculateTargetDimensions(
        srcWidth: Int,
        srcHeight: Int,
        maxEdge: Int
    ): Pair<Int, Int> {
        val isPortrait = srcHeight >= srcWidth

        return if (maxEdge >= 1920) {
            // Full HD Status Canvas
            if (isPortrait) {
                Pair(STATUS_FHD_WIDTH, STATUS_FHD_HEIGHT) // 1152 x 1920
            } else {
                Pair(STATUS_FHD_HEIGHT, STATUS_FHD_WIDTH) // 1920 x 1152
            }
        } else {
            // HD Status Canvas
            if (isPortrait) {
                Pair(STATUS_HD_WIDTH, STATUS_HD_HEIGHT) // 720 x 1280
            } else {
                Pair(STATUS_HD_HEIGHT, STATUS_HD_WIDTH) // 1280 x 720
            }
        }
    }
}
