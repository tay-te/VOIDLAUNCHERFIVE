/**
 * The mod registry: the 12 rows of `pvp/schema/mods.json`, plus the extra bits the
 * Mods screen needs to render a tile and its settings pane.
 *
 * TODO(integrate): this is a transcription of `schema/mods.json`'s registry and its
 * per-mod settings sub-schemas. `mods.json` is written by `core` and read by everyone
 * (CONTRACTS.md), so the right home for this is `@void/protocol` — as generated data,
 * not just generated types. When it exports the registry, delete `MOD_REGISTRY` and
 * `SETTING_SCHEMA` here and import them; `hypixelReady`, the tile grid and the
 * settings pane all read through this module's helpers and will not move.
 *
 * Two rules from the schema that the UI must honour and that are easy to lose:
 *
 * 1. A loadout may omit a mod. The omitted mod falls back to `defaults` here — that is
 *    what keeps a loadout written before a mod existed valid afterwards. Never write
 *    all twelve blocks into a new loadout.
 * 2. Settings values are validated by the sub-schema, so the control a setting gets is
 *    a property of the schema, not of the mod: booleans get a switch, enums get a
 *    segmented control, numbers get a slider with the schema's own min/max/step.
 */

import type { HypixelSafe, ModId, ModKind, ModState } from './protocol';

/** Which FilterTab a mod sits under — the tab set is `All/HUD/PvP/Visual/Utility`. */
export type ModCategory = 'HUD' | 'PVP' | 'VISUAL' | 'UTILITY';

export interface SettingSpec {
  key: string;
  label: string;
  /** Rendered control. Derived from the sub-schema's type, not chosen per mod. */
  control: 'switch' | 'slider' | 'select' | 'keybind' | 'color';
  min?: number;
  max?: number;
  step?: number;
  options?: readonly string[];
  /** How the value reads next to the label: `1.0×`, `85%`, `1000 ms`. */
  format?: 'multiplier' | 'percent' | 'ms' | 'plain';
}

export interface ModEntry {
  id: ModId;
  kind: ModKind;
  hypixel_safe: HypixelSafe;
  category: ModCategory;
  label: string;
  description: string;
  /** The 1.8.9 field or injection point, quoted from the §3 table. Docs only. */
  source: string;
  defaults: ModState;
  settings: readonly SettingSpec[];
}

const SCALE: SettingSpec = {
  key: 'scale',
  label: 'Scale',
  control: 'slider',
  min: 0.25,
  max: 4,
  step: 0.05,
  format: 'multiplier',
};
const OPACITY: SettingSpec = {
  key: 'opacity',
  label: 'Opacity',
  control: 'slider',
  min: 0,
  max: 1,
  step: 0.01,
  format: 'percent',
};

