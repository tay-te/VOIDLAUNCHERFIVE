#!/usr/bin/env python3
"""The little things: small hand-made templates scattered over the land by the expanse:template feature
(world/feature/SurfaceTemplateFeature.java), one set per region (world/feature/LittleThings.java).

    python3 tools/structures/gen_micro.py                  # write + validate
    python3 tools/structures/gen_micro.py --preview DIR    # also render a contact sheet per template

Writes (and owns) only micro/ subfolders:
    data/expanse/structure/micro/<template>.nbt
    data/expanse/worldgen/processor_list/micro/<climate>.json
    data/expanse/worldgen/feature/micro/<group>.json           (configured features, type expanse:template)
    data/expanse/worldgen/placed_feature/micro/<group>.json
    data/expanse/loot_table/chests/micro/<name>.json

Template conventions are the structure tooling's (builder.py): x east, y up, z south; an unset cell is
structure void, so the land shows through; layer y=0 is the ground course. The feature puts layer 0 in
place of the top block of the lowest column under the footprint, so everything here is set *into* the
ground: stones and roots are bedded in layer 0, plants get a soil block under them, and nothing relies on
the land being exactly level. short_grass is a marker the climate's processor list turns into the local
flowers (or nothing), and the same lists moss, crack and strip each copy differently.

Proportions follow vanilla's own small things (fallen trees, forest rocks, desert wells, trail-ruin
fragments): a few blocks across, vanilla blocks used the way vanilla uses them, weathered, part buried.

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

from blocks import AIR, B, MOD_WOODS  # noqa: E402
from builder import DIRS, Build, fit_height, floating_report, slab, stairs  # noqa: E402
import loot as L  # noqa: E402
import nbt  # noqa: E402
from processors import (FOREST, HEATHER, KARST, LUMEN, STONE_WEATHER, aged_wood, flowers, plist, rot,  # noqa: E402
                        rule, rule_state, rules)

ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "expanse")
JAVA_GROUPS = os.path.join(ROOT, "src", "main", "java", "dev", "voidmc", "expanse", "world", "feature", "LittleThings.java")
SUB = "micro"
OUT_DIRS = {"structure": ".nbt", "worldgen/processor_list": ".json", "worldgen/feature": ".json",
            "worldgen/placed_feature": ".json", "loot_table/chests": ".json"}
MAX_FOOTPRINT = 24          # SurfaceTemplateFeature.MAX_FOOTPRINT
MARGIN = 8                  # clear_of_structures margin: a little thing keeps this far from any structure piece
LOST_CHEST = "expanse:chests/micro/lost_chest"
H4 = ["north", "east", "south", "west"]


def vec(d):
    return DIRS[d][0], DIRS[d][2]


def wn(wood, kind):
    """Id of a wood-set block: wn("oak", "log") -> oak_log, wn("redwood", "stripped_log") -> expanse:stripped_redwood_log."""
    ns = "expanse:" if wood in MOD_WOODS else ""
    if kind.startswith("stripped_"):
        return f"{ns}stripped_{wood}_{kind[len('stripped_'):]}"
    return f"{ns}{wood}_{kind}"


SOILS = {"grass_block", "podzol", "coarse_dirt", "dirt", "rooted_dirt", "moss_block", "mud", "sand", "red_sand",
         "opal_sand", "mycelium"}
MUSHROOM_SOILS = {"podzol", "mycelium"}           # BlockTags.MUSHROOM_GROW_BLOCK: they live there in daylight


def plant(b, x, z, what, soil, y=1):
    """A plant (name, state or ("tall", name)) at y with `soil` bedded under it; skips taken cells and
    cells over anything a plant could not grow from. Mushrooms always get podzol."""
    if not b.inb(x, y, z) or (x, y, z) in b.cells:
        return False
    if isinstance(what, tuple) and what[0] == "tall":
        if not b.inb(x, y + 1, z) or (x, y + 1, z) in b.cells:
            return False
    name = what[1] if isinstance(what, tuple) else (what if isinstance(what, str) else what.id)
    mushroom = name.endswith("_mushroom")
    below = b.get(x, y - 1, z)
    if below is None:
        b.set(x, y - 1, z, B("podzol") if mushroom else (B(soil) if isinstance(soil, str) else soil))
    elif below.short not in (MUSHROOM_SOILS if mushroom else SOILS):
        return False
    if isinstance(what, tuple) and what[0] == "tall":
        b.tall_plant(x, y, z, what[1])
    else:
        b.set(x, y, z, B(what) if isinstance(what, str) else what)
    return True


def stones(rng, mix):
    """Random state from [(name, weight)]."""
    tot = sum(w for _, w in mix)
    r = rng.random() * tot
    for name, w in mix:
        r -= w
        if r < 0:
            return B(name)
    return B(mix[-1][0])


# ============================================================== designs
# Every design returns a finished Build. Templates are named after what they are; the wood or stone they
# are made of is in the name when there are several.

def stump(name, wood, seed, soil=("podzol", "coarse_dirt"), height=2, top="moss_carpet", litter=None, extras=(),
          shelf=True, girth=1):
    """A felled or broken trunk: bedded in layer 0 with a flare of bark-covered roots round its foot, one
    root running out under the turf, a little moss or a mushroom on the cut, and the forest floor round it
    (podzol, a fern, fallen leaves). `girth` 2 is a 2x2 trunk (dark oak, redwood) with a ragged break."""
    rng = random.Random(seed)
    size = 5 + girth
    b = Build(name, size, height + 4, size, seed=seed)
    c = 2
    log = B(wn(wood, "log"), axis="y")
    bark = B(wn(wood, "wood"), axis="y")
    trunk = [(c + i, c + j) for i in range(girth) for j in range(girth)]
    tops = {}
    for k, (x, z) in enumerate(trunk):
        h = height if girth == 1 else max(1, height - [0, 1, 0, 2][k % 4] + (1 if k == 0 else 0))
        for y in range(0, h + 1):
            b.set(x, y, z, log)
        tops[(x, z)] = h
    # root flare: bark blocks bedded round the foot, a buttress or two climbing the trunk
    rim = sorted({(x + dx, z + dz) for (x, z) in trunk for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))} - set(trunk))
    rng.shuffle(rim)
    flare = rim[:max(2, len(rim) * 2 // 3)]
    for (x, z) in flare:
        b.set(x, 0, z, bark)
    for (x, z) in flare[:girth]:
        b.set(x, 1, z, bark)
    # one long root running out under the turf, away from the trunk
    x, z = flare[-1]
    tx, tz = next((tx, tz) for (tx, tz) in trunk if abs(tx - x) + abs(tz - z) == 1)
    dx, dz = x - tx, z - tz
    if b.inb(x + dx, 0, z + dz):
        b.set(x + dx, 0, z + dz, B(wn(wood, "log"), axis="x" if dx else "z"))
    # the cut: moss, or a mushroom
    for (x, z), h in tops.items():
        if top and rng.random() < 0.6 and b.get(x, h + 1, z) is None:
            b.set(x, h + 1, z, B(top) if isinstance(top, str) else top)
    # a shelf fungus on the side of the trunk
    if shelf:
        for (x, z) in trunk:
            for d in H4:
                ox, oz = vec(d)
                p = (x + ox, 1, z + oz)
                if (p[0], p[2]) not in trunk and b.get(*p) is None and tops[(x, z)] >= 1 and rng.random() < 0.25:
                    b.set(*p, B("shelf_mushroom", facing=d, age=rng.randint(0, 1)))
                    shelf = False
                    break
            if not shelf:
                break
    # the forest floor round it
    for (x, z) in [(x, z) for x in range(size) for z in range(size)]:
        if b.get(x, 0, z) is None and min(math.hypot(x - tx2, z - tz2) for tx2, tz2 in trunk) < 2.3 \
                and rng.random() < 0.45:
            b.set(x, 0, z, B(rng.choice(soil)))
    for what in extras:
        for _ in range(12):
            x, z = rng.randrange(size), rng.randrange(size)
            if (x, z) not in trunk and plant(b, x, z, what, rng.choice(soil) if not isinstance(soil, str) else soil):
                break
    if litter:
        for _ in range(4):
            x, z = rng.randrange(size), rng.randrange(size)
            if b.get(x, 1, z) is None and b.get(x, 0, z) is not None and b.get(x, 0, z).short in (
                    "podzol", "coarse_dirt", "grass_block", "rooted_dirt", "moss_block"):
                b.set(x, 1, z, B(litter, facing=rng.choice(H4), **{
                    "leaf_litter": {"segment_amount": rng.randint(1, 3)},
                    "pink_petals": {"flower_amount": rng.randint(1, 4)},
                    "wildflowers": {"flower_amount": rng.randint(1, 3)}}[litter]))
    return b


def jungle_stump(seed):
    """A broken jungle trunk two high, cocoa on one side and vines down another, moss on the break."""
    b = stump("stump_jungle", "jungle", seed, soil=("podzol", "coarse_dirt", "moss_block"), height=2,
              extras=["fern", "fern", ("tall", "large_fern")], shelf=False)
    b.set(2, 2, 1, B("cocoa", facing="south", age=2))
    b.set(3, 1, 2, B("cocoa", facing="west", age=1))
    for y in (2, 1):
        if b.get(1, y, 2) is None:
            b.set(1, y, 2, B("vine", east=True))
    return b


def fallen_log(name, wood, seed, length=5, soil=("podzol", "moss_block", "coarse_dirt"), mushrooms=True,
               glow=False, extras=("fern", "short_grass"), moss=True):
    """A trunk that came down long ago: a broken stub where it stood, a gap, then the log lying in the
    grass, broken once and mossed over, mushrooms on its back and a bracket fungus on its flank; the moss
    has crept off it onto the ground. Without `moss` (dry country) it is bare, split and sun-bleached."""
    rng = random.Random(seed)
    sx = length + 3
    b = Build(name, sx, 3, 4, seed=seed)
    z = 1
    log_x = B(wn(wood, "log"), axis="x")
    b.set(0, 0, z, B(wn(wood, "log"), axis="y"))           # the stub
    b.set(0, 1, z, B(wn(wood, "log"), axis="y"))
    b.set(0, 0, z + 1, B(wn(wood, "wood"), axis="y"))
    gap = rng.randint(3, length)                              # where it broke in two
    for x in range(2, 2 + length + 1):
        if x == gap + 1:                                      # rotted through: only a hump remains
            b.set(x, 0, z, B("moss_block" if moss else "coarse_dirt"))
            if moss:
                b.set(x, 1, z, B("moss_carpet"))
            continue
        b.set(x, 1, z, log_x if moss or rng.random() < 0.6 else B(wn(wood, "stripped_log"), axis="x"))
        r = rng.random()
        if moss and r < 0.45:
            b.set(x, 2, z, B("moss_carpet"))
        elif mushrooms and r < 0.62:
            b.set(x, 2, z, B(rng.choice(["brown_mushroom", "red_mushroom", "brown_mushroom"])))
    if moss:
        shelf_x = rng.choice([x for x in range(2, 2 + length) if x != gap + 1])
        b.set(shelf_x, 1, z + 1, B("shelf_mushroom", facing="south", age=1))
    if glow:
        for x in range(2, 2 + length):
            if rng.random() < 0.35 and b.get(x, 1, z - 1) is None:
                b.set(x, 1, z - 1, B("glow_lichen", south=True))
    for x in range(1, sx):                                    # moss creeping onto the ground beside it
        for zz in (z - 1, z + 1):
            if b.get(x, 0, zz) is None and rng.random() < 0.4:
                b.set(x, 0, zz, B(rng.choice(soil)))
    for _ in range(5):
        x, zz = rng.randrange(1, sx), rng.choice([0, 2, 3])
        plant(b, x, zz, rng.choice(list(extras)), rng.choice(soil))
    return b


def driftwood(seed):
    """Bleached logs washed up and half buried in the sand, a snapped branch still on one."""
    rng = random.Random(seed)
    b = Build("driftwood", 7, 2, 4, seed=seed)
    log = B("stripped_pale_oak_log", axis="x")
    b.set(0, 0, 1, log)                                       # one end under the sand
    for x in range(1, 5):
        b.set(x, 1, 1, log)
    b.set(5, 0, 1, B("stripped_pale_oak_wood", axis="x"))
    b.set(3, 1, 2, B("pale_oak_fence"))                       # the branch stub
    b.set(4, 0, 3, B("stripped_pale_oak_log", axis="x"))      # a second, smaller piece, mostly buried
    b.set(5, 1, 3, B("stripped_pale_oak_log", axis="x"))
    b.set(6, 0, 3, B("stripped_pale_oak_log", axis="x"))
    if rng.random() < 0.5:
        b.set(1, 1, 0, B("dead_bush"))
        b.set(1, 0, 0, B("sand"))
    return b


def cypress_knees(seed):
    """The knees of a bald cypress: bark-covered knobs standing out of the mud, mossed on top."""
    rng = random.Random(seed)
    b = Build("cypress_knees", 5, 4, 5, seed=seed)
    knees = [(1, 1, 1), (3, 1, 2), (2, 2, 3), (0, 1, 3), (4, 1, 4), (3, 2, 0)]
    for (x, h, z) in knees:
        for y in range(0, h + 1):
            b.set(x, y, z, B("expanse:willow_wood", axis="y") if y < h else B("expanse:willow_log", axis="y"))
        if rng.random() < 0.6:
            b.set(x, h + 1, z, B("moss_carpet"))
    for x in range(5):
        for z in range(5):
            if b.get(x, 0, z) is None and rng.random() < 0.3:
                b.set(x, 0, z, B(rng.choice(["mud", "moss_block", "mud"])))
    plant(b, 4, 1, "brown_mushroom", "podzol")
    return b


def snag_beehive(seed):
    """A dead oak, its top snapped off and its bark peeling, with a wild bees' nest in its side and
    flowers round its foot."""
    rng = random.Random(seed)
    b = Build("snag_beehive", 6, 8, 6, seed=seed)
    c = 2
    for y in range(0, 5):
        b.set(c, y, c, B("oak_log" if y < 3 or y == 4 else "stripped_oak_log", axis="y"))
    b.set(c, 5, c, B("stripped_oak_log", axis="y"))           # the snapped top
    for d in ("north", "west", "east"):                       # root flare
        ox, oz = vec(d)
        b.set(c + ox, 0, c + oz, B("oak_wood", axis="y"))
    b.set(c + 1, 4, c, B("oak_log", axis="x"))                # a bare limb with twigs
    b.set(c + 2, 4, c, B("oak_fence"))
    b.set(c + 2, 5, c, B("oak_fence"))
    b.set(c - 1, 3, c, B("oak_fence"))                        # a broken branch
    b.set(c, 2, c + 1, B("bee_nest", facing="south", honey_level=rng.randint(2, 5)),
          {"bees": nbt.List(nbt.TAG_COMPOUND, [
              {"entity_data": {"id": "minecraft:bee"}, "ticks_in_hive": nbt.Int(0), "min_ticks_in_hive": nbt.Int(600)}
              for _ in range(2)]), "id": "minecraft:beehive"})
    fl = ["dandelion", "poppy", "cornflower", "oxeye_daisy", "short_grass", "short_grass"]
    for (x, z) in [(1, 4), (2, 4), (3, 4), (4, 3), (4, 4), (0, 3), (3, 5), (1, 5), (5, 4)]:
        if rng.random() < 0.7:
            plant(b, x, z, rng.choice(fl), "grass_block")
    return b


def fox_den(seed):
    """A den dug under the roots of an old spruce stump: a dark hole into a grassy hump, roots arching
    over the mouth and hanging inside, fresh-dug soil in front, berries close by."""
    rng = random.Random(seed)
    b = Build("fox_den", 5, 4, 6, seed=seed)
    # the hole and the tunnel under the hump
    for z in (1, 2, 3):
        b.set(2, 0, z, AIR)
    b.set(2, 0, 3, B("hanging_roots"))
    for (x, z) in [(1, 1), (3, 1), (1, 2), (3, 2), (1, 3), (3, 3), (2, 4)]:
        b.set(x, 0, z, B(rng.choice(["rooted_dirt", "dirt", "coarse_dirt"])))
    # the hump: roots over the mouth, rooted soil, turf on the back
    b.set(1, 1, 2, B("spruce_wood", axis="y"))
    b.set(2, 1, 2, B("spruce_log", axis="x"))
    b.set(3, 1, 2, B("spruce_wood", axis="y"))
    for (x, z) in [(1, 3), (2, 3), (3, 3), (2, 4), (1, 4)]:
        b.set(x, 1, z, B("rooted_dirt" if (x, z) == (2, 3) else rng.choice(["grass_block", "podzol", "coarse_dirt"])))
    b.set(2, 2, 3, B("spruce_log", axis="y"))                 # the stump on top
    b.set(2, 3, 3, B("moss_carpet"))
    b.set(3, 2, 3, B("spruce_wood", axis="y"))
    # dug-out soil in front
    for (x, z) in [(1, 0), (2, 0), (3, 0), (0, 1), (4, 1)]:
        if rng.random() < 0.7:
            b.set(x, 0, z, B(rng.choice(["coarse_dirt", "coarse_dirt", "rooted_dirt", "podzol"])))
    plant(b, 4, 3, B("sweet_berry_bush", age=rng.choice([2, 3])), "podzol")
    plant(b, 0, 4, "fern", "podzol")
    plant(b, 4, 5, "short_grass", "grass_block")
    return b


def berry_clump(seed):
    """A tangle of sweet-berry bushes in a patch of forest floor, ferns round the edge."""
    rng = random.Random(seed)
    b = Build("berry_clump", 5, 3, 5, seed=seed)
    for (x, z) in [(1, 1), (2, 1), (2, 2), (3, 2), (1, 2), (2, 3), (3, 3)]:
        if rng.random() < 0.85:
            plant(b, x, z, B("sweet_berry_bush", age=rng.choice([1, 2, 3, 3])), rng.choice(["podzol", "coarse_dirt",
                                                                                          "grass_block"]))
    for (x, z) in [(0, 2), (4, 3), (1, 4), (3, 0), (4, 1)]:
        if rng.random() < 0.6:
            plant(b, x, z, rng.choice(["fern", "fern", ("tall", "large_fern"), "short_grass"]), "podzol")
    return b


def mushroom_ring(seed):
    """A fairy ring: brown and red mushrooms in a circle on a ring of darker soil."""
    rng = random.Random(seed)
    b = Build("mushroom_ring", 7, 2, 7, seed=seed)
    for x in range(7):
        for z in range(7):
            d = math.hypot(x - 3, z - 3)
            if 1.9 <= d <= 3.0:
                b.set(x, 0, z, B("podzol"))
                if rng.random() < 0.6:
                    b.set(x, 1, z, B("brown_mushroom" if rng.random() < 0.6 else "red_mushroom"))
    return b


def rocks(name, seed, mix, layout, moss=0.45, soil=None, plants=(), carpet="moss_carpet", top_slab=None):
    """A pile of stones bedded in the ground. `layout` is rows of digits, one row per z: the number of
    stones stacked in that column (0 = none), so the pile is drawn rather than computed. Moss carpets the
    tops, a slab softens an edge, and plants grow in the soil round the foot."""
    rng = random.Random(seed)
    sx, sz = len(layout[0]), len(layout)
    hmax = max(int(c) for row in layout for c in row)
    b = Build(name, sx, hmax + 2, sz, seed=seed)
    for z, row in enumerate(layout):
        for x, c in enumerate(row):
            h = int(c)
            for y in range(0, h):
                b.set(x, y, z, stones(rng, mix))
            if h >= 2 and rng.random() < moss:
                b.set(x, h, z, B(carpet))
            elif h >= 2 and top_slab and rng.random() < 0.25:
                b.set(x, h, z, B(top_slab, type="bottom"))
    for _ in range(len(plants) * 3):
        if not plants:
            break
        x, z = rng.randrange(sx), rng.randrange(sz)
        if layout[z][x] == "0":
            plant(b, x, z, rng.choice(plants), rng.choice(soil) if soil else "grass_block")
    return b


def cairn(seed):
    """A trail cairn: stones heaped on a bedded base, a flat stone on top."""
    rng = random.Random(seed)
    b = Build("cairn", 3, 5, 3, seed=seed)
    mix = [("cobblestone", 3), ("mossy_cobblestone", 2), ("stone", 2), ("andesite", 2)]
    for x in range(3):
        for z in range(3):
            if (x, z) != (2, 2):
                b.set(x, 0, z, stones(rng, mix))
    for (x, z) in [(1, 1), (0, 1), (1, 0), (2, 1), (1, 2)]:
        b.set(x, 1, z, stones(rng, mix))
    b.set(1, 2, 1, stones(rng, [("cobblestone", 1), ("stone", 1)]))
    b.set(0, 2, 1, B("cobblestone_slab", type="bottom"))
    b.set(1, 3, 1, B("andesite_slab", type="bottom"))
    if rng.random() < 0.5:
        b.set(2, 2, 1, B("moss_carpet"))
    return b


def old_wall(seed):
    """What is left of a dry-stone field wall: a bedded footing, a few courses standing, a gap where it
    fell, the fallen stones in the grass."""
    rng = random.Random(seed)
    b = Build("old_wall", 8, 4, 3, seed=seed)
    mix = [("cobblestone", 3), ("mossy_cobblestone", 3), ("stone", 1), ("andesite", 1)]
    z = 1
    heights = [2, 2, 1, 0, 0, 1, 2, 1]
    for x, h in enumerate(heights):
        if x != 4:
            b.set(x, 0, z, stones(rng, mix))
        for y in range(1, h + 1):
            b.set(x, y, z, stones(rng, mix))
    for x, h in enumerate(heights):                           # coping: a wall cap on the taller runs
        if h == 2 and rng.random() < 0.6:
            b.set(x, 3, z, B(rng.choice(["mossy_cobblestone_wall", "cobblestone_wall"])))
        elif h >= 1 and rng.random() < 0.35:
            b.set(x, h + 1, z, B("moss_carpet"))
    b.set(3, 1, 2, B("mossy_cobblestone"))                    # fallen stones
    b.set(4, 0, 0, B("cobblestone"))
    b.set(5, 1, 0, B("mossy_cobblestone_slab", type="bottom"))
    for x in range(8):
        for zz in (0, 2):
            if rng.random() < 0.35:
                plant(b, x, zz, "short_grass", "grass_block")
    return b


def bones(seed):
    """The bleached ribs of something large, half sunk in the dry ground: a buried spine, three pairs of
    ribs standing out of the sand with gaps between them, the skull a little way off."""
    rng = random.Random(seed)
    b = Build("bones", 9, 4, 5, seed=seed)
    z = 2
    for x in range(1, 8):
        if x != 5:
            b.set(x, 0, z, B("bone_block", axis="x"))         # the spine, buried (broken once)
    for x, h in ((2, 1), (4, 2), (6, 1)):
        for zz in (1, 3):
            if (x, zz) == (6, 3):
                continue                                      # a rib missing
            for y in range(0, h + 1):
                b.set(x, y, zz, B("bone_block", axis="y"))
    b.set(6, 0, 4, B("bone_block", axis="x"))                 # a loose rib, fallen and half buried
    b.set(7, 0, 4, B("bone_block", axis="x"))
    b.set(0, 1, 1, B("skeleton_skull", rotation=rng.choice([3, 5, 11])))
    b.set(8, 1, 0, B("dead_bush"))
    return b


def spring_ring(seed):
    """A spring rising in a ring of stones: clear water at ground level, the stones mossy, ferns round it.
    Some of the ring is dressed stone: someone once kept it."""
    rng = random.Random(seed)
    b = Build("spring_ring", 6, 3, 6, seed=seed)
    water = {(2, 2), (3, 2), (2, 3), (3, 3)}
    mix = [("mossy_cobblestone", 4), ("cobblestone", 2), ("stone", 2), ("mossy_stone_bricks", 2), ("andesite", 1)]
    for x in range(6):
        for z in range(6):
            if (x, z) in water:
                b.set(x, 0, z, B("water", level=0))
            elif 1 <= x <= 4 and 1 <= z <= 4:
                b.set(x, 0, z, stones(rng, mix))
    for (x, z) in [(1, 1), (4, 2), (2, 4), (1, 3)]:            # the taller stones of the ring
        b.set(x, 1, z, stones(rng, mix))
        if rng.random() < 0.5:
            b.set(x, 2, z, B("moss_carpet"))
    b.set(4, 1, 4, B("mossy_stone_brick_slab", type="bottom"))
    b.set(3, 1, 2, B("lily_pad"))
    for (x, z) in [(0, 2), (5, 3), (2, 0), (3, 5), (0, 4), (5, 1)]:
        if rng.random() < 0.7:
            plant(b, x, z, rng.choice(["fern", "short_grass", "short_grass"]), "grass_block")
    return b


def foundation_flowers(seed):
    """The footing of a cottage long gone: a broken outline of bedded stones, a corner still standing,
    a worn threshold, and flowers grown up thick where the floor was."""
    rng = random.Random(seed)
    b = Build("foundation_flowers", 7, 3, 6, seed=seed)
    mix = [("cobblestone", 3), ("mossy_cobblestone", 3), ("stone_bricks", 2), ("mossy_stone_bricks", 2),
           ("cracked_stone_bricks", 1)]
    for x in range(7):
        for z in range(6):
            edge = x in (0, 6) or z in (0, 5)
            if edge and not (x == 3 and z == 5) and rng.random() < 0.85:
                b.set(x, 0, z, stones(rng, mix))
    b.set(3, 0, 5, B("mossy_stone_brick_slab", type="top"))   # the threshold
    for (x, z) in [(0, 0), (1, 0), (0, 1), (6, 5)]:            # a corner still standing
        b.set(x, 1, z, stones(rng, mix))
    b.set(0, 2, 0, B("mossy_cobblestone_wall"))
    fl = ["poppy", "dandelion", "oxeye_daisy", "cornflower", "azure_bluet", "short_grass", "short_grass"]
    for x in range(1, 6):
        for z in range(1, 5):
            r = rng.random()
            if r < 0.5:
                plant(b, x, z, rng.choice(fl), "grass_block")
            elif r < 0.6:
                b.set(x, 0, z, B("coarse_dirt"))
    plant(b, 2, 2, ("tall", "rose_bush"), "grass_block") or plant(b, 3, 2, ("tall", "rose_bush"), "grass_block")
    plant(b, 4, 3, ("tall", "lilac"), "grass_block")
    return b


def stone_bench(seed):
    """A worn stone bench by the way, flagstones in front of it, one end broken off."""
    rng = random.Random(seed)
    b = Build("stone_bench", 4, 2, 3, seed=seed)
    b.set(0, 1, 1, stairs("stone_brick", "north"))
    b.set(1, 1, 1, stairs("mossy_stone_brick", "north"))
    b.set(2, 1, 1, slab("stone_brick"))
    for x in range(3):
        b.set(x, 0, 1, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cobblestone"])))
    for x in range(4):
        if rng.random() < 0.6:
            b.set(x, 0, 2, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "gravel"])))
    plant(b, 3, 1, "short_grass", "grass_block")
    plant(b, 3, 0, "short_grass", "grass_block")
    return b


def campfire(name, seed, seat_wood, ash=("coarse_dirt", "gravel", "coarse_dirt"), plants=("short_grass",),
             soil="grass_block", stripped=False):
    """A camp left cold: a dead campfire in a patch of ash and trodden soil, logs rolled round it to sit on."""
    rng = random.Random(seed)
    b = Build(name, 5, 2, 5, seed=seed)
    b.campfire(2, 1, 2, lit=False, facing=rng.choice(H4))
    kind = "stripped_log" if stripped else "log"
    for (x, z) in [(1, 1), (2, 1), (3, 1), (1, 2), (3, 2), (1, 3), (2, 3), (3, 3), (2, 2)]:
        if (x, z) == (2, 2) or rng.random() < 0.7:
            b.set(x, 0, z, B(rng.choice(ash)))
    b.set(1, 1, 0, B(wn(seat_wood, kind), axis="x"))          # north seat, two logs long
    b.set(2, 1, 0, B(wn(seat_wood, kind), axis="x"))
    b.set(0, 1, 2, B(wn(seat_wood, kind), axis="z"))          # west seat
    b.set(0, 1, 3, B(wn(seat_wood, kind), axis="z"))
    b.set(4, 1, 3, B(wn(seat_wood, "stripped_log" if not stripped else "log"), axis="z"))  # a short one east
    for (x, z) in [(4, 0), (0, 0), (4, 4), (1, 4), (3, 4)]:
        if rng.random() < 0.5:
            plant(b, x, z, rng.choice(plants), soil)
    return b


def woodpile(seed):
    """Split logs stacked to dry and never fetched: moss on the top course, a chopping block beside."""
    rng = random.Random(seed)
    b = Build("woodpile", 6, 4, 4, seed=seed)
    for x in range(0, 4):
        b.set(x, 0, 1, B("coarse_dirt") if rng.random() < 0.5 else B("dirt"))
        b.set(x, 1, 1, B("spruce_log", axis="z"))
        if x < 3:
            b.set(x, 2, 1, B("spruce_log", axis="z"))
            if rng.random() < 0.5:
                b.set(x, 3, 1, B("moss_carpet"))
    b.set(3, 2, 1, B("spruce_slab", type="bottom"))
    b.set(5, 0, 2, B("spruce_log", axis="y"))                 # chopping block, bedded
    b.set(5, 1, 2, B("spruce_log", axis="y"))
    b.set(4, 1, 2, B("spruce_slab", type="bottom"))           # a split round lying by it
    for (x, z) in [(4, 2), (4, 3), (5, 3), (5, 1)]:
        if b.get(x, 0, z) is None and rng.random() < 0.6:
            b.set(x, 0, z, B("coarse_dirt"))
    plant(b, 0, 2, "fern", "podzol")
    return b


def old_fence(seed):
    """A run of old fence crossing nowhere: posts leaning out of the grass, rails gone between them."""
    rng = random.Random(seed)
    b = Build("old_fence", 8, 3, 3, seed=seed)
    z = 1
    run = {0: 2, 1: 1, 2: 1, 4: 1, 6: 1, 7: 2}
    for x, h in run.items():
        for y in range(1, h + 1):
            b.set(x, y, z, B("spruce_fence"))
    for x in (0, 7):
        b.set(x, 0, z, B(rng.choice(["cobblestone", "mossy_cobblestone"])))
    b.set(4, 1, 2, B("spruce_trapdoor", facing="north", half="bottom"))   # a fallen board in the grass
    for x in range(8):
        for zz in (0, 1, 2):
            if b.get(x, 1, zz) is None and rng.random() < 0.3:
                plant(b, x, zz, rng.choice(["short_grass", "short_grass", ("tall", "tall_grass")]), "grass_block")
    return b


def signpost(seed):
    """A fingerpost where two ways once met: boards pointing two ways, the lettering long gone grey."""
    rng = random.Random(seed)
    b = Build("signpost", 3, 4, 3, seed=seed)
    b.set(1, 0, 1, B("mossy_cobblestone"))
    for y in (1, 2, 3):
        b.set(1, y, 1, B("spruce_fence"))
    b.sign(2, 2, 1, "spruce", ["", "<--", "", ""], wall_facing="east", color="gray")
    b.sign(1, 3, 0, "spruce", ["", "-->", "", ""], wall_facing="north", color="gray")
    if rng.random() < 0.5:
        b.set(0, 0, 1, B("cobblestone"))
    plant(b, 0, 2, "short_grass", "grass_block")
    plant(b, 2, 0, "short_grass", "grass_block")
    return b


def lantern_post(seed):
    """A way-lantern on a crooked post, its footing sunk in the ground."""
    rng = random.Random(seed)
    b = Build("lantern_post", 3, 5, 3, seed=seed)
    b.set(1, 0, 1, B(rng.choice(["mossy_cobblestone", "cobblestone"])))
    for y in (1, 2, 3):
        b.set(1, y, 1, B("spruce_fence"))
    b.set(2, 3, 1, B("spruce_fence"))
    b.set(2, 2, 1, B("lantern", hanging=True))
    b.set(0, 0, 1, B("cobblestone"))
    b.set(1, 0, 2, B("mossy_cobblestone"))
    plant(b, 0, 0, "short_grass", "grass_block")
    return b


def hay_spill(seed):
    """Where a cart lost part of its load: two ruts through the grass, bales tumbled onto the verge,
    one burst and settling into the ground, a board off the cart."""
    rng = random.Random(seed)
    b = Build("hay_spill", 7, 3, 5, seed=seed)
    for x in range(7):
        for z in (1, 3):
            if rng.random() < (0.85 if 1 <= x <= 5 else 0.45):
                b.set(x, 0, z, B(rng.choice(["dirt_path", "dirt_path", "coarse_dirt"])))
    b.set(2, 1, 4, B("hay_block", axis="x"))
    b.set(3, 1, 4, B("hay_block", axis="y"))
    b.set(5, 0, 0, B("hay_block", axis="z"))                  # the burst one, sunk
    b.set(1, 1, 0, B("spruce_trapdoor", facing="east", half="bottom"))
    for x in range(7):
        if rng.random() < 0.4:
            plant(b, x, 2, "short_grass", "grass_block")
    return b


def wheel_post(seed):
    """A cart wheel leant against a post by the way and left there."""
    rng = random.Random(seed)
    b = Build("wheel_post", 3, 3, 3, seed=seed)
    b.set(1, 0, 1, B("cobblestone"))
    b.set(1, 1, 1, B("spruce_fence"))
    b.set(1, 2, 1, B("spruce_fence"))
    b.set(2, 1, 1, B("spruce_trapdoor", facing="east", half="bottom", open=True))   # panel against the post
    for (x, z) in [(0, 0), (0, 2), (2, 2), (1, 2)]:
        if rng.random() < 0.6:
            plant(b, x, z, rng.choice(["short_grass", "short_grass", ("tall", "tall_grass")]), "grass_block")
    return b


def lost_chest(seed):
    """A chest someone buried or lost: lid at ground level, a stone set beside it as a marker."""
    b = Build("lost_chest", 3, 2, 3, seed=seed)
    b.chest(1, 0, 1, "south", LOST_CHEST)
    b.set(2, 0, 0, B("cobblestone"))
    b.set(2, 1, 0, B("mossy_cobblestone_slab", type="bottom"))
    return b


def prismite_rocks(seed):
    """A split boulder with a crust of calcite and prismite crystals growing in the crack."""
    rng = random.Random(seed)
    b = rocks("prismite_rocks", seed, [("stone", 3), ("calcite", 3), ("tuff", 1)],
              ["0110", "1221", "1210", "0100"], moss=0.0)
    b.set(2, 1, 1, B("expanse:prismite_block"))
    b.set(2, 2, 1, B("expanse:prismite_cluster", facing="up"))
    if b.get(3, 1, 1) is None:
        b.set(3, 1, 1, B("expanse:prismite_cluster", facing="up"))
    if rng.random() < 0.6:
        b.set(0, 1, 2, B("expanse:prismite_cluster", facing="up"))
    b.set(0, 0, 2, B("calcite"))
    return b


# ============================================================== the catalogue
MOSSY = [("mossy_cobblestone", 4), ("cobblestone", 2), ("stone", 2), ("andesite", 1)]
BOULDER = [("stone", 4), ("andesite", 3), ("cobblestone", 1), ("tuff", 1)]
SANDSTONE = [("sandstone", 5), ("smooth_sandstone", 2), ("cut_sandstone", 1)]
OPAL = [("expanse:opal_sandstone", 5), ("expanse:smooth_opal_sandstone", 2), ("expanse:cut_opal_sandstone", 1)]
LIMESTONE = [("expanse:limestone", 4), ("expanse:mossy_limestone", 3), ("expanse:polished_limestone", 1)]


def templates():
    """name -> Build (finished, not finalized)."""
    out = {}

    def add(b):
        out[b.name] = b

    S = 7001
    add(stump("stump_oak", "oak", S + 1, litter="leaf_litter", extras=["fern", "brown_mushroom"]))
    add(stump("stump_birch", "birch", S + 2, height=1, litter="leaf_litter", extras=["short_grass", "fern"]))
    add(stump("stump_spruce", "spruce", S + 3, extras=["fern", ("tall", "large_fern")]))
    add(stump("stump_dark_oak", "dark_oak", S + 4, girth=2, height=2, extras=["brown_mushroom", "red_mushroom", "fern"],
              litter="leaf_litter"))
    add(stump("stump_redwood", "redwood", S + 5, girth=2, height=3, extras=["fern", ("tall", "large_fern"), "fern"]))
    add(stump("stump_acacia", "acacia", S + 6, height=1, soil=("coarse_dirt", "coarse_dirt", "grass_block"), top=None,
              extras=["short_dry_grass", "short_dry_grass"], shelf=False))
    add(stump("stump_cherry", "cherry", S + 7, height=1, soil=("grass_block", "coarse_dirt"), litter="pink_petals",
              extras=["short_grass"]))
    add(stump("stump_wisteria", "wisteria", S + 8, height=2, soil=("grass_block", "coarse_dirt", "moss_block"),
              litter="pink_petals", extras=["fern", "allium"]))
    add(jungle_stump(S + 9))
    add(fallen_log("log_oak", "oak", S + 10, length=5))
    add(fallen_log("log_wisteria", "wisteria", S + 11, length=4, soil=("moss_block", "grass_block", "coarse_dirt"),
                   extras=("fern", "allium", "short_grass")))
    add(fallen_log("log_lumen", "lumen", S + 12, length=5, soil=("moss_block", "moss_block", "podzol"), glow=True,
                   extras=("expanse:glowcap", "fern", "short_grass")))
    add(fallen_log("log_baobab", "baobab", S + 13, length=4, soil=("coarse_dirt", "coarse_dirt", "grass_block"),
                   mushrooms=False, moss=False, extras=("short_dry_grass", "short_dry_grass", "dead_bush")))
    add(driftwood(S + 14))
    add(cypress_knees(S + 15))
    add(snag_beehive(S + 16))
    add(fox_den(S + 17))
    add(berry_clump(S + 18))
    add(mushroom_ring(S + 19))
    add(rocks("mossy_rocks", S + 20, MOSSY, ["01100", "12210", "02310", "00100"], soil=["moss_block", "grass_block"],
              plants=["fern", "short_grass", "fern"]))
    add(rocks("mossy_rocks_low", S + 21, MOSSY, ["11000", "12110", "01121", "00010"], soil=["moss_block", "grass_block"],
              plants=["fern", "short_grass"]))
    add(rocks("boulder", S + 22, BOULDER, ["0110", "1221", "1331", "0120"], moss=0.15, soil=["gravel", "coarse_dirt"],
              plants=["short_grass"], top_slab="andesite_slab"))
    add(rocks("boulders_scattered", S + 23, BOULDER, ["11000", "12000", "00010", "00121", "00010"], moss=0.1,
              soil=["gravel", "grass_block"], plants=["short_grass"], top_slab="stone_slab"))
    add(rocks("limestone_rocks", S + 24, LIMESTONE, ["0110", "1231", "0121", "0010"], soil=["moss_block", "grass_block"],
              plants=["fern", "short_grass"], top_slab="expanse:limestone_slab"))
    add(rocks("sandstone_rocks", S + 25, SANDSTONE, ["01100", "12210", "01221", "00110"], moss=0.0, soil=["sand"],
              plants=["dead_bush"], top_slab="sandstone_slab"))
    add(rocks("opal_rocks", S + 26, OPAL, ["0110", "1221", "0121"], moss=0.0, soil=["expanse:opal_sand"],
              plants=["dead_bush"], top_slab="expanse:opal_sandstone_slab"))
    add(prismite_rocks(S + 27))
    add(cairn(S + 28))
    add(old_wall(S + 29))
    add(bones(S + 30))
    add(spring_ring(S + 31))
    add(foundation_flowers(S + 32))
    add(stone_bench(S + 33))
    add(campfire("campfire_spruce", S + 34, "spruce", plants=("short_grass", "fern")))
    add(campfire("campfire_acacia", S + 35, "acacia", ash=("coarse_dirt", "coarse_dirt", "gravel"),
                 plants=("short_dry_grass", "dead_bush"), soil="coarse_dirt", stripped=True))
    add(woodpile(S + 36))
    add(old_fence(S + 37))
    add(signpost(S + 38))
    add(lantern_post(S + 39))
    add(hay_spill(S + 40))
    add(wheel_post(S + 41))
    add(lost_chest(S + 42))
    return out


# ============================================================== processor lists (per climate)
def recolor(pairs):
    """Rules swapping a whole block family for another (probability 1): sandstone -> red sandstone. A rule
    processor applies one rule per block, so these go in a processor of their own after the weathering."""
    out = []
    for src, dst in pairs:
        if src.endswith("_slab"):
            out += [rule_state(B(src, type=t), 1.0, B(dst, type=t)) for t in ("bottom", "top")]
        else:
            out.append(rule(src, 1.0, dst))
    return out


NO_MOSS = [rule("mossy_cobblestone", 1.0, "cobblestone"), rule("mossy_cobblestone_slab", 1.0, "cobblestone"),
           rule("moss_carpet", 1.0, "air"), rule("moss_block", 1.0, "coarse_dirt")]
CARPET_ROT = [rule("moss_carpet", 0.3, "air"), rule("leaf_litter", 0.3, "air")]
WALL_ROT = rot(0.8, ["cobblestone_wall", "mossy_cobblestone_wall"])
NORTH = flowers(("fern", 0.35), ("sweet_berry_bush", 0.04), ("air", 0.25))
SNOWY = flowers(("expanse:frostbloom", 0.12), ("air", 0.6))
PEAKS = flowers(("expanse:edelweiss", 0.15), ("azure_bluet", 0.08), ("fern", 0.1), ("air", 0.35))
VALE = flowers(("allium", 0.08), ("pink_tulip", 0.06), ("azure_bluet", 0.08), ("lily_of_the_valley", 0.06),
               ("fern", 0.1), ("air", 0.2))
BAYOU = flowers(("fern", 0.3), ("blue_orchid", 0.1), ("air", 0.2))
DRY = flowers(("short_dry_grass", 0.4), ("tall_dry_grass", 0.1), ("dead_bush", 0.1), ("air", 0.3))
SAND_WEATHER = [rule("cut_sandstone", 0.2, "sandstone"), rule("smooth_sandstone", 0.15, "sandstone")]
OPAL_WEATHER = [rule("expanse:smooth_opal_sandstone", 0.15, "expanse:opal_sandstone"),
                rule("expanse:cut_opal_sandstone", 0.2, "expanse:opal_sandstone")]
LIMESTONE_WEATHER = [rule("expanse:polished_limestone", 0.3, "expanse:limestone"),
                     rule("expanse:limestone", 0.15, "expanse:mossy_limestone")]
WARM_STONE = [rule("andesite", 0.35, "granite"), rule("stone", 0.15, "granite"), rule("cobblestone", 0.2, "stone")]
BADLANDS = recolor([("sandstone", "red_sandstone"), ("smooth_sandstone", "smooth_red_sandstone"),
                    ("cut_sandstone", "cut_red_sandstone"), ("sand", "red_sand"),
                    ("sandstone_slab", "red_sandstone_slab")])

PROCESSORS = {
    "temperate": plist(STONE_WEATHER, aged_wood("oak", "birch", "spruce", "dark_oak", "cherry"), FOREST, CARPET_ROT,
                       WALL_ROT),
    "north": plist(STONE_WEATHER, aged_wood("spruce", "redwood"), NORTH, CARPET_ROT),
    "moor": plist(STONE_WEATHER, HEATHER, [rule("moss_carpet", 0.4, "air")], WALL_ROT),
    "peaks": plist(STONE_WEATHER, PEAKS, [rule("moss_carpet", 0.5, "air"), rule("cobblestone", 0.2, "stone")]),
    "snow": plist([rule("cobblestone", 0.2, "stone"), rule("andesite", 0.1, "stone")], aged_wood("spruce"), SNOWY,
                  [rule("moss_carpet", 0.7, "air"), rule("mossy_cobblestone", 0.5, "cobblestone"),
                   rule("leaf_litter", 1.0, "air")]),
    "warm": plist(NO_MOSS, WARM_STONE, aged_wood("acacia", "baobab"), DRY),
    "desert": plist(NO_MOSS, SAND_WEATHER, DRY),
    "badlands": plist(NO_MOSS, SAND_WEATHER, DRY, rules(*BADLANDS)),   # recoloured after weathering
    "dunes": plist(NO_MOSS, OPAL_WEATHER, DRY),
    "coast": plist(NO_MOSS, SAND_WEATHER, flowers(("short_dry_grass", 0.2), ("air", 0.5))),
    "wet": plist(STONE_WEATHER, [rule("cobblestone", 0.4, "mossy_cobblestone")], aged_wood("oak", "willow"), BAYOU),
    "jungle": plist(STONE_WEATHER, [rule("cobblestone", 0.4, "mossy_cobblestone")], aged_wood("jungle"), KARST),
    "karst": plist(STONE_WEATHER, LIMESTONE_WEATHER, aged_wood("jungle"), KARST),
    "lumen": plist(STONE_WEATHER, aged_wood("lumen"), LUMEN, [rule("brown_mushroom", 0.5, "expanse:glowcap"),
                                                              rule("red_mushroom", 0.7, "expanse:glowcap")]),
    "vale": plist(STONE_WEATHER, aged_wood("wisteria", "oak"), VALE, CARPET_ROT),
    "cherry": plist(STONE_WEATHER, aged_wood("cherry"), flowers(("pink_tulip", 0.08), ("white_tulip", 0.06),
                                                               ("fern", 0.1), ("air", 0.3)), CARPET_ROT),
}


# ============================================================== loot
LOOT = {
    # a traveller's cache: about a buried-treasure chest's worth of common things, less the treasure
    "micro/lost_chest": L.table(
        "micro/lost_chest",
        L.pool((3, 6), L.item("bread", 4, (1, 3)), L.item("coal", 4, (2, 6)), L.item("iron_nugget", 4, (3, 9)),
               L.item("gold_nugget", 3, (2, 7)), L.item("string", 3, (1, 4)), L.item("leather", 3, (1, 3)),
               L.item("torch", 3, (2, 6)), L.item("arrow", 2, (2, 8)), L.item("flint", 2, (1, 3)),
               L.item("iron_ingot", 2, (1, 2)), L.item("emerald", 2, (1, 3)), L.item("pumpkin_seeds", 1, (1, 4)),
               L.item("paper", 1, (1, 3)), L.item("candle", 1, (1, 2)), L.item("compass", 1), L.item("name_tag", 1)),
        L.pool((0, 1), L.empty(5), L.item("iron_shovel", 2, damage=(0.3, 0.8)),
               L.item("iron_pickaxe", 1, damage=(0.2, 0.7)), L.item("leather_boots", 2, damage=(0.3, 0.8)),
               L.item("bow", 1, damage=(0.3, 0.9)), L.book(1),
               L.item("explorer_pottery_sherd", 1), L.item("howl_pottery_sherd", 1))),
}

# ============================================================== features
SOIL = {"type": "minecraft:matching_block_tag", "tag": "minecraft:substrate_overworld"}


def any_of(*preds):
    return {"type": "minecraft:any_of", "predicates": list(preds)}


def tag(t):
    return {"type": "minecraft:matching_block_tag", "tag": t}


def blocks_(*ids):
    return {"type": "minecraft:matching_blocks", "blocks": [i if ":" in i else "minecraft:" + i for i in ids]}


ROCKY = any_of(SOIL, tag("minecraft:base_stone_overworld"), blocks_("gravel", "calcite", "snow_block"))
SNOWY_GROUND = any_of(SOIL, blocks_("snow_block", "gravel"), tag("minecraft:base_stone_overworld"))
DRY_GROUND = any_of(tag("minecraft:sand"), tag("minecraft:terracotta"), SOIL,
                    blocks_("sandstone", "red_sandstone", "expanse:opal_sandstone"))
SANDY = any_of(tag("minecraft:sand"), blocks_("gravel", "sandstone"), SOIL)
NATURAL = any_of(SOIL, tag("minecraft:sand"), tag("minecraft:terracotta"), tag("minecraft:base_stone_overworld"),
                 blocks_("gravel", "snow_block", "calcite", "sandstone", "red_sandstone", "expanse:opal_sandstone"))


class Group:
    """A placed feature (expanse:micro/<name>) drawing one template from a weighted list, once in `rarity`
    chunks; the biomes it goes to are listed in LittleThings.java."""

    def __init__(self, name, rarity, items, procs, ground=SOIL, relief=2, biomes=""):
        self.name, self.rarity, self.items, self.procs, self.ground, self.relief = name, rarity, items, procs, ground, relief
        self.biomes = biomes


GROUPS = [
    # ---- natural things: every one or two chunks
    Group("oak_woods", 2, [("stump_oak", 5), ("mossy_rocks", 3), ("mossy_rocks_low", 2), ("mushroom_ring", 2),
                           ("log_oak", 1), ("snag_beehive", 1)], "temperate",
          biomes="forest, flower forest, windswept forest, dappled forest"),
    Group("birch_woods", 2, [("stump_birch", 5), ("mossy_rocks", 2), ("mossy_rocks_low", 2), ("mushroom_ring", 2),
                             ("snag_beehive", 1)], "temperate", biomes="birch forests"),
    Group("dark_woods", 2, [("stump_dark_oak", 5), ("mushroom_ring", 4), ("mossy_rocks", 2)], "temperate",
          biomes="dark forest"),
    Group("taiga", 2, [("stump_spruce", 5), ("berry_clump", 3), ("mossy_rocks", 2), ("boulders_scattered", 2),
                       ("fox_den", 2)], "north", biomes="taigas, snowy taiga"),
    Group("redwood", 2, [("stump_redwood", 4), ("berry_clump", 2), ("fox_den", 2), ("mossy_rocks", 2),
                         ("mossy_rocks_low", 2), ("mushroom_ring", 1)], "north", biomes="redwood giants"),
    Group("grassland", 3, [("boulder", 2), ("boulders_scattered", 1), ("snag_beehive", 2), ("foundation_flowers", 2),
                           ("mossy_rocks_low", 1), ("spring_ring", 1)], "temperate",
          biomes="plains, sunflower plains, meadow"),
    Group("moor", 2, [("boulder", 3), ("boulders_scattered", 3), ("cairn", 3), ("old_wall", 3), ("mossy_rocks", 1),
                      ("spring_ring", 1)], "moor", ROCKY, biomes="heather moor, windswept hills"),
    Group("peaks", 3, [("boulder", 3), ("boulders_scattered", 3), ("cairn", 3), ("mossy_rocks_low", 1)], "peaks",
          ROCKY, biomes="verdant peaks, stony peaks"),
    Group("crystal", 3, [("prismite_rocks", 4), ("boulders_scattered", 2), ("cairn", 1)], "peaks", ROCKY,
          biomes="prismatic peaks"),
    Group("snow", 3, [("boulder", 3), ("boulders_scattered", 2), ("cairn", 2), ("stump_spruce", 2), ("fox_den", 1)],
          "snow", SNOWY_GROUND, biomes="snowy plains, grove, frostbloom tundra"),
    Group("savanna", 2, [("stump_acacia", 4), ("boulder", 2), ("boulders_scattered", 2), ("bones", 1)], "warm",
          DRY_GROUND, biomes="savannas"),
    Group("steppe", 2, [("log_baobab", 3), ("boulder", 2), ("boulders_scattered", 3), ("bones", 1)], "warm",
          DRY_GROUND, biomes="amber steppe"),
    Group("desert", 3, [("sandstone_rocks", 4), ("bones", 2)], "desert", DRY_GROUND, biomes="desert"),
    Group("badlands", 3, [("sandstone_rocks", 4), ("bones", 2)], "badlands", DRY_GROUND, biomes="badlands"),
    Group("dunes", 3, [("opal_rocks", 4), ("bones", 2)], "dunes", DRY_GROUND, biomes="opal dunes"),
    Group("swamp", 3, [("stump_oak", 3), ("mushroom_ring", 3), ("mossy_rocks_low", 1)], "wet",
          biomes="swamp, mangrove swamp"),
    Group("bayou", 3, [("cypress_knees", 5), ("mushroom_ring", 2)], "wet", biomes="willow bayou"),
    Group("jungle", 2, [("stump_jungle", 4), ("mossy_rocks", 3), ("mossy_rocks_low", 2), ("spring_ring", 1)], "jungle",
          biomes="jungles, cloud forest"),
    Group("karst", 2, [("limestone_rocks", 5), ("stump_jungle", 1), ("spring_ring", 1)], "karst", ROCKY,
          biomes="jade karst"),
    Group("lumen", 2, [("log_lumen", 4), ("mossy_rocks", 2), ("mushroom_ring", 2), ("spring_ring", 1)], "lumen",
          biomes="lumen grove"),
    Group("vale", 2, [("stump_wisteria", 3), ("log_wisteria", 3), ("foundation_flowers", 2), ("stone_bench", 1),
                      ("spring_ring", 1)], "vale", biomes="wisteria vale"),
    Group("cherry", 2, [("stump_cherry", 4), ("stone_bench", 1), ("foundation_flowers", 1), ("mossy_rocks_low", 1)],
          "cherry", biomes="cherry grove"),
    Group("coast", 4, [("driftwood", 4), ("sandstone_rocks", 2)], "coast", SANDY, biomes="beach, palm coast"),
    # ---- traces of people: rarer, on gentler ground
    Group("traces_lowland", 6, [("signpost", 2), ("hay_spill", 2), ("wheel_post", 2), ("old_fence", 2),
                                ("campfire_spruce", 1), ("woodpile", 1), ("lantern_post", 1), ("stone_bench", 1)],
          "temperate", relief=1, biomes="plains, meadow, lowland woods, cherry grove, heather moor, wisteria vale"),
    Group("traces_north", 6, [("campfire_spruce", 3), ("woodpile", 3), ("signpost", 1), ("lantern_post", 1),
                              ("old_fence", 1)], "north", SNOWY_GROUND, relief=1,
          biomes="taigas, snowy plains, grove, windswept hills, redwood giants, tundra, verdant peaks"),
    Group("traces_warm", 6, [("campfire_acacia", 3), ("hay_spill", 1), ("wheel_post", 1)], "warm", DRY_GROUND,
          relief=1, biomes="savannas, amber steppe"),
    Group("traces_wet", 8, [("lantern_post", 2), ("signpost", 1), ("campfire_spruce", 1)], "wet", relief=1,
          biomes="swamp, willow bayou, jungles, jade karst, cloud forest"),
    # ---- the rare find
    Group("lost_chest", 48, [("lost_chest", 1)], None, NATURAL, relief=2, biomes="almost all land biomes"),
]


def configured(g):
    obj = {"type": "expanse:template", "max_relief": g.relief, "ground": g.ground,
           "templates": [{"data": f"expanse:{SUB}/{t}", "weight": w} for t, w in g.items]}
    if g.procs:
        obj["processors"] = f"expanse:{SUB}/{g.procs}"
    return obj


def placed(g):
    return {"feature": f"expanse:{SUB}/{g.name}", "placement": [
        {"type": "minecraft:rarity_filter", "chance": g.rarity},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
        {"type": "minecraft:biome"},
        {"type": "expanse:clear_of_structures", "margin": MARGIN}]}


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
    """Remove what an earlier run wrote (only our own micro/ folders)."""
    for d, ext in OUT_DIRS.items():
        folder = os.path.join(DATA, d, SUB)
        for f in sorted(glob.glob(os.path.join(folder, "*" + ext))):
            os.remove(f)


def java_groups():
    """Placed-feature names LittleThings.java adds, in order."""
    with open(JAVA_GROUPS) as f:
        return re.findall(r'\badd\("([a-z0-9_]+)"', f.read())


# ============================================================== validation
def validate_all(builds):
    """The structure validator's own template / processor-list / loot checks, plus the feature wiring."""
    import validate as V
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
                err(f"micro/{g.name}: no template {t}")
            elif not isinstance(w, int) or w < 1:
                err(f"micro/{g.name}: weight {w} for {t}")
            else:
                sx, _, sz = builds[t].size
                if max(sx, sz) > MAX_FOOTPRINT:
                    err(f"micro/{t}: {sx}x{sz} is wider than the feature allows ({MAX_FOOTPRINT})")
                if max(sx, sz) // 2 + 2 > MARGIN:
                    err(f"micro/{t}: {sx}x{sz} reaches past the clear_of_structures margin {MARGIN}")
        if g.procs and g.procs not in PROCESSORS:
            err(f"micro/{g.name}: processor list {g.procs} not written")
        if not 1 <= g.rarity <= 256 or not 0 <= g.relief <= 8:
            err(f"micro/{g.name}: rarity {g.rarity} / relief {g.relief}")
        check_predicate(ctx, f"micro/{g.name} ground", g.ground)
    for t in sorted(set(builds) - used):
        err(f"micro/{t}: template not used by any group")
    names = java_groups()
    if len(names) != len(set(names)):
        err("LittleThings.java adds a placed feature twice")
    if set(names) != {g.name for g in GROUPS}:
        err(f"LittleThings.java adds {sorted(set(names) - {g.name for g in GROUPS})} that are not written, "
            f"and not {sorted({g.name for g in GROUPS} - set(names))}")
    for b in builds.values():                                  # a little thing must sit on the ground
        if not any(p[1] == 0 for p in b.cells):
            err(f"micro/{b.name}: nothing in layer 0, so nothing is bedded in the ground")
    return ctx.problems, ctx.notes


