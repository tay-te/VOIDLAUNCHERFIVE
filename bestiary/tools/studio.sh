#!/usr/bin/env bash
# The Motion Studio (docs/STUDIO.md): a persistent dev client with a control port and live reload.
#   bash tools/studio.sh start [gradle args, e.g. -Piris]   takes a game slot (C:/Users/bitto/VOID/.tools/locks/game1|game2,
#                                                           polled every 20 s) and starts the studio; returns once it's ready
#   bash tools/studio.sh cmd "play bestiary:player/dodge_left; cam right; gif"
#   bash tools/studio.sh repl                               one command per line from stdin
#   bash tools/studio.sh stop                               quits the studio; the slot is released when it exits
# Pictures land in build/studio/{shots,strips,gif,split}; the log in build/studio/studio.log.
set -u
P=$(cd "$(dirname "$0")/.." && pwd)
PORT=${STUDIO_PORT:-25700}
OUT=$P/build/studio
L=/c/Users/bitto/VOID/.tools/locks

send() {
  curl -s --max-time 900 --data-binary "$1" "http://127.0.0.1:$PORT/"
}

case "${1:-}" in
  start)
    shift
    if curl -s --max-time 2 "http://127.0.0.1:$PORT/?status" >/dev/null 2>&1; then
      echo "studio already running on $PORT"; send status; exit 0
    fi
    mkdir -p "$OUT"
    rm -f "$OUT/ready"
    nohup bash -c '
      L='"$L"'; P='"$P"'; OUT='"$OUT"'
      slot=""
      while [ -z "$slot" ]; do
        for i in 1 2; do
          if mkdir "$L/game$i" 2>/dev/null; then slot="$L/game$i"; echo "bestiary-studio $(date +%T)" > "$slot/owner"; break; fi
        done
        [ -z "$slot" ] && { echo "waiting for a game slot $(date +%T)"; sleep 20; }
      done
      trap "rm -rf \"$slot\"" EXIT INT TERM
      echo "slot $slot"
      export JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25
      cd "$P" && ./gradlew runClientGameTest --offline --console=plain -Dfray.studio=true -Dfray.studio.port='"$PORT"' '"$*"'
    ' > "$OUT/studio.log" 2>&1 &
    disown
    echo "starting (log: $OUT/studio.log)"
    for i in $(seq 1 360); do
      [ -e "$OUT/ready" ] && { echo "ready on $PORT"; send status; exit 0; }
      if grep -qaE "BUILD FAILED|FAILURE:" "$OUT/studio.log" 2>/dev/null; then tail -30 "$OUT/studio.log"; exit 1; fi
      sleep 5
    done
    echo "studio not ready after 30 min"; tail -20 "$OUT/studio.log"; exit 1
    ;;
  cmd)
    shift
    send "$*"
    ;;
  repl)
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      send "$line"
    done
    ;;
  stop)
    send quit
    for i in $(seq 1 60); do
      grep -qaE "BUILD SUCCESSFUL|BUILD FAILED" "$OUT/studio.log" 2>/dev/null && { tail -3 "$OUT/studio.log"; exit 0; }
      sleep 2
    done
    echo "the studio didn't stop in 2 minutes"; exit 1
    ;;
  *)
    sed -n '2,8p' "$0"; exit 2
    ;;
esac
