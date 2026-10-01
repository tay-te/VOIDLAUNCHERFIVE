"""Wisteria manor - a country house of the wisteria vale, a small cousin of the woodland mansion (LANDMARK).

start  manor and forecourt (two variants: kept / gone to seed)
  The manor: a stone ground floor under a timber-framed wisteria-wood upper storey and a dark
  oak roof, a pillared porch. Ground floor: the hall with its stair, the dining room, the
  library (fireplace, lectern with the family's book). Upstairs: the master bedroom, the
  landing, the guest room. A kitchen wing on the east; a rug in the kitchen covers the hatch
  to the wine cellar. A gravel drive loops round a fountain between hedges and wisteria trees
  inside a low garden wall.
paths -> grounds (rigid satellites, one pool per side so nothing repeats)
  behind the house: the lake and boathouse; west: the walled kitchen garden or the orangery;
  east: the stables and coach house; past the gate: the garden folly
cellar (down from the kitchen): the wine cellar, the family silver
"""
import math
import random

import nbt

from arch import blob, leaves, straight_stair, tree
from blocks import AIR, B
from builder import Build
from furnish import chain_lamp, chair, long_table, rug, scarecrow, shelf_items, table, beehive_patch
from loot import book, empty, item as li, pool, table as loot_table
from pieces import Empty, Piece
from processors import OVERGROWTH, STONE_WEATHER, aged_wood, plist, rule
from small_data import VALE
from smallkit import cart
from terrain_paths import connector, path_piece, piece_in
from townkit import (door, goods_stack, hanging_lamp, house, journal, lamp, lore_entry, rect_ring, slab, stairs,
                     stripped, trapdoor, vec, wall_torch, walls, win)

NAME = "wisteria_manor"
STRUCTURE = dict(biomes=["wisteria_vale"], spacing=46, separation=16, size=4, max_distance=72)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
SUPPLIES = f"expanse:chests/{NAME}_supplies"
P_PATHS_REAR = f"expanse:{NAME}/paths_rear"
P_PATHS_WEST = f"expanse:{NAME}/paths_west"
P_PATHS_EAST = f"expanse:{NAME}/paths_east"
P_PATHS_FRONT = f"expanse:{NAME}/paths_front"
P_REAR = f"expanse:{NAME}/rear"
P_WEST = f"expanse:{NAME}/west"
P_EAST = f"expanse:{NAME}/east"
P_FRONT = f"expanse:{NAME}/front"
P_CELLAR = f"expanse:{NAME}/cellar"
J_CELLAR = "expanse:manor_cellar"
PROC = NAME
PROC_SEED = f"{NAME}_overgrown"
WI = "expanse:wisteria"
DO = "dark_oak"
DOL = B("dark_oak_log", axis="y")
SDO = "stripped_dark_oak_log"

BOOK = {
    "title": "The Vale House Book", "author": "Lady Amaryllis Vane",
    "pages": [
        "My grandfather planted the wisteria the year the house was roofed. It has since eaten one porch, "
        "two arbours and a gardener's hat. We let it. It is the house, now, as much as the stone is.",
        "Receipts: the orangery glass from the coast, the fountain stone from the karst, the boat from a "
        "man who swore it did not leak. It leaks.",
        "Should the house ever be shut up, the silver and my mother's jewels are below the kitchen with "
        "the wine, where cooks go and thieves never think to.",
    ]}

DATA = {
    "worldgen/processor_list": {
        PROC: plist(STONE_WEATHER, aged_wood("wisteria", "dark_oak"), VALE, [rule("moss_carpet", 0.3, "air")]),
        PROC_SEED: plist(STONE_WEATHER, aged_wood("wisteria", "dark_oak"), VALE, OVERGROWTH,
                         [rule("dark_oak_leaves", 0.25, "air"), rule("glass_pane", 0.12, "air"),
                          rule("gravel", 0.2, "coarse_dirt")]),
    },
    "loot_table/chests": {
        NAME: loot_table(
            NAME,
            pool((3, 6), li("book", 8, (1, 3)), li("paper", 8, (2, 6)), li("candle", 6, (1, 3)),
                 li("bread", 8, (1, 3)), li("apple", 6, (1, 3)), li("honey_bottle", 5, (1, 2)),
                 li("cookie", 5, (2, 6)), li("expanse:wisteria_sapling", 5, (1, 2)),
                 li("expanse:wisteria_blossoms", 5, (1, 4)), li("gold_nugget", 5, (2, 6)), li("white_wool", 4, (1, 3)),
                 li("clock", 1)),
            pool(1, empty(5), li("emerald", 3, (1, 3)), book(2), li("name_tag", 1))),
        f"{NAME}_hidden": loot_table(
            f"{NAME}_hidden",
            pool((3, 6), li("emerald", 10, (3, 8)), li("gold_ingot", 8, (2, 6)), li("gold_nugget", 6, (4, 10)),
                 li("diamond", 3, (1, 2)), li("amethyst_shard", 4, (2, 5)), li("golden_carrot", 4, (2, 5)),
                 li("golden_horse_armor", 2), li("clock", 2)),
            pool((1, 2), empty(2), book(5), li("golden_apple", 3), li("enchanted_golden_apple", 1),
                 li("music_disc_mall", 1)),
            pool(1, empty(1), lore_entry(BOOK, 2))),
        f"{NAME}_supplies": loot_table(
            f"{NAME}_supplies",
            pool((2, 5), li("bread", 8, (1, 4)), li("apple", 8, (1, 4)), li("sweet_berries", 6, (2, 6)),
                 li("honey_bottle", 4, (1, 2)), li("pumpkin_pie", 4, (1, 2)), li("sugar", 5, (1, 4)),
                 li("egg", 5, (1, 4)), li("milk_bucket", 2), li("wheat", 5, (2, 6)))),
    },
}

