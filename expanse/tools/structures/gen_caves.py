#!/usr/bin/env python3
"""The things underground: small hand-made templates set on cave floors by the expanse:cave_template feature
(world/feature/CaveTemplateFeature.java), one group per kind of cave (world/feature/CaveThings.java).

    python3 tools/structures/gen_caves.py                  # write + validate
    python3 tools/structures/gen_caves.py --preview DIR    # also render a contact sheet of every template

Writes (and owns) only cave/ subfolders:
    data/expanse/structure/cave/<template>.nbt
    data/expanse/worldgen/processor_list/cave/<name>.json
    data/expanse/worldgen/feature/cave/<group>.json           (configured features, type expanse:cave_template)
    data/expanse/worldgen/placed_feature/cave/<group>.json
    data/expanse/loot_table/chests/cave/<name>.json

Template conventions are the little things' (gen_micro.py): x east, y up, z south; an unset cell is structure
void, so the cave shows through; layer y=0 is the floor course. The feature puts layer 0 in place of the floor
of the lowest column under the footprint, so ballast, flagstones and boards are set into the floor and things
stand on layer 1. It only places a template where the air is clear from the floor to the template's top in
every column, so a template's height decides where it can go: three or four blocks fits the passages, taller
things only the chambers, the great tunnels and the realms.

A cave is dark, so most of these bring a light with them (a lantern, a candle, a campfire, glowing stone): coming
round a bend onto a lit camp is what makes a cave feel travelled. Proportions follow vanilla's own underground
pieces (mineshaft props and rails, dungeons, ancient city fragments, trail ruins).

Output is deterministic: seeded designs, gzip mtime 0, sorted JSON.
"""
import argparse
import glob
import json
import math
import os
import random
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True  # keep the source tree free of __pycache__

from blocks import AIR, B  # noqa: E402
from builder import Build, fit_height, floating_report, item, slab, stairs, written_book  # noqa: E402
import loot as L  # noqa: E402
from processors import STONE_WEATHER, aged_wood, plist, rule  # noqa: E402
from smallkit import a_tent, bedroll, campfire_ring, meal_fire  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "expanse")
JAVA_GROUPS = os.path.join(ROOT, "src", "main", "java", "dev", "voidmc", "expanse", "world", "feature", "CaveThings.java")
SUB = "cave"
OUT_DIRS = {"structure": ".nbt", "worldgen/processor_list": ".json", "worldgen/feature": ".json",
            "worldgen/placed_feature": ".json", "loot_table/chests": ".json"}
MAX_FOOTPRINT = 24          # CaveTemplateFeature.MAX_FOOTPRINT
PASSAGE_HEIGHT = 4          # a template this high (floor course included) fits most passages
H4 = ["north", "east", "south", "west"]


def chest_loot(name):
    return f"expanse:chests/{SUB}/{name}"


def lay(b, x, y, z, it, rotation=0):
    """An item lying on the block below (x, y, z): an item frame flat on the floor."""
    b.item_frame(x, y, z, "up", it if isinstance(it, dict) else item(it), rotation=rotation)


def journal(key):
    j = BOOKS[key]
    return written_book(j["title"], j["author"], j["pages"])


def pick(rng, mix):
    """Random state from [(name, weight)] or [name]."""
    if isinstance(mix[0], tuple):
        tot = sum(w for _, w in mix)
        r = rng.random() * tot
        for name, w in mix:
            r -= w
            if r < 0:
                return B(name) if isinstance(name, str) else name
        return B(mix[-1][0]) if isinstance(mix[-1][0], str) else mix[-1][0]
    o = rng.choice(mix)
    return B(o) if isinstance(o, str) else o


def patch(b, cells, rng, mix, p=1.0, y=0):
    """Floor course: trodden ground, ballast, ash, wherever nothing is set yet."""
    for (x, z) in cells:
        if b.inb(x, y, z) and b.get(x, y, z) is None and rng.random() < p:
            b.set(x, y, z, pick(rng, mix))


def disc(cx, cz, r, size):
    sx, sz = size
    return [(x, z) for x in range(sx) for z in range(sz) if math.hypot(x - cx, z - cz) <= r]


def candles(n=3, color=None, lit=True):
    return B(f"{color}_candle" if color else "candle", candles=n, lit=lit)


def post_lantern(b, x, z, rng, wood="spruce", h=2, lantern="lantern", footing=("cobblestone", "mossy_cobblestone")):
    """A lantern on a short post: bedded footing, `h` fence, the lantern standing on top."""
    b.set(x, 0, z, B(rng.choice(footing)))
    for y in range(1, h + 1):
        b.set(x, y, z, B(f"{wood}_fence"))
    b.set(x, h + 1, z, B(lantern))


COBBLE = [("cobblestone", 4), ("mossy_cobblestone", 2), ("stone", 2), ("andesite", 1)]
BALLAST = [("gravel", 5), ("cobblestone", 1), ("andesite", 1)]
TRODDEN = [("gravel", 3), ("coarse_dirt", 3), ("dirt", 1)]
ASH = [("coarse_dirt", 3), ("gravel", 2), ("tuff", 1)]


# ============================================================== the caves at large
def miner_rest(seed):
    """Where a miner slept: a straw bedroll on trodden gravel, the lantern still on the floor at its head,
    a barrel of supplies and an empty one, the pick laid down by the bed."""
    rng = random.Random(seed)
    b = Build("miner_rest", 5, 3, 4, seed=seed)
    patch(b, [(x, z) for x in range(5) for z in range(4) if (x, z) not in ((0, 0), (4, 0), (0, 3))], rng, TRODDEN,
          p=0.75)
    bedroll(b, 1, 1, 1, "east")
    b.set(3, 1, 1, B("lantern"))
    b.barrel(4, 1, 2, "up", chest_loot("miner_cache"))
    b.set(4, 1, 3, B("barrel", facing="north"))
    lay(b, 1, 1, 2, "iron_pickaxe", rotation=1)
    lay(b, 2, 1, 2, "bread", rotation=rng.randrange(8))
    return b


def lamp_post(seed, name="lamp_post", lantern="lantern", wood="spruce"):
    """A miners' lamp on a crooked post, its footing set in the floor: a light to find the way back by."""
    rng = random.Random(seed)
    b = Build(name, 3, 4, 3, seed=seed)
    b.set(1, 0, 1, B(rng.choice(["mossy_cobblestone", "cobblestone"])))
    for y in (1, 2, 3):
        b.set(1, y, 1, B(f"{wood}_fence"))
    b.set(2, 3, 1, B(f"{wood}_fence"))
    b.set(2, 2, 1, B(lantern, hanging=True))
    patch(b, [(0, 1), (1, 2), (0, 0)], rng, BALLAST, p=0.6)
    return b


def stake_lantern(seed):
    """A lantern stood on a stake by the way, low enough for the tightest passage."""
    rng = random.Random(seed)
    b = Build("stake_lantern", 2, 3, 2, seed=seed)
    b.set(0, 0, 0, B("cobblestone"))
    b.set(0, 1, 0, B("spruce_fence"))
    b.set(0, 2, 0, B(rng.choice(["lantern", "oxidized_copper_lantern"])))
    if rng.random() < 0.6:
        b.set(1, 0, 1, B("gravel"))
    return b


def candle_cairn(seed, name="candle_cairn", mix=COBBLE, top="andesite_slab", color=None):
    """A cairn heaped where the ways fork, candles burning on its capstone: someone keeps them lit."""
    rng = random.Random(seed)
    b = Build(name, 3, 4, 3, seed=seed)
    for (x, z) in [(0, 1), (1, 0), (1, 1), (2, 1), (1, 2), (0, 0), (2, 2)]:
        if (x, z) in ((0, 0), (2, 2)) and rng.random() < 0.5:
            continue
        b.set(x, 0, z, pick(rng, mix))
    for (x, z) in [(0, 1), (1, 0), (1, 1), (2, 1), (1, 2)]:
        if (x, z) == (1, 1) or rng.random() < 0.7:
            b.set(x, 1, z, pick(rng, mix))
    b.set(1, 2, 1, B(top, type="bottom") if top.endswith("_slab") else B(top))
    b.set(1, 3, 1, candles(rng.randint(2, 4), color))
    for (x, z) in [(0, 1), (2, 1), (1, 0), (1, 2)]:
        if b.get(x, 1, z) is not None and b.get(x, 2, z) is None and rng.random() < 0.35:
            b.set(x, 2, z, candles(1, color))
            break
    return b


def marker_sign(seed, name, lines):
    """A sign on a post where a miner left word for the next: water this way, bad air, no way through."""
    rng = random.Random(seed)
    b = Build(name, 3, 4, 3, seed=seed)
    b.set(1, 0, 1, B(rng.choice(["mossy_cobblestone", "cobblestone"])))
    b.set(1, 1, 1, B("spruce_fence"))
    b.set(1, 2, 1, B("spruce_fence"))
    b.sign(1, 2, 2, "spruce", lines, wall_facing="south", color="gray")
    b.set(1, 3, 1, B("candle", candles=1, lit=True))
    patch(b, [(0, 1), (2, 0), (2, 2)], rng, BALLAST, p=0.5)
    return b


