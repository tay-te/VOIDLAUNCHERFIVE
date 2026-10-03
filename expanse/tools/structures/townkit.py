"""Building kit for the settlement landmarks (palm_harbour, logging_camp, wisteria_manor, caravanserai,
mine_works): houses in the proportions of vanilla village houses, market stalls, cranes, stacked goods,
wheels, minecarts, ruins - and the loot/lore glue those designs share.

Conventions as everywhere else: layer y=0 of a rigid piece is the ground course; a house's `y` is its
floor layer, walls stand on y+1..; interiors are explicit air; lighting is torches and lanterns the way
villages place them.
"""
import math

import nbt
from blocks import AIR, B
from builder import CCW, CW, DIRS, OPP, item, slab, stairs, trapdoor, written_book
from furnish import vec, window
from kit import roof_gable
from loot import item as loot_item

H4 = ("north", "east", "south", "west")


# ------------------------------------------------------------------ states
def S(st):
    """A state from a state, an id, or a callable (called with no arguments)."""
    if callable(st):
        return st()
    return B(st) if isinstance(st, str) else st


def mix(rng, *opts):
    """A callable that picks one of the given ids/states (or (id, weight) pairs) each time."""
    flat = []
    for o in opts:
        if isinstance(o, tuple):
            flat += [o[0]] * o[1]
        else:
            flat.append(o)
    return lambda: S(rng.choice(flat))


def log_y(wood):
    return B(f"{wood}_log", axis="y")


def stripped(wood, axis="y"):
    ns, w = wood.split(":") if ":" in wood else ("minecraft", wood)
    return B(f"{ns}:stripped_{w}_log", axis=axis)


# ------------------------------------------------------------------ houses
def rect_ring(x0, z0, x1, z1):
    out = [(x, z0) for x in range(x0, x1 + 1)] + [(x, z1) for x in range(x0, x1 + 1)]
    out += [(x0, z) for z in range(z0 + 1, z1)] + [(x1, z) for z in range(z0 + 1, z1)]
    return out


def walls(b, x0, z0, x1, z1, y0, y1, post, fill, bay=4, base=None, beam=None, beam_ys=()):
    """Perimeter walls from y0 to y1: `post` at the corners and every `bay` cells along each wall,
    `fill` between, `base` as the bottom course, horizontal `beam` (a log id) at the beam_ys."""
    for (x, z) in rect_ring(x0, z0, x1, z1):
        corner = x in (x0, x1) and z in (z0, z1)
        along_x = z in (z0, z1) and not corner
        k = (x - x0) if along_x else (z - z0)
        is_post = corner or (bay and k % bay == 0)
        for y in range(y0, y1 + 1):
            if is_post:
                st = S(post)
            elif base is not None and y == y0:
                st = S(base)
            elif beam is not None and y in beam_ys:
                st = B(beam, axis="x" if along_x else "z") if isinstance(beam, str) else S(beam)
            else:
                st = S(fill)
            b.set(x, y, z, st)


def hollow(b, x0, z0, x1, z1, y0, y1):
    b.air(x0, y0, z0, x1, y1, z1)


