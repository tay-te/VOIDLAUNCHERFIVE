"""Ruined hill chapels of the lowlands (site=knoll: SitedJigsawStructure finds the top of a rise standing a dozen
blocks over the land, where a chapel is seen from every village round). The plains, meadows and broadleaf woods.

A small Romanesque chapel, older than the villages below and long out of use: a stone nave with round-headed
windows and a south portal, a round apse at the east end, a bell gable on the west front; part of the roof gone.
A low churchyard wall round it with the graves of the parish, and an old lime (oak) by the wall. A ruin, so it
stands whether or not VOID Hearth peoples the land.

start pool (2 variants)
  gable  : the nave roofed at its east end over the apse, bare rafters in the middle, open to the sky in
           the west; the bell still hangs in the gable over the west front. Pews in rows, the altar in
           the apse; the poor box by the door (public), and under the loose flag before the altar what
           the parish kept (hidden)
  belfry : a squat west tower with an open belfry and a pyramid roof; the nave roofless, its north wall
           fallen to its sills and spilled into the yard, a birch grown up in the aisle; the apse still
           roofed. Same poor box and hidden flag
"""
import math

from blocks import AIR, B
from builder import Build, slab, stairs
from common import vines_on
from pieces import Feature, Piece
from regions import MEADOW_LANDS, mc
from small_data import BOOKS, data_for
from smallkit import book, clear, flora, ground_item, pick, plant, rubble, small_tree, trail

NAME = "hill_chapel"
STRUCTURE = dict(biomes=[*MEADOW_LANDS, "woodland_glades", "ancient_oakwood", *mc("cherry_grove")],
                 size=1, max_distance=32, set="knolls", site="knoll", max_relief=24, weight=3)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
PROCS = "small_meadow"
STONE = [("stone_bricks", 4), ("cobblestone", 3), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1),
         ("andesite", 1)]
RUBBLE = [("cobblestone", 2), ("mossy_cobblestone", 2), ("stone_bricks", 1), ("gravel", 1)]
FLOOR = [("stone_bricks", 4), ("cracked_stone_bricks", 2), ("gravel", 1), ("coarse_dirt", 2), ("grass_block", 2)]
GROUND = ["grass_block", "grass_block", "grass_block", "coarse_dirt"]
PATH = [("dirt_path", 4), ("coarse_dirt", 3), ("gravel", 2)]
ROOF = "deepslate_tile"
TREE_POOL = f"expanse:{NAME}/tree"

# nave: outer x 5..14, z 7..13 (interior x 6..13, z 8..12); apse round the point (14, 10) to the east;
# walls to y 5, the roof from y 6; the south portal at x 8
NX0, NX1, NZ0, NZ1 = 5, 14, 7, 13
CZ = 10
EAVE = 5
PORTAL = 8
EPITAPHS = [["", "Here lies", "Jan the smith", ""], ["", "Marketa", "", ""], ["", "Pray for", "the souls", "of the hill"],
            ["", "Old Vitek", "", ""], ["", "Two brothers", "", ""], ["", "Agnes", "beloved", ""]]


def _apse_cells(r0, r1):
    return [(x, z) for x in range(NX1 + 1, NX1 + 5) for z in range(CZ - 4, CZ + 5)
            if r0 <= math.hypot(x - NX1, z - CZ) <= r1]