export const MOD_REGISTRY: Readonly<Record<ModId, ModEntry>> = {
  fps: {
    id: 'fps',
    kind: 'hud',
    hypixel_safe: 'safe',
    category: 'HUD',
    label: 'FPS display',
    description: 'Frames per second, updated once per tick.',
    source: 'Minecraft.debugFPS',
    defaults: { on: true, scale: 1, opacity: 1, color: '#FFFFFF', show_label: true },
    settings: [SCALE, OPACITY, { key: 'show_label', label: 'Show label', control: 'switch' }],
  },
  keystrokes: {
    id: 'keystrokes',
    kind: 'hud',
    hypixel_safe: 'safe',
    category: 'HUD',
    label: 'Keystrokes',
    description: 'WASD, mouse and spacebar tiles that light up as you press them.',
    source: 'KeyBinding.setKeyBindState, edge-triggered',
    defaults: {
      on: true,
      scale: 1,
      opacity: 0.85,
      keybind: 'NONE',
      show_mouse: true,
      show_spacebar: true,
      show_cps: false,
    },
    settings: [
      SCALE,
      OPACITY,
      { key: 'keybind', label: 'Keybind', control: 'keybind' },
      { key: 'show_mouse', label: 'Show mouse', control: 'switch' },
      { key: 'show_spacebar', label: 'Show spacebar', control: 'switch' },
      { key: 'show_cps', label: 'Show CPS', control: 'switch' },
    ],
  },
  cps: {
    id: 'cps',
    kind: 'hud',
    hypixel_safe: 'safe',
    category: 'HUD',
    label: 'CPS counter',
    description: 'Clicks per second over a sliding window.',
    source: 'derived from clicks in JS',
    defaults: { on: true, scale: 1, opacity: 1, mode: 'left', window_ms: 1000 },
    settings: [
      SCALE,
      OPACITY,
      { key: 'mode', label: 'Buttons', control: 'select', options: ['left', 'right', 'both'] },
      { key: 'window_ms', label: 'Window', control: 'slider', min: 200, max: 5000, step: 50, format: 'ms' },
    ],
  },
  ping: {
    id: 'ping',
    kind: 'hud',
    hypixel_safe: 'safe',
    category: 'HUD',
    label: 'Ping display',
    description: 'Round-trip time to the current server.',
    source: 'own NetworkPlayerInfo.responseTime',
    defaults: { on: true, scale: 1, opacity: 1, show_label: true, good_ms: 60, bad_ms: 150 },
    settings: [
      SCALE,
      OPACITY,
      { key: 'show_label', label: 'Show label', control: 'switch' },
      { key: 'good_ms', label: 'Good under', control: 'slider', min: 0, max: 1000, step: 5, format: 'ms' },
      { key: 'bad_ms', label: 'Bad over', control: 'slider', min: 0, max: 2000, step: 5, format: 'ms' },
    ],
  },
  coordinates: {
    id: 'coordinates',
    kind: 'hud',
    hypixel_safe: 'safe',
    category: 'HUD',
    label: 'Coordinates',
    description: 'Player position and facing direction.',
    source: 'EntityPlayerSP pos/yaw',
    defaults: { on: false, scale: 1, opacity: 1, decimals: 1, show_direction: true, layout: 'stacked' },
    settings: [
      SCALE,
      OPACITY,
      { key: 'decimals', label: 'Decimals', control: 'slider', min: 0, max: 3, step: 1, format: 'plain' },
      { key: 'show_direction', label: 'Show direction', control: 'switch' },
      { key: 'layout', label: 'Layout', control: 'select', options: ['stacked', 'inline'] },
    ],
  },
  armor_status: {
    id: 'armor_status',
    kind: 'hud',
    hypixel_safe: 'safe',
    category: 'HUD',
    label: 'Armor status',
    description: 'Worn armor and held item with remaining durability.',
    source: 'InventoryPlayer.armorInventory durability',
    defaults: {
      on: true,
      scale: 1,
      opacity: 1,
      orientation: 'horizontal',
      show_durability: true,
      show_held_item: true,
    },
    settings: [
      SCALE,
      OPACITY,
      { key: 'orientation', label: 'Orientation', control: 'select', options: ['horizontal', 'vertical'] },
      { key: 'show_durability', label: 'Show durability', control: 'switch' },
      { key: 'show_held_item', label: 'Show held item', control: 'switch' },
    ],
  },
  potion_effects: {
    id: 'potion_effects',
    kind: 'hud',
    hypixel_safe: 'safe',
    category: 'HUD',
    label: 'Potion effects',
    description: 'Active potion effects with amplifier and remaining duration.',
    source: 'getActivePotionEffects',
    defaults: {
      on: true,
      scale: 1,
      opacity: 1,
      show_duration: true,
      show_amplifier: true,
      hide_ambient: false,
    },
    settings: [
      SCALE,
      OPACITY,
      { key: 'show_duration', label: 'Show duration', control: 'switch' },
      { key: 'show_amplifier', label: 'Show amplifier', control: 'switch' },
      { key: 'hide_ambient', label: 'Hide ambient', control: 'switch' },
    ],
  },
  toggle_sprint: {
    id: 'toggle_sprint',
    kind: 'gameplay',
    hypixel_safe: 'safe',
    category: 'PVP',
    label: 'Toggle sprint',
    description: 'Latches sprint instead of holding the key.',
    source: 'KeyBinding override in onLivingUpdate',
    defaults: { on: true, mode: 'toggle', sneak_too: false, show_status: true },
    settings: [
      { key: 'mode', label: 'Mode', control: 'select', options: ['toggle', 'hold'] },
      { key: 'sneak_too', label: 'Sneak too', control: 'switch' },
      { key: 'show_status', label: 'Show status', control: 'switch' },
    ],
  },
  fullbright: {
    id: 'fullbright',
    kind: 'gameplay',
    hypixel_safe: 'grey',
    category: 'VISUAL',
    label: 'Fullbright',
    description: 'Raises gamma so caves and shadows are fully lit.',
    source: 'gammaSetting override (client-side, Watchdog-tolerated)',
    defaults: { on: false, gamma: 10 },
    settings: [{ key: 'gamma', label: 'Gamma', control: 'slider', min: 1, max: 15, step: 0.5, format: 'plain' }],
  },
  hitboxes: {
    id: 'hitboxes',
    kind: 'gameplay',
    hypixel_safe: 'grey',
    category: 'PVP',
    label: 'Hitboxes',
    description: 'Draws entity bounding boxes.',
    source: 'RenderManager.debugBoundingBox',
    defaults: { on: false, line_width: 2, color: '#FFFFFFFF', show_eye_line: false },
    settings: [
      { key: 'line_width', label: 'Line width', control: 'slider', min: 0.5, max: 5, step: 0.5, format: 'plain' },
      { key: 'color', label: 'Colour', control: 'color' },
      { key: 'show_eye_line', label: 'Show eye line', control: 'switch' },
    ],
  },
  zoom: {
    id: 'zoom',
    kind: 'gameplay',
    hypixel_safe: 'safe',
    category: 'UTILITY',
    label: 'Zoom',
    description: 'Narrows FOV while the zoom key is held.',
    source: 'FOV override while key held',
    defaults: { on: true, key: 'C', fov_divisor: 4, smooth: true, cinematic: false },
    settings: [
      { key: 'key', label: 'Keybind', control: 'keybind' },
      { key: 'fov_divisor', label: 'Amount', control: 'slider', min: 1.1, max: 10, step: 0.1, format: 'multiplier' },
      { key: 'smooth', label: 'Smooth', control: 'switch' },
      { key: 'cinematic', label: 'Cinematic', control: 'switch' },
    ],
  },
  crosshair: {
    id: 'crosshair',
    kind: 'gameplay',
    hypixel_safe: 'safe',
    category: 'VISUAL',
    label: 'Crosshair',
    description: 'Replaces the vanilla crosshair with a configurable one at the exact screen centre.',
    source: 'replaces vanilla crosshair pass; drawn in GL at exact center',
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
    settings: [
      { key: 'style', label: 'Style', control: 'select', options: ['default', 'cross', 'dot', 'circle', 't_shape', 'none'] },
      { key: 'size', label: 'Size', control: 'slider', min: 1, max: 20, step: 1, format: 'plain' },
      { key: 'thickness', label: 'Thickness', control: 'slider', min: 1, max: 5, step: 1, format: 'plain' },
      { key: 'gap', label: 'Gap', control: 'slider', min: 0, max: 10, step: 1, format: 'plain' },
      { key: 'color', label: 'Colour', control: 'color' },
      { key: 'outline', label: 'Outline', control: 'switch' },
      { key: 'dynamic', label: 'Dynamic', control: 'switch' },
    ],
  },
};

