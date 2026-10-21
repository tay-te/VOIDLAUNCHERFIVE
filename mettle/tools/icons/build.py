"""Render the Mettle perk icons.

    python build.py                 # write every perk PNG + contact sheets
    python build.py blades defense  # only these trees
    python build.py --sheets DIR    # contact sheets go to DIR (default: <tmp>/mettle-icons)

Icons are 16x16 char grids (see perkkit.py / px.py).  The original Mining tree icons live in icons.py and are
never overwritten from here.
"""
import importlib
import os
import sys

from PIL import Image, ImageDraw

from px import render, to_image
import perkkit

HERE = os.path.dirname(os.path.abspath(__file__))
PERK_DIR = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'mettle', 'textures', 'gui', 'perk'))
TREES = ['blades', 'heavy_weapons', 'marksmanship', 'defense', 'mining', 'athletics', 'survival', 'smithing', 'general', 'stealth']
# drawn by icons.py, kept as they are
ORIGINAL = {'quarryman', 'ore_sense', 'sure_footing', 'rock_hauler', 'gem_cutter', 'hard_hat', 'strike_the_seam',
            'lantern_eyes', 'tunneler', 'motherlode', 'deep_delver', 'mountain_breaker'}


def grid(entry):
    if isinstance(entry, list):
        out = None
        for layer in entry:
            g = grid(layer)
            if out is None:
                out = g
            else:
                for y in range(16):
                    for x in range(16):
                        if g[y][x] is not None:
                            out[y][x] = g[y][x]
        return out
    leg = dict(perkkit.LEG)
    if isinstance(entry, tuple):
        entry, extra = entry
        leg.update(extra)
    rows = entry.out() if hasattr(entry, 'out') else entry
    assert len(rows) == 16 and all(len(r) <= 16 for r in rows), rows
    return render(rows, leg, outline=True, noout=perkkit.NOOUT)


def contact(items, path, title=''):
    sc, cols = 6, 6
    cw, chh = 16 * sc + 24, 16 * sc + 24 + 14 + 52
    n = len(items)
    rows = (n + cols - 1) // cols
    im = Image.new('RGBA', (cw * cols, chh * rows + 20), (33, 32, 35, 255))
    d = ImageDraw.Draw(im)
    d.text((6, 4), title, fill=(200, 200, 200, 255))
    for i, (name, g) in enumerate(items):
        x = (i % cols) * cw
        y = 20 + (i // cols) * chh
        d.rectangle([x + 6, y + 6, x + cw - 6, y + 16 * sc + 18], fill=(48, 46, 50, 255))
        im.alpha_composite(to_image(g, sc), (x + 12, y + 12))
        d.text((x + 8, y + 16 * sc + 21), name[:19], fill=(225, 225, 225, 255))
        # real sizes 1x 2x 3x on dark and on parchment
        bx = x + 8
        by = y + 16 * sc + 36
        for s, bg in ((1, (48, 46, 50, 255)), (2, (48, 46, 50, 255)), (3, (48, 46, 50, 255)), (2, (196, 182, 150, 255))):
            d.rectangle([bx - 1, by - 1, bx + 16 * s, by + 16 * s], fill=bg)
            im.alpha_composite(to_image(g, s), (bx, by))
            bx += 16 * s + 6
    im.save(path)


def main(argv):
    import tempfile
    sheets = os.path.join(tempfile.gettempdir(), 'mettle-icons')
    if '--sheets' in argv:
        i = argv.index('--sheets')
        sheets = argv[i + 1]
        del argv[i:i + 2]
    trees = argv or TREES
    os.makedirs(sheets, exist_ok=True)
    total = 0
    for t in trees:
        mod = importlib.import_module('tree_' + t)
        importlib.reload(mod)
        items = []
        for pid, entry in mod.ICONS.items():
            assert pid not in ORIGINAL, pid
            g = grid(entry)
            to_image(g).save(os.path.join(PERK_DIR, pid + '.png'))
            items.append((pid, g))
        shown = list(items)
        for ref in getattr(mod, 'REF', []):   # finished icons drawn elsewhere, shown for comparison
            from px import from_image
            shown.append((ref + ' (orig)', from_image(Image.open(os.path.join(PERK_DIR, ref + '.png')))))
        contact(shown, os.path.join(sheets, 'sheet_' + t + '.png'), t)
        total += len(items)
        print(t, len(items))
    print('total', total)


if __name__ == '__main__':
    main(sys.argv[1:])
