"""Item sprites: food (hand-drawn maps) and two-tone spotted spawn eggs.
Every sprite gets a 1px outline in a darker shade of its neighbouring colour."""
import numpy as np

from texlib import blank, from_ascii, hexc, outline, put, shade


def _spans(spans):
    """{row: [(x0, x1), ...]} -> bool mask"""
    m = np.zeros((16, 16), bool)
    for y, segs in spans.items():
        for (x0, x1) in segs:
            m[y, x0:x1 + 1] = True
    return m


def _emboss(mask, light, mid, dark, deep=None):
    """Shade a silhouette lit from the upper-left: lit rim, mid body, dark
    lower-right rim (and an optional deeper second rim)."""
    img = blank()
    h, w = mask.shape

    def inside(x, y):
        return 0 <= x < w and 0 <= y < h and mask[y, x]
    for y in range(h):
        for x in range(w):
            if not mask[y, x]:
                continue
            c = mid
            if not inside(x + 1, y) or not inside(x, y + 1) or not inside(x + 1, y + 1):
                c = deep if (deep and (not inside(x + 1, y) and not inside(x, y + 1))) else dark
            elif not inside(x - 1, y) or not inside(x, y - 1):
                c = light
            put(img, x, y, c, wrap=False)
    return img


STEAK = _spans({
    3: [(7, 10)],
    4: [(5, 11)],
    5: [(4, 12)],
    6: [(3, 13)],
    7: [(2, 13)],
    8: [(2, 13)],
    9: [(2, 13)],
    10: [(2, 12)],
    11: [(3, 12)],
    12: [(4, 11)],
    13: [(6, 9)],
})
# fat cap along the upper-left edge and two marbling streaks
FAT_CAP = [(7, 3), (8, 3), (9, 3), (5, 4), (6, 4), (4, 5), (3, 6), (2, 7)]
MARBLE = [(4, 8), (5, 8), (6, 9), (7, 9), (8, 9), (9, 10), (10, 10),
          (8, 6), (9, 6), (10, 7), (6, 11), (7, 11)]


def steak(cols, cooked=False):
    img = _emboss(STEAK, cols['h'], cols['R'], cols['r'], cols['d'])
    # an inner band of mid-dark towards the lower right gives the slab volume
    for y in range(16):
        for x in range(16):
            if STEAK[y, x] and x + y >= 19 and img[y, x, 0] == hexc(cols['R'])[0]:
                put(img, x, y, cols['r'], wrap=False)
    for (x, y) in FAT_CAP:
        put(img, x, y, cols['F'], wrap=False)
    for i, (x, y) in enumerate(MARBLE):
        put(img, x, y, cols['F'] if i % 3 else cols['f'], wrap=False)
    if cooked:
        # two seared grill bands (solid diagonals)
        for c0 in (11, 17):
            for y in range(16):
                for x in range(16):
                    if STEAK[y, x] and (x + y == c0 or x + y == c0 + 1) and (x, y) not in FAT_CAP:
                        put(img, x, y, cols['c'], wrap=False)
    return outline(img, 0.55)


VENISON_RAW = {'h': '#de4f57', 'R': '#b3222f', 'r': '#8e1926', 'd': '#66111b',
               'F': '#f4e4dd', 'f': '#dcb7b0'}
VENISON_COOKED = {'h': '#b8723f', 'R': '#8f4f2a', 'r': '#723a1c', 'd': '#552812',
                  'F': '#e3bb8a', 'f': '#bf9060', 'c': '#3f1d0c'}

CRAB_MEAT = [
    "................",
    "................",
    "................",
    "...........pP...",
    ".........pPPPp..",
    ".......pPPPPPPp.",
    "......PPrPPWWWw.",
    ".....PrPPWWWwWv.",
    "....pPPWWWwWWwv.",
    "...pPWWWwWWwWvv.",
    "...PWWwWWwWWvv..",
    "..pWWWWwWWwvv...",
    "..WwWWwWwvvv....",
    "..wWwwwvvv......",
    "...vvvv.........",
    "................",
]
CRAB_RAW = {'W': '#fbf3ee', 'w': '#ead5cb', 'v': '#cdaea3', 'P': '#f4a499',
            'p': '#dd776d', 'r': '#c4483e'}

CLAW = _spans({
    1: [(8, 8)],
    2: [(7, 8), (13, 13)],
    3: [(6, 8), (12, 13)],
    4: [(6, 8), (12, 14)],
    5: [(5, 8), (11, 13)],
    6: [(5, 8), (11, 13)],
    7: [(4, 13)],
    8: [(3, 13)],
    9: [(3, 13)],
    10: [(3, 12)],
    11: [(3, 12)],
    12: [(3, 11)],
    13: [(2, 9)],
    14: [(1, 3)],
    15: [(0, 1)],
})
CRAB_COOKED = {'H': '#ffa45e', 'O': '#ee6629', 'o': '#c9471c', 'r': '#9c2e17',
               'k': '#4f140a', 's': '#ffd9b0'}


