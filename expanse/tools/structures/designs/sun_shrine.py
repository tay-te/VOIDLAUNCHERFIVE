"""Temple of the Pink Sun - a stepped sun temple half swallowed by the opal dunes (LANDMARK).

start  ziggurat : five opal-sandstone tiers (37x37 base) with orange and blue bands,
                  a twenty-step grand stair with brazier landings, and on top the sun
                  sanctum - portico, sun mosaic, lore lectern, offering pots - crowned by
                  a great standing sun disc (~31 high). Dunes bury the west and back faces.
                  The faithful pull the lever by the altar: the iron trapdoor in the
                  mosaic lifts and a shaft drops into the gallery inside the base
                  (sarcophagi, murals, brushable sand). A west doorway into the gallery is
                  choked with drift sand. From the gallery a stair leads down to:
undercroft (below ground, via a down jigsaw): the Hall of the Sun - pillars, a husk
                  spawner in the dark, sarcophagi, suspicious sand, and the sealed treasury
                  behind a cracked wall (hidden chest).
avenue (rigid, at the stair foot): processional way with sphinxes and two obelisks
paths -> grounds (terrain-matching): palm oasis, buried colonnade, priests' quarters
                  ruin, fallen obelisk plaza. Vanilla-sand patches in the ruins hide up to
                  four suspicious-sand finds per piece (processor, archaeology loot).
"""
import math
import random

from arch import ring, rock, straight_stair, tree, wide_stair
from blocks import AIR, B
from builder import Build, item, slab, stairs, written_book
from furnish import chain_lamp
from lore import BOOKS
from pieces import Empty, Piece
from terrain_paths import connector, path_piece, piece_in

NAME = "sun_shrine"
STRUCTURE = dict(biomes=["opal_dunes"], spacing=54, separation=20, size=4, max_distance=76)
LOOT = "expanse:chests/sun_shrine"
HIDDEN = "expanse:chests/sun_shrine_hidden"
ARCH = "expanse:chests/sun_shrine_archaeology"
P_UNDER = f"expanse:{NAME}/undercroft"
P_AVENUE = f"expanse:{NAME}/avenue"
P_PATHS = f"expanse:{NAME}/paths"
P_GROUNDS = f"expanse:{NAME}/grounds"
J_UNDER = "expanse:sun_undercroft"
J_AVENUE = "expanse:sun_avenue"
PROC = "sun_shrine"

OS = "expanse:opal_sandstone"          # stairs / slab / wall
SOS = "expanse:smooth_opal_sandstone"
COS = "expanse:cut_opal_sandstone"
CHOS = "expanse:chiseled_opal_sandstone"
SAND = "expanse:opal_sand"
BAND_A = "orange_terracotta"
BAND_B = "light_blue_terracotta"


def book():
    d = BOOKS[NAME]
    return written_book(d["title"], d["author"], d["pages"])


def body(rng):
    return B(rng.choice([OS, OS, SOS, SOS, COS]))


def dune_fill(b, rng, height_fn, y0=1, ymax=None, region=None):
    """Pile opal sand on empty cells up to height_fn(x, z) (bottom-up, so every grain is supported)."""
    sx, sy, sz = b.size
    ymax = ymax or sy - 1
    for x in range(sx):
        for z in range(sz):
            if region is not None and not region(x, z):
                continue
            h = min(ymax, int(height_fn(x, z)))
            for y in range(y0, h + 1):
                if b.get(x, y, z) is None:
                    if y > y0 and b.get(x, y - 1, z) is None:
                        break
                    b.set(x, y, z, B(SAND))
                elif b.get(x, y, z) == AIR:
                    break
            top = h + 1
            if 0 < top < sy and b.get(x, top, z) is None and b.get(x, top - 1, z) is not None and \
                    b.get(x, top - 1, z).id == SAND and rng.random() < 0.05:
                b.set(x, top, z, B(rng.choice(["dead_bush", "short_dry_grass"])))


