"""Rebuild a VOID screen from its Figma extraction (figma/*.txt) as native, tactile Blender geometry.

1 Figma px = 1 mm. The screen is built in the local XY plane of a root empty (x right, y up,
+z toward the viewer). Every visible Figma node becomes its own object so it can be animated:
  - filled rectangles / frames  -> rounded-rect slabs with a rounded bevel (real thickness)
  - strokes                     -> a fine rim ring on the slab's top face
  - text                        -> Outfit text objects, raised a fraction of a millimetre
  - vectors                     -> SVG paths as filled, extruded curves
  - cell groups                 -> linked duplicates of one rounded cell mesh
Stacking follows Figma paint order on a heightmap: a node sits on the highest thing already
painted beneath it, so overlapping siblings never intersect.

Colour lives on the object (ob.color, linear RGBA), read by a handful of shared materials, so
each element's colour/alpha is individually animatable with a keyframe on ob.color.
"""
import bpy, bmesh, math, re, os, json
from mathutils import Vector, Matrix

HERE = os.path.dirname(os.path.abspath(__file__))
FONT_FILES = {"Light": "outfit-300.ttf", "Regular": "outfit-400.ttf", "Medium": "outfit-500.ttf"}
MM = 0.001


def lin1(v):
    return (v / 12.92) if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4


def hexlin(h):
    return tuple(lin1(int(h[i:i + 2], 16) / 255) for i in (0, 2, 4))


def parse_paint(s):
    """'edeeef:0.92+ffffff:0.1' -> [(lin_rgb, alpha)]"""
    out = []
    for p in (s or "").split("+"):
        if ":" in p:
            h, o = p.split(":")
            out.append((hexlin(h), float(o)))
    return out


# ---------------------------------------------------------------- parse
def load(path):
    nodes, cells = {}, []
    order = []
    for line in open(path, encoding="utf-8").read().split("\n"):
        if not line:
            continue
        k = line[0]
        if k == "N":
            f = line.split("|")
            idx = int(f[1])
            n = dict(idx=idx, parent=int(f[2]), depth=int(f[3]), type=f[4], name=f[5], x=float(f[6]), y=float(f[7]),
                     w=float(f[8]), h=float(f[9]), fills=parse_paint(f[10]), strokes=parse_paint(f[11]),
                     sw=f[12], cr=f[13], op=float(f[14]), clip=int(f[15]), rot=float(f[16]), children=[])
            nodes[idx] = n
            order.append(("N", idx))
        elif k == "T":
            f = line.split("|", 5)
            idx = int(f[1])
            nodes[idx]["align"] = (f[2], f[3])
            nodes[idx]["text"] = json.loads(f[4])
            segs = []
            for sg in f[5].split(";"):
                a = sg.split(",")
                segs.append(dict(a=int(a[0]), b=int(a[1]), style=a[2], size=float(a[3]), ls=float(a[4]), lh=float(a[5]),
                                 fill=parse_paint(a[6]), case=a[7]))
            nodes[idx]["segs"] = segs
        elif k == "V":
            f = line.split("|", 2)
            nodes[int(f[1])]["svg"] = f[2]
        elif k == "C":
            f = line.split("|")
            parent, w, h, cr, hexc = int(f[1]), float(f[2]), float(f[3]), f[4], f[5]
            pts = []
            for t in f[6].split(";"):
                x, y, o = t.split(",")
                pts.append((float(x), float(y), float(o)))
            cells.append(dict(parent=parent, w=w, h=h, cr=float(cr.split("/")[0]), col=hexlin(hexc), pts=pts))
            order.append(("C", len(cells) - 1))
    for n in nodes.values():
        if n["parent"] >= 0:
            nodes[n["parent"]]["children"].append(n["idx"])
    return nodes, cells, order


# ---------------------------------------------------------------- materials (shared, colour from the object)
_MATS = {}