def _nave(b, rng, north_top=None):
    """Floor, walls with buttresses and round-headed windows, the south portal, the apse and chancel arch.
    north_top: per-x height of a fallen north wall (None: whole)."""
    for x in range(NX0, NX1 + 1):
        for z in range(NZ0, NZ1 + 1):
            wall = x in (NX0, NX1) or z in (NZ0, NZ1)
            b.set(x, 0, z, pick(rng, STONE) if wall else pick(rng, FLOOR))
            if not wall:
                continue
            top = EAVE
            if z == NZ0 and north_top is not None and NX0 < x < NX1:
                top = north_top[x]
            for y in range(1, top + 1):
                b.set(x, y, z, pick(rng, STONE))
    clear(b, NX0 + 1, 1, NZ0 + 1, NX1 - 1, EAVE, NZ1 - 1)
    # buttresses on the long walls, stepped back under a stair
    for x in (NX0 + 3, NX0 + 6):
        for z, f in ((NZ0 - 1, "south"), (NZ1 + 1, "north")):
            if z == NZ0 - 1 and north_top is not None and north_top[x] < 3:
                continue
            b.set(x, 0, z, pick(rng, STONE))
            for y in (1, 2):
                b.set(x, y, z, pick(rng, STONE))
            b.set(x, 3, z, stairs("stone_brick", f))
    # round-headed windows: a slit two high, a stair shoulder over it inside
    for x in (NX0 + 2, NX0 + 5, NX1 - 2):
        for z in (NZ0, NZ1):
            if b.get(x, 3, z) is None or (z == NZ1 and x == PORTAL):
                continue
            b.set(x, 2, z, AIR)
            b.set(x, 3, z, AIR)
    b.set(NX1 - 2, 2, NZ1, B("iron_bars"))
    # the south portal: a round arch three wide, an oak door half off its hinges
    for dx in (-1, 0, 1):
        for y in (1, 2):
            b.set(PORTAL + dx, y, NZ1, AIR)
    b.set(PORTAL, 3, NZ1, AIR)
    b.set(PORTAL - 1, 3, NZ1, stairs("stone_brick", "west", "top"))
    b.set(PORTAL + 1, 3, NZ1, stairs("stone_brick", "east", "top"))
    b.set(PORTAL, 4, NZ1, B("chiseled_stone_bricks"))
    b.door(PORTAL + 1, 1, NZ1, "dark_oak", "south", hinge="right", open_=True)
    b.set(PORTAL, 0, NZ1 + 1, B("stone_bricks"))
    b.set(PORTAL - 1, 0, NZ1 + 1, slab("stone_brick", "double"))
    # the apse: a half-round wall to y 4 under a stepped stone half-dome, the chancel arch into it
    for (x, z) in _apse_cells(2.3, 3.3):
        b.set(x, 0, z, pick(rng, STONE))
        for y in range(1, EAVE):
            b.set(x, y, z, pick(rng, STONE))
    for (x, z) in _apse_cells(0, 2.29):
        b.set(x, 0, z, pick(rng, [("stone_bricks", 3), ("cracked_stone_bricks", 1)]))
        for y in range(1, EAVE):
            b.set(x, y, z, AIR)
    for (x, z) in _apse_cells(0, 3.3):
        b.set(x, EAVE, z, slab(ROOF, "bottom") if math.hypot(x - NX1, z - CZ) > 2.3 else B(f"{ROOF}s"))
    for (x, z) in _apse_cells(0, 1.6):
        b.set(x, EAVE + 1, z, slab(ROOF))
    b.set(NX1 + 3, 2, CZ, AIR)                          # the east window over the altar
    for dz in (-1, 0, 1):
        for y in (1, 2, 3):
            b.set(NX1, y, CZ + dz, AIR)
    b.set(NX1, 4, CZ, AIR)
    b.set(NX1, 4, CZ - 1, stairs("stone_brick", "north", "top"))
    b.set(NX1, 4, CZ + 1, stairs("stone_brick", "south", "top"))
    # the altar, cracked, a candle stub; the loose flag before it over the parish box
    b.set(NX1 + 2, 1, CZ, B("chiseled_stone_bricks"))
    b.set(NX1 + 2, 1, CZ - 1, stairs("stone_brick", "north"))
    b.set(NX1 + 2, 1, CZ + 1, stairs("stone_brick", "south"))
    b.set(NX1 + 2, 2, CZ, B("candle", candles=3, lit=False))
    b.chest(NX1 + 1, 0, CZ, "west", HIDDEN)
    b.set(NX1 + 1, 1, CZ, slab("stone_brick"))
    # the poor box by the door, the parish book left on a pew
    b.barrel(PORTAL - 2, 1, NZ1 - 1, "up", LOOT)
    b.set(PORTAL - 2, 2, NZ1 - 1, B("candle", candles=1, lit=False))


def _pews(b, rng, xs, broken=0.3):
    for x in xs:
        for z in (CZ - 2, CZ - 1, CZ + 1, CZ + 2):
            r = rng.random()
            if r < broken:
                if rng.random() < 0.5:
                    b.set(x, 1, z, slab("spruce"))
                continue
            b.set(x, 1, z, stairs("spruce", "west"))


