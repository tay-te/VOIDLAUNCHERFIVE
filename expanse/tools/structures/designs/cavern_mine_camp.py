"""Abandoned miners' camp on the floor of a deep cavern hall (UNDERGROUND).

start  camp
  A timber headframe stands in the middle of the hall: four posts, braces and a sheave wheel,
  the lift cage hanging on its chains and, at the top, the stub of the boxed shaft it once
  climbed through. Round it the crew's tents and bedrolls, the forge (blast furnace, anvil,
  lava cauldron), ore carts on a rail spur and heaps of sorted ore. Lanterns, a campfire and
  glow lichen are the only light. The paymaster's strongbox is walled in by powder barrels.
tracks -> workings (rigid; tracks keep to the hall floor)
  ore face / collapsed adit with a cave spider nest / supply depot / sorting floor
"""
import math
import random

from blocks import AIR, B
from builder import Build, prune_unsupported
from furnish import chair, shelf_items, table
from loot import book, empty, item as li, pool, table as loot_table
from pieces import Empty, Piece
from processors import OVERGROWTH, STONE_WEATHER, aged_wood, plist, rule
from smallkit import a_tent, meal_fire
from townkit import (CAVE_STONE, crane, goods_stack, hanging_lamp, journal, lamp, lichen, lore_entry, minecart,
                     rail_line, rock_mound, slab, stairs, trapdoor, wheel)

NAME = "cavern_mine_camp"
STRUCTURE = dict(biomes=["#minecraft:is_overworld"], cavern=True, set="cavern_halls", weight=3, size=3,
                 max_distance=40)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
CART = f"expanse:chests/{NAME}_cart"
P_TRACKS = f"expanse:{NAME}/tracks"
P_WORK = f"expanse:{NAME}/workings"
J_TRACK = "expanse:mine_track"
J_WORK = "expanse:mine_work"
PROC = NAME
W = "spruce"
SW = B("stripped_spruce_log", axis="y")

BOOK = {
    "title": "Deep Lift Day Book", "author": "Shift captain Morwenna",
    "pages": [
        "Lift down at first bell with nine aboard. The hall below the shaft is bigger than the village above "
        "it, and darker than anything. We set the camp by the cage so the way home is always in sight.",
        "Good iron in the east face, copper in the floor, coal enough to keep the forge lit. The lamps eat "
        "oil faster than we eat bread. Something chitters in the old adit; we have boarded it up.",
        "Last shift. The upper shaft has started to shed rock and the winch rope is fraying. Pay is in the "
        "strongbox behind the powder barrels. Whoever comes for it: ride the cage up, not down.",
    ]}

DATA = {
    "worldgen/processor_list": {
        PROC: plist(STONE_WEATHER, aged_wood("spruce", "oak"),
                    [rule("cobweb", 0.3, "air"), rule("gravel", 0.1, "tuff"),
                     rule("cobbled_deepslate", 0.1, "deepslate_tiles")]),
    },
    "loot_table/chests": {
        NAME: loot_table(
            NAME,
            pool((3, 6), li("bread", 8, (1, 3)), li("coal", 10, (2, 8)), li("raw_iron", 8, (1, 5)),
                 li("raw_copper", 8, (2, 6)), li("torch", 8, (2, 8)), li("rail", 6, (2, 8)),
                 li("iron_nugget", 6, (3, 9)), li("glow_berries", 5, (1, 4)), li("candle", 4, (1, 3)),
                 li("iron_pickaxe", 2, damage=(0.3, 0.8))),
            pool(1, empty(6), li("emerald", 3, (1, 2)), li("lapis_lazuli", 3, (2, 6)), book(1))),
        f"{NAME}_cart": loot_table(
            f"{NAME}_cart",
            pool((2, 5), li("raw_iron", 10, (2, 6)), li("raw_copper", 10, (3, 9)), li("coal", 10, (3, 9)),
                 li("raw_gold", 4, (1, 3)), li("redstone", 5, (2, 6)), li("lapis_lazuli", 4, (2, 6)),
                 li("diamond", 1)),
            pool(1, empty(3), li("powered_rail", 2, (1, 4)), li("detector_rail", 1, (1, 2)),
                 li("name_tag", 1))),
        f"{NAME}_hidden": loot_table(
            f"{NAME}_hidden",
            pool((3, 6), li("emerald", 10, (3, 8)), li("gold_ingot", 8, (2, 6)), li("iron_ingot", 8, (2, 6)),
                 li("diamond", 4, (1, 3)), li("raw_gold", 5, (2, 5)), li("iron_pickaxe", 3, enchant=True),
                 li("compass", 2)),
            pool((1, 2), empty(2), book(4), li("golden_apple", 3), li("diamond_pickaxe", 1, damage=(0.4, 0.8)),
                 li("enchanted_golden_apple", 1)),
            pool(1, empty(1), lore_entry(BOOK, 2))),
    },
}


