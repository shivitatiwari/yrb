package com.apoorv.yrb.download

import android.content.Context
import android.net.Uri
import java.io.File

data class SessionStatus(
    val connected: Boolean,
    val cookieCount: Int = 0
)

class SessionStore(private val context: Context) {
    private val cookieFile = File(context.noBackupFilesDir, COOKIE_FILE_NAME)

    fun status(): SessionStatus {
        if (!cookieFile.exists() || cookieFile.length() <= 0L) {
            return SessionStatus(connected = false)
        }

        val count = runCatching {
            cookieFile.useLines { lines ->
                lines.count { line ->
                    val trimmed = line.trim()
                    trimmed.isNotEmpty() &&
                        !trimmed.startsWith("#") &&
                        trimmed.split('\t').size >= 7
                }
            }
        }.getOrDefault(0)

        return SessionStatus(
            connected = count > 0,
            cookieCount = count
        )
    }

    fun cookieFileOrNull(): File? =
        cookieFile.takeIf { status().connected }

    fun importFrom(uri: Uri): Result<SessionStatus> = runCatching {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not read the selected file.")

        validate(text)

        cookieFile.parentFile?.mkdirs()
        cookieFile.writeText(text)

        status().also {
            check(it.connected) { "No usable cookies were found." }
        }
    }

    fun clear() {
        runCatching { cookieFile.delete() }
    }

    private fun validate(text: String) {
        val lines = text.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()

        check(lines.isNotEmpty()) { "The selected file is empty." }

        val cookieRows = lines.filter {
            !it.startsWith("#") && it.split('\t').size >= 7
        }

        check(cookieRows.isNotEmpty()) {
            "This does not look like a Netscape cookies.txt file."
        }

        val hasYouTubeOrGoogle = cookieRows.any { row ->
            val domain = row.substringBefore('\t').lowercase()
            domain.contains("youtube.com") ||
                domain.contains("google.com") ||
                domain.contains("googlevideo.com")
        }

        check(hasYouTubeOrGoogle) {
            "The selected cookies file does not contain a YouTube/Google session."
        }
    }

    companion object {
        private const val COOKIE_FILE_NAME = "youtube-cookies.txt"
    }
}
