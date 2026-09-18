package com.apoorv.yrb.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.apoorv.yrb.MainActivity
import com.apoorv.yrb.data.DownloadStatus
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
import kotlin.math.max
import kotlin.math.roundToInt

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeProcessId: String? = null
    private var activeHistoryId: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
            activeJob?.cancel()
            activeHistoryId?.let { id ->
                HistoryStore(this).update(id) {
                    it.copy(
                        status = DownloadStatus.CANCELLED,
                        stage = "Cancelled",
                        speedBytesPerSecond = 0L,
                        etaSeconds = null
                    )
                }
                broadcastHistoryChange(id)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (activeJob?.isActive == true) return START_NOT_STICKY

        val jobId = intent?.getStringExtra(EXTRA_JOB_ID) ?: return START_NOT_STICKY
        val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_TITLE)?.ifBlank { "YouTube video" } ?: "YouTube video"
        val quality = intent.getIntExtra(EXTRA_QUALITY, 0)
        val selector = intent.getStringExtra(EXTRA_SELECTOR) ?: return START_NOT_STICKY
        val estimatedBytes = intent.getLongExtra(EXTRA_ESTIMATED_BYTES, -1L).takeIf { it > 0L }

        if (quality !in QualitySelector.supported) return START_NOT_STICKY

        val processId = "yrb-" + jobId
        activeProcessId = processId
        activeHistoryId = jobId

        HistoryStore(this).update(jobId) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                stage = "Preparing",
                progress = 0f,
                speedBytesPerSecond = 0L,
                etaSeconds = null
            )
        }
        broadcastHistoryChange(jobId)

        startForeground(
            PROGRESS_NOTIFICATION_ID,
            progressNotification(jobId, title, 0, 0L, null)
        )

        activeJob = scope.launch {
            performDownload(
                jobId = jobId,
                url = url,
                title = title,
                quality = quality,
                selector = selector,
                estimatedBytes = estimatedBytes,
                processId = processId,
                startId = startId
            )
        }
        return START_NOT_STICKY
    }

    private fun performDownload(
        jobId: String,
        url: String,
        title: String,
        quality: Int,
        selector: String,
        estimatedBytes: Long?,
        processId: String,
        startId: Int
    ) {
        val rootDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Yrb"
        )
        val partialRoot = File(rootDir, ".partial")
        val jobDir = File(partialRoot, jobId)
        rootDir.mkdirs()
        jobDir.mkdirs()

        var completed = false
        var maxProgress = 0f
        var lastHistoryUpdateAt = 0L
        var lastSampleAt = System.currentTimeMillis()
        var lastSampleBytes = directoryBytes(jobDir)

        try {
            HistoryStore(this).update(jobId) { it.copy(stage = "Refreshing extractor") }
            broadcastHistoryChange(jobId)
            YtDlpRuntime.ensureFresh(this)

            HistoryStore(this).update(jobId) { it.copy(stage = "Downloading") }
            broadcastHistoryChange(jobId)

            val request = YoutubeDLRequest(url)
                .addOption("--no-playlist")
                .addOption("-f", selector)
                .addOption("--merge-output-format", "mp4/mkv")
                .addOption("--newline")
                .addOption("--concurrent-fragments", "4")
                .addOption(
                    "-o",
                    File(jobDir, "%(title).180B [%(id)s].%(ext)s").absolutePath
                )
                .addOption("--print", "after_move:%(filepath)s")

            val response = YoutubeDL.getInstance().execute(
                request = request,
                processId = processId,
                callback = { callbackProgress, callbackEta, line ->
                    val now = System.currentTimeMillis()
                    if (now - lastHistoryUpdateAt < 450L && callbackProgress < 100f) {
                        return@execute
                    }

                    val bytes = directoryBytes(jobDir)
                    val elapsedMs = (now - lastSampleAt).coerceAtLeast(1L)
                    val sampledSpeed = if (bytes >= lastSampleBytes) {
                        ((bytes - lastSampleBytes) * 1000L) / elapsedMs
                    } else {
                        0L
                    }
                    val parsedSpeed = ProgressLineParser.speedBytesPerSecond(line)
                    val speed = parsedSpeed ?: sampledSpeed

                    val computedProgress = if (estimatedBytes != null && estimatedBytes > 0L) {
                        ((bytes.toDouble() / estimatedBytes.toDouble()) * 100.0)
                            .toFloat()
                            .coerceIn(0f, 99f)
                    } else {
                        callbackProgress.coerceIn(0f, 99f)
                    }
                    maxProgress = max(maxProgress, computedProgress)

                    val eta = when {
                        estimatedBytes != null && speed > 0L && bytes < estimatedBytes ->
                            ((estimatedBytes - bytes) / speed).coerceAtLeast(0L)
                        callbackEta >= 0L -> callbackEta
                        else -> null
                    }

                    val stage = if (
                        line?.contains("[Merger]", ignoreCase = true) == true ||
                        line?.contains("Merging formats", ignoreCase = true) == true
                    ) {
                        "Merging"
                    } else {
                        "Downloading"
                    }

                    HistoryStore(this).update(jobId) {
                        it.copy(
                            status = DownloadStatus.DOWNLOADING,
                            stage = stage,
                            progress = maxProgress,
                            speedBytesPerSecond = speed,
                            etaSeconds = eta,
                            downloadedBytes = bytes
                        )
                    }
                    broadcastHistoryChange(jobId)
                    notifySafely(
                        PROGRESS_NOTIFICATION_ID,
                        progressNotification(
                            jobId,
                            title,
                            maxProgress.roundToInt(),
                            speed,
                            eta
                        )
                    )

                    lastHistoryUpdateAt = now
                    lastSampleAt = now
                    lastSampleBytes = bytes
                }
            )

            HistoryStore(this).update(jobId) {
                it.copy(
                    stage = "Finalizing",
                    progress = max(maxProgress, 99f),
                    speedBytesPerSecond = 0L,
                    etaSeconds = null
                )
            }
            broadcastHistoryChange(jobId)

            val printedPath = response.out
                .lineSequence()
                .map { it.trim() }
                .lastOrNull { it.startsWith(jobDir.absolutePath) }

            val sourceFile = printedPath?.let(::File)?.takeIf { it.exists() }
                ?: jobDir.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(".part") }
                    ?.maxByOrNull { it.lastModified() }

            if (sourceFile == null || !sourceFile.exists()) {
                error("yt-dlp finished but the completed media file could not be located.")
            }

            val finalFile = File(rootDir, sourceFile.name)
            if (finalFile.exists()) finalFile.delete()

            val moved = sourceFile.renameTo(finalFile)
            if (!moved) {
                sourceFile.copyTo(finalFile, overwrite = true)
                sourceFile.delete()
            }

            if (!finalFile.exists() || finalFile.length() <= 0L) {
                error("The completed file could not be moved into Downloads/Yrb.")
            }

            MediaScannerConnection.scanFile(
                this,
                arrayOf(finalFile.absolutePath),
                arrayOf(mimeFor(finalFile)),
                null
            )

            HistoryStore(this).update(jobId) {
                it.copy(
                    filePath = finalFile.absolutePath,
                    status = DownloadStatus.COMPLETED,
                    stage = "Completed",
                    progress = 100f,
                    speedBytesPerSecond = 0L,
                    etaSeconds = 0L,
                    downloadedBytes = finalFile.length(),
                    error = null
                )
            }
            broadcastHistoryChange(jobId)
            completed = true

            stopForeground(STOP_FOREGROUND_REMOVE)
            notifySafely(
                COMPLETE_NOTIFICATION_ID,
                completeNotification(title, quality, finalFile)
            )
        } catch (t: Throwable) {
            val cancelled = t is CancellationException
            val humanError = if (cancelled) null else ProgressLineParser.humanError(t.message)

            HistoryStore(this).update(jobId) {
                it.copy(
                    status = if (cancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED,
                    stage = if (cancelled) "Cancelled" else "Failed",
                    speedBytesPerSecond = 0L,
                    etaSeconds = null,
                    error = humanError
                )
            }
            broadcastHistoryChange(jobId)
            stopForeground(STOP_FOREGROUND_REMOVE)

            if (!cancelled) {
                notifySafely(
                    FAILED_NOTIFICATION_ID,
                    failedNotification(title, humanError ?: "Download failed")
                )
            }
        } finally {
            if (completed || HistoryStore(this).find(jobId)?.status != DownloadStatus.DOWNLOADING) {
                runCatching { jobDir.deleteRecursively() }
                if (partialRoot.listFiles().isNullOrEmpty()) runCatching { partialRoot.delete() }
            }
            activeProcessId = null
            activeHistoryId = null
            activeJob = null
            stopSelf(startId)
        }
    }

    private fun directoryBytes(dir: File): Long =
        runCatching {
            dir.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        }.getOrDefault(0L)

    private fun progressNotification(
        jobId: String,
        title: String,
        progress: Int,
        speed: Long,
        etaSeconds: Long?
    ): android.app.Notification {
        val cancelIntent = Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL)
        val cancelPending = PendingIntent.getService(
            this,
            10,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_JOB_ID, jobId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPending = PendingIntent.getActivity(
            this,
            11,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val details = buildString {
            append(progress)
            append("%")
            if (speed > 0L) {
                append(" • ")
                append(FileSizeFormatter.format(speed))
                append("/s")
            }
            if (etaSeconds != null && etaSeconds >= 0L) {
                append(" • ")
                append(formatEta(etaSeconds))
                append(" left")
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(details)
            .setContentIntent(openPending)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), false)
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
            // Notification access can be revoked between the permission check and posting.
        }
    }

    private fun mimeFor(file: File): String =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "video/*"

    private fun formatEta(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val minutes = safe / 60L
        val remainingSeconds = safe % 60L
        return if (minutes > 0L) {
            minutes.toString() + "m " + remainingSeconds.toString() + "s"
        } else {
            remainingSeconds.toString() + "s"
        }
    }

    private fun broadcastHistoryChange(jobId: String) {
        sendBroadcast(
            Intent(ACTION_HISTORY_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_CHANGED_JOB_ID, jobId)
        )
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
        const val EXTRA_CHANGED_JOB_ID = "changed_job_id"
        private const val ACTION_CANCEL = "com.apoorv.yrb.CANCEL"
        private const val EXTRA_JOB_ID = "job_id"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_QUALITY = "quality"
        private const val EXTRA_SELECTOR = "selector"
        private const val EXTRA_ESTIMATED_BYTES = "estimated_bytes"
        private const val CHANNEL_DOWNLOADS = "downloads"
        private const val CHANNEL_RESULTS = "download_results"
        private const val PROGRESS_NOTIFICATION_ID = 1001
        private const val COMPLETE_NOTIFICATION_ID = 1002
        private const val FAILED_NOTIFICATION_ID = 1003

        fun createIntent(
            context: android.content.Context,
            jobId: String,
            url: String,
            title: String,
            quality: QualityOption
        ): Intent =
            Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_JOB_ID, jobId)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_QUALITY, quality.height)
                .putExtra(EXTRA_SELECTOR, quality.selector)
                .putExtra(EXTRA_ESTIMATED_BYTES, quality.estimatedBytes ?: -1L)

        fun cancelIntent(context: android.content.Context): Intent =
            Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL)
    }
}
