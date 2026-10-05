# Blades perks: the long blade, its timing and its reach.
from perkkit import Cv, blade, diag, arc, disc, mirror, heater, axe, sword_v

ICONS = {}

# ---------------------------------------------------------------- roots
# Fencer: a sword mid-swing, its tip trailing a swing arc
c = Cv()
blade(c, 13, 2, 9, 'Hhs')
diag(c, 1, 9, 6, '~'); diag(c, 3, 11, 3, ':')
diag(c, 2, 4, 3, ':')
ICONS['fencer'] = c

# Quick Draw: the blade half out of its scabbard, a glint on the steel
c = Cv()
blade(c, 14, 1, 11, 'Hs', guard=2, grip=2)
for x in range(16):
    for y in range(16):
        if 15 <= x + y <= 17 and x - y >= 3:
            c.put(x, y, 'L')
        if 15 <= x + y <= 17 and x - y in (3, 4):
            c.put(x, y, 'G')
        if 15 <= x + y <= 17 and x - y >= 13:
            c.put(x, y, 'G')
c.pts([(5, 5), (4, 6), (6, 6), (5, 7)], '^')
c.put(5, 6, '*')
ICONS['quick_draw'] = c

# ---------------------------------------------------------------- path A: Duelist
# Measure: a blade laid against a measuring rule that runs past its tip
c = Cv()
c.rows([
    '    G           ',
    ' LLLyHHHHHHHh   ',
    'GLLLGssssssss   ',
    '    G           ',
], 0, 4)
c.rows([
    'vvvvvvvvvvvvvvvv',
    'WqWWWqWWWqWWWqWW',
    'WqWqWqWqWqWqWqWq',
    'wwwwwwwwwwwwwwww',
], 0, 10)
ICONS['measure'] = c

# Disarm: a sword spinning up out of an opened hand
c = Cv()
c.rows([
    '         G      ',
    ' hHHHHHHHGLLG   ',
    '  ssssssssG     ',
], 0, 2)
c.put(9, 2, 'G'); c.put(9, 4, 'G'); c.put(9, 3, 'y')
c.rows([
    '   ~~~~~        ',
], 0, 0)
c.rows([
    '              ~ ',
    '             ~  ',
], 0, 5)
c.rows([
    '      N N N     ',
    '      N N N N   ',
    '     NNNNNN N   ',
    '     NNNNNNNN   ',
    '      NNNNNN    ',
    '       NNNN     ',
], 0, 9)
c.pts([(3, 7), (2, 8)], '~')
ICONS['disarm'] = c

# Momentum: a blade with rising marks, each hit heavier than the last
c = Cv()
blade(c, 10, 1, 8, 'Hs', guard=2, grip=2)
for i, y in enumerate((11, 6, 1)):
    c.rows(['   G   ', '  GGG  ', ' GG GG ', 'GG   GG'], 9, y)
ICONS['momentum'] = c

# Single Combat: a sword planted inside a ring scratched in the dirt
ring = Cv()
ring.rows([
    '    CCCCCCCC    ',
    '  CC        CC  ',
    ' C            C ',
    ' C            C ',
    '  CC        CC  ',
    '    CCCCCCCC    ',
], 0, 9)
sw = Cv()
sword_v(sw, 7, 12, 8, guard=2, grip=2)
ICONS['single_combat'] = [ring, sw]

# ---------------------------------------------------------------- path B: Skirmisher
# Light Feet: a boot and a feather, a step that weighs nothing
fe = Cv()
fe.rows([
    '      UU',
    '     UUo',
    '    UUUo',
    '   UUUo ',
    '  UUUo  ',
    ' UUoo   ',
    'Uo      ',
    'o       ',
], 0, 0)
bo = Cv()
bo.rows([
    '      jjjj ',
    '      LLLL ',
    '      LLkL ',
    '      LLLk ',
    '      LLkL ',
    '     LLLLLL',
    '   LLLLLLLL',
    '  LLLLLLLLL',
    '  wwwwwwwww',
], 5, 6)
bo.line(0, 11, 4, 11, '~'); bo.line(1, 14, 5, 14, '~')
ICONS['light_feet'] = [fe, bo]

# Wide Arc: a blade that sweeps round in a broad crescent
c = Cv()
for y in range(16):
    for x in range(16):
        o = (x - 7.5) ** 2 + (y - 8.0) ** 2 <= 7.3 ** 2
        i = (x - 8.6) ** 2 + (y - 7.0) ** 2 <= 5.6 ** 2
        if o and not i and not (x > 9 and y < 6):
            c.put(x, y, '~')
        if o and not i and not (x > 9 and y < 6) and (x - 8.6) ** 2 + (y - 7.0) ** 2 <= 6.5 ** 2:
            c.put(x, y, ',')
blade(c, 13, 2, 8, 'Hhs', guard=2, grip=2)
ICONS['wide_arc'] = c

# Bleed: a cutting edge, and the wound it leaves
c = Cv()
c.rows([
    '       R  ',
    '      RRR ',
    '      RRR ',
    '     RRRRR',
    '     RRRRR',
    '     RRnRR',
    '      RRR ',
], 5, 7)
c.rows([' R ', 'RRR', ' R '], 3, 11)
c.rows(['r'], 6, 14)
blade(c, 13, 0, 7, 'Hs', guard=0, grip=0, pommel=None)
c.pts([(7, 6), (8, 6)], '.')
c.pts([(10, 3), (9, 4)], 'R')
ICONS['bleed'] = c

