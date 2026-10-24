#!/usr/bin/env bash
# Switches the studio's shader pack (by zip name) and reloads Iris: bash tools/abpack.sh "photon_v1.4.zip"
set -u
R="$(cd "$(dirname "$0")/.." && pwd)"
CFG="$R/build/run/clientGameTest/config/iris.properties"
python -I - "$CFG" "$1" <<'PY'
import sys
cfg, pack = sys.argv[1], sys.argv[2]
lines = open(cfg, encoding="latin-1").read().splitlines()
lines = [l for l in lines if not l.startswith("shaderPack=")] + ["shaderPack=" + pack]
open(cfg, "w", encoding="latin-1").write("\n".join(lines) + "\n")
PY
bash "$R/tools/reel.sh" pack REEL_AB=1