VARIANTS = {"kept": dict(proc=PROC, lit=True), "overgrown": dict(proc=PROC_SEED, lit=False)}


def stone(rng):
    return B(rng.choice(["stone_bricks", "stone_bricks", "stone_bricks", "cobblestone"]))


# ------------------------------------------------------------------ manor and forecourt
def manor(variant, seed):
    V = VARIANTS[variant]
    b = Build(f"{NAME}_house_{variant}", 44, 24, 44, seed=seed)
    rng = b.rng
    for x in range(44):
        for z in range(44):
            edge = min(x, z, 43 - x, 43 - z)
            if edge == 0 and rng.random() < 0.5:
                continue
            b.set(x, 0, z, B("grass_block"))
            b.set(x, 1, z, AIR)
            b.set(x, 2, z, AIR)
    main_house(b, rng, V)
    kitchen_wing(b, rng, V)
    forecourt(b, rng, V)
    connector(b, 22, 1, 43, "south", P_PATHS_FRONT)       # the drive: a folly on the lawn beyond the gate
    connector(b, 0, 1, 30, "west", P_PATHS_WEST)         # west: the kitchen garden or the orangery
    connector(b, 43, 1, 30, "east", P_PATHS_EAST)        # east (by the kitchen wing): the stables
    connector(b, 22, 1, 0, "north", P_PATHS_REAR)        # behind the house: the lake (or the kitchen garden)
    for (x, z) in ((22, 43), (0, 30), (43, 30), (22, 0)):
        b.set(x, 0, z, B("gravel"))
    for z in range(0, 5):                                   # a garden path round the back
        b.set(22, 0, z, B(rng.choice(["gravel", "dirt_path"])))
    for x in list(range(0, 10)) + list(range(42, 44)):
        b.set(x, 0, 30, B(rng.choice(["gravel", "dirt_path", "gravel"])))
    return b


