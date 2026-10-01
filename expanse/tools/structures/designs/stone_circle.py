"""Stone circle on the heather moor: a ring of weathered standing stones, two
trilithons, a dolmen altar sheltering the loot chest, heather and edelweiss."""
import math

from blocks import AIR, B
from builder import Build, slab, stairs
from common import circle_cells

NAME = "stone_circle"
LOOT = "expanse:chests/stone_circle"

STONE = ["stone"] * 5 + ["andesite"] * 3 + ["mossy_cobblestone"] * 2 + ["cobblestone"]
SLABS = {"stone": "stone", "andesite": "andesite", "mossy_cobblestone": "mossy_cobblestone",
         "cobblestone": "cobblestone"}


def _stone(b, x, z, h, rng, axis, width=1, cap=True):
    cells = [(x, z)]
    if width == 2:
        cells.append((x + 1, z) if axis == "x" else (x, z + 1))
    for (sx, sz) in cells:
        hh = h - (rng.random() < 0.35 and width == 2)
        for y in range(0, hh + 1):
            b.set(sx, y, sz, rng.choice(STONE))
        top = b.get(sx, hh, sz).short
        r = rng.random()
        if cap and r < 0.35:
            b.set(sx, hh + 1, sz, slab(SLABS[top]))
        elif cap and r < 0.5 and top in ("stone", "cobblestone", "mossy_cobblestone", "andesite"):
            b.set(sx, hh + 1, sz, stairs(SLABS[top], rng.choice(["north", "south", "east", "west"])))
        elif r < 0.75:
            b.set(sx, hh + 1, sz, B("moss_carpet"))


def _trilithon(b, x, z, h, rng, axis, broken=False):
    off = [(-1, 0), (1, 0)] if axis == "x" else [(0, -1), (0, 1)]
    for dx, dz in off:
        for y in range(0, h + 1):
            b.set(x + dx, y, z + dz, rng.choice(["stone", "stone", "andesite", "mossy_cobblestone"]))
    # lintel
    span = [(x - 1, z), (x, z), (x + 1, z)] if axis == "x" else [(x, z - 1), (x, z), (x, z + 1)]
    for i, (lx, lz) in enumerate(span):
        if broken and i == 2:
            continue
        b.set(lx, h + 1, lz, rng.choice(["andesite", "stone", "polished_andesite"]))
    if broken:
        # the fallen end of the lintel lies at the foot of the stones
        fx, fz = span[2]
        b.set(fx + (1 if axis == "x" else 0), 1, fz + (1 if axis == "z" else 0), slab("andesite"))
    else:
        for (lx, lz) in span[::2]:
            if b.rng.random() < 0.6:
                b.set(lx, h + 2, lz, B("moss_carpet"))
    # keep the gate open
    b.set(x, 1, z, AIR)
    b.set(x, 2, z, AIR)


def _fallen(b, x, z, rng, axis):
    cells = [(x - 1, z), (x, z), (x + 1, z)] if axis == "x" else [(x, z - 1), (x, z), (x, z + 1)]
    for i, (fx, fz) in enumerate(cells):
        b.set(fx, 0, fz, rng.choice(["stone", "andesite"]))
        b.set(fx, 1, fz, rng.choice(["mossy_cobblestone", "stone", "andesite"]) if i < 2 else slab("mossy_cobblestone"))
        if rng.random() < 0.5:
            b.set(fx, 2, fz, B("moss_carpet"))


