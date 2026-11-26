#!/usr/bin/env bash
# Build the Android app with a pack built in.
#
#   scripts/build-android.sh <built pack folder> <public key file>
#   scripts/build-android.sh dist/example-2026.48 openpack.pub
#
# The pack must already be built and signed (openpack build ... --key ...). The public key is
# built into the app: afterwards the app only opens packs signed with the matching private key.
# Needs: Java 17 or newer, and ANDROID_HOME pointing at an Android SDK (Android Studio installs one).
set -euo pipefail

pack="${1:?usage: build-android.sh <built pack folder> <public key file>}"
pub="${2:?usage: build-android.sh <built pack folder> <public key file>}"
here="$(cd "$(dirname "$0")/.." && pwd)"
assets="$here/android/app/src/main/assets"

[ -f "$pack/manifest.json" ] || { echo "No manifest.json in $pack. Build the pack first." >&2; exit 1; }
[ -f "$pack/manifest.sig" ] || { echo "The pack is not signed (no manifest.sig)." >&2; exit 1; }
grep -q "BEGIN PUBLIC KEY" "$pub" || { echo "$pub is not a public key file." >&2; exit 1; }
if grep -rq "PRIVATE KEY" "$pub"; then echo "That is a PRIVATE key. Never build it into the app." >&2; exit 1; fi

node "$here/bin/openpack.js" verify "$pack" --pub "$pub"

rm -rf "$assets"
mkdir -p "$assets"
cp -R "$pack" "$assets/pack"
cp "$pub" "$assets/openpack.pub"
# The app copies exactly these files out of the APK on first start.
(cd "$assets/pack" && find . -type f | sed 's|^\./||' | LC_ALL=C sort) > "$assets/pack.files"

cd "$here/android"
# OPENPACK_GRADLE=gradle uses an installed Gradle instead of downloading the pinned one.
"${OPENPACK_GRADLE:-./gradlew}" --no-daemon -q testDebugUnitTest assembleDebug "${@:3}"
apk="$here/android/app/build/outputs/apk/debug/app-debug.apk"
ls -l "$apk"
echo "Built: $apk"
