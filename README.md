# Pick'em War Room Android — v0.3.6-mobile4

This is the Android companion for Pick'em War Room.

## Branding

The Android launcher now uses the supplied Pick'em War Room shield artwork, matching the Windows desktop launcher icon.

## Important architecture

The APK is a mobile client for the existing Pick'em War Room server. It does **not** run the Go backend or duplicate your data on the phone. Your Windows/server instance remains the single source of truth for picks, AI research, odds keys, notifications, results, bet tracking, and season history.

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

## Phone behavior

- Saves the War Room server URL on the device.
- Uses the responsive mobile web layout served by the current War Room server.
- JavaScript and DOM storage are enabled for the War Room UI.
- Same-server links remain inside the app.
- External AI/source links open in the phone's browser.
- Invalid HTTPS certificates are rejected rather than bypassed.
- HTTP is allowed for private LAN testing.
- Target SDK is 36.

## Build an APK with GitHub Actions

The repository contains `.github/workflows/build-apk.yml`.

The workflow builds a debug-signed APK suitable for private testing/sideloading. A future production release should use a persistent private signing key.
