"""Lumen shrine of the lumen grove: a ruined sanctuary with a glowing crown.

start  (two layouts, both on a raised round terrace with steps)
  spire   : broken colonnade around a crystal dais, a 5x5 ruined bell spire ~28 high
            (climb its ladder to the prismite crown that glows over the canopy), an
            ancient lumen tree breaking through the terrace
  chapel  : roofless chapel ruin with a tall gable wall and a twin-arched belfry ~26
            high crowned with prismite, the old tree growing out of the nave
  Both: Alchemist Vey's still built into the ruin - brewing stand, cauldrons, shelves of
  reagents, glowcap bottles, lore lectern; the public chest on the crystal dais; and
  "behind the vines" - a vine-curtained doorway into a niche with the hidden chest.
  Gravel drifts hide up to two suspicious-gravel finds (processor archaeology).
paths -> grounds (terrain-matching): glowcap ring, moon pool, fallen guardian statue,
  prismite outcrop, ruined gate arch
"""
import math
import random

from arch import plinth, ring, straight_stair, tree
from blocks import AIR, B
from builder import Build, item, slab, stairs, written_book
from common import circle_cells, ring_cells, vines_on
from furnish import chain_lamp, shelf_items
from lore import BOOKS
from pieces import Empty, Piece
from terrain_paths import connector, path_piece, piece_in

NAME = "lumen_shrine"
STRUCTURE = dict(biomes=["lumen_grove"], spacing=30, separation=10, size=2, max_distance=48)
LOOT = "expanse:chests/lumen_shrine"
HIDDEN = "expanse:chests/lumen_shrine_hidden"
P_PATHS = f"expanse:{NAME}/paths"
P_GROUNDS = f"expanse:{NAME}/grounds"
PROC = "lumen_shrine"
L = "expanse:lumen"
STONE = ["mossy_stone_bricks", "mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks", "tuff_bricks"]


def book():
    d = BOOKS[NAME]
    return written_book(d["title"], d["author"], d["pages"])


def st(rng):
    return B(rng.choice(STONE))


def lumen_tree(b, x, y, z, rng, h=12, crown=4.5):
    """Old lumen tree: thick buttressed trunk, branching crown of glowing leaves, hanging moss."""
    for k in range(h):
        b.set(x, y + k, z, B(f"{L}_log", axis="y"), clip=True)
        if k < 4:
            for dx, dz in ((1, 0), (0, 1)):
                b.set(x + dx, y + k, z + dz, B(f"{L}_log", axis="y"), clip=True)
            b.set(x + 1, y + k, z + 1, B(f"{L}_log", axis="y"), clip=True)
    for dx, dz, ax in ((-1, 0, "x"), (2, 0, "x"), (0, -1, "z"), (0, 2, "z")):
        b.set(x + dx, y, z + dz, B(f"{L}_wood", axis="y"), clip=True)
    tops = []
    for d, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        bx, bz, by = x, z, y + h - 3 - d % 2
        for i in range(3):
            bx, bz, by = bx + dx, bz + dz, by + (1 if i == 2 else 0)
            b.set(bx, by, bz, B(f"{L}_log", axis="x" if dx else "z"), clip=True)
        tops.append((bx, by, bz))
    from arch import leaf_ball, leaves
    leaf_ball(b, x, y + h, z, crown, leaves(f"{L}_leaves"), rng, flat=0.6)
    for (tx, ty, tz) in tops:
        leaf_ball(b, tx, ty + 1, tz, 2.4, leaves(f"{L}_leaves"), rng, flat=0.7)
    sx, sy, sz = b.size
    for xx in range(int(x - crown - 3), int(x + crown + 4)):
        for zz in range(int(z - crown - 3), int(z + crown + 4)):
            if not (0 <= xx < sx and 0 <= zz < sz):
                continue
            for yy in range(min(sy - 1, y + h + 4), y + h - 6, -1):
                c = b.get(xx, yy, zz)
                if c is not None and c.id.endswith("leaves"):
                    if b.inb(xx, yy - 1, zz) and b.get(xx, yy - 1, zz) is None and rng.random() < 0.3:
                        b.hang_moss(xx, yy - 1, zz, rng.randint(1, 4))
                    break


