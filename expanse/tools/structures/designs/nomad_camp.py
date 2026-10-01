"""Camp of the Amber Road - a nomad camp of the amber steppe.

start  (two layouts)
  firepit : a beaten-earth plaza round a fire with a roasting spit, log benches and
            cushions, banner poles, a water trough and hay; the elder's covered wagon is
            parked at the edge - behind the red cloth under the driver's bench rides the
            caravan strongbox (hidden chest). Lore lectern under the awning.
  market  : the same fire circle beside a striped market awning with goods, the
            elder's wagon and a hitching rail
paths -> tents (terrain-matching, so every tent sits on its own patch of ground):
  family yurt (beds, chest, rugs, stove under the smoke hole), dyers' yurt (looms,
  dye barrels with supplies, cauldrons, wool bolts), story yurt (cushion circle,
  banners, note block drum), llama corral (llamas, hay, trough), drying racks
"""
import math
import random

import nbt
from arch import ring
from blocks import AIR, B
from builder import Build, item, slab, stairs, written_book
from common import circle_cells, ring_cells
from furnish import chain_lamp, chair, rug, shelf_items, table
from lore import BOOKS
from pieces import Empty, Piece
from terrain_paths import connector, path_piece, piece_in

NAME = "nomad_camp"
STRUCTURE = dict(biomes=["amber_steppe"], spacing=26, separation=9, size=2, max_distance=48)
LOOT = "expanse:chests/nomad_camp"
HIDDEN = "expanse:chests/nomad_camp_hidden"
SUPPLIES = "expanse:chests/nomad_camp_supplies"
P_PATHS = f"expanse:{NAME}/paths"
P_TENTS = f"expanse:{NAME}/tents"
PROC = "nomad_camp"
WOOD = "acacia"
BANNERS = [
    ("red", [("stripe_bottom", "yellow"), ("triangles_bottom", "orange"), ("border", "brown")]),
    ("orange", [("rhombus", "red"), ("stripe_top", "brown"), ("curly_border", "yellow")]),
    ("yellow", [("half_horizontal_bottom", "red"), ("circle", "orange"), ("border", "brown")]),
    ("brown", [("small_stripes", "orange"), ("triangle_top", "yellow")]),
]


def book():
    d = BOOKS[NAME]
    return written_book(d["title"], d["author"], d["pages"])


def ground(b, rng, x0, z0, x1, z1, y=0):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            r = rng.random()
            b.set(x, y, z, B("coarse_dirt") if r < 0.45 else B("dirt_path") if r < 0.6 else B("grass_block"))


def banner_pole(b, x, y, z, rng, rot=0, h=4):
    for k in range(h):
        b.set(x, y + k, z, B(f"{WOOD}_fence"))
    col, pats = rng.choice(BANNERS)
    b.banner(x, y + h, z, col, pats, rotation=rot)


def roasting_spit(b, x, y, z):
    b.campfire(x, y, z, lit=True)
    for dx in (-1, 1):
        b.set(x + dx, y, z, B(f"{WOOD}_fence"))
        b.set(x + dx, y + 1, z, B(f"{WOOD}_fence"))
    b.set(x, y + 2, z, B("iron_chain", axis="x"))
    b.set(x - 1, y + 2, z, B(f"{WOOD}_fence"))
    b.set(x + 1, y + 2, z, B(f"{WOOD}_fence"))


