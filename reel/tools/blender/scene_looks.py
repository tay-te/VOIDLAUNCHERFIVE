"""
Stylized looks over a reel.sh export, one script, a look per run:

  storybook  warm low sun against a cool sky, light haze, tilt-shift miniature (the first liked look)
  bluehour   deep blue evening, every lantern a real warm light, cool haze
  mist       morning fog lying in the streets, roofs and towers standing out of it, low sun through it
  island     an export cut as an island (reel.sh export cut=island) floating on a dark stage, lit like a studio model

    blender -b --factory-startup -P tools/blender/scene_looks.py -- EXPORT_DIR OUT.png --look bluehour
        [--cam x,y,z --at x,y,z]   Minecraft coordinates (defaults: the Bradhurst diorama view)
"""
import math
import os
import sys

import bpy
import numpy as np
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import reel_camera as rc  # noqa: E402
import reel_world  # noqa: E402
import voxkit as vk  # noqa: E402

argv = sys.argv[sys.argv.index("--") + 1:]
EXPORT, OUT = argv[0], argv[1]
opt = {argv[i].lstrip("-"): argv[i + 1] for i in range(2, len(argv) - 1, 2)}
LOOK = opt.get("look", "storybook")
vec = lambda s: tuple(float(v) for v in s.split(","))

# sun: (elevation, colour, strength, angle); sky: (colour, strength); lantern: watts as at full size
# haze: "falloff" thins with height above the water; "layer" is a fog bank with a soft top at a world height
LOOKS = {
    "storybook": dict(scale=0.02, sun=(17, (1.0, 0.8, 0.55), 5.5, 1.5), sky=((0.36, 0.5, 0.75), 1.1),
                      haze=dict(kind="falloff", density=0.0025, colour=(1.0, 0.9, 0.78), falloff=-0.025),
                      lantern=0, exposure=0.0, fstop=2.0, lens=55, look="AgX - Punchy"),
    "bluehour": dict(scale=0.02, sun=(26, (0.55, 0.68, 1.0), 0.45, 6), sky=((0.04, 0.08, 0.24), 1.25),
                     haze=dict(kind="falloff", density=0.006, colour=(0.5, 0.6, 0.9), falloff=-0.05),
                     lantern=30, exposure=1.1, fstop=2.0, lens=55, look="AgX - Medium High Contrast"),
    "mist": dict(scale=0.02, sun=(8, (1.0, 0.76, 0.5), 5.5, 3), sky=((0.46, 0.58, 0.8), 1.15),
                 haze=dict(kind="layer", density=0.05, colour=(1.0, 0.97, 0.94), top=99, soft=4, above=0.004, anisotropy=0.3),
                 cam="-3655,113,-3500", at="-3795,109,-3612",
                 lantern=0, exposure=0.45, fstop=2.8, lens=55, look="AgX - Medium Low Contrast"),
    "island": dict(scale=0.005, sun=None, sky=((0.012, 0.014, 0.02), 1.0), haze=None,
                   lantern=12, exposure=0.6, fstop=2.8, lens=70, look="AgX - Punchy"),
}
L = LOOKS[LOOK]
S = L["scale"]

vk.clear()
world_obj = reel_world.load(EXPORT, glow=10.0 if LOOK in ("bluehour", "island") else 5.0, style="toon")
origin = tuple(bpy.context.scene["reel_origin"])
world_obj.scale = (S, S, S)
sc = bpy.context.scene
me = world_obj.data
mat_index = np.zeros(len(me.polygons), np.int32)
me.polygons.foreach_get("material_index", mat_index)
centres = np.zeros(len(me.polygons) * 3, np.float32)
me.polygons.foreach_get("center", centres)
# the OBJ importer keeps the mesh Y-up and turns the object instead: bring the centres into Blender's axes
centres = centres.reshape(-1, 3) @ np.array(world_obj.rotation_euler.to_matrix(), np.float32).T


def faces_of(test):
    """Centres (object space, a block a unit) of the faces whose material name passes the test."""
    want = [i for i, m in enumerate(me.materials) if m and test(m.name.split(".")[0])]
    return centres[np.isin(mat_index, want)]


