"""Hunters' high seats (Hochsitze) at the edge of the woods (site=wood_edge: SitedJigsawStructure finds where a wood
gives onto open land). The broadleaf, birch and dark woods, the taigas and the Expanse's woodlands.

The lord's gamekeeper sits up here at dusk and dawn, his back to the trees, looking out over the clearing where
the deer come to the salt and the hay. Spruce poles and boards, nothing more.

start pool (2 variants)
  open   : four spruce poles about five high, braced, carrying an open box with a board floor, a plank
           roof and a look-out gap all round the front; a pole ladder with slab rungs up the side; inside a
           bench and the keeper's chest (public). Before it, a lump of rock salt on a stump in a patch of
           licked bare earth, and a roofed hay rack whose trough board hides his spare kit (hidden)
  kanzel : a closed pulpit on log legs six high with a landing, a door, shuttered shooting windows and a
           gabled roof; a ladder up a leg. The salt lick, a log feeding trough with hay beside it, and the
           spare kit under a trapdoor at the foot of the ladder
"""
from blocks import AIR, B
from builder import Build, slab, stairs, trapdoor
from pieces import Piece
from regions import mc
from small_data import data_for
from smallkit import clear, flora, pick, trail

NAME = "hunters_hide"
STRUCTURE = dict(biomes=["woodland_glades", "ancient_oakwood", "bluebell_woods", "maple_highlands", "aspen_parkland",
                         "redwood_giants", "pine_heath", "larch_taiga", "boreal_muskeg",
                         *mc("forest", "flower_forest", "birch_forest", "old_growth_birch_forest", "dark_forest",
                             "dappled_forest", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga",
                             "windswept_forest", "snowy_taiga", "grove")],
                 size=1, max_distance=24, set="wood_edges", site="wood_edge", max_relief=20)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
PROCS = "small_woods"
GROUND = ["grass_block", "grass_block", "podzol", "coarse_dirt"]
LICKED = [("coarse_dirt", 3), ("mud", 1), ("packed_mud", 1), ("grass_block", 2)]
PATH = [("coarse_dirt", 3), ("podzol", 2), ("dirt_path", 2)]


