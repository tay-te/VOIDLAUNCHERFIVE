"""Wayside shrines: the little roadside devotions travellers keep up.

One design, three material palettes (separate structures so each biome gets
its own stone and timber):
  wayside_shrine        timber & cobble  - heather moor, redwood giants, verdant peaks
  wayside_shrine_karst  limestone        - jade karst, wisteria vale, cloud forest
  wayside_shrine_sand   sandstone        - amber steppe, opal dunes, palm coast

start pool (3 variants)
  post   : a roofed post shrine (in limestone a stone lantern) on a stepped plinth,
           candles on the offering stone, a cushion worn flat by knees, an alms
           barrel; what was given is kept under the offering stone (hidden chest)
  niche  : a drystone wall with an arched niche, offering pots that hold coins,
           a bench and a hanging lantern; a cupboard is set into the back of the wall
  cross  : a crossroads signpost with a lantern, milestone, a traveller's pack
           and notes left on the bench; a trapdoor in the grass hides a stash
"""
from blocks import AIR, B
from builder import Build, item, slab, stairs, trapdoor
from pieces import Feature, Piece
from small_data import BOOKS, data_for
from smallkit import book, clear, flora, ground_item, pick, plant, ring, rubble, trail, vec

BASE = "wayside_shrine"

PALETTES = {
    "timber": dict(
        name="wayside_shrine", biomes=["heather_moor", "redwood_giants", "verdant_peaks"], procs="small_wayside",
        log="spruce_log", post_wall=None, planks="spruce_planks", wood="spruce", roof="dark_oak",
        stone=[("cobblestone", 4), ("mossy_cobblestone", 2), ("stone", 2), ("andesite", 1)],
        brick="stone_bricks", bstairs="stone_brick", cobble_stairs="cobblestone", slab_="cobblestone",
        wall="cobblestone_wall", ledge="polished_andesite", fence="spruce_fence",
        ground=["podzol", "coarse_dirt", "grass_block", "grass_block"], soil="grass_block",
        path=[("dirt_path", 6), ("coarse_dirt", 3), ("gravel", 1)],
        pots=["expanse:potted_heather", "potted_poppy", "potted_fern", "expanse:potted_edelweiss"],
        offer=["expanse:heather", "poppy", "dandelion"], candle="white_candle", icon="meditative", sign="spruce",
        sign_lines=["Keep a light", "for those who", "walk the moor", "by night."],
        places=["Moorfold", "Old Barrow", "Tarn Cross", "Redwood Hold"], tree="minecraft:spruce_checked"),
    "karst": dict(
        name="wayside_shrine_karst", biomes=["jade_karst", "wisteria_vale", "cloud_forest"], procs="small_karst",
        log="expanse:redwood_log", post_wall="expanse:limestone_brick_wall", planks="expanse:redwood_planks",
        wood="expanse:redwood", roof="dark_oak",
        stone=[("expanse:limestone_bricks", 4), ("expanse:limestone", 2), ("expanse:mossy_limestone", 2)],
        brick="expanse:polished_limestone", bstairs="expanse:limestone_brick", cobble_stairs="expanse:limestone",
        slab_="expanse:limestone_brick", wall="expanse:limestone_brick_wall", ledge="expanse:chiseled_limestone",
        fence="bamboo_fence", ground=["grass_block", "grass_block", "moss_block"], soil="grass_block",
        path=[("gravel", 4), ("expanse:limestone", 2), ("coarse_dirt", 2)],
        pots=["potted_bamboo", "potted_azure_bluet", "potted_lily_of_the_valley", "expanse:potted_wisteria_sapling"],
        offer=["azure_bluet", "lily_of_the_valley", "allium"], candle="red_candle", icon="kebab", sign="bamboo",
        sign_lines=["Rest a while.", "The river", "will wait", "for you."],
        places=["Stone Forest", "Jade Ferry", "Mist Steps", "Wisteria Bridge"], tree="minecraft:cherry_checked"),
    "sand": dict(
        name="wayside_shrine_sand", biomes=["amber_steppe", "opal_dunes", "palm_coast"], procs="small_sand",
        log="acacia_log", post_wall=None, planks="acacia_planks", wood="acacia", roof="acacia",
        stone=[("cut_sandstone", 3), ("sandstone", 3), ("smooth_sandstone", 2)],
        brick="chiseled_sandstone", bstairs="sandstone", cobble_stairs="smooth_sandstone", slab_="cut_sandstone",
        wall="sandstone_wall", ledge="chiseled_sandstone", fence="acacia_fence",
        ground=["coarse_dirt", "coarse_dirt", "sand"], soil="coarse_dirt",
        path=[("coarse_dirt", 4), ("gravel", 1), ("sand", 2)],
        pots=["potted_dead_bush", "potted_cactus", "potted_orange_tulip", "expanse:potted_palm_sapling"],
        offer=["dead_bush", "short_dry_grass", "orange_tulip"], candle="orange_candle", icon="wasteland", sign="acacia",
        sign_lines=["Water is life.", "Share yours", "with the", "next traveller."],
        places=["Amber Well", "Opal Gate", "Palm Strand", "Sunstone"], tree="minecraft:acacia_checked"),
}


