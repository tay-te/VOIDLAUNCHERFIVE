#!/usr/bin/env python3
"""
VOID Gloam's motes: the sprites of the small things in the air, and the mod's icon.

    python tools/gen_motes.py          (from gloam/; Windows Python wants C:\\ paths if you pass any)

It writes, deterministically (fixed seeds, no timestamps, so a rerun writes the same bytes):

    assets/gloam/textures/particle/<sprite>.png   the sprites, pixel art in natural colours
    assets/gloam/particles/<type>.json            which sprites each particle type draws from
    assets/gloam/icon.png                         128x128, a misty dawn valley

The sprites:

    leaf_0..7     a falling leaf, 4x4 or 5x5, three greys so the game can tint it with a palette colour
    pollen_0..2   pollen and dust: a one- to three-pixel speck, white (tinted pale cream in the game)
    firefly_0     a firefly's light: 2x2 bright pixels and a darker ring, drawn full bright, no glow
    mist_0..3     ground mist lying flat, 64x64, stepped and dithered (only when ground_mist.fsh cannot draw it)
    powder_0..3   powder snow blown off the ground: a ragged sideways streak in two steps
    veil_0..3     ground mist as upright veils, 128x48, for when a shader pack draws the mist
    breath_0..2   a breath in the cold: an 8x8 puff in three steps
    spray_0..3    waterfall spray: a 12x12 clumpy puff in three steps
    snow_0..1     a snow grain, one to three pixels

The look is pixel-true (docs/STYLE.md): every soft sprite is a few flat alpha steps with a Bayer 4x4 dither between
them and an empty outer pixel, so it stays crisp under nearest sampling and under a shader pack's lighting.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'gloam')
TEXTURES = os.path.join(ASSETS, 'textures', 'particle')
PARTICLES = os.path.join(ASSETS, 'particles')


def save(img, path):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path, optimize=False)


def rgba(array):
	"""An image from a float array (h, w, 4) in 0..1."""
	return Image.fromarray(np.clip(np.round(array * 255), 0, 255).astype(np.uint8), 'RGBA')


def smoothstep(e0, e1, x):
	t = np.clip((x - e0) / (e1 - e0), 0, 1)
	return t * t * (3 - 2 * t)


def value_noise(rng, size, cells):
	"""Smooth value noise on a size x size grid, `cells` lattice cells across, tiling."""
	lattice = rng.random((cells + 1, cells + 1))
	lattice[-1, :] = lattice[0, :]
	lattice[:, -1] = lattice[:, 0]
	t = np.linspace(0, cells, size, endpoint=False)
	i = np.floor(t).astype(int)
	f = t - i
	f = f * f * (3 - 2 * f)
	y0, x0 = np.meshgrid(i, i, indexing='ij')
	fy, fx = np.meshgrid(f, f, indexing='ij')
	a = lattice[y0, x0]
	b = lattice[y0, x0 + 1]
	c = lattice[y0 + 1, x0]
	d = lattice[y0 + 1, x0 + 1]
	return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(rng, size, base, octaves):
	total, amp, norm = np.zeros((size, size)), 1.0, 0.0
	for o in range(octaves):
		total += value_noise(rng, size, base * 2 ** o) * amp
		norm += amp
		amp *= 0.5
	return total / norm


def streaks(rng, size, across, up, octaves):
	"""Noise stretched sideways (`across` cells wide, `up` cells high per octave): the long streaks of lying mist."""
	total, amp, norm = np.zeros((size, size)), 1.0, 0.0
	for o in range(octaves):
		cx, cy = across * 2 ** o, up * 2 ** o
		lattice = rng.random((cy + 1, cx + 1))
		lattice[:, -1] = lattice[:, 0]
		ty = np.linspace(0, cy, size, endpoint=False)
		tx = np.linspace(0, cx, size, endpoint=False)
		iy, ix = np.floor(ty).astype(int), np.floor(tx).astype(int)
		fy, fx = ty - iy, tx - ix
		fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
		Y, X = np.meshgrid(iy, ix, indexing='ij')
		FY, FX = np.meshgrid(fy, fx, indexing='ij')
		a, b = lattice[Y, X], lattice[Y, X + 1]
		c, d = lattice[Y + 1, X], lattice[Y + 1, X + 1]
		total += ((a * (1 - FX) + b * FX) * (1 - FY) + (c * (1 - FX) + d * FX) * FY) * amp
		norm += amp
		amp *= 0.5
	return total / norm


# ------------------------------------------------------------------------------------------------------- pixel steps

# The ordered dither every stepped sprite fades with (docs/STYLE.md: steps, not gradients; dithered edges).
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


def stepped(value, levels):
	"""
	A smooth 0..1 field turned into `levels` flat steps above 0 (0, 1/levels, .. 1), each transition an ordered
	(Bayer 4x4) dither on the sprite's own pixel grid, so it reads as a few hard levels with dithered edges.
	"""
	h, w = value.shape
	y, x = np.mgrid[0:h, 0:w]
	threshold = BAYER[y % 4, x % 4]
	return np.clip(np.floor(value * levels + threshold), 0, levels) / levels


def margin(alpha, px=1):
	"""Clears the outermost `px` pixels, so no quad edge can show (nor a neighbour in the atlas bleed in)."""
	alpha[:px, :] = 0
	alpha[-px:, :] = 0
	alpha[:, :px] = 0
	alpha[:, -px:] = 0
	return alpha


# ------------------------------------------------------------------------------------------------------------ leaves

# Hand-drawn leaves, 4x4 and 5x5: '.' empty, 'a' the light side, 'b' the leaf, 'c' its shaded side and veins, 's' the
# stalk. Three greys (the game tints them with a palette colour), light enough that the tint is the colour.
LEAVES = [
	['.ab.',
	 'abbc',
	 'bbc.',
	 's...'],
	['..ab',
	 '.abc',
	 'abc.',
	 's...'],
	['.aa.',
	 'abbc',
	 'abcc',
	 '.cs.'],
	['ab..',
	 'abb.',
	 '.bcc',
	 '..cs'],
	['..a..',
	 '.abb.',
	 'abbbc',
	 '.bcc.',
	 '..s..'],
	['.ab..',
	 'abbb.',
	 'bbbcc',
	 '.bcc.',
	 '...s.'],
	['...ab',
	 '..abc',
	 '.abc.',
	 'abc..',
	 's....'],
	['.aab.',
	 'abbbc',
	 '.bcc.',
	 '..s..',
	 '.....'],
]
LEAF_GREY = {'a': 1.0, 'b': 0.84, 'c': 0.68, 's': 0.56}


def leaf(rows):
	h, w = len(rows), len(rows[0])
	out = np.zeros((h, w, 4))
	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			if ch in LEAF_GREY:
				v = LEAF_GREY[ch]
				out[y, x] = (v, v, v, 1)
	return rgba(out)


# ------------------------------------------------------------------------------------------------------- small motes

def speck(rows):
	"""A white speck drawn by hand: '#' solid, '+' half (a stepped rim), '.' empty."""
	h, w = len(rows), len(rows[0])
	out = np.zeros((h, w, 4))
	out[..., :3] = 1
	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			out[y, x, 3] = {'#': 1.0, '+': 0.5}.get(ch, 0.0)
	return rgba(out)


def firefly():
	"""
	A firefly's light (Photinus: yellow-green, ~560 nm), 4x4: two by two bright pale pixels and a ring of darker
	yellow-green ones beside them (none on the corners). No glow: the light is the pixels.
	"""
	out = np.zeros((4, 4, 4))
	for y in range(4):
		for x in range(4):
			inner = 1 <= x <= 2 and 1 <= y <= 2
			corner = x in (0, 3) and y in (0, 3)
			if inner:
				out[y, x] = (0.96, 1.0, 0.62, 1.0)
			elif not corner:
				out[y, x] = (0.62, 0.80, 0.20, 0.55)
	return rgba(out)


# ------------------------------------------------------------------------------------------------ stepped puffs, veils

def blob(rng, n, clump):
	"""A round, uneven 0..1 field filling an n x n sprite, 0 toward the rim."""
	y, x = np.mgrid[0:n, 0:n].astype(float)
	u = (x + 0.5) / n * 2 - 1
	v = (y + 0.5) / n * 2 - 1
	r = np.sqrt(u * u + v * v)
	return np.clip(1 - smoothstep(0.2, 0.92, r + (fbm(rng, n, 2, 3) - 0.5) * clump), 0, 1)


def puff(seed, n, clump):
	"""A small pixel puff (breath, spray): three alpha steps, dithered between, on its own pixel grid."""
	rng = np.random.default_rng(seed)
	field = blob(rng, n, clump) * (0.75 + 0.25 * fbm(rng, n, 2, 2))
	out = np.ones((n, n, 4))
	out[..., 3] = margin(stepped(field, 3))
	return rgba(out)


def streak(seed):
	"""Powder snow blown off the ground, 16x16: a ragged sideways streak in two alpha steps."""
	n = 16
	rng = np.random.default_rng(seed)
	y, x = np.mgrid[0:n, 0:n].astype(float)
	u = (x + 0.5) / n * 2 - 1
	v = (y + 0.5) / n * 2 - 1
	r = np.sqrt(u * u + (v / 0.3) ** 2)
	field = (1 - smoothstep(0.2, 1.0, r + (streaks(rng, n, 1, 3, 2) - 0.5) * 0.6)) * 0.8
	out = np.ones((n, n, 4))
	out[..., 3] = margin(stepped(field, 2) * 0.8)
	return rgba(out)


def sheet(seed):
	"""
	A patch of ground mist lying flat, 64x64, for when the mist cannot be drawn with its world-space shader (improved
	transparency): an uneven blob in four alpha steps, dithered between them. (Normally ground_mist.fsh draws the
	sheets on the world's own texel grid and this sprite is not used.)
	"""
	n = 64
	rng = np.random.default_rng(seed)
	field = blob(rng, n, 0.6) * (0.5 + 0.5 * fbm(rng, n, 3, 3))
	out = np.ones((n, n, 4))
	out[..., 3] = margin(stepped(field, 4), 2)
	return rgba(out)


def veil(seed):
	"""
	Ground mist as an upright veil, 128x48, for when a shader pack draws the mist (MistMote): wide and low, in long
	sideways streaks, four alpha steps with dithered edges, nothing on its outer pixels.
	"""
	w, h = 128, 48
	rng = np.random.default_rng(seed)
	y, x = np.mgrid[0:h, 0:w].astype(float)
	u = (x + 0.5) / w * 2 - 1
	v = (y + 0.5) / h * 2 - 1
	noise = streaks(rng, 128, 2, 9, 3)[40:88, :]
	body = streaks(rng, 128, 2, 11, 3)[20:68, :]
	r = np.sqrt((u / 0.95) ** 2 + (v / 0.8) ** 2)
	field = (1 - smoothstep(0.2, 0.95, r + (noise - 0.5) * 0.5)) * (0.45 + 0.55 * body)
	out = np.ones((h, w, 4))
	out[..., 3] = margin(stepped(field, 4), 2)
	return rgba(out)


# ------------------------------------------------------------------------------------------------------------- icon

def icon():
	"""
	The mod's icon: a valley at dawn in natural colours, flat pixel art (drawn at 64x64, doubled): a pale sky in
	bands, a low sun, a far blue ridge, a wooded middle ridge, white mist lying in the valley between them and over
	the meadow in front, a dark wood on the near slope.
	"""
	n = 64
	rng = np.random.default_rng(1307)
	img = np.zeros((n, n, 3))

	# sky: flat bands of colour, cool overhead to warm at the horizon
	bands = [(0, (146, 172, 198)), (8, (160, 184, 206)), (14, (178, 196, 210)), (19, (198, 207, 210)), (23, (216, 213, 200)),
		(26, (230, 217, 190)), (29, (240, 218, 180))]
	for i, (top, colour) in enumerate(bands):
		bottom = bands[i + 1][0] if i + 1 < len(bands) else n
		img[top:bottom] = np.array(colour) / 255
	# the sun, low and pale, half behind the far ridge
	sx, sy = 41, 29
	for y in range(n):
		for x in range(n):
			d = math.hypot(x - sx, y - sy)
			if d <= 4.2:
				img[y, x] = np.array((252, 242, 214)) / 255
			elif d <= 5.6:
				img[y, x] = np.array((246, 228, 192)) / 255

	def ridge(base, amp, scale, phase):
		xs = np.arange(n)
		h = base + amp * (0.6 * np.sin(xs / scale + phase) + 0.4 * np.sin(xs / (scale * 0.43) + phase * 2.1))
		return np.round(h).astype(int)

	def fill_below(tops, colour):
		for x in range(n):
			img[tops[x]:, x] = np.array(colour) / 255

	def mist(top, bottom, colour, ragged):
		for x in range(n):
			t = top + int(round(ragged * (math.sin(x / 3.1 + top) + 0.6 * math.sin(x / 1.7 + 2 * top))))
			b = bottom + int(round(ragged * 0.5 * math.sin(x / 4.7 + bottom)))
			img[t:b, x] = np.array(colour) / 255

	far = ridge(31, 2.6, 6.5, 0.4)
	far[30:] = np.minimum(far[30:], far[30:] - np.clip(4 - np.abs(np.arange(30, n) - 46) // 3, 0, 4))
	fill_below(far, (146, 160, 176))
	mist(35, 39, (214, 216, 216), 0.8)
	mid = ridge(39, 2.2, 5.0, 2.0)
	fill_below(mid, (104, 124, 112))
	# trees along the middle ridge: darker bumps on its crest
	for x in range(2, n - 2, 3):
		if rng.random() < 0.7:
			t = mid[x] - 1
			img[t, x] = np.array((86, 106, 96)) / 255
			img[t + 1, x - 1:x + 2] = np.array((86, 106, 96)) / 255
	mist(43, 47, (226, 226, 222), 1.0)
	mist(44, 46, (236, 234, 228), 0.6)
	# the near slope: a meadow falling from the left, sunlit on its crest
	near = np.array([int(round(44 + 12 * (x / n) ** 1.4 - 6 * (1 - x / n))) for x in range(n)])
	near = np.maximum(near, 38)
	fill_below(near, (84, 108, 62))
	for x in range(n):
		img[near[x], x] = np.array((132, 146, 84)) / 255
		img[near[x] + 1:near[x] + 3, x] = np.array((100, 124, 70)) / 255
	# a low mist over the meadow in front
	mist(57, 60, (210, 216, 206), 0.7)
	# a dark wood on the near slope: conifer and broadleaf silhouettes
	dark = np.array((46, 66, 48)) / 255
	shade = np.array((58, 82, 56)) / 255
	for cx, h in ((3, 16), (8, 13), (12, 18), (17, 11), (21, 9)):
		ground = near[cx] + 1
		for k in range(h):
			w = max(0, int((k / h) * 3.2))
			y = ground - h + k
			img[y, max(0, cx - w):min(n, cx + w + 1)] = dark
			if w > 0 and cx + w < n:
				img[y, cx + w] = shade
		img[ground:ground + 2, cx] = np.array((54, 44, 36)) / 255
	for cx, cy, r in ((26, 0, 3.2), (30, 0, 2.6)):
		cy = near[cx] - 3
		for y in range(n):
			for x in range(n):
				if math.hypot(x - cx, (y - cy) * 1.2) <= r:
					img[y, x] = dark if x < cx + 1 else shade
		img[cy + 3:cy + 5, cx] = np.array((54, 44, 36)) / 255

	out = Image.fromarray(np.clip(np.round(img * 255), 0, 255).astype(np.uint8), 'RGB').convert('RGBA')
	return out.resize((128, 128), Image.NEAREST)


# ------------------------------------------------------------------------------------------------------------- main

def main():
	sprites = {}
	for i, rows in enumerate(LEAVES):
		sprites['leaf_%d' % i] = leaf(rows)
	sprites['pollen_0'] = speck(['.+.', '+#+', '.+.'])
	sprites['pollen_1'] = speck(['##', '##'])
	sprites['pollen_2'] = speck(['.#.', '###', '.#.'])
	sprites['firefly_0'] = firefly()
	for i in range(4):
		sprites['mist_%d' % i] = sheet(9100 + i)
	for i in range(4):
		sprites['powder_%d' % i] = streak(9400 + i)
	for i in range(4):
		sprites['veil_%d' % i] = veil(9500 + i)
	for i in range(3):
		sprites['breath_%d' % i] = puff(9200 + i, 8, 0.35)
	for i in range(4):
		sprites['spray_%d' % i] = puff(9300 + i, 12, 0.8)
	sprites['snow_0'] = speck(['##', '##'])
	sprites['snow_1'] = speck(['.+.', '+#+', '.+.'])
	for name, img in sprites.items():
		save(img, os.path.join(TEXTURES, name + '.png'))

	types = {
		'leaf': ['leaf_%d' % i for i in range(len(LEAVES))],
		'pollen': ['pollen_0', 'pollen_1', 'pollen_2'],
		'firefly': ['firefly_0'],
		'mist': ['mist_%d' % i for i in range(4)],
		'powder': ['powder_%d' % i for i in range(4)],
		'veil': ['veil_%d' % i for i in range(4)],
		'breath': ['breath_%d' % i for i in range(3)],
		'spray': ['spray_%d' % i for i in range(4)],
		'snow': ['snow_0', 'snow_1'],
	}
	os.makedirs(PARTICLES, exist_ok=True)
	for name, textures in types.items():
		with open(os.path.join(PARTICLES, name + '.json'), 'w', newline='\n') as f:
			json.dump({'textures': ['gloam:' + t for t in textures]}, f, indent=2)
			f.write('\n')

	save(icon(), os.path.join(ASSETS, 'icon.png'))
	print('wrote %d sprites, %d particle types and the icon' % (len(sprites), len(types)))


if __name__ == '__main__':
	main()
