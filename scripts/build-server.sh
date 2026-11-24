#!/usr/bin/env bash
# Cross-compile the one-file server for the devices people actually have.
# Needs Go 1.21+ (build machine only; the devices need nothing installed).
set -euo pipefail
cd "$(dirname "$0")/../server"
OUT=../dist-bin
rm -rf "$OUT" && mkdir -p "$OUT"

build() { # os arch outfile
  env GOOS="$1" GOARCH="$2" GOARM=7 CGO_ENABLED=0 \
    go build -trimpath -ldflags "-s -w" -o "$OUT/$3" .
  printf '  %-34s %s\n' "$3" "$(du -h "$OUT/$3" | cut -f1)"
}

echo "Building openpack server binaries:"
build windows amd64 openpack-windows-amd64.exe   # most laptops
build windows 386   openpack-windows-386.exe     # 32-bit Windows
build windows arm64 openpack-windows-arm64.exe
build linux   amd64 openpack-linux-amd64         # Linux laptops, Chromebooks (Linux), mini PCs
build linux   arm64 openpack-linux-arm64         # modern Android phones via Termux, Raspberry Pi
build linux   arm   openpack-linux-arm           # older 32-bit Android phones via Termux
