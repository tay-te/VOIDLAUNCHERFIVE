/**
 * Every mod's art, in one exhaustive table.
 *
 * ## What this file guarantees
 *
 * `ART` is asserted `satisfies Record<ModId, ModArt>`. `ModId` is generated from
 * `schema/mods.json`, so **adding a mod to the schema breaks this file until its art exists**.
 * That is the entire point: a thumbnail and a live preview are now part of what it means for a
 * mod to be shipped, enforced by the compiler rather than by remembering.
 *
 * Before this, a mod's two pictures were four hand-maintained tables in three files across two
 * packages (`mods/types.ts` lists them), and every one of them failed quietly — a mod missing
 * from `TilePreview`'s `switch` did not fail to build, it drew its own id as text, in game, in
 * front of a player. `docs/mod-roster.md` §7 plans five waves of new mods, Wave 2 alone being
 * nine "all S, all the same shape"; nine mods against four silent tables is how a roster pass
 * ships broken art.
 *
 * ## Adding a mod
 *
 * 1. `schema/mods/<id>.json`, then `node schema/build.mjs` — see `docs/adding-a-mod.md`,
 *    which is the whole procedure. The icon is declared there too, not here.
 * 2. `packages/ingame/src/mods/<id>.tsx` — one {@link ModArt}.
 * 3. Add it to {@link MOD_ORDER} where it should read in the grid.
 *
 * Step 2 is not optional and step 3 is checked at runtime by {@link MOD_ORDER}'s own assertion,
 * because a mod missing from a *layout* array is the one thing a type cannot catch — the array
 * is `ModId[]` whether or not it is complete.
 */

import type { ModId } from '@/bridge/protocol';
import type { ModArt } from './types';

import armor_status from './armor_status';
import combo from './combo';
import coordinates from './coordinates';
import cps from './cps';
import direction from './direction';
import block_outline from './block_outline';
import clock from './clock';
import cps_graph from './cps_graph';
import hit_trade from './hit_trade';
import nametags from './nametags';
import potion_counter from './potion_counter';
import reach from './reach';
import sprint_reset from './sprint_reset';
import scoreboard from './scoreboard';
import item_counter from './item_counter';
import memory from './memory';
import momentum from './momentum';
import old_animations from './old_animations';
import old_input from './old_input';
import overlay from './overlay';
import crosshair from './crosshair';
import damage_tint from './damage_tint';
import fov from './fov';
import freelook from './freelook';
import fps from './fps';
import fullbright from './fullbright';
import hit_color from './hit_color';
import hitboxes from './hitboxes';
import keystrokes from './keystrokes';
import ping from './ping';
import saturation from './saturation';
import server_address from './server_address';
import stopwatch from './stopwatch';
import toggle_sneak from './toggle_sneak';
import potion_effects from './potion_effects';
import toggle_sprint from './toggle_sprint';
import watermark from './watermark';
import zoom from './zoom';

/**
 * The art for all twenty-five mods.
 *
 * `satisfies` rather than a type annotation, so each value keeps its narrow `ModArt<'fps'>`
 * type while the whole object is still proved exhaustive over `ModId`.
 */
const ART = {
  fps,
  keystrokes,
  cps,
  ping,
  coordinates,
  direction,
  armor_status,
  potion_effects,
  watermark,
  toggle_sprint,
  fullbright,
  hitboxes,
  zoom,
  crosshair,
  // The Wave 2 readout sweep (docs/mod-roster.md §7). Six chips, no new sensor between them.
  combo,
  saturation,
  momentum,
  memory,
  server_address,
  block_outline,
  clock,
  cps_graph,
  hit_trade,
  nametags,
  potion_counter,
  reach,
  sprint_reset,
  scoreboard,
  item_counter,
  // Wave 4 — §7's "four that change how the client feels", plus the stopwatch the `modaction`
  // input path unblocked. Four of the five draw into the world, so their art is a diagram.
  stopwatch,
  fov,
  toggle_sneak,
  overlay,
  // Roster §3.2's medium PvP set. `freelook` absorbs Snaplook (#9) and `damage_tint` absorbs
  // Hurt cam (#8); both bundles are argued in their own schema `$comment`s.
  freelook,
  hit_color,
  damage_tint,
  // The 1.7 pair. Split rather than bundled because the badge is per mod: `old_animations` is
  // `safe` and animation only, `old_input` is `grey` and changes what leaves the client.
  old_animations,
  old_input,
} satisfies Record<ModId, ModArt>;

/** Every mod's art, keyed by id. */
export const MOD_ART: Record<ModId, ModArt> = ART;

/**
 * The art for a mod, or `undefined` for an id the build does not know.
 *
 * The miss is not dead code: `src/dev/fake-mods.ts` injects synthetic ids into `MOD_IDS` at
 * runtime for grid-density work (`?fake=`), and those ids exist in no table here by
 * construction. Callers handle the miss by borrowing a real mod's art — see `TilePreview`.
 */
export function modArt(id: string): ModArt | undefined {
  return (ART as Record<string, ModArt>)[id];
}

export type { ModArt, ThumbnailProps } from './types';
export { defineMod } from './types';
export { MOD_ORDER } from './order';
