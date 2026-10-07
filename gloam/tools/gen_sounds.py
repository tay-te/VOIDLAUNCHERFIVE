#!/usr/bin/env python3
"""
The soundscape's clips: the ambient beds Gloam lays under the world (wind, rain, birds, insects, frogs, water) and the
distant thunder rolls, all synthesised here from noise and sine waves.

    python tools/gen_sounds.py            write the clips and sounds.json
    python tools/gen_sounds.py --check    decode every clip back and report its length, levels and loop seam;
                                          with --png DIR also draw a spectrogram of each into DIR

It writes, deterministically (fixed seeds, a fixed Ogg stream serial, so a rerun writes the same bytes):

    src/main/resources/assets/gloam/sounds/bed/<bed>.ogg     the looping beds, mono
    src/main/resources/assets/gloam/sounds/thunder/<n>.ogg   distant thunder, one-shots
    src/main/resources/assets/gloam/sounds.json

Every bed loops without a seam because it is built as one period of a periodic signal: the noise is shaped in the
frequency domain over exactly the clip's length (so it wraps), the slow swells are periodic, the echo is a circular
convolution, and every call, chirp, drop and bubble that runs off the end carries on from the start. Encoding is Ogg
Vorbis at quality 3 through libsndfile (pip install soundfile); nothing else is needed but numpy (and Pillow for the
spectrograms).
"""
import argparse
import json
import math
import os
import struct
import sys

import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'gloam')
SOUNDS = os.path.join(ASSETS, 'sounds')

SR = 24000
VORBIS_Q3 = 0.7  # libsndfile's compression level: quality = 1 - level, so 0.7 is Vorbis q3
SERIAL = 0x474C4F41  # 'GLOA': every stream gets the same serial, so the bytes do not change between runs


# ---------------------------------------------------------------------------------------------------------------------
# the toolkit: periodic noise, periodic swells, circular echo, events that wrap


def freqs(n, sr=SR):
	return np.fft.rfftfreq(n, 1.0 / sr)


def shaped(n, rng, shape, sr=SR):
	"""White noise shaped in the frequency domain over exactly n samples (so it loops), unit RMS."""
	spec = np.fft.rfft(rng.standard_normal(n))
	f = freqs(n, sr)
	f[0] = 1e-3
	y = np.fft.irfft(spec * shape(f), n)
	return y / (np.sqrt(np.mean(y * y)) + 1e-12)


def band(lo, hi, order=2):
	"""A smooth band-pass gain curve (Butterworth-like magnitudes), lo or hi None to leave that side open."""
	def g(f):
		out = np.ones_like(f)
		if lo:
			out = out / np.sqrt(1 + (lo / f) ** (2 * order))
		if hi:
			out = out / np.sqrt(1 + (f / hi) ** (2 * order))
		return out
	return g


def tilt(db_per_octave, ref=1000.0):
	"""A spectral slope: -3 dB/octave is pink, -6 brown."""
	return lambda f: (f / ref) ** (db_per_octave / (20 * math.log10(2)))


def mul(*gs):
	return lambda f: np.prod([g(f) for g in gs], axis=0)


def swell(n, rng, rate, lo=0.0, hi=1.0, sr=SR):
	"""A slow random swell between lo and hi, changing about `rate` times a second, periodic over n samples."""
	y = shaped(n, rng, lambda f: np.exp(-(f / rate) ** 2), sr)
	y = 0.5 + 0.5 * np.tanh(0.9 * y)
	return lo + (hi - lo) * y


def filt(x, shape, sr=SR):
	"""Filters x circularly (it stays a loop)."""
	return np.fft.irfft(np.fft.rfft(x) * shape(np.maximum(freqs(len(x), sr), 1e-3)), len(x))


def echo(x, rng, t60, wet, bright=4000.0, sr=SR):
	"""A diffuse echo (the air and the trees around), as a circular convolution with a decaying noise tail."""
	n = len(x)
	m = int(t60 * sr)
	t = np.arange(m) / sr
	ir = rng.standard_normal(m) * np.exp(-6.9 * t / t60)
	ir = np.fft.irfft(np.fft.rfft(ir) * band(None, bright, 1)(np.maximum(freqs(m, sr), 1e-3)), m)
	ir[: int(0.012 * sr)] *= np.linspace(0, 1, int(0.012 * sr))  # a short pre-delay: the reflections come later
	ir /= np.sqrt(np.sum(ir * ir)) + 1e-12
	h = np.zeros(n)
	h[:m] = ir
	tail = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(h), n)
	return x * (1 - wet) + tail * wet * 1.4


def far(x, cutoff, sr=SR):
	"""Distance: the air takes the highs away."""
	return filt(x, band(None, cutoff, 1), sr)


def put(buf, ev, at):
	"""Adds an event into the loop at sample `at`, wrapping round the end."""
	n = len(buf)
	idx = (int(at) + np.arange(len(ev))) % n
	buf[idx] += ev


