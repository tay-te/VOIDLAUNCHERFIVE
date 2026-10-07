#!/usr/bin/env python3
"""The little things: small hand-made templates scattered over the land by the expanse:template feature
(world/feature/SurfaceTemplateFeature.java), one set per region (world/feature/LittleThings.java).

    python3 tools/structures/gen_micro.py                  # write + validate
    python3 tools/structures/gen_micro.py --preview DIR    # also render a contact sheet per template
    python3 tools/structures/gen_micro.py --preview DIR --only 'ruin_*,stump_*' --tile 22   # just some

Three tiers, each region with its own sets: natural things (every chunk or two), traces of people (every
four or five chunks) and ruins (every ten to fourteen, up to 14 blocks across, kept further from structures
by a per-group clear_of_structures margin), and a rare lost chest nearly everywhere.

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

from blocks import AIR, B, MICRO_WOODS, MOD_WOODS  # noqa: E402
import builder  # noqa: E402
from builder import DIRS, Build, fit_height, floating_report, slab, stairs  # noqa: E402
import loot as L  # noqa: E402
import nbt  # noqa: E402
from processors import (FOREST, HEATHER, KARST, LUMEN, STONE_WEATHER, aged_wood, axes, flowers, plist, rot,  # noqa: E402
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
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}


def vec(d):
    return DIRS[d][0], DIRS[d][2]


def wn(wood, kind):
    """Id of a wood-set block: wn("oak", "log") -> oak_log, wn("redwood", "stripped_log") -> expanse:stripped_redwood_log."""
    ns = "expanse:" if wood in MOD_WOODS or wood in MICRO_WOODS else ""
    if kind.startswith("stripped_"):
        return f"{ns}stripped_{wood}_{kind[len('stripped_'):]}"
    return f"{ns}{wood}_{kind}"


SOILS = {"grass_block", "podzol", "coarse_dirt", "dirt", "rooted_dirt", "moss_block", "mud", "sand", "red_sand",
         "opal_sand", "mycelium", "red_earth", "peat", "sphagnum_moss", "coral_sand", "volcanic_ash", "pale_moss_block",
         "snow_block"}
MUSHROOM_SOILS = {"podzol", "mycelium"}           # BlockTags.MUSHROOM_GROW_BLOCK: they live there in daylight
# The mod's plants and a few newer vanilla ones are missing from builder's shape table, which would count them
# as full blocks (placed in the first pass, drawn as cubes in the previews). They are crosses and carpets.
builder._NONFULL.update({"lavender", "desert_marigold", "desert_pea", "moth_orchid", "spinifex", "reindeer_lichen",
                         "cotton_grass", "cranberry_bush", "bluebells", "fireweed", "ocotillo", "open_eyeblossom",
                         "closed_eyeblossom", "pitcher_plant", "resin_clump", "turtle_egg", "pale_hanging_moss",
                         "dead_tube_coral_fan", "dead_brain_coral_fan", "dead_bubble_coral_fan", "dead_fire_coral_fan",
                         "dead_horn_coral_fan", "dead_tube_coral", "dead_brain_coral", "dead_bubble_coral",
                         "dead_fire_coral", "dead_horn_coral"})


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


# ============================================================== the second wave: helpers
# Every region now has things in its own materials. The helpers below keep the designs short; the
# conventions are the same as above (layer 0 bedded, plants on their soil, short_grass a flower marker).

def mix_of(rng, m):
    """A state from a name, a state, a list of names or a [(name, weight)] mix."""
    if isinstance(m, str):
        return B(m)
    if isinstance(m, list) and m and isinstance(m[0], tuple):
        return stones(rng, m)
    if isinstance(m, (list, tuple)):
        return mix_of(rng, rng.choice(list(m)))
    return m


def put(b, x, y, z, st, nbt_=None):
    """Set a cell if it is inside the template and still empty."""
    if b.inb(x, y, z) and (x, y, z) not in b.cells:
        b.set(x, y, z, B(st) if isinstance(st, str) else st, nbt_)
        return True
    return False


def patch(b, rng, cells, mix, prob=1.0, y=0):
    """Ground-course blocks (soil, gravel, ash, salt) over the cells still empty, each with probability prob."""
    for (x, z) in cells:
        if rng.random() < prob:
            put(b, x, y, z, mix_of(rng, mix))


def strew(b, rng, cells, plants, soil, prob):
    """Plants, each on its soil, over the cells, each with probability prob."""
    for (x, z) in cells:
        if rng.random() < prob:
            plant(b, x, z, rng.choice(list(plants)), rng.choice(list(soil)) if isinstance(soil, (list, tuple)) else soil)


def disc(cx, cz, r, sx, sz, r0=-1.0):
    """Columns within r (and beyond r0) of (cx, cz) inside an sx x sz footprint."""
    return [(x, z) for x in range(sx) for z in range(sz) if r0 < math.hypot(x - cx, z - cz) <= r]


def cols(b):
    return [(x, z) for x in range(b.size[0]) for z in range(b.size[2])]


def free(b, cells, y=1):
    """The cells with nothing at y and something to stand on at y-1 (or bare ground there)."""
    return [(x, z) for (x, z) in cells if b.get(x, y, z) is None and (b.get(x, y - 1, z) is None or
                                                                      b.get(x, y - 1, z).short in SOILS)]


def litter_on(b, rng, cells, kind, n, y=1):
    """Leaf litter or petals on soil cells (never on a log or a stone)."""
    amounts = {"leaf_litter": "segment_amount", "pink_petals": "flower_amount", "wildflowers": "flower_amount",
               "expanse:bluebells": "flower_amount"}
    cells = list(cells)
    rng.shuffle(cells)
    for (x, z) in cells[:n]:
        below = b.get(x, y - 1, z)
        if b.get(x, y, z) is None and (below is None or below.short in SOILS):
            if below is None:
                b.set(x, y - 1, z, B("podzol" if kind == "leaf_litter" else "grass_block"))
            b.set(x, y, z, B(kind, facing=rng.choice(H4), **{amounts[kind]: rng.randint(1, 4)}))


def shelf(b, rng, x, y, z, d):
    """A bracket fungus on the side of the block at (x, y, z), sticking out toward d."""
    ox, oz = vec(d)
    return put(b, x + ox, y, z + oz, B("shelf_mushroom", facing=d, age=rng.randint(0, 1)))


def pool(b, cells, rim):
    """Standing water in the ground course, held in by `rim` (a mix) wherever its sides are not yet set. The
    water never touches the template's edge: the land beyond it may lie lower, and the water would run."""
    sx, _, sz = b.size
    for (x, z) in cells:
        assert 0 < x < sx - 1 and 0 < z < sz - 1, f"{b.name}: water at the edge ({x}, {z})"
        b.set(x, 0, z, B("water", level=0))
    rng = random.Random(len(cells) * 7919 + sx)
    for (x, z) in cells:
        for d in H4:
            ox, oz = vec(d)
            put(b, x + ox, 0, z + oz, mix_of(rng, rim))


def snow_on(b, rng, prob=0.8, layers=(1, 1, 2), y_min=0):
    """Snow layers on the exposed tops of full blocks (and the bare ground cells round them)."""
    tops = {}
    for (x, y, z), (st, _) in b.cells.items():
        if builder.is_full(st) and not st.short.endswith(("_leaves", "ice")) and y >= y_min:
            tops[(x, z)] = max(tops.get((x, z), -1), y)
    for (x, z), y in sorted(tops.items()):
        if rng.random() < prob:
            put(b, x, y + 1, z, B("snow", layers=rng.choice(layers)))


# ============================================================== the second wave: woods
def fallen_tree(name, wood, seed, length=6, peel=0.0, brackets=1, moss=0.3, mushrooms=0.12, litter=None,
                soil=("podzol", "coarse_dirt", "grass_block"), extras=("fern", "short_grass"), bark="log",
                crown=True, flare=True):
    """A whole tree down in the woods, not just a log: the root end flared and lifted a little out of the
    ground, the trunk lying along the floor and sinking into it where it has rotted, the crown end broken
    into stubs of branch. Bracket fungus on its flanks, moss and mushrooms on its back, the bark peeling
    (birch, maple) or gone (`bark` "stripped_log": ghost gum, a bleached snag)."""
    rng = random.Random(seed)
    sx = length + 3
    b = Build(name, sx, 4, 5, seed=seed)
    z = 2
    trunk = B(wn(wood, bark), axis="x")
    if flare:                                                 # the root end: bark-covered flare, bedded
        b.set(0, 1, z, B(wn(wood, "wood"), axis="x"))
        for dz in (-1, 1):
            b.set(0, 0, z + dz, B(wn(wood, "wood"), axis="x"))
        b.set(0, 0, z, B("rooted_dirt"))
        b.set(1, 0, z + rng.choice((-1, 1)), B(wn(wood, "log"), axis="x"))
    rot_at = rng.randint(3, length - 1)
    for x in range(1, length + 1):
        if x == rot_at:                                       # rotted through: a hump of soil and moss
            b.set(x, 0, z, B("moss_block" if moss else "coarse_dirt"))
            continue
        st = trunk
        if peel and rng.random() < peel:
            st = B(wn(wood, "stripped_log" if bark == "log" else "log"), axis="x")
        b.set(x, 1, z, st)
        r = rng.random()
        if r < moss:
            b.set(x, 2, z, B("moss_carpet"))
        elif r < moss + mushrooms:
            b.set(x, 2, z, B(rng.choice(["brown_mushroom", "red_mushroom", "brown_mushroom"])))
    if crown:                                                 # the crown end: snapped branches
        e = length + 1
        b.set(e, 1, z, B(wn(wood, "log"), axis="x"))
        b.set(e + 1, 1, z - 1, B(wn(wood, "log"), axis="z"))
        put(b, e + 1, 2, z - 1, B(wn(wood, "fence")))
        b.set(e + 1, 1, z + 1, B(wn(wood, "fence")))
    sides = [x for x in range(1, length + 1) if x != rot_at]
    rng.shuffle(sides)
    for i, x in enumerate(sides[:brackets]):
        shelf(b, rng, x, 1, z, "north" if i % 2 else "south")
    patch(b, rng, [(x, zz) for x in range(sx) for zz in (z - 1, z + 1)], soil, 0.35)
    strew(b, rng, [(x, zz) for x in range(sx) for zz in (0, 1, 3, 4)], extras, soil, 0.18)
    if litter:
        litter_on(b, rng, [(x, zz) for x in range(sx) for zz in (0, 1, 3, 4)], litter, 5)
    return b


def uprooted(name, wood, seed, soil=("podzol", "coarse_dirt", "grass_block"), litter=None, extras=("fern", "short_grass")):
    """A tree the wind took: its root plate torn up and standing on edge, a hollow where it stood, roots
    sticking out of the clay, and the trunk lying out across the floor with its crown long rotted away."""
    rng = random.Random(seed)
    b = Build(name, 11, 5, 5, seed=seed)
    px = 3                                                    # the root plate, on edge across x = 3
    plate = [(z, y) for z in range(5) for y in range(0, 5)
             if math.hypot(z - 2, (y - 1.6) * 1.15) <= 2.25 and not (y == 0 and z in (0, 4))]
    top = {z: max(y for (zz, y) in plate if zz == z) for z in range(5) if any(zz == z for zz, _ in plate)}
    for (z, y) in plate:
        rim = math.hypot(z - 2, (y - 1.6) * 1.15) > 1.7
        if rim and rng.random() < 0.45:
            st = B(wn(wood, "wood"), axis="x")                # roots poking out round the rim
        elif y == top[z] and y >= 2:
            st = B("grass_block")                             # the turf it lifted
        else:
            st = B(rng.choice(["rooted_dirt", "dirt", "coarse_dirt", "rooted_dirt"]))
        b.set(px, y, z, st)
    for z, y in top.items():
        if b.get(px, y, z).short == "grass_block":
            put(b, px, y + 1, z, B(rng.choice(["short_grass", "fern", "short_grass"])))
    for (z, y) in [(1, 2), (3, 1), (2, 3)]:                   # broken roots sticking out over the hollow
        if rng.random() < 0.8:
            b.set(px - 1, y, z, B(wn(wood, "log"), axis="x"))
    if b.get(px - 1, 2, 2) is not None:                      # roots hanging off the one above
        put(b, px - 1, 1, 2, B("hanging_roots"))
    for (x, z) in [(1, 1), (2, 1), (1, 2), (2, 2), (1, 3), (2, 3)]:   # the hollow where the roots were
        b.set(x, 0, z, AIR if (x, z) in ((1, 2), (2, 2), (2, 1)) else B(rng.choice(["coarse_dirt", "rooted_dirt"])))
    patch(b, rng, [(0, 1), (0, 2), (0, 3), (1, 0), (2, 0), (1, 4), (2, 4)], ["coarse_dirt", "dirt", "rooted_dirt"], 0.6)
    b.set(px + 1, 1, 2, B(wn(wood, "wood"), axis="x"))     # the trunk, lying east
    for x in range(px + 2, 10):
        if x == 8:
            b.set(x, 0, 2, B("moss_block"))                   # rotted through
            continue
        b.set(x, 1, 2, B(wn(wood, "log"), axis="x"))
        r = rng.random()
        if r < 0.35:
            b.set(x, 2, 2, B("moss_carpet"))
        elif r < 0.45:
            b.set(x, 2, 2, B("brown_mushroom"))
    b.set(9, 1, 1, B(wn(wood, "fence")))                      # what is left of the crown
    b.set(10, 1, 2, B(wn(wood, "fence")))
    shelf(b, rng, 6, 1, 2, rng.choice(["north", "south"]))
    patch(b, rng, [(x, z) for x in range(4, 11) for z in (1, 3)], soil, 0.35)
    strew(b, rng, [(x, z) for x in range(4, 11) for z in (0, 1, 3, 4)], extras, soil, 0.2)
    if litter:
        litter_on(b, rng, [(x, z) for x in range(4, 11) for z in (0, 1, 3, 4)], litter, 4)
    return b


def snag(name, wood, seed, height=5, peel=(3,), brackets=3, soil=("podzol", "coarse_dirt"), litter=None,
         fallen=True, extras=("fern", "short_grass")):
    """A dead tree still standing: bark peeling, the top snapped off and lying at its foot, bracket fungus
    stepped up the trunk the way polypores climb a dead birch."""
    rng = random.Random(seed)
    b = Build(name, 6, height + 2, 6, seed=seed)
    c = 2
    for y in range(0, height):
        b.set(c, y, c, B(wn(wood, "stripped_log" if y in peel else "log"), axis="y"))
    b.set(c, height, c, B(wn(wood, "stripped_log"), axis="y"))   # the splintered top
    for d in rng.sample(H4, 3):                                 # root flare
        ox, oz = vec(d)
        b.set(c + ox, 0, c + oz, B(wn(wood, "wood"), axis="y"))
    for i, d in enumerate(rng.sample(H4, min(brackets, 4))):
        shelf(b, rng, c, 1 + i, c, d)
    b.set(c + 1, height - 1, c, B(wn(wood, "fence")))           # a dead branch stub
    if fallen:                                                  # the top, fallen beside it
        b.set(c - 1, 1, c + 2, B(wn(wood, "log"), axis="x"))
        b.set(c, 1, c + 2, B(wn(wood, "log"), axis="x"))
        b.set(c + 1, 1, c + 3, B(wn(wood, "fence")))
        b.set(c + 1, 1, c + 2, B(wn(wood, "stripped_log"), axis="x"))
    patch(b, rng, disc(c, c, 2.3, 6, 6), soil, 0.4)
    strew(b, rng, cols(b), extras, soil, 0.15)
    if litter:
        litter_on(b, rng, cols(b), litter, 5)
    return b


def toadstools(seed):
    """Toadstools grown too big: a red cap and a brown one on thick pale stems, little mushrooms round
    their feet in a patch of dark soil."""
    rng = random.Random(seed)
    b = Build("toadstools", 7, 5, 7, seed=seed)
    stem = B("mushroom_stem")
    for y in range(0, 3):                                       # the red one
        b.set(2, y, 2, stem)
    for x in range(1, 4):
        for z in range(1, 4):
            b.set(x, 3, z, B("red_mushroom_block", down=False))
    for (x, z) in [(1, 2), (3, 2), (2, 1), (2, 3)]:
        b.set(x, 2, z, B("red_mushroom_block", up=False))
    b.set(2, 4, 2, B("red_mushroom_block", down=False))
    for y in range(0, 2):                                       # the brown one, squat and flat
        b.set(5, y, 5, stem)
    for x in range(4, 7):
        for z in range(4, 7):
            if (x, z) != (6, 6):
                b.set(x, 2, z, B("brown_mushroom_block", down=False))
    patch(b, rng, disc(3, 3, 3.2, 7, 7), ["podzol", "podzol", "coarse_dirt"], 0.5)
    for (x, z) in [(0, 1), (1, 4), (3, 0), (4, 2), (0, 3), (6, 3), (3, 5), (4, 6)]:
        if rng.random() < 0.7:
            plant(b, x, z, rng.choice(["brown_mushroom", "red_mushroom", "brown_mushroom"]), "podzol")
    return b


def pale_snag(seed):
    """A dead pale oak in the pale garden: a grey trunk with two bare limbs, pale moss hanging off them,
    resin bleeding from a wound in the bark, the moss carpet round its foot."""
    rng = random.Random(seed)
    b = Build("snag_pale_oak", 7, 7, 6, seed=seed)
    c = 3
    for y in range(0, 5):
        b.set(c, y, 2, B("pale_oak_log", axis="y"))
    b.set(c, 5, 2, B("stripped_pale_oak_log", axis="y"))
    for d in ("north", "west", "south"):
        ox, oz = vec(d)
        b.set(c + ox, 0, 2 + oz, B("pale_oak_wood", axis="y"))
    b.set(c + 1, 4, 2, B("pale_oak_log", axis="x"))            # the east limb
    b.set(c + 2, 4, 2, B("pale_oak_log", axis="x"))
    b.set(c + 3, 5, 2, B("pale_oak_fence"))
    b.set(c + 3, 4, 2, B("pale_oak_fence"))
    b.set(c + 2, 3, 2, B("pale_hanging_moss", tip=False))
    b.set(c + 2, 2, 2, B("pale_hanging_moss", tip=True))
    b.set(c + 1, 3, 2, B("pale_hanging_moss", tip=True))
    b.set(c - 1, 3, 2, B("pale_oak_log", axis="x"))            # the west limb, shorter
    b.set(c - 1, 2, 2, B("pale_hanging_moss", tip=True))
    b.set(c, 1, 3, B("resin_clump", north=True))               # resin from a wound
    b.set(c, 2, 3, B("resin_clump", north=True))
    patch(b, rng, disc(c, 2, 2.6, 7, 6), ["pale_moss_block", "pale_moss_block", "coarse_dirt"], 0.6)
    for (x, z) in cols(b):
        under = b.get(x, 0, z)
        if under is not None and under.short == "pale_moss_block" and b.get(x, 1, z) is None and rng.random() < 0.5:
            b.set(x, 1, z, B("pale_moss_carpet"))
    strew(b, rng, cols(b), ["closed_eyeblossom", "short_grass"], "pale_moss_block", 0.12)
    return b


def ant_mound(seed):
    """A wood ants' nest heaped against an old stump: a dome of needles a block or two high, the stump
    showing through on one side, the floor round it trodden bare."""
    rng = random.Random(seed)
    b = Build("ant_mound", 6, 4, 6, seed=seed)
    b.set(1, 0, 2, B("spruce_log", axis="y"))                  # the stump it is built against
    b.set(1, 1, 2, B("spruce_log", axis="y"))
    b.set(1, 2, 2, B("moss_carpet"))
    for (x, z) in disc(2.6, 2.6, 2.3, 6, 6):
        put(b, x, 0, z, B(rng.choice(["coarse_dirt", "rooted_dirt", "podzol"])))
    for (x, z) in disc(2.7, 2.6, 1.6, 6, 6):
        put(b, x, 1, z, B(rng.choice(["podzol", "podzol", "coarse_dirt"])))
    for (x, z) in [(2, 2), (3, 2), (2, 3), (3, 3)]:
        if rng.random() < 0.8:
            put(b, x, 2, z, B("podzol"))
    if b.get(3, 2, 3) is not None and rng.random() < 0.5:
        put(b, 3, 3, 3, B("podzol"))
    strew(b, rng, [(x, z) for (x, z) in cols(b) if math.hypot(x - 2.6, z - 2.6) > 2.3], ["fern", "short_grass"],
          "podzol", 0.25)
    return b


def hollow_redwood(seed):
    """A redwood that fell so long ago it has rotted hollow: a tunnel of red bark you can walk through,
    soil drifted in along its floor, ferns and moss on its back, a hole in its roof where it broke."""
    rng = random.Random(seed)
    b = Build("hollow_redwood", 12, 5, 5, seed=seed)
    z0 = 1                                                     # cross-section z 1..3, y 0..3
    for x in range(1, 11):
        for z in (z0, z0 + 2):
            for y in range(0, 4):
                b.set(x, y, z, B("expanse:redwood_wood", axis="x"))
        b.set(x, 3, z0 + 1, B("expanse:redwood_wood", axis="x"))
        b.set(x, 0, z0 + 1, B(rng.choice(["rooted_dirt", "coarse_dirt", "podzol"])))
        b.set(x, 1, z0 + 1, AIR)
        b.set(x, 2, z0 + 1, AIR)
    for (x, z) in [(1, z0), (1, z0 + 2), (10, z0), (10, z0 + 2)]:   # the cut ends show the wood
        for y in (1, 2):
            b.set(x, y, z, B("expanse:redwood_log", axis="x"))
    b.set(1, 3, z0 + 1, B("expanse:redwood_log", axis="x"))
    b.set(10, 3, z0 + 1, B("expanse:redwood_log", axis="x"))
    for (x, y, z) in [(6, 3, z0 + 1), (7, 3, z0 + 1), (7, 3, z0)]:  # broken through the roof
        b.set(x, y, z, AIR if y == 3 and z == z0 + 1 else B("expanse:stripped_redwood_wood", axis="x"))
    b.set(7, 2, z0 + 2, B("expanse:stripped_redwood_wood", axis="x"))
    plant(b, 7, z0 + 1, "fern", "podzol")                      # a fern under the hole
    for x in range(1, 11):                                     # moss and ferns on its back
        if b.get(x, 3, z0 + 1) is not None and b.get(x, 3, z0 + 1).short != "air":
            r = rng.random()
            for z in (z0, z0 + 1, z0 + 2):
                if b.get(x, 3, z) is not None and b.get(x, 3, z).short != "air" and rng.random() < 0.4:
                    b.set(x, 4, z, B("moss_carpet") if r < 0.7 else B("fern"))
    for (x, z) in [(0, z0), (0, z0 + 2), (11, z0 + 1), (11, z0)]:
        put(b, x, 0, z, B(rng.choice(["coarse_dirt", "podzol"])))
    shelf(b, rng, 3, 1, z0 + 2, "south")
    shelf(b, rng, 8, 2, z0, "north")
    strew(b, rng, [(x, z) for x in range(12) for z in (0, 4)], ["fern", ("tall", "large_fern"), "fern"], "podzol", 0.3)
    return b


def beaver_cuttings(seed):
    """Where beavers have been at the aspens by a creek: stumps gnawed to a point, one trunk felled and
    stripped, pale chips scattered in the grass."""
    rng = random.Random(seed)
    b = Build("beaver_cuttings", 8, 3, 6, seed=seed)
    log = "expanse:aspen_log"
    for (x, z) in [(1, 1), (5, 0), (2, 4)]:                    # gnawed stumps: a log with a pencil point
        b.set(x, 0, z, B(log, axis="y"))
        b.set(x, 1, z, B(log, axis="y"))
        b.set(x, 2, z, B("expanse:aspen_fence"))
    b.set(3, 1, 2, B("expanse:aspen_fence"))                   # the felled one, its point toward the stump
    for x in range(4, 8):
        b.set(x, 1, 2, B("expanse:stripped_aspen_log" if x in (5, 6) else log, axis="x"))
    for (x, z) in [(2, 1), (1, 2), (4, 1), (3, 3), (6, 1), (2, 3)]:   # chips
        if rng.random() < 0.7 and b.get(x, 1, z) is None:
            b.set(x, 0, z, B("coarse_dirt"))
            b.set(x, 1, z, B("expanse:aspen_button", face="floor", facing=rng.choice(H4)))
    strew(b, rng, cols(b), ["short_grass", ("tall", "expanse:fireweed"), "short_grass"], "grass_block", 0.2)
    return b


def bog_hummock(seed):
    """Hummocks in the muskeg: red sphagnum heaped into tussocks with cotton grass nodding on them,
    cranberries in the wet hollows between, and a pool of black bog water in the middle."""
    rng = random.Random(seed)
    b = Build("bog_hummock", 7, 3, 7, seed=seed)
    pool = [(3, 3), (4, 3), (3, 4)]
    for (x, z) in pool:
        b.set(x, 0, z, B("water", level=0))
    for (x, z) in disc(3.4, 3.4, 1.9, 7, 7):                   # the pool's peaty rim
        put(b, x, 0, z, B(rng.choice(["expanse:peat", "expanse:sphagnum_moss"])))
    for cells in ([(0, 0), (1, 0), (0, 1), (1, 1)], [(5, 5), (6, 5), (5, 6)], [(5, 0), (6, 1)], [(0, 5), (1, 6)]):
        for (x, z) in cells:                                    # tussocks
            b.set(x, 0, z, B("expanse:peat"))
            b.set(x, 1, z, B("expanse:sphagnum_moss"))
            if rng.random() < 0.6:
                b.set(x, 2, z, B("expanse:cotton_grass") if rng.random() < 0.7 else B("expanse:sphagnum_moss_carpet"))
    for (x, z) in cols(b):                                      # cranberries in the hollows
        if b.get(x, 0, z) is None and rng.random() < 0.5:
            b.set(x, 0, z, B("expanse:sphagnum_moss"))
            if rng.random() < 0.45:
                b.set(x, 1, z, B("expanse:cranberry_bush") if rng.random() < 0.6 else B("expanse:cotton_grass"))
    return b


def beech_hollow(seed):
    """An old beech stub gone hollow: a ring of grey bark standing knee to waist high and open to the sky,
    a gap in one side, moss and flowers grown up in the rotted heart."""
    rng = random.Random(seed)
    b = Build("beech_hollow", 6, 4, 6, seed=seed)
    ring = [(x, z) for x in range(1, 5) for z in range(1, 5) if x in (1, 4) or z in (1, 4)]
    heights = {}
    for (x, z) in ring:
        if (x, z) in ((4, 2), (4, 3)):                         # the gap
            heights[(x, z)] = 0
            continue
        corner = x in (1, 4) and z in (1, 4)
        heights[(x, z)] = max(1, rng.choice([1, 2, 2, 3]) - (1 if corner else 0))
    for (x, z), h in heights.items():                         # bark below, the broken end grain on top
        for y in range(0, h + 1):
            b.set(x, y, z, B("expanse:beech_wood" if y < h else "expanse:beech_log", axis="y"))
        if h >= 2 and rng.random() < 0.3:
            put(b, x, h + 1, z, B("moss_carpet"))
    for (x, z), d in (((1, 3), "west"), ((3, 1), "north"), ((4, 4), "east")):   # bracket fungus on the bark
        if heights.get((x, z), 0) >= 1:
            shelf(b, rng, x, 1, z, d)
    for x in (2, 3):                                           # the rotted heart
        for z in (2, 3):
            what = rng.choice(["short_grass", "fern", "short_grass", "brown_mushroom", None])
            b.set(x, 0, z, B("podzol" if what == "brown_mushroom" else rng.choice(["moss_block", "rooted_dirt"])))
            if what:
                b.set(x, 1, z, B(what))
    if heights.get((1, 2), 0) >= 2:
        shelf(b, rng, 1, 2, 2, "west")
    for d, (x, z) in (("north", (2, 0)), ("west", (0, 3)), ("south", (3, 5))):
        b.set(x, 0, z, B("expanse:beech_wood", axis="y"))         # roots running out
    strew(b, rng, cols(b), ["short_grass", "fern"], ["moss_block", "grass_block"], 0.2)
    litter_on(b, rng, cols(b), "leaf_litter", 4)
    return b


