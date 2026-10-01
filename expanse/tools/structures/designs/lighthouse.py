"""Palm coast lighthouse: a round red-and-white striped tower on a stone
plinth, spiral stair inside, quartz gallery with iron railing, glass lantern
room with a sea-lantern lamp, red cap with a lightning rod, and the keeper's
little palm-wood cottage beside it (with the loot chest)."""

from blocks import AIR, B
from builder import Build, slab, stairs, trapdoor
from common import circle_cells, ring_cells, spiral_floor_ok, spiral_stair

NAME = "lighthouse"
LOOT = "expanse:chests/lighthouse"
CX, CZ = 5, 5
TOP = 20          # last shaft course
GAL = 21          # gallery floor


def build():
    b = Build(NAME, 17, 30, 11, seed=1847451)
    rng = b.rng
    shell = ring_cells(CX, CZ, 3.4)
    inside = [c for c in circle_cells(CX, CZ, 3.4) if c not in shell]

    # ---- plinth
    for (x, z) in circle_cells(CX, CZ, 4.6):
        b.set(x, 0, z, rng.choice(["stone_bricks", "cobblestone", "mossy_stone_bricks", "andesite", "stone_bricks"]))
    for (x, z) in circle_cells(CX, CZ, 4.6):
        if (x, z) not in circle_cells(CX, CZ, 3.4):
            dx, dz = x - CX, z - CZ
            f = ("west" if dx > 0 else "east") if abs(dx) >= abs(dz) else ("north" if dz > 0 else "south")
            b.set(x, 1, z, stairs("stone_brick", f))

    # ---- shaft with stripes
    for y in range(1, TOP + 1):
        band = (y - 1) // 3
        for (x, z) in shell:
            if y == 1:
                b.set(x, y, z, B("stone_bricks"))
            else:
                b.set(x, y, z, B("white_concrete") if band % 2 == 0 else B("red_concrete"))
        for (x, z) in inside:
            b.set(x, y, z, AIR)
    for (x, z) in inside:
        b.set(x, 1, z, B("smooth_stone"))
        b.set(x, 0, z, B("stone_bricks"))

    # ---- spiral stair to the gallery
    steps = spiral_stair(b, CX, CZ, 2, GAL, "quartz", B("quartz_pillar", axis="y"), start=3,
                         underside=slab("quartz", "top"))

    # ---- door (east) and windows
    b.door(CX + 3, 2, CZ, "expanse:palm", "east")
    b.set(CX + 3, 4, CZ, B("quartz_block"))
    b.set(CX + 4, 1, CZ, stairs("stone_brick", "west"))
    for (x, z, y) in ((CX, CZ - 3, 7), (CX - 3, CZ, 11), (CX, CZ + 3, 14), (CX + 3, CZ, 17), (CX - 3, CZ, 5)):
        b.set(x, y, z, B("glass_pane"))
        b.set(x, y + 1, z, B("glass_pane"))
    # interior lights on the arm cells (outside the spiral's 3x3)
    for (x, z, y, f) in ((CX - 2, CZ, 7, "east"), (CX + 2, CZ, 12, "west"), (CX, CZ - 2, 17, "south"),
                         (CX, CZ + 2, 10, "north")):
        b.set(x, y, z, B("wall_torch", facing=f))
    for (x, z) in ((CX - 2, CZ - 1), (CX + 2, CZ + 1)):
        b.set(x, 2, z, B("barrel", facing="up"))
    b.set(CX - 2, 2, CZ + 1, B("lantern"))

    # ---- gallery on corbels, iron railing
    corbel = [c for c in circle_cells(CX, CZ, 4.6) if c not in circle_cells(CX, CZ, 3.4)]
    for (x, z) in corbel:
        dx, dz = x - CX, z - CZ
        f = ("west" if dx > 0 else "east") if abs(dx) >= abs(dz) else ("north" if dz > 0 else "south")
        b.set(x, TOP, z, stairs("quartz", f, "top"))
    for (x, z) in circle_cells(CX, CZ, 4.6):
        if spiral_floor_ok(steps, x, z, GAL) and (x, z) != (CX, CZ):
            b.set(x, GAL, z, B("smooth_quartz"))
    for (x, z) in ring_cells(CX, CZ, 4.6):
        b.set(x, GAL + 1, z, B("iron_bars"))
    for (x, z) in circle_cells(CX, CZ, 4.6):
        if b.get(x, GAL + 1, z) is None:
            b.set(x, GAL + 1, z, AIR)
            b.set(x, GAL + 2, z, AIR)

    # ---- lantern room: quartz posts, glass, lamp on the pillar
    room = ring_cells(CX, CZ, 2.3)
    for y in range(GAL + 1, GAL + 4):
        for (x, z) in room:
            dx, dz = x - CX, z - CZ
            post = abs(dx) == abs(dz)
            b.set(x, y, z, B("quartz_pillar", axis="y") if post else B("glass_pane"))
        for (x, z) in circle_cells(CX, CZ, 2.3):
            if (x, z) not in room:
                b.set(x, y, z, AIR)
    b.set(CX + 2, GAL + 1, CZ, AIR)      # hatch out to the gallery
    b.set(CX + 2, GAL + 2, CZ, AIR)
    b.set(CX, GAL + 1, CZ, B("quartz_pillar", axis="y"))
    b.set(CX, GAL + 2, CZ, B("sea_lantern"))
    b.set(CX, GAL + 3, CZ, B("sea_lantern"))
    # roof cap
    for (x, z) in circle_cells(CX, CZ, 3.0):
        b.set(x, GAL + 4, z, B("red_concrete"))
    for (x, z) in ring_cells(CX, CZ, 3.0):
        b.set(x, GAL + 4, z, B("red_concrete"))
    for (x, z) in circle_cells(CX, CZ, 2.0):
        b.set(x, GAL + 5, z, B("red_concrete"))
    for (x, z) in circle_cells(CX, CZ, 1.0):
        b.set(x, GAL + 6, z, B("red_concrete"))
    b.set(CX, GAL + 7, CZ, B("quartz_block"))
    b.set(CX, GAL + 8, CZ, B("lightning_rod"))

    # ---- keeper's cottage (x 11..15, z 3..7)
    X0, X1, Z0, Z1 = 11, 15, 3, 7
    P = "expanse:palm"
    b.fill(X0, 0, Z0, X1, 0, Z1, B("cobblestone"))
    b.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, B(f"{P}_planks"))
    b.air(X0 + 1, 1, Z0 + 1, X1 - 1, 6, Z1 - 1)
    for y in range(1, 5):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if x in (X0, X1) or z in (Z0, Z1):
                    corner = x in (X0, X1) and z in (Z0, Z1)
                    b.set(x, y, z, B("expanse:stripped_palm_log", axis="y") if corner else
                          B("white_terracotta") if y < 3 else B(f"{P}_planks"))
    # mangrove (red) gable roof running along z
    top = 4
    for k in range(0, 4):
        xw, xe = X0 - 1 + k, X1 + 1 - k
        if xw > xe:
            break
        for z in range(Z0 - 1, Z1 + 2):
            if xw == xe:
                b.set(xw, top + k, z, B("mangrove_slab", type="bottom"))
            else:
                b.set(xw, top + k, z, stairs("mangrove", "east"))
                b.set(xe, top + k, z, stairs("mangrove", "west"))
        for gz in (Z0, Z1):
            for x in range(max(xw + 1, X0), min(xe - 1, X1) + 1):
                b.set(x, top + k, gz, B(f"{P}_planks"))
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            for y in range(4, 7):
                if b.get(x, y, z) is None:
                    b.set(x, y, z, AIR)
    # door facing the tower, windows with shutters
    b.door(X0, 1, 5, P, "west")
    for (x, z, f) in ((13, Z0, "north"), (13, Z1, "south"), (X1, 5, "east")):
        b.set(x, 2, z, B("glass_pane"))
        ox, oz = {"north": (0, -1), "south": (0, 1), "east": (1, 0)}[f]
        for s in (-1, 1):
            px, pz = (x + s, z + oz) if f in ("north", "south") else (x + ox, z + s)
            b.set(px, 2, pz, trapdoor(P, f, "bottom", True))
    # cottage interior
    b.bed(12, 1, 6, "east", "light_blue")      # foot at (12,6), head at (13,6)
    b.chest(14, 1, 6, "north", LOOT)
    b.set(14, 1, 4, B("cartography_table"))
    b.set(13, 1, 4, B("barrel", facing="up"))
    b.set(12, 1, 4, B("smoker", facing="south"))
    b.set(13, 3, 5, B("lantern", hanging=True))
    for x in range(X0, X1 + 1):
        b.set(x, 4, 5, B("expanse:stripped_palm_log", axis="x"))
    b.set(14, 2, 4, B("expanse:potted_palm_sapling"))
    b.set(12, 1, 5, B("blue_carpet"))
    b.set(13, 1, 5, B("light_blue_carpet"))
    # porch bits, path to the tower
    b.set(X0 - 1, 1, 3, B("barrel", facing="up"))
    b.set(X0 - 1, 1, 7, B(f"{P}_fence"))
    b.set(X0 - 1, 2, 7, B("lantern"))
    for x in (9, 10):
        b.set(x, 0, 5, B("gravel" if x == 9 else "dirt_path"))
        b.set(x, 0, 4 if x == 10 else 6, B("gravel"))
    b.air(9, 1, 4, 10, 3, 6, only_void=True)
    # crab trap / fishing gear beside the cottage
    b.set(16, 1, 4, B("barrel", facing="east"))
    b.set(16, 0, 4, B("sand"))
    return [b]
