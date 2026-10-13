"""Summit crosses (Gipfelkreuze) on the mountain tops (site=summit: SitedJigsawStructure finds the highest ground
round, y 190 and up). The peaks, the snowy slopes and groves, the windswept heights and the fjord mountains.

A cross the valley carried up in pieces and raised on the top, on a stone plinth, with the summit book in a
box at its foot. Not a cairn: the summit cairns (summit_cairn.py) stand on lesser tops and passes.

start pool (3 variants)
  tall   : a tall dark-oak cross on a stepped stone plinth in a skirt of piled stones, iron stays
           chained from its arms to anchor stones, a lightning rod on its head; the summit book in
           the box on the plinth (public chest) and a plank-on-stones bench facing the view; a
           flat stone by the bench covers the builders' tin (hidden)
  roofed : a spruce weather cross with a little shingled roof over its head and arms, on a cairn,
           a plaque for the parish that raised it, the book box and a log bench; the tin lies
           under a slab in the cairn's skirt
  renewed: the new cross beside the stump of the old one, its broken arm lying in the rocks where
           the storm threw it; a plaque says so
"""
import math

from blocks import B
from builder import Build, slab, stairs
from pieces import Piece
from regions import mc
from small_data import data_for
from smallkit import clear, flora, pick, ring

NAME = "summit_cross"
STRUCTURE = dict(biomes=["verdant_peaks", "dolomite_peaks", "volcanic_highlands", "fjordlands",
                         *mc("stony_peaks", "jagged_peaks", "frozen_peaks", "snowy_slopes", "grove", "meadow",
                             "windswept_hills", "windswept_gravelly_hills")],
                 size=1, max_distance=24, set="summits", site="summit", max_relief=28)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
PROCS = "small_peaks"
STONE = [("cobblestone", 3), ("stone", 3), ("andesite", 3), ("mossy_cobblestone", 1), ("tuff", 1)]
GROUND = ["grass_block", "grass_block", "coarse_dirt"]


def _skirt(b, rng, c, r0=2.6, r1=1.6):
    """Piled stones round the plinth: a full ring at the foot, a ragged second course."""
    for (x, z) in ring(c, c, 0, r0, (b.size[0], b.size[2])):
        b.set(x, 0, z, pick(rng, STONE))
    for (x, z) in ring(c, c, 0, r1, (b.size[0], b.size[2])):
        b.set(x, 1, z, pick(rng, STONE))
    for (x, z) in ring(c, c, r1 + 0.1, r0, (b.size[0], b.size[2])):
        if rng.random() < 0.3:
            b.set(x, 1, z, slab("cobblestone") if rng.random() < 0.6 else pick(rng, STONE))


def _bench(b, rng, x, z):
    """A plank laid on two stones."""
    for dx in (-1, 1):
        b.set(x + dx, 0, z, pick(rng, STONE))
        b.set(x + dx, 1, z, pick(rng, [("stone", 1), ("andesite", 1)]))
    b.set(x, 1, z, slab("spruce", "top"))


def tall():
    b = Build(f"{NAME}_tall", 13, 15, 13, seed=1907611)
    rng = b.rng
    c = 6
    _skirt(b, rng, c)
    for dx in (-1, 0, 1):                              # the dressed plinth: a 3x3 course, a stepped top
        for dz in (-1, 0, 1):
            b.set(c + dx, 1, c + dz, B("stone_bricks"))
    b.set(c, 2, c, B("stone_bricks"))
    for (dx, dz, f) in ((-1, 0, "east"), (1, 0, "west"), (0, -1, "south")):
        b.set(c + dx, 2, c + dz, stairs("stone_brick", f))
    b.chest(c, 2, c + 1, "south", LOOT)                # the summit book in its box
    top = 12
    for y in range(3, top + 1):
        b.set(c, y, c, B("dark_oak_log", axis="y"))
    for dx in (-2, -1, 1, 2):
        b.set(c + dx, 10, c, B("dark_oak_log", axis="x"))
    b.set(c, top + 1, c, B("lightning_rod"))
    # iron stays from the arm ends down to anchor stones
    for dx in (-2, 2):
        for y in range(3, 10):
            b.set(c + dx, y, c, B("iron_chain", axis="y"))
        b.set(c + dx, 2, c, pick(rng, [("stone", 1), ("andesite", 1)]))
        b.set(c + dx, 1, c, B("stone_bricks"))
    # bench facing the view, the builders' tin under a flat stone beside it
    _bench(b, rng, c, c + 5)
    b.chest(c + 3, 0, c + 5, "south", HIDDEN)
    b.set(c + 3, 1, c + 5, slab("smooth_stone"))
    clear(b, 0, 2, 0, 12, 4, 12)
    flora(b, [(x, z) for x in range(13) for z in range(13) if math.hypot(x - c, z - c) > 3], rng, 0.12, GROUND)
    return b


