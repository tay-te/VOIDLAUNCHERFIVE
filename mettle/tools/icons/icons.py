import math
from px import render, sheet, to_image, encode


class Cv:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.g = [['.'] * w for _ in range(h)]

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.g[y][x] = c

    def pts(self, pts, c):
        for x, y in pts:
            self.put(x, y, c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            t = i / n if n else 0
            self.put(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c)

    def where(self, fn, c):
        for y in range(self.h):
            for x in range(self.w):
                if fn(x, y):
                    self.put(x, y, c)

    def rows(self, rows, ox=0, oy=0):
        for j, r in enumerate(rows):
            for i, ch in enumerate(r):
                if ch != ' ':
                    if ch == '_':
                        self.put(ox + i, oy + j, '.')
                    else:
                        self.put(ox + i, oy + j, ch)

    def get(self, x, y):
        return self.g[y][x] if 0 <= x < self.w and 0 <= y < self.h else '.'

    def out(self):
        return [''.join(r) for r in self.g]


def pickaxe(c, cx=3, cy=13, r0=9.4, r1=11.0, a0=-92, a1=2, handle=True, hx0=2, hy0=14, head='S', hdl='W'):
    # head arc
    for y in range(c.h):
        for x in range(c.w):
            dx, dy = x - cx, cy - y
            d = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(-dy, dx))
            if r0 <= d <= r1 and a0 <= ang <= a1:
                c.put(x, y, head)
    if handle:
        mx = cx + (r0 + 0.2) * math.cos(math.radians(-45))
        my = cy + (r0 + 0.2) * math.sin(math.radians(-45))
        c.line(hx0, hy0, round(mx) - 1, round(my) + 1, hdl)


S = {}

# ---------------------------------------------------------------- skills
# Blades
c = Cv()
for x in range(5, 15):
    for y in range(0, 16):
        d = x + y
        if d == 14 and x >= 6 and y >= 1: c.put(x, y, 'H')
        if d == 15 and x >= 6 and y >= 1: c.put(x, y, 'h')
        if d == 16 and x >= 6 and y >= 2: c.put(x, y, 's')
c.put(13, 1, 'h'); c.put(14, 1, 'h'); c.put(14, 2, 's'); c.put(13, 0, '.'); c.put(14, 0, '.')
c.put(12, 1, 'H')
c.pts([(2, 7), (3, 8), (4, 9), (5, 10), (6, 11), (7, 12)], 'G')
c.pts([(3, 7), (7, 11)], 'g')
c.pts([(4, 11), (3, 12), (4, 12)], 'L')
c.pts([(1, 13), (2, 13), (1, 14), (2, 14)], 'G')
c.put(5, 10, 'y')
S['skill/Blades'] = c.out()

# Heavy weapons (war hammer)
c = Cv()
c.where(lambda x, y: x + y in (14, 15) and x - y <= 4 and x >= 1, 'W')
c.where(lambda x, y: 5 <= x - y <= 9 and 10 <= x + y <= 20, 'S')
c.where(lambda x, y: x - y == 7 and 12 <= x + y <= 18, 'h')
c.pts([(3, 12), (4, 11), (3, 11), (2, 12)], 'L')
c.pts([(1, 14), (1, 13), (0, 14)], 'w')
S['skill/Heavy Weapons'] = c.out()

# Marksmanship (bow drawn, arrow pointing left)
c = Cv()
bow = [(9, 1), (8, 1), (7, 2), (6, 3), (5, 4), (5, 5), (4, 6), (4, 7), (4, 8), (4, 9), (5, 10), (5, 11), (6, 12), (7, 13), (8, 14), (9, 14)]
c.pts(bow, 'W')
c.pts([(9, 1), (9, 14)], 'w')
c.line(9, 2, 12, 7, 'Q'); c.line(12, 8, 9, 13, 'Q')
c.line(1, 7, 13, 7, 'v')
c.pts([(0, 7), (1, 6), (1, 8), (2, 7)], 'S')
c.pts([(13, 6), (14, 6), (13, 8), (14, 8), (14, 7), (15, 7)], 'n')
S['skill/Marksmanship'] = c.out()

