"""Textures for the landform biomes' blocks (src/main/java/.../registry/LandformBlocks.java)."""
import numpy as np

from texlib import paint, pnoise, put, quantize

ASH = ['#2f2c2c', '#3d3939', '#4a4646', '#565151', '#625d5c', '#6f6a68', '#7d7875']
TUFF = ['#4d4b45', '#5b5952', '#67655d', '#726f66', '#7d7a70', '#88857a']
SULPHUR = ['#8a7a2e', '#b39d36', '#d6c04a', '#efe07a']


def volcanic_ash(rng):
    f = pnoise(rng, sx=0.7, sy=0.7) + 0.6 * pnoise(rng, sx=2.0, sy=2.0)
    idx = quantize(f, [0.06, 0.14, 0.24, 0.24, 0.18, 0.10, 0.04])
    img = paint(idx, ASH)
    # pumice grit: a few pale flecks
    for _ in range(6):
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        put(img, x, y, '#9c958f')
    return img


def fumarole_side(rng):
    f = pnoise(rng, sx=0.8, sy=1.6) + 0.5 * pnoise(rng, sx=2.4, sy=2.4)
    idx = quantize(f, [0.10, 0.2, 0.3, 0.22, 0.12, 0.06])
    img = paint(idx, TUFF)
    # sulphur crust creeping down from the top
    for x in range(16):
        depth = int(1 + 3 * rng.random())
        for y in range(depth):
            put(img, x, y, SULPHUR[min(3, int(rng.random() * 4))])
    return img


def fumarole_top(rng):
    f = pnoise(rng, sx=0.9, sy=0.9) + 0.5 * pnoise(rng, sx=2.2, sy=2.2)
    idx = quantize(f, [0.1, 0.2, 0.3, 0.22, 0.12, 0.06])
    img = paint(idx, TUFF)
    # the vent: a dark throat ringed with yellow sulphur
    for y in range(16):
        for x in range(16):
            d = np.hypot(x - 7.5, y - 7.5) + 0.8 * rng.random()
            if d < 2.6:
                put(img, x, y, '#1a1414' if d < 1.8 else '#3a2a1e')
            elif d < 4.8:
                put(img, x, y, SULPHUR[min(3, int((4.8 - d) * 1.4 + rng.random()))])
    return img


SALT = ['#cfc9c0', '#ddd8cf', '#e8e4dc', '#f1eee8', '#f8f6f1', '#ffffff']


def salt_block(rng):
    f = pnoise(rng, sx=1.2, sy=1.2) + 0.5 * pnoise(rng, sx=3.0, sy=3.0)
    idx = quantize(f, [0.06, 0.14, 0.26, 0.28, 0.18, 0.08])
    img = paint(idx, SALT)
    # the polygonal cracks a drying pan leaves: a few faint grey seams
    for _ in range(3):
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        dx, dy = (1, 0) if rng.random() < 0.5 else (0, 1)
        for k in range(int(4 + 5 * rng.random())):
            put(img, x + k * dx, y + k * dy, '#bdb6ab')
    return img


def generate(rng_for):
    return [
        ('textures/block/volcanic_ash.png', volcanic_ash(rng_for('volcanic_ash')), True),
        ('textures/block/fumarole_side.png', fumarole_side(rng_for('fumarole_side')), True),
        ('textures/block/fumarole_top.png', fumarole_top(rng_for('fumarole_top')), False),
        ('textures/block/salt_block.png', salt_block(rng_for('salt_block')), True),
    ]
