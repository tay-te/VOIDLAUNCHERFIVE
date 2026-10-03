"""Willowmere - a stilt hamlet of the willow bayou.

All decks stand six blocks over the bayou floor on willow stilts; everything below a
deck is structure void, so the water stays where it is.

start  hub   : a 15x15 deck with the meeting house (long table, the Tide Book lectern,
               net-mending corner), a clay fire pit, benches, lamp posts, nets on poles
               and Spanish moss under the eaves. By the barrels in the meeting house a
               trapdoor opens on "the gap": a crate slung under the boards holds the
               hidden chest.
walks        : railed plank walkways on stilts (short, long, a dog-leg, a fishing
               landing) - each hub side may sprout one
huts         : dwelling (beds, chest, may lead on), fishing hut (smoker, rods, ladder down
               to a skiff landing), storehouse (supply barrels, crab traps), smokehouse,
               boat dock with a moored boat
"""
import random

from arch import ring
from blocks import AIR, B
from builder import Build, item, slab, stairs, written_book
from furnish import chain_lamp, chair, lamp_post, long_table, rug, shelf_items, table, window
from lore import BOOKS
from pieces import Empty, Piece

NAME = "stilt_hamlet"
STRUCTURE = dict(biomes=["willow_bayou"], spacing=30, separation=10, size=4, max_distance=56,
                 heightmap="OCEAN_FLOOR_WG")
LOOT = "expanse:chests/stilt_hamlet"
HIDDEN = "expanse:chests/stilt_hamlet_hidden"
SUPPLIES = "expanse:chests/stilt_hamlet_supplies"
P_WALKS = f"expanse:{NAME}/walks"
P_HUTS = f"expanse:{NAME}/huts"
J_WALK = "expanse:hamlet_walk"
J_HUT = "expanse:hamlet_hut"
PROC = "stilt_hamlet"
W = "expanse:willow"
SW = "expanse:stripped_willow"
ROOF = "dark_oak"
D = 6


def book():
    d = BOOKS[NAME]
    return written_book(d["title"], d["author"], d["pages"])


def stilt(b, x, z, top=D - 1):
    for y in range(0, top + 1):
        b.set(x, y, z, B(f"{W}_log", axis="y"))


def deck(b, rng, x0, z0, x1, z1, every=3, gaps=0.04):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, D, z, B(f"{W}_planks") if rng.random() > gaps or x in (x0, x1) or z in (z0, z1) else
                  B(f"{W}_slab", type="top"))
    for x in range(x0, x1 + 1, every):
        for z in range(z0, z1 + 1, every):
            stilt(b, x, z)
    for x in (x0, x1):
        for z in (z0, z1):
            stilt(b, x, z)


def rail(b, cells):
    for (x, z) in cells:
        b.set(x, D + 1, z, B(f"{W}_fence"))


def moss_under(b, rng, cells, y, prob=0.35):
    for (x, z) in cells:
        if b.inb(x, y, z) and b.inb(x, y + 1, z) and b.get(x, y, z) is None and \
                b.get(x, y + 1, z) not in (None, AIR) and rng.random() < prob:
            b.hang_moss(x, y, z, rng.randint(1, 3))


