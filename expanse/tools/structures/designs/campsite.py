"""Abandoned campsites: somebody stopped here, and something happened.

One design, four material palettes (separate structures, so tents and food
suit the biome):
  campsite         spruce, canvas      - redwood giants, heather moor, verdant peaks, lumen grove
  campsite_snow    spruce, furs        - frostbloom tundra, prismatic peaks
  campsite_steppe  acacia, dyed wool   - amber steppe, opal dunes, palm coast
  campsite_bamboo  bamboo, green cloth - wisteria vale, jade karst, cloud forest, willow bayou

start pool (3 variants)
  tent : ridge tent with a bedroll, a fire with the meal still on it, log seats,
         woodpile and axe, a drying rack of hides, the camper's diary on a stump;
         the valuables are buried under the bedroll (hidden chest)
  cart : a cart with a broken wheel and its cargo spilled across the track, a note
         nailed to a post ("gone for a wheelwright"); the strongbox is slung under
         the cart bed
  lean : a lean-to against a boulder, a spit over a cold firepit, a hunter's kit;
         a cache under a loose stone by the rock
"""
from blocks import B
from builder import Build, item, slab, trapdoor
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import a_tent, book, boulder, cart, clear, flora, ground_item, meal_fire, pick, ring, snow_cap, trail

PALETTES = {
    "forest": dict(
        name="campsite", biomes=["redwood_giants", "heather_moor", "verdant_peaks", "lumen_grove"],
        procs="small_wayside", wood="spruce", log="spruce_log", wools=["white", "brown", "light_gray"],
        bed="straw", rug="brown", foods=["expanse:venison", "potato", "cod", "salmon"], fence="spruce_fence",
        ground=["podzol", "coarse_dirt", "grass_block", "grass_block"], soil="grass_block",
        path=[("dirt_path", 6), ("coarse_dirt", 3), ("gravel", 1)],
        stones=["cobblestone", "mossy_cobblestone", "stone", "andesite"], rock=["stone", "andesite", "cobblestone",
                                                                               "mossy_cobblestone"],
        leaves="spruce_leaves", sign="spruce", town="Moorfold", hides=["leather", "rabbit_hide", "leather"],
        cargo=["pumpkin", "hay_block", "melon"], snow=False),
    "snow": dict(
        name="campsite_snow", biomes=["frostbloom_tundra", "prismatic_peaks"], procs="small_snow",
        wood="spruce", log="spruce_log", wools=["white", "light_gray", "brown"], bed="brown", rug="white",
        foods=["expanse:venison", "cod", "rabbit", "expanse:venison"], fence="spruce_fence",
        ground=["snow_block", "dirt", "snow_block"], soil="dirt",
        path=[("packed_ice", 1), ("snow_block", 3), ("gravel", 2), ("coarse_dirt", 2)],
        stones=["cobblestone", "stone", "andesite", "packed_ice"], rock=["stone", "andesite", "packed_ice", "stone"],
        leaves="spruce_leaves", sign="spruce", town="the frost camp", hides=["rabbit_hide", "leather", "white_wool"],
        cargo=["hay_block", "packed_ice", "hay_block"], snow=True),
    "steppe": dict(
        name="campsite_steppe", biomes=["amber_steppe", "opal_dunes", "palm_coast"], procs="small_sand",
        wood="acacia", log="acacia_log", wools=["orange", "red", "yellow"], bed="red", rug="orange",
        foods=["mutton", "potato", "beef", "rabbit"], fence="acacia_fence",
        ground=["coarse_dirt", "coarse_dirt", "sand"], soil="coarse_dirt",
        path=[("coarse_dirt", 4), ("gravel", 1), ("sand", 2)],
        stones=["sandstone", "cut_sandstone", "smooth_sandstone", "cobblestone"],
        rock=["sandstone", "smooth_sandstone", "terracotta", "sandstone"],
        leaves="acacia_leaves", sign="acacia", town="Amber Well", hides=["leather", "rabbit_hide", "red_wool"],
        cargo=["melon", "hay_block", "terracotta"], snow=False),
    "bamboo": dict(
        name="campsite_bamboo", biomes=["wisteria_vale", "jade_karst", "cloud_forest", "willow_bayou"],
        procs="small_karst", wood="bamboo", log="jungle_log", wools=["lime", "green", "cyan"], bed="straw",
        rug="green", foods=["salmon", "cod", "potato", "carrot"], fence="bamboo_fence",
        ground=["grass_block", "moss_block", "grass_block"], soil="grass_block",
        path=[("gravel", 3), ("coarse_dirt", 3), ("dirt_path", 2)],
        stones=["expanse:limestone", "expanse:mossy_limestone", "cobblestone", "mossy_cobblestone"],
        rock=["expanse:limestone", "expanse:mossy_limestone", "expanse:limestone", "stone"],
        leaves="jungle_leaves", sign="bamboo", town="Jade Ferry", hides=["leather", "rabbit_hide", "paper"],
        cargo=["melon", "hay_block", "pumpkin"], snow=False),
}


