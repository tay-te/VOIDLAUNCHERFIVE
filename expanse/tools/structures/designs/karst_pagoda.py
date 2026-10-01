"""Temple of the Stone Forest - a karst temple complex of the jade karst (LANDMARK).

start  court   : a 47x47 limestone terrace three blocks high. A gate hall with
                 guardian statues crowns the front stair; behind it a pond garden
                 with a stone causeway and moon bridge, cherry and wisteria trees,
                 bamboo, stone lanterns and a pond pavilion; a bell frame and a drum;
                 raked-gravel and stupa gardens; and the five-storey jade-roofed
                 PAGODA (~38 high) on its own podium:
                   1 hall of incense (golden figure, cushions, lore lectern)
                   2 library of rain - the tall shelf wall hides the scroll room
                     (break the near-empty chiseled shelves: hidden chest, old sutra)
                   3 treasury of quiet (public chest), 4 open bell storey, 5 lantern room
halls  (east + west, rigid on the terrace): refectory / dormitory / bell-and-drum court
rear   (behind the pagoda)                : rock garden with the abbot's study / stupa yard
paths -> grounds (terrain-matching tracks off each hall): tea house, bamboo hermitage,
                 karst spire shrine (climb for a chest), rice paddies, koi pond, pottery kiln
approach (in front, via a path)           : paifang gate with lantern posts, further grounds
"""
import random

from arch import (bamboo_clump, blob, flared_roof, leaves, plinth, pond, ring, rock, stone_lantern, straight_stair,
                  tree, wide_stair)
from blocks import AIR, B
from builder import Build, item, slab, stairs, written_book
from furnish import chain_lamp, chair, long_table, rug, shelf_items, table
from lore import BOOKS
from pieces import Empty, Piece
from terrain_paths import connector, path_piece, piece_in

NAME = "karst_pagoda"
STRUCTURE = dict(biomes=["jade_karst"], spacing=52, separation=20, size=5, max_distance=76)
LOOT = "expanse:chests/karst_pagoda"
HIDDEN = "expanse:chests/karst_pagoda_hidden"
P_HALLS = f"expanse:{NAME}/halls"
P_REAR = f"expanse:{NAME}/rear"
P_PATHS = f"expanse:{NAME}/paths"
P_GROUNDS = f"expanse:{NAME}/grounds"
P_APPROACH_PATHS = f"expanse:{NAME}/approach_paths"
P_APPROACH = f"expanse:{NAME}/approach"
J_HALL = "expanse:karst_hall"
J_REAR = "expanse:karst_rear"

LB = "expanse:limestone_brick"          # stairs / slab / wall
LBS = "expanse:limestone_bricks"
LS = "expanse:limestone"
PL = "expanse:polished_limestone"
CL = "expanse:chiseled_limestone"
ML = "expanse:mossy_limestone"
TILE = "waxed_oxidized_cut_copper"     # jade roof tiles
COL = "expanse:stripped_redwood_log"
WALL = "calcite"
BEAM = "dark_oak_planks"
WALLS = (LBS, LBS, LBS, ML, LS)
PROC = "pagoda"


def book():
    d = BOOKS[NAME]
    return written_book(d["title"], d["author"], d["pages"])


def sutra():
    return written_book("The Oldest Sutra", "unknown hand", [
        "Before the pillars there was the sea, and the sea laid down the stone grain by grain. Be patient as "
        "the sea.",
        "What is hidden is not lost. What is shelved is not forgotten. The quiet reader finds the hollow case.",
        "Leave a book for each book you take."])


def col():
    return B(COL, axis="y")


def paving(rng, moss=0.12):
    def f(x, z):
        if (x * 7 + z * 3) % 11 == 0:
            return B(LBS)
        return B(ML) if rng.random() < moss else B(PL)
    return f


def balustrade(b, cells, y, rng, post_every=6):
    for i, (x, z) in enumerate(cells):
        if i % post_every == 0:
            b.set(x, y, z, B(CL))
            if i % (post_every * 2) == 0:
                b.set(x, y + 1, z, B("lantern"))
        else:
            b.set(x, y, z, B(f"{LB}_wall"))


def lion(b, x, y, z, facing):
    """A small guardian lion: plinth, body, head with a stair snout."""
    b.set(x, y, z, B(CL))
    b.set(x, y + 1, z, B("polished_andesite"))
    b.set(x, y + 2, z, stairs("polished_andesite", facing))
    b.set(x, y + 3, z, B("andesite_wall"))


def hall_box(b, x0, z0, x1, z1, fy, wall_h, rng, front=None, cols_every=4, windows=True, roof_y=None,
             overhang=2, closed=True):
    """Timber hall: red columns, white walls with glass bays, dark-oak lintel, ceiling and a jade roof.
    Floor at fy, walls fy+1..fy+wall_h, beam at fy+wall_h+1, roof above. Returns the roof top y."""
    top = fy + wall_h
    b.air(x0 + 1, fy + 1, z0 + 1, x1 - 1, top, z1 - 1)
    for (x, z) in ring(x0, z0, x1, z1):
        corner = x in (x0, x1) and z in (z0, z1)
        u = (x - x0) if z in (z0, z1) else (z - z0)
        is_col = corner or u % cols_every == 0
        for y in range(fy + 1, top + 1):
            if is_col:
                b.set(x, y, z, col())
            elif not closed:
                if y == fy + 1:
                    b.set(x, y, z, B("dark_oak_fence"))
                elif y == top:
                    b.set(x, y, z, B(BEAM))
            elif y == top:
                b.set(x, y, z, B(BEAM))
            elif windows and fy + 2 <= y <= top - 1 and u % cols_every in (1, cols_every - 1) and \
                    (u % cols_every) and wall_h >= 4:
                b.set(x, y, z, B(WALL))
            elif windows and fy + 2 <= y <= top - 1 and wall_h >= 4:
                b.set(x, y, z, B("glass_pane"))
            else:
                b.set(x, y, z, B(WALL))
    b.fill(x0, top + 1, z0, x1, top + 1, z1, B(BEAM))
    return flared_roof(b, x0, z0, x1, z1, roof_y or top + 2, TILE, overhang=overhang, stop=None)


# ------------------------------------------------------------------ the pagoda
CX, CZ = 23, 33
TIERS = [(7, 5), (6, 10), (5, 15), (4, 20), (3, 25)]      # (half width, floor y)


