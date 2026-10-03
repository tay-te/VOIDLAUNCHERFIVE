"""Palm coast lighthouse on a rocky headland.

start  (two liveries: red-and-white stripes / black-and-white stripes with a slate cap)
  A round tower ~44 high on a rock-and-stone headland. A spiral stair winds round the
  central pillar past two landings to the watch room: cartography table, the MAP
  DRAWER chest (exploration maps to the nearest #expanse:on_lighthouse_maps - the karst
  temple or the sun temple), spyglass, charts. A ladder climbs to the gallery with its
  railing and the glass lantern room with the lamp, under a conical cap.
  The keeper's palm-wood cottage beside it: bedroom, kitchen, logbook lectern (lore);
  under the rug a trapdoor drops into the crawlspace with the spare sea lanterns and
  the hidden chest.
jetty  (rigid, off the headland) : plank jetty on posts, a moored boat, crab pots, lamp posts
paths -> shore (terrain-matching)  : net-drying racks, beached wreck, palm grove with
                                     a hammock, bonfire lookout
"""
import math
import random

from arch import ring, rock, tree
from blocks import AIR, B
from builder import Build, item, slab, stairs, written_book
from common import circle_cells, cone, ring_cells, spiral_floor_ok, spiral_stair
from furnish import chain_lamp, chair, lamp_post, rug, shelf_items, table, window
from kit import roof_gable
from lore import BOOKS
from pieces import Empty, Piece
from terrain_paths import connector, path_piece, piece_in

NAME = "lighthouse"
STRUCTURE = dict(biomes=["palm_coast"], spacing=36, separation=12, size=3, max_distance=56, sited=False)
LOOT = "expanse:chests/lighthouse"
MAPS = "expanse:chests/lighthouse_map"
HIDDEN = "expanse:chests/lighthouse_hidden"
P_JETTY = f"expanse:{NAME}/jetty"
P_PATHS = f"expanse:{NAME}/paths"
P_SHORE = f"expanse:{NAME}/shore"
J_JETTY = "expanse:lighthouse_jetty"
PROC = "lighthouse"
PALM = "expanse:palm"
CX, CZ = 12, 15
R = 4


def book():
    d = BOOKS[NAME]
    return written_book(d["title"], d["author"], d["pages"])


def headland(b, rng):
    for x in range(b.size[0]):
        for z in range(b.size[2]):
            d = math.hypot((x - CX) / 1.15, z - CZ)
            h = 3 - int(d / 4.5) + (1 if rng.random() < 0.25 else 0)
            if d > 13.5:
                continue
            for y in range(0, max(1, h)):
                b.set(x, y, z, B(rng.choice(["stone", "andesite", "stone", "cobblestone", "mossy_cobblestone",
                                             "gravel"])))
            top = max(1, h) - 1
            if d < 12 and rng.random() < 0.35 and b.get(x, top + 1, z) is None:
                b.set(x, top, z, B("grass_block") if rng.random() < 0.7 else B("coarse_dirt"))   # tufts need soil
                b.set(x, top + 1, z, B(rng.choice(["short_grass", "short_dry_grass", "short_grass"])))


