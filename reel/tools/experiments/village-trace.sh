#!/usr/bin/env bash
set -u
cd "$(dirname "$0")/../.."
R() { bash tools/reel.sh "$@"; }
until R where 2>/dev/null | grep -q eye; do sleep 5; done
R pack TAA_DEFINE=0 >/dev/null; sleep 5
R set syncBody=true lodClock=true lodFreeze=true lodCompensate=false lodTrace=true jitter=shot supersample=shot >/dev/null
rm -rf renders/m2-village/frames; R render m2-village encode=false >/dev/null
until R status | grep -q '"phase":"idle"'; do sleep 5; done
rm -rf renders/_x-P-trace-verify; mv renders/m2-village renders/_x-P-trace-verify
python tools/flashes.py _x-P-trace-verify
R set lodTrace=false >/dev/null; R pack TAA_DEFINE=-1 >/dev/null
echo done
