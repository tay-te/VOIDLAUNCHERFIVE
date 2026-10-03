"""Shepherd's bothies of the heather moor.

start pool (3 variants)
  bothy : a drystone bothy under a slate roof with a smoking chimney, a round
          sheepfold beside it. Inside: hearth, box bed, the tally book on the table,
          the dog's cushion and bowl by the door; a painting on the north wall hides
          the shepherd's cupboard (hidden chest)
  ruin  : the same bothy fallen in - roof timbers on the floor, nettles, a cold
          hearth with the old stash still under the hearthstone, the fold breached
  peat  : a peat bank being cut, turves stacked in little houses to dry, a barrow,
          the cutter's shelter; a keg of bog butter sticks out of the cut face
          (hidden barrel) and the cut floor gives up finds to a brush
"""
import math

from blocks import AIR, B
from builder import Build, item, slab, stairs, trapdoor
from furnish import chair
from kit import roof_gable
from pieces import Piece
from small_data import BOOKS, data_for
from common import vines_on
from smallkit import book, clear, flora, ground_item, pick, plant, ring, rubble, trail

NAME = "shepherd_bothy"
STRUCTURE = dict(biomes=["heather_moor"], size=1, max_distance=32, set="biome_sights")
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
STONE = [("cobblestone", 5), ("mossy_cobblestone", 3), ("stone", 2), ("andesite", 2)]
GROUND = ["grass_block", "grass_block", "podzol", "coarse_dirt"]
PATH = [("dirt_path", 6), ("coarse_dirt", 3), ("gravel", 1)]
ROOF = "deepslate_tile"
X0, X1, Z0, Z1 = 2, 7, 2, 6                      # bothy walls


def _hut(b, rng, ruined=False):
    """Drystone walls (y1..3), flagstone floor, gable ends; ruined: broken tops, no roof."""
    for x in range(X0 - 1, X1 + 2):                 # footing course, a little proud of the walls
        for z in range(Z0 - 1, Z1 + 2):
            if (X0 <= x <= X1 and Z0 <= z <= Z1) or rng.random() < 0.35:
                b.set(x, 0, z, pick(rng, STONE))
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            b.set(x, 0, z, pick(rng, [("stone_bricks", 3), ("cobblestone", 2), ("coarse_dirt", 2),
                                      ("packed_mud", 1)]))
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if x in (X0, X1) or z in (Z0, Z1):
                top = 3
                if ruined:
                    top = 3 - (1 if rng.random() < 0.45 else 0) - (1 if (x + z) % 4 == 0 else 0)
                    if (x, z) in ((X1, Z0), (X1, Z0 + 1), (X1 - 1, Z0)):
                        top = 1
                for y in range(1, top + 1):
                    b.set(x, y, z, pick(rng, STONE))
    clear(b, X0 + 1, 1, Z0 + 1, X1 - 1, 3, Z1 - 1)
    # gables (west and east) up toward the ridge
    for gx in (X0, X1):
        for k, y in enumerate((4, 5, 6)):
            for z in range(Z0 + 1 + k, Z1 - k):
                if not ruined or (gx == X0 and y < 6):
                    b.set(gx, y, z, pick(rng, STONE))
    # door (south) and a small shuttered window (east)
    b.set(4, 1, Z1, AIR)
    b.set(4, 2, Z1, AIR)
    if not ruined:
        b.door(4, 1, Z1, "spruce", "south", hinge="left")
        b.set(4, 3, Z1, B("stripped_spruce_log", axis="x"))
        b.set(X1, 2, 4, B("glass_pane"))
        b.set(X1 + 1, 2, 3, trapdoor("spruce", "east", "bottom", True))
        b.set(X1 + 1, 2, 5, trapdoor("spruce", "east", "bottom", True))
    b.set(4, 0, Z1 + 1, B("polished_andesite"))     # doorstep


