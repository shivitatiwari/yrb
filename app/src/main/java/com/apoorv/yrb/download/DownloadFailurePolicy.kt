package com.apoorv.yrb.download

object DownloadFailurePolicy {
    fun isRecoverable(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
            .lowercase()

        if (
            message.contains("downloaded file is empty") ||
            message.contains("empty output") ||
            message.contains("zero-byte")
        ) {
            return true
        }

        return YtDlpClient.isRecoverableDownloadError(error)
    }
}
