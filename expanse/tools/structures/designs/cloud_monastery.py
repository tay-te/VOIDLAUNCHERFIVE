"""Monastery of the Clouds - a terraced mountain abbey of the cloud forest (LANDMARK).

start  abbey  : three terraces climbing the slope, each held by a mossy retaining wall:
                 T0 forecourt  - gate arch, fountain, cypresses, flower beds, benches
                 T1 cloister   - arcaded walk with a lean-to roof round a garth (well,
                                 herb beds, sundial, flowering tree); kitchen garden and
                                 beehives outside the arcade; a grand stair from T0
                 T2 upper court- the abbey church (pews, altar, lore lectern, stained glass),
                                 the library with an enchanting table ringed by fifteen
                                 bookshelves and a hollow chiseled bookcase hiding the
                                 abbot's study (hidden chest), and the BELL TOWER: ladders
                                 up three floors to an open belfry (~46 above the forecourt,
                                 clear of the canopy). Under the church a dark crypt (via a
                                 trapdoor behind the altar) holds tombs, a skeleton spawner
                                 and the reliquary chest.
wings  (east + west of the cloister)   : refectory / dormitory / infirmary (brewing stand)
paths -> grounds (terrain-matching)     : terraced garden, apiary orchard, cemetery,
                                          belvedere lookout, hermit grotto, wash-house
"""
import random

from arch import leaves, plinth, ring, tree, wide_stair
from common import vines_on
from blocks import AIR, B
from builder import Build, fit_height, item, slab, split_build, stairs, written_book
from furnish import (beehive_patch, chain_lamp, chair, lamp_post, long_table, rug, scarecrow, shelf_items, table,
                     well, window)
from kit import chimney, roof_gable
from lore import BOOKS
from pieces import Empty, Piece
from terrain_paths import connector, path_piece, piece_in

NAME = "cloud_monastery"
STRUCTURE = dict(biomes=["cloud_forest"], spacing=56, separation=22, size=4, max_distance=76, max_relief=16)
LOOT = "expanse:chests/cloud_monastery"
HIDDEN = "expanse:chests/cloud_monastery_hidden"
P_WINGS = f"expanse:{NAME}/wings"
P_UPPER = f"expanse:{NAME}/upper"
J_UPPER = "expanse:monastery_upper"
P_PATHS = f"expanse:{NAME}/paths"
P_GROUNDS = f"expanse:{NAME}/grounds"
J_WING = "expanse:monastery_wing"
PROC = "cloud_monastery"

SB = "stone_brick"
SBS = "stone_bricks"
MSB = "mossy_stone_bricks"
CH = "chiseled_stone_bricks"
ROOF = "brick"
WALLS = (SBS, SBS, MSB, MSB, "cracked_stone_bricks", "cobblestone", "mossy_cobblestone")
TIMBER = "dark_oak"


def book():
    d = BOOKS[NAME]
    return written_book(d["title"], d["author"], d["pages"])


def abbot_book():
    return written_book("Abbot's Private Ledger", "Abbot Cyprian", [
        "Received from the valley: three sacks of flour, two candles, one confession I would rather not have "
        "heard. The old script on the enchanting desk grows harder to read each winter.",
        "I keep the reliquary key in the crypt with the founders. If the bones there walk again, ring the bell "
        "three times and do not go down alone.",
        "The novices think the hollow bookcase is a legend. Good."])


def stone(rng):
    return B(rng.choice(WALLS))


def cypress(b, x, y, z, h, rng):
    """Slim spruce-leaf cypress column."""
    for k in range(h):
        b.set(x, y + k, z, B("spruce_log", axis="y") if k < 2 else leaves("spruce_leaves"))
    for k in range(2, h - 1):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (k < h - 2 or rng.random() < 0.5) and b.get(x + dx, y + k, z + dz) is None:
                b.set(x + dx, y + k, z + dz, leaves("spruce_leaves"))
    b.set(x, y + h, z, leaves("spruce_leaves"))


def flowerbed(b, x0, z0, x1, z1, y, rng, flowers=("lily_of_the_valley", "azure_bluet", "allium", "oxeye_daisy",
                                                   "cornflower", "red_tulip", "white_tulip")):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            b.set(x, y, z, B("grass_block") if not edge else B(MSB))
            if not edge and rng.random() < 0.75:
                f = rng.choice(list(flowers) + ["rose_bush", "peony"])
                if f in ("rose_bush", "peony"):
                    b.tall_plant(x, y + 1, z, f)
                else:
                    b.set(x, y + 1, z, B(f))


def arch_row(b, cells, y, mat):
    """Upside-down stair arches between the columns at the given cells (row along x or z)."""
    for (x, z, f) in cells:
        b.set(x, y, z, stairs(mat, f, "top"))


