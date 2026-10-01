"""Frostwatch Citadel - a fortified waystation of the frostbloom tundra (LANDMARK).

start  bailey : a 51x51 walled bailey on a raised snow mound. Deepslate curtain walls
                with a wall walk, four corner towers with snow-capped roofs, a
                twin-towered gatehouse with a raised portcullis, posterns east and west.
                The KEEP (13x13, ~34 high with its turret) holds a great hall (hearth,
                long table, lore lectern, bear rug over the ice-cellar hatch), the armoury,
                the captain's quarters, the watch room and a roof deck with a signal fire.
                A spiral stair climbs it; stairs climb to the wall walk.
wards  (inside, west + east plots)  : stables (horses) / smithy (anvil, smithing table,
                                      blast furnace, lava forge) / waystation inn / barracks
cellar (down from the keep hatch)   : ice cellar - packed-ice vault, the hidden chest
paths -> grounds (terrain-matching) : hunters' camp under a mammoth-tusk arch, ice-fishing
                                      hut, signal cairn, mammoth bones, frozen wagon,
                                      frostbloom shrine
"""
import math
import random

import nbt
from arch import plinth, ring, straight_stair
from blocks import AIR, B
from builder import Build, fit_height, item, slab, split_build, stairs, written_book
from common import spiral_floor_ok, spiral_stair
from furnish import chain_lamp, chair, long_table, rug, shelf_items, table, woodpile
from lore import BOOKS
from pieces import Empty, Piece
from terrain_paths import connector, path_piece, piece_in

NAME = "frost_outpost"
STRUCTURE = dict(biomes=["frostbloom_tundra"], spacing=50, separation=20, size=5, max_distance=76)
LOOT = "expanse:chests/frost_outpost"
HIDDEN = "expanse:chests/frost_outpost_hidden"
P_WARDS = f"expanse:{NAME}/wards"
P_EAST = f"expanse:{NAME}/east"
P_GATE_W = f"expanse:{NAME}/gate_west"
P_GATE_E = f"expanse:{NAME}/gate_east"
J_EAST = "expanse:frost_east"
J_GATE = "expanse:frost_gate"
P_CELLAR = f"expanse:{NAME}/cellar"
P_PATHS = f"expanse:{NAME}/paths"
P_GROUNDS = f"expanse:{NAME}/grounds"
J_WARD = "expanse:frost_ward"
J_CELLAR = "expanse:frost_cellar"
PROC = "frost_outpost"

DB = "deepslate_bricks"
DBR = "deepslate_brick"            # stairs / slab / wall
CD = "cobbled_deepslate"
PD = "polished_deepslate"
DT = "deepslate_tile"
WALLS = (DB, DB, DB, "cracked_deepslate_bricks", CD)
W = "spruce"


def book():
    d = BOOKS[NAME]
    return written_book(d["title"], d["author"], d["pages"])


def wall_block(rng):
    return B(rng.choice(WALLS))


def court_floor(x, z, rng):
    """Packed snow with smooth gravel and ice patches (no salt-and-pepper)."""
    v = math.sin(x * 0.37) + math.sin(z * 0.29 + 1.7) + math.sin((x + z) * 0.19 + 0.4) + rng.random() * 0.6
    return B("snow_block") if v < 1.2 else B("gravel") if v < 1.9 else B("packed_ice") if v < 2.2 else B(CD)


def snow_layer(rng, hi=2):
    return B("snow", layers=rng.randint(1, hi))


def snow_roof(b, rng, cells, prob=0.7):
    """Dust snow layers on top of the roof cells given (x, y, z) where the cell above is empty."""
    for (x, y, z) in cells:
        if b.inb(x, y + 1, z) and b.get(x, y + 1, z) is None and rng.random() < prob:
            b.set(x, y + 1, z, B("snow", layers=1))


def pyramid_roof(b, x0, z0, x1, z1, y, mat, rng, snow=True, finial=None):
    k = 0
    placed = []
    while x0 + k <= x1 - k and z0 + k <= z1 - k:
        for (x, z) in ring(x0 + k, z0 + k, x1 - k, z1 - k):
            if x0 + k == x1 - k or z0 + k == z1 - k:
                b.set(x, y + k, z, slab(mat))
            else:
                f = "south" if z == z0 + k else "north" if z == z1 - k else "east" if x == x0 + k else "west"
                b.set(x, y + k, z, stairs(mat, f))
            placed.append((x, y + k, z))
        k += 1
    if finial:
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        b.set(cx, y + k, cz, finial)
    if snow:
        snow_roof(b, rng, [p for p in placed if b.get(*p).short.endswith("_slab")], 0.9)
    return y + k


def tower(b, rng, cx, cz, r, y0, top, door_y=None, doors=(), roof=True):
    """Square tower (2r+1) from y0 to top with a crenellated deck and a pyramid roof on posts."""
    x0, x1, z0, z1 = cx - r, cx + r, cz - r, cz + r
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(y0, top + 1):
                if edge:
                    b.set(x, y, z, B(PD) if y in (y0, top) else wall_block(rng))
                elif y in (y0, top):
                    b.set(x, y, z, B(f"{W}_planks"))
                else:
                    b.set(x, y, z, AIR)
    for y in range(y0 + 1, top + 1):
        b.set(x0 + 1, y, z0 + 1, B("ladder", facing="south"))
    for (dx, dz) in doors:
        for y in (door_y, door_y + 1):
            b.set(cx + dx, y, cz + dz, AIR)
    for y in range(y0 + 3, top - 1, 4):
        for (x, z) in ((cx, z0), (cx, z1), (x0, cz), (x1, cz)):
            if b.get(x, y, z) is not None and b.get(x, y, z).id != "minecraft:air":
                b.set(x, y, z, B("iron_bars"))
    for (x, z) in ring(x0, z0, x1, z1):
        u = (x - x0) + (z - z0)
        b.set(x, top + 1, z, B(f"{DBR}_wall") if u % 2 else B(DB))
    if roof:
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            for y in range(top + 2, top + 4):
                b.set(x, y, z, B(f"{W}_fence"))
        pyramid_roof(b, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top + 4, W, rng, finial=B("lightning_rod", facing="up"))
        b.set(cx, top + 1, cz, B("lantern"))


