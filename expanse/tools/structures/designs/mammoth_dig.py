"""The mammoth dig of the frostbloom tundra.

start pool (3 variants)
  tusks : a mammoth skull with great curling tusks coming out of a gridded dig,
          spoil heaps, a sieve, the finds table and the diggers' tent; brushing the
          gravel turns things up (archaeology), and the best finds were buried in
          the spoil heap when the diggers left (hidden chest)
  ribs  : a ribcage arching out of the snow along a half-buried spine, red marker
          flags on the finds, a leg bone, a collapsed tent; a strongbox under a drift
  pond  : the diggers' ice-fishing hut on a frozen pond - hole in the floor, stool,
          stove and a barrel of fish; out on the ice a chest frozen solid into it
"""
import math

from blocks import AIR, B
from builder import Build, item, slab, stairs
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import a_tent, book, clear, flora, ground_item, meal_fire, pick, ring, trail

NAME = "mammoth_dig"
STRUCTURE = dict(biomes=["frostbloom_tundra"], size=1, max_distance=32, set="biome_sights")
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
GROUND = ["snow_block", "snow_block", "dirt", "coarse_dirt"]
SPOIL = [("gravel", 3), ("coarse_dirt", 3), ("dirt", 2), ("snow_block", 2)]
PATH = [("snow_block", 3), ("coarse_dirt", 2), ("gravel", 2), ("packed_ice", 1)]


def bone(axis="y"):
    return B("bone_block", axis=axis)


def snow(b, x, y, z, layers):
    if b.inb(x, y, z) and b.is_air_or_void(x, y, z):
        b.set(x, y, z, B("snow", layers=max(1, min(7, layers))))


def drift(b, rng, cells, y=1, lo=1, hi=3):
    for (x, z) in cells:
        below = b.get(x, y - 1, z)
        if below is None or below.short not in ("air", "snow"):
            snow(b, x, y, z, rng.randint(lo, hi))


def spoil_heap(b, rng, cx, cz, r, h):
    """A mound of dug-out gravel and earth, snow on its top."""
    for x in range(int(cx - r), int(cx + r) + 1):
        for z in range(int(cz - r), int(cz + r) + 1):
            d = math.hypot(x - cx, z - cz)
            if d > r or not b.inb(x, 1, z):
                continue
            top = int(round(h * (1 - d / (r + 0.5)) + rng.random() * 0.6))
            for y in range(0, top + 1):
                b.set(x, y, z, pick(rng, SPOIL) if y < top else B("snow_block") if rng.random() < 0.5
                      else pick(rng, SPOIL))
            if rng.random() < 0.5:
                snow(b, x, top + 1, z, rng.randint(1, 2))


def tusk(b, x, z, side, rng):
    """A tusk sweeping forward (+z), out to `side` (-1/+1), up and curling back in (face-connected)."""
    s = side
    path = [(0, 1, 0), (0, 1, 1), (s, 1, 1), (s, 1, 2), (s, 2, 2), (s, 2, 3), (2 * s, 2, 3), (2 * s, 2, 4),
            (2 * s, 3, 4), (2 * s, 3, 5), (2 * s, 4, 5), (s, 4, 5), (s, 4, 6), (s, 5, 6)]
    for i, (dx, y, dz) in enumerate(path):
        nxt = path[min(i + 1, len(path) - 1)]
        ax = "x" if nxt[0] != dx else "y" if nxt[1] != y else "z"
        b.set(x + dx, y, z + dz, bone(ax))


def skull(b, x0, z0, rng):
    """Cranium 5 wide, 4 deep (front toward +z), half sunk; eye sockets, tusk roots, trunk boss."""
    for dx in range(5):
        for dz in range(4):
            edge = dx in (0, 4)
            h = 2 if edge else 3
            if dz == 0:
                h -= 1
            for y in range(0, h + 1):
                b.set(x0 + dx, y, z0 + dz, bone("y"))
    for dx in (1, 2, 3):                               # domed crown
        b.set(x0 + dx, 4, z0 + 1, bone("x"))
        b.set(x0 + dx, 4, z0 + 2, bone("x"))
    b.set(x0 + 2, 5, z0 + 1, bone("x"))
    b.set(x0 + 1, 2, z0 + 3, AIR)                      # eye sockets
    b.set(x0 + 3, 2, z0 + 3, AIR)
    b.set(x0 + 2, 1, z0 + 4, bone("z"))                # trunk boss
    b.set(x0 + 2, 2, z0 + 4, bone("y"))
    tusk(b, x0 + 1, z0 + 4, -1, rng)
    tusk(b, x0 + 3, z0 + 4, 1, rng)