def house(b, rng, x0, z0, x1, z1, y, *, post, fill, floor, roof, gable_fill=None, along="x", h=4,
          upper=None, upper_h=4, base=None, bay=4, overhang=1, gable_overhang=1, beam=None, loft=True):
    """A one- or two-storey house on the wall rectangle x0..x1/z0..z1 with its floor at y.
    Storey 1: walls y+1..y+h. With `upper` (the upper storey's fill), a floor at y+h+1 and walls on to
    y+h+1+upper_h. A gable roof (`roof` = stairs material) on top; the attic is air. Returns
    (floors, wall_top, ridge)."""
    b.fill(x0, y, z0, x1, y, z1, S(floor) if not callable(floor) else floor())
    for xx in range(x0 + 1, x1):
        for zz in range(z0 + 1, z1):
            b.set(xx, y, zz, S(floor))
    top = y + h
    walls(b, x0, z0, x1, z1, y + 1, top, post, fill, bay, base=base)
    hollow(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y + 1, top)
    floors = [y]
    if upper is not None:
        fy = top + 1
        walls(b, x0, z0, x1, z1, fy, fy, post, beam or post, bay)
        for (xx, zz) in rect_ring(x0, z0, x1, z1):
            if beam is not None and not (xx in (x0, x1) and zz in (z0, z1)):
                b.set(xx, fy, zz, B(beam, axis="x" if zz in (z0, z1) else "z") if isinstance(beam, str) else S(beam))
        for xx in range(x0 + 1, x1):
            for zz in range(z0 + 1, z1):
                b.set(xx, fy, zz, S(floor))
        top = fy + upper_h
        walls(b, x0, z0, x1, z1, fy + 1, top, post, upper, bay)
        hollow(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, fy + 1, top)
        floors.append(fy)
    gf = gable_fill if gable_fill is not None else (upper if upper is not None else fill)
    ridge = roof_gable(b, x0, x1, z0, z1, top, roof, lambda xx, yy, zz: S(gf), along=along, overhang=overhang,
                       gable_overhang=gable_overhang)
    if loft:
        for xx in range(x0 + 1, x1):
            for zz in range(z0 + 1, z1):
                if b.get(xx, top + 1, zz) is None:
                    b.set(xx, top + 1, zz, AIR)
    return floors, top, ridge


def flat_roof(b, x0, z0, x1, z1, y, mat, parapet=None, rng=None, gap=0.0):
    """Flat roof (desert-village style) with an optional parapet course."""
    b.fill(x0, y, z0, x1, y, z1, S(mat))
    if parapet is not None:
        for (x, z) in rect_ring(x0, z0, x1, z1):
            if rng is not None and rng.random() < gap:
                continue
            b.set(x, y + 1, z, S(parapet))


def door(b, x, y, z, wood, facing, hinge="left", step=None):
    """A door in the wall cell (x, y..y+1, z) opening toward `facing`, with an optional step outside."""
    b.door(x, y, z, wood, facing, hinge)
    if step is not None:
        dx, dz = vec(facing)
        if b.inb(x + dx, y - 1, z + dz):
            b.set(x + dx, y - 1, z + dz, S(step))


def win(b, x, y, z, out, h=1, glass="glass_pane", shutters=None, box=None, box_wood="spruce"):
    window(b, x, y, z, out, h, glass=glass, shutters=shutters, box=box, box_wood=box_wood)


def wall_torch(b, x, y, z, facing):
    """A torch on the wall behind (x, y, z), sticking out toward `facing` (as villages hang them)."""
    b.set(x, y, z, B("wall_torch", facing=facing))


def lamp(b, x, y, z, wood="spruce", h=2, torch=False):
    """Village lamp post: fence posts with a lantern (or a torch) on top."""
    for k in range(h):
        b.set(x, y + k, z, B(f"{wood}_fence"))
    b.set(x, y + h, z, B("torch") if torch else B("lantern"))


def hanging_lamp(b, x, y, z, max_chain=8):
    """A lantern hanging at (x, y, z); if nothing is right above it, an iron chain climbs to the first
    solid block (a roof or beam) above."""
    b.set(x, y, z, B("lantern", hanging=True))
    chain = []
    for yy in range(y + 1, min(y + 1 + max_chain, b.size[1])):
        st = b.get(x, yy, z)
        if st is not None and st.short != "air":
            for c in chain:
                b.set(x, c, z, B("iron_chain", axis="y"))
            return
        chain.append(yy)


