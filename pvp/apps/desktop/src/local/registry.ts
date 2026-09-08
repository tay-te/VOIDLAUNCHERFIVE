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
  getModDefaults,
  getModEntry,
  getModLabel,
  isModEnabled,
  resolveModSettings,
} from '@void/protocol';
import type { Loadout, ModId } from './protocol';

export {
  MOD_IDS,
  MOD_REGISTRY,
  getModDefaults,
  getModEntry,
  getModLabel,
  isModEnabled,
  enabledMods,
};

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

/**
 * A category's hue (quiet-cell-system §1).
 *
 * A mod's accent is its category's hue, exposed as `--hue` on the mod's root element so
 * that every accent-consuming rule underneath can read `var(--hue, var(--accent))`.
 * Nothing mod-scoped hard-codes `--accent`.
 */
export const CATEGORY_HUE: Readonly<Record<ModCategory, string>> = {
  HUD: 'var(--hue-hud)',
  PVP: 'var(--hue-pvp)',
  VISUAL: 'var(--hue-visual)',
  UTILITY: 'var(--hue-utility)',
};

/** The `--hue` value to put on a mod's root element. */
export function hueOf(id: ModId): string {
  return CATEGORY_HUE[categoryOf(id)];
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
  // Added when the watermark landed as the thirteenth mod. This table is
  // `Record<ModId, …>`, so a missing entry is a type error rather than a property
  // pane that quietly renders nothing — which is the only reason it was caught.
  watermark: [
    SCALE,
    OPACITY,
    { key: 'style', label: 'Style', control: 'select', options: ['full', 'mark', 'word'] },
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
    // 0-2, not 0-3: the in-game `tick` sensor rounds the position to 2 dp on the wire.
    { key: 'decimals', label: 'Decimals', control: 'slider', min: 0, max: 2, step: 1, format: 'plain' },
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
 *
 * The order below is `design/screens/launcher/Launcher-Mods.png` cell for cell — FPS
 * display, Keystrokes, CPS counter, Armor status, Crosshair on the first row; Potion
 * effects, Zoom, Toggle sprint, Reach display, Fullbright on the second — with the two
 * mods the frame's library does not carry trailing it.
 */
export const MOD_GRID_ORDER: readonly ModId[] = [
  'fps',
  'keystrokes',
  'cps',
  'armor_status',
  'crosshair',
  'potion_effects',
  'zoom',
  'toggle_sprint',
  'hitboxes',
  'fullbright',
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

/**
 * A mod's factory defaults, as an open bag — what "Reset to default" restores and what
 * every preset is derived from. Same cast as `effectiveState`, for the same reason: the
 * generated per-mod shapes are closed, and the UI reads them by key.
 */
export function defaultsFor(id: ModId): Record<string, unknown> {
  return { ...(getModDefaults(id) as unknown as Record<string, unknown>) };
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

/**
 * Settings that are *spatial* — position, size, placement.
 *
 * §8: those never appear as list rows. They live on the setup page's preview as drag
 * handles, so `propertiesFor` is what decides the shape of the properties column
 * (1 → no list, 2–4 → flat, 5+ → two groups) and `scale` is not part of that count.
 */
const SPATIAL_KEYS: ReadonlySet<string> = new Set(['scale']);

/** The settings that get a row in the properties column. */
export function propertiesFor(id: ModId): readonly SettingSpec[] {
  return SETTING_SPECS[id].filter((spec) => !SPATIAL_KEYS.has(spec.key));
}

/** The spatial settings, which the preview owns. */
export function spatialFor(id: ModId): readonly SettingSpec[] {
  return SETTING_SPECS[id].filter((spec) => SPATIAL_KEYS.has(spec.key));
}

/** Every mod in a category, in grid order. */
export function modsInCategory(category: ModCategory): readonly ModId[] {
  return MOD_GRID_ORDER.filter((id) => categoryOf(id) === category);
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
