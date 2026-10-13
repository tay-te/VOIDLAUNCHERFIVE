"""Buried giants of the deserts. One design, two palettes (separate structures):
  sand_colossus         red sandstone, red sand           - red dunes
  sand_colossus_desert  sandstone, yellow sand            - the desert and the salt flats

start pool (3 variants)
  head    : a colossal stone head sunk to the chin and leaning, crowned, its face
            cracked; drifts banked against it, fingers of a hand breaking the sand
            nearby, a digger's awning. A crack in the back of the skull opens on a
            little chamber (hidden chest); the sand around gives up finds to a brush
  obelisk : a toppled obelisk in three pieces with its stump still standing on a
            stepped base, glyph bands, the pyramidion lying apart; a box lies under
            the drift at the broken end
  gate    : two pylons and a lintel poking out of a dune, the doorway between them
            packed with sand; dig through to the sealed room behind (hidden chest,
            pots), or brush the sill for what the wind left
Up to five sand cells per instance become suspicious sand (archaeology).
"""
import math

from blocks import AIR, B
from builder import Build, item, slab, stairs
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import book, flora, ground_item, pick

PALETTES = {
    "red": dict(
        name="sand_colossus", biomes=["red_dunes"], procs="small_dunes", seed=0,
        os="red_sandstone", sand="red_sand", chiseled="chiseled_red_sandstone",
        smooth="smooth_red_sandstone", cut="cut_red_sandstone",
        stone=[("smooth_red_sandstone", 4), ("red_sandstone", 3), ("cut_red_sandstone", 2)],
        disc="yellow_glazed_terracotta", books=("sand_colossus", "sand_colossus_obelisk", "sand_colossus_gate")),
    "desert": dict(
        name="sand_colossus_desert", biomes=["minecraft:desert", "salt_flats"], procs="small_desert", seed=60,
        os="sandstone", sand="sand", chiseled="chiseled_sandstone", smooth="smooth_sandstone", cut="cut_sandstone",
        stone=[("smooth_sandstone", 4), ("sandstone", 3), ("cut_sandstone", 2)],
        disc="orange_glazed_terracotta",
        books=("sand_colossus_desert", "sand_colossus_desert_obelisk", "sand_colossus_gate")),
}
DRY = ["dead_bush", "short_dry_grass", "short_dry_grass"]


def drift(b, rng, p, cx, cz, r, h, skip=lambda x, y, z: False):
    """A sand drift (a smooth mound) of the palette's sand, supported all the way down."""
    for x in range(int(cx - r) - 1, int(cx + r) + 2):
        for z in range(int(cz - r) - 1, int(cz + r) + 2):
            d = math.hypot(x - cx, z - cz)
            if d > r or not b.inb(x, 0, z):
                continue
            top = int(round(h * math.cos(d / r * math.pi / 2) + rng.random() * 0.5))
            for y in range(0, top + 1):
                if b.inb(x, y, z) and b.get(x, y, z) is None and not skip(x, y, z):
                    b.set(x, y, z, B(p["sand"]))


