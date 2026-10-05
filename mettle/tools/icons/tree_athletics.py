# Athletics perks: the load you carry, and how far and how freely you move.
import math
from perkkit import BOOT, SACK, STRING, PRINT, Cv, arc, disc, heart, poly

ICONS = {}

# ---------------------------------------------------------------- roots
# Pack Mule: the long-eared, pale-nosed pack animal in its halter
c = Cv()
c.rows([
    ' 33          33 ',
    ' ZZ4        4ZZ ',
    '  ZZ4      4ZZ  ',
    '   ZZZ    ZZZ   ',
    '    ZZZZZZZZ    ',
    '    ZZZZZZZZ    ',
    '   Z%ZZZZZZ%Z   ',
    '    ZZZZZZZZ    ',
    '    ZZZZZZZZ    ',
    '     ZZZZZZ     ',
    '     LLLLLL     ',
    '     UUUUUU     ',
    '     U%UU%U     ',
    '      UUUU      ',
], 0, 1)
c.pts([(4, 11), (11, 11), (4, 10), (11, 10)], 'L')
c.put(6, 11, 'G')
ICONS['pack_mule'] = c

# Runner: a boot in full stride, kicking up dust
c = Cv()
c.rows(BOOT, 5, 2)
c.rows([
    ' UU  ',
    'UUUU ',
    ' UUUU',
], 0, 10)
c.rows([' UU', 'UUU'], 3, 13)
c.line(0, 4, 4, 4, '~'); c.line(1, 7, 4, 7, '~')
ICONS['runner'] = c

# ---------------------------------------------------------------- path A: Porter
# Strong Back: a heavy sack hoisted on a tumpline strap
c = Cv()
c.rows([
    '      ll      ',
    '     CLLC     ',
    '      CC      ',
    '    CCCCCC    ',
    '  CCCCCCCCCC  ',
    ' CCCCCCCCCCCC ',
    'CCCCCCCCCCCCCC',
    'CCCCCCCCCCCCCC',
    'CCCCCCCCCCCCCC',
    'CCCCCCCCCCCCCC',
    ' CCCCCCCCCCCC ',
    '  CCCCCCCCCC  ',
], 1, 3)
for (x, y) in ((4, 9), (6, 12), (10, 11), (8, 8), (11, 13)):
    c.put(x, y, 'c')
c.line(1, 9, 14, 9, 'L')
c.line(1, 10, 14, 10, 'l')
c.line(1, 9, 4, 1, 'L'); c.line(14, 9, 11, 1, 'L')
c.line(4, 1, 11, 1, 'L')
ICONS['strong_back'] = c

# Steady Pace: a bundle on a stick over the shoulder, the long walk at an even pace
c = Cv()
c.line(1, 15, 10, 6, 'W')
c.rows([
    '    LL    ',
    '   CCCC   ',
    '  CCRCCC  ',
    ' CCCCCRCC ',
    ' CRCCCCCC ',
    ' CCCCRCCC ',
    '  CCCCCC  ',
    '   CCCC   ',
], 6, 0)
c.pts([(10, 1), (11, 1)], 'L')
ICONS['steady_pace'] = c

# Beast of Burden: a towering load, crate and sacks lashed together
c = Cv()
c.rows([
    '    CCCCCC     ',
    '   CCCCCCCC    ',
    '   CCCCCCCC    ',
    '    CCCCCC     ',
], 1, 0)
c.rows([
    'WWWWWWWWWWWWW',
    'WvvvvvvvvvvvW',
    'WWWWWWWWWWWWW',
    'WvvvvvvvvvvvW',
    'WWWWWWWWWWWWW',
], 1, 4)
c.rows([
    '  CCCCC   CCCCC ',
    ' CCCCCCC CCCCCCC',
    ' CCCCCCC CCCCCCC',
    ' CCCCCCC CCCCCCC',
    '  CCCCC   CCCCC ',
], 0, 9)
c.line(7, 0, 7, 15, 'L')
c.pts([(6, 6), (8, 6)], 'l')
c.line(0, 11, 15, 11, 'L')
c.rows(['  ', '  '], 0, 14)
ICONS['beast_of_burden'] = c

