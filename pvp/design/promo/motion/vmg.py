"""VOID motion-graphics kit for Blender 5.1 (EEVEE, headless).

The picture follows the quiet cell system: every surface is flat emission (no lights, no
shading, no bloom), colours are the contract's tokens rendered exactly (Standard view
transform), and depth comes only from where flat planes sit and how the camera moves.

Animation is either baked per frame from Python (objects, camera) or driven in-shader from a
single linear time value `T` (seconds), which is how thousands of cells animate cheaply:
animate the light on the cells, not the cells.
"""
import bpy, bmesh, math, os
from mathutils import Vector, Matrix

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets")
FONT_FILES = {w: os.path.join(HERE, "fonts", f"outfit-{w}.ttf") for w in (300, 400, 500)}

C = dict(shell="#0B0B0C", base="#131315", card="#191A1C", raised="#212225",
         text="#EDEEEF", text2="#9A9DA1", muted="#626569", ok="#3DD68C",
         hud="#9F8BFF", pvp="#FF9E7A", visual="#7ADFFF", utility="#7AE0B0", amber="#F2B84B")

FPS = 60


# ---------------------------------------------------------------- basics
def lin(h, a=1.0):
    h = h.lstrip("#")
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple((x / 12.92) if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c) + (a,)


def setp(obj, attr, val):
    try:
        setattr(obj, attr, val)
    except Exception as ex:
        print(f"[setp] {type(obj).__name__}.{attr}={val!r}: {ex}")


def clamp01(x):
    return 0.0 if x < 0 else 1.0 if x > 1 else x


def ease_out(t, p=3):
    t = clamp01(t)
    return 1 - (1 - t) ** p


def ease_in(t, p=3):
    t = clamp01(t)
    return t ** p


def ease_io(t):
    t = clamp01(t)
    return t * t * t * (t * (t * 6 - 15) + 10)


