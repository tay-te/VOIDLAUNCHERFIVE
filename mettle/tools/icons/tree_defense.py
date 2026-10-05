# Defense perks: the shield, the armour and lasting the fight.
import math
from perkkit import CHEST, HELM, STRING, Cv, blade, diag, arc, disc, mirror, heater, heart, arrow_diag, arrow_h, axe

ICONS = {}

TOWER = [
    '  SSSSSS  ',
    ' SWWWWWWS ',
    'SWWWwWWWWS',
    'SWWWwWWWWS',
    'SWWWwWWWWS',
    'SWWWwWWWWS',
    'SWWSSSSWWS',
    'SWWSHSSWWS',
    'SWWWwWWWWS',
    'SWWWwWWWWS',
    'SWWWwWWWWS',
    'SWWWwWWWWS',
    ' SWWwWWWS ',
    '  SSSSSS  ',
]

FEATHER = [
    '    UU',
    '   UUo',
    '  UUUo',
    ' UUUo ',
    'UUoo  ',
    'o     ',
]

# ---------------------------------------------------------------- roots
# Thick Skin: a patch of overlapping iron scales
c = Cv()
SCALE = ['.hh.', 'h00s', 'h00s', '.ss.']
for j, y in enumerate(range(1, 14, 3)):
    off = 0 if j % 2 == 0 else 2
    for x in range(-2 + off, 16, 4):
        for dy, r in enumerate(SCALE):
            for dx, ch in enumerate(r):
                if ch != '.':
                    c.put(x + dx, y + dy, ch)
mask = lambda x, y: ((x - 7.5) / 7.0) ** 2 + ((y - 8.0) / 7.2) ** 2 <= 1
for y in range(16):
    for x in range(16):
        if not mask(x, y):
            c.put(x, y, '.')
ICONS['thick_skin'] = c

# Armour Training: a breastplate that wears as light as a feather
c = Cv()
c.rows(CHEST, 0, 4)
f = Cv()
f.rows(FEATHER, 10, 0)
ICONS['armour_training'] = [c, f]

# ---------------------------------------------------------------- path A: Shieldbearer
# Shield Up: a shield snapped up in an instant
c = Cv()
heater(c, 3, 0, 10, 12)
c.pts([(1, 9), (1, 10), (1, 11), (1, 12)], '~')
c.pts([(14, 8), (14, 9), (14, 10), (14, 11)], '~')
c.pts([(5, 13), (5, 14), (5, 15)], '~'); c.pts([(10, 13), (10, 14), (10, 15)], '~'); c.pts([(7, 14), (8, 14), (7, 15), (8, 15)], ':')
ICONS['shield_up'] = c

# Shield Bash: the shield driven forward into a foe
c = Cv()
heater(c, 1, 2, 10, 12)
for (x, y) in ((12, 3), (13, 2), (14, 1)):
    c.put(x, y, '^')
for (x, y) in ((12, 7), (13, 7), (14, 7), (15, 7)):
    c.put(x, y, '^')
for (x, y) in ((12, 11), (13, 12), (14, 13)):
    c.put(x, y, '^')
ICONS['shield_bash'] = c

# Perfect Block: an arrow turned straight back where it came from
c = Cv()
heater(c, 0, 2, 10, 12)
a = Cv()
for i in range(2, 7):
    a.put(10 + i, 6 - i, 'v')
a.pts([(10, 6), (11, 6), (12, 6), (10, 5), (10, 4)], '.')
c2 = Cv()
c2.line(11, 5, 15, 1, 'v')
c2.pts([(15, 1), (14, 1), (15, 2)], 'n')
c2.pts([(11, 7), (12, 6), (11, 6), (10, 6), (11, 8)], 'S')
c2.pts([(10, 4), (10, 9), (13, 8), (8, 5)], '^')
c2.put(11, 7, 'H')
ICONS['perfect_block'] = [c, c2]

