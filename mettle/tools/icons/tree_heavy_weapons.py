# Heavy Weapons perks: the axe in the forest and the mace in the fight.
import math
from perkkit import CHEST, HELM, Cv, blade, diag, arc, disc, mirror, heater, axe, mace, poly, heart

ICONS = {}

MACE = [
    '   xSx   ',
    '  xSSSx  ',
    ' xSHSSSs ',
    'xSHSSSSsx',
    'SSSSSSSSS',
    'xSSSSSSsx',
    ' xSSSSs  ',
    '  xSSs   ',
    '   xs    ',
]
MACE_S = [
    '  xSx  ',
    ' xSHSs ',
    'xSHSSsx',
    'SSSSSSS',
    'xSSSSsx',
    ' xSSs  ',
    '  xs   ',
]


LOG = [
    ' vWWWWWWWWWWW',
    'v;vWWWWWWWWWW',
    'v=;wWWWWWWWWW',
    'v;vwwwwwwwwww',
    ' vw          ',
]


def log_side(c, ox, oy, n=13):
    rows = [r[:n] for r in LOG]
    c.rows(rows, ox, oy)


def log_end(c, ox, oy, r=2.6, bark='w', face=';', ring='='):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - ox, y - oy)
            if d <= r + 0.5:
                c.put(x, y, bark)
            if d <= r - 0.5:
                c.put(x, y, face)
            if r - 0.5 >= 1.6 and abs(d - (r - 1.6)) < 0.5:
                c.put(x, y, ring)


# ---------------------------------------------------------------- roots
# Heft: a heavy flanged mace, the weight right there in the head
c = Cv()
c.line(1, 14, 8, 7, 'W')
c.pts([(1, 14), (2, 13)], 'L')
c.rows(MACE, 6, 1)
c.pts([(14, 0), (15, 1)], '~'); c.pts([(15, 4), (15, 5)], '~'); c.pts([(11, 0), (12, 0)], '~')
ICONS['heft'] = c

# Feller: an axe bitten deep into a stump
st = Cv()
st.rows([
    '    vvvvvvv     ',
    '  vv;;;;;;;vv   ',
    ' v;;;=====;;;v  ',
    ' v;;=;;;;;=;;v  ',
    ' wv;;=====;;vw  ',
    ' wwvv;;;;;vvww  ',
    ' wqwwvvvvvwwqw  ',
    ' wqwwqwwwqwwqw  ',
    'wwqwwqwwwqwwqww ',
], 0, 7)
ax = Cv()
axe(ax, 14, 1, 9, 8, side=1, k=1.2)
ax.pts([(2, 4), (4, 2), (1, 6)], 'v')
ICONS['feller'] = [st, ax]

# ---------------------------------------------------------------- path A: Woodsman
# Cleave: one axe stroke that bites the foes either side as well
c = Cv()
for ox in (0, 6, 12):
    c.rows([' SS ', 'SSSS', 'SSSS', 's%%s'], ox, 11)
c.pts([(3, 14), (9, 14), (15, 14)], '.')
ax = Cv()
axe(ax, 13, 1, 8, 8, side=1, k=1.2)
ax.pts([(2, 8), (1, 9), (14, 9), (15, 8)], '^')
ax.pts([(3, 9), (13, 10)], '^')
ICONS['cleave'] = [c, ax]

# Log Hauler: a stack of logs and a feather, carried as if they weighed nothing
lg = Cv()
lg.rows([
    '  vvvWWWWWWWWW  ',
    ' v;;;vWWWWWWWWW ',
    ' v;=;vWWWWWWWWW ',
    ' v;;;vwwwwwwwww ',
    '  vvvwwwwwwwww  ',
], 0, 10)
lg.rows([
    '  vvvWWWWWWWWW  ',
    ' v;;;vWWWWWWWWW ',
    ' v;=;vWWWWWWWWW ',
    ' v;;;vwwwwwwwww ',
    '  vvvwwwwwwwww  ',
], 1, 5)
lg.pts([(8, 12), (11, 6), (9, 7)], 'q')
fe = Cv()
fe.rows([
    '    UU',
    '   UUo',
    '  UUUo',
    ' UUUo ',
    'UUoo  ',
    'o     ',
], 10, 0)
ICONS['log_hauler'] = [lg, fe]

