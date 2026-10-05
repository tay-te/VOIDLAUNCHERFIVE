# Smithing perks: the armourer's and the toolwright's craft.
import math
from perkkit import (ANVIL, CHEST, GOLD_INGOT, HELM, INGOT, STRING, Cv, arc, axe, blade, diag, disc, hammer, heater,
                     poly, rpoly)

ICONS = {}

FEATHER = [
    '    UU',
    '   UUo',
    '  UUUo',
    ' UUUo ',
    'UUoo  ',
    'o     ',
]

# ---------------------------------------------------------------- roots
# Tinker: a copper kettle and the little hammer that mends it
c = Cv()
c.rows([
    '    lllll   ',
    '   l     l  ',
    '   XXXXXXX  ',
    '  XXXXXXXXX ',
    ' XXXXXXXXXXX',
    'XXXXXXXXXXXX',
    'XXXXXXXXXXX ',
    ' XXXXXXXXX  ',
    '  XXXXXXX   ',
], 0, 6)
c.pts([(0, 9), (0, 10)], '.')
c.rows(['XX', ' XX', '  X'], 0, 8) if False else None
h = Cv()
hammer(h, 12, 3, L=6, half=2.2, w=1.1)
h.pts([(8, 9), (9, 10)], '^')
ICONS['tinker'] = [c, h]

# Thrifty: an ingot used, and one handed back
c = Cv()
c.rows(INGOT, 0, 11)
c.rows(INGOT, 2, 8)
c2 = Cv()
c2.rows(['  hhhh ', ' HSSSSs', ' ssssss'], 8, 1)
arc(c2, 9, 7.5, 3.6, 4.4, 160, 260, '~')
c2.pts([(5, 5), (6, 4), (4, 4)], '~')
c2.pts([(15, 0), (14, 1)], '*')
ICONS['thrifty'] = [c, c2]

# ---------------------------------------------------------------- path A: Armourer
# Light Hand: an armoured hand holding a feather as if it weighed as much
c = Cv()
c.rows([
    '         SS     ',
    '      SS SS SS  ',
    '      SS SS SS  ',
    '   SS SS SS SS  ',
    '   SS SSSSSSSS  ',
    '   SSSSSSSSSSS  ',
    '   SSSSSSSSSSS  ',
    '    SSSSSSSSS   ',
    '    SSSSSSSSS   ',
    '     LLLLLLL    ',
    '     LLLLLLL    ',
], 0, 5)
f = Cv()
f.rows([
    '     UU',
    '    UUo',
    '   UUUo',
    '  UUUo ',
    ' UUoo  ',
    'Uo     ',
    'o      ',
], 2, 0)
ICONS['light_hand'] = [c, f]

# Riveted: two plates lapped and closed with a row of hammered rivets
c = Cv()
c.rows([
    ' SSSSSSSSSSSS ',
    'SSSSSSSSSSSSSS',
    'SSSSSSSSSSSSSS',
    'SSSSSSSSSSSSSS',
], 1, 6)
c2 = Cv()
c2.rows([
    'SSSSSSSSSSSSSS',
    'SSSSSSSSSSSSSS',
    'SSSSSSSSSSSSSS',
    'SSSSSSSSSSSSSS',
    ' SSSSSSSSSSSS ',
], 1, 10)
for x in (2, 5, 8, 11, 14):
    c2.put(x, 11, 'H'); c2.put(x, 12, 'x')
h = Cv()
hammer(h, 11, 2, L=6, half=2.2, w=1.1)
ICONS['riveted'] = [c, c2, h]

# Proofed: a breastplate bearing the armourer's proof mark
c = Cv()
c.rows(CHEST, 2, 4)
c.rows([' G ', 'GyG', ' G '], 3, 7)
c.pts([(10, 10), (10, 11)], 'x')
c.pts([(11, 10)], 'H')
c.pts([(1, 1), (2, 0), (14, 2)], '*')
ICONS['proofed'] = c

# Shieldwright: a fresh shield and the hammer that bound it
c = Cv()
heater(c, 0, 2, 11, 13)
h = Cv()
hammer(h, 12, 3, L=8, half=2.4, w=1.2, d=(-1, 1))
ICONS['shieldwright'] = [c, h]

# ---------------------------------------------------------------- path B: Toolwright
# Forged: a blade hot from the fire under the hammer, sparks flying
c = Cv()
c.rows(ANVIL, 1, 10)
c.rows([
    'JJJJJJJJMm  ',
    'YYYYYYYYJM  ',
], 2, 8)
c.pts([(1, 8), (1, 9), (0, 9)], 'L')
h = Cv()
hammer(h, 11, 3, L=6, half=2.4, w=1.2, d=(1, -1))
h.pts([(7, 5), (5, 4), (9, 6), (4, 6)], '^')
h.pts([(6, 3)], '*')
ICONS['forged'] = [c, h]

# Salvager: worn-out gear broken down into a heap of nuggets
c = Cv()
blade(c, 7, 1, 4, 'Hs', guard=2, grip=2)
c.pts([(8, 0), (7, 0), (8, 1)], '.')
c.rows(['  Hs', ' Hs ', 'Hs  '], 8, 0)
c.pts([(10, 0)], 'h')
n = Cv()
n.rows([
    '    SS     ',
    '  SSHSGG   ',
    ' SHSSSGy S ',
    'SSSSHSSSSHS',
    'SHSSSSHSSSS',
], 4, 11)
n.pts([(6, 7), (8, 8), (11, 9)], ':')
ICONS['salvager'] = [c, n]

