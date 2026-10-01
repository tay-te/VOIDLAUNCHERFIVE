"""Quick isometric previews of structure templates (for eyeballing designs).

Colours come from the average of each block's vanilla texture (resolved via
blockstates -> model -> textures); mod blocks use a hand-picked table since
their textures are generated elsewhere.
"""
import json
import os

from PIL import Image, ImageDraw

ASSETS = "/root/mc/res/assets/minecraft"
_cache = {}

FOLIAGE = (89, 174, 48)
GRASS = (124, 189, 107)
TINTED = {"oak_leaves": FOLIAGE, "jungle_leaves": FOLIAGE, "acacia_leaves": FOLIAGE, "dark_oak_leaves": FOLIAGE,
          "mangrove_leaves": FOLIAGE, "vine": FOLIAGE, "spruce_leaves": (97, 153, 97), "birch_leaves": (128, 167, 85),
          "fern": GRASS, "short_grass": GRASS, "tall_grass": GRASS, "large_fern": GRASS, "lily_pad": (32, 128, 48),
          "sugar_cane": GRASS}

MOD_COLORS = {
    "redwood_log": (92, 45, 34), "redwood_wood": (92, 45, 34), "stripped_redwood": (168, 84, 60),
    "redwood_planks": (155, 74, 54), "redwood_leaves": (63, 107, 52),
    "willow_log": (88, 84, 70), "willow_wood": (88, 84, 70), "stripped_willow": (160, 160, 120),
    "willow_planks": (146, 150, 104), "willow_leaves": (141, 176, 90),
    "palm_log": (150, 130, 100), "palm_wood": (150, 130, 100), "stripped_palm": (214, 190, 140),
    "palm_planks": (210, 182, 128), "palm_leaves": (94, 158, 58),
    "lumen_log": (70, 90, 120), "lumen_wood": (70, 90, 120), "stripped_lumen": (150, 210, 220),
    "lumen_planks": (120, 190, 205), "lumen_leaves": (95, 230, 210),
    "baobab_log": (140, 120, 110), "baobab_wood": (140, 120, 110), "stripped_baobab": (200, 150, 140),
    "baobab_planks": (190, 140, 130), "baobab_leaves": (126, 148, 64),
    "wisteria_log": (95, 85, 90), "wisteria_wood": (95, 85, 90), "stripped_wisteria": (175, 140, 185),
    "wisteria_planks": (170, 135, 180), "wisteria_leaves": (180, 135, 224), "azure_wisteria_leaves": (134, 169, 238),
    "limestone": (222, 214, 190), "polished_limestone": (230, 224, 204), "limestone_brick": (214, 205, 180),
    "chiseled_limestone": (226, 218, 196), "mossy_limestone": (170, 180, 130),
    "opal_sand": (233, 198, 214), "opal_sandstone": (226, 186, 204), "smooth_opal_sandstone": (234, 200, 216),
    "cut_opal_sandstone": (222, 182, 200), "chiseled_opal_sandstone": (214, 170, 196),
    "prismite": (120, 230, 240), "heather": (170, 90, 190), "edelweiss": (240, 240, 230), "frostbloom": (160, 220, 255),
    "glowcap": (90, 240, 200), "cattail": (110, 80, 50), "spanish_moss": (150, 170, 120),
}


def _load_json(path):
    with open(path) as f:
        return json.load(f)


