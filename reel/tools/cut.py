"""
Cuts rendered shots into one film with ffmpeg: dissolves between shots, a fade in from black and out to black, and
an optional title over the opening.

    python tools/cut.py NAME SHOT [SHOT ...] [--title "TEXT"] [--sub "TEXT"] [--dissolve 1.0] [--fade 1.5]

Shots are read from renders/<shot>/<shot>.mp4; the film goes to renders/_films/NAME.mp4. The first step toward
edit lists (docs/DESIGN.md, phase 2).
"""
import argparse
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REEL = os.path.dirname(HERE)
RENDERS = os.path.join(REEL, "renders")


def find_ffmpeg(name):
    d = REEL
    exe = name + (".exe" if os.name == "nt" else "")
    for _ in range(6):
        candidate = os.path.join(d, ".tools", "ffmpeg", "bin", exe)
        if os.path.isfile(candidate):
            return candidate
        d = os.path.dirname(d)
    return exe


FFMPEG = find_ffmpeg("ffmpeg")
FFPROBE = find_ffmpeg("ffprobe")


def duration(path):
    out = subprocess.run([FFPROBE, "-v", "error", "-show_entries", "format=duration", "-of", "json", path],
                         capture_output=True, text=True, check=True).stdout
    return float(json.loads(out)["format"]["duration"])


def font():
    for f in ("segoeuil.ttf", "segoeui.ttf", "arial.ttf"):
        p = os.path.join(os.environ.get("WINDIR", "C:/Windows"), "Fonts", f)
        if os.path.isfile(p):
            return p.replace("\\", "/").replace(":", "\\:")
    return None


def escape(text):
    return text.replace("\\", "\\\\").replace("'", "\u2019").replace(":", "\\:").replace("%", "\\%")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("shots", nargs="+")
    ap.add_argument("--title")
    ap.add_argument("--sub")
    ap.add_argument("--dissolve", type=float, default=1.0)
    ap.add_argument("--fade", type=float, default=1.5)
    a = ap.parse_args()

    files = [os.path.join(RENDERS, s, s + ".mp4") for s in a.shots]
    for f in files:
        if not os.path.isfile(f):
            sys.exit("missing " + f)
    lengths = [duration(f) for f in files]

    chains = []
    last = "[0:v]"
    total = lengths[0]
    for i in range(1, len(files)):
        out = f"[x{i}]"
        chains.append(f"{last}[{i}:v]xfade=transition=fade:duration={a.dissolve}:offset={total - a.dissolve:.3f}{out}")
        total += lengths[i] - a.dissolve
        last = out
    post = [f"fade=t=in:st=0:d={a.fade}", f"fade=t=out:st={total - a.fade - 0.5:.3f}:d={a.fade + 0.5}"]
    ff = font()
    if a.title and ff:
        # the title rises out of the dawn: in from 1.5 s, held, gone by 6.5 s
        alpha = "if(lt(t,1.5),0,if(lt(t,2.7),(t-1.5)/1.2,if(lt(t,5.3),1,if(lt(t,6.5),(6.5-t)/1.2,0))))"
        post.append(f"drawtext=fontfile='{ff}':text='{escape(a.title)}':fontsize=64:fontcolor=white:alpha='{alpha}'"
                    f":x=(w-text_w)/2:y=h*0.62:shadowcolor=black@0.35:shadowx=0:shadowy=2")
        if a.sub:
            post.append(f"drawtext=fontfile='{ff}':text='{escape(a.sub)}':fontsize=28:fontcolor=white@0.85:alpha='{alpha}'"
                        f":x=(w-text_w)/2:y=h*0.62+86:shadowcolor=black@0.35:shadowx=0:shadowy=2")
    chains.append(f"{last}{','.join(post)}[out]")

    os.makedirs(os.path.join(RENDERS, "_films"), exist_ok=True)
    target = os.path.join(RENDERS, "_films", a.name + ".mp4")
    cmd = [FFMPEG, "-y", "-v", "error"]
    for f in files:
        cmd += ["-i", f]
    cmd += ["-filter_complex", ";".join(chains), "-map", "[out]", "-c:v", "libx264", "-preset", "slow", "-crf", "15",
            "-pix_fmt", "yuv420p", "-movflags", "+faststart", target]
    subprocess.run(cmd, check=True)
    print(f"{target}: {total:.1f}s from {len(files)} shots")


if __name__ == "__main__":
    main()
