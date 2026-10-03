"""Palm harbour - a fishing and trading harbour on the palm coast (LANDMARK).

start  square (two liveries: whitewashed / sandstone)
  A paved harbour square (44x44). The harbour master's house (two storeys: the office with
  a cartography table, ledger lectern and charts; his room upstairs) - a rug in the office
  hides the hatch to the smugglers' hold. A warehouse with a hay-and-barrel loft and a crane,
  three market stalls (fish, fruit, pots), the well and the meeting bell, lamp posts.
  The square opens to the shore four ways: two piers and two sandy lanes.
piers  (terrain-matching, so they lie flat on the water and run up the beach as boardwalks)
  long jetty with mooring posts / T-headed fishing pier with a crane / short landing stage
lanes -> waterfront (rigid satellites; one of each kind per harbour)
  north: careened ship hauled out on the sand, or the boatyard with a hull on the stocks
  west: fisherman's cottage or the smokehouse
hold   (down from the office hatch): the smugglers' hold - rum barrels and the hidden chest
"""
import math
import random

from blocks import AIR, B
from arch import tree
from builder import Build, item
from furnish import chair, rug, shelf_items, table
from pieces import Empty, Piece
from loot import book, empty, item as li, pool, table as loot_table
from processors import COAST, STONE_WEATHER, aged_wood, plist, rule
from small_data import SAND_WEATHER
from smallkit import cart, trail
from terrain_paths import PATH_IN, connector, path_piece, piece_in
from townkit import (crane, door, goods_stack, hanging_lamp, house, journal, lamp, lore_entry, minecart, slab,
                     stairs, stall, stripped, trapdoor, vec, village_well, wall_torch, walls, win)

NAME = "palm_harbour"
STRUCTURE = dict(biomes=["palm_coast"], spacing=46, separation=16, size=4, max_distance=72, sited=False,
                 exclusion=("lighthouse", 8))
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
SUPPLIES = f"expanse:chests/{NAME}_supplies"
P_PIERS = f"expanse:{NAME}/piers"
P_LANES = f"expanse:{NAME}/lanes"
P_LANES_B = f"expanse:{NAME}/lanes_b"
P_WATERFRONT = f"expanse:{NAME}/waterfront"
P_SHORE = f"expanse:{NAME}/shore"
P_HOLD = f"expanse:{NAME}/hold"
J_HOLD = "expanse:harbour_hold"
PROC = NAME
P = "expanse:palm"
SP = stripped(P)

BOOK = {
    "title": "Harbour Ledger", "author": "Harbourmaster Odile",
    "pages": [
        "Moorings let: six. Herring boats in at dawn, the trader from the south twice a month. Dues paid in "
        "fish, in rope or in emeralds - never in promises.",
        "The careened brig on the beach will float again by midsummer, says the shipwright. He said so last "
        "midsummer. Tar is dear and palm is soft.",
        "Inspection of the warehouse found nothing amiss. Inspection of my own floor I leave to whoever reads "
        "this. Lift the rug, mind the ladder, and pay your dues.",
    ]}

DATA = {
    "worldgen/processor_list": {
        PROC: plist(SAND_WEATHER, STONE_WEATHER, aged_wood("palm", "acacia", "spruce"), COAST,
                    [rule("cobweb", 0.25, "air"), rule("white_terracotta", 0.06, "smooth_sandstone")]),
    },
    "loot_table/chests": {
        NAME: loot_table(
            NAME,
            pool((3, 6), li("cod", 10, (1, 4)), li("salmon", 8, (1, 3)), li("tropical_fish", 4, (1, 2)),
                 li("kelp", 6, (2, 6)), li("string", 8, (1, 4)), li("paper", 6, (1, 4)), li("bread", 6, (1, 3)),
                 li("expanse:cooked_crab", 5, (1, 3)), li("expanse:palm_sapling", 4, (1, 2)),
                 li("coal", 5, (1, 4)), li("iron_nugget", 5, (2, 6)), li("lead", 3), li("map", 2)),
            pool(1, empty(6), li("emerald", 3, (1, 3)), li("compass", 1), li("fishing_rod", 2, damage=(0.3, 0.9)))),
        f"{NAME}_hidden": loot_table(
            f"{NAME}_hidden",
            pool((3, 6), li("emerald", 10, (3, 8)), li("gold_ingot", 6, (2, 5)), li("gold_nugget", 6, (4, 12)),
                 li("nautilus_shell", 3, (1, 2)), li("spyglass", 3), li("compass", 3), li("sugar", 5, (2, 6)),
                 li("cocoa_beans", 4, (2, 5)), li("gunpowder", 4, (1, 4)), li("iron_sword", 2, damage=(0.4, 0.9))),
            pool((1, 2), empty(2), book(4), li("diamond", 2, (1, 2)), li("golden_apple", 2),
                 li("heart_of_the_sea", 1), li("music_disc_mellohi", 1), li("trident", 1, damage=(0.5, 0.9))),
            pool(1, empty(1), lore_entry(BOOK, 2))),
        f"{NAME}_supplies": loot_table(
            f"{NAME}_supplies",
            pool((2, 5), li("cod", 10, (2, 6)), li("salmon", 8, (1, 4)), li("dried_kelp", 6, (3, 9)),
                 li("string", 6, (2, 5)), li("sugar_cane", 5, (2, 6)), li("bamboo", 4, (2, 6)),
                 li("wheat", 4, (2, 6)), li("leather", 3, (1, 3)), li("ink_sac", 3, (1, 3)),
                 li("expanse:palm_log", 4, (2, 4)))),
    },
}

