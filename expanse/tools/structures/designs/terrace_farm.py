"""Rice farmers of the jade karst.

start pool (3 variants)
  paddies : three flooded terraces of young rice stepping up behind limestone walls,
            a scarecrow, a bamboo hut with a thatched roof (mat, low table, cooking pot,
            rice sacks); the family's savings are under the stack of sacks
  granary : a granary raised on staddle stones out of the rats' reach, a threshing
            floor, a millstone, baskets; the seed-rice box lies under the grain inside
  shelter : the buffalo's thatched shelter with trough and hay, a plough, a tiny
            field-god shrine with a stone lantern; a box buried under the shrine step
"""
from blocks import AIR, B
from builder import Build, item, slab, stairs
from furnish import scarecrow
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import book, clear, flora, ground_item, pick, trail

NAME = "terrace_farm"
STRUCTURE = dict(biomes=["jade_karst"], size=1, max_distance=32, set="biome_sights")
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
LB = "expanse:limestone_brick"
WALL = [("expanse:limestone_bricks", 4), ("expanse:mossy_limestone", 3), ("expanse:limestone", 2)]
GROUND = ["grass_block", "grass_block", "moss_block"]
PATH = [("gravel", 3), ("coarse_dirt", 3), ("dirt_path", 2)]
THATCH = "bamboo_mosaic"


