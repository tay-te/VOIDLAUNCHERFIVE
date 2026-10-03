"""The seven Expanse village styles: materials, regional habits and the few pieces of their own.

Every style shares villagekit's vanilla-scale archetypes (cottages, workshops for each trade, chapel,
library, farms, pens, meeting points, streets) and changes only what a vanilla village changes from
biome to biome: the wood, the stone, the roof, the crops, the beds, and a handful of regional pieces.

  wisteria   Wisteria Vale     timber frames of wisteria wood, plaster, clay-tile roofs; an orchard
  redwood    Redwood Giants    redwood log cabins, spruce roofs; a sawpit, a smokehouse (the butcher's)
  coast      Palm Coast        palm-wood houses on stilts under thatch; a fishing jetty, boats, drying racks
  steppe     Amber Steppe      round houses of ochre plaster and baobab; a market, a llama and goat pen
  moor       Heather Moor      rubble-stone cottages under slate; dry-stone walls, a sheep fold
  karst      Jade Karst        bamboo houses on limestone sills under green tile; rice paddies
  tundra     Frostbloom Tundra spruce cabins drifted in snow; a longhouse
"""
from blocks import AIR, B
from builder import slab, stairs
from pieces import Piece
import villagekit as K
from villagekit import Build, Style, decor, hash_seed, j_entrance, j_up, log, state_str


# ------------------------------------------------------------------ shared regional helpers
def small_tree(b, x, z, rng, log_id, leaves_id, h=4, r=2, blossoms=None, y=1):
    """A garden tree (persistent leaves) for decor and orchards."""
    for k in range(h):
        b.set(x, y + k, z, log(log_id))
    top = y + h
    leaf = B(leaves_id, persistent=True, distance=1)
    for dy in (-1, 0, 1):
        rr = r - (1 if dy == 1 else 0)
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if abs(dx) == rr and abs(dz) == rr and (dy != 0 or rng.random() < 0.6):
                    continue
                p = (x + dx, top + dy, z + dz)
                if b.inb(*p) and b.get(*p) is None:
                    b.set(*p, leaf)
    b.set(x, top - 1, z, log(log_id))
    if blossoms:
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                p = (x + dx, top - 2, z + dz)
                if b.inb(*p) and b.get(*p) is None and b.get(x + dx, top - 1, z + dz) is not None and \
                        b.get(x + dx, top - 1, z + dz).id == leaves_id and rng.random() < 0.55:
                    b.hang_moss(p[0], p[1], p[2], rng.randint(1, 2), block=blossoms)


def garden_ground(b, P, cells, rng, y=0):
    for (x, z) in cells:
        b.set(x, y, z, B(P.ground))
        for yy in (y + 1, y + 2, y + 3):
            if b.get(x, yy, z) is None:
                b.set(x, yy, z, AIR)


def plant_flowers(b, P, cells, rng, y=1, density=0.6):
    for (x, z) in cells:
        if rng.random() < density and b.get(x, y, z) in (None, AIR):
            f = rng.choice(P.flowers)
            if f not in K.TALL or b.get(x, y + 1, z) in (None, AIR):
                K.flower(b, x, y, z, f)


def tree_decor(P, name, log_id, leaves_id, blossoms=None, h=4):
    def dress(b, cx, cz):
        small_tree(b, cx, cz, b.rng, log_id, leaves_id, h=h, r=2, blossoms=blossoms)
    return decor(name, (5, h + 3, 5), dress, foot=state_str(P.ground))


def flower_bed(P, name):
    def dress(b, cx, cz):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                b.set(cx + dx, 0, cz + dz, B("grass_block") if P.ground == "grass_block" else B("dirt"))
        b.cells.pop((cx, 0, cz), None)
        plant_flowers(b, P, [(cx + dx, cz + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)], b.rng, density=0.9)
    return decor(name, (3, 3, 3), dress, foot="minecraft:grass_block")


def woodpile(P, name, wood):
    def dress(b, cx, cz):
        for dx in (-1, 0, 1):
            b.set(cx + dx, 1, cz, log(wid_log(wood), "z"))
            if dx != 1:
                b.set(cx + dx, 2, cz, log(wid_log(wood), "z"))
        b.set(cx + 1, 2, cz, slab(wood))
    return decor(name, (3, 3, 1), dress)