# Defense (heater shield, wood with iron rim + boss)
c = Cv()
def shield_in(x, y):
    if y < 1 or y > 14 or x < 2 or x > 13:
        return False
    if y <= 8:
        return True
    k = y - 8
    return 2 + k // 1 * 1 - (0) <= x + 0 <= 13 - k if False else (2 + (k * 5) // 6 <= x <= 13 - (k * 5) // 6)
c.where(shield_in, 'S')
c.where(lambda x, y: shield_in(x, y) and shield_in(x - 1, y) and shield_in(x + 1, y) and shield_in(x, y - 1) and shield_in(x, y + 1), 'W')
c.where(lambda x, y: c.get(x, y) == 'W' and x in (5, 10), 'w')
c.rect(7, 6, 8, 7, 'S'); c.put(7, 6, 'H')
S['skill/Defense'] = c.out()

# Athletics (boot with motion lines)
c = Cv()
c.rows([
    '       jjjj     ',
    '       LLLL     ',
    '       LLLL     ',
    '       LLkL     ',
    '       LLLk     ',
    '       LLkL     ',
    '      LLLLLL    ',
    '     LLLLLLLLL  ',
    '     LLLLLLLLLL ',
    '     wwwwwwwwww ',
], 0, 3)
c.line(0, 5, 4, 5, '~'); c.line(1, 8, 4, 8, '~'); c.line(0, 11, 3, 11, '~')
S['skill/Athletics'] = c.out()

# Mining (pickaxe)
c = Cv()
c.rows([
    '                ',
    '    hhhhh       ',
    '  hhSSSSSSs     ',
    ' hSSs    SSSs   ',
    ' Ss      WSSs   ',
    ' S      W  SSs  ',
    '       W    SSs ',
    '      W      Ss ',
    '     W       Ss ',
    '    W         s ',
    '   W            ',
    '  W             ',
    ' w              ',
    'w               ',
])
S['skill/Mining'] = c.out()

# Survival (campfire)
c = Cv()
c.rows([
    '      M         ',
    '     MM   M     ',
    '     MMM MM     ',
    '    MMYMMMM     ',
    '    MMYYMMM     ',
    '    MYYYYMM     ',
    '     MYYMM      ',
], 1, 2)
c.line(2, 14, 13, 11, 'W'); c.line(2, 11, 13, 14, 'W')
c.line(2, 13, 13, 10, 'w') if False else None
c.pts([(1, 13), (14, 13)], 'T'); c.pts([(0, 14), (15, 14)], 'T')
S['skill/Survival'] = c.out()

# Smithing (anvil + hot ingot)
c = Cv()
c.rows([
    '   MMMM         ',
    '  MYYYMM        ',
], 6, 3)
c.rect(1, 6, 14, 7, 'S')
c.rect(0, 6, 0, 6, 'S')
c.rect(4, 8, 11, 8, 's')
c.rect(5, 9, 10, 10, 'S')
c.rect(3, 11, 12, 12, 'S')
c.rect(2, 13, 13, 13, 's')
S['skill/Smithing'] = c.out()

# Alchemy (round flask)
c = Cv()
c.rect(6, 1, 9, 2, 'W')
c.rect(6, 3, 9, 5, 'h')
c.where(lambda x, y: (x - 7.5) ** 2 + (y - 10) ** 2 <= 24, 'h')
c.where(lambda x, y: (x - 7.5) ** 2 + (y - 10) ** 2 <= 24 and y >= 9, 'P')
c.pts([(5, 7), (4, 8)], 'H')
c.pts([(6, 11), (9, 12), (7, 13)], 'n')
c.pts([(11, 0), (12, 2), (3, 2)], '*')
S['skill/Alchemy'] = c.out()