def roofed():
    b = Build(f"{NAME}_roofed", 11, 12, 12, seed=1907613)
    rng = b.rng
    c = 5
    _skirt(b, rng, c, 2.4, 1.5)
    b.set(c, 2, c, pick(rng, STONE))
    b.set(c, 1, c, B("stone_bricks"))
    top = 9
    for y in range(3, top + 1):
        b.set(c, y, c, B("spruce_log", axis="y"))
    for dx in (-2, -1, 1, 2):
        b.set(c + dx, 8, c, B("spruce_log", axis="x"))
    # the little roof over head and arms, ridge along the arms
    for x in range(c - 3, c + 4):
        b.set(x, 10, c - 1, stairs("spruce", "south"))
        b.set(x, 10, c + 1, stairs("spruce", "north"))
        b.set(x, 10, c, slab("spruce"))
    b.set(c, 10, c, B("spruce_planks"))
    b.set(c, 11, c, slab("spruce"))
    b.set(c - 1, 9, c, B("spruce_fence"))
    b.set(c + 1, 9, c, B("spruce_fence"))
    # the parish plaque and the book box at the foot
    b.sign(c, 4, c + 1, "spruce", ["Raised by the", "men of the", "valley", "MCCCLXXX"], wall_facing="south")
    b.barrel(c + 1, 2, c + 1, "up", LOOT)
    b.set(c - 1, 2, c + 1, B("expanse:potted_edelweiss"))
    # log bench facing the view; the tin under a slab in the skirt
    b.set(c - 1, 1, c + 5, B("spruce_log", axis="x"))
    b.set(c, 1, c + 5, B("spruce_log", axis="x"))
    b.set(c + 1, 1, c + 5, B("spruce_log", axis="x"))
    b.chest(c - 2, 0, c - 2, "north", HIDDEN)
    b.set(c - 2, 1, c - 2, slab("smooth_stone"))
    clear(b, 0, 3, 0, 10, 7, 11)
    flora(b, [(x, z) for x in range(11) for z in range(12) if math.hypot(x - c, z - c) > 2.8], rng, 0.12, GROUND)
    return b


def renewed():
    b = Build(f"{NAME}_renewed", 13, 13, 13, seed=1907615)
    rng = b.rng
    c = 5
    _skirt(b, rng, c, 2.2, 1.2)
    b.set(c, 1, c, B("stone_bricks"))
    b.set(c, 2, c, B("stone_bricks"))
    for y in range(3, 11):
        b.set(c, y, c, B("dark_oak_log", axis="y"))
    for dz in (-2, -1, 1, 2):
        b.set(c, 8, c + dz, B("dark_oak_log", axis="z"))
    b.set(c, 11, c, B("lightning_rod"))
    for dz in (-1, 1):                                 # iron clasps at the crossing
        b.set(c, 9, c + dz, B("iron_bars"))
    b.sign(c + 1, 3, c, "dark_oak", ["The old cross", "fell to the", "storm. Raised", "anew."], wall_facing="east")
    b.chest(c + 1, 2, c + 1, "east", LOOT)
    b.set(c + 1, 1, c + 1, B("stone_bricks"))
    # the old cross: its stump in a heap of stones, the arm thrown down the slope
    ox, oz = 9, 8
    for (x, z) in ring(ox, oz, 0, 1.5, (13, 13)):
        b.set(x, 0, z, pick(rng, STONE))
        if rng.random() < 0.45:
            b.set(x, 1, z, pick(rng, STONE))
    b.set(ox, 1, oz, B("spruce_log", axis="y"))
    b.set(ox, 2, oz, B("stripped_spruce_log", axis="y"))
    b.set(ox, 3, oz, slab("spruce"))
    for x in (7, 8, 9, 10):
        b.set(x, 1, 11, B("stripped_spruce_log", axis="x"))
        b.set(x, 0, 11, pick(rng, STONE))
    b.set(11, 1, 11, B("iron_chain", axis="x"))
    b.chest(10, 0, 4, "west", HIDDEN)
    b.set(10, 1, 4, slab("cobblestone"))
    clear(b, 0, 3, 0, 12, 6, 12)
    flora(b, [(x, z) for x in range(13) for z in range(13) if math.hypot(x - c, z - c) > 2.5], rng, 0.12, GROUND)
    return b


def pools():
    return {"start": [Piece(tall(), 3, PROCS), Piece(roofed(), 2, PROCS), Piece(renewed(), 2, PROCS)]}


DATA = data_for(NAME, [PROCS])
