"""Ruined caravanserai - a courtyard inn of the old caravan road across the amber steppe (LANDMARK).

start  khan (two variants: half-ruined / one wing fallen)
  A square two-storey courtyard inn of sandstone with terracotta bands (the desert pyramid's
  palette): a tall gateway with a pointed arch, an arcade round the courtyard on both floors,
  rooms behind it - stables, storerooms, the kitchen, guest rooms and the keeper's room with
  his road book. A well in the courtyard, troughs, tethering posts and a camel. The north-east
  corner has fallen in; sand has drifted into the arcades (and under it, sometimes, something
  to brush up).
paths -> outskirts (rigid satellites): the caravan camp with tents and camels outside the gate;
  elsewhere a ruined watch post / roadside tombs / the dry market
cistern (down the courtyard well): a vaulted cistern, half flooded, the caravan master's strongbox
"""
import math
import random

import nbt
from blocks import AIR, B
from builder import Build, prune_unsupported
from furnish import chair, rug, shelf_items, table
from loot import SHERDS, book, empty, item as li, pool, table as loot_table
from pieces import Empty, Piece
from processors import STEPPE, aged_wood, archaeology, plist, rot, rule
from small_data import SAND_WEATHER
from smallkit import a_tent, cart, meal_fire
from terrain_paths import connector, path_piece, piece_in
from townkit import (door, drop_orphans, goods_stack, hanging_lamp, journal, lamp, lore_entry, rect_ring, slab, stairs, trapdoor,
                     wall_torch, win)

NAME = "caravanserai"
STRUCTURE = dict(biomes=["amber_steppe"], spacing=44, separation=16, size=4, max_distance=72)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
SUPPLIES = f"expanse:chests/{NAME}_supplies"
DIG = f"expanse:chests/{NAME}_archaeology"
P_PATHS = f"expanse:{NAME}/paths"
P_PATHS_GATE = f"expanse:{NAME}/paths_gate"
P_OUT = f"expanse:{NAME}/outskirts"
P_GATE = f"expanse:{NAME}/gate"
P_CISTERN = f"expanse:{NAME}/cistern"
J_CISTERN = "expanse:caravanserai_cistern"
PROC = NAME
AC = "acacia"
SS = "sandstone"
CS = "cut_sandstone"

BOOK = {
    "title": "Road Book of the Khan", "author": "Keeper Yusuf",
    "pages": [
        "Twelve days from the coast to this gate, eight more to the karst. The road is marked by our "
        "towers; the towers are marked by their bones now. Water first, then fodder, then gossip.",
        "Caravans this season: three. Last season: nine. The wells along the road are drying one by one, "
        "and the merchants go round by the sea. The camels do not mind. I do.",
        "When the last caravan leaves I will seal the strongbox in the cistern ledge, below the well, "
        "where only those who know to climb down a dry well will ever look.",
    ]}

DATA = {
    "worldgen/processor_list": {
        PROC: plist(SAND_WEATHER, aged_wood("acacia"), STEPPE,
                    [rule("cobweb", 0.3, "air"), rule("orange_terracotta", 0.08, "terracotta")],
                    rot(0.95, ["sandstone", "cut_sandstone", "sandstone_slab", "sandstone_stairs", "terracotta"]),
                    archaeology("sand", "suspicious_sand", DIG, 4)),
    },
    "loot_table/chests": {
        NAME: loot_table(
            NAME,
            pool((3, 6), li("bread", 8, (1, 3)), li("wheat", 8, (2, 6)), li("leather", 6, (1, 3)),
                 li("string", 6, (1, 4)), li("red_dye", 4, (1, 3)), li("orange_dye", 4, (1, 3)),
                 li("gold_nugget", 8, (2, 8)), li("expanse:baobab_sapling", 4, (1, 2)), li("candle", 5, (1, 3)),
                 li("rabbit_hide", 4, (1, 3)), li("lead", 3), li("map", 2)),
            pool(1, empty(6), li("emerald", 3, (1, 3)), li("saddle", 2), li("bundle", 1))),
        f"{NAME}_hidden": loot_table(
            f"{NAME}_hidden",
            pool((3, 6), li("emerald", 10, (3, 9)), li("gold_ingot", 8, (2, 6)), li("gold_nugget", 6, (4, 12)),
                 li("lapis_lazuli", 5, (3, 9)), li("diamond", 2, (1, 2)), li("golden_horse_armor", 2),
                 li("iron_horse_armor", 3), li("saddle", 3), li("spyglass", 2)),
            pool((1, 2), empty(2), book(4), li("golden_apple", 3), li("music_disc_far", 1), li("name_tag", 2)),
            pool(1, empty(1), lore_entry(BOOK, 2))),
        f"{NAME}_supplies": loot_table(
            f"{NAME}_supplies",
            pool((2, 5), li("wheat", 8, (3, 9)), li("hay_block", 3, (1, 2)), li("bread", 6, (1, 4)),
                 li("dried_kelp", 3, (2, 6)), li("melon_slice", 5, (2, 6)), li("sugar", 4, (1, 4)),
                 li("string", 4, (2, 5)), li("leather", 3, (1, 3)), li("white_wool", 4, (1, 3)))),
        f"{NAME}_archaeology": loot_table(
            f"{NAME}_archaeology",
            pool(1, li("gold_nugget", 4), li("emerald", 2), li("bone", 2), li("arms_up_pottery_sherd", 2),
                 li("prize_pottery_sherd", 2), li("snort_pottery_sherd", 2), li("burn_pottery_sherd", 1),
                 li("brick", 2), li("lapis_lazuli", 2), li("diamond", 1)), kind="archaeology"),
    },
}

