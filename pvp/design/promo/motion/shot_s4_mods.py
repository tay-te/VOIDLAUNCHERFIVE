"""S4 THE MODS GRID — ten live mod cards rise out of the panel on the app's 40 ms column stagger,
each one assembling from the inside out (slab, preview well, live preview, name, toggle). The last
card to arrive, Fullbright, lands switched off; the camera finishes its low dolly on it and the
toggle flips on: knob 15 px in 140 ms, track steps fill at the midpoint, the label lifts, the
preview brightens ring by ring, and the counters step from 11 to 12.

  Blender -b --factory-startup --python shot_s4_mods.py -- OUTDIR [PCT] [SAMPLES] [anim|stills] [BLEND]
"""
import bpy, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmg as V, film as F
from mathutils import Vector, noise

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else os.path.join(V.HERE, "out", "s4")
PCT = int(argv[1]) if len(argv) > 1 else 50
SAMPLES = int(argv[2]) if len(argv) > 2 else 32
MODE = argv[3] if len(argv) > 3 else "anim"
BLEND = argv[4] if len(argv) > 4 else None

SECONDS = 6.0
MM = 0.001
sc = F.setup(SECONDS, pct=PCT, samples=SAMPLES)
G = V.coll("Screen")
L = V.coll("Lights")
scr = F.screen("mods", G)
F.light_rig(L)
O = scr.objs
bpy.context.view_layer.update()

# ---------------------------------------------------------------- timing (seconds)
T_HEAD = 0.06        # grid header labels
T_GRID = 0.12        # first card column leaves the panel
COL = F.STAGGER      # 40 ms column stagger, left to right (the app's)
ROW = 0.10           # row 2 a beat behind
L_PREVIEW = 0.13     # inside each card: well, then its live preview, then name, then the switch
L_CONTENT = 0.26
WIPE = 0.26          # preview contents wipe top-left -> bottom-right over this long
L_NAME = 0.36
L_CAT = 0.40
L_TRACK = 0.44
L_KNOB = 0.50
T_FLIP = 4.30        # Fullbright switches on
FLIP = 0.14          # the product's own 140 ms


# ---------------------------------------------------------------- scene index
def node_box(n):
    return (n["x"], n["y"], n["x"] + n["w"], n["y"] + n["h"])


def inside(cx, cy, box):
    return box[0] <= cx <= box[2] and box[1] <= cy <= box[3]


CELLS = []
for ci, c in enumerate(scr.cell_objs):
    g = c["group"]
    for ob, (x, y, a) in zip(c["objs"], g["pts"]):
        CELLS.append(dict(ob=ob, x=x, y=y, w=g["w"], h=g["h"], a=a, ci=ci, cx=x + g["w"] / 2, cy=y + g["h"] / 2))


def cell_at(x, y, w, h):
    for c in CELLS:
        if abs(c["x"] - x) < 0.05 and abs(c["y"] - y) < 0.05 and abs(c["w"] - w) < 0.01 and abs(c["h"] - h) < 0.01:
            return c
    raise KeyError((x, y, w, h))


def adopt(child, parent):
    """re-parent keeping the world transform, so contents ride inside the container they belong to."""
    bpy.context.view_layer.update()
    mw = child.matrix_world.copy()
    child.parent = parent
    child.matrix_world = mw


def grp(i):
    return O[i]["group"]


def text_ob(i):
    return O[i]["text"]


