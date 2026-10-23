package com.claristatus.app.domain

import com.claristatus.app.data.model.SplitSegment
import com.claristatus.app.data.model.TrimRange

/**
 * Handles video duration limits and calculates consecutive 30-second split intervals for WhatsApp Status.
 */
object VideoSplitter {

    /**
     * Maximum allowed input video duration (4 minutes / 240 seconds).
     * Prevents thermal throttling, memory exhaustion, and WhatsApp spam.
     */
    const val MAX_SUPPORTED_DURATION_MS = 240_000L // 4 minutes

    /**
     * Standard WhatsApp status slice duration (30 seconds).
     */
    const val SEGMENT_DURATION_MS = 30_000L

    /**
     * Verifies if the source video duration is within the 4-minute limit.
     */
    fun isDurationSupported(durationMs: Long): Boolean {
        return durationMs in 1L..MAX_SUPPORTED_DURATION_MS
    }

    /**
     * Calculates consecutive segments within an arbitrary trimmed range.
     * E.g. trimmed range [10s..85s] with 30s slices -> [10..40s, 40..70s, 70..85s]
     */
    fun calculateSplitsForRange(
        range: TrimRange,
        segmentDurationMs: Long = SEGMENT_DURATION_MS
    ): List<SplitSegment> {
        val duration = range.durationMs
        if (duration <= 0L) return emptyList()

        val stepMs = segmentDurationMs.coerceAtLeast(5_000L)
        val segments = mutableListOf<SplitSegment>()
        var currentStart = range.startMs
        var partIndex = 1

        while (currentStart < range.endMs) {
            val currentEnd = minOf(currentStart + stepMs, range.endMs)
            segments.add(
                SplitSegment(
                    partIndex = partIndex,
                    totalParts = 0,
                    trimRange = TrimRange(startMs = currentStart, endMs = currentEnd)
                )
            )
            currentStart = currentEnd
            partIndex++
        }

        val totalCount = segments.size
        return segments.map { it.copy(totalParts = totalCount) }
    }

    /**
     * Calculates consecutive segments from a video's total duration.
     */
    fun calculateSplits(totalDurationMs: Long, segmentDurationMs: Long = SEGMENT_DURATION_MS): List<SplitSegment> {
        val effectiveDuration = totalDurationMs.coerceAtMost(MAX_SUPPORTED_DURATION_MS)
        return calculateSplitsForRange(TrimRange(0L, effectiveDuration), segmentDurationMs)
    }
}
