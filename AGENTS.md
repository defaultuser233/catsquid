# AGENTS.md

## ビルドとバージョン

- **APK をビルドする前に、必ずバージョンを上げる。**
  - 実行: `python3 scripts/bump_version.py`
  - `versionCode` を +1、`versionName` のパッチを +1 する（例: `1.2.1` → `1.2.2`）。
- `version.properties` がバージョンの唯一の情報源。`app/build.gradle` はこのファイルを読む。
- 手動で `aapt2 link` する場合も、`version.properties` の `versionCode` / `versionName` をそのまま渡す。新しい APK で同じ `versionCode` を再利用しない。
- GitHub Actions はコミット済みの `version.properties` を使う。CI の成果物も新しいバージョンにしたい場合は、push 前に bump してコミットする。

## リリース

- 署名: `app/typingpet-release.jks`（alias: `typingpet`）。リポジトリにはコミットしない。
- 出力: `app/build/outputs/apk/release/app-release.apk`
- パッケージ名: `com.maoyou.typingpet`

## アニメーション素材

- `res/raw/idle_*` と `typing_*` の割り当てはユーザーが意図的に設定したもの。元 GIF のファイル名や内容から推測して並べ替えない。
