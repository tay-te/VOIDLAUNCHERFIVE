# Stealth perks: the soft step, the dark, the knife from behind.
from perkkit import Cv, blade, arrow_diag

ICONS = {}


def hood(c, ox, oy, body='P', face='%', eyes=None):
    """A hooded figure from the chest up, 9 wide."""
    c.rows([
        '   PPP   ',
        '  PPPPP  ',
        ' PPP%PPP ',
        ' PP%%%PP ',
        ' PP%%%PP ',
        ' PPP%PPP ',
        'PPPPPPPPP',
        'PPPPPPPPP',
        'PPPPPPPPP',
    ], ox, oy)
    if body != 'P' or face != '%':
        for y in range(9):
            for x in range(9):
                ch = c.get(ox + x, oy + y)
                if ch == 'P':
                    c.put(ox + x, oy + y, body)
                elif ch == '%':
                    c.put(ox + x, oy + y, face)
    if eyes:
        c.pts([(ox + 3, oy + 3), (ox + 5, oy + 3)], eyes)


def dagger(c, tx, ty, L=6, bands='Hs'):
    """A short dagger pointing up-right with its tip at (tx, ty)."""
    return blade(c, tx, ty, L, bands=bands, guard=1, grip=2, pommel='G', gch='x', grch='D')


def boot(c, ox, oy, ch='D'):
    c.rows([
        '  DDD ',
        '  DDD ',
        '  DDD ',
        '  DDDD',
        'DDDDDD',
        'dddddd',
    ], ox, oy)
    if ch != 'D':
        for y in range(6):
            for x in range(6):
                if c.get(ox + x, oy + y) == 'D':
                    c.put(ox + x, oy + y, ch)


def moon(c, ox, oy):
    c.rows([
        ' yy ',
        'y   ',
        'y   ',
        ' yy ',
    ], ox, oy)
    c.pts([(ox + 1, oy + 1), (ox + 1, oy + 2)], 'G')


# ---------------------------------------------------------------- roots
# Soft Step: a boot set down lightly, sound lines fading
c = Cv()
boot(c, 3, 8)
c.rows(['~ ~', ' ~ ', '~ ~'], 11, 3)
c.pts([(10, 7), (12, 7)], '~')
c.rows([':  :'], 1, 15)
ICONS['soft_step'] = c

# Shadow: a hooded figure half in the dark
c = Cv()
hood(c, 3, 4)
c.where(lambda x, y: x >= 8 and y >= 4 and c.get(x, y) in 'P%', 'K')
c.pts([(5, 7), (7, 7)], 'a')
ICONS['shadow'] = c

# ---------------------------------------------------------------- path A: Ghost
# Quiet Feet: a boot over a sculk sensor that stays dark
c = Cv()
boot(c, 5, 2)
c.rows([
    'E  E  E  E ',
    'EEEEEEEEEEE',
    'KKKKKKKKKKK',
    'KzKzKzKzKzK',
], 2, 11)
c.rows(['~', ' ', '~'], 13, 3)
ICONS['quiet_feet'] = c

# Cloak of Dusk: a cloak under the crescent moon
c = Cv()
moon(c, 11, 0)
c.rows([
    '    PPP     ',
    '   PPPPP    ',
    '  PP%%%PP   ',
    '  PP%%%PP   ',
    '  PPP%PPP   ',
    ' PPPPPPPPP  ',
    ' PPPPPPPPP  ',
    'PPPPPPPPPPP ',
    'PPPPPPPPPPP ',
    'PPPPPpPPPPP ',
    'PPPPpppPPPP ',
], 1, 5)
ICONS['cloak_of_dusk'] = c

# Vanish: a figure fading into dots
c = Cv()
hood(c, 2, 4)
c.where(lambda x, y: x >= 7 and c.get(x, y) in 'P%', '.')
c.pts([(7, 5), (8, 7), (7, 9), (9, 10), (8, 12), (10, 8), (11, 11), (12, 6), (13, 9)], ':')
ICONS['vanish'] = c

# Silent Fall: a feather drifting down
c = Cv()
c.rows([
    '         U ',
    '        UUU',
    '       UUUo',
    '      UUUo ',
    '     UUUo  ',
    '    UUUo   ',
    '   UUUo    ',
    '  UUo      ',
    ' Uo        ',
    'W          ',
], 2, 1)
c.rows(['~ ~ ~'], 6, 14)
ICONS['silent_fall'] = c

