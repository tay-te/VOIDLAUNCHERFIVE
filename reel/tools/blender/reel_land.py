"""
The land far beyond an export, from `reel.sh terrain` grids (renders/_terrain/NAME: ground.f32 and surface.f32): block
columns as wide as the grid's step, so distance still reads as Minecraft (as VOID LOD draws it), painted by what the
heights say: grass in soft patches, forest standing a few blocks taller, a patchwork of fields on flat low ground,
rock where it is steep or high, snow on the peaks, sand at the shore, water (where the surface stands above the
ground) as glossy water. Generator grids carry no colours of their own,
so this is a painted backdrop, as an animated film paints its distance.

    import reel_land; land, water = reel_land.build(folder, origin, holes=[(x0, z0, x1, z1)], seed=1)

Holes are Minecraft x/z boxes left empty (where an export's real blocks are, or a finer grid). Same space as
reel_world.py: Minecraft's axes less the origin, in blocks.
"""
import json
import os

import bpy
import numpy as np

import voxkit as vk


def smooth_noise(shape, cells, rng):
    """Value noise over a grid: random values on a coarse lattice `cells` grid points apart, smoothly interpolated."""
    ny, nx = shape
    gy, gx = ny // cells + 3, nx // cells + 3
    lattice = rng.random((gy, gx))
    y = np.arange(ny) / cells
    x = np.arange(nx) / cells
    y0, x0 = np.floor(y).astype(int), np.floor(x).astype(int)
    fy, fx = y - y0, x - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a = lattice[np.ix_(y0, x0)]
    b = lattice[np.ix_(y0, x0 + 1)]
    c = lattice[np.ix_(y0 + 1, x0)]
    d = lattice[np.ix_(y0 + 1, x0 + 1)]
    return (a * (1 - fx) + b * fx) * (1 - fy)[:, None] + (c * (1 - fx) + d * fx) * fy[:, None]


