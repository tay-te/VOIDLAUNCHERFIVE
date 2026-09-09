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
import { ICON_NAMES, MOD_ICONS } from '@/ui';
import {
  BAR_ONLY_CELLS,
  SHEET_CELLS,
  SHEET_ORDER,
  SPRITE,
  cellStyle,
  installSpriteIcons,
} from '@/icons';
import { modProperties } from '@/menu/ModSettingsScreen';
import { modSettings } from '@/store/store';
import {
  FILTER_TABS,
  SETTING_ENUMS,
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
    // Both directions against the registry, and no count: the line above already proves the
    // sets are equal, so `toHaveLength(13)` only ever added a number to keep up to date. It
    // tripped on the fourteenth mod, which is the whole argument against it.
    expect([...MOD_ORDER].sort()).toEqual([...MOD_IDS].sort());
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

/**
 * The icon sheet, and the two ways it can silently draw the wrong thing.
 *
 * Both have already happened once in this file's neighbourhood: a hardcoded `background-size`
 * that stopped matching the sheet, and a channel list that stopped matching Java's. A sprite is
 * the same shape of bug — an off-by-one in the cell order shows a plausible icon in the wrong
 * place, which review does not catch and a screenshot barely does.
 */
describe('the icon sprite', () => {
  it('names a cell that exists for every icon it claims', () => {
    expect(installSpriteIcons()).toEqual([]);
  });

  it('covers every icon the overlay actually renders', () => {
    // The twelve mods plus the watermark: the list layout draws one per row and the palette
    // draws one per result, so a hole here is a hole on thirteen rows.
    for (const id of MOD_IDS) {
      expect(SPRITE[MOD_ICONS[id]]).toBeDefined();
    }
    // …and the names the palette's own actions and the party screen use.
    for (const name of ['layers', 'sword', 'users', 'box', 'move', 'close', 'chevron-down'] as const) {
      expect(SPRITE[name]).toBeDefined();
    }
  });

  it('places the i-th cell at -i cells, at whatever size it was asked for', () => {
    // 24 and 13 are both real call sites (`PartyScreen`, `hud-editor.tsx`), and the sheet is
    // stated in CSS pixels at the requested size — not in the sheet's own 48px device cells.
    expect(cellStyle(0, 16)).toMatchObject({ backgroundPosition: '0px 0' });
    expect(cellStyle(3, 16)).toMatchObject({
      backgroundPosition: '-48px 0',
      backgroundSize: `${SHEET_CELLS * 16}px 16px`,
    });
    expect(cellStyle(3, 24)).toMatchObject({
      backgroundPosition: '-72px 0',
      backgroundSize: `${SHEET_CELLS * 24}px 24px`,
    });
  });
});

/**
 * Every enum property has a table of options — the gate on a silent empty row.
 *
 * `kindOf` (`ModSettingsScreen.tsx`) *defaults* to `'enum'`: a key with no range, no swatch and
 * a non-boolean value is one, whether or not `SETTING_ENUMS` has ever heard of it. So a mod
 * added to `mods.json` with a string setting and no entry here renders a label and no control,
 * and nothing anywhere says so — jsdom draws the empty chip row exactly as happily as a full
 * one. It shipped that way on `watermark.style`.
 *
 * This walks the whole registry rather than the one mod that was wrong, because the next one
 * will be a different mod. See `design/rendering-invariants.md` §14.
 */
describe('enum settings', () => {
  it('gives every enum property a table, or the row draws no control at all', () => {
    const missing: string[] = [];
    for (const id of MOD_IDS) {
      const settings = modSettings(null, id);
      for (const property of modProperties(id, settings)) {
        if (property.kind !== 'enum') continue;
        const table = SETTING_ENUMS[`${id}.${property.key}`];
        if (table === undefined || table.length === 0) missing.push(`${id}.${property.key}`);
      }
    }
    expect(missing).toEqual([]);
  });

  it('names options the mod will actually accept', () => {
    // A table is only useful if its values round-trip: Java clamps an unknown enum back to the
    // default, so a typo here is a chip that silently does nothing when clicked.
    for (const [key, options] of Object.entries(SETTING_ENUMS)) {
      expect(options.length).toBeGreaterThan(0);
      for (const option of options) {
        expect(option).toMatch(/^[a-z0-9_]+$/);
      }
      expect(key).toMatch(/^[a-z0-9_]+\.[a-z0-9_]+$/);
    }
  });
});

/**
 * Every cell in the sheet is referenced by something.
 *
 * The sprite went from 5 cells to 25 in one change and cost 21 KB of the 400 KB bundle budget —
 * the first thing to move that budget meaningfully. A cell nobody draws is bytes in every JAR
 * forever, and there is no way to notice one by looking at the page.
 */
it('draws every cell it ships', () => {
  const referenced = new Set<string>([...Object.values(SPRITE), ...BAR_ONLY_CELLS]);
  const unused = SHEET_ORDER.filter((cell) => !referenced.has(cell));
  expect(unused).toEqual([]);
  // Both directions, and the arithmetic is the point: the mapping is **total** over `ICON_NAMES`
  // and the only cells outside it are the two the bar draws by class. A cell that lost its claim
  // and a name that lost its cell are the same bug and this catches either.
  expect(SHEET_ORDER.length).toBe(Object.keys(SPRITE).length + BAR_ONLY_CELLS.length);
});

/**
 * Every icon name the shared kit can produce has a cell.
 *
 * Not "every name a call site passes" — `resolveLoadoutIcon` turns a loadout's own `icon` string
 * into any member of `ICON_NAMES`, so the domain is the whole set and a subset chosen by
 * grepping is a guess. It was a guess for an hour, and four reachable names had no cell: `check`
 * on the HUD editor's active tool, `plus` on Loadouts' new-loadout button, and `star` and `bed`
 * through that resolution. Each drew an empty box, silently. See `rendering-invariants.md` §15.
 */
it('has a cell for every icon the shared kit can name', () => {
  for (const name of ICON_NAMES) {
    expect(SPRITE[name], name).toBeDefined();
  }
});