def check_predicate(ctx, where, p):
    t = p.get("type")
    if t == "minecraft:any_of":
        for q in p["predicates"]:
            check_predicate(ctx, where, q)
    elif t == "minecraft:matching_block_tag":
        ns, name = p["tag"].split(":")
        path = os.path.join(os.environ.get("MC_RES", "/root/mc/res"), "data", ns, "tags", "block", name + ".json")
        if ns != "minecraft" or (os.path.isdir(os.path.dirname(path)) and not os.path.exists(path)):
            ctx.err(f"{where}: unknown block tag {p['tag']}")
    elif t == "minecraft:matching_blocks":
        for bid in p["blocks"]:
            ns, name = bid.split(":")
            ok = (bid in __import__("blocks").MOD_ALLOWED) if ns == "expanse" else (
                ctx.vanilla_blocks is None or name in ctx.vanilla_blocks)
            if not ok:
                ctx.err(f"{where}: unknown block {bid}")
    else:
        ctx.err(f"{where}: predicate type {t}")


# ============================================================== preview
GROUND_FOR = {"sandstone_rocks": "sand", "opal_rocks": "expanse:opal_sand", "bones": "sand", "driftwood": "sand",
              "cypress_knees": "mud", "stump_acacia": "grass_block", "log_baobab": "coarse_dirt",
              "prismite_rocks": "calcite"}