def wid_log(wood):
    return K.wid(wood, "log")


# ------------------------------------------------------------------ wisteria vale
def orchard(P, name):
    """A walled garden of wisteria trees and beehives off the street (entrance at ground level)."""
    b = Build(name, 11, 9, 11, seed=hash_seed(name))
    rng = b.rng
    cells = [(x, z) for x in range(11) for z in range(11)]
    garden_ground(b, P, cells, rng)
    for (x, z) in cells:
        if x in (0, 10) or z in (0, 10):
            b.set(x, 1, z, B(P.W("fence")))
    for (x, z) in ((3, 3), (7, 3), (3, 7), (7, 7)):
        small_tree(b, x, z, rng, "expanse:wisteria_log", "expanse:wisteria_leaves", h=3, r=2,
                   blossoms="expanse:wisteria_blossoms")
    for (x, z) in ((5, 2), (5, 8)):
        b.set(x, 1, z, B(P.W("fence")))
        b.set(x, 2, z, B("beehive", facing="west"))
    plant_flowers(b, P, [(x, z) for x in range(1, 10) for z in range(1, 10) if (x + z) % 2 == 0], rng,
                  density=0.35)
    for x in range(0, 6):                                # a path in from the gate
        b.set(x, 0, 5, B(P.path))
        b.set(x, 1, 5, AIR)
    b.set(0, 2, 4, B("torch"))
    b.set(0, 2, 6, B("torch"))
    b.cells.pop((0, 1, 5), None)
    j_entrance(b, 0, 1, 5, state_str(B(P.W("fence_gate"), facing="east")))
    return K.trim(b)


def wisteria_extras(P):
    return {"houses": [(orchard(P, "orchard"), 3, None)],
            "decor": [(tree_decor(P, "wisteria_tree", "expanse:wisteria_log", "expanse:wisteria_leaves",
                                  "expanse:wisteria_blossoms"), 2), (flower_bed(P, "flower_bed"), 2)]}


WISTERIA = Style(
    name="wisteria", biomes=["wisteria_vale"], vtype="plains",
    wood="expanse:wisteria", frame="expanse:wisteria", walls="timber", wall="white_terracotta",
    roof="brick", roof_fill="expanse:wisteria_planks", base=("cobblestone", "stone_bricks", "cobblestone"),
    bridge="expanse:wisteria_planks", beds=("purple", "magenta", "white"), carpet="purple",
    chapel_glass="magenta_stained_glass_pane", flowers=("allium", "pink_tulip", "azure_bluet", "lilac", "peony"),
    pot="expanse:potted_wisteria_sapling", crops=(("carrots", 0.3), ("beetroots", 0.2), ("potatoes", 0.1)),
    features=("minecraft:flower_plain", "minecraft:pile_hay"),
    loot_items=(("expanse:wisteria_sapling", 5, (1, 2)), ("apple", 5, (1, 3)), ("wheat", 8, (1, 7)),
                ("allium", 2, (1, 3)), ("honey_bottle", 2, 1)),
    extras=wisteria_extras)


# ------------------------------------------------------------------ redwood giants
def sawpit(P, name):
    """A saw pit: a redwood log on trestles over a shallow pit, sawn planks and log piles beside it."""
    b = Build(name, 9, 5, 7, seed=hash_seed(name))
    for x in range(9):
        for z in range(7):
            b.set(x, 0, z, B("coarse_dirt") if (x + z) % 3 else B(P.ground))
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
    for x in range(3, 6):                                # the pit, the bottom sawyer's place
        for z in (2, 3, 4):
            b.set(x, 0, z, AIR)
    for x in (2, 6):                                     # trestles either end of it
        for z in (2, 4):
            b.set(x, 1, z, B(P.W("fence")))
    for x in range(1, 8):                                # the log being sawn
        b.set(x, 2, 3, log("expanse:redwood_log", "x"))
    for x in (1, 2, 3):                                  # a stack of sawn planks
        b.set(x, 1, 6, B("expanse:redwood_planks"))
        b.set(x, 2, 6, slab("expanse:redwood"))
    for z in (0, 1):
        for x in (6, 7, 8):
            b.set(x, 1, z, log("expanse:redwood_log", "x"))
        b.set(7, 2, z, log("expanse:redwood_log", "x"))
    b.set(8, 1, 6, B(P.W("fence")))
    b.set(8, 2, 6, B("lantern"))
    b.set(0, 0, 3, B(P.path))
    j_entrance(b, 0, 1, 3, "minecraft:air")
    return K.trim(b)


