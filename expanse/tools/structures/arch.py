"""Architecture helpers for the large structures: plinths with retaining walls,
flared hip roofs, straight stairs, battlements, stone lanterns, ponds, garden
trees, rocks and bamboo."""
import math

from blocks import AIR, B
from builder import DIRS, OPP, slab, stairs

H4 = ("north", "east", "south", "west")


def ring(x0, z0, x1, z1):
    """Perimeter cells of a rectangle (clockwise from the north-west corner)."""
    if x0 > x1 or z0 > z1:
        return []
    if x0 == x1 or z0 == z1:
        return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]
    out = [(x, z0) for x in range(x0, x1 + 1)]
    out += [(x1, z) for z in range(z0 + 1, z1 + 1)]
    out += [(x, z1) for x in range(x1 - 1, x0 - 1, -1)]
    out += [(x0, z) for z in range(z1 - 1, z0, -1)]
    return out


def plinth(b, x0, z0, x1, z1, top, rng, wall=("stone_bricks",), fill="dirt", floor=None, buttress=0,
           buttress_mat=None, y0=0, coping=None):
    """Solid platform x0..x1/z0..z1 from y0 to `top` (inclusive) with a retaining-wall skin. `floor`
    (state or fn(x, z)) paves the top; `buttress` > 0 adds a buttress every n cells outside the wall;
    `coping` puts a slab/stair course on the wall top instead of floor cells there."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(y0, top + 1):
                if edge:
                    b.set(x, y, z, B(rng.choice(wall)))
                elif y < top or floor is None:
                    b.set(x, y, z, B(fill) if isinstance(fill, str) else fill)
            if floor is not None and not edge:
                f = floor(x, z) if callable(floor) else floor
                if f is not None:
                    b.set(x, top, z, f)
            if coping is not None and edge:
                b.set(x, top, z, coping)
    if buttress:
        bm = buttress_mat or wall[0]
        for (x, z) in ring(x0, z0, x1, z1):
            k = (x - x0) + (z - z0)
            corner = x in (x0, x1) and z in (z0, z1)
            if corner or k % buttress:
                continue
            for d in H4:
                dx, _, dz = DIRS[d]
                ox, oz = x + dx, z + dz
                if x0 <= ox <= x1 and z0 <= oz <= z1:
                    continue
                if not b.inb(ox, 0, oz):
                    continue
                for y in range(y0, top - 1):
                    b.set(ox, y, oz, B(bm), clip=True)
                if b.inb(ox, top - 1, oz):
                    b.set(ox, top - 1, oz, stairs(bm.replace("_bricks", "_brick").replace("polished_", "")
                                                  if bm.endswith("bricks") else bm, OPP[d]), clip=True)


def flared_roof(b, x0, z0, x1, z1, y, tile, overhang=2, stop=0, flare=True, lanterns=True, cap=None,
                soul=False, lip=True):
    """Hip roof with an eave lip and up-turned corners over the wall rectangle x0..x1/z0..z1.

    Ring 0 (overhang cells out) is a slab lip at y, ring 1 stairs at y, ring k>=2 at y+k-1, working
    inward. `stop` = how many cells inside the wall line to stop (a pagoda tier stops where the next
    tier's walls stand); stop=None closes the roof with a ridge/cap. Returns the highest y used."""
    tf = tile if not tile.endswith("brick") else tile + "s"
    k = 0
    top = y
    while True:
        ax0, az0 = x0 - overhang + k, z0 - overhang + k
        ax1, az1 = x1 + overhang - k, z1 + overhang - k
        inset = k - overhang                       # cells inside the wall line (ring 0 is outside)
        if stop is not None and inset >= stop:
            break
        if ax0 > ax1 or az0 > az1:
            break
        yy = y if k <= 1 else y + k - 1
        if ax0 == ax1 or az0 == az1:              # ridge line / point (on a solid core)
            for x in range(ax0, ax1 + 1):
                for z in range(az0, az1 + 1):
                    b.set(x, yy, z, cap or slab(tile), clip=True)
                    if k > 1 and b.inb(x, yy - 1, z) and b.get(x, yy - 1, z) in (None, AIR):
                        b.set(x, yy - 1, z, B(tf))
            top = yy
            break
        for (x, z) in ring(ax0, az0, ax1, az1):
            if k == 0 and lip:
                b.set(x, yy, z, slab(tile), clip=True)
                continue
            if z == az0:
                f = "south"
            elif z == az1:
                f = "north"
            elif x == ax0:
                f = "east"
            else:
                f = "west"
            b.set(x, yy, z, stairs(tile, f), clip=True)
        top = yy
        k += 1
    if flare:
        for (cx, cz, fx) in ((x0 - overhang, z0 - overhang, "west"), (x1 + overhang, z0 - overhang, "east"),
                             (x0 - overhang, z1 + overhang, "west"), (x1 + overhang, z1 + overhang, "east")):
            if not b.inb(cx, y + 1, cz):
                continue
            b.set(cx, y, cz, B(tf))
            b.set(cx, y + 1, cz, stairs(tile, fx))
            if lanterns and b.inb(cx, y - 1, cz) and b.get(cx, y - 1, cz) is None:
                b.set(cx, y - 1, cz, B("soul_lantern" if soul else "lantern", hanging=True))
    return top


def straight_stair(b, x, y, z, d, n, mat, clear=3, under=None):
    """n stairs climbing toward direction d starting at (x,y,z) (first step at y). Clears `clear`
    cells of headroom above every step; `under` fills the cell under each step but the first."""
    dx, _, dz = DIRS[d]
    for i in range(n):
        px, py, pz = x + dx * i, y + i, z + dz * i
        b.set(px, py, pz, stairs(mat, d))
        for h in range(1, clear + 1):
            if b.inb(px, py + h, pz):
                b.set(px, py + h, pz, AIR)
        if under is not None and i > 0 and b.is_air_or_void(px, py - 1, pz):
            b.set(px, py - 1, pz, under)
    return x + dx * n, y + n - 1, z + dz * n


def wide_stair(b, xs, y, z, d, n, mat, clear=3, under=None):
    """A flight several cells wide: xs = cells across (x for d north/south, z for east/west)."""
    for c in xs:
        if d in ("north", "south"):
            straight_stair(b, c, y, z, d, n, mat, clear, under)
        else:
            straight_stair(b, z, y, c, d, n, mat, clear, under)


def battlement(b, cells, y, mat, merlon="wall", every=2, rng=None, broken=0.0):
    """Crenellations along cells at height y: merlons (walls or full blocks) on alternate cells."""
    for i, (x, z) in enumerate(cells):
        if rng is not None and rng.random() < broken:
            continue
        if i % every == 0:
            b.set(x, y, z, B(mat) if merlon == "block" else B(merlon))


def stone_lantern(b, x, y, z, base="stone_brick", soul=False, tall=False):
    """Toro-style stone lantern: footing, post, lamp, roof slab."""
    b.set(x, y, z, B("chiseled_stone_bricks") if base == "stone_brick" else B(base + "s"))
    yy = y + 1
    if tall:
        b.set(x, yy, z, B(f"{base}_wall"))
        yy += 1
    b.set(x, yy, z, B(f"{base}_wall"))
    b.set(x, yy + 1, z, B("soul_lantern" if soul else "lantern"))
    b.set(x, yy + 2, z, B(f"{base}_slab", type="bottom"))


def rock(b, x, y, z, rng, mats=("stone", "andesite", "mossy_cobblestone", "cobblestone"), size=2):
    """A small natural boulder sitting at y (bottom cell) around (x,z)."""
    cells = [(x, y, z)]
    for _ in range(size * 2):
        cx, cy, cz = rng.choice(cells)
        d = rng.choice(["north", "south", "east", "west", "up", "north", "east"])
        dx, dy, dz = DIRS[d]
        n = (cx + dx, min(cy + dy, y + size - 1), cz + dz)
        if n not in cells and b.inb(*n):
            cells.append(n)
    for c in cells:
        b.set(*c, B(rng.choice(mats)))
    return cells


def pond(b, cells, ys, rng, depth=2, rim=None, lily=0.12, plants=()):
    """Water over `cells` with the surface at ys, `depth` deep, bedded on clay/gravel, optional rim."""
    cs = set(cells)
    for (x, z) in cells:
        for d in range(depth):
            b.set(x, ys - d, z, B("water", level=0))
        if b.inb(x, ys - depth, z):                    # at layer 0 the natural ground is the bed
            b.set(x, ys - depth, z, B(rng.choice(["clay", "gravel", "dirt", "clay"])))
        b.set(x, ys + 1, z, AIR)
        if rng.random() < lily:
            b.set(x, ys + 1, z, B("lily_pad"))
    if rim:
        for (x, z) in cells:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                p = (x + dx, z + dz)
                if p in cs or not b.inb(p[0], ys, p[1]):
                    continue
                b.set(p[0], ys, p[1], B(rng.choice(rim)) if isinstance(rim, (list, tuple)) else rim)
                for d in range(1, depth + 1):
                    if b.inb(p[0], ys - d, p[1]) and b.get(p[0], ys - d, p[1]) is None:
                        b.set(p[0], ys - d, p[1], B("dirt"))
    for (x, z, st) in plants:
        b.set(x, ys + 1, z, st)


def blob(cx, cz, rx, rz, rng, rough=0.25):
    """Irregular elliptical patch of (x, z) cells."""
    out = []
    phase = rng.random() * 6.28
    for x in range(int(cx - rx - 2), int(cx + rx + 3)):
        for z in range(int(cz - rz - 2), int(cz + rz + 3)):
            a = math.atan2(z - cz, x - cx)
            r = 1 + rough * math.sin(3 * a + phase) * 0.8 + rough * 0.5 * math.sin(5 * a + 2 * phase)
            if ((x - cx) / (rx * r)) ** 2 + ((z - cz) / (rz * r)) ** 2 <= 1.0:
                out.append((x, z))
    return out


def leaf_ball(b, cx, cy, cz, r, leaves, rng, density=0.85, flat=1.0):
    for x in range(int(cx - r), int(cx + r) + 1):
        for y in range(int(cy - r * flat), int(cy + r * flat) + 1):
            for z in range(int(cz - r), int(cz + r) + 1):
                d = math.sqrt((x - cx) ** 2 + ((y - cy) / max(flat, 0.3)) ** 2 + (z - cz) ** 2)
                if d <= r + 0.3 and (d < r - 0.8 or rng.random() < density) and b.inb(x, y, z) and \
                        b.get(x, y, z) is None:
                    b.set(x, y, z, leaves)


def leaves(name):
    return B(name, distance=1, persistent=True, waterlogged=False)


def prune_leaves(b, x0, y0, z0, x1, y1, z1):
    """Drop leaf blocks in the box that touch nothing on any face (stray single leaves)."""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                c = b.get(x, y, z) if b.inb(x, y, z) else None
                if c is None or not c.id.endswith("leaves"):
                    continue
                if not any(b.inb(x + dx, y + dy, z + dz) and b.get(x + dx, y + dy, z + dz) not in (None, AIR)
                           for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
                    b.set(x, y, z, None)


def tree(b, x, y, z, rng, log, leaf, height=5, crown=2.5, lean=None, flat=0.7, blossoms=None, petals=None):
    """Garden tree: trunk (optionally leaning) with a leafy crown; `blossoms` = hanging block id
    (wisteria) strung under the crown, `petals` = ground cover block id under it."""
    tx, tz = x, z
    for h in range(height):
        if lean and h >= height // 2 and rng.random() < 0.6:
            dx, _, dz = DIRS[lean]
            tx, tz = tx + dx, tz + dz
            b.set(tx, y + h - 1, tz, B(log, axis="x" if dx else "z"), clip=True)   # keep the trunk joined
        b.set(tx, y + h, tz, B(log, axis="y"), clip=True)
    top = y + height
    for d in rng.sample(H4, 2):
        dx, _, dz = DIRS[d]
        b.set(tx + dx, top - 1, tz + dz, B(log, axis="x" if dx else "z"), clip=True)
    leaf_ball(b, tx, top, tz, crown, leaves(leaf), rng, flat=flat)
    r = int(crown) + 2
    prune_leaves(b, tx - r, top - r, tz - r, tx + r, top + r, tz + r)
    if blossoms:
        for xx in range(int(tx - crown), int(tx + crown) + 1):
            for zz in range(int(tz - crown), int(tz + crown) + 1):
                for yy in range(top + 2, top - 3, -1):
                    if b.inb(xx, yy, zz) and b.get(xx, yy, zz) is not None and \
                            b.get(xx, yy, zz).id.endswith("leaves"):
                        if b.inb(xx, yy - 1, zz) and b.get(xx, yy - 1, zz) is None and rng.random() < 0.55:
                            b.hang_moss(xx, yy - 1, zz, rng.randint(1, 3), block=blossoms)
                        break
    if petals:
        for xx in range(int(tx - crown), int(tx + crown) + 1):
            for zz in range(int(tz - crown), int(tz + crown) + 1):
                if b.inb(xx, y, zz) and b.get(xx, y, zz) is None and b.get(xx, y - 1, zz) is not None and \
                        rng.random() < 0.45:
                    b.set(xx, y, zz, B(petals, facing=rng.choice(H4), flower_amount=rng.randint(1, 4)))
    return tx, top, tz


def bamboo_clump(b, cx, cz, y, r, rng, density=0.6, hmin=4, hmax=9):
    for (x, z) in [(x, z) for x in range(cx - r, cx + r + 1) for z in range(cz - r, cz + r + 1)]:
        if (x - cx) ** 2 + (z - cz) ** 2 > r * r + 0.5 or rng.random() > density:
            continue
        if not b.inb(x, y, z) or b.get(x, y, z) is not None:
            continue
        if b.get(x, y - 1, z) is None:
            b.set(x, y - 1, z, B("podzol"))
        h = rng.randint(hmin, hmax)
        for k in range(h):
            if not b.inb(x, y + k, z) or b.get(x, y + k, z) is not None:
                break
            lv = "large" if k >= h - 2 else "small" if k >= h - 4 else "none"
            b.set(x, y + k, z, B("bamboo", age=1 if h > 6 else 0, leaves=lv, stage=0))


def column(b, x, z, y0, y1, st, base=None, capital=None):
    for y in range(y0, y1 + 1):
        b.set(x, y, z, st)
    if base is not None:
        b.set(x, y0, z, base)
    if capital is not None:
        b.set(x, y1, z, capital)


def lattice(rng=None, open_=True, wood="spruce"):
    """A decorative lattice window state (open trapdoor is decided by the caller's facing)."""
    return B(f"{wood}_trapdoor", facing="north", half="bottom", open=open_, powered=False, waterlogged=False)
