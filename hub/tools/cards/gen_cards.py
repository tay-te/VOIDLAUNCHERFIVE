#!/usr/bin/env python3
"""
VOID Mettle perk-card textures and sprite strips for VOID Hub (Ultralight).

    python hub/tools/cards/gen_cards.py            # regenerate everything into ui/cards/
    python hub/tools/cards/gen_cards.py --bake     # also bake one tinted copy per skill colour
                                                   # into hub/build/cards-baked/ (fallback only)

Design: Figma 3c0Wgc4ZKFSpm98fWJoARS, page 295:2, sections 2h (546:23595), 2f (510:18631),
2g set-complete (539:23542). Everything is procedural pixel art on a fixed texel grid:

    hand mode        1 texel = 4 CSS px   (card 248 x 328 = 62 x 82 texels)
    collection mode  1 texel = 2 CSS px   (card  96 x 128 = 48 x 64 texels)
    icons            1 texel = 2 CSS px   (glyph 8 x 8 -> 16 px, gem 7 x 7 -> 14 px)

Each asset is written at native texels (_px) and pre-upscaled nearest-neighbour to its exact display
size (_x1) and to x2 / x3 for larger device scales, because Ultralight smooths every image it scales
and ignores image-rendering: pixelated. Strips are horizontal; manifest.json carries frame size,
count and fps. Burning edges are WHITE masks ("fill", tinted by the page through -webkit-mask-image
with background-color = skill colour) plus a white highlight overlay ("glow") drawn on top; both
verified in the hub's Ultralight 1.4 build. Mythic, ash, card back and set rays are baked colour.

Deterministic: fixed seeds, so a regenerate reproduces the same bytes.
"""
import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
HUB = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(HUB, 'src', 'main', 'resources', 'assets', 'voidhub', 'ui', 'cards')

SCALES = (1, 2, 3)

# Skill colours: mettle/src/main/resources/data/mettle/mettle/skill/*.json ("colour").
SKILL_COLOURS = {
    'alchemy': '#5ED1E8', 'athletics': '#27C5B5', 'blades': '#8DB1D8', 'defense': '#3F95FF',
    'heavy_weapons': '#E2455A', 'husbandry': '#9CC97A', 'marksmanship': '#43BE6C',
    'mining': '#F5B03C', 'riding': '#C79A6B', 'scholarship': '#E6A3C7', 'smithing': '#FF6B35',
    'speech': '#E8C46A', 'stealth': '#7E7AE6', 'survival': '#A6BA48', 'thievery': '#C46BE0',
}
SPECIAL_COLOURS = {
    'cursed': '#FF2E4A',      # hand burn + eyebrow (Figma 2h Cursed)
    'mythic': '#F3D28B',      # white-gold; Mythic assets are baked, this is for CSS accents
    'earned': '#E8DDBC',      # champagne gold
    'keyword': '#C9CCD1',     # keyword json "colour"
    'glyph': '#D8CDBB',       # glyph / keyword components: one neutral colour
}
RARITY_COLOURS = {            # collection borders and gems (Figma 2h collection variants)
    'common': '#B9BEC6', 'rare': '#4DA3F5', 'epic': '#B27CFF', 'cursed': '#D8453B',
    'mythic': '#F3D28B', 'earned': '#E8DDBC',
}

# Keyword glyphs, 8 x 8, from the Figma components "glyph / keyword / *" (539:23796).
GLYPHS = {
    'brutal': ['######..', '######..', '######..', '..##....', '..##....', '..##....', '..##....', '..##....'],
    'deep':   ['#......#', '.#....#.', '..#..#..', '...##...', '........', '########', '#.#..#.#', '########'],
    'echo':   ['..####..', '.#....#.', '#..##..#', '#.#..#.#', '#.#..#.#', '#..##..#', '.#....#.', '..####..'],
    'ember':  ['....#...', '...##...', '..###.#.', '..####..', '.######.', '.##.###.', '.#...##.', '..####..'],
    'gale':   ['#####...', '.....#..', '####..#.', '......#.', '######..', '........', '###.....', '...#....'],
    'hex':    ['.######.', '########', '#..##..#', '#..##..#', '########', '.##..##.', '.#.##.#.', '........'],
    'hoard':  ['..####..', '.######.', '..####..', '.######.', '..####..', '.######.', '..####..', '........'],
    'iron':   ['........', '..####..', '.#....#.', '########', '#......#', '########', '........', '........'],
    'keen':   ['...#....', '...#....', '..###...', '###.###.', '..###...', '...#....', '...#....', '........'],
    'kin':    ['.#...#..', '###.###.', '.#...#..', '###.###.', '#.#.#.#.', '#.#.#.#.', '........', '........'],
    'swift':  ['#...#...', '.#...#..', '..#...#.', '...#...#', '..#...#.', '.#...#..', '#...#...', '........'],
    'tide':   ['........', '.##..##.', '#..##..#', '........', '.##..##.', '#..##..#', '........', '........'],
    'warden': ['########', '#......#', '#.####.#', '#.####.#', '.#.##.#.', '.#....#.', '..#..#..', '...##...'],
    'wild':   ['......##', '....####', '..#####.', '.####.#.', '.###.#..', '..#.#...', '.#......', '#.......'],
}

# Geometry ----------------------------------------------------------------------------------------
HAND_TEX = 4
HAND_CARD = (62, 82)          # texels (248 x 328 px)
HAND_M = 7                    # margin texels around the card in edge frames (28 px)
HAND_FRAME = (HAND_CARD[0] + 2 * HAND_M, HAND_CARD[1] + 2 * HAND_M)   # 76 x 96 -> 304 x 384 px
HAND_R = 1.5                  # corner radius, texels

COLL_TEX = 2
COLL_CARD = (48, 64)          # 96 x 128 px
COLL_M = 4
COLL_FRAME = (COLL_CARD[0] + 2 * COLL_M, COLL_CARD[1] + 2 * COLL_M)   # 56 x 72 -> 112 x 144 px
COLL_R = 3.0

MANIFEST = {}


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# Noise -------------------------------------------------------------------------------------------
def value_noise(h, w, cell, rng):
    """Smooth value noise in [0, 1], feature size ~cell texels."""
    gh, gw = int(h / cell) + 3, int(w / cell) + 3
    g = rng.random((gh, gw))
    ys = (np.arange(h) + 0.5) / cell
    xs = (np.arange(w) + 0.5) / cell
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = ys - y0
    fx = xs - x0
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = g[np.ix_(y0, x0)]
    b = g[np.ix_(y0, x0 + 1)]
    c = g[np.ix_(y0 + 1, x0)]
    d = g[np.ix_(y0 + 1, x0 + 1)]
    top = a + (b - a) * fx[None, :]
    bot = c + (d - c) * fx[None, :]
    n = top + (bot - top) * fy[:, None]
    lo, hi = n.min(), n.max()
    return (n - lo) / max(1e-6, hi - lo)