# ------------------------------------------------------------------ start: the bailey
def bailey():
    b = Build(f"{NAME}_bailey_full", 51, 37, 51, seed=1847801)
    rng = b.rng
    # the snow mound (glacis) the citadel stands on
    plinth(b, 1, 1, 49, 49, 3, rng, wall=(CD, "stone", "snow_block", CD), fill="dirt", floor=B("snow_block"))
    for (x, z) in ring(1, 1, 49, 49):
        f = "south" if z == 1 else "north" if z == 49 else "east" if x == 1 else "west"
        b.set(x, 3, z, stairs(CD, f))
        if rng.random() < 0.6:
            b.set(x, 4, z, snow_layer(rng))
    for (x, z) in ring(2, 2, 48, 48):
        if rng.random() < 0.5:
            b.set(x, 4, z, snow_layer(rng, 3))
    # inner court floor
    for x in range(6, 45):
        for z in range(6, 45):
            b.set(x, 3, z, court_floor(x, z, rng))
    for z in range(3, 45):                              # the main street gate -> keep, worn at the edges
        for x in range(23, 28):
            if x in (23, 27) and rng.random() < 0.35:
                continue
            b.set(x, 3, z, B(rng.choice([CD, CD, "gravel", "cobblestone"])))
    for x in range(6, 45):                              # cross street between the wards
        for z in (19, 20):
            if rng.random() < 0.8:
                b.set(x, 3, z, B(rng.choice([CD, "gravel", "gravel"])))
    # curtain walls with a wall walk at y=12
    for x in range(4, 47):
        for z in range(4, 47):
            if not (x <= 5 or x >= 45 or z <= 5 or z >= 45):
                continue
            for y in range(4, 13):
                b.set(x, y, z, wall_block(rng) if y < 12 else B(PD))
            outer = x == 4 or x == 46 or z == 4 or z == 46
            if outer:
                u = x + z
                b.set(x, 13, z, B(DB) if u % 2 else B(f"{DBR}_wall"))
    for x in range(4, 47):                               # drifted snow on the wall walk
        for z in range(4, 47):
            if (x <= 5 or x >= 45 or z <= 5 or z >= 45) and b.get(x, 13, z) is None and rng.random() < 0.55:
                b.set(x, 13, z, B("snow", layers=rng.randint(1, 2)))
    for (x, z, f) in ((14, 4, "north"), (36, 4, "north"), (14, 46, "south"), (36, 46, "south"), (4, 14, "west"),
                      (4, 25, "west"), (46, 14, "east"), (46, 25, "east")):
        dx = {"north": 0, "south": 0, "west": -1, "east": 1}[f]
        dz = {"north": -1, "south": 1, "west": 0, "east": 0}[f]
        b.banner(x + dx, 10, z + dz, "light_blue", [("stripe_center", "white"), ("triangles_bottom", "blue"),
                                                    ("border", "blue")], wall_facing=f)
    for x in range(4, 47):                               # arrow slits
        for z in range(4, 47):
            outer = x in (4, 46) or z in (4, 46)
            if outer and (x + z) % 6 == 3 and 4 < x < 46 or outer and (x + z) % 6 == 3 and 4 < z < 46:
                if b.get(x, 9, z) is not None:
                    b.set(x, 9, z, B("iron_bars"))
    for x in range(6, 45):                               # inner wall-walk lip
        for z in (6, 44):
            if b.get(x, 12, z) is None:
                if x % 7 == 0:
                    b.set(x, 12, z, slab(DBR, "top"))
    # corner towers
    for (cx, cz) in ((4, 4), (46, 4), (4, 46), (46, 46)):
        tower(b, rng, cx, cz, 3, 3, 19, door_y=13,
              doors=((3 if cx < 25 else -3, 0), (0, 3 if cz < 25 else -3)))
    # gatehouse
    for gx in (20, 27):
        for x in range(gx, gx + 4):
            for z in range(1, 9):
                edge = x in (gx, gx + 3) or z in (1, 8)
                for y in range(1, 17):
                    if y <= 3:
                        b.set(x, y, z, B(CD))
                    elif edge:
                        b.set(x, y, z, B(PD) if y in (4, 16) else wall_block(rng))
                    else:
                        b.set(x, y, z, AIR if y < 16 else B(f"{W}_planks"))
        for (x, z) in ring(gx, 1, gx + 3, 8):
            b.set(x, 17, z, B(DB) if (x + z) % 2 else B(f"{DBR}_wall"))
        for y in (9, 13):
            b.set(gx + 2, y, 1, B("iron_bars"))
        for y in range(4, 17):
            b.set(gx + 1, y, 2, B("ladder", facing="south"))
        b.set(gx + 3 if gx == 20 else gx, 13, 6, AIR)
        b.set(gx + 3 if gx == 20 else gx, 14, 6, AIR)
    for x in range(24, 27):                              # passage, arch, portcullis
        for z in range(1, 9):
            for y in range(4, 9):
                b.set(x, y, z, AIR)
            for y in range(9, 13):
                b.set(x, y, z, wall_block(rng) if y < 12 else B(PD))
            b.set(x, 3, z, B(CD))
        b.set(x, 8, 3, B("iron_bars"))
        b.set(x, 8, 6, B("iron_bars"))
        for y in range(12, 13):
            b.set(x, 13, 1, B(f"{DBR}_wall"))
    b.set(24, 8, 1, stairs(DBR, "east", "top"))
    b.set(26, 8, 1, stairs(DBR, "west", "top"))
    b.set(24, 8, 8, stairs(DBR, "east", "top"))
    b.set(26, 8, 8, stairs(DBR, "west", "top"))
    wide_ramp = range(24, 27)
    for x in wide_ramp:
        straight_stair(b, x, 1, 0, "south", 3, CD, clear=4)
    for x in range(21, 30):
        b.set(x, 0, 0, B(rng.choice(["gravel", CD, "snow_block"])))
    connector(b, 25, 1, 0, "north", P_PATHS)
    for x in (21, 29):                                   # banners and braziers at the gate
        b.banner(x, 6, 0, "light_blue", [("stripe_center", "white"), ("triangles_bottom", "blue"),
                                          ("border", "blue")], wall_facing="north")
    for (x, z) in ((23, 9), (27, 9)):
        b.set(x, 4, z, B(f"{DBR}_wall"))
        b.set(x, 5, z, B("lantern"))
    # posterns east and west with steps down the mound and paths out
    for (wx, ox, step_dir, out_dir) in ((4, -1, "east", "west"), (46, 1, "west", "east")):
        for x in (wx, wx + (1 if wx == 4 else -1)):
            for y in (4, 5):
                b.set(x, y, 37, AIR)
        b.door(wx, 4, 37, W, out_dir)
        for i in range(3):
            x = wx + ox * (1 + i)
            b.set(x, 3 - i, 37, stairs(CD, step_dir))
            for h in range(1, 4):
                b.set(x, 3 - i + h, 37, AIR)
        gx = 0 if wx == 4 else 50
        b.set(gx, 0, 37, B("gravel"))
        connector(b, gx, 1, 37, out_dir, P_PATHS)
    # stairs up to the wall walk (inside the west and east walls, behind the plots)
    straight_stair(b, 6, 4, 40, "north", 9, CD, under=B(CD))
    straight_stair(b, 44, 4, 40, "north", 9, CD, under=B(CD))
    for z in range(32, 41):
        for y in range(4, 4 + (40 - z)):
            b.set(6, y, z, B(CD))
            b.set(44, y, z, B(CD))
    # ward plots (structure void) with inward jigsaws
    for x in range(6, 18):
        for z in range(8, 31):
            for y in range(4, 20):
                b.cells.pop((x, y, z), None)
    for x in range(33, 45):
        for z in range(8, 31):
            for y in range(4, 20):
                b.cells.pop((x, y, z), None)
    b.jigsaw(18, 4, 19, "west", target=J_WARD, pool=P_WARDS)
    b.jigsaw(32, 4, 19, "east", target=J_WARD, pool=P_WARDS)
    keep(b, rng)
    yard(b, rng)
    return b


