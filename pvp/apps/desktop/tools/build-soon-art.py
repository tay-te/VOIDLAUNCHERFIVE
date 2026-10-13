"""Turn the Figma "Coming soon in motion" illustration exports into animation-ready SVG.

Source: Figma `3c0Wgc4ZKFSpm98fWJoARS`, page 52:2, section 122:276. Each scene's
`illustration · *` frame (760 x 420) exported as SVG keeps its layer names as element ids.
This script strips the page chrome, renames layers to classes, and wraps the layers that
carry both an entrance and a loop so the two animations never fight over one property.
The timings live in `src/local/soon.css`, taken from the frame `133:37164` motion spec.

    python tools/build-soon-art.py <dir with modpacks.svg servers.svg quests.svg> <out dir>
"""

import math
import re
import sys
import xml.etree.ElementTree as ET

SVG = 'http://www.w3.org/2000/svg'
ET.register_namespace('', SVG)
Q = '{%s}' % SVG


# Packet travel in five equal steps along each data line, from the Figma keyframes (violet,
# peach, cyan). Offsets are from the packet's resting spot.
PACKET_PATHS = [
    [(18.657, -4.485), (57.249, 10.805), (96.057, 48.737), (126.208, 68.075), (139.035, 66.385)],
    [(23.488, -6.559), (72.073, -8.733), (120.929, -0.405), (158.887, 1.469), (175.035, -2.295)],
    [(2.286, -9.392), (7.015, -34.621), (11.769, -61.62), (15.464, -73.088), (17.035, -72.975)],
]


def fix(s: str) -> str:
    """Figma writes the middle dot as UTF-8 read as Latin-1; undo that."""
    try:
        return s.encode('latin1').decode('utf8')
    except UnicodeError:
        return s


def base(name: str) -> str:
    """Figma de-duplicates repeated names with a `_N` suffix."""
    return re.sub(r'_\d+$', '', fix(name))


def wrap(el, parent, chain):
    """Replace `el` in `parent` by nested <g class=..> wrappers, outermost first.

    `chain` is [(class, {css var: value}), ...]; the element itself ends up innermost.
    """
    idx = list(parent).index(el)
    parent.remove(el)
    outer = inner = None
    for cls, vars_ in chain:
        g = ET.Element(Q + 'g')
        g.set('class', cls)
        if vars_:
            g.set('style', ';'.join(f'--{k}:{v}' for k, v in vars_.items()))
        if outer is None:
            outer = g
        else:
            inner.append(g)
        inner = g
    inner.append(el)
    parent.insert(idx, outer)
    return outer


def named(root, pred):
    """Every (element, parent) whose base layer name satisfies `pred`, document order."""
    out = []

    def walk(n):
        for c in list(n):
            i = c.get('id')
            if i is not None and pred(base(i)):
                out.append((c, n))
            walk(c)

    walk(root)
    return out


def ring_vars(circles):
    """Each ring dot slides to its neighbour's slot (12.86 deg) over 3 s along the ellipse.

    Returns three sub-step offsets per dot so the move follows the arc, not the chord.
    """
    pts = [(float(c.get('cx')), float(c.get('cy'))) for c in circles]
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    rx = max(abs(p[0] - cx) for p in pts)
    ry = max(abs(p[1] - cy) for p in pts)
    step = 2 * math.pi / len(pts)
    ang = [math.atan2((p[1] - cy) / ry, (p[0] - cx) / rx) for p in pts]
    # Figma moves dot 01 by (-21.1, +9.7): pick the rotation direction that matches.
    out = []
    for sign in (1, -1):
        a = ang[0] + sign * step
        dx = cx + rx * math.cos(a) - pts[0][0]
        dy = cy + ry * math.sin(a) - pts[0][1]
        out.append((sign, dx, dy))
    sign = min(out, key=lambda o: abs(o[1] + 21.142) + abs(o[2] - 9.734))[0]
    res = []
    for i, p in enumerate(pts):
        v = {}
        for k in (1, 2, 3):
            a = ang[i] + sign * step * k / 3
            v[f'x{k}'] = round(cx + rx * math.cos(a) - p[0], 2)
            v[f'y{k}'] = round(cy + ry * math.sin(a) - p[1], 2)
        res.append(v)
    return res


