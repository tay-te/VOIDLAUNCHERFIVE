"""Set dressing shared by the small structures (designs/<small>.py).

Ground conventions match the big designs: layer y=0 is the ground course
(the terrain is bearded up to it), so these helpers put soil, paths and
foundation stones at y=0 and leave every other y=0 cell as structure void,
letting the biome's own surface show through. Plants always get a soil
block under them; `short_grass` is a marker that the per-biome processor
lists turn into the local flowers (or nothing) per instance.
"""
import math

from blocks import AIR, B
from builder import CCW, CW, DIRS, OPP, item, slab, stairs, trapdoor, written_book


def vec(d):
    return DIRS[d][0], DIRS[d][2]


def pick(rng, opts):
    """Random state from a list of names/states or of (name/state, weight) pairs."""
    if opts and isinstance(opts[0], tuple):
        tot = sum(w for _, w in opts)
        r = rng.random() * tot
        for o, w in opts:
            r -= w
            if r < 0:
                break
    else:
        o = rng.choice(opts)
    return B(o) if isinstance(o, str) else o


def book(d, generation=0):
    """Written book item from a {title, author, pages} dict."""
    return written_book(d["title"], d["author"], d["pages"], generation)


# ---------------------------------------------------------------- ground
def soil(b, x, z, ground, y=0):
    """Put a soil block at (x, y, z) unless something is already there."""
    if b.inb(x, y, z) and b.get(x, y, z) is None:
        b.set(x, y, z, pick(b.rng, ground) if isinstance(ground, list) else
              (B(ground) if isinstance(ground, str) else ground))


def plant(b, x, y, z, what, ground):
    """A plant (name, state or ("tall", name)) at y with soil under it; skips occupied cells."""
    if not b.inb(x, y, z) or not b.is_air_or_void(x, y, z):
        return False
    below = b.get(x, y - 1, z)
    if below is None or below.short == "air":
        if y - 1 < 0:
            return False
        b.set(x, y - 1, z, pick(b.rng, ground) if isinstance(ground, list) else
              (B(ground) if isinstance(ground, str) else ground))
    if isinstance(what, tuple) and what[0] == "tall":
        if b.inb(x, y + 1, z) and b.is_air_or_void(x, y + 1, z):
            b.tall_plant(x, y, z, what[1])
        else:
            return False
    else:
        b.set(x, y, z, B(what) if isinstance(what, str) else what)
    return True


def flora(b, cells, rng, density, ground, plants=("short_grass",), y=1):
    """Scatter plants (default: biome-flower markers) over (x, z) cells at height y."""
    for (x, z) in cells:
        if rng.random() < density:
            plant(b, x, y, z, rng.choice(plants), ground)


def ring(cx, cz, r0, r1, size):
    """Cells with r0 <= distance <= r1 from (cx, cz) inside a (sx, sz) template."""
    sx, sz = size
    return [(x, z) for x in range(sx) for z in range(sz) if r0 <= math.hypot(x - cx, z - cz) <= r1]


def edge_cells(b, margin=2):
    """Cells within `margin` of the template's horizontal border."""
    sx, _, sz = b.size
    return [(x, z) for x in range(sx) for z in range(sz)
            if x < margin or z < margin or x >= sx - margin or z >= sz - margin]


