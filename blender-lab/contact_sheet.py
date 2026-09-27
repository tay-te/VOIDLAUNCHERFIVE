"""Labelled contact sheet of a lab_renders folder:  python3 contact_sheet.py <lab_renders dir> [out.jpg]"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ORDER = [("00_baseline", "00  baseline (game camera, baseline render settings)"),
         ("01_camera_CAM_Low", "01a CAM_Low  24 mm, lens 0.45 m off the ground"),
         ("01_camera_CAM_Long", "01b CAM_Long  long lens, compressed terrain"),
         ("02_light", "02  sculpted light"),
         ("03_materials", "03  physical materials"),
         ("04_air", "04  air"),
         ("05_lens", "05  camera imperfection"),
         ("06_final", "06  final (100 %, 128 samples)")]


def main():
    src = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(src, "contact_sheet.jpg")
    tiles = [(Image.open(os.path.join(src, f + ".png")).convert("RGB"), label)
             for f, label in ORDER if os.path.exists(os.path.join(src, f + ".png"))
             and os.path.getsize(os.path.join(src, f + ".png")) > 0]
    w = 640
    h = int(w * tiles[0][0].height / tiles[0][0].width)
    pad, bar, cols = 12, 30, 2
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * w + (cols + 1) * pad, rows * (h + bar) + (rows + 1) * pad), (18, 18, 20))
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 17)
    except OSError:
        font = ImageFont.load_default()
    d = ImageDraw.Draw(sheet)
    for i, (img, label) in enumerate(tiles):
        x = pad + (i % cols) * (w + pad)
        y = pad + (i // cols) * (h + bar + pad)
        sheet.paste(img.resize((w, h), Image.LANCZOS), (x, y + bar))
        d.text((x + 2, y + 5), label, fill=(225, 225, 225), font=font)
    sheet.save(out, quality=90)
    print(out)


if __name__ == "__main__":
    main()