def env_ar(m, attack, release, sr=SR):
	"""An attack-release envelope over m samples: raised-cosine attack, exponential-ish release to zero."""
	t = np.arange(m) / sr
	a = np.clip(t / max(attack, 1e-4), 0, 1)
	a = 0.5 - 0.5 * np.cos(np.pi * a)
	r = np.clip((m / sr - t) / max(release, 1e-4), 0, 1)
	return a * r ** 1.5


def tone(freq, sr=SR):
	"""A sine from a per-sample frequency curve."""
	return np.sin(2 * np.pi * np.cumsum(freq) / sr)


def ring(m, f0, tau, rng, rise=0.0, sr=SR):
	"""A damped sine (a drop, a tap, a bubble), its pitch rising by `rise` (relative) over its decay."""
	t = np.arange(m) / sr
	f = f0 * (1 + rise * t / tau)
	return np.sin(2 * np.pi * np.cumsum(f) / sr + rng.uniform(0, 6.28)) * np.exp(-t / tau)


def burst(m, rng, lo, hi, tau, sr=SR):
	"""A short decaying burst of band-limited noise (a splash, a leaf flick)."""
	y = rng.standard_normal(m)
	y = np.fft.irfft(np.fft.rfft(y) * band(lo, hi, 2)(np.maximum(freqs(m, sr), 1e-3)), m)
	return y * np.exp(-np.arange(m) / sr / tau)


def poisson(rng, n, rate, sr=SR):
	"""Event start samples for a Poisson process of `rate` per second over the loop."""
	count = rng.poisson(rate * n / sr)
	return np.sort(rng.integers(0, n, count))


def impulses(n, rng, rate, sr=SR, amp=lambda rng, k: rng.exponential(1.0, k)):
	buf = np.zeros(n)
	at = poisson(rng, n, rate, sr)
	np.add.at(buf, at, amp(rng, len(at)))
	return buf


def cconv(x, kernel):
	"""Circular convolution of a loop with a short kernel."""
	n = len(x)
	h = np.zeros(n)
	h[: len(kernel)] = kernel
	return np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(h), n)


# ---------------------------------------------------------------------------------------------------------------------
# birds: the calls the choruses are made of


def bird_note(rng, dur, f0, f1, vib=0.0, vib_rate=0.0, harm=0.12, attack=0.01, release=None, sr=SR):
	m = max(8, int(dur * sr))
	t = np.arange(m) / sr
	f = f0 + (f1 - f0) * (t / (m / sr)) ** rng.uniform(0.6, 1.6)
	if vib:
		f = f * (1 + vib * np.sin(2 * np.pi * vib_rate * t + rng.uniform(0, 6.28)))
	ph = 2 * np.pi * np.cumsum(f) / sr
	if 2 * f.max() > 0.45 * sr:
		harm = 0  # the overtone would fold back below the Nyquist limit
	y = np.sin(ph) + harm * np.sin(2 * ph + 0.5)
	return y * env_ar(m, attack, release if release is not None else dur * 0.6, sr)


def seq(parts, sr=SR):
	"""Joins (gap_seconds, samples) pairs into one phrase."""
	out = []
	for gap, y in parts:
		out.append(np.zeros(int(gap * sr)))
		out.append(y)
	return np.concatenate(out) if out else np.zeros(1)


def warbler(rng):
	"""A quick, wandering warble of short notes, 3-6 kHz (a blackcap, a garden warbler)."""
	parts = []
	base = rng.uniform(3000, 4500)
	for _ in range(rng.integers(7, 16)):
		f0 = base * rng.uniform(0.75, 1.4)
		parts.append((rng.uniform(0.01, 0.04), bird_note(rng, rng.uniform(0.03, 0.09), f0, f0 * rng.uniform(0.7, 1.4), harm=0.08)))
	return seq(parts)


def whistler(rng):
	"""A slow fluty song with glides (a blackbird), ending in a thin twitter."""
	parts = []
	for _ in range(rng.integers(3, 6)):
		f0 = rng.uniform(1600, 2900)
		parts.append((rng.uniform(0.03, 0.12), bird_note(rng, rng.uniform(0.12, 0.35), f0, f0 * rng.uniform(0.85, 1.25),
			vib=0.012, vib_rate=rng.uniform(20, 40), harm=0.2, attack=0.03)))
	for _ in range(rng.integers(3, 7)):
		f0 = rng.uniform(5000, 7000)
		parts.append((0.012, bird_note(rng, 0.025, f0, f0 * 0.8, harm=0.02) * 0.4))
	return seq(parts)