def _west_gable(b, rng, y_ridge, bell=True):
    """The west front carried up as a gable to the ridge and above it the bell gable with its bell."""
    for k, y in enumerate(range(EAVE + 1, y_ridge + 1)):
        for z in range(NZ0 + 1 + k, NZ1 - k):
            b.set(NX0, y, z, pick(rng, STONE))
    yb = y_ridge + 1
    for z in (CZ - 1, CZ + 1):
        for y in (yb, yb + 1):
            b.set(NX0, y, z, pick(rng, [("stone_bricks", 3), ("mossy_stone_bricks", 1)]))
    b.set(NX0, yb, CZ, AIR)
    b.set(NX0, yb + 1, CZ, AIR)
    for z in (CZ - 1, CZ, CZ + 1):
        b.set(NX0, yb + 2, z, B("stone_bricks"))
    b.set(NX0, yb + 3, CZ - 1, stairs("stone_brick", "south"))
    b.set(NX0, yb + 3, CZ + 1, stairs("stone_brick", "north"))
    b.set(NX0, yb + 3, CZ, B("stone_bricks"))
    b.set(NX0, yb + 4, CZ, slab("stone_brick"))
    if bell:
        b.bell(NX0, yb + 1, CZ, attachment="ceiling", facing="east")
    b.set(NX0, 3, CZ, AIR)                              # a round window low in the west front
    b.set(NX0 + 1, EAVE, CZ, B("iron_chain", axis="y"))


def _roof(b, rng, x_from, ribs=()):
    """Slate roof of the nave from x_from to the east gable (with its gable wall); bare rafters at `ribs`."""
    lo, hi = NZ0 - 1, NZ1 + 1
    k = 0
    while lo + k <= hi - k:
        p, q = lo + k, hi - k
        y = EAVE + 1 + k
        for x in list(range(x_from, NX1 + 2)) + list(ribs):
            if p == q:
                b.set(x, y, p, slab(ROOF) if x not in ribs else B("stripped_dark_oak_log", axis="x"))
                continue
            if x in ribs:
                b.set(x, y, p, stairs("dark_oak", "south"))
                b.set(x, y, q, stairs("dark_oak", "north"))
            else:
                b.set(x, y, p, stairs(ROOF, "south") if rng.random() > 0.12 else stairs("mossy_cobblestone", "south"))
                b.set(x, y, q, stairs(ROOF, "north") if rng.random() > 0.12 else stairs("mossy_cobblestone", "north"))
        for z in range(max(p + 1, NZ0), min(q - 1, NZ1) + 1):     # the east gable
            b.set(NX1, y, z, pick(rng, STONE))
            for x in range(x_from, NX1):
                if b.get(x, y, z) is None:
                    b.set(x, y, z, AIR)
        k += 1
    # the broken edge: a few tiles hanging on, the ridge beam running on into the open
    for z in range(NZ0 - 1, NZ1 + 2):
        if rng.random() < 0.35 and b.get(x_from - 1, EAVE + 1, z) is None:
            pass
    return EAVE + k


def _yard(b, rng, sx, sz, gate_x, tree_at):
    """Low churchyard wall round the template edge with a gap for the gate, graves, the lime tree."""
    for x in range(1, sx - 1):
        for z in range(1, sz - 1):
            if x not in (1, sx - 2) and z not in (1, sz - 2):
                continue
            if z == sz - 2 and gate_x - 1 <= x <= gate_x + 1:
                continue
            if b.get(x, 0, z) is not None:
                continue
            if rng.random() < 0.1:                     # tumbled
                b.set(x, 0, z, pick(rng, RUBBLE))
                continue
            b.set(x, 0, z, pick(rng, [("cobblestone", 3), ("mossy_cobblestone", 2), ("stone", 1)]))
            b.set(x, 1, z, pick(rng, [("cobblestone", 3), ("mossy_cobblestone", 3), ("stone", 1)]))
            if rng.random() < 0.3:
                b.set(x, 2, z, slab("cobblestone") if rng.random() < 0.6 else B("moss_carpet"))
    for x in (gate_x - 1, gate_x + 1):                   # the gate posts
        b.set(x, 0, sz - 2, B("stone_bricks"))
        b.set(x, 1, sz - 2, B("stone_bricks"))
        b.set(x, 2, sz - 2, slab("stone_brick"))
    tx, tz = tree_at
    b.jigsaw(tx, 0, tz, "up", pool=TREE_POOL, final="minecraft:grass_block")
    b.set(tx, 1, tz, AIR)


