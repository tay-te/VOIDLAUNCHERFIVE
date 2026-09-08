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

afterEach(cleanup);

describe('?fake= padding, end to end', () => {
  it('pads the grid and gives every synthetic mod what the panel reads', async () => {
    window.history.replaceState({}, '', '/?fake=24');

    const { MOD_ORDER, MOD_CATEGORY, modLabel, hueStyle, SETTING_ENUMS } = await import(
      '@/registry'
    );
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
    const REAL = 24 - FAKE_MOD_COUNT;
    expect(FAKE_MOD_COUNT).toBeGreaterThan(0);
    expect(MOD_ORDER).toHaveLength(24);
    expect(MOD_IDS).toHaveLength(24);

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

    // The layout re-solves: 24 mods are the frames' three rows of eight, not twelve's two of six.
    const shape = solveGrid(MOD_ORDER.length, IN_GAME_VIEW.width, IN_GAME_VIEW.height);
    expect(shape.rows).toBe(3);
    expect(shape.columns).toBe(8);
    expect(shape.scrolls).toBe(false);
    expect(gridRows([...MOD_ORDER], shape.columns).map((row) => row.length)).toEqual([8, 8, 8]);

    // Filtering still works on them, which is what proves the category is real data and not a
    // label: every tab matches at least one synthetic mod.
    for (const tag of ['HUD', 'PVP', 'VISUAL', 'UTILITY']) {
      expect(visibleMods(tag, '').some((id) => isFakeMod(id))).toBe(true);
    }

    // Enum rows need their options or the chip row renders empty.
    for (const key of Object.keys(SETTING_ENUMS)) {
      expect(SETTING_ENUMS[key]!.length).toBeGreaterThan(0);
    }
  });

  it('renders all twenty-four tiles, borrowed previews and all', async () => {
    window.history.replaceState({}, '', '/?fake=24');
    const { connectBridge } = await import('@/bridge/connect');
    const { useVoidStore } = await import('@/store/store');
    const { App } = await import('@/App');

    const bridge = connectBridge({ forceFake: true, runFakeClock: false });
    act(() => {
      useVoidStore.setState({ menuOpen: true, route: { name: 'mods' } });
    });
    const { container } = render(<App />);

    expect(container.querySelectorAll('.modcell')).toHaveLength(24);
    expect(container.querySelectorAll('.mods-row')).toHaveLength(3);
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
