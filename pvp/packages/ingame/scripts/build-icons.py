#!/usr/bin/env python3
"""
Bakes the overlay's icon set into one PNG sprite, inlined as a data URI in
`src/styles/icons.css`.

Why a sprite and not SVG: `design/ultralight-notes.md` rates inline <svg> **[risky]** —
"Ultralight's SVG support is partial — strokes, `stroke-linejoin` and non-scaling strokes are
the usual casualties" — so shipping a stroke-based set as SVG is shipping the one thing the
engine is worst at. The same line gives the answer: a PNG sprite at the densities actually in
use, with `background-position` offsets. Pixels cannot be partially supported.

Drawn at 8x and downsampled with LANCZOS, because PIL's ImageDraw has no antialiasing of its
own — at 1x the diagonals of the close X would be a staircase.

White, because a PNG cannot take `currentColor`: the bar's states are opacity steps on top of
it (`.obtn` in overlay.css), which is the same two-step hover the cell-art glyphs had.

The generated CSS is self-contained: it carries the sheet, the cell size, the total sheet
width and one `background-position` rule per icon. Nothing downstream should hardcode an
offset — add an icon here and the rule appears. ORDER IS LOAD-BEARING: cells are addressed by
index, so icons may only ever be appended, never reordered or removed.

Run: `python3 scripts/build-icons.py`.


## Lucide, and what happened to it

This set began as Lucide transcribed — its 24-unit grid, its 2px stroke, its geometry read off
the real paths. That was the right start and it is no longer what this is, for a reason worth
writing down rather than rediscovering.

Lucide is drawn for ~20-24px in a general product. `design/quiet-cell-system.md` is a
different language — flat, monochrome, geometric, four opaque fill steps, cells at a 30%
corner radius — and these render at **13 to 22px**, mostly 16. Every place the two disagreed,
the seam showed as a hand patch: nine per-icon stroke overrides between 1.7 and 2.2 (a 29%
spread in weight across one sheet), `hitboxes`' brackets lengthened by hand, `fps`' hub dot
deleted because it merged with the needle, and `toggle_sprint` shipping a drawing whose own
name said something else. Four separate cases of the source set not surviving contact with
this system's size.

So the vocabulary is still Lucide's — which shape means which thing, because deviating from
the convention is pure cost to a reader — and the **drawing is not**. Every glyph below is
constructed to the standard in the next section, at this size, for this system. That was
affordable because this file never imported anything: it has always drawn from primitives, so
"purpose-drawn" is a decision about which rules the drawing obeys, not about tooling.

Where a Lucide shape is still the right answer at this size it is kept and says so. Where it
is not, the substitution is named along with what was measured.


## The construction standard

Every number here was set by rendering at 16px on `#191A1C` and looking, not by theory. The
calibration is reproducible: draw the form at 48px, LANCZOS to 16, composite on the card fill.

```
GRID        24 units. Cell 48 device px. Judged at a 16px render (0.667 px/unit), which is
            the worst case in play: the CPU-fallback path rasterises 16 CSS px into ~18.7
            device px, and the GL path into ~37.5. A glyph that holds at 16 holds everywhere.
STROKE      2.5u, everywhere, with no per-icon exceptions. Below 2.4 a white stroke on this
            ground gamma-erodes to grey (measured across 2.0-2.8); above 2.6 a rounded corner
            at the radius rule below fills solid.
CAPS/JOINS  round, all of them. `polyline` draws a disc at every vertex, so a join and a
            terminal are the same object.
BOX         ink lives in an 18u optical box centred on (12,12):
              rectangular / orthogonal forms   span ~17
              circular forms                   diameter ~17.6 (a circle reads smaller than a
                                               square of the same span)
              diagonal-dominant forms          span ~16.5
            Full-bleed is reserved for forms that *are* a frame or a radial burst —
            `hitboxes`, `crosshair`, `fullbright`, `keystrokes` — where the box is the shape.
            This is the single biggest change on the sheet. Before it, `close` occupied 12
            units and `armor_status` 19.4, so the X read as a small mark beside a heavy one
            for no reason a player could name.
CORNERS     a rectangle's radius is **30% of its shorter side** — §3's cell rule, quoted, so
            the icons rhyme with the tiles, the toggles and the meters rather than inventing a
            second corner idea. Floored at the stroke, which means a stroked rectangle under
            ~8.3u on its short side cannot take the rule and must not be drawn: that is why
            `hitboxes` and `bed` mitre, and why `grid` and `keystrokes` are cells rather than
            little stroked boxes.
APERTURE    >= 3.0u of clear ground between the inner edges of two strokes that must read as
            separate marks. Measured: at 1.5u two parallel strokes are one grey band, at 2.5u
            the seam is a dim smear, at 3.0u it is black. Two relaxations, both measured:
              2.6u  for a repeated parallel rhythm of three or more (`list`, `ping`,
                    `keystrokes`) — the repetition carries the separation the gap cannot;
              1.9u  for a mark centred inside a ring (`zoom`'s cross), where container and
                    content share no direction at any point of approach.
            Strokes *meant* to meet are joins, not apertures, and the rule does not govern
            them: a needle and its dial, a pillow and its mattress, a button seam and its case.
SMALL RINGS a stroked circle below r 3.5 has no interior at this size — measured, the hole
            dies between r 3.0 and r 2.5 — so anything smaller is drawn as a disc. That is a
            size rule, not a taste one, and it is why `fullbright`, `crosshair`, `coordinates`
            and `eye` carry solid centres.
DETAIL      at most four ideas per glyph; a radial repeat counts as one. A mark under 3u in
            its long dimension is not a mark, it is noise — which is what killed Lucide's ten
            1-unit key dots, its four concentric signal arcs and its clipped second figure.
FILL        **the set is stroked.** Four cells are not, and each states why:
              `grid`, `keystrokes` — the subject is literally a discrete square unit, so it is
                drawn with the design system's own atom (§3's cell: a square at 30% radius,
                filled). Two atoms, not two idioms.
              `play`   — a solid triangle *is* the transport mark; a stroked one is a warning.
              `sparkle` — measured: a four-point star cannot be stroked here. Its arms meet at
                a point rather than enclosing anything, so the waist needed to keep the flanks
                concave (>= 3.9u) is the waist at which it stops reading as a star and becomes
                a rounded diamond. Rendered both.
            `star` and `heart` were filled and are not any more: both stroke cleanly at this
            size, and as fills they were the two heaviest marks on a sheet of line drawings.
```

The mod set is a closed comparison — thirteen glyphs seen in one column — so the last rule is
that no two of them may share a silhouette. That is why `crosshair` is a reticle and `zoom`
keeps the lens, why `keystrokes` gave up its case (three rounded rectangles among thirteen
mods was one too many), and why only `search` and `zoom` are magnifiers, at sizes and in
places that never appear together.
"""

