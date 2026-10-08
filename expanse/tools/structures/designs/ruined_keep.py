"""Ruined keeps on the knolls of the lowlands and uplands (site=knoll: SitedJigsawStructure finds the top of a rise
standing a dozen blocks over the land round it). The plains, meadows and broadleaf woods, the moors and the
windswept hills.

A small lord's tower house, given up generations ago and quarried by the villages since: a square keep of plain
rubble stone some thirteen high (not the tall limestone watchtower of the peaks, ruined_watchtower.py), the
stretch of curtain wall that closed its little bailey, the bailey well. Moss and vines over all of it.

start pool (2 variants)
  corner : the north-east corner fallen from the second floor up, its stones spilled down the slope;
           a battered plinth, arrow slits, the first floor's boards half there, the fireplace hanging in
           the west wall, a doorway in mid-air on the east where the outside stair stood; a ladder up
           from the ground floor; the curtain wall running west and turning south, broken down; the
           well in the bailey
  face   : the whole east face come down in one, the floors and the fireplace open to the weather like a
           cut-away; a high heap of rubble against it; the curtain wall with the stub of its gate arch;
           the well by the gate
  Both: what the last garrison left in a chest under the rubble of the ground floor (public), and under
  a trapdoor in the floor of the ground floor corner the lord's strongbox (hidden).
"""
import math

from blocks import AIR, B
from builder import Build, slab, stairs, trapdoor
from common import vines_on
from pieces import Piece
from regions import MEADOW_LANDS, mc
from small_data import data_for
from smallkit import clear, flora, pick, rubble, trail

NAME = "ruined_keep"
STRUCTURE = dict(biomes=[*MEADOW_LANDS, "woodland_glades", "ancient_oakwood", "heather_moor", "pine_heath",
                         *mc("windswept_hills", "windswept_forest", "taiga")],
                 size=1, max_distance=32, set="knolls", site="knoll", max_relief=26, weight=2)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
PROCS = "small_meadow"
STONE = [("cobblestone", 4), ("stone", 3), ("andesite", 3), ("mossy_cobblestone", 2), ("tuff", 1)]
QUOIN = [("stone_bricks", 3), ("stone", 2), ("mossy_stone_bricks", 1)]
RUBBLE = ["cobblestone", "mossy_cobblestone", "andesite", "stone", "gravel"]
LOOSE = [slab("cobblestone"), stairs("cobblestone", "north"), stairs("andesite", "east"), slab("andesite"),
         B("mossy_cobblestone")]
GROUND = ["grass_block", "grass_block", "grass_block", "coarse_dirt"]
PATH = [("dirt_path", 3), ("coarse_dirt", 3), ("gravel", 2)]

# keep: outer x 8..16, z 3..11 (inside x 9..15, z 4..10); floors at y 5 and 9, the wall head at y 13
KX0, KX1, KZ0, KZ1 = 8, 16, 3, 11
F1, F2, TOP = 5, 9, 13