LIVERIES = {
    "whitewash": dict(fill="white_terracotta", base="cut_sandstone", roof="acacia", sheds="spruce",
                      awnings=("red", "yellow", "light_blue")),
    "sandstone": dict(fill="smooth_sandstone", base="sandstone", roof="spruce", sheds="acacia",
                      awnings=("orange", "white", "lime")),
}


def paving(rng, x, z):
    v = math.sin(x * 0.31) + math.sin(z * 0.27 + 1.3) + math.sin((x - z) * 0.17) + rng.random() * 0.8
    if v < 0.2:
        return B("sand")
    if v < 1.1:
        return B("smooth_sandstone")
    if v < 1.7:
        return B("sandstone")
    if v < 2.1:
        return B("gravel")
    return B("cut_sandstone")


# ------------------------------------------------------------------ the square
def square(variant, seed):
    L = LIVERIES[variant]
    b = Build(f"{NAME}_square_{variant}", 44, 20, 44, seed=seed)
    rng = b.rng
    for x in range(44):
        for z in range(44):
            edge = min(x, z, 43 - x, 43 - z)
            if edge == 0 and rng.random() < 0.45 or edge == 1 and rng.random() < 0.15:
                continue
            b.set(x, 0, z, paving(rng, x, z))
            b.set(x, 1, z, AIR)
            b.set(x, 2, z, AIR)
    # lanes crossing the square: worn cobbles
    for i in range(44):
        for w in (21, 22, 23):
            for (x, z) in ((w, i), (i, w)):
                if rng.random() < 0.75:
                    b.set(x, 0, z, B(rng.choice(["cobblestone", "cobblestone", "gravel", "stone"])))
    master_house(b, rng, L)
    warehouse(b, rng, L)
    market(b, rng, L)
    village_well(b, 22, 19, 1, stone="cobblestone", roof=L["roof"])
    # meeting bell on a little frame, as village meeting points have
    for x in (25, 27):
        for y in (1, 2, 3):
            b.set(x, y, 26, B(f"{P}_fence"))
    for x in (25, 26, 27):
        b.set(x, 4, 26, B(SP.id, axis="x"))
    b.bell(26, 3, 26, attachment="ceiling", facing="south")
    for (x, z) in ((18, 16), (28, 18), (17, 28), (30, 32), (12, 22), (35, 23)):
        lamp(b, x, 1, z, P, 2)
    # quay aprons where the piers leave the square (south and east), with bollards
    for x in range(17, 28):
        for z in range(39, 44):
            b.set(x, 0, z, B(rng.choice(["stone_bricks", "cobblestone", "stone_bricks", "andesite"])))
    for z in range(25, 36):
        for x in range(39, 44):
            b.set(x, 0, z, B(rng.choice(["stone_bricks", "cobblestone", "stone_bricks", "andesite"])))
    for (x, z) in ((18, 42), (26, 42), (42, 26), (42, 34)):
        b.set(x, 1, z, B(f"{P}_fence"))
        b.set(x, 2, z, slab("stone_brick"))
    crane(b, 26, 1, 41, "south", "spruce", h=6, reach=2)
    goods_stack(b, 24, 1, 37, rng, ("barrel", "barrel", "kelp", "hay"))
    net_loft(b, rng, L)
    cottage(b, rng, L, 3, 36, 9, 42, "north", 1849005)
    cottage(b, rng, L, 3, 14, 9, 19, "east", 1849007)
    tree(b, 15, 1, 23, rng, f"{P}_log", f"{P}_leaves", height=6, crown=2.2, lean="west", flat=0.4)
    tree(b, 35, 1, 20, rng, f"{P}_log", f"{P}_leaves", height=7, crown=2.2, lean="south", flat=0.4)
    goods_stack(b, 19, 1, 36, rng, ("barrel", "log", "barrel"), wood="spruce")
    b.barrel(28, 1, 40, "up", SUPPLIES)
    cart(b, 31, 25, "x", "spruce", rng, y=1)
    b.set(31, 3, 25, B("barrel", facing="up"))
    # connectors: piers to the south and east, sandy lanes to the north and west
    connector(b, 22, 1, 43, "south", P_PIERS)
    connector(b, 43, 1, 30, "east", P_PIERS)
    connector(b, 22, 1, 0, "north", P_LANES)
    connector(b, 0, 1, 22, "west", P_LANES_B)
    for (x, z) in ((22, 43), (43, 30), (22, 0), (0, 22)):
        b.set(x, 0, z, B("cobblestone"))
    return b


