/**
 * The dev padding, end to end — `?fake=` in, a bigger grid out.
 *
 * Its own file, and with no static imports of the modules under test, because the injection is
 * a **module-load side effect**: it writes into `@void/protocol`'s `MOD_REGISTRY` / `MOD_IDS`
 * and `@void/ui`'s `MOD_ICONS`, and those are shared singletons. Vitest isolates a worker per
 * file, so a padded registry stays inside this one and `registry.test.ts` next door still sees
 * the real twelve — which is the arrangement that lets the switch be tested at all without the
 * rest of the suite having to know it exists.
 *
 * What this is really guarding is the reach of the injection. `ModsScreen` derives its rows,
 * its columns, its tile size and the panel's height from `MOD_ORDER.length`, `TilePreview`
 * borrows a real mod's art, `ModList` reads an icon out of `@void/ui`, and the properties panel
 * reads settings out of the registry. A padding that only reached one of those would produce a
 * grid of empty tiles, which is worse than no padding at all.
 */

import { act, cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { MOD_IDS as SHIPPED_MOD_IDS } from '@void/protocol';

afterEach(cleanup);

/**
 * The total this file asks `?fake=` for.
 *
 * A **total**, not a count of fakes — which is exactly why it cannot be a literal. It was 24,
 * written when the registry had thirteen mods; at twenty-five real mods that total asks for
 * padding of *minus one*, produces no synthetic mods at all, and leaves the assertions below
 * measuring an empty set. Read off the shipped registry instead, with a margin big enough to
 * come round the four-category cycle twice.
 *
 * Read here, before the `?fake=` URL is set and before any dynamic import triggers the
 * injection, so it is the real count rather than a padded one. This is the file's one static
 * import of a module under test, and it is deliberate: the alternative is another literal that
 * will be wrong at the next roster pass.
 */
const FAKE_TOTAL = SHIPPED_MOD_IDS.length + 8;

describe('?fake= padding, end to end', () => {
  it('draws §8’s sentence structure, which no shipped mod has a small enough page for', async () => {
    // The fixture's first shape is one property (`SHAPES` in `dev/fake-mods.ts` says so by
    // name), and that is now the only place the sentence layout can be reached: Fullbright was
    // the registry's one-property mod until it gained a toggle key. The structure is derived
    // from the count, so what is worth pinning is that the small end of the table still draws
    // the small layout — a preview with a line under it and no rows at all.
    window.history.replaceState({}, '', `/?fake=${FAKE_TOTAL}`);
    const { connectBridge } = await import('@/bridge/connect');
    const { useVoidStore } = await import('@/store/store');
    const { App } = await import('@/App');
    const { solveGrid } = await import('@/menu/ModsScreen');
    const { IN_GAME_VIEW } = await import('./setup');
    const { MOD_ORDER } = await import('@/registry');
    const { isFakeMod } = await import('@/dev/fake-mods');
    const { modProperties } = await import('@/menu/ModSettingsScreen');
    const { modSettings } = await import('@/store/store');

    const bridge = connectBridge({ forceFake: true, runFakeClock: false });
    act(() => {
      useVoidStore.setState({ menuOpen: true, route: { name: 'mods' } });
    });
    const { container } = render(<App />);

    const single = MOD_ORDER.filter(isFakeMod).find(
      (id) =>
        modProperties(id, modSettings(useVoidStore.getState().loadout, id)).length === 1,
    );
    expect(single, 'the fixture must still carry a one-property shape').toBeTruthy();

    act(() => {
      useVoidStore.getState().openMod(single!);
    });
    expect(container.querySelector('[data-structure="sentence"]')).not.toBeNull();
    expect(container.querySelector('.mprop')).toBeNull();
    expect(container.querySelector('.mprops--sentence .preview')).not.toBeNull();
    // The preview takes the height the properties leave rather than carrying one of its own.
    expect(container.querySelector('.preview--large')).toBeNull();
    bridge.dispose();
  });

  it('pads the grid and gives every synthetic mod what the panel reads', async () => {
    window.history.replaceState({}, '', `/?fake=${FAKE_TOTAL}`);

    const { MOD_ORDER, MOD_CATEGORY, MOD_CATEGORY_TAGS, modLabel, hueStyle, SETTING_ENUMS } =
      await import('@/registry');
    const { MOD_REGISTRY, MOD_IDS } = await import('@/bridge/protocol');
    const { MOD_ICONS } = await import('@/ui');
    const { FAKE_MOD_COUNT, isFakeMod, fakeArtSource } = await import('@/dev/fake-mods');
    const { gridRows, solveGrid, visibleMods } = await import('@/menu/ModsScreen');
    const { IN_GAME_VIEW } = await import('./setup');

    // `?fake=24` asks for a *total* of 24 tiles, so how many are synthetic depends on how many
    // are real — which changes every time a mod ships. Derived, therefore, rather than the
    // literal 11/13 this held when the registry had thirteen mods: those numbers stopped being
    // true at the fourteenth, and one of them would have kept passing while measuring the wrong
    // slice.
    const REAL = FAKE_TOTAL - FAKE_MOD_COUNT;
    expect(FAKE_MOD_COUNT).toBeGreaterThan(0);
    expect(MOD_ORDER).toHaveLength(FAKE_TOTAL);
    expect(MOD_IDS).toHaveLength(FAKE_TOTAL);

    // The registry's own mods are untouched and still first, so the frames' reading order
    // survives the padding.
    expect(MOD_ORDER.slice(0, REAL).every((id) => !isFakeMod(id))).toBe(true);
    expect(MOD_ORDER.slice(REAL).every((id) => isFakeMod(id))).toBe(true);

    for (const id of MOD_ORDER.slice(REAL)) {
      expect(MOD_REGISTRY[id]).toBeDefined();
      expect(modLabel(id)).toBeTruthy();
      // The category has to be one of the four, or the tag, the tab and the hue all miss.
      expect(['HUD', 'PVP', 'VISUAL', 'UTILITY']).toContain(MOD_CATEGORY[id]);
      expect(hueStyle(id)).toHaveProperty('--hue');
      // `ModList` renders `Icon name={MOD_ICONS[id]}`, which throws on an unknown name.
      expect(MOD_ICONS[id]).toBeTruthy();
      // The preview is a real one, borrowed.
      expect(fakeArtSource(id)).not.toBeNull();
      expect(MOD_ORDER.slice(0, REAL)).toContain(fakeArtSource(id));
    }

    // The layout re-solves for the padded count — the claim this test can make, now that the
    // total follows the registry. Written against `shape` rather than as three numbers: those
    // numbers were the frames' 24 mods in three rows of eight, and both the total and the shape
    // it solves to now move whenever a mod ships. What must hold at any total is that the grid
    // covers every tile exactly once, in full rows but for the last.
    const shape = solveGrid(MOD_ORDER.length, IN_GAME_VIEW.width, IN_GAME_VIEW.height);
    expect(shape.columns).toBeGreaterThan(0);
    expect(shape.rows).toBe(Math.ceil(FAKE_TOTAL / shape.columns));
    const rows = gridRows([...MOD_ORDER], shape.columns);
    expect(rows.flat()).toHaveLength(FAKE_TOTAL);
    expect(rows.slice(0, -1).every((row) => row.length === shape.columns)).toBe(true);

    // Filtering still works on them, which is what proves the category is real data and not a
    // label. Asked per synthetic mod rather than per tab: "every tab matches at least one fake"
    // silently depends on there being at least four fakes, and `?fake=24` is a *total*, so the
    // count shrinks by one every time a real mod ships — at twenty mods it is four, and four
    // only covers four tabs if the cycle starts on the right one. This asks the stronger
    // question anyway: each synthetic mod appears under its own tag, and under no other.
    const fakes = MOD_ORDER.filter((id) => isFakeMod(id));
    expect(fakes.length).toBeGreaterThan(0);
    for (const id of fakes) {
      const own = MOD_CATEGORY[id];
      expect(visibleMods(own, '')).toContain(id);
      for (const other of MOD_CATEGORY_TAGS.filter((c) => c !== own)) {
        expect(visibleMods(other, '')).not.toContain(id);
      }
    }

    // Enum rows need their options or the chip row renders empty.
    for (const key of Object.keys(SETTING_ENUMS)) {
      expect(SETTING_ENUMS[key]!.length).toBeGreaterThan(0);
    }
  });

  it('renders every tile it was asked for, borrowed previews and all', async () => {
    window.history.replaceState({}, '', `/?fake=${FAKE_TOTAL}`);
    const { connectBridge } = await import('@/bridge/connect');
    const { useVoidStore } = await import('@/store/store');
    const { App } = await import('@/App');
    const { solveGrid } = await import('@/menu/ModsScreen');
    const { IN_GAME_VIEW } = await import('./setup');

    const bridge = connectBridge({ forceFake: true, runFakeClock: false });
    act(() => {
      useVoidStore.setState({ menuOpen: true, route: { name: 'mods' } });
    });
    const { container } = render(<App />);

    expect(container.querySelectorAll('.modcell')).toHaveLength(FAKE_TOTAL);
    const columns = solveGrid(FAKE_TOTAL, IN_GAME_VIEW.width, IN_GAME_VIEW.height).columns;
    expect(container.querySelectorAll('.mods-row')).toHaveLength(
      Math.ceil(FAKE_TOTAL / columns),
    );
    // Every tile drew art rather than the id-as-caption fallback, which is what a fake with no
    // borrowed preview would have rendered.
    for (const cell of container.querySelectorAll('.modcell')) {
      expect(cell.querySelector('.tart')).not.toBeNull();
    }
    // And the badge says so, so a screenshot of this can never be mistaken for the registry.
    expect(container.querySelector('.void-debug-badge')?.textContent).toContain('fake');
    bridge.dispose();
  });
});