VARIANTS = {"standing": dict(collapse=0.55, wing=False), "fallen": dict(collapse=0.85, wing=True)}


def sandstone(rng):
    return B(rng.choice([SS, SS, CS, SS, "smooth_sandstone"]))


def band(y):
    """Terracotta bands in the walls, as the desert pyramid has them."""
    return {3: "orange_terracotta", 8: "terracotta"}.get(y)


# ------------------------------------------------------------------ the khan
X0, X1, Z0, Z1 = 5, 37, 5, 37          # outer wall
D = 6                                   # depth of the ring of rooms + arcade
GY, UY, RY = 0, 5, 10                   # ground course, upper floor, roof


def khan(variant, seed):
    V = VARIANTS[variant]
    b = Build(f"{NAME}_khan_{variant}", 44, 18, 44, seed=seed)
    rng = b.rng
    for x in range(44):
        for z in range(44):
            edge = min(x, z, 43 - x, 43 - z)
            if edge == 0 and rng.random() < 0.5:
                continue
            b.set(x, 0, z, B(rng.choice(["coarse_dirt", "sand", "coarse_dirt", "grass_block"])))
    ring_block(b, rng)
    gateway(b, rng)
    rooms(b, rng)
    courtyard(b, rng)
    collapse(b, rng, V)
    prune_unsupported(b, keep=lambda st: st.short == "jigsaw")
    drop_orphans(b)
    drifts(b, rng)
    connector(b, 21, 1, 43, "south", P_PATHS_GATE)        # the caravan camps by the gate
    connector(b, 0, 1, 21, "west", P_PATHS)
    connector(b, 43, 1, 21, "east", P_PATHS)
    connector(b, 21, 1, 0, "north", P_PATHS)
    for (x, z) in ((21, 43), (0, 21), (43, 21), (21, 0)):
        b.set(x, 0, z, B("coarse_dirt"))
    for z in range(38, 43):
        b.set(21, 0, z, B(rng.choice(["coarse_dirt", "gravel", "dirt_path"])))
    return b


def in_ring(x, z, inset=0):
    return X0 + inset <= x <= X1 - inset and Z0 + inset <= z <= Z1 - inset