def crown_lamp(b, x, y, z):
    """Prismite crown: a glowing crystal cluster atop a tower (seen from far away at night)."""
    b.set(x, y, z, B("expanse:prismite_block"))
    b.set(x, y + 1, z, B("expanse:prismite_block"))
    b.set(x, y + 2, z, B("expanse:prismite_cluster", facing="up"))
    for (dx, dz, f) in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
        b.set(x + dx, y, z + dz, B("expanse:prismite_block"))
        b.set(x + dx, y + 1, z + dz, B("expanse:prismite_cluster", facing="up"))
        b.set(x + 2 * dx, y, z + 2 * dz, B("expanse:prismite_cluster", facing=f))


def terrace(b, rng, cx, cz, r, h=2):
    for (x, z) in circle_cells(cx, cz, r):
        for y in range(0, h + 1):
            edge = (x - cx) ** 2 + (z - cz) ** 2 > (r - 1.2) ** 2
            b.set(x, y, z, st(rng) if edge or y == h else B("dirt"))
    for (x, z) in circle_cells(cx, cz, r - 1.5):
        b.set(x, h, z, st(rng) if rng.random() < 0.8 else B("moss_block"))
    # front steps
    zf = int(math.ceil(cz - r))
    for i in range(h):
        for x in range(cx - 2, cx + 3):
            b.set(x, 1 + i, zf + i, stairs("stone_brick", "south"))
            for yy in range(2 + i, h + 4):
                if b.get(x, yy, zf + i) is not None:
                    b.set(x, yy, zf + i, AIR)


def dais(b, rng, cx, cz, y):
    for (x, z) in circle_cells(cx, cz, 2.2):
        b.set(x, y, z, B("chiseled_stone_bricks") if (x + z) % 2 == 0 else B("stone_bricks"))
        b.set(x, y + 1, z, slab("stone_brick"))
    for (x, z) in ring_cells(cx, cz, 2.2):
        dx, dz = x - cx, z - cz
        f = ("west" if dx > 0 else "east") if abs(dx) >= abs(dz) else ("north" if dz > 0 else "south")
        b.set(x, y + 1, z, stairs("mossy_stone_brick", f))
    b.set(cx, y + 1, cz, B("chiseled_stone_bricks"))
    b.chest(cx, y + 2, cz, "south", LOOT)
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.set(cx + dx, y + 1, cz + dz, B("expanse:prismite_block"))
        b.set(cx + dx, y + 2, cz + dz, B("expanse:prismite_cluster", facing="up"))


