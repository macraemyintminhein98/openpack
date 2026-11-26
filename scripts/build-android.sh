#!/usr/bin/env bash
# Build the Android app with a pack built in.
#
#   scripts/build-android.sh <built pack folder> <public key file> [spare public key files...]
#   scripts/build-android.sh dist/example-2026.48 keys/openpack.pub spare/openpack.pub
#
# The pack must already be built and signed (openpack build ... --key ...). The public key is
# built into the app: afterwards the app only opens packs signed with the matching private key.
# Spare keys: the app also accepts packs signed by them. Make one spare now, store its private half
# offline, and you can still publish packs if the everyday key is ever lost.
# Needs: Java 17 or newer, and ANDROID_HOME pointing at an Android SDK (Android Studio installs one).
set -euo pipefail

usage="usage: build-android.sh <built pack folder> <public key file> [spare public key files...]"
pack="${1:?$usage}"
pub="${2:?$usage}"
shift
here="$(cd "$(dirname "$0")/.." && pwd)"
assets="$here/android/app/src/main/assets"

[ -f "$pack/manifest.json" ] || { echo "No manifest.json in $pack. Build the pack first." >&2; exit 1; }
[ -f "$pack/manifest.sig" ] || { echo "The pack is not signed (no manifest.sig)." >&2; exit 1; }
for k in "$@"; do
  grep -q "BEGIN PUBLIC KEY" "$k" || { echo "$k is not a public key file." >&2; exit 1; }
  if grep -q "PRIVATE KEY" "$k"; then echo "$k is a PRIVATE key. Never build it into the app." >&2; exit 1; fi
done

node "$here/bin/openpack.js" verify "$pack" --pub "$pub"

rm -rf "$assets/pack" "$assets/pack.files" "$assets/keys" "$assets/openpack.pub"
mkdir -p "$assets/keys"
cp -R "$pack" "$assets/pack"
n=0
for k in "$@"; do n=$((n + 1)); cp "$k" "$assets/keys/$n.pub"; done
echo "Trusted keys built in: $n"
# The app copies exactly these files out of the APK on first start.
(cd "$assets/pack" && find . -type f | sed 's|^\./||' | LC_ALL=C sort) > "$assets/pack.files"

cd "$here/android"
# OPENPACK_GRADLE=gradle uses an installed Gradle instead of downloading the pinned one.
"${OPENPACK_GRADLE:-./gradlew}" --no-daemon -q testDebugUnitTest assembleDebug
apk="$here/android/app/build/outputs/apk/debug/app-debug.apk"
ls -l "$apk"
echo "Built: $apk"