def bailey_pieces():
    """The bailey is drawn whole, then cut into four pieces within the 48-block template limit (west and
    east halves, each split again behind the gatehouse) joined by rigid jigsaw pairs buried in the mound.
    The start is the west-back quarter (keep, west ward)."""
    west, east = split_build(bailey(), "x", 25, f"{NAME}_bailey_w", f"{NAME}_bailey_e")
    west_front, west_back = split_build(west, "z", 8, f"{NAME}_bailey_gate_w", f"{NAME}_bailey")
    east_front, east_back = split_build(east, "z", 8, f"{NAME}_bailey_gate_e", f"{NAME}_bailey_east")
    west_back.jigsaw(24, 2, 12, "east", target=J_EAST, pool=P_EAST, final="minecraft:dirt")
    east_back.jigsaw(0, 2, 12, "west", name=J_EAST, final="minecraft:dirt")
    west_back.jigsaw(12, 2, 0, "north", target=J_GATE, pool=P_GATE_W, final="minecraft:dirt")
    west_front.jigsaw(12, 2, 7, "south", name=J_GATE, final="minecraft:dirt")
    east_back.jigsaw(13, 2, 0, "north", target=J_GATE, pool=P_GATE_E, final="minecraft:dirt")
    east_front.jigsaw(13, 2, 7, "south", name=J_GATE, final="minecraft:dirt")
    return [fit_height(p) for p in (west_back, east_back, west_front, east_front)]


