#!/usr/bin/env bash
# Builds everything the desktop app bundles, into dist-server/:
#   dist-server/server.jar   Spring Boot server (with the React apps inside)
#   dist-server/jre/         Minimal Java runtime for THIS OS/CPU (jlink)
#
# Run it on each OS you ship for (CI does this for Windows, macOS and Linux).
# Needs: JDK 21 (JAVA_HOME or on PATH), Maven, Node 20+.
# Works in Git Bash on Windows.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/dist-server"

echo "==> Building web apps"
(cd "$ROOT/web" && npm ci && npm run build)

echo "==> Building server jar"
MVN_ARGS="-B clean package"
if [[ "${SKIP_TESTS:-false}" == "true" ]]; then
  MVN_ARGS="$MVN_ARGS -DskipTests"
fi
(cd "$ROOT/server" && mvn $MVN_ARGS)

echo "==> Creating bundled Java runtime"
rm -rf "$OUT"
mkdir -p "$OUT"
cp "$ROOT/server/target/server.jar" "$OUT/server.jar"

JLINK="jlink"
if [[ -n "${JAVA_HOME:-}" ]]; then
  JLINK="$JAVA_HOME/bin/jlink"
fi

# Modules Spring Boot + Tomcat + Hibernate + SQLite need. If the server fails with
# NoClassDefFoundError for a java.* / jdk.* class, add its module here.
MODULES="java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.crypto.ec,jdk.management,jdk.naming.dns,jdk.unsupported,jdk.zipfs"

"$JLINK" \
  --add-modules "$MODULES" \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --compress=zip-6 \
  --output "$OUT/jre"

# jlink output is read-only; desktop bundling copies from dist-server and needs write access.
chmod -R u+w "$OUT"
touch "$OUT/.gitkeep"
echo "==> Done: $OUT"
du -sh "$OUT" 2>/dev/null || true
