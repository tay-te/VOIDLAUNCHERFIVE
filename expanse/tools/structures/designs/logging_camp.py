"""Redwood logging camp - a sawmill in the redwood giants (LANDMARK).

start  mill yard (two variants: working / laid-up for the winter)
  The sawmill shed: an open-sided timber frame over the saw bench (a stonecutter blade and a
  log on the carriage), stacks of boards along its back wall. A waterwheel on its east side
  turns in the mill race, which runs into the millpond. A log flume on trestles carries logs
  across the yard and drops them into the pond, where cut logs float in a boom. A log deck,
  board stacks drying in rows, a gin-pole crane, a cart, and the foreman's office - whose
  floor hatch opens into the payroll strongroom.
paths -> camp (rigid satellites)
  bunkhouse / cookhouse / ox barn / charcoal kilns / felling site with a half-sawn giant /
  tool shed and smithy
strongroom (down from the office): the payroll chest
"""
import math
import random

from arch import blob, rock
from blocks import AIR, B
from builder import Build
from furnish import chair, long_table, rug, shelf_items, table, woodpile
from loot import book, empty, item as li, pool, table as loot_table
from pieces import Empty, Piece
from processors import FOREST, OVERGROWTH, STONE_WEATHER, aged_wood, plist, rule
from smallkit import cart, meal_fire
from terrain_paths import connector, path_piece, piece_in
from townkit import (crane, door, goods_stack, hanging_lamp, house, journal, lamp, lore_entry, slab, stairs,
                     stripped, trapdoor, vec, wall_torch, walls, wheel, win)

NAME = "logging_camp"
STRUCTURE = dict(biomes=["redwood_giants"], spacing=44, separation=16, size=4, max_distance=72)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
SUPPLIES = f"expanse:chests/{NAME}_supplies"
P_PATHS = f"expanse:{NAME}/paths"
P_CAMP = f"expanse:{NAME}/camp"
P_VAULT = f"expanse:{NAME}/strongroom"
J_VAULT = "expanse:logging_strongroom"
PROC = NAME
R = "expanse:redwood"
SR = stripped(R)
SRX = stripped(R, "x")
SRZ = stripped(R, "z")

BOOK = {
    "title": "Tally of the Giants", "author": "Foreman Halvard",
    "pages": [
        "Season's tally: forty-one logs to the pond, nine to the mill, one lost in the flume when young "
        "Pell rode it down for a bet. Pell is fine. The log is not.",
        "We fell only the giants that lean. A standing redwood is worth more to the forest than to us, and "
        "the forest keeps a longer ledger than I do.",
        "Wages go out on the first frost. Until then the payroll sleeps under my floor, under the bearskin, "
        "under the eye of a foreman who sleeps very lightly.",
    ]}

DATA = {
    "worldgen/processor_list": {
        PROC: plist(aged_wood("redwood", "spruce"), STONE_WEATHER, FOREST, OVERGROWTH,
                    [rule("coarse_dirt", 0.15, "podzol"), rule("expanse:redwood_planks", 0.04, "spruce_planks")]),
    },
    "loot_table/chests": {
        NAME: loot_table(
            NAME,
            pool((3, 6), li("bread", 10, (1, 3)), li("expanse:cooked_venison", 6, (1, 3)), li("apple", 6, (1, 3)),
                 li("stick", 8, (3, 9)), li("expanse:redwood_log", 8, (2, 6)), li("expanse:redwood_sapling", 5, (1, 3)),
                 li("coal", 6, (2, 6)), li("charcoal", 6, (2, 6)), li("leather", 4, (1, 3)), li("string", 4, (1, 3)),
                 li("iron_nugget", 5, (2, 7)), li("iron_axe", 2, damage=(0.2, 0.7))),
            pool(1, empty(6), li("emerald", 3, (1, 2)), li("lead", 2), book(1))),
        f"{NAME}_hidden": loot_table(
            f"{NAME}_hidden",
            pool((3, 6), li("emerald", 10, (3, 8)), li("gold_ingot", 6, (1, 4)), li("iron_ingot", 8, (2, 6)),
                 li("gold_nugget", 6, (3, 9)), li("iron_axe", 3, enchant=True), li("compass", 2),
                 li("expanse:cooked_venison", 4, (2, 4))),
            pool((1, 2), empty(2), book(3), li("diamond", 2, (1, 2)), li("golden_apple", 2), li("diamond_axe", 1),
                 li("name_tag", 2)),
            pool(1, empty(1), lore_entry(BOOK, 2))),
        f"{NAME}_supplies": loot_table(
            f"{NAME}_supplies",
            pool((2, 5), li("bread", 8, (1, 4)), li("potato", 8, (2, 6)), li("baked_potato", 5, (1, 4)),
                 li("beef", 5, (1, 3)), li("expanse:venison", 5, (1, 3)), li("wheat", 5, (2, 6)),
                 li("sweet_berries", 5, (2, 6)), li("coal", 4, (1, 4)), li("stick", 4, (2, 8)))),
    },
}

