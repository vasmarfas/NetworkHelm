#!/bin/sh
# AppImage of the desktop app for x86_64 or aarch64 Linux, packed from the app image of :app:desktopApp:createDistributable.
# Usage: build-appimage.sh <app image folder> <version> <output folder>; writes NetworkHelm-<version>-linux-<arch>.AppImage.
# appimagetool and the AppImage runtime are fetched once into build/appimage and checked by SHA-256 before use.
# appimagetool takes the newest runtime on its own unless it is given one, so the pinned file is passed explicitly.
# appimagetool runs unpacked (--appimage-extract-and-run) because CI runners have no FUSE.
set -eu

APPIMAGETOOL=1.9.1
RUNTIME=20251108
case $(uname -m) in
    x86_64)
        ARCH=x86_64
        APPIMAGETOOL_SHA256=ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
        RUNTIME_SHA256=2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d
        ;;
    aarch64)
        ARCH=aarch64
        APPIMAGETOOL_SHA256=f0837e7448a0c1e4e650a93bb3e85802546e60654ef287576f46c71c126a9158
        RUNTIME_SHA256=00cbdfcf917cc6c0ff6d3347d59e0ca1f7f45a6df1a428a0d6d8a78664d87444
        ;;
    *) echo "no AppImage tools for $(uname -m)" >&2; exit 2 ;;
esac

[ $# -eq 3 ] || { echo "usage: $0 <app image folder> <version> <output folder>" >&2; exit 2; }
image=$1
version=$2
[ -x "$image/bin/NetworkHelm" ] || { echo "no launcher in $image/bin" >&2; exit 2; }
mkdir -p "$3"
out=$(cd "$3" && pwd)/NetworkHelm-$version-linux-$ARCH.AppImage

root=$(cd "$(dirname "$0")/../.." && pwd)
cache=$root/build/appimage
mkdir -p "$cache"
tool=$cache/appimagetool-$APPIMAGETOOL-$ARCH.AppImage
runtime=$cache/runtime-$RUNTIME-$ARCH

fetch() {
    [ -f "$1" ] || curl -fsSL -o "$1" "$2"
    echo "$3  $1" | sha256sum -c -
}

fetch "$tool" "https://github.com/AppImage/appimagetool/releases/download/$APPIMAGETOOL/appimagetool-$ARCH.AppImage" "$APPIMAGETOOL_SHA256"
fetch "$runtime" "https://github.com/AppImage/type2-runtime/releases/download/$RUNTIME/runtime-$ARCH" "$RUNTIME_SHA256"
chmod +x "$tool"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
appdir=$work/NetworkHelm.AppDir
mkdir "$appdir"
cp -a "$image" "$appdir/NetworkHelm"
ln -s NetworkHelm/bin/NetworkHelm "$appdir/AppRun"
cp "$root/app/desktopApp/icons/networkhelm.png" "$appdir/"
cat > "$appdir/networkhelm.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=NetworkHelm
Comment=Client for MikroTik RouterOS and OpenWrt routers
Exec=NetworkHelm
Icon=networkhelm
Categories=Network;
EOF

ARCH=$ARCH "$tool" --appimage-extract-and-run --no-appstream --runtime-file "$runtime" "$appdir" "$out"
echo "built $out"
