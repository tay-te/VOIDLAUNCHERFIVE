"""S5 SETUP — the live value. The properties column builds, the meters fill in monochrome, and the
one hue in the shot lands on the live cell. Then the value is nudged, a toggle flips, Save presses.

  Blender -b --factory-startup --python shot_s5_setup.py -- OUTDIR [PCT] [SAMPLES] [anim|stills] [BLEND]
"""
import bpy, sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmg as V, film as F, figma_build as FB
from mathutils import Vector, noise

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else os.path.join(V.HERE, "out", "s5")
PCT = int(argv[1]) if len(argv) > 1 else 50
SAMPLES = int(argv[2]) if len(argv) > 2 else 32
MODE = argv[3] if len(argv) > 3 else "anim"
BLEND = argv[4] if len(argv) > 4 else None

SECONDS = 5.0
sc = F.setup(SECONDS, pct=PCT, samples=SAMPLES)
G = V.coll("Screen")
L = V.coll("Lights")
scr = F.screen("setup", G)
F.light_rig(L)
O = scr.objs
MM = F.MM


def cells(w, h, hexc):
    col = FB.hexlin(hexc)
    for c in scr.cell_objs:
        g = c["group"]
        if abs(g["w"] - w) < 0.01 and abs(g["h"] - h) < 0.01 and max(abs(a - b) for a, b in zip(g["col"], col)) < 1e-3:
            return c
    raise KeyError((w, h, hexc))


def rgba(hexc, a):
    return tuple(FB.hexlin(hexc)) + (a,)


def at(ob, path, i, t):
    """the animated value of ob.path[i] at time t. view_layer.update() (arrive_node calls it) flushes the
    frame-1 pose back onto every animated object, so the live property is not the rest value."""
    fc = F._fcurve(ob, path, i)
    return fc.evaluate(F.f_of(t)) if fc else getattr(ob, path)[i]


def tint(ob, t, c1, dur=F.ARRIVE, curve="CUBIC", easing="EASE_OUT"):
    """move an object's colour (rgb + alpha) from its value at t to c1 over `dur`."""
    for i in range(4):
        c0 = at(ob, "color", i, t)
        if abs(c0 - c1[i]) > 1e-6:
            F.move(ob, "color", i, t, c0, t + dur, c1[i], curve, easing)


def hard(ob, t, c1):
    """a hard fill step at t (the product's own state change: no fade)."""
    for i in range(4):
        c0 = at(ob, "color", i, t)
        if abs(c0 - c1[i]) > 1e-6:
            F.step(ob, "color", i, t, c0, c1[i])


def slide(ob, t, dx_px, dur=F.ARRIVE, curve="CUBIC"):
    x = at(ob, "location", 0, t)
    F.move(ob, "location", 0, t, x, t + dur, x + dx_px * MM, curve, "EASE_OUT")


def press(ob, t):
    ob.location.z = at(ob, "location", 2, t)
    F.press(ob, t)


def rise(objs, t, dur):
    """a unit is a control and whatever is stacked on it (track > knob > grip dots, button > label).
    Each part starts fully sunk below the shell face and the stack builds bottom-up, one level every
    LEVEL_DT: the knob surfaces out of its track, the dots out of the knob. (A rigid stack would show
    its top first: the knob would break the panel face before its track.)"""
    for ob in objs:
        F.arrive(ob, t + LEVEL.get(ob, 0) * LEVEL_DT, depth=F.subtree_top_mm(scr, ob) + 1.0, dur=dur)


def smooth3(u):
    u = V.clamp01(u)
    return u * u * (3 - 2 * u)


def glide(beats, t, key_):
    """piecewise camera path: each segment eases out of one beat and into the next (no Catmull-Rom
    overshoot when a long move is followed by a slow drift)."""
    ts = [b["t"] for b in beats]
    if t <= ts[0]:
        return Vector(beats[0][key_]) if key_ != "lens" and key_ != "fstop" else beats[0][key_]
    if t >= ts[-1]:
        return Vector(beats[-1][key_]) if key_ != "lens" and key_ != "fstop" else beats[-1][key_]
    i = max(k for k in range(len(ts)) if ts[k] <= t)
    u = smooth3((t - ts[i]) / (ts[i + 1] - ts[i]))
    a, b = beats[i][key_], beats[i + 1][key_]
    if key_ in ("lens", "fstop"):
        return a + (b - a) * u
    return Vector(a).lerp(Vector(b), u)


