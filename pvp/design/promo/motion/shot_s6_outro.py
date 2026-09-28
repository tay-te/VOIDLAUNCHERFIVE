"""S6 OUTRO — the three rebuilt screens as one receding stack (camera drifts across them), then a
hard cut to the end card: the mark builds cell by cell, the wordmark and URL rise.

  Blender -b --factory-startup --python shot_s6_outro.py -- OUTDIR [PCT] [SAMPLES] [anim|stills] [BLEND]
"""
import bpy, sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmg as V, film as F, figma_build as FB
from mathutils import Vector, Matrix

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else os.path.join(V.HERE, "out", "s6")
PCT = int(argv[1]) if len(argv) > 1 else 50
SAMPLES = int(argv[2]) if len(argv) > 2 else 24
MODE = argv[3] if len(argv) > 3 else "anim"
BLEND = argv[4] if len(argv) > 4 else None

SECONDS = 6.0
T_CUT = 3.2
sc = F.setup(SECONDS, pct=PCT, samples=SAMPLES)
MM = F.MM

# ================================================================ part 1: the stack
G = V.coll("Stack")
L = V.coll("Lights")
screens = {}
for i, (which, off) in enumerate((("setup", (1.10, 1.25, 0.22)), ("mods", (0.55, 0.62, 0.11)), ("play", (0.0, 0.0, 0.0)))):
    s = F.screen(which, G, loc=(off[0], off[1], 0.49 + off[2]))
    kc = s.objs.get(71) if which == "play" else None
    if which == "play":
        F.show(F.subtree(s.objs[77]), 0.0, visible=False)       # no cursor in the stack
    screens[which] = s
F.light_rig(L, center=(0.55, 0.62, 0.60), scale=1.5)

# ================================================================ part 2: the end card (far away, own light)
E = V.coll("EndCard")
EC = Vector((0.0, 40.0, 0.0))          # the card lives far from the stack; the camera cuts to it
mat_cell = FB.ui_material("solid", 0.72, 0.30, spec=0.55, coat=0.35)
P = 0.050                              # lattice pitch 50 mm, cell 43 mm
MARK_C = EC + Vector((-0.30, 0.0, 0.06))
cells = []
for k, (lx, ly) in enumerate(V.MARK_CELLS):
    cu = FB.slab_curve(f"Mark{k}", 43, 43, 43 * 0.30, 9.0, 2.2)
    ob = bpy.data.objects.new(f"Mark{k}", cu)
    E.objects.link(ob)
    cu.materials.append(mat_cell)
    ob.location = MARK_C + Vector(((lx - 3) * P, 0.0, (3 - ly) * P))
    ob.rotation_euler = (math.radians(90), 0, 0)
    ob.color = (*FB.hexlin("edeeef"), 1.0)
    cells.append(ob)
font_l = bpy.data.fonts.load(os.path.join(V.HERE, "fonts", "outfit-300.ttf"), check_existing=True)
font_m = bpy.data.fonts.load(os.path.join(V.HERE, "fonts", "outfit-500.ttf"), check_existing=True)
mat_txt = FB.ui_material("solid", 0.85, 0.40)


def word(body, font, size, loc, color="edeeef", spacing=1.0, thick=4.0, align="LEFT"):
    cu = bpy.data.curves.new(body, "FONT")
    cu.body = body
    cu.font = font
    cu.size = size
    cu.space_character = spacing
    cu.extrude = thick / 2 * MM
    cu.align_x = align
    cu.materials.append(mat_txt)
    ob = bpy.data.objects.new(body, cu)
    E.objects.link(ob)
    ob.location = loc
    ob.rotation_euler = (math.radians(90), 0, 0)
    ob.color = (*FB.hexlin(color), 1.0)
    return ob


def text_width(body, font, size, spacing):
    tmp = word(body, font, size, Vector((0, 0, 0)), spacing=spacing)
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    me = tmp.evaluated_get(dg).to_mesh()
    w = max(v.co.x for v in me.vertices) if len(me.vertices) else 0.0
    tmp.evaluated_get(dg).to_mesh_clear()
    bpy.data.objects.remove(tmp)
    return w


WORD_SIZE, WORD_SP = 0.30, 1.28
cap = 0.694 * WORD_SIZE
x0 = MARK_C.x + 3 * P + 0.0215 + 0.085
base_z = MARK_C.z - cap / 2
letters = []
for k, ch in enumerate("VOID"):
    dx = text_width("VOID"[:k] + "|", font_l, WORD_SIZE, WORD_SP) - text_width("|", font_l, WORD_SIZE, WORD_SP) if k else 0.0
    letters.append(word(ch, font_l, WORD_SIZE, Vector((x0 + dx, EC.y, base_z)), spacing=WORD_SP, thick=5.0))