def master_house(b, rng, L):
    x0, x1, z0, z1 = 3, 13, 3, 11
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "stone_bricks", "cobblestone"])))
    floors, top, ridge = house(b, rng, x0, z0, x1, z1, 1, post=SP, fill=L["fill"], floor=f"{P}_planks",
                               roof=L["roof"], along="x", h=4, upper=f"{P}_planks", upper_h=4,
                               base=L["base"], bay=5, beam=SP.id, gable_fill=f"{P}_planks")
    # front door (south) with steps and a porch roof
    door(b, 8, 2, z1, P, "south", step=None)
    b.set(8, 1, z1 + 1, stairs(L["base"] if L["base"] != "cut_sandstone" else "sandstone", "north"))
    for x in (7, 8, 9):
        b.set(x, 6, z1 + 1, stairs(L["roof"], "south"))
    wall_torch(b, 7, 4, z1 + 1, "south")
    wall_torch(b, 9, 4, z1 + 1, "south")
    b.sign(10, 4, z1 + 1, "spruce", ["", "Harbourmaster", "", ""], wall_facing="south")
    # windows, both storeys
    for x in (5, 11):
        win(b, x, 3, z1, "south", 2, shutters=P)
        win(b, x, 3, z0, "north", 2)
        win(b, x, 8, z1, "south", 2)
        win(b, x, 8, z0, "north", 2, shutters=P)
    for z in (6, 8):
        win(b, x0, 3, z, "west", 2)
        win(b, x1, 3, z, "east", 2)
    win(b, x0, 8, 7, "west", 2)
    win(b, x1, 8, 7, "east", 2)
    # office: cartography table, the ledger, charts on the wall, a desk, the public chest
    fy = 1
    b.set(4, fy + 1, 4, B("cartography_table"))
    b.lectern(6, fy + 1, 4, "south", journal(BOOK))
    table(b, 10, fy + 1, 5, "spruce", top=slab("spruce", "top"))
    chair(b, 10, fy + 1, 6, "spruce", "south")
    b.set(10, fy + 2, 5, B("candle", candles=1, lit=True))
    b.item_frame(9, fy + 3, 4, "south", "map")
    b.item_frame(10, fy + 3, 4, "south", "map")
    b.item_frame(11, fy + 3, 4, "south", "compass")
    b.chest(4, fy + 1, 10, "east", LOOT)
    b.barrel(4, fy + 1, 9, "east")
    b.shelf(4, fy + 3, 9, "spruce", "east", shelf_items(rng, "study"))
    b.painting(12, fy + 2, 9, "west", "sea")
    # the rug over the hold hatch
    rug(b, 6, 7, 8, 9, fy + 1, "blue", "light_blue")
    b.set(7, fy, 8, trapdoor("spruce", "south", "top", False))
    b.jigsaw(7, 0, 8, "down", target=J_HOLD, pool=P_HOLD,
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    # ladder up (north-east corner) through the upper floor
    for y in range(fy + 1, floors[1] + 1):
        b.set(12, y, 4, B("ladder", facing="south"))
    hanging_lamp(b, 8, floors[1] - 1, 6)
    # upstairs: his room
    uy = floors[1]
    b.bed(4, uy + 1, 5, "north", "light_blue")
    b.set(5, uy + 1, 4, B("barrel", facing="south"))
    b.set(4, uy + 1, 7, B("chest", facing="east"))
    rug(b, 5, 6, 7, 8, uy + 1, "light_blue", None)
    b.set(10, uy + 1, 10, B("crafting_table"))
    b.set(11, uy + 1, 10, slab("spruce", "top"))
    b.set(11, uy + 2, 10, B("potted_red_tulip"))
    b.item_frame(12, uy + 2, 8, "west", "spyglass")
    b.painting(8, uy + 2, 10, "north", "tides")
    hanging_lamp(b, 8, top, 7)


def warehouse(b, rng, L):
    x0, x1, z0, z1 = 29, 40, 3, 15
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "stone", "cobblestone"])))
    floors, top, ridge = house(b, rng, x0, z0, x1, z1, 1, post=SP, fill=f"{L['sheds']}_planks",
                               floor="spruce_planks", roof=L["sheds"], along="z", h=6, base=L["base"], bay=4,
                               gable_fill=f"{L['sheds']}_planks")
    # wide doorway to the square (west) with a beam over it
    for z in (8, 9, 10):
        for y in (2, 3, 4):
            b.set(x0, y, z, AIR)
        b.set(x0, 1, z, B("spruce_planks"))
    b.set(x0, 5, 9, B(SP.id, axis="z"))
    wall_torch(b, x0 - 1, 4, 7, "west")
    wall_torch(b, x0 - 1, 4, 11, "west")
    door(b, 34, 2, z1, "spruce", "south")
    for z in (5, 13):
        win(b, x1, 4, z, "east", 1)
        win(b, x0, 4, z, "west", 1)
    # loft over the back half, reached by a ladder
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z0 + 6):
            b.set(x, 5, z, B("spruce_planks"))
    for y in range(2, 6):
        b.set(x1 - 1, y, z0 + 6, B("ladder", facing="south"))
    b.set(x1 - 1, 5, z0 + 6, B("ladder", facing="south"))
    for x in range(x0 + 1, x1 - 1):
        if x % 2:
            b.set(x, 6, z0 + 5, B("spruce_fence"))
    for (x, z) in ((31, 4), (32, 4), (33, 4), (31, 5), (36, 4), (37, 4)):
        b.set(x, 6, z, B("hay_block", axis="x"))
    for (x, z) in ((34, 4), (35, 4), (38, 5)):
        b.barrel(x, 6, z, "up")
    # goods on the floor: barrel rows, kelp bales, logs, the public chest and supply barrels
    for z in range(10, 15):
        b.barrel(x1 - 1, 2, z, "west", SUPPLIES if z == 12 else None)
        if z % 2 == 0:
            b.barrel(x1 - 1, 3, z, "up")
    for x in (32, 33):
        for z in (11, 12, 13):
            b.set(x, 2, z, B("dried_kelp_block") if (x + z) % 3 else B("hay_block", axis="y"))
    b.set(32, 3, 12, B("dried_kelp_block"))
    for z in (9, 10):
        for k in range(3):
            b.set(36 + k, 2, z, B("spruce_log", axis="x"))
    b.set(37, 3, 9, B("spruce_log", axis="x"))
    b.chest(30, 2, 14, "north", LOOT)
    b.set(30, 2, 13, B("composter", level=4))
    b.set(31, 2, 14, B("barrel", facing="north"))
    hanging_lamp(b, 34, top, 10)
    hanging_lamp(b, 34, 4, 6)


