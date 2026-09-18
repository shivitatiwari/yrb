# Yrb

Yrb is a native Android YouTube downloader. yt-dlp and FFmpeg run entirely on the phone.

## v1.4.0

The important change in v1.4.0 is **download-time recovery**.

A YouTube format can be visible during metadata inspection but later be rejected by Google Video Server with HTTP 403. Yrb no longer treats that first 403 as terminal.

When a recoverable media 403 happens:

1. the failed yt-dlp process is stopped
2. the failed partial attempt is discarded
3. Yrb refreshes/re-resolves the same requested resolution and audio language
4. alternate anonymous playback routes are tried automatically
5. the Download screen stays active and shows the retry stage

Current anonymous route order:

- default yt-dlp route
- IPv4 retry
- Safari HLS route
- web_embedded when the video allows embedding

The previous android_vr fallback was removed from the recovery ladder because current YouTube enforcement can return 403 for its useful media formats.

Yrb also gives yt-dlp a small bounded HTTP/fragment retry window before switching routes.

If every anonymous route is rejected, Yrb reports a concise error explaining that the video currently requires a valid PO token or authenticated session rather than dumping raw yt-dlp logs.

## Existing features

- 360p / 480p / 720p / 1080p / 1440p / 4K availability detection
- file-size estimates
- multi-audio language selection
- real local progress based on yt-dlp output + bytes written
- speed and ETA
- live History
- foreground notifications and cancellation
- on-device FFmpeg merging
- completed files in `Downloads/Yrb`
- creator/contact information for Apoorv Sandilya

## Android

- Application ID: `com.apoorv.yrb`
- versionCode: 5
- versionName: 1.4.0
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
