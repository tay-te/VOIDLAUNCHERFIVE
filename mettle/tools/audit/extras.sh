#!/usr/bin/env bash
# Mod combinations against a dedicated server (run under tools/audit/locked.sh 2):
#   1. a Mettle server (ServerAudit role H, :25599): a Fabric client without Mettle (Carol), the vanilla client (Dave),
#      a client with an older Mettle (Erin, $OLD_JAR) try to join; the server log says what happened to each.
#   2. a Fabric server without Mettle (BareHost, :25598): a client with Mettle (ServerAudit role M) joins and uses Mettle.
# Output: build/audit/<tag>/.
set -u
P=/c/Users/bitto/VOID/.claude/worktrees/audit-server/mettle
W=C:/Users/bitto/VOID/.claude/worktrees/audit-server/mettle
TAG=${1:?tag}; shift
OUT=$P/build/audit/$TAG
WOUT=$W/build/audit/$TAG
J=/c/Users/bitto/VOID/.tools/jdk-25/bin/java.exe
OLD_JAR=${OLD_JAR:-}
PHASES=${PHASES:-12}
rm -rf "$OUT"; mkdir -p "$OUT"
export JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25
PIDS=()
killpid() { local w; w=$(cat /proc/$1/winpid 2>/dev/null) && taskkill //F //T //PID "$w" >/dev/null 2>&1; kill "$1" 2>/dev/null; }
cleanup() { for p in "${PIDS[@]}"; do killpid "$p"; done; }
trap cleanup EXIT INT TERM
DLI="-Dfabric.dli.config=$W/.gradle/loom-cache/launch.cfg -Dfabric.dli.env=client -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient"
JV="-XX:StackShadowPages=32 --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -Dfile.encoding=UTF-8"
cp_of() { sed -n 2p "$P/build/audit/$1.args" | tr -d '"'; }
HOSTLOG=$P/build/run/clientGameTest/logs/latest.log

joiner() { # name mainclass classpath [jvm args...]; program args in $JARGS
  local name=$1 main=$2 cp=$3; shift 3
  local dir="$P/build/run/audit$name"
  mkdir -p "$dir"
  printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\ntutorialStep:none\n' > "$dir/options.txt"
  ( cd "$dir" && exec "$J" -Xmx1G $JV "$@" -cp "$cp" "$main" --username $name --quickPlayMultiplayer localhost:25599 $JARGS \
    > "$OUT/join-$name.log" 2>&1 ) &
  local pid=$!
  PIDS+=($pid)
  local t=0
  until grep -a -q "$name.*\(lost connection\|Disconnecting\|joined the game\)" "$HOSTLOG" 2>/dev/null || [ $t -ge 180 ]; do sleep 3; t=$((t+3)); done
  sleep 6
  grep -a "$name" "$HOSTLOG" | grep -v "AUDIT\|Setting user" | tail -6 > "$OUT/join-$name.server.txt"
  grep -a -i "connect\|disconnect\|kick\|incompatible\|registry\|missing\|error" "$OUT/join-$name.log" | grep -v -i "oshi\|perflib\|anisotropic" | tail -12 > "$OUT/join-$name.client.txt"
  echo "== $name server: $(cat "$OUT/join-$name.server.txt")"
  killpid $pid
  sleep 3
}

if [[ $PHASES == *1* ]]; then
  cd "$P"
  ./gradlew runClientGameTest --offline -Dmettle.audit.role=H -Dmettle.audit.dir="$WOUT" > "$OUT/host.log" 2>&1 &
  HOST=$!
  until [ -e "$OUT/H.waiting_extras" ] || [ -e "$OUT/H.failed" ] || ! kill -0 $HOST 2>/dev/null; do sleep 3; done
  if [ -e "$OUT/H.waiting_extras" ]; then
    CPBARE=$(cp_of runAuditClientBare)
    JARGS="" joiner Carol net.fabricmc.devlaunchinjector.Main "$CPBARE" $DLI
    CPV=$(echo "$CPBARE" | tr ';' '\n' | grep -v -i "fabric\|mixin\|asm-\|dev-launch\|/build/\|minecraft-common\|minecraft-clientOnly\|loom-cache" | tr '\n' ';')
    CPV="${CPV}C:/Users/bitto/.gradle/caches/fabric-loom/26.3/minecraft-client.jar"
    JARGS="--version 26.3 --accessToken 0 --assetsDir C:/Users/bitto/.gradle/caches/fabric-loom/assets --assetIndex 26.3-34 --gameDir ." \
      joiner Dave net.minecraft.client.main.Main "$CPV"
    if [ -n "$OLD_JAR" ]; then
      JARGS="" joiner Erin net.fabricmc.devlaunchinjector.Main "$CPBARE;$OLD_JAR" $DLI
    fi
  fi
  touch "$OUT/extras.done"
  until ! kill -0 $HOST 2>/dev/null; do sleep 3; done
  echo "phase 1 done"
fi

if [[ $PHASES == *2* ]]; then
  ( cd "$P/build/run/clientGameTest" && exec "$J" -Xmx2G $JV $DLI -Dfabric.client.gametest \
    -Dfabric.client.gametest.testModResourcesPath=$W/src/auditBareHost/resources -Dmettle.audit.dir="$WOUT" \
    -cp "$(cp_of runAuditBareHost)" net.fabricmc.devlaunchinjector.Main --username Host > "$OUT/barehost.log" 2>&1 ) &
  BARE=$!
  PIDS+=($BARE)
  until [ -e "$OUT/bare.server" ] || ! kill -0 $BARE 2>/dev/null; do sleep 3; done
  mkdir -p "$P/build/run/auditM"
  ( cd "$P/build/run/auditM" && exec "$J" -Xmx2G $JV $DLI -Dfabric.client.gametest \
    -Dfabric.client.gametest.testModResourcesPath=$W/src/gametest/resources -Dmettle.audit.role=M -Dmettle.audit.port=25598 \
    -Dmettle.audit.dir="$WOUT" -cp "$(cp_of runAuditB)" net.fabricmc.devlaunchinjector.Main --username Mia > "$OUT/mclient.log" 2>&1 ) &
  MC=$!
  PIDS+=($MC)
  t=0
  until ! kill -0 $MC 2>/dev/null || [ $t -ge 600 ]; do sleep 5; t=$((t+5)); done
  t=0
  until ! kill -0 $BARE 2>/dev/null || [ $t -ge 120 ]; do sleep 5; t=$((t+5)); done
  echo "phase 2 done"
fi
