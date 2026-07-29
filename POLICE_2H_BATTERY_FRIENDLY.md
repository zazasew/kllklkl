# Police alerts — 2-hour battery-friendly mode

TheBrief now uses local Android WorkManager polling for Norwegian Police operational messages.

- Runs every 2 hours (WorkManager periodic work is inexact and may be delayed by Android).
- Requires an active network connection.
- Requires Battery Not Low.
- No foreground service.
- No wake lock.
- First run establishes a baseline so existing incidents do not create a notification storm.
- Later runs notify only for incident IDs that have not been seen before.
- Municipality and category filters are taken from Settings.
- Norwegian incident text is translated to English online, with the Norwegian source retained.
- Notification opens the incident's published URL when the API supplies one; otherwise it opens the official Politiloggen page.
- YouTube polling remains disabled; its existing push architecture is not scheduled by WorkManager.
- Weather forecast is cached for 30 minutes in-process, with MET Norway as the primary source and Open-Meteo as a cross-check.

Android may defer WorkManager beyond two hours during Doze, restricted background activity, or other system conditions. This is intentional for battery preservation.