# ------------------------------------------------------------------ start: the abbey
def abbey():
    b = Build(f"{NAME}_abbey_full", 47, 50, 52, seed=1847701)
    rng = b.rng
    # --- terraces
    plinth(b, 5, 1, 41, 14, 2, rng, wall=WALLS, fill="dirt", floor=B("grass_block"))
    plinth(b, 2, 15, 44, 32, 7, rng, wall=WALLS, fill="dirt", floor=B("grass_block"), buttress=5, buttress_mat=SBS)
    plinth(b, 2, 33, 44, 50, 12, rng, wall=WALLS, fill="dirt", floor=B("grass_block"), buttress=5,
           buttress_mat=SBS)
    for x in range(5, 42):                              # copings
        if not 19 <= x <= 27:
            b.set(x, 3, 1, B(f"{SB}_wall"))
    for (x, z) in ring(2, 15, 44, 32):
        if z == 15 and not 19 <= x <= 27 or x in (2, 44) and not 21 <= z <= 27:
            b.set(x, 8, z, B(f"{SB}_wall"))
    for (x, z) in ring(2, 33, 44, 50):
        if z == 33 and not 20 <= x <= 26 or x in (2, 44) or z == 50:
            b.set(x, 13, z, B(f"{SB}_wall"))
    # bridges to the wings
    for z in range(21, 28):
        for x in (0, 1, 45, 46):
            for y in range(0, 8):
                b.set(x, y, z, stone(rng) if y < 7 else B("gravel"))
    b.jigsaw(0, 8, 24, "west", target=J_WING, pool=P_WINGS)
    b.jigsaw(46, 8, 24, "east", target=J_WING, pool=P_WINGS)

    # --- T0 forecourt
    for x in range(17, 30):
        b.set(x, 0, 0, B(rng.choice(["gravel", "cobblestone", "gravel", "coarse_dirt"])))
    wide_stair(b, range(20, 27), 1, 1, "south", 2, SB)
    for x in (19, 27):
        for z in (1, 2):
            for y in range(0, 4):
                b.set(x, y, z, stone(rng))
    connector(b, 23, 1, 0, "north", P_PATHS)
    for z in range(3, 10):
        for x in range(21, 26):
            b.set(x, 2, z, B(rng.choice(["gravel", "gravel", "cobblestone", "andesite"])))
    # gate arch
    for x in (19, 27):
        for y in range(3, 9):
            b.set(x, y, 3, B(CH) if y in (3, 8) else stone(rng))
            b.set(x, y, 4, stone(rng))
    for x in range(19, 28):
        b.set(x, 9, 3, slab(SB))
        b.set(x, 9, 4, slab(SB))
        if 20 <= x <= 26:
            b.set(x, 8, 3, B(SBS))
            b.set(x, 8, 4, B(SBS))
    for z in (3, 4):
        b.set(20, 7, z, stairs(SB, "west", "top"))
        b.set(26, 7, z, stairs(SB, "east", "top"))
    for x in range(19, 28):
        b.set(x, 10, 3, stairs(ROOF, "north"))
        b.set(x, 10, 4, stairs(ROOF, "south"))
    b.set(23, 9, 2, AIR)
    b.sign(23, 8, 2, "spruce", ["", "Monastery", "of the Clouds", ""], wall_facing="north")
    b.set(23, 7, 4, B("lantern", hanging=True))
    # fountain, cypresses, beds and benches
    for (cx, cz) in ((12, 8), (34, 8)):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                edge = abs(dx) == 2 or abs(dz) == 2
                b.set(cx + dx, 2, cz + dz, B(SBS) if not edge else B(CH) if abs(dx) == abs(dz) else B(SBS))
                if edge:
                    b.set(cx + dx, 3, cz + dz, slab(SB))
                else:
                    b.set(cx + dx, 2, cz + dz, B("water", level=0))
                    b.set(cx + dx, 1, cz + dz, B(SBS))
        b.set(cx, 2, cz, B(SBS))
        b.set(cx, 3, cz, B(f"{SB}_wall"))
        b.set(cx, 4, cz, B(CH))
        b.set(cx, 5, cz, B("water", level=0))
    for (x, z) in ((7, 3), (7, 13), (39, 3), (39, 13), (17, 13), (29, 13)):
        cypress(b, x, 3, z, rng.randint(6, 8), rng)
    flowerbed(b, 15, 4, 19, 8, 2, rng)
    flowerbed(b, 27, 4, 31, 8, 2, rng)
    for x in (16, 17, 18):
        b.set(x, 3, 11, stairs("spruce", "north"))
    for x in (28, 29, 30):
        b.set(x, 3, 11, stairs("spruce", "north"))
    for (x, z) in ((20, 6), (26, 6), (20, 10), (26, 10)):
        lamp_post(b, x, 3, z, "spruce", 2)
    for x in range(6, 41):
        for z in range(2, 15):
            if b.get(x, 3, z) is None and b.get(x, 2, z) is not None and b.get(x, 2, z).short == "grass_block" \
                    and rng.random() < 0.2:
                b.set(x, 3, z, B("short_grass"))

    # --- grand stair T0 -> T1
    wide_stair(b, range(20, 27), 3, 10, "south", 5, SB, under=B(SBS))
    for x in (19, 27):
        for z in range(10, 15):
            for y in range(0, 4 + (z - 10) + 1):
                b.set(x, y, z, stone(rng))
            b.set(x, 4 + (z - 10) + 1, z, B(f"{SB}_wall"))
    for x in range(20, 27):
        for z in range(10, 15):
            for y in range(0, 3 + (z - 10)):
                b.set(x, y, z, B(SBS))

    # --- T1 cloister
    cloister(b, rng)
    # kitchen garden (west) and beehives (east) outside the arcade
    for x in range(4, 10):
        for z in range(17, 31):
            if z in (22, 23, 24, 25, 26):
                b.set(x, 7, z, B("gravel"))
                continue
            if x in (4, 9) or z in (17, 30, 21, 27):
                b.set(x, 7, z, B("coarse_dirt") if rng.random() < 0.6 else B("dirt_path"))
            else:
                b.set(x, 7, z, B("farmland", moisture=7))
                crop = rng.choice(["wheat", "carrots", "potatoes", "beetroots"])
                b.set(x, 8, z, B(crop, age=rng.randint(2, 3) if crop == "beetroots" else rng.randint(3, 7)))
    b.set(4, 8, 18, B("composter", level=6))
    b.set(9, 8, 29, B("water_cauldron", level=3))
    for (x, z) in ((39, 18), (42, 20), (39, 29)):
        beehive_patch(b, x, 8, z, "west", rng, flowers=("lily_of_the_valley", "azure_bluet", "allium", "poppy"))
    for z in range(22, 27):
        for x in range(36, 45):
            b.set(x, 7, z, B("gravel"))
    tree(b, 41, 8, 30, rng, "oak_log", "flowering_azalea_leaves", height=4, crown=2.2)

    # --- stair T1 -> T2
    wide_stair(b, range(21, 26), 8, 33, "south", 5, SB, under=B(SBS))
    for (x, z) in ((20, 38), (26, 38)):
        lamp_post(b, x, 13, z, "spruce", 2)

    # --- T2: church, library, bell tower, crypt
    church(b, rng)
    library(b, rng)
    bell_tower(b, rng)
    for x in range(15, 18):
        for z in range(38, 50):
            b.set(x, 12, z, B("gravel") if rng.random() < 0.7 else B("cobblestone"))
    for x in range(30, 33):
        for z in range(38, 50):
            b.set(x, 12, z, B("gravel") if rng.random() < 0.7 else B("cobblestone"))
    for x in range(4, 43):
        for z in (34, 35, 36, 37):
            if b.get(x, 13, z) is None and not 20 <= x <= 26:
                b.set(x, 12, z, B("gravel") if z in (36, 37) else B("grass_block"))
    for (x, z) in ((4, 35), (16, 35), (30, 35), (42, 35), (42, 48)):
        cypress(b, x, 13, z, rng.randint(6, 9), rng)
    vines_on(b, rng, 0.03, ymin=2, region=lambda x, y, z: (z < 15 and y < 2) or (z < 33 and y < 7) or y < 12)
    for x in range(3, 44):
        for z in range(34, 50):
            if b.get(x, 13, z) is None and b.get(x, 12, z) is not None and b.get(x, 12, z).short == "grass_block" \
                    and rng.random() < 0.25:
                b.set(x, 13, z, B("short_grass"))
    return b


