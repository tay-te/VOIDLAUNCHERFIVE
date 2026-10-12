#!/usr/bin/env bash
# Runs one game JVM outside Gradle from the class paths `./gradlew auditLaunchers` writes (build/audit/<run>.args),
# so a dedicated server and several clients can run at once.
#   RUN=runAuditServer game.sh server <runDir>   a dedicated server (Knot server); console fed from <runDir>/console.in
#   RUN=runAuditA      game.sh client <runDir>   a client (Knot client)
# Extra JVM args in $JVM_EXTRA, program args in $PROG_ARGS. Output: <runDir>/console.log. The caller holds the game lock.
set -u
J=/c/Users/bitto/VOID/.tools/jdk-25/bin/java.exe
W=$(cd "$(dirname "$0")/../.." && pwd -W)
A=$W/build/audit
KIND=$1; DIR=$2; RUN=${RUN:?}; JVM_EXTRA=${JVM_EXTRA:-}; PROG_ARGS=${PROG_ARGS:-}
mkdir -p "$DIR"
cd "$DIR" || exit 1
CFG="-Dfabric.dli.config=$W/.gradle/loom-cache/launch.cfg"
COMMON="--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US"
if [ "$KIND" = server ]; then
  : > console.in
  exec 3< <(tail -f console.in)
  TP=${!:-}
  "$J" -Xmx2G $CFG -Dfabric.dli.env=server -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer \
    $COMMON $JVM_EXTRA "@$A/$RUN.args" nogui $PROG_ARGS > console.log 2>&1 <&3
  code=$?
  exec 3<&-
  [ -n "$TP" ] && kill "$TP" 2>/dev/null
  echo "exit $code" >> console.log
else
  "$J" -Xmx2G $CFG -Dfabric.dli.env=client -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient -XX:StackShadowPages=32 \
    $COMMON $JVM_EXTRA "@$A/$RUN.args" $PROG_ARGS > console.log 2>&1
  echo "exit $?" >> console.log
fi