PANEL_TOP = scr.tops[12]
cards = []
for idx in sorted(O):
    n = O[idx]["node"]
    if n["name"] != "card":
        continue
    box = node_box(n)
    members = [j for j in O if j not in (0, idx) and O[j]["node"]["parent"] == 0
               and inside(O[j]["node"]["x"] + O[j]["node"]["w"] / 2, O[j]["node"]["y"] + O[j]["node"]["h"] / 2, box)]
    prev = [j for j in members if O[j]["node"]["name"] == "preview"][0]
    pbox = node_box(O[prev]["node"])
    pn = O[prev]["node"]
    in_prev = [j for j in members if j != prev and inside(O[j]["node"]["x"] + O[j]["node"]["w"] / 2,
                                                         O[j]["node"]["y"] + O[j]["node"]["h"] / 2, pbox)]
    below = [j for j in members if j != prev and j not in in_prev]
    ccells = [c for c in CELLS if inside(c["cx"], c["cy"], box)]
    pcells = [c for c in ccells if inside(c["cx"], c["cy"], pbox)]
    texts = [j for j in below if O[j]["node"]["type"] == "TEXT"]
    name = [j for j in texts if O[j]["node"]["segs"][0]["size"] > 12][0]
    cat = [j for j in texts if O[j]["node"]["segs"][0]["size"] < 12][0]
    track_node = [j for j in below if O[j]["node"]["name"] == "track"]
    tcells = [c for c in ccells if c not in pcells]
    track = O[track_node[0]]["group"] if track_node else [c for c in tcells if c["w"] == 34][0]["ob"]
    knob = [c for c in tcells if c["w"] == 13][0]
    grips = [c for c in tcells if c["w"] == 2]
    cards.append(dict(idx=idx, box=box, col=round((n["x"] - 276) / 256), row=round((n["y"] - 152) / 342),
                      prev=prev, pbox=pbox, in_prev=in_prev, pcells=pcells, name=name, cat=cat,
                      track=track, track_node=track_node[0] if track_node else None, knob=knob, grips=grips,
                      top=scr.tops[idx], ptop=scr.tops[prev]))
assert len(cards) == 10, len(cards)
FB_CARD = [c for c in cards if c["track_node"] is not None][0]          # Fullbright: the one that is off

# ---- containers: every card's contents ride inside it (well -> preview -> ink; card -> name, switch)
for c in cards:
    cg = grp(c["idx"])
    adopt(grp(c["prev"]), cg)
    for j in c["in_prev"]:
        adopt(grp(j), grp(c["prev"]))
    for pc in c["pcells"]:
        adopt(pc["ob"], grp(c["prev"]))
    adopt(grp(c["name"]), cg)
    adopt(grp(c["cat"]), cg)
    adopt(c["track"], cg)
    adopt(c["knob"]["ob"], c["track"])
    for gc in c["grips"]:
        adopt(gc["ob"], c["knob"]["ob"])
bpy.context.view_layer.update()

# ---- text that fades while it rises uses the alpha twin of its material (same shading, animatable alpha)
def fadeable(ob):
    if ob.data.materials and ob.data.materials[0] is not scr.mat_alpha:
        ob.data.materials[0] = scr.mat_alpha
    return ob


# ---------------------------------------------------------------- plan (depths from real geometry, before any key)
JOBS = []   # (kind, ob, t, args)


def top_mm(ob):
    return F.subtree_top_mm(scr, ob)


def rise(ob, t, host_top, dur=0.34, margin=0.4, curve="QUART"):
    """ob comes up out of the surface it sits in: it starts with its whole subtree `margin` below the
    host's top face (hidden inside an opaque host) and eases out to rest."""
    JOBS.append(("rise", ob, t, dict(depth=top_mm(ob) - host_top + margin, dur=dur, curve=curve)))


def fade(ob, t, alpha, dur=F.ARRIVE, from_alpha=0.0):
    JOBS.append(("fade", ob, t, dict(alpha=alpha, dur=dur, from_alpha=from_alpha)))


def ink_in(i, t, host_top, dur=0.30):
    """a text node: rise out of its host and fade up to its token alpha over 140 ms."""
    ob = fadeable(text_ob(i))
    rise(grp(i), t, host_top, dur=dur, margin=0.4)
    fade(ob, t, ob.color[3], dur=F.ARRIVE)


# grid header labels
ink_in(28, T_HEAD, PANEL_TOP)
ink_in(29, T_HEAD + COL * 4, PANEL_TOP)

