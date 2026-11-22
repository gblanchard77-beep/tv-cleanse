#!/usr/bin/env bash
# Build the TV Cleanse home screen APK with the Android command-line tools.
# Needs: JDK 17, and an Android SDK (ANDROID_HOME) with build-tools and a platform.
# Output: out/tvcleanse-home.apk and out/tvcleanse-home.apk.sha256
set -euo pipefail
cd "$(dirname "$0")"

die() { echo "error: $*" >&2; exit 1; }

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] && [ -d "$SDK" ] || die "set ANDROID_HOME to your Android SDK folder"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -n 1)"
PLAT="$(ls -d "$SDK"/platforms/android-* 2>/dev/null | sort -V | tail -n 1)"
[ -d "$BT" ] || die "no build-tools in $SDK"
[ -f "$PLAT/android.jar" ] || die "no Android platform in $SDK"
JAR="$PLAT/android.jar"
echo "build-tools: $BT"
echo "platform:    $PLAT"

OUT=out
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 21 --target-sdk-version 30 \
  --version-code 5 --version-name 1.4.0 \
  "$OUT/res.zip"

javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath "$JAR" -d "$OUT/classes" \
  $(find src -name '*.java')

"$BT/d8" --lib "$JAR" --min-api 21 --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

# Put the compiled code into the APK.
( cd "$OUT/dex" && zip -q -j ../base.apk classes.dex )

"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

# Sign. Use KEYSTORE/KEYSTORE_PASS if given, otherwise make a throwaway key.
KS="${KEYSTORE:-$OUT/debug.keystore}"
PASS="${KEYSTORE_PASS:-android}"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass "$PASS" -keypass "$PASS" \
    -alias tvcleanse -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=TV Cleanse" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --out "$OUT/tvcleanse-home.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/tvcleanse-home.apk"

( cd "$OUT" && sha256sum tvcleanse-home.apk > tvcleanse-home.apk.sha256 )
cat "$OUT/tvcleanse-home.apk.sha256"
echo "built $OUT/tvcleanse-home.apk"
