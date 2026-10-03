#!/usr/bin/env bash
# One-command local setup for the Minecraft mods in this repo (macOS and Linux).
#
#   scripts/setup-dev.sh             # Java 25 + build Expanse and VOID LOD + run the tests
#   scripts/setup-dev.sh --launcher  # ...and install the Electron launcher's npm packages too
#
# Java 25 goes into .tools/ inside the repo (no admin rights, nothing installed system-wide) unless
# a Java 25 or newer is already on your PATH. Then: scripts/play.sh to launch Minecraft with both mods.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$ROOT/.tools"
JDK_DIR="$TOOLS/jdk-25"
WITH_LAUNCHER=0
for arg in "$@"; do
	case "$arg" in
		--launcher) WITH_LAUNCHER=1 ;;
		*) echo "unknown option: $arg" >&2; exit 2 ;;
	esac
done

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

java_major() { "$1" -version 2>&1 | awk -F '"' '/version/ {split($2, v, "."); print v[1]; exit}'; }

say "Checking tools"
command -v git >/dev/null || { echo "git is required: https://git-scm.com/downloads" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }

# ---- Java 25 ---------------------------------------------------------------------------------------
JAVA_HOME_FOUND=""
if command -v java >/dev/null && [ "$(java_major java)" -ge 25 ] 2>/dev/null; then
	JAVA_HOME_FOUND="$(dirname "$(dirname "$(readlink -f "$(command -v java)" 2>/dev/null || command -v java)")")"
	echo "Using the Java on your PATH ($(java_major java)): $JAVA_HOME_FOUND"
elif [ -x "$JDK_DIR/bin/java" ] || [ -x "$JDK_DIR/Contents/Home/bin/java" ]; then
	echo "Java 25 already in .tools/"
else
	case "$(uname -s)" in
		Darwin) OS=mac ;;
		Linux) OS=linux ;;
		*) echo "Unsupported OS $(uname -s): on Windows run scripts/setup-dev.ps1" >&2; exit 1 ;;
	esac
	case "$(uname -m)" in
		x86_64|amd64) ARCH=x64 ;;
		arm64|aarch64) ARCH=aarch64 ;;
		*) echo "Unsupported CPU $(uname -m)" >&2; exit 1 ;;
	esac
	say "Downloading Java 25 (Eclipse Temurin, $OS/$ARCH) into .tools/"
	mkdir -p "$TOOLS"
	curl -fL --progress-bar -o "$TOOLS/jdk25.tar.gz" \
		"https://api.adoptium.net/v3/binary/latest/25/ga/$OS/$ARCH/jdk/hotspot/normal/eclipse"
	rm -rf "$JDK_DIR" && mkdir -p "$JDK_DIR"
	tar -xzf "$TOOLS/jdk25.tar.gz" -C "$JDK_DIR" --strip-components=1
	rm -f "$TOOLS/jdk25.tar.gz"
fi
if [ -n "$JAVA_HOME_FOUND" ]; then
	export JAVA_HOME="$JAVA_HOME_FOUND"
elif [ -d "$JDK_DIR/Contents/Home" ]; then
	export JAVA_HOME="$JDK_DIR/Contents/Home" # macOS bundles the JDK inside Contents/Home
else
	export JAVA_HOME="$JDK_DIR"
fi
export PATH="$JAVA_HOME/bin:$PATH"
echo "JAVA_HOME=$JAVA_HOME ($(java_major "$JAVA_HOME/bin/java"))"

# ---- the mods --------------------------------------------------------------------------------------
say "Building Expanse and VOID LOD (first run downloads Minecraft 26.3 and Fabric, a few minutes)"
chmod +x "$ROOT/expanse/gradlew" "$ROOT/lod/gradlew"
(cd "$ROOT/lod" && ./gradlew build)
echo "Mod jars:"
ls -1 "$ROOT"/lod/build/libs/*.jar "$ROOT"/expanse/build/libs/*.jar 2>/dev/null | grep -v sources || true

# ---- the launcher (optional) -----------------------------------------------------------------------
if [ "$WITH_LAUNCHER" = 1 ]; then
	say "Installing the launcher's npm packages"
	command -v npm >/dev/null || { echo "Node.js 20+ is required for the launcher: https://nodejs.org" >&2; exit 1; }
	(cd "$ROOT" && npm install)
	echo "Start the launcher with: npm start"
fi

say "Done"
cat <<MSG
  Play:   scripts/play.sh                  (Minecraft 26.3 + Expanse + VOID LOD, F8 toggles the LOD)
  Bench:  cd lod && ./gradlew lodBench     (the horizon benchmark, no game)
  Tests:  cd lod && ./gradlew test
MSG
