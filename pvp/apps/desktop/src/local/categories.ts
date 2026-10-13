/**
 * Mods by category, as the v1 Ship frames count them — Home's loadout meter
 * (`69:299`…), its legend (`81:6357`) and the in-game category rows.
 *
 * The category is the registry's own (`mods.json` `category`), not `local/registry`'s older
 * `categoryOf`, whose override table predates the field and disagrees with it for every mod
 * added since. 11 HUD · 11 PvP · 4 Visual · 3 Utility = 29, which is what the frames draw.
 */

import { MOD_CATEGORIES, MOD_REGISTRY } from '@void/protocol';

import type { Loadout, ModId } from './protocol';
import { MOD_GRID_ORDER, isOn } from './registry';

export type Category = (typeof MOD_CATEGORIES)[number];
export const CATEGORIES: readonly Category[] = MOD_CATEGORIES;

export const CATEGORY_LABEL: Record<Category, string> = {
  hud: 'HUD',
  pvp: 'PvP',
  visual: 'Visual',
  utility: 'Utility',
};

/** The hue a category's live cells take (quiet-cell-system §1). */
export const CATEGORY_HUE_VAR: Record<Category, string> = {
  hud: 'var(--hue-hud)',
  pvp: 'var(--hue-pvp)',
  visual: 'var(--hue-visual)',
  utility: 'var(--hue-utility)',
};

/** Every registry mod, whatever the grid order happens to include. */
const ALL: readonly ModId[] = [
  ...MOD_GRID_ORDER,
  ...(Object.keys(MOD_REGISTRY) as ModId[]).filter((id) => !MOD_GRID_ORDER.includes(id)),
];

export const categoryOfMod = (id: ModId): Category => MOD_REGISTRY[id].category as Category;

export const modsIn = (category: Category): ModId[] => ALL.filter((id) => categoryOfMod(id) === category);

export const TOTAL_MODS = ALL.length;

export interface CategoryCount {
  category: Category;
  on: number;
  total: number;
}

export function countsFor(loadout: Pick<Loadout, 'mods'> | null): CategoryCount[] {
  return CATEGORIES.map((category) => {
    const ids = modsIn(category);
    return { category, on: loadout ? ids.filter((id) => isOn(loadout, id)).length : 0, total: ids.length };
  });
}

export const totalOn = (loadout: Pick<Loadout, 'mods'> | null): number =>
  loadout ? ALL.filter((id) => isOn(loadout, id)).length : 0;
