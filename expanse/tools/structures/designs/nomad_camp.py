"""Nomad camp of the amber steppe: two round felt yurts with striped conical
roofs, an open striped market canopy, a campfire with a roasting spit, log
benches, hay bales, barrels, banners on poles and the loot chest."""
import math

from blocks import AIR, B
from builder import Build
from common import circle_cells, ring_cells

NAME = "nomad_camp"
LOOT = "expanse:chests/nomad_camp"


def yurt(b, cx, cz, r, wall, band, roof_a, roof_b, door_to, rng):
    rim = ring_cells(cx, cz, r)
    inside = [c for c in circle_cells(cx, cz, r) if c not in rim]
    for (x, z) in inside:
        for y in range(1, 6):
            b.set(x, y, z, AIR)
    for (x, z) in rim:
        b.set(x, 1, z, B(f"{wall}_wool"))
        b.set(x, 2, z, B(f"{band}_wool"))
    # conical roof in radial stripes, overhanging the wall by one
    k = 0
    rr = r + 1.0
    while rr > 0.6:
        inner = set(circle_cells(cx, cz, rr - 1.6)) if rr > 1.6 else set()
        for (x, z) in circle_cells(cx, cz, rr):
            if (x, z) in inner:
                continue
            a = math.atan2(z - cz, x - cx)
            stripe = int((a + math.pi) / (2 * math.pi) * 8) % 2
            b.set(x, 3 + k, z, B(f"{roof_a if stripe else roof_b}_wool"), clip=True)
        rr -= 1.0
        k += 1
    top = 3 + k
    b.set(cx, top, cz, B("stripped_spruce_log", axis="y"))
    b.set(cx, top + 1, cz, B("spruce_fence"))
    # door facing the camp centre
    dx, dz = door_to[0] - cx, door_to[1] - cz
    best = min(rim, key=lambda c: math.hypot(c[0] - cx - dx * r / math.hypot(dx, dz),
                                             c[1] - cz - dz * r / math.hypot(dx, dz)))
    b.set(best[0], 1, best[1], AIR)
    b.set(best[0], 2, best[1], AIR)
    # rugs and a hanging lantern
    for (x, z) in inside:
        b.set(x, 1, z, B(rng.choice([f"{band}_carpet", f"{roof_a}_carpet", f"{roof_b}_carpet", f"{wall}_carpet"])))
    b.set(cx, top - 1, cz, B("lantern", hanging=True))
    return best, inside


def build():
    b = Build(NAME, 17, 9, 17, seed=1847541)
    rng = b.rng

    # ---- campfire with stone ring and a roasting spit
    fx, fz = 8, 9
    for (x, z) in circle_cells(fx, fz, 1.6):
        b.set(x, 0, z, B(rng.choice(["cobblestone", "stone", "coarse_dirt", "gravel"])))
    b.campfire(fx, 1, fz)
    for x in (fx - 1, fx + 1):
        b.set(x, 1, fz, B("spruce_fence"))
        b.set(x, 2, fz, B("spruce_fence"))
    b.set(fx, 2, fz, B("spruce_fence"))
    b.air(fx, 3, fz, fx, 4, fz)
    # trampled ground around the fire
    for (x, z) in circle_cells(fx, fz, 3.6):
        if b.get(x, 0, z) is None and rng.random() < 0.55:
            b.set(x, 0, z, B(rng.choice(["coarse_dirt", "dirt_path", "dirt_path", "rooted_dirt"])))
    # log benches
    b.set(fx - 3, 1, fz, B("stripped_spruce_log", axis="z"))
    b.set(fx - 3, 1, fz + 1, B("stripped_spruce_log", axis="z"))
    b.set(fx + 3, 1, fz - 1, B("stripped_spruce_log", axis="z"))
    b.set(fx + 3, 1, fz, B("stripped_spruce_log", axis="z"))
    b.set(fx, 1, fz + 3, B("stripped_spruce_log", axis="x"))

    # ---- yurts (A warm colours with the chest, B cool colours)
    door_a, in_a = yurt(b, 4, 4, 3.2, "white", "red", "orange", "red", (fx, fz), rng)
    door_b, in_b = yurt(b, 13, 5, 3.2, "white", "blue", "cyan", "light_blue", (fx, fz), rng)
    b.bed(2, 1, 4, "north", "red")
    b.chest(4, 1, 2, "south", LOOT)
    b.set(6, 1, 3, B("barrel", facing="up"))
    b.set(6, 1, 5, B("loom", facing="west"))
    b.set(3, 1, 6, B("decorated_pot", facing="north"))
    b.bed(15, 1, 6, "north", "light_blue")
    b.set(14, 1, 3, B("barrel", facing="up"))
    b.set(11, 1, 4, B("cartography_table"))
    b.set(15, 1, 4, B("decorated_pot", facing="west"))
    b.set(12, 1, 3, B("composter", level=0))

    # ---- open striped canopy (market awning) to the south
    cx0, cx1, cz0, cz1 = 2, 7, 12, 15
    for (x, z) in ((cx0, cz0), (cx1, cz0), (cx0, cz1), (cx1, cz1)):
        b.column(x, z, 1, 3, B("spruce_fence"))
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            b.set(x, 4, z, B("yellow_wool") if x % 2 else B("white_wool"))
    b.air(cx0 + 1, 1, cz0, cx1 - 1, 3, cz1, only_void=True)
    b.air(cx0, 1, cz0 + 1, cx1, 3, cz1 - 1, only_void=True)
    for x in range(cx0 + 1, cx1):
        for z in range(cz0 + 1, cz1):
            b.set(x, 1, z, B("brown_carpet") if (x + z) % 3 else B("orange_carpet"))
    b.set(3, 1, 15, B("barrel", facing="up"))
    b.set(4, 1, 15, B("barrel", facing="up"))
    b.set(3, 2, 15, B("barrel", facing="north"))
    b.set(6, 1, 15, B("hay_block", axis="x"))
    b.set(6, 1, 14, B("hay_block", axis="z"))
    b.set(6, 2, 15, B("hay_block", axis="y"))
    b.set(3, 1, 13, B("decorated_pot", facing="east"))
    b.set(5, 3, 14, B("lantern", hanging=True))

    # ---- hay stack, water trough and a hitching rail to the east
    for (x, y, z, ax) in ((12, 1, 13, "x"), (13, 1, 13, "x"), (12, 1, 14, "z"), (13, 1, 14, "z"), (12, 2, 13, "y"),
                          (14, 1, 14, "y")):
        b.set(x, y, z, B("hay_block", axis=ax))
    b.set(15, 1, 11, B("water_cauldron", level=3))
    for x in (11, 15):
        b.set(x, 1, 16, B("spruce_fence"))
    for x in range(12, 15):
        b.set(x, 1, 16, B("spruce_fence"))

    # ---- banners on poles
    for (x, z, color, pats, rot) in (
            (1, 9, "orange", [("rhombus", "yellow"), ("border", "brown"), ("triangles_top", "red")], 12),
            (16, 9, "light_blue", [("circle", "white"), ("stripe_bottom", "blue"), ("border", "cyan")], 4),
            (9, 1, "red", [("triangle_bottom", "yellow"), ("triangles_bottom", "orange")], 0)):
        b.column(x, z, 1, 2, B("spruce_fence"))
        b.banner(x, 3, z, color, pats, rotation=rot)
    return [b]

