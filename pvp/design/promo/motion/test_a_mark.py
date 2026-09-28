"""TEST A — THE MARK. Fly through a deep cell field; the mark builds cell by cell at the focal
plane (16 cells, 40 ms stagger, 140 ms ease-out, 0.8 -> 1.0), the wordmark wipes in.

  Blender -b --factory-startup --python test_a_mark.py -- OUTDIR [PCT] [anim|stills] [OUT.blend]
"""
import bpy, sys, os, math, random
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmg as V
from mathutils import Vector, Matrix

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUTDIR = argv[0] if argv else os.path.join(V.HERE, "out", "test_a")
PCT = int(argv[1]) if len(argv) > 1 else 50
MODE = argv[2] if len(argv) > 2 else "anim"
BLEND = argv[3] if len(argv) > 3 else None

SECONDS = 5.0
sc = V.reset(SECONDS, pct=PCT, samples=16)
G = V.coll("Field")
rng = random.Random(7)

# ---------------------------------------------------------------- deep field planes (pitch 1)
deep = []
for zi, z in enumerate((18, 24, 30, 36, 44, 52, 62, 74, 88)):
    span_x, span_y = 34 + z * 0.5, 20 + z * 0.3
    for gx in range(int(-span_x), int(span_x) + 1):
        for gy in range(int(-span_y), int(span_y) + 1):
            if rng.random() > 0.34:
                continue
            a = rng.choice((0.03, 0.04, 0.05, 0.07, 0.10, 0.14)) * (1.25 if rng.random() < 0.05 else 1.0)
            deep.append(dict(x=gx * 1.0, y=gy * 1.0, z=float(z), s=0.34, a=a, ph=rng.random() * 6.283,
                             sp=0.6 + rng.random() * 1.4))
me = V.cell_mesh("DeepField", deep, attrs=("a", "ph", "sp"))
m = V.blended(bpy.data.materials.new("DeepFieldMat"))
nb = V.NB(m)
T = nb.value("T")
shimmer = nb.math("ADD", 0.62, nb.math("MULTIPLY", 0.38,
                  nb.math("SINE", nb.math("ADD", nb.attr("ph"), nb.math("MULTIPLY", T, nb.attr("sp"))))))
alpha = nb.math("MULTIPLY", nb.math("MULTIPLY", nb.attr("a"), shimmer), nb.roundbox_mask(nb.uv()))
nb.emit_alpha(nb.rgb(V.C["text"]), 1.0, alpha)
V.key_time_value(T, SECONDS)
V.obj("DeepField", me, m, G)

# ---------------------------------------------------------------- focal plane: lattice p = 0.5
P = 0.5
MARK_C = Vector((-2.9, 0.0))
mark_set = {(gx, gy) for gx, gy in V.MARK_CELLS}
halo = []
for gx in range(-40, 41):
    for gy in range(-24, 25):
        lx = gx - int(round(MARK_C.x / P)) + 3
        ly = 3 - (gy - int(round(MARK_C.y / P)))
        if (lx, ly) in mark_set:
            continue
        x, y = gx * P, gy * P
        d = (Vector((x, y)) - MARK_C).length
        dens = max(0.0, 1.0 - d / 6.0) ** 1.6
        if d < 1.2:               # the enclosed interior stays a true void
            continue
        if rng.random() > 0.35 + 0.6 * dens:
            continue
        halo.append(dict(x=x, y=y, s=0.13 + 0.07 * dens, a=0.035 + 0.30 * dens * rng.uniform(0.5, 1.0),
                         d=d, ph=rng.random() * 6.283))
me = V.cell_mesh("Halo", halo, attrs=("a", "d", "ph"))
m = V.blended(bpy.data.materials.new("HaloMat"))
nb = V.NB(m)
T = nb.value("T")
# density ramp toward centre rises across the shot; the outer field is up from the start
ramp = nb.smooth(1.6, 3.2, T)
inner = nb.math("SUBTRACT", 1.0, nb.math("DIVIDE", nb.attr("d"), 6.0), clamp=True)
k = nb.math("ADD", nb.math("MULTIPLY", nb.math("SUBTRACT", 1.0, inner), 1.0),
            nb.math("MULTIPLY", inner, ramp))
alpha = nb.math("MULTIPLY", nb.math("MULTIPLY", nb.attr("a"), k), nb.roundbox_mask(nb.uv()))
nb.emit_alpha(nb.rgb(V.C["text"]), 1.0, alpha)
V.key_time_value(T, SECONDS)
V.obj("Halo", me, m, G)

# the mark: 16 cells, 40 ms stagger clockwise from the top, 140 ms ease-out, 0.8 -> 1.0
T_MARK = 2.25
marks = []
for i, (lx, ly) in enumerate(V.MARK_CELLS):
    x = MARK_C.x + (lx - 3) * P
    y = MARK_C.y + (3 - ly) * P
    marks.append(dict(x=x, y=y, z=0.002, s=P * 0.84, dl=T_MARK + i * 0.040))
