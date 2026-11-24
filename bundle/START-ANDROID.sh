#!/bin/sh
# OpenPack for Android phones, using the free Termux app.
# Run:  sh ~/storage/downloads/OpenPack/START-ANDROID.sh   (adjust the folder name if different)
cd "$(dirname "$0")" || exit 1
HERE="$(pwd)"

case "$(uname -m)" in
  aarch64|arm64)        A=arm64 ;;
  armv7l|armv8l|arm*)   A=arm ;;
  x86_64)               A=amd64 ;;
  *) echo "Unknown CPU type: $(uname -m)"; exit 1 ;;
esac

# Programs cannot be run straight from shared storage on Android, so copy it into Termux first.
DEST="$HOME/.openpack"
mkdir -p "$DEST"
cp "$HERE/bin/openpack-linux-$A" "$DEST/openpack" && chmod +x "$DEST/openpack" || {
  echo "Could not copy the program. Did you run: termux-setup-storage ?"; exit 1; }

# Keep the phone awake while serving.
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock

PUBARG=""
[ -f "$HERE/openpack.pub" ] && PUBARG="-pub $HERE/openpack.pub"
# shellcheck disable=SC2086
exec "$DEST/openpack" -dir "$HERE/pack" $PUBARG "$@"
