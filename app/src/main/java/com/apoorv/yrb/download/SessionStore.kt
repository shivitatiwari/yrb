package com.apoorv.yrb.download

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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

        val netscape = readWebViewCookieDatabase()
            .getOrElse { buildCookieManagerFallback(cookieManager) }

        SessionCookieValidator.validateAndCount(netscape)
        check(SessionCookieValidator.looksAuthenticated(netscape)) {
            "A signed-in YouTube session was not detected yet. Finish signing in, then try again."
        }

        cookieFile.parentFile?.mkdirs()
        cookieFile.writeText(netscape)
        prefs.edit().putString(KEY_USER_AGENT, userAgent).apply()

        status().also {
            check(it.connected) { "Could not save the signed-in YouTube session." }
        }
    }

    private fun readWebViewCookieDatabase(): Result<String> = runCatching {
        val databaseFile = context.dataDir.resolve("app_webview/Default/Cookies")
        check(databaseFile.exists()) { "WebView cookie database is not ready yet." }

        val rows = mutableListOf<String>()
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { database ->
            val projection = arrayOf(
                "host_key",
                "path",
                "name",
                "value",
                "expires_utc",
                "is_secure"
            )

            database.query(
                "cookies",
                projection,
                null,
                null,
                null,
                null,
                null
            ).use { cursor ->
                val hostIndex = cursor.getColumnIndexOrThrow("host_key")
                val pathIndex = cursor.getColumnIndexOrThrow("path")
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val valueIndex = cursor.getColumnIndexOrThrow("value")
                val expiresIndex = cursor.getColumnIndexOrThrow("expires_utc")
                val secureIndex = cursor.getColumnIndexOrThrow("is_secure")

                while (cursor.moveToNext()) {
                    val host = cursor.getString(hostIndex).orEmpty()
                    if (!isYouTubeSessionDomain(host)) continue

                    val name = cursor.getString(nameIndex).orEmpty()
                    val value = cursor.getString(valueIndex).orEmpty()
                    if (name.isBlank() || value.isBlank()) continue

                    val path = cursor.getString(pathIndex).orEmpty().ifBlank { "/" }
                    val secure = cursor.getInt(secureIndex) == 1
                    val expiry = chromiumExpiryToUnixSeconds(
                        cursor.getLong(expiresIndex)
                    )
                    val normalizedHost = if (host.startsWith(".")) host else ".$host"

                    rows += listOf(
                        normalizedHost,
                        "TRUE",
                        path,
                        secure.toString().uppercase(),
                        expiry.toString(),
                        name,
                        value
                    ).joinToString("\t")
                }
            }
        }

        check(rows.isNotEmpty()) { "No YouTube/Google browser cookies were found." }

        buildString {
            appendLine("# Netscape HTTP Cookie File")
            appendLine("# Generated locally by Yrb from Android WebView.")
            appendLine("# This file never leaves the device.")
            rows.forEach { appendLine(it) }
        }
    }

    private fun buildCookieManagerFallback(cookieManager: CookieManager): String {
        val sources = listOf(
            ".youtube.com" to "https://www.youtube.com/",
            ".google.com" to "https://accounts.google.com/"
        )
        val seen = linkedSetOf<String>()
        val rows = mutableListOf<String>()

        for ((domain, url) in sources) {
            cookieManager.getCookie(url).orEmpty()
                .split(';')
                .forEach { part ->
                    val trimmed = part.trim()
                    val separator = trimmed.indexOf('=')
                    if (separator <= 0) return@forEach

                    val name = trimmed.substring(0, separator).trim()
                    val value = trimmed.substring(separator + 1)
                    if (name.isBlank() || value.isBlank()) return@forEach
                    if (!seen.add(domain + "|" + name)) return@forEach

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
            appendLine("# Generated locally by Yrb from Android WebView.")
            appendLine("# This file never leaves the device.")
            rows.forEach { appendLine(it) }
        }
    }

    private fun isYouTubeSessionDomain(host: String): Boolean {
        val normalized = host.lowercase().removePrefix(".")
        return normalized == "youtube.com" ||
            normalized.endsWith(".youtube.com") ||
            normalized == "google.com" ||
            normalized.endsWith(".google.com") ||
            normalized == "googlevideo.com" ||
            normalized.endsWith(".googlevideo.com")
    }

    private fun chromiumExpiryToUnixSeconds(raw: Long): Long {
        if (raw <= 0L) return 0L
        val seconds = raw / 1_000_000L - 11_644_473_600L
        return seconds.coerceAtLeast(0L)
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