def sphinx(b, x, y, z, facing, rng):
    """Couchant guardian (2 wide, 4 long) facing `facing` (north or south)."""
    s = -1 if facing == "north" else 1
    for dz in range(4):
        for dx in (0, 1):
            b.set(x + dx, y, z + s * dz, B(SOS))
            if dz >= 2:
                b.set(x + dx, y + 1, z + s * dz, B(COS) if dz == 3 else slab(OS))
    for dx in (0, 1):
        b.set(x + dx, y + 2, z + s * 3, stairs(OS, "south" if facing == "north" else "north"))
        b.set(x + dx, y + 1, z + s * 4, stairs(OS, facing))
    b.set(x, y + 3, z + s * 3, B(BAND_B))


def obelisk(b, x, y, z, h, rng, broken=False):
    for k in range(h):
        b.set(x, y + k, z, B(CHOS) if k in (0, h - 1) else B(SOS))
    if not broken:
        b.set(x, y + h, z, B("gold_block"))
        b.set(x, y + h + 1, z, B("lightning_rod", facing="up"))


# ------------------------------------------------------------------ start: the ziggurat
CX, CZ = 22, 26
TIERS = [(18, 1), (15, 5), (12, 9), (9, 13), (6, 17)]     # (half size, first y)
TOP = 20


def ziggurat():
    b = Build(f"{NAME}_ziggurat", 45, 39, 48, seed=1847901)
    rng = b.rng
    # foundation course and tiers
    for x in range(CX - 19, CX + 20):
        for z in range(CZ - 19, CZ + 20):
            b.set(x, 0, z, B(COS) if abs(x - CX) <= 18 and abs(z - CZ) <= 18 else B(SAND))
    for k, (h, y0) in enumerate(TIERS):
        for x in range(CX - h, CX + h + 1):
            for z in range(CZ - h, CZ + h + 1):
                edge = abs(x - CX) == h or abs(z - CZ) == h
                for y in range(y0, y0 + 4):
                    if edge:
                        st = B(BAND_A) if y == y0 + 2 and k % 2 == 0 else B(BAND_B) if y == y0 + 2 else body(rng)
                        if y == y0 + 3:
                            st = B(CHOS) if (x + z) % 4 == 0 else B(COS)
                        b.set(x, y, z, st)
                    else:
                        b.set(x, y, z, B(OS))
        # ledge coping one step out
    for x in range(CX - 6, CX + 7):
        for z in range(CZ - 6, CZ + 7):
            b.set(x, TOP, z, B(COS) if (x + z) % 2 else B(SOS))
    # grand stair up the front, cheek walls and brazier landings
    for z in range(1, TOP + 1):
        y = z
        for x in range(19, 26):
            b.set(x, y, z, stairs(OS, "south"))
            for yy in range(1, y):
                b.set(x, yy, z, B(OS))
            for hh in range(1, 4):
                if b.get(x, y + hh, z) is not None:
                    b.set(x, y + hh, z, AIR)
        for x in (18, 26):
            for yy in range(0, y + 1):
                b.set(x, yy, z, body(rng))
            b.set(x, y + 1, z, B(f"{OS}_wall") if z % 4 else B(CHOS))
            if z % 4 == 0:
                b.campfire(x, y + 2, z, lit=True)
    for x in range(18, 27):
        b.set(x, 0, 1, B(COS))
        b.set(x, 0, 0, B(rng.choice([SAND, COS, SAND])))
    # sun mosaic platform and the sanctum
    sanctum(b, rng)
    # gallery inside the base and the sand-choked west doorway
    gallery(b, rng)
    # dunes: deep on the west and the back, a little on the east, the stair kept clear
    phase = rng.random() * 6.28

    def dune_h(x, z):
        edge = min(x, 44 - x, z, 47 - z)
        taper = min(1.0, edge / 6.0)
        d = 15 - 0.55 * x + 0.32 * max(0, z - 26) + 2.5 * math.sin(z * 0.23 + phase) + \
            1.5 * math.sin((x + z) * 0.17)
        d = max(d, 6 - 0.2 * abs(x - 38) + 0.4 * max(0, z - 30))
        if 17 <= x <= 27 and z <= 22:
            d = min(d, 0)
        return d * taper

    dune_fill(b, rng, dune_h, y0=1, ymax=19)
    # a few vanilla-sand patches on the dunes are archaeology candidates (processor)
    for (x, z) in ((6, 30), (8, 18), (10, 40), (36, 44), (4, 24)):
        for y in range(19, 0, -1):
            if b.get(x, y, z) is not None and b.get(x, y, z).id == SAND:
                b.set(x, y, z, B("sand"))
                break
    connector(b, 0, 1, 26, "west", P_PATHS)
    connector(b, 44, 1, 26, "east", P_PATHS)
    b.jigsaw(22, 1, 0, "north", target=J_AVENUE, pool=P_AVENUE)
    return b