# Scholarship (open book)
c = Cv()
c.rows([
    ' QQQQQ  QQQQQ   ',
    'QQQQQQQ QQQQQQ  ',
    'QOOOOQQRQOOOOQ  ',
    'QQQQQQQRQQQQQQ  ',
    'QOOOOQQRQOOOQQ  ',
    'QQQQQQQRQQQQQQ  ',
    'QOOOQQQ QOOOOQ  ',
    'QQQQQQQ QQQQQQ  ',
    'LLLLLLLLLLLLLLL ',
], 0, 3)
c.put(7, 4, 'O'); c.put(7, 11, 'L')
S['skill/Scholarship'] = c.out()

# Husbandry (sheep face)
c = Cv()
c.rows([
    '    UUUUUUUU    ',
    '  UUUUUUUUUUUU  ',
    ' UUUUUUUUUUUUUU ',
    ' UUUCCCCCCCCUUU ',
    'ccUCCCCCCCCCCUcc',
    'ccUC%CCCCCC%CUcc',
    '  UCCCCCCCCCCU  ',
    '   CCCCCCCCCC   ',
    '   CCCCCCCCCC   ',
    '    CCCCCCCC    ',
    '    CCCrrCCC    ',
    '     CCCCCC     ',
], 0, 2)
S['skill/Husbandry'] = c.out()

