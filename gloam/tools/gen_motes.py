#!/usr/bin/env python3
"""
VOID Gloam's motes: the sprites of the small things in the air, and the mod's icon.

    python tools/gen_motes.py          (from gloam/; Windows Python wants C:\\ paths if you pass any)

It writes, deterministically (fixed seeds, no timestamps, so a rerun writes the same bytes):

    assets/gloam/textures/particle/<sprite>.png   the sprites, pixel art in natural colours
    assets/gloam/particles/<type>.json            which sprites each particle type draws from
    assets/gloam/icon.png                         128x128, a misty dawn valley

The sprites:

    leaf_0..7     a fallen leaf, grey so the game can tint it like the leaves it fell from
    pollen_0..2   pollen and dust: a speck with a faint rim, white (tinted pale cream in the game)
    firefly_0     a firefly's light: a warm yellow-green point with a soft glow, drawn full bright
    mist_0..3     ground mist: soft, uneven patches drawn lying flat, white, alpha only
    powder_0..3   powder snow blown off the ground: faint sideways streaks
    veil_0..3     ground mist as upright veils, wide and low, for when a shader pack draws the mist
    breath_0..2   a breath in the cold: a small round puff
    spray_0..3    waterfall spray: a clumpy puff
    snow_0..1     a fine snow grain

The mist, breath and spray sprites are drawn through Gloam's own particle pipeline, which does not cut away the faint
edges the way vanilla's particle shader does (it discards alpha below 0.1), so they carry smooth alpha (with the
faintest fringe trimmed, see trim()); everything else keeps to alpha a vanilla pipeline draws.
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


# ------------------------------------------------------------------------------------------------------------ leaves

# Hand-drawn leaves: '.' empty, 'a' the light side, 'b' the leaf, 'c' its shaded side and veins, 's' the stalk.
# Grey (the game tints them), light enough that the tint is the colour and not a darkening.
LEAVES = [
	['..ab.',
	 '.abbc',
	 'abbc.',
	 'bbcc.',
	 's....'],
	['.aab.',
	 'abbbc',
	 'abcbc',
	 '.bcc.',
	 '.s...'],
	['...ab',
	 '..abc',
	 '.abc.',
	 'abc..',
	 's....'],
	['.ab..',
	 'abbb.',
	 'bbcbc',
	 '.bccc',
	 '...cs'],
	['......',
	 '..abb.',
	 '.abbbc',
	 '.bbcbc',
	 '..bcc.',
	 's.....'],
	['.ab...',
	 'abbb..',
	 'abcbb.',
	 '.bbcbc',
	 '..bccc',
	 '....cs'],
	['..a...',
	 '.abb..',
	 'abbcb.',
	 'bbcbbc',
	 '.bccc.',
	 '...s..'],
	['.aab..',
	 'abbbc.',
	 'bbcbc.',
	 '.bcc..',
	 's.....',
	 '......'],
]
LEAF_GREY = {'a': 1.0, 'b': 0.86, 'c': 0.70, 's': 0.58}


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

def speck(size, core, rim, corner=0.0):
	"""A white speck: the middle texel(s) solid, the rim faint, the corners fainter still."""
	out = np.zeros((size, size, 4))
	out[..., :3] = 1
	c = (size - 1) / 2
	for y in range(size):
		for x in range(size):
			d = max(abs(x - c), abs(y - c))
			manhattan = abs(x - c) + abs(y - c)
			if d < 0.75:
				out[y, x, 3] = core
			elif manhattan <= 1.01 + (size - 3):
				out[y, x, 3] = rim
			else:
				out[y, x, 3] = corner
	return rgba(out)


def firefly():
	"""
	A firefly's light (Photinus: yellow-green, ~560 nm), 7x7: a bright pale core and a warm glow falling off around it,
	its faintest ring kept just above the alpha vanilla's particle shader cuts (0.1), so the glow reads at a distance.
	"""
	n = 7
	out = np.zeros((n, n, 4))
	for y in range(n):
		for x in range(n):
			d = math.hypot(x - 3, y - 3)
			if d < 0.5:
				out[y, x] = (0.95, 1.0, 0.66, 1.0)
			elif d < 1.2:
				out[y, x] = (0.82, 0.95, 0.34, 0.72)
			elif d < 1.6:
				out[y, x] = (0.74, 0.90, 0.27, 0.42)
			elif d < 2.3:
				out[y, x] = (0.70, 0.86, 0.24, 0.22)
			elif d < 3.0:
				out[y, x] = (0.66, 0.82, 0.22, 0.12)
	return rgba(out)


# ------------------------------------------------------------------------------------------------------- soft veils

def feather(n):
	"""
	A round window that is exactly 0 from 0.9 of the sprite's half-width out (so on every edge and well inside the
	corners) and rises softly toward the middle. A soft sprite's alpha is multiplied by it, so no quad edge can ever
	show, not even where a shader pack's filtering bleeds in the atlas's neighbouring sprites or its lighting lifts a
	faint fringe.
	"""
	y, x = np.mgrid[0:n, 0:n].astype(float)
	u = np.abs((x + 0.5) / n * 2 - 1)
	v = np.abs((y + 0.5) / n * 2 - 1)
	return 1 - smoothstep(0.5, 0.9, np.sqrt(u * u + v * v))


def trim(alpha):
	"""
	Cuts the faintest alpha to nothing and stretches the rest back over 0..1: a shader pack's particle program writes
	its translucency data for every pixel it keeps (Complementary darkens what lies behind), so the soft sprites must
	not leave a near-invisible fringe over the whole quad.
	"""
	return np.clip((alpha - 0.05) / 0.95, 0, 1)


def sheet(seed):
	"""
	A patch of ground mist lying flat, 64x64: an uneven, cloudy blob with soft, ragged edges. Seen at a low angle it
	stretches into a long low band by itself. Its alpha shapes and breaks up the sheet; how opaque it is comes from the
	game (the mist's depth and the angle it is seen at).
	"""
	n = 64
	rng = np.random.default_rng(seed)
	y, x = np.mgrid[0:n, 0:n].astype(float)
	u = (x + 0.5) / n * 2 - 1
	v = (y + 0.5) / n * 2 - 1
	r = np.sqrt(u * u + v * v)
	shape = 1 - smoothstep(0.3, 1.0, r + (fbm(rng, n, 2, 4) - 0.5) * 0.6)
	body = 0.45 + 0.55 * fbm(rng, n, 3, 4)
	out = np.ones((n, n, 4))
	out[..., 3] = trim(shape * body) * feather(n)
	return rgba(out)


def veil(seed):
	"""
	Ground mist as an upright veil, 64x64, for when a shader pack draws the mist (MistMote): wide and low, lying in long
	sideways streaks, feathered to nothing inside an ellipse well within the quad, so no edge shows and no flat bottom
	is cut where it meets the ground.
	"""
	n = 64
	rng = np.random.default_rng(seed)
	y, x = np.mgrid[0:n, 0:n].astype(float)
	u = (x + 0.5) / n * 2 - 1
	v = (y + 0.5) / n * 2 - 1
	r = np.sqrt((u / 0.92) ** 2 + (v / 0.34) ** 2)
	shape = 1 - smoothstep(0.25, 1.0, r + (streaks(rng, n, 2, 7, 3) - 0.5) * 0.5)
	body = 0.4 + 0.6 * streaks(rng, n, 2, 9, 3)
	window = 1 - smoothstep(0.75, 1.0, np.sqrt((u / 0.95) ** 2 + (v / 0.42) ** 2))
	out = np.ones((n, n, 4))
	out[..., 3] = trim(shape * body) * window
	return rgba(out)


def streak(seed):
	"""Powder snow blown off the ground, 16x16: a faint sideways streak, ragged."""
	n = 16
	rng = np.random.default_rng(seed)
	y, x = np.mgrid[0:n, 0:n].astype(float)
	u = (x + 0.5) / n * 2 - 1
	v = (y + 0.5) / n * 2 - 1
	r = np.sqrt((u / 1.0) ** 2 + (v / 0.38) ** 2)
	shape = 1 - smoothstep(0.2, 1.0, r + (streaks(rng, n, 1, 3, 2) - 0.5) * 0.6)
	out = np.ones((n, n, 4))
	out[..., 3] = trim(shape * (0.55 + 0.45 * streaks(rng, n, 1, 4, 2)) * 0.9) * feather(n)
	return rgba(out)


def puff(seed, n, clump):
	"""A small round puff (breath, spray): soft-edged, uneven by `clump`."""
	rng = np.random.default_rng(seed)
	y, x = np.mgrid[0:n, 0:n].astype(float)
	u = (x + 0.5) / n * 2 - 1
	v = (y + 0.5) / n * 2 - 1
	r = np.sqrt(u * u + v * v)
	shape = 1 - smoothstep(0.25, 0.98, r + (fbm(rng, n, 2, 3) - 0.5) * clump)
	alpha = trim(shape * (0.6 + 0.4 * fbm(rng, n, 2, 3)) * 0.85) * feather(n)
	out = np.ones((n, n, 4))
	out[..., 3] = alpha
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
	sprites['pollen_0'] = speck(3, 1.0, 0.45, 0.0)
	sprites['pollen_1'] = speck(3, 1.0, 0.25, 0.0)
	sprites['pollen_2'] = speck(4, 1.0, 0.40, 0.12)
	sprites['firefly_0'] = firefly()
	for i in range(4):
		sprites['mist_%d' % i] = sheet(9100 + i)
	for i in range(4):
		sprites['powder_%d' % i] = streak(9400 + i)
	for i in range(4):
		sprites['veil_%d' % i] = veil(9500 + i)
	for i in range(3):
		sprites['breath_%d' % i] = puff(9200 + i, 16, 0.35)
	for i in range(4):
		sprites['spray_%d' % i] = puff(9300 + i, 16, 0.8)
	sprites['snow_0'] = speck(3, 1.0, 0.5, 0.0)
	sprites['snow_1'] = speck(3, 0.95, 0.3, 0.12)
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
