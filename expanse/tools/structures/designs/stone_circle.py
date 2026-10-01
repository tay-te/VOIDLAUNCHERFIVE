"""Stone circle of the heather moor.

start pool - three ring layouts, each with a landmark silhouette:
  henge   : ditch-and-bank henge, two trilithons, a leaning 11-high king stone
  barrow  : smaller ring around a grassy barrow mound with a dolmen passage
  avenue  : broken ring, fallen stones, a great 8-high trilithon and a stone avenue
crypt pool - reached through a gravel-covered hatch by the altar (down jigsaw):
  hall    : vaulted burial hall, skeleton spawner, sealed sarcophagus (hidden chest), lectern
  ossuary : cross-shaped ossuary, zombie spawner, chest behind a loose bone wall
Heather/edelweiss come from short_grass markers via the processor list, and up
to three gravel cells per ring become suspicious gravel (archaeology).
"""
import math

from blocks import AIR, B
from builder import Build, item, slab, stairs, written_book
from common import circle_cells
from lore import BOOKS
from pieces import Piece

NAME = "stone_circle"
STRUCTURE = dict(biomes=["heather_moor"], spacing=28, separation=10, size=1, max_distance=40)
LOOT = "expanse:chests/stone_circle"
HIDDEN = "expanse:chests/stone_circle_hidden"
CRYPT_POOL = "expanse:stone_circle/crypt"
STONE = ["stone"] * 5 + ["andesite"] * 3 + ["mossy_cobblestone"] * 2 + ["cobblestone", "tuff"]
SLAB_OF = {"stone": "stone", "andesite": "andesite", "mossy_cobblestone": "mossy_cobblestone",
           "cobblestone": "cobblestone", "tuff": "tuff"}


def lore_book():
    b = BOOKS["stone_circle"]
    return written_book(b["title"], b["author"], b["pages"])


def menhir(b, x, z, h, rng, width=1, axis="x", lean=None, cap=True):
    """A standing stone from y=0 (bedded) to y=h; optionally 2 wide and leaning toward `lean`."""
    cells = [(x, z)] + ([(x + 1, z)] if width == 2 and axis == "x" else [(x, z + 1)] if width == 2 else [])
    for i, (sx, sz) in enumerate(cells):
        hh = h - (1 if i and rng.random() < 0.5 else 0)
        for y in range(0, hh + 1):
            ox = oz = 0
            if lean and y > hh * 0.6:
                ox, oz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[lean]
            b.set(sx + ox, y, sz + oz, B(rng.choice(STONE)))
        top = b.get(sx + (ox if lean else 0), hh, sz + (oz if lean else 0))
        tx, tz = sx + (ox if lean else 0), sz + (oz if lean else 0)
        r = rng.random()
        if cap and r < 0.4:
            b.set(tx, hh + 1, tz, slab(SLAB_OF[top.short]))
        elif cap and r < 0.55:
            b.set(tx, hh + 1, tz, stairs(SLAB_OF[top.short] if top.short != "tuff" else "tuff",
                                          rng.choice(["north", "south", "east", "west"])))
        elif r < 0.85:
            b.set(tx, hh + 1, tz, B("moss_carpet"))
    # a little lichen on the shaded (south) face
    ly = rng.randint(1, max(1, h - 1))
    if rng.random() < 0.6 and b.get(x, ly, z + 1) is None:
        b.set(x, ly, z + 1, B("glow_lichen", north=True))


def trilithon(b, x, z, h, rng, axis, broken=False):
    """Two uprights 2 apart with a lintel; the gate between them is kept open."""
    off = [(-1, 0), (1, 0)] if axis == "x" else [(0, -1), (0, 1)]
    for dx, dz in off:
        for y in range(0, h + 1):
            b.set(x + dx, y, z + dz, B(rng.choice(["stone", "stone", "andesite", "mossy_cobblestone"])))
    span = [(x - 1, z), (x, z), (x + 1, z)] if axis == "x" else [(x, z - 1), (x, z), (x, z + 1)]
    for i, (lx, lz) in enumerate(span):
        if broken and i == 2:
            continue
        b.set(lx, h + 1, lz, B(rng.choice(["andesite", "stone", "polished_andesite"])))
    if broken:
        fx, fz = span[2]
        b.set(fx + (1 if axis == "x" else 0), 1, fz + (1 if axis == "z" else 0), B("andesite"))
        b.set(fx + (2 if axis == "x" else 0), 1, fz + (2 if axis == "z" else 0), slab("andesite"))
    for (lx, lz) in span[::2]:
        if rng.random() < 0.6 and b.get(lx, h + 1, lz) is not None:
            b.set(lx, h + 2, lz, B("moss_carpet"))
    for y in range(1, h + 1):
        b.set(x, y, z, AIR)


