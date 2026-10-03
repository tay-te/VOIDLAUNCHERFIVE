"""Rangers' places of the redwood giants.

start pool (3 variants)
  tree   : a lone redwood with buttress roots carrying a railed platform and a
           ranger's hut nine blocks up, a supply basket on a chain, a ladder on to a
           crow's nest; climbers find a cache in a knot-hole high on the trunk
  cabin  : a notched redwood-log cabin with porch and stone chimney, woodpile,
           kitchen plot, the wolf's cushion; the strongbox is under a floor hatch
  hollow : a fallen giant, hollowed out by a hermit - bed, shelves, lamp - with its
           root plate standing on end over the crater it tore; a chest hides in the
           roots
"""
import math

from blocks import AIR, B
from builder import Build, item, slab, stairs, trapdoor
from furnish import chair, table
from kit import chimney, log_walls, roof_gable, skirt
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import book, clear, flora, ground_item, meal_fire, pick, trail

NAME = "treehouse"
STRUCTURE = dict(biomes=["redwood_giants"], size=1, max_distance=32, set="biome_sights")
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
R = "expanse:redwood"
RLOG = "expanse:redwood_log"
SRLOG = "expanse:stripped_redwood_log"
GROUND = ["podzol", "podzol", "coarse_dirt", "grass_block"]
PATH = [("dirt_path", 5), ("podzol", 2), ("coarse_dirt", 2)]


def rlog(axis="y"):
    return B(RLOG, axis=axis)