def abbey_pieces():
    """The abbey is drawn whole, then cut at the T1/T2 retaining wall into two pieces (each within the
    48-block template limit) joined by a rigid jigsaw pair in the solid terrace under the upper stair."""
    lo, hi = split_build(abbey(), "z", 33, f"{NAME}_abbey", f"{NAME}_abbey_upper")
    lo.jigsaw(23, 7, 32, "south", target=J_UPPER, pool=P_UPPER, final="minecraft:stone_bricks")
    hi.jigsaw(23, 7, 0, "north", name=J_UPPER, final="minecraft:stone_bricks")
    return fit_height(lo), fit_height(hi)


def cloister(b, rng):
    ox0, oz0, ox1, oz1 = 11, 16, 35, 32          # outer wall line
    ix0, iz0, ix1, iz1 = 14, 19, 32, 29          # inner arcade line
    for x in range(ox0, ox1 + 1):
        for z in range(oz0, oz1 + 1):
            if not (ix0 < x < ix1 and iz0 < z < iz1):
                b.set(x, 7, z, B(SBS) if (x + z) % 2 else B("polished_andesite"))
    # outer wall with arched openings
    for (x, z) in ring(ox0, oz0, ox1, oz1):
        u = (x - ox0) if z in (oz0, oz1) else (z - oz0)
        corner = x in (ox0, ox1) and z in (oz0, oz1)
        door = (z in (oz0, oz1) and 21 <= x <= 25) or (x in (ox0, ox1) and 22 <= z <= 26)
        for y in range(8, 14):
            if door and y <= 11:
                b.set(x, y, z, AIR)
                continue
            if not corner and not door and u % 4 == 2 and y in (9, 10):
                b.set(x, y, z, B("iron_bars") if y == 9 else B(SBS))
                continue
            b.set(x, y, z, stone(rng))
    # inner arcade: columns every second cell, arches between them
    for (x, z) in ring(ix0, iz0, ix1, iz1):
        u = (x - ix0) if z in (iz0, iz1) else (z - iz0)
        corner = x in (ix0, ix1) and z in (iz0, iz1)
        gap = (z in (iz0, iz1) and x == 23) or (x in (ix0, ix1) and z == 24)
        if corner or (u % 2 == 0 and not gap):
            b.set(x, 8, z, B(CH))
            b.set(x, 9, z, B(f"{SB}_wall"))
            b.set(x, 10, z, B(f"{SB}_wall"))
        else:
            if z in (iz0, iz1):
                f = "east" if (x - ix0) % 2 else "west"
            else:
                f = "south" if (z - iz0) % 2 else "north"
            b.set(x, 10, z, stairs(SB, f, "top"))
        b.set(x, 11, z, B(SBS))
    b.air(ox0 + 1, 8, oz0 + 1, ox1 - 1, 10, iz0 - 1)
    b.air(ox0 + 1, 8, iz1 + 1, ox1 - 1, 10, oz1 - 1)
    b.air(ox0 + 1, 8, iz0, ix0 - 1, 10, iz1)
    b.air(ix1 + 1, 8, iz0, ox1 - 1, 10, iz1)
    # lean-to roof sloping down to the garth
    for k, yy in ((0, 14), (1, 14), (2, 13), (3, 12), (4, 12)):
        rx0, rz0, rx1, rz1 = ox0 + k, oz0 + k, ox1 - k, oz1 - k
        for (x, z) in ring(rx0, rz0, rx1, rz1):
            if k == 0:
                b.set(x, yy, z, slab(SB))
                continue
            f = "north" if z == rz0 else "south" if z == rz1 else "west" if x == rx0 else "east"
            b.set(x, yy, z, stairs(ROOF, f))
            if yy - 1 >= 11 and b.get(x, yy - 1, z) is None:
                b.set(x, yy - 1, z, B("dark_oak_planks"))
    for x in range(ox0 + 1, ox1):
        for z in range(oz0 + 1, oz1):
            if not (ix0 <= x <= ix1 and iz0 <= z <= iz1):
                if b.get(x, 11, z) is None:
                    b.set(x, 11, z, B("dark_oak_planks"))
    for (x, z) in ((ox0 + 2, oz0 + 2), (ox1 - 2, oz0 + 2), (ox0 + 2, oz1 - 2), (ox1 - 2, oz1 - 2), (23, oz0 + 1),
                   (23, oz1 - 1), (ox0 + 1, 24), (ox1 - 1, 24)):
        chain_lamp(b, x, 11, z, 0)
    # the garth
    for x in range(ix0 + 1, ix1):
        for z in range(iz0 + 1, iz1):
            b.set(x, 7, z, B("grass_block"))
    for x in range(ix0 + 1, ix1):
        b.set(x, 7, 24, B("gravel"))
    for z in range(iz0 + 1, iz1):
        b.set(23, 7, z, B("gravel"))
    well(b, 23, 8, 24, stone="cobblestone", roof="spruce")
    for (x0, z0, x1, z1) in ((15, 20, 21, 22), (25, 20, 31, 22), (15, 26, 21, 28), (25, 26, 31, 28)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if rng.random() < 0.55:
                    f = rng.choice(["lily_of_the_valley", "azure_bluet", "allium", "fern", "sweet_berry_bush",
                                    "white_tulip", "short_grass"])
                    b.set(x, 8, z, B(f, age=rng.randint(1, 3)) if f == "sweet_berry_bush" else B(f))
    tree(b, 17, 8, 21, rng, "oak_log", "flowering_azalea_leaves", height=4, crown=2.3)
    b.set(29, 8, 27, B(SBS))                                     # sundial
    b.set(29, 9, 27, B("waxed_lightning_rod", facing="up"))      # gnomon
    for (x, z, f) in ((21, 21, "west"), (25, 27, "east")):
        b.set(x, 8, z, stairs("spruce", f))
    b.armor_stand(30, 8, 21, yaw=225.0, equipment={"head": "leather_helmet", "chest": "leather_chestplate",
                                                    "mainhand": "iron_hoe"})


def church(b, rng):
    x0, x1, z0, z1 = 18, 28, 39, 49
    fy = 12
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, fy, z, B("polished_andesite") if (x + z) % 2 else B("stone_bricks"))
    for (x, z) in ring(x0, z0, x1, z1):
        corner = x in (x0, x1) and z in (z0, z1)
        for y in range(fy + 1, fy + 8):
            b.set(x, y, z, B(CH) if corner and y == fy + 1 else stone(rng))
    b.air(x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 7, z1 - 1)
    # buttresses along the long walls
    for z in (41, 44, 47):
        for x, f in ((x0 - 1, "west"), (x1 + 1, "east")):
            for y in range(fy + 1, fy + 5):
                b.set(x, y, z, B(SBS))
            b.set(x, fy + 5, z, stairs(SB, "east" if f == "west" else "west"))
    # tall stained-glass windows
    for z in (42, 45, 48):
        for x in (x0, x1):
            for y in range(fy + 2, fy + 6):
                b.set(x, y, z, B("light_blue_stained_glass_pane") if y < fy + 5 else B("yellow_stained_glass_pane"))
    # west front: portal and rose window
    for x in (22, 23, 24):
        for y in range(fy + 1, fy + 4):
            b.set(x, y, z0, AIR)
    b.set(22, fy + 3, z0, stairs(SB, "east", "top"))
    b.set(24, fy + 3, z0, stairs(SB, "west", "top"))
    for (dx, dy) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        b.set(23 + dx, fy + 6 + dy, z0, B("yellow_stained_glass_pane") if (dx, dy) == (0, 0) else
              B("light_blue_stained_glass_pane"))
    ridge = roof_gable(b, x0, x1, z0, z1, fy + 8, ROOF, lambda x, y, z: stone(rng), along="z", overhang=1,
                       gable_overhang=1)
    b.fill(x0 + 1, fy + 8, z0 + 1, x1 - 1, fy + 8, z1 - 1, B("dark_oak_planks"))
    b.set(23, ridge + 1, z0, B(f"{SB}_wall"))
    b.set(23, ridge + 2, z0, B(f"{SB}_wall"))
    # interior: pews, altar, lectern, candles
    for z in range(41, 46):
        for x in (20, 21, 25, 26):
            b.set(x, fy + 1, z, stairs("spruce", "south"))
    b.fill(20, fy + 1, 47, 26, fy + 1, 48, B("polished_andesite"))
    for x in range(20, 27):
        b.set(x, fy + 1, 46, slab("polished_andesite") if x not in (22, 23, 24) else stairs("polished_andesite",
                                                                                                "south"))
    b.set(23, fy + 2, 48, B(CH))
    b.set(22, fy + 2, 48, slab("stone_brick", "top"))
    b.set(24, fy + 2, 48, slab("stone_brick", "top"))
    b.set(23, fy + 3, 48, B("white_carpet"))
    b.set(22, fy + 3, 48, B("candle", candles=3, lit=True))
    b.set(24, fy + 3, 48, B("candle", candles=3, lit=True))
    b.lectern(23, fy + 1, 45, "north", book())
    b.banner(19, fy + 5, 47, "white", [("cross", "yellow"), ("border", "light_blue")], wall_facing="east")
    b.banner(27, fy + 5, 47, "white", [("cross", "yellow"), ("border", "light_blue")], wall_facing="west")
    for z in (42, 45):
        chain_lamp(b, 23, fy + 8, z, 2)
    for (x, z) in ((19, 40), (27, 40)):
        b.set(x, fy + 1, z, B("potted_lily_of_the_valley"))
    # trapdoor behind the altar and a ladder into the crypt
    b.set(27, fy, 48, B("spruce_trapdoor", facing="west", half="top", open=False))
    crypt(b, rng)


