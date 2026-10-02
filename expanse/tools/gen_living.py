#!/usr/bin/env python3
"""
The living world: the small wild creatures (butterflies and moths, dragonflies, songbirds, herons
and egrets, owls, deer) and what they need besides their Java classes.

    PYTHONDONTWRITEBYTECODE=1 python3 tools/gen_living.py

It writes, deterministically:

    assets/expanse/models/entity/<name>.json          geometry, read by JsonEntityModels (gen_entities.py's format)
    assets/expanse/textures/entity/<name>/<variant>.png  one skin per colour variant, painted onto that geometry
    assets/expanse/textures/item/<name>_spawn_egg.png  spawn egg sprites (tools/textures/items.py's egg)
    assets/expanse/sounds/<name>/*.ogg + sounds.json  birdsong, owl hoots, heron croaks, wingbeats: synthesised
    data/expanse/loot_table/entities/<name>.json      drops (feathers, venison)
    data/expanse/worldgen/placed_feature/living/*.json firefly bushes along the banks and in wet woods
    data/expanse/tags/worldgen/biome/habitat/*.json   where the habitat-bound creatures live (owls, deer)

The models reuse gen_entities.py's Part/Cube/pack machinery unchanged; the painter here adds what the
little creatures need on top: faces drawn from pixel grids (wing patterns), colour bands along a body
(a dragonfly's abdomen), decals on any face, and transparency (the cut-out shape of a wing, the glassy
wings of a dragonfly). Every colour variant of a creature is painted onto the same UV layout, so one
model file serves all its skins.

The sounds are synthesised here (sine sweeps, trills and harmonics for birdsong, formant-ish hoots,
filtered noise for wings) and encoded to Ogg Vorbis with ffmpeg in bit-exact mode, so a rerun writes
the same bytes.
"""
import json
import math
import os
import random
import shutil
import subprocess
import sys
import tempfile
import wave
import zlib

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, 'textures'))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import gen_entities as ge  # noqa: E402
from gen_entities import Cube, Part  # noqa: E402

ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'expanse')
DATA = os.path.join(RES, 'data', 'expanse')
JAR = os.path.expanduser('~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar')


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


# ====================================================================== painting

def rgba(h):
    h = h.lstrip('#')
    c = tuple(int(h[i:i + 2], 16) for i in range(0, len(h), 2))
    return c if len(c) == 4 else c + (255,)


class LivingPainter(ge.Painter):
    """gen_entities' Painter, plus:
        grid      {face kind: rows} with 'palette' {char: '#rrggbb[aa]'}; '.' is transparent. For the top
                  and bottom faces the rows run front to back and the columns from the cube's min x to
                  its max x (the wing root, for a wing drawn as a +x plane); for front/back/sides the rows
                  run top to bottom.
        zbands    colours by z, front to back, painted along the top, bottom and side faces (body bands)
        decals    {face kind: [(x, y, w, h, colour)]} on any face, after everything else
        alpha     opacity of the plainly painted faces
    """

    def px(self, x, y, c):
        a = c[3] if len(c) == 4 else self.alpha
        self.img.putpixel((x, y), tuple(c[:3]) + (a,))

    def face(self, x0, y0, w, h, spec, kind):
        self.alpha = spec.get('alpha', 255)
        grids = spec.get('grid', {})
        if kind in grids:
            rows = grids[kind]
            if kind in ('top', 'bottom'):
                rows = rows[::-1]  # texture row 0 is the back edge of a top face
            for yy in range(h):
                for xx in range(w):
                    ch = rows[yy][xx]
                    if ch == '.':
                        self.img.putpixel((x0 + xx, y0 + yy), (0, 0, 0, 0))
                        continue
                    c = rgba(spec['palette'][ch])
                    f = 1.0 + (self.rng.random() - 0.5) * 2 * spec.get('noise', 0.04)
                    self.img.putpixel((x0 + xx, y0 + yy), ge.shade(c[:3], f) + (c[3],))
        else:
            super().face(x0, y0, w, h, spec, kind)
        if 'zbands' in spec and kind in ('top', 'bottom', 'left', 'right'):
            bands = spec['zbands']
            for yy in range(h):
                for xx in range(w):
                    if kind in ('top', 'bottom'):
                        z = h - 1 - yy
                    elif kind == 'right':
                        z = w - 1 - xx
                    else:
                        z = xx
                    if z < len(bands) and bands[z]:
                        f = 1.0 + (self.rng.random() - 0.5) * 0.08
                        self.px(x0 + xx, y0 + yy, ge.shade(ge.hexrgb(bands[z]), f))
        for (dx, dy, dw, dh, colour) in spec.get('decals', {}).get(kind, []):
            if kind == 'left':
                dx = w - dx - dw
            for yy in range(dh):
                for xx in range(dw):
                    X, Y = x0 + dx + xx, y0 + dy + yy
                    if x0 <= X < x0 + w and y0 <= Y < y0 + h:
                        self.px(X, Y, rgba(colour))


def build_creature(name, parts_for, variants, width, seed):
    """One model, one skin per variant. `parts_for(paint)` builds the geometry from a variant's paint
    specs; the geometry must not depend on the variant (asserted), so the skins share one UV layout."""
    layouts = []
    first = None
    for i, (variant, paint) in enumerate(variants.items()):
        parts = parts_for(paint)
        th = ge.pack(parts, width)
        layout = [(p.name, [(c.uv, c.origin, c.size) for c in p.cubes]) for p in parts]
        layouts.append(layout)
        assert layout == layouts[0], f'{name}: variant {variant} changes the geometry'
        img = Image.new('RGBA', (width, th), (0, 0, 0, 0))
        painter = LivingPainter(img, random.Random(seed * 1000 + i))
        for p in parts:
            for c in p.cubes:
                painter.cube(c)
        out = os.path.join(ASSETS, 'textures', 'entity', name, f'{variant}.png')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        img.save(out)
        if first is None:
            first = (parts, th)
    parts, th = first
    model = {'texture_width': width, 'texture_height': th, 'parts': [
        {'name': p.name, 'parent': p.parent, 'pivot': list(p.pivot), 'rotation': list(p.rotation),
         'cubes': [{'uv': list(c.uv), 'origin': list(c.origin), 'size': list(c.size), 'inflate': c.inflate, 'mirror': c.mirror}
                   for c in p.cubes]}
        for p in parts]}
    write_json(os.path.join(ASSETS, 'models', 'entity', f'{name}.json'), model)
    print(f'{name}: {width}x{th}, {sum(len(p.cubes) for p in parts)} cubes, {len(variants)} skins')


