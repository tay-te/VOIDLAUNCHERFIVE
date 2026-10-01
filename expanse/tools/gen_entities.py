#!/usr/bin/env python3
"""
Entity models and their textures, from one description.

Each animal below is a list of parts (pivot, rotation, cubes) with painting instructions per cube.
The script packs every cube's box-UV net into a texture, paints the texture from those
instructions, and writes two files per animal:

    assets/expanse/models/entity/<name>.json   geometry + UVs, read by JsonEntityModels at runtime
    assets/expanse/textures/entity/<name>.png  the skin, painted onto exactly those UVs

so the model and its texture cannot drift apart: change a cube's size here and both move together.

Coordinates are Minecraft model space: pixels, y down, the animal facing -z, ground at y=24.
Box UV net of a cube (w, h, d) at (u, v), the layout ModelPart.Cube reads:
    top    (u+d,     v)    w x d        bottom (u+d+w, v)   w x d
    right  (u,       v+d)  d x h        front  (u+d,   v+d) w x h
    left   (u+d+w,   v+d)  d x h        back   (u+2d+w,v+d) w x h
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'expanse')


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


class Cube:
    def __init__(self, origin, size, paint, inflate=0.0, mirror=False):
        self.origin = origin
        self.size = size
        self.paint = paint
        self.inflate = inflate
        self.mirror = mirror
        self.uv = None


class Part:
    def __init__(self, name, parent='root', pivot=(0, 0, 0), rotation=(0, 0, 0), cubes=()):
        self.name = name
        self.parent = parent
        self.pivot = pivot
        self.rotation = rotation
        self.cubes = list(cubes)


# ---------------------------------------------------------------------- painting

class Painter:
    """Paints one cube's six faces. A paint spec is a dict:
        base       main colour
        top        colour of the top face (an animal's back), blended along the sides toward base
        bottom     belly colour, likewise
        noise      per-pixel brightness jitter (0..1)
        streaks    vertical fur streak strength
        front      list of decals on the front face: (x, y, w, h, colour), x/y from its top-left
        sides      decals on both side faces, mirrored
        rings      horizontal band colour every N pixels: (colour, period)
        gradient   'down' darkens toward the bottom of side faces (legs: darker hooves)
        tip        (colour, rows) paints the last rows of side faces (hooves, claw tips)
    """

    def __init__(self, img, rng):
        self.img = img
        self.rng = rng

    def px(self, x, y, c):
        self.img.putpixel((x, y), c + (255,))

    def face(self, x0, y0, w, h, spec, kind):
        base = hexrgb(spec['base'])
        top = hexrgb(spec.get('top', spec['base']))
        bottom = hexrgb(spec.get('bottom', spec['base']))
        noise = spec.get('noise', 0.06)
        streak = spec.get('streaks', 0.0)
        cols = [1.0 + (self.rng.random() - 0.5) * streak for _ in range(w)]
        for yy in range(h):
            for xx in range(w):
                if kind == 'top':
                    c = top
                elif kind == 'bottom':
                    c = bottom
                else:
                    t = yy / max(1, h - 1)
                    # Back colour bleeds two-fifths down the flank, belly colour up from the bottom fifth.
                    if 'top' in spec and t < 0.4:
                        c = mix(top, base, t / 0.4)
                    elif 'bottom' in spec and t > 0.8:
                        c = mix(base, bottom, (t - 0.8) / 0.2)
                    else:
                        c = base
                    if spec.get('gradient') == 'down':
                        c = shade(c, 1.05 - 0.25 * t)
                f = (1.0 + (self.rng.random() - 0.5) * 2 * noise) * (cols[xx] if kind not in ('top', 'bottom') else 1.0)
                self.px(x0 + xx, y0 + yy, shade(c, f))
        if kind in ('left', 'right', 'front', 'back') and 'rings' in spec:
            colour, period = spec['rings']
            for yy in range(period - 1, h, period):
                for xx in range(w):
                    self.px(x0 + xx, y0 + yy, shade(hexrgb(colour), 1.0 + (self.rng.random() - 0.5) * 0.1))
        if kind in ('left', 'right', 'front', 'back') and 'tip' in spec:
            colour, rows = spec['tip']
            for yy in range(max(0, h - rows), h):
                for xx in range(w):
                    self.px(x0 + xx, y0 + yy, shade(hexrgb(colour), 1.0 + (self.rng.random() - 0.5) * 0.1))
        decals = spec.get('front', []) if kind == 'front' else spec.get('sides', []) if kind in ('left', 'right') else []
        for d in decals:
            dx, dy, dw, dh, colour = d
            if kind == 'left':
                dx = w - dx - dw
            for yy in range(dh):
                for xx in range(dw):
                    X, Y = x0 + dx + xx, y0 + dy + yy
                    if x0 <= X < x0 + w and y0 <= Y < y0 + h:
                        self.px(X, Y, hexrgb(colour))

    def cube(self, cube):
        u, v = cube.uv
        w, h, d = cube.size
        spec = cube.paint
        self.face(u + d, v, w, d, spec, 'top')
        self.face(u + d + w, v, w, d, spec, 'bottom')
        self.face(u, v + d, d, h, spec, 'right')
        self.face(u + d, v + d, w, h, spec, 'front')
        self.face(u + d + w, v + d, d, h, spec, 'left')
        self.face(u + 2 * d + w, v + d, w, h, spec, 'back')


# ---------------------------------------------------------------------- packing

def pack(parts, width):
    """Shelf-packs every cube's UV net (2d+2w by d+h) into a texture `width` wide."""
    cubes = [c for p in parts for c in p.cubes]
    order = sorted(cubes, key=lambda c: -(c.size[2] + c.size[1]))
    x = y = shelf = 0
    for c in order:
        w, h, d = c.size
        nw, nh = 2 * d + 2 * w, d + h
        if x + nw > width:
            x, y, shelf = 0, y + shelf, 0
        c.uv = (x, y)
        x += nw
        shelf = max(shelf, nh)
    height = y + shelf
    th = 16
    while th < height:
        th *= 2
    return th


def build(name, parts, width, seed):
    th = pack(parts, width)
    img = Image.new('RGBA', (width, th), (0, 0, 0, 0))
    painter = Painter(img, random.Random(seed))
    for p in parts:
        for c in p.cubes:
            painter.cube(c)
    os.makedirs(os.path.join(ASSETS, 'textures', 'entity'), exist_ok=True)
    img.save(os.path.join(ASSETS, 'textures', 'entity', f'{name}.png'))
    model = {'texture_width': width, 'texture_height': th, 'parts': [
        {'name': p.name, 'parent': p.parent, 'pivot': list(p.pivot), 'rotation': list(p.rotation),
         'cubes': [{'uv': list(c.uv), 'origin': list(c.origin), 'size': list(c.size), 'inflate': c.inflate, 'mirror': c.mirror}
                   for c in p.cubes]}
        for p in parts]}
    os.makedirs(os.path.join(ASSETS, 'models', 'entity'), exist_ok=True)
    with open(os.path.join(ASSETS, 'models', 'entity', f'{name}.json'), 'w') as f:
        json.dump(model, f, indent=1)
    print(f'{name}: {width}x{th}, {sum(len(p.cubes) for p in parts)} cubes')


def legs(spec, size, xs, zs, top_y):
    w, h, d = size
    names = [('right_front_leg', -1, 0), ('left_front_leg', 1, 0), ('right_hind_leg', -1, 1), ('left_hind_leg', 1, 1)]
    return [Part(n, pivot=(sx * xs, top_y, zs[i]), cubes=[Cube((-w / 2, 0, -d / 2), size, spec, mirror=sx > 0)])
            for n, sx, i in names]


# ====================================================================== the animals

def elk():
    coat = {'base': '#7a5233', 'top': '#5c3b22', 'bottom': '#d9c39a', 'noise': 0.07, 'streaks': 0.12}
    mane = {'base': '#4a2f1c', 'top': '#3b2516', 'noise': 0.1, 'streaks': 0.3}
    leg = {'base': '#5a3a22', 'noise': 0.06, 'gradient': 'down', 'tip': ('#2a1a10', 2)}
    rump = {'base': '#e6d3a8', 'noise': 0.05}
    antler = {'base': '#e8dcc0', 'top': '#f4ecd8', 'noise': 0.08, 'tip': ('#fff8e8', 1)}
    face = dict(coat, front=[(1, 2, 1, 1, '#141010'), (4, 2, 1, 1, '#141010')], sides=[(5, 2, 1, 1, '#141010')])
    snout = {'base': '#5a3a24', 'front': [(1, 1, 1, 1, '#1a1210'), (2, 1, 1, 1, '#1a1210')], 'noise': 0.05}
    parts = [
        Part('body', pivot=(0, 0, 0), cubes=[
            Cube((-5, -1, -11), (10, 12, 22), coat),
            Cube((-4, 0, 9), (8, 9, 3), rump, inflate=0.2)]),
        Part('mane', pivot=(0, 0, 0), cubes=[Cube((-5.5, -3, -11), (11, 7, 9), mane, inflate=0.1)]),
        Part('head', pivot=(0, 2, -9), rotation=(22, 0, 0), cubes=[
            Cube((-2.5, -12, -3), (5, 13, 6), coat),
            Cube((-2, -7, -5), (4, 8, 2), mane),
            Cube((-3, -16, -6), (6, 5, 8), face),
            Cube((-2, -15, -10), (4, 4, 4), snout)]),
        Part('left_ear', parent='head', pivot=(3, -15, 0), rotation=(0, 0, -25), cubes=[Cube((0, -1, -1), (3, 2, 1), coat)]),
        Part('right_ear', parent='head', pivot=(-3, -15, 0), rotation=(0, 0, 25), cubes=[Cube((-3, -1, -1), (3, 2, 1), coat, mirror=True)]),
        Part('left_antler', parent='head', pivot=(2, -16, -1), rotation=(-15, 0, 28), cubes=[
            Cube((-0.5, -10, -0.5), (1, 10, 1), antler),
            Cube((-0.5, -4, -4), (1, 1, 4), antler),
            Cube((-0.5, -7, -3), (1, 1, 3), antler),
            Cube((-0.5, -10, 0.5), (1, 1, 3), antler),
            Cube((-0.5, -13, -1.5), (1, 3, 1), antler)]),
        Part('right_antler', parent='head', pivot=(-2, -16, -1), rotation=(-15, 0, -28), cubes=[
            Cube((-0.5, -10, -0.5), (1, 10, 1), antler, mirror=True),
            Cube((-0.5, -4, -4), (1, 1, 4), antler, mirror=True),
            Cube((-0.5, -7, -3), (1, 1, 3), antler, mirror=True),
            Cube((-0.5, -10, 0.5), (1, 1, 3), antler, mirror=True),
            Cube((-0.5, -13, -1.5), (1, 3, 1), antler, mirror=True)]),
        Part('tail', pivot=(0, 0, 11), rotation=(35, 0, 0), cubes=[Cube((-1, 0, 0), (2, 4, 2), rump)]),
    ] + legs(leg, (3, 14, 3), 3, [-8, 8], 10)
    build('elk', parts, 128, 11)


def mammoth():
    fur = {'base': '#5a3a24', 'top': '#432a19', 'bottom': '#3a2414', 'noise': 0.1, 'streaks': 0.35}
    shag = {'base': '#4a2f1d', 'noise': 0.14, 'streaks': 0.5, 'gradient': 'down'}
    leg = {'base': '#4a2f1d', 'noise': 0.1, 'streaks': 0.3, 'tip': ('#2a2420', 2)}
    skin = {'base': '#6b5a50', 'noise': 0.06, 'rings': ('#5a4a42', 3)}
    tusk = {'base': '#efe6cf', 'top': '#fbf6e8', 'noise': 0.05, 'tip': ('#fffdf4', 2)}
    head = dict(fur, front=[(2, 6, 2, 2, '#120c0a'), (10, 6, 2, 2, '#120c0a'), (2, 6, 1, 1, '#3a2a20'), (10, 6, 1, 1, '#3a2a20')])
    parts = [
        Part('body', cubes=[
            Cube((-10, -14, -16), (20, 26, 32), fur),
            Cube((-8, -18, -15), (16, 5, 12), fur)]),
        # The winter coat is its own part so the renderer can hide it once the mammoth is sheared.
        Part('coat', parent='body', cubes=[
            Cube((-10.5, 4, -15.5), (21, 10, 31), shag, inflate=0.4),
            Cube((-8.5, -18.5, -15.5), (17, 4, 13), shag, inflate=0.6)]),
        Part('head', pivot=(0, -6, -16), cubes=[
            Cube((-7, -10, -10), (14, 16, 11), head),
            Cube((-6, -14, -8), (12, 5, 9), fur)]),
        Part('left_ear', parent='head', pivot=(7, -4, -4), rotation=(0, -20, 0), cubes=[Cube((0, -4, 0), (2, 8, 6), skin)]),
        Part('right_ear', parent='head', pivot=(-7, -4, -4), rotation=(0, 20, 0), cubes=[Cube((-2, -4, 0), (2, 8, 6), skin, mirror=True)]),
        Part('trunk', parent='head', pivot=(0, 4, -9), rotation=(-8, 0, 0), cubes=[Cube((-2.5, 0, -2.5), (5, 8, 5), skin)]),
        Part('trunk_mid', parent='trunk', pivot=(0, 8, 0), rotation=(10, 0, 0), cubes=[Cube((-2, 0, -2), (4, 7, 4), skin)]),
        Part('trunk_tip', parent='trunk_mid', pivot=(0, 7, 0), rotation=(-35, 0, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 6, 3), skin)]),
        Part('left_tusk', parent='head', pivot=(4, 3, -8), rotation=(-25, 0, -8), cubes=[Cube((-1, 0, -1), (2, 9, 2), tusk)]),
        Part('left_tusk_tip', parent='left_tusk', pivot=(0, 9, 0), rotation=(-70, 0, 0), cubes=[Cube((-1, 0, -1), (2, 8, 2), tusk)]),
        Part('right_tusk', parent='head', pivot=(-4, 3, -8), rotation=(-25, 0, 8), cubes=[Cube((-1, 0, -1), (2, 9, 2), tusk, mirror=True)]),
        Part('right_tusk_tip', parent='right_tusk', pivot=(0, 9, 0), rotation=(-70, 0, 0), cubes=[Cube((-1, 0, -1), (2, 8, 2), tusk, mirror=True)]),
        Part('tail', pivot=(0, -10, 16), rotation=(25, 0, 0), cubes=[Cube((-1, 0, 0), (2, 8, 2), shag)]),
    ] + legs(leg, (7, 14, 7), 6, [-10, 10], 10)
    build('mammoth', parts, 256, 23)