def hut(b, rng, x0, z0, x1, z1, wall_h=4, door=None, windows=(), roof_axis="x"):
    """Willow-plank hut on the deck: stripped posts, windows, a dark-oak gable roof dusted with moss.
    door = (x, z, facing). Returns the roof ridge y."""
    from kit import roof_gable
    for (x, z) in ring(x0, z0, x1, z1):
        post = x in (x0, x1) and z in (z0, z1)
        for y in range(D + 1, D + 1 + wall_h):
            b.set(x, y, z, B(f"{SW}_log", axis="y") if post else B(f"{W}_planks"))
    b.air(x0 + 1, D + 1, z0 + 1, x1 - 1, D + wall_h, z1 - 1)
    if door:
        dx, dz, f = door
        b.door(dx, D + 1, dz, W, f)
    for (x, z, out) in windows:
        window(b, x, D + 2, z, out, 1, shutters=W)
    ridge = roof_gable(b, x0, x1, z0, z1, D + wall_h + 1, ROOF, lambda x, y, z: B(f"{W}_planks"), along=roof_axis,
                       overhang=1, gable_overhang=1)
    b.fill(x0 + 1, D + wall_h + 1, z0 + 1, x1 - 1, D + wall_h + 1, z1 - 1, B(f"{W}_planks"))
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(D + wall_h + 1, ridge + 1):
                c = b.get(x, y, z) if b.inb(x, y, z) else None
                if c is not None and c.short.endswith("_stairs") and b.inb(x, y + 1, z) and \
                        b.get(x, y + 1, z) is None and rng.random() < 0.25:
                    b.set(x, y + 1, z, B("moss_carpet"))
    eaves = [(x, z) for x in range(x0 - 1, x1 + 2) for z in range(z0 - 1, z1 + 2)
             if not (x0 <= x <= x1 and z0 <= z <= z1)]
    moss_under(b, rng, eaves, D + wall_h, 0.3)
    return ridge


# ------------------------------------------------------------------ start: hub
def hub():
    b = Build(f"{NAME}_hub", 15, 16, 15, seed=1848101)
    rng = b.rng
    deck(b, rng, 0, 0, 14, 14)
    rail(b, [(x, z) for (x, z) in ring(0, 0, 14, 14) if not (6 <= x <= 8 or 6 <= z <= 8)])
    # meeting house
    hut(b, rng, 3, 6, 11, 12, 4, door=(7, 6, "north"), windows=((3, 9, "west"), (11, 9, "east"), (5, 12, "south"),
                                                            (9, 12, "south")))
    long_table(b, [(x, 9) for x in range(5, 10)], D + 1, "spruce")
    for x in range(6, 9):
        chair(b, x, D + 1, 8, "spruce", "north")
        chair(b, x, D + 1, 10, "spruce", "south")
    b.lectern(4, D + 1, 7, "east", book())
    b.set(10, D + 1, 11, B("barrel", facing="up"))
    b.set(10, D + 2, 11, B("barrel", facing="up"))
    b.set(10, D + 1, 10, B("barrel", facing="west"))
    b.set(9, D + 1, 11, B("barrel", facing="up"))
    # "lift the trapdoor by the barrels, mind the gap": a crate slung under the boards
    b.set(9, D, 10, B(f"{W}_trapdoor", facing="south", half="top", open=False))
    for (x, z) in ((8, 10), (10, 10), (9, 9), (9, 11)):
        b.set(x, D - 1, z, B(f"{W}_planks"))
    b.set(9, D - 2, 10, B(f"{W}_planks"))
    b.chest(9, D - 1, 10, "north", HIDDEN)
    b.set(4, D + 1, 11, B("cobweb"))
    b.set(4, D + 2, 11, B(f"{W}_fence"))
    b.item_frame(5, D + 3, 12, "north", "fishing_rod")
    b.item_frame(8, D + 3, 12, "north", "string")
    chain_lamp(b, 7, D + 5, 9, 1)
    # fire pit and benches in front
    for (x, z) in ((6, 2), (8, 2), (7, 1), (7, 3)):
        b.set(x, D + 1, z, B("mud_brick_slab", type="bottom"))
    b.set(7, D, 2, B("packed_mud"))
    b.campfire(7, D + 1, 2, lit=True)
    for (x, z, f) in ((4, 2, "west"), (10, 2, "east")):
        b.set(x, D + 1, z, stairs("spruce", f))
        b.set(x, D + 1, z + 1, stairs("spruce", f))
    for (x, z) in ((1, 1), (13, 13), (13, 1)):
        lamp_post(b, x, D + 1, z, "spruce", 2, hanging_from="south" if z == 1 else "north")
    for (x, z) in ((1, 13),):
        b.set(x, D + 1, z, B("water_cauldron", level=2))
    b.set(12, D + 1, 4, B("barrel", facing="up"))
    b.set(12, D + 2, 4, B("expanse:potted_willow_sapling"))
    moss_under(b, rng, [(x, z) for (x, z) in ring(0, 0, 14, 14)], D - 1, 0.25)
    for (x, z, f) in ((7, 0, "north"), (7, 14, "south"), (0, 7, "west"), (14, 7, "east")):
        b.jigsaw(x, D + 1, z, f, target=J_WALK, pool=P_WALKS)
    return b


