# Survival perks: the hunt, the forage and living off the land.
import math
from perkkit import STRING, PRINT, Cv, arc, disc, heart, poly, blade

ICONS = {}


def campfire(c, ox=0, oy=0, small=False):
    flame = [
        '     M    ',
        '    MM  M ',
        '    MMM MM',
        '   MMYMMMM',
        '   MMYYMMM',
        '   MYYYYMM',
        '    MYYMM ',
    ]
    c.rows(flame, ox + 1, oy + 2)
    c.line(ox + 2, oy + 14, ox + 13, oy + 11, 'W'); c.line(ox + 2, oy + 11, ox + 13, oy + 14, 'W')
    c.pts([(ox + 1, oy + 13), (ox + 14, oy + 13)], 'T'); c.pts([(ox + 0, oy + 14), (ox + 15, oy + 14)], 'T')


def knife(c, tx, ty, L=6, handle='Z'):
    """Hunting knife pointing up-right, single-edged."""
    for i in range(L):
        c.put(tx - i, ty + i, 'H')
        if i >= 1:
            c.put(tx - i + 1, ty + i, 's')
    gx, gy = tx - L, ty + L
    c.pts([(gx, gy), (gx + 1, gy + 1), (gx - 1, gy - 1)], 'x')
    for m in range(1, 4):
        c.put(gx - m, gy + m, handle)
        c.put(gx - m + 1, gy + m, handle)
    c.put(gx - 4, gy + 4, 'x')


# ---------------------------------------------------------------- roots
# Gatherer: a basket brimming with berries and leaves
c = Cv()
c.rows([
    '   u  F   uF    ',
    '  FFR FuFRRFF   ',
    ' FRRRFFRRFRRRF  ',
    'RRnRRFRnRRRnRRF ',
], 0, 3)
c.rows([
    'WWWWWWWWWWWWWWW ',
    'WvWvWvWvWvWvWvW ',
    ' WvWvWvWvWvWvW  ',
    ' vWvWvWvWvWvWv  ',
    '  WvWvWvWvWvW   ',
    '  WWWWWWWWWWW   ',
], 0, 7)
c.rows(['ww', ' w'], 0, 5)
c.line(1, 5, 4, 1, 'w'); c.line(4, 1, 10, 1, 'w'); c.line(10, 1, 13, 5, 'w')
c.rows([' ', ' '], 0, 0)
ICONS['gatherer'] = c

# Field Cook: an iron pot on the boil, a wooden spoon in it
c = Cv()
c.rows([
    ' x            x ',
    'xKKKKKKKKKKKKKKx',
    ' KKKKKKKKKKKKKK ',
    ' KKKKKKKKKKKKKK ',
    ' KKKKKKKKKKKKKK ',
    '  KKKKKKKKKKKK  ',
    '   KKKKKKKKKK   ',
    '    K      K    ',
], 0, 7)
c.rows(['MMMMMMMMMMMM'], 2, 7)
c.rows(['YMMYMYMMYM'], 3, 7)
c.line(11, 2, 9, 7, 'W'); c.pts([(11, 2), (12, 1), (12, 2)], 'W')
c.pts([(3, 2), (4, 3), (3, 4), (4, 5), (6, 0), (7, 1), (6, 2), (7, 3), (6, 4)], '~')
c.pts([(8, 0)], '.')
ICONS['field_cook'] = c

# ---------------------------------------------------------------- path A: Hunter
# Quiet Approach: eyes watching from the long grass
c = Cv()
c.rows([
    '  @@@     @@@   ',
    ' @@%@@   @@%@@  ',
    '  @@@     @@@   ',
], 0, 4)
c.rows([
    '   F    F     F ',
    ' F F  F F  F  F ',
    ' F FF F F  F FF ',
    'FF FFFF FF FFFF ',
    'FFFFFFFFFFFFFFFF',
    'FFFFFFFFFFFFFFFF',
    'FfFFfFFFfFFfFFfF',
], 0, 9)
c.pts([(2, 9), (7, 9), (12, 8), (5, 10)], 'u')
ICONS['quiet_approach'] = c

# Skinner: a skinning knife and a stretched hide
c = Cv()
c.rows([
    '  Z        Z  ',
    ' ZZZZZZZZZZZZ ',
    '  ZZZZZZZZZZ  ',
    '  ZZZ4ZZZZZZ  ',
    '  ZZZZZZZ4ZZ  ',
    '  ZZZZZZZZZZ  ',
    ' ZZZZZ4ZZZZZZ ',
    '  ZZZZZZZZZZ  ',
    '  ZZZZZZZZZZ  ',
    ' ZZZ      ZZZ ',
], 0, 1)
k = Cv()
knife(k, 15, 6, 6)
ICONS['skinner'] = [c, k]

