"""Windpumps and watering places of the open grasslands. One design, two palettes (separate structures):
  windpump          acacia corral, an acacia for shade, dry grass  - amber steppe, the savannas, the outback,
                                                                     saguaro flats, the badlands
  windpump_prairie  oak corral, an oak for shade, field flowers    - plains, aspen parkland, olive groves

start pool (3 variants)
  pump     : a timber windpump - lattice tower, bladed wheel and tail vane, the pump
             rod down the middle - filling a long stone trough on trampled ground, a
             tree for shade, a cow's bones; the drovers' dues sit under the pump head
  dry      : a waterhole gone dry: cracked mud, a last puddle, dead bushes, bones, the
             old windpump lying where it fell; things turn up in the gravel of the bed
             (archaeology) and a box is buried in the bank
  corral   : a drovers' corral with hay, trough and salt lick, a shade lean-to with a
             bench, saddle and bell; the head drover's stash under the bench
"""
import math

from blocks import AIR, B
from builder import Build, item, slab, stairs, trapdoor
from pieces import Feature, Piece
from small_data import BOOKS, data_for
from smallkit import book, bones, clear, flora, ground_item, pick, ring, trail

TRAMPLED = [("coarse_dirt", 4), ("mud", 1), ("dirt_path", 2), ("packed_mud", 1)]
DRY = ["short_dry_grass", "tall_dry_grass", "dead_bush", "short_dry_grass"]
PALETTES = {
    "steppe": dict(
        name="windpump", procs=("small_steppe", "small_steppe_dig"), seed=0,
        biomes=["amber_steppe", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna",
                "minecraft:badlands", "minecraft:wooded_badlands", "ghost_gum_outback", "saguaro_flats"],
        ground=["coarse_dirt", "grass_block", "coarse_dirt"],
        path=[("coarse_dirt", 4), ("dirt_path", 2), ("gravel", 1)],
        plants=DRY + ["short_grass"], corral="acacia", cushion="orange", tree="minecraft:acacia_checked",
        books=("windpump", "windpump_dry", "windpump_corral")),
    "prairie": dict(
        name="windpump_prairie", procs=("small_prairie", "small_prairie_dig"), seed=70,
        biomes=["minecraft:plains", "minecraft:sunflower_plains", "aspen_parkland", "olive_groves"],
        ground=["grass_block", "grass_block", "coarse_dirt"],
        path=[("dirt_path", 4), ("coarse_dirt", 2), ("gravel", 1)],
        plants=["short_grass", "short_grass", "short_grass", "short_dry_grass"], corral="oak", cushion="red",
        tree="minecraft:oak_checked", books=("windpump_prairie", "windpump_dry", "windpump_corral")),
}


def tower(b, cx, cz, h, rng, fallen=False):
    """Lattice tower of fence legs with rings, a mast, wheel and vane on top. Returns head y."""
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.set(cx + dx, 0, cz + dz, B("cobblestone"))
        for y in range(1, h + 1):
            b.set(cx + dx, y, cz + dz, B("spruce_fence"))
    for y in (3, 6):
        for (dx, dz) in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            b.set(cx + dx, y, cz + dz, B("spruce_fence"))
    for y in range(1, h + 3):                         # pump rod
        b.set(cx, y, cz, B("iron_chain", axis="y"))
    for dx in (-1, 0, 1):                             # platform
        for dz in (-1, 0, 1):
            if dx or dz:
                b.set(cx + dx, h + 1, cz + dz, trapdoor("spruce", "north", "top", False))
    hy = h + 3
    b.set(cx, hy, cz, B("smooth_stone"))              # gear head
    b.set(cx, hy, cz - 1, B("iron_bars"))             # hub
    for x in range(cx - 2, cx + 3):                   # bladed wheel (in the x-y plane, z = cz-1)
        for y in range(hy - 2, hy + 3):
            d = math.hypot(x - cx, y - hy)
            if 0.9 < d <= 2.3 and (x + y) % 2 == 0:
                b.set(x, y, cz - 1, trapdoor("birch", "south", "bottom", True))
            elif 0.9 < d <= 1.5:
                b.set(x, y, cz - 1, B("iron_bars"))
    b.set(cx, hy, cz + 1, B("spruce_fence"))          # tail boom and vane
    b.set(cx, hy, cz + 2, B("spruce_fence"))
    for y in (hy, hy + 1):
        for z in (cz + 3, cz + 4):
            b.set(cx, y, z, trapdoor("spruce", "east", "bottom", True))
    return hy


