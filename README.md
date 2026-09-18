# Yrb

Yrb is a native Android YouTube downloader. yt-dlp and FFmpeg run on the phone; Yrb does not proxy media through a server.

## v1.3.0

Yrb keeps the normal/default yt-dlp path fast. If YouTube blocks anonymous extraction or returns recoverable 403/format errors, Yrb automatically tries documented anonymous fallback paths in this order:

1. default yt-dlp client
2. default client forced to IPv4
3. `web_safari`, preferring HLS formats
4. `android_vr`
5. `web_embedded` for videos that permit embedding

If the first pass fails for a recoverable YouTube error, Yrb refreshes yt-dlp from the nightly channel and repeats the fallback ladder once.

The successful extractor mode is attached to each quality option and reused by the download service, so inspection and download do not silently use different YouTube clients.

These fallbacks improve zero-login resilience but do not guarantee that every server-side YouTube bot/auth challenge can be satisfied anonymously.

## Existing behavior

- 10-minute inspection cache
- downloadable 360p, 480p, 720p, 1080p, 1440p and 4K/2160p options
- exact or estimated file sizes
- multi-audio language selection
- live progress, speed, ETA and bytes written
- foreground notification with cancellation
- History for active/completed jobs
- on-device yt-dlp + FFmpeg only
- output to `Downloads/Yrb`
- creator/contact information for Apoorv Sandilya

## Download method

Temporary work is written to:

`Downloads/Yrb/.partial/<job-id>/`

Yrb samples actual bytes written every 500 ms and combines that with yt-dlp's raw progress output. Separate video/audio streams are merged on-device with FFmpeg. The completed file is moved into `Downloads/Yrb/`.

## Android

- Application ID: `com.apoorv.yrb`
- versionCode: 4
- versionName: 1.3.0
- minSdk: 30
- targetSdk: 36
- compileSdk: 36
- Kotlin + Jetpack Compose + Material 3
- JDK 17 / Gradle 8.13 / AGP 8.13.2

## Creator

Made by Apoorv Sandilya

- Email: contact@apoorv.sbs
- X: @sandilyapoorv
- Instagram: apoorvsandilya

## Build

CI runs:

```bash
gradle testDebugUnitTest lintDebug assembleDebug bundleRelease
```

The repository contains no production signing key. CI publishes an installable debug APK and an unsigned release AAB until production signing credentials are supplied securely.

Only download content you own, are authorized to download, or that the service permits you to download.
