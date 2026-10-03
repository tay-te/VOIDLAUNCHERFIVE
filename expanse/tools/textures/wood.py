"""Wood sets: bark, log tops, stripped logs, planks (leaves / saplings /
doors / trapdoors live in their own modules)."""
import math

import numpy as np

from texlib import lerp, paint, pnoise, put, quantize

# --------------------------------------------------------------------------
# palettes (dark -> light).  Index 0 of bark is the deep crevice colour.
# --------------------------------------------------------------------------
WOODS = {
    'redwood': dict(
        bark=['#3e1b13', '#5a2a1e', '#67301f', '#773726', '#8a3e2a', '#9c4b32'],
        planks=['#6c2e1f', '#8f3f2b', '#a04a33', '#b5573b', '#c2664a'],
        stripped=['#8a3b28', '#9c4630', '#ad5239', '#bd6044', '#c86d50'],
        rings=['#8a3b28', '#a24c34', '#b55a3f', '#c4694c'],
    ),
    'willow': dict(
        bark=['#2f2a21', '#4c4436', '#584f3f', '#665c49', '#776b55', '#887c64'],
        planks=['#857850', '#a49668', '#b4a676', '#c9bb88', '#d6ca99'],
        stripped=['#968a5f', '#a99c6e', '#b9ad7d', '#c8bc8c', '#d4c99b'],
        rings=['#988c60', '#b0a374', '#c2b585', '#d1c697'],
    ),
    'palm': dict(
        bark=['#5e5140', '#7a6b52', '#8a7a5e', '#9c8b6b', '#ab9773', '#b7a37c', '#c8b690'],
        planks=['#a6824c', '#c9a46a', '#d6b47a', '#e2c48c', '#ecd3a0'],
        stripped=['#bf9a62', '#cba86f', '#d6b57e', '#e0c28d', '#e9cf9e'],
        rings=['#b48f58', '#c9a46a', '#d8b87e', '#e4c792'],
    ),
    'lumen': dict(
        bark=['#8e9da2', '#b9c4c6', '#c6d0d2', '#d4dddf', '#e3ecec', '#f2f7f7'],
        planks=['#86a0a8', '#a9bfc6', '#bccfd4', '#d0e0e3', '#dfecee'],
        stripped=['#b2c3c8', '#c3d1d5', '#d1dee1', '#dee9eb', '#ebf3f4'],
        rings=['#a7bcc2', '#bfd0d4', '#d2e0e3', '#e3eeef'],
        vein=['#2a9e94', '#4fe0cf', '#a6fbef'],
    ),
    'baobab': dict(
        bark=['#6d5d57', '#8c7b74', '#988780', '#a6938b', '#b7a39a', '#c5b2a9'],
        planks=['#98694f', '#b98a6e', '#c99a7d', '#d8ad8e', '#e3bc9f'],
        stripped=['#b38770', '#c3977e', '#cfa68c', '#dbb59b', '#e4c2a9'],
        rings=['#ad7f65', '#c4987d', '#d4aa8e', '#e1baa0'],
    ),
    'wisteria': dict(
        bark=['#2a2220', '#3f3632', '#4b413c', '#584c46', '#6a5c55', '#7c6d65'],
        planks=['#776a7e', '#9a8aa0', '#ab9bb1', '#c1b2c6', '#cec1d2'],
        stripped=['#8d7f8a', '#a09199', '#b0a2ab', '#c1b4bd', '#cec3cb'],
        rings=['#9b8c99', '#b1a3b0', '#c3b6c3', '#d2c7d3'],
    ),
}