# ---------------------------------------------------------------- path A tier III
# Rhythm of the Axe: a log notched by chop after chop
lg = Cv()
lg.rows([
    '  vvvWWWWWWWWWWW',
    ' v;;;vWWWWWWWWWW',
    ' v;=;vWWWWWWWWWW',
    ' v;;;vwwwwwwwwww',
    '  vvvwwwwwwwwwww',
], 0, 10)
for x in (6, 9, 12):
    lg.pts([(x, 10), (x + 1, 10), (x + 1, 11)], '.')
    lg.put(x, 11, 'q')
ax = Cv()
axe(ax, 15, 1, 11, 8, side=1, k=1.15)
for (x, y) in ((6, 8), (9, 7), (3, 7)):
    ax.put(x, y, '~')
ax.pts([(5, 7), (8, 6), (2, 6)], ':')
ICONS['rhythm_of_the_axe'] = [lg, ax]

# Throwing Axe: a hatchet tumbling end over end through the air
c = Cv()
arc(c, 8, 8, 6.4, 7.3, 200, 330, '~')
arc(c, 8, 8, 6.4, 7.3, 20, 120, ':')
axe(c, 4, 11, 10, 5, side=1, k=1.1)
c.line(0, 3, 2, 3, '~'); c.line(0, 6, 1, 6, '~')
ICONS['throwing_axe'] = c

# ---------------------------------------------------------------- path B: Crusher
# Armour Crusher: a mace caving in a breastplate
pl = Cv()
pl.rows(CHEST, 0, 5)
pl.pts([(8, 8), (7, 9), (8, 10), (6, 10), (9, 10), (7, 11), (5, 11)], 'x')
pl.pts([(8, 9), (7, 10)], 'H')
mc = Cv()
mc.line(15, 0, 13, 2, 'W')
mc.rows(MACE_S, 8, 1)
mc.pts([(6, 3), (7, 2), (15, 7), (14, 9)], '^')
ICONS['armour_crusher'] = [pl, mc]

# Bone Breaker: a bone snapped clean in two
c = Cv()
c.rows([
    ' @@           ',
    '@@@@          ',
    '@@@@@         ',
    ' @@@@@        ',
    '   @@@@       ',
    '    @@@       ',
    '              ',
    '        @@@   ',
    '        @@@@  ',
    '         @@@@@',
    '          @@@@',
    '           @@@',
    '            @ ',
], 1, 1)
c.pts([(7, 6), (8, 7)], '&')
c.pts([(9, 8)], '&')
c.pts([(7, 9), (10, 6), (6, 8), (11, 8)], '^')
c.put(8, 1, '.')
c.pts([(13, 11), (14, 12), (15, 12), (13, 13), (12, 13), (14, 13)], '@')
c.pts([(1, 3), (0, 2), (2, 1)], '@')
ICONS['bone_breaker'] = c

# Iron Grip: a mailed fist locked round a haft
c = Cv()
c.rect(9, 0, 9, 15, 'W'); c.rect(10, 0, 10, 15, 'w')
c.rows([
    '     SSShhhhh   ',
    '    SSSSSsssss  ',
    '   SSSSSShhhhhh ',
    '   SSSSSSssssss ',
    '   SSSSSShhhhhh ',
    '   SSSSSSssssss ',
    '   SSSSSShhhhh  ',
    '    SSSSSsssss  ',
    '     SSS        ',
], 0, 4)
c.rows(['LL', 'LL', 'LL', 'LL', 'LL', 'Ll'], 1, 6)
c.pts([(1, 6), (1, 11)], 'k')
ICONS['iron_grip'] = c

# Stagger: a helmed head knocked dizzy
c = Cv()
c.rows(HELM, 3, 6)
for (x, y) in ((2, 3), (8, 1), (13, 4)):
    c.pts([(x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)], '^')
    c.put(x, y, '*')
c.pts([(4, 2), (5, 1), (10, 1), (11, 2), (12, 2)], '~')
ICONS['stagger'] = c

# ---------------------------------------------------------------- masteries
# Timber: the whole tree coming down, the axe still in the stump
c = Cv()
c.rows([
    '         FFFF   ',
    '       FFFFFFF  ',
    '      FFFFFFFFF ',
    '      FFFFFFFFFF',
    '     FFFFFFFFFF ',
    '      FFFFFFFF  ',
    '      FWFFFFF   ',
    '     WWW FF     ',
    '    WWW         ',
    '   WWW          ',
    '  WWW           ',
], 0, 0)
c.pts([(8, 2), (11, 3), (9, 5), (12, 1), (7, 4)], 'u')
c.pts([(13, 4), (10, 6), (11, 5)], 'f')
c.rows([
    'qWWw',
    'wWWw',
    'wWWw',
], 1, 12)
c.pts([(1, 11), (2, 11), (3, 11)], 'v')
arc(c, 2, 11, 10.5, 11.3, 20, 60, '~')
c.pts([(6, 13), (7, 12), (8, 12), (7, 13), (6, 12)], 'S')
c.pts([(5, 12), (5, 13)], 'W')
ICONS['timber'] = c

