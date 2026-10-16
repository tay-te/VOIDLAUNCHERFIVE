#!/usr/bin/env bash
# Before/after sheets of single items, from two showcase runs of the polish views (pv-*,
# ShowcasePolish): one run before an art change, one after, every other thing equal.
#
#   cd pvp/mod   # take the game-test lock first, once per run
#   ./gradlew runShowcase -Pvoid.showcase.quality=1 -PvoidGlDebug "-Pvoid.showcase.only=pv-*" -Pvoid.showcase.out=<run>
#   bash pvp/design/cosmetics-shots/make-compare.sh <before-run> <after-run> <out-dir> <id> [id ...]
#
# Each sheet is <out-dir>/<id>.png: two rows (before, after), one column per view — day, night, at
# ten blocks by day and by night (the middle of the frame, 2x nearest-neighbour: the pixels the game
# drew, only bigger), close, side, and from behind for a head item. Labels need a monospace font
# (FONT=<ttf>; found on Windows, Debian and macOS), else the sheet is unlabelled.
set -euo pipefail
B="$1"
A="$2"
OUT="$3"
shift 3
FF="${FF:-ffmpeg}"
FONT="${FONT:-}"
for f in /c/Windows/Fonts/consola.ttf /usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf /System/Library/Fonts/Menlo.ttc; do
  if [ -z "$FONT" ] && [ -f "$f" ]; then FONT="$f"; fi
done
mkdir -p "$OUT"
T=360

label() { # $1 text: a drawtext filter, or a no-op without a font
  if [ -z "$FONT" ]; then echo "null"; return; fi
  local ff
  ff=$(cygpath -m "$FONT" 2>/dev/null || echo "$FONT")
  ff=${ff/:/\\:}
  echo "drawtext=fontfile='$ff':text='$1':x=10:y=10:fontsize=18:fontcolor=white:box=1:boxcolor=black@0.55:boxborderw=6"
}

#   bash make-compare.sh --shots <before-dir> <after-dir> <out-dir> <name> [name ...]
# stacks two whole stills of the same name (e.g. before-v4/lineup-back.png over lineup-back.png).
if [ "$B" = "--shots" ]; then
  B="$A"
  A="$OUT"
  OUT="$1"
  shift
  mkdir -p "$OUT"
  for n in "$@"; do
    "$FF" -loglevel error -y -i "$B/$n.png" -i "$A/$n.png" -filter_complex \
      "[0]$(label "before")[a];[1]$(label "after")[b];[a][b]vstack=2" "$OUT/$n.png"
  done
  echo "compare: $# stacked shot(s) in $OUT"
  exit 0
fi

for id in "$@"; do
  views="day night far farnight close side"
  head=0
  if [ -f "$B/pv-$id-back.png" ]; then views="$views back"; head=1; fi
  args=()
  f=""
  i=0
  for tag in before after; do
    dir="$B"
    [ "$tag" = after ] && dir="$A"
    for v in $views; do
      args+=(-i "$dir/pv-$id-$v.png")
      case "$v" in
        far | farnight)
          if [ "$head" = 1 ]; then crop="crop=180:180:550:290"; else crop="crop=180:180:550:305"; fi
          crop="$crop,scale=$T:$T:flags=neighbor" ;;
        *) crop="crop=600:600:340:60,scale=$T:$T:flags=area" ;;
      esac
      f="$f[$i]$crop,$(label "$tag  $v")[v$i];"
      i=$((i + 1))
    done
  done
  per=$((i / 2))
  l=""
  lay=""
  for ((k = 0; k < i; k++)); do
    l="$l[v$k]"
    lay="$lay$(((k % per) * T))_$(((k / per) * T))|"
  done
  "$FF" -loglevel error -y "${args[@]}" -filter_complex "${f}${l}xstack=inputs=$i:layout=${lay%|}" "$OUT/$id.png"
done
echo "compare: $# sheet(s) in $OUT"
