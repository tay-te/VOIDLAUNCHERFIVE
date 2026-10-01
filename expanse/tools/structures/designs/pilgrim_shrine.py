"""Pilgrim places of the cloud forest.

start pool (3 variants)
  spur  : a mossy rock spur climbed by steps cut into its side, a tiny roofed shrine
          on top with a bell, candles and an offering box, prayer-flag poles round its
          foot; a hollow in the rock at the bottom, behind the vines, keeps a stash
  cave  : a hermit's cave in a mossy outcrop - moss bed, candles, the pilgrims' book on
          a lectern, a spring in the back; a gong on a frame outside; something is
          tucked in an alcove behind the hanging roots
  stupa : a small white stupa on a stepped plinth with a golden finial, a row of
          prayer wheels, butter lamps and flag poles; the relic box is sealed in the
          plinth behind a carved panel (hidden chest)
"""
import math

from blocks import AIR, B
from builder import Build, slab, stairs
from common import vines_on
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import book, flag_pole, flora, ground_item, pick, ring, trail

NAME = "pilgrim_shrine"
STRUCTURE = dict(biomes=["cloud_forest"], size=1, max_distance=32, set="biome_sights")
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
ROCK = [("stone", 4), ("mossy_cobblestone", 3), ("andesite", 2), ("cobblestone", 1), ("moss_block", 1)]
GROUND = ["moss_block", "grass_block", "grass_block"]
PATH = [("mossy_cobblestone", 2), ("coarse_dirt", 3), ("gravel", 1), ("dirt_path", 2)]
FLAGS = ["red", "yellow", "green", "blue", "white"]


def rock_mound(b, rng, cx, cz, profile, skip=lambda x, y, z: False):
    """A lumpy outcrop: profile[y] = radius at height y."""
    for y, r in enumerate(profile):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            for z in range(int(cz - r) - 1, int(cz + r) + 2):
                d = math.hypot(x - cx, z - cz) + (rng.random() - 0.5) * 0.6
                if d <= r and b.inb(x, y, z) and not skip(x, y, z):
                    b.set(x, y, z, pick(rng, ROCK))


def flag_ring(b, rng, cx, cz, r, n, y=1):
    for i in range(n):
        th = 2 * math.pi * i / n + rng.random() * 0.4
        x, z = int(round(cx + r * math.sin(th))), int(round(cz + r * math.cos(th)))
        if b.inb(x, y, z) and b.get(x, y, z) is None and b.get(x, 0, z) is None:
            b.set(x, 0, z, B("cobblestone"))
            cols = rng.sample(FLAGS, 2)
            flag_pole(b, x, z, rng.choice([3, 4]), cols, "spruce_log", rng, y=y,
                      pattern_sets=[[("stripe_center", "white")], [("border", rng.choice(FLAGS))]])