def _hearth(b, rng, lit, top=8):
    """Hearth against the west wall, stone hood, chimney through the gable."""
    b.campfire(X0 + 1, 1, 4, lit=lit, facing="east")
    b.set(X0 + 1, 0, 4, B("stone_bricks"))
    b.set(X0 + 1, 1, 3, B("cobblestone_wall"))
    b.set(X0 + 1, 1, 5, B("cobblestone_wall"))
    b.set(X0 + 1, 2, 4, AIR)
    b.set(X0 + 1, 3, 4, stairs("stone_brick", "west", "top"))
    for y in range(1, top + 1):
        b.set(X0 - 1, y, 4, pick(rng, [("stone_bricks", 2), ("cobblestone", 2), ("mossy_cobblestone", 1)]))
    b.set(X0 - 1, 0, 4, B("cobblestone"))


def _fold(b, rng, cx, cz, r, gate_dir, broken=0.0):
    """Round drystone sheepfold with a gate; hay, a trough and the odd tuft of wool inside."""
    cells = []
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            d = math.hypot(x - cx, z - cz)
            if r - 0.5 <= d < r + 0.5 and b.inb(x, 1, z):
                cells.append((x, z))
    gx, gz = gate_dir
    gate = min(cells, key=lambda c: (c[0] - (cx + gx * r)) ** 2 + (c[1] - (cz + gz * r)) ** 2)
    for (x, z) in cells:
        if b.get(x, 1, z) is not None:
            continue
        b.set(x, 0, z, pick(rng, STONE))
        if (x, z) == gate:
            b.set(x, 1, z, B("spruce_fence_gate", facing="east" if gx else "south", open=rng.random() < 0.4))
            continue
        if rng.random() < broken:
            if rng.random() < 0.5:
                b.set(x, 1, z, slab("cobblestone") if rng.random() < 0.5 else B("mossy_cobblestone"))
            continue
        b.set(x, 1, z, pick(rng, STONE))
        if rng.random() < 0.55:
            b.set(x, 2, z, slab("cobblestone") if rng.random() < 0.6 else pick(rng, STONE))
    inner = [(x, z) for x in range(int(cx - r), int(cx + r) + 1) for z in range(int(cz - r), int(cz + r) + 1)
             if math.hypot(x - cx, z - cz) < r - 0.9]
    clear(b, int(cx - r) + 1, 1, int(cz - r) + 1, int(cx + r) - 1, 2, int(cz + r) - 1)
    return inner


def bothy():
    b = Build(f"{NAME}_bothy", 16, 10, 13, seed=1907401)
    rng = b.rng
    _hut(b, rng)
    roof_gable(b, X0, X1, Z0, Z1, 4, ROOF, lambda x, y, z: pick(rng, STONE), along="x", overhang=1,
               gable_overhang=1, brackets=False)
    for (x, y, z), (st, _) in list(b.cells.items()):   # moss creeping over the north slope
        if st.id == f"minecraft:{ROOF}_stairs" and z <= 3 and rng.random() < 0.3:
            b.set(x, y, z, B("mossy_cobblestone_stairs", facing=st.get("facing"), half=st.get("half")))
    _hearth(b, rng, lit=True)
    b.campfire(X0 - 1, 9, 4, lit=True)              # smoke from the chimney: someone is about
    # tie beam with a lantern, box bed, table with the tally book, chair, barrel, shelf
    for x in range(X0 + 1, X1):
        b.set(x, 4, 4, B("stripped_spruce_log", axis="x"))
    b.set(5, 3, 4, B("lantern", hanging=True))
    b.bed(6, 1, 4, "north", "brown")
    b.barrel(6, 1, 5, "up", LOOT)
    b.set(5, 1, 3, slab("spruce", "top"))
    ground_item(b, 5, 2, 3, book(BOOKS[NAME]), rotation=1)
    chair(b, 4, 1, 3, "spruce", "west")
    b.shelf(6, 2, 5, "spruce", "north", [item("shears"), item("bread"), item("white_wool")])
    b.item_frame(3, 2, 3, "south", item("lead"))
    # the dog's corner by the door: cushion, bowl and a gnawed bone
    b.cushion(3, 1, 5, color="red")
    ground_item(b, 5, 1, 5, item("bowl"))
    # cupboard in the north wall behind a painting
    b.chest(4, 2, Z0, "south", HIDDEN)
    b.set(4, 3, Z0, slab("cobblestone", "top"))        # a lintel slab, so the lid can lift
    b.painting(4, 2, Z0 + 1, "south", "plant")
    # outside: wool bales under the eaves, peat stack, the fold to the east
    b.set(X1 + 1, 1, 6, B("white_wool"))
    if rng.random() < 0.5:
        b.set(X1 + 1, 2, 6, B("white_wool"))
    for (x, z) in ((X0, Z1 + 2), (X0 + 1, Z1 + 2), (X0, Z1 + 3)):
        b.set(x, 1, z, B("packed_mud"))
    b.set(X0, 2, Z1 + 2, B("packed_mud"))
    inner = _fold(b, rng, 12, 7, 3.3, (-1, 0))
    for (x, z), st in zip(rng.sample(inner, 3), [B("hay_block", axis="y"), B("hay_block", axis="x"),
                                                  B("water_cauldron", level=3)]):
        b.set(x, 1, z, st)
    for (x, z) in rng.sample(inner, 3):
        if b.get(x, 1, z) is None:
            b.set(x, 1, z, B("white_carpet"))
    trail(b, [(4, Z1 + 2), (5, 9), (4, 12)], rng, PATH, width=2, fade=0.5)
    trail(b, [(5, Z1 + 2), (8, 7)], rng, PATH, width=1, fade=0.9)
    flora(b, ring(7, 6, 5.5, 9.5, (16, 13)), rng, 0.35, GROUND)
    return b


