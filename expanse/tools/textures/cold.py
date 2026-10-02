"""Textures for the cold and temperate biomes (registry/ColdBlocks.java): the maple, aspen, beech and pine wood
sets, orange maple and larch leaves, the larch sapling, peat and sphagnum, the plants, and cranberries.

Built with the same helpers as the other wood sets (wood.log_top / stripped_side / planks, leaves.Canvas and its
stamps, doors.Grid and render), each texture from its own seeded RNG, so the output is deterministic.
"""
import math

import numpy as np

import doors
import leaves as lv
import wood
from texlib import blank, from_ascii, lerp, outline, paint, pnoise, put, quantize, shade

# --------------------------------------------------------------------------
# wood palettes (dark -> light); bark index 0 is the deepest crevice
# --------------------------------------------------------------------------
WOODS = {
    # sugar maple: grey-brown bark in long plates; creamy pinkish-tan wood
    'maple': dict(
        bark=['#2f2622', '#4a3d36', '#5a4b43', '#695850', '#7a685e', '#8d7a6f'],
        planks=['#99714f', '#b78d66', '#c79e75', '#d5b086', '#e0bf97'],
        stripped=['#bf9670', '#caa37d', '#d5b08a', '#dfbe98', '#e8caa6'],
        rings=['#b08a62', '#c49c74', '#d2ac85', '#dfbc97'],
    ),
    # quaking aspen: chalky green-white bark with black "eyes"; pale cream wood
    'aspen': dict(
        bark=['#23221e', '#55584d', '#adb49b', '#bfc5ad', '#cfd4bf', '#e0e4d2'],
        planks=['#a69b7e', '#c6bc9e', '#d4cbae', '#e0d8bf', '#eae3ce'],
        stripped=['#cec5a6', '#d8d0b3', '#e1dabf', '#e9e3cb', '#f0ebd7'],
        rings=['#c8bc99', '#d6cbaa', '#e1d7b9', '#ebe3c9'],
    ),
    # beech: smooth elephant-grey bark; pale pinkish-brown wood
    'beech': dict(
        bark=['#474b4e', '#62676a', '#757a7d', '#878c8f', '#999ea0', '#adb1b2'],
        planks=['#93654b', '#ae7f63', '#be8e71', '#cb9e81', '#d7ad91'],
        stripped=['#c29276', '#cd9f84', '#d7ac92', '#dfb89f', '#e7c4ad'],
        rings=['#b7896d', '#c79b7f', '#d4aa8f', '#dfb99f'],
    ),
    # Scots pine: flaky orange-red plates between dark fissures; yellow resinous wood
    'pine': dict(
        bark=['#371e14', '#633723', '#7f462b', '#985632', '#b0683b', '#c47b48'],
        planks=['#9e7743', '#c0975c', '#cea76b', '#dab67b', '#e5c58d'],
        stripped=['#c59a60', '#cfa76e', '#d9b47d', '#e2c08c', '#e9cb9b'],
        rings=['#b78d56', '#c9a169', '#d6b27b', '#e2c08d'],
    ),
}


def _closed_walk(rng, n=16, stay=0.7, amp=None):
    return wood._closed_walk(rng, n, stay, amp)


def bark_maple(rng, p):
    """Long interlacing plates split by shallow furrows that break off every so often."""
    f = pnoise(rng, sx=0.5, sy=4.0) + 0.4 * pnoise(rng, sx=1.0, sy=1.0)
    idx = quantize(f, [0.18, 0.30, 0.32, 0.20]) + 1
    for x0 in (1, 6, 11):
        x0 = (x0 + int(rng.integers(0, 2))) % 16
        walk = _closed_walk(rng, 16, stay=0.75, amp=2)
        gap, glen = int(rng.integers(0, 16)), int(rng.integers(2, 4))
        for y in range(16):
            if (y - gap) % 16 < glen:
                continue                       # a plate bridges the furrow here
            x = (x0 + walk[y]) % 16
            idx[y, x] = 0
            xr = (x + 1) % 16
            idx[y, xr] = min(idx[y, xr], 2)
            xl = (x - 1) % 16
            if idx[y, xl] >= 2 and rng.random() < 0.5:
                idx[y, xl] = 5                 # lit plate edge
    return paint(idx, p['bark'])


EYE = ["1001",
       ".00."]


