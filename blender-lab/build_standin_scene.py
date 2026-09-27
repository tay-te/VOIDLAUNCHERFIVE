"""
Stand-in Minecraft scene for MC FILM LAB.

Builds what a typical Mineways / jmc2obj world export looks like once it is in Blender:
1 m blocks, 16 px pixel-art textures on Closest interpolation, chunk meshes with one
material per texture, unwelded quads (every face has its own 4 vertices), plus the usual
"Minecraft with shaders" dressing: noon-ish sun, physical sky, a flat global haze, a
compositor saturation boost and waving plants.

All textures are painted procedurally here (no Mojang assets).

    blender -b --factory-startup --python build_standin_scene.py -- <out_dir>

Writes <out_dir>/standin_minecraft.blend and <out_dir>/textures/*.png
"""
import bpy
import math
import os
import sys

import numpy as np
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = os.path.abspath(argv[0] if argv else os.path.join(os.getcwd(), "standin"))
TEX = os.path.join(OUT, "textures")
os.makedirs(TEX, exist_ok=True)
V5 = bpy.app.version >= (5, 0, 0)
bpy.ops.wm.read_homefile(use_empty=True)   # no default cube/camera/world name clashes

# ----------------------------------------------------------------------------- textures
def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def speckle(pal, seed, weights=None, size=16):
    r = np.random.default_rng(seed)
    cols = np.array([hexc(c) for c in pal])
    idx = r.choice(len(pal), size=(size, size), p=weights)
    return cols[idx]


def rgba(rgb, a=None):
    a = np.ones(rgb.shape[:2]) if a is None else a
    return np.dstack([rgb, a])


def voronoi_cells(n, seed, size=16):
    r = np.random.default_rng(seed)
    pts = r.uniform(0, size, (n, 2))
    yy, xx = np.mgrid[0:size, 0:size] + 0.5
    d = []
    for p in pts:  # toroidal distance so the texture tiles
        dx = np.minimum(abs(xx - p[0]), size - abs(xx - p[0]))
        dy = np.minimum(abs(yy - p[1]), size - abs(yy - p[1]))
        d.append(np.hypot(dx, dy))
    d = np.array(d)
    order = np.sort(d, axis=0)
    return np.argmin(d, axis=0), order[1] - order[0]


def tex_grass_top():
    return rgba(speckle(["#5B8A34", "#6E9E3F", "#7FB04A", "#4E7A2B", "#89B955"], 1,
                        [.25, .3, .2, .15, .1]))


def tex_dirt():
    return rgba(speckle(["#6C4A30", "#866043", "#9A7150", "#5A3C26", "#7B7B74"], 2,
                        [.25, .35, .2, .15, .05]))


def tex_grass_side():
    img = tex_dirt()[:, :, :3].copy()
    g = tex_grass_top()[:, :, :3]
    r = np.random.default_rng(3)
    for x in range(16):
        depth = r.choice([2, 3, 3, 4, 5])
        img[:depth, x] = g[:depth, x]
    return rgba(img)


def tex_stone():
    base = speckle(["#7F7F7F", "#8C8C8C", "#747474", "#999999", "#6A6A6A"], 4,
                   [.3, .25, .2, .1, .15])
    cells, _ = voronoi_cells(6, 44)
    shade = np.random.default_rng(45).uniform(-0.05, 0.05, 6)[cells]
    return rgba(np.clip(base + shade[..., None], 0, 1))


def tex_cobblestone():
    cells, edge = voronoi_cells(10, 5)
    r = np.random.default_rng(6)
    tone = r.uniform(0.45, 0.68, 10)[cells]
    img = np.repeat(tone[..., None], 3, axis=2) + r.uniform(-0.04, 0.04, (16, 16, 1))
    img[edge < 1.1] = hexc("#3F3F3F") + r.uniform(-0.03, 0.03, (int((edge < 1.1).sum()), 1))
    return rgba(np.clip(img, 0, 1))


def tex_stone_bricks(moss=False):
    r = np.random.default_rng(7 if not moss else 8)
    img = speckle(["#8A8A8A", "#7E7E7E", "#959595", "#848484"], 9)
    mortar = hexc("#4A4A4A")
    for row in range(4):
        y0 = row * 4
        img[y0 + 3, :] = mortar
        off = 0 if row % 2 == 0 else 4
        for jx in (off + 7, (off + 15) % 16):
            img[y0:y0 + 3, jx] = mortar
        img[y0, :] += 0.06          # lit upper lip of each course
        img[y0 + 2, :] -= 0.05
    if moss:
        m = r.random((16, 16)) < 0.28
        img[m] = speckle(["#4F6B2A", "#5E7D31", "#44602A"], 10)[m]
    return rgba(np.clip(img, 0, 1))


