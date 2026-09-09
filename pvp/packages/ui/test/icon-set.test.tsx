/**
 * The icon set as a **set**, rather than one glyph at a time.
 *
 * `test/mod-icons.test.tsx` walks the mods and asserts each one's glyph draws. That is the
 * schema's end of the chain and it stops at the thirteen names a mod happens to use today.
 * These tests are about the other property, the one the sprite script's docstring closes with:
 * the set is a **closed comparison** — every glyph is seen in one column beside its siblings —
 * so no two of them may be the same drawing, and a new one has to differ from its nearest
 * neighbour in shape rather than in detail.
 *
 * The failure being guarded against is on the record twice in this repo. `toggle_sprint`
 * shipped a cell whose drawing and whose name said different things for months, and the sprite
 * sheet and `PATHS` drew two different `crosshair`s for as long. Both are the same mistake: a
 * name that is not tied to one drawing. A duplicate path set is that mistake in its cheapest
 * form — a new name pointing at a glyph the set already has — and it is exactly what a copy of
 * a neighbouring entry produces.
 */
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ICON_NAMES, Icon, type IconName } from '../src/index.js';

/** The `d` of every path the glyph draws, in order. */
function drawingOf(name: IconName): string[] {
  const { container } = render(<Icon name={name} size={16} />);
  return [...container.querySelectorAll('path')].map((p) => p.getAttribute('d') ?? '');
}

/** How the glyph is painted — the set is stroked, with a handful of stated exceptions. */
function paintOf(name: IconName): { fill: string; stroke: string } {
  const { container } = render(<Icon name={name} size={16} />);
  const svg = container.querySelector('svg')!;
  return { fill: svg.getAttribute('fill') ?? '', stroke: svg.getAttribute('stroke') ?? '' };
}

describe('the shared icon set', () => {
  it('draws every name it declares, with real path data', () => {
    for (const name of ICON_NAMES) {
      const ds = drawingOf(name);
      expect(ds.length, name).toBeGreaterThan(0);
      for (const d of ds) expect(d, name).toMatch(/^[Mm][-0-9. ]/);
    }
  });

  it('never draws one shape under two names', () => {
    const seen = new Map<string, IconName>();
    for (const name of ICON_NAMES) {
      const key = drawingOf(name).join('|');
      const first = seen.get(key);
      expect(first, `${name} draws exactly what ${first} draws`).toBeUndefined();
      seen.set(key, name);
    }
  });

  /**
   * The four glyphs added for `freelook`, `hit_color`, `damage_tint` and the input mod. Each
   * one was drawn against a specific existing glyph it could have collapsed into, and each of
   * those pairs sits in one list in the Mods panel — so the pairing is the whole test, not the
   * name. The rendered comparison at 48 / 16 / 13px is in `Icon.tsx`'s comment for each and in
   * `ingame/scripts/build-icons.py`'s matching cell; what a test can hold is that the two are
   * still two drawings.
   */
  const neighbours: [IconName, IconName][] = [
    ['orbit', 'crosshair'], // a broken ring with a satellite in the break, against a closed one
    ['droplet', 'flask'], // a closed curve with a point, against a container with a flat lip
    ['heart-pulse', 'heart'], // differs across the bottom third, not inside the shape
    ['tap', 'cursor-click'], // a press on a surface, against the pointer family entirely
  ];

  it.each(neighbours)('draws %s as its own shape, not %s with a detail', (name, neighbour) => {
    expect(ICON_NAMES).toContain(name);
    const a = drawingOf(name);
    const b = drawingOf(neighbour);
    expect(a).not.toEqual(b);
    // Not a neighbour plus one mark, either: the shapes have no path in common.
    expect(a.filter((d) => b.includes(d))).toEqual([]);
  });

  it.each(neighbours.map(([name]) => name))('strokes %s, like the rest of the sheet', (name) => {
    // The overlay's sprite is one weight for the whole sheet and the launcher's is one stroke
    // for every unfilled glyph; a fill added here would be the heaviest mark in a column of
    // line drawings, which is the argument that un-filled `star` and `heart` in the first place.
    expect(paintOf(name)).toEqual({ fill: 'none', stroke: 'currentColor' });
  });
});
