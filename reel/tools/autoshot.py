"""
Places a moving shot from the land itself (the start of the director's eye, docs/DESIGN.md phase 2): reads a
`reel.sh terrain` grid around a spot, tries a set of headings, and writes a paced shot that keeps a set clearance over
the ground under and ahead of the camera, looks a little down toward the land some way off, and faces whichever
heading has the most interesting land ahead (relief far off, peaks above the eye), never one that runs into a hill.

    python tools/autoshot.py NAME --terrain renders/_terrain/GRID --at X,Z --time 11500
        [--headings 0,180] [--clear 80] [--length 240] [--speed 30] [--fov 60] [--rise 0]
        [--weather clear] [--rate 1] [--size 1280x720] [--samples 6]

Headings are Minecraft yaws (0 faces south, 90 west, 180 north, -90 east). For side light at dawn or golden hour,
when the sun is low in the east or west, give 0,180. --rise lifts the camera that many blocks over the move (a crane).
"""
import argparse
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REEL = os.path.dirname(HERE)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("--terrain", required=True)
    ap.add_argument("--at", required=True)
    ap.add_argument("--time", type=float, required=True)
    ap.add_argument("--headings", default="0,45,90,135,180,-135,-90,-45")
    ap.add_argument("--clear", type=float, default=80)
    ap.add_argument("--length", type=float, default=240)
    ap.add_argument("--speed", type=float, default=30)
    ap.add_argument("--fov", type=float, default=60)
    ap.add_argument("--rise", type=float, default=0)
    ap.add_argument("--weather", default="clear")
    ap.add_argument("--rate", type=float, default=1)
    ap.add_argument("--size", default="1280x720")
    ap.add_argument("--samples", type=int, default=6)
    ap.add_argument("--notes", default="")
    a = ap.parse_args()

    meta = json.load(open(os.path.join(a.terrain, "meta.json")))
    n, step, x0, z0 = meta["nx"], meta["step"], meta["x0"], meta["z0"]
    ground = np.fromfile(os.path.join(a.terrain, "surface.f32"), "<f4").reshape(n, n)
    cx, cz = (float(v) for v in a.at.split(","))

    def h(x, z):
        i = int(round((x - x0) / step))
        j = int(round((z - z0) / step))
        if 0 <= i < n and 0 <= j < n:
            return float(ground[j, i])
        return float("nan")

    def along(yaw, d0, d1, every=8, wide=40):
        """Heights in a band ahead of a point along a heading."""
        fx, fz = -math.sin(math.radians(yaw)), math.cos(math.radians(yaw))
        rx, rz = fz, -fx
        out = []
        for d in np.arange(d0, d1, every):
            for w in (-wide, 0, wide):
                out.append(h(cx + fx * d + rx * w, cz + fz * d + rz * w))
        return np.array([v for v in out if not math.isnan(v)])

    best = None
    for yaw in (float(v) for v in a.headings.split(",")):
        path = along(yaw, -a.length / 2, a.length / 2 + 120)       # under the move and just past it
        ahead = along(yaw, a.length / 2 + 120, a.length / 2 + 500)  # what the camera flies toward next
        far = along(yaw, 600, 3000, every=48, wide=300)
        if len(path) == 0 or len(far) == 0:
            continue
        cam = path.max() + a.clear
        blocked = len(ahead) and ahead.max() > cam + a.rise - 10
        peaks = max(0.0, far.max() - cam)
        score = far.std() + 0.6 * peaks - (1e6 if blocked else 0)
        if best is None or score > best[0]:
            best = (score, yaw, cam, far)
    if best is None:
        sys.exit("no heading has land under it in the grid")
    score, yaw, cam, far = best
    fx, fz = -math.sin(math.radians(yaw)), math.cos(math.radians(yaw))
    target = float(np.median(far))
    pitch = max(2.0, min(12.0, math.degrees(math.atan2(cam - target, 900))))
    half = a.length / 2
    keys = []
    for k, f in enumerate((-1.0, 0.0, 1.0)):
        y = cam + a.rise * (f + 1) / 2
        key = {"pos": [round(cx + fx * half * f, 1), round(y, 1), round(cz + fz * half * f, 1)], "yaw": round(yaw, 1),
               "pitch": round(pitch - (a.rise / max(1, a.length)) * 8 * (f + 1) / 2, 1)}
        if k == 0:
            key["speed"] = a.speed
        keys.append(key)
    w, hgt = (int(v) for v in a.size.split("x"))
    shot = {
        "notes": a.notes or "autoshot: heading %.0f, clearance %.0f over the path, scored %.0f" % (yaw, a.clear, score),
        "fps": 30,
        "size": [w, hgt],
        "world": {"time": a.time, "timeRate": a.rate, "weather": a.weather},
        "camera": {"fov": a.fov, "motion": {"in": 0, "out": 0}, "keys": keys},
        "render": {"samples": a.samples, "shutter": 180, "preroll": 1},
    }
    path = os.path.join(REEL, "shots", a.name + ".json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(shot, f, indent="\t")
        f.write("\n")
    print("%s: heading %.0f, camera y %.0f (path max %.0f), pitch %.1f, far median %.0f max %.0f -> %s" % (
        a.name, yaw, cam, cam - a.clear, pitch, target, far.max(), path))


if __name__ == "__main__":
    main()