# ====================================================================== butterflies and moths

# A wing seen from above: rows front (leading edge) to back, columns root to tip. Forewing in the
# first three rows, the notch, then the hindwing.
WING_MASK = ['XXXXX',
             'XXXXX',
             'XXXX.',
             'XXX..',
             'XXXX.',
             'XXX..']


def wing(top, under, palette, body):
    for rows in (top, under):
        for r, m in zip(rows, WING_MASK):
            assert all((a == '.') == (b == '.') for a, b in zip(r, m)), rows
    return {'base': body, 'grid': {'top': top, 'bottom': under}, 'palette': palette, 'noise': 0.05}


BUTTERFLIES = {
    # name: (upper side, underside, palette, body colour)
    'monarch': (['KKKWK', 'KOOOK', 'KOKO.', 'KOO..', 'KOOK.', 'KKK..'],
                ['KKKWK', 'KPPPK', 'KPKP.', 'KPP..', 'KPPK.', 'KKK..'],
                {'K': '#1c1612', 'O': '#e8822a', 'W': '#f4efe2', 'P': '#efae6a'}, '#1c1612'),
    'cabbage_white': (['GWWKK', 'GWWWK', 'GWKW.', 'GWW..', 'GWWW.', 'GWW..'],
                      ['GYYYY', 'GYYYY', 'GYKY.', 'GYY..', 'GYYY.', 'GYY..'],
                      {'G': '#c9c8bc', 'W': '#f4f2ea', 'K': '#2a2a2a', 'Y': '#ece8c4'}, '#3a3a36'),
    'morpho': (['KKKKK', 'KBLBK', 'KBBL.', 'KBB..', 'KBLB.', 'KKK..'],
               ['NNNNN', 'NNYNN', 'NNNN.', 'NYN..', 'NNYN.', 'NNN..'],
               {'K': '#141820', 'B': '#2f7fe8', 'L': '#6ab8ff', 'N': '#7a5a3a', 'Y': '#e0c040'}, '#1a1a22'),
    'brimstone': (['YYYYY', 'YYYYY', 'YYOY.', 'YYY..', 'YYYO.', 'YYY..'],
                  ['GGGGG', 'GGGGG', 'GGOG.', 'GGG..', 'GGGO.', 'GGG..'],
                  {'Y': '#f2e04a', 'G': '#d8dc78', 'O': '#e88a2a'}, '#8a8a3a'),
    'red_admiral': (['KKKWK', 'KKRWK', 'KRRK.', 'KKK..', 'KKKR.', 'KKR..'],
                    ['NNNWN', 'NNRWN', 'NRRN.', 'NNN..', 'NNNN.', 'NNN..'],
                    {'K': '#1c1a1a', 'R': '#e0502a', 'W': '#f4f0e8', 'N': '#4a3a32'}, '#1c1a1a'),
    # moths, which fly at night
    'luna_moth': (['PPPPP', 'GGGGG', 'GGYG.', 'GGG..', 'GGYG.', 'GGG..'],
                  ['PPPPP', 'HHHHH', 'HHYH.', 'HHH..', 'HHHH.', 'HHH..'],
                  {'P': '#8a6a7a', 'G': '#b8e8a8', 'H': '#cfeec4', 'Y': '#e8d070'}, '#e8f0e0'),
    'tiger_moth': (['BCBCB', 'BCCBB', 'BBCB.', 'BCB..', 'OOKO.', 'OKO..'],
                   ['BBBBB', 'BCBBB', 'BBBB.', 'BBB..', 'OOOO.', 'OOO..'],
                   {'B': '#5a3a2a', 'C': '#e8dcc0', 'O': '#e06a2a', 'K': '#1c1410'}, '#3a2a20'),
}


def butterfly():
    def parts(p):
        return [
            # head, thorax and abdomen in one stick
            Part('body', pivot=(0, 23, 0), cubes=[Cube((-0.5, -1, -3), (1, 1, 5), p['body'])]),
            Part('antennae', parent='body', pivot=(0, -1, -3), rotation=(-35, 0, 0), cubes=[Cube((-1.5, -2, 0), (3, 2, 0), p['antennae'])]),
            Part('left_wing', parent='body', pivot=(0.5, -1, -0.5), cubes=[Cube((0, 0, -3), (5, 0, 6), p['wing'])]),
            Part('right_wing', parent='body', pivot=(-0.5, -1, -0.5), cubes=[Cube((-5, 0, -3), (5, 0, 6), p['wing'], mirror=True)]),
        ]

    variants = {}
    for name, (top, under, palette, body) in BUTTERFLIES.items():
        variants[name] = {
            'body': {'base': body, 'noise': 0.06},
            'antennae': {'base': body, 'grid': {'front': ['K.K', '.K.'], 'back': ['K.K', '.K.']}, 'palette': {'K': body}},
            'wing': wing(top, under, palette, body),
        }
    build_creature('butterfly', parts, variants, 32, 41)


# ====================================================================== dragonflies

