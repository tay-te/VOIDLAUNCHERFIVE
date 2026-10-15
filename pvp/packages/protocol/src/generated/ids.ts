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

import type { GameplayModId, HUDModId, ModId } from './schema.js';

/** Every mod id, in registry order. */
export const MOD_IDS = [
  'fps',
  'keystrokes',
  'cps',
  'ping',
  'coordinates',
  'armor_status',
  'potion_effects',
  'watermark',
  'toggle_sprint',
  'fullbright',
  'hitboxes',
  'zoom',
  'crosshair',
  'direction',
  'combo',
  'saturation',
  'momentum',
  'memory',
  'server_address',
  'item_counter',
  'stopwatch',
  'fov',
  'toggle_sneak',
  'overlay',
  'freelook',
  'hit_color',
  'damage_tint',
  'old_animations',
  'old_input',
  'culling',
  'particles',
  'frame_limiter',
  'block_outline',
  'shiny_pots',
  'clear_glass',
  'nametags',
  'scoreboard',
  'gui_scale',
  'fog',
  'menu_blur',
  'time_weather',
  'potion_counter',
  'reach',
  'chat',
  'cosmetics',
] as const satisfies readonly ModId[];

/** The mods that own a draggable HUD item, in registry order. */
export const HUD_MOD_IDS = [
  'fps',
  'keystrokes',
  'cps',
  'ping',
  'coordinates',
  'armor_status',
  'potion_effects',
  'watermark',
  'direction',
  'combo',
  'saturation',
  'momentum',
  'memory',
  'server_address',
  'item_counter',
  'stopwatch',
  'scoreboard',
  'potion_counter',
  'reach',
] as const satisfies readonly HUDModId[];

/** The mods an actuator Mixin reads every frame, in registry order. */
export const GAMEPLAY_MOD_IDS = [
  'toggle_sprint',
  'fullbright',
  'hitboxes',
  'zoom',
  'crosshair',
  'fov',
  'toggle_sneak',
  'overlay',
  'freelook',
  'hit_color',
  'damage_tint',
  'old_animations',
  'old_input',
  'culling',
  'particles',
  'frame_limiter',
  'block_outline',
  'shiny_pots',
  'clear_glass',
  'nametags',
  'gui_scale',
  'fog',
  'menu_blur',
  'time_weather',
  'chat',
  'cosmetics',
] as const satisfies readonly GameplayModId[];
