"""
Finds camera spots in an export by looking through its real geometry: along a Hearth town's roads at eye height (a long
clear view ahead, open sky above: a street shot), or under its trees (a canopy close above, a clear view out: a woodland
shot). Prints the best as --cam/--at pairs for scene_looks.py, in Minecraft coordinates.

    blender -b --factory-startup -P tools/blender/find_views.py -- EXPORT_DIR street|forest|orbit [--near x,z --within 200]
        orbit: [--centre x,y,z --seed 5 --count 6]  random spots around a point that see it
"""
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
import reel_world  # noqa: E402
import voxkit as vk  # noqa: E402

argv = sys.argv[sys.argv.index("--") + 1:]
EXPORT, KIND = argv[0], argv[1]
opt = {argv[i].lstrip("-"): argv[i + 1] for i in range(2, len(argv) - 1, 2)}
vk.clear()
world = reel_world.load(EXPORT, style="real")
ox, oy, oz = bpy.context.scene["reel_origin"]
bpy.context.view_layer.update()
depsgraph = bpy.context.evaluated_depsgraph_get()
scene = bpy.context.scene
to_b = lambda x, y, z: Vector((x - ox, -(z - oz), y - oy))


def clear(x, y, z, dx, dy, dz, reach):
    """How far one can see from a Minecraft point along a Minecraft direction, up to `reach` blocks."""
    d = Vector((dx, -dz, dy)).normalized()
    hit, where, *_ = scene.ray_cast(depsgraph, to_b(x, y, z), d, distance=reach)
    return (where - to_b(x, y, z)).length if hit else reach


NAMES = [m.name.split(".")[0] if m else "" for m in world.data.materials]


def ground(x, z, top=200):
    """The first surface below a Minecraft x, z that is not a tree (looking down through leaves and logs), or None."""
    start = to_b(x, top, z)
    for _ in range(40):
        hit, where, normal, index, ob, _m = scene.ray_cast(depsgraph, start, Vector((0, 0, -1)), distance=400)
        if not hit:
            return None
        name = NAMES[world.data.polygons[index].material_index]
        if "leaves" not in name and "_log" not in name:
            return where.z + oy
        start = where - Vector((0, 0, 0.01))
    return None


near = tuple(float(v) for v in opt["near"].split(",")) if "near" in opt else None
within = float(opt.get("within", 1e9))
found = []
if KIND == "street":
    sys.path.insert(0, os.path.dirname(HERE))
    import nbt
    town = nbt.load(os.path.join(EXPORT, "hearth.dat")) if os.path.isfile(os.path.join(EXPORT, "hearth.dat")) else None
    if town is None:
        for c in ("bradhurst-floating",):
            p = os.path.join(os.path.dirname(EXPORT), c, "hearth.dat")
            if os.path.isfile(p):
                town = nbt.load(p)
    for plot in town["plots"]:
        spec = plot["spec"]
        pts = spec["params"].get("points") if spec["archetype"].endswith("/road") else None
        if not pts or plot.get("state") != "built":
            continue
        pts = np.array(pts, float).reshape(-1, 3)
        for a, b in zip(pts[:-1], pts[1:]):
            for t in np.linspace(0, 1, 4):
                x, _, z = a + (b - a) * t
                g = ground(x + 0.5, z + 0.5)
                if g is None:
                    continue
                for yaw in range(0, 360, 20):
                    dx, dz = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
                    eye = g + 1.7
                    ahead = clear(x + 0.5, eye, z + 0.5, dx, 0.03, dz, 90)
                    sky = clear(x + 0.5, eye, z + 0.5, 0, 1, 0, 40)
                    sides = min(clear(x + 0.5, eye, z + 0.5, -dz, 0, dx, 12), clear(x + 0.5, eye, z + 0.5, dz, 0, -dx, 12))
                    if ahead >= 30 and sky >= 30 and sides < 12:  # a long view, open sky, buildings close at the sides
                        found.append((ahead + 2 * (12 - sides), x + 0.5, eye, z + 0.5, dx, dz))
elif KIND == "forest":
    lo = np.array(world.bound_box[0]) ; hi = np.array(world.bound_box[6])
    rng = np.random.default_rng(3)
    for _ in range(4000):
        x = rng.uniform(-150, 150) + ox
        z = rng.uniform(-150, 150) + oz
        if near and math.hypot(x - near[0], z - near[1]) > within:
            continue
        g = ground(x, z, top=oy + 120)
        if g is None:
            continue
        eye = g + 1.7
        canopy = clear(x, eye, z, 0, 1, 0, 30)
        if not (4 <= canopy <= 16):  # under trees, not in a house or the open
            continue
        for yaw in range(0, 360, 30):
            dx, dz = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
            ahead = clear(x, eye, z, dx, 0.08, dz, 60)
            if ahead >= 25:
                found.append((ahead - canopy, x, eye, z, dx, dz))
elif KIND == "orbit":
    # anywhere around a point, at any height, that sees it: random spots kept where the camera stands in the air
    # and the line to the point is clear
    cx, cy, cz = (float(v) for v in opt.get("centre", "%f,100,%f" % (ox, oz)).split(","))
    rng = np.random.default_rng(int(opt.get("seed", 5)))
    for _ in range(3000):
        r = rng.uniform(25, 170)
        a_ = rng.uniform(0, 2 * math.pi)
        x, z = cx + r * math.cos(a_), cz + r * math.sin(a_)
        g = ground(x, z)
        if g is None:
            continue
        y = g + rng.choice([1.8, 4, 10, 25, 50])
        tx, ty, tz = cx + rng.uniform(-25, 25), cy + rng.uniform(0, 12), cz + rng.uniform(-25, 25)
        d = Vector((tx - x, ty - y, tz - z))
        if min(clear(x, y, z, *v, 3) for v in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0))) < 2.5:
            continue
        if clear(x, y, z, d.x, d.y, d.z, d.length) < d.length - 2:
            continue
        found.append((rng.random(), x, y, z, d.x / d.length, d.z / d.length, tx, ty, tz))
    found = [(f[0], f[1], f[2], f[3], f[4], f[5]) + tuple(f[6:]) for f in found]
found.sort(reverse=True)
picked = []
for f in found:
    if any(math.hypot(f[1] - p[1], f[3] - p[3]) < 15 for p in picked):
        continue
    picked.append(f)
    if len(picked) == int(opt.get("count", 6)):
        break
for f in picked:
    score, x, y, z, dx, dz = f[:6]
    at = f[6:] if len(f) > 6 else (x + dx * 30, y + (1.5 if KIND == "street" else 3), z + dz * 30)
    print("[views] %s score %.2f  --cam %.1f,%.1f,%.1f --at %.1f,%.1f,%.1f" % (KIND, score, x, y, z, *at))
