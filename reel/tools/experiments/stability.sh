#!/usr/bin/env bash
# The far-terrain stability experiments: each adds one fix to the last. Run with a studio open on the control port.
set -u
cd "$(dirname "$0")/../.."
R() { bash tools/reel.sh "$@"; }
film() {  # film <shot> <label> [render args]
  local shot=$1 label=$2; shift 2
  rm -rf "renders/$shot"
  R render "$shot" encode=false "$@" >/dev/null
  until R status | grep -q '"phase":"idle"'; do sleep 3; done
  rm -rf "renders/_x-$label"; mv "renders/$shot" "renders/_x-$label"
  echo "== $label [$(R set | tr -d '{}"')] $*: $(grep -o '"secondsPerFrame": [0-9.]*' renders/_x-$label/render.json)"
  python tools/stability.py "_x-$label" | sed 's/^/   /'
}
until R where 2>/dev/null | grep -q eye; do sleep 5; done
R pack TAA_DEFINE=-1 >/dev/null; sleep 5
R set syncBody=false lodClock=false lodFreeze=false jitter=false supersample=shot >/dev/null
film _t-static-run A-static-original
film _t-dolly C-dolly-original
R set syncBody=true >/dev/null
film _t-dolly D-dolly-syncBody
R set lodClock=true >/dev/null
film _t-dolly G-dolly-lodClock
R pack TAA_DEFINE=0 >/dev/null; sleep 5
R set lodFreeze=true jitter=true >/dev/null
film _t-dolly E-dolly-6jitter-noTAA samples=6
R set supersample=2 >/dev/null
film _t-dolly F-dolly-6jitter-ss2-noTAA samples=6
film m4-summit M4-all-fixes
R set jitter=shot supersample=shot >/dev/null; R pack TAA_DEFINE=-1 >/dev/null
echo experiments done