def dragonfly():
    glass = {'W': '#e8f2ffa0', 'V': '#8a9ab8d0', 'S': '#2a2420ff'}

    def parts(p):
        return [
            Part('body', pivot=(0, 22.5, 0), cubes=[
                Cube((-1, -0.5, -4), (2, 1, 1), p['eyes']),
                Cube((-0.5, -0.5, -3), (1, 1, 3), p['thorax']),
                Cube((-0.5, -0.5, 0), (1, 1, 7), p['tail'])]),
            Part('left_forewing', parent='body', pivot=(0.5, -0.5, -2.5), cubes=[Cube((0, 0, -1), (5, 0, 2), p['forewing'])]),
            Part('right_forewing', parent='body', pivot=(-0.5, -0.5, -2.5), cubes=[Cube((-5, 0, -1), (5, 0, 2), p['forewing'], mirror=True)]),
            Part('left_hindwing', parent='body', pivot=(0.5, -0.5, -0.5), cubes=[Cube((0, 0, -1), (5, 0, 2), p['hindwing'])]),
            Part('right_hindwing', parent='body', pivot=(-0.5, -0.5, -0.5), cubes=[Cube((-5, 0, -1), (5, 0, 2), p['hindwing'], mirror=True)]),
        ]

    fore = {'base': '#e8f2ff', 'grid': {'top': ['VVVSV', 'WWWW.'], 'bottom': ['VVVSV', 'WWWW.']}, 'palette': glass}
    hind = {'base': '#e8f2ff', 'grid': {'top': ['VVVV.', 'WWWW.'], 'bottom': ['VVVV.', 'WWWW.']}, 'palette': glass}
    variants = {}
    for name, (eye, thorax, tail, ring) in {
        'azure': ('#2a5ab8', '#2a3a5a', '#3a8ae0', '#14202e'),
        'emerald': ('#2a8a4a', '#2a4a32', '#3aa860', '#142a1a'),
        'scarlet': ('#a82a1a', '#5a2a22', '#d8402a', '#2a1210'),
    }.items():
        bands = [tail if i % 2 == 0 else ring for i in range(6)] + ['#1a1414']
        variants[name] = {
            'eyes': {'base': eye, 'noise': 0.08},
            'thorax': {'base': thorax, 'noise': 0.08},
            'tail': {'base': tail, 'zbands': bands, 'noise': 0.06},
            'forewing': fore, 'hindwing': hind,
        }
    build_creature('dragonfly', parts, variants, 32, 43)


# ====================================================================== songbirds

SONGBIRDS = {
    # back, flank, breast (front of the body), belly, head top, face (front of the head), wing, tail, beak, legs, extra
    'robin': dict(back='#6e5238', flank='#7a5c40', breast='#e2763a', belly='#e8e0d0', cap='#6e5238', face='#e2763a',
                  wing='#5e4430', tail='#5a4230', beak='#2a2420', legs='#8a6a50'),
    'bluebird': dict(back='#3a6ad8', flank='#d8823a', breast='#d8823a', belly='#f0ece4', cap='#3a6ad8', face='#3a6ad8',
                     wing='#2e58c0', tail='#2e58c0', beak='#1c1c22', legs='#2a2a2e'),
    'goldfinch': dict(back='#f2d42a', flank='#f2d42a', breast='#f6dc3a', belly='#f4ecc0', cap='#1a1a1a', face='#f2d42a',
                      wing='#1a1a1a', tail='#1a1a1a', beak='#e8a060', legs='#c08a6a', bar='#f4f4ee'),
    'cardinal': dict(back='#c42424', flank='#d42a2a', breast='#d83030', belly='#c84040', cap='#d42a2a', face='#141010',
                     wing='#a81e1e', tail='#a01c1c', beak='#e88a3a', legs='#a06a5a'),
    'sparrow': dict(back='#8a6440', flank='#a08a70', breast='#1e1a18', belly='#cfc4b0', cap='#8a8680', face='#a8a49c',
                    wing='#7a5636', tail='#6a4a30', beak='#3a3632', legs='#b08a70', bar='#e8dcc0'),
    'snow_bunting': dict(back='#e8e4dc', flank='#f2f2ee', breast='#f4f4f0', belly='#f6f6f4', cap='#f0eee8', face='#f4f2ee',
                         wing='#1c1c1c', tail='#1c1c1c', beak='#2a2a2a', legs='#1c1c1c', bar='#f6f6f2'),
}


def songbird():
    def parts(p):
        return [
            Part('body', pivot=(0, 21, 0), cubes=[Cube((-1.5, -2, -2.5), (3, 3, 5), p['body'])]),
            Part('head', pivot=(0, 19, -2), cubes=[Cube((-1.5, -3, -2), (3, 3, 3), p['head'])]),
            Part('beak', parent='head', pivot=(0, -1.5, -2), cubes=[Cube((-0.5, -0.5, -1), (1, 1, 1), p['beak'])]),
            Part('tail', pivot=(0, 20, 2.5), rotation=(12, 0, 0), cubes=[Cube((-1, -0.5, 0), (2, 1, 3), p['tail'])]),
            Part('left_wing', pivot=(1.5, 19, -1.5), cubes=[Cube((0, 0, 0), (1, 3, 4), p['wing'])]),
            Part('right_wing', pivot=(-1.5, 19, -1.5), cubes=[Cube((-1, 0, 0), (1, 3, 4), p['wing'], mirror=True)]),
            Part('left_leg', pivot=(0.8, 22, 0), cubes=[Cube((-0.5, 0, -0.5), (1, 2, 1), p['leg'])]),
            Part('right_leg', pivot=(-0.8, 22, 0), cubes=[Cube((-0.5, 0, -0.5), (1, 2, 1), p['leg'], mirror=True)]),
        ]

    variants = {}
    for name, c in SONGBIRDS.items():
        body = {'base': c['flank'], 'top': c['back'], 'bottom': c['belly'], 'noise': 0.07, 'streaks': 0.15 if name == 'sparrow' else 0.0,
                'decals': {'front': [(0, 0, 3, 3, c['breast'])] + ([(0, 2, 3, 1, c['belly'])] if name != 'cardinal' else [])}}
        if name == 'sparrow':
            body['decals']['front'] = [(0, 0, 3, 3, c['belly']), (1, 0, 1, 2, c['breast'])]
        head = {'base': c['cap'], 'top': c['cap'], 'noise': 0.05,
                'decals': {'front': [(0, 1, 3, 2, c['face'])], 'left': [(0, 1, 2, 2, c['face']), (1, 1, 1, 1, '#0c0a0a')],
                           'right': [(0, 1, 2, 2, c['face']), (1, 1, 1, 1, '#0c0a0a')]}}
        if name == 'cardinal':
            head['decals']['front'] = [(0, 1, 3, 2, c['face'])]
            head['decals']['left'] = [(1, 1, 2, 2, c['face']), (1, 1, 1, 1, '#2a0a0a')]
            head['decals']['right'] = [(1, 1, 2, 2, c['face']), (1, 1, 1, 1, '#2a0a0a')]
        if name == 'robin':
            head['decals']['front'] = [(0, 1, 3, 2, c['face'])]
        if name == 'goldfinch':
            head['decals']['front'] = [(0, 0, 3, 1, c['cap']), (0, 1, 3, 2, c['face'])]
            head['decals']['left'] = [(0, 1, 3, 2, c['face']), (1, 1, 1, 1, '#0c0a0a')]
            head['decals']['right'] = [(0, 1, 3, 2, c['face']), (1, 1, 1, 1, '#0c0a0a')]
        wing = {'base': c['wing'], 'noise': 0.08}
        if 'bar' in c:
            wing['decals'] = {'left': [(0, 1, 4, 1, c['bar'])], 'right': [(0, 1, 4, 1, c['bar'])]}
        variants[name] = {
            'body': body,
            'head': head,
            'beak': {'base': c['beak'], 'noise': 0.04},
            'tail': {'base': c['tail'], 'noise': 0.06},
            'wing': wing,
            'leg': {'base': c['legs'], 'noise': 0.04},
        }
    build_creature('songbird', parts, variants, 32, 47)


