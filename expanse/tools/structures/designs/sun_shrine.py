"""Sun shrine of the opal dunes: a walled courtyard with a sun mosaic, braziers
at the gate, and a small temple on a podium with a columned portico and a
great sun medallion. A sealed chamber inside the podium, under the sun disc
in the cella floor, holds the loot chest."""
import math

from blocks import AIR, B
from builder import Build, slab, stairs

NAME = "sun_shrine"
LOOT = "expanse:chests/sun_shrine"
OS = "expanse:opal_sandstone"


def sun_cell(dx, dy, big=True):
    """Block for the sun pattern at offset (dx, dy) from its centre, or None."""
    r = math.hypot(dx, dy)
    if r < 0.5:
        return B("glowstone") if big else B("expanse:chiseled_opal_sandstone")
    if r < 1.6:
        return B("expanse:chiseled_opal_sandstone")
    if r < 2.4:
        return B("orange_terracotta")
    if r < 3.5 and (dx == 0 or dy == 0 or abs(dx) == abs(dy)):
        return B("yellow_terracotta")
    if r < 3.5:
        return B("expanse:cut_opal_sandstone") if big else None
    return None


def build():
    b = Build(NAME, 15, 19, 19, seed=1847361)
    rng = b.rng
    X0, X1 = 1, 13

    # ---- ground course: smooth paving everywhere, courtyard sun mosaic
    b.fill(X0, 0, 0, X1, 0, 17, B("expanse:smooth_opal_sandstone"))
    ccx, ccz = 7, 13
    for x in range(X0 + 1, X1):
        for z in range(9, 17):
            s = sun_cell(x - ccx, z - ccz, big=False)
            if s is not None:
                b.set(x, 0, z, s)
            elif (x + z) % 2 == 0:
                b.set(x, 0, z, B("expanse:cut_opal_sandstone"))
    b.air(X0 + 1, 1, 9, X1 - 1, 4, 16)

    # ---- podium (y1-2) with the hidden chamber, floor at y3
    b.fill(X0, 1, 0, X1, 2, 8, B(OS))
    for x in range(X0, X1 + 1):
        for z in range(0, 9):
            if x in (X0, X1) or z in (0, 8):
                b.set(x, 2, z, B("expanse:cut_opal_sandstone"))
    for (x, z) in ((X0, 0), (X1, 0), (X0, 8), (X1, 8)):
        b.column(x, z, 1, 2, B("expanse:chiseled_opal_sandstone"))
    b.fill(X0, 3, 0, X1, 3, 8, B("expanse:smooth_opal_sandstone"))
    b.air(4, 1, 1, 10, 2, 4)                                  # sealed chamber
    # front steps
    for i, x in enumerate(range(5, 10)):
        b.set(x, 1, 11, stairs(OS, "north"))
        b.set(x, 1, 10, B(OS))
        b.set(x, 2, 10, stairs(OS, "north"))
        b.set(x, 1, 9, B(OS))
        b.set(x, 2, 9, B(OS))
        b.set(x, 3, 9, stairs(OS, "north"))
    for x in (4, 10):
        for z, y in ((9, 3), (10, 2), (11, 1)):
            b.fill(x, 1, z, x, y, z, B("expanse:cut_opal_sandstone"))
            b.set(x, y + 1, z, slab(OS))
        # obelisks flanking the steps
        b.column(x, 11, 1, 6, B("expanse:cut_opal_sandstone"))
        b.set(x, 1, 11, B("expanse:chiseled_opal_sandstone"))
        b.set(x, 7, 11, B("expanse:chiseled_opal_sandstone"))
        b.set(x, 8, 11, B("expanse:opal_sandstone_wall"))

    # ---- cella walls x2..12 z1..5, y4..9
    b.air(3, 4, 2, 11, 9, 4)
    for y in range(4, 10):
        for x in range(2, 13):
            for z in (1, 5):
                b.set(x, y, z, B(OS) if y % 3 else B("expanse:cut_opal_sandstone"))
        for z in range(1, 6):
            for x in (2, 12):
                b.set(x, y, z, B(OS) if y % 3 else B("expanse:cut_opal_sandstone"))
    for (x, z) in ((2, 1), (12, 1), (2, 5), (12, 5)):
        b.column(x, z, 4, 9, B("expanse:smooth_opal_sandstone"))
    # back-wall sun relief and side pilasters
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            c = sun_cell(dx, dy, big=False)
            if c is not None and abs(dx) + abs(dy) <= 3:
                b.set(7 + dx, 7 + dy, 1, c)
    b.set(7, 7, 1, B("expanse:chiseled_opal_sandstone"))
    for z in (1, 3, 5):
        for x in (2, 12):
            b.column(x, z, 4, 9, B("expanse:smooth_opal_sandstone"))
            b.set(x, 9, z, B("expanse:chiseled_opal_sandstone"))
    # doorway + side slits
    b.air(6, 4, 5, 8, 6, 5)
    b.set(6, 6, 5, stairs(OS, "east", "top"))
    b.set(8, 6, 5, stairs(OS, "west", "top"))
    for x in (2, 12):
        b.set(x, 6, 3, AIR)
        b.set(x, 7, 3, AIR)
    # portico: open area z6..8 with four columns at z8
    b.air(2, 4, 6, 12, 9, 7)
    b.air(2, 4, 8, 12, 9, 8)
    for x in (3, 5, 9, 11):
        b.set(x, 4, 8, B("expanse:chiseled_opal_sandstone"))
        b.column(x, 8, 5, 8, B("expanse:smooth_opal_sandstone"))
        b.set(x, 9, 8, B("expanse:chiseled_opal_sandstone"))
    # roof slab + cornice
    b.fill(2, 10, 1, 12, 10, 8, B("expanse:cut_opal_sandstone"))
    for x in range(1, 14):
        b.set(x, 10, 0, stairs(OS, "south", "top"))
        b.set(x, 10, 9, stairs(OS, "north", "top"))
    for z in range(1, 9):
        b.set(1, 10, z, stairs(OS, "east", "top"))
        b.set(13, 10, z, stairs(OS, "west", "top"))
    for x in range(2, 13):
        for z in (1, 8):
            b.set(x, 11, z, B("expanse:opal_sandstone_wall") if x % 2 else slab(OS))
    for z in range(2, 8):
        for x in (2, 12):
            b.set(x, 11, z, B("expanse:opal_sandstone_wall") if z % 2 else slab(OS))
    # sun medallion on the roof front
    for dx in range(-3, 4):
        for dy in range(-3, 4):
            s = sun_cell(dx, dy, big=True)
            if s is not None:
                b.set(7 + dx, 14 + dy, 8, s)
                b.set(7 + dx, 14 + dy, 7, B(OS))
    b.set(7, 18, 8, B("expanse:opal_sandstone_wall"))
    b.set(7, 18, 7, AIR)

    # ---- cella interior: sun floor, altar, banners, lights
    for dx in range(-2, 3):
        for dz in range(-1, 2):
            s = sun_cell(dx, dz * 1.0, big=False)
            if s is not None and abs(dx) + abs(dz) <= 2:
                b.set(7 + dx, 3, 3 + dz, s)
    b.set(7, 3, 3, B("expanse:chiseled_opal_sandstone"))   # the loose stone over the chamber
    b.set(7, 4, 2, B("expanse:chiseled_opal_sandstone"))
    b.set(6, 4, 2, stairs(OS, "east"))
    b.set(8, 4, 2, stairs(OS, "west"))
    b.set(7, 5, 2, B("decorated_pot", facing="south"),
          {"components": {}, "sherds": {"back": {"id": "minecraft:brick"}, "front": {"id": "minecraft:prize_pottery_sherd"},
                                        "right": {"id": "minecraft:brick"}, "left": {"id": "minecraft:brick"}},
           "id": "minecraft:decorated_pot"})
    b.set(6, 5, 2, B("yellow_candle", candles=3, lit=True))
    b.set(8, 5, 2, B("orange_candle", candles=2, lit=True))
    for x in (4, 10):
        b.banner(x, 8, 2, "yellow", [("circle", "orange"), ("flower", "yellow"), ("border", "orange")],
                 wall_facing="south")
    for x in (3, 11):
        b.set(x, 4, 4, B("decorated_pot", facing="south"))
        b.set(x, 4, 2, B("expanse:potted_heather" if x == 3 else "potted_dead_bush"))
    b.set(7, 9, 3, B("lantern", hanging=True))
    b.set(7, 9, 4, AIR)

    # ---- the hidden chamber
    b.chest(7, 1, 1, "south", LOOT)
    b.set(6, 1, 1, B("decorated_pot", facing="south"))
    b.set(8, 1, 1, B("decorated_pot", facing="south"))
    for x in (5, 9):
        b.set(x, 1, 1, B("candle", candles=3, lit=True))
    b.set(4, 1, 4, B("skeleton_skull", rotation=6))
    b.set(10, 1, 3, B("bone_block", axis="x"))
    b.set(10, 1, 4, B("expanse:opal_sand"))
    b.set(5, 1, 4, B("expanse:opal_sand"))
    b.set(4, 1, 3, B("cobweb"))
    b.set(9, 2, 1, B("cobweb"))

    # ---- courtyard wall, gate pillars with braziers
    for z in range(9, 18):
        for x in (X0, X1):
            b.set(x, 1, z, B("expanse:opal_sandstone_wall"))
    for x in range(X0, X1 + 1):
        if x < 5 or x > 9:
            b.set(x, 1, 17, B("expanse:opal_sandstone_wall"))
    for x in (X0, X1):
        for z in (9, 13, 17):
            b.set(x, 1, z, B("expanse:cut_opal_sandstone"))
            b.set(x, 2, z, slab(OS))
    for x in (5, 9):
        b.column(x, 17, 1, 3, B("expanse:cut_opal_sandstone"))
        b.set(x, 1, 17, B("expanse:chiseled_opal_sandstone"))
        b.set(x, 4, 17, B("expanse:chiseled_opal_sandstone"))
        b.campfire(x, 5, 17)
    for x in (6, 7, 8):
        b.set(x, 0, 17, B("expanse:chiseled_opal_sandstone") if x == 7 else B("expanse:cut_opal_sandstone"))
    # planters and pots
    for (x, z) in ((2, 10), (12, 10), (2, 16), (12, 16)):
        b.set(x, 0, z, B("coarse_dirt"))
        b.set(x, 1, z, B("dead_bush"))
    for (x, z) in ((3, 16), (11, 16)):
        b.set(x, 1, z, B("decorated_pot", facing="north"))
    for (x, z) in ((2, 13), (12, 13)):
        b.set(x, 1, z, B("potted_cactus"))
    b.set(12, 1, 11, B("water_cauldron", level=3))
    b.set(2, 1, 11, B("expanse:potted_palm_sapling"))
    b.set(4, 4, 7, B("lantern"))
    b.set(10, 4, 7, B("lantern"))
    return [b]
