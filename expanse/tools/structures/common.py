"""Reusable building pieces shared by the structure designs."""
import math

from blocks import AIR, B
from builder import DIRS, OPP, stairs, slab

# clockwise ring around a centre column (viewed from above: N -> E -> S -> W)
SPIRAL_RING = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]


def _dir_between(a, b):
    dx, dz = b[0] - a[0], b[1] - a[1]
    if dx > 0:
        return "east"
    if dx < 0:
        return "west"
    return "south" if dz > 0 else "north"


def spiral_stair(b, cx, cz, y0, y1, mat, pillar, start=0, underside=None):
    """Stairs winding clockwise around a pillar at (cx,cz) from y0 to y1 (inclusive).

    Returns {(x,z): [step ys]} so floors can leave headroom holes.
    """
    steps = {}
    b.column(cx, cz, y0, y1 + 2, pillar)
    i = start
    for y in range(y0, y1 + 1):
        ox, oz = SPIRAL_RING[i % 8]
        nx, nz = SPIRAL_RING[(i + 1) % 8]
        facing = _dir_between((ox, oz), (nx, nz))
        b.set(cx + ox, y, cz + oz, stairs(mat, facing))
        if underside is not None and y > y0 + 1:
            under = b.get(cx + ox, y - 1, cz + oz)
            if under is None or under == AIR:
                b.set(cx + ox, y - 1, cz + oz, underside)
        # headroom above each step
        for h in (1, 2):
            if b.get(cx + ox, y + h, cz + oz) is None:
                b.set(cx + ox, y + h, cz + oz, AIR)
        steps.setdefault((cx + ox, cz + oz), []).append(y)
        i += 1
    return steps


def spiral_floor_ok(steps, x, z, fy):
    """May a floor block go at (x,fy,z) without blocking the spiral below?"""
    ys = steps.get((x, z), [])
    if fy in ys:
        return False  # the step itself is there
    return not any(fy - 2 <= s <= fy - 1 for s in ys)


def gable_roof(b, x0, x1, z0, z1, y, mat, gable_fill, overhang=1, ridge=None, gable_x=True, trim=None):
    """Gable roof whose ridge runs along X (gable_x=True) over the rectangle of
    outer wall coords. Slopes rise one block per row from y. Gable end walls
    are filled with `gable_fill` up to the slope."""
    if not gable_x:
        # run along Z: swap roles by building in a rotated frame
        return _gable_roof_z(b, x0, x1, z0, z1, y, mat, gable_fill, overhang, ridge, trim)
    za, zb = z0 - overhang, z1 + overhang
    xa, xb = x0 - overhang, x1 + overhang
    k = 0
    while za + k <= zb - k:
        zn, zs = za + k, zb - k
        yy = y + k
        for x in range(xa, xb + 1):
            if zn == zs:
                b.set(x, yy, zn, ridge or slab(mat, "bottom"))
            else:
                b.set(x, yy, zn, stairs(mat, "south"))
                b.set(x, yy, zs, stairs(mat, "north"))
        # gable infill (inside wall line)
        for gx in (x0, x1):
            for z in range(max(zn + 1, z0), min(zs - 1, z1) + 1):
                b.set(gx, yy, z, gable_fill)
            if zn == zs and z0 <= zn <= z1:
                b.set(gx, yy, zn, gable_fill) if ridge is None else None
        k += 1
    return y + k - 1


def _gable_roof_z(b, x0, x1, z0, z1, y, mat, gable_fill, overhang, ridge, trim):
    xa, xb = x0 - overhang, x1 + overhang
    za, zb = z0 - overhang, z1 + overhang
    k = 0
    while xa + k <= xb - k:
        xw, xe = xa + k, xb - k
        yy = y + k
        for z in range(za, zb + 1):
            if xw == xe:
                b.set(xw, yy, z, ridge or slab(mat, "bottom"))
            else:
                b.set(xw, yy, z, stairs(mat, "east"))
                b.set(xe, yy, z, stairs(mat, "west"))
        for gz in (z0, z1):
            for x in range(max(xw + 1, x0), min(xe - 1, x1) + 1):
                b.set(x, yy, gz, gable_fill)
        k += 1
    return y + k - 1


def hip_ring(b, x0, x1, z0, z1, y, mat):
    """One ring of inward-facing stairs on the rectangle perimeter (hip roof course)."""
    for x in range(x0, x1 + 1):
        b.set(x, y, z0, stairs(mat, "south"))
        b.set(x, y, z1, stairs(mat, "north"))
    for z in range(z0 + 1, z1):
        b.set(x0, y, z, stairs(mat, "east"))
        b.set(x1, y, z, stairs(mat, "west"))