def preview_sheet(builds, out_dir):
    """Every template set into a little square of its usual ground (so 'sitting in the ground' shows)."""
    import preview as P
    panels = []
    for name in sorted(builds):
        b = builds[name]
        sx, sy, sz = b.size
        ground = B(GROUND_FOR.get(name, "grass_block"))
        cells = {}
        for x in range(-1, sx + 1):
            for z in range(-1, sz + 1):
                cells[(x, -1, z)] = B("dirt")
                cells[(x, 0, z)] = ground
        for p, (st, _) in b.cells.items():
            cells[p] = st
        cells = {p: st for p, st in cells.items() if st.short not in ("air",)}
        panels.append(P.render(cells, 0, 18, title=f"{name} {sx}x{sy}x{sz}"))
        panels.append(P.render(cells, 2, 18, title="NW"))
    P.stack([P.grid(panels, 4)], os.path.join(out_dir, "micro.png"))
    return os.path.join(out_dir, "micro.png")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", metavar="DIR", help="render a contact sheet of every template into DIR")
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
        written.append(write_json(f"loot_table/chests/{lname}.json", obj))
    for g in GROUPS:
        written.append(write_json(f"worldgen/feature/{SUB}/{g.name}.json", configured(g)))
        written.append(write_json(f"worldgen/placed_feature/{SUB}/{g.name}.json", placed(g)))
    print(f"micro: {len(builds)} templates, {len(PROCESSORS)} processor lists, {len(GROUPS)} placed features; "
          f"wrote {len(written)} files under {os.path.relpath(DATA, ROOT)}")
    for g in GROUPS:
        print(f"  {g.name:15s} 1 in {g.rarity:2d} chunks  {', '.join(t for t, _ in g.items)}")
    if args.preview:
        print("preview:", preview_sheet(builds, args.preview))
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
