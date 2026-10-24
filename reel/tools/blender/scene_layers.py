"""
The layers of the world: one real piece of land pulled apart into the mods that make it, stacked with gaps, each
labelled. From the bottom: VOID Expanse's land, its water, its woods, VOID Hearth's town and roads, VOID Atlas's
names, and on top what you actually see (the filmed frame laid over the land). Built from a `reel.sh terrain` grab
(heights and block map colours, step 1) and a top-down Reel still of the same place.

    blender -b --factory-startup -P tools/blender/scene_layers.py -- TERRAIN_DIR TOP_STILL.png OUT.png
        [--cx -3795 --cz -3606 --half 96 --cell 2]
        [--cam x,y,z,yaw,pitch,fov,w,h]   the top-down still's camera (Reel conventions)
        [--labels "Bradhurst" --far "Brambury,-4748,-3413"] [--samples 96]
"""
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Matrix, Vector

# ---------------------------------------------------------------- arguments
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
TERRAIN, STILL, OUT = argv[0], argv[1], argv[2]
opt = {}
k = 3
while k < len(argv):
    opt[argv[k].lstrip("-")] = argv[k + 1]
    k += 2
CX = float(opt.get("cx", -3795))
CZ = float(opt.get("cz", -3606))
HALF = int(opt.get("half", 96))
CELL = int(opt.get("cell", 2))
CAM = [float(v) for v in opt.get("cam", "-3795,380,-3606,180,90,36,1920,1080").split(",")]
TOWN = opt.get("labels", "Bradhurst")
FAR = opt.get("far", "Brambury,-4748,-3413").split(",")
SAMPLES = int(opt.get("samples", 96))
Y0 = 70.0           # the Minecraft height at the bottom of the stack
GAP = 100.0         # blocks between layers
VSCALE = 0.6        # the land's relief, flattened so the layers stay apart

# ---------------------------------------------------------------- data
meta = json.load(open(os.path.join(TERRAIN, "meta.json")))
n = meta["nx"]
x0, z0 = meta["x0"], meta["z0"]
surface = np.fromfile(os.path.join(TERRAIN, "surface.f32"), "<f4").reshape(n, n)
floor = np.fromfile(os.path.join(TERRAIN, "ground.f32"), "<f4").reshape(n, n)
colour = np.fromfile(os.path.join(TERRAIN, "colour.rgb"), np.uint8).reshape(n, n, 3).astype(np.int32)
rgb = (colour[..., 0] << 16) | (colour[..., 1] << 8) | colour[..., 2]

ci, cj = int(round(CX - x0)), int(round(CZ - z0))
sl = np.s_[cj - HALF:cj + HALF, ci - HALF:ci + HALF]
surface, floor, colour, rgb = surface[sl], floor[sl], colour[sl], rgb[sl]

WATER, FOLIAGE, DIRT, STONE = 0x4040FF, 0x007C00, 0x976D4D, 0x707070
water = rgb == WATER
foliage = rgb == FOLIAGE


def opening(h, r):
    """Grey-scale opening: removes bumps narrower than 2r+1 (houses, trees) and keeps the land's shape."""
    pad = np.pad(h, r, mode="edge")
    eroded = np.lib.stride_tricks.sliding_window_view(pad, (2 * r + 1, 2 * r + 1)).min(axis=(2, 3))
    pad = np.pad(eroded, r, mode="edge")
    return np.lib.stride_tricks.sliding_window_view(pad, (2 * r + 1, 2 * r + 1)).max(axis=(2, 3))


land = opening(np.where(water, floor, surface), 7)
land = np.where(water, floor, np.minimum(land, surface))
raised = surface - land
building = (raised >= 2) & ~foliage & ~water
road = (raised < 2) & np.isin(rgb, [STONE, DIRT, 0x999999, 0x4C4C4C, 0x87826B]) & ~water
rel = float(np.median(land) - Y0) * VSCALE

# ---------------------------------------------------------------- meshes


