"""S1b TITLE CARD — "Nothing you didn't ask for." Four words set as extruded type on black, each rising
140 ms ease-out, 90 ms apart, then held. The one line in the film (board 02).

  Blender -b --factory-startup --python shot_s1b_title.py -- OUTDIR [PCT] [SAMPLES] [anim|stills] [BLEND]
"""
import bpy, sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmg as V, film as F, figma_build as FB
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else os.path.join(V.HERE, "out", "s1b")
PCT = int(argv[1]) if len(argv) > 1 else 50
SAMPLES = int(argv[2]) if len(argv) > 2 else 24
MODE = argv[3] if len(argv) > 3 else "anim"
BLEND = argv[4] if len(argv) > 4 else None

SECONDS = 3.2
sc = F.setup(SECONDS, pct=PCT, samples=SAMPLES)
G = V.coll("Title")
MM = F.MM
font = bpy.data.fonts.load(os.path.join(V.HERE, "fonts", "outfit-300.ttf"), check_existing=True)
mat = FB.ui_material("solid", 0.86, 0.40)
SIZE = 0.094
WORDS = ["Nothing", "you", "didn’t", "ask", "for."]


def make(body, x):
    cu = bpy.data.curves.new(body, "FONT")
    cu.body = body
    cu.font = font
    cu.size = SIZE
    cu.extrude = 3.0 * MM
    cu.materials.append(mat)
    ob = bpy.data.objects.new(body, cu)
    G.objects.link(ob)
    ob.location = (x, 0.0, 0.0)
    ob.rotation_euler = (math.radians(90), 0, 0)
    ob.color = (*FB.hexlin("edeeef"), 1.0)
    return ob


def width(body):
    ob = make(body, 0.0)
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    me = ob.evaluated_get(dg).to_mesh()
    w = max(v.co.x for v in me.vertices) if len(me.vertices) else 0.0
    ob.evaluated_get(dg).to_mesh_clear()
    bpy.data.objects.remove(ob)
    return w


space = width("n n") - 2 * width("n")
xs, x = [], 0.0
for w_ in WORDS:
    xs.append(x)
    x += width(w_) + space
total = x - space
objs = [make(w_, xs[i] - total / 2) for i, w_ in enumerate(WORDS)]
cap = 0.694 * SIZE
for ob in objs:
    ob.location.z = -cap / 2

T0 = 0.45
for k, ob in enumerate(objs):
    t = T0 + k * 0.09
    F.show([ob], t, visible=True)
    F.move(ob, "location", 2, t, ob.location.z - 0.012, t + F.ARRIVE * 1.6, ob.location.z, "QUART", "EASE_OUT")
F.show(objs, 0.0, visible=False)

L = V.coll("Lights")
F.light_rig(L, center=(0, 0, 0), scale=0.8, key=40, top=40, fill=6)
cam = V.camera("Cam", G, lens=50.0, clip_start=0.05, clip_end=30.0)
beats = [dict(t=0.0, pos=(-0.06, -2.2, 0.03), tgt=(0.0, 0.0, 0.0), lens=50.0, fstop=8.0, focus=(0, 0, 0)),
         dict(t=SECONDS, pos=(-0.02, -2.12, 0.02), tgt=(0.0, 0.0, 0.0), lens=50.0, fstop=8.0, focus=(0, 0, 0))]
F.fly(cam, beats, SECONDS, omega=4.0, shake=0.0002)

if BLEND:
    V.save(BLEND)
if MODE == "stills":
    F.render(OUT, stills=[30, 40, 60, 150])
else:
    F.render(OUT)