def head(p):
    b = Build(f"{p['name']}_head", 16, 12, 16, seed=1907471 + p["seed"])
    rng = b.rng
    x0, x1, z0, z1 = 4, 10, 4, 9                      # head block; the face looks south (+z)
    cx = (x0 + x1) / 2
    for y in range(0, 10):
        lean = 1 if y >= 6 else 0                     # the head tips east
        shrink = 0 if y < 7 else 1 if y < 9 else 2
        for x in range(x0 + shrink, x1 - shrink + 1):
            for z in range(z0 + shrink, z1 - shrink + 1):
                corner = x in (x0 + shrink, x1 - shrink) and z in (z0 + shrink, z1 - shrink)
                if corner and (y > 5 or y < 1):
                    continue
                b.set(x + lean, y, z, pick(rng, p["stone"]))
    # the face (south): brow ridge, deep-set eyes, a broad nose, a hard mouth; a crown of sun discs
    f = z1 + 1
    for x in range(6, 11):                            # brow (the head leans one east from y=6)
        b.set(x, 6, f, stairs(p["os"], "north", "top"))
    for x in (5, 6, 8, 9):                            # eye sockets, carved pupils at their backs
        b.set(x, 5, z1, AIR)
        b.set(x, 5, z1 - 1, B(p["chiseled"]))
    b.set(7, 5, z1, B(p["smooth"]))
    for y in (3, 4):                                  # nose, its tip and nostril wings
        b.set(7, y, f, B(p["smooth"]))
    b.set(7, 2, f, stairs(p["os"], "north"))
    b.set(6, 3, f, stairs(p["os"], "east"))
    b.set(8, 3, f, stairs(p["os"], "west"))
    for x in (6, 7, 8):                               # the mouth, a hard straight line
        b.set(x, 1, z1, AIR)
        b.set(x, 1, z1 - 1, B(p["cut"]))
    for x in range(x0 + 1, x1):                       # crown band with sun discs
        b.set(x + 1, 7, z1 - 1, B(p["disc"], facing="north") if x in (5, 7, 9) else
              B(p["chiseled"]))
    # the crack: a scar across the brow and a breach in the back of the skull
    for (x, y, z) in ((9, 6, z1), (9, 7, z1), (10, 7, z1 - 1), (10, 8, z1 - 1)):
        b.set(x, y, z, AIR)
    for x in range(6, 9):                             # chamber inside the head
        for y in (1, 2, 3):
            for z in range(5, 8):
                b.set(x, y, z, AIR)
    b.set(7, 1, z0, AIR)
    b.set(7, 2, z0, AIR)
    b.set(6, 2, z0, AIR)
    b.chest(7, 1, 7, "north", f"expanse:chests/{p['name']}_hidden")
    b.pot(6, 1, 7, "north", ("brick", "prize_pottery_sherd", "brick", "brick"))
    b.set(8, 1, 6, B(p["sand"]))
    b.set(6, 1, 5, B(p["sand"]))
    # drifts banked against the west and north, fallen pieces of the crown on the sand
    drift(b, rng, p, 2.5, 7, 4, 4)
    drift(b, rng, p, 8, 1.5, 4.5, 3, skip=lambda x, y, z: (x, z) == (7, z0 - 1) and y in (1, 2))
    b.set(7, 1, z0 - 1, AIR)
    b.set(7, 2, z0 - 1, AIR)
    for (x, z) in ((12, 11), (13, 9), (2, 12)):
        b.set(x, 0, z, B(p["sand"]))
        b.set(x, 1, z, slab(p["os"]) if rng.random() < 0.5 else stairs(p["os"], rng.choice(["north", "east", "west"])))
    # fingers of a great hand breaking the sand to the south-east
    for (x, z, h) in ((12, 13, 2), (13, 13, 3), (14, 13, 2), (14, 12, 1)):
        for y in range(0, h + 1):
            b.set(x, y, z, pick(rng, p["stone"]))
        b.set(x, h + 1, z, slab(p["os"]))
    # the digger's awning: wool on fences, a crate, brushes and a note
    for (x, z) in ((1, 13), (4, 13), (1, 15), (4, 15)):
        b.set(x, 1, z, B("acacia_fence"))
        b.set(x, 2, z, B("acacia_fence"))
    for x in range(1, 5):
        for z in range(13, 16):
            b.set(x, 3, z, slab("white_wool") if (x + z) % 2 else slab("orange_wool"))
    b.barrel(2, 1, 14, "up", f"expanse:chests/{p['name']}")
    b.set(3, 1, 14, slab("acacia", "top"))
    ground_item(b, 3, 2, 14, item("brush"), rotation=1)
    b.set(3, 1, 15, slab("acacia", "top"))
    ground_item(b, 3, 2, 15, book(BOOKS[p["books"][0]]), rotation=0)
    flora(b, [(x, z) for x in range(16) for z in range(16)], rng, 0.08, [p["sand"]], plants=DRY)
    return b


