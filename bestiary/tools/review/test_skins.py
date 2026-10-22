"""Test skins for the rig check (MotionShowcase part "rig"): every face of every box a different hue with a vertical
gradient (a wrong UV slice shows as a step), and opaque striped sleeve / jacket / pants / hat layers.

    python tools/review/test_skins.py src/gametest/resources/assets/bestiary_test/textures/skin
"""
import colorsys
import os
import sys

from PIL import Image


def box(img, u, v, w, h, d, hue0, overlay=False):
    faces = [  # (x, y, width, height) of the box-UV faces: up, down, west, north, east, south
        (u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h), (u + d + w + d, v + d, w, h)]
    for i, (x, y, fw, fh) in enumerate(faces):
        hue = (hue0 + i / 6.0) % 1.0
        for yy in range(fh):
            light = 0.35 + 0.5 * yy / max(1, fh - 1)
            for xx in range(fw):
                if overlay and (yy // 2 + xx) % 3 != 0:
                    continue
                r, g, b = colorsys.hls_to_rgb(hue, light, 0.8 if not overlay else 0.4)
                img.putpixel((x + xx, y + yy), (int(r * 255), int(g * 255), int(b * 255), 255))


def skin(slim):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    aw = 3 if slim else 4
    box(img, 0, 0, 8, 8, 8, 0.0)          # head
    box(img, 32, 0, 8, 8, 8, 0.05, True)  # hat
    box(img, 16, 16, 8, 12, 4, 0.1)       # body
    box(img, 16, 32, 8, 12, 4, 0.15, True)
    box(img, 40, 16, aw, 12, 4, 0.3)      # right arm
    box(img, 40, 32, aw, 12, 4, 0.35, True)
    box(img, 32, 48, aw, 12, 4, 0.55)     # left arm
    box(img, 48, 48, aw, 12, 4, 0.6, True)
    box(img, 0, 16, 4, 12, 4, 0.75)       # legs
    box(img, 0, 32, 4, 12, 4, 0.8, True)
    box(img, 16, 48, 4, 12, 4, 0.85)
    box(img, 0, 48, 4, 12, 4, 0.9, True)
    return img


out = sys.argv[1]
os.makedirs(out, exist_ok=True)
skin(False).save(os.path.join(out, 'wide.png'))
skin(True).save(os.path.join(out, 'slim.png'))
print('wrote', out)
