"""Ruined watchtower of the peaks and moors.

start pool - the keep, an 11x11 limestone tower ~33 high on a battered base
  keep_fallen : the north-east corner has collapsed from the third floor up
  keep_breach : top intact with a timber hoarding, the west face breached
  Inside: guard room, iron-doored armoury opened by a lever (hidden chest,
  armour stands), barracks, captain's study (lore lectern), mess, watch room,
  and a corbelled top platform with a brazier and the lookout chest. A spiral
  stair climbs the whole height.
paths     - terrain-matching tracks leaving the keep on three sides
ruins     - gatehouse, curtain-wall stretch (rubble + suspicious gravel),
            roofless barracks with scavengers' fire, training yard, well
"""
import math

from blocks import AIR, B
from builder import Build, item, slab, stairs, written_book
from common import spiral_floor_ok, spiral_stair, vines_on
from furnish import chain_lamp, chair, rug, table
from lore import BOOKS
from pieces import Empty, Piece
from terrain_paths import connector, path_piece, piece_in

NAME = "ruined_watchtower"
STRUCTURE = dict(biomes=["verdant_peaks", "prismatic_peaks", "heather_moor"], spacing=34, separation=11, size=2,
                 max_distance=56, max_relief=20)
LOOT = "expanse:chests/ruined_watchtower"
HIDDEN = "expanse:chests/ruined_watchtower_hidden"
P_PATHS = f"expanse:{NAME}/paths"
P_RUINS = f"expanse:{NAME}/ruins"
LB = "expanse:limestone_brick"
X0, X1, Z0, Z1 = 5, 15, 5, 15           # shaft walls
C = 10
FLOORS = [6, 12, 18, 24]
PLAT = 30


def wall_block(rng):
    r = rng.random()
    if r < 0.2:
        return B("expanse:mossy_limestone")
    if r < 0.27:
        return B("expanse:limestone")
    if r < 0.29:
        return B("expanse:chiseled_limestone")
    return B("expanse:limestone_bricks")


