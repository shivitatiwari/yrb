package com.apoorv.yrb.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryEntry(
    val id: String,
    val title: String,
    val url: String,
    val quality: Int,
    val path: String?,
    val status: String,
    val timestamp: Long,
    val error: String? = null
)

class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "download_history.json")

    fun readAll(): List<HistoryEntry> = synchronized(FILE_LOCK) {
        if (!file.exists()) return emptyList()
        runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        HistoryEntry(
                            id = item.getString("id"),
                            title = item.optString("title", "Video"),
                            url = item.optString("url"),
                            quality = item.optInt("quality"),
                            path = item.optString("path").takeIf { it.isNotBlank() },
                            status = item.optString("status", "unknown"),
                            timestamp = item.optLong("timestamp"),
                            error = item.optString("error").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }.sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    fun add(entry: HistoryEntry) = synchronized(FILE_LOCK) {
        val current = readAll().toMutableList()
        current.removeAll { it.id == entry.id }
        current.add(0, entry)
        while (current.size > 100) current.removeAt(current.lastIndex)

        val array = JSONArray()
        current.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("url", item.url)
                    .put("quality", item.quality)
                    .put("path", item.path ?: "")
                    .put("status", item.status)
                    .put("timestamp", item.timestamp)
                    .put("error", item.error ?: "")
            )
        }
        file.writeText(array.toString())
    }

    fun clear() = synchronized(FILE_LOCK) {
        if (file.exists()) file.delete()
    }

    companion object {
        private val FILE_LOCK = Any()
    }
}
