"""Karst pagoda: three tiers of redwood columns and palm plank walls with
wisteria lattice windows on a limestone-brick podium, dark oak roofs with
upturned eaves, lanterns under every eave tip, a bell hung in the gate at the
foot of the steps, and the loot chest in the top tier."""
from blocks import AIR, B
from builder import Build, slab, stairs, trapdoor

NAME = "karst_pagoda"
LOOT = "expanse:chests/karst_pagoda"
C = 7
LB = "expanse:limestone_brick"
ROOF = "dark_oak"
COL = B("expanse:stripped_redwood_log", axis="y")


def ring(h):
    """Cells of the square ring at Chebyshev radius h around the centre."""
    out = []
    for x in range(C - h, C + h + 1):
        for z in range(C - h, C + h + 1):
            if max(abs(x - C), abs(z - C)) == h:
                out.append((x, z))
    return out


def inward(x, z):
    dx, dz = x - C, z - C
    if abs(dx) >= abs(dz):
        return "west" if dx > 0 else "east"
    return "north" if dz > 0 else "south"


def roof_tier(b, y, h_eave, h_top, lanterns=True):
    """Eave two rings wide at y, then one ring per level up to h_top; corners upturned."""
    for h in (h_eave, h_eave - 1):
        for (x, z) in ring(h):
            corner = abs(x - C) == h and abs(z - C) == h
            if corner and h == h_eave:
                b.set(x, y, z, B(f"{ROOF}_planks"))
                b.set(x, y + 1, z, stairs(ROOF, "north" if z > C else "south", "bottom"))
            else:
                b.set(x, y, z, stairs(ROOF, inward(x, z)))
    # soffit under the eave (upside-down slabs give the eave some thickness)
    for (x, z) in ring(h_eave - 1):
        if b.get(x, y - 1, z) is None:
            b.set(x, y - 1, z, slab(ROOF, "top"))
    k = 1
    for h in range(h_eave - 2, h_top - 1, -1):
        for (x, z) in ring(h):
            b.set(x, y + k, z, stairs(ROOF, inward(x, z)) if h > 0 else B(f"{ROOF}_planks"))
            if b.get(x, y + k - 1, z) is None:
                b.set(x, y + k - 1, z, B(f"{ROOF}_planks"))
        k += 1
    if lanterns:
        for sx in (-1, 1):
            for sz in (-1, 1):
                b.set(C + sx * h_eave, y - 1, C + sz * h_eave, B("lantern", hanging=True))


def tier_walls(b, h, y0, y1, door=False):
    b.air(C - h + 1, y0, C - h + 1, C + h - 1, y1, C + h - 1)
    for (x, z) in ring(h):
        off = x - C if abs(z - C) == h else z - C   # position along the wall
        corner = abs(x - C) == h and abs(z - C) == h
        for y in range(y0, y1 + 1):
            if corner or (abs(off) == h - 2 and h >= 3):
                b.set(x, y, z, COL)
            elif y == y1:
                b.set(x, y, z, B(f"{ROOF}_planks"))
            elif y == y0:
                b.set(x, y, z, B("expanse:palm_planks"))
            else:
                b.set(x, y, z, B("expanse:palm_planks"))
    # lattice windows: wisteria trapdoors set open against the wall line
    for (x, z) in ring(h):
        off = x - C if abs(z - C) == h else z - C
        corner = abs(x - C) == h and abs(z - C) == h
        if corner or abs(off) == h - 2 or (door and z == C + h and abs(off) <= 0):
            continue
        if abs(off) <= h - 3 or h <= 2:
            if abs(z - C) == h:
                f = "south" if z > C else "north"
            else:
                f = "east" if x > C else "west"
            for y in range(y0 + 1, y1):
                b.set(x, y, z, trapdoor("expanse:wisteria", f, "bottom", True))


