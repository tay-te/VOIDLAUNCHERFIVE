"""Cavern river landing - the old ferry stage of an underground river, on a cavern hall floor (UNDERGROUND).

Halls are often crossed by an underground river, but nothing here can know whether this one is.
So the landing is built to read either way: a stone quay and a timber stage standing where the
river runs (or ran - a dry bed with the boats hauled out on it reads just as well).

start  landing
  A cobbled-deepslate quay with bollards and steps down to the waterline, a timber stage on
  piles running out from it, the boat shed with the old ferry - a flat plank barge with a
  capstan and rails - hauled up on its slip, and the ferryman's hut: bed, stove, ferry log on
  the lectern, the bell he rang for passengers. The ferry rope still runs from its post. His
  takings are in a chest sunk under the shed floor.
yards -> riverside (rigid, on the floor): cargo stacks and a crane / a second, smaller stage
  with a boat tied up / the ruined toll gate
"""
import math
import random

from blocks import AIR, B
from builder import Build, prune_unsupported
from furnish import chair, rug, shelf_items, table
from loot import book, empty, item as li, pool, table as loot_table
from pieces import Empty, Piece
from processors import STONE_WEATHER, aged_wood, plist, rule
from smallkit import meal_fire
from townkit import (crane, door, goods_stack, hanging_lamp, house, journal, lamp, lichen, lore_entry, rect_ring,
                     rock_mound, slab, stairs, trapdoor, wall_torch, win)

NAME = "cavern_landing"
STRUCTURE = dict(biomes=["#minecraft:is_overworld"], cavern=True, set="cavern_halls", weight=2, size=2,
                 max_distance=40)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
P_YARDS = f"expanse:{NAME}/riverside"
J_YARD = "expanse:landing_yard"
PROC = NAME
W = "spruce"
CD = "cobbled_deepslate"

BOOK = {
    "title": "Ferry Log of the Dark Water", "author": "Ferryman Tobiah",
    "pages": [
        "Crossings today: four miners, two mules, one sack of letters, one goat that was not on the list. "
        "Fare: a coin or a candle. Most pay in candles. The dark has no shortage of buyers.",
        "The river rose a hand overnight and took the lower step. It will fall again. It always falls "
        "again. The ferry rope wants tarring and I want a warmer coat.",
        "If the bell goes unanswered, the ferryman is asleep or the ferryman is gone. Either way the "
        "takings are under the shed boards and the boat is yours. Bring her back.",
    ]}

DATA = {
    "worldgen/processor_list": {
        PROC: plist(STONE_WEATHER, aged_wood("spruce", "oak"),
                    [rule(CD, 0.12, "deepslate_tiles"), rule("cobweb", 0.3, "air"),
                     rule("spruce_planks", 0.05, "oak_planks")]),
    },
    "loot_table/chests": {
        NAME: loot_table(
            NAME,
            pool((3, 6), li("candle", 8, (1, 4)), li("cod", 6, (1, 3)), li("salmon", 5, (1, 3)),
                 li("glow_berries", 6, (1, 4)), li("string", 6, (1, 4)), li("lead", 4), li("paper", 5, (1, 4)),
                 li("gold_nugget", 6, (2, 8)), li("iron_nugget", 6, (2, 8)), li("lantern", 3),
                 li("fishing_rod", 2, damage=(0.3, 0.9))),
            pool(1, empty(5), li("emerald", 3, (1, 3)), li("glow_ink_sac", 2, (1, 3)), book(1))),
        f"{NAME}_hidden": loot_table(
            f"{NAME}_hidden",
            pool((3, 6), li("emerald", 10, (3, 8)), li("gold_ingot", 6, (1, 4)), li("gold_nugget", 8, (4, 12)),
                 li("candle", 5, (2, 6)), li("compass", 3), li("lapis_lazuli", 4, (2, 6)), li("diamond", 2),
                 li("nautilus_shell", 2)),
            pool((1, 2), empty(2), book(4), li("golden_apple", 2), li("heart_of_the_sea", 1),
                 li("music_disc_13", 1)),
            pool(1, empty(1), lore_entry(BOOK, 2))),
    },
}