# ------------------------------------------------------------------ walkways
def walk(name, length, seed, landing=False):
    b = Build(f"{NAME}_{name}", 5 if not landing else 9, 10, length, seed=seed)
    rng = b.rng
    x0 = 1
    for z in range(length):
        for x in (x0, x0 + 1, x0 + 2):
            b.set(x, D, z, B(f"{W}_planks") if rng.random() > 0.06 or x != x0 + 1 else B(f"{W}_slab", type="top"))
        if z % 3 == 1:
            stilt(b, x0, z)
            stilt(b, x0 + 2, z)
        if 0 < z < length - 1:
            rail(b, [(x0, z), (x0 + 2, z)])
    for z in range(2, length - 1, 6):
        b.set(x0, D + 2, z, B("lantern"))
    moss_under(b, rng, [(x0 - 1, z) for z in range(length)] + [(x0 + 3, z) for z in range(length)], D - 1, 0.2)
    if landing:                                      # a lower fishing landing off the side
        mid = length // 2
        b.set(x0 + 2, D + 1, mid, AIR)
        for i, y in enumerate((D - 1, D - 2, D - 3)):
            b.set(x0 + 3 + i, y, mid, stairs(W, "west"))
            if i < 2:
                b.set(x0 + 3 + i, y - 1, mid, B(f"{W}_planks"))
        for x in range(x0 + 5, x0 + 8):
            for z in range(mid - 1, mid + 2):
                b.set(x, D - 3, z, B(f"{W}_planks"))
        for (x, z) in ((x0 + 5, mid - 1), (x0 + 7, mid + 1)):
            for y in range(0, D - 3):
                b.set(x, y, z, B(f"{W}_log", axis="y"))
        b.set(x0 + 7, D - 2, mid - 1, B("barrel", facing="up"))
        b.set(x0 + 6, D - 2, mid + 1, B(f"{W}_fence"))
        b.set(x0 + 6, D - 1, mid + 1, B("lantern"))
    b.jigsaw(x0 + 1, D + 1, 0, "north", name=J_WALK)
    b.jigsaw(x0 + 1, D + 1, length - 1, "south", target=J_HUT, pool=P_HUTS)
    return b


def walk_bend():
    b = Build(f"{NAME}_walk_bend", 10, 10, 10, seed=1848113)
    rng = b.rng
    cells = [(x, z) for x in range(1, 4) for z in range(0, 9)] + [(x, z) for x in range(4, 10) for z in range(6, 9)]
    for (x, z) in cells:
        b.set(x, D, z, B(f"{W}_planks"))
    for (x, z) in ((1, 1), (3, 1), (1, 4), (3, 4), (1, 8), (5, 6), (5, 8), (8, 6), (8, 8)):
        stilt(b, x, z)
    rail(b, [(1, z) for z in range(1, 9)] + [(x, 8) for x in range(2, 9)] + [(3, z) for z in range(1, 6)] +
         [(x, 6) for x in range(4, 9)])
    b.set(1, D + 2, 8, B("lantern"))
    moss_under(b, rng, [(0, z) for z in range(10)] + [(x, 9) for x in range(10)], D - 1, 0.25)
    b.jigsaw(2, D + 1, 0, "north", name=J_WALK)
    b.jigsaw(9, D + 1, 7, "east", target=J_HUT, pool=P_HUTS)
    return b


