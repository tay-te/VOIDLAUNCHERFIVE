"""Labelled contact sheets of a lab_renders folder (needs Pillow):

    python3 contact_sheet.py <lab_renders dir>

writes contact_sheet.jpg (every step) and, when the optional stages ran, renderer_compare.jpg
(EEVEE as briefed / EEVEE quality pass / Cycles, same frame)."""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

STEPS = [("00_baseline", "00  baseline: game camera, baseline render settings"),
         ("01_camera_CAM_Low", "01a CAM_Low: 24 mm, lens 0.45 m off the ground"),
         ("01_camera_CAM_Long", "01b CAM_Long: 150 mm, compressed terrain"),
         ("02_light", "02  sculpted light"),
         ("03_materials", "03  physical materials"),
         ("04_air", "04  air"),
         ("05_lens", "05  camera imperfection"),
         ("06_final", "06  final: 100 %, 128 samples")]
COMPARE = [("05_lens", "EEVEE as briefed"),
           ("07_eevee_quality", "EEVEE + quality pass (baked GI probe)"),
           ("08_cycles", "Cycles (path traced)")]


def font(size):
    try:
        return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", size)
    except OSError:
        return ImageFont.load_default()


def sheet(src, items, cols, width, out):
    tiles = [(Image.open(os.path.join(src, f + ".png")).convert("RGB"), label) for f, label in items
             if os.path.exists(os.path.join(src, f + ".png")) and os.path.getsize(os.path.join(src, f + ".png"))]
    if not tiles:
        return None
    h = int(width * tiles[0][0].height / tiles[0][0].width)
    pad, bar = 12, 30
    rows = (len(tiles) + cols - 1) // cols
    img = Image.new("RGB", (cols * width + (cols + 1) * pad, rows * (h + bar) + (rows + 1) * pad), (18, 18, 20))
    d = ImageDraw.Draw(img)
    for i, (tile, label) in enumerate(tiles):
        x = pad + (i % cols) * (width + pad)
        y = pad + (i // cols) * (h + bar + pad)
        img.paste(tile.resize((width, h), Image.LANCZOS), (x, y + bar))
        d.text((x + 2, y + 5), label, fill=(225, 225, 225), font=font(17))
    img.save(out, quality=90)
    return out


def main():
    src = sys.argv[1]
    print(sheet(src, STEPS, 2, 640, os.path.join(src, "contact_sheet.jpg")))
    print(sheet(src, COMPARE, 3, 640, os.path.join(src, "renderer_compare.jpg")))


if __name__ == "__main__":
    main()