# ---------------------------------------------------------------- camera
if LOOK == "island":
    # three-quarter, a little above, far enough back for the whole island and some dark around it
    lo, hi = centres.min(axis=0), centres.max(axis=0)
    centre = Vector(((lo + hi) / 2).tolist()) * S
    size = Vector((hi - lo).tolist()) * S
    el, az = math.radians(17), math.radians(-48)
    vfov = 2 * math.atan(36 * 9 / 16 / 2 / L["lens"])
    dist = 1.3 * max(size.z, max(size.x, size.y) * 9 / 16) / (2 * math.tan(vfov / 2))
    cam_b = centre + dist * Vector((math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)))
    look_b = centre
else:
    cam_mc = vec(opt.get("cam", L.get("cam", "-3688,146,-3512")))
    look_mc = vec(opt.get("at", L.get("at", "-3778,98,-3598")))
    cam_b = rc.mc_to_blender(*cam_mc, origin) * S
    look_b = rc.mc_to_blender(*look_mc, origin) * S
cam = vk.camera(cam_b, look_b, lens=L["lens"], clip=4000 * S, dof=((look_b - cam_b).length, L["fstop"]))
cam.data.clip_start = 0.05 * S
if LOOK == "island":
    # frame what the camera actually sees of it: centre the silhouette with lens shift, then move in until it
    # fills about two thirds of the height
    sample = [Vector(c.tolist()) * S for c in centres[::25]]
    for _ in range(3):
        bpy.context.view_layer.update()
        ndc = np.array([tuple(world_to_camera_view(sc, cam, v))[:2] for v in sample])
        (x0, y0), (x1, y1) = ndc.min(axis=0), ndc.max(axis=0)
        cam.data.shift_x += (x0 + x1) / 2 - 0.5
        cam.data.shift_y += ((y0 + y1) / 2 - 0.5) * 9 / 16
        grow = max((y1 - y0) / 0.68, (x1 - x0) / 0.7)
        cam.location = look_b + (cam.location - look_b) * grow
        cam.data.shift_x *= 1 / grow
        cam.data.shift_y *= 1 / grow
    cam.data.dof.focus_distance = (look_b - cam.location).length
view = (look_b - cam_b).normalized()

# ---------------------------------------------------------------- light
if L["sun"]:
    elev, colour, energy, angle = L["sun"]
    flat_view = Vector((view.x, view.y, 0)).normalized()
    # light from behind the subject and to one side: rim and long shadows toward the camera
    side = Vector((-flat_view.y, flat_view.x, 0))
    towards = (flat_view * 0.7 + side * 0.7).normalized()
    travel = Vector((-towards.x, -towards.y, -math.tan(math.radians(elev)))).normalized()
    sd = bpy.data.lights.new("sun", "SUN")
    sd.energy, sd.color, sd.angle = energy, colour, math.radians(angle)
    sun = bpy.data.objects.new("sun", sd)
    sun.rotation_euler = travel.to_track_quat("-Z", "Y").to_euler()
    sc.collection.objects.link(sun)
else:
    # a studio around the island's middle: warm key above-left, cool rim from behind, faint fill from the camera side
    reach = 3.2 * max(size.x, size.y)
    for name, d, light_size, colour, power in (("key", Vector((-0.55, -0.6, 0.6)), 1.6, (1.0, 0.86, 0.7), 260),
                                               ("rim", Vector((-0.45, 0.8, 0.35)), 1.2, (0.6, 0.72, 1.0), 320),
                                               ("fill", Vector((0.4, -0.85, 0.25)), 2.0, (0.9, 0.92, 1.0), 30)):
        ld = bpy.data.lights.new(name, "AREA")
        ld.shape, ld.size, ld.color, ld.energy = "DISK", light_size, colour, power
        lo_ = bpy.data.objects.new(name, ld)
        lo_.location = centre + d.normalized() * reach
        lo_.rotation_euler = (centre - lo_.location).to_track_quat("-Z", "Y").to_euler()
        sc.collection.objects.link(lo_)

w = bpy.data.worlds.new("sky")
w.use_nodes = True
bg = w.node_tree.nodes["Background"]
bg.inputs["Color"].default_value = (*L["sky"][0], 1)
bg.inputs["Strength"].default_value = L["sky"][1]
sc.world = w

