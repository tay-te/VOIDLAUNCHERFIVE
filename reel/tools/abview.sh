#!/usr/bin/env bash
# Films one view in every shader pack: bash tools/abview.sh NAME TIME "x=.. y=.. z=.. yaw=.. pitch=.. fov=.."
set -u
R="$(cd "$(dirname "$0")/.." && pwd)"
name="$1"; time="$2"; view="$3"
bash "$R/tools/reel.sh" cmd "time set $time" >/dev/null
for pack in "ComplementaryReimagined_r5.9.3.zip" "ComplementaryUnbound_r5.9.3.zip" "Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip" "photon_v1.4.zip" "Solas Shader V3.8.zip" "BSL_v10.1.8.zip"; do
  short=$(echo "$pack" | sed -E 's/^(ComplementaryReimagined|ComplementaryUnbound|Bliss|photon|Solas|BSL).*/\1/' | tr 'A-Z' 'a-z')
  n0=$(wc -l < "$R/build/studio.log")
  bash "$R/tools/abpack.sh" "$pack" >/dev/null
  bash "$R/tools/reel.sh" snap $view w=1600 h=900 name="ab-$name-$short" >/dev/null
  bash "$R/tools/reel.sh" wait >/dev/null
  errs=$(tail -n +$n0 "$R/build/studio.log" | grep -ciE "Failed to (load|compile|create)|shader compilation|ERROR\]")
  echo "$short: done ($errs error lines)"
done