def trail(b, pts, rng, mats=(("dirt_path", 6), ("coarse_dirt", 2), ("gravel", 1)), width=1, fade=0.55,
          edge=0.35, air=True):
    """A worn track along the polyline pts [(x, z), ...] at y=0. Beyond `fade` of its length the
    track thins out (it peters out into the grass); `edge` is the chance of widening a step."""
    cells = []
    for a, c in zip(pts, pts[1:]):
        n = max(abs(c[0] - a[0]), abs(c[1] - a[1]), 1)
        for i in range(n + (1 if c == pts[-1] else 0)):
            p = (round(a[0] + (c[0] - a[0]) * i / n), round(a[1] + (c[1] - a[1]) * i / n))
            if not cells or cells[-1] != p:
                cells.append(p)
    L = len(cells)
    for i, (x, z) in enumerate(cells):
        t = i / max(1, L - 1)
        keep = 1.0 if t < fade else max(0.15, 1.0 - (t - fade) / (1 - fade) * 0.85)
        offs = [(0, 0)]
        if width > 1:
            offs += [(1, 0), (0, 1)] if width == 2 else [(1, 0), (-1, 0), (0, 1), (0, -1)]
        for k, (dx, dz) in enumerate(offs):
            q = (x + dx, z + dz)
            p_keep = keep if k == 0 else keep * 0.8
            if k == 0 and width == 1 and rng.random() < edge * keep:
                d = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
                _path_cell(b, x + d[0], z + d[1], rng, mats, air)
            if rng.random() < p_keep:
                _path_cell(b, q[0], q[1], rng, mats, air)
    return cells


def _path_cell(b, x, z, rng, mats, air):
    if not b.inb(x, 0, z):
        return
    cur = b.get(x, 0, z)
    if cur is not None and cur.short not in ("grass_block", "podzol", "coarse_dirt", "dirt", "moss_block",
                                               "snow_block", "sand", "opal_sand", "mud", "rooted_dirt"):
        return
    b.set(x, 0, z, pick(rng, list(mats)))
    if air and b.inb(x, 1, z):
        above = b.get(x, 1, z)
        if above is None or above.short in ("short_grass", "fern"):
            b.set(x, 1, z, AIR)


def stepping_stones(b, pts, rng, mats, every=2):
    for i, (x, z) in enumerate(pts):
        if i % every == 0 and b.inb(x, 0, z):
            b.set(x, 0, z, pick(rng, mats))
            if b.get(x, 1, z) is None:
                b.set(x, 1, z, AIR)


def clear(b, x0, y0, z0, x1, y1, z1):
    """Explicit air (carves terrain/plants) where nothing is set yet."""
    b.air(x0, y0, z0, x1, y1, z1, only_void=True)


def boulder(b, x, z, rng, mats, w=2, d=2, h=2, moss=None):
    """A lumpy rock sunk into the ground (y=0..h-1), rounded off at the top corners."""
    for dx in range(w):
        for dz in range(d):
            corner = dx in (0, w - 1) and dz in (0, d - 1) and w > 1 and d > 1
            hh = h - (1 if corner and rng.random() < 0.7 else 0) - (1 if rng.random() < 0.2 else 0)
            for y in range(0, max(1, hh)):
                if b.inb(x + dx, y, z + dz):
                    b.set(x + dx, y, z + dz, pick(rng, mats))
            if moss and rng.random() < 0.45 and b.inb(x + dx, max(1, hh), z + dz) and \
                    b.get(x + dx, max(1, hh), z + dz) is None:
                b.set(x + dx, max(1, hh), z + dz, B(moss))


def rubble(b, cells, rng, mats, loose=None, density=0.6, y=1):
    """Fallen stones: a ground-course block with, sometimes, a loose slab/stair/block on it."""
    for (x, z) in cells:
        if rng.random() >= density or not b.inb(x, y, z) or b.get(x, y, z) is not None:
            continue
        if b.get(x, y - 1, z) is None:
            b.set(x, y - 1, z, pick(rng, mats))
        if loose and rng.random() < 0.6:
            b.set(x, y, z, pick(rng, loose))


# ---------------------------------------------------------------- props
def ground_item(b, x, y, z, it, rotation=0, glow=False):
    """An item frame lying flat on the block below (x, y, z): a book left on a stone, a map on a table."""
    b.item_frame(x, y, z, "up", it if isinstance(it, dict) else item(it), rotation=rotation, glow=glow)


def wall_item(b, x, y, z, facing, it, rotation=0):
    b.item_frame(x, y, z, facing, it if isinstance(it, dict) else item(it), rotation=rotation)