# ====================================================================== herons and egrets

def heron():
    def parts(p):
        return [
            Part('body', pivot=(0, 13, 1), rotation=(-10, 0, 0), cubes=[Cube((-2, -2.5, -4.5), (4, 5, 9), p['body'])]),
            Part('left_wing', parent='body', pivot=(2, -2, -3.5), cubes=[Cube((0, 0, 0), (1, 4, 8), p['wing'])]),
            Part('right_wing', parent='body', pivot=(-2, -2, -3.5), cubes=[Cube((-1, 0, 0), (1, 4, 8), p['wing'], mirror=True)]),
            Part('left_wing_spread', parent='body', pivot=(2, -2, -1.5), cubes=[Cube((0, -0.5, -3), (12, 1, 6), p['spread'])]),
            Part('right_wing_spread', parent='body', pivot=(-2, -2, -1.5), cubes=[Cube((-12, -0.5, -3), (12, 1, 6), p['spread'], mirror=True)]),
            Part('tail', parent='body', pivot=(0, -1, 4.5), rotation=(-15, 0, 0), cubes=[Cube((-1.5, -0.5, 0), (3, 1, 3), p['tail'])]),
            Part('neck', pivot=(0, 11, -3), rotation=(20, 0, 0), cubes=[Cube((-1, -7, -1), (2, 7, 2), p['neck'])]),
            Part('head', parent='neck', pivot=(0, -7, 0), rotation=(-20, 0, 0), cubes=[Cube((-1, -2, -2), (2, 2, 3), p['head'])]),
            Part('beak', parent='head', pivot=(0, -1, -2), cubes=[Cube((-0.5, -0.5, -5), (1, 1, 5), p['beak'])]),
            Part('crest', parent='head', pivot=(0, -1.5, 1), rotation=(-25, 0, 0), cubes=[Cube((-0.5, -0.5, 0), (1, 1, 3), p['crest'])]),
            Part('left_leg', pivot=(1, 15, 1), cubes=[Cube((-0.5, 0, -0.5), (1, 9, 1), p['leg'])]),
            Part('right_leg', pivot=(-1, 15, 1), cubes=[Cube((-0.5, 0, -0.5), (1, 9, 1), p['leg'], mirror=True)]),
        ]

    variants = {}
    for name, c in {
        'grey_heron': dict(body='#9aa0a8', back='#8a9098', belly='#d8dce0', neck='#e4e8ea', stripe='#2a2c30', head='#f0f2f2',
                           crest='#1c1c20', beak='#e0b040', legs='#b89a60', flight='#3a3e44', spread='#a0a6ae'),
        'great_egret': dict(body='#f2f2ee', back='#ecece8', belly='#f6f6f2', neck='#f4f4f0', stripe='#f4f4f0', head='#f6f6f2',
                            crest='#f0f0ec', beak='#f0c030', legs='#1c1c1c', flight='#e4e4e0', spread='#f4f4f0'),
    }.items():
        variants[name] = {
            'body': {'base': c['body'], 'top': c['back'], 'bottom': c['belly'], 'noise': 0.05, 'streaks': 0.08},
            'wing': {'base': c['back'], 'noise': 0.05, 'tip': (c['flight'], 1),
                     'decals': {'left': [(0, 0, 2, 4, c['flight'])], 'right': [(0, 0, 2, 4, c['flight'])]}},
            'spread': {'base': c['spread'], 'top': c['spread'], 'bottom': c['belly'], 'noise': 0.05,
                       'decals': {'top': [(8, 0, 4, 6, c['flight'])], 'bottom': [(8, 0, 4, 6, c['flight'])]}},
            'tail': {'base': c['back'], 'noise': 0.05},
            'neck': {'base': c['neck'], 'noise': 0.04, 'decals': {'front': [(0, 2, 1, 5, c['stripe'])]}},
            'head': {'base': c['head'], 'noise': 0.04,
                     'decals': {'left': [(0, 0, 3, 1, c['crest']), (1, 1, 1, 1, '#d8a020')],
                                'right': [(0, 0, 3, 1, c['crest']), (1, 1, 1, 1, '#d8a020')]}},
            'beak': {'base': c['beak'], 'noise': 0.04},
            'crest': {'base': c['crest'], 'noise': 0.04},
            'leg': {'base': c['legs'], 'noise': 0.05},
        }
    build_creature('heron', parts, variants, 64, 53)


# ====================================================================== owls

