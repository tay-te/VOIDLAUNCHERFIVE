"""Builds the stage 1 review set from the Motion Studio's frames (build/studio/shots/gif_<clip>_<pack>_<tick>.png):

    python tools/review/compose.py <shots dir> <out dir>

  <clip>.gif      Complementary and VOID Welkin side by side, one frame per tick (real time)
  <clip>_fp.gif   first person beside third person (Welkin), for the player clips that have a first-person block
  sheet_<group>.jpg   contact sheets: six frames per clip across its length (Welkin), with ticks and phases
"""
import glob
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

PANEL = 340
ZOOM = 0.62  # crop height as a share of the frame (the subject sits mid-frame); travelling clips use the whole frame


def frames(shots, base):
    found = []
    for f in glob.glob(os.path.join(shots, 'gif_' + base + '_*.png')):
        m = re.search(r'_(\d+\.\d)\.png$', f)
        if m and os.path.basename(f)[len('gif_' + base):].count('_') == 1:
            found.append((float(m.group(1)), f))
    return [f for _, f in sorted(found)], [t for t, _ in sorted(found)]


def font(size):
    for name in ('arialbd.ttf', 'arial.ttf', 'DejaVuSans-Bold.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


F_SMALL = font(13)
F_TITLE = font(15)


def panel(path, label, zoom=1.0):
    im = Image.open(path).convert('RGB')
    # The subject sits in the middle: crop to 4:3 around it so the panels stay large.
    w, h = im.size
    ch = int(h * zoom)
    cw = int(ch * 4 / 3)
    cy = int(h * 0.54) if zoom < 1 else h // 2
    top = max(0, min(h - ch, cy - ch // 2))
    im = im.crop(((w - cw) // 2, top, (w - cw) // 2 + cw, top + ch)).resize((PANEL, PANEL * 3 // 4), Image.LANCZOS)
    d = ImageDraw.Draw(im)
    tw = d.textlength(label, font=F_SMALL)
    d.rectangle([0, 0, tw + 10, 18], fill=(0, 0, 0))
    d.text((5, 2), label, font=F_SMALL, fill=(255, 255, 255))
    return im


def strip(panels, title):
    w = sum(p.width for p in panels)
    h = panels[0].height + 22
    out = Image.new('RGB', (w, h), (20, 20, 22))
    x = 0
    for p in panels:
        out.paste(p, (x, 22))
        x += p.width
    d = ImageDraw.Draw(out)
    d.text((6, 3), title, font=F_TITLE, fill=(235, 235, 235))
    return out


def save_gif(images, path, step_ms):
    pal = [im.convert('P', palette=Image.ADAPTIVE, colors=72) for im in images]
    durations = [step_ms] * len(pal)
    durations[-1] = max(step_ms, 600)
    pal[0].save(path, save_all=True, append_images=pal[1:], duration=durations, loop=0, optimize=True, disposal=1)


def main():
    shots, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    clips = sorted({re.sub(r'_(comp|welk|fp)_\d+\.\d\.png$', '', os.path.basename(f))[4:] for f in glob.glob(os.path.join(shots, 'gif_*_*.png'))
                    if re.search(r'_(comp|welk|fp)_\d+\.\d\.png$', f)})
    sheets = {}
    for clip in clips:
        comp, ticks = frames(shots, clip + '_comp')
        welk, wticks = frames(shots, clip + '_welk')
        name = clip.replace('player_sword_', 'player/sword/').replace('player_', 'player/', 1).replace('zombie_', 'zombie/', 1).replace(
            'gallows_knight_', 'gallows_knight/', 1)
        step = int(round((ticks[1] - ticks[0]) * 50)) if len(ticks) > 1 else 50
        z = 1.0 if ('dodge' in clip or 'backstep' in clip) else ZOOM
        if comp and welk:
            n = min(len(comp), len(welk))
            imgs = [strip([panel(comp[i], 'Complementary  t%d' % ticks[i], z), panel(welk[i], 'VOID Welkin  t%d' % ticks[i], z)], name) for i in range(n)]
            save_gif(imgs, os.path.join(out, clip + '.gif'), step)
            print(clip + '.gif', n, 'frames')
        fp, fticks = frames(shots, clip + '_fp')
        if fp and welk:
            n = min(len(fp), len(welk))
            imgs = [strip([panel(fp[i], 'first person  t%d' % fticks[i]), panel(welk[i], 'third person  t%d' % wticks[i], z)], name + '  (Welkin)') for i in
                    range(n)]
            save_gif(imgs, os.path.join(out, clip + '_fp.gif'), step)
            print(clip + '_fp.gif', n, 'frames')
        if welk:
            group = clip.split('_')[0] if not clip.startswith('gallows') else 'knight'
            picks = [welk[round(i * (len(welk) - 1) / 5)] for i in range(6)]
            pticks = [wticks[round(i * (len(welk) - 1) / 5)] for i in range(6)]
            row = strip([panel(p, 't%d' % t, z).resize((260, 195), Image.LANCZOS) for p, t in zip(picks, pticks)], name)
            sheets.setdefault(group, []).append(row)
    for group, rows in sheets.items():
        w = max(r.width for r in rows)
        sheet = Image.new('RGB', (w, sum(r.height for r in rows)), (20, 20, 22))
        y = 0
        for r in rows:
            sheet.paste(r, (0, y))
            y += r.height
        sheet.save(os.path.join(out, 'sheet_' + group + '.jpg'), quality=85)
        print('sheet_' + group + '.jpg', len(rows), 'clips')


if __name__ == '__main__':
    main()
