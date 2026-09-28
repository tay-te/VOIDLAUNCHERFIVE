"""S2 LAUNCH — the cursor opens the loadout dropdown, picks Bedwars (the title swaps), then presses
Launch; the press sends a ripple of light through the cell field.

  Blender -b --factory-startup --python shot_s2_launch.py -- OUTDIR [PCT] [SAMPLES] [anim|stills] [BLEND]
"""
import bpy, sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmg as V, film as F, figma_build as FB
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else os.path.join(V.HERE, "out", "s2")
PCT = int(argv[1]) if len(argv) > 1 else 50
SAMPLES = int(argv[2]) if len(argv) > 2 else 24
MODE = argv[3] if len(argv) > 3 else "anim"
BLEND = argv[4] if len(argv) > 4 else None

SECONDS = 5.5
sc = F.setup(SECONDS, pct=PCT, samples=SAMPLES)
G = V.coll("Screen")
L = V.coll("Lights")
scr = F.screen("play", G)
F.light_rig(L)
O = scr.objs
MM = F.MM


def cells(w, h, parent=0):
    for c in scr.cell_objs:
        g = c["group"]
        if abs(g["w"] - w) < 0.01 and abs(g["h"] - h) < 0.01 and g["parent"] == parent:
            return c
    raise KeyError((w, h, parent))


def grp(i):
    return O[i]["group"]


def twin_text(ob, body):
    """a second text object with new copy, same place/style, hidden until swapped in."""
    new = ob.copy()
    new.data = ob.data.copy()
    new.data.body = body
    ob.users_collection[0].objects.link(new)
    new.parent = ob.parent
    new.matrix_parent_inverse = ob.matrix_parent_inverse.copy()
    new.location = ob.location.copy()
    return new


kc = grp(71)                       # search keycap belongs to the header, not the launch button
mw = kc.matrix_world.copy()
kc.parent = scr.root
kc.matrix_world = mw

# ---------------------------------------------------------------- cursor path (screen px), eased
cur = grp(77)
CX, CY = 760, 846                  # cursor's resting place in the file


def cursor_to(t0, t1, p0, p1, curve="CUBIC", easing="EASE_IN_OUT"):
    F.move(cur, "location", 0, t0, (p0[0] - CX) * MM, t1, (p1[0] - CX) * MM, curve, easing)
    F.move(cur, "location", 1, t0, -(p0[1] - CY) * MM, t1, -(p1[1] - CY) * MM, curve, easing)


P_START = (1010, 990)
P_LOAD = (478, 850)
P_BED = (470, 686)
P_LAUNCH = (792, 850)
cursor_to(0.00, 1.05, P_START, P_LOAD)
cursor_to(1.55, 1.95, P_LOAD, P_BED)
cursor_to(2.75, 3.55, P_BED, P_LAUNCH)

# click feedback: the cursor dips a hair on each click
for tc in (1.12, 2.22, 3.90):
    F.press(cur, tc, depth=0.6, down=0.06, up=0.12)

# ---------------------------------------------------------------- dropdown / loadout opens, Bedwars picked
T_OPEN, T_PICK, T_CLOSE = 1.18, 2.24, 2.40
dd = O[24]
hover_objs = set(F.subtree(O[29]))
F.show([ob for ob in F.subtree(dd) if ob not in hover_objs], T_OPEN, visible=True)
dg = dd["group"]
F.move(dg, "location", 2, T_OPEN, -3.0 * MM, T_OPEN + 0.16, 0.0, "QUART", "EASE_OUT")
F.move(dg, "location", 1, T_OPEN, -14 * MM, T_OPEN + 0.16, 0.0, "QUART", "EASE_OUT")
chev = grp(22)                                   # the chevron flips while open
F.move(chev, "rotation_euler", 2, T_OPEN, 0.0, T_OPEN + 0.14, math.pi, "CUBIC", "EASE_OUT")
F.move(chev, "rotation_euler", 2, T_CLOSE, math.pi, T_CLOSE + 0.10, 0.0, "CUBIC", "EASE_IN")
# hover lives on Bedwars already; it appears when the pointer arrives
hover = grp(29)
F.show(F.subtree(O[29]), 1.90, visible=True)          # hidden until the pointer reaches Bedwars
# the pick: selection fill and the live dot move from Sword PvP to Bedwars, names swap weight
row_sw, row_bw = O[26]["slab"], O[30]["slab"]
F.recolor(row_sw, T_PICK, FB.hexlin("191a1c"), dur=F.ARRIVE)
F.light(row_bw, T_PICK, 1.0, dur=F.ARRIVE)
F.recolor(row_bw, T_PICK, FB.hexlin("212225"), dur=0.001)
dot = cells(7, 7, parent=26)["objs"][0]
F.move(dot, "location", 1, T_PICK, dot.location.y, T_PICK + 0.2, dot.location.y - 38 * MM, "CUBIC", "EASE_OUT")
F.recolor(O[27]["text"], T_PICK, FB.hexlin("9a9da1"))
F.recolor(O[31]["text"], T_PICK, FB.hexlin("edeeef"))
# close: sink and slide away, then gone
F.move(dg, "location", 2, T_CLOSE, 0.0, T_CLOSE + 0.10, -3.0 * MM, "CUBIC", "EASE_IN")
F.move(dg, "location", 1, T_CLOSE, 0.0, T_CLOSE + 0.10, -10 * MM, "CUBIC", "EASE_IN")
F.show(F.subtree(dd), T_CLOSE + 0.11, visible=False)
for ob in hover_objs:                                  # the close must not re-show the hover before 1.9 s
    for path in ("hide_render", "hide_viewport"):
        fc = F._fcurve(ob, path, 0)
        if fc:
            first = min(kp.co.x for kp in fc.keyframe_points)
            for kp in fc.keyframe_points:
                if kp.co.x == first:
                    kp.co.y = 1.0