def main_house(b, rng, V):
    x0, x1, z0, z1 = 10, 33, 5, 15
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            b.set(x, 0, z, stone(rng))
    floors, top, ridge = house(b, rng, x0, z0, x1, z1, 1, post=DOL, fill=lambda: stone(rng), floor=f"{DO}_planks",
                               roof=DO, along="x", h=5, upper=f"{WI}_planks", upper_h=4, bay=4,
                               beam=SDO, gable_fill=f"{WI}_planks")
    gy, uy = floors
    # stone quoins on the ground storey instead of the timber posts
    for (x, z) in rect_ring(x0, z0, x1, z1):
        for y in range(gy + 1, gy + 6):
            if b.get(x, y, z) == DOL:
                b.set(x, y, z, B("chiseled_stone_bricks") if y == gy + 5 else B("stone_bricks"))
    # partitions: dining | hall | library, both storeys
    for px in (18, 25):
        for z in range(z0 + 1, z1):
            for y in range(gy + 1, gy + 6):
                b.set(px, y, z, B(f"{DO}_planks"))
            for y in range(uy + 1, uy + 5):
                b.set(px, y, z, B(f"{WI}_planks"))
        door(b, px, gy + 1, 11, DO, "east" if px == 25 else "west")
        door(b, px, uy + 1, 12, DO, "east" if px == 25 else "west")
    # front doors and the porch
    for x in (21, 22):
        b.set(x, gy + 1, z1, AIR)
        b.set(x, gy + 2, z1, AIR)
    b.door(21, gy + 1, z1, DO, "south", hinge="left")
    b.door(22, gy + 1, z1, DO, "south", hinge="right")
    for x in range(19, 25):
        for z in range(z1 + 1, z1 + 4):
            b.set(x, gy, z, B("polished_andesite") if 20 <= x <= 23 else B("stone_bricks"))
        b.set(x, gy - 1, z1 + 4, B("stone_bricks"))
        b.set(x, gy, z1 + 4, stairs("stone_brick", "north"))
    for x in (19, 24):
        for y in range(gy + 1, gy + 5):
            b.set(x, y, z1 + 3, B("stone_brick_wall"))
        b.set(x, gy + 5, z1 + 3, B("chiseled_stone_bricks"))
    for x in range(18, 26):
        for z in range(z1 + 1, z1 + 5):
            k = min(z - z1, 4)
            b.set(x, gy + 6, z, stairs(DO, "west" if x < 22 else "east") if x in (18, 25) else slab(DO, "top"))
        b.set(x, gy + 7, z1 + 1, stairs(DO, "south"))
    for x in (20, 23):
        wall_torch(b, x, gy + 3, z1 + 1, "south")
    # windows: tall below, shuttered above
    for x in (12, 15, 28, 31):
        win(b, x, gy + 2, z1, "south", 2)
        win(b, x, gy + 2, z0, "north", 2)
        win(b, x, uy + 2, z1, "south", 2, shutters=DO)
        win(b, x, uy + 2, z0, "north", 2)
    for x in (20, 23):
        win(b, x, uy + 2, z1, "south", 2, box="potted_allium", box_wood=DO)
    for z in (8, 12):
        win(b, x0, gy + 2, z, "west", 2)
        win(b, x0, uy + 2, z, "west", 2)
    win(b, x1, uy + 2, 10, "east", 2)
    # hall: the stair, a grandfather of a chest, banners, a chandelier
    straight_stair(b, 19, gy + 1, 6, "east", 5, DO, clear=3)
    for x in range(19, 24):
        b.set(x, uy + 1, 7, B(f"{DO}_fence"))
    rug(b, 20, 9, 23, 13, gy + 1, "purple", "magenta" if V["lit"] else "gray")
    b.banner(24, gy + 4, 6, "purple", [("flower", "magenta"), ("border", "black")], wall_facing="south")
    b.set(24, gy + 1, 13, B("potted_flowering_azalea_bush"))
    b.set(19, gy + 1, 13, B("potted_flowering_azalea_bush"))
    chain_lamp(b, 22, uy, 11, 2)
    # dining room
    long_table(b, [(x, 10) for x in range(12, 18)], gy + 1, DO, cloth="white_carpet")
    for x in range(13, 17):
        chair(b, x, gy + 1, 9, DO, "north")
        chair(b, x, gy + 1, 11, DO, "south")
    b.set(14, gy + 2, 10, B("candle", candles=3, lit=V["lit"]))
    b.set(16, gy + 2, 10, B("flower_pot"))
    for z in (6, 7, 13, 14):
        b.set(11, gy + 1, z, B("barrel", facing="east"))
    b.chest(11, gy + 1, 10, "east", LOOT)
    b.painting(14, gy + 3, 6, "south", "bouquet")
    chain_lamp(b, 15, uy, 10, 2)
    # library: shelves, the house book, the fireplace, reading chairs
    for x in range(26, 33):
        for y in (gy + 1, gy + 2, gy + 3):
            if x not in (29, 30):
                b.set(x, y, 6, B("bookshelf"))
    for z in range(7, 14):
        for y in (gy + 1, gy + 2):
            if z not in (10,):
                b.set(32, y, z, B("bookshelf"))
    b.bookshelf(32, gy + 3, 9, "west", slots=(0, 1, 3))
    for x in (29, 30):                                    # fireplace in the north wall, chimney behind
        b.set(x, gy + 1, z0, AIR)
        b.set(x, gy + 2, z0, AIR)
        b.campfire(x, gy + 1, z0 - 1, lit=V["lit"], facing="south")
        b.set(x, gy + 2, z0 - 1, AIR)
        b.set(x, gy + 3, 6, stairs("stone_brick", "south", "top"))
        b.set(x, gy, z0 - 1, B("stone_bricks"))
        for y in range(gy + 3, ridge + 3):
            b.set(x, y, z0 - 1, stone(rng))
        b.set(x, gy + 1, 6, AIR)
        b.set(x, gy + 2, 6, AIR)
    for x in (28, 31):
        for y in range(gy + 1, gy + 3):
            b.set(x, y, z0 - 1, stone(rng))
    b.campfire(29, ridge + 3, z0 - 1, lit=V["lit"])
    b.set(30, ridge + 3, z0 - 1, slab("stone_brick"))
    rug(b, 27, 8, 31, 12, gy + 1, "red", "brown")
    chair(b, 28, gy + 1, 9, DO, "north")
    chair(b, 31, gy + 1, 9, DO, "north")
    table(b, 29, gy + 1, 12, DO, top=slab(DO, "top"))
    b.lectern(27, gy + 1, 13, "east", journal(BOOK))
    b.set(29, gy + 2, 12, B("candle", candles=2, lit=V["lit"]))
    b.painting(26, gy + 3, 14, "north", "meditative")
    chain_lamp(b, 29, uy, 10, 2)
    # upstairs: master bedroom (west), landing, guest room (east)
    b.bed(11, uy + 1, 9, "west", "purple")
    b.bed(11, uy + 1, 10, "west", "purple")
    for z in (6, 7):
        b.set(11, uy + 1, z, B("barrel", facing="east"))
    b.set(11, uy + 2, 6, B("barrel", facing="east"))
    b.set(17, uy + 1, 6, B("chest", facing="south"))
    rug(b, 13, 8, 16, 11, uy + 1, "magenta", "purple")
    b.set(17, uy + 1, 13, B("potted_allium"))
    b.painting(14, uy + 2, 14, "north", "sunflowers")
    b.set(11, uy + 1, 13, slab(DO, "top"))
    b.set(11, uy + 2, 13, B("candle", candles=1, lit=False))
    hanging_lamp(b, 14, top, 10)
    for x in (27, 31):
        b.bed(x, uy + 1, 7, "north", "light_gray")
    b.set(29, uy + 1, 6, B("chest", facing="south"))
    b.set(32, uy + 1, 13, B("bookshelf"))
    b.set(32, uy + 2, 13, B("potted_white_tulip"))
    b.set(26, uy + 1, 13, B("crafting_table"))
    hanging_lamp(b, 29, top, 10)
    hanging_lamp(b, 22, top, 11)


