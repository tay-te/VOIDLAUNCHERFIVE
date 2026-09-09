/**
 * The dev-only registry padding (`src/dev/fake-mods.ts`).
 *
 * Two things are worth pinning. **It is off**: the suite runs with the `define` in
 * `vitest.config.ts` pinned to `''`, so `MOD_ORDER` is still the registry's twelve and every
 * other test in this directory is measuring the real thing — a regression that switched the
 * padding on by default would otherwise show up as twenty unrelated failures with no obvious
 * cause. And **the plan is honest**: every synthetic mod is marked, takes a real category, and
 * the shapes between them cover all three of §8's property structures, which is the only reason
 * the fixtures exist rather than twelve copies of one mod.
 */

import { describe, expect, it } from 'vitest';
import { MOD_IDS, type ModCategory } from '@/bridge/protocol';
import { MOD_ORDER } from '@/registry';
import { FAKE_MOD_COUNT, planFakeMods } from '@/dev/fake-mods';
import { modProperties, propertyStructure } from '@/menu/ModSettingsScreen';
import type { SettingValue } from '@/store/store';

// The real registry's size, read from the registry. It was `13` and the fourteenth mod tripped
// it — this constant is only here to say "the padding added nothing", which is a comparison to
// the registry, not to a number.
const REAL = MOD_IDS.length;
const CATEGORIES: readonly ModCategory[] = ['hud', 'pvp', 'visual', 'utility'];

describe('the fake-mod padding', () => {
  it('is off unless asked for, so every other test measures the real registry', () => {
    expect(FAKE_MOD_COUNT).toBe(0);
    // `MOD_ORDER` against the registry, not against a number — `MOD_IDS` IS `REAL`, so
    // asserting its length says nothing. What matters is that no synthetic id reached either.
    expect(MOD_ORDER).toHaveLength(REAL);
    expect(MOD_ORDER.every((id) => (MOD_IDS as readonly string[]).includes(id))).toBe(true);
  });

  it('pads up to a total rather than adding a count', () => {
    expect(planFakeMods(24, REAL)).toHaveLength(24 - REAL);
    expect(planFakeMods(17, REAL)).toHaveLength(17 - REAL);
    // Never removes a real mod, and never trips over nonsense. `REAL - 1` is "asked for fewer
    // tiles than there are mods", which was written as the literal 13 back when that was the
    // registry's size and silently became "exactly the registry" at fourteen — a test that
    // still passed while no longer testing what it says.
    expect(planFakeMods(REAL - 1, REAL)).toHaveLength(0);
    expect(planFakeMods(3, REAL)).toHaveLength(0);
    expect(planFakeMods(Number.NaN, REAL)).toHaveLength(0);
    // Capped, so a stray zero cannot ask for a thousand tiles.
    expect(planFakeMods(9999, REAL)).toHaveLength(64 - REAL);
  });

  it('marks every one of them, and gives each a distinct id', () => {
    const mods = planFakeMods(64, REAL);
    for (const mod of mods) {
      expect(mod.id.startsWith('fake_')).toBe(true);
      expect(mod.description).toMatch(/not a real mod/i);
    }
    expect(new Set(mods.map((m) => m.id)).size).toBe(mods.length);
    expect(new Set(mods.map((m) => m.label)).size).toBe(mods.length);
  });

  it('draws categories from the real four, and uses all of them', () => {
    const mods = planFakeMods(24, REAL);
    for (const mod of mods) expect(CATEGORIES).toContain(mod.category);
    expect(new Set(mods.map((m) => m.category)).size).toBe(CATEGORIES.length);
  });

  it('varies enablement, so the disabled treatment is visible without clicking', () => {
    const on = planFakeMods(24, REAL).map((m) => m.defaults.on);
    expect(on).toContain(true);
    expect(on).toContain(false);
  });

  it('covers all three of §8`s property structures', () => {
    const structures = planFakeMods(24, REAL).map((mod) =>
      propertyStructure(
        modProperties(
          mod.id as never,
          mod.defaults as unknown as Record<string, SettingValue>,
        ).length,
      ),
    );
    expect(new Set(structures)).toEqual(new Set(['sentence', 'flat', 'grouped']));
  });

  it('gives every `mode` row its options, or the enum chips would render empty', () => {
    for (const mod of planFakeMods(24, REAL)) {
      if ('mode' in mod.defaults) expect(mod.enums[`${mod.id}.mode`]).toEqual(['toggle', 'hold']);
    }
  });
});