def elder_wagon(b, rng, x0, z0, y):
    """Covered wagon 4 long (x) x 3 wide; driver's bench at the front (x0); behind the red cloth under
    the bench the strongbox. Wheels are open trapdoors on the axle ends."""
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 3):
            b.set(x, y + 1, z, B(f"{WOOD}_planks") if x > x0 else B(f"{WOOD}_slab", type="top"))
    for x in (x0 + 1, x0 + 4):
        b.set(x, y, z0, B(f"stripped_{WOOD}_log", axis="z"))
        b.set(x, y, z0 + 2, B(f"stripped_{WOOD}_log", axis="z"))
        b.set(x, y, z0 + 1, B(f"stripped_{WOOD}_log", axis="z"))
        b.set(x, y, z0 - 1, B("dark_oak_trapdoor", facing="north", half="bottom", open=True))
        b.set(x, y, z0 + 3, B("dark_oak_trapdoor", facing="south", half="bottom", open=True))
    # canvas hoops over the bed (two high, so the strongbox under them can still be opened)
    for x in range(x0 + 1, x0 + 5):
        for z in (z0, z0 + 2):
            b.set(x, y + 2, z, B("white_wool"))
            b.set(x, y + 3, z, B("white_wool") if x % 2 else B("light_gray_wool"))
        for z in (z0, z0 + 1, z0 + 2):
            b.set(x, y + 4, z, B("white_wool") if z != z0 + 1 or x % 2 else B("light_gray_wool"))
    b.set(x0 + 4, y + 2, z0 + 1, B("white_wool"))
    b.set(x0 + 4, y + 3, z0 + 1, B("white_wool"))
    # driver's bench and the strongbox behind the red cloth
    b.set(x0 + 1, y + 2, z0 + 1, B("red_wool"))
    b.chest(x0 + 2, y + 2, z0 + 1, "west", HIDDEN)
    b.set(x0 + 3, y + 2, z0 + 1, B("barrel", facing="up"))
    b.set(x0, y + 2, z0 + 1, stairs(WOOD, "east"))
    b.set(x0, y + 2, z0, B(f"{WOOD}_fence"))
    b.set(x0, y + 2, z0 + 2, B(f"{WOOD}_fence"))
    b.set(x0 - 1, y + 1, z0 + 1, B(f"{WOOD}_fence"))
    b.set(x0 - 2, y + 1, z0 + 1, B(f"{WOOD}_fence"))
    b.set(x0 + 4, y + 2, z0 + 1, B("white_wool"))


def start_firepit():
    b = Build(f"{NAME}_firepit", 21, 9, 21, seed=1848201)
    rng = b.rng
    cx, cz = 10, 10
    for (x, z) in circle_cells(cx, cz, 9.6):
        b.set(x, 0, z, B("coarse_dirt") if rng.random() < 0.5 else B("dirt_path") if rng.random() < 0.5 else
              B("grass_block"))
    for (x, z) in ring_cells(cx, cz, 1.6):
        b.set(x, 1, z, B("mud_brick_slab", type="bottom") if (x + z) % 2 else B("cobblestone_slab", type="bottom"))
    roasting_spit(b, cx, 1, cz)
    for (x, z, ax) in ((cx - 4, cz - 1, "z"), (cx - 4, cz + 1, "z"), (cx + 4, cz - 1, "z"), (cx + 4, cz + 1, "z"),
                       (cx - 1, cz + 4, "x"), (cx + 1, cz + 4, "x")):
        b.set(x, 1, z, B(f"stripped_{WOOD}_log", axis=ax))
    for (x, z, col) in ((cx - 4, cz, "red"), (cx + 4, cz, "orange"), (cx, cz + 4, "yellow")):
        b.set(x, 1, z, B(f"{WOOD}_slab", type="bottom"))
        b.cushion(x, 2, z, col, support_top=0.5)
    for (x, z, r) in ((cx - 6, cz - 6, 2), (cx + 6, cz - 6, 14), (cx + 7, cz + 6, 10)):
        banner_pole(b, x, 1, z, rng, rot=r)
    # awning with the lore lectern and the elder's wagon
    for (x, z) in ((cx - 7, cz + 4), (cx - 3, cz + 4), (cx - 7, cz + 8), (cx - 3, cz + 8)):
        for y in range(1, 4):
            b.set(x, y, z, B(f"{WOOD}_fence"))
    for x in range(cx - 8, cx - 1):
        for z in range(cz + 4, cz + 9):
            b.set(x, 4, z, B("orange_wool") if x % 2 else B("yellow_wool"))
    b.lectern(cx - 5, 1, cz + 7, "north", book())
    rug(b, cx - 6, cz + 5, cx - 4, cz + 6, 1, "red", "orange")
    b.set(cx - 7, 1, cz + 7, B("barrel", facing="up"))
    elder_wagon(b, rng, cx + 3, cz - 8, 0)
    b.set(cx - 7, 1, cz - 2, B("hay_block", axis="y"))
    b.set(cx - 7, 1, cz - 3, B("hay_block", axis="x"))
    b.set(cx - 7, 2, cz - 2, B("hay_block", axis="z"))
    b.set(cx + 7, 1, cz + 1, B("water_cauldron", level=3))
    b.set(cx + 7, 1, cz + 2, B("water_cauldron", level=2))
    b.chest(cx - 5, 1, cz - 7, "south", LOOT)
    for (x, z) in circle_cells(cx, cz, 9.6):
        if b.get(x, 1, z) is None and rng.random() < 0.12 and (x - cx) ** 2 + (z - cz) ** 2 > 16:
            b.set(x, 1, z, B("short_grass"))
    connector(b, cx, 1, 0, "north", P_PATHS)
    connector(b, 0, 1, cz, "west", P_PATHS)
    connector(b, 20, 1, cz, "east", P_PATHS)
    connector(b, cx, 1, 20, "south", P_PATHS)
    for (x, z) in ((cx, 0), (0, cz), (20, cz), (cx, 20)):
        b.set(x, 0, z, B("dirt_path"))
    return b


