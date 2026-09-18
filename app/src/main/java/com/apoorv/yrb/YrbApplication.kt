package com.apoorv.yrb

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class YrbApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (t: Throwable) {
            Log.e("YrbApplication", "Failed to initialize yt-dlp runtime", t)
        }
    }
}