def floor(rng, x, z):
    v = math.sin(x * 0.37) + math.sin(z * 0.31 + 1.1) + rng.random() * 0.9
    if v < -0.6:
        return B("gravel")
    if v < 0.5:
        return B("cobbled_deepslate") if rng.random() < 0.4 else B("tuff")
    if v < 1.2:
        return B("coarse_dirt")
    return B("andesite")


# ------------------------------------------------------------------ the camp
def camp(seed):
    b = Build(f"{NAME}_camp", 33, 24, 33, seed=seed)
    rng = b.rng
    for x in range(33):
        for z in range(33):
            edge = min(x, z, 32 - x, 32 - z)
            if edge == 0 and rng.random() < 0.5:
                continue
            b.set(x, 0, z, floor(rng, x, z))
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
    headframe(b, rng)
    # tents and bedrolls on the west side
    a_tent(b, 3, 3, 5, "z", "brown", wide=True)
    a_tent(b, 3, 10, 5, "z", "gray", wide=True)
    for (x, z) in ((4, 5), (4, 12)):
        b.bed(x, 1, z, "south", "brown")
    b.set(6, 1, 6, B("candle", candles=2, lit=True))
    b.set(6, 1, 13, B("barrel", facing="up"))
    b.chest(6, 1, 5, "west", LOOT)
    meal_fire(b, 11, 1, 10, foods=("potato", "beef"), lit=True, done=(300, 100, 0, 0))
    for (x, z, ax) in ((9, 10, "z"), (13, 10, "z"), (11, 12, "x")):
        b.set(x, 1, z, B("stripped_spruce_log", axis=ax))
    # the forge on the south side
    forge(b, rng)
    # rail spur east across the camp, ore carts, sorted heaps
    rail_line(b, [(x, 16) for x in range(10, 33)], 1, "east_west")
    for x in range(10, 33):
        b.set(x, 0, 16, B("gravel"))
        if x % 2 == 0:
            b.set(x, 0, 15, B("spruce_planks") if b.get(x, 1, 15) is None else b.get(x, 0, 15))
    b.set(9, 1, 16, B(f"{W}_fence"))
    minecart(b, 14, 1, 16, yaw=90.0, loot=CART)
    minecart(b, 22, 1, 16, yaw=90.0)
    for (x, z, ore) in ((24, 13, "iron_ore"), (27, 13, "coal_ore"), (24, 19, "copper_ore")):
        for dx in (0, 1, 2):
            for dz in (0, 1):
                b.set(x + dx, 1, z + dz, B(ore) if rng.random() < 0.6 else B(rng.choice(CAVE_STONE[:3])))
        b.set(x + 1, 2, z, B(ore))
    # the paymaster's strongbox behind a wall of powder barrels
    for (x, z) in ((27, 26), (28, 26), (29, 26), (27, 27), (27, 28), (29, 27), (29, 28), (28, 29)):
        b.barrel(x, 1, z, "up")
        if rng.random() < 0.6:
            b.set(x, 2, z, B("barrel", facing="up"))
    b.chest(28, 1, 28, "north", HIDDEN)
    b.set(28, 1, 27, B("barrel", facing="up"))
    b.set(28, 2, 28, B("barrel", facing="up"))
    b.lectern(25, 1, 24, "south", journal(BOOK))
    # lamps round the camp, glow lichen on the boulders
    for (x, z) in ((8, 2), (16, 22), (26, 10), (20, 29), (2, 24)):
        lamp(b, x, 1, z, W, 2)
    for (cx, cz, r) in ((4, 27, 2.5), (30, 3, 2.2), (20, 2, 1.8)):
        rock_mound(b, rng, cx, cz, r, r * 0.8, 3)
    lichen(b, rng, 0.12)
    # tracks off to the workings, east and north
    b.jigsaw(32, 1, 16, "east", target=J_TRACK, pool=P_TRACKS, final="minecraft:rail[shape=east_west,waterlogged=false]")
    b.jigsaw(16, 1, 0, "north", target=J_TRACK, pool=P_TRACKS, final="minecraft:air")
    b.jigsaw(0, 1, 18, "west", target=J_TRACK, pool=P_TRACKS, final="minecraft:air")
    return b