# --------------------------------------------------------------------------
# helpers
# --------------------------------------------------------------------------
def _closed_walk(rng, n=16, stay=0.7, amp=None):
    """dx steps (-1/0/1) of length n whose sum is 0 (so a vertical line drawn
    with them wraps seamlessly)."""
    while True:
        steps = []
        for _ in range(n):
            r = rng.random()
            steps.append(0 if r < stay else (1 if r < stay + (1 - stay) / 2 else -1))
        s = sum(steps)
        # fix the sum by flipping some non-zero / zero steps
        idxs = list(range(n))
        rng.shuffle(idxs)
        for i in idxs:
            if s == 0:
                break
            if s > 0 and steps[i] >= 0:
                steps[i] -= 1
                s -= 1
            elif s < 0 and steps[i] <= 0:
                steps[i] += 1
                s += 1
        if s == 0:
            pos = np.cumsum([0] + steps[:-1])
            if amp is None or (pos.max() - pos.min()) <= amp:
                return pos


# --------------------------------------------------------------------------
# bark (log side)
# --------------------------------------------------------------------------
def bark_redwood(rng, p):
    f = pnoise(rng, sx=0.45, sy=6.0) + 0.45 * pnoise(rng, sx=0.5, sy=1.5)
    idx = quantize(f, [0.16, 0.30, 0.32, 0.22]) + 1
    # long fibrous furrows
    xs = [1, 5, 9, 13]
    rng.shuffle(xs)
    for k, x0 in enumerate(xs):
        x0 = (x0 + rng.integers(-1, 2)) % 16
        y0 = int(rng.integers(0, 16))
        length = int(rng.integers(9, 15)) if k > 0 else 16
        walk = _closed_walk(rng, 16, stay=0.82, amp=2)
        for i in range(length):
            y = (y0 + i) % 16
            x = (x0 + walk[(y0 + i) % 16]) % 16
            idx[y, x] = 0
            xr = (x + 1) % 16
            idx[y, xr] = min(idx[y, xr], 1 if i not in (0, length - 1) else 2)
            xl = (x - 1) % 16
            if idx[y, xl] >= 3 and rng.random() < 0.55:
                idx[y, xl] = 5 if rng.random() < 0.35 else 4
    return paint(idx, p['bark'])


def bark_willow(rng, p):
    f = pnoise(rng, sx=0.6, sy=3.0) + 0.4 * pnoise(rng, sx=1.0, sy=1.0)
    idx = quantize(f, [0.20, 0.32, 0.30, 0.18]) + 1
    # braided furrows: lines that drift diagonally and wrap
    for x0 in (0, 5, 10):
        x0 = (x0 + int(rng.integers(0, 2))) % 16
        walk = _closed_walk(rng, 16, stay=0.35, amp=4)
        for y in range(16):
            x = (x0 + walk[y]) % 16
            idx[y, x] = 0
            if rng.random() < 0.6:
                idx[y, (x + 1) % 16] = min(idx[y, (x + 1) % 16], 1)
            xl = (x - 1) % 16
            if idx[y, xl] >= 3 and rng.random() < 0.5:
                idx[y, xl] = 5
    # break a few crevice pixels so it doesn't look like ruled lines
    for _ in range(5):
        y, x = rng.integers(0, 16, 2)
        if idx[y, x] == 0:
            idx[y, x] = 1
    return paint(idx, p['bark'])


def bark_palm(rng, p):
    """Palm trunk: a lattice of diamond leaf-base scars arranged in rings."""
    n = pnoise(rng, sx=0.9, sy=0.5)
    idx = np.zeros((16, 16), int)
    for y in range(16):
        for x in range(16):
            u = (x + y) % 8
            v = (x - y) % 8
            if u == 0 or v == 0:
                idx[y, x] = 0
                continue
            yl = (u - v) / 2.0                 # -3.5 (top) .. +3.5 (bottom)
            ring = int(round((y - yl) / 4.0)) % 2   # alternate rows of scars = rings
            val = 3.6 - 0.45 * yl + 0.5 * n[y, x] - 0.8 * ring
            if u == 1:
                val += 1.4                      # lit upper-left rim
            elif v == 7:
                val += 0.7
            elif u == 7:
                val -= 1.6                      # shaded lower-right rim
            elif v == 1:
                val -= 0.8
            idx[y, x] = int(np.clip(round(val), 1, 6))
    # fibrous strands caught in a few grooves
    for _ in range(6):
        y, x = (int(v) for v in rng.integers(0, 16, 2))
        if idx[y, x] == 0:
            idx[y, x] = 2
    return paint(idx, p['bark'])