def rrect_sdf(w, h, x0, y0, cw, ch, r):
    """Signed distance (texels) to a rounded rect, sampled at texel centres; > 0 outside."""
    ys, xs = np.mgrid[0:h, 0:w].astype(float)
    px = xs + 0.5 - (x0 + cw / 2.0)
    py = ys + 0.5 - (y0 + ch / 2.0)
    qx = np.abs(px) - (cw / 2.0 - r)
    qy = np.abs(py) - (ch / 2.0 - r)
    outside = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0))
    inside = np.minimum(np.maximum(qx, qy), 0)
    return outside + inside - r


# Writing -----------------------------------------------------------------------------------------
def to_png(arr, path):
    """RGBA uint8 array -> PNG, palette mode when it fits (much smaller), else RGBA."""
    flat = np.ascontiguousarray(arr.reshape(-1, 4)).copy()
    # Fully transparent pixels collapse to one colour.
    flat[flat[:, 3] == 0] = 0
    packed = flat.view(np.uint32).ravel()
    keys, inv = np.unique(packed, return_inverse=True)
    cols = keys.view(np.uint8).reshape(-1, 4)
    if len(cols) <= 256:
        idx = inv.reshape(arr.shape[:2]).astype(np.uint8)
        im = Image.fromarray(idx, 'P')
        pal = cols[:, :3].astype(np.uint8).flatten().tolist()
        pal += [0] * (768 - len(pal))
        im.putpalette(pal)
        alpha = bytes(int(a) for a in cols[:, 3])
        im.save(path, optimize=True, transparency=alpha)
    else:
        Image.fromarray(flat.reshape(arr.shape), 'RGBA').save(path, optimize=True)


def upscale(arr, k):
    return arr.repeat(k, axis=0).repeat(k, axis=1)


def write_asset(key, frames, texel, meta, scales=SCALES, native=True):
    """frames: list of HxWx4 uint8 (native texels). Writes key_px / key_x1.. strips; returns file map."""
    strip = np.concatenate(frames, axis=1) if len(frames) > 1 else frames[0]
    path = os.path.join(OUT, key)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    files = {}
    if native:
        to_png(strip, path + '_px.png')
        files['px'] = key + '_px.png'
    for s in scales:
        to_png(upscale(strip, texel * s), path + '_x%d.png' % s)
        files['x%d' % s] = key + '_x%d.png' % s
    h, w = frames[0].shape[:2]
    entry = {
        'frames': len(frames),
        'frame': [w * texel, h * texel],
        'strip': [w * texel * len(frames), h * texel],
        'texel': texel,
        'files': files,
        'decodedBytes': {('x%d' % s): (w * texel * s) * len(frames) * (h * texel * s) * 4 for s in scales},
    }
    entry.update(meta)
    return entry


def white_alpha(alpha):
    """float alpha array (H, W) -> white RGBA uint8."""
    a = np.clip(np.round(alpha * 255), 0, 255).astype(np.uint8)
    out = np.zeros(alpha.shape + (4,), np.uint8)
    out[..., :3] = 255
    out[..., 3] = a
    out[a == 0] = 0
    return out


def rgba_canvas(h, w):
    return np.zeros((h, w, 4), np.uint8)


def put(img, x, y, rgb, a=255):
    h, w = img.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        if a >= img[y, x, 3]:
            img[y, x, :3] = rgb
            img[y, x, 3] = a


def register(key, entry):
    MANIFEST[key] = entry


def tinted(key, fill_frames, glow_frames, texel, meta):
    """A tinted strip = fill mask (page colours it) + glow overlay (white highlights)."""
    f = write_asset(key + '_fill', [white_alpha(a) for a in fill_frames], texel, {})
    g = write_asset(key + '_glow', [white_alpha(a) for a in glow_frames], texel, {})
    entry = {
        'kind': 'tinted',
        'frames': f['frames'], 'frame': f['frame'], 'strip': f['strip'], 'texel': texel,
        'layers': {
            'fill': {'use': 'mask: -webkit-mask-image + background-color: <tint>', 'files': f['files']},
            'glow': {'use': 'background-image, drawn over fill', 'files': g['files']},
        },
        'decodedBytes': {k: f['decodedBytes'][k] + g['decodedBytes'][k] for k in f['decodedBytes']},
    }
    entry.update(meta)
    register(key, entry)
    return entry


# Burning edge ------------------------------------------------------------------------------------
TONE_DARK = 0.4      # fill alpha for char texels: colour at 40 % over the near-black card = base x 0.4
GLOW_LIGHT = 0.55    # white over base = the design's light tone (e.g. F5B03C -> FBDBA7)
GLOW_WHITE = 0.85    # white-hot core (Epic / hover / flare)

EDGE_SPECS = {
    #            rim_min rim_var out_max  hot   white  dark  sparks cell  amp   holes
    'common':   dict(rim_min=1, rim_var=1.0, out_max=1.4, hot=0.05, white=0.0, dark=0.45, sparks=4, cell=3.0, amp=0.15, holes=0.0),
    'rare':     dict(rim_min=2, rim_var=1.0, out_max=2.7, hot=0.22, white=0.0, dark=0.16, sparks=11, cell=3.0, amp=0.30, holes=0.0),
    'epic':     dict(rim_min=2, rim_var=1.0, out_max=3.9, hot=0.34, white=0.07, dark=0.10, sparks=18, cell=3.0, amp=0.40, holes=0.0),
    'cursed':   dict(rim_min=2, rim_var=1.0, out_max=3.9, hot=0.26, white=0.0, dark=0.24, sparks=18, cell=2.0, amp=0.45, holes=0.0),
    'wildcard': dict(rim_min=2, rim_var=1.0, out_max=2.9, hot=0.20, white=0.0, dark=0.10, sparks=11, cell=3.0, amp=0.30, holes=0.14),
    'hover':    dict(rim_min=3, rim_var=0.0, out_max=4.6, hot=0.42, white=0.14, dark=0.05, sparks=24, cell=3.0, amp=0.40, holes=0.0),
}


class EdgeField:
    """Shared fields for one edge strip so every frame of the loop is coherent."""

    def __init__(self, geom, cell, seed):
        (self.w, self.h), (self.x0, self.y0), (self.cw, self.ch), r = geom
        rng = np.random.default_rng(seed)
        self.rng = rng
        self.d = rrect_sdf(self.w, self.h, self.x0, self.y0, self.cw, self.ch, r)
        n = lambda c: value_noise(self.h, self.w, c, rng)
        self.A = n(cell)
        self.P = n(cell * 2.5)
        self.G = n(cell)
        self.GP = n(cell * 2.0)
        self.H = n(cell * 0.8)
        self.HP = n(cell * 1.5)
        self.r_fixed = rng.random((self.h, self.w))
        self.side = n(5.0)


