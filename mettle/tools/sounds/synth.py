"""Synthesises Mettle's own card-draft sound layers (docs/SOUNDS.md) as mono Ogg Vorbis files.

    python tools/sounds/synth.py            # from mettle/: writes src/main/resources/assets/mettle/sounds/{card,mythic}/

Deterministic: each file has its own seed, so a rerun rewrites the same sounds. Only these synthesised layers ship as
audio; the vanilla layers are referenced by name from assets/mettle/sounds.json and are never copied.
"""
import os
import sys

import numpy as np
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dsp import (SR, add_at, add_circular, bandpass, bell, brown, crackle, decay, fades, flicker, highpass, hiss,
                 lowpass, n_of, noise, normalise, ping, pop, semi, sine, smooth_random, svf, times, warm)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "mettle", "sounds"))

# Every chord is in F#, the note block's home pitch, so the synth layers sit with the note-block chimes Mettle's
# moments already use (MomentSounds).
FS3, AS3, CS4, FS4 = 185.0, 233.08, 277.18, 369.99
FS5, AS5, CS6, FS6 = 739.99, 932.33, 1108.73, 1479.98
PENTA_HIGH = [1479.98, 1661.22, 1864.66, 2217.46, 2489.02, 2959.96, 3322.44, 3729.31, 4434.92]


def rng_for(name):
    return np.random.default_rng(abs(hash_name(name)))


def hash_name(name):
    h = 2166136261
    for c in name.encode():
        h = ((h ^ c) * 16777619) & 0xFFFFFFFF
    return h


# ---------------------------------------------------------------- the card layers

def slide(rng, v):
    """A card dealt across the table: a quick papery swish that lands with a soft tap."""
    dur = 0.17 + 0.03 * v
    n = n_of(dur)
    t = times(n)
    src = noise(rng, n)
    grain = 0.7 + 0.3 * smooth_random(rng, n, 280)
    peak = 0.32 + 0.06 * rng.uniform(-1, 1)
    f0, f1, f2 = 1500 * rng.uniform(0.9, 1.1), 3600 * rng.uniform(0.9, 1.1), 2300
    fc = np.where(t < peak * dur, f0 + (f1 - f0) * (t / (peak * dur)), f1 + (f2 - f1) * ((t - peak * dur) / ((1 - peak) * dur)))
    body = svf(src * grain, fc, q=0.9, mode="bp")
    env = np.where(t < peak * dur, (t / (peak * dur)) ** 1.6, np.exp(-(t - peak * dur) / (0.055 + 0.01 * v)))
    x = lowpass(body, 6500, 2) * env
    # felt underneath
    x += 0.35 * lowpass(noise(rng, n), 900, 2) * env
    # the landing tap
    tn = n_of(0.03)
    tt = times(tn)
    tap = 0.6 * np.sin(2 * np.pi * rng.uniform(700, 950) * tt) * np.exp(-tt / 0.006)
    tap += 0.5 * lowpass(noise(rng, tn), 3200, 2) * np.exp(-tt / 0.003)
    add_at(x, tap * 0.55, dur - 0.035)
    return fades(x, 0.001, 0.02)


def riffle(rng, v):
    """The deck riffled back together: a stream of card-edge clicks that speeds up, then squares off."""
    dur = 0.55
    n = n_of(dur)
    x = np.zeros(n)
    t = 0.02
    count = 0
    while t < 0.44:
        frac = t / 0.44
        interval = 0.03 - 0.018 * np.sin(np.pi * frac) + rng.uniform(-0.003, 0.003)
        cn = n_of(0.006)
        ct = times(cn)
        click = bandpass(noise(rng, cn), 1800, 6000, 2) * np.exp(-ct / 0.0012)
        click += 0.5 * np.sin(2 * np.pi * rng.uniform(2500, 4200) * ct) * np.exp(-ct / 0.0015)
        amp = (0.55 + 0.45 * np.sin(np.pi * frac)) * rng.uniform(0.6, 1.0)
        add_at(x, click * amp, t)
        t += max(0.008, interval)
        count += 1
    tt = times(n)
    swish = bandpass(noise(rng, n), 1500, 4500, 2) * 0.22 * np.clip(np.sin(np.pi * np.clip(tt / 0.46, 0, 1)), 0, 1)
    x += swish
    # squared off on the table
    sn = n_of(0.06)
    st = times(sn)
    settle = 0.5 * np.sin(2 * np.pi * 190 * st) * np.exp(-st / 0.018) + 0.4 * lowpass(noise(rng, sn), 1500, 2) * np.exp(-st / 0.01)
    add_at(x, settle, 0.47 + 0.01 * v)
    return fades(x, 0.002, 0.03)


