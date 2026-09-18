package com.apoorv.yrb.download

object ProgressLineParser {
    private val speedRegex = Regex("""\bat\s+([0-9.]+)\s*([KMGT]?i?B)/s""", RegexOption.IGNORE_CASE)

    fun speedBytesPerSecond(line: String?): Long? {
        if (line.isNullOrBlank()) return null
        val match = speedRegex.find(line) ?: return null
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
}
