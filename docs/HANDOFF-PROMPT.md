# Handoff prompt: Couch "Sky Home" launcher for a Vision Android 7 TV

Paste everything below into Antigravity / Freebuff (or any coding agent) with the repo open.

---

You are continuing work on **https://github.com/wakilibaraka/couch-android7tv**, branch **`android7-sky`**, open as **PR #1** (CI is green as of commit `3f0defb`). It is a personal, non-commercial fork of Samuel Loranger's Couch launcher (plain Kotlin + Android Views, no AndroidX/Compose), used with the author's permission. Keep the credit in README.

## The TV (hard constraints)
- Vision TV, Android 7.0 (API 24), MediaTek MT5863 on a CVTE board, weak GPU, 813 MB RAM, armeabi-v7a, 1920x1080 at 240 dpi.
- Remote: Vision WM-P100R (D-pad, OK, Back, Home, colour keys).
- ADB over Wi-Fi is on (no prompt; `adb root` works). IP is in `~/Desktop/TV Sideload/tv-ip.txt` on the Mac (last 192.168.68.108:5555).
- Never modify /system, never uninstall/disable CVTE packages, Couchy (`com.conreo.couchytv`), tv-detox or `com.example.user.myapplication` (they are backup homes). Native YouTube, EShare/AirPlay must stay.
- Keep it light: static images, no live blur, no per-frame effects.

## What is already done
1. **Android 7 port**: minSdk 24, core-library desugaring (java.time), Android 8+ calls guarded (fonts, preview channels, line height, notification access...), `values-v26` theme flag, `res/xml/network_security.xml` bundling ISRG/DigiCert/GTS/USERTrust/Amazon roots (Android 7.0 lacks Let's Encrypt). App id `io.github.wakilibaraka.couch` (installs beside upstream). Update check points at this repo's releases.
2. **CVTE input switching** (`data/CvteTv.kt`): binds `com.cvte.tv.api.TV_API_SERVICE` in `com.cvte.tv.api.impl` and uses reflection on boot-classpath `com.cvte.tv.api.aidl.*`: `getTVApiSystemInputSource().eventSystemInputSourceGetList()` / `eventSystemInputSourceSetInputSource(id)`, then `getTVApiScreenWindow().eventScreenWindowSetFull()` and starts `ACTION_MAIN` + category `android.intent.category.TV_HOME` with extra `isLauncherGoToTv=true` (the stock launcher's own recipe). Untested on the device yet. `InputSource.kt` uses it when available (TV/AV/HDMI 1-3).
3. **Sky layout** (default, `Config.LAYOUT_SKY`): `ui/SkyOverlay.kt` (big clock, date pill, weather line top-left; input pills + gear top-right; 2x2 quick tiles Wi-Fi / Picture (CVTE `com.cvte.intent.action.TV_MENU`) / Sound / Screensaver; photo credit), `ui/Glass.kt` (glass pills, white when focused), dock glass behind the first row (`home/HomeLayout.kt` `showSky/placeDock`), lighter `skyScrimDrawable`, row titles hidden, inputs row removed from rows. UI drawn on a 960x540dp canvas via `canvasContext()` in `ui/Dimens.kt` (the TV reports 240dpi).
4. **Weather-matched photos** (`data/SkyPhotos.kt`): wallpaper `"sky"` picks from `/sdcard/Android/data/io.github.wakilibaraka.couch/files/sky/<time>-<weather>/` (time: dawn/day/dusk/night from Open-Meteo sunrise/sunset; weather: clear/cloudy/rain/storm/fog) with fallbacks, changes hourly; `<photo>.txt` beside a photo = credit line. Falls back to Bing photo of the day. Weather city default `"Ogembo, KE"` (country code narrows the geocoder). Home reloads every 15 min. Unit tests in `app/src/test/.../SkyPhotosTest.kt`.
5. **Photos are done**: 28 real, openly licensed photos (CC BY / BY-SA / CC0, from Openverse and Wikimedia Commons), 2 per slot for 14 slots (dawn-clear, dawn-cloudy, day-clear, day-cloudy, day-rain, day-storm, day-fog, dusk-clear, dusk-cloudy, dusk-rain, night-clear, night-cloudy, night-rain, night-storm), each 1920x1080, 5.2 MB total, in `~/Desktop/TV Sideload/Sky photos/<slot>/1.jpg, 2.jpg` with `CREDITS.txt` and `contact-sheet.jpg`. Weak spots: night-rain (storm clouds, no visible rain), night-storm/1 and dusk-rain/2 are dark.
6. **APK downloaded** and verified (package io.github.wakilibaraka.couch 0.1.0, launcher activity `cloud.samlo.couch.MainActivity`): `~/Desktop/TV Sideload/Drop APKs here/couch-sky.apk`. On 2026-10-25 18:11 the user approved installing it; check whether it is already installed (`adb shell pm list packages | grep wakilibaraka`) and whether photos were pushed before redoing step 1-2 below.

## Next steps
1. Use `couch-sky.apk` above (or for a newer build, download the APK artifact from the latest green CI run on PR #1: Actions tab → run → artifact `couch-<sha>`), `adb install -r`, make it the Home app (see `~/Desktop/TV Sideload/Switch Home screen.command`), `adb shell pm grant io.github.wakilibaraka.couch android.permission.WRITE_SECURE_SETTINGS`. If Button Mapper remaps HOME to Couchy, point it at the new app.
2. Push photos to the sky folder above (one per slot is fine; 1920x1080 JPEG ≤450 KB), plus `<n>.txt` credits.
3. Screenshot (`adb exec-out screencap -p > s.png`) and logcat (`adb logcat -d | grep -iE "AndroidRuntime|FATAL|couch"`). Compare with the two Apple TV reference screenshots (big clock top-left, glass dock bottom, quick tiles top-right). Fix layout issues: dock panel position, focus order (Up from dock → tiles/pills), text sizes.
4. Verify input switching on the TV (HDMI pill → picture switches full screen). If the CVTE call fails, log the reflection exception in `CvteTv.show`.
5. CI signs with a random debug key per run, so updates need `adb uninstall io.github.wakilibaraka.couch` first, or add a fixed signing key via GitHub secrets (`COUCH_KEYSTORE_BASE64` etc. in `release.yml`).
6. Building: there is no local Android SDK. Push to `android7-sky`; CI (`.github/workflows/ci.yml`) runs assembleRelease + unit tests + lint (lint NewApi errors fail the build; keep Android 7 guards). APK budget 1.5 MB.

Rules: small commits, keep code style (short files, KDoc comments explaining why), don't add libraries, don't touch the TV's system apps.
