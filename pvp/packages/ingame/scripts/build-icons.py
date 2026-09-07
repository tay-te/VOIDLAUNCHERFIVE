#!/usr/bin/env python3
"""
Bakes the overlay's bar icons into one PNG sprite, inlined as a data URI in
`src/styles/icons.css`.

Why a sprite and not SVG: `design/ultralight-notes.md` rates inline <svg> **[risky]** —
"Ultralight's SVG support is partial — strokes, `stroke-linejoin` and non-scaling strokes are
the usual casualties" — and Lucide is entirely stroke-based, so shipping it as SVG is shipping
the one thing the engine is worst at. The same line gives the answer: a PNG sprite at the
densities actually in use, with `background-position` offsets. Pixels cannot be partially
supported.

Why generated rather than exported: the geometry below **is** Lucide's, transcribed from its
24x24 grid, so the shapes are the real ones rather than an impression of them; and generating
means the sprite is reproducible from source instead of being a binary somebody has to
remember how to rebuild. Run: `python3 scripts/build-icons.py`.

Drawn at 8x and downsampled with LANCZOS, because PIL's ImageDraw has no antialiasing of its
own — at 1x the diagonals of the close X would be a staircase.

White, because a PNG cannot take `currentColor`: the bar's states are opacity steps on top of
it (`.obtn` in overlay.css), which is the same two-step hover the cell-art glyphs had.

The generated CSS is self-contained: it carries the sheet, the cell size, the total sheet
width and one `background-position` rule per icon. Nothing downstream should hardcode an
offset — add an icon here and the rule appears. ORDER IS LOAD-BEARING: cells are addressed by
index, so icons may only ever be appended, never reordered or removed.
"""

import base64, io, math, os
from PIL import Image, ImageDraw

CELL = 48          # sprite cell, in device px: 16 CSS px at the 3x the view rasterises to
CSS = 16           # the cell's size in CSS px, which is what the offsets are expressed in
SS = 8             # supersample factor for the draw
U = 24.0           # Lucide's grid
STROKE = 2.2       # Lucide draws 2; 2.2 holds up at 16px on a dark ground

# Per-icon stroke, only where 2.2 demonstrably closes a gap that has to stay open at 16px.
# Never lower STROKE globally — the plain icons need the weight on a dark ground.
STROKE_FOR = {
    'settings': 1.9,        # gear: hub, body and teeth are three rings inside 24 units
    'keystrokes': 1.7,      # keyboard: the key marks merge into bars at 2.2
    'potion_effects': 2.0,  # flask: the fill line sits 2 units off the base
    'layers': 2.0,          # three stacked planes, 4.3 units apart
    'move': 2.0,            # arrowheads sit right on the axes they point along
    'users': 2.0,           # two figures, one behind the other
    'bed': 2.0,             # headboard, mattress, base line and pillow in one 24-unit box
    'eye': 2.0,             # the pupil sits inside the almond with 4 units to spare
    'reset': 2.0,           # the arrow's L tucks into the ring's own gap
}

# Order is the sprite order and is permanent — see the module docstring.
ICONS = [
    # the bar's own controls (offsets 0-4, referenced from the first build onward)
    'search', 'close', 'grid', 'list', 'back',
    'settings',
    # the mod tiles
    'fps', 'keystrokes', 'cps', 'toggle_sprint', 'crosshair', 'zoom',
    'fullbright', 'hitboxes', 'armor_status', 'potion_effects', 'ping', 'coordinates',
    # quick-palette action rows and the party screen
    'layers', 'sword', 'users', 'box', 'move', 'chevron-down', 'sparkle',
    # the rest of `@void/ui`'s ICON_NAMES, so the sprite covers it exactly and the
    # renderer's name map can be asserted total rather than kept as a list of exceptions
    'check', 'plus', 'star', 'heart', 'eye', 'play', 'reset', 'chevron-right', 'bed',
]