# Berserker: a blood-edged double-bit axe, and a heart running low
c = Cv()
axe(c, 7, 15, 7, 2, side=-1, k=1.3, double=True)
for y in range(16):
    for x in range(16):
        if c.get(x, y) == 'H' and y >= 4 and (x + y) % 3 == 0:
            c.put(x, y, 'R')
c.pts([(1, 8), (14, 8), (1, 9)], 'R')
hr = Cv()
hr.rows(['RR RR', 'RRRRR', ' RRR ', '  R  '], 10, 11)
hr.pts([(12, 11), (12, 12), (13, 13)], 'K')
ICONS['berserker'] = [c, hr]

# Earthshaker: a mace slammed into the ground, the earth cracking outwards
c = Cv()
c.line(7, 0, 7, 5, 'W'); c.line(8, 0, 8, 5, 'w')
c.rows(MACE, 3, 4)
c.rows([
    '                ',
    'VVVVVVVVVVVVVVVV',
    '//////////\\\\\\\\\\\\',
    '////////////////',
    '////////////////',
], 0, 11)
c.pts([(7, 13), (6, 14), (8, 14), (5, 15), (9, 15), (10, 13), (4, 13)], '\\\\'[0])
arc(c, 7.5, 12, 6.0, 6.8, 140, 175, '~')
arc(c, 7.5, 12, 6.0, 6.8, 5, 40, '~')
c.pts([(1, 8), (14, 7), (2, 5)], 'T')
ICONS['earthshaker'] = c

# ---------------------------------------------------------------- feats
# Headhunter: an axe and the head it took
hd = Cv()
hd.rows([
    'fffffffff',
    'ffFfFFfFf',
    'FFFFFFFFF',
    'F%%FFF%%F',
    'FFFFFFFFF',
    'FFFfffFFF',
    'FFf%%%fFF',
    'FFFFFFFFF',
], 1, 7)
ax = Cv()
axe(ax, 15, 15, 10, 2, side=1, k=1.15)
ICONS['headhunter'] = [hd, ax]

# Old Growth: a great trunk's cross-section, ring upon ring
c = Cv()
for y in range(16):
    for x in range(16):
        d = math.hypot(x - 7.5, y - 7.5)
        if d <= 7.4:
            c.put(x, y, 'q' if d > 6.4 else 'v')
        if d <= 6.4 and int(d) % 2 == 1:
            c.put(x, y, 'W')
c.pts([(7, 7), (8, 8), (7, 8), (8, 7)], '=')
c.pts([(10, 6), (11, 5), (12, 4)], 'q')
ICONS['old_growth'] = c

# ---------------------------------------------------------------- teaching
# Overhead Blow: the axe brought down from high over the head
c = Cv()
import math as _m
for y in range(16):
    for x in range(16):
        dx, dy = x - 6.0, 9.5 - y
        r = _m.hypot(dx, dy)
        ang = _m.degrees(_m.atan2(dy, dx))
        if -80 <= ang <= 80:
            t = (80 - ang) / 160.0
            w = 0.6 + 1.8 * _m.sin(_m.pi * t)
            if 8.2 - w <= r <= 8.2:
                c.put(x, y, ',' if r > 8.2 - w * 0.5 else '~')
axe(c, 4, 15, 4, 3, side=-1, k=1.25)
c.pts([(4, 12), (4, 11)], 'G')
ICONS['overhead_blow'] = c

# ---------------------------------------------------------------- synergy
# Stonebreaker (Mining + Heavy Weapons): a mace splitting a stone block
st = Cv()
st.rect(1, 7, 10, 15, 'T')
st.pts([(3, 9), (4, 10), (8, 12), (3, 13)], 't')
st.pts([(2, 8), (9, 13)], 'i')
st.pts([(6, 7), (6, 8), (5, 9), (6, 10), (5, 11), (5, 12), (6, 13), (6, 14), (6, 15)], 'z')
mc = Cv()
mc.line(15, 0, 11, 4, 'W')
mc.rows(MACE_S, 7, 2)
mc.pts([(12, 9), (13, 7), (2, 5)], 'T')
mc.pts([(14, 9)], 't')
ICONS['stonebreaker'] = [st, mc]