def floor(rng, x, z):
    r = rng.random()
    return B("gravel") if r < 0.35 else B("tuff") if r < 0.6 else B("andesite") if r < 0.75 else \
        B("cobbled_deepslate") if r < 0.9 else B("clay")


# ------------------------------------------------------------------ the landing
def landing(seed):
    b = Build(f"{NAME}_landing", 37, 18, 33, seed=seed)
    rng = b.rng
    for x in range(37):
        for z in range(33):
            edge = min(x, z, 36 - x, 32 - z)
            if edge == 0 and rng.random() < 0.5:
                continue
            b.set(x, 0, z, floor(rng, x, z))
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
    quay(b, rng)
    stage(b, rng)
    boat_shed(b, rng)
    hut(b, rng)
    # the ferry rope post and the bell
    for y in range(1, 6):
        b.set(30, y, 20, B("stripped_spruce_log", axis="y"))
    b.set(30, 6, 20, B("spruce_log", axis="y"))
    for x in range(31, 37):
        b.set(x, 4, 20, B("iron_chain", axis="x"))
    for y in (1, 2, 3):
        b.set(8, y, 22, B(f"{W}_fence"))
    b.set(8, 4, 22, B("stripped_spruce_log", axis="y"))
    b.set(9, 4, 22, B("stripped_spruce_log", axis="x"))
    b.bell(9, 3, 22, attachment="ceiling", facing="east")
    for (x, z) in ((4, 10), (14, 20), (22, 28), (34, 10)):
        lamp(b, x, 1, z, W, 2)
    for (cx, cz, r) in ((3, 29, 2.2), (34, 3, 2.0)):
        rock_mound(b, rng, cx, cz, r, r, 3)
    lichen(b, rng, 0.1)
    b.jigsaw(0, 1, 16, "west", target=J_YARD, pool=P_YARDS)
    b.jigsaw(18, 1, 32, "south", target=J_YARD, pool=P_YARDS)
    b.jigsaw(18, 1, 0, "north", target=J_YARD, pool=P_YARDS)
    for (x, z) in ((0, 16), (18, 32), (18, 0)):
        b.set(x, 0, z, B(CD))
    return b


def quay(b, rng):
    """The quay: a raised deepslate apron along x 16..22, its east face stepping down to the waterline."""
    for x in range(16, 23):
        for z in range(2, 31):
            b.set(x, 1, z, B(CD) if rng.random() < 0.7 else B("deepslate_tiles"))
            for y in (2, 3, 4):
                b.set(x, y, z, AIR)
    for z in range(2, 31):
        b.set(22, 1, z, stairs("cobbled_deepslate", "west"))
        b.set(23, 0, z, B(rng.choice([CD, "gravel", "deepslate_tiles"])))
    for z in range(2, 31, 4):
        b.set(21, 2, z, B("deepslate_brick_wall"))              # bollards
    for (z0, z1) in ((8, 10), (24, 26)):                        # steps up on the land side
        for z in range(z0, z1 + 1):
            b.set(15, 1, z, stairs("cobbled_deepslate", "east"))


def stage(b, rng):
    """The timber stage out over the water (or the dry bed): plank deck on log piles, posts, a lamp."""
    for x in range(23, 37):
        for z in range(13, 18):
            edge = z in (13, 17)
            b.set(x, 1, z, B("spruce_planks") if rng.random() < 0.9 or edge else slab(W, "top"))
            for y in (2, 3):
                b.set(x, y, z, AIR)
            if x % 4 == 1 and edge:
                b.set(x, 0, z, B("stripped_spruce_log", axis="y"))
                b.set(x, 2, z, B(f"{W}_fence"))
    b.set(36, 2, 13, B(f"{W}_fence"))
    b.set(36, 3, 13, B("lantern"))
    b.set(25, 2, 16, B("barrel", facing="up"))
    b.set(26, 2, 16, B("barrel", facing="east"))
    b.boat(33, 1, 19, wood="spruce", yaw=90.0)
    b.set(33, 1, 18, B(f"{W}_fence"))


