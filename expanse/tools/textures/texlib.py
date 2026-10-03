"""Shared helpers for the VOID Expanse procedural texture generator.

Everything works on small numpy arrays.  Textures are usually built as an
*index map* (2D int array) that is then painted with a short hand-picked
palette, which keeps the vanilla-like "few shades per material" look.
Alpha is always 0 or 255.
"""
import colorsys
import math

import numpy as np
from PIL import Image

T = None  # transparent marker in palettes / ascii maps


# --------------------------------------------------------------------------
# colour helpers
# --------------------------------------------------------------------------
def hexc(h):
    if h is None:
        return None
    if isinstance(h, tuple):
        return h
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def lerp(a, b, t):
    a, b = hexc(a), hexc(b)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f, hue_shift=0.0, sat=1.0):
    """Darken (f<1) / lighten (f>1) a colour with an optional hue shift
    (in turns) - darker pixel-art shades read better when shifted slightly."""
    r, g, b = [v / 255 for v in hexc(c)]
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    if f < 1:
        l = l * f
    else:
        l = l + (1 - l) * (f - 1)
    h = (h + hue_shift) % 1.0
    s = max(0.0, min(1.0, s * sat))
    r, g, b = colorsys.hls_to_rgb(h, max(0, min(1, l)), s)
    return (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)))


# --------------------------------------------------------------------------
# noise
# --------------------------------------------------------------------------
def pnoise(rng, w=16, h=16, sx=1.0, sy=1.0, angle=0.0):
    """Periodic (seamlessly tiling) gaussian-filtered noise, zero mean / unit
    std.  sx / sy are the blur sigmas in pixels along the (optionally rotated)
    axes, so sx small & sy large gives vertical streaks."""
    white = rng.standard_normal((h, w))
    F = np.fft.fft2(white)
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    if angle:
        ca, sa = math.cos(angle), math.sin(angle)
        u = fx * ca + fy * sa
        v = -fx * sa + fy * ca
    else:
        u, v = fx, fy
    G = np.exp(-2 * (math.pi ** 2) * ((u * sx) ** 2 + (v * sy) ** 2))
    n = np.real(np.fft.ifft2(F * G))
    n = n - n.mean()
    return n / (n.std() + 1e-9)


def quantize(field, fracs, mask=None):
    """Map a float field to integer levels 0..len(fracs)-1 so that level i
    covers roughly fracs[i] of the pixels (inside mask if given)."""
    fracs = np.asarray(fracs, dtype=float)
    fracs = fracs / fracs.sum()
    vals = field[mask] if mask is not None else field.ravel()
    qs = np.quantile(vals, np.cumsum(fracs)[:-1])
    return np.digitize(field, qs)


def lowest(field, frac, mask=None):
    """Boolean mask of the lowest `frac` fraction of a field."""
    vals = field[mask] if mask is not None else field.ravel()
    q = np.quantile(vals, frac)
    m = field <= q
    if mask is not None:
        m &= mask
    return m


# --------------------------------------------------------------------------
# images
# --------------------------------------------------------------------------
def blank(w=16, h=16):
    return np.zeros((h, w, 4), dtype=np.uint8)


def paint(idx, palette, mask=None):
    """index map + palette (list of rgb / None) -> RGBA array."""
    h, w = idx.shape
    out = blank(w, h)
    for i, c in enumerate(palette):
        if c is None:
            continue
        sel = idx == i
        if mask is not None:
            sel &= mask
        out[sel, :3] = hexc(c)
        out[sel, 3] = 255
    return out


def put(img, x, y, c, wrap=True):
    h, w = img.shape[:2]
    if wrap:
        x %= w
        y %= h
    elif not (0 <= x < w and 0 <= y < h):
        return
    if c is None:
        img[y, x] = 0
    else:
        img[y, x, :3] = hexc(c)
        img[y, x, 3] = 255


def from_ascii(rows, mapping, w=16, h=16):
    """Build an RGBA image from rows of characters.  mapping: char -> colour
    (None / missing / '.' = transparent)."""
    img = blank(w, h)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            c = mapping.get(ch)
            if c is not None and ch != '.':
                put(img, x, y, c, wrap=False)
    return img


def outline(img, factor=0.55, hue_shift=0.0, diagonal=False, color=None):
    """Add a 1px outline *outside* the opaque silhouette, coloured with a
    darker shade of the neighbouring pixel colour (vanilla item style)."""
    h, w = img.shape[:2]
    out = img.copy()
    op = img[:, :, 3] > 0
    nbrs = [(0, -1), (0, 1), (-1, 0), (1, 0)]
    if diagonal:
        nbrs += [(-1, -1), (1, -1), (-1, 1), (1, 1)]
    for y in range(h):
        for x in range(w):
            if op[y, x]:
                continue
            cols = []
            for dx, dy in nbrs:
                xx, yy = x + dx, y + dy
                if 0 <= xx < w and 0 <= yy < h and op[yy, xx]:
                    cols.append(tuple(int(v) for v in img[yy, xx, :3]))
            if cols:
                if color is not None:
                    c = hexc(color)
                else:
                    # darkest neighbour drives the outline colour
                    c = min(cols, key=lambda c: sum(c))
                    c = shade(c, factor, hue_shift)
                out[y, x, :3] = c
                out[y, x, 3] = 255
    return out


def save(img, path):
    img = img.copy()
    a = img[:, :, 3]
    img[:, :, 3] = np.where(a >= 128, 255, 0).astype(np.uint8)
    img[img[:, :, 3] == 0, :3] = 0
    Image.fromarray(img, 'RGBA').save(path, optimize=True)