def smokehouse(P, name):
    """The butcher's smokehouse: a low log hut, smoker and a smoking fire inside, a chimney stack, a pen
    for the butcher's animals behind."""
    b, x0, z0, x1, z1 = K.house_build(name, 6, 5, back=5)
    rng = b.rng
    dz = z0 + 2
    K.shell(b, P, x0, z0, x1, z1, rng, dz, windows=[(x0 + 2, 2, z0), (x0 + 2, 2, z1)], floor="cobblestone")
    K.job(b, x1 - 1, 1, z0 + 1, "butcher")
    b.chest(x1 - 1, 1, z1 - 1, "north", K.VLOOT + "butcher")
    b.campfire(x1 - 2, 1, z0 + 1, lit=True, facing="south")
    b.set(x0 + 1, 1, z1 - 1, B("hay_block", axis="y"))
    K.wall_torch(b, x1 - 1, 3, dz, "west")
    top = K.roof(b, P, x0, z0, x1, z1, 4, axis="x")
    for y in range(2, top + 2):                          # a stone chimney over the fire
        b.set(x1 - 2, y, z0, B("cobblestone"))
    b.set(x1 - 2, 1, z0, B("cobblestone"))
    b.campfire(x1 - 2, top + 2, z0, lit=True)
    px0 = x1 + 1                                         # the pen behind
    for x in range(px0, px0 + 5):
        for z in range(z0, z1 + 1):
            edge = x in (px0, px0 + 4) or z in (z0, z1)
            b.set(x, 0, z, B(P.ground))
            b.set(x, 1, z, B(P.W("fence")) if edge and x != px0 else AIR)
            b.set(x, 2, z, AIR)
    j_up(b, px0 + 2, 0, dz, K.V_BUTCHER_ANIMALS, state_str(P.ground))
    return K.finish(b, P, x0, dz)


def redwood_extras(P):
    return {"houses": [(sawpit(P, "sawpit"), 2, None), (smokehouse(P, "smokehouse"), 2, "houses")],
            "replaces": ("butcher",),
            "decor": [(woodpile(P, "woodpile", "expanse:redwood"), 2)]}


REDWOOD = Style(
    name="redwood", biomes=["redwood_giants"], vtype="taiga",
    wood="expanse:redwood", frame="expanse:redwood", walls="cabin", roof="spruce", roof_fill="spruce_planks",
    base=("cobblestone", "cobblestone", "mossy_cobblestone"), bridge="spruce_planks",
    street_mix=(("coarse_dirt", 0.08), ("podzol", 0.05)), beds=("green", "brown", "red"), carpet="brown",
    chapel_glass="green_stained_glass_pane", flowers=("fern", "poppy", "lily_of_the_valley"),
    pot="expanse:potted_redwood_sapling", crops=(("potatoes", 0.3), ("carrots", 0.2), ("beetroots", 0.1)),
    lamp="torches", features=("minecraft:spruce", "minecraft:pile_hay"),
    loot_items=(("expanse:redwood_sapling", 5, (1, 2)), ("sweet_berries", 5, (1, 7)), ("potato", 10, (1, 7)),
                ("spruce_log", 2, (1, 4)), ("fern", 2, 1)),
    extras=redwood_extras)


# ------------------------------------------------------------------ palm coast
def jetty(P, name):
    """A fishing jetty: a plank deck on posts running out from the street, a fisherman's barrel, lanterns
    and a boat tied alongside."""
    L = 12
    b = Build(name, L, 5, 5, seed=hash_seed(name))
    for x in range(L):
        for z in (1, 2, 3):
            b.set(x, 0, z, B(P.W("planks")))
            for y in (1, 2):
                b.set(x, y, z, AIR)
        if x % 3 == 2:
            for z in (1, 3):
                b.set(x, 1, z, B(P.W("fence")))
                b.set(x, 0, z, log(P.F("log")))
    b.set(L - 1, 2, 1, B("lantern"))
    b.set(L - 1, 1, 1, B(P.W("fence")))
    b.set(5, 2, 3, B("lantern"))
    b.barrel(L - 2, 1, 3, facing="up")
    b.set(L - 3, 1, 3, B("dried_kelp_block"))
    b.boat(L - 4, 0, 4, wood="jungle", yaw=90.0)
    j_entrance(b, 0, 0, 2, state_str(B(P.W("planks"))))
    return K.trim(b)