def net_loft(b, rng, L):
    """Net loft: nets mended on the looms below, sailors' bunks above."""
    x0, x1, z0, z1 = 30, 38, 34, 41
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "stone_bricks", "cobblestone"])))
    floors, top, ridge = house(b, rng, x0, z0, x1, z1, 1, post=SP, fill=f"{P}_planks", floor="spruce_planks",
                               roof=L["sheds"], along="x", h=4, upper=L["fill"], upper_h=3, base=L["base"], bay=4,
                               beam=SP.id, gable_fill=f"{P}_planks")
    door(b, 34, 2, z0, P, "north")
    b.set(34, 1, z0 - 1, stairs("cobblestone", "south"))
    wall_torch(b, 33, 4, z0 - 1, "north")
    for x in (32, 36):
        win(b, x, 3, z0, "north", 1, shutters=P)
        win(b, x, 8, z0, "north", 1)
        win(b, x, 3, z1, "south", 1)
    win(b, x1, 3, 37, "east", 1)
    win(b, x1, 8, 38, "east", 1)
    for x in (31, 32):
        b.set(x, 2, 40, B("loom", facing="north"))
    b.barrel(37, 2, 40, "north")
    b.chest(37, 2, 39, "west", LOOT)
    for x in range(31, 38):                              # nets hung from a rail to dry
        b.set(x, 5, 37, B("spruce_fence"))
        if x % 2:
            b.set(x, 4, 37, B("cobweb"))
    b.set(31, 2, 35, B("white_wool"))
    b.set(32, 2, 35, B("white_carpet"))
    for y in range(2, floors[1] + 1):
        b.set(37, y, 35, B("ladder", facing="south"))
    uy = floors[1]
    for x in (31, 33):
        b.bed(x, uy + 1, 40, "north", "white")
    b.bed(36, uy + 1, 40, "north", "light_gray")
    b.set(32, uy + 1, 40, B("chest", facing="north"))
    b.set(34, uy + 1, 40, B("barrel", facing="north"))
    hanging_lamp(b, 34, top, 37)


def cottage(b, rng, L, x0, z0, x1, z1, face, seed):
    """A sailor's cottage on the square: bed, stove, chest, a fishing rod on the wall."""
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if b.inb(x, 0, z):
                b.set(x, 0, z, B(rng.choice(["cobblestone", "sandstone", "cobblestone"])))
    along = "x" if (x1 - x0) >= (z1 - z0) else "z"
    floors, top, ridge = house(b, rng, x0, z0, x1, z1, 1, post=SP, fill=L["fill"], floor=f"{P}_planks",
                               roof=L["roof"], along=along, h=4, base=L["base"], bay=3, gable_fill=f"{P}_planks")
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    dx, dz = vec(face)
    dpos = {"north": (cx, z0), "south": (cx, z1), "east": (x1, cz), "west": (x0, cz)}[face]
    door(b, dpos[0], 2, dpos[1], P, face)
    b.set(dpos[0] + dx, 1, dpos[1] + dz, stairs("sandstone", OPPF[face]))
    wall_torch(b, dpos[0] + dx + (dz != 0), 4, dpos[1] + dz + (dx != 0), face)
    for (x, z, out) in ((x0, cz, "west"), (x1, cz, "east"), (cx, z0, "north"), (cx, z1, "south")):
        if out != face:
            win(b, x, 3, z, out, 1, shutters=P if out in ("north", "south") else None)
    # furniture in the corners away from the door
    if face == "north":
        b.bed(x0 + 1, 2, z1 - 2, "south", "red")
        b.set(x1 - 1, 2, z1 - 1, B("smoker", facing="north"))
        b.set(x1 - 1, 2, z1 - 2, B("chest", facing="west"))
        b.barrel(x0 + 1, 2, z1 - 3, "east")
    else:
        bx = x0 + 1 if face == "east" else x1 - 1
        b.bed(bx, 2, z0 + 2, "north", "red")
        b.set(bx, 2, z1 - 1, B("smoker", facing=face))
        b.set(cx, 2, z1 - 1, B("chest", facing="north"))
        b.barrel(cx, 2, z0 + 1, "south")
    b.item_frame(cx, 4, z0 + 1 if face != "north" else z1 - 1, "south" if face != "north" else "north", "fishing_rod")
    hanging_lamp(b, cx, top, cz)