def down(a, c):
    """c x c cells: heights take the cell's maximum, masks any member, colours the first."""
    h, w = a.shape[:2]
    a = a[: h // c * c, : w // c * c]
    if a.dtype == bool:
        return a.reshape(h // c, c, w // c, c).any(axis=(1, 3))
    if a.ndim == 3:
        return a[::c, ::c]
    return a.reshape(h // c, c, w // c, c).max(axis=(1, 3))


def columns(name, top, bottom, mask, col, z_off, image_uv=None):
    """Block columns: a top face per cell, walls down to the neighbour (or to the bottom at edges and holes)."""
    H, B, M = down(top, CELL), down(bottom, CELL), down(mask, CELL)
    C = down(np.broadcast_to(col, top.shape + (3,)).copy(), CELL).astype(np.float32) / 255.0
    rows, cols = H.shape
    s = float(CELL)
    verts, faces, fcol = [], [], []

    def quad(p, c):
        base = len(verts)
        verts.extend(p)
        faces.append((base, base + 1, base + 2, base + 3))
        fcol.append(c)

    Z = lambda v: (v - Y0) * VSCALE + z_off
    for j in range(rows):
        for i in range(cols):
            if not M[j, i]:
                continue
            h, b = Z(H[j, i]), Z(B[j, i])
            x, y = -HALF + i * s, HALF - j * s         # Minecraft +z (south) is Blender -y
            c = C[j, i]
            quad([(x, y, h), (x + s, y, h), (x + s, y - s, h), (x, y - s, h)], c)
            shade = c * 0.7
            for dj, di, edge in ((0, -1, "w"), (0, 1, "e"), (-1, 0, "n"), (1, 0, "s")):
                jj, ii = j + dj, i + di
                inside = 0 <= jj < rows and 0 <= ii < cols and M[jj, ii]
                lo = max(Z(H[jj, ii]), b) if inside else b
                if lo >= h:
                    continue
                if edge == "w":
                    quad([(x, y - s, lo), (x, y, lo), (x, y, h), (x, y - s, h)], shade)
                elif edge == "e":
                    quad([(x + s, y, lo), (x + s, y - s, lo), (x + s, y - s, h), (x + s, y, h)], shade)
                elif edge == "n":
                    quad([(x, y, lo), (x + s, y, lo), (x + s, y, h), (x, y, h)], shade)
                else:
                    quad([(x + s, y - s, lo), (x, y - s, lo), (x, y - s, h), (x + s, y - s, h)], shade)

    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    attr = mesh.color_attributes.new("Col", "FLOAT_COLOR", "CORNER")
    data = np.repeat(np.array([(c[0], c[1], c[2], 1.0) for c in fcol], np.float32), 4, axis=0)
    attr.data.foreach_set("color", data.ravel())
    if image_uv is not None:
        uv = mesh.uv_layers.new(name="UV")
        uvs = image_uv(np.array(verts, np.float64), z_off)
        idx = np.zeros(len(mesh.loops), np.int32)
        mesh.loops.foreach_get("vertex_index", idx)
        uv.data.foreach_set("uv", uvs[idx].ravel())
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def still_uv(verts, z_off):
    """Where each point lands in the top-down still: the frame laid over the land it shows."""
    cx, cy, cz, yaw, pitch, fov, w, h = CAM
    R = np.array(Matrix.Rotation(math.pi - math.radians(yaw), 3, 'Y') @ Matrix.Rotation(-math.radians(pitch), 3, 'X'))
    mc = np.stack([CX + verts[:, 0], Y0 + (verts[:, 2] - z_off) / VSCALE, CZ - verts[:, 1]], axis=1)
    c = (mc - np.array([cx, cy, cz])) @ R
    th = math.tan(math.radians(fov) / 2)
    nx = c[:, 0] / (-c[:, 2]) / (th * w / h)
    ny = c[:, 1] / (-c[:, 2]) / th
    return np.stack([(nx + 1) / 2, (ny + 1) / 2], axis=1).astype(np.float32)


# ---------------------------------------------------------------- materials
def vertex_material(name, rough=0.85, emit=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    a = nt.nodes.new("ShaderNodeVertexColor")
    a.layer_name = "Col"
    nt.links.new(a.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = rough
    if emit:
        nt.links.new(a.outputs["Color"], bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = emit
    return m


def image_material(name, path):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = bpy.data.images.load(path)
    t.interpolation = "Closest"
    t.extension = "EXTEND"
    nt.links.new(t.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(t.outputs["Color"], bsdf.inputs["Emission Color"])
    bsdf.inputs["Emission Strength"].default_value = 0.45
    bsdf.inputs["Roughness"].default_value = 0.9
    return m


def flat_material(name, rgb, alpha=1.0, emit=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (*rgb, 1)
    bsdf.inputs["Alpha"].default_value = alpha
    bsdf.inputs["Roughness"].default_value = 0.6
    if emit:
        bsdf.inputs["Emission Color"].default_value = (*rgb, 1)
        bsdf.inputs["Emission Strength"].default_value = emit
    return m


def assign(obj, mat):
    obj.data.materials.append(mat)


# ---------------------------------------------------------------- layers
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)

grass = np.array([0x5E, 0x8C, 0x34])
land_col = np.where(((building | foliage | road) & ~water)[..., None], grass, colour)
land_col = np.where(water[..., None], np.array([0x6E, 0x5A, 0x44]), land_col)        # riverbed
slab = land.min() - 10
layers = []

land_col = np.where((rgb == 0x7FB238)[..., None], grass, land_col)
L0 = columns("Land (Expanse)", land, np.full_like(land, slab), np.ones_like(water), land_col, 0)
assign(L0, vertex_material("land"))
layers.append((0, "VOID Expanse", "the land"))

wl = np.where(water, surface, land)
L1 = columns("Water (Expanse)", wl, wl - 1.5, water, np.array([0x3F, 0x76, 0xE4]), GAP)
assign(L1, vertex_material("water", rough=0.2, emit=0.15))
layers.append((GAP, "VOID Expanse", "rivers and lakes"))

L2 = columns("Woods (Expanse)", np.where(foliage, surface, land), land, foliage, np.array([0x2F, 0x6B, 0x22]), 2 * GAP)
assign(L2, vertex_material("woods"))
layers.append((2 * GAP, "VOID Expanse", "woods"))

town_top = np.where(building, surface, np.where(road, land + 0.8, land))
town_col = np.where(road[..., None], np.array([0xB8, 0xB0, 0x9C]), colour)
L3 = columns("Town (Hearth)", town_top, land, building | road, town_col, 3 * GAP)
assign(L3, vertex_material("town"))
layers.append((3 * GAP, "VOID Hearth", "towns and roads"))

L5 = columns("View (LOD + BSL)", surface, land - 2, np.ones_like(water), colour, 5 * GAP, image_uv=still_uv)
assign(L5, image_material("view", STILL))
layers.append((5 * GAP, "What you see", "VOID LOD + BSL"))

# Atlas: a glass plate with the town's name and a bearing to the next town
z_names = 4 * GAP + rel
bpy.ops.mesh.primitive_plane_add(size=2 * HALF, location=(0, 0, z_names))
plate = bpy.context.active_object
plate.name = "Names (Atlas)"
# the Atlas layer is the map Atlas draws from the same blocks: their map colours, a little muted
img = bpy.data.images.new("atlas map", 2 * HALF, 2 * HALF, alpha=True)
px = colour.astype(np.float32) / 255.0
px = px * 0.82 + 0.1
rgba = np.concatenate([px, np.full(px.shape[:2] + (1,), 0.92, np.float32)], axis=2)
img.pixels.foreach_set(rgba[::-1].ravel())
mm = bpy.data.materials.new("atlas")
mm.use_nodes = True
bsdf = mm.node_tree.nodes["Principled BSDF"]
tex = mm.node_tree.nodes.new("ShaderNodeTexImage")
tex.image = img
tex.interpolation = "Closest"
mm.node_tree.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
mm.node_tree.links.new(tex.outputs["Color"], bsdf.inputs["Emission Color"])
bsdf.inputs["Emission Strength"].default_value = 0.35
bsdf.inputs["Roughness"].default_value = 0.7
assign(plate, mm)
layers.append((4 * GAP, "VOID Atlas", "names and discovery"))

font = bpy.data.fonts.load("C:/Windows/Fonts/seguisb.ttf")
FACE = math.radians(45)      # the camera looks north-west, so text reads along north-east


def text(body, loc, size, rgb=(0.95, 0.95, 0.93), emit=1.4, standing=True, align="LEFT"):
    bpy.ops.object.text_add(location=loc, rotation=(math.radians(90) if standing else 0, 0, FACE))
    t = bpy.context.active_object
    t.data.body = body
    t.data.font = font
    t.data.size = size
    t.data.align_x = align
    t.data.align_y = "CENTER"
    t.data.extrude = 0.2
    assign(t, flat_material("txt " + body, rgb, emit=emit))
    return t


text(TOWN, (8, 8, z_names + 14.0), 15, standing=True, align="CENTER")
bpy.ops.mesh.primitive_cone_add(vertices=24, radius1=3.2, radius2=0, depth=9, location=(0, 0, z_names + 4.5), rotation=(math.pi, 0, 0))
assign(bpy.context.active_object, flat_material("pin", (0.95, 0.95, 0.93), emit=1.2))
far_dx, far_dz = float(FAR[1]) - CX, float(FAR[2]) - CZ
dist = math.hypot(far_dx, far_dz)
ang = math.atan2(-far_dz, far_dx)
edge = HALF * 0.78
text("%s  %d m" % (FAR[0], round(dist, -1)), (edge * math.cos(ang) + 2, edge * math.sin(ang) + 12, z_names + 1.0), 7,
     rgb=(0.75, 0.8, 0.95), emit=0.9, standing=False, align="CENTER")
bpy.ops.mesh.primitive_cone_add(vertices=3, radius1=4, depth=0.4, location=(edge * math.cos(ang), edge * math.sin(ang), z_names + 1),
                                rotation=(0, 0, ang - math.pi / 2))
assign(bpy.context.active_object, flat_material("arrow", (0.75, 0.8, 0.95), emit=0.9))

# labels beyond each layer's north-east corner, which is the right of the frame
for z, title, sub in layers:
    zz = z + rel + 6
    at = HALF + 34
    text(title, (at, at, zz + 8), 15)
    text(sub, (at, at, zz - 10), 10.5, rgb=(0.62, 0.65, 0.7), emit=0.8)
    # a leader from the layer's corner to its label
    a, b = Vector((HALF + 2, HALF + 2, z + rel)), Vector((at - 4, at - 4, zz))
    bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=0.35, depth=(b - a).length, location=(a + b) / 2)
    line = bpy.context.active_object
    line.rotation_euler = (b - a).to_track_quat("Z", "Y").to_euler()
    assign(line, flat_material("leader", (0.5, 0.53, 0.58), emit=0.6))

# ---------------------------------------------------------------- camera, light, render
cam_data = bpy.data.cameras.new("cam")
cam_data.lens = 50
cam_data.clip_end = 8000
cam = bpy.data.objects.new("cam", cam_data)
bpy.context.scene.collection.objects.link(cam)
target = Vector((HALF * 0.32, HALF * 0.32, 2.5 * GAP + rel + 4))
el, az, dist_cam = math.radians(31), math.radians(-45), 1400
# each layer is lit on its own: the upper ones cast no shadow on the ones below
for obj in (L3, L5, plate):
    obj.visible_shadow = False
cam.location = target + dist_cam * Vector((math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)))
cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
bpy.context.scene.camera = cam

sun_data = bpy.data.lights.new("sun", "SUN")
sun_data.energy = 3.2
sun_data.angle = math.radians(4)
sun_data.color = (1.0, 0.96, 0.9)
sun = bpy.data.objects.new("sun", sun_data)
sun.rotation_euler = (math.radians(48), 0, math.radians(-35))
bpy.context.scene.collection.objects.link(sun)

world = bpy.data.worlds.new("stage")
world.use_nodes = True
bg = world.node_tree.nodes["Background"]
bg.inputs["Color"].default_value = (0.012, 0.014, 0.017, 1)
bg.inputs["Strength"].default_value = 1.0
bpy.context.scene.world = world

sc = bpy.context.scene
sc.render.engine = "CYCLES"
prefs = bpy.context.preferences.addons["cycles"].preferences
for kind in ("OPTIX", "CUDA"):
    try:
        prefs.compute_device_type = kind
        prefs.get_devices()
        if any(d.type == kind for d in prefs.devices):
            for d in prefs.devices:
                d.use = d.type == kind
            sc.cycles.device = "GPU"
            break
    except TypeError:
        continue
sc.cycles.samples = SAMPLES
sc.cycles.use_denoising = True
sc.render.resolution_x, sc.render.resolution_y = 1920, 1080
sc.render.filepath = OUT
for transform in ("AgX", "Filmic"):
    try:
        sc.view_settings.view_transform = transform
        break
    except TypeError:
        continue
bpy.ops.render.render(write_still=True)
print("[layers] wrote", OUT, "engine", sc.render.engine, "device", sc.cycles.device)
