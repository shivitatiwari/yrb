# Changelog

## 1.4.0

- Recover automatically when a media download starts and later fails with HTTP 403.
- Re-resolve the same selected quality and audio language through alternate playback clients.
- Reset failed partial data before retrying to avoid corrupt resumptions.
- Show Switching playback route / retry stages instead of immediately failing the job.
- Preserve live progress/history/notification state across automatic retry attempts.
- Add bounded HTTP and fragment retries before switching routes.
- Remove android_vr from the anonymous recovery ladder because current YouTube enforcement can reject its media URLs.
- Prioritize default, IPv4, Safari HLS and web_embedded anonymous routes.
- Add unit coverage that classifies YouTube 403 download failures as recoverable.

## 1.3.0

- Added zero-login anonymous YouTube fallback ladder.
- Kept normal/default extraction first for every new URL.
- Added IPv4 retry.
- Added web_safari fallback.
- Added web_embedded fallback.
- Carried successful extractor settings into download requests.