def treehouse():
    b = Build(f"{NAME}_tree", 14, 28, 14, seed=1907431)
    rng = b.rng
    t0, t1 = 6, 7                                     # 2x2 trunk
    for y in range(0, 25):
        for x in (t0, t1):
            for z in (t0, t1):
                b.set(x, y, z, rlog())
    roots = [((t0 - 1, t0), "x", 2), ((t0 - 2, t0), "x", 1), ((t1 + 1, t1), "x", 2), ((t1 + 2, t1), "x", 1),
             ((t0, t0 - 1), "z", 2), ((t0, t0 - 2), "z", 1), ((t1, t1 + 1), "z", 2), ((t1, t1 + 2), "z", 1),
             ((t1 + 1, t0), "x", 1), ((t0 - 1, t1), "x", 1)]
    for (x, z), ax, h in roots:                       # buttress roots
        for y in range(0, h):
            b.set(x, y, z, rlog(ax if y == 0 else "y"))
    # canopy: a narrow cone of redwood leaves with branch stubs
    leaf = B(f"{R}_leaves", persistent=True, distance=1)
    for y in range(15, 27):
        rr = 3.3 - (y - 15) * 0.25 if y < 24 else 1.2
        for x in range(0, 14):
            for z in range(0, 14):
                d = math.hypot(x - 6.5, z - 6.5)
                if d <= rr and b.get(x, y, z) is None and (d < rr - 0.7 or rng.random() < 0.6):
                    b.set(x, y, z, leaf)
    for y in (16, 19, 22):
        for (dx, dz, ax) in ((-1, 0, "x"), (2, 1, "x"), (1, -1, "z"), (0, 2, "z")):
            if rng.random() < 0.6:
                b.set(t0 + dx, y, t0 + dz, rlog(ax))
    b.set(6, 25, 6, rlog())
    b.set(6, 26, 6, leaf)
    # platform at y=9 with knee braces and a fence railing
    D = 9
    for x in range(2, 12):
        for z in range(2, 12):
            if b.get(x, D, z) is None:
                edge = x in (2, 11) or z in (2, 11)
                b.set(x, D, z, B(SRLOG, axis="x" if z in (2, 11) else "z") if edge else B("spruce_planks"))
    for (x, z, f) in ((5, 2, "south"), (8, 2, "south"), (5, 11, "north"), (8, 11, "north")):
        b.set(x, D - 1, z, stairs("spruce", f, "top"))
    for (x, z, f) in ((2, 5, "east"), (2, 8, "east"), (11, 5, "west"), (11, 8, "west")):
        b.set(x, D - 1, z, stairs("spruce", f, "top"))
    for z in list(range(2, t0)) + list(range(t1 + 1, 12)):   # joists from the trunk to the deck edges
        b.set(t0, D - 1, z, B(SRLOG, axis="z"))
    for x in list(range(2, t0)) + list(range(t1 + 1, 12)):
        b.set(x, D - 1, t1, B(SRLOG, axis="x"))
    for x in range(2, 12):
        for z in range(2, 12):
            if (x in (2, 11) or z in (2, 11)) and not (z == 11 and x in (6, 7)):
                b.set(x, D + 1, z, B("spruce_fence"))
    # the hut on the west half of the platform (door east, toward the trunk)
    hx0, hz0, hx1, hz1 = 2, 2, 5, 7
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            wall = x in (hx0, hx1) or z in (hz0, hz1)
            for y in (D + 1, D + 2, D + 3):
                corner = x in (hx0, hx1) and z in (hz0, hz1)
                b.set(x, y, z, B(SRLOG, axis="y") if corner else B(f"{R}_planks") if wall else AIR)
    roof_gable(b, hx0, hx1, hz0, hz1, D + 4, "spruce", B(f"{R}_planks"), along="z", overhang=1, gable_overhang=1,
               brackets=False)
    b.door(hx1, D + 1, 5, "spruce", "east", hinge="left")
    for (x, z, out) in ((hx0, 4, "west"), (3, hz0, "north"), (4, hz1, "south")):
        b.set(x, D + 2, z, B("glass_pane"))
    b.bed(3, D + 1, 3, "south", "green")
    b.barrel(4, D + 1, 3, "up", LOOT)
    b.set(3, D + 1, 6, B("cartography_table"))
    b.set(4, D + 1, 6, stairs("spruce", "north"))
    b.item_frame(4, D + 2, hz0 + 1, "south", item("spyglass"))
    b.set(4, D + 1, 5, B("lantern"))
    ground_item(b, 3, D + 2, 6, book(BOOKS[NAME]), rotation=1)
    # ladder up the south face of the trunk, through a hatch, on to the crow's nest
    for y in range(1, 18):
        b.set(6, y, t1 + 1, B("ladder", facing="south"))
    b.set(6, D, t1 + 1, B("ladder", facing="south"))
    N = 18
    for x in range(4, 10):
        for z in range(8, 11):
            if b.get(x, N, z) in (None, leaf):
                b.set(x, N, z, B("spruce_planks") if 5 <= x <= 8 else slab("spruce", "top"))
    for x in range(4, 10):
        b.set(x, N + 1, 10, B("spruce_fence"))
    for z in (8, 9):
        b.set(4, N + 1, z, B("spruce_fence"))
        b.set(9, N + 1, z, B("spruce_fence"))
    b.air(5, N + 1, 9, 8, N + 2, 9)
    b.set(6, N, 8, B("spruce_trapdoor", facing="south", half="top", open=True))
    b.set(6, N + 1, 8, B("ladder", facing="south"))
    b.set(8, N + 1, 9, B("lantern"))
    # the knot-hole cache, just below the nest where only climbers look
    b.chest(t1, 14, t1, "south", HIDDEN)
    b.set(t1, 15, t1, trapdoor(R, "south", "top", False))   # the knot-hole's lip
    # supply basket on a chain from a beam over the east rail
    b.set(12, D, 6, B(SRLOG, axis="x"))
    b.set(13, D, 6, B(SRLOG, axis="x"))
    for y in range(2, D):
        b.set(13, y, 6, B("iron_chain", axis="y"))
    b.set(13, 1, 6, B("barrel", facing="up"))
    # on the ground: a fire ring, a woodpile, the path in
    meal_fire(b, 10, 1, 10, ["expanse:venison"], lit=False, done=(150, 0, 0, 0))
    for (x, z) in ((9, 10), (11, 10), (10, 9), (10, 11)):
        b.set(x, 0, z, pick(rng, [("cobblestone", 2), ("mossy_cobblestone", 1)]))
    for x in (1, 2):
        for y in (1, 2):
            b.set(x, y, 11, rlog("z"))
    trail(b, [(6, 9), (6, 11), (5, 13)], rng, PATH, width=1, fade=0.5)
    clear(b, 2, 1, 2, 11, 7, 11)
    flora(b, [(x, z) for x in range(14) for z in range(14)], rng, 0.2, GROUND)
    return b