def tusks():
    b = Build(f"{NAME}_tusks", 17, 9, 17, seed=1907411)
    rng = b.rng
    # the dig: a gridded floor of gravel and frozen earth, stakes at the grid corners
    for x in range(3, 14):
        for z in range(3, 15):
            b.set(x, 0, z, pick(rng, [("gravel", 5), ("coarse_dirt", 3), ("dirt", 1), ("packed_ice", 1)]))
            b.set(x, 1, z, AIR)
            b.set(x, 2, z, AIR)
    for x in range(3, 14, 3):
        for z in (3, 14):
            b.set(x, 1, z, B("spruce_fence"))
    for z in range(6, 14, 3):
        for x in (3, 13):
            b.set(x, 1, z, B("spruce_fence"))
    skull(b, 6, 3, rng)
    # spoil heaps on the dig's edges (the finds were buried in the big one)
    spoil_heap(b, rng, 14.5, 2.5, 2.6, 3)
    spoil_heap(b, rng, 1.5, 9, 2.2, 2)
    b.chest(15, 0, 2, "west", HIDDEN)
    # sieve (scaffolding frame), finds table with brushes and a tray of bits, crates
    b.set(11, 1, 12, B("scaffolding", distance=0, bottom=False))
    b.set(11, 0, 12, B("gravel"))
    for x in (8, 9):
        b.set(x, 1, 13, slab("spruce", "top"))
    ground_item(b, 8, 2, 13, item("brush"), rotation=1)
    ground_item(b, 9, 2, 13, item("bone"), rotation=3)
    b.set(10, 1, 13, B("spruce_fence"))
    b.set(10, 2, 13, B("lantern"))
    b.barrel(12, 1, 13, "up", LOOT)
    b.set(12, 1, 14, B("barrel", facing="north"))
    # the diggers' tent and fire to the south-west
    a_tent(b, 0, 12, 4, "z", "white", wide=True)
    b.bed(2, 1, 14, "north", "straw")
    b.set(2, 2, 13, B("lantern", hanging=True))       # hung from the ridge
    b.set(3, 1, 13, B("barrel", facing="up"))
    meal_fire(b, 6, 1, 15, ["cod", "expanse:venison"], lit=False, done=(400, 250, 0, 0))
    b.set(5, 1, 16, B("spruce_log", axis="x"))
    b.set(7, 1, 16, B("spruce_log", axis="x"))
    ground_item(b, 4, 1, 11, book(BOOKS[NAME]), rotation=0)
    trail(b, [(8, 15), (9, 16)], rng, PATH, width=1, fade=0.5)
    drift(b, rng, [(x, z) for x in range(17) for z in range(17) if (x < 3 or x > 13 or z < 3) and rng.random() < 0.3])
    flora(b, ring(8, 8, 8.2, 12, (17, 17)), rng, 0.3, GROUND)
    return b


def ribs():
    b = Build(f"{NAME}_ribs", 17, 8, 13, seed=1907413)
    rng = b.rng
    sz = 6
    for x in range(2, 15):                             # the spine, half buried
        y = 2 if 4 <= x <= 11 else 1
        b.set(x, y, sz, bone("x"))
        b.set(x, 0, sz, B("gravel"))
        if y == 2:
            b.set(x, 1, sz, B("packed_ice") if x % 3 == 0 else B("gravel"))
    for i, x in enumerate(range(4, 13, 2)):            # ribs arching up and out
        for side in (-1, 1):
            if rng.random() < 0.15:
                continue
            broken = rng.random() < 0.3
            arc = [(1, 3), (2, 3), (3, 2), (3, 1)]
            for k, (dz, y) in enumerate(arc):
                if broken and k >= 2:
                    break
                b.set(x, y, sz + side * dz, bone("z" if y == 3 else "y"))
            if broken:                                  # its lower half lies in the snow beside it
                b.set(x + 1, 1, sz + side * 4, bone("x"))
    for x in range(3, 14):                             # gravel floor of the dig between the ribs
        for z in range(2, 11):
            if b.get(x, 0, z) is None and rng.random() < 0.6:
                b.set(x, 0, z, pick(rng, [("gravel", 4), ("coarse_dirt", 2), ("packed_ice", 1)]))
    clear(b, 3, 1, 3, 13, 2, 9)
    # the hip at the east end and a leg bone lying out in the snow
    for dz in (-1, 0, 1):
        b.set(14, 1, sz + dz, bone("y"))
        b.set(14, 2, sz + dz, bone("z"))
    b.set(15, 1, sz, bone("x"))
    for z in range(9, 13):
        b.set(13, 1, z, bone("z"))
    b.set(13, 1, 12, bone("y"))
    # red flags marking the finds, a collapsed tent and its cold fire
    for (x, z) in ((5, 2), (9, 10), (11, 3)):
        b.set(x, 1, z, B("spruce_fence"))
        b.banner(x, 2, z, "red", [], rotation=rng.randrange(16))
    for i in range(3):                                # tent, one side fallen flat
        b.set(1, 1, 8 + i, stairs("white_wool", "east"))
        b.set(2, 2, 8 + i, slab("white_wool", "bottom") if i < 2 else B("white_wool"))
        b.set(3, 1, 8 + i, slab("white_wool", "bottom"))
        b.set(2, 1, 8 + i, AIR)
    b.bed(2, 1, 9, "south", "straw")
    b.campfire(4, 1, 11, lit=False)
    ground_item(b, 5, 1, 11, book(BOOKS[f"{NAME}_ribs"]), rotation=2)
    b.barrel(1, 1, 11, "up", LOOT)
    # strongbox under the drift against the ribs (windward, north)
    b.chest(8, 0, 2, "south", HIDDEN)
    drift(b, rng, [(x, z) for x in range(2, 15) for z in (1, 2, 3)], lo=2, hi=5)
    drift(b, rng, [(x, z) for x in range(17) for z in range(13) if rng.random() < 0.25])
    flora(b, ring(8, 6, 7, 10, (17, 13)), rng, 0.25, GROUND)
    return b


