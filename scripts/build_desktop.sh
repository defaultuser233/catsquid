#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

VERSION=$(sed -n 's/^versionName=//p' "$ROOT/version.properties" | head -n 1)
if [ -z "$VERSION" ]; then
    echo "Could not read versionName from $ROOT/version.properties" >&2
    exit 1
fi

command -v kotlinc >/dev/null 2>&1 || {
    echo "kotlinc not found; install Kotlin 2.x or add it to PATH" >&2
    exit 1
}
command -v jar >/dev/null 2>&1 || {
    echo "jar not found; a JDK 21 is required" >&2
    exit 1
}
command -v unzip >/dev/null 2>&1 || {
    echo "unzip not found" >&2
    exit 1
}

LIB_DIR="$ROOT/desktop/libs"
JNH="$LIB_DIR/jnativehook-2.2.2.jar"
JNH_URL="https://repo1.maven.org/maven2/com/github/kwhat/jnativehook/2.2.2/jnativehook-2.2.2.jar"

mkdir -p "$LIB_DIR"
if [ ! -s "$JNH" ]; then
    command -v curl >/dev/null 2>&1 || {
        echo "curl not found; cannot download $JNH_URL" >&2
        exit 1
    }
    echo "Downloading JNativeHook 2.2.2 from Maven Central..."
    rm -f "$JNH"
    if ! curl -fL --retry 3 --connect-timeout 20 -o "$JNH" "$JNH_URL"; then
        rm -f "$JNH"
        echo "Download failed: $JNH_URL" >&2
        exit 1
    fi
fi
if [ ! -s "$JNH" ]; then
    echo "JNativeHook jar is missing or empty: $JNH" >&2
    exit 1
fi

SRC_DIR="$ROOT/desktop/src/main/kotlin"
if [ ! -d "$SRC_DIR" ]; then
    echo "Desktop source directory not found: $SRC_DIR" >&2
    exit 1
fi
SOURCES=$(find "$SRC_DIR" -type f -name '*.kt' | sort)
if [ -z "$SOURCES" ]; then
    echo "No Kotlin sources found under $SRC_DIR" >&2
    exit 1
fi

ANIM_DIR="$ROOT/app/src/main/res/raw"
if ! ls "$ANIM_DIR"/*.gif >/dev/null 2>&1; then
    echo "No animation GIFs found under $ANIM_DIR" >&2
    exit 1
fi

STAGE=$(mktemp -d "${TMPDIR:-/tmp}/catsquid-desktop-build.XXXXXX") || {
    echo "Could not create a temporary directory" >&2
    exit 1
}
trap 'rm -rf "$STAGE"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

echo "Compiling desktop sources with kotlinc..."
# shellcheck disable=SC2086
kotlinc $SOURCES -include-runtime -jvm-target 21 -cp "$JNH" -d "$STAGE/app.jar"

JARROOT="$STAGE/jarroot"
mkdir -p "$JARROOT"
unzip -q "$STAGE/app.jar" -d "$JARROOT" -x 'META-INF/MANIFEST.MF'
if ! unzip -q "$JNH" -d "$JARROOT" \
    -x 'META-INF/MANIFEST.MF' 'META-INF/*.SF' 'META-INF/*.DSA' 'META-INF/*.RSA' 'META-INF/*.EC' \
    2>"$STAGE/jnativehook-unzip.log"; then
    cat "$STAGE/jnativehook-unzip.log" >&2
    exit 1
fi

if [ ! -f "$JARROOT/com/maoyou/typingpet/desktop/MainKt.class" ]; then
    echo "MainKt.class is missing; expected a Main.kt with a top-level main()" >&2
    exit 1
fi

mkdir -p "$JARROOT/animations"
cp "$ANIM_DIR"/*.gif "$JARROOT/animations/"

MANIFEST="$STAGE/MANIFEST.MF"
printf 'Manifest-Version: 1.0\r\nMain-Class: com.maoyou.typingpet.desktop.MainKt\r\nMulti-Release: true\r\n\r\n' > "$MANIFEST"

OUTDIR="$ROOT/dist/desktop"
mkdir -p "$OUTDIR"
OUT="$OUTDIR/catsquid-desktop-$VERSION.jar"
rm -f "$OUT"
jar --create --file "$OUT" --manifest "$MANIFEST" -C "$JARROOT" .

echo "Created $OUT"
echo "SHA-256: $(sha256sum "$OUT" | cut -d' ' -f1)"

sh "$ROOT/scripts/package_desktop_dist.sh" linux "$OUT"
sh "$ROOT/scripts/package_desktop_dist.sh" windows "$OUT"
