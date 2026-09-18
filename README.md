# Yrb

Yrb is a native Android YouTube downloader. yt-dlp and FFmpeg run entirely on the phone.

## v1.5.0

Yrb now supports a private per-device YouTube session.

When YouTube blocks anonymous extraction/downloads, the user can import a standard Netscape `cookies.txt` file once. Yrb immediately copies it into Android private no-backup storage and automatically passes it to yt-dlp for:

- video inspection
- format discovery
- download-time retries
- actual media downloads

The imported session is never committed to GitHub, bundled into the APK, uploaded to Yrb infrastructure, or shown in logs/UI.

Session state is visible in the Download screen:

- Not connected
- Connected / stored only on this device
- Replace session
- Remove session

If YouTube returns a sign-in/bot/authenticated-session error, the error card exposes an Import/Replace YouTube session action directly.

A normal Google OAuth token is not a replacement for YouTube browser cookies, and Google sign-in inside an embedded WebView is not used as the authentication mechanism. Yrb instead uses yt-dlp's supported cookies-file path.

## Existing behavior

- Automatic anonymous fallback routes for recoverable YouTube failures
- Download-time HTTP 403 recovery
- 360p / 480p / 720p / 1080p / 1440p / 4K quality detection
- file-size estimates
- multi-audio language selection
- local progress, speed, ETA and bytes written
- foreground download notification and cancellation
- active/completed History
- on-device FFmpeg merging
- completed files in `Downloads/Yrb`
- creator/contact information for Apoorv Sandilya

## Session storage

Imported cookies are stored at an app-private path under Android's no-backup directory. The file is readable only by Yrb under the normal Android application sandbox and is not included in Android backup.

## Android

- Application ID: `com.apoorv.yrb`
- versionCode: 6
- versionName: 1.5.0
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