def tier_cols(hw):
    s = {-hw, hw, 0}
    if hw >= 5:
        s |= {-(hw + 1) // 2, (hw + 1) // 2}
    return s


def pagoda(b, rng):
    # floors first (each covers the previous tier's beam ring)
    for i, (hw, fy) in enumerate(TIERS):
        b.fill(CX - hw, fy, CZ - hw, CX + hw, fy, CZ + hw, B(BEAM) if i else B("dark_oak_planks"))
    for i, (hw, fy) in enumerate(TIERS):
        x0, x1, z0, z1 = CX - hw, CX + hw, CZ - hw, CZ + hw
        b.air(x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 4, z1 - 1)
        cols = tier_cols(hw)
        bell_storey = i == 3
        for (x, z) in ring(x0, z0, x1, z1):
            u = x - CX if z in (z0, z1) else z - CZ
            is_col = u in cols
            for y in range(fy + 1, fy + 5):
                if is_col:
                    st = col()
                elif y == fy + 4:
                    st = B(BEAM)
                elif bell_storey:
                    st = B("dark_oak_fence") if y == fy + 1 else AIR
                elif y == fy + 1:
                    st = B(WALL)
                else:
                    st = B("glass_pane") if abs(u) % 2 == 1 or hw <= 4 else B(WALL)
                b.set(x, y, z, st)
            if bell_storey and not is_col:
                b.set(x, fy + 3, z, stairs("dark_oak", "north" if z == z0 else "south" if z == z1 else
                                           "west" if x == x0 else "east", "top"))
        b.fill(x0, fy + 5, z0, x1, fy + 5, z1, B(BEAM))
        last = i == len(TIERS) - 1
        flared_roof(b, x0, z0, x1, z1, fy + 5, TILE, overhang=2, stop=None if last else 1,
                    cap=B("waxed_oxidized_cut_copper"))
    # front portal of the ground storey
    hw, fy = TIERS[0]
    for x in (CX - 1, CX, CX + 1):
        b.air(x, fy + 1, CZ - hw, x, fy + 3, CZ - hw)
    b.set(CX - 1, fy + 3, CZ - hw, stairs("dark_oak", "east", "top"))
    b.set(CX + 1, fy + 3, CZ - hw, stairs("dark_oak", "west", "top"))
    # spire
    b.set(CX, 35, CZ, B("waxed_oxidized_copper"))
    b.set(CX, 36, CZ, B("waxed_oxidized_copper_chain", axis="y"))
    b.set(CX, 37, CZ, B("waxed_oxidized_copper_chain", axis="y"))
    b.set(CX, 38, CZ, B("waxed_oxidized_lightning_rod", facing="up"))

    # --- storey 1: hall of incense
    b.fill(20, 6, 37, 26, 6, 39, B(PL))
    for x in range(20, 27):
        b.set(x, 6, 36, slab(LB))
    for (x, y) in ((23, 7), (23, 8), (22, 7), (24, 7)):
        b.set(x, y, 38, B("honeycomb_block"))
    b.set(23, 9, 38, B("gold_block"))
    b.set(22, 8, 38, stairs("smooth_sandstone", "east", "top"))
    b.set(24, 8, 38, stairs("smooth_sandstone", "west", "top"))
    for x in (21, 25):
        b.set(x, 7, 37, B("yellow_candle", candles=4, lit=True))
    b.pot(20, 7, 38, "north", ("archer_pottery_sherd", "brick", "flow_pottery_sherd", "brick"))
    b.pot(26, 7, 38, "north", ("brick", "heart_pottery_sherd", "brick", "scrape_pottery_sherd"))
    b.set(23, 6, 35, B("flower_pot"))
    b.pot(23, 6, 34, "north", ("brick", "brick", "brick", "brick"))
    for x in (22, 24):
        b.set(x, 6, 34, B("white_candle", candles=3, lit=True))
    for (x, z) in ((19, 29), (27, 29), (19, 37), (27, 37)):
        for y in range(6, 10):
            b.set(x, y, z, col())
    for x in (21, 23, 25):
        for z in (30, 32):
            b.cushion(x, 6, z, "green")
    b.lectern(21, 6, 28, "north", book())
    b.banner(17, 8, 31, "green", [("border", "yellow"), ("circle", "lime")], wall_facing="east")
    b.banner(29, 8, 31, "green", [("border", "yellow"), ("flower", "lime")], wall_facing="west")
    b.banner(17, 8, 35, "red", [("border", "yellow"), ("rhombus", "yellow")], wall_facing="east")
    b.banner(29, 8, 35, "red", [("border", "yellow"), ("rhombus", "yellow")], wall_facing="west")
    chain_lamp(b, 23, 10, 31, 1)
    b.set(28, 6, 28, B("potted_bamboo"))
    b.set(17, 6, 28, B("potted_bamboo"))
    straight_stair(b, 18, 6, 35, "north", 5, "dark_oak", under=B(BEAM))
    for z in (32, 33, 34):
        b.set(19, 11, z, B("dark_oak_fence"))
    b.set(18, 11, 35, B("dark_oak_fence"))

    # --- storey 2: library of rain, the tall shelves and the scroll room behind them
    for x in range(18, 29):
        for y in range(11, 15):
            b.set(x, y, 36, B("bookshelf"))
    for y in (11, 12):
        b.bookshelf(26, y, 36, "north", slots=(4,) if y == 11 else ())
    b.air(18, 11, 37, 28, 14, 38)
    for x in range(19, 25):
        b.bookshelf(x, 11, 38, "north", slots=(0, 1, 2, 3, 4, 5) if x % 2 else (0, 2, 3, 5))
        b.bookshelf(x, 12, 38, "north", slots=(1, 3, 4) if x % 2 else (0, 1, 2, 3, 4, 5))
        if x % 2:
            b.set(x, 13, 38, B("white_candle", candles=rng.randint(1, 3), lit=False))
    b.lectern(25, 11, 38, "north", sutra())
    b.chest(27, 11, 38, "north", HIDDEN)
    b.pot(18, 11, 37, "east", ("brick", "danger_pottery_sherd", "brick", "brick"))
    b.set(28, 11, 37, B("cobweb"))
    for x in (20, 26):
        b.set(x, 11, 28, B("bookshelf"))
        b.set(x, 12, 28, B("bookshelf"))
    for x in (21, 22, 24, 25):
        b.bookshelf(x, 11, 28, "south", slots=tuple(rng.sample(range(6), rng.randint(2, 5))))
    table(b, 22, 11, 32, "dark_oak", top=slab("dark_oak", "top"))
    table(b, 24, 11, 32, "dark_oak", top=slab("dark_oak", "top"))
    b.set(23, 11, 32, slab("dark_oak", "top"))
    b.lectern(23, 11, 34, "north")
    b.set(22, 12, 32, B("candle", candles=2, lit=True))
    b.item_frame(24, 12, 32, "up", "writable_book")
    chair(b, 22, 11, 31, "dark_oak", "north")
    chair(b, 24, 11, 31, "dark_oak", "north")
    b.set(23, 11, 33, B("cauldron"))                   # catches the rain from the lantern well above
    chain_lamp(b, 21, 15, 34, 1)
    chain_lamp(b, 25, 15, 30, 1)
    straight_stair(b, 27, 11, 34, "north", 5, "dark_oak", under=B(BEAM))
    for z in (31, 32, 33):
        b.set(26, 16, z, B("dark_oak_fence"))
    b.set(27, 16, 34, B("dark_oak_fence"))

    # --- storey 3: treasury of quiet
    b.chest(23, 16, 37, "north", LOOT)
    b.armor_stand(21, 16, 37, yaw=0.0, equipment={"head": "chainmail_helmet", "chest": "chainmail_chestplate",
                                                    "mainhand": "iron_sword"})
    b.armor_stand(25, 16, 37, yaw=0.0, equipment={"head": "chainmail_helmet", "chest": "chainmail_chestplate",
                                                    "mainhand": "bow"})
    rug(b, 21, 31, 25, 35, 16, "green", "lime")
    b.cushion(23, 16, 33, "lime")
    b.item_frame(19, 18, 33, "east", "map")
    b.item_frame(19, 18, 35, "east", "paper")
    b.item_frame(27, 18, 35, "west", "emerald")
    b.set(19, 16, 37, B("potted_bamboo"))
    b.set(27, 16, 37, B("potted_azalea_bush"))
    for y in range(16, 20):
        b.set(19, y, 30, B(BEAM))
    for y in range(16, 21):
        b.set(20, y, 30, B("ladder", facing="east"))
    chain_lamp(b, 23, 20, 33, 1)

    # --- storey 4: open bell storey
    b.bell(23, 24, 33, "ceiling", "north")
    for y in range(21, 25):
        b.set(20, y, 31, B(BEAM))
    for y in range(21, 26):
        b.set(21, y, 31, B("ladder", facing="east"))
    b.set(25, 21, 35, B("dark_oak_slab", type="bottom"))

    # --- storey 5: the lantern room at the top
    b.set(23, 26, 33, B(CL))
    b.set(23, 27, 33, B("waxed_oxidized_copper_lantern"))
    b.item_frame(25, 27, 35, "north", "spyglass")
    b.set(25, 26, 31, B("white_candle", candles=3, lit=True))
    b.set(22, 26, 35, B("potted_bamboo"))


# ------------------------------------------------------------------ start: the court
def court():
    b = Build(f"{NAME}_court", 47, 42, 47, seed=1847601)
    rng = b.rng
    plinth(b, 1, 1, 45, 45, 3, rng, wall=WALLS, fill="dirt", floor=paving(rng), buttress=6, buttress_mat=LBS)
    # bridges toward the halls and the rear piece (the jigsaws sit above them)
    for z in range(28, 33):
        for x in (0, 46):
            for y in range(0, 4):
                b.set(x, y, z, B(LBS) if y < 3 else B(PL))
    for x in range(21, 26):
        for y in range(0, 4):
            b.set(x, y, 46, B(LBS) if y < 3 else B(PL))
    # front stair with cheek walls and lions
    for x in range(18, 29):
        b.set(x, 0, 0, B(rng.choice(["gravel", "expanse:polished_limestone", "gravel", "coarse_dirt"])))
    for x in (19, 27):
        for z in range(0, 4):
            for y in range(0, 4):
                b.set(x, y, z, B(LBS))
            b.set(x, 4, z, slab(LB) if z < 3 else B(CL))
        lion(b, x, 4, 1, "north")
        b.set(x, 4, 0, AIR)
    wide_stair(b, range(20, 27), 1, 1, "south", 3, LB)
    for x in (19, 27):
        b.set(x, 4, 2, slab(LB))
    # balustrade around the terrace (gaps for stair, hall bridges and rear)
    edge = [(x, z) for (x, z) in ring(1, 1, 45, 45)
            if not (z == 1 and 18 <= x <= 28) and not (x in (1, 45) and 28 <= z <= 32)
            and not (z == 45 and 21 <= x <= 25)]
    balustrade(b, edge, 4, rng)

    # gate hall over the stair head
    hall_box(b, 16, 5, 30, 9, 3, 5, rng, cols_every=4, windows=False)
    for z in range(6, 9):                               # open the central passage, enclose the side bays
        for x in range(21, 26):
            b.set(x, 4, z, AIR)
    for x in range(17, 30):
        for z in (5, 9):
            if 21 <= x <= 25:
                for y in range(4, 8):
                    b.set(x, y, z, AIR)
    for x in (20, 26):
        for z in range(6, 9):
            for y in range(4, 8):
                b.set(x, y, z, B(WALL) if y < 7 else B(BEAM))
    for x in range(21, 26):
        b.set(x, 8, 5, B(BEAM))
        b.set(x, 8, 9, B(BEAM))
    b.set(21, 7, 5, stairs("dark_oak", "east", "top"))
    b.set(25, 7, 5, stairs("dark_oak", "west", "top"))
    b.set(21, 7, 9, stairs("dark_oak", "east", "top"))
    b.set(25, 7, 9, stairs("dark_oak", "west", "top"))
    b.sign(23, 8, 7, "dark_oak", ["", "Temple of the", "Stone Forest", ""], hanging=True, rotation=0,
           attached=False)                              # hangs from the gate-hall ceiling
    for (x, eq, yaw) in ((18, {"head": "golden_helmet", "chest": "chainmail_chestplate", "mainhand": "iron_axe"}, 0.0),
                         (28, {"head": "golden_helmet", "chest": "chainmail_chestplate", "mainhand": "iron_sword"},
                          0.0)):
        b.set(x, 4, 7, B(PL))
        b.armor_stand(x, 5, 7, yaw=180.0, equipment=eq,
                      pose={"Head": (0.0, 0.0, 0.0), "Body": (0.0, 0.0, 0.0), "RightArm": (-30.0, 0.0, 10.0),
                            "LeftArm": (-10.0, 0.0, -10.0)})
        b.set(x, 4, 6, B("red_carpet"))
        b.set(x, 4, 8, B("red_carpet"))
    chain_lamp(b, 23, 9, 6, 1)

    # pond garden with causeway and moon bridge
    cells = [(x, z) for (x, z) in blob(23, 15.5, 17, 4.6, rng, rough=0.22) if 4 <= x <= 42 and 11 <= z <= 20]
    pond(b, cells, 3, rng, depth=2, lily=0.1)
    for (x, z) in cells:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, z + dz)
            if p not in cells and 2 <= p[0] <= 44 and 10 <= p[1] <= 22 and rng.random() < 0.7:
                b.set(p[0], 3, p[1], B(rng.choice([ML, LS, "mossy_cobblestone", "moss_block"])))
    for z in range(10, 22):
        for x in range(21, 26):
            if not (13 <= z <= 17):
                b.set(x, 3, z, B(PL) if x in (22, 23, 24) else B(LBS))
                b.set(x, 2, z, B(LBS))
                b.set(x, 1, z, B(LBS))
                b.set(x, 4, z, AIR)
    for x in range(21, 26):                             # moon bridge
        b.set(x, 4, 13, stairs(LB, "south"))
        b.set(x, 5, 14, stairs(LB, "south"))
        b.set(x, 4, 14, stairs(LB, "north", "top"))
        b.set(x, 5, 15, B(PL))
        b.set(x, 4, 15, AIR)
        b.set(x, 5, 16, stairs(LB, "north"))
        b.set(x, 4, 16, stairs(LB, "south", "top"))
        b.set(x, 4, 17, stairs(LB, "north"))
        for z in range(13, 18):
            b.set(x, 3, z, B("water", level=0))
            b.set(x, 2, z, B("water", level=0))
    for x in (21, 25):
        b.set(x, 5, 13, B(f"{LB}_wall"))
        for z in (14, 15, 16):
            b.set(x, 6, z, B(f"{LB}_wall"))
        b.set(x, 5, 17, B(f"{LB}_wall"))
    for (x, z) in ((20, 10), (26, 10), (20, 21), (26, 21)):
        stone_lantern(b, x, 4, z, "stone_brick")
    # pond pavilion on the east bank
    for x in range(34, 39):
        for z in range(13, 18):
            b.set(x, 3, z, B(PL))
            b.set(x, 2, z, B(LBS))
            b.set(x, 1, z, B(LBS))
            b.set(x, 4, z, AIR)
    for (x, z) in ((34, 13), (38, 13), (34, 17), (38, 17)):
        for y in range(4, 7):
            b.set(x, y, z, col())
    b.fill(34, 7, 13, 38, 7, 17, B(BEAM))
    flared_roof(b, 34, 13, 38, 17, 8, TILE, overhang=1, stop=None, lanterns=False)
    table(b, 36, 4, 15, "dark_oak", top=slab("dark_oak", "top"))
    b.set(36, 5, 15, B("flower_pot"))
    b.cushion(35, 4, 15, "white")
    b.cushion(37, 4, 15, "white")
    for x in (34, 38):
        b.set(x, 4, 15, AIR)
    for z in (14, 16):
        b.set(34, 4, z, B("dark_oak_fence"))
        b.set(38, 4, z, B("dark_oak_fence"))
    # garden trees, bamboo, rocks
    tree(b, 9, 4, 13, rng, "cherry_log", "cherry_leaves", height=5, crown=3, lean="east", petals="pink_petals")
    tree(b, 37, 4, 21, rng, "expanse:wisteria_log", "expanse:wisteria_leaves", height=5, crown=3, lean="west",
         blossoms="expanse:wisteria_blossoms")
    tree(b, 7, 4, 21, rng, "expanse:wisteria_log", "expanse:azure_wisteria_leaves", height=4, crown=2.5,
         blossoms="expanse:azure_wisteria_blossoms")
    bamboo_clump(b, 4, 5, 4, 2, rng)
    bamboo_clump(b, 42, 5, 4, 2, rng)
    bamboo_clump(b, 4, 43, 4, 2, rng, density=0.7)
    for (x, z) in ((12, 18), (31, 12), (40, 19)):
        rock(b, x, 4, z, rng, mats=(ML, LS, "mossy_cobblestone", "andesite"), size=2)
    # bell frame (east) and drum (west) beside the gate hall
    for (x, z) in ((37, 6), (41, 6)):
        for y in range(4, 8):
            b.set(x, y, z, col())
    for x in range(36, 43):
        b.set(x, 8, 6, B(BEAM))
    b.set(36, 8, 6, stairs(TILE, "east"))
    b.set(42, 8, 6, stairs(TILE, "west"))
    for x in range(37, 42):
        b.set(x, 9, 6, slab(TILE))
    b.bell(39, 7, 6, "ceiling", "south")
    b.set(39, 4, 6, B(PL))
    for x in (6, 9):
        b.set(x, 4, 6, B("dark_oak_fence"))
        b.set(x, 5, 6, B("dark_oak_fence"))
    b.set(7, 5, 6, B("expanse:stripped_redwood_wood", axis="x"))
    b.set(8, 5, 6, B("expanse:stripped_redwood_wood", axis="x"))
    b.set(7, 6, 6, B("red_carpet"))
    b.set(8, 6, 6, B("red_carpet"))
    b.set(6, 6, 6, B("lantern"))
    b.set(9, 6, 6, B("lantern"))
    # raked-gravel garden (west) and stupa garden (east) beside the pagoda
    for x in range(3, 13):
        for z in range(35, 44):
            b.set(x, 3, z, B("gravel") if (x + z) % 3 else B("light_gray_concrete_powder"))
    for (x, z) in ((6, 38), (10, 41), (8, 43)):
        rock(b, x, 4, z, rng, mats=(ML, "moss_block", "mossy_cobblestone"), size=2)
    for (x, z, h) in ((37, 37, 5), (41, 40, 4), (36, 42, 3)):
        stupa(b, x, 4, z, h, rng)
    stone_lantern(b, 34, 4, 26, "stone_brick")
    stone_lantern(b, 12, 4, 26, "stone_brick")

    # pagoda podium and the pagoda itself
    plinth(b, 14, 24, 32, 42, 5, rng, wall=(LBS, LBS, CL), fill=LBS, floor=B(PL), y0=4)
    wide_stair(b, range(20, 27), 4, 22, "south", 2, LB)
    pod = [(x, z) for (x, z) in ring(14, 24, 32, 42) if not (z == 24 and 19 <= x <= 27)]
    for i, (x, z) in enumerate(pod):
        b.set(x, 6, z, B(f"{LB}_wall") if i % 4 else B(CL))
    for (x, z) in ((19, 23), (27, 23)):
        stone_lantern(b, x, 4, z, "stone_brick", tall=True)
    pagoda(b, rng)
    # jigsaws: halls left/right, rear, the approach in front
    b.jigsaw(46, 4, 30, "east", name="minecraft:empty", target=J_HALL, pool=P_HALLS, final="minecraft:air")
    b.jigsaw(0, 4, 30, "west", name="minecraft:empty", target=J_HALL, pool=P_HALLS, final="minecraft:air")
    b.jigsaw(23, 4, 46, "south", name="minecraft:empty", target=J_REAR, pool=P_REAR, final="minecraft:air")
    connector(b, 23, 1, 0, "north", P_APPROACH_PATHS)
    return b


