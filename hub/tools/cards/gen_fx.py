"""Soft textures for the hub's native card effects (dev.voidmc.hub.screen.CardFx).

The card strips are pixel art and come from gen_cards.py. The glows the page used to paint as CSS radial gradients
are drawn by Java now, so they need textures: a white bloom (tinted by the vertex colour) and the Mythic's prismatic
halo ring (cyan to pink, its own colours). The draft hand's red warmth (halo.png) is a page background. All smooth, sampled LINEAR. Deterministic.

    python hub/tools/cards/gen_fx.py      (Python 3, Pillow, numpy)
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "voidhub", "ui", "cards", "fx")


def radial(size):
    y, x = np.mgrid[0:size, 0:size].astype(np.float64)
    c = (size - 1) / 2.0
    return np.sqrt((x - c) ** 2 + (y - c) ** 2) / c  # 0 at the centre, 1 at the edge


def save(name, rgba):
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(np.clip(rgba * 255 + 0.5, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name), optimize=True)


def bloom(size=256):
    """White, alpha falling off smoothly to nothing at the edge (closest-side radial, like the CSS it replaces)."""
    r = radial(size)
    a = np.clip(1.0 - r, 0, 1) ** 1.8
    rgba = np.ones((size, size, 4))
    rgba[..., 3] = a
    return rgba


def prism(size=256):
    """The Mythic's prismatic halo: a thin ring, cyan inside to pink outside, clear in the middle and beyond."""
    r = radial(size)
    ring = np.exp(-((r - 0.86) / 0.06) ** 2)
    t = np.clip((r - 0.78) / 0.16, 0, 1)
    cyan = np.array([143, 240, 255]) / 255.0
    pink = np.array([255, 155, 234]) / 255.0
    rgba = np.zeros((size, size, 4))
    for k in range(3):
        rgba[..., k] = cyan[k] * (1 - t) + pink[k] * t
    rgba[..., 3] = ring * 0.22
    return rgba


def halo(size=256):
    """The dark red warmth behind a draft hand (a page background, scaled to the hand's box): alpha reaches exactly
    nothing at the box's edge, so stretched to any box it never shows a hard edge. Premultiplied-safe dark red."""
    r = radial(size)
    rgba = np.zeros((size, size, 4))
    rgba[..., 0], rgba[..., 1], rgba[..., 2] = 106 / 255.0, 15 / 255.0, 24 / 255.0
    rgba[..., 3] = np.clip(1.0 - r, 0, 1) ** 1.6 * 0.42
    return rgba


def mythic_frame_glow(scale=2, w=96, h=128, pad=8, radius=10):
    """The steady white-gold glow round a Mythic in the binder (collection 96 x 128 + 8 px each side, x2): strongest at
    the card's rounded edge, gone 8 px out; clear inside (the card covers it). A page background, so it costs nothing
    once painted."""
    W, H = (w + pad * 2) * scale, (h + pad * 2) * scale
    y, x = np.mgrid[0:H, 0:W].astype(np.float64) / scale + 0.5 / scale
    # distance outside the rounded rect [pad, pad + w] x [pad, pad + h]
    qx = np.abs(x - (pad + w / 2.0)) - (w / 2.0 - radius)
    qy = np.abs(y - (pad + h / 2.0)) - (h / 2.0 - radius)
    d = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2) + np.minimum(np.maximum(qx, qy), 0) - radius
    a = np.where(d > 0, np.clip(1 - d / pad, 0, 1) ** 2 * 0.6, 0.0)
    rgba = np.zeros((H, W, 4))
    rgba[..., 0], rgba[..., 1], rgba[..., 2] = 255 / 255.0, 236 / 255.0, 190 / 255.0
    rgba[..., 3] = a
    return rgba


if __name__ == "__main__":
    save("bloom.png", bloom())
    save("prism.png", prism())
    save("halo.png", halo())
    save("mythic_glow.png", mythic_frame_glow())
    print("wrote", os.path.abspath(OUT))
