# AGENTS.md

## ビルドとバージョン

- **APK をビルドする前に、必ずバージョンを上げる。**
  - 実行: `python3 scripts/bump_version.py`
  - `versionCode` を +1、`versionName` のパッチを +1 する（例: `1.2.1` → `1.2.2`）。
- `version.properties` がバージョンの唯一の情報源。`app/build.gradle` はこのファイルを読む。
- 手動で `aapt2 link` する場合も、`version.properties` の `versionCode` / `versionName` をそのまま渡す。新しい APK で同じ `versionCode` を再利用しない。
- GitHub Actions はコミット済みの `version.properties` を使う。CI の成果物も新しいバージョンにしたい場合は、push 前に bump してコミットする。
- CI の署名は Secrets（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`）を使う。未登録の場合は失敗せず debug 鍵で署名し、警告を出す。

## Desktop (Linux / Windows)

- Module: `desktop/` (Kotlin/JVM + Swing), package root `com.maoyou.typingpet.desktop`, entry point `MainKt`. Animation GIFs are read from `app/src/main/res/raw/*.gif` and packaged in the jar under `animations/`.
- Version: the desktop build reads the same `version.properties` as the Android app. Do not bump the version for desktop-only changes; bump it when a release also includes Android changes.
- Local build: `sh scripts/build_desktop.sh`. It downloads `desktop/libs/jnativehook-2.2.2.jar` from Maven Central if missing, compiles with the system `kotlinc`, and writes:
  - `dist/desktop/catsquid-desktop-<version>.jar` (fat jar)
  - `dist/desktop/catsquid-desktop-<version>-linux.zip` and `-windows.zip`, created by `scripts/package_desktop_dist.sh`
- The desktop module has its own standalone Gradle build (`desktop/`, Kotlin JVM plugin) used by CI; do not add it to the Android root build, and do not build the Android app through it.
- jpackage/CI limitation: self-contained app images and installers must be built on their target OS (Windows on Windows, Linux on Linux). `.github/workflows/desktop.yml` builds both Linux and Windows artifacts, including optional app images.

## リリース

- 署名: `app/typingpet-release.jks`（alias: `typingpet`）。リポジトリにはコミットしない。
- 出力: `app/build/outputs/apk/release/app-release.apk`（Gradle 標準のパス）
- 配布用の短いパス: `sh scripts/package_apk.sh` で `dist/catsquid-<version>.apk` にコピーする。
- パッケージ名: `com.maoyou.typingpet`

## アニメーション素材

- `res/raw/idle_*` と `typing_*` の割り当てはユーザーが意図的に設定したもの。元 GIF のファイル名や内容から推測して並べ替えない。
- 特殊アニメは `delete.gif` / `exclamation.gif` / `space.gif`。`question.gif` は任意で、無い場合は待機にフォールバックする。リソースは名前で動的に探すため、ファイルを追加・削除してもビルドできる。