# ------------------------------------------------------------------ furniture & props
def stall(b, x, y, z, facing, wood, cloth, goods=(), rng=None, w=3):
    """Market stall `w` wide: a counter of top slabs / barrels on y facing the customer side `facing`,
    four fence posts and a wool-slab awning at y+3. (x, z) is the counter's left end (seen from the
    customer)."""
    fx, fz = vec(facing)
    sx, sz = vec(CW[facing])          # along the counter, left -> right seen from the front
    sx, sz = -sx, -sz
    cells = [(x + sx * i, z + sz * i) for i in range(w)]
    back = [(cx - fx, cz - fz) for cx, cz in cells]
    for i, (cx, cz) in enumerate(cells):
        b.set(cx, y, cz, B("barrel", facing=facing) if (i == 1 and w >= 3) else slab(wood, "top"))
        g = goods[i % len(goods)] if goods else None
        if g is not None and i not in (0, w - 1):
            b.set(cx, y + 1, cz, S(g))
    for (cx, cz) in (cells[0], cells[-1]):                 # front posts stand on the counter ends
        b.set(cx, y + 1, cz, B(f"{wood}_fence"))
        b.set(cx, y + 2, cz, B(f"{wood}_fence"))
    for (cx, cz) in (back[0], back[-1]):                   # back posts from the ground
        for k in range(3):
            b.set(cx, y + k, cz, B(f"{wood}_fence"))
    for (cx, cz) in back[1:-1]:
        b.set(cx, y, cz, AIR)
        b.set(cx, y + 1, cz, AIR)
        b.set(cx, y + 2, cz, AIR)
    for (cx, cz) in cells + back:                          # awning: wool roof, slab lip out front
        b.set(cx, y + 3, cz, B(f"{cloth}_wool"))
    for (cx, cz) in cells:
        if b.inb(cx + fx, y + 3, cz + fz) and b.get(cx + fx, y + 3, cz + fz) is None:
            b.set(cx + fx, y + 3, cz + fz, B(f"{cloth}_wool_slab", type="top"))
    return cells, back