def stupa(b, x, y, z, h, rng, mat="calcite"):
    """Stepped white stupa with a jade finial."""
    b.set(x, y, z, B("polished_andesite"))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            b.set(x + dx, y, z + dz, B("polished_andesite") if dx or dz else B(mat))
    for k in range(1, h):
        b.set(x, y + k, z, B(mat) if k < h - 1 else B("quartz_pillar", axis="y"))
    for dx, dz, f in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        b.set(x + dx, y + 1, z + dz, stairs("diorite", f))
    b.set(x, y + h, z, B("waxed_oxidized_copper_chain", axis="y"))
    b.set(x, y + h + 1, z, B("waxed_oxidized_lightning_rod", facing="up"))


# ------------------------------------------------------------------ halls (east / west of the court)
def hall_base(name, seed):
    b = Build(name, 19, 19, 27, seed=seed)
    rng = b.rng
    plinth(b, 0, 0, 17, 26, 3, rng, wall=WALLS, fill="dirt", floor=paving(rng), buttress=6, buttress_mat=LBS)
    edge = [(x, z) for (x, z) in ring(0, 0, 17, 26) if not (x == 0 and 11 <= z <= 15)
            and not (x == 17 and 11 <= z <= 15)]
    balustrade(b, edge, 4, rng)
    for z in range(11, 16):
        b.set(18, 0, z, B("gravel"))
        for y in range(1, 4):
            b.set(18, y, z, AIR)
    for z in range(12, 15):
        straight_stair(b, 17, 1, z, "west", 3, LB)
    b.jigsaw(0, 4, 13, "west", name=J_HALL, target="minecraft:empty", pool="minecraft:empty", final="minecraft:air")
    connector(b, 18, 1, 13, "east", P_PATHS)
    return b, rng


