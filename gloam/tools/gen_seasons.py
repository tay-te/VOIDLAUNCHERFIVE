"""Generates the seasons' block textures and models (dev.voidmc.gloam.season.SeasonalLeaves).

  python tools/gen_seasons.py            # writes into src/main/resources/assets/gloam
  python tools/gen_seasons.py --preview out.png

What it makes:
  textures/block/seasons/twigs.png, twigs_top.png       bare branches, light grey (tinted by the leaves' tint)
  textures/block/seasons/twigs_bark*.png                 the same in grey-brown bark, for untinted leaves
  textures/block/seasons/<expanse leaves>_autumn|green   Expanse's leaves in their other season, from its own textures
  models/block/seasons/twigs(_bark).json                 a bare crown: two crossed twig planes and a flat one
  models/block/seasons/<leaves>_tufts_spring|autumn.json twigs with a few small clumps of leaves (thinning, budding)
  models/block/seasons/<leaves>_alt.json                 Expanse's leaf cube in its other season's texture

Expanse's textures are read from ../expanse; rerun after they change.
"""
import json
import math
import os
import random
import sys
import zlib

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'gloam')
EXPANSE = os.path.join(HERE, '..', '..', 'expanse', 'src', 'main', 'resources', 'assets', 'expanse', 'textures', 'block')
TEX = os.path.join(ROOT, 'textures', 'block', 'seasons')
MODELS = os.path.join(ROOT, 'models', 'block', 'seasons')

# the leaves that turn: (block path, namespace, tinted, other season's texture: None | ('autumn', palette) | ('green', colour))
LEAVES = [
	('oak_leaves', 'minecraft', True, None),
	('dark_oak_leaves', 'minecraft', True, None),
	('birch_leaves', 'minecraft', True, None),
	('jungle_leaves', 'minecraft', True, None),
	('acacia_leaves', 'minecraft', True, None),
	('mangrove_leaves', 'minecraft', True, None),
	('beech_leaves', 'expanse', False, ('autumn', [0xB8642A, 0xC98235, 0x9A4E28, 0xB07A30])),
	('willow_leaves', 'expanse', False, ('autumn', [0xC8B048, 0xB09A3C, 0xBFA850])),
	('maple_leaves', 'expanse', False, ('green', 0x5F8C35)),
	('orange_maple_leaves', 'expanse', False, ('green', 0x6B9038)),
	('aspen_leaves', 'expanse', False, ('green', 0x86A848)),
	('larch_leaves', 'expanse', False, ('green', 0x7FA850)),
]

BARK = (0.60, 0.50, 0.42)


def line(px, x0, y0, x1, y1, value):
	n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
	for i in range(n + 1):
		t = i / max(1, n)
		x, y = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
		if 0 <= x < 16 and 0 <= y < 16:
			px[(x, y)] = max(px.get((x, y), 0), value)


def branch(px, rng, x, y, angle, length, depth):
	x1 = x + math.cos(angle) * length
	y1 = y - math.sin(angle) * length
	line(px, x, y, x1, y1, 1.0 - 0.12 * depth)
	if depth >= 3:
		return
	for side in (-1, 1):
		if depth > 0 and rng.random() < 0.4:
			continue
		a = angle + side * (0.45 + rng.random() * 0.35)
		branch(px, rng, x1, y1, a, length * (0.62 + rng.random() * 0.15), depth + 1)


def twigs(seed, top):
	"""A branching tracery, light grey on clear, as a pixel map {(x, y): 0..1}."""
	rng = random.Random(seed)
	px = {}
	if top:
		# seen from above: limbs out from the middle, forking toward the edges
		for k in range(5):
			a = k * 2 * math.pi / 5 + rng.random() * 0.6
			branch(px, rng, 7.5 + rng.random(), 7.5 + rng.random(), a, 3.2, 1)
	else:
		# from the side: a limb up from the bottom, forking into the crown, and a stray twig
		branch(px, rng, 7.5, 15.5, math.pi / 2 + (rng.random() - 0.5) * 0.3, 6.5, 0)
		branch(px, rng, 2 + rng.random() * 3, 9, math.pi / 2 + 0.5, 3.5, 2)
		branch(px, rng, 11 + rng.random() * 3, 8, math.pi / 2 - 0.5, 3.5, 2)
	return px


def save_twigs(name, px, tint):
	"""Pixel-art twigs in the style of vanilla's leaves: a few flat grey steps (thick limbs darkest, the finest twigs
	lightest), a darker pixel under each limb for its shadow, and a light bud at the tips."""
	img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
	steps = {0: 120, 1: 138, 2: 156, 3: 172}
	for (x, y), v in sorted(px.items(), key=lambda kv: -kv[1]):
		depth = min(3, round((1.0 - v) / 0.12))
		g = steps[depth]
		img.putpixel((x, y), (int(g * tint[0]), int(g * tint[1]), int(g * tint[2]), 255))
	# a shadow pixel under the limbs where there is room, and buds at the ends of the finest twigs
	for (x, y), v in list(px.items()):
		if y + 1 < 16 and (x, y + 1) not in px and v > 0.85 and img.getpixel((x, y + 1))[3] == 0:
			g = 100
			img.putpixel((x, y + 1), (int(g * tint[0]), int(g * tint[1]), int(g * tint[2]), 255))
		around = sum((x + dx, y + dy) in px for dx in (-1, 0, 1) for dy in (-1, 0, 1)) - 1
		if around <= 1 and v < 0.75:
			g = 196
			img.putpixel((x, y), (int(g * tint[0]), int(g * tint[1]), int(g * tint[2]), 255))
	img.save(os.path.join(TEX, name + '.png'))
	with open(os.path.join(TEX, name + '.png.mcmeta'), 'w') as f:
		json.dump({'texture': {'mipmap_strategy': 'cutout'}}, f, indent=2)
	return img


