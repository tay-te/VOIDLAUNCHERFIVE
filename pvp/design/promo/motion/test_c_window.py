"""TEST C — ONE WINDOW. Play, Mods and Setup float as a receding stack; they slide into one
window, and the window then changes screen by cell-grid wipes — every cell steps to the raised
fill for a beat before it turns over, on a diagonal 22 ms stagger. Hard cuts, cell by cell.

  Blender -b --factory-startup --python test_c_window.py -- OUTDIR [PCT] [anim|stills] [OUT.blend]
"""
import bpy, sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmg as V
from mathutils import Vector, Matrix

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUTDIR = argv[0] if argv else os.path.join(V.HERE, "out", "test_c")
PCT = int(argv[1]) if len(argv) > 1 else 50
MODE = argv[2] if len(argv) > 2 else "anim"
BLEND = argv[3] if len(argv) > 3 else None

SECONDS = 5.0
sc = V.reset(SECONDS, pct=PCT, samples=32)
G = V.coll("Window")
WIDTH = 16.0
HEIGHT = WIDTH * 980 / 1600
CORNER = 36
SHOTS = ["play", "mods", "setup"]
PATHS = {k: os.path.join(V.ASSETS, f"shell_{k}@2x.png") for k in SHOTS}

# ---------------------------------------------------------------- the stack (one plane per screen)
stack = {}
for k in SHOTS:
    o, m = V.image_plane(f"Stack_{k}", PATHS[k], WIDTH, G, corner_px=CORNER)
    V.setp(m, "surface_render_method", "DITHERED")
    stack[k] = o
OFF = {"play": Vector((0, 0, 0)), "mods": Vector((3.1, 2.2, -4.2)), "setup": Vector((6.2, 4.4, -8.4))}

# ---------------------------------------------------------------- the window: one plane, three screens, cell wipes
GX, GY = 40, 24.5            # cells across / down (80 px at 2x)
T_W1, T_W2 = 2.55, 3.75      # wipe starts
STEP = 0.075                 # the raised-fill beat before a cell turns over
m = bpy.data.materials.new("WindowMat")
nb = V.NB(m)
T = nb.value("T")
uv = nb.uv()
tex = {}
for k in SHOTS:
    img = bpy.data.images.load(PATHS[k], check_existing=True)
    n = nb.node("ShaderNodeTexImage", image=img, interpolation="Cubic")
    nb._in(n.inputs["Vector"], uv)
    tex[k] = n.outputs["Color"]
s = nb.sep(uv)
cx = nb.math("FLOOR", nb.math("MULTIPLY", s[0], GX))
cy = nb.math("FLOOR", nb.math("MULTIPLY", s[1], GY))
lu = nb.math("FRACT", nb.math("MULTIPLY", s[0], GX))
lv = nb.math("FRACT", nb.math("MULTIPLY", s[1], GY))
cell = nb.roundbox_mask(nb.comb(lu, lv))
d1 = nb.math("ADD", T_W1, nb.math("MULTIPLY", nb.math("ADD", cx, nb.math("MULTIPLY", nb.math("SUBTRACT", GY, cy), 0.5)), 0.022))
d2 = nb.math("ADD", T_W2, nb.math("MULTIPLY", nb.math("ADD", nb.math("SUBTRACT", GX - 1, cx), nb.math("MULTIPLY", cy, 0.5)), 0.022))
s1 = nb.math("SUBTRACT", T, d1)
s2 = nb.math("SUBTRACT", T, d2)
raised_cell = nb.mix_rgb(nb.rgb(V.C["shell"]), nb.rgb(V.C["raised"]), cell)
col = tex["play"]
col = nb.mix_rgb(col, raised_cell, nb.math("GREATER_THAN", s1, 0.0))
col = nb.mix_rgb(col, tex["mods"], nb.math("GREATER_THAN", s1, STEP))
col = nb.mix_rgb(col, raised_cell, nb.math("GREATER_THAN", s2, 0.0))
col = nb.mix_rgb(col, tex["setup"], nb.math("GREATER_THAN", s2, STEP))
# rounded window corners in pixel space
W2, H2 = 3200, 1960
px = nb.math("MULTIPLY", s[0], float(W2))
py = nb.math("MULTIPLY", s[1], float(H2))
qx = nb.math("SUBTRACT", nb.math("ABSOLUTE", nb.math("SUBTRACT", px, W2 / 2)), W2 / 2 - CORNER)
qy = nb.math("SUBTRACT", nb.math("ABSOLUTE", nb.math("SUBTRACT", py, H2 / 2)), H2 / 2 - CORNER)
outside = nb.vmath("LENGTH", nb.comb(nb.math("MAXIMUM", qx, 0.0), nb.math("MAXIMUM", qy, 0.0)), out=1)
inside = nb.math("MINIMUM", nb.math("MAXIMUM", qx, qy), 0.0)
dd = nb.math("SUBTRACT", nb.math("ADD", outside, inside), float(CORNER))
alpha = nb.math("SUBTRACT", 1.0, nb.math("ADD", dd, 0.5), clamp=True)
nb.emit_alpha(col, 1.0, alpha)
V.setp(m, "surface_render_method", "DITHERED")
V.key_time_value(T, SECONDS)
window = V.obj("Window", V.plane_mesh("Window", WIDTH, HEIGHT), m, G)

T_M0, T_M1 = 1.35, 2.35      # the stack slides into one window
for f in V.frames():
    t = V.t_of(f)
    for i, k in enumerate(SHOTS):
        u = V.ease_io((t - T_M0 - (2 - i) * 0.10) / (T_M1 - T_M0 - 0.2))
        stack[k].location = OFF[k] * (1 - u) + Vector((0, 0, -0.03 * i))
        stack[k].keyframe_insert("location", frame=f)
    merged = t >= T_M1 + 0.05
    for k in SHOTS:
        stack[k].hide_render = merged
        stack[k].keyframe_insert("hide_render", frame=f)
    window.hide_render = not merged
    window.keyframe_insert("hide_render", frame=f)

# ---------------------------------------------------------------- camera: truck across the stack, settle front-on
cam = V.camera("Cam", G, lens=45.0, clip_start=2.0, clip_end=200.0)
for f in V.frames():
    t = V.t_of(f)
    u = V.ease_io(t / 2.8)
    yaw = math.radians(-30.0 + 30.0 * u) + math.radians(6.0) * (1 - u) * math.sin(t * 1.3)
    pitch = math.radians(-9.0 * (1 - u))
    dist = 35.0 + (29.2 - 35.0) * u - max(0.0, t - 2.8) * 0.45
    look = Vector((3.6 * (1 - u), 2.3 * (1 - u), -4.0 * (1 - u)))
    cam.location = look + dist * Vector((math.sin(yaw) * math.cos(pitch), math.sin(pitch), math.cos(yaw) * math.cos(pitch)))
    V.look_at(cam, look, up=(0.0, 1.0, 0.0))
    cam.keyframe_insert("location", frame=f)
    cam.keyframe_insert("rotation_quaternion", frame=f)

for o in list(stack.values()) + [window]:
    ad = o.animation_data
    if ad and ad.action:
        V.set_linear(o, "CONSTANT")
for o in stack.values():
    pass

if BLEND:
    V.save(BLEND)
if MODE == "stills":
    V.render_frames(OUTDIR, stills=[1, 70, 130, 160, 175, 210, 245, 300])
else:
    V.render_frames(OUTDIR)