def whoosh(rng, v):
    """Air as the hand is swept off the table."""
    dur = 0.38 + 0.04 * v
    n = n_of(dur)
    t = times(n)
    peak = 0.55
    u = t / dur
    fc = np.where(u < peak, 380 * (2300 / 380) ** (u / peak), 2300 * (850 / 2300) ** ((u - peak) / (1 - peak)))
    src = noise(rng, n) * (1 + 0.15 * np.sin(2 * np.pi * 28 * t))
    body = svf(src, fc, q=1.1, mode="bp")
    env = np.sin(np.pi * np.clip(u / (2 * peak), 0, 0.5)) ** 2
    env = np.where(u < peak, env, np.cos(np.pi / 2 * np.clip((u - peak) / (1 - peak), 0, 1)) ** 1.5)
    x = body * env + 0.25 * lowpass(noise(rng, n), 500, 2) * env
    return fades(x, 0.004, 0.03)


def flare(rng, v):
    """The kept card flares: a soft ignition fwoomp with a low thump and a few sparks."""
    dur = 0.5
    n = n_of(dur)
    t = times(n)
    fc = np.where(t < 0.06, 220 + (2600 - 220) * (t / 0.06), 2600 * (700 / 2600) ** ((t - 0.06) / (dur - 0.06)))
    body = svf(noise(rng, n), fc * rng.uniform(0.9, 1.1), q=0.8, mode="lp") * decay(n, 0.16 + 0.02 * v, 0.012)
    thump = np.tanh(2.2 * sine(np.linspace(92, 46, n), n)) * decay(n, 0.07, 0.003) * 0.55
    top = highpass(noise(rng, n), 5000, 2) * decay(n, 0.08, 0.004) * 0.15
    sparks = crackle(rng, n, 10, start_rate=70, bright=1.1, level_curve=lambda f: 1 - 0.8 * f, first_at=0.02) * 0.35
    x = 0.9 * body + thump + top + sparks
    return fades(x, 0.001, 0.05)


def crackle_hover(rng, v):
    """The hover crackle: the card's burning edge catches. Starts at once (it answers a mouse move)."""
    dur = 0.16 + 0.03 * (v % 4)
    n = n_of(dur)
    x = crackle(rng, n, 22 + 6 * (v % 3), start_rate=55, bright=1.0 + 0.05 * (v % 3), dark_share=0.2,
                level_curve=lambda f: 1 - 0.6 * f, first_at=0.003 + 0.002 * (v % 2))
    x += hiss(rng, n, 2000, 5000) * decay(n, 0.07, 0.006) * 0.1
    x += lowpass(noise(rng, n), 650, 2) * decay(n, 0.04, 0.004) * 0.22
    return fades(x, 0.0005, 0.03)


def sizzle(rng, v):
    """An ember bed glowing hotter under the rarer cards: a breathy fsss with fine crackle."""
    dur = 0.45
    n = n_of(dur)
    env = decay(n, 0.2, 0.08)
    x = hiss(rng, n, 2500, 6500) * flicker(rng, n, 20, 0.45) * env * 0.5
    x += lowpass(noise(rng, n), 800, 2) * env * 0.3
    x += crackle(rng, n, 30, start_rate=45, bright=1.2, level_curve=lambda f: 1 - 0.7 * f) * 0.4
    return fades(x, 0.004, 0.06)