def drying_rack(P, name):
    """Fish and kelp drying on a rack of palm posts."""
    def dress(b, cx, cz):
        for x in (cx - 1, cx + 1):
            b.set(x, 1, cz, B(P.W("fence")))
            b.set(x, 2, cz, B(P.W("fence")))
        for x in (cx - 1, cx, cx + 1):
            b.set(x, 3, cz, B(P.W("fence")))
        b.set(cx, 2, cz, B("dried_kelp_block"))
    return decor(name, (3, 4, 1), dress)


def beached_boat(P, name):
    def dress(b, cx, cz):
        b.set(cx, 1, cz, AIR)
        b.boat(cx, 1, cz, wood="jungle", yaw=30.0)
    return decor(name, (1, 2, 1), dress)


def palm(P, name):
    def dress(b, cx, cz):
        for k in range(5):
            b.set(cx, 1 + k, cz, log("expanse:palm_log"))
        leaf = B("expanse:palm_leaves", persistent=True, distance=1)
        b.set(cx, 6, cz, leaf)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            b.set(cx + dx, 6, cz + dz, leaf)
            b.set(cx + 2 * dx, 6, cz + 2 * dz, leaf)
            b.set(cx + 2 * dx, 5, cz + 2 * dz, leaf)
    return decor(name, (5, 7, 5), dress, foot=state_str(P.ground))


def coast_extras(P):
    return {"houses": [(jetty(P, "jetty"), 3, None)],
            "decor": [(drying_rack(P, "drying_rack"), 2), (beached_boat(P, "boat"), 1), (palm(P, "palm"), 2)]}


COAST = Style(
    name="coast", biomes=["palm_coast"], vtype="jungle",
    wood="expanse:palm", frame="expanse:palm", walls="timber", wall="expanse:palm_planks",
    roof="bamboo_mosaic", roof_fill="bamboo_mosaic", roof_shape="hip", stilts=2,
    base=("expanse:palm_planks",), floor="expanse:palm_planks", ground="sand", path="dirt_path",
    bridge="expanse:palm_planks", street_mix=(("sand", 0.15), ("coarse_dirt", 0.1)), path_wear=0.0,
    beds=("cyan", "light_blue", "yellow"), carpet="cyan", chapel_glass="light_blue_stained_glass_pane",
    flowers=("short_dry_grass", "dandelion"), pot="expanse:potted_palm_sapling",
    crops=(("carrots", 0.3), ("potatoes", 0.3), ("beetroots", 0.1)), features=("minecraft:pile_hay",),
    loot_items=(("expanse:palm_sapling", 5, (1, 2)), ("cod", 8, (1, 4)), ("salmon", 4, (1, 3)),
                ("dried_kelp", 6, (2, 8)), ("sugar_cane", 3, (1, 5))),
    extras=coast_extras)


# ------------------------------------------------------------------ amber steppe
def market(P, name):
    """A market square: three stalls under coloured awnings with goods on their counters."""
    b = Build(name, 11, 6, 11, seed=hash_seed(name))
    rng = b.rng
    for x in range(11):
        for z in range(11):
            b.set(x, 0, z, B(P.path) if rng.random() < 0.8 else B("coarse_dirt"))
            for y in range(1, 5):
                b.set(x, y, z, AIR)
    goods = ["melon", "pumpkin", "hay_block", "orange_wool", "yellow_wool"]
    for (x0, z0, col) in ((2, 1, "orange"), (2, 7, "yellow"), (7, 4, "red")):
        for (x, z) in ((x0, z0), (x0 + 2, z0), (x0, z0 + 2), (x0 + 2, z0 + 2)):
            for y in (1, 2):
                b.set(x, y, z, B(P.W("fence")))
        for x in range(x0 - 1, x0 + 4):
            for z in range(z0, z0 + 3):
                b.set(x, 3, z, B(f"{col}_wool") if (x + z) % 2 == 0 else B("white_wool"))
        for x in (x0 + 1,):
            for z in (z0, z0 + 1, z0 + 2):
                b.set(x, 1, z, slab(P.wood, "top"))
        b.set(x0 + 1, 2, z0 + 1, B(rng.choice(goods)) if rng.random() < 0.5 else B("hay_block", axis="y"))
        b.pot(x0 + 1, 2, z0, "west")
    b.set(0, 0, 5, B(P.path))
    j_entrance(b, 0, 1, 5, "minecraft:air")
    return K.trim(b)


