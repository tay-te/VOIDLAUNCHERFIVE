"""House-building kit: log walls with notched corners, foundation skirts,
gable roofs with overhangs and brackets, dormers and chimneys."""
from blocks import AIR, B
from builder import slab, stairs


def skirt(b, x0, z0, x1, z1, y, rng, mats=("cobblestone", "mossy_cobblestone", "stone", "cobblestone"), out=1,
          gap=0.15):
    """Irregular stone ground course around a footprint (cells within `out` of it, at height y)."""
    for x in range(x0 - out, x1 + out + 1):
        for z in range(z0 - out, z1 + out + 1):
            inside = x0 <= x <= x1 and z0 <= z <= z1
            if inside or not b.inb(x, y, z) or b.get(x, y, z) is not None:
                continue
            corner = (x < x0 or x > x1) and (z < z0 or z > z1)
            if rng.random() < (gap + (0.3 if corner else 0)):
                continue
            b.set(x, y, z, B(rng.choice(mats)))


def foundation(b, x0, z0, x1, z1, y0, y1, rng, mats=("cobblestone", "mossy_cobblestone", "stone_bricks")):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                b.set(x, y, z, B(rng.choice(mats)))


def log_walls(b, x0, z0, x1, z1, y0, y1, log_id, post_id=None, notch=True, infill=None):
    """Horizontal log walls (axis along each wall), vertical corner posts, and at every other course
    the logs run one block past the corner (notched cabin corners)."""
    post_id = post_id or log_id
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                b.set(x, y, z, infill(x, y, z) if infill else B(log_id, axis="x"))
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                b.set(x, y, z, infill(x, y, z) if infill else B(log_id, axis="z"))
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            b.set(x, y, z, B(post_id, axis="y"))
        if notch and (y - y0) % 2 == 1:
            for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
                dx = -1 if x == x0 else 1
                dz = -1 if z == z0 else 1
                b.set(x + dx, y, z, B(log_id, axis="x"), clip=True)
                b.set(x, y, z + dz, B(log_id, axis="z"), clip=True)


def roof_gable(b, x0, x1, z0, z1, y, mat, fill, along="x", overhang=1, gable_overhang=1, ridge=None,
               brackets=True, air_inside=True):
    """Gable roof over the wall rectangle (x0..x1, z0..z1). The ridge runs along `along`; slopes rise
    one block per row from height y (the first row sits on the overhang). `fill(x, y, z)` (or a block)
    closes the gable ends. Returns the ridge height."""
    def F(x, yy, z):
        return fill(x, yy, z) if callable(fill) else fill

    if along == "x":
        lo, hi = z0 - overhang, z1 + overhang
        a0, a1 = x0 - gable_overhang, x1 + gable_overhang
    else:
        lo, hi = x0 - overhang, x1 + overhang
        a0, a1 = z0 - gable_overhang, z1 + gable_overhang
    k = 0
    while lo + k <= hi - k:
        p, q = lo + k, hi - k
        yy = y + k
        for a in range(a0, a1 + 1):
            if along == "x":
                cells = [((a, yy, p), "south"), ((a, yy, q), "north")]
            else:
                cells = [((p, yy, a), "east"), ((q, yy, a), "west")]
            if p == q:
                pos = cells[0][0]
                b.set(*pos, ridge or slab(mat), clip=True)
                continue
            for pos, f in cells:
                b.set(*pos, stairs(mat, f), clip=True)
        # gable ends and the attic space
        for g in ((x0, x1) if along == "x" else (z0, z1)):
            for c in range(max(p + 1, z0 if along == "x" else x0), min(q - 1, z1 if along == "x" else x1) + 1):
                pos = (g, yy, c) if along == "x" else (c, yy, g)
                b.set(*pos, F(*pos))
        if air_inside:
            for a in range((x0 if along == "x" else z0) + 1, (x1 if along == "x" else z1)):
                for c in range(max(p + 1, z0 + 1 if along == "x" else x0 + 1),
                               min(q - 1, z1 - 1 if along == "x" else x1 - 1) + 1):
                    pos = (a, yy, c) if along == "x" else (c, yy, a)
                    if b.get(*pos) is None:
                        b.set(*pos, AIR)
        k += 1
    ridge_y = y + k - 1
    if brackets:                                       # little upside-down stair brackets under the eaves
        for a in range(a0 + 1, a1, 3 if (a1 - a0) > 6 else 2):
            if along == "x":
                for z, f in ((z0 - overhang, "north"), (z1 + overhang, "south")):
                    if overhang >= 1 and b.get(a, y - 1, z) is None:
                        b.set(a, y - 1, z, stairs(mat, "south" if f == "north" else "north", "top"), clip=True)
            else:
                for x, f in ((x0 - overhang, "west"), (x1 + overhang, "east")):
                    if overhang >= 1 and b.get(x, y - 1, a) is None:
                        b.set(x, y - 1, a, stairs(mat, "east" if f == "west" else "west", "top"), clip=True)
    return ridge_y


def chimney(b, x, z, y0, y1, rng, w=1, d=1, mats=("cobblestone", "stone_bricks", "mossy_cobblestone"),
            signal=False, cap="stone_brick"):
    """Stone chimney stack (w x d) from y0 to y1 with a smoking campfire on top."""
    for y in range(y0, y1 + 1):
        for dx in range(w):
            for dz in range(d):
                b.set(x + dx, y, z + dz, B(rng.choice(mats)))
    top = y1 + 1
    if signal:
        b.set(x, y1, z, B("hay_block", axis="y"))      # a hay bale under the fire makes tall signal smoke
    b.campfire(x, top, z, lit=True)
    if w > 1 or d > 1:
        for dx in range(w):
            for dz in range(d):
                if (dx, dz) != (0, 0):
                    b.set(x + dx, top, z + dz, slab(cap))


def gable_boards(log_id, plank_id, window_cells=()):
    """Gable fill pattern: vertical boards with a window or two."""
    def f(x, y, z):
        if (x, y, z) in window_cells:
            return B("glass_pane")
        return B(log_id, axis="y") if (x + z) % 3 == 0 else B(plank_id)
    return f