VARIANTS = {
    "working": dict(roof="dark_oak", lit=True, snow=False),
    "laid_up": dict(roof="spruce", lit=False, snow=False),
}


def ground(rng, x, z):
    v = math.sin(x * 0.29 + 0.7) + math.sin(z * 0.23) + rng.random() * 0.9
    if v < -0.4:
        return B("podzol")
    if v < 0.7:
        return B("coarse_dirt")
    if v < 1.3:
        return B("dirt_path")
    if v < 1.7:
        return B("gravel")
    return B("rooted_dirt")


# ------------------------------------------------------------------ the mill yard
def yard(variant, seed):
    V = VARIANTS[variant]
    b = Build(f"{NAME}_yard_{variant}", 44, 22, 44, seed=seed)
    rng = b.rng
    for x in range(44):
        for z in range(44):
            edge = min(x, z, 43 - x, 43 - z)
            if edge == 0 and rng.random() < 0.5 or edge == 1 and rng.random() < 0.2:
                continue
            b.set(x, 0, z, ground(rng, x, z))
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
    pond_cells = millpond(b, rng)
    sawmill(b, rng, V)
    race_and_wheel(b, rng)
    flume(b, rng)
    log_deck(b, rng)
    board_stacks(b, rng)
    office(b, rng, V)
    # gin-pole crane at the pond, a log cart, lamps, a stump or two left in the yard
    crane(b, 25, 1, 21, "south", R, h=7, reach=3, load=SRX)
    cart(b, 14, 18, "x", "spruce", rng, y=1)
    for x in (14, 15, 16):
        b.set(x, 3, 18, B(f"{R}_log", axis="x"))
    for (x, z) in ((23, 16), (12, 27), (24, 41), (40, 14)):
        lamp(b, x, 1, z, "spruce", 2, torch=not V["lit"])
    for (x, z) in ((41, 4), (36, 9)):
        b.set(x, 0, z, B("podzol"))
        b.set(x, 1, z, B(f"{R}_wood", axis="y"))
        b.set(x, 2, z, slab(R, "bottom"))
    # connectors to the camp
    connector(b, 33, 1, 0, "north", P_PATHS)
    connector(b, 0, 1, 16, "west", P_PATHS)
    connector(b, 14, 1, 43, "south", P_PATHS)
    connector(b, 43, 1, 10, "east", P_PATHS)
    for (x, z) in ((33, 0), (0, 16), (14, 43), (43, 10)):
        b.set(x, 0, z, B("dirt_path"))
    return b


def millpond(b, rng):
    cells = [(x, z) for (x, z) in blob(34, 32, 7.5, 9.0, rng, rough=0.18) if 1 <= x <= 42 and 1 <= z <= 42]
    cs = set(cells)
    for (x, z) in cells:
        b.set(x, 0, z, B("water", level=0))
        b.set(x, 1, z, AIR)
    for (x, z) in cells:                                    # a muddy, reedy bank
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, z + dz)
            if p not in cs and 0 <= p[0] < 44 and 0 <= p[1] < 44:
                b.set(p[0], 0, p[1], B(rng.choice(["mud", "coarse_dirt", "clay", "mud", "gravel"])))
                if rng.random() < 0.18:
                    b.set(p[0], 1, p[1], B("short_grass"))
                    b.set(p[0], 0, p[1], B("grass_block"))
    # the log boom: cut logs floating in the pond, a boom of logs chained across one bay
    for (x, z, ax) in ((31, 28, "x"), (32, 29, "x"), (35, 34, "z"), (36, 35, "z"), (33, 37, "x"), (37, 30, "z")):
        if (x, z) in cs:
            b.set(x, 0, z, B(f"{R}_log", axis=ax))
    for z in range(36, 42):
        if (29, z) in cs:
            b.set(29, 0, z, B(f"{R}_log", axis="z"))
    for (x, z) in ((36, 26), (38, 36), (31, 38)):
        if (x, z) in cs:
            b.set(x, 1, z, B("lily_pad"))
    return cs