def cabin():
    b = Build(f"{NAME}_cabin", 14, 12, 15, seed=1907433)
    rng = b.rng
    x0, z0, x1, z1 = 3, 3, 9, 8
    skirt(b, x0, z0, x1, z1, 0, rng, out=1, gap=0.4)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, pick(rng, [("cobblestone", 3), ("mossy_cobblestone", 2), ("stone", 1)]))
            b.set(x, 1, z, B("spruce_planks") if x0 < x < x1 and z0 < z < z1 else pick(rng, [("cobblestone", 2),
                                                                                              ("stone", 1)]))
    log_walls(b, x0, z0, x1, z1, 2, 4, RLOG, SRLOG)
    b.air(x0 + 1, 2, z0 + 1, x1 - 1, 4, z1 - 1)
    roof_gable(b, x0, x1, z0, z1, 5, "dark_oak", B(f"{R}_planks"), along="x", overhang=1, gable_overhang=1)
    chimney(b, x0 - 1, 5, 1, 9, rng, signal=False)
    b.set(x0, 2, 5, AIR)
    b.set(x0, 3, 5, AIR)
    b.campfire(x0, 2, 5, lit=True, facing="east")
    b.set(x0, 3, 5, stairs("stone_brick", "west", "top"))
    # door, windows, porch with a bench and a lantern
    b.door(6, 2, z1, R, "south")
    for (x, z) in ((4, z1), (8, z1), (x1, 5), (5, z0)):
        b.set(x, 3, z, B("glass_pane"))
    for x in range(x0, x1 + 1):
        b.set(x, 1, z1 + 1, B("spruce_planks"))
        b.set(x, 0, z1 + 1, B("cobblestone"))
    for x in (x0, x1):
        b.set(x, 2, z1 + 1, B(f"{R}_fence"))
        b.set(x, 3, z1 + 1, B(f"{R}_fence"))
    b.set(4, 2, z1 + 1, stairs("spruce", "north"))
    b.set(5, 2, z1 + 1, stairs("spruce", "north"))
    b.set(8, 4, z1 + 1, B("lantern", hanging=True))     # under the eaves
    # inside: bed, stove, table with the map, bows on the wall, the wolf's cushion; hatch to the strongbox
    b.bed(8, 2, 4, "south", "green")
    b.barrel(8, 2, 6, "west", LOOT)
    b.set(x0 + 1, 2, z0 + 1, B("smoker", facing="east"))
    table(b, 5, 2, 5, "spruce")
    chair(b, 5, 2, 6, "spruce", "south")
    b.set(4, 2, 7, B("crafting_table"))
    b.item_frame(6, 3, z0 + 1, "south", item("bow"))
    b.item_frame(7, 3, z0 + 1, "south", item("crossbow"))
    b.item_frame(5, 3, z0 + 1, "south", item("map"))
    b.cushion(7, 2, 7, color="brown")
    for x in range(x0 + 1, x1):                       # tie beam carrying the lamp
        b.set(x, 5, 5, B(SRLOG, axis="x"))
    b.set(6, 4, 5, B("lantern", hanging=True))
    b.set(7, 1, 5, trapdoor("spruce", "north", "top", False))
    b.chest(7, 0, 5, "north", HIDDEN)
    b.lectern(4, 2, 5, "east", book(BOOKS[f"{NAME}_cabin"]))
    # outside: woodpile under the eaves, chopping block, kitchen plot, rain barrel
    for z in (4, 5, 6):
        for y in (1, 2):
            b.set(x1 + 1, y, z, rlog("x"))
    b.set(x1 + 2, 1, 9, rlog())
    ground_item(b, x1 + 2, 2, 9, item("iron_axe"), rotation=1)
    for x in range(2, 7):
        for z in (12, 13):
            b.set(x, 0, z, B("farmland", moisture=7))
            b.set(x, 1, z, B(rng.choice(["carrots", "potatoes", "beetroots"])))
    for x in (1, 7):
        for z in (12, 13):
            b.set(x, 1, z, B("spruce_fence"))
    b.set(x1 + 1, 1, z1, B("water_cauldron", level=3))
    trail(b, [(6, z1 + 2), (8, 12), (9, 14)], rng, PATH, width=2, fade=0.5)
    flora(b, [(x, z) for x in range(14) for z in range(15)], rng, 0.18, GROUND)
    return b