# ---------------------------------------------------------------- path B tier III
# Keen Edge: a whetstone drawn along the edge until it gleams
c = Cv()
blade(c, 14, 1, 10, 'Hhs', guard=2, grip=2)
w = Cv()
w.rows(['TTTT', 'TTTTT', ' TTTT'], 9, 6)
w.pts([(13, 0), (15, 2), (14, 1), (12, 1), (14, 3)], '.')
w.pts([(14, 0), (13, 1), (15, 1), (14, 2)], '^')
w.put(14, 1, '*')
ICONS['keen_edge'] = [c, w]

# True Temper: a red-hot pick head quenched, steam boiling off
c = Cv()
c.rows([
    'WWWWWWWWWW',
    'v<<<<<<<<w',
    'WWWWWWWWWW',
    'wWWWWWWWWw',
    'WWWWWWWWWW',
    ' wwwwwwww ',
], 3, 10)
c.rows(['S', 'S'], 2, 10); c.rows(['S', 'S'], 13, 10)
c.pts([(3, 11), (12, 11)], 'S')
p = Cv()
p.rows([
    '   YYYYYY   ',
    ' YJJJJJJJJM ',
    'JMM  MM  MMJ',
    'M    MM    M',
    '     mm     ',
], 2, 4)
p.pts([(1, 3), (2, 2), (1, 1), (14, 3), (13, 2), (14, 1), (7, 2), (8, 1), (7, 0)], '~')
ICONS['true_temper'] = [c, p]

# ---------------------------------------------------------------- masteries
# Reforge: armour back on the anvil, an amethyst shard set to work into it
c = Cv()
c.rows(ANVIL, 1, 10)
c.rows(CHEST[:9], 2, 1)
c.rows(['  P ', ' PPp', 'PPp ', ' p  '], 12, 0)
c.pts([(13, 1)], 'P')
c.pts([(0, 8), (15, 7), (1, 5)], '^')
ICONS['reforge'] = c

# Masterwork: one blade in ten that comes out of the forge perfect
c = Cv()
blade(c, 14, 1, 10, 'Hhs', guard=3, grip=2, pommel='G')
c.pts([(2, 9), (8, 15)], 'y')
c.rows(['  ^  ', '  ^  ', '^^*^^', '  ^  ', '  ^  '], 1, 0)
ICONS['masterwork'] = c

# Honing: an edge brought to life on the grindstone, sparks flying
c = Cv()
for y in range(16):
    for x in range(16):
        d = math.hypot(x - 6.5, y - 7.5)
        if d <= 5.4:
            c.put(x, y, 'T')
        if d <= 1.4:
            c.put(x, y, 'W')
c.pts([(3, 5), (4, 4), (9, 10), (10, 9)], 't')
c.rows(['WW', 'WW', 'WW', 'WW'], 0, 12); c.rows(['WW', 'WW', 'WW', 'WW'], 11, 12)
c.rect(0, 11, 12, 11, 'W')
b = Cv()
for i in range(7):
    b.put(15 - i, 1 + i, 'H')
    b.put(15 - i, 2 + i, 's')
b.pts([(15, 1)], '.')
b.pts([(10, 1), (12, 0), (13, 3), (14, 5), (11, 3)], '^')
b.put(12, 2, '*')
ICONS['honing'] = [c, b]

# ---------------------------------------------------------------- feats
# A Hundred Hammers: hammer after hammer, worn smooth with use
c = Cv()
hammer(c, 2, 6, L=10, half=2.2, w=1.1, d=(2, 3))
hammer(c, 13, 6, L=10, half=2.2, w=1.1, d=(-2, 3))
c2 = Cv()
hammer(c2, 7, 2, L=13, half=2.6, w=1.3, d=(0, 1))
c2.rect(5, 13, 9, 13, 'L')
ICONS['a_hundred_hammers'] = [c, c2]

# Black Iron: the darkest metal, hardest won
c = Cv()
c.rows([
    '   ----------   ',
    '  ------------  ',
    ' -------------- ',
    '}}}}}}}}}}}}}}}}',
], 0, 8)
c.rows([
    '   ---------- ',
    '  ------------',
    ' -------------',
    '}}}}}}}}}}}}}}',
], 1, 4)
c.pts([(4, 5), (5, 5), (6, 5), (3, 9), (4, 9)], 'm')
c.pts([(13, 1), (12, 2), (14, 2), (13, 3)], '^')
c.put(13, 2, '*')
ICONS['black_iron'] = c

# ---------------------------------------------------------------- teaching
# Folded Steel: a blade showing the water-pattern of folded steel
c = Cv()
blade(c, 14, 1, 11, 'Hhs', guard=2, grip=2)
for i in range(0, 11):
    x, y = 14 - i, 1 + i
    wave = [0, 1, 2, 1][i % 4]
    for k in range(3):
        if i < k:
            continue
        ch = {0: 'H', 1: '0', 2: 's'}[k]
        if k == wave:
            ch = 'x' if k == 2 else ('h' if k == 0 else 'x')
        c.put(x + k, y, ch)
c.pts([(4, 9), (8, 13)], 'y')
ICONS['folded_steel'] = c
