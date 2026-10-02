"""Textures for the warm and dry biomes (registry/WarmBlocks.java): the olive, eucalyptus, teak and kapok wood sets,
the flame tree's leaves, red earth and coral sand, and the plants (lavender, Sturt's desert pea, moth orchid, desert
marigold, spinifex, saguaro, ocotillo).

Built with the same helpers as the other wood sets (wood.log_top / stripped_side / planks, leaves.Canvas and its
stamps, doors.Grid and render), each texture from its own seeded RNG, so the output is deterministic.
"""
import math

import numpy as np

import doors
import leaves as lv
import wood
from texlib import blank, from_ascii, lerp, paint, pnoise, put, quantize

# --------------------------------------------------------------------------
# wood palettes (dark -> light); bark index 0 is the deepest crevice
# --------------------------------------------------------------------------
WOODS = {
    # olive: grey-brown bark, deeply furrowed and twisted; cream-yellow wood with dark swirling figure
    'olive': dict(
        bark=['#332d25', '#544b3f', '#655b4e', '#786d5f', '#8c8172', '#a39887'],
        planks=['#8c774b', '#b19a66', '#c4ad78', '#d4be8a', '#e1cd9e'],
        stripped=['#b8a575', '#c5b383', '#d1c092', '#dccba1', '#e6d7b1'],
        rings=['#b09a68', '#c3ae7b', '#d2bf8e', '#dfcda1'],
        figure='#5a4527',
    ),
    # ghost gum: chalk-white bark, mottled cream, grey and salmon where it sheds; red-brown timber
    'eucalyptus': dict(
        bark=['#8a847c', '#c8c3ba', '#d7d3cb', '#e4e1da', '#eeece6', '#f8f7f3'],
        planks=['#6e3527', '#8b4534', '#9b503c', '#ac5d47', '#ba6b53'],
        stripped=['#c79882', '#d1a58f', '#dab29c', '#e2bea9', '#e9c9b5'],
        rings=['#a0543e', '#b2634c', '#c2725b', '#ce826a'],
        patches=['#e3d4ba', '#d9b5a0', '#b9b4aa'],
    ),
    # teak: brownish-grey bark in long shallow fibrous strips; golden honey-brown wood with dark grain
    'teak': dict(
        bark=['#3a3128', '#584c40', '#685b4d', '#786b5c', '#887b6b', '#998c7c'],
        planks=['#7a5024', '#9a6932', '#ac793d', '#be8a4a', '#cc9a5a'],
        stripped=['#b8894f', '#c3955c', '#cda26a', '#d7af79', '#dfbb88'],
        rings=['#a6753d', '#b8864c', '#c7965c', '#d4a66d'],
    ),
    # kapok: smooth grey-green bark studded with conical prickles; pale blond wood
    'kapok': dict(
        bark=['#555a53', '#7a8077', '#8a9086', '#9aa095', '#aab0a4', '#bcc1b5'],
        planks=['#a6977a', '#c3b593', '#d0c3a2', '#dcd0b1', '#e7dcc1'],
        stripped=['#cdc19f', '#d6cbac', '#dfd5b8', '#e6dec4', '#ede6d0'],
        rings=['#c6b997', '#d3c7a6', '#ddd2b4', '#e7ddc2'],
    ),
}


def _walk(rng, n=16, stay=0.7, amp=None):
    return wood._closed_walk(rng, n, stay, amp)


# --------------------------------------------------------------------------
# bark
# --------------------------------------------------------------------------
def bark_olive(rng, p):
    """Gnarled: deep furrows that twist round the trunk, with a knot or two."""
    f = pnoise(rng, sx=0.7, sy=2.5, angle=0.5) + 0.45 * pnoise(rng, sx=0.8, sy=0.8)
    idx = quantize(f, [0.16, 0.30, 0.32, 0.22]) + 1
    for x0 in (0, 5, 10):
        x0 = (x0 + int(rng.integers(0, 2))) % 16
        walk = _walk(rng, 16, stay=0.35, amp=5)
        for y in range(16):
            x = (x0 + walk[y]) % 16
            idx[y, x] = 0
            idx[y, (x + 1) % 16] = min(idx[y, (x + 1) % 16], 1)
            xl = (x - 1) % 16
            if idx[y, xl] >= 3 and rng.random() < 0.55:
                idx[y, xl] = 5
    # knots: a dark eye with a lit rim
    for _ in range(2):
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        for dx, dy in ((0, 0), (1, 0)):
            idx[(y + dy) % 16, (x + dx) % 16] = 0
        for dx, dy in ((-1, 0), (2, 0), (0, -1), (1, -1)):
            idx[(y + dy) % 16, (x + dx) % 16] = 4
        for dx, dy in ((0, 1), (1, 1)):
            idx[(y + dy) % 16, (x + dx) % 16] = 2
    return paint(idx, p['bark'])


