package com.apoorv.yrb.download

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

data class AudioLanguageOption(
    val id: String,
    val label: String
)

data class QualityOption(
    val height: Int,
    val selector: String,
    val estimatedBytes: Long?,
    val approximate: Boolean,
    val extractorArgs: String? = null,
    val forceIpv4: Boolean = false,
    val clientKey: String = "default",
    val audioLanguageId: String = "default",
    val audioOnly: Boolean = false
) {
    val label: String
        get() = when {
            audioOnly || height == 0 -> "Audio only"
            height == 2160 -> "4K"
            else -> height.toString() + "p"
        }
}

data class VideoInspection(
    val title: String,
    val durationSeconds: Long?,
    val audioLanguages: List<AudioLanguageOption>,
    val defaultLanguageId: String,
    val qualitiesByLanguage: Map<String, List<QualityOption>>,
    val audioOnlyByLanguage: Map<String, QualityOption>
) {
    fun qualities(languageId: String): List<QualityOption> =
        qualitiesByLanguage[languageId]
            ?: qualitiesByLanguage[defaultLanguageId]
            ?: emptyList()

    fun audioOnly(languageId: String): QualityOption? =
        audioOnlyByLanguage[languageId]
            ?: audioOnlyByLanguage[defaultLanguageId]
            ?: audioOnlyByLanguage.values.firstOrNull()
}

object QualitySelector {
    val supported = listOf(2160, 1440, 1080, 720, 480, 360)
}

private data class InspectionMode(
    val key: String,
    val extractorArgs: String? = null,
    val forceIpv4: Boolean = false,
    val preferHls: Boolean = false
)

private data class FormatCandidate(
    val id: String,
    val height: Int?,
    val ext: String,
    val vcodec: String,
    val acodec: String,
    val tbr: Double,
    val vbr: Double,
    val abr: Double,
    val size: Long?,
    val exactSize: Boolean,
    val language: String,
    val languagePreference: Int,
    val formatNote: String,
    val audioChannels: Int,
    val protocol: String
) {
    val hasVideo: Boolean get() = vcodec.isNotBlank() && vcodec != "none"
    val hasAudio: Boolean get() = acodec.isNotBlank() && acodec != "none"
    val videoOnly: Boolean get() = hasVideo && !hasAudio
    val audioOnly: Boolean get() = !hasVideo && hasAudio
    val combined: Boolean get() = hasVideo && hasAudio

    fun estimatedSize(durationSeconds: Long?): Pair<Long?, Boolean> {
        size?.let { return it to !exactSize }
        val duration = durationSeconds ?: return null to true
        val kbps = when {
            videoOnly -> vbr.takeIf { it > 0.0 } ?: tbr.takeIf { it > 0.0 }
            audioOnly -> abr.takeIf { it > 0.0 } ?: tbr.takeIf { it > 0.0 }
            else -> tbr.takeIf { it > 0.0 }
        } ?: return null to true

        val estimate = ((kbps * 1000.0 / 8.0) * duration.toDouble())
            .roundToLong()
            .coerceAtLeast(1L)
        return estimate to true
    }
}

private data class CachedInspection(
    val createdAt: Long,
    val inspection: VideoInspection
)

object YtDlpClient {
    private const val CACHE_TTL_MS = 10L * 60L * 1000L
    private val cache = ConcurrentHashMap<String, CachedInspection>()

    private val MODE_DEFAULT = InspectionMode("default")
    private val MODE_AUTHENTICATED = InspectionMode(
        "authenticated",
        extractorArgs = "youtube:player_client=default,web_embedded"
    )
    private val MODE_IPV4 = InspectionMode("ipv4", forceIpv4 = true)
    private val MODE_WEB_SAFARI = InspectionMode(
        "web_safari_hls",
        extractorArgs = "youtube:player_client=default,web_safari",
        preferHls = true
    )
    private val MODE_WEB_EMBEDDED = InspectionMode(
        "web_embedded",
        extractorArgs = "youtube:player_client=web_embedded"
    )

    private val anonymousModes = listOf(
        MODE_DEFAULT,
        MODE_IPV4,
        MODE_WEB_SAFARI,
        MODE_WEB_EMBEDDED
    )

