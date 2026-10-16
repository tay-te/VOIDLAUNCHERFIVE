#!/usr/bin/env bash
# Builds the style-test comparison sheets and clips from a showcase run.
#
#   ./gradlew runShowcase -Pvoid.showcase.quality=1 -Pvoid.showcase.only='st-*' -Pvoid.showcase.out=<run>
#   bash pvp/design/cosmetics-style-test/make-sheets.sh <run> [ffmpeg]
#
# One sheet per view: three labelled columns (C final / C · Luxe test / D · Toy), each the middle 640 px of
# that set's 1280 x 720 frame, staged identically (ShowcaseStyle). Clips: each set's running and
# fight sequences as MP4, plus side-by-side comparisons in the same three columns.
set -euo pipefail
RUN="$1"
FF="${2:-ffmpeg}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHEETS="${SHEETS:-$HERE/sheets}"; CLIPS="${CLIPS:-$HERE/clips}"
mkdir -p "$SHEETS" "$CLIPS"
FONT="C\\:/Windows/Fonts/segoeui.ttf"
[ -f /c/Windows/Fonts/segoeui.ttf ] || FONT="DejaVuSans"

# The label strip: ink, with the three column names centred in ivory and the view on the left.
label() { # $1 view name
  local t="fontfile='$FONT':fontcolor=0xe8e2d6:fontsize=26"
  echo "pad=iw:ih+64:0:64:color=0x0f1015,drawtext=$t:text='C final (Atelier)':x=320-tw/2:y=20,drawtext=$t:text='C · Luxe':x=960-tw/2:y=20,drawtext=$t:text='D · Toy':x=1600-tw/2:y=20,drawtext=fontfile='$FONT':fontcolor=0x7d838d:fontsize=16:text='$1':x=14:y=24,drawbox=x=639:y=64:w=2:h=ih-64:color=0x0f1015:t=fill,drawbox=x=1279:y=64:w=2:h=ih-64:color=0x0f1015:t=fill"
}

sheet() { # $1 view (scene name without the set suffix)
  local v="$1"
  for s in lux c d; do [ -f "$RUN/st-$v-$s.png" ] || { echo "skip $v (no $s)"; return; }; done
  "$FF" -loglevel error -y -i "$RUN/st-$v-lux.png" -i "$RUN/st-$v-c.png" -i "$RUN/st-$v-d.png" \
    -filter_complex "[0]crop=640:720:320:0[a];[1]crop=640:720:320:0[b];[2]crop=640:720:320:0[c];[a][b][c]hstack=3,$(label "$v")" \
    "$SHEETS/$v.png"
  echo "sheet $v"
}

for v in front-day back-day front-night back-night far-front-day far-back-day far-front-night far-back-night \
         close-cape close-back close-head close-back-night; do
  sheet "$v"
done

# The ten-block views again with the middle of each column enlarged 2x (nearest neighbour: the same
# pixels the game drew, only bigger).
for v in far-front-day far-back-day far-front-night far-back-night; do
  for s in lux c d; do [ -f "$RUN/st-$v-$s.png" ] || continue 2; done
  z="crop=320:360:480:210,scale=640:720:flags=neighbor"
  "$FF" -loglevel error -y -i "$RUN/st-$v-lux.png" -i "$RUN/st-$v-c.png" -i "$RUN/st-$v-d.png" \
    -filter_complex "[0]$z[a];[1]$z[b];[2]$z[c];[a][b][c]hstack=3,$(label "$v (2x)")" \
    "$SHEETS/$v-2x.png"
  echo "sheet $v-2x"
done

# The three sets in one frame (no columns to cut: they already stand abreast).
for v in front-day back-day front-night back-night; do
  [ -f "$RUN/st-all-$v.png" ] && cp "$RUN/st-all-$v.png" "$SHEETS/all-$v.png"
done

# Clips: per set, and the three side by side.
clip() { # $1 kind (run, fight)
  local k="$1"
  for s in lux c d; do
    [ -d "$RUN/st-$k-$s" ] || { echo "skip clip $k"; return; }
    "$FF" -loglevel error -y -framerate 24 -i "$RUN/st-$k-$s/%03d.png" -c:v libx264 -pix_fmt yuv420p -preset slow -crf 24 "$CLIPS/$k-$s.mp4"
  done
  "$FF" -loglevel error -y -i "$CLIPS/$k-lux.mp4" -i "$CLIPS/$k-c.mp4" -i "$CLIPS/$k-d.mp4" \
    -filter_complex "[0]crop=640:720:320:0[a];[1]crop=640:720:320:0[b];[2]crop=640:720:320:0[c];[a][b][c]hstack=3,$(label "$k")" \
    -c:v libx264 -pix_fmt yuv420p -preset slow -crf 24 "$CLIPS/$k-compare.mp4"
  echo "clip $k"
}
clip run
clip fight