def _log(p, axis="y"):
    return B(p["log"], axis=axis)


def _plank_slab(p, t="bottom"):
    return slab(p["wood"] if p["wood"] != "bamboo" else "bamboo_mosaic", t)


def _cargo(rng, p):
    c = rng.choice(p["cargo"])
    return B(c, axis=rng.choice(["x", "y", "z"])) if c == "hay_block" else B(c)


def _tent(p, seed):
    b = Build(f"{p['name']}_tent", 13, 7, 13, seed=seed)
    rng = b.rng
    wool = rng.choice(p["wools"])
    # tent along z, open to the south, its back to the north-west corner
    a_tent(b, 1, 2, 4, "z", wool, closed_back=False, wide=True)   # walk-in tent, door south to the fire
    for x in (2, 3, 4):
        b.set(x, 1, 2, B(f"{wool}_wool"))
    b.set(3, 2, 2, B(f"{wool}_wool"))
    b.bed(2, 1, 4, "north", p["bed"])                 # bedroll, head to the back of the tent
    b.chest(2, 0, 3, "north", f"expanse:chests/{p['name']}_hidden")   # buried under the bedroll
    b.set(3, 1, 4, B(f"{p['rug']}_carpet"))
    b.set(3, 1, 5, B(f"{p['rug']}_carpet"))
    b.set(4, 1, 4, slab(f"{p['rug']}_wool"))          # a rolled pack
    b.set(3, 2, 3, B("lantern", hanging=True))         # hung from the ridge
    for y in (1, 2, 3):                                # lantern pole by the door
        b.set(0, y, 6, B(p["fence"]))
    b.set(0, 4, 6, B("lantern"))
    for (x, z) in ((0, 1), (6, 1), (6, 6)):            # tent pegs
        b.set(x, 1, z, B(f"{p['wood']}_button", face="floor", facing="north"))
    # the camper's pack and the fire with the meal still on it
    b.barrel(4, 1, 3, "up", f"expanse:chests/{p['name']}")
    fx, fz = 7, 7
    lit = rng.random() < 0.5
    meal_fire(b, fx, 1, fz, [rng.choice(p["foods"]), rng.choice(p["foods"])], lit=lit, done=(310, 120, 0, 0))
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1)):
        b.set(fx + dx, 0, fz + dz, pick(rng, p["stones"]))
    b.set(fx, 0, fz, B("coarse_dirt"))
    b.set(fx - 1, 1, fz + 2, _log(p, "x"))             # log seats
    b.set(fx, 1, fz + 2, _log(p, "x"))
    b.set(fx + 2, 1, fz, _log(p, "z"))
    b.set(fx + 2, 1, fz - 1, _log(p, "z"))
    b.set(fx + 1, 1, fz + 1, B("water_cauldron", level=2) if not p["snow"] else B("cauldron"))
    b.set(fx - 2, 1, fz - 1, _log(p))                  # the diary left open on a stump
    ground_item(b, fx - 2, 2, fz - 1, book(BOOKS[p["name"]]), rotation=2)
    b.cushion(fx - 1, 1, fz - 1, color=rng.choice(p["wools"]))
    # woodpile and a chopping block with the axe still in it
    for x in (10, 11):
        for y in (1, 2):
            if not (y == 2 and x == 11 and rng.random() < 0.5):
                b.set(x, y, 2, _log(p, "z"))
        b.set(x, 1, 3, _log(p, "z"))
    b.set(11, 1, 5, _log(p))
    ground_item(b, 11, 2, 5, item("iron_axe" if rng.random() < 0.4 else "stone_axe"), rotation=1)
    # drying rack: a fence frame with hides pinned to it
    rx, rz = 9, 10
    for x in range(rx, rx + 3):
        b.set(x, 2, rz, B(p["fence"]))
    b.set(rx, 1, rz, B(p["fence"]))
    b.set(rx + 2, 1, rz, B(p["fence"]))
    for i, x in enumerate((rx, rx + 1, rx + 2)):
        if rng.random() < 0.8:
            b.item_frame(x, 2, rz + 1, "south", item(p["hides"][i % len(p["hides"])]), rotation=rng.randrange(8))
    trail(b, [(4, 7), (5, 9), (6, 12)], rng, p["path"], width=1, fade=0.4)
    trail(b, [(6, 6), (8, 4), (12, 3)], rng, p["path"], width=1, fade=0.5)
    clear(b, 5, 1, 4, 10, 3, 9)
    flora(b, ring(7, 7, 4.2, 7.5, (13, 13)), rng, 0.3, p["ground"])
    if p["snow"]:
        snow_cap(b, rng, 0.7)
    return b