def keep(b, rng):
    x0, x1, z0, z1 = 19, 31, 30, 42
    floors = (4, 10, 16, 22)
    deck = 27
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            for y in range(4, deck + 1):
                if edge:
                    b.set(x, y, z, B(PD) if (y in floors or y == deck or corner and y % 3 == 0) else wall_block(rng))
                elif y in floors or y == deck:
                    b.set(x, y, z, B(PD) if y == 4 else B(f"{W}_planks"))
                else:
                    b.set(x, y, z, AIR)
    # buttress-like corner pilasters
    for (x, z) in ((x0 - 1, z0), (x0, z0 - 1), (x1 + 1, z0), (x1, z0 - 1), (x0 - 1, z1), (x0, z1 + 1),
                   (x1 + 1, z1), (x1, z1 + 1)):
        for y in range(4, 9):
            b.set(x, y, z, B(DB))
        b.set(x, 9, z, B(f"{DBR}_wall"))
    # windows on every face of every storey
    for fy in floors:
        for (x, z) in ((22, z0), (28, z0), (22, z1), (28, z1), (x0, 33), (x0, 39), (x1, 33), (x1, 39)):
            for y in (fy + 2, fy + 3):
                b.set(x, y, z, B("light_blue_stained_glass_pane") if fy > 4 else B("glass_pane"))
    # the door and its steps
    for y in (5, 6, 7):
        b.set(25, y, z0, AIR)
    b.door(25, 5, z0, W, "north")
    b.set(25, 7, z0, B(PD))
    for x in (24, 25, 26):
        b.set(x, 4, z0 - 1, stairs(PD, "south"))
    b.banner(24, 7, z0 - 1, "blue", [("stripe_center", "white"), ("border", "light_blue")], wall_facing="north")
    b.banner(26, 7, z0 - 1, "blue", [("stripe_center", "white"), ("border", "light_blue")], wall_facing="north")
    # spiral stair in the north-east corner, holes in the floors
    steps = spiral_stair(b, 29, 40, 5, deck, W, B(f"{W}_log", axis="y"), underside=B(f"{W}_planks"))
    for fy in floors[1:] + (deck,):
        for x in range(28, 31):
            for z in range(39, 42):
                if (x, z) == (29, 40):
                    continue
                if not spiral_floor_ok(steps, x, z, fy) and b.get(x, fy, z) is not None and \
                        not b.get(x, fy, z).short.endswith("_stairs"):
                    b.set(x, fy, z, AIR)
    b.set(29, deck + 1, 40, B(f"{W}_fence"))
    b.set(29, deck + 2, 40, B("lantern"))

    # ground floor: great hall
    fy = 4
    long_table(b, [(x, 35) for x in range(21, 27)], fy + 1, W, cloth=None)
    for x in range(22, 26):
        chair(b, x, fy + 1, 34, W, "north")
        chair(b, x, fy + 1, 36, W, "south")
        b.set(x, fy + 2, 35, B(rng.choice(["white_carpet", "flower_pot", "white_carpet", "candle"])))
    b.set(26, fy + 2, 35, B("candle", candles=3, lit=True))
    chair(b, 20, fy + 1, 35, W, "west")
    for z in (35, 36, 37):
        b.set(x0, fy + 1, z, AIR)
        b.set(x0, fy + 2, z, AIR)
    for z in (35, 37):
        b.set(x0 - 1, fy + 1, z, B(PD))
        b.set(x0 - 1, fy + 2, z, B(PD))
    b.campfire(x0 - 1, fy + 1, 36, lit=True, facing="east")
    b.set(x0 - 1, fy + 2, 36, AIR)
    for y in range(fy + 3, deck + 3):
        for z in (35, 36, 37):
            b.set(x0 - 1, y, z, B(DB) if not (z == 36 and y < deck + 2) else B(DB))
    for z in (35, 36, 37):
        b.set(x0, fy + 3, z, stairs(DBR, "east", "top"))
    # bear rug with the ice-cellar hatch
    rug(b, 21, 38, 23, 41, fy + 1, "brown", None)
    b.set(22, fy + 1, 37, B("white_carpet"))
    b.set(21, fy + 1, 37, B("black_carpet"))
    b.set(23, fy + 1, 37, B("black_carpet"))
    b.set(22, fy, 40, B("spruce_trapdoor", facing="south", half="top", open=False))
    for y in range(1, fy):
        b.set(22, y, 40, B("ladder", facing="south"))
    b.jigsaw(22, 0, 40, "down", target=J_CELLAR, pool=P_CELLAR,
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    b.lectern(27, fy + 1, 32, "north", book())
    b.armor_stand(20, fy + 1, 31, yaw=135.0, equipment={"head": "iron_helmet", "chest": "chainmail_chestplate",
                                                       "legs": "chainmail_leggings", "mainhand": "iron_sword"})
    b.banner(25, fy + 4, z1 - 1, "light_blue", [("stripe_center", "white"), ("triangles_bottom", "blue"),
                                                 ("border", "blue")], wall_facing="north")
    for x in (21, 27):
        b.set(x, fy + 1, z1 - 1, B("barrel", facing="up"))
    chain_lamp(b, 23, 10, 35, 2)
    chain_lamp(b, 27, 10, 36, 2)
    b.set(30, fy + 1, 31, B("potted_spruce_sapling"))

    # first floor: armoury
    fy = 10
    for x in (21, 23):
        b.armor_stand(x, fy + 1, 31, yaw=180.0,
                      equipment={"head": "chainmail_helmet", "chest": "chainmail_chestplate", "mainhand": "iron_axe"})
    b.armor_stand(20, fy + 1, 37, yaw=270.0, equipment={"head": "leather_helmet", "chest": "leather_chestplate",
                                                        "legs": "leather_leggings", "feet": "leather_boots",
                                                        "mainhand": "bow"})
    for (x, it) in ((24, "iron_sword"), (25, "shield"), (26, "crossbow"), (27, "iron_axe")):
        b.item_frame(x, fy + 3, 31, "south", it)
    b.chest(20, fy + 1, 41, "east", LOOT)
    b.set(20, fy + 1, 40, B("grindstone", face="floor", facing="east"))
    b.barrel(21, fy + 1, 41, "up")
    b.set(21, fy + 2, 41, B("target"))
    for z in (33, 34):
        b.set(30, fy + 1, z, B("barrel", facing="west"))
    chain_lamp(b, 24, 16, 36, 1)

    # second floor: captain's quarters
    fy = 16
    b.bed(21, fy + 1, 32, "west", "blue")
    b.set(20, fy + 1, 34, B("chest", facing="east"))
    rug(b, 22, 33, 26, 37, fy + 1, "light_blue", "blue")
    table(b, 24, fy + 1, 39, W, top=slab(W, "top"))
    b.set(25, fy + 1, 39, slab(W, "top"))
    chair(b, 24, fy + 1, 40, W, "south")
    b.item_frame(20, fy + 3, 37, "east", "map")
    b.item_frame(20, fy + 3, 38, "east", "map")
    b.item_frame(20, fy + 3, 39, "east", "compass")
    b.set(24, fy + 2, 39, B("candle", candles=2, lit=True))
    b.item_frame(25, fy + 2, 39, "up", "writable_book")
    b.painting(27, fy + 3, 31, "south", "sunset")
    b.shelf(30, fy + 2, 33, W, "west", shelf_items(rng, "study"))
    b.set(30, fy + 1, 33, B("bookshelf"))
    chain_lamp(b, 24, 22, 35, 1)

    # third floor: watch room
    fy = 22
    b.bed(21, fy + 1, 31, "east", "white")
    b.bed(21, fy + 1, 33, "east", "white")
    b.set(20, fy + 1, 35, B("barrel", facing="east"))
    b.item_frame(25, fy + 3, 31, "south", "spyglass")
    b.set(24, fy + 1, 37, B("crafting_table"))
    b.set(20, fy + 1, 41, B("chest", facing="east"))
    chain_lamp(b, 25, 27, 36, 1)

    # roof deck: corbelled crenellations, signal fire, banner pole, turret
    for (x, z) in ring(x0 - 1, z0 - 1, x1 + 1, z1 + 1):
        f = "south" if z == z0 - 1 else "north" if z == z1 + 1 else "east" if x == x0 - 1 else "west"
        b.set(x, deck, z, stairs(DBR, f, "top"))
        b.set(x, deck + 1, z, B(DB) if (x + z) % 2 else B(f"{DBR}_wall"))
    for (x, z) in ring(x0, z0, x1, z1):
        b.set(x, deck + 1, z, B(PD))
    b.set(23, deck + 1, 36, B("hay_block", axis="y"))
    b.campfire(23, deck + 2, 36, lit=True)
    for (x, z) in ((22, 36), (24, 36), (23, 35), (23, 37)):
        b.set(x, deck + 1, z, slab(PD))
    for y in range(deck + 1, deck + 7):
        b.set(21, y, 32, B(f"{W}_fence"))
    b.banner(21, deck + 7, 32, "light_blue",
                                                          [("stripe_center", "white"), ("triangles_bottom", "blue"),
                                                           ("border", "blue")], rotation=8)
    tower(b, rng, 30, 31, 1, deck, deck + 4, roof=False)
    pyramid_roof(b, 28, 29, 32, 33, deck + 6, DT, rng, finial=B("lightning_rod", facing="up"))
    for (x, z) in ((29, 30), (31, 30), (29, 32), (31, 32)):
        if b.get(x, deck + 5, z) is None:
            b.set(x, deck + 5, z, B(f"{W}_fence"))


def yard(b, rng):
    """Court dressing between the wards and the keep."""
    from furnish import well
    well(b, 25, 4, 20, stone="cobblestone", roof=W)
    for (x, z, yaw) in ((20, 13, 90.0), (20, 16, 90.0)):
        b.set(x, 4, z, B(f"{W}_fence"))
        b.armor_stand(x, 5, z, yaw=yaw, equipment={"head": "carved_pumpkin", "chest": "leather_chestplate"})
    b.set(19, 4, 11, B("target"))
    b.set(19, 4, 18, B("hay_block", axis="y"))
    b.set(19, 5, 18, B("hay_block", axis="x"))
    woodpile(b, 29, 4, 12, W, "z", length=3, height=2, rng=rng)
    woodpile(b, 29, 4, 15, W, "z", length=3, height=3, rng=rng)
    # a sledge parked by the gate
    for z in range(10, 14):
        b.set(31, 4, z, slab(W))
        b.set(32, 4, z, slab(W))
    b.set(31, 4, 9, stairs(W, "south"))
    b.set(32, 4, 9, stairs(W, "south"))
    b.set(31, 5, 12, B("barrel", facing="up"))
    b.set(32, 5, 13, B("white_wool"))
    for (x, z) in ((22, 28), (28, 28), (22, 10), (28, 10)):
        b.set(x, 4, z, B(f"{W}_fence"))
        b.set(x, 5, z, B(f"{W}_fence"))
        b.set(x, 6, z, B("soul_lantern"))
    for x in range(7, 44):
        for z in range(7, 44):
            if b.get(x, 4, z) is None and b.get(x, 3, z) is not None and b.get(x, 3, z).short == "snow_block" \
                    and rng.random() < 0.25 and not (6 <= x <= 17 or 33 <= x <= 44) or \
                    b.get(x, 4, z) is None and z > 31 and rng.random() < 0.15 and b.get(x, 3, z) is not None:
                if 8 <= z <= 30 and (6 <= x <= 17 or 33 <= x <= 44):
                    continue
                b.set(x, 4, z, snow_layer(rng))


# ------------------------------------------------------------------ wards (inner plots, 12 x 23)
def ward_base(name, seed, h=16):
    b = Build(name, 12, h, 23, seed=seed)
    rng = b.rng
    for x in range(12):
        for z in range(23):
            b.set(x, 0, z, court_floor(x * 3 + seed % 7, z, rng))
    b.jigsaw(11, 1, 11, "east", name=J_WARD)
    return b, rng


def lean_to(b, rng, x0, x1, z0, z1, y_back, y_front, mat=W):
    """Roof sloping from the back (x0, high) to the front (x1, low)."""
    n = x1 - x0 + 1
    placed = []
    for i, x in enumerate(range(x0, x1 + 1)):
        y = y_back - (i * (y_back - y_front)) // max(1, n - 1)
        for z in range(z0, z1 + 1):
            b.set(x, y, z, stairs(mat, "west"))
            placed.append((x, y, z))
    snow_roof(b, rng, placed, 0.8)


def ward_stables():
    b, rng = ward_base(f"{NAME}_ward_stables", 1847811)
    # back wall (wall side, x=0..1) and stall partitions
    for z in range(1, 22):
        for y in range(1, 6):
            b.set(0, y, z, B(f"{W}_planks") if z % 5 else B(f"{W}_log", axis="y"))
    for z in (1, 21):
        for x in range(1, 9):
            for y in range(1, 5):
                b.set(x, y, z, B(f"{W}_planks") if x % 4 else B(f"{W}_log", axis="y"))
    for z in (6, 11, 16):
        for x in range(1, 6):
            b.set(x, 1, z, B(f"{W}_fence"))
            b.set(x, 2, z, B(f"{W}_fence"))
    for z in range(2, 21):
        if z in (6, 11, 16):
            continue
        b.set(6, 1, z, B(f"{W}_fence_gate", facing="east", open=False, in_wall=False)) if z in (4, 9, 14, 19) \
            else b.set(6, 1, z, B(f"{W}_fence"))
    for x in range(1, 6):
        for z in range(2, 21):
            if b.get(x, 1, z) is None:
                b.set(x, 0, z, B("coarse_dirt") if rng.random() < 0.5 else B("dirt"))
    for z in (3, 8, 13, 18):
        b.set(1, 1, z, B("water_cauldron", level=3))
        b.set(1, 1, z + 1, B("hay_block", axis="y"))
    for (z, v) in ((4, 0), (9, 258), (14, 513)):
        b.mob(3, 1, z, "horse", yaw=90.0, extra={"Variant": nbt.Int(v), "Tame": nbt.Byte(0),
                                                  "Temper": nbt.Int(0)})
    b.item_frame(1, 3, 17, "east", "saddle")
    b.item_frame(1, 3, 12, "east", "lead")
    # posts and the roof
    for z in (1, 6, 11, 16, 21):
        for y in range(1, 5):
            b.set(8, y, z, B(f"{W}_log", axis="y"))
    lean_to(b, rng, 0, 9, 0, 22, 6, 4)
    # hayloft at the end and tack corner
    b.set(9, 1, 2, B("hay_block", axis="y"))
    b.set(10, 1, 2, B("hay_block", axis="x"))
    b.set(9, 2, 2, B("hay_block", axis="z"))
    b.chest(9, 1, 20, "west", LOOT)
    b.set(10, 1, 20, B("barrel", facing="up"))
    b.set(10, 1, 19, B("composter", level=7))
    for z in (5, 15):
        b.set(7, 4, z, B("lantern", hanging=True))
    return b


def ward_smithy():
    b, rng = ward_base(f"{NAME}_ward_smithy", 1847813)
    for (x, z) in ring(0, 2, 7, 20):
        for y in range(1, 6):
            if x == 7 and 8 <= z <= 14 and y <= 4:
                continue
            b.set(x, y, z, wall_block(rng) if y < 3 else B(f"{W}_planks") if (x + z) % 4 else B(f"{W}_log", axis="y"))
    b.air(1, 1, 3, 6, 5, 19)
    for z in (8, 14):
        for y in range(1, 5):
            b.set(7, y, z, B(f"{W}_log", axis="y"))
    lean_to(b, rng, 0, 8, 1, 21, 7, 5)
    # forge: lava basin under a stone hood, chimney through the roof
    for (x, z) in ((1, 3), (2, 3), (1, 4), (2, 4)):
        b.set(x, 1, z, B(PD))
    b.set(1, 2, 3, B("lava_cauldron"))
    b.set(2, 2, 3, B("blast_furnace", facing="east"))
    b.set(1, 2, 4, B("furnace", facing="east"))
    for y in range(3, 10):
        b.set(1, y, 3, B(DB))
        b.set(2, y, 3, B(DB) if y > 3 else B(f"{DBR}_slab", type="top"))
    b.campfire(1, 10, 3, lit=True)
    b.set(1, 3, 4, stairs(DBR, "south", "top"))
    b.set(2, 3, 4, stairs(DBR, "south", "top"))
    b.set(4, 1, 6, B("anvil", facing="north"))
    b.set(4, 1, 9, B("smithing_table"))
    b.set(1, 1, 8, B("grindstone", face="floor", facing="east"))
    b.set(1, 1, 10, B("water_cauldron", level=3))
    b.chest(1, 1, 18, "east", LOOT)
    b.barrel(1, 1, 17, "east")
    b.set(1, 1, 16, B("coal_block"))
    b.set(1, 2, 16, B("coal_block"))
    b.armor_stand(5, 1, 18, yaw=200.0, equipment={"chest": "iron_chestplate", "head": "chainmail_helmet"})
    b.shelf(1, 3, 13, W, "east", ["iron_ingot", "iron_nugget", "iron_chain"])
    for (z, it) in ((11, "iron_pickaxe"), (12, "iron_shovel")):
        b.item_frame(1, 3, z, "east", it)
    chain_lamp(b, 4, 6, 13, 1)
    # outside: quench trough and ore carts
    b.set(9, 1, 6, B("water_cauldron", level=2))
    b.set(9, 1, 15, B("raw_iron_block"))
    b.set(10, 1, 15, B("barrel", facing="up"))
    woodpile(b, 9, 1, 18, W, "z", length=3, height=2, rng=rng)
    return b


def ward_inn():
    b, rng = ward_base(f"{NAME}_ward_inn", 1847815, h=18)
    x0, x1, z0, z1 = 0, 9, 1, 21
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, B(f"{W}_planks") if 0 < x < x1 and z0 < z < z1 else B(CD))
    for (x, z) in ring(x0, z0, x1, z1):
        post = (x in (x0, x1) and z in (z0, z1)) or (z - z0) % 5 == 0
        for y in range(1, 9):
            if y <= 1:
                b.set(x, y, z, wall_block(rng))
            elif post:
                b.set(x, y, z, B(f"{W}_log", axis="y"))
            elif y in (5,):
                b.set(x, y, z, B(f"stripped_{W}_log", axis="z" if x in (x0, x1) else "x"))
            else:
                b.set(x, y, z, B("light_gray_terracotta"))
    b.air(1, 1, 2, 8, 8, 20)
    b.fill(1, 5, 2, 8, 5, 20, B(f"{W}_planks"))
    from kit import roof_gable
    roof_gable(b, x0, x1, z0, z1, 9, W, B(f"{W}_planks"), along="z", overhang=1, gable_overhang=1)
    for x in range(-1, 11):
        for z in range(0, 23):
            for y in range(9, 15):
                st = b.get(x, y, z) if b.inb(x, y, z) else None
                if st is not None and st.short.endswith("_stairs") and b.get(x, y + 1, z) is None and \
                        rng.random() < 0.6:
                    b.set(x, y + 1, z, B("snow", layers=1))
    for z in (5, 10, 16):
        for y in (2, 3):
            b.set(x1, y, z, B("glass_pane"))
            b.set(x1, y + 4, z, B("glass_pane"))
    for y in (2, 3):
        b.set(x1, y, 12, AIR)
    b.door(x1, 2, 12, W, "east")
    b.set(x1, 1, 12, B(f"{W}_planks"))
    b.set(x1 + 1, 1, 12, stairs(W, "east"))
    b.sign(10, 4, 11, W, ["", "The Last", "Warm Hearth", ""], wall_facing="east")
    b.set(x1, 4, 11, B(f"{W}_planks"))
    # taproom
    for (x, z) in ((3, 5), (6, 5), (3, 9), (6, 9)):
        table(b, x, 2, z, W, top=slab(W, "top"))
        chair(b, x, 2, z + 1, W, "south")
        chair(b, x, 2, z - 1, W, "north")
    for z in range(14, 20):
        b.set(1, 2, z, B("barrel", facing="east"))
        if z % 2:
            b.set(1, 3, z, B("barrel", facing="east"))
    long_table(b, [(4, z) for z in range(15, 20)], 2, W)
    b.set(1, 2, 3, B("smoker", facing="east"))
    b.set(1, 2, 4, B("water_cauldron", level=2))
    b.chest(8, 2, 20, "west", LOOT)
    b.shelf(1, 3, 12, W, "east", shelf_items(rng, "kitchen"))
    chain_lamp(b, 4, 5, 7, 1)
    chain_lamp(b, 4, 5, 17, 1)
    # rooms upstairs, reached by a ladder by the back wall
    for y in range(2, 6):
        b.set(8, y, 18, B("ladder", facing="west"))
    for z in (3, 8, 16):
        b.bed(2, 6, z, "west", rng.choice(["red", "light_blue", "white"]))
        b.set(1, 6, z + 1, B("candle", candles=1, lit=False))
    for z in (6, 13):
        for x in range(1, 7):
            if x != 4:
                b.set(x, 6, z, B(f"{W}_planks"))
    return b


