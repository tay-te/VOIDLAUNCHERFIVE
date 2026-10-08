"""Gallows hills of the lowlands (site=knoll: SitedJigsawStructure finds a bare rise standing a dozen blocks over
the land, where the lord's justice is seen from the roads). The plains, meadows, open woods and moors.

Grim but plain: the frame where the condemned were hanged, a breaking wheel on its pole, the unmarked graves of
those who could not be buried in the churchyard, and a weathered board telling the passer-by what the place is.
No bodies; one skull in the grass at most.

start pool (2 variants)
  gallows : a two-post gallows of black oak on a stone footing, knee-braced, a chain hanging from the
            beam, the ladder still against one post; the breaking wheel on its pole with a chain hanging
            from the rim; four graves, mounds and crude stick crosses; the hangman's barrel (public);
            the newest mound still heaped up over what was buried with the man (hidden)
  gibbet  : a tall single-arm gibbet on a round stone platform (a raven stone) with steps, an iron
            cage hung from the arm on a chain; graves at the foot, a dead tree, the board; the same two
            containers
"""
import math

from blocks import AIR, B
from builder import Build, slab, stairs
from pieces import Piece
from regions import mc
from small_data import data_for
from smallkit import clear, flora, pick, ring, trail

NAME = "gallows_hill"
STRUCTURE = dict(biomes=["aspen_parkland", "maple_highlands", "olive_groves", "heather_moor", "woodland_glades",
                         *mc("plains", "sunflower_plains", "meadow", "forest", "flower_forest", "birch_forest")],
                 size=1, max_distance=24, set="knolls", site="knoll", max_relief=24, weight=2)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
PROCS = "small_bare"
STONE = [("cobblestone", 4), ("mossy_cobblestone", 2), ("stone", 2), ("andesite", 1)]
GROUND = ["grass_block", "coarse_dirt", "grass_block", "coarse_dirt"]
PATH = [("coarse_dirt", 4), ("dirt_path", 2), ("gravel", 2)]
SIGN = ["Here the Lord's", "justice is", "done. Let all", "take heed."]
SIGN_BACK = ["Thieves,", "robbers and", "murderers", ""]


def _mound(b, rng, x, z, along="z", fresh=False, marker=None):
    """An unmarked grave: a strip of coarse earth, heaped if fresh; a crude stick cross or a stone at its head."""
    cells = [(x, z), (x, z + 1)] if along == "z" else [(x, z), (x + 1, z)]
    for (cx, cz) in cells:
        b.set(cx, 0, cz, B("coarse_dirt"))
        if fresh:
            b.set(cx, 1, cz, B("coarse_dirt") if rng.random() < 0.8 else B("rooted_dirt"))
        elif b.get(cx, 1, cz) is None:
            b.set(cx, 1, cz, AIR)
    hx, hz = (x, z - 1) if along == "z" else (x - 1, z)
    if marker == "cross":
        b.set(hx, 0, hz, B("coarse_dirt"))
        b.set(hx, 1, hz, B("spruce_fence"))
        b.set(hx, 2, hz, B("spruce_fence"))
        if along == "z":
            b.set(hx + 1, 2, hz, B("spruce_fence"))
            b.set(hx - 1, 2, hz, B("spruce_fence"))
        else:
            b.set(hx, 2, hz + 1, B("spruce_fence"))
            b.set(hx, 2, hz - 1, B("spruce_fence"))
    elif marker == "stick":                            # only the upright left
        b.set(hx, 0, hz, B("coarse_dirt"))
        b.set(hx, 1, hz, B("spruce_fence"))
    elif marker == "stone":
        b.set(hx, 0, hz, pick(rng, STONE))
        b.set(hx, 1, hz, pick(rng, [("cobblestone", 1), ("mossy_cobblestone", 1)]))
    return cells


def _board(b, rng, x, z, rot):
    b.set(x, 0, z, B("coarse_dirt"))
    b.set(x, 1, z, B("dark_oak_fence"))
    b.sign(x, 2, z, "dark_oak", SIGN, rotation=rot, back=SIGN_BACK)


def _dead_tree(b, rng, x, z):
    for y in range(1, 6):
        b.set(x, y, z, B("dark_oak_log", axis="y"))
    b.set(x + 1, 4, z, B("dark_oak_log", axis="x"))
    b.set(x + 2, 5, z, B("dark_oak_fence"))
    b.set(x + 2, 4, z, B("dark_oak_fence"))
    b.set(x, 5, z - 1, B("dark_oak_log", axis="z"))
    b.set(x, 6, z - 1, B("dark_oak_fence"))
    b.set(x - 1, 6, z, B("dark_oak_fence"))
    b.set(x - 1, 5, z, B("dark_oak_fence"))
    b.set(x, 6, z, B("dark_oak_fence"))


