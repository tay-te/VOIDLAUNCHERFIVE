#!/usr/bin/env bash
# Collects the AI showcase's pictures (bestiary.ai parts media, elites, tokens, dedicated) into GIFs and stills outside
# the repository: C:/Users/bitto/VOID/.tools/share/bestiary/stage1/ai/
#   bash tools/ai_media.sh
set -u
P=$(cd "$(dirname "$0")/.." && pwd)
S="$P/build/run/clientGameTest/screenshots"
OUT=/c/Users/bitto/VOID/.tools/share/bestiary/stage1/ai
mkdir -p "$OUT"
for set in $(ls "$S" | grep -E '^gif_.*_[0-9]{2}\.png$' | sed -E 's/_[0-9]{2}\.png$//' | sort -u); do
  python "$P/tools/gif.py" "$OUT/${set#gif_}.gif" 720 10 "$S/${set}"_*.png
done
cp "$S"/ai_*.png "$OUT"/ 2>/dev/null
ls -la "$OUT"
