package com.claristatus.app.data.model

/**
 * Encapsulates codec parameters and resolution targets chosen by the 30-second rule engine.
 */
data class CompressionPreset(
    val maxEdge: Int,
    val targetBitrateBps: Int,
    val maxBitrateBps: Int,
    val frameRate: Float = 30.0f,
    val iFrameIntervalSeconds: Float = 1.0f,
    val badgeLabel: String,
    val description: String
) {
    /**
     * Estimates output file size in megabytes based on selected duration and target bitrate + 128k audio.
     */
    fun estimateOutputSizeMb(durationMs: Long): Float {
        val totalBitsPerSecond = targetBitrateBps + 128_000L
        val totalBytes = (totalBitsPerSecond * (durationMs / 1000.0f)) / 8.0f
        return totalBytes / (1024.0f * 1024.0f)
    }
}