def _cart(p, seed):
    b = Build(f"{p['name']}_cart", 14, 6, 13, seed=seed)
    rng = b.rng
    # the track, rutted, running east-west under the cart
    trail(b, [(0, 5), (6, 6), (13, 5)], rng, p["path"], width=2, fade=0.8, edge=0.2)
    trail(b, [(13, 5), (8, 6)], rng, p["path"], width=1, fade=0.8)
    cells, sag, lost = cart(b, 5, 5, "x", p["wood"], rng, broken_corner=1)
    # the lost wheel lies flat by the cart, a spare propped against the side
    b.set(6, 1, 8, trapdoor(p["wood"], "south", "bottom", False))
    # strongbox slung under the cart bed (open it from the side)
    b.chest(6, 1, 5, "north", f"expanse:chests/{p['name']}_hidden")
    # cargo: barrels on the bed, more spilled toward the sag, cracked pots and a split sack
    b.barrel(5, 2, 6, "up", f"expanse:chests/{p['name']}")
    b.set(7, 2, 5, _cargo(rng, p) if rng.random() < 0.7 else B(f"{rng.choice(p['wools'])}_wool"))
    b.set(4, 1, 7, B("barrel", facing="east"))
    b.set(3, 1, 8, B("barrel", facing="south"))
    b.set(5, 1, 8, _cargo(rng, p))
    b.pot(2, 1, 6, "east", ("brick", "brick", "brick", "brick"))
    b.set(2, 1, 6, b.get(2, 1, 6).with_(cracked=True), b.cells[(2, 1, 6)][1])
    b.set(3, 1, 4, B(f"{rng.choice(p['wools'])}_wool_slab", type="bottom"))
    for (x, z) in ((2, 7), (4, 9), (1, 5)):
        if rng.random() < 0.7:
            ground_item(b, x, 1, z, item(rng.choice(["apple", "potato", "carrot", "wheat", "bread"])),
                        rotation=rng.randrange(8))
    # where the shafts hang down: the harness, and the note on a post
    b.set(10, 1, 7, B(p["fence"]))
    b.set(10, 2, 7, B(p["fence"]))
    b.sign(10, 2, 8, p["sign"], ["", "Back soon", "", ""], wall_facing="south")
    # the carter's overnight fire and bedroll
    b.campfire(11, 1, 10, lit=False)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        b.set(11 + dx, 0, 10 + dz, pick(rng, p["stones"]))
    b.bed(9, 1, 11, "west", p["bed"])
    b.set(12, 1, 11, _log(p, "x"))
    ground_item(b, 12, 2, 11, book(BOOKS[p["name"]]), rotation=3)
    clear(b, 1, 1, 3, 12, 3, 11)
    flora(b, [(x, z) for x in range(14) for z in range(13) if z < 3 or z > 9], rng, 0.25, p["ground"])
    if p["snow"]:
        snow_cap(b, rng, 0.7)
    return b


