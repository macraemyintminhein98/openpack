#!/usr/bin/env bash
# One command for the weekly release: build, sign, verify, bundle, write the change note and
# checksums, and tidy old versions.
#
# Usage: scripts/weekly-release.sh <pack-dir> [--key keys/openpack.key] [--pub keys/openpack.pub]
#                                             [--keep 4] [--version 2026.48]
#
# Output in dist/:
#   OpenPack-<name>-<version>.zip     copy this to USB sticks / SD cards / phones
#   OpenPack-<name>-<version>.zip.sha256
#   CHANGES-<name>-<version>.txt      what is new, updated, removed since the last release
#   releases/<name>/<version>.json    manifest history (used for the next comparison)
#
# Cron example (every Monday 06:00):
#   0 6 * * 1 cd /srv/openpack && scripts/weekly-release.sh packs/weekly >> release.log 2>&1
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PACK="${1:?usage: $0 <pack-dir> [--key K] [--pub P] [--keep N] [--version V]}"; shift
KEY="keys/openpack.key"; PUB="keys/openpack.pub"; KEEP=4; VERSION=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --key) KEY="$2"; shift 2 ;;
    --pub) PUB="$2"; shift 2 ;;
    --keep) KEEP="$2"; shift 2 ;;
    --version) VERSION="$2"; shift 2 ;;
    *) echo "unknown option: $1"; exit 2 ;;
  esac
done

[[ -f "$PACK/pack.json" ]] || { echo "no pack.json in $PACK"; exit 1; }
[[ -f "$KEY" ]] || { echo "signing key not found: $KEY  (create one: node bin/openpack.js keygen keys)"; exit 1; }
[[ -f "$PUB" ]] || { echo "public key not found: $PUB"; exit 1; }
[[ "$KEEP" =~ ^[1-9][0-9]*$ ]] || { echo "--keep must be a number, 1 or more"; exit 2; }

echo "== 1/5 Build and sign"
ARGS=(build "$PACK" --key "$KEY")
[[ -n "$VERSION" ]] && ARGS+=(--version "$VERSION")
node bin/openpack.js "${ARGS[@]}"

PACKABS="$(cd "$PACK" && pwd)"
NAME="$(node -e 'console.log(require(process.argv[1]).name)' "$PACKABS/pack.json")"
BUILT="$(ls -dt dist/"$NAME"-*/ | head -1)"; BUILT="${BUILT%/}"
VER="$(node -e 'console.log(require(process.argv[1]).version)' "$ROOT/$BUILT/manifest.json")"

echo "== 2/5 Verify signature and every file"
node bin/openpack.js verify "$BUILT" --pub "$PUB"

echo "== 3/5 Bundle"
bash scripts/make-bundle.sh "$BUILT" "$PUB" | tail -2
ZIP="dist/OpenPack-$NAME-$VER.zip"
[[ -f "$ZIP" ]] || { echo "bundle zip missing: $ZIP"; exit 1; }

echo "== 4/5 Change note and checksum"
HIST="dist/releases/$NAME"; mkdir -p "$HIST"
PREV="$(find "$HIST" -name '*.json' ! -name "$VER.json" -printf '%T@ %p\n' | sort -rn | head -1 | cut -d' ' -f2-)"
node -e '
  const { changesText } = require("./lib/changes");
  const fs = require("fs");
  const next = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
  const prev = process.argv[2] ? JSON.parse(fs.readFileSync(process.argv[2], "utf8")) : null;
  fs.writeFileSync(process.argv[3], changesText(prev, next));
' "$BUILT/manifest.json" "$PREV" "dist/CHANGES-$NAME-$VER.txt"
cp "$BUILT/manifest.json" "$HIST/$VER.json"
(cd dist && sha256sum "OpenPack-$NAME-$VER.zip" > "OpenPack-$NAME-$VER.zip.sha256")
cat "dist/CHANGES-$NAME-$VER.txt"

echo "== 5/5 Keep the newest $KEEP releases"
# Only this pack's own outputs are removed. Manifest history is kept (it is tiny).
ls -t dist/OpenPack-"$NAME"-*.zip 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do
  base="${old%.zip}"; v="${base#dist/OpenPack-"$NAME"-}"
  echo "removing old release $v"
  rm -rf -- "$base" "$old" "$old.sha256" "dist/$NAME-$v" "dist/CHANGES-$NAME-$v.txt"
done

echo
echo "DONE: $ZIP ($(du -h "$ZIP" | cut -f1))"
echo "Checksum: $(cut -d' ' -f1 "$ZIP.sha256")"
