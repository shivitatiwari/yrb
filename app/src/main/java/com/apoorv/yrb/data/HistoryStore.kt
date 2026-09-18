package com.apoorv.yrb.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DownloadRecord(
    val id: String,
    val title: String,
    val url: String,
    val quality: Int,
    val audioLanguage: String? = null,
    val filePath: String? = null,
    val status: String,
    val stage: String = status,
    val progress: Float = 0f,
    val speedBytesPerSecond: Long = 0L,
    val etaSeconds: Long? = null,
    val estimatedBytes: Long? = null,
    val downloadedBytes: Long = 0L,
    val timestamp: Long,
    val error: String? = null
)

object DownloadStatus {
    const val QUEUED = "queued"
    const val DOWNLOADING = "downloading"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"

    fun isActive(value: String): Boolean =
        value == QUEUED || value == DOWNLOADING
}

class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "download_history.json")

    fun readAll(): List<DownloadRecord> = synchronized(FILE_LOCK) {
        readUnlocked()
    }

    fun find(id: String): DownloadRecord? = synchronized(FILE_LOCK) {
        readUnlocked().firstOrNull { it.id == id }
    }

    fun upsert(entry: DownloadRecord) = synchronized(FILE_LOCK) {
        val current = readUnlocked().toMutableList()
        current.removeAll { it.id == entry.id }
        current.add(0, entry)
        while (current.size > MAX_HISTORY) current.removeAt(current.lastIndex)
        writeUnlocked(current)
    }

    fun update(id: String, transform: (DownloadRecord) -> DownloadRecord): DownloadRecord? =
        synchronized(FILE_LOCK) {
            val current = readUnlocked().toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return null
            val updated = transform(current[index])
            current[index] = updated
            writeUnlocked(current)
            updated
        }

    fun clearFinished() = synchronized(FILE_LOCK) {
        val active = readUnlocked().filter { DownloadStatus.isActive(it.status) }
        writeUnlocked(active)
    }

    fun markActiveInterrupted() = synchronized(FILE_LOCK) {
        val current = readUnlocked()
        if (current.none { DownloadStatus.isActive(it.status) }) return
        writeUnlocked(
            current.map { item ->
                if (DownloadStatus.isActive(item.status)) {
                    item.copy(
                        status = DownloadStatus.FAILED,
                        stage = "Interrupted",
                        speedBytesPerSecond = 0L,
                        etaSeconds = null,
                        error = "The app process stopped before this download completed."
                    )
                } else {
                    item
                }
            }
        )
    }

    private fun readUnlocked(): List<DownloadRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        DownloadRecord(
                            id = item.getString("id"),
                            title = item.optString("title", "Video"),
                            url = item.optString("url"),
                            quality = item.optInt("quality"),
                            audioLanguage = item.optString("audioLanguage").takeIf { it.isNotBlank() },
                            filePath = item.optString("filePath", item.optString("path"))
                                .takeIf { it.isNotBlank() },
                            status = item.optString("status", DownloadStatus.FAILED),
                            stage = item.optString("stage", item.optString("status", "unknown")),
                            progress = item.optDouble("progress", 0.0).toFloat(),
                            speedBytesPerSecond = item.optLong("speedBytesPerSecond", 0L),
                            etaSeconds = if (item.has("etaSeconds") && !item.isNull("etaSeconds")) {
                                item.optLong("etaSeconds")
                            } else null,
                            estimatedBytes = if (item.has("estimatedBytes") && !item.isNull("estimatedBytes")) {
                                item.optLong("estimatedBytes")
                            } else null,
                            downloadedBytes = item.optLong("downloadedBytes", 0L),
                            timestamp = item.optLong("timestamp"),
                            error = item.optString("error").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }.sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    private fun writeUnlocked(entries: List<DownloadRecord>) {
        val array = JSONArray()
        entries.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("url", item.url)
                    .put("quality", item.quality)
                    .put("audioLanguage", item.audioLanguage ?: "")
                    .put("filePath", item.filePath ?: "")
                    .put("status", item.status)
                    .put("stage", item.stage)
                    .put("progress", item.progress)
                    .put("speedBytesPerSecond", item.speedBytesPerSecond)
                    .put("etaSeconds", item.etaSeconds ?: JSONObject.NULL)
                    .put("estimatedBytes", item.estimatedBytes ?: JSONObject.NULL)
                    .put("downloadedBytes", item.downloadedBytes)
                    .put("timestamp", item.timestamp)
                    .put("error", item.error ?: "")
            )
        }
        file.writeText(array.toString())
    }

    companion object {
        private const val MAX_HISTORY = 150
        private val FILE_LOCK = Any()
    }
}