def sawmill(b, rng, V):
    x0, x1, z0, z1 = 3, 22, 3, 13
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, B(rng.choice(["spruce_planks", f"{R}_planks", "spruce_planks"])))
    # timber frame: posts every 4, a half wall of boards at the back (north), open elsewhere
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            post = (x - x0) % 4 == 0 or x == x1
            for y in range(1, 6):
                if post:
                    b.set(x, y, z, SR)
                elif z == z0 and y <= 3:
                    b.set(x, y, z, B(f"{R}_planks"))
            if not post:
                b.set(x, 5, z, SRX)
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            for y in range(1, 5):
                b.set(x, y, z, B(f"{R}_planks") if (x == x0 or y <= 2) else AIR)
            b.set(x, 5, z, SRZ)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            for y in range(1, 6):
                b.set(x, y, z, AIR)
    from kit import roof_gable
    roof_gable(b, x0, x1, z0, z1, 5, V["roof"], lambda xx, yy, zz: B(f"{R}_planks"), along="x", overhang=1,
               gable_overhang=1)
    for x in range(x0 + 4, x1, 4):                           # tie beams under the roof
        for z in range(z0 + 1, z1):
            b.set(x, 6, z, SRZ)
    # the saw bench: carriage of top slabs, a log on it and the stonecutter blade in the middle
    for x in range(x0 + 2, x1 - 1):
        b.set(x, 1, 9, slab("spruce", "top"))
        b.set(x, 1, 8, B("rail", shape="east_west") if x % 3 else slab("spruce", "top"))
    b.set(12, 1, 9, B("stonecutter", facing="north"))
    for x in range(5, 11):
        b.set(x, 2, 9, B(f"{R}_log", axis="x"))
    for x in range(13, 17):
        b.set(x, 2, 9, slab(R, "bottom"))
    # boards stacked along the back wall, logs waiting at the west end
    for x in range(x0 + 1, x1 - 1):
        for y in (1, 2):
            if (x + y) % 5 and rng.random() < 0.8:
                b.set(x, y, z0 + 1, B(f"{R}_planks") if y == 1 else slab(R, "bottom"))
    for z in range(5, 12):
        b.set(x0 + 1, 1, z, B(f"{R}_log", axis="z") if z % 3 else B("spruce_log", axis="z"))
    b.set(x0 + 1, 2, 7, B(f"{R}_log", axis="z"))
    # sharpening corner and tools
    b.set(x1 - 1, 1, z1 - 1, B("grindstone", face="floor", facing="west"))
    b.set(x1 - 2, 1, z1 - 1, B("crafting_table"))
    b.barrel(x1 - 1, 1, z1 - 2, "west")
    b.chest(x1 - 3, 1, z1 - 1, "north", LOOT)
    b.item_frame(x0 + 1, 3, z0 + 1, "south", "iron_axe")
    b.item_frame(x0 + 2, 3, z0 + 1, "south", "stone_axe")
    for x in (8, 16):
        hanging_lamp(b, x, 5, 8)


def race_and_wheel(b, rng):
    # the race: a stone-lined channel along the shed's east side into the pond
    for z in range(1, 28):
        for x in (24, 25):
            if b.get(x, 0, z) is not None and b.get(x, 0, z).short == "water":
                continue
            b.set(x, 0, z, B("water", level=0))
            b.set(x, 1, z, AIR)
        for x in (23, 26):
            if b.get(x, 0, z) is None or b.get(x, 0, z).short != "water":
                b.set(x, 0, z, B(rng.choice(["cobblestone", "mossy_cobblestone", "stone"])))
    for x in (23, 24, 25, 26):
        b.set(x, 0, 0, B("cobblestone"))
    for x in (24, 25):                                       # a plank footbridge
        b.set(x, 1, 17, slab("spruce", "bottom"))
        b.set(x, 1, 18, slab("spruce", "bottom"))
    wheel(b, 24, 3, 8, 3, "x", "spruce", rim=B("stripped_spruce_log", axis="x"))
    for x in (22, 23):                                       # the axle into the shed
        b.set(x, 3, 8, B("spruce_log", axis="x"))
    b.set(25, 3, 8, B("spruce_log", axis="x"))
    for (x, z) in ((25, 4), (25, 12)):
        for y in range(1, 4):
            b.set(x, y, z, B("spruce_fence"))
    b.set(25, 3, 5, B("spruce_fence"))
    b.set(25, 3, 11, B("spruce_fence"))
    for z in range(5, 12):
        if z != 8:
            b.set(25, 3, z, B("spruce_fence"))