# ---------------------------------------------------------------- the copy swaps to the new loadout
T_SWAP = 2.46
title = O[9]["text"]
title_bw = twin_text(title, "Bedwars")
z = title.location.z
F.move(title, "location", 2, T_SWAP, z, T_SWAP + 0.16, z - 3.5 * MM, "CUBIC", "EASE_IN")
F.show([title], T_SWAP + 0.17, visible=False)
F.show([title_bw], 0.0, visible=False)
F.show([title_bw], T_SWAP + 0.17, visible=True)
F.move(title_bw, "location", 2, T_SWAP + 0.17, z - 3.5 * MM, T_SWAP + 0.62, z, "QUART", "EASE_OUT")
sub = O[10]["text"]
sub_bw = twin_text(sub, "12 mods on   ·   142 fps avg   ·   12 ms to Hypixel")
F.show([sub_bw], 0.0, visible=False)
F.show([sub], T_SWAP + 0.26, visible=False)
F.show([sub_bw], T_SWAP + 0.26, visible=True)
dock_val = O[13]["text"]
dock_bw = twin_text(dock_val, "Bedwars")
F.show([dock_bw], 0.0, visible=False)
F.show([dock_val], T_PICK + 0.05, visible=False)
F.show([dock_bw], T_PICK + 0.05, visible=True)

# ---------------------------------------------------------------- Launch: hover lift, then a real press
T_HOVER, T_PRESS = 3.42, 3.90
launch = [grp(67)] + cells(56, 22)["objs"]
for ob in launch:
    z = ob.location.z
    F.key(ob, "location", 2, F.f_of(T_HOVER), z, "CUBIC", "EASE_OUT")
    F.key(ob, "location", 2, F.f_of(T_HOVER + 0.14), z + 1.2 * MM, "CONSTANT")
    F.key(ob, "location", 2, F.f_of(T_PRESS), z + 1.2 * MM, "CUBIC", "EASE_OUT")
    F.key(ob, "location", 2, F.f_of(T_PRESS + 0.10), z - 2.2 * MM, "CUBIC", "EASE_OUT")
    F.key(ob, "location", 2, F.f_of(T_PRESS + 0.28), z + 0.4 * MM, "CUBIC", "EASE_IN_OUT")
    F.key(ob, "location", 2, F.f_of(T_PRESS + 0.60), z, "CONSTANT")

# the press sends a wave of light out through the field: each cell steps up, then drains
BX, BY = 809, 846
for c in (cells(10, 10), cells(44, 44)):
    for ob, (x, y, a) in zip(c["objs"], c["group"]["pts"]):
        d = math.hypot(x + c["group"]["w"] / 2 - BX, y + c["group"]["h"] / 2 - BY)
        t = T_PRESS + 0.06 + d / 1500.0
        peak = min(0.55, a * 7.0 + 0.08)
        F.key(ob, "color", 3, F.f_of(t), a, "CUBIC", "EASE_OUT")
        F.key(ob, "color", 3, F.f_of(t + 0.07), peak, "CUBIC", "EASE_IN")
        F.key(ob, "color", 3, F.f_of(t + 0.07 + F.DRAIN * 2.2), a, "CONSTANT")

# ---------------------------------------------------------------- camera
cam = V.camera("Cam", G, lens=50.0, clip_start=0.02, clip_end=40.0)
beats = [
    dict(t=0.0, pos=(-0.64, -0.66, 0.24), tgt=(-0.30, 0.0, 0.17), lens=50.0, fstop=3.2, focus=(-0.33, 0.0, 0.14)),
    dict(t=1.3, pos=(-0.66, -0.74, 0.38), tgt=(-0.28, 0.0, 0.25), lens=50.0, fstop=3.5, focus=(-0.30, 0.0, 0.26)),
    dict(t=2.55, pos=(-0.62, -0.98, 0.52), tgt=(-0.34, 0.0, 0.34), lens=48.0, fstop=4.0, focus=(-0.52, 0.0, 0.36)),
    dict(t=3.55, pos=(-0.24, -0.64, 0.26), tgt=(0.0, 0.0, 0.15), lens=55.0, fstop=3.2, focus=(0.0, 0.0, 0.135)),
    dict(t=4.2, pos=(-0.15, -0.52, 0.21), tgt=(0.02, 0.0, 0.15), lens=58.0, fstop=3.2, focus=(0.0, 0.0, 0.135)),
    dict(t=5.5, pos=(-0.34, -1.02, 0.44), tgt=(0.12, 0.0, 0.32), lens=50.0, fstop=4.5, focus=(0.0, 0.0, 0.16)),
]
F.fly(cam, beats, SECONDS, omega=3.6)

if BLEND:
    V.save(BLEND)
if MODE == "stills":
    F.render(OUT, stills=[1, 60, 80, 120, 140, 160, 190, 240, 262, 290, 330])
else:
    F.render(OUT)