# ---------------------------------------------------------------- path B: Knife in the Dark
# Ambusher: a knife from behind a bush
c = Cv()
dagger(c, 13, 1, 7)
c.rows([
    '  FF  FF ',
    ' FFFFFFFF',
    'FFuFFFFuF',
    'FFFFFuFFF',
    'FFFFFFFFF',
    ' FFfFFfF ',
], 0, 9)
ICONS['ambusher'] = c

# Hamstring: a knife across a heel, a red mark
c = Cv()
boot(c, 1, 9, 'L')
dagger(c, 14, 2, 7)
c.pts([(4, 11), (5, 12), (6, 13)], 'R')
ICONS['hamstring'] = c

# Unseen Blade: a knife beside a closed eye
c = Cv()
c.rows([
    ' UUUUU ',
    'UU%%%UU',
    '  %%%  ',
], 0, 2)
c.rows(['%%%%%%%'], 0, 3)
dagger(c, 14, 4, 8)
ICONS['unseen_blade'] = c

# Poisoner: a knife dripping green
c = Cv()
dagger(c, 13, 1, 8, bands='Hu')
c.pts([(10, 6), (10, 7), (11, 9), (11, 10), (8, 9)], 'F')
c.pts([(10, 8), (11, 11)], 'u')
ICONS['poisoner'] = c

# ---------------------------------------------------------------- masteries
# Ghost: a pale hood, nothing inside
c = Cv()
hood(c, 3, 3, body='U', face='%')
c.rows([' U U U U '], 3, 12)
ICONS['ghost'] = c

# Shadowstep: a dark figure, an arrow round behind another
c = Cv()
c.rows([
    ' KK ',
    'KKKK',
    'KKKK',
    ' KK ',
    ' KK ',
    'K  K',
], 1, 6)
c.rows([
    ' TT ',
    'TTTT',
    'TTTT',
    ' TT ',
    ' TT ',
    'T  T',
], 9, 6)
c.rows(['  ~~~~~~~ ', ' ~       ~', '~         '], 3, 2)
c.pts([(13, 4), (14, 4), (14, 3)], '^')
ICONS['shadowstep'] = c

# Assassin: a knife and three red marks
c = Cv()
dagger(c, 13, 2, 9)
c.pts([(2, 2), (2, 3), (4, 1), (4, 2), (6, 2), (6, 3)], 'R')
ICONS['assassin'] = c

# ---------------------------------------------------------------- feats, Teaching, synergies
# Never Seen: a closed eye
c = Cv()
c.rows([
    '              ',
    '  UUUUUUUUUU  ',
    ' UU        UU ',
    'U  %%%%%%%%  U',
    '   ~  ~~  ~   ',
], 1, 5)
ICONS['never_seen'] = c

# Under Its Nose: the Warden's head, and a small figure beneath it
c = Cv()
c.rows([
    ' a        a ',
    'aE        Ea',
    'EEEEEEEEEEEE',
    'EEzzEEEEzzEE',
    'EEEEEEEEEEEE',
    'EEEzzzzzzEEE',
    ' EEEEEEEEEE ',
], 2, 1)
c.rows([' P ', 'PPP', 'P P'], 7, 12)
ICONS['under_its_nose'] = c

# The Quiet Way: the Nightwalker's book under the moon
c = Cv()
moon(c, 11, 0)
c.rows([
    'DDDDDDDDDD',
    'DQQQQQQQQD',
    'DQOOOOOQQD',
    'DQQQQQQQQD',
    'DQOOOOQQQD',
    'DQQQQQQQQD',
    'DQOOOOOQQD',
    'DDDDDDDDDD',
    'dddddddddd',
], 1, 5)
ICONS['the_quiet_way'] = c

# Night Hunter: an arrow under the crescent
c = Cv()
moon(c, 1, 1)
arrow_diag(c, 14, 1, 13)
ICONS['night_hunter'] = c

# Footpad: a ladder, and quiet prints beside it
c = Cv()
for y in range(1, 15):
    c.put(2, y, 'W')
    c.put(6, y, 'W')
for y in (3, 7, 11):
    c.rows(['vvv'], 3, y)
c.rows(['DD', 'DD'], 10, 12)
c.rows(['DD', 'DD'], 12, 8)
c.rows(['DD', 'DD'], 10, 4)
ICONS['footpad'] = c