def ring_block(b, rng):
    """Outer wall, the room ring (floors, cross walls), the courtyard arcades on both storeys, the roof."""
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if not in_ring(x, z) or in_ring(x, z, D + 1):
                continue
            b.set(x, GY, z, B(rng.choice([SS, "smooth_sandstone", SS])))
            for y in range(1, RY):
                b.set(x, y, z, AIR)
            b.set(x, UY, z, B(f"{AC}_planks") if not in_ring(x, z, D - 1) else B("smooth_sandstone"))
            b.set(x, RY, z, B("smooth_sandstone") if (x + z) % 7 else B(CS))
    # outer wall with bands and a crenellated parapet
    for (x, z) in rect_ring(X0, Z0, X1, Z1):
        for y in range(1, RY + 1):
            t = band(y)
            b.set(x, y, z, B(t) if t else sandstone(rng))
        corner = x in (X0, X1) and z in (Z0, Z1)
        b.set(x, RY + 1, z, B(CS) if corner or (x + z) % 2 == 0 else slab(SS))
    # inner wall of the rooms (offset D-1) and the arcade pillars on the courtyard line (offset D)
    for (x, z) in rect_ring(X0 + D - 1, Z0 + D - 1, X1 - D + 1, Z1 - D + 1):
        for y in range(1, RY):
            if y != UY:
                b.set(x, y, z, sandstone(rng))
    arcade(b, rng, X0 + D, Z0 + D, X1 - D, Z1 - D)
    # cross walls dividing the rooms (every 5 along each side)
    for k in range(X0 + 5, X1 - 1, 5):
        for (x, z0_, z1_) in ((k, Z0 + 1, Z0 + D - 2), (k, Z1 - D + 2, Z1 - 1)):
            for z in range(z0_, z1_ + 1):
                for y in range(1, RY):
                    if y != UY:
                        b.set(x, y, z, sandstone(rng))
    for k in range(Z0 + 5, Z1 - 1, 5):
        for (z, x0_, x1_) in ((k, X0 + 1, X0 + D - 2), (k, X1 - D + 2, X1 - 1)):
            for x in range(x0_, x1_ + 1):
                for y in range(1, RY):
                    if y != UY:
                        b.set(x, y, z, sandstone(rng))
    # corner stair towers: a ladder shaft up to the roof in the four corners of the arcade
    for (x, z, f) in ((X0 + D, Z0 + D, "south"), (X1 - D, Z1 - D, "north")):
        for y in range(1, RY):
            b.set(x, y, z, B("ladder", facing=f))
        b.set(x, UY, z, B("ladder", facing=f))
        b.set(x, RY, z, trapdoor(AC, f, "top", False))


def arcade(b, rng, ax0, az0, ax1, az1):
    """Pointed arches round the courtyard: pillars every 3, arch stairs over the openings, both storeys,
    with a sandstone-wall balustrade on the upper gallery."""
    for (x, z) in rect_ring(ax0, az0, ax1, az1):
        on_x = z in (az0, az1)
        k = (x - ax0) if on_x else (z - az0)
        corner = x in (ax0, ax1) and z in (az0, az1)
        pillar = corner or k % 3 == 0
        inward = ("south" if z == az0 else "north") if on_x else ("east" if x == ax0 else "west")
        for (fy, top) in ((GY, UY), (UY, RY)):
            for y in range(fy + 1, top):
                if pillar:
                    b.set(x, y, z, B(CS) if y > fy + 1 else B("chiseled_sandstone") if fy == GY else B(CS))
                else:
                    b.set(x, y, z, AIR)
            if not pillar:
                # the arch: upside-down stairs springing from both pillars
                left = (x - 1, z) if on_x else (x, z - 1)
                right = (x + 1, z) if on_x else (x, z + 1)
                lp = (left[0] - ax0) % 3 == 0 if on_x else (left[1] - az0) % 3 == 0
                f = ("east" if lp else "west") if on_x else ("south" if lp else "north")
                b.set(x, top - 1, z, stairs(SS, f, "top"))
            b.set(x, top, z, B(CS) if top == UY else B("smooth_sandstone"))
        if not pillar:
            b.set(x, UY + 1, z, B("sandstone_wall"))
        b.set(x, RY + 1, z, slab(SS) if (x + z) % 2 else B("sandstone_wall"))
    b.set(ax0, UY + 2, az0, B("lantern"))


def gateway(b, rng):
    """The tall gateway (pishtaq) in the south wall: a pointed portal through the room ring."""
    gx = 21
    zs = range(Z1 - D, Z1 + 1)
    for z in zs:
        for x in range(gx - 2, gx + 3):
            for y in range(1, 6):
                b.set(x, y, z, AIR)
            b.set(x, GY, z, B(rng.choice(["smooth_sandstone", CS])))
        for x in (gx - 2, gx + 2):
            b.set(x, 5, z, stairs(SS, "east" if x < gx else "west", "top"))
        for x in (gx - 1, gx, gx + 1):
            b.set(x, 6, z, B(CS))
        b.set(gx, 5, z, AIR)
        b.set(gx - 1, 6, z, stairs(SS, "east", "top"))
        b.set(gx + 1, 6, z, stairs(SS, "west", "top"))
        b.set(gx, 6, z, AIR)
        b.set(gx, 7, z, B(CS))
    # the raised frontage, framed in blue and orange terracotta, a chiseled keystone, merlons
    for x in range(gx - 4, gx + 5):
        for y in range(1, 15):
            if b.get(x, y, Z1) is not None and b.get(x, y, Z1).short == "air" and y < 8:
                continue
            edge = x in (gx - 4, gx + 4) or y == 14
            trim = x in (gx - 3, gx + 3) or y == 13
            b.set(x, y, Z1, B(CS) if edge else B("light_blue_terracotta") if trim and y > 1 else
                  B("orange_terracotta") if (x + y) % 4 == 0 and y > 8 else sandstone(rng))
        b.set(x, 15, Z1, B(CS) if x % 2 else slab(SS))
    for y in range(1, 8):
        for x in range(gx - 2, gx + 3):
            if b.get(x, y, Z1 - 1) is not None and b.get(x, y, Z1 - 1).short == "air":
                b.set(x, y, Z1, AIR)
    b.set(gx, 8, Z1 + 0, B("chiseled_sandstone"))
    for x in (gx - 2, gx + 2):                                # the old gate leaves, open and sagging
        b.set(x, 1, Z1 - 1, trapdoor(AC, "south", "bottom", True))
        b.set(x, 2, Z1 - 1, trapdoor(AC, "south", "bottom", True))
    for x in (gx - 3, gx + 3):
        wall_torch(b, x, 3, Z1 + 1, "south")


