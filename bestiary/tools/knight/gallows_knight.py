"""The Gallows Knight's geometry and texture, generated together (the Expanse generator style: model and texture can't
drift) and written as a Blockbench 5 project (modded entity, box UV) for Fray's rig bake and for artists to open.

    python tools/knight/gallows_knight.py <out.bbmodel>

Geometry is written in vanilla's model space (pixels, Y down, front -Z, the model origin 24 px above the feet, a
bone's cubes relative to its pivot) and converted to Blockbench's absolute coordinates (Y up, feet at 0, X mirrored),
the inverse of Blockbench's own Java exporter. Then run tools/blockbench/bb.mjs roundtrip to have the real Blockbench
re-save it (the committed rig source is Blockbench's own file) and write its Java export for the verification test.

BESTIARY.md §8.3: a revenant knight of a gallows hill: tall (2.8 blocks), dark plate with rust, a torn cape, a gibbet
cage on its back, a chain-and-hook in the right hand. Real joints: pelvis, spine, chest, neck, head; shoulders,
elbows, wrists; hips, knees, ankles; three chain links and the hook; a two-piece cape; front and back skirts.
"""
import base64
import io
import json
import random
import sys
import uuid

from PIL import Image

TEX = 128
TEX_H = 64
rnd = random.Random(20261021)

# ------------------------------------------------------------------ palette (dark, weathered, not glowing)
PAL = {
    'plate': [(0x55, 0x5a, 0x61), (0x4a, 0x4e, 0x55), (0x40, 0x44, 0x4a), (0x36, 0x39, 0x3e)],
    'plate_hi': (0x78, 0x7e, 0x86),
    'plate_lo': (0x24, 0x26, 0x2a),
    'rust': [(0x6b, 0x47, 0x2e), (0x58, 0x3a, 0x26), (0x7c, 0x52, 0x33), (0x46, 0x2f, 0x21)],
    'mail': [(0x3b, 0x3e, 0x43), (0x2a, 0x2c, 0x30), (0x4f, 0x53, 0x59)],
    'cloth': [(0x3d, 0x24, 0x22), (0x47, 0x2b, 0x27), (0x33, 0x1e, 0x1c), (0x2a, 0x19, 0x18)],
    'leather': [(0x4a, 0x35, 0x26), (0x3d, 0x2b, 0x1f), (0x56, 0x3e, 0x2c)],
    'chain': [(0x33, 0x36, 0x3a), (0x26, 0x28, 0x2c), (0x57, 0x5c, 0x63)],
    'hook': [(0x5c, 0x41, 0x2f), (0x4a, 0x34, 0x26), (0x76, 0x53, 0x37), (0x8a, 0x8f, 0x96)],
    'cage': [(0x2b, 0x2d, 0x30), (0x22, 0x24, 0x27), (0x4b, 0x3a, 0x2c)],
    'slit': (0x0c, 0x0d, 0x0f),
    'eye': (0x9d, 0xb0, 0xa4),
}

# ------------------------------------------------------------------ geometry (model space)
# bone: name, parent, pivot (absolute model px), rotation (deg, vanilla convention), cubes [(x, y, z, dx, dy, dz, material, mirror)]
BONES = []


def bone(name, parent, pivot, cubes=(), rot=(0, 0, 0)):
    BONES.append({'name': name, 'parent': parent, 'pivot': pivot, 'rot': rot, 'cubes': list(cubes)})


def leg(side, sx):
    m = side == 'l'
    bone(f'{side}_thigh', 'pelvis', (2.6 * sx, 6, 0), [(-2, 0, -2.5, 4, 8, 5, 'plate', m)])
    bone(f'{side}_shin', f'{side}_thigh', (2.6 * sx, 14, 0), [(-2, 0, -2, 4, 8, 4, 'plate', m), (-2.5, -1.5, -3, 5, 3, 2, 'knee', m)])
    bone(f'{side}_foot', f'{side}_shin', (2.6 * sx, 22, 0), [(-2, 0, -4.5, 4, 2, 6, 'plate', m)])