# Bulwark: a great tower shield that covers the flanks too
c = Cv()
c.rows(TOWER, 3, 1)
arc(c, 7.5, 8, 6.4, 7.2, 110, 160, '~')
arc(c, 7.5, 8, 6.4, 7.2, 20, 70, '~')
arc(c, 7.5, 8, 6.4, 7.2, 200, 250, '~')
arc(c, 7.5, 8, 6.4, 7.2, 290, 340, '~')
ICONS['bulwark'] = c

# ---------------------------------------------------------------- path B: Ironclad
# Mender: a helm being stitched and kept in good repair
c = Cv()
c.rows(HELM, 0, 4)
c.pts([(2, 9), (4, 9), (6, 9), (8, 9)], 'C')
c.line(10, 0, 15, 5, 'h')
c.put(15, 5, 'H'); c.put(10, 0, 's')
c.pts([(11, 2), (12, 3), (11, 3)], '.')
c.pts([(9, 1), (8, 2), (8, 3), (9, 4), (10, 5), (11, 6), (12, 6)], STRING)
ICONS['mender'] = c

# Second Wind: a heart caught by a fresh breath of air
c = Cv()
heart(c, 0, 7, 'R', 9)
c.pts([(1, 9), (2, 8)], 'n')
c.rows([
    '      ~~~   ',
    '     ~   ~  ',
    '~~~~~~~   ~ ',
    '      ~  ~  ',
    '  ~~~~~~~   ',
], 4, 0)
c.rows([
    '  ~~ ',
    ' ~  ~',
    '~~~~ ',
], 11, 5)
ICONS['second_wind'] = c

# Hold the Line: a wall of shields, rim to rim
c = Cv()
heater(c, 0, 3, 7, 10, plank=False)
heater(c, 9, 3, 7, 10, plank=False)
d = Cv()
heater(d, 4, 1, 8, 12)
ICONS['hold_the_line'] = [c, d]

# Unbowed: poison running off good plate
c = Cv()
c.rows(CHEST, 0, 5)
c2 = Cv()
c2.rows([
    '  E  ',
    ' EEE ',
    ' EEE ',
    'EEEEE',
    'EaEEE',
    ' EEE ',
], 11, 0)
c2.rows([' E ', 'EEE', 'aEE', ' E '], 12, 9)
c2.pts([(13, 7)], ':')
ICONS['unbowed'] = [c, c2]

# ---------------------------------------------------------------- masteries
# Guardian: a raised shield with the folk it shelters behind it
p = Cv()
for ox in (0, 11):
    p.rows([
        ' 333 ',
        ' NNN ',
        ' NNN ',
        'CCCCC',
        'CCCCC',
        'CCCCC',
        'CCCCC',
    ], ox, 5)
sh = Cv()
heater(sh, 3, 1, 10, 14, face='B', plank='b', rim='G')
sh.pts([(7, 7), (8, 7), (7, 8), (8, 8)], 'G')
sh.put(7, 7, 'y')
ICONS['guardian'] = [p, sh]

# Last Stand: one heart left, and the shield still up
sh = Cv()
heater(sh, 1, 0, 12, 14)
for (x, y) in ((3, 2), (4, 3), (10, 9), (11, 8)):
    sh.put(x, y, 'q')
h = Cv()
heart(h, 8, 9, 'R', 7)
h.pts([(9, 10)], 'n')
ICONS['last_stand'] = [sh, h]

# Juggernaut: a horned great helm charging through
c = Cv()
c.rows([
    '   SSSSSS   ',
    '  SSSSSSSS  ',
    '  SSSSSSSS  ',
    '  SSSSSSSS  ',
    '  ssssssss  ',
    '  S%%%%%%S  ',
    '  SSSSSSSS  ',
    '  SSS%%SSS  ',
    '  SSS%%SSS  ',
    '  SSSSSSSS  ',
    '   SSSSSS   ',
], 3, 4)
c.rows([
    '@@        @@',
    '@&        &@',
    ' @&      &@ ',
    ' @@      @@ ',
    '  @      @  ',
], 3, 1)
c.rows(['@@'], 3, 0)
c.rows(['@@'], 13, 0)
c.pts([(0, 8), (1, 8), (0, 11), (1, 11), (2, 11), (0, 14), (1, 14)], '~')
ICONS['juggernaut'] = c

