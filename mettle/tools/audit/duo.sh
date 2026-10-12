#!/usr/bin/env bash
# The two-player dedicated-server audit (ServerAudit), then other clients trying to join the same server.
#   locked.sh 2 bash tools/audit/duo.sh <tag> [gradle args for host, e.g. -Phearth]
# Host (role A): ./gradlew runClientGameTest (Fabric's in-process DedicatedServer on :25599, joined over TCP).
# Guest (role B): a second client JVM (Bob). Extras (after B): a Fabric client without Mettle (Carol), a vanilla client
# (Dave, the pristine Mojang client jar), and a client with an older Mettle (Erin, -Dold.jar=...).
# Everything lands in build/audit/<tag>/.
set -u
P=$(cd "$(dirname "$0")/../.." && pwd)
W=$(cd "$P" && pwd -W)
TAG=${1:?tag}; shift
OUT=$P/build/audit/$TAG
WOUT=$W/build/audit/$TAG
J=/c/Users/bitto/VOID/.tools/jdk-25/bin/java.exe
OLD_JAR=${OLD_JAR:-}
EXTRA_CP=${EXTRA_CP:-}
rm -rf "$OUT"; mkdir -p "$OUT"
export JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25
PIDS=()
cleanup() {
  for p in "${PIDS[@]}"; do
    w=$(cat /proc/$p/winpid 2>/dev/null) && taskkill //F //T //PID "$w" >/dev/null 2>&1
    kill "$p" 2>/dev/null
  done
}
trap cleanup EXIT INT TERM

cd "$P"
./gradlew runClientGameTest --offline -Dmettle.audit.role=A -Dmettle.audit.dir="$WOUT" "$@" > "$OUT/host.log" 2>&1 &
HOST=$!
echo "host started"

until [ -e "$OUT/A.server" ] || [ -e "$OUT/A.failed" ] || ! kill -0 $HOST 2>/dev/null; do sleep 3; done
[ -e "$OUT/A.server" ] || { echo "host never came up"; exit 1; }

CPB=$(sed -n 2p "$P/build/audit/runAuditB.args" | tr -d '"')
[ -n "$EXTRA_CP" ] && CPB="$CPB;$EXTRA_CP"
mkdir -p "$P/build/run/auditB"
( cd "$P/build/run/auditB" && exec "$J" -Xmx2G -Dfabric.dli.config=$W/.gradle/loom-cache/launch.cfg -Dfabric.dli.env=client \
  -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient -XX:StackShadowPages=32 --enable-native-access=ALL-UNNAMED \
  --sun-misc-unsafe-memory-access=allow -Dfile.encoding=UTF-8 -Dfabric.client.gametest \
  -Dfabric.client.gametest.testModResourcesPath=$W/src/gametest/resources -Dmettle.audit.role=B -Dmettle.audit.dir="$WOUT" \
  -cp "$CPB" net.fabricmc.devlaunchinjector.Main --username Bob > "$OUT/guest.log" 2>&1 ) &
GUEST=$!
PIDS+=($GUEST)
echo "guest started"

until [ -e "$OUT/B.done" ] || [ -e "$OUT/B.failed" ] || [ -e "$OUT/A.failed" ] || ! kill -0 $GUEST 2>/dev/null; do sleep 5; done
echo "guest finished: $(ls $OUT)"
until ! kill -0 $GUEST 2>/dev/null; do sleep 2; done

HOSTLOG=$P/build/run/clientGameTest/logs/latest.log
joiner() { # name mainclass classpath extra-args...
  local name=$1 main=$2 cp=$3; shift 3
  mkdir -p "$P/build/run/audit$name"
  ( cd "$P/build/run/audit$name" && exec "$J" -Xmx1G -XX:StackShadowPages=32 --enable-native-access=ALL-UNNAMED \
    --sun-misc-unsafe-memory-access=allow -Dfile.encoding=UTF-8 "$@" -cp "$cp" "$main" --username $name \
    --quickPlayMultiplayer localhost:25599 $JARGS > "$OUT/join-$name.log" 2>&1 ) &
  local pid=$!
  PIDS+=($pid)
  local t=0
  until grep -q "$name.*\(lost connection\|Disconnecting\|joined the game\)" "$HOSTLOG" 2>/dev/null || [ $t -ge 120 ]; do sleep 3; t=$((t+3)); done
  sleep 5
  grep -a "$name" "$HOSTLOG" | grep -v "AUDIT" | tail -5 > "$OUT/join-$name.server.txt"
  echo "== $name: $(cat "$OUT/join-$name.server.txt")"
  w=$(cat /proc/$pid/winpid 2>/dev/null) && taskkill //F //T //PID "$w" >/dev/null 2>&1
  sleep 3
}

if [ -z "${SKIP_EXTRAS:-}" ] && [ -e "$OUT/A.waiting_extras" ]; then
  CPBARE=$(sed -n 2p "$P/build/audit/runAuditClientBare.args" | tr -d '"')
  DLI="-Dfabric.dli.config=$W/.gradle/loom-cache/launch.cfg -Dfabric.dli.env=client -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient"
  JARGS="" joiner Carol net.fabricmc.devlaunchinjector.Main "$CPBARE" $DLI
  # Vanilla: the pristine client jar and the plain libraries, no Fabric anywhere.
  CPV=$(echo "$CPBARE" | tr ';' '\n' | grep -v -i "fabric\|mixin\|asm-\|dev-launch\|/build/\|minecraft-common\|minecraft-clientOnly\|loom-cache" | tr '\n' ';')
  CPV="${CPV}C:/Users/bitto/.gradle/caches/fabric-loom/26.3/minecraft-client.jar"
  JARGS="--version 26.3 --accessToken 0 --assetsDir C:/Users/bitto/.gradle/caches/fabric-loom/assets --assetIndex 26.3-34 --gameDir ." \
    joiner Dave net.minecraft.client.main.Main "$CPV"
  if [ -n "$OLD_JAR" ]; then
    JARGS="" joiner Erin net.fabricmc.devlaunchinjector.Main "$CPBARE;$OLD_JAR" $DLI
  fi
fi
touch "$OUT/extras.done"
until ! kill -0 $HOST 2>/dev/null; do sleep 5; done
echo "host finished"
tail -5 "$OUT/host.log"
