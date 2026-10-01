"""Leaves (all seamless, opaque pixels + transparent holes, colours baked in).

Most leaves are painted with small hand-made leaf *stamps* dropped on a
jittered grid (wrapping at the edges, so the result tiles), back layers in
darker shades and front layers lighter.  Uncovered pixels become the holes.
"""
import numpy as np

from texlib import paint, pnoise


PAL = {
    'redwood': ['#152d1d', '#1d3a25', '#24452a', '#2e5633', '#3b673a', '#4f7d43', '#66934f'],
    'willow': ['#3f5e26', '#527832', '#6f9a3e', '#86ad4c', '#98bd59', '#a9c96a', '#c3dc88'],
    'palm': ['#174a1d', '#216224', '#2f7a2a', '#3d8e31', '#55a33c', '#6fb84a', '#a2d673'],
    'lumen': ['#1a5c5c', '#257976', '#348f8a', '#4aa8a0', '#69c2b8', '#8fdccf', '#d6fcf4'],
    'baobab': ['#3b481b', '#4b5b25', '#5c7030', '#6d8139', '#80943f', '#93a84f', '#aec06a'],
}
WISTERIA = {
    'wisteria': ['#4a2a78', '#6a3fa0', '#8458b8', '#9f78d2', '#b78fe3', '#c9a2ef', '#e6d2fa'],
    'azure_wisteria': ['#26397f', '#3f5fb0', '#5577c6', '#6f92da', '#8aabea', '#a9c4f5', '#d6e5fc'],
}
WISTERIA_GREEN = ['#24401d', '#355c27', '#4a7533', '#5f8a3e', '#76a04c', '#8bb35a', '#8bb35a']


class Canvas:
    """Wrap-around index canvas; -1 = hole."""

    def __init__(self, fill=-1):
        self.idx = np.full((16, 16), fill, int)

    def set(self, x, y, v):
        self.idx[int(y) % 16, int(x) % 16] = int(np.clip(v, 0, 6))

    def coverage(self):
        return (self.idx >= 0).mean()

    def image(self, pal):
        img = paint(np.maximum(self.idx, 0), pal)
        img[self.idx < 0] = 0
        return img


def _stamp(cv, st, x0, y0, off=0, mirror=False):
    for yy, row in enumerate(st):
        if mirror:
            row = row[::-1]
        for xx, ch in enumerate(row):
            if ch != '.':
                cv.set(x0 + xx, y0 + yy, int(ch) + off)