def tex_oak_log():
    r = np.random.default_rng(11)
    cols = np.array([hexc(c) for c in ["#6B5130", "#5A4428", "#7A5E38", "#4A371F"]])
    colidx = r.integers(0, 4, 16)
    img = cols[colidx][None, :, :].repeat(16, 0) + r.uniform(-0.03, 0.03, (16, 16, 1))
    for x in r.choice(16, 4, replace=False):
        ys = r.integers(0, 16, 2)
        img[min(ys):max(ys) + 1, x] = hexc("#3E2E1A")
    return rgba(np.clip(img, 0, 1))


def tex_oak_log_top():
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.maximum(abs(xx - 7.5), abs(yy - 7.5))
    rings = np.array([hexc(c) for c in ["#B39460", "#9C7C48", "#AD8D57", "#8A6A3A",
                                        "#A58550", "#8F703F", "#9A7A46", "#5A4428"]])
    img = rings[np.clip(d.astype(int), 0, 7)]
    img += np.random.default_rng(12).uniform(-0.03, 0.03, (16, 16, 1))
    return rgba(np.clip(img, 0, 1))


def tex_oak_planks():
    r = np.random.default_rng(13)
    img = np.zeros((16, 16, 3))
    for p in range(4):
        tone = hexc(["#A2824E", "#B08D58", "#9A7A48", "#A8864F"][p])
        img[p * 4:p * 4 + 4] = tone + r.uniform(-0.035, 0.035, (4, 16, 1))
        img[p * 4 + 3] = tone * 0.72
        jx = r.integers(2, 14)
        img[p * 4:p * 4 + 3, jx] = tone * 0.78
        for _ in range(2):
            gx, gy = r.integers(0, 12), p * 4 + r.integers(0, 3)
            img[gy, gx:gx + r.integers(2, 5)] = tone * 0.86
    return rgba(np.clip(img, 0, 1))


def tex_oak_leaves():
    r = np.random.default_rng(14)
    img = speckle(["#3F6B1F", "#4F7F28", "#2F5418", "#5E8F30", "#6FA23A"], 15,
                  [.3, .3, .15, .17, .08])
    a = (r.random((16, 16)) > 0.2).astype(float)
    return rgba(img, a)


def tex_sand():
    return rgba(speckle(["#DBCFA3", "#D1C391", "#E3D8B0", "#C9BA86"], 16))


def tex_gravel():
    return rgba(speckle(["#857F7C", "#6E6866", "#9A938F", "#5C5551", "#8A7E72"], 17))


def tex_water():
    r = np.random.default_rng(18)
    img = speckle(["#2F5CBF", "#3A6BD0", "#2A50A8", "#355FC4"], 19)
    for _ in range(6):
        y, x = r.integers(0, 16), r.integers(0, 12)
        img[y, x:x + r.integers(2, 5)] = hexc("#5C8BE0")
    return rgba(img, np.full((16, 16), 0.78))


def tex_glowstone():
    cells, edge = voronoi_cells(9, 20)
    r = np.random.default_rng(21)
    pal = np.array([hexc(c) for c in ["#FFE7A3", "#F6C35A", "#D99A3A", "#FFF3CF", "#B57A2B"]])
    img = pal[r.integers(0, 5, 9)][cells]
    img[edge < 0.9] = hexc("#8A5A22")
    return rgba(img)


def blank(a=0.0):
    return np.zeros((16, 16, 4)) + np.array([0, 0, 0, a])


def tex_torch_stick():
    img = speckle(["#6B4F2A", "#8A6A3A", "#5A4122"], 22)
    return rgba(img)


def tex_torch_flame():
    img = np.zeros((16, 16, 3))
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.hypot(xx - 7.5, (yy - 9) * 0.8)
    img[:] = hexc("#FF8A1E")
    img[d < 6] = hexc("#FFC94A")
    img[d < 3.5] = hexc("#FFF3B0")
    return rgba(img)


def tex_lantern():
    img = np.zeros((16, 16, 3)) + hexc("#FFB347")
    yy, xx = np.mgrid[0:16, 0:16]
    img[(abs(xx - 7.5) < 3) & (abs(yy - 8) < 4)] = hexc("#FFE9A8")
    frame = (xx < 2) | (xx > 13) | (yy < 2) | (yy > 13) | (abs(xx - 7.5) < 0.6)
    img[frame] = hexc("#2E3136")
    img[(yy == 0) | (yy == 15)] = hexc("#43474E")
    return rgba(img)