def paddy(b, rng, x0, x1, z0, z1, y):
    """A flooded field at level y: bunds of earth round water planted with rice (seagrass) rows."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for yy in range(0, y):
                if b.get(x, yy, z) is None:
                    b.set(x, yy, z, B("dirt"))
            bund = x in (x0, x1) or z in (z0, z1) or (x - x0) % 5 == 0
            if bund:
                b.set(x, y, z, B("grass_block") if rng.random() < 0.7 else B("coarse_dirt"))
                b.set(x, y + 1, z, AIR)
            else:
                b.set(x, y, z, B("seagrass") if (z - z0) % 2 == 0 and rng.random() < 0.85 else
                      B("water", level=0))
                b.set(x, y - 1, z, B("mud"))
                b.set(x, y + 1, z, AIR)


def retaining_wall(b, rng, x0, x1, z, top):
    for x in range(x0, x1 + 1):
        for y in range(0, top + 1):
            b.set(x, y, z, pick(rng, WALL))
        if rng.random() < 0.35:
            b.set(x, top + 1, z, B("moss_carpet"))


def bamboo_hut(b, rng, x0, z0, x1, z1, y0=1):
    """Bamboo hut on stilts (floor y0), bamboo walls, a thatched gable roof (along x)."""
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.set(x, 0, z, B("expanse:limestone"))
        for y in range(1, y0 + 4):
            b.set(x, y, z, B("bamboo_block", axis="y"))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, y0, z, B("bamboo_planks") if b.get(x, y0, z) is None else b.get(x, y0, z))
            if (x in (x0, x1) or z in (z0, z1)) and b.get(x, y0 + 1, z) is None:
                for y in (y0 + 1, y0 + 2):
                    b.set(x, y, z, B("bamboo_planks") if y == y0 + 1 else B("bamboo_fence"))
    b.air(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y0 + 3, z1 - 1)
    from kit import roof_gable
    roof_gable(b, x0, x1, z0, z1, y0 + 3, THATCH, B("bamboo_planks"), along="x", overhang=1, gable_overhang=1,
               brackets=False)


def paddies():
    b = Build(f"{NAME}_paddies", 16, 10, 16, seed=1907481)
    rng = b.rng
    paddy(b, rng, 1, 14, 0, 4, 2)
    retaining_wall(b, rng, 0, 15, 5, 2)
    paddy(b, rng, 1, 14, 6, 9, 1)
    retaining_wall(b, rng, 0, 9, 10, 1)
    for x in (0, 15):
        for z in range(0, 10):
            for y in range(0, 3 if z < 5 else 2):
                b.set(x, y, z, pick(rng, WALL))
    for (x, z, top) in ((12, 5, 2), (12, 10, 1)):     # steps up the terraces
        b.set(x, top, z, stairs(LB, "north"))
        b.set(x, top + 1, z, AIR)
    scarecrow(b, 8, 2, 7, 160.0, rng)
    b.set(8, 1, 7, B("dirt"))
    # the hut on the bottom terrace, yard with the pot on the fire and the rice sacks
    bamboo_hut(b, rng, 1, 11, 6, 15)
    b.door(4, 2, 11, "bamboo", "north")
    b.set(4, 1, 10, AIR)
    b.bed(2, 2, 13, "east", "straw")
    b.set(5, 2, 14, slab("bamboo", "top"))
    ground_item(b, 5, 3, 14, book(BOOKS[NAME]), rotation=0)
    b.barrel(5, 2, 12, "up", LOOT)
    b.set(2, 2, 14, B("lantern"))
    b.cushion(4, 2, 14, color="green")
    b.campfire(9, 1, 13, lit=True)
    b.set(8, 1, 13, B("water_cauldron", level=2))
    for (x, z, y) in ((11, 12, 1), (12, 12, 1), (11, 13, 1), (11, 12, 2)):
        b.set(x, y, z, B("hay_block", axis="x"))
    b.chest(12, 0, 13, "north", HIDDEN)
    b.set(12, 1, 13, B("hay_block", axis="z"))
    trail(b, [(9, 11), (11, 14), (13, 15)], rng, PATH, width=1, fade=0.6)
    flora(b, [(x, z) for x in range(16) for z in range(11, 16)], rng, 0.2, GROUND)
    return b


def granary():
    b = Build(f"{NAME}_granary", 14, 10, 13, seed=1907483)
    rng = b.rng
    gx0, gz0, gx1, gz1 = 2, 2, 6, 6
    for (x, z) in ((gx0, gz0), (gx1, gz0), (gx0, gz1), (gx1, gz1)):   # staddle stones
        b.set(x, 0, z, B("expanse:limestone"))
        b.set(x, 1, z, B(f"expanse:limestone_wall"))
        b.set(x, 2, z, slab(LB, "bottom"))
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            b.set(x, 3, z, B("bamboo_planks"))
            if x in (gx0, gx1) or z in (gz0, gz1):
                for y in (4, 5):
                    b.set(x, y, z, B("bamboo_block", axis="y") if x in (gx0, gx1) and z in (gz0, gz1) else
                          B("bamboo_planks"))
    b.air(gx0 + 1, 4, gz0 + 1, gx1 - 1, 5, gz1 - 1)
    from kit import roof_gable
    roof_gable(b, gx0, gx1, gz0, gz1, 6, THATCH, B("bamboo_planks"), along="z", overhang=1, gable_overhang=1,
               brackets=False)
    b.set(4, 4, gz1, AIR)
    b.set(4, 5, gz1, AIR)
    for y in (1, 2):                                  # a post under the door carries the ladder
        b.set(4, y, gz1, B("bamboo_block", axis="y"))
    for y in (1, 2, 3):
        b.set(4, y, gz1 + 1, B("ladder", facing="south"))
    # inside: the grain heaped up, the seed-rice box under it
    for (x, z) in ((3, 3), (4, 3), (5, 3), (3, 4), (5, 4)):
        b.set(x, 4, z, B("hay_block", axis="y"))
    b.chest(4, 4, 4, "south", HIDDEN)                  # buried in the grain
    b.set(4, 5, 4, B("hay_block", axis="x"))
    b.set(4, 4, 5, B("hay_block", axis="y"))
    b.set(3, 5, 3, B("hay_block", axis="z"))
    b.barrel(5, 4, 5, "up", LOOT)
    # threshing floor, millstone, baskets, the flail
    for x in range(8, 13):
        for z in range(6, 11):
            if (x - 10) ** 2 + (z - 8) ** 2 <= 5:
                b.set(x, 0, z, B("smooth_stone"))
                b.set(x, 1, z, AIR)
    b.set(10, 1, 8, B("hay_block", axis="y"))
    b.set(12, 1, 6, B("grindstone", face="floor", facing="north"))
    b.set(8, 1, 10, B("composter", level=7))
    b.set(9, 1, 11, B("barrel", facing="up"))
    ground_item(b, 11, 1, 9, item("wheat"), rotation=3)
    b.set(7, 1, 3, slab("bamboo", "top"))
    ground_item(b, 7, 2, 3, book(BOOKS[f"{NAME}_granary"]), rotation=1)
    trail(b, [(10, 10), (11, 12)], rng, PATH, width=1, fade=0.4)
    flora(b, [(x, z) for x in range(14) for z in range(13)], rng, 0.2, GROUND)
    return b


def shelter():
    b = Build(f"{NAME}_shelter", 14, 8, 12, seed=1907485)
    rng = b.rng
    x0, z0, x1, z1 = 1, 1, 7, 5
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (4, z0)):
        b.set(x, 0, z, B("expanse:limestone"))
        for y in (1, 2, 3):
            b.set(x, y, z, B("bamboo_block", axis="y"))
    for x in range(x0, x1 + 1):                        # back wall of woven bamboo
        for y in (1, 2):
            if b.get(x, y, z0) is None:
                b.set(x, y, z0, B("bamboo_fence"))
    from kit import roof_gable
    roof_gable(b, x0, x1, z0, z1, 4, THATCH, B("bamboo_planks"), along="x", overhang=1, gable_overhang=1,
               brackets=False, air_inside=False)
    clear(b, x0 + 1, 1, z0 + 1, x1 - 1, 3, z1)
    for x in range(x0 + 1, x1):
        b.set(x, 0, z0 + 1, B("coarse_dirt"))
    for x in (2, 3):
        b.set(x, 1, z0 + 1, B("hay_block", axis="x"))
    b.set(2, 2, z0 + 1, B("hay_block", axis="x"))
    for x in (5, 6):
        b.set(x, 1, z0 + 1, B("water_cauldron", level=3))
    b.barrel(6, 1, 4, "up", LOOT)
    b.item_frame(4, 2, z0 + 1, "south", item("lead"))
    # the plough left in the furrows
    for x in range(9, 13):
        for z in range(2, 7):
            b.set(x, 0, z, B("farmland", moisture=3) if z % 2 else B("coarse_dirt"))
            b.set(x, 1, z, AIR)
    b.set(10, 1, 4, stairs("spruce", "east"))
    b.set(11, 1, 4, B("spruce_fence"))
    b.set(12, 1, 4, B("spruce_fence"))
    # the field god's shrine with a stone lantern, a box under its step
    b.set(10, 0, 9, B("expanse:limestone_bricks"))
    b.set(10, 1, 9, B("expanse:polished_limestone"))
    b.set(10, 2, 9, B("expanse:chiseled_limestone"))
    b.set(10, 3, 9, slab(LB))
    b.set(10, 1, 10, stairs(LB, "north"))
    b.chest(10, 0, 10, "north", HIDDEN)
    b.set(9, 0, 9, B("expanse:limestone_bricks"))
    b.set(9, 1, 9, B("red_candle", candles=2, lit=True))
    b.set(11, 1, 9, B("potted_bamboo"))
    b.set(12, 0, 11, B("expanse:limestone"))
    b.set(12, 1, 11, B("expanse:limestone_brick_wall"))
    b.set(12, 2, 11, B("lantern"))
    b.set(12, 3, 11, slab(LB))
    ground_item(b, 8, 1, 9, book(BOOKS[f"{NAME}_shelter"]), rotation=1)
    trail(b, [(4, 6), (6, 9), (7, 11)], rng, PATH, width=2, fade=0.5)
    flora(b, [(x, z) for x in range(14) for z in range(12)], rng, 0.18, GROUND)
    return b


def pools():
    return {"start": [Piece(paddies(), 3, "small_karst"), Piece(granary(), 2, "small_karst"),
                      Piece(shelter(), 2, "small_karst")]}


DATA = data_for(NAME, ["small_karst"])
