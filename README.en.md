[日本語](README.ja.md) | **English** | [한국어](README.ko.md) | [简体中文](README.zh.md) | [All languages](README.md)

# 猫鱿 (CatSquid)

A cat pet overlay that floats above other apps such as X. No custom keyboard is required — keep using your normal keyboard. Every time you type in any text field, the pet switches its animation (or your own uploaded images) and gives a little bounce. You can assign specific characters ("!", "あ", etc.) to your own images, and the pet returns to its idle animation as soon as you stop typing.

How it works: (1) the "display over other apps" permission lets the pet float on screen; (2) an accessibility service detects changes in text fields and notifies the pet. Normally it only notices that a change happened. If you configure character triggers or enable the built-in "⌫ when deleting" animation, it compares the text around the cursor on the spot (never saved or sent; password fields are ignored).

On first launch, a language picker appears (device language / 日本語 / English / 한국어 / 简体中文). You can change it anytime from the "言語 / Language / 언어 / 语言" button.

## Settings

- **Language**: device language / 日本語 / English / 한국어 / 简体中文 (first-launch dialog + settings button)
- **Size**: small / medium / large / extra large
- **Bounce strength**: none (image switch only) to 3 levels
- **Always on top**: ON = the pet appears automatically when you open other apps; OFF = manual display only
- **Position lock**: ON = taps pass through to the app below and dragging is disabled
- **Reset position**
- **Motion**: tilt the phone and the pet slides gently toward gravity and tilts its head. Tilt sensitivity (low / medium / high) is adjustable. Sensors run only while the pet is visible and the screen is on.
- **Character presets**: save a set of images + built-in color per named character and switch between them
- **Multiple characters**: show any number of saved characters at once. Tap the "characters on screen" icons to show/hide; drag each character on screen.
- **Built-in cat animations**: with no images registered, the pet uses GIF animations for idle, typing, and delete. For `!`, `?`, and space it uses the special animations (`exclamation.gif` / `question.gif` / `space.gif`); if one is missing, it falls back to idle.
- **4 built-in colors** (tints the built-in cat animation)
- **Images are registered in 3 categories (unlimited count)**
  - **Idle**: shown when not typing. If multiple, one is picked at random each time it returns to idle.
  - **Typing (sequence)**: switches 1st → 2nd → …; add alternative images per slot and one is picked at random for that slot.
  - **Character triggers (optional)**: register characters; when one is typed, a random image from that slot appears. Multiple characters can map to one image (e.g., "あ" and "う" for a gloomy face).
  - When typing stops, the pet returns to idle and starts from the 1st slot next time. With no images, the built-in cat animation is used.
- **Home screen icon (optional)**: choose an image and add a shortcut to the home screen (see "About the icon").

## About the icon

The app's default icon is the CatSquid cat. Android does not allow changing the installed app's icon, so if you want a different one, choose an image under "Change home screen icon" in settings; it creates a shortcut with that image. Open the app from that shortcut (the original icon stays in the app drawer).

## Versioning

- Versions are managed in `version.properties`.
- Before building, run `python3 scripts/bump_version.py`; it increments `versionCode` by 1 and the patch of `versionName` by 1.
- Both `app/build.gradle` and manual builds read this file.

## Release build

- The locally built release APK is at `app/build/outputs/apk/release/app-release.apk`.
- For a shorter distribution path, run `sh scripts/package_apk.sh` (creates `dist/catsquid-<version>.apk`).
- It is signed with `app/typingpet-release.jks` (alias: `typingpet`). Do not commit the keystore.

## Build on GitHub (no Android Studio)

1. Push this folder to a new GitHub repository, including hidden folders such as `.github`.
2. Open the **Actions** tab; `Build APK` runs automatically (use `Run workflow` to trigger it manually).
3. When it finishes, download `catsquid-debug-apk` from **Artifacts**, unzip it, and take out `app-debug.apk`.

## Using it on your phone

1. Transfer `app-debug.apk` to your phone and install it (allow "install unknown apps" the first time).
2. Choose a language on first launch.
3. ① "Allow display over other apps" → turn it on.
4. ② "Open accessibility settings" → turn on 猫鱿 in the list (Android shows a standard warning; the app does nothing except the text matching described above).
5. ③ "Show pet".
6. Choose a color in the "Presets" tab, or add your own images in the "Images" tab. Character triggers are configured there too.
7. Type in X or any app; the pet reacts. Drag it to move it.

## Project layout

- `Prefs.kt`: saves/loads all settings (including language and !/? image URIs)
- `LocaleHelper.kt`: applies the chosen language to Activities
- `MainActivity.kt`: settings UI (first-launch language dialog, permission buttons + 3 tabs, home icon shortcut, all built in code)
- `PetView.kt`: draws the pet (registered images, built-in cat GIFs, squishy bounce)
- `ImageLoader.kt`: downsamples registered images
- `OverlayService.kt`: shows the pet overlay; applies settings live; starts/stops sensors with screen on/off
- `MotionController.kt`: derives device tilt from the accelerometer
- `PetAccessibilityService.kt`: detects typing in other apps; when character triggers or the built-in "⌫ delete" reaction are active, compares text around the cursor on the spot
- `res/values/strings.xml`, `res/values-en/strings.xml`, `res/values-ko/strings.xml`, `res/values-zh/strings.xml`: Japanese / English / Korean / Simplified Chinese strings
- `res/raw/*.gif`: built-in cat animations (idle / typing / delete / `!` `?` space specials; `question.gif` optional)
- `res/mipmap-*/ic_launcher.png`, `ic_launcher_round.png`: legacy app icons
- `res/mipmap-anydpi-v26/ic_launcher*.xml`, `res/drawable-xxxhdpi/ic_launcher_foreground.png`: Android 8+ adaptive icons
- `version.properties`, `scripts/bump_version.py`: version source and pre-build increment
- `AGENTS.md`: build and versioning rules
