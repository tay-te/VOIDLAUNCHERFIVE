#!/usr/bin/env bash
# Dolly variants on the LOD recording API: detail compensation off, then off with the pack's TAA on.
set -u
cd "$(dirname "$0")/../.."
R() { bash tools/reel.sh "$@"; }
film() {
  local label=$1; shift
  rm -rf renders/_t-dolly; R render _t-dolly encode=false samples=6 >/dev/null
  until R status | grep -q '"phase":"idle"'; do sleep 3; done
  rm -rf "renders/_x-$label"; mv renders/_t-dolly "renders/_x-$label"
  echo "== $label [$(R set | tr -d '{}"')]"; python tools/stability.py "_x-$label" | sed 's/^/   /'
}
until R where 2>/dev/null | grep -q eye; do sleep 5; done
R set syncBody=true lodClock=true lodFreeze=true lodCompensate=false jitter=true supersample=2 >/dev/null
R pack TAA_DEFINE=0 >/dev/null; sleep 5
film I-dolly-api-nocomp-noTAA
R pack TAA_DEFINE=-1 >/dev/null; sleep 5
film J-dolly-api-nocomp-TAA
echo done