def trill(rng):
	"""A rapid run of repeated notes falling in pitch (a wren, a chaffinch's run)."""
	parts = []
	k = rng.integers(10, 26)
	f_hi, f_lo = rng.uniform(5000, 7000), rng.uniform(3200, 4500)
	rate = rng.uniform(14, 24)
	for i in range(k):
		f = f_hi + (f_lo - f_hi) * i / k
		parts.append((1 / rate - 0.03, bird_note(rng, 0.03, f * 1.1, f * 0.85, harm=0.05)))
	if rng.random() < 0.6:  # the chaffinch's flourish
		parts.append((0.03, bird_note(rng, 0.18, 3800, 2600, harm=0.1)))
	return seq(parts)


def two_note(rng):
	"""Two notes, again and again (a chiffchaff; a great tit's 'tea-cher')."""
	a, b = rng.uniform(4200, 6400), rng.uniform(3400, 4600)
	parts = []
	for i in range(rng.integers(5, 11)):
		f = a if i % 2 == 0 else b
		parts.append((rng.uniform(0.11, 0.2), bird_note(rng, 0.075, f * 1.05, f * 0.95, harm=0.06)))
	return seq(parts)


def coo(rng):
	"""A wood pigeon's soft five-note coo, low and hollow."""
	f = rng.uniform(430, 560)
	parts = []
	for i, (d, g) in enumerate([(0.35, 0.18), (0.55, 0.12), (0.3, 0.1), (0.3, 0.3), (0.3, 0.12)]):
		y = bird_note(rng, d, f * 1.03, f * 0.96, harm=0.35, attack=0.08, release=d * 0.5)
		parts.append((g if i else 0, y * (1.0 if i != 1 else 1.2)))
	return seq(parts)


def cuckoo(rng):
	"""The cuckoo: two falling notes, a few times over."""
	f = rng.uniform(650, 760)
	parts = []
	for i in range(rng.integers(3, 7)):
		parts.append((0.6 if i else 0, bird_note(rng, 0.22, f, f * 0.98, harm=0.25, attack=0.04)))
		parts.append((0.12, bird_note(rng, 0.32, f * 0.8, f * 0.77, harm=0.25, attack=0.04)))
	return seq(parts)


def caw(rng, sr=SR):
	"""A crow's caw: harsh, buzzing, a few times."""
	parts = []
	for i in range(rng.integers(2, 5)):
		d = rng.uniform(0.22, 0.34)
		m = int(d * sr)
		t = np.arange(m) / sr
		f = rng.uniform(520, 680) * (1 - 0.15 * t / d)
		ph = 2 * np.pi * np.cumsum(f) / sr
		saw = sum(np.sin(k * ph) / k for k in range(1, 12))
		rough = 1 + 0.5 * np.sin(2 * np.pi * 70 * t)
		y = saw * rough
		y = np.fft.irfft(np.fft.rfft(y) * band(900, 2600, 1)(np.maximum(freqs(m, sr), 1e-3)), m)
		y = y * env_ar(m, 0.02, d * 0.4)
		parts.append((rng.uniform(0.25, 0.5) if i else 0, y / (np.abs(y).max() + 1e-9)))
	return seq(parts)


def chip(rng):
	"""A single call note (a robin's tick, a blackbird's chink)."""
	f = rng.uniform(4500, 7500)
	return seq([(0, bird_note(rng, rng.uniform(0.015, 0.04), f, f * rng.uniform(0.7, 1.1), harm=0.04))])


# ---------------------------------------------------------------------------------------------------------------------
# the beds


def wind_open(n, rng):
	"""Wind over open grassland: a deep, slow body with a breathy top that rises in the gusts."""
	gust = swell(n, rng, 0.12, 0.0, 1.0)
	body = shaped(n, rng, mul(tilt(-6, 200), band(50, 700, 2)))
	breath = shaped(n, rng, mul(tilt(-3, 800), band(400, 2500, 2)))
	grass = shaped(n, rng, band(2500, 7000, 2))
	slow = swell(n, rng, 0.05, 0.55, 1.0)
	return body * slow * (0.6 + 0.4 * gust) + breath * 0.45 * gust ** 1.5 * slow + grass * 0.12 * gust ** 2


def wind_leaves(n, rng):
	"""Wind through a wood: leaves rustling and flickering, louder and busier in the gusts, a soft roar beneath."""
	gust = swell(n, rng, 0.1, 0.0, 1.0)
	flutter = swell(n, rng, 9.0, 0.0, 1.0) ** 2.5
	rustle = shaped(n, rng, mul(band(1200, 7500, 2), tilt(-1.5, 3000)))
	roar = shaped(n, rng, mul(tilt(-6, 200), band(60, 600, 2)))
	flicks = impulses(n, rng, 160, amp=lambda r, k: r.exponential(1.0, k) ** 1.2)
	flicks = cconv(flicks, burst(int(0.006 * SR), rng, 2500, 9000, 0.0015))
	flicks /= np.sqrt(np.mean(flicks ** 2)) + 1e-12
	busy = 0.25 + 0.75 * gust
	return rustle * (0.15 + 0.85 * flutter) * busy * 0.8 + flicks * 0.25 * busy ** 2 + roar * (0.35 + 0.3 * gust)


