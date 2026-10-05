# General feats: earned by anyone, not tied to one tree.
import math
from perkkit import Cv, arc, blade, disc, hammer, heart, poly, axe

ICONS = {}

# Nine Lives: the black cat that always lands on its feet
c = Cv()
c.rows([
    ' K          K  ',
    ' KK        KK  ',
    ' KPK      KPK  ',
    ' KKKKKKKKKKKK  ',
    'KKKKKKKKKKKKKK ',
    'KKKKKKKKKKKKKK ',
    'KKGGKKKKKKGGKK ',
    'KKG%KKKKKK%GKK ',
    'KKKKKKKKKKKKKK ',
    ' KKKKKpKKKKKK  ',
    '6~KKKK%%KKKK~6 ',
    ' KKKKKKKKKKKK  ',
    '  KKKKKKKKKK   ',
    '    KKKKKK     ',
], 0, 1)
c.pts([(0, 11), (13, 11)], '~')
c.pts([(1, 11), (12, 11)], '~')
ICONS['nine_lives'] = c

# Seen It All: a brass spyglass, and one more hill on the horizon
c = Cv()
c.rows([
    '         T      ',
    '        TTT     ',
    '   T   TTTTT    ',
    '  TTT TTTTTTT   ',
    ' TTTTTTTTTTTTT  ',
], 0, 11)
c.pts([(9, 11), (8, 12), (10, 12), (3, 13)], 'U')
s = Cv()
for i in range(0, 13):
    x, y = 2 + i, 13 - i
    if i < 5:
        ks, ch = (-1, 0, 1), ('G' if i in (0, 4) else 'L')
    elif i < 9:
        ks, ch = (-1, 0), ('g' if i == 5 else 'G')
    else:
        ks, ch = (0,), ('g' if i == 9 else 'G')
    for k in ks:
        s.put(x + k, y, ch) if k <= 0 else s.put(x, y + 1, ch)
s.pts([(1, 13), (2, 14), (1, 14)], 'b')
s.put(1, 14, 'B')
ICONS['seen_it_all'] = [c, s]

# Pack Horse: a horse laden down with packs
c = Cv()
c.rows([
    '  Z             ',
    ' ZZZ            ',
    'ZZZZ3           ',
    'ZZ ZZ3          ',
    '    ZZ3         ',
    '    ZZZZZZZZZZ  ',
    '    ZZZZZZZZZZZ3',
    '    ZZZZZZZZZZZ3',
    '     ZZZZZZZZZ 3',
    '     Z Z   Z Z  ',
    '     Z Z   Z Z  ',
    '     Z Z   Z Z  ',
    '     3 3   3 3  ',
], 0, 3)
c.put(1, 4, '%')
p = Cv()
p.rows([
    '    CCCC   ',
    '  CCCCCCCC ',
    '  CCRCCCCC ',
    ' CCCCCCCCCC',
    ' CCCCCCCCCC',
    '  CCCCCCCC ',
], 4, 4)
p.rows(['LLLLLLLLL'], 5, 7)
p.pts([(6, 3), (7, 2), (8, 2), (9, 3)], 'W')
ICONS['pack_horse'] = [c, p]

# Seasoned: thirty days through the glass, and still standing
c = Cv()
c.rows([
    'WWWWWWWWWWW',
    ' W       W ',
    ' W >>>>> W ',
    ' W  AAA  W ',
    ' W   A   W ',
    ' W   A   W ',
    ' W  > >  W ',
    ' W >>A>> W ',
    ' W AAAAA W ',
    ' W       W ',
    'WWWWWWWWWWW',
], 1, 1)
c.pts([(4, 2), (8, 2), (4, 10), (8, 10)], '>')
for y in (3, 9):
    c.pts([(3, y), (9, y)], '>')
c.pts([(3, 2), (9, 2)], '.')
h = Cv()
heart(h, 10, 10, 'R', 5)
ICONS['seasoned'] = [c, h]

# Jack of All Trades: pick, sword and hammer, a little of everything
c = Cv()
c.line(13, 14, 4, 5, 'W')
c.pts([(1, 6), (2, 5), (3, 4), (4, 3), (5, 2), (6, 1)], 'S')
c.pts([(0, 7), (7, 0)], 's')
c.pts([(3, 3), (4, 4)], 'S')
s = Cv()
blade(s, 14, 1, 10, 'Hs', guard=2, grip=2)
h = Cv()
hammer(h, 8, 3, L=11, half=2.4, w=1.2, d=(0, 1))
ICONS['jack_of_all_trades'] = [c, h, s]
