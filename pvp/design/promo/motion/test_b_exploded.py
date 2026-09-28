"""TEST B — EXPLODED. The Play screen as its five fill-step sheets (shell, ground, card, raised,
ink) pulled apart in depth; the camera swings from an oblique three-quarter to dead front while
the sheets collapse, back to front, into the exact launcher screen. Board 11, in real 3D.

  Blender -b --factory-startup --python test_b_exploded.py -- OUTDIR [PCT] [anim|stills] [OUT.blend]
"""
import bpy, sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmg as V
from mathutils import Vector, Matrix

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUTDIR = argv[0] if argv else os.path.join(V.HERE, "out", "test_b")
PCT = int(argv[1]) if len(argv) > 1 else 50
MODE = argv[2] if len(argv) > 2 else "anim"
BLEND = argv[3] if len(argv) > 3 else None

SECONDS = 5.0
sc = V.reset(SECONDS, pct=PCT, samples=32)
G = V.coll("Sheets")
LAY = os.path.join(V.ASSETS, "layers")
NAMES = ["play_0_shell", "play_1_ground", "play_2_card", "play_3_raised", "play_4_ink"]
WIDTH = 16.0
HEIGHT = WIDTH * 980 / 1600
SPACING = 2.1

sheets = []
for k, n in enumerate(NAMES):
    o, m = V.image_plane(f"Sheet{k}", os.path.join(LAY, n + ".png"), WIDTH, G)
    V.setp(m, "surface_render_method", "DITHERED")
    sheets.append(o)

# sheet outlines + corner rails: the construction lines of the exploded view, only while apart
line_m = V.flat_mat("LineMat", V.C["text"], 0.16)
rail_m = V.flat_mat("RailMat", V.C["text"], 0.10)
outlines = []
for k in range(len(NAMES)):
    t = 0.012
    boxes = [(0, HEIGHT / 2, 0, WIDTH, t, t), (0, -HEIGHT / 2, 0, WIDTH, t, t),
             (WIDTH / 2, 0, 0, t, HEIGHT, t), (-WIDTH / 2, 0, 0, t, HEIGHT, t)]
    import bmesh
    bm = bmesh.new()
    for cx, cy, cz, sx, sy, sz in boxes:
        r = bmesh.ops.create_cube(bm, size=1.0)
        for v in r["verts"]:
            v.co = Vector((cx + v.co.x * sx, cy + v.co.y * sy, cz + v.co.z * sz))
    me = bpy.data.meshes.new(f"Outline{k}")
    bm.to_mesh(me)
    bm.free()
    outlines.append(V.obj(f"Outline{k}", me, line_m, G))
rails = []
for cx, cy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
    bm = bmesh.new()
    r = bmesh.ops.create_cube(bm, size=1.0)
    for v in r["verts"]:
        v.co = Vector((cx * WIDTH / 2 + v.co.x * 0.01, cy * HEIGHT / 2 + v.co.y * 0.01, (v.co.z + 0.5)))
    me = bpy.data.meshes.new("Rail")
    bm.to_mesh(me)
    bm.free()
    rails.append(V.obj("Rail", me, rail_m, G))

line_a = line_m.node_tree.nodes["Alpha"].outputs[0]
rail_a = rail_m.node_tree.nodes["Alpha"].outputs[0]

cam = V.camera("Cam", G, lens=50.0, clip_start=2.0, clip_end=200.0)
CENTER = Vector((0.0, 0.0, 0.0))
T0, T1 = 1.15, 3.95                      # collapse window


def explode(t, k):
    # back sheets close first, the ink lands last: 90 ms apart per sheet
    u = (t - T0 - (len(NAMES) - 1 - k) * 0.09) / (T1 - T0 - 0.36)
    return 1.0 - V.ease_io(u)


for f in V.frames():
    t = V.t_of(f)
    for k, o in enumerate(sheets):
        z = k * SPACING * explode(t, k) + k * 0.02
        o.location = (0.0, 0.0, z)
        o.keyframe_insert("location", frame=f)
        outlines[k].location = (0.0, 0.0, z + 0.01)
        outlines[k].keyframe_insert("location", frame=f)
    e = explode(t, 2)
    depth = sheets[-1].location.z
    for r in rails:
        r.scale = (1.0, 1.0, max(depth, 0.0001))
        r.keyframe_insert("scale", frame=f)
    line_a.default_value = 0.16 * min(1.0, e * 1.6)
    line_a.keyframe_insert("default_value", frame=f)
    rail_a.default_value = 0.10 * min(1.0, e * 1.6)
    rail_a.keyframe_insert("default_value", frame=f)
    # camera: oblique three-quarter swinging to dead front, then a 3 % push on the hold
    u = V.ease_io((t - 0.0) / 4.2)
    yaw = math.radians(-40.0 * (1 - u))
    pitch = math.radians(22.0 * (1 - u))
    dist = 38.0 + (26.4 - 38.0) * u - max(0.0, t - 4.2) * 0.9
    look = CENTER + Vector((0.4 * (1 - u), -0.2 * (1 - u), SPACING * 2.0 * (1 - u)))
    cam.location = look + dist * Vector((math.sin(yaw) * math.cos(pitch), -math.sin(pitch) * 0.0 + math.sin(pitch),
                                         math.cos(yaw) * math.cos(pitch)))
    V.look_at(cam, look, up=(0.0, 1.0, 0.0))
    cam.keyframe_insert("location", frame=f)
    cam.keyframe_insert("rotation_quaternion", frame=f)

if BLEND:
    V.save(BLEND)
if MODE == "stills":
    V.render_frames(OUTDIR, stills=[1, 60, 120, 170, 220, 300])
else:
    V.render_frames(OUTDIR)