def burn(rng, v):
    """An unkept card burns away: it catches, curls, hisses, and thins to a few ashy ticks."""
    dur = 1.1
    n = n_of(dur)
    t = times(n)
    hiss_env = decay(n, 0.33 + 0.04 * v, 0.03)
    x = hiss(rng, n, 2000, 6500) * flicker(rng, n, 12, 0.4) * hiss_env * 0.45
    x += lowpass(noise(rng, n), 520, 2) * decay(n, 0.24, 0.02) * flicker(rng, n, 7, 0.5) * 0.4
    x += crackle(rng, n, 7, start_rate=95, bright=1.05, dark_share=0.25, level_curve=lambda f: 1 - 0.75 * f) * 0.55
    # the card curling: a short dense crinkle
    cn = n_of(0.16)
    curl = crackle(rng, cn, 320, bright=1.4, dark_share=0.0) * np.sin(np.pi * times(cn) / 0.16) * 0.25
    add_at(x, curl, 0.18 + 0.05 * v)
    # the last of it: ashy ticks, darker
    an = n_of(0.45)
    ash_ticks = crackle(rng, an, 9, bright=0.6, dark_share=0.7, level_curve=lambda f: 0.5 * (1 - f)) * 0.4
    add_at(x, ash_ticks, 0.62)
    return fades(x, 0.002, 0.12)


def ash(rng, v):
    """Ash settling: a soft puff and a few crumbling grains."""
    dur = 0.55
    n = n_of(dur)
    x = lowpass(noise(rng, n), 320, 2) * decay(n, 0.05, 0.01) * 0.55
    t = 0.0
    while t < dur - 0.02:
        gn = n_of(rng.uniform(0.004, 0.012))
        g = lowpass(noise(rng, gn), rng.uniform(1200, 3000), 2) * np.hanning(gn)
        add_at(x, g * rng.uniform(0.25, 0.7) * (1 - t / dur) ** 1.5, t)
        t += rng.exponential(0.012 + 0.05 * t)
    return fades(x, 0.003, 0.08)


def wrong(rng, v):
    """The cursed card's hover: a low, beating, slightly sinking tone with a breath and a crackle that runs
    backwards, so it swells into its pops instead of out of them."""
    dur = 0.9
    n = n_of(dur)
    t = times(n)
    f0 = 55 * (1 - 0.05 * t / dur) * (1 + 0.02 * v)
    tone = (sine(f0, n) + 0.9 * sine(f0 * 1.06, n) + 0.5 * sine(f0 * 1.414, n) + 0.35 * sine(f0 * 2.02, n)
            + 0.18 * sine(f0 * 2.83, n))
    tone = lowpass(np.tanh(2.2 * tone / 2.0), 1300, 2)
    breath = bandpass(noise(rng, n), 230, 460, 2) * (0.6 + 0.4 * smooth_random(rng, n, 3)) * 0.5
    back = crackle(rng, n, 16, bright=0.8, dark_share=0.4, level_curve=lambda f: f)[::-1] * 0.3
    env = np.clip(t / 0.25, 0, 1) ** 1.5 * np.clip((dur - t) / 0.3, 0, 1)
    x = (0.8 * tone + breath + back) * env
    return fades(x, 0.004, 0.03)


def gold(rng, v):
    """An earned card: a small bright bell glint over a warm body and a little glitter."""
    dur = 0.75
    n = n_of(dur)
    x = bell(2217.46 * (1 + 0.003 * v), n, tau=0.42, rng=rng) * 0.6
    x = add_at(x, bell(2959.96, n, tau=0.35, rng=rng) * 0.35, 0.07)
    x += ping(554.37, n, 0.25, 0.004) * 0.25
    t = 0.01
    while t < 0.55:
        pn = n_of(0.02)
        add_at(x, ping(rng.uniform(5000, 9000), pn, rng.uniform(0.002, 0.006)) * rng.uniform(0.08, 0.2) * (1 - t / 0.6), t)
        t += rng.exponential(0.045)
    return fades(x, 0.0005, 0.08)


