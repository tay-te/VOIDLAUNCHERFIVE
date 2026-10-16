#!/usr/bin/env bash
# Builds the VOID luxe shots in this folder from a showcase run.
#
#   cd pvp/mod   # take the game-test lock first
#   ./gradlew runShowcase -Pvoid.showcase.quality=1 -PvoidGlDebug -Pvoid.showcase.only=<scenes> -Pvoid.showcase.out=<run>
#   bash pvp/design/cosmetics-shots/make-shots.sh <run> [ffmpeg]
#
# Stills are copied as the game drew them; frame sequences become MP4s; the per-item stills are
# tiled into one sheet by day and one by night; the ten-block line-ups get a 2x nearest-neighbour
# crop; the style test's Atelier pieces (st-*-lux) are paired with the test's C (st-*-c) in c-fix/.
set -euo pipefail
RUN="$1"
FF="${2:-ffmpeg}"
OUT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for f in hero-night hero-dusk lineup-front lineup-back night-front night-back far-back far-back-night \
         capes-close capes-close-2 capes-close-night capes-close-2-night back-close back-close-night \
         heads-day heads-night badges perf-20; do
  [ -f "$RUN/$f.png" ] && cp "$RUN/$f.png" "$OUT/$f.png"
done

clip() { # $1 scene, $2 fps
  [ -d "$RUN/$1" ] || return 0
  "$FF" -loglevel error -y -framerate "$2" -i "$RUN/$1/%03d.png" -c:v libx264 -pix_fmt yuv420p -preset slow -crf 22 "$OUT/$1.mp4"
}
clip hero-night 16; clip hero-dusk 16; clip night-loop 16; clip run-capes 30; clip run-dusk 30
clip wings-glide 24; clip wings-night 24; clip emotes 20; clip emote-cam 20
clip fight 24; clip fight-night 24; clip sprint-jump 24; clip brawl-20 24
[ -d "$RUN/st-run-lux" ] && "$FF" -loglevel error -y -framerate 24 -i "$RUN/st-run-lux/%03d.png" -c:v libx264 -pix_fmt yuv420p -preset slow -crf 22 "$OUT/run-atelier.mp4"
[ -d "$RUN/st-fight-lux" ] && "$FF" -loglevel error -y -framerate 24 -i "$RUN/st-fight-lux/%03d.png" -c:v libx264 -pix_fmt yuv420p -preset slow -crf 22 "$OUT/fight-atelier.mp4"

# Every worn item, alone, by day and by night: a 4 x 4 sheet of 320 px tiles.
IDS="origin-cape bespoke-cape meridian-cape atelier-cape nocturne-cape regent-cape blade-wings halcyon-ring vesper-sheath atelier-fedora sovereign-crown cloister-hood aureole-halo lumen-halo porcelain-mask aegis-visor"
sheet() { # $1 prefix, $2 out
  local args=() f="" i=0 l="" lay=""
  for id in $IDS; do args+=(-i "$RUN/$1-$id.png"); f="$f[$i]crop=560:560:360:60,scale=320:320[v$i];"; l="$l[v$i]"; i=$((i + 1)); done
  for r in 0 1 2 3; do for c in 0 1 2 3; do lay="$lay$((c * 320))_$((r * 320))|"; done; done
  "$FF" -loglevel error -y "${args[@]}" -filter_complex "${f}${l}xstack=inputs=16:layout=${lay%|}" "$2"
}
sheet item "$OUT/items-day.png"
sheet item-night "$OUT/items-night.png"

# The ten-block line-ups, the middle enlarged 2x: the same pixels the game drew, only bigger.
for f in far-back far-back-night; do
  [ -f "$RUN/$f.png" ] && "$FF" -loglevel error -y -i "$RUN/$f.png" -vf "crop=640:360:320:200,scale=1280:720:flags=neighbor" "$OUT/$f-2x.png"
done

# The C fix: the shipped Atelier pieces beside the style test's C, view by view.
mkdir -p "$OUT/c-fix"
for f in "$RUN"/st-*-lux.png; do
  [ -f "$f" ] || continue
  v=$(basename "$f" .png); v=${v#st-}; v=${v%-lux}
  [ -f "$RUN/st-$v-c.png" ] || continue
  "$FF" -loglevel error -y -i "$RUN/st-$v-lux.png" -i "$RUN/st-$v-c.png" \
    -filter_complex "[0]crop=640:720:320:0[a];[1]crop=640:720:320:0[b];[a][b]hstack=2" "$OUT/c-fix/$v.png"
done
echo "shots: $(ls "$OUT"/*.png "$OUT"/*.mp4 2>/dev/null | wc -l) files, c-fix $(ls "$OUT/c-fix" | wc -l)"
