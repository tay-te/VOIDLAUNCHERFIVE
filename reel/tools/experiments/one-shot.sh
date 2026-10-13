#!/usr/bin/env bash
# Films one shot with the film settings and measures it: one-shot.sh SHOT
set -u
cd "$(dirname "$0")/../.."
R() { bash tools/reel.sh "$@"; }
until R where 2>/dev/null | grep -q eye; do sleep 5; done
R pack TAA_DEFINE=0 >/dev/null; sleep 5
R set syncBody=true lodClock=true lodFreeze=true lodCompensate=false jitter=shot supersample=shot >/dev/null
rm -rf "renders/$1/frames" "renders/$1/$1.mp4"
R render "$1" >/dev/null
until R status | grep -q '"phase":"idle"'; do sleep 5; done
until [ -f "renders/$1/$1.mp4" ]; do sleep 5; done; sleep 5
grep -E -o '"(seconds|secondsPerFrame)": [0-9.]*|"framesFilmedLoading": \[[^]]*\]|"framesFarStillBuilding": [0-9]*' "renders/$1/render.json" | tr '\n' ' '; echo
python tools/stability.py "$1"
R pack TAA_DEFINE=-1 >/dev/null