def kitchen_wing(b, rng, V):
    x0, x1, z0, z1 = 33, 41, 7, 14
    for x in range(x0, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            b.set(x, 0, z, stone(rng))
    floors, top, ridge = house(b, rng, x0, z0, x1, z1, 1, post=DOL, fill=f"{WI}_planks", floor="stone_bricks",
                               roof=DO, along="z", h=4, base="cobblestone", bay=4, gable_fill=f"{WI}_planks")
    # it shares the manor's east wall: open a door through
    b.set(33, 2, 10, AIR)
    b.set(33, 3, 10, AIR)
    b.door(33, 2, 10, DO, "west")
    for z in range(z0, z1 + 1):
        for y in range(2, 6):
            if b.get(33, y, z) is not None and b.get(33, y, z).short not in ("dark_oak_door", "air"):
                b.set(33, y, z, stone(rng))
    door(b, 37, 2, z1, DO, "south")
    b.set(37, 1, z1 + 1, stairs("cobblestone", "north"))
    win(b, x1, 3, 9, "east", 1, shutters=DO)
    win(b, x1, 3, 12, "east", 1)
    win(b, 39, 3, z1, "south", 1)
    # range and chimney, larder, the cook's table
    b.set(40, 2, 8, B("smoker", facing="west"))
    b.set(39, 2, 8, B("furnace", facing="south"))
    for y in range(2, ridge + 2):
        b.set(40, y, z0 - 1 if y > 1 else z0, stone(rng))
    b.campfire(40, ridge + 2, z0 - 1, lit=V["lit"])
    b.set(38, 2, 8, B("water_cauldron", level=3))
    b.barrel(40, 2, 12, "west", SUPPLIES)
    b.barrel(40, 2, 13, "west")
    b.set(40, 3, 13, B("barrel", facing="up"))
    b.chest(34, 2, 13, "east", LOOT)
    table(b, 36, 2, 10, "spruce", top=slab("spruce", "top"))
    b.set(36, 3, 10, B("cake"))
    b.shelf(34, 4, 8, "spruce", "east", shelf_items(rng, "kitchen"))
    rug(b, 37, 11, 38, 12, 2, "brown", None)
    b.set(38, 1, 12, trapdoor("spruce", "south", "top", False))
    b.jigsaw(38, 0, 12, "down", target=J_CELLAR, pool=P_CELLAR,
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    hanging_lamp(b, 37, top, 10)


def forecourt(b, rng, V):
    # gravel drive from the gate to the porch, looping round the fountain
    for z in range(20, 43):
        for x in (21, 22, 23):
            b.set(x, 0, z, B(rng.choice(["gravel", "gravel", "gravel", "andesite"])))
    for x in range(19, 26):
        b.set(x, 0, 19, B("gravel"))
    cx, cz = 22, 29
    for x in range(cx - 7, cx + 8):
        for z in range(cz - 7, cz + 8):
            d = math.hypot(x - cx, z - cz)
            if 4.5 <= d <= 6.6:
                b.set(x, 0, z, B(rng.choice(["gravel", "gravel", "andesite"])))
    # the fountain: a stone basin with a column and a lantern
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if d <= 2.6:
                b.set(x, 0, z, B("water", level=0))
                b.set(x, 1, z, B("water", level=0))
            elif d <= 3.6:
                b.set(x, 0, z, B("stone_bricks"))
                b.set(x, 1, z, B("stone_bricks"))
                b.set(x, 2, z, slab("stone_brick"))
            elif d <= 4.4:
                b.set(x, 0, z, B("grass_block"))
    b.set(cx, 0, cz, B("stone_bricks"))
    b.set(cx, 1, cz, B("chiseled_stone_bricks"))
    b.set(cx, 2, cz, B("stone_brick_wall"))
    b.set(cx, 3, cz, B("stone_brick_wall"))
    b.set(cx, 4, cz, B("lantern") if V["lit"] else B("stone_brick_wall"))
    b.set(cx + 1, 2, cz + 1, B("lily_pad"))
    # hedges either side of the drive and round the lawn
    hedge = leaves("dark_oak_leaves")
    for z in range(21, 41):
        for x in (17, 27):
            if not (24 <= z <= 34):
                b.set(x, 1, z, hedge)
    for x in list(range(4, 17)) + list(range(28, 40)):
        b.set(x, 1, 20, hedge)
    # wisteria trees flanking the lawn, flowers in beds
    for (tx, tz, lean) in ((8, 26, "east"), (36, 26, "west"), (8, 37, None), (36, 37, None)):
        tree(b, tx, 1, tz, rng, f"{WI}_log", f"{WI}_leaves", height=5, crown=2.6, lean=lean, flat=0.6,
             blossoms="expanse:wisteria_blossoms")
    for x in range(3, 41):
        for z in range(21, 41):
            if b.get(x, 1, z) is None and b.get(x, 0, z) is not None and b.get(x, 0, z).short == "grass_block" \
                    and rng.random() < 0.12:
                b.set(x, 1, z, B(rng.choice(["short_grass", "short_grass", "allium", "azure_bluet"])))
    for x in range(13, 17):
        for z in (22, 23):
            b.set(x, 1, z, B(rng.choice(["allium", "pink_tulip", "white_tulip", "azure_bluet"])))
    for x in range(28, 32):
        for z in (22, 23):
            b.set(x, 1, z, B(rng.choice(["allium", "pink_tulip", "white_tulip", "lily_of_the_valley"])))
    # garden wall along the front with gate piers
    for x in range(2, 42):
        if 20 <= x <= 24:
            continue
        b.set(x, 1, 41, B("stone_brick_wall") if x % 6 else B("stone_bricks"))
        if x % 6 == 0:
            b.set(x, 2, 41, slab("stone_brick"))
    for x in (20, 24):
        for y in (1, 2, 3):
            b.set(x, y, 41, B("stone_bricks"))
        b.set(x, 4, 41, B("lantern") if V["lit"] else slab("stone_brick"))
    for (x, z) in ((19, 21), (25, 21), (19, 37), (25, 37)):
        lamp(b, x, 1, z, DO, 2, torch=not V["lit"])
    cart(b, 30, 17, "x", DO, rng, y=1)


# ------------------------------------------------------------------ grounds
def grounds_site(name, sx, sy, sz, seed, mat="grass_block"):
    b = Build(name, sx, sy, sz, seed=seed)
    rng = b.rng
    piece_in(b, sx // 2, 1, 0, "north")
    for x in range(sx):
        for z in range(1, sz):
            if rng.random() < 0.9:
                b.set(x, 0, z, B(mat))
    for z in range(0, 3):
        b.set(sx // 2, 0, z, B("gravel"))
    return b, rng


def kitchen_garden():
    b, rng = grounds_site(f"{NAME}_kitchen_garden", 21, 9, 22, 1849331)
    x0, x1, z0, z1 = 1, 19, 2, 20
    for (x, z) in rect_ring(x0, z0, x1, z1):
        for y in (1, 2):
            b.set(x, y, z, B(rng.choice(["bricks", "bricks", "stone_bricks"])))
        b.set(x, 3, z, slab("brick") if (x + z) % 2 else slab("stone_brick"))
    for y in (1, 2, 3):
        b.set(10, y, z0, AIR)
    b.set(10, 1, z0, B("spruce_fence_gate", facing="north", open=False, in_wall=False))
    # four beds of crops round a dipping well, a path between
    crops = ["wheat", "carrots", "potatoes", "beetroots"]
    k = 0
    for (bx, bz) in ((3, 4), (12, 4), (3, 12), (12, 12)):
        crop = crops[k]
        k += 1
        for x in range(bx, bx + 6):
            for z in range(bz, bz + 6):
                if (x - bx) == 2 and (z - bz) == 2:
                    b.set(x, 0, z, B("water", level=0))
                    continue
                b.set(x, 0, z, B("farmland", moisture=7))
                age = {"beetroots": rng.randint(1, 3)}.get(crop, rng.randint(3, 7))
                b.set(x, 1, z, B(crop, age=age))
    for x in range(2, 19):
        b.set(x, 0, 10, B("gravel"))
        b.set(x, 0, 11, B("gravel"))
    for z in range(3, 20):
        b.set(10, 0, z, B("gravel"))
        b.set(9, 0, z, B("dirt_path"))
    # the gardener's lean-to against the back wall: composter, tools, chest
    for x in range(14, 19):
        b.set(x, 4, 19, stairs("spruce", "south"))
        b.set(x, 3, 18, stairs("spruce", "south"))
    for x in (14, 18):
        for y in (1, 2):
            b.set(x, y, 18, B("spruce_fence"))
    b.set(15, 1, 19, B("composter", level=5))
    b.chest(16, 1, 19, "north", LOOT)
    b.barrel(17, 1, 19, "north", SUPPLIES)
    b.item_frame(16, 2, 19, "north", "iron_hoe")
    for (x, z) in ((2, 18), (4, 18)):
        beehive_patch(b, x, 1, z, "east", rng, flowers=("allium", "azure_bluet", "cornflower"))
    scarecrow(b, 15, 1, 7, 180.0, rng)
    return b


def orangery():
    b, rng = grounds_site(f"{NAME}_orangery", 21, 11, 15, 1849333, mat="grass_block")
    x0, x1, z0, z1 = 2, 18, 3, 12
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, B("stone_bricks") if (x, z) in set(rect_ring(x0, z0, x1, z1)) else
                  B(rng.choice(["polished_andesite", "smooth_stone", "polished_andesite"])))
    # a stone sill, then tall glass between dark oak mullions, a glass roof on dark oak ribs
    for (x, z) in rect_ring(x0, z0, x1, z1):
        b.set(x, 1, z, B("stone_bricks"))
        mull = (x - x0) % 3 == 0 if z in (z0, z1) else (z - z0) % 3 == 0
        corner = x in (x0, x1) and z in (z0, z1)
        for y in range(2, 6):
            b.set(x, y, z, DOL if (mull or corner) else B("glass_pane"))
    b.air(x0 + 1, 1, z0 + 1, x1 - 1, 7, z1 - 1)
    k = 0
    while z0 + k <= z1 - k:
        for x in range(x0, x1 + 1):
            for z in (z0 + k, z1 - k):
                rib = (x - x0) % 3 == 0
                b.set(x, 6 + k, z, B(f"{DO}_planks") if rib else B("glass"))
        k += 1
    b.air(x0 + 1, 6, z0 + 1, x1 - 1, 6, z1 - 1, only_void=True)
    for y in (2, 3):
        b.set(10, y, z0, AIR)
    b.door(10, 2, z0, DO, "north")
    # citrus trees in planters, potted flowers, a little fountain basin
    for (x, z) in ((5, 6), (5, 10), (15, 6), (15, 10)):
        b.set(x, 1, z, B("dirt"))
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            b.set(x + dx, 1, z + dz, stairs("stone_brick", {(1, 0): "west", (-1, 0): "east", (0, 1): "north",
                                                            (0, -1): "south"}[(dx, dz)]))
        b.set(x, 2, z, B("oak_log", axis="y"))
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1), (0, 0)):
            b.set(x + dx, 3, z + dz, leaves("flowering_azalea_leaves") if (dx + dz) % 2 else leaves("azalea_leaves"))
        b.set(x, 4, z, leaves("azalea_leaves"))
    for x in range(8, 13):
        for z in (7, 8, 9):
            edge = x in (8, 12) or z in (7, 9)
            b.set(x, 1, z, slab("stone_brick") if edge else B("water", level=0))
    for x in (x0 + 1, x1 - 1):
        for z in range(z0 + 1, z1, 2):
            b.set(x, 1, z, B(rng.choice(["potted_flowering_azalea_bush", "potted_azalea_bush", "potted_red_tulip",
                                         "potted_allium", "potted_orange_tulip"])))
    b.set(x1 - 1, 1, z1 - 1, B("barrel", facing="up"))
    b.chest(x0 + 1, 1, z1 - 1, "east", LOOT)
    b.set(10, 1, z1 - 1, stairs(DO, "south"))
    b.set(9, 1, z1 - 1, stairs(DO, "south"))
    b.set(11, 1, z1 - 1, stairs(DO, "south"))
    hanging_lamp(b, 10, 9, 8)
    return b


def lake_boathouse():
    b, rng = grounds_site(f"{NAME}_lake", 27, 12, 30, 1849335)
    cells = [(x, z) for (x, z) in blob(13, 18, 10.0, 9.5, rng, rough=0.15) if 1 <= x <= 25 and 6 <= z <= 28]
    cs = set(cells)
    for (x, z) in cells:
        b.set(x, 0, z, B("water", level=0))
        b.set(x, 1, z, AIR)
    for (x, z) in cells:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, z + dz)
            if p not in cs and 0 <= p[0] < 27 and 1 <= p[1] < 30:
                b.set(p[0], 0, p[1], B(rng.choice(["grass_block", "mud", "clay", "grass_block"])))
                if rng.random() < 0.15:
                    b.set(p[0], 1, p[1], B("sugar_cane", age=0))
                    b.set(p[0], 0, p[1], B("grass_block"))
    for (x, z) in ((6, 14), (17, 23), (10, 25), (20, 12), (8, 20)):
        if (x, z) in cs:
            b.set(x, 1, z, B("lily_pad"))
    # boathouse straddling the near shore: an open water end, a slip for the boat inside
    x0, x1, z0, z1 = 9, 17, 3, 12
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if z <= 6:
                b.set(x, 0, z, stone(rng))
    for x in range(x0 + 2, x1 - 1):
        for z in range(4, z1 + 1):
            b.set(x, 0, z, B("water", level=0))
            b.set(x, 1, z, AIR)
    for (x, z) in rect_ring(x0, z0, x1, z1):
        if z == z1 and x0 < x < x1:
            continue
        for y in range(1, 5):
            post = (x in (x0, x1) and (z - z0) % 3 == 0) or (z in (z0,) and x in (x0, x1))
            b.set(x, y, z, DOL if post else B(f"{WI}_planks"))
    for z in range(z0 + 1, z1 + 1):                          # walkways either side of the slip
        for x in (x0 + 1, x1 - 1):
            b.set(x, 1, z, B(f"{DO}_planks"))
            for y in (2, 3, 4):
                b.set(x, y, z, AIR)
    for x in range(x0 + 2, x1 - 1):
        for z in range(z0 + 1, z1 + 1):
            for y in (2, 3, 4):
                b.set(x, y, z, AIR)
    from kit import roof_gable
    roof_gable(b, x0, x1, z0, z1, 4, DO, lambda xx, yy, zz: B(f"{WI}_planks"), along="z", overhang=1,
               gable_overhang=1)
    for x in range(x0, x1 + 1):                              # the open lake end: a beam over the water gate
        b.set(x, 4, z1, B(SDO, axis="x"))
    b.set(x0, 1, z1, DOL)
    b.set(x1, 1, z1, DOL)
    for y in (2, 3):
        b.set(13, y, z0, AIR)
    b.door(13, 2, z0, DO, "north")
    b.set(13, 1, z0, B(f"{DO}_planks"))
    b.boat(13, 1, 8, wood="dark_oak", yaw=0.0)
    b.set(x0 + 1, 2, 4, B("barrel", facing="up"))
    b.chest(x1 - 1, 2, 4, "west", LOOT)
    b.item_frame(x0 + 1, 3, 9, "east", "fishing_rod")
    hanging_lamp(b, 13, 4, 7)
    # a little jetty out from the east bank
    for x in range(19, 24):
        for z in (15, 16):
            b.set(x, 1, z, slab(DO, "top"))
        if x % 2 and (x, 14) in cs:
            b.set(x, 0, 14, B(SDO, axis="y"))
    b.set(23, 2, 15, B(f"{DO}_fence"))
    b.set(23, 3, 15, B("lantern"))
    return b


