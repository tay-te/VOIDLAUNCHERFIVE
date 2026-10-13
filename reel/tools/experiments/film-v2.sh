#!/usr/bin/env bash
# Re-films "Morning in the Expanse" with the stability fixes, after one comparison run of the dolly test.
set -u
cd "$(dirname "$0")/../.."
R() { bash tools/reel.sh "$@"; }
wait_idle() { until R status | grep -q '"phase":"idle"'; do sleep 5; done; }
until R where 2>/dev/null | grep -q eye; do sleep 5; done
R pack TAA_DEFINE=0 >/dev/null; sleep 5
R set syncBody=true lodClock=true lodFreeze=true jitter=true supersample=2 >/dev/null
rm -rf renders/_t-dolly; R render _t-dolly encode=false samples=6 >/dev/null; wait_idle
rm -rf renders/_x-H-dolly-lodapi; mv renders/_t-dolly renders/_x-H-dolly-lodapi
echo "== H dolly, LOD recording API + hysteresis + detail scale, 6 jittered, ss2, TAA off"; python tools/stability.py _x-H-dolly-lodapi | sed 's/^/   /'
R set jitter=shot supersample=shot >/dev/null
for s in m4-summit m1-range-dawn m2-village m3-gorge; do
  rm -rf "renders/$s/frames" "renders/$s/$s.mp4"
  R render "$s" >/dev/null; wait_idle
  until [ -f "renders/$s/$s.mp4" ]; do sleep 5; done; sleep 5
  echo "== $s: $(grep -E -o '"(seconds|secondsPerFrame)": [0-9.]*|"framesFilmedLoading": \[[^]]*\]|"framesFarStillBuilding": [0-9]*' renders/$s/render.json | tr '\n' ' ')"
  python tools/stability.py "$s" | sed 's/^/   /'
done
R pack TAA_DEFINE=-1 >/dev/null
python tools/cut.py morning-in-the-expanse-v2 m1-range-dawn m2-village m3-gorge m4-summit --title "Morning in the Expanse" --sub "VOID Expanse  ·  VOID LOD  ·  filmed with VOID Reel"
echo film done