def arm(side, sx, hand_cubes, extra=()):
    m = side == 'l'
    bone(f'{side}_upper_arm', 'chest', (7 * sx, -9, 0), [(-4.5 if sx < 0 else -1.5, -3, -3.5, 6, 5, 7, 'pauldron', m, (0, 0, 12 * sx), (0, -1, 0)),
                                                         (-2.5 if sx < 0 else -1.5, 0, -2, 4, 8, 4, 'plate', m)], rot=(0, 0, -6 * sx))
    bone(f'{side}_forearm', f'{side}_upper_arm', (7.5 * sx, -1, 0), [(-2.5, -1.5, -2.5, 5, 3, 5, 'knee', m), (-2, 0, -2, 4, 8, 4, 'plate', m)])
    bone(f'{side}_hand', f'{side}_forearm', (7.5 * sx, 7, 0), hand_cubes)
    for e in extra:
        bone(*e)


bone('root', None, (0, 5, 0))
bone('pelvis', 'root', (0, 5, 0), [(-5, -2, -3.5, 10, 4, 7, 'belt', False)])
bone('skirt_f', 'pelvis', (0, 7, -3.5), [(-4.5, 0, -1, 9, 6, 1, 'plate', False)])
bone('skirt_b', 'pelvis', (0, 7, 3.5), [(-4.5, 0, 0, 9, 6, 1, 'plate', False)])
leg('r', -1)
leg('l', 1)
bone('spine', 'pelvis', (0, 3, 0), [(-4.5, -4, -3, 9, 4, 6, 'mail', False)])
bone('chest', 'spine', (0, -1, 0), [(-6, -10, -3.5, 12, 10, 7, 'breast', False), (-3.5, -11.5, -3, 7, 2, 6, 'mail', False)])
bone('neck', 'chest', (0, -11, 0), [(-2, -1.5, -2, 4, 2, 4, 'mail', False)])
bone('head', 'neck', (0, -12, 0), [(-3.5, -8, -3.5, 7, 8, 7, 'helm', False), (-0.5, -9, -3, 1, 1, 6, 'plate', False)])
bone('cape', 'chest', (0, -10, 3.5), [(-5.5, 0, 0, 11, 11, 1, 'cloth', False)])
bone('cape_lower', 'cape', (0, 1, 3.5), [(-5.5, 0, 0, 11, 11, 1, 'cloth_torn', False)])
bone('cage', 'chest', (0, -9, 4.5), [
    (-4, -1, 0.5, 8, 1, 6, 'cage', False), (-4, 10, 0.5, 8, 1, 6, 'cage', False),
    (-4, 0, 0.5, 1, 10, 1, 'cage', False), (3, 0, 0.5, 1, 10, 1, 'cage', False),
    (-4, 0, 5.5, 1, 10, 1, 'cage', False), (3, 0, 5.5, 1, 10, 1, 'cage', False),
    (-1.5, 0, 5.5, 1, 10, 1, 'cage', False), (0.5, 0, 5.5, 1, 10, 1, 'cage', False),
    (-4, 0, 3, 1, 10, 1, 'cage', False), (3, 0, 3, 1, 10, 1, 'cage', False),
    (-0.5, -3, 3, 1, 2, 1, 'chain', False),
    # a diagonal brace across the back of the cage (a rotated cube: Blockbench exports it in a helper bone)
    (-0.5, -0.5, 6.5, 1, 11, 1, 'cage', False, (0, 0, 35), (0, 5, 7)),
])
arm('r', -1, [(-2, 0, -2, 4, 3, 4, 'gauntlet', False)], extra=[
    ('chain_1', 'r_hand', (-7.5, 9.5, -0.5), [(-0.5, 0, -1, 1, 3, 2, 'chain', False)]),
    ('chain_2', 'chain_1', (-7.5, 12.5, -0.5), [(-1, 0, -0.5, 2, 3, 1, 'chain', False)]),
    ('chain_3', 'chain_2', (-7.5, 15.5, -0.5), [(-0.5, 0, -1, 1, 3, 2, 'chain', False)]),
    ('hook', 'chain_3', (-7.5, 18.5, -0.5), [(-1, 0, -1, 2, 1, 2, 'hook', False), (-0.5, 1, -0.5, 1, 3, 1, 'hook', False),
                                             (-0.5, 3, -3.5, 1, 1, 3.5, 'hook', False), (-0.5, 1, -3.5, 1, 2, 1, 'hook_tip', False)]),
])
arm('l', 1, [(-2, 0, -2, 4, 3, 4, 'gauntlet', True)])

