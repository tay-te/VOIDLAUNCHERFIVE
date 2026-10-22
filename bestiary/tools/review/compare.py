"""Two first-person films side by side (e.g. the heavy at fpSwingScale 1.0 and 0.6):

    python tools/review/compare.py <screenshots dir> <out.gif> <name A> <label A> <name B> <label B> [width]

reads gif_fp_<name>_<nn>.png for both names, one frame per tick.
"""
import glob
import os
import sys

from PIL import Image, ImageDraw


def main():
    shots, out, a, la, b, lb = sys.argv[1:7]
    width = int(sys.argv[7]) if len(sys.argv) > 7 else 640
    fa = sorted(glob.glob(os.path.join(shots, 'gif_fp_%s_[0-9]*.png' % a)))
    fb = sorted(glob.glob(os.path.join(shots, 'gif_fp_%s_[0-9]*.png' % b)))
    frames = []
    for i, (x, y) in enumerate(zip(fa, fb)):
        ia, ib = Image.open(x).convert('RGB'), Image.open(y).convert('RGB')
        h = round(ia.height * width / ia.width)
        sheet = Image.new('RGB', (width * 2, h))
        sheet.paste(ia.resize((width, h), Image.LANCZOS), (0, 0))
        sheet.paste(ib.resize((width, h), Image.LANCZOS), (width, 0))
        d = ImageDraw.Draw(sheet)
        for x0, label in ((0, la), (width, lb)):
            d.rectangle((x0, 0, x0 + 260, 16), fill=(0, 0, 0))
            d.text((x0 + 4, 2), '%s  t%02d' % (label, i), fill=(255, 255, 255))
        frames.append(sheet.convert('P', palette=Image.ADAPTIVE, colors=160))
    durations = [50] * len(frames)
    durations[-1] = 600
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=durations, loop=0, optimize=True)
    print(out, len(frames), 'frames')


if __name__ == '__main__':
    main()