def village_well(b, x, z, y=1, stone="cobblestone", roof="spruce", post="spruce_fence"):
    """Village-style well centred on (x, z) for a piece whose ground course is y-1: a stone curb round
    a water shaft (water from the ground course up), two posts and a little roof with the bucket chain."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            b.set(x + dx, y - 1, z + dz, B(stone))
            if dx or dz:
                b.set(x + dx, y, z + dz, B(f"{stone}_wall") if (dx and dz) else B(stone))
    b.set(x, y - 1, z, B("water", level=0))
    b.set(x, y, z, B("water", level=0))
    for dx, dz in ((-1, -1), (1, 1)):
        b.set(x + dx, y + 1, z + dz, B(post))
        b.set(x + dx, y + 2, z + dz, B(post))
    for dx in (-1, 0, 1):
        b.set(x + dx, y + 3, z - 1, stairs(roof, "south"))
        b.set(x + dx, y + 3, z + 1, stairs(roof, "north"))
        b.set(x + dx, y + 3, z, slab(roof))
    b.set(x, y + 2, z, B("iron_chain", axis="y"))


def goods_stack(b, x, y, z, rng, kinds=("barrel", "barrel", "hay", "log"), wood="spruce", n=3):
    """A small pile of goods at (x, y, z) and around: barrels (some on top), hay bales, a log."""
    placed = 0
    for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1))[:n]:
        k = rng.choice(kinds)
        st = {"barrel": B("barrel", facing=rng.choice(["up", "up", "north", "east"])),
              "hay": B("hay_block", axis=rng.choice(["x", "y", "z"])),
              "log": B(f"{wood}_log", axis=rng.choice(["x", "z"])),
              "melon": B("melon"), "pumpkin": B("pumpkin"), "kelp": B("dried_kelp_block"),
              "wool": B("white_wool")}[k]
        if not b.is_air_or_void(x + dx, y, z + dz):
            continue
        b.set(x + dx, y, z + dz, st)
        placed += 1
        if k == "barrel" and rng.random() < 0.4 and b.is_air_or_void(x + dx, y + 1, z + dz):
            b.set(x + dx, y + 1, z + dz, B("barrel", facing="up"))
    return placed


def crane(b, x, y, z, facing, wood, h=6, reach=3, load="barrel"):
    """Wooden jib crane: a log mast at (x, y.., z), a fence jib reaching `reach` toward `facing`
    with a brace, and a chain with a load hanging at its tip."""
    for k in range(h):
        b.set(x, y + k, z, B(f"{wood}_log", axis="y"))
    dx, dz = vec(facing)
    for i in range(1, reach + 1):
        b.set(x + dx * i, y + h - 1, z + dz * i, B(f"{wood}_fence"))
    b.set(x + dx, y + h - 2, z + dz, stairs(wood if ":" not in wood else wood, OPP[facing], "top"))
    tx, tz = x + dx * reach, z + dz * reach
    yy = y + h - 2
    for _ in range(2):
        b.set(tx, yy, tz, B("iron_chain", axis="y"))
        yy -= 1
    if load == "barrel":
        b.set(tx, yy, tz, B("barrel", facing="up"))
    elif load:
        b.set(tx, yy, tz, S(load))
    return tx, yy, tz


def wheel(b, cx, cy, cz, r, axis, wood, rim=None, spokes=True):
    """A vertical wheel of radius r centred at (cx, cy, cz) turning about `axis` ("x" or "z"):
    a rim of planks/stripped logs, spokes of fences and an axle log. Returns the rim cells."""
    rim_cells = []
    for a in range(-r - 1, r + 2):
        for v in range(-r - 1, r + 2):
            d = math.hypot(a, v)
            if r - 0.5 <= d <= r + 0.65:
                p = (cx + a, cy + v, cz) if axis == "z" else (cx, cy + v, cz + a)
                b.set(*p, S(rim) if rim is not None else B(f"{wood}_planks"), clip=True)
                rim_cells.append(p)
    if spokes:
        for k in range(1, r):
            for (a, v) in ((k, 0), (-k, 0), (0, k), (0, -k)):
                p = (cx + a, cy + v, cz) if axis == "z" else (cx, cy + v, cz + a)
                if b.inb(*p):
                    b.set(*p, B(f"{wood}_fence"))
    b.set(cx, cy, cz, B(f"{wood}_log", axis=axis))
    return rim_cells


def minecart(b, x, y, z, yaw=0.0, loot=None):
    """A minecart (or a chest minecart with a loot table, as mineshafts have) standing on the rail at
    (x, y, z)."""
    extra = {}
    if loot:
        extra = {"LootTable": loot}
    b._entity("chest_minecart" if loot else "minecart", (x + 0.5, y + 0.0625, z + 0.5), (x, y, z), extra, yaw=yaw)


def rail_line(b, cells, y, shape):
    for (x, z) in cells:
        b.set(x, y, z, B("rail", shape=shape))


def sign_board(b, x, y, z, wood, lines, facing):
    """A short name board on a wall (at most two words a line, like a shop board)."""
    b.sign(x, y, z, wood, lines, wall_facing=facing)


def bench(b, cells, y, wood, facing):
    for (x, z) in cells:
        b.set(x, y, z, stairs(wood, facing))


def bed_pair(b, x, y, z, facing, colors=("white", "white")):
    b.bed(x, y, z, facing, colors[0])


# ------------------------------------------------------------------ ground & ruin
def pave(b, x0, z0, x1, z1, y, pick, skip=None):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if skip and skip(x, z):
                continue
            b.set(x, y, z, pick(x, z))


def ruin(b, rng, x0, z0, x1, z1, y_lo, y_hi, chance=0.5, keep=lambda st: False, drop_to=None):
    """Knock the top off walls in a box: every column of non-air blocks loses a random number of top
    blocks (more toward a random 'collapse' corner). Removed blocks become structure void, so the
    sky shows through."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            ys = [y for y in range(y_lo, y_hi + 1) if b.get(x, y, z) is not None and b.get(x, y, z).short != "air"]
            if not ys or rng.random() > chance:
                continue
            cut = rng.randint(1, max(1, (y_hi - y_lo) // 2))
            for y in sorted(ys, reverse=True)[:cut]:
                st = b.get(x, y, z)
                if keep(st):
                    continue
                b.set(x, y, z, None)


def drop_orphans(b):
    """After knocking things down: remove bed, door and tall-plant halves whose other half is gone, and
    lanterns or torches left hanging from nothing."""
    changed = True
    while changed:
        changed = False
        for (x, y, z), (st, _) in list(b.cells.items()):
            s = st.short
            other = None
            if s.endswith("_bed"):
                dx, _, dz = DIRS[st.get("facing")]
                k = 1 if st.get("part") == "foot" else -1
                other = (x + k * dx, y, z + k * dz)
            elif st.get("half") in ("upper", "lower") and "stairs" not in s and "trapdoor" not in s:
                other = (x, y + 1, z) if st.get("half") == "lower" else (x, y - 1, z)
            elif s in ("lantern", "soul_lantern") and st.get("hanging") == "true" or s == "iron_chain":
                above = b.get(x, y + 1, z)
                if above is None or above.short == "air":
                    b.set(x, y, z, None)
                    changed = True
                continue
            elif s == "wall_torch":
                dx, _, dz = DIRS[st.get("facing")]
                behind = b.get(x - dx, y, z - dz)
                if behind is None or behind.short == "air":
                    b.set(x, y, z, None)
                    changed = True
                continue
            else:
                continue
            o = b.get(*other)
            if o is None or o.id != st.id:
                b.set(x, y, z, None)
                changed = True


def overgrow(b, rng, cells, y, density=0.4, plants=("short_grass",), ground=None):
    """Biome-flower markers (short_grass) on ground cells; ground placed under each one if given."""
    for (x, z) in cells:
        if rng.random() >= density or not b.inb(x, y, z) or not b.is_air_or_void(x, y, z):
            continue
        below = b.get(x, y - 1, z)
        if below is None:
            if ground is None:
                continue
            b.set(x, y - 1, z, S(ground))
        elif below.short not in ("grass_block", "dirt", "coarse_dirt", "podzol", "moss_block", "rooted_dirt"):
            continue
        b.set(x, y, z, S(rng.choice(plants)))


# ------------------------------------------------------------------ underground
CAVE_STONE = ("stone", "stone", "andesite", "tuff", "cobblestone", "deepslate")


def rock_mound(b, rng, cx, cz, rx, rz, h, mats=CAVE_STONE, y=1):
    """A heap of rock (a fallen boulder pile or the stub of a rock wall) rising to h at its centre."""
    cells = []
    for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
        for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
            d = math.hypot((x - cx) / rx, (z - cz) / rz)
            if d > 1.0:
                continue
            top = y + int(round((h - 1) * (1 - d * d) + rng.random() * 0.8))
            for yy in range(y, top + 1):
                if b.inb(x, yy, z) and b.get(x, yy, z) is None:
                    st = rng.choice(mats)
                    b.set(x, yy, z, B(st, axis="y") if st == "deepslate" else B(st))
            cells.append((x, top, z))
    return cells


def lichen(b, rng, prob=0.08, ymin=1):
    """Glow lichen on exposed faces of full stone-like blocks (the caves' own light)."""
    from builder import is_full
    adds = {}
    stony = ("stone", "andesite", "tuff", "deepslate", "cobblestone", "cobbled_deepslate", "mossy_cobblestone",
             "deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles", "polished_deepslate", "calcite")
    for (x, y, z), (st, n) in sorted(b.cells.items()):
        if y < ymin or n is not None or st.short not in stony or not is_full(st):
            continue
        for d in ("north", "south", "east", "west", "up"):
            dx, dy, dz = DIRS[d]
            p = (x + dx, y + dy, z + dz)
            if not b.inb(*p) or p in b.cells or p in adds or rng.random() >= prob:
                continue
            adds[p] = B("glow_lichen", **{OPP[d]: True})
    for p, st in adds.items():
        b.set(*p, st)


# ------------------------------------------------------------------ loot / lore glue
def lore_entry(book_def, weight=1):
    """Loot entry for a journal {title, author, pages} (as loot.lore_book does for the big structures)."""
    return loot_item("written_book", weight, extra=[
        {"type": "minecraft:set_written_book_pages", "pages": list(book_def["pages"]), "mode": "replace_all"},
        {"type": "minecraft:set_book_cover", "title": book_def["title"], "author": book_def["author"],
         "generation": 2}])


def journal(book_def):
    return written_book(book_def["title"], book_def["author"], book_def["pages"])


__all__ = ["S", "mix", "log_y", "stripped", "rect_ring", "walls", "hollow", "house", "flat_roof", "door", "win",
           "wall_torch", "lamp", "hanging_lamp", "stall", "goods_stack", "crane", "wheel", "minecart", "rail_line",
           "sign_board", "bench", "pave", "ruin", "overgrow", "lore_entry", "journal", "H4", "AIR", "B", "CW",
           "CCW", "DIRS", "OPP", "item", "slab", "stairs", "trapdoor", "vec", "nbt"]
