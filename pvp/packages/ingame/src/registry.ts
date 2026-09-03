/**
 * The mod registry, transcribed from `pvp/schema/mods.json` `examples[0]` — the
 * registry VOID actually ships. `schema/` is owned by **core** and read by
 * everyone (CONTRACTS.md); this file is the read, not a second source of truth.
 * When `@void/protocol` publishes the generated registry, this collapses into a
 * re-export of it.
 *
 * Two fields are additions the schema does not carry:
 *
 *   · `category`  — the Mods panel filter taxonomy (All / HUD / PvP / Visual /
 *                   Utility). `mods.json` only classifies by `kind`
 *                   (hud | gameplay), which is a data-direction split, not the
 *                   product one the frames tab across. Values are read off
 *                   frame 244:538 tile by tile.
 *   · `label`     — the frames read "FPS display", "CPS counter" and
 *                   "Ping display" where `mods.json` says "FPS", "CPS", "Ping".
 *                   The frames win here: this is panel copy. Flagged for
 *                   reconciliation in schema/mods.json.
 *
 * `order` is the reading order of the 3 × 4 grid in the frame.
 */

import type { HypixelSafe, ModId, ModKind, ModSettingValue } from '@/bridge/protocol';

export type ModCategory = 'HUD' | 'PVP' | 'VISUAL' | 'UTILITY';

export interface ModEntry {
  id: ModId;
  kind: ModKind;
  hypixel_safe: HypixelSafe;
  /** Panel copy, per the Figma frames. */
  label: string;
  /** One-line explanation, from mods.json. */
  description: string;
  /** The 1.8.9 field / injection point. Documentation only. */
  source: string;
  /** Filter-tab taxonomy, per frame 244:538. */
  category: ModCategory;
  /** Icon name for `<Icon />`. */
  icon: string;
  /** Reading order in the tile grid. */
  order: number;
  /** Factory settings, used when a loadout omits the mod. */
  defaults: Record<string, ModSettingValue>;
}

export const MOD_REGISTRY: Record<ModId, ModEntry> = {
  fps: {
    id: 'fps',
    kind: 'hud',
    hypixel_safe: 'safe',
    label: 'FPS display',
    description: 'Frames per second, updated once per tick.',
    source: 'Minecraft.debugFPS',
    category: 'HUD',
    icon: 'gauge',
    order: 0,
    defaults: { on: true, scale: 1, opacity: 1, color: '#FFFFFF', show_label: true },
  },
  keystrokes: {
    id: 'keystrokes',
    kind: 'hud',
    hypixel_safe: 'safe',
    label: 'Keystrokes',
    description: 'WASD, mouse and spacebar tiles that light up as you press them.',
    source: 'KeyBinding.setKeyBindState, edge-triggered',
    category: 'HUD',
    icon: 'keyboard',
    order: 1,
    defaults: {
      on: true,
      scale: 1,
      opacity: 0.85,
      keybind: 'NONE',
      show_mouse: true,
      show_spacebar: true,
      show_cps: false,
    },
  },
  cps: {
    id: 'cps',
    kind: 'hud',
    hypixel_safe: 'safe',
    label: 'CPS counter',
    description: 'Clicks per second over a sliding window.',
    source: 'derived from clicks in JS',
    category: 'HUD',
    icon: 'click',
    order: 2,
    defaults: { on: true, scale: 1, opacity: 1, mode: 'left', window_ms: 1000 },
  },
  toggle_sprint: {
    id: 'toggle_sprint',
    kind: 'gameplay',
    hypixel_safe: 'safe',
    label: 'Toggle sprint',
    description: 'Latches sprint instead of holding the key.',
    source: 'KeyBinding override in onLivingUpdate',
    category: 'PVP',
    icon: 'footprints',
    order: 3,
    defaults: { on: true, mode: 'toggle', sneak_too: false, show_status: true },
  },
  crosshair: {
    id: 'crosshair',
    kind: 'gameplay',
    hypixel_safe: 'safe',
    label: 'Crosshair',
    description: 'Replaces the vanilla crosshair with a configurable one at the exact screen centre.',
    source: 'replaces vanilla crosshair pass; drawn in GL at exact center',
    category: 'VISUAL',
    icon: 'crosshair',
    order: 4,
    defaults: {
      on: false,
      style: 'cross',
      size: 5,
      thickness: 1,
      gap: 2,
      color: '#FFFFFFFF',
      outline: true,
      dynamic: false,
    },
  },
  zoom: {
    id: 'zoom',
    kind: 'gameplay',
    hypixel_safe: 'safe',
    label: 'Zoom',
    description: 'Narrows FOV while the zoom key is held.',
    source: 'FOV override while key held',
    category: 'UTILITY',
    icon: 'zoom',
    order: 5,
    defaults: { on: true, key: 'C', fov_divisor: 4, smooth: true, cinematic: false },
  },
  fullbright: {
    id: 'fullbright',
    kind: 'gameplay',
    hypixel_safe: 'grey',
    label: 'Fullbright',
    description: 'Raises gamma so caves and shadows are fully lit.',
    source: 'gammaSetting override (client-side, Watchdog-tolerated)',
    category: 'VISUAL',
    icon: 'sun',
    order: 6,
    defaults: { on: false, gamma: 10 },
  },
  hitboxes: {
    id: 'hitboxes',
    kind: 'gameplay',
    hypixel_safe: 'grey',
    label: 'Hitboxes',
    description: 'Draws entity bounding boxes.',
    source: 'RenderManager.debugBoundingBox',
    category: 'PVP',
    icon: 'box',
    order: 7,
    defaults: { on: false, line_width: 2, color: '#FFFFFFFF', show_eye_line: false },
  },
  armor_status: {
    id: 'armor_status',
    kind: 'hud',
    hypixel_safe: 'safe',
    label: 'Armor status',
    description: 'Worn armor and held item with remaining durability.',
    source: 'InventoryPlayer.armorInventory durability',
    category: 'HUD',
    icon: 'shield',
    order: 8,
    defaults: {
      on: true,
      scale: 1,
      opacity: 1,
      orientation: 'horizontal',
      show_durability: true,
      show_held_item: true,
    },
  },
  potion_effects: {
    id: 'potion_effects',
    kind: 'hud',
    hypixel_safe: 'safe',
    label: 'Potion effects',
    description: 'Active potion effects with amplifier and remaining duration.',
    source: 'getActivePotionEffects',
    category: 'HUD',
    icon: 'flask',
    order: 9,
    defaults: {
      on: true,
      scale: 1,
      opacity: 1,
      show_duration: true,
      show_amplifier: true,
      hide_ambient: false,
    },
  },
  ping: {
    id: 'ping',
    kind: 'hud',
    hypixel_safe: 'safe',
    label: 'Ping display',
    description: 'Round-trip time to the current server.',
    source: 'own NetworkPlayerInfo.responseTime',
    category: 'HUD',
    icon: 'wifi',
    order: 10,
    defaults: { on: true, scale: 1, opacity: 1, show_label: true, good_ms: 60, bad_ms: 150 },
  },
  coordinates: {
    id: 'coordinates',
    kind: 'hud',
    hypixel_safe: 'safe',
    label: 'Coordinates',
    description: 'Player position and facing direction.',
    source: 'EntityPlayerSP pos/yaw',
    category: 'HUD',
    icon: 'compass',
    order: 11,
    defaults: { on: false, scale: 1, opacity: 1, decimals: 1, show_direction: true, layout: 'stacked' },
  },
};

