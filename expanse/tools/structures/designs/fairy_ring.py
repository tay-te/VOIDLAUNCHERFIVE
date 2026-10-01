"""Fairy places of the lumen grove.

start pool (3 variants)
  ring     : a ring of glowcaps and toadstools round a glowing wellspring (sea pickles
             under the water, prismite on the rim), lumen stumps for seats, a cake left
             out on one; coins thrown in lie in a chest at the bottom of the spring, and
             a hollow stump holds the warden's box
  toadstool: a cottage in a giant toadstool - red cap, stem walls, a round window,
             tiny bed and shelf of glow berries, a picket garden; a hatch under the rug
  moonwell : a round basin of lit water under four lumen-wood arches hung with moss
             and lichen, an altar with prismite and candles; something glints on the
             bottom of the basin
"""
import math

from blocks import AIR, B
from builder import Build, item, stairs, trapdoor
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import book, flora, ground_item, pick, plant, ring, trail

NAME = "fairy_ring"
STRUCTURE = dict(biomes=["lumen_grove"], size=1, max_distance=32, set="biome_sights")
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
L = "expanse:lumen"
GROUND = ["moss_block", "moss_block", "grass_block", "rooted_dirt"]
PATH = [("moss_block", 2), ("rooted_dirt", 2), ("coarse_dirt", 2)]
GLOW = ["expanse:glowcap", "expanse:glowcap", "red_mushroom", "brown_mushroom"]


def stump(b, x, z, h=1, lichen=True):
    for y in range(1, h + 1):
        b.set(x, y, z, B(f"{L}_log", axis="y"))
    if lichen and b.get(x + 1, 1, z) is None:
        b.set(x + 1, 1, z, B("glow_lichen", west=True))


def wellspring():
    b = Build(f"{NAME}_ring", 15, 6, 15, seed=1907441)
    rng = b.rng
    c = 7
    # the spring: water lit from below by sea pickles, a chest of thrown coins on the bottom
    for (x, z) in ring(c, c, 0, 1.9, (15, 15)):
        b.set(x, 0, z, B("water", level=0))
        b.set(x, 1, z, AIR)
    b.chest(c, 0, c, "south", HIDDEN)
    b.set(c, 0, c, b.get(c, 0, c).with_(waterlogged=True), b.cells[(c, 0, c)][1])
    for (x, z) in ((c - 1, c), (c + 1, c - 1), (c, c + 1)):
        b.set(x, 0, z, B("sea_pickle", pickles=rng.randint(2, 4), waterlogged=True))
    for (x, z) in ring(c, c, 1.9, 2.9, (15, 15)):    # mossy rim with prismite
        b.set(x, 0, z, pick(rng, [("mossy_cobblestone", 3), ("moss_block", 2), ("expanse:prismite_block", 1)]))
        if rng.random() < 0.3:
            b.set(x, 1, z, B("expanse:prismite_cluster", facing="up"))
        elif rng.random() < 0.3:
            b.set(x, 1, z, B("moss_carpet"))
    # the ring itself
    for (x, z) in ring(c, c, 4.6, 5.5, (15, 15)):
        if rng.random() < 0.85:
            plant(b, x, 1, z, rng.choice(GLOW), "moss_block")
    # lumen stumps: seats, a cake left out, and the warden's box in a hollow one
    stump(b, c - 4, c + 3)
    stump(b, c + 3, c + 4)
    stump(b, c + 4, c - 2)
    b.set(c + 4, 2, c - 2, B("cake", bites=2))
    for (x, z) in ((c - 4, c - 3), (c - 4, c - 2), (c - 5, c - 3), (c - 5, c - 2)):   # a great old stump
        b.set(x, 1, z, B(f"{L}_log", axis="y"))
        b.set(x, 2, z, B(f"{L}_log", axis="y"))
    ground_item(b, c - 4, 3, c - 3, item("honey_bottle"))
    b.chest(c - 6, 1, c - 3, "west", LOOT)             # the warden's box, nested in the roots
    for (x, z) in ((c - 6, c - 4), (c - 6, c - 2), (c - 7, c - 3)):
        b.set(x, 1, z, B(f"{L}_log", axis="x" if z == c - 3 else "z"))
    b.set(c - 6, 0, c - 3, B("moss_block"))
    ground_item(b, c + 3, 2, c + 4, book(BOOKS[NAME]), rotation=1)
    # glow lichen spreading over the stones, a path of moss in
    trail(b, [(c, c + 3), (c + 1, 11), (c, 14)], rng, PATH, width=1, fade=0.4)
    flora(b, [(x, z) for x in range(15) for z in range(15) if math.hypot(x - c, z - c) > 5.6], rng, 0.3, GROUND,
          plants=("short_grass", "short_grass", "fern", "expanse:glowcap"))
    return b


