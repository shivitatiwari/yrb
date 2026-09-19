# Changelog

## 1.9.0

- Cache successful raw yt-dlp inspection JSON for 10 minutes in app-private storage.
- Reuse cached inspection data for the selected download through `--load-info-json` instead of extracting the same video twice.
- Pass cached inspection paths through video, combined-format and audio-only quality options.
- Remove forced yt-dlp update + duplicate extraction from the normal interactive Inspect failure path.
- Disable normal-path metadata retries and format probing checks; use a 4-second socket timeout.
- Delay the daily background yt-dlp updater by 60 seconds and stop runtime initialization from waiting on its synchronized lock.
- Keep existing recovery extraction for genuinely rejected/expired media routes.
- Preserve ABI-specific APKs, yt-dlp/Python/FFmpeg, audio languages, quality selection, progress, History and the 4.7-second staged loading UI.
- Bump versionCode to 13 and versionName to 1.9.0.

## 1.8.3

- Fix the optimized APK regression where yt-dlp reported "instance not initialized".
- Explicitly enable native-library extraction required by youtubedl-android.
- Add idempotent yt-dlp + FFmpeg initialization before inspect and download paths.
- Keep ABI-split APKs and local vector icons.
- Disable R8/resource shrinking for direct APKs to prioritize runtime correctness.
- Bump versionCode to 12 and versionName to 1.8.3.

## 1.8.2

- Split direct-install APKs by ABI: arm64-v8a, armeabi-v7a, x86 and x86_64.
- Make arm64-v8a the primary modern-phone distribution instead of shipping a universal APK with every native architecture.
- Enable R8 code shrinking and resource shrinking for optimized builds.
- Replace material-icons-extended with eight local vector assets.
- Keep yt-dlp, bundled Python and FFmpeg unchanged so download behavior is preserved.
- Keep both staged loading rotations at 4.7 seconds.
- Bump versionCode to 11 and versionName to 1.8.2.

## 1.8.1

- Slow both staged loading-state rotations from 2.7 seconds to 4.7 seconds.
- Bump versionCode to 10 and versionName to 1.8.1.

## 1.8.0

- Rename the visible app branding to **YRB by Apoorv** while keeping application ID `com.apoorv.yrb`.
- Replace the single inspection spinner with rotating status states every 2.7 seconds.
- Add separate pre-transfer loading states on the download screen until real bytes/progress arrive.
- Add language-aware audio-only download choices.
- Finalize audio-only downloads as M4A on-device.
- Reuse the existing yt-dlp session, retry, History, notification and cancellation pipeline for audio-only jobs.
- Open completed audio/video files using their actual MIME type.
- Keep the creator signature and contact links for Apoorv Sandilya.
- Bump versionCode to 9 and versionName to 1.8.0.

## 1.7.0

- Rebuild the normal YouTube path around one authenticated yt-dlp request.
- Use the current authenticated YouTube client workaround: `default,web_embedded`.
- Stop doing sequential extractor-client probing before normal metadata results.
- Read the Android WebView cookie database instead of flattening only youtube.com cookies.
- Preserve cookie domain, path, secure flag and expiry.
- Restrict exported browser cookies to YouTube/Google/GoogleVideo domains.
- Reuse the exact WebView User-Agent as a request header for metadata and downloads.
- Automatically migrate an existing in-app browser session to the improved cookie format.
- Run yt-dlp refresh in the background at app startup.
- Reduce metadata socket timeout to 5 seconds with one retry.
- Key metadata cache by both URL and current session file timestamp.
- Treat "The page needs to be reloaded" as recoverable metadata state.
- Treat yt-dlp "The downloaded file is empty" as a recoverable download failure.
- Let yt-dlp own temporary and final download paths.
- Keep partial fragments under `Downloads/Yrb/.partial/<job-id>`.
- Let yt-dlp/FFmpeg move the completed file directly into `Downloads/Yrb`.
- Remove the duplicate connected-session status sentence from the home screen.
- Preserve quality detection, file-size estimates, audio-language selection, live progress, History and notifications.

## 1.6.0

- Validate completed media inside each download attempt before leaving the recovery loop.
- Treat zero-byte output as a failed playback route rather than finalizing a broken file.
- Put YouTube connection state ahead of download controls when a session is required.
- Link authentication errors directly back to the in-app sign-in flow.

## 1.5.0

- Add an in-app YouTube session browser.
- Convert the user's local browser session into a private cookies file for yt-dlp.
- Reuse the browser User-Agent.
- Apply the session to metadata inspection and downloads.
- Keep manual cookies.txt import as a fallback.

## 1.4.0

- Add automatic recovery for recoverable YouTube media HTTP 403 failures.
- Preserve live progress/history/notification state across retry attempts.