def timber_frame(seed, name="timber_frame", wood="spruce", width=5, height=4, broken=False):
    """A set of pit props: two posts and a cap beam, as the mineshafts are timbered, a lamp hung from the cap.
    `broken`: one post has given way and the cap lies across the floor."""
    rng = random.Random(seed)
    b = Build(name, width, height + 1, 3, seed=seed)
    z = 1
    log = f"{wood}_log"
    for x in (0, width - 1):
        b.set(x, 0, z, B(rng.choice(["gravel", "cobblestone"])))
    if not broken:
        for x in (0, width - 1):
            for y in range(1, height):
                b.set(x, y, z, B(log, axis="y"))
        for x in range(width):
            b.set(x, height, z, B(log, axis="x"))
        b.set(width // 2, height - 1, z, B("lantern", hanging=True))
        if rng.random() < 0.6:
            b.set(1, height - 1, z, B(f"{wood}_fence"))            # a brace in the corner
        patch(b, [(x, z) for x in range(1, width - 1)], rng, BALLAST, p=0.5)
    else:
        for y in range(1, height):
            b.set(0, y, z, B(log, axis="y"))
        b.set(0, height, z, B(f"{wood}_slab", type="bottom"))
        b.set(width - 1, 1, z, B(log, axis="y"))                   # the stub of the other post
        for x in range(1, width):                                  # the cap, fallen across
            b.set(x, 1, z + 1, B(log, axis="x"))
        b.set(1, 1, z, B("lantern"))
        patch(b, [(x, zz) for x in range(width) for zz in (0, 1, 2)], rng, [("gravel", 4), ("cobblestone", 2)], p=0.35)
        b.set(width - 2, 1, z, B("gravel"))
    return b


def remains(seed):
    """Someone who did not come back: a skull and bones on the floor, a candle burnt out beside them, the pick
    and the notebook where they fell; the gravel round them gives things up to a brush."""
    rng = random.Random(seed)
    b = Build("remains", 5, 2, 4, seed=seed)
    b.set(1, 1, 1, B("skeleton_skull", rotation=rng.randrange(16)))
    b.set(2, 1, 2, B("bone_block", axis="x"))
    lay(b, 3, 1, 1, "bone", rotation=rng.randrange(8))
    lay(b, 3, 1, 2, "bone", rotation=rng.randrange(8))
    lay(b, 1, 1, 2, journal("last_entry"), rotation=1)
    lay(b, 2, 1, 1, "stone_pickaxe", rotation=3)
    b.set(0, 1, 2, candles(1, lit=False))
    b.brushable(2, 0, 3, "gravel", chest_loot("remains_archaeology"))
    patch(b, [(1, 3), (3, 3), (0, 1), (4, 2)], rng, [("gravel", 3)], p=0.8)
    return b


def stash(seed):
    """A cache tucked against the rock: barrels stood together, one on another, a pot, the cobwebs over it all."""
    rng = random.Random(seed)
    b = Build("stash", 3, 3, 3, seed=seed)
    b.barrel(0, 1, 0, "up", chest_loot("miner_cache"))
    b.set(1, 1, 0, B("barrel", facing="up"))
    b.set(0, 2, 0, B("barrel", facing="up"))
    b.pot(0, 1, 1, rng.choice(H4), ("brick", "miner_pottery_sherd", "brick", "brick"), loot=chest_loot("cave_pot"))
    b.set(1, 2, 0, B("cobweb"))
    b.set(2, 1, 0, B("cobweb"))
    patch(b, [(0, 2), (1, 1), (2, 1)], rng, TRODDEN, p=0.6)
    return b


def cold_camp(seed):
    """A camp left cold: a dead fire ringed with stones in a patch of ash, logs rolled up to sit on, the
    cooking pot, a lantern left burning on the floor."""
    rng = random.Random(seed)
    b = Build("cold_camp", 5, 2, 5, seed=seed)
    meal_fire(b, 2, 1, 2, foods=["cod", "potato"], lit=False, done=(300, 450, 0, 0))
    patch(b, [(x, z) for x in (1, 2, 3) for z in (1, 2, 3)], rng, ASH, p=0.8)
    for (x, z) in [(2, 1), (1, 2), (3, 2), (2, 3)]:
        if b.get(x, 0, z) is None or rng.random() < 0.5:
            b.set(x, 0, z, pick(rng, COBBLE))
    b.set(1, 1, 0, B("spruce_log", axis="x"))
    b.set(2, 1, 0, B("spruce_log", axis="x"))
    b.set(0, 1, 2, B("spruce_log", axis="z"))
    b.set(0, 1, 3, B("spruce_log", axis="z"))
    b.set(4, 1, 1, B("cauldron"))
    b.set(4, 1, 4, B("lantern"))
    lay(b, 3, 1, 4, "bowl", rotation=rng.randrange(8))
    return b


def cave_in(seed):
    """Where the roof came down on a working: a heap of gravel and broken rock, a prop snapped off in it, the
    lamp set down on the spoil, a shovel left stuck in it."""
    rng = random.Random(seed)
    b = Build("cave_in", 5, 4, 5, seed=seed)
    heap = {(x, z): max(0, 3 - round(math.hypot(x - 1.6, z - 1.8) * 1.15)) for x in range(5) for z in range(5)}
    for (x, z), h in heap.items():
        h = h - (1 if rng.random() < 0.25 else 0)
        if h <= 0:
            continue
        b.set(x, 0, z, pick(rng, [("gravel", 5), ("cobblestone", 2), ("andesite", 1)]))
        for y in range(1, h + 1):
            b.set(x, y, z, pick(rng, [("gravel", 6), ("cobblestone", 2), ("andesite", 1), ("tuff", 1)]))
    b.set(3, 1, 3, B("spruce_log", axis="x"))                       # the prop, snapped
    b.set(4, 1, 3, B("spruce_log", axis="x"))
    b.set(4, 2, 3, B("spruce_fence"))
    b.set(3, 1, 4, B("lantern"))
    lay(b, 4, 1, 1, "iron_shovel", rotation=2)
    return b


def rail_stub(seed, name="rail_stub", cart="chest_minecart"):
    """The end of a forgotten line: a few rails on gravel ballast running out of nowhere, a buffer of posts
    at the end, a cart left standing on them (some still loaded)."""
    rng = random.Random(seed)
    b = Build(name, 7, 3, 3, seed=seed)
    z = 1
    for x in range(0, 6):
        b.set(x, 0, z, pick(rng, BALLAST))
    for x in range(0, 6):
        if x == 1 and rng.random() < 0.6:
            continue                                                 # a rail lifted long ago
        b.set(x, 1, z, B("rail", shape="east_west"))
    b.set(6, 0, z, B("cobblestone"))
    b.set(6, 1, z, B("spruce_fence"))                               # the buffer
    b.set(6, 2, z, B("lantern"))
    patch(b, [(x, zz) for x in range(7) for zz in (0, 2)], rng, BALLAST, p=0.3)
    extra = {"LootTable": chest_loot("ore_cart")} if cart == "chest_minecart" else {}
    b._entity(cart, (4.5, 1.0625, z + 0.5), (4, 1, z), extra, yaw=90.0)
    return b


def ore_spill(seed):
    """A tub that tipped at the face: lumps of raw ore and coal across the floor, the pick, a lantern."""
    rng = random.Random(seed)
    b = Build("ore_spill", 5, 3, 4, seed=seed)
    b.set(1, 1, 1, B("raw_iron_block"))
    b.set(2, 1, 2, B(rng.choice(["coal_block", "raw_copper_block"])))
    for (x, z, it) in [(3, 1, "raw_iron"), (2, 1, "coal"), (3, 2, "raw_copper"), (1, 2, "coal"), (4, 2, "raw_iron")]:
        lay(b, x, 1, z, it, rotation=rng.randrange(8))
    lay(b, 0, 1, 2, "iron_pickaxe", rotation=1)
    b.set(4, 1, 0, B("lantern"))
    b.set(0, 1, 0, B("barrel", facing="east"))                       # the tub, on its side
    patch(b, [(x, z) for x in range(5) for z in range(4)], rng, [("gravel", 3), ("cobblestone", 1)], p=0.45)
    return b


def mushroom_patch(seed):
    """Mushrooms spreading in the dark over a patch of mycelium, and one grown huge, a cap over a pale stem."""
    rng = random.Random(seed)
    b = Build("mushroom_patch", 5, 4, 5, seed=seed)
    for (x, z) in disc(2, 2, 2.3, (5, 5)):
        if rng.random() < 0.85:
            b.set(x, 0, z, B(rng.choice(["mycelium", "mycelium", "podzol"])))
    stem = B("mushroom_stem", up=False, down=False)
    b.set(2, 1, 2, stem)
    b.set(2, 2, 2, stem)
    red = rng.random() < 0.5
    cap = "red_mushroom_block" if red else "brown_mushroom_block"
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            b.set(2 + dx, 3, 2 + dz, B(cap, down=False))
    for (x, z) in disc(2, 2, 2.3, (5, 5)):
        if (x, z) != (2, 2) and b.get(x, 0, z) is not None and b.get(x, 1, z) is None and rng.random() < 0.45:
            b.set(x, 1, z, B("brown_mushroom" if rng.random() < 0.6 else "red_mushroom"))
    return b


def crystal_pocket(seed):
    """A crust of amethyst broken open in the floor, buds and clusters catching the light."""
    rng = random.Random(seed)
    b = Build("crystal_pocket", 4, 3, 4, seed=seed)
    for (x, z) in disc(1.5, 1.5, 1.9, (4, 4)):
        b.set(x, 0, z, B("calcite") if math.hypot(x - 1.5, z - 1.5) > 1.2 else B("amethyst_block"))
    for (x, z) in [(1, 1), (2, 2), (1, 2)]:
        b.set(x, 1, z, B("amethyst_block"))
    for (x, z) in disc(1.5, 1.5, 2.3, (4, 4)):
        y = 2 if b.get(x, 1, z) is not None else 1
        if b.get(x, y, z) is None and b.get(x, y - 1, z) is not None and rng.random() < 0.6:
            bud = rng.choice(["amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud"])
            b.set(x, y, z, B(bud, facing="up"))
    return b


def spring_pool(seed):
    """A spring welling up in a ring of stones, mossed over; glow lichen on the stones lights the water."""
    rng = random.Random(seed)
    b = Build("spring_pool", 5, 3, 5, seed=seed)
    water = {(2, 2), (2, 1), (1, 2), (3, 2), (2, 3)}
    if rng.random() < 0.5:
        water.add((1, 1))
    mix = [("mossy_cobblestone", 4), ("stone", 2), ("cobblestone", 1), ("moss_block", 2)]
    for x in range(5):
        for z in range(5):
            if (x, z) in water:
                b.set(x, 0, z, B("water", level=0))
            elif any((x + dx, z + dz) in water for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
                b.set(x, 0, z, pick(rng, mix))
    for (x, z) in [(0, 2), (3, 0), (4, 3), (1, 4)]:
        if b.get(x, 0, z) is not None:
            b.set(x, 1, z, pick(rng, [("mossy_cobblestone", 3), ("stone", 1)]))
            b.set(x, 2, z, B("glow_lichen", down=True))
    for (x, z) in [(4, 1), (0, 3), (3, 4)]:
        if b.get(x, 0, z) is not None and b.get(x, 1, z) is None and rng.random() < 0.7:
            b.set(x, 1, z, B("moss_carpet"))
    if rng.random() < 0.3:
        b.set(2, 1, 2, B("lily_pad"))
    return b


def waystation(seed):
    """A rest under a roof of boards in a big cave: four posts, a slab roof, a lantern hung under it, a bench,
    supplies, and on the board a map of where the passages go (for whoever comes next)."""
    rng = random.Random(seed)
    b = Build("waystation", 5, 4, 5, seed=seed)
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        b.set(x, 0, z, B("cobblestone"))
        for y in (1, 2):
            b.set(x, y, z, B("spruce_log", axis="y"))
    for x in range(5):
        for z in range(5):
            b.set(x, 3, z, slab("spruce"))
    b.set(2, 2, 2, B("lantern", hanging=True))
    for x in (1, 2, 3):
        b.set(x, 1, 0, stairs("spruce", "south"))                   # the bench along the back
    b.set(0, 1, 2, B("cartography_table"))
    b.barrel(4, 1, 2, "up", chest_loot("waystation"))
    b.set(4, 1, 3, B("barrel", facing="up"))
    lay(b, 2, 1, 3, journal("survey"), rotation=0)
    patch(b, [(x, z) for x in range(5) for z in range(5)], rng, TRODDEN, p=0.5)
    return b


def forge(seed):
    """A smithy dug into the deep: a hearth of stone with a furnace and a blast furnace under a little stack, a
    pot of lava for the heat, an anvil, the quenching tub, the grindstone, the smith's barrel."""
    rng = random.Random(seed)
    b = Build("forge", 7, 4, 5, seed=seed)
    for x in range(7):
        for z in range(5):
            if rng.random() < 0.85:
                b.set(x, 0, z, pick(rng, [("cobblestone", 3), ("stone_bricks", 2), ("smooth_stone", 1),
                                         ("cracked_stone_bricks", 1)]))
    b.set(0, 1, 0, B("cobblestone"))
    b.set(1, 1, 0, B("furnace", facing="south"))
    b.set(2, 1, 0, B("blast_furnace", facing="south"))
    b.set(3, 1, 0, B("cobblestone"))
    for x in range(4):
        b.set(x, 2, 0, B("cobblestone_wall") if x in (0, 3) else B("cobblestone"))
    b.set(1, 3, 0, B("cobblestone_wall"))
    b.set(2, 3, 0, B("cobblestone_wall"))
    b.set(0, 3, 0, B("lantern"))
    b.set(4, 1, 0, B("lava_cauldron"))
    b.set(3, 1, 2, B("chipped_anvil", facing="east"))
    b.set(5, 1, 1, B("water_cauldron", level=2))
    b.set(6, 1, 3, B("grindstone", face="floor", facing="north"))
    b.barrel(6, 1, 0, "up", chest_loot("smithy"))
    b.set(6, 2, 0, B("barrel", facing="up"))
    b.set(0, 1, 3, B("smithing_table"))
    lay(b, 2, 1, 3, "iron_ingot", rotation=2)
    lay(b, 4, 1, 3, "iron_nugget", rotation=1)
    return b


def spider_den(seed):
    """A hollow the cave spiders took over from someone: webs in every corner, a spawner in the nest, bones,
    and the chest they never needed."""
    rng = random.Random(seed)
    b = Build("spider_den", 7, 4, 7, seed=seed)
    for (x, z) in disc(3, 3, 3.2, (7, 7)):
        if rng.random() < 0.7:
            b.set(x, 0, z, pick(rng, [("mossy_cobblestone", 3), ("cobblestone", 2), ("gravel", 1)]))
    b.spawner(3, 1, 3, "cave_spider", min_delay=300, max_delay=900, count=3, max_nearby=5, player_range=12)
    b.chest(3, 1, 6, "north", chest_loot("spider_den"))
    for (x, z) in disc(3, 3, 3.2, (7, 7)):
        if (x, z) in ((3, 3), (3, 6), (3, 5)):
            continue
        for y in (1, 2, 3):
            r = 0.35 if y == 1 else 0.3 if y == 2 else 0.25
            if b.get(x, y, z) is None and rng.random() < r * (0.6 + 0.4 * math.hypot(x - 3, z - 3) / 3):
                b.set(x, y, z, B("cobweb"))
    b.set(1, 1, 5, B("skeleton_skull", rotation=rng.randrange(16)))
    b.set(5, 1, 1, B("bone_block", axis="z"))
    if b.get(2, 1, 5) is None:
        lay(b, 2, 1, 5, "string", rotation=rng.randrange(8))
    return b


def cave_shrine(seed):
    """An old shrine in the rock: a dais of worked stone, an altar with a skull and candles, soul lanterns on
    two pillars, a kneeling cushion, offerings; under the altar stone, what the pilgrims left."""
    rng = random.Random(seed)
    b = Build("cave_shrine", 5, 4, 5, seed=seed)
    for x in range(5):
        for z in range(5):
            if (x in (0, 4) and z in (0, 4)) or rng.random() < 0.9:
                b.set(x, 0, z, pick(rng, [("stone_bricks", 4), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1),
                                         ("chiseled_stone_bricks", 1)]))
    b.chest(2, 0, 1, "south", chest_loot("shrine"))                 # under the altar stone
    b.set(1, 1, 1, B("polished_andesite"))
    b.set(2, 1, 1, B("chiseled_stone_bricks"))
    b.set(3, 1, 1, B("polished_andesite"))
    b.set(1, 2, 1, candles(rng.randint(2, 4)))
    b.set(2, 2, 1, B("skeleton_skull", rotation=0))
    b.set(3, 2, 1, candles(rng.randint(1, 3)))
    for x in (0, 4):
        b.set(x, 1, 0, B("stone_brick_wall"))
        b.set(x, 2, 0, B("stone_brick_wall"))
        b.set(x, 3, 0, B("soul_lantern"))
    b.cushion(2, 1, 3, rng.choice(["red", "purple", "gray"]))
    lay(b, 1, 1, 2, "gold_nugget", rotation=rng.randrange(8))
    lay(b, 3, 1, 2, "emerald", rotation=rng.randrange(8))
    return b


def prospector_camp(seed):
    """A prospector's camp: a pup tent with the bedroll inside, a fire burning in its ring, a crate of samples,
    the lantern on its post, a cart wheel against the tent."""
    rng = random.Random(seed)
    b = Build("prospector_camp", 7, 4, 7, seed=seed)
    inside = a_tent(b, 0, 1, 4, "z", rng.choice(["brown", "white", "light_gray", "green"]))
    bedroll(b, inside[0][0], 1, inside[0][1], "south")
    campfire_ring(b, 4, 4, rng, lit=True)
    for (x, z) in [(5, 3), (3, 3), (5, 5), (3, 5)]:
        if b.get(x, 0, z) is None and rng.random() < 0.5:
            b.set(x, 0, z, pick(rng, ASH))
    b.set(6, 1, 4, B("spruce_log", axis="z"))
    b.set(6, 1, 5, B("spruce_log", axis="z"))
    b.barrel(4, 1, 0, "up", chest_loot("prospector"))
    b.set(5, 1, 0, B("barrel", facing="up"))
    post_lantern(b, 6, 1, rng)
    lay(b, 4, 1, 6, "iron_shovel", rotation=1)
    lay(b, 3, 1, 1, "raw_gold", rotation=rng.randrange(8))
    return b


def lost_explorer(seed):
    """The last camp of someone who went too deep: a bedroll, their chest, a lantern still burning, the
    journal open on the floor, and themselves."""
    rng = random.Random(seed)
    b = Build("lost_explorer", 6, 3, 4, seed=seed)
    patch(b, [(x, z) for x in range(6) for z in range(4)], rng, TRODDEN, p=0.4)
    bedroll(b, 1, 1, 1, "east", "brown")
    b.set(0, 1, 2, B("skeleton_skull", rotation=rng.randrange(16)))
    lay(b, 1, 1, 2, "bone", rotation=2)
    b.chest(4, 1, 1, "west", chest_loot("explorer"))
    b.set(5, 1, 2, B("lantern"))
    lay(b, 3, 1, 2, journal("last_entry"), rotation=0)
    lay(b, 2, 1, 2, "compass", rotation=rng.randrange(8))
    b.set(4, 1, 3, B("barrel", facing="north"))
    return b


# ============================================================== the caves under each kind of country
def frozen_camp(seed):
    """A camp the cold took: snow over everything, the fire long dead, a sled on its side, the chest frozen in
    behind a wall of ice, a frostbloom glowing in the drift."""
    rng = random.Random(seed)
    b = Build("frozen_camp", 5, 3, 5, seed=seed)
    for (x, z) in disc(2, 2, 2.5, (5, 5)):
        if rng.random() < 0.8:
            b.set(x, 0, z, B(rng.choice(["snow_block", "snow_block", "packed_ice"])))
    b.campfire(2, 1, 2, lit=False)
    b.set(0, 1, 1, B("spruce_trapdoor", facing="east", half="bottom"))   # the sled
    b.set(1, 1, 1, B("spruce_trapdoor", facing="west", half="bottom"))
    b.set(0, 1, 0, B("spruce_fence"))
    b.chest(4, 1, 4, "west", chest_loot("frozen_cache"))
    b.set(4, 1, 3, B("blue_ice"))
    b.set(3, 1, 4, B("packed_ice"))
    b.set(4, 2, 4, B("packed_ice"))
    b.set(0, 1, 4, B("skeleton_skull", rotation=rng.randrange(16)))
    for (x, z) in disc(2, 2, 2.5, (5, 5)):
        if b.get(x, 1, z) is None and b.get(x, 0, z) is not None and rng.random() < 0.5:
            b.set(x, 1, z, B("snow", layers=rng.choice([1, 1, 2])))
    b.set(3, 1, 0, B("expanse:frostbloom"))
    b.set(3, 0, 0, B("snow_block"))
    return b


def ice_shrine(seed):
    """A pillar of blue ice someone set up in the dark, a soul lantern on top, frostblooms planted round it."""
    rng = random.Random(seed)
    b = Build("ice_shrine", 3, 5, 3, seed=seed)
    for (x, z) in [(0, 1), (1, 0), (1, 1), (2, 1), (1, 2)]:
        b.set(x, 0, z, B("packed_ice"))
    b.set(1, 1, 1, B("blue_ice"))
    b.set(1, 2, 1, B("packed_ice"))
    b.set(1, 3, 1, B("blue_ice"))
    b.set(1, 4, 1, B("soul_lantern"))
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        b.set(x, 0, z, B("snow_block"))
        b.set(x, 1, z, B("expanse:frostbloom") if rng.random() < 0.7 else B("snow", layers=1))
    return b


def sand_shrine(seed):
    """A desert people's shrine carried underground: a sandstone plinth with a skull between candles, pots of
    offerings at its foot, sand drifted over the steps."""
    rng = random.Random(seed)
    b = Build("sand_shrine", 5, 4, 5, seed=seed)
    for x in range(5):
        for z in range(5):
            if rng.random() < 0.85:
                b.set(x, 0, z, pick(rng, [("sand", 3), ("sandstone", 2), ("smooth_sandstone", 1)]))
    b.set(1, 1, 1, B("cut_sandstone"))
    b.set(2, 1, 1, B("chiseled_sandstone"))
    b.set(3, 1, 1, B("cut_sandstone"))
    b.set(2, 2, 1, B("skeleton_skull", rotation=0))
    b.set(1, 2, 1, candles(2, "orange"))
    b.set(3, 2, 1, candles(3, "orange"))
    b.set(2, 1, 2, stairs("sandstone", "north"))
    b.pot(0, 1, 3, "east", ("brick", "archer_pottery_sherd", "brick", "brick"), loot=chest_loot("cave_pot"))
    b.pot(4, 1, 3, "west", ("brick", "brick", "skull_pottery_sherd", "brick"))
    b.set(0, 1, 0, B("sandstone_wall"))
    b.set(0, 2, 0, B("lantern"))
    b.set(4, 1, 0, B("sandstone_wall"))
    b.set(4, 2, 0, B("lantern"))
    return b


def pot_cache(seed):
    """Three pots stood together in the sand, as the old desert peoples kept their stores: one still sealed."""
    rng = random.Random(seed)
    b = Build("pot_cache", 3, 2, 3, seed=seed)
    for x in range(3):
        for z in range(3):
            if rng.random() < 0.7:
                b.set(x, 0, z, B("sand"))
    sherds = ["archer", "prize", "skull", "arms_up", "danger", "heart"]
    b.pot(0, 1, 0, rng.choice(H4), ("brick", f"{rng.choice(sherds)}_pottery_sherd", "brick", "brick"),
          loot=chest_loot("cave_pot"))
    b.pot(1, 1, 0, rng.choice(H4), ("brick", "brick", "brick", "brick"))
    b.pot(0, 1, 1, rng.choice(H4), (f"{rng.choice(sherds)}_pottery_sherd", "brick", "brick", "brick"),
          loot=chest_loot("cave_pot"))
    b.brushable(2, 0, 2, "sand", chest_loot("dry_archaeology"))
    return b


def dry_bones(seed):
    """Bones of some great beast half sunk in the cave sand, sand to brush round them."""
    rng = random.Random(seed)
    b = Build("dry_bones", 6, 3, 4, seed=seed)
    for x in range(6):
        for z in range(4):
            if rng.random() < 0.6:
                b.set(x, 0, z, B("sand"))
    for x in range(1, 5):
        b.set(x, 0, 2, B("bone_block", axis="x"))
    for x in (1, 3):
        for zz in (1, 3):
            b.set(x, 1, zz, B("bone_block", axis="y"))
    b.set(3, 2, 1, B("bone_block", axis="z"))
    b.set(5, 1, 2, B("skeleton_skull", rotation=rng.randrange(16)))
    b.brushable(2, 0, 1, "sand", chest_loot("dry_archaeology"))
    b.brushable(4, 0, 3, "sand", chest_loot("dry_archaeology"))
    return b


def moss_shrine(seed):
    """A shrine the moss has taken back: a mossy plinth and candles, an azalea grown up beside it, moss over
    the flagstones, a lantern on a stub of wall."""
    rng = random.Random(seed)
    b = Build("moss_shrine", 5, 4, 5, seed=seed)
    for x in range(5):
        for z in range(5):
            if rng.random() < 0.85:
                b.set(x, 0, z, pick(rng, [("mossy_stone_bricks", 3), ("moss_block", 3), ("mossy_cobblestone", 1)]))
    b.set(2, 1, 1, B("mossy_stone_bricks"))
    b.set(1, 1, 1, slab("mossy_stone_brick"))
    b.set(3, 1, 1, slab("mossy_stone_brick"))
    b.set(2, 2, 1, candles(4, "green"))
    b.set(0, 0, 3, B("moss_block"))
    b.set(0, 1, 3, B("flowering_azalea"))
    b.set(4, 1, 0, B("mossy_stone_brick_wall"))
    b.set(4, 2, 0, B("lantern"))
    for (x, z) in [(1, 3), (3, 3), (2, 4), (4, 2)]:
        if b.get(x, 0, z) is not None and b.get(x, 1, z) is None and rng.random() < 0.6:
            b.set(x, 1, z, B("moss_carpet"))
    return b


def overgrown_cart(seed):
    """A cart left so long on a dead line that the moss has grown over it and an azalea out of its ballast."""
    rng = random.Random(seed)
    b = Build("overgrown_cart", 6, 3, 3, seed=seed)
    z = 1
    for x in range(6):
        b.set(x, 0, z, B(rng.choice(["moss_block", "gravel", "moss_block"])))
    for x in (0, 2, 3, 4):
        b.set(x, 1, z, B("rail", shape="east_west"))
    b.set(1, 1, z, B("moss_carpet"))
    b.set(5, 1, z, B("azalea"))
    for x in range(6):
        for zz in (0, 2):
            if rng.random() < 0.5:
                b.set(x, 0, zz, B("moss_block"))
                if rng.random() < 0.5:
                    b.set(x, 1, zz, B("moss_carpet"))
    b._entity("chest_minecart", (3.5, 1.0625, z + 0.5), (3, 1, z), {"LootTable": chest_loot("ore_cart")}, yaw=90.0)
    return b


def drip_cauldrons(seed, name="drip_cauldrons", n=3):
    """Cauldrons stood under the drips to catch the water, a bucket, a barrel; the stalagmites growing round."""
    rng = random.Random(seed)
    b = Build(name, 5, 3, 5, seed=seed)
    for (x, z) in disc(2, 2, 2.4, (5, 5)):
        if rng.random() < 0.7:
            b.set(x, 0, z, B(rng.choice(["dripstone_block", "dripstone_block", "calcite"])))
    spots = [(1, 1), (3, 2), (1, 3), (3, 4), (2, 0)][:n]
    for (x, z) in spots:
        b.set(x, 1, z, B("water_cauldron", level=rng.randint(1, 3)) if rng.random() < 0.8 else B("cauldron"))
    b.barrel(4, 1, 0, "up", chest_loot("miner_cache"))
    lay(b, 2, 1, 2, "bucket", rotation=rng.randrange(8))
    for (x, z) in [(0, 2), (4, 3), (2, 4)]:
        if b.get(x, 1, z) is None:
            b.set(x, 0, z, B("dripstone_block"))
            b.set(x, 1, z, B("pointed_dripstone", thickness="tip", vertical_direction="up"))
    b.set(0, 1, 0, B("lantern"))
    return b


def flowstone_shrine(seed):
    """Candles burning on a ledge of flowstone, offerings of amethyst laid at its foot."""
    rng = random.Random(seed)
    b = Build("flowstone_shrine", 4, 4, 4, seed=seed)
    for (x, z) in disc(1.5, 1.5, 2.0, (4, 4)):
        b.set(x, 0, z, B(rng.choice(["calcite", "dripstone_block"])))
    b.set(1, 1, 1, B("calcite"))
    b.set(2, 1, 1, B("dripstone_block"))
    b.set(1, 2, 1, B("calcite"))
    b.set(1, 3, 1, candles(3, "white"))
    b.set(2, 2, 1, candles(2, "white"))
    lay(b, 1, 1, 2, "amethyst_shard", rotation=rng.randrange(8))
    lay(b, 2, 1, 2, "amethyst_shard", rotation=rng.randrange(8))
    b.set(3, 1, 3, B("pointed_dripstone", thickness="tip", vertical_direction="up"))
    return b


def echo_arch(seed):
    """A fragment of the deep builders' halls: the stump of an arch in deepslate tile, its lintel half fallen,
    soul lanterns still burning, sculk creeping over the flags."""
    rng = random.Random(seed)
    b = Build("echo_arch", 7, 6, 3, seed=seed)
    z = 1
    for x in range(7):
        for zz in range(3):
            if rng.random() < 0.75:
                b.set(x, 0, zz, pick(rng, [("deepslate_tiles", 3), ("cracked_deepslate_tiles", 2),
                                          ("polished_deepslate", 1), ("sculk", 2)]))
    for y in range(1, 5):
        b.set(0, y, z, B("deepslate_tiles") if y < 4 else B("chiseled_deepslate"))
    for y in range(1, 3):
        b.set(6, y, z, B("cracked_deepslate_tiles"))
    b.set(0, 5, z, B("deepslate_tiles"))
    b.set(1, 5, z, B("deepslate_tiles"))
    b.set(2, 5, z, B("deepslate_tiles"))
    b.set(3, 5, z, B("deepslate_tile_slab", type="bottom"))       # where the lintel broke off
    b.set(1, 4, z, B("soul_lantern", hanging=True))
    b.set(5, 1, z, B("deepslate_tile_stairs", facing="west"))      # fallen pieces of the lintel
    b.set(4, 1, 0, B("deepslate_tiles"))
    b.set(6, 3, z, B("soul_lantern"))
    b.set(3, 1, 2, candles(2, "light_blue"))
    for x in range(7):
        for zz in range(3):
            if b.get(x, 1, zz) is None and b.get(x, 0, zz) is not None and rng.random() < 0.15:
                b.set(x, 1, zz, B("sculk_vein", down=True))
    return b


def soul_altar(seed):
    """A dais of polished deepslate round a soul fire, skulls set about it, candles: whatever was worshipped
    down here still has a light kept for it."""
    rng = random.Random(seed)
    b = Build("soul_altar", 5, 3, 5, seed=seed)
    for x in range(5):
        for z in range(5):
            edge = x in (0, 4) or z in (0, 4)
            if not (edge and x in (0, 4) and z in (0, 4)):
                b.set(x, 0, z, pick(rng, [("polished_deepslate", 3), ("deepslate_bricks", 2),
                                         ("cracked_deepslate_bricks", 1)]))
    b.campfire(2, 1, 2, lit=True, soul=True)
    for (x, z, r) in [(2, 0, 8), (0, 2, 4), (4, 2, 12)]:
        b.set(x, 1, z, B("skeleton_skull", rotation=r))
    b.set(2, 1, 4, candles(4, "black"))
    b.set(1, 1, 1, candles(1, "black"))
    b.set(3, 1, 3, candles(2, "black"))
    return b


def sulfur_camp(seed):
    """Where the sulfur diggers worked the seep: a cart on a stub of rail, sulfur heaped by it, barrels, a
    tub of water against the fumes, the lamp."""
    rng = random.Random(seed)
    b = Build("sulfur_camp", 6, 3, 5, seed=seed)
    for x in range(6):
        b.set(x, 0, 1, pick(rng, BALLAST))
    for x in range(1, 5):
        b.set(x, 1, 1, B("rail", shape="east_west"))
    b._entity("chest_minecart", (2.5, 1.0625, 1.5), (2, 1, 1), {"LootTable": chest_loot("sulfur_cart")}, yaw=90.0)
    for (x, z, h) in [(1, 3, 2), (2, 3, 1), (1, 4, 1)]:
        for y in range(1, h + 1):
            b.set(x, y, z, B("sulfur"))
    b.barrel(4, 1, 3, "up", chest_loot("miner_cache"))
    b.set(5, 1, 3, B("barrel", facing="up"))
    b.set(5, 1, 4, B("water_cauldron", level=3))
    b.set(0, 1, 3, B("sulfur_brick_wall"))
    b.set(0, 2, 3, B("lantern"))
    patch(b, [(x, z) for x in range(6) for z in (2, 3, 4)], rng, [("gravel", 2), ("sulfur", 1)], p=0.3)
    return b


# ============================================================== the realms
def waystone(seed, name="waystone", mix=COBBLE, cap="stone_brick_slab", lantern="lantern"):
    """A tall stacked-stone marker with a lantern on top, set up by the explorers to find their way across a
    realm in the dark: seen far off, a point of light on the hills."""
    rng = random.Random(seed)
    b = Build(name, 3, 8, 3, seed=seed)
    for (x, z) in [(0, 1), (1, 0), (1, 1), (2, 1), (1, 2), (0, 0), (2, 2), (2, 0), (0, 2)]:
        if rng.random() < 0.8 or (x, z) == (1, 1):
            b.set(x, 0, z, pick(rng, mix))
    for (x, z) in [(0, 1), (1, 0), (1, 1), (2, 1), (1, 2)]:
        if (x, z) == (1, 1) or rng.random() < 0.85:
            b.set(x, 1, z, pick(rng, mix))
    b.set(1, 2, 1, pick(rng, mix))
    side = rng.choice([(0, 1), (2, 1), (1, 0), (1, 2)])
    if b.get(side[0], 1, side[1]) is not None:
        b.set(side[0], 2, side[1], B(cap, type="bottom"))
    for y in (3, 4, 5):
        b.set(1, y, 1, pick(rng, mix))
    b.set(1, 6, 1, B(cap, type="bottom"))
    b.set(1, 7, 1, B(lantern))
    return b


def explorer_camp(seed):
    """A base camp of the expedition that mapped the realms: a big tent with two bedrolls, the fire, the map
    table, a flag on its pole, supplies, lanterns at the corners."""
    rng = random.Random(seed)
    b = Build("explorer_camp", 9, 4, 7, seed=seed)
    inside = a_tent(b, 0, 1, 4, "z", rng.choice(["white", "light_gray", "brown"]), wide=True)
    bedroll(b, inside[0][0], 1, inside[0][1], "south", "brown")
    bedroll(b, inside[2][0], 1, inside[2][1], "south", "green")
    campfire_ring(b, 6, 4, rng, lit=True)
    b.set(8, 1, 3, B("spruce_log", axis="z"))
    b.set(8, 1, 4, B("spruce_log", axis="z"))
    b.set(6, 1, 1, B("cartography_table"))
    lay(b, 7, 1, 1, journal("survey"), rotation=0)
    b.barrel(8, 1, 0, "up", chest_loot("waystation"))
    b.set(8, 2, 0, B("barrel", facing="up"))
    b.chest(5, 1, 0, "south", chest_loot("explorer"))
    b.set(5, 1, 6, B("spruce_fence"))
    b.set(5, 2, 6, B("spruce_fence"))
    b.banner(5, 3, 6, rng.choice(["blue", "red", "yellow"]), [("stripe_center", "white"), ("border", "black")],
             rotation=8)
    post_lantern(b, 0, 6, rng)
    post_lantern(b, 8, 6, rng)
    patch(b, [(x, z) for x in range(4, 9) for z in range(2, 7)], rng, TRODDEN, p=0.35)
    return b


def survey_tower(seed):
    """A lookout of poles the surveyors raised in the big caves: four posts, a ladder up a corner log, a plank
    floor at the top with a lantern, a rail round it."""
    rng = random.Random(seed)
    b = Build("survey_tower", 5, 9, 5, seed=seed)
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        b.set(x, 0, z, B("cobblestone"))
        for y in range(1, 7):
            b.set(x, y, z, B("spruce_log", axis="y") if (x, z) == (0, 0) else B("spruce_fence"))
    for y in range(1, 7):
        b.set(0, y, 1, B("ladder", facing="south"))
    for x in range(5):
        for z in range(5):
            if (x, z) != (0, 1) and b.get(x, 6, z) is None:
                b.set(x, 6, z, slab("spruce", "top"))
    b.set(0, 6, 1, B("ladder", facing="south"))
    for x in range(5):
        for z in range(5):
            if (x in (0, 4) or z in (0, 4)) and (x, z) != (0, 1):
                b.set(x, 7, z, B("spruce_fence"))
    b.set(2, 7, 2, B("lantern"))
    b.set(4, 8, 4, B("lantern"))
    b.set(0, 1, 2, B("barrel", facing="up"))
    patch(b, [(x, z) for x in range(5) for z in range(5)], rng, TRODDEN, p=0.4)
    return b


def forager_hut(seed):
    """A forager's hut under the giant mushrooms, made of them: walls of stem, a domed red cap for a roof, a
    door, a shroomlight to see by, baskets of what grows down here."""
    rng = random.Random(seed)
    b = Build("forager_hut", 7, 6, 7, seed=seed)
    c = 3
    for x in range(7):
        for z in range(7):
            d = math.hypot(x - c, z - c)
            if d <= 3.2 and rng.random() < 0.9:
                b.set(x, 0, z, B("mycelium") if d > 2.2 else B(rng.choice(["spruce_planks", "mushroom_stem"])))
    stem = B("mushroom_stem", up=False, down=False)
    for x in range(1, 6):
        for z in range(1, 6):
            d = math.hypot(x - c, z - c)
            if 1.6 < d <= 2.3:
                for y in (1, 2):
                    b.set(x, y, z, stem)
            elif d <= 1.6:
                for y in (1, 2):
                    b.set(x, y, z, AIR)
    b.door(c, 1, 5, "spruce", "south")
    b.set(c, 1, 6, AIR)
    for x in range(7):                                                  # the cap
        for z in range(7):
            d = math.hypot(x - c, z - c)
            if d <= 3.4:
                b.set(x, 3, z, B("red_mushroom_block", down=d > 2.3 or d < 1.1))
            if d <= 2.4:
                b.set(x, 4, z, B("red_mushroom_block", down=False))
            if d <= 1.2:
                b.set(x, 5, z, B("red_mushroom_block", down=False))
    for (x, z) in [(2, 2), (4, 2), (2, 4), (4, 4), (3, 2), (2, 3), (4, 3), (3, 3), (3, 4)]:
        if b.get(x, 3, z) is not None and math.hypot(x - c, z - c) < 1.6:
            b.set(x, 3, z, B("red_mushroom_block", up=False))
    b.set(c, 3, c, B("shroomlight"))
    b.set(2, 1, 2, B("barrel", facing="up"))
    b.barrel(4, 1, 2, "up", chest_loot("forager"))
    b.set(2, 1, 3, slab("spruce", "top"))
    for (x, z) in [(0, 2), (6, 4), (1, 6), (5, 0), (0, 5), (6, 1)]:
        if b.get(x, 0, z) is not None and b.get(x, 1, z) is None:
            b.set(x, 1, z, B(rng.choice(["brown_mushroom", "red_mushroom", "expanse:glowcap"])))
    return b


def mushroom_ring(seed):
    """A fairy ring of glowcaps and mushrooms round a stump someone sat on to watch them."""
    rng = random.Random(seed)
    b = Build("mushroom_ring", 7, 2, 7, seed=seed)
    for (x, z) in disc(3, 3, 3.2, (7, 7)):
        d = math.hypot(x - 3, z - 3)
        if 2.0 <= d <= 3.1:
            b.set(x, 0, z, B("mycelium"))
            if rng.random() < 0.75:
                b.set(x, 1, z, B(rng.choice(["expanse:glowcap", "expanse:glowcap", "brown_mushroom", "red_mushroom"])))
    b.set(3, 0, 3, B("mushroom_stem", up=False, down=False))
    b.set(3, 1, 3, B("mushroom_stem", up=False, down=False))
    return b


def moss_gazebo(seed):
    """A pavilion of mossy stone in the green hollow: four columns, a slab roof furred with moss and azalea, a
    bench, a lantern hanging in the middle."""
    rng = random.Random(seed)
    b = Build("moss_gazebo", 7, 6, 7, seed=seed)
    for x in range(7):
        for z in range(7):
            if 1 <= x <= 5 and 1 <= z <= 5:
                b.set(x, 0, z, pick(rng, [("mossy_stone_bricks", 3), ("stone_bricks", 1), ("moss_block", 1)]))
    for (x, z) in [(1, 1), (5, 1), (1, 5), (5, 5)]:
        for y in (1, 2, 3):
            b.set(x, y, z, B("mossy_stone_brick_wall") if y < 3 else B("mossy_stone_bricks"))
    for x in range(7):
        for z in range(7):
            if 0 <= x <= 6 and 0 <= z <= 6 and not (x in (0, 6) and z in (0, 6)):
                b.set(x, 4, z, slab("mossy_stone_brick") if (x in (0, 6) or z in (0, 6)) else B("mossy_stone_bricks"))
    for x in range(2, 5):
        for z in range(2, 5):
            b.set(x, 5, z, B("moss_block") if (x, z) != (3, 3) else B("flowering_azalea"))
    b.set(3, 3, 3, B("lantern", hanging=True))
    for x in (2, 3, 4):
        b.set(x, 1, 1, stairs("mossy_stone_brick", "south"))
    for (x, z) in [(0, 2), (6, 4), (2, 6), (6, 1)]:
        b.set(x, 0, z, B("moss_block"))
        b.set(x, 1, z, B("azalea") if rng.random() < 0.5 else B("moss_carpet"))
    return b


def crystal_prospector(seed):
    """A crystal-hunter's camp: a pup tent, heaps of what they chipped from the vault, a spyglass laid on the
    crate, a lamp of glowing stone behind tinted glass."""
    rng = random.Random(seed)
    b = Build("crystal_prospector", 6, 4, 6, seed=seed)
    inside = a_tent(b, 0, 1, 4, "z", "purple")
    bedroll(b, inside[0][0], 1, inside[0][1], "south", "purple")
    patch(b, [(x, z) for x in range(6) for z in range(6)], rng, [("calcite", 2), ("gravel", 2), ("tuff", 1)], p=0.35)
    b.set(4, 1, 1, B("amethyst_block"))
    b.set(4, 2, 1, B("amethyst_cluster", facing="up"))
    b.set(5, 1, 1, B("calcite"))
    b.set(5, 2, 1, B("expanse:prismite_cluster", facing="up"))
    b.set(4, 1, 0, B("expanse:prismite_block"))
    b.barrel(5, 1, 3, "up", chest_loot("crystal_cache"))
    lay(b, 4, 1, 3, "spyglass", rotation=1)
    b.set(4, 1, 5, B("tinted_glass"))
    b.set(5, 1, 5, B("lantern"))
    for (x, z) in [(3, 4), (5, 4), (3, 2)]:
        lay(b, x, 1, z, "amethyst_shard", rotation=rng.randrange(8))
    return b


def crystal_altar(seed):
    """A plinth of calcite crowned with a cluster of prismite, candles set round it."""
    rng = random.Random(seed)
    b = Build("crystal_altar", 3, 5, 3, seed=seed)
    for x in range(3):
        for z in range(3):
            b.set(x, 0, z, B("calcite") if (x + z) % 2 else B("polished_diorite"))
    b.set(1, 1, 1, B("calcite"))
    b.set(1, 2, 1, B("expanse:prismite_block"))
    b.set(1, 3, 1, B("expanse:prismite_cluster", facing="up"))
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        b.set(x, 1, z, candles(rng.randint(1, 3), "purple"))
    return b


def obsidian_altar(seed):
    """An altar of blackstone and crying obsidian on the shore of the lava, gold set into its steps, soul
    lanterns on chains; whoever kept it went into the fire or out of the deep."""
    rng = random.Random(seed)
    b = Build("obsidian_altar", 5, 4, 5, seed=seed)
    for x in range(5):
        for z in range(5):
            b.set(x, 0, z, pick(rng, [("polished_blackstone_bricks", 3), ("blackstone", 2), ("gilded_blackstone", 1)]))
    b.set(2, 1, 1, B("crying_obsidian"))
    b.set(1, 1, 1, B("obsidian"))
    b.set(3, 1, 1, B("obsidian"))
    b.set(2, 2, 1, B("gilded_blackstone"))
    b.set(2, 3, 1, candles(4, "red"))
    b.set(2, 1, 2, stairs("polished_blackstone_brick", "north"))
    for x in (0, 4):
        b.set(x, 1, 0, B("blackstone_wall"))
        b.set(x, 2, 0, B("blackstone_wall"))
        b.set(x, 3, 0, B("soul_lantern"))
    b.chest(0, 1, 4, "east", chest_loot("ember_cache"))
    return b


def basalt_ruin(seed):
    """The stumps of a colonnade of polished basalt, one column fallen across the floor, magma in the cracks."""
    rng = random.Random(seed)
    b = Build("basalt_ruin", 7, 5, 5, seed=seed)
    for x in range(7):
        for z in range(5):
            if rng.random() < 0.7:
                b.set(x, 0, z, pick(rng, [("polished_blackstone_bricks", 2), ("basalt", 2), ("smooth_basalt", 2),
                                         ("magma_block", 1)]))
    for (x, z, h) in [(0, 0, 4), (3, 0, 2), (6, 0, 3), (0, 4, 1)]:
        for y in range(1, h + 1):
            b.set(x, y, z, B("polished_basalt", axis="y"))
    for x in range(2, 6):
        b.set(x, 1, 3, B("polished_basalt", axis="x"))
    b.set(6, 4, 0, B("soul_lantern"))
    return b


def beached_boat(seed, name="beached_boat", wood="oak", ground=("gravel", "gravel", "sand")):
    """A ferry boat drawn up on the shore of the underground lake and left: a planked hull, pointed at bow and
    stern, settled into the shingle; a thwart across it, the mast stump, a barrel still aboard, a lantern on
    the gunwale."""
    rng = random.Random(seed)
    b = Build(name, 8, 3, 4, seed=seed)
    planks = B(f"{wood}_planks")
    for x in range(8):                                                  # the shingle it lies in
        for z in range(4):
            if rng.random() < 0.6:
                b.set(x, 0, z, B(rng.choice(ground)))
    for x in range(1, 7):                                               # the bottom boards
        for z in (1, 2):
            b.set(x, 0, z, planks)
    for x in range(2, 6):                                               # the sides
        b.set(x, 0, 0, planks)
        b.set(x, 0, 3, planks)
        b.set(x, 1, 0, planks)
        b.set(x, 1, 3, planks)
    for z in (1, 2):                                                    # stem and stern, pointed
        b.set(1, 1, z, stairs(wood, "west"))
        b.set(6, 1, z, stairs(wood, "east"))
    for x in range(2, 6):
        for z in (1, 2):
            b.set(x, 1, z, AIR)
    b.set(3, 1, 1, slab(wood))                                          # the thwart
    b.set(3, 1, 2, slab(wood))
    b.set(4, 1, 1, B(f"{wood}_fence"))                                  # the mast stump
    b.set(4, 2, 1, B(f"{wood}_fence"))
    b.barrel(5, 1, 2, "up", chest_loot("ferry"))
    b.set(2, 2, 0, B("lantern"))
    lay(b, 2, 1, 2, "stick", rotation=1)
    return b


def fisher_camp(seed):
    """A fisher's camp by the dark water: a drying rack of poles hung with kelp, a barrel of the catch, the rod
    laid by a stool, a lantern."""
    rng = random.Random(seed)
    b = Build("fisher_camp", 5, 4, 4, seed=seed)
    for x in (0, 3):
        b.set(x, 0, 0, B("gravel"))
        b.set(x, 1, 0, B("spruce_fence"))
        b.set(x, 2, 0, B("spruce_fence"))
    for x in (1, 2):
        b.set(x, 2, 0, B("spruce_fence"))
        b.set(x, 1, 0, B("dried_kelp_block"))
    b.barrel(4, 1, 2, "up", chest_loot("ferry"))
    b.set(1, 1, 2, stairs("spruce", "north"))
    lay(b, 2, 1, 2, "fishing_rod", rotation=1)
    lay(b, 2, 1, 3, "cod", rotation=rng.randrange(8))
    b.set(0, 1, 3, B("lantern"))
    patch(b, [(x, z) for x in range(5) for z in range(4)], rng, [("gravel", 3), ("sand", 2)], p=0.5)
    return b


def snow_hut(seed):
    """An igloo of snow blocks raised in the frozen hollow: a low door, a carpet, a lantern inside, a chest, a
    furnace to keep alive by."""
    b = Build("snow_hut", 7, 5, 7, seed=seed)
    c = 3
    for x in range(7):
        for z in range(7):
            d = math.hypot(x - c, z - c)
            if d <= 3.2:
                b.set(x, 0, z, B("snow_block") if d > 2.2 else B("packed_ice"))
            for y in range(1, 5):
                r = math.sqrt(max(0.0, 9.6 - (y - 0.6) ** 2 * 1.1))
                if r - 0.9 < d <= r:
                    b.set(x, y, z, B("snow_block"))
                elif d <= r - 0.9:
                    b.set(x, y, z, AIR)
    for y in (1, 2):
        b.set(c, y, 6, AIR)
        b.set(c, y, 5, AIR)
    b.set(c, 0, 6, B("snow_block"))
    b.set(2, 1, 3, B("white_carpet"))
    b.set(3, 1, 3, B("white_carpet"))
    b.set(c, 3, c, B("lantern", hanging=True))
    b.chest(4, 1, 2, "south", chest_loot("frozen_cache"))
    b.set(2, 1, 2, B("furnace", facing="south"))
    return b


def root_hermitage(seed):
    """A hermit's burrow among the giant roots: a mound of rooted earth with roots hanging in its mouth, a
    door of boards, a lantern by it, a garden of ferns and glowroots."""
    rng = random.Random(seed)
    b = Build("root_hermitage", 7, 5, 7, seed=seed)
    c = 3
    for x in range(7):
        for z in range(7):
            d = math.hypot(x - c, (z - c) * 1.1)
            if d <= 3.2:
                b.set(x, 0, z, B(rng.choice(["rooted_dirt", "moss_block", "dirt"])))
            for y in range(1, 5):
                r = 3.3 - (y - 1) * 0.8
                if d <= r:
                    inner = d <= r - 1.0 and y <= 2
                    soil = B(rng.choice(["rooted_dirt", "rooted_dirt", "moss_block", "dirt"]))
                    b.set(x, y, z, AIR if inner else soil)
    for y in (1, 2):
        b.set(c, y, 5, AIR)
        b.set(c, y, 6, AIR)
    b.door(c, 1, 5, "spruce", "south")
    if b.get(c, 3, 4) is not None:
        b.set(c, 2, 4, B("hanging_roots"))
    b.set(2, 1, 3, B("barrel", facing="up"))
    b.barrel(4, 1, 3, "up", chest_loot("forager"))
    b.set(c, 1, 2, B("lantern"))
    for (x, z) in [(1, 6), (5, 6), (0, 4), (6, 4)]:
        b.set(x, 0, z, B("moss_block"))
        b.set(x, 1, z, B(rng.choice(["fern", "fern", "moss_carpet"])))
    b.set(4, 0, 6, B("rooted_dirt"))
    b.set(4, 1, 6, B("spruce_fence"))
    b.set(4, 2, 6, B("lantern"))
    for (x, y, z) in [(c, 4, c), (2, 3, 2), (4, 3, 4)]:
        if b.get(x, y, z) is not None and b.get(x, y - 1, z) is not None and b.get(x, y - 1, z).short == "air":
            b.set(x, y - 1, z, B("expanse:glowroots"))
    return b


def dig_site(seed):
    """Where the bone-hunters were digging: a grid of pegged string over the sand, bones half uncovered,
    suspicious sand still to brush, the tools laid out, a tent and the finds crate."""
    rng = random.Random(seed)
    b = Build("dig_site", 9, 4, 7, seed=seed)
    for x in range(9):
        for z in range(7):
            if rng.random() < 0.8:
                b.set(x, 0, z, B(rng.choice(["sand", "sand", "sandstone"])))
    for x in range(1, 6):
        for z in range(1, 5):
            b.set(x, 0, z, B("sand"))
    for (x, z) in [(1, 1), (5, 1), (1, 4), (5, 4)]:
        b.set(x, 1, z, B("spruce_fence"))
    for x in range(2, 5):
        b.set(x, 0, 2, B("bone_block", axis="x"))
    b.set(3, 1, 3, B("bone_block", axis="y"))
    for (x, z) in [(2, 3), (4, 3), (3, 4), (4, 2)]:
        b.brushable(x, 0, z, "sand", chest_loot("fossil_archaeology"))
    lay(b, 6, 1, 3, "brush", rotation=1)
    lay(b, 6, 1, 4, "iron_shovel", rotation=1)
    b.chest(8, 1, 1, "west", chest_loot("fossil_finds"))
    b.set(8, 1, 2, B("barrel", facing="up"))
    b.set(7, 1, 6, B("spruce_fence"))
    b.set(7, 2, 6, B("lantern"))
    b.set(0, 1, 6, B("skeleton_skull", rotation=rng.randrange(16)))
    return b


def shell_shrine(seed):
    """A shrine of the tide caves: prismarine steps, dead coral set on them like offerings, sea lanterns,
    a pickle glowing in a bowl of stone."""
    rng = random.Random(seed)
    b = Build("shell_shrine", 5, 4, 5, seed=seed)
    for x in range(5):
        for z in range(5):
            if rng.random() < 0.85:
                b.set(x, 0, z, pick(rng, [("prismarine_bricks", 3), ("prismarine", 2), ("sand", 1)]))
    b.set(2, 1, 1, B("dark_prismarine"))
    b.set(2, 2, 1, B("sea_lantern"))
    b.set(1, 1, 1, slab("prismarine_brick", "bottom"))
    b.set(3, 1, 1, slab("prismarine_brick", "bottom"))
    b.set(1, 1, 3, B(rng.choice(["dead_brain_coral_block", "dead_tube_coral_block"])))
    b.set(3, 1, 3, B(rng.choice(["dead_fire_coral_block", "dead_horn_coral_block"])))
    b.set(0, 1, 0, B("sea_lantern"))
    b.set(4, 1, 0, B("sea_lantern"))
    lay(b, 2, 1, 2, "nautilus_shell", rotation=0)
    b.chest(2, 0, 1, "south", chest_loot("tidal_cache"))
    return b


def resin_camp(seed):
    """Amber-tappers' camp: barrels of resin, comb and honey stacked, the cauldron they rendered it in,
    candles, a chunk of amber with something caught in it set out to admire."""
    rng = random.Random(seed)
    b = Build("resin_camp", 7, 4, 5, seed=seed)
    patch(b, [(x, z) for x in range(7) for z in range(5)], rng, TRODDEN, p=0.4)
    b.barrel(0, 1, 0, "up", chest_loot("amber_cache"))
    b.set(1, 1, 0, B("barrel", facing="up"))
    b.set(0, 2, 0, B("honeycomb_block"))
    b.set(2, 1, 0, B("honey_block"))
    b.set(3, 1, 2, B("cauldron"))
    b.campfire(3, 1, 3, lit=True)
    b.set(5, 1, 1, B("expanse:insect_amber"))
    b.set(5, 2, 1, candles(3, "orange"))
    b.set(6, 1, 1, B("expanse:amber"))
    b.set(6, 1, 4, B("lantern"))
    lay(b, 1, 1, 2, "honey_bottle", rotation=1)
    lay(b, 5, 1, 3, "honeycomb", rotation=3)
    return b


def sulfur_outpost(seed):
    """A sulfur-diggers' blockhouse in the brimstone vault: walls of sulfur brick, a roof of basalt slabs, an
    open front, a cart of sulfur outside, barrels, lanterns."""
    rng = random.Random(seed)
    b = Build("sulfur_outpost", 7, 5, 7, seed=seed)
    for x in range(1, 6):
        for z in range(1, 6):
            b.set(x, 0, z, pick(rng, [("polished_sulfur", 2), ("sulfur_bricks", 2), ("smooth_basalt", 1)]))
    for x in range(1, 6):
        for z in range(1, 6):
            wall = x in (1, 5) or z in (1, 5)
            for y in (1, 2, 3):
                if wall and not (z == 5 and 2 <= x <= 4 and y <= 2):
                    b.set(x, y, z, B("sulfur_bricks") if (x, z) not in ((1, 1), (5, 1), (1, 5), (5, 5)) else
                          B("polished_basalt", axis="y"))
                elif not wall:
                    b.set(x, y, z, AIR)
            b.set(x, 4, z, slab("polished_blackstone_brick"))
    b.set(3, 3, 3, B("lantern", hanging=True))
    b.barrel(2, 1, 2, "up", chest_loot("sulfur_cart"))
    b.set(4, 1, 2, B("barrel", facing="up"))
    b.set(2, 1, 3, B("crafting_table"))
    for x in range(0, 7):
        b.set(x, 0, 6, pick(rng, BALLAST))
    for x in (0, 1, 2, 3):
        b.set(x, 1, 6, B("rail", shape="east_west"))
    b._entity("chest_minecart", (1.5, 1.0625, 6.5), (1, 1, 6), {"LootTable": chest_loot("sulfur_cart")}, yaw=90.0)
    b.set(5, 1, 6, B("sulfur"))
    b.set(6, 1, 6, B("sulfur"))
    b.set(6, 2, 6, B("lantern"))
    return b



# ============================================================== cut into the cave wall (wall_depth = WALL)
# Rows z < WALL go into the rock (z = 0 the deepest, z = WALL - 1 the face of the wall); rows z >= WALL stand in
# the cave in front of it, and nothing there may stand above layer 2 (the feature asks for only two blocks of air
# in front of the wall, which leans in overhead). Unset cells in the rock stay rock, so a room is carved by its
# explicit air.
WALL = 5
FRONT = 2


def carve(b, x0, y0, z0, x1, y1, z1):
    b.fill(x0, y0, z0, x1, y1, z1, AIR)


def hermit_cell(seed):
    """A hermit's cell hewn into the side of the passage: a plank door in a frame of logs, a sign that says
    they are not coming back; inside a bed, a shelf of books, a chest, a table with a candle, a lantern hung
    from the rock; outside, a lamp on a post by the door."""
    rng = random.Random(seed)
    b = Build("hermit_cell", 7, 5, WALL + FRONT, seed=seed)
    carve(b, 1, 1, 1, 5, 3, 3)
    for x in range(1, 6):
        for z in range(1, 4):
            b.set(x, 0, z, B("spruce_planks"))
    face = WALL - 1
    for y in (1, 2, 3):
        b.set(2, y, face, B("stripped_spruce_log", axis="y"))
        b.set(4, y, face, B("stripped_spruce_log", axis="y"))
    b.set(3, 3, face, B("spruce_log", axis="x"))
    b.door(3, 1, face, "spruce", "south")
    b.set(3, 0, face, B("spruce_planks"))
    bedroll(b, 1, 1, 2, "north", "brown")
    b.set(2, 1, 1, B("bookshelf"))
    b.barrel(4, 1, 1, "up", None)
    b.chest(5, 1, 1, "south", chest_loot("hermit"))
    b.set(5, 1, 2, slab("spruce", "top"))
    b.set(5, 2, 2, candles(2))
    b.set(5, 1, 3, stairs("spruce", "east"))
    b.set(3, 1, 2, B("brown_carpet"))
    b.set(3, 3, 2, B("lantern", hanging=True))
    lay(b, 4, 1, 3, journal("hermit"), rotation=0)
    post_lantern(b, 1, WALL, rng, h=1)
    b.sign(4, 2, WALL, "spruce", ["GONE BELOW", "", "TAKE WHAT", "YOU NEED"], wall_facing="south", color="gray")
    patch(b, [(3, WALL), (3, WALL + 1), (2, WALL), (4, WALL + 1)], rng, TRODDEN, p=0.8)
    return b


def mine_store(seed):
    """A store at the end of a short timbered adit: two sets of props, rails running in from the passage, a
    cart left at the back by the barrels, a lamp on the cap."""
    rng = random.Random(seed)
    b = Build("mine_store", 5, 5, WALL + FRONT, seed=seed)
    carve(b, 1, 1, 0, 3, 3, WALL - 1)
    for z in range(0, WALL + FRONT):
        b.set(2, 0, z, pick(rng, BALLAST))
        b.set(2, 1, z, B("rail", shape="north_south"))
    for z in range(0, WALL):
        for x in (1, 3):
            b.set(x, 0, z, B("gravel"))
    for z in (WALL - 1, 1):
        for x in (1, 3):
            for y in (1, 2):
                b.set(x, y, z, B("spruce_log", axis="y"))
        for x in (1, 2, 3):
            b.set(x, 3, z, B("spruce_log", axis="x"))
    b.set(2, 2, WALL - 1, B("lantern", hanging=True))
    b.barrel(1, 1, 0, "up", chest_loot("miner_cache"))
    b.set(3, 1, 0, B("barrel", facing="up"))
    b.set(3, 1, 2, B("barrel", facing="west"))
    b.set(1, 1, 3, B("barrel", facing="up"))
    b.set(1, 2, 3, B("lantern"))
    b._entity("chest_minecart", (2.5, 1.0625, 0.5), (2, 1, 0), {"LootTable": chest_loot("ore_cart")}, yaw=0.0)
    patch(b, [(x, z) for x in range(5) for z in range(WALL, WALL + FRONT)], rng, BALLAST, p=0.4)
    return b


def crypt_niche(seed):
    """A burial niche cut into the rock and framed in dressed stone: the bones laid in it, candles still set
    round them, cobwebs, a chest of grave goods at the back; a candle left on the floor outside."""
    rng = random.Random(seed)
    b = Build("crypt_niche", 5, 4, WALL + FRONT, seed=seed)
    carve(b, 1, 1, 2, 3, 2, WALL - 1)
    for x in range(1, 4):
        for z in range(2, WALL):
            b.set(x, 0, z, pick(rng, [("stone_bricks", 3), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1)]))
    face = WALL - 1
    for y in (1, 2):
        b.set(0, y, face, B("stone_bricks"))
        b.set(4, y, face, B("stone_bricks"))
    for x in range(5):
        b.set(x, 3, face, B("chiseled_stone_bricks") if x == 2 else B("stone_bricks"))
    b.set(2, 1, 2, B("skeleton_skull", rotation=0))
    b.set(1, 1, 3, B("bone_block", axis="z"))
    b.set(3, 1, 2, candles(3, "white"))
    b.set(3, 1, 3, candles(1, "white"))
    b.chest(1, 1, 2, "south", chest_loot("crypt"))
    b.set(2, 2, 2, B("cobweb"))
    b.set(1, 2, 3, B("cobweb"))
    b.set(2, 0, WALL, B("stone_bricks"))
    b.set(0, 1, WALL, candles(1, "white"))
    b.set(0, 0, WALL, B("cobblestone"))
    return b


