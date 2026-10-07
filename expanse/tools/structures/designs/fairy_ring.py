"""Fairy rings: rings of mushrooms in meadows and woodland clearings, as real ones grow (the fungus spreads
outward underground, fruiting at its edge). Nothing magic about them, nothing that glows; country folk
step round them all the same.

start pool (3 variants)
  meadow   : a ring of red and brown mushrooms in open grass, the grass inside the ring
             trodden thin and bare in patches, a lusher band of it just outside
  arc      : a wider ring broken into arcs where the ground dips, two squat brown toadstools
             (a stem or two under a flat brown cap) standing in it
  stump    : a ring round an old mossy oak stump in a forest clearing, ferns and moss about it
"""
import math

from blocks import B
from builder import Build
from pieces import Piece
from regions import mc
from small_data import data_for
from smallkit import flora, pick, plant, ring

NAME = "fairy_ring"
STRUCTURE = dict(biomes=["ancient_oakwood", "bluebell_woods", "woodland_glades", "aspen_parkland",
                         *mc("dark_forest", "flower_forest", "forest", "birch_forest", "old_growth_birch_forest", "meadow")],
                 size=1, max_distance=32, set="biome_sights")
MUSHROOMS = [("red_mushroom", 2), ("brown_mushroom", 3)]
TRODDEN = [("grass_block", 5), ("coarse_dirt", 2), ("dirt", 1), ("rooted_dirt", 1)]
LUSH = [("grass_block", 1)]


def mushroom_ring(b, c, r, rng, gaps=0.12, ground="grass_block"):
    """The ring itself: mushrooms on the cells about r out from (c, c), a few missing."""
    for (x, z) in ring(c, c, r - 0.5, r + 0.45, (b.size[0], b.size[2])):
        if rng.random() > gaps:
            plant(b, x, 1, z, pick(rng, MUSHROOMS), ground)


def trodden(b, c, r, rng, density=0.55):
    """Inside the ring the grass is thin: bare earth in patches, as where the fungus has fed."""
    for (x, z) in ring(c, c, 0, r - 0.8, (b.size[0], b.size[2])):
        if rng.random() < density:
            b.set(x, 0, z, pick(rng, TRODDEN))


def lush(b, c, r, rng):
    """Just outside the ring the grass grows thick and tall."""
    flora(b, ring(c, c, r + 0.9, r + 2.2, (b.size[0], b.size[2])), rng, 0.55, ["grass_block"],
          plants=("short_grass", "short_grass", "fern"))


def toadstool(b, x, z, h, rng):
    """A squat brown mushroom: a stem or two under a flat 3x3 cap with the corners off."""
    for y in range(1, h + 1):
        b.set(x, y, z, B("mushroom_stem"))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if abs(dx) + abs(dz) < 2 or rng.random() < 0.3:
                b.set(x + dx, h + 1, z + dz, B("brown_mushroom_block"))


def meadow():
    b = Build(f"{NAME}_meadow", 15, 3, 15, seed=1907441)
    rng = b.rng
    c = 7
    trodden(b, c, 4.6, rng)
    mushroom_ring(b, c, 4.6, rng)
    lush(b, c, 4.6, rng)
    return b


def arc():
    b = Build(f"{NAME}_arc", 19, 5, 19, seed=1907443)
    rng = b.rng
    c = 9
    trodden(b, c, 6.6, rng, density=0.4)
    for (x, z) in ring(c, c, 6.1, 7.05, (19, 19)):
        a = math.atan2(z - c, x - c)
        if math.sin(a * 3 + 0.7) > -0.55 and rng.random() < 0.85:   # three arcs with gaps between
            plant(b, x, 1, z, pick(rng, MUSHROOMS), "grass_block")
    toadstool(b, c + 5, c + 4, 2, rng)
    toadstool(b, c - 6, c - 2, 1, rng)
    lush(b, c, 6.6, rng)
    return b


def stump():
    b = Build(f"{NAME}_stump", 15, 4, 15, seed=1907445)
    rng = b.rng
    c = 7
    for (x, z) in ((c, c), (c + 1, c), (c, c + 1), (c + 1, c + 1)):   # an old oak stump, two blocks across
        b.set(x, 0, z, B("rooted_dirt"))
        b.set(x, 1, z, B("oak_log", axis="y"))
        if rng.random() < 0.5:
            b.set(x, 2, z, B("oak_log", axis="y"))
        elif rng.random() < 0.6:
            b.set(x, 2, z, B("moss_carpet"))
    for (x, z) in ring(c + 0.5, c + 0.5, 1.4, 3.2, (15, 15)):
        if rng.random() < 0.5:
            b.set(x, 0, z, pick(rng, [("moss_block", 3), ("rooted_dirt", 1), ("coarse_dirt", 1)]))
            if rng.random() < 0.3:
                b.set(x, 1, z, B("moss_carpet"))
    mushroom_ring(b, c, 5.1, rng, gaps=0.2, ground="moss_block")
    flora(b, ring(c, c, 6.0, 7.2, (15, 15)), rng, 0.4, ["grass_block", "moss_block"], plants=("fern", "short_grass", "fern"))
    return b


def pools():
    return {"start": [Piece(meadow(), 3, "small_lumen"), Piece(arc(), 2, "small_lumen"), Piece(stump(), 2, "small_lumen")]}


DATA = data_for(NAME, ["small_lumen"])
