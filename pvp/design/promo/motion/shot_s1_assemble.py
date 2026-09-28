"""S1 ASSEMBLE — the Play screen builds itself out of its own surfaces.

  Blender -b --factory-startup --python shot_s1_assemble.py -- OUTDIR [PCT] [SAMPLES] [anim|stills] [BLEND]
"""
import bpy, sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmg as V, film as F
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else os.path.join(V.HERE, "out", "s1")
PCT = int(argv[1]) if len(argv) > 1 else 50
SAMPLES = int(argv[2]) if len(argv) > 2 else 32
MODE = argv[3] if len(argv) > 3 else "anim"
BLEND = argv[4] if len(argv) > 4 else None

SECONDS = 5.0
sc = F.setup(SECONDS, pct=PCT, samples=SAMPLES)
G = V.coll("Screen")
L = V.coll("Lights")
scr = F.screen("play", G)
F.light_rig(L)
O = scr.objs


def cells(w, h, parent=0):
    for c in scr.cell_objs:
        g = c["group"]
        if abs(g["w"] - w) < 0.01 and abs(g["h"] - h) < 0.01 and g["parent"] == parent:
            return c
    raise KeyError((w, h, parent))


def grp(i):
    return O[i]["group"]


def arrive_cells(objs, t, depth=None, dur=0.3):
    for ob in objs:
        F.arrive_cell(ob, t, dur=dur)


# ---- the search keycap lives inside the launch-button frame in the file; give it back to the header
kc = grp(71)
mw = kc.matrix_world.copy()
kc.parent = scr.root
kc.matrix_world = mw

# ---- stage out of the shell
F.arrive_node(scr, 5, 0.25, dur=0.6)

# ---- header: the small mark lights clockwise, wordmark, nav, search, profile
logo = cells(4, 4)["objs"]
for i, ob in enumerate(logo):
    F.light(ob, 0.32 + i * 0.022, ob.color[3], dur=0.12)
F.arrive_node(scr, 1, 0.62, dur=0.3)
pill = cells(58, 30)["objs"][0]
F.light(pill, 0.80, pill.color[3], dur=0.14)
for k, i in enumerate((51, 52, 53, 54, 55)):
    F.arrive_node(scr, i, 0.72 + k * F.STAGGER, dur=0.28)
for i in (2, 3, 4, 71):
    F.arrive_node(scr, i, 0.92, dur=0.34)
F.arrive_node(scr, 73, 1.00, dur=0.34)

# ---- the field: a column sweep, left to right, each cell stepping up to its token alpha
field = cells(10, 10)
for ob, (x, y, a) in zip(field["objs"], field["group"]["pts"]):
    t = 0.62 + (x - 48) / 1484 * 1.55 + (y - 96) / 728 * 0.12
    F.light(ob, t, a, dur=0.16)

# ---- the big mark in the stage: clockwise from the top, 40 ms apart, rising a touch
mark = cells(44, 44)
cx, cy = 1182 + 22, 372 + 22
order = sorted(range(len(mark["objs"])), key=lambda j: (math.atan2(mark["group"]["pts"][j][0] + 22 - cx,
                                                                  -(mark["group"]["pts"][j][1] + 22 - cy)) % (2 * math.pi)))
for k, j in enumerate(order):
    ob = mark["objs"][j]
    t = 1.72 + k * F.STAGGER
    F.light(ob, t, mark["group"]["pts"][j][2], dur=0.18)
    arrive_cells([ob], t, depth=2.0, dur=0.34)

# ---- hero copy
green = cells(8, 8)["objs"]            # chip dot + online dot
F.arrive_node(scr, 6, 1.22, dur=0.34)
F.arrive_node(scr, 7, 1.30, dur=0.3)
F.light(green[0], 1.34, green[0].color[3], dur=0.14)
F.arrive_node(scr, 8, 1.45, dur=0.3)
F.arrive_node(scr, 9, 1.55, dur=0.5)
F.arrive_node(scr, 10, 1.78, dur=0.3)

# ---- the dock lifts, its contents follow left to right, the Launch button pops last
T_DOCK = 2.15
F.arrive_node(scr, 11, T_DOCK, dur=0.5)
dock_items = [(12, 394), (13, 394), (22, 518), (14, 586), (15, 586), (23, 654), (16, 978), (17, 1004), (18, 1030),
              (19, 1098), (20, 1162)]
for i, x in dock_items:
    F.arrive_node(scr, i, T_DOCK + 0.14 + (x - 394) / 800 * 0.36, dur=0.34)
for name, (w, h) in (("dividers", (5, 5)), ("favdots", (12, 12)), ("gear", (3, 3))):
    c = cells(w, h)
    for ob, (x, y, a) in zip(c["objs"], c["group"]["pts"]):
        arrive_cells([ob], T_DOCK + 0.14 + (x - 394) / 800 * 0.36, depth=5.0, dur=0.34)
arrive_cells([green[1]], T_DOCK + 0.14 + (1080 - 394) / 800 * 0.36, depth=5.0, dur=0.34)
T_LAUNCH = 2.62
F.arrive_node(scr, 67, T_LAUNCH, dur=0.42)
arrive_cells(cells(56, 22)["objs"], T_LAUNCH, depth=8.0, dur=0.42)       # ENTER keycap rides with the button
F.arrive_node(scr, 21, 2.95, dur=0.3)

# ---- no cursor in this shot
F.show(F.subtree(O[77]), 0.0, visible=False)

# ---- camera: grazing macro across the stage -> three-quarter hero
cam = V.camera("Cam", G, lens=40.0, clip_start=0.02, clip_end=40.0)
beats = [
    dict(t=0.0, pos=(-1.10, -0.62, 0.30), tgt=(-0.40, 0.0, 0.42), lens=35.0, fstop=4.0, focus=(-0.58, 0.0, 0.40)),
    dict(t=2.1, pos=(-0.98, -1.10, 0.52), tgt=(-0.22, 0.0, 0.42), lens=40.0, fstop=4.5, focus=(-0.52, 0.0, 0.36)),
    dict(t=3.6, pos=(-0.80, -1.70, 0.68), tgt=(-0.10, 0.0, 0.42), lens=45.0, fstop=5.0, focus=(-0.20, 0.0, 0.25)),
    dict(t=5.0, pos=(-0.66, -2.12, 0.76), tgt=(-0.04, 0.0, 0.45), lens=46.0, fstop=5.6, focus=(-0.12, 0.0, 0.25)),
]
F.fly(cam, beats, SECONDS, omega=3.2)

if BLEND:
    V.save(BLEND)
if MODE == "stills":
    F.render(OUT, stills=[1, 40, 80, 120, 150, 180, 240, 300])
else:
    F.render(OUT)
