"""Stone circle of the heather moor.

start pool - three ring layouts, each bold enough to read across the moor haze:
  every ring is 24-26 across, made of sarsens 2x1 / 2x2 in section and 5-9 tall,
  with trilithons carrying real lintels, a trampled ring path (coarse dirt, gravel,
  dirt path) and a paved centre with a recumbent altar stone, and a 2x2 / 3x2 king
  stone 11-13 tall
  henge   : ditch-and-bank henge, three trilithons, an inner horseshoe of bluestones,
            the leaning king (heel) stone in the entrance gap
  barrow  : ring round a grassy barrow mound with a dolmen passage, king stone outside
  avenue  : broken ring with a great 2x2-legged trilithon and a sarsen avenue leading
            to the king stone
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
STRUCTURE = dict(biomes=["heather_moor"], spacing=28, separation=10, size=1, max_distance=40,
                 exclusion=("ruined_watchtower", 4),  # both stand on the moors
                 max_relief=8)                         # settle on gentle ground, not a crag
LOOT = "expanse:chests/stone_circle"
HIDDEN = "expanse:chests/stone_circle_hidden"
CRYPT_POOL = "expanse:stone_circle/crypt"


def lore_book():
    b = BOOKS["stone_circle"]
    return written_book(b["title"], b["author"], b["pages"])


SARSEN = ["stone"] * 6 + ["andesite"] * 4 + ["tuff"] * 2 + ["polished_andesite"]
LINTEL = ["polished_andesite", "polished_andesite", "andesite", "smooth_stone"]
FOOT = ["mossy_cobblestone", "stone", "andesite", "mossy_cobblestone"]


def _cells(x, z, tangent, width, depth):
    """Footprint of a stone whose long side runs along `tangent` (x or z), centred on (x, z)."""
    o = -(width // 2)
    if tangent == "x":
        return [(x + o + i, z + j) for i in range(width) for j in range(depth)]
    return [(x + j, z + o + i) for i in range(width) for j in range(depth)]


def sarsen(b, x, z, h, rng, tangent="x", width=2, depth=1, lean=None):
    """A dressed standing stone, `width` x `depth` in section, bedded in layer 0 and rising to y=h.
    The foot is mossy, one top corner is knocked off and the crown gets a little moss."""
    cells = _cells(x, z, tangent, width, depth)
    chipped = rng.choice(cells)
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.get(lean, (0, 0))
    for (sx, sz) in cells:
        hh = h - (1 if (sx, sz) == chipped else 0)
        for y in range(0, hh + 1):
            off = 1 if lean and y >= h - 2 else 0
            st = B(rng.choice(FOOT)) if y <= 1 and rng.random() < 0.6 else B(rng.choice(SARSEN))
            b.set(sx + dx * off, y, sz + dz * off, st)
    for (sx, sz) in cells:                             # a little moss on the crown
        tx, tz = sx + dx, sz + dz
        ys = [y for y in range(h + 1) if b.get(tx, y, tz) not in (None, AIR)]
        if ys and rng.random() < 0.35 and b.get(tx, ys[-1] + 1, tz) in (None, AIR):
            b.set(tx, ys[-1] + 1, tz, B("moss_carpet"))
    face = rng.choice(cells)
    ly = rng.randint(2, max(2, h - 2))
    if b.get(face[0], ly, face[1] + 1) is None:
        b.set(face[0], ly, face[1] + 1, B("glow_lichen", north=True))
    return cells


def trilithon(b, x, z, h, rng, tangent, depth=1, leg=2, broken=False):
    """Two sarsen legs (`leg` wide, `depth` deep) with a one-block gate between them, capped by a lintel
    that runs the full width at y=h+1. A broken one has lost half its lintel to the grass."""
    span = 2 * leg + 1
    o = -(span // 2)
    for i in range(span):
        if i == leg:
            continue
        for j in range(depth):
            cx, cz = (x + o + i, z + j) if tangent == "x" else (x + j, z + o + i)
            for y in range(0, h + 1):
                b.set(cx, y, cz, B(rng.choice(FOOT)) if y <= 1 and rng.random() < 0.5 else B(rng.choice(SARSEN)))
    for i in range(span):
        if broken and i > leg:
            continue
        for j in range(depth):
            cx, cz = (x + o + i, z + j) if tangent == "x" else (x + j, z + o + i)
            b.set(cx, h + 1, cz, B(rng.choice(LINTEL)))
            if rng.random() < 0.3:
                b.set(cx, h + 2, cz, B("moss_carpet"))
    gx, gz = (x + o + leg, z) if tangent == "x" else (x, z + o + leg)
    for j in range(depth):
        for y in range(1, h + 1):
            b.set(gx + (0 if tangent == "x" else j), y, gz + (j if tangent == "x" else 0), AIR)
    if broken:                                          # the fallen half of the lintel lies at its foot
        for i in range(3):
            if tangent == "x":
                fx, fz = x + o + span - 1 + i, z + depth + 1
            else:
                fx, fz = x + depth + 1, z + o + span - 1 + i
            b.set(fx, 0, fz, B("andesite"))
            b.set(fx, 1, fz, B(rng.choice(LINTEL)) if i < 2 else slab("andesite"))


def recumbent(b, x, z, rng, tangent, length=4):
    """A fallen sarsen lying in the grass, two high, half sunk."""
    for i in range(length):
        for j in range(2):
            cx, cz = (x + i, z + j) if tangent == "x" else (x + j, z + i)
            b.set(cx, 0, cz, B(rng.choice(SARSEN)))
            b.set(cx, 1, cz, B(rng.choice(SARSEN)) if i < length - 1 or j == 0 else slab("andesite"))
            if rng.random() < 0.4:
                b.set(cx, 2, cz, B("moss_carpet"))


def king_stone(b, x, z, rng, h=12, w=2, d=2, lean="east"):
    """The landmark stone: w x d at the foot, losing a column above 2/3 height and leaning at the top."""
    dx = 1 if lean == "east" else -1 if lean == "west" else 0
    dz = 1 if lean == "south" else -1 if lean == "north" else 0
    for y in range(0, h + 1):
        if y < h * 0.55:
            cells = [(x + i, z + j) for i in range(w) for j in range(d)]
        elif y < h * 0.8:
            cells = [(x + i, z + j) for i in range(w) for j in range(d) if (i, j) != (w - 1, d - 1)]
        else:
            cells = [(x + i + dx, z + j + dz) for i in range(max(1, w - 1)) for j in range(max(1, d - 1))]
        for (cx, cz) in cells:
            b.set(cx, y, cz, B(rng.choice(FOOT)) if y <= 1 else B(rng.choice(SARSEN)))
    b.set(x + dx, h + 1, z + dz, slab("andesite"))
    for y in (2, 4, 6):
        if b.get(x - 1, y, z) is None:
            b.set(x - 1, y, z, B("glow_lichen", east=True))


def ring_positions(cx, cz, r, n, phase, rng, jitter=0.03):
    """(x, z, tangent) for n stones round a ring of radius r; angle 0 is north, the gate is south."""
    out = []
    for i in range(n):
        th = phase + 2 * math.pi * i / n + rng.uniform(-jitter, jitter)
        x = int(round(cx + r * math.sin(th)))
        z = int(round(cz - r * math.cos(th)))
        tangent = "z" if abs(math.sin(th)) > abs(math.cos(th)) else "x"
        out.append((x, z, tangent, th))
    return out


def ring_path(b, cx, cz, r0, r1, rng, density=0.8):
    """The trampled path round the inside of the stones (so the circle reads from above)."""
    for (x, z) in circle_cells(cx, cz, r1):
        d = math.hypot(x - cx, z - cz)
        if r0 <= d <= r1 and b.get(x, 0, z) is None and rng.random() < density:
            b.set(x, 0, z, B(rng.choice(["coarse_dirt", "coarse_dirt", "dirt_path", "gravel", "dirt_path"])))


def clear_interior(b, cx, cz, r, h=4):
    for (x, z) in circle_cells(cx, cz, r):
        b.air(x, 1, z, x, h, z, only_void=True)


def heather_markers(b, cells, rng, density):
    """short_grass markers (turned into heather/edelweiss/fern per instance by the processor list)."""
    for (x, z) in cells:
        if b.get(x, 0, z) is None and b.get(x, 1, z) in (None, AIR) and rng.random() < density:
            b.set(x, 0, z, B("grass_block"))
            b.set(x, 1, z, B("short_grass"))


def altar_and_hatch(b, cx, cz, rng, style):
    """Recumbent altar stone north of the centre; the crypt hatch (down jigsaw) at the centre."""
    if style == "table":
        for x in range(cx - 2, cx + 3):                 # the recumbent altar stone behind the offering chest
            for z in (cz - 3, cz - 2):
                b.set(x, 1, z, B(rng.choice(SARSEN)))
            b.set(x, 2, cz - 3, B(rng.choice(SARSEN)) if abs(x - cx) < 2 else slab("andesite"))
        b.chest(cx, 1, cz - 1, "south", LOOT)
        b.set(cx - 1, 2, cz - 2, B("white_candle", candles=3, lit=True))
        b.set(cx + 1, 2, cz - 2, B("candle", candles=2, lit=True))
        b.set(cx, 3, cz - 3, B("moss_carpet"))
    else:
        for x in range(cx - 2, cx + 3):                 # recumbent stone on the north side of the centre
            b.set(x, 1, cz - 3, B(rng.choice(SARSEN)))
            b.set(x, 2, cz - 3, B(rng.choice(SARSEN)) if abs(x - cx) < 2 else slab("andesite"))
        b.set(cx, 1, cz - 1, B("chiseled_stone_bricks"))
        b.set(cx - 1, 1, cz - 1, stairs("stone_brick", "east"))
        b.set(cx + 1, 1, cz - 1, stairs("stone_brick", "west"))
        b.chest(cx, 2, cz - 1, "south", LOOT)
        b.set(cx - 1, 2, cz - 1, B("white_candle", candles=2, lit=True))
        b.pot(cx + 1, 2, cz - 1, "south", ("brick", "mourner_pottery_sherd", "brick", "brick"))
    # paving and the hatch
    for (x, z) in circle_cells(cx, cz, 3.4):
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
        b.sign(sx, 2, sz, "spruce", ["", "Here sleep", "the old kings", ""],
               rotation=0)


def ring_henge():
    b = Build(f"{NAME}_henge", 37, 16, 37, seed=1847261)
    rng = b.rng
    cx = cz = 18
    gate = lambda x, z: abs(x - cx) <= 2 and z > cz            # noqa: E731 - the south entrance gap
    # ditch (cut one deep) and bank (raised one) outside the stones
    for (x, z) in circle_cells(cx, cz, 17.6):
        d = math.hypot(x - cx, z - cz)
        if gate(x, z):
            continue
        if 15.0 <= d < 16.0:
            b.set(x, 0, z, AIR)
            b.set(x, 1, z, AIR)
        elif 16.0 <= d <= 17.6 and rng.random() < 0.9:
            b.set(x, 0, z, B("dirt"))
            b.set(x, 1, z, B("grass_block"))
            if rng.random() < 0.3:
                b.set(x, 2, z, B("short_grass"))
    clear_interior(b, cx, cz, 14.6, 4)
    # the sarsen ring: 14 places round r=13, a gap left for the entrance in the south
    for i, (x, z, tg, th) in enumerate(ring_positions(cx, cz, 13, 14, math.pi / 14, rng)):
        if i in (0, 4, 10):
            trilithon(b, x, z, rng.choice([7, 8]), rng, tg, depth=1, broken=(i == 10))
        elif i == 8:
            recumbent(b, x - (1 if tg == "x" else 0), z - (1 if tg == "z" else 0), rng, tg)
        elif i == 2:
            sarsen(b, x, z, 3, rng, tg, width=2, depth=1)       # a stump
        else:
            sarsen(b, x, z, rng.randint(5, 9), rng, tg, width=2, depth=2 if i in (1, 12) else 1,
                   lean="west" if i == 5 else None)
    # inner horseshoe of smaller bluestones open to the south
    for (x, z, tg, th) in ring_positions(cx, cz, 7.5, 9, math.pi / 9, rng):
        if math.cos(th) < -0.5:
            continue
        sarsen(b, x, z, rng.randint(3, 4), rng, tg, width=1, depth=1)
    ring_path(b, cx, cz, 10.4, 11.6, rng)
    altar_and_hatch(b, cx, cz, rng, "table")
    king_stone(b, cx + 3, cz + 15, rng, h=12, w=2, d=2, lean="north")   # the heel stone in the entrance
    entrance(b, cx, cz + 4, 37, rng, sign_at=(cx - 3, 33))
    heather_markers(b, [(x, z) for x in range(37) for z in range(37)
                        if 3.6 < math.hypot(x - cx, z - cz) < 18.4], rng, 0.3)
    for (x, z) in ((cx - 5, cz + 5), (cx + 6, cz - 4), (cx - 8, cz - 1)):
        b.set(x, 0, z, B("gravel"))
    return b


def ring_barrow():
    b = Build(f"{NAME}_barrow", 33, 15, 33, seed=1847263)
    rng = b.rng
    cx = cz = 16
    clear_interior(b, cx, cz, 13.6, 4)
    # barrow mound (grassy dome) with a dolmen passage from the south to the hatch
    for (x, z) in circle_cells(cx, cz, 5.2):
        d = math.hypot(x - cx, z - cz)
        h = 4 if d < 2.2 else 3 if d < 3.6 else 2 if d < 4.6 else 1
        for y in range(0, h):
            b.set(x, y, z, B("dirt") if y < h - 1 else B("grass_block"))
        if rng.random() < 0.4:
            b.set(x, h, z, B("short_grass"))
    for z in range(cz, cz + 6):
        b.set(cx, 1, z, AIR)
        b.set(cx, 2, z, AIR)
        for x in (cx - 1, cx + 1):
            b.set(x, 1, z, B(rng.choice(["mossy_stone_bricks", "stone", "andesite"])))
            b.set(x, 2, z, B(rng.choice(["mossy_stone_bricks", "stone", "andesite"])))
        b.set(cx, 3, z, B(rng.choice(["andesite", "polished_andesite", "stone"])))
    trilithon(b, cx, cz + 6, 4, rng, "x", depth=1, leg=1)        # the dolmen portal stands proud
    b.set(cx, 0, cz + 6, B("gravel"))
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
    # the sarsen ring round the mound: 12 places at r=12 (24 across)
    for i, (x, z, tg, th) in enumerate(ring_positions(cx, cz, 12, 12, math.pi / 12, rng)):
        if math.cos(th) < -0.97:
            continue                                    # the south gap toward the dolmen
        if i in (2, 9):
            trilithon(b, x, z, rng.choice([6, 7]), rng, tg)
        elif i == 4:
            recumbent(b, x, z, rng, tg, length=3)
        else:
            sarsen(b, x, z, rng.randint(5, 8), rng, tg, width=2, depth=1 if i % 3 else 2,
                   lean="east" if i == 7 else None)
    ring_path(b, cx, cz, 7.6, 8.8, rng)
    king_stone(b, cx + 11, cz - 13, rng, h=11, w=2, d=2, lean="west")
    entrance(b, cx, cz + 7, 33, rng, sign_at=(cx - 3, 29))
    heather_markers(b, [(x, z) for x in range(33) for z in range(33)
                        if 5.4 < math.hypot(x - cx, z - cz) < 16.2], rng, 0.32)
    for (x, z) in ((cx + 5, cz + 6), (cx - 7, cz + 3)):
        b.set(x, 0, z, B("gravel"))
    return b


def ring_avenue():
    b = Build(f"{NAME}_avenue", 33, 16, 47, seed=1847265)
    rng = b.rng
    cx, cz = 16, 16
    clear_interior(b, cx, cz, 13.6, 4)
    for i, (x, z, tg, th) in enumerate(ring_positions(cx, cz, 13, 12, math.pi / 12, rng)):
        if math.cos(th) > 0.9 or math.cos(th) < -0.97:
            continue                                    # north: the great trilithon; south: the avenue gap
        if i in (3, 7):
            recumbent(b, x, z, rng, "z" if tg == "x" else "x", length=4)
        elif i == 5:
            trilithon(b, x, z, 6, rng, tg, broken=True)
        elif i == 9:
            sarsen(b, x, z, 2, rng, tg)                 # a stump
        else:
            sarsen(b, x, z, rng.randint(5, 8), rng, tg, width=2, depth=1 if i % 2 else 2)
    # great trilithon on the north: 2x2 legs, 9 high, lintel across both
    trilithon(b, cx, cz - 13, 9, rng, "x", depth=2, leg=2)
    ring_path(b, cx, cz, 9.6, 10.8, rng)
    altar_and_hatch(b, cx, cz, rng, "steps")
    # an avenue of paired sarsens leading south to the king stone
    for k, z in enumerate(range(cz + 16, 45, 6)):
        for side in (-4, 4):
            if rng.random() < 0.9:
                sarsen(b, cx + side, z, rng.randint(4, 6), rng, "z", width=2, depth=1)
    king_stone(b, cx + 6, 42, rng, h=13, w=3, d=2, lean="west")
    entrance(b, cx, cz + 4, 47, rng, sign_at=(cx + 2, 30))
    heather_markers(b, [(x, z) for x in range(33) for z in range(47)
                        if math.hypot(x - cx, z - cz) > 3.6], rng, 0.3)
    for (x, z) in ((cx + 4, cz + 6), (cx - 6, cz - 3), (cx + 1, cz + 17)):
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
