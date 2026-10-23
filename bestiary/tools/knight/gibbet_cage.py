"""The gibbet cage the Gallows Knight tears off its back in phase 2 (BESTIARY.md §8.3): the knight's own cage cubes
(gallows_knight.py) as a rig of its own, painted the same way, for the rolling hazard entity. Written as a Blockbench 5
project (modded entity, box UV), read by the motion bake like the knight's.

    python tools/knight/gibbet_cage.py src/main/blockbench/rig/bestiary/gibbet_cage.bbmodel

The cage lies on its side (its tall axis across, along X) so it rolls forward like a barrel; `root` is the roll axis.
"""
import base64
import io
import json
import os
import random
import sys
import uuid

sys.path.insert(0, os.path.dirname(__file__))
import gallows_knight as gk  # noqa: E402  (its palette and painting; its main() isn't run)

from PIL import Image  # noqa: E402

TEX, TEX_H = 64, 32


def main():
    out = sys.argv[1]
    gk.rnd.seed(20261022)
    rnd = random.Random(20261022)
    knight_cage = [b for b in gk.BONES if b['name'] == 'cage'][0]
    # The knight's cage spans x -4..4, y -3..11, z 0.5..7.5 around its pivot; centre it on the roll axis.
    cx, cy, cz = 0, 4, 4
    cubes = []
    for cube in knight_cage['cubes']:
        (x, y, z, dx, dy, dz, mat, mirror) = cube[:8]
        crot = cube[8] if len(cube) > 8 else None
        corg = cube[9] if len(cube) > 9 else (0, 0, 0)
        cubes.append({'x': x - cx, 'y': y - cy, 'z': z - cz, 'dx': dx, 'dy': dy, 'dz': dz, 'mat': mat, 'mirror': mirror, 'rot': crot,
                      'origin': (corg[0] - cx, corg[1] - cy, corg[2] - cz), 'uvkey': ('cage', mat, dx, dy, dz, x, y, z)})
    gk.TEX, gk.TEX_H = TEX, TEX_H
    used = gk.pack(cubes)
    img = Image.new('RGBA', (TEX, TEX_H), (0, 0, 0, 0))
    painted = set()
    for c in cubes:
        if c['uv'] in painted:
            continue
        painted.add(c['uv'])
        u, v = c['uv']
        dx, dy, dz = int(c['dx'] + 0.999), int(c['dy'] + 0.999), int(c['dz'] + 0.999)
        gk.paint_face(img, u + dz, v, dx, dz, c['mat'], 'up')
        gk.paint_face(img, u + dz + dx, v, dx, dz, c['mat'], 'down')
        gk.paint_face(img, u, v + dz, dz, dy, c['mat'], 'right')
        gk.paint_face(img, u + dz, v + dz, dx, dy, c['mat'], 'front')
        gk.paint_face(img, u + dz + dx, v + dz, dz, dy, c['mat'], 'left')
        gk.paint_face(img, u + 2 * dz + dx, v + dz, dx, dy, c['mat'], 'back')
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    data_url = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()

    def bb_point(p):
        return [-p[0], 24 - p[1], p[2]]

    def uid():
        return str(uuid.UUID(int=rnd.getrandbits(128)))

    # Bones (model space, Y down, feet at y = 24): root on the roll axis, 4.5 px above the ground; the cage lies along X.
    pivot = (0, 24 - 4.5, 0)
    bones = [('root', None, pivot, (0, 0, 0)), ('cage', 'root', pivot, (0, 0, 90))]
    groups, elements, children, uuids = [], [], {}, {}
    for name, parent, pv, rot in bones:
        uuids[name] = uid()
        children[name] = []
        rx, ry, rz = rot
        groups.append({'name': name, 'uuid': uuids[name], 'export': True, 'origin': bb_point(pv), 'rotation': [-rx, -ry, rz], 'color': 0,
                       'children': [], 'reset': False, 'shade': True, 'mirror_uv': False, 'visibility': True, 'autouv': 0, 'isOpen': False})
    children['root'].append(('group', 'cage'))
    px, py, pz = pivot
    for c in cubes:
        ax0, ax1 = px + c['x'], px + c['x'] + c['dx']
        ay0, ay1 = py + c['y'], py + c['y'] + c['dy']
        az0, az1 = pz + c['z'], pz + c['z'] + c['dz']
        el = {'name': 'cage_' + c['mat'], 'box_uv': True, 'export': True, 'from': [-ax1, 24 - ay1, az0], 'to': [-ax0, 24 - ay0, az1], 'autouv': 0,
              'color': 0, 'origin': bb_point(pivot), 'uv_offset': list(c['uv']), 'type': 'cube', 'uuid': uid()}
        if c['rot']:
            rx, ry, rz = c['rot']
            o = c['origin']
            el['rotation'] = [-rx, -ry, rz]
            el['origin'] = bb_point((px + o[0], py + o[1], pz + o[2]))
        elements.append(el)
        children['cage'].append(('el', el['uuid']))

    def outline(name):
        return {'uuid': uuids[name], 'isOpen': False, 'children': [outline(n) if k == 'group' else n for (k, n) in children[name]]}

    project = {
        'meta': {'format_version': '5.0', 'model_format': 'modded_entity', 'box_uv': True},
        'name': 'gibbet_cage', 'model_identifier': '', 'modded_entity_entity_class': '', 'modded_entity_version': '1.17', 'modded_entity_flip_y': True,
        'visible_box': [1, 1, 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [], 'timeline_setups': [], 'unhandled_root_fields': {},
        'resolution': {'width': TEX, 'height': TEX_H}, 'elements': elements, 'groups': groups, 'outliner': [outline('root')],
        'textures': [{'path': '', 'name': 'gibbet_cage.png', 'folder': '', 'namespace': '', 'id': '0', 'width': TEX, 'height': TEX_H, 'uv_width': TEX,
                      'uv_height': TEX_H, 'particle': False, 'render_mode': 'default', 'render_sides': 'auto', 'frame_time': 1,
                      'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False, 'visible': True, 'internal': True, 'saved': False,
                      'uuid': uid(), 'source': data_url}],
        'animations': [],
    }
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(project, f, indent='\t')
        f.write('\n')
    # Drawn 1.4x the size it has on the knight's back, so the hazard reads from across the arena.
    with open(out.replace('.bbmodel', '.fray.json'), 'w', encoding='utf-8') as f:
        json.dump({'model_scale': 1.4, 'shadow': 0.5}, f, indent=2)
        f.write(chr(10))
    print(out, len(cubes), 'cubes, texture rows used', used, 'of', TEX_H)


if __name__ == '__main__':
    main()