def paint(ground, top, water, step, rng, xs=None, zs=None):
    """Colours (linear) for every cell from its height, its slope and some noise, and how tall its canopy stands."""
    gz, gx = np.gradient(ground, step)
    slope = np.hypot(gx, gz)
    blocks = max(1, int(round(48 / step)))
    patch = smooth_noise(ground.shape, blocks, rng)
    fine = smooth_noise(ground.shape, max(1, blocks // 4), rng)
    grass = np.array([0.15, 0.3, 0.06])
    meadow = np.array([0.26, 0.36, 0.08])
    forest = np.array([0.06, 0.16, 0.045])
    rock = np.array([0.24, 0.23, 0.22])
    snow = np.array([0.82, 0.86, 0.92])
    sand = np.array([0.5, 0.44, 0.28])
    deep = np.array([0.02, 0.07, 0.1])
    col = grass[None, None, :] + (meadow - grass)[None, None, :] * fine[..., None]
    canopy = np.zeros_like(ground)
    if step <= 4 and xs is not None:
        # fields: plots of about 20 by 14 blocks on flat, low ground, each wheat, green, pale or ploughed
        crops = np.array([[0.46, 0.36, 0.08], [0.2, 0.33, 0.07], [0.3, 0.38, 0.12], [0.22, 0.14, 0.07], [0.5, 0.42, 0.12]])
        X, Z = np.meshgrid(xs, zs)
        plot = ((X // 20) * 73856093 ^ (Z // 14) * 19349663) % 1000
        farmed = (patch > 0.18) & (patch < 0.48) & (slope < 0.6) & (ground < 160)
        col[farmed] = crops[plot[farmed] % len(crops)] * (0.9 + 0.2 * fine[farmed])[:, None]
    woods = patch > 0.55
    col[woods] = forest + (grass - forest) * 0.3 * fine[woods][:, None]
    canopy[woods] = 5 + 5 * fine[woods]
    steep = (slope > 0.9) | (ground > 250 + 40 * fine)
    col[steep] = rock * (0.8 + 0.4 * fine[steep])[:, None]
    high = ground > 330 + 50 * patch
    col[high] = snow
    shore = (~water) & (ground < 67) & (ground > 60)
    col[shore] = sand
    col[water] = deep
    canopy[steep | high | water] = 0
    return col, canopy


def build(folder, origin, holes=(), seed=1, name=None):
    meta = json.load(open(os.path.join(folder, "meta.json")))
    nx, nz, step = meta["nx"], meta["nz"], meta["step"]
    ground = np.fromfile(os.path.join(folder, "ground.f32"), "<f4").reshape(nz, nx).astype(np.float64)
    surface = np.fromfile(os.path.join(folder, "surface.f32"), "<f4").reshape(nz, nx).astype(np.float64)
    water = surface - ground > 0.5
    top = np.where(water, surface, ground)
    rng = np.random.default_rng(seed)
    xs = meta["x0"] + np.arange(nx) * step
    zs = meta["z0"] + np.arange(nz) * step
    col, canopy = paint(ground, top, water, step, rng, xs, zs)
    top = top + canopy
    keep = np.ones((nz, nx), bool)
    for x0, z0, x1, z1 in holes:
        inside_x = (xs >= x0) & (xs + step <= x1)
        inside_z = (zs >= z0) & (zs + step <= z1)
        keep[np.ix_(inside_z, inside_x)] = False
    ox, oy, oz = origin
    # voxkit's grid: row 0 north (Blender +y), x east; a cell spans x..x+step and y..y-step
    at = (meta["x0"] - ox, oz - meta["z0"])
    bottom = np.full_like(top, top.min() - 24)
    name = name or meta["name"]
    if step > 8:
        # a coarse grid's columns would stand as giant steps; that far off the land reads as a smooth painted surface
        land = smooth_surface(name + " land", ground - oy, keep, col, xs + step / 2 - ox, oz - (zs + step / 2))
        sea = smooth_surface(name + " water", np.where(water, top, -1e4) - oy, keep & water, col, xs + step / 2 - ox, oz - (zs + step / 2))
    else:
        land = vk.columns(name + " land", top - oy, bottom - oy, keep & ~water, col, cell=step, origin=at)
        sea = vk.columns(name + " water", top - oy, top - oy - 1, keep & water, col, cell=step, origin=at)
    land.data.materials.append(land_material(name + " land"))
    sea.data.materials.append(water_material(name + " water"))
    print("[reel_land] %s: %d x %d at %d blocks, %d land and %d water columns" % (name, nx, nz, step, int((keep & ~water).sum()),
                                                                                int((keep & water).sum())))
    return land, sea


def smooth_surface(name, height, keep, col, bx, by):
    """A smooth surface through the cells' middles (Blender x `bx`, y `by`), a quad wherever any corner is kept, so it
    runs on a little way under whatever fills its hole; the points inside the hole are tucked down out of sight."""
    nz, nx = height.shape
    X, Y = np.meshgrid(bx, by)
    verts = np.stack([X, Y, np.where(keep, height, height - 8)], -1).reshape(-1, 3)
    idx = np.arange(nz * nx).reshape(nz, nx)
    whole = keep[:-1, :-1] | keep[1:, :-1] | keep[1:, 1:] | keep[:-1, 1:]
    quads = np.stack([idx[:-1, :-1], idx[:-1, 1:], idx[1:, 1:], idx[1:, :-1]], -1)[whole]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts.tolist(), [], quads.tolist())
    me.update()
    colours = me.color_attributes.new("Col", "FLOAT_COLOR", "POINT")
    colours.data.foreach_set("color", np.concatenate([col.reshape(-1, 3), np.ones((nz * nx, 1))], 1).astype(np.float32).ravel())
    for poly in me.polygons:
        poly.use_smooth = True
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def land_material(name):
    """Painted land in the film style: its colour a little richer, matte, light falling softly."""
    m = vk.vertex_material(name, rough=0.9)
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    v = next(n for n in nt.nodes if n.type == "VERTEX_COLOR")
    hs = nt.nodes.new("ShaderNodeHueSaturation")
    hs.inputs["Saturation"].default_value = 1.2
    nt.links.new(v.outputs["Color"], hs.inputs["Color"])
    nt.links.new(hs.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Specular IOR Level"].default_value = 0.2
    return m


def water_material(name):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (0.02, 0.07, 0.1, 1)
    bsdf.inputs["Roughness"].default_value = 0.08
    bsdf.inputs["Specular IOR Level"].default_value = 0.6
    return m


# ---------------------------------------------------------------- VOID LOD's own far land (reel.sh lod)
SKIRT = 12  # blocks a column's side reaches down where the next tile, not this one, knows the ground
SMOOTH_FROM = 32  # cells this coarse are drawn as smooth land: as blocks they would stand like a skyline
TREES = ("log", "leaves", "_wood", "stem", "mushroom_block")
NATURAL = ("grass", "dirt", "podzol", "mud", "stone", "granite", "diorite", "andesite", "deepslate", "tuff", "calcite", "gravel", "sand",
           "snow", "ice", "water", "clay", "moss", "terracotta", "basalt", "fern", "flower", "mycelium")
BUILT = ("brick", "cobble", "polished", "smooth", "planks", "slab", "stairs", "fence", "wall", "door", "glass", "chiseled", "cut_")


def build_lod(folder, origin, holes=(), name="lod"):
    """The far land as VOID LOD draws it (renders/_lod/NAME): each tile's cells as block columns at the tile's own cell
    size (1 block next to the player, coarser toward the horizon), in the colours LOD drew them, water as glossy water.
    Newer tiles over older ones; cells inside `holes` (Minecraft x/z boxes) are left out."""
    meta = json.load(open(os.path.join(folder, "tiles.json")))
    raw = open(os.path.join(folder, "tiles.bin"), "rb").read()
    ox, oy, oz = origin
    land, water, smooth = [], [], []
    for t in sorted(meta["list"], key=lambda t: t["revision"]):
        n, c = t["size"], t["cell"]
        at = t["offset"]
        heights = np.frombuffer(raw, ">i2", n * n, at).reshape(n, n).astype(np.float64)
        colours = np.frombuffer(raw, np.uint8, n * n * 3, at + n * n * 2).reshape(n, n, 3) / 255.0
        blocks = np.frombuffer(raw, np.int8, n * n, at + n * n * 5).reshape(n, n)
        xs = t["minX"] + np.arange(n) * c
        zs = t["minZ"] + np.arange(n) * c
        valid = heights > -32768
        for x0, z0, x1, z1 in holes:
            valid &= ~(((xs[None, :] + c) > x0) & (xs[None, :] < x1) & ((zs[:, None] + c) > z0) & (zs[:, None] < z1))
        wet_ids = [i for i, b in enumerate(t["palette"]) if "water" in b]
        wet = np.isin(blocks, wet_ids) & valid
        # a coarse cell takes its highest point: one tree or crag would make the whole cell a pillar. Cells standing
        # well above their neighbours come down to them; tree tops become a low canopy in forest green
        # (blocks LOD cannot name, -1, are mostly modded trees: Expanse's beech and the like)
        tree_ids = [i for i, b in enumerate(t["palette"]) if any(k in b for k in TREES)]
        treed = (np.isin(blocks, tree_ids) | (blocks < 0)) & valid & ~wet
        around = np.pad(np.where(valid, heights, np.nan), 2, mode="edge")
        tint = np.pad(colours, ((2, 2), (2, 2), (0, 0)), mode="edge")
        window = [(dj, di) for dj in range(-2, 3) for di in range(-2, 3)]
        with np.errstate(all="ignore"):
            import warnings
            warnings.simplefilter("ignore")
            ground = np.nanmedian(np.stack([around[2 + dj:2 + dj + n, 2 + di:2 + di + n] for dj, di in window]), 0)
        land_tint = np.median(np.stack([tint[2 + dj:2 + dj + n, 2 + di:2 + di + n] for dj, di in window]), 0)
        ground = np.where(np.isnan(ground), heights, ground)
        rise = heights - ground
        spike = valid & ~wet & (rise > max(2.5, 0.75 * c))
        heights = np.where(spike | treed, ground + np.where(treed, np.clip(rise, 0, 4), np.clip(rise, 0, 1.5)), heights)
        colours = colours.copy()
        colours[spike & ~treed] = land_tint[spike & ~treed]
        brownish = (colours[..., 0] > colours[..., 1] + 0.06)
        colours[treed & brownish] = (0.2, 0.32, 0.12)
        # a building in a coarse cell would stand as a box the cell's width: far off it is a low, darker roof patch
        built_ids = [i for i, b in enumerate(t["palette"])
                     if any(k in b for k in BUILT) or not any(k in b for k in NATURAL + TREES)]
        built = np.isin(blocks, built_ids) & valid & ~wet & (c >= 4)
        heights = np.where(built, ground + np.clip(rise, 0, min(3.0, 0.4 * c)), heights)
        colours[built] *= 0.7
        lin = np.where(colours <= 0.04045, colours / 12.92, ((colours + 0.055) / 1.055) ** 2.4)
        if c >= SMOOTH_FROM:
            smooth.append(tile_surface(heights, valid, lin, c, xs, zs, origin))
            continue
        land.append(columns_faces(heights, valid & ~wet, lin, c, xs, zs, origin))
        water.append(columns_faces(heights, wet, lin, c, xs, zs, origin, walls=False))
    print("[reel_land] %s: %d tiles, %d cells" % (name, len(meta["list"]), meta["cells"]))
    land_ob = faces_object(name + " land", land)
    water_ob = faces_object(name + " water", water)
    far_ob = surfaces_object(name + " far", smooth)
    land_ob.data.materials.append(land_material(name + " land"))
    water_ob.data.materials.append(water_material(name + " water"))
    far_ob.data.materials.append(land_material(name + " far"))
    return land_ob, water_ob, far_ob


def tile_surface(top, keep, col, c, xs, zs, origin):
    """A coarse tile as a smooth surface through its cells' middles, reaching one cell past its edges (heights carried
    out) so it overlaps its neighbours rather than leaving a crack: points (M, 3), quads (Q, 4) and colours (M, 3)."""
    ox, oy, oz = origin
    h = np.pad(np.where(keep, top, np.nan), 1, mode="edge")
    h = np.where(np.isnan(h), np.nanmin(top[keep]) if keep.any() else 0, h)
    k = np.pad(keep, 1, mode="edge")
    cl = np.pad(col, ((1, 1), (1, 1), (0, 0)), mode="edge")
    n = h.shape[0]
    bx = np.concatenate([[xs[0] - c], xs, [xs[-1] + c]]) + c / 2 - ox
    by = oz - (np.concatenate([[zs[0] - c], zs, [zs[-1] + c]]) + c / 2)
    X, Y = np.meshgrid(bx, by)
    pts = np.stack([X, Y, h - oy], -1).reshape(-1, 3)
    idx = np.arange(n * n).reshape(n, n)
    use = k[:-1, :-1] | k[1:, :-1] | k[1:, 1:] | k[:-1, 1:]
    quads = np.stack([idx[:-1, :-1], idx[1:, :-1], idx[1:, 1:], idx[:-1, 1:]], -1)[use]
    return pts, quads, cl.reshape(-1, 3)


def surfaces_object(name, parts):
    """One smooth mesh from many tile surfaces."""
    pts, quads, cols, base = [], [], [], 0
    for p, q, cl in parts:
        pts.append(p)
        quads.append(q + base)
        cols.append(cl)
        base += len(p)
    me = bpy.data.meshes.new(name)
    if parts:
        me.from_pydata(np.concatenate(pts).tolist(), [], np.concatenate(quads).tolist())
        me.update()
        attr = me.color_attributes.new("Col", "FLOAT_COLOR", "POINT")
        allc = np.concatenate(cols)
        attr.data.foreach_set("color", np.concatenate([allc, np.ones((len(allc), 1))], 1).astype(np.float32).ravel())
        me.polygons.foreach_set("use_smooth", np.ones(len(me.polygons), bool))
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def columns_faces(top, keep, col, c, xs, zs, origin, walls=True):
    """Quads (N, 4, 3) in Blender's axes and their colours (N, 3): a top for every kept cell, and walls wherever the
    cell beside stands lower, down to it (or a skirt's depth where the cell beside is not this tile's to know)."""
    ox, oy, oz = origin
    n = top.shape[0]
    jj, ii = np.nonzero(keep)
    X0 = xs[ii] - ox
    X1 = X0 + c
    Yn = -(zs[jj] - oz)  # the cell's north edge (smaller z) is Blender's larger y
    Ys = Yn - c
    Z = top[jj, ii] - oy
    quads = [np.stack([np.stack([X0, Yn, Z], -1), np.stack([X0, Ys, Z], -1), np.stack([X1, Ys, Z], -1), np.stack([X1, Yn, Z], -1)], 1)]
    shades = [col[jj, ii]]
    if walls:
        padded = np.pad(np.where(keep, top, np.nan), 1, constant_values=np.nan)
        for dj, di, side in ((0, 1, "e"), (0, -1, "w"), (-1, 0, "n"), (1, 0, "s")):
            other = padded[jj + 1 + dj, ii + 1 + di]
            low = np.where(np.isnan(other), top[jj, ii] - SKIRT, other) - oy
            show = low < Z
            if not show.any():
                continue
            x0, x1, yn, ys, z, lo = X0[show], X1[show], Yn[show], Ys[show], Z[show], low[show]
            if side == "e":
                q = [(x1, ys, lo), (x1, yn, lo), (x1, yn, z), (x1, ys, z)]
            elif side == "w":
                q = [(x0, yn, lo), (x0, ys, lo), (x0, ys, z), (x0, yn, z)]
            elif side == "n":
                q = [(x1, yn, lo), (x0, yn, lo), (x0, yn, z), (x1, yn, z)]
            else:
                q = [(x0, ys, lo), (x1, ys, lo), (x1, ys, z), (x0, ys, z)]
            quads.append(np.stack([np.stack(v, -1) for v in q], 1))
            shades.append(col[jj, ii][show] * 0.72)
    return np.concatenate(quads), np.concatenate(shades)


def faces_object(name, parts):
    """One mesh from many (quads, colours) parts, built with foreach_set: fast for millions of faces."""
    quads = np.concatenate([q for q, _ in parts]) if parts else np.zeros((0, 4, 3))
    colours = np.concatenate([c for _, c in parts]) if parts else np.zeros((0, 3))
    nq = len(quads)
    me = bpy.data.meshes.new(name)
    me.vertices.add(nq * 4)
    me.vertices.foreach_set("co", quads.reshape(-1).astype(np.float32))
    me.loops.add(nq * 4)
    me.loops.foreach_set("vertex_index", np.arange(nq * 4, dtype=np.int32))
    me.polygons.add(nq)
    me.polygons.foreach_set("loop_start", np.arange(0, nq * 4, 4, dtype=np.int32))
    me.polygons.foreach_set("loop_total", np.full(nq, 4, np.int32))
    me.update()
    attr = me.color_attributes.new("Col", "FLOAT_COLOR", "CORNER")
    rgba = np.concatenate([colours, np.ones((nq, 1))], 1).astype(np.float32)
    attr.data.foreach_set("color", np.repeat(rgba, 4, axis=0).ravel())
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob
