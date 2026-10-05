# Marksmanship perks: the bow, the arrow and the long shot.
import math
from perkkit import PRINT, STRING, Cv, blade, diag, arc, disc, mirror, arrow_diag, arrow_dr, arrow_h, arrow_up, poly

ICONS = {}


def bow_drawn_diag(c, O=(7.5, 7.5), grip_u=2.6, half=7.2, nock_u=-5.6, tip_u=6.6, arrow=True):
    """A bow at full draw, arrow pointing up-right.  u runs along the arrow, v along the stave."""
    r2 = 2 ** 0.5
    def P(u, v):
        return (O[0] + (u + v) / r2, O[1] + (-u + v) / r2)
    k = (grip_u + 0.9) / (half * half)
    pts = []
    for i in range(-200, 201):
        v = half * i / 200
        x, y = P(grip_u - k * v * v, v)
        pts.append((int(x), int(y)))
    for (x, y) in pts:
        c.put(x, y, 'W')
    (ax, ay), (bx, by) = pts[0], pts[-1]
    nx, ny = P(nock_u, 0)
    nx, ny = int(nx), int(ny)
    c.line(ax, ay, nx, ny, '+')
    c.line(bx, by, nx, ny, '+')
    c.put(ax, ay, 'w'); c.put(bx, by, 'w')
    if arrow:
        tx, ty = P(tip_u, 0)
        tx, ty = int(tx), int(ty)
        arrow_diag(c, tx, ty, tx - nx + 1)
    gx, gy = P(grip_u, 0)
    return int(gx), int(gy)


def bow_v(c, x0=2, flip=False, string_x=None):
    """Upright bow, belly to the right, string at the back (left)."""
    pts = [(4, 0), (3, 0), (2, 1), (1, 2), (1, 3), (0, 4), (0, 5), (0, 6), (0, 7), (0, 8), (0, 9), (0, 10), (1, 11), (1, 12),
           (2, 13), (3, 14), (4, 14)]
    for (x, y) in pts:
        c.put(x0 + x, y + 1, 'W')
    c.put(x0 + 4, 1, 'w'); c.put(x0 + 4, 15, 'w')


def target(c, cx, cy, r=6.4):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                c.put(x, y, 'A')
            if d <= r * 0.74:
                c.put(x, y, 'R')
            if d <= r * 0.5:
                c.put(x, y, 'C')
            if d <= r * 0.26:
                c.put(x, y, 'G')


# ---------------------------------------------------------------- roots
# Steady Aim: a bow held at full draw, arrow true
c = Cv()
upper = [(9, 4), (8, 3), (7, 2), (6, 1), (5, 1), (4, 1), (3, 2)]
for (x, y) in upper:
    c.put(x, y, 'W')
    c.put(15 - y, 15 - x, 'W')
c.pts([(3, 2), (13, 12)], 'w')
c.line(3, 3, 4, 10, STRING)
c.line(12, 12, 5, 11, STRING)
arrow_diag(c, 13, 2, 10)
c.pts([(10, 5), (9, 5), (10, 6)], 'L')
c.put(10, 5, 'L')
ICONS['steady_aim'] = c

# Retrieval: a spent arrow found standing where its quarry fell
c = Cv()
c.rows([
    '    ?????????   ',
    '  ?????????????  ',
    ' ////////////// ',
], 0, 12)
c.g[13] = list('  ?????????????  '[:16])
arrow_diag(c, 13, 1, 11, head='S')
for (x, y) in [(13, 1), (12, 1), (13, 2), (12, 2)]:
    c.put(x, y, '.')
# flip: head buried in the ground, fletching up
c = Cv()
c.rows([
    '   ?|????|??    ',
    ' ?????????????? ',
    '////////////////',
], 0, 13)
c.line(5, 13, 11, 4, 'v')
c.pts([(11, 4), (12, 3), (11, 3), (12, 2), (13, 3), (12, 4)], 'n')
c.pts([(11, 2), (13, 4)], 'n')
c.pts([(10, 4), (12, 5)], 'r')
c.pts([(3, 4), (4, 3), (5, 3), (6, 4)], ':')
c.pts([(2, 5), (2, 6)], ':')
ICONS['retrieval'] = c

# ---------------------------------------------------------------- path A: Sharpshooter
# Long Shot: an arrow at the end of a long, high flight
c = Cv()
pts = []
for i in range(0, 200):
    t = i / 199
    x = 0.5 + 9.0 * t
    y = 15.0 - 13.0 * t + 6.0 * t * (1 - t) * 0 - 3.0 * math.sin(math.pi * t) * 0.9
    pts.append((int(x), int(y)))