OPPF = {"north": "south", "south": "north", "east": "west", "west": "east"}


def market(b, rng, L):
    aw = L["awnings"]
    fruit = [lambda: B("melon"), lambda: B("pumpkin")]
    for (x, cloth, goods) in ((7, aw[0], ()), (13, aw[1], fruit), (19, aw[2], ())):
        stall(b, x, 1, 31, "north", "spruce", cloth, goods, rng)
    # fish laid out on the fish stall, a cauldron of water by it
    b.item_frame(6, 2, 31, "up", "cod")
    b.set(8, 1, 32, B("water_cauldron", level=3))
    b.pot(18, 2, 31, "north", ("brick", "brick", "angler_pottery_sherd", "brick"))
    for x in (6, 12, 18):
        b.set(x, 1, 33, B("barrel", facing="up"))
    goods_stack(b, 8, 1, 34, rng, ("hay", "barrel", "melon"))
    goods_stack(b, 14, 1, 35, rng, ("barrel", "pumpkin", "barrel"))


# ------------------------------------------------------------------ piers (terrain-matching)
def pier(name, length, head=0, crane_at=None, seed=0):
    """A plank pier `length` long and 5 wide running +z from its back connector, mooring posts every
    four cells, an optional T-head `head` cells to each side at the far end."""
    w = 5 + 2 * head
    mid = w // 2
    b = Build(name, w, 9, length, seed=seed)
    rng = b.rng
    x0, x1 = mid - 2, mid + 2
    for z in range(length):
        is_head = head and z >= length - 4
        xa, xb = (0, w - 1) if is_head else (x0, x1)
        for x in range(xa, xb + 1):
            edge = x in (xa, xb)
            if z % 4 == 2 and edge or (is_head and z == length - 1 and x in (xa, xb)):
                b.set(x, 0, z, B(SP.id, axis="y"))
            b.set(x, 1, z, B(f"{P}_planks") if rng.random() < 0.9 or edge else slab(P, "top"))
            b.set(x, 2, z, AIR)
            if edge and (z % 4 == 2 or is_head and z == length - 1):
                b.set(x, 2, z, B(f"{P}_fence"))
    for z in range(2, length - (4 if head else 0), 8):
        b.set(x0, 2, z, B(f"{P}_fence"))
        b.set(x0, 3, z, B("lantern"))
    zl = length - 1 if head else (length - 1) - ((length - 1 - 2) % 4)
    xl = w - 1 if head else x1
    b.set(xl, 2, zl, B(f"{P}_fence"))
    b.set(xl, 3, zl, B("lantern"))
    b.jigsaw(mid, 1, 0, "north", name=PATH_IN, target="minecraft:empty", pool="minecraft:empty",
             final=f"expanse:palm_planks")
    # gear left on the boards
    b.barrel(x0 + 1, 2, length - 3, "up")
    b.set(x0 + 2, 2, length - 3, B("barrel", facing="north"))
    b.set(x1, 2, length // 2, B("spruce_trapdoor", facing="east", half="bottom", open=False))   # crab pot lid
    if head:
        b.set(1, 2, length - 2, stairs("spruce", "east"))
        b.set(w - 2, 2, length - 2, B("composter", level=2))
        b.item_frame(mid, 2, length - 1, "up", "fishing_rod")
    if crane_at is not None:
        crane(b, crane_at, 2, length - 4, "south", "spruce", h=5, reach=2, load="barrel")
    return b


# ------------------------------------------------------------------ waterfront satellites
def sand_ground(b, rng, mats=("sand", "sand", "sand", "gravel")):
    for x in range(b.size[0]):
        for z in range(1, b.size[2]):
            if rng.random() < 0.85:
                b.set(x, 0, z, B(rng.choice(mats)))


def careened_ship():
    """A brig hauled out on the beach and heeled over on her side for her bottom to be scraped and tarred."""
    b = Build(f"{NAME}_careened", 17, 18, 28, seed=1849101)
    rng = b.rng
    piece_in(b, 8, 1, 0, "north")
    sand_ground(b, rng)
    th = 0.48                                   # heel (radians), toward +x
    xc, yk = 6.0, 0.9                           # keel line (the low bilge sinks into the sand)
    H = 6.0
    z0, z1 = 4, 25
    c, s = math.cos(th), math.sin(th)
    hull = {}
    for z in range(z0, z1 + 1):
        t = (z - z0) / (z1 - z0)
        wd = 4.3 * (1.0 if t < 0.55 else math.sqrt(max(0.0, 1 - ((t - 0.55) / 0.47) ** 2)))
        if wd < 0.6:
            continue
        for x in range(17):
            for y in range(0, 16):
                xp, yp = x - xc, y - yk
                u = xp * c - yp * s
                v = xp * s + yp * c
                if v < -0.5 or v > H:
                    continue
                hw = wd * (max(v, 0.0) / H) ** 0.45 + 0.4
                if abs(u) > hw:
                    continue
                shell = abs(u) > hw - 1.15 or v < 0.6 or z == z0 or t > 0.97
                hull[(x, y, z)] = "shell" if shell else ("deck" if v > H - 1.0 else "hold")
    for (x, y, z), k in hull.items():
        if y < 1:
            continue
        if k == "shell":
            v = (x - xc) * s + (y - yk) * c
            if abs(v - (H - 1.6)) < 0.5:
                b.set(x, y, z, B("stripped_spruce_log", axis="z"))
            else:
                b.set(x, y, z, B(rng.choice(["dark_oak_planks", "spruce_planks", "spruce_planks"])))
        elif k == "deck":
            b.set(x, y, z, B("spruce_planks") if rng.random() < 0.8 else AIR)
        else:
            b.set(x, y, z, AIR)
    # bottom planks stripped for repair on the high side, ribs showing
    for z in range(8, 16):
        for y in range(1, 6):
            for x in range(0, 6):
                if (x, y, z) in hull and hull[(x, y, z)] == "shell" and z % 3 and rng.random() < 0.5:
                    b.set(x, y, z, AIR)
    # the mast, tilted with the hull, its top resting on a trestle
    last = None
    for k in range(0, 40):
        v = k * 0.35
        x = round(xc + v * s)
        y = round(yk + v * c)
        if v < 1 or v > H + 4.5 or not b.inb(x, y, 14):
            continue
        if last is not None and last[0] != x and last[1] != y:
            b.set(x, last[1], 14, B("spruce_log", axis="y"))      # keep the mast joined at each step
        b.set(x, y, 14, B("spruce_log", axis="y"))
        last = (x, y)
    from builder import prune_unsupported
    prune_unsupported(b, keep=lambda st: st.short == "jigsaw")
    # shores propping the hull, tar pot, plank stack, sail spread out to dry
    for z in (7, 12, 17, 22):
        for y in (1, 2):
            b.set(1, y, z, B("stripped_spruce_log", axis="x" if y == 2 else "y"))
    b.campfire(14, 1, 10, lit=True)                     # the tar pot
    b.set(14, 2, 10, B("cauldron"))
    b.set(15, 1, 12, B("barrel", facing="up"))
    b.set(15, 1, 13, B("barrel", facing="up"))
    b.set(15, 2, 12, B("barrel", facing="up"))
    for z in range(16, 21):
        for y in (1, 2):
            if y == 1 or z % 2:
                b.set(15, y, z, B("dark_oak_planks"))
    b.set(14, 1, 18, B("crafting_table"))
    for x in range(1, 5):
        for z in range(1, 4):
            if b.get(x, 1, z) is None and rng.random() < 0.8:
                b.set(x, 1, z, B("white_carpet"))
    for y in range(1, 10):                              # the ship's chest, on the floor of the hold
        below, here = b.get(7, y - 1, 18), b.get(7, y, 18)
        if below is not None and below.short != "air" and here is not None and here.short == "air":
            b.chest(7, y, 18, "west", LOOT)
            break
    lamp(b, 12, 1, 3, "spruce", 2)
    return b


def fisher_cottage(L):
    b = Build(f"{NAME}_fisher", 14, 13, 16, seed=1849111)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    sand_ground(b, rng, ("sand", "sand", "coarse_dirt", "gravel"))
    x0, x1, z0, z1 = 3, 9, 5, 11
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "sandstone", "cobblestone"])))
    house(b, rng, x0, z0, x1, z1, 1, post=SP, fill=L["fill"], floor=f"{P}_planks", roof=L["roof"], along="z",
          h=4, base=L["base"], bay=3, gable_fill=f"{P}_planks")
    door(b, 6, 2, z0, P, "north")
    b.set(6, 1, z0 - 1, stairs("sandstone", "south"))
    for z in (7, 9):
        win(b, x0, 3, z, "west", 1, shutters=P)
        win(b, x1, 3, z, "east", 1, shutters=P)
    win(b, 6, 3, z1, "south", 2)
    wall_torch(b, 5, 4, z0 - 1, "north")
    # inside: bed, the fisherman's barrel, smoker, rod on the wall
    b.bed(4, 2, 9, "south", "light_blue")
    b.barrel(8, 2, 6, "west")
    b.set(8, 2, 10, B("smoker", facing="west"))
    b.chest(8, 2, 9, "west", LOOT)
    b.set(4, 2, 6, B("crafting_table"))
    b.item_frame(x0 + 1, 4, 8, "east", "fishing_rod")
    b.item_frame(x1 - 1, 4, 7, "west", "cod")
    b.shelf(8, 4, 10, "spruce", "west", shelf_items(rng, "fisher"))
    hanging_lamp(b, 6, 5, 8)
    # outside: net racks, a boat drawn up, barrels
    for z in (4, 8, 12):
        for y in (1, 2, 3):
            b.set(11, y, z, B(f"{P}_fence"))
    for z in range(4, 13):
        b.set(11, 3, z, B(f"{P}_fence"))
        if z % 4 and rng.random() < 0.8:
            b.set(11, 2, z, B("cobweb"))
    b.boat(1, 1, 13, wood="spruce", yaw=0.0)
    b.set(9, 1, 13, B("barrel", facing="up"))
    b.set(10, 1, 14, B("barrel", facing="up"))
    lamp(b, 2, 1, 3, P, 2)
    return b


