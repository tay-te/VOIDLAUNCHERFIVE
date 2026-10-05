# Shared drawing kit for the perk icons: extended legend, materials and small reusable shapes.
# Everything renders through px.render exactly like the original Mining icons (top-left light, selective outline).
from px import MAT
from icons import Cv  # noqa: F401  (re-exported for the tree modules)

# extra material ramps (dark -> light), same 5-step structure as px.MAT
MAT.setdefault('bone', ['#4E4638', '#7E7562', '#ADA48C', '#CFC8B1', '#E9E4D3'])
MAT.setdefault('nether', ['#161214', '#262023', '#3A3135', '#51464B', '#6C6066'])
MAT.setdefault('hay', ['#5A4A1C', '#85702E', '#AD9545', '#CDB665', '#E4D394'])
MAT.setdefault('rawmeat', ['#4A1C1E', '#7A2E30', '#A44A46', '#C46C62', '#DDA090'])
MAT.setdefault('water', ['#1A2C3A', '#28455A', '#3E6680', '#6390A6', '#9CC0CC'])
MAT.setdefault('dirt', ['#3A2A1C', '#57402A', '#76583A', '#957452', '#B4946E'])
MAT.setdefault('grass', ['#24331F', '#38502C', '#527038', '#6F8C4A', '#93AC6A'])

# Extended legend on top of px.LEG + icons.EXTRA_LEG.  Upper-case-ish = auto-shaded, others fixed tones.
LEG = {
    '~': '#8E97A3',   # motion / sound lines (no outline)
    '^': '#D9C48A',   # warm light, chevrons (no outline)
    ':': '#6B727C',   # faint trail dots (no outline)
    '0': ('steel', 2),
    '1': ('skin', 1), '2': ('skin', 3),
    '3': ('fur', 1), '4': ('fur', 3),
    '5': ('green', 4),
    '6': ('wool', 3),
    '7': ('red', 4),
    '8': ('copper', 3), '9': ('copper', 1),
    '=': ('wood', 2), ';': ('wood', 4),
    'I': ('stone', 4), 'J': ('ember', 2),
    '@': ('bone', 'auto'), '&': ('bone', 1), '$': ('bone', 4),
    '!': ('cloth', 3),
    '<': ('water', 'auto'), '>': ('water', 4), '{': ('water', 1),
    '"': ('gold', 2), "'": ('gold', 3),
    '[': ('hay', 'auto'), ']': ('hay', 1),
    '(': ('rawmeat', 'auto'), ')': ('rawmeat', 1),
    '/': ('dirt', 'auto'), '\\': ('dirt', 1),
    '?': ('grass', 'auto'), '|': ('grass', 1), '`': ('grass', 4),
    '-': ('nether', 'auto'), '}': ('nether', 1),
    '+': ('paper', 3),
    ',': '#CFCBC0',   # pale swoosh (no outline)
}
# extra no-outline marks that need characters beyond ASCII (kept as escapes so the file stays ASCII)
LEG['\u00b7'] = '#7E6A55'   # prints on the ground
LEG['\u00a6'] = '#B3AA97'   # bowstring
STRING = '\u00a6'
PRINT = '\u00b7'
NOOUT = '~^*:,' + PRINT + STRING


def blade(c, tx, ty, L, bands='Hhs', guard=2, grip=2, pommel='G', gch='G', grch='L'):
    """45-degree sword, tip at (tx, ty) pointing up-right; returns the hilt centre."""
    nb = len(bands)
    for i in range(L):
        for k, ch in enumerate(bands):
            if i < k:
                continue
            c.put(tx - i + k, ty + i, ch)
    # guard runs along (1,1) through the middle band at the hilt
    mid = nb // 2
    gx, gy = tx - L + mid, ty + L
    for j in range(-guard, guard + 1):
        c.put(gx + j, gy + j, gch)
    c.put(gx, gy, 'y' if gch == 'G' else gch)
    for m in range(1, grip + 1):
        c.put(gx - m, gy + m, grch)
    if pommel:
        c.put(gx - grip - 1, gy + grip + 1, pommel)
    return gx, gy


