#!/usr/bin/env bash
# Downloads the official cloudflared binary for one OS/arch into dist-server/bin/.
# Usage: ./scripts/fetch-cloudflared.sh <os> <arch>
#   os:   windows | darwin | linux
#   arch: amd64 | arm64
set -euo pipefail

CLOUDFLARED_VERSION="2026.10.0"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT/dist-server/bin"
SUMS_FILE="$ROOT/scripts/cloudflared.sha256"

OS="${1:?os (windows|darwin|linux)}"
ARCH="${2:?arch (amd64|arm64)}"

mkdir -p "$OUT_DIR"

case "${OS}-${ARCH}" in
  windows-amd64)
    ASSET="cloudflared-windows-amd64.exe"
    OUT_NAME="cloudflared.exe"
    EXTRACT=copy
    ;;
  linux-amd64)
    ASSET="cloudflared-linux-amd64"
    OUT_NAME="cloudflared"
    EXTRACT=copy
    ;;
  darwin-amd64)
    ASSET="cloudflared-darwin-amd64.tgz"
    OUT_NAME="cloudflared"
    EXTRACT=tgz
    ;;
  darwin-arm64)
    ASSET="cloudflared-darwin-arm64.tgz"
    OUT_NAME="cloudflared"
    EXTRACT=tgz
    ;;
  *)
    echo "Unsupported platform: ${OS}-${ARCH}" >&2
    exit 1
    ;;
esac

KEY="${OS}-${ARCH}"
EXPECTED="$(awk -v k="$KEY" '$1 == k { print $2; exit }' "$SUMS_FILE")"
if [[ -z "$EXPECTED" ]]; then
  echo "No SHA-256 entry for $KEY in $SUMS_FILE" >&2
  exit 1
fi

URL="https://github.com/cloudflare/cloudflared/releases/download/${CLOUDFLARED_VERSION}/${ASSET}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "==> Downloading cloudflared ${CLOUDFLARED_VERSION} (${KEY})"
curl -fsSL "$URL" -o "$TMP/$ASSET"

if [[ "$EXTRACT" == "tgz" ]]; then
  tar xzf "$TMP/$ASSET" -C "$TMP"
  BIN="$TMP/cloudflared"
else
  BIN="$TMP/$ASSET"
fi

ACTUAL="$(shasum -a 256 "$BIN" | awk '{print $1}')"
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "SHA-256 mismatch for $KEY" >&2
  echo "  expected: $EXPECTED" >&2
  echo "  actual:   $ACTUAL" >&2
  exit 1
fi

cp "$BIN" "$OUT_DIR/$OUT_NAME"
chmod +x "$OUT_DIR/$OUT_NAME"
echo "==> Installed $OUT_DIR/$OUT_NAME"