def gallows():
    b = Build(f"{NAME}_gallows", 15, 9, 15, seed=1907641)
    rng = b.rng
    gz = 6
    for x in range(3, 12):                              # the stone footing under the frame
        for z in range(gz - 1, gz + 2):
            if rng.random() < 0.85 or z == gz:
                b.set(x, 0, z, pick(rng, STONE))
    for px in (5, 9):
        b.set(px, 0, gz, B("stone_bricks"))
        for y in range(1, 6):
            b.set(px, y, gz, B("dark_oak_log", axis="y"))
    for x in range(4, 11):
        b.set(x, 6, gz, B("dark_oak_log", axis="x"))
    b.set(6, 5, gz, stairs("dark_oak", "west", "top"))   # knee braces
    b.set(8, 5, gz, stairs("dark_oak", "east", "top"))
    b.set(7, 5, gz, B("iron_chain", axis="y"))
    b.set(7, 4, gz, B("iron_chain", axis="y"))
    for y in (1, 2, 3):                                 # the ladder against the west post
        b.set(5, y, gz + 1, B("ladder", facing="south"))
    b.barrel(10, 1, gz + 1, "up", LOOT)
    # the breaking wheel on its pole
    wx, wz = 11, 11
    b.set(wx, 0, wz, pick(rng, STONE))
    for y in range(1, 6):
        b.set(wx, y, wz, B("spruce_log", axis="y"))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            b.set(wx + dx, 6, wz + dz, B("spruce_trapdoor", facing="north", half="top", open=False))
    b.set(wx + 1, 5, wz, B("iron_chain", axis="y"))
    b.set(wx - 1, 1, wz + 1, B("skeleton_skull", rotation=6))
    b.set(wx - 1, 0, wz + 1, B("coarse_dirt"))
    # the graves of the hanged, outside the consecrated ground: mounds and crude crosses
    _mound(b, rng, 2, 10, "z", marker="cross")
    _mound(b, rng, 4, 11, "z", marker="stick")
    _mound(b, rng, 6, 12, "z", fresh=True, marker=None)
    b.set(6, 0, 12, None)
    b.chest(6, 0, 12, "south", HIDDEN)                 # buried with him, under the fresh mound
    _mound(b, rng, 11, 2, "x", marker="stone")
    _board(b, rng, 2, 3, 6)
    trail(b, [(1, 14), (4, 9), (7, gz + 2)], rng, PATH, width=1, fade=0.7)
    clear(b, 0, 1, 0, 14, 3, 14)
    flora(b, [(x, z) for x in range(15) for z in range(15) if not (3 <= x <= 11 and gz - 1 <= z <= gz + 1)],
          rng, 0.14, GROUND)
    return b


def gibbet():
    b = Build(f"{NAME}_gibbet", 15, 11, 15, seed=1907643)
    rng = b.rng
    c = 7
    # the raven stone: a round platform of rubble two high, steps up on the south
    for (x, z) in ring(c, c, 0, 3.3, (15, 15)):
        b.set(x, 0, z, pick(rng, STONE))
        b.set(x, 1, z, pick(rng, STONE) if math.hypot(x - c, z - c) > 2.4 else
              pick(rng, [("coarse_dirt", 2), ("gravel", 1), ("cobblestone", 1)]))
    for (x, z) in ring(c, c, 2.4, 3.3, (15, 15)):
        if rng.random() < 0.5:
            b.set(x, 2, z, slab("cobblestone") if rng.random() < 0.7 else B("moss_carpet"))
    for dx in (-1, 0, 1):
        b.set(c + dx, 1, c + 3, stairs("cobblestone", "north"))
        b.set(c + dx, 2, c + 3, AIR)
    # the gibbet: one tall post, an arm, a brace, the cage on its chain
    b.set(c - 1, 1, c, B("stone_bricks"))
    for y in range(2, 10):
        b.set(c - 1, y, c, B("dark_oak_log", axis="y"))
    for x in range(c - 1, c + 3):
        b.set(x, 9, c, B("dark_oak_log", axis="x"))
    b.set(c, 8, c, stairs("dark_oak", "west", "top"))
    b.set(c + 2, 8, c, B("iron_chain", axis="y"))
    b.set(c + 2, 7, c, B("iron_chain", axis="y"))
    b.set(c + 2, 6, c, B("iron_bars"))
    b.set(c + 2, 5, c, B("iron_bars"))
    b.set(c + 2, 4, c, B("iron_chain", axis="y"))
    b.barrel(c - 2, 2, c - 1, "up", LOOT)
    # graves round the foot of the stone
    _mound(b, rng, 2, 10, "z", marker="stick")
    _mound(b, rng, 12, 9, "z", marker="cross")
    _mound(b, rng, 11, 12, "x", fresh=True)
    b.set(11, 0, 12, None)
    b.chest(11, 0, 12, "east", HIDDEN)
    _mound(b, rng, 2, 3, "x", marker="stone")
    _dead_tree(b, rng, 12, 3)
    _board(b, rng, 4, 13, 8)
    trail(b, [(c, 11), (c - 1, 13), (c - 2, 14)], rng, PATH, width=1, fade=0.6)
    clear(b, 0, 1, 0, 14, 3, 14)
    flora(b, [(x, z) for x in range(15) for z in range(15) if math.hypot(x - c, z - c) > 3.6], rng, 0.14, GROUND)
    return b


def pools():
    return {"start": [Piece(gallows(), 3, PROCS), Piece(gibbet(), 2, PROCS)]}


DATA = data_for(NAME, [PROCS])