def glow(rng, v):
    """A warm pad swell under a chord: a deck slot or a set completed."""
    dur = 1.3
    n = n_of(dur)
    t = times(n)
    x = np.zeros(n)
    for f in (FS3, AS3, CS4, FS4):
        for det in (-0.0023, 0.0023):
            for h in range(1, 9):
                x += np.sin(2 * np.pi * f * (1 + det) * h * t + rng.uniform(0, 6.28)) / h ** 1.6
    fc = 600 + 2200 * np.clip(t / 0.5, 0, 1) - 1200 * np.clip((t - 0.5) / 0.8, 0, 1)
    x = svf(x / 12, fc, q=0.7, mode="lp")
    env = np.sin(np.pi / 2 * np.clip(t / 0.45, 0, 1)) * np.clip((dur - t) / 0.85, 0, 1) ** 1.3
    return fades(x * env, 0.005, 0.05)


# ---------------------------------------------------------------- the Mythic reveal

def rumble(rng, v):
    """Darkness: the face-down card trembles. A low shaking rumble that builds for 2.5 s and stops on the burst."""
    dur = 2.5
    n = n_of(dur)
    t = times(n)
    grow = np.clip(t / 2.3, 0, 1) ** 1.3 * 0.7 + 0.3 * np.clip(t / 0.4, 0, 1)
    rate = 8 + 7 * t / dur
    trem = np.sin(2 * np.pi * np.cumsum(rate) / SR)
    trem = 1 + 0.55 * (0.6 * trem + 0.4 * smooth_random(rng, n, 14))
    sub = np.tanh(2.5 * (sine(42, n) + 0.4 * sine(63, n)) * 0.6) * trem
    low = lowpass(brown(rng, n), 160, 2) * 2.0 * trem
    body = lowpass(sub * 0.8 + low * 0.5, 420, 2)
    # the card rattling on the table, in time with the shaking
    rattle = np.zeros(n)
    phase = np.cumsum(rate) / SR
    ticks = np.nonzero(np.diff(np.floor(phase)) > 0)[0]
    for i in ticks:
        if rng.random() < 0.85:
            tn = n_of(0.004)
            tk = bandpass(noise(rng, tn), 1100, 2600, 2) * np.exp(-times(tn) / 0.0011)
            rattle = add_at(rattle, tk * rng.uniform(0.5, 1.0), i / SR)
    x = body * grow + rattle * 0.35 * grow ** 1.4
    x *= np.clip((dur - t) / 0.12, 0, 1)
    return fades(x, 0.02, 0.01)


def crack(rng, v):
    """A crack of light through the card: a sharp tick, a falling zip and glassy rings, then a little debris."""
    dur = 0.4
    n = n_of(dur)
    t = times(n)
    x = np.zeros(n)
    x[0:n_of(0.0008)] += np.hanning(n_of(0.0008)) * 0.9
    x += highpass(noise(rng, n), 1000, 2) * np.exp(-t / 0.0016) * 0.7
    zn = n_of(0.03)
    zt = times(zn)
    zip_ = np.sin(2 * np.pi * np.cumsum(np.linspace(7200, 2400, zn)) / SR) * np.exp(-zt / 0.012) * 0.35
    add_at(x, zip_, 0.001)
    for _ in range(5):
        x += ping(rng.uniform(2600, 7600), n, rng.uniform(0.04, 0.22), 0.0008) * rng.uniform(0.15, 0.32)
    x += np.sin(2 * np.pi * 140 * t) * np.exp(-t / 0.03) * 0.3
    deb = crackle(rng, n_of(0.25), 30, bright=1.3, dark_share=0.1, level_curve=lambda f: 0.6 * (1 - f)) * 0.35
    add_at(x, deb, 0.012)
    return fades(x, 0.0, 0.06)