def tower(b, rng, livery):
    a, c = livery["a"], livery["c"]
    top = 33
    for y in range(1, top + 1):
        band = a if ((y - 1) // 4) % 2 == 0 else c
        for (x, z) in ring_cells(CX, CZ, R + 0.4):
            b.set(x, y, z, B(band) if y > 3 else B("stone_bricks"))
        for (x, z) in circle_cells(CX, CZ, R - 0.6):
            if (x, z) not in ring_cells(CX, CZ, R + 0.4):
                b.set(x, y, z, AIR)
    for (x, z) in circle_cells(CX, CZ, R + 0.4):
        b.set(x, 0, z, B("stone_bricks"))
        b.set(x, 1, z, B("polished_andesite") if (x, z) not in ring_cells(CX, CZ, R + 0.4) else B("stone_bricks"))
    # plinth flare at the foot
    for (x, z) in ring_cells(CX, CZ, R + 1.4):
        for y in (1, 2):
            b.set(x, y, z, B("stone_bricks") if y == 1 else stairs("stone_brick", INWARD[_out(x, z)]))
    # spiral stair to the watch room, landings
    steps = spiral_stair(b, CX, CZ, 2, 27, livery["stair"], B("stone_bricks"),
                         underside=B(f"{livery['stair']}_planks"))
    for fy in (12, 20, 27):
        for (x, z) in circle_cells(CX, CZ, R - 0.6):
            if (x, z) in ring_cells(CX, CZ, R + 0.4) or (x, z) == (CX, CZ):
                continue
            if spiral_floor_ok(steps, x, z, fy) and b.get(x, fy, z) in (None, AIR):
                b.set(x, fy, z, B(f"{livery['stair']}_planks") if livery["stair"] != "stone_brick" else
                      B("spruce_planks"))
    # windows
    for (y, d) in ((6, "south"), (10, "east"), (16, "north"), (22, "west"), (29, "south"), (29, "north")):
        dx, dz = {"south": (0, R), "north": (0, -R), "east": (R, 0), "west": (-R, 0)}[d]
        b.set(CX + dx, y, CZ + dz, B("glass_pane"))
        b.set(CX + dx, y + 1, CZ + dz, B("glass_pane"))
    # door toward the cottage (east)
    for y in (2, 3):
        b.set(CX + R, y, CZ, AIR)
    b.door(CX + R, 2, CZ, "spruce", "east")
    for y in (2, 3):
        b.set(CX + R + 1, y, CZ, AIR)
    # watch room (floor 27): cartography, map drawer, spyglass, charts
    fy = 27
    b.set(CX - 3, fy + 1, CZ, B("cartography_table"))
    b.chest(CX - 3, fy + 1, CZ + 1, "east", MAPS)
    b.chest(CX - 3, fy + 1, CZ - 1, "east", LOOT)
    b.item_frame(CX, fy + 3, CZ + R - 1, "north", "spyglass")
    b.item_frame(CX - 1, fy + 3, CZ - R + 1, "south", "map")
    b.set(CX + 3, fy + 1, CZ, B("barrel", facing="west"))
    for y in range(fy + 1, top + 2):
        b.set(CX, y, CZ - 2, B("ladder", facing="south"))
    b.set(CX, fy + 1, CZ, B("stone_bricks"))
    for y in range(fy + 1, top + 1):                       # pilaster carrying the ladder
        b.set(CX, y, CZ - 3, B(a))
    # gallery and lantern room
    g = top + 1
    for (x, z) in circle_cells(CX, CZ, R + 2.4):
        b.set(x, g, z, slab("smooth_stone", "top") if (x, z) not in circle_cells(CX, CZ, R + 0.4) else
              B("smooth_stone"))
    b.set(CX, g, CZ - 2, B("ladder", facing="south"))
    for (x, z) in ring_cells(CX, CZ, R + 2.4):
        b.set(x, g + 1, z, B("iron_bars"))
    for (x, z) in ring_cells(CX, CZ, R + 1.4):
        b.set(x, g - 1, z, stairs("stone_brick", INWARD[_out(x, z)], "top"))
    for (x, z) in ring_cells(CX, CZ, 2.6):
        for y in range(g + 1, g + 5):
            b.set(x, y, z, B("glass") if (x + z) % 3 else B("iron_bars") if y in (g + 1, g + 4) else B("glass"))
    for (x, z) in circle_cells(CX, CZ, 1.6):
        if (x, z) not in ring_cells(CX, CZ, 2.6):
            for y in range(g + 1, g + 5):
                b.set(x, y, z, AIR)
    b.set(CX, g + 1, CZ, B("sea_lantern"))
    b.set(CX, g + 2, CZ, B("sea_lantern"))
    b.set(CX, g + 3, CZ, B("sea_lantern"))
    for (x, z) in ring_cells(CX, CZ, 2.6):
        if (x, z) == (CX, CZ - 2):
            for y in (g + 1, g + 2):
                b.set(x, y, z, AIR)
    # cap
    cone(b, CX, CZ, 3.6, g + 5, lambda k, x, z: B(livery["cap"]) if k % 2 == 0 else B(livery["cap2"]), step_r=0.9)
    for y in range(g + 5, g + 11):
        b.set(CX, y, CZ, B(livery["cap"]))
    b.set(CX, g + 11, CZ, B("lightning_rod", facing="up"))


INWARD = {"north": "south", "south": "north", "east": "west", "west": "east"}


def _out(x, z):
    dx, dz = x - CX, z - CZ
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def cottage(b, rng, mirrored=False):
    x0, x1, z0, z1 = 19, 27, 11, 19
    fy = 3
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(0, fy):
                edge = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
                b.set(x, y, z, B(rng.choice(["cobblestone", "stone", "mossy_cobblestone"])) if edge or y == 0
                      else AIR)
            b.set(x, fy, z, B(f"{PALM}_planks"))
    # walls: palm planks with stripped palm posts, white band
    for (x, z) in ring(x0, z0, x1, z1):
        post = (x in (x0, x1) and z in (z0, z1)) or (x - x0) % 4 == 0 and z in (z0, z1) or \
            (z - z0) % 4 == 0 and x in (x0, x1)
        for y in range(fy + 1, fy + 5):
            b.set(x, y, z, B("expanse:stripped_palm_log", axis="y") if post
                  else B("white_terracotta") if y == fy + 1 else B(f"{PALM}_planks"))
    b.air(x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 4, z1 - 1)
    roof_gable(b, x0, x1, z0, z1, fy + 5, "mangrove", lambda x, y, z: B(f"{PALM}_planks"), along="x", overhang=1,
               gable_overhang=1)
    b.fill(x0 + 1, fy + 5, z0 + 1, x1 - 1, fy + 5, z1 - 1, B(f"{PALM}_planks"))
    # partition: bedroom (east) | kitchen and keeper's room (west)
    for z in range(z0 + 1, z1):
        for y in range(fy + 1, fy + 5):
            b.set(24, y, z, B(f"{PALM}_planks"))
    for y in (fy + 1, fy + 2):
        b.set(24, y, 16, AIR)
    b.door(24, fy + 1, 16, f"{PALM}_door", "east")
    # door toward the tower and a path between
    for y in (fy + 1, fy + 2):
        b.set(x0, y, 15, AIR)
    b.door(x0, fy + 1, 15, f"{PALM}_door", "west")
    for x in range(CX + R + 1, x0):
        b.set(x, 2, 15, B("gravel") if x % 2 else B("cobblestone"))
        b.set(x, 3, 15, AIR)
        b.set(x, 4, 15, AIR)
    for z in (13, 17):
        window(b, x0, fy + 2, z, "west", 2, shutters=PALM)
        window(b, x1, fy + 2, z, "east", 2, shutters=PALM)
    for x in (21, 26):
        window(b, x, fy + 2, z0, "north", 2, box="potted_red_tulip", box_wood=PALM)
        window(b, x, fy + 2, z1, "south", 2)
    # keeper's room: logbook, table, stove, shelves
    b.lectern(20, fy + 1, 12, "south", book())
    table(b, 22, fy + 1, 13, "spruce", top=slab("spruce", "top"))
    chair(b, 22, fy + 1, 14, "spruce", "south")
    b.set(22, fy + 2, 13, B("candle", candles=1, lit=True))
    b.set(20, fy + 1, 18, B("smoker", facing="east"))
    b.set(21, fy + 1, 18, B("barrel", facing="north"))
    b.set(22, fy + 1, 18, B("water_cauldron", level=2))
    b.shelf(20, fy + 3, 18, "spruce", "north", shelf_items(rng, "fisher"))
    b.chest(23, fy + 1, 18, "north", LOOT)
    b.item_frame(23, fy + 3, 12, "south", "compass")
    chain_lamp(b, 22, fy + 5, 15, 1)
    # bedroom; the rug hides the crawlspace hatch
    b.bed(26, fy + 1, 12, "north", "blue")
    b.set(25, fy + 1, 12, B("spruce_trapdoor", facing="north", half="top", open=False))
    b.set(25, fy + 2, 12, B("candle", candles=2, lit=False))
    b.painting(26, fy + 3, 18, "north", "pond")
    rug(b, 25, 15, 26, 17, fy + 1, "light_blue", None)
    b.set(26, fy, 16, B("spruce_trapdoor", facing="south", half="top", open=False))
    # crawlspace under the floor: spare sea lanterns and the hidden chest
    b.set(25, 1, 16, B("sea_lantern"))
    b.set(25, 1, 17, B("sea_lantern"))
    b.set(25, 2, 17, B("sea_lantern"))
    b.chest(27, 1, 17, "west", HIDDEN)
    b.set(27, 1, 15, B("barrel", facing="up"))
    b.set(26, 1, 18, B("cobweb"))
    # chimney
    for y in range(fy + 1, fy + 10):
        b.set(x1 + 1, y, 14, B("bricks") if y > fy + 4 else B("cobblestone"))
    b.campfire(x1 + 1, fy + 10, 14, lit=True)


def start(livery, name, seed):
    b = Build(f"{NAME}_{name}", 31, 47, 31, seed=seed)
    rng = b.rng
    headland(b, rng)
    tower(b, rng, livery)
    cottage(b, rng)
    for (x, z) in ((CX - 7, CZ + 3), (CX - 5, CZ - 6), (26, 24)):
        rock(b, x, 1, z, rng, mats=("stone", "andesite", "mossy_cobblestone"), size=2)
    tree(b, 5, 2, 24, rng, f"{PALM}_log", f"{PALM}_leaves", height=6, crown=2.2, lean="west", flat=0.4)
    tree(b, 27, 1, 6, rng, f"{PALM}_log", f"{PALM}_leaves", height=5, crown=2.0, lean="north", flat=0.4)
    lamp_post(b, 17, 2, 13, "spruce", 2)
    # the jetty leaves the headland on the west, paths to the shore north and south
    for x in range(0, 4):
        b.set(x, 1, CZ, B(f"{PALM}_planks"))
        for y in (2, 3, 4):
            b.set(x, y, CZ, AIR)
    b.jigsaw(0, 2, CZ, "west", target=J_JETTY, pool=P_JETTY)
    connector(b, CX, 1, 0, "north", P_PATHS)
    connector(b, 23, 1, 30, "south", P_PATHS)
    b.set(CX, 0, 0, B("gravel"))
    b.set(23, 0, 30, B("gravel"))
    return b


# ------------------------------------------------------------------ jetty
def jetty():
    b = Build(f"{NAME}_jetty", 18, 6, 7, seed=1848011)
    rng = b.rng
    # local x 17 is the land end (jigsaw), the jetty runs toward x=0
    for x in range(0, 18):
        for z in range(1, 6):
            b.set(x, 2, z, B(f"{PALM}_slab", type="top") if rng.random() < 0.94 or z in (1, 5) else AIR)
            if x < 17 and z in (1, 5):
                b.set(x, 3, z, B(f"{PALM}_fence"))           # continuous rail
                if x % 4 == 0:
                    for y in range(0, 2):
                        b.set(x, y, z, B("expanse:stripped_palm_log", axis="y"))
                    if x % 8 == 0:
                        b.set(x, 4, z, B("lantern"))
    for z in (2, 3, 4):
        b.set(0, 3, z, B(f"{PALM}_fence"))
    b.jigsaw(17, 3, 3, "east", name=J_JETTY)
    b.boat(2, 1, 6, wood="spruce", yaw=90.0)
    b.set(5, 3, 2, B("barrel", facing="up"))
    b.set(6, 3, 2, B("barrel", facing="up"))
    b.set(12, 3, 4, B("barrel", facing="east"))
    b.set(0, 3, 3, B(f"{PALM}_fence"))
    b.set(0, 4, 3, B("lantern"))
    return b


# ------------------------------------------------------------------ shore satellites
def shore_nets():
    b = Build(f"{NAME}_nets", 11, 6, 10, seed=1848021)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    for x in range(11):
        for z in range(1, 10):
            b.set(x, 0, z, B(rng.choice(["sand", "sand", "gravel"])))
    for x in (1, 5, 9):
        for y in range(1, 4):
            b.set(x, y, 5, B(f"{PALM}_fence"))
    for x in range(1, 10):
        b.set(x, 4, 5, B(f"{PALM}_fence"))
        if x not in (1, 5, 9) and rng.random() < 0.8:
            b.set(x, 3, 5, B("cobweb") if rng.random() < 0.3 else B("cobweb"))
    for (x, z) in ((2, 8), (8, 8), (3, 2)):
        b.set(x, 1, z, B("barrel", facing="up"))
    b.set(7, 1, 2, B("composter", level=3))
    return b


def shore_wreck():
    b = Build(f"{NAME}_wreck", 9, 8, 15, seed=1848023)
    rng = b.rng
    piece_in(b, 4, 1, 0, "north")
    for x in range(9):
        for z in range(1, 15):
            b.set(x, 0, z, B("sand"))
    # a beached hull, half buried, tilted
    for z in range(3, 14):
        w = 3 if 5 <= z <= 11 else 2 if z in (4, 12) else 1
        for x in range(4 - w, 5 + w):
            edge = abs(x - 4) == w
            if edge:
                b.set(x, 1, z, B("spruce_planks"))
                if rng.random() < 0.7 and x > 4:
                    b.set(x, 2, z, B("spruce_planks"))
            else:
                b.set(x, 1, z, B("sand") if rng.random() < 0.6 else B("spruce_slab", type="bottom"))
    for y in range(1, 6):
        b.set(4, y, 8, B("stripped_spruce_log", axis="y"))
    b.set(4, 6, 8, B("stripped_spruce_log", axis="x"))
    b.set(5, 6, 8, B("white_wool"))
    b.barrel(3, 2, 10, "up")
    b.set(5, 2, 5, B("spruce_trapdoor", facing="north", half="bottom", open=True))
    return b


def shore_palms():
    b = Build(f"{NAME}_palms", 13, 10, 12, seed=1848025)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    for x in range(13):
        for z in range(1, 12):
            b.set(x, 0, z, B("sand") if rng.random() < 0.7 else B("grass_block"))
    t1 = tree(b, 2, 1, 6, rng, f"{PALM}_log", f"{PALM}_leaves", height=6, crown=2.2, lean="east", flat=0.4)
    t2 = tree(b, 10, 1, 7, rng, f"{PALM}_log", f"{PALM}_leaves", height=6, crown=2.2, lean="west", flat=0.4)
    for x in range(4, 9):                                 # hammock slung between the trunks
        b.set(x, 2, 7 if x < 6 else 7, B("white_wool"))
    for x in range(3, 10):
        if b.get(x, 3, 7) is None and x in (3, 9):
            b.set(x, 3, 7, B(f"{PALM}_fence"))
    b.set(3, 2, 7, B(f"{PALM}_fence"))
    b.set(9, 2, 7, B(f"{PALM}_fence"))
    b.campfire(6, 1, 3, lit=False)
    b.set(5, 1, 10, B("barrel", facing="up"))
    return b


def shore_bonfire():
    b = Build(f"{NAME}_bonfire", 9, 7, 9, seed=1848027)
    rng = b.rng
    piece_in(b, 4, 1, 0, "north")
    for x in range(9):
        for z in range(1, 9):
            b.set(x, 0, z, B(rng.choice(["stone", "gravel", "sand", "andesite"])))
    for (x, z) in ring_cells(4, 5, 2.2):
        b.set(x, 1, z, B(rng.choice(["cobblestone", "stone", "mossy_cobblestone"])))
    b.set(4, 1, 5, B("hay_block", axis="y"))
    b.campfire(4, 2, 5, lit=True)
    for (x, z) in ((1, 2), (7, 2)):
        b.set(x, 1, z, B("stripped_spruce_log", axis="x"))
    b.set(7, 1, 7, B("barrel", facing="up"))
    return b


LIVERIES = [
    dict(a="calcite", c="red_terracotta", cap="red_terracotta", cap2="bricks", stair="spruce"),
    dict(a="calcite", c="black_terracotta", cap="deepslate_tiles", cap2="polished_deepslate",
         stair="dark_oak"),
]


def pools():
    rp = random.Random(1848031)
    mats = (("sand", 3), ("gravel", 2), ("dirt_path", 2), ("coarse_dirt", 1))
    return {
        "start": [Piece(start(LIVERIES[0], "tower_red", 1848001), 2, PROC),
                  Piece(start(LIVERIES[1], "tower_black", 1848003), 1, PROC)],
        "jetty": [Piece(jetty(), 1, PROC)],
        "paths": [Piece(path_piece(f"{NAME}_path_a", 7, P_SHORE, rp, mats=mats), 3, PROC, "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_b", 11, P_SHORE, rp, mats=mats), 2, PROC, "terrain_matching"),
                  Empty(1)],
        "shore": [Piece(shore_nets(), 2, PROC), Piece(shore_wreck(), 2, PROC), Piece(shore_palms(), 3, PROC),
                  Piece(shore_bonfire(), 2, PROC)],
    }
