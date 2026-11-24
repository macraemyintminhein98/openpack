#!/usr/bin/env bash
# Make one folder (and a .zip) that can be copied to a USB stick, SD card or phone.
# It contains the pack, the server for every platform, and start scripts + instructions.
#
# Usage: scripts/make-bundle.sh <built-pack-dir> [public-key-file]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACK="${1:?usage: $0 <built-pack-dir> [public-key]}"
PUB="${2:-}"

[[ -f "$PACK/manifest.json" ]] || { echo "not a built pack: $PACK"; exit 1; }

echo "Verifying pack before bundling..."
if [[ -n "$PUB" ]]; then node "$ROOT/bin/openpack.js" verify "$PACK" --pub "$PUB"
else node "$ROOT/bin/openpack.js" verify "$PACK"; fi

[[ -d "$ROOT/dist-bin" ]] || "$ROOT/scripts/build-server.sh"

NAME="$(node -e 'const m=require(process.argv[1]);console.log(m.name+"-"+m.version)' "$(cd "$PACK" && pwd)/manifest.json")"
OUTDIR="$ROOT/dist/OpenPack-$NAME"
rm -rf "$OUTDIR" "$OUTDIR.zip"
mkdir -p "$OUTDIR/bin"

cp "$ROOT"/dist-bin/* "$OUTDIR/bin/"
cp "$ROOT"/bundle/START-* "$OUTDIR/"
cp -a "$PACK" "$OUTDIR/pack"
[[ -n "$PUB" ]] && cp "$PUB" "$OUTDIR/openpack.pub"
chmod +x "$OUTDIR"/START-*.sh "$OUTDIR"/bin/openpack-linux-* 2>/dev/null || true

# Windows-friendly text files: CRLF line endings for the .bat, and CRLF + UTF-8 BOM for the
# instructions, so old Notepad shows the Burmese text correctly.
sed -i 's/$/\r/' "$OUTDIR/START-WINDOWS.bat"
{ printf '\xEF\xBB\xBF'; sed 's/$/\r/' "$OUTDIR/START-HERE.txt"; } > "$OUTDIR/START-HERE.txt.tmp"
mv "$OUTDIR/START-HERE.txt.tmp" "$OUTDIR/START-HERE.txt"

(cd "$ROOT/dist" && zip -qr "OpenPack-$NAME.zip" "OpenPack-$NAME")
echo
echo "Bundle folder: $OUTDIR"
echo "Bundle zip:    $OUTDIR.zip ($(du -h "$OUTDIR.zip" | cut -f1))"