def fly_glide(cam, beats, seconds, omega=4.5, shake=0.0006, up=(0, 0, 1)):
    """film.fly with eased segments for the goal path: same spring, same breath of handheld drift. The
    focus point rides the same spring as the camera, so focus never pulls ahead of the move."""
    sub = 8
    pos, tgt = glide(beats, 0, "pos"), glide(beats, 0, "tgt")
    lens, foc = glide(beats, 0, "lens"), glide(beats, 0, "focus")
    vp, vt, vf, vl = Vector(), Vector(), Vector(), 0.0
    cd = cam.data
    cd.dof.use_dof = True
    for f in range(1, int(round(seconds * F.FPS)) + 1):
        t = (f - 1) / F.FPS
        for s in range(sub):
            h = 1 / F.FPS / sub
            tt = t - 1 / F.FPS + (s + 1) * h
            for cur, v, goal in ((pos, vp, glide(beats, tt, "pos")), (tgt, vt, glide(beats, tt, "tgt")),
                                 (foc, vf, glide(beats, tt, "focus"))):
                a = omega * omega * (goal - cur) - 2 * omega * v
                v += a * h
                cur += v * h
            al = omega * omega * (glide(beats, tt, "lens") - lens) - 2 * omega * vl
            vl += al * h
            lens += vl * h
        nz = Vector((noise.noise(Vector((t * 0.6, 0.0, 1.1))), noise.noise(Vector((t * 0.6, 3.3, 0.4))),
                     noise.noise(Vector((t * 0.6, 7.1, 2.2)))))
        cam.location = pos + nz * shake
        V.look_at(cam, tgt + nz * shake * 2.0, up=up)
        cam.keyframe_insert("location", frame=f)
        cam.keyframe_insert("rotation_quaternion", frame=f)
        cd.lens = lens
        cd.keyframe_insert("lens", frame=f)
        cd.dof.aperture_fstop = glide(beats, t, "fstop")
        cd.dof.keyframe_insert("aperture_fstop", frame=f)
        cd.dof.focus_distance = max(0.05, (foc - cam.location).length)
        cd.dof.keyframe_insert("focus_distance", frame=f)


EDE, VIO = "edeeef", "9f8bff"
LIT, UNLIT, LIVE = 0.38, 0.06, 0.95

# ---------------------------------------------------------------- the cells we play with
meter = cells(12, 12, EDE)
m_objs, m_pts = meter["objs"], meter["group"]["pts"]
scale_row, opac_row = list(zip(m_objs[:10], m_pts[:10])), list(zip(m_objs[10:], m_pts[10:]))
# the file paints the violet live cells as a second, coplanar layer; the live cell is ONE object here
# (the grey cell itself takes the hue), so the duplicate layer goes.
for ob in cells(12, 12, VIO)["objs"]:
    ob.hide_render = ob.hide_viewport = True
on_tracks, on_knobs = cells(46, 26, EDE)["objs"], cells(20, 20, "0b0b0c")["objs"]
on_dots = cells(2, 2, EDE)["objs"]
off_knobs, off_dots = cells(20, 20, "626569")["objs"], cells(2, 2, "0b0b0c")["objs"]
divider = cells(644, 1, "ffffff")["objs"][0]
save_btn = cells(150, 46, EDE)["objs"][0]
live_dot = cells(6, 6, VIO)["objs"][0]               # the selected profile chip's dot
chip_dots = cells(6, 6, EDE)["objs"]