def bark_aspen(rng, p):
    """Smooth chalky bark, horizontal lenticels, and the black eyes left where lower branches fell."""
    f = pnoise(rng, sx=1.4, sy=2.6) + 0.3 * pnoise(rng, sx=0.5, sy=0.5)
    idx = quantize(f, [0.12, 0.33, 0.37, 0.18]) + 2
    placed = 0
    while placed < 6:
        y, x = (int(v) for v in rng.integers(0, 16, 2))
        for dx in range(int(rng.integers(2, 4))):
            idx[y, (x + dx) % 16] = 1
        placed += 1
    ex, ey = int(rng.integers(2, 11)), int(rng.integers(2, 12))
    for yy, row in enumerate(EYE):
        for xx, ch in enumerate(row):
            if ch in '01':
                idx[(ey + yy) % 16, (ex + xx) % 16] = int(ch)
    # the scar's grey halo
    for xx in (-1, 4):
        idx[ey % 16, (ex + xx) % 16] = min(idx[ey % 16, (ex + xx) % 16], 2)
    return paint(idx, p['bark'])


def bark_beech(rng, p):
    """Smooth grey, mottled, with a few faint horizontal wrinkles."""
    f = pnoise(rng, sx=2.2, sy=2.2) + 0.5 * pnoise(rng, sx=0.7, sy=1.3)
    idx = quantize(f, [0.06, 0.22, 0.36, 0.24, 0.12]) + 1
    for _ in range(4):
        y, x = (int(v) for v in rng.integers(0, 16, 2))
        for dx in range(int(rng.integers(3, 6))):
            xx = (x + dx) % 16
            idx[y, xx] = max(1, min(idx[y, xx], 2))
            idx[(y + 1) % 16, xx] = max(idx[(y + 1) % 16, xx], 4) if rng.random() < 0.5 else idx[(y + 1) % 16, xx]
    return paint(np.clip(idx, 0, 5), p['bark'])


def bark_pine(rng, p):
    """Irregular flakes of orange bark, longer than wide, between dark fissures."""
    pts = []
    for gy in range(3):
        for gx in range(4):
            pts.append((gx * 4 + 2 + rng.uniform(-1.2, 1.2), gy * 16 / 3 + 2.6 + rng.uniform(-1.4, 1.4), int(rng.integers(2, 6))))
    n = pnoise(rng, sx=0.6, sy=0.6)
    idx = np.zeros((16, 16), int)
    for y in range(16):
        for x in range(16):
            ds = []
            for (px, py, shade_) in pts:
                dx = min(abs(x - px), 16 - abs(x - px))
                dy = min(abs(y - py), 16 - abs(y - py))
                ds.append((math.hypot(dx, dy * 0.62), shade_, py, y))
            ds.sort()
            (d1, s1, py, _), (d2, _, _, _) = ds[0], ds[1]
            if d2 - d1 < 0.55:
                idx[y, x] = 0
            elif d2 - d1 < 1.0:
                idx[y, x] = 1
            else:
                up = ((y - py + 8) % 16) - 8 < -1     # upper part of a flake catches the light
                idx[y, x] = int(np.clip(s1 + (1 if up else 0) + (1 if n[y, x] > 1.0 else 0) - (1 if n[y, x] < -1.0 else 0), 2, 5))
    return paint(idx, p['bark'])


BARK = dict(maple=bark_maple, aspen=bark_aspen, beech=bark_beech, pine=bark_pine)

# --------------------------------------------------------------------------
# leaves
# --------------------------------------------------------------------------
LEAF_PAL = {
    'maple': ['#4a0e0c', '#6c1612', '#8e2015', '#b02c1a', '#c94023', '#de5a32', '#f2864e'],
    'orange_maple': ['#5e2a08', '#82390c', '#a64e10', '#c76716', '#dd8222', '#eda03c', '#f8c468'],
    'aspen': ['#5b4b0f', '#7c6714', '#9f891b', '#c0a925', '#d6c136', '#e7d651', '#f7ed8a'],
    'beech': ['#1d4614', '#295d1b', '#387623', '#4a8d2c', '#5fa339', '#79ba4a', '#9fd36a'],
    'pine': ['#173a2e', '#205040', '#2b604c', '#38735a', '#478666', '#5b9a74', '#78b28a'],
    'larch': ['#5a3809', '#7c500d', '#9f6a13', '#bf881c', '#d5a32c', '#e6bd48', '#f4d678'],
}
MAPLE_LEAF = ["..5..",
              "4.5.4",
              "45654",
              ".454.",
              "..3.."]
COIN = [".5.",
        "565",
        ".4."]
