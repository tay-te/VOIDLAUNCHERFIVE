"""Small DSP kit for the card-draft sounds (docs/SOUNDS.md). numpy only: no scipy on this machine.

Everything is mono float64 at SR. Static filters work in the frequency domain (zero phase, fast); filters whose cutoff
moves over time use a state-variable filter run sample by sample.
"""
import math

import numpy as np

SR = 48000


def n_of(seconds):
    return int(round(seconds * SR))


def times(n):
    return np.arange(n) / SR


def pad_to(x, n):
    if len(x) >= n:
        return x[:n]
    return np.concatenate([x, np.zeros(n - len(x))])


def add_at(dst, src, at_seconds, gain=1.0):
    """Mixes src into dst starting at a time; whatever runs past the end is dropped."""
    i = n_of(at_seconds)
    if i >= len(dst) or i + len(src) <= 0:
        return dst
    s0 = max(0, -i)
    i = max(0, i)
    m = min(len(dst) - i, len(src) - s0)
    dst[i:i + m] += gain * src[s0:s0 + m]
    return dst


def add_circular(dst, src, at_index, gain=1.0):
    """Mixes src into dst wrapping round the end, for seamless loops."""
    n = len(dst)
    idx = (at_index + np.arange(len(src))) % n
    np.add.at(dst, idx, gain * src)
    return dst


# ---------------------------------------------------------------- filters

def spectral(x, response):
    """Zero-phase static filter: response(freqs_hz) -> gain per bin."""
    n = len(x)
    size = 1 << (int(math.ceil(math.log2(max(n, 2)))) + 1)
    spec = np.fft.rfft(x, size)
    f = np.fft.rfftfreq(size, 1 / SR)
    spec *= response(f)
    return np.fft.irfft(spec, size)[:n]


def lowpass(x, fc, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (f / fc) ** (2 * order)))


def highpass(x, fc, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-3)) ** (2 * order)))


def bandpass(x, lo, hi, order=2):
    return highpass(lowpass(x, hi, order), lo, order)


def svf(x, fc, q=0.7071, mode="lp"):
    """Topology-preserving state-variable filter; fc may be an array (one cutoff per sample)."""
    n = len(x)
    fc = np.broadcast_to(np.asarray(fc, dtype=float), (n,))
    g = np.tan(np.pi * np.clip(fc, 10, SR * 0.45) / SR)
    k = 1.0 / q
    a1 = 1.0 / (1.0 + g * (g + k))
    a2 = g * a1
    a3 = g * a2
    out = np.empty(n)
    ic1 = ic2 = 0.0
    xs = x.tolist()
    a1l, a2l, a3l = a1.tolist(), a2.tolist(), a3.tolist()
    want = {"lp": 0, "bp": 1, "hp": 2}[mode]
    for i in range(n):
        v0 = xs[i]
        v3 = v0 - ic2
        v1 = a1l[i] * ic1 + a2l[i] * v3
        v2 = ic2 + a2l[i] * ic1 + a3l[i] * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        if want == 0:
            out[i] = v2
        elif want == 1:
            out[i] = v1
        else:
            out[i] = v0 - k * v1 - v2
    return out


# ---------------------------------------------------------------- sources and envelopes

def noise(rng, n):
    return rng.standard_normal(n)


def brown(rng, n, leak=0.995):
    w = rng.standard_normal(n)
    out = np.empty(n)
    acc = 0.0
    wl = w.tolist()
    for i in range(n):
        acc = acc * leak + wl[i]
        out[i] = acc
    out -= out.mean()
    return out / (np.abs(out).max() + 1e-9)


def smooth_random(rng, n, rate_hz):
    """A random wobble with about rate_hz changes a second, -1..1."""
    pts = max(2, int(n / SR * rate_hz) + 2)
    v = rng.uniform(-1, 1, pts)
    xp = np.linspace(0, n, pts)
    # cosine interpolation between the points
    i = np.arange(n)
    j = np.clip(np.searchsorted(xp, i, side="right") - 1, 0, pts - 2)
    t = (i - xp[j]) / (xp[j + 1] - xp[j])
    t = (1 - np.cos(np.pi * t)) / 2
    return v[j] * (1 - t) + v[j + 1] * t