def fallen_stone(b, x, z, rng, axis, length=3):
    cells = [(x + i, z) if axis == "x" else (x, z + i) for i in range(length)]
    for i, (fx, fz) in enumerate(cells):
        b.set(fx, 0, fz, B(rng.choice(["stone", "andesite"])))
        b.set(fx, 1, fz, B(rng.choice(["mossy_cobblestone", "stone", "andesite"])) if i < length - 1
              else slab("mossy_cobblestone"))
        if rng.random() < 0.5:
            b.set(fx, 2, fz, B("moss_carpet"))


def king_stone(b, x, z, rng, h=11, lean="east"):
    """A tall 2x2-based landmark menhir that narrows and leans near the top."""
    for y in range(0, h + 1):
        if y < 4:
            cells = [(x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)]
        elif y < 8:
            cells = [(x, z), (x + 1, z), (x, z + 1)]
        else:
            ox = 1 if lean == "east" and y >= 9 else 0
            cells = [(x + ox, z)]
        for (cx, cz) in cells:
            b.set(cx, y, cz, B(rng.choice(["stone", "stone", "andesite", "tuff", "mossy_cobblestone"])))
    b.set(x + (1 if lean == "east" else 0), h + 1, z, slab("andesite"))
    b.set(x + 1, 4, z + 1, B("moss_carpet"))
    for y in (2, 3, 5):
        if rng.random() < 0.7:
            b.set(x - 1, y, z, B("glow_lichen", east=True))


def heather_markers(b, cells, rng, density):
    """short_grass markers (turned into heather/edelweiss/fern per instance by the processor list)."""
    for (x, z) in cells:
        if b.get(x, 0, z) is None and b.get(x, 1, z) in (None, AIR) and rng.random() < density:
            b.set(x, 0, z, B("grass_block"))
            b.set(x, 1, z, B("short_grass"))


def altar_and_hatch(b, cx, cz, rng, style):
    """Recumbent altar stone north of the centre; the crypt hatch (down jigsaw) at the centre."""
    if style == "table":
        for x in (cx - 1, cx + 1):
            b.set(x, 1, cz - 1, B("polished_andesite"))
        for x in range(cx - 1, cx + 2):
            b.set(x, 2, cz - 1, slab("andesite"))
        b.chest(cx, 1, cz - 1, "south", LOOT)
        b.set(cx - 1, 3, cz - 1, B("white_candle", candles=3, lit=True))
        b.set(cx + 1, 3, cz - 1, B("candle", candles=2, lit=True))
    else:
        b.set(cx, 1, cz - 1, B("chiseled_stone_bricks"))
        b.set(cx - 1, 1, cz - 1, stairs("stone_brick", "east"))
        b.set(cx + 1, 1, cz - 1, stairs("stone_brick", "west"))
        b.chest(cx, 2, cz - 1, "south", LOOT)
        b.set(cx - 1, 2, cz - 1, B("white_candle", candles=2, lit=True))
        b.pot(cx + 1, 2, cz - 1, "south", ("brick", "mourner_pottery_sherd", "brick", "brick"))
    # paving and the hatch
    for (x, z) in circle_cells(cx, cz, 2.3):
        if b.get(x, 0, z) is None:
            b.set(x, 0, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "cobblestone",
                                         "mossy_cobblestone", "gravel"])))
    b.jigsaw(cx, 0, cz, "down", name="minecraft:empty", target="expanse:crypt_top", pool=CRYPT_POOL,
             final="minecraft:gravel", top="north")