# Hauler's Stride: a full sack carried at a run
c = Cv()
c.rows(SACK, 1, 0)
b = Cv()
b.rows(BOOT, 6, 7)
b.line(0, 12, 4, 12, '~'); b.line(1, 15, 5, 15, '~')
ICONS['haulers_stride'] = [c, b]

# ---------------------------------------------------------------- path B: Freerunner
# Swimmer: rolling water and rising bubbles
c = Cv()
c.rows([
    '  <<<     <<<   ',
    ' <<<<<   <<<<<  ',
    '<<<<<<<<<<<<<<<<',
    '{{{{{{{{{{{{{{{{',
], 0, 6)
c.rows([
    '    <<<     <<< ',
    '   <<<<<   <<<<<',
    '<<<<<<<<<<<<<<<<',
    '{{{{{{{{{{{{{{{{',
], 0, 11)
c.pts([(3, 6), (10, 6), (5, 11), (12, 11)], '>')
c.rows([' > ', '> >', ' > '], 3, 1)
c.put(9, 0, '>'); c.put(12, 3, '>')
ICONS['swimmer'] = c

# Soft Landing: dropping onto a hay bale
c = Cv()
c.rows([
    '[[[[[[[[[[[[[[',
    '[[[[[[[[[[[[[[',
    ']]]]]]]]]]]]]]',
    '[[[[[[[[[[[[[[',
    '[[[[[[[[[[[[[[',
    '[[[[[[[[[[[[[[',
], 1, 10)
c.pts([(4, y) for y in range(10, 16)] + [(11, y) for y in range(10, 16)], 'W')
for (x, y) in ((2, 13), (7, 11), (9, 14), (13, 11)):
    c.put(x, y, ']')
b = Cv()
b.rows(BOOT[1:], 3, 0)
b.pts([(1, 2), (1, 3), (1, 4), (14, 3), (14, 4), (14, 5)], '~')
ICONS['soft_landing'] = [c, b]

# Vaulter: stepping straight up onto a block at a sprint
c = Cv()
c.rect(8, 8, 15, 15, 'T')
c.pts([(9, 9), (13, 12), (10, 14)], 't'); c.pts([(12, 9), (14, 14)], 'i')
b = Cv()
b.rows(BOOT, 5, 0)
b.pts([(1, 13), (2, 12), (3, 11), (4, 10)], ':')
b.pts([(0, 3), (1, 3), (2, 3), (0, 6), (1, 6)], '~')
ICONS['vaulter'] = [c, b]

# Spring: a long, high sprint-jump over a gap
c = Cv()
c.rect(0, 13, 3, 15, 'V'); c.rect(12, 13, 15, 15, 'V')
c.rect(0, 13, 3, 13, '?'); c.rect(12, 13, 15, 13, '?')
for (x, y) in ((2, 11), (3, 9), (12, 9), (13, 11)):
    c.put(x, y, ':')
b = Cv()
b.rows(BOOT, 3, 0)
ICONS['spring'] = [c, b]

# ---------------------------------------------------------------- masteries
# Ox: the great-horned ox, strongest of the burden beasts
c = Cv()
c.rows([
    '@@            @@',
    '&@@          @@&',
    ' &@@@      @@@& ',
    '   ZZZZZZZZZZ   ',
    '  ZZZZZZZZZZZZ  ',
    ' 3ZZZZZZZZZZZZ3 ',
    '   Z%ZZZZZZ%Z   ',
    '   ZZZZZZZZZZ   ',
    '    ZZZZZZZZ    ',
    '    44444444    ',
    '    4%4444%4    ',
    '    44GGGG44    ',
    '     4G44G4     ',
    '      GGGG      ',
], 0, 1)
c.pts([(6, 4), (7, 4), (8, 4), (9, 4)], '4')
ICONS['ox'] = c

# Marathon: a trail cairn, stone on stone, one of many along the long road
c = Cv()
c.rows([
    '     TTTT     ',
    '    TTTTTT    ',
    '   tttttttt   ',
    '  TTTTTTTTTT  ',
    '  TTTTTTTTTTT ',
    ' tttttttttttt ',
    ' TTTTTTTTTTTTT',
    'TTTTTTTTTTTTTT',
    'tttttttttttttt',
], 1, 2)
c.rows([
    '?????//?????????',
    '????/////???????',
    '???////////?????',
], 0, 11)
c.rows([' TTTTTTTTTT '], 2, 10)
ICONS['marathon'] = c