def start_market():
    b = Build(f"{NAME}_market", 23, 9, 21, seed=1848203)
    rng = b.rng
    cx, cz = 11, 10
    ground(b, rng, 1, 1, 21, 19)
    for (x, z) in ring_cells(cx - 4, cz, 1.6):
        b.set(x, 1, z, B("cobblestone_slab", type="bottom"))
    roasting_spit(b, cx - 4, 1, cz)
    for (x, z, col) in ((cx - 7, cz, "red"), (cx - 4, cz + 3, "orange"), (cx - 4, cz - 3, "yellow")):
        b.cushion(x, 1, z, col)
    # striped market awning with stalls
    x0, x1, z0, z1 = cx + 1, cx + 9, cz - 4, cz + 4
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (x0, cz), (x1, cz)):
        for y in range(1, 4):
            b.set(x, y, z, B(f"{WOOD}_fence"))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            ridge = abs(z - cz)
            b.set(x, 5 - (ridge + 1) // 2 if ridge else 5, z, B("red_wool") if (x - x0) % 2 else B("white_wool"))
    for z in range(z0 + 1, z1):
        b.set(x0 + 2, 1, z, B(f"{WOOD}_slab", type="top") if z != cz else AIR)
        b.set(x1 - 2, 1, z, B(f"{WOOD}_slab", type="top") if z != cz else AIR)
    for (x, z) in ((x0 + 2, z0 + 1), (x0 + 2, z1 - 1), (x1 - 2, z0 + 2)):
        b.barrel(x, 2, z, "up", SUPPLIES if (x + z) % 2 else None)
    for (x, z, it) in ((x0 + 2, z0 + 2, "red_dye"), (x1 - 2, z1 - 1, "saddle"), (x1 - 2, z0 + 1, "lead")):
        b.item_frame(x, 2, z, "up", it)
    b.set(x1 - 2, 2, z1 - 2, B("red_wool"))
    b.set(x1 - 2, 2, z1 - 3, B("yellow_wool"))
    b.set(x0 + 2, 2, z1 - 2, B("flower_pot"))
    b.lectern(x0 + 4, 1, z1 - 1, "north", book())
    b.chest(x1 - 1, 1, z1 - 1, "west", LOOT)
    elder_wagon(b, rng, 2, 2, 0)
    for x in range(14, 21):                              # hitching rail
        b.set(x, 1, 18, B(f"{WOOD}_fence"))
    b.set(13, 1, 18, B(f"stripped_{WOOD}_log", axis="y"))
    b.set(21, 1, 18, B(f"stripped_{WOOD}_log", axis="y"))
    for (x, z, r) in ((2, 17, 4), (21, 2, 12)):
        banner_pole(b, x, 1, z, rng, rot=r)
    connector(b, cx, 1, 0, "north", P_PATHS)
    connector(b, 0, 1, cz + 4, "west", P_PATHS)
    connector(b, 22, 1, cz, "east", P_PATHS)
    for (x, z) in ((cx, 0), (0, cz + 4), (22, cz)):
        b.set(x, 0, z, B("dirt_path"))
    return b


# ------------------------------------------------------------------ tents
def yurt(b, rng, cx, cz, r, wall, band, roof_a, roof_b, door_to="north"):
    """Round felt yurt: lattice-felt wall with a coloured band, striped cone roof, smoke hole."""
    for (x, z) in circle_cells(cx, cz, r):
        b.set(x, 0, z, B(f"{WOOD}_planks") if (x - cx) ** 2 + (z - cz) ** 2 < (r - 0.8) ** 2 else B("coarse_dirt"))
    rim = ring_cells(cx, cz, r)
    for (x, z) in rim:
        for y in (1, 2, 3):
            b.set(x, y, z, B(band) if y == 2 else B(wall))
    for (x, z) in circle_cells(cx, cz, r - 0.6):
        if (x, z) not in rim:
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
    # door
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[door_to]
    door_cell = (cx + dx * int(round(r)), cz + dz * int(round(r)))
    for y in (1, 2):
        b.set(door_cell[0], y, door_cell[1], AIR)
    b.set(door_cell[0], 3, door_cell[1], B(f"{WOOD}_planks"))
    # cone roof with radial stripes and a crown ring around the smoke hole

    def roof(k, x, z):
        a = math.atan2(z - cz, x - cx)
        return B(roof_a) if int((a + math.pi) / (math.pi / 4)) % 2 == 0 else B(roof_b)
    # each course is two cells deep so it rests on the course below; the crown ring frames the smoke hole
    k = 0
    rr = r + 0.6
    while rr > 1.6:
        inner = set(circle_cells(cx, cz, rr - 1.7))
        for (x, z) in circle_cells(cx, cz, rr):
            if (x, z) not in inner:
                b.set(x, 4 + k, z, roof(k, x, z), clip=True)
        rr -= 1.0
        k += 1
    for (x, z) in circle_cells(cx, cz, 1.5):
        if (x, z) != (cx, cz):
            b.set(x, 4 + k, z, B(f"{WOOD}_fence"), clip=True)
            b.set(x, 4 + k - 1, z, B(f"{WOOD}_planks"), clip=True)
    b.set(cx, 4 + k - 1, cz, AIR)
    for (x, z) in ring_cells(cx, cz, r + 0.6):                # drop eave corners touching nothing
        if b.inb(x, 4, z) and not any(b.get(x + dx, 4 + dy, z + dz) not in (None, AIR)
                                      for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0))
                                      if b.inb(x + dx, 4 + dy, z + dz)):
            b.set(x, 4, z, None)
    return door_cell


