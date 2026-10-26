# Handoff: redesign Couch Sky home in a neumorphic style

Paste this into Antigravity with the repo open.

---

You are refining the home screen of **Couch Sky**, a personal, non-commercial Apple TV–inspired launcher for a Vision Android 7 TV.

- Repo: https://github.com/wakilibaraka/couch-android7tv. Branch from `main` (last merge was PR #3, `332870f`).
- The code is a fork of Samuel Loranger's Couch, used with the author's permission. Keep the credit in the README.
- It is plain Kotlin with Android Views. There is no AndroidX and no Compose.

## The owner's verdict on the current build
"Too cluttered and ugly, poor readability." The owner wants a **neumorphism** look: soft, calm and readable at a glance from the sofa. Fix the design, don't add features. Reference screenshot: `~/Desktop/couch-sky-3.png` on the Mac.

## Hard constraints
- **Hardware:** Android 7.0 (API 24), MediaTek MT5863 on a CVTE board, a weak GPU and 813 MB of RAM. The screen is 1920x1080 at 240 dpi, and the UI is drawn on a 960x540 dp canvas via `canvasContext()` in `ui/Dimens.kt`.
- **Speed first:**
  - No live blur, no RenderScript and no per-frame animation.
  - Draw neumorphic shadows as static drawables (a layer-list or a cached 9-patch/bitmap), not with view elevation stacks.
  - The current build uses about 40 MB of RAM. Keep it at that level.
- **Remote only:** the Vision WM-P100R remote (D-pad, OK, Back, Home, colour keys). Every control must show a clear focus state, and focus must move predictably.
- **Screensaver:** it is local-only. It shows paintings from `/sdcard/Android/data/io.github.wakilibaraka.couch/files/art/` after 5 minutes idle. No streaming.
- **No mod APKs.**
- **Leave alone:**
  - CVTE packages, `com.talents.huanclientauth` (disabling it hangs boot), tv-detox, `com.example.user.myapplication`, native YouTube and EShare.
  - USB and Wi‑Fi debugging stay ON.
- **Credentials:** never commit keystores or credentials.

## Build and install
CI builds the APK on every PR (`.github/workflows/ci.yml` runs build, unit tests and lint). Merge only when CI is green.

Each CI build is signed with a random key, so installing means an uninstall and a reinstall:

```
adb connect <TV IP>:5555      # see ~/Desktop/TV Sideload/tv-ip.txt; the host part is always .108
adb uninstall io.github.wakilibaraka.couch
adb install couch.apk
adb shell pm grant io.github.wakilibaraka.couch android.permission.WRITE_SECURE_SETTINGS
adb shell cmd package set-home-activity io.github.wakilibaraka.couch/cloud.samlo.couch.MainActivity
```

Uninstalling also deletes `files/art/`, so push the paintings again after every install.

## Where the UI lives
| File | What it does |
|---|---|
| `ui/SkyOverlay.kt` (~600 lines) | Clock, date, weather line, forecast card, top bar, quick panel, status line |
| `ui/Glass.kt` | Current glass pills; replace these with neumorphic surfaces |
| `ui/Fonts.kt` | Hanken Grotesk (bundled) is the SF-like font; Nunito Black is used for the clock |
| `home/HomeLayout.kt`, `home/SectionBuilder.kt`, `home/SectionBinder.kt` | The dock (app row): 160x90 cards and DOCK_INSET margins |
| `ui/Card.kt` | `setGlow()` draws the app card glow, which can be toggled |
| `data/QuickActions.kt` | Settings, Wi‑Fi, files, apps, volume and mute intents |
| `data/CvteTv.kt`, `InputSource.kt` | CVTE input switching (works on the TV) |
| `data/Weather.kt`, `WeatherNow.kt` | Open-Meteo weather for Ogembo (°C): 7 hours in 2-hour steps and 7 days |
| `data/AppSource.kt` | Dock order: pinned packages first, then label matching (YouTube, SmartTube, TV Bro, X-plore, VLC, LocalSend, Aurora) |
| `data/Wallpapers.kt` | `gradient` is the default; ambient mode uses `files/art` |

## Features and decisions to keep
1. **Top-left:** a big clock that changes colour with the time of day (24 h), the date, and a weather line ("17° Cloudy · Ogembo").
2. **Forecast card on the left:**
   - Two symmetric rows of 7: hours at 02:00, 04:00… and days shown as "23°/14°".
   - Same-size icons, plus the rain chance.
   - When focused, the icons animate.
   - It must not overlap the dock.
3. **Top-right bar, in this order:** Inputs ▾ (a drop-down listing TV/AV/HDMI 1‑3; Back closes it), Launcher settings, Android settings.
4. **Quick panel:**
   - Four tiles: Sound, Picture (CVTE menus), Wi‑Fi (shows the network name and opens Wi‑Fi settings; it never toggles Wi‑Fi) and Screensaver.
   - Labelled round buttons: Apps, Files, Home, Refresh.
   - A volume slider and a mute button.
5. **Status line** where the "Home" label used to be: net speed, RAM and storage, refreshed every 3 s.
6. **Dock:** one row of app cards, symmetric, with no overflow. The first app gets focus. The glow behind the cards is on by default and can be turned off.
7. **Navigation:** pressing Up from the dock reaches the top controls directly. The left half of the dock leads to the forecast card, the right half to the panel.
8. **Background:** a time-of-day gradient by default. The weather-sky photos were removed.
9. **Pressing Home on Home** must stay harmless. The CVTE system otherwise toggles to the TV tuner; Button Mapper remaps the Home key on the TV.

## Problems to fix (the goal of this handoff)
- **Clutter:** too many pills and lines compete for attention. Group the controls, cut visual noise, use fewer outlines, and use more spacing on an 8 dp grid.
- **Readability:** text over the background is hard to read. Use solid neumorphic surfaces with high-contrast text (aim for WCAG AA from about 3 m away), a minimum of 14 sp, and real hierarchy (one hero, everything else quieter).
- **Neumorphic system:**
  - One base colour per time of day, e.g. a soft slate `#2B3140` at night and a warm grey `#E6E9EF` during the day.
  - Raised surfaces get a light top-left shadow and a dark bottom-right shadow. The focused or pressed state is inset.
  - Focus must remain obvious on a TV, so add an accent ring or a scale of 1.05 on focus.
- **Keep it consistent:** same corner radii, same paddings, same icon set. Use the existing vector drawables (`ic_*`) or one consistent outline set.
- **Before writing code:** make a mockup (HTML or an image) and get the owner's OK, then implement in small PRs.

## Known issues in the current build (10c944a)
- RAM is about 54 MB (it was 40 MB). Bring it back down.
- The background shows a photo of a snowy mountain, not the default gradient. Check `Hero`/`Wallpapers.current` and any bundled fallback wallpaper.
- SmartTube is missing from the dock. Check its launcher label/category in `AppSource`.
- The see-through tiles and the dock labels are unreadable over bright backgrounds.

## Previous artefacts
- Plan: https://claude.ai/artifact/9HpchhtZD1FogRa6Hg3SAY
- Earlier mockup: https://claude.ai/artifact/KuoUSU4RzmvysuaaspCCcs
- Older handoff: `HANDOFF-PROMPT.md` (in the same folder)
