"""
Cinematic concept: Bradhurst across the river at golden hour, from the east bank. The real blocks (a reel.sh export),
the sun low behind the town, haze in the air so the light comes through the trees in shafts, a 50 mm lens focused on
the town.

    blender -b --factory-startup -P tools/blender/cinematic_bradhurst.py -- EXPORT_DIR OUT.png [--variant gold|mist|night]
"""
import math
import os
import sys

import bpy
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import reel_camera as rc  # noqa: E402
import reel_world  # noqa: E402
import voxkit as vk  # noqa: E402

argv = sys.argv[sys.argv.index("--") + 1:]
EXPORT, OUT = argv[0], argv[1]
VARIANT = argv[argv.index("--variant") + 1] if "--variant" in argv else "gold"
STYLE = argv[argv.index("--style") + 1] if "--style" in argv else "real"
SHOT = argv[argv.index("--shot") + 1] if "--shot" in argv else "bank"

vk.clear()
world_obj = reel_world.load(EXPORT, glow=8.0 if VARIANT == "night" else 5.0, style=STYLE)
origin = tuple(bpy.context.scene["reel_origin"])

# the camera: on the east bank, low over the water, looking across at the town
if SHOT == "diorama":
    # higher, three-quarter over the river to the town, with tilt-shift focus: a hand-built miniature
    cam_mc, look_mc, lens, fstop = (-3688.0, 146.0, -3512.0), (-3778.0, 98.0, -3598.0), 55, 2.0
else:
    cam_mc, look_mc, lens, fstop = (-3652.0, 87.5, -3527.0), (-3790.0, 103.0, -3604.0), 50, 4.0
# a diorama is shot at model scale (a block is 2 cm), so the lens blurs it the way it blurs a real miniature
S = 0.02 if SHOT == "diorama" else 1.0
world_obj.scale = (S, S, S)
cam_b = rc.mc_to_blender(*cam_mc, origin) * S
look_b = rc.mc_to_blender(*look_mc, origin) * S
dist = (look_b - cam_b).length
vk.camera(cam_b, look_b, lens=lens, clip=2000 * S, dof=(dist, fstop))
bpy.context.scene.camera.data.clip_start = 0.05 * S

# light: the sun low behind the town (west-north-west of the camera), and haze for the shafts
view = (look_b - cam_b).normalized()
LIGHT = {
    "gold": dict(elev=7, color=(1.0, 0.72, 0.42), energy=4.5, sky=(0.32, 0.36, 0.45), sky_strength=0.9, haze=0.010, haze_col=(1.0, 0.86, 0.7)),
    "storybook": dict(elev=17, color=(1.0, 0.8, 0.55), energy=5.5, sky=(0.36, 0.5, 0.75), sky_strength=1.1, haze=0.0025, haze_col=(1.0, 0.9, 0.78)),
    "mist": dict(elev=14, color=(1.0, 0.9, 0.78), energy=2.6, sky=(0.55, 0.62, 0.7), sky_strength=1.2, haze=0.022, haze_col=(0.9, 0.93, 0.96)),
    "night": dict(elev=22, color=(0.55, 0.65, 1.0), energy=0.25, sky=(0.02, 0.03, 0.06), sky_strength=0.6, haze=0.008, haze_col=(0.5, 0.6, 0.85)),
}[VARIANT]
sun_dir = Vector((view.x, view.y, 0)).normalized()
travel = Vector((-sun_dir.x, -sun_dir.y, -math.tan(math.radians(LIGHT["elev"])))).normalized()
sd = bpy.data.lights.new("sun", "SUN")
sd.energy = LIGHT["energy"]
sd.color = LIGHT["color"]
sd.angle = math.radians(1.5)
sun = bpy.data.objects.new("sun", sd)
sun.rotation_euler = travel.to_track_quat("-Z", "Y").to_euler()
bpy.context.scene.collection.objects.link(sun)

w = bpy.data.worlds.new("sky")
w.use_nodes = True
bg = w.node_tree.nodes["Background"]
bg.inputs["Color"].default_value = (*LIGHT["sky"], 1)
bg.inputs["Strength"].default_value = LIGHT["sky_strength"]
bpy.context.scene.world = w

# haze: a volume over the whole export, thinning with height
bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 60 * S))
vol = bpy.context.active_object
vol.name = "haze"
vol.scale = (420 * S, 420 * S, 160 * S)
m = bpy.data.materials.new("haze")
m.use_nodes = True
nt = m.node_tree
nt.nodes.clear()
outn = nt.nodes.new("ShaderNodeOutputMaterial")
pv = nt.nodes.new("ShaderNodeVolumePrincipled")
pv.inputs["Color"].default_value = (*LIGHT["haze_col"], 1)
pv.inputs["Anisotropy"].default_value = 0.55
geo = nt.nodes.new("ShaderNodeNewGeometry")
sep = nt.nodes.new("ShaderNodeSeparateXYZ")
nt.links.new(geo.outputs["Position"], sep.inputs["Vector"])
fall = nt.nodes.new("ShaderNodeMath")
fall.operation = "MULTIPLY"
fall.inputs[1].default_value = -0.025 / S
nt.links.new(sep.outputs["Z"], fall.inputs[0])
ex = nt.nodes.new("ShaderNodeMath")
ex.operation = "EXPONENT"
nt.links.new(fall.outputs["Value"], ex.inputs[0])
dens = nt.nodes.new("ShaderNodeMath")
dens.operation = "MULTIPLY"
dens.inputs[1].default_value = LIGHT["haze"] / S
nt.links.new(ex.outputs["Value"], dens.inputs[0])
nt.links.new(dens.outputs["Value"], pv.inputs["Density"])
nt.links.new(pv.outputs["Volume"], outn.inputs["Volume"])
vol.data.materials.append(m)

sc = bpy.context.scene
if STYLE == "toon":
    for look in ("AgX - Punchy", "AgX - Medium High Contrast", "Medium High Contrast"):
        try:
            sc.view_settings.look = look
            break
        except TypeError:
            continue
sc.cycles.volume_step_rate = 4.0
sc.cycles.volume_max_steps = 256
vk.render(OUT, samples=160)
