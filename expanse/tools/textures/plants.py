"""Cross-model plants (transparent background), hand-drawn pixel maps plus a
procedural hanging moss."""
import numpy as np

from texlib import blank, from_ascii, put


HEATHER = dict(
    pal={'W': '#f6b6ee', 'M': '#e06fd2', 'P': '#b8479f', 'p': '#86307a',
         'G': '#7f9a4a', 'g': '#5c7536', 'd': '#3e4f27', 'b': '#6b4a33', 'B': '#4a3322'},
    art=[
        "................",
        "................",
        "................",
        "................",
        "......W.........",
        "...W..M..W...W..",
        "...M..PW.M..WM..",
        "..WMP.Pp.PW.MP..",
        "..MPg.gM.pM.Pp..",
        "..PpGMgPgGPgpg..",
        "...gGPgpgGpgGgd.",
        "..dgGgdGgdGgGdd.",
        "...dgGdgbgdgGd..",
        "....dgbdbBdgd...",
        ".....bB.Bb.B....",
        "......B.B.B.....",
    ])

EDELWEISS = dict(
    pal={'W': '#fbfcf8', 'w': '#dfe5dc', 'v': '#b8c2b4', 'Y': '#f4d35a', 'y': '#c99a2c',
         'G': '#a9b9a2', 'g': '#7f9478', 'd': '#5e7259'},
    art=[
        "................",
        "................",
        "................",
        ".......W........",
        "...W..WWw..W....",
        "....WWwWwWWv....",
        "....wWWYYWwv....",
        ".WWWWWYyyYWWWwv.",
        "..vwwWYyyYWwvv..",
        "....wWWYyWWv....",
        "....vwWwwwWv....",
        "...wv..Wv..vv...",
        ".......vg.......",
        "......Gg..G.....",
        ".......gGGg.....",
        ".......dg.......",
    ])

FROSTBLOOM = dict(
    pal={'W': '#f6fdff', 'C': '#cff0ff', 'c': '#9fd8f6', 'b': '#6aa6e0', 'n': '#4a78c0',
         'S': '#d8eef2', 's': '#a6c8d2', 'L': '#bfe6f0'},
    art=[
        "................",
        "................",
        ".......W........",
        "......WCc.......",
        "...C..CWc..C....",
        "...Wc.cWb.Wc....",
        "....CcCWcCcb....",
        "..WCCWWWWCCcbn..",
        "....cbCWcCbn....",
        "...Wb.cWb.cb....",
        "...c..bcn..n....",
        ".......S........",
        "......Ls........",
        ".....LLsS.......",
        ".......SLL......",
        ".......s........",
    ])

GLOWCAP = dict(
    pal={'W': '#e2fff8', 'G': '#7ff5e0', 'g': '#3fc7b8', 'q': '#1f8a84', 'Q': '#145f60',
         'S': '#eef4ec', 's': '#bccbc2', 'r': '#8fa39a'},
    art=[
        "................",
        "................",
        "................",
        "................",
        "......GGG.......",
        ".....GWGGg......",
        "....GWGGggq.....",
        "....gggggqQ.....",
        "......qSsQ.GGg..",
        ".GGg...Ss.GWggq.",
        "GWGgq..Ss.ggqqQ.",
        "ggqqQ..Ss..qSQ..",
        ".qSQ...Ss...Ss..",
        "..Ss...Ss...Ss..",
        "..Ss...Sr...Sr..",
        "..sr...sr...sr..",
    ])

CATTAIL_PAL = {'L': '#a2c75e', 'l': '#7aa445', 'm': '#5b8636', 'd': '#3f6328',
               'S': '#8db04f', 's': '#6c8d3a'}