def flume(b, rng):
    """A water trough on trestles from the west edge to its drop over the pond."""
    z = 27
    ys = 5                                                    # water level in the trough
    x_end = 30
    for x in range(0, x_end + 1):
        b.set(x, ys - 1, z, B("spruce_planks"))
        for zz in (z - 1, z + 1):
            b.set(x, ys - 1, zz, slab("spruce", "top"))
            b.set(x, ys, zz, B("spruce_planks"))
        b.set(x, ys, z, B("water", level=0))
        if x % 4 == 1 and b.get(x, 0, z) is not None and b.get(x, 0, z).short != "water":
            for y in range(1, ys - 1):
                for zz in (z - 1, z + 1):
                    b.set(x, y, zz, B("spruce_fence") if y < ys - 2 else B("spruce_log", axis="y"))
            b.set(x, ys - 2, z, B("spruce_log", axis="z"))
    for zz in (z - 1, z, z + 1):                              # closed at the head
        b.set(0, ys, zz, B("spruce_planks"))
    b.set(x_end, ys, z, B("water", level=0))
    for x in (9, 20):                                         # logs riding down
        b.set(x, ys, z, B(f"{R}_log", axis="x"))
    # a ladder up to the trough for the flume tender
    for y in range(1, ys):
        b.set(13, y, z + 2, B("ladder", facing="south"))
    b.set(13, ys, z + 2, B("spruce_trapdoor", facing="south", half="bottom", open=False))


def log_deck(b, rng):
    for x in range(3, 12):
        for k, z in enumerate(range(17, 23)):
            b.set(x, 1, z, B(f"{R}_log", axis="x"))
            if 0 < k < 5:
                b.set(x, 2, z, B(f"{R}_log", axis="x"))
            if 1 < k < 4 and rng.random() < 0.8:
                b.set(x, 3, z, B(f"{R}_log", axis="x"))
    for z in (16, 23):
        for x in (3, 11):
            b.set(x, 1, z, B("spruce_fence"))
            b.set(x, 2, z, B("spruce_fence"))


def board_stacks(b, rng):
    """Boards drying in stickered stacks: planks, slab spacers, planks."""
    for (x0, z0) in ((15, 31), (15, 36), (20, 31), (20, 36)):
        for x in range(x0, x0 + 4):
            for z in range(z0, z0 + 3):
                b.set(x, 1, z, B(f"{R}_planks"))
                b.set(x, 2, z, slab("spruce", "bottom") if (x + z) % 2 else B(f"{R}_planks"))
                if rng.random() < 0.7:
                    b.set(x, 3, z, B(f"{R}_planks") if rng.random() < 0.6 else B("spruce_planks"))
    goods_stack(b, 19, 1, 41, rng, ("barrel", "log", "barrel"), wood="spruce")


