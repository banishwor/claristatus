package com.claristatus.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts lightweight video frame thumbnails for the story split carousel.
 */
object ThumbnailExtractor {

    suspend fun extractFrame(
        context: Context,
        videoUri: Uri,
        timeMs: Long,
        targetWidth: Int = 160,
        targetHeight: Int = 160
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val timeUs = timeMs * 1000L
            val rawBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            if (rawBitmap != null) {
                // Scale down bitmap to save memory
                val scaled = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
                if (scaled != rawBitmap) {
                    rawBitmap.recycle()
                }
                scaled
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}
