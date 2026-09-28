"""VOID promo film kit: scene/lights/camera for the natively rebuilt screens, and UI animation
helpers keyed with real easing curves (two keys per move, not per-frame bakes).

Screens are FB.Screen objects standing upright (root rotated +90° X, facing -Y). Every node has a
group empty whose local Z is the screen normal, so `arrive()` literally raises an element out of the
surface it sits in — elements start sunk inside their parent slab and rise into place.
"""
import bpy, math, os, sys
from mathutils import Vector, Matrix, Quaternion, noise
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmg as V
import figma_build as FB

FPS = V.FPS
MM = 0.001


def setup(seconds, pct=100, samples=64, motion_blur=True):
    sc = V.reset(seconds, pct=pct, samples=samples)
    e = sc.eevee
    for k, v in dict(use_raytracing=True, ray_tracing_method="SCREEN", use_shadows=True, shadow_ray_count=2,
                     shadow_step_count=8, use_fast_gi=True, fast_gi_method="GLOBAL_ILLUMINATION",
                     fast_gi_distance=0.25, motion_blur_steps=1).items():
        V.setp(e, k, v)
    V.setp(e.ray_tracing_options, "trace_max_roughness", 0.6)
    sc.view_settings.view_transform = "Standard"
    sc.render.filter_size = 1.2
    sc.render.use_motion_blur = motion_blur
    V.setp(sc.render, "motion_blur_shutter", 0.5)
    sc.world.node_tree.nodes["Background"].inputs["Color"].default_value = V.lin("#060607")
    return sc


def f_of(t):
    return 1 + t * FPS


# ---------------------------------------------------------------- screens
def screen(which, coll, loc=(0, 0, 0.49), rot_z=0.0, name=None):
    s = FB.Screen(os.path.join(V.HERE, "figma", f"{which}.txt"), coll, name=name or f"Screen_{which}")
    s.root.rotation_euler = (math.radians(90), 0, rot_z)
    s.root.location = loc
    return s


def light_rig(coll, center=(0, 0, 0.49), scale=1.0, key=55, top=70, graze=0, fill=10):
    c = Vector(center)

    def area(name, power, off, size, sy=None, col="#FFFFFF", target=None, spread=None):
        ld = bpy.data.lights.new(name, "AREA")
        ld.energy = power * scale * scale
        ld.color = V.lin(col)[:3]
        ld.shape = "RECTANGLE" if sy else "SQUARE"
        ld.size = size * scale
        if sy:
            ld.size_y = sy * scale
        if spread:
            ld.spread = math.radians(spread)
        o = bpy.data.objects.new(name, ld)
        coll.objects.link(o)
        o.location = c + Vector(off) * scale
        V.look_at(o, target if target is not None else c)
        return o
    rig = [area("Key", key, (-1.4, -1.6, 1.21), 1.4, 0.9, "#FFF6EC"),
           area("Fill", fill, (1.8, -1.4, 0.11), 1.6, 1.0, "#E8EEFF"),
           area("Top", top, (0.0, 0.25, 1.06), 2.2, 0.12, "#FFFFFF", target=c + Vector((0, 0.2, 0.41)) * scale)]
    if graze:
        rig.append(area("Graze", graze, (1.9, 0.9, 0.3), 0.08, 1.2, "#FFFFFF", spread=35))
    return rig


# ---------------------------------------------------------------- keyframes with easing
def _fcurve(idblock, path, index):
    ad = idblock.animation_data
    if not ad or not ad.action:
        return None
    for layer in ad.action.layers:
        for strip in layer.strips:
            for cb in strip.channelbags:
                for fc in cb.fcurves:
                    if fc.data_path == path and fc.array_index == index:
                        return fc
    return None