def _w(p, kind, **kw):
    """Wood block of the palette's timber: stairs / slab / fence / trapdoor."""
    w = p["wood"]
    return B(f"{w}_{kind}", **kw)


def _stone(rng, p):
    return pick(rng, p["stone"])


def _post_shrine(p, seed):
    """Roofed post shrine on a stepped plinth (or, in limestone, a stone lantern)."""
    b = Build(f"{p['name']}_post", 9, 9, 12, seed=seed)
    rng = b.rng
    cx, cz = 4, 4
    # ground course: an irregular pad of stones under the plinth, then the worn approach
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            corner = abs(x - cx) == 2 and abs(z - cz) == 2
            if (abs(x - cx) <= 1 and abs(z - cz) <= 1) or (not corner and rng.random() < 0.55):
                b.set(x, 0, z, _stone(rng, p))
    trail(b, [(cx, cz + 2), (cx, 7), (cx + rng.choice([-1, 1]), 9), (cx, 11)], rng, p["path"], width=1)
    trail(b, [(cx + 2, cz + 2), (7, 6), (8, 8)], rng, p["path"], width=1, fade=0.3)
    clear(b, cx - 2, 1, cz - 2, cx + 2, 6, cz + 3)
    # stepped plinth with the offering stone in front
    b.set(cx, 1, cz, B(p["brick"]))
    b.set(cx - 1, 1, cz, stairs(p["cobble_stairs"], "east"))
    b.set(cx + 1, 1, cz, stairs(p["cobble_stairs"], "west"))
    b.set(cx, 1, cz - 1, stairs(p["cobble_stairs"], "south"))
    for x in (cx - 1, cx + 1):
        if rng.random() < 0.7:
            b.set(x, 1, cz - 1, slab(p["slab_"]))
    b.set(cx, 1, cz + 1, B(p["ledge"]))
    b.chest(cx, 0, cz + 1, "south", f"expanse:chests/{p['name']}_hidden")   # kept under the offering stone
    b.set(cx, 2, cz + 1, B(p["candle"], candles=3, lit=True))
    if p["post_wall"]:
        # stone lantern: wall shaft, slab table, a lantern in an open firebox, a stepped cap
        b.set(cx, 2, cz, B(p["post_wall"]))
        b.set(cx, 3, cz, B(p["post_wall"]))
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                b.set(cx + dx, 4, cz + dz, slab(p["bstairs"], "top") if (dx or dz) else B(p["brick"]))
        b.set(cx, 5, cz, B("lantern"))
        for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            b.set(cx + dx, 5, cz + dz, B(p["post_wall"]))
        for dx, dz in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            b.set(cx + dx, 5, cz + dz, AIR)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx or dz:
                    f = "south" if dz == -1 else "north" if dz == 1 else "east" if dx == -1 else "west"
                    b.set(cx + dx, 6, cz + dz, stairs(p["bstairs"], f))
        b.set(cx, 6, cz, B(p["brick"]))
        b.set(cx, 7, cz, slab(p["slab_"]))
    else:
        for y in (2, 3, 4):
            b.set(cx, y, cz, B(p["log"], axis="y"))
        # the shrine box: planks with the icon on its face, side panels, a little hip roof
        b.set(cx, 5, cz, B(p["planks"]))
        b.painting(cx, 5, cz + 1, "south", p["icon"])
        b.set(cx - 1, 5, cz, _w(p, "trapdoor", facing="west", half="bottom", open=True))
        b.set(cx + 1, 5, cz, _w(p, "trapdoor", facing="east", half="bottom", open=True))
        b.set(cx, 5, cz - 1, _w(p, "trapdoor", facing="north", half="bottom", open=True))
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx or dz:
                    f = "south" if dz == -1 else "north" if dz == 1 else "east" if dx == -1 else "west"
                    b.set(cx + dx, 6, cz + dz, stairs(p["roof"], f))
        b.set(cx, 6, cz, B(p["planks"]))
        b.set(cx, 7, cz, slab(p["roof"]))
        b.set(cx - 1, 5, cz + 1, B("lantern", hanging=True))
        b.sign(cx, 3, cz + 1, p["sign"], p["sign_lines"], wall_facing="south",
               back=["What is given", "is kept", "beneath the", "offering stone."])
    # offerings: alms barrel with a candle, flowers in pots and in the grass, a kneeler's cushion
    b.barrel(cx + 1, 1, cz + 1, "up", f"expanse:chests/{p['name']}")
    b.set(cx + 1, 2, cz + 1, B("candle", candles=rng.choice([1, 2]), lit=rng.random() < 0.6))
    b.set(cx - 1, 1, cz + 1, B(rng.choice(p["pots"])))
    plant(b, cx - 2, 1, cz + 1, rng.choice(p["offer"]), p["soil"])
    plant(b, cx - 1, 1, cz + 2, rng.choice(p["offer"]), p["soil"])
    b.cushion(cx, 1, cz + 2, color=rng.choice(["brown", "red", "gray"]))
    # a prayer stack of stones and bunches of flowers left at the side
    sx, sz = cx + 2, cz - 1
    b.set(sx, 0, sz, _stone(rng, p))
    b.set(sx, 1, sz, _stone(rng, p))
    b.set(sx, 2, sz, slab(p["slab_"]))
    flora(b, ring(cx, cz, 2.6, 5.2, (9, 12)), rng, 0.32, p["ground"])
    return b