def office(b, rng, V):
    x0, x1, z0, z1 = 3, 10, 32, 39
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "mossy_cobblestone", "stone"])))
    floors, top, ridge = house(b, rng, x0, z0, x1, z1, 1, post=SR, fill=lambda: B(f"{R}_log", axis="x"),
                               floor="spruce_planks", roof=V["roof"], along="z", h=4, bay=0,
                               base="cobblestone", gable_fill=f"{R}_planks")
    for (xx, zz) in ((x0, z) for z in range(z0 + 1, z1)):
        for y in range(3, 6):
            b.set(xx, y, zz, B(f"{R}_log", axis="z"))
    for zz in range(z0 + 1, z1):
        for y in range(3, 6):
            b.set(x1, y, zz, B(f"{R}_log", axis="z"))
    door(b, x1, 2, 35, "spruce", "east")
    b.set(x1 + 1, 1, 35, stairs("cobblestone", "west"))
    wall_torch(b, x1 + 1, 4, 34, "east")
    for z in (34, 37):
        win(b, x0, 3, z, "west", 1, shutters="spruce")
    win(b, x1, 3, 37, "east", 1)
    win(b, 6, 3, z0, "north", 1)
    # the foreman's desk, ledger, map of the cutting, his bunk and the bearskin over the hatch
    b.lectern(4, 2, 33, "east", journal(BOOK))
    table(b, 7, 2, 33, "spruce", top=slab("spruce", "top"))
    chair(b, 7, 2, 34, "spruce", "south")
    b.set(7, 3, 33, B("candle", candles=1, lit=V["lit"]))
    b.item_frame(8, 4, 33, "south", "map")
    b.bed(4, 2, 37, "south", "brown")
    b.chest(9, 2, 38, "west", LOOT)
    b.barrel(9, 2, 37, "west")
    rug(b, 6, 36, 8, 38, 2, "brown", None)
    b.set(7, 1, 37, trapdoor("spruce", "south", "top", False))
    b.jigsaw(7, 0, 37, "down", target=J_VAULT, pool=P_VAULT,
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    b.shelf(4, 4, 38, "spruce", "east", shelf_items(rng, "study"))
    hanging_lamp(b, 6, top, 35)
    # chimney stove
    for y in range(1, top + 4):
        b.set(x0 - 1, y, 36, B(rng.choice(["cobblestone", "mossy_cobblestone"])))
    b.campfire(x0 - 1, top + 4, 36, lit=V["lit"])
    b.set(x0, 2, 36, B("furnace", facing="east", lit=False))


# ------------------------------------------------------------------ camp satellites
def site(name, sx, sy, sz, seed, mats=("coarse_dirt", "podzol", "dirt", "coarse_dirt")):
    b = Build(name, sx, sy, sz, seed=seed)
    rng = b.rng
    piece_in(b, sx // 2, 1, 0, "north")
    for x in range(sx):
        for z in range(1, sz):
            if rng.random() < 0.8:
                b.set(x, 0, z, B(rng.choice(mats)))
    for z in range(0, 4):
        b.set(sx // 2, 0, z, B("dirt_path"))
    return b, rng


def log_cabin(b, rng, x0, z0, x1, z1, roof, h=4, along="x"):
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if b.inb(x, 0, z):
                b.set(x, 0, z, B(rng.choice(["cobblestone", "mossy_cobblestone", "cobblestone", "stone"])))

    def fill():
        return B(f"{R}_log", axis="x")
    floors, top, ridge = house(b, rng, x0, z0, x1, z1, 1, post=SR, fill=fill, floor="spruce_planks", roof=roof,
                               along=along, h=h, bay=0, base="cobblestone", gable_fill=f"{R}_planks")
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            for y in range(3, 2 + h):
                b.set(x, y, z, B(f"{R}_log", axis="z"))
    return floors, top


def bunkhouse():
    b, rng = site(f"{NAME}_bunkhouse", 19, 12, 14, 1849231)
    x0, x1, z0, z1 = 2, 16, 4, 11
    floors, top = log_cabin(b, rng, x0, z0, x1, z1, "dark_oak")
    door(b, 9, 2, z0, "spruce", "north")
    b.set(9, 1, z0 - 1, stairs("cobblestone", "south"))
    wall_torch(b, 8, 4, z0 - 1, "north")
    for x in (5, 13):
        win(b, x, 3, z0, "north", 1, shutters="spruce")
        win(b, x, 3, z1, "south", 1)
    win(b, x0, 3, 7, "west", 1)
    win(b, x1, 3, 8, "east", 1)
    for x in (3, 5, 7, 11, 13, 15):                          # bunks along the back wall
        b.bed(x, 2, z1 - 2, "south", ["red", "brown", "green", "red", "gray", "brown"][x % 6])
    for x in (4, 6, 12, 14):
        b.barrel(x, 2, z1 - 1, "north")
    b.chest(10, 2, z1 - 1, "north", LOOT)
    b.set(3, 2, z0 + 1, B("furnace", facing="south"))
    long_table(b, [(x, 7) for x in range(6, 12)], 2, "spruce")
    for x in range(7, 11):
        chair(b, x, 2, 6, "spruce", "north")
    b.set(8, 3, 7, B("candle", candles=2, lit=False))
    b.item_frame(x1 - 1, 3, z0 + 1, "west", "iron_axe")
    b.set(15, 2, 5, B("crafting_table"))
    for x in (6, 12):
        hanging_lamp(b, x, top, 8)
    woodpile(b, 1, 1, 1, R, "x", length=4, height=2, rng=rng)
    return b


def cookhouse():
    b, rng = site(f"{NAME}_cookhouse", 17, 14, 15, 1849233)
    x0, x1, z0, z1 = 2, 13, 4, 11
    floors, top = log_cabin(b, rng, x0, z0, x1, z1, "spruce")
    door(b, 8, 2, z0, "spruce", "north")
    b.set(8, 1, z0 - 1, stairs("cobblestone", "south"))
    wall_torch(b, 7, 4, z0 - 1, "north")
    for x in (4, 11):
        win(b, x, 3, z0, "north", 1, shutters="spruce")
    win(b, x0, 3, 8, "west", 1)
    # hearth in the east wall with its chimney, smoker and furnace beside it
    for z in (7, 8):
        for y in range(1, top + 4):
            b.set(x1 + 1, y, z, B(rng.choice(["cobblestone", "stone_bricks", "mossy_cobblestone"])))
        b.set(x1, 2, z, AIR)
        b.set(x1, 3, z, AIR)
    meal_fire(b, x1, 2, 7, foods=("beef", "potato"), lit=True, done=(300, 200, 0, 0))
    b.campfire(x1, 2, 8, lit=True)
    b.set(x1, 4, 7, stairs("stone_brick", "west", "top"))
    b.set(x1, 4, 8, stairs("stone_brick", "west", "top"))
    b.campfire(x1 + 1, top + 4, 7, lit=True)
    b.set(x1 - 1, 2, 5, B("smoker", facing="west"))
    b.set(x1 - 1, 2, 10, B("furnace", facing="west"))
    b.set(x1 - 1, 2, 6, B("water_cauldron", level=3))
    b.barrel(x1 - 1, 2, 9, "west", SUPPLIES)
    b.shelf(x1 - 1, 4, 5, "spruce", "west", shelf_items(rng, "kitchen"))
    # long tables for the crew
    for z in (6, 9):
        long_table(b, [(x, z) for x in range(3, 9)], 2, "spruce")
        for x in range(4, 8):
            chair(b, x, 2, z - 1 if z == 6 else z + 1, "spruce", "north" if z == 6 else "south")
    b.set(5, 3, 6, B("candle", candles=3, lit=True))
    b.chest(3, 2, 10, "east", LOOT)
    b.set(3, 2, 5, B("barrel", facing="up"))
    for x in (5, 9):
        hanging_lamp(b, x, top, 8)
    # the dinner bell and a chopping block by the door
    b.set(11, 1, 2, B("spruce_fence"))
    b.set(11, 2, 2, B("spruce_fence"))
    b.set(11, 3, 2, B("spruce_planks"))
    b.bell(11, 4, 2, attachment="floor", facing="north")
    b.set(4, 1, 2, SR)
    b.item_frame(4, 2, 2, "up", "iron_axe")
    woodpile(b, 1, 1, 13, R, "z", length=5, height=2, rng=rng)
    return b


def ox_barn():
    b, rng = site(f"{NAME}_ox_barn", 19, 12, 19, 1849235, mats=("coarse_dirt", "dirt", "podzol", "coarse_dirt"))
    x0, x1, z0, z1 = 2, 12, 5, 15
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, B(rng.choice(["coarse_dirt", "dirt", "spruce_planks"])))
    walls(b, x0, z0, x1, z1, 1, 4, SR, lambda: B(f"{R}_planks"), bay=5)
    b.air(x0 + 1, 1, z0 + 1, x1 - 1, 4, z1 - 1)
    from kit import roof_gable
    roof_gable(b, x0, x1, z0, z1, 4, "spruce", lambda xx, yy, zz: B(f"{R}_planks"), along="z", overhang=1,
               gable_overhang=1)
    for y in (1, 2, 3):                                      # big barn door, open
        for x in (6, 7, 8):
            b.set(x, y, z0, AIR)
    b.set(7, 4, z0, SRX)
    # stalls with hay and water, two oxen (cattle) and a draft horse
    for z in (8, 11):
        for x in range(x0 + 1, x0 + 4):
            b.set(x, 1, z, B("spruce_fence"))
    for (z, mob, yaw) in ((7, "cow", 90.0), (10, "cow", 90.0), (13, "horse", 90.0)):
        b.set(x0 + 1, 1, z - 1, B("hay_block", axis="y"))
        b.mob(x0 + 2, 1, z, mob, yaw=yaw)
    b.set(x0 + 1, 1, 14, B("water_cauldron", level=3))
    for z in range(z0 + 1, z1):
        if z not in (8, 11):
            b.set(x0 + 4, 1, z, B("spruce_fence_gate", facing="east", open=False, in_wall=False)) if z in (7, 10, 13) \
                else b.set(x0 + 4, 1, z, B("spruce_fence"))
    for (x, z) in ((10, 13), (11, 13), (11, 14), (10, 14)):
        b.set(x, 1, z, B("hay_block", axis="y"))
    b.set(11, 2, 14, B("hay_block", axis="x"))
    b.item_frame(x1 - 1, 3, z0 + 1, "west", "saddle")
    b.chest(x1 - 1, 1, z0 + 1, "west", LOOT)
    hanging_lamp(b, 7, 4, 10)
    # paddock fence outside with a log skid
    for z in range(4, 17):
        b.set(16, 1, z, B("spruce_fence"))
    for x in range(13, 17):
        b.set(x, 1, 4, B("spruce_fence"))
        b.set(x, 1, 16, B("spruce_fence"))
    b.set(16, 1, 10, B("spruce_fence_gate", facing="east", open=False, in_wall=False))
    for x in (14, 15):
        b.set(x, 1, 9, B(f"{R}_log", axis="z"))
        b.set(x, 1, 10, B(f"{R}_log", axis="z"))
    b.set(14, 1, 12, B("iron_chain", axis="z"))
    return b


def charcoal_kilns():
    b, rng = site(f"{NAME}_kilns", 19, 9, 15, 1849237, mats=("coarse_dirt", "gravel", "coarse_dirt", "dirt"))
    for (cx, cz) in ((5, 7), (13, 7)):
        for x in range(cx - 3, cx + 4):
            for z in range(cz - 3, cz + 4):
                d = math.hypot(x - cx, z - cz)
                if d <= 3.2:
                    b.set(x, 0, z, B("cobblestone"))
                for y in range(1, 5):
                    r = 3.2 - (y - 1) * 0.7
                    if r - 1.0 < d <= r:
                        b.set(x, y, z, B(rng.choice(["cobblestone", "mossy_cobblestone", "stone", "bricks"])))
                    elif d <= r - 1.0:
                        b.set(x, y, z, AIR)
        b.campfire(cx, 1, cz, lit=True)
        b.set(cx, 4, cz, AIR)                                # the smoke vent
        b.set(cx, 1, cz - 3, AIR)
        b.set(cx, 2, cz - 3, AIR)
        b.set(cx, 1, cz - 3, B("iron_bars"))
    for x in range(2, 17):                                   # cordwood waiting, charcoal sacks
        if x % 5:
            b.set(x, 1, 13, B("spruce_log", axis="z"))
            if x % 2:
                b.set(x, 2, 13, B(f"{R}_log", axis="z"))
    b.set(9, 1, 2, B("coal_block"))
    b.barrel(9, 1, 3, "up", SUPPLIES)
    b.set(10, 1, 3, B("barrel", facing="up"))
    b.set(17, 1, 2, B("composter", level=0))
    return b


def felling_site():
    b, rng = site(f"{NAME}_felling", 21, 10, 22, 1849239, mats=("podzol", "coarse_dirt", "grass_block", "podzol"))
    # two giant stumps (3x3 of wood), one with a springboard notch
    for (cx, cz, h) in ((4, 6, 2), (15, 15, 3)):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for y in range(1, h + 1 - (1 if dx and dz and rng.random() < 0.5 else 0)):
                    b.set(cx + dx, y, cz + dz, B(f"{R}_wood", axis="y"))
        b.set(cx, h, cz, B(f"{R}_log", axis="y"))
    b.set(4, 2, 4, B("spruce_trapdoor", facing="north", half="bottom", open=True))
    # the fallen giant, 2x2 thick, cut through near its foot, the crosscut saw left in the kerf
    for z in range(5, 21):
        if z == 9:
            continue
        for x in (9, 10):
            for y in (1, 2):
                b.set(x, y, z, B(f"{R}_log", axis="z"))
        if rng.random() < 0.3:
            b.set(9, 3, z, B("moss_carpet"))
    b.set(9, 2, 9, B("iron_bars"))
    b.set(10, 2, 9, B("iron_bars"))
    for z in (8, 10):
        b.set(11, 1, z, B("spruce_fence"))
    b.set(11, 2, 9, B("spruce_fence"))
    # wedges, axes, a sledge of cut rounds, and the crew's lunch fire
    b.item_frame(8, 1, 12, "up", "iron_axe")
    b.item_frame(12, 1, 16, "up", "iron_shovel")
    for z in (12, 13, 14):
        b.set(13, 1, z, slab("spruce", "top"))
    b.set(13, 2, 12, B(f"{R}_log", axis="y"))
    b.set(13, 2, 14, B(f"{R}_log", axis="y"))
    meal_fire(b, 4, 1, 16, foods=("expanse:venison", "potato"), lit=False, done=(400, 100, 0, 0))
    for (x, z) in ((3, 18), (5, 18)):
        b.set(x, 1, z, SRX)
    b.chest(2, 1, 14, "east", LOOT)
    b.set(2, 1, 13, B("barrel", facing="up"))
    for (x, z) in ((17, 4), (18, 10), (2, 20)):
        b.set(x, 1, z, B("fern"))
    for (x, z) in ((18, 18), (6, 11)):
        b.set(x, 1, z, B("expanse:redwood_sapling", stage=0))
    return b


def tool_shed():
    b, rng = site(f"{NAME}_smithy", 15, 12, 14, 1849241, mats=("gravel", "coarse_dirt", "cobblestone"))
    x0, x1, z0, z1 = 2, 10, 4, 11
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "stone", "gravel"])))
    # open-fronted forge shed: back and side walls of stone below and boards above
    for (x, z) in [(x, z1) for x in range(x0, x1 + 1)] + [(x, z) for x in (x0, x1) for z in range(z0, z1)]:
        for y in range(1, 5):
            post = (x in (x0, x1) and z in (z0, z1))
            b.set(x, y, z, SR if post else B("cobblestone") if y <= 2 else B(f"{R}_planks"))
    for x in (x0, 6, x1):
        for y in range(1, 5):
            b.set(x, y, z0, SR)
    b.air(x0 + 1, 1, z0, x1 - 1, 4, z1 - 1)
    for x in (x0, 6, x1):
        for y in range(1, 5):
            b.set(x, y, z0, SR)
    from kit import roof_gable
    roof_gable(b, x0, x1, z0, z1, 4, "spruce", lambda xx, yy, zz: B(f"{R}_planks"), along="x", overhang=1,
               gable_overhang=1)
    # forge: furnaces, anvil, grindstone, smithing table, axes on the wall
    b.set(x0 + 1, 1, z1 - 1, B("blast_furnace", facing="north"))
    b.set(x0 + 2, 1, z1 - 1, B("furnace", facing="north"))
    b.set(x0 + 1, 1, z1 - 2, B("water_cauldron", level=2))
    for y in range(1, 9):
        b.set(x0 + 1, y, z1 + 1, B(rng.choice(["cobblestone", "stone_bricks"])))
    b.campfire(x0 + 1, 9, z1 + 1, lit=True)
    b.set(5, 1, 8, B("anvil", facing="east"))
    b.set(x1 - 1, 1, z1 - 1, B("smithing_table"))
    b.set(x1 - 1, 1, z1 - 2, B("grindstone", face="floor", facing="west"))
    b.chest(x1 - 1, 1, z1 - 3, "west", LOOT)
    for (x, it) in ((4, "iron_axe"), (5, "iron_pickaxe"), (7, "iron_shovel"), (8, "shears")):
        b.item_frame(x, 3, z1 - 1, "north", it)
    b.shelf(x1 - 1, 3, z1 - 2, "spruce", "west", ["iron_ingot", "iron_nugget", "iron_chain"])
    hanging_lamp(b, 6, 4, 8)
    b.set(12, 1, 6, B("raw_iron_block"))
    b.set(12, 1, 7, B("coal_block"))
    b.barrel(12, 1, 8, "up")
    return b


