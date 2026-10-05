# Pixel-art engine: char-grid sprites -> auto top-left shading -> selective outline -> RGBA grid
from PIL import Image

OL = '#1B1A1F'

MAT = {
    'steel':  ['#2B2F36', '#4A515B', '#737C87', '#A3ACB5', '#D3D8DD'],
    'wood':   ['#3A2717', '#5A3D24', '#7D5835', '#A27A4F', '#C49E72'],
    'leather':['#3D2219', '#5E3524', '#82503A', '#A66E50', '#C48E6E'],
    'dleather':['#1F1B1A', '#332D2C', '#4A4241', '#655C5A', '#857B78'],
    'gold':   ['#5A3F14', '#8A6526', '#B88C3E', '#D9B566', '#EFD8A0'],
    'stone':  ['#2F2F33', '#4A4A50', '#6C6C72', '#8E8E94', '#B4B4B8'],
    'deep':   ['#1C1C22', '#2C2C34', '#40404A', '#585862', '#74747F'],
    'cloth':  ['#6E6450', '#968A6E', '#BDB091', '#D8CDB0', '#ECE4CF'],
    'red':    ['#3E1618', '#6A2628', '#934040', '#B8615A', '#D48E80'],
    'green':  ['#1F2E1E', '#334A2D', '#4E6B3E', '#6F8B52', '#98AE72'],
    'teal':   ['#173B3B', '#245E5C', '#3A8780', '#69B1A6', '#A8D8CC'],
    'blue':   ['#1D2840', '#2E4060', '#4A6288', '#7088AE', '#A3B5CF'],
    'purple': ['#2E1A2A', '#4E2B45', '#734062', '#9A6185', '#C090AE'],
    'skin':   ['#4E3324', '#7A5236', '#A7774F', '#C99A6E', '#E4BE94'],
    'wool':   ['#6F6D69', '#9C9992', '#C4C1B9', '#DEDBD3', '#F2F0EA'],
    'ember':  ['#5C2412', '#8C3A18', '#B85A26', '#D88544', '#EDB673'],
    'wheat':  ['#5E4A1E', '#8A6F2E', '#B8954A', '#D9BC6E', '#EEDCA0'],
    'paper':  ['#8C7F65', '#B3A585', '#D2C6A6', '#E8DFC6', '#F6F1E2'],
    'fur':    ['#4A3B2C', '#6E5940', '#94795A', '#B89C7A', '#D6C0A0'],
    'copper': ['#4A2A1C', '#74412A', '#9C5E3C', '#BF8058', '#DBA882'],
    'moss':   ['#2A3322', '#3F4B30', '#5A6A42', '#7A8A58', '#A0AE7C'],
}

# default legend: char -> (mat, tone|'auto') or '#hex'
LEG = {
    'S': ('steel', 'auto'), 's': ('steel', 1), 'H': ('steel', 4), 'h': ('steel', 3), 'x': ('steel', 0),
    'W': ('wood', 'auto'), 'w': ('wood', 1), 'v': ('wood', 3), 'q': ('wood', 0),
    'L': ('leather', 'auto'), 'l': ('leather', 1), 'j': ('leather', 3), 'k': ('leather', 0),
    'D': ('dleather', 'auto'), 'd': ('dleather', 1),
    'G': ('gold', 'auto'), 'g': ('gold', 1), 'y': ('gold', 4),
    'T': ('stone', 'auto'), 't': ('stone', 1), 'i': ('stone', 3),
    'K': ('deep', 'auto'), 'z': ('deep', 0),
    'C': ('cloth', 'auto'), 'c': ('cloth', 1),
    'R': ('red', 'auto'), 'r': ('red', 1), 'n': ('red', 3),
    'F': ('green', 'auto'), 'f': ('green', 1), 'u': ('green', 3),
    'E': ('teal', 'auto'), 'e': ('teal', 1), 'a': ('teal', 4),
    'B': ('blue', 'auto'), 'b': ('blue', 1),
    'P': ('purple', 'auto'), 'p': ('purple', 1),
    'N': ('skin', 'auto'),
    'U': ('wool', 'auto'), 'o': ('wool', 1),
    'M': ('ember', 'auto'), 'm': ('ember', 1), 'Y': ('ember', 4),
    'A': ('wheat', 'auto'),
    'Q': ('paper', 'auto'), 'O': ('paper', 1),
    'Z': ('fur', 'auto'),
    'X': ('copper', 'auto'),
    'V': ('moss', 'auto'),
    '#': OL,
    '*': '#F4F2EC',
    '%': '#2A2730',
}
NOOUT = set('*')


def hx(c):
    c = c.lstrip('#')
    if len(c) == 6:
        return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), int(c[6:8], 16))


