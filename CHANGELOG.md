# Changelog

## 1.5.0

- Add one-time per-device YouTube session import.
- Validate standard Netscape cookies.txt files before accepting them.
- Require imported session files to contain YouTube/Google cookie domains.
- Copy the imported session into Android private no-backup storage.
- Automatically pass the private session to yt-dlp during inspection.
- Automatically pass the private session to yt-dlp during actual media downloads.
- Add Connected / Replace / Remove controls for the local session.
- Add Import/Replace session action directly on authentication-related errors.
- Do not commit, upload, log or bundle cookie contents.
- Add unit coverage for session-file validation.

## 1.4.0

- Recover automatically when a media download starts and later fails with HTTP 403.
- Re-resolve the same selected quality and audio language through alternate playback clients.
- Reset failed partial data before retrying.
- Preserve live progress/history/notification state across automatic retry attempts.
- Prioritize default, IPv4, Safari HLS and web_embedded anonymous routes.