def stables():
    b, rng = grounds_site(f"{NAME}_stables", 21, 13, 17, 1849337, mat="grass_block")
    x0, x1, z0, z1 = 2, 18, 3, 11
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "stone_bricks", "gravel"])))
    floors, top, ridge = house(b, rng, x0, z0, x1, z1, 1, post=DOL, fill=f"{WI}_planks", floor="spruce_planks",
                               roof=DO, along="x", h=4, base="stone_bricks", bay=4, gable_fill=f"{WI}_planks")
    # coach arch at the west end, stalls at the east, a hayloft over all
    for z in (6, 7, 8):
        for y in (2, 3, 4):
            b.set(x0, y, z, AIR)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            b.set(x, 1, z, B(rng.choice(["coarse_dirt", "dirt", "spruce_planks"])) if x > 9 else B("cobblestone"))
    cart(b, 4, 6, "x", DO, rng, y=2)
    b.set(4, 4, 6, B("white_wool"))
    for z in (5, 9):
        for x in range(10, 18):
            b.set(x, 2, z, B(f"{DO}_fence"))
    for (x, v) in ((11, 0), (15, 514)):
        b.set(x, 2, z1 - 1, B("hay_block", axis="y"))
        b.mob(x + 1, 2, z1 - 2, "horse", yaw=180.0, extra={"Variant": nbt.Int(v)})
    b.set(17, 2, 10, B("water_cauldron", level=3))
    b.chest(17, 2, z0 + 1, "west", LOOT)
    b.item_frame(16, 4, z0 + 1, "south", "saddle")
    b.item_frame(14, 4, z0 + 1, "south", "lead")
    for x in (11, 14):
        b.set(x, 2, z0 + 1, B("hay_block", axis="x"))
    b.set(12, 2, z0 + 1, B("barrel", facing="up"))
    for y in (2, 3, 4):
        b.set(10, y, 7, AIR)
    for y in (2, 3):
        b.set(13, y, z1, AIR)
    b.door(13, 2, z1, DO, "south")
    for x in (6, 15):
        win(b, x, 3, z0, "north", 1)
    hanging_lamp(b, 6, top, 7)
    hanging_lamp(b, 14, top, 7)
    # paddock
    for x in range(2, 19):
        b.set(x, 1, 15, B(f"{DO}_fence"))
    for z in range(12, 16):
        b.set(2, 1, z, B(f"{DO}_fence"))
        b.set(18, 1, z, B(f"{DO}_fence"))
    b.set(10, 1, 15, B(f"{DO}_fence_gate", facing="south", open=False, in_wall=False))
    return b


