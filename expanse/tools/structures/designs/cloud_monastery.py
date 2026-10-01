"""Cloud monastery: a small mossy stone-brick cloister with red tile roofs - a
library hall to the north, a dormitory to the west, covered arcades on the
east and south, a bell tower in the south-east corner and a walled garden
courtyard with a well, flowers and a flowering tree."""
from blocks import AIR, B
from builder import Build, slab, stairs
from common import gable_roof, hip_roof, vines_on, weather

NAME = "cloud_monastery"
LOOT = "expanse:chests/cloud_monastery"
ROOF = "brick"
SB = "stone_brick"


def stone(rng):
    r = rng.random()
    return B("mossy_stone_bricks") if r < 0.3 else B("cracked_stone_bricks") if r < 0.38 else B("stone_bricks")


def box_walls(b, x0, z0, x1, z1, y0, y1, rng):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    corner = x in (x0, x1) and z in (z0, z1)
                    b.set(x, y, z, B("chiseled_stone_bricks") if corner and y == y0 else
                          B("stone_bricks") if corner else stone(rng))
    b.air(x0 + 1, y0, z0 + 1, x1 - 1, y1 + 4, z1 - 1)


def build():
    b = Build(NAME, 17, 23, 17, seed=1847571)
    rng = b.rng

    # ---- foundation / courtyard ground
    b.fill(1, 0, 1, 15, 0, 15, B("stone_bricks"))
    for x in range(6, 13):
        for z in range(7, 13):
            b.set(x, 0, z, B("grass_block"))
    for x in range(6, 13):
        b.set(x, 0, 10, B("gravel"))
    for z in range(7, 13):
        b.set(9, 0, z, B("gravel"))

    # ---- library hall (north): x1..15 z1..6, walls y1..5
    box_walls(b, 1, 1, 15, 6, 1, 5, rng)
    b.fill(2, 0, 2, 14, 0, 5, B("spruce_planks"))
    for x in range(2, 15):
        b.set(x, 5, 1, B("stone_bricks"))
        b.set(x, 5, 6, B("stone_bricks"))
    gable_roof(b, 1, 15, 1, 6, 5, ROOF, B("stone_bricks"), overhang=1, ridge=B("brick_slab", type="bottom"))
    for x in range(2, 15):
        for z in range(2, 6):
            for y in range(6, 9):
                if b.get(x, y, z) is None:
                    b.set(x, y, z, AIR)
    # tall arched windows (north) and round window in the gables
    for x in (4, 8, 12):
        b.set(x, 2, 1, B("glass_pane"))
        b.set(x, 3, 1, B("glass_pane"))
        b.set(x, 4, 1, B("glass_pane"))
    for gx in (1, 15):
        b.set(gx, 7, 3, B("glass_pane"))
        b.set(gx, 7, 4, B("glass_pane"))
    # doors into the courtyard
    b.door(9, 1, 6, "spruce", "south")
    b.door(14, 1, 6, "spruce", "south")
    for x in (7, 11):
        b.set(x, 2, 6, B("glass_pane"))
        b.set(x, 3, 6, B("glass_pane"))
    # interior: bookshelves, lecterns, reading table, altar and the chest
    for x in range(2, 15):
        if x in (4, 8, 12):
            continue
        for y in (1, 2, 3):
            b.set(x, y, 2, B("bookshelf") if (x + y) % 5 else B("chiseled_bookshelf", facing="south",
                                                                       slot_0_occupied=True, slot_2_occupied=True,
                                                                       slot_3_occupied=True))
    for x in (4, 8, 12):
        b.set(x, 1, 3, B("lectern", facing="south"))
    for x in range(5, 8):
        b.set(x, 1, 4, B("spruce_fence"))
        b.set(x, 2, 4, slab("spruce", "bottom"))
    b.set(6, 3, 4, B("candle", candles=3, lit=True))
    b.set(5, 1, 5, stairs("spruce", "north"))
    b.set(7, 1, 5, stairs("spruce", "north"))
    b.set(2, 1, 3, B("chiseled_stone_bricks"))
    b.set(2, 1, 4, B("chiseled_stone_bricks"))
    b.set(2, 2, 3, B("white_candle", candles=4, lit=True))
    b.set(2, 2, 4, B("potted_azalea_bush"))
    b.chest(13, 1, 3, "west", LOOT)
    b.set(13, 1, 4, B("barrel", facing="up"))
    b.set(10, 1, 4, B("green_carpet"))
    b.set(11, 1, 4, B("green_carpet"))
    for x in (4, 8, 12):
        b.set(x, 5, 4, B("lantern", hanging=True))
    for x in range(2, 15):
        b.set(x, 6, 4, B("stripped_spruce_log", axis="x"))

    # ---- dormitory (west): x1..5 z7..15
    box_walls(b, 1, 7, 5, 15, 1, 4, rng)
    b.fill(2, 0, 8, 4, 0, 14, B("spruce_planks"))
    gable_roof(b, 1, 5, 7, 15, 4, ROOF, B("stone_bricks"), overhang=1, gable_x=False,
               ridge=B("brick_slab", type="bottom"))
    for x in range(2, 5):
        for z in range(8, 15):
            for y in range(5, 7):
                if b.get(x, y, z) is None:
                    b.set(x, y, z, AIR)
    b.door(5, 1, 10, "spruce", "east")
    for z in (8, 13):
        b.set(5, 2, z, B("glass_pane"))
        b.set(1, 2, z, B("glass_pane"))
    b.set(1, 2, 11, B("glass_pane"))
    b.bed(2, 1, 9, "north", "brown")
    b.bed(2, 1, 12, "north", "brown")
    b.bed(4, 1, 14, "north", "brown")
    b.set(2, 1, 14, B("barrel", facing="up"))
    b.set(2, 1, 10, B("barrel", facing="east"))
    b.set(4, 1, 8, B("crafting_table"))
    b.set(3, 4, 11, B("lantern", hanging=True))
    for z in range(8, 15):
        if b.get(3, 5, z) in (None, AIR):
            b.set(3, 5, z, B("stripped_spruce_log", axis="z"))

    # ---- bell tower (south-east): x12..15 z12..15, walls to y17, belfry y14..16, hip roof above
    box_walls(b, 12, 12, 15, 15, 1, 17, rng)
    b.air(13, 1, 13, 14, 17, 14)
    b.door(12, 1, 13, "spruce", "west")
    for y in range(1, 14):
        b.set(14, y, 14, B("ladder", facing="north"))
    b.fill(13, 13, 13, 14, 13, 14, B("spruce_planks"))
    b.set(14, 13, 14, B("ladder", facing="north"))
    for y in (6, 10):                                  # narrow windows on the shaft
        for (x, z) in ((13, 12), (15, 13), (14, 15)):
            b.set(x, y, z, B("iron_bars"))
    # belfry: two-high arched openings on every side
    for y in (14, 15):
        for (x, z) in ((13, 12), (14, 12), (13, 15), (14, 15), (12, 13), (12, 14), (15, 13), (15, 14)):
            b.set(x, y, z, AIR)
    for (x, z, f) in ((13, 12, "east"), (14, 12, "west"), (13, 15, "east"), (14, 15, "west")):
        b.set(x, 16, z, stairs(SB, f, "top"))
    for (x, z, f) in ((12, 13, "south"), (12, 14, "north"), (15, 13, "south"), (15, 14, "north")):
        b.set(x, 16, z, stairs(SB, f, "top"))
    b.fill(12, 17, 12, 15, 17, 15, B("stone_bricks"))
    for x in range(11, 17):
        for z in (11, 16):
            b.set(x, 17, z, stairs(SB, "south" if z == 11 else "north", "top"))
    for z in range(12, 16):
        b.set(11, 17, z, stairs(SB, "east", "top"))
        b.set(16, 17, z, stairs(SB, "west", "top"))
    hip_roof(b, 11, 16, 11, 16, 18, ROOF, cap=B("bricks"))
    b.set(13, 21, 13, B("bricks"))
    b.set(13, 22, 13, B("lightning_rod"))
    b.bell(13, 15, 14, attachment="ceiling", facing="east")
    b.set(13, 16, 14, B("stone_bricks"))
    b.set(13, 16, 13, B("stone_bricks"))

    # ---- arcades (east x13..15 z7..11, south x6..11 z13..15)
    def arcade(x0, z0, x1, z1, inner_axis, inner):
        b.air(x0, 1, z0, x1, 4, z1)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                b.set(x, 0, z, B("polished_andesite") if (x + z) % 2 else B("stone_bricks"))
        # outer wall with windows
        if inner_axis == "x":       # columns along x = inner
            outer = x1
            for z in range(z0, z1 + 1):
                for y in range(1, 4):
                    b.set(outer, y, z, stone(rng) if not (y == 2 and z % 2) else B("iron_bars"))
                if (z - z0) % 2 == 0:
                    b.column(inner, z, 1, 3, B("stone_brick_wall"))
                    b.set(inner, 0, z, B("chiseled_stone_bricks"))
            for z in range(z0 - 1, z1 + 1):
                b.set(inner - 1, 4, z, stairs(ROOF, "east"))
                for x in range(inner, outer + 1):
                    b.set(x, 4, z, B("bricks") if x == outer else stairs(ROOF, "east", "top") if x == inner
                          else slab(ROOF, "top"))
                b.set(outer, 5, z, stairs(ROOF, "west"))
        else:
            outer = z1
            for x in range(x0, x1 + 1):
                for y in range(1, 4):
                    b.set(x, y, outer, stone(rng) if not (y == 2 and x % 2) else B("iron_bars"))
                if (x - x0) % 2 == 0:
                    b.column(x, inner, 1, 3, B("stone_brick_wall"))
                    b.set(x, 0, inner, B("chiseled_stone_bricks"))
            for x in range(x0, x1 + 1):
                b.set(x, 4, inner - 1, stairs(ROOF, "south"))
                for z in range(inner, outer + 1):
                    b.set(x, 4, z, B("bricks") if z == outer else stairs(ROOF, "south", "top") if z == inner
                          else slab(ROOF, "top"))
                b.set(x, 5, outer, stairs(ROOF, "north"))

    arcade(13, 7, 15, 11, "x", 13)
    arcade(6, 13, 11, 15, "z", 13)
    # gateway through the south arcade
    b.set(8, 1, 15, AIR)
    b.set(8, 2, 15, AIR)
    b.set(9, 1, 15, AIR)
    b.set(9, 2, 15, AIR)
    b.set(8, 3, 15, stairs(SB, "east", "top"))
    b.set(9, 3, 15, stairs(SB, "west", "top"))
    for x in (8, 9):
        b.set(x, 0, 16, B("stone_bricks"))
    for x in (7, 10):                                    # gate lanterns on wall posts
        b.set(x, 1, 16, B("mossy_stone_brick_wall"))
        b.set(x, 2, 16, B("lantern"))

    # ---- courtyard garden: well, flowers, a flowering azalea tree
    b.air(6, 1, 7, 12, 6, 12, only_void=True)
    b.set(9, 1, 10, B("water_cauldron", level=3))
    for (x, z) in ((8, 9), (10, 9), (8, 11), (10, 11)):
        b.set(x, 1, z, B("mossy_stone_brick_wall"))
        b.set(x, 2, z, B("spruce_fence"))
        b.set(x, 3, z, slab("spruce"))
    b.set(9, 3, 9, slab("spruce"))
    b.set(9, 3, 11, slab("spruce"))
    b.set(8, 3, 10, slab("spruce"))
    b.set(10, 3, 10, slab("spruce"))
    b.set(9, 3, 10, B("spruce_planks"))
    b.set(9, 2, 10, B("lantern", hanging=True))
    # tree in the north-west corner of the garden
    tx, tz = 7, 8
    for y in range(1, 5):
        b.set(tx, y, tz, B("oak_log", axis="y"))
    for (x, z) in [(x, z) for x in range(5, 10) for z in range(6, 11)]:
        for y, r in ((4, 2.2), (5, 2.0), (6, 1.2)):
            if (x - tx) ** 2 + (z - tz) ** 2 <= r * r and b.get(x, y, z) in (None, AIR):
                b.set(x, y, z, B("flowering_azalea_leaves") if rng.random() < 0.5 else B("azalea_leaves"),
                      clip=True)
    flowers = ["poppy", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "lily_of_the_valley", "fern"]
    for x in range(6, 13):
        for z in range(7, 13):
            if b.get(x, 0, z).short == "grass_block" and b.get(x, 1, z) in (None, AIR) and rng.random() < 0.45:
                b.set(x, 1, z, B(rng.choice(flowers)))
    for (x, z) in ((6, 12), (12, 7), (12, 12)):
        b.set(x, 1, z, B("sweet_berry_bush", age=3))
    # lanterns on the arcade columns and outside the doors
    vines_on(b, rng, 0.05, ymin=2, region=lambda x, y, z: y < 6, max_len=3)
    weather(b, {"minecraft:stone_bricks": [B("mossy_stone_bricks"), B("cracked_stone_bricks")]}, rng, 0.12,
            region=lambda x, y, z: y >= 1)
    return [b]
