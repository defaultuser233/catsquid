#!/bin/sh
set -e
cd "$(dirname "$0")/.."

VERSION=$(grep '^versionName=' version.properties | cut -d= -f2)
SRC="app/build/outputs/apk/release/app-release.apk"
OUT="dist/catsquid-${VERSION}.apk"

mkdir -p dist
cp "$SRC" "$OUT"
echo "$OUT"