def key(idblock, path, index, frame, value, interp="BEZIER", easing="AUTO"):
    """insert one keyframe and set the interpolation of the segment that STARTS at it."""
    prop = idblock.path_resolve(path)
    if index >= 0:
        prop[index] = value
        idblock.keyframe_insert(path, index=index, frame=frame)
    else:
        setattr(idblock, path, value) if "." not in path else None
        idblock.keyframe_insert(path, frame=frame)
    fc = _fcurve(idblock, path, max(index, 0))
    if fc:
        for kp in fc.keyframe_points:
            if abs(kp.co.x - frame) < 1e-3:
                kp.interpolation = interp
                kp.easing = easing
    return fc


def move(idblock, path, index, t0, v0, t1, v1, curve="CUBIC", easing="EASE_OUT"):
    """a two-key move from t0->t1 (seconds) with an easing curve (CUBIC/QUART/QUINT/EXPO/SINE...)."""
    key(idblock, path, index, f_of(t0), v0, curve, easing)
    key(idblock, path, index, f_of(t1), v1, "CONSTANT", "AUTO")


def step(idblock, path, index, t, v_before, v_after):
    key(idblock, path, index, f_of(t) - 1, v_before, "CONSTANT")
    key(idblock, path, index, f_of(t), v_after, "CONSTANT")


# ---------------------------------------------------------------- UI moves (the product's own timings)
ARRIVE = 0.14     # arrive / hover in, ease-out
LEAVE = 0.10      # leave, ease-in
DRAIN = 0.20      # release drain, ease-in
STAGGER = 0.04


def arrive(ob, t, depth=3.0, dur=0.32, curve="QUART"):
    """rise out of the surface: start sunk `depth` below rest, ease out to rest."""
    z = ob.location.z
    move(ob, "location", 2, t, z - depth * MM, t + dur, z, curve, "EASE_OUT")


def subtree_top_mm(scr, grp):
    """highest point (mm above the shell face) of everything under a group, from real geometry."""
    bpy.context.view_layer.update()
    inv = scr.root.matrix_world.inverted()
    top = 0.0
    for ob in [grp] + list(grp.children_recursive):
        if ob.type in ("MESH", "CURVE", "FONT"):
            for c in ob.bound_box:
                z = (inv @ (ob.matrix_world @ Vector(c))).z / MM
                top = max(top, z)
    return top


def arrive_node(scr, idx, t, dur=0.32, extra=1.0, curve="QUART"):
    """raise a node's group out of the shell: it starts fully sunk below the shell's top face."""
    grp = scr.objs[idx]["group"]
    arrive(grp, t, depth=subtree_top_mm(scr, grp) + extra, dur=dur, curve=curve)


def arrive_cell(ob, t, dur=0.3, extra=0.6, curve="QUART"):
    z_mm = ob.location.z / MM
    arrive(ob, t, depth=z_mm + extra + 1.0, dur=dur, curve=curve)


def sink(ob, t, depth=3.0, dur=0.18, curve="CUBIC"):
    z = ob.location.z
    move(ob, "location", 2, t, z, t + dur, z - depth * MM, curve, "EASE_IN")


def press(ob, t, depth=2.4, down=0.10, up=0.16):
    z = ob.location.z
    key(ob, "location", 2, f_of(t), z, "CUBIC", "EASE_OUT")
    key(ob, "location", 2, f_of(t + down), z - depth * MM, "CUBIC", "EASE_OUT")
    key(ob, "location", 2, f_of(t + down + up), z, "CONSTANT")


def light(ob, t, alpha, dur=ARRIVE, from_alpha=0.0, curve="CUBIC"):
    c = list(ob.color)
    ob.color = (c[0], c[1], c[2], from_alpha)
    move(ob, "color", 3, t, from_alpha, t + dur, alpha, curve, "EASE_OUT")


def recolor(ob, t, rgb, dur=ARRIVE, curve="CUBIC"):
    c0 = list(ob.color)
    for i in range(3):
        move(ob, "color", i, t, c0[i], t + dur, rgb[i], curve, "EASE_OUT")