def wall_shrine(seed):
    """A saint in a niche in the rock: a figure in a golden crown, candles at their feet, flowers in pots, a
    cushion on the floor in front for whoever stops to kneel."""
    rng = random.Random(seed)
    b = Build("wall_shrine", 5, 5, WALL + FRONT, seed=seed)
    carve(b, 1, 1, 2, 3, 3, WALL - 1)
    face = WALL - 1
    for x in range(1, 4):
        for z in range(2, WALL):
            b.set(x, 0, z, B("polished_andesite") if z < face else B("stone_bricks"))
    for y in (1, 2, 3):
        b.set(0, y, face, B("stone_brick_wall") if y < 3 else B("stone_bricks"))
        b.set(4, y, face, B("stone_brick_wall") if y < 3 else B("stone_bricks"))
    b.set(1, 4, face, stairs("stone_brick", "east", "top"))
    b.set(2, 4, face, B("chiseled_stone_bricks"))
    b.set(3, 4, face, stairs("stone_brick", "west", "top"))
    b.set(2, 1, 2, B("chiseled_stone_bricks"))
    b.armor_stand(2, 2, 2, yaw=0.0, equipment={"head": "golden_helmet", "chest": "chainmail_chestplate"},
                  pose={"Head": (0.0, 0.0, 0.0), "Body": (0.0, 0.0, 0.0), "LeftArm": (-20.0, 0.0, -10.0),
                        "RightArm": (-20.0, 0.0, 10.0)})
    b.set(1, 1, 3, candles(3, "yellow"))
    b.set(3, 1, 3, candles(2, "yellow"))
    b.set(1, 1, face, B("potted_poppy"))
    b.set(3, 1, face, B(rng.choice(["potted_azure_bluet", "potted_lily_of_the_valley", "potted_cornflower"])))
    b.set(2, 3, 3, B("lantern", hanging=True))
    b.cushion(2, 1, WALL, rng.choice(["red", "purple", "blue"]))
    lay(b, 1, 1, WALL, "gold_nugget", rotation=rng.randrange(8))
    return b