for c in cards:
    s = T_GRID + c["col"] * COL + c["row"] * ROW
    rise(grp(c["idx"]), s, PANEL_TOP, dur=0.40)
    rise(grp(c["prev"]), s + L_PREVIEW, c["top"], dur=0.36)
    # live preview: a top-left -> bottom-right wipe inside the well; the one hue (crosshair centre) lands last
    x0, y0 = c["pbox"][0], c["pbox"][1]
    items = [("n", j, O[j]["node"]["x"] + O[j]["node"]["w"] / 2, O[j]["node"]["y"] + O[j]["node"]["h"] / 2)
             for j in c["in_prev"]] + [("c", pc, pc["cx"], pc["cy"]) for pc in c["pcells"]]
    hue = [it for it in items if it[0] == "c" and it[1]["ci"] == 20]
    last = 0.0
    for kind, it, cx, cy in items:
        if it in [h[1] for h in hue]:
            continue
        d = WIPE * (0.45 * (cx - x0) / 216 + 0.55 * (cy - y0) / 216)
        last = max(last, d)
        tt = s + L_CONTENT + d
        if kind == "n":
            ink_in(it, tt, c["ptop"], dur=0.30)
        else:
            rise(it["ob"], tt, c["ptop"], dur=0.30)
            fade(it["ob"], tt, it["a"], dur=0.16)
    for kind, it, cx, cy in hue:
        tt = s + L_CONTENT + last + 0.10
        rise(it["ob"], tt, c["ptop"], dur=0.30)
        fade(it["ob"], tt, it["a"], dur=F.ARRIVE)
    ink_in(c["name"], s + L_NAME, c["top"])
    ink_in(c["cat"], s + L_CAT, c["top"])
    # the switch: track out of the card, then the knob (with its grip dots) out of the card through it
    rise(c["track"], s + L_TRACK, c["top"], dur=0.32)
    if c["track_node"] is None:
        fade(c["track"], s + L_TRACK, c["track"].color[3], dur=0.16)
    rise(c["knob"]["ob"], s + L_KNOB, c["top"], dur=0.30)
    for gc in c["grips"]:
        fade(gc["ob"], s + L_KNOB + 0.12, gc["a"], dur=0.16)

# ---------------------------------------------------------------- live previews
def twin_text(i, body, centre=False):
    """a hidden copy of text node i with another body (for hard-stepped counters and count-ups)."""
    ob = text_ob(i)
    n = O[i]["node"]
    tw = ob.copy()
    tw.animation_data_clear()          # a copied object shares the original's action; keep them apart
    tw.data = ob.data.copy()
    tw.data.body = body
    tw.name = f"{ob.name}_{body}"
    for coll in ob.users_collection:
        coll.objects.link(tw)
    tw.parent = ob.parent
    tw.matrix_parent_inverse = ob.matrix_parent_inverse.copy()
    tw.location = ob.location.copy()
    if centre:
        tw.data.align_x = "CENTER"
        tw.location.x = ob.location.x + n["w"] / 2 * MM
    tw.hide_render = tw.hide_viewport = True
    return tw


def visible_between(ob, a, b=None):
    F.show([ob], a, visible=True)
    if b is not None:
        F.show([ob], b, visible=False)


def count_up(i, t, fmt, v0, v1, steps=9, step=0.05):
    """the number counts up as it arrives: hard steps every 3 frames on an ease-out, then the real text."""
    ob = text_ob(i)
    vals = []
    for k in range(steps):
        e = 1 - (1 - k / steps) ** 3
        s = fmt(v0 + (v1 - v0) * e)
        if s != fmt(v1) and (not vals or vals[-1] != s):
            vals.append(s)
    for k, body in enumerate(vals):
        tw = twin_text(i, body, centre=True)
        tw.color = (*tuple(ob.color)[:3], 1.0)
        a = t + k * step
        visible_between(tw, a, a + step)
        JOBS.append(("fade", tw, t, dict(alpha=1.0, dur=F.ARRIVE, from_alpha=0.0)))
    # the real text is hidden until the count lands on it
    F.show([ob], t, visible=False)
    F.show([ob], t + len(vals) * step, visible=True)


COUNTS = {32: (lambda v: f"{round(v)}", 0, 142), 42: (lambda v: f"{v:.1f}", 0.0, 6.2),
          75: (lambda v: f"{v:.1f}", 0.0, 3.2), 64: (lambda v: f"{v:.1f}×", 1.0, 2.0)}
for kind, ob, t, a in list(JOBS):
    if kind != "fade":
        continue
    for i, (fmt, v0, v1) in COUNTS.items():
        if ob is text_ob(i):
            count_up(i, t, fmt, v0, v1)