def crypt(b, rng):
    """Dark vaulted crypt inside the upper terrace, under the church (no light: the spawner works)."""
    x0, x1, z0, z1 = 19, 28, 40, 49
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(4, 12):
                b.set(x, y, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "tuff"])))
    b.air(x0, 6, z0, x1 - 1, 9, z1)
    for z in range(z0, z1 + 1):
        b.set(x0, 9, z, stairs(SB, "east", "top"))
        b.set(x1 - 1, 9, z, stairs(SB, "west", "top"))
    for x in range(x0, x1):
        b.set(x, 5, z0 + 0, B("tuff"))
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            b.set(x, 5, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cobblestone", "gravel"])))
    for y in range(6, 12):                              # ladder up to the trapdoor behind the altar
        b.set(x1 - 1, y, 48, B("ladder", facing="west"))
        b.set(x1, y, 48, B("stone_bricks"))
    # tombs in niches
    for z in (41, 44, 47):
        for x in (x0, x1 - 1):
            b.set(x, 6, z, B("polished_andesite"))
            b.set(x, 7, z, slab("stone_brick"))
            if rng.random() < 0.6:
                b.set(x, 8, z, B("skeleton_skull", rotation=rng.randint(0, 15)))
    b.spawner(23, 6, 45, "skeleton", min_delay=300, max_delay=900, count=2, max_nearby=4)
    b.set(23, 6, 44, B("cobweb"))
    b.set(21, 8, 42, B("cobweb"))
    b.set(25, 8, 48, B("cobweb"))
    b.chest(23, 6, 49, "north", LOOT)
    b.pot(22, 6, 49, "north", ("skull_pottery_sherd", "brick", "brick", "brick"))
    b.pot(24, 6, 49, "north", ("brick", "brick", "mourner_pottery_sherd", "brick"))
    b.set(21, 6, 49, B("candle", candles=2, lit=False))
    b.set(25, 6, 40, B("candle", candles=1, lit=False))


def library(b, rng):
    x0, x1, z0, z1 = 3, 14, 36, 49
    fy = 12
    b.fill(x0, fy, z0, x1, fy, z1, B("spruce_planks"))
    for (x, z) in ring(x0, z0, x1, z1):
        for y in range(fy + 1, fy + 7):
            b.set(x, y, z, stone(rng))
    b.air(x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 6, z1 - 1)
    for z in (38, 41, 44):
        for y in (fy + 2, fy + 3):
            b.set(x0, y, z, B("glass_pane"))
    for x in (6, 11):
        for y in (fy + 2, fy + 3):
            b.set(x, y, z0, B("glass_pane"))
    b.door(x1, fy + 1, 39, "spruce", "east")
    ridge = roof_gable(b, x0, x1, z0, z1, fy + 7, ROOF, lambda x, y, z: stone(rng), along="z", overhang=1,
                       gable_overhang=1)
    b.fill(x0 + 1, fy + 7, z0 + 1, x1 - 1, fy + 7, z1 - 1, B("spruce_planks"))
    chimney(b, x0 + 1, z1 - 1, fy + 1, ridge + 1, rng, mats=(SBS, MSB, "cobblestone"))
    # enchanting table with fifteen bookshelves two blocks away (one gap to walk in)
    ex, ez = 8, 40
    b.set(ex, fy + 1, ez, B("enchanting_table"))
    k = 0
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) != 2:
                continue
            if (dx, dz) == (0, 2):
                continue                                   # the gap toward the door side
            b.set(ex + dx, fy + 1, ez + dz, B("bookshelf"))
            k += 1
    assert k == 15
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                b.set(ex + dx, fy + 1, ez + dz, B("purple_carpet"))
    b.set(ex - 2, fy + 2, ez - 2, B("candle", candles=3, lit=True))
    b.set(ex + 2, fy + 2, ez - 2, B("candle", candles=2, lit=True))
    # scriptorium desks
    for z in (43, 45):
        table(b, 6, fy + 1, z, "spruce", top=slab("spruce", "top"))
        b.set(7, fy + 1, z, slab("spruce", "top"))
        chair(b, 6, fy + 1, z + 1, "spruce", "south")
        chair(b, 7, fy + 1, z + 1, "spruce", "south")
        b.item_frame(7, fy + 2, z, "up", rng.choice(["writable_book", "feather", "paper"]))
        b.set(6, fy + 2, z, B("candle", candles=1, lit=True))
    b.lectern(11, fy + 1, 44, "west")
    for z in range(41, 46):
        b.set(13, fy + 1, z, B("bookshelf"))
        b.set(13, fy + 2, z, B("bookshelf"))
    for z in range(42, 46):
        b.bookshelf(13, fy + 3, z, "west", slots=tuple(rng.sample(range(6), rng.randint(1, 5))))
    b.shelf(4, fy + 2, 37, "spruce", "east", shelf_items(rng, "study"))
    b.shelf(4, fy + 2, 46, "spruce", "east", shelf_items(rng, "study"))
    chain_lamp(b, 8, fy + 7, 40, 2)
    chain_lamp(b, 8, fy + 7, 45, 2)
    # the bookshelf wall at z=47 hides the abbot's study (z 48); one hollow chiseled case is the way in
    for x in range(x0 + 1, x1):
        for y in range(fy + 1, fy + 5):
            b.set(x, y, 47, B("bookshelf"))
    b.bookshelf(10, fy + 1, 47, "north", slots=(2,))
    b.bookshelf(10, fy + 2, 47, "north", slots=())
    b.air(x0 + 1, fy + 1, 48, x1 - 1, fy + 4, 48)
    b.chest(12, fy + 1, 48, "west", HIDDEN)
    b.lectern(8, fy + 1, 48, "south", abbot_book())
    b.set(6, fy + 1, 48, B("brewing_stand", has_bottle_0=True, has_bottle_1=False, has_bottle_2=True))
    b.set(5, fy + 1, 48, B("barrel", facing="up"))
    b.set(4, fy + 1, 48, B("candle", candles=4, lit=False))
    b.set(11, fy + 1, 48, B("cobweb"))
    b.painting(9, fy + 3, 48, "south", "skull_and_roses")