def trough(b, x0, z, length, rng):
    for x in range(x0, x0 + length + 2):
        for dz in (-1, 0, 1):
            b.set(x, 0, z + dz, B("stone"))
            inner = x0 < x < x0 + length + 1 and dz == 0
            b.set(x, 1, z + dz, B("water", level=0) if inner else
                  B("smooth_stone_slab", type="double") if rng.random() < 0.7 else B("cobblestone"))


def pump(p):
    b = Build(f"{p['name']}_pump", 15, 16, 15, seed=1907461 + p["seed"])
    rng = b.rng
    cx, cz = 5, 6
    for (x, z) in ring(cx + 3, cz + 2, 0, 5.5, (15, 15)):   # trampled ground round the water
        if rng.random() < 0.75:
            b.set(x, 0, z, pick(rng, TRAMPLED))
            b.set(x, 1, z, AIR)
    hy = tower(b, cx, cz, 9, rng)
    b.set(cx, 1, cz, B("cauldron"))                   # pump head; the dues are buried under it
    b.set(cx, 0, cz, None)
    b.chest(cx, 0, cz, "south", f"expanse:chests/{p['name']}_hidden")
    trough(b, cx + 1, cz + 3, 5, rng)
    for (x, z) in ((cx, cz + 1), (cx, cz + 2), (cx, cz + 3), (cx + 1, cz + 3)):   # pipe to the trough
        b.set(x, 2, z, B("iron_bars"))
    b.set(cx, 1, cz + 3, B("cobblestone_wall"))
    # bucket on a hook, the drovers' barrel, a cow's bones, the shade tree
    b.barrel(cx - 2, 1, cz + 2, "up", f"expanse:chests/{p['name']}")
    ground_item(b, cx - 2, 2, cz + 2, book(BOOKS[p["books"][0]]), rotation=1)
    b.set(cx + 2, 1, cz, B("spruce_fence"))
    b.item_frame(cx + 2, 1, cz + 1, "south", item("bucket"))
    bones(b, [(12, 2), (13, 3), (12, 3), (11, 2)], rng)
    b.set(13, 1, 2, B("skeleton_skull", rotation=5))
    b.jigsaw(12, 0, 12, "up", pool=f"expanse:{p['name']}/tree", final="minecraft:coarse_dirt")
    b.set(12, 1, 12, AIR)
    trail(b, [(cx + 3, cz + 5), (7, 13), (6, 14)], rng, p["path"], width=2, fade=0.5)
    trail(b, [(cx + 8, cz + 3), (14, 9)], rng, p["path"], width=2, fade=0.5)
    flora(b, [(x, z) for x in range(15) for z in range(15)], rng, 0.18, p["ground"], plants=p["plants"])
    return b


def dry(p):
    b = Build(f"{p['name']}_dry", 15, 7, 15, seed=1907463 + p["seed"])
    rng = b.rng
    c = 7
    for (x, z) in ring(c, c, 0, 4.2, (15, 15)):       # cracked bed
        r = rng.random()
        b.set(x, 0, z, B("packed_mud") if r < 0.45 else B("mud") if r < 0.65 else B("coarse_dirt") if r < 0.85
              else B("gravel"))
        b.set(x, 1, z, AIR)
    for (x, z) in ring(c, c, 0, 1.2, (15, 15)):
        b.set(x, 0, z, B("water", level=0))
    for (x, z) in ring(c, c, 4.2, 6.0, (15, 15)):     # the bank, a step up
        if rng.random() < 0.8:
            b.set(x, 0, z, B("coarse_dirt"))
            b.set(x, 1, z, B("coarse_dirt") if rng.random() < 0.6 else B("grass_block"))
            if rng.random() < 0.3:
                b.set(x, 2, z, B(rng.choice(DRY)))
    # the fallen windpump lying across the bank: legs, rings, the wheel flat on the ground
    for x in range(1, 12):
        for z in (11, 13):
            if rng.random() < 0.85:
                b.set(x, 2 if b.get(x, 1, z) is not None and b.get(x, 1, z).short != "air" else 1, z,
                      B("spruce_fence"))
    for x in (3, 7):
        b.set(x, 1 if b.get(x, 1, 12) is None else 2, 12, B("spruce_fence"))
    for (x, z) in ring(12, 12, 0.9, 2.3, (15, 15)):
        if (x + z) % 2 == 0 and b.get(x, 1, z) is None:
            b.set(x, 1, z, trapdoor("birch", "north", "bottom", False))
    b.set(12, 1, 12, B("smooth_stone"))
    # bones of the ones that waited too long
    bones(b, [(5, 5), (6, 4), (9, 8), (8, 9), (4, 8)], rng, skull_chance=0.3)
    b.set(10, 1, 6, B("dead_bush"))
    # the buried box in the bank (west side) and the waterhole keeper's note
    b.chest(1, 1, 7, "east", f"expanse:chests/{p['name']}_hidden")
    b.set(1, 2, 7, B("coarse_dirt"))
    b.barrel(13, 1, 6, "west", f"expanse:chests/{p['name']}")
    b.set(12, 1, 7, B("cobblestone"))
    ground_item(b, 12, 2, 7, book(BOOKS[p["books"][1]]), rotation=2)
    flora(b, [(x, z) for x in range(15) for z in range(15)], rng, 0.15, p["ground"], plants=DRY)
    return b