# leaf centre-lines, bottom -> top; (x, y, wide) - wide leaves get a shaded
# right edge.  Leaves A and B continue into cattail_top at x=3 / x=13.
CATTAIL_LEAVES = [
    [(5, 15, 1), (5, 14, 1), (4, 13, 1), (4, 12, 1), (4, 11, 1), (4, 10, 1), (3, 9, 0), (3, 8, 0),
     (3, 7, 0), (3, 6, 0), (3, 5, 0), (3, 4, 0), (3, 3, 0), (3, 2, 0), (3, 1, 0), (3, 0, 0)],
    [(10, 15, 1), (10, 14, 1), (11, 13, 1), (11, 12, 1), (11, 11, 1), (12, 10, 1), (12, 9, 0),
     (12, 8, 0), (13, 7, 0), (13, 6, 0), (13, 5, 0), (13, 4, 0), (13, 3, 0), (13, 2, 0), (13, 1, 0),
     (13, 0, 0)],
    [(6, 15, 1), (5, 14, 0), (4, 14, 0), (3, 13, 0), (2, 12, 0), (2, 11, 0), (1, 10, 0), (1, 9, 0),
     (0, 8, 0)],
    [(11, 15, 1), (12, 14, 0), (13, 13, 0), (14, 12, 0), (14, 11, 0), (15, 10, 0), (15, 9, 0)],
    [(7, 15, 0), (7, 14, 0), (6, 13, 0), (6, 12, 0), (6, 11, 0), (6, 10, 0), (6, 9, 0), (6, 8, 0),
     (5, 7, 0), (5, 6, 0), (5, 5, 0)],
]


def cattail_bottom():
    img = blank()
    c = CATTAIL_PAL
    for y in range(16):                      # the flower stalk runs straight through
        put(img, 8, y, c['S'], wrap=False)
        put(img, 9, y, c['s'], wrap=False)
    for i, leaf in enumerate(CATTAIL_LEAVES):
        n = len(leaf)
        for j, (x, y, wide) in enumerate(leaf):
            tip = j >= n - 2 and i >= 2
            put(img, x, y, c['l'] if tip else c['L'], wrap=False)
            if wide:
                put(img, x + 1, y, c['m'], wrap=False)
    for (x, y) in ((7, 15), (8, 15), (9, 15), (6, 15), (10, 15)):
        put(img, x, y, c['d'], wrap=False)
    return img


CATTAIL_TOP = dict(
    pal={'L': '#a2c75e', 'l': '#a2c75e', 'm': '#5b8636', 'd': '#3f6328',
         'S': '#8db04f', 's': '#6c8d3a',
         'T': '#c9a77a', 't': '#a3835a',
         'H': '#8a5a34', 'h': '#6e4426', 'k': '#4f2e18', 'K': '#a8724a'},
    art=[
        "................",
        "........T.......",
        "........t.......",
        "........t.......",
        ".......KHh......",
        ".......HHhk.....",
        ".......KHhk.....",
        ".......HHhk.....",
        ".......KHhk.....",
        ".......HHhk.....",
        ".......Khhk.....",
        "........Ss......",
        "...l....Ss......",
        "...l....Ss...L..",
        "...l....Ss...L..",
        "...l....Ss...L..",
    ])

MOSS = ['#5d6b57', '#76856c', '#909e84', '#a9b79b', '#c3cfb4']


def _moss_strands(rng):
    """Strand layout shared by the body and the tip so they line up."""
    strands = []
    xs = [1, 4, 6, 9, 11, 14]
    ends = rng.permutation([6, 8, 10, 12, 13, 15])
    for i, x in enumerate(xs):
        strands.append(dict(x=x, ph=rng.random() * 2 * np.pi, amp=float(rng.choice([0.6, 1.0, 1.4])),
                            shade=int(rng.integers(1, 4)), step=int(rng.integers(0, 3)),
                            tufts=sorted(int(v) for v in rng.choice(16, 3, replace=False)),
                            end=int(ends[i])))
    return strands


