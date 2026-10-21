# Speech perks: the right word, the right price.  Trader (coin) and Courtier (people); honey and gold accents.
import math
from perkkit import Cv, arc, disc, GOLD_INGOT

# gold tones by digit, for the hand-shaded coin (dark -> light)
GOLD = {'0': ('gold', 0), '1': ('gold', 1), '2': ('gold', 2), '3': ('gold', 3), '4': ('gold', 4)}

ICONS = {}


def stack(c, ox, oy, n, w=6):
    """A stack of n coins seen side-on, top coin at oy; each coin is two rows."""
    for i in range(n):
        y = oy + i * 2
        c.rows(["'" * w], ox, y)
        c.rows(['g' * w], ox, y + 1)
    c.put(ox, oy, 'y')


def bubble(c, ox, oy, w, h, ch='Q', tail='left'):
    """A rounded speech bubble w x h with a small tail under it."""
    for y in range(h):
        inset = 1 if y in (0, h - 1) else 0
        c.rect(ox + inset, oy + y, ox + w - 1 - inset, oy + y, ch)
    if tail == 'left':
        c.pts([(ox + 2, oy + h), (ox + 3, oy + h), (ox + 1, oy + h + 1), (ox + 2, oy + h + 1)], ch)
        c.put(ox + 1, oy + h + 1, ch)
    elif tail == 'right':
        x1 = ox + w - 1
        c.pts([(x1 - 3, oy + h), (x1 - 2, oy + h), (x1 - 2, oy + h + 1), (x1 - 1, oy + h + 1)], ch)


def big_coin(cx, cy, r):
    """A large coin face-on, hand-shaded: lit rim, a sunken ring inside it, a raised boss in the middle."""
    c = Cv()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            k = (dx + dy) / (d or 1)
            if d > r - 1.25:
                t = '4' if k < -0.6 else '1' if k > 0.6 else '3' if k < 0.3 else '2'
            elif d > r - 2.3:
                t = '1' if k < 0 else '3'
            else:
                t = '2'
            c.put(x, y, t)
    x0, y0 = int(cx - 1), int(cy - 1)
    c.pts([(x0, y0)], '4')
    c.pts([(x0 + 1, y0), (x0, y0 + 1)], '3')
    c.pts([(x0 + 1, y0 + 1)], '1')
    return (c, GOLD)


def ring(c, cx, cy, r0, r1, ch):
    for y in range(16):
        for x in range(16):
            if r0 <= math.hypot(x - cx, y - cy) <= r1:
                c.put(x, y, ch)


def sweep(c, pts, widths, ch):
    """A tapering tube: discs of the given half-widths along a polyline."""
    for y in range(16):
        for x in range(16):
            for (px_, py_), w in zip(pts, widths):
                if math.hypot(x + 0.5 - px_, y + 0.5 - py_) <= w:
                    c.put(x, y, ch)
                    break


# ---------------------------------------------------------------- roots
# Haggler: a coin, and the price coming down
c = big_coin(5.5, 7.5, 5.5)
a = Cv()
a.rows([
    '  FF  ',
    '  FF  ',
    '  FF  ',
    '  FF  ',
    'FFFFFF',
    ' FFFF ',
    '  FF  ',
], 10, 5)
a.pts([(12, 5), (12, 6), (12, 7), (12, 8)], 'u')
ICONS['haggler'] = [c, a]

# Glib: a speech bubble with a golden flourish
c = Cv()
bubble(c, 0, 1, 15, 9, 'Q', 'none')
c.rows(['QQQ', 'QQ ', 'Q  '], 2, 10)
c.rows([
    '    ggg    ',
    '   g   g gg',
    '  g  g g   ',
    ' g    g    ',
    'g          ',
], 2, 3)
c.rows(['  ^  ', ' ^^^ ', '  ^  '], 11, 11)
ICONS['glib'] = c