# Hit and hurt volumes (Blockbench locators hv_<name>_a@radius / hv_<name>_b, in a bone's frame, model px).
LOCATORS = [
    ('hook', 'hook', (0, 1, -0.5), (0, 3.5, -3.5), 0.45),
    ('chain', 'chain_2', (0, -3, 0), (0, 3, 0), 0.3),
    ('chest', 'chest', (0, -8, -3.5), (0, -3, -3.5), 0.4),
]

# ------------------------------------------------------------------ texture painting


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def pick(lst):
    return lst[rnd.randrange(len(lst))]


def paint_face(img, x0, y0, w, h, mat, face):
    """face: up, down, right, front, left, back. Pixels (x, y) within the face, y down."""
    if w <= 0 or h <= 0:
        return
    light = {'up': 1.18, 'down': 0.62, 'front': 1.0, 'back': 0.82, 'right': 0.9, 'left': 0.9}[face]
    for y in range(h):
        for x in range(w):
            c = texel(mat, face, x, y, w, h)
            if c is None:
                img.putpixel((x0 + x, y0 + y), (0, 0, 0, 0))
                continue
            img.putpixel((x0 + x, y0 + y), shade(c, light) + (255,))


def texel(mat, face, x, y, w, h):
    base_mat = mat
    if mat in ('breast', 'helm', 'pauldron', 'knee', 'gauntlet', 'belt'):
        base_mat = 'plate'
    if base_mat == 'plate':
        c = pick(PAL['plate'][:2]) if rnd.random() < 0.7 else pick(PAL['plate'])
        # bevel: lighter top edge, darker bottom edge on the sides
        if face not in ('up', 'down'):
            if y == 0:
                c = PAL['plate_hi'] if rnd.random() < 0.8 else c
            elif y == h - 1:
                c = PAL['plate_lo']
            if x == 0 or x == w - 1:
                c = mix(c, PAL['plate_lo'], 0.35)
        # rust: streaks run down from rivets and edges
        if rnd.random() < 0.06 + (0.08 if y > h * 0.6 else 0):
            c = pick(PAL['rust'])
        if mat == 'breast' and face == 'front':
            mid = w // 2
            if x in (mid - 1, mid) and 0 < y < h - 1:
                c = mix(c, PAL['plate_hi'], 0.45)  # the ridge
            # a faded, torn tabard band across the lower half
            if y >= h - 4 and 2 <= x < w - 2:
                c = pick(PAL['cloth'])
            if y == 2 and x in (2, w - 3):
                c = PAL['plate_hi']  # rivets
        if mat == 'helm' and face == 'front':
            if y in (3, 4) and 1 <= x <= w - 2:
                c = PAL['slit']
                if y == 3 and x in (2, w - 3):
                    c = PAL['eye']  # a pale glint in the dark, not a glow
            if y >= 6 and x % 2 == 1 and 1 <= x <= w - 2:
                c = mix(c, PAL['slit'], 0.75)  # breathing holes
        if mat == 'helm' and face == 'up':
            c = mix(c, PAL['plate_hi'], 0.15)
        if mat == 'pauldron' and face != 'down' and y % 2 == 1:
            c = mix(c, PAL['plate_lo'], 0.4)  # lames
        if mat == 'knee' and face == 'front' and x == w // 2:
            c = mix(c, PAL['plate_hi'], 0.5)
        if mat == 'belt':
            c = pick(PAL['leather']) if face not in ('up', 'down') and 1 <= y <= h - 2 else c
            if face == 'front' and y in (1, 2) and x in (w // 2 - 1, w // 2):
                c = PAL['hook'][3]  # buckle
        if mat == 'gauntlet' and face == 'front' and y == 1:
            c = mix(c, PAL['plate_lo'], 0.5)
        return c
    if mat == 'mail':
        c = PAL['mail'][(x + y) % 2]
        if rnd.random() < 0.12:
            c = PAL['mail'][2]
        if rnd.random() < 0.05:
            c = pick(PAL['rust'])
        return c
    if mat in ('cloth', 'cloth_torn'):
        fold = (x // 2) % 3
        c = PAL['cloth'][fold] if rnd.random() < 0.85 else pick(PAL['cloth'])
        if rnd.random() < 0.04:
            c = mix(c, (0x20, 0x18, 0x14), 0.6)
        if mat == 'cloth_torn' and face in ('front', 'back'):
            # tatters: the bottom rows go ragged (alpha cutout)
            rag = (x * 7 + 3) % 5
            if y >= h - 1 - rag // 2 and rnd.random() < 0.65:
                return None
            if y >= h - 4 and (x * 13) % 7 == 0:
                return None
        return c
    if mat == 'chain':
        c = PAL['chain'][(x + y) % 2]
        if (x + y) % 3 == 0:
            c = PAL['chain'][2]
        return c
    if mat in ('hook', 'hook_tip'):
        c = pick(PAL['hook'][:3])
        if mat == 'hook_tip' and y == 0:
            c = PAL['hook'][3]
        return c
    if mat == 'cage':
        c = PAL['cage'][(y // 2) % 2]
        if rnd.random() < 0.15:
            c = PAL['cage'][2]
        return c
    return (255, 0, 255)


# ------------------------------------------------------------------ UV packing (box UV) and output


def pack(cubes):
    """Shelf-packs box-UV islands (2(dx+dz) x (dz+dy)) into the texture; mirrored left cubes share their right twin's island."""
    x = y = shelf = 0
    places = {}
    order = sorted(range(len(cubes)), key=lambda i: -(cubes[i]['dz'] + cubes[i]['dy']))
    for i in order:
        c = cubes[i]
        key = c['uvkey']
        if key in places:
            c['uv'] = places[key]
            continue
        w = int(2 * (c['dx'] + c['dz']) + 0.999)
        h = int(c['dz'] + c['dy'] + 0.999)
        if x + w > TEX:
            x, y, shelf = 0, y + shelf, 0
        if y + h > TEX_H:
            raise SystemExit('texture too small for the box UV islands')
        c['uv'] = (x, y)
        places[key] = (x, y)
        x += w
        shelf = max(shelf, h)
    return y + shelf


def main():
    out = sys.argv[1]
    by_name = {b['name']: b for b in BONES}
    cubes = []
    for b in BONES:
        for cube in b['cubes']:
            (x, y, z, dx, dy, dz, mat, mirror) = cube[:8]
            crot = cube[8] if len(cube) > 8 else None
            corg = cube[9] if len(cube) > 9 else (0, 0, 0)
            # A left cube with a right twin shares its UV island (mirrored), like vanilla's left limbs.
            twin = None
            if mirror and b['name'].startswith('l_'):
                twin = ('r_' + b['name'][2:], mat, dx, dy, dz)
            cubes.append({'bone': b['name'], 'x': x, 'y': y, 'z': z, 'dx': dx, 'dy': dy, 'dz': dz, 'mat': mat, 'mirror': mirror,
                          'uvkey': twin or (b['name'], mat, dx, dy, dz, x, y, z), 'rot': crot, 'origin': corg})
    for c in cubes:
        if c['mirror'] and c['bone'].startswith('l_'):
            c['uvkey'] = ('r_' + c['bone'][2:], c['mat'], c['dx'], c['dy'], c['dz'])
        elif c['bone'].startswith('r_'):
            c['uvkey'] = (c['bone'], c['mat'], c['dx'], c['dy'], c['dz'])
    used = pack(cubes)
    img = Image.new('RGBA', (TEX, TEX_H), (0, 0, 0, 0))
    painted = set()
    for c in cubes:
        if c['uv'] in painted:
            continue
        painted.add(c['uv'])
        u, v = c['uv']
        dx, dy, dz = int(c['dx'] + 0.999), int(c['dy'] + 0.999), int(c['dz'] + 0.999)
        paint_face(img, u + dz, v, dx, dz, c['mat'], 'up')
        paint_face(img, u + dz + dx, v, dx, dz, c['mat'], 'down')
        paint_face(img, u, v + dz, dz, dy, c['mat'], 'right')
        paint_face(img, u + dz, v + dz, dx, dy, c['mat'], 'front')
        paint_face(img, u + dz + dx, v + dz, dz, dy, c['mat'], 'left')
        paint_face(img, u + 2 * dz + dx, v + dz, dx, dy, c['mat'], 'back')
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    data_url = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()

    # model space → Blockbench absolute: x' = -x, y' = 24 - y, z' = z; rotations (-rx, -ry, rz)
    def bb_point(p):
        return [-p[0], 24 - p[1], p[2]]

    groups, elements, uuids = [], [], {}
    for b in BONES:
        uuids[b['name']] = str(uuid.UUID(int=rnd.getrandbits(128)))
    children = {b['name']: [] for b in BONES}
    roots = []
    for b in BONES:
        rx, ry, rz = b['rot']
        groups.append({'name': b['name'], 'uuid': uuids[b['name']], 'export': True, 'origin': bb_point(b['pivot']), 'rotation': [-rx, -ry, rz],
                       'color': 0, 'children': [], 'reset': False, 'shade': True, 'mirror_uv': False, 'visibility': True, 'autouv': 0, 'isOpen': False})
        (children[b['parent']] if b['parent'] else roots).append(('group', b['name']))
    for c in cubes:
        b = by_name[c['bone']]
        px, py, pz = b['pivot']
        # cube in model space (absolute): x in [px + x, px + x + dx], y likewise (down), z likewise
        ax0, ax1 = px + c['x'], px + c['x'] + c['dx']
        ay0, ay1 = py + c['y'], py + c['y'] + c['dy']
        az0, az1 = pz + c['z'], pz + c['z'] + c['dz']
        el = {'name': f"{c['bone']}_{c['mat']}", 'box_uv': True, 'export': True, 'from': [-ax1, 24 - ay1, az0], 'to': [-ax0, 24 - ay0, az1],
              'autouv': 0, 'color': 0, 'origin': bb_point(b['pivot']), 'uv_offset': list(c['uv']), 'type': 'cube',
              'uuid': str(uuid.UUID(int=rnd.getrandbits(128)))}
        if c['rot']:
            rx, ry, rz = c['rot']
            o = c['origin']
            el['rotation'] = [-rx, -ry, rz]
            el['origin'] = bb_point((px + o[0], py + o[1], pz + o[2]))
        if c['mirror']:
            el['mirror_uv'] = True
        elements.append(el)
        children[c['bone']].append(('el', el['uuid']))
    for (name, bname, a, b_, r) in LOCATORS:
        bn = by_name[bname]
        for suffix, p in (('_a@%g' % r, a), ('_b', b_)):
            abs_p = (bn['pivot'][0] + p[0], bn['pivot'][1] + p[1], bn['pivot'][2] + p[2])
            el = {'name': f'hv_{name}{suffix}', 'position': bb_point(abs_p), 'rotation': [0, 0, 0], 'type': 'locator',
                  'uuid': str(uuid.UUID(int=rnd.getrandbits(128)))}
            elements.append(el)
            children[bname].append(('el', el['uuid']))

    def outline(name):
        return {'uuid': uuids[name], 'isOpen': False, 'children': [outline(n) if k == 'group' else n for (k, n) in children[name]]}

    # A short animation that exercises the axes (nested bones, rotation on all three axes, a position, a step key):
    # Blockbench's own Java export of it is the reference BlockbenchExportTest compares bbImport's conversion with.
    probe = {'uuid': str(uuid.UUID(int=rnd.getrandbits(128))), 'name': 'animation.gallows_knight.axis_probe', 'loop': 'once', 'override': False,
             'length': 1.0, 'snapping': 20, 'animators': {}}

    def kf(channel, t, xyz, interp='linear'):
        return {'channel': channel, 'data_points': [{'x': str(xyz[0]), 'y': str(xyz[1]), 'z': str(xyz[2])}], 'uuid': str(uuid.UUID(int=rnd.getrandbits(128))),
                'time': t, 'color': -1, 'interpolation': interp}

    probe['animators'][uuids['r_forearm']] = {'name': 'r_forearm', 'type': 'bone', 'keyframes': [
        kf('rotation', 0, (0, 0, 0), 'catmullrom'), kf('rotation', 0.5, (70, 15, -10), 'catmullrom'), kf('rotation', 1, (0, 0, 0), 'catmullrom')]}
    probe['animators'][uuids['chest']] = {'name': 'chest', 'type': 'bone', 'keyframes': [
        kf('rotation', 0, (0, 0, 0)), kf('rotation', 0.5, (-12, 30, 8)), kf('position', 0, (0, 0, 0), 'step'), kf('position', 0.5, (1, -2, 3))]}
    probe['animators'][uuids['hook']] = {'name': 'hook', 'type': 'bone', 'keyframes': [kf('rotation', 0, (0, 0, 0)), kf('rotation', 1, (-90, 0, 45))]}

    project = {
        'meta': {'format_version': '5.0', 'model_format': 'modded_entity', 'box_uv': True},
        'name': 'gallows_knight', 'model_identifier': '', 'modded_entity_entity_class': '', 'modded_entity_version': '1.17', 'modded_entity_flip_y': True,
        'visible_box': [1.2, 2.8, 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [], 'timeline_setups': [], 'unhandled_root_fields': {},
        'resolution': {'width': TEX, 'height': TEX_H},
        'elements': elements, 'groups': groups,
        'outliner': [outline(n) for (k, n) in roots],
        'textures': [{'path': '', 'name': 'gallows_knight.png', 'folder': '', 'namespace': '', 'id': '0', 'width': TEX, 'height': TEX_H, 'uv_width': TEX,
                      'uv_height': TEX_H, 'particle': False, 'render_mode': 'default', 'render_sides': 'auto', 'frame_time': 1, 'frame_order_type': 'loop',
                      'frame_order': '', 'frame_interpolate': False, 'visible': True, 'internal': True, 'saved': False,
                      'uuid': str(uuid.UUID(int=rnd.getrandbits(128))), 'source': data_url}],
        'animations': [probe],
    }
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(project, f, indent='\t')
        f.write('\n')
    # What Blockbench has no field for (the renderer's shadow and scale) sits beside the project.
    with open(out.replace('.bbmodel', '.fray.json'), 'w', encoding='utf-8') as f:
        json.dump({'model_scale': 1.0, 'shadow': 0.85}, f, indent=2)
        f.write(chr(10))
    print(out, len(BONES), 'bones,', len(cubes), 'cubes, texture rows used', used, 'of', TEX_H)


if __name__ == '__main__':
    main()
