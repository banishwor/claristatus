package com.claristatus.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claristatus.app.data.model.CompressionPreset
import com.claristatus.app.data.model.SplitSegment
import com.claristatus.app.data.model.TrimRange
import com.claristatus.app.data.model.VideoMetadata
import com.claristatus.app.domain.Media3VideoTranscoder
import com.claristatus.app.domain.ShareManager
import com.claristatus.app.domain.TargetConfigResolver
import com.claristatus.app.domain.ThumbnailExtractor
import com.claristatus.app.domain.VideoMetadataExtractor
import com.claristatus.app.domain.VideoSplitter
import com.claristatus.app.service.TranscodeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val metadataExtractor = VideoMetadataExtractor(application)
    private val transcoder = Media3VideoTranscoder(application)



    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _metadata = MutableStateFlow<VideoMetadata?>(null)
    val metadata: StateFlow<VideoMetadata?> = _metadata.asStateFlow()

    // Static blurred background frame
    private val _bgFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val bgFrameBitmap: StateFlow<Bitmap?> = _bgFrameBitmap.asStateFlow()

    // Split Duration (30s vs 60s)
    private val _splitDurationMs = MutableStateFlow(30_000L)
    val splitDurationMs: StateFlow<Long> = _splitDurationMs.asStateFlow()

    // Quality (1080p vs 720p)
    private val _qualityOption = MutableStateFlow("1080p")
    val qualityOption: StateFlow<String> = _qualityOption.asStateFlow()

    // Overall Trim Range across whole video
    private val _overallTrimRange = MutableStateFlow(TrimRange(0L, 0L))
    val overallTrimRange: StateFlow<TrimRange> = _overallTrimRange.asStateFlow()

    private val _splitSegments = MutableStateFlow<List<SplitSegment>>(emptyList())
    val splitSegments: StateFlow<List<SplitSegment>> = _splitSegments.asStateFlow()

    private val _selectedPartIndex = MutableStateFlow(1)
    val selectedPartIndex: StateFlow<Int> = _selectedPartIndex.asStateFlow()

    private val _currentPreset = MutableStateFlow(TargetConfigResolver.resolvePreset(0L))
    val currentPreset: StateFlow<CompressionPreset> = _currentPreset.asStateFlow()

    private val _transcodeState = MutableStateFlow<TranscodeState>(TranscodeState.Idle)
    val transcodeState: StateFlow<TranscodeState> = _transcodeState.asStateFlow()

    private val _isLoadingMetadata = MutableStateFlow(false)
    val isLoadingMetadata: StateFlow<Boolean> = _isLoadingMetadata.asStateFlow()

    private val _durationError = MutableStateFlow<String?>(null)
    val durationError: StateFlow<String?> = _durationError.asStateFlow()

    // Controls manual "Edit Video" trimmer bottom modal
    private val _showTrimEditor = MutableStateFlow(false)
    val showTrimEditor: StateFlow<Boolean> = _showTrimEditor.asStateFlow()

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            _isLoadingMetadata.value = true
            _durationError.value = null
            val result = metadataExtractor.extract(uri)
            _isLoadingMetadata.value = false

            result.onSuccess { meta ->
                if (!VideoSplitter.isDurationSupported(meta.durationMs)) {
                    val durationMin = meta.durationMs / 60_000.0f
                    _durationError.value = "Selected video is ${"%.1f".format(durationMin)} mins long. Videos longer than 4 minutes are not supported for Status. Please select a shorter video."
                    _selectedUri.value = null
                    _metadata.value = null
                    return@onSuccess
                }

                _selectedUri.value = uri
                _metadata.value = meta
                _selectedPartIndex.value = 1
                _overallTrimRange.value = TrimRange(0L, meta.durationMs)
                android.util.Log.i("ClariStatus", "Video picked: ${meta.displayWidth}x${meta.displayHeight}, raw=${meta.rawWidth}x${meta.rawHeight}, rotation=${meta.rotationDegrees}, duration=${meta.durationMs}ms")

                // 1. Extract static background frame for blurred backdrop
                launch {
                    val frame = ThumbnailExtractor.extractFrame(
                        context = getApplication(),
                        videoUri = uri,
                        timeMs = 0L,
                        targetWidth = 480,
                        targetHeight = 640
                    )
                    _bgFrameBitmap.value = frame
                }

                // 2. Calculate initial split segments
                recalculateSplits(uri, _overallTrimRange.value, _splitDurationMs.value)
            }
        }
    }

    fun onSplitDurationSelected(durationMs: Long) {
        _splitDurationMs.value = durationMs
        if (durationMs > 30_000L) {
            _qualityOption.value = "720p"
        }
        updatePreset()
        val uri = _selectedUri.value ?: return
        _selectedPartIndex.value = 1
        recalculateSplits(uri, _overallTrimRange.value, durationMs)
    }

    fun onQualityOptionSelected(quality: String) {
        if (_splitDurationMs.value > 30_000L && quality == "1080p") {
            _qualityOption.value = "720p"
        } else {
            _qualityOption.value = quality
        }
        updatePreset()
    }

    fun onOverallTrimRangeChanged(newRange: TrimRange) {
        _overallTrimRange.value = newRange
        val uri = _selectedUri.value ?: return
        _selectedPartIndex.value = 1
        recalculateSplits(uri, newRange, _splitDurationMs.value)
    }

    private fun recalculateSplits(uri: Uri, range: TrimRange, sliceMs: Long) {
        val rawSplits = VideoSplitter.calculateSplitsForRange(range, sliceMs)
        _splitSegments.value = rawSplits
        updatePreset()

        // Load thumbnails for each split part
        viewModelScope.launch {
            val updated = rawSplits.map { segment ->
                val thumb = ThumbnailExtractor.extractFrame(
                    context = getApplication(),
                    videoUri = uri,
                    timeMs = segment.trimRange.startMs
                )
                segment.copy(thumbnail = thumb)
            }
            _splitSegments.value = updated
        }
    }

    private fun updatePreset() {
        val meta = _metadata.value ?: return
        val isFhd = _qualityOption.value == "1080p"
        val maxSourceEdge = maxOf(meta.displayWidth, meta.displayHeight)

        _currentPreset.value = if (isFhd) {
            val edge = minOf(TargetConfigResolver.FHD_MAX_EDGE, maxSourceEdge)
            CompressionPreset(
                maxEdge = edge,
                targetBitrateBps = TargetConfigResolver.FHD_TARGET_BITRATE,
                maxBitrateBps = TargetConfigResolver.FHD_MAX_BITRATE,
                frameRate = 30.0f,
                iFrameIntervalSeconds = 1.0f,
                badgeLabel = "Full HD (1080p)",
                description = "Maximum clarity for WhatsApp Status"
            )
        } else {
            val edge = minOf(TargetConfigResolver.HD_MAX_EDGE, maxSourceEdge)
            CompressionPreset(
                maxEdge = edge,
                targetBitrateBps = TargetConfigResolver.HD_TARGET_BITRATE,
                maxBitrateBps = TargetConfigResolver.HD_MAX_BITRATE,
                frameRate = 30.0f,
                iFrameIntervalSeconds = 1.0f,
                badgeLabel = "HD (720p)",
                description = "Status-safe balanced compression"
            )
        }
    }

    fun selectPart(segment: SplitSegment) {
        _selectedPartIndex.value = segment.partIndex
    }

    fun toggleTrimEditor(open: Boolean) {
        _showTrimEditor.value = open
    }

    /**
     * Compresses all split parts in one go sequentially.
     */
    fun startBatchCompression(context: android.content.Context): Boolean {
        val uri = _selectedUri.value ?: return false
        val segments = _splitSegments.value
        if (segments.isEmpty()) return false

        val preset = _currentPreset.value
        val meta = _metadata.value
        val outputDir = File(context.cacheDir, "videos").apply { mkdirs() }

        viewModelScope.launch {
            val completedSegments = mutableListOf<SplitSegment>()

            for ((index, segment) in segments.withIndex()) {
                val currentPartNumber = index + 1
                _transcodeState.value = TranscodeState.Processing(
                    progressPercentage = 0,
                    currentPart = currentPartNumber,
                    totalParts = segments.size
                )

                val outputFile = File(outputDir, "Story_Part${currentPartNumber}_${System.currentTimeMillis()}.mp4")

                val result = transcoder.transcodeSegment(
                    sourceUri = uri,
                    outputFile = outputFile,
                    trimRange = segment.trimRange,
                    preset = preset,
                    sourceWidth = meta?.displayWidth ?: 0,
                    sourceHeight = meta?.displayHeight ?: 0,
                    onProgress = { progress ->
                        _transcodeState.value = TranscodeState.Processing(
                            progressPercentage = progress,
                            currentPart = currentPartNumber,
                            totalParts = segments.size
                        )
                    }
                )

                if (result.isSuccess) {
                    completedSegments.add(segment.copy(outputFile = outputFile, isCompleted = true))
                } else {
                    val ex = result.exceptionOrNull()
                    val errorMsg = ex?.localizedMessage ?: "Encoding failed"
                    _transcodeState.value = TranscodeState.Error("Part $currentPartNumber failed: $errorMsg", ex)
                    return@launch
                }
            }

            // All parts finished successfully!
            _transcodeState.value = TranscodeState.BatchSuccess(
                completedParts = completedSegments,
                preset = preset
            )
        }

        return true
    }

    fun cancelCompression() {
        transcoder.cancelCurrentJob()
        _transcodeState.value = TranscodeState.Idle
    }



    fun shareSinglePart(context: android.content.Context, file: File) {
        ShareManager.shareVideo(context, file, targetWhatsAppDirectly = true)
    }

    fun shareAllParts(context: android.content.Context, files: List<File>) {
        ShareManager.shareMultipleVideos(context, files, targetWhatsAppDirectly = true)
    }

    fun sendViaCompanion(
        context: android.content.Context,
        file: File,
        ip: String,
        phone: String,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            com.claristatus.app.domain.CompanionClient.setServerIp(context, ip)
            com.claristatus.app.domain.CompanionClient.setPhoneNumber(context, phone)
            val result = com.claristatus.app.domain.CompanionClient.sendVideoViaCompanion(context, file, ip, phone)
            onResult(result)
        }
    }

    fun dismissDurationError() {
        _durationError.value = null
    }

    fun reset() {
        cancelCompression()
        _selectedUri.value = null
        _metadata.value = null
        _bgFrameBitmap.value = null
        _overallTrimRange.value = TrimRange(0L, 0L)
        _splitSegments.value = emptyList()
        _selectedPartIndex.value = 1
        _transcodeState.value = TranscodeState.Idle
        _durationError.value = null
        _showTrimEditor.value = false
    }
}
