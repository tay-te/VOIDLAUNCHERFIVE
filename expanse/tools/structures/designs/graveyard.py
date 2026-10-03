"""Graveyards of the moors, forests, peaks and tundra.

start pool (3 variants)
  plot     : a walled family plot entered through a little roofed lychgate - headstones,
             crosses and a table tomb, epitaphs on the stones, a yew, a lantern and a
             bench; the gravedigger's barrel by the gate. The table tomb's lid lifts
             (hidden chest), and the gravel of the older graves hides grave goods
             (archaeology)
  stranger : a lone grave on a rise: a cairn, a dressed stake wearing the stranger's
             rusted mail and helmet, sword in hand, a sign of who found him; his
             belongings are buried under the mound
  chapel   : the broken gable of a chapel with its bell still in the cote, a cracked
             altar under the sky, graves among the rubble; the altar hides the plate
"""
from blocks import AIR, B
from builder import Build, item, slab, stairs
from common import vines_on
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import book, clear, flora, ground_item, pick, plant, rubble, small_tree, trail

NAME = "graveyard"
STRUCTURE = dict(biomes=["heather_moor", "redwood_giants", "verdant_peaks", "frostbloom_tundra", "cloud_forest"],
                 size=1, max_distance=32, set="wayside", weight=2)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
STONE = [("cobblestone", 3), ("mossy_cobblestone", 3), ("stone", 2), ("andesite", 1)]
GROUND = ["grass_block", "grass_block", "podzol", "coarse_dirt"]
PATH = [("gravel", 3), ("coarse_dirt", 3), ("dirt_path", 2)]
FLOWERS = ["poppy", "oxeye_daisy", "azure_bluet", "expanse:heather", "lily_of_the_valley"]
EPITAPHS = [["", "Old Ysolde", "", ""], ["", "Bran", "a good dog", ""], ["", "Tobias Hale", "", ""],
            ["", "Mother", "", ""], ["", "Unknown", "", ""], ["", "Aled", "drover", ""], ["", "Grandfather", "", ""]]


def headstone(b, rng, x, z, kind, epitaph=None):
    """A grave at (x, z): marker at z (head), grave plot at z+1..z+2 (gravel for the older ones)."""
    if kind == "stone":
        b.set(x, 1, z, B("stone_bricks"))
        b.set(x, 2, z, slab("stone_brick"))
    elif kind == "slab":
        b.set(x, 1, z, B("polished_andesite"))
    elif kind == "cross":
        b.set(x, 1, z, B("spruce_fence"))
        b.set(x, 2, z, B("spruce_fence"))
        b.set(x - 1, 2, z, B("spruce_fence"))
        b.set(x + 1, 2, z, B("spruce_fence"))
    elif kind == "skull":
        b.set(x, 1, z, B("mossy_cobblestone"))
        b.set(x, 2, z, B("skeleton_skull", rotation=0))
    b.set(x, 0, z, pick(rng, STONE))
    if epitaph and kind in ("stone", "slab"):
        b.sign(x, 1, z + 1, "spruce", epitaph, wall_facing="south")
    for dz in (1, 2):
        if b.get(x, 0, z + dz) is None:
            b.set(x, 0, z + dz, B("gravel") if rng.random() < 0.5 else B("coarse_dirt"))
        if b.get(x, 1, z + dz) is None and rng.random() < 0.5:
            plant(b, x, 1, z + dz, rng.choice(FLOWERS + ["short_grass", "short_grass"]), "coarse_dirt")