def sanctum(b, rng):
    y = TOP
    x0, x1, z0, z1 = 17, 27, 23, 31
    # portico columns in front, walls on three sides
    for x in (17, 19, 25, 27):
        b.set(x, y + 1, 21, B(CHOS))
        for yy in range(y + 2, y + 6):
            b.set(x, yy, 21, B(SOS))
        b.set(x, y + 6, 21, B(CHOS))
    for (x, z) in ring(x0, z0, x1, z1):
        if z == z0 and 20 <= x <= 24:
            continue
        for yy in range(y + 1, y + 7):
            b.set(x, yy, z, B(BAND_A) if yy == y + 5 else body(rng))
    for x in range(20, 25):
        b.set(x, y + 6, z0, B(COS))
        b.set(x, y + 5, z0, stairs(OS, "east" if x == 20 else "west" if x == 24 else "north", "top")
              if x in (20, 24) else B(COS))
    b.air(x0 + 1, y + 1, z0 + 1, x1 - 1, y + 5, z1 - 1)
    for x in range(20, 25):
        for yy in range(y + 1, y + 5):
            b.set(x, yy, z0, AIR)
    for x in range(16, 29):                              # roof over portico and cella
        for z in range(20, 33):
            b.set(x, y + 7, z, slab(OS) if x in (16, 28) or z in (20, 32) else B(COS))
    for (x, z) in ring(17, 21, 27, 31):
        b.set(x, y + 8, z, B(f"{OS}_wall") if (x + z) % 2 else B(CHOS))
    # windows of light-blue glass in the side walls
    for z in (26, 28):
        for xx in (x0, x1):
            b.set(xx, y + 3, z, B("light_blue_stained_glass_pane"))
            b.set(xx, y + 4, z, B("light_blue_stained_glass_pane"))
    # the sun mosaic and its hidden shaft
    mx, mz = 22, 27
    for x in range(mx - 3, mx + 4):
        for z in range(mz - 3, mz + 4):
            d = math.hypot(x - mx, z - mz)
            if d <= 3.3:
                b.set(x, y, z, B("yellow_glazed_terracotta", facing=["north", "east", "south", "west"][(x + z) % 4])
                      if d > 1.6 else B("orange_glazed_terracotta", facing="north"))
    b.set(mx, y, mz, B("iron_trapdoor", facing="south", half="top", open=False, powered=False, waterlogged=False))
    for yy in range(1, y):
        b.set(mx, yy, mz, B("ladder", facing="south"))
        b.set(mx, yy, mz - 1, body(rng))
    b.set(mx + 1, y + 1, mz, B("lever", face="floor", facing="west", powered=False))
    # altar, lore, offerings, braziers
    b.set(22, y + 1, 30, B(CHOS))
    b.set(21, y + 1, 30, stairs(OS, "west", "top"))
    b.set(23, y + 1, 30, stairs(OS, "east", "top"))
    b.set(22, y + 2, 30, B("gold_block"))
    b.lectern(19, y + 1, 29, "north", book())
    b.pot(25, y + 1, 30, "north", ("brick", "prize_pottery_sherd", "brick", "brick"))
    b.pot(19, y + 1, 30, "north", ("brick", "brick", "explorer_pottery_sherd", "brick"))
    b.set(26, y + 1, 30, B("candle", candles=4, lit=True))
    b.set(18, y + 1, 24, B("orange_candle", candles=3, lit=True))
    chain_lamp(b, 22, y + 7, 25, 1)
    for (x, z) in ((16, 20), (28, 20)):
        b.set(x, y + 1, z, B(CHOS))
        b.campfire(x, y + 2, z, lit=True)
    # the standing sun disc on the roof, facing the stair
    dz = 27
    for x in range(22 - 4, 22 + 5):
        for yy in range(y + 8, y + 17):
            d = math.hypot(x - 22, yy - (y + 12))
            if d <= 4.2:
                st = B("raw_gold_block") if d < 1.5 else B("yellow_terracotta") if d < 3.0 else \
                    B("orange_terracotta")
                b.set(x, yy, dz, st)
    b.set(22, y + 17, dz, B("lightning_rod", facing="up"))          # rays
    b.set(17, y + 12, dz, B("lightning_rod", facing="west"))
    b.set(27, y + 12, dz, B("lightning_rod", facing="east"))
    b.set(19, y + 15, dz, B("lightning_rod", facing="up"))
    b.set(25, y + 15, dz, B("lightning_rod", facing="up"))