# ---------------------------------------------------------------- 1. rows build top-down, 40 ms apart
# (x_px, unit) per row; inside a row the units follow left to right, 60 ms across the column, and each
# unit builds bottom-up by stack level (LEVEL).
g = lambda i: O[i]["group"]
LEVEL_DT = 0.05
LEVEL = {ob: 1 for ob in on_knobs + off_knobs + chip_dots + [live_dot, g(50), g(58), g(61), g(63), g(65)]}
LEVEL.update({ob: 2 for ob in on_dots + off_dots})
rows = [
    [(892, [g(40)])],                                                             # PROPERTIES
    [(892, [g(41)])],                                                             # DISPLAY
    [(892, [g(42)])] + [(x, [ob]) for ob, (x, y, a) in scale_row],               # Scale
    [(892, [g(44)])] + [(x, [ob]) for ob, (x, y, a) in opac_row],                # Opacity
    [(892, [g(46)]), (1490, [on_tracks[0], on_knobs[0]] + on_dots[:3])],          # Drop shadow
    [(892, [g(47)])],                                                             # INPUT
    [(892, [g(48)]), (1458, [g(49), g(50)])],                                     # Keybind
    [(892, [g(51)]), (1490, [on_tracks[1], on_knobs[1]] + on_dots[3:])],          # Show mouse buttons
    [(892, [g(52)])],                                                             # BEHAVIOUR
    [(892, [g(53)]), (1490, [g(54), off_knobs[0]] + off_dots[:3])],               # Hide while typing
    [(892, [g(55)]), (1490, [g(56), off_knobs[1]] + off_dots[3:])],               # Show on scoreboard
    [(892, [divider])],
    [(892, [g(57)]), (1386, [save_btn, g(58)])],                                  # Reset / Save
    [(892, [g(59)])],                                                             # ENABLED IN
    [(892, [g(60), live_dot, g(61)]), (1014, [g(62), chip_dots[0], g(63)]), (1122, [g(64), chip_dots[1], g(65)])],
]
T_ROWS = 0.08
for k, row in enumerate(rows):
    for x, unit in row:
        rise(unit, T_ROWS + k * F.STAGGER + (x - 892) / 644 * 0.06, dur=0.34)

# the meters come up empty (every cell at the unlit alpha), the values are withheld,
# and the profile chip's dot is monochrome until Save lands.
for ob, (x, y, a) in scale_row + opac_row:
    ob.color = rgba(EDE, UNLIT)
for i in (43, 45):
    O[i]["text"].color = (*O[i]["text"].color[:3], 0.0)
live_dot.color = rgba(EDE, 0.16)

# ---------------------------------------------------------------- 2. the meters fill, the hue lands last
FILL = 0.03


def fill(row, t0, n_lit):
    """cells 0..n_lit-2 step to the lit alpha one every 30 ms; returns (live cell, fill end)."""
    for j in range(n_lit - 1):
        F.light(row[j][0], t0 + j * FILL, LIT, dur=F.ARRIVE, from_alpha=UNLIT)
    return row[n_lit - 1][0], t0 + (n_lit - 2) * FILL


def value_in(entry, t):
    """the value lands with its cell: 140 ms ease-out, rising out of the panel face."""
    txt = entry["text"]
    F.light(txt, t, LIVE, dur=F.ARRIVE, from_alpha=0.0)
    grp = entry["group"]
    grp.location.z = at(grp, "location", 2, t)
    F.arrive(grp, t, depth=0.9, dur=0.2)


T_FILL_S, T_FILL_O = 1.16, 1.24
live_s, end_s = fill(scale_row, T_FILL_S, 7)          # 1.4x : seven lit, the seventh is live
live_o, end_o = fill(opac_row, T_FILL_O, 9)           # 85%  : nine lit, the ninth is live
T_LIVE_S = end_s + 0.16
T_LIVE_O = max(end_o + 0.12, T_LIVE_S + 0.16)
tint(live_s, T_LIVE_S, rgba(VIO, LIVE))
value_in(O[43], T_LIVE_S)
tint(live_o, T_LIVE_O, rgba(VIO, LIVE))
value_in(O[45], T_LIVE_O)

# ---------------------------------------------------------------- 3. the nudge: one step right, 1.4x -> 1.5x
T_NUDGE = 2.18
nxt = scale_row[7][0]
tint(live_s, T_NUDGE, rgba(EDE, LIT), dur=F.LEAVE, easing="EASE_IN")      # the hue leaves: ease-in 100 ms
tint(nxt, T_NUDGE, rgba(VIO, LIVE))                                          # and lands: ease-out 140 ms
v14 = O[43]["text"]
v15 = v14.copy()
v15.data = v14.data.copy()
v15.data.body = "1.5×"
v15.name = "043_1.5x"
G.objects.link(v15)
bpy.context.view_layer.update()
right = lambda ob: max(c[0] for c in ob.bound_box)
v15.location.x += right(v14) - right(v15)              # values are right-aligned to the column edge
if v15.animation_data:
    v15.animation_data_clear()
