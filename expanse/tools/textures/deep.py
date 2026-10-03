"""The world underground's blocks (registry/DeepBlocks.java): icicles, amber, amber spikes, deeproot,
glowroots and glowworm silk."""
import math

import numpy as np

from texlib import blank, from_ascii, paint, pnoise, put, quantize, shade

# ----------------------------------------------------------------------------
# speleothems: an "up" silhouette per thickness (rows from the bottom), shaded from the left; "down" is the
# same turned over. Widths at the bottom and top of the block, and where the point is.
# ----------------------------------------------------------------------------
SHAPES = {
    # thickness: (width at bottom, width at top, rows filled from the bottom)
    'base': (12.0, 10.0, 16),
    'middle': (10.0, 8.0, 16),
    'frustum': (8.0, 4.5, 16),
    'tip': (4.5, 0.0, 12),
}


def _speleothem_up(rng, thickness, ramp, streak_frac=0.18, glints=()):
    img = blank()
    noise = pnoise(rng, sx=0.6, sy=3.0)
    if thickness == 'tip_merge':
        # two tips meeting: an hourglass, thinnest in the middle
        for r in range(16):
            w = 0.9 + 3.6 * abs(r - 7.5) / 7.5
            _row(img, r, w, ramp, noise)
    else:
        w0, w1, rows = SHAPES[thickness]
        for r in range(rows):
            t = r / max(1, rows - 1)
            w = w0 + (w1 - w0) * t
            if thickness == 'tip':
                w = w0 * (1 - t) ** 0.8 + 0.6
            _row(img, r, w, ramp, noise)
    # a few long streaks, as water or resin runs down it
    for x in range(16):
        if rng.random() < streak_frac:
            for y in range(16):
                if img[y, x, 3] and rng.random() < 0.8:
                    put(img, x, y, shade(tuple(int(v) for v in img[y, x, :3]), 1.12))
    for gx, gy in glints:
        if img[gy, gx, 3]:
            put(img, gx, gy, ramp[-1])
    return img


def _row(img, r, w, ramp, noise):
    y = 15 - r
    half = w / 2
    for x in range(16):
        d = x + 0.5 - 8
        if abs(d) <= half:
            # lit from the left: brighter toward the left edge, a dark rim on the right
            u = (d + half) / max(0.5, 2 * half)
            k = 3.4 - 3.0 * u + 0.7 * noise[y, x]
            if u > 0.86 and half > 1:
                k -= 1.2
            put(img, x, y, ramp[int(np.clip(round(k), 0, len(ramp) - 1))])


def speleothem_set(rng_for, name, ramp, glints=()):
    out = []
    for thickness in ['tip', 'tip_merge', 'frustum', 'middle', 'base']:
        up = _speleothem_up(rng_for(f'{name}_{thickness}'), thickness, ramp, glints=glints)
        out.append((f'textures/block/{name}_up_{thickness}.png', up, False))
        out.append((f'textures/block/{name}_down_{thickness}.png', up[::-1].copy(), False))
    return out


def speleothem_item(ramp):
    """A small spike, point up, for the inventory."""
    img = blank()
    for r in range(15):
        w = 6.0 * (1 - r / 14.0) ** 0.9 + 0.5
        _row(img, r, w, ramp, np.zeros((16, 16)))
    return img


ICE = ['#5f8fc4', '#86b6e2', '#b3dcf6', '#d9f1fd', '#ffffff']
AMBER_RAMP = ['#7a3d0a', '#b8661a', '#e39a2a', '#ffc65a', '#fff0b0']


# ----------------------------------------------------------------------------
# amber
# ----------------------------------------------------------------------------
def amber(rng, insect=False):
    swirl = pnoise(rng, sx=2.4, sy=1.2, angle=0.6) + 0.5 * pnoise(rng, sx=1.0, sy=1.0)
    idx = quantize(swirl, [0.12, 0.26, 0.32, 0.22, 0.08])
    img = paint(idx, ['#9a4c0e', '#c46e18', '#e8952a', '#f7b84a', '#ffd986'])
    # bubbles caught in it, lit from above
    for _ in range(5):
        x, y = int(rng.integers(0, 16)), int(rng.integers(0, 16))
        put(img, x, y, '#fff3c8')
        put(img, x + 1, y + 1, '#b25e14')
    # a few dark specks of grit
    for _ in range(3):
        put(img, int(rng.integers(0, 16)), int(rng.integers(0, 16)), '#5e2c08')
    if insect:
        body = [
            "................",
            "................",
            ".....a....a.....",
            "......a..a......",
            ".......hh.......",
            "....l..hh..l....",
            ".....l.tt.l.....",
            "......bBBb......",
            "....lbBBBBbl....",
            "......bBBb......",
            "....lbBBBBbl....",
            ".....bBBBBb.....",
            "......bBBb......",
            ".......bb.......",
            "................",
            "................",
        ]
        bug = from_ascii(body, {'a': '#4a2408', 'h': '#3a1c06', 't': '#5a2c0a', 'b': '#3e1e06', 'B': '#6e3a10', 'l': '#4a2408'})
        mask = bug[:, :, 3] > 0
        img[mask] = bug[mask]
        put(img, 7, 8, '#a8661e')  # a glint on its wing case
    return img


