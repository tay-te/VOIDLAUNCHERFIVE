"""Ruined limestone watchtower: a 9x9 tower ~22 high with a battered base,
string courses, a corbelled crenellated top platform, a spiral stair around a
central pillar, two inner floors and a broken open top floor holding the loot
chest. The north-east corner has collapsed; its rubble lies inside and out."""
from blocks import AIR, B
from builder import Build, slab, stairs
from common import spiral_floor_ok, spiral_stair, vines_on

NAME = "ruined_watchtower"
LOOT = "expanse:chests/ruined_watchtower"

LB = "expanse:limestone_brick"
X0, X1, Z0, Z1 = 3, 11, 3, 11       # outer wall square of the shaft
CX, CZ = 7, 7
FLOORS = [6, 12]                    # inner floor block levels
PLAT = 18                           # top platform level (on corbels)


def wall_block(rng):
    r = rng.random()
    if r < 0.24:
        return B("expanse:mossy_limestone")
    if r < 0.32:
        return B("expanse:limestone")
    return B("expanse:limestone_bricks")


def collapse_k(x, z):
    """How badly the NE corner has collapsed at column (x, z) (0 = intact)."""
    d = (X1 - x) + (z - Z0)          # manhattan distance from the NE corner
    return max(0.0, 7 - d) * 1.4


