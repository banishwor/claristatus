package com.claristatus.app.service

import com.claristatus.app.data.model.CompressionPreset
import com.claristatus.app.data.model.SplitSegment
import java.io.File

/**
 * Reactive state machine emitted by the video compression engine.
 */
sealed interface TranscodeState {

    /**
     * Engine is idle, ready to accept a new transcoding job.
     */
    data object Idle : TranscodeState

    /**
     * Transcoding is actively in progress.
     * @param progressPercentage integer between 0 and 100 for current part.
     * @param currentPart current part index being encoded (1-based).
     * @param totalParts total number of parts to encode.
     */
    data class Processing(
        val progressPercentage: Int,
        val currentPart: Int = 1,
        val totalParts: Int = 1
    ) : TranscodeState

    /**
     * Single clip transcoding completed successfully.
     */
    data class Success(
        val outputFile: File,
        val outputSizeBytes: Long,
        val durationMs: Long,
        val preset: CompressionPreset
    ) : TranscodeState {
        val outputSizeMb: Float
            get() = outputSizeBytes / (1024.0f * 1024.0f)
    }

    /**
     * Batch story transcoding completed successfully for all split parts.
     */
    data class BatchSuccess(
        val completedParts: List<SplitSegment>,
        val preset: CompressionPreset
    ) : TranscodeState {
        val totalSizeBytes: Long
            get() = completedParts.sumOf { it.outputFile?.length() ?: 0L }
        val totalSizeMb: Float
            get() = totalSizeBytes / (1024.0f * 1024.0f)
    }

    /**
     * Transcoding failed due to an error.
     */
    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : TranscodeState

    /**
     * Transcoding was cancelled by the user.
     */
    data object Cancelled : TranscodeState
}