def px(v):
    """Lucide grid units -> supersampled pixels."""
    return v / U * CELL * SS


def line(d, x1, y1, x2, y2, w):
    d.line([(px(x1), px(y1)), (px(x2), px(y2))], fill=255, width=int(round(px(w))))
    # Round caps: Pillow's `joint` only joins, so the ends are drawn as discs.
    r = px(w) / 2.0
    for (x, y) in ((px(x1), px(y1)), (px(x2), px(y2))):
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)


def ring(d, cx, cy, rad, w):
    """`rad` is the OUTER radius: Pillow strokes an ellipse inward from its bounding box.

    Kept exactly as first written because `search` and `zoom` are drawn with it and their
    cells must not shift. New geometry should use `circle`, which is the centred version.
    """
    d.ellipse([px(cx - rad), px(cy - rad), px(cx + rad), px(cy + rad)],
              outline=255, width=int(round(px(w))))


def circle(d, cx, cy, rad, w):
    """A ring whose stroke is centred on `rad`, the way Lucide's `<circle r>` reads."""
    ring(d, cx, cy, rad + w / 2.0, w)


def rrect(d, x, y, w, h, rad, stroke):
    d.rounded_rectangle([px(x), px(y), px(x + w), px(y + h)], radius=px(rad),
                        outline=255, width=int(round(px(stroke))))


def arc(d, cx, cy, rad, a0, a1, w):
    """Stroke centred on `rad`, from `a0` to `a1` clockwise.

    Pillow's angles: 0 deg at 3 o'clock, increasing clockwise because y runs down. Like
    `line`, the ends need their own discs — `ImageDraw.arc` has no round cap.
    """
    out = rad + w / 2.0
    d.arc([px(cx - out), px(cy - out), px(cx + out), px(cy + out)],
          a0, a1, fill=255, width=int(round(px(w))))
    r = px(w) / 2.0
    for a in (a0, a1):
        x, y = px(cx + rad * math.cos(math.radians(a))), px(cy + rad * math.sin(math.radians(a)))
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)


def polyline(d, pts, w, close=False):
    """Round-capped segments, so every vertex reads as a round join."""
    seq = list(pts) + ([pts[0]] if close else [])
    for (x1, y1), (x2, y2) in zip(seq, seq[1:]):
        line(d, x1, y1, x2, y2, w)


def disc(d, cx, cy, rad):
    """Solid circle. Same caveat as `polygon` — fills, not strokes."""
    d.ellipse([px(cx - rad), px(cy - rad), px(cx + rad), px(cy + rad)], fill=255)


def polygon(d, pts):
    """Solid fill. Only for marks that a stroke would turn to mush at 16px."""
    d.polygon([(px(x), px(y)) for (x, y) in pts], fill=255)


def polar(cx, cy, rad, deg):
    a = math.radians(deg)
    return cx + rad * math.cos(a), cy + rad * math.sin(a)


