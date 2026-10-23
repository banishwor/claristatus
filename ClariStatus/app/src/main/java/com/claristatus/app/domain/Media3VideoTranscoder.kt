package com.claristatus.app.domain

import android.content.Context
import android.media.MediaCodecInfo
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultMuxer
import androidx.media3.transformer.VideoEncoderSettings
import com.claristatus.app.data.model.CompressionPreset
import com.claristatus.app.data.model.TrimRange
import com.claristatus.app.service.TranscodeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Clean-room, 100% offline video transcoding engine powered by AndroidX Media3 Transformer.
 * Uses device hardware MediaCodec for fast, battery-efficient H.264 encoding with WhatsApp Status optimizations.
 */
class Media3VideoTranscoder(private val context: Context) {

    private val _transcodeState = MutableStateFlow<TranscodeState>(TranscodeState.Idle)
    val transcodeState: StateFlow<TranscodeState> = _transcodeState.asStateFlow()

    private var activeTransformer: Transformer? = null
    private var progressPollingJob: Job? = null

    /**
     * Suspending transcode of a single segment on the Main Looper thread.
     * Guarantees safe execution with AndroidX Media3 Transformer.
     */
    suspend fun transcodeSegment(
        sourceUri: Uri,
        outputFile: File,
        trimRange: TrimRange,
        preset: CompressionPreset,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.Main) {
        cancelCurrentJob()

        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.parentFile?.mkdirs()

        suspendCancellableCoroutine { continuation ->
            // 1. Configure sub-second trimming
            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
            if (trimRange.startMs > 0) {
                clippingBuilder.setStartPositionMs(trimRange.startMs)
            }
            if (trimRange.endMs > trimRange.startMs) {
                clippingBuilder.setEndPositionMs(trimRange.endMs)
            }

            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setClippingConfiguration(clippingBuilder.build())
                .build()

            // 2. Configure aspect-ratio preserving scaling
            var effSrcWidth = sourceWidth
            var effSrcHeight = sourceHeight
            if (effSrcWidth <= 0 || effSrcHeight <= 0) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, sourceUri)
                    val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val r = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (r == 90 || r == 270) {
                        effSrcWidth = h
                        effSrcHeight = w
                    } else {
                        effSrcWidth = w
                        effSrcHeight = h
                    }
                } catch (e: Exception) {
                    Log.w("ClariStatus", "Fallback metadata extraction failed", e)
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }

            val (targetW, targetH) = if (effSrcWidth > 0 && effSrcHeight > 0) {
                TargetConfigResolver.calculateTargetDimensions(
                    srcWidth = effSrcWidth,
                    srcHeight = effSrcHeight,
                    maxEdge = preset.maxEdge
                )
            } else {
                Pair(preset.maxEdge, preset.maxEdge)
            }

            Log.i("ClariStatus", "Configuring transcode: source=(${effSrcWidth}x${effSrcHeight}), target=(${targetW}x${targetH}), maxEdge=${preset.maxEdge}, bitrate=${preset.targetBitrateBps}")

            val scaleEffect = Presentation.createForWidthAndHeight(
                targetW,
                targetH,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )

            val audioProcessor = SonicAudioProcessor().apply {
                setOutputSampleRateHz(44100)
            }

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(listOf(audioProcessor), listOf(scaleEffect)))
                .build()

            // 3. Configure hardware encoder constraints (AVC High Profile, VBR, 1s GOP cadence)
            val encoderSettings = VideoEncoderSettings.Builder()
                .setBitrate(preset.targetBitrateBps)
                .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                .setEncodingProfileLevel(
                    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                    MediaCodecInfo.CodecProfileLevel.AVCLevel41
                )
                .setiFrameIntervalSeconds(preset.iFrameIntervalSeconds)
                .build()

            val audioEncoderSettings = AudioEncoderSettings.Builder()
                .setBitrate(128_000)
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(encoderSettings)
                .setRequestedAudioEncoderSettings(audioEncoderSettings)
                .setEnableFallback(true)
                .build()

            // 4. Build Transformer instance
            val transformer = Transformer.Builder(context)
                .setMuxerFactory(DefaultMuxer.Factory())
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setPortraitEncodingEnabled(true)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Log.i("ClariStatus", "Transcode complete: ${outputFile.name} (raw size: ${outputFile.length()} bytes)")
                        stopProgressPolling()
                        activeTransformer = null

                        // Faststart post-processing: moves moov atom to the front for WhatsApp Status fast-pass
                        val fastStartSuccess = Mp4FastStart.process(outputFile)
                        Log.i("ClariStatus", "Mp4FastStart result: $fastStartSuccess, final size: ${outputFile.length()} bytes")

                        if (continuation.isActive) {
                            continuation.resume(Result.success(outputFile))
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e("ClariStatus", "Transcode export error: ${exportException.errorCodeName}", exportException)
                        stopProgressPolling()
                        activeTransformer = null
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(exportException))
                        }
                    }
                })
                .build()

            activeTransformer = transformer

            continuation.invokeOnCancellation {
                Log.w("ClariStatus", "Transcode cancelled via coroutine cancellation")
                transformer.cancel()
                stopProgressPolling()
                activeTransformer = null
            }

            // 5. Start progress polling on Main dispatcher
            val progressHolder = ProgressHolder()
            progressPollingJob = launch(Dispatchers.Main) {
                while (isActive) {
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val progress = progressHolder.progress.coerceIn(0, 100)
                        onProgress(progress)
                    }
                    delay(250L)
                }
            }

            // 6. Launch export on main looper thread
            try {
                Log.i("ClariStatus", "Starting Transformer export to: ${outputFile.absolutePath}")
                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (e: Exception) {
                Log.e("ClariStatus", "Failed to start Transformer export", e)
                stopProgressPolling()
                activeTransformer = null
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    /**
     * Initiates video transcoding and trimming in the background.
     */
    fun startTranscode(
        scope: CoroutineScope,
        sourceUri: Uri,
        outputFile: File,
        trimRange: TrimRange,
        preset: CompressionPreset,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0
    ) {
        cancelCurrentJob()

        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.parentFile?.mkdirs()

        _transcodeState.value = TranscodeState.Processing(progressPercentage = 0)

        // 1. Configure sub-second trimming
        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
        if (trimRange.startMs > 0) {
            clippingBuilder.setStartPositionMs(trimRange.startMs)
        }
        if (trimRange.endMs > trimRange.startMs) {
            clippingBuilder.setEndPositionMs(trimRange.endMs)
        }

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(clippingBuilder.build())
            .build()

        // 2. Configure aspect-ratio preserving scaling (never stretches, never letterboxes)
        var effSrcWidth = sourceWidth
        var effSrcHeight = sourceHeight
        if (effSrcWidth <= 0 || effSrcHeight <= 0) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, sourceUri)
                val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val r = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (r == 90 || r == 270) {
                    effSrcWidth = h
                    effSrcHeight = w
                } else {
                    effSrcWidth = w
                    effSrcHeight = h
                }
            } catch (e: Exception) {
                Log.w("ClariStatus", "Fallback metadata extraction failed in startTranscode", e)
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        val (targetW, targetH) = if (effSrcWidth > 0 && effSrcHeight > 0) {
            TargetConfigResolver.calculateTargetDimensions(
                srcWidth = effSrcWidth,
                srcHeight = effSrcHeight,
                maxEdge = preset.maxEdge
            )
        } else {
            Pair(preset.maxEdge, preset.maxEdge)
        }

        val scaleEffect = Presentation.createForWidthAndHeight(
            targetW,
            targetH,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )

        val audioProcessor = SonicAudioProcessor().apply {
            setOutputSampleRateHz(44100)
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(listOf(audioProcessor), listOf(scaleEffect)))
            .build()

        // 3. Configure hardware encoder constraints (AVC High Profile, VBR, 1s GOP cadence)
        val encoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(preset.targetBitrateBps)
            .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            .setEncodingProfileLevel(
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                MediaCodecInfo.CodecProfileLevel.AVCLevel41
            )
            .setiFrameIntervalSeconds(preset.iFrameIntervalSeconds)
            .build()

        val audioEncoderSettings = AudioEncoderSettings.Builder()
            .setBitrate(128_000)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(encoderSettings)
            .setRequestedAudioEncoderSettings(audioEncoderSettings)
            .setEnableFallback(true) // Graceful fallback if device hardware encoder lacks High Profile
            .build()

        // 4. Build Transformer instance
        val transformer = Transformer.Builder(context)
            .setMuxerFactory(DefaultMuxer.Factory())
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setPortraitEncodingEnabled(true)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgressPolling()
                    Mp4FastStart.process(outputFile)
                    val outputSizeBytes = outputFile.length()
                    _transcodeState.value = TranscodeState.Success(
                        outputFile = outputFile,
                        outputSizeBytes = outputSizeBytes,
                        durationMs = trimRange.durationMs,
                        preset = preset
                    )
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    stopProgressPolling()
                    _transcodeState.value = TranscodeState.Error(
                        message = exportException.message ?: "Video transcoding failed",
                        throwable = exportException
                    )
                }
            })
            .build()

        activeTransformer = transformer

        // 5. Start background progress ticker (every 250ms)
        startProgressPolling(scope, transformer)

        // 6. Launch export
        try {
            transformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (e: Exception) {
            stopProgressPolling()
            _transcodeState.value = TranscodeState.Error(
                message = e.message ?: "Failed to start transcoder",
                throwable = e
            )
        }
    }

    /**
     * Cancels any ongoing transcoding job and cleans up temporary resources.
     */
    fun cancelCurrentJob() {
        stopProgressPolling()
        activeTransformer?.cancel()
        activeTransformer = null
        if (_transcodeState.value is TranscodeState.Processing) {
            _transcodeState.value = TranscodeState.Cancelled
        }
    }

    /**
     * Resets state to Idle.
     */
    fun reset() {
        cancelCurrentJob()
        _transcodeState.value = TranscodeState.Idle
    }

    private fun startProgressPolling(scope: CoroutineScope, transformer: Transformer) {
        val progressHolder = ProgressHolder()
        progressPollingJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val progress = progressHolder.progress.coerceIn(0, 100)
                    if (_transcodeState.value is TranscodeState.Processing) {
                        _transcodeState.value = TranscodeState.Processing(progress)
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = null
    }
}