def plot():
    b = Build(f"{NAME}_plot", 13, 8, 14, seed=1907531)
    rng = b.rng
    x0, z0, x1, z1 = 1, 1, 11, 11
    for x in range(x0, x1 + 1):                       # low wall with a lychgate on the south
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                if z == z1 and x in (5, 6, 7):
                    continue
                b.set(x, 0, z, pick(rng, STONE))
                if rng.random() < 0.9:
                    b.set(x, 1, z, B("cobblestone_wall") if rng.random() < 0.6 else B("mossy_cobblestone_wall"))
    for x in (5, 7):
        for y in (1, 2, 3):
            b.set(x, y, z1, B("spruce_log", axis="y"))
    b.set(6, 1, z1, B("spruce_fence_gate", facing="south", open=True))
    for x in range(4, 9):
        b.set(x, 4, z1 - 1, stairs("dark_oak", "south"))
        b.set(x, 4, z1 + 1, stairs("dark_oak", "north"))
        b.set(x, 4, z1, B("dark_oak_planks") if 5 <= x <= 7 else slab("dark_oak", "top"))
        b.set(x, 5, z1, slab("dark_oak"))
    b.set(6, 3, z1, B("lantern", hanging=True))
    b.barrel(8, 1, z1 + 1, "west", LOOT)
    b.set(4, 1, z1 + 1, B("spruce_fence"))
    b.item_frame(4, 1, z1 + 2, "south", item("iron_shovel"))
    # graves in two rows, their markers to the north
    kinds = ["stone", "cross", "slab", "stone", "skull", "cross", "stone"]
    rng.shuffle(kinds)
    eps = list(EPITAPHS)
    rng.shuffle(eps)
    spots = [(3, 3), (5, 3), (8, 3), (3, 7), (5, 7)]
    for i, (x, z) in enumerate(spots):
        headstone(b, rng, x, z, kinds[i], eps[i])
    # the table tomb (lid lifts: hidden chest) and a candle on it
    for x in (8, 9):
        for z in (7, 8):
            b.set(x, 0, z, B("stone_bricks"))
    b.set(8, 1, 7, B("stone_bricks"))
    b.chest(8, 1, 8, "south", HIDDEN)
    b.set(9, 1, 7, B("stone_bricks"))
    b.set(9, 1, 8, B("stone_bricks"))
    for z in (7, 8):
        for x in (8, 9):
            b.set(x, 2, z, slab("smooth_stone", "bottom"))
    b.sign(8, 1, 9, "spruce", ["The Hales", "of Moorfold", "", ""], wall_facing="south")
    # the yew in the north-east corner, a bench, flowers in pots
    small_tree(b, 10, 3, rng, "spruce_log", "spruce_leaves", h=2, r=1.6, flat=True)
    b.set(2, 1, 9, slab("spruce", "top"))
    b.set(3, 1, 10, B("expanse:potted_heather"))
    ground_item(b, 2, 2, 9, book(BOOKS[NAME]), rotation=1)
    trail(b, [(6, z1), (6, 12), (5, 13)], rng, PATH, width=2, fade=0.5)
    clear(b, x0 + 1, 1, z0 + 1, x1 - 1, 3, z1 - 1)
    flora(b, [(x, z) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1)], rng, 0.2, GROUND)
    return b


def stranger():
    b = Build(f"{NAME}_stranger", 9, 7, 10, seed=1907533)
    rng = b.rng
    c = 4
    for x in range(c - 2, c + 3):                     # a low rise
        for z in range(2, 8):
            if abs(x - c) + abs(z - 4.5) < 4:
                b.set(x, 0, z, B("dirt"))
                b.set(x, 1, z, B("grass_block") if abs(x - c) + abs(z - 4.5) < 2.5 else AIR)
    for (x, z) in ((c - 1, 3), (c + 1, 3), (c, 2), (c - 1, 2), (c + 1, 2)):   # cairn at the head
        b.set(x, 1, z, pick(rng, STONE))
    b.set(c, 2, 2, pick(rng, STONE))
    b.set(c, 1, 3, B("mossy_cobblestone"))
    b.set(c, 2, 3, B("spruce_fence"))
    b.armor_stand(c, 3, 3, yaw=0.0, equipment={"head": "iron_helmet", "chest": "chainmail_chestplate",
                                                 "mainhand": "iron_sword"},
                  pose={"Head": (12.0, 0.0, 0.0), "Body": (0.0, 0.0, 0.0), "RightArm": (-10.0, 0.0, 10.0),
                        "LeftArm": (-10.0, 0.0, -10.0)})
    b.set(c, 2, 4, B("coarse_dirt"))                  # the mound, his belongings buried beneath
    b.set(c, 2, 5, B("coarse_dirt"))
    b.chest(c, 1, 4, "south", HIDDEN)
    plant(b, c, 3, 5, "poppy", "coarse_dirt")
    b.set(c + 2, 1, 6, B("spruce_fence"))
    b.sign(c + 2, 2, 6, "spruce", ["A stranger,", "found in the", "snow below", "the pass."], rotation=0)
    flora(b, [(x, z) for x in range(9) for z in range(10)], rng, 0.25, GROUND)
    trail(b, [(c, 7), (c + 1, 9)], rng, PATH, width=1, fade=0.3)
    return b