def headframe(b, rng):
    x0, x1, z0, z1 = 13, 18, 4, 9
    top = 17
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            b.set(x, 0, z, B("cobbled_deepslate") if (x + z) % 3 else B("polished_deepslate"))
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(1, top + 1):
            b.set(x, y, z, SW)
    for y in range(5, top, 5):                                # braces and girts every five
        for x in range(x0 + 1, x1):
            b.set(x, y, z0, B("stripped_spruce_log", axis="x"))
            b.set(x, y, z1, B("stripped_spruce_log", axis="x"))
        for z in range(z0 + 1, z1):
            b.set(x0, y, z, B("stripped_spruce_log", axis="z"))
            b.set(x1, y, z, B("stripped_spruce_log", axis="z"))
    for x in range(x0, x1 + 1):                               # head beams
        for z in (z0, z1):
            b.set(x, top + 1, z, B("spruce_log", axis="x"))
    for z in range(z0, z1 + 1):
        b.set(15, top + 1, z, B("spruce_log", axis="z"))
        b.set(16, top + 1, z, B("spruce_log", axis="z"))
    wheel(b, 16, top + 3, 3, 2, "x", W, rim=B("stripped_spruce_log", axis="x"))
    # the boxed shaft stub at the top: plank lining the shaft once ran up through
    for y in range(top + 2, top + 5):
        for (x, z) in ((14, 5), (17, 5), (14, 8), (17, 8)):
            b.set(x, y, z, B("spruce_log", axis="y"))
        for x in (15, 16):
            for z in (5, 8):
                b.set(x, y, z, B("spruce_planks") if rng.random() < 0.8 else AIR)
        for z in (6, 7):
            for x in (14, 17):
                b.set(x, y, z, B("spruce_planks") if rng.random() < 0.8 else AIR)
    # chains down from the head beams to the cage
    cy = 7
    for y in range(cy + 4, top + 1):
        for (x, z) in ((15, 6), (16, 7)):
            b.set(x, y, z, B("iron_chain", axis="y"))
    for x in (15, 16):
        for z in (6, 7):
            b.set(x, cy, z, B("spruce_planks"))
            for y in (cy + 1, cy + 2):
                b.set(x, y, z, AIR)
            b.set(x, cy + 3, z, slab(W, "bottom"))
    for (x, z) in ((14, 6), (14, 7), (17, 6), (17, 7), (15, 5), (16, 5), (15, 8), (16, 8)):
        b.set(x, cy, z, B("spruce_planks"))
        b.set(x, cy + 1, z, B("iron_bars"))
        b.set(x, cy + 2, z, B("iron_bars"))
    b.set(16, cy + 1, 5, trapdoor(W, "north", "bottom", True))
    b.set(16, cy + 2, 5, trapdoor(W, "north", "top", True))
    b.set(15, cy + 1, 7, B("lantern"))
    # a ladder up one post to the cage and the head
    for y in range(1, top + 1):
        b.set(x0, y, z0 - 1, B("ladder", facing="north"))
    # the winch below: a drum on trestles
    for x in (19, 21):
        b.set(x, 1, 7, B(f"{W}_fence"))
    for x in (19, 20, 21):
        b.set(x, 2, 7, B("stripped_spruce_log", axis="x"))
    b.set(20, 1, 6, B("iron_chain", axis="x"))
    for (x, z) in ((x0, z0), (x1, z1)):
        b.set(x + (1 if x == x0 else -1), 9, z, B("lantern", hanging=True))
    b.set(x0 + 1, 10, z0, B("stripped_spruce_log", axis="x"))