def _draw_moss(strands, rng, tip):
    img = blank()
    for i, s in enumerate(strands):
        last = s['end'] if tip else 16
        side = 1 if i % 2 else -1
        for y in range(last):
            off = int(round(s['amp'] * np.sin(2 * np.pi * y / 16 + s['ph'])))
            x = (s['x'] + off) % 16
            v = s['shade'] + (1 if (y + s['step']) % 3 == 0 else 0)
            if tip and y >= last - 2:
                v = 4 if y == last - 1 else 3           # pale frayed ends
            put(img, x, y, MOSS[min(4, v)], wrap=True)
            if y in s['tufts'] and (not tip or y < last - 3):
                put(img, x + side, y, MOSS[min(4, s['shade'] + 1)], wrap=True)
                put(img, x + side, y + 1, MOSS[max(0, s['shade'] - 1)], wrap=True)
    return img


BLOSSOMS = {
    'wisteria_blossoms': ['#3e2266', '#5a3590', '#7a4fb2', '#9a72cf', '#b892e3', '#d3b7f3', '#efe2fd'],
    'azure_wisteria_blossoms': ['#1f2f6e', '#33509f', '#4b6cbd', '#6a8ed6', '#8eade9', '#b4cdf6', '#e0ebfe'],
}


def _blossom_layout(rng):
    """Racemes for the hanging blossom block: wide clusters of florets, shared by body and tip."""
    return [dict(x=x, ph=rng.random() * 2 * np.pi, end=int(e), shade=int(rng.integers(0, 2)))
            for x, e in zip([2, 6, 10, 13], rng.permutation([9, 11, 13, 15]))]


def _draw_blossoms(layout, palette, rng, tip):
    """Hanging wisteria racemes. The body tiles vertically (florets repeat every 16 rows); the tip
    narrows each raceme to a point, pale and open at the top of the cluster, deep buds at the end."""
    img = blank()
    for s in layout:
        last = s['end'] if tip else 16
        for y in range(last):
            t = y / max(1, last - 1) if tip else 0.0
            width = 3 if t < 0.55 else (2 if t < 0.85 else 1)
            off = int(round(0.7 * np.sin(2 * np.pi * y / 16 + s['ph'])))
            x0 = s['x'] + off - (1 if width == 3 else 0)
            for c in range(width):
                v = 4 + s['shade'] - (1 if (y + c) % 2 else 0)
                if c == 0 and width > 1:
                    v += 1                      # lit side
                if c == width - 1 and width > 1:
                    v -= 1
                if tip:
                    v -= int(round(3 * t))      # buds darken toward the end
                put(img, x0 + c, y, palette[int(np.clip(v, 0, 6))], wrap=True)
    return img


def generate(rng_for):
    out = []
    for name, d in (('heather', HEATHER), ('edelweiss', EDELWEISS), ('frostbloom', FROSTBLOOM),
                    ('glowcap', GLOWCAP)):
        out.append((f'textures/block/{name}.png', from_ascii(d['art'], d['pal']), False))
    out.append(('textures/block/cattail_bottom.png', cattail_bottom(), False))
    out.append(('textures/block/cattail_top.png', from_ascii(CATTAIL_TOP['art'], CATTAIL_TOP['pal']), False))
    seed = rng_for('spanish_moss_layout').integers(1 << 30)
    strands = _moss_strands(np.random.default_rng(seed))
    out.append(('textures/block/spanish_moss.png', _draw_moss(strands, rng_for('spanish_moss'), tip=False), True))
    out.append(('textures/block/spanish_moss_tip.png', _draw_moss(strands, rng_for('spanish_moss_tip'), tip=True), False))
    for name, palette in BLOSSOMS.items():
        layout = _blossom_layout(np.random.default_rng(rng_for(name + '_layout').integers(1 << 30)))
        out.append((f'textures/block/{name}.png', _draw_blossoms(layout, palette, rng_for(name), tip=False), True))
        out.append((f'textures/block/{name}_tip.png', _draw_blossoms(layout, palette, rng_for(name + '_tip'), tip=True), False))
    return out
