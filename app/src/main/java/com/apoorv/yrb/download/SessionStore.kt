package com.apoorv.yrb.download

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import java.io.File

data class SessionStatus(
    val connected: Boolean,
    val cookieCount: Int = 0
)

object SessionCookieValidator {
    private val authCookieNames = setOf(
        "SAPISID",
        "SID",
        "LOGIN_INFO",
        "__Secure-1PSID",
        "__Secure-3PSID",
        "__Secure-1PAPISID",
        "__Secure-3PAPISID"
    )

    fun validateAndCount(text: String): Int {
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

        return cookieRows.size
    }

    fun looksAuthenticated(text: String): Boolean {
        return text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { row ->
                val columns = row.split('\t')
                columns.getOrNull(5)
            }
            .any { it in authCookieNames }
    }
}

class SessionStore(private val context: Context) {
    private val cookieFile = File(context.noBackupFilesDir, COOKIE_FILE_NAME)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun status(): SessionStatus {
        if (!cookieFile.exists() || cookieFile.length() <= 0L) {
            return SessionStatus(connected = false)
        }

        val text = runCatching { cookieFile.readText() }.getOrDefault("")
        val count = runCatching {
            SessionCookieValidator.validateAndCount(text)
        }.getOrDefault(0)

        return SessionStatus(
            connected = count > 0 && SessionCookieValidator.looksAuthenticated(text),
            cookieCount = count
        )
    }

    fun cookieFileOrNull(): File? =
        cookieFile.takeIf { status().connected }

    fun userAgentOrNull(): String? =
        prefs.getString(KEY_USER_AGENT, null)?.takeIf { it.isNotBlank() }

    fun importFrom(uri: Uri): Result<SessionStatus> = runCatching {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not read the selected file.")

        SessionCookieValidator.validateAndCount(text)
        check(SessionCookieValidator.looksAuthenticated(text)) {
            "The file contains YouTube cookies, but no signed-in session was detected."
        }

        cookieFile.parentFile?.mkdirs()
        cookieFile.writeText(text)

        status().also {
            check(it.connected) { "No usable signed-in YouTube session was found." }
        }
    }

    fun webViewSessionLooksAuthenticated(cookieManager: CookieManager): Boolean =
        runCatching {
            SessionCookieValidator.looksAuthenticated(
                netscapeFromWebView(cookieManager)
            )
        }.getOrDefault(false)

    fun captureFromWebView(
        cookieManager: CookieManager,
        userAgent: String
    ): Result<SessionStatus> = runCatching {
        cookieManager.flush()

        val netscape = netscapeFromWebView(cookieManager)

        SessionCookieValidator.validateAndCount(netscape)
        check(SessionCookieValidator.looksAuthenticated(netscape)) {
            "A signed-in YouTube session was not detected yet. Finish signing in first."
        }

        cookieFile.parentFile?.mkdirs()
        cookieFile.writeText(netscape)
        prefs.edit().putString(KEY_USER_AGENT, userAgent).apply()

        status().also {
            check(it.connected) { "Could not save the signed-in YouTube session." }
        }
    }

    private fun netscapeFromWebView(cookieManager: CookieManager): String {
        val sources = listOf(
            ".youtube.com" to "https://www.youtube.com/"
        )

        val seen = linkedSetOf<String>()
        val rows = mutableListOf<String>()

        for ((domain, url) in sources) {
            val raw = cookieManager.getCookie(url).orEmpty()
            raw.split(';').forEach { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq <= 0) return@forEach

                val name = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1)
                if (name.isBlank()) return@forEach

                val dedupe = domain + "|" + name
                if (!seen.add(dedupe)) return@forEach

                rows += listOf(
                    domain,
                    "TRUE",
                    "/",
                    "TRUE",
                    "0",
                    name,
                    value
                ).joinToString("\t")
            }
        }

        return buildString {
            appendLine("# Netscape HTTP Cookie File")
            appendLine("# Generated locally by Yrb from the user's in-app YouTube session.")
            appendLine("# This file never leaves the device.")
            rows.forEach { appendLine(it) }
        }
    }

    fun clear() {
        runCatching { cookieFile.delete() }
        prefs.edit().remove(KEY_USER_AGENT).apply()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    companion object {
        private const val COOKIE_FILE_NAME = "youtube-cookies.txt"
        private const val PREFS_NAME = "youtube_session"
        private const val KEY_USER_AGENT = "webview_user_agent"
    }
}
