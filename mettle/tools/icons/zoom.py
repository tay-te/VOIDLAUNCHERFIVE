"""python zoom.py tree id id ... -> big preview (x12) in the sheets dir"""
import sys, os, importlib
from PIL import Image, ImageDraw
from px import to_image
import build
tree, ids = sys.argv[1], sys.argv[2:]
out = sys.argv[-1] if ids and ids[-1].endswith('.png') else None
if out: ids = ids[:-1]
mod = importlib.import_module('tree_' + tree)
ids = ids or list(mod.ICONS)
sc = 12
im = Image.new('RGBA', ((16 * sc + 16) * min(len(ids), 6), (16 * sc + 30) * ((len(ids) + 5) // 6)), (48, 46, 50, 255))
d = ImageDraw.Draw(im)
for i, pid in enumerate(ids):
    g = build.grid(mod.ICONS[pid])
    x = (i % 6) * (16 * sc + 16) + 8; y = (i // 6) * (16 * sc + 30) + 8
    im.alpha_composite(to_image(g, sc), (x, y))
    d.text((x, y + 16 * sc + 4), pid, fill=(230, 230, 230, 255))
D = os.environ.get('ICONS_OUT', os.path.join(__import__('tempfile').gettempdir(), 'mettle-icons'))
os.makedirs(D, exist_ok=True)
im.save(os.path.join(D, out or 'zoom.png'))