seen = []
for p in pts:
    if p not in seen:
        seen.append(p)
for j, p in enumerate(seen):
    if j % 2 == 0:
        c.put(p[0], p[1], ':')
arrow_diag(c, 15, 0, 7)
ICONS['long_shot'] = c

# Hunter's Mark: a quarry outlined for your eyes alone
c = Cv()
c.rows([
    ' , ,            ',
    ',K,K,           ',
    ',,KK,,          ',
    ',KKKK,          ',
    ' ,,KK,,,,,,,,,  ',
    '   ,KKKKKKKKKK, ',
    '   ,KKKKKKKKKK, ',
    '   ,KKKKKKKKKK, ',
    '   ,,KKKKKKKK,, ',
    '    ,K,K,,K,K,  ',
    '    ,K,K,,K,K,  ',
    '    ,K,K,,K,K,  ',
    '    ,,,,  ,,,,  ',
], 0, 1)
c.put(2, 3, 'z')
arrow_diag(c, 9, 7, 6)
for (x, y) in [(9, 7), (8, 7), (7, 7), (9, 8), (9, 9), (8, 8)]:
    c.put(x, y, 'K')
ICONS['hunters_mark'] = c

# Steady Breath: a slow breath out, and an arrow flying dead straight
c = Cv()
c.rows([
    '  UU    ',
    ' UUUU U ',
    'UUUUUUUU',
    ' UUUUUU ',
], 0, 3)
c.pts([(9, 5), (11, 5), (13, 5)], ':')
arrow_h(c, 15, 11, 13)
c.pts([(4, 9), (6, 9), (8, 9), (10, 9)], ':')
ICONS['steady_breath'] = c

# Pinning Shot: an arrow driven through a boot into the ground
c = Cv()
c.rows([
    '  jjjj      ',
    '  LLLL      ',
    '  LLLL      ',
    '  LLLLL     ',
    '  LLLLLLLL  ',
    '  LLLLLLLLL ',
    '  wwwwwwwww ',
], 2, 7)
c.rows(['////////////////'], 0, 14)
c.line(4, 13, 12, 5, 'v')
c.pts([(12, 5), (13, 4), (12, 4), (13, 3), (14, 4), (13, 5)], 'n')
c.pts([(12, 3), (14, 5)], 'n')
c.pts([(4, 13), (5, 12)], 'v')
c.pts([(2, 10), (2, 11), (1, 12)], '~')
ICONS['pinning_shot'] = c

# ---------------------------------------------------------------- path B: Quickshot
# Quick Nock: two arrows away, one hard on the other's heels
c = Cv()
arrow_diag(c, 15, 4, 9)
arrow_diag(c, 11, 0, 9)
c.pts([(1, 12), (2, 11)], '~'); c.pts([(4, 15), (5, 14)], '~'); c.pts([(0, 8), (1, 7)], ':')
ICONS['quick_nock'] = c

# Fletcher: a bundle of fresh arrows and a goose feather
c = Cv()
for k in (0, 2, 4):
    arrow_diag(c, 11 + k, 1 + k, 10)
c.pts([(9, 8), (10, 9), (11, 10), (8, 7)], 'L')
c.pts([(9, 7), (10, 8)], 'l')
fe = Cv()
fe.rows([
    '  UU',
    ' UUo',
    ' UUo',
    'UUo ',
    'UUo ',
    'Uo  ',
    'o   ',
], 0, 1)
ICONS['fletcher'] = [c, fe]

# ---------------------------------------------------------------- path B tier III
# Double Nock: two arrows loosed side by side from one bow
c = Cv()
bow_v(c, 1)
c.line(6, 2, 6, 14, STRING)
for y in (5, 10):
    arrow_h(c, 15, y, 12)
ICONS['double_nock'] = c

# Quiverwise: a full quiver, and an arrow that finds its way back into it
c = Cv()
c.rows([
    '         n  n   ',
    '        nn nn  n',
    '        Wn Wn nn',
    '         W  W W ',
    '       LLLLLLL  ',
    '      LjLLLLL   ',
    '     LjLLLLL    ',
    '    LjLLLLL     ',
    '   LjLLLLL      ',
    '  LLLLLLL       ',
    '  LLLLLL        ',
    '  LLLLL         ',
    '   LLL          ',
], 0, 2)
c.line(5, 11, 10, 6, 'k')
c.pts([(2, 3), (3, 2), (4, 2), (5, 3)], ':')
c.pts([(1, 4), (1, 5)], ':')
c.pts([(6, 4), (7, 4)], ':')
ICONS['quiverwise'] = c