# ------------------------------------------------------------------ strongroom
def strongroom():
    b = Build(f"{NAME}_strongroom", 9, 6, 9, seed=1849251)
    rng = b.rng
    for x in range(9):
        for z in range(9):
            for y in range(6):
                b.set(x, y, z, B(rng.choice(["dirt", "stone", "dirt", "andesite", "rooted_dirt"])))
    b.air(1, 1, 2, 7, 3, 7)
    for x in range(1, 8):
        for z in range(2, 8):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "mossy_cobblestone"])))
    for (x, z) in ((1, 2), (7, 2), (1, 7), (7, 7)):
        for y in (1, 2, 3):
            b.set(x, y, z, SR)
    for x in range(1, 8):
        b.set(x, 4, 4, SRX)
    b.air(4, 1, 1, 4, 5, 1)
    for y in range(1, 6):
        b.set(4, y, 1, B("ladder", facing="south"))
    b.jigsaw(4, 5, 1, "up", name=J_VAULT, final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    b.chest(4, 1, 7, "north", HIDDEN)
    b.set(3, 1, 7, B("barrel", facing="up"))
    b.set(5, 1, 7, B("barrel", facing="up"))
    table(b, 2, 1, 4, "spruce", top=slab("spruce", "top"))
    b.item_frame(2, 2, 4, "up", "paper")
    b.set(6, 1, 4, B("iron_bars"))
    b.set(6, 1, 5, B("chest", facing="west"))
    b.set(6, 1, 3, B("barrel", facing="west"))
    hanging_lamp(b, 4, 3, 4)
    b.item_frame(2, 2, 7, "north", "emerald")
    return b


def pools():
    rp = random.Random(1849261)
    mats = (("dirt_path", 5), ("coarse_dirt", 2), ("podzol", 1), ("gravel", 1))
    return {
        "start": [Piece(yard("working", 1849201), 2, PROC), Piece(yard("laid_up", 1849203), 1, PROC)],
        "paths": [Piece(path_piece(f"{NAME}_path_a", 9, P_CAMP, rp, mats=mats), 3, PROC, "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_b", 15, P_CAMP, rp, mats=mats), 2, PROC, "terrain_matching")],
        "camp": [Piece(bunkhouse(), 3, PROC), Piece(cookhouse(), 3, PROC), Piece(ox_barn(), 2, PROC),
                 Piece(charcoal_kilns(), 2, PROC), Piece(felling_site(), 3, PROC), Piece(tool_shed(), 2, PROC)],
        "strongroom": [Piece(strongroom(), 1, PROC)],
    }
