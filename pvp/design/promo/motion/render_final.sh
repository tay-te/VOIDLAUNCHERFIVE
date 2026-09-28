#!/bin/zsh
# Full-resolution render of every shot (1920x1080, 48 spp, 60 fps), then the final cut.
cd "$(dirname "$0")"
B=/Applications/Blender.app/Contents/MacOS/Blender
typeset -A SCRIPT
SCRIPT=(s1 shot_s1_assemble.py s1b shot_s1b_title.py s2 shot_s2_launch.py s4 shot_s4_mods.py s5 shot_s5_setup.py s6 shot_s6_outro.py)
for s in s1 s1b s2 s4 s5 s6; do
  mkdir -p out/final/$s
  start=$(date +%s)
  $B -b --factory-startup --python ${SCRIPT[$s]} -- out/final/$s/frames 100 48 anim out/final/$s/$s.blend > out/final/$s/render.log 2>&1
  echo "$s: $(ls out/final/$s/frames | wc -l | tr -d ' ') frames in $(( $(date +%s) - start ))s"
done
./cut_film.sh final