def _keep(b, rng, top_at):
    """The keep's shell: battered plinth, rubble walls with dressed quoins to top_at(x, z), slits, door."""
    for x in range(KX0, KX1 + 1):
        for z in range(KZ0, KZ1 + 1):
            wall = x in (KX0, KX1) or z in (KZ0, KZ1)
            b.set(x, 0, z, pick(rng, STONE) if wall else pick(rng, [("coarse_dirt", 3), ("gravel", 2),
                                                                     ("cobblestone", 2), ("grass_block", 1)]))
            if not wall:
                continue
            corner = x in (KX0, KX1) and z in (KZ0, KZ1)
            for y in range(1, top_at(x, z) + 1):
                b.set(x, y, z, pick(rng, QUOIN if corner else STONE))
    # the batter: a sloped course round the foot
    for x in range(KX0 - 1, KX1 + 2):
        for z in range(KZ0 - 1, KZ1 + 2):
            if KX0 <= x <= KX1 and KZ0 <= z <= KZ1:
                continue
            if (x in (KX0 - 1, KX1 + 1)) and (z in (KZ0 - 1, KZ1 + 1)):
                continue
            nx = min(max(x, KX0), KX1)
            nz = min(max(z, KZ0), KZ1)
            if top_at(nx, nz) < 2:
                continue
            f = "east" if x < KX0 else "west" if x > KX1 else "south" if z < KZ0 else "north"
            b.set(x, 0, z, pick(rng, STONE))
            b.set(x, 1, z, stairs("cobblestone", f) if rng.random() < 0.7 else stairs("andesite", f))
    clear(b, KX0 + 1, 1, KZ0 + 1, KX1 - 1, TOP, KZ1 - 1)
    # arrow slits on every face, three storeys of them
    for y0 in (3, 7, 11):
        for x in (KX0 + 3, KX1 - 3):
            for z in (KZ0, KZ1):
                if b.get(x, y0 + 1, z) is not None:
                    b.set(x, y0, z, AIR)
                    b.set(x, y0 + 1, z, AIR)
        for z in (KZ0 + 4,):
            for x in (KX0, KX1):
                if b.get(x, y0 + 1, z) is not None:
                    b.set(x, y0, z, AIR)
                    b.set(x, y0 + 1, z, AIR)
    # the ground-floor door on the south, a round head
    dx = KX0 + 4
    for y in (1, 2, 3):
        b.set(dx, y, KZ1, AIR)
    b.set(dx - 1, 3, KZ1, stairs("stone_brick", "west", "top"))
    b.set(dx + 1, 3, KZ1, stairs("stone_brick", "east", "top"))
    b.set(dx, 4, KZ1, B("stone_bricks"))
    b.set(dx, 0, KZ1 + 1, B("stone_bricks"))
    # putlog holes and the corbels the floors rested on
    for y in (F1 - 1, F2 - 1):
        for x in range(KX0 + 2, KX1 - 1, 2):
            for z in (KZ0 + 1, KZ1 - 1):
                wall = b.get(x, y, z + (1 if z == KZ1 - 1 else -1))
                if (b.get(x, y, z) is None or b.get(x, y, z).short == "air") and wall is not None and                         wall.short != "air":
                    b.set(x, y, z, stairs("stone_brick", "south" if z == KZ1 - 1 else "north", "top"))


def _floor(b, rng, y, keep, beams=True):
    """A boarded floor at y on joists running along x; `keep(x, z)` -> whether the boards are still there."""
    for x in range(KX0 + 1, KX1):
        for z in range(KZ0 + 1, KZ1):
            if z % 2 == 0 and beams and keep(x, z, True):
                b.set(x, y, z, B("stripped_dark_oak_log", axis="x"))
            elif keep(x, z, False):
                b.set(x, y, z, B("dark_oak_planks") if rng.random() < 0.7 else slab("dark_oak", "top"))


def _fireplace(b, rng, x, z, y):
    """A fireplace in the west wall at floor y: a recess, stone cheeks, a hood, the flue up the wall."""
    b.set(x, y + 1, z, AIR)
    b.set(x, y + 2, z, AIR)
    for dz in (-1, 1):
        b.set(x + 1, y + 1, z + dz, B("stone_bricks"))
        b.set(x + 1, y + 2, z + dz, B("stone_bricks"))
    b.set(x + 1, y + 3, z, stairs("stone_brick", "west", "top"))
    b.set(x + 1, y + 3, z - 1, stairs("stone_brick", "north", "top"))
    b.set(x + 1, y + 3, z + 1, stairs("stone_brick", "south", "top"))
    b.campfire(x, y + 1, z, lit=False, facing="east")


