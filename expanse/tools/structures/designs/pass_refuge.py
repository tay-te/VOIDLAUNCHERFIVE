"""Mountain refuges on the way over a range (site=pass: SitedJigsawStructure finds the saddle between two
heights). The peaks and high pastures, the windswept hills, the upland moors and the mountain taigas.

A drystone hut kept up by the valley parishes for whoever is caught on the pass: thick walls, a low roof,
a hearth, plank bunks, a table and a chest of supplies; outside, a wayside shrine for a safe crossing, the
woodpile, a stone trough and a rail to tie the mules to. The track over the pass runs past the door.

start pool (2 variants)
  slab    : walls two stones thick under a low roof of split stone slabs, a stack on the west gable;
            a stone shrine pillar with its niche by the road, the trough fed from a log spout, the
            woodpile along the east wall under a lean-to, the hitching rail
  shingle : the same hut with a steep spruce-shingled roof and a boarded gable, a log stable lean-to
            for the pack animals with hay and a rail, a hollowed-log trough and a roofed wooden cross
  Inside both: hearth in the west wall, two-high plank bunks with blankets, a table and stools, a lantern
  on the tie beam, the supply chest (public) and, under the lower bunk, the warden's box (hidden).
"""
from blocks import AIR, B
from builder import Build, item, slab, stairs, trapdoor
from kit import roof_gable
from pieces import Piece
from regions import mc
from small_data import BOOKS, data_for
from smallkit import book, clear, flora, ground_item, pick, plant, trail

NAME = "pass_refuge"
STRUCTURE = dict(biomes=["verdant_peaks", "dolomite_peaks", "fjordlands", "volcanic_highlands", "pine_heath",
                         "heather_moor", "maple_highlands", "larch_taiga",
                         *mc("meadow", "grove", "snowy_slopes", "stony_peaks", "frozen_peaks", "windswept_hills",
                             "windswept_gravelly_hills", "windswept_forest", "taiga", "snowy_taiga",
                             "old_growth_spruce_taiga", "old_growth_pine_taiga")],
                 size=1, max_distance=32, set="passes", site="pass", max_relief=24)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
PROCS = "small_peaks"
STONE = [("cobblestone", 4), ("stone", 3), ("andesite", 3), ("mossy_cobblestone", 1), ("tuff", 1)]
QUOIN = [("stone_bricks", 2), ("stone", 1), ("andesite", 1)]
FLAGS = [("stone_bricks", 3), ("cobblestone", 2), ("andesite", 2), ("gravel", 1), ("coarse_dirt", 1)]
ROOF_SLABS = [("stone", 3), ("andesite", 2), ("cobblestone", 1)]   # split stone plates, a mixed grey
GROUND = ["grass_block", "grass_block", "coarse_dirt", "stone"]
PATH = [("gravel", 4), ("coarse_dirt", 3), ("stone", 1), ("dirt_path", 2)]

# hut: outer walls x 2..11, z 2..9, two stones thick (interior x 4..9, z 4..7); door in the south wall at x 6
X0, X1, Z0, Z1 = 2, 11, 2, 9
IX0, IX1, IZ0, IZ1 = 4, 9, 4, 7
DOOR_X = 6
WALL_TOP = 4


def _wall_cell(x, z):
    return X0 <= x <= X1 and Z0 <= z <= Z1 and not (IX0 <= x <= IX1 and IZ0 <= z <= IZ1)


def _hut_shell(b, rng, top=WALL_TOP):
    """Footing course, flagged floor, the double drystone walls with dressed corners; door and window."""
    for x in range(X0 - 1, X1 + 2):
        for z in range(Z0 - 1, Z1 + 2):
            if X0 <= x <= X1 and Z0 <= z <= Z1:
                b.set(x, 0, z, pick(rng, FLAGS) if not _wall_cell(x, z) else pick(rng, STONE))
            elif rng.random() < 0.45:                         # the footing stones spread a little
                b.set(x, 0, z, pick(rng, STONE))
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if not _wall_cell(x, z):
                continue
            corner = x in (X0, X1) and z in (Z0, Z1)
            for y in range(1, top + 1):
                b.set(x, y, z, pick(rng, QUOIN if corner else STONE))
    clear(b, IX0, 1, IZ0, IX1, top, IZ1)
    # door: plank door in the outer course, the passage through the inner course, a log lintel
    b.door(DOOR_X, 1, Z1, "spruce", "south", hinge="right")
    b.set(DOOR_X, 1, Z1 - 1, AIR)
    b.set(DOOR_X, 2, Z1 - 1, AIR)
    b.set(DOOR_X, 3, Z1, B("stripped_spruce_log", axis="x"))
    b.set(DOOR_X, 0, Z1 + 1, B("polished_andesite"))         # the doorstep
    # one small deep window in the east wall, barred against the weather, a shutter outside
    b.set(X1 - 1, 2, 6, AIR)
    b.set(X1, 2, 6, B("iron_bars"))
    b.set(X1 + 1, 2, 5, trapdoor("spruce", "east", "bottom", True))


