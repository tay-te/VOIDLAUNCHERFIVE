"""Deepstone ruins - a collapsed hall of the old deep builders on a cavern floor (UNDERGROUND).

start  nave
  A long hall of deepslate bricks and tiles (the ancient city's stone) with two rows of
  columns, its vault long fallen: broken arches, wall stubs, heaps of rubble. At the far end a
  dais with the altar, candles burning on it still and soul lanterns on chains. Gravel has
  silted into the aisles, and some of it hides finds for a brush. A stair behind the altar
  goes down to the crypt.
crypt  (down from the dais): sarcophagi, candles, the hidden chest
gates -> outworks (rigid, on the hall floor): a ruined gate arch / a tower stump / a
  colonnade with fallen statues
"""
import math
import random

from blocks import AIR, B
from builder import Build, prune_unsupported
from loot import book, empty, item as li, pool, table as loot_table
from pieces import Empty, Piece
from processors import archaeology, plist, rot, rule
from townkit import drop_orphans, hanging_lamp, journal, lichen, lore_entry, rect_ring, rock_mound, slab, stairs

NAME = "deepstone_ruins"
STRUCTURE = dict(biomes=["#minecraft:is_overworld"], cavern=True, set="cavern_halls", weight=2, size=2,
                 max_distance=40)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
DIG = f"expanse:chests/{NAME}_archaeology"
P_CRYPT = f"expanse:{NAME}/crypt"
P_OUT = f"expanse:{NAME}/outworks"
J_CRYPT = "expanse:deepstone_crypt"
J_OUT = "expanse:deepstone_out"
PROC = NAME
DB, DT, PD = "deepslate_bricks", "deepslate_tiles", "polished_deepslate"

BOOK = {
    "title": "Rubbing from the Altar", "author": "copied by Brother Anselm",
    "pages": [
        "The script is older than any I know. Where it can be read: 'We went down to be nearer the warm "
        "heart of the stone, and built a hall so great the dark had to make room for it.'",
        "'We lit the candles for those who sleep below the dais. Let them burn while the hall stands.' "
        "The candles are still burning. I did not light them.",
        "There is a stair behind the altar. I went down three steps and came back up. Someone braver may "
        "go on. Take light.",
    ]}

DATA = {
    "worldgen/processor_list": {
        PROC: plist([rule(DB, 0.18, "cracked_deepslate_bricks"), rule(DB, 0.06, "cobbled_deepslate"),
                     rule(DT, 0.15, "cracked_deepslate_tiles"), rule(PD, 0.06, "cobbled_deepslate"),
                     rule("cobweb", 0.35, "air"), rule("candle", 0.25, "air"), rule("glow_lichen", 0.3, "air")],
                    rot(0.9, [DB, "cracked_deepslate_bricks", "cobbled_deepslate", "deepslate_brick_slab"]),
                    archaeology("gravel", "suspicious_gravel", DIG, 5)),
    },
    "loot_table/chests": {
        NAME: loot_table(
            NAME,
            pool((2, 5), li("candle", 8, (1, 4)), li("bone", 8, (1, 4)), li("coal", 6, (2, 6)),
                 li("gold_nugget", 8, (2, 8)), li("amethyst_shard", 5, (1, 4)), li("echo_shard", 1),
                 li("glow_ink_sac", 4, (1, 3)), li("book", 4, (1, 2)), li("iron_ingot", 4, (1, 3))),
            pool(1, empty(5), li("emerald", 3, (1, 3)), book(2), li("disc_fragment_5", 1))),
        f"{NAME}_hidden": loot_table(
            f"{NAME}_hidden",
            pool((3, 6), li("gold_ingot", 8, (2, 6)), li("emerald", 8, (2, 6)), li("diamond", 4, (1, 3)),
                 li("lapis_lazuli", 6, (4, 10)), li("echo_shard", 3, (1, 3)), li("amethyst_shard", 5, (2, 6)),
                 li("experience_bottle", 5, (2, 5)), li("iron_sword", 2, enchant=True)),
            pool((1, 2), empty(2), book(5), li("golden_apple", 3), li("enchanted_golden_apple", 1),
                 li("music_disc_otherside", 1)),
            pool(1, empty(1), lore_entry(BOOK, 2))),
        f"{NAME}_archaeology": loot_table(
            f"{NAME}_archaeology",
            pool(1, li("gold_nugget", 4), li("emerald", 2), li("bone", 3), li("candle", 2), li("coal", 2),
                 li("mourner_pottery_sherd", 2), li("skull_pottery_sherd", 2), li("explorer_pottery_sherd", 2),
                 li("heartbreak_pottery_sherd", 1), li("echo_shard", 1), li("diamond", 1)), kind="archaeology"),
    },
}