def gallery(b, rng):
    """Pillared gallery inside the base course (floor y=0, air 1..5) - sarcophagi, murals, sand."""
    x0, x1, z0, z1 = 12, 32, 17, 35
    b.air(x0, 1, z0, x1, 5, z1)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, B(COS) if (x + z) % 3 else B(SOS))
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                for y in range(1, 6):
                    b.set(x, y, z, B(BAND_B) if y == 3 and (x + z) % 2 else B(SOS))
    for x in range(x0 + 2, x1 - 1, 4):
        for z in (z0 + 3, z1 - 3):
            for y in range(1, 6):
                b.set(x, y, z, B(CHOS) if y in (1, 5) else B(COS))
    # the shaft from the sanctum lands here (pillar + ladder down to the floor)
    for y in range(1, 6):
        b.set(22, y, 26, body(rng))
        b.set(22, y, 27, B("ladder", facing="south"))
    # murals of glazed terracotta on the long walls
    for x in range(x0 + 1, x1, 2):
        b.set(x, 3, z0 - 1, B("orange_glazed_terracotta", facing="south"))
        b.set(x, 3, z1 + 1, B("light_blue_glazed_terracotta", facing="north"))
    # sarcophagi with candles, pots and sand drifts
    for (x, z) in ((15, 21), (15, 31), (29, 21), (29, 31)):
        for dx in (0, 1):
            b.set(x + dx, 1, z, B(CHOS) if dx == 0 else B(SOS))
            b.set(x + dx, 2, z, slab(OS))
        b.set(x, 3, z, B("candle", candles=2, lit=False))
    for (x, z) in ((13, 18), (31, 34), (13, 34), (31, 18)):
        b.pot(x, 1, z, "north", (rng.choice(["brick", "sheaf_pottery_sherd", "prize_pottery_sherd"]), "brick",
                                 "brick", rng.choice(["brick", "arms_up_pottery_sherd"])))
    for (x, z) in ((14, 26), (30, 24), (20, 34), (25, 18)):
        b.set(x, 1, z, B(SAND))
        b.brushable(x, 2, z, "sand", ARCH) if (x + z) % 2 else b.set(x, 2, z, B(SAND))
    b.set(12, 1, 25, B("soul_lantern"))
    b.set(32, 1, 27, B("soul_lantern"))
    # west doorway, choked with drift sand
    for x in range(4, x0 - 1):
        for z in (25, 26, 27):
            for y in range(1, 5):
                b.set(x, y, z, B(SAND) if y <= 3 and z == 26 or (y <= 2) else body(rng))
            if z == 26:
                b.set(x, 0, z, B(COS))
    for x in range(4, x0 - 1):
        for y in (1, 2, 3):
            b.set(x, y, 26, B(SAND) if x < 9 or y == 1 else AIR)
    for x in range(x0 - 1, x0):
        for y in (1, 2, 3):
            b.set(x, y, 26, AIR)
    b.set(4, 4, 26, B(CHOS))
    # stair down to the undercroft
    b.jigsaw(24, 0, 33, "down", target=J_UNDER, pool=P_UNDER, final="minecraft:ladder[facing=south,waterlogged=false]",
             top="north")
    for (x, z) in ((23, 33), (25, 33), (24, 34)):
        b.set(x, 1, z, B(f"{OS}_wall"))