def _niche_shrine(p, seed):
    """A drystone wall with an arched niche; offering pots, a bench, a lantern; a cupboard at the back."""
    b = Build(f"{p['name']}_niche", 11, 8, 10, seed=seed)
    rng = b.rng
    zb, zf = 3, 4                                     # wall back / front rows (visitors come from the south)
    heights = {1: 2, 2: 3, 3: 4, 4: 5, 5: 5, 6: 5, 7: 4, 8: 3, 9: 2}
    for x, h in heights.items():
        h -= 1 if rng.random() < 0.3 and x not in (4, 5, 6) else 0
        for z in (zb, zf):
            b.set(x, 0, z, _stone(rng, p))
            for y in range(1, h + 1):
                b.set(x, y, z, _stone(rng, p))
            if x not in (4, 5, 6) and rng.random() < 0.55:
                b.set(x, h + 1, z, slab(p["slab_"]) if rng.random() < 0.7 else B("moss_carpet"))
    # the niche (x=5) in the front face: ledge, recessed arch, the icon on its back wall, candles
    b.set(5, 1, zf, B(p["ledge"]))
    b.set(5, 2, zf, B(p["candle"], candles=4, lit=True))
    b.set(5, 3, zf, AIR)
    b.painting(5, 3, zf, "south", p["icon"])
    b.set(5, 4, zf, stairs(p["bstairs"], "north", "top"))
    for x in (4, 5, 6):                               # a gabled coping over the niche
        b.set(x, 6, zb, stairs(p["bstairs"], "south"))
        b.set(x, 6, zf, stairs(p["bstairs"], "north"))
    b.set(3, 5, zf, stairs(p["bstairs"], "east"))
    b.set(7, 5, zf, stairs(p["bstairs"], "west"))
    # offering pots (one holds the coins), flowers at the foot of the wall
    b.pot(4, 1, zf + 1, "south", ("brick", "heart_pottery_sherd", "brick", "brick"),
          loot=f"expanse:chests/{p['name']}")
    b.set(6, 1, zf + 1, B(rng.choice(p["pots"])))
    b.set(5, 0, zf + 1, _stone(rng, p))
    for x in (2, 3, 7, 8):
        if rng.random() < 0.75:
            plant(b, x, 1, zf + 1, rng.choice(p["offer"]), p["soil"])
    # stone bench facing the niche, a lamp post with a hanging lantern
    for x in (4, 5, 6):
        b.set(x, 0, 7, _stone(rng, p))
    b.set(4, 1, 7, stairs(p["bstairs"], "east"))
    b.set(5, 1, 7, slab(p["slab_"]))
    b.set(6, 1, 7, stairs(p["bstairs"], "west"))
    for y in (1, 2, 3):
        b.set(9, y, 6, B(p["fence"]))
    b.set(8, 3, 6, B(p["fence"]))
    b.set(8, 2, 6, B("lantern", hanging=True))
    b.set(9, 0, 6, _stone(rng, p))
    # the back: vines, and a cupboard set into the wall (hidden chest) behind a loose stone
    b.chest(7, 1, zb, "north", f"expanse:chests/{p['name']}_hidden")
    b.set(7, 2, zb, slab(p["slab_"], "top"))          # a slab lintel over the cupboard, so the lid lifts
    for x in (2, 4, 6, 8):
        if rng.random() < 0.7:
            for y in range(rng.randint(1, 3), 0, -1):
                if b.get(x, y, zb) is not None:
                    b.set(x, y, zb - 1, B("vine", south=True))
    trail(b, [(5, 6), (5, 8), (rng.choice([4, 6]), 9)], rng, p["path"], width=2, fade=0.5)
    clear(b, 1, 1, 5, 9, 4, 9)
    clear(b, 1, 1, 0, 9, 3, 2)
    rubble(b, [(1, 5), (9, 2), (10, 4), (0, 3), (2, 2)], rng, [s for s, _ in p["stone"]],
           loose=[slab(p["slab_"]), stairs(p["bstairs"], "north")], density=0.6)
    flora(b, ring(5, 3.5, 1.5, 6.5, (11, 10)), rng, 0.25, p["ground"])
    return b


