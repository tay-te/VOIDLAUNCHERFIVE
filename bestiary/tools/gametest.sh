#!/usr/bin/env bash
# Runs the motion showcase (client game test) while holding one of the machine's two game slots
# (C:/Users/bitto/VOID/.tools/locks/game1|game2, polled every 20 s), released by a trap.
#   bash tools/gametest.sh [gradle args, e.g. -Piris -Dfray.show.tag=iris -Dfray.show.only=zombie,perf]
# Screenshots: build/run/clientGameTest/screenshots; report: build/run/clientGameTest/fray-report-<tag>.txt;
# log: build/gametest[-<tag>].log
set -u
P=$(cd "$(dirname "$0")/.." && pwd)
L=/c/Users/bitto/VOID/.tools/locks
TAG=${GT_TAG:-}
slot=""
while [ -z "$slot" ]; do
  for i in 1 2; do
    if mkdir "$L/game$i" 2>/dev/null; then
      slot="$L/game$i"
      echo "bestiary-motion $(date +%T)" > "$slot/owner"
      break
    fi
  done
  [ -z "$slot" ] && { echo "waiting for a game slot $(date +%T)"; sleep 20; }
done
trap 'rm -rf "$slot"' EXIT INT TERM
echo "slot $slot"
export JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25
cd "$P" || exit 1
LOG=build/gametest${TAG:+-$TAG}.log
mkdir -p build
./gradlew runClientGameTest --offline --console=plain "$@" > "$LOG" 2>&1
code=$?
grep -a "\[fray\]" "$LOG" | tail -120
grep -aiE "Mixin apply failed|InvalidInjection|Exception|FAILED" "$LOG" | grep -av "^\s*at " | head -20
echo "exit $code"
exit $code
