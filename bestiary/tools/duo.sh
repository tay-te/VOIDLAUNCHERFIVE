#!/usr/bin/env bash
# The two-client duo (gametest MotionDuo) on one dedicated server, holding BOTH game slots
# (C:/Users/bitto/VOID/.tools/locks/game1 and game2, polled every 20 s), released by a trap.
#   bash tools/duo.sh <tag> [lag ms for client B, default 0] [gradle args for the host]
# Host (role A): ./gradlew runClientGameTest -Dfray.duo.role=A (Fabric's in-process dedicated server on :25612).
# Guest (role B, "Bob"): a second client JVM from build/duo/runGuest.args (./gradlew frayLaunchers).
# Everything lands in build/duo/<tag>/ (markers, traces, both reports, both logs); screenshots stay in each run dir.
set -u
P=$(cd "$(dirname "$0")/.." && pwd)
W=$(cd "$P" && pwd -W)
TAG=${1:?tag}
LAG=${2:-0}
shift; [ $# -gt 0 ] && shift
L=/c/Users/bitto/VOID/.tools/locks
OUT=$P/build/duo/$TAG
WOUT=$W/build/duo/$TAG
J=/c/Users/bitto/VOID/.tools/jdk-25/bin/java.exe
export JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25

HELD=()
PIDS=()
cleanup() {
  for p in "${PIDS[@]}"; do
    w=$(cat /proc/$p/winpid 2>/dev/null) && taskkill //F //T //PID "$w" >/dev/null 2>&1
    kill "$p" 2>/dev/null
  done
  for d in "${HELD[@]}"; do rm -rf "$d"; done
}
trap cleanup EXIT
trap 'exit 130' INT TERM
for want in 1 2; do
  got=
  until [ -n "$got" ]; do
    for i in 1 2; do
      if [[ " ${HELD[*]} " != *" $L/game$i "* ]] && mkdir "$L/game$i" 2>/dev/null; then
        echo "bestiary-duo $(date +%T)" > "$L/game$i/owner"
        HELD+=("$L/game$i"); got=1; break
      fi
    done
    [ -n "$got" ] || { echo "waiting for game slots (have ${#HELD[@]}) $(date +%T)"; sleep 20; }
  done
done
echo "slots: ${HELD[*]}"

cd "$P" || exit 1
./gradlew --offline -q frayLaunchers || exit 1
rm -rf "$OUT"; mkdir -p "$OUT"
rm -rf build/run/clientGameTest/screenshots build/run/guest/screenshots

./gradlew runClientGameTest --offline --console=plain -Dfray.duo.role=A -Dfray.duo.dir="$WOUT" "$@" > "$OUT/host.log" 2>&1 &
HOST=$!
PIDS+=($HOST)
echo "host started"
until [ -e "$OUT/A.server" ] || [ -e "$OUT/A.failed" ] || ! kill -0 $HOST 2>/dev/null; do sleep 3; done
[ -e "$OUT/A.server" ] || { echo "host never came up"; tail -30 "$OUT/host.log"; exit 1; }

CPB=$(sed -n '/^"-cp"$/{n;p;}' "$P/build/duo/runGuest.args" | tr -d '"')
mkdir -p "$P/build/run/guest"
( cd "$P/build/run/guest" && exec "$J" -Xmx2G -Dfabric.dli.config=$W/.gradle/loom-cache/launch.cfg -Dfabric.dli.env=client \
  -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient -XX:StackShadowPages=32 --enable-native-access=ALL-UNNAMED \
  --sun-misc-unsafe-memory-access=allow -Dfile.encoding=UTF-8 -Dfabric.client.gametest \
  -Dfabric.client.gametest.testModResourcesPath=$W/src/gametest/resources -Dfray.duo.role=B -Dfray.duo.dir="$WOUT" -Dfray.duo.lag=$LAG \
  -cp "$CPB" net.fabricmc.devlaunchinjector.Main --username Bob > "$OUT/guest.log" 2>&1 ) &
GUEST=$!
PIDS+=($GUEST)
echo "guest started"

until [ -e "$OUT/B.done" ] || [ -e "$OUT/B.failed" ] || [ -e "$OUT/A.failed" ] || ! kill -0 $GUEST 2>/dev/null; do sleep 5; done
echo "guest finished: $(ls "$OUT" | tr '\n' ' ')"
t=0; until ! kill -0 $GUEST 2>/dev/null || [ $t -ge 60 ]; do sleep 2; t=$((t+2)); done
t=0; until ! kill -0 $HOST 2>/dev/null || [ $t -ge 180 ]; do sleep 3; t=$((t+3)); done
echo "host finished"
grep -a "\[fray\]" "$OUT/host.log" | sed 's/^.*\[STDOUT\]: //' | tail -40
echo "--- guest"
grep -a "\[fray\]" "$OUT/guest.log" | sed 's/^.*\[STDOUT\]: //' | tail -20
cp -r build/run/clientGameTest/screenshots "$OUT/host-screenshots" 2>/dev/null
cp -r build/run/guest/screenshots "$OUT/guest-screenshots" 2>/dev/null
cp build/run/clientGameTest/fray-report-duo-A.txt build/run/guest/fray-report-duo-B.txt "$OUT/" 2>/dev/null
tail -3 "$OUT/host.log"
