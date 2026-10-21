"""Labelled contact sheets from game-test screenshots.

    python tools/sheet.py <out.jpg> <columns> <width> <file.png>...

Each tile is scaled to <width> px wide and labelled with its file name (minus the counter and the fray_ prefix).
"""
import os
import sys

from PIL import Image, ImageDraw


def main():
    out, cols, width = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    files = sys.argv[4:]
    tiles = []
    for f in files:
        im = Image.open(f).convert("RGB")
        h = round(im.height * width / im.width)
        im = im.resize((width, h), Image.LANCZOS)
        label = os.path.basename(f).rsplit(".", 1)[0]
        if "_fray_" in label:
            label = label.split("_fray_", 1)[1]
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 8 + 7 * len(label), 16], fill=(0, 0, 0))
        d.text((4, 2), label, fill=(255, 255, 255))
        tiles.append(im)
    rows = (len(tiles) + cols - 1) // cols
    th = max(t.height for t in tiles)
    sheet = Image.new("RGB", (cols * width, rows * th), (24, 24, 24))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * width, (i // cols) * th))
    sheet.save(out, quality=85)
    print(out, sheet.size)


if __name__ == "__main__":
    main()
