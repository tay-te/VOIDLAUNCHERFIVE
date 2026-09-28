"""Split a flat launcher export into its fill-step sheets (board 11's anatomy).

Every pixel is classified to the nearest contract fill — shell, ground, card, raised — or to
INK (text, borders, cells, icons) when it matches none. Sheet k holds the shapes of fill k with
their interiors filled (the card under a raised pill is still card), so the sheets stack back
into the exact screen and pull apart cleanly in depth.

  python3 split_layers.py assets/shell_play@2x.png assets/layers/play
"""
import sys, os
import numpy as np
from PIL import Image
from scipy import ndimage

src, prefix = sys.argv[1], sys.argv[2]
os.makedirs(os.path.dirname(prefix), exist_ok=True)
im = np.asarray(Image.open(src).convert("RGBA")).astype(np.int16)
rgb = im[..., :3]
H, W = rgb.shape[:2]

FILLS = [("shell", (11, 11, 12)), ("ground", (19, 19, 21)), ("card", (25, 26, 28)), ("raised", (33, 34, 37))]
d = np.stack([np.abs(rgb - np.array(c)).sum(-1) for _, c in FILLS], -1)
cls = d.argmin(-1)
ink = d.min(-1) > 3

# outside the window's rounded corners (the export's light surround)
light = (rgb.min(-1) > 200)
lab, _ = ndimage.label(light)
corner_ids = {lab[0, 0], lab[0, W - 1], lab[H - 1, 0], lab[H - 1, W - 1]} - {0}
outside = np.isin(lab, list(corner_ids))
ink &= ~outside

# small patches of a fill colour are cells and marks drawn at in-between greys, not surfaces: ink
for k in (1, 2, 3):
    lab_k, n_k = ndimage.label((cls == k) & ~ink)
    if n_k:
        sizes = ndimage.sum(np.ones_like(lab_k), lab_k, index=np.arange(1, n_k + 1))
        small = np.isin(lab_k, np.nonzero(sizes < {1: 6000, 2: 4200, 3: 1500}[k])[0] + 1)
        ink |= small

# the fill under each ink pixel: nearest non-ink pixel's class
idx = ndimage.distance_transform_edt(ink, return_distances=False, return_indices=True)
under = cls[idx[0], idx[1]]

window = ~outside
sheets = {}
sheets["shell"] = window
for k, (name, _) in enumerate(FILLS[1:], start=1):
    m = (under == k) & window
    m = ndimage.binary_fill_holes(m)
    m = ndimage.binary_opening(m, iterations=1)       # drop 1-px anti-alias slivers
    sheets[name] = m & window

for k, (name, col) in enumerate(FILLS):
    out = np.zeros((H, W, 4), np.uint8)
    out[..., :3] = col
    out[..., 3] = sheets[name].astype(np.uint8) * 255
    Image.fromarray(out).save(f"{prefix}_{k}_{name}.png")
    print(name, int(sheets[name].sum()))

out = np.zeros((H, W, 4), np.uint8)
out[..., :3] = rgb.clip(0, 255).astype(np.uint8)
out[..., 3] = (ink & window).astype(np.uint8) * 255
Image.fromarray(out).save(f"{prefix}_4_ink.png")
print("ink", int(ink.sum()))
