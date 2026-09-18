# Changelog

## 1.5.0

Release build verified by GitHub Actions.

- Add an in-app YouTube session browser.
- Let the user sign in directly on YouTube/Google pages inside Yrb.
- Add an explicit **Use this session** action after sign-in.
- Capture only the local browser cookie jar; Yrb does not receive the user's password.
- Convert the session to a private Netscape cookies file for yt-dlp.
- Store session material in Android no-backup app storage.
- Reuse the WebView user-agent with the saved session.
- Apply the saved session to both metadata inspection and media downloads.
- Keep manual cookies.txt import as a fallback.
- Keep remove/replace session controls in the main UI.
- Register the login browser as a non-exported Android activity.
- Add unit coverage for authenticated YouTube session-cookie detection.

## 1.4.0

- Recover automatically when a media download starts and later fails with HTTP 403.
- Re-resolve the same selected quality and audio language through alternate playback clients.
- Reset failed partial data before retrying to avoid corrupt resumptions.
- Show Switching playback route / retry stages instead of immediately failing the job.
- Preserve live progress/history/notification state across automatic retry attempts.
- Add bounded HTTP and fragment retries before switching routes.
- Prioritize default, IPv4, Safari HLS and web_embedded anonymous routes.