def toadstool():
    b = Build(f"{NAME}_toadstool", 13, 11, 13, seed=1907443)
    rng = b.rng
    c = 6
    stem = B("mushroom_stem")
    for x in range(c - 2, c + 3):                     # stem walls (5x5, 3x3 inside)
        for z in range(c - 2, c + 3):
            b.set(x, 0, z, B("moss_block") if abs(x - c) < 2 and abs(z - c) < 2 else stem)
            for y in (1, 2, 3):
                inside = abs(x - c) < 2 and abs(z - c) < 2
                corner = abs(x - c) == 2 and abs(z - c) == 2
                if corner:
                    continue
                b.set(x, y, z, AIR if inside else stem)
    cap = B("red_mushroom_block")
    for y, r in ((4, 4.2), (5, 3.6), (6, 2.6), (7, 1.4)):
        for (x, z) in ring(c, c, 0, r, (13, 13)):
            b.set(x, y, z, cap)
    for (x, z) in ring(c, c, 3.3, 4.15, (13, 13)):   # the drooping lip
        if rng.random() < 0.8:
            b.set(x, 3, z, cap)
    b.door(c, 1, c + 2, "spruce", "south")
    b.set(c, 3, c + 3, AIR)
    b.set(c + 2, 2, c, B("glass_pane"))
    b.set(c - 2, 2, c, B("glass_pane"))
    # inside: tiny bed, shelf of glow berries, cauldron, a hanging lantern, rug over the hatch
    b.bed(c - 1, 1, c, "north", "red")
    b.shelf(c + 1, 2, c - 1, "spruce", "south", [item("glow_berries"), item("mushroom_stew"), item("glow_berries")])
    b.barrel(c + 1, 1, c - 1, "up", LOOT)
    b.set(c + 1, 1, c + 1, B("water_cauldron", level=2))
    b.set(c, 3, c, B("iron_chain", axis="y"))
    b.set(c, 2, c, B("lantern", hanging=True))
    b.set(c, 1, c, trapdoor("spruce", "north", "bottom", False))
    b.chest(c, 0, c, "north", HIDDEN)
    b.set(c - 1, 1, c + 1, B("red_carpet"))
    ground_item(b, c - 1, 2, c + 1, book(BOOKS[f"{NAME}_toadstool"]), rotation=0)
    # picket garden of lumen fence with glowcaps, a crooked path, a sign
    for x in range(1, 12):
        for z in (9, 12):
            if x not in (c - 1, c, c + 1) or z == 12:
                if rng.random() < 0.85:
                    b.set(x, 1, z, B(f"{L}_fence"))
    for x in range(1, 12):
        for z in (10, 11):
            if x not in (c - 1, c, c + 1) and rng.random() < 0.6:
                plant(b, x, 1, z, rng.choice(GLOW), "moss_block")
    trail(b, [(c, c + 3), (c, 9), (c + 1, 12)], rng, PATH, width=2, fade=0.7)
    b.set(c + 2, 1, 8, B(f"{L}_fence"))
    b.sign(c + 2, 2, 8, "spruce", ["Mind the", "ring.", "Wipe your", "feet."], rotation=0)
    flora(b, [(x, z) for x in range(13) for z in range(13) if z < 9], rng, 0.25, GROUND,
          plants=("short_grass", "fern", "expanse:glowcap"))
    return b


