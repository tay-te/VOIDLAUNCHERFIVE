"""Fishing shacks of the willow bayou. Placed on the bayou floor (OCEAN_FLOOR_WG):
everything under the decks is structure void, so the shallows stay wet and the
mud stays muddy around the stilts.

start pool (3 variants)
  shack     : a willow shack on stilts with a porch, crab pots and drying fish, a
              ladder down to a low landing; a rowboat lies sunk beside the stilts with
              the fisherman's strongbox still in its bow (hidden chest)
  smokehouse: a low jetty with a smokehouse (fire, chimney, fish on racks), mooring
              posts and a barrel of the catch; a stash under the jetty's end boards
  sunk      : the same kind of shack, one corner of stilts gone - floor tilting into
              the water, roof sagging, moss over everything; the box slid into the
              low corner
"""
from blocks import AIR, B
from builder import Build, item, prune_unsupported, slab, stairs, trapdoor
from kit import roof_gable
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import book, ground_item

NAME = "bayou_shack"
STRUCTURE = dict(biomes=["willow_bayou"], size=1, max_distance=32, set="biome_sights", heightmap="OCEAN_FLOOR_WG")
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
W = "expanse:willow"
SW = "expanse:stripped_willow"
MOSS = "expanse:spanish_moss"


def wlog(axis="y"):
    return B(f"{W}_log", axis=axis)


def stilt(b, x, z, top):
    b.set(x, 0, z, B("mud"))
    for y in range(1, top + 1):
        b.set(x, y, z, wlog())


def moss_eaves(b, rng, cells, y, p=0.5, longest=3):
    for (x, z) in cells:
        if rng.random() < p and b.get(x, y, z) is None:
            b.hang_moss(x, y, z, rng.randint(1, longest), block=MOSS)


def sunken_boat(b, x, z, rng, along="x"):
    """A rowboat 2 x 4 lying on the bottom (y=1), its gunwales stairs, bow chest under a thwart."""
    cells = []
    for i in range(4):
        for j in range(2):
            px, pz = (x + i, z + j) if along == "x" else (x + j, z + i)
            cells.append((px, pz, i, j))
            b.set(px, 0, pz, B("mud"))
            end = i in (0, 3)
            if end:
                f = ("west" if i == 0 else "east") if along == "x" else ("north" if i == 0 else "south")
                b.set(px, 1, pz, stairs("spruce", f))
            else:
                f = ("north" if j == 0 else "south") if along == "x" else ("west" if j == 0 else "east")
                b.set(px, 1, pz, stairs("spruce", f) if rng.random() < 0.8 else slab("spruce"))
    return cells