def swell(rng, v):
    """The light rising behind the cracks: a reversed-cymbal swell and a chord that climbs into the burst."""
    dur = 1.0
    n = n_of(dur)
    t = times(n)
    u = t / dur
    fc = 800 * (14000 / 800) ** u
    air = svf(noise(rng, n), fc, q=0.7, mode="lp") * u ** 3 * 0.6
    glide = 1 + (semi(0.5) - 1) * u
    chord = np.zeros(n)
    for f, a in ((FS4, 1.0), (466.16, 0.8), (554.37, 0.8), (FS5, 0.6), (CS6, 0.35)):
        vib = 1 + 0.004 * u * np.sin(2 * np.pi * 5.5 * t)
        chord += a * sine(f * glide * vib, n, rng.uniform(0, 6.28))
    chord = chord / 3.5 * u ** 2
    sub = sine(np.linspace(40, 55, n), n) * u ** 2 * 0.35
    x = air + chord + sub
    x *= np.clip((dur - t) / 0.012, 0, 1)
    return fades(x, 0.01, 0.0)


def burst(rng, v):
    """The flip: a big warm boom and a bright chord bloom, kept round rather than loud."""
    dur = 1.5
    n = n_of(dur)
    t = times(n)
    boom = np.tanh(2.0 * sine(62 * (36 / 62) ** np.clip(t / 0.3, 0, 1), n)) * decay(n, 0.42, 0.002) * 0.8
    fc = 3000 * (400 / 3000) ** np.clip(t / 0.4, 0, 1)
    whoomp = svf(noise(rng, n), fc, q=0.8, mode="lp") * decay(n, 0.18, 0.004) * 0.7
    spark = highpass(noise(rng, n), 5000, 2) * decay(n, 0.09, 0.002) * 0.3
    bloom = np.zeros(n)
    for f, a, tau in ((FS4, 1.0, 1.2), (466.16, 0.7, 1.0), (554.37, 0.7, 1.0), (FS5, 0.6, 0.9), (AS5, 0.4, 0.7), (CS6, 0.35, 0.6)):
        bloom += a * (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * f * 2.01 * t) + 0.12 * np.sin(2 * np.pi * f * 3 * t)) * decay(n, tau, 0.006)
    bloom = bloom / 4.0 * 0.6
    sparks = crackle(rng, n, 6, start_rate=200, bright=1.2, level_curve=lambda f: np.exp(-f * 6)) * 0.4
    x = boom + whoomp + spark + bloom + sparks
    return fades(x, 0.0, 0.2)


def tail(rng, v):
    """After the burst: high sparkles in F# pentatonic falling away over a fading shimmer, and a few embers."""
    dur = 2.8
    n = n_of(dur)
    t = times(n)
    x = np.zeros(n)
    for _ in range(46):
        at = min(rng.exponential(0.7), dur - 0.4)
        f = PENTA_HIGH[rng.integers(len(PENTA_HIGH))] * (1 + rng.uniform(-0.002, 0.002))
        pn = n_of(0.6)
        add_at(x, ping(f, pn, rng.uniform(0.12, 0.45)) * rng.uniform(0.25, 0.6) * (1 - at / dur) ** 1.5, at)
    pad = (sine(FS5, n) + 0.8 * sine(AS5, n) + 0.7 * sine(CS6, n)) * (1 + 0.3 * np.sin(2 * np.pi * 6 * t))
    x += pad * 0.12 * np.clip(1 - t / dur, 0, 1) ** 1.6
    x += crackle(rng, n, 2, start_rate=20, bright=0.9, level_curve=lambda f: 0.4 * (1 - f)) * 0.3
    return fades(x, 0.01, 0.3)


