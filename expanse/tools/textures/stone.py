"""Limestone family, opal sand / sandstone family, prismite."""
import numpy as np

from texlib import blank, from_ascii, lerp, paint, pnoise, put, quantize, shade

# --------------------------------------------------------------------------
# limestone
# --------------------------------------------------------------------------
LIME = ['#a69e88', '#bfb8a3', '#cac3ae', '#d4cdb9', '#dcd6c3', '#e3dccb', '#ece7da']
#        0 pit     1 dark     2          3 base     4          5 light    6 highlight


def _pocks(idx, rng, n, base_min=2):
    """Porous pits: dark pit pixel(s), a lit lip on the lower-right edge."""
    pts = []
    tries = 0
    while len(pts) < n and tries < 500:
        tries += 1
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        if any(min(abs(x - a), 16 - abs(x - a)) + min(abs(y - b), 16 - abs(y - b)) < 4 for a, b in pts):
            continue
        pts.append((x, y))
        big = rng.random() < 0.4
        cells = [(x, y)] + ([(x + 1, y)] if big else [])
        for (cx, cy) in cells:
            idx[cy % 16, cx % 16] = 0
        lip = [(x, y + 1)] + ([(x + 1, y + 1)] if big else [])
        for (lx, ly) in lip:
            idx[ly % 16, lx % 16] = 5
        idx[(y - 1) % 16, x % 16] = min(idx[(y - 1) % 16, x % 16], 2)
    return idx


def limestone_idx(rng):
    f = pnoise(rng, sx=1.6, sy=1.3) + 0.45 * pnoise(rng, sx=0.6, sy=0.6)
    idx = quantize(f, [0.12, 0.26, 0.34, 0.20, 0.08]) + 1
    return _pocks(idx, rng, 7)


def limestone(rng):
    return paint(limestone_idx(rng), LIME)


def polished_limestone(rng):
    f = pnoise(rng, sx=2.0, sy=2.0) + 0.3 * pnoise(rng, sx=0.6, sy=0.6)
    idx = quantize(f, [0.25, 0.5, 0.25]) + 3      # 3..5, very soft
    idx[0, :] = 6
    idx[:, 0] = 6
    idx[15, :] = 1
    idx[:, 15] = 1
    idx[15, 0] = 3
    idx[0, 15] = 3
    # faint inner line to sell the bevel
    idx[1, 1:15] = np.maximum(idx[1, 1:15], 5)
    idx[1:15, 1] = np.maximum(idx[1:15, 1], 5)
    idx[14, 1:15] = np.minimum(idx[14, 1:15], 2)
    idx[1:15, 14] = np.minimum(idx[1:15, 14], 2)
    idx[14, 1] = 3
    idx[1, 14] = 3
    return paint(idx, LIME)


def limestone_bricks(rng):
    """Three ashlar courses (6/5/5 px incl. mortar) with irregular stagger."""
    mortar = ['#9a927c', '#a9a18b']
    f = pnoise(rng, sx=1.2, sy=1.2)
    idx = np.zeros((16, 16), int)
    img = blank()
    courses = [(0, 5, [3, 11]), (6, 10, [7, 15]), (11, 15, [1, 10])]
    for (y0, y1, joints) in courses:
        # mortar row at y1 (last row of course)
        bricks = []
        js = sorted(joints)
        for i, j in enumerate(js):
            nxt = js[(i + 1) % len(js)]
            bricks.append((j + 1, nxt - 1 if nxt > j else nxt - 1 + 16))
        for (a, b) in bricks:
            tone = rng.normal(0, 0.35)
            for y in range(y0, y1):
                for xx in range(a, b + 1):
                    x = xx % 16
                    v = 3.2 + tone + 0.6 * f[y, x]
                    if y == y0:
                        v += 1.6
                    elif y == y1 - 1:
                        v -= 1.4
                    if xx == a:
                        v += 0.9
                    elif xx == b:
                        v -= 0.9
                    idx[y, x] = int(np.clip(round(v), 1, 6))
            # one pit per brick
            px = (a + int(rng.integers(2, max(3, b - a - 1)))) % 16
            py = y0 + int(rng.integers(1, y1 - y0 - 1))
            idx[py, px] = 0
            idx[(py + 1), px] = max(idx[py + 1, px], 4) if py + 1 < y1 - 1 else idx[py + 1, px]
        img_c = paint(idx, LIME)
        for y in range(y0, y1):
            for x in range(16):
                img[y, x] = img_c[y, x]
        for x in range(16):
            put(img, x, y1, mortar[0] if rng.random() < 0.7 else mortar[1])
        for j in joints:
            for y in range(y0, y1):
                put(img, j, y, mortar[0] if rng.random() < 0.7 else mortar[1])
    return img