def vault_door(seed):
    """An iron door in a frame of stone set into the rock, a lever beside it; behind it a little strongroom
    and whatever someone thought worth locking up down here."""
    b = Build("vault_door", 5, 4, WALL + FRONT, seed=seed)
    carve(b, 1, 1, 1, 3, 2, WALL - 2)
    for x in range(1, 4):
        for z in range(1, WALL - 1):
            b.set(x, 0, z, B("stone_bricks"))
    face = WALL - 1
    for x in range(5):
        for y in (1, 2, 3):
            if not (x == 2 and y in (1, 2)):
                b.set(x, y, face, B("chiseled_stone_bricks") if (x, y) == (2, 3) else B("stone_bricks"))
    b.set(2, 0, face, B("stone_bricks"))
    b.set(2, 1, face, B("iron_door", facing="south", half="lower", hinge="left", open=False))
    b.set(2, 2, face, B("iron_door", facing="south", half="upper", hinge="left", open=False))
    b.set(1, 2, WALL, B("lever", face="wall", facing="south"))
    b.chest(2, 1, 1, "south", chest_loot("vault"))
    b.set(1, 1, 1, B("barrel", facing="up"))
    b.set(3, 1, 1, B("lantern"))
    b.set(3, 1, 3, B("cobweb"))
    b.sign(3, 2, WALL, "dark_oak", ["", "PROPERTY OF", "THE SURVEY", ""], wall_facing="south", color="white")
    return b