    fun inspect(context: Context, url: String): VideoInspection {
        val normalized = url.trim()
        val sessionStore = SessionStore(context)
        val cookieFile = sessionStore.cookieFileOrNull()
        val sessionKey = cookieFile?.lastModified()?.toString() ?: "anonymous"
        val cacheKey = normalized + "|" + sessionKey
        val now = System.currentTimeMillis()

        cache[cacheKey]?.let { cached ->
            if (now - cached.createdAt <= CACHE_TTL_MS) {
                return cached.inspection
            }
            cache.remove(cacheKey)
        }

        val mode = if (cookieFile != null) {
            MODE_AUTHENTICATED
        } else {
            MODE_DEFAULT
        }

        val inspection = runCatching {
            inspectOnce(context, normalized, mode)
        }.getOrElse { firstError ->
            if (!shouldTryRecovery(firstError)) throw firstError

            YtDlpRuntime.refreshIfDue(context, force = true)

            runCatching {
                inspectOnce(context, normalized, mode)
            }.getOrElse { finalError ->
                throw IllegalStateException(
                    friendlyInspectionFailure(
                        finalError,
                        authenticated = cookieFile != null
                    ),
                    finalError
                )
            }
        }

        cache[cacheKey] = CachedInspection(now, inspection)
        return inspection
    }

    private fun tryModes(
        context: Context,
        url: String,
        modes: List<InspectionMode>
    ): Result<VideoInspection> {
        var lastError: Throwable? = null

        for (mode in modes) {
            val result = runCatching { inspectOnce(context, url, mode) }
            result.onSuccess {
                return result
            }.onFailure {
                lastError = it
                if (!shouldTryRecovery(it)) return Result.failure(it)
            }
        }

        return Result.failure(
            lastError ?: IllegalStateException("Video inspection failed.")
        )
    }