def build():
    b = Build(NAME, 15, 23, 15, seed=1847301)
    rng = b.rng

    def top_of(x, z):
        """Highest surviving course of the shaft wall/parapet at (x,z)."""
        k = collapse_k(min(max(x, X0), X1), min(max(z, Z0), Z1))
        if k <= 1.0:
            return 99
        return int(PLAT + 1 - min(9, k * 1.5) + ((x * 7 + z * 13) % 3) - 1)

    # ---- ground course & battered base
    for x in range(2, 13):
        for z in range(2, 13):
            b.set(x, 0, z, rng.choice(["expanse:limestone", "expanse:limestone", "cobblestone",
                                       "expanse:limestone_bricks"]))
    for x in range(2, 13):
        b.set(x, 1, 2, stairs(LB, "south"))
        b.set(x, 1, 12, stairs(LB, "north"))
    for z in range(3, 12):
        b.set(2, 1, z, stairs(LB, "east"))
        b.set(12, 1, z, stairs(LB, "west"))

    # ---- shaft walls
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if x not in (X0, X1) and z not in (Z0, Z1):
                continue
            t = min(top_of(x, z), PLAT - 1)
            corner = x in (X0, X1) and z in (Z0, Z1)
            for y in range(1, t + 1):
                if corner:
                    b.set(x, y, z, B("expanse:chiseled_limestone") if y % 6 == 1 else B("expanse:polished_limestone"))
                else:
                    b.set(x, y, z, wall_block(rng))
    b.fill(X0 + 1, 1, Z0 + 1, X1 - 1, PLAT + 3, Z1 - 1, AIR)

    # ---- outer ring helpers (the 11x11 ring just outside the shaft)
    def outer_ring():
        for x in range(2, 13):
            yield x, 2, "south"
            yield x, 12, "north"
        for z in range(3, 12):
            yield 2, z, "east"
            yield 12, z, "west"

    # string courses
    for fy in FLOORS:
        for x, z, f in outer_ring():
            if top_of(x, z) >= fy:
                b.set(x, fy, z, stairs(LB, f, "top"))
    # corbels carrying the platform
    for x, z, f in outer_ring():
        if top_of(x, z) >= PLAT - 1:
            b.set(x, PLAT - 1, z, stairs(LB, f, "top"))

    # ---- spiral stair (enters from the south door)
    steps = spiral_stair(b, CX, CZ, 1, PLAT, LB, B("expanse:polished_limestone"), start=5,
                         underside=slab(LB, "top"))
    b.set(CX, PLAT + 1, CZ, B("expanse:limestone_brick_wall"))
    b.set(CX, PLAT + 2, CZ, B("lantern"))

    # ---- inner floors
    for fy in FLOORS:
        for x in range(X0 + 1, X1):
            for z in range(Z0 + 1, Z1):
                if (x, z) == (CX, CZ) or not spiral_floor_ok(steps, x, z, fy):
                    continue
                inner = abs(x - CX) <= 1 and abs(z - CZ) <= 1
                b.set(x, fy, z, slab(LB, "top") if inner else B("spruce_planks"))
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            b.set(x, 0, z, rng.choice(["expanse:polished_limestone", "expanse:limestone_bricks", "cobblestone",
                                       "expanse:mossy_limestone"]))

    # ---- top platform, parapet and merlons (broken in the NE)
    hole = lambda x, z: collapse_k(x, z) > 2.2
    for x in range(2, 13):
        for z in range(2, 13):
            if (x, z) == (CX, CZ) or not spiral_floor_ok(steps, x, z, PLAT) or hole(x, z):
                continue
            edge = x in (2, 12) or z in (2, 12)
            if edge and top_of(x, z) < PLAT:
                continue
            b.set(x, PLAT, z, wall_block(rng) if edge or x in (X0, X1) or z in (Z0, Z1)
                  else B("expanse:polished_limestone") if (x + z) % 2 else B("expanse:limestone_bricks"))
    for x, z, f in outer_ring():
        t = top_of(x, z)
        if t >= PLAT + 1:
            b.set(x, PLAT + 1, z, wall_block(rng))
            if (x + z) % 2 == 0 and t >= PLAT + 2:
                b.set(x, PLAT + 2, z, wall_block(rng))
                if rng.random() < 0.25:
                    b.set(x, PLAT + 3, z, slab(LB))
    for (x, z) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        if top_of(x, z) >= PLAT + 2:
            b.set(x, PLAT + 1, z, B("expanse:polished_limestone"))
            b.set(x, PLAT + 2, z, B("expanse:chiseled_limestone"))

    # ---- door (south) with an arch
    for y in (1, 2, 3):
        b.set(CX, y, Z1, AIR)
    b.set(CX - 1, 3, Z1, stairs(LB, "east", "top"))
    b.set(CX + 1, 3, Z1, stairs(LB, "west", "top"))
    b.set(CX, 4, Z1, B("expanse:chiseled_limestone"))
    b.set(CX, 1, 12, stairs(LB, "north"))
    b.set(CX - 1, 2, 12, B("wall_torch", facing="south"))

    # ---- arrow slits
    for fy in [0] + FLOORS:
        for (x, z) in ((CX, Z0), (X0, CZ), (X1, CZ), (CX, Z1)):
            if fy == 0 and z == Z1:
                continue
            for y in (fy + 2, fy + 3):
                if top_of(x, z) > y:
                    b.set(x, y, z, AIR if (x + y + z) % 3 else B("iron_bars"))

    # ---- ground floor: storeroom
    b.barrel(4, 1, 4, "up")
    b.barrel(4, 1, 5, "north")
    b.barrel(5, 1, 4, "up")
    b.barrel(4, 2, 4, "up")
    b.set(10, 1, 4, B("crafting_table"))
    b.set(10, 1, 5, B("furnace", facing="west"))
    b.set(4, 1, 10, B("hay_block", axis="x"))
    b.set(5, 1, 10, B("hay_block", axis="y"))
    b.set(10, 1, 10, B("cauldron"))
    b.set(X0 + 1, 3, 8, B("wall_torch", facing="east"))
    b.set(X1 - 1, 3, 9, B("wall_torch", facing="west"))
    b.set(9, 1, 10, B("cobweb"))

    # ---- first floor: bunks
    y = FLOORS[0] + 1
    b.bed(4, y, 10, "north", "brown")
    b.bed(10, y, 10, "north", "brown")
    b.set(4, y, 4, B("bookshelf"))
    b.set(4, y + 1, 4, B("bookshelf"))
    b.set(5, y, 4, B("chiseled_bookshelf", facing="south", slot_0_occupied=True, slot_4_occupied=True))
    b.set(10, y, 4, B("spruce_fence"))
    b.set(10, y + 1, 4, B("spruce_pressure_plate"))
    b.set(9, y, 4, stairs("spruce", "east"))
    b.set(10, y, 5, B("barrel", facing="west"))
    b.set(5, y, 10, B("brown_carpet"))
    b.set(9, y, 10, B("brown_carpet"))
    b.set(X0 + 1, y + 2, 5, B("wall_torch", facing="east"))
    b.set(4, y + 3, 9, B("cobweb"))

    # ---- second floor: armoury / lookout, with rubble fallen from above
    y = FLOORS[1] + 1
    b.set(4, y, 4, B("anvil", facing="east"))
    b.set(4, y, 10, B("grindstone", face="floor", facing="north"))
    b.set(10, y, 10, B("barrel", facing="up"))
    b.set(9, y, 10, B("barrel", facing="north"))
    b.set(4, y, 9, B("fletching_table"))
    b.set(X0 + 1, y + 2, 9, B("wall_torch", facing="east"))
    b.set(5, y + 3, 10, B("cobweb"))
    for (x, z) in ((9, 4), (10, 4), (10, 5), (9, 5), (8, 4), (10, 6)):
        if rng.random() < 0.75:
            b.set(x, y, z, rng.choice([slab(LB), B("expanse:mossy_limestone"), stairs(LB, "west"), B("gravel")]))

    # ---- top platform: loot chest, lantern, torn banner
    y = PLAT + 1
    b.chest(4, y, 10, "north", LOOT)
    b.barrel(3, y, 10, "up")
    b.set(5, y, 10, B("lantern"))
    b.set(3, y, 4, slab(LB))
    b.set(4, y, 3, B("expanse:mossy_limestone"))
    b.banner(CX, PLAT + 1, 13, "white", [("stripe_bottom", "gray"), ("triangles_bottom", "gray"),
                                         ("border", "light_gray")], wall_facing="south")

    # ---- rubble spilled outside the collapsed corner (north-east)
    for x in range(8, 15):
        for z in range(0, 8):
            if b.get(x, 1, z) is not None or b.get(x, 0, z) is not None and x <= 12 and z >= 2:
                continue
            d = (x - 8) + (7 - z)
            if 3 <= d and rng.random() < max(0.0, 0.8 - 0.07 * d):
                b.set(x, 0, z, rng.choice(["expanse:limestone", "gravel", "cobblestone"]))
                b.set(x, 1, z, rng.choice([slab(LB), B("expanse:mossy_limestone"), B("mossy_cobblestone"),
                                           stairs(LB, rng.choice(["north", "east", "south", "west"])),
                                           B("expanse:limestone_bricks")]))
                if rng.random() < 0.3:
                    b.set(x, 2, z, slab(LB) if rng.random() < 0.6 else B("moss_carpet"))

    vines_on(b, rng, 0.06, ymin=2, region=lambda x, y, z: y < 14, max_len=4)
    return [b]