# ------------------------------------------------------------------ undercroft
def undercroft():
    b = Build(f"{NAME}_undercroft", 19, 9, 19, seed=1847911)
    rng = b.rng
    for x in range(19):
        for z in range(19):
            for y in range(9):
                b.set(x, y, z, B(rng.choice([OS, OS, SAND, "sandstone"])))
    b.air(2, 1, 2, 16, 5, 16)
    for x in range(2, 17):
        for z in range(2, 17):
            b.set(x, 0, z, B(COS) if (x + z) % 2 else B(SOS))
    # vault ribs and pillars
    for (x, z) in ((5, 5), (13, 5), (5, 13), (13, 13), (9, 5), (9, 13), (5, 9), (13, 9)):
        for y in range(1, 6):
            b.set(x, y, z, B(CHOS) if y in (1, 5) else B(SOS))
    for x in range(2, 17):
        b.set(x, 5, 9, stairs(OS, "south", "top") if x % 2 else B(COS))
    for z in range(2, 17):
        b.set(9, 5, z, stairs(OS, "east", "top") if z % 2 else B(COS))
    # ladder shaft up to the gallery
    for y in range(1, 9):
        b.set(9, y, 10, B("ladder", facing="south"))
        b.set(9, y, 9, B(SOS))
    b.jigsaw(9, 8, 10, "up", name=J_UNDER, final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    # the guardian spawner (dark hall), sarcophagi, sand piles
    b.spawner(4, 1, 15, "husk", min_delay=240, max_delay=800, count=3, max_nearby=5)
    for (x, z) in ((3, 3), (15, 3), (3, 15)):
        for dz in (0, 1):
            b.set(x, 1, z + dz, B(SOS))
            b.set(x, 2, z + dz, slab(OS))
    for (x, z) in ((7, 3), (11, 15), (15, 7), (3, 11), (12, 12), (6, 7)):
        b.set(x, 1, z, B(SAND))
        b.brushable(x, 2, z, "sand", ARCH)
    for x in range(3, 16, 3):
        b.set(x, 3, 1, B("orange_glazed_terracotta", facing="south"))
        b.set(x, 3, 17, B("light_blue_glazed_terracotta", facing="north"))
    b.chest(15, 1, 15, "west", LOOT)
    # sealed treasury behind a cracked wall (east)
    b.air(17, 1, 8, 17, 3, 10)
    for z in (8, 9, 10):
        for y in (1, 2, 3):
            b.set(16, y, z, B("sandstone"))
    b.set(16, 2, 9, B("chiseled_sandstone"))
    b.chest(17, 1, 9, "west", HIDDEN)
    b.set(17, 1, 8, B("gold_block"))
    b.set(17, 1, 10, B("skeleton_skull", rotation=4))
    b.set(17, 2, 8, B("candle", candles=3, lit=False))
    b.set(15, 1, 9, B("cobweb"))
    return b


# ------------------------------------------------------------------ avenue (rigid, at the stair foot)
def avenue():
    b = Build(f"{NAME}_avenue", 21, 14, 22, seed=1847921)
    rng = b.rng
    for x in range(21):
        for z in range(22):
            b.set(x, 0, z, B(COS) if 8 <= x <= 12 else B(SAND))
    for z in range(0, 22):
        b.set(7, 0, z, B(CHOS) if z % 4 == 0 else B(SOS))
        b.set(13, 0, z, B(CHOS) if z % 4 == 0 else B(SOS))
    for z in (5, 12, 19):                               # guardians face the approaching pilgrims
        sphinx(b, 4, 1, z, "north", rng)
        sphinx(b, 15, 1, z, "north", rng)
    obelisk(b, 6, 1, 1, 9, rng)
    obelisk(b, 14, 1, 1, 9, rng)
    for (x, z) in ((2, 8), (18, 15), (1, 19)):
        b.set(x, 1, z, B(SOS))
        b.set(x, 2, z, B(SOS))
        if rng.random() < 0.5:
            b.set(x, 3, z, slab(OS))
    phase = rng.random() * 6

    def dune(x, z):
        side = min(x, 20 - x)
        return (3.5 - side * 0.6 + 1.2 * math.sin(z * 0.4 + phase)) if not 7 <= x <= 13 else 0

    dune_fill(b, rng, dune, y0=1, ymax=4)
    b.jigsaw(10, 1, 21, "south", name=J_AVENUE)
    connector(b, 10, 1, 0, "north", P_PATHS)
    return b


# ------------------------------------------------------------------ grounds
def ground_oasis():
    b = Build(f"{NAME}_oasis", 15, 12, 15, seed=1847931)
    rng = b.rng
    piece_in(b, 7, 2, 0, "north")
    for x in range(15):
        for z in range(1, 15):
            b.set(x, 0, z, B("sandstone"))
            b.set(x, 1, z, B(SAND))
    pool = [(x, z) for x in range(15) for z in range(1, 15) if math.hypot(x - 7, z - 8) < 4.2]
    for (x, z) in pool:
        b.set(x, 1, z, B("water", level=0))
        b.set(x, 0, z, B("clay"))
        if rng.random() < 0.12:
            b.set(x, 2, z, B("lily_pad"))
    for (x, z) in ((3, 6), (11, 5), (4, 12), (12, 12)):
        if b.get(x, 1, z) is not None and b.get(x, 1, z).id == SAND:
            b.set(x, 1, z, B("grass_block"))
            tree(b, x, 2, z, rng, "expanse:palm_log", "expanse:palm_leaves", height=rng.randint(5, 7), crown=2.2,
                 lean=rng.choice(["north", "south", "east", "west"]), flat=0.4)
    for (x, z) in pool:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, z + dz)
            if p not in pool and 0 <= p[0] < 15 and 1 <= p[1] < 15 and b.get(p[0], 2, p[1]) is None and \
                    rng.random() < 0.5:
                b.set(p[0], 1, p[1], B("grass_block"))
                if rng.random() < 0.5:
                    b.tall_plant(p[0], 2, p[1], "expanse:cattail")
                else:
                    b.set(p[0], 2, p[1], B("short_grass"))
    b.set(1, 2, 2, B("barrel", facing="up"))
    b.set(13, 2, 2, B(COS))
    b.set(13, 3, 2, B("water_cauldron", level=3))
    return b