def cooked_crab():
    c = CRAB_COOKED
    img = _emboss(CLAW, c['H'], c['O'], c['o'], c['r'])
    for (x, y) in ((8, 1), (13, 2), (7, 2), (12, 3)):
        put(img, x, y, c['k'] if y < 3 or x == 8 else c['r'], wrap=False)
    # wrist joint
    for (x, y) in ((3, 13), (4, 13), (2, 14)):
        put(img, x, y, c['r'], wrap=False)
    # bumpy shell speckles
    for (x, y) in ((5, 9), (8, 8), (6, 11), (10, 10), (7, 4)):
        put(img, x, y, c['s'], wrap=False)
    return outline(img, color='#4a1209')


def food():
    out = []
    out.append(('textures/item/venison.png', steak(VENISON_RAW)))
    out.append(('textures/item/cooked_venison.png', steak(VENISON_COOKED, cooked=True)))
    out.append(('textures/item/crab_meat.png', outline(from_ascii(CRAB_MEAT, CRAB_RAW), 0.6)))
    out.append(('textures/item/cooked_crab.png', cooked_crab()))
    return out


# --------------------------------------------------------------------------
# spawn eggs
# --------------------------------------------------------------------------
def egg_mask():
    m = np.zeros((16, 16), bool)
    cy, b = 8.6, 6.6
    for y in range(16):
        for x in range(16):
            X, Y = x + 0.5 - 8.0, y + 0.5 - cy
            a = 4.75 + 1.0 * (Y / b)
            if (X / a) ** 2 + (Y / b) ** 2 <= 1.0:
                m[y, x] = True
    return m


EGGS = {
    'elk': dict(base='#7a5233', spot='#e2cfa6',
                spots=[(5, 4, 2), (9, 6, 1), (6, 9, 3), (10, 10, 2), (4, 12, 1), (8, 12, 1)]),
    'mammoth': dict(base='#4a3222', spot='#efe6cf', shaggy=True,
                    spots=[(6, 4, 1), (9, 7, 2), (5, 8, 2), (8, 11, 3), (11, 11, 1)]),
    'capybara': dict(base='#a0764a', spot='#5a3b22',
                     spots=[(7, 4, 1), (5, 7, 2), (9, 8, 3), (6, 11, 2), (10, 12, 1)]),
    'crab': dict(base='#c8462c', spot='#e8c48a',
                 spots=[(6, 4, 2), (9, 5, 1), (4, 8, 1), (8, 8, 3), (5, 11, 2), (10, 11, 2)]),
}
SPOT_SHAPES = {
    1: [(0, 0), (1, 0)],
    2: [(0, 0), (1, 0), (0, 1), (1, 1)],
    3: [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (2, 2)],
}


def spawn_egg(kind, rng):
    d = EGGS[kind]
    m = egg_mask()
    base = hexc(d['base'])
    spot = hexc(d['spot'])
    img = blank()
    cy = 8.6
    for y in range(16):
        for x in range(16):
            if not m[y, x]:
                continue
            X, Y = x + 0.5 - 8.0, y + 0.5 - cy
            l = -0.55 * X / 5.0 - 0.8 * Y / 6.6
            c = base
            if l > 0.5:
                c = shade(base, 1.18)
            elif l < -0.45:
                c = shade(base, 0.8)
            put(img, x, y, c, wrap=False)
    spot_px = set()
    for (sx, sy, k) in d['spots']:
        for (dx, dy) in SPOT_SHAPES[k]:
            x, y = sx + dx, sy + dy
            if (0 < x < 15 and 0 < y < 15 and m[y, x] and m[y, x + 1] and m[y, x - 1]
                    and m[y + 1, x] and m[y - 1, x]):
                spot_px.add((x, y))
    for (x, y) in spot_px:
        lower_right = (x + 1, y) not in spot_px or (x, y + 1) not in spot_px
        upper_left = (x - 1, y) not in spot_px or (x, y - 1) not in spot_px
        c = spot
        if lower_right and not upper_left:
            c = shade(spot, 0.86)
        put(img, x, y, c, wrap=False)
    if d.get('shaggy'):
        dark = shade(base, 0.72)
        for (x, y) in ((4, 9), (4, 10), (6, 12), (6, 13), (9, 13), (11, 9), (11, 10), (7, 6),
                       (10, 4), (12, 12), (3, 11)):
            if m[y, x] and (x, y) not in spot_px:
                put(img, x, y, dark, wrap=False)
    # glossy highlight
    for (x, y) in ((5, 3), (4, 4), (4, 5)):
        if m[y, x] and (x, y) not in spot_px:
            put(img, x, y, shade(base, 1.4), wrap=False)
    return outline(img, 0.5)


def generate(rng_for):
    out = [(rel, img, False) for rel, img in food()]
    for kind in ('elk', 'mammoth', 'capybara', 'crab'):
        out.append((f'textures/item/{kind}_spawn_egg.png', spawn_egg(kind, rng_for(f'{kind}_spawn_egg')), False))
    return out