def chapel():
    b = Build(f"{NAME}_chapel", 13, 11, 14, seed=1907535)
    rng = b.rng
    gx0, gx1, gz = 3, 9, 3
    for x in range(gx0, gx1 + 1):                     # the gable wall with a pointed window
        h = 6 - abs(x - 6)
        for y in range(0, h + 4):
            b.set(x, y, gz, pick(rng, [("stone_bricks", 3), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1)]))
    for y in (2, 3, 4):
        b.set(6, y, gz, AIR)
    b.set(5, 4, gz, stairs("stone_brick", "east", "top"))
    b.set(7, 4, gz, stairs("stone_brick", "west", "top"))
    b.set(6, 2, gz, B("iron_bars"))
    b.set(6, 9, gz, AIR)                              # bell cote on the apex
    b.bell(6, 9, gz, attachment="floor", facing="north")
    for x in (5, 7):
        b.set(x, 9, gz, B("stone_brick_wall"))
    for x in (5, 6, 7):
        b.set(x, 10, gz, slab("stone_brick"))
    for x in (gx0, gx1):                              # stubs of the side walls, broken off
        for z in range(gz + 1, gz + 7):
            top = max(0, 4 - (z - gz) + rng.randint(-1, 1))
            for y in range(0, top + 1):
                b.set(x, y, z, pick(rng, STONE))
    for x in range(gx0 + 1, gx1):                     # nave floor and the cracked altar
        for z in range(gz + 1, gz + 7):
            b.set(x, 0, z, pick(rng, [("stone_bricks", 2), ("cracked_stone_bricks", 2), ("gravel", 1),
                                      ("coarse_dirt", 1)]))
    clear(b, gx0 + 1, 1, gz + 1, gx1 - 1, 4, gz + 6)
    b.set(5, 1, gz + 1, stairs("stone_brick", "east"))
    b.set(7, 1, gz + 1, stairs("stone_brick", "west"))
    b.set(6, 1, gz + 1, B("chiseled_stone_bricks"))
    b.chest(6, 0, gz + 1, "south", HIDDEN)
    b.set(6, 2, gz + 1, B("candle", candles=3, lit=False))
    b.barrel(gx1 - 1, 1, gz + 5, "west", LOOT)
    ground_item(b, 5, 1, gz + 3, book(BOOKS[f"{NAME}_chapel"]), rotation=0)
    rubble(b, [(x, z) for x in range(gx0 + 1, gx1) for z in range(gz + 2, gz + 7)], rng, [s for s, _ in STONE],
           loose=[slab("stone_brick"), stairs("stone_brick", "north"), B("mossy_stone_bricks")], density=0.25)
    # graves among the rubble outside, and a yew
    eps = list(EPITAPHS)
    rng.shuffle(eps)
    for i, (x, z) in enumerate(((2, 10), (5, 10), (8, 10), (11, 7))):
        headstone(b, rng, x, z, rng.choice(["stone", "cross", "slab", "skull"]), eps[i])
    small_tree(b, 11, 11, rng, "spruce_log", "spruce_leaves", h=2, r=1.5, flat=True)
    vines_on(b, rng, 0.08, ymin=2, max_len=3)
    trail(b, [(6, gz + 6), (6, 12), (7, 13)], rng, PATH, width=1, fade=0.5)
    flora(b, [(x, z) for x in range(13) for z in range(14)], rng, 0.2, GROUND)
    return b


def pools():
    return {"start": [Piece(plot(), 3, "small_grave"), Piece(stranger(), 2, "small_grave"),
                      Piece(chapel(), 2, "small_grave")]}


DATA = data_for(NAME, ["small_grave"])