import base64, io, math, os
from PIL import Image, ImageDraw

CELL = 48          # sprite cell, in device px: 16 CSS px at the 3x the view rasterises to
CSS = 16           # the cell's size in CSS px, which is what the offsets are expressed in
SS = 8             # supersample factor for the draw
U = 24.0           # the drawing grid
STROKE = 2.5       # one weight for the whole sheet — see "The construction standard" above

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
    # appended, never inserted — see the module docstring
    'watermark',
    'clock',
]


def px(v):
    """Grid units -> supersampled pixels."""
    return v / U * CELL * SS


def line(d, x1, y1, x2, y2, w):
    d.line([(px(x1), px(y1)), (px(x2), px(y2))], fill=255, width=int(round(px(w))))
    # Round caps: Pillow's `joint` only joins, so the ends are drawn as discs.
    r = px(w) / 2.0
    for (x, y) in ((px(x1), px(y1)), (px(x2), px(y2))):
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)


def ring(d, cx, cy, rad, w):
    """`rad` is the OUTER radius: Pillow strokes an ellipse inward from its bounding box.

    Kept because it is the honest primitive; new geometry should use `circle`, which is the
    centred version and the one the standard's radii are expressed in.
    """
    d.ellipse([px(cx - rad), px(cy - rad), px(cx + rad), px(cy + rad)],
              outline=255, width=int(round(px(w))))


def circle(d, cx, cy, rad, w):
    """A ring whose stroke is centred on `rad`, the way an SVG `<circle r>` reads."""
    ring(d, cx, cy, rad + w / 2.0, w)


def rrect(d, x, y, w, h, rad, stroke):
    d.rounded_rectangle([px(x), px(y), px(x + w), px(y + h)], radius=px(rad),
                        outline=255, width=int(round(px(stroke))))


def cell(d, cx, cy, size):
    """§3's cell primitive: a filled square at a 30% corner radius, centred on (cx, cy).

    The design system's atom. Used where the subject is itself a discrete square unit — a
    layout tile, a keycap — and nowhere else; see FILL in the standard.
    """
    d.rounded_rectangle([px(cx - size / 2), px(cy - size / 2),
                         px(cx + size / 2), px(cy + size / 2)],
                        radius=px(size * 0.30), fill=255)


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
    """Solid circle. The only legal centre for anything under r 3.5 — see SMALL RINGS."""
    d.ellipse([px(cx - rad), px(cy - rad), px(cx + rad), px(cy + rad)], fill=255)


