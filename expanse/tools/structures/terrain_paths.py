"""Terrain-matching connector paths (village street style).

A path piece is projection terrain_matching: every block is placed at the
local surface (GravityProcessor), so the path drapes over the slope, and a
rigid piece attached to its far end is dropped onto the terrain height at
that point (JigsawPlacement: non-rigid source -> targetBoxY = surface -
targetJigsawLocalY). Both connector jigsaws sit at local y=1 so the rigid
child's layer 0 lands on the topmost terrain block and its beard ground
(groundLevelDelta 1) matches the convention of the start pieces.
"""
from blocks import AIR, B
from builder import Build

PATH_IN = "expanse:path_in"      # name of a path's back connector (and target of parents)
PIECE_IN = "expanse:piece_in"    # name of a satellite piece's connector (and target of path ends)


def path_piece(name, length, pool_next, rng, mats=(("dirt_path", 6), ("coarse_dirt", 2), ("gravel", 1)),
               width=3, edge_density=0.45, end_final="minecraft:air", stones=None):
    """Straight path running +z from its back connector; ends in a jigsaw to `pool_next`.
    `stones`: optional block id used as stepping stones instead of a continuous path."""
    b = Build(name, width, 2, length, seed=sum(map(ord, name)))
    mid = width // 2
    pool = [m for m, w in mats for _ in range(w)]
    for z in range(length):
        for x in range(width):
            centre = x == mid
            if stones:
                if centre and z % 2 == 0 or (not centre and rng.random() < 0.15):
                    b.set(x, 0, z, B(stones))
            elif centre or rng.random() < edge_density:
                b.set(x, 0, z, B(rng.choice(pool)))
            if b.get(x, 0, z) is not None:
                b.set(x, 1, z, AIR)
    b.jigsaw(mid, 1, 0, "north", name=PATH_IN, target="minecraft:empty", pool="minecraft:empty",
             final="minecraft:air")
    b.jigsaw(mid, 1, length - 1, "south", name="minecraft:empty", target=PIECE_IN, pool=pool_next,
             final=end_final)
    return b


def connector(b, x, y, z, front, pool, final="minecraft:air"):
    """A jigsaw on a rigid piece that sprouts a path (from `pool`) outward."""
    b.jigsaw(x, y, z, front, name="minecraft:empty", target=PATH_IN, pool=pool, final=final)


def piece_in(b, x, y, z, front, final="minecraft:air"):
    """The connector a satellite piece uses to hang off a path end (front points back at the path)."""
    b.jigsaw(x, y, z, front, name=PIECE_IN, target="minecraft:empty", pool="minecraft:empty", final=final)