# every lantern a small warm light, one per lantern block
if L["lantern"]:
    blocks = {tuple(np.floor(c).astype(int)) for c in faces_of(lambda n: "lantern" in n and "sea" not in n)}
    for b in blocks:
        ld = bpy.data.lights.new("lantern", "POINT")
        # watts as at full size: a model S times smaller needs S^2 the power for the same light at S times the distance
        ld.energy = L["lantern"] * S * S
        ld.color = (1.0, 0.6, 0.28)
        ld.shadow_soft_size = 0.25 * S
        lo_ = bpy.data.objects.new("lantern", ld)
        lo_.location = (Vector(b) + Vector((0.5, 0.5, 0.4))) * S
        sc.collection.objects.link(lo_)
    print("[looks] %d lantern lights" % len(blocks))

# ---------------------------------------------------------------- haze / mist
H = L["haze"]
if H:
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 60 * S))
    vol = bpy.context.active_object
    vol.scale = (440 * S, 440 * S, 170 * S)
    m = bpy.data.materials.new("haze")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    outn = nt.nodes.new("ShaderNodeOutputMaterial")
    pv = nt.nodes.new("ShaderNodeVolumePrincipled")
    pv.inputs["Color"].default_value = (*H["colour"], 1)
    pv.inputs["Anisotropy"].default_value = H.get("anisotropy", 0.5)
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(geo.outputs["Position"], sep.inputs["Vector"])
    if H["kind"] == "layer":
        # full density up to the soft top, then a trace of it above for air
        band = nt.nodes.new("ShaderNodeMapRange")
        band.interpolation_type = "SMOOTHSTEP"
        band.clamp = True
        band.inputs["From Min"].default_value = (H["top"] - H["soft"] - origin[1]) * S
        band.inputs["From Max"].default_value = (H["top"] + H["soft"] - origin[1]) * S
        band.inputs["To Min"].default_value = H["density"] / S
        band.inputs["To Max"].default_value = H["density"] * H.get("above", 0.03) / S
        nt.links.new(sep.outputs["Z"], band.inputs["Value"])
        density = band.outputs["Result"]
    else:
        # thinning with height above the water: the river's surface is the valley floor
        zs = faces_of(lambda n: n == "water")[:, 2]
        floor = float(np.median(zs)) if len(zs) else 16.0
        print("[looks] valley floor y %.1f" % (floor + origin[1]))
        shift = nt.nodes.new("ShaderNodeMath")
        shift.operation = "SUBTRACT"
        shift.inputs[1].default_value = floor * S
        nt.links.new(sep.outputs["Z"], shift.inputs[0])
        fall = nt.nodes.new("ShaderNodeMath")
        fall.operation = "MULTIPLY"
        fall.inputs[1].default_value = H["falloff"] / S
        nt.links.new(shift.outputs["Value"], fall.inputs[0])
        ex = nt.nodes.new("ShaderNodeMath")
        ex.operation = "EXPONENT"
        nt.links.new(fall.outputs["Value"], ex.inputs[0])
        clamp = nt.nodes.new("ShaderNodeMath")
        clamp.operation = "MINIMUM"
        clamp.inputs[1].default_value = 1.0
        nt.links.new(ex.outputs["Value"], clamp.inputs[0])
        dens = nt.nodes.new("ShaderNodeMath")
        dens.operation = "MULTIPLY"
        dens.inputs[1].default_value = H["density"] / S
        nt.links.new(clamp.outputs["Value"], dens.inputs[0])
        density = dens.outputs["Value"]
    nt.links.new(density, pv.inputs["Density"])
    nt.links.new(pv.outputs["Volume"], outn.inputs["Volume"])
    vol.data.materials.append(m)
    sc.cycles.volume_step_rate = 4.0
    # light scattered more than once is what makes thick fog glow rather than sit grey
    sc.cycles.volume_bounces = 4
    sc.cycles.volume_max_steps = 256

for look in (L["look"], "AgX - Punchy", "Medium High Contrast"):
    try:
        sc.view_settings.look = look
        break
    except TypeError:
        continue
sc.view_settings.exposure = L["exposure"]
vk.render(OUT, samples=192)
