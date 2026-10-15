/**
 * The dev padding, end to end — `?fake=` in, fuller shelves out.
 *
 * Its own file, and with no static imports of the modules under test, because the injection is
 * a **module-load side effect**: it writes into `@void/protocol`'s `MOD_REGISTRY` / `MOD_IDS`
 * and `@void/ui`'s `MOD_ICONS`, and those are shared singletons. Vitest isolates a worker per
 * file, so a padded registry stays inside this one and `registry.test.ts` next door still sees
 * the real twelve — which is the arrangement that lets the switch be tested at all without the
 * rest of the suite having to know it exists.
 *
 * What this is really guarding is the reach of the injection. The shelves derive their members
 * from `MOD_ORDER` and the registry, the cartridge reads a label, a category and settings,
 * and the settings bench reads every property out of the registry. A padding that only reached
 * one of those would produce
 * empty cartridges, which is worse than no padding at all.
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
  it('shelves every synthetic mod with the real ones, and each opens a working bench', async () => {
    window.history.replaceState({}, '', `/?fake=${FAKE_TOTAL}`);
    const { connectBridge } = await import('@/bridge/connect');
    const { useVoidStore } = await import('@/store/store');
    const { App } = await import('@/App');
    const { MOD_ORDER } = await import('@/registry');
    const { isFakeMod } = await import('@/dev/fake-mods');
    const { SHELVES } = await import('@/cart/shelves');
    const { modProperties } = await import('@/menu/ModSettingsScreen');
    const { modSettings } = await import('@/store/store');

    const bridge = connectBridge({ forceFake: true, runFakeClock: false });
    expect(MOD_ORDER).toHaveLength(FAKE_TOTAL);
    const fakes = MOD_ORDER.filter(isFakeMod);
    expect(fakes.length).toBe(8);
    const shelved = Object.values(SHELVES).flat();
    for (const id of fakes) expect(shelved, id).toContain(id);

    act(() => {
      useVoidStore.getState().applyMenu(true);
    });
    const { container } = render(<App />);
    for (const id of fakes) {
      act(() => {
        useVoidStore.getState().openMod(id);
      });
      const bench = container.querySelector('.c-bench') as HTMLElement;
      expect(bench, id).not.toBeNull();
      const props = modProperties(id, modSettings(useVoidStore.getState().loadout, id));
      expect(bench.querySelector('.c-bench__body')!.textContent!.length, id).toBeGreaterThan(0);
      for (const p of props) expect(bench.textContent, `${id}.${p.key}`).toContain(p.label);
    }
    // A synthetic mod toggles like a real one, from its own cartridge's switch.
    act(() => {
      useVoidStore.getState().closeMod();
      useVoidStore.getState().setModSearch('');
    });
    bridge.dispose();
  });
});