LIME_CARVED = ['#8e856e', '#b0a891', '#c6bfaa', '#d4cdb9', '#dfd9c7', '#ebe6d8', '#f7f4ec']

STAR = [
    "................",
    "................",
    "................",
    ".......##.......",
    ".......##.......",
    "......####......",
    ".....######.....",
    "...##########...",
    "...##########...",
    ".....######.....",
    "......####......",
    ".......##.......",
    ".......##.......",
    "................",
    "................",
    "................",
]


def chiseled_limestone(rng):
    """Bevelled panel with a carved compass-star motif."""
    idx = np.full((16, 16), 2, int)
    # outer bevel + flat rim
    idx[0, :] = 6
    idx[:, 0] = 6
    idx[15, :] = 1
    idx[:, 15] = 1
    idx[1:15, 1:15] = 5
    idx[1:15, 14] = 3
    idx[14, 1:15] = 3
    idx[15, 0] = 3
    idx[0, 15] = 3
    # sunken field with a groove: shadowed top/left, lit bottom/right
    idx[2:14, 2:14] = 3
    idx[2, 2:14] = 1
    idx[2:14, 2] = 1
    idx[13, 3:14] = 5
    idx[3:14, 13] = 5

    # carved diamond-point stud: four bevelled facets lit from the upper
    # left, with a sunken diamond at its heart and small studs in the corners
    def stud(cx, cy, r, inner=0.0):
        for y in range(16):
            for x in range(16):
                dx, dy = x - cx, y - cy
                d = abs(dx) + abs(dy)
                if d > r:
                    continue
                sunk = d <= inner
                north, west = dy < 0, dx < 0
                if abs(dy) >= abs(dx):
                    v = 6 if north else 1
                    if not north and not sunk:
                        v = 2 if west else 1
                    if north and not west:
                        v = 5
                else:
                    v = 5 if west else 2
                    if not west and dy < 0:
                        v = 4
                if sunk:
                    v = 6 - v if v != 6 else 1
                    v = max(0, min(6, v))
                idx[y, x] = v

    stud(7.5, 7.5, 4.6, inner=1.6)
    for (cx, cy) in ((3.5, 3.5), (11.5, 3.5), (3.5, 11.5), (11.5, 11.5)):
        stud(cx, cy, 1.1)
    return paint(idx, LIME_CARVED)


MOSS = ['#3f5a26', '#4d6b2c', '#5f7f34', '#73963f', '#8aab4c', '#a3c060']