def tent_family():
    b = Build(f"{NAME}_yurt_family", 11, 12, 12, seed=1848211)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    ground(b, rng, 0, 1, 10, 11)
    yurt(b, rng, 5, 6, 4.3, "white_wool", "red_wool", "red_wool", "white_wool", "north")
    rug(b, 3, 5, 7, 8, 1, "red", "orange", "yellow")
    b.campfire(5, 1, 6, lit=True)
    b.set(5, 2, 6, AIR)
    b.bed(3, 1, 9, "west", "red")
    b.bed(8, 1, 9, "east", "yellow")
    b.chest(5, 1, 9, "north", LOOT)
    b.set(2, 1, 5, B("barrel", facing="east"))
    b.set(8, 1, 4, B("loom", facing="west"))
    b.set(8, 1, 6, B("flower_pot"))
    b.cushion(4, 2, 7, "orange", support_top=0.0625)
    b.cushion(6, 2, 7, "yellow", support_top=0.0625)
    b.banner(2, 3, 6, "red", BANNERS[0][1], wall_facing="east")
    return b


def tent_dyers():
    b = Build(f"{NAME}_yurt_dyers", 13, 12, 13, seed=1848213)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    ground(b, rng, 0, 1, 12, 12)
    yurt(b, rng, 6, 7, 4.8, "light_gray_wool", "blue_wool", "cyan_wool", "light_blue_wool", "north")
    for (x, z, f) in ((3, 6, "east"), (9, 6, "west"), (4, 9, "north")):
        b.set(x, 1, z, B("loom", facing=f))
    for (x, z) in ((8, 9), (9, 8), (3, 8)):
        b.barrel(x, 1, z, "up", SUPPLIES)
    for (x, z, lvl) in ((5, 10, 3), (7, 10, 2)):
        b.set(x, 1, z, B("water_cauldron", level=lvl))
    for (x, z, col) in ((6, 5, "red"), (6, 6, "yellow"), (7, 5, "orange")):
        b.set(x, 1, z, B(f"{col}_wool"))
    b.set(6, 2, 5, B("cyan_wool"))
    # drying lines outside
    for x in (1, 11):
        for y in (1, 2, 3):
            b.set(x, y, 11, B(f"{WOOD}_fence"))
    for x in range(2, 11):
        b.set(x, 3, 11, B(f"{WOOD}_fence"))
        if x % 2:
            b.set(x, 2, 11, B(rng.choice(["red_wool", "yellow_wool", "blue_wool", "orange_wool"])))
    return b