# ---------------------------------------------------------------- path A tier III
# Stalker: a knife out of the grass, one clean stroke
c = Cv()
knife(c, 13, 2, 6)
g = Cv()
g.rows([
    '  F    F    F   ',
    ' FF F FF  F FF  ',
    ' FFFF FFF FFFF F',
    'FFFFFFFFFFFFFFFF',
    'FfFFfFFFfFFfFFfF',
], 0, 11)
ICONS['stalker'] = [c, g]

# Sixth Sense: a quickening heartbeat only you can hear
c = Cv()
heart(c, 4, 5, 'R', 7)
c.pts([(5, 6), (6, 7)], 'n')
for r in (5.0, 7.0):
    arc(c, 7.5, 8, r - 0.4, r + 0.4, 145, 215, '~')
    arc(c, 7.5, 8, r - 0.4, r + 0.4, 325, 35, '~')
ICONS['sixth_sense'] = c

# ---------------------------------------------------------------- path B: Forager
# Iron Stomach: raw meat and a spider eye, and no harm in either
c = Cv()
c.rows([
    '    ((((((    ',
    '  ((((((((((  ',
    ' (((((((((((( ',
    ' (((7(((((7(( ',
    '  (((((((((( ',
    '    (((((((   ',
], 1, 2)
c.rows([' RRR ', 'RRRRR', 'RR%RR', 'RRRRR', ' RRR '], 10, 8)
c.pts([(11, 9)], 'n')
c.rows([
    'SSSSSSSSSSSSSSSS',
    ' ssssssssssssss ',
    '   SSSSSSSSSS   ',
], 0, 13)
ICONS['iron_stomach'] = c

# Campfire Rest: warmth, stillness and a heart mending by the fire
c = Cv()
campfire(c, 0, 2)
h = Cv()
heart(h, 10, 0, 'R', 5)
ICONS['campfire_rest'] = [c, h]

# Pathfinder: a worn path through the brambles, easy going
c = Cv()
c.rows([
    '       ??       ',
    '  ????/???????  ',
    ' ??????/??????? ',
    '???????//???????',
    '??????///???????',
    '?????////???????',
    '????/////???????',
    '???//////???????',
    '??///////???????',
    '?///////????????',
], 0, 6)
c.pts([(8, 7), (9, 8)], '/')
c.rows([
    '  FFF  ',
    ' FFRFF ',
    'FRFFFRF',
    'FFFRFFF',
    'FRFFFRF',
    ' FFFF  ',
], 9, 2)
c.pts([(10, 3), (13, 6)], 'u')
ICONS['pathfinder'] = c

# Self-Sufficient: a loaf and a full waterskin, enough for days
c = Cv()
c.rows([
    '   ll    ',
    '   LL    ',
    '  LLLL   ',
    ' LLLLLL  ',
    'LLLLLLLL ',
    'LLLLLLLL ',
    'LLLLLLLL ',
    'LLLLLLLL ',
    ' LLLLLL  ',
], 8, 1)
c.pts([(10, 6), (10, 7), (11, 8)], 'j')
c.rows([
    '   AAAAA    ',
    ' AAAAAAAAA  ',
    'AAyAAyAAyAA ',
    'AAAAAAAAAAA ',
    ' gggggggggg ',
], 0, 10)
ICONS['self_sufficient'] = c

# ---------------------------------------------------------------- masteries
# Tracker: a line of fresh hoofprints leading away
c = Cv()
for (x, y) in ((0, 11), (5, 8), (2, 5), (7, 2), (11, 6), (10, 0)):
    pass
for (x, y) in ((1, 11), (6, 9), (4, 5), (9, 3), (12, 0)):
    c.rows(['Z Z', 'Z Z', 'ZZZ'], x, y) if False else None
    c.rows(['3.3', '3.3', '3.3'], x, y)
for (x, y) in ((1, 11), (6, 9), (4, 5), (9, 3)):
    pass
c = Cv()
PAW = [' P P ', 'P   P', ' PPP ', ' PPP ']
for (x, y) in ((0, 11), (6, 8), (2, 4), (9, 1)):
    c.rows([r.replace('P', PRINT) for r in PAW], x, y)
c.rows(['u u', 'FuF', 'FFF'], 12, 12)
c.rows(['u', 'F'], 14, 6)
ICONS['tracker'] = c