# ---------------------------------------------------------------- feats
# Pincushion: a shield bristling with the arrows it caught
c = Cv()
heater(c, 0, 5, 10, 11)
for (x, y, L) in ((3, 8, 6), (6, 10, 7), (4, 13, 7)):
    for i in range(L):
        c.put(x + i, y - i, ';' if i < L - 2 else 'v')
    ex, ey = x + L - 1, y - L + 1
    c.pts([(ex, ey - 1), (ex - 1, ey - 1)], 'n')
    c.pts([(ex + 1, ey), (ex + 1, ey + 1)], 'r')
    c.put(x, y, 'q')
ICONS['pincushion'] = c

# Still Standing: a helm split by a blow that should have killed
c = Cv()
c.rows(HELM, 3, 6)
c.pts([(8, 6), (8, 7), (7, 8), (8, 9), (7, 10)], 'x')
c.pts([(9, 7), (8, 8)], 'H')
for (x, y) in ((4, 2), (7, 1), (11, 2), (12, 4), (3, 4)):
    c.put(x, y, '^')
c.pts([(8, 3), (8, 4)], '*'); c.pts([(7, 3), (9, 3)], '^')
ICONS['still_standing'] = c

# ---------------------------------------------------------------- teaching
# Roll with the Blow: a heart that lets a quarter of the hit trail away
c = Cv()
c.rows([
    ' RRR RRR ',
    'RRRRRRRRR',
    'RRRRRRRRR',
    'RRRRRRRRR',
    ' RRRRRRR ',
    '  RRRRR  ',
    '   RRR   ',
    '    R    ',
], 1, 5)
for (x, y) in [(6, 5), (7, 5), (8, 5), (9, 5), (6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7), (6, 8), (7, 8)]:
    c.put(x, y, '.')
c.rows([
    ' rr',
    'rrr',
    'rrr',
    'r  ',
], 11, 2)
c.pts([(10, 6), (9, 7)], ':')
c.pts([(14, 1), (15, 2)], '~')
c.pts([(5, 5), (5, 6), (5, 7), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8)], 'R')
ICONS['roll_with_the_blow'] = c

# ---------------------------------------------------------------- synergies
# Marching Order (Defense + Athletics): an armoured boot on the march
c = Cv()
c.rows([
    '      jjjj     ',
    '      LLLL     ',
    '      SSSS     ',
    '      ssss     ',
    '      SSSS     ',
    '      SSSSS    ',
    '     SSSSSSs   ',
    '    LLLSSSSSs  ',
    '    LLLLLSSSSs ',
    '    xxxxxxxxxx ',
], 1, 4)
c.pts([(0, 8), (1, 8), (2, 8), (3, 8), (0, 11), (1, 11), (2, 11), (1, 14), (2, 14), (3, 14)], '~')
ICONS['marching_order'] = c

# Battle-Worn (Smithing + Defense): a hacked, dented shield held together with rivets
c = Cv()
heater(c, 2, 1, 12, 14)
for (x, y) in ((11, 1), (12, 1), (13, 1), (13, 2), (12, 2), (13, 3)):
    c.put(x, y, '.')
c.put(11, 2, 'S'); c.put(12, 3, 'S'); c.put(13, 4, 'S')
c.pts([(4, 5), (5, 6), (5, 7), (6, 8)], 'q')
c.pts([(10, 10), (9, 11), (10, 12)], 'q')
c.pts([(3, 9), (4, 9)], 'S')
for (x, y) in ((4, 8), (6, 10), (9, 9), (11, 7)):
    c.put(x, y, 'G')
ICONS['battle_worn'] = c