def tent_story():
    b = Build(f"{NAME}_yurt_story", 13, 12, 13, seed=1848215)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    ground(b, rng, 0, 1, 12, 12)
    yurt(b, rng, 6, 7, 4.8, "white_wool", "orange_wool", "orange_wool", "yellow_wool", "north")
    rug(b, 4, 5, 8, 9, 1, "brown", "orange")
    for (x, z, col) in ((4, 6, "red"), (8, 6, "orange"), (4, 8, "yellow"), (8, 8, "red"), (6, 9, "brown")):
        b.cushion(x, 2, z, col, support_top=0.0625)
    b.set(6, 1, 7, B("brown_wool"))
    b.set(6, 2, 7, B("candle", candles=3, lit=True))
    b.set(3, 1, 9, B("barrel", facing="up"))
    b.banner(3, 3, 7, "orange", BANNERS[1][1], wall_facing="east")
    b.banner(9, 3, 7, "yellow", BANNERS[2][1], wall_facing="west")
    b.set(9, 1, 10, B("flower_pot"))
    return b


def tent_corral():
    b = Build(f"{NAME}_corral", 13, 6, 13, seed=1848217)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    ground(b, rng, 0, 1, 12, 12)
    for (x, z) in ring(1, 2, 11, 11):
        if not (z == 2 and x == 6):
            b.set(x, 1, z, B(f"{WOOD}_fence"))
    b.set(6, 1, 2, B(f"{WOOD}_fence_gate", facing="north", open=False, in_wall=False))
    for (x, z) in ((3, 9), (4, 10), (3, 10)):
        b.set(x, 1, z, B("hay_block", axis="y"))
    b.set(9, 1, 10, B("water_cauldron", level=3))
    b.set(10, 1, 10, B("water_cauldron", level=2))
    for (x, z, v, yaw) in ((5, 6, 0, 30.0), (8, 5, 2, 200.0), (7, 8, 1, 120.0)):
        b.mob(x, 1, z, "llama", yaw=yaw, extra={"Variant": nbt.Int(v), "Strength": nbt.Int(3),
                                                 "Tame": nbt.Byte(0), "Temper": nbt.Int(0)})
    for x in range(1, 12):
        for z in range(3, 11):
            if b.get(x, 1, z) is None and rng.random() < 0.2:
                b.set(x, 1, z, B("short_dry_grass"))
    return b


def tent_racks():
    b = Build(f"{NAME}_racks", 11, 6, 9, seed=1848219)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    ground(b, rng, 0, 1, 10, 8)
    for z in (3, 6):
        for x in (1, 5, 9):
            for y in (1, 2, 3):
                b.set(x, y, z, B(f"{WOOD}_fence"))
        for x in range(1, 10):
            b.set(x, 3, z, B(f"{WOOD}_fence"))
            if x not in (1, 5, 9) and rng.random() < 0.7:          # hides hung under the rail
                b.set(x, 2, z, B(rng.choice(["brown_wool", "brown_wool", "white_wool"])))
    b.set(2, 1, 8, B("barrel", facing="up"))
    b.set(8, 1, 8, B("hay_block", axis="x"))
    b.set(3, 1, 1, B("smoker", facing="north"))
    return b


def pools():
    rp = random.Random(1848221)
    mats = (("dirt_path", 4), ("coarse_dirt", 3), ("grass_block", 1))
    return {
        "start": [Piece(start_firepit(), 1, PROC), Piece(start_market(), 1, PROC)],
        "paths": [Piece(path_piece(f"{NAME}_path_a", 4, P_TENTS, rp, mats=mats), 3, PROC, "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_b", 7, P_TENTS, rp, mats=mats), 2, PROC, "terrain_matching"),
                  Empty(1)],
        "tents": [Piece(tent_family(), 3, PROC), Piece(tent_dyers(), 3, PROC), Piece(tent_story(), 2, PROC),
                  Piece(tent_corral(), 2, PROC), Piece(tent_racks(), 1, PROC)],
    }