# Cut and Run: a bloodied blade carried forward at a run
c = Cv()
blade(c, 14, 1, 9, 'Hhs', guard=2, grip=2)
c.pts([(14, 1), (13, 2), (14, 2), (12, 3)], 'R')
c.put(14, 3, 'r')
c.line(0, 4, 5, 4, '~'); c.line(1, 7, 5, 7, '~'); c.line(0, 10, 2, 10, '~')
ICONS['cut_and_run'] = c

# ---------------------------------------------------------------- masteries
# Duelist: two blades caught in a parry, a spark where they bind
c = Cv()
blade(c, 12, 2, 9, 'Hs', guard=2, grip=2)
m = Cv()
blade(m, 12, 2, 9, 'sH', guard=2, grip=2)
mirror(m)
for y in range(16):
    for x in range(16):
        ch = m.get(x, y)
        if ch != '.' and (c.get(x, y) == '.' or y > 6):
            if c.get(x, y) in 'GyL' and ch in 'Hs':
                continue
            c.put(x, y, ch)
c.pts([(7, 5), (8, 5), (6, 6), (9, 6), (6, 7), (9, 7), (7, 8), (8, 8)], '^')
c.pts([(7, 6), (8, 6), (7, 7), (8, 7)], '*')
c.pts([(7, 4), (8, 4), (5, 6), (10, 7)], '^')
ICONS['duelist'] = c

# Whirlwind: a blade at the heart of a full spin, three swirling cuts around it
import math
c = Cv()
for y in range(16):
    for x in range(16):
        dx, dy = x - 7.5, 7.5 - y
        r = math.hypot(dx, dy)
        ang = math.degrees(math.atan2(dy, dx)) % 360
        for a0 in (20, 140, 260):
            t = ((ang - a0) % 360) / 100.0
            if t <= 1.0:
                w = 0.6 + 2.2 * t
                if 7.3 - w <= r <= 7.4:
                    c.put(x, y, ',' if r > 7.3 - w * 0.55 else '~')
blade(c, 11, 4, 6, 'Hhs', guard=2, grip=1)
ICONS['whirlwind'] = c

# Executioner: the broad headsman's sword over a skull
c = Cv()
c.rows([
    '  @@@@@  ',
    ' @@@@@@@ ',
    '@@@@@@@@@',
    '@%%@@@%%@',
    '@%%@@@%%@',
    ' @@@%@@@ ',
    '  @&@&@  ',
], 6, 9)
blade(c, 12, 0, 10, 'Hhs', guard=2, grip=2)
c.pts([(12, 0), (13, 0), (13, 1), (14, 1), (14, 2)], 'h')
c.put(14, 1, 's'); c.put(14, 2, 's')
ICONS['executioner'] = c

# ---------------------------------------------------------------- feats
# Unbroken: a clean blade through a victor's wreath
c = Cv()
for y in range(16):
    for x in range(16):
        d = ((x - 7.5) ** 2 + (y - 8) ** 2) ** 0.5
        if 4.6 <= d <= 6.6 and not (y < 4 and 5 <= x <= 10):
            c.put(x, y, 'F')
for (x, y) in [(2, 5), (1, 8), (2, 11), (13, 5), (14, 8), (13, 11), (4, 13), (11, 13)]:
    c.put(x, y, 'u')
blade(c, 13, 1, 10, 'Hs', guard=2, grip=2)
ICONS['unbroken'] = c

# Giant Slayer: a horned giant's skull with a sword driven into its crown
sk = Cv()
sk.rows([
    'Z              Z',
    'ZZ            ZZ',
    ' ZZ          ZZ ',
    ' ZZZ        ZZZ ',
    '  ZZZ@@@@@@ZZZ  ',
    '   ZZ@@@@@@ZZ   ',
    '   @@@@@@@@@@   ',
    '  @@@@@@@@@@@@  ',
    '  @%%%@@@@%%%@  ',
    '  @%%%@@@@%%%@  ',
    '  @@@@@%%@@@@@  ',
    '   @@@@%%@@@@   ',
    '    @@@@@@@@    ',
    '    @&@&@&@&    ',
    '     @@@@@@     ',
], 0, 1)
sk.pts([(9, 5), (9, 6), (10, 7)], '&')
swd = Cv()
sword_v(swd, 7, 5, 3, up=False, guard=2, grip=1)
ICONS['giant_slayer'] = [sk, swd]

# ---------------------------------------------------------------- teaching
# Master Strike: a counter that drives clean through plate
sw = Cv()
blade(sw, 14, 1, 11, 'Hhs', guard=2, grip=2)
pl = Cv()
pl.rows([
    '  SSS    SSS ',
    '  SSSS  SSSS ',
    ' SSSSSSSSSSS ',
    ' SSSSSSSSSSS ',
    ' SSSSSSSSSSS ',
    '  SSSSSSSSS  ',
    '  SSSSSSSSS  ',
    '   SSSSSSS   ',
], 1, 4)
pl.pts([(7, 7), (7, 8), (7, 9), (7, 10)], 's')
ICONS['master_strike'] = [sw, pl]

# ---------------------------------------------------------------- synergies
# Sword and Board (Blades + Defense)
c = Cv()
heater(c, 1, 2, 10, 12)
blade(c, 14, 1, 10, 'Hs', guard=2, grip=2)
ICONS['sword_and_board'] = c

# Weapon Master (Blades + Heavy Weapons): sword and axe crossed
ax = Cv()
axe(ax, 15, 15, 4, 3, side=-1, k=1.3)
sw = Cv()
blade(sw, 13, 1, 10, 'Hs', guard=2, grip=2)
ICONS['weapon_master'] = [ax, sw]