# Lay of the Land: a hand-drawn map, a path and the mark you have not reached yet
c = Cv()
c.rows([
    ' QQQQQQQQQQQQQ  ',
    'QQQQQQQQQQQQQQQ ',
    'QQQQQQQQQQQQQQQ ',
    'QQQQQQQQQQQQQQQ ',
    'QQQQQQQQQQQQQQQ ',
    'QQQQQQQQQQQQQQQ ',
    'QQQQQQQQQQQQQQQ ',
    'QQQQQQQQQQQQQQQ ',
    'QQQQQQQQQQQQQQQ ',
    ' QQQQQQQQQQQQQ  ',
], 0, 3)
for x in (5, 10):
    for y in range(4, 13):
        c.put(x, y, 'O')
c.pts([(2, 10), (3, 10), (4, 9), (6, 9), (7, 8), (8, 7), (9, 7)], '\\')
c.pts([(11, 6), (13, 6), (12, 5), (12, 7), (11, 4), (13, 4)], 'R')
c.pts([(11, 4), (13, 4)], '.')
c.pts([(2, 5), (3, 5), (3, 6)], '|')
c.pts([(7, 11), (8, 11), (8, 12)], '<')
ICONS['lay_of_the_land'] = c

# Living off the Land: what the wild gives - a mushroom, an apple, berries and herbs
c = Cv()
c.rows([
    '  RRRR  ',
    ' RR!RRR ',
    'RRRRR!RR',
    'RRRRRRRR',
    '  CCCC  ',
    '  CCCC  ',
    '  CCCC  ',
], 0, 6)
c.rows([
    '  w  ',
    ' FwF ',
    'RRRRR',
    'RRRnR',
    'RRRRR',
    ' RRR ',
], 10, 1)
c.rows([' R ', 'RRR', ' R R', '  RR'], 9, 11)
c.rows(['u F ', 'FFF ', ' FFu', '  F '], 4, 0)
c.rows(['F', 'Fu', 'FF', ' F'], 14, 9)
ICONS['living_off_the_land'] = c

# ---------------------------------------------------------------- feats
# Weatherwise: a heavy cloud rolling in, rain on its way
c = Cv()
c.rows([
    '     6666       ',
    '   66666666 66  ',
    '  6666666666666 ',
    ' 66666666666666 ',
    '666666666666666 ',
    ' oooooooooooo   ',
], 0, 2)
c = Cv()
c.rows([
    '     UUUU       ',
    '   UUUUUUUU UU  ',
    '  UUUUUUUUUUUUU ',
    ' UUUUUUUUUUUUUU ',
    'UUUUUUUUUUUUUUU ',
    ' TTTTTTTTTTTT   ',
], 0, 2)
for (x, y) in ((2, 9), (6, 10), (10, 9), (4, 12), (8, 13), (12, 12), (1, 14)):
    c.pts([(x, y), (x - 1, y + 1)], '<')
c.pts([(13, 9), (12, 10), (13, 10), (12, 11)], '^')
ICONS['weatherwise'] = c

# Big Game: a great spread of antlers
c = Cv()
c.rows([
    '4  4      4  4  ',
    '4 4 4    4 4 4 ',
    ' 44 4    4 44  ',
    '  44 4  4 44   ',
    ' 4  444444  4  ',
    '  444 @@@@ 444 ',
    '     @@@@@@    ',
    '      @@@@     ',
    '      @%%@     ',
    '      @@@@     ',
    '       @@      ',
], 0, 1)
c.g = [[('Z' if ch == '4' else ch) for ch in row] for row in c.g]
c.pts([(6, 7), (9, 7)], '%')
ICONS['big_game'] = c

# ---------------------------------------------------------------- teaching
# Poultice: a roll of linen and the herbs bound into it
c = Cv()
c.rows([
    '  UUUUU         ',
    ' UUoooUU        ',
    'UUoUUUoUUUUUUUU ',
    'UoUoooUoUUUUUUUU',
    'UoUoUoUoUUUUUUU ',
    'UoUUoUUoUUUUUUU ',
    'UUoooooUU       ',
    ' UUUUUUU        ',
    '  UUUUU         ',
], 0, 7)
c.rows([
    '  u  ',
    ' FuF ',
    'FFuFF',
    ' FuF ',
    '  u  ',
], 9, 1)
c.rows(['u ', 'Fu', 'FF', ' F'], 4, 2)
c.pts([(10, 10), (12, 11), (14, 10)], 'F')
ICONS['poultice'] = c