def folly():
    b, rng = grounds_site(f"{NAME}_folly", 15, 12, 16, 1849339)
    cx, cz = 7, 9
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if d <= 3.6:
                b.set(x, 1, z, B("stone_bricks") if d > 2.6 else B("polished_andesite"))
                b.set(x, 0, z, B("stone_bricks"))
            elif d <= 4.4:
                b.set(x, 1, z, stairs("stone_brick", "north" if abs(z - cz) > abs(x - cx) and z > cz else
                                      "south" if abs(z - cz) > abs(x - cx) else "west" if x > cx else "east"))
    for (x, z) in ((cx - 2, cz - 2), (cx + 2, cz - 2), (cx - 2, cz + 2), (cx + 2, cz + 2)):
        for y in range(2, 6):
            b.set(x, y, z, B("stone_brick_wall"))
    from smallkit import hip_cap
    hip_cap(b, cx - 3, cz - 3, cx + 3, cz + 3, 6, DO)
    b.set(cx, 8, cz, B(f"{DO}_planks"))
    for x in range(cx - 2, cx + 3):                         # a boarded ceiling under the roof
        for z in range(cz - 2, cz + 3):
            if b.get(x, 5, z) is None:
                b.set(x, 5, z, slab(DO, "top"))
    hanging_lamp(b, cx, 4, cz)
    for x in range(cx - 1, cx + 2):
        b.set(x, 2, cz + 2, stairs(DO, "south"))
    b.set(cx, 2, cz - 1, slab("stone_brick", "top"))
    b.item_frame(cx, 3, cz - 1, "up", "book")
    b.set(cx - 1, 2, cz - 1, B("potted_allium"))
    tree(b, 12, 1, 4, rng, f"{WI}_log", f"{WI}_leaves", height=5, crown=2.4, lean="west", flat=0.6,
         blossoms="expanse:wisteria_blossoms")
    b.barrel(cx + 1, 2, cz - 1, "west")
    return b


