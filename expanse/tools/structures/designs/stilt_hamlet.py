"""Stilt hamlet of the willow bayou: three willow huts on stilts (a dwelling, a
fishing hut and a storehouse) linked by railed plank walkways ~6 blocks over
the bayou floor, with lanterns, barrels, Spanish moss and a low fishing
landing. Everything below the decks is structure void so water stays put."""
from blocks import AIR, B
from builder import Build
from common import gable_roof

NAME = "stilt_hamlet"
LOOT = "expanse:chests/stilt_hamlet"
W = "expanse:willow"
SW = "expanse:stripped_willow"
D = 6            # deck level (stilts are y0..5)
ROOF = "spruce"


def stilt(b, x, z, top=D - 1, fence=False):
    for y in range(0, top + 1):
        b.set(x, y, z, B(f"{W}_fence") if fence else B(f"{W}_log", axis="y"))


def build():
    b = Build(NAME, 19, 15, 19, seed=1847391)
    rng = b.rng
    huts = []          # (x0, z0, x1, z1)
    openings = set()   # deck edge cells that must stay free of railings
    rail_cells = set()

    def platform(x0, z0, x1, z1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                ex, ez = x in (x0, x1), z in (z0, z1)
                if ex and ez:
                    b.set(x, D, z, B(f"{W}_log", axis="y"))
                    stilt(b, x, z)
                elif ez:
                    b.set(x, D, z, B(f"{SW}_log", axis="x"))
                elif ex:
                    b.set(x, D, z, B(f"{SW}_log", axis="z"))
                else:
                    b.set(x, D, z, B(f"{W}_planks"))
                if ex or ez:
                    rail_cells.add((x, z))
        for x in range(x0 + 2, x1 - 1, 3):
            stilt(b, x, z0, fence=True)
            stilt(b, x, z1, fence=True)
        for z in range(z0 + 2, z1 - 1, 3):
            stilt(b, x0, z, fence=True)
            stilt(b, x1, z, fence=True)
        b.air(x0, D + 1, z0, x1, D + 3, z1, only_void=True)

    def walk(x0, z0, x1, z1, axis):
        """Deck of the inner cells plus edge beams carrying the railings."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                b.set(x, D, z, B(f"{W}_planks") if (x * 3 + z) % 4 else B(f"{W}_slab", type="top"))
        if axis == "x":
            beams = [(x, z0 - 1) for x in range(x0, x1 + 1)] + [(x, z1 + 1) for x in range(x0, x1 + 1)]
            openings.update({(x0 - 1, z) for z in range(z0, z1 + 1)} | {(x1 + 1, z) for z in range(z0, z1 + 1)})
        else:
            beams = [(x0 - 1, z) for z in range(z0, z1 + 1)] + [(x1 + 1, z) for z in range(z0, z1 + 1)]
            openings.update({(x, z0 - 1) for x in range(x0, x1 + 1)} | {(x, z1 + 1) for x in range(x0, x1 + 1)})
        for (x, z) in beams:
            if b.get(x, D, z) is None:
                b.set(x, D, z, B(f"{SW}_log", axis=axis))
                rail_cells.add((x, z))
                k = (x - x0) if axis == "x" else (z - z0)
                if k % 3 == 1:
                    stilt(b, x, z, fence=True)
        b.air(x0, D + 1, z0, x1, D + 3, z1)

    def hut(x0, z0, x1, z1, door, roof_x=True):
        huts.append((x0, z0, x1, z1))
        b.air(x0 + 1, D + 1, z0 + 1, x1 - 1, D + 7, z1 - 1)
        for y in range(D + 1, D + 5):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        corner = x in (x0, x1) and z in (z0, z1)
                        b.set(x, y, z, B(f"{SW}_log", axis="y") if corner else B(f"{W}_planks"))
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                b.set(x, D + 4, z, B(f"{SW}_log", axis="x"))
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                b.set(x, D + 4, z, B(f"{SW}_log", axis="z"))
        gable_roof(b, x0, x1, z0, z1, D + 4, ROOF, B(f"{W}_planks"), overhang=1, gable_x=roof_x,
                   ridge=B(f"{ROOF}_slab", type="bottom"))
        dx, dz, f = door
        b.door(dx, D + 1, dz, W, f)
        ox, oz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[f]
        openings.add((dx + ox, dz + oz))

    # ---- platforms: A dwelling (NW), B fishing hut (NE), C storehouse (S), junction landing
    platform(0, 0, 6, 7)
    platform(11, 0, 17, 7)
    platform(3, 10, 9, 17)
    platform(12, 12, 15, 15)
    walk(7, 3, 10, 4, "x")           # A <-> B
    walk(3, 8, 4, 9, "z")            # A <-> C
    walk(13, 8, 14, 11, "z")         # B <-> junction
    walk(10, 13, 11, 14, "x")        # junction <-> C

    hut(1, 1, 5, 5, (3, 5, "south"))
    hut(12, 1, 16, 5, (14, 5, "south"), roof_x=False)
    hut(4, 12, 8, 16, (6, 12, "north"))

    # ---- railings: deck edges, except ledges beside hut walls, walkway mouths and door fronts
    def beside_hut(x, z):
        return any(x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1 for (x0, z0, x1, z1) in huts)

    for (x, z) in sorted(rail_cells):
        if (x, z) in openings or beside_hut(x, z) or b.get(x, D + 1, z) not in (None, AIR):
            continue
        b.set(x, D + 1, z, B(f"{W}_fence"))

    # windows
    for (x, z) in ((1, 3), (5, 3), (3, 1), (16, 3), (14, 1), (12, 3), (4, 14), (8, 14), (6, 16)):
        b.set(x, D + 2, z, B("glass_pane"))
        b.set(x, D + 3, z, B("glass_pane") if (x + z) % 2 else B(f"{W}_planks"))

    # lanterns on railing posts
    for (x, z) in ((0, 7), (6, 7), (11, 7), (17, 7), (3, 17), (9, 17), (15, 15), (12, 15), (7, 2), (10, 5),
                   (2, 9), (15, 9), (11, 12)):
        st = b.get(x, D + 1, z)
        if st is not None and st.short == "willow_fence":
            b.set(x, D + 2, z, B("lantern"))

    y = D + 1
    # ---- hut A: dwelling
    b.bed(2, y, 3, "north", "green")
    b.chest(4, y, 2, "south", LOOT)
    b.set(2, y, 4, B("barrel", facing="up"))
    b.set(2, y + 1, 4, B("expanse:potted_willow_sapling"))
    b.set(4, y, 4, B("crafting_table"))
    b.set(3, y, 3, B("green_carpet"))
    b.set(3, y, 2, B("green_carpet"))
    b.fill(2, D + 4, 3, 4, D + 4, 3, B(f"{SW}_log", axis="x"))
    b.set(3, D + 3, 3, B("lantern", hanging=True))
    b.set(1, y, 6, B("barrel", facing="up"))
    b.set(5, y, 6, B("composter", level=0))

    # ---- hut B: fishing hut
    b.set(15, y, 2, B("barrel", facing="up"))
    b.set(15, y + 1, 2, B("barrel", facing="up"))
    b.set(15, y, 3, B("barrel", facing="west"))
    b.set(13, y, 2, B("smoker", facing="south"))
    b.set(13, y, 4, B("water_cauldron", level=2))
    b.set(15, y, 4, B("barrel", facing="up"))
    b.fill(13, D + 4, 3, 15, D + 4, 3, B(f"{SW}_log", axis="x"))
    b.set(14, D + 3, 3, B("lantern", hanging=True))
    for x in (12, 16):            # drying rack on the porch
        b.set(x, y, 6, B(f"{W}_fence"))
        b.set(x, y + 1, 6, B(f"{W}_fence"))

    # ---- hut C: storehouse
    b.set(5, y, 15, B("barrel", facing="up"))
    b.set(5, y + 1, 15, B("barrel", facing="up"))
    b.set(5, y, 14, B("barrel", facing="east"))
    b.set(7, y, 15, B("hay_block", axis="y"))
    b.set(7, y + 1, 15, B("hay_block", axis="x"))
    b.set(7, y, 14, B("composter", level=0))
    b.set(5, y, 13, B("fletching_table"))
    b.fill(5, D + 4, 14, 7, D + 4, 14, B(f"{SW}_log", axis="x"))
    b.set(6, D + 3, 14, B("lantern", hanging=True))
    b.set(8, y, 11, B("barrel", facing="up"))

    # ---- fishing landing below hut B's porch, reached by a ladder
    for x in (16, 17, 18):
        for z in (8, 9, 10):
            b.set(x, 3, z, B(f"{W}_planks"))
    stilt(b, 18, 10, top=2)
    stilt(b, 16, 10, top=2)
    stilt(b, 18, 8, top=2)
    for (x, z) in ((18, 8), (18, 9), (18, 10), (17, 10), (16, 10)):
        b.set(x, 4, z, B(f"{W}_fence"))
    b.set(18, 5, 10, B("lantern"))
    b.set(16, 4, 9, B("barrel", facing="up"))
    for yy in range(4, D + 1):
        b.set(17, yy, 8, B("ladder", facing="south"))
    b.set(17, D + 1, 7, AIR)
    b.set(17, D + 2, 7, AIR)
    b.air(16, 4, 8, 18, 5, 10, only_void=True)

    # ---- spanish moss under decks and from eaves
    for (x, z) in ((1, 2), (5, 5), (2, 7), (12, 2), (16, 6), (15, 0), (4, 13), (8, 16), (6, 10), (8, 2), (9, 5),
                   (3, 8), (14, 9), (13, 14), (10, 12), (0, 4), (17, 3), (9, 15), (4, 17)):
        if b.get(x, D, z) is not None and b.get(x, D - 1, z) is None:
            b.hang_moss(x, D - 1, z, rng.randint(1, 3))
    for (x, z) in ((0, 1), (0, 4), (6, 2), (11, 3), (17, 4), (3, 11), (9, 12), (10, 15), (2, 6), (13, 6)):
        for yy in range(14, D + 1, -1):
            st = b.get(x, yy, z)
            if st is not None and st.short != "air":
                if yy - 1 > D + 1 and b.get(x, yy - 1, z) in (None, AIR):
                    b.hang_moss(x, yy - 1, z, rng.randint(1, 2))
                break
    # moss creeping over ridges and decks
    for pos, (st, n) in list(b.cells.items()):
        x, yy, z = pos
        if b.get(x, yy + 1, z) not in (None, AIR):
            continue
        if (st.short == f"{ROOF}_slab" and rng.random() < 0.4) or (
                yy == D and st.short == "willow_planks" and rng.random() < 0.08):
            b.set(x, yy + 1, z, B("moss_carpet"))
    return [b]