def ward_barracks():
    b, rng = ward_base(f"{NAME}_ward_barracks", 1847817)
    for (x, z) in ring(0, 1, 6, 21):
        for y in range(1, 6):
            b.set(x, y, z, wall_block(rng) if y < 2 else B(f"{W}_planks") if (z % 5) else B(f"{W}_log", axis="y"))
    b.air(1, 1, 2, 5, 5, 20)
    for z in (8, 14):
        for y in (1, 2):
            b.set(6, y, z, AIR)
    b.door(6, 1, 8, W, "east")
    b.door(6, 1, 14, W, "east")
    for (z, col) in ((3, "gray"), (5, "gray"), (17, "blue"), (19, "blue")):
        b.bed(2, 1, z, "west", col)
    b.chest(1, 1, 11, "east", LOOT)
    b.barrel(1, 1, 10, "east")
    b.barrel(1, 1, 12, "east")
    for z in (7, 15):
        b.armor_stand(5, 1, z, yaw=90.0, equipment={"head": "chainmail_helmet", "mainhand": "iron_sword"})
    lean_to(b, rng, 0, 7, 0, 22, 7, 5)
    chain_lamp(b, 3, 6, 11, 1)
    # training yard
    for z in (3, 9, 15):
        b.set(9, 1, z, B(f"{W}_fence"))
        b.armor_stand(9, 2, z, yaw=270.0, equipment={"head": "carved_pumpkin", "chest": "leather_chestplate"})
    b.set(10, 1, 20, B("target"))
    b.set(10, 2, 20, B("target"))
    b.set(10, 1, 12, B("hay_block", axis="y"))
    return b


