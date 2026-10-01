"""Lumen shrine: a ruined, overgrown circular colonnade of lumen-wood columns
on mossy stone-brick bases around a raised dais with the loot pedestal.
Prismite crystals and glowcaps light it; a young lumen tree has rooted in
the ruin and drapes its glowing leaves over the broken architrave."""
import math

from blocks import AIR, B
from builder import Build, prune_unsupported, slab, stairs
from common import circle_cells, ring_cells

NAME = "lumen_shrine"
LOOT = "expanse:chests/lumen_shrine"
C = 7
L = "expanse:lumen"


def build():
    b = Build(NAME, 15, 13, 15, seed=1847481)
    rng = b.rng
    STONE = ["mossy_stone_bricks", "mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks", "moss_block"]

    # ---- floor disc and clearing above it
    for (x, z) in circle_cells(C, C, 6.6):
        b.set(x, 0, z, B(rng.choice(STONE)))
        b.air(x, 1, z, x, 4, z)
    for (x, z) in circle_cells(C, C, 2.2):
        b.set(x, 0, z, B("chiseled_stone_bricks") if (x + z) % 2 == 0 else B("stone_bricks"))
        b.set(x, 1, z, slab("stone_brick"))
    for (x, z) in ring_cells(C, C, 2.2):
        dx, dz = x - C, z - C
        f = ("west" if dx > 0 else "east") if abs(dx) >= abs(dz) else ("north" if dz > 0 else "south")
        b.set(x, 1, z, stairs("mossy_stone_brick" if rng.random() < 0.5 else "stone_brick", f))

    # ---- central pedestal with the chest
    b.set(C, 1, C, B("chiseled_stone_bricks"))
    b.chest(C, 2, C, "south", LOOT)
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.set(C + dx, 1, C + dz, B("expanse:prismite_block"))
        b.set(C + dx, 2, C + dz, B("expanse:prismite_cluster", facing="up"))

    # ---- columns (8 around the ring), two of them broken
    arch = ring_cells(C, C, 5.9)
    cols = []
    for i in range(8):
        a = 2 * math.pi * i / 8 + math.pi / 8
        best = min(arch, key=lambda p: abs(math.atan2(math.sin(math.atan2(p[1] - C, p[0] - C) - a),
                                                      math.cos(math.atan2(p[1] - C, p[0] - C) - a))))
        cols.append(best)
    broken = {2: 3, 5: 2}
    for i, (x, z) in enumerate(cols):
        b.set(x, 0, z, B("stone_bricks"))
        b.set(x, 1, z, B("mossy_stone_bricks"))
        top = broken.get(i, 6)
        for y in range(2, top + 1):
            b.set(x, y, z, B(f"{L}_log", axis="y") if y % 2 == 0 or rng.random() < 0.6 else
                  B("expanse:stripped_lumen_log", axis="y"))
        if i in broken:
            b.set(x, top + 1, z, B("moss_carpet"))
        else:
            b.set(x, 7, z, B("chiseled_stone_bricks"))

    # ---- architrave ring at y8 (missing over the broken columns)
    def near_broken(x, z):
        return any(abs(x - cols[i][0]) + abs(z - cols[i][1]) <= 3 for i in broken)
    for (x, z) in arch:
        if near_broken(x, z):
            continue
        b.set(x, 8, z, B("mossy_stone_bricks") if rng.random() < 0.5 else B("stone_bricks"))
    # inner lip of slabs and a few ribs of the old dome
    for (x, z) in ring_cells(C, C, 4.9):
        if not near_broken(x, z) and rng.random() < 0.7:
            b.set(x, 8, z, slab("stone_brick", "top"))
    for (x, z) in ((C, C - 4), (C, C - 3), (C + 3, C - 3), (C + 4, C)):
        b.set(x, 9, z, slab("mossy_stone_brick"))
    # prune architrave/dome fragments that no longer touch anything
    for _ in range(3):
        for (x, y, z), (st, n) in sorted(b.cells.items()):
            if y >= 8 and st.short != "air":
                if all(b.get(x + dx, y + dy, z + dz) in (None, AIR)
                       for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0), (0, 1, 0))):
                    b.set(x, y, z, None)
    # low walls between some columns
    for (x, z) in ring_cells(C, C, 6.6):
        a = math.atan2(z - C, x - C)
        if abs(math.sin(a)) > 0.92:     # leave north/south entrances open
            continue
        if b.get(x, 1, z) in (None, AIR) and rng.random() < 0.55:
            b.set(x, 1, z, B("mossy_stone_brick_wall") if rng.random() < 0.6 else B("stone_brick_wall"))

    # ---- rubble of the broken columns
    for i in broken:
        x, z = cols[i]
        dx = 1 if x < C else -1
        b.set(x + dx, 1, z, B(f"{L}_log", axis="x"))
        b.set(x + 2 * dx, 1, z, B(f"{L}_log", axis="x"))
        b.set(x, 1, z + (1 if z < C else -1), B("mossy_stone_bricks"))
        b.set(x - dx, 1, z, slab("mossy_stone_brick"))

    # ---- a lumen tree rooted in the ruin (south-west), leaves over the ring
    tx, tz = 2, 11
    b.set(tx, 0, tz, B("moss_block"))
    for y in range(1, 10):
        b.set(tx, y, tz, B(f"{L}_log", axis="y"))
    b.set(tx + 1, 1, tz, B(f"{L}_wood", axis="y"))
    b.set(tx, 1, tz - 1, B(f"{L}_wood", axis="y"))
    for (x, z) in circle_cells(tx, tz, 3.3):
        for y, r in ((8, 3.3), (9, 3.0), (10, 2.2), (11, 1.2)):
            if (x - tx) ** 2 + (z - tz) ** 2 <= r * r and b.get(x, y, z) in (None, AIR):
                if rng.random() < 0.88:
                    b.set(x, y, z, B(f"{L}_leaves"), clip=True)
    # leaves draped along the architrave
    for (x, z) in arch:
        if b.get(x, 8, z) is not None and b.get(x, 8, z).short != "air" and rng.random() < 0.35:
            b.set(x, 9, z, B(f"{L}_leaves"))
            if rng.random() < 0.5 and b.get(x, 7, z) is None:
                b.set(x, 7, z, B("hanging_roots"))

    # ---- moss, glowcaps, crystals
    for (x, z) in circle_cells(C, C, 6.6):
        if b.get(x, 1, z) in (None, AIR):
            r = rng.random()
            if b.get(x, 0, z).short == "moss_block" and r < 0.6:
                b.set(x, 1, z, B("expanse:glowcap") if rng.random() < 0.6 else B("moss_carpet"))
            elif r < 0.12:
                b.set(x, 1, z, B("moss_carpet"))
            elif r < 0.17:
                b.set(x, 0, z, B("moss_block"))
                b.set(x, 1, z, B("expanse:glowcap"))
    for (x, z) in ((C - 4, C + 1), (C + 4, C - 2), (C + 1, C + 4), (C - 2, C - 4)):
        b.set(x, 0, z, B("expanse:prismite_block"))
        if b.get(x, 1, z) in (None, AIR):
            b.set(x, 1, z, B("expanse:prismite_cluster", facing="up"))
    # glowing lichen / vines on the columns
    for i, (x, z) in enumerate(cols):
        for y in range(2, 6):
            if i in broken and y > broken[i]:
                break
            for d, (ox, oz) in (("east", (-1, 0)), ("west", (1, 0)), ("south", (0, -1)), ("north", (0, 1))):
                p = (x + ox, y, z + oz)
                if b.inb(*p) and b.get(*p) in (None, AIR) and rng.random() < 0.18:
                    b.set(*p, B("glow_lichen", **{d: True}) if rng.random() < 0.5 else B("vine", **{d: True}))
    prune_unsupported(b)
    return [b]
