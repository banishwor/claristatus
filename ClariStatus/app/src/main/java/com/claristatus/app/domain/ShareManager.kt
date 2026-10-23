package com.claristatus.app.domain

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles exporting compressed status videos to public device storage and launching the Android Share Sheet.
 */
object ShareManager {

    /**
     * Copies the compressed MP4 file into the device's public Movies/ClariStatus gallery collection.
     */
    suspend fun saveToGallery(context: Context, videoFile: File, displayName: String = "Status_${System.currentTimeMillis()}"): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ClariStatus")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collectionUri, values) ?: return@withContext null

        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                videoFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            itemUri
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            null
        }
    }

    /**
     * Opens the native Android Share Sheet to share the video directly to WhatsApp or any compatible app.
     */
    fun shareVideo(context: Context, videoFile: File, targetWhatsAppDirectly: Boolean = true) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                videoFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (targetWhatsAppDirectly) {
                    setPackage("com.whatsapp")
                }
            }

            if (targetWhatsAppDirectly) {
                try {
                    context.startActivity(shareIntent)
                    return
                } catch (_: Exception) {
                    // Fallback to standard chooser if WhatsApp package is not installed
                    shareIntent.setPackage(null)
                }
            }

            val chooser = Intent.createChooser(shareIntent, "Share Status Video")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares multiple split story parts in sequential order directly to WhatsApp Status.
     */
    fun shareMultipleVideos(context: Context, videoFiles: List<File>, targetWhatsAppDirectly: Boolean = true) {
        if (videoFiles.isEmpty()) return

        if (videoFiles.size == 1) {
            shareVideo(context, videoFiles.first(), targetWhatsAppDirectly)
            return
        }

        try {
            val contentUris = ArrayList<Uri>()
            for (file in videoFiles) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                contentUris.add(uri)
            }

            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "video/mp4"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, contentUris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (targetWhatsAppDirectly) {
                    setPackage("com.whatsapp")
                }
            }

            if (targetWhatsAppDirectly) {
                try {
                    context.startActivity(shareIntent)
                    return
                } catch (_: Exception) {
                    shareIntent.setPackage(null)
                }
            }

            val chooser = Intent.createChooser(shareIntent, "Share Story to WhatsApp")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share stories: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