def ground_colonnade():
    b = Build(f"{NAME}_colonnade", 17, 10, 13, seed=1847933)
    rng = b.rng
    piece_in(b, 8, 1, 0, "north")
    for x in range(17):
        for z in range(1, 13):
            b.set(x, 0, z, B(COS) if 3 <= z <= 9 and rng.random() < 0.7 else B(SAND))
    for i, x in enumerate(range(2, 16, 3)):
        for z in (4, 8):
            h = 6 if (i + z) % 3 else rng.randint(2, 4)
            for y in range(1, h + 1):
                b.set(x, y, z, B(CHOS) if y == 1 else B(SOS))
            if h == 6:
                b.set(x, 7, z, B(COS))
    for x in range(1, 10):
        if b.get(x, 7, 4) is not None or x in range(2, 9):
            b.set(x, 7, 4, B(COS))
    for (x, z) in ((10, 10), (12, 11), (4, 11)):
        b.set(x, 1, z, B(SOS))
        b.set(x + 1, 1, z, B(CHOS))
    phase = rng.random() * 6

    def dune(x, z):
        return 3.2 + 1.8 * math.sin(x * 0.4 + phase) - abs(z - 6) * 0.25
    dune_fill(b, rng, dune, y0=1, ymax=5)
    for (x, z) in ((3, 6), (7, 5), (11, 7), (14, 6), (6, 9)):
        for y in range(5, 0, -1):
            if b.get(x, y, z) is not None and b.get(x, y, z).id == SAND:
                b.set(x, y, z, B("sand"))
                break
    return b