def hollow():
    b = Build(f"{NAME}_hollow", 19, 9, 11, seed=1907435)
    rng = b.rng
    cy, cz = 3, 5                                     # log axis along x, radius 2.6, bore 1.5
    for x in range(1, 15):
        for y in range(0, 7):
            for z in range(1, 10):
                d = math.hypot(y - cy, z - cz)
                if d <= 2.6 and not (x == 1 and rng.random() < 0.4):
                    inner = d <= 1.5 and 2 <= x
                    b.set(x, y, z, AIR if inner else rlog("x"))
        if x < 4 and rng.random() < 0.5:             # broken, splintered top end
            b.set(x, 5, cz + rng.choice([-1, 0, 1]), None)
    for x in range(2, 15):                            # boards along the floor of the bore
        b.set(x, 1, cz, B(f"{R}_planks"))
    # the root plate on end at the east, a crater before it
    for y in range(0, 9):
        for z in range(0, 11):
            d = math.hypot(y - 3.5, z - 5)
            if d <= 4.6 and b.inb(15, y, z):
                b.set(15, y, z, pick(rng, [("rooted_dirt", 3), ("dirt", 2), ("coarse_dirt", 1)]) if d > 2.4 else
                      rlog("x"))
                if d > 3.6 and rng.random() < 0.4 and b.inb(16, y, z):
                    b.set(16, y, z, pick(rng, [("rooted_dirt", 1), ("dirt", 1)]))
    for z in range(3, 8):
        for x in (16, 17):
            b.set(x, 0, z, B("coarse_dirt") if rng.random() < 0.5 else B("water", level=0))
            b.set(x, 1, z, AIR)
    b.chest(16, 1, 5, "east", HIDDEN)
    b.set(16, 2, 5, B("rooted_dirt"))
    # the hermit's nook: bed of moss, shelves of jars, lamp, a barrel and a door-flap
    b.set(4, 2, cz, B("moss_carpet"))
    b.bed(11, 2, cz, "east", "green")
    b.barrel(13, 2, cz, "west", LOOT)
    b.shelf(14, 2, cz, "spruce", "west", [item("glass_bottle"), item("brown_mushroom"), item("candle")])
    b.set(10, 2, cz, B("lantern"))
    b.set(1, 2, cz, trapdoor("spruce", "west", "bottom", True))
    ground_item(b, 5, 2, cz, book(BOOKS[f"{NAME}_hollow"]), rotation=0)
    # moss, mushrooms and bracket fungus on the log
    for x in range(2, 15):
        if rng.random() < 0.6:
            b.set(x, 6, cz + rng.choice([-1, 0, 0, 1]), B("moss_carpet"))
        if rng.random() < 0.25:
            b.set(x, 6, cz, B(rng.choice(["red_mushroom", "brown_mushroom", "fern"])))
        for (z, f) in ((cz - 3, "north"), (cz + 3, "south")):
            if rng.random() < 0.2 and b.get(x, 3, z) is None:
                b.set(x, 3, z, B("shelf_mushroom", facing=f))
    trail(b, [(0, cz), (0, cz + 3)], rng, PATH, width=1, fade=0.3)
    flora(b, [(x, z) for x in range(19) for z in range(11)], rng, 0.2, GROUND)
    return b


def pools():
    return {"start": [Piece(treehouse(), 3, "small_forest"), Piece(cabin(), 2, "small_forest"),
                      Piece(hollow(), 2, "small_forest")]}


DATA = data_for(NAME, ["small_forest"])