def llama_pen(P, name):
    return K.pen(P, name, 7, 8, pool=P.pool("herd"), animals=3)


def steppe_extras(P):
    return {"houses": [(market(P, "market"), 3, None), (llama_pen(P, "herd_pen"), 2, None)],
            "pools": {"herd": [Piece(K.animal_piece("llamas", ["llama", "llama"]), 2),
                               Piece(K.animal_piece("goats", ["goat", "goat"]), 2),
                               Piece(K.animal_piece("goat", ["goat"]), 1)]},
            "decor": [(hay_stack(P, "hay_stack"), 2), (pots(P, "pots"), 1)]}


def hay_stack(P, name):
    def dress(b, cx, cz):
        b.set(cx, 1, cz, B("hay_block", axis="y"))
        b.set(cx, 2, cz, B("hay_block", axis="y"))
        b.set(cx + 1, 1, cz, B("hay_block", axis="x"))
    return decor(name, (3, 3, 1), dress)


def pots(P, name):
    def dress(b, cx, cz):
        b.pot(cx, 1, cz, "north", ("brick", "brick", "brick", "brick"))
        b.pot(cx + 1, 1, cz, "west", ("brick", "brick", "brick", "brick"))
    return decor(name, (3, 2, 1), dress)


STEPPE = Style(
    name="steppe", biomes=["amber_steppe"], vtype="savanna",
    wood="expanse:baobab", frame="expanse:baobab", walls="plaster", wall="yellow_terracotta", plinth="terracotta",
    roof="expanse:baobab", roof_fill="expanse:baobab_planks", roof_shape="hip", round_houses=True,
    base=("terracotta", "terracotta", "brown_terracotta"), floor="expanse:baobab_planks", bridge="expanse:baobab_planks",
    street_mix=(("coarse_dirt", 0.1),), beds=("orange", "yellow", "brown"), carpet="orange",
    chapel_glass="orange_stained_glass_pane", flowers=("short_dry_grass", "dandelion", "orange_tulip"),
    pot="expanse:potted_baobab_sapling", crops=(("beetroots", 0.25), ("carrots", 0.15), ("potatoes", 0.05)),
    lamp="torches", features=("minecraft:pile_hay",),
    loot_items=(("expanse:baobab_sapling", 5, (1, 2)), ("wheat", 10, (2, 7)), ("beetroot", 6, (2, 6)),
                ("melon_seeds", 3, (1, 5)), ("leather", 3, (1, 3))),
    extras=steppe_extras)


# ------------------------------------------------------------------ heather moor
def sheep_fold(P, name):
    return K.pen(P, name, 7, 9, pool=K.V_SHEEP, animals=3, wall="cobblestone_wall")


def drystone_wall(P, name):
    def dress(b, cx, cz):
        for dx in (-2, -1, 0, 1, 2):
            b.set(cx + dx, 1, cz, B("mossy_cobblestone_wall") if (dx * 7) % 3 == 0 else B("cobblestone_wall"))
        b.set(cx - 2, 2, cz, B("cobblestone_wall"))
    return decor(name, (5, 3, 1), dress)


def moor_extras(P):
    return {"houses": [(sheep_fold(P, "sheep_fold"), 4, None)],
            "decor": [(drystone_wall(P, "drystone_wall"), 3)]}


