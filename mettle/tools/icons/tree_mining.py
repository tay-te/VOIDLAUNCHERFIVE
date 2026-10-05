# Mining perks that the original Mining set (icons.py) did not cover: Prospector and the earned perks.
import math
from perkkit import Cv, arc, disc

ICONS = {}

# Prospector: a prospector's pan with nuggets showing in the gravel
c = Cv()
for y in range(16):
    for x in range(16):
        e = ((x - 7.5) / 7.4) ** 2 + ((y - 9.5) / 4.6) ** 2
        if e <= 1:
            c.put(x, y, 'X')
        if ((x - 7.5) / 5.9) ** 2 + ((y - 9.0) / 3.4) ** 2 <= 1:
            c.put(x, y, 'i')
for (x, y) in ((4, 8), (6, 10), (9, 8), (11, 10), (8, 11), (3, 10), (12, 8), (5, 7), (9, 11)):
    c.put(x, y, 't')
for (x, y) in ((6, 8), (7, 8), (6, 9), (10, 10), (11, 9), (11, 10), (4, 10), (8, 10), (9, 10)):
    c.put(x, y, 'G')
c.pts([(6, 8), (10, 9)], 'y')
c.pts([(13, 2), (12, 3), (14, 3), (13, 4), (3, 4)], '*')
ICONS['prospector'] = c

# Diamond Hands (feat): a fist that will not let go of its diamond
c = Cv()
c.rows([
    '   eEEEEe   ',
    '  eEaEEaEe  ',
    ' EEEEEEEEEE ',
    ' eEEEEEEEEe ',
    '  eEEEEEEe  ',
    '   eEEEEe   ',
], 2, 1)
h = Cv()
h.rows([
    '   NNN NNN NNN  ',
    '  N2NNN2NNN2NNN ',
    '  NNN1NNN1NNN1N ',
    '  NNN1NNN1NNN1N ',
    '  1NNNNNNNNNNNN ',
    '  N22222222NNNN ',
    '   NNNNNNNNNNN  ',
    '    LLLLLLLLL   ',
    '    LLLLLLLLL   ',
], 0, 7)
ICONS['diamond_hands'] = [c, h]

# Down There (feat): an eye that has learned the deep dark
c = Cv()
c.rect(1, 1, 14, 14, 'K')
for y in (4, 11):
    c.where(lambda x, yy, y=y: yy == y and 1 <= x <= 14 and (x + y) % 5 != 0, 'z')
c.rows([
    '   UUUUUU   ',
    ' UUUUUUUUUU ',
    'UUUUA%%AUUUU',
    ' UUUUUUUUUU ',
    '   UUUUUU   ',
], 2, 6)
c.pts([(7, 7), (8, 7)], 'A')
c.pts([(7, 9), (8, 9)], 'A')
ICONS['down_there'] = c

# Forgemaster (synergy, Mining + Smithing): pick and hammer crossed over a fresh ingot
c = Cv()
c.line(1, 1, 11, 11, 'W')
c.pts([(0, 3), (1, 2), (2, 1), (3, 0)], 'S')
c.pts([(0, 4), (4, 0)], 's')
c.line(14, 1, 4, 11, 'W')
c.rows(['SSS', 'SSSS', ' SSS'], 12, 0)
c.rows([
    '  hhhhhhhh  ',
    ' HSSSSSSSSs ',
    ' ssssssssss ',
], 2, 12)
c.rows([
    '  yGGGGGGg  ',
    ' yGGGGGGGGg ',
    ' gggggggggg ',
], 2, 12)
c.pts([(1, 11), (14, 11)], '^')
ICONS['forgemaster'] = c

# Stonesense (teaching): a low hum through the rock, leading the pick to a diamond
c = Cv()
c.rect(9, 9, 15, 15, 'T')
c.pts([(10, 10), (14, 14)], 'i')
c.rows([' EE ', 'EaEE', 'EEEe', ' ee '], 11, 11)
for r in (3.6, 5.6, 7.6):
    arc(c, 12.5, 12.5, r - 0.45, r + 0.45, 115, 155, '~')
c.line(0, 15, 6, 9, 'W')
c.pts([(3, 6), (4, 6), (5, 6), (6, 7), (7, 8), (8, 9), (8, 10)], 'S')
c.pts([(2, 5), (9, 11)], 's')
ICONS['stonesense'] = c

REF = ['quarryman', 'ore_sense', 'sure_footing', 'rock_hauler', 'gem_cutter', 'hard_hat', 'strike_the_seam',
       'lantern_eyes', 'tunneler', 'motherlode', 'deep_delver', 'mountain_breaker']
