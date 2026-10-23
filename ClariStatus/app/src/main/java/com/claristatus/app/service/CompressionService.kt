package com.claristatus.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.claristatus.app.ClariStatusApp
import com.claristatus.app.MainActivity
import com.claristatus.app.R
import com.claristatus.app.data.model.CompressionPreset
import com.claristatus.app.data.model.TrimRange
import com.claristatus.app.domain.Media3VideoTranscoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service ensuring uninterrupted background video transcoding.
 * Compliant with Android 14+ FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING.
 */
class CompressionService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.claristatus.app.action.START"
        const val ACTION_CANCEL = "com.claristatus.app.action.CANCEL"

        const val EXTRA_SOURCE_URI = "extra_source_uri"
        const val EXTRA_OUTPUT_PATH = "extra_output_path"
        const val EXTRA_START_MS = "extra_start_ms"
        const val EXTRA_END_MS = "extra_end_ms"
        const val EXTRA_MAX_EDGE = "extra_max_edge"
        const val EXTRA_TARGET_BITRATE = "extra_target_bitrate"
        const val EXTRA_MAX_BITRATE = "extra_max_bitrate"
        const val EXTRA_BADGE_LABEL = "extra_badge_label"
        const val EXTRA_SOURCE_WIDTH = "extra_source_width"
        const val EXTRA_SOURCE_HEIGHT = "extra_source_height"

        fun createStartIntent(
            context: Context,
            sourceUri: Uri,
            outputFile: File,
            trimRange: TrimRange,
            preset: CompressionPreset,
            sourceWidth: Int = 0,
            sourceHeight: Int = 0
        ): Intent {
            return Intent(context, CompressionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SOURCE_URI, sourceUri.toString())
                putExtra(EXTRA_OUTPUT_PATH, outputFile.absolutePath)
                putExtra(EXTRA_START_MS, trimRange.startMs)
                putExtra(EXTRA_END_MS, trimRange.endMs)
                putExtra(EXTRA_MAX_EDGE, preset.maxEdge)
                putExtra(EXTRA_TARGET_BITRATE, preset.targetBitrateBps)
                putExtra(EXTRA_MAX_BITRATE, preset.maxBitrateBps)
                putExtra(EXTRA_BADGE_LABEL, preset.badgeLabel)
                putExtra(EXTRA_SOURCE_WIDTH, sourceWidth)
                putExtra(EXTRA_SOURCE_HEIGHT, sourceHeight)
            }
        }

        fun createCancelIntent(context: Context): Intent {
            return Intent(context, CompressionService::class.java).apply {
                action = ACTION_CANCEL
            }
        }
    }

    inner class LocalBinder : Binder() {
        val service: CompressionService get() = this@CompressionService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var transcoder: Media3VideoTranscoder

    val transcodeState: StateFlow<TranscodeState>
        get() = transcoder.transcodeState

    override fun onCreate() {
        super.onCreate()
        transcoder = Media3VideoTranscoder(applicationContext)

        // Listen for progress updates to refresh notification and handle terminal states
        serviceScope.launch {
            transcoder.transcodeState.collect { state ->
                when (state) {
                    is TranscodeState.Processing -> {
                        updateNotification(state.progressPercentage)
                    }
                    is TranscodeState.Success,
                    is TranscodeState.BatchSuccess,
                    is TranscodeState.Error,
                    TranscodeState.Cancelled -> {
                        stopForegroundNotification()
                    }
                    TranscodeState.Idle -> {
                        // No-op
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val uriStr = intent.getStringExtra(EXTRA_SOURCE_URI) ?: return START_NOT_STICKY
                val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH) ?: return START_NOT_STICKY
                val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)
                val endMs = intent.getLongExtra(EXTRA_END_MS, 0L)
                val maxEdge = intent.getIntExtra(EXTRA_MAX_EDGE, 1920)
                val targetBitrate = intent.getIntExtra(EXTRA_TARGET_BITRATE, 3_500_000)
                val maxBitrate = intent.getIntExtra(EXTRA_MAX_BITRATE, 3_800_000)
                val badgeLabel = intent.getStringExtra(EXTRA_BADGE_LABEL) ?: "1080p FHD"
                val sourceWidth = intent.getIntExtra(EXTRA_SOURCE_WIDTH, 0)
                val sourceHeight = intent.getIntExtra(EXTRA_SOURCE_HEIGHT, 0)

                val preset = CompressionPreset(
                    maxEdge = maxEdge,
                    targetBitrateBps = targetBitrate,
                    maxBitrateBps = maxBitrate,
                    badgeLabel = badgeLabel,
                    description = "Status optimization"
                )

                startForegroundWithNotification()
                transcoder.startTranscode(
                    scope = serviceScope,
                    sourceUri = Uri.parse(uriStr),
                    outputFile = File(outputPath),
                    trimRange = TrimRange(startMs, endMs),
                    preset = preset,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight
                )
            }
            ACTION_CANCEL -> {
                transcoder.cancelCurrentJob()
                stopForegroundNotification()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        transcoder.cancelCurrentJob()
        serviceScope.cancel()
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(progress: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(progress))
    }

    private fun stopForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(progress: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            createCancelIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ClariStatusApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Compressing video... $progress%")
            .setProgress(100, progress, false)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            )
            .build()
    }
}