def _grave(b, rng, x, z, kind, epitaph=None):
    """Marker at z (the head, to the west of the yard's rows they face east), the mound running south."""
    b.set(x, 0, z, pick(rng, [("cobblestone", 1), ("stone", 1)]))
    if kind == "cross":
        b.set(x, 1, z, B("spruce_fence"))
        b.set(x, 2, z, B("spruce_fence"))
        b.set(x, 3, z, B("spruce_fence"))
        b.set(x - 1, 2, z, B("spruce_fence"))
        b.set(x + 1, 2, z, B("spruce_fence"))
    elif kind == "stone_cross":
        b.set(x, 1, z, B("stone_bricks"))
        b.set(x, 2, z, B("stone_brick_wall"))
        b.set(x - 1, 2, z, B("stone_button", face="wall", facing="west"))
        b.set(x + 1, 2, z, B("stone_button", face="wall", facing="east"))
    elif kind == "stone":
        b.set(x, 1, z, B("stone_bricks"))
        b.set(x, 2, z, slab("stone_brick"))
    elif kind == "post":                               # a wooden marker rotted to its stump
        b.set(x, 1, z, B("stripped_spruce_log", axis="y"))
    if epitaph and kind == "stone":
        b.sign(x, 1, z + 1, "spruce", epitaph, wall_facing="south")
    for dz in (1, 2):
        if b.get(x, 0, z + dz) is None:
            b.set(x, 0, z + dz, B("coarse_dirt") if rng.random() < 0.6 else B("grass_block"))
        if b.get(x, 1, z + dz) is None and rng.random() < 0.4:
            plant(b, x, 1, z + dz, rng.choice(["poppy", "short_grass", "lily_of_the_valley", "oxeye_daisy"]),
                  "coarse_dirt")


def _graves(b, rng, rows):
    eps = list(EPITAPHS)
    rng.shuffle(eps)
    kinds = ["cross", "stone", "cross", "post", "stone_cross", "stone", "cross", "post"]
    i = 0
    for (z, xs) in rows:
        for x in xs:
            _grave(b, rng, x, z, kinds[i % len(kinds)], eps[i % len(eps)])
            i += 1


def gable():
    b = Build(f"{NAME}_gable", 23, 16, 21, seed=1907621)
    rng = b.rng
    _nave(b, rng)
    ridge = _roof(b, rng, 11, ribs=(7, 9))
    for x in range(6, 11):                              # the ridge beam the bare rafters hang from
        b.set(x, ridge, CZ, B("stripped_dark_oak_log", axis="x"))
    _west_gable(b, rng, ridge - 1)
    _pews(b, rng, (7, 9, 11))
    ground_item(b, 11, 2, CZ + 2, book(BOOKS[NAME]), rotation=1)
    # what came down from the open west end: slates and stones on the floor, grass through the flags
    rubble(b, [(x, z) for x in range(NX0 + 1, 10) for z in range(NZ0 + 1, NZ1)], rng, [s for s, _ in RUBBLE],
           loose=[slab(ROOF), stairs(ROOF, "north"), B("cobblestone")], density=0.22)
    flora(b, [(x, z) for x in range(NX0 + 1, 10) for z in range(NZ0 + 1, NZ1)], rng, 0.3, ["coarse_dirt"])
    _yard(b, rng, 23, 21, PORTAL, (19, 4))
    _graves(b, rng, [(15, (3, 12, 15, 18)), (2, (4, 7, 11, 15))])
    trail(b, [(PORTAL, NZ1 + 1), (PORTAL, 17), (PORTAL, 20)], rng, PATH, width=1, fade=0.7)
    vines_on(b, rng, 0.05, ymin=2, max_len=3)
    clear(b, 2, 1, 2, 20, 4, 18)
    flora(b, [(x, z) for x in range(23) for z in range(21)], rng, 0.18, GROUND)
    return b


