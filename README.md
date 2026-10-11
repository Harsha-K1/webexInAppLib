# webexInAppLib

Android library (`.aar`) that opens a Webex meeting link inside your Volt MX app with
working camera and microphone.

## Why the stock browser widget fails on Android

The Volt MX browser widget uses a WebView without a `WebChromeClient.onPermissionRequest`
handler, so every `getUserMedia()` request from Webex is denied, regardless of the
permissions in Android settings. It also never asks for the runtime permissions itself.
This library hosts its own WebView in an Activity that:

1. requests `CAMERA` / `RECORD_AUDIO` (and `BLUETOOTH_CONNECT` on Android 12+) at runtime,
2. grants the page's media request via `onPermissionRequest`,
3. keeps `webex://` / `intent://` links inside the WebView, and
4. uses a desktop user agent (optional) so Webex serves "join from browser".

## Build the AAR

Open the folder in Android Studio (or run `gradle wrapper` once, then):

```
./gradlew :webexlib:assembleRelease
```

Output: `webexlib/build/outputs/aar/webexlib-release.aar`

## Use in Volt MX

1. In Volt MX Iris: **Project > Native Functions / Android > Add a library** (the project's
   `Android/libs` area) and add `webexlib-release.aar`.
2. The meeting is shown in a full-screen dialog on your current Activity, so **no manifest
   activity is needed**. Make sure the app declares `CAMERA`, `RECORD_AUDIO`,
   `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH_CONNECT` and `INTERNET` (the AAR declares them; add
   them in your Iris Android manifest settings if they are not merged).
3. Call from Iris JS (Android only; keep iOS on the browser widget):

```js
if (kony.os.deviceInfo().name === "android") {
  var WebexMeeting = java.import("com.webexinapp.lib.WebexMeeting");
  var KonyMain = java.import("com.konylabs.android.KonyMain");
  var ok = WebexMeeting.open(KonyMain.getActivityContext(), "https://yourco.webex.com/meet/room");
  // ok === false -> no Activity context; see Logcat tag "WebexMeeting"
  // or: WebexMeeting.open(ctx, url, "Standup", true /* desktop user agent */);
}
```

Note: the class and method names above are not verified against a running Volt MX build.