def show(obs, t, visible=True):
    """hard visibility step (hide_render/hide_viewport) at time t for a list of objects."""
    for ob in obs:
        for path in ("hide_render", "hide_viewport"):
            setattr(ob, path, visible)
            ob.keyframe_insert(path, frame=f_of(t) - 1)
            setattr(ob, path, not visible)
            ob.keyframe_insert(path, frame=f_of(t))
            fc = _fcurve(ob, path, 0)
            if fc:
                for kp in fc.keyframe_points:
                    kp.interpolation = "CONSTANT"


def subtree(entry):
    g = entry["group"]
    return [g] + list(g.children_recursive)


# ---------------------------------------------------------------- flown camera
def catmull(beats, t, key_):
    ts = [b["t"] for b in beats]
    if t <= ts[0]:
        return Vector(beats[0][key_])
    if t >= ts[-1]:
        return Vector(beats[-1][key_])
    i = max(k for k in range(len(ts)) if ts[k] <= t)
    p0 = Vector(beats[max(i - 1, 0)][key_]); p1 = Vector(beats[i][key_])
    p2 = Vector(beats[i + 1][key_]); p3 = Vector(beats[min(i + 2, len(beats) - 1)][key_])
    u = (t - ts[i]) / (ts[i + 1] - ts[i])
    u2, u3 = u * u, u * u * u
    return 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3)


def lerp_beat(beats, t, key_):
    ts = [b["t"] for b in beats]
    if t <= ts[0]:
        return beats[0][key_]
    if t >= ts[-1]:
        return beats[-1][key_]
    i = max(k for k in range(len(ts)) if ts[k] <= t)
    u = V.ease_io((t - ts[i]) / (ts[i + 1] - ts[i]))
    return beats[i][key_] + (beats[i + 1][key_] - beats[i][key_]) * u


def fly(cam, beats, seconds, omega=5.0, shake=0.0006, up=(0, 0, 1)):
    """beats: [dict(t, pos, tgt, lens, fstop, focus)] -> per-frame camera with spring smoothing and a
    breath of handheld drift. Focus distance tracks the `focus` point of the beats."""
    sub = 8
    pos = catmull(beats, 0, "pos")
    tgt = catmull(beats, 0, "tgt")
    vp, vt = Vector(), Vector()
    cd = cam.data
    cd.dof.use_dof = True
    n = int(round(seconds * FPS))
    for f in range(1, n + 1):
        t = (f - 1) / FPS
        for s in range(sub):
            tt = t - 1 / FPS + (s + 1) / FPS / sub
            h = 1 / FPS / sub
            for cur, v, goal in ((pos, vp, catmull(beats, tt, "pos")), (tgt, vt, catmull(beats, tt, "tgt"))):
                a = omega * omega * (goal - cur) - 2 * omega * v
                v += a * h
                cur += v * h
        nz = Vector((noise.noise(Vector((t * 0.6, 0.0, 1.1))), noise.noise(Vector((t * 0.6, 3.3, 0.4))),
                     noise.noise(Vector((t * 0.6, 7.1, 2.2)))))
        cam.location = pos + nz * shake
        V.look_at(cam, tgt + nz * shake * 2.0, up=up)
        cam.keyframe_insert("location", frame=f)
        cam.keyframe_insert("rotation_quaternion", frame=f)
        cd.lens = lerp_beat(beats, t, "lens")
        cd.keyframe_insert("lens", frame=f)
        cd.dof.aperture_fstop = lerp_beat(beats, t, "fstop")
        cd.dof.keyframe_insert("aperture_fstop", frame=f)
        foc = catmull(beats, t, "focus")
        cd.dof.focus_distance = max(0.05, (foc - cam.location).length)
        cd.dof.keyframe_insert("focus_distance", frame=f)


def screen_point(scr, x_px, y_px, z_mm=0.0):
    """world position of a Figma pixel coordinate on a screen (after the root transform)."""
    bpy.context.view_layer.update()
    local = Vector(((x_px - scr.W / 2) * MM, (scr.H / 2 - y_px) * MM, z_mm * MM))
    return scr.root.matrix_world @ local


def render(outdir, stills=None):
    V.render_frames(outdir, stills)