def capybara():
    coat = {'base': '#a0764a', 'top': '#8a6238', 'bottom': '#c49a6a', 'noise': 0.07, 'streaks': 0.15}
    leg = {'base': '#7a5432', 'noise': 0.06, 'tip': ('#3a2618', 1)}
    head = dict(coat, front=[(1, 1, 5, 2, '#5a3b22'), (2, 1, 1, 1, '#1a120c'), (4, 1, 1, 1, '#1a120c')],
                sides=[(5, 2, 1, 1, '#140e0a')])
    parts = [
        Part('body', pivot=(0, 13, 0), cubes=[Cube((-4.5, -2, -7.5), (9, 9, 16), coat)]),
        Part('head', pivot=(0, 13, -7), cubes=[Cube((-3.5, -4, -9), (7, 7, 9), head)]),
        Part('left_ear', parent='head', pivot=(2.5, -4, -2), cubes=[Cube((-0.5, -1, -0.5), (2, 1, 1), coat)]),
        Part('right_ear', parent='head', pivot=(-2.5, -4, -2), cubes=[Cube((-1.5, -1, -0.5), (2, 1, 1), coat, mirror=True)]),
    ] + legs(leg, (3, 4, 3), 2.5, [-5, 5.5], 20)
    build('capybara', parts, 64, 5)


