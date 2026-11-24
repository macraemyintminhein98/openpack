#!/usr/bin/env bash
# Update a running OpenPack box from a USB stick or SD card.
# Refuses to install a pack whose files or signature do not verify.
#
# Usage (as root):  ./update-pack.sh /media/usb/<pack-folder> [/etc/openpack/openpack.pub]
set -euo pipefail

NEW="${1:?usage: $0 <new-pack-dir> [pubkey]}"
PUB="${2:-/etc/openpack/openpack.pub}"
WWW=/var/www/openpack
HERE="$(cd "$(dirname "$0")/.." && pwd)"

[[ $EUID -eq 0 ]] || { echo "run as root"; exit 1; }
[[ -f "$PUB" ]] || { echo "public key not found at $PUB (copy openpack.pub there first)"; exit 1; }

echo "Verifying..."
node "$HERE/bin/openpack.js" verify "$NEW" --pub "$PUB"

echo "Installing (atomic swap)..."
STAGE="$WWW.new"; OLD="$WWW.old"
rm -rf "$STAGE" "$OLD"
cp -a "$NEW" "$STAGE"
mv "$WWW" "$OLD"
mv "$STAGE" "$WWW"
rm -rf "$OLD"
systemctl restart openpack-kiwix 2>/dev/null || true
echo "Updated."