def mossy_limestone(rng, base_rng):
    idx = limestone_idx(base_rng)
    img = paint(idx, LIME)
    # moss depth per column (periodic), ~40% coverage from the top down
    prof = pnoise(rng, w=16, h=1, sx=1.5, sy=0.1)[0]
    depth = np.clip(np.round(5.6 + 2.0 * prof), 3, 9).astype(int)
    drips = rng.choice(16, 3, replace=False)
    for x in drips:
        depth[x] += int(rng.integers(2, 4))
    mn = pnoise(rng, sx=0.8, sy=0.8)
    moss = np.zeros((16, 16), bool)
    for x in range(16):
        for y in range(depth[x]):
            moss[y, x] = True
    # let a little stone peek through near the top (so stacked blocks blend)
    for _ in range(5):
        x, y = int(rng.integers(0, 16)), int(rng.integers(0, 3))
        moss[y, x] = False
    # isolated moss specks lower down, a few reaching the bottom rows
    for _ in range(6):
        x, y = int(rng.integers(0, 16)), int(rng.integers(9, 16))
        moss[y, x] = True
        if rng.random() < 0.5:
            moss[y, (x + 1) % 16] = True
    for y in range(16):
        for x in range(16):
            if moss[y, x]:
                v = 3 + mn[y, x] * 1.2
                below = moss[(y + 1) % 16, x] if y < 15 else True
                if not below:
                    v -= 1.6            # moss lip catches shadow underneath
                if y > 0 and not moss[y - 1, x]:
                    v += 1.0
                put(img, x, y, MOSS[int(np.clip(round(v), 0, 5))])
            elif y > 0 and moss[y - 1, x] and y < 15:
                # shadow cast on the stone just below the moss edge
                put(img, x, y, LIME[1] if idx[y, x] > 1 else LIME[0])
    return img


# --------------------------------------------------------------------------
# opal sand / sandstone
# --------------------------------------------------------------------------
OPAL = ['#c79fb3', '#d4adc0', '#dfbacb', '#e8c6d4', '#efd2de', '#f6e0e8']
MINT, SKY, PEARL = '#bfe8dc', '#c7dcf2', '#fdf2f6'
STRATA = ['#e6c0cf', '#d9b3cd', '#e9cbd8', '#cdb2d8', '#dfb7c8', '#d4bde0']


def _glints(img, rng, n_mint, n_sky, n_pearl, mask=None):
    cells = [(x, y) for y in range(16) for x in range(16) if mask is None or mask[y, x]]
    sel = rng.permutation(len(cells))
    k = 0
    for col, n in ((MINT, n_mint), (SKY, n_sky), (PEARL, n_pearl)):
        for _ in range(n):
            x, y = cells[sel[k]]
            k += 1
            put(img, x, y, col)


def opal_sand(rng):
    f = pnoise(rng, sx=0.6, sy=0.6) + 0.5 * pnoise(rng, sx=1.8, sy=1.8)
    idx = quantize(f, [0.10, 0.22, 0.34, 0.24, 0.10])
    img = paint(idx, OPAL)
    _glints(img, rng, 7, 6, 4)
    return img


def opal_sandstone_top(rng):
    f = pnoise(rng, sx=1.4, sy=1.4) + 0.35 * pnoise(rng, sx=0.5, sy=0.5)
    idx = quantize(f, [0.18, 0.46, 0.28, 0.08]) + 2
    img = paint(idx, OPAL)
    _glints(img, rng, 2, 2, 2)
    return img


def _strata_img(rng, cap=True):
    """Gently undulating strata, each with a darker base line."""
    n = pnoise(rng, sx=1.6, sy=0.4)
    img = blank()
    bounds = [3, 6, 9, 11, 14, 16] if cap else [2, 5, 8, 10, 13, 16]
    xs = np.arange(16)
    waves = []
    for _ in bounds:
        a1, a2 = rng.uniform(0.5, 1.1), rng.uniform(0.0, 0.5)
        p1, p2 = rng.uniform(0, 2 * np.pi, 2)
        w = a1 * np.sin(2 * np.pi * xs / 16 + p1) + a2 * np.sin(4 * np.pi * xs / 16 + p2)
        waves.append(np.round(w).astype(int))
    order = rng.permutation(len(STRATA))
    for x in range(16):
        yb = [b + int(waves[i][x]) for i, b in enumerate(bounds)]
        yb[-1] = 16
        layer = 0
        for y in range(16):
            while layer < len(yb) - 1 and y >= yb[layer]:
                layer += 1
            base = STRATA[order[layer % len(STRATA)]]
            c = base
            if n[y, x] > 1.1:
                c = shade(base, 1.05)
            elif n[y, x] < -1.1:
                c = shade(base, 0.95)
            if y == yb[layer] - 1 and layer < len(yb) - 1:
                c = shade(base, 0.88)
            put(img, x, y, c)
    if cap:
        top = opal_sandstone_top(np.random.default_rng(int(rng.integers(1 << 30))))
        for y in range(3):
            for x in range(16):
                img[y, x] = top[y + 4, x]
        for x in range(16):
            put(img, x, 3, OPAL[1] if rng.random() < 0.75 else OPAL[2])
    return img


