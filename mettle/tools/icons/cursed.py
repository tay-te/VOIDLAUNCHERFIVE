"""Cursed card icons (docs/DRAFT.md §4): each is a sibling perk's icon recast in the curse palette, with a small
crimson brand in the top-left corner, so the sixteen read as one family next to the trees they come from.

    python cursed.py          # writes the 16 PNGs next to the other perk icons

A stand-in set for the mechanics pass; the draft UI pass may draw them properly.
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PERK_DIR = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'mettle', 'textures', 'gui', 'perk'))

# cursed perk -> the perk whose icon it is recast from
BASE = {
    'greedy_pick': 'quarryman', 'black_lung': 'lantern_eyes',
    'reckless_edge': 'fencer', 'glass_dancer': 'light_feet',
    'bloodrage': 'berserker', 'immovable': 'iron_grip',
    'heavy_draw': 'long_shot', 'snap_shot': 'quick_nock',
    'iron_hide': 'thick_skin', 'bastion': 'shield_up',
    'featherweight': 'runner', 'bulls_back': 'pack_mule',
    'wolfs_hunger': 'iron_stomach', 'wild_luck': 'gatherer',
    'brittle_edge': 'keen_edge', 'slapdash': 'forged',
    'skulker': 'soft_step', 'cowards_edge': 'ambusher',
}

# dark -> light: bruise violet into ember red into a pale, sickly bone
RAMP = [(22, 10, 18), (52, 18, 34), (92, 26, 44), (142, 38, 52), (196, 72, 70), (232, 150, 130), (246, 222, 200)]
BRAND = [(0, 0, (232, 64, 56)), (1, 0, (232, 64, 56)), (0, 1, (232, 64, 56)), (1, 1, (120, 20, 30)), (2, 0, (120, 20, 30)), (0, 2, (120, 20, 30))]


def tone(r, g, b):
    lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    pos = lum * (len(RAMP) - 1)
    i = min(int(pos), len(RAMP) - 2)
    t = pos - i
    a, c = RAMP[i], RAMP[i + 1]
    return tuple(round(a[k] + (c[k] - a[k]) * t) for k in range(3))


def recast(src):
    im = Image.open(src).convert('RGBA')
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a:
                out.putpixel((x, y), tone(r, g, b) + (a,))
    for x, y, c in BRAND:
        out.putpixel((x, y), c + (255,))
    return out


def main():
    for name, base in BASE.items():
        recast(os.path.join(PERK_DIR, base + '.png')).save(os.path.join(PERK_DIR, name + '.png'))
    print(len(BASE), 'cursed icons ->', PERK_DIR)


if __name__ == '__main__':
    main()