def entrance(b, cx, z_from, z_to, rng, sign_at=None):
    for z in range(z_from, z_to):
        for x in (cx - 1, cx, cx + 1):
            if b.get(x, 0, z) is None and rng.random() < (0.9 if x == cx else 0.4):
                b.set(x, 0, z, B(rng.choice(["dirt_path", "dirt_path", "coarse_dirt", "gravel"])))
    if sign_at:
        sx, sz = sign_at
        b.set(sx, 1, sz, B("spruce_fence"))
        b.sign(sx, 2, sz, "spruce", ["Here sleep", "the old kings.", "Tread softly,", "bring flowers."],
               rotation=0)


def ring_henge():
    b = Build(f"{NAME}_henge", 25, 14, 25, seed=1847261)
    rng = b.rng
    cx = cz = 12
    # ditch (carved) and bank
    for (x, z) in circle_cells(cx, cz, 11.4):
        d = math.hypot(x - cx, z - cz)
        if abs(z - cz) <= 1 and z > cz and x - cx == 0:
            continue
        gate = abs(x - cx) <= 1 and z > cz
        if 9.0 <= d < 10.0 and not gate:
            b.set(x, 0, z, AIR)
            b.set(x, 1, z, AIR)
        elif 10.0 <= d <= 11.4 and not gate and rng.random() < 0.85:
            b.set(x, 0, z, B("dirt"))
            b.set(x, 1, z, B("grass_block"))
            if rng.random() < 0.3:
                b.set(x, 2, z, B("short_grass"))
    for (x, z) in circle_cells(cx, cz, 8.6):
        b.air(x, 1, z, x, 3, z, only_void=True)
    n, radius = 10, 7.4
    for i in range(n):
        th = math.pi / n + 2 * math.pi * i / n + rng.uniform(-0.07, 0.07)
        x = int(round(cx + radius * math.sin(th)))
        z = int(round(cz - radius * math.cos(th)))
        axis = "x" if abs(math.sin(th)) < abs(math.cos(th)) else "z"
        if i in (0, 3):
            trilithon(b, x, z, 6, rng, axis)
        elif i == 7:
            fallen_stone(b, x, z, rng, "z" if axis == "x" else "x")
        else:
            menhir(b, x, z, rng.choice([3, 4, 4, 5, 6]), rng, width=2 if rng.random() < 0.2 else 1, axis=axis)
    king_stone(b, 19, 3, rng, h=11)
    altar_and_hatch(b, cx, cz, rng, "table")
    entrance(b, cx, cz + 3, 25, rng, sign_at=(cx + 2, 21))
    heather_markers(b, [(x, z) for x in range(25) for z in range(25)
                        if 2.6 < math.hypot(x - cx, z - cz) < 12.4], rng, 0.32)
    for (x, z) in ((cx - 4, cz + 4), (cx + 5, cz - 3), (cx - 6, cz - 1)):
        b.set(x, 0, z, B("gravel"))
    return b