OVAL = [".4.",
        "455",
        "354",
        ".3."]
NEEDLES = ["3...3",
           ".4.4.",
           "..5..",
           ".4.4.",
           "3...3"]
ROSETTE = [".4.4.",
           "45654",
           ".565.",
           "45654",
           ".4.4."]


def _layered(rng, front, back=None, step=4, carve=0.18, extra=13, mirror=True):
    cv = lv.Canvas()
    back = back or front
    for (x, y) in lv._grid(rng, step=step, jitter=1):
        lv._stamp(cv, back, x - len(back[0]) // 2, y - len(back) // 2, off=-2, mirror=mirror and rng.random() < 0.5)
    for (x, y) in lv._grid(rng, step=step, jitter=1, ox=step // 2, oy=step // 2)[:extra]:
        lv._stamp(cv, front, x - len(front[0]) // 2, y - len(front) // 2, off=0, mirror=mirror and rng.random() < 0.5)
    lv.carve_holes(cv, rng, carve)
    lv._shadow_under(cv)
    return cv


def leaves_maple(rng, kind):
    return _layered(rng, MAPLE_LEAF).image(LEAF_PAL[kind])


def leaves_aspen(rng):
    """Small round leaves on long stalks, a few turned to show their pale undersides."""
    cv = _layered(rng, COIN, COIN, step=3, carve=0.2, extra=20)
    tops = np.argwhere(cv.idx == 5)
    rng.shuffle(tops)
    for (y, x) in tops[:6]:
        cv.idx[y, x] = 6
    return cv.image(LEAF_PAL['aspen'])


def leaves_beech(rng):
    return _layered(rng, OVAL, lv.LEAF_BLOB, carve=0.17).image(LEAF_PAL['beech'])


def leaves_pine(rng):
    cv = _layered(rng, NEEDLES, lv.REDWOOD_SPRIG, carve=0.16, extra=16, mirror=False)
    return cv.image(LEAF_PAL['pine'])


def leaves_larch(rng):
    """Soft rosettes of short needles, as a larch carries them on its spurs."""
    return _layered(rng, ROSETTE, NEEDLES, carve=0.22, extra=12, mirror=False).image(LEAF_PAL['larch'])


LEAVES = {
    'maple': lambda r: leaves_maple(r, 'maple'),
    'orange_maple': lambda r: leaves_maple(r, 'orange_maple'),
    'aspen': leaves_aspen,
    'beech': leaves_beech,
    'pine': leaves_pine,
    'larch': leaves_larch,
}

# --------------------------------------------------------------------------
# saplings (cross models)
# --------------------------------------------------------------------------
SAPLINGS = {
    'maple': dict(
        pal={'e': '#f2864e', 'd': '#de5a32', 'c': '#b02c1a', 'b': '#8e2015', 'o': '#eda03c', 'O': '#c76716',
             'T': '#7a685e', 't': '#5a4b43', 's': '#3a2f2a'},
        art=[
            "................",
            "................",
            "......e.........",
            "....e.d.e.......",
            "....dddddo......",
            ".....cdc.oo.....",
            "......c.oooO....",
            "..e...T..oO.....",
            "..d.e.T...O.....",
            "..ddddT.........",
            "...cdcT.........",
            "....c.T.........",
            "......TT........",
            ".......T........",
            ".......t........",
            ".......s........",
        ]),
    'aspen': dict(
        pal={'e': '#f7ed8a', 'd': '#e7d651', 'c': '#c0a925', 'b': '#9f891b',
             'W': '#e0e4d2', 'w': '#bfc5ad', 'k': '#23221e'},
        art=[
            "................",
            "......ed........",
            ".....edcd.......",
            "....edcbcd......",
            "....dce.dc......",
            "...ed.dcWde.....",
            "...dcb.Wcdc.....",
            "....bdcWbc......",
            ".....bcWd.......",
            ".......W........",
            ".......k........",
            ".......W........",
            ".......W........",
            ".......w........",
            ".......W........",
            ".......w........",
        ]),
    'beech': dict(
        pal={'e': '#9fd36a', 'd': '#79ba4a', 'c': '#4a8d2c', 'b': '#295d1b',
             'T': '#999ea0', 't': '#757a7d', 's': '#525659'},
        art=[
            "................",
            "................",
            "......ed........",
            "....edcdde......",
            "...dcbdcccd.....",
            "...cb.cTbcb.....",
            "..ed...T..de....",
            "..dcd..T.edc....",
            "...bc..T.cb.....",
            ".......Tt.......",
            "......tT........",
            ".......T........",
            ".......T........",
            ".......Tt.......",
            ".......T........",
            ".......s........",
        ]),
    'pine': dict(
        pal={'e': '#649778', 'd': '#4c7f65', 'c': '#3b6c57', 'b': '#234b3f', 'a': '#1a3b34',
             'T': '#b0683b', 't': '#7f462b', 's': '#4a2a1a'},
        art=[
            "................",
            ".......e........",
            "......ede.......",
            ".....e.d.e......",
            "....d.cdc.d.....",
            "......bTb.......",
            "...e.d.T.d.e....",
            "....dccTccd.....",
            ".....bbTbb......",
            "..e.d..T..d.e...",
            "...dcc.T.ccd....",
            "....bbaTabb.....",
            ".......T........",
            ".......t........",
            ".......T........",
            ".......s........",
        ]),
    'larch': dict(
        pal={'e': '#f4d678', 'd': '#e6bd48', 'c': '#d5a32c', 'b': '#9f6a13',
             'T': '#7a5a3a', 't': '#5c3f27', 's': '#3d2919'},
        art=[
            "................",
            ".......d........",
            "......ded.......",
            ".......T........",
            ".....edTde......",
            "....d.cTc.d.....",
            ".......T........",
            "...e.dcTcd.e....",
            "....d.bTb.d.....",
            ".......T........",
            "..e.ddcTcdd.e...",
            "...d.b.T.b.d....",
            ".......T........",
            ".......t........",
            ".......T........",
            ".......s........",
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
        g.rect(0, y, 1, y + 1, 'M')
        g.set(1, y + 1, 'm')


def _handle(g, y=15):
    g.rect(12, y, 13, y + 1, 'K')
    g.set(13, y + 1, 'k')


def _maple_leaf(g, cx, cy):
    """A little carved maple leaf in accent colours."""
    for (dx, dy, c) in [(0, -2, 'A'), (-2, -1, 'a'), (0, -1, 'A'), (2, -1, 'a'), (-1, 0, 'a'), (0, 0, 'A'), (1, 0, 'a'),
                        (-2, 0, 'B'), (2, 0, 'B'), (0, 1, 'a'), (-1, 1, 'B'), (1, 1, 'B'), (0, 2, 'L')]:
        g.set(cx + dx, cy + dy, c)


def door_maple():
    """Four-pane window over two sunken panels, each carved with a maple leaf."""
    g = Grid(16, 32, 'F')
    g.border(0, 0, 15, 31, 'O')
    g.rect(3, 3, 12, 12, '.')
    g.vline(7, 3, 12, 'F')
    g.hline(3, 12, 7, 'F')
    g.hline(3, 12, 2, 'f')
    g.vline(2, 2, 12, 'f')
    g.hline(2, 13, 13, 'h')
    g.recess(3, 17, 7, 28)
    g.recess(9, 17, 13, 28)
    _maple_leaf(g, 5, 22)
    _maple_leaf(g, 11, 22)
    g.hline(1, 14, 30, 'f')
    _hinges(g, (5, 25))
    _handle(g)
    return g


def door_aspen():
    """Pale boards with a diamond window, the shape of an aspen's eye."""
    g = Grid(16, 32, 'P')
    for x in (4, 8, 11):
        g.vline(x, 1, 30, 's')
    g.border(0, 0, 15, 31, 'O')
    for y in range(2, 15):
        for x in range(1, 15):
            d = abs(x - 7.5) + abs(y - 8.0)
            if d < 4.6:
                g.set(x, y, '.')
            elif d < 5.8:
                g.set(x, y, 'h' if (x > 7.5 and y > 8) else ('f' if (x < 7.5 and y < 8) else 'F'))
    g.rect(1, 16, 14, 17, 'F')
    g.hline(1, 14, 18, 'f')
    g.rect(1, 27, 14, 28, 'F')
    g.hline(1, 14, 29, 'f')
    _hinges(g, (4, 24))
    _handle(g, 19)
    return g


def door_beech():
    """A heavy panelled door: round-headed window above, three panels below."""
    g = Grid(16, 32, 'F')
    g.border(0, 0, 15, 31, 'O')
    cx, cy = 7.5, 6.5
    for y in range(2, 13):
        for x in range(3, 13):
            if (y >= cy and 3.5 <= x <= 11.5) or math.hypot(x - cx, y - cy) < 4.6:
                g.set(x, y, '.')
    g.hline(3, 12, 13, 'h')
    g.recess(3, 16, 12, 19)
    g.recess(3, 21, 7, 28)
    g.recess(9, 21, 12, 28)
    g.hline(1, 14, 30, 'f')
    _hinges(g, (5, 25))
    _handle(g, 14)
    return g


def door_pine():
    """A rustic door of horizontal boards on a Z brace, with a small square window."""
    g = Grid(16, 32, 'Q')
    for y in range(4, 32, 5):
        g.hline(1, 14, y, 's')
    g.border(0, 0, 15, 31, 'O')
    g.rect(5, 4, 10, 9, '.')
    g.border(4, 3, 11, 10, 'F')
    g.hline(4, 11, 3, 'f')
    g.vline(4, 3, 10, 'f')
    for r in range(13, 29):
        c = 2 + (r - 13) * 11 // 15
        g.set(c, r, 'F')
        g.set(c + 1, r, 'F')
        if g.get(c + 2, r) != 'O':
            g.set(c + 2, r, 'f')
    g.rect(1, 12, 14, 12, 'F')
    g.rect(1, 29, 14, 29, 'F')
    for y in (12, 28):
        g.hline(0, 4, y, 'M')
        g.hline(1, 4, y + 1, 'm')
    _handle(g, 16)
    return g


def trap_maple():
    g = Grid(16, 16, 'F')
    g.border(0, 0, 15, 15, 'O')
    g.recess(2, 2, 7, 13)
    g.recess(8, 2, 13, 13)
    for (x, y) in ((4, 4), (10, 4), (4, 10), (10, 10)):
        g.rect(x, y, x + 1, y + 1, '.')
    _maple_leaf(g, 5, 7)
    return g


def trap_aspen():
    g = Grid(16, 16, 'P')
    for x in (4, 8, 11):
        g.vline(x, 1, 14, 's')
    g.border(0, 0, 15, 15, 'O')
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d < 3.2:
                g.set(x, y, '.')
            elif d < 4.3:
                g.set(x, y, 'F')
    g.rect(1, 1, 14, 1, 'F')
    g.rect(1, 14, 14, 14, 'F')
    return g


def trap_beech():
    g = Grid(16, 16, 'F')
    g.border(0, 0, 15, 15, 'O')
    g.recess(2, 2, 13, 13, 'P')
    for (x0, y0) in ((4, 4), (9, 4), (4, 9), (9, 9)):
        g.rect(x0, y0, x0 + 2, y0 + 2, '.')
    return g


def trap_pine():
    g = Grid(16, 16, 'Q')
    for y in (4, 8, 12):
        g.hline(1, 14, y, 's')
    g.border(0, 0, 15, 15, 'O')
    for r in range(2, 14):
        c = 2 + (r - 2) * 10 // 11
        g.set(c, r, 'F')
        g.set(c + 1, r, 'F')
    g.rect(5, 5, 6, 6, '.')
    g.rect(9, 9, 10, 10, '.')
    for y in (2, 13):
        g.hline(1, 3, y, 'M')
    return g


DOOR_ITEMS = {
    'maple': [
        "OFFFFFFFFf",
        "OF..F...Ff",
        "OFFFFFFFFf",
        "OF..F...Ff",
        "OFhhhhhhFf",
        "OffffffffO",
        "OFFFFFFKFf",
        "OfPPhfPPhf",
        "OfAPhfAPhf",
        "OfaBhfaBhf",
        "OfPPhfPPhf",
        "OFhhhFhhhf",
        "OFFFFFFFFf",
        "OOOOOOOOOO",
    ],
    'aspen': [
        "OPPPsPPPsf",
        "OPPP.PPPsf",
        "OPP...PPsf",
        "OP.....Psf",
        "OPP...PPsf",
        "OPPP.PPPsf",
        "OFFFFFFKFf",
        "OffffffffO",
        "OPPPsPPPsf",
        "OPPPsPPPsf",
        "OPPPsPPPsf",
        "OFFFFFFFFf",
        "OPPPsPPPsf",
        "OOOOOOOOOO",
    ],
    'beech': [
        "OFFFFFFFFf",
        "OFF....FFf",
        "OF......Ff",
        "OF......Ff",
        "OF......Ff",
        "OFhhhhhhFf",
        "OFfPPPPKFf",
        "OFfPPPPhFf",
        "OFFFFFFFFf",
        "OFfPPfPPhf",
        "OFfPPfPPhf",
        "OFfPPfPPhf",
        "OFhhhhhhhf",
        "OOOOOOOOOO",
    ],
    'pine': [
        "OQQQQQQQQf",
        "OQF....FQf",
        "OQF....FQf",
        "OQFFFFFFQf",
        "OssssssssO",
        "MMQQQQQQQf",
        "OQQQQQQKQf",
        "OssssssssO",
        "OQFFQQQQQf",
        "OQQFFQQQQf",
        "OssFFsssss",
        "MMQQQFFQQf",
        "OQQQQQFFQf",
        "OOOOOOOOOO",
    ],
}
DOORS = dict(maple=door_maple, aspen=door_aspen, beech=door_beech, pine=door_pine)
TRAPS = dict(maple=trap_maple, aspen=trap_aspen, beech=trap_beech, pine=trap_pine)
STYLE = {
    'maple': dict(metal='dark_iron', accent=['#e05a32', '#b8301c', '#7a1a10', '#5f8a3e']),
    'aspen': dict(metal='iron'),
    'beech': dict(metal='brass'),
    'pine': dict(metal='dark_iron'),
}


def _door_item(w, planks, rng, **kw):
    g = Grid(16, 16, '.')
    g.ascii(DOOR_ITEMS[w], 3, 2, skip='\0')
    return doors.render(g, planks, rng, **kw)


def make_doors(w, planks, rng_for):
    st = STYLE[w]
    kw = dict(metal=st['metal'], accent=st.get('accent'), glow=None)
    full = doors.render(DOORS[w](), planks, rng_for(f'{w}_door'), **kw)
    trap = doors.render(TRAPS[w](), planks, rng_for(f'{w}_trapdoor'), **kw)
    item = _door_item(w, planks, rng_for(f'{w}_door_item'), **kw)
    return full[:16].copy(), full[16:].copy(), trap, item


# --------------------------------------------------------------------------
# plants
# --------------------------------------------------------------------------
COTTON_GRASS = dict(
    pal={'W': '#fbfbf6', 'w': '#e6e6dc', 'v': '#c9c9bd', 'G': '#8e9a52', 'g': '#6f7b3e', 'd': '#525c2e', 'b': '#7a5a36'},
    art=[
        "................",
        "....WW.......W..",
        "...WWWw.....WWw.",
        "...wWWv..W..wWv.",
        "....wv..WWw..v..",
        ".....g..wWv..g..",
        ".....g...v...g..",
        "....g....g..g...",
        "....g...g...g...",
        "...g....g..g..G.",
        "...g..G.g..g.G..",
        "..G...Gg...gG...",
        "..Gd...Gg.dG....",
        "...Gd..dGgd.....",
        "....dd.bdGd.....",
        ".....dbdbd......",
    ])
CRANBERRY = dict(
    pal={'R': '#e04a4a', 'r': '#b0242c', 'q': '#7a1420', 'h': '#ff9a8a', 'G': '#4f7a3a', 'g': '#3a5e2c', 'd': '#26421f',
         'b': '#5a3a2a'},
    art=[
        "................",
        "................",
        "................",
        "................",
        "................",
        "......G.........",
        "....GgGG..G.....",
        "...GgdhRGGgG....",
        "..GgRrRrgdGgG...",
        "..gdrqGgGhRgd...",
        "..GGgGhRgrrqGg..",
        ".gdgGrrqdGgdgd..",
        "..GdgdgGbgdhR...",
        "...dbgd.bdgrq...",
        ".....b...b......",
        ".....b..b.......",
    ])
LICHEN = dict(
    pal={'W': '#e8ecd8', 'w': '#cfd5bb', 'v': '#b3ba9c', 'u': '#949b7e', 'k': '#6f7560'},
    art=[
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..W.W.......W...",
        "...wv.W.W..wv.W.",
        ".W.vw.wvw.W.vwv.",
        "..wvuwvu..wvwu..",
        ".WvuvwuvW.vuvuW.",
        "..vuwvuvwvuwvu..",
        ".wuvukuvukuvuvw.",
        "..kuvukvuvkuvk..",
        "...kkukkukkuk...",
    ])
FIREWEED_TOP = dict(
    pal={'P': '#f7a6e0', 'M': '#e05cc0', 'm': '#b8389a', 'n': '#862a74', 'u': '#6a4a7a',
         'G': '#6f9a3e', 'g': '#4f7a2e', 'r': '#8a3a4a'},
    art=[
        "................",
        ".......u........",
        ".......n........",
        "......nmn.......",
        ".......m........",
        "......mMn.......",
        ".....PMrMm......",
        "......Mrm.......",
        ".....MPrPMm.....",
        "....PMgrMPm.....",
        ".....mPrPm......",
        "....PMPrMPM.....",
        "...MPm.r.mPMm...",
        "....mPMrPMm.....",
        "...MP.Grg.PMm...",
        "......grG.......",
    ])
FIREWEED_BOTTOM = dict(
    pal={'G': '#6f9a3e', 'g': '#4f7a2e', 'd': '#365a22', 'r': '#8a3a4a', 'R': '#a24a5a'},
    art=[
        ".......r........",
        ".......r.G......",
        "....G..rGg......",
        ".....Gg.r.......",
        "......gGr.......",
        ".......r..Gg....",
        ".......rgG..g...",
        "..G....r........",
        "...gG..r........",
        ".....gGR.G......",
        ".......rGgd.....",
        ".......r...d....",
        ".......R........",
        "....gGgr........",
        "..d....r..d.....",
        ".......r........",
    ])
# Bluebells, seen from above (vanilla's flower-bed layout: one quadrant per flower amount, each head over its
# stem): nodding blue bells in a cluster, the strap leaves showing round them.
BLUEBELLS = dict(
    pal={'L': '#a9b0ff', 'B': '#7378e6', 'b': '#5458d0', 'n': '#3a3eae', 'k': '#2a2b80', 'G': '#5a9a38', 'g': '#3f7a2a'},
    art=[
        "...gBL.g........",
        "..gbLBbg........",
        "...nbBn.....g...",
        "g...gn....gBLBg.",
        "BL.......gbLBLbg",
        "bLBn..BL..nbBbn.",
        "nbn.gbLBg..gnkg.",
        ".g...nbn........",
        ".........BL....g",
        "...g....bLBn..BL",
        "..gBLBg..nbn.bLB",
        ".gbLBLbg..g...nb",
        ".gnbBbnk..gBLg..",
        "..gnkng..gbLBbg.",
        "...g.....gnbBng.",
        "..........gnkg..",
    ])
BLUEBELL_ITEM = dict(
    pal={'L': '#a9b0ff', 'B': '#7378e6', 'b': '#5458d0', 'n': '#3a3eae', 'G': '#6fae48', 'g': '#4f8a32', 'd': '#356222'},
    art=[
        "................",
        "......gGg.......",
        ".....g...g......",
        "....g.....G.....",
        "...LB.....g.....",
        "...bn....LBg....",
        "..LB.....bn.....",
        "..bn....LB......",
        "...g....bn..G...",
        "...LB...g..G....",
        "...bn..g..G.....",
        ".......g.G......",
        ".......gG.......",
        "......Gg........",
        ".....Gdg........",
        "......dg........",
    ])
CRANBERRIES_ITEM = dict(
    pal={'H': '#ffb0a0', 'R': '#e04a4a', 'r': '#b0242c', 'q': '#7a1420', 'G': '#6f9a3e', 'g': '#4f7a2e'},
    art=[
        "................",
        "................",
        "................",
        "........gG......",
        ".......gGG......",
        "......gGg.......",
        "....HRr.HRr.....",
        "...HRRrqRRrq....",
        "...RRrqqRrqq....",
        "....rqHRrq......",
        ".....HRRrq......",
        ".....RRrqq......",
        "......rqq.......",
        "................",
        "................",
        "................",
    ])


def stem_texture():
    """The flower bed's stem: vanilla's layout, a strip at x=0 of rows 4-6, drawn green (untinted)."""
    img = blank()
    for y, c in zip((4, 5, 6), ('#5a9a38', '#4a8a30', '#3f7a2a')):
        put(img, 0, y, c, wrap=False)
    return img


# --------------------------------------------------------------------------
# ground blocks
# --------------------------------------------------------------------------
def peat(rng):
    """Dark fibrous peat: clods, pale rootlets and a few half-rotted stems."""
    pal = ['#24160d', '#33210f', '#422c18', '#52381f', '#634528', '#7a5a36', '#8f6e46']
    f = pnoise(rng, sx=0.9, sy=0.7) + 0.5 * pnoise(rng, sx=2.5, sy=2.0)
    idx = quantize(f, [0.1, 0.22, 0.3, 0.26, 0.12])
    for _ in range(6):                       # fibres, mostly lying flat
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        dx = 1 if rng.random() < 0.5 else -1
        for i in range(int(rng.integers(3, 6))):
            idx[(y + (1 if i == 3 else 0)) % 16, (x + dx * i) % 16] = 5
    for _ in range(4):                       # pale rootlets
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        idx[y, x] = 6
        idx[(y + 1) % 16, (x + (1 if rng.random() < 0.5 else 0)) % 16] = 5
    return paint(idx, pal)


def sphagnum(rng):
    """Bog moss: green cushions shot through with the red of the hummock tops."""
    green = ['#2f4a1c', '#425f25', '#56742f', '#6b8a39', '#84a346', '#9cba5a']
    red = ['#4f1a1c', '#6b2426', '#8a3330', '#a6443a', '#bf5a46', '#d27a5c']
    tone = pnoise(rng, sx=1.3, sy=1.3) + 0.35 * pnoise(rng, sx=0.6, sy=0.6)
    f = pnoise(rng, sx=0.6, sy=0.6) + 0.4 * pnoise(rng, sx=1.2, sy=1.2)
    idx = quantize(f, [0.1, 0.2, 0.3, 0.24, 0.12, 0.04])
    img = blank()
    for y in range(16):
        for x in range(16):
            t = tone[y, x]
            pal = red if t > 0.35 else green
            c = pal[int(idx[y, x])]
            if -0.1 < t <= 0.35:
                c = lerp(green[int(idx[y, x])], red[int(idx[y, x])], 0.35 if t > 0.15 else 0.15)
            put(img, x, y, c)
    # bright capitula: little star heads catching the light
    for _ in range(10):
        x, y = (int(v) for v in rng.integers(0, 16, 2))
        put(img, x, y, red[5] if tone[y, x] > 0.35 else green[5])
    return img


# --------------------------------------------------------------------------
def generate(rng_for):
    out = []
    for w, p in WOODS.items():
        out.append((f'textures/block/{w}_log.png', BARK[w](rng_for(f'{w}_log'), p), True))
        out.append((f'textures/block/{w}_log_top.png', wood.log_top(rng_for(f'{w}_log_top'), w, p), False))
        out.append((f'textures/block/stripped_{w}_log.png', wood.stripped_side(rng_for(f'stripped_{w}_log'), w, p), True))
        out.append((f'textures/block/stripped_{w}_log_top.png', wood.log_top(rng_for(f'stripped_{w}_log_top'), w, p, stripped=True), False))
        out.append((f'textures/block/{w}_planks.png', wood.planks(rng_for(f'{w}_planks'), w, p), True))
        out.append((f'textures/block/{w}_leaves.png', LEAVES[w](rng_for(f'{w}_leaves')), True))
        out.append((f'textures/block/{w}_sapling.png', sapling(w), False))
        top, bottom, trap, item = make_doors(w, p['planks'], rng_for)
        out.append((f'textures/block/{w}_door_top.png', top, False))
        out.append((f'textures/block/{w}_door_bottom.png', bottom, False))
        out.append((f'textures/block/{w}_trapdoor.png', trap, False))
        out.append((f'textures/item/{w}_door.png', item, False))
    out.append(('textures/block/orange_maple_leaves.png', LEAVES['orange_maple'](rng_for('orange_maple_leaves')), True))
    out.append(('textures/block/larch_leaves.png', LEAVES['larch'](rng_for('larch_leaves')), True))
    out.append(('textures/block/larch_sapling.png', sapling('larch'), False))
    out.append(('textures/block/peat.png', peat(rng_for('peat')), True))
    out.append(('textures/block/sphagnum_moss.png', sphagnum(rng_for('sphagnum_moss')), True))
    for name, d in [('cotton_grass', COTTON_GRASS), ('cranberry_bush', CRANBERRY), ('reindeer_lichen', LICHEN),
                    ('fireweed_top', FIREWEED_TOP), ('fireweed_bottom', FIREWEED_BOTTOM), ('bluebells', BLUEBELLS)]:
        out.append((f'textures/block/{name}.png', from_ascii(d['art'], d['pal']), False))
    out.append(('textures/block/bluebells_stem.png', stem_texture(), False))
    out.append(('textures/item/bluebells.png', from_ascii(BLUEBELL_ITEM['art'], BLUEBELL_ITEM['pal']), False))
    out.append(('textures/item/cranberries.png', outline(from_ascii(CRANBERRIES_ITEM['art'], CRANBERRIES_ITEM['pal'])), False))
    return out
