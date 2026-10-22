"""First person beside third person, one frame per tick (the combat showcase's fpgif part):

    python tools/review/sidebyside.py <screenshots dir> <out dir> [width per panel]

reads gif_fp_<name>_<nn>.png and gif_tp_<name>_<nn>.png and writes <name>_fp_tp.gif (20 fps, real time), labelled with
the tick.
"""
import glob
import os
import re
import sys

from PIL import Image, ImageDraw


def main():
    shots, out = sys.argv[1], sys.argv[2]
    width = int(sys.argv[3]) if len(sys.argv) > 3 else 640
    os.makedirs(out, exist_ok=True)
    names = sorted({re.sub(r'_\d+\.png$', '', os.path.basename(f))[len('gif_fp_'):] for f in glob.glob(os.path.join(shots, 'gif_fp_*.png'))})
    for name in names:
        fps = sorted(glob.glob(os.path.join(shots, 'gif_fp_%s_[0-9]*.png' % name)))
        frames = []
        for i, f in enumerate(fps):
            t = f.replace('gif_fp_', 'gif_tp_')
            if not os.path.exists(t):
                continue
            a, b = Image.open(f).convert('RGB'), Image.open(t).convert('RGB')
            h = round(a.height * width / a.width)
            sheet = Image.new('RGB', (width * 2, h))
            sheet.paste(a.resize((width, h), Image.LANCZOS), (0, 0))
            sheet.paste(b.resize((width, h), Image.LANCZOS), (width, 0))
            d = ImageDraw.Draw(sheet)
            for x, label in ((0, 'first person'), (width, 'third person (F5 behind)' if 'zombie' in name else 'third person (F5 front, mirrored)')):
                d.rectangle((x, 0, x + 260, 16), fill=(0, 0, 0))
                d.text((x + 4, 2), '%s  t%02d' % (label, i), fill=(255, 255, 255))
            frames.append(sheet.convert('P', palette=Image.ADAPTIVE, colors=160))
        if not frames:
            continue
        durations = [50] * len(frames)
        durations[-1] = 600
        path = os.path.join(out, name + '_fp_tp.gif')
        frames[0].save(path, save_all=True, append_images=frames[1:], duration=durations, loop=0, optimize=True)
        print(path, len(frames), 'frames')


if __name__ == '__main__':
    main()