def ruin():
    b = Build(f"{NAME}_ruin", 16, 8, 13, seed=1907403)
    rng = b.rng
    _hut(b, rng, ruined=True)
    _hearth(b, rng, lit=False, top=4)               # chimney broken off
    b.set(X0 - 1, 5, 4, slab("cobblestone"))
    # fallen roof: timbers and slates on the floor, a rafter leaning on the wall
    for x in range(X0 + 1, X1):
        if rng.random() < 0.6:
            b.set(x, 1, 3, B("stripped_spruce_log", axis="x"))
    b.set(5, 1, 5, stairs(ROOF, "north"))
    b.set(6, 1, 5, slab(ROOF))
    b.set(6, 2, 4, stairs(ROOF, "east"))
    b.set(6, 1, 4, B("stripped_spruce_log", axis="y"))
    for (x, z) in ((X1 + 1, 2), (X1 + 2, 3), (X1 + 1, 4), (X1 + 2, 1)):
        b.set(x, 1, z, rng.choice([slab(ROOF), stairs(ROOF, "west"), pick(rng, STONE)]))
    # what is left: the old barrel, nettles and a rowan seedling inside
    b.barrel(3, 1, 5, "east", LOOT)
    flora(b, [(x, z) for x in range(X0 + 1, X1) for z in range(Z0 + 1, Z1)], rng, 0.45, ["coarse_dirt"],
          plants=("short_grass", "fern", "short_grass"))
    plant(b, 5, 1, 4, "oak_sapling", "grass_block")
    # the stash under the hearthstone: the cold campfire sits on it
    b.set(X0 + 1, 0, 4, None)
    b.chest(X0 + 1, 0, 4, "east", HIDDEN)
    b.set(4, 1, 4, B("stripped_spruce_log", axis="x"))    # the old table, a log on its side
    ground_item(b, 4, 2, 4, book(BOOKS[f"{NAME}_ruin"]), rotation=3)
    # the fold, breached
    _fold(b, rng, 12, 6, 3.3, (-1, 0), broken=0.35)
    rubble(b, [(X1 + 1, z) for z in range(1, 7)] + [(x, Z0 - 1) for x in range(X0, X1 + 1)], rng,
           [s for s, _ in STONE], loose=[slab("cobblestone"), B("mossy_cobblestone")], density=0.35)
    vines_on(b, rng, 0.07, ymin=1, max_len=2)
    trail(b, [(4, Z1 + 2), (3, 9), (4, 12)], rng, PATH, width=1, fade=0.3)
    flora(b, ring(6, 6, 4.5, 9.5, (16, 13)), rng, 0.4, GROUND)
    return b


