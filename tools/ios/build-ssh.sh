#!/bin/sh
# OpenSSL and libssh2 as static libraries for the SSH of :core on iOS: devices (ios-arm64) and the arm64 simulator
# (ios-simulator-arm64). Release archives are checked by SHA-256 before use. Output: build/ios-ssh/<target>/include
# and lib (libssh2.a, libssl.a, libcrypto.a), which core/build.gradle.kts reads on a Mac.
# Needs Xcode, perl and make; nothing from Homebrew. NETWORKHELM_IOS_SSH_SOURCES may point at the archives already here.
set -eu

OPENSSL=3.5.9
OPENSSL_SHA256=603f5602e2eef00d77fbd429d34dcd5822bb301757a1bc9cdb24c670f1eb859a
LIBSSH2=1.11.1
LIBSSH2_SHA256=d9ec76cbe34db98eec3539fe2c899d26b0c837cb3eb466a56b0f109cabf658f7
MIN_IOS=15.0

root=$(cd "$(dirname "$0")/../.." && pwd)
out=$root/build/ios-ssh
sources=${NETWORKHELM_IOS_SSH_SOURCES:-$out/src}
mkdir -p "$sources" "$out/work"

fetch() {
    file=$sources/$1
    [ -f "$file" ] || curl -fsSL -o "$file" "$2"
    echo "$3  $file" | shasum -a 256 -c -
}

fetch "openssl-$OPENSSL.tar.gz" "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL/openssl-$OPENSSL.tar.gz" "$OPENSSL_SHA256"
fetch "libssh2-$LIBSSH2.tar.gz" "https://github.com/libssh2/libssh2/releases/download/libssh2-$LIBSSH2/libssh2-$LIBSSH2.tar.gz" "$LIBSSH2_SHA256"

# target, OpenSSL configuration, SDK, clang target
build() {
    prefix=$out/$1
    [ -f "$prefix/include/libssh2.h" ] && return
    work=$out/work/$1
    rm -rf "$work" "$prefix"
    mkdir -p "$work"
    sdk=$(xcrun --sdk "$3" --show-sdk-path)

    cd "$work"
    tar xzf "$sources/openssl-$OPENSSL.tar.gz"
    cd "openssl-$OPENSSL"
    ./Configure "$2" no-shared no-tests no-apps no-docs no-engine no-dso no-module "-target $4" --prefix="$prefix" --libdir=lib
    make -j"$(sysctl -n hw.ncpu)" build_libs >/dev/null
    make install_dev >/dev/null

    cd "$work"
    tar xzf "$sources/libssh2-$LIBSSH2.tar.gz"
    cd "libssh2-$LIBSSH2"
    ./configure --host=aarch64-apple-darwin --prefix="$prefix" --disable-shared --enable-static --disable-examples-build \
        --with-crypto=openssl --with-libssl-prefix="$prefix" \
        CC="$(xcrun --sdk "$3" -f clang)" CFLAGS="-target $4 -isysroot $sdk -O2" >/dev/null
    make -j"$(sysctl -n hw.ncpu)" -C src >/dev/null
    make -C src install >/dev/null
    cp include/libssh2.h include/libssh2_sftp.h include/libssh2_publickey.h "$prefix/include/"
}

build ios-arm64 ios64-xcrun iphoneos "arm64-apple-ios$MIN_IOS"
build ios-simulator-arm64 iossimulator-arm64-xcrun iphonesimulator "arm64-apple-ios$MIN_IOS-simulator"
echo "built in $out"