def _grid(rng, step=4, jitter=1, ox=0, oy=0, stagger=True):
    pts = []
    for gy in range(0, 16, step):
        for gx in range(0, 16, step):
            sx = (step // 2) if (stagger and (gy // step) % 2) else 0
            pts.append((gx + sx + ox + int(rng.integers(-jitter, jitter + 1)),
                        gy + oy + int(rng.integers(-jitter, jitter + 1))))
    order = rng.permutation(len(pts))
    return [pts[i] for i in order]


def _shadow_under(cv):
    """Pixels directly below a hole are darker (depth cue, like vanilla)."""
    i = cv.idx.copy()
    for y in range(16):
        for x in range(16):
            if cv.idx[y, x] > 1 and i[(y - 1) % 16, x] < 0:
                cv.idx[y, x] = max(1, cv.idx[y, x] - 2)


def _fill_holes_to(cv, rng, lo, hi, fill_shade=1):
    """Clamp the hole fraction into [lo, hi] (fill isolated holes first,
    or punch extra holes into the darkest pixels)."""
    frac = (cv.idx < 0).mean()
    if frac > hi:
        holes = np.argwhere(cv.idx < 0)
        rng.shuffle(holes)
        # prefer holes with many opaque neighbours (isolated pinholes)
        def nb(p):
            y, x = p
            return sum(cv.idx[(y + dy) % 16, (x + dx) % 16] >= 0
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        holes = sorted(holes.tolist(), key=lambda p: -nb(p))
        for (y, x) in holes[:int(round((frac - hi) * 256))]:
            cv.idx[y, x] = fill_shade
    elif frac < lo:
        cand = np.argwhere(cv.idx == cv.idx[cv.idx >= 0].min())
        rng.shuffle(cand)
        for (y, x) in cand[:int(round((lo - frac) * 256))]:
            cv.idx[y, x] = -1


def carve_holes(cv, rng, frac=0.19, step=4, sizes=(2, 3, 3, 4), noise=0.6):
    """Evenly scatter small holes: one per (jittered) grid cell, seeded at the
    darkest pixel of the cell and grown into the darkest neighbours."""
    cv.idx[cv.idx < 0] = 1
    jit = pnoise(rng, sx=0.5, sy=0.5) * noise
    target = int(round(frac * 256))
    gx0, gy0 = (int(v) for v in rng.integers(0, step, 2))
    cells = [(gx, gy) for gy in range(0, 16, step) for gx in range(0, 16, step)]
    order = rng.permutation(len(cells))
    holes = np.zeros((16, 16), bool)
    made = 0
    for n, ci in enumerate(order):
        gx, gy = cells[ci]
        left = target - made
        cells_left = len(cells) - n
        size = int(np.clip(round(left / cells_left + rng.choice([-1, 0, 0, 1])), 1, max(sizes)))
        best = None
        for yy in range(step):
            for xx in range(step):
                x, y = (gx + gx0 + xx) % 16, (gy + gy0 + yy) % 16
                v = cv.idx[y, x] + jit[y, x]
                if best is None or v < best[0]:
                    best = (v, x, y)
        _, x, y = best
        region = [(x, y)]
        holes[y, x] = True
        while len(region) < size:
            cand = []
            for (hx, hy) in region:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = (hx + dx) % 16, (hy + dy) % 16
                    if not holes[ny, nx]:
                        cand.append((cv.idx[ny, nx] + jit[ny, nx] + rng.random() * 0.8, nx, ny))
            if not cand:
                break
            _, nx, ny = min(cand)
            holes[ny, nx] = True
            region.append((nx, ny))
        made += len(region)
    cv.idx[holes] = -1


# --------------------------------------------------------------------------
REDWOOD_SPRIG = ["3.3.3",
                 ".454.",
                 "2.5.2",
                 ".464.",
                 "..5.."]


def leaves_redwood(rng):
    """Dense drooping needle sprigs (rank after rank of tiny fishbones)."""
    cv = Canvas()
    for (x, y) in _grid(rng, step=4, jitter=1):
        _stamp(cv, REDWOOD_SPRIG, x - 2, y - 2, off=-2)
    for (x, y) in _grid(rng, step=4, jitter=1, ox=2, oy=2)[:13]:
        _stamp(cv, REDWOOD_SPRIG, x - 2, y - 2, off=0)
    carve_holes(cv, rng, 0.16)
    _shadow_under(cv)
    return cv.image(PAL['redwood'])


def leaves_willow(rng):
    """Slender leaves hanging in vertical strands."""
    cv = Canvas()
    n = 0
    while cv.coverage() < 0.80:
        n += 1
        back = n < 14
        x = int(rng.integers(0, 16))
        y = int(rng.integers(0, 16))
        L = int(rng.integers(4, 7))
        prof = [3] + [5] * (L - 3) + [4, 2]
        if not back and rng.random() < 0.6:
            prof[1] = 6
        for i, lv in enumerate(prof):
            cv.set(x, y + i, lv - (2 if back else 0))
        if L >= 5 and rng.random() < 0.45:        # leaf belly, shaded side
            cv.set(x + 1, y + 2, 3 - (2 if back else 0))
            cv.set(x + 1, y + 3, 2 - (1 if back else 0))
    _fill_holes_to(cv, rng, 0.17, 0.22)
    _shadow_under(cv)
    return cv.image(PAL['willow'])


def leaves_palm(rng):
    """Two hanging fronds per tile: glossy midrib, broad strap leaflets
    drooping down and out (each strap = lit edge / body / shade, then gap)."""
    idx = np.full((16, 16), -1, int)
    for mx, yo, dep in ((3, 0, 0), (11, 2, -1)):
        # per-leaflet length (some are short so their tip leaves a gap)
        lens = {}
        for x in range(mx - 4, mx + 5):
            k = abs(x - mx)
            side = 0 if x < mx else 1
            for y in range(16):
                xx = x % 16
                if k == 0:
                    idx[y, xx] = (6 if y % 4 == (yo + 1) % 4 else 5) + dep
                    continue
                ph = (y - yo - k) % 4
                leaflet = ((y - yo - k) // 4, side)
                if leaflet not in lens:
                    lens[leaflet] = int(rng.choice([3, 3, 4, 4, 2]))
                if k > lens[leaflet] or k == 4 and lens[leaflet] < 4:
                    if idx[y, xx] < 0:
                        idx[y, xx] = -2      # mark: gap between fronds
                    continue
                lit = 1 if side == 0 else 0
                v = {0: 4 + lit + (1 if k == 1 else 0), 1: 3 + lit, 2: 2, 3: -1}[ph]
                if k >= 3 and v > 2:
                    v -= 1
                if v >= 0:
                    v = max(1, v + dep)
                if idx[y, xx] < 0 or v >= 0:
                    idx[y, xx] = v
    cv = Canvas()
    cv.idx = np.where(idx == -2, -1, idx)
    # holes: gaps under each strap; fill some back in so ~20% stays open
    frac = (cv.idx < 0).mean()
    if frac > 0.21:
        cand = np.argwhere(cv.idx < 0)
        rng.shuffle(cand)
        for (y, x) in cand[:int(round((frac - 0.20) * 256))]:
            cv.idx[y, x] = 1
    return cv.image(PAL['palm'])


LEAF_BLOB = [".45.",
             "4564",
             "3453",
             ".23."]
LEAF_SMALL = [".5.",
              "454",
              ".3."]
LEAF_TEAR = [".5.",
             "465",
             "454",
             ".3."]


def leaves_lumen(rng):
    """Glowing tear-drop leaves; only a few bright near-white glints."""
    cv = Canvas()
    for (x, y) in _grid(rng, step=4, jitter=1):
        _stamp(cv, LEAF_TEAR, x, y, off=-2, mirror=rng.random() < 0.5)
    for (x, y) in _grid(rng, step=4, jitter=1, ox=2, oy=2)[:13]:
        st = LEAF_TEAR if rng.random() < 0.7 else LEAF_BLOB
        _stamp(cv, st, x, y, off=0, mirror=rng.random() < 0.5)
    hi = np.argwhere(cv.idx == 6)
    rng.shuffle(hi)
    for (y, x) in hi[5:]:
        cv.idx[y, x] = 5
    carve_holes(cv, rng, 0.19)
    _shadow_under(cv)
    return cv.image(PAL['lumen'])


def leaves_baobab(rng):
    """Small olive leaflets in dense little clusters."""
    cv = Canvas()
    for (x, y) in _grid(rng, step=4, jitter=1):
        _stamp(cv, LEAF_BLOB, x, y, off=-2, mirror=rng.random() < 0.5)
    for (x, y) in _grid(rng, step=3, jitter=1, ox=1, oy=1, stagger=False):
        _stamp(cv, LEAF_SMALL, x, y, off=0 if rng.random() < 0.6 else -1)
    carve_holes(cv, rng, 0.19)
    _shadow_under(cv)
    return cv.image(PAL['baobab'])


# --------------------------------------------------------------------------
GREEN_LEAF = [".4.",
              "453",
              ".2."]


def leaves_wisteria(rng, kind='wisteria'):
    """Cascading blossom racemes over a sparse green leaf layer."""
    bloom = WISTERIA[kind]
    cv = Canvas()
    for (x, y) in _grid(rng, step=4, jitter=1):
        _stamp(cv, GREEN_LEAF, x, y, off=-1, mirror=rng.random() < 0.5)
    green = cv.idx.copy()
    cv.idx[:] = -1
    # racemes: (x, y, length); tapered, pale & open at the top, deep buds at the tip
    racemes = [(1, 0, 8), (6, 6, 7), (10, 1, 8), (14, 9, 7), (4, 12, 6), (11, 12, 5)]
    flower = np.full((16, 16), -1, int)
    for j, (px, py, L) in enumerate(racemes):
        px += int(rng.integers(0, 2))
        py += int(rng.integers(0, 2))
        for r in range(L):
            t = r / (L - 1)
            wdt = 3 if t < 0.45 else (2 if t < 0.8 else 1)
            x0 = px - (1 if wdt == 3 else 0)
            for c in range(wdt):
                v = 5.6 - 4.2 * t
                if wdt > 1 and c == 0:
                    v += 0.6
                if wdt > 1 and c == wdt - 1:
                    v -= 0.9
                if (r + c) % 2 == 1:
                    v -= 0.6          # alternating florets give a bumpy cluster
                flower[(py + r) % 16, (x0 + c) % 16] = int(np.clip(round(v), 0, 6))
        # tiny stalk where the raceme hangs from the branch
        if green[(py - 1) % 16, px % 16] < 0:
            green[(py - 1) % 16, px % 16] = 1
    img_g = paint(np.maximum(green, 0), WISTERIA_GREEN)
    img_f = paint(np.maximum(flower, 0), bloom)
    img = img_g.copy()
    img[flower >= 0] = img_f[flower >= 0]
    opaque = (flower >= 0) | (green >= 0)
    # top up / trim holes in the green areas only
    frac = 1 - opaque.mean()
    d = pnoise(rng, sx=0.7, sy=0.7)
    if frac < 0.18:
        score = np.where(flower >= 0, -99, np.where(green >= 0, d, -99))
        k = int(round((0.19 - frac) * 256))
        order = np.argsort(-score.ravel())[:k]
        opaque.ravel()[order] = False
    elif frac > 0.24:
        cand = np.argwhere(~opaque)
        rng.shuffle(cand)
        for (y, x) in cand[:int(round((frac - 0.22) * 256))]:
            opaque[y, x] = True
            img[y, x, :3] = [int(WISTERIA_GREEN[0][i:i + 2], 16) for i in (1, 3, 5)]
            img[y, x, 3] = 255
    img[~opaque] = 0
    return img


LEAVES = dict(redwood=leaves_redwood, willow=leaves_willow, palm=leaves_palm,
              lumen=leaves_lumen, baobab=leaves_baobab,
              wisteria=lambda r: leaves_wisteria(r, 'wisteria'))