def peat():
    b = Build(f"{NAME}_peat", 15, 6, 13, seed=1907405)
    rng = b.rng
    # the bank: uncut moor (mud under turf) along the north, its cut face looking south
    depths = {}
    for x in range(0, 15):
        depth = depths[x] = 3 + (1 if rng.random() < 0.3 else 0) + (1 if 4 <= x <= 9 else 0)
        for z in range(0, depth):
            edge = (z == 0) + (x in (0, 14)) + (x in (1, 13) and rng.random() < 0.5)
            if edge >= 2:
                continue                              # the bank melts into the moor at its ends
            h = 1 if edge else 2
            b.set(x, 0, z, B("mud"))
            if h == 2:
                b.set(x, 1, z, B("packed_mud") if z == depth - 1 and rng.random() < 0.5 else B("mud"))
            b.set(x, h, z, B("grass_block"))
            if rng.random() < 0.4:
                b.set(x, h + 1, z, B("short_grass"))
        if 2 <= x <= 12 and rng.random() < 0.3:      # turf overhanging the cut
            b.set(x, 2, depth, B("grass_block"))
            b.set(x, 1, depth, AIR)
    # the keg of bog butter the spade struck, its end showing in the cut face
    b.barrel(7, 1, depths[7] - 1, "south", HIDDEN)
    b.set(7, 2, depths[7], None)
    b.set(7, 1, depths[7], AIR)
    # the cut-over floor: wet mud, puddles, gravel where things turn up (archaeology)
    for x in range(1, 14):
        for z in range(5, 8):
            if b.get(x, 0, z) is None:
                r = rng.random()
                b.set(x, 0, z, B("mud") if r < 0.45 else B("coarse_dirt") if r < 0.7 else
                      B("gravel") if r < 0.85 else B("water", level=0))
                b.set(x, 1, z, AIR)
    # turves stacked in little houses to dry
    for (sx, sz) in ((2, 9), (5, 10), (9, 9)):
        for dx in (0, 1):
            for dz in (0, 1):
                b.set(sx + dx, 1, sz + dz, B("packed_mud"))
        b.set(sx, 2, sz, B("packed_mud"))
        b.set(sx + 1, 2, sz + 1, B("mud_bricks") if rng.random() < 0.3 else B("packed_mud"))
    # the barrow and the spade, the cutter's shelter with his piece (lunch)
    b.set(12, 1, 9, slab("spruce", "top"))
    b.set(12, 1, 10, slab("spruce", "top"))
    b.set(12, 2, 9, slab("mud_brick"))                 # a load of turves in the barrow
    b.set(11, 1, 9, trapdoor("spruce", "west", "bottom", True))
    b.set(12, 1, 11, B("spruce_fence"))
    ground_item(b, 6, 1, 7, item("iron_shovel"), rotation=1)
    for x in (10, 13):
        b.set(x, 1, 12, B("spruce_fence"))
        b.set(x, 2, 12, B("spruce_fence"))
    for x in range(10, 14):
        b.set(x, 3, 12, slab("spruce"))
    b.set(11, 1, 12, stairs("spruce", "south"))
    b.set(12, 1, 12, B("spruce_log", axis="y"))
    ground_item(b, 12, 2, 12, item("bread"), rotation=2)
    ground_item(b, 11, 1, 11, book(BOOKS[f"{NAME}_peat"]), rotation=0)
    trail(b, [(11, 11), (10, 10), (8, 12)], rng, PATH, width=1, fade=0.3)
    flora(b, [(x, z) for x in range(15) for z in range(8, 13)], rng, 0.25, GROUND)
    return b


def pools():
    return {"start": [Piece(bothy(), 3, "small_moor"), Piece(ruin(), 2, "small_moor_ruin"),
                      Piece(peat(), 2, "small_peat")]}


DATA = data_for(NAME, ["small_moor", "small_moor_ruin", "small_peat"])