def _model_textures(model_ref):
    textures = {}
    ref = model_ref
    for _ in range(12):
        if ref is None:
            break
        p = ref.split(":")[-1]
        path = os.path.join(ASSETS, "models", p + ".json")
        if not os.path.exists(path):
            break
        m = _load_json(path)
        for k, v in m.get("textures", {}).items():
            textures.setdefault(k, v)
        ref = m.get("parent")
    # resolve '#ref'
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
    p = os.path.join(ASSETS, "textures", tex_ref.split(":")[-1] + ".png")
    if not os.path.exists(p):
        return None
    im = Image.open(p).convert("RGBA")
    w, h = im.size
    im = im.crop((0, 0, w, min(h, w)))  # animated strips: first frame
    px = list(im.getdata())
    tot = [0, 0, 0]
    n = 0
    for r, g, b, a in px:
        if a > 40:
            tot[0] += r
            tot[1] += g
            tot[2] += b
            n += 1
    if n == 0:
        return None
    return tuple(t // n for t in tot)


def block_colors(block_id):
    """(top_rgb, side_rgb) for a namespaced block id."""
    if block_id in _cache:
        return _cache[block_id]
    ns_, name = block_id.split(":")
    res = None
    if ns_ == "expanse":
        key = name
        for k in sorted(MOD_COLORS, key=len, reverse=True):
            if k in key:
                c = MOD_COLORS[k]
                res = (c, c)
                break
        if res is None:
            for w in ("redwood", "willow", "palm", "lumen", "baobab", "wisteria"):
                if w in key:
                    c = MOD_COLORS[w + "_planks"]
                    res = (c, c)
            for st in ("limestone_brick", "limestone", "opal_sandstone"):
                if res is None and st in key:
                    c = MOD_COLORS[st]
                    res = (c, c)
        if res is None:
            res = ((255, 0, 255), (255, 0, 255))
    else:
        bs = os.path.join(ASSETS, "blockstates", name + ".json")
        top = side = None
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
        if name.endswith("_bed"):
            col = name[:-4]
            c = _avg(f"minecraft:block/{col}_wool") or (200, 50, 50)
            top = side = c
        if name.endswith("_banner"):
            col = name.replace("_wall_banner", "").replace("_banner", "")
            c = _avg(f"minecraft:block/{col}_wool") or (200, 200, 200)
            top = side = c
        if name in ("chest", "trapped_chest"):
            top = side = (160, 110, 40)
        if name == "bell":
            top = side = (230, 190, 60)
        if name == "water_cauldron":
            top = (60, 90, 200)
        top = top or side or (255, 0, 255)
        side = side or top
        if name in TINTED or name == "grass_block":
            tint = TINTED.get(name, GRASS)
            top = tuple(min(255, int(a * b / 255 * 1.3)) for a, b in zip(top, tint))
            if name != "grass_block":
                side = tuple(min(255, int(a * b / 255 * 1.3)) for a, b in zip(side, tint))
        res = (top, side)
    _cache[block_id] = res
    return res


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
    if s.endswith("_carpet") or s == "moss_carpet":
        return [(0, 0, 0, 1, .07, 1)]
    if s == "snow":
        h = int(p["layers"]) / 8
        return [(0, 0, 0, 1, h, 1)]
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
    if s.endswith("_door"):
        f = p["facing"]
        return [{"north": (0, 0, .81, 1, 1, 1), "south": (0, 0, 0, 1, 1, .19), "east": (0, 0, 0, .19, 1, 1),
                 "west": (.81, 0, 0, 1, 1, 1)}[f]]
    if s.endswith("_trapdoor"):
        if p["open"] == "true":
            f = p["facing"]
            return [{"north": (0, 0, .81, 1, 1, 1), "south": (0, 0, 0, 1, 1, .19), "east": (0, 0, 0, .19, 1, 1),
                     "west": (.81, 0, 0, 1, 1, 1)}[f]]
        return [(0, .81, 0, 1, 1, 1)] if p["half"] == "top" else [(0, 0, 0, 1, .19, 1)]
    if s.endswith("_bed"):
        return [(0, 0, 0, 1, .56, 1)]
    if s in ("chest", "trapped_chest"):
        return [(.06, 0, .06, .94, .88, .94)]
    if s in ("lantern", "soul_lantern"):
        return [(.31, .1, .31, .69, .6, .69)] if p["hanging"] == "true" else [(.31, 0, .31, .69, .5, .69)]
    if s in ("torch", "wall_torch", "soul_torch", "end_rod", "lightning_rod"):
        return [(.44, 0, .44, .56, .65, .56)]
    if s in ("campfire", "soul_campfire"):
        return [(0, 0, 0, 1, .44, 1)]
    if s == "bell":
        return [(.25, .1, .25, .75, .8, .75)]
    if s in ("ladder", "vine", "glow_lichen"):
        f = p.get("facing", "north")
        return [{"north": (0, 0, .88, 1, 1, 1), "south": (0, 0, 0, 1, 1, .12), "east": (0, 0, 0, .12, 1, 1),
                 "west": (.88, 0, 0, 1, 1, 1)}.get(f, (0, 0, 0, 1, 1, .1))]
    if s.endswith("_wall_banner"):
        f = p["facing"]
        return [{"north": (0, -.6, .9, 1, .9, 1), "south": (0, -.6, 0, 1, .9, .1), "east": (0, -.6, 0, .1, .9, 1),
                 "west": (.9, -.6, 0, 1, .9, 1)}[f]]
    if s.endswith("_banner"):
        return [(.1, 0, .45, .9, 1.8, .55)]
    if s == "flower_pot" or s.startswith("potted_"):
        return [(.31, 0, .31, .69, .45, .69), (.38, .45, .38, .62, .8, .62)]
    if s in ("iron_chain",):
        return [(.44, 0, .44, .56, 1, .56)]
    if s in ("spanish_moss", "hanging_roots", "cobweb"):
        return [(.2, 0, .2, .8, 1, .8)]
    if s.endswith("_candle") or s == "candle":
        return [(.4, 0, .4, .6, .4, .6)]
    if s.endswith("_button") or s.endswith("_pressure_plate"):
        return [(.2, 0, .2, .8, .08, .8)]
    if s in ("lectern", "grindstone", "stonecutter", "anvil", "chipped_anvil", "damaged_anvil", "brewing_stand",
             "decorated_pot", "skeleton_skull"):
        return [(.1, 0, .1, .9, .8, .9)]
    if s == "dirt_path" or s == "farmland":
        return [(0, 0, 0, 1, .94, 1)]
    if s in ("lily_pad", "leaf_litter", "pink_petals", "wildflowers"):
        return [(0, 0, 0, 1, .05, 1)]
    if s.endswith("_cluster"):
        return [(.2, 0, .2, .8, .7, .8)]
    from builder import is_full
    if not is_full(st):  # plants & misc
        return [(.25, 0, .25, .75, .8, .75)]
    return [(0, 0, 0, 1, 1, 1)]


def _rot_state_view(cells, size, rot):
    """Rotate the grid by rot*90 degrees around Y (for viewing from other sides)."""
    sx, sy, sz = size
    out = {}
    for (x, y, z), st in cells.items():
        for _ in range(rot):
            x, z = sz - 1 - z, x
            sx, sz = sz, sx
        sx, sy, sz = size
        out[(x, y, z)] = st
    nsx, nsz = (sx, sz) if rot % 2 == 0 else (sz, sx)
    return out, (nsx, sy, nsz)


def _rotate_box(b, rot):
    x0, y0, z0, x1, y1, z1 = b
    for _ in range(rot):
        # (x,z) -> (1-z, x)
        x0, z0, x1, z1 = 1 - z1, x0, 1 - z0, x1
    return (x0, y0, z0, x1, y1, z1)


def render(cells, size, rot=0, tile=14, cut_y=None, title=None):
    """cells: {(x,y,z): BlockState}. Returns a PIL image (view from +x,+z, above)."""
    sx, sy, sz = size
    rc = {}
    for (x, y, z), st in cells.items():
        if st.short in ("air", "structure_void"):
            continue
        if cut_y is not None and y > cut_y:
            continue
        xx, zz = x, z
        w, d = sx, sz
        for _ in range(rot):
            xx, zz = d - 1 - zz, xx
            w, d = d, w
        rc[(xx, y, zz)] = st
    w, d = (sx, sz) if rot % 2 == 0 else (sz, sx)
    hw = tile / 2.0        # half width of a cube's top rhombus
    hh = tile / 4.0        # half height of the rhombus
    vh = tile * 0.6        # vertical edge length
    W = int((w + d) * hw + tile * 2)
    H = int((w + d) * hh + sy * vh + tile * 3 + (16 if title else 0))
    img = Image.new("RGB", (W, H), (236, 238, 242))
    dr = ImageDraw.Draw(img)
    ox = d * hw + tile
    oy = sy * vh + tile + (16 if title else 0)

    def proj(x, y, z):
        return (ox + (x - z) * hw, oy + (x + z) * hh - y * vh)

    # ground grid
    for gx in range(w + 1):
        dr.line([proj(gx, 0, 0), proj(gx, 0, d)], fill=(210, 212, 218))
    for gz in range(d + 1):
        dr.line([proj(0, 0, gz), proj(w, 0, gz)], fill=(210, 212, 218))

    order = sorted(rc.items(), key=lambda kv: (kv[0][0] + kv[0][1] + kv[0][2], kv[0][1], kv[0][0]))
    for (x, y, z), st in order:
        top, side = block_colors(st.id)
        for b in shape_boxes(st):
            b = _rotate_box(b, rot)
            x0, y0, z0, x1, y1, z1 = b
            X0, Y0, Z0, X1, Y1, Z1 = x + x0, y + y0, z + z0, x + x1, y + y1, z + z1
            tface = [proj(X0, Y1, Z0), proj(X1, Y1, Z0), proj(X1, Y1, Z1), proj(X0, Y1, Z1)]
            sface = [proj(X0, Y0, Z1), proj(X1, Y0, Z1), proj(X1, Y1, Z1), proj(X0, Y1, Z1)]
            eface = [proj(X1, Y0, Z0), proj(X1, Y0, Z1), proj(X1, Y1, Z1), proj(X1, Y1, Z0)]
            for poly, col, k in ((sface, side, 0.78), (eface, side, 0.62), (tface, top, 1.0)):
                c = tuple(max(0, min(255, int(v * k))) for v in col)
                edge = tuple(max(0, int(v * 0.55)) for v in c)
                dr.polygon(poly, fill=c, outline=edge)
    if title:
        dr.text((4, 2), title, fill=(30, 30, 40))
    return img


def plan(cells, size, px=10, title="plan"):
    """Top-down view: colour of the highest block per column, darkened by depth."""
    sx, sy, sz = size
    img = Image.new("RGB", (sx * px + 2, sz * px + 18), (236, 238, 242))
    dr = ImageDraw.Draw(img)
    top = {}
    for (x, y, z), st in cells.items():
        if st.short in ("air", "structure_void"):
            continue
        if (x, z) not in top or y > top[(x, z)][0]:
            top[(x, z)] = (y, st)
    for (x, z), (y, st) in top.items():
        c = block_colors(st.id)[0]
        k = 0.55 + 0.45 * (y + 1) / sy
        c = tuple(max(0, min(255, int(v * k))) for v in c)
        dr.rectangle([1 + x * px, 17 + z * px, x * px + px, 16 + z * px + px], fill=c)
    dr.text((2, 2), title + " (N up)", fill=(30, 30, 40))
    return img


def sheet(build, path, cuts=(), tile=14):
    """Write a contact sheet: two opposite iso views + optional cutaways."""
    img = sheet_image(build, cuts, tile)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def stack(images, path):
    """Stack several sheets vertically into one PNG."""
    W = max(i.width for i in images)
    out = Image.new("RGB", (W, sum(i.height for i in images)), (255, 255, 255))
    y = 0
    for i in images:
        out.paste(i, (0, y))
        y += i.height
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path)
    return path


def sheet_image(build, cuts=(), tile=14):
    cells = {p: st for p, (st, _) in build.cells.items()}
    panels = [render(cells, build.size, 0, tile, title=f"{build.name} SE {build.size}"),
              render(cells, build.size, 2, tile, title="NW")]
    for cy, rot in cuts:
        panels.append(render(cells, build.size, rot, tile, cut_y=cy, title=f"cut y<={cy} rot{rot}"))
    panels.append(plan(cells, build.size, px=max(8, tile // 2 + 2)))
    W = sum(p.width for p in panels[:2])
    rows = [panels[i:i + 2] for i in range(0, len(panels), 2)]
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
