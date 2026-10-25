"""
Concept: "Same horizon". Real land from a coarse reel.sh terrain grab (12 km) split in two: VOID LOD's half is all
there as soon as the world loads, because it samples the terrain model one column at a time; Distant Horizons' half has
only its near ring built, the rest still empty chunk outlines, because it runs the full world generator on every far
chunk. Shown as the moment just after loading.

    blender -b --factory-startup -P tools/blender/concept_bench_split.py -- TERRAIN_DIR OUT.png
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
meta = json.load(open(os.path.join(TERRAIN, "meta.json")))
n = meta["nx"]
ground = np.fromfile(os.path.join(TERRAIN, "ground.f32"), "<f4").reshape(n, n)
surface = np.fromfile(os.path.join(TERRAIN, "surface.f32"), "<f4").reshape(n, n)
c = n // 2
R = 100                                   # cells each side: 200 x 40 blocks = 8 km
g = ground[c - R:c + R, c - R:c + R]
s = surface[c - R:c + R, c - R:c + R]
water = s > g + 0.5
VS = 1.0 / 40 * 2.2                       # 40 blocks a cell, relief exaggerated 2.2x
h = np.where(water, s, g)

# colour by what the land is: water, lowland, upland, rock, snow
col = np.zeros(h.shape + (3,), np.float32)
col[:] = vk.lin(vk.hexrgb("#4f7a32"))
col[h > 140] = vk.lin(vk.hexrgb("#6b8a3c"))
col[h > 220] = vk.lin(vk.hexrgb("#7b7468"))
col[h > 330] = vk.lin(vk.hexrgb("#e8ecef"))
col[water] = vk.lin(vk.hexrgb("#3d6fb8"))
col = np.array(col) ** (1 / 2.2)          # columns() expects display-ish colours like the map ones

vk.clear()
vk.use_font()
base = h.min() - 30
SC = 0.55                                 # each diorama: 200 cells at 0.55 units
GAP = 26
W = 2 * R * SC
everywhere = np.ones(h.shape, bool)
yy, xx = np.mgrid[0:2 * R, 0:2 * R]
near = np.hypot(xx - R, yy - R) < 30      # Distant Horizons just after loading: only a ring round the player
centres = {"VOID LOD": -(W / 2 + GAP / 2), "Distant Horizons": W / 2 + GAP / 2}
for side, cx in centres.items():
    mask = everywhere if side == "VOID LOD" else near
    o = vk.columns(side, h - base, np.zeros_like(h), mask, col, cell=SC, origin=(cx - W / 2, W / 2), z_scale=VS * SC / 1.0 * 1.6)
    vk.assign(o, vk.vertex_material(side))
    bpy.ops.mesh.primitive_cone_add(vertices=24, radius1=2.0, depth=5.5, location=(cx, 0, 34), rotation=(math.pi, 0, 0))
    vk.assign(bpy.context.active_object, vk.flat("pin", "#9f8bff", emit=1.5))

# the rest of Distant Horizons' land: empty chunk outlines on a dim floor
dx = centres["Distant Horizons"]
bpy.ops.mesh.primitive_plane_add(size=1, location=(dx, 0, 0.1))
fl = bpy.context.active_object
fl.scale = (W, W, 1)
vk.assign(fl, vk.flat("dh floor", "#171a1e", rough=0.9))
lines = vk.flat("chunk line", "#3a4048", emit=0.45)
for k in range(0, 2 * R + 1, 5):
    t = -W / 2 + k * SC
    for horiz in (True, False):
        bpy.ops.mesh.primitive_cube_add(size=1)
        ln = bpy.context.active_object
        ln.location = (dx, t, 0.25) if horiz else (dx + t, 0, 0.25)
        ln.scale = (W, 0.1, 0.08) if horiz else (0.1, W, 0.08)
        vk.assign(ln, lines)

for side, cx in centres.items():
    vk.text(side, (cx, W / 2 + 6, 30), 7.5, "#f1f3f5", emit=1.6)
vk.text("all of it, the moment the world loads", (centres["VOID LOD"], W / 2 + 6, 22), 3.6, "#c7bbff", emit=1.2)
vk.text("still generating, chunk by chunk", (centres["Distant Horizons"], W / 2 + 6, 22), 3.6, "#a6aeb6", emit=1.0)

vk.camera((0, -300, 230), (0, 4, 4), lens=42)
vk.sun(elev=35, az=-60, energy=3.4)
vk.stage("#0e1012")
vk.render(OUT, samples=128)