# ----------------------------------------------------------------------------
# deeproot
# ----------------------------------------------------------------------------
def deeproot_side(rng):
    """Gnarled root bark: twisted fibres, deep cracks, pale root hairs."""
    fib = pnoise(rng, sx=0.5, sy=4.0, angle=0.25)
    idx = quantize(fib, [0.14, 0.3, 0.34, 0.22])
    img = paint(idx, ['#24170d', '#3b2819', '#4f3622', '#674a2f'])
    for _ in range(6):
        x, y = int(rng.integers(0, 16)), int(rng.integers(0, 16))
        put(img, x, y, '#8a7055')       # root hairs and knots
        put(img, x, y + 1, '#5a4029')
    return img


def deeproot_top(rng):
    img = blank()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            wobble = 0.6 * math.sin(3 * a + 1.3) + 0.4 * rng.random()
            ring = int(d + wobble) % 3
            if d > 6.8 + 0.5 * math.sin(5 * a):
                c = '#3b2819' if (x + y) % 2 else '#24170d'
            else:
                c = ['#7a5636', '#5e4129', '#8d6a45'][ring]
            put(img, x, y, c)
    put(img, 7, 7, '#3b2819')
    put(img, 8, 8, '#3b2819')
    return img


# ----------------------------------------------------------------------------
# glowroots and glowworm silk (cross models, transparent)
# ----------------------------------------------------------------------------
def glowroots(rng):
    img = blank()
    for x0 in (2, 5, 7, 10, 13):
        x = x0 + int(rng.integers(-1, 2))
        length = int(rng.integers(8, 16))
        for y in range(length):
            if rng.random() < 0.25:
                x += int(rng.integers(-1, 2))
            x = max(0, min(15, x))
            put(img, x, y, '#5a3f28' if y < length - 3 else '#7a5a3a')
        # the glowing tip
        put(img, x, length - 1, '#d6ff8a')
        if length < 16:
            put(img, x, length, '#9be060')
        put(img, x, length - 2, '#b8f070')
    return img


def glowworm_silk(rng, tip=False):
    img = blank()
    for x0 in (3, 6, 9, 12):
        x = x0 + int(rng.integers(-1, 2))
        end = 16 if not tip else int(rng.integers(9, 15))
        for y in range(end):
            put(img, x, y, '#a9b9c2')
            # beads of sticky light along the thread
            if (y + x0) % 4 == 1:
                put(img, x, y, '#8ff0ff')
            if (y + x0) % 8 == 5:
                put(img, x, y, '#e0fdff')
        if tip:
            put(img, x, end, '#e0fdff')
            if end + 1 < 16:
                put(img, x, end + 1, '#8ff0ff')
    return img


def generate(rng_for):
    out = []
    out += speleothem_set(rng_for, 'icicle', ICE, glints=((6, 4), (5, 9), (7, 13)))
    out.append(('textures/item/icicle.png', speleothem_item(ICE), False))
    out += speleothem_set(rng_for, 'amber_spike', AMBER_RAMP, glints=((6, 6), (7, 11)))
    out.append(('textures/item/amber_spike.png', speleothem_item(AMBER_RAMP), False))
    out.append(('textures/block/amber.png', amber(rng_for('amber')), True))
    out.append(('textures/block/insect_amber.png', amber(rng_for('insect_amber'), insect=True), False))
    out.append(('textures/block/deeproot.png', deeproot_side(rng_for('deeproot')), True))
    out.append(('textures/block/deeproot_top.png', deeproot_top(rng_for('deeproot_top')), False))
    out.append(('textures/block/glowroots.png', glowroots(rng_for('glowroots')), False))
    out.append(('textures/block/glowworm_silk.png', glowworm_silk(rng_for('glowworm_silk')), False))
    out.append(('textures/block/glowworm_silk_tip.png', glowworm_silk(rng_for('glowworm_silk_tip'), tip=True), False))
    return out