def smokehouse(L):
    b = Build(f"{NAME}_smokehouse", 12, 13, 13, seed=1849121)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    sand_ground(b, rng, ("sand", "gravel", "coarse_dirt"))
    x0, x1, z0, z1 = 2, 8, 4, 9
    house(b, rng, x0, z0, x1, z1, 1, post=B("cobblestone"), fill=lambda: B(rng.choice(["cobblestone", "sandstone",
                                                                                         "mossy_cobblestone"])),
          floor="cobblestone", roof="spruce", along="x", h=4, bay=3, gable_fill=f"{P}_planks")
    door(b, 5, 2, z0, "spruce", "north")
    for x in (3, 7):
        b.set(x, 2, z1 - 1, B("smoker", facing="north"))
    b.campfire(5, 2, z1 - 1, lit=True)
    for x in range(3, 8):
        b.set(x, 5, 6, B("spruce_fence"))
    for x in (3, 4, 6, 7):
        b.item_frame(x, 4, z1 - 1, "north", rng.choice(["cod", "salmon", "cod", "dried_kelp"]))
    b.barrel(3, 2, 5, "east")
    b.barrel(7, 2, 5, "west", SUPPLIES)
    # chimney over the fire
    for y in range(2, 11):
        b.set(5, y, z1 + 1, B(rng.choice(["cobblestone", "mossy_cobblestone"])) if y > 1 else B("cobblestone"))
    b.set(5, 1, z1 + 1, B("cobblestone"))
    b.campfire(5, 11, z1 + 1, lit=True)
    b.set(5, 2, z1, AIR)
    # woodpile and a chopping block outside
    for z in range(4, 9):
        for y in (1, 2):
            if y == 1 or z % 2:
                b.set(10, y, z, B(f"{P}_log", axis="z"))
    b.set(9, 1, 2, B("stripped_spruce_log", axis="y"))
    b.item_frame(9, 2, 2, "up", "iron_axe")
    wall_torch(b, 4, 4, z0 - 1, "north")
    return b