def _hearth(b, rng):
    """Hearth in the west wall: a recess with the fire, stone cheeks and hood; the flue in the wall."""
    hz = 6
    b.set(X0 + 1, 1, hz, AIR)
    b.set(X0 + 1, 2, hz, AIR)
    b.set(X0 + 1, 0, hz, B("stone_bricks"))
    b.campfire(X0 + 1, 1, hz, lit=False, facing="east")
    for z in (hz - 1, hz + 1):
        for y in (1, 2, 3):
            b.set(IX0, y, z, pick(rng, [("cobblestone", 2), ("stone_bricks", 1)]))
    b.set(IX0, 3, hz, stairs("stone_brick", "west", "top"))
    b.set(IX0, 4, hz, B("stone_bricks"))
    b.set(IX0, 4, hz - 1, B("stone_bricks"))
    b.set(IX0, 4, hz + 1, B("stone_bricks"))
    b.set(IX0, 1, hz, AIR)
    b.set(IX0, 2, hz, AIR)
    b.set(IX0, 0, hz, B("stone_bricks"))
    b.set(IX0, 2, hz - 1, None)
    b.set(IX0, 2, hz - 1, B("cobblestone"))
    return hz


def _interior(b, rng):
    """Bunks along the north wall, table and stools, supplies, the lantern on the tie beam."""
    # two-high plank bunks (x 6..9) with blankets; a post at their foot; the warden's box under the lower one
    for x in range(6, IX1 + 1):
        b.set(x, 1, IZ0, slab("spruce", "top"))
        b.set(x, 3, IZ0, slab("spruce", "top"))
        b.set(x, 2, IZ0, B(rng.choice(["brown_carpet", "gray_carpet", "brown_carpet"])))
        b.set(x, 4, IZ0, B(rng.choice(["brown_carpet", "light_gray_carpet"])))
    for y in (1, 2, 3):
        b.set(5, y, IZ0, B("spruce_fence"))
    b.set(5, 4, IZ0, slab("spruce", "top"))
    b.set(IX1, 0, IZ0, None)
    b.chest(IX1, 0, IZ0, "south", HIDDEN)
    # a little firewood stacked by the hearth
    b.set(IX0, 1, IZ0, B("spruce_log", axis="z"))
    b.set(IX0, 2, IZ0, B("spruce_log", axis="z"))
    b.set(IX0, 1, IZ1, B("spruce_log", axis="x"))
    # the table and two stools; a candle stub and the hut book on the table
    b.set(7, 1, 6, B("spruce_fence"))
    b.set(7, 2, 6, B("spruce_pressure_plate"))
    b.set(8, 1, 6, slab("spruce", "top"))
    ground_item(b, 8, 2, 6, book(BOOKS[NAME]), rotation=1)
    b.set(6, 1, 6, stairs("spruce", "west"))
    b.set(8, 1, 7, stairs("spruce", "south"))
    # supplies: the public chest, a barrel of water, a shelf
    b.chest(IX1, 1, IZ1, "west", LOOT)
    b.set(IX1, 1, 6, B("barrel", facing="up"))
    b.set(IX1, 2, 6, B("candle", candles=2, lit=False))
    b.shelf(IX1, 3, 5, "spruce", "west", [item("bread"), item("lantern"), item("lead")])
    # tie beam across the middle with the lantern, a coil of rope on the wall
    for z in range(IZ0, IZ1 + 1):
        b.set(7, WALL_TOP + 1, z, B("stripped_spruce_log", axis="z"))
    b.set(7, WALL_TOP, 5, B("lantern", hanging=True))
    b.item_frame(5, 3, IZ1, "north", item("lead"))


