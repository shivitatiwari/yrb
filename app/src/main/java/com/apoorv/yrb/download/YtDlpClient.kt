package com.apoorv.yrb.download

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import kotlin.math.roundToLong

data class QualityOption(
    val height: Int,
    val selector: String,
    val estimatedBytes: Long?,
    val approximate: Boolean
) {
    val label: String
        get() = if (height == 2160) "4K" else height.toString() + "p"
}

data class VideoInspection(
    val title: String,
    val durationSeconds: Long?,
    val qualities: List<QualityOption>
)

object QualitySelector {
    val supported = listOf(2160, 1440, 1080, 720, 480, 360)
}

private data class FormatCandidate(
    val id: String,
    val height: Int?,
    val ext: String,
    val vcodec: String,
    val acodec: String,
    val bitrate: Double,
    val size: Long?,
    val exactSize: Boolean
) {
    val hasVideo: Boolean get() = vcodec.isNotBlank() && vcodec != "none"
    val hasAudio: Boolean get() = acodec.isNotBlank() && acodec != "none"
    val videoOnly: Boolean get() = hasVideo && !hasAudio
    val audioOnly: Boolean get() = !hasVideo && hasAudio
    val combined: Boolean get() = hasVideo && hasAudio
}

object YtDlpClient {
    fun inspect(context: Context, url: String): VideoInspection {
        YtDlpRuntime.ensureFresh(context)

        val request = YoutubeDLRequest(url)
            .addOption("--dump-single-json")
            .addOption("--skip-download")
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--quiet")

        val response = YoutubeDL.getInstance().execute(request)
        val root = JSONObject(response.out.trim())
        val formats = root.optJSONArray("formats")
        val candidates = buildList {
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val format = formats.optJSONObject(i) ?: continue
                    val id = format.optString("format_id").trim()
                    if (id.isBlank()) continue
                    if (format.optBoolean("has_drm", false)) continue

                    val url = format.optString("url").trim()
                    val manifestUrl = format.optString("manifest_url").trim()
                    if (url.isBlank() && manifestUrl.isBlank()) continue

                    val rawVideoCodec = format.optString("vcodec")
                    if (rawVideoCodec.equals("images", ignoreCase = true)) continue

                    val height = format.optInt("height", -1).takeIf { it > 0 }
                    val filesize = format.optLong("filesize", -1L).takeIf { it > 0 }
                    val approx = format.optLong("filesize_approx", -1L).takeIf { it > 0 }
                    add(
                        FormatCandidate(
                            id = id,
                            height = height,
                            ext = format.optString("ext"),
                            vcodec = rawVideoCodec,
                            acodec = format.optString("acodec"),
                            bitrate = format.optDouble("tbr", format.optDouble("abr", 0.0)),
                            size = filesize ?: approx,
                            exactSize = filesize != null
                        )
                    )
                }
            }
        }

        val bestAudio = candidates
            .filter { it.audioOnly }
            .maxWithOrNull(
                compareBy<FormatCandidate>(
                    { if (it.ext == "m4a") 1 else 0 },
                    { it.bitrate }
                )
            )

        val qualityOptions = QualitySelector.supported.mapNotNull { height ->
            val exact = candidates.filter { it.height == height }
            val videoOnly = exact
                .filter { it.videoOnly }
                .maxWithOrNull(
                    compareBy<FormatCandidate>(
                        { if (it.ext == "mp4" && it.vcodec.startsWith("avc1")) 3 else if (it.ext == "mp4") 2 else 1 },
                        { it.bitrate }
                    )
                )

            if (videoOnly != null && bestAudio != null) {
                val total = if (videoOnly.size != null && bestAudio.size != null) {
                    videoOnly.size + bestAudio.size
                } else null
                QualityOption(
                    height = height,
                    selector = videoOnly.id + "+" + bestAudio.id,
                    estimatedBytes = total,
                    approximate = !videoOnly.exactSize || !bestAudio.exactSize
                )
            } else {
                val combined = exact
                    .filter { it.combined }
                    .maxWithOrNull(
                        compareBy<FormatCandidate>(
                            { if (it.ext == "mp4") 2 else 1 },
                            { it.bitrate }
                        )
                    ) ?: return@mapNotNull null

                QualityOption(
                    height = height,
                    selector = combined.id,
                    estimatedBytes = combined.size,
                    approximate = !combined.exactSize
                )
            }
        }

        if (qualityOptions.isEmpty()) {
            error("No supported video resolutions are currently downloadable for this video.")
        }

        return VideoInspection(
            title = root.optString("title").ifBlank { "YouTube video" },
            durationSeconds = root.optDouble("duration", -1.0)
                .takeIf { it > 0 }
                ?.roundToLong(),
            qualities = qualityOptions
        )
    }
}

object FileSizeFormatter {
    fun format(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "Size unavailable"
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
            String.format("%.1f %s", value, units[index])
        }
    }
}
