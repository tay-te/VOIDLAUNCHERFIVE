/**
 * The mod registry, plus the one thing the launcher adds to it: how each setting is
 * *rendered*.
 *
 * The registry itself — ids, kinds, `hypixel_safe` classes, labels, descriptions and
 * factory defaults — comes from `@void/protocol`, generated from `pvp/schema/mods.json`.
 * A mod is declared in exactly one place, and it is not here.
 *
 * What is here is `SETTING_SPECS`: which control a setting gets, and its range. That is
 * a UI decision, not a contract one, and it is derived from the settings sub-schema
 * rather than chosen per mod — booleans get a switch, enums a segmented control,
 * numbers a slider with the schema's own bounds. When `@void/ui` grows a schema-driven
 * settings renderer this file shrinks to the category map.
 */

import {
  MOD_IDS,
  MOD_REGISTRY,
  enabledMods,
  getModEntry,
  getModLabel,
  isModEnabled,
  resolveModSettings,
} from '@void/protocol';
import type { Loadout, ModId } from './protocol';

export { MOD_IDS, MOD_REGISTRY, getModEntry, getModLabel, isModEnabled, enabledMods };

/** Which FilterTab a mod sits under. The tab set is `All/HUD/PvP/Visual/Utility`. */
export type ModCategory = 'HUD' | 'PVP' | 'VISUAL' | 'UTILITY';

/**
 * The Figma's grouping (`244:110`), which is finer than the registry's `kind`: a
 * gameplay mod can be a PvP one (Toggle sprint, Hitboxes), a visual one (Fullbright,
 * Crosshair) or a utility (Zoom). Every HUD mod is `HUD`.
 */
const CATEGORY_OVERRIDES: Partial<Record<ModId, ModCategory>> = {
  toggle_sprint: 'PVP',
  hitboxes: 'PVP',
  fullbright: 'VISUAL',
  crosshair: 'VISUAL',
  zoom: 'UTILITY',
};

export function categoryOf(id: ModId): ModCategory {
  return CATEGORY_OVERRIDES[id] ?? 'HUD';
}