def ui_material(kind, emission=0.9, rough=0.46, spec=0.28, coat=0.0, diffuse=0.18):
    """kind: 'solid' | 'alpha'. Base colour and alpha come from Object Info > Color.
    A share of the colour is emitted so the design's tones survive the lighting; the rest is
    real shading, which is what gives the surfaces their tactile edge light and contact shadow."""
    key = (kind, emission, rough, spec, coat, diffuse)
    if key in _MATS:
        return _MATS[key]
    m = bpy.data.materials.new(f"UI_{kind}_{emission}_{rough}")
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        if n.type != "OUTPUT_MATERIAL":
            nt.nodes.remove(n)
    oi = nt.nodes.new("ShaderNodeObjectInfo")
    P = nt.nodes.new("ShaderNodeBsdfPrincipled")
    P.inputs["Roughness"].default_value = rough
    P.inputs["Specular IOR Level"].default_value = spec
    if coat:
        P.inputs["Coat Weight"].default_value = coat
        P.inputs["Coat Roughness"].default_value = 0.12
    dim = nt.nodes.new("ShaderNodeVectorMath")        # diffuse share: shading, not repainting
    dim.operation = "SCALE"
    dim.inputs["Scale"].default_value = diffuse
    nt.links.new(oi.outputs["Color"], dim.inputs[0])
    nt.links.new(dim.outputs[0], P.inputs["Base Color"])
    nt.links.new(oi.outputs["Color"], P.inputs["Emission Color"])
    P.inputs["Emission Strength"].default_value = emission
    out = nt.nodes["Material Output"]
    if kind == "alpha":
        nt.links.new(oi.outputs["Alpha"], P.inputs["Alpha"])
        m.surface_render_method = "BLENDED"
        m.use_backface_culling = True
        try:
            m.use_transparency_overlap = False
            m.show_transparent_back = False
        except Exception:
            pass
    nt.links.new(P.outputs[0], out.inputs["Surface"])
    _MATS[key] = m
    return m


# ---------------------------------------------------------------- geometry
def rrect_pts(w, h, r, seg=8):
    """rounded rectangle centred at the origin, counter-clockwise"""
    r = max(0.0, min(r, w / 2 - 1e-6, h / 2 - 1e-6))
    if r <= 1e-6:
        return [(-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)]
    pts = []
    for cx, cy, a0 in ((w / 2 - r, -h / 2 + r, -90), (w / 2 - r, h / 2 - r, 0), (-w / 2 + r, h / 2 - r, 90),
                       (-w / 2 + r, -h / 2 + r, 180)):
        for k in range(seg + 1):
            a = math.radians(a0 + 90 * k / seg)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def slab_curve(name, w, h, r, thick, bevel, holes=()):
    """rounded-rect slab (mm units in, metres out) with a rounded bevel; outline compensated for the bevel."""
    b = max(0.0, min(bevel, thick * 0.45, w * 0.3, h * 0.3))
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "2D"
    cu.fill_mode = "BOTH"
    cu.extrude = max(0.0, thick / 2 - b) * MM
    cu.bevel_depth = b * MM
    cu.bevel_resolution = 3
    cu.resolution_u = 1
    loops = [rrect_pts(w - 2 * b, h - 2 * b, r - b)] + [rrect_pts(*hh) for hh in holes]
    for loop in loops:
        sp = cu.splines.new("POLY")
        sp.points.add(len(loop) - 1)
        for p, (x, y) in zip(sp.points, loop):
            p.co = (x * MM, y * MM, 0.0, 1.0)
        sp.use_cyclic_u = True
        sp.use_smooth = True
    return cu


def ring_curve(name, w, h, r, width, thick=0.12):
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "2D"
    cu.fill_mode = "BOTH"
    cu.extrude = thick / 2 * MM
    for loop in (rrect_pts(w, h, r), rrect_pts(w - 2 * width, h - 2 * width, max(0, r - width))[::-1]):
        sp = cu.splines.new("POLY")
        sp.points.add(len(loop) - 1)
        for p, (x, y) in zip(sp.points, loop):
            p.co = (x * MM, y * MM, 0.0, 1.0)
        sp.use_cyclic_u = True
    return cu