def a_tent(b, x0, z0, length, along, wool, y=1, closed_back=True, wide=False):
    """Ridge tent of wool stairs (as vanilla abandoned_camp tents), tall halves toward the ridge.
    3 wide (crawl-in pup tent) or, wide=True, 5 wide with a walk-in middle. The last row is closed
    when closed_back. Returns the floor cells inside.

    Cross-sections:   .W.            ..W..
                      S.S            .S.S.
                                     S...S
    """
    w = f"{wool}_wool"
    n = 5 if wide else 3
    inside = []
    for i in range(length):
        row = [(x0 + i, z0 + j) if along == "x" else (x0 + j, z0 + i) for j in range(n)]
        f_lo, f_hi = ("south", "north") if along == "x" else ("east", "west")
        last = closed_back and i == length - 1
        if wide:
            b.set(*_xyz(row[0], y), stairs(w, f_lo))
            b.set(*_xyz(row[4], y), stairs(w, f_hi))
            b.set(*_xyz(row[1], y + 1), stairs(w, f_lo))
            b.set(*_xyz(row[3], y + 1), stairs(w, f_hi))
            b.set(*_xyz(row[2], y + 2), B(w))
            for j in (1, 2, 3):
                b.set(*_xyz(row[j], y), B(w) if last else AIR)
                if not last:
                    inside.append(row[j])
            b.set(*_xyz(row[2], y + 1), B(w) if last else AIR)
        else:
            b.set(*_xyz(row[0], y), stairs(w, f_lo))
            b.set(*_xyz(row[2], y), stairs(w, f_hi))
            b.set(*_xyz(row[1], y + 1), B(w))
            b.set(*_xyz(row[1], y), B(w) if last else AIR)
            if not last:
                inside.append(row[1])
    return inside


def _xyz(xz, y):
    return xz[0], y, xz[1]


def bedroll(b, x, y, z, facing, color=None):
    """A straw bedroll (vanilla straw_bed) or a wool-colour bed laid toward `facing`."""
    b.bed(x, y, z, facing, color or "straw")


def log_seat(b, x, z, rng, wood, axis="x", length=2, y=1):
    dx, dz = (1, 0) if axis == "x" else (0, 1)
    for i in range(length):
        b.set(x + dx * i, y, z + dz * i, B(f"{wood}_log", axis=axis))


def stump(b, x, z, wood, y=1, top=None):
    b.set(x, y, z, B(f"{wood}_log", axis="y"))
    if top:
        b.set(x, y + 1, z, B(top) if isinstance(top, str) else top)


def campfire_ring(b, x, z, rng, stones=("cobblestone", "stone", "mossy_cobblestone"), lit=True, y=1):
    """Campfire in a ring of stone slabs/buttons on the ground course."""
    b.campfire(x, y, z, lit=lit)
    b.set(x, y - 1, z, B("coarse_dirt") if y - 1 >= 0 and b.get(x, y - 1, z) is None else b.get(x, y - 1, z))
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if b.get(x + dx, y - 1, z + dz) is None:
            b.set(x + dx, y - 1, z + dz, pick(rng, list(stones)))


def flag_pole(b, x, z, h, colors, pole, rng, y=1, pattern_sets=None):
    """Prayer-flag pole: a column of `pole` (log/wall) with wall banners hanging off its upper part
    on alternating sides, capped by a slab or rod."""
    for k in range(h):
        b.set(x, y + k, z, B(pole, axis="y") if pole.endswith(("_log", "_wood")) else B(pole))
    top = y + h - 1
    sides = [d for d in ("north", "east", "south", "west")
             if b.inb(x + vec(d)[0], top - 1, z + vec(d)[1]) and b.get(x + vec(d)[0], top, z + vec(d)[1]) is None]
    rng.shuffle(sides)
    for i, side in enumerate(sides[:min(len(colors), 2)]):
        dx, dz = vec(side)
        pats = (pattern_sets or [[]])[i % len(pattern_sets or [[]])]
        b.banner(x + dx, top - i, z + dz, colors[i], pats, wall_facing=side)