def render(rows, legend=None, outline=True, out_soft=False, noout=None, shade=True):
    """rows: list of strings. returns list of rows of RGBA tuples or None."""
    leg = dict(LEG)
    if legend:
        leg.update(legend)
    no = set(NOOUT) | set(noout or '')
    h = len(rows)
    w = max(len(r) for r in rows)
    rows = [r.ljust(w, '.') for r in rows]
    for i, r in enumerate(rows):
        assert len(r) == w, (i, r)
    g = [[None] * w for _ in range(h)]
    mat_at = [[None] * w for _ in range(h)]

    def ch(x, y):
        if 0 <= x < w and 0 <= y < h:
            return rows[y][x]
        return '.'

    for y in range(h):
        for x in range(w):
            c = rows[y][x]
            if c == '.' or c == ' ':
                continue
            e = leg.get(c)
            if e is None:
                raise KeyError('legend missing ' + c)
            if isinstance(e, str):
                g[y][x] = hx(e)
                continue
            mat, tone = e
            ramp = MAT[mat]
            mat_at[y][x] = mat
            if tone == 'auto':
                if not shade:
                    t = 2
                else:
                    up = ch(x, y - 1) == c
                    lf = ch(x - 1, y) == c
                    dn = ch(x, y + 1) == c
                    rt = ch(x + 1, y) == c
                    tl = (not up) or (not lf)
                    br = (not dn) or (not rt)
                    if tl and not br:
                        t = 3
                    elif br and not tl:
                        t = 1
                    else:
                        t = 2
                g[y][x] = hx(ramp[t])
            else:
                g[y][x] = hx(ramp[tone])
    if outline:
        add = {}
        for y in range(h):
            for x in range(w):
                if g[y][x] is not None:
                    continue
                # neighbours that are opaque and outline-able
                nb = []
                for dx, dy, side in ((-1, 0, 'br'), (0, -1, 'br'), (1, 0, 'tl'), (0, 1, 'tl')):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < w and 0 <= yy < h and g[yy][xx] is not None and rows[yy][xx] not in no:
                        nb.append((xx, yy, side))
                if not nb:
                    continue
                # if any neighbour is above/left of us we're on the bottom-right side -> dark outline
                if any(s == 'br' for _, _, s in nb) and not out_soft:
                    add[(x, y)] = hx(OL)
                else:
                    xx, yy, _ = nb[0]
                    m = mat_at[yy][xx]
                    if m:
                        add[(x, y)] = hx(MAT[m][0])
                    else:
                        add[(x, y)] = hx(OL)
        for (x, y), c in add.items():
            g[y][x] = c
    return g


def to_image(g, scale=1, bg=None):
    h = len(g)
    w = len(g[0])
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0) if bg is None else bg)
    for y in range(h):
        for x in range(w):
            if g[y][x] is not None:
                if bg is None:
                    im.putpixel((x, y), g[y][x])
                else:
                    base = im.getpixel((x, y))
                    c = g[y][x]
                    a = c[3] / 255
                    im.putpixel((x, y), tuple(int(c[i] * a + base[i] * (1 - a)) for i in range(3)) + (255,))
    if scale != 1:
        im = im.resize((w * scale, h * scale), Image.NEAREST)
    return im


def from_image(im):
    im = im.convert('RGBA')
    w, h = im.size
    g = [[None] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            p = im.getpixel((x, y))
            if p[3] > 0:
                g[y][x] = p
    return g


CHARS = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!$%&()*+,-/:;<=>?@[]^_{|}~'


def encode(g, names=None):
    """palette + string grid. returns dict {w,h,p:[hex], r:[rows]}"""
    pal = {}
    order = []
    rows = []
    for row in g:
        s = ''
        for c in row:
            if c is None:
                s += '.'
                continue
            key = c
            if key not in pal:
                pal[key] = CHARS[len(order)]
                order.append(key)
            s += pal[key]
        rows.append(s)
    def tohex(c):
        hh = '#%02X%02X%02X' % c[:3]
        if c[3] != 255:
            hh += '%02X' % c[3]
        return hh
    return {'w': len(g[0]), 'h': len(g), 'p': [tohex(c) for c in order], 'r': rows}


def blit(dst, src, ox, oy):
    for y, row in enumerate(src):
        for x, c in enumerate(row):
            if c is None:
                continue
            X, Y = ox + x, oy + y
            if 0 <= Y < len(dst) and 0 <= X < len(dst[0]):
                if c[3] == 255 or dst[Y][X] is None:
                    dst[Y][X] = c
                else:
                    b = dst[Y][X]
                    a = c[3] / 255
                    dst[Y][X] = tuple(int(c[i] * a + b[i] * (1 - a)) for i in range(3)) + (255,)


def sheet(items, scale=4, cols=8, pad=6, bg=(25, 26, 28, 255)):
    """items: list of (name, grid). returns preview Image"""
    cw = max(len(g[0]) for _, g in items) * scale + pad * 2
    chh = max(len(g) for _, g in items) * scale + pad * 2
    rows = (len(items) + cols - 1) // cols
    im = Image.new('RGBA', (cw * cols, chh * rows), bg)
    for i, (n, g) in enumerate(items):
        sp = to_image(g, scale)
        x = (i % cols) * cw + (cw - sp.width) // 2
        y = (i // cols) * chh + (chh - sp.height) // 2
        im.alpha_composite(sp, (x, y))
    return im