def _curtain(b, rng, cells, h, merlon_side):
    """A stretch of curtain wall two thick over cells [(x, z)] (pairs inner/outer handled by the caller)."""
    for i, (x, z) in enumerate(cells):
        hh = h[i]
        b.set(x, 0, z, pick(rng, STONE))
        for y in range(1, hh + 1):
            b.set(x, y, z, pick(rng, STONE))
        if hh >= 4 and merlon_side(x, z) and i % 2 == 0:
            b.set(x, hh + 1, z, pick(rng, STONE))
        elif hh >= 3 and rng.random() < 0.35:
            b.set(x, hh + 1, z, slab("cobblestone") if rng.random() < 0.6 else B("moss_carpet"))


def _well(b, rng, cx, cz):
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                b.set(cx + dx, 0, cz + dz, pick(rng, STONE))
                b.set(cx + dx, 1, cz + dz, pick(rng, [("cobblestone", 2), ("mossy_cobblestone", 2), ("stone_bricks", 1)]))
                if rng.random() < 0.5:
                    b.set(cx + dx, 2, cz + dz, B("cobblestone_wall") if (dx == 0 or dz == 0) else slab("cobblestone"))
    b.set(cx, 0, cz, B("water", level=0))
    b.set(cx, 1, cz, B("water", level=0))
    b.set(cx, 2, cz, AIR)
    b.set(cx - 1, 2, cz, B("oak_fence"))               # what is left of the windlass post
    b.set(cx - 1, 3, cz, B("oak_fence"))


def _ground_floor(b, rng, keep_ladder=True):
    """Rubble, the garrison's chest half under it, the strongbox under a trapdoor, the ladder up."""
    b.chest(KX0 + 2, 1, KZ1 - 2, "east", LOOT)
    b.set(KX0 + 1, 1, KZ1 - 2, B("cobblestone"))
    b.set(KX0 + 2, 1, KZ1 - 1, slab("cobblestone"))
    b.set(KX0 + 1, 1, KZ1 - 1, B("barrel", facing="up"))
    b.set(KX1 - 1, 0, KZ1 - 1, None)
    b.chest(KX1 - 1, 0, KZ1 - 1, "west", HIDDEN)
    b.set(KX1 - 1, 1, KZ1 - 1, trapdoor("dark_oak", "west", "bottom", False))
    if keep_ladder:
        for y in range(1, F1 + 1):
            b.set(KX0 + 2, y, KZ0 + 1, B("ladder", facing="south"))
        b.set(KX0 + 2, F1, KZ0 + 1, B("ladder", facing="south"))
    rubble(b, [(x, z) for x in range(KX0 + 3, KX1) for z in range(KZ0 + 2, KZ1 - 1)], rng, RUBBLE, loose=LOOSE,
           density=0.35)