def owl():
    def parts(p):
        return [
            Part('body', pivot=(0, 22, 0), cubes=[Cube((-2.5, -7, -2), (5, 7, 4), p['body'])]),
            Part('head', pivot=(0, 15, 0), cubes=[Cube((-3.5, -5, -2.5), (7, 5, 5), p['head'])]),
            Part('eyelids', parent='head', pivot=(0, 0, 0), cubes=[Cube((-3.5, -4, -2.6), (7, 2, 0), p['eyelids'])]),
            Part('left_wing', pivot=(2.5, 15.5, -1.5), cubes=[Cube((0, 0, 0), (1, 6, 4), p['wing'])]),
            Part('right_wing', pivot=(-2.5, 15.5, -1.5), cubes=[Cube((-1, 0, 0), (1, 6, 4), p['wing'], mirror=True)]),
            Part('left_wing_spread', pivot=(2.5, 16, 0), cubes=[Cube((0, -0.5, -2.5), (9, 1, 5), p['spread'])]),
            Part('right_wing_spread', pivot=(-2.5, 16, 0), cubes=[Cube((-9, -0.5, -2.5), (9, 1, 5), p['spread'], mirror=True)]),
            Part('tail', pivot=(0, 21, 2), rotation=(-30, 0, 0), cubes=[Cube((-1.5, 0, 0), (3, 1, 3), p['tail'])]),
            Part('left_foot', pivot=(1, 22, -0.5), cubes=[Cube((-0.5, 0, -0.5), (1, 2, 1), p['foot'])]),
            Part('right_foot', pivot=(-1, 22, -0.5), cubes=[Cube((-0.5, 0, -0.5), (1, 2, 1), p['foot'], mirror=True)]),
        ]

    variants = {}
    for name, c in {
        'tawny': dict(body='#8a5a32', back='#6e4426', belly='#b0804a', disc='#c49a6a', rim='#5a3820', eye='#141010', iris=None,
                      beak='#d8c8a0', foot='#c8a878', flight='#4a2e1a', fleck='#3a2414'),
        'barn': dict(body='#d8a860', back='#c89850', belly='#f4ece0', disc='#f6f2ea', rim='#b88a50', eye='#141010', iris=None,
                     beak='#e0d0c0', foot='#f0e6d6', flight='#a87840', fleck='#8a6a40'),
        'snowy': dict(body='#f4f4f2', back='#ececea', belly='#f8f8f6', disc='#f6f6f4', rim='#e0e0dc', eye='#f0c020', iris='#141010',
                      beak='#2a2a2a', foot='#f4f4f2', flight='#e4e4e0', fleck='#2a2a2a'),
    }.items():
        eyes = [(1, 1, 2, 2, c['eye']), (4, 1, 2, 2, c['eye'])]
        if c['iris']:
            eyes += [(2, 2, 1, 1, c['iris']), (4, 2, 1, 1, c['iris'])]
        flecks = [(1, 1, 1, 1, c['fleck']), (3, 3, 1, 1, c['fleck']), (2, 5, 1, 1, c['fleck']), (0, 4, 1, 1, c['fleck'])]
        variants[name] = {
            'body': {'base': c['body'], 'top': c['back'], 'noise': 0.08, 'streaks': 0.1,
                     'decals': {'front': [(0, 0, 5, 7, c['belly'])] + flecks, 'left': flecks, 'right': flecks}},
            'head': {'base': c['body'], 'top': c['back'], 'noise': 0.06,
                     'decals': {'front': [(0, 0, 7, 5, c['rim']), (1, 0, 5, 5, c['disc']), (0, 1, 7, 3, c['disc'])] + eyes
                                + [(3, 3, 1, 1, c['beak'])]}},
            'eyelids': {'base': c['disc'], 'grid': {'front': ['DDDDDDD', 'DKKDKKD'], 'back': ['DDDDDDD', 'DDDDDDD']},
                        'palette': {'D': c['disc'], 'K': c['rim']}},
            'wing': {'base': c['back'], 'noise': 0.08, 'tip': (c['flight'], 1),
                     'decals': {'left': flecks, 'right': flecks}},
            'spread': {'base': c['back'], 'top': c['back'], 'bottom': c['belly'], 'noise': 0.07,
                       'decals': {'top': [(6, 0, 3, 5, c['flight']), (2, 1, 1, 1, c['fleck']), (4, 3, 1, 1, c['fleck'])],
                                  'bottom': [(7, 0, 2, 5, c['flight'])]}},
            'tail': {'base': c['back'], 'noise': 0.06, 'decals': {'top': [(0, 1, 3, 1, c['flight'])]}},
            'foot': {'base': c['foot'], 'noise': 0.05},
        }
    build_creature('owl', parts, variants, 64, 59)


# ====================================================================== deer

def deer():
    def parts(p):
        return [
            Part('body', cubes=[Cube((-3, 6, -7), (6, 7, 14), p['coat'])]),
            Part('head', pivot=(0, 7, -6), rotation=(25, 0, 0), cubes=[
                Cube((-1.5, -9, -2), (3, 10, 3), p['neck']),
                Cube((-2, -12, -4), (4, 4, 6), p['face']),
                Cube((-1.5, -11, -7), (3, 3, 3), p['snout'])]),
            Part('left_ear', parent='head', pivot=(2, -12, 0), rotation=(0, 0, -30), cubes=[Cube((0, -1, -0.5), (3, 2, 1), p['ear'])]),
            Part('right_ear', parent='head', pivot=(-2, -12, 0), rotation=(0, 0, 30), cubes=[Cube((-3, -1, -0.5), (3, 2, 1), p['ear'], mirror=True)]),
            Part('left_antler', parent='head', pivot=(1.5, -12, -1), rotation=(-10, 0, 20), cubes=[
                Cube((-0.5, -6, -0.5), (1, 6, 1), p['antler']),
                Cube((-0.5, -3, -3), (1, 1, 3), p['antler']),
                Cube((-0.5, -6, -2.5), (1, 1, 2), p['antler'])]),
            Part('right_antler', parent='head', pivot=(-1.5, -12, -1), rotation=(-10, 0, -20), cubes=[
                Cube((-0.5, -6, -0.5), (1, 6, 1), p['antler'], mirror=True),
                Cube((-0.5, -3, -3), (1, 1, 3), p['antler'], mirror=True),
                Cube((-0.5, -6, -2.5), (1, 1, 2), p['antler'], mirror=True)]),
            Part('tail', pivot=(0, 7, 7), rotation=(25, 0, 0), cubes=[Cube((-1, 0, 0), (2, 3, 1), p['tail'])]),
        ] + ge.legs(p['leg'], (2, 11, 2), 2, [-5, 5], 13)

    def paints(coat_c, top_c, spots):
        coat = {'base': coat_c, 'top': top_c, 'bottom': '#efe6d6', 'noise': 0.06, 'streaks': 0.1,
                'decals': {'back': [(1, 2, 4, 5, '#f2ece0')], 'left': spots, 'right': spots, 'top': [(1 + s[0] % 4, s[1] % 13, 1, 1, '#f4ece0') for s in spots]}}
        return {
            'coat': coat,
            'neck': {'base': coat_c, 'top': top_c, 'noise': 0.06, 'decals': {'front': [(0, 3, 3, 3, '#f2ece0')]}},
            'face': {'base': coat_c, 'top': top_c, 'noise': 0.05,
                     'decals': {'left': [(1, 1, 1, 1, '#120c0a'), (0, 2, 1, 1, '#e8e0d0')], 'right': [(1, 1, 1, 1, '#120c0a'), (0, 2, 1, 1, '#e8e0d0')]}},
            'snout': {'base': '#6a4a32', 'top': coat_c, 'noise': 0.05,
                      'decals': {'front': [(0, 0, 3, 1, '#1a1210'), (0, 2, 3, 1, '#f2ece0')], 'bottom': [(0, 0, 3, 3, '#f2ece0')]}},
            'ear': {'base': top_c, 'noise': 0.05, 'decals': {'front': [(1, 0, 1, 1, '#e8c8b0')]}},
            'antler': {'base': '#d8c8a8', 'top': '#ece0c8', 'noise': 0.06, 'tip': ('#f4ecd8', 1)},
            'tail': {'base': top_c, 'noise': 0.05, 'decals': {'back': [(0, 0, 2, 3, '#f6f2ea')], 'bottom': [(0, 0, 2, 1, '#f6f2ea')]}},
            'leg': {'base': '#7a5236', 'noise': 0.05, 'gradient': 'down', 'tip': ('#2a1a12', 1)},
        }

    fawn_spots = [(2, 1, 1, 1, '#f4ece0'), (5, 1, 1, 1, '#f4ece0'), (8, 2, 1, 1, '#f4ece0'), (11, 1, 1, 1, '#f4ece0'),
                  (3, 3, 1, 1, '#f4ece0'), (6, 3, 1, 1, '#f4ece0'), (9, 4, 1, 1, '#f4ece0'), (12, 3, 1, 1, '#f4ece0')]
    build_creature('deer', parts, {'deer': paints('#9a6a42', '#7e5434', []),
                                   'fawn': paints('#b07040', '#9a6036', fawn_spots)}, 64, 61)


