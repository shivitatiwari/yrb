# YRB by Apoorv

YRB by Apoorv is a native Android YouTube downloader. yt-dlp and FFmpeg run on the phone; Yrb does not proxy video through a server.

## v1.8.2

v1.8.2 reduces distribution size without changing Yrb's yt-dlp/Python/FFmpeg downloader architecture.

### Size optimization

- Publish separate installable APKs for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64` instead of one universal APK carrying every native architecture.
- The `arm64-v8a` APK is the primary build for modern Android phones.
- Enable R8 code shrinking and Android resource shrinking for optimized direct-install builds and the release bundle.
- Replace the full `material-icons-extended` dependency with eight local vector icons used by Yrb.
- Preserve yt-dlp, its bundled Python runtime, FFmpeg, language selection, audio-only downloads, high-resolution merging, History and live progress.

### Loading timing

- Inspection and pre-transfer preparation states now rotate every **4.7 seconds**.

## v1.8.0

v1.8.0 improves perceived responsiveness and adds first-class audio-only downloads without moving media through a server.

### Loading experience

- Inspect now cycles through clear on-device states every 2.7 seconds: video details, video streams, audio tracks, languages, quality options and final choices.
- The download screen shows a separate preparation sequence while the job has not written its first media bytes.
- Once real download bytes/progress arrive, the UI switches back to real progress, speed, ETA and written bytes.

### Audio-only

- Every detected audio language can expose an **Audio only** choice.
- The selected audio stream uses the same yt-dlp session, retry, progress, History and notification pipeline as video.
- Audio-only downloads are finalized as M4A with FFmpeg on-device.
- Completed audio opens with its real MIME type.

### Branding

- Launcher/app name: **YRB by Apoorv**
- Application ID remains **com.apoorv.yrb** so installed-app identity stays stable.
- Creator signature remains **Made by Apoorv Sandilya** with the existing contact links.

## v1.7.0

v1.7.0 simplifies Yrb around one fast authenticated yt-dlp path instead of stacking normal-path client fallbacks.

### Fast metadata path

When a YouTube session is connected, Yrb performs one metadata request with:

- `--dump-single-json`
- one retry
- 5 second socket timeout
- the saved browser cookies
- the matching WebView User-Agent
- `youtube:player_client=default,web_embedded`

The authenticated client selection follows the current yt-dlp workaround for the logged-in `tv_downgraded` "page needs to be reloaded" failure.

Successful inspections are cached in memory for 10 minutes. The cache key includes the current session file timestamp, so refreshing the browser session invalidates stale metadata automatically.

Yrb also refreshes yt-dlp in the background when the app starts rather than putting updater latency in front of the metadata request.

Network and YouTube response time are outside the app's control, so a hard 2 second guarantee is not possible, but the normal path now performs only one extractor request instead of sequential client probing.

### Browser session

Yrb's in-app YouTube sign-in still keeps the user's password inside Google's/YouTube's page.

The browser session export now reads Android WebView's cookie database and preserves the cookie:

- domain
- path
- secure flag
- expiry
- name
- value

Only YouTube/Google/GoogleVideo domains are exported. The resulting Netscape cookie file stays in Android private no-backup storage.

Existing v1.5/v1.6 browser sessions are re-exported automatically from the WebView database on startup when possible.

If the database snapshot is unavailable, Yrb falls back to Android's CookieManager.

### Download pipeline

yt-dlp now owns both the temporary and final paths:

`Downloads/Yrb/.partial/<job-id>/` — temporary fragments

`Downloads/Yrb/` — finished file

For separate video/audio streams, yt-dlp downloads the streams locally and FFmpeg merges them on-device. Yrb monitors the temporary directory every 500 ms for live bytes/speed/progress and uses yt-dlp's own `after_move` path as the finished file.

Zero-byte output and yt-dlp's "downloaded file is empty" error are treated as recoverable failures instead of valid completed downloads.

### Existing features

- 360p / 480p / 720p / 1080p / 1440p / 4K availability detection
- exact or estimated file sizes
- multi-audio language selection
- live local progress, speed, ETA and written bytes
- foreground download notification and cancellation
- active/completed History
- completed-file open action
- creator/contact information for Apoorv Sandilya

## Android

- Application ID: `com.apoorv.yrb`
- versionCode: 11
- versionName: 1.8.2
- minSdk: 30
- targetSdk: 36
- compileSdk: 36
- Kotlin + Jetpack Compose + Material 3
- JDK 17 / Gradle 8.13 / AGP 8.13.2
- youtubedl-android 0.18.1

## Creator

Made by Apoorv Sandilya

- Email: contact@apoorv.sbs
- X: @sandilyapoorv
- Instagram: apoorvsandilya

## Build

CI runs:

```bash
gradle testDebugUnitTest lintDebug assembleDirect bundleRelease
```

The repository contains no production signing key. CI publishes ABI-specific optimized direct-install APKs signed with the CI debug key plus an unsigned release AAB until production signing credentials are supplied securely.

Only download content you own, are authorized to download, or that the service permits you to download.