def boatyard():
    b = Build(f"{NAME}_boatyard", 15, 11, 24, seed=1849131)
    rng = b.rng
    piece_in(b, 7, 1, 0, "north")
    sand_ground(b, rng)
    # slipway of planks running down to the sea end (+z)
    for z in range(3, 24):
        for x in range(4, 11):
            b.set(x, 0, z, B("spruce_planks") if x in (4, 10) or z % 3 == 0 else B("sand"))
    # a hull on the stocks: keel, ribs, half the strakes on
    for z in range(5, 20):
        b.set(7, 1, z, B("stripped_spruce_log", axis="z"))
    for z in range(6, 19, 2):
        half = 2 if 8 <= z <= 16 else 1
        for side in (-1, 1):
            for k in range(1, half + 1):
                b.set(7 + side * k, 1, z, B("spruce_fence"))
            for y in range(2, 3 + half):
                b.set(7 + side * half, y, z, B("spruce_fence"))
    for z in range(7, 18):
        for (x, y) in ((5, 2), (5, 3)) if 8 <= z <= 16 else ((6, 2),):
            if z % 2 == 1 or rng.random() < 0.4:
                b.set(x, y, z, B("dark_oak_planks"))
    b.set(7, 2, 19, B("stripped_spruce_log", axis="y"))
    b.set(7, 3, 19, B("stripped_spruce_log", axis="y"))
    for z in (7, 12, 17):
        b.set(7, 1, z, B("stripped_spruce_log", axis="x"))
    # sawhorses, plank stacks, tools, the finished skiff waiting
    for (x, z) in ((1, 4), (1, 7)):
        b.set(x, 1, z, B("spruce_fence"))
        b.set(x + 1, 1, z, B("spruce_fence"))
        b.set(x, 2, z, slab("spruce", "bottom"))
        b.set(x + 1, 2, z, slab("spruce", "bottom"))
    for z in range(10, 15):
        for y in (1, 2):
            b.set(1, y, z, B("dark_oak_planks") if y == 1 or z < 13 else AIR)
    b.set(13, 1, 4, B("crafting_table"))
    b.set(13, 1, 5, B("grindstone", face="floor", facing="west"))
    b.chest(13, 1, 6, "west", LOOT)
    b.barrel(13, 1, 7, "west")
    b.item_frame(13, 2, 4, "up", "iron_axe")
    b.boat(12, 1, 18, wood="dark_oak", yaw=0.0)
    crane(b, 12, 1, 12, "west", "spruce", h=6, reach=3, load=B("stripped_spruce_log", axis="y"))
    lamp(b, 3, 1, 2, "spruce", 2)
    return b