# ---------------------------------------------------------------- path A: Trader
# Provisioner: a loaf and a bottle
bo = Cv()
bo.rows([
    ' W ',
    ' E ',
    ' E ',
    'EEE',
    'EEE',
    'EEE',
    'EEE',
    'EEE',
    'EEE',
], 11, 2)
bo.rows([' WW ', ' ee ', ' ee ', 'EEEE', 'EaEE', 'EaEE', 'EEEE', 'EEEE', 'EEEE', 'eeee'], 11, 1)
br = Cv()
br.rows([
    '   AAAAAA   ',
    ' AAAAAAAAAA ',
    'AAmAAAmAAAmA',
    'AAAmAAAmAAAm',
    'AAAAAAAAAAAA',
    ' AAAAAAAAAA ',
], 0, 8)
ICONS['provisioner'] = [bo, br]

# Appraiser: a brass lens over a gem
c = Cv()
ring(c, 6, 6, 3.7, 5.3, 'G')
disc(c, 6, 6, 3.6, 'a')
c.rows([
    ' EEEE ',
    'EaEEEe',
    ' EEEe ',
    '  ee  ',
], 3, 4)
c.pts([(3, 3), (4, 2), (3, 4)], '*')
c.line(10, 10, 14, 14, 'W')
c.line(11, 10, 15, 14, 'W')
c.line(10, 11, 14, 15, 'W')
c.pts([(10, 10)], 'G')
ICONS['appraiser'] = c

# Ledger-Keeper: an open ledger and a quill
bk = Cv()
bk.rows([
    ' QQQQQQ QQQQQQ ',
    'QQQQQQQ QQQQQQQ',
    'QOOOOQQ QOOOOQQ',
    'QQQQQQQ QQQQQQQ',
    'QOOOQQQ QOOOOQQ',
    'QQQQQQQ QQQQQQQ',
    'QOOOOQQ QOOQQQQ',
    'LLLLLLLLLLLLLLL',
], 0, 7)
bk.pts([(7, 8), (7, 9), (7, 10), (7, 11), (7, 12), (7, 13)], 'O')
bk.pts([(11, 13), (12, 13)], 'G')
q = Cv()
q.rows([
    '      U',
    '     UU',
    '    UUo',
    '   UUo ',
    '  UUo  ',
    ' Uo    ',
    'x      ',
], 8, 0)
ICONS['ledger_keeper'] = [bk, q]

# Shrewd Eye: a narrowed, weighing eye with a gold coin for its pupil
c = Cv()
c.rows([
    '    KKKKKKKK    ',
    '  KKKKKKKKKKKK  ',
    ' KKUUGGGGGGUUKK ',
    'KUUUGGyyGGGgUUUK',
    'KUUUGGyGGGGgUUUK',
    ' KUUUGGGGGggUUK ',
    '  KKUUGggggUUKK ',
    '    KKUUUUKK    ',
    '      KKKK      ',
], 0, 4)
ICONS['shrewd_eye'] = c

# ---------------------------------------------------------------- path B: Courtier
# Charmer: a red rose
c = Cv()
c.rows([
    '   RRRR  ',
    '  RnRRRR ',
    ' RRrrrRRR',
    ' RRrRRrRR',
    ' RRRrrRRR',
    '  RRRRRR ',
    '   RRRR  ',
], 3, 0)
s = Cv()
s.rows([
    '  uFFu  ',
    '   FF   ',
    '   F    ',
    'FF F    ',
    'FuFF    ',
    ' FFF  F ',
    '   FFFF ',
    '   F  u ',
    '   F    ',
], 3, 7)
ICONS['charmer'] = [s, c]

# Barterer: a gold ingot for an emerald, and the trade going both ways
c = Cv()
c.rows(GOLD_INGOT, -1, 3)
c.rows([
    '  uu  ',
    ' uFFF ',
    'uFFFFF',
    'FFFFFf',
    ' FFFf ',
    '  ff  ',
], 9, 9)
c.rows(['^^^^ ', '   ^^', '   ^ '], 9, 1)
c.rows([' ^   ', '^^   ', ' ^^^^'], 2, 11)
ICONS['barterer'] = c

