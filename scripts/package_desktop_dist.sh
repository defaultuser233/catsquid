#!/bin/sh
set -eu

usage() {
    echo "Usage: sh scripts/package_desktop_dist.sh <linux|windows> <jar-path>" >&2
}

if [ "$#" -ne 2 ]; then
    usage
    exit 2
fi

PLATFORM=$1
JAR=$2

case "$PLATFORM" in
    linux|windows) ;;
    *)
        echo "Unsupported platform: $PLATFORM (expected linux or windows)" >&2
        usage
        exit 2
        ;;
esac

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [ ! -f "$JAR" ]; then
    echo "Jar not found: $JAR" >&2
    exit 1
fi
if [ ! -s "$JAR" ]; then
    echo "Jar is empty: $JAR" >&2
    exit 1
fi

VERSION=$(sed -n 's/^versionName=//p' "$ROOT/version.properties" | head -n 1)
if [ -z "$VERSION" ]; then
    echo "Could not read versionName from $ROOT/version.properties" >&2
    exit 1
fi

NAME="catsquid-desktop-$VERSION"
OUTDIR="$ROOT/dist/desktop"
OUT="$OUTDIR/$NAME-$PLATFORM.zip"

mkdir -p "$OUTDIR"
STAGE=$(mktemp -d "${TMPDIR:-/tmp}/catsquid-desktop.XXXXXX") || {
    echo "Could not create a temporary directory" >&2
    exit 1
}
trap 'rm -rf "$STAGE"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

PKG="$STAGE/$NAME"
mkdir -p "$PKG"
cp "$JAR" "$PKG/$NAME.jar"

cat > "$PKG/run.sh" <<EOF
#!/bin/sh
cd "\$(dirname "\$0")" || exit 1
exec java -jar "$NAME.jar" "\$@"
EOF
chmod +x "$PKG/run.sh"

{
    printf '@echo off\r\n'
    printf 'cd /d "%%~dp0"\r\n'
    printf 'java -jar "%s.jar" %%*\r\n' "$NAME"
} > "$PKG/run.bat"

cat > "$PKG/README.txt" <<EOF
CatSquid Desktop $VERSION ($PLATFORM)

Run
  Linux:   ./run.sh
  Windows: run.bat (or double-click it)

Java 21 or newer is required; java must be on PATH. The jar is
self-contained and needs no extra libraries.

Linux notes
  The global key hook uses X11. On a Wayland session it only works for
  XWayland (X11) windows; native Wayland windows are not observed. Start
  the app from a terminal to see any key-hook error message.
  A running compositor is needed for the transparent window; without one
  the pet may appear on an opaque background.

Right-click the pet and choose 设置 to change size, opacity and
always-on-top, or 退出 to quit. Drag the pet with the mouse to move it.
EOF

make_zip() {
    if command -v zip >/dev/null 2>&1; then
        (cd "$STAGE" && zip -qr "$OUT" "$NAME")
        return
    fi
    if command -v powershell.exe >/dev/null 2>&1; then
        out_win=$OUT
        pkg_win=$PKG
        if command -v cygpath >/dev/null 2>&1; then
            out_win=$(cygpath -w "$OUT")
            pkg_win=$(cygpath -w "$PKG")
        fi
        powershell.exe -NoProfile -Command \
            "Compress-Archive -LiteralPath '$pkg_win' -DestinationPath '$out_win' -Force"
        return
    fi
    if command -v 7z >/dev/null 2>&1; then
        (cd "$STAGE" && 7z a -tzip "$OUT" "$NAME" >/dev/null)
        return
    fi
    echo "No zip tool found (need zip, powershell.exe, or 7z)" >&2
    return 1
}

rm -f "$OUT"
if ! make_zip; then
    rm -f "$OUT"
    echo "Could not create $OUT" >&2
    exit 1
fi

if command -v unzip >/dev/null 2>&1; then
    zip_ok=0
    unzip -t "$OUT" >/dev/null || zip_ok=1
else
    zip_ok=0
    jar --list --file "$OUT" >/dev/null || zip_ok=1
fi
if [ "$zip_ok" -ne 0 ]; then
    rm -f "$OUT"
    echo "Zip verification failed: $OUT" >&2
    exit 1
fi

echo "Created $OUT"
echo "SHA-256: $(sha256sum "$OUT" | cut -d' ' -f1)"