def alchemist_still(b, rng, x0, z0, y, facing_east=True):
    """Lean-to laboratory 6 deep x 5 wide against a ruin wall at x0 (opens toward +x)."""
    for z in range(z0, z0 + 6):
        for yy in range(y + 1, y + 6):
            b.set(x0, yy, z, st(rng))
    for z in range(z0, z0 + 6):
        for i, x in enumerate(range(x0 + 1, x0 + 5)):
            b.set(x, y + 5 - i // 2, z, stairs(f"{L}", "west"))
        if z in (z0, z0 + 5):
            b.set(x0 + 4, y + 1, z, B(f"{L}_fence"))
    for z in (z0, z0 + 5):
        for yy in range(y + 1, y + 4):
            b.set(x0 + 4, yy, z, B(f"{L}_fence"))
    b.set(x0 + 1, y + 1, z0 + 1, B("brewing_stand", has_bottle_0=True, has_bottle_1=False, has_bottle_2=True))
    b.set(x0 + 1, y + 1, z0 + 2, B("water_cauldron", level=2))
    b.set(x0 + 1, y + 1, z0 + 3, B("cauldron"))
    b.lectern(x0 + 1, y + 1, z0 + 4, "east", book())
    b.shelf(x0 + 1, y + 3, z0 + 1, "oak", "east", ["glass_bottle", "glowstone_dust", "expanse:glowcap"])
    b.shelf(x0 + 1, y + 3, z0 + 2, "oak", "east", shelf_items(rng, "alchemy"))
    b.shelf(x0 + 1, y + 3, z0 + 3, "oak", "east", ["potion", "glass_bottle", "spider_eye"])
    b.barrel(x0 + 2, y + 1, z0 + 5, "up")
    b.set(x0 + 3, y + 1, z0 + 5, B("expanse:potted_glowcap"))
    b.set(x0 + 2, y + 2, z0 + 5, B("candle", candles=3, lit=True))
    b.campfire(x0 + 3, y + 1, z0 + 2, lit=False)
    chain_lamp(b, x0 + 2, y + 5, z0 + 3, 0)


def vine_niche(b, rng, x, y, z, out):
    """A doorway in a wall cell column at (x, y..y+1, z), curtained by vines on its jambs, leading to
    a small niche behind (away from `out`) with the hidden chest."""
    from builder import DIRS, OPP, CW
    ox, _, oz = DIRS[OPP[out]]
    sx, _, sz = DIRS[CW[out]]
    for h in (0, 1):
        b.set(x, y + h, z, B("vine", **{CW[out]: True}))
        b.set(x + sx, y + h, z + sz, st(rng))
        b.set(x - sx, y + h, z - sz, st(rng))
    b.set(x, y + 2, z, st(rng))
    nx, nz = x + ox, z + oz
    for (cx, cz) in ((nx, nz), (nx + ox, nz + oz)):
        for h in (0, 1):
            b.set(cx, y + h, cz, AIR)
        b.set(cx, y + 2, cz, st(rng))
        b.set(cx, y - 1, cz, st(rng))
        for s in (1, -1):
            for h in (0, 1, 2):
                b.set(cx + s * sx, y + h, cz + s * sz, st(rng))
    ex, ez = nx + 2 * ox, nz + 2 * oz
    for h in (0, 1, 2):
        b.set(ex, y + h, ez, st(rng))
    b.chest(nx + ox, y, nz + oz, out, HIDDEN)
    b.set(nx, y, nz, B("expanse:glowcap"))
    b.set(nx, y - 1, nz, B("moss_block"))


def start_spire():
    b = Build(f"{NAME}_spire", 31, 33, 31, seed=1847961)
    rng = b.rng
    cx, cz = 15, 14
    terrace(b, rng, cx, cz, 12.5, 2)
    y = 2
    dais(b, rng, cx, cz, y + 1)
    # broken colonnade
    for i in range(12):
        a = i * math.pi / 6
        x, z = int(round(cx + 7.5 * math.cos(a))), int(round(cz + 7.5 * math.sin(a)))
        if i in (9,):
            continue
        h = rng.choice([2, 4, 6, 6, 6]) if i not in (3, 4) else 6
        b.set(x, y + 1, z, B("chiseled_stone_bricks"))
        for yy in range(y + 2, y + 2 + h):
            b.set(x, yy, z, B(f"{L}_log", axis="y") if yy < y + 1 + h else B("expanse:stripped_lumen_log", axis="y"))
        if h == 6:
            b.set(x, y + 8, z, B("mossy_stone_bricks"))
    rim = sorted(ring_cells(cx, cz, 7.5), key=lambda p: math.atan2(p[1] - cz, p[0] - cx))
    tops = [i for i, (x, z) in enumerate(rim) if b.get(x, y + 8, z) is not None]
    for i in tops:                                    # broken architrave stubs running off each full column
        for d in (1, -1):
            for k in (1, 2, 3):
                x, z = rim[(i + d * k) % len(rim)]
                if b.get(x, y + 8, z) is not None or rng.random() < 0.3 * k:
                    break
                b.set(x, y + 8, z, slab("mossy_stone_brick") if k > 1 else B("mossy_stone_bricks"))
    # the ruined bell spire at the back
    sx0, sz0 = cx - 2, cz + 9
    top = 28
    for x in range(sx0, sx0 + 5):
        for z in range(sz0, sz0 + 5):
            edge = x in (sx0, sx0 + 4) or z in (sz0, sz0 + 4)
            for yy in range(0, top + 1):
                if edge:
                    jag = yy > top - 4 and (x * 7 + z * 3 + yy) % 3 == 0
                    if not jag:
                        b.set(x, yy, z, st(rng) if yy % 6 else B("chiseled_stone_bricks"))
                elif yy <= y:
                    b.set(x, yy, z, B("stone_bricks"))
                else:
                    b.set(x, yy, z, AIR)
    for yy in (y + 1, y + 2):
        b.set(cx, yy, sz0, AIR)
    for yy in range(y + 1, top - 2):
        b.set(cx + 1, yy, sz0 + 3, B("ladder", facing="north"))
    for yy in (8, 14, 20):
        for (x, z) in ((cx, sz0), (cx, sz0 + 4), (sx0, sz0 + 2), (sx0 + 4, sz0 + 2)):
            b.set(x, yy, z, AIR)
            b.set(x, yy + 1, z, AIR)
            b.set(x, yy + 2, z, B("stone_bricks"))
    b.fill(sx0 + 1, top - 3, sz0 + 1, sx0 + 3, top - 3, sz0 + 3, B("stone_bricks"))
    b.set(cx + 1, top - 3, sz0 + 3, B("ladder", facing="north"))
    b.bell(cx - 1, top - 4, sz0 + 1, "ceiling", "north")
    crown_lamp(b, cx, top - 2, sz0 + 2)
    # the old lumen tree breaking through the terrace (east)
    lumen_tree(b, cx + 8, y + 1, cz - 2, rng, h=13, crown=4.5)
    # alchemist's still against a ruin wall (west) and the vine-curtained niche beside it
    for z in range(cz - 4, cz + 6):
        for yy in range(y + 1, y + 7 - abs(z - cz) // 3):
            b.set(cx - 10, yy, z, st(rng))
    alchemist_still(b, rng, cx - 10, cz - 4, y)
    vine_niche(b, rng, cx - 10, y + 1, cz + 3, "east")
    # gravel drifts (archaeology candidates) and overgrowth
    for (x, z) in ((cx - 4, cz + 5), (cx + 4, cz + 6), (cx - 2, cz - 7), (cx + 6, cz + 3)):
        b.set(x, y, z, B("gravel"))
    for (x, z) in circle_cells(cx, cz, 11):
        if b.get(x, y + 1, z) is None and rng.random() < 0.25:
            b.set(x, y + 1, z, B(rng.choice(["short_grass", "short_grass", "expanse:glowcap", "fern",
                                             "moss_carpet"])))
    vines_on(b, rng, 0.05, ymin=y + 2)
    connector(b, cx, 1, 0, "north", P_PATHS)
    connector(b, 30, 1, cz, "east", P_PATHS)
    connector(b, 0, 1, cz - 6, "west", P_PATHS)
    for (x, z) in ((cx, 0), (30, cz), (0, cz - 6)):
        b.set(x, 0, z, B("gravel"))
    return b


def start_chapel():
    b = Build(f"{NAME}_chapel", 31, 31, 31, seed=1847963)
    rng = b.rng
    cx, cz = 15, 14
    terrace(b, rng, cx, cz, 12.5, 2)
    y = 2
    # roofless nave x 10..20, z 7..24 with broken side walls
    x0, x1, z0, z1 = 10, 20, 6, 24
    for (x, z) in ring(x0, z0, x1, z1):
        h = 6 - ((x * 5 + z * 3) % 4) if x in (x0, x1) else 4
        if z == z0 and 13 <= x <= 17:
            continue
        for yy in range(y + 1, y + 1 + max(1, h)):
            b.set(x, yy, z, st(rng))
        if x in (x0, x1) and z % 4 == 2 and h >= 4:
            b.set(x, y + 3, z, AIR)
            b.set(x, y + 4, z, AIR)
    # tall west-front gable wall with a twin-arched belfry and the crown
    for x in range(x0, x1 + 1):
        gh = 20 - abs(x - cx) * 2
        for yy in range(y + 1, y + gh):
            b.set(x, yy, z1, st(rng))
    for yy in range(y + 1, y + 26):
        for x in (cx - 1, cx, cx + 1):
            b.set(x, yy, z1, st(rng))
            b.set(x, yy, z1 + 1, st(rng))
    for x in (cx - 1, cx + 1):                          # twin belfry arches
        for yy in (y + 20, y + 21):
            b.set(x, yy, z1, AIR)
    b.bell(cx - 1, y + 21, z1, "ceiling", "north")
    b.set(cx, y + 22, z1, B("chiseled_stone_bricks"))
    crown_lamp(b, cx, y + 26, z1)
    # rose window
    for dy in (-1, 0, 1):                                 # slit rose window with a crystal eye
        b.set(cx, y + 11 + dy, z1, B("expanse:prismite_block") if dy == 0 else B("glass_pane"))
    # altar dais in the apse, the tree in the nave
    dais(b, rng, cx, z1 - 4, y + 1)
    lumen_tree(b, cx + 2, y + 1, 11, rng, h=12, crown=4.2)
    # rubble
    for (x, z) in ((12, 15), (18, 9), (11, 20), (17, 21)):
        b.set(x, y + 1, z, st(rng))
        if rng.random() < 0.5:
            b.set(x + 1, y + 1, z, slab("mossy_stone_brick"))
    # still and niche outside the east wall
    for z in range(cz - 4, cz + 6):
        for yy in range(y + 1, y + 6):
            b.set(x1 + 3, yy, z, st(rng))
    alchemist_still(b, rng, x1 + 3, cz - 4, y)
    vine_niche(b, rng, x1 + 3, y + 1, cz + 3, "east")
    for (x, z) in ((12, 10), (18, 18), (14, 22), (8, 14)):
        b.set(x, y, z, B("gravel"))
    for (x, z) in circle_cells(cx, cz, 11):
        if b.get(x, y + 1, z) is None and rng.random() < 0.25:
            b.set(x, y + 1, z, B(rng.choice(["short_grass", "short_grass", "expanse:glowcap", "fern",
                                             "moss_carpet"])))
    vines_on(b, rng, 0.05, ymin=y + 2)
    connector(b, cx, 1, 0, "north", P_PATHS)
    connector(b, 0, 1, cz, "west", P_PATHS)
    connector(b, 30, 1, cz + 7, "east", P_PATHS)
    for (x, z) in ((cx, 0), (0, cz), (30, cz + 7)):
        b.set(x, 0, z, B("gravel"))
    return b


# ------------------------------------------------------------------ grounds
def ground_glowcap_ring():
    b = Build(f"{NAME}_glowcap_ring", 11, 4, 12, seed=1847971)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    for x in range(11):
        for z in range(1, 12):
            b.set(x, 0, z, B("moss_block") if rng.random() < 0.6 else B("grass_block"))
    for (x, z) in ring_cells(5, 6, 4):
        b.set(x, 1, z, B("expanse:glowcap") if rng.random() < 0.8 else B("red_mushroom"))
    b.set(5, 1, 6, B("expanse:prismite_cluster", facing="up"))
    b.set(5, 0, 6, B("expanse:prismite_block"))
    b.set(3, 1, 6, B("brown_mushroom"))
    return b


def ground_moon_pool():
    b = Build(f"{NAME}_moon_pool", 13, 6, 13, seed=1847973)
    rng = b.rng
    piece_in(b, 6, 2, 0, "north")
    for x in range(13):
        for z in range(1, 13):
            b.set(x, 0, z, B("dirt"))
            b.set(x, 1, z, B("moss_block"))
    for (x, z) in circle_cells(6, 7, 3.6):
        b.set(x, 1, z, B("water", level=0))
        b.set(x, 0, z, B("water", level=0) if (x - 6) ** 2 + (z - 7) ** 2 < 4 else B("clay"))
        if rng.random() < 0.15:
            b.set(x, 2, z, B("lily_pad"))
    for (x, z) in ring_cells(6, 7, 4.6):
        b.set(x, 1, z, st(rng))
        if rng.random() < 0.3:
            b.set(x, 2, z, slab("mossy_stone_brick"))
    b.set(6, 0, 7, B("expanse:prismite_block"))
    for (x, z) in ((2, 2), (10, 11)):
        b.set(x, 2, z, B("expanse:prismite_cluster", facing="up"))
        b.set(x, 1, z, B("expanse:prismite_block"))
    return b


def ground_fallen_statue():
    b = Build(f"{NAME}_statue", 11, 6, 13, seed=1847975)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    for x in range(11):
        for z in range(1, 13):
            b.set(x, 0, z, B(rng.choice(["moss_block", "grass_block", "coarse_dirt"])))
    b.set(5, 1, 3, B("chiseled_stone_bricks"))
    b.set(5, 2, 3, B("stone_bricks"))
    for z in range(5, 10):                              # the toppled body, arms flung out
        b.set(5, 1, z, B("stone_bricks") if z != 9 else B("chiseled_stone_bricks"))
    b.set(4, 1, 6, B("stone_bricks"))
    b.set(6, 1, 7, B("stone_bricks"))
    b.set(6, 1, 10, B("stone_brick_stairs", facing="south"))
    b.set(5, 1, 11, B("mossy_stone_bricks"))
    b.set(5, 2, 9, B("moss_carpet"))
    b.set(7, 1, 7, B("expanse:glowcap"))
    b.set(3, 1, 10, B("gravel"))
    return b


def ground_outcrop():
    b = Build(f"{NAME}_outcrop", 11, 9, 11, seed=1847977)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    for x in range(11):
        for z in range(1, 11):
            b.set(x, 0, z, B(rng.choice(["stone", "tuff", "moss_block"])))
    for (x, z) in circle_cells(5, 6, 2.5):
        h = rng.randint(1, 3)
        for y in range(1, h + 1):
            b.set(x, y, z, B(rng.choice(["tuff", "stone", "expanse:prismite_block"])))
    for (x, z, y) in ((5, 6, 4), (4, 5, 3), (6, 7, 3), (3, 7, 2)):
        b.set(x, y, z, B("expanse:prismite_block"))
        b.set(x, y + 1, z, B("expanse:prismite_cluster", facing="up"))
    for (x, z) in ((2, 3), (8, 9), (8, 3)):
        b.set(x, 1, z, B("expanse:prismite_cluster", facing="up"))
    return b


def ground_gate():
    b = Build(f"{NAME}_gate", 11, 9, 7, seed=1847979)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    for x in range(11):
        for z in range(1, 7):
            b.set(x, 0, z, B(rng.choice(["moss_block", "stone_bricks", "gravel", "mossy_cobblestone"])))
    for x in (2, 3, 7, 8):
        for y in range(1, 7 if x in (3, 7) else 5):
            b.set(x, y, 3, st(rng))
    for x in range(3, 8):
        if x in (3, 4, 7):
            b.set(x, 7, 3, st(rng))
    b.set(4, 6, 3, stairs("stone_brick", "west", "top"))
    b.set(6, 6, 3, stairs("stone_brick", "east", "top"))
    b.set(6, 7, 3, slab("mossy_stone_brick"))
    vines_on(b, rng, 0.25, ymin=2)
    b.set(1, 1, 5, B("expanse:glowcap"))
    b.set(9, 1, 2, B("expanse:glowcap"))
    return b


def pools():
    rp = random.Random(1847981)
    mats = (("moss_block", 2), ("gravel", 2), ("mossy_cobblestone", 2), ("coarse_dirt", 2))
    return {
        "start": [Piece(start_spire(), 1, PROC), Piece(start_chapel(), 1, PROC)],
        "paths": [Piece(path_piece(f"{NAME}_path_a", 6, P_GROUNDS, rp, mats=mats), 3, PROC, "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_b", 10, P_GROUNDS, rp, mats=mats), 2, PROC, "terrain_matching"),
                  Empty(2)],
        "grounds": [Piece(ground_glowcap_ring(), 3, PROC), Piece(ground_moon_pool(), 2, PROC),
                    Piece(ground_fallen_statue(), 2, PROC), Piece(ground_outcrop(), 2, PROC),
                    Piece(ground_gate(), 2, PROC)],
    }