def hall_refectory():
    b, rng = hall_base(f"{NAME}_hall_refectory", 1847611)
    b.fill(2, 4, 3, 13, 4, 23, B("spruce_planks"))
    hall_box(b, 2, 3, 13, 23, 4, 4, rng)
    for z in (12, 13, 14):
        b.air(2, 5, z, 2, 7, z)
    b.door(13, 5, 13, "spruce", "east")
    for (x, z) in ((6, 6), (9, 6)):
        long_table(b, [(x, zz) for zz in range(z, z + 9)], 5, "spruce", cloth=None)
        for zz in range(z + 1, z + 8):
            chair(b, x - 1, 5, zz, "spruce", "west")
            chair(b, x + 1, 5, zz, "spruce", "east")
            if zz % 2:
                b.set(x, 6, zz, B("flower_pot") if zz == z + 3 else B("white_carpet"))
    for x in range(3, 13):                              # kitchen behind a half wall
        if x not in (7, 8):
            b.set(x, 5, 17, B("spruce_fence"))
    b.set(3, 5, 22, B("smoker", facing="east"))
    b.set(3, 5, 21, B("smoker", facing="east"))
    b.set(3, 5, 20, B("furnace", facing="east"))
    b.set(3, 5, 19, B("water_cauldron", level=3))
    b.chest(12, 5, 22, "west", LOOT)
    for (x, z) in ((12, 21), (12, 20), (11, 22), (12, 19)):
        b.barrel(x, 5, z, "up")
    b.set(12, 6, 21, B("hay_block", axis="y"))
    b.set(12, 6, 20, B("pumpkin"))
    b.shelf(3, 7, 21, "spruce", "east", shelf_items(rng, "kitchen"))
    b.shelf(3, 7, 22, "spruce", "east", shelf_items(rng, "kitchen"))
    b.campfire(7, 5, 21, lit=True)
    b.set(7, 6, 21, AIR)
    b.set(8, 5, 21, B("water_cauldron", level=1))
    for z in (8, 13, 19):
        chain_lamp(b, 7, 9, z, 1)
    b.item_frame(4, 7, 23, "north", "bowl")
    b.item_frame(6, 7, 23, "north", "cooked_salmon")
    return b