def bell_tower(b, rng):
    x0, x1, z0, z1 = 34, 40, 40, 46
    fy = 12
    top = 38
    for (x, z) in ring(x0, z0, x1, z1):
        corner = x in (x0, x1) and z in (z0, z1)
        for y in range(fy, top + 1):
            b.set(x, y, z, B(CH) if corner and y % 7 == 5 else stone(rng))
    b.fill(x0 + 1, fy, z0 + 1, x1 - 1, fy, z1 - 1, B(SBS))
    b.air(x0 + 1, fy + 1, z0 + 1, x1 - 1, top - 1, z1 - 1)
    # floors with ladder holes
    for fl in (19, 26, 33):
        b.fill(x0 + 1, fl, z0 + 1, x1 - 1, fl, z1 - 1, B("spruce_planks"))
    for y in range(fy + 1, 34):
        b.set(x0 + 1, y, z0 + 1, B("ladder", facing="south"))
    # door, slit windows
    for y in (fy + 1, fy + 2):
        b.set(37, y, z1, AIR)
    b.door(37, fy + 1, z1, "spruce", "south")
    for y in (16, 23, 30):
        for (x, z) in ((37, z0), (x0, 43), (x1, 43)):
            b.set(x, y, z, B("iron_bars"))
            b.set(x, y + 1, z, B("iron_bars"))
    # belfry: open arches on every side
    for (x, z) in ring(x0, z0, x1, z1):
        u = (x - x0) if z in (z0, z1) else (z - z0)
        if 2 <= u <= 4:
            for y in range(34, 37):
                b.set(x, y, z, AIR)
            f = None
        if u in (2, 4):
            if z in (z0, z1):
                b.set(x, 37, z, stairs(SB, "east" if u == 2 else "west", "top"))
            else:
                b.set(x, 37, z, stairs(SB, "south" if u == 2 else "north", "top"))
        if 2 <= u <= 4:
            b.set(x, 34, z, B(f"{SB}_wall"))
    b.fill(x0 + 1, top, z0 + 1, x1 - 1, top, z1 - 1, B("spruce_planks"))
    b.bell(37, top - 1, 43, "ceiling", "south")
    for (x, z) in ring(x0 - 1, z0 - 1, x1 + 1, z1 + 1):
        b.set(x, top, z, stairs(SB, "north" if z == z0 - 1 else "south" if z == z1 + 1 else
                                "west" if x == x0 - 1 else "east", "top"))
    # spire
    k = 0
    while x0 + k <= x1 - k:
        for (x, z) in ring(x0 + k, z0 + k, x1 - k, z1 - k):
            if x0 + k == x1 - k:
                b.set(x, top + 1 + k, z, B("bricks"))
                continue
            f = "south" if z == z0 + k else "north" if z == z1 - k else "east" if x == x0 + k else "west"
            b.set(x, top + 1 + k, z, stairs(ROOF, f))
        k += 1
    b.set(37, top + 1 + k, 43, B(f"{SB}_wall"))
    b.set(37, top + 2 + k, 43, B("lightning_rod", facing="up"))
    for (x, z) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
        b.set(x, top + 1, z, B(f"{SB}_wall"))
        b.set(x, top + 2, z, B("lantern"))
    b.chest(39, 34, 45, "north", LOOT)


