"""Woodland lodge of the redwood giants and wisteria vale.

start pool
  hunters : two-storey redwood log lodge with notched corners, loft (ladder),
            hearth on the north wall, kitchen, bedroom, porch with a lean-to roof
  cutters : long single-storey woodcutter's cabin with a west chimney and bread
            oven, an open workshop lean-to and a front porch
  Both hide a root cellar under the hearth rug (trapdoor -> down jigsaw).
cellar pool : root cellar (hidden chest) / smoke cellar (hidden chest)
paths/yard  : terrain-matching tracks to a vegetable garden with a scarecrow,
              woodshed, beehive meadow, smokehouse, wisteria arbor, well, fallen giant
"""
import random

from blocks import AIR, B
from builder import Build, item, slab, stairs, trapdoor, written_book
from furnish import (beehive_patch, chain_lamp, chair, fallen_log, long_table, rug, scarecrow, shelf_items, table,
                     well, window, woodpile)
from kit import chimney, foundation, gable_boards, log_walls, roof_gable, skirt
from lore import BOOKS
from pieces import Empty, Piece
from terrain_paths import connector, path_piece, piece_in

NAME = "woodland_lodge"
STRUCTURE = dict(biomes=["redwood_giants", "wisteria_vale"], spacing=30, separation=10, size=2, max_distance=48)
LOOT = "expanse:chests/woodland_lodge"
HIDDEN = "expanse:chests/woodland_lodge_hidden"
P_CELLAR = f"expanse:{NAME}/cellar"
P_PATHS = f"expanse:{NAME}/paths"
P_YARD = f"expanse:{NAME}/yard"
W = "expanse:redwood"
SW = "expanse:stripped_redwood"


def book():
    d = BOOKS[NAME]
    return written_book(d["title"], d["author"], d["pages"])


