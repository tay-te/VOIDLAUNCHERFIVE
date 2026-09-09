/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 *
 * Source: pvp/schema/mods.json, loadout.json, protocol.json, bridge.json
 * Generator: json-schema-to-typescript, via `pnpm --filter @void/protocol gen`.
 *
 * The four documents are compiled together as one bundle so that a definition shared
 * between them (keybind, hud_item, loadout, …) yields exactly one TypeScript type.
 */

import type { HUDAnchor, HUDModId } from './schema.js';

/**
 * Where this mod's widget sits on a HUD nobody has touched — the layout of Figma frame
 * 244:1722, which is what a new loadout is seeded with and what the HUD editor's `Reset layout`
 * restores. Anchor plus `dx`/`dy`, exactly as `loadout.json#/definitions/hud_item`, minus the
 * `id` (it is the entry's own) and the per-item `scale` (a factory layout is always 1). The
 * numbers are in the **overlay's own design-canvas pixels** — `VoidClient.pumpUi` fits the view
 * to a 1300 x 820 canvas — because that is the space the page actually lays out in, and they
 * are on a **38-42 px vertical rhythm**, which is what it takes to stack chips that are taller
 * than that without overlapping. They are therefore NOT the tighter offsets in
 * `crates/void-loadout`'s `defaults.rs` library or in `loadout.json`'s own `examples`, whose
 * 18-20 px rhythm belongs to hand-authored product loadouts rather than to the factory layout.
 * Mods that ship off (`coordinates`, `direction`) are placed too: a placement is where a widget
 * *would* go, not whether it is drawn — the `on` setting decides that. Required on every `kind:
 * hud` mod and forbidden on every `kind: gameplay` mod; the per-mod `<id>_entry` definitions
 * are where that is enforced, so a HUD mod with no placement, or a gameplay mod with one, is a
 * schema error rather than a silent default.
 */
export const DEFAULT_HUD_PLACEMENTS = {
  fps: { anchor: 'top-left', dx: 23, dy: 23 },
  keystrokes: { anchor: 'bottom-left', dx: 31, dy: -109 },
  cps: { anchor: 'bottom-left', dx: 175, dy: -108 },
  ping: { anchor: 'top-left', dx: 23, dy: 65 },
  coordinates: { anchor: 'top-left', dx: 23, dy: 103 },
  armor_status: { anchor: 'top-right', dx: -25, dy: 299 },
  potion_effects: { anchor: 'top-right', dx: -25, dy: 23 },
  /**
   * The next row of the left column, under Coordinates at 103. **This number is deliberately
   * not `loadout.json`'s.** The Sword PvP example there places the mark at `top-left 20,58`,
   * under an fps at `dy 20` and a ping at `dy 38` — an 18-20 px rhythm, which is a
   * hand-authored product loadout rather than the factory layout. This table's rhythm is 38-42
   * px, and 58 here would land the mark on top of the ping chip at 65 rather than under it.
   * Same intent — third in the top-left stack, which is where every PvP client puts its mark —
   * expressed in the space the page actually lays out in.
   */
  watermark: { anchor: 'top-left', dx: 23, dy: 141 },
  /**
   * The next row of the same column, on this table's 38 px rhythm. Under Coordinates on
   * purpose: it is the mod a player confuses with Coordinates, and stacking them makes the
   * difference — a position, versus a facing — visible at a glance rather than argued about.
   */
  direction: { anchor: 'top-left', dx: 23, dy: 179 },
  /**
   * The sixth and last row of the left column, 38 px under Direction on this table's rhythm.
   * NOT "under Coordinates, the mod players confuse it with" — that argument belongs to
   * Direction and Direction has held `dy 179` since it was added. What this row actually is, is
   * the *bottom* of the column the eye already sweeps: the five above it are ambient readouts
   * checked between fights, and the combo is the one that means nothing except during one. Last
   * in the stack puts a fight-time number somewhere already looked at without dropping it into
   * the middle of that sweep.
   */
  combo: { anchor: 'top-left', dx: 23, dy: 217 },
  saturation: { anchor: 'top-left', dx: 23, dy: 255 },
  momentum: { anchor: 'top-left', dx: 23, dy: 293 },
  /**
   * Opens the bottom-right corner, which the factory layout does not otherwise use. Both mods
   * that live there — this and Server address, 38 px above it — are *diagnostics*: you read
   * them when something is wrong, not while it is going wrong. Keeping them opposite the
   * top-left reference stack means the glance for "is the client healthy" never crosses the
   * column holding the glance for "where am I". The insets match the corners already in use: 25
   * px in from the right edge as the top-right stack is, 23 px up from the bottom as the
   * top-left stack is down from the top.
   */
  memory: { anchor: 'bottom-right', dx: -25, dy: -23 },
  server_address: { anchor: 'bottom-right', dx: -25, dy: -61 },
  /**
   * Above the CPS chip, in the hand-and-clicks corner, and in the **175 column rather than the
   * 31 one**. The bottom-left corner has two columns because Keystrokes at `dx 31` is a *tall*
   * widget: a WASD block with a mouse row and a space bar under it is over a hundred
   * design-canvas pixels of column, so 31 is spoken for far above its own `dy -109`, and
   * anything stacked there lands on the caps. 175 is the column that already answers the same
   * question this mod does — CPS at `dy -108` is how fast the hand is going, and the held stack
   * directly above it at -146 is what the hand is going *through*. The 38 px gap is this
   * table's rhythm.
   */
  item_counter: { anchor: 'bottom-left', dx: 175, dy: -146 },
  /**
   * Third row of the bottom-right corner, 38 px above Server address (`-61`) and 76 above
   * Memory (`-23`), continuing upward the rhythm those two opened. It is not a diagnostic like
   * its two neighbours, and that is the argument for putting it there rather than against:
   * Memory and Server address are read when something is wrong, and a stopwatch is read when a
   * fight is over. Neither is a glance you take mid-swing, so both belong in the corner
   * furthest from the crosshair, and the top-left column stays what it is — the things you
   * sweep while playing.
   */
  stopwatch: { anchor: 'bottom-right', dx: -25, dy: -99 },
} as const satisfies Record<HUDModId, { anchor: HUDAnchor; dx: number; dy: number }>;