word_w = text_width("VOID", font_l, WORD_SIZE, WORD_SP)
LOCK_C = ((MARK_C.x - 3 * P - 0.0215) + (x0 + word_w)) / 2
url = word("void.gg", font_l, 0.052, Vector((LOCK_C, EC.y, MARK_C.z - 0.29)), color="9a9da1", thick=2.0, align="CENTER")
foot = word("MINECRAFT 1.8.9   ·   MAC & WINDOWS   ·   FREE", font_m, 0.019, Vector((LOCK_C, EC.y, MARK_C.z - 0.345)),
            color="626569", spacing=1.25, thick=1.2, align="CENTER")

# end-card light: a soft key from top-left, a top strip for the cell edges
for name, power, off, size, sy in (("E_Key", 45, (-0.9, -1.2, 1.0), 1.2, 0.8), ("E_Top", 35, (0.1, 0.3, 1.1), 2.0, 0.1),
                                   ("E_Fill", 6, (1.2, -1.0, 0.1), 1.2, 0.8)):
    ld = bpy.data.lights.new(name, "AREA")
    ld.energy = power
    ld.shape = "RECTANGLE"
    ld.size, ld.size_y = size, sy
    o = bpy.data.objects.new(name, ld)
    E.objects.link(o)
    o.location = EC + Vector(off)
    V.look_at(o, EC + Vector((0.05, 0, 0.0)))

# ---- end-card animation: black beat, mark clockwise (40 ms, 0.8 -> 1.0, 140 ms), wordmark, URL, footer
T_MARK = T_CUT + 0.28
for k, ob in enumerate(cells):
    t = T_MARK + k * F.STAGGER
    F.show([ob], t, visible=True)
    y0 = ob.location.y
    F.move(ob, "location", 1, t, y0 + 0.012, t + 0.22, y0, "QUART", "EASE_OUT")
    for i in range(3):
        F.move(ob, "scale", i, t, 0.8, t + F.ARRIVE, 1.0, "CUBIC", "EASE_OUT")
T_WORD = T_MARK + 16 * F.STAGGER + 0.12
for k, ob in enumerate(letters):
    t = T_WORD + k * F.STAGGER * 1.5
    F.show([ob], t, visible=True)
    z0 = ob.location.z
    F.move(ob, "location", 2, t, z0 - 0.018, t + 0.30, z0, "QUART", "EASE_OUT")
for ob, t in ((url, T_WORD + 0.42), (foot, T_WORD + 0.62)):
    F.show([ob], t, visible=True)
    z0 = ob.location.z
    F.move(ob, "location", 2, t, z0 - 0.008, t + 0.26, z0, "QUART", "EASE_OUT")

# ================================================================ cameras + cut
cam1 = V.camera("Cam_Stack", G, lens=40.0, clip_start=0.05, clip_end=60.0)
beats = [
    dict(t=0.0, pos=(-1.75, -2.35, 1.30), tgt=(0.50, 0.55, 0.62), lens=38.0, fstop=6.3, focus=(0.30, 0.30, 0.55)),
    dict(t=1.6, pos=(-1.30, -2.80, 1.18), tgt=(0.55, 0.60, 0.62), lens=40.0, fstop=6.3, focus=(0.35, 0.35, 0.56)),
    dict(t=3.3, pos=(-0.80, -3.15, 1.08), tgt=(0.62, 0.62, 0.62), lens=42.0, fstop=7.1, focus=(0.40, 0.40, 0.57)),
]
F.fly(cam1, beats, T_CUT + 0.1, omega=3.0)
cam2 = V.camera("Cam_End", E, lens=50.0, clip_start=0.05, clip_end=60.0)
CEN = Vector((LOCK_C, EC.y, MARK_C.z - 0.07))
beats2 = [dict(t=0.0, pos=tuple(CEN + Vector((0.0, -2.35, 0.0))), tgt=tuple(CEN), lens=50.0, fstop=8.0, focus=tuple(CEN)),
          dict(t=SECONDS, pos=tuple(CEN + Vector((0.0, -2.26, 0.0))), tgt=tuple(CEN), lens=50.0, fstop=8.0, focus=tuple(CEN))]
F.fly(cam2, beats2, SECONDS, omega=4.0, shake=0.0002)
sc.timeline_markers.new("stack", frame=1).camera = cam1
sc.timeline_markers.new("end", frame=int(F.f_of(T_CUT))).camera = cam2
sc.camera = cam1
# end-card items start hidden
F.show(cells + letters + [url, foot], 0.0, visible=False)
for ob in cells + letters + [url, foot]:
    pass

if BLEND:
    V.save(BLEND)
if MODE == "stills":
    F.render(OUT, stills=[1, 90, 180, 200, 230, 250, 280, 330, 360])
else:
    F.render(OUT)
