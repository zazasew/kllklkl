# TheBrief near-real-time police alerts

The phone no longer polls Politiloggen. The intended path is:

**Politiet official Atom feed -> push server (~60 sec) -> Firebase Cloud Messaging -> Android**

The server polls the official feed about once a minute, filters by the user's municipality and selected categories, translates the message to English, and sends FCM.

## Important limitation

A real FCM deployment necessarily needs a Firebase project/service-account credential and a public HTTPS URL for this server. Those credentials cannot safely be embedded in a public GitHub repository. The Android code treats the push URL as optional; if it is blank, the app still works normally but instant police push is disabled.

Set the Android Gradle property `POLICE_PUSH_SERVER_URL` to your deployed HTTPS server URL when you are ready. The server needs `GOOGLE_APPLICATION_CREDENTIALS` (or another supported Firebase Admin credential mechanism).

The official Politiloggen API/feed is still the source of truth; the server is only a low-latency delivery layer.