# ---------------------------------------------------------------- masteries
# Deadeye: an arrow dead in the gold
c = Cv()
target(c, 7, 8.5, 6.6)
arrow_diag(c, 8, 8, 8, head='S')
for (x, y) in [(8, 8), (7, 8), (8, 9), (7, 9)]:
    c.put(x, y, 'G')
c.put(7, 8, 'y')
ICONS['deadeye'] = c

# Volley: three arrows loosed together in a spread
c = Cv()
arrow_up(c, 2, 0, 13)
arrow_diag(c, 14, 1, 11)
arrow_h(c, 15, 13, 13)
ICONS['volley'] = c

# Arrowstorm: arrows falling like rain
c = Cv()
for (x, y, L) in ((6, 7, 7), (11, 4, 7), (15, 0, 5), (10, 13, 6), (15, 10, 6), (4, 15, 4)):
    arrow_dr(c, x, y, L)
ICONS['arrowstorm'] = c

# ---------------------------------------------------------------- feats
# Sixty Paces: a long line of footsteps out to a far mark, an arrow in it
c = Cv()
for (x, y) in ((0, 13), (2, 10), (0, 7), (3, 5), (1, 2)):
    c.pts([(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1), (x, y + 2)], PRINT)
target(c, 10.5, 9.5, 4.6)
arrow_diag(c, 10, 10, 6)
c.put(10, 10, 'y'); c.put(9, 10, 'G'); c.put(10, 11, 'G')
ICONS['sixty_paces'] = c

# Wingshot: a bird brought down on the wing
c = Cv()
c.rows([
    'ZZ              ',
    ' ZZZ        ZZZ ',
    '  ZZZZ    ZZZZ  ',
    '   ZZZZZZZZZZ   ',
    '    ZZZZZZZZ    ',
    '      ZZZZ@@    ',
    '      ZZZZ %    ',
    '       ZZ       ',
    '      Z  Z      ',
], 0, 4)
c.pts([(10, 9), (11, 9)], 'G')
c.pts([(2, 6), (4, 7), (13, 6), (11, 7)], '3')
arrow_diag(c, 13, 2, 12)
ICONS['wingshot'] = c

# ---------------------------------------------------------------- teaching
# Steppe Draw: the short recurved horse bow, and a horseshoe
c = Cv()
pts = [(6, 0), (5, 0), (4, 1), (4, 2), (3, 3), (2, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9), (2, 10), (3, 11), (4, 12), (4, 13),
       (5, 14), (6, 14)]
for (x, y) in pts:
    c.put(x + 1, y + 1, 'W')
c.pts([(8, 1), (8, 15)], 'w')
c.pts([(2, 7), (2, 8), (2, 9)], 'L')
c.pts([(2, 6), (2, 10)], 'G')
c.line(5, 3, 5, 13, STRING)
c.pts([(6, 2), (6, 14)], STRING)
c.rows([
    ' SSSS ',
    'SS  SS',
    'S    S',
    'S    S',
    'S    S',
], 9, 9)
c.pts([(10, 10), (13, 10), (9, 12), (14, 12)], 'x')
ICONS['steppe_draw'] = c

# ---------------------------------------------------------------- synergies
# Huntsman (Marksmanship + Survival): an arrow through a haunch of meat
c = Cv()
c.rows([
    '    ((((    ',
    '  ((((((((  ',
    ' (((((((((( ',
    '(((((((((((',
    '((((((((((( ',
    ' ((((((((( ',
    '  (((((((  ',
    '    (((@@  ',
    '       @@@ ',
    '        $@ ',
], 1, 5)
c.pts([(4, 7), (5, 7), (3, 8)], '7')
arrow_diag(c, 14, 1, 13)
ICONS['huntsman'] = c

# Leaping Shot (Marksmanship + Athletics): an arrow loosed at the top of a jump
c = Cv()
c.rows([
    '   jjj  ',
    '   LLL  ',
    '   LLLL ',
    ' LLLLLL ',
    'LLLLLLL ',
    'wwwwwww ',
], 2, 6)
for (x, y) in ((0, 15), (1, 14), (2, 13), (11, 13), (12, 14), (13, 15)):
    c.put(x, y, ':')
arrow_h(c, 15, 2, 10)
ICONS['leaping_shot'] = c