# Commanding Voice: a war horn, and the call going out
c = Cv()
P = []
for i in range(25):
    t = i / 24
    # a crescent: mouthpiece up on the left, sagging low, the bell lifted on the right
    x = (1 - t) ** 2 * 1.5 + 2 * (1 - t) * t * 3.5 + t * t * 11.5
    y = (1 - t) ** 2 * 4.5 + 2 * (1 - t) * t * 15.0 + t * t * 10.0
    P.append((x, y))
sweep(c, P, [0.8 + 1.8 * (i / 24) ** 1.5 for i in range(25)], '@')
# the bell mouth, tilted to face up and right
bx, by = 12.3, 9.0
ca, sa = math.cos(math.radians(-35)), math.sin(math.radians(-35))
for y in range(16):
    for x in range(16):
        dx, dy = x + 0.5 - bx, y + 0.5 - by
        u, v = dx * ca + dy * sa, -dx * sa + dy * ca
        if (u / 1.7) ** 2 + (v / 3.4) ** 2 <= 1:
            c.put(x, y, '-' if (u / 0.8) ** 2 + (v / 2.3) ** 2 <= 1 else '$')
# brass mouthpiece and a brass band round the middle
for i in (0, 1):
    c.put(int(P[i][0]), int(P[i][1]), 'G')
c.put(1, 3, 'G')
x, y = P[13]
for ddx in (-1, 0):
    for ddy in (-2, -1, 0, 1, 2):
        if c.get(int(x) + ddx, int(y) + ddy) == '@':
            c.put(int(x) + ddx, int(y) + ddy, 'G')
c.rows([
    '   ~ ',
    ' ~  ~',
    '  ~  ',
    '~  ~ ',
], 10, 1)
ICONS['commanding_voice'] = c

# Second Word: a dim bubble, then a bright one
b1 = Cv()
bubble(b1, 0, 0, 10, 7, 'C', 'left')
b1.pts([(2, 3), (4, 3), (6, 3)], 'c')
b2 = Cv()
bubble(b2, 5, 6, 11, 7, 'U', 'right')
b2.pts([(8, 9), (10, 9), (12, 9)], 'G')
ICONS['second_word'] = [b1, b2]

# ---------------------------------------------------------------- masteries
# Merchant Prince: a crown over the coin stacks
cr = Cv()
cr.rows([
    'y   y   y',
    'G   G   G',
    'GG GGG GG',
    'GGGGGGGGG',
    'GRGGBGGRG',
    'ggggggggg',
], 3, 0)
st = Cv()
stack(st, 1, 9, 3, 6)
stack(st, 8, 7, 4, 7)
ICONS['merchant_prince'] = [st, cr]

# Hero's Welcome: a laurel wreath tied with a red ribbon
c = Cv()
arc(c, 7.5, 7.5, 4.6, 6.4, 105, 255, 'F')
arc(c, 7.5, 7.5, 4.6, 6.4, 285, 75, 'F')
for x, y in ((2, 4), (1, 7), (2, 10), (13, 4), (14, 7), (13, 10), (4, 2), (11, 2)):
    c.put(x, y, 'u')
c.rows([
    ' RRRR ',
    'RRRRRR',
    ' RRrR ',
    ' R  R ',
    'R    R',
], 5, 11)
c.rows(['  y  ', ' yGy ', '  y  '], 5, 5)
ICONS['heros_welcome'] = c

# Voice of the Realm: the royal decree and its seal
sc = Cv()
sc.rows([
    ' OQQQQQQQQQO ',
    'OQQQQQQQQQQQO',
    ' QQQQQQQQQQQ ',
    ' QOOOOOOOOQQ ',
    ' QQQQQQQQQQQ ',
    ' QOOOOOOQQQQ ',
    ' QQQQQQQQQQQ ',
    ' QOOOOOQQQQQ ',
    ' QQQQQQQQQQQ ',
    ' QQQQQQQQQQQ ',
    'OQQQQQQQQQQQO',
    ' OQQQQQQQQQO ',
], 0, 1)
se = Cv()
se.rows([
    ' RRR ',
    'RRRRR',
    'RRyRR',
    'RRRRR',
    ' RRR ',
    ' R R ',
    'r   r',
], 9, 9)
ICONS['voice_of_the_realm'] = [sc, se]