export interface SettingSpec {
  key: string;
  label: string;
  control: 'switch' | 'slider' | 'select' | 'keybind' | 'color';
  min?: number;
  max?: number;
  step?: number;
  options?: readonly string[];
  /** How the value reads next to the label: `1.0×`, `85%`, `1000 ms`. */
  format?: 'multiplier' | 'percent' | 'ms' | 'plain';
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

/** Bounds and control kinds, transcribed from each mod's settings sub-schema. */
export const SETTING_SPECS: Readonly<Record<ModId, readonly SettingSpec[]>> = {
  fps: [SCALE, OPACITY, { key: 'show_label', label: 'Show label', control: 'switch' }],
  keystrokes: [
    SCALE,
    OPACITY,
    { key: 'keybind', label: 'Keybind', control: 'keybind' },
    { key: 'show_mouse', label: 'Show mouse', control: 'switch' },
    { key: 'show_spacebar', label: 'Show spacebar', control: 'switch' },
    { key: 'show_cps', label: 'Show CPS', control: 'switch' },
  ],
  cps: [
    SCALE,
    OPACITY,
    { key: 'mode', label: 'Buttons', control: 'select', options: ['left', 'right', 'both'] },
    { key: 'window_ms', label: 'Window', control: 'slider', min: 200, max: 5000, step: 50, format: 'ms' },
  ],
  ping: [
    SCALE,
    OPACITY,
    { key: 'show_label', label: 'Show label', control: 'switch' },
    { key: 'good_ms', label: 'Good under', control: 'slider', min: 0, max: 1000, step: 5, format: 'ms' },
    { key: 'bad_ms', label: 'Bad over', control: 'slider', min: 0, max: 2000, step: 5, format: 'ms' },
  ],
  coordinates: [
    SCALE,
    OPACITY,
    { key: 'decimals', label: 'Decimals', control: 'slider', min: 0, max: 3, step: 1, format: 'plain' },
    { key: 'show_direction', label: 'Show direction', control: 'switch' },
    { key: 'layout', label: 'Layout', control: 'select', options: ['stacked', 'inline'] },
  ],
  armor_status: [
    SCALE,
    OPACITY,
    { key: 'orientation', label: 'Orientation', control: 'select', options: ['horizontal', 'vertical'] },
    { key: 'show_durability', label: 'Show durability', control: 'switch' },
    { key: 'show_held_item', label: 'Show held item', control: 'switch' },
  ],
  potion_effects: [
    SCALE,
    OPACITY,
    { key: 'show_duration', label: 'Show duration', control: 'switch' },
    { key: 'show_amplifier', label: 'Show amplifier', control: 'switch' },
    { key: 'hide_ambient', label: 'Hide ambient', control: 'switch' },
  ],
  toggle_sprint: [
    { key: 'mode', label: 'Mode', control: 'select', options: ['toggle', 'hold'] },
    { key: 'sneak_too', label: 'Sneak too', control: 'switch' },
    { key: 'show_status', label: 'Show status', control: 'switch' },
  ],
  fullbright: [
    { key: 'gamma', label: 'Gamma', control: 'slider', min: 1, max: 15, step: 0.5, format: 'plain' },
  ],
  hitboxes: [
    { key: 'line_width', label: 'Line width', control: 'slider', min: 0.5, max: 5, step: 0.5, format: 'plain' },
    { key: 'color', label: 'Colour', control: 'color' },
    { key: 'show_eye_line', label: 'Show eye line', control: 'switch' },
  ],
  zoom: [
    { key: 'key', label: 'Keybind', control: 'keybind' },
    { key: 'fov_divisor', label: 'Amount', control: 'slider', min: 1.1, max: 10, step: 0.1, format: 'multiplier' },
    { key: 'smooth', label: 'Smooth', control: 'switch' },
    { key: 'cinematic', label: 'Cinematic', control: 'switch' },
  ],
  crosshair: [
    {
      key: 'style',
      label: 'Style',
      control: 'select',
      options: ['default', 'cross', 'dot', 'circle', 't_shape', 'none'],
    },
    { key: 'size', label: 'Size', control: 'slider', min: 1, max: 20, step: 1, format: 'plain' },
    { key: 'thickness', label: 'Thickness', control: 'slider', min: 1, max: 5, step: 1, format: 'plain' },
    { key: 'gap', label: 'Gap', control: 'slider', min: 0, max: 10, step: 1, format: 'plain' },
    { key: 'color', label: 'Colour', control: 'color' },
    { key: 'outline', label: 'Outline', control: 'switch' },
    { key: 'dynamic', label: 'Dynamic', control: 'switch' },
  ],
};

/**
 * Reading order of the Mods grid, taken from the Figma rather than from registry order:
 * the frame leads with the mods that are on by default and trails with the ones that
 * are off, which is what makes the grid read at a glance.
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

export const FILTER_TABS = ['All', 'HUD', 'PvP', 'Visual', 'Utility'] as const;
export type FilterTab = (typeof FILTER_TABS)[number];

export function matchesTab(id: ModId, tab: FilterTab): boolean {
  return tab === 'All' || categoryOf(id) === tab.toUpperCase();
}

/**
 * The settings a mod actually runs with: the loadout's own values over the registry
 * defaults. A mod the loadout omits falls back entirely — that is what keeps a loadout
 * written before a mod existed valid afterwards.
 */
export function effectiveState(
  loadout: Pick<Loadout, 'mods'>,
  id: ModId,
): Record<string, unknown> {
  return resolveModSettings(loadout, id) as unknown as Record<string, unknown>;
}

export function isOn(loadout: Pick<Loadout, 'mods'>, id: ModId): boolean {
  return isModEnabled(loadout, id);
}

export function enabledCount(loadout: Pick<Loadout, 'mods'>): number {
  return enabledMods(loadout).length;
}

export function settingsFor(id: ModId): readonly SettingSpec[] {
  return SETTING_SPECS[id];
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