def hip_roof(b, x0, x1, z0, z1, y, mat, cap=None):
    k = 0
    while x0 + k < x1 - k and z0 + k < z1 - k:
        hip_ring(b, x0 + k, x1 - k, z0 + k, z1 - k, y + k, mat)
        k += 1
    # remaining centre line / point
    for x in range(x0 + k, x1 - k + 1):
        for z in range(z0 + k, z1 - k + 1):
            b.set(x, y + k, z, cap or slab(mat, "bottom"))
    return y + k


def cone(b, cx, cz, r0, y, st_fn, step_r=1.0, min_r=0.5):
    """Stepped cone of rings; st_fn(k, x, z) -> state for ring k."""
    k = 0
    r = r0
    while r >= min_r:
        for x in range(int(math.floor(cx - r)), int(math.ceil(cx + r)) + 1):
            for z in range(int(math.floor(cz - r)), int(math.ceil(cz + r)) + 1):
                d = math.hypot(x - cx, z - cz)
                if r - 1.0 < d <= r + 0.01 or (r < 1.0 and d <= r + 0.01):
                    b.set(x, y + k, z, st_fn(k, x, z), clip=True)
        r -= step_r
        k += 1
    return y + k - 1


def circle_cells(cx, cz, r):
    out = []
    for x in range(int(math.floor(cx - r)), int(math.ceil(cx + r)) + 1):
        for z in range(int(math.floor(cz - r)), int(math.ceil(cz + r)) + 1):
            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r + 0.01:
                out.append((x, z))
    return out


def ring_cells(cx, cz, r):
    """Cells on the (thin, 4-connected-closed) rim of a disc of radius r."""
    inside = set(circle_cells(cx, cz, r))
    rim = []
    for (x, z) in inside:
        if any((x + dx, z + dz) not in inside for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            rim.append((x, z))
    return sorted(rim)


def scatter_plants(b, cells, y, plants, rng, density=0.4, ground=None):
    """Place random plants at height y on the given (x,z) cells (only where empty)."""
    for (x, z) in cells:
        if rng.random() >= density:
            continue
        if not b.is_air_or_void(x, y, z):
            continue
        below = b.get(x, y - 1, z)
        if below is not None and below.short not in ("grass_block", "moss_block", "podzol", "dirt", "coarse_dirt",
                                                      "rooted_dirt", "mud"):
            continue
        p = rng.choice(plants)
        if ground is not None and below is None:
            b.set(x, y - 1, z, ground)
        if isinstance(p, tuple) and p[0] == "tall":
            if b.is_air_or_void(x, y + 1, z):
                b.tall_plant(x, y, z, p[1])
        else:
            b.set(x, y, z, p)


def weather(b, mapping, rng, prob, region=None):
    """mapping: {block_id: [replacement states...]}; replaces with probability prob."""
    for pos, (st, n) in sorted(b.cells.items()):
        if n is not None:
            continue
        reps = mapping.get(st.id) or mapping.get(st.short)
        if reps and (region is None or region(*pos)) and rng.random() < prob:
            r = rng.choice(reps)
            if callable(r):
                r = r(st)
            b.cells[pos] = (r, None)


def vines_on(b, rng, prob, ymin=1, region=None, max_len=3):
    """Hang vines on exposed outside faces of solid blocks (vine sits in the air
    cell next to the wall; its property names the wall side)."""
    from builder import is_full
    adds = {}
    for (x, y, z), (st, n) in sorted(b.cells.items()):
        if y < ymin or not is_full(st) or st.short.endswith("_leaves"):
            continue
        if region is not None and not region(x, y, z):
            continue
        for d in ("north", "south", "east", "west"):
            dx, _, dz = DIRS[d]
            p = (x + dx, y, z + dz)
            if not b.inb(*p) or p in b.cells or p in adds:
                continue
            if rng.random() < prob:
                # vine at p attached to the wall on its OPP[d] side
                L = rng.randint(1, max_len)
                for i in range(L):
                    q = (p[0], p[1] - i, p[2])
                    if q[1] < 1 or q in b.cells or q in adds:
                        break
                    wall = (x, y - i, z)
                    if i > 0 and not (wall in b.cells and is_full(b.cells[wall][0])):
                        # vines may hang free below their support; keep it short
                        if i > 1:
                            break
                    adds[q] = B("vine", **{OPP[d]: True})
    for q, st in adds.items():
        b.set(*q, st)
