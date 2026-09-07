/**
 * The two rules the mod setup page is built on, pinned as tests.
 *
 * §8 of `design/quiet-cell-system.md` says spatial properties never appear as list rows
 * and that what remains picks its own structure by count. Both of those are decisions
 * made in data — `propertiesFor` and the length of what it returns — so they can be
 * checked without rendering anything, which is what keeps this suite in the `node`
 * environment the store tests already use.
 */

import { describe, expect, it } from 'vitest';

import { PRESETS, matchingPreset, presetSettings } from './presets';
import type { ModId } from './protocol';
import {
  MOD_GRID_ORDER,
  MOD_REGISTRY,
  categoryOf,
  defaultsFor,
  hueOf,
  propertiesFor,
  settingsFor,
  spatialFor,
} from './registry';
import { useUi } from '../stores/ui';

describe('§8 — structure follows property count', () => {
  it('keeps every spatial property off the list', () => {
    for (const id of MOD_GRID_ORDER) {
      expect(propertiesFor(id).map((s) => s.key)).not.toContain('scale');
      expect(propertiesFor(id).length + spatialFor(id).length).toBe(settingsFor(id).length);
    }
  });

  it('gives the HUD mods a scale handle and the gameplay mods none', () => {
    for (const id of MOD_GRID_ORDER) {
      const spatial = spatialFor(id).length;
      expect(spatial).toBe(MOD_REGISTRY[id].kind === 'hud' ? 1 : 0);
    }
  });

  it('lands every mod on one of the three structures, and never on tabs', () => {
    // 1 → no list, 2–4 → flat, 5+ → two groups. The point of the assertion is that no
    // mod falls through to zero rows, which would leave the page with nothing to show.
    for (const id of MOD_GRID_ORDER) {
      expect(propertiesFor(id).length).toBeGreaterThan(0);
    }
    expect(propertiesFor('fullbright' as ModId)).toHaveLength(1);
    expect(propertiesFor('fps' as ModId)).toHaveLength(2);
    expect(propertiesFor('keystrokes' as ModId).length).toBeGreaterThanOrEqual(5);
  });

  it('gives every mod its category hue and never a bare accent', () => {
    for (const id of MOD_GRID_ORDER) {
      expect(hueOf(id)).toBe(`var(--hue-${categoryOf(id).toLowerCase()})`);
    }
  });
});

describe('presets', () => {
  it('Default is the registry defaults, verbatim', () => {
    for (const id of MOD_GRID_ORDER) {
      expect(presetSettings(id, 'default')).toEqual(defaultsFor(id));
    }
  });

  it('reports which preset a set of values is on, and null when it is hand-tuned', () => {
    for (const preset of PRESETS) {
      expect(matchingPreset('keystrokes' as ModId, presetSettings('keystrokes', preset.id))).toBe(
        preset.id,
      );
    }
    const tuned = { ...presetSettings('keystrokes', 'default'), opacity: 0.42 };
    expect(matchingPreset('keystrokes' as ModId, tuned)).toBeNull();
  });

  it('never turns a mod on by applying a preset', () => {
    for (const id of MOD_GRID_ORDER) {
      for (const preset of PRESETS) {
        expect(presetSettings(id, preset.id).on).toBe(defaultsFor(id).on);
      }
    }
  });
});

describe('the mod-setup sub-route', () => {
  it('opens on the Mods screen and closes back to the grid', () => {
    useUi.setState({ screen: 'play', modSetup: null });

    useUi.getState().openModSetup('cps' as ModId);
    expect(useUi.getState().screen).toBe('mods');
    expect(useUi.getState().modSetup).toBe('cps');

    useUi.getState().closeModSetup();
    expect(useUi.getState().screen).toBe('mods');
    expect(useUi.getState().modSetup).toBeNull();
  });

  it('is dropped by any navigation, so coming back to Mods lands on the grid', () => {
    useUi.getState().openModSetup('zoom' as ModId);
    useUi.getState().go('friends');
    expect(useUi.getState().modSetup).toBeNull();
  });
});
