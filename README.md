# Yrb

Yrb is a native Android YouTube downloader. yt-dlp and FFmpeg run entirely on the phone.

## v1.5.0

Yrb can now use the user's own YouTube browser session without requiring an external cookies.txt export.

### Connect YouTube

1. Tap **Sign in to YouTube** inside Yrb.
2. Yrb opens a private in-app browser pointed at YouTube.
3. Sign in on Google's/YouTube's own page.
4. When YouTube shows the account as signed in, tap **Use this session**.
5. Yrb converts the local WebView cookie jar into a Netscape-format cookies file stored under Android private no-backup storage.
6. Metadata inspection and media downloads automatically pass that private cookie file to yt-dlp.
7. Yrb also reuses the same WebView user-agent because yt-dlp notes that authenticated/session-sensitive downloads can depend on matching cookies and request headers.

Yrb never receives the user's Google password. No JavaScript bridge is injected into the login page, and the saved session is not uploaded to a backend, committed to GitHub, or included in release assets.

Manual **Import cookies.txt** remains available as a fallback.

Google can restrict embedded WebView sign-in on some accounts/devices. If that happens, the manual cookie-file import path remains the reliable fallback because yt-dlp's current YouTube documentation says OAuth login no longer works and cookies are required.

## Existing features

- 360p / 480p / 720p / 1080p / 1440p / 4K availability detection
- file-size estimates
- multi-audio language selection
- real local progress based on yt-dlp output + bytes written
- speed and ETA
- live History
- foreground notifications and cancellation
- automatic recovery across alternate playback routes after recoverable 403s
- on-device FFmpeg merging
- completed files in `Downloads/Yrb`

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