def hall_dormitory():
    b, rng = hall_base(f"{NAME}_hall_dormitory", 1847613)
    b.fill(2, 4, 3, 13, 4, 23, B("bamboo_planks"))
    hall_box(b, 2, 3, 13, 23, 4, 4, rng)
    for z in (12, 13, 14):
        b.air(2, 5, z, 2, 7, z)
    b.door(13, 5, 13, "spruce", "east")
    cols = ["white", "light_gray", "white", "lime", "white", "light_gray"]
    i = 0
    for z in (5, 8, 17, 20):
        b.bed(4, 5, z, "west", cols[i % 6])
        b.bed(11, 5, z, "east", cols[(i + 1) % 6])
        b.barrel(3, 5, z + 1, "east")
        b.barrel(12, 5, z + 1, "west")
        b.set(3, 6, z + 1, B("white_candle", candles=1, lit=False))
        i += 2
    rug(b, 6, 10, 9, 16, 5, "green", "lime")
    b.cushion(7, 5, 12, "green")
    b.cushion(8, 5, 14, "green")
    table(b, 7, 5, 13, "spruce", top=slab("spruce", "top"))
    b.set(8, 5, 13, slab("spruce", "top"))
    b.set(7, 6, 13, B("potted_azalea_bush"))
    b.set(3, 5, 22, B("water_cauldron", level=3))
    b.chest(12, 5, 22, "west", LOOT)
    b.painting(3, 7, 12, "east", "wanderer")
    b.painting(12, 7, 14, "west", "plant")
    for z in (6, 13, 20):
        chain_lamp(b, 7, 9, z, 1)
    b.armor_stand(12, 5, 4, yaw=270.0, equipment={"head": "leather_helmet", "chest": "leather_chestplate"})
    return b