# Freerunner: a hand catching the top of a wall
c = Cv()
c.rect(0, 7, 15, 15, 'T')
for y in (10, 13):
    c.rect(0, y, 15, y, 't')
for (x, y) in ((2, 8), (13, 8), (6, 11), (12, 11), (3, 14), (9, 14)):
    c.rect(x, y, x, y + 1, 't')
c.rect(0, 7, 15, 7, 'i')
h = Cv()
h.rows([
    '  N N N N  ',
    '  N N N N  ',
    '  NNNNNNN  ',
    ' NNNNNNNNN ',
    ' NNNNNNNNN ',
    '  NNNNNNN  ',
    '   NNNNN   ',
    '   LLLLL   ',
    '   LLLLL   ',
], 2, 5)
h.pts([(4, 7), (6, 7), (8, 7), (10, 7)], '1')
h.rows(['  ^  ', ' ^^^ ', '^^ ^^'], 0, 0)
h.rows(['  ^  ', ' ^^^ ', '^^ ^^'], 11, 0)
ICONS['freerunner'] = [c, h]

# ---------------------------------------------------------------- feats
# Ironlung: a long breath held deep under water
c = Cv()
for (cx, cy, r) in ((5.5, 10.5, 4.3), (11.5, 5.0, 2.6), (12.5, 13.0, 1.6), (7.0, 2.0, 1.4)):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= r + 0.3:
                c.put(x, y, '<')
            if d <= r - 0.8:
                c.put(x, y, '.')
for (x, y) in ((4, 8), (3, 9), (11, 4), (12, 12), (7, 2)):
    c.put(x, y, '*')
ICONS['ironlung'] = c

# Mountain Goat: horns curled back, a beard, and a head for heights
c = Cv()
c.rows([
    '  @@@      @@@  ',
    ' @@&@@    @@&@@ ',
    '@&   @@  @@   &@',
    '@     @  @     @',
    '      UUUU      ',
    '    6UUUUUU6    ',
    '   66U%UU%U66   ',
    '     UUUUUU     ',
    '     UUUUUU     ',
    '      UUUU      ',
    '      UUUU      ',
    '      o%%o      ',
    '       oo       ',
    '       oo       ',
    '        o       ',
], 0, 1)
c.pts([(0, 4), (15, 4)], '&')
ICONS['mountain_goat'] = c

# ---------------------------------------------------------------- teaching
# Courier's Stride: a sealed letter carried at speed
c = Cv()
c.rows([
    'QQQQQQQQQQQ',
    'QQQQQQQQQQQ',
    'OQQQQQQQQQO',
    'QOQQQQQQQOQ',
    'QQOQQQQQOQQ',
    'QQQOQQQOQQQ',
    'QQQQORROQQQ',
    'QQQQRRRRQQQ',
], 5, 5)
c.pts([(9, 11), (10, 12)], 'n')
c.line(0, 6, 3, 6, '~'); c.line(1, 9, 3, 9, '~'); c.line(0, 12, 3, 12, '~')
ICONS['couriers_stride'] = c

# ---------------------------------------------------------------- synergy
# Trailblazer (Survival + Athletics): a signpost at a crossroads
c = Cv()
c.rect(7, 2, 8, 15, 'W')
c.rows([
    ' WWWWWWWW ',
    'WWWWWWWWWWW',
    ' WWWWWWWW ',
], 5, 2)
c.rows([
    ' WWWWWWWW ',
    'WWWWWWWWWWW',
    ' WWWWWWWW ',
], 0, 6)
c.pts([(5, 2), (15, 3), (5, 4)], '.')
c.pts([(0, 7), (10, 6), (10, 8)], '.')
for (x, y) in ((7, 3), (9, 3), (11, 3), (3, 7), (5, 7)):
    c.put(x, y, 'q')
c.rows(['??', '????', '?????'], 4, 13)
c.rows(['?? ', '???'], 10, 14)
ICONS['trailblazer'] = c