def room_cells(side, i):
    """Interior cells (x0, z0, x1, z1) of room i along a side of the ring."""
    if side == "north":
        return (X0 + 1 + 5 * i, Z0 + 1, X0 + 4 + 5 * i, Z0 + D - 2)
    if side == "south":
        return (X0 + 1 + 5 * i, Z1 - D + 2, X0 + 4 + 5 * i, Z1 - 1)
    if side == "west":
        return (X0 + 1, Z0 + 1 + 5 * i, X0 + D - 2, Z0 + 4 + 5 * i)
    return (X1 - D + 2, Z0 + 1 + 5 * i, X1 - 1, Z0 + 4 + 5 * i)


def rooms(b, rng):
    """Doors from the arcade into every room, and what each room holds."""
    kinds = {
        "north": ["store", "store", "kitchen", "store", "guest", "guest"],
        "west": ["stable", "stable", "stable", "stable", "store", "guest"],
        "east": ["guest", "guest", "keeper", "store", "guest", "store"],
        "south": ["stable", "guest", "gate", "gate", "store", "guest"],
    }
    for side, ks in kinds.items():
        for i, kind in enumerate(ks):
            x0, z0, x1, z1 = room_cells(side, i)
            if x1 >= X1 or z1 >= Z1 or x0 <= X0 or z0 <= Z0:
                continue
            if kind == "gate" or (side == "south" and 18 <= x0 <= 24):
                continue
            # door in the inner wall, onto the arcade
            cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
            for fy in (GY, UY):
                if side == "north":
                    dp, f = (cx, Z0 + D - 1), "south"
                elif side == "south":
                    dp, f = (cx, Z1 - D + 1), "north"
                elif side == "west":
                    dp, f = (X0 + D - 1, cz), "east"
                else:
                    dp, f = (X1 - D + 1, cz), "west"
                b.set(dp[0], fy + 1, dp[1], AIR)
                b.set(dp[0], fy + 2, dp[1], AIR)
                if fy == UY or kind != "stable":
                    door(b, dp[0], fy + 1, dp[1], AC, f)
                furnish(b, rng, kind if fy == GY else ("guest" if kind in ("stable", "store") else kind),
                        x0, z0, x1, z1, fy, f, dp)
            if side in ("west", "east") and i % 2 == 0:
                win(b, X0 if side == "west" else X1, UY + 2, cz, side, 1)


