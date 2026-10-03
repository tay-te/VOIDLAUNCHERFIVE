"""VOID Hearth economy textures: coins (single, a few, a stack, a pile), the coin pouch and the deed, plus the
parchment-and-wood GUI panels for the market and bank screens.

Run from anywhere:  python hearth/tools/economy/gen_textures.py [--preview DIR]

Coins are drawn as small tilted discs (a face ellipse over a milled edge), lit from the upper left like vanilla's
nuggets and ingots, in each metal's vanilla palette (copper, iron and gold taken from the ingot and nugget sprites).
Each coin carries its own stamp: a square-holed copper bit, an iron mark struck with an anvil, a gold crown with a
crown.  Larger counts are drawn as overlapping coins, a short column, and a heap.
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
ITEM_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hearth', 'textures', 'item')
GUI_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hearth', 'textures', 'gui', 'economy')


def hexc(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


# outline, deep, dark, mid, light, bright, highlight  (vanilla ingot/nugget palettes)
METALS = {
    'copper': [hexc(c) for c in ('#4a2216', '#6d3421', '#9c4529', '#c15a36', '#e77c56', '#fc9982', '#fbc3b6')],
    'iron': [hexc(c) for c in ('#2a2c30', '#393c40', '#585f68', '#7d8791', '#a2b0be', '#c9d3df', '#f2f2f2')],
    'gold': [hexc(c) for c in ('#4f2f05', '#752802', '#b26411', '#dc9613', '#e9b115', '#fad64a', '#fffde0')],
}
O, DEEP, DARK, MID, LIGHT, BRIGHT, HI = range(7)


def blank(w=16, h=16):
    return np.zeros((h, w, 4), np.uint8)


def ellipse_mask(cx, cy, rx, ry, w=16, h=16):
    ys, xs = np.mgrid[0:h, 0:w]
    return ((xs + 0.5 - cx) / rx) ** 2 + ((ys + 0.5 - cy) / ry) ** 2 <= 1.0


# Stamps on the coin face, as offsets from the face centre: 'x' = struck low (dark), 'h' = raised edge (highlight).
STAMPS = {
    # copper bit: a cash coin, square hole through the middle
    'copper': (["xx",
                "xx"], 1, 1),
    # iron mark: an anvil
    'iron': ([".x.",
              "xxx",
              ".x."], 1, 1),
    # gold crown: a three-pointed crown
    'gold': (["x.x.x",
              "xxxxx",
              "xxxxx"], 2, 1),
}


def draw_coin(img, cx, cy, rx, ry, thick, metal, stamp=True, face_only=False):
    """Draws one tilted coin onto img (painter's order: call back to front)."""
    p = METALS[metal]
    h, w = img.shape[:2]
    prior = img[..., 3] > 0
    face = ellipse_mask(cx, cy, rx, ry, w, h)
    body = face.copy()
    for t in range(1, thick + 1):
        body |= ellipse_mask(cx, cy + t, rx, ry, w, h)
    edge = body & ~face
    # milled edge: alternating shades, darker toward the right
    for y in range(h):
        for x in range(w):
            if edge[y, x]:
                right = (x + 0.5 - cx) / rx
                base = DARK if right < 0.35 else DEEP
                milled = thick >= 2 and x % 2 == 1
                img[y, x] = p[(MID if base == DARK else DARK) if milled else base]
    if face_only:
        return
    # face: lit from the upper left, with a raised rim
    inner = ellipse_mask(cx, cy, rx - 1.0, ry - 1.0, w, h)
    for y in range(h):
        for x in range(w):
            if not face[y, x]:
                continue
            nx = (x + 0.5 - cx) / rx
            ny = (y + 0.5 - cy) / ry
            light = -(nx * 0.7 + ny * 0.7)
            if not inner[y, x]:
                # rim: bright on the upper left, dark on the lower right
                c = BRIGHT if light > 0.25 else LIGHT if light > -0.35 else MID
            else:
                c = LIGHT if light > 0.3 else MID if light > -0.45 else MID
                if light > 0.75:
                    c = BRIGHT
            img[y, x] = p[c]
    # a glint on the rim
    gx, gy = int(cx - rx * 0.55), int(cy - ry * 0.6)
    if 0 <= gy < h and 0 <= gx < w and face[gy, gx]:
        img[gy, gx] = p[HI]
    # where this coin lies over another, a dark line parts them
    ring = body.copy()
    inner_body = body.copy()
    inner_body[1:-1, 1:-1] = body[1:-1, 1:-1] & body[:-2, 1:-1] & body[2:, 1:-1] & body[1:-1, :-2] & body[1:-1, 2:]
    ring &= ~inner_body
    img[ring & prior] = p[DEEP]
    if stamp:
        rows, ox, oy = STAMPS[metal]
        sx, sy = int(round(cx - 0.5)) - ox, int(round(cy - 0.5)) - oy
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                x, y = sx + i, sy + j
                if ch == 'x' and 0 <= x < w and 0 <= y < h and inner[y, x]:
                    img[y, x] = p[DARK]
                    # struck: the lower-right lip catches the light
                    if (x + 1 < w and y + 1 < h and inner[y + 1, x + 1]
                            and (j + 1 >= len(rows) or i + 1 >= len(row) or rows[j + 1][i + 1] != 'x')):
                        if img[y + 1, x + 1][3] and tuple(img[y + 1, x + 1]) != tuple(p[DARK]):
                            img[y + 1, x + 1] = p[BRIGHT]
        if metal == 'copper':
            # the hole goes right through: the deep shade, not just a stamp
            for (x, y) in ((sx, sy), (sx + 1, sy), (sx, sy + 1), (sx + 1, sy + 1)):
                img[y, x] = p[DEEP]


def outline(img, metal):
    """Vanilla-style silhouette: every empty pixel touching the sprite becomes the metal's outline shade."""
    solid = img[..., 3] > 0
    out = np.zeros_like(solid)
    out[1:, :] |= solid[:-1, :]
    out[:-1, :] |= solid[1:, :]
    out[:, 1:] |= solid[:, :-1]
    out[:, :-1] |= solid[:, 1:]
    out &= ~solid
    img[out] = METALS[metal][O]
    return img


def coin_single(metal):
    img = blank()
    size = {'copper': (4.2, 3.4), 'iron': (4.7, 3.7), 'gold': (5.0, 4.0)}[metal]
    draw_coin(img, 8.0, 7.5, size[0], size[1], 2, metal)
    return img


def coin_few(metal):
    img = blank()
    rx, ry = {'copper': (3.8, 3.0), 'iron': (4.2, 3.3), 'gold': (4.4, 3.5)}[metal]
    draw_coin(img, 10.5, 6.0, rx, ry, 2, metal, stamp=False)
    draw_coin(img, 5.5, 8.5, rx, ry, 2, metal, stamp=False)
    draw_coin(img, 9.0, 10.5, rx, ry, 2, metal)
    return img


def coin_stack(metal):
    img = blank()
    rx, ry = {'copper': (3.6, 2.6), 'iron': (3.9, 2.8), 'gold': (4.1, 2.9)}[metal]
    # a column of coins seen edge-on, a loose coin leaning beside it
    draw_coin(img, 11.5, 11.5, rx * 0.85, ry * 0.85, 2, metal, stamp=False)
    base = 11.5
    for k in range(5):
        draw_coin(img, 6.5, base - k * 1.5, rx, ry, 1, metal, stamp=k == 4)
    return img


def coin_pile(metal):
    img = blank()
    rx, ry = {'copper': (3.6, 2.6), 'iron': (3.8, 2.7), 'gold': (3.9, 2.8)}[metal]
    # a heap: a broad bottom row, coins tumbled on top, one standing proud at the crest
    spots = [(3.8, 12.6), (8.2, 13.2), (12.4, 12.2), (5.9, 10.2), (10.6, 9.9), (8.0, 7.6), (4.6, 8.4), (11.8, 7.0)]
    for (x, y) in sorted(spots, key=lambda s: s[1]):
        draw_coin(img, x, y, rx, ry, 2, metal, stamp=False)
    draw_coin(img, 8.4, 5.4, rx, ry, 2, metal, stamp=True)
    return img


# ---------------------------------------------------------------- pouch and deed (hand-drawn maps)

LEATHER = {'o': hexc('#3a1d0c'), 'd': hexc('#5c2f14'), 'm': hexc('#82481f'), 'l': hexc('#a8652e'), 'h': hexc('#c98c4f'),
           's': hexc('#d8c7a0'), 'S': hexc('#9e8c69'), 'g': hexc('#e9b115'), 'G': hexc('#fad64a'), 'k': hexc('#b26411')}

POUCH_EMPTY = [
    "................",
    "................",
    "................",
    "......sSs.......",
    ".....oSsSo......",
    "....odmmmdo.....",
    "....omlhlmo.....",
    "...omlhhlmmo....",
    "...omlhlmmdo....",
    "..omllmmmmddo...",
    "..omlmmmmmddo...",
    "..odmmmmmdddo...",
    "...oddmmdddo....",
    "....ooooooo.....",
    "................",
    "................",
]

POUCH_SOME = [
    "................",
    "................",
    "......sSs.......",
    ".....oSsSo......",
    "....odmgmdo.....",
    "...omlhGlmmo....",
    "..omlhhhlmmmo...",
    "..omlhhlmmmdo...",
    ".omllhlmmmmddo..",
    ".omlhlmmmmmddo..",
    ".omllmmmmmmddo..",
    ".odlmmmmmmdddo..",
    "..odmmmmmdddo...",
    "...oddddddoo....",
    "....oooooo......",
    "................",
]

POUCH_FULL = [
    "................",
    "......sSs.......",
    "....ogGsGgo.....",
    "...okgGgGgko....",
    "...odmmgmmdo....",
    "..omlhhGhlmmo...",
    ".omlhhhhhlmmmo..",
    ".omlhhhlmmmmdo..",
    "omllhhlmmmmmddo.",
    "omlhhlmmmmmmddo.",
    "omllhlmmmmmmddo.",
    "omllmmmmmmmdddo.",
    ".odlmmmmmmmddo..",
    ".oodmmmmmdddoo..",
    "...oooooooooo...",
    "................",
]

PARCH = {'o': hexc('#5a4630'), 'd': hexc('#b49a6c'), 'm': hexc('#d9c49a'), 'l': hexc('#eadcb8'), 'h': hexc('#f6eedb'),
         'r': hexc('#7a1414'), 'R': hexc('#b02a20'), 'p': hexc('#d6493a'), 'i': hexc('#3b2a1c'), 's': hexc('#8a6a3c')}

DEED = [
    "................",
    "................",
    "...oooooooooo...",
    "..odllllllllldo.",
    "..odhhhhhhhhldo.",
    "...ohiihiiihlo..",
    "...ohhhhhhhhlo..",
    "...ohiiihiihlo..",
    "...ohhhhhhhhlo..",
    "...ohiihhhRRlo..",
    "...ohhhhhhRprR..",
    "..odllllllRRrRo.",
    "..odmmmmmmmrssd.",
    "...ooooooooo.s..",
    "................",
    "................",
]


def from_map(rows, pal):
    img = blank()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img[y, x] = pal[ch]
    return img


# ---------------------------------------------------------------- GUI panels

def noise(rng, h, w, scale):
    n = rng.standard_normal((h // scale + 2, w // scale + 2))
    img = Image.fromarray(((n - n.min()) / (np.ptp(n) + 1e-9) * 255).astype(np.uint8))
    img = img.resize((w + scale * 2, h + scale * 2), Image.BICUBIC)
    a = np.asarray(img, np.float32)[scale:scale + h, scale:scale + w] / 255.0
    return a


def wood_rgb(rng, h, w, vertical=False):
    """Oak-ish planks: grain streaks, plank seams, a few knots; vanilla oak plank colours."""
    base = np.array([162, 130, 78], np.float32)
    dark = np.array([104, 83, 50], np.float32)
    light = np.array([188, 152, 98], np.float32)
    grain = noise(rng, h, w, 2)
    streak = noise(rng, h, max(4, w // 16), 1)
    streak = np.asarray(Image.fromarray((streak * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR), np.float32) / 255.0
    t = np.clip(0.55 * grain + 0.45 * streak, 0, 1)
    a = np.abs(t - 0.5)[..., None]
    img = base[None, None, :] * (1 - a) + np.where((t > 0.5)[..., None], light, dark) * a
    # plank seams every 8 px, staggered joints
    for y in range(0, h, 8):
        img[y, :, :] = dark * 0.85
        off = (y // 8 % 2) * 23
        for x in range(off % 46, w, 46):
            img[y:y + 8, x, :] = dark * 0.85
    return img


def parchment_rgb(rng, h, w):
    base = np.array([236, 221, 186], np.float32)
    n = noise(rng, h, w, 6) * 0.6 + noise(rng, h, w, 2) * 0.4
    img = base[None, None, :] * (0.93 + 0.1 * n[..., None])
    ys, xs = np.mgrid[0:h, 0:w]
    # darker, foxed toward the edges, as old paper is
    edge = np.minimum(np.minimum(xs, w - 1 - xs), np.minimum(ys, h - 1 - ys)).astype(np.float32)
    fade = np.clip(edge / 7.0, 0, 1)
    img = img * (0.82 + 0.18 * fade[..., None])
    return img


def paste(dst, src, x, y):
    h, w = src.shape[:2]
    dst[y:y + h, x:x + w, :3] = np.clip(src, 0, 255)
    dst[y:y + h, x:x + w, 3] = 255


def frame(dst, x0, y0, x1, y1, outer, inner_hi, inner_lo):
    """A bevelled frame: dark outline, light upper-left inner edge, shadow lower-right."""
    dst[y0, x0:x1, :3] = outer
    dst[y1 - 1, x0:x1, :3] = outer
    dst[y0:y1, x0, :3] = outer
    dst[y0:y1, x1 - 1, :3] = outer
    dst[y0 + 1, x0 + 1:x1 - 1, :3] = inner_hi
    dst[y0 + 1:y1 - 1, x0 + 1, :3] = inner_hi
    dst[y1 - 2, x0 + 1:x1 - 1, :3] = inner_lo
    dst[y0 + 1:y1 - 1, x1 - 2, :3] = inner_lo


def slot(dst, x, y):
    """A vanilla-style 18x18 inventory slot, tinted to sit on parchment."""
    dst[y:y + 18, x:x + 18, :3] = (139, 120, 88)
    dst[y:y + 17, x, :3] = (92, 74, 50)
    dst[y, x:x + 17, :3] = (92, 74, 50)
    dst[y + 17, x + 1:x + 18, :3] = (250, 240, 214)
    dst[y + 1:y + 18, x + 17, :3] = (250, 240, 214)
    dst[y + 1:y + 17, x + 1:x + 17, :3] = (176, 156, 120)


# Layout shared with MarketScreen/BankScreen (keep in step with the Java constants).
GUI_W, GUI_H = 276, 232
PAGE_TOP, PAGE_BOTTOM, PAGE_W = 15, 121, 130
NOTE_TOP, NOTE_BOTTOM = 124, 145
INV_X, INV_Y = 58, 152


def gui_panel(seed, w=GUI_W, h=GUI_H):
    """The market/bank background: an oak frame round two parchment pages, a note strip and an inventory well."""
    rng = np.random.default_rng(seed)
    img = np.zeros((512, 512, 4), np.uint8)
    paste(img, wood_rgb(rng, h, w), 0, 0)
    frame(img, 0, 0, w, h, (42, 30, 16), (204, 170, 112), (84, 64, 38))
    dark, lo, hi = (58, 40, 22), (120, 92, 58), (250, 238, 206)
    for x in (7, w - 7 - PAGE_W):
        paste(img, parchment_rgb(rng, PAGE_BOTTOM - PAGE_TOP, PAGE_W), x, PAGE_TOP)
        frame(img, x - 1, PAGE_TOP - 1, x + PAGE_W + 1, PAGE_BOTTOM + 1, dark, lo, hi)
    # the merchant's note: a strip across the whole width
    paste(img, parchment_rgb(rng, NOTE_BOTTOM - NOTE_TOP, w - 14), 7, NOTE_TOP)
    frame(img, 6, NOTE_TOP - 1, w - 6, NOTE_BOTTOM + 1, dark, lo, hi)
    # inventory well
    x0, y0 = INV_X - 6, INV_Y - 3
    paste(img, parchment_rgb(rng, h - 5 - y0, 162 + 12), x0, y0)
    frame(img, x0 - 1, y0 - 1, x0 + 162 + 13, h - 4, dark, lo, hi)
    for r in range(3):
        for c in range(9):
            slot(img, INV_X - 1 + c * 18, INV_Y - 1 + r * 18)
    for c in range(9):
        slot(img, INV_X - 1 + c * 18, INV_Y + 58 - 1)
    return img


def gui_sprites():
    """Small sprites the screens draw: row highlight, a hammer for 'the builders need this', tabs."""
    img = np.zeros((64, 64, 4), np.uint8)
    # hammer 9x9 at (0,0)
    hammer = [
        ".oooo....",
        "ommmmo...",
        "ommmmmo..",
        ".oooodo..",
        "....odso.",
        ".....osso",
        "......oso",
        ".......o.",
        ".........",
    ]
    pal = {'o': (40, 30, 20, 255), 'm': (150, 154, 160, 255), 'd': (96, 70, 40, 255), 's': (150, 108, 62, 255)}
    for y, row in enumerate(hammer):
        for x, ch in enumerate(row):
            if ch != '.':
                img[y, x] = pal[ch]
    # 10x10 coin icons for prices: copper (16,0), iron (26,0), gold (36,0)
    for i, metal in enumerate(('copper', 'iron', 'gold')):
        icon = blank(10, 10)
        draw_coin(icon, 5.0, 4.4, 3.9, 3.1, 1, metal, stamp=False)
        solid = icon[..., 3] > 0
        out = np.zeros_like(solid)
        out[1:, :] |= solid[:-1, :]
        out[:-1, :] |= solid[1:, :]
        out[:, 1:] |= solid[:, :-1]
        out[:, :-1] |= solid[:, 1:]
        icon[out & ~solid] = METALS[metal][O]
        img[0:10, 16 + i * 10:26 + i * 10] = icon
    return img


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(img, 'RGBA').save(path)


def main():
    out = {}
    for metal, coin in (('copper', 'copper_bit'), ('iron', 'iron_mark'), ('gold', 'gold_crown')):
        out[coin] = outline(coin_single(metal), metal)
        out[coin + '_few'] = outline(coin_few(metal), metal)
        out[coin + '_stack'] = outline(coin_stack(metal), metal)
        out[coin + '_pile'] = outline(coin_pile(metal), metal)
    out['coin_pouch'] = from_map(POUCH_EMPTY, LEATHER)
    out['coin_pouch_some'] = from_map(POUCH_SOME, LEATHER)
    out['coin_pouch_full'] = from_map(POUCH_FULL, LEATHER)
    out['deed'] = from_map(DEED, PARCH)
    for name, img in out.items():
        save(img, os.path.join(ITEM_DIR, name + '.png'))
    save(gui_panel(7), os.path.join(GUI_DIR, 'market.png'))
    save(gui_panel(11), os.path.join(GUI_DIR, 'bank.png'))
    save(gui_sprites(), os.path.join(GUI_DIR, 'sprites.png'))

    if '--preview' in sys.argv:
        dest = sys.argv[sys.argv.index('--preview') + 1]
        names = list(out.keys())
        sheet = Image.new('RGBA', (len(names) * 16 * 8 + (len(names) - 1) * 8, 16 * 8), (139, 139, 139, 255))
        for i, n in enumerate(names):
            sheet.alpha_composite(Image.fromarray(out[n], 'RGBA').resize((128, 128), Image.NEAREST), (i * (128 + 8), 0))
        os.makedirs(dest, exist_ok=True)
        sheet.save(os.path.join(dest, 'economy_items.png'))
    print('wrote', len(out), 'item textures and 3 gui textures')


if __name__ == '__main__':
    main()
