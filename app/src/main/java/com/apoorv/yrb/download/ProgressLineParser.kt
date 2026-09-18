package com.apoorv.yrb.download

object ProgressLineParser {
    private val ansiRegex = Regex("""\u001B\[[;\d]*m""")
    private val percentRegex = Regex("""([0-9]{1,3}(?:\.[0-9]+)?)%""")
    private val speedRegex = Regex("""\bat\s+([0-9.]+)\s*([KMGT]?i?B)/s""", RegexOption.IGNORE_CASE)
    private val etaRegex = Regex("""\bETA\s+(?:(\d+):)?(\d+):(\d+)""", RegexOption.IGNORE_CASE)

    fun percent(line: String?): Float? {
        val clean = clean(line) ?: return null
        val match = percentRegex.find(clean) ?: return null
        return match.groupValues[1].toFloatOrNull()?.coerceIn(0f, 100f)
    }

    fun speedBytesPerSecond(line: String?): Long? {
        val clean = clean(line) ?: return null
        val match = speedRegex.find(clean) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()
        val multiplier = when (unit) {
            "B" -> 1.0
            "KB" -> 1_000.0
            "KIB" -> 1_024.0
            "MB" -> 1_000_000.0
            "MIB" -> 1_048_576.0
            "GB" -> 1_000_000_000.0
            "GIB" -> 1_073_741_824.0
            "TB" -> 1_000_000_000_000.0
            "TIB" -> 1_099_511_627_776.0
            else -> return null
        }
        return (value * multiplier).toLong().coerceAtLeast(0L)
    }

    fun etaSeconds(line: String?): Long? {
        val clean = clean(line) ?: return null
        val match = etaRegex.find(clean) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        return hours * 3600L + minutes * 60L + seconds
    }

    fun isMerging(line: String?): Boolean {
        val clean = clean(line) ?: return false
        return clean.contains("[Merger]", ignoreCase = true) ||
            clean.contains("Merging formats", ignoreCase = true)
    }

    fun humanError(raw: String?): String {
        if (raw.isNullOrBlank()) return "Download failed."
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        val error = lines.lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
            ?: lines.lastOrNull { !it.startsWith("WARNING:", ignoreCase = true) }
            ?: lines.lastOrNull()
            ?: "Download failed."

        return error.removePrefix("ERROR:").trim().take(320)
    }

    private fun clean(line: String?): String? {
        if (line.isNullOrBlank()) return null
        return ansiRegex.replace(line, "").trim()
    }
}
