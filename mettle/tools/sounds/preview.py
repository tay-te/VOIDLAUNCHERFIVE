"""Renders the card-draft sound cues to .wav previews, the way the game mixes them (docs/SOUNDS.md).

    python tools/sounds/preview.py [out_dir]     # from mettle/; default ../sound-previews (not committed)

It reads the same files the game does: assets/mettle/card_cues.json (the cues), assets/mettle/sounds.json (Mettle's
events) and Minecraft's own sounds.json and sound files from Loom's asset cache (vanilla layers are decoded for the
preview only, never copied into the mod). Each layer is placed like client.draft.CardAudio plays it: a weighted random
variant, pitch = cue pitch x jitter x the variant's pitch (clamped 0.5-2, played as a speed change like OpenAL),
volume = cue volume x the variant's volume (clamped to 1). The mix is not normalised, so loudness is as in game at
100 % master and UI volume.
"""
import json
import math
import os
import sys

import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
METTLE = os.path.normpath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(METTLE, "src", "main", "resources", "assets", "mettle")
LOOM = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "fabric-loom", "assets")
SR = 48000


def _index():
    best = None
    for f in os.listdir(os.path.join(LOOM, "indexes")):
        if f.startswith("26.3"):
            best = f
    if best is None:
        raise SystemExit("no 26.3 asset index under " + LOOM + " (run a Mettle build once so Loom fetches it)")
    return json.load(open(os.path.join(LOOM, "indexes", best)))["objects"]


INDEX = _index()


def vanilla_path(key):
    h = INDEX[key]["hash"]
    return os.path.join(LOOM, "objects", h[:2], h)


CUES = json.load(open(os.path.join(ASSETS, "card_cues.json"), encoding="utf-8"))
METTLE_EVENTS = json.load(open(os.path.join(ASSETS, "sounds.json"), encoding="utf-8"))
VANILLA_EVENTS = json.load(open(vanilla_path("minecraft/sounds.json"), encoding="utf-8"))

_cache = {}


def load(sound_name):
    """Decodes a sound file (namespace:path) to mono at SR."""
    if sound_name in _cache:
        return _cache[sound_name]
    ns, path = sound_name.split(":", 1) if ":" in sound_name else ("minecraft", sound_name)
    if ns == "mettle":
        file = os.path.join(ASSETS, "sounds", *path.split("/")) + ".ogg"
    else:
        file = vanilla_path(f"{ns}/sounds/{path}.ogg")
    x, sr = sf.read(file, always_2d=True)
    x = x.mean(axis=1)
    _cache[sound_name] = (x, sr)
    return x, sr


def entries(event):
    ns, name = event.split(":", 1) if ":" in event else ("mettle", event)
    table = METTLE_EVENTS if ns == "mettle" else VANILLA_EVENTS
    out = []
    for s in table[name]["sounds"]:
        if isinstance(s, str):
            s = {"name": s}
        if s.get("type") == "event":
            out.extend(entries(s["name"]))
            continue
        nm = s["name"] if ":" in s["name"] else f"{ns}:{s['name']}"
        out.append((nm, s.get("volume", 1.0), s.get("pitch", 1.0), s.get("weight", 1)))
    return out


def resample(x, sr, pitch):
    rate = pitch * sr / SR
    n = int(len(x) / rate)
    pos = np.arange(n) * rate
    i = np.floor(pos).astype(int)
    frac = pos - i
    i1 = np.minimum(i + 1, len(x) - 1)
    return x[i] * (1 - frac) + x[i1] * frac


class Mix:
    def __init__(self, seconds, seed=7):
        self.buf = np.zeros(int(seconds * SR))
        self.rng = np.random.default_rng(seed)

    def ensure(self, n):
        if n > len(self.buf):
            self.buf = np.concatenate([self.buf, np.zeros(n - len(self.buf))])

    def layer(self, at_s, event, pitch, volume, jitter=0.0, gain_curve=None):
        options = entries(event)
        weights = np.array([w for *_, w in options], dtype=float)
        name, v_vol, v_pitch, _ = options[self.rng.choice(len(options), p=weights / weights.sum())]
        j = 1.0 + (self.rng.uniform(-jitter, jitter) if jitter else 0.0)
        p = min(2.0, max(0.5, pitch * j * v_pitch))
        vol = min(1.0, max(0.0, volume * v_vol))
        x, sr = load(name)
        y = resample(x, sr, p) * vol
        if gain_curve is not None:
            y = y * gain_curve(len(y))
        i = int(round(at_s * SR))
        self.ensure(i + len(y))
        self.buf[i:i + len(y)] += y
        return len(y) / SR

    def cue(self, at_s, name):
        layers = CUES["cues"].get(name)
        if layers is None:
            return
        for at_ms, event, pitch, volume, *rest in layers:
            self.layer(at_s + at_ms / 1000, event, pitch, volume, rest[0] if rest else 0.0)

    def loop(self, at_s, name, hold_s):
        """A loop as client.draft.MythicShimmer plays it: fade in, hold, fade out."""
        spec = CUES["loops"][name]
        options = entries(spec["sound"])
        x, sr = load(options[0][0])
        x = resample(x, sr, 1.0)
        fin, fout = spec["fadeInMs"] / 1000, spec["fadeOutMs"] / 1000
        total = hold_s + fout
        reps = int(math.ceil(total * SR / len(x))) + 1
        y = np.tile(x, reps)[:int(total * SR)]
        t = np.arange(len(y)) / SR
        env = np.clip(t / fin, 0, 1) * np.clip((total - t) / fout, 0, 1)
        y = y * env * min(1.0, spec["volume"] * options[0][1])
        i = int(round(at_s * SR))
        self.ensure(i + len(y))
        self.buf[i:i + len(y)] += y

    def write(self, path):
        y = self.buf
        nz = np.nonzero(np.abs(y) > 1e-4)[0]
        end = (nz[-1] + int(0.25 * SR)) if len(nz) else len(y)
        y = y[:end]
        peak = np.abs(y).max()
        if peak > 0.98:
            # what OpenAL would clip, kept round instead
            y = np.tanh(y / 0.98) * 0.98
        sf.write(path, y.astype(np.float32), SR, subtype="PCM_16")
        return len(y) / SR, peak