def hall_bell_court():
    b, rng = hall_base(f"{NAME}_hall_bellcourt", 1847615)
    # two-storey bell tower on the north half
    plinth(b, 4, 2, 12, 10, 4, rng, wall=(LBS, CL), fill=LBS, floor=B(PL), y0=4)
    for (x, z) in ((5, 3), (11, 3), (5, 9), (11, 9)):
        for y in range(5, 13):
            b.set(x, y, z, col())
    b.fill(5, 9, 3, 11, 9, 9, B(BEAM))
    b.air(6, 9, 4, 10, 9, 8)
    for x in range(6, 11):
        for z in (4, 8):
            b.set(x, 9, z, B(BEAM))
    for z in range(5, 8):
        b.set(6, 9, z, B(BEAM))
        b.set(10, 9, z, B(BEAM))
    for (x, z) in ring(5, 3, 11, 9):
        if b.get(x, 10, z) is None:
            b.set(x, 10, z, B("dark_oak_fence"))
    b.fill(5, 13, 3, 11, 13, 9, B(BEAM))
    flared_roof(b, 5, 3, 11, 9, 14, TILE, overhang=2, stop=None)
    b.bell(8, 12, 6, "ceiling", "south")
    straight_stair(b, 7, 5, 13, "north", 4, "dark_oak", under=B(BEAM))
    for y in range(5, 9):
        b.set(7, y, 9, AIR)
    # drum pavilion and stele on the south half
    for (x, z) in ((5, 16), (10, 16), (5, 21), (10, 21)):
        for y in range(4, 8):
            b.set(x, y, z, col())
    b.fill(5, 8, 16, 10, 8, 21, B(BEAM))
    flared_roof(b, 5, 16, 10, 21, 9, TILE, overhang=1, stop=None, lanterns=False)
    for (x, z) in ((7, 18), (8, 18), (7, 19), (8, 19)):
        b.set(x, 4, z, B("expanse:stripped_redwood_wood", axis="y"))
        b.set(x, 5, z, B("brown_carpet"))
    b.set(13, 4, 18, B(CL))
    b.set(13, 5, 18, B(CL))
    b.set(13, 6, 18, B(CL))
    b.sign(13, 5, 17, "dark_oak", ["The bell wakes", "the valley;", "the drum keeps", "the hours."],
           wall_facing="north")
    b.chest(15, 4, 22, "west", LOOT)
    b.barrel(15, 4, 21, "up")
    stone_lantern(b, 2, 4, 2, "stone_brick")
    stone_lantern(b, 2, 4, 24, "stone_brick")
    tree(b, 14, 4, 4, rng, "cherry_log", "cherry_leaves", height=4, crown=2.5, petals="pink_petals")
    return b


# ------------------------------------------------------------------ rear (behind the pagoda)
def rear_base(name, seed):
    b = Build(name, 31, 16, 15, seed=seed)
    rng = b.rng
    plinth(b, 0, 0, 30, 13, 3, rng, wall=WALLS, fill="dirt", floor=paving(rng), buttress=6, buttress_mat=LBS)
    edge = [(x, z) for (x, z) in ring(0, 0, 30, 13) if not (z == 0 and 13 <= x <= 17)]
    balustrade(b, edge, 4, rng)
    b.jigsaw(15, 4, 0, "north", name=J_REAR, target="minecraft:empty", pool="minecraft:empty",
             final="minecraft:air")
    return b, rng


def rear_rock_garden():
    b, rng = rear_base(f"{NAME}_rear_rockgarden", 1847621)
    for x in range(2, 18):
        for z in range(3, 12):
            b.set(x, 3, z, B("gravel") if (z % 2 or x % 5 == 0) else B("light_gray_concrete_powder"))
    for (x, z) in ((5, 6), (10, 9), (14, 5)):
        rock(b, x, 4, z, rng, mats=(ML, "moss_block", LS, "mossy_cobblestone"), size=3)
    # the abbot's study
    b.fill(20, 4, 3, 28, 4, 11, B(PL))
    hall_box(b, 20, 3, 28, 11, 4, 4, rng, cols_every=4)
    b.door(24, 5, 3, "spruce", "north")
    b.lectern(26, 5, 9, "west")
    b.bookshelf(27, 5, 4, "west", slots=(0, 1, 3, 5))
    b.bookshelf(27, 6, 4, "west", slots=(2, 4))
    b.set(27, 5, 5, B("bookshelf"))
    b.bed(21, 5, 9, "north", "green")
    b.chest(21, 5, 4, "east", LOOT)
    table(b, 24, 5, 8, "spruce", top=slab("spruce", "top"))
    b.set(24, 6, 8, B("potted_bamboo"))
    b.item_frame(21, 7, 7, "east", "compass")
    chain_lamp(b, 24, 9, 6, 1)
    tree(b, 18, 4, 11, rng, "cherry_log", "cherry_leaves", height=4, crown=2.5, petals="pink_petals")
    return b


def rear_stupa_yard():
    b, rng = rear_base(f"{NAME}_rear_stupas", 1847623)
    for x in range(2, 29):
        for z in range(3, 12):
            if rng.random() < 0.3:
                b.set(x, 3, z, B("moss_block"))
    for (x, z, h) in ((5, 7, 6), (11, 9, 4), (19, 9, 5), (25, 6, 7), (15, 5, 3)):
        stupa(b, x, 4, z, h, rng)
    for (x, z) in ((8, 4), (22, 4), (8, 11), (22, 11)):
        stone_lantern(b, x, 4, z, "stone_brick")
    for (x, z) in ((13, 11), (17, 11), (28, 10)):
        b.pot(x, 4, z, "north", ("brick", "brick", "brick", "brick"))
    b.set(15, 4, 8, B("white_candle", candles=4, lit=True))
    bamboo_clump(b, 28, 3, 4, 1, rng, density=0.9)
    bamboo_clump(b, 2, 11, 4, 1, rng, density=0.9)
    return b


# ------------------------------------------------------------------ grounds (satellites at path ends)
def ground_tea_house():
    b = Build(f"{NAME}_teahouse", 11, 10, 12, seed=1847631)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    for x in range(1, 10):
        for z in range(1, 11):
            b.set(x, 0, z, B("dirt"))
    plinth(b, 2, 2, 8, 8, 1, rng, wall=(LBS, ML), fill=LBS, floor=B("bamboo_planks"))
    for (x, z) in ((2, 2), (8, 2), (2, 8), (8, 8)):
        for y in range(2, 5):
            b.set(x, y, z, col())
    b.fill(2, 5, 2, 8, 5, 8, B(BEAM))
    b.air(3, 5, 3, 7, 5, 7)
    b.fill(3, 5, 3, 7, 5, 7, B("bamboo_planks"))
    flared_roof(b, 2, 2, 8, 8, 6, TILE, overhang=1, stop=None)
    b.set(5, 1, 1, stairs(LB, "south"))
    table(b, 5, 2, 5, "bamboo", top=slab("bamboo", "top"))
    b.set(5, 3, 5, B("flower_pot"))
    for (x, z) in ((4, 5), (6, 5), (5, 6), (5, 4)):
        b.cushion(x, 2, z, rng.choice(["white", "green", "lime"]))
    b.campfire(3, 2, 7, lit=True)
    b.set(4, 2, 7, B("water_cauldron", level=2))
    b.shelf(7, 3, 7, "bamboo", "west", shelf_items(rng, "kitchen"))
    b.set(7, 2, 7, B("barrel", facing="up"))
    for x in (3, 4, 6, 7):
        b.set(x, 2, 2, B("bamboo_fence"))
    cells = [(x, z) for (x, z) in blob(5, 10, 3, 1.3, rng) if 1 <= x <= 9 and 9 <= z <= 11]
    pond(b, cells, 0, rng, depth=1, lily=0.3)
    stone_lantern(b, 9, 1, 2, "stone_brick")
    bamboo_clump(b, 1, 9, 1, 1, rng, density=0.8)
    return b


