# Pick'em War Room Android — v0.3.1-mobile1

This is the Android companion for Pick'em War Room v0.3.1.

## Important architecture

The APK is a mobile client for the existing Pick'em War Room server. It does **not** run the Go backend or duplicate your data on the phone. Your Windows/server instance remains the single source of truth for picks, AI research, odds keys, notifications, results, and bet tracking.

That is intentional: you can make a pick on your phone and immediately see the same change on your desktop.

## Recommended remote connection

Keep Pick'em War Room bound to localhost and expose it privately with Tailscale Serve:

```powershell
tailscale serve --bg http://127.0.0.1:8765
```

Tailscale will show an HTTPS URL similar to:

```text
https://your-pc.your-tailnet.ts.net
```

Install Tailscale on the Android phone, sign into the same tailnet, then paste that HTTPS URL into the Android app on first launch.

You can also use a LAN URL such as `http://192.168.1.25:8765` if the server is configured to listen on the LAN. The current Windows War Room build listens on localhost by default, so Tailscale Serve is the safer/easier route.

## Phone behavior

- Saves the War Room server URL on the device.
- JavaScript and DOM storage are enabled for the War Room UI.
- Same-server links remain inside the app.
- External AI/source links open in the phone's browser.
- Invalid HTTPS certificates are rejected rather than bypassed.
- HTTP is allowed for private LAN testing.
- Target SDK is 36.

## Build an APK with GitHub Actions

The repository contains `.github/workflows/build-apk.yml`.

1. Open the repository's **Actions** tab.
2. Run **Build Pick'em War Room APK**, or push a change to `main`.
3. Download the artifact named `PickemWarRoom-Android-v0.3.1-mobile1`.
4. Extract and sideload the APK onto Android.

The workflow builds a debug-signed APK, which is suitable for private testing/sideloading. A future production release should use a persistent private signing key.

## Build locally

Open the folder in Android Studio and build the `app` module, or install Android SDK 36 + Gradle 8.11.1 and run:

```bash
gradle :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```