def pond():
    b = Build(f"{NAME}_pond", 13, 7, 13, seed=1907415)
    rng = b.rng
    cx, cz = 6, 6
    for (x, z) in ring(cx, cz, 0, 5.2, (13, 13)):     # the frozen pond
        b.set(x, 0, z, pick(rng, [("ice", 3), ("packed_ice", 2)]))
        b.set(x, 1, z, AIR)
        if rng.random() < 0.25:
            snow(b, x, 1, z, 1)
    for (x, z) in ring(cx, cz, 5.2, 6.3, (13, 13)):
        if rng.random() < 0.7:
            b.set(x, 0, z, pick(rng, [("snow_block", 3), ("gravel", 1), ("dirt", 1)]))
    # the hut (3 x 4, door east), plank floor around the fishing hole
    hx0, hz0, hx1, hz1 = 2, 3, 5, 7
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            wall = x in (hx0, hx1) or z in (hz0, hz1)
            b.set(x, 0, z, B("spruce_planks"))
            for y in (1, 2):
                b.set(x, y, z, B("spruce_planks") if wall and (x + z + y) % 5 else
                      B("stripped_spruce_log", axis="y") if wall else AIR)
    for x in range(hx0 - 1, hx1 + 2):                  # lean-to roof
        for z in range(hz0, hz1 + 1):
            b.set(x, 3, z, slab("spruce"))
    for z in (hz0, hz1):
        for x in (hx0, hx1):
            b.set(x, 1, z, B("spruce_log", axis="y"))
            b.set(x, 2, z, B("spruce_log", axis="y"))
    b.door(hx1, 1, 5, "spruce", "east", hinge="right")
    b.set(hx0, 2, 5, B("glass_pane"))
    b.set(3, 0, 5, B("water", level=0))                # the hole in the floor
    b.set(3, 1, 4, stairs("spruce", "north"))          # stool
    b.item_frame(4, 2, hz0 + 1, "south", item("fishing_rod"), rotation=1)
    b.barrel(4, 1, 6, "up", LOOT)
    b.set(3, 1, 6, B("smoker", facing="east"))
    b.set(3, 4, 6, B("lightning_rod", facing="up"))    # stovepipe
    b.set(3, 3, 6, B("spruce_planks"))
    b.set(4, 1, 4, B("lantern"))
    # out on the ice: a tip-up over a second hole, fish frozen where they were thrown, a sled
    b.set(8, 0, 4, B("water", level=0))
    b.set(9, 1, 4, B("spruce_fence"))
    b.set(9, 2, 4, B("spruce_trapdoor", facing="west", half="top", open=True))
    for (x, z, f) in ((8, 6, "cod"), (9, 7, "salmon"), (7, 5, "cod")):
        if rng.random() < 0.8:
            ground_item(b, x, 1, z, item(f), rotation=rng.randrange(8))
    for z in (9, 10, 11):
        b.set(8, 1, z, slab("spruce", "bottom"))
    b.set(8, 1, 12, B("spruce_fence"))                 # the sled's handle
    # the chest frozen into the pond: you can see it under the ice
    b.chest(9, 0, 8, "north", HIDDEN)
    b.set(9, 1, 8, B("ice"))
    ground_item(b, 6, 1, 8, book(BOOKS[f"{NAME}_pond"]), rotation=1)
    trail(b, [(hx1 + 1, 5), (10, 6), (12, 8)], rng, PATH, width=1, fade=0.4)
    flora(b, ring(cx, cz, 6.3, 9, (13, 13)), rng, 0.35, GROUND)
    return b


def pools():
    return {"start": [Piece(tusks(), 3, "small_dig"), Piece(ribs(), 2, "small_dig"),
                      Piece(pond(), 2, "small_tundra")]}


DATA = data_for(NAME, ["small_dig", "small_tundra"])