def _salt_lick(b, rng, x, z):
    """Rock salt on a sawn stump, the earth round it licked bare and trodden."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx or dz) and rng.random() < 0.85:
                b.set(x + dx, 0, z + dz, pick(rng, LICKED))
                b.set(x + dx, 1, z + dz, AIR)
    b.set(x, 0, z, B("coarse_dirt"))
    b.set(x, 1, z, B("spruce_log", axis="y"))
    b.set(x, 2, z, B("expanse:salt_block"))


def _hay_rack(b, rng, x0, z, hidden):
    """A roofed hay rack (Futterraufe): two posts, hay between slatted sides over a trough, a little roof."""
    x1 = x0 + 3
    for x in (x0, x1):
        for y in (1, 2, 3):
            b.set(x, y, z, B("spruce_fence"))
    for x in (x0 + 1, x0 + 2):
        b.set(x, 1, z, slab("spruce", "top"))          # the trough board
        b.set(x, 2, z, B("hay_block", axis="x"))
        b.set(x, 2, z - 1, trapdoor("spruce", "north", "bottom", True))
        b.set(x, 2, z + 1, trapdoor("spruce", "south", "bottom", True))
        b.set(x, 3, z, B("spruce_fence"))
    for x in range(x0 - 1, x1 + 2):
        b.set(x, 4, z - 1, stairs("spruce", "south"))
        b.set(x, 4, z + 1, stairs("spruce", "north"))
        b.set(x, 4, z, B("spruce_planks"))
        b.set(x, 5, z, slab("spruce"))
    if hidden:
        b.chest(x0 + 1, 0, z, "south", HIDDEN)         # under the trough board
    for x in range(x0 - 1, x1 + 2):                    # trodden and strewn with hay round the rack
        for dz in (-2, -1, 1, 2):
            if b.get(x, 0, z + dz) is None and rng.random() < 0.5:
                b.set(x, 0, z + dz, pick(rng, LICKED))
    b.set(x1 + 1, 1, z + 2, B("hay_block", axis="z"))


def open_seat():
    b = Build(f"{NAME}_open", 14, 11, 15, seed=1907651)
    rng = b.rng
    X0, X1, Z0, Z1 = 4, 7, 3, 5                        # the box over the poles (front to the south)
    FL = 5
    legs = [(X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)]
    for (x, z) in legs:
        b.set(x, 0, z, B("coarse_dirt"))
        for y in range(1, FL + 3):
            b.set(x, y, z, B("spruce_fence"))
    b.set(X0, 2, Z0 + 1, B("spruce_fence"))            # braces across the short sides
    b.set(X1, 2, Z0 + 1, B("spruce_fence"))
    for x in range(X0, X1 + 1):                        # the board floor
        for z in range(Z0, Z1 + 1):
            if (x, z) not in legs:
                b.set(x, FL, z, slab("spruce", "top"))
    # board walls: low along the front (the look-out gap above), the back higher with the bench against it
    for x in range(X0 + 1, X1):
        b.set(x, FL + 1, Z1, trapdoor("spruce", "south", "bottom", True))
        b.set(x, FL + 1, Z0, stairs("spruce", "north"))
        b.set(x, FL + 2, Z0, trapdoor("spruce", "north", "bottom", True))
    b.chest(X0, FL + 1, Z0 + 1, "east", LOOT)
    b.set(X0, FL + 2, Z0 + 1, trapdoor("spruce", "west", "bottom", True))
    b.set(X1, FL + 1, Z0 + 1, AIR)                     # the way in from the ladder (east)
    b.set(X1, FL + 2, Z0 + 1, AIR)
    # the plank roof, low-gabled along x
    R = FL + 3
    for x in range(X0 - 1, X1 + 2):
        b.set(x, R, Z0 - 1, stairs("spruce", "south"))
        b.set(x, R, Z1 + 1, stairs("spruce", "north"))
        b.set(x, R, Z0, B("spruce_planks"))
        b.set(x, R, Z1, B("spruce_planks"))
        b.set(x, R, Z0 + 1, B("spruce_planks"))
        b.set(x, R + 1, Z0, stairs("spruce", "south"))
        b.set(x, R + 1, Z1, stairs("spruce", "north"))
        b.set(x, R + 1, Z0 + 1, slab("spruce"))
    # the pole ladder: two rails, slab rungs, a board across to the box
    lx = X1 + 2
    for y in range(1, FL + 1):
        b.set(lx, y, Z0, B("spruce_fence"))
        b.set(lx, y, Z1, B("spruce_fence"))
        b.set(lx, y, Z0 + 1, slab("spruce"))
    b.set(lx, 0, Z0 + 1, B("coarse_dirt"))
    b.set(X1 + 1, FL, Z0 + 1, slab("spruce", "top"))
    # the clearing before it: the salt lick and the hay rack
    _salt_lick(b, rng, 4, 11)
    _hay_rack(b, rng, 8, 11, True)
    trail(b, [(lx, Z0 + 2), (lx - 1, 8), (6, 10)], rng, PATH, width=1, fade=0.5)
    clear(b, 0, 1, 0, 13, 4, 14)
    flora(b, [(x, z) for x in range(14) for z in range(15)], rng, 0.25, GROUND)
    flora(b, [(x, z) for x in range(14) for z in range(0, 3)], rng, 0.3, GROUND, plants=("short_grass", "fern"))
    return b


def kanzel():
    b = Build(f"{NAME}_kanzel", 14, 13, 15, seed=1907653)
    rng = b.rng
    X0, X1, Z0, Z1 = 4, 7, 2, 5
    FL = 6
    legs = [(X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)]
    for (x, z) in legs:
        b.set(x, 0, z, B("coarse_dirt"))
        for y in range(1, FL + 3):
            b.set(x, y, z, B("stripped_spruce_log", axis="y"))
    for z0 in (Z0, Z1):                                # rails tying the legs
        for x in range(X0 + 1, X1):
            b.set(x, 3, z0, B("spruce_log", axis="x"))
    for z in range(Z0 + 1, Z1):
        b.set(X0, 3, z, B("spruce_log", axis="z"))
        b.set(X1, 3, z, B("spruce_log", axis="z"))
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if (x, z) not in legs:
                b.set(x, FL, z, B("spruce_planks"))
    # board walls with shooting windows (shutters hooked open), a door off the landing
    for y in (FL + 1, FL + 2):
        for x in range(X0 + 1, X1):
            b.set(x, y, Z0, B("spruce_planks"))
            b.set(x, y, Z1, B("spruce_planks"))
        for z in range(Z0 + 1, Z1):
            b.set(X0, y, z, B("spruce_planks"))
            b.set(X1, y, z, B("spruce_planks"))
    clear(b, X0 + 1, FL + 1, Z0 + 1, X1 - 1, FL + 2, Z1 - 1)
    for (x, z, f) in ((X0 + 2, Z1, "south"), (X0, Z0 + 2, "west"), (X1, Z0 + 1, "east")):
        b.set(x, FL + 2, z, AIR)
        dx, dz = {"south": (0, 1), "west": (-1, 0), "east": (1, 0)}[f]
        b.set(x + dx, FL + 2, z + dz, trapdoor("spruce", f, "top", True))
    b.door(X0 + 1, FL + 1, Z1, "spruce", "south", hinge="left")
    b.set(X0 + 1, FL, Z1 + 1, slab("spruce", "top"))   # the landing
    b.set(X0 + 2, FL, Z1 + 1, slab("spruce", "top"))
    b.set(X0 + 2, FL + 1, Z1 + 1, B("spruce_fence"))
    for y in range(1, FL + 2):                         # the ladder up the south-west leg
        b.set(X0, y, Z1 + 1, B("ladder", facing="south"))
    b.set(X0 + 2, FL + 1, Z0 + 1, stairs("spruce", "north"))   # bench under the back window
    b.chest(X0 + 1, FL + 1, Z0 + 1, "south", LOOT)
    # gabled roof along z
    R = FL + 3
    for z in range(Z0 - 1, Z1 + 2):
        b.set(X0 - 1, R, z, stairs("spruce", "east"))
        b.set(X1 + 1, R, z, stairs("spruce", "west"))
        b.set(X0, R, z, stairs("spruce", "east"))
        b.set(X1, R, z, stairs("spruce", "west"))
        b.set(X0 + 1, R + 1, z, stairs("spruce", "east"))
        b.set(X1 - 1, R + 1, z, stairs("spruce", "west"))
    for z in range(Z0, Z1 + 1):
        b.set(X0 + 1, R, z, B("spruce_planks"))
        b.set(X1 - 1, R, z, B("spruce_planks"))
    # the spare kit under a trapdoor at the foot of the ladder
    b.chest(X0 + 1, 0, Z1 + 2, "south", HIDDEN)
    b.set(X0 + 1, 1, Z1 + 2, trapdoor("spruce", "south", "bottom", False))
    # the clearing: the salt lick, a hollowed-log feeding trough with hay
    _salt_lick(b, rng, 9, 11)
    for x in (3, 4, 5):
        b.set(x, 1, 11, B("stripped_spruce_log", axis="x"))
    b.set(4, 1, 10, trapdoor("spruce", "north", "bottom", True))
    b.set(4, 1, 12, trapdoor("spruce", "south", "bottom", True))
    b.set(4, 2, 11, B("hay_block", axis="x"))
    b.set(6, 1, 12, B("hay_block", axis="y"))
    b.set(6, 1, 13, B("hay_block", axis="x"))
    for x in range(2, 8):
        for z in range(10, 14):
            if b.get(x, 0, z) is None and rng.random() < 0.6:
                b.set(x, 0, z, pick(rng, LICKED))
    trail(b, [(X0 + 1, Z1 + 3), (6, 9), (8, 14)], rng, PATH, width=1, fade=0.5)
    clear(b, 0, 1, 0, 13, 4, 14)
    flora(b, [(x, z) for x in range(14) for z in range(15)], rng, 0.25, GROUND)
    return b


def pools():
    return {"start": [Piece(open_seat(), 3, PROCS), Piece(kanzel(), 2, PROCS)]}


DATA = data_for(NAME, [PROCS])