# ------------------------------------------------------------------ wings
def wing_base(name, seed):
    b = Build(name, 19, 22, 25, seed=seed)
    rng = b.rng
    plinth(b, 0, 0, 17, 24, 7, rng, wall=WALLS, fill="dirt", floor=B("grass_block"), buttress=5, buttress_mat=SBS)
    for (x, z) in ring(0, 0, 17, 24):
        if not (x == 0 and 9 <= z <= 15) and not (x in (15, 16, 17) and z == 0):
            b.set(x, 8, z, B(f"{SB}_wall"))
    for z in range(9, 16):
        b.set(0, 7, z, B("gravel"))
        b.set(1, 7, z, B("gravel"))
        b.set(2, 7, z, B("gravel"))
    # outer stair down the front corner and the path connector
    for i in range(7):
        for x in (15, 16):
            b.set(x, 1 + i, 7 - i, stairs(SB, "south"))
            for h in range(1, 4):
                b.set(x, 1 + i + h, 7 - i, AIR)
            for y in range(0, 1 + i):
                b.set(x, y, 7 - i, B(SBS))
    for x in (15, 16):
        b.set(x, 0, 0, B("gravel"))
        b.set(x, 1, 0, AIR)
    b.jigsaw(0, 8, 12, "west", name=J_WING)
    connector(b, 16, 1, 0, "north", P_PATHS)
    # undercroft storeroom in the terrace, with a postern door and a second path out
    b.air(13, 1, 17, 16, 3, 22)
    for z in range(17, 23):
        for x in range(13, 17):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "gravel", "stone_bricks"])))
    b.door(17, 1, 20, "spruce", "east")
    b.set(18, 0, 20, B("gravel"))
    for (x, z) in ((13, 17), (14, 17), (13, 18), (16, 22), (15, 22)):
        b.barrel(x, 1, z, "up")
    b.set(13, 2, 17, B("barrel", facing="up"))
    b.set(13, 1, 22, B("hay_block", axis="y"))
    b.set(14, 1, 22, B("hay_block", axis="x"))
    b.set(13, 1, 21, B("pumpkin"))
    chain_lamp(b, 15, 4, 19, 0)
    connector(b, 18, 1, 20, "east", P_PATHS)
    vines_on(b, rng, 0.035, ymin=2, region=lambda x, y, z: y < 7)
    return b, rng


def wing_hall(b, rng, fy=7, plaster="calcite"):
    """Stone ground course, plaster upper wall with timber posts, brick gable roof; returns ridge y."""
    x0, x1, z0, z1 = 3, 13, 9, 23
    b.fill(x0, fy, z0, x1, fy, z1, B("spruce_planks"))
    for (x, z) in ring(x0, z0, x1, z1):
        post = (x in (x0, x1) and z in (z0, z1)) or ((z - z0) % 4 == 0 and x in (x0, x1)) or \
            ((x - x0) % 5 == 0 and z in (z0, z1))
        for y in range(fy + 1, fy + 6):
            if y <= fy + 1:
                b.set(x, y, z, stone(rng))
            elif post:
                b.set(x, y, z, B(f"stripped_{TIMBER}_log", axis="y"))
            elif y == fy + 5:
                b.set(x, y, z, B(f"stripped_{TIMBER}_log", axis="x" if z in (z0, z1) else "z"))
            else:
                b.set(x, y, z, B(plaster))
    b.air(x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 5, z1 - 1)
    for z in (11, 15, 19, 21):
        window(b, x1, fy + 2, z, "east", 2, shutters="spruce")
        if z != 15:
            window(b, x0, fy + 2, z, "west", 2)
    for y in (fy + 1, fy + 2):
        b.set(x0, y, 12, AIR)
    b.door(x0, fy + 1, 12, "spruce", "west")
    for y in (fy + 1, fy + 2):
        b.set(8, y, z0, AIR)
    b.door(8, fy + 1, z0, "spruce", "north")
    ridge = roof_gable(b, x0, x1, z0, z1, fy + 6, ROOF, lambda x, y, z: B(plaster), along="z", overhang=1,
                       gable_overhang=1)
    b.fill(x0 + 1, fy + 6, z0 + 1, x1 - 1, fy + 6, z1 - 1, B("spruce_planks"))
    return ridge


def wing_refectory():
    b, rng = wing_base(f"{NAME}_wing_refectory", 1847711)
    ridge = wing_hall(b, rng)
    fy = 7
    long_table(b, [(8, z) for z in range(11, 20)], fy + 1, "spruce", cloth="white_carpet")
    for z in range(12, 19):
        chair(b, 7, fy + 1, z, "spruce", "west")
        chair(b, 9, fy + 1, z, "spruce", "east")
    b.lectern(11, fy + 1, 15, "west")
    chimney(b, 12, 21, fy + 1, ridge + 1, rng, mats=(SBS, MSB, "cobblestone"))
    b.set(11, fy + 1, 22, B("smoker", facing="west"))
    b.set(11, fy + 1, 20, B("furnace", facing="west"))
    b.set(4, fy + 1, 22, B("water_cauldron", level=3))
    b.chest(4, fy + 1, 21, "east", LOOT)
    for z in (19, 20):
        b.barrel(4, fy + 1, z, "east")
    b.set(4, fy + 2, 20, B("pumpkin"))
    b.shelf(4, fy + 3, 21, "spruce", "east", shelf_items(rng, "kitchen"))
    for z in (12, 17):
        chain_lamp(b, 8, fy + 6, z, 1)
    b.set(4, fy + 1, 10, B("potted_lily_of_the_valley"))
    b.item_frame(8, fy + 3, 22, "north", "bread")
    # herb-and-salad beds outside
    for x in range(4, 13):
        for z in (2, 3, 4, 5):
            if z in (2, 5):
                b.set(x, 7, z, B("coarse_dirt"))
            else:
                b.set(x, 7, z, B("farmland", moisture=7))
                b.set(x, 8, z, B(rng.choice(["carrots", "potatoes", "beetroots"]), age=3)
                      if rng.random() < 0.5 else B("beetroots", age=3))
    return b


def wing_dormitory():
    b, rng = wing_base(f"{NAME}_wing_dormitory", 1847713)
    wing_hall(b, rng)
    fy = 7
    for i, z in enumerate((11, 14, 17, 20)):
        b.bed(5, fy + 1, z, "west", "brown")
        b.bed(11, fy + 1, z, "east", "brown")
        b.set(4, fy + 1, z + 1, B("spruce_trapdoor", facing="east", half="top", open=False))
        b.set(12, fy + 1, z + 1, B("spruce_trapdoor", facing="west", half="top", open=False))
        b.set(4, fy + 2, z + 1, B("candle", candles=1, lit=i % 2 == 0))
    b.chest(12, fy + 1, 22, "west", LOOT)
    b.set(4, fy + 1, 22, B("water_cauldron", level=2))
    b.lectern(8, fy + 1, 22, "north")
    rug(b, 7, 11, 9, 20, fy + 1, "brown", None)
    b.armor_stand(9, fy + 1, 10, yaw=180.0, equipment={"head": "leather_helmet", "chest": "leather_chestplate",
                                                       "legs": "leather_leggings"})
    for z in (13, 19):
        chain_lamp(b, 8, fy + 6, z, 1)
    b.painting(10, fy + 3, 22, "north", "pond")
    for (x, z) in ((5, 3), (11, 3)):
        cypress(b, x, 8, z, 6, rng)
    for x in range(7, 10):
        b.set(x, 8, 4, stairs("spruce", "north"))
    return b