def hatch(b, x, z, wall_side="north"):
    """Trapdoor in the floor (y=1) under a rug cell, with the cellar jigsaw below it (y=0)."""
    b.set(x, 1, z, trapdoor("spruce", "south", "top", False))
    b.set(x, 2, z, B("brown_carpet"))
    b.jigsaw(x, 0, z, "down", name="minecraft:empty", target="expanse:cellar_top", pool=P_CELLAR,
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north")


def lodge_hunters():
    b = Build(f"{NAME}_hunters", 22, 17, 21, seed=1847331)
    rng = b.rng
    x0, x1, z0, z1 = 4, 16, 4, 11
    skirt(b, x0, z0, x1, z1, 0, rng, out=2, gap=0.35)
    foundation(b, x0, z0, x1, z1, 0, 0, rng)
    foundation(b, x0, z0, x1, z1, 1, 1, rng, mats=("cobblestone", "mossy_cobblestone", "cobblestone", "stone"))
    b.fill(x0 + 1, 1, z0 + 1, x1 - 1, 1, z1 - 1, B(f"{W}_planks"))
    b.air(x0 + 1, 2, z0 + 1, x1 - 1, 5, z1 - 1)
    log_walls(b, x0, z0, x1, z1, 2, 5, f"{W}_log", f"{SW}_log")
    # loft floor and knee walls
    b.fill(x0 + 1, 6, z0 + 1, x1 - 1, 6, z1 - 1, B("spruce_planks"))
    log_walls(b, x0, z0, x1, z1, 6, 8, f"{W}_log", f"{SW}_log", notch=False)
    b.air(x0 + 1, 7, z0 + 1, x1 - 1, 8, z1 - 1)
    for x in range(x0 + 1, x1):                      # exposed ceiling beams
        if x % 3 == 0:
            b.set(x, 6, z0 + 1, B(f"{SW}_log", axis="z"))
    roof_gable(b, x0, x1, z0, z1, 8, "dark_oak", gable_boards(f"{SW}_log", f"{W}_planks",
                                                               {(x0, 10, 7), (x0, 10, 8), (x1, 10, 7), (x1, 10, 8)}),
               along="x", overhang=1, gable_overhang=1)
    # porch with a lean-to roof
    for x in range(x0 + 2, x1 - 1):
        for z in (z1 + 1, z1 + 2, z1 + 3):
            b.set(x, 1, z, B("spruce_planks") if z < z1 + 3 else B(f"{SW}_log", axis="x"))
            b.set(x, 0, z, B("cobblestone"))
        b.set(x, 6, z1 + 1, stairs("dark_oak", "north"))
        b.set(x, 5, z1 + 2, stairs("dark_oak", "north"))
        b.set(x, 4, z1 + 3, stairs("dark_oak", "north"))
        for z in (z1 + 1, z1 + 2, z1 + 3):
            b.air(x, 2, z, x, 3 if z == z1 + 3 else 4, z, only_void=True)
    for x in (x0 + 2, (x0 + x1) // 2 - 1, x1 - 2):
        b.column(x, z1 + 3, 2, 3, B(f"{SW}_log", axis="y"))
    for x in range(x0 + 3, x1 - 2):
        if x not in ((x0 + x1) // 2 - 1, 11, 12, 13):
            b.set(x, 2, z1 + 3, B(f"{W}_fence"))
    for x in (11, 12, 13):
        b.set(x, 1, z1 + 4, stairs("spruce", "north"))
        b.set(x, 0, z1 + 4, B("cobblestone"))
    # chimney on the north wall with the hearth inside
    chimney(b, 10, z0 - 2, 0, 14, rng, w=2, d=2, signal=True)
    for x in (10, 11):
        b.set(x, 2, z0, AIR)
        b.set(x, 3, z0, AIR)
        b.set(x, 2, z0 - 1, AIR)
        b.set(x, 4, z0, stairs("stone_brick", "south", "top"))
    b.campfire(10, 2, z0 - 1, facing="south")
    b.campfire(11, 2, z0 - 1, facing="south")
    b.set(10, 3, z0 - 1, AIR)
    b.set(11, 3, z0 - 1, AIR)
    b.set(10, 1, z0 - 1, B("stone_bricks"))
    b.set(11, 1, z0 - 1, B("stone_bricks"))
    for x in (9, 12):
        b.column(x, z0 + 1, 2, 4, B("stone_bricks"))
    b.set(9, 5, z0 + 1, slab("stone_brick"))
    b.set(12, 5, z0 + 1, slab("stone_brick"))
    for x in (10, 11):
        b.set(x, 5, z0 + 1, slab("stone_brick", "top"))
    # doors and windows
    b.door(12, 2, z1, W, "south")
    for (x, z, out) in ((6, z1, "south"), (7, z1, "south"), (14, z1, "south"), (6, z0, "north"), (14, z0, "north")):
        window(b, x, 3, z, out, 2, shutters=W if x in (6, 15, 14) else None,
               box=rng.choice(["potted_red_tulip", "expanse:potted_heather", "potted_azalea_bush"])
               if out == "north" else None)
    window(b, x0, 3, 7, "west", 2, shutters=W)
    window(b, x0, 3, 8, "west", 2)
    # partition: bedroom (west) | hall (east)
    for z in range(z0 + 1, z1):
        for y in range(2, 6):
            b.set(8, y, z, B(f"{W}_planks"))
    b.door(8, 2, 9, W, "east", hinge="right")
    # bedroom
    b.bed(5, 2, z0 + 2, "north", "red")
    b.set(5, 2, z0 + 3, B("spruce_trapdoor", facing="north", half="top", open=False))
    b.set(6, 2, z0 + 1, B("barrel", facing="south"))
    b.set(6, 3, z0 + 1, B("candle", candles=2, lit=True))
    b.chest(7, 2, z0 + 1, "south", LOOT)
    b.set(5, 2, z1 - 1, B("chiseled_bookshelf", facing="east"))
    b.bookshelf(5, 2, z1 - 1, "east", slots=(0, 2, 5))
    b.set(7, 2, z1 - 1, B("expanse:potted_redwood_sapling"))
    rug(b, 6, z0 + 3, 7, z0 + 5, 2, "red", "brown")
    b.painting(5, 4, z0 + 1, "south", "wanderer")
    chain_lamp(b, 6, 6, 7, 1)
    # hall: hearth rug with the cellar hatch, dining table, kitchen along the east wall
    rug(b, 9, z0 + 2, 12, z0 + 3, 2, "brown", None)
    hatch(b, 10, z0 + 3)
    long_table(b, [(10, 8), (11, 8), (12, 8)], 2, "spruce", cloth="white_carpet")
    for x in (10, 11, 12):
        chair(b, x, 2, 9, "spruce", "south")
    chair(b, 13, 2, 8, "spruce", "east")
    b.set(15, 2, z0 + 1, B("smoker", facing="west"))
    b.set(15, 2, z0 + 2, B("water_cauldron", level=3))
    b.barrel(15, 2, z0 + 3, "west")
    b.set(15, 3, z0 + 3, B("expanse:potted_heather"))
    b.shelf(15, 4, z0 + 1, "spruce", "west", shelf_items(rng, "kitchen"))
    b.shelf(15, 4, z0 + 2, "spruce", "west", shelf_items(rng, "kitchen"))
    b.item_frame(14, 4, z0 + 1, "south", "expanse:venison")
    b.item_frame(13, 4, z0 + 1, "south", "rabbit")
    b.lectern(13, 2, z0 + 1, "south", book())
    b.set(9, 2, z1 - 1, B("crafting_table"))
    b.armor_stand(14, 2, z1 - 1, yaw=180.0, equipment={"head": "leather_helmet", "chest": "leather_chestplate",
                                                         "mainhand": "bow"})
    chain_lamp(b, 11, 6, 7, 1)
    chain_lamp(b, 14, 6, 6, 1)
    # ladder to the loft
    for y in range(2, 7):
        b.set(15, y, z1 - 1, B("ladder", facing="north"))
    # loft
    b.bed(6, 7, z0 + 1, "west", "green")
    b.bed(6, 7, z1 - 1, "west", "lime")
    b.barrel(13, 7, z0 + 1, "up")
    b.barrel(14, 7, z0 + 1, "up")
    b.set(12, 7, z0 + 1, B("hay_block", axis="x"))
    b.set(10, 7, z1 - 1, B("chest", facing="north"))
    b.set(9, 7, z0 + 2, B("white_carpet"))
    b.set(9, 7, z0 + 3, B("white_carpet"))
    chain_lamp(b, 10, 12, 7, 2)
    # porch furniture
    chair(b, 6, 2, z1 + 1, "spruce", "north")
    chair(b, 7, 2, z1 + 1, "spruce", "north")
    b.set(14, 2, z1 + 1, B("barrel", facing="up"))
    b.set(14, 3, z1 + 1, B("expanse:potted_redwood_sapling"))
    b.set(9, 4, z1 + 2, B("lantern", hanging=True))
    # paths out (front and both sides) and markers
    for x in range(0, 22):
        for z in range(0, 21):
            if b.get(x, 0, z) is None and rng.random() < 0.12:
                b.set(x, 0, z, B("grass_block"))
                b.set(x, 1, z, B("short_grass"))
    for z in range(z1 + 5, 21):
        b.set(12, 0, z, B(rng.choice(["dirt_path", "dirt_path", "coarse_dirt"])))
    connector(b, 12, 1, 20, "south", P_PATHS)
    connector(b, 21, 1, 8, "east", P_PATHS)
    connector(b, 0, 1, 8, "west", P_PATHS)
    return b


def lodge_cutters():
    b = Build(f"{NAME}_cutters", 23, 14, 19, seed=1847333)
    rng = b.rng
    x0, x1, z0, z1 = 5, 15, 4, 10
    skirt(b, x0, z0, x1, z1, 0, rng, out=2, gap=0.35)
    foundation(b, x0, z0, x1, z1, 0, 1, rng, mats=("cobblestone", "mossy_cobblestone", "stone"))
    b.fill(x0 + 1, 1, z0 + 1, x1 - 1, 1, z1 - 1, B("spruce_planks"))
    b.air(x0 + 1, 2, z0 + 1, x1 - 1, 5, z1 - 1)

    def infill(x, y, z):
        return B(f"{W}_log", axis="x" if z in (z0, z1) else "z") if y % 2 == 0 else \
            B(f"{SW}_log", axis="x" if z in (z0, z1) else "z")
    log_walls(b, x0, z0, x1, z1, 2, 5, f"{W}_log", f"{SW}_log", infill=infill)
    roof_gable(b, x0, x1, z0, z1, 5, "spruce", gable_boards(f"{SW}_log", f"{W}_planks", {(x1, 7, 7)}),
               along="x", overhang=1, gable_overhang=1)
    for x in range(x0 + 1, x1):
        if x % 2 == 1:
            for z in range(z0 + 1, z1):
                b.set(x, 5, z, B(f"{SW}_log", axis="z"))
    # west chimney with a bread oven bulge
    chimney(b, x0 - 2, 6, 0, 11, rng, w=2, d=3)
    b.set(x0 - 3, 1, 7, B("cobblestone"))
    b.set(x0 - 3, 2, 7, B("furnace", facing="west"))
    b.set(x0 - 3, 3, 7, slab("cobblestone"))
    for z in (6, 7, 8):
        b.set(x0, 2, z, AIR)
        b.set(x0, 3, z, AIR)
        b.set(x0 - 1, 2, z, AIR)
        b.set(x0 - 1, 3, z, AIR)
        b.set(x0, 4, z, stairs("stone_brick", "east", "top"))
    b.campfire(x0 - 1, 2, 7, facing="east")
    b.set(x0 - 1, 2, 6, B("stone_bricks"))
    b.set(x0 - 1, 2, 8, B("stone_bricks"))
    # open workshop lean-to on the east side
    for z in range(z0 + 1, z1):
        for x in (x1 + 1, x1 + 2, x1 + 3):
            b.set(x, 0, z, B(rng.choice(["coarse_dirt", "gravel", "dirt_path"])))
            b.air(x, 1, z, x, 3, z)
        b.set(x1 + 1, 5, z, stairs("spruce", "west"))
        b.set(x1 + 2, 4, z, stairs("spruce", "west"))
        b.set(x1 + 3, 4, z, slab("spruce", "bottom"))
    for z in (z0 + 1, z1 - 1):
        b.column(x1 + 3, z, 1, 3, B(f"{SW}_log", axis="y"))
    b.set(x1 + 2, 1, z0 + 2, B(f"{W}_log", axis="y"))
    b.item_frame(x1 + 2, 2, z0 + 2, "up", "iron_axe")
    b.set(x1 + 1, 1, z0 + 2, B("stonecutter", facing="east"))
    b.set(x1 + 1, 1, z1 - 2, B("grindstone", face="floor", facing="north"))
    woodpile(b, x1 + 2, 1, z1 - 1, W, "x", length=2, height=2, rng=rng)
    b.set(x1 + 3, 1, 7, B("crafting_table"))
    # porch on the south
    for x in range(x0 + 1, x1):
        b.set(x, 1, z1 + 1, B("spruce_planks"))
        b.set(x, 0, z1 + 1, B("cobblestone"))
        b.set(x, 4, z1 + 1, stairs("spruce", "north"))
        b.air(x, 2, z1 + 1, x, 3, z1 + 1, only_void=True)
    for x in (x0 + 1, x1 - 1):
        b.column(x, z1 + 1, 2, 3, B(f"{W}_fence"))
    b.set(10, 1, z1 + 2, stairs("spruce", "north"))
    b.set(10, 0, z1 + 2, B("cobblestone"))
    b.door(10, 2, z1, W, "south")
    for (x, z, out) in ((7, z1, "south"), (13, z1, "south"), (8, z0, "north"), (12, z0, "north")):
        window(b, x, 3, z, out, 2, shutters="spruce", box=rng.choice(["potted_red_tulip", "potted_fern"])
               if out == "south" else None)
    window(b, x1, 3, 7, "east", 2)
    # interior
    b.bed(x0 + 1, 2, z0 + 2, "north", "brown")
    b.chest(x0 + 2, 2, z0 + 1, "south", LOOT)
    b.set(x0 + 3, 2, z0 + 1, B("barrel", facing="south"))
    b.set(x0 + 3, 3, z0 + 1, B("lantern"))
    rug(b, x0 + 1, 7, x0 + 3, 8, 2, "green", "brown")
    hatch(b, x0 + 2, 8)
    table(b, 11, 2, 7, "spruce", top="plate")
    chair(b, 11, 2, 8, "spruce", "south")
    chair(b, 12, 2, 7, "spruce", "east")
    b.set(x1 - 1, 2, z0 + 1, B("smoker", facing="west"))
    b.set(x1 - 1, 2, z0 + 2, B("barrel", facing="west"))
    b.set(x1 - 2, 2, z0 + 1, B("crafting_table"))
    b.shelf(x1 - 1, 4, z0 + 1, "spruce", "west", shelf_items(rng, "kitchen"))
    b.lectern(x1 - 1, 2, z1 - 1, "west", book())
    b.set(x1 - 2, 2, z1 - 1, B("expanse:potted_wisteria_sapling"))
    b.painting(x0 + 1, 3, z1 - 1, "east", "kebab")
    chain_lamp(b, 9, 7, 7, 2)
    chain_lamp(b, 13, 6, 6, 1)
    for x in range(0, 23):
        for z in range(0, 19):
            if b.get(x, 0, z) is None and rng.random() < 0.12:
                b.set(x, 0, z, B("grass_block"))
                b.set(x, 1, z, B("short_grass"))
    for z in range(z1 + 3, 19):
        b.set(10, 0, z, B(rng.choice(["dirt_path", "dirt_path", "coarse_dirt"])))
    connector(b, 10, 1, 18, "south", P_PATHS)
    connector(b, 22, 1, 7, "east", P_PATHS)
    connector(b, 0, 1, 12, "west", P_PATHS)
    return b


# ------------------------------------------------------------------ cellars (below the hatch)
def cellar_root():
    b = Build(f"{NAME}_cellar_root", 9, 6, 9, seed=1847335)
    rng = b.rng
    for x in range(9):
        for z in range(9):
            for y in range(0, 5):
                b.set(x, y, z, B(rng.choice(["dirt", "dirt", "coarse_dirt", "stone"])))
    for x in range(1, 8):
        for z in range(2, 8):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "mossy_cobblestone", "stone_bricks"])))
            b.air(x, 1, z, x, 3, z)
    for x in range(1, 8):
        b.set(x, 4, 5, B(f"{SW}_log", axis="x"))
    for y in range(1, 6):                              # ladder shaft from the hatch
        b.set(4, y, 1, B("ladder", facing="south"))
    for y in (4, 5):
        for dx in (-1, 1):
            b.set(4 + dx, y, 1, B("cobblestone"))
        b.set(4, y, 0, B("cobblestone"))
    b.jigsaw(4, 5, 1, "up", name="expanse:cellar_top", target="minecraft:empty", pool="minecraft:empty",
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    # shelves of barrels, crates of produce, hanging meat, the hidden chest behind barrels
    for z in range(2, 8):
        b.barrel(1, 1, z, "east")
        if z % 2:
            b.barrel(1, 2, z, "up")
    for (x, z) in ((7, 3), (7, 4), (7, 6)):
        b.set(x, 1, z, B(rng.choice(["pumpkin", "melon", "hay_block"])) if rng.random() < 0.7 else B("hay_block",
                                                                                                       axis="y"))
    b.chest(7, 1, 7, "west", HIDDEN)                   # tucked behind a barrel, lid free to open
    b.set(6, 1, 7, B("barrel", facing="up"))
    b.set(6, 2, 7, B("barrel", facing="up"))
    chain_lamp(b, 4, 4, 5, 1)
    b.item_frame(4, 2, 7, "north", "expanse:venison")
    b.item_frame(3, 2, 7, "north", "carrot")
    b.set(5, 1, 2, B("composter", level=3))
    b.set(3, 1, 2, B("water_cauldron", level=2))
    return b


def cellar_smoke():
    b = Build(f"{NAME}_cellar_smoke", 9, 6, 8, seed=1847337)
    rng = b.rng
    for x in range(9):
        for z in range(8):
            for y in range(0, 5):
                b.set(x, y, z, B(rng.choice(["dirt", "stone", "stone", "andesite"])))
    for x in range(1, 8):
        for z in range(2, 7):
            b.set(x, 0, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks"])))
            b.air(x, 1, z, x, 3, z)
    for y in range(1, 6):
        b.set(4, y, 1, B("ladder", facing="south"))
    b.jigsaw(4, 5, 1, "up", name="expanse:cellar_top", target="minecraft:empty", pool="minecraft:empty",
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    for x in range(1, 8):
        b.set(x, 3, 4, B("spruce_fence"))
    for x in (2, 4, 6):
        b.item_frame(x, 2, 4, "north", rng.choice(["expanse:venison", "porkchop", "rabbit", "cod"]))
    b.set(1, 1, 6, B("smoker", facing="east"))
    b.set(1, 1, 5, B("barrel", facing="east"))
    b.chest(7, 1, 6, "west", HIDDEN)
    b.set(7, 1, 2, B("barrel", facing="west"))
    b.set(6, 1, 6, B("barrel", facing="up"))
    chain_lamp(b, 4, 4, 3, 0)
    b.set(7, 2, 2, B("lantern"))
    return b


# ------------------------------------------------------------------ yard satellites
def yard_garden():
    b = Build(f"{NAME}_garden", 9, 4, 10, seed=1847341)
    rng = b.rng
    piece_in(b, 4, 1, 0, "north")
    for x in range(1, 8):
        for z in range(1, 9):
            edge = x in (1, 7) or z in (1, 8)
            if edge:
                if not (z == 1 and x == 4):
                    b.set(x, 1, z, B("spruce_fence"))
                b.set(x, 0, z, B("coarse_dirt") if rng.random() < 0.5 else B("dirt"))
            else:
                b.set(x, 0, z, B("farmland", moisture=7))
                crop = rng.choice(["wheat", "carrots", "potatoes", "beetroots"]) if x != 4 else None
                if crop and z != 5:
                    b.set(x, 1, z, B(crop, age=rng.randint(3, 7) if crop != "beetroots" else rng.randint(1, 3)))
    b.set(4, 1, 1, B("spruce_fence_gate", facing="north"))
    b.set(4, 0, 5, B("water", level=0))
    for z in range(2, 8):
        if z != 5:
            b.set(4, 0, z, B("dirt_path"))
            b.set(4, 1, z, AIR)
    scarecrow(b, 2, 0, 7, 135.0)
    b.set(2, 0, 7, B("hay_block", axis="y"))
    b.set(6, 1, 7, B("composter", level=5))
    b.set(8, 1, 8, B("barrel", facing="up"))
    return b


def yard_woodshed():
    b = Build(f"{NAME}_woodshed", 9, 6, 8, seed=1847343)
    rng = b.rng
    piece_in(b, 4, 1, 0, "north")
    for x in range(1, 8):
        for z in range(2, 7):
            b.set(x, 0, z, B(rng.choice(["coarse_dirt", "dirt_path", "gravel"])))
    for x in (1, 7):
        b.column(x, 6, 1, 3, B(f"{SW}_log", axis="y"))
        b.column(x, 3, 1, 2, B(f"{SW}_log", axis="y"))
    for x in range(0, 9):
        b.set(x, 4, 6, stairs("spruce", "north"))
        b.set(x, 3, 3, slab("spruce", "top"))
        b.set(x, 4, 5, slab("spruce", "bottom"))
        b.set(x, 4, 4, slab("spruce", "bottom"))
    b.air(1, 1, 3, 7, 2, 3, only_void=True)
    for x in range(2, 7):
        woodpile(b, x, 1, 5, W, "z", length=1, height=3 if x % 2 else 2, rng=rng)
    b.set(3, 1, 2, B(f"{W}_log", axis="y"))
    b.item_frame(3, 2, 2, "up", "iron_axe")
    b.set(5, 1, 2, B("spruce_slab", type="bottom"))
    b.set(6, 1, 2, B(f"{W}_log", axis="x"))
    return b


def yard_bees():
    b = Build(f"{NAME}_beehives", 9, 4, 10, seed=1847345)
    rng = b.rng
    piece_in(b, 4, 1, 0, "north")
    for (x, z) in ((2, 2), (5, 1), (6, 4)):
        beehive_patch(b, x, 1, z, "south", rng)
    for (x, z) in ((1, 6), (3, 6), (7, 3), (2, 1)):
        b.set(x, 0, z, B("grass_block"))
        b.set(x, 1, z, B(rng.choice(["poppy", "cornflower", "oxeye_daisy", "allium"])))
    b.set(4, 1, 6, B("barrel", facing="up"))
    b.set(4, 2, 6, B("honey_block"))
    return b


def yard_smokehouse():
    b = Build(f"{NAME}_smokehouse", 7, 7, 8, seed=1847347)
    rng = b.rng
    piece_in(b, 3, 1, 0, "north")
    foundation(b, 1, 2, 5, 6, 0, 0, rng)
    for y in range(1, 4):
        for x in range(1, 6):
            for z in range(2, 7):
                if x in (1, 5) or z in (2, 6):
                    b.set(x, y, z, B(rng.choice(["cobblestone", "mossy_cobblestone", "stone"])))
    b.air(2, 1, 3, 4, 3, 5)
    b.door(3, 1, 2, "spruce", "north")
    roof_gable(b, 1, 5, 2, 6, 4, "spruce", B("cobblestone"), along="z", overhang=1, gable_overhang=0,
               brackets=False)
    b.campfire(3, 1, 5, lit=True)
    for x in (2, 4):
        b.item_frame(x, 2, 5, "north", rng.choice(["expanse:venison", "porkchop", "beef"]))
    b.set(2, 1, 3, B("barrel", facing="up"))
    return b


def yard_arbor():
    b = Build(f"{NAME}_arbor", 7, 6, 9, seed=1847349)
    rng = b.rng
    piece_in(b, 3, 1, 0, "north")
    for z in range(1, 9):
        b.set(3, 0, z, B("dirt_path"))
    for (x, z) in ((1, 2), (5, 2), (1, 7), (5, 7)):
        b.column(x, z, 1, 3, B(f"expanse:wisteria_log", axis="y"))
    for x in range(0, 7):
        for z in (2, 7):
            b.set(x, 4, z, B("expanse:wisteria_log", axis="x"))
    for z in range(1, 9):
        for x in (1, 3, 5):
            b.set(x, 4, z, B("expanse:wisteria_log", axis="z") if b.get(x, 4, z) is None else b.get(x, 4, z))
    for x in range(0, 7):
        for z in range(1, 9):
            if b.get(x, 4, z) is None and rng.random() < 0.6:
                b.set(x, 4, z, B("expanse:wisteria_leaves"))
            if b.get(x, 4, z) is not None and b.get(x, 3, z) is None and rng.random() < 0.55:
                kind = "expanse:wisteria_blossoms" if rng.random() < 0.75 else "expanse:azure_wisteria_blossoms"
                b.hang_moss(x, 3, z, rng.randint(1, 2), block=kind)
    chair(b, 1, 1, 4, "spruce", "west")
    chair(b, 1, 1, 5, "spruce", "west")
    b.set(5, 1, 4, B("expanse:potted_wisteria_sapling"))
    return b


def yard_well():
    b = Build(f"{NAME}_well", 7, 7, 7, seed=1847351)
    piece_in(b, 3, 2, 0, "north")                    # one layer deeper: the well shaft is buried
    for x in range(1, 6):
        for z in range(1, 6):
            b.set(x, 0, z, B("dirt"))
            b.set(x, 1, z, B(b.rng.choice(["cobblestone", "gravel", "coarse_dirt", "mossy_cobblestone"])))
    well(b, 3, 2, 3, stone="cobblestone", roof="spruce")
    b.set(5, 2, 1, B("water_cauldron", level=2))
    return b


def yard_fallen_giant():
    b = Build(f"{NAME}_fallen_giant", 6, 4, 13, seed=1847353)
    rng = b.rng
    piece_in(b, 2, 1, 0, "north")
    for z in range(2, 12):
        for x in (2, 3):
            b.set(x, 1, z, B(f"{W}_log", axis="z"))
            if rng.random() < 0.5:
                b.set(x, 2, z, B("moss_carpet") if rng.random() < 0.7 else B(rng.choice(["red_mushroom",
                                                                                         "brown_mushroom"])))
    for x in range(1, 5):                              # the root plate standing up at the end
        for y in range(1, 4):
            b.set(x, y, 12, B(f"{W}_wood", axis="y") if rng.random() < 0.7 else B("rooted_dirt"))
    b.set(1, 1, 5, B("fern"))
    b.set(4, 1, 8, B("fern"))
    return b


def pools():
    rp = random.Random(1847355)
    return {
        "start": [Piece(lodge_hunters(), 1, "lodge"), Piece(lodge_cutters(), 1, "lodge")],
        "cellar": [Piece(cellar_root(), 1, "lodge"), Piece(cellar_smoke(), 1, "lodge")],
        "paths": [Piece(path_piece(f"{NAME}_path_a", 5, P_YARD, rp), 3, "lodge", "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_b", 8, P_YARD, rp, stones="cobblestone"), 1, "lodge",
                        "terrain_matching"),
                  Empty(2)],
        "yard": [Piece(yard_garden(), 3, "lodge"), Piece(yard_woodshed(), 3, "lodge"), Piece(yard_bees(), 2, "lodge"),
                 Piece(yard_smokehouse(), 2, "lodge"), Piece(yard_arbor(), 2, "lodge"), Piece(yard_well(), 1, "lodge"),
                 Piece(yard_fallen_giant(), 2, "lodge")],
    }