def opal_sandstone(rng):
    return _strata_img(rng, cap=True)


def opal_sandstone_bottom(rng):
    img = _strata_img(rng, cap=False)
    # soften: blend toward the sand colour so the underside reads calmer
    out = img.copy()
    for y in range(16):
        for x in range(16):
            c = tuple(int(v) for v in img[y, x, :3])
            put(out, x, y, lerp(c, OPAL[3], 0.35))
    return out


def _cut_frame(img, rng):
    for x in range(16):
        put(img, x, 0, OPAL[5] if rng.random() < 0.8 else OPAL[4])
        put(img, x, 1, OPAL[4])
        put(img, x, 2, OPAL[1])
        put(img, x, 13, OPAL[5] if rng.random() < 0.8 else OPAL[4])
        put(img, x, 14, OPAL[3])
        put(img, x, 15, OPAL[0])
    return img


def cut_opal_sandstone(rng):
    f = pnoise(rng, sx=3.0, sy=0.5) + 0.25 * pnoise(rng, sx=0.5, sy=0.5)
    idx = quantize(f, [0.22, 0.56, 0.22]) + 2
    img = paint(idx, OPAL)
    return _cut_frame(img, rng)


SUN = [
    "................",
    "................",
    "................",
    ".......##.......",
    "...#...##...#...",
    "....#......#....",
    "......####......",
    "..##.######.##..",
    "..##.######.##..",
    "......####......",
    "....#......#....",
    "...#...##...#...",
    ".......##.......",
    "................",
    "................",
    "................",
]


def chiseled_opal_sandstone(rng):
    """Cut sandstone frame with a carved sun (raised relief, embossed)."""
    img = cut_opal_sandstone(rng)
    n = pnoise(rng, sx=2.0, sy=1.0)
    for y in range(3, 13):
        for x in range(16):
            put(img, x, y, OPAL[3] if n[y, x] < 1.2 else OPAL[4])
    mask = np.array([[c == '#' for c in r] for r in SUN])
    for y in range(16):
        for x in range(16):
            if mask[y, x]:
                lit = not mask[y - 1, x] or not mask[y, x - 1]
                dark = not mask[y + 1, x] or not mask[y, x + 1]
                disk = abs(x - 7.5) < 2.6 and abs(y - 7.5) < 2.6
                if disk:
                    c = '#fff1e4' if (lit and not dark) else ('#d9a594' if (dark and not lit) else '#f5d2bf')
                elif lit and not dark:
                    c = PEARL
                elif dark and not lit:
                    c = OPAL[1]
                else:
                    c = OPAL[5]
                put(img, x, y, c)
            elif mask[y - 1, x - 1] and 3 <= y <= 12:
                put(img, x, y, OPAL[0])               # cast shadow of the relief
    # opal glint at the heart of the sun
    put(img, 7, 7, '#ffffff')
    put(img, 8, 8, MINT)
    return img


# --------------------------------------------------------------------------
# prismite
# --------------------------------------------------------------------------
PRISM_CYAN = ['#3d5db4', '#5287dc', '#7fd6ff', '#b4ecff', '#e8f7ff']
PRISM_VIOLET = ['#4a3ba6', '#6e5cd4', '#9886f0', '#b9a4ff', '#e4ddff']