def arrow_diag(c, tx, ty, L, shaft='v', head='S', fl='n', fl2='r'):
    """Arrow pointing up-right with its head at (tx, ty)."""
    for i in range(2, L):
        c.put(tx - i, ty + i, shaft)
    c.pts([(tx, ty), (tx - 1, ty), (tx - 2, ty), (tx, ty + 1), (tx, ty + 2)], head)
    c.put(tx - 1, ty + 1, 'h' if head == 'S' else head)
    ex, ey = tx - L + 1, ty + L - 1
    for k in range(3):
        c.put(ex - k, ey + k, shaft)
        c.put(ex - k - 1, ey + k, fl)
        c.put(ex - k, ey + k + 1, fl2)


def arrow_dr(c, tx, ty, L, shaft='v', head='S', fl='n', fl2='r'):
    """Arrow pointing down-right with its head at (tx, ty)."""
    for i in range(2, L):
        c.put(tx - i, ty - i, shaft)
    c.pts([(tx, ty), (tx - 1, ty), (tx - 2, ty), (tx, ty - 1), (tx, ty - 2)], head)
    c.put(tx - 1, ty - 1, 'h' if head == 'S' else head)
    ex, ey = tx - L + 1, ty - L + 1
    for k in range(3):
        c.put(ex - k, ey - k, shaft)
        c.put(ex - k, ey - k - 1, fl)
        c.put(ex - k - 1, ey - k, fl2)


def arrow_h(c, tx, y, L, shaft='v', head='S', fl='n'):
    """Horizontal arrow pointing right with its tip at (tx, y)."""
    c.pts([(tx, y), (tx - 1, y), (tx - 1, y - 1), (tx - 1, y + 1)], head)
    c.put(tx, y, 'H' if head == 'S' else head)
    c.line(tx - 2, y, tx - L + 1, y, shaft)
    x0 = tx - L + 1
    c.pts([(x0, y - 1), (x0 + 1, y - 1), (x0, y + 1), (x0 + 1, y + 1)], fl)


def heart(c, ox, oy, ch='R', size=7):
    """Heart; size 7 (7x6) or 5 (5x4)."""
    if size == 7:
        rows = [' RR RR ', 'RRRRRRR', 'RRRRRRR', ' RRRRR ', '  RRR  ', '   R   ']
    elif size == 9:
        rows = [' RRR RRR ', 'RRRRRRRRR', 'RRRRRRRRR', 'RRRRRRRRR', ' RRRRRRR ', '  RRRRR  ', '   RRR   ', '    R    ']
    else:
        rows = ['RR RR', 'RRRRR', ' RRR ', '  R  ']
    c.rows([r.replace('R', ch) for r in rows], ox, oy)


