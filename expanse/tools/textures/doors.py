"""Doors (16x32 split into top/bottom), trapdoors and door item icons.

Designs are drawn as *part maps* (one character per pixel naming a part:
frame, panel, seam, window, metal, accent ...) with a few drawing
primitives, then shaded with the wood's plank palette plus grain noise.
"""
import math

import numpy as np

from texlib import blank, pnoise, put

# part characters ----------------------------------------------------------
#  O  outer edge / deepest seam        s  board seam (deep)
#  F  frame / rail (light, grain)      f  shadow line
#  h  highlight line                   P  panel, vertical grain
#  Q  panel, horizontal grain          .  transparent
#  M m  metal light / dark             K k  handle light / dark
#  A a B  accent light / mid / dark    L  accent leaf green
#  G g  glow light / dark (lumen)


class Grid:
    def __init__(self, w, h, fill='P'):
        self.w, self.h = w, h
        self.g = [[fill] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.g[y][x] = c

    def get(self, x, y):
        return self.g[y][x]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def hline(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def border(self, x0, y0, x1, y1, c):
        self.hline(x0, x1, y0, c)
        self.hline(x0, x1, y1, c)
        self.vline(x0, y0, y1, c)
        self.vline(x1, y0, y1, c)

    def recess(self, x0, y0, x1, y1, inner='P'):
        """Sunken panel: shadow on the top/left inner edge, light bottom/right."""
        self.rect(x0, y0, x1, y1, inner)
        self.hline(x0, x1, y0, 'f')
        self.vline(x0, y0, y1, 'f')
        self.hline(x0 + 1, x1, y1, 'h')
        self.vline(x1, y0 + 1, y1, 'h')

    def ascii(self, rows, x0=0, y0=0, skip=' '):
        for yy, r in enumerate(rows):
            for xx, ch in enumerate(r):
                if ch != skip:
                    self.set(x0 + xx, y0 + yy, ch)

    def rows(self):
        return [''.join(r) for r in self.g]


METALS = {
    'iron': ['#d8d8d8', '#a8a8a8', '#6e6e6e', '#4a4a4a'],
    'dark_iron': ['#9a9a9e', '#6f6f74', '#4c4c52', '#323237'],
    'brass': ['#f0d27a', '#c99d45', '#94702c', '#5e451a'],
    'silver': ['#f4fbfb', '#c9d6d9', '#93a6ab', '#6b7d82'],
}


def render(grid, planks, rng, metal='iron', accent=None, glow=None, vgrain=True):
    w, h = grid.w, grid.h
    nv = pnoise(rng, w, h, sx=0.45, sy=3.0)
    nh = pnoise(rng, w, h, sx=3.0, sy=0.45)
    met = METALS[metal]
    img = blank(w, h)
    for y in range(h):
        for x in range(w):
            c = grid.g[y][x]
            col = None
            if c == 'O' or c == 's':
                col = planks[0]
            elif c == 'f':
                col = planks[1]
            elif c == 'h':
                col = planks[4]
            elif c == 'F':
                n = nh[y, x] if not vgrain else nv[y, x]
                col = planks[3] if n < 1.0 else planks[4]
                if n < -1.2:
                    col = planks[2]
            elif c == 'P':
                n = nv[y, x]
                col = planks[2] if -0.9 < n < 1.1 else (planks[1] if n <= -0.9 else planks[3])
            elif c == 'Q':
                n = nh[y, x]
                col = planks[2] if -0.9 < n < 1.1 else (planks[1] if n <= -0.9 else planks[3])
            elif c in 'Mm':
                col = met[1] if c == 'M' else met[2]
            elif c in 'Kk':
                col = met[0] if c == 'K' else met[3]
            elif c in 'AaBL' and accent:
                col = accent['AaBL'.index(c)]
            elif c in 'Gg' and glow:
                col = glow[0] if c == 'G' else glow[1]
            if col is not None:
                put(img, x, y, col, wrap=False)
    return img


# ==========================================================================
# full doors (16 x 32)
# ==========================================================================
def door_redwood():
    """Cabin door: three vertical boards, two-pane window, rails, Z-brace,
    long iron strap hinges."""
    g = Grid(16, 32, 'P')
    for x in (5, 10):
        g.vline(x, 1, 30, 's')
    g.border(0, 0, 15, 31, 'O')
    # top rail + window block
    g.rect(1, 1, 14, 9, 'F')
    g.rect(3, 3, 6, 7, '.')
    g.rect(9, 3, 12, 7, '.')
    g.hline(1, 14, 10, 'f')
    # middle rail
    g.rect(1, 17, 14, 18, 'F')
    g.hline(1, 14, 19, 'f')
    # bottom rail
    g.rect(1, 28, 14, 29, 'F')
    g.hline(1, 14, 30, 'f')
    # Z brace between middle and bottom rail
    for r in range(20, 28):
        c = 13 - (r - 20) * 10 // 7
        g.set(c, r, 'F')
        g.set(c - 1, r, 'F')
        g.set(c + 1, r, 'f') if g.get(c + 1, r) != 'O' else None
    # strap hinges + handle
    for y in (12, 24):
        g.hline(0, 4, y, 'M')
        g.hline(1, 4, y + 1, 'm')
        g.set(3, y, 'K')
    g.rect(12, 14, 13, 15, 'K')
    g.set(13, 15, 'k')
    g.set(12, 16, 'k')
    return g


def _lattice(g, x0, y0, x1, y1):
    """Diagonal lattice window: 1px slats on both diagonals (period 6) so the
    openings are clean diamonds."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            u, v = (x - x0) + (y - y0), (x - x0) - (y - y0)
            g.set(x, y, 'F' if (u % 6 == 0 or v % 6 == 0) else '.')
    g.border(x0, y0, x1, y1, 'F')
    g.hline(x0, x1, y0, 'f')
    g.vline(x0, y0, y1, 'f')


def door_willow():
    """Diamond-lattice window over two tall sunken panels."""
    g = Grid(16, 32, 'F')
    g.border(0, 0, 15, 31, 'O')
    _lattice(g, 2, 2, 13, 13)
    g.hline(2, 13, 14, 'h')
    g.recess(3, 17, 7, 28)
    g.recess(9, 17, 13, 28)
    g.hline(1, 14, 30, 'f')
    for y in (4, 25):
        g.rect(0, y, 1, y + 1, 'M')
        g.set(1, y + 1, 'm')
    g.rect(12, 15, 13, 16, 'K')
    g.set(13, 16, 'k')
    return g


def door_palm():
    """Tropical louvred door: open slats above, closed slats below."""
    g = Grid(16, 32, 'F')
    g.border(0, 0, 15, 31, 'O')
    for y in range(3, 14):
        k = (y - 3) % 3
        g.hline(3, 12, y, ['h', 'Q', '.'][k])
    g.hline(3, 12, 2, 'f')
    for y in range(18, 29):
        k = (y - 18) % 3
        g.hline(3, 12, y, ['h', 'Q', 'f'][k])
    g.hline(3, 12, 17, 'f')
    g.vline(2, 2, 13, 'f')
    g.vline(2, 17, 28, 'f')
    g.hline(1, 14, 30, 'f')
    g.rect(1, 14, 14, 16, 'F')
    g.hline(2, 13, 14, 'h')
    for y in (5, 24):
        g.rect(0, y, 1, y + 1, 'M')
        g.set(1, y + 1, 'm')
    g.rect(12, 15, 13, 16, 'K')
    g.set(13, 16, 'k')
    return g


def door_lumen():
    """Pale door with a tall arched window and a glowing vein inlay."""
    g = Grid(16, 32, 'F')
    g.border(0, 0, 15, 31, 'O')
    arch = [(6, 9), (5, 10), (4, 11)]
    for i, (a, b) in enumerate(arch):
        g.hline(a, b, 3 + i, '.')
    for y in range(6, 14):
        g.hline(4, 11, y, '.')
    # glowing trim around the arch
    trim = [(6, 2), (7, 2), (8, 2), (9, 2), (5, 3), (10, 3), (4, 4), (11, 4), (3, 5), (12, 5)]
    for (x, y) in trim:
        g.set(x, y, 'G')
    for y in range(6, 14):
        g.set(3, y, 'g')
        g.set(12, y, 'g')
    g.hline(3, 12, 14, 'h')
    g.set(7, 1, 'G')
    g.set(8, 1, 'G')
    # lower panel with vein inlay
    g.recess(3, 17, 12, 28, 'P')
    # glowing sprig inlay (alternate branches swept upward)
    for y in range(19, 28):
        g.set(7, y, 'g')
    for (x, y) in ((6, 21), (5, 20), (8, 23), (9, 22), (6, 25), (5, 24), (8, 26), (9, 25)):
        g.set(x, y, 'g')
    for (x, y) in ((7, 18), (5, 20), (9, 22), (5, 24), (9, 25)):
        g.set(x, y, 'G')
    g.hline(1, 14, 30, 'f')
    for y in (5, 25):
        g.rect(0, y, 1, y + 1, 'M')
        g.set(1, y + 1, 'm')
    g.rect(12, 15, 13, 16, 'K')
    g.set(13, 16, 'k')
    return g


def door_baobab():
    """Heavy door of horizontal planks with a round porthole and ring pull."""
    g = Grid(16, 32, 'Q')
    for y in range(4, 32, 5):
        g.hline(1, 14, y, 's')
    g.border(0, 0, 15, 31, 'O')
    g.vline(1, 1, 30, 'F')
    g.vline(14, 1, 30, 'F')
    cx, cy = 7.5, 8.0
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d < 3.4:
                g.set(x, y, '.')
            elif d < 4.7:
                up = (x - cx) + (y - cy) < 0
                g.set(x, y, 'f' if up and d > 4.1 else ('h' if (not up and d > 4.1) else 'F'))
    # strap hinges
    for y in (3, 26):
        g.hline(0, 5, y, 'M')
        g.hline(1, 5, y + 1, 'm')
    # iron ring pull hanging from a small plate, just below the mid rail
    g.rect(11, 17, 12, 17, 'm')
    ring = [" MM ", "M..m", "M..m", " mm "]
    g.ascii(ring, 10, 18)
    return g


def door_wisteria():
    """Twin pointed-arch windows; carved panel with inlaid wisteria blooms."""
    g = Grid(16, 32, 'F')
    g.border(0, 0, 15, 31, 'O')
    for x0 in (3, 9):
        g.hline(x0 + 1, x0 + 2, 3, '.')
        for y in range(4, 13):
            g.hline(x0, x0 + 3, y, '.')
        g.set(x0 + 1, 2, 'f')
        g.set(x0 + 2, 2, 'f')
        g.set(x0, 3, 'f')
        g.set(x0 + 3, 3, 'f')
        g.hline(x0, x0 + 3, 13, 'h')
    # small blossom carving between the arches
    g.set(7, 2, 'A')
    g.set(8, 2, 'a')
    g.set(7, 3, 'a')
    g.set(8, 3, 'B')
    g.recess(3, 17, 12, 28, 'P')
    # two hanging racemes on the panel
    for (x0, L) in ((4, 7), (9, 6)):
        g.set(x0 + 1, 18, 'L')
        for r in range(L):
            wdt = 3 if r < 3 else (2 if r < L - 1 else 1)
            for c in range(wdt):
                col = 'A' if r < 2 else ('a' if r < 4 else 'B')
                if c == wdt - 1 and wdt > 1 and col == 'A':
                    col = 'a'
                g.set(x0 + c + (1 if wdt == 1 else 0), 19 + r, col)
    g.set(7, 18, 'L')
    g.set(8, 18, 'L')
    g.hline(1, 14, 30, 'f')
    for y in (5, 25):
        g.rect(0, y, 1, y + 1, 'M')
        g.set(1, y + 1, 'm')
    g.rect(12, 15, 13, 16, 'K')
    g.set(13, 16, 'k')
    return g


# ==========================================================================
# trapdoors (16 x 16)
# ==========================================================================
def trap_redwood():
    g = Grid(16, 16, 'P')
    for x in (5, 10):
        g.vline(x, 1, 14, 's')
    g.border(0, 0, 15, 15, 'O')
    g.rect(1, 1, 14, 2, 'F')
    g.hline(1, 14, 3, 'f')
    g.rect(1, 12, 14, 13, 'F')
    g.hline(1, 14, 14, 'f')
    for x in (3, 7, 12):
        g.rect(x, 5, x + 1 if x != 12 else x, 10, '.')
    g.rect(2, 1, 3, 2, 'M')
    g.rect(2, 12, 3, 13, 'M')
    g.set(3, 2, 'm')
    g.set(3, 13, 'm')
    return g


def trap_willow():
    g = Grid(16, 16, 'F')
    g.border(0, 0, 15, 15, 'O')
    _lattice(g, 2, 2, 13, 13)
    return g


def trap_palm():
    g = Grid(16, 16, 'F')
    g.border(0, 0, 15, 15, 'O')
    for y in range(3, 13):
        k = (y - 3) % 3
        g.hline(3, 12, y, ['h', 'Q', '.'][k])
    g.hline(3, 12, 2, 'f')
    g.vline(2, 2, 12, 'f')
    g.hline(2, 13, 13, 'h')
    return g


def trap_lumen():
    g = Grid(16, 16, 'F')
    g.border(0, 0, 15, 15, 'O')
    g.recess(2, 2, 13, 13, 'P')
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d < 2.6:
                g.set(x, y, '.')
            elif d < 3.5:
                g.set(x, y, 'g' if (x + y) % 2 else 'G')
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        g.set(x, y, '.')
        g.set(x + (1 if x < 8 else -1), y, 'g')
        g.set(x, y + (1 if y < 8 else -1), 'g')
    return g


def trap_baobab():
    g = Grid(16, 16, 'Q')
    for y in (3, 7, 11):
        g.hline(1, 14, y, 's')
    g.border(0, 0, 15, 15, 'O')
    g.vline(1, 1, 14, 'F')
    g.vline(14, 1, 14, 'F')
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d < 2.6:
                g.set(x, y, '.')
            elif d < 3.9:
                up = (x - cx) + (y - cy) < 0
                g.set(x, y, 'f' if (up and d > 3.3) else ('h' if (not up and d > 3.3) else 'F'))
    g.rect(1, 1, 3, 1, 'M')
    g.rect(1, 14, 3, 14, 'M')
    return g


def trap_wisteria():
    g = Grid(16, 16, 'F')
    g.border(0, 0, 15, 15, 'O')
    g.recess(2, 2, 13, 13, 'P')
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            for (px, py) in ((cx - 2.3, cy), (cx + 2.3, cy), (cx, cy - 2.3), (cx, cy + 2.3)):
                if math.hypot(x - px, y - py) < 1.75:
                    g.set(x, y, '.')
    g.rect(7, 7, 8, 8, 'a')
    g.set(7, 7, 'A')
    g.set(8, 8, 'B')
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        g.set(x, y, 'a')
    return g


# ==========================================================================
# door items (10 x 14 footprint inside 16 x 16, like vanilla)
# ==========================================================================
ITEMS = {
    'redwood': [
        "OFFFFFFFFf",
        "OF..FF..Ff",
        "OF..FF..Ff",
        "OF..FF..Ff",
        "OFFFFFFFFf",
        "OffffffffO",
        "MMPPsPPPsf",
        "OPPPsPPFFf",
        "OPPPsPFFKf",
        "OPPPsFFPsf",
        "OPPPFFPPsf",
        "MMPFFPPPsf",
        "OFFFFFFFFf",
        "OOOOOOOOOO",
    ],
    'willow': [
        "OfffffffFf",
        "Of.F..F.Ff",
        "Of..FF..Ff",
        "Of..FF..Ff",
        "Of.F..F.Ff",
        "OhhhhhhhhO",
        "OFFFFFFKFf",
        "OfPPhfPPhf",
        "OfPPhfPPhf",
        "OfPPhfPPhf",
        "OfPPhfPPhf",
        "OFhhhFhhhf",
        "OFFFFFFFFf",
        "OOOOOOOOOO",
    ],
    'palm': [
        "OFFFFFFFFf",
        "OfhhhhhhFf",
        "Of......Ff",
        "OfhhhhhhFf",
        "Of......Ff",
        "OFFFFFFFFf",
        "OFFFFFFKFf",
        "OfhhhhhhFf",
        "OfQQQQQQFf",
        "OfhhhhhhFf",
        "OfQQQQQQFf",
        "OfhhhhhhFf",
        "OFFFFFFFFf",
        "OOOOOOOOOO",
    ],
    'lumen': [
        "OFFGGGGFFf",
        "OFG....GFf",
        "OFg....gFf",
        "OFg....gFf",
        "OFg....gFf",
        "OFhhhhhhFf",
        "OFFFFFFKFf",
        "OfPPGPPPhf",
        "OfPGgPPPhf",
        "OfPPgGPPhf",
        "OfPPgPPPhf",
        "OFhhhhhhhf",
        "OFFFFFFFFf",
        "OOOOOOOOOO",
    ],
    'baobab': [
        "OFQQQQQQFf",
        "OFQf..fQFf",
        "OFf....hFf",
        "OFf....hFf",
        "OFQh..hQFf",
        "OsssssssFf",
        "OFQQQQQmQf",
        "OFQQQQMQmf",
        "OFQQQQQmQf",
        "OsssssssFf",
        "MMMQQQQQFf",
        "OFQQQQQQFf",
        "OFQQQQQQFf",
        "OOOOOOOOOO",
    ],
    'wisteria': [
        "OFFFFFFFFf",
        "OF.FAF.FFf",
        "O...F...Ff",
        "O...F...Ff",
        "O...F...Ff",
        "OhhhFhhhFf",
        "OFFFFFFKFf",
        "OfLPPPLPhf",
        "OfAaPPAahf",
        "OfaBPPaPhf",
        "OfBPPPBPhf",
        "OFhhhhhhhf",
        "OFFFFFFFFf",
        "OOOOOOOOOO",
    ],
}


def door_item(w, planks, rng, metal, accent, glow):
    g = Grid(16, 16, ' ')
    g.ascii(ITEMS[w], 3, 2, skip='\0')
    for y in range(16):
        for x in range(16):
            if g.g[y][x] == ' ':
                g.g[y][x] = '.'
    return render(g, planks, rng, metal, accent, glow)


DOORS = dict(redwood=door_redwood, willow=door_willow, palm=door_palm,
             lumen=door_lumen, baobab=door_baobab, wisteria=door_wisteria)
TRAPS = dict(redwood=trap_redwood, willow=trap_willow, palm=trap_palm,
             lumen=trap_lumen, baobab=trap_baobab, wisteria=trap_wisteria)
STYLE = {
    'redwood': dict(metal='dark_iron'),
    'willow': dict(metal='iron'),
    'palm': dict(metal='brass'),
    'lumen': dict(metal='silver', glow=['#8ff5e6', '#3fc7b8']),
    'baobab': dict(metal='dark_iron'),
    'wisteria': dict(metal='iron', accent=['#d7b8f5', '#a77fd8', '#6a3fa0', '#5f8a3e']),
}


def make(w, planks, rng_for):
    st = STYLE[w]
    kw = dict(metal=st['metal'], accent=st.get('accent'), glow=st.get('glow'))
    full = render(DOORS[w](), planks, rng_for(f'{w}_door'), **kw)
    top, bottom = full[:16].copy(), full[16:].copy()
    trap = render(TRAPS[w](), planks, rng_for(f'{w}_trapdoor'), **kw)
    item = door_item(w, planks, rng_for(f'{w}_door_item'), **kw)
    return top, bottom, trap, item
