# Changelog

## 1.3.0

Release build verified by GitHub Actions.

- Add documented anonymous YouTube fallback ladder.
- Keep normal/default extraction first for every new URL.
- Retry the default path with IPv4.
- Add `web_safari` fallback and prefer its HLS formats.
- Add `android_vr` fallback.
- Add `web_embedded` fallback for embeddable videos.
- Refresh yt-dlp and retry the ladder once after recoverable anonymous extraction failures.
- Carry the successful extractor arguments into the actual download request.
- Keep normal videos on the fast path with no extra fallback requests.
- Add a unit test locking fallback order.

## 1.2.0

Release build verified by GitHub Actions.

Released: 2026-09-18

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