def heater(c, x0, y0, w, h, rim='S', face='W', plank='w', boss=True):
    """Heater shield with an iron rim, wooden face and boss. (x0,y0) top-left."""
    x1 = x0 + w - 1
    top = h * 5 // 9

    def inside(x, y):
        if not (x0 <= x <= x1 and y0 <= y <= y0 + h - 1):
            return False
        if y - y0 <= top:
            return True
        k = y - y0 - top
        inset = (k * (w // 2)) // (h - 1 - top + 1)
        return x0 + inset <= x <= x1 - inset
    c.where(inside, rim)
    c.where(lambda x, y: inside(x, y) and inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1), face)
    if plank:
        third = w // 3
        c.where(lambda x, y: c.get(x, y) == face and x in (x0 + third, x1 - third), plank)
    if boss:
        cx, cy = x0 + w // 2 - 1, y0 + h // 2 - 1
        c.rect(cx, cy, cx + 1, cy + 1, rim)
        c.put(cx, cy, 'H')
    return inside


def diag(c, x, y, n, ch, dx=1, dy=-1):
    for i in range(n):
        c.put(x + i * dx, y + i * dy, ch)


def arc(c, cx, cy, r0, r1, a0, a1, ch, only_empty=False):
    """Ring segment around (cx, cy); angles in degrees, 0 = right, 90 = up (screen)."""
    import math
    for y in range(c.h):
        for x in range(c.w):
            dx, dy = x - cx, cy - y
            d = math.hypot(dx, dy)
            a = math.degrees(math.atan2(dy, dx)) % 360
            lo, hi = a0 % 360, a1 % 360
            ok = (lo <= a <= hi) if lo <= hi else (a >= lo or a <= hi)
            if r0 <= d <= r1 and ok and (not only_empty or c.get(x, y) == '.'):
                c.put(x, y, ch)


def disc(c, cx, cy, r, ch, only_empty=False):
    for y in range(c.h):
        for x in range(c.w):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and (not only_empty or c.get(x, y) == '.'):
                c.put(x, y, ch)


def mirror(c):
    """Flip a canvas left-right in place (explicit tones keep their letters)."""
    c.g = [list(reversed(r)) for r in c.g]
    return c


def stamp(c, rows, ox=0, oy=0):
    c.rows(rows, ox, oy)


def poly(c, pts, ch, only_empty=False):
    """Fill a polygon (screen coords, pixel centres are x+0.5,y+0.5)."""
    n = len(pts)
    for y in range(c.h):
        for x in range(c.w):
            px_, py_ = x + 0.5, y + 0.5
            inside = False
            j = n - 1
            for i in range(n):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if (yi > py_) != (yj > py_) and px_ < (xj - xi) * (py_ - yi) / (yj - yi) + xi:
                    inside = not inside
                j = i
            if inside and (not only_empty or c.get(x, y) == '.'):
                c.put(x, y, ch)


def rpoly(c, origin, a, b, st, ch, only_empty=False):
    """Polygon given in a rotated frame: point = origin + s*a + t*b."""
    ox, oy = origin
    pts = [(ox + s * a[0] + t * b[0], oy + s * a[1] + t * b[1]) for s, t in st]
    poly(c, pts, ch, only_empty)


class Layers(list):
    """An icon built from stacked canvases; each is outlined on its own, later ones on top."""


def _unit(dx, dy):
    import math
    n = math.hypot(dx, dy)
    return dx / n, dy / n


def axe(c, x0, y0, x1, y1, side=1, k=1.0, head='S', edge='H', haft='W', bearded=True, double=False):
    """Axe: haft from (x0,y0) handle end to (x1,y1) head end; blade on `side` (+1 = clockwise of the haft)."""
    a = _unit(x1 - x0, y1 - y0)
    b = (-a[1] * side, a[0] * side)
    c.line(x0, y0, x1, y1, haft)
    E = (x1 + 0.5 - a[0] * 1.2, y1 + 0.5 - a[1] * 1.2)
    lo = -3.1 if bearded else -2.2
    st = [(-0.9, -1.7), (1.0, -1.7), (1.0, 1.1), (2.3 * k, 3.2 * k), (2.1 * k, 4.4 * k), (0, 4.9 * k),
          (lo * k * 0.8, 4.6 * k), (lo * k, 3.6 * k), (-1.0, 1.3)]
    sides = [1, -1] if double else [1]
    for sd in sides:
        bb = (b[0] * sd, b[1] * sd)
        pts = [(E[0] + s * a[0] + t * bb[0], E[1] + s * a[1] + t * bb[1]) for s, t in st]
        poly(c, pts, head)
        # bright cutting edge
        for y in range(c.h):
            for x in range(c.w):
                if c.get(x, y) == head:
                    t = (x + 0.5 - E[0]) * bb[0] + (y + 0.5 - E[1]) * bb[1]
                    if t > 3.7 * k:
                        c.put(x, y, edge)
    return a, b


def mace(c, x0, y0, x1, y1, r=2.6, head='S', haft='W', flanges=True):
    """Flanged mace: haft from (x0,y0) to (x1,y1), round head centred on (x1,y1)."""
    c.line(x0, y0, x1, y1, haft)
    disc(c, x1, y1, r, head)
    if flanges:
        for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
            c.put(round(x1 + dx * (r + 1)), round(y1 + dy * (r + 1)), head)


def sword_v(c, x, y_tip, L, up=False, bands='Hs', guard=2, grip=2, gch='G', grch='L', pommel='G'):
    """Vertical sword in columns x, x+1.  Tip at y_tip pointing down (or up if up=True)."""
    d = -1 if up else 1   # direction from tip towards hilt
    for i in range(L):
        y = y_tip - d * i
        c.put(x, y, bands[0]); c.put(x + 1, y, bands[1])
    yt = y_tip
    c.put(x + (1 if not up else 0), yt, '.')
    gy = y_tip - d * L
    c.rect(x - guard, gy, x + 1 + guard, gy, gch)
    for m in range(1, grip + 1):
        c.rect(x, gy - d * m, x + 1, gy - d * m, grch)
    if pommel:
        c.rect(x, gy - d * (grip + 1), x + 1, gy - d * (grip + 1), pommel)


# chestplate, front view (12 x 11)
CHEST = [
    ' SSS    SSS ',
    'SSSSS  SSSSS',
    'SSSSSSSSSSSS',
    'SSSSSSSSSSSS',
    ' SSSSHhSSSS ',
    ' SSSSHhSSSS ',
    ' SSSSHhSSSS ',
    ' SSSSSSSSSS ',
    ' ssssssssss ',
    ' SSSSSSSSSS ',
    '  SSSSSSSS  ',
]
# conical nasal helm, front view (10 x 10)
HELM = [
    '    SS    ',
    '   SSSS   ',
    '  SSSSSS  ',
    ' SSSSSSSS ',
    ' SSSSSSSS ',
    'hhhhhhhhhh',
    'S%%%SS%%%S',
    'S%%%SS%%%S',
    'S %%SS%% S',
    'S   SS   S',
]


def arrow_up(c, x, ty, L, shaft='v', head='S', fl='n', fl2='r'):
    """Vertical arrow pointing up, tip at (x, ty)."""
    c.line(x, ty + 1, x, ty + L - 1, shaft)
    c.pts([(x, ty), (x - 1, ty + 1), (x + 1, ty + 1), (x, ty + 1)], head)
    c.put(x, ty, 'H' if head == 'S' else head)
    y0 = ty + L - 1
    c.pts([(x - 1, y0 - 1), (x - 1, y0)], fl)
    c.pts([(x + 1, y0 - 1), (x + 1, y0)], fl2)


# leather boot, side view facing right (10 x 9), same family as the Athletics skill icon
BOOT = [
    ' jjjj     ',
    ' LLLL     ',
    ' LLkL     ',
    ' LLLk     ',
    ' LLkL     ',
    ' LLLLLL   ',
    'LLLLLLLLL ',
    'LLLLLLLLLL',
    'wwwwwwwwww',
]
# tied burlap sack (8 x 9)
SACK = [
    '   ll   ',
    '  CLLC  ',
    '   CC   ',
    '  CCCC  ',
    ' CCCCCC ',
    'CCCCCCCC',
    'CCCCCCCC',
    'CCCCCCCC',
    ' CCCCCC ',
]


def hammer(c, hx, hy, L=8, half=2.6, w=1.6, head='S', haft='W', d=(-1, 1)):
    """Smith's hammer: head centred on (hx, hy), haft running off in direction d (default down-left)."""
    import math
    n = math.hypot(*d)
    a = (d[0] / n, d[1] / n)          # along the haft
    b = (-a[1], a[0])                 # along the head
    c.line(round(hx + a[0] * 1.5), round(hy + a[1] * 1.5), round(hx + a[0] * L), round(hy + a[1] * L), haft)
    rpoly(c, (hx + 0.5, hy + 0.5), a, b, [(-w, -half), (w, -half), (w, half), (-w, half)], head)


ANVIL = [
    'SSSSSSSSSSSSS',
    ' SSSSSSSSSSS ',
    '    sssss    ',
    '    SSSSS    ',
    '   SSSSSSS   ',
    '  sssssssss  ',
]
INGOT = [
    '  hhhhhhhh  ',
    ' HSSSSSSSSs ',
    ' ssssssssss ',
]
GOLD_INGOT = [
    "  yGGGGGGg  ",
    " yGGGGGGGGg ",
    " gggggggggg ",
]
