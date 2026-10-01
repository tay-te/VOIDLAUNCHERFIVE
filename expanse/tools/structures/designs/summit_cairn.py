"""Summit cairns of the high country (verdant peaks, prismatic peaks, frostbloom tundra).

start pool (3 variants)
  prayer   : a tall cairn with a flag pole, strings of prayer flags round it on four
             poles, edelweiss left in a pot, and the summit register in a stone box at
             its foot (public chest); a flat stone nearby covers a climber's stash
  shelter  : a horseshoe windbreak of drystone with a bench and a cold fire pit, a trig
             pillar beside it; the hut-keeper's box is under the bench
  memorial : a memorial cairn with an ice axe laid on top, a coil of rope, a plaque and
             a bench facing the view; the climber's pack is buried at the cairn's foot
"""
import math

from blocks import B
from builder import Build, item, slab, stairs
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import book, clear, flora, ground_item, pick, ring, snow_cap, trail

NAME = "summit_cairn"
STRUCTURE = dict(biomes=["verdant_peaks", "prismatic_peaks", "frostbloom_tundra"], size=1, max_distance=32,
                 set="wayside", weight=2)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
STONE = [("cobblestone", 3), ("stone", 3), ("andesite", 2), ("mossy_cobblestone", 1), ("tuff", 1)]
GROUND = ["grass_block", "stone", "grass_block", "coarse_dirt"]
PATH = [("gravel", 4), ("coarse_dirt", 2), ("stone", 1)]
FLAGS = ["red", "yellow", "green", "blue", "white"]


def cairn(b, rng, cx, cz, radii):
    for y, r in enumerate(radii, start=0):
        for (x, z) in ring(cx, cz, 0, r, (b.size[0], b.size[2])):
            if r < 1.2 or math.hypot(x - cx, z - cz) <= r - 0.4 or rng.random() < 0.7:
                b.set(x, y, z, pick(rng, STONE))
    return len(radii)


def flag_string(b, rng, p0, p1, y):
    """A string of prayer flags (alternating wool top-slabs) between two pole tops on one axis."""
    (x0, z0), (x1, z1) = p0, p1
    cells = [(x, z0) for x in range(min(x0, x1) + 1, max(x0, x1))] if z0 == z1 else \
        [(x0, z) for z in range(min(z0, z1) + 1, max(z0, z1))]
    for i, (x, z) in enumerate(cells):
        if b.get(x, y, z) is None:
            b.set(x, y, z, slab(f"{FLAGS[i % len(FLAGS)]}_wool", "top"))


def prayer():
    b = Build(f"{NAME}_prayer", 13, 10, 13, seed=1907521)
    rng = b.rng
    c = 6
    top = cairn(b, rng, c, c, [2.6, 2.2, 1.7, 1.2, 0.6])
    for y in range(top, top + 3):
        b.set(c, y, c, B("spruce_log", axis="y"))
    b.banner(c + 1, top + 2, c, "red", [("stripe_center", "yellow")], wall_facing="east")
    b.banner(c - 1, top + 2, c, "blue", [("stripe_center", "white")], wall_facing="west")
    # four poles and the strings of flags between them
    poles = [(c - 4, c - 4), (c + 4, c - 4), (c + 4, c + 4), (c - 4, c + 4)]
    for (x, z) in poles:
        b.set(x, 0, z, pick(rng, STONE))
        for y in (1, 2, 3):
            b.set(x, y, z, B("spruce_fence"))
        b.set(x, 4, z, slab("spruce"))
    for p0, p1 in zip(poles, poles[1:] + poles[:1]):
        flag_string(b, rng, p0, p1, 3)
    # the summit register in a stone box at the foot, an offering, the flat stone over a stash
    b.chest(c, 1, c + 2, "south", LOOT)
    b.set(c, 2, c + 2, slab("stone"))
    b.set(c - 1, 1, c + 3, B("expanse:potted_edelweiss"))
    b.set(c + 1, 0, c + 3, pick(rng, STONE))
    b.set(c + 1, 1, c + 3, B("spruce_fence"))
    b.sign(c + 1, 2, c + 3, "spruce", ["SUMMIT", "Sign the book,", "close the lid,", "go carefully."],
           rotation=0)
    b.chest(2, 0, 9, "north", HIDDEN)
    b.set(2, 1, 9, slab("smooth_stone"))
    trail(b, [(c, c + 3), (c + 1, 10), (c, 12)], rng, PATH, width=1, fade=0.4)
    flora(b, [(x, z) for x in range(13) for z in range(13) if math.hypot(x - c, z - c) > 3], rng, 0.2, GROUND)
    snow_cap(b, rng, 0.35, layers=(1, 1, 2))
    return b