def bones(b, cells, rng, y=1, skull_chance=0.25):
    """Scattered bones: bone blocks lying in the grass, the odd skull."""
    for (x, z) in cells:
        if not b.is_air_or_void(x, y, z):
            continue
        r = rng.random()
        if r < skull_chance:
            b.set(x, y, z, B("skeleton_skull", rotation=rng.randrange(16)))
        else:
            b.set(x, y, z, B("bone_block", axis=rng.choice(["x", "z"])))


def pot_of(rng, opts):
    return B(rng.choice(opts))


def hang_lantern(b, x, y, z, soul=False):
    b.set(x, y, z, B("soul_lantern" if soul else "lantern", hanging=True))


def snow_cap(b, rng, prob=0.85, layers=(1, 1, 2), skip=()):
    """Snow layers on every exposed full-block top (cold-biome variants)."""
    from builder import is_full
    tops = {}
    for (x, y, z), (st, _) in b.cells.items():
        if is_full(st) and st.short not in skip and not st.short.endswith(("_leaves", "ice")):
            if (x, z) not in tops or y > tops[(x, z)]:
                tops[(x, z)] = y
    for (x, z), y in sorted(tops.items()):
        if b.inb(x, y + 1, z) and b.is_air_or_void(x, y + 1, z) and rng.random() < prob:
            b.set(x, y + 1, z, B("snow", layers=rng.choice(layers)))


__all__ = ["vec", "pick", "book", "soil", "plant", "flora", "ring", "edge_cells", "trail", "stepping_stones",
           "clear", "boulder", "rubble", "ground_item", "wall_item", "a_tent", "bedroll", "log_seat", "stump",
           "campfire_ring", "flag_pole", "bones", "hang_lantern", "snow_cap", "CW", "CCW", "OPP", "AIR", "B",
           "slab", "stairs", "trapdoor", "item"]


def meal_fire(b, x, y, z, foods=(), lit=True, done=(0, 0, 0, 0), soul=False):
    """Campfire with food left on it (CampfireBlockEntity: Items in slots 0-3, cooking progress).
    Lit, it finishes cooking and pops the food off; unlit, the meal stays half-done."""
    import nbt
    items = []
    for i, f in enumerate(list(foods)[:4]):
        it = item(f)
        it["Slot"] = nbt.Byte(i)
        items.append(it)
    b.set(x, y, z, B("soul_campfire" if soul else "campfire", lit=lit, facing="north"),
          {"components": {}, "Items": nbt.List(nbt.TAG_COMPOUND if items else nbt.TAG_END, items),
           "CookingTimes": nbt.IntArray(list(done)), "CookingTotalTimes": nbt.IntArray([600, 600, 600, 600]),
           "id": "minecraft:campfire"})


def cart(b, x, z, along, wood, rng, broken_corner=None, y=1):
    """Two-axle cart 2 wide x 3 long (deck of top slabs on y+1, wheels are open trapdoors on the
    sides, shafts reaching forward). `along` is its length axis; the front is toward +along.
    broken_corner (0..3) drops that corner: its wheel lies flat beside the cart instead."""
    cells = []
    for i in range(3):
        for j in range(2):
            cells.append((x + i, z + j) if along == "x" else (x + j, z + i))
    corners = [cells[0], cells[1], cells[4], cells[5]]
    sag = corners[broken_corner] if broken_corner is not None else None
    for (cx, cz) in cells:
        b.set(cx, y + 1 if (cx, cz) != sag else y, cz, slab(wood, "top" if (cx, cz) != sag else "bottom"))
    # side boards on the long edges (fences read as rails)
    wheel_pos = []
    for i in (0, 2):
        for j, side in ((0, -1), (1, 1)):
            if along == "x":
                wx, wz, face = x + i, z + j + side, "north" if side < 0 else "south"
            else:
                wx, wz, face = x + j + side, z + i, "west" if side < 0 else "east"
            wheel_pos.append((wx, wz, face, (x + i, z + j) if along == "x" else (x + j, z + i)))
    for (wx, wz, face, axle) in wheel_pos:
        if axle == sag:
            continue
        b.set(wx, y, wz, trapdoor(wood, face, "bottom", True))
    # shafts
    if along == "x":
        fx = x + 3
        b.set(fx, y, z, B(f"{wood}_fence"))
        b.set(fx, y, z + 1, B(f"{wood}_fence"))
    else:
        fz = z + 3
        b.set(x, y, fz, B(f"{wood}_fence"))
        b.set(x + 1, y, fz, B(f"{wood}_fence"))
    return cells, sag, [w for w in wheel_pos if w[3] == sag]


