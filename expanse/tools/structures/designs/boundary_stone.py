"""Boundary stones where one country gives way to another (site=border: SitedJigsawStructure finds the line).
Most of the land: the lowlands and broadleaf woods, the taigas and uplands, the moors and the snowlands.

A dressed stone set at the line between two lordships, with the marks cut into it; nothing kept in it.

start pool (3 variants)
  post  : one dressed post, a carved block on a squared shaft under a weathered cap, its number cut on
          one face and the year on the other; the grass trodden round it
  cairn : a shorter stone with the mark on its head, a cairn of field stones heaped beside it by
          the men who walked the bounds, and a worn track along the line
  pair  : two low stones facing each other across the line, each with its lord's letter on the
          side that looks into his land, and a thorn bush grown up by one of them
"""
from blocks import B
from builder import Build, slab, stairs
from pieces import Piece
from regions import MEADOW_LANDS, SNOW_LANDS, TAIGA_LANDS, mc
from small_data import data_for
from smallkit import clear, flora, pick, plant, trail

NAME = "boundary_stone"


def _uniq(xs):
    out = []
    for x in xs:
        if x not in out:
            out.append(x)
    return out


STRUCTURE = dict(biomes=_uniq([*MEADOW_LANDS, *TAIGA_LANDS, *SNOW_LANDS, "heather_moor", "woodland_glades",
                               "ancient_oakwood", "redwood_giants", "verdant_peaks", "dolomite_peaks", "lichen_tundra",
                               *mc("stony_peaks", "savanna", "savanna_plateau")]),
                 size=1, max_distance=16, set="borders", site="border", max_relief=24)
PROCS = "small_meadow"
STONE = [("cobblestone", 3), ("stone", 3), ("andesite", 2), ("mossy_cobblestone", 2)]
GROUND = ["grass_block", "grass_block", "coarse_dirt"]
PATH = [("coarse_dirt", 4), ("dirt_path", 3), ("gravel", 1)]
TRODDEN = [("coarse_dirt", 3), ("grass_block", 4), ("gravel", 1)]


def _footing(b, rng, x, z, r=1):
    """Packing stones round the foot of a post, half sunk in the turf."""
    b.set(x, 0, z, pick(rng, [("cobblestone", 2), ("stone", 2)]))
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if (dx or dz) and b.inb(x + dx, 0, z + dz) and b.get(x + dx, 0, z + dz) is None:
                if abs(dx) + abs(dz) == 1 and rng.random() < 0.7:
                    b.set(x + dx, 0, z + dz, pick(rng, STONE))
                elif rng.random() < 0.5:
                    b.set(x + dx, 0, z + dz, pick(rng, TRODDEN))


def post():
    b = Build(f"{NAME}_post", 7, 5, 7, seed=1907701)
    rng = b.rng
    c = 3
    _footing(b, rng, c, c)
    b.set(c, 1, c, B("stone_bricks"))
    b.set(c, 2, c, B("chiseled_stone_bricks"))
    b.set(c, 3, c, slab("smooth_stone"))
    # the cut marks: its number toward one land, the year it was set toward the other
    b.sign(c, 1, c + 1, "spruce", ["", "+", "No. 47", ""], wall_facing="south")
    b.sign(c, 1, c - 1, "spruce", ["", "+", "MCCCXCI", ""], wall_facing="north")
    clear(b, 1, 1, 1, 5, 3, 5)
    flora(b, [(x, z) for x in range(7) for z in range(7) if abs(x - c) + abs(z - c) >= 2], rng, 0.3, GROUND)
    return b


def cairn():
    b = Build(f"{NAME}_cairn", 9, 5, 7, seed=1907703)
    rng = b.rng
    sx, sz = 3, 3
    _footing(b, rng, sx, sz)
    b.set(sx, 1, sz, B("stone_bricks"))
    b.set(sx, 2, sz, B("chiseled_stone_bricks"))
    b.sign(sx, 2, sz + 1, "spruce", ["", "X", "", ""], wall_facing="south")
    # the bounds-walkers' cairn: field stones heaped up, a capstone
    cx, cz = 6, 3
    for (x, z) in ((cx, cz), (cx + 1, cz), (cx, cz + 1), (cx + 1, cz + 1), (cx, cz - 1), (cx - 1, cz + 1)):
        b.set(x, 0, z, pick(rng, STONE))
        if (x, z) in ((cx, cz), (cx + 1, cz), (cx, cz + 1)) or rng.random() < 0.35:
            b.set(x, 1, z, pick(rng, STONE))
    b.set(cx, 2, cz, slab("cobblestone") if rng.random() < 0.5 else B("mossy_cobblestone"))
    b.set(cx + 1, 2, cz, stairs("cobblestone", "west"))
    # the track the bound-walkers wear along the line (east-west)
    trail(b, [(0, 5), (4, 5), (8, 5)], rng, PATH, width=1, fade=0.6, edge=0.25)
    clear(b, 0, 1, 0, 8, 3, 6)
    flora(b, [(x, z) for x in range(9) for z in range(7)], rng, 0.25, GROUND)
    return b


def pair():
    b = Build(f"{NAME}_pair", 7, 5, 11, seed=1907705)
    rng = b.rng
    x = 3
    for z, out, letter in ((1, "north", "R"), (9, "south", "W")):
        _footing(b, rng, x, z)
        b.set(x, 1, z, B("stone_bricks"))
        b.set(x, 2, z, slab("stone_brick"))
        dz = -1 if out == "north" else 1
        # each lord's letter looks out into his own land; the cross on the other face looks at the line
        b.sign(x, 1, z + dz, "spruce", ["", letter, "", ""], wall_facing=out)
        b.sign(x, 1, z - dz, "spruce", ["", "+", "", ""], wall_facing="south" if out == "north" else "north")
    # the line itself: a few half-buried witness stones between the two
    for z in (4, 6):
        b.set(x, 0, z, pick(rng, [("andesite", 1), ("stone", 1)]))
    plant(b, 5, 1, 2, "bush", "grass_block")
    plant(b, 5, 1, 1, "sweet_berry_bush", "grass_block")
    plant(b, 4, 1, 3, "fern", "grass_block")
    clear(b, 0, 1, 0, 6, 3, 10)
    flora(b, [(xx, zz) for xx in range(7) for zz in range(11)], rng, 0.25, GROUND)
    return b


def pools():
    return {"start": [Piece(post(), 3, PROCS), Piece(cairn(), 2, PROCS), Piece(pair(), 2, PROCS)]}


DATA = data_for(NAME, [PROCS])