def tex_door(top):
    r = np.random.default_rng(23 + top)
    img = np.zeros((16, 16, 3))
    for c in range(4):
        tone = hexc(["#8F6B3C", "#9C7845", "#86633A", "#957140"][c])
        img[:, c * 4:c * 4 + 4] = tone + r.uniform(-0.03, 0.03, (16, 4, 1))
        img[:, c * 4 + 3] = tone * 0.75
    img[[0, 15], :] = hexc("#6A4C27")
    img[:, [0, 15]] = hexc("#6A4C27")
    if top:
        img[3:8, 3:7] = hexc("#2A2F38")
        img[3:8, 9:13] = hexc("#2A2F38")
    else:
        img[7, 11:13] = hexc("#3B3B3B")
    return rgba(np.clip(img, 0, 1))


def tex_cross(kind):
    r = np.random.default_rng(30 + len(kind))
    img = np.zeros((16, 16, 3))
    a = np.zeros((16, 16))
    greens = [hexc(c) for c in ["#4E7A2B", "#5E8F35", "#6FA23F", "#3F6B22"]]
    if kind == "short_grass":
        for x in range(1, 15, 2):
            h = r.integers(5, 14)
            for y in range(16 - h, 16):
                xx = min(15, max(0, x + (1 if (y + x) % 5 == 0 else 0)))
                img[y, xx] = greens[r.integers(0, 4)]
                a[y, xx] = 1
    else:
        petal = hexc("#C8282A") if kind == "poppy" else hexc("#F2D338")
        for y in range(8, 16):
            img[y, 7] = greens[1]
            a[y, 7] = 1
        img[9, 8] = greens[2]
        a[9, 8] = 1
        for (y, x) in [(4, 6), (4, 7), (4, 8), (5, 6), (5, 7), (5, 8), (5, 9), (6, 6),
                       (6, 7), (6, 8), (3, 7), (6, 5), (7, 7)]:
            img[y, x] = petal * (0.8 if (x + y) % 3 == 0 else 1.0)
            a[y, x] = 1
        img[5, 7] = hexc("#2A1E12") if kind == "poppy" else hexc("#F7A928")
    return rgba(img, a)


SKIN, SKIN_D = hexc("#C9A27E"), hexc("#B18A68")
HOOD = ["#8E2B22", "#A2352A", "#7A2219", "#B24536"]


def tex_face():
    img = speckle([HOOD[0], HOOD[1], HOOD[2]], 40)
    img[4:16, 2:14] = SKIN
    img[4, 2:14] = SKIN_D
    img[8, 4:6] = [hexc("#F2F2F2"), hexc("#2B4A7A")]
    img[8, 10:12] = [hexc("#2B4A7A"), hexc("#F2F2F2")]
    img[7, 3:7] = hexc("#5A3A22")
    img[7, 9:13] = hexc("#5A3A22")
    img[10:12, 7:9] = SKIN_D
    img[12:16, 3:13] = hexc("#6E4A2E")   # beard
    img[13, 6:10] = hexc("#8A5C3A")
    return rgba(img)


def tex_hood():
    return rgba(speckle(HOOD, 41))


def tex_tunic():
    img = speckle(HOOD, 42)
    img[10:12] = hexc("#4A3222")
    img[10:12, 7:9] = hexc("#C9A94A")
    img[12:16] = speckle(["#5A3C2A", "#6B4A33", "#4E3424"], 43)[12:16]
    return rgba(img)


def tex_arm():
    img = speckle(HOOD, 44)
    img[12:16] = SKIN
    img[12] = SKIN_D
    return rgba(img)


def tex_leg():
    img = speckle(["#3B3A4A", "#45445A", "#34333F"], 45)
    img[11:16] = speckle(["#4A3526", "#5A4130", "#3E2C1F"], 46)[11:16]
    return rgba(img)


TEXTURES = {
    "grass_block_top": tex_grass_top, "grass_block_side": tex_grass_side, "dirt": tex_dirt,
    "stone": tex_stone, "cobblestone": tex_cobblestone, "stone_bricks": tex_stone_bricks,
    "mossy_stone_bricks": lambda: tex_stone_bricks(True), "oak_log": tex_oak_log,
    "oak_log_top": tex_oak_log_top, "oak_planks": tex_oak_planks, "oak_leaves": tex_oak_leaves,
    "sand": tex_sand, "gravel": tex_gravel, "water_still": tex_water, "glowstone": tex_glowstone,
    "torch_stick": tex_torch_stick, "torch_flame": tex_torch_flame, "lantern": tex_lantern,
    "oak_door_top": lambda: tex_door(1), "oak_door_bottom": lambda: tex_door(0),
    "short_grass": lambda: tex_cross("short_grass"), "poppy": lambda: tex_cross("poppy"),
    "dandelion": lambda: tex_cross("dandelion"),
    "wanderer_face": tex_face, "wanderer_hood": tex_hood, "wanderer_tunic": tex_tunic,
    "wanderer_arm": tex_arm, "wanderer_leg": tex_leg,
}
ALPHA_TEX = {"oak_leaves", "water_still", "short_grass", "poppy", "dandelion"}
EMISSIVE = {"glowstone": 6.0, "torch_flame": 12.0, "lantern": 5.0}