/**
 * Reading order of the Mods grid, taken from the Figma (`244:110`) rather than from
 * the registry's alphabetical key order — the frame groups the on-by-default HUD mods
 * first and the off ones last, which is what makes the grid read.
 */
export const MOD_GRID_ORDER: readonly ModId[] = [
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
];

export const MOD_IDS = MOD_GRID_ORDER;

export const FILTER_TABS = ['All', 'HUD', 'PvP', 'Visual', 'Utility'] as const;
export type FilterTab = (typeof FILTER_TABS)[number];

export function matchesTab(entry: ModEntry, tab: FilterTab): boolean {
  if (tab === 'All') return true;
  return entry.category === tab.toUpperCase();
}

/**
 * The effective state of a mod in a loadout: the loadout's own values layered over the
 * registry defaults. Rule 1 above lives here.
 */
export function effectiveState(mods: Partial<Record<ModId, ModState>>, id: ModId): ModState {
  const entry = MOD_REGISTRY[id];
  return { ...entry.defaults, ...(mods[id] ?? {}) } as ModState;
}

export function isOn(mods: Partial<Record<ModId, ModState>>, id: ModId): boolean {
  return effectiveState(mods, id).on === true;
}

export function enabledCount(mods: Partial<Record<ModId, ModState>>): number {
  return MOD_IDS.filter((id) => isOn(mods, id)).length;
}

/** Format a setting value the way the settings pane prints it next to its label. */
export function formatSetting(spec: SettingSpec, value: unknown): string {
  const n = typeof value === 'number' ? value : Number(value);
  switch (spec.format) {
    case 'multiplier':
      return `${n.toFixed(1)}×`;
    case 'percent':
      return `${Math.round(n * 100)}%`;
    case 'ms':
      return `${Math.round(n)} ms`;
    default:
      return Number.isFinite(n) ? String(Number(n.toFixed(2))) : String(value ?? '');
  }
}