def _slab_roof(b, rng):
    """Low roof of split stone plates: half a block of rise per row, full stones where it bears on a wall."""
    lo, hi = Z0 - 1, Z1 + 1
    n = hi - lo + 1
    for k in range(n):
        z = lo + k
        i = min(k, n - 1 - k)
        y = WALL_TOP + (i + 1) // 2
        top = i % 2 == 0
        for x in range(X0 - 1, X1 + 2):
            over_wall = (x in (X0, X0 + 1, X1 - 1, X1) and Z0 <= z <= Z1) or z in (Z0, Z0 + 1, Z1 - 1, Z1)
            over_wall = over_wall and X0 <= x <= X1
            mat = pick(rng, ROOF_SLABS).short
            if top and over_wall and k != 0 and k != n - 1:
                b.set(x, y, z, pick(rng, STONE))            # the wall itself carries the plate
            else:
                b.set(x, y, z, slab(mat, "top" if top else "bottom"))
            # the gables: walled up to the plates
            if x in (X0, X0 + 1, X1 - 1, X1) and Z0 <= z <= Z1:
                for yy in range(WALL_TOP + 1, y):
                    b.set(x, yy, z, pick(rng, STONE))
            elif Z0 <= z <= Z1 and X0 <= x <= X1 and z in (Z0, Z0 + 1, Z1 - 1, Z1):
                for yy in range(WALL_TOP + 1, y):
                    b.set(x, yy, z, pick(rng, STONE))
    # the attic void under the plates
    for x in range(IX0, IX1 + 1):
        for z in range(IZ0, IZ1 + 1):
            for y in range(WALL_TOP + 1, WALL_TOP + 4):
                if b.get(x, y, z) is None:
                    b.set(x, y, z, AIR)
    # a few loose stones weighing the plates down against the wind
    for _ in range(6):
        x, z = rng.randint(X0, X1), rng.choice([Z0, Z0 + 2, Z1 - 2, Z1])
        ytop = max(y for (xx, y, zz) in b.cells if xx == x and zz == z)
        if b.get(x, ytop + 1, z) is None and b.inb(x, ytop + 1, z):
            b.set(x, ytop + 1, z, B("cobblestone_wall") if rng.random() < 0.3 else slab("cobblestone"))


def _chimney(b, rng, hz, y1):
    for x in (X0, X0 + 1):
        for y in range(WALL_TOP + 1, y1 + 1):
            b.set(x, y, hz, pick(rng, STONE))
    b.set(X0, y1 + 1, hz, slab("stone_brick"))
    b.set(X0 + 1, y1 + 1, hz, slab("stone_brick"))


def _road(b, rng):
    """The track over the pass, running past the door (east-west), and the step down to it."""
    trail(b, [(DOOR_X, Z1 + 1), (DOOR_X, 12), (DOOR_X - 1, 13)], rng, PATH, width=1, fade=0.9)
    trail(b, [(0, 14), (5, 13), (11, 13), (17, 12)], rng, PATH, width=2, fade=0.75, edge=0.3)