me = V.cell_mesh("Mark", marks, attrs=("dl",))
m = V.blended(bpy.data.materials.new("MarkMat"))
nb = V.NB(m)
T = nb.value("T")
u = nb.math("DIVIDE", nb.math("SUBTRACT", T, nb.attr("dl")), 0.140, clamp=True)
e = nb.math("SUBTRACT", 1.0, nb.math("POWER", nb.math("SUBTRACT", 1.0, u), 3.0))
half = nb.math("MULTIPLY", 0.5, nb.math("ADD", 0.8, nb.math("MULTIPLY", 0.2, e)))
# scaled rounded cell: mask with half-size `half`, radius 30 % of the cell
uvp = nb.vmath("SUBTRACT", nb.uv(), (0.5, 0.5, 0.0))
q = nb.vmath("SUBTRACT", nb.vmath("ABSOLUTE", uvp), nb.comb(nb.math("MULTIPLY", half, 0.4), nb.math("MULTIPLY", half, 0.4)))
outside = nb.vmath("LENGTH", nb.vmath("MAXIMUM", q, (0, 0, 0)), out=1)
s = nb.sep(q)
dd = nb.math("SUBTRACT", nb.math("ADD", outside, nb.math("MINIMUM", nb.math("MAXIMUM", s[0], s[1]), 0.0)),
             nb.math("MULTIPLY", half, 0.6))
mask = nb.math("LESS_THAN", dd, 0.0)
nb.emit_alpha(nb.rgb(V.C["text"]), 1.0, nb.math("MULTIPLY", mask, nb.math("GREATER_THAN", u, 0.0)))
V.key_time_value(T, SECONDS)
V.obj("Mark", me, m, G)

# ---------------------------------------------------------------- wordmark: wipes in left to right
T_WORD = T_MARK + 16 * 0.040 + 0.14 + 0.06
word, wm = V.text("Wordmark", "VOID", 2.05, G, weight=300, spacing=1.32,
                  mtx=Matrix.Translation((MARK_C.x + 2.55, MARK_C.y - 0.74, 0.0)))
nb = V.NB(wm)
tc = nb.node("ShaderNodeTexCoord")
W = nb.value("Wipe", -1.0)
xs = nb.sep(tc.outputs["Object"])[0]
nb.emit_alpha(nb.rgb(V.C["text"]), 1.0, nb.math("LESS_THAN", xs, W))
V.blended(wm)
bpy.context.view_layer.update()
x0, x1 = -0.2, 7.2
for f in V.frames():
    t = V.t_of(f)
    W.default_value = x0 + (x1 - x0) * V.ease_out((t - T_WORD) / 0.42, 3)
    W.keyframe_insert("default_value", frame=f)

eyebrow, em = V.text("Eyebrow", "MINECRAFT PVP CLIENT   ·   1.8.9   ·   MAC & WINDOWS", 0.215, G, weight=400,
                     hexc=V.C["muted"], align="CENTER", spacing=1.35,
                     mtx=Matrix.Translation((MARK_C.x + 4.05, -2.95, 0.0)))
aN = em.node_tree.nodes["Alpha"].outputs[0]
T_EYE = T_WORD + 0.55
for f in V.frames():
    aN.default_value = V.ease_out((V.t_of(f) - T_EYE) / 0.20, 2)
    aN.keyframe_insert("default_value", frame=f)

# ---------------------------------------------------------------- camera: fly in, settle
cam = V.camera("Cam", G, lens=32.0)
target = Vector((MARK_C.x + 4.05, -0.35, 0.0))
for f in V.frames():
    t = V.t_of(f)
    u = V.ease_out(t / 2.9, 4)                           # fast through the field, settling on the focal plane
    z = 90.0 + (12.6 - 90.0) * u
    drift = (1 - u)
    x = target.x + 5.0 * drift * math.sin(1.3 + t * 0.9)
    y = target.y - 2.6 * drift + 0.15 * math.sin(t * 0.7)
    cam.location = (x, y, z)
    look = target + Vector((1.6 * drift, 0.8 * drift, 0.0))
    push = 0.0 if t < 2.9 else (t - 2.9) * 0.10          # a 3 % creep on the hold
    cam.location = (x, y, z - push)
    V.look_at(cam, look, roll_deg=5.0 * drift ** 2, up=(0.0, 1.0, 0.0))
    cam.keyframe_insert("location", frame=f)
    cam.keyframe_insert("rotation_quaternion", frame=f)

if BLEND:
    V.save(BLEND)
if MODE == "stills":
    V.render_frames(OUTDIR, stills=[30, 90, 140, 170, 200, 240, 300])
else:
    V.render_frames(OUTDIR)
