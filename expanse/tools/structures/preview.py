"""Isometric previews of templates and assembled jigsaw structures.

Colours are averages of each block's texture, resolved through
blockstates -> models -> textures for both the vanilla assets ($MC_RES) and
the mod's own assets; entities (armor stands, paintings, frames, boats,
cushions) are drawn as simple boxes.
"""
import json
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOTS = {"minecraft": os.path.join(os.environ.get("MC_RES", "/root/mc/res"), "assets", "minecraft"),
         "expanse": os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "expanse"))}
_cache = {}

FOLIAGE = (89, 174, 48)
GRASS = (124, 189, 107)
TINTED = {"oak_leaves": FOLIAGE, "jungle_leaves": FOLIAGE, "acacia_leaves": FOLIAGE, "dark_oak_leaves": FOLIAGE,
          "mangrove_leaves": FOLIAGE, "vine": FOLIAGE, "spruce_leaves": (97, 153, 97), "birch_leaves": (128, 167, 85),
          "fern": GRASS, "short_grass": GRASS, "tall_grass": GRASS, "large_fern": GRASS, "lily_pad": (32, 128, 48),
          "sugar_cane": GRASS, "water": (60, 100, 220), "bush": GRASS}
FALLBACK = {"water": (50, 90, 210), "chest": (160, 110, 40), "trapped_chest": (160, 110, 40), "bell": (230, 190, 60),
            "spawner": (40, 50, 70), "jigsaw": (255, 0, 255)}


def _load_json(path):
    with open(path) as f:
        return json.load(f)


def _split(ref):
    ns, _, p = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    return ns, p


def _model_textures(model_ref):
    textures = {}
    ref = model_ref
    for _ in range(12):
        if ref is None:
            break
        ns, p = _split(ref)
        path = os.path.join(ROOTS.get(ns, ROOTS["minecraft"]), "models", p + ".json")
        if not os.path.exists(path):
            break
        m = _load_json(path)
        for k, v in m.get("textures", {}).items():
            textures.setdefault(k, v)
        ref = m.get("parent")
    for _ in range(5):
        for k, v in list(textures.items()):
            if isinstance(v, dict):
                v = textures[k] = v.get("sprite", v.get("texture"))
            if isinstance(v, str) and v.startswith("#"):
                textures[k] = textures.get(v[1:], v)
    return textures


