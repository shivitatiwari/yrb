# Changelog

## 1.2.0

- Keep yt-dlp update checks off the normal metadata-fetch path.
- Retry metadata extraction with a nightly yt-dlp refresh only when extraction fails.
- Cache recent inspections for 10 minutes.
- Estimate selected video + audio size from bitrate and duration when YouTube does not publish filesize metadata.
- Parse modern yt-dlp percentage, speed and ETA output directly instead of relying on the wrapper's older regex.
- Poll actual bytes written under Downloads/Yrb/.partial/<job-id> every 500 ms for real local progress.
- Standardize yt-dlp progress output with a progress template.
- Detect multiple YouTube audio tracks and let the user choose the audio language before download.
- Carry the chosen language into the Downloading screen and History.
- Show only one loader while inspecting.
- Show 0 B rather than "Size unavailable" before bytes begin writing.
- Add "Made by Apoorv Sandilya" plus contact@apoorv.sbs, X @sandilyapoorv and Instagram apoorvsandilya.
- Preserve the v1.1 partial-download, cancellation, notification and process-recovery behavior.

## 1.1.0

- Refresh yt-dlp on-device from the nightly channel when the runtime is stale.
- Filter out DRM and YouTube/SABR format entries that do not expose a downloadable URL.
- Show available resolution file-size estimates before download.
- Start a download directly from the selected quality.
- Add a dedicated live Downloading screen.
- Show progress, current speed, ETA and bytes written.
- Show active downloads in History.
- Keep partial streams under Downloads/Yrb/.partial/<job-id>.
- Use yt-dlp fragment concurrency where supported.
- Merge separate video/audio streams locally with FFmpeg.
- Move only the finished file into Downloads/Yrb.
- Preserve cancellation state correctly.
- Mark interrupted jobs accurately after process restarts and clean abandoned partial files.
- Replace raw yt-dlp warning dumps with concise failure messages.
- Prevent duplicate foreground jobs.
- Refresh the Material 3 UI and dark/light theme.
- Add CI tests for quality ordering, file-size formatting, speed parsing and error sanitization.