def boulder_field(seed):
    """Moss-covered boulders strewn through the wood where the ice left them, flowers between."""
    rng = random.Random(seed)
    b = Build("boulder_field", 9, 4, 8, seed=seed)
    mix = [("mossy_cobblestone", 4), ("stone", 2), ("andesite", 2), ("cobblestone", 1)]
    for (x, z, w, d, h) in [(0, 0, 2, 2, 2), (4, 1, 2, 1, 2), (6, 4, 3, 2, 3), (1, 5, 2, 2, 2), (4, 6, 1, 1, 1)]:
        for dx in range(w):
            for dz in range(d):
                corner = w > 1 and d > 1 and dx in (0, w - 1) and dz in (0, d - 1)
                hh = h - (1 if corner and rng.random() < 0.6 else 0)
                for y in range(0, hh):
                    b.set(x + dx, y, z + dz, stones(rng, mix))
                if rng.random() < 0.7:
                    put(b, x + dx, hh, z + dz, B("moss_carpet"))
    patch(b, rng, cols(b), ["moss_block", "grass_block", "moss_block"], 0.25)
    strew(b, rng, cols(b), ["short_grass", "short_grass", "fern"], ["moss_block", "grass_block"], 0.35)
    return b


def erratic(seed):
    """A glacial erratic: one great rounded boulder the ice dropped on open ground, crusted with lichen,
    a few smaller stones that came with it."""
    b = rocks("erratic", seed, [("stone", 3), ("andesite", 3), ("granite", 1), ("diorite", 1), ("tuff", 1)],
              ["0111000", "1222100", "1233210", "1233210", "0122100", "0011000", "0000010"], moss=0.3,
              soil=["podzol", "coarse_dirt", "grass_block"], plants=["short_grass", "fern", "short_grass"],
              top_slab="andesite_slab")
    return b


# ============================================================== the second wave: open country and stone
MENHIR = [("stone", 4), ("andesite", 3), ("tuff", 1), ("mossy_cobblestone", 1)]


def standing_stones(name, seed, count=5, r=3.4):
    """A ring of standing stones on the open hill: tall stones set upright round a ring, one fallen in
    the grass, a flat stone in the middle; worn, lichened, the turf inside kept short by sheep."""
    rng = random.Random(seed)
    n = int(2 * r + 3)
    b = Build(name, n, 6, n, seed=seed)
    c = (n - 1) / 2
    a0 = rng.random() * math.tau
    fallen = rng.randrange(count)
    for i in range(count):
        a = a0 + i * math.tau / count + rng.uniform(-0.2, 0.2)
        x, z = int(round(c + r * math.cos(a))), int(round(c + r * math.sin(a)))
        tx, tz = (1, 0) if abs(math.sin(a)) > abs(math.cos(a)) else (0, 1)   # broad face toward the centre
        wide = rng.random() < 0.6
        if i == fallen:                                         # lying where it fell, outward
            ox, oz = (1 if math.cos(a) > 0 else -1, 0) if tx == 0 else (0, 1 if math.sin(a) > 0 else -1)
            b.set(x, 0, z, stones(rng, MENHIR))
            for k in range(1, 3):
                if b.inb(x + ox * k, 1, z + oz * k):
                    b.set(x + ox * k, 0 if k == 2 else 1, z + oz * k, stones(rng, MENHIR))
            continue
        h = rng.randint(2, 4)
        for y in range(0, h + 1):
            b.set(x, y, z, stones(rng, MENHIR))
            if wide and y < h and b.inb(x + tx, y, z + tz):
                b.set(x + tx, y, z + tz, stones(rng, MENHIR))
        if wide and b.inb(x + tx, h, z + tz) and rng.random() < 0.5:
            b.set(x + tx, h, z + tz, B("andesite_slab", type="bottom"))
        if rng.random() < 0.4:
            put(b, x, h + 1, z, B("moss_carpet"))
    ci = int(round(c))
    b.set(ci, 0, ci, B("stone"))
    b.set(ci, 1, ci, B("smooth_stone_slab", type="bottom"))
    strew(b, rng, cols(b), ["short_grass"], "grass_block", 0.18)
    return b


def dolmen(seed):
    """A dolmen: upright stones carrying a great capstone, the earth mound that once covered it long
    washed away, a dark hollow beneath."""
    rng = random.Random(seed)
    b = Build("dolmen", 7, 5, 6, seed=seed)
    for (x, z) in [(1, 1), (1, 4), (5, 2)]:                     # three uprights, the portal pair west
        for y in range(0, 3):
            b.set(x, y, z, stones(rng, MENHIR))
    b.set(5, 0, 3, stones(rng, MENHIR))                          # a fourth, broken off at the ground
    b.set(5, 1, 3, B("cobblestone_slab", type="bottom"))
    for x in range(1, 6):                                        # the capstone, sloping down to the east
        for z in range(1, 5):
            if (x, z) in ((5, 1), (5, 4)):
                continue
            if x <= 3:
                b.set(x, 3, z, stones(rng, [("stone", 3), ("andesite", 2)]))
            else:
                b.set(x, 3, z, B("andesite_slab" if rng.random() < 0.6 else "stone_slab", type="bottom"))
    for (x, z) in [(2, 2), (3, 2), (2, 3), (3, 3), (4, 2), (4, 3)]:   # the chamber floor, open at the sides
        b.set(x, 0, z, B(rng.choice(["coarse_dirt", "gravel", "coarse_dirt"])))
        for y in (1, 2):
            put(b, x, y, z, AIR)
    for (x, z) in [(2, 2), (3, 3), (2, 4)]:
        if rng.random() < 0.6:
            put(b, x, 4, z, B("moss_carpet"))
    patch(b, rng, [(0, 1), (0, 4), (6, 2), (6, 3)], ["cobblestone", "mossy_cobblestone"], 0.5)   # packing stones
    strew(b, rng, cols(b), ["short_grass"], "grass_block", 0.25)
    return b


def scree(seed):
    """Scree below an outcrop: two big blocks where it broke away, then a fan of gravel and broken stone."""
    rng = random.Random(seed)
    b = Build("scree", 8, 3, 6, seed=seed)
    for (x, z, h) in [(0, 2, 2), (1, 2, 2), (0, 3, 2), (1, 3, 1), (0, 1, 1)]:
        for y in range(0, h + 1):
            b.set(x, y, z, stones(rng, [("stone", 3), ("andesite", 2), ("tuff", 1)]))
    for (x, z) in cols(b):
        w = 1.0 + x * 0.4
        if x >= 1 and abs(z - 2.5) <= w and rng.random() < 0.85 - x * 0.06:
            put(b, x, 0, z, stones(rng, [("gravel", 5), ("cobblestone", 1), ("andesite", 1), ("stone", 1)]))
            if rng.random() < 0.25 and b.get(x, 0, z).short != "gravel":
                put(b, x, 1, z, stones(rng, [("cobblestone", 1), ("andesite", 1), ("stone", 1)]))
            elif rng.random() < 0.12:
                put(b, x, 1, z, B(rng.choice(["cobblestone_slab", "andesite_slab"]), type="bottom"))
    return b


def ice_boulder(seed):
    """A block of old glacier ice stranded in the snow: blue at the heart, drifted round with snow."""
    rng = random.Random(seed)
    b = rocks("ice_boulder", seed, [("packed_ice", 5), ("blue_ice", 1)], ["01100", "12210", "12321", "01210", "00100"],
              moss=0.0)
    for (x, z) in cols(b):
        if b.get(x, 0, z) is None and rng.random() < 0.5:
            b.set(x, 0, z, B("snow_block"))
    snow_on(b, rng, 0.7, (1, 2, 3))
    return b


def avalanche_debris(seed):
    """What an avalanche left at the foot of the slope: snapped spruce trunks jumbled with snow and stone."""
    rng = random.Random(seed)
    b = Build("avalanche_debris", 8, 4, 7, seed=seed)
    for x in range(0, 6):                                       # a trunk lying across
        b.set(x, 1, 2, B("spruce_log", axis="x"))
    b.set(6, 1, 2, B("stripped_spruce_log", axis="x"))
    for z in range(1, 6):                                       # another across it
        b.set(3, 2 if z == 2 else 1, z, B("spruce_log", axis="z"))
    b.set(3, 1, 2, B("spruce_log", axis="x"))
    b.set(5, 2, 2, B("spruce_fence"))
    b.set(1, 1, 4, B("spruce_log", axis="y"))                   # a snapped stem, end-up
    for (x, z) in [(0, 3), (1, 3), (2, 4), (4, 4), (5, 3), (2, 1), (4, 1), (6, 3), (5, 5), (0, 1)]:
        b.set(x, 0, z, B("snow_block"))
        if rng.random() < 0.5:
            put(b, x, 1, z, B("snow_block") if rng.random() < 0.5 else stones(rng, [("stone", 1), ("cobblestone", 1)]))
    b.set(6, 1, 5, B("spruce_leaves", persistent=True))
    b.set(7, 1, 4, B("spruce_leaves", persistent=True))
    snow_on(b, rng, 0.85, (1, 2, 2, 3))
    return b


def prismite_geode(seed):
    """A geode split open by frost: a rind of smooth basalt and calcite, the hollow inside it lined with
    prismite and bristling with crystals."""
    rng = random.Random(seed)
    b = Build("prismite_geode", 6, 4, 6, seed=seed)
    for (x, z) in disc(2.5, 2.5, 2.6, 6, 6):
        d = math.hypot(x - 2.5, z - 2.5)
        if d > 2.0 and z > 3 and rng.random() < 0.5:
            continue                                            # the front of the rind is broken away
        b.set(x, 0, z, B("smooth_basalt" if d > 2.0 else "calcite" if d > 1.2 else "expanse:prismite_block"))
    for (x, z) in disc(2.5, 2.5, 2.6, 6, 6):                   # the back half of the shell still stands
        d = math.hypot(x - 2.5, z - 2.5)
        if z <= 2 and d > 1.2:
            h = 2 if d > 2.0 and 1 <= x <= 4 else 1
            for y in range(1, h + 1):
                b.set(x, y, z, B("smooth_basalt" if d > 2.0 else "calcite"))
    for x in (2, 3):                                            # its inside face lined with crystal
        if b.get(x, 1, 1) is not None:
            b.set(x, 1, 1, B("expanse:prismite_block"))
            put(b, x, 2, 1, B("expanse:prismite_cluster", facing="up"))
    for (x, z) in [(2, 3), (3, 3), (2, 2), (3, 2), (1, 3), (4, 3)]:
        if b.get(x, 1, z) is None and rng.random() < 0.8:
            b.set(x, 1, z, B("expanse:prismite_cluster", facing="up"))
    for (x, z) in [(2, 5), (4, 5), (0, 4)]:                     # the broken front half, in pieces
        put(b, x, 0, z, B(rng.choice(["calcite", "smooth_basalt"])))
    return b


def ice_shards(seed):
    """Ice spikes in miniature: shards of packed ice pushed up through the snow, one snapped off and lying."""
    rng = random.Random(seed)
    b = Build("ice_shards", 6, 6, 6, seed=seed)
    for (x, z, h) in [(2, 2, 4), (3, 2, 3), (2, 3, 2), (4, 4, 2), (1, 4, 1), (4, 1, 1)]:
        for y in range(0, h + 1):
            b.set(x, y, z, B("packed_ice"))
    b.set(2, 5, 2, B("packed_ice"))
    for x in (0, 1):                                            # the snapped one
        b.set(x, 1, 1, B("packed_ice"))
    b.set(0, 0, 1, B("snow_block"))
    patch(b, rng, cols(b), ["snow_block"], 0.4)
    snow_on(b, rng, 0.5, (1, 2))
    return b


def rune_stone(seed):
    """A runestone at the head of the fjord: a tall grey slab carved with a band of knotwork, lichen on its
    shoulders, set in a little cairn of packing stones."""
    rng = random.Random(seed)
    b = Build("rune_stone", 4, 6, 3, seed=seed)
    for y in range(0, 5):
        for x in (1, 2):
            if y == 4 and x == 2:
                b.set(x, y, 1, B("stone_slab", type="bottom"))
                continue
            carved = 1 <= y <= 3 and (x + y) % 2 == 0
            b.set(x, y, 1, B("chiseled_stone_bricks" if carved else rng.choice(["stone", "stone", "andesite"])))
    b.set(1, 5, 1, B("stone_slab", type="bottom"))
    for (x, z) in [(0, 1), (3, 1), (1, 0), (2, 2), (0, 0), (3, 2)]:   # packing stones
        put(b, x, 0, z, stones(rng, [("cobblestone", 2), ("mossy_cobblestone", 2), ("andesite", 1)]))
    put(b, 0, 1, 1, B("mossy_cobblestone_slab", type="bottom"))
    strew(b, rng, cols(b), ["short_grass", "fern"], "grass_block", 0.4)
    return b


def pinnacles(name, seed, mix, layout, cap="moss_carpet", soil=("moss_block", "grass_block"),
              plants=("fern", "short_grass")):
    """Weathered towers of rock left standing out of the ground, capped with moss and ferns. `layout` is
    rows of digits as in rocks(): the height of the stone in each column."""
    rng = random.Random(seed)
    sx, sz = len(layout[0]), len(layout)
    hmax = max(int(c) for row in layout for c in row)
    b = Build(name, sx, hmax + 2, sz, seed=seed)
    for z, row in enumerate(layout):
        for x, ch in enumerate(row):
            h = int(ch)
            for y in range(0, h):
                b.set(x, y, z, stones(rng, mix))
            if h >= 2 and cap and rng.random() < 0.75:
                b.set(x, h, z, B(cap) if isinstance(cap, str) else cap)
    strew(b, rng, cols(b), plants, soil, 0.3)
    return b


def rock_pool(name, seed, mix, soil=("moss_block", "grass_block"), plants=("fern", "short_grass"), tide=False):
    """A hollow in the bare rock holding water: rain water on the tepui top, sea water left by the tide on
    the shore (sea pickles glowing in it, a tuft of seagrass). Ringed with stone, a few taller rocks."""
    rng = random.Random(seed)
    b = Build(name, 6, 3, 6, seed=seed)
    water = [(2, 2), (3, 2), (2, 3), (3, 3), (3, 4)]
    for (x, z) in disc(2.6, 3.0, 2.7, 6, 6):
        if (x, z) not in water:
            b.set(x, 0, z, stones(rng, mix))
    for (x, z) in water:
        b.set(x, 0, z, B("water", level=0))
    if tide:
        b.set(2, 0, 3, B("sea_pickle", pickles=rng.randint(2, 4), waterlogged=True))
        b.set(3, 0, 2, B("seagrass"))
    for (x, z) in [(1, 1), (4, 2), (1, 4), (4, 5)]:
        if rng.random() < 0.7:
            put(b, x, 1, z, stones(rng, mix))
    if not tide:
        strew(b, rng, cols(b), plants, soil, 0.4)
    else:
        patch(b, rng, cols(b), ["gravel", "sand"], 0.3)
    return b


# ============================================================== the second wave: the warm and dry lands
def termite_mound(name, seed, mix, height=4, spires=((2, 2, 0),), soil=("coarse_dirt",), plants=("short_dry_grass",)):
    """A termite mound: a spire of baked earth taller than a man, buttressed at its foot, the ground round
    it bare. `spires` are (x, z, extra height) for the pinnacles of a cathedral mound."""
    rng = random.Random(seed)
    b = Build(name, 6, height + 4, 6, seed=seed)
    for (x, z) in disc(2.5, 2.5, 1.9, 6, 6):                   # the broad foot
        for y in range(0, 2 if math.hypot(x - 2.5, z - 2.5) < 1.6 else 1):
            b.set(x, y, z, mix_of(rng, mix))
    for (sx_, sz_, extra) in spires:
        top = height + extra
        for y in range(1, top + 1):
            b.set(sx_, y, sz_, mix_of(rng, mix))
            if y <= top - 2:                                     # thickened low down
                for d in rng.sample(H4, 2 if y < top - 2 else 1):
                    ox, oz = vec(d)
                    put(b, sx_ + ox, y, sz_ + oz, mix_of(rng, mix))
    patch(b, rng, disc(2.5, 2.5, 2.9, 6, 6), soil, 0.6)
    strew(b, rng, [(x, z) for (x, z) in cols(b) if math.hypot(x - 2.5, z - 2.5) > 2.2], plants, soil, 0.3)
    return b


def tor(name, seed, layers, mix, cap, soil=("coarse_dirt",), plants=("short_dry_grass",)):
    """A tor: rounded boulders stacked by weathering, one balanced on top. `layers` lists, bottom up, the
    rows of each course ('#' rock, 's' a slab of `cap` rounding a shoulder, '.' nothing)."""
    rng = random.Random(seed)
    sz, sx = len(layers[0]), len(layers[0][0])
    b = Build(name, sx, len(layers) + 1, sz, seed=seed)
    for y, rows in enumerate(layers):
        for z, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "#":
                    b.set(x, y, z, mix_of(rng, mix))
                elif ch == "s":
                    b.set(x, y, z, B(cap, type="bottom"))
    patch(b, rng, cols(b), soil, 0.35)
    strew(b, rng, cols(b), plants, soil, 0.25)
    return b


def hoodoo(seed):
    """Hoodoos: thin spires of banded clay left standing by the wind, each with a hard cap of sandstone
    balanced on top, wider than the neck under it."""
    rng = random.Random(seed)
    b = Build("hoodoo", 7, 9, 6, seed=seed)
    bands = ["terracotta", "orange_terracotta", "white_terracotta", "terracotta", "red_terracotta",
             "yellow_terracotta", "orange_terracotta", "terracotta"]
    for (cx, cz, h) in [(2, 2, 6), (5, 4, 4)]:
        for (x, z) in disc(cx, cz, 1.5, 7, 6):                   # the skirt of fallen clay
            put(b, x, 0, z, B(bands[0] if rng.random() < 0.5 else "red_sand"))
        for (x, z) in disc(cx, cz, 1.0, 7, 6):
            if (x, z) != (cx, cz):
                b.set(x, 1, z, B(bands[1]))
        for y in range(0, h):
            b.set(cx, y, cz, B(bands[y % len(bands)]))
        b.set(cx, h, cz, B("cut_red_sandstone"))              # the cap rock, wider than the neck
        for (x, z) in [(cx + 1, cz), (cx, cz - 1), (cx + 1, cz - 1)]:
            if b.inb(x, h, z):
                b.set(x, h, z, B("smooth_red_sandstone") if rng.random() < 0.4 else
                      B("red_sandstone_slab", type="bottom"))
    strew(b, rng, cols(b), ["dead_bush", "short_dry_grass"], "red_sand", 0.15)
    return b


def rockfall(name, seed, mix, n=12, soil=("red_sand",), plants=("dead_bush",)):
    """A rockfall from the canyon wall: banded blocks tumbled across the floor, some on top of others."""
    rng = random.Random(seed)
    b = Build(name, 7, 3, 6, seed=seed)
    cells = disc(3, 2.5, 3.2, 7, 6)
    rng.shuffle(cells)
    for (x, z) in cells[:n]:
        b.set(x, 0, z, mix_of(rng, mix))
        if rng.random() < 0.4:
            b.set(x, 1, z, mix_of(rng, mix))
    patch(b, rng, cells, soil, 0.3)
    strew(b, rng, cols(b), plants, soil, 0.1)
    return b


def dry_wash(seed):
    """A dry wash: a strip of gravel and water-rounded cobbles where the flash floods run, desert
    marigolds and dry grass along its banks."""
    rng = random.Random(seed)
    b = Build("dry_wash", 9, 2, 6, seed=seed)
    for x in range(9):
        zc = 2.5 + math.sin(x * 0.7) * 0.9
        for z in range(6):
            if abs(z - zc) < 1.3:
                b.set(x, 0, z, stones(rng, [("gravel", 5), ("coarse_dirt", 1), ("cobblestone", 1), ("granite", 1)]))
                if rng.random() < 0.1:
                    put(b, x, 1, z, B(rng.choice(["cobblestone", "granite"])))
            elif abs(z - zc) < 2.4 and rng.random() < 0.4:
                plant(b, x, z, rng.choice(["expanse:desert_marigold", "short_dry_grass", "short_dry_grass",
                                           "dead_bush"]), "coarse_dirt")
    return b


def salt_ridges(seed):
    """Salt heaved into polygons as the lake dried: low white ridges a block high meeting in threes,
    the crust between them flat and cracked."""
    rng = random.Random(seed)
    b = Build("salt_ridges", 10, 3, 9, seed=seed)
    pts = [(1, 2), (4, 1), (8, 2), (5, 5), (2, 7), (8, 7)]
    edges = [(0, 1), (1, 2), (1, 3), (0, 4), (3, 4), (3, 5), (2, 5)]
    for i, j in edges:
        (x0, z0), (x1, z1) = pts[i], pts[j]
        n = max(abs(x1 - x0), abs(z1 - z0))
        for k in range(n + 1):
            x = round(x0 + (x1 - x0) * k / n)
            z = round(z0 + (z1 - z0) * k / n)
            if rng.random() < 0.85:
                b.set(x, 1, z, B("expanse:salt_block") if rng.random() < 0.85 else B("calcite"))
                b.set(x, 0, z, B("expanse:salt_block"))
    for (x, z) in pts:
        if rng.random() < 0.5:
            put(b, x, 2, z, B("expanse:salt_block"))
    return b


def brine_pool(seed):
    """A brine pool drying out on the flats: the last of the water lying in a hollow, a rim of salt crust
    heaved up round it, crystals of calcite in the crust, a bleached bone at its edge."""
    rng = random.Random(seed)
    b = Build("brine_pool", 7, 3, 7, seed=seed)
    for (x, z) in disc(3, 3, 3.1, 7, 7):
        d = math.hypot(x - 3, z - 3)
        if d > 1.9:
            b.set(x, 0, z, B("expanse:salt_block"))
            if rng.random() < 0.7:
                b.set(x, 1, z, B("expanse:salt_block") if rng.random() < 0.8 else B("calcite"))
        else:
            b.set(x, 0, z, B("water", level=0))
    put(b, 0, 1, 3, B("bone_block", axis="z"))
    put(b, 0, 0, 3, B("expanse:salt_block"))
    return b


# ============================================================== the second wave: fire and ice
def lava_bomb(seed):
    """A lava bomb: a round boulder of basalt flung out by the old volcano, split open with a glowing seam
    in it, a fumarole steaming in the ash beside."""
    rng = random.Random(seed)
    b = rocks("lava_bomb", seed, [("basalt", 3), ("blackstone", 3), ("smooth_basalt", 1)],
              ["01100", "12210", "12310", "01100", "00000"], moss=0.0)
    for (x, y, z), (st, n) in list(b.cells.items()):
        if st.short == "basalt":
            b.cells[(x, y, z)] = (B("basalt", axis=rng.choice(["x", "y", "z"])), n)
    b.set(2, 1, 2, B("magma_block"))                            # the seam
    b.set(4, 0, 3, B("expanse:fumarole"))
    patch(b, rng, cols(b), ["expanse:volcanic_ash", "expanse:volcanic_ash", "tuff"], 0.5)
    return b


def obsidian_shards(seed):
    """Obsidian where a flow cooled fast: black glassy shards standing in the ash, a crying one among them."""
    rng = random.Random(seed)
    b = Build("obsidian_shards", 7, 3, 6, seed=seed)
    for (x, z, h) in [(1, 1, 2), (2, 1, 1), (4, 2, 1), (5, 4, 2), (2, 4, 1), (3, 3, 0), (6, 1, 0)]:
        for y in range(0, h + 1):
            b.set(x, y, z, B("obsidian"))
    b.set(4, 1, 2, B("crying_obsidian"))
    patch(b, rng, disc(3, 2.5, 3.2, 7, 6), ["expanse:volcanic_ash", "expanse:volcanic_ash", "blackstone"], 0.6)
    return b


def fumarole_crust(seed):
    """A fumarole: a vent in a crust of yellowed tuff and ash, a lip of stained rock round it, the ground
    warm and bare."""
    rng = random.Random(seed)
    b = Build("fumarole_crust", 6, 3, 6, seed=seed)
    b.set(2, 0, 3, B("expanse:fumarole"))
    for (x, z) in disc(2.5, 3, 2.6, 6, 6):
        d = math.hypot(x - 2, z - 3)
        if d < 1.5:
            put(b, x, 0, z, B(rng.choice(["yellow_terracotta", "tuff", "yellow_terracotta"])))
            if d > 0.5 and rng.random() < 0.5:
                put(b, x, 1, z, B(rng.choice(["tuff", "yellow_terracotta"])))
        elif rng.random() < 0.6:
            put(b, x, 0, z, B(rng.choice(["expanse:volcanic_ash", "tuff", "expanse:volcanic_ash"])))
    return b


def charred_snag(seed):
    """A tree killed by the ash fall: a black, barkless snag standing in grey ash, its branches burnt to
    stubs, a fallen limb half buried."""
    rng = random.Random(seed)
    b = Build("charred_snag", 5, 6, 5, seed=seed)
    for y in range(0, 4):
        b.set(2, y, 2, B("dark_oak_wood", axis="y") if y < 3 else B("stripped_dark_oak_log", axis="y"))
    b.set(2, 4, 2, B("coal_block"))
    b.set(3, 3, 2, B("dark_oak_fence"))
    b.set(1, 2, 2, B("dark_oak_fence"))
    b.set(0, 1, 3, B("dark_oak_log", axis="x"))
    b.set(1, 1, 3, B("dark_oak_log", axis="x"))
    patch(b, rng, cols(b), ["expanse:volcanic_ash", "expanse:volcanic_ash", "coarse_dirt"], 0.6)
    return b


# ============================================================== the second wave: wet country
def swamp_snag(name, wood, seed, moss=None):
    """A dead tree standing in the shallows: a grey snag in a puddle ringed with mud, vines down one side,
    mushrooms at its foot, a lily pad on the water (and moss hanging off it in the bayou)."""
    rng = random.Random(seed)
    b = Build(name, 6, 7, 6, seed=seed)
    c = 2
    for y in range(0, 5):
        b.set(c, y, c, B(wn(wood, "stripped_log" if y >= 3 else "log"), axis="y"))
    b.set(c + 1, 4, c, B(wn(wood, "log"), axis="x"))
    b.set(c - 1, 3, c, B(wn(wood, "fence")))
    for y in (1, 2, 3):
        put(b, c, y, c + 1, B("vine", north=True))
    water = [(3, 3), (4, 3), (3, 4), (4, 2)]
    for (x, z) in disc(3, 3, 2.6, 6, 6):
        if (x, z) not in water and (x, z) != (c, c):
            put(b, x, 0, z, B(rng.choice(["mud", "mud", "moss_block", "podzol"])))
    for (x, z) in water:
        b.set(x, 0, z, B("water", level=0))
    put(b, 4, 1, 3, B("lily_pad"))
    plant(b, 1, 2, "brown_mushroom", "podzol")
    if rng.random() < 0.5:
        plant(b, 1, 1, "red_mushroom", "podzol")
    if moss:                                                   # moss hanging off the limb
        b.set(c + 2, 4, c, B(wn(wood, "log"), axis="x"))
        b.hang_moss(c + 2, 3, c, 2, block=moss)
        b.hang_moss(c + 1, 3, c, 1, block=moss)
    strew(b, rng, cols(b), ["fern", "short_grass"], "mud", 0.15)
    return b


