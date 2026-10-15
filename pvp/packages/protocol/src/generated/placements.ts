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
  fps: { anchor: 'top-left', dx: 20, dy: 20 },
  keystrokes: { anchor: 'bottom-left', dx: 20, dy: -20 },
  /**
   * Beside FPS in the top row, as the Cartridge fight HUD (Figma 630:2) draws it: FPS 96 wide
   * at 20, CPS 128 wide at 122, Ping at 256, a 6 px gap between 56 px modules.
   */
  cps: { anchor: 'top-left', dx: 122, dy: 20 },
  ping: { anchor: 'top-left', dx: 256, dy: 20 },
  coordinates: { anchor: 'top-left', dx: 20, dy: 128 },
  armor_status: { anchor: 'bottom-right', dx: -20, dy: -20 },
  potion_effects: { anchor: 'top-right', dx: -20, dy: 20 },
  /**
   * Under the top row of modules (56 px tall at 20, so 84 is the next row with the 8 px rhythm
   * the Cartridge HUD uses). Third thing in the top-left reading order, where every PvP client
   * puts its mark.
   */
  watermark: { anchor: 'top-left', dx: 20, dy: 84 },
  /**
   * Under Coordinates, one 64 px Cartridge row down: the mod a player confuses with
   * Coordinates, stacked so the difference — a position versus a facing — is visible at a
   * glance.
   */
  direction: { anchor: 'top-left', dx: 20, dy: 192 },
  /**
   * Just below and right of the crosshair, as the Cartridge fight HUD (Figma 630:2) places it:
   * the one number that means something only mid-fight belongs where the eyes already are
   * during one.
   */
  combo: { anchor: 'center', dx: 40, dy: 24 },
  saturation: { anchor: 'top-left', dx: 20, dy: 256 },
  momentum: { anchor: 'top-left', dx: 20, dy: 320 },
  /**
   * Above Armor status (160 tall at -20) in the bottom-right column, the corner furthest from
   * the crosshair: diagnostics are read when something is wrong, not while it is going wrong.
   */
  memory: { anchor: 'bottom-right', dx: -20, dy: -188 },
  /**
   * The next 64 px row of the bottom-right column, over Memory: the other diagnostic.
   */
  server_address: { anchor: 'bottom-right', dx: -20, dy: -252 },
  /**
   * The second bottom-left column, right of Keystrokes (156 wide at 20, so 184 with the 8 px
   * gap): the hand-and-clicks corner, what the hand is going through.
   */
  item_counter: { anchor: 'bottom-left', dx: 184, dy: -20 },
  /**
   * Top of the bottom-right column: read when a fight is over, never mid-swing, so it shares
   * the corner furthest from the crosshair.
   */
  stopwatch: { anchor: 'bottom-right', dx: -20, dy: -316 },
  /**
   * Right edge, vertically centred — where vanilla draws the sidebar, so turning the mod on
   * restyles it in place before anyone moves it.
   */
  scoreboard: { anchor: 'right', dx: -20, dy: 0 },
  /**
   * Above the Item counter in the 184 column, one 64 px Cartridge row up: bottom to top, what
   * the hand holds and how many heals are left.
   */
  potion_counter: { anchor: 'bottom-left', dx: 184, dy: -84 },
  /**
   * The left column's last row on the 64 px Cartridge rhythm, beside Momentum: the numbers a
   * fight produces read together. Combo, the other one, sits beside the crosshair.
   */
  reach: { anchor: 'top-left', dx: 20, dy: 384 },
} as const satisfies Record<HUDModId, { anchor: HUDAnchor; dx: number; dy: number }>;