_TOK = re.compile(r"[MmLlHhVvCcZzSsQq]|-?\d*\.?\d+(?:e-?\d+)?")


def svg_paths(svg):
    """minimal absolute/relative M L H V C Z path parser -> list of closed polylines (svg units), + fill hex"""
    polys = []
    for m in re.finditer(r'<path[^>]*d="([^"]+)"[^>]*>', svg):
        d = m.group(1)
        toks = _TOK.findall(d)
        i, cmd = 0, None
        cur, start, poly = Vector((0, 0)), Vector((0, 0)), []

        def num():
            nonlocal i
            v = float(toks[i]); i += 1
            return v
        while i < len(toks):
            t = toks[i]
            if re.match(r"[A-Za-z]", t):
                cmd = t; i += 1
                if cmd in "Zz":
                    if poly:
                        polys.append(poly)
                    poly = []; cur = start.copy()
                    continue
            rel = cmd.islower()
            c = cmd.upper()
            if c == "M":
                p = Vector((num(), num())); cur = cur + p if rel else p; start = cur.copy()
                if poly:
                    polys.append(poly)
                poly = [cur.copy()]; cmd = "l" if rel else "L"
            elif c == "L":
                p = Vector((num(), num())); cur = cur + p if rel else p; poly.append(cur.copy())
            elif c == "H":
                x = num(); cur = Vector((cur.x + x if rel else x, cur.y)); poly.append(cur.copy())
            elif c == "V":
                y = num(); cur = Vector((cur.x, cur.y + y if rel else y)); poly.append(cur.copy())
            elif c == "C":
                p1 = Vector((num(), num())); p2 = Vector((num(), num())); p3 = Vector((num(), num()))
                if rel:
                    p1, p2, p3 = cur + p1, cur + p2, cur + p3
                for k in range(1, 9):
                    t_ = k / 8
                    poly.append((1 - t_) ** 3 * cur + 3 * (1 - t_) ** 2 * t_ * p1 + 3 * (1 - t_) * t_ ** 2 * p2 + t_ ** 3 * p3)
                cur = p3
            else:
                i += 1
        if poly:
            polys.append(poly)
    return polys


# ---------------------------------------------------------------- style
DEFAULT_STYLE = dict(
    root_thick=16.0, root_bevel=3.0,
    panel_thick=2.2, card_thick=3.2, button_thick=5.0, cell_thick=0.9, overlay_thick=0.35,
    text_thick=0.35, bevel=0.9, emission=0.9, rough=0.46,
)


def classify_thick(n, S):
    fills = n["fills"]
    if n["idx"] == 0:
        return S["root_thick"]
    if not fills:
        return 0.0
    col, a = fills[0]
    if a < 0.5:
        return S["overlay_thick"]
    if min(col) > 0.6:                   # white buttons and pills
        return S["button_thick"]
    if n["w"] < 60 and n["h"] < 60:
        return S["card_thick"]
    lum = sum(col) / 3
    return S["card_thick"] if lum > lin1(0x17 / 255) else S["panel_thick"]