# keystrokes: a real little input loop while the camera glides past (strafe A/D, two clicks); W held
KEY_ON, KEY_OFF = 0.42, 0.10


def key_press(c, t):
    F.move(c["ob"], "color", 3, t, KEY_OFF, t + 0.06, KEY_ON, "CUBIC", "EASE_OUT")
    F.press(c["ob"], t, depth=0.5, down=0.06, up=0.16)


def key_release(c, t):
    F.move(c["ob"], "color", 3, t, KEY_ON, t + 0.10, KEY_OFF, "CUBIC", "EASE_IN")


kA, kD = cell_at(605, 262, 26, 26), cell_at(669, 262, 26, 26)
lmb = cell_at(605, 296, 38, 26)
LIVE = []
for t, fn, c in ((1.05, key_release, kD), (1.10, key_press, kA), (1.42, key_release, lmb), (1.52, key_press, lmb),
                 (1.70, key_release, kA), (1.75, key_press, kD), (1.90, key_release, lmb), (2.00, key_press, lmb)):
    LIVE.append((fn, c, t))

# ---------------------------------------------------------------- apply
for fn, c, t in LIVE:
    fn(c, t)
for kind, ob, t, a in JOBS:
    if kind == "rise":
        F.arrive(ob, t, depth=a["depth"], dur=a["dur"], curve=a["curve"])
    else:
        F.light(ob, t, a["alpha"], dur=a["dur"], from_alpha=a["from_alpha"])


# ---------------------------------------------------------------- the payoff: Fullbright switches on
def lin(h):
    return V.lin(h)[:3]


def step_color(ob, t, rgba_before, rgba_after):
    for i in range(4):
        if abs(rgba_before[i] - rgba_after[i]) > 1e-6:
            F.step(ob, "color", i, t, rgba_before[i], rgba_after[i])


fb = FB_CARD
t_mid = T_FLIP + FLIP / 2
trk_grp = fb["track"]                               # group 83: slab + rim, knob and grips ride inside it
trk = O[fb["track_node"]]["slab"]
rim = O[fb["track_node"]].get("rim")
knob = fb["knob"]["ob"]
# a small press of the whole switch, as if a finger clicked it (no cursor in this shot)
F.press(trk_grp, T_FLIP - 0.03, depth=0.45, down=0.07, up=0.18)
# knob travels 15 px right, 140 ms ease-out
F.move(knob, "location", 0, T_FLIP, knob.location.x, T_FLIP + FLIP, knob.location.x + 15 * MM, "CUBIC", "EASE_OUT")
# the track steps to the on fill at the midpoint (alpha twin so it matches the other on-tracks exactly)
trk.data.materials[0] = scr.mat_alpha
step_color(trk, t_mid, tuple(trk.color), (*lin("#EDEEEF"), 0.88))
if rim is not None:
    step_color(rim, t_mid, tuple(rim.color), (*tuple(rim.color)[:3], 0.0))
step_color(knob, t_mid, tuple(knob.color), (*lin("#0B0B0C"), 1.0))
for gc in fb["grips"]:
    step_color(gc["ob"], t_mid, tuple(gc["ob"].color), (*lin("#EDEEEF"), gc["a"]))
# the label lifts from muted to primary; the category from 60 % to full
name_ob, cat_ob = text_ob(fb["name"]), text_ob(fb["cat"])
F.recolor(name_ob, T_FLIP, lin("#EDEEEF"), dur=FLIP)
F.move(cat_ob, "color", 3, T_FLIP, cat_ob.color[3], T_FLIP + FLIP, 1.0, "CUBIC", "EASE_OUT")
# the preview goes full bright, ring by ring from the centre
FB_LIT = 0.34
pc = fb["pcells"]
mx = sum(p["cx"] for p in pc) / len(pc)
my = sum(p["cy"] for p in pc) / len(pc)
for p in pc:
    ring = round(max(abs(p["cx"] - mx), abs(p["cy"] - my)) / 22)
    F.move(p["ob"], "color", 3, T_FLIP + 0.08 + ring * 0.05, p["a"], T_FLIP + 0.08 + ring * 0.05 + 0.22, FB_LIT,
           "CUBIC", "EASE_OUT")