def furnish(b, rng, kind, x0, z0, x1, z1, fy, door_f, dp):
    y = fy + 1
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[door_f]
    inside = (dp[0] - dx, dp[1] - dz)
    cells = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]
    far = [c for c in cells if (c[0] in (x0, x1) or c[1] in (z0, z1)) and c != inside]
    rng.shuffle(far)
    if kind == "stable":
        for (x, z) in cells:
            b.set(x, fy, z, B(rng.choice(["coarse_dirt", "dirt", "sand"])))
        (ax, az), (bx, bz) = far[0], far[1]
        b.set(ax, y, az, B("hay_block", axis="y"))
        b.set(bx, y, bz, B("water_cauldron", level=rng.choice([1, 2, 3])))
        if rng.random() < 0.5:
            b.mob((x0 + x1) // 2, y, (z0 + z1) // 2, rng.choice(["horse", "donkey"]), yaw=rng.choice([0.0, 90.0]))
        b.set(far[2][0], y, far[2][1], B("hay_block", axis="x"))
    elif kind == "store":
        n = 0
        for (x, z) in far[:6]:
            b.barrel(x, y, z, "up", SUPPLIES if n == 0 else None)
            if rng.random() < 0.3:
                b.set(x, y + 1, z, B("barrel", facing="up"))
            n += 1
        if len(far) > 6 and rng.random() < 0.5:
            b.chest(far[6][0], y, far[6][1], "north", LOOT)
        if len(far) > 7:
            b.pot(far[7][0], y, far[7][1], "north", ("brick", rng.choice(SHERDS) + "_pottery_sherd", "brick", "brick"))
    elif kind == "kitchen":
        (ax, az) = far[0]
        meal_fire(b, ax, y, az, foods=("mutton", "potato"), lit=False, done=(100, 0, 0, 0))
        b.set(far[1][0], y, far[1][1], B("smoker", facing="south"))
        b.set(far[2][0], y, far[2][1], B("water_cauldron", level=2))
        b.barrel(far[3][0], y, far[3][1], "up", SUPPLIES)
        b.chest(far[4][0], y, far[4][1], "south", LOOT)
        for (x, z) in far[5:8]:
            b.pot(x, y, z, "north", ("brick", "brick", "brick", "brick"))
    elif kind == "guest":
        for (x, z) in cells:
            if b.get(x, y, z) is None or b.get(x, y, z).short == "air":
                if rng.random() < 0.55:
                    b.set(x, y, z, B(rng.choice(["orange_carpet", "red_carpet", "orange_carpet", "yellow_carpet"])))
        f = {"south": "north", "north": "south", "east": "west", "west": "east"}[door_f]
        # a bed against the back wall
        if door_f in ("south", "north"):
            bz = z0 if door_f == "south" else z1
            b.bed(x0, y, bz + (1 if door_f == "south" else -1), "north" if door_f == "south" else "south", "orange")
        else:
            bx = x0 if door_f == "east" else x1
            b.bed(bx + (1 if door_f == "east" else -1), y, z0, "west" if door_f == "east" else "east", "orange")
        if rng.random() < 0.5:
            b.set(x1, y, z1, B("loom", facing="north"))
        if rng.random() < 0.4:
            b.chest(x1, y, z0, "west", LOOT)
        else:
            b.barrel(x1, y, z0, "up")
    elif kind == "keeper":
        b.lectern(x0, y, z0, "east", journal(BOOK))
        b.set(x0, y, z1, B("cartography_table"))
        b.bed(x1, y, z0, "south", "red")
        b.chest(x1, y, z1, "west", LOOT)
        rug(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y, "red", "orange")
        b.item_frame(x0 + 1, y + 2, z0, "south", "map")
    hanging_lamp(b, (x0 + x1) // 2, fy + 4 if fy == GY else RY - 1, (z0 + z1) // 2)


def courtyard(b, rng):
    c0, c1 = X0 + D + 1, X1 - D - 1
    for x in range(c0, c1 + 1):
        for z in range(c0, c1 + 1):
            b.set(x, GY, z, B(rng.choice(["smooth_sandstone", "sandstone", "smooth_sandstone", "coarse_dirt",
                                          "sand"])))
            for y in range(1, 5):
                b.set(x, y, z, AIR)
    cx = cz = 21
    # the well: a sandstone curb on a stepped platform; the shaft goes down to the cistern
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            b.set(x, GY, z, B(CS))
    for (x, z) in rect_ring(cx - 1, cz - 1, cx + 1, cz + 1):
        b.set(x, 1, z, B(CS) if (x + z) % 2 == 0 else B("sandstone_wall"))
    b.set(cx, 1, cz, AIR)
    b.set(cx, GY, cz, AIR)
    for (x, z) in ((cx - 1, cz - 1), (cx + 1, cz + 1)):
        b.set(x, 2, z, B(f"{AC}_fence"))
        b.set(x, 3, z, B(f"{AC}_fence"))
    for (x, z) in ((cx - 1, cz - 1), (cx, cz - 1), (cx + 1, cz - 1), (cx - 1, cz), (cx, cz), (cx + 1, cz),
                   (cx - 1, cz + 1), (cx, cz + 1), (cx + 1, cz + 1)):
        b.set(x, 4, z, slab(AC))
    b.set(cx, 3, cz, B("iron_chain", axis="y"))
    b.set(cx, 2, cz, B("iron_chain", axis="y"))
    b.jigsaw(cx, GY, cz, "down", target=J_CISTERN, pool=P_CISTERN,
             final="minecraft:ladder[facing=north,waterlogged=false]", top="north")
    # troughs, tethering posts, a resting camel, a mastaba with carpets for the traders
    for x in range(c0 + 1, c0 + 5):
        b.set(x, 1, c1 - 1, B("water_cauldron", level=rng.choice([1, 2])))
    for (x, z) in ((c0 + 2, c0 + 2), (c1 - 2, c0 + 2), (c1 - 2, c1 - 3)):
        b.set(x, 1, z, B(f"{AC}_fence"))
        b.set(x, 2, z, B(f"{AC}_fence"))
    b.mob(c1 - 3, 1, c0 + 4, "camel", yaw=45.0)
    for x in range(c1 - 5, c1 + 1):
        for z in range(c1 - 2, c1 + 1):
            b.set(x, 1, z, slab(SS, "bottom") if x in (c1 - 5,) or z == c1 - 2 else B("smooth_sandstone"))
            if not (x in (c1 - 5,) or z == c1 - 2):
                b.set(x, 2, z, B(rng.choice(["orange_carpet", "red_carpet", "white_carpet"])))
    b.set(c0 + 4, 1, c0 + 1, B("hay_block", axis="y"))
    b.set(c0 + 5, 1, c0 + 1, B("hay_block", axis="x"))
    cart(b, c0 + 1, cz + 3, "z", AC, rng, broken_corner=2, y=1)
    meal_fire(b, cx + 4, 1, cz - 4, foods=("mutton",), lit=False, done=(50, 0, 0, 0))


def collapse(b, rng, V):
    """The north-east corner has come down: the roof and upper storey are gone, the walls broken off
    ragged, the fallen stone heaped on the floor. With `wing`, the whole east range has fallen."""
    x_lo = 26 if not V["wing"] else X1 - D
    z_hi = 16 if not V["wing"] else Z1 - D
    cx, cz = X1, Z0
    for x in range(x_lo, X1 + 1):
        for z in range(Z0, z_hi + 1):
            d = math.hypot((x - cx) / (X1 - x_lo + 1), (z - cz) / (z_hi - Z0 + 1))
            if d > 1.0:
                continue
            top = int(RY - (1.0 - d) * (RY - 2) * V["collapse"] - rng.random() * 2)
            for y in range(max(2, top), RY + 3):
                if b.get(x, y, z) is not None:
                    b.set(x, y, z, None)
            if b.get(x, 1, z) is not None and b.get(x, 1, z).short == "air" and rng.random() < 0.45:
                b.set(x, 1, z, B(rng.choice([SS, CS, "sand", "sandstone_slab"]))
                      if rng.random() < 0.8 else B("sandstone_stairs", facing=rng.choice(["north", "east"])))
                if rng.random() < 0.25 and b.get(x, 2, z) is not None and b.get(x, 2, z).short == "air":
                    b.set(x, 2, z, slab(SS))
    # loose planks of the fallen floor
    for _ in range(8):
        x, z = rng.randint(x_lo, X1 - 1), rng.randint(Z0 + 1, z_hi)
        if b.get(x, 1, z) is not None and b.get(x, 1, z).short == "air":
            b.set(x, 1, z, slab(AC))


def drifts(b, rng):
    """Sand blown into the arcades and against the outer walls (some of it will hide finds)."""
    for (x, z) in rect_ring(X0 + D, Z0 + D, X1 - D, Z1 - D):
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, z + dz)
            if b.get(p[0], 1, p[1]) is not None and b.get(p[0], 1, p[1]).short == "air" and rng.random() < 0.18:
                b.set(p[0], 1, p[1], B("sand"))
    for (x, z) in rect_ring(X0 - 1, Z0 - 1, X1 + 1, Z1 + 1):
        if rng.random() < 0.4:
            b.set(x, 1, z, B("sand"))
            b.set(x, 0, z, B("sand"))


# ------------------------------------------------------------------ outskirts
def out_site(name, sx, sy, sz, seed):
    b = Build(name, sx, sy, sz, seed=seed)
    rng = b.rng
    piece_in(b, sx // 2, 1, 0, "north")
    for x in range(sx):
        for z in range(1, sz):
            if rng.random() < 0.75:
                b.set(x, 0, z, B(rng.choice(["coarse_dirt", "sand", "coarse_dirt", "grass_block"])))
    for z in range(0, 3):
        b.set(sx // 2, 0, z, B("coarse_dirt"))
    return b, rng


def caravan_camp():
    b, rng = out_site(f"{NAME}_camp", 21, 8, 19, 1849431)
    a_tent(b, 2, 4, 5, "z", "orange", wide=True)
    a_tent(b, 13, 8, 5, "z", "white", wide=True)
    b.bed(4, 1, 6, "south", "orange")
    b.bed(15, 1, 10, "south", "white")
    b.chest(4, 1, 8, "north", LOOT)
    b.barrel(16, 1, 12, "north", SUPPLIES)
    meal_fire(b, 10, 1, 6, foods=("mutton", "mutton"), lit=True, done=(200, 100, 0, 0))
    for (x, z) in ((9, 4), (11, 4), (10, 8)):
        b.set(x, 1, z, B(f"stripped_{AC}_log", axis="x" if z == 8 else "z"))
    for (x, z, yaw) in ((7, 15, 30.0), (12, 16, 200.0)):
        b.mob(x, 1, z, "camel", yaw=yaw)
    for x in range(3, 18, 4):
        b.set(x, 1, 18, B(f"{AC}_fence"))
    for x in range(3, 18):
        b.set(x, 2, 18, B(f"{AC}_fence"))
    cart(b, 16, 2, "z", AC, rng, y=1)
    b.set(16, 3, 3, B("white_wool"))
    b.set(17, 3, 3, B("barrel", facing="up"))
    return b


def watch_post():
    b, rng = out_site(f"{NAME}_watch_post", 11, 13, 12, 1849433)
    x0, x1, z0, z1 = 3, 7, 4, 8
    for (x, z) in rect_ring(x0, z0, x1, z1):
        h = 10 - int(rng.random() * 4) if (x + z) % 3 else 11
        for y in range(1, h):
            b.set(x, y, z, B(band(y)) if band(y) else sandstone(rng))
    b.air(x0 + 1, 1, z0 + 1, x1 - 1, 9, z1 - 1)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, B(CS))
    for y in range(1, 9):
        b.set(x0 + 1, y, z1 - 1, B("ladder", facing="north"))
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if (x, z) != (x0 + 1, z1 - 1):
                b.set(x, 5, z, B(f"{AC}_planks") if rng.random() < 0.7 else AIR)
    b.set(5, 1, z0, AIR)
    b.set(5, 2, z0, AIR)
    b.set(x1 - 1, 6, z0 + 1, B("barrel", facing="up"))
    b.chest(x1 - 1, 1, z1 - 1, "west", LOOT)
    b.set(x1 - 1, 1, z0 + 1, B("sand"))
    b.set(x0 + 1, 1, z0 + 1, B("sand"))
    for (x, z) in ((1, 10), (9, 9), (9, 2)):
        b.set(x, 1, z, B(rng.choice([SS, CS])))
        b.set(x, 0, z, B("sand"))
    prune_unsupported(b, keep=lambda st: st.short == "jigsaw")
    return b


def tombs():
    b, rng = out_site(f"{NAME}_tombs", 15, 8, 13, 1849435)
    for (x0, z0) in ((2, 4), (8, 4), (5, 9)):
        for x in range(x0, x0 + 4):
            for z in range(z0, z0 + 3):
                b.set(x, 0, z, B(CS))
                b.set(x, 1, z, B("smooth_sandstone") if x in (x0, x0 + 3) or z in (z0, z0 + 2) else B("sand"))
            b.set(x, 2, z0 + 1, slab(SS) if rng.random() < 0.8 else AIR)
        b.set(x0, 2, z0, B("chiseled_sandstone"))
        b.pot(x0 + 3, 2, z0, "north", ("brick", rng.choice(["mourner", "skull", "heart"]) + "_pottery_sherd",
                                       "brick", "brick"))
    b.set(12, 1, 11, B("skeleton_skull", rotation=6))
    b.barrel(1, 1, 11, "up")
    b.set(13, 1, 2, B("dead_bush"))
    b.set(13, 0, 2, B("sand"))
    for (x, z) in ((7, 11), (3, 2)):
        b.set(x, 1, z, B("candle", candles=2, lit=False))
        b.set(x, 0, z, B(CS))
    return b


def dry_market():
    b, rng = out_site(f"{NAME}_market", 19, 8, 15, 1849437)
    for (x0, cloth) in ((2, "orange"), (8, "red"), (14, "white")):
        for x in range(x0, x0 + 3):
            b.set(x, 1, 5, slab(SS, "top") if x != x0 + 1 else B("barrel", facing="north"))
            b.set(x, 0, 6, B(CS))
        for x in (x0, x0 + 2):
            for y in (2, 3):
                b.set(x, y, 5, B(f"{AC}_fence") if rng.random() < 0.85 else AIR)
            for y in (1, 2, 3):
                b.set(x, y, 7, B(f"{AC}_fence") if not (y == 3 and rng.random() < 0.4) else AIR)
        for x in range(x0, x0 + 3):
            if rng.random() < 0.7:
                b.set(x, 4, 6, B(f"{cloth}_wool_slab", type="bottom"))
        b.pot(x0 + 1, 2, 5, "north", ("brick", "brick", rng.choice(["prize", "plenty", "sheaf"]) + "_pottery_sherd",
                                      "brick"))
    for (x, z) in ((5, 10), (11, 11), (16, 10)):
        b.set(x, 1, z, B("barrel", facing="up"))
    b.chest(9, 1, 12, "north", LOOT)
    for (x, z) in ((2, 12), (17, 13), (8, 9)):
        b.set(x, 1, z, B("sand"))
        b.set(x, 0, z, B("sand"))
    lamp(b, 9, 1, 2, AC, 2, torch=True)
    prune_unsupported(b, keep=lambda st: st.short == "jigsaw")
    return b


# ------------------------------------------------------------------ cistern
def cistern():
    b = Build(f"{NAME}_cistern", 13, 9, 13, seed=1849441)
    rng = b.rng
    for x in range(13):
        for z in range(13):
            for y in range(9):
                b.set(x, y, z, B(rng.choice([SS, SS, "sand", "stone"])))
    b.air(1, 1, 1, 11, 5, 11)
    for x in range(1, 12):
        for z in range(1, 12):
            b.set(x, 0, z, B(rng.choice([CS, "smooth_sandstone"])))
            if 3 <= x <= 9 and 3 <= z <= 9:
                b.set(x, 1, z, B("water", level=0))
    # vaulted on four pillars, arches between them
    for (x, z) in ((4, 4), (8, 4), (4, 8), (8, 8)):
        for y in range(1, 6):
            b.set(x, y, z, B(CS))
    for x in range(1, 12):
        for z in range(1, 12):
            if x in (1, 11) or z in (1, 11):
                b.set(x, 5, z, stairs(SS, "south" if z == 1 else "north" if z == 11 else "east" if x == 1 else "west",
                                      "top"))
    # the shaft down from the well: ladder on the north face of the shaft
    cx = cz = 6
    for y in range(1, 9):
        b.set(cx, y, cz, AIR)
    for y in range(6, 9):
        b.set(cx, y, cz, B("ladder", facing="north"))
    b.jigsaw(cx, 8, cz, "up", name=J_CISTERN, final="minecraft:ladder[facing=north,waterlogged=false]",
             top="north")
    for y in range(1, 6):
        b.set(cx, y, cz, B("ladder", facing="north") if y > 1 else B("water", level=0))
    for y in range(1, 6):                                    # the ladder's backing pier
        b.set(cx, y, cz + 1, B(CS))
    # the ledge with the strongbox, candles, a bucket left behind
    for x in range(1, 4):
        b.set(x, 1, 10, B(CS))
    b.chest(2, 2, 10, "north", HIDDEN)
    b.set(1, 2, 10, B("candle", candles=3, lit=False))
    b.set(3, 2, 10, B("barrel", facing="up"))
    b.item_frame(2, 3, 11, "north", "bucket")
    b.set(10, 1, 2, B("cobweb"))
    b.set(11, 4, 11, B("cobweb"))
    hanging_lamp(b, 6, 4, 9)
    return b


def pools():
    rp = random.Random(1849451)
    mats = (("coarse_dirt", 4), ("sand", 2), ("dirt_path", 2), ("gravel", 1))
    return {
        "start": [Piece(khan("standing", 1849401), 2, PROC), Piece(khan("fallen", 1849403), 1, PROC)],
        "paths": [Piece(path_piece(f"{NAME}_path_a", 9, P_OUT, rp, mats=mats), 3, PROC, "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_b", 15, P_OUT, rp, mats=mats), 2, PROC, "terrain_matching"),
                  Empty(1)],
        "paths_gate": [Piece(path_piece(f"{NAME}_path_gate", 9, P_GATE, rp, mats=mats), 1, PROC,
                             "terrain_matching")],
        "gate": [Piece(caravan_camp(), 1, PROC)],
        "outskirts": [Piece(watch_post(), 2, PROC), Piece(tombs(), 2, PROC), Piece(dry_market(), 2, PROC)],
        "cistern": [Piece(cistern(), 1, PROC)],
    }