def mangrove_tangle(seed):
    """A knot of mangrove roots arching out of the mud where an old tree died, its broken stump still in
    the middle, muddy roots spreading under the surface."""
    rng = random.Random(seed)
    b = Build("mangrove_tangle", 7, 5, 7, seed=seed)
    c = 3
    for y in range(1, 4):
        b.set(c, y, c, B("mangrove_log", axis="y"))
    b.set(c, 4, c, B("stripped_mangrove_log", axis="y"))
    b.set(c, 0, c, B("muddy_mangrove_roots", axis="y"))
    for d in H4:                                                # arching roots: up from the trunk, down to the mud
        ox, oz = vec(d)
        b.set(c + ox, 2, c + oz, B("mangrove_roots"))
        b.set(c + 2 * ox, 1, c + 2 * oz, B("mangrove_roots"))
        b.set(c + 2 * ox, 0, c + 2 * oz, B("muddy_mangrove_roots", axis="y"))
        if rng.random() < 0.6:
            b.set(c + 3 * ox, 0, c + 3 * oz, B("muddy_mangrove_roots", axis="y"))
    for (x, z) in [(c + 1, c + 1), (c - 1, c - 1)]:
        b.set(x, 1, z, B("mangrove_roots"))
        b.set(x, 0, z, B("muddy_mangrove_roots", axis="y"))
    patch(b, rng, disc(c, c, 3.2, 7, 7), ["mud", "mud", "mud", B("muddy_mangrove_roots", axis="y")], 0.4)
    return b


def buttress_root(seed):
    """The foot of a fallen kapok giant: plank buttress roots taller than a man fanning out from a broken
    stump, leaf mould and ferns in the bays between them."""
    rng = random.Random(seed)
    b = Build("buttress_root", 11, 5, 11, seed=seed)
    w = B("expanse:kapok_wood", axis="y")
    for x in (4, 5, 6):                                         # the stump, 3 x 3, broken unevenly
        for z in (4, 5, 6):
            h = 3 if (x, z) in ((4, 4), (5, 4), (4, 5)) else 2 if (x + z) % 2 else 1
            for y in range(0, h + 1):
                b.set(x, y, z, w if y < h or (x, z) != (5, 5) else B("expanse:stripped_kapok_log", axis="y"))
    b.set(5, 2, 5, B("expanse:kapok_log", axis="y"))
    for d in H4:                                                # four fins, tapering away
        ox, oz = vec(d)
        for k, h in enumerate((4, 3, 2, 1, 0), start=2):
            x, z = 5 + ox * k, 5 + oz * k
            if not b.inb(x, 0, z):
                continue
            for y in range(0, h + 1):
                b.set(x, y, z, w)
    for (x, z) in cols(b):                                      # leaf mould in the bays
        if b.get(x, 0, z) is None and math.hypot(x - 5, z - 5) < 4.5 and rng.random() < 0.5:
            b.set(x, 0, z, B(rng.choice(["podzol", "coarse_dirt", "moss_block"])))
    strew(b, rng, [(x, z) for (x, z) in cols(b) if math.hypot(x - 5, z - 5) < 4.5],
          ["fern", "fern", ("tall", "large_fern"), "short_grass"], "podzol", 0.3)
    for (x, y, z), (st, _) in list(b.cells.items()):            # moss along the fin tops, vines down them
        if st.short == "kapok_wood" and b.get(x, y + 1, z) is None and rng.random() < 0.4:
            b.set(x, y + 1, z, B("moss_carpet"))
        elif st.short == "kapok_wood" and y >= 2 and rng.random() < 0.25:
            for d in H4:
                ox, oz = vec(d)
                if put(b, x + ox, y, z + oz, B("vine", **{OPPOSITE[d]: True})):
                    break
    shelf(b, rng, 5, 2, 6, "south")
    return b


def vine_boulder(seed):
    """A jungle boulder: mossy stone draped with vines, ferns crowding its foot."""
    rng = random.Random(seed)
    b = rocks("vine_boulder", seed, [("mossy_cobblestone", 4), ("stone", 2), ("andesite", 1), ("cobblestone", 1)],
              ["01100", "12210", "13321", "12210", "01100"], moss=0.5, soil=["moss_block", "podzol"],
              plants=["fern", "fern", ("tall", "large_fern")])
    for (x, y, z), (st, _) in list(b.cells.items()):
        if y >= 1 and st.short in ("mossy_cobblestone", "stone", "andesite", "cobblestone"):
            for d in H4:
                ox, oz = vec(d)
                p = (x + ox, y, z + oz)
                if b.inb(*p) and p not in b.cells and rng.random() < 0.35:
                    b.set(*p, B("vine", **{OPPOSITE[d]: True}))
    return b


def karst_pinnacle(seed):
    """A limestone pinnacle: a fluted spire of grey rock with a tuft of azalea on its top, ferns at its foot."""
    b = pinnacles("karst_pinnacle", seed, LIMESTONE, ["0110", "1251", "1431", "0110"], cap=None,
                  soil=("moss_block", "grass_block"), plants=("fern", "short_grass"))
    b.size = (4, 7, 4)
    top = max(y for (x, y, z) in b.cells if (x, z) == (2, 1))
    b.set(2, top, 1, B("moss_block"))
    b.set(2, top + 1, 1, B("azalea"))
    return b


def olive_stump(seed):
    """The stump of an ancient olive, its bole split and twisted and hollow at the heart, a ring of new
    shoots coming up from the root, red earth round it."""
    rng = random.Random(seed)
    b = Build("olive_stump", 5, 4, 5, seed=seed)
    w = B("expanse:olive_wood", axis="y")
    for (x, z, h) in [(1, 1, 2), (2, 1, 1), (1, 2, 3), (3, 2, 2), (2, 3, 1), (3, 3, 2), (3, 1, 0)]:
        for y in range(0, h + 1):
            b.set(x, y, z, w)
    b.set(2, 0, 2, B("expanse:red_earth"))                     # the hollow heart
    b.set(1, 3, 2, B("expanse:olive_log", axis="z"))           # a twisted limb stub
    for (x, z) in [(0, 2), (4, 3), (2, 4)]:                     # suckers
        if rng.random() < 0.8:
            put(b, x, 0, z, B("expanse:red_earth"))
            put(b, x, 1, z, B("expanse:olive_leaves", persistent=True))
    patch(b, rng, cols(b), ["expanse:red_earth", "coarse_dirt"], 0.4)
    strew(b, rng, cols(b), ["short_grass", "short_grass"], "expanse:red_earth", 0.25)
    return b


# ============================================================== the second wave: shores
def whale_bones(seed):
    """A whale's bones on a cold shore: the long jaws standing up out of the shingle as an arch, a row of
    ribs half sunk, vertebrae scattered down the beach."""
    rng = random.Random(seed)
    b = Build("whale_bones", 10, 6, 7, seed=seed)
    for y in range(0, 4):                                       # the jaws, an arch over the beach
        b.set(1, y, 1, B("bone_block", axis="y"))
        b.set(5, y, 1, B("bone_block", axis="y"))
    b.set(2, 4, 1, B("bone_block", axis="x"))
    b.set(4, 4, 1, B("bone_block", axis="x"))
    b.set(3, 5, 1, B("bone_block", axis="x"))
    b.set(3, 4, 1, B("bone_block", axis="x"))
    for x in range(2, 9):                                       # the spine, buried
        if x != 6:
            b.set(x, 0, 4, B("bone_block", axis="x"))
    for x, h in ((3, 2), (5, 2), (7, 1)):                       # ribs
        for z in (3, 5):
            for y in range(0, h + 1):
                b.set(x, y, z, B("bone_block", axis="y"))
    for (x, z) in [(8, 1), (9, 5), (0, 5)]:                     # loose vertebrae
        b.set(x, 0, z, B("bone_block", axis=rng.choice(["x", "z"])))
    patch(b, rng, cols(b), ["gravel", "gravel", "cobblestone"], 0.3)
    return b


def coral_rubble(seed):
    """Coral rubble thrown up by a storm: bleached heads and fans in a drift of coral sand, a few sea
    pickles still alive in a puddle the tide left."""
    rng = random.Random(seed)
    b = Build("coral_rubble", 7, 3, 6, seed=seed)
    heads = ["dead_brain_coral_block", "dead_tube_coral_block", "dead_horn_coral_block", "dead_fire_coral_block",
             "dead_bubble_coral_block"]
    for (x, z, h) in [(1, 1, 1), (2, 1, 0), (4, 2, 1), (5, 4, 0), (1, 4, 0), (3, 4, 1), (6, 1, 0)]:
        for y in range(0, h + 1):
            b.set(x, y, z, B(rng.choice(heads)))
    for (x, z) in [(0, 2), (2, 3), (4, 1), (5, 3), (6, 4), (3, 0)]:
        put(b, x, 0, z, B("expanse:coral_sand"))
        put(b, x, 1, z, B(f"dead_{rng.choice(['tube', 'brain', 'horn', 'fire', 'bubble'])}_coral_fan"))
    b.set(3, 0, 2, B("water", level=0))
    b.set(2, 0, 2, B("sea_pickle", pickles=rng.randint(1, 3), waterlogged=True))
    for (x, z) in [(2, 2), (3, 2)]:
        for d in H4:
            ox, oz = vec(d)
            put(b, x + ox, 0, z + oz, B("expanse:coral_sand"))
    patch(b, rng, cols(b), ["expanse:coral_sand"], 0.35)
    return b


def drift(name, seed, wood, ground):
    """Bleached logs washed up and half buried at the top of the beach, a snapped branch on one."""
    rng = random.Random(seed)
    b = Build(name, 7, 2, 5, seed=seed)
    log = B(wn(wood, "stripped_log"), axis="x")
    b.set(0, 0, 1, log)
    for x in range(1, 5):
        b.set(x, 1, 1, log)
    b.set(5, 0, 1, B(wn(wood, "stripped_wood"), axis="x"))
    b.set(3, 1, 2, B(wn(wood, "fence")))
    for z in (2, 3, 4):                                         # a second piece, slantwise and mostly buried
        b.set(5 - (z - 2), 0 if z != 3 else 1, z, B(wn(wood, "stripped_log"), axis="z"))
    patch(b, rng, cols(b), [ground], 0.3)
    if rng.random() < 0.6:
        plant(b, 6, 0, "dead_bush", "sand")
    return b


def kelp_wrack(seed):
    """The wrack line: kelp the storm threw up, dried black in a long heap along the shingle, a plank and
    a lost float caught in it."""
    rng = random.Random(seed)
    b = Build("kelp_wrack", 9, 2, 4, seed=seed)
    for x in range(9):
        z = 1 + (1 if math.sin(x * 0.8) > 0.3 else 0)
        if rng.random() < 0.85:
            b.set(x, 0, z, B("dried_kelp_block"))
            if rng.random() < 0.35:
                b.set(x, 1, z, B("dried_kelp_block"))
        if rng.random() < 0.4:
            put(b, x, 0, z + rng.choice((-1, 1)), B("dried_kelp_block"))
    put(b, 3, 1, 3, B("spruce_slab", type="bottom"))
    put(b, 3, 0, 3, B("gravel"))
    patch(b, rng, cols(b), ["gravel", "gravel", "sand"], 0.3)
    return b


# ============================================================== the second wave: traces of people
LOST_CACHE = "expanse:chests/micro/lost_cache"
RUIN_CACHE = "expanse:chests/micro/ruin_cache"
AMPHORA = "expanse:chests/micro/amphora"
PROSPECTOR = "expanse:chests/micro/prospector_cache"


def bees(n):
    """A beehive's block entity with n bees at home (as snag_beehive's nest)."""
    return {"bees": nbt.List(nbt.TAG_COMPOUND, [
        {"entity_data": {"id": "minecraft:bee"}, "ticks_in_hive": nbt.Int(0), "min_ticks_in_hive": nbt.Int(600)}
        for _ in range(n)]), "id": "minecraft:beehive"}