def edge_frame(field, spec, f, F, rng):
    """One frame: returns (fill alpha, glow alpha) float arrays."""
    d = field.d
    ph = 2 * math.pi * f / F
    amp = spec['amp']
    Ff = np.clip(field.A * (1 - amp) + amp * (0.5 + 0.5 * np.cos(ph + 4 * math.pi * field.P)), 0, 1)
    Gf = np.clip(field.G * (1 - amp) + amp * (0.5 + 0.5 * np.cos(ph + 4 * math.pi * field.GP)), 0, 1)
    Hf = np.clip(field.H * 0.5 + 0.5 * (0.5 + 0.5 * np.cos(ph + 4 * math.pi * field.HP)), 0, 1)
    flame = spec['out_max'] * Ff ** 1.4
    inner = np.minimum(spec['rim_min'] + spec['rim_var'] * Gf, 3.0)      # 3 texels = 12 px cap
    fire_out = (d > 0) & (d <= flame)
    fire_in = (d <= 0) & (-d < inner)
    fire = fire_out | fire_in

    r = 0.6 * field.r_fixed + 0.4 * rng.random(d.shape)
    tip = (fire_out & (d > flame - 1.0)) | (fire_in & (-d > inner - 1.0) & (inner > 1.5))
    dark = fire & ((r < spec['dark']) | (tip & (rng.random(d.shape) < 0.35)))
    holes = fire & (rng.random(d.shape) < spec['holes']) if spec['holes'] > 0 else np.zeros_like(fire)

    near = np.abs(d) < 1.6
    heat = Hf + 0.25 * near + 0.1 * rng.random(d.shape)
    cand = fire & ~dark & ~holes
    fill = np.where(fire, 1.0, 0.0)
    fill[dark] = TONE_DARK
    fill[holes] = 0.0
    glow = np.zeros_like(fill)
    if cand.any() and spec['hot'] > 0:
        thr = np.quantile(heat[cand], 1 - spec['hot'])
        hot = cand & (heat >= thr)
        glow[hot] = GLOW_LIGHT
        if spec['white'] > 0:
            thw = np.quantile(heat[cand], 1 - spec['white'])
            glow[cand & (heat >= thw)] = GLOW_WHITE
    return fill, glow


def add_sparks(fills, glows, field, n, F, rng, dmin=1.5, dmax=6.5, life=(2, 3), rise=1):
    """Single-texel sparks that rise one texel per frame and cool: hot -> base -> char."""
    d = field.d
    ys, xs = np.nonzero((d >= dmin) & (d <= dmax))
    if len(xs) == 0:
        return
    # Prefer the top half: sparks rise.
    wts = 1.0 + (ys < field.y0 + field.ch * 0.5) * 1.5
    wts = wts / wts.sum()
    for _ in range(n):
        i = rng.choice(len(xs), p=wts)
        x, y = int(xs[i]), int(ys[i])
        birth = int(rng.integers(0, F))
        L = int(rng.integers(life[0], life[1] + 1))
        drift = int(rng.choice([-1, 0, 0, 1]))
        for age in range(L):
            fr = (birth + age) % F
            yy = y - age * rise
            xx = x + (drift if age == L - 1 else 0)
            if 0 <= xx < field.w and 0 <= yy < field.h:
                if age == 0:
                    fills[fr][yy, xx] = 1.0
                    glows[fr][yy, xx] = max(glows[fr][yy, xx], GLOW_LIGHT)
                elif age == L - 1:
                    fills[fr][yy, xx] = max(fills[fr][yy, xx], TONE_DARK)
                else:
                    fills[fr][yy, xx] = 1.0


def hand_geom():
    return (HAND_FRAME, (HAND_M, HAND_M), HAND_CARD, HAND_R)


def build_hand_edges():
    for i, (name, spec) in enumerate(EDGE_SPECS.items()):
        F = 4
        field = EdgeField(hand_geom(), spec['cell'], 100 + i)
        rng = np.random.default_rng(200 + i)
        fills, glows = [], []
        for f in range(F):
            a, b = edge_frame(field, spec, f, F, rng)
            fills.append(a)
            glows.append(b)
        add_sparks(fills, glows, field, spec['sparks'], F, rng)
        tinted('hand/edge_' + name, fills, glows, HAND_TEX, {
            'fps': 8, 'loop': True, 'offset': [-HAND_M * HAND_TEX, -HAND_M * HAND_TEX],
            'about': ('Hover/hot frame: cross-fade over the rarity edge in 120 ms.' if name == 'hover'
                      else '%s burning edge, 4-frame flicker at 8 fps, always on in hand mode.' % name.capitalize()),
        })


def build_embers():
    """Embers: 6 frames, 1.2 s loop (5 fps), each rises 12 px (3 texels) and fades."""
    for j, (name, count) in enumerate((('sparse', 9), ('dense', 20))):
        F = 6
        w, h = HAND_FRAME
        field = EdgeField(hand_geom(), 3.0, 300 + j)
        rng = np.random.default_rng(310 + j)
        fills = [np.zeros((h, w)) for _ in range(F)]
        glows = [np.zeros((h, w)) for _ in range(F)]
        d = field.d
        ys, xs = np.nonzero((d >= 0.5) & (d <= 2.5) & (np.arange(h)[:, None] < HAND_M + HAND_CARD[1] * 0.75))
        for k in range(count):
            i = int(rng.integers(0, len(xs)))
            x, y = int(xs[i]), int(ys[i])
            birth = (k * F) // count + int(rng.integers(0, 2))
            sway = int(rng.choice([-1, 1]))
            for age in range(F):
                fr = (birth + age) % F
                yy = y - (age * 3) // (F - 1)              # 0 .. 3 texels = 12 px
                xx = x + (sway if age in (2, 3) else 0)
                if not (0 <= xx < w and 0 <= yy < h):
                    continue
                a = (1.0, 1.0, 1.0, 0.7, 0.4, 0.0)[age]
                if a <= 0:
                    continue
                fills[fr][yy, xx] = max(fills[fr][yy, xx], a)
                if age <= 1:
                    glows[fr][yy, xx] = GLOW_LIGHT
        tinted('hand/embers_' + name, fills, glows, HAND_TEX, {
            'fps': 5, 'loop': True, 'offset': [-HAND_M * HAND_TEX, -HAND_M * HAND_TEX],
            'about': 'Embers rising 12 px and fading, 6 frames over 1.2 s. %s: %s.' % (
                name, 'Common/Rare' if name == 'sparse' else 'Epic/Cursed/hover'),
        })


def build_flare():
    """Kept card flares: 4 frames x 50 ms = 200 ms, then the card flies to the hand."""
    F = 4
    specs = [
        dict(EDGE_SPECS['hover'], white=0.55, hot=0.9, out_max=3.0, amp=0.1),
        dict(EDGE_SPECS['hover'], white=0.30, hot=0.7, out_max=6.0, amp=0.2),
        dict(EDGE_SPECS['hover'], white=0.12, hot=0.45, out_max=4.6),
        dict(EDGE_SPECS['epic']),
    ]
    field = EdgeField(hand_geom(), 3.0, 400)
    rng = np.random.default_rng(401)
    fills, glows = [], []
    for f, sp in enumerate(specs):
        a, b = edge_frame(field, sp, f, F, rng)
        fills.append(a)
        glows.append(b)
    # Outward burst of sparks on frames 1-3.
    cx, cy = HAND_FRAME[0] / 2, HAND_FRAME[1] / 2
    d = field.d
    ys, xs = np.nonzero((d > 0.5) & (d < 2.5))
    for k in range(40):
        i = int(rng.integers(0, len(xs)))
        x, y = xs[i], ys[i]
        vx, vy = x + 0.5 - cx, y + 0.5 - cy
        n = math.hypot(vx, vy)
        vx, vy = vx / n, vy / n
        for f in (1, 2, 3):
            dist = (f) * (1.4 + rng.random())
            xx, yy = int(round(x + vx * dist)), int(round(y + vy * dist))
            if 0 <= xx < HAND_FRAME[0] and 0 <= yy < HAND_FRAME[1]:
                fills[f][yy, xx] = 1.0 if f < 3 else TONE_DARK
                if f == 1:
                    glows[f][yy, xx] = GLOW_WHITE
    tinted('hand/flare', fills, glows, HAND_TEX, {
        'fps': 20, 'loop': False, 'offset': [-HAND_M * HAND_TEX, -HAND_M * HAND_TEX],
        'about': 'Pick: the kept card flares, 4 frames x 50 ms = 200 ms. Pair with a radial-gradient halo.',
    })