# ====================================================================== spawn eggs

LIVING_EGGS = {
    'butterfly': dict(base='#e8822a', spot='#1c1612', spots=[(6, 4, 1), (9, 6, 2), (5, 8, 2), (10, 10, 1), (7, 11, 2)]),
    'dragonfly': dict(base='#3a8ae0', spot='#e8f2ff', spots=[(6, 4, 2), (9, 7, 1), (5, 9, 1), (8, 10, 3), (11, 12, 1)]),
    'songbird': dict(base='#6e5238', spot='#e2763a', spots=[(5, 4, 2), (9, 5, 1), (6, 8, 3), (10, 10, 2), (5, 12, 1)]),
    'heron': dict(base='#9aa0a8', spot='#2a2c30', spots=[(6, 4, 1), (9, 6, 2), (5, 9, 2), (9, 11, 1), (7, 12, 1)]),
    'owl': dict(base='#8a5a32', spot='#c49a6a', spots=[(5, 4, 2), (9, 4, 2), (7, 8, 1), (5, 11, 2), (10, 10, 1)]),
    'deer': dict(base='#9a6a42', spot='#f2ece0', spots=[(6, 4, 1), (9, 5, 2), (5, 8, 1), (8, 9, 2), (6, 12, 1), (10, 12, 1)]),
}


def spawn_eggs():
    import items
    import texlib
    items.EGGS.update(LIVING_EGGS)
    for kind in LIVING_EGGS:
        rng = np.random.default_rng(zlib.crc32(f'{kind}_spawn_egg'.encode()) ^ 0x5EED)
        texlib.save(items.spawn_egg(kind, rng), os.path.join(ASSETS, 'textures', 'item', f'{kind}_spawn_egg.png'))
    print(f'{len(LIVING_EGGS)} spawn eggs')


# ====================================================================== loot

def loot():
    import zipfile
    cow = json.loads(zipfile.ZipFile(JAR).read('data/minecraft/loot_table/entities/cow.json'))
    leather_pool, beef_pool = cow['pools'][0], cow['pools'][1]

    def pool(template, item, lo, hi):
        p = json.loads(json.dumps(template))
        p['entries'][0]['name'] = item
        p['entries'][0]['modifier'][0]['count'] = {'type': 'minecraft:uniform', 'min': lo, 'max': hi}
        return p

    tables = {
        'deer': [pool(leather_pool, 'minecraft:leather', 0, 1), pool(beef_pool, 'expanse:venison', 1, 2)],
        'songbird': [pool(leather_pool, 'minecraft:feather', 0, 1)],
        'heron': [pool(leather_pool, 'minecraft:feather', 0, 2)],
        'owl': [pool(leather_pool, 'minecraft:feather', 0, 2)],
    }
    for name, pools in tables.items():
        write_json(os.path.join(DATA, 'loot_table', 'entities', f'{name}.json'),
                   {'type': 'minecraft:entity', 'pools': pools, 'random_sequence': f'expanse:entities/{name}'})


# ====================================================================== fireflies

WATER = ['minecraft:water', 'minecraft:flowing_water']


def fluid_at(dx, dy, dz):
    return {'type': 'minecraft:matching_fluids', 'fluids': WATER, 'offset': [dx, dy, dz]}


def water_beside(radius, dy):
    return {'type': 'minecraft:any_of', 'predicates': [fluid_at(dx, dy, dz) for dx in range(-radius, radius + 1)
                                                       for dz in range(-radius, radius + 1)
                                                       if (dx, dz) != (0, 0) and abs(dx) + abs(dz) <= radius]}


def trapezoid(lo, hi):
    return {'type': 'minecraft:trapezoid', 'min': lo, 'max': hi, 'plateau': 0}


