/**
 * Reading order of the tile grid.
 *
 * Its own module, and deliberately a **leaf**: it imports the registry and the dev fixtures and
 * nothing else. `mods/index.ts` pulls in all thirteen mod modules and through them the HUD
 * widgets and the store, while `registry.ts` — which half the menu imports — only wants this
 * array. Keeping them apart is what stops `registry.ts -> mods/index.ts -> widgets -> store`
 * from closing into a cycle.
 */

import { MOD_IDS, type ModId } from '@/bridge/protocol';
import { FAKE_MOD_ORDER } from '@/dev/fake-mods';

/**
 * Reading order of the tile grid, left to right, top to bottom.
 *
 * A **layout** decision, which is why it is here and not in `schema/mods.json`: the registry's
 * own order is the order mods were added, and the grid groups by what a player is looking for.
 * `ModsScreen` derives its rows, its column count and therefore the tile size and the panel's
 * height from this array's length.
 *
 * `FAKE_MOD_ORDER` is empty in every build that did not ask for it (`src/dev/fake-mods.ts`);
 * it is spread here because the grid's shape is the one thing a dev-only padding has to reach.
 */
export const MOD_ORDER: ModId[] = [
  'fps',
  'keystrokes',
  'cps',
  'toggle_sprint',
  'crosshair',
  'zoom',
  'fullbright',
  'hitboxes',
  'armor_status',
  'potion_effects',
  'ping',
  'coordinates',
  // Beside Coordinates, which is the mod a player confuses it with — the two are next to each
  // other so the difference (a position, versus a facing) is visible rather than argued.
  'direction',
  // Last, and newest. The mark is not a readout, so it does not belong among the four that end
  // the order; putting it after them is also the honest reading of a mod added after the grid
  // was designed.
  'watermark',
  ...FAKE_MOD_ORDER,
];

/**
 * A mod present in the registry but missing from {@link MOD_ORDER} would simply not be drawn —
 * no error, no empty tile, just absent from a grid nobody counts. The types cannot catch it
 * (the array is `ModId[]` whether or not it is complete), so this does, at import time, in
 * every build including production. It costs one array walk once.
 */
const unordered = MOD_IDS.filter((id) => !MOD_ORDER.includes(id));
if (unordered.length > 0) {
  throw new Error(
    `mods/index.ts: MOD_ORDER is missing ${unordered.join(', ')} — ` +
      'a mod absent from the grid order is never drawn.',
  );
}