# Riding (horseshoe)
c = Cv()
c.where(lambda x, y: 3.9 ** 2 <= (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 6.9 ** 2 and y <= 7, 'S')
c.where(lambda x, y: y > 7 and 3.9 <= abs(x - 7.5) <= 6.9 and y <= 13, 'S')
c.pts([(0, 13), (15, 13), (0, 12), (15, 12)], 'S')
for p in [(2, 6), (13, 6), (2, 10), (13, 10), (5, 2), (10, 2)]:
    c.put(*p, '%')
S['skill/Riding'] = c.out()

# Stealth (hood)
c = Cv()
c.rows([
    '     DDDDD      ',
    '    DDDDDDD     ',
    '   DDDDDDDDD    ',
    '  DDDd%%%dDDD   ',
    '  DDd%%%%%dDD   ',
    '  DD%%%%%%%DD   ',
    '  DD%a%%%a%DD   ',
    '  DD%%%%%%%DD   ',
    '  DDd%%%%%dDD   ',
    ' DDDDd%%%dDDDD  ',
    ' DDDDDDDDDDDDD  ',
    'DDDDDDDDDDDDDDD ',
    'DDDDDDDDDDDDDDD ',
], 0, 1)
S['skill/Stealth'] = c.out()

# Speech (two bubbles)
c = Cv()
c.rows([
    '      hhhhhhhh  ',
    '     hhhhhhhhhh ',
    '     hhhhhhhhhh ',
    '     hhhhhhhhhh ',
    '      hhhhhhhh  ',
], 1, 1)
c.put(13, 6, 'h'); c.put(14, 7, 'h')
c.rows([
    ' QQQQQQQQQ      ',
    'QQQQQQQQQQQ     ',
    'QQ%QQ%QQ%QQ     ',
    'QQQQQQQQQQQ     ',
    ' QQQQQQQQQ      ',
    '  QQ            ',
    '  Q             ',
], 0, 6)
S['skill/Speech'] = c.out()

# Thievery (padlock + pick)
c = Cv()
c.where(lambda x, y: 2.0 ** 2 <= (x - 6.5) ** 2 + (y - 5.5) ** 2 <= 3.6 ** 2 and y <= 6, 'S')
c.rect(2, 7, 11, 14, 'G')
c.rect(6, 9, 7, 11, '%'); c.put(6, 12, '%')
c.line(9, 11, 15, 5, 'h'); c.pts([(15, 4), (14, 4)], 'h')
S['skill/Thievery'] = c.out()

# ---------------------------------------------------------------- mining perks
def stone_block(c, x0, y0, x1, y1, ch='T'):
    c.rect(x0, y0, x1, y1, ch)

# Quarryman
c = Cv()
c.rect(1, 8, 9, 15, 'T')
c.pts([(3, 10), (4, 11), (4, 12), (7, 13), (6, 9)], 't')
c.pts([(2, 9), (8, 12)], 'i')
pickaxe(c, cx=8, cy=10, r0=5.6, r1=7.0, a0=-95, a1=5, hx0=15, hy0=13, head='S', hdl='W') if False else None
c.line(15, 0, 9, 6, 'W')
c.pts([(8, 3), (9, 3), (10, 4), (11, 5), (12, 6), (12, 7)], 'S')
c.pts([(7, 2), (13, 8)], 'S')
c.pts([(10, 8), (12, 10), (11, 11)], '*')
S['perk/Quarryman'] = c.out()

# Ore Sense
c = Cv()
c.rect(1, 4, 9, 12, 'T')
c.pts([(3, 6), (4, 6), (3, 7), (6, 9), (7, 9), (7, 10), (4, 10)], 'X')
c.pts([(3, 6), (6, 9)], 'G')
for (x, y) in [(11, 6), (12, 7), (12, 8), (12, 9), (11, 10)]:
    c.put(x, y, '~')
for (x, y) in [(13, 4), (14, 5), (14, 6), (15, 7), (15, 8), (15, 9), (14, 10), (14, 11), (13, 12)]:
    c.put(x, y, '^')
S['perk/Ore Sense'] = c.out()

# Sure Footing (ladder + boot)
c = Cv()
c.rect(2, 0, 2, 15, 'W'); c.rect(9, 0, 9, 15, 'W')
for y in (2, 6, 10, 14):
    c.rect(3, y, 8, y, 'w')
c.rows([
    '   jjj  ',
    '   LLL  ',
    '   LLL  ',
    '  LLLLL ',
    ' LLLLLLL',
    ' wwwwwww',
], 7, 4)
S['perk/Sure Footing'] = c.out()

# Rock Hauler (ingots + stone + feather)
c = Cv()
c.rect(1, 11, 5, 15, 'T')
c.pts([(2, 12), (4, 14)], 't')
c.rows([
    '  hhhhhh  ',
    ' HSSSSSSs ',
    ' ssssssss ',
], 6, 13)
c.rows([
    '  hhhhhh  ',
    ' HSSSSSSs ',
    ' ssssssss ',
], 6, 10)
c.rows([
    '         UU ',
    '       UUUU ',
    '      UUUUo ',
    '     UUUUo  ',
    '    UUUUo   ',
    '   UUUoo    ',
    '  UUoo      ',
    ' Uo         ',
    'o           ',
], 2, 0)
S['perk/Rock Hauler'] = c.out()

# Gem Cutter
c = Cv()
c.rows([
    '   eEEEEe   ',
    '  eEaEEaEe  ',
    ' EEEEEEEEEE ',
    ' eEEEEEEEEe ',
    '  eEEEEEEe  ',
    '   eEEEEe   ',
    '    eEEe    ',
    '     ee     ',
], 2, 5)
c.pts([(13, 1), (12, 2), (14, 2), (13, 3), (2, 3)], '*')
S['perk/Gem Cutter'] = c.out()

# Hard Hat
c = Cv()
c.rows([
    '     SSSSS      ',
    '   SSSSSSSSS    ',
    '  SSSSSSSSSSS   ',
    '  SSSGGGSSSSS   ',
    ' SSSGYYYGSSSSS  ',
    ' SSSGYYYGSSSSS  ',
    'ssssssssssssssss',
    'ssssssssssssssss',
], 0, 4)
c.pts([(5, 5), (4, 6)], 'H')
S['perk/Hard Hat'] = c.out()

# Strike the Seam (vein + two nuggets popping)
c = Cv()
c.rect(1, 6, 11, 15, 'T')
c.line(1, 13, 11, 8, 'G')
c.line(1, 14, 11, 9, 'g')
c.rows(['GG', 'Gg'], 11, 2)
c.rows(['GG', 'Gg'], 14, 5)
c.pts([(9, 1), (13, 1), (15, 3)], '*')
S['perk/Strike the Seam'] = c.out()

# Lantern Eyes
c = Cv()
c.rows([
    '    UUUUUU    ',
    '  UUUUUUUUUU  ',
    ' UUUUAAAAUUUU ',
    'UUUUAA%%AAUUUU',
    ' UUUUAAAAUUUU ',
    '  UUUUUUUUUU  ',
    '    UUUUUU    ',
], 1, 5)
c.legend = None
for (x, y) in [(8, 0), (8, 1), (3, 2), (4, 3), (13, 2), (12, 3), (1, 8), (14, 8), (8, 14), (8, 15)]:
    c.put(x, y, '^')
S['perk/Lantern Eyes'] = c.out()

# Tunneler (stone wall with 1x2 opening)
c = Cv()
c.rect(0, 2, 13, 15, 'T')
c.rect(4, 6, 8, 15, '%')
c.pts([(4, 6), (8, 6)], 'T')
c.pts([(1, 4), (10, 9), (11, 13), (2, 12)], 't')
c.line(15, 0, 11, 4, 'W')
c.pts([(10, 1), (11, 1), (12, 2), (13, 3), (13, 4)], 'S')
S['perk/Tunneler'] = c.out()

# Motherlode
c = Cv()
c.rect(1, 2, 14, 15, 'T')
for (x, y) in [(3, 4), (4, 4), (4, 5), (7, 6), (8, 6), (8, 7), (5, 8), (6, 8), (6, 9), (10, 9), (11, 9), (11, 10), (3, 11), (4, 11), (4, 12), (8, 12), (9, 12), (9, 13), (12, 5), (12, 4)]:
    c.put(x, y, 'G')
for (x, y) in [(3, 4), (7, 6), (5, 8), (10, 9), (3, 11), (8, 12), (12, 4)]:
    c.put(x, y, 'y')
c.pts([(15, 0), (0, 1)], '*')
S['perk/Motherlode'] = c.out()

# Deep Delver (deepslate layers + down chevrons)
c = Cv()
c.rect(1, 1, 14, 14, 'K')
for y in (4, 8, 12):
    c.where(lambda x, yy, y=y: yy == y and 1 <= x <= 14 and (x + y) % 5 != 0, 'z')
c.pts([(5, 3), (6, 4), (7, 5), (8, 5), (9, 4), (10, 3)], '^')
c.pts([(5, 7), (6, 8), (7, 9), (8, 9), (9, 8), (10, 7)], '^')
c.pts([(5, 11), (6, 12), (7, 13), (8, 13), (9, 12), (10, 11)], '^')
S['perk/Deep Delver'] = c.out()

# Mountain Breaker (3x3 cubes + pick)
c = Cv()
for i in range(3):
    for j in range(3):
        c.rect(1 + i * 4, 4 + j * 4, 3 + i * 4, 6 + j * 4, 'T')
c.pts([(2, 5), (6, 9), (10, 13)], 't')
c.line(15, 0, 11, 4, 'W')
c.pts([(9, 1), (10, 1), (11, 2), (12, 2), (13, 3), (14, 4), (14, 5)], 'S')
S['perk/Mountain Breaker'] = c.out()

# ---------------------------------------------------------------- empty-slot outlines (vanilla style)
c = Cv()
c.rows([
    '                ',
    '     ######     ',
    '     #    #     ',
    '   ##########   ',
    '  #          #  ',
    '  #  ######  #  ',
    '  #  #    #  #  ',
    '  #  ##..##  #  ',
    '  #          #  ',
    '  #  ######  #  ',
    '  #  #    #  #  ',
    '  #  #    #  #  ',
    '  #  ######  #  ',
    '  #          #  ',
    '   ##########   ',
    '                ',
])
S['slot/Back'] = c.out()
c = Cv()
c.rows([
    '                ',
    '                ',
    '                ',
    '                ',
    '    ########    ',
    '  ##        ##  ',
    ' #   ######   # ',
    ' #  ##    ##  # ',
    '#####  ##  #####',
    ' #  ##    ##  # ',
    ' #   ######   # ',
    '  ##        ##  ',
    '    ########    ',
    '                ',
    '                ',
    '                ',
])
S['slot/Belt'] = c.out()
c = Cv()
c.rows([
    '         # # #  ',
    '        ### #   ',
    '        # ###   ',
    '       ######   ',
    '      #    #    ',
    '     #    #     ',
    '    #    #      ',
    '   #    #       ',
    '  #    #        ',
    '  #   #         ',
    ' #   #          ',
    ' #  #           ',
    ' # #            ',
    ' ##             ',
    '                ',
    '                ',
])
S['slot/Quiver'] = c.out()

# ---------------------------------------------------------------- gear items
# Satchel
c = Cv()
c.rows([
    '     llllll     ',
    '    l      l    ',
    '   l        l   ',
    '   l        l   ',
    '  LLLLLLLLLLLL  ',
    ' LLLLLLLLLLLLLL ',
    ' LjjjjjjjjjjjjL ',
    ' LLLLLLGGLLLLLL ',
    ' LLLLLLGGLLLLLL ',
    ' LLLLLLLLLLLLLL ',
    ' LLLLLLLLLLLLLL ',
    ' LLLLLLLLLLLLLL ',
    '  LLLLLLLLLLLL  ',
], 0, 1)
c.rect(2, 5, 13, 7, 'X')
c.rect(6, 7, 9, 8, 'G')
S['gear/Satchel'] = c.out()

# Leather pack
c = Cv()
c.rows([
    '     kkkkkk     ',
    '    k      k    ',
    '   LLLLLLLLLL   ',
    '  LjjjjjjjjjjL  ',
    '  LXXXXXXXXXXL  ',
    '  LXXXXXXXXXXL  ',
    '  LXXGGXXGGXXL  ',
    ' lLLLGGLLGGLLLl ',
    ' lLLLLlLLlLLLLl ',
    ' lLLLLlLLlLLLLl ',
    ' lLLLLlLLlLLLLl ',
    ' lLLLLlLLlLLLLl ',
    '  LLLLlLLlLLLL  ',
    '  LLLLLLLLLLLL  ',
], 0, 1)
S['gear/Leather Pack'] = c.out()

# Frame pack
c = Cv()
c.rows([
    '   RRRRRRRRRR   ',
    '  RnnnnnnnnnnR  ',
    '  RRRRLLRRRRRR  ',
    '   RRRLLRRRRR   ',
    '  W  CCCCCC  W  ',
    '  W CCCCCCCC W  ',
    '  WWCCCCCCCCWW  ',
    '  W CCCCCCCC W  ',
    '  W CCCLLCCC W  ',
    '  W CCCCCCCC W  ',
    '  WWCCCCCCCCWW  ',
    '  W CCCCCCCC W  ',
    '  W  CCCCCC  W  ',
    '  W          W  ',
], 0, 1)
S['gear/Frame Pack'] = c.out()

# Belt (coiled)
c = Cv()
c.where(lambda x, y: 3.2 ** 2 <= (x - 7.5) ** 2 + (y - 8) ** 2 <= 6.3 ** 2, 'L')
c.where(lambda x, y: 4.5 ** 2 <= (x - 7.5) ** 2 + (y - 8) ** 2 <= 5.0 ** 2 and y < 8, 'j')
c.rect(9, 10, 14, 14, 'S')
c.rect(10, 11, 13, 13, '.')
c.rect(10, 11, 13, 13, 'L')
c.rect(11, 12, 12, 12, 'S')
c.pts([(4, 6), (6, 4), (3, 9)], 'k')
S['gear/Belt'] = c.out()

# Quiver
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
], 0, 1)
c.line(5, 9, 10, 4, 'k')
S['gear/Quiver'] = c.out()