def decay(n, tau, attack=0.002):
    t = times(n)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-t / tau)


def fades(x, fade_in=0.003, fade_out=0.01):
    x = x.copy()
    i = min(len(x), n_of(fade_in))
    o = min(len(x), n_of(fade_out))
    if i > 0:
        x[:i] *= np.sin(np.linspace(0, np.pi / 2, i)) ** 2
    if o > 0:
        x[-o:] *= np.cos(np.linspace(0, np.pi / 2, o)) ** 2
    return x


def sine(freq, n, phase=0.0):
    """freq may be an array (a glide); the phase is integrated."""
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    ph = phase + 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph)


def semi(n):
    return 2 ** (n / 12)


# ---------------------------------------------------------------- building blocks

def pop(rng, bright=1.0, size=1.0, dark=False):
    """One ember pop: a short noise burst plus a damped ring; dark pops thock lower (ash, wood)."""
    length = n_of(rng.uniform(0.008, 0.02) * (1.3 if dark else 1.0))
    t = times(length)
    burst_len = rng.uniform(0.0006, 0.0025) * size
    burst = lowpass(rng.standard_normal(length), 3200 * bright, 1) * np.exp(-t / burst_len)
    if dark:
        f = rng.uniform(350, 1100)
    else:
        f = rng.uniform(1000, 3800) * bright
    ring = np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * np.exp(-t / rng.uniform(0.0008, 0.003))
    x = 0.65 * burst + 0.55 * ring
    if dark:
        x = lowpass(x, 2500, 1)
    return x * rng.uniform(0.6, 1.0)


def crackle(rng, n, rate, start_rate=None, bright=1.0, dark_share=0.15, level_curve=None, first_at=None):
    """Ember crackle: pops at random times. rate (pops/s) may fall from start_rate to rate over the length;
    sizes follow a power law (many small, a few big snaps)."""
    out = np.zeros(n)
    dur = n / SR
    r0 = start_rate if start_rate is not None else rate
    t = 0.0 if first_at is None else first_at
    first = first_at is not None
    while t < dur:
        frac = t / dur
        level = 1.0 if level_curve is None else float(level_curve(frac))
        amp = min(1.0, (rng.pareto(2.2) + 0.25) * 0.45) * level
        if first:
            amp = max(amp, 0.8 * level)
            first = False
        p = pop(rng, bright=bright, size=1 + amp, dark=rng.random() < dark_share)
        add_at(out, p * amp, t)
        r = r0 + (rate - r0) * frac
        t += rng.exponential(1.0 / max(r, 0.5))
    return out


def hiss(rng, n, lo=2500, hi=6500):
    return bandpass(noise(rng, n), lo, hi, 2)


def flicker(rng, n, rate=12, depth=0.4):
    return 1 + depth * smooth_random(rng, n, rate)


def warm(x, drive=1.25, cutoff=11000):
    """The slightly lo-fi finish every synth layer gets: soft saturation, then the very top rolled off."""
    y = np.tanh(drive * x) / np.tanh(drive)
    return lowpass(y, cutoff, 1)


def normalise(x, peak=0.89):
    m = np.abs(x).max()
    return x if m < 1e-9 else x * (peak / m)


def bell(freq, n, partials=((1, 1, 1.0), (2.76, 0.45, 0.4), (5.4, 0.25, 0.18), (8.93, 0.12, 0.1)), tau=0.5, rng=None):
    """An inharmonic struck bell: (ratio, amplitude, decay share of tau) per partial."""
    t = times(n)
    out = np.zeros(n)
    for ratio, amp, share in partials:
        ph = 0 if rng is None else rng.uniform(0, 6.28)
        out += amp * np.sin(2 * np.pi * freq * ratio * t + ph) * np.exp(-t / (tau * share))
    a = np.clip(t / 0.0015, 0, 1)
    return out * a


def ping(freq, n, tau, attack=0.002):
    t = times(n)
    return np.sin(2 * np.pi * freq * t) * np.clip(t / attack, 0, 1) * np.exp(-t / tau)