def slab_hut():
    b = Build(f"{NAME}_slab", 18, 10, 16, seed=1907601)
    rng = b.rng
    _hut_shell(b, rng)
    hz = _hearth(b, rng)
    _slab_roof(b, rng)
    _chimney(b, rng, hz, WALL_TOP + 4)
    _interior(b, rng)
    # woodpile along the east wall under a lean-to of slabs, the axe in the block
    for z in range(3, 8):
        h = 3 if z in (4, 5, 6) else 2
        for y in range(1, h + 1):
            b.set(X1 + 1, y, z, B("spruce_log", axis="x"))
        b.set(X1 + 1, 0, z, pick(rng, STONE))
        b.set(X1 + 1, 4, z, slab("spruce"))
    b.set(X1 + 1, 4, 2, slab("spruce"))
    b.set(X1 + 1, 4, 8, slab("spruce"))
    b.set(X1 + 2, 1, 8, B("spruce_log", axis="y"))
    ground_item(b, X1 + 2, 2, 8, item("iron_axe"), rotation=1)
    # the stone trough on the road, fed from a log spout
    tx0, tx1, tz = 10, 13, 11
    for x in range(tx0, tx1 + 1):
        for z in (tz - 1, tz, tz + 1):
            b.set(x, 0, z, B("stone"))
            inner = tx0 < x < tx1 and z == tz
            b.set(x, 1, z, B("water", level=0) if inner else pick(rng, [("stone", 2), ("cobblestone", 1)]))
    b.set(tx1 + 1, 0, tz, pick(rng, STONE))
    for y in (1, 2):
        b.set(tx1 + 1, y, tz, B("spruce_log", axis="y"))
    b.set(tx1, 2, tz, trapdoor("spruce", "west", "top", False))     # the spout
    # the hitching rail
    for x in (14, 17):
        b.set(x, 0, 10, pick(rng, STONE))
        b.set(x, 1, 10, B("spruce_fence"))
        b.set(x, 2, 10, B("spruce_fence"))
    for x in (15, 16):
        b.set(x, 2, 10, B("spruce_fence"))
    b.set(16, 1, 9, B("hay_block", axis="x"))
    # the shrine pillar by the road: a squared stone shaft, a niche with its candle, a stone cap
    sx, sz = 2, 12
    b.set(sx, 0, sz, B("stone_bricks"))
    b.set(sx, 1, sz, B("stone_bricks"))
    b.set(sx, 2, sz, B("stone_bricks"))
    b.set(sx, 3, sz, B("chiseled_stone_bricks"))
    b.set(sx, 4, sz, B("stone_bricks"))
    b.painting(sx, 3, sz + 1, "south", "meditative")
    for (dx, dz, f) in ((-1, 0, "east"), (1, 0, "west"), (0, -1, "south"), (0, 1, "north")):
        b.set(sx + dx, 5, sz + dz, stairs("stone_brick", f))
    b.set(sx, 5, sz, slab("stone_brick"))
    b.set(sx, 1, sz + 1, B("candle", candles=2, lit=False))
    b.set(sx, 0, sz + 1, B("stone_bricks"))
    plant(b, sx - 1, 1, sz + 1, "expanse:edelweiss", "grass_block")
    # a bench by the door in the lee of the hut
    b.set(8, 1, Z1 + 1, B("spruce_log", axis="x"))
    b.set(9, 1, Z1 + 1, B("spruce_log", axis="x"))
    _road(b, rng)
    clear(b, 0, 1, 10, 17, 4, 15)
    flora(b, [(x, z) for x in range(18) for z in range(16) if not (X0 - 1 <= x <= X1 + 1 and Z0 - 1 <= z <= Z1 + 1)],
          rng, 0.25, GROUND)
    return b


