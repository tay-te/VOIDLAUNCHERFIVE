"""Old wells. One design, four material palettes (separate structures):
  old_well            cobble & spruce     - heather moor, redwood giants, verdant peaks, lumen grove
  old_well_limestone  limestone & redwood - jade karst, cloud forest, wisteria vale
  old_well_sandstone  sandstone & acacia  - amber steppe, opal dunes, palm coast
  old_well_mud        mud brick & willow  - willow bayou

start pool (3 variants)
  covered : a well under a little gabled roof with a windlass and chain, flagstones, a
            trough and a bench; it is a wishing well - the coins lie in a chest on the
            bottom, under the water
  dry     : a dry well, its windlass broken; a ladder goes down the shaft (down jigsaw)
  ruin    : a well fallen in, the roof timbers down, overgrown, with a long trough still
            holding water and an alms barrel
cistern pool (under the dry well): a vaulted cistern with a puddle, the bones of whoever
  fell in, pots, a chest, and silt that gives things up to a brush (archaeology)
"""
from blocks import AIR, B
from builder import Build, item, slab, stairs
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import bones, book, flora, ground_item, pick, plant, ring, trail

PALETTES = {
    "cobble": dict(
        name="old_well", biomes=["heather_moor", "redwood_giants", "verdant_peaks", "lumen_grove"],
        procs="small_wayside", stone=[("cobblestone", 4), ("mossy_cobblestone", 3), ("stone", 2)],
        brick="stone_bricks", bstairs="stone_brick", slab_="cobblestone", wall="cobblestone_wall",
        wood="spruce", log="spruce_log", roof="dark_oak", soil="grass_block",
        ground=["grass_block", "grass_block", "podzol", "coarse_dirt"], path=[("dirt_path", 5), ("coarse_dirt", 3),
                                                                              ("gravel", 1)],
        flowers=["poppy", "dandelion", "expanse:heather", "fern"], sign="spruce"),
    "limestone": dict(
        name="old_well_limestone", biomes=["jade_karst", "cloud_forest", "wisteria_vale"], procs="small_karst",
        stone=[("expanse:limestone_bricks", 4), ("expanse:mossy_limestone", 3), ("expanse:limestone", 2)],
        brick="expanse:polished_limestone", bstairs="expanse:limestone_brick", slab_="expanse:limestone_brick",
        wall="expanse:limestone_brick_wall", wood="expanse:redwood", log="expanse:redwood_log", roof="dark_oak",
        soil="grass_block", ground=["grass_block", "grass_block", "moss_block"],
        path=[("gravel", 4), ("expanse:limestone", 2), ("coarse_dirt", 2)],
        flowers=["azure_bluet", "lily_of_the_valley", "fern", "allium"], sign="bamboo"),
    "sandstone": dict(
        name="old_well_sandstone", biomes=["amber_steppe", "opal_dunes", "palm_coast"], procs="small_sand",
        stone=[("sandstone", 4), ("cut_sandstone", 2), ("smooth_sandstone", 2)],
        brick="chiseled_sandstone", bstairs="sandstone", slab_="cut_sandstone", wall="sandstone_wall",
        wood="acacia", log="acacia_log", roof="acacia", soil="coarse_dirt",
        ground=["coarse_dirt", "coarse_dirt", "sand"], path=[("coarse_dirt", 4), ("sand", 2), ("gravel", 1)],
        flowers=["dead_bush", "short_dry_grass", "short_dry_grass"], sign="acacia"),
    "mud": dict(
        name="old_well_mud", biomes=["willow_bayou"], procs="small_bayou",
        stone=[("mud_bricks", 4), ("packed_mud", 2), ("mossy_cobblestone", 1)],
        brick="mud_bricks", bstairs="mud_brick", slab_="mud_brick", wall="mud_brick_wall",
        wood="expanse:willow", log="expanse:willow_log", roof="spruce", soil="grass_block",
        ground=["mud", "grass_block", "grass_block"], path=[("mud", 2), ("coarse_dirt", 3), ("packed_mud", 1)],
        flowers=["blue_orchid", "fern", "fern"], sign="mangrove"),
}