def ground_hermitage():
    b = Build(f"{NAME}_hermitage", 13, 12, 14, seed=1847633)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    for x in range(0, 13):
        for z in range(1, 14):
            b.set(x, 0, z, B("podzol") if rng.random() < 0.6 else B("coarse_dirt"))
    for z in range(1, 6):
        b.set(6, 0, z, B("gravel"))
    # bamboo hut
    b.fill(4, 1, 6, 9, 1, 11, B("bamboo_mosaic"))
    for (x, z) in ring(4, 6, 9, 11):
        for y in range(2, 5):
            corner = x in (4, 9) and z in (6, 11)
            b.set(x, y, z, B("bamboo_block", axis="y") if corner or y == 4 else B("bamboo_planks"))
    b.air(5, 2, 7, 8, 3, 10)
    b.door(6, 2, 6, "bamboo", "north")
    b.set(4, 3, 8, B("bamboo_fence"))
    b.set(9, 3, 9, B("bamboo_fence"))
    for k, y in enumerate(range(5, 8)):
        for x in range(3 + k, 11 - k):
            b.set(x, y, 5 + k, stairs("bamboo_mosaic", "south"))
            b.set(x, y, 12 - k, stairs("bamboo_mosaic", "north"))
        for z in range(6 + k, 12 - k):
            b.set(3 + k, y, z, stairs("bamboo_mosaic", "east"))
            b.set(10 - k, y, z, stairs("bamboo_mosaic", "west"))
    b.fill(6, 8, 8, 7, 8, 9, slab("bamboo_mosaic"))
    b.fill(5, 5, 7, 8, 5, 10, B("bamboo_planks"))
    b.bed(8, 2, 10, "west", "green")
    b.barrel(5, 2, 10, "up")
    b.set(5, 3, 10, B("candle", candles=1, lit=False))
    b.lectern(5, 2, 7, "east")
    b.set(8, 2, 7, B("potted_bamboo"))
    chain_lamp(b, 6, 5, 8, 1)
    bamboo_clump(b, 1, 9, 1, 2, rng, density=0.75)
    bamboo_clump(b, 11, 4, 1, 2, rng, density=0.75)
    bamboo_clump(b, 11, 12, 1, 2, rng, density=0.75)
    bamboo_clump(b, 2, 3, 1, 1, rng, density=0.75)
    b.campfire(8, 1, 3, lit=False)
    b.set(7, 1, 3, stairs("spruce", "west"))
    b.set(9, 1, 3, stairs("spruce", "east"))
    return b


def ground_spire_shrine():
    b = Build(f"{NAME}_spire", 11, 26, 11, seed=1847635)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    import math
    for y in range(0, 20):
        r = 3.6 - y * 0.09 + (0.6 if y < 3 else 0) + 0.35 * math.sin(y * 1.3)
        for x in range(0, 11):
            for z in range(1, 11):
                d = math.hypot(x - 5, z - 6)
                if d <= r:
                    b.set(x, y, z, B(rng.choice([LS, LS, LS, ML, "stone", "andesite"])))
    # one straight ladder line up the front face, backed by a ridge of limestone where the rock recedes
    fronts = {}
    for y in range(1, 20):
        zs = [z for z in range(1, 11) if b.get(5, y, z) is not None]
        fronts[y] = min(zs) if zs else 6
    lz = max(1, min(fronts.values()) - 1)
    for y in range(1, 21):
        for z in range(lz + 1, fronts.get(y, 4)):
            b.set(5, y, z, B(rng.choice([LS, ML])))
        b.set(5, y, lz, B("ladder", facing="north"))
    top = 20
    plinth(b, 3, 4, 7, 8, top, rng, wall=(LBS,), fill=LS, floor=B(PL), y0=top - 1)
    for (x, z) in ((3, 4), (7, 4), (3, 8), (7, 8)):
        for y in range(top + 1, top + 3):
            b.set(x, y, z, col())
    b.fill(3, top + 3, 4, 7, top + 3, 8, B(BEAM))
    flared_roof(b, 3, 4, 7, 8, top + 4, TILE, overhang=1, stop=None, lanterns=False)
    b.chest(5, top + 1, 7, "north", LOOT)
    b.set(4, top + 1, 7, B("white_candle", candles=2, lit=True))
    b.set(6, top + 1, 7, B("potted_bamboo"))
    for (x, z) in ((4, 4), (6, 4)):
        b.set(x, top + 1, z, B(f"{LB}_wall"))
    for z in range(lz + 1, 4):                          # the ridge meets the platform edge
        b.set(5, top, z, B(PL))
    for x in range(0, 11):
        for z in range(0, 11):
            if b.get(x, 0, z) is None and rng.random() < 0.4:
                b.set(x, 0, z, B("moss_block"))
    bamboo_clump(b, 9, 2, 1, 1, rng)
    return b


def ground_rice_paddy():
    b = Build(f"{NAME}_paddy", 15, 4, 13, seed=1847637)
    rng = b.rng
    piece_in(b, 7, 1, 0, "north")
    for x in range(0, 15):
        for z in range(1, 13):
            edge = x in (0, 14) or z in (1, 12) or x == 7
            b.set(x, 0, z, B("mud") if not edge else B("packed_mud"))
            if edge:
                b.set(x, 1, z, B("mud_brick_slab", type="bottom") if x != 7 or z % 4 else AIR)
            elif z % 3 == 0:
                b.set(x, 0, z, B("water", level=0))
                if rng.random() < 0.15:
                    b.set(x, 1, z, B("lily_pad"))
            else:
                b.set(x, 0, z, B("farmland", moisture=7))
                b.set(x, 1, z, B("wheat", age=rng.randint(3, 7)))
    for x in range(1, 7):
        b.set(x, 0, 6, B("water", level=0))
        b.set(x, 1, 6, AIR)
    from furnish import scarecrow
    scarecrow(b, 11, 1, 7, 200.0)
    b.barrel(14, 1, 1, "up")
    b.set(13, 1, 1, B("composter", level=4))
    return b


