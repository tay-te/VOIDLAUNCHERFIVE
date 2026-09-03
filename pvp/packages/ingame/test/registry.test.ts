/**
 * The overlay's view of the registry is derived, not transcribed.
 *
 * Before `mods.json` carried `category` and the frames' labels, this module held a
 * hand-written map of both, read off the Figma tile by tile. These tests are what stop
 * that coming back: if a future edit re-hard-codes either, the schema stops being the
 * single source and this fails.
 */

import { describe, expect, it } from 'vitest';
import { MOD_REGISTRY, MOD_IDS, getModCategory } from '@/bridge/protocol';
import {
  FILTER_TABS,
  MOD_CATEGORY,
  MOD_CATEGORY_TAGS,
  MOD_ORDER,
  categoryLabel,
  modLabel,
} from '@/registry';

describe('the mod registry view', () => {
  it('takes every category straight from mods.json', () => {
    for (const id of MOD_IDS) {
      expect(MOD_CATEGORY[id]).toBe(getModCategory(id).toUpperCase());
    }
  });

  it('takes every label straight from mods.json, with no overrides left', () => {
    for (const id of MOD_IDS) {
      expect(modLabel(id)).toBe(MOD_REGISTRY[id].label);
    }
    // The three the panel used to override, now the registry's own copy.
    expect(modLabel('fps')).toBe('FPS display');
    expect(modLabel('cps')).toBe('CPS counter');
    expect(modLabel('ping')).toBe('Ping display');
  });

  it('tabs across All plus every category the schema declares', () => {
    expect(FILTER_TABS.map((t) => t.label)).toEqual([
      'All',
      'HUD',
      'PvP',
      'Visual',
      'Utility',
    ]);
    expect(FILTER_TABS[0].id).toBe('all');
    // Every non-`all` tab id is a value MOD_CATEGORY can actually take, or the tab
    // would filter to nothing.
    for (const tab of FILTER_TABS.slice(1)) {
      expect(MOD_CATEGORY_TAGS).toContain(tab.id);
      expect(MOD_IDS.some((id) => MOD_CATEGORY[id] === tab.id)).toBe(true);
    }
  });

  it('prints the frame`s label for a tag', () => {
    expect(categoryLabel('PVP')).toBe('PvP');
    expect(categoryLabel('HUD')).toBe('HUD');
  });

  it('still owns the grid order, which is layout and not a property of a mod', () => {
    expect([...MOD_ORDER].sort()).toEqual([...MOD_IDS].sort());
    expect(MOD_ORDER).toHaveLength(12);
  });

  it('keeps category distinct from kind', () => {
    // If these agreed everywhere, the filter could read `kind` and `category` would be
    // dead weight in the schema.
    expect(MOD_REGISTRY.crosshair.kind).toBe('gameplay');
    expect(MOD_CATEGORY.crosshair).toBe('VISUAL');
    expect(MOD_REGISTRY.zoom.kind).toBe('gameplay');
    expect(MOD_CATEGORY.zoom).toBe('UTILITY');
  });
});