# Hunter's quiver
c = Cv()
c.rows([
    '      u  u  u u ',
    '     uu uu uuuu ',
    '     Wu Wu WuW  ',
    '      W  W  W   ',
    '     ZZZZZZZZZ  ',
    '    ZZZZZZZZZ   ',
    '    DDDDDDDD    ',
    '   DdDDDDDD     ',
    '  DdDDDDDD      ',
    '  DdDDDDD       ',
    ' DdDDDDD        ',
    ' DDDDDD         ',
    ' DDDDD          ',
    '  DDD           ',
], 0, 1)
c.line(4, 10, 9, 5, 'G')
S["gear/Hunter's Quiver"] = c.out()

# ---------------------------------------------------------------- extra kit items
c = Cv()
c.rows([
    '      kkk       ',
    '     LLLLL      ',
    '      LjL       ',
    '    LLLLLLL     ',
    '   LLLLLLLLL    ',
    '  LLLLLLLLLLL   ',
    '  LLLLLLLLLGGG  ',
    '  LLLLLLLLGyGGG ',
    '  LLLLLLLLGGGGG ',
    '   LLLLLLLLGGG  ',
    '    LLLLLLL     ',
], 0, 3)
S['item/Coin Pouch'] = c.out()

c = Cv()
c.rows([
    '    RRRRRRRRR   ',
    '  RRnnnnnnnnnR  ',
    ' RnRRRRRRRRRRRR ',
    ' RnRrrrLLrrrRRR ',
    'RnRrRRRLLRRRrRR ',
    'RnRrRRRLLRRRrRR ',
    'RRRrRRRLLRRRrRR ',
    ' RRRrrrLLrrrRR  ',
    '  RRRRRLLRRRR   ',
], 0, 4)
S['item/Bedroll'] = c.out()