def corral(p):
    b = Build(f"{p['name']}_corral", 16, 8, 13, seed=1907465 + p["seed"])
    rng = b.rng
    x0, z0, x1, z1 = 1, 1, 10, 9
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                if (x, z) == (x1, 5):
                    b.set(x, 1, z, B(f"{p['corral']}_fence_gate", facing="east", open=True))
                elif rng.random() < 0.93:
                    b.set(x, 1, z, B(f"{p['corral']}_fence"))
            else:
                if rng.random() < 0.6:
                    b.set(x, 0, z, pick(rng, TRAMPLED))
                b.set(x, 1, z, AIR)
    for (x, z) in ((3, 3), (3, 4), (4, 3)):            # hay, salt lick, trough
        b.set(x, 1, z, B("hay_block", axis=rng.choice(["x", "y", "z"])))
    b.set(3, 2, 3, B("hay_block", axis="y"))
    b.set(7, 1, 7, B("calcite"))
    for x in (5, 6, 7):
        b.set(x, 1, z0 + 1, B("water_cauldron", level=rng.choice([1, 2, 3])))
    # the shade lean-to outside the gate, bench, saddle and bell
    lx0, lz0 = 11, 7
    for (x, z) in ((lx0, lz0), (lx0 + 3, lz0), (lx0, lz0 + 3), (lx0 + 3, lz0 + 3)):
        for y in (1, 2, 3):
            b.set(x, y, z, B(f"{p['corral']}_log", axis="y"))
    for x in range(lx0 - 1, lx0 + 5):
        for z in range(lz0 - 1, lz0 + 5):
            if b.inb(x, 4, z):
                b.set(x, 4, z, slab(p["corral"]) if (x + z) % 4 else B("hay_block", axis="x"))
    for x in (lx0 + 1, lx0 + 2):
        b.set(x, 1, lz0 + 3, stairs(p["corral"], "south"))
    b.chest(lx0 + 1, 0, lz0 + 3, "north", f"expanse:chests/{p['name']}_hidden")
    b.barrel(lx0 + 3, 1, lz0 + 1, "west", f"expanse:chests/{p['name']}")
    b.item_frame(lx0, 2, lz0 + 1, "south", item("saddle"))
    b.item_frame(lx0 + 3, 2, lz0 + 1, "south", item("lead"))
    b.bell(lx0 + 1, 3, lz0, attachment="ceiling", facing="east")
    b.cushion(lx0 + 2, 1, lz0 + 1, color=p["cushion"])
    ground_item(b, lx0 + 2, 1, lz0 + 2, book(BOOKS[p["books"][2]]), rotation=1)
    clear(b, lx0 + 1, 1, lz0, lx0 + 2, 3, lz0 + 3)
    trail(b, [(x1 + 1, 5), (13, 4), (15, 3)], rng, p["path"], width=2, fade=0.5)
    flora(b, [(x, z) for x in range(16) for z in range(13)], rng, 0.15, p["ground"], plants=p["plants"])
    return b


def variant_module(key):
    p = PALETTES[key]
    name = p["name"]
    camp, dig = p["procs"]
    structure = dict(biomes=p["biomes"], size=1, max_distance=32, set="biome_sights")

    def pools():
        return {"start": [Piece(pump(p), 3, camp), Piece(dry(p), 2, dig), Piece(corral(p), 2, camp)],
                "tree": [Feature(p["tree"])]}
    return name, structure, data_for(name, [camp, dig]), pools


NAME, STRUCTURE, DATA, pools = variant_module("steppe")