def wind_high(n, rng):
	"""Wind on a bare summit: hollow, whistling, moaning, over a deep rumble."""
	out = np.zeros(n)
	t = np.arange(n) / SR
	for k in range(3):
		f_mid = rng.uniform(260, 720)
		drift = swell(n, rng, 0.08, -1, 1)
		f = f_mid * (1 + 0.18 * drift)
		cycles = round(np.sum(f) / SR)
		f *= cycles * SR / np.sum(f)  # a whole number of cycles over the loop, so the whistle wraps too
		carrier = np.sin(2 * np.pi * np.cumsum(f) / SR)
		hum = shaped(n, rng, lambda fr: np.exp(-(fr / 25.0) ** 2))  # narrow noise: the whistle breathes
		amp = swell(n, rng, 0.07, 0.0, 1.0) ** 2
		out += carrier * hum * amp * (0.5 / (k + 1) ** 0.5)
	hollow = shaped(n, rng, lambda fr: np.exp(-((np.log(fr) - np.log(240)) / 0.35) ** 2))
	rumble = shaped(n, rng, mul(tilt(-6, 100), band(30, 300, 2)))
	hiss = shaped(n, rng, band(1500, 5000, 2))
	gust = swell(n, rng, 0.1, 0.2, 1.0)
	return out * 0.9 + hollow * 0.35 * gust + rumble * 0.6 * (0.6 + 0.4 * gust) + hiss * 0.1 * gust ** 2


def rain_ground(n, rng):
	"""Rain on open ground and grass: a steady hiss full of small drops."""
	hiss = shaped(n, rng, mul(band(600, 9000, 2), tilt(-1.5, 3000)))
	small = cconv(impulses(n, rng, 900), burst(int(0.004 * SR), rng, 2000, 10000, 0.0008))
	mid = cconv(impulses(n, rng, 160, amp=lambda r, k: r.exponential(1.0, k)), burst(int(0.01 * SR), rng, 700, 4000, 0.0025))
	small /= np.sqrt(np.mean(small ** 2))
	mid /= np.sqrt(np.mean(mid ** 2))
	vary = swell(n, rng, 0.08, 0.8, 1.0)
	return (hiss * 0.7 + small * 0.5 + mid * 0.35) * vary


def rain_leaves(n, rng):
	"""Rain on a canopy: bright pattering taps on the leaves, big slow drips falling through, a hiss beneath."""
	out = np.zeros(n)
	for at in poisson(rng, n, 220):
		m = int(0.02 * SR)
		f0 = rng.uniform(1800, 5500)
		out[np.arange(at, at + m) % n] += ring(m, f0, rng.uniform(0.0015, 0.004), rng) * rng.exponential(1.0) ** 1.3
	for at in poisson(rng, n, 5):  # drips from the leaves, heavier
		m = int(0.06 * SR)
		f0 = rng.uniform(700, 1600)
		y = ring(m, f0, rng.uniform(0.008, 0.02), rng, rise=0.15) + burst(m, rng, 800, 6000, 0.004) * 0.6
		out[np.arange(at, at + m) % n] += y * rng.uniform(1.0, 2.5)
	out /= np.sqrt(np.mean(out ** 2))
	hiss = shaped(n, rng, mul(band(1500, 9000, 2), tilt(-2, 3000)))
	patter = cconv(impulses(n, rng, 600), burst(int(0.005 * SR), rng, 1500, 8000, 0.001))
	patter /= np.sqrt(np.mean(patter ** 2))
	return out * 0.5 + hiss * 0.45 + patter * 0.4


def rain_roof(n, rng):
	"""Rain on a roof heard from under it: dull taps and thuds, a low roar, drips from the eaves into a puddle."""
	out = np.zeros(n)
	for at in poisson(rng, n, 140):
		m = int(0.04 * SR)
		y = ring(m, rng.uniform(220, 900), rng.uniform(0.004, 0.012), rng) + burst(m, rng, 200, 2000, 0.003) * 0.5
		out[np.arange(at, at + m) % n] += y * rng.exponential(1.0) ** 1.2
	out /= np.sqrt(np.mean(out ** 2))
	roar = shaped(n, rng, mul(band(80, 1600, 2), tilt(-3, 400)))
	eaves = np.zeros(n)
	step = SR * 0.7
	at = 0.0
	while at < n:  # a drip from the eaves, every half second or so, not quite regular
		m = int(0.09 * SR)
		y = ring(m, rng.uniform(900, 1500), rng.uniform(0.012, 0.025), rng, rise=0.4)
		eaves[np.arange(int(at), int(at) + m) % n] += y * rng.uniform(0.5, 1.0)
		at += step * rng.uniform(0.6, 1.5)
	eaves = echo(eaves, rng, 0.4, 0.3, 3000)
	return out * 0.6 + roar * 0.55 + eaves * 0.25