def forge(b, rng):
    x0, z0 = 12, 23
    for x in range(x0, x0 + 7):
        for z in range(z0, z0 + 5):
            b.set(x, 0, z, B(rng.choice(["cobbled_deepslate", "polished_deepslate", "cobblestone"])))
    for x in range(x0, x0 + 3):
        for y in (1, 2, 3):
            b.set(x, y, z0 + 4, B(rng.choice(["cobbled_deepslate", "deepslate_bricks"])))
    b.set(x0, 1, z0 + 3, B("blast_furnace", facing="north"))
    b.set(x0 + 1, 1, z0 + 3, B("lava_cauldron"))
    b.set(x0 + 2, 1, z0 + 3, B("furnace", facing="north"))
    for x in range(x0, x0 + 3):
        b.set(x, 4, z0 + 3, stairs("deepslate_brick", "north", "top"))
        b.set(x, 4, z0 + 4, B("deepslate_bricks"))
    b.set(x0 + 4, 1, z0 + 2, B("anvil", facing="east"))
    b.set(x0 + 5, 1, z0 + 3, B("grindstone", face="floor", facing="north"))
    b.set(x0 + 6, 1, z0 + 3, B("water_cauldron", level=2))
    b.set(x0 + 6, 1, z0 + 1, B("smithing_table"))
    b.shelf(x0 + 3, 2, z0 + 4, W, "north", ["iron_ingot", "iron_pickaxe", "iron_chain"])
    b.set(x0 + 3, 1, z0 + 4, B("barrel", facing="north"))
    b.item_frame(x0 + 2, 2, z0 + 3, "north", "iron_shovel")
    b.chest(x0 + 6, 1, z0 + 4, "north", LOOT)
    b.set(x0 + 4, 1, z0 + 4, B("raw_iron_block"))