# ---------------------------------------------------------------- build
class Screen:
    def __init__(self, path, coll, name="Screen", style=None, font_scale=None):
        self.S = dict(DEFAULT_STYLE, **(style or {}))
        self.coll = coll
        self.nodes, self.cells, self.order = load(path)
        root = self.nodes[0]
        self.W, self.H = root["w"], root["h"]
        self.root = bpy.data.objects.new(name, None)
        self.root.empty_display_size = 0.2
        coll.objects.link(self.root)
        self.objs = {}           # idx -> dict(slab, rim, text, ...)
        self.cell_objs = []      # list of lists per cell group
        self.placed = []         # (x0, y0, x1, y1, top_mm)
        self.mat_solid = ui_material("solid", self.S["emission"], self.S["rough"])
        self.mat_alpha = ui_material("alpha", self.S["emission"], self.S["rough"])
        self.mat_glossy = ui_material("solid", self.S["emission"] * 0.8, 0.28, spec=0.5, coat=0.4)
        self._fonts = {}
        self.build()

    # figma (x, y) top-left px -> local centre in metres
    def centre(self, x, y, w, h):
        return Vector(((x + w / 2 - self.W / 2) * MM, (self.H / 2 - (y + h / 2)) * MM, 0.0))

    def base_z(self, x, y, w, h, floor):
        top = floor
        for (a0, b0, a1, b1, t) in self.placed:
            if a0 < x + w - 0.5 and x + 0.5 < a1 and b0 < y + h - 0.5 and y + 0.5 < b1:
                top = max(top, t)
        return top

    def add(self, name, data, mat, loc_mm_z, x, y, w, h, color, alpha, parent_obj=None):
        ob = bpy.data.objects.new(name, data)
        self.coll.objects.link(ob)
        if mat is not None and hasattr(data, "materials"):
            data.materials.append(mat)
        c = self.centre(x, y, w, h)
        ob.location = (c.x, c.y, loc_mm_z * MM)
        ob.color = (*color, alpha)
        ob.parent = parent_obj or self.root
        return ob

    def font(self, style):
        f = FONT_FILES.get(style, FONT_FILES["Regular"])
        if f not in self._fonts:
            self._fonts[f] = bpy.data.fonts.load(os.path.join(HERE, "fonts", f), check_existing=True)
        return self._fonts[f]

    def build(self):
        tops = {}
        self.pass_ = "slabs"
        for kind, i in self.order:
            if kind == "N":
                self.build_node(self.nodes[i], tops)
        self.pass_ = "cells"
        for kind, i in self.order:
            if kind == "C":
                self.build_cells(i, tops)
        self.pass_ = "ink"
        for kind, i in self.order:
            if kind == "N" and (self.nodes[i]["type"] == "TEXT" or "svg" in self.nodes[i]):
                self.build_node(self.nodes[i], tops)
        self.tops = tops
        self.hide_ghosts()

    def is_ghost(self, idx):
        while idx >= 0:
            n = self.nodes[idx]
            if n["op"] < 0.5:
                return True
            idx = n["parent"]
        return False

    def build_node(self, n, tops):
        S = self.S
        idx, p = n["idx"], n["parent"]
        ghost = self.is_ghost(idx)
        parent_top = tops.get(p, 0.0)
        is_ink = n["type"] == "TEXT" or "svg" in n
        if self.pass_ == "slabs" and is_ink:
            # reserve the group now so children order is stable; geometry comes in the ink pass
            pass
        if self.pass_ == "ink":
            entry = self.objs[idx]
        else:
            pobj = self.objs.get(p, {}).get("group") if p >= 0 else None
            # every node gets a group empty (so a whole subtree animates together)
            grp = bpy.data.objects.new(f"{idx:03d}_{n['name']}", None)
            grp.empty_display_size = 0.01
            self.coll.objects.link(grp)
            grp.parent = pobj or self.root
            grp.location = (0, 0, 0)
            entry = dict(node=n, group=grp)
            self.objs[idx] = entry
            if is_ink:
                return
        grp = entry["group"]
        x, y, w, h = n["x"], n["y"], n["w"], n["h"]
        t = classify_thick(n, S) if n["type"] in ("FRAME", "RECTANGLE", "INSTANCE", "COMPONENT", "ELLIPSE") else 0.0
        floor = parent_top if idx else 0.0
        if n["type"] == "TEXT":
            z0 = self.base_z(x, y, w, h, floor)
            self.build_text(n, entry, z0)
            tops[idx] = z0 + S["text_thick"]
            if not ghost:
                self.placed.append((x, y, x + w, y + h, tops[idx]))
            return
        if "svg" in n:
            z0 = self.base_z(x, y, w, h, floor)
            self.build_vector(n, entry, z0)
            tops[idx] = z0 + 0.6
            if not ghost:
                self.placed.append((x, y, x + w, y + h, tops[idx]))
            return
        z0 = self.base_z(x, y, w, h, floor) if idx else -S["root_thick"]
        if t > 0 and n["fills"]:
            col, a = n["fills"][0]
            r = float(n["cr"].split("/")[0]) if n["cr"] else 0.0
            bev = S["root_bevel"] if idx == 0 else min(S["bevel"], t * 0.45)
            cu = slab_curve(f"{idx:03d}_{n['name']}_slab", w, h, r, t, bev)
            mat = self.mat_alpha if a < 0.999 else (self.mat_glossy if min(col) > 0.6 else self.mat_solid)
            ob = self.add(f"{idx:03d}_{n['name']}", cu, mat, z0 + t / 2, x, y, w, h, col, a, grp)
            entry["slab"] = ob
            entry["thick"] = t
            if n["strokes"]:
                scol, sa = n["strokes"][0]
                sw = float(n["sw"].split(":")[0]) if n["sw"] else 1.0
                bev_in = min(bev, t * 0.45) if idx else 0.0
                rc = ring_curve(f"{idx:03d}_rim", w - 2 * bev_in, h - 2 * bev_in, max(0, r - bev_in), sw)
                rim = self.add(f"{idx:03d}_{n['name']}_rim", rc, self.mat_alpha, z0 + t + 0.06, x, y, w, h, scol, sa, grp)
                entry["rim"] = rim
            top = z0 + t if idx else 0.0
        else:
            top = floor if idx else 0.0
        tops[idx] = top
        if idx and t > 0 and not ghost:
            self.placed.append((x, y, x + w, y + h, top))
        if n["op"] < 0.999:
            grp.hide_render = False
            entry["opacity"] = n["op"]

    def build_text(self, n, entry, z0):
        S = self.S
        seg = n["segs"][0]
        cu = bpy.data.curves.new(f"{n['idx']:03d}_txt", "FONT")
        body = n["text"]
        if seg["case"] == "U":
            body = body.upper()
        cu.body = body
        cu.font = self.font(seg["style"])
        size = seg["size"]
        cu.size = size * MM
        cu.space_character = 1.0 + seg["ls"] / (0.57 * size)
        cu.extrude = S["text_thick"] / 2 * MM
        cu.align_x = {"L": "LEFT", "C": "CENTER", "R": "RIGHT"}.get(n["align"][0], "LEFT")
        col, a = (seg["fill"][0] if seg["fill"] else n["fills"][0])
        # baseline: 1 em below the box top (Outfit ascender = 1000/1000)
        lh = seg["lh"] or 1.26 * size
        base = n["y"] + (lh - 1.26 * size) / 2 + 1.0 * size
        ob = bpy.data.objects.new(f"{n['idx']:03d}_{n['name']}", cu)
        self.coll.objects.link(ob)
        mat = self.mat_alpha if a < 0.999 else self.mat_solid
        cu.materials.append(mat)
        ax = n["x"] if cu.align_x == "LEFT" else (n["x"] + n["w"] / 2 if cu.align_x == "CENTER" else n["x"] + n["w"])
        ob.location = ((ax - self.W / 2) * MM, (self.H / 2 - base) * MM, (z0 + S["text_thick"] / 2) * MM)
        ob.color = (*col, a)
        ob.parent = entry["group"]
        entry["text"] = ob

    def build_vector(self, n, entry, z0):
        polys = svg_paths(n["svg"])
        m = re.search(r'viewBox="([\d.\- ]+)"', n["svg"])
        vb = [float(v) for v in m.group(1).split()] if m else [0, 0, n["w"], n["h"]]
        sx, sy = n["w"] / vb[2], n["h"] / vb[3]
        fm = re.search(r'fill="#([0-9A-Fa-f]{6})"', n["svg"].split("<path", 1)[-1])
        col = hexlin(fm.group(1).lower()) if fm else (n["fills"][0][0] if n["fills"] else (1, 1, 1))
        cu = bpy.data.curves.new(f"{n['idx']:03d}_vec", "CURVE")
        cu.dimensions = "2D"
        cu.fill_mode = "BOTH"
        cu.extrude = 0.3 * MM
        cu.bevel_depth = 0.15 * MM
        for poly in polys:
            sp = cu.splines.new("POLY")
            sp.points.add(len(poly) - 1)
            for p, v in zip(sp.points, poly):
                p.co = ((v.x * sx - n["w"] / 2) * MM, (n["h"] / 2 - v.y * sy) * MM, 0.0, 1.0)
            sp.use_cyclic_u = True
        ob = self.add(f"{n['idx']:03d}_{n['name']}", cu, self.mat_solid, z0 + 0.35, n["x"], n["y"], n["w"], n["h"], col, 1.0,
                      entry["group"])
        entry["vector"] = ob

    def build_cells(self, ci, tops):
        S = self.S
        g = self.cells[ci]
        p = g["parent"]
        pentry = self.objs.get(p)
        pgroup = pentry["group"] if pentry else self.root
        floor = tops.get(p, 0.0)
        big = g["w"] > 40 and g["h"] > 30 and g["col"][0] > 0.6 and g["pts"][0][2] > 0.9
        t = S["button_thick"] if big else (S["cell_thick"] if g["w"] <= 60 else S["overlay_thick"])
        r = g["cr"]
        me_cu = slab_curve(f"cell_{ci}", g["w"], g["h"], r, t, min(S["bevel"] * 0.6, t * 0.4, g["w"] * 0.2))
        dg = bpy.context.evaluated_depsgraph_get()
        tmp = bpy.data.objects.new("_tmp", me_cu)
        bpy.context.scene.collection.objects.link(tmp)
        dg.update()
        me = bpy.data.meshes.new_from_object(tmp.evaluated_get(dg))
        bpy.data.objects.remove(tmp)
        me.name = f"cell_{ci}"
        objs = []
        for j, (x, y, o) in enumerate(g["pts"]):
            z0 = self.base_z(x, y, g["w"], g["h"], floor)
            mat = self.mat_glossy if big else (self.mat_alpha if o < 0.999 else self.mat_solid)
            ob = bpy.data.objects.new(f"C{ci:02d}_{j:03d}", me)
            self.coll.objects.link(ob)
            if not me.materials:
                me.materials.append(mat)
            ob.material_slots[0].link = "OBJECT"
            ob.material_slots[0].material = mat
            c = self.centre(x, y, g["w"], g["h"])
            ob.location = (c.x, c.y, (z0 + t / 2) * MM)
            ob.color = (*g["col"], o)
            ob.parent = pgroup
            objs.append(ob)
        self.cell_objs.append(dict(group=g, objs=objs, thick=t))
        ghost = p >= 0 and self.is_ghost(p)
        self.cell_objs[-1]["ghost"] = ghost
        for (x, y, o) in g["pts"]:
            if t >= S["cell_thick"] and o > 0.5 and not ghost:
                self.placed.append((x, y, x + g["w"], y + g["h"], self.base_z(x, y, g["w"], g["h"], floor) + t))

    def hide_ghosts(self):
        """opacity-0 states in the file (dropdowns, hovers) start hidden; animation reveals them."""
        self.ghost_roots = []
        for idx, e in self.objs.items():
            n = e["node"]
            if n["op"] < 0.5 and not (n["parent"] >= 0 and self.is_ghost(n["parent"])):
                self.ghost_roots.append(idx)
                for ob in [e["group"]] + list(e["group"].children_recursive):
                    ob.hide_render = True
                    ob.hide_viewport = True

    # helpers for animation code
    def find(self, name):
        return [e for e in self.objs.values() if e["node"]["name"] == name]