# ------------------------------------------------------------------ the smugglers' hold
def hold():
    b = Build(f"{NAME}_hold", 11, 6, 11, seed=1849141)
    rng = b.rng
    for x in range(11):
        for z in range(11):
            for y in range(6):
                b.set(x, y, z, B(rng.choice(["dirt", "sand", "sandstone", "stone"])))
    b.air(1, 1, 2, 9, 3, 9)
    for x in range(1, 10):
        for z in range(2, 10):
            b.set(x, 0, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cobblestone"])))
    for x in range(1, 10):
        b.set(x, 4, 5, B("stripped_spruce_log", axis="x"))
    for y in range(1, 6):
        b.set(5, y, 1, B("ladder", facing="south"))
    b.air(5, 1, 1, 5, 5, 1)
    for y in range(1, 6):
        b.set(5, y, 1, B("ladder", facing="south"))
    b.jigsaw(5, 5, 1, "up", name=J_HOLD, final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    # rum barrels in racks, contraband crates, the strongbox behind them
    for z in range(3, 10):
        b.barrel(1, 1, z, "east", SUPPLIES if z == 6 else None)
        if z % 2:
            b.barrel(1, 2, z, "east")
    for x in (7, 8, 9):
        for y in (1, 2):
            if (x, y) != (9, 2):
                b.barrel(x, y, 9, "north")
    b.chest(9, 1, 8, "west", HIDDEN)
    b.set(8, 1, 8, B("barrel", facing="up"))
    b.set(7, 1, 3, B("barrel", facing="up"))
    b.set(8, 1, 3, B("chest", facing="south"))
    table(b, 5, 1, 6, "spruce", top=slab("spruce", "top"))
    b.item_frame(5, 2, 6, "up", "map")
    b.set(4, 1, 6, stairs("spruce", "west"))
    b.set(5, 2, 9, B("candle", candles=2, lit=True))
    b.set(5, 1, 9, B("barrel", facing="up"))
    b.set(2, 3, 9, B("cobweb"))
    hanging_lamp(b, 5, 3, 5)
    b.item_frame(3, 2, 9, "north", "gunpowder")
    return b


def pools():
    rp = random.Random(1849151)
    mats = (("sand", 4), ("gravel", 2), ("dirt_path", 1), ("coarse_dirt", 1))
    W, S_ = LIVERIES["whitewash"], LIVERIES["sandstone"]
    return {
        "start": [Piece(square("whitewash", 1849001), 2, PROC), Piece(square("sandstone", 1849003), 1, PROC)],
        "piers": [Piece(pier(f"{NAME}_pier_long", 26, seed=1849061), 3, PROC, "terrain_matching"),
                  Piece(pier(f"{NAME}_pier_t", 22, head=3, crane_at=10, seed=1849063), 3, PROC, "terrain_matching"),
                  Piece(pier(f"{NAME}_pier_short", 12, seed=1849065), 2, PROC, "terrain_matching"),
                  Empty(1)],
        "lanes": [Piece(path_piece(f"{NAME}_lane_a", 8, P_WATERFRONT, rp, mats=mats), 3, PROC, "terrain_matching"),
                  Piece(path_piece(f"{NAME}_lane_b", 14, P_WATERFRONT, rp, mats=mats), 2, PROC, "terrain_matching")],
        "lanes_b": [Piece(path_piece(f"{NAME}_lane_c", 10, P_SHORE, rp, mats=mats), 1, PROC, "terrain_matching")],
        "waterfront": [Piece(careened_ship(), 3, PROC), Piece(boatyard(), 2, PROC)],
        "shore": [Piece(fisher_cottage(W), 2, PROC), Piece(fisher_cottage_alt(S_), 1, PROC),
                  Piece(smokehouse(W), 2, PROC)],
        "hold": [Piece(hold(), 1, PROC)],
    }


def fisher_cottage_alt(L):
    b = fisher_cottage(L)
    b.name = f"{NAME}_fisher_b"
    return b
