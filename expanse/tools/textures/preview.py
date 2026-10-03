"""Contact-sheet preview: every texture at 8x (nearest neighbour), labelled,
plus a 3x3 tiled copy of each tileable block texture so seams are visible."""
import numpy as np
from PIL import Image, ImageDraw, ImageFont

SCALE = 8
TILE_SCALE = 4
PAD = 8
LABEL_H = 16


def _checker(w, h, a=(58, 60, 68), b=(70, 72, 82), cell=8):
    yy, xx = np.mgrid[0:h, 0:w]
    m = ((xx // cell + yy // cell) % 2).astype(bool)
    arr = np.zeros((h, w, 4), np.uint8)
    arr[..., 3] = 255
    arr[~m, :3] = a
    arr[m, :3] = b
    return Image.fromarray(arr, 'RGBA')


def _font():
    for p in ('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
              '/usr/share/fonts/dejavu/DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(p, 12)
        except OSError:
            pass
    return ImageFont.load_default()


def _render(arr, tileable):
    """Return the composited cell image (without label) for one texture."""
    im = Image.fromarray(arr, 'RGBA')
    w, h = im.size
    if w <= 16 and h <= 16:
        s = SCALE
    else:  # e.g. the 128px mod icon: show at 2x
        s = max(1, 256 // max(w, h))
    bg = _checker(w * s, h * s)
    bg.alpha_composite(im.resize((w * s, h * s), Image.NEAREST))
    if not tileable:
        return bg
    t = Image.new('RGBA', (w * 3, h * 3))
    for ty in range(3):
        for tx in range(3):
            t.paste(im, (tx * w, ty * h))
    t = t.resize((w * 3 * TILE_SCALE, h * 3 * TILE_SCALE), Image.NEAREST)
    bg2 = _checker(t.width, t.height)
    bg2.alpha_composite(t)
    out = Image.new('RGBA', (bg.width + PAD + bg2.width, max(bg.height, bg2.height)), (0, 0, 0, 0))
    out.alpha_composite(bg, (0, 0))
    out.alpha_composite(bg2, (bg.width + PAD, 0))
    return out


def build_sheet(entries, path, cols=4, title=None):
    """entries: list of (label, rgba ndarray, tileable bool)."""
    font = _font()
    cell_w = PAD + 16 * SCALE + PAD + 48 * TILE_SCALE + PAD
    rendered = [(label, _render(arr, tl)) for label, arr, tl in entries]
    rows = [rendered[i:i + cols] for i in range(0, len(rendered), cols)]
    head = 28 if title else 0
    row_h = [LABEL_H + max(im.height for _, im in r) + PAD for r in rows]
    sheet = Image.new('RGBA', (cols * cell_w, head + sum(row_h)), (34, 35, 42, 255))
    d = ImageDraw.Draw(sheet)
    if title:
        d.text((PAD, 6), title, fill=(235, 235, 245, 255), font=font)
    y = head
    for r, rh in zip(rows, row_h):
        for i, (label, im) in enumerate(r):
            x = i * cell_w
            d.text((x + PAD, y + 1), label, fill=(225, 225, 235, 255), font=font)
            sheet.alpha_composite(im, (x + PAD, y + LABEL_H))
        y += rh
    sheet.save(path)
    return sheet