# counters: 11 -> 12, a hard swap to a twin text object, and one more meter cell lit
def swap_text(i, body, t):
    tw = twin_text(i, body)
    F.show([text_ob(i)], t, visible=False)
    F.show([tw], t, visible=True)
    return tw


swap_text(88, "12 OF 24 MODS ENABLED", t_mid)
swap_text(28, "24 MODS  ·  12 ENABLED", t_mid)
meter = cell_at(521, 926, 8, 8)
F.light(meter["ob"], t_mid, 0.36, dur=FLIP, from_alpha=meter["a"])

# ---------------------------------------------------------------- camera: a low dolly along the grid -> Fullbright
cam = V.camera("Cam", G, lens=36.0, clip_start=0.02, clip_end=40.0)


def P(x, y, d=0.0):
    """world point at Figma pixel (x, y), d mm in front of the screen face."""
    return tuple(F.screen_point(scr, x, y, d))


beats = [
    dict(t=-0.8, pos=P(-200, 520, 660), tgt=P(620, 415), lens=31.0, fstop=4.0, focus=P(400, 330)),
    dict(t=0.0, pos=P(-70, 530, 650), tgt=P(700, 420), lens=31.0, fstop=4.0, focus=P(420, 330)),
    dict(t=1.5, pos=P(420, 575, 610), tgt=P(1080, 450), lens=34.0, fstop=4.5, focus=P(800, 430)),
    dict(t=2.8, pos=P(860, 690, 590), tgt=P(1330, 595), lens=37.0, fstop=4.5, focus=P(1200, 620)),
    dict(t=3.7, pos=P(1110, 790, 575), tgt=P(1416, 670), lens=41.0, fstop=4.5, focus=P(1422, 765)),
    dict(t=6.0, pos=P(1128, 798, 545), tgt=P(1418, 672), lens=42.0, fstop=4.5, focus=P(1425, 770)),
]


def fly(cam, beats, seconds, omega=3.2, shake=0.0006):
    """film.fly with a pre-roll: the spring starts at the first beat's (negative) time so the dolly is
    already travelling on frame 1, as it would be on a cut."""
    sub = 8
    t0 = beats[0]["t"]
    pos = F.catmull(beats, t0, "pos")
    tgt = F.catmull(beats, t0, "tgt")
    vp, vt = Vector(), Vector()
    cd = cam.data
    cd.dof.use_dof = True
    n = int(round(seconds * V.FPS))
    t = t0
    h = 1 / V.FPS / sub
    f = 1
    while f <= n:
        ft = (f - 1) / V.FPS
        while t < ft - 1e-9:
            t += h
            for cur, v, goal in ((pos, vp, F.catmull(beats, t, "pos")), (tgt, vt, F.catmull(beats, t, "tgt"))):
                a = omega * omega * (goal - cur) - 2 * omega * v
                v += a * h
                cur += v * h
        nz = Vector((noise.noise(Vector((ft * 0.6, 0.0, 1.1))), noise.noise(Vector((ft * 0.6, 3.3, 0.4))),
                     noise.noise(Vector((ft * 0.6, 7.1, 2.2)))))
        cam.location = pos + nz * shake
        V.look_at(cam, tgt + nz * shake * 2.0)
        cam.keyframe_insert("location", frame=f)
        cam.keyframe_insert("rotation_quaternion", frame=f)
        cd.lens = F.lerp_beat(beats, ft, "lens")
        cd.keyframe_insert("lens", frame=f)
        cd.dof.aperture_fstop = F.lerp_beat(beats, ft, "fstop")
        cd.dof.keyframe_insert("aperture_fstop", frame=f)
        foc = F.catmull(beats, ft, "focus")
        cd.dof.focus_distance = max(0.05, (foc - cam.location).length)
        cd.dof.keyframe_insert("focus_distance", frame=f)
        f += 1


fly(cam, beats, SECONDS, omega=3.2)

if BLEND:
    V.save(BLEND)
if MODE == "stills":
    F.render(OUT, stills=[1, 14, 22, 30, 40, 52, 64, 80, 110, 150, 200, 240, 258, 263, 270, 300, 360])
else:
    F.render(OUT)