MOOR = Style(
    name="moor", biomes=["heather_moor"], vtype="plains",
    wood="spruce", frame="spruce", walls="stone", stone=("cobblestone", "cobblestone", "mossy_cobblestone",
                                                         "stone", "andesite"),
    corner="stone_bricks", roof="deepslate_tile", roof_fill="spruce_planks", gable_fill="cobblestone",
    base=("cobblestone", "mossy_cobblestone", "stone"), floor="spruce_planks", bridge="spruce_planks",
    street_mix=(("gravel", 0.06), ("coarse_dirt", 0.08)), beds=("white", "light_gray", "purple"), carpet="purple",
    chapel_glass="purple_stained_glass_pane", flowers=("expanse:heather", "expanse:edelweiss", "fern"),
    pot="expanse:potted_heather", crops=(("potatoes", 0.35), ("beetroots", 0.15), ("carrots", 0.1)),
    lamp="torches", features=("minecraft:pile_hay",), animals=K.V_SHEEP,
    loot_items=(("potato", 10, (1, 7)), ("expanse:heather", 4, (1, 3)), ("white_wool", 3, (1, 3)),
                ("birch_sapling", 3, (1, 2)), ("mutton", 4, (1, 3))),
    extras=moor_extras)


# ------------------------------------------------------------------ jade karst
def paddy(P, name):
    """A rice paddy: long beds of 'rice' (wheat) between flooded channels with lily pads, a limestone
    kerb, a composter for the farmer."""
    sx, sz = 10, 11
    b = Build(name, sx, 3, sz, seed=hash_seed(name))
    rng = b.rng
    for x in range(sx):
        for z in range(sz):
            if x in (0, sx - 1) or z in (0, sz - 1):
                b.set(x, 0, z, B("expanse:limestone_bricks"))
            elif x % 3 == 0:
                b.set(x, 0, z, B("water"))
                b.set(x, 1, z, B("lily_pad") if rng.random() < 0.3 else AIR)
            else:
                b.set(x, 0, z, B("farmland", moisture=7))
                b.set(x, 1, z, B("wheat", age=7))
            b.set(x, 2, z, AIR)
            if b.get(x, 1, z) is None:
                b.set(x, 1, z, AIR)
    b.set(sx - 2, 0, sz - 2, B("dirt"))
    b.set(sx - 2, 1, sz - 2, B("composter"))
    j_entrance(b, 0, 0, sz // 2, state_str(B("expanse:limestone_bricks")))
    return b


def bamboo_grove(P, name):
    def dress(b, cx, cz):
        for (dx, dz, h) in ((0, 0, 7), (1, 0, 5), (0, 1, 6), (-1, 0, 4)):
            for k in range(h):
                lv = "large" if k >= h - 2 else "small" if k >= h - 4 else "none"
                b.set(cx + dx, 1 + k, cz + dz, B("bamboo", age=1, leaves=lv))
            b.set(cx + dx, 0, cz + dz, B("podzol"))
        b.cells.pop((cx, 0, cz), None)
    return decor(name, (3, 9, 3), dress, foot="minecraft:podzol[snowy=false]")


def stone_lantern(P, name):
    def dress(b, cx, cz):
        b.set(cx, 1, cz, B("expanse:limestone_brick_wall"))
        b.set(cx, 2, cz, B("expanse:polished_limestone"))
        b.set(cx, 3, cz, B("lantern"))
    return decor(name, (1, 4, 1), dress)


def karst_extras(P):
    return {"houses": [(paddy(P, "paddy"), 5, "farm")],
            "decor": [(bamboo_grove(P, "bamboo_grove"), 2), (stone_lantern(P, "stone_lantern"), 2)]}


KARST = Style(
    name="karst", biomes=["jade_karst"], vtype="jungle",
    wood="bamboo", frame="jungle", walls="bamboo", wall="bamboo_planks", plinth="expanse:limestone_bricks",
    corner="stripped_bamboo_block", roof="dark_prismarine", roof_fill="bamboo_planks", roof_shape="hip",
    base=("expanse:limestone_bricks", "expanse:limestone_bricks", "expanse:mossy_limestone"),
    floor="bamboo_planks", bridge="bamboo_planks", street_mix=(("expanse:polished_limestone", 0.08),),
    beds=("green", "lime", "white"), carpet="green", chapel_glass="lime_stained_glass_pane",
    flowers=("fern", "azure_bluet"), pot="potted_bamboo",
    crops=(("carrots", 0.2), ("potatoes", 0.1)), features=(), lamp="lantern",
    loot_items=(("bamboo", 8, (2, 6)), ("wheat", 8, (2, 7)), ("carrot", 5, (1, 5)), ("cocoa_beans", 3, (1, 3)),
                ("melon_slice", 4, (1, 5))),
    extras=karst_extras)


# ------------------------------------------------------------------ frostbloom tundra
def longhouse(P, name):
    """A spruce longhouse half-buried in drifted snow: one long hall with four beds along its walls,
    furnaces at the far end, lanterns from the ridge beam."""
    b, x0, z0, x1, z1 = K.house_build(name, 11, 6)
    rng = b.rng
    dz = z0 + 2
    win = [(x0 + 3, 2, z0), (x0 + 7, 2, z0), (x0 + 3, 2, z1), (x0 + 7, 2, z1)]
    K.shell(b, P, x0, z0, x1, z1, rng, dz, windows=win)
    for (x, z, f) in ((x0 + 2, z0 + 1, "east"), (x0 + 5, z0 + 1, "east"), (x0 + 2, z1 - 1, "east"),
                      (x0 + 5, z1 - 1, "east")):
        b.bed(x, 1, z, f, P.beds[(x + z) % len(P.beds)])
    b.set(x1 - 1, 1, z0 + 2, B("furnace", facing="west"))
    b.set(x1 - 1, 1, z0 + 3, B("furnace", facing="west"))
    b.set(x1 - 1, 1, z0 + 1, B("crafting_table"))
    b.chest(x1 - 1, 1, z1 - 1, "west", P.loot)
    K.table(b, P, x0 + 8, 1, z0 + 1)
    for x in (x0 + 3, x0 + 7):
        b.set(x, 3, dz + 1, B("lantern", hanging=True))
    K.wall_torch(b, x1 - 1, 3, dz + 1, "west")
    K.roof(b, P, x0, z0, x1, z1, 4, axis="x")
    for x in (x0 + 3, x0 + 7):                          # lanterns hang from the ceiling
        b.set(x, 3, dz + 1, B("lantern", hanging=True))
    return K.finish(b, P, x0, dz, villagers=[(x0 + 3, dz + 1), (x0 + 6, dz + 1)])


def firewood(P, name):
    def dress(b, cx, cz):
        for dx in (-1, 0, 1):
            b.set(cx + dx, 1, cz, log("spruce_log", "z"))
        b.set(cx, 2, cz, log("spruce_log", "z"))
        b.set(cx - 1, 2, cz, B("snow", layers=2))
        b.set(cx + 1, 2, cz, B("snow", layers=2))
        b.set(cx, 3, cz, B("snow", layers=1))
    return decor(name, (3, 4, 1), dress)


def tundra_extras(P):
    return {"houses": [(longhouse(P, "longhouse"), 3, "houses")], "decor": [(firewood(P, "firewood"), 2)]}


TUNDRA = Style(
    name="tundra", biomes=["frostbloom_tundra"], vtype="snow",
    wood="spruce", frame="spruce", walls="cabin", roof="spruce", roof_fill="spruce_planks", snow=True,
    base=("cobblestone", "stone_bricks", "cobblestone"), floor="spruce_planks", bridge="spruce_planks",
    beds=("light_blue", "white", "cyan"), carpet="light_blue", chapel_glass="light_blue_stained_glass_pane",
    flowers=("expanse:frostbloom", "fern"), pot="expanse:potted_frostbloom",
    crops=(("potatoes", 0.35), ("beetroots", 0.3)), features=("minecraft:pile_snow", "minecraft:pile_ice"),
    loot_items=(("potato", 10, (1, 7)), ("beetroot", 6, (1, 5)), ("snowball", 8, (1, 7)),
                ("spruce_sapling", 4, (1, 2)), ("expanse:frostbloom", 3, (1, 3)), ("rabbit_hide", 3, (1, 3))),
    extras=tundra_extras)


STYLES = {s.name: s for s in (WISTERIA, REDWOOD, COAST, STEPPE, MOOR, KARST, TUNDRA)}