def wing_infirmary():
    b, rng = wing_base(f"{NAME}_wing_infirmary", 1847715)
    wing_hall(b, rng)
    fy = 7
    for z in (11, 15, 19):
        b.bed(5, fy + 1, z, "west", "white")
        b.set(4, fy + 1, z + 1, B("flower_pot"))
    b.set(11, fy + 1, 20, B("brewing_stand", has_bottle_0=True, has_bottle_1=True, has_bottle_2=False))
    b.set(11, fy + 1, 21, B("water_cauldron", level=2))
    b.set(11, fy + 1, 22, B("cauldron"))
    b.chest(11, fy + 1, 19, "west", LOOT)
    for z in (12, 13, 14):
        b.shelf(12, fy + 2, z, "spruce", "west", shelf_items(rng, "alchemy"))
    for z in (16, 17):
        b.set(12, fy + 1, z, B("barrel", facing="west"))
    b.item_frame(12, fy + 3, 17, "west", "glistering_melon_slice")
    b.set(9, fy + 1, 22, B("potted_azure_bluet"))
    b.set(8, fy + 1, 22, B("potted_lily_of_the_valley"))
    for z in (13, 19):
        chain_lamp(b, 8, fy + 6, z, 1)
    # physic garden outside
    for x in range(3, 14):
        for z in range(2, 7):
            edge = x in (3, 13) or z in (2, 6)
            b.set(x, 7, z, B(MSB) if edge else B("grass_block"))
            if not edge and rng.random() < 0.7:
                b.set(x, 8, z, B(rng.choice(["lily_of_the_valley", "azure_bluet", "allium", "poppy", "cornflower",
                                             "fern", "short_grass"])))
    return b