/** The 12 mods in the reading order of the tile grid. */
export const MOD_ORDER: ModId[] = (Object.keys(MOD_REGISTRY) as ModId[]).sort(
  (a, b) => MOD_REGISTRY[a].order - MOD_REGISTRY[b].order,
);

/** The 7 mods that own a draggable HUD item (`hud_mod_id` in mods.json). */
export const HUD_MOD_IDS = [
  'fps',
  'keystrokes',
  'cps',
  'ping',
  'coordinates',
  'armor_status',
  'potion_effects',
] as const;

/** The 5 mods `setGameplay` accepts (`gameplay_mod_id` in mods.json). */
export const GAMEPLAY_MOD_IDS = [
  'toggle_sprint',
  'fullbright',
  'hitboxes',
  'zoom',
  'crosshair',
] as const;

/** Tab set of the Mods panel, verbatim from frame 244:538. */
export const FILTER_TABS = [
  { id: 'all', label: 'All' },
  { id: 'HUD', label: 'HUD' },
  { id: 'PVP', label: 'PvP' },
  { id: 'VISUAL', label: 'Visual' },
  { id: 'UTILITY', label: 'Utility' },
] as const;

export type FilterTabId = (typeof FILTER_TABS)[number]['id'];

/**
 * Numeric / enum ranges for the settings controls, transcribed from the
 * `<id>_settings` sub-schemas of mods.json. The pane clamps to these; Java
 * clamps again and returns the value it stored, which is what the control binds
 * to (bridge.json, `setModSetting_returns`).
 */
export const SETTING_RANGES: Record<string, { min: number; max: number; step: number; unit?: string }> =
  {
    scale: { min: 0.25, max: 4, step: 0.05, unit: '×' },
    opacity: { min: 0, max: 1, step: 0.01, unit: '%' },
    window_ms: { min: 200, max: 5000, step: 50, unit: 'ms' },
    good_ms: { min: 0, max: 1000, step: 5, unit: 'ms' },
    bad_ms: { min: 0, max: 2000, step: 5, unit: 'ms' },
    decimals: { min: 0, max: 3, step: 1 },
    gamma: { min: 1, max: 15, step: 0.5 },
    line_width: { min: 0.5, max: 5, step: 0.5 },
    fov_divisor: { min: 1.1, max: 10, step: 0.1, unit: '×' },
    size: { min: 1, max: 20, step: 1, unit: 'px' },
    thickness: { min: 1, max: 5, step: 1, unit: 'px' },
    gap: { min: 0, max: 10, step: 1, unit: 'px' },
    /* `corner_radius` appears on the Mod settings frame (244:834) but is not in
       mods.json. It is rendered read-through-clamp like the rest; if it is kept,
       it needs adding to keystrokes_settings. */
    corner_radius: { min: 0, max: 20, step: 1, unit: 'px' },
  };