def bark_lumen(rng, p):
    f = pnoise(rng, sx=0.8, sy=3.5) + 0.3 * pnoise(rng, sx=0.6, sy=0.6)
    idx = quantize(f, [0.04, 0.20, 0.34, 0.28, 0.14])
    # a few soft horizontal lenticel marks
    for _ in range(4):
        y, x = int(rng.integers(0, 16)), int(rng.integers(0, 16))
        for dx in range(int(rng.integers(2, 4))):
            idx[y, (x + dx) % 16] = min(idx[y, (x + dx) % 16], 1)
        idx[(y + 1) % 16, x] = max(idx[(y + 1) % 16, x], 3)
    img = paint(idx, p['bark'])
    # glowing veins
    vdark, vmid, vbright = p['vein']
    for x0 in (3, 11):
        x0 += int(rng.integers(-1, 2))
        walk = _closed_walk(rng, 16, stay=0.75, amp=2)
        for y in range(16):
            x = (x0 + walk[y]) % 16
            put(img, x, y, vmid)
            if y > 0 and walk[y] != walk[y - 1]:
                put(img, (x0 + walk[y - 1]) % 16, y, vdark)
        # bright nodes
        for y in rng.choice(16, 2, replace=False):
            put(img, (x0 + walk[y]) % 16, int(y), vbright)
        # little branch
        yb = int(rng.integers(0, 16))
        xb = (x0 + walk[yb]) % 16
        d = 1 if rng.random() < 0.5 else -1
        put(img, xb + d, yb + 1, vdark)
        put(img, xb + 2 * d, yb + 2, vdark)
    return img


def bark_baobab(rng, p):
    f = pnoise(rng, sx=2.2, sy=1.6) + 0.35 * pnoise(rng, sx=0.5, sy=1.4)
    idx = quantize(f, [0.18, 0.32, 0.32, 0.18]) + 1
    # subtle horizontal creases, with a lit lip below each
    placed = []
    tries = 0
    while len(placed) < 7 and tries < 400:
        tries += 1
        x, y = int(rng.integers(0, 16)), int(rng.integers(0, 16))
        L = int(rng.integers(2, 6))
        if any(abs(((y - py + 8) % 16) - 8) < 2 or
               (abs(((y - py + 8) % 16) - 8) < 4 and abs(((x - px + 8) % 16) - 8) < 7) for px, py in placed):
            continue
        placed.append((x, y))
        bend = int(rng.choice([-1, 0, 0, 1]))
        for i in range(L):
            yy = y + (bend if (i == L - 1 and L > 2) else 0)
            core = i == L // 2
            idx[yy % 16, (x + i) % 16] = 0 if core else 1
            if rng.random() < 0.7:
                idx[(yy + 1) % 16, (x + i) % 16] = 4
    return paint(idx, p['bark'])


def _twist_coord(x, y, strands=3):
    """Coordinate across diagonal 'rope' strands (seamless: slope 1)."""
    t = (x - y + 1.0 * math.sin(2 * math.pi * y / 16)) % 16
    return (t * strands / 16.0) % 1.0


def bark_wisteria(rng, p):
    """Twisted, rope-like trunk: diagonal strands with deep crevices."""
    fib = pnoise(rng, sx=0.5, sy=3.0, angle=-math.pi / 4)
    det = pnoise(rng, sx=0.6, sy=0.6)
    idx = np.zeros((16, 16), int)
    for y in range(16):
        for x in range(16):
            f = _twist_coord(x, y)
            if f < 0.16:
                idx[y, x] = 0
                continue
            g = (f - 0.16) / 0.84                     # 0 lit edge .. 1 shadow edge
            val = 4.3 - 2.6 * g + 0.55 * fib[y, x] + 0.25 * det[y, x]
            idx[y, x] = int(np.clip(round(val), 1, 5))
    # break the crevices here and there so strands seem to merge
    for _ in range(4):
        y, x = (int(v) for v in rng.integers(0, 16, 2))
        if idx[y, x] == 0:
            idx[y, x] = 2
    return paint(idx, p['bark'])