def obelisk(p):
    b = Build(f"{p['name']}_obelisk", 18, 8, 12, seed=1907473 + p["seed"])
    rng = b.rng

    def shaft(x, y, z, band):
        b.set(x, y, z, B(p["chiseled"]) if band else pick(rng, p["stone"]))

    # stepped base and the standing stump (2x2, 3 high, broken top)
    for x in range(1, 7):
        for z in range(3, 9):
            b.set(x, 0, z, B(p["cut"]))
            if 2 <= x <= 5 and 4 <= z <= 7:
                b.set(x, 1, z, B(p["smooth"]))
    for x in (3, 4):
        for z in (5, 6):
            for y in (2, 3, 4):
                shaft(x, y, z, y == 3)
    b.set(4, 4, 6, slab(p["os"]))
    b.set(3, 5, 5, slab(p["os"]))
    # three fallen segments lying east, the pyramidion apart
    segs = [(7, 9, 5), (10, 13, 5), (14, 15, 6)]
    for (sx0, sx1, sz) in segs:
        for x in range(sx0, sx1 + 1):
            for z in (sz, sz + 1):
                for y in (1, 2):
                    shaft(x, y, z, x % 4 == 0)
                b.set(x, 0, z, B(p["sand"]))
    b.set(16, 1, 9, stairs(p["os"], "west"))
    b.set(16, 1, 10, stairs(p["os"], "north"))
    b.set(16, 2, 9, slab(p["os"]))
    # drifts over the broken ends; a box under the one at the break
    drift(b, rng, p, 9.5, 4, 2.6, 2)
    drift(b, rng, p, 14, 8.5, 2.8, 2)
    b.chest(13, 0, 9, "north", f"expanse:chests/{p['name']}_hidden")
    b.set(13, 1, 9, B(p["sand"]))
    b.pot(1, 1, 9, "east", ("brick", "burn_pottery_sherd", "brick", "brick"), loot=f"expanse:chests/{p['name']}")
    ground_item(b, 2, 1, 2, book(BOOKS[p["books"][1]]), rotation=1)
    b.set(2, 0, 2, B(p["cut"]))
    flora(b, [(x, z) for x in range(18) for z in range(12)], rng, 0.08, [p["sand"]], plants=DRY)
    return b


def gate(p):
    b = Build(f"{p['name']}_gate", 15, 9, 14, seed=1907475 + p["seed"])
    rng = b.rng
    # the sealed room behind the doorway (x 5..9, z 2..5), inside a great dune
    for x in range(4, 11):
        for z in range(1, 7):
            for y in range(0, 5):
                wall = x in (4, 10) or z in (1, 6) or y in (0, 4)
                b.set(x, y, z, pick(rng, p["stone"]) if wall else AIR)
    b.chest(7, 1, 2, "south", f"expanse:chests/{p['name']}_hidden")
    b.pot(5, 1, 2, "south", ("brick", "prize_pottery_sherd", "brick", "brick"), loot=f"expanse:chests/{p['name']}")
    b.pot(9, 1, 2, "south", ("brick", "plenty_pottery_sherd", "brick", "brick"))
    b.set(6, 1, 2, B(p["sand"]))
    b.set(8, 1, 2, B("candle", candles=3, lit=False))
    b.set(7, 3, 2, B(p["chiseled"]))
    # pylons and lintel on the south face; the doorway packed with sand
    for x in (5, 9):
        for y in range(0, 7):
            b.set(x, y, 7, B(p["cut"]) if y % 3 else B(p["chiseled"]))
            b.set(x, y, 6, B(p["cut"]))
    for x in range(4, 11):
        b.set(x, 6, 7, B(p["smooth"]))
        b.set(x, 7, 7, slab(p["os"]) if x not in (4, 10) else stairs(p["os"], "east" if x == 4 else "west"))
    for x in (6, 7, 8):
        for y in (1, 2, 3):
            b.set(x, y, 6, B(p["sand"]))
            b.set(x, y, 7, B(p["sand"]) if y < 3 else AIR)
    b.set(7, 0, 8, B(p["cut"]))   # the sill
    # the dune heaped over everything but the gate
    drift(b, rng, p, 7, 3.5, 6.8, 6, skip=lambda x, y, z: z >= 7 and 5 <= x <= 9)
    drift(b, rng, p, 12.5, 9, 2.4, 2)
    b.set(11, 0, 11, B(p["cut"]))
    b.set(11, 1, 11, slab(p["os"], "top"))
    ground_item(b, 11, 2, 11, book(BOOKS[p["books"][2]]), rotation=1)
    flora(b, [(x, z) for x in range(15) for z in range(8, 14)], rng, 0.1, [p["sand"]], plants=DRY)
    return b


def variant_module(key):
    p = PALETTES[key]
    name = p["name"]
    structure = dict(biomes=p["biomes"], size=1, max_distance=32, set="biome_sights")

    def pools():
        return {"start": [Piece(head(p), 3, p["procs"]), Piece(obelisk(p), 2, p["procs"]),
                          Piece(gate(p), 2, p["procs"])]}
    return name, structure, data_for(name, [p["procs"]]), pools


NAME, STRUCTURE, DATA, pools = variant_module("red")
