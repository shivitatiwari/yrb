# Yrb

Yrb is a native Android YouTube downloader. yt-dlp and FFmpeg run on the phone; Yrb does not proxy media through a server.

## v1.1.0

- Refreshes the yt-dlp executable on-device from the nightly channel when the local copy is stale.
- Inspects a YouTube URL before downloading.
- Shows only downloadable 360p, 480p, 720p, 1080p, 1440p and 4K/2160p options.
- Shows an exact or approximate video + audio file size when YouTube exposes enough metadata.
- Tapping a quality creates the job immediately and opens a dedicated Downloading screen.
- Live Downloading screen shows stage, progress, speed, ETA and bytes written.
- Active jobs appear in History while they are still downloading.
- Foreground notification mirrors progress, speed and ETA and supports cancellation.
- Completion notification opens the completed video using Android's selected video player.
- Keeps up to 150 local history records.
- Uses concise human-readable errors instead of dumping yt-dlp warning logs into notifications.
- Uses a neutral Material 3 light/dark UI.
- No account, analytics, ads, media proxy or cloud storage.

## Download method

Yrb creates a job and hands an exact yt-dlp format selector to the on-device yt-dlp runtime.

Temporary work is written to:

`Downloads/Yrb/.partial/<job-id>/`

Depending on the format, yt-dlp may download a single progressive stream or fragmented/DASH media. For higher YouTube resolutions, video and audio are commonly separate streams. yt-dlp downloads those streams locally and FFmpeg merges them on-device. Yrb uses four concurrent fragments where the selected extractor supports fragmented downloading.

When processing is complete, the final media file is moved into:

`Downloads/Yrb/`

The temporary job directory is then removed. Nothing is uploaded to a Yrb server.

## Android

- Application ID: `com.apoorv.yrb`
- versionCode: 2
- versionName: 1.1.0
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

Installable development APK:

`app/build/outputs/apk/debug/app-debug.apk`

Release bundle output:

`app/build/outputs/bundle/release/app-release.aab`

The repository contains no production signing key. CI publishes an installable debug APK and an unsigned release AAB until production signing credentials are supplied securely.

## Storage and privacy

Finished media is public in `Downloads/Yrb`. Partial media is kept under `Downloads/Yrb/.partial` only while a job is active. Download history and runtime-update timestamps stay in private app storage.

Only download content you own, are authorized to download, or that the service permits you to download.

## yt-dlp runtime

The Android host runtime uses `io.github.junkfood02.youtubedl-android 0.18.1` plus its FFmpeg artifact. The host library can refresh the yt-dlp executable itself in-app, which Yrb uses so YouTube extractor fixes do not require waiting for a new Android library release.
