#!/usr/bin/env bash
# Talks to a running Reel (the studio with -Preel.studio=N, or any dev run with -Preel.control=PORT).
#   bash tools/reel.sh status | shots | where | stop | quit
#   bash tools/reel.sh check  <shot>
#   bash tools/reel.sh play   <shot>
#   bash tools/reel.sh render <shot> [samples=8 w=1920 h=1080 encode=false]
#   bash tools/reel.sh stills <shot> [count=8 | at=0,2.5,6] [w=.. h=..]
#   bash tools/reel.sh snap   x=.. y=.. z=.. yaw=.. pitch=.. [fov=70 name=..]
#   bash tools/reel.sh height "x,z;x,z"                    ground heights (top = water/leaves, ground = solid floor)
#   bash tools/reel.sh terrain x=.. z=.. [radius=512 step=2 name=..]   height grids for Blender (renders/_terrain/NAME)
#   bash tools/reel.sh cmd    "time set 23000"
#   bash tools/reel.sh set    [syncBody=true jitter=shot supersample=2]   live filming settings
#   bash tools/reel.sh pack   TAA_DEFINE=0                   shader pack options (pack reloads)
#   bash tools/reel.sh wait                                  until the director is idle
# REEL_PORT picks the port (default 7341).
set -u
PORT="${REEL_PORT:-7341}"
BASE="http://127.0.0.1:$PORT"
verb="${1:-status}"; shift || true
query() { local q=""; for kv in "$@"; do q="$q&$kv"; done; echo "${q#&}"; }
case "$verb" in
  status|shots|where) curl -s "$BASE/$verb"; echo ;;
  stop|quit) curl -s -X POST "$BASE/$verb"; echo ;;
  check) curl -s "$BASE/check?shot=$1" | python -c "import json,sys; print(json.load(sys.stdin).get('report') or '')" ;;
  play|render|stills) shot="$1"; shift; curl -s -X POST "$BASE/$verb?$(query "shot=$shot" "$@")"; echo ;;
  snap|terrain) curl -s -X POST "$BASE/$verb?$(query "$@")"; echo ;;
  cmd) curl -s -X POST --data "$*" "$BASE/cmd"; echo ;;
  set|pack) curl -s -X POST "$BASE/$verb?$(query "$@")"; echo ;;
  height) curl -s -G --data-urlencode "at=$1" "$BASE/height"; echo ;;
  wait)
    while :; do
      s=$(curl -s "$BASE/status")
      case "$s" in *'"phase":"idle"'*) echo "$s"; break ;; esac
      sleep 3
    done ;;
  *) sed -n '2,17p' "$0"; exit 1 ;;
esac