def dawn_chorus(n, rng):
	"""The dawn chorus: many birds singing at once, near and far, under the far wash of all the rest."""
	out = np.zeros(n)
	kinds = [(warbler, 3.0), (whistler, 2.0), (trill, 2.4), (two_note, 1.6), (chip, 3.0), (coo, 0.35), (cuckoo, 0.15)]
	total = sum(w for _, w in kinds)
	for kind, w in kinds:
		for at in poisson(rng, n, 4.2 * w / total):
			y = kind(rng)
			d = rng.uniform(0.25, 1.0)  # how near: 1 near, 0.25 far
			y = far(y, 2500 + 6000 * d) if len(y) > 64 else y
			put(out, y * d ** 1.3, at)
	wash = shaped(n, rng, lambda f: np.exp(-((np.log(f) - np.log(4200)) / 0.3) ** 2))
	wash *= swell(n, rng, 6.0, 0.0, 1.0) ** 3 * 0.05  # the far birds blur into a shimmer
	out = echo(out, rng, 1.3, 0.35, 5000)
	return out + wash


def birds_day(n, rng):
	"""Birds through the day: sparse, distant, a song here and a call there, a crow far off."""
	out = np.zeros(n)
	kinds = [(warbler, 1.0), (whistler, 1.0), (trill, 0.8), (two_note, 1.0), (chip, 1.6), (coo, 0.25), (caw, 0.25)]
	total = sum(w for _, w in kinds)
	for kind, w in kinds:
		for at in poisson(rng, n, 0.9 * w / total):
			d = rng.uniform(0.2, 0.75)
			y = far(kind(rng), 2000 + 5000 * d)
			put(out, y * d ** 1.3, at)
	wash = shaped(n, rng, lambda f: np.exp(-((np.log(f) - np.log(3800)) / 0.35) ** 2))
	wash *= swell(n, rng, 3.0, 0.0, 1.0) ** 3 * 0.006  # the faintest far twitter, so the gaps are never dead
	return echo(out, rng, 1.6, 0.45, 4500) + wash


def crickets(n, rng):
	"""Summer night: field crickets chirping near and far, tree crickets trilling steadily, a far insect hum."""
	out = np.zeros(n)
	for k in range(9):
		f = rng.uniform(4200, 5100)
		d = rng.uniform(0.15, 1.0)
		period = rng.uniform(0.42, 0.75)
		pulses = rng.integers(3, 5)
		at = rng.uniform(0, period) * SR
		while at < n:
			chirp = []
			for p in range(pulses):
				m = int(0.016 * SR)
				t = np.arange(m) / SR
				y = (np.sin(2 * np.pi * f * t) + 0.06 * np.sin(4 * np.pi * f * t)) * np.sin(np.pi * t / t[-1]) ** 2
				chirp.append((0.018 if p else 0, y))
			put(out, seq(chirp) * d * rng.uniform(0.85, 1.0), at)
			at += period * SR * rng.uniform(0.95, 1.05)
	t = np.arange(n) / SR
	for k in range(3):  # tree crickets: an endless soft trill
		f = rng.uniform(2600, 3300)
		rate = rng.uniform(38, 55)
		cycles_f, cycles_r = round(f * n / SR), round(rate * n / SR)
		car = np.sin(2 * np.pi * cycles_f * t * SR / n)
		pul = np.maximum(0, np.sin(2 * np.pi * cycles_r * t * SR / n)) ** 3
		out += car * pul * swell(n, rng, 0.06, 0.15, 0.5) * 0.35
	hum = shaped(n, rng, lambda fr: np.exp(-((np.log(fr) - np.log(3800)) / 0.18) ** 2)) * 0.06
	return echo(out, rng, 0.9, 0.3, 7000) + hum


def frogs(n, rng):
	"""Frogs at the water on a spring evening: tree frogs' rattling bouts, pool frogs' purring croaks, soft grunts."""
	out = np.zeros(n)
	for k in range(4):  # tree frogs: 'krak-krak-krak', a few seconds at a time
		f = rng.uniform(1900, 2500)
		d = rng.uniform(0.25, 1.0)
		at = rng.uniform(0, 4) * SR
		while at < n:
			bout = []
			rate = rng.uniform(4.5, 6.5)
			for i in range(rng.integers(6, 16)):
				m = int(0.045 * SR)
				t = np.arange(m) / SR
				ph = 2 * np.pi * f * t
				y = (np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph)) * (0.5 + 0.5 * np.sin(2 * np.pi * 190 * t)) ** 2
				bout.append((1 / rate - 0.045 if i else 0, y * env_ar(m, 0.004, 0.03)))
			put(out, far(seq(bout), 3000 + 4000 * d) * d, at)
			at += rng.uniform(3, 8) * SR
	for k in range(4):  # pool frogs: a purring, rattling croak
		f = rng.uniform(500, 900)
		d = rng.uniform(0.2, 0.9)
		at = rng.uniform(0, 3) * SR
		while at < n:
			dur = rng.uniform(0.4, 1.0)
			m = int(dur * SR)
			t = np.arange(m) / SR
			ph = 2 * np.pi * f * (1 + 0.08 * t / dur) * t
			y = sum(np.sin(h * ph) / h ** 0.8 for h in range(1, 6))
			y *= (0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(22, 34) * t)) ** 3
			y = np.fft.irfft(np.fft.rfft(y) * band(400, 2200, 1)(np.maximum(freqs(m), 1e-3)), m)
			put(out, y * env_ar(m, 0.05, 0.2) * d * 0.5, at)
			at += rng.uniform(1.5, 5) * SR
	for at in poisson(rng, n, 1.2):  # low grunts and plops
		m = int(0.25 * SR)
		t = np.arange(m) / SR
		y = np.sin(2 * np.pi * rng.uniform(180, 320) * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 14 * t)) ** 2
		put(out, y * env_ar(m, 0.02, 0.12) * rng.uniform(0.1, 0.3), at)
	return echo(out, rng, 1.1, 0.35, 5000)