# ------------------------------------------------------------------ cellar
def cellar_ice():
    b = Build(f"{NAME}_ice_cellar", 11, 7, 11, seed=1847821)
    rng = b.rng
    for x in range(11):
        for z in range(11):
            for y in range(7):
                b.set(x, y, z, B(rng.choice(["dirt", "stone", CD, "packed_ice"])))
    b.air(1, 1, 1, 9, 4, 9)
    for (x, z) in ring(1, 1, 9, 9):
        for y in range(1, 5):
            b.set(x, y, z, B("packed_ice") if rng.random() < 0.75 else B("blue_ice"))
    b.air(2, 1, 2, 8, 4, 8)
    for x in range(2, 9):
        for z in range(2, 9):
            b.set(x, 0, z, B("packed_ice") if (x + z) % 2 else B("snow_block"))
    for y in range(1, 7):
        b.set(5, y, 5 + 0, AIR)
    for y in range(1, 6):
        b.set(5, y, 5, B("ladder", facing="south"))
    b.set(5, 0, 5, B(PD))
    b.set(5, 1, 4, B("packed_ice"))
    for y in range(1, 6):
        b.set(5, y, 4, B("packed_ice"))
    b.jigsaw(5, 6, 5, "up", name=J_CELLAR, final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    b.chest(8, 1, 8, "west", HIDDEN)
    b.barrel(2, 1, 8, "up")
    b.barrel(2, 1, 7, "up")
    for (x, z) in ((2, 2), (3, 2), (8, 2)):
        b.set(x, 1, z, B("snow_block"))
    for (x, it) in ((3, "expanse:venison"), (4, "cod"), (6, "salmon"), (7, "beef")):
        b.item_frame(x, 3, 8, "north", it)
    b.armor_stand(8, 1, 3, yaw=315.0, equipment={"head": "iron_helmet", "chest": "leather_chestplate",
                                                 "feet": "leather_boots", "mainhand": "iron_axe"})
    b.set(2, 1, 5, B("soul_lantern"))
    b.set(8, 1, 5, B("powder_snow"))
    return b


# ------------------------------------------------------------------ grounds
def ground_hunters_camp():
    b = Build(f"{NAME}_hunters_camp", 13, 9, 14, seed=1847831)
    rng = b.rng
    piece_in(b, 6, 1, 0, "north")
    for x in range(13):
        for z in range(1, 14):
            b.set(x, 0, z, B(rng.choice(["snow_block", "snow_block", "gravel", "coarse_dirt"])))
    # mammoth-tusk arch over the camp entrance
    for (x, s) in ((3, 1), (9, -1)):
        pts = [(x, 1), (x, 2), (x, 3), (x + s, 4), (x + s, 5), (x + 2 * s, 6)]
        for (px, py) in pts:
            b.set(px, py, 2, B("bone_block", axis="y"))
    b.set(6, 6, 2, B("bone_block", axis="x"))
    b.set(5, 6, 2, B("bone_block", axis="x"))
    b.set(7, 6, 2, B("bone_block", axis="x"))
    # fire pit with log seats and a hide rack
    b.campfire(6, 1, 8, lit=True)
    for (x, z, ax) in ((4, 8, "z"), (8, 8, "z"), (6, 10, "x")):
        b.set(x, 1, z, B(f"stripped_{W}_log", axis=ax))
    for x in (2, 4):
        b.set(x, 1, 12, B(f"{W}_fence"))
        b.set(x, 2, 12, B(f"{W}_fence"))
    for x in (2, 3, 4):
        b.set(x, 3, 12, B(f"{W}_fence"))
    for x in (2, 3, 4):
        if x == 3:
            b.set(x, 2, 12, B("brown_wool"))                # a hide stretched on the rack
    # tent: wool lean-to
    for z in range(9, 13):
        b.set(10, 1, z, B("white_wool"))
        b.set(10, 2, z, stairs(W, "west"))
        b.set(11, 1, z, B("white_wool"))
        b.set(9, 1, z, stairs(W, "east") if z in (9, 12) else AIR)
    b.barrel(11, 1, 12, "up")
    b.set(9, 1, 12, B("barrel", facing="up"))
    b.set(1, 1, 7, B("snow_block"))
    b.set(1, 2, 7, B("snow", layers=3))
    b.set(12, 1, 4, B("spruce_slab", type="bottom"))
    return b


def ground_ice_fishing():
    b = Build(f"{NAME}_ice_fishing", 13, 7, 13, seed=1847833)
    rng = b.rng
    piece_in(b, 6, 2, 0, "north")
    for x in range(13):
        for z in range(1, 13):
            d = math.hypot(x - 6, z - 7)
            b.set(x, 0, z, B("water", level=0) if d < 4.5 else B("dirt"))
            b.set(x, 1, z, B("ice") if d < 4.5 else B("snow_block"))
    for (x, z) in ((5, 6), (8, 8)):
        b.set(x, 1, z, B("water", level=0))
    # little hut on the ice
    for (x, z) in ring(3, 8, 6, 11):
        for y in range(2, 4):
            b.set(x, y, z, B(f"{W}_planks"))
    b.air(4, 2, 9, 5, 3, 10)
    b.set(4, 2, 8, AIR)
    b.set(4, 3, 8, AIR)
    for x in range(2, 8):
        b.set(x, 4, 8, stairs(W, "north"))
        b.set(x, 4, 11, stairs(W, "south"))
        b.set(x, 4, 9, slab(W))
        b.set(x, 4, 10, slab(W))
        b.set(x, 5, 9, B("snow", layers=1))
    b.set(5, 1, 10, B("water", level=0))
    b.set(4, 2, 10, B("barrel", facing="up"))
    b.item_frame(5, 3, 11, "north", "fishing_rod")
    b.set(8, 2, 8, stairs(W, "west"))
    b.set(9, 2, 6, B("barrel", facing="up"))
    b.set(9, 3, 6, B("snow", layers=2))
    return b


def ground_cairn():
    b = Build(f"{NAME}_cairn", 9, 12, 9, seed=1847835)
    rng = b.rng
    piece_in(b, 4, 1, 0, "north")
    for x in range(9):
        for z in range(1, 9):
            b.set(x, 0, z, B(rng.choice(["snow_block", "gravel", "stone"])))
    # inukshuk-style cairn with a signal fire beside it
    for y in range(1, 4):
        b.set(3, y, 5, B(rng.choice(["stone", "cobblestone", CD])))
        b.set(5, y, 5, B(rng.choice(["stone", "cobblestone", CD])))
    for x in range(2, 7):
        b.set(x, 4, 5, B(rng.choice(["stone", CD, "polished_andesite"])))
    for y in (5, 6):
        b.set(4, y, 5, B(rng.choice(["stone", CD])))
    b.set(4, 7, 5, B(f"{CD}_slab", type="bottom"))
    b.set(6, 1, 2, B("hay_block", axis="y"))
    b.campfire(6, 2, 2, lit=True)
    for (x, z) in ((5, 2), (7, 2), (6, 1), (6, 3)):
        b.set(x, 1, z, B(CD))
    b.set(2, 1, 2, B("barrel", facing="up"))
    return b


def ground_mammoth_bones():
    b = Build(f"{NAME}_mammoth", 15, 8, 15, seed=1847837)
    rng = b.rng
    piece_in(b, 7, 1, 0, "north")
    for x in range(15):
        for z in range(1, 15):
            b.set(x, 0, z, B(rng.choice(["snow_block", "snow_block", "gravel"])))
    for z in range(4, 12):                               # spine
        b.set(7, 2, z, B("bone_block", axis="z"))
    for z in range(5, 11, 2):                            # ribs
        for (x, y) in ((6, 2), (5, 1), (8, 2), (9, 1)):
            if rng.random() < 0.85:
                b.set(x, y, z, B("bone_block", axis="y"))
    b.set(7, 1, 12, B("bone_block", axis="y"))           # skull and tusks
    b.set(7, 2, 12, B("bone_block", axis="y"))
    for x in (6, 8):                                    # tusks curling up
        b.set(x, 2, 12, B("bone_block", axis="x"))
        b.set(x, 2, 13, B("bone_block", axis="z"))
        b.set(x, 2, 14, B("bone_block", axis="z"))
        b.set(x, 3, 14, B("bone_block", axis="y"))
    for x in range(15):
        for z in range(1, 15):
            if b.get(x, 1, z) is None and rng.random() < 0.4:
                b.set(x, 1, z, B("snow", layers=rng.randint(1, 3)))
    b.set(12, 1, 3, B("expanse:frostbloom"))
    b.set(2, 1, 11, B("expanse:frostbloom"))
    return b


def ground_frozen_wagon():
    b = Build(f"{NAME}_wagon", 11, 6, 12, seed=1847839)
    rng = b.rng
    piece_in(b, 5, 1, 0, "north")
    for x in range(11):
        for z in range(1, 12):
            b.set(x, 0, z, B(rng.choice(["snow_block", "gravel", "snow_block"])))
    for z in range(4, 10):
        for x in (4, 5, 6):
            b.set(x, 1, z, B(f"{W}_slab", type="top") if not (x == 6 and z > 7) else AIR)
    for z in (4, 9):
        b.set(3, 1, z, B(f"stripped_{W}_log", axis="x"))
        if z == 4:
            b.set(7, 1, z, B(f"stripped_{W}_log", axis="x"))
    for x in (4, 5):
        for z in (5, 6, 8):
            if rng.random() < 0.7:
                b.set(x, 2, z, B("barrel", facing="up"))
    b.set(5, 2, 7, B("chest", facing="north"))
    b.set(6, 1, 10, B(f"{W}_fence"))
    b.set(6, 1, 11, B(f"{W}_fence"))
    b.set(4, 2, 9, B("white_wool"))
    for x in range(11):
        for z in range(1, 12):
            if b.get(x, 1, z) is None and rng.random() < 0.45:
                b.set(x, 1, z, B("snow", layers=rng.randint(1, 4)))
            elif b.get(x, 2, z) is None and b.get(x, 1, z) is not None and rng.random() < 0.3 and \
                    b.get(x, 1, z).short != "snow":
                b.set(x, 2, z, B("snow", layers=1))
    return b


def ground_frostbloom_shrine():
    b = Build(f"{NAME}_shrine", 9, 7, 10, seed=1847841)
    rng = b.rng
    piece_in(b, 4, 1, 0, "north")
    for x in range(9):
        for z in range(1, 10):
            b.set(x, 0, z, B("snow_block"))
    for x in range(2, 7):
        for z in range(4, 9):
            b.set(x, 0, z, B(PD) if (x + z) % 2 else B(DB))
    for (x, z) in ((2, 8), (6, 8)):
        for y in range(1, 4):
            b.set(x, y, z, B(f"{DBR}_wall"))
    for x in range(2, 7):
        b.set(x, 4, 8, slab(DBR) if x in (2, 6) else B(DB))
        b.set(x, 5, 8, slab(DBR) if x == 4 else AIR)
    b.set(4, 1, 8, B(PD))
    b.set(4, 2, 8, B("soul_lantern"))
    for (x, z) in ((3, 6), (5, 6), (2, 5), (6, 5), (4, 5)):
        b.set(x, 1, z, B("expanse:frostbloom"))
    b.pot(5, 1, 7, "north", ("brick", "brick", "snort_pottery_sherd", "brick"))
    return b


def pools():
    rp = random.Random(1847851)
    mats = (("snow_block", 3), ("gravel", 3), (CD, 1), ("packed_ice", 1))
    west, east, gate_w, gate_e = bailey_pieces()
    return {
        "start": [Piece(west, 1, PROC)],
        "east": [Piece(east, 1, PROC)],
        "gate_west": [Piece(gate_w, 1, PROC)],
        "gate_east": [Piece(gate_e, 1, PROC)],
        "wards": [Piece(ward_stables(), 3, PROC), Piece(ward_smithy(), 3, PROC), Piece(ward_inn(), 2, PROC),
                  Piece(ward_barracks(), 2, PROC)],
        "cellar": [Piece(cellar_ice(), 1, PROC)],
        "paths": [Piece(path_piece(f"{NAME}_path_a", 9, P_GROUNDS, rp, mats=mats), 3, PROC, "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_b", 15, P_GROUNDS, rp, mats=mats), 2, PROC, "terrain_matching"),
                  Empty(1)],
        "grounds": [Piece(ground_hunters_camp(), 3, PROC), Piece(ground_ice_fishing(), 2, PROC),
                    Piece(ground_cairn(), 2, PROC), Piece(ground_mammoth_bones(), 2, PROC),
                    Piece(ground_frozen_wagon(), 2, PROC), Piece(ground_frostbloom_shrine(), 2, PROC)],
    }
