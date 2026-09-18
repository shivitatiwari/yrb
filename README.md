# Yrb

Yrb is a native Android YouTube downloader. yt-dlp and FFmpeg run on the phone; Yrb does not proxy media through a server.

## v1.2.0

- Fast metadata path: Yrb uses the current working yt-dlp immediately and only performs a network updater + retry if extraction fails.
- Recent inspection results are cached for 10 minutes.
- Shows only downloadable 360p, 480p, 720p, 1080p, 1440p and 4K/2160p options.
- Shows exact or approximate video + audio file sizes. When YouTube omits a filesize, Yrb estimates it from stream bitrate and video duration.
- Detects multi-audio YouTube videos and shows an Audio language selector only when there is more than one language.
- Tapping a quality immediately creates the job and opens the Downloading screen.
- Progress is driven by both raw yt-dlp output and the actual bytes physically written under the job's partial directory.
- Downloading and History show percentage, speed, ETA, bytes written, quality and selected language.
- Foreground notification mirrors the live transfer and supports cancellation.
- Completion notification opens the video in Android's chosen video player.
- Keeps up to 150 local history records.
- Uses a neutral Material 3 light/dark UI.
- Includes creator/contact links for Apoorv Sandilya.
- No account, analytics, ads, media proxy or cloud storage.

## Download method

Yrb runs yt-dlp locally inside the Android app.

Temporary work is written to:

`Downloads/Yrb/.partial/<job-id>/`

Depending on the selected format, yt-dlp may download a single progressive stream or fragmented/DASH media. Higher YouTube resolutions commonly use separate video-only and audio-only streams. yt-dlp downloads those locally and FFmpeg merges them on-device. Yrb requests up to four concurrent fragments where the extractor supports fragmented downloading.

Every 500 ms Yrb samples the actual bytes currently written to the job directory. That local byte count is combined with yt-dlp's raw progress output to drive percentage, speed and ETA. Nothing is uploaded to a Yrb server.

When processing is complete, the final media file is moved into:

`Downloads/Yrb/`

The temporary job directory is removed.

## Android

- Application ID: `com.apoorv.yrb`
- versionCode: 3
- versionName: 1.2.0
- minSdk: 30 (Android 11)
- targetSdk: 36 (Android 16)
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

Installable development APK:

`app/build/outputs/apk/debug/app-debug.apk`

Release bundle output:

`app/build/outputs/bundle/release/app-release.aab`

The repository contains no production signing key. CI publishes an installable debug APK and an unsigned release AAB until production signing credentials are supplied securely.

Only download content you own, are authorized to download, or that the service permits you to download.
