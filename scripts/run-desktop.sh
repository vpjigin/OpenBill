#!/usr/bin/env bash
# macOS/Linux: clean rebuild of the desktop app, launch it, and verify the billing
# website serves the same JS bundle as the jar inside the app (catches stale assets).
#
# Needs: JDK 21, Maven, Node 20+, Rust (for Tauri), curl, unzip, lsof.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DESKTOP="$ROOT/desktop"
BUNDLE_ROOT="$DESKTOP/src-tauri/target/release/bundle"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

case "$(uname -s)" in
  Darwin | Linux) ;;
  *) fail "run-desktop.sh supports macOS and Linux only (use scripts/build-server.sh + desktop build on Windows)" ;;
esac

index_asset_from_jar() {
  local jar="$1"
  [[ -f "$jar" ]] || fail "Missing jar: $jar"
  local hits count
  hits="$(unzip -l "$jar" 2>/dev/null | grep -oE 'static/assets/index-[^.]+\.js' | sort -u || true)"
  if [[ -z "$hits" ]]; then
    fail "No index-*.js found in $jar"
  fi
  count="$(echo "$hits" | wc -l | tr -d ' ')"
  if [[ "$count" -gt 1 ]]; then
    echo "Found multiple index bundles in $jar:" >&2
    echo "$hits" | sed 's/^/  /' >&2
    fail "Expected exactly one index-*.js in the bundled server.jar"
  fi
  basename "$(echo "$hits" | head -1)"
}

index_asset_from_html() {
  local html
  html="$(curl -fsS "http://127.0.0.1:8080/" 2>/dev/null)" || fail "Could not fetch http://127.0.0.1:8080/"
  local hit
  hit="$(echo "$html" | grep -oE 'assets/index-[^"]+\.js' | head -1 || true)"
  [[ -n "$hit" ]] || fail "No assets/index-*.js in billing index.html"
  basename "$hit"
}

wait_for_health() {
  local url="http://127.0.0.1:8080/api/health"
  local timeout="${HEALTH_TIMEOUT_SEC:-120}"
  local start
  start="$(date +%s)"
  echo "==> Waiting for $url"
  while true; do
    if curl -fsS "$url" 2>/dev/null | grep -q '"status"[[:space:]]*:[[:space:]]*"ok"'; then
      echo "    Server is up"
      return 0
    fi
    if (( $(date +%s) - start > timeout )); then
      fail "Timed out after ${timeout}s waiting for $url"
    fi
    sleep 1
  done
}

find_bundled_server_jar() {
  local jar
  jar="$(find "$BUNDLE_ROOT" -path '*/server/server.jar' 2>/dev/null | head -1 || true)"
  [[ -n "$jar" ]] || fail "Could not find server/server.jar under $BUNDLE_ROOT (did tauri build succeed?)"
  echo "$jar"
}

launch_app() {
  case "$(uname -s)" in
    Darwin)
      local app="$BUNDLE_ROOT/macos/OpenBill.app"
      [[ -d "$app" ]] || fail "Missing $app"
      echo "==> Opening $app"
      open "$app"
      ;;
    Linux)
      local appimage
      appimage="$(find "$BUNDLE_ROOT" -name '*.AppImage' 2>/dev/null | head -1 || true)"
      if [[ -n "$appimage" ]]; then
        chmod +x "$appimage"
        echo "==> Launching $appimage"
        "$appimage" &
        return
      fi
      for bin in "$DESKTOP/src-tauri/target/release/openbill" "$DESKTOP/src-tauri/target/release/OpenBill"; do
        if [[ -x "$bin" ]]; then
          echo "==> Launching $bin"
          "$bin" &
          return
        fi
      done
      fail "Could not find Linux AppImage or release binary after build"
      ;;
  esac
}

echo "==> Stopping any running OpenBill"
pkill -f 'OpenBill.app' 2>/dev/null || true

echo "==> Freeing ports 8080, 9090, 8081"
if PIDS="$(lsof -t -i :8080 -i :9090 -i :8081 2>/dev/null || true)"; then
  if [[ -n "$PIDS" ]]; then
    # shellcheck disable=SC2086
    kill $PIDS 2>/dev/null || true
    sleep 1
  fi
fi
if lsof -nP -i :8080 -i :9090 -i :8081 2>/dev/null | grep -q LISTEN; then
  fail "Ports 8080/9090/8081 still in use — stop those processes and retry"
fi

echo "==> Building server bundle"
"$ROOT/scripts/build-server.sh"

echo "==> Fixing dist-server permissions"
chmod -R u+w "$ROOT/dist-server"

echo "==> Removing stale copied server resources from last desktop build"
rm -rf "$DESKTOP/src-tauri/target/release/server"

echo "==> Building desktop app"
(cd "$DESKTOP" && npm run build)

BUNDLED_JAR="$(find_bundled_server_jar)"
BUNDLED_ASSET="$(index_asset_from_jar "$BUNDLED_JAR")"
echo "==> Bundled billing JS: $BUNDLED_ASSET (in $BUNDLED_JAR)"

launch_app
wait_for_health

SERVED_ASSET="$(index_asset_from_html)"
echo "==> Served billing JS on :8080: $SERVED_ASSET"

if [[ "$BUNDLED_ASSET" != "$SERVED_ASSET" ]]; then
  echo >&2
  fail "Bundle mismatch: jar has $BUNDLED_ASSET but :8080 serves $SERVED_ASSET (stale desktop install or wrong process on 8080)"
fi

echo "==> OK — bundled and served assets match ($BUNDLED_ASSET)"