def ground_quarters():
    b = Build(f"{NAME}_quarters", 15, 8, 14, seed=1847935)
    rng = b.rng
    piece_in(b, 7, 1, 0, "north")
    for x in range(15):
        for z in range(1, 14):
            b.set(x, 0, z, B(COS) if 1 <= x <= 13 and 2 <= z <= 12 else B(SAND))
    rooms = [(1, 2, 7, 7), (7, 2, 13, 7), (1, 7, 13, 12)]
    for (x0, z0, x1, z1) in rooms:
        for (x, z) in ring(x0, z0, x1, z1):
            h = rng.choice([1, 2, 2, 3, 4])
            for y in range(1, h + 1):
                b.set(x, y, z, body(rng))
    for (x, z) in ((7, 2), (4, 7), (10, 7), (7, 12)):
        for y in range(1, 5):
            b.set(x, y, z, AIR)
    b.set(7, 1, 2, AIR)
    b.chest(2, 1, 11, "east", LOOT)
    b.pot(12, 1, 11, "north", ("brick", "brick", "brick", "burn_pottery_sherd"))
    b.pot(2, 1, 3, "north", ("brick", "scrape_pottery_sherd", "brick", "brick"))
    b.set(12, 1, 3, B("water_cauldron", level=1))
    phase = rng.random() * 6

    def dune(x, z):
        return 2.6 + 1.6 * math.sin(z * 0.5 + phase) + 0.25 * (x - 7)
    dune_fill(b, rng, dune, y0=1, ymax=4)
    for (x, z) in ((4, 4), (10, 5), (6, 10), (11, 9), (3, 9)):
        for y in range(4, 0, -1):
            if b.get(x, y, z) is not None and b.get(x, y, z).id == SAND:
                b.set(x, y, z, B("sand"))
                break
    return b


def ground_fallen_obelisk():
    b = Build(f"{NAME}_fallen_obelisk", 15, 9, 15, seed=1847937)
    rng = b.rng
    piece_in(b, 7, 1, 0, "north")
    for x in range(15):
        for z in range(1, 15):
            d = math.hypot(x - 7, z - 8)
            b.set(x, 0, z, B(COS) if d < 5 else B(SAND))
    for (x, z) in ring(3, 4, 11, 12):
        if (x + z) % 2 == 0:
            b.set(x, 1, z, slab(OS))
    # sundial gnomon at the centre, the obelisk broken and fallen across the plaza
    b.set(7, 1, 8, B(CHOS))
    b.set(7, 2, 8, B(SOS))
    b.set(7, 3, 8, B("lightning_rod", facing="up"))
    for x in range(1, 6):
        b.set(x, 1, 12, B(SOS))
    b.set(0, 1, 12, B(CHOS))
    for x in range(9, 13):
        b.set(x, 1, 3, B(SOS))
    b.set(13, 1, 3, B("gold_block"))
    obelisk(b, 12, 1, 12, 3, rng, broken=True)
    phase = rng.random() * 6

    def dune(x, z):
        return 2.5 + 2 * math.sin(x * 0.45 + phase) - math.hypot(x - 7, z - 8) * 0.0 - (2 if math.hypot(x - 7, z - 8) < 5
                                                                                        else 0)
    dune_fill(b, rng, dune, y0=1, ymax=4)
    for (x, z) in ((2, 6), (12, 9), (5, 13)):
        for y in range(4, 0, -1):
            if b.get(x, y, z) is not None and b.get(x, y, z).id == SAND:
                b.set(x, y, z, B("sand"))
                break
    return b


def pools():
    rp = random.Random(1847941)
    mats = ((COS, 2), (SAND, 3), ("expanse:opal_sandstone", 1))
    return {
        "start": [Piece(ziggurat(), 1, PROC)],
        "undercroft": [Piece(undercroft(), 1, PROC)],
        "avenue": [Piece(avenue(), 1, PROC)],
        "paths": [Piece(path_piece(f"{NAME}_path_a", 9, P_GROUNDS, rp, mats=mats), 3, PROC, "terrain_matching"),
                  Piece(path_piece(f"{NAME}_path_b", 14, P_GROUNDS, rp, mats=mats), 2, PROC, "terrain_matching"),
                  Empty(1)],
        "grounds": [Piece(ground_oasis(), 3, PROC), Piece(ground_colonnade(), 3, PROC),
                    Piece(ground_quarters(), 2, PROC), Piece(ground_fallen_obelisk(), 2, PROC)],
    }