def luma(c):
	return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def recolour(src, kind, arg, seed):
	"""Expanse's leaves in their other season: each pixel's brightness kept, its colour the season's."""
	img = Image.open(src).convert('RGBA')
	opaque = [img.getpixel((x, y)) for y in range(img.height) for x in range(img.width) if img.getpixel((x, y))[3] > 0]
	mean = sum(luma(p) for p in opaque) / max(1, len(opaque))
	rng = random.Random(seed)
	out = img.copy()
	cells = {}
	for y in range(img.height):
		for x in range(img.width):
			p = img.getpixel((x, y))
			if p[3] == 0:
				continue
			if kind == 'green':
				target = arg
			else:
				key = (x // 2, y // 2)
				if key not in cells:
					cells[key] = rng.choice(arg)
				target = cells[key]
			t = ((target >> 16) & 255, (target >> 8) & 255, target & 255)
			lift = luma(p) / max(1, mean)
			out.putpixel((x, y), tuple(min(255, int(c * lift)) for c in t) + (p[3],))
	return out


def model(name, data):
	with open(os.path.join(MODELS, name + '.json'), 'w') as f:
		json.dump(data, f, indent=2)


def plane_faces(tex, tint, dirs):
	face = {'uv': [0, 0, 16, 16], 'texture': tex}
	if tint:
		face['tintindex'] = 0
	return {d: dict(face) for d in dirs}


def twig_elements(tint, side, top):
	rot = lambda a: {'origin': [8, 8, 8], 'axis': 'y', 'angle': a, 'rescale': True}
	return [
		{'from': [0.8, 0, 8], 'to': [15.2, 16, 8], 'rotation': rot(45), 'shade': False, 'faces': plane_faces(side, tint, ['north', 'south'])},
		{'from': [8, 0, 0.8], 'to': [8, 16, 15.2], 'rotation': rot(45), 'shade': False, 'faces': plane_faces(side, tint, ['west', 'east'])},
		{'from': [0, 5, 0], 'to': [16, 5, 16], 'shade': False, 'faces': plane_faces(top, tint, ['up', 'down'])},
	]


def tuft_elements(tint):
	"""A few small clumps of leaves among the twigs."""
	out = []
	for (x, y, z, s) in [(1, 8, 2, 6), (9, 3, 8, 5), (5, 10, 9, 6), (10, 9, 1, 4)]:
		faces = {}
		for d in ['down', 'up', 'north', 'south', 'west', 'east']:
			f = {'uv': [x % 10, y % 10, x % 10 + s, y % 10 + s], 'texture': '#leaves'}
			if tint:
				f['tintindex'] = 0
			faces[d] = f
		out.append({'from': [x, y, z], 'to': [x + s, y + s, z + s], 'faces': faces})
	return out


def main():
	os.makedirs(TEX, exist_ok=True)
	os.makedirs(MODELS, exist_ok=True)
	side, top = twigs(11, False), twigs(23, True)
	sheet = [save_twigs('twigs', side, (1, 1, 1)), save_twigs('twigs_top', top, (1, 1, 1)),
		save_twigs('twigs_bark', side, BARK), save_twigs('twigs_bark_top', top, BARK)]
	for tinted, suffix in [(True, ''), (False, '_bark')]:
		s, t = 'gloam:block/seasons/twigs' + suffix, 'gloam:block/seasons/twigs' + suffix + '_top'
		model('twigs' + suffix, {'ambientocclusion': False, 'textures': {'particle': s, 'side': s, 'top': t},
			'elements': twig_elements(tinted, '#side', '#top')})
		model('tufts' + suffix, {'ambientocclusion': False, 'textures': {'particle': '#leaves', 'side': s, 'top': t},
			'elements': twig_elements(tinted, '#side', '#top') + tuft_elements(tinted)})
	for name, ns, tinted, other in LEAVES:
		own = f'{ns}:block/{name}'
		suffix = '' if tinted else '_bark'
		spring, autumn = own, own
		if other is not None:
			kind, arg = other
			src = os.path.join(EXPANSE, name + '.png')
			if not os.path.exists(src):
				print('missing', src)
				continue
			alt = f'{name}_{kind}'
			recolour(src, kind, arg, zlib.crc32(name.encode()) & 0xFFFF).save(os.path.join(TEX, alt + '.png'))
			sheet.append(Image.open(os.path.join(TEX, alt + '.png')))
			model(name + '_alt', {'parent': 'minecraft:block/leaves', 'textures': {'all': 'gloam:block/seasons/' + alt}})
			if kind == 'autumn':
				autumn = 'gloam:block/seasons/' + alt
			else:
				spring = 'gloam:block/seasons/' + alt
		model(name + '_tufts_spring', {'parent': 'gloam:block/seasons/tufts' + suffix, 'textures': {'leaves': spring}})
		model(name + '_tufts_autumn', {'parent': 'gloam:block/seasons/tufts' + suffix, 'textures': {'leaves': autumn}})
	if len(sys.argv) > 2 and sys.argv[1] == '--preview':
		out = Image.new('RGBA', (16 * len(sheet) * 8, 16 * 8), (120, 160, 220, 255))
		for i, im in enumerate(sheet):
			big = im.resize((128, 128), Image.NEAREST)
			out.alpha_composite(big, (i * 128, 0))
		out.save(sys.argv[2])


if __name__ == '__main__':
	main()