# Burn-away ---------------------------------------------------------------------------------------
def build_burn():
    """The other two burn away: 8 frames x 75 ms = 0.6 s, eaten from the edges inward."""
    F = 8
    cw, ch = HAND_CARD
    w, h = HAND_FRAME
    rng = np.random.default_rng(500)
    dc = rrect_sdf(cw, ch, 0, 0, cw, ch, HAND_R)                # card-local
    nz = value_noise(ch, cw, 4.0, rng)
    nz2 = value_noise(ch, cw, 1.5, rng)
    T = -dc + 9.0 * nz + 3.0 * nz2                           # burn time, texels of depth
    T -= T.min()
    tmax = T.max() + 0.01
    prog = [(f + 1) / F * (tmax + 2.5) - 1.0 for f in range(F)]
    masks, fills, glows, ash = [], [], [], []
    ash_parts = []
    prev_p = -10.0
    masks.append(np.ones((ch, cw)))                         # frame 0: intact, so the mask can sit ready
    for f in range(F):
        p = prog[f]
        alive = T >= p
        masks.append(np.where(alive, 1.0, 0.0))
        fill = np.zeros((h, w))
        glow = np.zeros((h, w))
        loc = np.zeros((ch, cw))
        loc_g = np.zeros((ch, cw))
        front = alive & (T < p + 1.6)
        char = alive & (T >= p + 1.6) & (T < p + 2.6)
        lick = (~alive) & (T >= p - 1.2) & (rng.random(T.shape) < 0.55)
        loc[front] = 1.0
        loc[char] = TONE_DARK
        loc[lick] = np.maximum(loc[lick], np.where(rng.random(lick.sum()) < 0.5, 1.0, TONE_DARK))
        hotsel = front & (rng.random(T.shape) < 0.45)
        loc_g[hotsel] = GLOW_LIGHT
        loc_g[front & (T < p + 0.6) & (rng.random(T.shape) < 0.3)] = GLOW_WHITE
        if f == F - 1:
            # Last frame: only a few dying embers remain.
            keep = rng.random(T.shape) < 0.04
            loc = np.where(keep & (T > tmax - 6), TONE_DARK, 0.0)
            loc_g[:] = 0
        fill[HAND_M:HAND_M + ch, HAND_M:HAND_M + cw] = loc
        glow[HAND_M:HAND_M + ch, HAND_M:HAND_M + cw] = loc_g
        fills.append(fill)
        glows.append(glow)
        # Ash: spawn where texels just burnt, then drift up 1 texel per frame and fade.
        just = (T >= prev_p) & (T < p)
        ys, xs = np.nonzero(just & (rng.random(T.shape) < 0.022))
        for x, y in zip(xs, ys):
            ash_parts.append((f, x + HAND_M, y + HAND_M, int(rng.choice([-1, 0, 0, 1])), int(rng.integers(0, 3))))
        prev_p = p
    ash_cols = [(0xB8, 0xB2, 0xAC), (0x8A, 0x85, 0x80), (0x5E, 0x5A, 0x57)]
    for f in range(F):
        img = rgba_canvas(h, w)
        for (b, x, y, dx, c) in ash_parts:
            age = f - b
            if age < 0 or age > 4:
                continue
            xx = x + (dx if age >= 2 else 0) + (dx if age >= 4 else 0)
            yy = y - age
            a = (255, 230, 180, 120, 60)[age]
            put(img, xx, yy, ash_cols[min(2, c + age // 2)], a)
        ash.append(img)
    register('hand/burn_mask', write_asset('hand/burn_mask', [white_alpha(m) for m in masks], HAND_TEX, {
        'kind': 'mask', 'fps': 13.333, 'frameMs': 75, 'loop': False, 'offset': [0, 0],
        'about': 'Card-sized mask (248 x 328) for the card face (-webkit-mask-image); alpha = what is left. '
                 'Frame 0 is the intact card, frames 1-8 burn (75 ms each); burn_front/burn_ash frame k '
                 'matches burn_mask frame k+1.',
    }))
    tinted('hand/burn_front', fills, glows, HAND_TEX, {
        'fps': 13.333, 'frameMs': 75, 'loop': False, 'offset': [-HAND_M * HAND_TEX, -HAND_M * HAND_TEX],
        'about': 'The burning front that eats the card, same timing as burn_mask. Tint with the skill colour.',
    })
    register('hand/burn_ash', write_asset('hand/burn_ash', ash, HAND_TEX, {
        'kind': 'colour', 'fps': 13.333, 'frameMs': 75, 'loop': False,
        'offset': [-HAND_M * HAND_TEX, -HAND_M * HAND_TEX],
        'about': 'Grey ash pixels drifting up while the card burns. Plain background-image.',
    }))


# Card back ---------------------------------------------------------------------------------------
BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0
GOLD = (243, 210, 139)
CREAM = (255, 246, 222)
WHITE = (255, 255, 255)
DGOLD = (106, 85, 48)
MGOLD = (160, 128, 74)
PINK = (255, 155, 234)
CYAN = (143, 240, 255)


def card_back_frame():
    w, h = HAND_FRAME
    img = rgba_canvas(h, w)
    d = rrect_sdf(w, h, HAND_M, HAND_M, HAND_CARD[0], HAND_CARD[1], HAND_R)
    top, bot = np.array([34, 27, 58]), np.array([13, 10, 22])
    for y in range(h):
        for x in range(w):
            dd = d[y, x]
            if dd <= 0:
                t = (y - HAND_M) / (HAND_CARD[1] - 1)
                t = min(1, max(0, t + (BAYER4[y % 4, x % 4] - 0.5) * 0.18))
                t = round(t * 6) / 6
                c = top + (bot - top) * t
                img[y, x, :3] = c.astype(int)
                img[y, x, 3] = 255
                if dd > -1:
                    img[y, x, :3] = CREAM if (x + y) % 9 else WHITE
                elif dd > -2:
                    img[y, x, :3] = GOLD
            else:
                # Baked stepped halo, white-gold.
                for lim, a in ((1, 110), (2, 70), (3, 44), (4.5, 24), (6.5, 10)):
                    if dd <= lim:
                        img[y, x, :3] = GOLD
                        img[y, x, 3] = a
                        break
    # Inner frame line, 4 texels in.
    ix0, iy0 = HAND_M + 4, HAND_M + 4
    ix1, iy1 = HAND_M + HAND_CARD[0] - 5, HAND_M + HAND_CARD[1] - 5
    for x in range(ix0, ix1 + 1):
        img[iy0, x, :3] = MGOLD
        img[iy1, x, :3] = MGOLD
    for y in range(iy0, iy1 + 1):
        img[y, ix0, :3] = MGOLD
        img[y, ix1, :3] = MGOLD
    # Centre star.
    cx, cy = HAND_M + HAND_CARD[0] // 2, HAND_M + HAND_CARD[1] // 2
    for k in range(-17, 18):
        img[cy, cx + k, :3] = MGOLD
    for k in range(-21, 22):
        img[cy + k, cx, :3] = MGOLD
    for y in range(cy - 12, cy + 13):
        for x in range(cx - 10, cx + 11):
            m = abs(x - cx) + abs(y - cy) * 0.78
            if m <= 8.6:
                img[y, x, :3] = (214, 176, 104) if m > 7.6 else GOLD
            if m <= 4.2:
                img[y, x, :3] = CREAM
            if m <= 2.0:
                img[y, x, :3] = WHITE
    return img


def build_back():
    register('hand/back', write_asset('hand/back', [card_back_frame()], HAND_TEX, {
        'kind': 'colour', 'frames': 1, 'offset': [-HAND_M * HAND_TEX, -HAND_M * HAND_TEX],
        'about': 'Face-down card (Mythic reveal, deal-in): navy body, white-gold frame, centre star, halo baked.',
    }))


# Mythic ------------------------------------------------------------------------------------------
def build_mythic_edge():
    """White-gold fire edge, 6 frames at 10 fps: steady (never flickers), only the tips move."""
    F = 6
    field = EdgeField(hand_geom(), 3.0, 600)
    rng = np.random.default_rng(601)
    w, h = HAND_FRAME
    d = field.d
    frames = []
    tipcol = value_noise(h, w, 4.0, np.random.default_rng(602))
    for f in range(F):
        ph = 2 * math.pi * f / F
        Ff = np.clip(field.A * 0.8 + 0.2 * (0.5 + 0.5 * np.cos(ph + 4 * math.pi * field.P)), 0, 1)
        flame = 1.6 + 4.0 * Ff ** 1.2
        inner_char = (field.G > 0.55) & (d <= -2.5) & (d > -3.5)
        img = rgba_canvas(h, w)
        r = rng.random(d.shape)
        for y in range(h):
            for x in range(w):
                dd = d[y, x]
                col = None
                if -3.5 < dd <= 0:
                    if dd > -1:
                        col = WHITE
                    elif dd > -2:
                        col = WHITE if r[y, x] < 0.45 else CREAM
                    elif dd > -3:
                        col = GOLD if r[y, x] > 0.15 else CREAM
                    elif inner_char[y, x]:
                        col = DGOLD
                elif 0 < dd <= flame[y, x]:
                    tip = dd > flame[y, x] - 1.0 and dd > 1.0
                    if dd <= 1:
                        col = WHITE
                    elif tip:
                        col = PINK if tipcol[y, x] > 0.62 else (CYAN if tipcol[y, x] < 0.38 else GOLD)
                    elif dd <= 2:
                        col = CREAM
                    else:
                        col = GOLD
                if col is not None:
                    img[y, x, :3] = col
                    img[y, x, 3] = 255
        frames.append(img)
    # Sparks: white / cyan / pink single texels, rising.
    ys, xs = np.nonzero((d >= 2.5) & (d <= 6.5))
    for k in range(26):
        i = int(rng.integers(0, len(xs)))
        x, y = int(xs[i]), int(ys[i])
        b = int(rng.integers(0, F))
        c = (WHITE, CYAN, PINK, CREAM)[k % 4]
        for age in range(3):
            fr = (b + age) % F
            put(frames[fr], x, y - age, c, (255, 220, 140)[age])
    register('mythic/edge', write_asset('mythic/edge', frames, HAND_TEX, {
        'kind': 'colour', 'fps': 10, 'loop': True, 'offset': [-HAND_M * HAND_TEX, -HAND_M * HAND_TEX],
        'about': 'Mythic fire edge: white base, gold body, cyan/pink tips. Its own colours, never tinted.',
    }))


def build_mythic_rays():
    """One soft frame, 512 CSS px; the page rotates it 6 deg/s (and a second copy counter-rotates)."""
    rng = np.random.default_rng(700)
    beams = []
    n = 22
    for i in range(n):
        ang = (i + rng.random() * 0.6) / n * 2 * math.pi
        width = math.radians(1.2 + rng.random() * 3.8)
        length = 0.55 + rng.random() * 0.45
        alpha = 0.28 + rng.random() * 0.35
        beams.append((ang, width, length, alpha))
    files = {}
    entry = None
    for s in (1, 2):
        size = 512 * s
        ys, xs = np.mgrid[0:size, 0:size].astype(float)
        cx = cy = size / 2
        dx, dy = xs + 0.5 - cx, ys + 0.5 - cy
        rr = np.hypot(dx, dy) / (size / 2)
        th = np.arctan2(dy, dx)
        a = np.zeros((size, size))
        for ang, wd, ln, al in beams:
            dth = np.abs((th - ang + math.pi) % (2 * math.pi) - math.pi)
            prof = np.clip(1 - dth / wd, 0, 1)
            radial = np.clip(1 - rr / ln, 0, 1) ** 1.3 * np.clip((rr - 0.06) / 0.12, 0, 1)
            a = np.maximum(a, al * prof * radial)
        a = np.round(a * 48) / 48                                     # 48 alpha steps -> palette PNG
        img = np.zeros((size, size, 4), np.uint8)
        img[..., 0], img[..., 1], img[..., 2] = 255, 244, 214
        img[..., 3] = np.round(a * 255).astype(np.uint8)
        img[img[..., 3] == 0] = 0
        p = os.path.join(OUT, 'mythic', 'rays_x%d.png' % s)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        to_png(img, p)
        files['x%d' % s] = 'mythic/rays_x%d.png' % s
    register('mythic/rays', {
        'kind': 'colour', 'frames': 1, 'frame': [512, 512], 'strip': [512, 512], 'texel': None,
        'files': files, 'decodedBytes': {'x1': 512 * 512 * 4, 'x2': 1024 * 1024 * 4},
        'about': 'Soft light rays (not pixel art: it is rotated, so it is smooth by design). Centre it on the '
                 'card, transform: rotate() 6 deg/s; a second copy at 70 % opacity counter-rotates. x1/x2 only.',
    })


def mote_frames(col):
    """One mote's twinkle, 12 frames: dot, plus, bright core, fade. 5 x 5 texels (10 px)."""
    F = 12
    seq = [0, 1, 2, 3, 3, 4, 3, 3, 2, 1, 1, 0]
    alpha = [120, 200, 255, 255, 255, 255, 255, 230, 200, 160, 110, 70]
    frames = []
    for f in range(F):
        img = rgba_canvas(5, 5)
        st, a = seq[f], alpha[f]
        core = WHITE if st >= 3 else col
        put(img, 2, 2, core, a)
        if st >= 2:
            for (x, y) in ((1, 2), (3, 2), (2, 1), (2, 3)):
                put(img, x, y, col, int(a * 0.8))
        if st >= 4:
            for (x, y) in ((0, 2), (4, 2), (2, 0), (2, 4)):
                put(img, x, y, col, int(a * 0.45))
        if st == 1:
            for (x, y) in ((1, 2), (3, 2), (2, 1), (2, 3)):
                put(img, x, y, col, int(a * 0.3))
        frames.append(img)
    return frames


def build_mythic_motes():
    for name, col in (('white', CREAM), ('gold', GOLD), ('cyan', CYAN), ('pink', PINK)):
        register('mythic/mote_' + name, write_asset('mythic/mote_' + name, mote_frames(col), 2, {
            'kind': 'colour', 'fps': 10, 'loop': True,
            'about': 'One orbiting mote, 12 frames at 10 fps (10 x 10 px). Scatter ~24 of them around the '
                     'card with different animation-delay and a slow CSS translate drift.',
        }))


def build_mythic_foil():
    """Foil sheen: 16 frames sliding across the card (clip to the art panel), every 4 s."""
    F = 16
    cw, ch = HAND_CARD
    frames = []
    for f in range(F):
        img = rgba_canvas(ch, cw)
        pos = -30 + f * (cw + ch + 30) / (F - 1)            # diagonal x + y = pos
        for y in range(ch):
            for x in range(cw):
                t = (x + y * 0.75) - pos
                if -7 <= t <= 7:
                    k = abs(t)
                    if k < 1.5:
                        put(img, x, y, WHITE, 120)
                    elif k < 3.5:
                        put(img, x, y, CYAN if t < 0 else PINK, 70)
                    elif k < 5.5:
                        put(img, x, y, CREAM, 38)
                    elif (x + y) % 2 == 0:
                        put(img, x, y, CREAM, 20)
        frames.append(img)
    register('mythic/foil', write_asset('mythic/foil', frames, HAND_TEX, {
        'kind': 'colour', 'fps': 10, 'loop': False, 'repeatEvery': 4.0,
        'about': 'Foil sheen over the art, card-sized (248 x 328): 16 frames at 10 fps, once every 4 s. '
                 'overflow:hidden on the art panel clips it.',
    }))


def build_mythic_crack():
    """Cracks of light across the card back, 8 frames (0.6-1.4 s of the reveal). Back-frame sized."""
    F = 8
    w, h = HAND_FRAME
    rng = np.random.default_rng(800)
    cx, cy = HAND_M + HAND_CARD[0] // 2, HAND_M + HAND_CARD[1] // 2
    paths = []
    for i in range(9):
        ang = (i + rng.random() * 0.5) / 9 * 2 * math.pi
        x, y = float(cx), float(cy)
        pts = []
        L = int(16 + rng.random() * 18)
        for s in range(L):
            ang += (rng.random() - 0.5) * 0.7
            x += math.cos(ang)
            y += math.sin(ang)
            pts.append((int(round(x)), int(round(y))))
        paths.append(pts)
    frames = []
    for f in range(F):
        img = rgba_canvas(h, w)
        frac = (f + 1) / F
        for pts in paths:
            n = int(len(pts) * frac)
            for j, (x, y) in enumerate(pts[:n]):
                if HAND_M + 1 <= x < HAND_M + HAND_CARD[0] - 1 and HAND_M + 1 <= y < HAND_M + HAND_CARD[1] - 1:
                    put(img, x + 1, y + 1, DGOLD, 200)
                    put(img, x, y, WHITE if j > n - 4 or f >= 5 else CREAM, 255)
        # Core brightens.
        rad = 1 + f * 0.4
        for y in range(int(cy - rad - 1), int(cy + rad + 2)):
            for x in range(int(cx - rad - 1), int(cx + rad + 2)):
                if abs(x - cx) + abs(y - cy) <= rad:
                    put(img, x, y, WHITE, 255)
        # Light leaking at the border late on.
        if f >= 5:
            for k in range(6 * (f - 4)):
                side = rng.integers(0, 4)
                if side < 2:
                    x = int(rng.integers(HAND_M, HAND_M + HAND_CARD[0]))
                    y = HAND_M - 1 if side == 0 else HAND_M + HAND_CARD[1]
                else:
                    y = int(rng.integers(HAND_M, HAND_M + HAND_CARD[1]))
                    x = HAND_M - 1 if side == 2 else HAND_M + HAND_CARD[0]
                put(img, x, y, CREAM, 200)
        frames.append(img)
    register('mythic/crack', write_asset('mythic/crack', frames, HAND_TEX, {
        'kind': 'colour', 'fps': 10, 'loop': False, 'offset': [-HAND_M * HAND_TEX, -HAND_M * HAND_TEX],
        'about': 'White-gold cracks spreading over hand/back, 8 frames at 10 fps. Same frame as hand/back.',
    }))


def build_mythic_burst():
    """Flip into a burst: white flash, shockwave ring and pixel sparks. 10 frames, 448 px square."""
    F = 10
    S = 112
    c = S / 2
    rng = np.random.default_rng(900)
    parts = []
    for k in range(70):
        ang = rng.random() * 2 * math.pi
        sp = 2.2 + rng.random() * 3.6
        col = (WHITE, CREAM, GOLD, CYAN, PINK)[int(rng.integers(0, 5))]
        parts.append((ang, sp, col, 3 + rng.random() * 4))
    frames = []
    for f in range(F):
        img = rgba_canvas(S, S)
        ys, xs = np.mgrid[0:S, 0:S]
        rr = np.hypot(xs + 0.5 - c, ys + 0.5 - c)
        # Flash disc on the first frames.
        if f <= 2:
            rad = (22, 15, 8)[f]
            m = rr <= rad
            img[m, :3] = WHITE
            img[m, 3] = (255, 210, 140)[f]
            m2 = (rr > rad) & (rr <= rad + 2)
            img[m2, :3] = CREAM
            img[m2, 3] = (170, 120, 70)[f]
        # Shockwave ring.
        R = 8 + f * 5.2
        a = int(255 * (1 - f / F) ** 0.8)
        ring = np.abs(rr - R) < 1.0
        img[ring, :3] = WHITE if f < 4 else CREAM
        img[ring, 3] = np.maximum(img[ring, 3], a)
        ring2 = (np.abs(rr - (R - 1.6)) < 0.6) & ~ring
        img[ring2, :3] = GOLD
        img[ring2, 3] = np.maximum(img[ring2, 3], a // 2)
        for ang, sp, col, life in parts:
            if f > life + 2:
                continue
            dist = 4 + sp * f
            x = int(round(c + math.cos(ang) * dist))
            y = int(round(c + math.sin(ang) * dist + 0.15 * f * f))
            fade = int(255 * max(0.25, 1 - f / (life + 3)))
            put(img, x, y, col, fade)
            if sp > 4 and f < 6:
                put(img, int(round(c + math.cos(ang) * (dist - sp * 0.5))),
                    int(round(c + math.sin(ang) * (dist - sp * 0.5) + 0.15 * f * f)), col, fade // 2)
        frames.append(img)
    register('mythic/burst', write_asset('mythic/burst', frames, HAND_TEX, {
        'kind': 'colour', 'fps': 10, 'loop': False,
        'about': 'Mythic reveal burst, 10 frames at 10 fps (448 x 448 px), centred on the card at the flip.',
    }))


def build_mythic_ring():
    """Pulse ring, one pixel frame (scale 0.95 -> 1.08 and fade in CSS, 2.4 s)."""
    S = 120
    c = S / 2
    img = rgba_canvas(S, S)
    ys, xs = np.mgrid[0:S, 0:S]
    rr = np.hypot(xs + 0.5 - c, ys + 0.5 - c)
    for (r0, r1, col, a) in ((53, 54, CYAN, 70), (54, 55.2, CREAM, 150), (55.2, 56.4, WHITE, 210),
                             (56.4, 57.6, PINK, 90), (57.6, 59, GOLD, 40)):
        m = (rr >= r0) & (rr < r1)
        img[m, :3] = col
        img[m, 3] = a
    register('mythic/ring', write_asset('mythic/ring', [img], HAND_TEX, {
        'kind': 'colour', 'frames': 1,
        'about': 'Prismatic pulse ring (480 px). Centre on the card; transform: scale(.95 -> 1.08) + opacity '
                 'over 2.4 s. A CSS radial-gradient ring is the cheaper alternative.',
    }))


# Set complete ------------------------------------------------------------------------------------
def build_set():
    """Set-complete moment: rays + white sparks (baked) and coloured sparks (tinted), 12 frames, 1.2 s."""
    F = 12
    W, H = 192, 128          # texels at 2 px -> 384 x 256 px
    TEX = 2
    cx, cy = W / 2, H / 2
    rng = np.random.default_rng(1000)
    beams = []
    nb = 30
    for i in range(nb):
        ang = (i + rng.random() * 0.4) / nb * 2 * math.pi
        L = (34 if i % 2 == 0 else 22) + rng.random() * 14
        beams.append((ang, L))
    parts = []
    for k in range(46):
        ang = rng.random() * 2 * math.pi
        sp = 2.0 + rng.random() * 4.0
        parts.append((ang, sp, int(rng.integers(0, 3)), int(rng.integers(0, 3))))
    rays, sparks = [], []
    for f in range(F):
        img = rgba_canvas(H, W)
        sp_a = np.zeros((H, W))
        grow = min(1.0, (f + 1) / 4)
        fade = 1.0 if f < 8 else (F - f) / 5
        for ang, L in beams:
            r1 = 12 + L * grow
            r = 12.0
            while r < r1:
                x = int(round(cx + math.cos(ang) * r))
                y = int(round(cy + math.sin(ang) * r * 0.62))
                t = (r - 12) / max(1, L)
                a = int(255 * fade * (0.75 if t < 0.35 else 0.5 if t < 0.65 else 0.28))
                put(img, x, y, (232, 226, 214), a)
                r += 0.7
        for ang, spd, kind, delay in parts:
            age = f - delay
            if age < 0 or age > 9:
                continue
            dist = 14 + spd * age * 1.4
            x = int(round(cx + math.cos(ang) * dist))
            y = int(round(cy + math.sin(ang) * dist * 0.62 + 0.2 * age * age))
            a = 1.0 if age < 6 else (10 - age) / 4.0
            if kind == 0:
                for (ox, oy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    put(img, x + ox, y + oy, WHITE, int(255 * a))
            else:
                for (ox, oy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    if 0 <= x + ox < W and 0 <= y + oy < H:
                        sp_a[y + oy, x + ox] = max(sp_a[y + oy, x + ox], round(a * 4) / 4)
        rays.append(img)
        sparks.append(sp_a)
    register('set/rays', write_asset('set/rays', rays, TEX, {
        'kind': 'colour', 'fps': 10, 'loop': False,
        'about': 'Set complete: rays + white pixel sparks, 12 frames at 10 fps (1.2 s), 384 x 256 px, '
                 'centred on the keyword glyph. Bloom behind it is a CSS radial-gradient.',
    }))
    f = write_asset('set/sparks_fill', [white_alpha(a) for a in sparks], TEX, {})
    register('set/sparks', {
        'kind': 'mask', 'frames': F, 'frame': f['frame'], 'strip': f['strip'], 'texel': TEX, 'fps': 10,
        'loop': False, 'files': f['files'], 'decodedBytes': f['decodedBytes'],
        'about': 'Coloured sparks for the set moment (mask; tint with the skill colours of the set, e.g. two '
                 'layers, one mirrored with transform: scaleX(-1)). Same frame/timing as set/rays.',
    })


# Collection hover --------------------------------------------------------------------------------
COLL_SPECS = {'common': 0.10, 'rare': 0.18, 'epic': 0.28, 'cursed': 0.30, 'wildcard': 0.20}


def build_collection():
    w, h = COLL_FRAME
    d = rrect_sdf(w, h, COLL_M, COLL_M, COLL_CARD[0], COLL_CARD[1], COLL_R)
    for i, (name, dens) in enumerate(COLL_SPECS.items()):
        rng = np.random.default_rng(1100 + i)
        nz = value_noise(h, w, 2.0, rng)
        fills, glows = [], []
        for f in range(2):
            r = rng.random(d.shape)
            ring = (np.abs(d) < 1.0)
            fl = np.zeros((h, w))
            gl = np.zeros((h, w))
            sel = ring & ((0.5 * nz + 0.5 * r) > 1 - dens) if f == 0 else ring & ((0.5 * (1 - nz) + 0.5 * r) > 1 - dens)
            fl[sel] = 1.0
            gl[sel & (r > 0.7)] = GLOW_LIGHT
            out = (d >= 1.0) & (d < 3.0) & (r < dens * 0.18)
            fl[out] = np.where(rng.random(out.sum()) < 0.5, 1.0, TONE_DARK)
            fills.append(fl)
            glows.append(gl)
        tinted('collection/hover_' + name, fills, glows, COLL_TEX, {
            'fps': 6, 'loop': True, 'offset': [-COLL_M * COLL_TEX, -COLL_M * COLL_TEX],
            'about': 'Collection card hover: 2-frame edge flicker at 6 fps while hovered (none at rest). '
                     '96 x 128 card, frame 112 x 144 at -8 px.',
        })


# Icons -------------------------------------------------------------------------------------------
GEM_ORDER = ['common', 'rare', 'epic', 'cursed', 'mythic', 'earned']


def gem(col, mask=False):
    img = rgba_canvas(7, 7)
    c = np.array(col)
    for y in range(7):
        for x in range(7):
            m = abs(x - 3) + abs(y - 3)
            if m > 3:
                continue
            if mask:
                img[y, x] = (255, 255, 255, 255)
                continue
            if m == 3:
                rgb = c * 0.45
            elif (x - 3) + (y - 3) < 0:
                rgb = c + (255 - c) * 0.35
            elif (x - 3) + (y - 3) > 0:
                rgb = c * 0.78
            else:
                rgb = c
            img[y, x, :3] = np.clip(rgb, 0, 255).astype(int)
            img[y, x, 3] = 255
    if not mask:
        img[2, 2, :3] = (255, 255, 255)
    return img


def dot(col, mask=False):
    img = rgba_canvas(3, 3)
    c = np.array(col)
    img[:, :, 3] = 255
    if mask:
        img[:, :, :3] = 255
        return img
    img[:, :, :3] = c.astype(int)
    img[0, 0, :3] = np.clip(c + (255 - c) * 0.4, 0, 255).astype(int)
    img[2, 2, :3] = (c * 0.75).astype(int)
    return img


def build_icons():
    gems = [gem(hexrgb(RARITY_COLOURS[k])) for k in GEM_ORDER]
    register('icons/gems', write_asset('icons/gems', gems, 2, {
        'kind': 'sheet', 'order': GEM_ORDER,
        'about': 'Rarity gems, 14 px, one per frame in "order". background-position: -14px * index (x1).',
    }))
    register('icons/gem_mask', write_asset('icons/gem_mask', [gem((0, 0, 0), True)], 2, {
        'kind': 'mask', 'about': 'Gem silhouette for tinting (wildcard, or any colour).',
    }))
    dots = [dot(hexrgb(RARITY_COLOURS[k])) for k in GEM_ORDER]
    register('icons/trait_dots', write_asset('icons/trait_dots', dots, 2, {
        'kind': 'sheet', 'order': GEM_ORDER,
        'about': 'Trait dot, 6 px, rarity colour per frame in "order"; repeat per trait tier.',
    }))
    register('icons/trait_dot_mask', write_asset('icons/trait_dot_mask', [dot((0, 0, 0), True)], 2, {
        'kind': 'mask', 'about': 'Trait dot silhouette for tinting.',
    }))
    order = sorted(GLYPHS)
    gcol = hexrgb(SPECIAL_COLOURS['glyph'])
    gl, gm = [], []
    for k in order:
        img = rgba_canvas(8, 8)
        msk = rgba_canvas(8, 8)
        for y, row in enumerate(GLYPHS[k]):
            for x, ch in enumerate(row):
                if ch == '#':
                    img[y, x] = gcol + (255,)
                    msk[y, x] = (255, 255, 255, 255)
        gl.append(img)
        gm.append(msk)
    e = write_asset('icons/glyphs', gl, 2, {
        'kind': 'sheet', 'order': order,
        'about': 'Keyword glyphs, 16 px (8 x 8 texels), neutral #D8CDBB, one per frame in "order". '
                 'x5 (80 px) is for the set-complete moment.',
    }, scales=(1, 2, 3, 5))
    register('icons/glyphs', e)
    register('icons/glyph_mask', write_asset('icons/glyph_mask', gm, 2, {
        'kind': 'mask', 'order': order,
        'about': 'Keyword glyph silhouettes (white) to tint through -webkit-mask-image.',
    }, scales=(1, 2, 3, 5)))


# Optional fallback: bake tinted copies ----------------------------------------------------------
def bake(dest):
    os.makedirs(dest, exist_ok=True)
    cols = dict(SKILL_COLOURS)
    cols.update({k: SPECIAL_COLOURS[k] for k in ('cursed', 'earned')})
    n = 0
    for key, e in MANIFEST.items():
        if e.get('kind') != 'tinted':
            continue
        fill = e['layers']['fill']['files']
        glow = e['layers']['glow']['files']
        for s in ('x1', 'x2', 'x3'):
            fa = np.asarray(Image.open(os.path.join(OUT, fill[s])).convert('RGBA')).astype(float)
            ga = np.asarray(Image.open(os.path.join(OUT, glow[s])).convert('RGBA')).astype(float)
            for name, hx in cols.items():
                c = np.array(hexrgb(hx), float)
                a = fa[..., 3:4] / 255
                g = ga[..., 3:4] / 255
                rgb = c * (1 - g) + 255 * g
                out = np.concatenate([rgb, np.maximum(a, g) * 255], axis=2)
                p = os.path.join(dest, key.replace('/', '_') + '_' + name + '_' + s + '.png')
                to_png(np.round(out).astype(np.uint8), p)
                n += 1
    print('baked', n, 'files into', dest)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--bake', action='store_true', help='also bake per-colour copies into hub/build/cards-baked')
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    build_hand_edges()
    build_embers()
    build_flare()
    build_burn()
    build_back()
    build_mythic_edge()
    build_mythic_rays()
    build_mythic_motes()
    build_mythic_foil()
    build_mythic_crack()
    build_mythic_burst()
    build_mythic_ring()
    build_set()
    build_collection()
    build_icons()
    manifest = {
        'about': 'VOID Mettle card textures. Generated by hub/tools/cards/gen_cards.py; see README.md.',
        'figma': {'file': '3c0Wgc4ZKFSpm98fWJoARS', 'page': '295:2', 'sections': ['546:23595', '510:18631', '539:23542']},
        'naming': '<key>_px.png is native texels; <key>_x1.png is the exact CSS display size; _x2/_x3 are '
                  'nearest-neighbour copies for device scales above 1 (and _x5 for glyphs).',
        'grid': {'hand': {'texel': HAND_TEX, 'card': [HAND_CARD[0] * HAND_TEX, HAND_CARD[1] * HAND_TEX],
                          'frame': [HAND_FRAME[0] * HAND_TEX, HAND_FRAME[1] * HAND_TEX],
                          'offset': [-HAND_M * HAND_TEX, -HAND_M * HAND_TEX], 'burnCapPx': 12, 'safeAreaPx': 28},
                 'collection': {'texel': COLL_TEX, 'card': [COLL_CARD[0] * COLL_TEX, COLL_CARD[1] * COLL_TEX],
                                'frame': [COLL_FRAME[0] * COLL_TEX, COLL_FRAME[1] * COLL_TEX],
                                'offset': [-COLL_M * COLL_TEX, -COLL_M * COLL_TEX], 'safeAreaPx': 10}},
        'tones': {'fillDark': TONE_DARK, 'glowLight': GLOW_LIGHT, 'glowWhite': GLOW_WHITE},
        'colours': {'skills': SKILL_COLOURS, 'special': SPECIAL_COLOURS, 'rarity': RARITY_COLOURS},
        'edgeForRarity': {'common': 'hand/edge_common', 'rare': 'hand/edge_rare', 'epic': 'hand/edge_epic',
                          'cursed': 'hand/edge_cursed', 'wildcard': 'hand/edge_wildcard',
                          'hot': 'hand/edge_hover', 'mythic': 'mythic/edge'},
        'assets': MANIFEST,
    }
    with open(os.path.join(OUT, 'manifest.json'), 'w', newline='\n') as fh:
        json.dump(manifest, fh, indent=1)
        fh.write('\n')
    total = 0
    for root, _, files in os.walk(OUT):
        for fn in files:
            if fn.endswith('.png'):
                total += os.path.getsize(os.path.join(root, fn))
    print('assets:', len(MANIFEST), 'png bytes:', total)
    if args.bake:
        bake(os.path.join(HUB, 'build', 'cards-baked'))
    sys.path.insert(0, HERE)
    import preview
    preview.write(OUT, manifest)


if __name__ == '__main__':
    main()
