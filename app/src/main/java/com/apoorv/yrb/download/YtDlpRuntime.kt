package com.apoorv.yrb.download

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL

object YtDlpRuntime {
    private const val PREFS = "yt_dlp_runtime"
    private const val LAST_SUCCESS = "last_success"
    private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L

    @Synchronized
    fun ensureFresh(context: Context, force: Boolean = false): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSuccess = prefs.getLong(LAST_SUCCESS, 0L)
        val now = System.currentTimeMillis()

        if (!force && now - lastSuccess < CHECK_INTERVAL_MS) return true

        return try {
            YoutubeDL.getInstance().updateYoutubeDL(
                context.applicationContext,
                YoutubeDL.UpdateChannel.NIGHTLY
            )
            prefs.edit().putLong(LAST_SUCCESS, now).apply()
            true
        } catch (t: Throwable) {
            Log.w("YtDlpRuntime", "Could not refresh yt-dlp; bundled runtime will be used", t)
            false
        }
    }
}
