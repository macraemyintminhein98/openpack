#!/usr/bin/env bash
# End-to-end check: Node builds and signs a pack, the Go server verifies it (cross-language),
# tampering is refused, and the running server answers correctly.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
T="$(mktemp -d)"; trap 'kill $SRV 2>/dev/null || true; rm -rf "$T"' EXIT
SRV=""

echo "1. Build + sign with the Node CLI"
node bin/openpack.js keygen "$T/keys" >/dev/null
node bin/openpack.js build packs/example --key "$T/keys/openpack.key" --out "$T/dist" --version e2e.1 >/dev/null
PACK="$T/dist/example-e2e.1"

echo "2. Build the Go server"
(cd server && go build -o "$T/openpack" .)

echo "3. Go verifies the Node-signed pack"
"$T/openpack" -dir "$PACK" -pub "$T/keys/openpack.pub" -check | tail -1

echo "4. Tampered file must be refused"
cp -a "$PACK" "$T/bad"; echo x >> "$T/bad/content/learn/sample-lesson.md"
if "$T/openpack" -dir "$T/bad" -pub "$T/keys/openpack.pub" -check >/dev/null 2>&1; then echo "FAIL: tampered pack accepted"; exit 1; fi
echo "   refused, good"

echo "5. Tampered manifest.js must be refused"
cp -a "$PACK" "$T/bad2"; sed -i 's/e2e.1/9.9/' "$T/bad2/manifest.js"
if "$T/openpack" -dir "$T/bad2" -pub "$T/keys/openpack.pub" -check >/dev/null 2>&1; then echo "FAIL: tampered manifest.js accepted"; exit 1; fi
echo "   refused, good"

echo "6. Wrong key must be refused"
node bin/openpack.js keygen "$T/other" >/dev/null
if "$T/openpack" -dir "$PACK" -pub "$T/other/openpack.pub" -check >/dev/null 2>&1; then echo "FAIL: wrong key accepted"; exit 1; fi
echo "   refused, good"

echo "7. Serve and probe"
"$T/openpack" -dir "$PACK" -pub "$T/keys/openpack.pub" -port 18080 >"$T/server.log" 2>&1 &
SRV=$!
for _ in $(seq 1 30); do curl -sf localhost:18080/features.json >/dev/null 2>&1 && break; sleep 0.2; done
code() { curl -s -o /dev/null -w '%{http_code}' "localhost:18080$1"; }
for p in / /app.js /manifest.json /manifest.js /features.json /content/about/welcome.txt; do
  printf '   %-30s %s\n' "$p" "$(code $p)"; [[ "$(code $p)" == 200 ]] || { echo "FAIL $p"; exit 1; }
done
[[ "$(code /content/)" == 404 ]] || { echo "FAIL: directory listing exposed"; exit 1; }
[[ "$(code /.git)" == 404 ]] || { echo "FAIL: dotfile exposed"; exit 1; }
echo "   JS content-type: $(curl -sI localhost:18080/app.js | grep -i '^content-type' | tr -d '\r')"
echo "   server memory (RSS): $(grep VmRSS /proc/$SRV/status | awk '{print $2/1024 " MB"}')"
echo "ALL E2E CHECKS PASSED"
