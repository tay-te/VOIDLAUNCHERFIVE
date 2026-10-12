"""
Measures temporal instability in a rendered shot: for each frame, how far it is from the mean of its two neighbours
(a smooth camera move scores low; a tile popping in, swapping or ghosting for one frame spikes). Reported per band
of the image (sky, far, middle, near) so far-terrain trouble shows apart from the foreground, and plotted to
renders/<shot>/flicker.png.

    python tools/flicker.py SHOT [--from F --to F] [--threshold 2.0]
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

REEL = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def load(path, scale):
    img = Image.open(path).convert("L")
    if scale != 1:
        img = img.resize((img.width // scale, img.height // scale), Image.BILINEAR)
    return np.asarray(img, dtype=np.float32)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("shot")
    ap.add_argument("--from", dest="start", type=int, default=0)
    ap.add_argument("--to", dest="end", type=int, default=10**9)
    ap.add_argument("--threshold", type=float, default=2.0)
    ap.add_argument("--bands", default="0.0,0.30,0.45,0.65,1.0", help="row fractions splitting sky/far/middle/near")
    a = ap.parse_args()
    folder = os.path.join(REEL, "renders", a.shot, "frames")
    if not os.path.isdir(folder):
        sys.exit("no frames in " + folder)
    files = sorted(f for f in os.listdir(folder) if f.endswith(".png"))
    files = files[max(0, a.start):min(len(files), a.end + 1)]
    frames = [load(os.path.join(folder, f), 2) for f in files]
    h = frames[0].shape[0]
    cuts = [int(float(c) * h) for c in a.bands.split(",")]
    names = ["sky", "far", "middle", "near"][:len(cuts) - 1]
    scores = {n: [] for n in names}
    for i in range(1, len(frames) - 1):
        expected = (frames[i - 1] + frames[i + 1]) / 2
        diff = np.abs(frames[i] - expected)
        for n, (top, bottom) in zip(names, zip(cuts, cuts[1:])):
            scores[n].append(float(diff[top:bottom].mean()))
    for n in names:
        s = np.array(scores[n])
        spikes = [files[i + 1][:-4] for i in np.nonzero(s > a.threshold)[0]]
        print(f"{n:6s} mean {s.mean():5.2f}  p95 {np.percentile(s, 95):5.2f}  max {s.max():6.2f}  spikes>{a.threshold}: {len(spikes)} {spikes[:12]}")

    w, ph = 1200, 300
    plot = Image.new("RGB", (w, ph + 30), (250, 248, 244))
    d = ImageDraw.Draw(plot)
    top = max(max(v) for v in scores.values()) * 1.1 or 1
    colours = {"sky": (150, 170, 200), "far": (200, 70, 50), "middle": (60, 120, 160), "near": (90, 150, 90)}
    for n in names:
        pts = [(int(i / max(1, len(scores[n]) - 1) * (w - 20)) + 10, ph - int(v / top * (ph - 20))) for i, v in enumerate(scores[n])]
        d.line(pts, fill=colours[n], width=2)
    d.text((10, ph + 8), f"{a.shot}: deviation from neighbours per band (sky blue, far red, middle teal, near green); peak {top / 1.1:.1f}", fill=(40, 40, 40))
    out = os.path.join(REEL, "renders", a.shot, "flicker.png")
    plot.save(out)
    print("plot:", out)


if __name__ == "__main__":
    main()
