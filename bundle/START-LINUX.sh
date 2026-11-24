#!/bin/sh
# OpenPack for Linux laptops and mini PCs. Run:  sh START-LINUX.sh
cd "$(dirname "$0")" || exit 1
HERE="$(pwd)"

case "$(uname -m)" in
  x86_64)               A=amd64 ;;
  aarch64|arm64)        A=arm64 ;;
  armv7l|armv8l|arm*)   A=arm ;;
  *) echo "Unknown CPU type: $(uname -m)"; exit 1 ;;
esac

BIN="$HERE/bin/openpack-linux-$A"
[ -x "$BIN" ] || chmod +x "$BIN" 2>/dev/null

PUBARG=""
[ -f "$HERE/openpack.pub" ] && PUBARG="-pub $HERE/openpack.pub"
# shellcheck disable=SC2086
exec "$BIN" -dir "$HERE/pack" $PUBARG "$@"
