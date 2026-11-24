#!/usr/bin/env bash
# Serves every .zim in the installed pack through kiwix-serve at /kiwix on port 8081
# (nginx proxies /kiwix/ to it). Exits quietly if the pack has no ZIM files.
set -euo pipefail
WWW="${OPENPACK_WWW:-/var/www/openpack}"

mapfile -t ZIMS < <(find "$WWW/content" -type f -name '*.zim' 2>/dev/null | sort)
if [[ ${#ZIMS[@]} -eq 0 ]]; then
  echo "no .zim files in pack; nothing to serve"
  exit 0
fi
exec kiwix-serve --port 8081 --address 127.0.0.1 --urlRootLocation /kiwix "${ZIMS[@]}"