def firefly_features():
    """Firefly bushes: vanilla's firefly bush makes the fireflies (at night, or in deep shade), so these
    put bushes where fireflies belong. Vanilla already scatters a few near water; these follow the Earth
    terrain's rivers more closely, and fill the wet woods."""
    survive = {'type': 'minecraft:would_survive', 'state': 'minecraft:firefly_bush'}  # 26.x: a block state as a bare id
    air = {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:air'}
    out = os.path.join(DATA, 'worldgen', 'placed_feature', 'living')
    shutil.rmtree(out, ignore_errors=True)
    # Anchors that land on a bank (water within two blocks, a block down) seed a cluster along it.
    write_json(os.path.join(out, 'riverbank_fireflies.json'), {'feature': 'minecraft:firefly_bush', 'placement': [
        {'type': 'minecraft:count', 'count': 3},
        {'type': 'minecraft:in_square'},
        {'type': 'minecraft:heightmap', 'heightmap': 'MOTION_BLOCKING_NO_LEAVES'},
        {'type': 'minecraft:biome'},
        {'type': 'minecraft:block_predicate_filter', 'predicate': water_beside(2, -1)},
        {'type': 'minecraft:count', 'count': 10},
        {'type': 'minecraft:offset', 'x': trapezoid(-4, 4), 'y': trapezoid(-1, 1), 'z': trapezoid(-4, 4)},
        {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:all_of', 'predicates': [
            air, survive, water_beside(3, -1)]}},
    ]})
    # A few clusters under the canopy of wet woods, away from water too.
    write_json(os.path.join(out, 'woodland_fireflies.json'), {'feature': 'minecraft:firefly_bush', 'placement': [
        {'type': 'minecraft:rarity_filter', 'chance': 3},
        {'type': 'minecraft:in_square'},
        {'type': 'minecraft:heightmap', 'heightmap': 'MOTION_BLOCKING_NO_LEAVES'},
        {'type': 'minecraft:biome'},
        {'type': 'minecraft:count', 'count': 8},
        {'type': 'minecraft:offset', 'x': trapezoid(-5, 5), 'y': trapezoid(-2, 2), 'z': trapezoid(-5, 5)},
        {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:all_of', 'predicates': [
            air, survive, {'type': 'minecraft:any_of', 'predicates': [
                {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:leaves', 'offset': [0, dy, 0]} for dy in range(3, 8)]}]}},
    ]})


# ====================================================================== habitats

def habitats():
    """Biome tags for the creatures bound to a habitat. They name vanilla's own tags wherever one fits, and
    the Expanse biomes join vanilla's tags through their analogs, so new biomes are covered as they come.
    (The creatures of every bank and meadow, songbirds, butterflies, dragonflies and herons, go by climate
    instead; see world/LivingWorld.java.)"""
    out = os.path.join(DATA, 'tags', 'worldgen', 'biome', 'habitat')
    shutil.rmtree(out, ignore_errors=True)
    opt = lambda i: {'id': i, 'required': False}
    tags = {
        # Owls: woods of every kind, and the open snow for the snowy owl.
        'owls': ['#minecraft:is_forest', '#minecraft:is_taiga', '#minecraft:is_jungle', 'minecraft:swamp', 'minecraft:mangrove_swamp',
                 'minecraft:snowy_plains', opt('expanse:willow_bayou'), opt('expanse:jade_karst'), opt('expanse:cloud_forest'),
                 opt('expanse:frostbloom_tundra'), opt('expanse:heather_moor')],
        # Deer: broadleaf and mixed woods (the elk has the taiga and the moors).
        'deer': ['#minecraft:is_forest', 'minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:cherry_grove'],
        # Where frogs live anywhere, not only beside water (everywhere else, a frog spawns within a few blocks of it).
        'frogs_anywhere': ['minecraft:swamp', 'minecraft:mangrove_swamp', opt('expanse:willow_bayou')],
    }
    for name, values in tags.items():
        write_json(os.path.join(out, f'{name}.json'), {'replace': False, 'values': values})


# ====================================================================== sounds

SR = 44100


def env(n, attack, release):
    """A raised-cosine fade in and out, so no syllable starts or stops with a click."""
    e = np.ones(n)
    a = min(n, max(2, int(attack * SR)))
    r = min(n, max(2, int(release * SR)))
    e[:a] = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, a))
    e[-r:] = np.minimum(e[-r:], 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, r)))
    return e


