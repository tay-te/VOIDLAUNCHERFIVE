"""Frost outpost: a low spruce-and-cobblestone hunters' lodge half buried in
snow drifts (the north eave is snowed under), a mammoth-tusk arch of bone
blocks over the yard entrance, a campfire with log seats, a hide-drying
rack, a woodpile and a small roofed lookout."""
from blocks import AIR, B
from builder import Build, trapdoor
from common import gable_roof

NAME = "frost_outpost"
LOOT = "expanse:chests/frost_outpost"


def build():
    b = Build(NAME, 15, 10, 18, seed=1847511)
    rng = b.rng
    x0, x1, z0, z1 = 2, 10, 3, 9

    # ---- lodge: cobble footing, log walls, spruce floor
    b.fill(x0, 0, z0, x1, 0, z1, B("cobblestone"))
    b.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, B("spruce_planks"))
    b.air(x0 + 1, 1, z0 + 1, x1 - 1, 6, z1 - 1)
    for y in range(1, 4):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                if corner:
                    b.set(x, y, z, B("cobblestone") if y < 3 else B("mossy_cobblestone"))
                elif y == 1:
                    b.set(x, y, z, B("cobblestone"))
                else:
                    b.set(x, y, z, B("spruce_log", axis="x" if z in (z0, z1) else "z"))
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            b.set(x, 3, z, B("stripped_spruce_log", axis="x"))
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            b.set(x, 3, z, B("stripped_spruce_log", axis="z"))
    top = gable_roof(b, x0, x1, z0, z1, 3, "spruce", B("spruce_planks"), overhang=1, ridge=B("spruce_planks"))
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            for y in range(4, 7):
                if b.get(x, y, z) is None:
                    b.set(x, y, z, AIR)
    # door, windows, shutters
    door_x = 6
    b.door(door_x, 1, z1, "spruce", "south")
    for (x, z) in ((4, z1), (8, z1), (x1, 6)):
        b.set(x, 2, z, B("glass_pane"))
    for x in (3, 5, 7, 9):
        b.set(x, 2, z1 + 1, trapdoor("spruce", "south", "bottom", True))
    for x in range(x0 + 1, x1):                       # ceiling beam carrying the lanterns
        b.set(x, 3, 6, B("stripped_spruce_log", axis="x"))

    # ---- interior
    b.bed(3, 1, 5, "north", "white")
    b.chest(4, 1, 4, "south", LOOT)
    b.set(5, 1, 4, B("barrel", facing="up"))
    b.set(5, 2, 4, B("lantern"))
    b.set(9, 1, 4, B("smoker", facing="south"))
    b.set(8, 1, 4, B("furnace", facing="south"))
    b.set(9, 1, 5, B("crafting_table"))
    b.set(9, 1, 8, B("barrel", facing="west"))
    b.set(9, 2, 8, B("barrel", facing="up"))
    b.set(3, 1, 8, B("fletching_table"))
    for (x, z) in ((5, 6), (6, 6), (5, 7), (6, 7), (7, 6)):
        b.set(x, 1, z, B("white_carpet") if (x + z) % 2 else B("light_gray_carpet"))
    b.set(4, 2, 6, B("lantern", hanging=True))
    b.set(8, 2, 6, B("lantern", hanging=True))
    b.set(x0 + 1, 2, 7, B("skeleton_wall_skull", facing="east"))

    # ---- snow drifts (heights in snow layers; 8 = one block). The north drift buries the eave.
    NORTH, WEST, EAST = (27, 20, 11), (22, 12), (9, 4)
    for x in range(0, 15):
        for z in range(0, z1 + 1):
            if x0 <= x <= x1 and z0 <= z <= z1:
                continue
            h = 0
            if z < z0 and z0 - 1 - z < len(NORTH):
                h = max(h, NORTH[z0 - 1 - z] - (2 if x in (0, 14) else 0))
            if x < x0 and x0 - 1 - x < len(WEST):
                h = max(h, WEST[x0 - 1 - x])
            if x > x1 and x - x1 - 1 < len(EAST) and z <= z1:
                h = max(h, EAST[x - x1 - 1])
            if h <= 0:
                continue
            h -= rng.randint(0, 2)
            y = 1
            while h >= 8:
                cur = b.get(x, y, z)
                if cur is None or (cur.short == "spruce_stairs" and y <= 3):
                    b.set(x, y, z, B("snow_block"))
                elif cur.short != "snow_block":
                    h = 0
                    break
                h -= 8
                y += 1
            if 0 < h < 8 and b.get(x, y, z) is None:
                b.set(x, y, z, B("snow", layers=h))
    for x in range(x0 - 1, x1 + 2):          # snow along the ridge (full blocks carry layers)
        if b.get(x, top + 1, (z0 + z1) // 2) is None:
            b.set(x, top + 1, (z0 + z1) // 2, B("snow", layers=2))

    # ---- yard: snowy ground, path, campfire ring and seats
    for x in range(2, 13):
        for z in range(z1 + 1, 18):
            if b.get(x, 0, z) is None:
                r = rng.random()
                b.set(x, 0, z, B("packed_ice") if r < 0.05 else B("coarse_dirt") if r < 0.25 else B("snow_block"))
            if b.get(x, 1, z) is None:
                b.set(x, 1, z, AIR)
    for z in range(z1 + 1, 18):
        b.set(door_x, 0, z, B("dirt_path") if z % 3 else B("gravel"))
    fx, fz = 9, 13
    b.campfire(fx, 1, fz)
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.set(fx + dx, 0, fz + dz, B("cobblestone"))
    for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        b.set(fx + dx, 0, fz + dz, B("stone"))
    b.set(fx + 2, 1, fz, B("spruce_log", axis="z"))
    b.set(fx, 1, fz + 2, B("spruce_log", axis="x"))
    b.set(fx + 1, 1, fz + 2, B("spruce_log", axis="x"))

    # ---- mammoth-tusk arch over the path: two curved tusks, a skull between the tips
    az = 16
    tusk = [(3, 0, "y"), (3, 1, "y"), (3, 2, "y"), (3, 3, "y"), (4, 3, "x"), (4, 4, "y"), (5, 4, "x"), (5, 5, "x")]
    for (x, y, ax) in tusk:
        b.set(x, y, az, B("bone_block", axis=ax))
        b.set(12 - x, y, az, B("bone_block", axis=ax))
    b.set(6, 5, az, B("skeleton_skull", rotation=0))
    b.air(4, 1, az, 8, 2, az)
    b.air(5, 3, az, 7, 3, az)
    b.set(6, 4, az, AIR)

    # ---- hide-drying rack and woodpile
    for z in (11, 14):
        b.column(1, z, 1, 2, B("spruce_fence"))
        b.set(1, 3, z, B("spruce_slab"))
    for z in (12, 13):
        b.set(1, 2, z, B("spruce_fence"))
        b.set(1, 3, z, B("brown_wool") if z == 12 else B("white_wool"))
    for (x, y) in ((12, 1), (13, 1), (12, 2), (13, 2), (14, 1)):
        b.set(x, y, 10, B("spruce_log", axis="z"))
        b.set(x, y, 11, B("spruce_log", axis="z"))
    b.set(12, 3, 10, B("snow", layers=2))
    b.set(13, 3, 10, B("snow", layers=1))

    # ---- lookout on the east side: four posts, railed platform, slab roof, ladder
    lz0, lz1 = 4, 7
    for (x, z) in ((12, lz0), (14, lz0), (12, lz1), (14, lz1)):
        b.column(x, z, 0, 5, B("spruce_log", axis="y"))
        b.column(x, z, 7, 8, B("spruce_fence"))
    b.fill(12, 6, lz0, 14, 6, lz1, B("spruce_planks"))
    for x in range(12, 15):
        for z in range(lz0, lz1 + 1):
            if (x in (12, 14) or z in (lz0, lz1)) and b.get(x, 7, z) is None:
                b.set(x, 7, z, B("spruce_fence"))
    b.fill(12, 9, lz0, 14, 9, lz1, B("spruce_slab", type="bottom"))
    b.set(13, 9, lz0 + 1, B("spruce_planks"))
    b.air(13, 7, lz0 + 1, 13, 8, lz1 - 1)
    b.set(13, 8, lz0 + 1, B("lantern", hanging=True))
    for y in range(1, 7):
        b.set(12, y, lz1 + 1, B("ladder", facing="south"))
    b.set(12, 7, lz1, AIR)
    return [b]