def mythic_idle(rng, v):
    """The Mythic card's idle shimmer while hovered: a seamless 4 s loop. Every frequency and wobble fits a whole
    number of cycles into the loop, and sparkles and embers wrap round its end."""
    dur = 4.0
    n = n_of(dur)
    t = times(n)
    x = np.zeros(n)
    for f, a, cycles in ((740.0, 0.9, 3), (740.75, 0.7, 5), (1109.0, 0.6, 7), (1480.0, 0.45, 2), (1865.0, 0.3, 9)):
        lfo = 0.65 + 0.35 * np.sin(2 * np.pi * cycles / dur * t + rng.uniform(0, 6.28))
        x += a * np.sin(2 * np.pi * f * t) * lfo
    x *= 0.18
    for _ in range(14):
        at = int(rng.integers(n))
        f = PENTA_HIGH[rng.integers(len(PENTA_HIGH))]
        pn = n_of(0.5)
        add_circular(x, ping(f, pn, rng.uniform(0.08, 0.25)) * rng.uniform(0.12, 0.3), at)
    for _ in range(36):
        at = int(rng.integers(n))
        p = pop(rng, bright=1.1, dark=rng.random() < 0.3)
        add_circular(x, p * rng.uniform(0.05, 0.18), at)
    return x


# name -> (function, variants)
SOUNDS = {
    "card/slide": (slide, 4),
    "card/riffle": (riffle, 2),
    "card/whoosh": (whoosh, 2),
    "card/flare": (flare, 3),
    "card/crackle": (crackle_hover, 6),
    "card/sizzle": (sizzle, 2),
    "card/burn": (burn, 3),
    "card/ash": (ash, 3),
    "card/wrong": (wrong, 2),
    "card/gold": (gold, 2),
    "card/glow": (glow, 1),
    "mythic/rumble": (rumble, 1),
    "mythic/crack": (crack, 3),
    "mythic/swell": (swell, 1),
    "mythic/burst": (burst, 1),
    "mythic/tail": (tail, 1),
    "mythic/idle": (mythic_idle, 1),
}


# Peak level each layer is mastered to. Vanilla's sound files sit quiet (a book page peaks near 0.5, a campfire
# crackle near 0.2), so the synth layers do too, and cue volumes mean the same thing for both kinds of layer.
LEVEL = {"card/wrong": 0.22, "card/glow": 0.22, "mythic/idle": 0.2, "mythic/rumble": 0.4, "mythic/burst": 0.5}
DEFAULT_LEVEL = 0.3


def render(name, variant):
    fn, _ = SOUNDS[name]
    rng = rng_for(f"{name}{variant}")
    x = fn(rng, variant - 1)
    x = normalise(x, 0.95)
    if name == "mythic/idle":
        # The same finish as warm(), but filtered circularly so the loop's seam stays seamless.
        x = np.tanh(1.25 * x) / np.tanh(1.25)
        spec = np.fft.rfft(x)
        f = np.fft.rfftfreq(len(x), 1 / SR)
        x = np.fft.irfft(spec / np.sqrt(1 + (f / 11000) ** 2), len(x))
        x -= x.mean()
    else:
        x = warm(x, 1.25, 11000)
    return normalise(x, LEVEL.get(name, DEFAULT_LEVEL))


def file_name(name, variant, count):
    return f"{name}{variant}" if count > 1 else name


def main():
    written = []
    for name, (fn, count) in SOUNDS.items():
        family = [render(name, v) for v in range(1, count + 1)]
        # Variants should differ in detail, not in level: bring each to the family's median loudness (RMS), never past
        # a 0.95 peak.
        rms = [np.sqrt(np.mean(x ** 2)) for x in family]
        target = float(np.median(rms))
        family = [x * min(target / r, 0.95 / np.abs(x).max()) for x, r in zip(family, rms)]
        for v, x in enumerate(family, start=1):
            rel = file_name(name, v, count) + ".ogg"
            path = os.path.join(OUT, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            sf.write(path, x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
            written.append((rel, len(x) / SR))
    for rel, secs in written:
        print(f"{rel:28s} {secs:5.2f} s")
    print(f"{len(written)} files in {OUT}")


if __name__ == "__main__":
    main()