def moonwell():
    b = Build(f"{NAME}_moonwell", 13, 9, 13, seed=1907445)
    rng = b.rng
    c = 6
    for (x, z) in ring(c, c, 0, 2.2, (13, 13)):       # basin: lit floor under a skin of water
        b.set(x, 0, z, B("sea_lantern"))
        b.set(x, 1, z, B("water", level=0))
    for (x, z) in ring(c, c, 2.2, 3.2, (13, 13)):     # rim
        b.set(x, 0, z, pick(rng, [("mossy_stone_bricks", 3), ("stone_bricks", 2)]))
        b.set(x, 1, z, B("mossy_stone_brick_slab", type="bottom") if rng.random() < 0.3 else
              pick(rng, [("mossy_stone_bricks", 2), ("stone_bricks", 2), ("cracked_stone_bricks", 1)]))
    b.chest(c + 1, 0, c - 1, "south", HIDDEN)
    # four arches of lumen wood, hung with moss and lichen
    for (ax, az, along) in ((c, c - 4, "x"), (c, c + 4, "x"), (c - 4, c, "z"), (c + 4, c, "z")):
        dx, dz = (1, 0) if along == "x" else (0, 1)
        for s in (-1, 1):
            px, pz = ax + s * dx, az + s * dz
            b.set(px, 0, pz, B("stone_bricks"))
            for y in (1, 2, 3):
                b.set(px, y, pz, B(f"{L}_log", axis="y"))
        for s in (-1, 0, 1):
            b.set(ax + s * dx, 4, az + s * dz, B(f"expanse:stripped_lumen_log", axis=along))
        b.set(ax - 2 * dx, 4, az - 2 * dz, stairs(L, "east" if along == "x" else "south", "top"))
        b.set(ax + 2 * dx, 4, az + 2 * dz, stairs(L, "west" if along == "x" else "north", "top"))
        b.hang_moss(ax, 3, az, rng.randint(1, 2))
        for s in (-1, 1):                             # lichen on the outer face of each post
            if rng.random() < 0.6:
                px, pz = ax + s * dx, az + s * dz
                if along == "x":
                    b.set(px, 2, pz + 1, B("glow_lichen", north=True))
                else:
                    b.set(px + 1, 2, pz, B("glow_lichen", west=True))
    # altar with prismite and candles on the north side
    b.set(c, 1, c - 6, B("chiseled_stone_bricks"))
    b.set(c - 1, 1, c - 6, stairs("stone_brick", "east"))
    b.set(c + 1, 1, c - 6, stairs("stone_brick", "west"))
    b.set(c, 2, c - 6, B("expanse:prismite_cluster", facing="up"))
    b.set(c + 1, 0, c - 5, B("stone_bricks"))
    b.set(c + 1, 1, c - 5, B("purple_candle", candles=3, lit=True))
    b.pot(c - 1, 1, c - 5, "south", ("brick", "flow_pottery_sherd", "brick", "brick"), loot=LOOT)
    ground_item(b, c, 1, c - 5, book(BOOKS[f"{NAME}_moonwell"]), rotation=0)
    trail(b, [(c, c + 5), (c, 12)], rng, PATH, width=1, fade=0.4)
    flora(b, [(x, z) for x in range(13) for z in range(13) if math.hypot(x - c, z - c) > 3.4], rng, 0.3, GROUND,
          plants=("short_grass", "fern", "expanse:glowcap"))
    return b


def pools():
    return {"start": [Piece(wellspring(), 3, "small_lumen"), Piece(toadstool(), 2, "small_lumen"),
                      Piece(moonwell(), 2, "small_lumen")]}


DATA = data_for(NAME, ["small_lumen"])