def miners_cubby(seed):
    """A low cubby a miner cut for themselves off the passage: a doorway between two props, a bed in the rock,
    a barrel, the tools hung on the wall, a lantern on the floor."""
    rng = random.Random(seed)
    b = Build("miners_cubby", 5, 4, WALL + FRONT, seed=seed)
    carve(b, 1, 1, 1, 3, 2, WALL - 1)
    for x in range(1, 4):
        for z in range(1, WALL):
            b.set(x, 0, z, pick(rng, TRODDEN))
    face = WALL - 1
    for y in (1, 2):
        b.set(1, y, face, B("spruce_log", axis="y"))
        b.set(3, y, face, B("spruce_log", axis="y"))
    bedroll(b, 1, 1, 2, "north")
    b.barrel(3, 1, 1, "up", chest_loot("miner_cache"))
    b.set(2, 1, 1, B("lantern"))
    b.item_frame(3, 2, 2, "west", item("iron_pickaxe"), rotation=1)
    b.item_frame(3, 2, 3, "west", item("iron_shovel"), rotation=1)
    patch(b, [(2, WALL), (2, WALL + 1), (1, WALL)], rng, TRODDEN, p=0.7)
    return b


# ============================================================== the catalogue
def templates():
    """name -> Build (finished, not finalized)."""
    out = {}

    def add(b):
        if b.name in out:
            raise ValueError(f"cave template {b.name} twice")
        out[b.name] = b

    S = 9101
    add(miner_rest(S + 1))
    add(lamp_post(S + 2))
    add(lamp_post(S + 3, "lamp_post_copper", lantern="oxidized_copper_lantern", wood="dark_oak"))
    add(stake_lantern(S + 4))
    add(candle_cairn(S + 5))
    add(candle_cairn(S + 6, "candle_cairn_deep", mix=[("cobbled_deepslate", 4), ("deepslate_bricks", 1), ("tuff", 2)],
                     top="cobbled_deepslate_slab", color="white"))
    add(marker_sign(S + 7, "sign_water", ["", "WATER", "THIS WAY", ""]))
    add(marker_sign(S + 8, "sign_bad_air", ["BAD AIR", "", "TURN BACK", ""]))
    add(marker_sign(S + 9, "sign_no_way", ["", "NO WAY", "THROUGH", ""]))
    add(marker_sign(S + 10, "sign_survey", ["SURVEY", "MARK 14", "", "- U.S."]))
    add(timber_frame(S + 11))
    add(timber_frame(S + 12, "timber_frame_broken", broken=True))
    add(timber_frame(S + 13, "timber_frame_oak", wood="oak", width=4))
    add(remains(S + 14))
    add(stash(S + 15))
    add(cold_camp(S + 16))
    add(cave_in(S + 17))
    add(rail_stub(S + 18))
    add(rail_stub(S + 19, "rail_stub_empty", cart="minecart"))
    add(ore_spill(S + 20))
    add(mushroom_patch(S + 21))
    add(crystal_pocket(S + 22))
    add(spring_pool(S + 23))
    add(waystation(S + 24))
    add(forge(S + 25))
    add(spider_den(S + 26))
    add(cave_shrine(S + 27))
    add(prospector_camp(S + 28))
    add(lost_explorer(S + 29))
    add(frozen_camp(S + 30))
    add(ice_shrine(S + 31))
    add(sand_shrine(S + 32))
    add(pot_cache(S + 33))
    add(dry_bones(S + 34))
    add(moss_shrine(S + 35))
    add(overgrown_cart(S + 36))
    add(drip_cauldrons(S + 37))
    add(flowstone_shrine(S + 38))
    add(echo_arch(S + 39))
    add(soul_altar(S + 40))
    add(sulfur_camp(S + 41))
    add(waystone(S + 42))
    add(waystone(S + 43, "waystone_deep", mix=[("cobbled_deepslate", 4), ("deepslate_bricks", 2), ("tuff", 1)],
                 cap="deepslate_brick_slab", lantern="soul_lantern"))
    add(explorer_camp(S + 44))
    add(survey_tower(S + 45))
    add(forager_hut(S + 46))
    add(mushroom_ring(S + 47))
    add(moss_gazebo(S + 48))
    add(crystal_prospector(S + 49))
    add(crystal_altar(S + 50))
    add(obsidian_altar(S + 51))
    add(basalt_ruin(S + 52))
    add(beached_boat(S + 53))
    add(fisher_camp(S + 54))
    add(snow_hut(S + 55))
    add(root_hermitage(S + 56))
    add(dig_site(S + 57))
    add(shell_shrine(S + 58))
    add(beached_boat(S + 59, "beached_boat_tidal", wood="spruce"))
    add(resin_camp(S + 60))
    add(sulfur_outpost(S + 61))
    add(hermit_cell(S + 62))
    add(mine_store(S + 63))
    add(crypt_niche(S + 64))
    add(wall_shrine(S + 65))
    add(vault_door(S + 66))
    add(miners_cubby(S + 67))
    return out