def keep(variant):
    b = Build(f"{NAME}_keep_{variant}", 21, 38, 21, seed=1847301 + (variant == "breach"))
    rng = b.rng

    if variant == "fallen":
        def top_of(x, z):
            d = (X1 - min(max(x, X0), X1)) + (min(max(z, Z0), Z1) - Z0)
            k = max(0.0, 8 - d) * 1.6
            return 99 if k <= 1 else int(PLAT + 1 - min(14, k * 1.3) + (x * 7 + z * 13) % 3 - 1)
    else:
        def top_of(x, z):
            return 99

    # ---- ground course: irregular rubble skirt, battered base
    for x in range(2, 19):
        for z in range(2, 19):
            d = max(abs(x - C), abs(z - C))
            if d <= 6 or (d == 7 and rng.random() < 0.7) or (d == 8 and rng.random() < 0.25):
                b.set(x, 0, z, B(rng.choice(["expanse:limestone", "expanse:limestone", "cobblestone", "gravel",
                                             "expanse:limestone_bricks", "coarse_dirt"])))
    for x in range(4, 17):
        for z in range(4, 17):
            if x in (4, 16) or z in (4, 16):
                f = "south" if z == 4 else "north" if z == 16 else "east" if x == 4 else "west"
                b.set(x, 1, z, stairs(LB, f) if (x, z) not in ((4, 4), (16, 4), (4, 16), (16, 16)) else
                      B("expanse:limestone_bricks"))

    # ---- shaft walls with quoins
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if x not in (X0, X1) and z not in (Z0, Z1):
                continue
            t = min(top_of(x, z), PLAT - 1)
            corner = x in (X0, X1) and z in (Z0, Z1)
            for y in range(1, t + 1):
                if corner:
                    b.set(x, y, z, B("expanse:polished_limestone") if (y // 2) % 2 else B("expanse:chiseled_limestone"))
                else:
                    b.set(x, y, z, wall_block(rng))
    b.fill(X0 + 1, 1, Z0 + 1, X1 - 1, PLAT + 3, Z1 - 1, AIR)

    def outer_ring():
        for x in range(X0 - 1, X1 + 2):
            yield x, Z0 - 1, "south"
            yield x, Z1 + 1, "north"
        for z in range(Z0, Z1 + 1):
            yield X0 - 1, z, "east"
            yield X1 + 1, z, "west"

    for fy in FLOORS:                                  # string courses
        for x, z, f in outer_ring():
            if top_of(x, z) >= fy:
                b.set(x, fy, z, stairs(LB, f, "top"))
    for x, z, f in outer_ring():                       # corbels under the platform
        if top_of(x, z) >= PLAT - 1:
            b.set(x, PLAT - 1, z, stairs(LB, f, "top"))
            if (x + z) % 2 == 0:
                b.set(x, PLAT - 2, z, stairs(LB, f, "top"))

    # ---- spiral stair and floors
    steps = spiral_stair(b, C, C, 1, PLAT, LB, B("expanse:polished_limestone"), start=5,
                         underside=slab(LB, "top"))
    b.set(C, PLAT + 1, C, B("expanse:limestone_brick_wall"))
    b.campfire(C, PLAT + 2, C)                         # beacon brazier on the stair pillar
    for fy in FLOORS:
        for x in range(X0 + 1, X1):
            for z in range(Z0 + 1, Z1):
                if (x, z) == (C, C) or not spiral_floor_ok(steps, x, z, fy):
                    continue
                if variant == "fallen" and fy >= 18 and top_of(x, z) < fy + 2 and (x - C) + (C - z) > 3:
                    continue                              # the floors fell in with the corner
                inner = abs(x - C) <= 1 and abs(z - C) <= 1
                b.set(x, fy, z, slab(LB, "top") if inner else B("spruce_planks"))
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            b.set(x, 0, z, B(rng.choice(["expanse:polished_limestone", "expanse:limestone_bricks", "cobblestone",
                                         "expanse:mossy_limestone"])))

    # ---- top platform, parapet, merlons
    hole = (lambda x, z: (X1 - x) + (z - Z0) < 6) if variant == "fallen" else (lambda x, z: False)
    for x in range(X0 - 1, X1 + 2):
        for z in range(Z0 - 1, Z1 + 2):
            if (x, z) == (C, C) or not spiral_floor_ok(steps, x, z, PLAT) or hole(x, z):
                continue
            edge = x in (X0 - 1, X1 + 1) or z in (Z0 - 1, Z1 + 1)
            if edge and top_of(x, z) < PLAT:
                continue
            b.set(x, PLAT, z, wall_block(rng) if edge else
                  B("expanse:polished_limestone") if (x + z) % 2 else B("expanse:limestone_bricks"))
    for x, z, f in outer_ring():
        if top_of(x, z) >= PLAT + 1 and not hole(x, z):
            b.set(x, PLAT + 1, z, wall_block(rng))
            if (x + z) % 2 == 0 and top_of(x, z) >= PLAT + 2:
                b.set(x, PLAT + 2, z, wall_block(rng))
    for (x, z) in ((X0 - 1, Z0 - 1), (X1 + 1, Z0 - 1), (X0 - 1, Z1 + 1), (X1 + 1, Z1 + 1)):
        if top_of(x, z) >= PLAT + 2 and not hole(x, z):
            b.column(x, z, PLAT + 1, PLAT + 2, B("expanse:chiseled_limestone"))
            b.set(x, PLAT + 3, z, slab(LB))

    if variant == "breach":
        # timber hoarding on the south-west corner of the top and a breach in the west face
        for x in range(X0 - 1, X0 + 4):
            for z in range(Z1 - 3, Z1 + 2):
                if x in (X0 - 1, X0 + 3) and z in (Z1 - 3, Z1 + 1):
                    b.column(x, z, PLAT + 1, PLAT + 3, B("spruce_log", axis="y"))
        for x in range(X0 - 2, X0 + 5):
            for z in range(Z1 - 4, Z1 + 3):
                ring = min(x - (X0 - 2), (X0 + 4) - x, z - (Z1 - 4), (Z1 + 2) - z)
                if 0 <= x <= 20 and 0 <= z <= 20:
                    b.set(x, PLAT + 4 + min(ring, 2), z, stairs("spruce", "east" if x - (X0 - 2) == ring else
                                                                "west" if (X0 + 4) - x == ring else
                                                                "south" if z - (Z1 - 4) == ring else "north")
                          if ring < 2 else slab("spruce"))
        for z in range(Z0 + 3, Z0 + 7):
            for y in range(8, 15 - abs(z - (Z0 + 5)) * 2):
                b.set(X0, y, z, AIR)
        for (x, z) in ((X0 - 2, Z0 + 4), (X0 - 3, Z0 + 5), (X0 - 2, Z0 + 6), (X0 - 1, Z0 + 5)):
            b.set(x, 1, z, B(rng.choice(["expanse:mossy_limestone", "gravel", "expanse:limestone_bricks"])))
            if rng.random() < 0.5:
                b.set(x, 2, z, slab(LB))

    # ---- door (south) with an arch and a raised portcullis
    for y in (1, 2, 3):
        b.set(C, y, Z1, AIR)
    b.set(C - 1, 3, Z1, stairs(LB, "east", "top"))
    b.set(C + 1, 3, Z1, stairs(LB, "west", "top"))
    b.set(C, 4, Z1, B("iron_bars"))
    b.set(C, 5, Z1, B("expanse:chiseled_limestone"))
    b.set(C, 1, Z1 + 1, stairs(LB, "north"))
    b.set(C - 1, 3, Z1 + 1, B("wall_torch", facing="south"))
    # arrow slits on every floor
    for fy in [0] + FLOORS:
        for (x, z) in ((C, Z0), (X0, C), (X1, C), (C - 3, Z1), (C + 3, Z0)):
            if fy == 0 and z == Z1:
                continue
            for y in (fy + 2, fy + 3):
                if top_of(x, z) > y and b.get(x, y, z) is not None and b.get(x, y, z).short != "air":
                    b.set(x, y, z, AIR if (x + y + z) % 3 else B("iron_bars"))

    # ---- ground floor: guard room + iron-doored armoury (NW corner, x6..8 z6..8)
    for z in range(Z0 + 1, Z0 + 4):
        for y in (1, 2, 3, 4, 5):
            b.set(X0 + 4, y, z, B("expanse:limestone_bricks"))
    for x in range(X0 + 1, X0 + 4):
        for y in (1, 2, 3, 4, 5):
            b.set(x, y, Z0 + 4, B("expanse:limestone_bricks"))
    b.set(X0 + 4, 1, Z0 + 4, B("expanse:limestone_bricks"))
    b.door(X0 + 2, 1, Z0 + 4, "iron", "south")
    b.set(X0 + 1, 2, Z0 + 5, B("lever", face="wall", facing="south"))       # powers the wall beside the door
    b.chest(X0 + 1, 1, Z0 + 1, "south", HIDDEN)
    b.armor_stand(X0 + 3, 1, Z0 + 1, yaw=180.0, equipment={"head": "iron_helmet", "chest": "chainmail_chestplate",
                                                             "mainhand": "crossbow"})
    b.set(X0 + 1, 1, Z0 + 3, B("anvil", facing="east"))
    b.set(X0 + 1, 1, Z0 + 2, B("grindstone", face="floor", facing="east"))
    b.set(X0 + 3, 1, Z0 + 3, B("barrel", facing="up"))
    b.item_frame(X0 + 2, 3, Z0 + 1, "south", "iron_sword")
    b.item_frame(X0 + 1, 3, Z0 + 2, "east", "bow")
    # guard room
    b.chest(X1 - 1, 1, Z0 + 1, "west", LOOT)
    b.barrel(X1 - 1, 1, Z0 + 2, "up")
    b.barrel(X1 - 1, 2, Z0 + 2, "up")
    b.barrel(X1 - 2, 1, Z0 + 1, "west")
    b.set(X0 + 1, 1, Z1 - 1, B("hay_block", axis="x"))
    b.set(X0 + 2, 1, Z1 - 1, B("hay_block", axis="y"))
    table(b, X1 - 2, 1, Z1 - 2, "spruce")
    chair(b, X1 - 3, 1, Z1 - 2, "spruce", "west")
    chair(b, X1 - 2, 1, Z1 - 1, "spruce", "south")
    b.set(X1 - 1, 1, Z1 - 1, B("cauldron"))
    b.set(X0 + 1, 3, C + 2, B("wall_torch", facing="east"))
    b.set(X1 - 1, 3, C - 1, B("wall_torch", facing="west"))
    b.banner(C, 4, Z0 + 1, "white", [("stripe_bottom", "gray"), ("cross", "light_gray"), ("border", "gray")],
             wall_facing="south")

    # ---- floor 1: barracks
    y = FLOORS[0] + 1
    for z in (Z0 + 1, Z0 + 3):
        b.bed(X0 + 2, y, z, "west", "brown")
    b.bed(X1 - 2, y, Z1 - 1, "east", "brown")
    b.barrel(X0 + 3, y, Z0 + 2, "up")
    b.barrel(X1 - 1, y, Z1 - 2, "west")
    rug(b, X0 + 1, Z1 - 3, X0 + 3, Z1 - 1, y, "brown", "gray")
    b.armor_stand(X1 - 1, y, Z0 + 1, yaw=135.0, equipment={"head": "leather_helmet", "chest": "leather_chestplate"})
    chain_lamp(b, C + 3, FLOORS[1], C - 3, 1)
    b.set(X0 + 1, y + 2, C + 2, B("wall_torch", facing="east"))

    # ---- floor 2: captain's study with the lore lectern
    y = FLOORS[1] + 1
    b.lectern(X0 + 2, y, Z1 - 1, "north", written_book(BOOKS[NAME]["title"], BOOKS[NAME]["author"],
                                                        BOOKS[NAME]["pages"]))
    for x in range(X0 + 1, X0 + 4):
        b.set(x, y, Z0 + 1, B("bookshelf"))
        b.bookshelf(x, y + 1, Z0 + 1, "south", slots=(0, 1, 4) if x % 2 else (2, 3))
    b.set(X1 - 1, y, Z0 + 1, B("cartography_table"))
    b.set(X1 - 2, y, Z0 + 1, B("barrel", facing="south"))
    table(b, X1 - 2, y, Z1 - 2, "spruce", top="white_carpet")
    chair(b, X1 - 2, y, Z1 - 1, "spruce", "south")
    b.set(X1 - 1, y, Z1 - 1, B("expanse:potted_edelweiss"))
    b.painting(X0 + 1, y + 1, C + 1, "east", "wanderer")
    rug(b, C - 2, C + 2, C + 2, C + 3, y, "red", "black")
    chain_lamp(b, C - 3, FLOORS[2], C + 3, 1)

    # ---- floor 3: mess hall (open to the sky where the corner fell)
    y = FLOORS[2] + 1
    b.set(X0 + 1, y, Z1 - 1, B("smoker", facing="east"))
    b.set(X0 + 1, y, Z1 - 2, B("cauldron"))
    b.barrel(X0 + 2, y, Z1 - 1, "north")
    for z in range(Z1 - 4, Z1 - 1):
        b.set(C - 2, y, z, slab("spruce", "top") if z != Z1 - 4 else B("spruce_fence"))
    b.set(C - 2, y, Z1 - 1, B("spruce_fence"))
    for (x, z) in ((X1 - 2, Z0 + 2), (X1 - 1, Z0 + 3), (X1 - 3, Z0 + 1)):
        if b.get(x, FLOORS[2], z) is not None:
            b.set(x, y, z, rng.choice([slab(LB), B("expanse:mossy_limestone"), B("gravel")]))

    # ---- floor 4: watch room
    y = FLOORS[3] + 1
    b.set(X0 + 1, y, Z0 + 1, B("barrel", facing="up"))
    b.item_frame(X0 + 1, y + 1, C, "east", "spyglass")
    b.set(X0 + 1, y, Z1 - 1, B("fletching_table"))
    if variant == "breach":
        b.bed(X0 + 3, y, Z1 - 1, "west", "gray")

    # ---- top: lookout chest, banner, rubble
    y = PLAT + 1
    b.chest(X0 + 1, y, Z1 - 1, "north", LOOT)
    b.set(X0 + 2, y, Z1 - 1, B("lantern"))
    b.banner(C, PLAT, Z1 + 2, "white", [("stripe_bottom", "gray"), ("triangles_bottom", "gray"),
                                        ("border", "light_gray")], wall_facing="south")
    if variant == "fallen":
        for (x, z) in ((X0 + 1, Z0 + 2), (X0 + 2, Z0 + 1)):
            b.set(x, y, z, slab(LB))
        # rubble spilled outside the collapsed corner, with gravel for archaeology
        for x in range(12, 21):
            for z in range(0, 9):
                if b.get(x, 1, z) is not None:
                    continue
                d = (x - 12) + (8 - z)
                if 4 <= d and rng.random() < max(0.0, 0.85 - 0.07 * d):
                    b.set(x, 0, z, B(rng.choice(["expanse:limestone", "gravel", "gravel", "cobblestone"])))
                    b.set(x, 1, z, rng.choice([slab(LB), B("expanse:mossy_limestone"), B("mossy_cobblestone"),
                                               stairs(LB, rng.choice(["north", "east", "south", "west"])),
                                               B("expanse:limestone_bricks"), B("short_grass")]))
                    if rng.random() < 0.25:
                        b.set(x, 2, z, slab(LB) if rng.random() < 0.6 else B("moss_carpet"))
        for (x, z) in ((X1 - 1, Z0 + 1), (X1 - 2, Z0 + 1), (X1 - 1, Z0 + 2)):
            b.set(x, FLOORS[1] + 1, z, rng.choice([slab(LB), B("expanse:mossy_limestone"), B("gravel")]))

    # ---- weathering, heather markers and the path connectors (E, W, N, S)
    vines_on(b, rng, 0.05, ymin=2, region=lambda x, y, z: y < 20, max_len=4)
    for x in range(0, 21):
        for z in range(0, 21):
            if b.get(x, 0, z) is None and rng.random() < 0.18 and max(abs(x - C), abs(z - C)) > 6:
                b.set(x, 0, z, B("grass_block"))
                b.set(x, 1, z, B("short_grass"))
    connector(b, 20, 1, C, "east", P_PATHS)
    connector(b, 0, 1, C, "west", P_PATHS)
    connector(b, C, 1, 0, "north", P_PATHS)
    connector(b, C, 1, 20, "south", P_PATHS)
    return b


# ------------------------------------------------------------------ satellites
def ruin_gatehouse():
    b = Build(f"{NAME}_gatehouse", 11, 9, 8, seed=1847311)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    for x in range(0, 11):
        for z in range(1, 8):
            if rng.random() < 0.55:
                b.set(x, 0, z, B(rng.choice(["expanse:limestone", "gravel", "coarse_dirt", "cobblestone"])))
    for x0 in (1, 7):                 # two gate towers 3x3, broken at different heights
        h = 6 if x0 == 1 else 4
        for x in range(x0, x0 + 3):
            for z in range(3, 6):
                for y in range(1, h + 1 - (1 if (x + z) % 3 == 0 else 0)):
                    b.set(x, y, z, wall_block(rng))
    for x in range(4, 7):             # the arch between them
        b.set(x, 4, 4, wall_block(rng) if x != 6 else B("expanse:mossy_limestone"))
    b.set(4, 3, 4, stairs(LB, "east", "top"))
    b.set(6, 3, 4, stairs(LB, "west", "top"))
    b.air(4, 1, 3, 6, 2, 5)
    for x in range(4, 7):
        b.set(x, 1, 6, B("iron_bars"))                 # the fallen portcullis lies in the passage
    b.set(5, 1, 6, AIR)
    for (x, z) in ((3, 6), (8, 2), (2, 2), (9, 6)):
        b.set(x, 1, z, rng.choice([slab(LB), B("expanse:mossy_limestone"), B("gravel")]))
    b.set(5, 0, 5, B("gravel"))
    b.set(5, 0, 6, B("gravel"))
    vines_on(b, rng, 0.08, ymin=2, max_len=3)
    return b


def ruin_wall():
    b = Build(f"{NAME}_wall", 13, 8, 7, seed=1847313)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    for x in range(0, 13):
        h = int(5 + 1.5 * math.sin(x * 0.9) - (3 if 5 <= x <= 7 else 0))
        for z in (3, 4):
            for y in range(0, max(1, h) + 1):
                b.set(x, y, z, wall_block(rng) if y else B("expanse:limestone"))
        if h >= 4 and x % 2 == 0:
            b.set(x, h + 1, 3, wall_block(rng))
        if h >= 4 and x % 3 == 1:
            b.set(x, 3, 3, AIR)                       # arrow slits
    for x in range(4, 9):                              # the breach and its rubble (archaeology gravel)
        for z in (1, 2, 5, 6):
            if rng.random() < 0.7:
                b.set(x, 0, z, B("gravel") if rng.random() < 0.5 else B("expanse:limestone"))
                b.set(x, 1, z, rng.choice([slab(LB), B("expanse:mossy_limestone"), B("short_grass"),
                                           stairs(LB, "north")]))
    b.set(10, 1, 2, B("barrel", facing="up"))
    b.set(2, 1, 2, B("short_grass"))
    vines_on(b, rng, 0.08, ymin=2, max_len=3)
    return b


def ruin_barracks():
    b = Build(f"{NAME}_barracks", 11, 7, 11, seed=1847315)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    for x in range(1, 10):
        for z in range(1, 10):
            edge = x in (1, 9) or z in (1, 9)
            b.set(x, 0, z, B(rng.choice(["expanse:limestone_bricks", "cobblestone", "gravel", "coarse_dirt"])))
            if edge:
                h = 3 + ((x * 5 + z * 3) % 3) - (2 if (x, z) in ((9, 4), (9, 5), (6, 9)) else 0)
                for y in range(1, h + 1):
                    b.set(x, y, z, wall_block(rng))
            else:
                b.set(x, 1, z, AIR)
                b.set(x, 2, z, AIR)
    b.air(5, 1, 1, 5, 2, 1)
    b.bed(2, 1, 7, "south", "brown")
    b.set(3, 1, 8, B("barrel", facing="up"))
    b.campfire(6, 1, 6)                                # scavengers' fire
    b.set(7, 1, 6, B("spruce_log", axis="z"))
    b.set(6, 1, 4, B("spruce_log", axis="x"))
    b.barrel(8, 1, 8, "up", LOOT)
    b.set(2, 1, 2, B("cobweb"))
    b.set(8, 1, 2, B("crafting_table"))
    for x in range(2, 9):                              # charred roof beams
        if rng.random() < 0.5:
            b.set(x, 4, 5, B("stripped_spruce_log", axis="x"))
    vines_on(b, rng, 0.06, ymin=2, max_len=2)
    return b


def ruin_training():
    b = Build(f"{NAME}_training", 11, 4, 10, seed=1847317)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    for x in range(1, 10):
        for z in range(1, 9):
            if x in (1, 9) or z in (1, 8):
                if not (z == 1 and x in (4, 5, 6)) and rng.random() < 0.85:
                    b.set(x, 1, z, B("spruce_fence"))
            elif rng.random() < 0.6:
                b.set(x, 0, z, B(rng.choice(["coarse_dirt", "dirt_path", "gravel"])))
    for (x, z, yaw) in ((3, 5, 180.0), (7, 6, 200.0)):
        b.set(x, 1, z, B("hay_block", axis="y"))
        b.armor_stand(x, 2, z, yaw=yaw, equipment={"head": "carved_pumpkin" if x == 3 else "leather_helmet"})
    b.set(5, 1, 7, B("target"))
    b.set(5, 2, 7, B("target"))
    b.set(2, 1, 7, B("hay_block", axis="x"))
    b.set(2, 1, 6, B("hay_block", axis="z"))
    b.set(8, 1, 2, B("barrel", facing="up"))
    b.item_frame(8, 2, 2, "up", "arrow")
    return b


def ruin_well():
    b = Build(f"{NAME}_well", 7, 6, 8, seed=1847319)
    rng = b.rng
    piece_in(b, 3, 1, 0, "north")
    for x in range(1, 6):
        for z in range(2, 7):
            b.set(x, 0, z, B(rng.choice(["cobblestone", "gravel", "expanse:limestone"])))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            x, z = 3 + dx, 4 + dz
            if dx or dz:
                b.set(x, 1, z, B("expanse:limestone_brick_wall") if dx and dz else wall_block(rng))
            else:
                b.set(x, 0, z, B("water", level=0))
                b.set(x, 1, z, AIR)
    b.set(2, 2, 3, B("spruce_fence"))
    b.set(4, 2, 5, B("spruce_fence"))
    b.set(2, 3, 3, slab("spruce"))
    b.set(4, 3, 5, slab("spruce"))
    b.set(3, 1, 7, B("cauldron"))
    return b


def pools():
    rng_paths = __import__("random").Random(1847321)
    return {
        "start": [Piece(keep("fallen"), 1, "watchtower"), Piece(keep("breach"), 1, "watchtower")],
        "paths": [Piece(path_piece(f"{NAME}_path_short", 4, P_RUINS, rng_paths,
                                   mats=(("gravel", 3), ("coarse_dirt", 3), ("cobblestone", 1))), 2, "watchtower",
                        "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_long", 8, P_RUINS, rng_paths,
                                   mats=(("gravel", 3), ("coarse_dirt", 3), ("dirt_path", 2))), 2, "watchtower",
                        "terrain_matching"),
                  Empty(3)],
        "ruins": [Piece(ruin_gatehouse(), 2, "watchtower"), Piece(ruin_wall(), 3, "watchtower"),
                  Piece(ruin_barracks(), 2, "watchtower"), Piece(ruin_training(), 2, "watchtower"),
                  Piece(ruin_well(), 1, "watchtower"), Empty(1)],
    }