def polygon(d, pts):
    """Solid fill. Four cells only — see FILL in the standard."""
    d.polygon([(px(x), px(y)) for (x, y) in pts], fill=255)


def polar(cx, cy, rad, deg):
    a = math.radians(deg)
    return cx + rad * math.cos(a), cy + rad * math.sin(a)


def scaled(pts, k, cx=12.0, cy=12.0):
    """Scale a point list about the cell centre. How a good outline is brought to the box."""
    return [(cx + (x - cx) * k, cy + (y - cy) * k) for (x, y) in pts]


# The shield's crest, sampled off Lucide's bezier: flat-ish shoulders, straight flanks, then
# the taper to the point. Kept at full fidelity and scaled, rather than redrawn — the outline
# is right, only its size was wrong (19.4u tall against a 12u `close`).
SHIELD = [(12, 2.3), (19.8, 5.7), (19.8, 12.3), (19.3, 15.0), (18.2, 17.1), (16.6, 18.9),
          (14.5, 20.4), (12, 21.7), (9.5, 20.4), (7.4, 18.9), (5.8, 17.1), (4.7, 15.0),
          (4.2, 12.3), (4.2, 5.7)]


def draw(name, d):
    s = STROKE

    # ---------------------------------------------------------------- the bar's controls

    if name == 'search':          # lucide `search`
        # Lens 13.2u across with the handle carrying the mark to 21.5 on the diagonal. The
        # only other magnifier is `zoom`, which keeps the same lens and adds a cross; they
        # are a deliberate pair (search / zoom-in) and never appear in the same list.
        circle(d, 10.4, 10.4, 6.6, s)
        line(d, 15.6, 15.6, 20.2, 20.2, s)

    elif name == 'close':         # lucide `x`
        # Span 16.2, up from 12. At 12 this was the smallest mark on the sheet and read as a
        # weak X beside a full-height shield; the X's optical size *is* its span, and the
        # calibration strip put a 2.5-stroke diagonal at 16-17 to match an orthogonal 17.
        line(d, 20.1, 3.9, 3.9, 20.1, s)
        line(d, 3.9, 3.9, 20.1, 20.1, s)

    elif name == 'grid':
        # §3's cell, four times. WAS four stroked 7u boxes, whose interiors are 2.6u — under
        # the aperture floor, so at 13px they close into blobs with a dark speck. Cells are
        # what a layout grid is made of, and they are the design system's own atom, so this
        # is the vocabulary rather than an exception to it. 6.4u cells on a 4.6u gutter.
        for cx in (7.5, 16.5):
            for cy in (7.5, 16.5):
                cell(d, cx, cy, 6.4)

    elif name == 'list':          # lucide `menu`
        for y in (5.8, 12.0, 18.2):
            line(d, 4.0, y, 20.0, y, s)

    elif name == 'back':          # lucide `chevron-left`
        polyline(d, [(15.0, 4.8), (8.4, 12.0), (15.0, 19.2)], s)

    elif name == 'settings':
        # DELIBERATE SUBSTITUTION, rebuilt. Lucide's `settings` is a 12-tooth gear whose
        # outline is one continuous path of 24 arcs; at 16px it is a fuzzy disc.
        #
        # The drawn gear that replaced it was three concentric rings — hub, body, teeth — and
        # the hub-to-body gap was 1.75u, well under the aperture floor: rendered, the middle
        # of the gear was a grey smear with a speck in it, and at 13px a solid blob.
        #
        # A gear at this size is **one ring and its teeth**, with the hub being the ring's own
        # hole. r 5.4 leaves an 8.3u hole (5.5px of black at 16); eight teeth to r 8.4 leave a
        # 4.1u gap between them at the tips. Both are clear at 13px, which the three-ring
        # version never was.
        circle(d, 12, 12, 5.4, s)
        for k in range(8):
            a = k * 45
            polyline(d, [polar(12, 12, 5.4, a), polar(12, 12, 8.4, a)], s)

    # ---------------------------------------------------------------- the mods

    elif name == 'fps':           # lucide `gauge`
        # A speedometer: fps is a rate, and a closed form carries the same optical weight as
        # `armor_status` and `cps` beside it. (It was `activity`, the ECG pulse — the lightest
        # mark on the sheet and, worse, a word rather than a measurement.)
        #
        # Drawn WITHOUT Lucide's hub dot: at 16px the dot and the needle's root merge into a
        # blob that closes the dial's centre. The needle stops 1u short of the arc, which is a
        # join and not an aperture — a needle is meant to point at its dial.
        arc(d, 12, 13.6, 8.0, 150, 390, s)
        line(d, 12, 13.6, 15.92, 9.40, s)

    elif name == 'keystrokes':
        # DELIBERATE SUBSTITUTION. Lucide's `keyboard` is a case around ten 1-unit key dots;
        # they are sub-pixel here and downsample to a haze that fills the case. The simplified
        # case that replaced it kept three rows of marks on 2.7u gaps and needed its stroke
        # dropped to 1.7 to stay open — the lightest weight on the sheet, for a glyph sitting
        # beside a shield.
        #
        # The case cannot be saved by tuning: a 2.5 stroke leaves ~10u of interior, and one
        # centred bar is all that fits legally. "Rounded box with a minus in it" is not a
        # keyboard, and it was the third rounded rectangle among thirteen mods.
        #
        # So the keys themselves, as §3 cells: a row of three caps over a spacebar, which is
        # what the mod draws in its own tile. The W-over-ASD cluster was rendered first and
        # rejected — at 16px four cells on a cross read as scattered dots, and the row plus a
        # bar is the arrangement that says keyboard rather than pattern. It is also the shape
        # that cannot be mistaken for `grid`'s 2x2 at a glance.
        for k in (-1, 0, 1):
            cell(d, 12 + k * 7.9, 7.4, 5.4)
        d.rounded_rectangle([px(4.3), px(13.4), px(19.7), px(19.2)], radius=px(1.74), fill=255)

    elif name == 'cps':           # lucide `mouse`
        # The seam floats 2.1u below the nose, which is under the aperture floor and stays
        # there deliberately: attached, this glyph is a **power button** — a capsule with a
        # line running out of its top edge is the most recognisable symbol in consumer
        # electronics, and it was rendered before it was rejected. A T of seam plus a button
        # split was rendered too and reads as a face at 13px. Two marks, and the tick stays
        # clear of the case.
        rrect(d, 5.4, 4.0, 13.2, 16.0, 5.8, s)
        line(d, 12, 8.2, 12, 12.2, s)

    elif name == 'toggle_sprint':
        # THE SET'S ONE KNOWN FAILURE, closed. This cell drew `chevrons-right` while
        # `MOD_ICONS` called it `footprints` — the drawing and the name disagreed, and neither
        # read as the mod. `>>` is the skip control in every product a player uses, so in a
        # list of mods it read as chrome.
        #
        # Lucide's `footprints` was measured before it was given up: at 16px, stroked, the two
        # soles close into hollow rings and the toe pads into dots (two lowercase o-with-a-dot,
        # not feet); filled, four blobs. A single enlarged sole is a lozenge with a dot. The
        # arithmetic says why — two soles need four marks in 24 units and none of them can
        # clear 3u of its neighbour.
        #
        # A bolt is speed, it is one mark, it holds at 13px, and it carries the weight of the
        # shield next to it. `@void/ui` was renamed with it: `MOD_ICONS.toggle_sprint` is
        # `bolt` now, so the sheet and the kit say the same thing for the first time.
        polyline(d, [(14.8, 2.8), (6.2, 13.2), (11.4, 13.2), (9.2, 21.2), (17.8, 10.8),
                     (12.6, 10.8)], s, close=True)

    elif name == 'crosshair':
        # A reticle: ring plus a solid centre. Lucide's `crosshair` is a ring with four spokes
        # crossing *into* it — at 16px the spokes weld to the ring and the whole thing reads as
        # a wheel, and the geometry says it cannot be fixed: an outward tick needs the ring's
        # outer edge plus 3u plus its own length, which is more radius than the cell has.
        #
        # The alternative measured was the bare cross-with-a-gap, which is literally what the
        # mod draws — rejected because at 16px it is a `+` with a hole, and `plus` is on this
        # same sheet. The ring is the mark that cannot be mistaken for anything else here.
        circle(d, 12, 12, 8.5, s)
        disc(d, 12, 12, 2.1)

    elif name == 'zoom':          # lucide `zoom-in` — `search`'s lens plus a cross
        # The cross clears the lens wall by 1.9u, which is the ring relaxation in the standard
        # and the only place on the sheet that uses it: a cross inside a circle approaches the
        # wall at four points, at right angles to it, and reads as contained rather than as
        # merged. Rendered against the 3.0u version, which needs a lens too small to be one.
        circle(d, 10.4, 10.4, 6.6, s)
        line(d, 15.6, 15.6, 20.2, 20.2, s)
        line(d, 7.2, 10.4, 13.6, 10.4, s)
        line(d, 10.4, 7.2, 10.4, 13.6, s)

    elif name == 'fullbright':    # lucide `sun`
        # Lucide's sun leaves 1.1u between its disc and its rays, which is 0.73px here and the
        # reason this cell read as a spider. The centre is a disc rather than a ring (SMALL
        # RINGS: r 2.3 has no interior at this size), which buys the gap back: 2.85u clear,
        # with rays long enough — 2.6u — to read as radiating rather than as ticks.
        disc(d, 12, 12, 2.3)
        for k in range(8):
            a = k * 45
            polyline(d, [polar(12, 12, 6.4, a), polar(12, 12, 9.0, a)], s)

    elif name == 'hitboxes':      # lucide `scan`
        # Corner brackets: the bounding-box mark in every tool that draws one, which is what a
        # hitbox is. Mitred rather than radiused — a 2u corner radius against a 2.5 stroke
        # fills solid (CORNERS), and a clean angle reads better than a blob at this size.
        #
        # Arms of 5.8 rather than Lucide's 4.5. At 4.5 the four brackets leave a 15u hole in a
        # 24u box and the cell is the airiest on the sheet; at 5.8 the gaps are 3.1u — the eye
        # closes them into a box, and the aperture is still legal, which the 6.6 the previous
        # pass reached for was not.
        for (cx, cy, dx, dy) in ((3.6, 3.6, 1, 1), (20.4, 3.6, -1, 1),
                                 (20.4, 20.4, -1, -1), (3.6, 20.4, 1, -1)):
            polyline(d, [(cx, cy + 5.8 * dy), (cx, cy), (cx + 5.8 * dx, cy)], s)

    elif name == 'armor_status':  # lucide `shield`
        # The outline is Lucide's and was always right. It was 19.4u tall on a sheet whose
        # smallest mark was 12, which is the whole of what made it the heaviest thing in the
        # column; at 17.8 it sits level with its neighbours without losing a line.
        polyline(d, scaled(SHIELD, 0.918), s, close=True)

    elif name == 'potion_effects':  # lucide `flask-conical`
        # Every gap here is derived rather than eyeballed, because this glyph is the sheet's
        # most crowded and it is the one that had its stroke cut to 2.0 to survive. The neck
        # lines sit 5.5u apart (3.0 clear), the fill line sits at the height where 3.0u of
        # ground remains above the base, and the flanks meet the neck where the shoulder is.
        line(d, 8.0, 4.0, 16.0, 4.0, s)          # the lip
        line(d, 9.25, 4.0, 9.25, 9.4, s)
        line(d, 14.75, 4.0, 14.75, 9.4, s)
        line(d, 9.25, 9.4, 5.0, 20.0, s)
        line(d, 14.75, 9.4, 19.0, 20.0, s)
        line(d, 5.0, 20.0, 19.0, 20.0, s)
        line(d, 7.2, 14.5, 16.8, 14.5, s)        # the fill line, where the flask is that wide

    elif name == 'ping':
        # DELIBERATE SUBSTITUTION. Lucide's `signal` is four concentric ascending arcs; at
        # 16px the three inner ones fall inside a 2px band and read as one smear. Four
        # ascending bars carry the same meaning and stay four marks, on the 2.6u rhythm
        # relaxation — a repeated parallel beat separates itself in a way two strokes cannot.
        for x, top in ((3.9, 15.8), (9.3, 12.2), (14.7, 8.6), (20.1, 5.0)):
            line(d, x, 20.2, x, top, s)

    elif name == 'coordinates':   # lucide `map-pin`
        # Teardrop: the bowl is an arc of r 6.6 about (12,10.2), and the flanks are its
        # tangents from the point at (12,20.2) — so the straights leave the arc without a
        # kink. The hole is a disc (SMALL RINGS), which is also what buys it 3.05u of clear
        # ground inside the bowl; stroked at r 3 it was 1.8u and the pin's head filled in.
        disc(d, 12, 10.2, 2.3)
        a = math.degrees(math.acos(6.6 / 10.0))       # 48.7
        arc(d, 12, 10.2, 6.6, 90 + a, 360 + 90 - a, s)
        for end in (90 - a, 90 + a):
            line(d, *polar(12, 10.2, 6.6, end), 12, 20.2, s)

    # ---------------------------------------------------------------- palette actions

    elif name == 'layers':
        # Two planes, not three. Lucide's three sit 4.2u apart, which is 2.3u of perpendicular
        # clearance at this stroke — at 16px the lower two merge and at 13px, where the HUD
        # editor's Grid tool draws this, the whole thing is a striped diamond. Three cannot be
        # rescued by flattening either: the spacing a legal gap requires puts the third plane
        # at y 23.2, outside the cell. A closed top plane and one chevron still say stacked.
        polyline(d, [(12, 2.6), (20.0, 7.2), (12, 11.8), (4.0, 7.2)], s, close=True)
        polyline(d, [(4.0, 13.8), (12, 18.4), (20.0, 13.8)], s)

    elif name == 'sword':         # lucide `sword`
        # Lucide's composition, with the blade opened up. Its two edges run 4.2u apart, which
        # at 2.5 leaves 1.7u — a grey band, not a blade, and this glyph is drawn at **13px**
        # in the party well, the smallest size in the overlay. At 5.9u apart the blade is open
        # at 16 and still legible at 13. The opposite error was measured too: at 6.8u it stops
        # being a sword and reads as a bent pipe.
        polyline(d, [(14.4, 18.6), (3.0, 7.2), (3.0, 3.0), (7.2, 3.0), (18.6, 14.4)], s)
        line(d, 12.8, 20.2, 20.2, 12.8, s)        # guard
        line(d, 16.0, 17.0, 20.4, 21.4, s)        # pommel

    elif name == 'users':
        # THE HONEST ONE. Two separated figures do not fit, and the arithmetic is short enough
        # to record: two heads big enough to keep an interior (r >= 3.5, SMALL RINGS) leave
        # 0.7u between them, and their shoulder arcs, at any radius that reads as shoulders,
        # overlap outright. Every construction that clears 3.0u between the figures needs more
        # than 24 units.
        #
        # So: two heads over one shared shoulder line — which is a group, and is how half the
        # icon sets in the world draw `users` anyway. It is deliberate here rather than the
        # accident it was: the cell used to hold a whole figure plus a *clipped* second one,
        # and at 16px the fragments were 2px slivers that read as a bracket beside a face.
        #
        # The heads are discs, not rings: at the size that clears the 3.0u gap between them
        # they have no interior left, and a ring whose hole has closed is a worse dot than a
        # dot. Rendered against the single-figure version, which is cleaner and says "person"
        # rather than "party"; plurality won.
        #
        # The near miss worth recording: two heads over ONE shared shoulder dome fits easily,
        # and at 16px it is a **sad face**. Two dots above an arch is a face before it is a
        # group, and no amount of proportion fixes it. The bodies have to be two, with the
        # 3.37u between them that a narrow dome and a vertical side buy back.
        for cx in (5.8, 18.2):
            disc(d, cx, 7.6, 2.4)
            arc(d, cx, 18.6, 3.6, 205, 335, s)
            for ex in (cx - 3.263, cx + 3.263):
                line(d, ex, 17.079, ex, 20.6, s)

    elif name == 'box':           # lucide `box`
        polyline(d, scaled([(12, 2.2), (20.8, 7.1), (20.8, 16.9), (12, 21.8), (3.2, 16.9),
                            (3.2, 7.1)], 0.98), s, close=True)
        polyline(d, scaled([(3.4, 7.2), (12, 12), (20.6, 7.2)], 0.98), s)
        line(d, 12, 11.8, 12, 21.6, s)

    elif name == 'move':          # lucide `move`
        # Shafts pulled in to 5.6 and heads grown to 3.2u barbs. Lucide's proportions put four
        # long thin shafts and four small heads in one cell, which at 16px is a spider; the
        # ink has to sit in the heads, because they are what says "direction" and the shafts
        # only say "cross". Drawn at 13px in the HUD editor's toolbar, so this matters twice.
        line(d, 12, 5.6, 12, 18.4, s)
        line(d, 5.6, 12, 18.4, 12, s)
        polyline(d, [(8.8, 5.8), (12, 2.6), (15.2, 5.8)], s)
        polyline(d, [(18.2, 8.8), (21.4, 12), (18.2, 15.2)], s)
        polyline(d, [(15.2, 18.2), (12, 21.4), (8.8, 18.2)], s)
        polyline(d, [(5.8, 15.2), (2.6, 12), (5.8, 8.8)], s)

    elif name == 'chevron-down':  # lucide `chevron-down`
        polyline(d, [(4.8, 8.7), (12, 15.3), (19.2, 8.7)], s)

    elif name == 'sparkle':
        # lucide `sparkles` reduced to its one centred four-point star, and FILLED — the one
        # form on the sheet that cannot be stroked. Its arms meet at a point rather than
        # enclosing anything, so the waist that keeps the flanks concave (< 2.9) is the waist
        # at which opposite flanks are closer than the stroke, and the waist that clears the
        # stroke (>= 3.9) is a rounded diamond. Both were rendered.
        i = 2.3
        polygon(d, [(12, 3.2), (12 + i, 12 - i), (20.8, 12), (12 + i, 12 + i),
                    (12, 20.8), (12 - i, 12 + i), (3.2, 12), (12 - i, 12 - i)])

    # ---------------------------------------------------------------- the rest of the kit

    elif name == 'check':         # lucide `check`
        polyline(d, [(19.6, 6.6), (9.4, 16.8), (4.4, 11.8)], s)

    elif name == 'plus':          # lucide `plus`
        # Same 16.2 span as `close`, so the two sit at one weight — they are the same mark
        # rotated, and they used to differ by 2 units for no reason.
        line(d, 3.9, 12, 20.1, 12, s)
        line(d, 12, 3.9, 12, 20.1, s)

    elif name == 'star':
        # STROKED now. It was filled on the argument that five stroked points close over their
        # own middle — measured, and false at these radii: outer 8.8 against inner 3.9 leaves
        # a clean hole. As a fill it was one of the two heaviest marks on a sheet of line
        # drawings, and it is reachable from a loadout's own `icon`, which puts it at 22px
        # beside stroked neighbours on the Loadouts screen.
        pts = []
        for k in range(5):
            pts.append(polar(12, 12, 8.8, -90 + 72 * k))
            pts.append(polar(12, 12, 3.9, -54 + 72 * k))
        polyline(d, pts, s, close=True)

    elif name == 'heart':
        # STROKED now, for `star`'s reason. Built as two lobe arcs plus their common tangents
        # to the point, so the flanks leave the lobes without a kink — the union-of-discs fill
        # it replaces had no outline to keep smooth and simply read as a blob.
        lx, ly, r, ax, ay = 8.2, 9.1, 4.15, 12.0, 20.2
        dd = math.hypot(ax - lx, ay - ly)
        tan = math.degrees(math.atan2(ay - ly, ax - lx)) + math.degrees(math.acos(r / dd))
        notch = math.degrees(math.acos((12 - lx) / r))    # where the two lobes cross, at x=12
        arc(d, lx, ly, r, tan, 360 + notch, s)
        arc(d, 24 - lx, ly, r, 180 - notch, 360 + 180 - tan, s)
        tx, ty = polar(lx, ly, r, tan)
        line(d, tx, ty, ax, ay, s)
        line(d, 24 - tx, ty, ax, ay, s)

    elif name == 'eye':
        # The almond is two arcs on the same chord (3.6,12)-(20.4,12), bulging to y 5.4 and
        # y 18.6. Both centres sit on x=12 at r 8.65.
        #
        # The endpoint angle is `asin((r - sagitta) / r)`, which is a correction: it was
        # `asin(sagitta / r)`, so both lids stopped ~20 degrees short of the chord and the
        # eye's corners were open. It read as a rounded almond, which is why nobody saw it.
        half, sag = 8.4, 6.6
        r = (sag ** 2 + half ** 2) / (2 * sag)
        a = math.degrees(math.asin((r - sag) / r))
        arc(d, 12, (12 - sag) + r, r, 180 + a, 360 - a, s)     # upper lid
        arc(d, 12, (12 + sag) - r, r, a, 180 - a, s)           # lower lid
        disc(d, 12, 12, 2.5)                                   # pupil (SMALL RINGS)

    elif name == 'play':          # lucide `play`, filled
        polygon(d, [(7.8, 4.6), (19.6, 12), (7.8, 19.4)])

    elif name == 'reset':
        # A ring open at the upper left with a corner arrowhead on its end, which is
        # `@void/ui`'s `PATHS.reset` and Lucide's `rotate-ccw` before it. What changed is the
        # gap: at 48 degrees the arrow's arm ran alongside the arc's other end with 0.1u
        # between them and the two welded into a hook. At 70 degrees the arm clears it by 3.3.
        # The corner sits *on* the arc's endpoint, tangent to it — a join, not an aperture.
        arc(d, 12, 12.2, 7.8, 250, 540, s)
        polyline(d, [(4.2, 7.6), (4.2, 12.2), (8.8, 12.2)], s)

    elif name == 'chevron-right':  # lucide `chevron-right` — the mirror of `back`
        polyline(d, [(9.0, 4.8), (15.6, 12.0), (9.0, 19.2)], s)

    elif name == 'bed':
        # `@void/ui`'s `PATHS.bed`, with the horizontals pulled apart: the mattress and the
        # base sat 4.4u apart, which is 1.9u of ground, and the pillow's top sat 2.6u off the
        # mattress. Both now clear, and the bed is taller and narrower than the 20x11 letter
        # box it was — it read as light for the same reason `close` did.
        line(d, 3.4, 4.6, 3.4, 19.8, s)                                   # headboard
        polyline(d, [(3.4, 12.4), (18.4, 12.4), (20.6, 14.6), (20.6, 19.8)], s)  # mattress
        line(d, 3.4, 18.0, 20.6, 18.0, s)                                 # base
        polyline(d, [(7.0, 12.4), (7.0, 7.0), (12.4, 7.0), (12.4, 12.4)], s)     # pillow

    elif name == 'watermark':
        # NOT a Lucide name. What the mod does: a mark placed in the corner of the screen.
        # (It used `sparkle` once, which was wrong twice over — `sparkle` means "AI / magic"
        # everywhere else, and it is filled, so the mod that draws the quietest thing on
        # screen carried the heaviest mark in the column.)
        #
        # The corner mark is a short bar rather than the disc it was: a watermark is a
        # wordmark, and a line of text in the low-left corner says that where a dot says
        # "image placeholder". It has to be **short and properly cornered** — a 4.8u bar at
        # (10.4, 13.4) reads as a minus sign in a box, which is a collapse control; 3.8u at
        # (9.2, 15.0) is 2.8 left and 3.0 down of the frame's centre and reads as a caption.
        # The frame runs wider than the box rule so those offsets exist at all: this is the
        # one cell on the sheet where the optical margin fights the meaning, and the meaning
        # wins at 2.6u of clearance.
        rrect(d, 2.2, 3.8, 19.6, 16.4, 4.92, s)
        line(d, 7.3, 15.0, 11.1, 15.0, s)

    elif name == 'clock':
        # A **stopwatch**, which is a different drawing from a wall clock and has to be, because
        # the mod is a stopwatch and this sheet already carries two rings.
        #
        # The wall clock was rendered first and is not survivable here. Its two hands are the
        # whole of what says "clock", and at 16px the case's interior is 11.7u across — 7.8px —
        # so an hour hand and a minute hand at any angle short of a right angle fall inside one
        # 2px band and downsample to a ring with a smudge in it. That is `crosshair` with a
        # dirty centre, and `crosshair` is six cells away on the same sheet. Drawn at a right
        # angle it survives and reads as a *dial*, not a clock, because two perpendicular
        # radii is what `fps`' needle would look like with a friend.
        #
        # The stopwatch is legible for a reason the clock is not: its identity is on the
        # OUTSIDE of the case. A crown at 12 o'clock is a mark against black ground with
        # nothing to merge into, so it holds at 13px where an interior mark cannot. So the
        # glyph is case + crown + one sweep hand — three ideas, and the hand can then be a
        # single stroke, which the interior does have room for.
        #
        # Geometry. The crown is 3.0u clear of the case's outer edge, which is `settings`'
        # teeth exactly: that is the measured length at which a radial stub off a ring is a
        # deliberate mark rather than a bump. Its inner end sits inside the case's stroke —
        # a join, like the needle and its dial, not an aperture. Working back from those two
        # constraints, the case is r 7.1 about (12, 13.4): crown tip to case bottom is 17.2u,
        # the box rule's height for a non-full-bleed form, and the case takes what is left.
        # It is the narrowest ring on the sheet at 14.2u across, and it is meant to be — a
        # stopwatch is taller than it is wide, and a case sized to the 17.6u circular rule
        # would have put the crown outside the cell.
        #
        # The hand sits at 1 o'clock rather than at 12. At 12 it is the crown continued
        # through the case and the whole glyph reads as one vertical stroke skewered through a
        # ring; 30 degrees off is far enough to separate them and still reads as a few seconds
        # elapsed rather than as half past. It clears the case's inner edge by 1.0u — `fps`'
        # needle gap, and the same argument: a hand is meant to point at its dial.
        #
        # No hub dot, for `fps`' measured reason: at this size the hub and the hand's root
        # merge into a blob that closes the dial's centre.
        circle(d, 12, 13.4, 7.1, s)
        line(d, 12, 6.3, 12, 3.3, s)          # the crown
        line(d, 12, 13.4, 14.43, 9.20, s)     # the sweep hand, 30 degrees off 12

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
    #
    # **Greyscale + alpha, not RGBA.** Every pixel's colour is 255,255,255, so three of RGBA's
    # four channels carry the same constant for the whole sheet; PNG colour type 4 stores one
    # grey plus the alpha and lets the filters do the rest. Measured on this sheet: 31,870 bytes
    # against 24,153, which is 42,496 characters of base64 against 32,204 — the sprite is the
    # single largest asset in a 400 KB budget, and this is 10 KB of it for no visual difference
    # whatsoever. (It is also why the redraw did not cost anything: the glyphs carry more ink at
    # a 2.5 stroke than at 2.2, and the sheet still came out ~5 KB smaller than the one it
    # replaced.)
    la = Image.merge('LA', (Image.new('L', sheet.size, 255), sheet))
    buf = io.BytesIO()
    la.save(buf, format='PNG', optimize=True)
    data = base64.b64encode(buf.getvalue()).decode('ascii')

    css = ['/* GENERATED by scripts/build-icons.py — do not edit. Re-run that script instead.',
           ' *',
           ' * One sprite, %d icons of %dpx displayed at %dpx:' % (len(ICONS), CELL, CSS),
           ' * %s.' % ', '.join(ICONS),
           ' * Purpose-drawn for the quiet-cell system at this size — see the script for the',
           ' * construction standard, and for why this is not an <svg>.',
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
