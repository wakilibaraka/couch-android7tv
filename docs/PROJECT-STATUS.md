# Couch Sky project status (shelved 2026-10-26)

This file is a snapshot of the Couch Sky launcher project for Emmanuel's Vision Android 7 TV. It covers what was built, what is still pending, and the requests and ideas behind it, so the work can be picked up again later.

## Where things stand
- `main` builds green on CI. The last change on the TV was PR #9.
- The TV runs build `9da64b5` (app id `io.github.wakilibaraka.couch`), set as the default Home.
- **Layout:** the classic Couch **rows** layout is the default (PR #8). It has an Apps row with YouTube first and an Inputs row (TV, AV, HDMI 1‑3). The weather, a settings gear and the clock sit at the top right.
- **Screensaver:** 24 public-domain paintings stored on the TV in `files/art/`, shown after 5 minutes idle. Nothing streams.
- **RAM:** about 12 MB after boot and about 26 MB in use. No crashes were logged.
- **Couchy:** deleted at the user's request. He doesn't want it.

## What was done
1. **Android 7 port of Couch:**
   - minSdk 24, with desugaring and guarded API 26+ calls.
   - Bundled root CAs, because Android 7.0 lacks Let's Encrypt.
   - Its own app id, so it installs beside upstream Couch.
2. **CVTE integration:** input switching via TvApiService reflection, and the Picture and Sound tiles reflect the CVTE settings. Both work on the TV.
3. **Sky layout** (still in the app, switchable in settings): see the Sky layout section below.
4. **Weather:** Open-Meteo for Ogembo in °C, with 7 hours in 2-hour steps (02:00, 04:00…) and 7 days.
5. **Wallpaper:** the gradient is the default, and ambient mode prefers local paintings in `files/art`.
6. **Dock order:** pinned packages first, then label matching (YouTube, SmartTube, TV Bro, X-plore, VLC, LocalSend, Aurora).
7. **MENU key:** pressing MENU on the home screen opens the launcher settings (PR #9).
8. **Load retry:** if the home rows fail to build or come up empty right after boot, the launcher tries again after 3 s (PR #9).

### Sky layout
- A clock coloured by time of day, the date and a weather line.
- A forecast card.
- An Inputs drop-down, Launcher settings and Android settings.
- Sound, Picture and Wi‑Fi tiles.
- A status line showing net speed, RAM and storage.
- A glass dock with a glow that can be toggled.
- Up from the dock jumps to the top controls.

## Pending and known issues
- **Rows layout:**
  - No focus outline was visible on cards in one screenshot (it showed on YouTube after a reboot). Check the focus ring.
  - A sixth app card is cut off at the right edge.
- **SmartTube** sometimes doesn't appear in the dock. Check its launcher label and category.
- **Neumorphism redesign:** handed off to Antigravity (see "Handoff documents" below). The user found the sky layout cluttered and hard to read, and wants a soft, readable neumorphic style. Make a mockup first.
- **Layout v4 mockup**, not built: https://claude.ai/artifact/1Bn3ZHK7TfR7iHQKbAv5dv
  - An Apple-style weather widget with two page dots (hours and days).
  - Volume and mute removed.
  - A right column of Launcher | TV settings, Wi‑Fi | Files, Sound | Picture, EShare | AirPlay, then all inputs on one strip.
  - The rows layout was chosen instead for now.
- **Photo credits:** CC BY credits for the old sky photos were hidden. Restore them if those photos come back.

## Emmanuel's requests and ideas (to keep in mind)
- **Look:** Apple TV / LG inspired, clean and readable at a glance, with symmetric layouts and no overflow.
- **Fonts:** SF-like (Hanken Grotesk is bundled), with a colourful clock.
- **Speed first:** Android 7, 813 MB of RAM, a weak GPU. No live blur and no per-frame effects.
- **Weather:** 7 hours and 7 days, temperatures shown as "23°/14°", with dynamic, animated icons.
- **Wi‑Fi tile:** shows the network name and opens Wi‑Fi settings. It never toggles Wi‑Fi.
- **Two separate settings buttons:** Launcher settings and Android/TV settings.
- **Screensaver:** dynamic art that stays local on the TV, with no streaming.
- **No mod APKs.**

## TV and setup notes (from the other threads)
- **Hardware:** Vision TV, Android 7.0 (API 24), MediaTek MT5863 on a CVTE board (`cvt_mt5863_eu`), 813 MB of RAM, WebView 51 (locked). Remote: Vision WM-P100R.
- **ADB:**
  - ADB over Wi‑Fi on port 5555 needs no prompt, and `adb root` works.
  - The TV's IP changes. The host part is always `.108`, and the current address is in `~/Desktop/TV Sideload/tv-ip.txt` (last seen 192.168.102.108).
  - Leave USB and Wi‑Fi debugging ON.
- **Never** disable `com.talents.huanclientauth` (the TV hangs at boot). Don't remove CVTE packages, EShare/AirPlay, native YouTube or `com.example.user.myapplication`.
- **HOME button:** Button Mapper maps the physical HOME key to its own Home action, because CVTE otherwise toggles HOME to the TV tuner.
  - Never send `adb shell input keyevent 3`. It bypasses Button Mapper.
  - To show Home over adb, use `am start -a android.intent.action.MAIN -c android.intent.category.HOME`.
  - The other remote keys have recorded key codes but are not mapped. The user wants to be asked before any more remapping.
- **Installing a CI build:** each build has a new signing key, so it needs `adb uninstall`, then `adb install`, then:
  - `pm grant io.github.wakilibaraka.couch android.permission.WRITE_SECURE_SETTINGS`
  - `cmd package set-home-activity io.github.wakilibaraka.couch/cloud.samlo.couch.MainActivity`
  - Back up `files/art` and push it again afterwards. A copy is on the Mac in `~/Desktop/TV Sideload/Screensaver art/`.
- **Backups:** partition backups in `~/Desktop/visionplus-tv-backup/`, and the sideload kit in `~/Desktop/TV Sideload/`.
- **Play Store:** sign-in loops on 2FA because of WebView 51. Use Aurora Store (anonymous), or register the GSF ID at google.com/android/uncertified and use an app password.
- **Plan B launcher:** Gathin23/cleanyourtv (MIT) needs Android 8+, so it would have to be ported. Its Weather, Capacity Stats, AirPlay Status, Sources and Quiet Hours add-ons are worth borrowing. `tools/bench.py` is useful for measuring RAM and lag.

## Plans and handoff documents
- TV setup plan: https://claude.ai/artifact/64itmPZRmdFHTsfC2iVmBx
- Multitasking, input and media plan (not approved yet): https://claude.ai/artifact/BsGpwZU4Se89auPVE4F2cF
  - Planned order: artwork screensaver, Button Mapper recents/last app, keyboard and mouse, EShare with a fixed IP, phone as a remote, music casting, then picture-in-picture.
- Sky layout plan: https://claude.ai/artifact/9HpchhtZD1FogRa6Hg3SAY
- Earlier mockup: https://claude.ai/artifact/KuoUSU4RzmvysuaaspCCcs
- Handoff documents:
  - The neumorphism handoff for Antigravity is `docs/HANDOFF-NEUMORPHISM.md`, also on the Mac in `~/Desktop/TV Sideload/`.
  - The older handoff is `docs/HANDOFF-PROMPT.md`.