# ------------------------------------------------------------------ tracks and workings
def track(name, length, seed, bend=False):
    """A rail track along +z on sleepers, lantern posts; jigsaw in at z=0, out at the far end."""
    b = Build(name, 5, 6, length, seed=seed)
    rng = b.rng
    for z in range(length):
        for x in range(5):
            b.set(x, 0, z, B(rng.choice(["gravel", "tuff", "cobbled_deepslate", "gravel"])))
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
        if z % 2 == 0:
            for x in (1, 2, 3):
                b.set(x, 0, z, B("spruce_planks"))
        b.set(2, 1, z, B("rail", shape="north_south"))
    for z in range(2, length, 6):
        b.set(0, 1, z, B(f"{W}_fence"))
        b.set(0, 2, z, B(f"{W}_fence"))
        b.set(0, 3, z, B("lantern"))
    b.jigsaw(2, 1, 0, "north", name=J_TRACK, final="minecraft:rail[shape=north_south,waterlogged=false]")
    b.jigsaw(2, 1, length - 1, "south", target=J_WORK, pool=P_WORK,
             final="minecraft:rail[shape=north_south,waterlogged=false]")
    if rng.random() < 0.6:
        minecart(b, 2, 1, length // 2, yaw=0.0)
    return b


def work_site(name, sx, sy, sz, seed):
    b = Build(name, sx, sy, sz, seed=seed)
    rng = b.rng
    for x in range(sx):
        for z in range(sz):
            if rng.random() < 0.85 or z < 3:
                b.set(x, 0, z, floor(rng, x, z))
            for y in (1, 2):
                b.set(x, y, z, AIR)
    mid = sx // 2
    b.jigsaw(mid, 1, 0, "north", name=J_WORK, final="minecraft:rail[shape=north_south,waterlogged=false]")
    return b, rng, mid


def ore_face():
    b, rng, mid = work_site(f"{NAME}_ore_face", 17, 12, 15, 1849531)
    # the face: a rock wall across the far end, rich with ore where it has been worked
    for x in range(17):
        for z in range(9, 15):
            h = 9 - abs(x - 8) // 3 - (14 - z) // 2 + rng.randint(0, 2)
            for y in range(1, max(2, h)):
                r = rng.random()
                st = "iron_ore" if r < 0.08 else "coal_ore" if r < 0.15 else "copper_ore" if r < 0.2 else \
                    rng.choice(CAVE_STONE)
                b.set(x, y, z, B(st, axis="y") if st == "deepslate" else B(st))
    for x in range(mid - 2, mid + 3):                          # the drift being driven into it
        for z in range(9, 13):
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
    for z in (9, 12):                                          # timber sets, mineshaft-style
        for x in (mid - 2, mid + 2):
            b.set(x, 1, z, B("oak_fence"))
            b.set(x, 2, z, B("oak_fence"))
        for x in range(mid - 2, mid + 3):
            b.set(x, 3, z, B("oak_planks"))
    for z in range(1, 12):
        b.set(mid, 1, z, B("rail", shape="north_south"))
    b.set(mid, 1, 12, B(f"{W}_fence"))
    minecart(b, mid, 1, 7, yaw=0.0, loot=CART)
    b.set(mid - 1, 1, 11, B("iron_ore"))
    b.set(mid + 1, 1, 11, B("coal_ore"))
    b.item_frame(mid - 1, 2, 12, "north", "iron_pickaxe")
    b.set(3, 1, 6, B("scaffolding", bottom=False, distance=0))
    b.set(3, 2, 6, B("scaffolding", bottom=False, distance=0))
    b.set(3, 3, 6, B("scaffolding", bottom=False, distance=0))
    b.set(13, 1, 5, B("barrel", facing="up"))
    b.chest(13, 1, 6, "west", LOOT)
    lamp(b, 4, 1, 3, W, 2)
    hanging_lamp(b, mid, 2, 10)
    lichen(b, rng, 0.1)
    return b


def old_adit():
    b, rng, mid = work_site(f"{NAME}_adit", 15, 12, 16, 1849533)
    rock_mound(b, rng, 7, 13, 7.5, 3.5, 9)
    for z in range(9, 16):                                     # the boarded adit mouth, cobwebs inside
        for x in range(mid - 1, mid + 2):
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
        b.set(mid, 1, z, B("rail", shape="north_south"))
        if z % 3 == 0:
            for x in (mid - 2, mid + 2):
                b.set(x, 1, z, B("oak_fence"))
                b.set(x, 2, z, B("oak_fence"))
                b.set(x, 3, z, B("oak_planks"))
            for x in (mid - 1, mid, mid + 1):
                b.set(x, 3, z, B("oak_planks"))
    for z in range(1, 9):
        b.set(mid, 1, z, B("rail", shape="north_south"))
    for x in (mid - 1, mid + 1):
        b.set(x, 2, 9, B("spruce_planks"))
        b.set(x, 1, 9, B("spruce_planks"))
    b.set(mid, 3, 9, B("spruce_planks"))
    for (x, y, z) in ((mid - 1, 2, 12), (mid + 1, 1, 13), (mid, 2, 14), (mid - 1, 1, 11), (mid + 1, 2, 11)):
        b.set(x, y, z, B("cobweb"))
    b.spawner(mid, 2, 13, "cave_spider")
    b.chest(mid + 1, 1, 14, "west", LOOT)
    b.set(mid - 1, 1, 14, B("cobweb"))
    lamp(b, 3, 1, 4, W, 2, torch=True)
    lichen(b, rng, 0.08)
    return b


def supply_depot():
    b, rng, mid = work_site(f"{NAME}_depot", 15, 9, 14, 1849535)
    for z in range(1, 8):
        b.set(mid, 1, z, B("rail", shape="north_south"))
    b.set(mid, 1, 8, B(f"{W}_fence"))
    # a lean-to of planks over stacked goods
    for x in range(2, 13):
        b.set(x, 5, 12, stairs(W, "south"))
        b.set(x, 4, 11, stairs(W, "south"))
        b.set(x, 3, 10, stairs(W, "south"))
    for x in (2, 12):
        for y in (1, 2, 3, 4):
            b.set(x, y, 12, SW)
        for y in (1, 2):
            b.set(x, y, 10, SW)
    for x in range(3, 12):
        for z in (11, 12):
            if rng.random() < 0.75:
                b.barrel(x, 1, z, rng.choice(["up", "north"]))
                if rng.random() < 0.4:
                    b.set(x, 2, z, B("barrel", facing="up"))
    b.chest(6, 1, 11, "south", LOOT)
    for (x, z) in ((3, 6), (4, 6), (3, 7)):
        b.set(x, 1, z, B("spruce_log", axis="z"))
    b.set(11, 1, 6, B("hay_block", axis="y"))
    goods_stack(b, 10, 1, 4, rng, ("barrel", "log", "barrel"), wood=W)
    hanging_lamp(b, 7, 3, 11)
    lamp(b, 2, 1, 3, W, 2)
    return b


def sorting_floor():
    b, rng, mid = work_site(f"{NAME}_sorting", 17, 9, 14, 1849537)
    for z in range(1, 6):
        b.set(mid, 1, z, B("rail", shape="north_south"))
    b.set(mid, 1, 6, B(f"{W}_fence"))
    for (x, z) in ((3, 9), (5, 9), (11, 9), (13, 9)):          # sorting tables
        table(b, x, 1, z, W, top=slab(W, "top"))
        b.item_frame(x, 2, z, "up", rng.choice(["raw_iron", "raw_copper", "coal"]))
    b.set(8, 1, 10, B("grindstone", face="floor", facing="north"))
    b.set(9, 1, 10, B("stonecutter", facing="north"))
    for (x0, ore) in ((2, "coal_ore"), (7, "iron_ore"), (12, "copper_ore")):   # heaps by kind
        for x in range(x0, x0 + 3):
            for z in (12, 13):
                b.set(x, 1, z, B(ore) if rng.random() < 0.7 else B("gravel"))
        b.set(x0 + 1, 2, 12, B(ore))
    b.chest(15, 1, 9, "west", LOOT)
    for (x, z) in ((1, 4), (15, 4)):
        lamp(b, x, 1, z, W, 2)
    meal_fire(b, 4, 1, 4, foods=("potato",), lit=False, done=(0, 0, 0, 0))
    return b


def pools():
    return {
        "start": [Piece(camp(1849501), 1, PROC)],
        "tracks": [Piece(track(f"{NAME}_track_a", 8, 1849511), 3, PROC),
                   Piece(track(f"{NAME}_track_b", 12, 1849513), 2, PROC), Empty(1)],
        "workings": [Piece(ore_face(), 3, PROC), Piece(old_adit(), 2, PROC), Piece(supply_depot(), 2, PROC),
                     Piece(sorting_floor(), 2, PROC)],
    }