def boat_shed(b, rng):
    x0, x1, z0, z1 = 24, 32, 21, 31
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, B("spruce_planks") if z0 < z < z1 and x0 < x < x1 else B(CD))
    for (x, z) in rect_ring(x0, z0, x1, z1):
        if z == z0 and x0 < x < x1:
            continue                                            # open to the water (north)
        post = x in (x0, x1) or (x - x0) % 4 == 0
        for y in range(1, 6):
            b.set(x, y, z, B("stripped_spruce_log", axis="y") if post else B("spruce_planks"))
    b.air(x0 + 1, 1, z0, x1 - 1, 5, z1 - 1)
    from kit import roof_gable
    roof_gable(b, x0, x1, z0, z1, 5, W, lambda xx, yy, zz: B("spruce_planks"), along="z", overhang=1,
               gable_overhang=1)
    for x in range(x0, x1 + 1):
        b.set(x, 5, z0, B("stripped_spruce_log", axis="x"))
    # the slip: two log rails, the ferry on them - a flat plank barge with low sides and a capstan
    for z in range(z0, z1):
        for x in (27, 29):
            b.set(x, 1, z, B("spruce_log", axis="z"))
    for x in range(26, 31):
        for z in range(z0 + 1, z1 - 1):
            edge = x in (26, 30) or z in (z0 + 1, z1 - 2)
            b.set(x, 2, z, B("dark_oak_planks"))
            if edge:
                b.set(x, 3, z, B("dark_oak_fence") if z not in (z0 + 1, z1 - 2) else slab("dark_oak", "bottom"))
            else:
                b.set(x, 3, z, AIR)
    for x in range(27, 30):                                     # ramps at either end of the barge
        b.set(x, 3, z0 + 1, stairs("dark_oak", "south"))
        b.set(x, 3, z1 - 2, stairs("dark_oak", "north"))
    b.set(28, 3, 26, B("dark_oak_fence"))
    b.set(28, 4, 26, slab("dark_oak", "bottom"))
    b.set(27, 3, 28, B("barrel", facing="up"))
    b.set(29, 3, 24, B("lantern"))
    # tools, rope, the takings under the boards
    b.set(x1 - 1, 1, z1 - 1, B("crafting_table"))
    b.barrel(x0 + 1, 1, z1 - 1, "up")
    b.item_frame(x1 - 1, 3, 28, "west", "lead")
    b.item_frame(x1 - 1, 3, 26, "west", "oak_boat")
    b.chest(x0 + 1, 0, 28, "east", HIDDEN)                     # sunk in the floor under a hatch
    b.set(x0 + 1, 1, 28, trapdoor(W, "east", "bottom", False))
    hanging_lamp(b, 28, 5, 27)


def hut(b, rng):
    x0, x1, z0, z1 = 3, 11, 3, 9
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            b.set(x, 0, z, B(CD))
    floors, top, ridge = house(b, rng, x0, z0, x1, z1, 1, post=B("stripped_spruce_log", axis="y"),
                               fill="spruce_planks", floor="spruce_planks", roof="dark_oak", along="x", h=4,
                               base=CD, bay=4, gable_fill="spruce_planks")
    door(b, 7, 2, z1, W, "south")
    b.set(7, 1, z1 + 1, stairs("cobbled_deepslate", "north"))
    wall_torch(b, 6, 4, z1 + 1, "south")
    win(b, x1, 3, 6, "east", 1, shutters=W)
    win(b, 5, 3, z1, "south", 1)
    b.bed(4, 2, 5, "north", "blue")
    b.lectern(10, 2, 4, "west", journal(BOOK))
    b.set(4, 2, 8, B("furnace", facing="east"))
    b.chest(10, 2, 8, "west", LOOT)
    b.barrel(10, 2, 7, "west")
    table(b, 7, 2, 5, W, top=slab(W, "top"))
    chair(b, 7, 2, 6, W, "south")
    b.set(7, 3, 5, B("candle", candles=3, lit=True))
    b.shelf(x0 + 1, 4, 7, W, "east", shelf_items(rng, "fisher"))
    b.item_frame(8, 4, z0 + 1, "south", "fishing_rod")
    hanging_lamp(b, 7, top, 6)