def belfry():
    b = Build(f"{NAME}_belfry", 23, 18, 21, seed=1907623)
    rng = b.rng
    north = {x: (1 if rng.random() < 0.5 else 2) + (1 if x in (NX0 + 1, NX1 - 1) else 0) for x in range(NX0, NX1 + 1)}
    _nave(b, rng, north_top=north)
    # the west tower over the west end of the nave: x 3..6, z 8..12, to y 11, an open belfry, a pyramid roof
    tx0, tx1, tz0, tz1 = 2, 6, 8, 12
    for x in range(tx0, tx1 + 1):
        for z in range(tz0, tz1 + 1):
            wall = x in (tx0, tx1) or z in (tz0, tz1)
            b.set(x, 0, z, pick(rng, STONE) if wall else pick(rng, FLOOR))
            if wall:
                for y in range(1, 12):
                    b.set(x, y, z, pick(rng, STONE))
            else:
                for y in range(1, 12):
                    b.set(x, y, z, AIR)
    for (x, z) in ((tx0, tz0), (tx1, tz0), (tx0, tz1), (tx1, tz1)):
        for y in range(1, 12):
            b.set(x, y, z, B("stone_bricks"))
    for y in (1, 2):                                    # the arch from the tower into the nave
        for z in (CZ - 1, CZ, CZ + 1):
            b.set(tx1, y, z, AIR)
    b.set(tx1, 3, CZ, AIR)
    for y in (9, 10):                                   # the belfry openings, a pair in each face
        for z in (CZ - 1, CZ + 1):
            b.set(tx0, y, z, AIR)
            b.set(tx1, y, z, AIR)
        for x in (tx0 + 1, tx1 - 1):
            b.set(x, y, tz0, AIR)
            b.set(x, y, tz1, AIR)
    b.set(tx0, 4, CZ, AIR)
    b.set(tx0, 5, CZ, AIR)
    for x in range(tx0 + 1, tx1):                       # the bell floor and the bell
        for z in range(tz0 + 1, tz1):
            b.set(x, 8, z, B("spruce_planks") if rng.random() < 0.8 else AIR)
    b.bell(4, 10, CZ, attachment="ceiling", facing="east")
    for x in range(tx0 + 1, tx1):
        for z in range(tz0 + 1, tz1):
            b.set(x, 11, z, B("dark_oak_planks"))
    # a pyramid roof of slate
    for k in range(3):
        a0, a1, c0, c1 = tx0 - 1 + k, tx1 + 1 - k, tz0 - 1 + k, tz1 + 1 - k
        y = 12 + k
        for x in range(a0, a1 + 1):
            b.set(x, y, c0, stairs(ROOF, "south"))
            b.set(x, y, c1, stairs(ROOF, "north"))
        for z in range(c0 + 1, c1):
            b.set(a0, y, z, stairs(ROOF, "east"))
            b.set(a1, y, z, stairs(ROOF, "west"))
        for x in range(a0 + 1, a1):
            for z in range(c0 + 1, c1):
                b.set(x, y, z, B(f"{ROOF}s"))
    b.set(4, 15, CZ, slab(ROOF))
    b.set(4, 16, CZ, B("iron_bars"))
    # the nave: its east gable stands, roofless; pews mostly gone, a birch in the aisle
    for k, y in enumerate(range(EAVE + 1, EAVE + 5)):
        for z in range(NZ0 + 1 + k, NZ1 - k):
            b.set(NX1, y, z, pick(rng, STONE))
    _pews(b, rng, (9, 11), broken=0.55)
    small_tree(b, 8, CZ, rng, "birch_log", "birch_leaves", h=5, r=2.0)
    # the north wall spilled out into the yard
    rubble(b, [(x, z) for x in range(NX0, NX1 + 1) for z in range(NZ0 - 3, NZ0)], rng, [s for s, _ in RUBBLE],
           loose=[slab("stone_brick"), stairs("stone_brick", "south"), B("mossy_cobblestone")], density=0.45)
    rubble(b, [(x, z) for x in range(NX0 + 1, NX1) for z in range(NZ0 + 1, NZ1)], rng, [s for s, _ in RUBBLE],
           loose=[slab("stone_brick"), B("cobblestone")], density=0.15)
    flora(b, [(x, z) for x in range(NX0 + 1, NX1) for z in range(NZ0 + 1, NZ1)], rng, 0.35, ["coarse_dirt"])
    _yard(b, rng, 23, 21, PORTAL + 3, (19, 17))
    _graves(b, rng, [(15, (3, 13, 16)), (1 + 1, (9, 13, 17))])
    trail(b, [(PORTAL, NZ1 + 1), (PORTAL + 2, 17), (PORTAL + 3, 20)], rng, PATH, width=1, fade=0.7)
    vines_on(b, rng, 0.07, ymin=2, max_len=4)
    clear(b, 2, 1, 2, 20, 4, 18)
    flora(b, [(x, z) for x in range(23) for z in range(21)], rng, 0.18, GROUND)
    return b


def pools():
    # (the churchyard tree an ordinary oak or a birch: a fancy oak's crown spreads over the nave from the yard's corner)
    return {"start": [Piece(gable(), 3, PROCS), Piece(belfry(), 2, PROCS)],
            "tree": [Feature("minecraft:oak_checked", 2), Feature("minecraft:birch_checked", 1)]}


DATA = data_for(NAME, [PROCS])