# ============================================================== processor lists
COBWEB_ROT = [rule("cobweb", 0.35, "air")]
CANDLE_OUT = [rule("candle", 0.25, "air")]
DEEP_DECAY = [rule("deepslate_tiles", 0.15, "cracked_deepslate_tiles"), rule("deepslate_bricks", 0.15,
                                                                            "cracked_deepslate_bricks"),
              rule("polished_deepslate", 0.08, "cobbled_deepslate")]
BLACKSTONE_DECAY = [rule("polished_blackstone_bricks", 0.2, "cracked_polished_blackstone_bricks"),
                    rule("blackstone", 0.1, "basalt")]
MOSS_OVER = [rule("cobblestone", 0.45, "mossy_cobblestone"), rule("stone_bricks", 0.35, "mossy_stone_bricks"),
             rule("gravel", 0.25, "moss_block")]
SAND_WEAR = [rule("cut_sandstone", 0.15, "sandstone"), rule("smooth_sandstone", 0.1, "sandstone")]
ICE_OVER = [rule("cobblestone", 0.3, "packed_ice"), rule("gravel", 0.3, "snow_block"), rule("snow", 0.3, "air")]
SULFUR_WEAR = [rule("sulfur_bricks", 0.12, "sulfur"), rule("polished_sulfur", 0.1, "sulfur")]

PROCESSORS = {
    "stone": plist(STONE_WEATHER, aged_wood("spruce", "oak", "dark_oak"), COBWEB_ROT),
    "deep": plist(STONE_WEATHER, DEEP_DECAY, aged_wood("spruce", "dark_oak"), COBWEB_ROT, CANDLE_OUT),
    "cold": plist(aged_wood("spruce"), ICE_OVER, [rule("cobweb", 0.6, "air")]),
    "dry": plist(SAND_WEAR, aged_wood("acacia", "spruce"), COBWEB_ROT),
    "lush": plist(STONE_WEATHER, MOSS_OVER, aged_wood("spruce", "oak")),
    "drip": plist(STONE_WEATHER, [rule("calcite", 0.1, "dripstone_block")], aged_wood("spruce")),
    "echo": plist(DEEP_DECAY, [rule("sculk", 0.2, "deepslate_tiles"), rule("candle", 0.2, "air")]),
    "sulfur": plist(STONE_WEATHER, SULFUR_WEAR, aged_wood("spruce")),
    "ember": plist(BLACKSTONE_DECAY, [rule("magma_block", 0.3, "smooth_basalt")]),
    "tidal": plist([rule("prismarine_bricks", 0.15, "prismarine"), rule("gravel", 0.3, "sand")],
                   aged_wood("spruce", "oak")),
    "realm": plist(STONE_WEATHER, aged_wood("spruce", "oak"), COBWEB_ROT),
}


# ============================================================== journals
BOOKS = {
    "last_entry": {
        "title": "Last Entry", "author": "M. Okafor",
        "pages": [
            "Day nine below. The lamp oil is low but the passage keeps going down and I keep going with it. "
            "There was a wind from somewhere ahead, warm, and it smelled of rain.",
            "Lost the cord at the third fork. Marked the walls but the marks all look the same by lamplight. "
            "I can hear water. I can hear something else under the water.",
            "If you are reading this, go back the way the candles point. Somebody keeps them lit. It was not me.",
        ]},
    "hermit": {
        "title": "Below the Weather", "author": "Brother Anselm",
        "pages": [
            "Forty days in the cell. The rock is the same temperature every hour of every season. Up there they "
            "argue about rain. Down here there is no weather and I have stopped arguing about anything.",
            "Travellers pass most weeks: miners, the survey with their waystones, once a woman looking for her "
            "son. I give them bread and a candle. Nobody asks why I stay, which is a kindness.",
            "I am going deeper to see the lake the ferrymen talk about. Take what you need from the chest. "
            "Leave the candle lit for the next one.",
        ]},
    "survey": {
        "title": "Survey of the Lower Ways", "author": "The Underground Survey",
        "pages": [
            "Mark 14. Three levels of passage: the upper climbs with the hills, the middle meets the great "
            "tunnels, the lowest opens into the walls of the realms. Keep a lamp at every fork.",
            "Great tunnels run between the realms, twelve to twenty blocks wide. One family carries rivers. "
            "Follow the water down and you will come out high on a realm wall, beside a fall.",
            "The realms: lands of their own under a stone sky. Waystones are raised on the hills of each so "
            "the next party can cross in the dark. Leave the camps stocked. Take only what you need.",
        ]},
}


# ============================================================== loot
def _t(name, *pools, kind="chest"):
    return L.table(f"{SUB}/{name}", *pools, kind=kind)