def ring_barrow():
    b = Build(f"{NAME}_barrow", 23, 14, 23, seed=1847263)
    rng = b.rng
    cx = cz = 11
    # barrow mound (grassy dome) with a dolmen passage from the south to the hatch
    for (x, z) in circle_cells(cx, cz, 5.2):
        d = math.hypot(x - cx, z - cz)
        h = 4 if d < 2.2 else 3 if d < 3.6 else 2 if d < 4.6 else 1
        for y in range(0, h):
            b.set(x, y, z, B("dirt") if y < h - 1 else B("grass_block"))
        if rng.random() < 0.4:
            b.set(x, h, z, B("short_grass"))
    # passage: walls of stone, capstones, open from z=cz+5 to the centre
    for z in range(cz, cz + 6):
        b.set(cx, 1, z, AIR)
        b.set(cx, 2, z, AIR)
        for x in (cx - 1, cx + 1):
            b.set(x, 1, z, B(rng.choice(["mossy_stone_bricks", "stone", "andesite"])))
            b.set(x, 2, z, B(rng.choice(["mossy_stone_bricks", "stone", "andesite"])))
        b.set(cx, 3, z, B(rng.choice(["andesite", "polished_andesite", "stone"])))
    for x in (cx - 1, cx + 1):           # dolmen portal stones stand proud of the mound
        for y in range(0, 5):
            b.set(x, y, cz + 6, B(rng.choice(["stone", "andesite"])))
        b.set(x, 5, cz + 6, B("moss_carpet"))
    for x in range(cx - 2, cx + 3):
        b.set(x, 4, cz + 6, B("andesite") if abs(x - cx) < 2 else slab("andesite"))
    b.set(cx, 1, cz + 6, AIR)
    b.set(cx, 2, cz + 6, AIR)
    b.set(cx, 3, cz + 6, AIR)
    b.set(cx, 0, cz + 6, B("gravel"))
    # inner chamber of the barrow
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            b.set(x, 1, z, AIR)
            b.set(x, 2, z, AIR)
            b.set(x, 3, z, B("andesite"))
    b.chest(cx, 1, cz - 1, "south", LOOT)
    b.set(cx - 1, 1, cz - 1, B("skeleton_skull", rotation=8))
    b.set(cx + 1, 1, cz - 1, B("candle", candles=3, lit=True))
    b.pot(cx - 1, 1, cz + 1, "east", ("brick", "skull_pottery_sherd", "brick", "brick"))
    b.jigsaw(cx, 0, cz, "down", name="minecraft:empty", target="expanse:crypt_top", pool=CRYPT_POOL,
             final="minecraft:gravel", top="north")
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if (x, z) != (cx, cz):
                b.set(x, 0, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "gravel"])))
    # the ring around the mound
    n, radius = 9, 8.3
    for i in range(n):
        th = 2 * math.pi * i / n + 0.2 + rng.uniform(-0.06, 0.06)
        x = int(round(cx + radius * math.sin(th)))
        z = int(round(cz - radius * math.cos(th)))
        if abs(x - cx) <= 1 and z > cz:
            continue
        axis = "x" if abs(math.sin(th)) < abs(math.cos(th)) else "z"
        if i == 4:
            fallen_stone(b, x, z, rng, axis)
        else:
            menhir(b, x, z, rng.choice([3, 4, 5, 5]), rng, axis=axis, lean="west" if i == 6 else None)
    king_stone(b, 2, 2, rng, h=9, lean="east")
    entrance(b, cx, cz + 7, 23, rng, sign_at=(cx - 2, 19))
    heather_markers(b, [(x, z) for x in range(23) for z in range(23)
                        if 5.4 < math.hypot(x - cx, z - cz) < 11.2], rng, 0.35)
    for (x, z) in ((cx + 4, cz + 6), (cx - 6, cz + 3)):
        b.set(x, 0, z, B("gravel"))
    return b


def ring_avenue():
    b = Build(f"{NAME}_avenue", 23, 14, 31, seed=1847265)
    rng = b.rng
    cx, cz = 11, 11
    for (x, z) in circle_cells(cx, cz, 8.4):
        b.air(x, 1, z, x, 3, z, only_void=True)
    n, radius = 9, 7.3
    for i in range(n):
        th = 2 * math.pi * i / n + math.pi / n + rng.uniform(-0.05, 0.05)
        x = int(round(cx + radius * math.sin(th)))
        z = int(round(cz - radius * math.cos(th)))
        axis = "x" if abs(math.sin(th)) < abs(math.cos(th)) else "z"
        if i == 0:
            continue                                   # replaced by the great trilithon
        if i in (3, 6):
            fallen_stone(b, x, z, rng, "z" if axis == "x" else "x")
        elif i == 4:
            trilithon(b, x, z, 5, rng, axis, broken=True)
        else:
            menhir(b, x, z, rng.choice([3, 4, 5, 6]), rng, axis=axis)
    # great trilithon on the north
    for dx in (-2, -1, 1, 2):
        for y in range(0, 8):
            b.set(cx + dx, y, cz - 8, B(rng.choice(["stone", "andesite", "tuff", "stone"])))
    for dx in range(-2, 3):
        b.set(cx + dx, 8, cz - 8, B(rng.choice(["andesite", "polished_andesite"])))
    for dx in (-3, 3):
        b.set(cx + dx, 8, cz - 8, slab("andesite", "top"))
    b.set(cx - 1, 9, cz - 8, B("moss_carpet"))
    for y in range(1, 8):
        b.set(cx, y, cz - 8, AIR)
    altar_and_hatch(b, cx, cz, rng, "steps")
    # avenue of paired stones leading south out of the ring
    for k, z in enumerate(range(cz + 9, 31, 4)):
        for side in (-3, 3):
            if rng.random() < 0.85:
                menhir(b, cx + side + rng.choice([-1, 0, 0]), z, rng.choice([2, 3, 3, 4]), rng, cap=False)
    entrance(b, cx, cz + 3, 31, rng, sign_at=(cx + 2, 28))
    heather_markers(b, [(x, z) for x in range(23) for z in range(31)
                        if math.hypot(x - cx, z - cz) > 2.6], rng, 0.3)
    for (x, z) in ((cx + 3, cz + 5), (cx - 5, cz - 2), (cx + 1, cz + 12)):
        b.set(x, 0, z, B("gravel"))
    return b


