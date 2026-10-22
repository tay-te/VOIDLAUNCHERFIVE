"""Pixel comparison of the rig check's screenshots from two builds (vanilla arms vs the elbow rig):

    python tools/review/rig_compare.py <before dir> <after dir> [out dir]

matches files by name (ignoring the counter prefix) and reports, per shot, how many pixels differ (any channel by more
than 8) and the largest difference; with an out dir, writes a side-by-side with a difference mask.
"""
import glob
import os
import re
import sys

from PIL import Image, ImageChops


def key(f):
    return re.sub(r'^\d+_', '', os.path.basename(f))


def main():
    a = {key(f): f for f in glob.glob(os.path.join(sys.argv[1], '*.png'))}
    b = {key(f): f for f in glob.glob(os.path.join(sys.argv[2], '*.png'))}
    out = sys.argv[3] if len(sys.argv) > 3 else None
    for k in sorted(a):
        if k not in b:
            continue
        ia, ib = Image.open(a[k]).convert('RGB'), Image.open(b[k]).convert('RGB')
        d = ImageChops.difference(ia, ib)
        px = d.load()
        n, mx = 0, 0
        for y in range(d.height):
            for x in range(d.width):
                m = max(px[x, y])
                mx = max(mx, m)
                if m > 8:
                    n += 1
        print('%-40s %7d px differ (%.3f %%), max %d' % (k, n, 100.0 * n / (d.width * d.height), mx))
        if out:
            os.makedirs(out, exist_ok=True)
            mask = d.convert('L').point(lambda v: 255 if v > 8 else 0)
            sheet = Image.new('RGB', (ia.width * 3, ia.height))
            sheet.paste(ia, (0, 0))
            sheet.paste(ib, (ia.width, 0))
            sheet.paste(Image.merge('RGB', (mask, mask, mask)), (ia.width * 2, 0))
            sheet.save(os.path.join(out, k.replace('.png', '_cmp.jpg')), quality=85)


if __name__ == '__main__':
    main()