# ------------------------------------------------------------------ wine cellar
def cellar():
    b = Build(f"{NAME}_cellar", 11, 6, 11, seed=1849341)
    rng = b.rng
    for x in range(11):
        for z in range(11):
            for y in range(6):
                b.set(x, y, z, B(rng.choice(["dirt", "stone", "stone", "andesite"])))
    b.air(1, 1, 2, 9, 3, 9)
    for x in range(1, 10):
        for z in range(2, 10):
            b.set(x, 0, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"])))
    for (x, z) in rect_ring(1, 2, 9, 9):
        b.set(x, 4, z, B("stone_bricks"))
    b.air(5, 1, 1, 5, 5, 1)
    for y in range(1, 6):
        b.set(5, y, 1, B("ladder", facing="south"))
    b.jigsaw(5, 5, 1, "up", name=J_CELLAR, final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    # wine racks of barrels, a tasting table, the family silver in the far corner behind a grille
    for z in range(3, 10):
        for y in (1, 2):
            b.set(1, y, z, B("barrel", facing="east"))
            if z < 7:
                b.set(9, y, z, B("barrel", facing="west"))
    b.barrel(1, 1, 6, "east", SUPPLIES)
    for x in (7, 8):
        b.set(x, 1, 7, B("iron_bars"))
    b.chest(9, 1, 9, "west", HIDDEN)
    b.set(8, 1, 9, B("barrel", facing="up"))
    b.set(7, 1, 9, B("iron_bars"))
    b.set(7, 1, 8, B("iron_bars"))
    table(b, 4, 1, 6, "spruce", top=slab("spruce", "top"))
    b.set(4, 2, 6, B("candle", candles=3, lit=True))
    chair(b, 4, 1, 7, "spruce", "south")
    b.set(3, 3, 9, B("cobweb"))
    hanging_lamp(b, 5, 3, 5)
    return b


def pools():
    rp = random.Random(1849351)
    mats = (("gravel", 4), ("dirt_path", 2), ("coarse_dirt", 1))
    return {
        "start": [Piece(manor("kept", 1849301), 2, PROC), Piece(manor("overgrown", 1849303), 1, PROC_SEED)],
        "paths_rear": [Piece(path_piece(f"{NAME}_path_rear", 9, P_REAR, rp, mats=mats), 1, PROC,
                             "terrain_matching")],
        "paths_west": [Piece(path_piece(f"{NAME}_path_west", 9, P_WEST, rp, mats=mats), 1, PROC,
                             "terrain_matching")],
        "paths_east": [Piece(path_piece(f"{NAME}_path_east", 13, P_EAST, rp, mats=mats), 1, PROC,
                             "terrain_matching")],
        "paths_front": [Piece(path_piece(f"{NAME}_path_front", 7, P_FRONT, rp, mats=mats), 2, PROC,
                              "terrain_matching"), Empty(1)],
        "rear": [Piece(lake_boathouse(), 1, PROC)],
        "west": [Piece(kitchen_garden(), 1, PROC), Piece(orangery(), 1, PROC)],
        "east": [Piece(stables(), 1, PROC)],
        "front": [Piece(folly(), 1, PROC)],
        "cellar": [Piece(cellar(), 1, PROC)],
    }