# ------------------------------------------------------------------ riverside
def yard(name, sx, sy, sz, seed):
    b = Build(name, sx, sy, sz, seed=seed)
    rng = b.rng
    for x in range(sx):
        for z in range(sz):
            if rng.random() < 0.8:
                b.set(x, 0, z, floor(rng, x, z))
            for y in (1, 2):
                b.set(x, y, z, AIR)
    b.jigsaw(sx // 2, 1, 0, "north", name=J_YARD)
    return b, rng


def cargo_yard():
    b, rng = yard(f"{NAME}_cargo", 15, 10, 13, 1849731)
    for (x, z) in ((2, 3), (2, 6), (6, 9), (10, 9), (11, 4)):
        goods_stack(b, x, 1, z, rng, ("barrel", "barrel", "log", "wool"), wood=W, n=4)
    crane(b, 7, 1, 5, "east", W, h=7, reach=3)
    b.chest(13, 1, 11, "west", LOOT)
    lamp(b, 1, 1, 11, W, 2)
    b.set(5, 1, 2, B("hay_block", axis="y"))
    b.set(8, 1, 11, B("barrel", facing="up"))
    return b


def small_stage():
    b, rng = yard(f"{NAME}_stage", 9, 8, 17, 1849733)
    for z in range(3, 17):
        for x in range(2, 7):
            b.set(x, 1, z, B("spruce_planks") if rng.random() < 0.88 or x in (2, 6) else AIR)
            if z % 4 == 1 and x in (2, 6):
                b.set(x, 0, z, B("stripped_spruce_log", axis="y"))
                b.set(x, 2, z, B(f"{W}_fence"))
    b.set(6, 2, 15, B(f"{W}_fence"))
    b.set(6, 3, 15, B("lantern"))
    b.boat(7, 1, 12, wood="oak", yaw=0.0)
    b.set(3, 2, 14, B("barrel", facing="up"))
    b.item_frame(4, 2, 15, "up", "cod")
    return b


def toll_gate():
    b, rng = yard(f"{NAME}_toll", 13, 9, 9, 1849735)
    for x in (2, 10):
        for y in range(1, 6):
            b.set(x, y, 4, B(CD) if y < 5 else B("deepslate_tiles"))
        b.set(x, 6, 4, slab("deepslate_tile"))
    for x in range(3, 10):
        b.set(x, 3, 4, B(f"{W}_fence") if x < 7 else AIR)       # the bar, broken off
    b.set(7, 1, 5, B("stripped_spruce_log", axis="x"))
    b.set(8, 1, 5, B("stripped_spruce_log", axis="x"))
    # the keeper's booth
    for (x, z) in rect_ring(10, 5, 12, 7):
        for y in (1, 2):
            b.set(x, y, z, B("spruce_planks") if (x, z) != (11, 5) else AIR)
    b.set(11, 1, 6, B("barrel", facing="up"))
    for x in (10, 11, 12):
        for z in (5, 6, 7):
            b.set(x, 3, z, slab(W, "bottom"))
    b.set(10, 3, 4, B("lantern"))
    rock_mound(b, rng, 3, 7, 1.6, 1.4, 2)
    lichen(b, rng, 0.1)
    return b


def pools():
    return {
        "start": [Piece(landing(1849701), 1, PROC)],
        "riverside": [Piece(cargo_yard(), 3, PROC), Piece(small_stage(), 2, PROC), Piece(toll_gate(), 2, PROC),
                      Empty(1)],
    }
