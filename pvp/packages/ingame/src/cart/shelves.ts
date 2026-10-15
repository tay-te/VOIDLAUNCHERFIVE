/**
 * Which cartridge sits where on each shelf — layout, not data, like `MOD_ORDER`.
 *
 * Each category shelf leads with the mods a player reaches for first (the order the Cartridge
 * frames draw: FPS, CPS, Keystrokes, Armor, Potion effects on HUD; Toggle sprint, Hit colour,
 * 1.7 animations, Hitboxes on PvP …); everything else follows in `MOD_ORDER`. The lists are
 * derived, so a mod added to the registry lands on its category's shelf with no edit here.
 */

import { MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { MOD_ORDER } from '@/registry';
import { CATEGORIES, categoryOf, type CartCategory } from './prims';

const LEAD: Record<CartCategory, readonly string[]> = {
  hud: ['fps', 'cps', 'keystrokes', 'armor_status', 'potion_effects', 'ping', 'coordinates', 'direction', 'scoreboard'],
  pvp: ['toggle_sprint', 'hit_color', 'old_animations', 'hitboxes', 'reach', 'combo', 'toggle_sneak', 'freelook'],
  visual: ['crosshair', 'fullbright', 'block_outline', 'nametags', 'time_weather', 'fog'],
  utility: ['zoom', 'chat', 'culling', 'particles', 'frame_limiter'],
};

/** Every real registry mod (the `?fake=` padding never reaches the shelves). */
export const SHELF_MODS: readonly ModId[] = MOD_ORDER.filter((id) => Boolean(MOD_REGISTRY[id]));

function shelf(category: CartCategory): ModId[] {
  const members = SHELF_MODS.filter((id) => categoryOf(id) === category);
  const lead = LEAD[category].filter((id) => members.includes(id as ModId)) as ModId[];
  return [...lead, ...members.filter((id) => !lead.includes(id))];
}

export const SHELVES: Readonly<Record<CartCategory, readonly ModId[]>> = Object.fromEntries(
  CATEGORIES.map((c) => [c, shelf(c)]),
) as unknown as Record<CartCategory, readonly ModId[]>;

/** All mods, shelf by shelf. */
export const ALL_SHELVED: readonly ModId[] = CATEGORIES.flatMap((c) => SHELVES[c]);

/** Six cartridges to a row, as every Cartridge frame lays them out. */
export const COLUMNS = 6;

/**
 * What a shelf shows in the All view: everything if it fits one row, otherwise five and a
 * `+N more` cartridge that opens the shelf.
 */
export function shelfPreview(ids: readonly ModId[]): { shown: readonly ModId[]; more: number } {
  if (ids.length <= COLUMNS) return { shown: ids, more: 0 };
  return { shown: ids.slice(0, COLUMNS - 1), more: ids.length - (COLUMNS - 1) };
}