# ------------------------------------------------------------------ huts
def hut_dwelling():
    b = Build(f"{NAME}_dwelling", 11, 16, 11, seed=1848121)
    rng = b.rng
    deck(b, rng, 0, 0, 10, 10)
    rail(b, [(x, z) for (x, z) in ring(0, 0, 10, 10) if not (4 <= x <= 6 and z in (0, 10))])
    hut(b, rng, 2, 3, 8, 8, 4, door=(5, 3, "north"), windows=((2, 5, "west"), (8, 5, "east"), (4, 8, "south")),
        roof_axis="z")
    b.bed(3, D + 1, 5, "south", "brown")
    b.bed(7, D + 1, 6, "south", "green")
    b.chest(3, D + 1, 7, "east", LOOT)
    b.set(7, D + 1, 4, B("crafting_table"))
    b.set(6, D + 1, 7, B("barrel", facing="up"))
    b.set(6, D + 2, 7, B("flower_pot"))
    rug(b, 4, 4, 6, 6, D + 1, "green", None)
    b.set(4, D + 1, 4, AIR)
    chain_lamp(b, 5, D + 5, 5, 0)
    b.set(1, D + 1, 1, B("expanse:potted_willow_sapling"))
    b.set(9, D + 1, 1, B("water_cauldron", level=1))
    b.jigsaw(5, D + 1, 0, "north", name=J_HUT)
    b.jigsaw(5, D + 1, 10, "south", target=J_WALK, pool=P_WALKS)
    return b


def hut_fishing():
    b = Build(f"{NAME}_fishing", 11, 14, 11, seed=1848123)
    rng = b.rng
    deck(b, rng, 0, 0, 10, 10)
    rail(b, [(x, z) for (x, z) in ring(0, 0, 10, 10) if not (4 <= x <= 6 and z == 0) and not (x == 10 and z == 5)])
    # open lean-to on the back half
    for z in (6, 10):
        for x in (1, 9):
            for y in range(D + 1, D + 4):
                b.set(x, y, z, B(f"{SW}_log", axis="y"))
    for x in range(0, 11):
        for z, y in ((6, D + 4), (7, D + 4), (8, D + 5), (9, D + 5), (10, D + 5)):
            b.set(x, y, z, stairs(ROOF, "south") if z < 8 else slab(ROOF, "bottom"))
    b.set(2, D + 1, 9, B("smoker", facing="south"))
    b.set(3, D + 1, 9, B("barrel", facing="up"))
    b.set(4, D + 1, 9, B("barrel", facing="up"))
    b.barrel(5, D + 1, 9, "up", SUPPLIES)
    for (x, it) in ((6, "fishing_rod"), (7, "cod"), (8, "salmon")):
        b.item_frame(x, D + 2, 10, "north", it)
    b.shelf(2, D + 2, 10, "spruce", "north", shelf_items(rng, "fisher"))
    b.set(8, D + 1, 7, B("cobweb"))
    b.set(8, D + 2, 7, B(f"{W}_fence"))
    b.set(2, D + 1, 3, B("cauldron"))
    # ladder down to the skiff landing
    for y in range(1, D):
        b.set(9, y, 5, B(f"{W}_log", axis="y"))
        b.set(10, y, 5, B("ladder", facing="east"))
    b.set(10, D, 5, B(f"{W}_trapdoor", facing="east", half="top", open=True))
    chain_lamp(b, 5, D + 5, 8, 0)
    b.jigsaw(5, D + 1, 0, "north", name=J_HUT)
    return b


def hut_storehouse():
    b = Build(f"{NAME}_storehouse", 9, 17, 11, seed=1848125)
    rng = b.rng
    deck(b, rng, 0, 0, 8, 10)
    rail(b, [(x, z) for (x, z) in ring(0, 0, 8, 10) if not (3 <= x <= 5 and z == 0)])
    hut(b, rng, 1, 3, 7, 9, 4, door=(4, 3, "north"), windows=((1, 6, "west"), (7, 6, "east")), roof_axis="z")
    for (x, z) in ((2, 8), (3, 8), (6, 8), (6, 7), (2, 4)):
        b.barrel(x, D + 1, z, "up", SUPPLIES if (x, z) in ((3, 8), (6, 7)) else None)
    b.set(2, D + 2, 8, B("barrel", facing="up"))
    b.set(6, D + 1, 4, B("hay_block", axis="y"))
    b.set(6, D + 1, 5, B("composter", level=4))
    b.chest(2, D + 1, 6, "east", LOOT)
    for (x, z) in ((1, 1), (7, 1), (7, 2)):
        b.set(x, D + 1, z, B(f"{W}_trapdoor", facing="north", half="bottom", open=False))
    chain_lamp(b, 4, D + 5, 6, 0)
    b.jigsaw(4, D + 1, 0, "north", name=J_HUT)
    return b