def cicadas(n, rng):
	"""Cicadas in the heat: buzzing songs swelling and fading in the trees, overlapping."""
	out = np.zeros(n)
	t = np.arange(n) / SR
	for k in range(5):
		centre = rng.uniform(4200, 7000)
		buzz = shaped(n, rng, lambda f, c=centre: np.exp(-((np.log(f) - np.log(c)) / 0.15) ** 2))
		rate = round(rng.uniform(180, 380) * n / SR)
		click = (0.5 + 0.5 * np.sin(2 * np.pi * rate * t * SR / n)) ** 4
		cycles = rng.integers(2, 5)  # each song swells and fades a whole number of times over the loop
		phase = rng.uniform(0, 6.28)
		sw = np.clip(np.sin(2 * np.pi * cycles * t * SR / n + phase) * 1.4 - 0.2, 0, 1) ** 1.5
		d = rng.uniform(0.3, 1.0)
		out += buzz * (0.3 + 0.7 * click) * (0.08 + 0.92 * sw) * d
	return out


def stream(n, rng):
	"""A brook: babbling water, a bed of moving noise full of small bubbles and gurgles."""
	gurgle = swell(n, rng, 4.0, 0.3, 1.0)
	surge = swell(n, rng, 0.2, 0.6, 1.0)
	out = np.zeros(n)
	rate = 180
	for at in poisson(rng, n, rate):
		f0 = math.exp(rng.uniform(math.log(350), math.log(2600)))
		tau = 0.018 * (600 / f0) ** 0.8 * rng.uniform(0.6, 1.4)
		m = min(int(tau * 6 * SR), int(0.15 * SR))
		amp = rng.exponential(1.0) ** 1.5 * gurgle[at] * surge[at]
		out[np.arange(at, at + m) % n] += ring(m, f0, tau, rng, rise=rng.uniform(0.05, 0.4)) * amp
	out /= np.sqrt(np.mean(out ** 2))
	bed = shaped(n, rng, mul(band(150, 5000, 1), tilt(-2, 800)))
	hiss = shaped(n, rng, band(3000, 9000, 2))
	return out * 0.55 + bed * 0.5 * (0.6 + 0.4 * gurgle) * surge + hiss * 0.12 * surge


def waves(n, rng):
	"""Waves on a shore: the swell gathering, breaking, washing up the sand and drawing back, over the sea's murmur."""
	t = np.arange(n) / SR
	low, mid, high, back = (np.zeros(n) for _ in range(4))
	starts = []
	at = 0.0
	while at < n / SR - 5:
		starts.append(at)
		at += rng.uniform(6.5, 9.0)
	starts = [s * (n / SR) / (at) for s in starts]  # stretch so the last wave's spacing wraps evenly
	for s in starts:
		size = rng.uniform(0.6, 1.0)
		dt = (t - s) % (n / SR)
		dt = np.where(dt > n / SR - 2.0, dt - n / SR, dt)  # the swell begins a little before the break
		low += size * np.exp(-((dt - 0.2) / 1.2) ** 2)
		brk = np.where(dt > 0, 1 - np.exp(-dt / 0.08), 0) * np.exp(-np.maximum(dt, 0) / 1.4)
		mid += size * brk
		wash = np.where(dt > 0.3, np.exp(-(dt - 0.3) / 2.2) * (1 - np.exp(-(dt - 0.3) / 0.5)), 0)
		high += size * wash
		draw = np.exp(-((dt - 3.8) / 1.0) ** 2)
		back += size * draw
	n_low = shaped(n, rng, mul(tilt(-6, 100), band(30, 400, 2)))
	n_mid = shaped(n, rng, mul(tilt(-3, 500), band(150, 3000, 1)))
	n_high = shaped(n, rng, band(1500, 9000, 2))
	n_back = cconv(impulses(n, rng, 600), burst(int(0.006 * SR), rng, 1500, 7000, 0.0015))
	n_back /= np.sqrt(np.mean(n_back ** 2))
	murmur = shaped(n, rng, mul(tilt(-4, 200), band(40, 1200, 1)))
	return n_low * (0.12 + 0.6 * low) + n_mid * 0.9 * mid + n_high * 0.5 * high + n_back * 0.3 * back + murmur * 0.14