def shack():
    b = Build(f"{NAME}_shack", 14, 12, 14, seed=1907451)
    rng = b.rng
    D = 4
    x0, z0, x1, z1 = 2, 2, 7, 7
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (x0, 4), (x1, 5), (10, z0 + 1), (10, z1 - 1)):
        stilt(b, x, z, D - 1)
    for x in range(x0, 11):                           # floor and porch
        for z in range(z0, z1 + 1):
            if x <= x1 or z0 + 1 <= z <= z1 - 1:
                b.set(x, D, z, wlog("z") if x in (x0, x1) or x == 10 else B(f"{W}_planks"))
    for x in range(x0, x1 + 1):                       # walls
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                corner = x in (x0, x1) and z in (z0, z1)
                for y in range(D + 1, D + 4):
                    b.set(x, y, z, B(f"{SW}_log", axis="y") if corner else B(f"{W}_planks"))
    b.air(x0 + 1, D + 1, z0 + 1, x1 - 1, D + 3, z1 - 1)
    roof_gable(b, x0, x1, z0, z1, D + 4, "spruce", B(f"{W}_planks"), along="x", overhang=1, gable_overhang=1,
               brackets=False)
    b.door(x1, D + 1, 5, W, "east", hinge="right")
    for (x, z, out) in ((4, z0, "north"), (5, z1, "south"), (x0, 5, "west")):
        b.set(x, D + 2, z, B("glass_pane"))
        sx, sz = (1, 0) if out in ("north", "south") else (0, 1)
        dx, dz = {"north": (0, -1), "south": (0, 1), "west": (-1, 0)}[out]
        b.set(x + sx + dx, D + 2, z + sz + dz, trapdoor(W, out, "bottom", True))
    # porch railing, crab pots and drying fish
    for x in range(x1 + 1, 11):
        for z in (z0 + 1, z1 - 1):
            b.set(x, D + 1, z, B(f"{W}_fence"))
    b.set(10, D + 1, 4, B(f"{W}_fence"))
    for (x, z) in ((9, 4), (9, 3)):
        b.set(x, D + 1, z, B("composter", level=0))
    b.set(10, D + 2, z0 + 1, B(f"{W}_fence"))
    b.set(10, D + 3, z0 + 1, B("lantern"))
    for x in (8, 9):                                  # fish hung on the outside of the rail
        b.item_frame(x, D + 1, z1, "south", item(rng.choice(["cod", "salmon"])), rotation=rng.randrange(8))
    # ladder down the porch end to a low landing
    stilt(b, 10, 4, D - 1)
    for y in range(2, D):
        b.set(11, y, 4, B("ladder", facing="east"))
    b.set(10, D, 4, wlog("z"))
    for x in (11, 12):
        for z in (3, 4, 5):
            b.set(x, 1, z, B(f"{W}_planks") if (x + z) % 3 else slab(W, "top"))
    stilt(b, 12, 3, 0)
    stilt(b, 12, 5, 0)
    b.set(12, 1, 3, wlog())
    b.set(12, 1, 5, wlog())
    b.set(12, 2, 5, B(f"{W}_fence"))
    # inside: bed, stove with pipe, nets, barrel of the catch, the cat's cushion, lantern
    b.bed(3, D + 1, 3, "south", "light_gray")
    b.set(6, D + 1, 3, B("smoker", facing="south"))
    for y in range(D + 2, D + 6):                      # stovepipe up through the roof
        b.set(6, y, 3, B("iron_chain", axis="y"))
    b.set(6, D + 7, 3, B("lightning_rod", facing="up"))
    b.barrel(6, D + 1, 6, "up", LOOT)
    b.set(3, D + 1, 6, B("cobweb"))
    b.set(3, D + 2, 6, B("cobweb"))
    b.cushion(5, D + 1, 6, color="orange")
    b.shelf(4, D + 2, 6, "spruce", "north", [item("glass_bottle"), item("string"), item("slime_ball")])
    for x in range(x0 + 1, x1):
        b.set(x, D + 4, 4, B(f"{SW}_log", axis="x"))
    b.set(4, D + 3, 4, B("lantern", hanging=True))
    ground_item(b, 5, D + 1, 3, book(BOOKS[NAME]), rotation=0)
    # spanish moss off the eaves, the sunk rowboat by the stilts with the strongbox in its bow
    moss_eaves(b, rng, [(x, z) for x in range(x0 - 2, x1 + 3) for z in (z0 - 1, z1 + 1)], D + 3, 0.45)
    sunken_boat(b, 2, 10, rng)
    b.chest(2, 1, 10, "east", HIDDEN)
    b.set(3, 1, 10, slab("spruce", "top"))
    for (x, z) in ((0, 12), (6, 11), (12, 9), (1, 1)):
        if b.get(x, 1, z) is None:
            b.set(x, 0, z, B("mud"))
            b.tall_plant(x, 1, z, "expanse:cattail")
    return b


def smokehouse():
    b = Build(f"{NAME}_smokehouse", 14, 10, 11, seed=1907453)
    rng = b.rng
    D = 2
    for x in range(1, 13):                            # the jetty
        for z in (4, 5, 6):
            b.set(x, D, z, B(f"{W}_planks") if (x * 7 + z) % 5 else slab(W, "top"))
        if x % 3 == 1:
            stilt(b, x, 3, D)
            stilt(b, x, 7, D)
            b.set(x, D + 1, 3, B(f"{W}_fence"))
            b.set(x, D + 1, 7, B(f"{W}_fence"))
    # the smokehouse on its own platform at the shore end (west)
    for x in range(1, 5):
        for z in range(0, 4):
            b.set(x, D, z, B(f"{W}_planks"))
    for (x, z) in ((1, 0), (4, 0), (1, 3), (4, 3)):
        stilt(b, x, z, D - 1)
    for x in range(1, 5):
        for z in range(0, 4):
            if x in (1, 4) or z in (0, 3):
                for y in range(D + 1, D + 4):
                    b.set(x, y, z, B("mud_bricks") if y == D + 1 else B(f"{W}_planks"))
    b.air(2, D + 1, 1, 3, D + 3, 2)
    b.door(3, D + 1, 3, W, "south")
    for x in range(0, 6):
        for z in range(-1, 5):
            if b.inb(x, D + 4, z):
                b.set(x, D + 4, z, slab("spruce"))
    b.campfire(2, D + 1, 1, lit=True)
    for y in range(D + 4, D + 7):
        b.set(1, y, 1, B("mud_bricks"))
    b.campfire(1, D + 7, 1, lit=True)
    b.set(3, D + 3, 1, B(f"{W}_fence"))
    b.item_frame(3, D + 2, 1, "west", item("salmon"))
    # drying racks of fish on the jetty, barrels of the catch, mooring posts
    for x in (6, 8):
        b.set(x, D + 1, 5, B(f"{W}_fence"))
        b.set(x, D + 2, 5, B(f"{W}_fence"))
    b.set(7, D + 2, 5, B(f"{W}_fence"))
    for x in (6, 7, 8):
        b.item_frame(x, D + 2, 6, "south", item(rng.choice(["cod", "salmon", "cod"])), rotation=rng.randrange(8))
    b.barrel(10, D + 1, 4, "up", LOOT)
    b.set(10, D + 1, 6, B("barrel", facing="up"))
    b.set(11, D + 1, 5, B("composter", level=0))
    b.set(12, D + 1, 4, B(f"{W}_fence"))
    b.set(12, D + 2, 4, B("lantern"))
    ground_item(b, 9, D + 1, 5, book(BOOKS[f"{NAME}_smokehouse"]), rotation=3)
    # stash under the end boards (a trapdoor in the jetty)
    b.set(12, D, 6, trapdoor(W, "south", "top", False))
    b.chest(12, D - 1, 6, "north", HIDDEN)
    stilt(b, 12, 6, 0)
    moss_eaves(b, rng, [(x, z) for x in range(0, 6) for z in (-1, 4) if 0 <= z], D + 3, 0.5)
    for (x, z) in ((0, 8), (5, 8), (13, 2), (9, 9)):
        b.set(x, 0, z, B("mud"))
        b.tall_plant(x, 1, z, "expanse:cattail")
    return b