def prismite_block(rng):
    """Faceted crystal: every facet is lit from the upper-left, so the dark
    lower-right of one facet meets the bright upper-left of the next."""
    pts = []
    while len(pts) < 7:
        x, y = rng.random() * 16, rng.random() * 16
        if all(min(abs(x - a), 16 - abs(x - a)) ** 2 + min(abs(y - b), 16 - abs(y - b)) ** 2 > 22
               for a, b in pts):
            pts.append((x, y))
    ramps = [PRISM_CYAN if i % 2 == 0 else PRISM_VIOLET for i in range(len(pts))]
    tilt = [rng.uniform(0.7, 1.3) for _ in pts]
    img = blank()
    for y in range(16):
        for x in range(16):
            best = None
            for i, (px, py) in enumerate(pts):
                dx = (x + 0.5 - px + 8) % 16 - 8
                dy = (y + 0.5 - py + 8) % 16 - 8
                d = dx * dx + dy * dy
                if best is None or d < best[0]:
                    best = (d, i, dx, dy)
            _, i, dx, dy = best
            v = 2.0 - tilt[i] * (dx * 0.8 + dy) / 2.6
            k = int(np.clip(round(v), 0, 4))
            put(img, x, y, ramps[i][k])
    for (x, y) in ((3, 4), (12, 9), (7, 13)):
        put(img, x, y, '#ffffff')
    return img


CLUSTER = [
    "................",
    ".......Wo.......",
    ".......WVo......",
    "......WCVo......",
    "......WCVvo.....",
    "..o...WCVvo.....",
    ".Wo...WCVvo..Wo.",
    ".WVo..WCVvo.WVo.",
    ".WCVo.WCVvoWCVo.",
    "..WCVoWCVvoWCVo.",
    "..WCVoWCVvoWCVo.",
    "...WCVWCVvWCVvo.",
    "...WCVWCVvWCVvo.",
    "...WCVWCVvWCVvo.",
    "....WCWCVvWCVv..",
    "....WCWCVvWCVv..",
]


def prismite_cluster(rng):
    pal = {'W': '#e8f7ff', 'C': '#7fd6ff', 'V': '#b9a4ff', 'v': '#8a76e6', 'o': '#4b3fa8'}
    return from_ascii(CLUSTER, pal)


def generate(rng_for):
    out = []
    lim_rng = 'limestone'
    out.append(('textures/block/limestone.png', limestone(rng_for(lim_rng)), True))
    out.append(('textures/block/polished_limestone.png', polished_limestone(rng_for('polished_limestone')), True))
    out.append(('textures/block/limestone_bricks.png', limestone_bricks(rng_for('limestone_bricks')), True))
    out.append(('textures/block/chiseled_limestone.png', chiseled_limestone(rng_for('chiseled_limestone')), True))
    out.append(('textures/block/mossy_limestone.png',
                mossy_limestone(rng_for('mossy_limestone'), rng_for(lim_rng)), True))
    out.append(('textures/block/opal_sand.png', opal_sand(rng_for('opal_sand')), True))
    out.append(('textures/block/opal_sandstone.png', opal_sandstone(rng_for('opal_sandstone')), True))
    out.append(('textures/block/opal_sandstone_top.png', opal_sandstone_top(rng_for('opal_sandstone_top')), True))
    out.append(('textures/block/opal_sandstone_bottom.png',
                opal_sandstone_bottom(rng_for('opal_sandstone_bottom')), True))
    out.append(('textures/block/cut_opal_sandstone.png', cut_opal_sandstone(rng_for('cut_opal_sandstone')), True))
    out.append(('textures/block/chiseled_opal_sandstone.png',
                chiseled_opal_sandstone(rng_for('chiseled_opal_sandstone')), True))
    out.append(('textures/block/prismite_block.png', prismite_block(rng_for('prismite_block')), True))
    out.append(('textures/block/prismite_cluster.png', prismite_cluster(rng_for('prismite_cluster')), False))
    return out