def spur():
    b = Build(f"{NAME}_spur", 15, 15, 15, seed=1907491)
    rng = b.rng
    cx, cz = 7, 7
    prof = [4.6, 4.2, 3.8, 3.4, 3.0, 2.7, 2.4, 2.2, 2.0]
    rock_mound(b, rng, cx, cz, prof)
    # steps cut up the outside, winding clockwise from the south
    steps = []
    for i in range(0, 18):
        th = -math.pi / 2 + i * 0.36
        y = 1 + i // 2
        if y > 8:
            break
        r = prof[min(y, len(prof) - 1)] + 0.2
        x, z = int(round(cx + r * math.cos(th))), int(round(cz - r * math.sin(th)))
        steps.append((x, y, z))
    prev = None
    for (x, y, z) in steps:
        if prev and prev[1] < y:
            dx, dz = x - prev[0], z - prev[2]
            f = "east" if dx > 0 else "west" if dx < 0 else "south" if dz > 0 else "north"
            b.set(x, y, z, stairs("stone_brick", f))
        else:
            b.set(x, y, z, slab("stone_brick", "bottom") if prev else B("stone_bricks"))
        for k in range(1, 3):
            if b.inb(x, y + k, z):
                b.set(x, y + k, z, AIR)
        for yy in range(0, y):                        # make sure the step stands on rock
            if b.get(x, yy, z) is None:
                b.set(x, yy, z, pick(rng, ROCK))
        prev = (x, y, z)
    # the top: a little roofed shrine with bell, candles and the offering box
    T = len(prof)
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            b.set(x, T - 1, z, B("stone_bricks"))
            b.air(x, T, z, x, T + 2, z)
    for (x, z) in ((cx - 1, cz - 1), (cx + 1, cz - 1), (cx - 1, cz + 1), (cx + 1, cz + 1)):
        b.set(x, T, z, B("dark_oak_fence"))
        b.set(x, T + 1, z, B("dark_oak_fence"))
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            edge = abs(x - cx) == 2 or abs(z - cz) == 2
            if edge:
                f = "south" if z == cz - 2 else "north" if z == cz + 2 else "east" if x == cx - 2 else "west"
                b.set(x, T + 2, z, stairs("dark_oak", f))
            else:
                b.set(x, T + 2, z, B("dark_oak_planks"))
    b.set(cx, T + 3, cz, slab("dark_oak"))
    b.bell(cx, T + 1, cz, attachment="ceiling", facing="north")
    b.barrel(cx, T, cz - 1, "up", LOOT)
    b.pot(cx - 1, T, cz, "east", ("brick", "friend_pottery_sherd", "brick", "brick"))
    b.set(cx, T, cz + 1, B("white_candle", candles=3, lit=True))
    ground_item(b, cx + 1, T, cz, book(BOOKS[NAME]), rotation=1)
    # the hollow at the foot, behind vines: the stash
    hz = cz + 1
    hx = max(x for x in range(15) if b.get(x, 1, hz) is not None and b.get(x, 1, hz).short != "air")
    b.set(hx, 1, hz, AIR)
    b.chest(hx - 1, 1, hz, "east", HIDDEN)
    b.set(hx - 1, 2, hz, AIR)                          # headroom so the lid lifts
    b.set(hx, 2, hz, pick(rng, ROCK))
    b.set(hx, 0, hz, pick(rng, ROCK))
    for y in (2, 1):                                  # a curtain of vines over the hollow
        b.set(hx + 1, y, hz, B("vine", west=True))
    vines_on(b, rng, 0.12, ymin=2, max_len=3)
    flag_ring(b, rng, cx, cz, 6.2, 5)
    trail(b, [steps[0][::2], (cx, 13), (cx + 1, 14)], rng, PATH, width=1, fade=0.4)
    flora(b, [(x, z) for x in range(15) for z in range(15)], rng, 0.25, GROUND)
    return b


def cave():
    b = Build(f"{NAME}_cave", 14, 9, 13, seed=1907493)
    rng = b.rng
    cx, cz = 6, 5
    rock_mound(b, rng, cx, cz, [5.2, 5.0, 4.6, 4.2, 3.6, 2.8, 1.8])
    for x in range(cx - 2, cx + 3):                   # the cave: a room 5 x 4, 3 high, mouth to the south
        for z in range(cz - 2, cz + 2):
            for y in (1, 2, 3 if abs(x - cx) < 2 else 2):
                b.set(x, y, z, AIR)
            b.set(x, 0, z, B("moss_block") if rng.random() < 0.5 else B("coarse_dirt"))
    for z in range(cz + 2, cz + 6):
        for x in (cx - 1, cx, cx + 1):
            for y in (1, 2):
                b.set(x, y, z, AIR)
            if b.get(x, 0, z) is None or b.get(x, 0, z).short not in ("air",):
                b.set(x, 0, z, B("coarse_dirt") if x == cx else B("moss_block"))
    b.bed(cx - 2, 1, cz - 1, "north", "green")
    b.set(cx - 2, 1, cz + 1, B("moss_carpet"))
    b.lectern(cx + 1, 1, cz + 1, "west", book(BOOKS[f"{NAME}_cave"]))
    for (x, z, n) in ((cx - 1, cz - 2, 3), (cx + 2, cz - 1, 2), (cx, cz - 2, 1)):
        b.set(x, 1, z, B("candle", candles=n, lit=True))
    b.barrel(cx + 2, 1, cz, "west", LOOT)
    b.set(cx + 2, 0, cz - 2, B("water", level=0))      # the spring, a basin sunk in the floor
    b.set(cx + 2, 1, cz - 2, AIR)
    # alcove behind the hanging roots (north-west)
    b.set(cx - 3, 1, cz - 2, AIR)
    b.chest(cx - 4, 1, cz - 2, "east", HIDDEN)
    b.set(cx - 3, 2, cz - 2, pick(rng, ROCK))
    b.set(cx - 3, 1, cz - 2, B("hanging_roots"))     # a curtain of roots across the alcove
    # the gong on its frame outside the mouth
    for y in (1, 2, 3):
        b.set(cx + 3, y, cz + 6, B("dark_oak_fence"))
        b.set(cx + 5, y, cz + 6, B("dark_oak_fence"))
    b.set(cx + 4, 3, cz + 6, B("dark_oak_fence"))
    b.bell(cx + 4, 2, cz + 6, attachment="ceiling", facing="east")
    vines_on(b, rng, 0.1, ymin=2, max_len=2)
    trail(b, [(cx, cz + 5), (cx - 1, 10), (cx, 12)], rng, PATH, width=1, fade=0.4)
    flora(b, [(x, z) for x in range(14) for z in range(13)], rng, 0.25, GROUND)
    return b