def _lean_to(p, seed):
    b = Build(f"{p['name']}_lean", 12, 7, 11, seed=seed)
    rng = b.rng
    # the boulder the shelter leans on (north side)
    boulder(b, 2, 1, rng, p["rock"], w=6, d=2, h=4, moss="moss_carpet" if not p["snow"] else None)
    boulder(b, 3, 0, rng, p["rock"], w=4, d=1, h=3)
    # lean-to: posts at the front, a sloping roof of slabs down to the rock
    for x in (2, 7):
        b.set(x, 1, 5, B(p["fence"]))
        b.set(x, 2, 5, B(p["fence"]))
    for x in range(1, 9):
        b.set(x, 3, 5, slab(p["wood"] if p["wood"] != "bamboo" else "bamboo_mosaic", "bottom"))
        b.set(x, 3, 4, slab(p["wood"] if p["wood"] != "bamboo" else "bamboo_mosaic", "top"))
        b.set(x, 4, 3, slab(p["wood"] if p["wood"] != "bamboo" else "bamboo_mosaic", "bottom"))
        if rng.random() < 0.5:
            b.set(x, 4, 4, B(p["leaves"], persistent=True))
    clear(b, 2, 1, 3, 7, 2, 4)
    b.bed(4, 1, 3, "east", p["bed"])
    b.set(6, 1, 3, B(f"{p['rug']}_carpet"))
    b.set(6, 1, 4, B(f"{p['rug']}_carpet"))
    b.set(2, 1, 4, B("lantern"))
    b.barrel(7, 1, 4, "west", f"expanse:chests/{p['name']}")
    # firepit with a spit across it
    fx, fz = 5, 7
    meal_fire(b, fx, 1, fz, [rng.choice(p["foods"])], lit=False, done=(200, 0, 0, 0))
    b.set(fx - 1, 1, fz, B(p["fence"]))
    b.set(fx + 1, 1, fz, B(p["fence"]))
    b.set(fx - 1, 2, fz, B(p["fence"]))
    b.set(fx + 1, 2, fz, B(p["fence"]))
    b.set(fx, 2, fz, B("iron_chain", axis="x"))
    for dx, dz in ((0, 1), (1, 1), (-1, 1), (2, 0)):
        b.set(fx + dx, 0, fz + dz, pick(rng, p["stones"]))
    # hunter's kit: a stump with the knife and the tally, a rack of hides, a bow on the rock
    b.set(8, 1, 8, _log(p))
    ground_item(b, 8, 2, 8, book(BOOKS[p["name"]]), rotation=1)
    b.set(9, 1, 7, _log(p, "x"))
    b.item_frame(2, 2, 2, "south", item(rng.choice(["bow", "fishing_rod", "crossbow"])))
    for z in (7, 8, 9):
        b.set(1, 1, z, B(p["fence"]))
        b.set(1, 2, z, B(p["fence"]))
    b.item_frame(2, 2, 8, "east", item(p["hides"][0]), rotation=1)
    b.item_frame(2, 1, 9, "east", item(p["hides"][1]))
    # the cache: a loose slab at the foot of the boulder over a buried chest
    b.chest(9, 0, 2, "south", f"expanse:chests/{p['name']}_hidden")
    b.set(9, 1, 2, slab(rng.choice(["cobblestone", "mossy_cobblestone"]) if not p["snow"] else "cobblestone"))
    trail(b, [(5, 6), (4, 8), (6, 10)], rng, p["path"], width=1, fade=0.35)
    clear(b, 3, 1, 6, 9, 3, 9)
    flora(b, [(x, z) for x in range(12) for z in range(11) if z > 5 or x > 8], rng, 0.22, p["ground"])
    if p["snow"]:
        snow_cap(b, rng, 0.8)
    return b


def variant_module(key):
    p = PALETTES[key]
    name = p["name"]
    structure = dict(biomes=p["biomes"], size=1, max_distance=32, set="wayside", weight=3)

    def pools():
        s = sum(map(ord, name)) * 7
        return {"start": [Piece(_tent(p, s + 1), 3, p["procs"]), Piece(_cart(p, s + 2), 2, p["procs"]),
                          Piece(_lean_to(p, s + 3), 2, p["procs"])]}
    return name, structure, data_for(name, [p["procs"]]), pools


NAME, STRUCTURE, DATA, pools = variant_module("forest")