def hut_smokehouse():
    b = Build(f"{NAME}_smokehouse", 9, 14, 9, seed=1848127)
    rng = b.rng
    deck(b, rng, 0, 0, 8, 8)
    rail(b, [(x, z) for (x, z) in ring(0, 0, 8, 8) if not (3 <= x <= 5 and z == 0)])
    for x in range(2, 7):
        for z in range(3, 8):
            b.set(x, D, z, B("packed_mud"))
    hut(b, rng, 2, 3, 6, 7, 3, door=(4, 3, "north"), roof_axis="z")
    b.campfire(4, D + 1, 5, lit=True)
    for (x, z, f) in ((3, 6, "north"), (5, 6, "north"), (3, 4, "south")):
        b.item_frame(x, D + 3, z + (1 if f == "north" else -1), f, rng.choice(["cod", "salmon", "porkchop"]))
    b.set(5, D + 1, 4, B("barrel", facing="up"))
    b.jigsaw(4, D + 1, 0, "north", name=J_HUT)
    b.jigsaw(8, D + 1, 4, "east", target=J_WALK, pool=P_WALKS)
    return b


def hut_dock():
    b = Build(f"{NAME}_dock", 9, 10, 13, seed=1848129)
    rng = b.rng
    for z in range(0, 13):
        for x in (3, 4, 5):
            b.set(x, D, z, B(f"{W}_planks"))
        if z % 3 == 0:
            stilt(b, 3, z)
            stilt(b, 5, z)
    rail(b, [(3, z) for z in range(1, 6)] + [(5, z) for z in range(1, 6)])
    # steps down to a low pontoon with a moored boat
    for i, z in enumerate(range(6, 9)):
        for x in (3, 4, 5):
            b.set(x, D, z, AIR)
            b.set(x, D - 1 - i, z, stairs(W, "north"))
    for x in range(1, 8):
        for z in range(9, 13):
            b.set(x, D - 4, z, B(f"{W}_planks") if x in (1, 2, 3, 4, 5) or z in (9,) else AIR)
    for z in range(9, 13):
        for x in (6, 7):
            b.set(x, D - 4, z, AIR)
    for (x, z) in ((1, 9), (1, 12), (5, 12)):
        for y in range(0, D - 4):
            b.set(x, y, z, B(f"{W}_log", axis="y"))
        b.set(x, D - 3, z, B(f"{W}_fence"))
    b.boat(6, D - 4, 11, wood="mangrove", yaw=0.0)
    b.set(2, D - 3, 10, B("barrel", facing="up"))
    b.set(1, D - 2, 12, B("lantern"))
    b.jigsaw(4, D + 1, 0, "north", name=J_HUT)
    return b


def pools():
    return {
        "start": [Piece(hub(), 1, PROC)],
        "walks": [Piece(walk("walk_short", 6, 1848111), 3, PROC), Piece(walk("walk_long", 11, 1848115), 2, PROC),
                  Piece(walk("walk_landing", 9, 1848117, landing=True), 2, PROC), Piece(walk_bend(), 2, PROC),
                  Empty(2)],
        "huts": [Piece(hut_dwelling(), 3, PROC), Piece(hut_fishing(), 3, PROC), Piece(hut_storehouse(), 2, PROC),
                 Piece(hut_smokehouse(), 2, PROC), Piece(hut_dock(), 2, PROC)],
    }
