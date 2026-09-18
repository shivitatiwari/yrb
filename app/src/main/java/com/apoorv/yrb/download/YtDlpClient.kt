package com.apoorv.yrb.download

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject

data class VideoInspection(
    val title: String,
    val qualities: List<Int>
)

object QualitySelector {
    val supported = listOf(2160, 1440, 1080, 720, 480, 360)

    fun selector(height: Int): String {
        require(height in supported)
        return "bestvideo[height=" + height + "][vcodec^=avc1]+bestaudio[ext=m4a]/" +
            "bestvideo[height=" + height + "]+bestaudio/" +
            "best[height=" + height + "]"
    }
}

object YtDlpClient {
    fun inspect(url: String): VideoInspection {
        val request = YoutubeDLRequest(url)
            .addOption("--dump-single-json")
            .addOption("--skip-download")
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--quiet")

        val response = YoutubeDL.getInstance().execute(request)
        val root = JSONObject(response.out.trim())
        val formats = root.optJSONArray("formats")
        val available = mutableSetOf<Int>()

        if (formats != null) {
            for (i in 0 until formats.length()) {
                val format = formats.optJSONObject(i) ?: continue
                val height = format.optInt("height", -1)
                if (height > 0) available += height
            }
        }

        val qualities = QualitySelector.supported.filter { it in available }
        if (qualities.isEmpty()) {
            error("No supported video resolutions were reported by YouTube.")
        }

        return VideoInspection(
            title = root.optString("title").ifBlank { "YouTube video" },
            qualities = qualities
        )
    }
}
