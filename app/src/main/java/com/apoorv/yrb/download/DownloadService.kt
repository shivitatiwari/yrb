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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
        val extractorArgs = intent.getStringExtra(EXTRA_EXTRACTOR_ARGS)
        val forceIpv4 = intent.getBooleanExtra(EXTRA_FORCE_IPV4, false)
        val estimatedBytes = intent.getLongExtra(EXTRA_ESTIMATED_BYTES, -1L).takeIf { it > 0L }

        if (quality !in QualitySelector.supported) return START_NOT_STICKY

        val processId = "yrb-" + jobId
        activeProcessId = processId
        activeHistoryId = jobId

        HistoryStore(this).update(jobId) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                stage = "Starting",
                progress = 0f,
                speedBytesPerSecond = 0L,
                etaSeconds = null
            )
        }
        broadcastHistoryChange(jobId)

        startForeground(
            PROGRESS_NOTIFICATION_ID,
            progressNotification(
                jobId = jobId,
                title = title,
                progress = 0,
                speed = 0L,
                etaSeconds = null,
                determinate = estimatedBytes != null
            )
        )

        activeJob = scope.launch {
            performDownload(
                jobId = jobId,
                url = url,
                title = title,
                quality = quality,
                selector = selector,
                extractorArgs = extractorArgs,
                forceIpv4 = forceIpv4,
                estimatedBytes = estimatedBytes,
                processId = processId,
                startId = startId
            )
        }
        return START_NOT_STICKY
    }

    private suspend fun performDownload(
        jobId: String,
        url: String,
        title: String,
        quality: Int,
        selector: String,
        extractorArgs: String?,
        forceIpv4: Boolean,
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
        var monitorJob: Job? = null

        val rawPercent = AtomicReference<Float?>(null)
        val rawSpeed = AtomicLong(-1L)
        val rawEta = AtomicLong(-1L)
        val currentStage = AtomicReference("Downloading")

        try {
            HistoryStore(this).update(jobId) {
                it.copy(stage = "Downloading")
            }
            broadcastHistoryChange(jobId)

            var previousBytes = directoryBytes(jobDir)
            var previousAt = System.currentTimeMillis()
            var maxProgress = 0f

            monitorJob = scope.launch {
                while (true) {
                    delay(500L)

                    val bytes = directoryBytes(jobDir)
                    val now = System.currentTimeMillis()
                    val elapsedMs = (now - previousAt).coerceAtLeast(1L)
                    val sampledSpeed = if (bytes >= previousBytes) {
                        ((bytes - previousBytes) * 1000L) / elapsedMs
                    } else {
                        0L
                    }

                    val stage = currentStage.get()
                    val parsedSpeed = rawSpeed.get().takeIf { it > 0L }
                    val speed = if (stage == "Merging" || stage == "Finalizing") {
                        0L
                    } else {
                        parsedSpeed ?: sampledSpeed
                    }

                    val estimatedProgress = estimatedBytes
                        ?.takeIf { it > 0L }
                        ?.let {
                            ((bytes.toDouble() / it.toDouble()) * 100.0)
                                .toFloat()
                                .coerceIn(0f, 99f)
                        }

                    val parsedProgress = rawPercent.get()?.coerceIn(0f, 99f)
                    val candidateProgress = estimatedProgress ?: parsedProgress ?: maxProgress
                    maxProgress = max(maxProgress, candidateProgress)

                    val eta = when {
                        stage == "Merging" || stage == "Finalizing" -> null
                        estimatedBytes != null &&
                            estimatedBytes > bytes &&
                            speed > 0L ->
                            ((estimatedBytes - bytes) / speed).coerceAtLeast(0L)
                        rawEta.get() >= 0L -> rawEta.get()
                        else -> null
                    }

                    HistoryStore(this@DownloadService).update(jobId) {
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
                            jobId = jobId,
                            title = title,
                            progress = maxProgress.roundToInt(),
                            speed = speed,
                            etaSeconds = eta,
                            determinate = estimatedBytes != null || parsedProgress != null
                        )
                    )

                    previousBytes = bytes
                    previousAt = now
                }
            }

            val request = YoutubeDLRequest(url)
                .addOption("--no-playlist")
                .addOption("-f", selector)
                .addOption("--merge-output-format", "mp4/mkv")
                .addOption("--newline")
                .addOption("--concurrent-fragments", "4")
                .addOption(
                    "--progress-template",
                    "download:[download] %(progress._percent_str)s of %(progress._total_bytes_str)s at %(progress._speed_str)s ETA %(progress._eta_str)s"
                )
                .addOption(
                    "-o",
                    File(jobDir, "%(title).180B [%(id)s].%(ext)s").absolutePath
                )
                .addOption("--print", "after_move:%(filepath)s")

            extractorArgs?.let {
                request.addOption("--extractor-args", it)
            }
            if (forceIpv4) {
                request.addOption("--force-ipv4")
            }

            val response = YoutubeDL.getInstance().execute(
                request = request,
                processId = processId,
                callback = { callbackProgress, callbackEta, line ->
                    ProgressLineParser.percent(line)
                        ?.let { rawPercent.set(it) }
                        ?: callbackProgress
                            .takeIf { it >= 0f }
                            ?.let { rawPercent.set(it) }

                    ProgressLineParser.speedBytesPerSecond(line)
                        ?.let { rawSpeed.set(it) }

                    ProgressLineParser.etaSeconds(line)
                        ?.let { rawEta.set(it) }
                        ?: callbackEta
                            .takeIf { it >= 0L }
                            ?.let { rawEta.set(it) }

                    if (ProgressLineParser.isMerging(line)) {
                        currentStage.set("Merging")
                        rawSpeed.set(-1L)
                        rawEta.set(-1L)
                    } else if (line?.contains("[download]", ignoreCase = true) == true) {
                        currentStage.set("Downloading")
                    }
                }
            )

            currentStage.set("Finalizing")
            rawSpeed.set(-1L)
            rawEta.set(-1L)

            HistoryStore(this).update(jobId) {
                it.copy(
                    stage = "Finalizing",
                    progress = max(it.progress, 99f),
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
            val alreadyCancelled =
                HistoryStore(this).find(jobId)?.status == DownloadStatus.CANCELLED
            val cancelled =
                t is CancellationException ||
                    t is YoutubeDL.CanceledException ||
                    alreadyCancelled
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
            monitorJob?.cancel()

            if (
                completed ||
                HistoryStore(this).find(jobId)?.status != DownloadStatus.DOWNLOADING
            ) {
                runCatching { jobDir.deleteRecursively() }
                if (partialRoot.listFiles().isNullOrEmpty()) {
                    runCatching { partialRoot.delete() }
                }
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
        etaSeconds: Long?,
        determinate: Boolean
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
            if (determinate) {
                append(progress.coerceIn(0, 99))
                append("%")
            } else {
                append("Downloading")
            }

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
            .setProgress(
                100,
                progress.coerceIn(0, 99),
                !determinate
            )
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

    private fun failedNotification(
        title: String,
        message: String
    ): android.app.Notification {
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

    private fun notifySafely(
        id: Int,
        notification: android.app.Notification
    ) {
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
            // Notification access can be revoked between check and post.
        }
    }

    private fun mimeFor(file: File): String =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "video/*"

    private fun formatEta(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val remainingSeconds = safe % 60L

        return when {
            hours > 0L ->
                hours.toString() + "h " + minutes.toString() + "m"
            minutes > 0L ->
                minutes.toString() + "m " + remainingSeconds.toString() + "s"
            else ->
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
        private const val EXTRA_EXTRACTOR_ARGS = "extractor_args"
        private const val EXTRA_FORCE_IPV4 = "force_ipv4"
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
                .putExtra(EXTRA_EXTRACTOR_ARGS, quality.extractorArgs)
                .putExtra(EXTRA_FORCE_IPV4, quality.forceIpv4)
                .putExtra(EXTRA_ESTIMATED_BYTES, quality.estimatedBytes ?: -1L)

        fun cancelIntent(context: android.content.Context): Intent =
            Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL)
    }
}