def thunder(n, rng, sr=22050):
	"""A distant roll of thunder: no crack, a low rumble rolling in a few bumps and dying away."""
	t = np.arange(n) / sr
	rumble = shaped(n, rng, mul(tilt(-6, 60), band(25, rng.uniform(140, 260), 3)), sr)
	growl = shaped(n, rng, mul(tilt(-4, 200), band(120, 700, 2)), sr)
	envelope = np.zeros(n)
	at = rng.uniform(0.2, 0.6)
	for k in range(rng.integers(3, 7)):
		a, d = rng.uniform(0.15, 0.5), rng.uniform(0.6, 2.0)
		dt = t - at
		envelope += np.where(dt > 0, (1 - np.exp(-dt / a)) * np.exp(-dt / d), 0) * rng.uniform(0.4, 1.0) * (0.85 ** k)
		at += rng.uniform(0.3, 1.3)
	tail = np.exp(-np.maximum(t - at, 0) / 2.0)
	envelope = envelope * tail
	early = np.exp(-t / 2.5)
	y = rumble * envelope + growl * envelope * early * 0.35
	fade = np.minimum(1, np.minimum(t / 0.05, (n / sr - t) / 0.8))
	return y * np.clip(fade, 0, 1)


# ---------------------------------------------------------------------------------------------------------------------
# the catalogue: (name, maker, seconds, seed, level), level ('rms', dBFS) or ('peak', dBFS)

BEDS = [
	('wind_open', wind_open, 20, 101, ('rms', -18)),
	('wind_leaves', wind_leaves, 20, 102, ('rms', -18)),
	('wind_high', wind_high, 22, 103, ('rms', -18)),
	('rain_ground', rain_ground, 16, 104, ('rms', -18)),
	('rain_leaves', rain_leaves, 16, 105, ('rms', -18)),
	('rain_roof', rain_roof, 16, 106, ('rms', -18)),
	('dawn_chorus', dawn_chorus, 24, 107, ('peak', -3)),
	('birds_day', birds_day, 24, 108, ('peak', -3)),
	('crickets', crickets, 16, 109, ('peak', -4)),
	('frogs', frogs, 20, 110, ('peak', -3)),
	('cicadas', cicadas, 20, 111, ('rms', -20)),
	('stream', stream, 16, 112, ('rms', -18)),
	('waves', waves, 24, 113, ('rms', -18)),
]
THUNDER = [(f'roll{i}', 7 + i, 200 + i) for i in range(1, 5)]
PEAK_CEILING = 10 ** (-1.5 / 20)  # nothing goes above -1.5 dBFS: headroom for the encoder's overshoot


def level(y, how):
	kind, db = how
	g = 10 ** (db / 20)
	y = y * (g / (np.sqrt(np.mean(y * y)) if kind == 'rms' else np.abs(y).max()))
	peak = np.abs(y).max()
	return y * (PEAK_CEILING / peak) if peak > PEAK_CEILING else y


# ---------------------------------------------------------------------------------------------------------------------
# writing


_CRC = []
for _i in range(256):
	_r = _i << 24
	for _ in range(8):
		_r = ((_r << 1) ^ 0x04C11DB7) if _r & 0x80000000 else (_r << 1)
	_CRC.append(_r & 0xFFFFFFFF)


def ogg_crc(data):
	crc = 0
	for b in data:
		crc = ((crc << 8) & 0xFFFFFFFF) ^ _CRC[((crc >> 24) & 0xFF) ^ b]
	return crc


def fix_serial(path):
	"""libsndfile picks a random Ogg stream serial; set it to a fixed one (and redo each page's checksum)."""
	data = bytearray(open(path, 'rb').read())
	i = 0
	while i < len(data):
		assert data[i:i + 4] == b'OggS', f'{path}: lost the page boundary at {i}'
		segs = data[i + 26]
		size = 27 + segs + sum(data[i + 27:i + 27 + segs])
		struct.pack_into('<I', data, i + 14, SERIAL)
		struct.pack_into('<I', data, i + 22, 0)
		struct.pack_into('<I', data, i + 22, ogg_crc(bytes(data[i:i + size])))
		i += size
	open(path, 'wb').write(bytes(data))


def write_ogg(path, y, sr):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	sf.write(path, y.astype(np.float32), sr, format='OGG', subtype='VORBIS', compression_level=VORBIS_Q3)
	fix_serial(path)