v15.color = rgba(VIO, LIVE)
v15.hide_render = v15.hide_viewport = True
T_SWAP = T_NUDGE + 0.07                                # hard swap at the middle of the hue's travel
F.show([v14], T_SWAP, visible=False)
F.show([v15], T_SWAP, visible=True)

# ---------------------------------------------------------------- 4. Hide while typing flips on
T_TOG = 3.50
knob, grip = off_knobs[0], off_dots[:3]
slide(knob, T_TOG, 20)                                 # 1493 -> 1513, the on position of the rows above
tint(knob, T_TOG, rgba("0b0b0c", 1.0))
for d in grip:
    slide(d, T_TOG, 20)
    tint(d, T_TOG, rgba(EDE, 0.3))
on_fill = tuple(0.88 * a + 0.12 * b for a, b in zip(FB.hexlin(EDE), FB.hexlin("131315"))) + (1.0,)
hard(O[54]["slab"], T_TOG + F.ARRIVE / 2, on_fill)     # the track steps its fill at the midpoint
hard(O[54]["rim"], T_TOG + F.ARRIVE / 2, (*O[54]["rim"].color[:3], 0.0))

# ---------------------------------------------------------------- 5. Save presses; the active profile takes the hue
T_SAVE = 3.90
press(save_btn, T_SAVE)
press(O[58]["group"], T_SAVE)
tint(live_dot, T_SAVE + 0.10, rgba(VIO, LIVE))

# ---------------------------------------------------------------- camera: a three-quarter from above the right edge
# (from below, the key light mirrors off the shell as a warm sheen; from above the shell stays charcoal).
# Wide on the column as it builds -> close on the meters for the hue -> down to the toggles -> out wide
# with the HUD editor soft behind the column.
cam = V.camera("Cam", G, lens=42.0, clip_start=0.02, clip_end=40.0)
beats = [                                      # the spring lags ~0.4 s: beats lead the action
    dict(t=0.00, pos=(0.98, -0.86, 1.14), tgt=(0.40, 0.0, 0.70), lens=42.0, fstop=4.0, focus=(0.42, 0.0, 0.76)),
    dict(t=0.22, pos=(0.97, -0.84, 1.13), tgt=(0.41, 0.0, 0.70), lens=42.5, fstop=4.0, focus=(0.44, 0.0, 0.76)),
    dict(t=0.92, pos=(0.79, -0.43, 0.935), tgt=(0.62, 0.0, 0.800), lens=60.0, fstop=3.2, focus=(0.64, 0.0, 0.80)),
    dict(t=2.45, pos=(0.785, -0.42, 0.93), tgt=(0.625, 0.0, 0.800), lens=62.0, fstop=3.2, focus=(0.645, 0.0, 0.80)),
    dict(t=3.25, pos=(0.84, -0.74, 0.58), tgt=(0.48, 0.0, 0.46), lens=42.0, fstop=3.5, focus=(0.66, 0.0, 0.46)),
    dict(t=3.80, pos=(0.845, -0.755, 0.59), tgt=(0.48, 0.0, 0.46), lens=42.5, fstop=3.5, focus=(0.64, 0.0, 0.42)),
    dict(t=4.55, pos=(1.16, -1.18, 1.14), tgt=(0.33, 0.0, 0.61), lens=40.0, fstop=2.8, focus=(0.46, 0.0, 0.54)),
    dict(t=5.00, pos=(1.17, -1.20, 1.15), tgt=(0.33, 0.0, 0.61), lens=40.0, fstop=2.8, focus=(0.46, 0.0, 0.54)),
]
fly_glide(cam, beats, SECONDS, omega=5.5)

if BLEND:
    V.save(BLEND)
if MODE == "stills":
    F.render(OUT, stills=[1, 30, 60, 90, 110, 140, 185, 212, 218, 236, 244, 270, 300])
else:
    F.render(OUT)
