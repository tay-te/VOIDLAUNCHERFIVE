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

import type { ModId } from './schema.js';

/**
 * The glyph each mod names — `mod_entry.icon`, lifted out of the shipped registry.
 *
 * Every value keeps its **literal** type, which is the whole reason this is a generated
 * table and not a fold over `MOD_REGISTRY`: `@void/ui` re-exports it as
 * `MOD_ICONS` under a `satisfies Record<ModId, IconName>`, so a schema that names a glyph
 * the icon set cannot draw is a type error there rather than an empty box in game.
 *
 * Not to be confused with `ICON_NAMES` in `@void/ui`, which is the set of glyphs that
 * *can* be drawn. This is the mapping from mod to one of them, and nothing in this package
 * knows the difference — by design: `@void/protocol` must not depend on `@void/ui`.
 *
 * Prefer `@void/ui`'s `MOD_ICONS` in an application. Reach for this only where `@void/ui`
 * is not a dependency, and note it is the *registry's* view: the overlay's `?fake=` padding
 * extends `MOD_ICONS`, never this.
 */
export const MOD_ICON_NAMES = {
  "fps": "gauge",
  "keystrokes": "keyboard",
  "cps": "cursor-click",
  "ping": "wifi",
  "coordinates": "compass",
  "armor_status": "shield",
  "potion_effects": "flask",
  "watermark": "watermark",
  "toggle_sprint": "bolt",
  "fullbright": "sun",
  "hitboxes": "cube",
  "zoom": "zoom",
  "crosshair": "crosshair"
} as const satisfies Record<ModId, string>;