def draw(name, d):
    s = STROKE_FOR.get(name, STROKE)

    if name == 'search':          # lucide `search`
        ring(d, 11, 11, 8, s)
        line(d, 21, 21, 16.65, 16.65, s)
    elif name == 'close':         # lucide `x`
        line(d, 18, 6, 6, 18, s)
        line(d, 6, 6, 18, 18, s)
    elif name == 'grid':          # lucide `layout-grid`
        for (x, y) in ((3, 3), (14, 3), (3, 14), (14, 14)):
            rrect(d, x, y, 7, 7, 1, s)
    elif name == 'list':          # lucide `menu`
        for y in (6, 12, 18):
            line(d, 4, y, 20, y, s)
    elif name == 'back':          # lucide `chevron-left`
        line(d, 15, 18, 9, 12, s)
        line(d, 9, 12, 15, 6, s)

    elif name == 'settings':
        # DELIBERATE SUBSTITUTION. Lucide's `settings` is a 12-tooth gear whose outline is one
        # continuous path of 24 arcs; transcribed at 16px it is a fuzzy disc. This is a drawn
        # gear instead — hub, body, 8 radial teeth — because the acceptance test is "reads as a
        # gear at 16px on #191A1C", not path fidelity. The three radii are tuned so the gap
        # between hub and body survives the downsample: 1.6 CSS px of black.
        circle(d, 12, 12, 7.35, s)     # body
        circle(d, 12, 12, 3.1, s)      # hub
        for k in range(8):
            a = k * 45
            polyline(d, [polar(12, 12, 6.9, a), polar(12, 12, 10.3, a)], 2.4)

    elif name == 'fps':           # lucide `activity`
        polyline(d, [(22, 12), (18, 12), (15, 21), (9, 3), (6, 12), (2, 12)], s)

    elif name == 'keystrokes':
        # lucide `keyboard`, simplified: its ten 1-unit key dots are sub-pixel at 16px and
        # downsample to a grey haze that fills the case. Kept: a 3-key row, a staggered 2-key
        # row and the spacebar. The gaps are 2.7 units (1.8 CSS px) because anything under
        # ~1.5 px of black closes up and the rows read as solid bars.
        rrect(d, 2, 4, 20, 16, 2.5, s)
        for x in (6.2, 12.0, 17.8):
            line(d, x - 0.7, 8.6, x + 0.7, 8.6, s)
        for x in (9.1, 14.9):
            line(d, x - 0.7, 12.4, x + 0.7, 12.4, s)
        line(d, 8.4, 16.2, 15.6, 16.2, s)

    elif name == 'cps':           # lucide `mouse`
        rrect(d, 5, 2, 14, 20, 7, s)
        line(d, 12, 6, 12, 12, s)

    elif name == 'toggle_sprint':  # lucide `chevrons-right`
        polyline(d, [(6, 17), (11, 12), (6, 7)], s)
        polyline(d, [(13, 17), (18, 12), (13, 7)], s)

    elif name == 'crosshair':     # lucide `crosshair`
        circle(d, 12, 12, 10, s)
        line(d, 22, 12, 18, 12, s)
        line(d, 6, 12, 2, 12, s)
        line(d, 12, 6, 12, 2, s)
        line(d, 12, 22, 12, 18, s)

    elif name == 'zoom':          # lucide `zoom-in` — `search` plus a `+` in the lens
        ring(d, 11, 11, 8, s)
        line(d, 21, 21, 16.65, 16.65, s)
        line(d, 8, 11, 14, 11, s)
        line(d, 11, 8, 11, 14, s)

    elif name == 'fullbright':    # lucide `sun`
        # Rays at 45 deg steps, r 7.3 -> 10.2 (Lucide's axis rays are 2 -> 4.9 and 19.1 -> 22).
        # The disc is r 3.7 rather than 4 so the ray gap stays open after the downsample.
        circle(d, 12, 12, 3.7, s)
        for k in range(8):
            a = k * 45
            polyline(d, [polar(12, 12, 7.3, a), polar(12, 12, 10.2, a)], s)

    elif name == 'hitboxes':      # lucide `scan`
        # Lucide's brackets turn each corner with a 2-unit radius. At 48px that radius is 4px
        # and the 2.2 stroke fills it, so the corners are mitred instead — a clean angle reads
        # better than a blob, and the round caps soften the outside anyway.
        for (cx, cy, dx, dy) in ((3, 3, 1, 1), (21, 3, -1, 1), (21, 21, -1, -1), (3, 21, 1, -1)):
            polyline(d, [(cx, cy + 4.5 * dy), (cx, cy), (cx + 4.5 * dx, cy)], s)

    elif name == 'armor_status':  # lucide `shield`
        # The crest, as a polyline sampled off Lucide's bezier: flat-ish shoulders, straight
        # flanks, then the taper to the point.
        polyline(d, [(12, 2.3), (19.8, 5.7), (19.8, 12.3), (19.3, 15.0), (18.2, 17.1),
                     (16.6, 18.9), (14.5, 20.4), (12, 21.7), (9.5, 20.4), (7.4, 18.9),
                     (5.8, 17.1), (4.7, 15.0), (4.2, 12.3), (4.2, 5.7)], s, close=True)

    elif name == 'potion_effects':  # lucide `flask-conical`
        line(d, 8.5, 2.2, 15.5, 2.2, s)     # the lip
        line(d, 10, 2.2, 10, 9.3, s)
        line(d, 14, 2.2, 14, 9.3, s)
        line(d, 10, 9.3, 4.9, 20.4, s)
        line(d, 14, 9.3, 19.1, 20.4, s)
        line(d, 4.9, 20.4, 19.1, 20.4, s)
        line(d, 6.8, 16.2, 17.2, 16.2, s)   # the fill line, where the flask is that wide

    elif name == 'ping':
        # DELIBERATE SUBSTITUTION. Lucide's `signal` is four concentric ascending arcs; at 16px
        # the three inner ones fall inside a 2px band and read as one smear. Four ascending
        # bars carry the same meaning and stay four distinct marks at this size.
        for x, top in ((4, 16.5), (9, 13.0), (14, 9.5), (19, 6.0)):
            line(d, x, 20, x, top, s)

    elif name == 'coordinates':   # lucide `map-pin`
        # Teardrop: the bowl is an arc of r 7 about (12,10), and the flanks are its tangents
        # from the point at (12,21) — tangent length sqrt(11^2-7^2), touching at 90 +/- 50.5 deg,
        # so the straights leave the arc without a kink.
        circle(d, 12, 10, 3, s)
        a = math.degrees(math.acos(7.0 / 11.0))       # 50.5
        arc(d, 12, 10, 7, 90 + a, 360 + 90 - a, s)
        for end in (90 - a, 90 + a):
            line(d, *polar(12, 10, 7, end), 12, 21, s)

    elif name == 'layers':
        # lucide `layers`, simplified: its three planes are closed parallelograms whose lower
        # edges sit 4.2 units apart. Keeping all three closed puts two strokes 1.3px apart at
        # 16px, so only the top plane is closed and the two below are the chevrons that read.
        polyline(d, [(12, 2.1), (21.5, 6.8), (12, 11.5), (2.5, 6.8)], s, close=True)
        for y in (11.4, 15.7):
            polyline(d, [(2.5, y), (12, y + 4.7), (21.5, y)], s)

    elif name == 'sword':         # lucide `sword`
        polyline(d, [(14.5, 17.5), (3, 6), (3, 3), (6, 3), (17.5, 14.5)], s)
        line(d, 13, 19, 19, 13, s)
        line(d, 16, 16, 20, 20, s)

    elif name == 'users':
        # DELIBERATE SUBSTITUTION. Lucide composes `users` from one whole figure and a second
        # one clipped to a half head plus a quarter shoulder, which only works because the
        # fragments are read against the whole figure. At 16px the fragments are 2px slivers:
        # rendered, it read as a face beside a bracket, not as two people. Two equal figures
        # side by side instead — the same meaning, and both halves survive the downsample.
        # Lucide's composition (whole figure + smaller one behind right), but the figure behind
        # is a head and a shoulder sweep rather than Lucide's two clipped fragments — a half
        # head and a quarter shoulder are 2px slivers at 16px and read as a stray bracket.
        # Everything else here is fighting one failure mode: any two same-size heads sitting
        # level above a single continuous curve read as a face. Hence the size and height
        # offset between the heads, and the front body kept under the front head only.
        circle(d, 8.6, 9.2, 3.1, s)                     # front figure
        arc(d, 8.6, 21.4, 5.6, 212, 328, s)
        for ex in (8.6 - 4.75, 8.6 + 4.75):
            line(d, ex, 18.43, ex, 21.0, s)
        circle(d, 18.2, 5.8, 2.4, s)                    # the figure behind
        arc(d, 18.2, 16.0, 4.6, 250, 332, s)

    elif name == 'box':           # lucide `box`
        polyline(d, [(12, 2.2), (20.8, 7.1), (20.8, 16.9), (12, 21.8), (3.2, 16.9), (3.2, 7.1)],
                 s, close=True)
        polyline(d, [(3.4, 7.2), (12, 12), (20.6, 7.2)], s)
        line(d, 12, 12, 12, 21.8, s)

    elif name == 'move':
        # lucide `move`. The axes are pulled back to 3.4 so the arrowheads are not drawn on top
        # of the shaft they terminate — at 16px an overlapping head fills in solid.
        line(d, 12, 3.4, 12, 20.6, s)
        line(d, 3.4, 12, 20.6, 12, s)
        polyline(d, [(9, 5), (12, 2), (15, 5)], s)
        polyline(d, [(19, 9), (22, 12), (19, 15)], s)
        polyline(d, [(15, 19), (12, 22), (9, 19)], s)
        polyline(d, [(5, 9), (2, 12), (5, 15)], s)

    elif name == 'chevron-down':  # lucide `chevron-down`
        polyline(d, [(6, 9), (12, 15), (18, 9)], s)

    elif name == 'sparkle':
        # lucide `sparkles` reduced to its one centred four-point star, and FILLED: stroked,
        # the concave flanks are 0.7px apart at 16px and it reads as a blur. This is the VOID
        # watermark mod's icon, so it has to hold as a mark.
        # The waist is 2.2 off centre rather than Lucide's 1.5: at 1.5 the four arms are one
        # pixel wide at 16px and the whole thing reads as a `+`. 2.2 keeps the flanks concave
        # (2.9 rounds it off into a diamond) while giving the arms enough mass to be a star.
        i = 2.2
        polygon(d, [(12, 3), (12 + i, 12 - i), (21, 12), (12 + i, 12 + i),
                    (12, 21), (12 - i, 12 + i), (3, 12), (12 - i, 12 - i)])

    elif name == 'check':         # lucide `check`
        polyline(d, [(20, 6), (9, 17), (4, 12)], s)

    elif name == 'plus':          # lucide `plus`
        line(d, 5, 12, 19, 12, s)
        line(d, 12, 5, 12, 19, s)

    elif name == 'star':
        # FILLED, for the reason `sparkle` is: the five points of a stroked star are 1px wide
        # at 16px and the outline closes over its own middle. Outer r 9, inner r 3.7, one
        # point up (screen angles run clockwise from 3 o'clock, so up is -90).
        pts = []
        for k in range(5):
            pts.append(polar(12, 12, 9.0, -90 + 72 * k))
            pts.append(polar(12, 12, 3.7, -54 + 72 * k))
        polygon(d, pts)

    elif name == 'heart':
        # FILLED, same reason. Built as a union rather than a path: two lobe discs plus the
        # triangle from their shared diameter down to the point. The lobes are 8 units apart
        # so the top notch reaches y 5.9 — closer together and the notch vanishes at 16px.
        for cx in (8.0, 16.0):
            disc(d, cx, 8.2, 4.6)
        polygon(d, [(3.4, 8.2), (20.6, 8.2), (12, 20.6)])

    elif name == 'eye':
        # The almond is two arcs on the same chord (2,12)-(22,12), bulging to y 5 and y 19.
        # Chord half-width 10 and sagitta 7 put both centres on x=12 at r 10.64.
        r = (7.0 ** 2 + 10.0 ** 2) / (2 * 7.0)
        a = math.degrees(math.asin(7.0 / r))          # 20 deg off the horizontal
        arc(d, 12, 5 + r, r, 180 + a, 360 - a, s)     # upper lid
        arc(d, 12, 19 - r, r, a, 180 - a, s)          # lower lid
        circle(d, 12, 12, 3, s)                       # pupil

    elif name == 'play':          # lucide `play`, filled
        polygon(d, [(8, 5.5), (19, 12), (8, 18.5)])

    elif name == 'reset':
        # NOT a Lucide name — this is `@void/ui`'s own `PATHS.reset`, transcribed from
        # `packages/ui/src/components/Icon.tsx`: `M3 12a9 9 0 1 0 3-6.7` plus `M3 4v5h5`.
        # The arc is r 9 about (12,12) — both endpoints are exactly 9 out — swept the long way
        # round, leaving the ring open between 180 and 228 deg. The L sits in that gap.
        end = math.degrees(math.atan2(-6.7, -6.0)) % 360   # 228.2
        arc(d, 12, 12, 9, end, 180 + 360, s)
        polyline(d, [(3, 4), (3, 9), (8, 9)], s)

    elif name == 'chevron-right':  # lucide `chevron-right` — the mirror of `back`
        polyline(d, [(9, 6), (15, 12), (9, 18)], s)

    elif name == 'bed':
        # `@void/ui`'s `PATHS.bed`. Its two 1- and 2-unit corner radii are 2 and 4 device px
        # against a 2.0 stroke, so they are mitred for the reason `hitboxes` is.
        line(d, 2, 8, 2, 19, s)                                    # headboard
        polyline(d, [(2, 12), (20.4, 12), (22, 13.6), (22, 19)], s)  # mattress
        line(d, 2, 17, 22, 17, s)                                  # base
        polyline(d, [(6.5, 12), (6.5, 9.5), (12, 9.5), (12, 12)], s)  # pillow

    else:
        raise SystemExit('unknown icon ' + name)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    sheet = Image.new('L', (CELL * len(ICONS), CELL), 0)
    for i, name in enumerate(ICONS):
        big = Image.new('L', (CELL * SS, CELL * SS), 0)
        draw(name, ImageDraw.Draw(big))
        sheet.paste(big.resize((CELL, CELL), Image.LANCZOS), (i * CELL, 0))

    # White pixels, alpha from the mask: one drawing colour, opacity carries the states.
    rgba = Image.merge('RGBA', (Image.new('L', sheet.size, 255),) * 3 + (sheet,))
    buf = io.BytesIO()
    rgba.save(buf, format='PNG', optimize=True)
    data = base64.b64encode(buf.getvalue()).decode('ascii')

    css = ['/* GENERATED by scripts/build-icons.py — do not edit. Re-run that script instead.',
           ' *',
           ' * One sprite, %d icons of %dpx displayed at %dpx:' % (len(ICONS), CELL, CSS),
           ' * %s.' % ', '.join(ICONS),
           ' * Lucide geometry, rasterised — see the script for why this is not an <svg>.',
           ' *',
           ' * The offsets live here, not in overlay.css: with %d cells a hardcoded' % len(ICONS),
           ' * `background-position` is one appended icon away from showing its neighbour.',
           ' */',
           '',
           ':root {',
           '  --icon-sheet: url(data:image/png;base64,%s);' % data,
           '  --icon-cell: %dpx;' % CELL,
           '  --icon-sheet-w: %dpx;' % (CSS * len(ICONS)),
           '}',
           '']
    css += ['.oicon--%s { background-position: %s 0; }'
            % (n, '-%dpx' % (i * CSS) if i else '0')
            for i, n in enumerate(ICONS)]
    css += ['']

    out = os.path.join(here, '..', 'src', 'styles', 'icons.css')
    with open(out, 'w') as f:
        f.write('\n'.join(css))
    print('wrote %s — %d icons, %dx%d, %d bytes of PNG, %d of base64'
          % (out, len(ICONS), sheet.width, sheet.height, buf.tell(), len(data)))


if __name__ == '__main__':
    main()