def shelter():
    b = Build(f"{NAME}_shelter", 13, 6, 13, seed=1907523)
    rng = b.rng
    c = 6
    for (x, z) in ring(c, c, 2.6, 3.6, (13, 13)):     # horseshoe wall, open to the south
        if z > c + 1 and abs(x - c) <= 1:
            continue
        b.set(x, 0, z, pick(rng, STONE))
        h = 2 if rng.random() < 0.75 else 1
        for y in range(1, h + 1):
            b.set(x, y, z, pick(rng, STONE))
        if h == 2 and rng.random() < 0.4:
            b.set(x, 3, z, slab("cobblestone"))
    clear(b, c - 2, 1, c - 2, c + 2, 2, c + 2)
    for x in range(c - 2, c + 3):                     # bench along the back wall
        b.set(x, 1, c - 2, slab("smooth_stone", "top"))
    b.chest(c, 0, c - 2, "south", HIDDEN)
    b.campfire(c, 1, c, lit=False)
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        b.set(c + dx, 0, c + dz, B("cobblestone"))
    b.barrel(c + 2, 1, c, "west", LOOT)
    ground_item(b, c - 1, 2, c - 2, book(BOOKS[f"{NAME}_shelter"]), rotation=0)
    # trig pillar outside, with its brass mount
    b.set(10, 0, 10, B("stone_bricks"))
    b.set(10, 1, 10, B("smooth_stone_slab", type="double"))
    b.set(10, 2, 10, B("smooth_stone_slab", type="double"))
    b.set(10, 3, 10, B("lodestone"))
    trail(b, [(c, c + 3), (c - 1, 10), (c, 12)], rng, PATH, width=1, fade=0.4)
    flora(b, [(x, z) for x in range(13) for z in range(13) if math.hypot(x - c, z - c) > 3.8], rng, 0.2, GROUND)
    snow_cap(b, rng, 0.35, layers=(1, 2))
    return b


def memorial():
    b = Build(f"{NAME}_memorial", 11, 8, 11, seed=1907525)
    rng = b.rng
    c = 5
    top = cairn(b, rng, c, c, [2.0, 1.5, 1.0, 0.6])
    b.set(c, top, c, B("polished_andesite"))
    ground_item(b, c, top + 1, c, item("iron_pickaxe"), rotation=1)
    b.item_frame(c, top - 1, c + 1, "south", item("lead"))
    b.set(c, 0, c + 3, pick(rng, STONE))
    b.set(c, 1, c + 3, B("polished_andesite"))
    b.sign(c, 1, c + 4, "spruce", ["For Brannagh,", "who went", "higher.", ""], wall_facing="south")
    b.set(c - 1, 1, c + 3, B("expanse:potted_edelweiss"))
    b.set(c + 1, 1, c + 3, B("white_candle", candles=2, lit=False))
    b.chest(c + 2, 0, c - 1, "east", HIDDEN)
    b.set(c + 2, 1, c - 1, pick(rng, STONE))
    for x in (c - 1, c, c + 1):                       # a bench facing the view (south)
        b.set(x, 0, 9, pick(rng, STONE))
    b.set(c - 1, 1, 9, stairs("stone_brick", "east"))
    b.set(c, 1, 9, slab("stone_brick", "top"))
    b.set(c + 1, 1, 9, stairs("stone_brick", "west"))
    b.barrel(c + 2, 1, 9, "up", LOOT)
    ground_item(b, c + 2, 2, 9, book(BOOKS[f"{NAME}_memorial"]), rotation=0)
    flora(b, [(x, z) for x in range(11) for z in range(11) if math.hypot(x - c, z - c) > 2.5], rng, 0.2, GROUND)
    snow_cap(b, rng, 0.3, layers=(1,))
    return b


def pools():
    return {"start": [Piece(prayer(), 3, "small_peaks"), Piece(shelter(), 2, "small_peaks"),
                      Piece(memorial(), 2, "small_peaks")]}


DATA = data_for(NAME, ["small_peaks"])