def _avg(tex_ref):
    if isinstance(tex_ref, dict):
        tex_ref = tex_ref.get("sprite") or tex_ref.get("texture")
    if not isinstance(tex_ref, str) or tex_ref.startswith("#"):
        return None
    ns, p = _split(tex_ref)
    path = os.path.join(ROOTS.get(ns, ROOTS["minecraft"]), "textures", p + ".png")
    if not os.path.exists(path):
        return None
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    im = im.crop((0, 0, w, min(h, w)))
    tot = [0, 0, 0]
    n = 0
    for r, g, b, a in im.getdata():
        if a > 40:
            tot[0] += r
            tot[1] += g
            tot[2] += b
            n += 1
    return tuple(t // n for t in tot) if n else None


def block_colors(block_id):
    """(top_rgb, side_rgb) for a namespaced block id."""
    if block_id in _cache:
        return _cache[block_id]
    ns, name = block_id.split(":")
    top = side = None
    bs = os.path.join(ROOTS.get(ns, ROOTS["minecraft"]), "blockstates", name + ".json")
    if os.path.exists(bs):
        d = _load_json(bs)
        model = None
        if "variants" in d:
            v = next(iter(d["variants"].values()))
            model = (v[0] if isinstance(v, list) else v)["model"]
        elif "multipart" in d:
            a = d["multipart"][0]["apply"]
            model = (a[0] if isinstance(a, list) else a)["model"]
        t = _model_textures(model)
        for k in ("top", "end", "all", "texture", "cross", "plant", "pattern", "particle", "side", "wall", "lantern",
                  "torch", "rail", "front", "content", "fire", "layer0", "flowerpot"):
            if k in t and _avg(t[k]):
                top = _avg(t[k])
                break
        for k in ("side", "all", "texture", "cross", "plant", "wall", "particle", "front", "lantern", "torch", "top"):
            if k in t and _avg(t[k]):
                side = _avg(t[k])
                break
    if name.endswith("_bed") or name.endswith("_banner"):
        col = name.replace("_wall_banner", "").replace("_banner", "").replace("_bed", "")
        top = side = _avg(f"minecraft:block/{col}_wool") or (200, 50, 50)
    if name in FALLBACK:
        top = side = FALLBACK[name]
    top = top or side or (255, 0, 255)
    side = side or top
    if name in TINTED or name == "grass_block":
        tint = TINTED.get(name, GRASS)
        top = tuple(min(255, int(a * b / 255 * 1.3)) for a, b in zip(top, tint))
        if name != "grass_block":
            side = tuple(min(255, int(a * b / 255 * 1.3)) for a, b in zip(side, tint))
    _cache[block_id] = (top, side)
    return _cache[block_id]


# --------------------------------------------------------------- shapes
def shape_boxes(st):
    """List of (x0,y0,z0,x1,y1,z1) in 0..1 block units (preview approximation)."""
    s = st.short
    p = st.props
    if s in ("air", "structure_void"):
        return []
    if s.endswith("_slab"):
        t = p["type"]
        return [(0, 0, 0, 1, 1, 1)] if t == "double" else [(0, 0.5, 0, 1, 1, 1)] if t == "top" else [(0, 0, 0, 1, .5, 1)]
    if s.endswith("_stairs"):
        f = p["facing"]
        top = p["half"] == "top"
        base = (0, .5, 0, 1, 1, 1) if top else (0, 0, 0, 1, .5, 1)
        y0, y1 = (0, .5) if top else (.5, 1)
        back = {"north": (0, y0, 0, 1, y1, .5), "south": (0, y0, .5, 1, y1, 1), "east": (.5, y0, 0, 1, y1, 1),
                "west": (0, y0, 0, .5, y1, 1)}[f]
        return [base, back]
    if s.endswith("_carpet") or s in ("moss_carpet", "lily_pad", "leaf_litter", "pink_petals", "wildflowers"):
        return [(0, 0, 0, 1, .07, 1)]
    if s == "snow":
        return [(0, 0, 0, 1, int(p["layers"]) / 8, 1)]
    if s.endswith("_fence") or s.endswith("glass_pane") or s == "iron_bars":
        t = 0.06 if not s.endswith("_fence") else 0.125
        bx = [(.5 - t, 0, .5 - t, .5 + t, 1, .5 + t)]
        hy = (.35, .9) if s.endswith("_fence") else (0, 1)
        for d, b in (("north", (.5 - t, hy[0], 0, .5 + t, hy[1], .5)), ("south", (.5 - t, hy[0], .5, .5 + t, hy[1], 1)),
                     ("west", (0, hy[0], .5 - t, .5, hy[1], .5 + t)), ("east", (.5, hy[0], .5 - t, 1, hy[1], .5 + t))):
            if p.get(d) == "true":
                bx.append(b)
        return bx
    if s.endswith("_wall") and "up" in p:
        bx = [(.25, 0, .25, .75, 1, .75)] if p["up"] == "true" else []
        for d, b in (("north", (.31, 0, 0, .69, None, .5)), ("south", (.31, 0, .5, .69, None, 1)),
                     ("west", (0, 0, .31, .5, None, .69)), ("east", (.5, 0, .31, 1, None, .69))):
            if p.get(d) != "none":
                h = 1 if p[d] == "tall" else .875
                bx.append(b[:4] + (h,) + b[5:])
        return bx or [(.25, 0, .25, .75, 1, .75)]
    if s.endswith("_fence_gate"):
        return [(0, .3, .44, 1, .95, .56)] if p["facing"] in ("north", "south") else [(.44, .3, 0, .56, .95, 1)]
    if s.endswith("_door") or (s.endswith("_trapdoor") and p.get("open") == "true") or s in ("ladder",) or \
            s.endswith("wall_sign") or s.endswith("wall_hanging_sign") or s.endswith("_shelf"):
        f = p["facing"]
        th = .19 if not s.endswith("_shelf") else .45
        return [{"north": (0, 0, 1 - th, 1, 1, 1), "south": (0, 0, 0, 1, 1, th), "east": (0, 0, 0, th, 1, 1),
                 "west": (1 - th, 0, 0, 1, 1, 1)}[f]]
    if s.endswith("_trapdoor"):
        return [(0, .81, 0, 1, 1, 1)] if p["half"] == "top" else [(0, 0, 0, 1, .19, 1)]
    if s.endswith("_bed"):
        return [(0, 0, 0, 1, .56, 1)]
    if s in ("chest", "trapped_chest"):
        return [(.06, 0, .06, .94, .88, .94)]
    if "lantern" in s and "hanging" in p:
        return [(.31, .1, .31, .69, .6, .69)] if p["hanging"] == "true" else [(.31, 0, .31, .69, .5, .69)]
    if s in ("torch", "wall_torch", "soul_torch", "end_rod", "lightning_rod", "redstone_torch", "lever"):
        return [(.44, 0, .44, .56, .65, .56)]
    if s in ("campfire", "soul_campfire"):
        return [(0, 0, 0, 1, .44, 1)]
    if s == "bell":
        return [(.25, .1, .25, .75, .8, .75)]
    if s in ("vine", "glow_lichen"):
        for d, b in (("north", (0, 0, 0, 1, 1, .1)), ("south", (0, 0, .9, 1, 1, 1)), ("west", (0, 0, 0, .1, 1, 1)),
                     ("east", (.9, 0, 0, 1, 1, 1))):
            if p.get(d) == "true":
                return [b]
        return [(0, .9, 0, 1, 1, 1)]
    if s.endswith("_wall_banner"):
        f = p["facing"]
        return [{"north": (0, -.6, .9, 1, .9, 1), "south": (0, -.6, 0, 1, .9, .1), "east": (0, -.6, 0, .1, .9, 1),
                 "west": (.9, -.6, 0, 1, .9, 1)}[f]]
    if s.endswith("_banner"):
        return [(.1, 0, .45, .9, 1.8, .55)]
    if s.endswith("_sign"):
        return [(.1, .3, .45, .9, .95, .55), (.45, 0, .45, .55, .3, .55)]
    if s == "flower_pot" or s.startswith("potted_"):
        return [(.31, 0, .31, .69, .45, .69), (.38, .45, .38, .62, .8, .62)]
    if s == "iron_chain":
        return [(.44, 0, .44, .56, 1, .56)]
    if s in ("spanish_moss", "hanging_roots", "cobweb", "pale_hanging_moss") or s.endswith("_blossoms"):
        return [(.2, 0, .2, .8, 1, .8)]
    if s.endswith("_candle") or s == "candle":
        return [(.4, 0, .4, .6, .4, .6)]
    if s.endswith("_button") or s.endswith("_pressure_plate"):
        return [(.2, 0, .2, .8, .08, .8)]
    if s in ("lectern", "grindstone", "stonecutter", "anvil", "chipped_anvil", "damaged_anvil", "brewing_stand",
             "decorated_pot", "skeleton_skull", "enchanting_table"):
        return [(.1, 0, .1, .9, .8, .9)]
    if s in ("dirt_path", "farmland"):
        return [(0, 0, 0, 1, .94, 1)]
    if s == "water":
        return [(0, 0, 0, 1, .88, 1)]
    if s.endswith("_cluster"):
        return [(.2, 0, .2, .8, .7, .8)]
    from builder import is_full
    if not is_full(st):
        return [(.25, 0, .25, .75, .8, .75)]
    return [(0, 0, 0, 1, 1, 1)]


def entity_boxes(e):
    """World-space boxes + colour for an entity record {pos, nbt}."""
    n = e["nbt"]
    eid = n["id"].split(":")[1]
    x, y, z = e["pos"]
    if eid == "armor_stand":
        return [((x - .25, y, z - .25, x + .25, y + 1.9, z + .25), (190, 160, 110))]
    if eid in ("painting", "item_frame", "glow_item_frame"):
        return [((x - .45, y - .45, z - .45, x + .45, y + .45, z + .45), (150, 90, 160) if eid == "painting"
                 else (140, 100, 60))]
    if eid.endswith("_boat"):
        return [((x - .7, y, z - .7, x + .7, y + .5, z + .7), (120, 85, 50))]
    if eid == "cushion":
        return [((x - .4, y, z - .4, x + .4, y + .25, z + .4), (220, 120, 60))]
    return [((x - .3, y, z - .3, x + .3, y + 1, z + .3), (255, 0, 255))]


def render(cells, rot=0, tile=14, cut_y=None, title=None, entities=()):
    """cells: {(x,y,z): BlockState} (any coordinates). View from +x,+z, above; rot turns the view."""
    pts = [p for p, st in cells.items() if st.short not in ("air", "structure_void")]
    if not pts:
        return Image.new("RGB", (64, 64), (236, 238, 242))
    mnx, mny, mnz = (min(p[i] for p in pts) for i in range(3))
    mxx, mxy, mxz = (max(p[i] for p in pts) for i in range(3))
    sx, sy, sz = mxx - mnx + 1, mxy - mny + 1, mxz - mnz + 1

    def tr(X, Z):
        """Continuous view rotation of a point (X, Z) (relative to the bounds)."""
        X, Z = X - mnx, Z - mnz
        w, d = sx, sz
        for _ in range(rot % 4):
            X, Z = d - Z, X
            w, d = d, w
        return X, Z

    def tbox(x0, z0, x1, z1):
        a, c = tr(x0, z0), tr(x1, z1)
        return min(a[0], c[0]), min(a[1], c[1]), max(a[0], c[0]), max(a[1], c[1])

    rc = []
    for (x, y, z), st in cells.items():
        if st.short in ("air", "structure_void") or (cut_y is not None and y - mny > cut_y):
            continue
        top, side = block_colors(st.id)
        for (bx0, by0, bz0, bx1, by1, bz1) in shape_boxes(st):
            X0, Z0, X1, Z1 = tbox(x + bx0, z + bz0, x + bx1, z + bz1)
            rc.append(((X0, y - mny + by0, Z0, X1, y - mny + by1, Z1), top, side))
    for e in entities:
        if cut_y is not None and e["pos"][1] - mny > cut_y:
            continue
        for (x0, y0, z0, x1, y1, z1), col in entity_boxes(e):
            X0, Z0, X1, Z1 = tbox(x0, z0, x1, z1)
            rc.append(((X0, y0 - mny, Z0, X1, y1 - mny, Z1), col, col))
    w, d = (sx, sz) if rot % 2 == 0 else (sz, sx)
    hw, hh, vh = tile / 2.0, tile / 4.0, tile * 0.6
    W = int((w + d + 2) * hw + tile * 2)
    H = int((w + d + 2) * hh + (sy + 2) * vh + tile * 3 + 16)
    img = Image.new("RGB", (W, H), (236, 238, 242))
    dr = ImageDraw.Draw(img)
    ox = (d + 1) * hw + tile
    oy = (sy + 1) * vh + tile + 16

    def proj(x, y, z):
        return (ox + (x - z) * hw, oy + (x + z) * hh - y * vh)

    for gx in range(w + 1):
        dr.line([proj(gx, 0, 0), proj(gx, 0, d)], fill=(214, 216, 222))
    for gz in range(d + 1):
        dr.line([proj(0, 0, gz), proj(w, 0, gz)], fill=(214, 216, 222))

    def key(r):
        b = r[0]
        return (b[0] + b[1] + b[2] + (b[3] - b[0] + b[4] - b[1] + b[5] - b[2]) / 2, b[1], b[0])

    for b, top, side in sorted(rc, key=key):
        X0, Y0, Z0, X1, Y1, Z1 = b
        tface = [proj(X0, Y1, Z0), proj(X1, Y1, Z0), proj(X1, Y1, Z1), proj(X0, Y1, Z1)]
        sface = [proj(X0, Y0, Z1), proj(X1, Y0, Z1), proj(X1, Y1, Z1), proj(X0, Y1, Z1)]
        eface = [proj(X1, Y0, Z0), proj(X1, Y0, Z1), proj(X1, Y1, Z1), proj(X1, Y1, Z0)]
        for poly, col, k in ((sface, side, 0.78), (eface, side, 0.62), (tface, top, 1.0)):
            c = tuple(max(0, min(255, int(v * k))) for v in col)
            dr.polygon(poly, fill=c, outline=tuple(max(0, int(v * 0.55)) for v in c))
    if title:
        dr.text((4, 2), title, fill=(30, 30, 40))
    return img


def plan(cells, px=8, title="plan (N up)", entities=()):
    pts = [p for p, st in cells.items() if st.short not in ("air", "structure_void")]
    mnx, mnz = min(p[0] for p in pts), min(p[2] for p in pts)
    mxx, mxz = max(p[0] for p in pts), max(p[2] for p in pts)
    mny, mxy = min(p[1] for p in pts), max(p[1] for p in pts)
    img = Image.new("RGB", ((mxx - mnx + 1) * px + 2, (mxz - mnz + 1) * px + 18), (236, 238, 242))
    dr = ImageDraw.Draw(img)
    top = {}
    for (x, y, z), st in cells.items():
        if st.short in ("air", "structure_void"):
            continue
        if (x, z) not in top or y > top[(x, z)][0]:
            top[(x, z)] = (y, st)
    for (x, z), (y, st) in top.items():
        c = block_colors(st.id)[0]
        k = 0.5 + 0.5 * (y - mny + 1) / (mxy - mny + 1)
        c = tuple(max(0, min(255, int(v * k))) for v in c)
        X, Z = x - mnx, z - mnz
        dr.rectangle([1 + X * px, 17 + Z * px, X * px + px, 16 + Z * px + px], fill=c)
    dr.text((2, 2), title, fill=(30, 30, 40))
    return img


def grid(panels, cols=2):
    rows = [panels[i:i + cols] for i in range(0, len(panels), cols)]
    W = max(sum(p.width for p in r) for r in rows)
    H = sum(max(p.height for p in r) for r in rows)
    out = Image.new("RGB", (W, H), (255, 255, 255))
    yy = 0
    for r in rows:
        xx = 0
        for p in r:
            out.paste(p, (xx, yy))
            xx += p.width
        yy += max(p.height for p in r)
    return out


def build_cells(build):
    return {p: st for p, (st, _) in build.cells.items()}


def sheet_image(build, cuts=(), tile=14):
    cells = build_cells(build)
    ents = [{"pos": e["pos"], "nbt": e["nbt"]} for e in build.entities]
    panels = [render(cells, 0, tile, title=f"{build.name} SE {build.size}", entities=ents),
              render(cells, 2, tile, title="NW", entities=ents)]
    for cy, r in cuts:
        panels.append(render(cells, r, tile, cut_y=cy, title=f"cut y<={cy} rot{r}", entities=ents))
    panels.append(plan(cells, px=max(6, tile // 2)))
    return grid(panels, 2)


def stack(images, path):
    W = max(i.width for i in images)
    out = Image.new("RGB", (W, sum(i.height for i in images)), (255, 255, 255))
    y = 0
    for i in images:
        out.paste(i, (0, y))
        y += i.height
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path)
    return path