c = Cv()
c.rows([
    '  SSSSSSSSSSS   ',
    '  SWWWWWWWWWS   ',
    '  SWWwWWWwWWS   ',
    '  SWWwWWWwWWS   ',
    '  SWWwWWWwWWS   ',
    '  SSSSSSSSSSS   ',
    '  SWWwWWWwWWS   ',
    '  SWWwWWWwWWS   ',
    '  SWWwWWWwWWS   ',
    '  SWWwWWWwWWS   ',
    '  SWWwWWWwWWS   ',
    '   SWwWWWwWS    ',
    '    SSSSSSS     ',
], 0, 1)
S['item/Shield'] = c.out()

EXTRA_LEG = {
    '~': '#8E97A3',  # motion lines / sound waves (no outline)
    '^': '#D9C48A',  # warm light rays / chevrons (no outline)
}
NOOUT = '~^*'
OUTLINE_OFF = {'slot/Back', 'slot/Belt', 'slot/Quiver'}
SLOT_LEG = {'#': '#555555'}

if __name__ == '__main__':
    items = []
    for k, rows in S.items():
        if k in OUTLINE_OFF:
            g = render(rows, SLOT_LEG, outline=False)
        else:
            g = render(rows, EXTRA_LEG, outline=True, noout=NOOUT)
        items.append((k, g))
    sheet(items, scale=6, cols=10, pad=8).save('prev_icons.png')
    sheet([(k, g) for k, g in items if k.startswith('slot')], scale=6, cols=3, pad=8, bg=(139, 139, 139, 255)).save('prev_slots.png')
    print(len(items))
