# Changelog

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