def generate():
	sounds = {}
	for name, maker, seconds, seed, how in BEDS:
		rng = np.random.default_rng(seed)
		y = level(maker(seconds * SR, rng), how)
		write_ogg(os.path.join(SOUNDS, 'bed', name + '.ogg'), y, SR)
		sounds['bed.' + name] = {'sounds': [{'name': 'gloam:bed/' + name}]}
		print(f'  bed/{name}.ogg  {seconds} s')
	rolls = []
	for name, seconds, seed in THUNDER:
		rng = np.random.default_rng(seed)
		y = level(thunder(seconds * 22050, rng), ('peak', -2))
		write_ogg(os.path.join(SOUNDS, 'thunder', name + '.ogg'), y, 22050)
		rolls.append({'name': 'gloam:thunder/' + name})
		print(f'  thunder/{name}.ogg  {seconds} s')
	sounds['thunder'] = {'sounds': rolls}
	with open(os.path.join(ASSETS, 'sounds.json'), 'w', newline='\n') as f:
		json.dump(sounds, f, indent='\t')
		f.write('\n')


# ---------------------------------------------------------------------------------------------------------------------
# checking


def spectrogram(y, sr, path, title):
	from PIL import Image, ImageDraw
	win = 1024
	hop = max(1, len(y) // 1200)
	frames = np.lib.stride_tricks.sliding_window_view(np.concatenate([y, y[:win]]), win)[::hop][:1200]
	mag = np.abs(np.fft.rfft(frames * np.hanning(win), axis=1)).T  # (freq, time)
	db = 20 * np.log10(mag + 1e-9)
	db = np.clip((db - (db.max() - 60)) / 60, 0, 1)
	img = (255 * db[::-1]).astype(np.uint8)  # high frequencies at the top
	h = 400
	im = Image.fromarray(img).resize((img.shape[1], h))
	im = im.convert('RGB')
	draw = ImageDraw.Draw(im)
	# the loudness over time, in green: 0 dBFS at the top, -60 at the bottom
	level = 20 * np.log10(np.sqrt(np.mean(frames ** 2, axis=1)) + 1e-9)
	pts = [(x, int(h * min(1, max(0, -v / 60)))) for x, v in enumerate(level)]
	draw.line(pts, fill=(60, 255, 60))
	for khz in range(2, int(sr / 2000) + 1, 2):
		yy = int(h - h * (khz * 1000) / (sr / 2))
		draw.line([(0, yy), (12, yy)], fill=(255, 80, 80))
		draw.text((14, yy - 6), f'{khz}k', fill=(255, 80, 80))
	draw.text((40, 4), title, fill=(255, 255, 0))
	im.save(path)


def check(png_dir):
	total = 0
	ok = True
	files = [('bed', b[0], b[2]) for b in BEDS] + [('thunder', t[0], t[1]) for t in THUNDER]
	for folder, name, seconds in files:
		path = os.path.join(SOUNDS, folder, name + '.ogg')
		size = os.path.getsize(path)
		total += size
		y, sr = sf.read(path, dtype='float64')
		length = len(y) / sr
		peak = np.abs(y).max()
		rms = np.sqrt(np.mean(y * y))
		line = f'{folder}/{name:12s} {size / 1024:6.1f} KB  {sr} Hz  {length:5.2f} s  peak {20 * np.log10(peak):6.1f} dB  rms {20 * np.log10(rms):6.1f} dB'
		if abs(length - seconds) > 0.01:
			line += '  LENGTH WRONG'
			ok = False
		if peak >= 0.999:
			line += '  CLIPS'
			ok = False
		if folder == 'bed':
			d = np.abs(np.diff(y))
			seam = abs(y[0] - y[-1])
			p999 = np.quantile(d, 0.999)
			# the level either side of the seam against the level anywhere else (a dip or bump would show)
			w = int(0.05 * sr)
			around = np.sqrt(np.mean(np.concatenate([y[-w:], y[:w]]) ** 2))
			blocks = np.sqrt(np.mean(y[: len(y) // (2 * w) * 2 * w].reshape(-1, 2 * w) ** 2, axis=1))
			lo, hi = np.quantile(blocks, 0.02), np.quantile(blocks, 0.98)
			line += f'  seam step {seam:.4f} (p99.9 step {p999:.4f})  seam level {around:.4f} in [{lo:.4f}, {hi:.4f}]'
			if seam > p999 * 1.5 or not (lo * 0.7 <= around <= hi * 1.3):
				line += '  SEAM?'
				ok = False
		print(line)
		if png_dir:
			os.makedirs(png_dir, exist_ok=True)
			spectrogram(y, sr, os.path.join(png_dir, f'{folder}_{name}.png'), f'{folder}/{name}')
	print(f'total {total / 1024 / 1024:.2f} MB, {"all good" if ok else "PROBLEMS"}')
	return ok


if __name__ == '__main__':
	parser = argparse.ArgumentParser()
	parser.add_argument('--check', action='store_true')
	parser.add_argument('--png')
	args = parser.parse_args()
	if args.check:
		sys.exit(0 if check(args.png) else 1)
	generate()