def save_png(name, arr):
    img = bpy.data.images.new(name + ".png", 16, 16, alpha=True)
    img.pixels.foreach_set(np.flipud(arr).astype(np.float32).ravel())
    path = os.path.join(TEX, name + ".png")
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()
    bpy.data.images.remove(img)
    return path


def make_material(name):
    path = save_png(name, TEXTURES[name]())
    mat = bpy.data.materials.new(name)
    if not V5:
        mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    img = bpy.data.images.load(path, check_existing=True)
    tn = nt.nodes.new("ShaderNodeTexImage")
    tn.image = img
    tn.interpolation = "Closest"
    tn.location = (-420, 220)
    nt.links.new(tn.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.6
    if name in ALPHA_TEX:
        nt.links.new(tn.outputs["Alpha"], bsdf.inputs["Alpha"])
        if hasattr(mat, "surface_render_method"):
            mat.surface_render_method = "DITHERED"
    if name == "water_still":
        bsdf.inputs["Roughness"].default_value = 0.08
    if name in EMISSIVE:
        nt.links.new(tn.outputs["Color"], bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = EMISSIVE[name]
    return mat


# ----------------------------------------------------------------------------- world gen
SX, SY, SZ = 128, 128, 56
AIR, GRASS, DIRT, STONE, COBBLE, BRICKS, MOSSY, LOG, PLANKS, LEAVES, SAND, GRAVEL, WATER, GLOW = range(14)
FACE_TEX = {  # block: (top, side, bottom)
    GRASS: ("grass_block_top", "grass_block_side", "dirt"), DIRT: ("dirt",) * 3,
    STONE: ("stone",) * 3, COBBLE: ("cobblestone",) * 3, BRICKS: ("stone_bricks",) * 3,
    MOSSY: ("mossy_stone_bricks",) * 3, LOG: ("oak_log_top", "oak_log", "oak_log_top"),
    PLANKS: ("oak_planks",) * 3, LEAVES: ("oak_leaves",) * 3, SAND: ("sand",) * 3,
    GRAVEL: ("gravel",) * 3, WATER: ("water_still",) * 3, GLOW: ("glowstone",) * 3,
}
OPAQUE = np.ones(14, bool)
OPAQUE[[AIR, LEAVES, WATER]] = False
WATER_LEVEL = 4


def value_noise(shape, cell, seed):
    r = np.random.default_rng(seed)
    gx, gy = shape[0] // cell + 2, shape[1] // cell + 2
    g = r.random((gx, gy))
    xs = np.arange(shape[0]) / cell
    ys = np.arange(shape[1]) / cell
    x0, y0 = xs.astype(int), ys.astype(int)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = g[x0][:, y0]
    b = g[x0 + 1][:, y0]
    c = g[x0][:, y0 + 1]
    d = g[x0 + 1][:, y0 + 1]
    fx, fy = fx[:, None], fy[None, :]
    return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy


def fbm(shape, seed):
    return (value_noise(shape, 32, seed) * 0.55 + value_noise(shape, 16, seed + 1) * 0.3
            + value_noise(shape, 8, seed + 2) * 0.15)


xx, yy = np.mgrid[0:SX, 0:SY].astype(float)
n1, n2 = fbm((SX, SY), 100), fbm((SX, SY), 200)
H = 7.0 + (n1 - 0.5) * 5.0
H -= 5.5 * np.exp(-((yy - 30) / 8.0) ** 2) * (0.8 + 0.4 * n2)            # east-west valley
H += 4.0 * np.exp(-(((xx - 64) / 20.0) ** 2 + ((yy - 70) / 16.0) ** 2))   # tower hill
mount = np.clip((yy - 84) / 40.0, 0, 1)
ridge = 1 - np.abs(2 * (0.7 * value_noise((SX, SY), 20, 500) + 0.3 * value_noise((SX, SY), 9, 501)) - 1)
H += (mount ** 1.3) * (12.0 + 32.0 * ridge ** 1.5)                         # ridged mountains north
H += np.clip((np.abs(xx - 64) - 44) / 20.0, 0, 1) * 9.0 * n2               # side hills
H += np.clip((12 - yy) / 12.0, 0, 1) * 3.0                                # south bank
H = np.clip(np.round(H), 2, SZ - 12).astype(int)

W = np.zeros((SX, SY, SZ), np.uint8)
slope = np.maximum(np.abs(np.gradient(H.astype(float))[0]), np.abs(np.gradient(H.astype(float))[1]))
for x in range(SX):
    for y in range(SY):
        h = H[x, y]
        W[x, y, 0:max(0, h - 3)] = STONE
        rocky = h >= 27 or slope[x, y] >= 2.5
        W[x, y, max(0, h - 3):h] = STONE if rocky else DIRT
        top = STONE if rocky else GRASS
        if h <= WATER_LEVEL + 1:
            top = SAND
        W[x, y, h] = top
        if h < WATER_LEVEL:
            W[x, y, h + 1:WATER_LEVEL + 1] = WATER
gpatch = value_noise((SX, SY), 6, 400) > 0.78
for x, y in zip(*np.nonzero(gpatch & (H > WATER_LEVEL + 1) & (H < 20))):
    W[x, y, H[x, y]] = GRAVEL if (x + y) % 3 else COBBLE


def ground(x, y):
    col = W[x, y]
    solid = np.nonzero(OPAQUE[col])[0]
    return int(solid.max()) + 1


# -- the tower: 7x7 stone-brick keep with windows, crenellations and a glowing room
TX, TY = 61, 69
tg = min(ground(x, y) for x in range(TX - 1, TX + 8) for y in range(TY - 1, TY + 8))
W[TX - 1:TX + 8, TY - 1:TY + 8, tg - 1] = COBBLE              # foundation pad
W[TX - 1:TX + 8, TY - 1:TY + 8, tg:] = AIR
for x in range(TX - 1, TX + 8):
    for y in range(TY - 1, TY + 8):
        W[x, y, :tg - 1][W[x, y, :tg - 1] == AIR] = DIRT
TH = 22
rng = np.random.default_rng(9)
for z in range(tg, tg + TH):
    for x in range(TX, TX + 7):
        for y in range(TY, TY + 7):
            edge = x in (TX, TX + 6) or y in (TY, TY + 6)
            if edge:
                blk = BRICKS
                if z < tg + 4 and rng.random() < 0.45:
                    blk = MOSSY if rng.random() < 0.6 else COBBLE
                if (x in (TX, TX + 6)) and (y in (TY, TY + 6)):
                    blk = COBBLE if z % 4 else BRICKS
                W[x, y, z] = blk
    if (z - tg) % 6 == 5:
        W[TX + 1:TX + 6, TY + 1:TY + 6, z] = PLANKS
top_z = tg + TH
W[TX - 1:TX + 8, TY - 1:TY + 8, top_z] = BRICKS
W[TX:TX + 7, TY:TY + 7, top_z] = PLANKS
for x in range(TX - 1, TX + 8):
    for y in range(TY - 1, TY + 8):
        if (x in (TX - 1, TX + 7) or y in (TY - 1, TY + 7)) and (x + y) % 2 == 0:
            W[x, y, top_z + 1] = BRICKS
W[TX + 3, TY, tg:tg + 2] = AIR                                   # door opening (south)
for zz in (tg + 6, tg + 12, tg + 17):                            # windows
    W[TX + 3, TY, zz:zz + 2] = AIR
    W[TX, TY + 3, zz:zz + 2] = AIR
    W[TX + 6, TY + 3, zz:zz + 2] = AIR
W[TX + 3, TY + 3, tg + 12] = GLOW                                # lit room behind a window
W[TX + 3, TY + 2, tg + 11] = GLOW


def tree(x, y, h=5):
    g = ground(x, y)
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            for dz in range(h - 3, h + 1):
                r = abs(dx) + abs(dy)
                if dz >= h - 1 and r > 2:
                    continue
                if dz < h - 1 and abs(dx) == 2 and abs(dy) == 2 and (x + y + dz) % 2:
                    continue
                if W[x + dx, y + dy, g + dz] == AIR:
                    W[x + dx, y + dy, g + dz] = LEAVES
    W[x, y, g + h] = LEAVES
    W[x, y, g:g + h] = LOG


for (tx, ty, th) in [(53, 66, 5), (74, 60, 6), (47, 57, 5), (81, 76, 5), (40, 77, 6),
                     (70, 12, 5), (35, 13, 6), (90, 55, 5), (57, 82, 5), (26, 60, 6),
                     (100, 40, 5), (20, 36, 5), (86, 16, 5)]:
    tree(tx, ty, th)
forest = np.random.default_rng(55)
placed = 0
while placed < 70:                                   # scattered woods, denser on the flanks
    tx, ty = forest.integers(4, SX - 4), forest.integers(4, 96)
    if math.hypot(tx - (TX + 3), ty - (TY + 3)) < 11 or math.hypot(tx - 58, ty - 61) < 5:
        continue
    if H[tx, ty] <= WATER_LEVEL + 1 or slope[tx, ty] >= 2 or W[tx, ty, ground(tx, ty) - 1] != GRASS:
        continue
    if forest.random() > 0.35 + 0.6 * min(1.0, abs(tx - 64) / 48.0):
        continue
    if np.any(W[tx - 2:tx + 3, ty - 2:ty + 3, ground(tx, ty):] == LOG):
        continue
    tree(tx, ty, int(forest.integers(4, 7)))
    placed += 1

# ----------------------------------------------------------------------------- meshing
DIRS = [((1, 0, 0), "side"), ((-1, 0, 0), "side"), ((0, 1, 0), "side"),
        ((0, -1, 0), "side"), ((0, 0, 1), "top"), ((0, 0, -1), "bottom")]
QUADS = {  # corner offsets per direction, counter-clockwise seen from outside; uv order
    (1, 0, 0): [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)],
    (-1, 0, 0): [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    (0, 1, 0): [(1, 1, 0), (0, 1, 0), (0, 1, 1), (1, 1, 1)],
    (0, -1, 0): [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],
    (0, 0, 1): [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
    (0, 0, -1): [(0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)],
}
UVS = [(0, 0), (1, 0), (1, 1), (0, 1)]
TEX_NAMES = sorted(TEXTURES)
MATS = {}


def mat(name):
    if name not in MATS:
        MATS[name] = make_material(name)
    return MATS[name]


chunks = {}
pad = np.zeros((SX + 2, SY + 2, SZ + 2), np.uint8)
pad[1:-1, 1:-1, 1:-1] = W
for d, kind in DIRS:
    nb = pad[1 + d[0]:SX + 1 + d[0], 1 + d[1]:SY + 1 + d[1], 1 + d[2]:SZ + 1 + d[2]]
    vis = (W != AIR) & (~OPAQUE[nb]) & ((nb != W) | (W == LEAVES))
    if d == (0, 0, -1):
        vis &= np.arange(SZ)[None, None, :] > 1
    for x, y, z in zip(*np.nonzero(vis)):
        b = W[x, y, z]
        if b == WATER and d != (0, 0, 1):
            continue
        t = FACE_TEX[b][{"top": 0, "side": 1, "bottom": 2}[kind]]
        key = (x // 16, y // 16)
        c = chunks.setdefault(key, {"v": [], "f": [], "uv": [], "m": []})
        base = len(c["v"])
        for (ox, oy, oz) in QUADS[d]:
            zz = z + oz
            if b == WATER and oz == 1:
                zz = z + 0.875
            c["v"].append((x + ox, y + oy, zz))
        c["f"].append((base, base + 1, base + 2, base + 3))
        c["uv"].extend(UVS)
        c["m"].append(t)

coll_world = bpy.data.collections.new("World_Export")
bpy.context.scene.collection.children.link(coll_world)


def build_mesh(name, data, coll):
    me = bpy.data.meshes.new(name)
    me.from_pydata(data["v"], [], data["f"])
    names = sorted(set(data["m"]))
    for n in names:
        me.materials.append(mat(n))
    me.polygons.foreach_set("material_index", [names.index(m) for m in data["m"]])
    uvl = me.uv_layers.new(name="UVMap")
    uvl.data.foreach_set("uv", [c for uv in data["uv"] for c in uv])
    me.update()
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    return ob


for (cx, cy), data in sorted(chunks.items()):
    build_mesh(f"chunk_{cx:02d}_{cy:02d}", data, coll_world)


def box(data, lo, hi, tex, uv_tex=None):
    """Axis-aligned box as 6 separate quads (exporter style)."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    for d, _ in DIRS:
        base = len(data["v"])
        for (ox, oy, oz) in QUADS[d]:
            data["v"].append((x1 if ox else x0, y1 if oy else y0, z1 if oz else z0))
        data["f"].append((base, base + 1, base + 2, base + 3))
        data["uv"].extend(UVS)
        t = tex(d) if callable(tex) else tex
        data["m"].append(t)


# -- plants (cross models) with the shader-pack "Wind" sway
coll_props = bpy.data.collections.new("Props")
bpy.context.scene.collection.children.link(coll_props)
plants = {"v": [], "f": [], "uv": [], "m": []}
r = np.random.default_rng(77)
for x in range(20, 108):
    for y in range(8, 92):
        g = ground(x, y)
        if W[x, y, g - 1] != GRASS or W[x, y, g] != AIR:
            continue
        near = math.hypot(x - 60, y - 55) < 38
        p = r.random()
        if p < (0.16 if near else 0.05):
            kind = "short_grass" if p > 0.022 else ("poppy" if p > 0.011 else "dandelion")
            jx, jy = r.uniform(-0.15, 0.15, 2)
            for (a, b) in [((0, 0), (1, 1)), ((1, 0), (0, 1))]:
                base = len(plants["v"])
                (ax, ay), (bx, by) = a, b
                ins = 0.15
                ax, bx = ax + (ins if ax == 0 else -ins), bx + (ins if bx == 0 else -ins)
                ay, by = ay + (ins if ay == 0 else -ins), by + (ins if by == 0 else -ins)
                plants["v"] += [(x + ax + jx, y + ay + jy, g), (x + bx + jx, y + by + jy, g),
                                (x + bx + jx, y + by + jy, g + 0.85), (x + ax + jx, y + ay + jy, g + 0.85)]
                plants["f"].append((base, base + 1, base + 2, base + 3))
                plants["uv"].extend(UVS)
                plants["m"].append(kind)
ob_pl = build_mesh("plants", plants, coll_props)
wind = ob_pl.modifiers.new("Wind", "WAVE")
wind.height, wind.width, wind.speed, wind.narrowness = 0.04, 3.0, 0.08, 1.2

# -- door, torches, hanging lantern
door = {"v": [], "f": [], "uv": [], "m": []}
box(door, (TX + 3, TY + 0.02, tg), (TX + 4, TY + 0.2, tg + 1), "oak_door_bottom")
box(door, (TX + 3, TY + 0.02, tg + 1), (TX + 4, TY + 0.2, tg + 2), "oak_door_top")
build_mesh("oak_door", door, coll_props)


def torch(name, x, y, z, tilt_y=0.0):
    t = {"v": [], "f": [], "uv": [], "m": []}
    box(t, (-0.0625, -0.0625, 0), (0.0625, 0.0625, 0.5), "torch_stick")
    box(t, (-0.0625, -0.0625, 0.5), (0.0625, 0.0625, 0.625), "torch_flame")
    ob = build_mesh(name, t, coll_props)
    ob.location = (x, y, z)
    ob.rotation_euler = (math.radians(tilt_y), 0, 0)
    return ob


torch("wall_torch_L", TX + 1.5, TY - 0.28, tg + 1.2, 22.5)
torch("wall_torch_R", TX + 5.5, TY - 0.28, tg + 1.2, 22.5)

# -- the subject: a hooded wanderer holding a lantern (MC proportions, 1.8 m tall)
WX, WY = 58.5, 61.5
wg = ground(int(WX), int(WY))
wan = {"v": [], "f": [], "uv": [], "m": []}
box(wan, (-0.25, -0.25, 1.4), (0.25, 0.25, 1.9),
    lambda d: "wanderer_face" if d == (0, -1, 0) else "wanderer_hood")
box(wan, (-0.25, -0.125, 0.7), (0.25, 0.125, 1.4), "wanderer_tunic")
box(wan, (-0.5, -0.125, 0.7), (-0.25, 0.125, 1.4), "wanderer_arm")
box(wan, (-0.25, -0.125, 0.0), (0.0, 0.125, 0.7), "wanderer_leg")
box(wan, (0.0, -0.125, 0.0), (0.25, 0.125, 0.7), "wanderer_leg")
ob_w = build_mesh("Wanderer", wan, coll_props)
arm = {"v": [], "f": [], "uv": [], "m": []}
box(arm, (0.0, -0.125, -0.7), (0.25, 0.125, 0.0), "wanderer_arm")
ob_arm = build_mesh("Wanderer_arm_R", arm, coll_props)
ob_arm.parent = ob_w
ob_arm.location = (0.25, 0, 1.4)
ob_arm.rotation_euler = (math.radians(-38), 0, 0)
lan = {"v": [], "f": [], "uv": [], "m": []}
box(lan, (-0.16, -0.16, -0.42), (0.16, 0.16, 0.0), "lantern")
ob_lan = build_mesh("lantern_held", lan, coll_props)
ob_lan.parent = ob_arm
ob_lan.location = (0.125, 0.0, -0.72)
ob_lan.rotation_euler = (math.radians(38), 0, 0)
ob_w.location = (WX, WY, wg)
ob_w.rotation_euler = (0, 0, math.radians(-25))

# ----------------------------------------------------------------------------- look dev ("shader pack")
scene = bpy.context.scene
scene.render.engine = "BLENDER_EEVEE" if V5 else "BLENDER_EEVEE_NEXT"
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.eevee.taa_render_samples = 32
scene.view_settings.view_transform = "Standard"
scene.frame_start, scene.frame_end, scene.frame_current = 1, 120, 1

sun_data = bpy.data.lights.new("Sun", "SUN")
sun_data.energy = 4.5
sun_data.color = (1.0, 0.96, 0.9)
sun_data.angle = math.radians(1.0)
sun = bpy.data.objects.new("Sun", sun_data)
scene.collection.objects.link(sun)
sun.rotation_euler = (math.radians(40), 0, math.radians(35))   # high, from behind the player

world = bpy.data.worlds.new("World")
scene.world = world
if not V5:
    world.use_nodes = True
wn = world.node_tree
bg = wn.nodes["Background"]
sky = wn.nodes.new("ShaderNodeTexSky")
sky.sky_type = "MULTIPLE_SCATTERING" if V5 else "NISHITA"
sky.sun_disc = False
sky.sun_elevation = math.radians(50)
sky.sun_rotation = math.radians(215)
sky.location = (-300, 300)
wn.links.new(sky.outputs["Color"], bg.inputs["Color"])
bg.inputs["Strength"].default_value = 0.35
# flat global haze. EEVEE Next lets an infinite world volume swallow sun and sky light,
# so (like Blender's "Convert Volume" operator) it lives on a mesh that wraps the map.
haze_mat = bpy.data.materials.new("Haze")
if not V5:
    haze_mat.use_nodes = True
hn = haze_mat.node_tree
hn.nodes.remove(hn.nodes["Principled BSDF"])
haze = hn.nodes.new("ShaderNodeVolumePrincipled")
haze.inputs["Density"].default_value = 0.0035
haze.inputs["Color"].default_value = (0.75, 0.85, 1.0, 1)
hn.links.new(haze.outputs["Volume"], hn.nodes["Material Output"].inputs["Volume"])
hme = bpy.data.meshes.new("Atmosphere")
hme.from_pydata([(x, y, z) for x in (-40, SX + 40) for y in (-60, SY + 40) for z in (0, 70)], [],
                [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)])
hme.materials.append(haze_mat)
atmo = bpy.data.objects.new("Atmosphere", hme)
scene.collection.objects.link(atmo)

cam_data = bpy.data.cameras.new("Camera")
cam_data.sensor_fit = "VERTICAL"
cam_data.angle_y = math.radians(70)   # Minecraft's default FOV is 70 deg vertical
cam = bpy.data.objects.new("Camera", cam_data)
scene.collection.objects.link(cam)
px, py = 55, 40
eye = Vector((px + 0.5, py + 0.5, ground(px, py) + 1.62))
look = Vector((TX + 3.5, TY + 3.5, tg + 9))
cam.location = eye
cam.rotation_euler = (look - eye).to_track_quat("-Z", "Y").to_euler()
scene.camera = cam

# existing compositor: a shader-pack style saturation punch
if V5:
    comp = bpy.data.node_groups.new("Compositing", "CompositorNodeTree")
    scene.compositing_node_group = comp
    comp.interface.new_socket("Image", in_out="OUTPUT", socket_type="NodeSocketColor")
    out = comp.nodes.new("NodeGroupOutput")
else:
    scene.use_nodes = True
    comp = scene.node_tree
    for n in list(comp.nodes):
        comp.nodes.remove(n)
    out = comp.nodes.new("CompositorNodeComposite")
rl = comp.nodes.new("CompositorNodeRLayers")
hs = comp.nodes.new("CompositorNodeHueSat")
hs.name = hs.label = "Saturation Punch"
hs.inputs["Saturation"].default_value = 1.18
rl.location, hs.location, out.location = (-400, 0), (-100, 0), (200, 0)
comp.links.new(rl.outputs["Image"], hs.inputs["Image"])
comp.links.new(hs.outputs["Image"], out.inputs[0])

blend_path = os.path.join(OUT, "standin_minecraft.blend")
bpy.ops.wm.save_as_mainfile(filepath=blend_path)
bpy.ops.file.make_paths_relative()
bpy.ops.wm.save_mainfile(filepath=blend_path)
faces = sum(len(o.data.polygons) for o in bpy.data.objects if o.type == "MESH")
print(f"STANDIN_OK faces={faces} objects={len(bpy.data.objects)} tower_ground={tg} "
      f"wanderer=({WX},{WY},{wg}) materials={len(bpy.data.materials)}")
