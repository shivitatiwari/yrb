package com.apoorv.yrb.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.apoorv.yrb.MainActivity
import com.apoorv.yrb.data.HistoryEntry
import com.apoorv.yrb.data.HistoryStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeProcessId: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
            activeJob?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (activeJob?.isActive == true) return START_NOT_STICKY

        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_TITLE)?.ifBlank { "YouTube video" } ?: "YouTube video"
        val quality = intent.getIntExtra(EXTRA_QUALITY, 0)
        if (quality !in QualitySelector.supported) return START_NOT_STICKY

        val processId = "yrb-" + UUID.randomUUID().toString()
        activeProcessId = processId
        startForeground(PROGRESS_NOTIFICATION_ID, progressNotification(title, 0, null))

        activeJob = scope.launch {
            performDownload(url, title, quality, processId, startId)
        }
        return START_NOT_STICKY
    }

    private fun performDownload(
        url: String,
        title: String,
        quality: Int,
        processId: String,
        startId: Int
    ) {
        val historyId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val outputDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Yrb"
        )
        outputDir.mkdirs()

        try {
            val request = YoutubeDLRequest(url)
                .addOption("--no-playlist")
                .addOption("-f", QualitySelector.selector(quality))
                .addOption("--merge-output-format", "mp4")
                .addOption("--newline")
                .addOption("-o", File(outputDir, "%(title).180B [%(id)s].%(ext)s").absolutePath)
                .addOption("--print", "after_move:%(filepath)s")

            var lastPercent = -1
            var lastUpdateAt = 0L
            val response = YoutubeDL.getInstance().execute(
                request = request,
                processId = processId,
                callback = { progress, etaSeconds, _ ->
                    val percent = progress.coerceIn(0f, 100f).roundToInt()
                    val now = System.currentTimeMillis()
                    if (percent != lastPercent && (now - lastUpdateAt >= 400 || percent == 100)) {
                        lastPercent = percent
                        lastUpdateAt = now
                        notifySafely(
                            PROGRESS_NOTIFICATION_ID,
                            progressNotification(title, percent, etaSeconds)
                        )
                    }
                }
            )

            val printedPath = response.out
                .lineSequence()
                .map { it.trim() }
                .lastOrNull { it.startsWith(outputDir.absolutePath) }

            val file = printedPath?.let(::File)?.takeIf { it.exists() }
                ?: outputDir.listFiles()
                    ?.filter { it.isFile && it.lastModified() >= startedAt - 2_000 }
                    ?.maxByOrNull { it.lastModified() }

            if (file == null || !file.exists()) {
                error("yt-dlp completed but the downloaded file could not be located.")
            }

            MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf(mimeFor(file)),
                null
            )

            HistoryStore(this).add(
                HistoryEntry(
                    id = historyId,
                    title = title,
                    url = url,
                    quality = quality,
                    path = file.absolutePath,
                    status = "completed",
                    timestamp = System.currentTimeMillis()
                )
            )
            sendBroadcast(Intent(ACTION_HISTORY_CHANGED).setPackage(packageName))

            stopForeground(STOP_FOREGROUND_REMOVE)
            notifySafely(
                COMPLETE_NOTIFICATION_ID,
                completeNotification(title, quality, file)
            )
        } catch (t: Throwable) {
            HistoryStore(this).add(
                HistoryEntry(
                    id = historyId,
                    title = title,
                    url = url,
                    quality = quality,
                    path = null,
                    status = if (t is CancellationException) "cancelled" else "failed",
                    timestamp = System.currentTimeMillis(),
                    error = t.message
                )
            )
            sendBroadcast(Intent(ACTION_HISTORY_CHANGED).setPackage(packageName))
            stopForeground(STOP_FOREGROUND_REMOVE)

            if (t !is CancellationException) {
                notifySafely(
                    FAILED_NOTIFICATION_ID,
                    failedNotification(title, t.message ?: "Download failed")
                )
            }
        } finally {
            activeProcessId = null
            activeJob = null
            stopSelf(startId)
        }
    }

    private fun progressNotification(
        title: String,
        progress: Int,
        etaSeconds: Long?
    ): android.app.Notification {
        val cancelIntent = Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL)
        val cancelPending = PendingIntent.getService(
            this,
            10,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val subtitle = if (etaSeconds != null && etaSeconds >= 0) {
            progress.toString() + "% • about " + etaSeconds + "s remaining"
        } else {
            progress.toString() + "%"
        }

        return NotificationCompat.Builder(this, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .addAction(0, "Cancel", cancelPending)
            .build()
    }

    private fun completeNotification(
        title: String,
        quality: Int,
        file: File
    ): android.app.Notification {
        val uri = FileProvider.getUriForFile(this, packageName + ".files", file)
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val openPending = PendingIntent.getActivity(
            this,
            20,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val qualityLabel = if (quality == 2160) "4K" else quality.toString() + "p"
        return NotificationCompat.Builder(this, CHANNEL_RESULTS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(title + " • " + qualityLabel)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()
    }

    private fun failedNotification(title: String, message: String): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            30,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_RESULTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        val allowed =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (!allowed) return

        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (_: SecurityException) {
            // The user or system can revoke notification access between check and post.
        }
    }

    private fun mimeFor(file: File): String {
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "video/*"
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RESULTS,
                    "Download results",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        activeJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_HISTORY_CHANGED = "com.apoorv.yrb.HISTORY_CHANGED"
        private const val ACTION_CANCEL = "com.apoorv.yrb.CANCEL"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_QUALITY = "quality"
        private const val CHANNEL_DOWNLOADS = "downloads"
        private const val CHANNEL_RESULTS = "download_results"
        private const val PROGRESS_NOTIFICATION_ID = 1001
        private const val COMPLETE_NOTIFICATION_ID = 1002
        private const val FAILED_NOTIFICATION_ID = 1003

        fun createIntent(
            context: android.content.Context,
            url: String,
            title: String,
            quality: Int
        ): Intent {
            return Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_QUALITY, quality)
        }
    }
}