def small_tree(b, x, z, rng, log, leaves, h=4, lean=None, r=2.2, blossoms=None, y=1, flat=False):
    """A hand-built tree (persistent leaves): trunk from y with an optional lean (direction) for the
    top half, a lumpy canopy of radius r, and blossoms/moss strands hanging from its underside.
    Returns the crown centre."""
    cx, cz = x, z
    for k in range(h):
        if lean and k >= h // 2:
            dx, dz = vec(lean)
            cx, cz = x + dx, z + dz
            if k == h // 2:
                b.set(cx, y + k - 1, cz, B(log, axis="x" if dx else "z"), clip=True)
        b.set(cx, y + k, cz, B(log, axis="y"), clip=True)
    top = y + h
    leaf = B(leaves, persistent=True, distance=1)
    canopy = []
    for dy in (-1, 0, 1) if not flat else (0, 1):
        rr = r - (0.9 if dy == 1 else 0.3 if dy == -1 else 0)
        for ax in range(int(-rr) - 1, int(rr) + 2):
            for az in range(int(-rr) - 1, int(rr) + 2):
                d = math.hypot(ax, az)
                if d <= rr and (d < rr - 0.6 or rng.random() < 0.65):
                    p = (cx + ax, top + dy, cz + az)
                    if b.inb(*p) and b.get(*p) is None:
                        b.set(*p, leaf)
                        canopy.append(p)
    b.set(cx, top - 1, cz, B(log, axis="y"), clip=True)
    if blossoms:
        for (px, py, pz) in canopy:
            if py == min(q[1] for q in canopy) and rng.random() < 0.45:
                if b.inb(px, py - 1, pz) and b.get(px, py - 1, pz) is None:
                    b.hang_moss(px, py - 1, pz, rng.randint(1, 3), block=blossoms)
    return cx, top, cz


def hip_cap(b, x0, z0, x1, z1, y, mat, cap=None, upturn=False):
    """Hip roof over the rectangle (stairs rings stepping in), optional upturned corner tips."""
    k = 0
    while x0 + k <= x1 - k and z0 + k <= z1 - k:
        a0, a1, c0, c1 = x0 + k, x1 - k, z0 + k, z1 - k
        if a0 == a1 or c0 == c1:
            for xx in range(a0, a1 + 1):
                for zz in range(c0, c1 + 1):
                    b.set(xx, y + k, zz, cap or slab(mat))
            break
        for xx in range(a0, a1 + 1):
            b.set(xx, y + k, c0, stairs(mat, "south"))
            b.set(xx, y + k, c1, stairs(mat, "north"))
        for zz in range(c0 + 1, c1):
            b.set(a0, y + k, zz, stairs(mat, "east"))
            b.set(a1, y + k, zz, stairs(mat, "west"))
        k += 1
    if upturn:
        for (cx, cz, f) in ((x0, z0, "south"), (x1, z0, "south"), (x0, z1, "north"), (x1, z1, "north")):
            b.set(cx, y, cz, stairs(mat, f, "top"))
            if b.inb(cx, y + 1, cz) and b.get(cx, y + 1, cz) is None:
                b.set(cx, y + 1, cz, slab(mat))
    return y + k


def raft(b, x, y, z, yaw=0.0):
    """A bamboo raft pulled up on the shore (the boat entity of bamboo)."""
    b._entity("bamboo_raft", (x + 0.5, y, z + 0.5), (x, y, z), {}, yaw=yaw)