def build_variant(variant):
    seed = 1847261 + variant * 31
    b = Build(f"{NAME}_{variant}", 17, 9, 17, seed=seed)
    rng = b.rng
    cx = cz = 8
    n = 9 if variant == 1 else 11
    radius = 6.5
    phase = math.pi / n  # leave the south (+z) point empty for the entrance path

    # clear a level floor inside the ring (keeps cut terrain tidy)
    for (x, z) in circle_cells(cx, cz, 7.2):
        b.air(x, 1, z, x, 3, z)

    gate = n // 4  # stone index used for the main trilithon
    for i in range(n):
        theta = phase + 2 * math.pi * i / n + rng.uniform(-0.06, 0.06)
        x = int(round(cx + radius * math.sin(theta)))
        z = int(round(cz - radius * math.cos(theta)))
        axis = "x" if abs(math.sin(theta)) < abs(math.cos(theta)) else "z"  # tangent to the ring
        if variant == 1 and i in (gate, gate + n // 2):
            _trilithon(b, x, z, 4, rng, axis)
        elif variant == 2 and i == gate:
            _trilithon(b, x, z, 4, rng, axis, broken=False)
        elif variant == 2 and i == gate + n // 2:
            _trilithon(b, x, z, 4, rng, axis, broken=True)
        elif variant == 2 and i == n - 2:
            _fallen(b, x, z, rng, "z" if axis == "x" else "x")
        else:
            h = rng.choice([2, 3, 3, 4, 4, 5])
            w = 2 if rng.random() < 0.2 else 1
            _stone(b, x, z, h, rng, axis, width=w)

    # paved, worn centre
    for (x, z) in circle_cells(cx, cz, 2.3):
        b.set(x, 0, z, rng.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "cobblestone",
                                   "mossy_cobblestone", "gravel"]))
    # entrance path to the south
    for z in range(cz + 3, 17):
        for x in (cx - 1, cx, cx + 1):
            if rng.random() < (0.85 if x == cx else 0.35):
                b.set(x, 0, z, rng.choice(["dirt_path", "dirt_path", "coarse_dirt", "gravel"]))

    if variant == 1:
        # dolmen: two uprights and a capstone sheltering the chest
        for x in (cx - 1, cx + 1):
            b.set(x, 1, cz, "polished_andesite")
            b.set(x, 2, cz, "andesite")
        for x in range(cx - 2, cx + 3):
            b.set(x, 3, cz, slab("andesite") if x in (cx - 2, cx + 2) else B("polished_andesite"))
        b.chest(cx, 1, cz, "south", LOOT)
        b.set(cx, 2, cz, AIR)
        b.set(cx - 1, 4, cz, B("white_candle", candles=3, lit=True))
        b.set(cx + 1, 4, cz, B("candle", candles=2, lit=True))
        b.set(cx, 4, cz, B("moss_carpet"))
        b.set(cx, 1, cz - 1, stairs("stone_brick", "south"))
        b.set(cx, 1, cz + 1, slab("stone_brick"))
    else:
        # altar table: chiseled block flanked by steps, chest on top, lanterns
        b.set(cx, 1, cz, "chiseled_stone_bricks")
        b.set(cx - 1, 1, cz, stairs("stone_brick", "east"))
        b.set(cx + 1, 1, cz, stairs("stone_brick", "west"))
        b.set(cx, 1, cz + 1, stairs("mossy_stone_brick", "north"))
        b.set(cx, 1, cz - 1, stairs("stone_brick", "south"))
        b.chest(cx, 2, cz, "south", LOOT)
        for (x, z) in ((cx - 1, cz - 1), (cx + 1, cz - 1), (cx - 1, cz + 1), (cx + 1, cz + 1)):
            b.set(x, 1, z, "mossy_cobblestone")
            b.set(x, 2, z, B("lantern") if (x + z) % 4 == 0 else B("white_candle", candles=2, lit=True))

    # heel stone standing beside the entrance path
    hx, hz = cx + 2, 15
    for y in range(0, 4):
        b.set(hx, y, hz, rng.choice(["stone", "andesite", "mossy_cobblestone"]))
    b.set(hx, 4, hz, slab("andesite"))

    # heather, edelweiss and grass around (ground under each plant made grass)
    plants = []
    for x in range(17):
        for z in range(17):
            d = math.hypot(x - cx, z - cz)
            if d < 2.6 or d > 8.4:
                continue
            if b.get(x, 1, z) not in (None, AIR) or b.get(x, 0, z) is not None:
                continue
            r = rng.random()
            dens = 0.36 if d > 5.2 else 0.18
            if r < dens * 0.62:
                plants.append((x, z, "expanse:heather"))
            elif r < dens * 0.82:
                plants.append((x, z, "expanse:edelweiss"))
            elif r < dens:
                plants.append((x, z, rng.choice(["short_grass", "fern", "short_grass"])))
    for x, z, p in plants:
        b.set(x, 0, z, B("grass_block"))
        b.set(x, 1, z, p)
    return b


def build():
    return [build_variant(1), build_variant(2)]