def shingle_hut():
    b = Build(f"{NAME}_shingle", 19, 12, 16, seed=1907603)
    rng = b.rng
    _hut_shell(b, rng, top=3)
    hz = _hearth(b, rng)
    # the hearth hood sits under the wall top here; the plank gables and steep shingle roof
    for z in range(Z0, Z1 + 1):                     # a log wall plate on both long walls
        for x in range(X0, X1 + 1):
            if _wall_cell(x, z) and (z in (Z0, Z1) or x in (X0, X1)):
                b.set(x, 4, z, B("stripped_spruce_log", axis="x") if z in (Z0, Z1) else
                      B("stripped_spruce_log", axis="z"))
            elif _wall_cell(x, z):
                b.set(x, 4, z, B("spruce_planks"))
    clear(b, IX0, 4, IZ0, IX1, 4, IZ1)

    def gable(x, y, z):
        return B("spruce_log", axis="y") if z % 3 == 0 else B("spruce_planks")
    ridge = roof_gable(b, X0, X1, Z0, Z1, 5, "spruce", gable, along="x", overhang=1, gable_overhang=1,
                       brackets=True)
    for (x, y, z), (st, _) in list(b.cells.items()):   # lichen and moss on the north slope
        if st.id == "minecraft:spruce_stairs" and z <= Z0 + 1 and rng.random() < 0.18:
            b.set(x, y, z, B("mossy_cobblestone_stairs", facing=st.get("facing"), half=st.get("half")))
    for x in (X0 + 1, X1 - 1):
        for z in range(Z0 + 1, Z1):
            if b.get(x, 5, z) is not None and b.get(x, 5, z).short == "spruce_planks":
                b.set(x, 5, z, B("spruce_planks"))
    _chimney(b, rng, hz, ridge + 1)
    _interior(b, rng)
    # the lean-to stable on the east side: log posts, a slab roof, hay, a manger and the rail
    sx0, sx1, sz0, sz1 = X1 + 1, X1 + 4, 3, 8
    for (x, z) in ((sx1, sz0), (sx1, sz1)):
        b.set(x, 0, z, pick(rng, STONE))
        for y in (1, 2, 3):
            b.set(x, y, z, B("spruce_log", axis="y"))
    for x in range(sx0, sx1 + 2):
        for z in range(sz0 - 1, sz1 + 2):
            b.set(x, 4 if x <= sx0 + 1 else 3 if x <= sx1 else 3, z,
                  slab("spruce", "bottom") if x > sx0 + 1 else slab("spruce", "top") if x == sx0 + 1 else
                  B("spruce_planks"))
    for z in range(sz0, sz1 + 1):
        if z not in (sz0, sz1):
            b.set(sx1, 1, z, B("spruce_fence"))
    b.set(sx1, 1, 6, B("spruce_fence_gate", facing="east", open=True))
    b.set(sx0, 1, sz0, B("hay_block", axis="z"))
    b.set(sx0, 1, sz0 + 1, B("hay_block", axis="z"))
    b.set(sx0, 2, sz0, B("hay_block", axis="x"))
    b.set(sx0, 1, sz1, B("composter", level=3))
    b.set(sx0 + 1, 1, sz0, trapdoor("spruce", "south", "bottom", True))
    for x in range(sx0, sx1):
        for z in range(sz0, sz1 + 1):
            if b.get(x, 0, z) is None:
                b.set(x, 0, z, pick(rng, [("coarse_dirt", 3), ("packed_mud", 1), ("gravel", 1)]))
    clear(b, sx0, 1, sz0, sx1 - 1, 3, sz1)
    # the trough: a hollowed log by the road, water in it
    for x in (10, 11, 12):
        b.set(x, 0, 11, B("stripped_spruce_log", axis="x"))
        b.set(x, 1, 11, B("water", level=0) if x == 11 else B("stripped_spruce_log", axis="x"))
        b.set(x, 1, 10, trapdoor("spruce", "north", "bottom", True))
        b.set(x, 1, 12, trapdoor("spruce", "south", "bottom", True))
    b.set(11, 1, 10, trapdoor("spruce", "north", "bottom", True))
    # the roofed wooden cross by the road
    cx, cz = 2, 12
    b.set(cx, 0, cz, pick(rng, STONE))
    for y in range(1, 5):
        b.set(cx, y, cz, B("spruce_log", axis="y"))
    b.set(cx - 1, 3, cz, B("spruce_fence"))
    b.set(cx + 1, 3, cz, B("spruce_fence"))
    b.set(cx, 5, cz, slab("spruce"))
    b.set(cx, 5, cz - 1, stairs("spruce", "south"))
    b.set(cx, 5, cz + 1, stairs("spruce", "north"))
    b.set(cx, 4, cz + 1, B("lantern", hanging=True))
    b.set(cx, 1, cz + 1, B("candle", candles=1, lit=False))
    b.set(cx, 0, cz + 1, B("stone"))
    plant(b, cx + 1, 1, cz + 1, "expanse:edelweiss", "grass_block")
    # a chopping block and the woodpile against the west gable
    for z in (3, 4, 7, 8):
        for y in (1, 2):
            b.set(X0 - 1, y, z, B("spruce_log", axis="x"))
    b.set(X0 - 1, 0, 5, pick(rng, STONE))
    _road(b, rng)
    clear(b, 0, 1, 10, 18, 4, 15)
    flora(b, [(x, z) for x in range(19) for z in range(16) if not (X0 - 1 <= x <= X1 + 4 and Z0 - 1 <= z <= Z1 + 1)],
          rng, 0.25, GROUND)
    return b


def pools():
    return {"start": [Piece(slab_hut(), 3, PROCS), Piece(shingle_hut(), 2, PROCS)]}


DATA = data_for(NAME, [PROCS])
