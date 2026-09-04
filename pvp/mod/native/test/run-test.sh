#!/usr/bin/env bash
# Builds (if needed) and runs the CPU-renderer smoke test on Linux.
#
# The classpath is deliberately the *shipping* shape: build/ contributes natives/<key>/… exactly as
# the mod JAR will, so the run exercises files.txt, the temp-dir extraction and System.load() in
# dependency order — not just the API.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
native="$(dirname "$here")"
build="${VOIDUL_BUILD_DIR:-$native/build}"

if [[ ! -f "$build/voidultralight.so" && ! -f "$build/voidultralight.dylib" ]]; then
  "$native/scripts/build.sh"
fi

out="${1:-$native/test/expected/out.png}"

exec java \
  -cp "$build/test-classes:$build/java-classes:$build:$native/test/resources" \
  dev.voidclient.ultralight.test.CpuRenderTest "$out"
