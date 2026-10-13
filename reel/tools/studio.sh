#!/usr/bin/env bash
# Runs the Reel studio (the client game test in src/gametest) while holding one of the machine's two game slots
# (C:/Users/bitto/VOID/.tools/locks/game1|game2; a slot older than 3 hours counts as stale), and releases it on exit.
#
#   bash tools/studio.sh -Preel.shots=smoke,smoke-run              film shots, then quit
#   bash tools/studio.sh -Preel.shots=example -Preel.mode=stills   eight stills and a contact sheet per shot
#   bash tools/studio.sh -Preel.studio=60                          stay open an hour on the control port (tools/reel.sh)
#   add -Pexpanse for Earth terrain, -Piris for shader packs, -Preel.seed=N, -Preel.distance=N (chunks)
#
# Log: build/studio.log. Frames: renders/<shot>/.
set -u
R="$(cd "$(dirname "$0")/.." && pwd)"
L=/c/Users/bitto/VOID/.tools/locks
slot=""
while [ -z "$slot" ]; do
  # stale after 3 hours (the machine-wide rule); a slot may hold an owner file, so remove it whole
  find "$L" -maxdepth 1 -name 'game[0-9]' -type d -mmin +180 -exec rm -rf {} + 2>/dev/null
  for i in 1 2; do
    if mkdir "$L/game$i" 2>/dev/null; then slot="$L/game$i"; echo "reel $(date +%T)" > "$slot/owner"; break; fi
  done
  [ -z "$slot" ] && { echo "waiting for a game slot"; sleep 20; }
done
trap 'rm -rf "$slot" 2>/dev/null' EXIT
trap 'exit 1' INT TERM
export JAVA_HOME="C:/Users/bitto/VOID/.tools/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$R" || exit 1
mkdir -p build
# each run makes a fresh world from the seed; the last one is only disk space
rm -rf "$R/build/run/clientGameTest/saves/New World"
./gradlew runClientGameTest --console=plain "$@" > build/studio.log 2>&1
code=$?
grep "\[reel\]" build/studio.log | grep -v "^\s*at "
grep -iE "Mixin apply failed|InvalidInjection|Exception|FAILED" build/studio.log | grep -v "^\s*at " | head -20
echo "exit $code (log: $R/build/studio.log)"
exit $code