# ------------------------------------------------------------------ crypts
def earth_block(b, x0, x1, z0, z1, y0, y1):
    """Solid natural fill so the crypt is a sealed pocket inside the hill."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                b.set(x, y, z, B(b.rng.choice(["stone", "stone", "dirt", "andesite", "tuff"])))


def crypt_shaft(b, x, z, top, bottom):
    """1x1 ladder shaft (ladder against its north wall) from the jigsaw cell at `top` down to `bottom`."""
    for y in range(bottom, top + 1):
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if y > bottom + 2 or (dx, dz) != (0, 1):
                b.set(x + dx, y, z + dz, B(b.rng.choice(["stone_bricks", "mossy_stone_bricks", "cobblestone"])))
        b.set(x, y, z, B("ladder", facing="south"))
    b.jigsaw(x, top, z, "up", name="expanse:crypt_top", target="minecraft:empty", pool="minecraft:empty",
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north")


def crypt_hall():
    b = Build(f"{NAME}_crypt_hall", 13, 13, 16, seed=1847267)
    rng = b.rng
    earth_block(b, 0, 12, 0, 15, 0, 7)
    # barrel-vaulted hall: x2..10, z4..14, floor y0, walls to y3, vault y4-5
    for x in range(2, 11):
        for z in range(4, 15):
            b.set(x, 0, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"])))
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
            if 3 <= x <= 9:
                b.set(x, 4, z, AIR)
            if 5 <= x <= 7:
                b.set(x, 5, z, AIR)
        b.set(2, 3, z, stairs("stone_brick", "west", "top"))
        b.set(10, 3, z, stairs("stone_brick", "east", "top"))
        b.set(3, 4, z, stairs("stone_brick", "west", "top"))
        b.set(9, 4, z, stairs("stone_brick", "east", "top"))
        b.set(4, 5, z, stairs("stone_brick", "west", "top"))
        b.set(8, 5, z, stairs("stone_brick", "east", "top"))
        b.set(5, 6, z, B("stone_bricks"))
        b.set(6, 6, z, B("chiseled_stone_bricks") if z % 4 == 0 else B("stone_bricks"))
        b.set(7, 6, z, B("stone_bricks"))
    # pillars and wall niches with skulls / bones
    for z in (6, 9, 12):
        for x in (3, 9):
            b.column(x, z, 1, 3, B("chiseled_stone_bricks") if z == 9 else B("stone_bricks"))
    for z in (5, 8, 11, 14):
        for x, rot_ in ((1, 4), (11, 12)):
            b.set(x, 2, z, B("skeleton_skull", rotation=rot_) if rng.random() < 0.6 else B("bone_block", axis="z"))
    # entrance: the ladder shaft north of the hall, a short passage south into it
    crypt_shaft(b, 6, 2, 12, 1)
    b.set(6, 0, 2, B("stone_bricks"))
    b.air(6, 1, 3, 6, 2, 3)
    b.set(6, 3, 3, B("stone_bricks"))
    b.set(6, 0, 3, B("mossy_stone_bricks"))
    # lore lectern by the stair foot (the crypt stays unlit so its spawner works)
    b.lectern(4, 1, 4, "south", lore_book())
    # spawner on a pedestal in the middle
    b.set(6, 1, 9, B("chiseled_stone_bricks"))
    b.spawner(6, 2, 9, "skeleton", min_delay=300, max_delay=900, count=3, max_nearby=5)
    # sealed sarcophagus with the hidden chest under its lid, and a broken one
    for x in (5, 6, 7):
        b.set(x, 1, 14, B("polished_andesite"))
        b.set(x, 2, 14, slab("stone_brick"))
    b.set(4, 1, 14, B("stone_bricks"))
    b.set(8, 1, 14, B("stone_bricks"))
    b.chest(6, 1, 14, "north", HIDDEN)
    b.set(6, 1, 13, B("polished_andesite"))
    b.set(5, 1, 13, stairs("stone_brick", "south"))
    b.set(7, 1, 13, stairs("stone_brick", "south"))
    b.set(9, 1, 6, B("polished_andesite"))
    b.set(9, 1, 7, B("polished_andesite"))
    b.set(9, 2, 7, slab("stone_brick"))
    b.set(8, 1, 6, B("bone_block", axis="x"))
    b.set(8, 1, 5, B("skeleton_skull", rotation=3))
    b.pot(3, 1, 5, "south", ("brick", "skull_pottery_sherd", "brick", "mourner_pottery_sherd"))
    b.pot(9, 1, 11, "west", ("brick", "heart_pottery_sherd", "brick", "brick"))
    for (x, y, z) in ((2, 3, 4), (10, 3, 14), (4, 3, 10), (8, 3, 5), (3, 3, 14)):
        b.set(x, y, z, B("cobweb"))
    b.set(2, 1, 10, B("candle", candles=1, lit=False))
    return b


def crypt_ossuary():
    b = Build(f"{NAME}_crypt_ossuary", 13, 12, 14, seed=1847269)
    rng = b.rng
    earth_block(b, 0, 12, 0, 13, 0, 4)
    # cross-shaped crypt: arms 3 wide, y1..3
    arms = {(x, z) for x in range(5, 8) for z in range(3, 13)} | {(x, z) for x in range(1, 12) for z in range(6, 9)}
    for (x, z) in sorted(arms):
        b.set(x, 0, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cobblestone"])))
        for y in (1, 2, 3):
            b.set(x, y, z, AIR)
    for (x, z) in sorted(arms):                       # ossuary walls: bones and skulls
        for dx, dz, ax in ((-1, 0, "x"), (1, 0, "x"), (0, -1, "z"), (0, 1, "z")):
            q = (x + dx, z + dz)
            if q in arms or not (0 <= q[0] <= 12 and 0 <= q[1] <= 13):
                continue
            for y in (1, 2, 3):
                r = rng.random()
                if r < 0.35:
                    b.set(q[0], y, q[1], B("bone_block", axis="y" if y != 2 else ax))
                elif r < 0.5:
                    b.set(q[0], y, q[1], B("skeleton_skull", rotation=rng.randrange(16)))
    crypt_shaft(b, 6, 1, 11, 1)
    b.set(6, 0, 1, B("stone_bricks"))
    b.air(6, 1, 2, 6, 2, 2)
    b.set(6, 0, 2, B("cobblestone"))
    b.lectern(5, 1, 3, "east", lore_book())
    b.set(6, 0, 7, B("chiseled_stone_bricks"))
    b.spawner(6, 1, 7, "zombie", min_delay=300, max_delay=900, count=3, max_nearby=5)
    # the hidden chest sits behind a loose wall of bone at the end of the east arm
    for y in (1, 2, 3):
        b.set(11, y, 7, B("bone_block", axis="x"))
    b.air(12, 1, 7, 12, 2, 7)
    b.chest(12, 1, 7, "west", HIDDEN)
    b.pot(1, 1, 6, "east", ("brick", "skull_pottery_sherd", "brick", "brick"))
    b.pot(6, 1, 12, "north", ("brick", "mourner_pottery_sherd", "brick", "brick"))
    b.barrel(1, 1, 8, "east")
    for (x, y, z) in ((5, 3, 12), (11, 3, 8), (1, 3, 7), (7, 3, 4)):
        b.set(x, y, z, B("cobweb"))
    return b


def pools():
    return {
        "start": [Piece(ring_henge(), 2, "stone_circle_ring"), Piece(ring_barrow(), 2, "stone_circle_ring"),
                  Piece(ring_avenue(), 2, "stone_circle_ring")],
        "crypt": [Piece(crypt_hall(), 1, "stone_circle_crypt"), Piece(crypt_ossuary(), 1, "stone_circle_crypt")],
    }