BARK = dict(redwood=bark_redwood, willow=bark_willow, palm=bark_palm,
            lumen=bark_lumen, baobab=bark_baobab, wisteria=bark_wisteria)


# --------------------------------------------------------------------------
# log tops
# --------------------------------------------------------------------------
def _ring_field(rng, power=3.0, spiral=0.0, jitter=0.35, cx=7.5, cy=7.5):
    n = pnoise(rng, sx=1.2, sy=1.2)
    d = np.zeros((16, 16))
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            r = (abs(dx) ** power + abs(dy) ** power) ** (1 / power)
            if spiral:
                r += spiral * (math.atan2(dy, dx) / (2 * math.pi) + 0.5)
            d[y, x] = r + jitter * n[y, x]
    return d


def log_top(rng, wood, p, stripped=False):
    rings = p['rings']
    if wood == 'palm':
        # palms are monocots: fibrous dotted end grain, no growth rings
        f = pnoise(rng, sx=0.4, sy=0.4) + 0.3 * pnoise(rng, sx=1.5, sy=1.5)
        idx = quantize(f, [0.10, 0.45, 0.35, 0.10])
        d = _ring_field(rng, power=2.4, jitter=0.0)
        # darker fibre ring just inside the bark, paler heart
        idx = np.where((d > 5.6) & (idx > 0) & (idx < 3), idx - 1, idx)
        idx = np.where((d < 2.2) & (idx < 3), idx + 1, idx)
        img = paint(idx, rings)
    else:
        spiral = 2.0 if wood == 'wisteria' else 0.0
        power = {'baobab': 2.2, 'wisteria': 2.6, 'lumen': 2.8}.get(wood, 3.2)
        d = _ring_field(rng, power=power, spiral=spiral, jitter=0.3)
        spacing = 2.0
        ring = np.floor(d / spacing).astype(int)
        frac = (d / spacing) - ring
        idx = np.where(frac < 0.5, 1, 2)
        # rings get slightly lighter towards the centre
        idx = np.where((ring <= 1) & (idx == 2), 3, idx)
        idx = np.where((ring >= 3) & (idx == 1), 0, idx)
        idx[7:9, 7:9] = np.where(idx[7:9, 7:9] >= 2, 1, idx[7:9, 7:9])
        img = paint(idx, rings)
        if wood == 'lumen':
            vdark, vmid, vbright = p['vein']
            put(img, 7, 7, vmid); put(img, 8, 8, vmid)
            put(img, 8, 7, vbright); put(img, 7, 8, vdark)
            # one faint glowing growth ring
            glow = lerp(rings[2], vmid, 0.28)
            for y in range(16):
                for x in range(16):
                    if idx[y, x] == 1 and 4.0 < d[y, x] < 6.0:
                        put(img, x, y, glow)
    # rim
    if stripped:
        rim_cols = [p['stripped'][0], p['stripped'][1]]
        inner = rings[0]
    else:
        rim_cols = [p['bark'][1], p['bark'][2], p['bark'][3]]
        inner = None
    rn = pnoise(rng, sx=0.8, sy=0.8)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                k = int(np.clip((rn[y, x] + 1.2) / 2.4 * len(rim_cols), 0, len(rim_cols) - 1))
                put(img, x, y, rim_cols[k])
            elif inner is not None and (x in (1, 14) or y in (1, 14)) and rn[y, x] < -0.6:
                put(img, x, y, inner)
    if not stripped:
        # thick-barked woods: bark bites into the second ring here and there
        bites = {'redwood': 7, 'willow': 4, 'wisteria': 6, 'palm': 3}.get(wood, 0)
        for _ in range(bites):
            side = int(rng.integers(0, 4))
            t = int(rng.integers(2, 14))
            x, y = [(t, 1), (t, 14), (1, t), (14, t)][side]
            put(img, x, y, p['bark'][0] if rng.random() < 0.5 else p['bark'][1])
    return img


