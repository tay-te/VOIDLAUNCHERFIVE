#!/bin/zsh
# Cut the VOID promo from rendered shot frames: hard cuts, 60 fps, H.264.
#   ./cut_film.sh [preview|final]   (preview = out/sN/frames, final = out/final/sN/frames)
set -e
cd "$(dirname "$0")"
MODE=${1:-preview}
ROOT=out
[ "$MODE" = "final" ] && ROOT=out/final
SHOTS=(s1 s1b s2 s4 s5 s6)
TMP=$ROOT/_cut
rm -rf $TMP && mkdir -p $TMP
LIST=$TMP/list.txt
: > $LIST
for s in $SHOTS; do
  [ -d $ROOT/$s/frames ] || { echo "missing $ROOT/$s/frames"; exit 1; }
  ffmpeg -loglevel error -y -framerate 60 -i $ROOT/$s/frames/f_%04d.png \
    -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p" -c:v libx264 -preset slow -crf 14 -r 60 $TMP/$s.mp4
  echo "file '$s.mp4'" >> $LIST
done
OUTF=$ROOT/VOID_promo_${MODE}.mp4
ffmpeg -loglevel error -y -f concat -safe 0 -i $LIST -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p -r 60 -movflags +faststart $OUTF
ffprobe -v error -show_entries format=duration:stream=width,height,r_frame_rate -of csv=p=0 $OUTF
echo "wrote $OUTF"