def bark_eucalyptus(rng, p):
    """Smooth and pale, shedding in soft patches of cream, salmon and grey, with fine dark scribbles."""
    f = pnoise(rng, sx=1.6, sy=2.4) + 0.3 * pnoise(rng, sx=0.6, sy=0.6)
    idx = quantize(f, [0.06, 0.22, 0.38, 0.26, 0.08]) + 1
    img = paint(idx, p['bark'])
    patch = pnoise(rng, sx=1.8, sy=1.4)
    tint = pnoise(rng, sx=2.5, sy=2.5)
    for y in range(16):
        for x in range(16):
            if patch[y, x] > 0.9:
                c = p['patches'][0] if tint[y, x] > 0.3 else (p['patches'][1] if tint[y, x] > -0.6 else p['patches'][2])
                put(img, x, y, lerp(tuple(int(v) for v in img[y, x, :3]), c, 0.75))
    # scribbly gum: the wandering tracks of moth larvae, a few dark short squiggles
    for _ in range(2):
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        for i in range(int(rng.integers(4, 7))):
            put(img, x + i, y + (1 if (i // 2) % 2 else 0), p['bark'][0])
    # a few horizontal lenticel scars
    for _ in range(3):
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        for i in range(2):
            put(img, x + i, y, p['bark'][1])
    return img


def bark_teak(rng, p):
    """Long shallow fibrous strips, split by thin straight fissures."""
    f = pnoise(rng, sx=0.4, sy=5.0) + 0.35 * pnoise(rng, sx=0.6, sy=1.2)
    idx = quantize(f, [0.12, 0.28, 0.34, 0.20, 0.06]) + 1
    idx = np.minimum(idx, 5)
    for x0 in (2, 6, 10, 13):
        x0 = (x0 + int(rng.integers(0, 2))) % 16
        walk = _walk(rng, 16, stay=0.85, amp=1)
        y0, length = int(rng.integers(0, 16)), int(rng.integers(8, 15))
        for i in range(length):
            y = (y0 + i) % 16
            x = (x0 + walk[y]) % 16
            idx[y, x] = 0
            if rng.random() < 0.4:
                idx[y, (x + 1) % 16] = max(idx[y, (x + 1) % 16], 4)
    return paint(idx, p['bark'])


def bark_kapok(rng, p):
    """Smooth grey-green with faint vertical streaks and conical prickles, each lit on top and shadowed below."""
    f = pnoise(rng, sx=0.8, sy=4.0) + 0.3 * pnoise(rng, sx=0.7, sy=0.7)
    idx = quantize(f, [0.08, 0.24, 0.36, 0.24, 0.08]) + 1
    idx = np.minimum(idx, 5)
    spots = []
    tries = 0
    while len(spots) < 6 and tries < 300:
        tries += 1
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        if any(min(abs(x - a), 16 - abs(x - a)) + min(abs(y - b), 16 - abs(y - b)) < 5 for a, b in spots):
            continue
        spots.append((x, y))
        idx[(y - 1) % 16, x] = 5          # lit tip
        idx[y, x] = 3
        idx[y, (x + 1) % 16] = 2
        idx[(y + 1) % 16, x] = 1          # its shadow
        idx[(y + 1) % 16, (x + 1) % 16] = 0
    return paint(idx, p['bark'])


BARK = dict(olive=bark_olive, eucalyptus=bark_eucalyptus, teak=bark_teak, kapok=bark_kapok)


def planks(rng, w, p):
    img = wood.planks(rng, w, p)
    if w == 'olive':
        # olive wood's dark swirling figure, a wavy streak or two across each board
        for b in range(4):
            if rng.random() < 0.35:
                continue
            y0 = b * 4 + 1
            x0 = int(rng.integers(0, 16))
            ph = rng.random() * 2 * math.pi
            for i in range(int(rng.integers(5, 10))):
                y = y0 + (1 if math.sin(ph + i * 0.9) > 0.3 else 0)
                put(img, x0 + i, y, lerp(p['planks'][2], p['figure'], 0.55))
    return img


# --------------------------------------------------------------------------
# leaves
# --------------------------------------------------------------------------
LEAF_PAL = {
    'olive': ['#2f3a2a', '#45523b', '#5b6a4d', '#73835f', '#8c9b77', '#a9b694', '#c8d2b6'],
    'eucalyptus': ['#33463d', '#465d52', '#5b7366', '#71897a', '#89a08f', '#a3b7a6', '#c3d1c3'],
    'teak': ['#3b481b', '#526328', '#6b7d34', '#839842', '#9cb052', '#b6c468', '#d1da8c'],
    'kapok': ['#143a18', '#1d4c20', '#28602a', '#347533', '#448a3e', '#58a04b', '#7ab966'],
    'flame_tree': ['#6f1a0c', '#9c2a10', '#c24116', '#dc5b1e', '#ec7a29', '#f69b3b', '#ffc56a'],
}
# narrow willow-like leaves, two-tone: dark above, silver below
OLIVE_LEAF = ["5...",
              ".64.",
              "..53",
              "...3"]
# sickle-shaped hanging gum leaves
GUM_LEAF = [".4.",
            ".53",
            ".53",
            "..3",
            "..2"]
# big broad teak leaves
TEAK_LEAF = [".454.",
             "45654",
             "45543",
             ".443.",
             "..3.."]
# a palmate cluster of kapok leaflets
PALMATE = ["4.5.4",
           ".454.",
           "34643",
           ".343.",
           "..2.."]
# a cluster of curved flame-tree petals
FLAME = [".56.",
         "4565",
         "3454",
         ".23."]


def _layered(rng, pal, front, back=None, step=4, carve=0.18, extra=13, mirror=True):
    cv = lv.Canvas()
    back = back or front
    for (x, y) in lv._grid(rng, step=step, jitter=1):
        lv._stamp(cv, back, x - len(back[0]) // 2, y - len(back) // 2, off=-2, mirror=mirror and rng.random() < 0.5)
    for (x, y) in lv._grid(rng, step=step, jitter=1, ox=step // 2, oy=step // 2)[:extra]:
        lv._stamp(cv, front, x - len(front[0]) // 2, y - len(front) // 2, off=0, mirror=mirror and rng.random() < 0.5)
    lv.carve_holes(cv, rng, carve)
    lv._shadow_under(cv)
    return cv.image(pal)


def leaves_olive(rng):
    img = _layered(rng, LEAF_PAL['olive'], OLIVE_LEAF, lv.LEAF_SMALL, step=4, carve=0.18, extra=14)
    # a few ripe olives, nearly black with a purple sheen
    spots = 0
    for _ in range(60):
        if spots >= 4:
            break
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        if img[y, x, 3] and img[(y + 1) % 16, x, 3]:
            put(img, x, y, '#4a2f4f')
            put(img, x, (y + 1) % 16, '#271a2b')
            spots += 1
    return img


def leaves_eucalyptus(rng):
    return _layered(rng, LEAF_PAL['eucalyptus'], GUM_LEAF, lv.LEAF_SMALL, step=4, carve=0.27, extra=11)


def leaves_teak(rng):
    img = _layered(rng, LEAF_PAL['teak'], TEAK_LEAF, lv.LEAF_BLOB, step=5, carve=0.16, extra=9)
    # the dry season: one or two leaves gone gold and brown
    for _ in range(2):
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        for dx, dy, c in ((0, 0, '#c9a24c'), (1, 0, '#b08a3a'), (0, 1, '#a07a30'), (1, 1, '#c9a24c'), (-1, 0, '#d8b45c')):
            xx, yy = (x + dx) % 16, (y + dy) % 16
            if img[yy, xx, 3]:
                put(img, xx, yy, c)
    return img


def leaves_kapok(rng):
    return _layered(rng, LEAF_PAL['kapok'], PALMATE, lv.LEAF_BLOB, step=4, carve=0.17, extra=13)


def leaves_flame_tree(rng):
    """Scarlet-orange blossom crowded on a few bare dark twigs, hardly a leaf to be seen."""
    cv = lv.Canvas()
    for (x, y) in lv._grid(rng, step=4, jitter=1):
        lv._stamp(cv, FLAME, x - 2, y - 2, off=-2, mirror=rng.random() < 0.5)
    for (x, y) in lv._grid(rng, step=4, jitter=1, ox=2, oy=2)[:12]:
        lv._stamp(cv, FLAME, x - 2, y - 2, off=0, mirror=rng.random() < 0.5)
    lv.carve_holes(cv, rng, 0.2)
    lv._shadow_under(cv)
    img = cv.image(LEAF_PAL['flame_tree'])
    # dark twigs showing through, and a sepal or two of green
    for _ in range(3):
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        for i in range(3):
            if img[(y + i) % 16, (x + i // 2) % 16, 3]:
                put(img, x + i // 2, y + i, '#3a2a22')
    for _ in range(3):
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        if img[y, x, 3]:
            put(img, x, y, '#56743a')
    return img


LEAVES = dict(olive=leaves_olive, eucalyptus=leaves_eucalyptus, teak=leaves_teak, kapok=leaves_kapok)

# --------------------------------------------------------------------------
# saplings
# --------------------------------------------------------------------------
SAPLINGS = {
    'olive': dict(
        pal={'e': '#c8d2b6', 'd': '#a9b694', 'c': '#8c9b77', 'b': '#5b6a4d', 'a': '#45523b', 'o': '#2f2234',
             'T': '#8c8172', 't': '#655b4e', 's': '#433a30'},
        art=[
            "................",
            "................",
            "......d.e.......",
            "....edcbd.dc....",
            "...dc.dbcdcbe...",
            "..ec.bc.T.bcd...",
            "..dcbo..T..obc..",
            "...ba..tT...ba..",
            "......T.Tt......",
            ".......TT.......",
            "......tT........",
            ".......Tt.......",
            "........T.......",
            ".......tT.......",
            ".......Ts.......",
            "......sTts......",
        ]),
    'eucalyptus': dict(
        pal={'e': '#bccabd', 'd': '#9aae9f', 'c': '#7f9788', 'b': '#516960', 'a': '#3d534d',
             'W': '#f8f7f3', 'w': '#d7d3cb', 's': '#a39e95'},
        art=[
            "................",
            "........d.......",
            ".......dc.e.....",
            "....e..cb.dc....",
            "...dc..W..cb....",
            "...cb.Ww...b..e.",
            "...b..W.....dc..",
            "..d...Ww...cb...",
            "..c....W..Wb....",
            "..b....Ww.W.....",
            ".......WwW......",
            "........Ww......",
            "........Ww......",
            ".......sWw......",
            "........Ww......",
            ".......sWws.....",
        ]),
    'teak': dict(
        pal={'e': '#d1da8c', 'd': '#b6c468', 'c': '#9cb052', 'b': '#6b7d34', 'a': '#526328', 'y': '#c9a24c',
             'T': '#887b6b', 't': '#685b4d', 's': '#4a3f34'},
        art=[
            "................",
            "................",
            "...edd....dde...",
            "..edccb..edccb..",
            "..dccbb..dccyb..",
            "...cbba..cbba...",
            "....ba.TT.ba....",
            "......edT.......",
            ".....edcbT......",
            ".....dccb.T.....",
            "......cba.T.....",
            "..........T.....",
            ".........tT.....",
            ".........Tt.....",
            ".........Tt.....",
            "........sTts....",
        ]),
    'kapok': dict(
        pal={'e': '#7ab966', 'd': '#58a04b', 'c': '#448a3e', 'b': '#28602a', 'a': '#1d4c20',
             'T': '#aab0a4', 't': '#8a9086', 's': '#5f645c'},
        art=[
            "................",
            "................",
            "....e.d.e.......",
            "...dcdcdc.......",
            "....bcbcb...e.d.",
            ".....bab...dcdc.",
            ".......T....bcb.",
            ".......T..T.ba..",
            ".......TTT......",
            "........Tt......",
            "........Tt......",
            "........Tt......",
            ".......tTt......",
            "........Tt......",
            ".......sTt......",
            "......stTts.....",
        ]),
}


def sapling(name):
    s = SAPLINGS[name]
    return from_ascii(s['art'], s['pal'])


# --------------------------------------------------------------------------
# doors and trapdoors (doors.Grid part maps, shaded by doors.render)
# --------------------------------------------------------------------------
Grid = doors.Grid


def _hinges(g, ys):
    for y in ys:
        g.hline(0, 3, y, 'M')
        g.hline(1, 3, y + 1, 'm')


def door_olive():
    """A Mediterranean farmhouse door: vertical boards, a small barred window up top, studded rails."""
    g = Grid(16, 32, 'P')
    for x in (4, 8, 12):
        g.vline(x, 1, 30, 's')
    g.border(0, 0, 15, 31, 'O')
    g.rect(1, 1, 14, 2, 'F')
    g.hline(1, 14, 3, 'f')
    g.rect(5, 5, 10, 10, '.')
    g.vline(7, 5, 10, 'm')
    g.vline(8, 5, 10, 'M')
    g.border(4, 4, 11, 11, 'F')
    for y in (15, 27):
        g.rect(1, y, 14, y + 1, 'F')
        g.hline(1, 14, y + 2, 'f')
        for x in (3, 6, 9, 12):
            g.set(x, y, 'K')
    _hinges(g, (7, 22))
    g.rect(12, 19, 13, 20, 'K')
    g.set(13, 20, 'k')
    return g


def door_eucalyptus():
    """A bush door: wide horizontal slabs of red gum held by a Z-brace, with a sliding peephole."""
    g = Grid(16, 32, 'Q')
    for y in (6, 12, 18, 24):
        g.hline(1, 14, y, 's')
    g.border(0, 0, 15, 31, 'O')
    g.rect(6, 3, 9, 4, '.')
    g.rect(5, 2, 10, 2, 'F')
    g.rect(5, 5, 10, 5, 'f')
    for r in range(8, 28):
        c = 2 + (r - 8) * 11 // 19
        g.set(c, r, 'F')
        g.set(c + 1, r, 'F')
        g.set(c + 2, r, 'f')
    g.rect(1, 8, 14, 9, 'F')
    g.hline(1, 14, 10, 'f')
    g.rect(1, 27, 14, 28, 'F')
    g.hline(1, 14, 29, 'f')
    _hinges(g, (8, 27))
    g.rect(12, 16, 13, 17, 'K')
    g.set(13, 17, 'k')
    return g


def door_teak():
    """A carved temple door: four sunk panels, each with a lozenge, brass bosses at the corners."""
    g = Grid(16, 32, 'F')
    g.border(0, 0, 15, 31, 'O')
    for (x0, y0, x1, y1) in ((2, 2, 7, 13), (8, 2, 13, 13), (2, 17, 7, 29), (8, 17, 13, 29)):
        g.recess(x0, y0, x1, y1, 'P')
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        for d in range(3):
            for (dx, dy) in ((d, 2 - d), (-d, 2 - d), (d, d - 2), (-d, d - 2)):
                g.set(cx + dx, cy + dy, 'h' if dy <= 0 else 'f')
        g.set(cx, cy, 'A')
    g.hline(1, 14, 15, 'h')
    for (x, y) in ((1, 1), (14, 1), (1, 30), (14, 30), (1, 15), (14, 15)):
        g.set(x, y, 'K')
    g.rect(7, 15, 8, 16, 'K')
    g.set(8, 16, 'k')
    return g


def door_kapok():
    """A light door of pale kapok boards with a round porthole and rope-lashed rails."""
    g = Grid(16, 32, 'P')
    for x in (5, 10):
        g.vline(x, 1, 30, 's')
    g.border(0, 0, 15, 31, 'O')
    cx, cy = 7.5, 7.5
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - cx, y - cy)
            if d < 3.6:
                g.set(x, y, '.')
            elif d < 4.6:
                g.set(x, y, 'F' if y < cy else 'f')
    for y in (17, 27):
        g.rect(1, y, 14, y + 1, 'F')
        for x in (2, 13):
            g.set(x, y, 'A')
            g.set(x, y + 1, 'a')
    g.hline(1, 14, 29, 'f')
    g.rect(12, 21, 13, 22, 'K')
    g.set(13, 22, 'k')
    return g


def trap_olive():
    g = Grid(16, 16, 'P')
    for x in (4, 8, 12):
        g.vline(x, 1, 14, 's')
    g.border(0, 0, 15, 15, 'O')
    g.rect(1, 2, 14, 3, 'F')
    g.rect(1, 12, 14, 13, 'F')
    for x in (3, 6, 9, 12):
        g.set(x, 2, 'K')
        g.set(x, 12, 'K')
    g.rect(6, 6, 9, 9, '.')
    g.vline(7, 6, 9, 'M')
    return g


def trap_eucalyptus():
    g = Grid(16, 16, 'Q')
    for y in (4, 8, 12):
        g.hline(1, 14, y, 's')
    g.border(0, 0, 15, 15, 'O')
    for r in range(1, 15):
        c = 1 + (r - 1) * 12 // 13
        g.set(c, r, 'F')
        g.set(c + 1, r, 'f')
    g.rect(2, 1, 3, 2, 'M')
    g.rect(12, 13, 13, 14, 'M')
    return g


def trap_teak():
    g = Grid(16, 16, 'F')
    g.border(0, 0, 15, 15, 'O')
    g.recess(2, 2, 13, 13, 'P')
    for d in range(5):
        for (dx, dy) in ((d, 4 - d), (-d, 4 - d), (d, d - 4), (-d, d - 4)):
            g.set(8 + dx, 8 + dy, '.' if abs(dx) + abs(dy) < 3 else ('h' if dy <= 0 else 'f'))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        g.set(x, y, 'K')
    return g


def trap_kapok():
    g = Grid(16, 16, 'P')
    for x in (5, 10):
        g.vline(x, 1, 14, 's')
    g.border(0, 0, 15, 15, 'O')
    for y in (2, 12):
        g.rect(1, y, 14, y + 1, 'F')
        g.set(3, y, 'A')
        g.set(12, y, 'A')
    for y in range(5, 10):
        for x in range(5, 11):
            if math.hypot(x - 7.5, y - 7) < 2.4:
                g.set(x, y, '.')
    return g


DOOR_ITEMS = {
    'olive': [
        "OPPsPPsPPf",
        "OFFFFFFFFf",
        "OPF..MFPPf",
        "OPF..MFPPf",
        "OPFFFFFPPf",
        "OKFKFKFKFf",
        "MMPsPPsPPf",
        "OPPsPPsPKf",
        "OPPsPPsPPf",
        "OKFKFKFKFf",
        "OFFFFFFFFf",
        "MMPsPPsPPf",
        "OPPsPPsPPf",
        "OOOOOOOOOO",
    ],
    'eucalyptus': [
        "OQQQQQQQQf",
        "OQQF..FQQf",
        "OssssssssO",
        "OFFFFFFFFf",
        "OQQQQQQFQf",
        "MMQQQQFQQf",
        "OssssFssKf",
        "OQQQFQQQQf",
        "OQQFQQQQQf",
        "OssFsssssO",
        "OQFQQQQQQf",
        "MMFFFFFFFf",
        "OQQQQQQQQf",
        "OOOOOOOOOO",
    ],
    'teak': [
        "OKFFFFFFKf",
        "OfPPhfPPhf",
        "OfPAhfPAhf",
        "OfPPhfPPhf",
        "OhhhhhhhhO",
        "OKFFKKFFKf",
        "OfPPhfPPhf",
        "OfPAhfPAhf",
        "OfPPhfPPhf",
        "OfPPhfPPhf",
        "OhhhhhhhhO",
        "OKFFFFFFKf",
        "OFFFFFFFFf",
        "OOOOOOOOOO",
    ],
    'kapok': [
        "OPPPsPPPPf",
        "OPFFFFPPPf",
        "OF....FPPf",
        "OF....FPPf",
        "OPFFFFPPPf",
        "OPPPsPPPPf",
        "OAFFFFFFAf",
        "OPPPsPPPKf",
        "OPPPsPPPPf",
        "OAFFFFFFAf",
        "OPPPsPPPPf",
        "OPPPsPPPPf",
        "OffffffffO",
        "OOOOOOOOOO",
    ],
}
DOORS = dict(olive=door_olive, eucalyptus=door_eucalyptus, teak=door_teak, kapok=door_kapok)
TRAPS = dict(olive=trap_olive, eucalyptus=trap_eucalyptus, teak=trap_teak, kapok=trap_kapok)
STYLE = {
    'olive': dict(metal='dark_iron'),
    'eucalyptus': dict(metal='iron'),
    'teak': dict(metal='brass', accent=['#f0d27a', '#c99d45', '#94702c', '#5f8a3e']),
    'kapok': dict(metal='brass', accent=['#d9c28a', '#b49a5e', '#8a7240', '#5f8a3e']),
}


def _door_item(w, planks, rng, **kw):
    g = Grid(16, 16, '.')
    g.ascii(DOOR_ITEMS[w], 3, 2)
    return doors.render(g, planks, rng, **kw)


def make_doors(w, planks, rng_for):
    st = STYLE[w]
    kw = dict(metal=st['metal'], accent=st.get('accent'), glow=st.get('glow'))
    full = doors.render(DOORS[w](), planks, rng_for(f'{w}_door'), **kw)
    trap = doors.render(TRAPS[w](), planks, rng_for(f'{w}_trapdoor'), **kw)
    item = _door_item(w, planks, rng_for(f'{w}_door_item'), **kw)
    return full[:16].copy(), full[16:].copy(), trap, item


# --------------------------------------------------------------------------
# ground
# --------------------------------------------------------------------------
RED_EARTH = ['#5e2414', '#7a2f19', '#8f3a1f', '#a24524', '#b3522b', '#c26437', '#cf7a4c']


def red_earth(rng):
    """Iron-red soil: clods, a scatter of pale quartz grit and darker ironstone pebbles."""
    f = pnoise(rng, sx=0.8, sy=0.8) + 0.5 * pnoise(rng, sx=2.2, sy=2.2)
    idx = quantize(f, [0.08, 0.18, 0.30, 0.26, 0.14, 0.04])
    img = paint(idx, RED_EARTH)
    for _ in range(5):                          # ironstone pebbles, dark with a lit edge
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        put(img, x, y, '#4a1d10')
        put(img, x + 1, y, '#5e2414')
        put(img, x, y - 1, '#c26437')
    for _ in range(6):                          # quartz grit
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        put(img, x, y, '#e0a37a')
    return img


CORAL_SAND = ['#cbbfb1', '#d9cfc2', '#e4dcd0', '#ece6dc', '#f4f0e8']


def coral_sand(rng):
    """Crushed shell and coral: near white, flecked with pink, peach and the odd grey fragment."""
    f = pnoise(rng, sx=0.6, sy=0.6) + 0.5 * pnoise(rng, sx=1.8, sy=1.8)
    idx = quantize(f, [0.10, 0.24, 0.34, 0.22, 0.10])
    img = paint(idx, CORAL_SAND)
    for colours, n in ((('#f0b6ac', '#e9a39a'), 6), (('#f2cfab',), 4), (('#b2a79b',), 3)):
        for _ in range(n):
            x, y = (int(v) for v in rng.integers(0, 16, 2))
            put(img, x, y, colours[int(rng.integers(0, len(colours)))])
    # a shell fragment: a little pale curve with a pink lip
    x, y = (int(v) for v in rng.integers(0, 16, 2))
    for dx, dy, c in ((0, 0, '#fbf6ee'), (1, 0, '#fbf6ee'), (2, 1, '#f0b6ac'), (-1, 1, '#e4dcd0')):
        put(img, x + dx, y + dy, c)
    return img


# --------------------------------------------------------------------------
# plants
# --------------------------------------------------------------------------
LAVENDER = dict(
    pal={'P': '#c8a6ec', 'p': '#9f78d6', 'q': '#7a55b8', 'Q': '#5a3a8f',
         'G': '#a3b08f', 'g': '#7f8c6c', 'd': '#5d684f'},
    art=[
        "................",
        "...P.......P....",
        "...p...P...p..P.",
        "..Pq...p..Pq..p.",
        "..pQ..Pq..pQ..q.",
        "..qg..pQ..qg.Pq.",
        "..Qg..qg..Qg.pQ.",
        "...g..Qg...g.qg.",
        "...g...g..g..Qg.",
        "...gG..g..g...g.",
        "....g..gG.g..Gg.",
        ".G..g...g.g..g..",
        "..g.gG..gg..gd..",
        "...ggd..gg.gd...",
        "....dg.dgg.d....",
        ".....dddgdd.....",
    ])

DESERT_PEA = dict(
    pal={'R': '#f04a3a', 'r': '#c92a22', 'k': '#8e1414', 'B': '#1a1214', 'b': '#3a2a2c',
         'G': '#a9b59a', 'g': '#80907a', 'd': '#5e6c58'},
    art=[
        "................",
        "................",
        "....R......R....",
        "...RrR....RrR...",
        "...rBk.R..rBk...",
        "....b.RrR..b....",
        "...R..rBk..R....",
        "..RrR..b..RrR...",
        "..rBk..g..rBk...",
        "...b..GgG..b....",
        "...gGgg.gGgg....",
        "..GgdG.g.Ggd....",
        ".Gg..dGgGd..gG..",
        "..d.GgdgdgG..d..",
        "...gd..g..dg....",
        "......dgd.......",
    ])

MOTH_ORCHID = dict(
    pal={'W': '#fff7fb', 'w': '#f3d8e8', 'p': '#e7a5cc', 'M': '#c23f8f', 'm': '#8f2a6a', 'Y': '#f4d35a',
         'S': '#6e8a3a', 's': '#4e6a28', 'L': '#3f7a35', 'l': '#2c5a26', 'K': '#5a8f45'},
    art=[
        "................",
        "......S.........",
        ".....S.SS.......",
        "..ww.S...SwW....",
        ".wWWwS...wWWw...",
        ".wpMWw..wWMpWw..",
        "..wmYw..wYmMw...",
        "...ww....wWw.S..",
        ".....S...ww..S..",
        "......S.....wWw.",
        "......S....wWMpw",
        ".......S....wmw.",
        "..KL...S...KLL..",
        ".KLLlL.S.LLLlK..",
        "..lLLllSllLLl...",
        "....lllslll.....",
    ])

DESERT_MARIGOLD = dict(
    pal={'Y': '#ffe35a', 'y': '#f2c12e', 'o': '#d99a1c', 'C': '#c27a12',
         'G': '#c9cfbf', 'g': '#a3ab98', 'd': '#7b8471'},
    art=[
        "................",
        "................",
        "...Y.Y.....Y....",
        "..YyCyY...YyY...",
        "...yoy...YyCyY..",
        "..Y.g.Y...yoy.Y.",
        ".....g...Y.g....",
        "..Y..g.Y...g....",
        ".YyY.gYyY..g....",
        "YyCyYgyoy.gG....",
        ".yoy.gYg..g.....",
        "..g..gGg.gG.....",
        "..gG.gdg.g......",
        "...gGgdggd......",
        "....dgdgd.......",
        ".....ddd........",
    ])

SPINIFEX = dict(
    pal={'L': '#e6dd8c', 'Y': '#cfc36a', 'y': '#b0a64e', 'g': '#8f8c3e', 'd': '#6a6a2c', 'b': '#57472a'},
    art=[
        "................",
        "................",
        "................",
        "................",
        ".......L........",
        "...L...Y..L.....",
        "....Y..Y.Y...L..",
        ".L...Y.Y.Y..Y...",
        "..Y..YyYyY.Y..L.",
        "...YyLyYyYYy.Y..",
        "L...yYyLyYyYy...",
        ".YyYyYgyYgyYyYL.",
        "..yYgyYgyYgYyg..",
        ".gyYgdygdygdygy.",
        "..dgbgdgbdgdbg..",
        "...dbdbdbdbdd...",
    ])

OCOTILLO_PAL = {'G': '#7f8a5a', 'g': '#5f6a42', 'd': '#434b30', 'L': '#8fb04a', 'l': '#6f9038',
                'R': '#f0502c', 'r': '#c8321c', 'O': '#ff8a3a'}
# whip centre-lines (x at each row, top to bottom), shared by the stem and the tip so they line up
OCOTILLO_WHIPS = [[3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 7, 7], [8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8],
                  [12, 12, 12, 11, 11, 11, 11, 10, 10, 10, 10, 10, 9, 9, 9, 9], [1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 6, 6, 7]]


def ocotillo(tip):
    """Whips of grey-green stem with tiny leaves along them; at the tip each ends in a scarlet flower spike."""
    img = blank()
    for i, xs in enumerate(OCOTILLO_WHIPS):
        top = (4 + 2 * i) if tip else 0
        for y in range(top, 16):
            put(img, xs[y], y, OCOTILLO_PAL['G'] if (y + i) % 3 else OCOTILLO_PAL['g'], wrap=False)
            if (y + i) % 4 == 0:
                put(img, xs[y] + (1 if i % 2 else -1), y, OCOTILLO_PAL['L' if y % 2 else 'l'], wrap=False)
        if tip:
            for k, c in enumerate(['O', 'R', 'R', 'r']):
                put(img, xs[top] + (1 if k == 1 else 0), top - 4 + k, OCOTILLO_PAL[c], wrap=False)
    return img


SAGUARO = ['#1f4a24', '#2c5e2c', '#3a7234', '#4a863e', '#5d994a', '#77ad5c']


def saguaro_side(rng):
    """Vertical ribs, each lit on one side, with spine clusters (areoles) along the ridges. Tiles vertically."""
    img = blank()
    rib = [0, 2, 4, 5, 3, 1]          # one rib, six pixels across: groove, flank, ridge, flank
    n = pnoise(rng, sx=0.8, sy=2.0)
    for y in range(16):
        for x in range(16):
            v = rib[(x + 1) % 6] + (1 if n[y, x] > 1.1 else 0) - (1 if n[y, x] < -1.2 else 0)
            put(img, x, y, SAGUARO[int(np.clip(v, 0, 5))])
    for x in (2, 8, 14):              # areoles on the ridges: a pale fuzzy dot with dark spines
        for y in range(int(rng.integers(0, 3)), 16, 3):
            put(img, x, y, '#d8d2a6')
            put(img, x + 1, y, '#2a2416')
    return img


def saguaro_top(rng):
    """The crown from above: ribs converging on a woolly felted tip."""
    img = blank()
    for y in range(16):
        for x in range(16):
            a = math.atan2(y - 7.5, x - 7.5)
            r = math.hypot(x - 7.5, y - 7.5)
            v = 2.5 + 2.5 * math.cos(a * 12) - r * 0.1
            put(img, x, y, SAGUARO[int(np.clip(round(v), 0, 5))])
    for y in range(6, 10):
        for x in range(6, 10):
            put(img, x, y, '#cfc79a' if (x + y) % 2 else '#b8af84')
    return img


# --------------------------------------------------------------------------
def generate(rng_for):
    out = []
    for w, p in WOODS.items():
        out.append((f'textures/block/{w}_log.png', BARK[w](rng_for(f'{w}_log'), p), True))
        out.append((f'textures/block/{w}_log_top.png', wood.log_top(rng_for(f'{w}_log_top'), w, p), False))
        out.append((f'textures/block/stripped_{w}_log.png', wood.stripped_side(rng_for(f'stripped_{w}_log'), w, p), True))
        out.append((f'textures/block/stripped_{w}_log_top.png', wood.log_top(rng_for(f'stripped_{w}_log_top'), w, p, stripped=True), False))
        out.append((f'textures/block/{w}_planks.png', planks(rng_for(f'{w}_planks'), w, p), True))
        out.append((f'textures/block/{w}_leaves.png', LEAVES[w](rng_for(f'{w}_leaves')), True))
        out.append((f'textures/block/{w}_sapling.png', sapling(w), False))
        top, bottom, trap, item = make_doors(w, p['planks'], rng_for)
        out.append((f'textures/block/{w}_door_top.png', top, False))
        out.append((f'textures/block/{w}_door_bottom.png', bottom, False))
        out.append((f'textures/block/{w}_trapdoor.png', trap, False))
        out.append((f'textures/item/{w}_door.png', item, False))
    out.append(('textures/block/flame_tree_leaves.png', leaves_flame_tree(rng_for('flame_tree_leaves')), True))
    out.append(('textures/block/red_earth.png', red_earth(rng_for('red_earth')), True))
    out.append(('textures/block/coral_sand.png', coral_sand(rng_for('coral_sand')), True))
    for name, d in [('lavender', LAVENDER), ('desert_pea', DESERT_PEA), ('moth_orchid', MOTH_ORCHID),
                    ('desert_marigold', DESERT_MARIGOLD), ('spinifex', SPINIFEX)]:
        out.append((f'textures/block/{name}.png', from_ascii(d['art'], d['pal']), False))
    out.append(('textures/block/ocotillo.png', ocotillo(False), True))
    out.append(('textures/block/ocotillo_top.png', ocotillo(True), False))
    out.append(('textures/block/saguaro_side.png', saguaro_side(rng_for('saguaro_side')), True))
    out.append(('textures/block/saguaro_top.png', saguaro_top(rng_for('saguaro_top')), False))
    return out