def sweep(f0, f1, dur, *, curve=1.0, vib=0.0, vib_rate=0.0, h2=0.1, h3=0.02, attack=0.009, release=0.02):
    """A tone gliding from f0 to f1, with a touch of vibrato and harmonics: one syllable of birdsong."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    k = (t / dur) ** curve
    f = f0 + (f1 - f0) * k
    if vib:
        f = f + vib * np.sin(2 * np.pi * vib_rate * t)
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(ph) + h2 * np.sin(2 * ph) + h3 * np.sin(3 * ph)
    return s * env(n, attack, release)


def silence(dur):
    return np.zeros(int(dur * SR))


def trill(f, n, note, gap, drop=0.0):
    out = []
    for i in range(n):
        out += [sweep(f * (1 - drop * i / n), f * (1 - drop * i / n) * 0.9, note, h2=0.15), silence(gap)]
    return np.concatenate(out)


def reverb(x, rng):
    """A short room of leaves: a few quiet, damped echoes."""
    y = np.concatenate([x, np.zeros(int(0.25 * SR))])
    for delay, gain in ((0.023, 0.22), (0.041, 0.14), (0.067, 0.08), (0.11, 0.05)):
        d = int(delay * SR)
        y[d:d + len(x)] += gain * x
    return y


def finish(x, peak=0.6):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


SYLLABLES = {
    'up': lambda r: sweep(2600 + r.uniform(-200, 200), 4300 + r.uniform(-300, 300), 0.07 + r.uniform(0, 0.03)),
    'down': lambda r: sweep(5000 + r.uniform(-300, 300), 3000 + r.uniform(-200, 200), 0.09 + r.uniform(0, 0.03), curve=0.6),
    'whistle': lambda r: sweep(3400 + r.uniform(-300, 300), 3300 + r.uniform(-300, 300), 0.16 + r.uniform(0, 0.08), vib=60, vib_rate=28),
    'trill': lambda r: trill(4200 + r.uniform(-400, 400), 5 + r.randrange(4), 0.022, 0.012, drop=0.1),
    'chip': lambda r: sweep(6000 + r.uniform(-400, 400), 3600, 0.028, curve=0.5, h2=0.1),
    'warble': lambda r: sweep(3500 + r.uniform(-300, 300), 3600 + r.uniform(-300, 300), 0.2 + r.uniform(0, 0.1), vib=700, vib_rate=20 + r.uniform(0, 8)),
    'slur': lambda r: sweep(4300 + r.uniform(-200, 200), 2200 + r.uniform(-200, 200), 0.24 + r.uniform(0, 0.06), curve=0.8, h2=0.12),
}
SONGS = [
    ['warble', 'up', 'down', 'warble', 'whistle'],          # robin-ish
    ['slur', 'slur', 'slur', 'trill'],                       # cardinal-ish "cheer cheer cheer"
    ['trill', 'up', 'trill', 'chip', 'chip', 'up'],          # finch twitter
    ['chip', 'chip', 'whistle', 'trill'],                    # sparrow
    ['warble', 'down', 'warble'],                            # bluebird
    ['whistle', 'whistle', 'down', 'up', 'trill'],
    ['up', 'up', 'down', 'chip', 'warble'],
    ['chip', 'trill', 'slur', 'chip'],
]


def songbird_songs(rng):
    out = []
    for i, song in enumerate(SONGS):
        parts = [silence(0.03)]
        for s in song:
            parts += [SYLLABLES[s](rng), silence(0.04 + rng.uniform(0, 0.09))]
        x = np.concatenate(parts)
        out.append(finish(reverb(x, rng), 0.55))
    return out


def songbird_calls(rng):
    out = []
    for n in (2, 3, 4):
        parts = []
        for _ in range(n):
            parts += [SYLLABLES['chip'](rng), silence(0.05 + rng.uniform(0, 0.04))]
        out.append(finish(reverb(np.concatenate(parts), rng), 0.6))
    return out


def hoot(f, dur, *, quaver=0.0, fall=0.06):
    n = int(dur * SR)
    t = np.arange(n) / SR
    freq = f * (1 + fall * (1 - t / dur) * 0.5) * (1 + quaver * np.sin(2 * np.pi * 7 * t))
    ph = 2 * np.pi * np.cumsum(freq) / SR
    s = np.sin(ph) + 0.18 * np.sin(2 * ph) + 0.05 * np.sin(3 * ph)
    return s * env(n, 0.05, min(0.18, dur * 0.4)) * (1 + quaver * 2 * np.sin(2 * np.pi * 7 * t))


def owl_hoots(rng):
    noise = lambda n: np.array([rng.gauss(0, 1) for _ in range(n)])
    out = []
    # "hoo ... hu-hu-hoooo"
    a = np.concatenate([hoot(410, 0.42), silence(0.55), hoot(400, 0.12), silence(0.07), hoot(400, 0.12), silence(0.07),
                        hoot(395, 0.9, quaver=0.025)])
    # "hoo-hoo"
    b = np.concatenate([hoot(380, 0.35), silence(0.25), hoot(370, 0.5)])
    # a long, quavering "hoooooo"
    c = hoot(420, 1.3, quaver=0.035, fall=0.1)
    for x in (a, b, c):
        # a breath of air at the start of each hoot
        x = x + 0.004 * noise(len(x)) * np.abs(x) / (np.max(np.abs(x)) or 1)
        out.append(finish(reverb(x, rng), 0.55))
    return out


def croak(rng, dur, f0, f1):
    """A heron's harsh "fraank": a buzzy, jittery low tone, its upper harmonics left loud."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    jitter = np.cumsum(np.array([rng.gauss(0, 1) for _ in range(n)])) / SR * 40
    freq = f0 + (f1 - f0) * (t / dur) + jitter
    ph = 2 * np.pi * np.cumsum(freq) / SR
    s = sum((1.0 / k ** 0.7) * np.sin(k * ph + rng.uniform(0, 6.28)) for k in range(1, 14))
    s = s * (1 + 0.5 * np.sin(2 * np.pi * 31 * t))
    s = s + 0.12 * np.array([rng.gauss(0, 1) for _ in range(n)])
    k = np.ones(5) / 5
    s = np.convolve(s, k, mode='same')  # take the fizz off the top
    return s * env(n, 0.02, 0.12)


def heron_croaks(rng):
    return [finish(reverb(croak(rng, 0.34, 240, 170), rng), 0.6),
            finish(reverb(np.concatenate([croak(rng, 0.2, 230, 190), silence(0.12), croak(rng, 0.28, 220, 160)]), rng), 0.6)]


def wingbeats(rng):
    """Wings: a few soft bursts of low, filtered noise."""
    out = []
    for flaps, rate in ((4, 13), (6, 11)):
        n = int((flaps / rate + 0.15) * SR)
        x = np.zeros(n)
        for i in range(flaps):
            start = int(i / rate * SR)
            m = int(0.06 * SR)
            burst = np.array([rng.gauss(0, 1) for _ in range(m)]) * np.hanning(m) * (1 - 0.12 * i)
            x[start:start + m] += burst
        # a crude low-pass: a moving average, twice
        k = np.ones(9) / 9
        x = np.convolve(np.convolve(x, k, mode='same'), k, mode='same')
        out.append(finish(x, 0.5))
    return out


# event: (folder, generator, subtitle). Every clip fades out over 16 blocks, the distance the server sends
# a sound of volume 1 to; a creature that should carry further plays louder (an owl at 2 is heard to 32),
# which stretches both alike.
SOUNDS = {
    'entity.songbird.song': ('songbird/song', songbird_songs, 'Songbird sings'),
    'entity.songbird.call': ('songbird/call', songbird_calls, 'Songbird calls in alarm'),
    'entity.owl.hoot': ('owl/hoot', owl_hoots, 'Owl hoots'),
    'entity.heron.croak': ('heron/croak', heron_croaks, 'Heron croaks'),
    'entity.bird.wings': ('bird/wings', wingbeats, 'Wings flap'),
}


def encode(samples, path):
    pcm = (np.clip(samples, -1, 1) * 32767).astype('<i2').tobytes()
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'in.wav')
        with wave.open(wav, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '3',
                        '-fflags', '+bitexact', '-flags:a', '+bitexact', '-map_metadata', '-1', path], check=True)


def sounds():
    shutil.rmtree(os.path.join(ASSETS, 'sounds'), ignore_errors=True)
    index = {}
    for event, (folder, gen, subtitle) in SOUNDS.items():
        rng = random.Random(zlib.crc32(event.encode()))
        clips = gen(rng)
        names = []
        for i, clip in enumerate(clips, 1):
            name = f'{folder}{i}'
            encode(clip, os.path.join(ASSETS, 'sounds', f'{name}.ogg'))
            names.append(f'expanse:{name}')
        index[event] = {'sounds': names, 'subtitle': f'subtitles.expanse.{event}'}
    write_json(os.path.join(ASSETS, 'sounds.json'), index)
    print(f'{sum(len(v["sounds"]) for v in index.values())} sound clips')


if __name__ == '__main__':
    butterfly()
    dragonfly()
    songbird()
    heron()
    owl()
    deer()
    spawn_eggs()
    loot()
    firefly_features()
    habitats()
    # The sounds are only as reproducible as the Vorbis encoder; CI checks everything else and keeps the
    # committed clips (--keep-sounds).
    if '--keep-sounds' not in sys.argv:
        sounds()