    private fun inspectOnce(
        context: Context,
        url: String,
        mode: InspectionMode
    ): VideoInspection {
        val request = YoutubeDLRequest(url)
            .addOption("--dump-single-json")
            .addOption("--skip-download")
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--quiet")
            .addOption("--socket-timeout", "5")
            .addOption("--retries", "1")

        mode.extractorArgs?.let {
            request.addOption("--extractor-args", it)
        }
        if (mode.forceIpv4) {
            request.addOption("--force-ipv4")
        }

        val sessionStore = SessionStore(context)
        sessionStore.cookieFileOrNull()?.let { cookieFile ->
            request.addOption("--cookies", cookieFile.absolutePath)
        }
        sessionStore.userAgentOrNull()?.let { userAgent ->
            request.addOption("--add-header", "User-Agent:" + userAgent)
        }

        val response = YoutubeDL.getInstance().execute(request)
        val root = JSONObject(response.out.trim())
        val duration = root.optDouble("duration", -1.0)
            .takeIf { it > 0.0 }
            ?.roundToLong()

        val formats = root.optJSONArray("formats")
        val candidates = buildList {
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val format = formats.optJSONObject(i) ?: continue
                    val id = format.optString("format_id").trim()
                    if (id.isBlank()) continue
                    if (format.optBoolean("has_drm", false)) continue

                    val mediaUrl = format.optString("url").trim()
                    val manifestUrl = format.optString("manifest_url").trim()
                    if (mediaUrl.isBlank() && manifestUrl.isBlank()) continue

                    val rawVideoCodec = format.optString("vcodec")
                    if (rawVideoCodec.equals("images", ignoreCase = true)) continue

                    val filesize = format.optLong("filesize", -1L).takeIf { it > 0L }
                    val approx = format.optLong("filesize_approx", -1L).takeIf { it > 0L }

                    add(
                        FormatCandidate(
                            id = id,
                            height = format.optInt("height", -1).takeIf { it > 0 },
                            ext = format.optString("ext"),
                            vcodec = rawVideoCodec,
                            acodec = format.optString("acodec"),
                            tbr = format.optDouble("tbr", 0.0),
                            vbr = format.optDouble("vbr", 0.0),
                            abr = format.optDouble("abr", 0.0),
                            size = filesize ?: approx,
                            exactSize = filesize != null,
                            language = format.optString("language").trim(),
                            languagePreference = format.optInt("language_preference", -1),
                            formatNote = format.optString("format_note").trim(),
                            audioChannels = format.optInt("audio_channels", 0),
                            protocol = format.optString("protocol").trim()
                        )
                    )
                }
            }
        }

        val usableCandidates = if (mode.preferHls) {
            candidates.filter { it.protocol.contains("m3u8", ignoreCase = true) }
        } else {
            candidates
        }

        val audioCandidates = usableCandidates.filter { it.audioOnly }
        val groupedAudio = audioCandidates.groupBy {
            it.language.ifBlank { DEFAULT_LANGUAGE }
        }

        val selectedAudioByLanguage = groupedAudio.mapValues { (_, options) ->
            options.maxWith(
                compareBy<FormatCandidate>(
                    { if (it.formatNote.contains("original", ignoreCase = true)) 1 else 0 },
                    { it.languagePreference },
                    { it.audioChannels },
                    { if (it.ext == "m4a") 1 else 0 },
                    { it.abr.takeIf { value -> value > 0.0 } ?: it.tbr }
                )
            )
        }

        val fallbackLanguage = DEFAULT_LANGUAGE
        val fallbackAudio = selectedAudioByLanguage[fallbackLanguage]
            ?: audioCandidates.maxWithOrNull(
                compareBy<FormatCandidate>(
                    { if (it.formatNote.contains("original", ignoreCase = true)) 1 else 0 },
                    { it.languagePreference },
                    { it.audioChannels },
                    { if (it.ext == "m4a") 1 else 0 },
                    { it.abr.takeIf { value -> value > 0.0 } ?: it.tbr }
                )
            )

        val languageEntries = if (selectedAudioByLanguage.isNotEmpty()) {
            selectedAudioByLanguage.entries.sortedWith(
                compareByDescending<Map.Entry<String, FormatCandidate>> {
                    it.value.formatNote.contains("original", ignoreCase = true)
                }.thenByDescending {
                    it.value.languagePreference
                }.thenBy {
                    languageLabel(it.key, it.value.formatNote)
                }
            )
        } else {
            emptyList()
        }

        val defaultLanguageId = languageEntries.firstOrNull()?.key ?: fallbackLanguage
        val languages = if (languageEntries.isEmpty()) {
            listOf(AudioLanguageOption(fallbackLanguage, "Default audio"))
        } else {
            languageEntries.map { (languageId, candidate) ->
                AudioLanguageOption(
                    id = languageId,
                    label = languageLabel(languageId, candidate.formatNote)
                )
            }
        }

        val languageIds = languages.map { it.id }
        val qualitiesByLanguage = languageIds.associateWith { languageId ->
            val chosenAudio = selectedAudioByLanguage[languageId] ?: fallbackAudio
            buildQualities(
                candidates = usableCandidates,
                audio = chosenAudio,
                durationSeconds = duration,
                allowCombinedFallback = languageIds.size == 1,
                languageId = languageId,
                mode = mode
            )
        }.filterValues { it.isNotEmpty() }

        val audioOnlyByLanguage = languageIds.mapNotNull { languageId ->
            val chosenAudio = selectedAudioByLanguage[languageId] ?: fallbackAudio
                ?: return@mapNotNull null
            val (bytes, approximate) = chosenAudio.estimatedSize(duration)
            languageId to QualityOption(
                height = 0,
                selector = chosenAudio.id,
                estimatedBytes = bytes,
                approximate = approximate,
                extractorArgs = mode.extractorArgs,
                forceIpv4 = mode.forceIpv4,
                clientKey = mode.key,
                audioLanguageId = languageId,
                audioOnly = true
            )
        }.toMap()

        val availableLanguageIds =
            (qualitiesByLanguage.keys + audioOnlyByLanguage.keys).toSet()

        if (availableLanguageIds.isEmpty()) {
            error("No supported downloadable video or audio streams were reported for this video.")
        }

        val realDefault = if (availableLanguageIds.contains(defaultLanguageId)) {
            defaultLanguageId
        } else {
            availableLanguageIds.first()
        }

        val visibleLanguages = languages.filter { availableLanguageIds.contains(it.id) }

        return VideoInspection(
            title = root.optString("title").ifBlank { "YouTube video" },
            durationSeconds = duration,
            audioLanguages = visibleLanguages.ifEmpty {
                listOf(AudioLanguageOption(realDefault, "Default audio"))
            },
            defaultLanguageId = realDefault,
            qualitiesByLanguage = qualitiesByLanguage,
            audioOnlyByLanguage = audioOnlyByLanguage
        )
    }

    private fun buildQualities(
        candidates: List<FormatCandidate>,
        audio: FormatCandidate?,
        durationSeconds: Long?,
        allowCombinedFallback: Boolean,
        languageId: String,
        mode: InspectionMode
    ): List<QualityOption> {
        return QualitySelector.supported.mapNotNull { height ->
            val exact = candidates.filter { it.height == height }

            val videoOnly = exact
                .filter { it.videoOnly }
                .maxWithOrNull(
                    compareBy<FormatCandidate>(
                        { if (it.ext == "mp4" && it.vcodec.startsWith("avc1")) 3 else if (it.ext == "mp4") 2 else 1 },
                        { it.vbr.takeIf { value -> value > 0.0 } ?: it.tbr }
                    )
                )

            if (videoOnly != null && audio != null) {
                val (videoBytes, videoApprox) = videoOnly.estimatedSize(durationSeconds)
                val (audioBytes, audioApprox) = audio.estimatedSize(durationSeconds)
                val total = if (videoBytes != null && audioBytes != null) {
                    videoBytes + audioBytes
                } else null

                QualityOption(
                    height = height,
                    selector = videoOnly.id + "+" + audio.id,
                    estimatedBytes = total,
                    approximate = videoApprox || audioApprox,
                    extractorArgs = mode.extractorArgs,
                    forceIpv4 = mode.forceIpv4,
                    clientKey = mode.key,
                    audioLanguageId = languageId
                )
            } else if (allowCombinedFallback) {
                val combined = exact
                    .filter { it.combined }
                    .maxWithOrNull(
                        compareBy<FormatCandidate>(
                            { if (it.ext == "mp4") 2 else 1 },
                            { it.tbr }
                        )
                    ) ?: return@mapNotNull null

                val (bytes, approximate) = combined.estimatedSize(durationSeconds)
                QualityOption(
                    height = height,
                    selector = combined.id,
                    estimatedBytes = bytes,
                    approximate = approximate,
                    extractorArgs = mode.extractorArgs,
                    forceIpv4 = mode.forceIpv4,
                    clientKey = mode.key,
                    audioLanguageId = languageId
                )
            } else {
                null
            }
        }
    }

    private fun shouldTryRecovery(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
            .lowercase()

        if (message.isBlank()) return true

        return RECOVERABLE_MARKERS.any { marker -> message.contains(marker) }
    }

    private fun friendlyInspectionFailure(
        error: Throwable,
        authenticated: Boolean
    ): String {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
            .lowercase()

        if (authenticated && message.contains("page needs to be reloaded")) {
            return "YouTube rejected the saved browser session. Refresh the YouTube connection and try again."
        }

        if (
            message.contains("sign in to confirm") ||
            message.contains("not a bot") ||
            message.contains("login required")
        ) {
            return if (authenticated) {
                "The saved YouTube session is no longer accepted. Refresh the YouTube connection."
            } else {
                "YouTube requires a signed-in session for this video."
            }
        }

        return ProgressLineParser.humanError(error.message)
    }

    private fun languageLabel(languageId: String, formatNote: String): String {
        if (languageId == DEFAULT_LANGUAGE) {
            return if (formatNote.contains("original", ignoreCase = true)) {
                "Original audio"
            } else {
                "Default audio"
            }
        }

        val locale = Locale.forLanguageTag(languageId.replace("_", "-"))
        val display = locale.getDisplayLanguage(Locale.getDefault())
            .takeIf { it.isNotBlank() && it != languageId }
            ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            ?: languageId

        return if (formatNote.contains("original", ignoreCase = true)) {
            display + " (Original)"
        } else {
            display
        }
    }

    fun recoveryCandidates(
        context: Context,
        url: String,
        height: Int,
        audioLanguageId: String,
        currentClientKey: String
    ): List<QualityOption> {
        YtDlpRuntime.refreshIfDue(context, force = true)

        return anonymousModes
            .filter { it.key != currentClientKey }
            .mapNotNull { mode ->
                runCatching { inspectOnce(context, url.trim(), mode) }
                    .getOrNull()
                    ?.let { inspection ->
                        val languageId = when {
                            inspection.qualitiesByLanguage.containsKey(audioLanguageId) ->
                                audioLanguageId
                            else -> inspection.defaultLanguageId
                        }
                        if (height == 0) {
                            inspection.audioOnly(languageId)
                        } else {
                            inspection.qualities(languageId)
                                .firstOrNull { it.height == height }
                        }
                    }
            }
            .distinctBy { it.clientKey + "|" + it.selector }
    }

    fun isRecoverableDownloadError(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
            .lowercase()

        return message.contains("http error 403") ||
            message.contains("403 forbidden") ||
            message.contains("unable to download video data") ||
            message.contains("downloaded file is empty") ||
            message.contains("po token") ||
            message.contains("forbidden")
    }

    internal fun anonymousFallbackNamesForTest(): List<String> =
        anonymousModes.map { it.key }

    internal fun authenticatedExtractorArgsForTest(): String =
        MODE_AUTHENTICATED.extractorArgs.orEmpty()

    private const val DEFAULT_LANGUAGE = "default"

    private val RECOVERABLE_MARKERS = listOf(
        "sign in to confirm",
        "not a bot",
        "page needs to be reloaded",
        "http error 403",
        "forbidden",
        "po token",
        "requested format is not available",
        "no formats",
        "no supported downloadable video resolutions",
        "unable to download",
        "timed out",
        "timeout"
    )
}

object FileSizeFormatter {
    fun format(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return "Size unavailable"
        if (bytes == 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) {
            bytes.toString() + " " + units[index]
        } else {
            String.format(Locale.getDefault(), "%.1f %s", value, units[index])
        }
    }
}