def dfloor(rng, x, z):
    r = rng.random()
    return B(DT) if r < 0.45 else B(PD) if r < 0.65 else B(DB) if r < 0.85 else B("cobbled_deepslate")


# ------------------------------------------------------------------ the nave
def nave(seed):
    b = Build(f"{NAME}_nave", 41, 22, 41, seed=seed)
    rng = b.rng
    x0, x1, z0, z1 = 6, 34, 3, 37                      # outer walls
    for x in range(41):
        for z in range(41):
            inside = x0 <= x <= x1 and z0 <= z <= z1
            if inside:
                b.set(x, 0, z, dfloor(rng, x, z))
            elif rng.random() < 0.6:
                b.set(x, 0, z, B(rng.choice(["tuff", "deepslate", "cobbled_deepslate", "gravel"])) if
                      rng.random() < 0.5 else B("tuff"))
            if inside:
                for y in (1, 2, 3):
                    b.set(x, y, z, AIR)
    # walls standing to a ragged height, with tall window slots
    for (x, z) in rect_ring(x0, z0, x1, z1):
        h = int(14 * (0.15 + 0.85 * abs(math.sin(x * 0.19 + z * 0.13 + 0.5)) ** 1.5) - rng.random() * 3)
        for y in range(1, max(2, h)):
            b.set(x, y, z, B(PD) if y in (1, 9) else B(DB))
        along_x = z in (z0, z1)
        k = (x - x0) if along_x else (z - z0)
        if k % 5 == 0 and not (x in (x0, x1) and z in (z0, z1)):
            for y in range(1, max(2, h) + 1):                 # pilasters
                b.set(x, y, z, B(DT))
        elif k % 5 == 2:
            for y in (4, 5, 6):
                if b.get(x, y, z) is not None:
                    b.set(x, y, z, AIR)
    for x in range(19, 22):                                    # the great door (south)
        for y in range(1, 7):
            b.set(x, y, z1, AIR)
    b.set(19, 6, z1, stairs("deepslate_brick", "east", "top"))
    b.set(21, 6, z1, stairs("deepslate_brick", "west", "top"))
    for x in range(16, 25):
        b.set(x, 0, z1 + 1, B(PD))
        b.set(x, 0, z1 + 2, stairs("deepslate_tile", "north") if 18 <= x <= 22 else B(DT))
    # the aisles' columns and the broken arches between them
    cols = [(x, z) for x in (12, 28) for z in range(8, 33, 5)]
    for (x, z) in cols:
        h = 12 if rng.random() < 0.55 else rng.randint(3, 8)
        b.set(x, 1, z, B("chiseled_deepslate"))
        for y in range(2, h):
            b.set(x, y, z, B(PD) if y % 4 else B(DT))
        if h == 12:
            b.set(x, 12, z, B("chiseled_deepslate"))
            for d in (-1, 1):                                  # springers of the fallen arches
                b.set(x, 11, z + d, stairs("deepslate_brick", "south" if d < 0 else "north", "top"))
                b.set(x + d, 12, z, stairs("deepslate_brick", "east" if d < 0 else "west", "top"))
    for z in (8, 13, 28):
        if rng.random() < 0.7:
            for x in range(13, 28):                            # a surviving transverse arch
                if 16 <= x <= 24:
                    continue
                b.set(x, 12, z, B(DB))
            b.set(15, 11, z, stairs("deepslate_brick", "west", "top"))
            b.set(25, 11, z, stairs("deepslate_brick", "east", "top"))
    # silted aisles and rubble
    for _ in range(26):
        x, z = rng.randint(x0 + 1, x1 - 1), rng.randint(z0 + 1, z1 - 1)
        if b.get(x, 1, z) is not None and b.get(x, 1, z).short == "air":
            b.set(x, 1, z, B(rng.choice(["gravel", "gravel", "cobbled_deepslate", "deepslate_brick_slab"]))
                  if rng.random() < 0.85 else B("cracked_deepslate_bricks"))
    for (cx, cz, r) in ((9, 20, 2.5), (31, 15, 2.8), (24, 33, 2.2), (31, 30, 1.8)):
        rock_mound(b, rng, cx, cz, r, r, 3, mats=(DB, "cobbled_deepslate", "cracked_deepslate_bricks", "gravel",
                                                  DT))
    # the dais, altar and candles at the north end
    for x in range(14, 27):
        for z in range(z0 + 1, z0 + 7):
            b.set(x, 1, z, B(PD) if 15 <= x <= 25 and z <= z0 + 5 else stairs("polished_deepslate", "south"))
    for x in range(15, 26):
        b.set(x, 1, z0 + 6, stairs("polished_deepslate", "south"))
    for x in (14, 26):
        for z in range(z0 + 1, z0 + 7):
            b.set(x, 1, z, stairs("polished_deepslate", "east" if x == 14 else "west"))
    for x in range(18, 23):
        b.set(x, 2, z0 + 3, B("chiseled_deepslate") if x in (18, 22) else B(PD))
        b.set(x, 3, z0 + 3, slab("polished_deepslate", "bottom") if x in (18, 22) else B(PD) if x == 20 else
              slab("deepslate_tile", "top"))
    for x in (19, 21):
        b.set(x, 3, z0 + 3, B("candle", candles=3, lit=True))
    b.set(20, 4, z0 + 3, B("candle", candles=4, lit=True))
    b.pot(18, 4, z0 + 3, "south", ("brick", "mourner_pottery_sherd", "brick", "brick"))
    b.lectern(20, 2, z0 + 5, "north", journal(BOOK))
    for x in (15, 25):                                         # soul lanterns on chains from the wall
        for y in (6, 7, 8):
            b.set(x, y, z0 + 1, B("iron_chain", axis="y"))
        b.set(x, 9, z0 + 1, B(DB))
        b.set(x, 5, z0 + 1, B("soul_lantern", hanging=True))
    for (x, z) in ((16, z0 + 2), (24, z0 + 2), (17, z0 + 5), (23, z0 + 5)):
        b.set(x, 2, z, B("candle", candles=rng.randint(1, 4), lit=True))
    # the stair down behind the altar to the crypt
    for y in (1, 2, 3):
        b.set(20, y, z0 + 1, AIR)
    b.set(20, 1, z0 + 1, B(PD))
    b.jigsaw(20, 0, z0 + 1, "down", target=J_CRYPT, pool=P_CRYPT,
             final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    b.set(20, 1, z0 + 1, B("ladder", facing="south"))
    b.set(20, 2, z0 + 1, AIR)
    # a public chest among the rubble, decorated pots by the columns, soul lanterns low on columns
    b.chest(10, 1, 30, "east", LOOT)
    for (x, z) in ((11, 18), (29, 23), (13, 33)):
        b.pot(x, 1, z, "north", ("brick", rng.choice(["skull", "mourner", "explorer", "heartbreak"]) +
                                 "_pottery_sherd", "brick", "brick"))
    for (x, z) in ((12, 18), (28, 18), (12, 28), (28, 28)):
        b.set(x + (1 if x < 20 else -1), 1, z, B("cobbled_deepslate"))
        b.set(x + (1 if x < 20 else -1), 2, z, B("soul_lantern"))
    for z in (12, 24):
        for x in (x0 + 1, x1 - 1):
            b.set(x, 1, z, B("candle", candles=rng.randint(1, 3), lit=True))
    for (x, y, z) in ((x0 + 1, 7, 10), (x1 - 1, 6, 22), (13, 10, 33)):
        if b.get(x, y, z) is not None and b.get(x, y, z).short == "air":
            b.set(x, y, z, B("cobweb"))
    prune_unsupported(b, keep=lambda st: st.short == "jigsaw")
    drop_orphans(b)
    lichen(b, rng, 0.07)
    # outworks off the east and west walls and the great door
    b.jigsaw(0, 1, 20, "west", target=J_OUT, pool=P_OUT)
    b.jigsaw(40, 1, 20, "east", target=J_OUT, pool=P_OUT)
    b.jigsaw(20, 1, 40, "south", target=J_OUT, pool=P_OUT)
    for (x, z) in ((0, 20), (40, 20), (20, 40)):
        b.set(x, 0, z, B("cobbled_deepslate"))
    return b


# ------------------------------------------------------------------ crypt
def crypt():
    b = Build(f"{NAME}_crypt", 13, 7, 13, seed=1849641)
    rng = b.rng
    for x in range(13):
        for z in range(13):
            for y in range(7):
                b.set(x, y, z, B("deepslate", axis="y") if rng.random() < 0.6 else
                      B(rng.choice(["tuff", "cobbled_deepslate"])))
    b.air(1, 1, 2, 11, 4, 11)
    for x in range(1, 12):
        for z in range(2, 12):
            b.set(x, 0, z, B(DT) if (x + z) % 2 else B(PD))
    for (x, z) in rect_ring(1, 2, 11, 11):
        b.set(x, 4, z, B(DB))
    for x in (4, 8):                                           # vault ribs
        for z in range(2, 12):
            b.set(x, 4, z, B(DB))
    b.air(6, 1, 1, 6, 6, 1)
    for y in range(1, 7):
        b.set(6, y, 1, B("ladder", facing="south"))
    b.jigsaw(6, 6, 1, "up", name=J_CRYPT, final="minecraft:ladder[facing=south,waterlogged=false]", top="north")
    # sarcophagi: chiseled lids on polished bases, candles at their heads
    for (x, z) in ((2, 5), (2, 9), (9, 5), (9, 9)):
        for dz in (0, 1):
            b.set(x, 1, z + dz, B(PD))
            b.set(x + 1, 1, z + dz, B(PD))
            b.set(x, 2, z + dz, slab("deepslate_tile", "bottom"))
            b.set(x + 1, 2, z + dz, slab("deepslate_tile", "bottom"))
        b.set(x if x < 6 else x + 1, 3, z, AIR)
        b.set(x, 1, z - 1, B("candle", candles=rng.randint(1, 3), lit=True))
    b.chest(6, 1, 11, "north", HIDDEN)
    b.set(5, 1, 11, B("chiseled_deepslate"))
    b.set(7, 1, 11, B("chiseled_deepslate"))
    b.set(5, 2, 11, B("soul_lantern"))
    b.set(7, 2, 11, B("soul_lantern"))
    b.set(10, 3, 3, B("cobweb"))
    b.set(2, 3, 11, B("cobweb"))
    b.set(6, 1, 7, B("skeleton_skull", rotation=4))
    return b


# ------------------------------------------------------------------ outworks
def out_site(name, sx, sy, sz, seed):
    b = Build(name, sx, sy, sz, seed=seed)
    rng = b.rng
    for x in range(sx):
        for z in range(sz):
            if rng.random() < 0.7:
                b.set(x, 0, z, dfloor(rng, x, z) if rng.random() < 0.5 else B(rng.choice(["tuff", "gravel"])))
            for y in (1, 2):
                b.set(x, y, z, AIR)
    b.jigsaw(sx // 2, 1, 0, "north", name=J_OUT)
    return b, rng


def gate_arch():
    b, rng = out_site(f"{NAME}_gate", 13, 14, 9, 1849651)
    for x in (2, 3, 9, 10):
        for z in (3, 4, 5):
            h = 11 if x in (3, 9) else 9
            for y in range(1, h):
                b.set(x, y, z, B(DB) if (y % 4) else B(PD))
    for x in range(4, 9):
        for z in (3, 4, 5):
            if x in (4, 8):
                b.set(x, 8, z, stairs("deepslate_brick", "east" if x == 4 else "west", "top"))
            b.set(x, 9, z, B(DB) if not (x == 6 and rng.random() < 0.5) else AIR)
            b.set(x, 10, z, B(DT) if x in (5, 6, 7) and rng.random() < 0.7 else AIR)
    b.set(6, 9, 4, B("chiseled_deepslate"))
    for (x, z) in ((3, 6), (9, 6)):
        b.set(x, 3, z, B("soul_wall_torch", facing="south"))
    rock_mound(b, rng, 11.5, 7, 1.5, 1.5, 2, mats=(DB, "cobbled_deepslate", "cracked_deepslate_bricks"))
    b.set(6, 1, 7, B("gravel"))
    b.set(5, 1, 6, B("gravel"))
    prune_unsupported(b, keep=lambda st: st.short == "jigsaw")
    return b


def tower_stump():
    b, rng = out_site(f"{NAME}_tower", 13, 16, 14, 1849653)
    cx, cz = 6, 8
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if 3.0 <= d <= 4.3:
                h = int(10 + 4 * math.sin(math.atan2(z - cz, x - cx) * 2 + 1) - rng.random() * 2)
                for y in range(1, h):
                    b.set(x, y, z, B(DB) if y % 5 else B(PD))
            elif d < 3.0:
                b.set(x, 0, z, B(DT))
                for y in range(1, 13):
                    b.set(x, y, z, AIR)
    for y in (1, 2, 3):                                        # doorway toward the nave
        b.set(cx, y, cz - 4, AIR)
        b.set(cx, y, cz - 3, AIR)
    # a stair spiralling up the inside wall to a broken landing
    ring = [(cx - 2, cz - 1), (cx - 2, cz), (cx - 2, cz + 1), (cx - 1, cz + 2), (cx, cz + 2), (cx + 1, cz + 2),
            (cx + 2, cz + 1), (cx + 2, cz), (cx + 2, cz - 1)]
    fac = ["south", "south", "south", "east", "east", "east", "north", "north", "north"]
    for i, ((x, z), f) in enumerate(zip(ring, fac)):
        b.set(x, 1 + i, z, stairs("deepslate_brick", f))
        if i > 0:
            b.set(x, i, z, B(DB))
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 1):
            if rng.random() < 0.7:
                b.set(x, 9, z, B(DT))
    b.chest(cx + 1, 10, cz - 1, "west", LOOT)
    b.set(cx - 1, 10, cz - 1, B("candle", candles=2, lit=True))
    b.set(cx, 1, cz, B("gravel"))
    b.set(cx + 1, 1, cz + 1, B("gravel"))
    b.set(cx, 8, cz, B("soul_lantern", hanging=True))
    prune_unsupported(b, keep=lambda st: st.short == "jigsaw")
    drop_orphans(b)
    lichen(b, rng, 0.08)
    return b


def colonnade():
    b, rng = out_site(f"{NAME}_colonnade", 9, 14, 17, 1849655)
    for z in range(3, 17, 4):
        for x in (1, 7):
            h = 10 if rng.random() < 0.6 else rng.randint(2, 6)
            b.set(x, 1, z, B("chiseled_deepslate"))
            for y in range(2, h):
                b.set(x, y, z, B(PD))
            if h == 10:
                b.set(x, 10, z, B("chiseled_deepslate"))
    for z in range(2, 17):
        for x in range(2, 7):
            b.set(x, 0, z, B(DT) if (x + z) % 3 else B(PD))
    # a fallen statue: a toppled column of tiles with its head beside it
    for z in range(7, 12):
        b.set(4, 1, z, B("deepslate_tiles"))
    b.set(5, 1, 12, B("chiseled_deepslate"))
    b.set(3, 1, 8, slab("deepslate_tile", "bottom"))
    for z in (4, 14):
        b.set(4, 1, z, B("candle", candles=rng.randint(2, 4), lit=True))
    b.pot(2, 1, 15, "north", ("brick", "brick", "skull_pottery_sherd", "brick"))
    b.set(6, 1, 13, B("gravel"))
    b.set(2, 1, 6, B("gravel"))
    b.set(5, 1, 15, B("soul_lantern"))
    prune_unsupported(b, keep=lambda st: st.short == "jigsaw")
    lichen(b, rng, 0.08)
    return b


def pools():
    return {
        "start": [Piece(nave(1849601), 1, PROC)],
        "crypt": [Piece(crypt(), 1, PROC)],
        "outworks": [Piece(gate_arch(), 3, PROC), Piece(tower_stump(), 2, PROC), Piece(colonnade(), 2, PROC),
                     Empty(1)],
    }
