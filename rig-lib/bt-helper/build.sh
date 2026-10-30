#!/usr/bin/env bash
# Build rig-bt-helper.apk with the SDK's own tools (javac, d8, aapt2, zipalign, apksigner); no Gradle.
# Usage: bash rig-lib/bt-helper/build.sh   ->  prints the APK path. SDK: ANDROID_HOME, ANDROID_SDK_ROOT or ~/Android/Sdk.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$HERE/build
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
PLAT=$(ls -d "$SDK"/platforms/android-* 2>/dev/null | sort -V | tail -1)
[ -n "$BT" ] && [ -n "$PLAT" ] || { echo "build.sh: no build-tools or platform under $SDK" >&2; exit 1; }
JAR=$PLAT/android.jar
# javac: PATH, then JAVA_HOME, then Android Studio's own JDK (the tester PC's PATH JDK has no compiler)
for j in "$(command -v javac || true)" "${JAVA_HOME:-/nonexistent}/bin/javac" /opt/android-studio/jbr/bin/javac \
         "$HOME/android-studio/jbr/bin/javac"; do [ -x "$j" ] && { JAVAC=$j; break; }; done
[ -n "${JAVAC:-}" ] || { echo "build.sh: no javac (set JAVA_HOME to a JDK)" >&2; exit 1; }
rm -rf "$OUT/classes" "$OUT/dex"; mkdir -p "$OUT/classes" "$OUT/dex"
"$JAVAC" -nowarn -source 11 -target 11 -cp "$JAR" -d "$OUT/classes" $(find "$HERE/src" -name '*.java') 1>&2 || true   # notes and errors to stderr; the check below catches a failure
[ -n "$(find "$OUT/classes" -name '*.class')" ] || { echo "build.sh: javac produced nothing" >&2; exit 1; }
"$BT/d8" --min-api 17 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
"$BT/aapt2" link -o "$OUT/unsigned.apk" --manifest "$HERE/AndroidManifest.xml" -I "$JAR" \
  --min-sdk-version 17 --target-sdk-version 34
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KS=${XDG_CACHE_HOME:-$HOME/.cache}/rig-bt-helper/debug.keystore   # outside build/: a round's fresh copy keeps the key
mkdir -p "$(dirname "$KS")"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias rig \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=rig-bt-helper" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --ks-key-alias rig \
  --out "$OUT/rig-bt-helper.apk" "$OUT/aligned.apk"
rm -f "$OUT/unsigned.apk" "$OUT/aligned.apk" "$OUT/rig-bt-helper.apk.idsig"
echo "$OUT/rig-bt-helper.apk"
