#!/usr/bin/env bash
# Launches Minecraft 26.3 with VOID Expanse and VOID LOD from source (macOS and Linux).
# Run scripts/setup-dev.sh once first. Extra arguments go to Gradle; LOD settings pass through, e.g.:
#   scripts/play.sh -Pvoid.lod.radius=16384 -Pvoid.lod.detail=4
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK_DIR="$ROOT/.tools/jdk-25"
if [ -d "$JDK_DIR/Contents/Home" ]; then
	export JAVA_HOME="$JDK_DIR/Contents/Home"
elif [ -x "$JDK_DIR/bin/java" ]; then
	export JAVA_HOME="$JDK_DIR"
fi
if [ -n "${JAVA_HOME:-}" ]; then
	export PATH="$JAVA_HOME/bin:$PATH"
fi
cd "$ROOT/lod"
exec ./gradlew runClient "$@"