def reset(seconds=5.0, res=(1920, 1080), pct=100, samples=16):
    for ob in list(bpy.data.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    sc = bpy.context.scene
    sc.render.fps = FPS
    sc.frame_start = 1
    sc.frame_end = int(round(seconds * FPS))
    r = sc.render
    r.engine = "BLENDER_EEVEE"
    r.resolution_x, r.resolution_y = res
    r.resolution_percentage = pct
    r.film_transparent = False
    setp(r, "filter_size", 1.0)
    r.image_settings.file_format = "PNG"
    r.image_settings.color_depth = "8"
    setp(r, "dither_intensity", 0.5)
    r.use_motion_blur = False
    e = sc.eevee
    setp(e, "taa_render_samples", samples)
    setp(e, "use_raytracing", False)
    setp(e, "use_shadows", False)
    setp(e, "use_fast_gi", False)
    vs = sc.view_settings
    vs.view_transform = "Standard"
    setp(vs, "look", "None")
    vs.exposure = 0.0
    vs.gamma = 1.0
    w = bpy.data.worlds.new("VOID_World")
    sc.world = w
    setp(w, "use_nodes", True)
    bg = w.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = lin(C["shell"])
    bg.inputs["Strength"].default_value = 1.0
    sc.compositing_node_group = None
    return sc


def coll(name):
    c = bpy.data.collections.new(name)
    bpy.context.scene.collection.children.link(c)
    return c


# ---------------------------------------------------------------- shader node helper
class NB:
    """Tiny node-builder so shader maths reads like maths."""

    def __init__(self, mat):
        setp(mat, "use_nodes", True)
        self.nt = mat.node_tree
        for n in list(self.nt.nodes):
            if n.type != "OUTPUT_MATERIAL":
                self.nt.nodes.remove(n)
        self.out = self.nt.nodes["Material Output"]

    def node(self, t, **props):
        n = self.nt.nodes.new(t)
        for k, v in props.items():
            setattr(n, k, v)
        return n

    def _in(self, sock, v):
        if hasattr(v, "is_output") or hasattr(v, "links"):
            self.nt.links.new(v, sock)
        else:
            sock.default_value = v

    def math(self, op, a, b=0.0, c=None, clamp=False):
        n = self.node("ShaderNodeMath", operation=op, use_clamp=clamp)
        self._in(n.inputs[0], a)
        self._in(n.inputs[1], b)
        if c is not None:
            self._in(n.inputs[2], c)
        return n.outputs[0]

    def vmath(self, op, a, b=None, out=0):
        n = self.node("ShaderNodeVectorMath", operation=op)
        self._in(n.inputs[0], a)
        if b is not None:
            self._in(n.inputs[1], b)
        return n.outputs[out]

    def sep(self, v):
        n = self.node("ShaderNodeSeparateXYZ")
        self._in(n.inputs[0], v)
        return n.outputs

    def comb(self, x, y, z=0.0):
        n = self.node("ShaderNodeCombineXYZ")
        self._in(n.inputs[0], x)
        self._in(n.inputs[1], y)
        self._in(n.inputs[2], z)
        return n.outputs[0]

    def attr(self, name, kind="GEOMETRY"):
        n = self.node("ShaderNodeAttribute", attribute_name=name, attribute_type=kind)
        return n.outputs["Fac"]

    def attr_vec(self, name):
        n = self.node("ShaderNodeAttribute", attribute_name=name)
        return n.outputs["Vector"]

    def uv(self, name="UVMap"):
        n = self.node("ShaderNodeUVMap", uv_map=name)
        return n.outputs[0]

    def value(self, name, v=0.0):
        n = self.node("ShaderNodeValue", name=name, label=name)
        n.outputs[0].default_value = v
        return n.outputs[0]

    def smooth(self, e0, e1, x):
        n = self.node("ShaderNodeMapRange", interpolation_type="SMOOTHSTEP")
        self._in(n.inputs["Value"], x)
        self._in(n.inputs["From Min"], e0)
        self._in(n.inputs["From Max"], e1)
        return n.outputs[0]

    def lerp(self, a, b, t):
        return self.math("ADD", a, self.math("MULTIPLY", self.math("SUBTRACT", b, a), t))

    def emit_alpha(self, color, strength, alpha):
        """flat emission over transparency: the only surface VOID has."""
        em = self.node("ShaderNodeEmission")
        self._in(em.inputs["Color"], color)
        self._in(em.inputs["Strength"], strength)
        tr = self.node("ShaderNodeBsdfTransparent")
        mx = self.node("ShaderNodeMixShader")
        self._in(mx.inputs["Fac"], alpha)
        self.nt.links.new(tr.outputs[0], mx.inputs[1])
        self.nt.links.new(em.outputs[0], mx.inputs[2])
        self.nt.links.new(mx.outputs[0], self.out.inputs["Surface"])

    def rgb(self, hexc):
        n = self.node("ShaderNodeRGB")
        n.outputs[0].default_value = lin(hexc)
        return n.outputs[0]

    def mix_rgb(self, a, b, t):
        n = self.node("ShaderNodeMix", data_type="RGBA")
        for s in n.inputs:
            if s.identifier == "Factor_Float":
                self._in(s, t)
            elif s.identifier == "A_Color":
                self._in(s, a)
            elif s.identifier == "B_Color":
                self._in(s, b)
        return [o for o in n.outputs if o.identifier == "Result_Color"][0]

    def roundbox_mask(self, uvsock, radius=0.30, half=0.5):
        """1 inside a rounded square filling UV 0..1 (the cell: 30 % radius), 0 outside."""
        p = self.vmath("SUBTRACT", uvsock, (0.5, 0.5, 0.0))
        q = self.vmath("SUBTRACT", self.vmath("ABSOLUTE", p), (half - radius, half - radius, 0.0))
        outside = self.vmath("LENGTH", self.vmath("MAXIMUM", q, (0, 0, 0)), out=1)
        s = self.sep(q)
        inside = self.math("MINIMUM", self.math("MAXIMUM", s[0], s[1]), 0.0)
        d = self.math("SUBTRACT", self.math("ADD", outside, inside), radius)
        return self.math("LESS_THAN", d, 0.0)


def blended(mat):
    setp(mat, "surface_render_method", "BLENDED")
    setp(mat, "use_backface_culling", False)
    return mat


def flat_mat(name, hexc, alpha=1.0):
    m = bpy.data.materials.new(name)
    nb = NB(m)
    nb.emit_alpha(nb.rgb(hexc), 1.0, nb.value("Alpha", alpha))
    if alpha < 1.0:
        blended(m)
    return m


def image_mat(name, path, corner_px=0, alpha_value=1.0):
    """exact-colour image surface; optional rounded-corner mask in image pixels."""
    img = bpy.data.images.load(path, check_existing=True)
    m = bpy.data.materials.new(name)
    nb = NB(m)
    tex = nb.node("ShaderNodeTexImage", image=img, interpolation="Cubic")
    uvs = nb.uv()
    nb._in(tex.inputs["Vector"], uvs)
    a = tex.outputs["Alpha"]
    if corner_px:
        w, h = img.size
        # rounded-rect in pixel space
        s = nb.sep(uvs)
        px = nb.math("MULTIPLY", s[0], float(w))
        py = nb.math("MULTIPLY", s[1], float(h))
        qx = nb.math("SUBTRACT", nb.math("ABSOLUTE", nb.math("SUBTRACT", px, w / 2)), w / 2 - corner_px)
        qy = nb.math("SUBTRACT", nb.math("ABSOLUTE", nb.math("SUBTRACT", py, h / 2)), h / 2 - corner_px)
        outside = nb.vmath("LENGTH", nb.comb(nb.math("MAXIMUM", qx, 0.0), nb.math("MAXIMUM", qy, 0.0)), out=1)
        inside = nb.math("MINIMUM", nb.math("MAXIMUM", qx, qy), 0.0)
        d = nb.math("SUBTRACT", nb.math("ADD", outside, inside), float(corner_px))
        cm = nb.math("SUBTRACT", 1.0, nb.math("MULTIPLY", nb.math("ADD", d, 0.5), 1.0, clamp=True))
        a = nb.math("MULTIPLY", a, cm)
    a = nb.math("MULTIPLY", a, nb.value("Alpha", alpha_value))
    nb.emit_alpha(tex.outputs["Color"], 1.0, a)
    blended(m)
    return m, img


def plane_mesh(name, w, h):
    bm = bmesh.new()
    vs = [bm.verts.new((x, y, 0)) for x, y in ((-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2))]
    f = bm.faces.new(vs)
    uvl = bm.loops.layers.uv.new("UVMap")
    for loop, uv in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
        loop[uvl].uv = uv
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    return me


def obj(name, me, mat, collection, mtx=None):
    o = bpy.data.objects.new(name, me)
    collection.objects.link(o)
    if mat is not None:
        me.materials.append(mat)
    if mtx is not None:
        o.matrix_world = mtx
    o.visible_shadow = False
    return o


def image_plane(name, path, width, collection, corner_px=0):
    m, img = image_mat(name + "_Mat", path, corner_px)
    w, h = img.size
    return obj(name, plane_mesh(name, width, width * h / w), m, collection), m


FONTS = {}


def font(weight=300):
    if weight not in FONTS:
        FONTS[weight] = bpy.data.fonts.load(FONT_FILES[weight], check_existing=True)
    return FONTS[weight]


def text(name, body, size, collection, weight=300, hexc=None, align="LEFT", spacing=1.0, alpha=1.0, mtx=None):
    cu = bpy.data.curves.new(name, "FONT")
    cu.body = body
    cu.font = font(weight)
    cu.size = size
    cu.align_x = align
    cu.space_character = spacing
    m = flat_mat(name + "_Mat", hexc or C["text"], alpha)
    blended(m)
    cu.materials.append(m)
    o = bpy.data.objects.new(name, cu)
    collection.objects.link(o)
    if mtx is not None:
        o.matrix_world = mtx
    return o, m


# ---------------------------------------------------------------- cells
def cell_mesh(name, cells, attrs=()):
    """cells: list of dicts with x, y, z, s (size) and any attrs; one quad per cell with its own
    UV square (for the rounded-cell mask) and per-vertex float attributes (for in-shader timing)."""
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UVMap")
    layers = {a: bm.verts.layers.float.new(a) for a in attrs}
    for c in cells:
        x, y, z, s = c["x"], c["y"], c.get("z", 0.0), c["s"]
        h = s / 2
        vs = [bm.verts.new((x + dx, y + dy, z)) for dx, dy in ((-h, -h), (h, -h), (h, h), (-h, h))]
        for v in vs:
            for a, L in layers.items():
                v[L] = float(c.get(a, 0.0))
        f = bm.faces.new(vs)
        for loop, uv in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
            loop[uvl].uv = uv
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    return me


def key_time_value(sock, seconds, frame_start=1):
    """a Value node output animated linearly from 0 to `seconds` over the clip (the in-shader clock)."""
    sock.default_value = 0.0
    sock.keyframe_insert("default_value", frame=frame_start)
    sock.default_value = seconds
    sock.keyframe_insert("default_value", frame=frame_start + int(round(seconds * FPS)))
    set_linear(sock.id_data)


def set_linear(idblock, interp="LINEAR"):
    ad = idblock.animation_data
    if not ad or not ad.action:
        return
    for layer in ad.action.layers:
        for strip in layer.strips:
            for cb in strip.channelbags:
                for fc in cb.fcurves:
                    for kp in fc.keyframe_points:
                        kp.interpolation = interp
                    try:
                        fc.extrapolation = "LINEAR"
                    except Exception:
                        pass


# ---------------------------------------------------------------- the mark
# The VOID mark: 16 cells on a 7 x 7 lattice (3 top, 2 diagonal, 3 per side, 2 diagonal, 3 bottom),
# listed clockwise from the top-left of the top row — the build order the treatment asks for.
MARK_CELLS = [(2, 0), (3, 0), (4, 0), (5, 1), (6, 2), (6, 3), (6, 4), (5, 5), (4, 6), (3, 6), (2, 6), (1, 5),
              (0, 4), (0, 3), (0, 2), (1, 1)]


# ---------------------------------------------------------------- camera
def camera(name, collection, lens=50.0, clip_end=500.0, clip_start=0.05):
    cd = bpy.data.cameras.new(name)
    cd.lens = lens
    cd.sensor_width = 36.0
    cd.clip_start = clip_start
    cd.clip_end = clip_end
    o = bpy.data.objects.new(name, cd)
    collection.objects.link(o)
    bpy.context.scene.camera = o
    return o


def look_at(o, target, roll_deg=0.0, up=(0.0, 0.0, 1.0)):
    """explicit basis: camera -Z toward target, +Y toward `up` (falls back to +Y when looking along up)."""
    from mathutils import Quaternion
    f = (Vector(target) - Vector(o.location)).normalized()
    upv = Vector(up)
    if abs(f.dot(upv.normalized())) > 0.995:
        upv = Vector((0.0, 1.0, 0.0))
    zc = -f
    xc = upv.cross(zc).normalized()
    yc = zc.cross(xc)
    q = Matrix((xc, yc, zc)).transposed().to_quaternion()
    o.rotation_mode = "QUATERNION"
    o.rotation_quaternion = q @ Quaternion((0, 0, 1), math.radians(roll_deg))


def frames():
    sc = bpy.context.scene
    return range(sc.frame_start, sc.frame_end + 1)


def t_of(f):
    return (f - 1) / FPS


# ---------------------------------------------------------------- output
def render_frames(outdir, stills=None):
    sc = bpy.context.scene
    os.makedirs(outdir, exist_ok=True)
    if stills:
        for f in stills:
            sc.frame_set(f)
            sc.render.filepath = os.path.join(outdir, f"still_{f:04d}.png")
            bpy.ops.render.render(write_still=True)
    else:
        sc.render.filepath = os.path.join(outdir, "f_")
        bpy.ops.render.render(animation=True)


def save(path):
    try:
        bpy.ops.file.pack_all()
    except Exception as ex:
        print("[pack]", ex)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=path)