LOOT = {
    # a miner's barrel: the common things of the work, about a mineshaft cart's worth, less the treasure
    "miner_cache": _t("miner_cache",
                      L.pool((3, 6), L.item("torch", 5, (3, 10)), L.item("coal", 5, (2, 8)), L.item("bread", 4, (1, 3)),
                             L.item("raw_iron", 4, (1, 5)), L.item("raw_copper", 4, (2, 8)), L.item("iron_nugget", 3, (2, 8)),
                             L.item("rail", 3, (2, 8)), L.item("ladder", 2, (2, 6)), L.item("candle", 2, (1, 3)),
                             L.item("baked_potato", 2, (1, 3)), L.item("lantern", 1), L.item("string", 1, (1, 4))),
                      L.pool((0, 1), L.empty(4), L.item("iron_pickaxe", 2, damage=(0.2, 0.7)),
                             L.item("iron_shovel", 1, damage=(0.2, 0.7)), L.item("miner_pottery_sherd", 1))),
    # a loaded cart: ore, as the mineshaft carts carry
    "hermit": _t("hermit",
                 L.pool((3, 6), L.item("bread", 5, (1, 4)), L.item("candle", 4, (1, 4)), L.item("book", 3, (1, 3)),
                        L.item("paper", 3, (2, 6)), L.item("glow_berries", 3, (2, 6)), L.item("mushroom_stew", 2),
                        L.item("emerald", 2, (1, 3)), L.item("torch", 2, (2, 6))),
                 L.pool((0, 1), L.empty(3), L.book(3), L.item("golden_apple", 1), L.item("compass", 1))),
    "crypt": _t("crypt",
                L.pool((2, 5), L.item("bone", 5, (2, 6)), L.item("gold_nugget", 4, (2, 9)), L.item("candle", 3, (1, 3)),
                       L.item("rotten_flesh", 2, (1, 3)), L.item("emerald", 2, (1, 2))),
                L.pool((0, 1), L.empty(3), L.book(2), L.item("golden_sword", 1, damage=(0.3, 0.8)),
                       L.item("mourner_pottery_sherd", 1), L.item("music_disc_cat", 1))),
    "vault": _t("vault",
                L.pool((3, 6), L.item("gold_ingot", 5, (2, 6)), L.item("emerald", 5, (2, 6)), L.item("iron_ingot", 4, (2, 7)),
                       L.item("lapis_lazuli", 3, (3, 9)), L.item("redstone", 2, (3, 9))),
                L.pool((1, 2), L.item("diamond", 3, (1, 3)), L.book(3), L.item("golden_apple", 2),
                       L.item("enchanted_golden_apple", 1), L.item("name_tag", 2),
                       L.item("diamond_pickaxe", 1, enchant=True))),
    "ore_cart": _t("ore_cart",
                   L.pool((3, 6), L.item("raw_iron", 5, (2, 7)), L.item("raw_copper", 5, (3, 9)), L.item("coal", 5, (3, 9)),
                          L.item("raw_gold", 2, (1, 4)), L.item("redstone", 3, (2, 7)), L.item("lapis_lazuli", 3, (2, 7)),
                          L.item("rail", 2, (2, 6))),
                   L.pool((0, 1), L.empty(6), L.item("diamond", 1, (1, 2)), L.item("emerald", 2, (1, 3)))),
    "sulfur_cart": _t("sulfur_cart",
                      L.pool((3, 6), L.item("sulfur", 5, (2, 8)), L.item("coal", 3, (2, 6)), L.item("gunpowder", 3, (1, 4)),
                             L.item("torch", 2, (2, 6)), L.item("bread", 2, (1, 2)), L.item("raw_iron", 2, (1, 3))),
                      L.pool((0, 1), L.empty(4), L.item("flint_and_steel", 1), L.item("fire_charge", 2, (1, 3)))),
    "waystation": _t("waystation",
                     L.pool((3, 6), L.item("torch", 5, (4, 12)), L.item("bread", 4, (2, 4)), L.item("cooked_cod", 3, (1, 3)),
                            L.item("ladder", 3, (3, 8)), L.item("paper", 2, (1, 4)), L.item("candle", 2, (1, 3)),
                            L.item("lantern", 2), L.item("string", 2, (2, 5)), L.item("arrow", 2, (2, 8))),
                     L.pool((0, 1), L.empty(3), L.item("compass", 2), L.item("map", 2), L.item("spyglass", 1),
                            L.item("lead", 1))),
    "smithy": _t("smithy",
                 L.pool((3, 6), L.item("iron_ingot", 5, (1, 4)), L.item("iron_nugget", 5, (3, 9)), L.item("coal", 4, (2, 8)),
                        L.item("copper_ingot", 3, (2, 6)), L.item("flint", 2, (1, 3)), L.item("iron_chain", 2, (1, 3))),
                 L.pool((0, 1), L.empty(3), L.item("iron_sword", 2, damage=(0.3, 0.8)),
                        L.item("chainmail_chestplate", 1, damage=(0.3, 0.8)), L.item("iron_pickaxe", 2, enchant=True),
                        L.item("shield", 1))),
    "spider_den": _t("spider_den",
                     L.pool((2, 5), L.item("string", 6, (2, 7)), L.item("spider_eye", 4, (1, 3)), L.item("bone", 4, (2, 6)),
                            L.item("gold_nugget", 3, (2, 8)), L.item("gunpowder", 2, (1, 4))),
                     L.pool((1, 2), L.item("emerald", 3, (1, 4)), L.item("gold_ingot", 3, (1, 3)), L.book(2),
                            L.item("golden_apple", 1), L.item("name_tag", 2), L.item("music_disc_13", 1),
                            L.item("iron_horse_armor", 1))),
    "shrine": _t("shrine",
                 L.pool((2, 5), L.item("candle", 4, (1, 4)), L.item("gold_nugget", 5, (3, 12)), L.item("lapis_lazuli", 3, (2, 6)),
                        L.item("amethyst_shard", 3, (1, 4)), L.item("emerald", 2, (1, 3))),
                 L.pool((0, 1), L.empty(2), L.book(2), L.item("golden_apple", 1), L.item("echo_shard", 1))),
    "prospector": _t("prospector",
                     L.pool((3, 6), L.item("raw_gold", 3, (1, 4)), L.item("raw_iron", 4, (2, 6)), L.item("bread", 4, (1, 3)),
                            L.item("torch", 4, (3, 9)), L.item("coal", 3, (2, 6)), L.item("lapis_lazuli", 2, (2, 6)),
                            L.item("gold_nugget", 3, (3, 9))),
                     L.pool((0, 1), L.empty(5), L.item("diamond", 1), L.item("golden_pickaxe", 1, damage=(0.3, 0.8)))),
    "explorer": _t("explorer",
                   L.pool((3, 6), L.item("bread", 3, (1, 3)), L.item("torch", 3, (3, 8)), L.item("paper", 3, (1, 5)),
                          L.item("gold_ingot", 3, (1, 3)), L.item("emerald", 3, (1, 4)), L.item("lead", 2),
                          L.item("leather", 2, (1, 3)), L.item("string", 2, (2, 5))),
                   L.pool((1, 2), L.item("compass", 3), L.item("spyglass", 2), L.item("map", 2), L.book(3),
                          L.item("name_tag", 2), L.item("diamond", 1, (1, 2)), L.item("golden_apple", 1),
                          L.item("explorer_pottery_sherd", 2))),
    "frozen_cache": _t("frozen_cache",
                       L.pool((3, 6), L.item("snowball", 3, (2, 8)), L.item("leather", 4, (1, 4)), L.item("rabbit_hide", 3, (1, 4)),
                              L.item("cooked_rabbit", 3, (1, 3)), L.item("torch", 3, (2, 6)), L.item("coal", 3, (2, 6)),
                              L.item("emerald", 2, (1, 3))),
                       L.pool((0, 1), L.empty(3), L.item("leather_boots", 2, damage=(0.2, 0.6)),
                              L.item("leather_chestplate", 1, enchant=True), L.item("spyglass", 1), L.item("compass", 1))),
    "cave_pot": _t("cave_pot",
                   L.pool((1, 3), L.item("gold_nugget", 4, (2, 7)), L.item("bone", 3, (1, 3)), L.item("emerald", 2),
                          L.item("string", 2, (1, 3)), L.item("bread", 2), L.item("iron_nugget", 3, (2, 6)),
                          L.item("lapis_lazuli", 2, (1, 4)))),
    "remains_archaeology": _t("remains_archaeology",
                              L.pool(1, L.item("bone", 4), L.item("iron_nugget", 3), L.item("gold_nugget", 2),
                                     L.item("emerald", 1), L.item("compass", 1), L.item("miner_pottery_sherd", 1),
                                     L.item("explorer_pottery_sherd", 1), L.item("candle", 2)), kind="archaeology"),
    "dry_archaeology": _t("dry_archaeology",
                          L.pool(1, L.item("bone", 3), L.item("gold_nugget", 3), L.item("emerald", 1),
                                 L.item("archer_pottery_sherd", 1), L.item("skull_pottery_sherd", 1),
                                 L.item("prize_pottery_sherd", 1), L.item("brick", 2)), kind="archaeology"),
    "fossil_archaeology": _t("fossil_archaeology",
                             L.pool(1, L.item("bone", 5), L.item("bone_meal", 3), L.item("skull_pottery_sherd", 1),
                                    L.item("danger_pottery_sherd", 1), L.item("sniffer_egg", 1), L.item("gold_nugget", 2),
                                    L.item("armadillo_scute", 1)), kind="archaeology"),
    "fossil_finds": _t("fossil_finds",
                       L.pool((3, 5), L.item("bone", 5, (3, 9)), L.item("bone_block", 2, (1, 3)), L.item("paper", 3, (2, 5)),
                              L.item("bread", 2, (1, 3)), L.item("torch", 2, (2, 6)), L.item("brush", 1)),
                       L.pool((0, 1), L.empty(4), L.item("sniffer_egg", 1), L.item("skull_pottery_sherd", 2),
                              L.item("danger_pottery_sherd", 2))),
    "forager": _t("forager",
                  L.pool((3, 6), L.item("brown_mushroom", 4, (2, 6)), L.item("red_mushroom", 4, (2, 6)),
                         L.item("glow_berries", 4, (2, 8)), L.item("mushroom_stew", 2), L.item("bowl", 2, (1, 3)),
                         L.item("glow_lichen", 2, (2, 5)), L.item("shroomlight", 1, (1, 2)), L.item("moss_block", 2, (1, 4))),
                  L.pool((0, 1), L.empty(4), L.item("suspicious_stew", 2), L.item("golden_carrot", 1, (1, 3)))),
    "crystal_cache": _t("crystal_cache",
                        L.pool((3, 6), L.item("amethyst_shard", 6, (2, 8)), L.item("calcite", 2, (2, 6)),
                               L.item("tinted_glass", 2, (1, 3)), L.item("torch", 3, (2, 6)), L.item("bread", 2, (1, 2))),
                        L.pool((0, 1), L.empty(3), L.item("spyglass", 2), L.item("diamond", 1, (1, 2)),
                               L.item("emerald", 2, (1, 3)))),
    "ember_cache": _t("ember_cache",
                      L.pool((2, 5), L.item("gold_ingot", 4, (1, 4)), L.item("gold_nugget", 4, (4, 12)),
                             L.item("crying_obsidian", 2, (1, 3)), L.item("obsidian", 2, (1, 4)), L.item("magma_cream", 2, (1, 3))),
                      L.pool((0, 1), L.empty(3), L.item("golden_apple", 2), L.book(2), L.item("fire_charge", 2, (1, 4)))),
    "ferry": _t("ferry",
                L.pool((3, 6), L.item("cod", 4, (1, 4)), L.item("salmon", 3, (1, 3)), L.item("dried_kelp", 4, (3, 9)),
                       L.item("string", 3, (2, 6)), L.item("lantern", 1), L.item("paper", 2, (1, 3)), L.item("emerald", 2, (1, 2))),
                L.pool((0, 1), L.empty(3), L.item("fishing_rod", 2, enchant=True), L.item("nautilus_shell", 1),
                       L.item("oak_boat", 1))),
    "tidal_cache": _t("tidal_cache",
                      L.pool((2, 5), L.item("prismarine_shard", 5, (2, 7)), L.item("prismarine_crystals", 4, (1, 5)),
                             L.item("sea_pickle", 3, (1, 4)), L.item("gold_nugget", 3, (2, 8)), L.item("nautilus_shell", 1)),
                      L.pool((0, 1), L.empty(5), L.item("heart_of_the_sea", 1), L.item("trident", 1, damage=(0.4, 0.9)))),
    "amber_cache": _t("amber_cache",
                      L.pool((3, 6), L.item("honey_bottle", 4, (1, 3)), L.item("honeycomb", 5, (2, 6)),
                             L.item("expanse:amber", 3, (1, 4)), L.item("candle", 3, (1, 4)), L.item("bread", 2, (1, 3))),
                      L.pool((0, 1), L.empty(3), L.item("expanse:insect_amber", 2), L.item("golden_apple", 1))),
}


# ============================================================== feature groups
def tag(t):
    return {"type": "minecraft:matching_block_tag", "tag": t}


def blocks_(*ids):
    return {"type": "minecraft:matching_blocks", "blocks": [i if ":" in i else "minecraft:" + i for i in ids]}


def any_of(*preds):
    return {"type": "minecraft:any_of", "predicates": list(preds)}


# The floors of the caves as the land and the cave decoration leave them (CavernLife.natural and what it covers
# rock with): never anything built.
CAVE_FLOOR = any_of(tag("minecraft:base_stone_overworld"), tag("minecraft:dirt"), tag("minecraft:sand"),
                    blocks_("gravel", "clay", "calcite", "dripstone_block", "smooth_basalt", "basalt", "blackstone",
                            "sandstone", "red_sandstone", "snow_block", "packed_ice", "sulfur", "amethyst_block",
                            "expanse:amber", "expanse:limestone", "expanse:salt_block", "expanse:volcanic_ash"))

COLD = ["minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga", "minecraft:grove",
        "minecraft:snowy_slopes", "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:frozen_river",
        "minecraft:snowy_beach", "expanse:lichen_tundra", "expanse:larch_taiga"]
DRY = ["minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands",
       "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna", "expanse:red_dunes",
       "expanse:amber_steppe", "expanse:saguaro_flats", "expanse:ghost_gum_outback", "expanse:painted_canyons",
       "expanse:salt_flats"]
LUSH = ["minecraft:lush_caves", "expanse:mossy_caves", "minecraft:jungle", "minecraft:sparse_jungle",
        "minecraft:bamboo_jungle", "minecraft:swamp", "minecraft:mangrove_swamp", "expanse:jade_karst",
        "expanse:cloud_forest", "expanse:willow_bayou", "expanse:kapok_rainforest", "expanse:monsoon_forest",
        "expanse:ancient_oakwood", "expanse:tepui"]
DRIP = ["minecraft:dripstone_caves", "expanse:dripstone_grottos"]
ECHO = ["minecraft:deep_dark", "expanse:echoing_depths"]
SULFUR = ["minecraft:sulfur_caves", "expanse:sulfur_seeps"]
ANY_CAVE = "#minecraft:is_overworld"     # every land and cave biome; the realms are not in it
ALL_REALMS = "#expanse:realms"


def realm(name):
    return [f"expanse:realm_{name}"]


class Group:
    """A placed feature (expanse:cave/<name>) trying one column in `rarity` chunks (`count` columns a chunk), for
    one template from a weighted list on a floor in `biomes`. Every group runs in every chunk (CaveThings.java)."""

    def __init__(self, name, items, procs, biomes, rarity=1, count=1, relief=1, depth=10, clearance=4, min_y=None,
                 max_y=None, wall=0):
        self.name, self.items, self.procs, self.biomes = name, items, procs, biomes
        self.rarity, self.count, self.relief, self.depth, self.clearance = rarity, count, relief, depth, clearance
        self.min_y, self.max_y, self.wall = min_y, max_y, wall


