package com.apoorv.yrb

import android.app.Application
import android.os.Environment
import android.util.Log
import com.apoorv.yrb.data.HistoryStore
import com.apoorv.yrb.download.SessionStore
import com.apoorv.yrb.download.YtDlpRuntime
import java.io.File

class YrbApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        HistoryStore(this).markActiveInterrupted()
        cleanupAbandonedPartials()

        try {
            YtDlpRuntime.ensureInitialized(this)

            Thread {
                SessionStore(this).refreshFromWebViewDatabaseIfAvailable()
                Thread.sleep(60_000L)
                YtDlpRuntime.refreshIfDue(this)
            }.start()
        } catch (t: Throwable) {
            Log.e("YrbApplication", "Failed to initialize yt-dlp runtime", t)
        }
    }

    private fun cleanupAbandonedPartials() {
        runCatching {
            val root = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Yrb/.partial"
            )
            if (root.exists()) root.deleteRecursively()
        }.onFailure {
            Log.w("YrbApplication", "Could not clean abandoned partial downloads", it)
        }
    }
}
