# Yrb

Yrb is a native Android YouTube downloader that runs yt-dlp on the user's phone. There is no media download backend or proxy.

## v1.0.0 features

- Inspects a YouTube URL before downloading.
- Shows only available 360p, 480p, 720p, 1080p, 1440p and 4K/2160p options.
- Uses on-device yt-dlp and FFmpeg.
- Saves finished videos to `Downloads/Yrb`.
- Runs downloads in an Android foreground service.
- Shows progress and ETA in the download notification.
- Supports cancellation directly from the notification.
- Shows a completion notification that opens the video with Android's chosen/default video player.
- Keeps the latest 100 completed, failed or cancelled downloads in private local history.
- History entries can reopen files that still exist.
- No account, analytics, ads, server download proxy or cloud storage.

## Android

- Application ID: `com.apoorv.yrb`
- minSdk: 30 (Android 11)
- targetSdk: 36 (Android 16)
- compileSdk: 36
- Kotlin + Jetpack Compose + Material 3
- JDK 17 / Gradle 8.13 / AGP 8.13.2

## Build

CI runs:

```bash
gradle testDebugUnitTest lintDebug assembleDebug bundleRelease
```

Installable debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

Release bundle output:

`app/build/outputs/bundle/release/app-release.aab`

The repository does not contain production signing credentials. CI therefore publishes the installable debug APK and an unsigned release AAB. Production Play signing must be supplied through secure GitHub Actions secrets before Play submission.

## Storage and privacy

Yrb writes finished media to the public `Downloads/Yrb` directory. Download history stays in the app's private storage. Network traffic is the traffic required for yt-dlp/YouTube extraction and media downloading.

Only download content you own, are authorized to download, or that the service permits you to download.

## yt-dlp runtime

The Android runtime uses `io.github.junkfood02.youtubedl-android` plus its FFmpeg artifact. Review and comply with upstream licensing before redistribution.