def crab():
    shell = {'base': '#c8462c', 'top': '#d9573a', 'bottom': '#e8c48a', 'noise': 0.08,
             'front': [(2, 1, 4, 1, '#a83420')]}
    claw = {'base': '#d24e30', 'top': '#e0603e', 'noise': 0.07, 'tip': ('#3a1a10', 1)}
    leg = {'base': '#b8402a', 'noise': 0.05, 'tip': ('#e8c48a', 1)}
    stalk = {'base': '#c8462c', 'top': '#101010', 'tip': ('#101010', 0)}
    parts = [
        Part('body', pivot=(0, 19, 0), cubes=[Cube((-4, -2, -3), (8, 4, 6), shell)]),
        Part('left_eye', parent='body', pivot=(1.5, -2, -2.5), cubes=[Cube((-0.5, -2, -0.5), (1, 2, 1), stalk)]),
        Part('right_eye', parent='body', pivot=(-1.5, -2, -2.5), cubes=[Cube((-0.5, -2, -0.5), (1, 2, 1), stalk)]),
        Part('left_claw', pivot=(4, 19, -2), rotation=(0, -30, 0), cubes=[
            Cube((0, -1, -1), (3, 2, 2), claw), Cube((1, -2.5, -4), (3, 3, 3), claw)]),
        Part('right_claw', pivot=(-4, 19, -2), rotation=(0, 30, 0), cubes=[
            Cube((-3, -1, -1), (3, 2, 2), claw, mirror=True), Cube((-4, -2.5, -4), (3, 3, 3), claw, mirror=True)]),
    ]
    for i, z in enumerate([-1, 1, 2.5]):
        parts.append(Part(f'left_leg_{i}', pivot=(4, 20, z), rotation=(0, 0, 50), cubes=[Cube((0, -0.5, -0.5), (5, 1, 1), leg)]))
        parts.append(Part(f'right_leg_{i}', pivot=(-4, 20, z), rotation=(0, 0, -50), cubes=[Cube((-5, -0.5, -0.5), (5, 1, 1), leg, mirror=True)]))
    build('crab', parts, 64, 7)