# ------------------------------------------------------------------ grounds
def ground_terraces():
    b = Build(f"{NAME}_terraces", 15, 10, 16, seed=1847721)
    rng = b.rng
    piece_in(b, 7, 1, 0, "north")
    for k, (z0, z1) in enumerate(((1, 5), (6, 10), (11, 15))):
        top = k * 2
        for x in range(0, 15):
            for z in range(z0, z1 + 1):
                for y in range(0, top + 1):
                    edge = z == z0 and y >= top - 1 and k > 0 or x in (0, 14)
                    b.set(x, y, z, B(rng.choice(WALLS)) if edge else B("dirt"))
                if x in (0, 14) or z == z0:
                    b.set(x, top, z, B(rng.choice(WALLS)))
                    continue
                if x == 7:
                    b.set(x, top, z, B("dirt_path"))
                    continue
                b.set(x, top, z, B("farmland", moisture=7))
                crop = ["potatoes", "carrots", "beetroots"][k]
                b.set(x, top + 1, z, B(crop, age=rng.randint(2, 3) if crop == "beetroots" else rng.randint(3, 7)))
        if k > 0:
            b.set(7, top - 1, z0, stairs(SB, "south"))
            b.set(7, top, z0, stairs(SB, "south"))
    b.set(7, 1, 1, AIR)
    for (x, z) in ((1, 5), (13, 10)):
        b.set(x, 1 + (z // 6) * 2, z, B("water_cauldron", level=3))
    scarecrow(b, 3, 3, 8, 30.0)
    b.set(13, 5, 14, B("composter", level=5))
    b.barrel(1, 5, 14, "up")
    return b


def ground_apiary():
    b = Build(f"{NAME}_apiary", 13, 9, 13, seed=1847723)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    for x in range(0, 13):
        for z in range(1, 13):
            b.set(x, 0, z, B("grass_block"))
    for (x, z) in ((3, 4), (9, 4), (3, 9), (9, 9)):
        beehive_patch(b, x, 1, z, "south", rng, flowers=("lily_of_the_valley", "azure_bluet", "allium", "poppy",
                                                          "cornflower"))
    tree(b, 6, 1, 7, rng, "oak_log", "flowering_azalea_leaves", height=4, crown=2.5)
    for x in range(0, 13):
        for z in (1, 12):
            if x % 3:
                b.set(x, 1, z, B("spruce_fence"))
    b.set(6, 1, 1, B("spruce_fence_gate", facing="north"))
    b.set(0, 1, 6, B("barrel", facing="up"))
    b.set(0, 2, 6, B("honey_block"))
    b.campfire(12, 1, 6, lit=False)
    return b


def ground_cemetery():
    b = Build(f"{NAME}_cemetery", 15, 11, 15, seed=1847725)
    rng = b.rng
    piece_in(b, 7, 1, 0, "north")
    for x in range(0, 15):
        for z in range(1, 15):
            b.set(x, 0, z, B("grass_block") if rng.random() < 0.8 else B("podzol"))
    for (x, z) in ring(0, 1, 14, 14):
        if not (z == 1 and 6 <= x <= 8):
            b.set(x, 1, z, stone(rng))
            if rng.random() < 0.6:
                b.set(x, 2, z, B(f"{SB}_wall") if rng.random() < 0.8 else B("mossy_stone_brick_wall"))
    b.set(6, 1, 1, B(CH))
    b.set(8, 1, 1, B(CH))
    b.set(6, 2, 1, B("lantern"))
    b.set(8, 2, 1, B("lantern"))
    names = [["Brother", "Anselm", "rang the bell", "for forty years"], ["Sister", "Odile", "kept the", "garden"],
             ["Brother", "Gregor", "fell asleep", "copying"], ["Here lies", "a novice", "who opened", "the case"],
             ["Abbot", "Benedikt", "founder", ""], ["", "Unknown", "pilgrim", ""]]
    i = 0
    for z in (4, 7, 10):
        for x in (3, 5, 9, 11):
            if rng.random() < 0.8:
                b.set(x, 1, z, B(rng.choice(["mossy_stone_brick_wall", "stone_brick_wall", "cobblestone_wall"])))
                b.set(x, 1, z + 1, B("moss_carpet"))
                if i < len(names) and rng.random() < 0.7:
                    b.sign(x, 2, z, "spruce", names[i], rotation=0)
                    i += 1
    # little ossuary chapel
    for (x, z) in ring(5, 11, 9, 13):
        for y in range(1, 5):
            b.set(x, y, z, stone(rng))
    b.air(6, 1, 12, 8, 3, 12)
    b.set(7, 1, 11, AIR)
    b.set(7, 2, 11, AIR)
    b.set(7, 1, 12, B("candle", candles=2, lit=False))
    b.set(6, 1, 12, B("skeleton_skull", rotation=8))
    b.pot(8, 1, 12, "north", ("skull_pottery_sherd", "brick", "brick", "brick"))
    for x in range(4, 11):
        b.set(x, 5, 11, stairs(ROOF, "north"))
        b.set(x, 5, 13, stairs(ROOF, "south"))
        b.set(x, 6, 12, slab(ROOF))
    b.set(7, 5, 12, B("bricks"))
    b.set(7, 7, 12, B(f"{SB}_wall"))
    for (x, z) in ((1, 13), (13, 3), (2, 3)):
        cypress(b, x, 1, z, rng.randint(5, 7), rng)
    return b


def ground_belvedere():
    b = Build(f"{NAME}_belvedere", 9, 23, 10, seed=1847727)
    rng = b.rng
    piece_in(b, 4, 1, 0, "north")
    cx, cz = 4, 5
    for x in range(0, 9):
        for z in range(1, 10):
            b.set(x, 0, z, B(rng.choice(["grass_block", "coarse_dirt", "grass_block"])))
    for (x, z) in ring(2, 3, 6, 7):
        for y in range(1, 13):
            b.set(x, y, z, stone(rng))
    b.air(3, 1, 4, 5, 12, 6)
    b.set(4, 1, 3, AIR)
    b.set(4, 2, 3, AIR)
    b.door(4, 1, 3, "spruce", "north")
    for y in range(1, 14):
        b.set(5, y, 6, B("ladder", facing="north"))
    for y in (5, 9):
        b.set(2, y, 5, B("iron_bars"))
        b.set(6, y, 5, B("iron_bars"))
    b.fill(1, 13, 2, 7, 13, 8, B(SBS))
    b.set(5, 13, 6, B("spruce_trapdoor", facing="north", half="top", open=False))
    for (x, z) in ring(1, 2, 7, 8):
        b.set(x, 14, z, B(f"{SB}_wall"))
    for (x, z) in ((1, 2), (7, 2), (1, 8), (7, 8)):
        b.set(x, 15, z, B(f"{SB}_wall"))
        b.set(x, 16, z, B(f"{SB}_wall"))
    for k in range(5):
        for (x, z) in ring(0 + k, 1 + k, 8 - k, 9 - k):
            if k == 4:
                b.set(x, 17 + k, z, B("bricks"))
                b.set(x, 18 + k, z, B("lightning_rod", facing="up"))
                continue
            f = "south" if z == 1 + k else "north" if z == 9 - k else "east" if x == k else "west"
            b.set(x, 17 + k, z, stairs(ROOF, f))
    b.item_frame(4, 15, 2, "south", "spyglass")
    b.set(6, 14, 7, B("lantern"))
    return b


def ground_grotto():
    b = Build(f"{NAME}_grotto", 13, 11, 12, seed=1847729)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    import math
    for x in range(0, 13):
        for z in range(1, 12):
            b.set(x, 0, z, B(rng.choice(["moss_block", "grass_block", "coarse_dirt"])))
            d = math.hypot((x - 6) / 6.0, (z - 9) / 4.0)
            h = int(8 * (1 - d)) + rng.randint(0, 1)
            for y in range(1, h + 1):
                b.set(x, y, z, B(rng.choice(["stone", "andesite", "mossy_cobblestone", "moss_block", "tuff"])))
    b.air(4, 1, 5, 8, 4, 9)
    b.air(5, 1, 3, 7, 3, 5)
    b.set(6, 1, 9, B(CH))
    b.set(6, 2, 9, B("lantern"))
    b.bed(4, 1, 6, "south", "brown")
    b.set(8, 1, 8, B("barrel", facing="up"))
    b.lectern(8, 1, 6, "west")
    b.set(4, 1, 9, B("water_cauldron", level=3))
    b.set(5, 1, 9, B("candle", candles=3, lit=True))
    for x in range(0, 13):
        for z in range(1, 12):
            if b.get(x, 1, z) is None and rng.random() < 0.3:
                b.set(x, 1, z, B(rng.choice(["fern", "short_grass", "lily_of_the_valley"])))
    return b


def ground_washhouse():
    b = Build(f"{NAME}_washhouse", 11, 8, 11, seed=1847731)
    rng = b.rng
    piece_in(b, 5, 2, 0, "north")
    for x in range(0, 11):
        for z in range(1, 11):
            b.set(x, 0, z, B("dirt"))
            b.set(x, 1, z, B(rng.choice(["cobblestone", "mossy_cobblestone", "gravel"])))
    for x in range(3, 8):
        for z in range(4, 8):
            b.set(x, 1, z, B("water", level=0))
            b.set(x, 0, z, B(SBS))
    for (x, z) in ring(2, 3, 8, 8):
        b.set(x, 1, z, B(SBS))
        b.set(x, 2, z, slab(SB))
    for (x, z) in ((2, 3), (8, 3), (2, 8), (8, 8)):
        for y in range(2, 5):
            b.set(x, y, z, B(f"stripped_{TIMBER}_log", axis="y"))
    roof_gable(b, 2, 8, 3, 8, 5, ROOF, B("dark_oak_planks"), along="x", overhang=1, gable_overhang=1)
    b.set(1, 2, 2, B("barrel", facing="up"))
    b.set(9, 2, 9, B("water_cauldron", level=2))
    for x in (3, 7):
        b.set(x, 2, 9, B("spruce_fence"))
        b.set(x, 3, 9, B("spruce_fence"))
    for x in (4, 5, 6):
        b.set(x, 3, 9, B("spruce_fence"))
    for x in (4, 6):
        b.set(x, 2, 9, B("white_wool"))
    return b


def pools():
    rp = random.Random(1847741)
    mats = (("gravel", 3), ("cobblestone", 2), ("mossy_cobblestone", 2), ("dirt_path", 2))
    lower, upper = abbey_pieces()
    return {
        "start": [Piece(lower, 1, PROC)],
        "upper": [Piece(upper, 1, PROC)],
        "wings": [Piece(wing_refectory(), 2, PROC), Piece(wing_dormitory(), 2, PROC),
                  Piece(wing_infirmary(), 2, PROC)],
        "paths": [Piece(path_piece(f"{NAME}_path_a", 8, P_GROUNDS, rp, mats=mats), 3, PROC, "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_b", 13, P_GROUNDS, rp, mats=mats), 2, PROC, "terrain_matching"),
                  Empty(1)],
        "grounds": [Piece(ground_terraces(), 3, PROC), Piece(ground_apiary(), 2, PROC),
                    Piece(ground_cemetery(), 2, PROC), Piece(ground_belvedere(), 2, PROC),
                    Piece(ground_grotto(), 1, PROC), Piece(ground_washhouse(), 2, PROC)],
    }
