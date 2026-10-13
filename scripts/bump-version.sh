#!/usr/bin/env bash
# Usage: ./scripts/bump-version.sh 0.2.0
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VER="${1:?Usage: $0 <x.y.z>}"
if ! [[ "$VER" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must be x.y.z" >&2
  exit 1
fi

TAURI="$ROOT/desktop/src-tauri/tauri.conf.json"
CARGO="$ROOT/desktop/src-tauri/Cargo.toml"
POM="$ROOT/server/pom.xml"
PKG="$ROOT/web/package.json"

python3 << PY
import json, re, pathlib
ver = "$VER"
tauri = pathlib.Path("$TAURI")
data = json.loads(tauri.read_text())
data["version"] = ver
tauri.write_text(json.dumps(data, indent=2) + "\n")

cargo = pathlib.Path("$CARGO").read_text()
cargo = re.sub(r'^version = ".*"$', f'version = "{ver}"', cargo, count=1, flags=re.M)
pathlib.Path("$CARGO").write_text(cargo)

pom = pathlib.Path("$POM").read_text()
pom = re.sub(r'(<artifactId>billing-server</artifactId>\s*\n\s*<version>)[^<]+(</version>)', rf'\g<1>{ver}\2', pom, count=1)
pathlib.Path("$POM").write_text(pom)

pkg = json.loads(pathlib.Path("$PKG").read_text())
pkg["version"] = ver
pathlib.Path("$PKG").write_text(json.dumps(pkg, indent=2) + "\n")
print(f"Bumped to {ver}")
PY

echo "  $TAURI"
echo "  $CARGO"
echo "  $POM"
echo "  $PKG"