def ground_koi_pond():
    b = Build(f"{NAME}_koipond", 15, 8, 13, seed=1847639)
    rng = b.rng
    piece_in(b, 7, 2, 0, "north")                      # one layer buried: the pond is two deep
    for x in range(0, 15):
        for z in range(1, 13):
            b.set(x, 0, z, B("dirt"))
            b.set(x, 1, z, B("grass_block"))
    cells = [(x, z) for (x, z) in blob(7, 7, 6, 4, rng) if 1 <= x <= 13 and 2 <= z <= 12]
    pond(b, cells, 1, rng, depth=2, lily=0.18, rim=(ML, LS, "mossy_cobblestone", "moss_block"))
    for z in (6, 7, 8):                                 # little moon bridge across the middle
        b.set(3, 2, z, stairs(LB, "east"))
        b.set(4, 3, z, stairs(LB, "east"))
        b.set(5, 3, z, B(PL))
        b.set(6, 4, z, slab(LB))
        b.set(7, 4, z, slab(LB))
        b.set(8, 4, z, slab(LB))
        b.set(9, 3, z, B(PL))
        b.set(10, 3, z, stairs(LB, "west"))
        b.set(11, 2, z, stairs(LB, "west"))
    for x in (6, 7, 8):
        b.set(x, 5, 6, B(f"{LB}_wall"))
        b.set(x, 5, 8, B(f"{LB}_wall"))
    stone_lantern(b, 2, 2, 3, "stone_brick")
    stone_lantern(b, 12, 2, 11, "stone_brick")
    for (x, z) in ((1, 11), (13, 2)):
        if b.get(x, 2, z) is None:
            b.tall_plant(x, 2, z, "tall_grass")
    tree(b, 1, 2, 6, rng, "cherry_log", "cherry_leaves", height=4, crown=2.5, lean="east", petals="pink_petals")
    return b


def ground_kiln():
    b = Build(f"{NAME}_kiln", 13, 9, 11, seed=1847641)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    for x in range(0, 13):
        for z in range(1, 11):
            b.set(x, 0, z, B(rng.choice(["coarse_dirt", "gravel", "packed_mud", "dirt"])))
    # dragon kiln: a long brick vault stepping up the slope
    for i, x in enumerate(range(2, 11)):
        h = 2 + i // 3
        for z in range(5, 9):
            for y in range(1, h + 1):
                edge = z in (5, 8) or y == h
                b.set(x, y, z, B("bricks") if edge else AIR)
    b.campfire(2, 1, 6, lit=True)
    b.campfire(2, 1, 7, lit=True)
    b.set(1, 1, 6, B("bricks"))
    b.set(1, 1, 7, B("bricks"))
    b.set(10, 5, 6, B("bricks"))
    b.campfire(10, 5, 7, lit=True)
    # drying racks with pots
    sherds = ["brick", "flow_pottery_sherd", "heart_pottery_sherd", "brick", "sheaf_pottery_sherd",
              "plenty_pottery_sherd", "brick"]
    for x in range(2, 11, 2):
        b.pot(x, 1, 2, "north", tuple(rng.choice(sherds) for _ in range(4)))
    b.set(12, 1, 3, B("clay"))
    b.set(12, 1, 4, B("clay"))
    b.set(11, 1, 4, B("water_cauldron", level=2))
    b.barrel(12, 1, 9, "up")
    b.set(0, 1, 9, B("stonecutter", facing="east"))
    return b


# ------------------------------------------------------------------ approach (in front of the gate)
def approach_paifang():
    b = Build(f"{NAME}_paifang", 15, 12, 7, seed=1847651)
    rng = b.rng
    piece_in(b, 7, 1, 0, "north")
    for x in range(0, 15):
        for z in range(0, 7):
            b.set(x, 0, z, B(PL) if 5 <= x <= 9 else B(rng.choice(["gravel", "coarse_dirt", "moss_block"])))
    for x in (2, 5, 9, 12):
        b.set(x, 1, 3, B(CL))
        for y in range(2, 8 if x in (5, 9) else 6):
            b.set(x, y, 3, col())
    for x in range(1, 14):
        b.set(x, 6 if not 5 <= x <= 9 else 8, 3, B(BEAM))
    flared_roof(b, 5, 3, 9, 3, 9, TILE, overhang=1, stop=None, lanterns=False)
    flared_roof(b, 1, 3, 4, 3, 7, TILE, overhang=1, stop=None, lanterns=False)
    flared_roof(b, 10, 3, 13, 3, 7, TILE, overhang=1, stop=None, lanterns=False)
    b.set(7, 7, 3, B(BEAM))
    b.sign(7, 7, 2, "dark_oak", ["", "Stone Forest", "Temple", ""], wall_facing="north")
    for x in (3, 11):
        b.set(x, 5, 3, B("lantern", hanging=True))
    stone_lantern(b, 3, 1, 6, "stone_brick")
    stone_lantern(b, 11, 1, 6, "stone_brick")
    connector(b, 7, 1, 6, "south", P_PATHS)
    return b


def pools():
    rp = random.Random(1847661)
    path_mats = (("expanse:polished_limestone", 3), ("gravel", 3), ("moss_block", 1), ("coarse_dirt", 1))
    return {
        "start": [Piece(court(), 1, PROC)],
        "halls": [Piece(hall_refectory(), 2, PROC), Piece(hall_dormitory(), 2, PROC),
                  Piece(hall_bell_court(), 2, PROC)],
        "rear": [Piece(rear_rock_garden(), 1, PROC), Piece(rear_stupa_yard(), 1, PROC)],
        "paths": [Piece(path_piece(f"{NAME}_path_a", 7, P_GROUNDS, rp, mats=path_mats), 3, PROC, "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_b", 12, P_GROUNDS, rp, stones="expanse:polished_limestone"), 2,
                        PROC, "terrain_matching"),
                  Empty(1)],
        "approach_paths": [Piece(path_piece(f"{NAME}_approach_path", 10, P_APPROACH, rp, mats=path_mats), 1, PROC,
                                 "terrain_matching")],
        "approach": [Piece(approach_paifang(), 1, PROC)],
        "grounds": [Piece(ground_tea_house(), 3, PROC), Piece(ground_hermitage(), 2, PROC),
                    Piece(ground_spire_shrine(), 2, PROC), Piece(ground_rice_paddy(), 2, PROC),
                    Piece(ground_koi_pond(), 2, PROC), Piece(ground_kiln(), 2, PROC)],
    }
