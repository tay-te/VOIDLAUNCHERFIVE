"""
Concept: "Blocks of frames". A diorama of real land (a reel.sh terrain grab with colours) with two columns of blocks
rising out of it, one block for every 50 fps: VOID LOD against Distant Horizons on the same views. Solid blocks reach
the low end of each measured range, glass blocks the high end.

    blender -b --factory-startup -P tools/blender/concept_bench_bars.py -- TERRAIN_DIR OUT.png
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import voxkit as vk  # noqa: E402
import bpy  # noqa: E402

argv = sys.argv[sys.argv.index("--") + 1:]
TERRAIN, OUT = argv[0], argv[1]
CX, CZ, HALF, CELL = -3795, -3606, 80, 2
VS = 0.6
FPS = {"VOID LOD": (605, 843), "Distant Horizons": (230, 264)}
PER_BLOCK = 50.0
BLOCK = 11.0

meta = json.load(open(os.path.join(TERRAIN, "meta.json")))
n, x0, z0 = meta["nx"], meta["x0"], meta["z0"]
surface = np.fromfile(os.path.join(TERRAIN, "surface.f32"), "<f4").reshape(n, n)
colour = np.fromfile(os.path.join(TERRAIN, "colour.rgb"), np.uint8).reshape(n, n, 3)
ci, cj = CX - x0, CZ - z0
sl = np.s_[cj - HALF:cj + HALF:CELL, ci - HALF:ci + HALF:CELL]
top = surface[sl]
col = colour[sl].astype(np.float32) / 255.0
col = np.where((col.sum(axis=2) == 0)[..., None], np.array([0.37, 0.55, 0.2]), col)
base = top.min() - 8

vk.clear()
vk.use_font()
land = vk.columns("diorama", top - base, np.zeros_like(top), np.ones(top.shape, bool), col, cell=CELL,
                  origin=(-HALF, HALF), z_scale=VS)
vk.assign(land, vk.vertex_material("land"))
ground_z = float(np.median(top - base)) * VS

# the two columns, standing on the land's front edge
for k, (name, (lo, hi)) in enumerate(FPS.items()):
    x = -42 if k == 0 else 42
    y = -HALF + 26
    solid = int(lo // PER_BLOCK)
    glass = int(math.ceil(hi / PER_BLOCK)) - solid
    hue = "#9f8bff" if k == 0 else "#8a929a"
    for b in range(solid + glass):
        z = ground_z + b * BLOCK + BLOCK / 2
        bpy.ops.mesh.primitive_cube_add(size=BLOCK * 0.94, location=(x, y, z))
        cube = bpy.context.active_object
        cube.data.polygons.foreach_set("use_smooth", [False] * len(cube.data.polygons))
        if b < solid:
            vk.assign(cube, vk.flat("block %s %d" % (name, b), hue, emit=0.18, rough=0.5))
        else:
            vk.assign(cube, vk.flat("glass %s %d" % (name, b), hue, emit=0.35, alpha=0.28, rough=0.2))
    top_z = ground_z + (solid + glass) * BLOCK
    vk.text("%d–%d fps" % (lo, hi), (x, y, top_z + 16), 13, "#f1f3f5", emit=1.6)
    vk.text(name, (x, y, top_z + 4), 7, "#a6aeb6", emit=1.0)

vk.text("8 km view · same machine, same views · bench of 2026-10-08, to be re-measured", (0, -HALF - 10, ground_z - 22), 4.2,
        "#6c747c", emit=0.8)

vk.camera((300, -560, 250), (0, -20, 95), lens=50, dof=(640, 8))
vk.sun(elev=40, az=-40, energy=3.0)
vk.stage("#0e1012")
vk.render(OUT, samples=128)