def _curb(b, p, rng, c, top=2, broken=0.0):
    """3x3 curb ring round the shaft at (c, c): stone to y=top-1, slabs/walls on top."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            x, z = c + dx, c + dz
            b.set(x, 0, z, pick(rng, p["stone"]))
            if dx == 0 and dz == 0:
                continue
            for y in range(1, top):
                if rng.random() >= broken or y == 1:
                    b.set(x, y, z, pick(rng, p["stone"]))
            if rng.random() >= broken * 2:
                b.set(x, top, z, B(p["wall"]) if dx and dz else slab(p["slab_"]))


def _flagstones(b, p, rng, c, r=2.7):
    for (x, z) in ring(c, c, 1.6, r, (b.size[0], b.size[2])):
        if b.get(x, 0, z) is None and rng.random() < 0.8:
            b.set(x, 0, z, pick(rng, p["stone"] + [(p["brick"], 2)]))
            b.set(x, 1, z, AIR)


def _roof(b, p, c, y):
    """Little gable roof over the well (ridge along x)."""
    for x in range(c - 2, c + 3):
        b.set(x, y, c - 1, stairs(p["roof"], "south"))
        b.set(x, y, c + 1, stairs(p["roof"], "north"))
        b.set(x, y + 1, c, slab(p["roof"]))
        b.set(x, y, c, B(f"{p['log']}", axis="x") if abs(x - c) < 2 else stairs(p["roof"], "north", "top"))


def covered(p, seed):
    b = Build(f"{p['name']}_covered", 11, 9, 11, seed=seed)
    rng = b.rng
    c = 5
    _curb(b, p, rng, c, top=2)
    b.chest(c, 0, c, "north", f"expanse:chests/{p['name']}_hidden")    # wishing coins on the bottom
    b.set(c, 0, c, b.get(c, 0, c).with_(waterlogged=True), b.cells[(c, 0, c)][1])
    b.set(c, 1, c, B("water", level=0))
    b.set(c, 2, c, AIR)
    for x in (c - 1, c + 1):                         # posts and windlass
        for y in (3, 4):
            b.set(x, y, c, B(p["log"], axis="y"))
    _roof(b, p, c, 5)
    for y in (3, 4):
        b.set(c, y, c, B("iron_chain", axis="y"))
    b.set(c + 2, 4, c, B(f"{p['wood']}_fence"))       # crank handle
    _flagstones(b, p, rng, c)
    # trough, bench, bucket on a hook, flowers by the curb
    for x in (c + 2, c + 3):
        b.set(x, 1, c + 2, B("water_cauldron", level=rng.randint(1, 3)))
    for x in (c - 3, c - 2):
        b.set(x, 1, c + 3, slab(p["slab_"], "top"))
    b.barrel(c - 3, 1, c - 2, "up", f"expanse:chests/{p['name']}")
    b.set(c + 2, 1, c - 2, B(f"{p['wood']}_fence"))
    b.item_frame(c + 2, 1, c - 1, "south", item("bucket"))
    for (x, z) in ((c - 2, c - 1), (c + 2, c + 1), (c, c - 2)):
        plant(b, x, 1, z, rng.choice(p["flowers"]), p["soil"])
    ground_item(b, c - 2, 2, c + 3, book(BOOKS[p["name"]]), rotation=1)
    trail(b, [(c, c + 3), (c + 1, 9), (c, 10)], rng, p["path"], width=2, fade=0.5)
    flora(b, [(x, z) for x in range(11) for z in range(11)], rng, 0.18, p["ground"])
    return b


def dry(p, seed):
    b = Build(f"{p['name']}_dry", 11, 8, 11, seed=seed)
    rng = b.rng
    c = 5
    _curb(b, p, rng, c, top=2, broken=0.15)
    b.set(c, 1, c, AIR)
    b.set(c, 2, c, AIR)
    b.set(c, 1, c - 1, pick(rng, p["stone"]))         # the ladder hangs on the north side of the shaft
    b.set(c, 0, c - 1, pick(rng, p["stone"]))
    b.set(c, 1, c, B("ladder", facing="south"))
    b.jigsaw(c, 0, c, "down", name="minecraft:empty", target="expanse:well_shaft", pool=f"expanse:{p['name']}/cistern",
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north", joint="aligned")
    # the broken windlass: one post standing, one down across the flagstones
    for y in (3, 4):
        b.set(c - 1, y, c, B(p["log"], axis="y"))
    b.set(c - 1, 5, c, slab(p["roof"]))
    for z in (c + 2, c + 3, c + 4):
        b.set(c + 2, 1, z, B(p["log"], axis="z"))
    b.set(c + 1, 1, c + 2, B("iron_chain", axis="x"))
    _flagstones(b, p, rng, c)
    b.set(c - 3, 0, c, pick(rng, p["stone"]))
    b.set(c - 3, 1, c, B(p["brick"]))
    b.sign(c - 3, 1, c + 1, p["sign"], ["WELL DRY", "Do not drink.", "Do not climb", "down."],
           wall_facing="south")
    ground_item(b, c - 3, 2, c, item("bone"), rotation=1)
    trail(b, [(c, c + 3), (c - 1, 9), (c, 10)], rng, p["path"], width=1, fade=0.5)
    flora(b, [(x, z) for x in range(11) for z in range(11)], rng, 0.22, p["ground"])
    return b


def ruin(p, seed):
    b = Build(f"{p['name']}_ruin", 13, 7, 11, seed=seed)
    rng = b.rng
    c = 4
    _curb(b, p, rng, c + 1, top=2, broken=0.35)
    cx = c + 1
    b.set(cx, 0, cx, B("water", level=0))
    b.set(cx, 1, cx, AIR)
    for (x, y, z, st) in ((cx - 1, 1, cx + 2, B(p["log"], axis="z")), (cx - 1, 1, cx + 3, B(p["log"], axis="z")),
                          (cx + 2, 1, cx - 1, stairs(p["roof"], "east")), (cx + 2, 1, cx, slab(p["roof"])),
                          (cx + 3, 1, cx + 1, stairs(p["roof"], "west"))):
        b.set(x, y, z, st)
    # the long trough still holding water, an alms barrel, flowers in the broken curb
    for x in range(7, 12):
        for dz in (-1, 0, 1):
            z = 8 + dz
            b.set(x, 0, z, pick(rng, p["stone"]))
            inner = 7 < x < 11 and dz == 0
            b.set(x, 1, z, B("water", level=0) if inner else pick(rng, p["stone"]))
    b.barrel(11, 1, 5, "west", f"expanse:chests/{p['name']}")
    b.chest(1, 0, 8, "east", f"expanse:chests/{p['name']}_hidden")   # the well fund, under the flat stone
    b.set(1, 1, 8, slab(p["slab_"]))
    for (x, z) in ((cx, cx - 1), (cx - 1, cx), (cx + 1, cx + 1)):   # moss on the broken curb
        if b.get(x, 2, z) is None and b.get(x, 1, z) is not None:
            b.set(x, 2, z, B("moss_carpet"))
    ground_item(b, 10, 1, 4, book(BOOKS["old_well_ruin"]), rotation=3)
    trail(b, [(cx, cx + 3), (5, 9), (6, 10)], rng, p["path"], width=1, fade=0.4)
    flora(b, [(x, z) for x in range(13) for z in range(11)], rng, 0.3, p["ground"])
    return b


def cistern(p, seed):
    """Underground: a 2-wide-walled block of earth round a vaulted cistern, the shaft up its north side."""
    b = Build(f"{p['name']}_cistern", 9, 10, 9, seed=seed)
    rng = b.rng
    for x in range(9):
        for z in range(9):
            for y in range(10):
                b.set(x, y, z, pick(rng, [("dirt", 3), ("stone", 3), ("gravel", 1), ("andesite", 1)]))
    for x in range(2, 7):                             # the cistern room, y1..3
        for z in range(2, 7):
            b.set(x, 0, z, pick(rng, p["stone"]))
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
            if x in (2, 6) or z in (2, 6):
                b.set(x, 4, z, pick(rng, p["stone"]))
            else:
                b.set(x, 4, z, AIR)
                b.set(x, 5, z, pick(rng, p["stone"]))
    for (x, z) in ((3, 3), (4, 3), (3, 4)):           # the puddle of what water is left
        b.set(x, 0, z, B("water", level=0))
    for (x, z) in ((5, 5), (4, 5), (5, 4)):           # silt (archaeology)
        b.set(x, 0, z, B("gravel"))
    # shaft: from the top jigsaw (y=9) down the north side to the room
    sx, sz = 4, 1
    for y in range(1, 10):
        b.set(sx, y, sz, B("ladder", facing="south"))
        b.set(sx, y, sz - 1, pick(rng, p["stone"]))
    b.set(sx, 9, sz, AIR)
    b.jigsaw(sx, 9, sz, "up", name="expanse:well_shaft", target="minecraft:empty", pool="minecraft:empty",
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    for y in (1, 2, 3):
        b.set(sx, y, 2, AIR)
    # whoever fell in, the pots they kept here, the chest
    bones(b, [(5, 3), (3, 5)], rng, skull_chance=0.5)
    b.chest(6, 1, 4, "west", f"expanse:chests/{p['name']}_hidden")
    b.pot(6, 1, 6, "west", ("brick", "mourner_pottery_sherd", "brick", "brick"))
    b.pot(2, 1, 6, "east", ("brick", "brick", "brick", "brick"), loot=f"expanse:chests/{p['name']}")
    b.set(6, 1, 2, B("cobweb"))
    b.set(2, 3, 2, B("cobweb"))
    ground_item(b, 3, 1, 6, book(BOOKS["old_well_cistern"]), rotation=2)
    return b


def variant_module(key):
    p = PALETTES[key]
    name = p["name"]
    structure = dict(biomes=p["biomes"], size=1, max_distance=32, set="wayside", weight=2)

    def pools():
        s = sum(map(ord, name)) * 13
        return {"start": [Piece(covered(p, s + 1), 3, p["procs"]), Piece(dry(p, s + 2), 2, p["procs"]),
                          Piece(ruin(p, s + 3), 2, p["procs"])],
                "cistern": [Piece(cistern(p, s + 4), 1, "small_cistern")]}
    return name, structure, data_for(name, [p["procs"], "small_cistern"], ["old_well_archaeology"]), pools


NAME, STRUCTURE, DATA, pools = variant_module("cobble")