# ---------------------------------------------------------------- how the game strings cues together

RULES = CUES["rules"]


def deal(mix, at, rarities):
    mix.cue(at, "deal")
    for r in RULES["stingerOrder"]:
        if r in rarities:
            mix.cue(at + RULES["stingerAtMs"] / 1000, "stinger." + r)
            break


def hover(mix, at, rarity, idle_s=0.0):
    mix.cue(at, "hover." + rarity)
    if rarity == "mythic" and idle_s > 0:
        mix.loop(at, "mythic_idle", idle_s)


def pick(mix, at, rarity):
    mix.cue(at, "pick")
    mix.cue(at, "pick." + rarity)
    mix.cue(at + RULES["burnAfterPickMs"] / 1000, "burn")


RARITIES = ["common", "rare", "epic", "mastery", "cursed", "wildcard", "earned", "mythic"]


def renders():
    r = {}

    def one(name, seconds, build, seed=7):
        m = Mix(seconds, seed)
        build(m)
        r[name] = m

    one("deal", 1.5, lambda m: deal(m, 0.05, ["common", "common", "rare"]))
    for s in ["epic", "cursed", "wildcard", "earned", "mastery"]:
        one(f"deal_stinger_{s}", 2.0, lambda m, s=s: deal(m, 0.05, ["common", s, "rare"]))
    for rar in RARITIES:
        one(f"hover_{rar}", 1.5 if rar != "mythic" else 4.0, lambda m, rar=rar: hover(m, 0.05, rar, 2.5))
    one("hover_sweep_repeat", 3.0, lambda m: [hover(m, 0.05 + 0.28 * i, ["common", "rare", "epic"][i % 3]) for i in range(9)])
    for rar in RARITIES:
        one(f"pick_{rar}", 2.5, lambda m, rar=rar: pick(m, 0.05, rar))
    one("burn_away", 1.8, lambda m: m.cue(0.05, "burn"))
    one("reroll", 2.0, lambda m: (m.cue(0.05, "reroll"), deal(m, 0.75, ["common", "rare", "common"])))
    one("reroll_denied", 1.0, lambda m: m.cue(0.05, "reroll_denied"))
    one("queued_draft_nudge", 1.0, lambda m: m.cue(0.05, "nudge"))
    one("deck_slot", 1.5, lambda m: m.cue(0.05, "slot"))
    one("set_complete", 2.5, lambda m: m.cue(0.05, "set_complete"))
    one("mythic_reveal", 9.0, lambda m: (m.cue(0.3, "mythic_reveal"), hover(m, 6.0, "mythic", 2.5)))
    one("mythic_idle_loop", 9.0, lambda m: m.loop(0.05, "mythic_idle", 8.0))

    def showcase(m):
        t = 0.2
        deal(m, t, ["rare", "epic", "cursed"])            # a hand with a cursed card: its stinger
        for i, rar in enumerate(["rare", "epic", "cursed", "epic"]):
            hover(m, t + 1.3 + 0.65 * i, rar)
        pick(m, t + 4.2, "epic")                           # keep the epic; the other two burn away
        m.cue(t + 6.0, "reroll")                           # spend a reroll on the next skill's hand
        deal(m, t + 6.75, ["common", "rare", "mythic"])    # ...and the deck has a Mythic in it
        m.cue(t + 7.9, "mythic_reveal")
        hover(m, t + 13.4, "mythic", 2.6)
        pick(m, t + 16.3, "mythic")
    one("showcase", 20.0, showcase, seed=11)
    return r


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(os.path.join(METTLE, "..", "sound-previews"))
    os.makedirs(out, exist_ok=True)
    for name, mix in renders().items():
        secs, peak = mix.write(os.path.join(out, name + ".wav"))
        print(f"{name + '.wav':32s} {secs:5.2f} s  peak {peak:.2f}")
    print("previews in", out)


if __name__ == "__main__":
    main()