# --------------------------------------------------------------------------
# stripped log side
# --------------------------------------------------------------------------
def stripped_side(rng, wood, p):
    f = pnoise(rng, sx=0.5, sy=4.0) + 0.3 * pnoise(rng, sx=0.8, sy=1.0)
    idx = quantize(f, [0.10, 0.26, 0.32, 0.22, 0.10])
    if wood == 'wisteria':
        f2 = pnoise(rng, sx=0.5, sy=4.0, angle=-math.pi / 4)
        tw = np.array([[_twist_coord(x, y) for x in range(16)] for y in range(16)])
        f2 = f2 + 0.9 * np.sin(2 * math.pi * tw)
        idx = quantize(f2, [0.10, 0.26, 0.32, 0.22, 0.10])
    if wood == 'palm':
        # faint horizontal ring lines remain on stripped palm
        for y in (3, 11):
            for x in range(16):
                if idx[y, x] > 1 and rng.random() < 0.45:
                    idx[y, x] -= 1
    img = paint(idx, p['stripped'])
    if wood == 'lumen':
        vdark, vmid, vbright = p['vein']
        walk = _closed_walk(rng, 16, stay=0.6, amp=2)
        for y in range(16):
            put(img, (9 + walk[y]) % 16, y, mix_teal(p['stripped'][2], vmid))
        put(img, (9 + walk[5]) % 16, 5, vmid)
        put(img, (9 + walk[12]) % 16, 12, vmid)
    return img


def mix_teal(a, b):
    return lerp(a, b, 0.55)


# --------------------------------------------------------------------------
# planks
# --------------------------------------------------------------------------
def planks(rng, wood, p):
    pal = list(p['planks'])
    idx = np.zeros((16, 16), int)
    grain = pnoise(rng, sx=4.0, sy=0.3) + 0.35 * pnoise(rng, sx=0.6, sy=0.4)
    joints = [int(v) for v in rng.permutation([1, 5, 9, 12])]
    for b in range(4):
        y0 = b * 4
        boff = rng.normal(0, 0.2)
        for r in range(3):
            y = y0 + r
            for x in range(16):
                v = grain[y, x] + boff + (0.7 if r == 0 else (-0.35 if r == 2 else 0))
                idx[y, x] = 1 if v < -0.95 else (2 if v < 0.0 else (3 if v < 0.9 else 4))
        # seam row: mostly darkest, a few lighter pixels so it isn't a ruled line
        for x in range(16):
            idx[y0 + 3, x] = 0 if rng.random() < 0.78 else 1
        # end joint of the board (short, no bevel highlight)
        j = joints[b]
        idx[y0 + 1, j] = 0
        idx[y0 + 2, j] = 0
        idx[y0, j] = min(idx[y0, j], 2)
        # a dark grain streak per board
        x0 = int(rng.integers(0, 16))
        r = int(rng.integers(1, 3))
        for i in range(int(rng.integers(2, 5))):
            x = (x0 + i) % 16
            if x != j:
                idx[y0 + r, x] = 1
    img = paint(idx, pal)
    if wood == 'lumen':
        teal = lerp(pal[2], p['vein'][1], 0.45)
        for b in range(4):
            y = b * 4 + int(rng.integers(1, 3))
            x0 = int(rng.integers(0, 16))
            for i in range(int(rng.integers(3, 6))):
                x = (x0 + i) % 16
                if idx[y, x] not in (0,):
                    put(img, x, y, teal)
    return img