def corner():
    b = Build(f"{NAME}_corner", 21, 17, 20, seed=1907631)
    rng = b.rng

    def top_at(x, z):
        d = (KX1 - x) + (z - KZ0)                      # steps from the fallen north-east corner
        if d > 8:
            return TOP
        return min(TOP, 3 + d + (1 if rng.random() < 0.4 else 0))
    _keep(b, rng, top_at)
    # merlons on the standing wall head
    for x in range(KX0, KX1 + 1):
        for z in range(KZ0, KZ1 + 1):
            if (x in (KX0, KX1) or z in (KZ0, KZ1)) and top_at(x, z) == TOP and (x + z) % 2 == 0 and rng.random() < 0.8:
                b.set(x, TOP + 1, z, pick(rng, STONE))
    # first floor: boards along the south and west, joists only toward the fall; second floor: two beams
    _floor(b, rng, F1, lambda x, z, beam: (x + (KZ1 - z) < 12 or beam and rng.random() < 0.6) and
           not (x == KX0 + 2 and z == KZ0 + 1))
    for x in range(KX0 + 1, KX1 - 2):
        b.set(x, F2, KZ1 - 2, B("stripped_dark_oak_log", axis="x"))
    _fireplace(b, rng, KX0, KZ0 + 5, F1)
    # the doorway in mid-air on the east (the outside stair is gone), its sill stone
    for y in (F1 + 1, F1 + 2):
        b.set(KX1, y, KZ1 - 2, AIR)
    b.set(KX1 + 1, F1, KZ1 - 2, slab("stone_brick", "top"))
    _ground_floor(b, rng)
    # the fall: a spill of stones down the north-east
    for x in range(KX1 - 4, 21):
        for z in range(0, KZ0 + 5):
            if KX0 <= x <= KX1 and KZ0 <= z <= KZ1:
                continue
            d = math.hypot(x - (KX1 + 0.5), z - (KZ0 - 0.5))
            h = int(3.2 - d * 0.7 + rng.random())
            if d > 5.5 or b.get(x, 1, z) is not None:
                continue
            b.set(x, 0, z, pick(rng, [(s, 1) for s in RUBBLE]))
            for y in range(1, max(0, h) + 1):
                b.set(x, y, z, pick(rng, [(s, 1) for s in RUBBLE[:4]]))
            if rng.random() < 0.4:
                b.set(x, max(0, h) + 1, z, pick(rng, LOOSE))
    rubble(b, [(x, z) for x in range(KX0 + 4, KX1) for z in range(KZ0 + 1, KZ0 + 4)], rng, RUBBLE, loose=LOOSE,
           density=0.6)
    # the curtain: west along the bailey's north side, then south; broken down toward its end
    north = [(x, z) for x in range(1, KX0) for z in (KZ1 - 2, KZ1 - 1)]
    hn = [max(1, 5 - (KX0 - 1 - x) // 3 - (1 if rng.random() < 0.3 else 0)) for (x, z) in north]
    _curtain(b, rng, north, hn, lambda x, z: z == KZ1 - 2)
    west = [(x, z) for z in range(KZ1, 19) for x in (1, 2)]
    hw = [max(0, 3 - (z - KZ1) // 2 - (1 if rng.random() < 0.4 else 0)) for (x, z) in west]
    _curtain(b, rng, west, hw, lambda x, z: x == 1)
    rubble(b, [(x, z) for x in range(3, 6) for z in range(KZ1, 19)], rng, RUBBLE, loose=LOOSE, density=0.25)
    _well(b, rng, 9, 15)
    trail(b, [(KX0 + 4, KZ1 + 1), (11, 16), (14, 19)], rng, PATH, width=1, fade=0.6)
    vines_on(b, rng, 0.06, ymin=3, max_len=4)
    for (x, y, z), (st, _) in list(b.cells.items()):    # moss over the wall heads
        if st.short in ("cobblestone", "stone", "andesite", "stone_bricks") and b.inb(x, y + 1, z) and \
                b.get(x, y + 1, z) is None and y >= 3 and rng.random() < 0.25:
            b.set(x, y + 1, z, B("moss_carpet"))
    clear(b, 3, 1, KZ1 + 2, 20, 3, 19)
    flora(b, [(x, z) for x in range(21) for z in range(20)], rng, 0.2, GROUND)
    flora(b, [(x, z) for x in range(KX0 + 1, KX1) for z in range(KZ0 + 1, KZ1)], rng, 0.25, ["coarse_dirt"],
          plants=("short_grass", "fern"))
    return b


def face():
    b = Build(f"{NAME}_face", 22, 17, 20, seed=1907633)
    rng = b.rng

    def top_at(x, z):
        if x == KX1 and KZ0 < z < KZ1:
            return 1 + (1 if rng.random() < 0.5 else 0)
        if x == KX1:                                    # the corners keep a ragged stump
            return 6 + rng.randint(0, 3)
        if x >= KX1 - 2:
            return TOP - 2 - rng.randint(0, 2)
        return TOP - (1 if rng.random() < 0.25 else 0)
    _keep(b, rng, top_at)
    _floor(b, rng, F1, lambda x, z, beam: x < KX1 - 1 or (beam and x < KX1))
    _floor(b, rng, F2, lambda x, z, beam: (x < KX1 - 3 and rng.random() < 0.75) or (beam and x < KX1 - 1))
    _fireplace(b, rng, KX0, KZ0 + 5, F1)
    _fireplace(b, rng, KX0, KZ0 + 3, F2)
    b.set(KX0 + 1, F1 + 1, KZ1 - 1, slab("dark_oak", "top"))     # a shelf of the old room
    b.set(KX0 + 1, F2 + 1, KZ1 - 1, B("barrel", facing="up"))
    _ground_floor(b, rng)
    # the high heap where the face came down
    for x in range(KX1 - 1, 22):
        for z in range(KZ0 - 2, KZ1 + 3):
            if KX0 <= x < KX1 and KZ0 <= z <= KZ1:
                continue
            d = abs(x - (KX1 + 1)) * 0.9 + abs(z - (KZ0 + KZ1) / 2) * 0.45
            h = int(4.0 - d + rng.random() * 1.2)
            if h < 0 or (x == KX1 and b.get(x, 1, z) is not None and h <= 1):
                continue
            if b.get(x, 0, z) is None or x > KX1:
                b.set(x, 0, z, pick(rng, [(s, 1) for s in RUBBLE]))
            for y in range(1, h + 1):
                if b.get(x, y, z) is None or b.get(x, y, z).short == "air":
                    b.set(x, y, z, pick(rng, [(s, 1) for s in RUBBLE[:4]]))
            if rng.random() < 0.45 and b.get(x, h + 1, z) in (None, AIR):
                b.set(x, h + 1, z, pick(rng, LOOSE))
    # the curtain wall south from the keep's south-west corner, with the stub of the gate arch
    wall = [(x, z) for z in range(KZ1 + 1, 19) for x in (KX0, KX0 + 1) if not (15 <= z <= 16)]
    hw = [5 - (1 if rng.random() < 0.3 else 0) for _ in wall]
    _curtain(b, rng, wall, hw, lambda x, z: x == KX0)
    for x in (KX0, KX0 + 1):                            # gate arch springers either side of the gap
        b.set(x, 5, 14, stairs("stone_brick", "south", "top"))
        b.set(x, 5, 17, stairs("stone_brick", "north", "top"))
    west = [(x, z) for x in range(1, KX0) for z in (KZ1 - 2, KZ1 - 1)]
    hn = [max(0, 4 - (KX0 - 1 - x) // 2 - (1 if rng.random() < 0.3 else 0)) for (x, z) in west]
    _curtain(b, rng, west, hn, lambda x, z: z == KZ1 - 2)
    _well(b, rng, 13, 15)
    trail(b, [(0, 16), (KX0 + 1, 16), (KX0 + 4, KZ1 + 1)], rng, PATH, width=2, fade=0.8)
    vines_on(b, rng, 0.06, ymin=3, max_len=4)
    for (x, y, z), (st, _) in list(b.cells.items()):
        if st.short in ("cobblestone", "stone", "andesite", "stone_bricks") and b.inb(x, y + 1, z) and \
                b.get(x, y + 1, z) is None and y >= 3 and rng.random() < 0.25:
            b.set(x, y + 1, z, B("moss_carpet"))
    clear(b, KX0 + 2, 1, KZ1 + 2, 21, 3, 19)
    flora(b, [(x, z) for x in range(22) for z in range(20)], rng, 0.2, GROUND)
    flora(b, [(x, z) for x in range(KX0 + 1, KX1) for z in range(KZ0 + 1, KZ1)], rng, 0.25, ["coarse_dirt"],
          plants=("short_grass", "fern"))
    return b


def pools():
    return {"start": [Piece(corner(), 3, PROCS), Piece(face(), 2, PROCS)]}


DATA = data_for(NAME, [PROCS])
