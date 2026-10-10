"""Soft textures for the hub's native card effects (dev.voidmc.hub.screen.CardFx).

The card strips are pixel art and come from gen_cards.py. The glows the page used to paint as CSS radial gradients
are drawn by Java now, so they need textures: a white bloom (tinted by the vertex colour) and the Mythic's prismatic
halo ring (cyan to pink, its own colours). Both are smooth, sampled LINEAR. Deterministic.

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


if __name__ == "__main__":
    save("bloom.png", bloom())
    save("prism.png", prism())
    print("wrote", os.path.abspath(OUT))