def _crossroads(p, seed):
    """A crossroads by a field-wall corner: signpost and lantern, milestone, a post shrine in the wall,
    a lone tree over a bench with a traveller's pack and notes; a trapdoor in the grass hides a stash."""
    b = Build(f"{p['name']}_cross", 15, 7, 15, seed=seed)
    rng = b.rng
    c = 7
    for end, wide in (((c, 0), 2), ((c - 1, 14), 2), ((0, c), 1), ((14, c + 1), 1)):   # four arms fading out
        jx, jz = (rng.choice([-1, 0, 1]), 0) if end[0] in (c, c - 1) else (0, rng.choice([-1, 0, 1]))
        mid = ((c + end[0]) // 2 + jx, (c + end[1]) // 2 + jz)
        trail(b, [(c, c), mid, end], rng, p["path"], width=wide, fade=0.45)
    # field wall in the north-east quadrant, broken in places, with a stile and the post shrine at its corner
    wall_cells = [(x, c - 2) for x in range(c + 2, 15)] + [(c + 2, z) for z in range(0, c - 2)]
    for i, (x, z) in enumerate(wall_cells):
        if rng.random() < 0.12:
            b.set(x, 0, z, _stone(rng, p))           # a gap with its footing left
            b.set(x, 1, z, slab(p["slab_"]) if rng.random() < 0.5 else AIR)
            continue
        b.set(x, 0, z, _stone(rng, p))
        h = 2 if rng.random() < 0.6 else 1
        for y in range(1, h + 1):
            b.set(x, y, z, _stone(rng, p))
        if rng.random() < 0.4:
            b.set(x, h + 1, z, slab(p["slab_"]))
    b.set(12, 1, c - 1, stairs(p["bstairs"], "north"))          # stile over the wall
    b.set(12, 1, c - 3, stairs(p["bstairs"], "south"))
    b.set(12, 2, c - 2, slab(p["slab_"]))
    sx, sz = c + 2, c - 2                                       # shrine post on the wall corner
    b.set(sx, 1, sz, B(p["brick"]))
    b.set(sx, 2, sz, B(p["post_wall"] or p["fence"]))
    b.set(sx, 3, sz, B(p["planks"]))
    b.item_frame(sx - 1, 3, sz, "west", item(rng.choice(["gold_nugget", "emerald", "amethyst_shard"])))
    b.set(sx, 4, sz, slab(p["roof"]))
    b.set(sx - 1, 4, sz, stairs(p["roof"], "east"))
    b.set(sx + 1, 4, sz, stairs(p["roof"], "west"))
    b.set(sx - 1, 1, sz + 1, B(p["brick"]))
    b.set(sx - 1, 2, sz + 1, B(p["candle"], candles=2, lit=True))
    # signpost on the south-west corner: arms point down each road, a lantern on top
    px, pz = c - 2, c + 2
    b.set(px, 0, pz, _stone(rng, p))
    for y in (1, 2, 3):
        b.set(px, y, pz, B(p["fence"]))
    b.set(px, 4, pz, B("lantern"))
    names = list(p["places"])
    rng.shuffle(names)
    for (f, nm, arrow, y) in (("north", names[0], "^ ^ ^", 3), ("east", names[1], "> > >", 3),
                              ("west", names[2], "< < <", 2), ("south", names[3], "v v v", 2)):
        dx, dz = vec(f)
        b.sign(px + dx, y, pz + dz, p["sign"], ["", nm, arrow, ""], wall_facing=f)
    # milestone by the north road
    b.set(c - 2, 0, 2, _stone(rng, p))
    b.set(c - 2, 1, 2, B(p["brick"]))
    b.set(c - 2, 2, 2, slab(p["slab_"]))
    b.sign(c - 2, 1, 3, p["sign"], ["", names[0], f"{rng.randint(2, 9)} leagues", ""], wall_facing="south")
    plant(b, c - 3, 1, 2, rng.choice(p["offer"]), p["soil"])
    # the lone tree (vanilla feature at a jigsaw) shading a log bench, the traveller's pack and notes
    tx, tz = 11, 11
    b.jigsaw(tx, 0, tz, "up", pool=f"expanse:{p['name']}/tree", final=f"minecraft:{p['soil']}")
    b.set(tx, 1, tz, AIR)
    b.set(tx - 2, 1, tz - 1, B(p["log"], axis="x"))
    b.set(tx - 3, 1, tz - 1, B(p["log"], axis="x"))
    b.barrel(tx - 1, 1, tz + 1, "north", f"expanse:chests/{p['name']}")
    b.set(tx - 1, 2, tz + 1, B("candle", candles=1, lit=False))
    b.set(tx + 1, 1, tz - 2, B(p["brick"]))
    ground_item(b, tx + 1, 2, tz - 2, book(BOOKS[p["name"]]), rotation=1)
    b.set(tx - 2, 1, tz + 1, B(rng.choice(p["pots"])))
    # the stash: a trapdoor lying in the grass over a buried chest, behind the milestone
    b.chest(2, 0, 3, "north", f"expanse:chests/{p['name']}_hidden")
    b.set(2, 1, 3, trapdoor(p["wood"], "north", "bottom", False))
    plant(b, 1, 1, 3, rng.choice(p["offer"]), p["soil"])
    plant(b, 2, 1, 4, rng.choice(p["offer"]), p["soil"])
    clear(b, 8, 1, 8, 13, 3, 13)
    # flowers gather along the wall foot and round the posts, thin elsewhere
    near = [(x, z) for (x, z) in ((x, z) for x in range(15) for z in range(15))
            if any(abs(x - wx) + abs(z - wz) == 1 for (wx, wz) in wall_cells)]
    flora(b, near, rng, 0.45, p["ground"])
    flora(b, [(px + dx, pz + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)], rng, 0.5, p["ground"])
    flora(b, [(x, z) for x in range(15) for z in range(15)], rng, 0.06, p["ground"])
    return b


def variant_module(key):
    """(NAME, STRUCTURE, DATA, pools) for one palette - the palette modules are one-liners."""
    p = PALETTES[key]
    name = p["name"]
    structure = dict(biomes=p["biomes"], size=1, max_distance=32, set="wayside", weight=3)

    def pools():
        s = sum(map(ord, name))
        return {"start": [Piece(_post_shrine(p, s + 1), 3, p["procs"]),
                          Piece(_niche_shrine(p, s + 2), 2, p["procs"]),
                          Piece(_crossroads(p, s + 3), 2, p["procs"])],
                "tree": [Feature(p["tree"])]}
    return name, structure, data_for(name, [p["procs"]]), pools


NAME, STRUCTURE, DATA, pools = variant_module("timber")