# ---------------------------------------------------------------- feats
# Golden Mouth: gilded lips and a glint
c = Cv()
c.rows([
    '   GGGG  GGGG   ',
    '  GGyGGGGGGyGG  ',
    ' GGGGGGGGGGGGGG ',
    'GG%%%%%%%%%%%%GG',
    ' GGGGGGGGGGGGGG ',
    '  GGGGyyyGGGGG  ',
    '   GGGGGGGGGG   ',
    '     GGGGGG     ',
], 0, 6)
c.rows(['  *  ', '  *  ', '**y**', '  *  ', '  *  '], 11, 0)
c.pts([(4, 2), (2, 3)], '^')
ICONS['golden_mouth'] = c

# Market Regular: a striped stall with goods on the board
c = Cv()
c.rows([
    'RRUURRUURRUURRUU',
    'RRUURRUURRUURRUU',
    'RRUURRUURRUURRUU',
    ' R  R  R  R  R  ',
], 0, 1)
c.rows(['W              W'] * 6, 0, 5)
c.rows(['WWWWWWWWWWWWWWWW', 'wwwwwwwwwwwwwwww'], 0, 11)
c.rows(['W W          W W'] * 3, 0, 13)
c.rows([' RR AAA  EE ', 'RRRRAAAA EEE'], 2, 9)
c.pts([(1, 13), (14, 13), (1, 14), (14, 14), (1, 15), (14, 15)], '.')
ICONS['market_regular'] = c

# ---------------------------------------------------------------- synergies
# Caravan Master (Speech + Athletics): an iron-shod cart wheel rolling down the road
c = Cv()
ring(c, 9, 7, 5.6, 6.6, 's')
ring(c, 9, 7, 4.5, 5.55, 'W')
for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0), (-0.7, -0.7), (0.7, 0.7), (-0.7, 0.7), (0.7, -0.7)):
    for k in range(2, 5):
        c.put(round(9 + dx * k), round(7 + dy * k), 'v')
disc(c, 9, 7, 1.5, 'S')
c.put(8, 6, 'H')
c.rows(['~~  ', '    ', '~~~ ', '    ', '~~  '], 0, 5)
c.rows(['////////////////'], 0, 15)
ICONS['caravan_master'] = c

# Tavern-Keeper (Speech + Survival): a foaming tankard
c = Cv()
c.rows([
    '  UU UU    ',
    ' UUUUUUUU  ',
    'UUUUUUUUUU ',
    'oUoUUUoUUo ',
    'WWWWWWWWWW ',
    'SSSSSSSSSS ',
    'WWvWWWvWWW ',
    'WWvWWWvWWW ',
    'WWvWWWvWWW ',
    'WWvWWWvWWW ',
    'SSSSSSSSSS ',
    'WWWWWWWWWW ',
], 1, 3)
h = Cv()
h.rows([
    'SSSS',
    '   S',
    '   S',
    '   S',
    'SSSS',
], 11, 8)
c.pts([(3, 0), (4, 1), (7, 1), (8, 0)], '~')
ICONS['tavern_keeper'] = [h, c]

# ---------------------------------------------------------------- teaching
# The Courtier's Manners: A Treatise on Courtesy, gold-cornered, a ribbon to mark the place
c = Cv()
c.rows([
    'GRRRRRRRRRG',
    'RRRRRRRRRRR',
    'RRRRRRRRRRR',
    'RRRyGGGyRRR',
    'RRRGRRRGRRR',
    'RRRGRRRGRRR',
    'RRRyGGGyRRR',
    'RRRRRRRRRRR',
    'RRRRRRRRRRR',
    'GRRRRRRRRRG',
    'QQQQQQQQQQQ',
    'rrrrrrrrrrr',
], 2, 1)
c.rows(['BB', 'BB', 'B ', 'BB'], 10, 12)
c.put(11, 15, '.')
ICONS['the_courtiers_manners'] = c