def scarecrow_patch(seed):
    """A scarecrow over a vegetable patch gone back to weeds: the straw man on his pole with a pumpkin for a
    head and his arms out, rows of carrots and beets still coming up among the grass, a hurdle fallen in."""
    rng = random.Random(seed)
    b = Build("scarecrow_patch", 8, 5, 7, seed=seed)
    crops = [("carrots", 7), ("beetroots", 3), ("potatoes", 7)]
    for z in (1, 3, 5):                                         # the rows
        name, top = crops[(z // 2) % 3]
        for x in range(1, 7):
            if (x, z) == (4, 3):
                continue
            r = rng.random()
            if r < 0.55:
                b.set(x, 0, z, B("farmland", moisture=rng.choice([0, 0, 3])))
                b.set(x, 1, z, B(name, age=rng.randint(max(1, top - 4), top)))
            elif r < 0.8:
                plant(b, x, z, rng.choice(["short_grass", ("tall", "tall_grass"), "short_grass"]), "grass_block")
            else:
                b.set(x, 0, z, B("coarse_dirt"))
    for z in (2, 4):                                            # weeds between the rows
        for x in range(1, 7):
            if rng.random() < 0.4:
                plant(b, x, z, "short_grass", "grass_block")
    b.set(4, 0, 3, B("coarse_dirt"))                            # the scarecrow
    b.set(4, 1, 3, B("spruce_fence"))
    b.set(4, 2, 3, B("hay_block", axis="y"))
    b.set(4, 3, 3, B("carved_pumpkin", facing=rng.choice(["south", "east"])))
    b.set(3, 2, 3, B("spruce_fence"))
    b.set(5, 2, 3, B("spruce_fence"))
    for z in (0, 3, 6):                                         # the hurdle fence, mostly down
        b.set(0, 1, z, B("spruce_fence"))
    b.set(0, 1, 1, B("spruce_trapdoor", facing="east", half="bottom"))
    b.set(0, 1, 4, B("spruce_trapdoor", facing="east", half="bottom"))
    return b


def bee_skeps(seed):
    """Hives on a plank bench under a hedge: three boxes with their bees still at home, the hedge grown wild
    behind them, flowers all round for the bees to work."""
    rng = random.Random(seed)
    b = Build("bee_skeps", 7, 4, 6, seed=seed)
    for x in range(7):                                          # the hedge
        b.set(x, 1, 0, B("oak_leaves", persistent=True))
        if rng.random() < 0.6:
            b.set(x, 2, 0, B(rng.choice(["oak_leaves", "oak_leaves", "flowering_azalea_leaves"]), persistent=True))
        if rng.random() < 0.5:
            b.set(x, 1, 1, B("oak_leaves", persistent=True))
    for x in (0, 6):                                            # the bench
        b.set(x, 0, 2, B("cobblestone"))
        b.set(x, 1, 2, B("spruce_log", axis="y"))
    for x in range(1, 6):
        b.set(x, 1, 2, B("spruce_slab", type="top"))
    for x in (1, 3, 5):
        b.set(x, 2, 2, B("beehive", facing="south", honey_level=rng.randint(1, 5)), bees(rng.randint(1, 2)))
    fl = ["poppy", "cornflower", "oxeye_daisy", "short_grass", "short_grass", "allium", "dandelion"]
    strew(b, rng, [(x, z) for x in range(7) for z in (3, 4, 5)], fl, "grass_block", 0.6)
    return b


def bird_box(seed):
    """A bird box on a post at the edge of a field: a little plank house with a round hole and a lid,
    the post grown over with grass at its foot."""
    rng = random.Random(seed)
    b = Build("bird_box", 3, 5, 3, seed=seed)
    b.set(1, 0, 1, B("cobblestone"))
    b.set(1, 1, 1, B("spruce_fence"))
    b.set(1, 2, 1, B("spruce_fence"))
    b.set(1, 3, 1, B("oak_planks"))
    b.set(1, 3, 2, B("oak_button", face="wall", facing="south"))   # the entrance hole
    b.set(1, 4, 1, B("spruce_trapdoor", facing="south", half="bottom"))  # the lid
    strew(b, rng, cols(b), ["short_grass", "short_grass", "dandelion"], "grass_block", 0.5)
    return b


def wayside_cross(seed, name="wayside_cross", mat="stone_brick", base="stone_bricks"):
    """A wayside cross of weathered stone where two tracks meet: a stepped plinth, the shaft and its arms,
    a pot of flowers and a stub of candle left at its foot."""
    rng = random.Random(seed)
    b = Build(name, 5, 6, 5, seed=seed)
    c = 2
    for (x, z) in disc(c, c, 1.5, 5, 5):                       # the plinth
        b.set(x, 0, z, B(base))
        if (x, z) != (c, c):
            b.set(x, 1, z, B(f"{mat}_slab", type="bottom"))
    b.set(c, 1, c, B(base))
    for y in (2, 3, 4):
        b.set(c, y, c, B(f"{mat}_wall"))
    b.set(c - 1, 4, c, B(f"{mat}_wall"))                       # the arms
    b.set(c + 1, 4, c, B(f"{mat}_wall"))
    b.set(c, 5, c, B(f"{mat}_wall"))
    b.set(c + 1, 2, c + 1, B("candle", candles=rng.randint(1, 3), lit=False))
    b.set(c - 1, 2, c + 1, B(rng.choice(["potted_poppy", "potted_dandelion", "potted_cornflower"])))
    strew(b, rng, cols(b), ["short_grass"], "grass_block", 0.35)
    return b


def milestone(seed):
    """A milestone by the old road: a squat dressed stone with a rounded top, the figures worn off it, a
    strip of the road's metalling still showing through the grass."""
    rng = random.Random(seed)
    b = Build("milestone", 5, 3, 5, seed=seed)
    for x in range(5):                                          # the road
        for z in (2, 3):
            if rng.random() < 0.75:
                b.set(x, 0, z, B(rng.choice(["dirt_path", "dirt_path", "gravel", "coarse_dirt"])))
    b.set(2, 0, 1, B("stone_bricks"))
    b.set(2, 1, 1, B("chiseled_stone_bricks"))
    b.set(2, 2, 1, B("stone_brick_slab", type="bottom"))
    strew(b, rng, [(x, z) for x in range(5) for z in (0, 1, 4)], ["short_grass", "short_grass", "fern"],
          "grass_block", 0.45)
    return b


def picnic(seed):
    """A picnic someone left in a hurry: a chequered blanket in the long grass, the basket (a barrel) with a
    cake on it a slice short, a jug, a cushion of grass trodden flat."""
    rng = random.Random(seed)
    b = Build("picnic", 6, 3, 5, seed=seed)
    for x in range(1, 4):
        for z in range(1, 4):
            b.set(x, 1, z, B("red_carpet" if (x + z) % 2 else "white_carpet"))
    b.set(4, 1, 2, B("barrel", facing="up"))
    b.set(4, 2, 2, B("cake", bites=rng.randint(1, 3)))
    b.pot(4, 1, 3, facing=rng.choice(H4))
    strew(b, rng, [(x, z) for (x, z) in cols(b) if not (1 <= x <= 4 and 1 <= z <= 3)],
          ["short_grass", "short_grass", ("tall", "tall_grass"), "dandelion"], "grass_block", 0.55)
    return b


def broken_cart(name, seed, wood, load, ground=("coarse_dirt", "dirt_path"), plants=("short_grass",),
                soil="grass_block"):
    """A cart that lost a wheel on the way: down at one corner, the wheel lying flat beside it, part of
    its load spilled into the grass."""
    from smallkit import cart
    rng = random.Random(seed)
    b = Build(name, 7, 4, 6, seed=seed)
    cells, sag, lost = cart(b, 1, 2, "x", wood, rng, broken_corner=0)
    for (wx, wz, face, _) in lost:                              # the wheel, lying flat
        b.set(wx - 1, 1, wz, B(f"{wood}_trapdoor", facing=face, half="bottom"))
    for (x, z) in cells[2:]:
        if rng.random() < 0.6:
            put(b, x, 2, z, mix_of(rng, load))
    for (x, z) in [(0, 4), (1, 5), (4, 5)]:                     # what spilled
        if rng.random() < 0.7:
            put(b, x, 1, z, mix_of(rng, load))
    patch(b, rng, [(x, z) for x in range(7) for z in (2, 3)], list(ground), 0.5)
    strew(b, rng, cols(b), list(plants), soil, 0.25)
    return b


def charcoal_clamp(seed):
    """A charcoal burner's clamp left to go out: a dome of turf and earth over the stacked wood, one side
    broken open on black charcoal, billets stacked ready beside it, the vent on top cold."""
    rng = random.Random(seed)
    b = Build("charcoal_clamp", 8, 4, 7, seed=seed)
    c = (3, 3)
    for (x, z) in disc(*c, 2.7, 8, 7):
        b.set(x, 0, z, B(rng.choice(["coarse_dirt", "dirt", "coarse_dirt"])))
    for (x, z) in disc(*c, 2.1, 8, 7):
        b.set(x, 1, z, B(rng.choice(["dirt", "coarse_dirt", "grass_block", "podzol"])))
    for (x, z) in disc(*c, 1.2, 8, 7):
        b.set(x, 2, z, B(rng.choice(["coarse_dirt", "podzol", "dirt"])))
    b.campfire(3, 3, 3, lit=False, facing="north")              # the vent
    for (x, z) in [(3, 5), (4, 5), (2, 5), (3, 4)]:            # broken open: charcoal
        if b.get(x, 1, z) is not None:
            b.set(x, 1, z, B("coal_block"))
    b.set(3, 0, 5, B("coal_block"))
    b.set(3, 2, 4, B("coal_block"))
    for z in (1, 2, 3, 4):                                      # billets, stacked to dry
        b.set(7, 1, z, B("oak_log", axis="x"))
        if z in (2, 3):
            b.set(7, 2, z, B("oak_log", axis="x"))
    b.set(6, 0, 6, B("oak_log", axis="y"))
    return b


def high_seat(seed):
    """A hunter's high seat at the edge of the wood: four poles holding a boarded platform with a rail, a
    ladder up the front and a plank roof against the rain."""
    rng = random.Random(seed)
    b = Build("high_seat", 5, 9, 6, seed=seed)
    for (x, z) in [(1, 1), (3, 1), (1, 3), (3, 3)]:            # thin poles on bedded feet
        b.set(x, 0, z, B("spruce_log", axis="y"))
        for y in (1, 2, 3, 5, 6):
            b.set(x, y, z, B("spruce_fence"))
    for x in range(1, 4):
        for z in range(1, 4):
            b.set(x, 4, z, B("spruce_planks"))
    for (x, z) in [(2, 1), (1, 2), (3, 2)]:                    # the rail
        b.set(x, 5, z, B("spruce_fence"))
    for x in range(0, 5):                                       # the roof
        for z in range(0, 5):
            b.set(x, 7, z, B("spruce_slab", type="bottom"))
    b.set(2, 0, 3, B("spruce_log", axis="y"))                   # ladder post
    for y in range(1, 4):
        b.set(2, y, 3, B("spruce_log", axis="y"))
    for y in range(1, 5):
        b.set(2, y, 4, B("ladder", facing="south"))
    b.set(2, 0, 4, B("coarse_dirt"))
    strew(b, rng, cols(b), ["short_grass", "fern"], "grass_block", 0.25)
    return b


def conciliation_cross(seed):
    """A conciliation cross, as the Bohemian countryside is dotted with them: a squat cross of rough stone set
    up long ago where someone was killed, the price of peace between two families. Sunk in the turf to its
    arms, weathered, moss on it; nobody remembers the names."""
    rng = random.Random(seed)
    stone, wall = rng.choice([("andesite", "andesite_wall"), ("tuff", "tuff_wall"), ("mossy_cobblestone", "mossy_cobblestone_wall")])
    b = Build("conciliation_cross", 3, 4, 3, seed=seed)
    b.set(1, 0, 1, B("coarse_dirt"))
    b.set(1, 1, 1, B(stone))
    b.set(1, 2, 1, B(wall))
    b.set(0, 2, 1, B(wall))                                     # the stubby arms
    b.set(2, 2, 1, B(wall))
    b.set(1, 3, 1, B(wall))
    b.set(0, 1, 1, B("moss_carpet"))
    strew(b, rng, cols(b), ["short_grass", "short_grass", "fern"], "grass_block", 0.5)
    return b


def field_chapel(seed):
    """A field chapel at the edge of the fields: a whitewashed shrine the size of a wardrobe under a little tiled
    gable, open at the front on a niche with a stone altar and a candle, a cross on the ridge. Nobody keeps it
    now but the flowers someone still leaves."""
    rng = random.Random(seed)
    b = Build("field_chapel", 5, 7, 6, seed=seed)
    for x in range(1, 4):                                       # the step and floor
        for z in range(1, 4):
            b.set(x, 0, z, B("stone_bricks"))
    for x in range(1, 4):
        b.set(x, 0, 4, B(rng.choice(["coarse_dirt", "dirt_path", "gravel"])))
    for y in (1, 2, 3):                                         # whitewashed walls, open at the front
        for x in range(1, 4):
            for z in range(1, 4):
                if (x, z) == (2, 2) or (x, z) == (2, 3) and y < 3:
                    continue
                b.set(x, y, z, B("calcite"))
    b.set(2, 1, 2, B("chiseled_stone_bricks"))                  # the altar in the niche
    b.set(2, 2, 2, B("candle", candles=rng.randint(1, 3), lit=False))
    for z in range(0, 5):                                       # the tiled gable, overhanging front and back
        b.set(1, 4, z, stairs("brick", "east"))
        b.set(3, 4, z, stairs("brick", "west"))
        b.set(2, 4, z, B("bricks"))
        b.set(2, 5, z, slab("brick"))
    b.set(2, 6, 1, B("oak_fence"))                              # the cross on the ridge
    b.set(1, 1, 4, B(rng.choice(["potted_poppy", "potted_cornflower", "potted_oxeye_daisy"])))
    strew(b, rng, [(x, z) for (x, z) in cols(b) if not (1 <= x <= 3 and 1 <= z <= 4)],
          ["short_grass", "poppy", "oxeye_daisy", "short_grass"], "grass_block", 0.5)
    return b


def linden(seed):
    """A lime tree planted at a field corner long ago, as Bohemians plant them by crossroads and chapels: a thick
    bole under a broad round crown, and a bench beneath it looking out over the fields."""
    from arch import tree
    rng = random.Random(seed)
    b = Build("linden", 11, 12, 11, seed=seed)
    for x in range(11):
        for z in range(11):
            b.set(x, 0, z, B("grass_block"))
    tree(b, 5, 1, 5, rng, "oak_log", "oak_leaves", height=5, crown=4.0, flat=0.8)
    b.set(6, 1, 5, B("oak_log", axis="y"))                      # the bole thickens at the foot
    b.set(5, 1, 6, B("oak_log", axis="y"))
    for x in (4, 5):                                            # the bench, looking south
        b.set(x, 1, 8, stairs("oak", "north"))
    strew(b, rng, [(x, z) for (x, z) in cols(b) if abs(x - 5) + abs(z - 5) > 2 and not (z == 8 and x in (4, 5))],
          ["short_grass", "short_grass", "dandelion", "fern"], "grass_block", 0.35)
    return b


def saw_trestle(seed):
    """A saw trestle with a log still across it, half cut through, the rounds already sawn lying in the
    grass, the chopping block beside them."""
    rng = random.Random(seed)
    b = Build("saw_trestle", 7, 3, 5, seed=seed)
    for x in (1, 4):
        b.set(x, 0, 1, B("coarse_dirt"))
        b.set(x, 1, 1, B("spruce_fence"))
    for x in range(0, 6):
        b.set(x, 2, 1, B("oak_log", axis="x") if x != 3 else B("stripped_oak_log", axis="x"))
    for (x, z) in [(1, 3), (3, 3), (4, 4)]:                     # sawn rounds
        b.set(x, 1, z, B("oak_log", axis="y"))
        b.set(x, 0, z, B("coarse_dirt"))
    b.set(6, 1, 3, B("oak_slab", type="bottom"))
    patch(b, rng, [(x, z) for x in range(7) for z in (0, 1, 2)], ["coarse_dirt", "dirt"], 0.35)
    strew(b, rng, cols(b), ["short_grass", "fern"], "grass_block", 0.2)
    return b


def sugaring_kettle(seed):
    """A sugar maker's kettle in the maple woods: the cauldron hung by a chain from a pole on two crotched
    stakes over a fire pit, sap barrels beside it and a stack of split maple."""
    rng = random.Random(seed)
    b = Build("sugaring_kettle", 7, 5, 5, seed=seed)
    for x in (1, 4):
        b.set(x, 0, 2, B("coarse_dirt"))
        for y in (1, 2):
            b.set(x, y, 2, B("expanse:maple_fence"))
    for x in range(1, 5):
        b.set(x, 3, 2, B("expanse:maple_log", axis="x"))
    b.set(2, 2, 2, B("iron_chain", axis="y"))
    b.set(2, 1, 2, B("water_cauldron", level=rng.randint(1, 3)))
    b.campfire(2, 0, 2, lit=False, facing="north")
    for (x, z) in [(2, 1), (2, 3), (3, 2)]:                     # the fire pit's stones
        b.set(x, 0, z, B(rng.choice(["cobblestone", "mossy_cobblestone"])))
    b.set(5, 1, 1, B("barrel", facing="up"))
    b.set(5, 1, 3, B("barrel", facing="up"))
    for z in (0, 1, 2, 3, 4):                                   # split maple, stacked
        b.set(6, 1, z, B("expanse:maple_log", axis="x"))
        if 1 <= z <= 3:
            b.set(6, 2, z, B("expanse:maple_log", axis="x"))
    litter_on(b, rng, cols(b), "leaf_litter", 6)
    return b


def sheepfold(seed):
    """The corner of a dry-stone sheepfold: two walls meeting, a hurdle gate in one, a hay rack and a water
    trough inside, sheep long gone."""
    rng = random.Random(seed)
    b = Build("sheepfold", 8, 4, 8, seed=seed)
    mix = [("cobblestone", 3), ("mossy_cobblestone", 3), ("stone", 1), ("andesite", 1)]
    wall = [(x, 0) for x in range(8)] + [(0, z) for z in range(1, 8)]
    for (x, z) in wall:
        if (x, z) == (5, 0):
            b.set(x, 0, z, B("coarse_dirt"))
            b.set(x, 1, z, B("spruce_fence_gate", facing="south", open=rng.random() < 0.5))
            continue
        h = 2 if (x + z) % 7 != 3 else 1
        if x + z >= 7 and rng.random() < 0.4:
            h = 1
        for y in range(0, h + 1):
            b.set(x, y, z, stones(rng, mix))
        if h == 2 and rng.random() < 0.6:
            b.set(x, 3, z, B("mossy_cobblestone_slab" if rng.random() < 0.5 else "cobblestone_slab", type="bottom"))
    b.set(2, 1, 2, B("hay_block", axis="y"))                    # the hay rack
    b.set(1, 1, 3, B("water_cauldron", level=rng.randint(1, 3)))   # the trough
    patch(b, rng, [(x, z) for x in range(1, 8) for z in range(1, 8)], ["coarse_dirt", "dirt"], 0.15)
    strew(b, rng, [(x, z) for x in range(1, 8) for z in range(1, 8)], ["short_grass"], "grass_block", 0.3)
    return b


def peat_stack(seed):
    """Peat cut from the bank and set up to dry: little ricks of brown turves, the cut face of the bank a
    step down beside them, the bog closing in round it."""
    rng = random.Random(seed)
    b = Build("peat_stack", 7, 3, 6, seed=seed)
    for x in range(1, 6):                                       # the cutting: a step down, peat in its face
        b.set(x, 0, 1, AIR)
        b.set(x, 0, 0, B("expanse:peat"))
    for (x, z) in [(1, 3), (3, 3), (5, 3), (2, 5), (4, 5)]:    # ricks of turves
        b.set(x, 0, z, B("expanse:peat"))
        b.set(x, 1, z, B("expanse:peat"))
        if rng.random() < 0.5:
            b.set(x, 2, z, B("expanse:peat"))
    patch(b, rng, cols(b), ["coarse_dirt", "expanse:peat", "podzol"], 0.25)
    strew(b, rng, cols(b), ["short_grass", "short_grass"], "podzol", 0.3)
    return b


def shepherds_shelter(seed):
    """A shepherd's shelter: a horseshoe of dry stone waist high, open away from the weather, a flat
    stone for a seat inside and the black ring of an old fire."""
    rng = random.Random(seed)
    b = Build("shepherds_shelter", 6, 3, 6, seed=seed)
    mix = [("cobblestone", 3), ("mossy_cobblestone", 2), ("stone", 2), ("andesite", 1)]
    for (x, z) in disc(2.5, 2.5, 2.6, 6, 6, 1.6):
        if z >= 4 and 2 <= x <= 3:
            continue                                            # the opening
        h = 2 if z <= 2 else 1
        for y in range(0, h + 1):
            b.set(x, y, z, stones(rng, mix))
    b.campfire(3, 1, 3, lit=False, facing="north")
    b.set(3, 0, 3, B("coarse_dirt"))
    b.set(2, 0, 2, B("stone"))
    b.set(2, 1, 2, B("stone_slab", type="bottom"))              # the seat
    strew(b, rng, cols(b), ["short_grass"], "grass_block", 0.3)
    return b


def lean_to(seed):
    """A trapper's lean-to: a slope of spruce poles and boughs against a ridge pole on two forked stakes,
    a bedroll under it, a fire ring in front, cold."""
    rng = random.Random(seed)
    b = Build("lean_to", 6, 4, 6, seed=seed)
    for x in (0, 4):                                            # forked stakes and the ridge pole
        b.set(x, 1, 3, B("spruce_fence"))
        b.set(x, 2, 3, B("spruce_fence"))
    for x in range(0, 5):
        b.set(x, 3, 3, B("spruce_log", axis="x"))
        b.set(x, 2, 4, B("spruce_stairs", facing="north"))     # the sloping roof, down to the back
        b.set(x, 1, 5, B("spruce_stairs", facing="north"))
        if rng.random() < 0.5:
            b.set(x, 3, 4, B("spruce_leaves", persistent=True))
    b.bed(1, 1, 4, "east", "straw")                             # the bedroll, under the slope
    b.campfire(2, 1, 1, lit=False, facing="north")
    b.set(2, 0, 1, B("coarse_dirt"))
    for (x, z) in [(1, 1), (3, 1), (2, 0)]:
        b.set(x, 0, z, B(rng.choice(["cobblestone", "stone"])))
    strew(b, rng, cols(b), ["fern", "short_grass"], "podzol", 0.25)
    return b


def snow_sled(seed):
    """A sled left in the snow and drifted half over: a plank deck on runners curled up at the front, a
    barrel and a bundle still lashed to it, snow lying on everything."""
    rng = random.Random(seed)
    b = Build("snow_sled", 7, 3, 4, seed=seed)
    for x in range(1, 5):
        for z in (1, 2):
            b.set(x, 1, z, B("spruce_slab", type="bottom"))
            b.set(x, 0, z, B("snow_block"))                       # sunk into the drift
    for z in (1, 2):
        b.set(5, 1, z, B("spruce_stairs", facing="west"))      # the curled-up front of the runners
    b.set(2, 1, 1, B("barrel", facing="up"), {"LootTable": LOST_CACHE, "components": {}, "id": "minecraft:barrel"})
    b.set(3, 1, 2, B("white_wool"))
    for (x, z) in cols(b):
        if b.get(x, 1, z) is None and rng.random() < 0.5:
            b.set(x, 1, z, B("snow", layers=rng.choice([2, 3, 4])))
    snow_on(b, rng, 0.6, (1, 2))
    return b


def ice_fishing(seed):
    """Someone's ice-fishing on a frozen pond: a hole cut in the ice, a rod rest of forked sticks over it,
    a low tent of hides against the wind, a barrel of bait, snow drifting across."""
    from smallkit import a_tent
    rng = random.Random(seed)
    b = Build("ice_fishing", 8, 4, 7, seed=seed)
    for (x, z) in disc(3.5, 3, 3.2, 8, 7):
        b.set(x, 0, z, B("ice"))
    b.set(3, 0, 3, B("water", level=0))                         # the hole
    b.set(3, 1, 4, B("spruce_fence"))                           # the rod rest
    b.set(2, 1, 4, B("tripwire_hook", facing="west"))
    a_tent(b, 5, 1, 3, "z", "brown", y=1)                       # the tent of hides
    b.set(1, 1, 2, B("barrel", facing="up"))
    for (x, z) in cols(b):
        if b.get(x, 0, z) is not None and b.get(x, 0, z).short == "ice" and b.get(x, 1, z) is None \
                and rng.random() < 0.3:
            b.set(x, 1, z, B("snow", layers=1))
    return b


def marker_pole(seed):
    """A trail marker on the high pass: a pole in a heap of stones with a faded red banner tied to it, so
    the way can be found when the snow comes."""
    rng = random.Random(seed)
    b = Build("marker_pole", 3, 5, 3, seed=seed)
    for (x, z) in cols(b):
        b.set(x, 0, z, stones(rng, [("cobblestone", 2), ("stone", 2), ("andesite", 1)]))
    for (x, z) in [(1, 0), (0, 1), (2, 1), (1, 2)]:
        if rng.random() < 0.6:
            b.set(x, 1, z, stones(rng, [("cobblestone", 2), ("stone", 2)]))
    for y in (1, 2, 3):
        b.set(1, y, 1, B("spruce_log", axis="y"))
    b.banner(1, 3, 2, "red", [("stripe_bottom", "white")], wall_facing="south")
    snow_on(b, rng, 0.6, (1, 2))
    return b


def springboard_stump(seed):
    """A redwood the old fallers cut: a huge stump sawn off a man's height up, the notches for their
    springboards still in its side with a board left in one, sawn rounds lying round."""
    rng = random.Random(seed)
    b = Build("springboard_stump", 6, 5, 6, seed=seed)
    for x in (2, 3):
        for z in (2, 3):
            for y in range(0, 4):
                b.set(x, y, z, B("expanse:redwood_log", axis="y"))
            b.set(x, 3, z, B("expanse:stripped_redwood_log", axis="y"))   # the flat saw cut
    for (x, z) in [(1, 2), (4, 3), (2, 4), (3, 1)]:              # root flare
        b.set(x, 0, z, B("expanse:redwood_wood", axis="y"))
    b.set(1, 2, 3, B("spruce_slab", type="top"))                 # the springboard, jutting from its notch
    b.set(5, 1, 0, B("expanse:redwood_log", axis="y"))           # a sawn round
    b.set(0, 1, 5, B("expanse:redwood_log", axis="x"))
    b.set(1, 1, 5, B("expanse:redwood_log", axis="x"))
    b.set(3, 4, 3, B("moss_carpet"))
    strew(b, rng, cols(b), ["fern", ("tall", "large_fern"), "fern"], "podzol", 0.3)
    return b


def boardwalk(seed):
    """What is left of a boardwalk across the bog: planks on sunk posts, some gone, some sunk into the moss,
    one post leaning with nothing on it."""
    rng = random.Random(seed)
    b = Build("boardwalk", 10, 3, 4, seed=seed)
    for x in range(10):
        if x % 3 == 0:
            for z in (1, 2):
                b.set(x, 0, z, B("spruce_log", axis="y"))
        r = rng.random()
        for z in (1, 2):
            if r < 0.65:
                b.set(x, 1, z, B("spruce_slab", type="bottom"))
            elif r < 0.85:
                b.set(x, 0, z, B("spruce_slab", type="top"))   # sunk into the moss
    b.set(6, 1, 0, B("spruce_fence"))
    patch(b, rng, cols(b), ["expanse:sphagnum_moss", "expanse:peat", "expanse:sphagnum_moss"], 0.5)
    strew(b, rng, [(x, z) for x in range(10) for z in (0, 3)], ["expanse:cotton_grass", "expanse:cranberry_bush",
                                                                  "short_grass"], "expanse:sphagnum_moss", 0.4)
    return b


def upturned_boat(name, seed, wood, ground="gravel"):
    """A rowing boat hauled up and turned over for the winter: its hull upside down on the shingle, an oar
    leaning against it, a net mending on a post."""
    rng = random.Random(seed)
    b = Build(name, 7, 4, 5, seed=seed)
    for x in range(1, 5):
        b.set(x, 1, 1, B(f"{wn(wood, 'stairs')}", facing="north", half="top"))
        b.set(x, 1, 3, B(f"{wn(wood, 'stairs')}", facing="south", half="top"))
        b.set(x, 2, 2, B(wn(wood, "slab"), type="bottom"))      # the keel
        b.set(x, 1, 2, B(wn(wood, "planks")))
    b.set(0, 1, 2, B(wn(wood, "stairs"), facing="west", half="top"))   # the stem
    b.set(5, 1, 2, B(wn(wood, "stairs"), facing="east", half="top"))   # the stern
    b.set(6, 1, 1, B(wn(wood, "fence")))                         # an oar against a post
    b.set(6, 2, 1, B(wn(wood, "fence")))
    patch(b, rng, cols(b), [ground], 0.4)
    return b


def ranch_gate(name, seed, wood, soil="coarse_dirt", plants=("short_dry_grass", "dead_bush")):
    """A ranch gateway standing on its own: two tall posts and a crossbar with the brand board hanging
    from it, the fence it once closed long gone, a skull nailed above."""
    rng = random.Random(seed)
    b = Build(name, 6, 6, 3, seed=seed)
    for x in (0, 5):
        b.set(x, 0, 1, B(wn(wood, "log"), axis="y"))
        for y in range(1, 5):
            b.set(x, y, 1, B(wn(wood, "log"), axis="y"))
    for x in range(0, 6):
        b.set(x, 5, 1, B(wn(wood, "log"), axis="x"))
    b.sign(2, 4, 1, "spruce", ["", "", "", ""], hanging=True, attached=False, rotation=4)
    b.set(3, 4, 1, B("iron_chain", axis="y"))
    for x in (1, 4):                                            # what is left of the rails
        if rng.random() < 0.6:
            b.set(x, 1, 1, B(wn(wood, "fence")))
    patch(b, rng, [(x, 1) for x in range(1, 5)], ["coarse_dirt", "dirt_path"], 0.6)
    strew(b, rng, cols(b), list(plants), soil, 0.3)
    return b


def net_rack(name, seed, wood, ground="sand"):
    """Nets hung to dry: a rail of posts with the nets draped between them, a float or two, a barrel of
    tackle at the end."""
    rng = random.Random(seed)
    b = Build(name, 6, 4, 3, seed=seed)
    for x in range(0, 5):
        b.set(x, 2, 1, B(wn(wood, "fence")))
        if x % 2 == 0:
            b.set(x, 1, 1, B(wn(wood, "fence")))
            b.set(x, 0, 1, B(wn(wood, "log"), axis="y"))
        else:
            b.set(x, 1, 1, B("cobweb"))
    b.set(5, 1, 1, B("barrel", facing="up"))
    patch(b, rng, cols(b), [ground], 0.3)
    return b


def thorn_boma(seed):
    """A thorn boma: a ring of cut thorn brush where herders penned their cattle for the night, a gap for
    the gate, the ground inside trodden bare."""
    rng = random.Random(seed)
    b = Build("thorn_boma", 8, 3, 8, seed=seed)
    for (x, z) in disc(3.5, 3.5, 3.6, 8, 8, 2.6):
        if z >= 6 and 3 <= x <= 4:
            continue
        b.set(x, 0, z, B("coarse_dirt"))
        b.set(x, 1, z, B("dead_bush") if rng.random() < 0.7 else B("acacia_fence"))
    patch(b, rng, disc(3.5, 3.5, 2.6, 8, 8), ["coarse_dirt", "coarse_dirt", "dirt"], 0.6)
    return b


def yurt_ring(seed):
    """Where a yurt stood: a ring of stones that held down its felt, the hearth stone in the middle, a
    scrap of felt and some bones left when the camp moved on."""
    rng = random.Random(seed)
    b = Build("yurt_ring", 7, 2, 7, seed=seed)
    for (x, z) in disc(3, 3, 3.1, 7, 7, 2.4):
        if rng.random() < 0.75:
            b.set(x, 0, z, stones(rng, [("cobblestone", 2), ("andesite", 2), ("stone", 1)]))
    b.campfire(3, 1, 3, lit=False, facing="north")
    b.set(3, 0, 3, B("coarse_dirt"))
    for (x, z) in [(2, 2), (4, 4)]:
        b.set(x, 1, z, B(rng.choice(["red_carpet", "orange_carpet", "brown_carpet"])))
    patch(b, rng, disc(3, 3, 2.3, 7, 7), ["coarse_dirt", "dirt"], 0.4)
    b.set(5, 1, 2, B("bone_block", axis="x"))
    return b


def ovoo(seed):
    """An ovoo on the steppe: a cairn of stones heaped round a pole hung with blue cloth, left by
    travellers who walked round it three times for a safe road."""
    rng = random.Random(seed)
    b = Build("ovoo", 5, 5, 5, seed=seed)
    mix = [("cobblestone", 2), ("andesite", 2), ("stone", 1), ("granite", 1)]
    for (x, z) in disc(2, 2, 2.2, 5, 5):
        b.set(x, 0, z, stones(rng, mix))
    for (x, z) in disc(2, 2, 1.5, 5, 5):
        b.set(x, 1, z, stones(rng, mix))
    for y in (2, 3, 4):
        b.set(2, y, 2, B("spruce_log", axis="y") if y < 4 else B("spruce_fence"))
    b.banner(2, 3, 3, "light_blue", [], wall_facing="south")
    b.banner(1, 3, 2, "blue", [], wall_facing="west")
    for (x, z) in [(1, 2), (2, 1), (3, 2)]:
        if rng.random() < 0.7:
            put(b, x, 2, z, stones(rng, mix))
    return b


def balbal(seed):
    """A balbal: a stone figure on the open steppe, roughly carved, its face to the east, set up over a
    grave so long ago no one knows whose."""
    rng = random.Random(seed)
    b = Build("balbal", 3, 5, 3, seed=seed)
    b.set(1, 0, 1, B("stone"))
    b.set(1, 1, 1, B("andesite"))
    b.set(1, 2, 1, B("andesite"))
    b.set(1, 3, 1, B("chiseled_stone_bricks"))                   # the face
    b.set(1, 4, 1, B("andesite_slab", type="bottom"))
    for (x, z) in [(0, 1), (2, 1), (1, 0)]:
        put(b, x, 0, z, stones(rng, [("cobblestone", 1), ("andesite", 1)]))
    strew(b, rng, cols(b), ["short_grass"], "coarse_dirt", 0.4)
    return b


def swag_camp(seed):
    """A drover's camp in the outback: a swag rolled out by a dead fire, the billy can by the coals, a
    saddle on a log, red dust over everything."""
    rng = random.Random(seed)
    b = Build("swag_camp", 7, 3, 6, seed=seed)
    b.bed(1, 1, 2, "south", "straw")                             # the swag
    b.campfire(3, 1, 3, lit=False, facing="north")
    b.set(3, 0, 3, B("coarse_dirt"))
    b.set(4, 1, 3, B("cauldron"))                                # the billy
    b.set(4, 1, 1, B("expanse:eucalyptus_log", axis="x"))
    b.set(5, 1, 1, B("expanse:eucalyptus_log", axis="x"))
    b.set(5, 2, 1, B("brown_carpet"))                            # a saddle blanket
    patch(b, rng, disc(3, 3, 2.4, 7, 6), ["coarse_dirt", "expanse:red_earth"], 0.5)
    strew(b, rng, cols(b), ["expanse:spinifex", "short_dry_grass"], "expanse:red_earth", 0.2)
    return b


def stockyard(name, seed, wood, soil="coarse_dirt", plants=("short_dry_grass",)):
    """The corner of an old stockyard: post-and-rail fence meeting at a strainer post, a water trough
    against it, the ground inside trodden to dust."""
    rng = random.Random(seed)
    b = Build(name, 8, 3, 8, seed=seed)
    run = [(x, 0) for x in range(8)] + [(0, z) for z in range(1, 8)]
    for (x, z) in run:
        post = (x + z) % 3 == 0
        if post:
            b.set(x, 0, z, B(wn(wood, "log"), axis="y"))
            b.set(x, 1, z, B(wn(wood, "log"), axis="y"))
            if x + z == 0:
                b.set(x, 2, z, B(wn(wood, "log"), axis="y"))
        elif rng.random() < 0.8:
            b.set(x, 1, z, B(wn(wood, "fence")))
    b.set(2, 1, 1, B("water_cauldron", level=rng.randint(1, 3)))
    b.set(3, 1, 1, B("cauldron"))
    patch(b, rng, [(x, z) for x in range(1, 8) for z in range(1, 8)], ["coarse_dirt", soil], 0.35)
    strew(b, rng, cols(b), list(plants), soil, 0.25)
    return b


def ocotillo_fence(seed):
    """A living fence of ocotillo canes set in a row the old way, a mesquite post at the corner and a gate
    hung on it, the canes rooted and leafing."""
    rng = random.Random(seed)
    b = Build("ocotillo_fence", 8, 4, 6, seed=seed)
    run = [(x, 0) for x in range(1, 8)] + [(0, z) for z in range(1, 6)]
    b.set(0, 0, 0, B("dark_oak_log", axis="y"))
    b.set(0, 1, 0, B("dark_oak_log", axis="y"))
    b.set(0, 2, 0, B("dark_oak_log", axis="y"))
    for (x, z) in run:
        if (x, z) == (0, 3):
            b.set(x, 1, z, B("dark_oak_fence_gate", facing="east"))
            b.set(x, 0, z, B("coarse_dirt"))
            continue
        if rng.random() < 0.12:
            continue
        h = rng.choice([2, 2, 3])
        b.set(x, 0, z, B("coarse_dirt"))
        for y in range(1, h + 1):
            b.set(x, y, z, B("expanse:ocotillo", tip=(y == h)))
    strew(b, rng, cols(b), ["short_dry_grass", "dead_bush", "expanse:desert_marigold"], "coarse_dirt", 0.15)
    return b


def prospect_pit(name, seed, spoil, wood="spruce", soil="red_sand"):
    """A prospector's pit: a shaft barely begun, the windlass over it on two posts with its chain hanging
    down, the spoil heaped beside, a pan of water for washing the dirt, a barrel of tools."""
    rng = random.Random(seed)
    b = Build(name, 7, 4, 6, seed=seed)
    for (x, z) in [(2, 2), (3, 2), (2, 3), (3, 3)]:
        b.set(x, 0, z, AIR)
    for x in (1, 4):
        b.set(x, 0, 2, B(wn(wood, "log"), axis="y"))
        b.set(x, 1, 2, B(wn(wood, "fence")))
    for x in range(1, 5):
        b.set(x, 2, 2, B(wn(wood, "log"), axis="x"))
    b.set(2, 1, 2, B("iron_chain", axis="y"))
    for (x, z) in [(5, 3), (5, 4), (6, 3), (4, 4), (5, 5)]:      # the spoil heap
        b.set(x, 0, z, mix_of(rng, spoil))
        if rng.random() < 0.5:
            b.set(x, 1, z, mix_of(rng, spoil))
    b.set(1, 1, 4, B("water_cauldron", level=2))
    b.set(0, 1, 4, B("barrel", facing="up"), {"LootTable": PROSPECTOR, "components": {}, "id": "minecraft:barrel"})
    patch(b, rng, cols(b), [soil], 0.2)
    return b


def salt_stack(seed):
    """Salt cut from the flat in blocks and stacked to be fetched, a broken barrow beside it, the pit they
    came out of filling with brine."""
    b = Build("salt_stack", 7, 4, 6, seed=seed)
    for (x, z, h) in [(1, 1, 3), (2, 1, 2), (1, 2, 2), (2, 2, 1), (3, 1, 1)]:
        for y in range(0, h + 1):
            b.set(x, y, z, B("expanse:salt_block"))
    pool(b, [(4, 3), (5, 3)], "expanse:salt_block")
    b.set(0, 1, 4, B("spruce_slab", type="bottom"))
    b.set(1, 1, 4, B("spruce_trapdoor", facing="north", half="bottom", open=True))
    return b


def salt_cairn(seed):
    """A cairn of salt blocks raised to mark a way across the flat, a stick standing out of its top."""
    rng = random.Random(seed)
    b = Build("salt_cairn", 3, 5, 3, seed=seed)
    for (x, z) in cols(b):
        if (x, z) != (2, 2) or rng.random() < 0.5:
            b.set(x, 0, z, B("expanse:salt_block"))
    for (x, z) in [(1, 1), (0, 1), (1, 0), (2, 1), (1, 2)]:
        b.set(x, 1, z, B("expanse:salt_block"))
    b.set(1, 2, 1, B("expanse:salt_block"))
    b.set(1, 3, 1, B("spruce_fence"))
    return b


def column_amphorae(seed):
    """A broken column in the olive grove: the stump of the shaft still standing, a drum and the carved
    capital lying where they fell, amphorae half sunk in the red earth beside them, one still sealed."""
    rng = random.Random(seed)
    b = Build("column_amphorae", 8, 4, 6, seed=seed)
    b.set(1, 0, 2, B("smooth_quartz"))                          # the base
    for y in (1, 2):
        b.set(1, y, 2, B("quartz_pillar", axis="y"))
    b.set(1, 3, 2, B("quartz_slab", type="bottom"))              # broken off
    for x in (3, 4):                                            # a fallen drum
        b.set(x, 1, 2, B("quartz_pillar", axis="x"))
    b.set(6, 1, 1, B("chiseled_quartz_block"))                   # the capital, lying
    b.set(6, 0, 2, B("quartz_block"))
    b.set(5, 1, 4, B("decorated_pot", facing="east"), {"components": {}, "sherds": {
        "back": {"id": "minecraft:brick"}, "front": {"id": "minecraft:brick"},
        "left": {"id": "minecraft:brick"}, "right": {"id": "minecraft:brick"}},
        "LootTable": AMPHORA, "id": "minecraft:decorated_pot"})
    b.pot(3, 1, 4, facing="south")
    b.set(2, 0, 4, B("decorated_pot", cracked=True, facing="north"), {"components": {}, "sherds": {
        "back": {"id": "minecraft:brick"}, "front": {"id": "minecraft:brick"},
        "left": {"id": "minecraft:brick"}, "right": {"id": "minecraft:brick"}}, "id": "minecraft:decorated_pot"})
    patch(b, rng, cols(b), ["expanse:red_earth", "coarse_dirt"], 0.35)
    strew(b, rng, cols(b), ["short_grass", "expanse:lavender"], "expanse:red_earth", 0.25)
    return b


def terrace_wall(seed):
    """A dry-stone terrace on the hillside: the retaining wall knee high, the red earth it holds up behind
    it planted with lavender, a stone step up at one end."""
    rng = random.Random(seed)
    b = Build("terrace_wall", 9, 3, 5, seed=seed)
    mix = [("expanse:limestone", 3), ("cobblestone", 1), ("expanse:mossy_limestone", 1)]
    for x in range(9):
        b.set(x, 0, 1, stones(rng, mix))
        if x != 7 and rng.random() < 0.9:
            b.set(x, 1, 1, stones(rng, mix))
        for z in (2, 3, 4):
            b.set(x, 0, z, B("expanse:red_earth"))
            if x < 8:
                b.set(x, 1, z, B("expanse:red_earth"))
    b.set(7, 1, 1, B("expanse:limestone_stairs", facing="south"))   # the step up
    for x in range(8):
        for z in (2, 4):
            if rng.random() < 0.75:
                b.set(x, 2, z, B("expanse:lavender"))
    return b


def sunken_skiff(name, seed, wood, ground="mud"):
    """A skiff swamped in the shallows and left: its gunwales just above the water, the bottom silted, a
    lily pad grown in it, the painter's post nearby."""
    rng = random.Random(seed)
    b = Build(name, 7, 3, 5, seed=seed)
    hull = [(x, z) for x in range(1, 6) for z in range(1, 4)]
    for (x, z) in hull:
        edge = x in (1, 5) or z in (1, 3)
        b.set(x, 0, z, B(wn(wood, "planks")) if edge else B("water", level=0))
        if edge and rng.random() < 0.7:
            b.set(x, 1, z, B(wn(wood, "slab"), type="bottom"))
    b.set(3, 1, 2, B("lily_pad"))
    b.set(6, 0, 2, B(wn(wood, "log"), axis="y"))
    b.set(6, 1, 2, B(wn(wood, "fence")))
    patch(b, rng, cols(b), [ground, "mud", "moss_block"], 0.4)
    return b


def fish_weir(seed):
    """A fish weir in the creek mud: a V of stakes leading into a trap, water standing between them."""
    rng = random.Random(seed)
    b = Build("fish_weir", 7, 3, 7, seed=seed)
    stakes = [(0, 1), (1, 2), (2, 3), (3, 4), (6, 1), (5, 2), (4, 3), (3, 5), (2, 5), (4, 5)]
    for (x, z) in stakes:
        b.set(x, 0, z, B("mud"))
        b.set(x, 1, z, B("bamboo_fence"))
    pool(b, [(2, 2), (3, 2), (4, 2), (3, 3), (1, 1), (5, 1)], "mud")
    patch(b, rng, cols(b), ["mud", "mud", "mangrove_roots"], 0.5)
    return b


def stilt_stubs(name, seed, wood):
    """All that stands of a stilt house: four posts out of the mud, a few floor boards still across two of
    them, the ladder fallen."""
    rng = random.Random(seed)
    b = Build(name, 6, 4, 6, seed=seed)
    for (x, z, h) in [(1, 1, 3), (4, 1, 2), (1, 4, 2), (4, 4, 1)]:
        for y in range(0, h + 1):
            b.set(x, y, z, B(wn(wood, "log"), axis="y"))
    for x in range(1, 5):
        if rng.random() < 0.7:
            put(b, x, 3, 1, B(wn(wood, "slab"), type="bottom"))
    b.set(2, 1, 2, B(wn(wood, "trapdoor"), facing="north", half="bottom"))
    b.set(3, 1, 3, B(wn(wood, "trapdoor"), facing="east", half="bottom"))
    patch(b, rng, cols(b), ["mud", "mud", "moss_block"], 0.4)
    strew(b, rng, cols(b), ["fern", "short_grass"], "mud", 0.2)
    return b


def jungle_idol(seed):
    """A stone idol in the forest: a squat figure of mossy stone with a carved face, offerings of flowers
    in a bowl and candles burnt down at its feet, vines over its shoulders."""
    rng = random.Random(seed)
    b = Build("jungle_idol", 5, 5, 4, seed=seed)
    for (x, z) in [(1, 1), (2, 1), (3, 1), (1, 2), (2, 2), (3, 2)]:
        b.set(x, 0, z, B("mossy_stone_bricks"))
    b.set(2, 1, 1, B("mossy_stone_bricks"))
    b.set(2, 2, 1, B("stone_bricks"))
    b.set(2, 3, 1, B("chiseled_stone_bricks"))                  # the face
    b.set(2, 4, 1, B("mossy_stone_brick_slab", type="bottom"))
    b.set(1, 1, 1, B("mossy_stone_brick_stairs", facing="east"))   # its shoulders
    b.set(3, 1, 1, B("mossy_stone_brick_stairs", facing="west"))
    b.set(2, 1, 2, B("potted_red_tulip" if rng.random() < 0.5 else "potted_dandelion"))
    b.set(1, 1, 2, B("candle", candles=rng.randint(1, 3), lit=False))
    b.set(3, 1, 2, B("candle", candles=rng.randint(1, 2), lit=False))
    b.set(2, 2, 0, B("vine", south=True))
    b.set(2, 3, 0, B("vine", south=True))
    b.set(1, 2, 1, B("vine", east=True))
    strew(b, rng, cols(b), ["fern", "fern", ("tall", "large_fern")], "podzol", 0.3)
    return b


def stone_lantern(name, seed, stone="stone_brick", base="stone_bricks", top="stone_brick_slab"):
    """A stone lantern by the path: a post on a footing, the light box with a lantern in it, a cap on top;
    moss in the joints."""
    rng = random.Random(seed)
    b = Build(name, 3, 5, 3, seed=seed)
    b.set(1, 0, 1, B(base))
    b.set(1, 1, 1, B(f"{stone}_wall"))
    b.set(1, 2, 1, B("lantern", hanging=False))
    b.set(1, 3, 1, B(top, type="bottom"))
    for (x, z) in [(0, 1), (2, 1), (1, 0), (1, 2)]:
        b.set(x, 0, z, B(f"{stone}_slab", type="top") if rng.random() < 0.5 else B(base))
    strew(b, rng, [(0, 0), (2, 2), (0, 2), (2, 0)], ["fern", "short_grass"], "moss_block", 0.6)
    return b


def garden_urn(seed):
    """An urn on its pedestal at the end of a lost garden walk, flowers seeded all round it."""
    rng = random.Random(seed)
    b = Build("garden_urn", 5, 4, 5, seed=seed)
    b.set(2, 0, 2, B("stone_bricks"))
    b.set(2, 1, 2, B("stone_brick_wall"))
    b.set(2, 2, 2, B("decorated_pot", facing=rng.choice(H4)), {"components": {}, "sherds": {
        "back": {"id": "minecraft:brick"}, "front": {"id": "minecraft:flow_pottery_sherd"},
        "left": {"id": "minecraft:brick"}, "right": {"id": "minecraft:brick"}}, "id": "minecraft:decorated_pot"})
    for (x, z) in [(2, 0), (2, 1), (2, 3), (2, 4)]:              # a line of flagstones
        if rng.random() < 0.7:
            b.set(x, 0, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"])))
    strew(b, rng, cols(b), ["short_grass", "short_grass", "allium", "pink_tulip", ("tall", "peony")],
          "grass_block", 0.5)
    return b


def lost_cache(name, seed, cover, ground):
    """A cache someone hid and never came back for: a barrel sunk to its lid under a heaped stone or two,
    the earth round it disturbed."""
    rng = random.Random(seed)
    b = Build(name, 3, 2, 3, seed=seed)
    b.barrel(1, 0, 1, "up", LOST_CACHE)
    b.set(2, 0, 1, mix_of(rng, cover))
    if rng.random() < 0.6:
        b.set(2, 1, 1, mix_of(rng, cover))
    b.set(1, 0, 2, mix_of(rng, cover))
    patch(b, rng, cols(b), ground, 0.6)
    return b


# ============================================================== the second wave: ruins
# Bigger hand-made finds, 7-14 blocks across: the corner of a cottage, a chapel gable, a kiln, a press. Each
# region has its own few. They keep further from the structures (a per-group margin) and to gentle ground.
RUBBLE = [("cobblestone", 3), ("mossy_cobblestone", 3), ("stone", 1), ("andesite", 1)]
DRESSED = [("stone_bricks", 4), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1)]


def column(b, rng, x, z, h, mix, y0=0):
    """Masonry from y0 up to h inclusive (nothing if h < y0)."""
    for y in range(y0, h + 1):
        b.set(x, y, z, mix_of(rng, mix))


def ruin_cottage(seed):
    """The corner of a cottage: two rubble walls standing to the eaves where they meet and falling away
    along their length, a window in one, the hearth with its chimney stack tumbled across the floor, a rose
    grown up through the doorway; under a slab in the hearth corner, what the last tenant hid."""
    rng = random.Random(seed)
    b = Build("ruin_cottage", 11, 7, 9, seed=seed)
    north = {x: h for x, h in zip(range(1, 10), (4, 4, 4, 3, 3, 4, 4, 2, 1))}   # wall heights along the north
    west = {z: h for z, h in zip(range(1, 8), (4, 4, 3, 2, 0, 1, 1))}           # and the west (door at z=5)
    for x, h in north.items():
        column(b, rng, x, 1, h, DRESSED if x == 1 else RUBBLE)
    for z, h in west.items():
        if z == 5:
            b.set(1, 0, z, B("stone_brick_slab", type="top"))   # the doorstep
            continue
        column(b, rng, 1, z, h, DRESSED if z == 1 else RUBBLE)
    for (x, y) in [(3, 2), (3, 3), (4, 2), (4, 3)]:             # the window
        b.set(x, y, 1, AIR)
    b.set(3, 4, 1, B("oak_log", axis="x"))                      # its lintel
    b.set(4, 4, 1, B("oak_log", axis="x"))
    for (x, z) in [(2, 1), (5, 1), (1, 2), (1, 3)]:              # the wall plate where the roof sat
        if b.get(x, 4, z) is not None and rng.random() < 0.7:
            put(b, x, 5, z, B("cobblestone_slab", type="bottom"))
    for x in (6, 7):                                            # the hearth and the foot of its stack
        for y in range(0, 4):
            b.set(x, y, 2, B("bricks") if y >= 1 else B("stone_bricks"))
    b.set(6, 1, 2, AIR)
    b.set(6, 2, 2, AIR)
    b.campfire(6, 1, 2, lit=False, facing="south")
    b.set(6, 0, 2, B("stone_bricks"))
    b.set(7, 4, 2, B("bricks"))
    for z, y in ((3, 1), (4, 1), (5, 1), (6, 0), (7, 1)):        # the stack, fallen across the floor
        b.set(7, y, z, B("bricks") if rng.random() < 0.75 else B("brick_slab", type="bottom"))
        if y == 0:
            put(b, 7, 1, z, B("brick_slab", type="bottom"))
    b.chest(5, 0, 2, "east", RUIN_CACHE)                        # hidden under a slab by the hearth
    b.set(5, 1, 2, B("stone_brick_slab", type="bottom"))
    for x in range(2, 9):                                       # the floor: flags and nettles
        for z in range(2, 8):
            if (x, z) in b.cells or b.get(x, 0, z) is not None:
                continue
            r = rng.random()
            if r < 0.3:
                b.set(x, 0, z, B(rng.choice(["stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks"])))
            elif r < 0.45:
                b.set(x, 0, z, B("coarse_dirt"))
    for x in range(2, 6):                                       # a roof beam down across the floor
        put(b, x, 1, 5, B("stripped_oak_log", axis="x"))
    plant(b, 2, 4, ("tall", "rose_bush"), "grass_block")
    plant(b, 0, 5, ("tall", "rose_bush"), "grass_block")
    plant(b, 8, 6, ("tall", "lilac"), "grass_block")
    strew(b, rng, free(b, cols(b)), ["short_grass", "short_grass", "fern", ("tall", "tall_grass")], "grass_block", 0.35)
    return b


def ruin_chapel(seed):
    """A chapel's west gable still standing, with its bell-cote empty against the sky; the bell lies in the
    grass where it fell, the side walls are down to their footings, two ledger stones in the nave floor."""
    rng = random.Random(seed)
    b = Build("ruin_chapel", 9, 11, 13, seed=seed)
    gable = {1: 3, 2: 4, 3: 5, 4: 6, 5: 5, 6: 4, 7: 3}
    for x, h in gable.items():                                   # the gable wall, z = 1
        column(b, rng, x, 1, h, DRESSED)
    for x, h in gable.items():                                   # its coping, stepping up to the apex
        if x < 4:
            b.set(x, h + 1, 1, B("stone_brick_stairs", facing="east"))
        elif x > 4:
            b.set(x, h + 1, 1, B("stone_brick_stairs", facing="west"))
    for x in (3, 5):                                             # the bell-cote over the apex
        b.set(x, 7, 1, B("stone_brick_wall"))
        b.set(x, 8, 1, B("stone_brick_wall"))
    b.set(4, 7, 1, B("stone_bricks"))
    for x in (3, 4, 5):
        b.set(x, 9, 1, B("stone_brick_slab", type="bottom"))
    b.set(4, 8, 1, AIR)
    for y in (1, 2):                                             # the door, with a pointed head
        b.set(4, y, 1, AIR)
    b.set(4, 3, 1, AIR)
    b.set(3, 3, 1, B("stone_brick_stairs", facing="east", half="top"))
    b.set(5, 3, 1, B("stone_brick_stairs", facing="west", half="top"))
    b.set(4, 0, 1, B("stone_brick_slab", type="top"))
    b.set(2, 3, 1, AIR)                                          # two lancet windows
    b.set(6, 3, 1, AIR)
    for z in range(2, 12):                                       # side walls down to their footings
        for x in (1, 7):
            h = rng.choice([0, 0, 1, 1, 2]) if z < 6 else rng.choice([0, 0, 1])
            if z == 11 or rng.random() < 0.9:
                column(b, rng, x, z, h, RUBBLE)
    for x in range(2, 7):                                        # the east wall, a line of stones
        if rng.random() < 0.8:
            b.set(x, 0, 11, mix_of(rng, RUBBLE))
    for (x, z) in [(3, 5), (5, 8)]:                              # ledger stones
        b.set(x, 0, z, B("smooth_stone"))
        b.set(x, 0, z + 1, B("smooth_stone"))
    b.bell(5, 1, 4, "floor", "east")                             # the fallen bell
    b.set(5, 0, 4, B("coarse_dirt"))
    for (x, z) in [(2, 6), (3, 7), (6, 3), (2, 9)]:              # fallen stones in the nave
        put(b, x, 1, z, B(rng.choice(["stone_bricks", "mossy_stone_bricks", "cobblestone"])))
    strew(b, rng, free(b, cols(b)), ["short_grass", "short_grass", ("tall", "tall_grass"), "fern"], "grass_block", 0.3)
    return b


def ruin_lime_kiln(seed):
    """A collapsed lime kiln: a squat round tower of stone with its draw arch still open at the foot, the
    top fallen in, burnt lime showing white in the rubble that spilled out of it."""
    rng = random.Random(seed)
    b = Build("ruin_lime_kiln", 9, 6, 9, seed=seed)
    c = 4
    for (x, z) in disc(c, c, 3.2, 9, 9, 1.6):
        a = math.atan2(z - c, x - c)
        h = 3 + int(1.5 * math.cos(a + 0.6)) + rng.choice([0, 0, -1])
        column(b, rng, x, z, max(1, h), RUBBLE)
    for (x, z) in disc(c, c, 1.6, 9, 9):                       # the pot, fallen in: lime and rubble
        b.set(x, 0, z, B(rng.choice(["calcite", "gravel", "calcite", "cobblestone"])))
        if rng.random() < 0.5:
            b.set(x, 1, z, B(rng.choice(["calcite", "gravel"])))
    for y in (1, 2):                                             # the draw arch, south
        for z in (6, 7):
            b.set(c, y, z, AIR)
    b.set(c, 3, 7, B("stone_bricks"))
    b.set(c, 0, 7, B("calcite"))
    b.set(c, 0, 8, B("calcite"))
    for (x, z) in [(6, 8), (2, 8), (8, 5), (1, 2)]:              # what spilled
        put(b, x, 0, z, B(rng.choice(["calcite", "cobblestone", "gravel"])))
    strew(b, rng, free(b, cols(b)), ["short_grass", "fern"], "grass_block", 0.3)
    return b


def ruin_watchpost(seed):
    """A timber watch-post brought down in a storm: four legs snapped off at the stumps, the trestle lying
    out across the moss with its cross-braces, the lookout's platform on its side at the far end and the
    signal brazier spilled beside it."""
    rng = random.Random(seed)
    b = Build("ruin_watchpost", 14, 5, 7, seed=seed)
    for (x, z, h) in [(1, 2, 2), (3, 2, 1), (1, 4, 1), (3, 4, 2)]:   # the stumps, jagged
        column(b, rng, x, z, h, [B("spruce_log", axis="y")])
        b.set(x, h, z, B("stripped_spruce_log", axis="y"))
    for x in range(4, 11):                                       # the legs, lying
        for z in (2, 4):
            if (x, z) != (7, 4):
                b.set(x, 1, z, B("spruce_log", axis="x"))
        if x % 2 == 1:
            b.set(x, 1, 3, B("spruce_fence"))                     # braces between them
    for y in range(1, 4):                                        # the platform, on edge
        for z in range(1, 6):
            b.set(11, y, z, B("spruce_planks") if rng.random() < 0.85 else B("spruce_trapdoor", facing="east",
                                                                             half="bottom", open=True))
    for z in (1, 5):                                             # its rail posts, sticking out sideways
        b.set(12, 2, z, B("spruce_fence"))
        b.set(13, 2, z, B("spruce_fence"))
    b.campfire(12, 1, 3, lit=False, facing="east")              # the brazier, spilled
    b.set(13, 0, 3, B("coal_block"))                            # its coals
    for x in range(5, 10):                                       # the ladder, lying in the grass
        put(b, x, 1, 6, B("spruce_trapdoor", facing="north", half="bottom"))
    patch(b, rng, cols(b), ["podzol", "coarse_dirt", "moss_block"], 0.25)
    strew(b, rng, free(b, cols(b)), ["fern", "short_grass", ("tall", "large_fern")], "podzol", 0.3)
    return b


def ruin_statue(seed, name="ruin_statue"):
    """A statue swallowed by moss in a forgotten garden: a robed figure on a stepped plinth, one arm raised
    and the hand broken off, vines to its waist, the urn that stood beside it lying broken in the grass."""
    rng = random.Random(seed)
    b = Build(name, 9, 8, 9, seed=seed)
    c = 4
    for x in range(c - 2, c + 3):                               # the plinth: a step all round, then the block
        for z in range(c - 2, c + 3):
            b.set(x, 0, z, mix_of(rng, DRESSED))
            if abs(x - c) == 2 or abs(z - c) == 2:
                d = "south" if z == c - 2 else "north" if z == c + 2 else "east" if x == c - 2 else "west"
                if abs(x - c) == 2 and abs(z - c) == 2:
                    b.set(x, 1, z, B("stone_brick_slab", type="bottom"))
                else:
                    b.set(x, 1, z, B("stone_brick_stairs", facing=OPPOSITE[d]))
            else:
                b.set(x, 1, z, mix_of(rng, DRESSED))
    b.set(c, 2, c, B("polished_andesite"))                       # the figure: robe, body, head
    b.set(c, 3, c, B("polished_andesite"))
    b.set(c, 4, c, B("andesite"))
    b.set(c, 5, c, B("chiseled_stone_bricks"))
    b.set(c + 1, 2, c, B("andesite_stairs", facing="west"))       # the robe's hem
    b.set(c - 1, 2, c, B("andesite_stairs", facing="east"))
    b.set(c - 1, 4, c, B("andesite_wall"))                       # the raised arm, broken off at the wrist
    b.set(c - 1, 5, c, B("andesite_wall"))
    b.set(c + 1, 4, c, B("andesite_stairs", facing="west", half="top"))   # the other, at its side
    for y in (2, 3):                                             # vines to its waist
        put(b, c, y, c + 1, B("vine", north=True))
        put(b, c, y, c - 1, B("vine", south=True))
    put(b, c, 6, c, B("moss_carpet"))
    b.set(7, 1, 7, B("decorated_pot", cracked=True, facing="west"), {"components": {}, "sherds": {
        "back": {"id": "minecraft:brick"}, "front": {"id": "minecraft:brick"},
        "left": {"id": "minecraft:brick"}, "right": {"id": "minecraft:brick"}}, "id": "minecraft:decorated_pot"})
    b.set(7, 0, 7, B("stone_bricks"))
    b.set(8, 1, 6, B("stone_brick_slab", type="bottom"))
    strew(b, rng, free(b, cols(b)), ["short_grass", "short_grass", "fern", ("tall", "peony")], "grass_block", 0.4)
    return b


def ruin_garden_gate(seed):
    """An overgrown garden wall and its gateway: brick piers with stone caps and urns, an iron gate hanging
    open off one of them, the wall running away both sides and broken, roses over it, the walk through the
    gate gone to grass."""
    rng = random.Random(seed)
    b = Build("ruin_garden_gate", 14, 6, 7, seed=seed)
    z = 2
    for x in range(14):
        if 5 <= x <= 7:
            continue
        h = 2 if 3 <= x <= 10 else rng.choice([0, 1, 1, 2])
        if rng.random() < 0.12:
            h = 0
        column(b, rng, x, z, h, [("bricks", 5), ("mossy_stone_bricks", 1), ("cracked_stone_bricks", 1)])
        if h == 2:
            b.set(x, 3, z, B("stone_brick_slab", type="bottom"))
    for x in (5, 7):                                             # the piers
        column(b, rng, x, z, 3, [B("bricks")])
        b.set(x, 4, z, B("stone_bricks"))
        b.set(x, 5, z, B("decorated_pot", facing="south"), {"components": {}, "sherds": {
            "back": {"id": "minecraft:brick"}, "front": {"id": "minecraft:brick"},
            "left": {"id": "minecraft:brick"}, "right": {"id": "minecraft:brick"}}, "id": "minecraft:decorated_pot"})
    b.set(5, 5, z, AIR)                                          # one urn fallen
    b.set(4, 1, 4, B("decorated_pot", cracked=True, facing="east"), {"components": {}, "sherds": {
        "back": {"id": "minecraft:brick"}, "front": {"id": "minecraft:brick"},
        "left": {"id": "minecraft:brick"}, "right": {"id": "minecraft:brick"}}, "id": "minecraft:decorated_pot"})
    for y in (1, 2):                                             # the gate, swung open
        b.set(7, y, 3, B("iron_bars"))
        b.set(7, y, 4, B("iron_bars"))
    b.set(6, 0, z, B("stone_brick_slab", type="top"))            # the threshold
    for zz in range(0, 7):                                       # the walk, gone to grass
        if zz != z and rng.random() < 0.6:
            b.set(6, 0, zz, B(rng.choice(["gravel", "coarse_dirt", "mossy_cobblestone"])))
    for x in (2, 9, 11):                                         # roses over the wall
        plant(b, x, 3, ("tall", "rose_bush"), "grass_block")
    for x in range(14):
        if b.get(x, 2, z) is not None and rng.random() < 0.35:
            put(b, x, 2, z + 1, B("vine", north=True))
            put(b, x, 1, z + 1, B("vine", north=True))
    strew(b, rng, free(b, cols(b)), ["short_grass", "short_grass", "poppy", "oxeye_daisy", ("tall", "peony")],
          "grass_block", 0.4)
    return b


def ruin_farmstead(seed):
    """The foundations of a farmstead: the outline of the house and its barn in footing stones, a worn
    doorstep, a hearthstone, the stub of the well with its winding post fallen in, a lone gatepost."""
    rng = random.Random(seed)
    b = Build("ruin_farmstead", 14, 4, 12, seed=seed)

    def outline(x0, z0, x1, z1, door):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    if (x, z) == door:
                        b.set(x, 0, z, B("stone_slab", type="top"))
                        continue
                    if rng.random() < 0.88:
                        b.set(x, 0, z, mix_of(rng, RUBBLE))
                        if rng.random() < 0.3:
                            b.set(x, 1, z, mix_of(rng, RUBBLE))
    outline(0, 0, 6, 5, (3, 5))                                  # the house
    outline(8, 0, 13, 6, (8, 3))                                 # the barn
    b.set(4, 0, 2, B("smooth_stone"))                             # the hearthstone
    b.campfire(4, 1, 2, lit=False, facing="south")
    for (x, z) in [(1, 1), (1, 2)]:                               # the corner still standing
        column(b, rng, x - 1, z - 1, 2, RUBBLE)
    for (x, z) in disc(4, 9, 1.5, 14, 12):                       # the well
        if (x, z) == (4, 9):
            b.set(x, 0, z, B("water", level=0))
        else:
            b.set(x, 0, z, mix_of(rng, DRESSED))
            if rng.random() < 0.7:
                b.set(x, 1, z, mix_of(rng, DRESSED))
    b.set(6, 1, 9, B("spruce_fence"))                             # the winding post, and its fallen beam
    b.set(6, 2, 9, B("spruce_fence"))
    for x in (6, 7, 8):
        put(b, x, 1, 10, B("spruce_log", axis="x"))
    b.set(12, 1, 10, B("spruce_log", axis="y"))                   # the gatepost
    b.set(12, 2, 10, B("spruce_log", axis="y"))
    b.set(12, 0, 10, B("cobblestone"))
    patch(b, rng, [(x, z) for x in range(1, 6) for z in range(1, 5)], ["coarse_dirt", "dirt", "stone_bricks"], 0.3)
    strew(b, rng, free(b, cols(b)), ["short_grass", "short_grass", ("tall", "tall_grass"), "dandelion"], "grass_block",
          0.35)
    return b


def ruin_olive_press(seed):
    """An olive press in ruins: the round stone basin with its millstone standing on edge in it, the beam of
    the screw press fallen across, oil jars broken among the lavender, the press-house wall standing behind
    with its doorway; one jar still sealed."""
    rng = random.Random(seed)
    b = Build("ruin_olive_press", 11, 6, 10, seed=seed)
    c = (4, 4)
    for (x, z) in disc(*c, 2.3, 11, 10):                         # the basin
        b.set(x, 0, z, B("smooth_stone"))
        if math.hypot(x - c[0], z - c[1]) > 1.5:
            b.set(x, 1, z, B("smooth_stone_slab", type="bottom") if rng.random() < 0.8 else B("smooth_stone"))
    for (y, z) in [(1, 3), (1, 4), (1, 5), (2, 3), (2, 4), (2, 5), (3, 4)]:   # the millstone, on edge
        b.set(c[0], y, z, B("polished_andesite") if (y, z) != (2, 4) else B("andesite"))
    b.set(c[0], 3, 3, B("polished_andesite_slab", type="bottom"))
    b.set(c[0], 3, 5, B("polished_andesite_slab", type="bottom"))
    for x in range(1, 9):                                        # the press beam, fallen across
        put(b, x, 1, 7, B("expanse:stripped_olive_log", axis="x"))
    for x in range(0, 11):                                       # the press-house wall behind
        h = 3 if 2 <= x <= 8 else rng.choice([1, 2])
        if x in (5,):
            b.set(x, 0, 9, B("expanse:limestone_slab", type="top"))
            b.set(x, 3, 9, B("expanse:olive_log", axis="x"))
            continue
        column(b, rng, x, 9, h, [("expanse:limestone", 3), ("expanse:limestone_bricks", 2), ("cobblestone", 1)])
    b.set(4, 3, 9, B("expanse:olive_log", axis="x"))
    b.set(6, 3, 9, B("expanse:olive_log", axis="x"))
    b.set(5, 3, 9, B("expanse:olive_log", axis="x"))
    jars = [(8, 3), (9, 4), (8, 5), (1, 2)]
    for i, (x, z) in enumerate(jars):
        n = {"components": {}, "sherds": {s: {"id": "minecraft:brick"} for s in ("back", "front", "left", "right")},
             "id": "minecraft:decorated_pot"}
        if i == 1:
            n["LootTable"] = AMPHORA
        b.set(x, 1, z, B("decorated_pot", cracked=(i in (0, 3)), facing=rng.choice(H4)), n)
    patch(b, rng, cols(b), ["expanse:red_earth", "coarse_dirt"], 0.35)
    strew(b, rng, free(b, cols(b)), ["expanse:lavender", "short_grass", "short_grass"], "expanse:red_earth", 0.35)
    return b


def ruin_adobe(seed):
    """An adobe ruin melting back into the desert: two mud-brick walls, their tops worn round, the roof
    beams still poking out of one, a doorway with its log lintel, a beehive oven against the wall and an
    old fallen viga in the sand."""
    rng = random.Random(seed)
    b = Build("ruin_adobe", 12, 6, 9, seed=seed)
    mud = [("packed_mud", 3), ("mud_bricks", 2), ("terracotta", 1)]
    north = (3, 4, 4, 4, 3, 3, 4, 4, 3, 2, 1)
    for x, h in zip(range(0, 11), north):
        column(b, rng, x, 1, h, mud)
        if h >= 3 and rng.random() < 0.5:
            b.set(x, h + 1, 1, B("mud_brick_slab", type="bottom"))
    for x in (2, 4, 6, 8):                                       # vigas through the wall
        if north[x] >= 4:
            b.set(x, 4, 2, B("dark_oak_log", axis="z"))
            b.set(x, 4, 1, B("dark_oak_log", axis="z"))
    for z, h in zip(range(2, 8), (3, 3, 0, 0, 2, 1)):            # the west wall, with its door
        if h:
            column(b, rng, 0, z, h, mud)
    b.set(0, 3, 4, B("dark_oak_log", axis="z"))                   # the lintel over the door
    b.set(0, 3, 5, B("dark_oak_log", axis="z"))
    for z in (4, 5):
        column(b, rng, 0, z, 0, mud)
    b.set(0, 4, 4, B("mud_brick_slab", type="bottom"))
    for (x, z) in disc(7, 4, 1.2, 12, 9):                         # the horno
        b.set(x, 0, z, B("packed_mud"))
        b.set(x, 1, z, B("packed_mud"))
    b.set(7, 2, 4, B("packed_mud"))
    b.set(7, 1, 5, AIR)
    b.set(7, 1, 4, AIR)
    b.set(7, 1, 4, B("barrel", facing="south"), {"LootTable": RUIN_CACHE, "components": {}, "id": "minecraft:barrel"})
    for x in range(3, 8):                                        # a fallen viga
        put(b, x, 1, 7, B("dark_oak_log", axis="x"))
    patch(b, rng, cols(b), ["coarse_dirt", "red_sand", "packed_mud"], 0.3)
    strew(b, rng, free(b, cols(b)), ["dead_bush", "short_dry_grass", "short_dry_grass"], "coarse_dirt", 0.25)
    return b


def ruin_stair(seed, name="ruin_stair", mix=DRESSED, stair="stone_brick", soil=("moss_block", "grass_block")):
    """A flight of stone steps going up out of the grass to nothing: treads worn hollow, the side walls
    broken, the landing at the top with half a doorway still framing the sky."""
    rng = random.Random(seed)
    b = Build(name, 7, 9, 9, seed=seed)
    for z in range(1, 5):                                        # the flight, z 1..4 rising to y 4
        top = z
        for x in range(2, 5):
            column(b, rng, x, z, top - 1, mix)
            b.set(x, top, z, B(f"{stair}_stairs", facing="south") if rng.random() < 0.85 else
                  B(f"{stair}_slab", type="bottom"))
        for x in (1, 5):                                         # side walls, broken
            h = top + 1 if rng.random() < 0.6 else max(0, top - 2)
            column(b, rng, x, z, h, mix)
    for z in (5, 6, 7):                                          # the landing
        for x in range(1, 6):
            column(b, rng, x, z, 4 if (x, z) != (5, 7) else 2, mix)
    for y in (5, 6, 7):                                          # half a doorway: one jamb and its lintel stub
        b.set(1, y, 6, mix_of(rng, mix))
    b.set(2, 7, 6, mix_of(rng, mix))
    b.set(5, 5, 6, mix_of(rng, mix))
    for (x, z) in [(0, 2), (6, 4), (0, 6), (6, 8), (3, 0), (4, 8)]:   # stones that came off it
        put(b, x, 0, z, mix_of(rng, mix))
    for (x, y, z), (st, _) in list(b.cells.items()):            # moss in the joints
        if not st.short.endswith(("_stairs", "_slab")) and b.get(x, y + 1, z) is None and y >= 2 and \
                rng.random() < 0.3:
            b.set(x, y + 1, z, B("moss_carpet"))
    strew(b, rng, free(b, cols(b)), ["fern", "short_grass"], list(soil), 0.35)
    return b


def ruin_stone_face(seed):
    """A great stone face sunk in the forest floor: a head of mossy blocks with dark hollows for eyes and
    mouth, a root over its brow and vines down its cheeks; something glints in its mouth."""
    rng = random.Random(seed)
    b = Build("ruin_stone_face", 9, 8, 6, seed=seed)
    face = [("mossy_stone_bricks", 3), ("mossy_cobblestone", 2), ("stone_bricks", 1), ("cracked_stone_bricks", 1)]
    for x in range(1, 8):
        for y in range(0, 6):
            if y == 5 and x in (1, 7):
                continue
            for z in (2, 3, 4):
                if z == 4 and y >= 4 and x in (1, 7):
                    continue
                b.set(x, y, z, mix_of(rng, face))
    for (x, y) in [(2, 3), (3, 3), (5, 3), (6, 3)]:              # the eyes, sunk deep
        b.set(x, y, 2, AIR)
    for x in (3, 4, 5):                                          # the mouth
        b.set(x, 1, 2, AIR)
    b.chest(4, 1, 3, "north", RUIN_CACHE)                        # what it holds in its mouth
    b.set(4, 2, 3, B("mossy_stone_brick_slab", type="top"))      # (room over the lid to open it)
    b.set(4, 2, 1, B("mossy_stone_brick_stairs", facing="south"))    # the nose
    b.set(4, 3, 1, B("mossy_stone_brick_slab", type="top"))
    for x in range(1, 8):                                        # the brow
        if rng.random() < 0.8:
            b.set(x, 4, 1, B("mossy_stone_brick_slab", type="top"))
    for x in range(0, 9):                                        # a root arching over its head
        y = 6 if 2 <= x <= 6 else 5 if x in (1, 7) else 0
        if x in (0, 8):
            b.set(x, 0, 3, B("jungle_wood", axis="y"))
            for yy in (1, 2, 3, 4):
                b.set(x, yy, 3, B("jungle_log", axis="y"))
        else:
            b.set(x, y, 3, B("jungle_log", axis="x"))
    for (x, y) in [(1, 4), (7, 4), (1, 3), (7, 2), (2, 1)]:      # vines down its cheeks
        put(b, x, y, 1, B("vine", south=True))
    for x in range(1, 8):
        if b.get(x, 6, 3) is None and rng.random() < 0.6:
            if b.get(x, 5, 2) is not None:
                put(b, x, 6, 2, B("moss_carpet"))
    patch(b, rng, cols(b), ["podzol", "moss_block", "coarse_dirt"], 0.3)
    strew(b, rng, free(b, cols(b)), ["fern", "fern", ("tall", "large_fern")], "podzol", 0.4)
    return b


def ruin_brick_shrine(seed):
    """A brick shrine split by a fig: a square plinth of old red brick, a stepped tower broken off halfway,
    the fig's roots running down over it to the ground, little niches with candles burnt out."""
    rng = random.Random(seed)
    b = Build("ruin_brick_shrine", 9, 8, 9, seed=seed)
    br = [("bricks", 5), ("mossy_stone_bricks", 1), ("packed_mud", 1)]
    for x in range(1, 8):
        for z in range(1, 8):
            b.set(x, 0, z, mix_of(rng, br))
            if 1 < x < 7 and 1 < z < 7 or rng.random() < 0.6:
                b.set(x, 1, z, mix_of(rng, br))
    for k, (lo, hi) in enumerate([(2, 6), (3, 5)]):              # the stepped tower
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                for y in (2 + 2 * k, 3 + 2 * k):
                    if rng.random() < (0.95 if k == 0 else 0.7):
                        b.set(x, y, z, mix_of(rng, br))
    b.set(4, 2, 2, AIR)                                          # a niche with a candle
    b.set(4, 2, 2, B("candle", candles=2, lit=False))
    b.set(2, 2, 4, B("candle", candles=1, lit=False))
    b.set(4, 6, 4, B("bricks"))
    b.set(4, 1, 0, B("brick_stairs", facing="north"))             # a step up onto the plinth
    b.set(4, 0, 0, B("bricks"))
    root = B("jungle_wood", axis="y")                           # the fig: a trunk on the corner, roots over all
    for y in range(2, 7):
        b.set(6, y, 6, B("jungle_log", axis="y"))
    for (x, y, z) in [(7, 1, 6), (8, 0, 6), (6, 1, 7), (6, 0, 8), (5, 2, 6), (4, 3, 6), (3, 3, 6), (2, 2, 6), (1, 1, 6),
                      (0, 0, 6), (6, 2, 5), (6, 3, 4), (6, 3, 3), (6, 2, 2), (6, 1, 1), (6, 0, 0)]:
        b.set(x, y, z, root)
    for (x, y, z) in [(5, 7, 6), (6, 7, 6), (6, 7, 5), (7, 7, 6), (6, 7, 7)]:
        b.set(x, y, z, B("jungle_leaves", persistent=True))
    for (x, y, z), (st, _) in list(b.cells.items()):
        if st.short == "bricks" and b.get(x, y + 1, z) is None and rng.random() < 0.2:
            b.set(x, y + 1, z, B("moss_carpet"))
    strew(b, rng, free(b, cols(b)), ["fern", "short_grass"], "podzol", 0.3)
    return b


def ruin_boathouse(seed):
    """A boathouse fallen in: its stone end wall still up with the wide door it was launched through, the
    turf roof down in a heap, the rotted ribs of the boat inside, an oar rack on the wall."""
    rng = random.Random(seed)
    b = Build("ruin_boathouse", 9, 7, 11, seed=seed)
    gable = {0: 2, 1: 3, 2: 4, 3: 5, 4: 5, 5: 4, 6: 3, 7: 2, 8: 1}
    for x, h in gable.items():                                   # the end wall, z = 1
        column(b, rng, x, 1, h, RUBBLE)
    for x in range(2, 6):                                        # the big door
        for y in (1, 2, 3):
            b.set(x, y, 1, AIR)
    for x in range(2, 6):
        b.set(x, 4, 1, B("spruce_log", axis="x"))
    for z in range(2, 10):                                       # the side walls, low
        for x in (0, 8):
            if rng.random() < 0.8:
                column(b, rng, x, z, rng.choice([0, 1, 1, 2]), RUBBLE)
    for z in range(3, 9):                                        # the boat's keel and ribs
        b.set(4, 0, z, B("spruce_log", axis="z"))
        if z % 2 == 1:
            for x in (2, 6):
                b.set(x, 1, z, B("spruce_fence"))
                if z in (5, 7):
                    b.set(x, 2, z, B("spruce_fence"))
            b.set(3, 1, z, B("spruce_slab", type="bottom"))
            b.set(5, 1, z, B("spruce_slab", type="bottom"))
    b.set(4, 1, 9, B("spruce_log", axis="y"))                     # the stem post
    b.set(4, 2, 9, B("spruce_log", axis="y"))
    for (x, z) in [(6, 9), (7, 8), (7, 7), (1, 9), (2, 9), (1, 8)]:   # the turf roof, fallen
        b.set(x, 0, z, B("dirt"))
        b.set(x, 1, z, B(rng.choice(["grass_block", "dirt", "coarse_dirt"])))
    b.set(7, 2, 2, B("spruce_trapdoor", facing="south", half="bottom", open=True))   # an oar rack on the wall
    patch(b, rng, cols(b), ["gravel", "coarse_dirt"], 0.3)
    strew(b, rng, free(b, cols(b)), ["short_grass", "fern"], "grass_block", 0.3)
    return b


def ruin_chimney(seed, name="ruin_chimney", soil="grass_block", plants=("short_grass",)):
    """All that stands of a burnt homestead: the stone chimney alone, its fireplace open to the sky, the
    hearthstone in front, sheets of rusted iron roofing lying in the grass round the footings."""
    rng = random.Random(seed)
    b = Build(name, 9, 8, 8, seed=seed)
    for x in (1, 2, 3):                                          # the chimney: a stack 3 wide narrowing to 2
        for z in (1, 2):
            column(b, rng, x, z, 4 if x != 2 or z != 2 else 0, RUBBLE)
    for x in (1, 2):
        for z in (1, 2):
            column(b, rng, x, z, 7, RUBBLE, y0=5)
    b.set(2, 1, 2, AIR)                                          # the fireplace
    b.set(2, 2, 2, AIR)
    b.set(2, 3, 2, B("stone_slab", type="top"))
    b.set(2, 0, 2, B("cobblestone"))
    b.set(2, 0, 3, B("smooth_stone"))                             # the hearthstone
    b.set(2, 1, 2, B("cauldron"))
    for (x, z) in [(1, 6), (5, 6), (8, 1), (8, 5)]:               # the footings of the house
        b.set(x, 0, z, mix_of(rng, RUBBLE))
    for x in range(1, 9):
        if rng.random() < 0.5:
            put(b, x, 0, 6, mix_of(rng, RUBBLE))
    for z in range(1, 7):
        if rng.random() < 0.5:
            put(b, 8, 0, z, mix_of(rng, RUBBLE))
    for (x, z) in [(4, 3), (5, 3), (5, 4), (6, 2), (3, 5), (6, 5), (7, 4)]:   # rusted iron roofing
        put(b, x, 1, z, B(rng.choice(["exposed_cut_copper_slab", "weathered_cut_copper_slab",
                                      "exposed_cut_copper_slab"]), type="bottom"))
    strew(b, rng, free(b, cols(b)), list(plants), soil, 0.3)
    return b


def ruin_salt_works(seed):
    """Salt works abandoned on the flat: shallow pans walled with salt blocks, one still holding brine, the
    boards of the drying shed fallen flat, a length of rail and a tipped tub."""
    rng = random.Random(seed)
    b = Build("ruin_salt_works", 13, 3, 11, seed=seed)
    for px in (0, 4, 8):
        for pz in (0, 4):
            for x in range(px, px + 5):
                for z in range(pz, pz + 5):
                    if not b.inb(x, 0, z):
                        continue
                    if x in (px, px + 4) or z in (pz, pz + 4):
                        if rng.random() < 0.85:
                            b.set(x, 1, z, B("expanse:salt_block"))
                            b.set(x, 0, z, B("expanse:salt_block"))
                    elif (px, pz) == (4, 0):
                        b.set(x, 0, z, B("water", level=0))
    for x in range(1, 7):                                        # the shed, fallen flat
        for z in (9, 10):
            if rng.random() < 0.8:
                b.set(x, 1, z, B("spruce_slab", type="bottom"))
    b.set(0, 1, 9, B("spruce_log", axis="y"))
    b.set(7, 1, 10, B("spruce_log", axis="y"))
    b.set(7, 2, 10, B("spruce_fence"))
    for x in range(8, 13):                                       # the rail
        if rng.random() < 0.8:
            b.set(x, 1, 9, B("rail", shape="east_west"))
            b.set(x, 0, 9, B("expanse:salt_block"))
    b.set(10, 1, 10, B("barrel", facing="north"))
    return b


def ruin_kurgan(seed):
    """A kurgan: a grassed burial mound on the steppe, dug open by robbers long ago so that its stone-lined
    passage shows, a balbal standing at its foot; the robbers missed one chest at the end of the passage."""
    rng = random.Random(seed)
    b = Build("ruin_kurgan", 13, 6, 13, seed=seed)
    c = 6
    for (x, z) in disc(c, c, 5.8, 13, 13):
        d = math.hypot(x - c, z - c)
        h = int(round(3.6 * math.cos(min(1.0, d / 6.0) * math.pi / 2)))
        for y in range(0, h + 1):
            b.set(x, y, z, B("dirt") if y < h else B(rng.choice(["grass_block", "grass_block", "coarse_dirt"])))
    for z in range(c, 13):                                       # the robbers' cut, from the south
        for x in (c - 1, c, c + 1):
            for y in range(1, 5):
                if (x, y, z) in b.cells:
                    del b.cells[(x, y, z)]
            if x == c:
                b.set(x, 0, z, B(rng.choice(["coarse_dirt", "gravel", "cobblestone"])))
        for x in (c - 2, c + 2):                                 # the passage's stone lining
            for y in (1, 2):
                if (x, y, z) in b.cells and z < 11:
                    b.set(x, y, z, mix_of(rng, [("cobblestone", 2), ("mossy_cobblestone", 1), ("andesite", 1)]))
    for x in (c - 1, c, c + 1):                                  # the chamber's lintel stones
        b.set(x, 3, c + 1, B("andesite"))
    b.chest(c, 1, c, "south", RUIN_CACHE)
    b.set(c - 1, 1, c, B("bone_block", axis="z"))
    b.set(c + 1, 1, c, B("skeleton_skull", rotation=rng.choice([4, 6, 10])))
    b.set(c, 0, 12, B("andesite"))                                # the balbal at its foot
    b.set(c + 3, 0, 12, B("stone"))
    for y in (1, 2):
        b.set(c + 3, y, 12, B("andesite"))
    b.set(c + 3, 3, 12, B("chiseled_stone_bricks"))
    strew(b, rng, free(b, cols(b), y=1), ["short_grass", "short_grass"], "grass_block", 0.25)
    for (x, y, z), (st, _) in list(b.cells.items()):            # flowers on the mound
        if st.short == "grass_block" and b.get(x, y + 1, z) is None and rng.random() < 0.25:
            b.set(x, y + 1, z, B("short_grass"))
    return b


def ruin_ash_house(seed):
    """A house buried by the ash fall: only its roof stands out of the grey drift, ridge and chimney top,
    one gable with its little window showing; under the roof there is still room to crawl, and something
    was left there."""
    rng = random.Random(seed)
    b = Build("ruin_ash_house", 10, 5, 9, seed=seed)
    for x in range(1, 9):                                        # the roof: a ridge along x at y 2
        b.set(x, 0, 2, B("spruce_stairs", facing="south"))
        b.set(x, 1, 3, B("spruce_stairs", facing="south"))
        b.set(x, 2, 4, B("spruce_slab", type="bottom") if x != 5 else B("spruce_planks"))
        b.set(x, 1, 5, B("spruce_stairs", facing="north"))
        b.set(x, 0, 6, B("spruce_stairs", facing="north"))
        b.set(x, 0, 4, AIR)                                       # the loft under it
        if x != 5:
            b.set(x, 1, 4, AIR)
        b.set(x, 0, 3, AIR)
        b.set(x, 0, 5, AIR)
    for (x, y, z) in [(6, 1, 3), (6, 0, 2), (7, 1, 3)]:          # fallen through here
        b.set(x, y, z, AIR)
    b.chest(3, 0, 4, "east", RUIN_CACHE)
    for z in (3, 4, 5):                                          # the west gable, with a window
        b.set(0, 0, z, B("spruce_planks"))
    b.set(0, 1, 4, B("spruce_planks"))
    b.set(0, 0, 4, B("glass_pane"))
    for y in range(0, 4):                                        # the chimney top
        b.set(8, y, 4, B("cobblestone") if y else B("cobblestone"))
    b.set(8, 2, 4, B("cobblestone"))
    b.set(8, 3, 4, B("cobblestone_slab", type="bottom"))
    for (x, z) in cols(b):                                       # the drift, piled against it
        if b.get(x, 0, z) is None and rng.random() < 0.7:
            b.set(x, 0, z, B("expanse:volcanic_ash"))
        if (x, 1, z) not in b.cells and b.get(x, 0, z) is not None and b.get(x, 0, z).short == "volcanic_ash" \
                and z in (1, 7) and rng.random() < 0.5:
            b.set(x, 1, z, B("expanse:volcanic_ash"))
    return b


def ruin_snow_cabin(seed):
    """A trapper's cabin under the snow: log walls a few rounds high, the roof fallen in under the weight of
    a winter, the stove still standing with its pipe, snowshoes on the wall, drifts inside."""
    rng = random.Random(seed)
    b = Build("ruin_snow_cabin", 9, 5, 8, seed=seed)
    for x in range(1, 8):                                        # log walls, notched at the corners
        for z in range(1, 7):
            edge_x, edge_z = x in (1, 7), z in (1, 6)
            if not (edge_x or edge_z):
                continue
            if (x, z) == (4, 6):
                b.set(x, 0, z, B("spruce_slab", type="top"))      # the door
                continue
            h = 2 if not (x > 5 and z > 3) else 1
            for y in range(0, h + 1):
                b.set(x, y, z, B("spruce_log", axis="x" if edge_z and not edge_x else "z" if edge_x and not edge_z
                                  else "y"))
    for x in range(2, 7):                                        # the roof, fallen in
        for z in range(2, 6):
            if rng.random() < 0.55:
                b.set(x, 1, z, B("spruce_slab", type="bottom"))
    b.set(2, 1, 2, B("furnace", facing="south"))                  # the stove and its pipe
    b.set(2, 2, 2, B("iron_bars"))
    b.set(2, 3, 2, B("iron_bars"))
    b.set(6, 2, 2, B("spruce_trapdoor", facing="west", half="bottom", open=True))   # snowshoes on the wall
    for (x, z) in cols(b):
        if b.get(x, 1, z) is None and rng.random() < 0.6:
            b.set(x, 1, z, B("snow", layers=rng.choice([2, 3, 4])))
    snow_on(b, rng, 0.7, (1, 2))
    return b


def ruin_wreck(seed, name="ruin_wreck", wood="spruce", sand="sand"):
    """A wreck on the beach: the ribs of a small ship standing out of the sand, the keel buried, the stern
    post leaning up, the mast lying along the tide line with its rigging still on it."""
    rng = random.Random(seed)
    b = Build(name, 14, 6, 9, seed=seed)
    for x in range(1, 12):                                       # the keel and the bottom planks
        b.set(x, 0, 4, B(wn(wood, "log"), axis="x"))
        for z in (3, 5):
            if rng.random() < 0.7:
                b.set(x, 0, z, B(wn(wood, "planks")))
    for x in range(2, 11, 2):                                    # ribs
        h = 3 if 4 <= x <= 8 else 2
        for z in (2, 6):
            for y in range(1, h + 1 - (1 if rng.random() < 0.3 else 0)):
                b.set(x, y, z, B(wn(wood, "fence")))
        b.set(x, 1, 3, B(wn(wood, "fence")))
        b.set(x, 1, 5, B(wn(wood, "fence")))
    for x in (3, 5, 9):                                          # a few strakes still fixed to them
        b.set(x, 1, 2, B(wn(wood, "planks")))
        if x != 5:
            b.set(x, 1, 6, B(wn(wood, "planks")))
    for y in range(1, 5):                                        # the stern post
        b.set(12, y, 4, B(wn(wood, "log"), axis="y"))
    b.set(12, 0, 4, B(wn(wood, "log"), axis="y"))
    b.set(13, 4, 4, B(wn(wood, "fence")))
    for x in range(1, 13):                                       # the mast, along the tide line
        if x != 6:                                               # broken once
            b.set(x, 1, 8, B(wn(wood, "log"), axis="x"))
    for x in (3, 9):
        b.set(x, 1, 7, B("cobweb"))                               # rigging
    b.set(10, 1, 7, B("iron_chain", axis="z"))
    patch(b, rng, cols(b), [sand], 0.3)
    return b


def ruin_pavilion(seed):
    """A pavilion fallen in on its terrace: red posts, two still standing with their brackets, the jade
    tiled roof slid off in a heap, the stone floor cracked and lifting, a lantern by the steps."""
    rng = random.Random(seed)
    b = Build("ruin_pavilion", 11, 6, 11, seed=seed)
    LB, TILE, COL = "expanse:limestone_brick", "waxed_weathered_cut_copper", "expanse:stripped_redwood_log"
    for x in range(1, 10):                                       # the terrace
        for z in range(1, 10):
            b.set(x, 0, z, B(rng.choice(["expanse:limestone_bricks", "expanse:polished_limestone",
                                         "expanse:mossy_limestone", "expanse:limestone_bricks"])))
            if 2 <= x <= 8 and 2 <= z <= 8:
                b.set(x, 1, z, B("expanse:polished_limestone") if rng.random() < 0.85 else
                      B(f"{LB}_slab", type="bottom"))
    for x in (4, 5, 6):                                          # the steps
        b.set(x, 1, 9, B(f"{LB}_stairs", facing="north"))
    for (x, z, h) in [(2, 2, 5), (8, 2, 5), (2, 8, 2), (8, 8, 1)]:
        for y in range(2, h + 1):
            b.set(x, y, z, B(COL, axis="y"))
    for x in range(3, 8):                                         # the lintel between the standing pair
        b.set(x, 5, 2, B("dark_oak_planks"))
    for (x, z) in [(3, 6), (4, 7), (5, 6), (6, 7), (3, 8), (6, 5), (5, 8), (7, 6), (4, 5)]:   # the tiles, slid off
        b.set(x, 2, z, B(f"{TILE}_stairs", facing=rng.choice(H4)) if rng.random() < 0.6 else
              B(f"{TILE}_slab", type="bottom"))
    for (x, z) in [(4, 6), (5, 7), (5, 5)]:
        b.set(x, 2, z, B(TILE))
        put(b, x, 3, z, B(f"{TILE}_slab", type="bottom"))
    b.set(2, 5, 2, B(f"{TILE}_stairs", facing="east"))
    b.set(8, 5, 2, B(f"{TILE}_stairs", facing="west"))
    b.set(8, 0, 10, B("expanse:limestone_bricks"))                # a lantern by the steps
    b.set(8, 1, 10, B(f"{LB}_wall"))
    b.set(8, 2, 10, B("lantern", hanging=False))
    b.set(8, 3, 10, B("expanse:limestone_slab", type="bottom"))
    strew(b, rng, free(b, cols(b)), ["fern", "short_grass"], ["moss_block", "grass_block"], 0.35)
    return b


def ruin_stilt_hut(seed, wood="mangrove"):
    """A stilt hut collapsed into the swamp: its posts leaning, the floor tipped down into the water at one
    end, the thatch in a heap, the ladder hanging off a post."""
    rng = random.Random(seed)
    b = Build("ruin_stilt_hut", 10, 6, 9, seed=seed)
    for (x, z, h) in [(1, 1, 4), (6, 1, 4), (1, 6, 3), (6, 6, 1)]:
        for y in range(0, h + 1):
            b.set(x, y, z, B(wn(wood, "log"), axis="y"))
    for x in range(1, 7):                                        # the floor, tipping down to the south-east
        for z in range(1, 7):
            if (x, z) in ((1, 1), (6, 1), (1, 6), (6, 6)):
                continue
            y = 4 if z <= 2 else 3 if z <= 4 else 2
            if rng.random() < 0.8 or x == 1:
                b.set(x, y, z, B(wn(wood, "slab"), type="bottom"))
    for z in range(2, 7):                                        # the thatch, fallen
        for x in (7, 8):
            if rng.random() < 0.6:
                b.set(x, 1, z, B("hay_block", axis=rng.choice(["x", "z"])))
    pool(b, [(3, 7), (4, 7), (5, 7), (7, 7), (8, 6), (2, 7)], "mud")   # water standing under it
    for y in (1, 2, 3):
        b.set(1, y, 0, B("ladder", facing="north"))
    patch(b, rng, cols(b), ["mud", "mud", "moss_block"], 0.5)
    strew(b, rng, free(b, cols(b)), ["fern", "short_grass"], "mud", 0.25)
    return b


def ruin_villa_floor(seed):
    """The floor of a villa's room coming up through the turf: a mosaic of coloured stone, a key border
    round a rosette, broken column bases at its corners and a wall standing knee high along one side."""
    rng = random.Random(seed)
    b = Build("ruin_villa_floor", 11, 3, 10, seed=seed)
    for x in range(1, 10):
        for z in range(1, 9):
            border = x in (1, 9) or z in (1, 8)
            inner = x in (2, 8) or z in (2, 7)
            d = math.hypot(x - 5, z - 4.5)
            if border:
                st = "black_terracotta" if (x + z) % 2 else "white_terracotta"
            elif inner:
                st = "white_terracotta"
            elif d < 1.2:
                st = "red_terracotta"
            elif d < 2.2:
                st = "orange_terracotta" if (x + z) % 2 else "yellow_terracotta"
            else:
                st = "light_blue_terracotta" if (x * z) % 3 == 0 else "white_terracotta"
            if rng.random() < 0.82:                               # where the turf has crept back
                b.set(x, 0, z, B(st))
    for (x, z) in [(1, 1), (9, 1), (1, 8), (9, 8)]:              # column bases
        b.set(x, 0, z, B("smooth_quartz"))
        b.set(x, 1, z, B("quartz_slab", type="bottom") if rng.random() < 0.5 else B("quartz_pillar", axis="y"))
    for x in range(0, 11):                                       # the wall along the north
        if rng.random() < 0.8:
            b.set(x, 0, 0, B(rng.choice(["expanse:limestone", "expanse:limestone_bricks", "cobblestone"])))
            if rng.random() < 0.6:
                b.set(x, 1, 0, B(rng.choice(["expanse:limestone", "expanse:limestone_bricks"])))
    strew(b, rng, free(b, cols(b)), ["short_grass", "expanse:lavender", "short_grass"], "expanse:red_earth", 0.2)
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

    # ---- the second wave: natural things in every region's own materials
    N = 7100
    add(uprooted("uprooted_oak", "oak", N + 1, litter="leaf_litter"))
    add(uprooted("uprooted_beech", "beech", N + 2, soil=("moss_block", "podzol", "grass_block"), litter="leaf_litter"))
    add(fallen_tree("log_birch", "birch", N + 3, length=5, peel=0.4, brackets=2, moss=0.1, litter="leaf_litter"))
    add(snag("snag_birch", "birch", N + 4, height=5, peel=(3,), brackets=3, litter="leaf_litter"))
    add(fallen_tree("log_dark_oak", "dark_oak", N + 5, length=6, brackets=2, moss=0.35, mushrooms=0.25,
                    soil=("podzol", "moss_block", "coarse_dirt"), extras=("fern", "brown_mushroom", "red_mushroom")))
    add(toadstools(N + 6))
    add(stump("stump_pale_oak", "pale_oak", N + 7, girth=2, height=2, soil=("pale_moss_block", "coarse_dirt"),
              top="pale_moss_carpet", extras=["closed_eyeblossom", "short_grass"]))
    b = fallen_tree("log_pale_oak", "pale_oak", N + 8, length=5, brackets=1, moss=0.45, mushrooms=0.0,
                    soil=("pale_moss_block", "coarse_dirt"), extras=("closed_eyeblossom", "short_grass"))
    b.replace("moss_carpet", "pale_moss_carpet")
    b.replace("moss_block", "pale_moss_block")
    add(b)
    add(rocks("pale_rocks", N + 9, MOSSY, ["01100", "12210", "01221", "00110"], soil=["pale_moss_block"],
              plants=["closed_eyeblossom", "short_grass"], carpet="pale_moss_carpet"))
    add(pale_snag(N + 10))
    add(fallen_tree("log_spruce", "spruce", N + 11, length=6, brackets=1, moss=0.4, extras=("fern", ("tall", "large_fern"))))
    add(ant_mound(N + 12))
    add(hollow_redwood(N + 13))
    add(stump("stump_maple", "maple", N + 14, height=2, litter="leaf_litter", extras=["fern", "short_grass"]))
    add(fallen_tree("log_maple", "maple", N + 15, length=6, peel=0.15, brackets=3, moss=0.2, litter="leaf_litter"))
    add(stump("stump_aspen", "aspen", N + 16, height=1, soil=("grass_block", "coarse_dirt"), top=None,
              extras=[("tall", "expanse:fireweed"), "short_grass"]))
    add(beaver_cuttings(N + 17))
    add(stump("stump_beech", "beech", N + 18, height=2, soil=("moss_block", "podzol"), litter="leaf_litter",
              extras=["fern", "short_grass"]))
    add(beech_hollow(N + 19))
    add(boulder_field(N + 20))
    add(stump("stump_pine", "pine", N + 21, height=2, soil=("podzol", "coarse_dirt"), extras=["fern", "short_grass"]))
    add(erratic(N + 22))
    add(bog_hummock(N + 64))
    add(standing_stones("standing_stones", N + 23, count=5, r=3.4))
    add(standing_stones("standing_stones_small", N + 24, count=3, r=2.2))
    add(dolmen(N + 25))
    add(scree(N + 26))
    add(ice_boulder(N + 27))
    add(avalanche_debris(N + 28))
    add(prismite_geode(N + 29))
    add(ice_shards(N + 30))
    add(rune_stone(N + 31))
    add(pinnacles("tepui_pinnacles", N + 32, [("stone", 3), ("andesite", 2), ("mossy_cobblestone", 2)],
                  ["011000", "145000", "010021", "000163", "000110", "100000"]))
    add(rock_pool("rock_pool", N + 33, [("stone", 3), ("mossy_cobblestone", 2), ("andesite", 1)]))
    add(rock_pool("tide_pool", N + 34, [("stone", 3), ("andesite", 2), ("cobblestone", 1)], tide=True))
    add(termite_mound("termite_mound", N + 35, [("coarse_dirt", 2), ("terracotta", 3), ("packed_mud", 2)], height=3,
                      spires=((2, 2, 1),)))
    add(termite_mound("termite_cathedral", N + 36, [("expanse:red_earth", 3), ("orange_terracotta", 2),
                                                    ("terracotta", 1)], height=4,
                      spires=((2, 2, 1), (3, 3, -1), (3, 2, 0)), soil=("expanse:red_earth",),
                      plants=("expanse:spinifex", "short_dry_grass")))
    add(fallen_tree("log_ghost_gum", "eucalyptus", N + 37, length=6, bark="stripped_log", brackets=0, moss=0.0,
                    mushrooms=0.0, soil=("expanse:red_earth", "coarse_dirt"),
                    extras=("expanse:spinifex", "short_dry_grass", "dead_bush")))
    add(tor("tor_granite", N + 38, [
        ["..##.", ".####", "#####", ".###.", "..#.."],
        [".....", "..##.", ".###.", "..##.", "....."],
        [".....", "..s..", "..#..", ".....", "....."],
        [".....", ".....", "..#..", ".....", "....."]],
        [("granite", 4), ("polished_granite", 1), ("expanse:red_earth", 1)], "granite_slab",
        soil=("expanse:red_earth",), plants=("expanse:spinifex", "short_dry_grass")))
    add(tor("tor_sandstone", N + 39, [
        [".###.", "#####", "####.", ".##..", "....."],
        [".##..", ".###.", ".##..", ".....", "....."],
        [".s...", "..#..", ".....", ".....", "....."]],
        [("red_sandstone", 3), ("smooth_red_sandstone", 1), ("cut_red_sandstone", 1)], "red_sandstone_slab",
        soil=("red_sand", "coarse_dirt"), plants=("dead_bush", "short_dry_grass", "expanse:desert_marigold")))
    add(hoodoo(N + 40))
    add(rockfall("rockfall_canyon", N + 41, [("orange_terracotta", 2), ("terracotta", 2), ("red_terracotta", 1),
                                              ("white_terracotta", 1), ("red_sandstone", 2)]))
    add(dry_wash(N + 42))
    add(salt_ridges(N + 43))
    add(brine_pool(N + 44))
    add(lava_bomb(N + 45))
    add(obsidian_shards(N + 46))
    add(fumarole_crust(N + 47))
    add(charred_snag(N + 48))
    add(swamp_snag("snag_swamp", "oak", N + 49))
    add(swamp_snag("snag_bayou", "willow", N + 50, moss="expanse:spanish_moss"))
    add(mangrove_tangle(N + 51))
    add(stump("stump_teak", "teak", N + 52, height=2, soil=("coarse_dirt", "podzol"), litter="leaf_litter",
              extras=["fern", "short_grass"]))
    add(buttress_root(N + 53))
    add(fallen_tree("log_kapok", "kapok", N + 54, length=7, brackets=2, moss=0.45, mushrooms=0.1,
                    soil=("podzol", "moss_block", "mud"), extras=("fern", "expanse:moth_orchid", ("tall", "large_fern"))))
    add(vine_boulder(N + 55))
    add(karst_pinnacle(N + 56))
    add(olive_stump(N + 57))
    add(rocks("terra_rossa_rocks", N + 58, LIMESTONE, ["0110", "1221", "0121", "0010"], moss=0.0,
              soil=["expanse:red_earth", "coarse_dirt"], plants=["short_grass", "expanse:lavender"],
              top_slab="expanse:limestone_slab"))
    add(whale_bones(N + 59))
    add(coral_rubble(N + 60))
    add(drift("driftwood_palm", N + 61, "palm", "expanse:coral_sand"))
    add(drift("driftwood_spruce", N + 62, "spruce", "gravel"))
    add(kelp_wrack(N + 63))

    # ---- the second wave: traces of people
    T = 7200
    add(scarecrow_patch(T + 1))
    add(bee_skeps(T + 2))
    add(bird_box(T + 3))
    add(wayside_cross(T + 4))
    add(milestone(T + 5))
    add(picnic(T + 6))
    add(broken_cart("cart_pumpkins", T + 7, "spruce", ["pumpkin", "pumpkin", B("hay_block", axis="y")]))
    add(broken_cart("cart_olives", T + 8, "expanse:olive", ["barrel", "melon"],
                    ground=("coarse_dirt", "expanse:red_earth"), plants=("short_grass",), soil="expanse:red_earth"))
    add(charcoal_clamp(T + 9))
    add(high_seat(T + 10))
    add(conciliation_cross(T + 101))
    add(field_chapel(T + 102))
    add(linden(T + 103))
    add(saw_trestle(T + 11))
    add(sugaring_kettle(T + 12))
    add(sheepfold(T + 13))
    add(peat_stack(T + 14))
    add(shepherds_shelter(T + 15))
    add(lean_to(T + 16))
    add(snow_sled(T + 17))
    add(ice_fishing(T + 18))
    add(marker_pole(T + 19))
    add(springboard_stump(T + 20))
    add(boardwalk(T + 21))
    add(upturned_boat("upturned_boat", T + 22, "spruce"))
    add(upturned_boat("upturned_boat_palm", T + 23, "palm", ground="sand"))
    add(ranch_gate("ranch_gate", T + 24, "dark_oak", soil="coarse_dirt"))
    add(ranch_gate("ranch_gate_acacia", T + 25, "acacia", soil="coarse_dirt"))
    add(net_rack("net_rack", T + 26, "spruce", "gravel"))
    add(net_rack("net_rack_palm", T + 27, "palm", "sand"))
    add(thorn_boma(T + 28))
    add(yurt_ring(T + 29))
    add(ovoo(T + 30))
    add(balbal(T + 31))
    add(swag_camp(T + 32))
    add(stockyard("stockyard", T + 33, "eucalyptus", soil="expanse:red_earth", plants=("expanse:spinifex",)))
    add(stockyard("stockyard_aspen", T + 34, "aspen", soil="grass_block", plants=("short_grass",)))
    add(ocotillo_fence(T + 35))
    add(prospect_pit("prospect_pit", T + 36, ["gravel", "red_sand", "gravel"], wood="dark_oak"))
    add(prospect_pit("sulphur_pit", T + 37, ["sulfur", "tuff", "expanse:volcanic_ash"], wood="spruce",
                     soil="expanse:volcanic_ash"))
    add(salt_stack(T + 38))
    add(salt_cairn(T + 39))
    add(column_amphorae(T + 40))
    add(terrace_wall(T + 41))
    add(sunken_skiff("sunken_skiff", T + 42, "spruce"))
    add(sunken_skiff("sunken_skiff_mangrove", T + 43, "mangrove"))
    add(fish_weir(T + 44))
    add(stilt_stubs("stilt_stubs", T + 45, "mangrove"))
    add(jungle_idol(T + 46))
    add(stone_lantern("stone_lantern", T + 47))
    add(stone_lantern("stone_lantern_limestone", T + 48, "expanse:limestone_brick", "expanse:limestone_bricks",
                      "expanse:limestone_slab"))
    add(garden_urn(T + 49))
    add(lost_cache("lost_cache", T + 50, ["mossy_cobblestone", "cobblestone"], ["coarse_dirt", "dirt"]))
    add(lost_cache("lost_cache_dry", T + 51, ["sandstone", "red_sandstone"], ["coarse_dirt", "sand"]))
    add(lost_cache("lost_cache_snow", T + 52, ["cobblestone", "stone"], ["snow_block", "gravel"]))

    # ---- the second wave: ruins
    R = 7300
    add(ruin_cottage(R + 1))
    add(ruin_chapel(R + 2))
    add(ruin_lime_kiln(R + 3))
    add(ruin_watchpost(R + 4))
    add(ruin_statue(R + 5))
    add(ruin_garden_gate(R + 6))
    add(ruin_farmstead(R + 7))
    add(ruin_olive_press(R + 8))
    add(ruin_adobe(R + 9))
    add(ruin_stair(R + 10))
    add(ruin_stone_face(R + 11))
    add(ruin_brick_shrine(R + 12))
    add(ruin_boathouse(R + 13))
    add(ruin_chimney(R + 14))
    add(ruin_chimney(R + 15, "ruin_chimney_outback", soil="expanse:red_earth", plants=("expanse:spinifex",
                                                                                       "short_dry_grass")))
    add(ruin_salt_works(R + 16))
    add(ruin_kurgan(R + 17))
    add(ruin_ash_house(R + 18))
    add(ruin_snow_cabin(R + 19))
    add(ruin_wreck(R + 20))
    add(ruin_wreck(R + 21, "ruin_wreck_palm", "palm", "expanse:coral_sand"))
    add(ruin_pavilion(R + 22))
    add(ruin_stilt_hut(R + 23))
    add(ruin_villa_floor(R + 24))
    add(ruin_stair(R + 25, "ruin_stair_limestone", LIMESTONE, "expanse:limestone"))
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


def aged(*woods):
    """aged_wood for any wood set, the newer mod woods too: a log here and there stripped of its bark."""
    out = []
    for w in woods:
        out += axes(wn(w, "log"), wn(w, "stripped_log"), 0.12)
    return out


def bluebells(*amounts):
    return [(B("expanse:bluebells", facing=f, flower_amount=n), 0.18) for f, n in zip(H4, amounts)]


FROST = [rule("cobblestone", 0.3, "stone"), rule("mossy_cobblestone", 0.8, "cobblestone"), rule("moss_carpet", 1.0, "air"),
         rule("short_grass", 0.9, "air"), rule("fern", 0.9, "air"), rule("leaf_litter", 1.0, "air"),
         rule("mossy_cobblestone_slab", 1.0, "cobblestone")]
RED_EARTH = [rule("grass_block", 0.7, "expanse:red_earth"), rule("coarse_dirt", 0.3, "expanse:red_earth")]
# The second wave's climates: the newer regions, each with its own flowers, woods and weathering.
PROCESSORS.update({
    "pale": plist(STONE_WEATHER, aged("pale_oak"), flowers(("closed_eyeblossom", 0.15), ("open_eyeblossom", 0.03),
                                                           ("air", 0.35)),
                  [rule("moss_carpet", 1.0, B("pale_moss_carpet")), rule("moss_block", 1.0, "pale_moss_block")]),
    "maple": plist(STONE_WEATHER, aged("maple", "oak"), flowers(("fern", 0.3), ("poppy", 0.04), ("air", 0.2)),
                   CARPET_ROT, WALL_ROT),
    "aspen": plist(STONE_WEATHER, aged("aspen", "spruce"),
                   flowers((B("wildflowers", facing="north", flower_amount=3), 0.15), ("dandelion", 0.06),
                           ("cornflower", 0.05), ("air", 0.25)), [rule("moss_carpet", 0.6, "air")]),
    "larch": plist(STONE_WEATHER, aged("spruce"), flowers(("expanse:reindeer_lichen", 0.25), ("fern", 0.2),
                                                          ("air", 0.2)), CARPET_ROT),
    "muskeg": plist(STONE_WEATHER, aged("spruce"), flowers(("expanse:cotton_grass", 0.3),
                                                           ("expanse:cranberry_bush", 0.12), ("air", 0.2)),
                    [rule("moss_carpet", 0.6, "expanse:sphagnum_moss_carpet"),
                     rule("moss_block", 0.6, "expanse:sphagnum_moss")]),
    "bluebell": plist(STONE_WEATHER, aged("beech", "oak"), flowers(*bluebells(4, 3, 4, 2), ("fern", 0.15)),
                      CARPET_ROT),
    "heath": plist(STONE_WEATHER, aged("pine"), flowers(("expanse:heather", 0.35), ("expanse:reindeer_lichen", 0.12),
                                                        ("fern", 0.1), ("air", 0.15)),
                   [rule("moss_carpet", 0.4, "air")], WALL_ROT),
    "fjord": plist(STONE_WEATHER, aged("spruce"), flowers(("fern", 0.25), ("expanse:reindeer_lichen", 0.1),
                                                          ("azure_bluet", 0.05), ("air", 0.3)),
                   [rule("moss_carpet", 0.3, "air")]),
    "frost": plist(FROST, aged("spruce")),
    "tepui": plist(STONE_WEATHER, [rule("cobblestone", 0.4, "mossy_cobblestone"), rule("stone", 0.15, "mossy_cobblestone")],
                   aged("jungle"), flowers(("fern", 0.35), ("azure_bluet", 0.05), ("lily_of_the_valley", 0.04),
                                           ("air", 0.15))),
    "volcanic": plist(NO_MOSS, [rule("cobblestone", 0.35, "tuff"), rule("andesite", 0.3, "tuff"),
                                rule("stone", 0.2, "smooth_basalt"), rule("gravel", 0.4, "expanse:volcanic_ash")],
                      aged("spruce", "dark_oak"), flowers(("dead_bush", 0.15), ("short_dry_grass", 0.1), ("air", 0.6))),
    "salt": plist(NO_MOSS, [rule("cobblestone", 0.3, "expanse:salt_block"), rule("gravel", 0.5, "expanse:salt_block")],
                  flowers(("dead_bush", 0.1), ("air", 0.9))),
    "outback": plist(NO_MOSS, WARM_STONE, aged("eucalyptus", "acacia"), RED_EARTH,
                     flowers(("expanse:spinifex", 0.35), ("expanse:desert_pea", 0.05), ("short_dry_grass", 0.2),
                             ("air", 0.2))),
    "saguaro": plist(NO_MOSS, SAND_WEATHER, aged("dark_oak"), [rule("grass_block", 0.8, "coarse_dirt")],
                     flowers(("expanse:desert_marigold", 0.1), ("short_dry_grass", 0.3), ("dead_bush", 0.1),
                             ("air", 0.3))),
    "olive": plist(LIMESTONE_WEATHER, [rule("cobblestone", 0.3, "expanse:limestone"), rule("moss_carpet", 0.7, "air"),
                                       rule("mossy_cobblestone", 0.5, "cobblestone"), rule("moss_block", 1.0, "coarse_dirt"),
                                       rule("grass_block", 0.4, "expanse:red_earth")],
                   aged("olive"), flowers(("expanse:lavender", 0.25), ("poppy", 0.05), ("short_dry_grass", 0.15),
                                          ("air", 0.15))),
    "monsoon": plist(STONE_WEATHER, [rule("cobblestone", 0.4, "mossy_cobblestone"), rule("grass_block", 0.3, "coarse_dirt")],
                     aged("teak", "jungle"), flowers(("fern", 0.3), ("air", 0.2))),
    "kapok": plist(STONE_WEATHER, [rule("cobblestone", 0.5, "mossy_cobblestone"),
                                   rule("stone_bricks", 0.3, "mossy_stone_bricks")],
                   aged("kapok", "jungle"), flowers(("fern", 0.3), ("expanse:moth_orchid", 0.06), ("air", 0.15))),
    "coral": plist(NO_MOSS, SAND_WEATHER, aged("palm"), flowers(("short_dry_grass", 0.2), ("air", 0.5)),
                   rules(*recolor([("sand", "expanse:coral_sand"), ("gravel", "expanse:coral_sand")]))),
    "mangrove": plist(STONE_WEATHER, [rule("cobblestone", 0.4, "mossy_cobblestone"), rule("grass_block", 0.6, "mud")],
                      aged("mangrove"), BAYOU),
    "mushroom": plist(STONE_WEATHER, flowers(("red_mushroom", 0.15), ("brown_mushroom", 0.15), ("air", 0.6)),
                      [rule("grass_block", 1.0, "mycelium"), rule("podzol", 1.0, "mycelium"),
                       rule("moss_carpet", 1.0, "air"), rule("moss_block", 1.0, "mycelium")]),
    "cold_shore": plist([rule("cobblestone", 0.2, "stone"), rule("moss_carpet", 0.8, "air"),
                         rule("mossy_cobblestone", 0.5, "cobblestone")], aged("spruce"), flowers(("air", 0.7))),
})


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
    # a barrel sunk under a stone by someone on the road: less than the lost chest, plain things for travelling
    "micro/lost_cache": L.table(
        "micro/lost_cache",
        L.pool((2, 4), L.item("bread", 4, (1, 3)), L.item("dried_kelp", 2, (2, 6)), L.item("coal", 3, (1, 4)),
               L.item("torch", 3, (1, 4)), L.item("string", 2, (1, 3)), L.item("leather", 2, (1, 2)),
               L.item("iron_nugget", 3, (2, 6)), L.item("arrow", 2, (2, 6)), L.item("flint", 1, (1, 2)),
               L.item("wheat", 2, (2, 5)), L.item("potato", 2, (1, 4)), L.item("emerald", 1, (1, 2))),
        L.pool((0, 1), L.empty(6), L.item("lead", 1), L.item("compass", 1), L.item("iron_axe", 1, damage=(0.3, 0.8)),
               L.item("bucket", 1))),
    # what the last people to live in a ruin hid there: a little better than a village house chest
    "micro/ruin_cache": L.table(
        "micro/ruin_cache",
        L.pool((3, 6), L.item("gold_nugget", 4, (2, 8)), L.item("iron_nugget", 4, (3, 9)), L.item("bread", 3, (1, 3)),
               L.item("candle", 3, (1, 3)), L.item("paper", 2, (1, 4)), L.item("book", 2, (1, 2)),
               L.item("emerald", 2, (1, 3)), L.item("iron_ingot", 2, (1, 3)), L.item("gold_ingot", 1, (1, 2)),
               L.item("lapis_lazuli", 2, (2, 6)), L.item("bone", 2, (1, 4)), L.item("glass_bottle", 1, (1, 2))),
        L.pool((0, 1), L.empty(4), L.book(2), L.item("golden_apple", 1), L.item("clock", 1), L.item("name_tag", 1),
               L.item("iron_sword", 1, damage=(0.2, 0.7)), L.item("archer_pottery_sherd", 1),
               L.item("heart_pottery_sherd", 1))),
    # a sealed jar in the olive country: oil (honey), grain, a coin or two
    "micro/amphora": L.table(
        "micro/amphora",
        L.pool((1, 3), L.item("honey_bottle", 3, (1, 2)), L.item("wheat", 3, (2, 6)), L.item("wheat_seeds", 2, (2, 6)),
               L.item("gold_nugget", 3, (2, 6)), L.item("emerald", 1, (1, 2)), L.item("brick", 1, (1, 3))),
        L.pool((0, 1), L.empty(6), L.item("prize_pottery_sherd", 1), L.item("sheaf_pottery_sherd", 1))),
    # a prospector's barrel: the tools of the trade and a little of what was found
    "micro/prospector_cache": L.table(
        "micro/prospector_cache",
        L.pool((2, 5), L.item("torch", 3, (2, 6)), L.item("raw_iron", 3, (1, 4)), L.item("raw_copper", 3, (2, 6)),
               L.item("gold_nugget", 3, (3, 9)), L.item("coal", 2, (2, 5)), L.item("bread", 2, (1, 2)),
               L.item("rail", 1, (2, 6)), L.item("raw_gold", 1, (1, 2))),
        L.pool((0, 1), L.empty(5), L.item("iron_pickaxe", 2, damage=(0.2, 0.7)),
               L.item("iron_shovel", 1, damage=(0.3, 0.8)), L.item("miner_pottery_sherd", 1))),
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
                 blocks_("gravel", "snow_block", "calcite", "sandstone", "red_sandstone", "expanse:opal_sandstone",
                         "expanse:volcanic_ash", "expanse:salt_block", "basalt", "blackstone"))
VOLCANIC = any_of(SOIL, blocks_("expanse:volcanic_ash", "basalt", "blackstone", "gravel"),
                  tag("minecraft:base_stone_overworld"))
SALT = any_of(blocks_("expanse:salt_block", "gravel"), tag("minecraft:sand"), SOIL)
SHORE = any_of(tag("minecraft:sand"), blocks_("gravel", "sandstone", "snow_block", "calcite"), SOIL,
               tag("minecraft:base_stone_overworld"))
STONY = any_of(SOIL, tag("minecraft:base_stone_overworld"))


class Group:
    """A placed feature (expanse:micro/<name>) drawing one template from a weighted list, once in `rarity`
    chunks; the biomes it goes to are listed in LittleThings.java. `margin` is how far (in blocks) its
    placements keep from any structure piece: the ruins, being bigger, keep further off."""

    def __init__(self, name, rarity, items, procs, ground=SOIL, relief=2, biomes="", margin=MARGIN):
        self.name, self.rarity, self.items, self.procs, self.ground, self.relief = name, rarity, items, procs, ground, relief
        self.biomes, self.margin = biomes, margin


RUIN_MARGIN = 14            # a ruin's origin keeps this far from any structure piece (its footprint reaches 7)
RUIN = dict(margin=RUIN_MARGIN)

GROUPS = [
    # ---- natural things: every one or two chunks, in each region's own materials
    Group("oak_woods", 2, [("stump_oak", 5), ("mossy_rocks", 3), ("mossy_rocks_low", 2), ("mushroom_ring", 2),
                           ("log_oak", 2), ("snag_beehive", 1), ("uprooted_oak", 2), ("boulder_field", 2),
                           ("ant_mound", 1)], "temperate",
          biomes="forest, flower forest, windswept forest, dappled forest, woodland glades"),
    Group("birch_woods", 2, [("stump_birch", 5), ("log_birch", 3), ("snag_birch", 2), ("mossy_rocks", 2),
                             ("mossy_rocks_low", 2), ("mushroom_ring", 2), ("snag_beehive", 1)], "temperate",
          biomes="birch forests"),
    Group("dark_woods", 2, [("stump_dark_oak", 5), ("mushroom_ring", 4), ("mossy_rocks", 2), ("log_dark_oak", 3),
                            ("toadstools", 2)], "temperate", biomes="dark forest, ancient oakwood"),
    Group("taiga", 2, [("stump_spruce", 5), ("berry_clump", 3), ("mossy_rocks", 2), ("boulders_scattered", 2),
                       ("fox_den", 2), ("log_spruce", 3), ("ant_mound", 2), ("erratic", 1)], "north",
          biomes="taigas, snowy taiga"),
    Group("redwood", 2, [("stump_redwood", 4), ("berry_clump", 2), ("fox_den", 2), ("mossy_rocks", 2),
                         ("mossy_rocks_low", 2), ("mushroom_ring", 1), ("hollow_redwood", 3)], "north",
          biomes="redwood giants"),
    Group("grassland", 2, [("boulder", 2), ("boulders_scattered", 1), ("snag_beehive", 2), ("foundation_flowers", 2),
                           ("mossy_rocks_low", 1), ("spring_ring", 1), ("erratic", 2), ("standing_stones_small", 1)],
          "temperate", biomes="plains, sunflower plains, meadow"),
    Group("moor", 2, [("boulder", 3), ("boulders_scattered", 3), ("cairn", 3), ("old_wall", 3), ("mossy_rocks", 1),
                      ("spring_ring", 1), ("erratic", 2), ("standing_stones", 1), ("standing_stones_small", 1),
                      ("dolmen", 1)], "moor", ROCKY, biomes="heather moor, windswept hills"),
    Group("peaks", 3, [("boulder", 3), ("boulders_scattered", 3), ("cairn", 3), ("mossy_rocks_low", 1), ("erratic", 2),
                       ("scree", 3)], "peaks", ROCKY, biomes="verdant peaks, stony peaks, dolomite peaks"),
    Group("crystal", 3, [("prismite_rocks", 4), ("boulders_scattered", 2), ("cairn", 1), ("prismite_geode", 3)],
          "peaks", ROCKY, biomes="(none: kept registered, see LittleThings)"),
    Group("snow", 2, [("boulder", 3), ("boulders_scattered", 2), ("cairn", 2), ("stump_spruce", 2), ("fox_den", 1),
                      ("ice_boulder", 2), ("erratic", 1)], "snow", SNOWY_GROUND,
          biomes="snowy plains, grove, frostbloom tundra"),
    Group("savanna", 2, [("stump_acacia", 4), ("boulder", 2), ("boulders_scattered", 2), ("bones", 1),
                         ("termite_mound", 3)], "warm", DRY_GROUND, biomes="savannas"),
    Group("steppe", 2, [("log_baobab", 3), ("boulder", 2), ("boulders_scattered", 3), ("bones", 1)], "warm",
          DRY_GROUND, biomes="amber steppe"),
    Group("desert", 3, [("sandstone_rocks", 4), ("bones", 2), ("dry_wash", 1)], "desert", DRY_GROUND, biomes="desert"),
    Group("badlands", 3, [("sandstone_rocks", 4), ("bones", 2), ("hoodoo", 2), ("rockfall_canyon", 1)], "badlands",
          DRY_GROUND, biomes="badlands, red dunes"),
    Group("dunes", 3, [("opal_rocks", 4), ("bones", 2)], "dunes", DRY_GROUND, biomes="(none: kept registered, see LittleThings)"),
    Group("swamp", 2, [("stump_oak", 3), ("mushroom_ring", 3), ("mossy_rocks_low", 1), ("snag_swamp", 3)], "wet",
          biomes="swamp"),
    Group("bayou", 2, [("cypress_knees", 5), ("mushroom_ring", 2), ("snag_bayou", 3)], "wet", biomes="willow bayou"),
    Group("jungle", 2, [("stump_jungle", 4), ("mossy_rocks", 3), ("mossy_rocks_low", 2), ("spring_ring", 1),
                        ("vine_boulder", 3)], "jungle", biomes="jungles, cloud forest"),
    Group("karst", 2, [("limestone_rocks", 5), ("stump_jungle", 1), ("spring_ring", 1), ("karst_pinnacle", 3)], "karst",
          ROCKY, biomes="jade karst"),
    Group("lumen", 2, [("log_lumen", 4), ("mossy_rocks", 2), ("mushroom_ring", 2), ("spring_ring", 1)], "lumen",
          biomes="(none: kept registered, see LittleThings)"),
    Group("vale", 2, [("stump_wisteria", 3), ("log_wisteria", 3), ("foundation_flowers", 2), ("stone_bench", 1),
                      ("spring_ring", 1)], "vale", biomes="(none: kept registered, see LittleThings)"),
    Group("cherry", 2, [("stump_cherry", 4), ("stone_bench", 1), ("foundation_flowers", 1), ("mossy_rocks_low", 1)],
          "cherry", biomes="cherry grove"),
    Group("coast", 3, [("driftwood", 4), ("sandstone_rocks", 2), ("tide_pool", 2)], "coast", SANDY,
          biomes="beach, palm coast"),
    Group("pale", 2, [("stump_pale_oak", 4), ("log_pale_oak", 3), ("pale_rocks", 3), ("snag_pale_oak", 3)], "pale",
          biomes="pale garden"),
    Group("maple", 2, [("stump_maple", 5), ("log_maple", 3), ("boulder_field", 2), ("erratic", 2), ("mossy_rocks", 2),
                       ("mushroom_ring", 1)], "maple", STONY, biomes="maple highlands"),
    Group("aspen", 2, [("stump_aspen", 5), ("beaver_cuttings", 3), ("boulder", 2), ("boulders_scattered", 2),
                       ("erratic", 1), ("snag_beehive", 1)], "aspen", biomes="aspen parkland"),
    Group("larch", 2, [("stump_spruce", 4), ("log_spruce", 3), ("erratic", 3), ("ant_mound", 2), ("berry_clump", 2),
                       ("fox_den", 1)], "larch", biomes="larch taiga"),
    Group("muskeg", 2, [("bog_hummock", 5), ("erratic", 2), ("log_spruce", 2), ("stump_spruce", 2), ("ant_mound", 1)],
          "muskeg", biomes="boreal muskeg"),
    Group("bluebell", 2, [("stump_beech", 5), ("beech_hollow", 3), ("uprooted_beech", 2), ("boulder_field", 3),
                          ("mossy_rocks", 2), ("mushroom_ring", 1)], "bluebell", biomes="bluebell woods"),
    Group("heath", 2, [("stump_pine", 5), ("erratic", 3), ("ant_mound", 2), ("boulder", 2), ("boulders_scattered", 2)],
          "heath", biomes="pine heath"),
    Group("frost", 3, [("ice_boulder", 3), ("avalanche_debris", 2), ("boulders_scattered", 2), ("cairn", 2),
                       ("scree", 2)], "frost", SNOWY_GROUND, biomes="snowy slopes, frozen and jagged peaks"),
    Group("ice_spikes", 3, [("ice_shards", 4), ("ice_boulder", 3)], "frost", SNOWY_GROUND, biomes="ice spikes"),
    Group("fjord", 2, [("erratic", 4), ("boulders_scattered", 2), ("stump_spruce", 2), ("scree", 2), ("rock_pool", 2),
                       ("mossy_rocks", 1)], "fjord", ROCKY, biomes="fjordlands"),
    Group("tepui", 2, [("tepui_pinnacles", 4), ("rock_pool", 3), ("mossy_rocks", 2), ("vine_boulder", 1)], "tepui",
          ROCKY, biomes="tepui"),
    Group("outback", 2, [("termite_cathedral", 4), ("log_ghost_gum", 3), ("tor_granite", 3), ("bones", 1)], "outback",
          DRY_GROUND, biomes="ghost gum outback"),
    Group("saguaro", 2, [("tor_sandstone", 4), ("dry_wash", 3), ("sandstone_rocks", 2), ("bones", 1)], "saguaro",
          DRY_GROUND, biomes="saguaro flats"),
    Group("canyons", 3, [("hoodoo", 4), ("rockfall_canyon", 4), ("sandstone_rocks", 2), ("bones", 1)], "badlands",
          DRY_GROUND, biomes="painted canyons"),
    Group("salt", 3, [("salt_ridges", 4), ("brine_pool", 3), ("bones", 1)], "salt", SALT, biomes="salt flats"),
    Group("volcanic", 2, [("lava_bomb", 4), ("obsidian_shards", 3), ("fumarole_crust", 3), ("charred_snag", 2),
                          ("boulders_scattered", 1)], "volcanic", VOLCANIC, biomes="volcanic highlands"),
    Group("mangrove", 2, [("mangrove_tangle", 5), ("snag_swamp", 1), ("mushroom_ring", 1)], "mangrove",
          biomes="mangrove swamp"),
    Group("monsoon", 2, [("stump_teak", 5), ("termite_mound", 2), ("mossy_rocks", 2), ("vine_boulder", 1),
                         ("mushroom_ring", 1)], "monsoon", biomes="monsoon forest"),
    Group("kapok", 2, [("buttress_root", 3), ("log_kapok", 3), ("vine_boulder", 3), ("stump_jungle", 2),
                       ("mossy_rocks", 2)], "kapok", biomes="kapok rainforest"),
    Group("olive", 2, [("olive_stump", 4), ("terra_rossa_rocks", 4), ("boulder", 1)], "olive", STONY,
          biomes="olive groves"),
    Group("mushroom", 2, [("toadstools", 4), ("mushroom_ring", 3)], "mushroom", biomes="mushroom fields"),
    Group("coral", 3, [("coral_rubble", 4), ("driftwood_palm", 3), ("tide_pool", 1)], "coral", SHORE,
          biomes="coral coast"),
    Group("cold_shore", 3, [("whale_bones", 2), ("driftwood_spruce", 3), ("kelp_wrack", 3), ("tide_pool", 2),
                            ("boulders_scattered", 1)], "cold_shore", SHORE, biomes="stony shore, snowy beach"),
    # ---- traces of people: one every four or five chunks, on gentler ground
    Group("traces_fields", 4, [("signpost", 2), ("hay_spill", 2), ("wheel_post", 1), ("old_fence", 2),
                               ("scarecrow_patch", 3), ("bee_skeps", 2), ("wayside_cross", 2), ("milestone", 2),
                               ("picnic", 1), ("cart_pumpkins", 2), ("bird_box", 2), ("lost_cache", 1),
                               ("conciliation_cross", 2), ("field_chapel", 2), ("linden", 2)], "temperate",
          relief=1, biomes="plains, sunflower plains, meadow"),
    Group("traces_woods", 4, [("campfire_spruce", 2), ("woodpile", 2), ("lantern_post", 1), ("signpost", 1),
                              ("charcoal_clamp", 3), ("high_seat", 2), ("saw_trestle", 2), ("bird_box", 1),
                              ("stone_bench", 1), ("lost_cache", 1), ("conciliation_cross", 1), ("field_chapel", 1)],
          "temperate", relief=1,
          biomes="the oak, birch and dark woods, bluebell woods"),
    Group("traces_maple", 4, [("sugaring_kettle", 4), ("woodpile", 2), ("charcoal_clamp", 2), ("high_seat", 1),
                              ("saw_trestle", 2), ("lost_cache", 1)], "maple", STONY, relief=1,
          biomes="maple highlands"),
    Group("traces_moor", 4, [("sheepfold", 3), ("peat_stack", 2), ("shepherds_shelter", 2), ("bee_skeps", 1),
                             ("signpost", 1), ("wayside_cross", 1), ("lost_cache", 1)], "moor", ROCKY, relief=1,
          biomes="heather moor, windswept hills, pine heath"),
    Group("traces_vale", 4, [("stone_bench", 2), ("garden_urn", 3), ("bee_skeps", 2), ("picnic", 2), ("lantern_post", 2),
                             ("wayside_cross", 1), ("lost_cache", 1)], "vale", relief=1, biomes="(none: kept registered, see LittleThings)"),
    Group("traces_cherry", 4, [("stone_lantern", 3), ("stone_bench", 2), ("picnic", 2), ("garden_urn", 1),
                               ("lantern_post", 1)], "cherry", relief=1, biomes="cherry grove"),
    Group("traces_north", 4, [("campfire_spruce", 3), ("woodpile", 3), ("lean_to", 3), ("high_seat", 1), ("signpost", 1),
                              ("lantern_post", 1), ("old_fence", 1), ("lost_cache", 1)], "north", SNOWY_GROUND, relief=1,
          biomes="taigas, snowy taiga, larch taiga"),
    Group("traces_redwood", 4, [("springboard_stump", 3), ("woodpile", 2), ("campfire_spruce", 2), ("saw_trestle", 2),
                                ("lean_to", 1)], "north", relief=1, biomes="redwood giants"),
    Group("traces_snow", 4, [("snow_sled", 3), ("ice_fishing", 2), ("campfire_spruce", 2), ("lean_to", 1),
                             ("marker_pole", 1), ("lost_cache_snow", 1)], "snow", SNOWY_GROUND, relief=1,
          biomes="snowy plains, grove, frostbloom tundra, ice spikes"),
    Group("traces_peaks", 5, [("marker_pole", 2), ("shepherds_shelter", 3), ("sheepfold", 1)], "peaks", ROCKY, relief=1,
          biomes="verdant, stony and dolomite peaks"),
    Group("traces_high", 5, [("marker_pole", 3), ("snow_sled", 1), ("lost_cache_snow", 1)], "frost", SNOWY_GROUND,
          relief=1, biomes="snowy slopes, frozen and jagged peaks"),
    Group("traces_muskeg", 4, [("boardwalk", 4), ("peat_stack", 2), ("campfire_spruce", 1), ("lean_to", 1)], "muskeg",
          relief=1, biomes="boreal muskeg"),
    Group("traces_fjord", 4, [("upturned_boat", 3), ("rune_stone", 2), ("net_rack", 2), ("campfire_spruce", 1),
                              ("woodpile", 1)], "fjord", ROCKY, relief=1, biomes="fjordlands"),
    Group("traces_cold_shore", 5, [("upturned_boat", 3), ("net_rack", 3), ("campfire_spruce", 1), ("lost_cache", 1)],
          "cold_shore", SHORE, relief=1, biomes="stony shore, snowy beach"),
    Group("traces_aspen", 4, [("stockyard_aspen", 3), ("old_fence", 2), ("hay_spill", 1), ("campfire_spruce", 1),
                              ("scarecrow_patch", 1), ("cart_pumpkins", 1), ("lost_cache", 1)], "aspen", relief=1,
          biomes="aspen parkland"),
    Group("traces_warm", 4, [("campfire_acacia", 3), ("hay_spill", 1), ("wheel_post", 1), ("thorn_boma", 3),
                             ("ranch_gate_acacia", 1), ("lost_cache_dry", 1)], "warm", DRY_GROUND, relief=1,
          biomes="savannas"),
    Group("traces_steppe", 4, [("campfire_acacia", 2), ("yurt_ring", 3), ("ovoo", 2), ("balbal", 2), ("wheel_post", 1),
                               ("lost_cache_dry", 1)], "warm", DRY_GROUND, relief=1, biomes="amber steppe"),
    Group("traces_outback", 4, [("swag_camp", 3), ("stockyard", 3), ("ranch_gate_acacia", 1), ("lost_cache_dry", 1)],
          "outback", DRY_GROUND, relief=1, biomes="ghost gum outback"),
    Group("traces_saguaro", 4, [("ranch_gate", 2), ("ocotillo_fence", 3), ("prospect_pit", 2), ("campfire_acacia", 1),
                                ("lost_cache_dry", 1)], "saguaro", DRY_GROUND, relief=1, biomes="saguaro flats"),
    Group("traces_badlands", 5, [("prospect_pit", 3), ("ranch_gate", 1), ("campfire_acacia", 1), ("lost_cache_dry", 1)],
          "badlands", DRY_GROUND, relief=1, biomes="badlands, painted canyons"),
    Group("traces_desert", 5, [("campfire_acacia", 2), ("lost_cache_dry", 2), ("wheel_post", 1)], "desert", DRY_GROUND,
          relief=1, biomes="desert, red dunes"),
    Group("traces_salt", 5, [("salt_cairn", 3), ("salt_stack", 3), ("lost_cache_dry", 1)], "salt", SALT, relief=1,
          biomes="salt flats"),
    Group("traces_volcanic", 5, [("sulphur_pit", 3), ("cairn", 2), ("lost_cache", 1)], "volcanic", VOLCANIC, relief=1,
          biomes="volcanic highlands"),
    Group("traces_olive", 4, [("column_amphorae", 3), ("terrace_wall", 3), ("cart_olives", 2), ("bee_skeps", 1),
                              ("wayside_cross", 1), ("lost_cache_dry", 1)], "olive", STONY, relief=1,
          biomes="olive groves"),
    Group("traces_wet", 4, [("lantern_post", 2), ("signpost", 1), ("campfire_spruce", 1), ("sunken_skiff", 3),
                            ("stilt_stubs", 1), ("lost_cache", 1)], "wet", relief=1, biomes="swamp, willow bayou"),
    Group("traces_mangrove", 4, [("sunken_skiff_mangrove", 3), ("fish_weir", 3), ("stilt_stubs", 2)], "mangrove",
          relief=1, biomes="mangrove swamp"),
    Group("traces_jungle", 4, [("jungle_idol", 3), ("campfire_spruce", 1), ("lantern_post", 1), ("lost_cache", 1)],
          "jungle", relief=1, biomes="jungles, cloud forest, kapok rainforest, monsoon forest, tepui"),
    Group("traces_karst", 4, [("stone_lantern_limestone", 3), ("lantern_post", 1), ("jungle_idol", 1),
                              ("campfire_spruce", 1)], "karst", ROCKY, relief=1, biomes="jade karst"),
    Group("traces_coast", 4, [("upturned_boat_palm", 3), ("net_rack_palm", 3), ("campfire_acacia", 1),
                              ("lost_cache_dry", 1)], "coast", SANDY, relief=1, biomes="beach, palm coast, coral coast"),
    Group("traces_pale", 5, [("wayside_cross", 1), ("lantern_post", 1), ("lost_cache", 1)], "pale", relief=1,
          biomes="pale garden"),
    Group("traces_lumen", 5, [("lantern_post", 2), ("campfire_spruce", 1), ("stone_lantern", 1)], "lumen", relief=1,
          biomes="(none: kept registered, see LittleThings)"),
    # ---- ruins: bigger hand-made finds, one every ten to fourteen chunks, kept to gentle ground
    Group("ruins_fields", 12, [("ruin_cottage", 3), ("ruin_farmstead", 3), ("ruin_chapel", 2), ("ruin_chimney", 1)],
          "temperate", relief=1, biomes="plains, sunflower plains, meadow, aspen parkland", **RUIN),
    Group("ruins_woods", 12, [("ruin_cottage", 2), ("ruin_statue", 2), ("ruin_chapel", 1), ("ruin_lime_kiln", 1),
                              ("ruin_garden_gate", 1)], "temperate", relief=2,
          biomes="the oak, birch and dark woods, maple highlands, bluebell woods", **RUIN),
    Group("ruins_moor", 12, [("ruin_chapel", 2), ("ruin_lime_kiln", 2), ("ruin_farmstead", 1), ("ruin_cottage", 1)],
          "moor", ROCKY, relief=2, biomes="heather moor, windswept hills, pine heath", **RUIN),
    Group("ruins_vale", 12, [("ruin_garden_gate", 3), ("ruin_statue", 2), ("ruin_cottage", 1)], "vale", relief=1,
          biomes="cherry grove", **RUIN),
    Group("ruins_pale", 14, [("ruin_statue", 2), ("ruin_garden_gate", 1), ("ruin_chapel", 1)], "pale", relief=1,
          biomes="pale garden", **RUIN),
    Group("ruins_north", 12, [("ruin_watchpost", 3), ("ruin_snow_cabin", 1)], "north", SNOWY_GROUND, relief=2,
          biomes="taigas, snowy taiga, larch taiga, redwood giants, boreal muskeg", **RUIN),
    Group("ruins_snow", 13, [("ruin_snow_cabin", 3), ("ruin_watchpost", 1)], "snow", SNOWY_GROUND, relief=1,
          biomes="snowy plains, grove, frostbloom tundra", **RUIN),
    Group("ruins_fjord", 12, [("ruin_boathouse", 3), ("ruin_wreck", 2), ("ruin_watchpost", 1)], "fjord", SHORE, relief=1,
          biomes="fjordlands, stony shore, snowy beach", **RUIN),
    Group("ruins_peaks", 14, [("ruin_stair", 2), ("ruin_lime_kiln", 1)], "peaks", ROCKY, relief=2,
          biomes="verdant peaks, stony peaks", **RUIN),
    Group("ruins_tepui", 12, [("ruin_stair", 3), ("ruin_stone_face", 1)], "tepui", ROCKY, relief=2, biomes="tepui",
          **RUIN),
    Group("ruins_olive", 10, [("ruin_olive_press", 3), ("ruin_villa_floor", 3)], "olive", STONY, relief=1,
          biomes="olive groves", **RUIN),
    Group("ruins_dry", 12, [("ruin_adobe", 3)], "saguaro", DRY_GROUND, relief=1,
          biomes="desert, saguaro flats, badlands, painted canyons", **RUIN),
    Group("ruins_savanna", 12, [("ruin_chimney", 2), ("ruin_adobe", 1)], "warm", DRY_GROUND, relief=1,
          biomes="savannas", **RUIN),
    Group("ruins_steppe", 12, [("ruin_kurgan", 3)], "warm", DRY_GROUND, relief=1, biomes="amber steppe", **RUIN),
    Group("ruins_outback", 12, [("ruin_chimney_outback", 3)], "outback", DRY_GROUND, relief=1,
          biomes="ghost gum outback", **RUIN),
    Group("ruins_jungle", 12, [("ruin_stone_face", 3), ("ruin_brick_shrine", 2)], "kapok", relief=2,
          biomes="jungles, cloud forest, kapok rainforest, monsoon forest", **RUIN),
    Group("ruins_karst", 12, [("ruin_pavilion", 3), ("ruin_stair_limestone", 2)], "karst", ROCKY, relief=1,
          biomes="jade karst", **RUIN),
    Group("ruins_volcanic", 12, [("ruin_ash_house", 3)], "volcanic", VOLCANIC, relief=1,
          biomes="volcanic highlands", **RUIN),
    Group("ruins_salt", 14, [("ruin_salt_works", 3)], "salt", SALT, relief=1, biomes="salt flats", **RUIN),
    Group("ruins_wet", 12, [("ruin_stilt_hut", 3)], "mangrove", relief=1,
          biomes="swamp, mangrove swamp, willow bayou", **RUIN),
    Group("ruins_coast", 14, [("ruin_wreck_palm", 2), ("ruin_wreck", 1)], "coast", SANDY, relief=1,
          biomes="beach, palm coast, coral coast", **RUIN),
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
        {"type": "expanse:clear_of_structures", "margin": g.margin}]}


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
                if max(sx, sz) // 2 + 2 > g.margin:
                    err(f"micro/{g.name}: {t} ({sx}x{sz}) reaches past its clear_of_structures margin {g.margin}")
        if g.procs and g.procs not in PROCESSORS:
            err(f"micro/{g.name}: processor list {g.procs} not written")
        if not 1 <= g.rarity <= 256 or not 0 <= g.relief <= 8 or not 0 <= g.margin <= 48:
            err(f"micro/{g.name}: rarity {g.rarity} / relief {g.relief} / margin {g.margin}")
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
        sx, _, sz = b.size
        for (x, y, z), (st, _) in b.cells.items():              # and its water must not run out over the land
            if st.short == "water" or st.get("waterlogged") == "true":
                sides = [b.get(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
                if y != 0 or not (0 < x < sx - 1 and 0 < z < sz - 1) or any(n is None or n.short == "air" for n in sides):
                    err(f"micro/{b.name}: water at {(x, y, z)} is not held in on every side")
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
              "prismite_rocks": "calcite",
              # the second wave
              "stump_pale_oak": "pale_moss_block", "log_pale_oak": "pale_moss_block",
              "pale_rocks": "pale_moss_block", "snag_pale_oak": "pale_moss_block", "hollow_redwood": "podzol",
              "ant_mound": "podzol", "stump_pine": "podzol", "erratic": "podzol", "bog_hummock": "expanse:peat",
              "scree": "stone", "ice_boulder": "snow_block", "avalanche_debris": "snow_block", "ice_shards": "snow_block",
              "prismite_geode": "calcite", "rune_stone": "podzol", "tepui_pinnacles": "moss_block",
              "rock_pool": "moss_block", "tide_pool": "gravel", "tide_pool_cold": "gravel",
              "termite_mound": "coarse_dirt", "termite_cathedral": "expanse:red_earth",
              "log_ghost_gum": "expanse:red_earth", "tor_granite": "expanse:red_earth", "tor_sandstone": "red_sand",
              "hoodoo": "red_sand", "rockfall_canyon": "red_sand", "dry_wash": "coarse_dirt",
              "salt_ridges": "expanse:salt_block", "brine_pool": "expanse:salt_block",
              "lava_bomb": "expanse:volcanic_ash", "obsidian_shards": "expanse:volcanic_ash",
              "fumarole_crust": "expanse:volcanic_ash", "charred_snag": "expanse:volcanic_ash",
              "snag_swamp": "mud", "snag_bayou": "mud", "mangrove_tangle": "mud", "buttress_root": "podzol",
              "vine_boulder": "moss_block", "karst_pinnacle": "expanse:limestone", "olive_stump": "expanse:red_earth",
              "terra_rossa_rocks": "expanse:red_earth", "whale_bones": "gravel", "coral_rubble": "expanse:coral_sand",
              "driftwood_palm": "expanse:coral_sand", "driftwood_spruce": "gravel", "kelp_wrack": "gravel",
              "toadstools": "mycelium", "mushroom_ring_mycelium": "mycelium",
              "lost_cache_dry": "sand", "lost_cache_snow": "snow_block", "snow_sled": "snow_block",
              "ice_fishing": "snow_block", "marker_pole": "snow_block", "boardwalk": "expanse:sphagnum_moss",
              "upturned_boat": "gravel", "upturned_boat_palm": "sand", "net_rack": "gravel", "net_rack_palm": "sand",
              "ranch_gate": "coarse_dirt", "ranch_gate_acacia": "coarse_dirt", "thorn_boma": "coarse_dirt",
              "yurt_ring": "coarse_dirt", "ovoo": "coarse_dirt", "balbal": "coarse_dirt", "swag_camp": "expanse:red_earth",
              "stockyard": "expanse:red_earth", "ocotillo_fence": "red_sand", "prospect_pit": "red_sand",
              "sulphur_pit": "expanse:volcanic_ash", "salt_stack": "expanse:salt_block",
              "salt_cairn": "expanse:salt_block", "column_amphorae": "expanse:red_earth",
              "terrace_wall": "expanse:red_earth", "cart_olives": "expanse:red_earth", "sunken_skiff": "mud",
              "sunken_skiff_mangrove": "mud", "fish_weir": "mud", "stilt_stubs": "mud", "jungle_idol": "podzol",
              "stone_lantern_limestone": "expanse:limestone", "springboard_stump": "podzol",
              "ruin_adobe": "coarse_dirt", "ruin_salt_works": "expanse:salt_block",
              "ruin_ash_house": "expanse:volcanic_ash", "ruin_snow_cabin": "snow_block", "ruin_wreck": "sand",
              "ruin_wreck_palm": "expanse:coral_sand", "ruin_stilt_hut": "mud", "ruin_villa_floor": "expanse:red_earth",
              "ruin_olive_press": "expanse:red_earth", "ruin_kurgan": "coarse_dirt",
              "ruin_chimney_outback": "expanse:red_earth", "ruin_stone_face": "podzol", "ruin_brick_shrine": "coarse_dirt",
              "ruin_boathouse": "gravel", "ruin_stair_limestone": "expanse:limestone", "ruin_watchpost": "podzol"}


def ground_for(name):
    return GROUND_FOR.get(name, "grass_block")


def preview_sheet(builds, out_dir, only=None, tile=18, name="micro"):
    """Every template (or those matching `only`, a list of fnmatch patterns) set into a little square of its
    usual ground (so 'sitting in the ground' shows). Air cells dig the ground, as they do in the world."""
    import fnmatch
    import preview as P
    panels = []
    for tname in sorted(builds):
        if only and not any(fnmatch.fnmatch(tname, pat) for pat in only):
            continue
        b = builds[tname]
        sx, sy, sz = b.size
        ground = B(ground_for(tname))
        cells = {}
        for x in range(-1, sx + 1):
            for z in range(-1, sz + 1):
                cells[(x, -1, z)] = B("dirt")
                cells[(x, 0, z)] = ground
        for p, (st, _) in b.cells.items():
            cells[p] = st
        cells = {p: st for p, st in cells.items() if st.short not in ("air",)}
        panels.append(P.render(cells, 0, tile, title=f"{tname} {sx}x{sy}x{sz}"))
        panels.append(P.render(cells, 2, tile, title="NW"))
    path = os.path.join(out_dir, name + ".png")
    P.stack([P.grid(panels, 4)], path)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", metavar="DIR", help="render a contact sheet of every template into DIR")
    ap.add_argument("--only", metavar="NAMES", help="preview only these templates (comma-separated, wildcards ok)")
    ap.add_argument("--tile", type=int, default=18, help="preview block size in pixels")
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
        print(f"  {g.name:18s} 1 in {g.rarity:2d} chunks  {', '.join(t for t, _ in g.items)}")
    if args.preview:
        only = args.only.split(",") if args.only else None
        print("preview:", preview_sheet(builds, args.preview, only, args.tile, "micro_only" if only else "micro"))
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