def build():
    b = Build(NAME, 15, 25, 16, seed=1847421)
    rng = b.rng

    # ---- podium y0..2 (13x13) with steps to the south, balustrade on top
    for x in range(1, 14):
        for z in range(1, 14):
            for y in range(0, 3):
                edge = x in (1, 13) or z in (1, 13)
                b.set(x, y, z, B(f"{LB}s") if edge or y < 2 else
                      (B("expanse:polished_limestone") if (x + z) % 2 else B("expanse:limestone_bricks")))
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        b.column(x, z, 0, 2, B("expanse:chiseled_limestone"))
    for x in range(1, 14):
        b.set(x, 2, 1, stairs(LB, "north", "top"))
        b.set(x, 2, 13, stairs(LB, "south", "top"))
    for z in range(2, 13):
        b.set(1, 2, z, stairs(LB, "west", "top"))
        b.set(13, 2, z, stairs(LB, "east", "top"))
    for x in (5, 6, 7, 8, 9):
        b.set(x, 2, 13, B("expanse:polished_limestone"))
        b.set(x, 1, 14, stairs(LB, "north"))
        b.set(x, 2, 14, AIR)
        b.set(x, 0, 14, B("expanse:limestone_bricks"))
    for (x, z) in [(x, 1) for x in range(1, 14)] + [(1, z) for z in range(2, 14)] + \
                  [(13, z) for z in range(2, 14)] + [(x, 13) for x in range(1, 14)]:
        if 5 <= x <= 9 and z == 13:
            continue
        b.set(x, 3, z, B(f"{LB}_wall"))
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13), (4, 13), (10, 13)):
        b.set(x, 3, z, B("expanse:chiseled_limestone"))
        b.set(x, 4, z, B("lantern"))
    b.air(2, 3, 2, 12, 6, 12, only_void=True)

    # ---- tier 1 (9x9) y3..6, tier 2 (7x7) y8..10, tier 3 (5x5) y12..14
    tier_walls(b, 4, 3, 6, door=True)
    b.fill(C - 4, 7, C - 4, C + 4, 7, C + 4, B(f"{ROOF}_planks"))
    roof_tier(b, 7, 6, 4)
    tier_walls(b, 3, 8, 11)
    b.fill(C - 3, 12, C - 3, C + 3, 12, C + 3, B(f"{ROOF}_planks"))
    roof_tier(b, 12, 5, 3)
    tier_walls(b, 2, 13, 16)
    b.fill(C - 2, 17, C - 2, C + 2, 17, C + 2, B(f"{ROOF}_planks"))
    roof_tier(b, 17, 4, 0)
    b.set(C, 21, C, B(f"{ROOF}_planks"))
    b.set(C, 22, C, B(f"{ROOF}_fence"))
    b.set(C, 23, C, B("gold_block"))
    b.set(C, 24, C, B("lightning_rod"))

    # front door of tier 1
    b.door(C, 3, C + 4, "dark_oak", "south")

    # ---- interior access: ladders
    b.column(C, C - 3, 3, 6, B("bookshelf"))               # screen carrying the first ladder
    for y in range(3, 8):
        b.set(C, y, C - 2, B("ladder", facing="south"))
    b.column(C - 2, C - 1, 8, 11, B("bookshelf"))
    for y in range(8, 13):
        b.set(C - 1, y, C - 1, B("ladder", facing="east"))

    # ---- tier 1: shrine hall
    y = 3
    b.set(C - 3, y, C - 3, B("decorated_pot", facing="south"))
    b.set(C + 3, y, C - 3, B("decorated_pot", facing="south"))
    b.set(C - 2, y, C - 3, B("expanse:potted_wisteria_sapling"))
    b.set(C + 2, y, C - 3, B("expanse:potted_wisteria_sapling"))
    b.set(C - 3, y, C, B("lectern", facing="east"))
    b.set(C + 3, y, C, B("barrel", facing="up"))
    b.set(C + 3, y, C + 1, B("barrel", facing="west"))
    for x in range(C - 1, C + 2):
        for z in range(C - 1, C + 3):
            b.set(x, y, z, B("red_carpet") if (x == C) else B("purple_carpet"))
    b.set(C - 2, 6, C + 1, B("lantern", hanging=True))
    b.set(C + 2, 6, C + 1, B("lantern", hanging=True))
    b.set(C - 3, y, C + 3, B("potted_azalea_bush"))
    b.set(C + 3, y, C + 3, B("white_candle", candles=3, lit=True))

    # ---- tier 2: library
    y = 8
    b.set(C + 2, y, C - 2, B("bookshelf"))
    b.set(C + 2, y + 1, C - 2, B("bookshelf"))
    b.set(C + 2, y, C + 2, B("chiseled_bookshelf", facing="west", slot_0_occupied=True, slot_3_occupied=True))
    b.set(C - 2, y, C + 2, B("barrel", facing="up"))
    b.set(C + 1, y, C + 2, B("red_carpet"))
    b.set(C, y, C + 1, B("red_carpet"))
    b.set(C, 11, C, B("lantern", hanging=True))

    # ---- tier 3: treasure room
    y = 13
    b.chest(C + 1, y, C + 1, "north", LOOT)
    b.set(C - 1, y, C + 1, B("lantern"))
    b.set(C + 1, y, C - 1, B("expanse:potted_wisteria_sapling"))
    b.bell(C, 16, C, attachment="ceiling")

    # ---- gate with the bell at the foot of the steps
    for x in (4, 10):
        b.column(x, 14, 1, 5, COL)
        b.set(x, 0, 14, B("expanse:chiseled_limestone"))
    for x in range(3, 12):
        b.set(x, 6, 14, B("expanse:stripped_redwood_log", axis="x"))
    for x in range(3, 12):
        b.set(x, 7, 14, slab(ROOF) if x not in (3, 11) else stairs(ROOF, "east" if x == 3 else "west"))
    b.bell(C, 5, 14, attachment="ceiling", facing="east")
    b.air(5, 2, 14, 9, 5, 14, only_void=True)

    # wisteria accents: blossom bushes at the podium's front corners
    for (x, z) in ((2, 12), (12, 12), (2, 2), (12, 2)):
        b.set(x, 3, z, B("expanse:wisteria_leaves"))
        if rng.random() < 0.6:
            b.set(x, 4, z, B("expanse:wisteria_leaves"))
    return [b]
