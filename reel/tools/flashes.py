"""
Finds one-frame flashes in a rendered shot: frames that differ from both neighbours while the neighbours agree with
each other (a tile drawn wrong for a single frame, then right again). Prints each with its strength and the gaps
between them.

    python tools/flashes.py SHOT [--band 0.08,0.45] [--ratio 2.5]
"""
import argparse
import os

import cv2
import numpy as np

REEL = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("shot")
    ap.add_argument("--band", default="0.08,0.45")
    ap.add_argument("--ratio", type=float, default=2.5, help="how much more a flash frame differs than its neighbours do")
    a = ap.parse_args()
    folder = os.path.join(REEL, "renders", a.shot, "frames")
    files = sorted(f for f in os.listdir(folder) if f.endswith(".png"))
    top_f, bottom_f = (float(v) for v in a.band.split(","))
    frames = []
    for f in files:
        img = cv2.imread(os.path.join(folder, f), cv2.IMREAD_GRAYSCALE)
        h = img.shape[0]
        frames.append(cv2.resize(img[int(top_f * h):int(bottom_f * h)], None, fx=0.5, fy=0.5, interpolation=cv2.INTER_AREA).astype(np.float32))
    found = []
    for i in range(1, len(frames) - 1):
        before = float(np.abs(frames[i] - frames[i - 1]).mean())
        after = float(np.abs(frames[i] - frames[i + 1]).mean())
        across = float(np.abs(frames[i + 1] - frames[i - 1]).mean())
        if min(before, after) > a.ratio * max(across / 2, 0.05):
            found.append((i, round(min(before, after) / max(across / 2, 0.05), 1)))
    print(f"{a.shot}: {len(found)} one-frame flashes")
    print("  frames:", [f for f, _ in found][:40])
    if len(found) > 1:
        gaps = np.diff([f for f, _ in found])
        print("  gaps:", gaps.tolist()[:40])


if __name__ == "__main__":
    main()