# ====================================================================== loot

def loot_tables(jar_path=os.path.expanduser('~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar')):
    """Entity drops, cloned from the cow's table so meat cooks when the animal dies burning, and looting
    counts, exactly as vanilla's do."""
    import zipfile
    cow = json.loads(zipfile.ZipFile(jar_path).read('data/minecraft/loot_table/entities/cow.json'))
    leather_pool, beef_pool = cow['pools'][0], cow['pools'][1]

    def meat(item, lo, hi):
        p = json.loads(json.dumps(beef_pool))
        p['entries'][0]['name'] = item
        p['entries'][0]['modifier'][0]['count'] = {'type': 'minecraft:uniform', 'min': lo, 'max': hi}
        return p

    def plain(item, lo, hi):
        p = json.loads(json.dumps(leather_pool))
        p['entries'][0]['name'] = item
        p['entries'][0]['modifier'][0]['count'] = {'type': 'minecraft:uniform', 'min': lo, 'max': hi}
        return p

    tables = {
        'entities/elk': [plain('minecraft:leather', 0, 2), meat('expanse:venison', 1, 3)],
        'entities/mammoth': [plain('minecraft:leather', 2, 5), plain('minecraft:brown_wool', 1, 3), meat('minecraft:beef', 2, 4)],
        'entities/capybara': [plain('minecraft:leather', 0, 1)],
        'entities/crab': [meat('expanse:crab_meat', 1, 2)],
    }
    root = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'expanse', 'loot_table')
    for name, pools in tables.items():
        out = os.path.join(root, name + '.json')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, 'w') as f:
            json.dump({'type': 'minecraft:entity', 'pools': pools, 'random_sequence': f'expanse:{name}'}, f, indent=2)
    out = os.path.join(root, 'shearing', 'mammoth.json')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w') as f:
        json.dump({'type': 'minecraft:shearing', 'pools': [{'entries': [{'type': 'minecraft:item', 'name': 'minecraft:brown_wool'}],
                                                             'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}}],
                   'random_sequence': 'expanse:shearing/mammoth'}, f, indent=2)


if __name__ == '__main__':
    elk()
    mammoth()
    capybara()
    crab()
    loot_tables()
