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
  // Beside FPS because they are the same question asked twice — is this machine keeping up.
  // `docs/mod-roster.md` §3.1 lists Memory as "trivial and expected next to an FPS chip".
  'memory',
  'keystrokes',
  'cps',
  // Beside CPS, because it is the same measurement drawn twice: one as a figure, one as a shape.
  // The pair shares the `cursor-click` glyph, so the grid has to keep them together — two
  // identical marks with other tiles between them is a collision, side by side with different
  // labels they are a pair.
  'cps_graph',
  // Beside CPS: one counts the hand, the other counts what the hand landed, and a player
  // reading one is reading the other in the same glance.
  'combo',
  // And beside Combo, because they are the same two counters read over two spans: Combo is the
  // fight and owns the timeout, Trade counter is the session and owns no clock at all. They
  // share the `sword` glyph, so the grid has to put them together — two identical marks with
  // other tiles between them is the confusion; two identical marks side by side with different
  // labels is a pair, which is what Coordinates and Direction already are.
  'hit_trade',
  // And beside those two, closing the fight block: the chain, the session, and how far away the
  // last one connected. It is the only `grey` mod in this run of the order, which is visible on
  // the tile — the badge is per mod — and is the honest place for it: a player reading the three
  // together should see which of them carries the classification.
  'reach',
  'toggle_sprint',
  // Its own mod as of Wave 4, and it reads next to the one it was a boolean on until then.
  'toggle_sneak',
  // The two that change how a swing feels, beside the two that change how it starts. Adjacent
  // and deliberately separate: one is animation and `safe`, the other is input and `grey`, and a
  // player should be able to take the first without paying the badge for the second.
  'old_animations',
  'old_input',
  'crosshair',
  'zoom',
  // Beside Zoom: both are the camera's own angle, one held and one fixed.
  'fov',
  // And beside those two: the third thing a player does to the camera, which is to stop it
  // being aimed by their body.
  'freelook',
  'fullbright',
  // Beside Fullbright, the other mod whose subject is what the game stops you seeing.
  'overlay',
  // Beside Hitboxes, the other mod whose whole subject is a wireframe drawn in the world — the
  // two share a `cube` glyph for that reason, and the grid keeps them together so the shared mark
  // reads as a pair rather than as a collision.
  'block_outline',
  'hitboxes',
  // Beside Hitboxes, the other mod whose subject is reading a fight off the entity in front of
  // you rather than off the HUD.
  'hit_color',
  'damage_tint',
  'armor_status',
  // The survival block: what you are wearing, what is keeping you alive, what is in your hand.
  // Saturation sits inside it because in 1.8 saturation *is* regen, and vanilla never draws it.
  'saturation',
  'potion_effects',
  'item_counter',
  // Beside Item counter, which is the mod it shares a sensor with: one counts what you are
  // holding (and now every copy of it), the other counts what you have to fix yourself with.
  // §3.1 #5's "same engine as #4 — do them together", drawn as adjacency.
  'potion_counter',
  // The one mod whose subject is a *vanilla* overlay rather than one of ours, and it reads here
  // because the sidebar is the thing it covers up: Ping display and Server address are the
  // top-right corner's own readouts, and the scoreboard is what vanilla puts over them.
  'scoreboard',
  'ping',
  // Beside Ping, the other half of "what am I connected to". They share the `wifi` glyph for
  // the same reason Coordinates and Direction share `compass`.
  'server_address',
  // Closes the session block — the readouts about this sitting rather than about the fight.
  'stopwatch',
  // Beside Stopwatch, whose `clock` glyph it shares: elapsed time and time of day are two
  // readings of one subject, and the same reasoning that pairs Coordinates with Direction pairs
  // these. It is also the last of the session block and the only readout in the registry that
  // is correct with no game running.
  'clock',
  'coordinates',
  // Beside Coordinates, which is the mod a player confuses it with — the two are next to each
  // other so the difference (a position, versus a facing) is visible rather than argued.
  'direction',
  // Closes the movement block. Coordinates says where, Direction says which way, Momentum says
  // how fast — the three readouts a bridging or knockback player reads together.
  'momentum',
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

