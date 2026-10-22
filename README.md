# 猫鱿 (CatSquid)

A floating cat pet overlay for Android that reacts to typing, tilt, and touch.

**Languages:** [日本語](README.ja.md) | [English](README.en.md) | [한국어](README.ko.md) | [简体中文](README.zh.md)

## Features

- Floats above other apps with the "display over other apps" permission.
- Reacts to typing through an accessibility service; no custom keyboard required.
- Built-in cat GIF animations (4 idle / 3 typing / 1 delete) plus custom images.
- Character presets, multiple pets on screen, four built-in colors.
- Tilt the phone and the pet follows gravity; a strong tilt triggers a short haptic bump.
- Japanese / English / Korean / Simplified Chinese UI.
- Adaptive launcher icon for Android 8+.

## Quick start

1. Build with GitHub Actions (`.github/workflows/build.yml`) or install `app/build/outputs/apk/release/app-release.apk`.
2. Allow "display over other apps" and enable accessibility for 猫鱿.
3. Tap "Show pet" and type in any app.

## Build and versioning

- Before every APK build, run `python3 scripts/bump_version.py`.
- `version.properties` is the single source of truth; `app/build.gradle` and manual builds read it.
- Details: [AGENTS.md](AGENTS.md).

## Documentation

| Language | README |
| --- | --- |
| 日本語 | [README.ja.md](README.ja.md) |
| English | [README.en.md](README.en.md) |
| 한국어 | [README.ko.md](README.ko.md) |
| 简体中文 | [README.zh.md](README.zh.md) |
