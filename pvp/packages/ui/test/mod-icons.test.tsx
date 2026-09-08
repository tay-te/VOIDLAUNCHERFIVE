/**
 * `MOD_ICONS` — the schema's icon names, checked against the glyphs this package can draw.
 *
 * `Icon` looks its `name` up in a table and maps the result, so a name with no drawing is a
 * `TypeError`, not a blank space (and under the overlay's sprite renderer it is a cell that
 * does not exist — an empty box, silently). `MOD_ICONS` used to be written by hand, so both
 * halves of that were somebody's attention; now it is
 * `{ ...MOD_ICON_NAMES } satisfies Record<ModId, IconName>` and the compiler proves totality
 * in both directions before this file runs.
 *
 * These tests exist for what the compiler cannot cover: the built `dist` a JavaScript
 * consumer imports, the actual rendering of all thirteen glyphs, and — the one that would
 * otherwise be found only in a dev build — that the table is still an ordinary extensible
 * object, because `packages/ingame/src/dev/fake-mods.ts` writes synthetic mods into it at
 * import time under `?fake=`.
 */

import { render } from '@testing-library/react';
import { MOD_ICON_NAMES, MOD_IDS, MOD_REGISTRY } from '@void/protocol';
import { describe, expect, it } from 'vitest';

import { ICON_NAMES, Icon, MOD_ICONS } from '../src/index.js';

describe('MOD_ICONS', () => {
  it('has a row for every mod the registry ships, and no others', () => {
    expect(Object.keys(MOD_ICONS)).toEqual([...MOD_IDS]);
  });

  it('is the schema’s icon, not a second transcription of it', () => {
    for (const id of MOD_IDS) {
      expect(MOD_ICONS[id], id).toBe(MOD_REGISTRY[id].icon);
      expect(MOD_ICONS[id], id).toBe(MOD_ICON_NAMES[id]);
    }
  });

  it('names only glyphs this package can draw', () => {
    for (const id of MOD_IDS) {
      expect(ICON_NAMES, `${id} -> ${MOD_ICONS[id]}`).toContain(MOD_ICONS[id]);
    }
  });

  it('draws every one of them', () => {
    // The end of the chain the schema starts: `mods/<id>.json` said `gauge`, and something
    // with paths in it comes out. A name that reached here undrawable would throw.
    for (const id of MOD_IDS) {
      const { container } = render(<Icon name={MOD_ICONS[id]} size={16} />);
      expect(container.querySelectorAll('path').length, id).toBeGreaterThan(0);
    }
  });

  it('throws on a name it cannot draw — which is why the table is total', () => {
    // The stake, stated. This is the failure the `satisfies` in `Icon.tsx` makes
    // unrepresentable for a mod icon, and it is why the schema's `pattern` is not enough on
    // its own: a pattern cannot know what this package draws.
    expect(() =>
      render(<Icon name={'not-a-real-icon' as (typeof ICON_NAMES)[number]} />),
    ).toThrow();
  });

  /**
   * The dev padding's contract, from this side of it.
   *
   * `fake-mods.ts` does `(MOD_ICONS as unknown as Record<string, string>)[id] = icon` for each
   * synthetic mod, having pushed the same id into `MOD_IDS` and `MOD_REGISTRY`. That is the
   * whole reason this is a spread copy of the generated constant rather than the constant
   * itself, or a frozen object, or a `Proxy`: it has to accept a row it was not generated
   * with. Nothing in this package would notice if it stopped — the padding only runs in the
   * overlay's dev build — so it is asserted here, where the object is defined.
   */
  it('stays extensible, so `?fake=` can inject synthetic mods into it', () => {
    expect(Object.isFrozen(MOD_ICONS)).toBe(false);
    expect(Object.isSealed(MOD_ICONS)).toBe(false);
    expect(Object.isExtensible(MOD_ICONS)).toBe(true);

    const table = MOD_ICONS as unknown as Record<string, string>;
    try {
      table.fake_reach_display = 'gauge';
      expect(table.fake_reach_display).toBe('gauge');
      // …and it is the same object the rest of the bundle reads, or the padding reaches
      // nothing: `ModList` and the palette import `MOD_ICONS` from the package root.
      expect((MOD_ICONS as unknown as Record<string, string>).fake_reach_display).toBe('gauge');
    } finally {
      delete table.fake_reach_display;
    }
    expect(Object.keys(MOD_ICONS)).toEqual([...MOD_IDS]);
  });

  it('does not write the padding back into the generated constant', () => {
    // The copy also runs the other way: `@void/protocol`'s table is the contract and reads
    // the same in a dev build as in a release one.
    const table = MOD_ICONS as unknown as Record<string, string>;
    try {
      table.fake_reach_display = 'gauge';
      expect(Object.keys(MOD_ICON_NAMES)).toEqual([...MOD_IDS]);
      expect((MOD_ICON_NAMES as unknown as Record<string, string>).fake_reach_display).toBeUndefined();
    } finally {
      delete table.fake_reach_display;
    }
  });
});