def sunk():
    b = Build(f"{NAME}_sunk", 12, 11, 12, seed=1907455)
    rng = b.rng
    D = 4
    x0, z0, x1, z1 = 2, 2, 7, 7
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1)):
        stilt(b, x, z, D - 1)
    b.set(x1, 0, z1, B("mud"))
    b.set(x1, 1, z1, wlog("x"))                        # the broken stilt lies in the water
    b.set(x1 + 1, 1, z1, wlog("x"))
    # the floor tilts down toward the lost corner (south-east)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            drop = max(0, (x - 4) + (z - 4))
            y = D - min(3, drop // 2)
            b.set(x, y, z, slab(W, "top") if drop % 2 else B(f"{W}_planks"))
    for x in range(x0, x1 + 1):                       # walls, broken where the floor fell
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            drop = max(0, (x - 4) + (z - 4))
            base = D - min(3, drop // 2)
            top = base + 3 - (2 if drop > 3 else 0)
            for y in range(base + 1, top + 1):
                b.set(x, y, z, B(f"{W}_planks") if (x + y + z) % 4 else B(f"{SW}_log", axis="y"))
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            drop = max(0, (x - 4) + (z - 4))
            base = D - min(3, drop // 2)
            b.air(x, base + 1, z, x, base + 3, z)
    for x in range(x0 - 1, x1 + 2):                   # the roof, sagging
        for z in range(z0 - 1, z1 + 2):
            drop = max(0, (x - 4) + (z - 4))
            y = D + 4 - min(3, drop // 2)
            if rng.random() < 0.85 and b.inb(x, y, z):
                b.set(x, y, z, slab("spruce") if (x + z) % 3 else stairs("spruce", "south"))
    b.barrel(3, D + 1, 3, "up", LOOT)
    b.chest(6, 2, 6, "north", HIDDEN)                  # slid into the low corner
    b.set(6, 3, 6, AIR)
    b.set(4, D + 1, 3, B("cobweb"))
    ground_item(b, 3, D + 1, 4, book(BOOKS[f"{NAME}_sunk"]), rotation=1)
    for (x, y, z), (st, _) in list(b.cells.items()):  # moss over everything
        if st.short.endswith(("_slab", "_stairs", "_planks")) and rng.random() < 0.25 and \
                b.inb(x, y - 1, z) and b.get(x, y - 1, z) is None:
            b.hang_moss(x, y - 1, z, rng.randint(1, 3), block=MOSS)
    for (x, z) in ((1, 10), (9, 2), (10, 9)):
        b.set(x, 0, z, B("mud"))
        b.tall_plant(x, 1, z, "expanse:cattail")
    prune_unsupported(b)                              # roof slabs that slid off with the corner
    return b


def pools():
    return {"start": [Piece(shack(), 3, "small_bayou"), Piece(smokehouse(), 2, "small_bayou"),
                      Piece(sunk(), 2, "small_bayou")]}


DATA = data_for(NAME, ["small_bayou"])