def process(scene: str, src: str):
    root = ET.parse(src).getroot()

    def find(n):
        if fix(n.get('id', '')).startswith('illustration'):
            return n
        for c in n:
            x = find(c)
            if x is not None:
                return x
        return None

    ill = find(root)
    defs = root.find(Q + 'defs')

    def has(name):
        return lambda b: b == name

    def op(el):
        return el.get('opacity') or '1'

    # ---- shared -------------------------------------------------------------
    for el, p in named(ill, has('ground · slab')):
        wrap(el, p, [('e-slab', {})])
    for el, p in named(ill, has('shadow · contact')):
        wrap(el, p, [('e-shadow', {})])

    if scene == 'modpacks':
        for el, p in named(ill, has('ring · dotted')):
            circles = [c for c in el if c.tag == Q + 'circle']
            for c, v in zip(circles, ring_vars(circles)):
                c.set('class', 'dot')
                c.set('style', ';'.join(f'--{k}:{val}px' for k, val in v.items()))
            wrap(el, p, [('e-ring', {})])
        cells = named(ill, lambda b: b.startswith('cell · '))
        keyed = []
        for el, p in cells:
            k, i, j = (int(x) for x in re.findall(r'k(\d) i(\d) j(\d)', fix(el.get('id')))[0])
            keyed.append((k, i + j, el, p))
        for n, (_, _, el, p) in enumerate(sorted(keyed, key=lambda t: (t[0], t[1]))):
            wrap(el, p, [('e-cell', {'n': n})])
        for el, p in named(ill, has('badge · verified')):
            wrap(el, p, [('e-badge', {})])
        for i, (el, p) in enumerate(named(ill, lambda b: b.startswith('cube · '))):
            wrap(el, p, [('e-cube', {'i': i}), ('l-orbit', {'i': i})])
        for i, (el, p) in enumerate(named(ill, lambda b: re.fullmatch(r'sparkle \d', b))):
            wrap(el, p, [('e-spark', {'i': i, 'op': op(el)}), ('l-twinkle', {'i': i, 'per': round(2.6 + 0.45 * i, 2)})])

    if scene == 'servers':
        racks = named(ill, lambda b: re.fullmatch(r'rack \d.*', b))
        for n, (rack, rp) in enumerate(racks):
            for k, (el, p) in enumerate(named(rack, lambda b: b in ('led · ok', 'led · cyan', 'led · violet'))):
                per = (3.0, 3.6, 4.2)[k]
                wrap(el, p, [('e-led', {'n': n, 'k': k}), ('s-led', {}), ('l-blink', {'per': per})])
            for el, p in named(rack, has('status strip')):
                wrap(el, p, [('e-strip', {'n': n}), ('s-strip', {})])
            # the rack last, so its children are already wrapped inside it
            wrap(rack, rp, [('e-rack', {'n': n})])
        for el, p in named(ill, has('data · lines')):
            for i, (ln, lp) in enumerate(named(el, lambda b: b.startswith('line · '))):
                for path in ln.iter(Q + 'path'):
                    path.set('pathLength', '1')
                    path.set('class', 'draw')
                    path.set('style', f'--i:{i}')
            wrap(el, p, [('s-lines', {})])
        for el, p in named(ill, has('data · packets')):
            order = {'packet · violet': 0, 'packet · peach': 1, 'packet · cyan': 2}
            for pk, pp in named(el, lambda b: b.startswith('packet · ')):
                i = order[base(pk.get('id'))]
                v = {'i': i}
                for n, (x, y) in enumerate(PACKET_PATHS[i], 1):
                    v[f'x{n}'] = f'{x}px'
                    v[f'y{n}'] = f'{y}px'
                wrap(pk, pp, [('l-packet', v)])
            wrap(el, p, [('s-packets', {})])
        for el, p in named(ill, has('friends')):
            order = {'friend · violet': 0, 'friend · peach': 1, 'friend · cyan': 2}
            for fr, fp in named(el, lambda b: b.startswith('friend · ')):
                i = order[base(fr.get('id'))]
                wrap(fr, fp, [('e-friend', {'i': i}), ('l-bob', {'i': i})])
            wrap(el, p, [('s-friends', {})])
        for el, p in named(ill, has('moon')):
            wrap(el, p, [('e-moon', {}), ('s-moon', {})])
        for i, (el, p) in enumerate(named(ill, lambda b: re.fullmatch(r'star \d', b))):
            wrap(el, p, [('e-spark', {'i': i, 'op': op(el)}), ('l-twinkle', {'i': i, 'per': round(2.8 + 0.5 * i, 2)})])
        for k, (el, p) in enumerate(named(ill, lambda b: re.fullmatch(r'z \d', b))):
            wrap(el, p, [('s-z', {}), ('l-z', {'k': k, 'zo': (0.7, 0.55, 0.4)[k]})])

    if scene == 'quests':
        for n, (el, p) in enumerate(named(ill, lambda b: re.fullmatch(r'step \d', b))):
            wrap(el, p, [('e-step', {'n': n})])
        for n, (el, p) in enumerate(named(ill, has('pip · fill'))):
            wrap(el, p, [('e-pip', {'n': n}), ('s-pip', {'n': n})])
        for el, p in named(ill, has('pedestal')):
            wrap(el, p, [('e-ped', {}), ('s-ped', {})])
        for el, p in named(ill, has('trophy')):
            wrap(el, p, [('e-trophy', {}), ('l-hover', {}), ('l-flare', {})])
        durs = [3.4, 3.8, 4.2, 4.6]
        sway = [(-4, -50), (6, -65), (-8, -80), (4, -50), (-6, -65), (8, -80), (-4, -50), (6, -65)]
        for k, (el, p) in enumerate(named(ill, lambda b: re.fullmatch(r'ember \d', b))):
            dur = durs[k % 4]
            wrap(el, p, [('l-ember', {'k': k, 'dur': f'{dur}s', 'per': f'{round(dur + 0.8 + 0.1 * (k % 4), 2)}s',
                                      'dx': f'{sway[k][0]}px', 'dy': f'{sway[k][1]}px'})])
        for i, (el, p) in enumerate(named(ill, lambda b: b.startswith('gem · '))):
            wrap(el, p, [('e-gem', {'i': i}), ('l-bob', {'i': i})])
        for i, (el, p) in enumerate(named(ill, lambda b: re.fullmatch(r'sparkle \d', b))):
            wrap(el, p, [('e-spark', {'i': i, 'op': op(el)}), ('l-twinkle', {'i': i, 'per': round(2.6 + 0.45 * i, 2)})])

    # ---- emit ---------------------------------------------------------------
    out = ET.Element(Q + 'svg', {'viewBox': '0 0 760 420', 'fill': 'none', 'class': 'soon-art', 'aria-hidden': 'true'})
    if defs is not None:
        out.append(defs)
    g = ET.SubElement(out, Q + 'g')
    for c in list(ill):
        g.append(c)
    for el in out.iter():
        i = el.get('id')
        if i is None:
            continue
        # keep ids only where something references them (gradients, filters, clips)
        if el.tag.replace(Q, '') not in ('linearGradient', 'radialGradient', 'filter', 'clipPath', 'mask', 'pattern'):
            del el.attrib['id']
    xml = ET.tostring(out, encoding='unicode')
    return xml.replace('_141_21895', '_' + scene)


if __name__ == '__main__':
    src, dst = sys.argv[1], sys.argv[2]
    for scene in ('modpacks', 'servers', 'quests'):
        xml = process(scene, f'{src}/{scene}.svg')
        with open(f'{dst}/{scene}.svg', 'w', encoding='utf-8') as f:
            f.write(xml)
        print(scene, len(xml))