GROUPS = [
    # ---- the caves at large: a lamp, a cairn, a camp or a prop every few dozen blocks of passage
    Group("caves", [("lamp_post", 4), ("lamp_post_copper", 3), ("stake_lantern", 3), ("candle_cairn", 3),
                    ("candle_cairn_deep", 2), ("sign_water", 1), ("sign_bad_air", 1), ("sign_no_way", 1),
                    ("sign_survey", 1), ("timber_frame", 4), ("timber_frame_broken", 3), ("timber_frame_oak", 2),
                    ("miner_rest", 3), ("remains", 2), ("stash", 3), ("cold_camp", 3), ("cave_in", 3),
                    ("rail_stub", 2), ("rail_stub_empty", 2), ("ore_spill", 2), ("mushroom_patch", 3),
                    ("crystal_pocket", 2), ("spring_pool", 2)], "stone", ANY_CAVE, count=1),
    # ---- rarer and larger: they need a chamber, a tunnel or a realm to stand in
    Group("caves_rare", [("waystation", 3), ("forge", 2), ("spider_den", 1), ("cave_shrine", 2),
                         ("prospector_camp", 3), ("lost_explorer", 2)], "deep", ANY_CAVE, rarity=4, clearance=6),
    # ---- cut into the walls of the passages: cells, stores, niches; a lamp by the door of most
    Group("caves_wall", [("hermit_cell", 3), ("mine_store", 3), ("crypt_niche", 2), ("wall_shrine", 2),
                         ("vault_door", 1), ("miners_cubby", 3)], "stone", ANY_CAVE, rarity=2, wall=WALL, clearance=3),
    # ---- the caves under each kind of country
    Group("caves_cold", [("frozen_camp", 3), ("ice_shrine", 3)], "cold", COLD, rarity=3),
    Group("caves_dry", [("sand_shrine", 2), ("pot_cache", 3), ("dry_bones", 2)], "dry", DRY, rarity=3),
    Group("caves_lush", [("moss_shrine", 3), ("overgrown_cart", 2), ("mushroom_patch", 2)], "lush", LUSH, rarity=3),
    Group("caves_drip", [("drip_cauldrons", 3), ("flowstone_shrine", 2)], "drip", DRIP, rarity=2),
    Group("caves_echo", [("echo_arch", 2), ("soul_altar", 2), ("candle_cairn_deep", 2)], "echo", ECHO, rarity=2),
    Group("caves_sulfur", [("sulfur_camp", 3), ("stake_lantern", 1)], "sulfur", SULFUR, rarity=2),
    # ---- the realms: the explorers' marks in all of them, then each realm's own
    Group("realms", [("waystone", 2), ("waystone_deep", 2), ("explorer_camp", 4), ("survey_tower", 2),
                     ("prospector_camp", 3), ("lost_explorer", 2), ("cave_shrine", 2)], "realm", ALL_REALMS,
          rarity=4, relief=3, depth=16, clearance=6),
    Group("realm_wilds", [("forager_hut", 3), ("mushroom_ring", 2)], "realm", realm("wilds"), rarity=3, relief=3,
          depth=16, clearance=6),
    Group("realm_lush", [("moss_gazebo", 3), ("moss_shrine", 2)], "lush", realm("lush"), rarity=3, relief=3, depth=16,
          clearance=6),
    Group("realm_dripstone", [("drip_cauldrons", 2), ("flowstone_shrine", 1)], "drip", realm("dripstone"), rarity=3,
          relief=3, depth=16),
    Group("realm_crystal", [("crystal_prospector", 4), ("crystal_altar", 1), ("crystal_pocket", 1)], "realm",
          realm("crystal"), rarity=3, relief=3, depth=16),
    Group("realm_ember", [("obsidian_altar", 2), ("basalt_ruin", 2)], "ember", realm("ember"), rarity=3, relief=3,
          depth=16),
    Group("realm_mere", [("beached_boat", 3), ("fisher_camp", 2)], "realm", realm("mere"), rarity=3, relief=3, depth=16),
    Group("realm_frozen", [("snow_hut", 3), ("ice_shrine", 2), ("frozen_camp", 2)], "cold", realm("frozen"), rarity=3,
          relief=3, depth=16, clearance=6),
    Group("realm_roots", [("root_hermitage", 3), ("mushroom_patch", 1)], "lush", realm("roots"), rarity=3, relief=3,
          depth=16, clearance=6),
    Group("realm_fossil", [("dig_site", 3), ("dry_bones", 2), ("pot_cache", 1)], "dry", realm("fossil"), rarity=3,
          relief=3, depth=16),
    Group("realm_tidal", [("shell_shrine", 2), ("beached_boat_tidal", 2), ("fisher_camp", 2)], "tidal", realm("tidal"),
          rarity=3, relief=3, depth=16),
    Group("realm_amber", [("resin_camp", 3)], "realm", realm("amber"), rarity=3, relief=3, depth=16),
    Group("realm_brimstone", [("sulfur_outpost", 3), ("sulfur_camp", 2)], "sulfur", realm("brimstone"), rarity=3,
          relief=3, depth=16, clearance=6),
]


def configured(g):
    obj = {"type": "expanse:cave_template", "biomes": g.biomes, "ground": CAVE_FLOOR, "max_relief": g.relief,
           "min_depth": g.depth, "clearance": g.clearance, "processors": f"expanse:{SUB}/{g.procs}",
           "templates": [{"data": f"expanse:{SUB}/{t}", "weight": w} for t, w in g.items]}
    if g.wall:
        obj["wall_depth"] = g.wall
    if g.min_y is not None:
        obj["min_y"] = g.min_y
    if g.max_y is not None:
        obj["max_y"] = g.max_y
    return obj


def placed(g):
    placement = []
    if g.rarity > 1:
        placement.append({"type": "minecraft:rarity_filter", "chance": g.rarity})
    if g.count > 1:
        placement.append({"type": "minecraft:count", "count": g.count})
    placement.append({"type": "minecraft:in_square"})
    return {"feature": f"expanse:{SUB}/{g.name}", "placement": placement}


# ============================================================== output
def vanilla_order(o):
    """'type' first, remaining keys sorted - the layout vanilla's data generator writes."""
    if isinstance(o, dict):
        keys = sorted(o, key=lambda k: (k != "type", k))
        return {k: vanilla_order(o[k]) for k in keys}
    if isinstance(o, list):
        return [vanilla_order(v) for v in o]
    return o


def write_json(rel, obj):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(json.dumps(vanilla_order(obj), indent=2) + "\n")
    return path


def clean():
    """Remove what an earlier run wrote (only our own cave/ folders)."""
    for d, ext in OUT_DIRS.items():
        for f in sorted(glob.glob(os.path.join(DATA, d, SUB, "*" + ext))):
            os.remove(f)


def java_groups():
    """Placed-feature names CaveThings.java adds, in order."""
    with open(JAVA_GROUPS) as f:
        return re.findall(r'\badd\("([a-z0-9_]+)"', f.read())


# ============================================================== validation
def validate_all(builds):
    """The structure validator's own template / processor-list / loot checks, plus the feature wiring."""
    import validate as V
    from gen_micro import check_predicate
    ctx = V.Ctx(DATA)
    for name in sorted(builds):
        V.check_template(ctx, os.path.join(DATA, "structure", SUB, name + ".nbt"), set())
    for p in sorted(glob.glob(os.path.join(DATA, "worldgen", "processor_list", SUB, "*.json"))):
        V.check_processor_list(ctx, p)
    for p in sorted(glob.glob(os.path.join(DATA, "loot_table", "chests", SUB, "*.json"))):
        V.check_loot(ctx, p)
    err = ctx.err
    used = set()
    for g in GROUPS:
        for t, w in g.items:
            used.add(t)
            if t not in builds:
                err(f"cave/{g.name}: no template {t}")
            elif not isinstance(w, int) or w < 1:
                err(f"cave/{g.name}: weight {w} for {t}")
            else:
                sx, _, sz = builds[t].size
                if max(sx, sz) > MAX_FOOTPRINT:
                    err(f"cave/{t}: {sx}x{sz} is wider than the feature allows ({MAX_FOOTPRINT})")
        for t, _ in g.items:
            if t in builds and g.wall and any(p[2] >= g.wall and p[1] > 2 for p in builds[t].cells):
                err(f"cave/{t}: something in front of the wall stands above layer 2")
            if t in builds and g.wall and builds[t].size[2] <= g.wall:
                err(f"cave/{t}: {builds[t].size[2]} rows deep, nothing in front of the wall ({g.wall} rows go into it)")
        if g.procs not in PROCESSORS:
            err(f"cave/{g.name}: processor list {g.procs} not written")
        if not 1 <= g.rarity <= 256 or not 1 <= g.count <= 16 or not 0 <= g.relief <= 4:
            err(f"cave/{g.name}: rarity {g.rarity} / count {g.count} / relief {g.relief}")
        biomes = [g.biomes] if isinstance(g.biomes, str) else g.biomes
        for bid in biomes:
            if bid.startswith("#"):
                continue
            ns, n = bid.split(":")
            if ns == "expanse" and not os.path.exists(os.path.join(DATA, "worldgen", "biome", n + ".json")):
                err(f"cave/{g.name}: unknown biome {bid}")
            if ns == "minecraft" and n not in VANILLA_BIOMES:
                err(f"cave/{g.name}: unknown biome {bid}")
        check_predicate(ctx, f"cave/{g.name} ground", CAVE_FLOOR)
    for t in sorted(set(builds) - used):
        err(f"cave/{t}: template not used by any group")
    names = java_groups()
    if len(names) != len(set(names)):
        err("CaveThings.java adds a placed feature twice")
    if names != [g.name for g in GROUPS]:
        err(f"CaveThings.java adds {names}, gen_caves.py writes {[g.name for g in GROUPS]} (same names, same order)")
    for b in builds.values():
        if not any(p[1] == 0 for p in b.cells):
            err(f"cave/{b.name}: nothing in layer 0, so nothing is set into the floor")
    return ctx.problems, ctx.notes


VANILLA_BIOMES = set("""plains sunflower_plains snowy_plains ice_spikes desert swamp mangrove_swamp forest flower_forest
birch_forest dappled_forest dark_forest pale_garden old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga
taiga snowy_taiga savanna savanna_plateau windswept_hills windswept_gravelly_hills windswept_forest windswept_savanna
jungle sparse_jungle bamboo_jungle badlands eroded_badlands wooded_badlands meadow cherry_grove grove snowy_slopes
frozen_peaks jagged_peaks stony_peaks river frozen_river beach snowy_beach stony_shore mushroom_fields dripstone_caves
lush_caves deep_dark sulfur_caves""".split())


# ============================================================== preview
FLOOR_FOR = {"frozen_camp": "packed_ice", "ice_shrine": "snow_block", "snow_hut": "snow_block",
             "sand_shrine": "sandstone", "pot_cache": "sand", "dry_bones": "sand", "dig_site": "sand",
             "moss_shrine": "moss_block", "overgrown_cart": "moss_block", "moss_gazebo": "moss_block",
             "drip_cauldrons": "dripstone_block", "flowstone_shrine": "calcite", "crystal_altar": "calcite",
             "echo_arch": "deepslate", "soul_altar": "deepslate", "candle_cairn_deep": "deepslate",
             "waystone_deep": "deepslate", "obsidian_altar": "basalt", "basalt_ruin": "basalt",
             "sulfur_camp": "tuff", "sulfur_outpost": "smooth_basalt", "forager_hut": "mycelium",
             "mushroom_ring": "mycelium", "root_hermitage": "rooted_dirt", "shell_shrine": "sand",
             "beached_boat_tidal": "sand", "resin_camp": "expanse:amber"}


def preview_sheet(builds, out_dir, only=None):
    """Every template on a little slab of its usual cave floor."""
    import preview as P
    panels = []
    for name in sorted(builds):
        if only and name not in only:
            continue
        b = builds[name]
        sx, sy, sz = b.size
        floor = B(FLOOR_FOR.get(name, "stone"))
        cells = {}
        for x in range(-1, sx + 1):
            for z in range(-1, sz + 1):
                cells[(x, -1, z)] = B("stone")
                cells[(x, 0, z)] = floor
        for p, (st, _) in b.cells.items():
            cells[p] = st
        cells = {p: st for p, st in cells.items() if st.short not in ("air",)}
        panels.append(P.render(cells, 0, 18, title=f"{name} {sx}x{sy}x{sz}", entities=b.entities))
        panels.append(P.render(cells, 2, 18, title="NW", entities=b.entities))
    path = os.path.join(out_dir, "caves.png")
    P.stack([P.grid(panels, 4)], path)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", metavar="DIR", help="render a contact sheet of every template into DIR")
    ap.add_argument("--only", help="with --preview: comma-separated template names")
    ap.add_argument("--no-validate", action="store_true")
    args = ap.parse_args()

    clean()
    written = []
    builds = templates()
    for name, b in sorted(builds.items()):
        b.finalize()
        fit_height(b)
        for pos, st, why in floating_report(b):
            print(f"  warn {name}: {why}: {st} @ {pos}")
        path = os.path.join(DATA, "structure", SUB, name + ".nbt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        b.save(path)
        written.append(path)
    for pname, obj in PROCESSORS.items():
        written.append(write_json(f"worldgen/processor_list/{SUB}/{pname}.json", obj))
    for lname, obj in LOOT.items():
        written.append(write_json(f"loot_table/chests/{SUB}/{lname}.json", obj))
    for g in GROUPS:
        written.append(write_json(f"worldgen/feature/{SUB}/{g.name}.json", configured(g)))
        written.append(write_json(f"worldgen/placed_feature/{SUB}/{g.name}.json", placed(g)))
    print(f"caves: {len(builds)} templates, {len(PROCESSORS)} processor lists, {len(GROUPS)} placed features; "
          f"wrote {len(written)} files under {os.path.relpath(DATA, ROOT)}")
    for g in GROUPS:
        tall = [t for t, _ in g.items if builds[t].size[1] > PASSAGE_HEIGHT]
        print(f"  {g.name:16s} 1 in {g.rarity:2d} chunks x{g.count}  {', '.join(t for t, _ in g.items)}"
              + (f"  (chambers only: {', '.join(tall)})" if tall else ""))
    if args.preview:
        print("preview:", preview_sheet(builds, args.preview, set(args.only.split(",")) if args.only else None))
    if not args.no_validate:
        problems, notes = validate_all(builds)
        for n in notes:
            print("  note:", n)
        if problems:
            print(f"VALIDATION FAILED ({len(problems)} problems)")
            for p in problems:
                print("  -", p)
            sys.exit(1)
        print("validation OK")


if __name__ == "__main__":
    main()