def stupa():
    b = Build(f"{NAME}_stupa", 15, 13, 15, seed=1907495)
    rng = b.rng
    c = 7
    for y, r in ((0, 3), (1, 3), (2, 2)):             # stepped plinth
        for x in range(c - r, c + r + 1):
            for z in range(c - r, c + r + 1):
                b.set(x, y, z, B("polished_andesite") if y == 0 else B("calcite"))
    for y, r in ((3, 1.9), (4, 2.1), (5, 1.9), (6, 1.3)):   # dome
        for (x, z) in ring(c, c, 0, r, (15, 15)):
            b.set(x, y, z, B("calcite") if y < 6 else B("smooth_quartz"))
    for x in range(c - 1, c + 2):                     # harmika and spire
        for z in range(c - 1, c + 2):
            b.set(x, 7, z, B("chiseled_quartz_block") if (x, z) == (c, c) else slab("smooth_quartz"))
    b.set(c, 8, c, B("gold_block"))
    b.set(c, 9, c, B("lightning_rod", facing="up"))
    # the relic box set into the plinth, hidden behind a painted panel (east face)
    b.chest(c + 3, 1, c, "east", HIDDEN)
    b.painting(c + 4, 1, c, "east", "meditative")
    # butter lamps on the plinth steps, a row of prayer wheels (barrels on posts) to the west
    for (x, z) in ((c - 3, c - 3), (c + 3, c - 3), (c - 3, c + 3), (c + 3, c + 3)):
        b.set(x, 1, z, B("yellow_candle", candles=rng.randint(2, 4), lit=True))
    for z in range(c - 3, c + 4, 2):
        b.set(1, 0, z, B("polished_andesite"))
        b.set(1, 1, z, B("dark_oak_fence"))
        b.set(1, 2, z, B("barrel", facing="north"))
    b.set(1, 1, c + 1, B("dark_oak_fence"))
    b.barrel(1, 2, c + 1, "north", LOOT)
    b.set(1, 0, c + 1, B("polished_andesite"))
    ground_item(b, c, 3, c + 3, book(BOOKS[f"{NAME}_stupa"]), rotation=0)
    for (x, z) in ((c, 0), (14, c), (c, 14)):
        b.set(x, 0, z, B("cobblestone"))
        flag_pole(b, x, z, 5, rng.sample(FLAGS, 2), "spruce_log", rng,
                  pattern_sets=[[("stripe_center", "white")], [("circle", "yellow")]])
    trail(b, [(c, c + 4), (c - 1, 12), (c, 14)], rng, PATH, width=2, fade=0.6)
    flora(b, [(x, z) for x in range(15) for z in range(15) if max(abs(x - c), abs(z - c)) > 3], rng, 0.22, GROUND)
    return b


def pools():
    return {"start": [Piece(spur(), 3, "small_cloud"), Piece(cave(), 2, "small_cloud"),
                      Piece(stupa(), 2, "small_cloud")]}


DATA = data_for(NAME, ["small_cloud"])
