#!/usr/bin/env bash
# Which Reel LOD hook the village shot's periodic one-frame flashes depend on.
set -u
cd "$(dirname "$0")/../.."
R() { bash tools/reel.sh "$@"; }
film() {
  local label=$1; shift
  R set "$@" >/dev/null
  rm -rf renders/m2-village/frames; R render m2-village encode=false >/dev/null
  until R status | grep -q '"phase":"idle"'; do sleep 5; done
  rm -rf "renders/_x-$label"; mv renders/m2-village "renders/_x-$label"
  echo "== $label [$(R set | tr -d '{}"')]"; python tools/flashes.py "_x-$label" | sed 's/^/   /'
}
until R where 2>/dev/null | grep -q eye; do sleep 5; done
R pack TAA_DEFINE=0 >/dev/null; sleep 5
film K-wallclock syncBody=true lodClock=false lodFreeze=true lodCompensate=false jitter=shot supersample=shot
film L-nohold lodClock=true lodFreeze=false
R pack TAA_DEFINE=-1 >/dev/null
echo done
