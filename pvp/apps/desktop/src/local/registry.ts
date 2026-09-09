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

/**
 * Bounds and control kinds, transcribed from each mod's settings sub-schema.
 *
 * Still hand-written, and safely so: the type is `Record<ModId, …>`, so a mod added to the
 * schema fails `pnpm typecheck` here until it has a row. That is the whole difference between
 * this table and the ones the codegen pass removed — those were `Partial`, or a `switch` with a
 * default, or an array under a `satisfies` that only checked its elements. This one cannot be
 * forgotten, only filled in wrong, and filling it in wrong is visible on the screen it draws.
 *
 * The launcher deliberately does not share the overlay's control layer: `packages/ingame` reads
 * `SETTING_BOUNDS` out of `@void/protocol`, but that carries bounds, not which control draws
 * them, and the two applications genuinely differ there — the launcher has no HUD to preview
 * against, so it draws a plain form.
 */
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
  direction: [
    SCALE,
    OPACITY,
    { key: 'style', label: 'Style', control: 'select', options: ['letter', 'word', 'axis'] },
    { key: 'show_degrees', label: 'Show degrees', control: 'switch' },
  ],
  // The Wave 2 readout sweep. Six rows, added because this table is `Record<ModId, …>` and
  // would not compile without them — which is the property the codegen pass was after.
  combo: [
    SCALE,
    OPACITY,
    { key: 'reset_ms', label: 'Reset after', control: 'slider', min: 500, max: 10000, step: 100, format: 'ms' },
    { key: 'show_label', label: 'Show label', control: 'switch' },
  ],
  // Wave 9's sidebar customiser. `sidebar_scale` rather than `scale` because `SETTING_BOUNDS`
  // is keyed by the bare name and this mod's range is not the shared HUD block's.
  scoreboard: [
    { key: 'hide', label: 'Hide', control: 'switch' },
    { key: 'sidebar_scale', label: 'Sidebar scale', control: 'slider', min: 0.5, max: 1.5, step: 0.05, format: 'multiplier' },
    { key: 'offset_x', label: 'Horizontal offset', control: 'slider', min: -200, max: 200, step: 1, format: 'plain' },
    { key: 'offset_y', label: 'Vertical offset', control: 'slider', min: -200, max: 200, step: 1, format: 'plain' },
  ],
  // Wave 8's two, both computed by the page itself — no sensor either side.
  clock: [
    SCALE,
    OPACITY,
    { key: 'format', label: 'Format', control: 'select', options: ['h24', 'h12'] },
    { key: 'show_seconds', label: 'Show seconds', control: 'switch' },
  ],
  cps_graph: [
    SCALE,
    OPACITY,
    { key: 'mode', label: 'Buttons', control: 'select', options: ['left', 'right', 'both'] },
    { key: 'window_s', label: 'Window', control: 'slider', min: 10, max: 60, step: 1, format: 'plain' },
    { key: 'show_figure', label: 'Show figure', control: 'switch' },
  ],
  // Wave 7's one readout, off counters the wire already carried.
  hit_trade: [
    SCALE,
    OPACITY,
    { key: 'style', label: 'Style', control: 'select', options: ['traded', 'ratio', 'dealt'] },
    { key: 'show_bar', label: 'Show bar', control: 'switch' },
    { key: 'show_label', label: 'Show label', control: 'switch' },
  ],
  saturation: [
    SCALE,
    OPACITY,
    { key: 'style', label: 'Style', control: 'select', options: ['number', 'bar', 'both'] },
    { key: 'decimals', label: 'Decimals', control: 'slider', min: 0, max: 2, step: 1, format: 'plain' },
    { key: 'show_label', label: 'Show label', control: 'switch' },
  ],
  momentum: [
    SCALE,
    OPACITY,
    { key: 'unit', label: 'Unit', control: 'select', options: ['bps', 'kmh'] },
    { key: 'decimals', label: 'Decimals', control: 'slider', min: 0, max: 2, step: 1, format: 'plain' },
    { key: 'show_label', label: 'Show label', control: 'switch' },
  ],
  memory: [
    SCALE,
    OPACITY,
    { key: 'style', label: 'Style', control: 'select', options: ['used', 'used_of_max', 'percent'] },
    { key: 'show_bar', label: 'Show bar', control: 'switch' },
    { key: 'show_label', label: 'Show label', control: 'switch' },
  ],
  server_address: [
    SCALE,
    OPACITY,
    { key: 'style', label: 'Style', control: 'select', options: ['short', 'full'] },
  ],
  item_counter: [
    SCALE,
    OPACITY,
    { key: 'show_label', label: 'Show label', control: 'switch' },
    // 0 disables the warn treatment, which is why the floor is 0 rather than 1.
    { key: 'low_threshold', label: 'Warn at or below', control: 'slider', min: 0, max: 64, step: 1, format: 'plain' },
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
  ],
  fullbright: [
    { key: 'gamma', label: 'Gamma', control: 'slider', min: 1, max: 15, step: 0.5, format: 'plain' },
  ],
  // Wave 4 (docs/mod-roster.md §7, "the four that change how the client feels", plus the
  // stopwatch the input path unblocked). Gameplay mods carry no SCALE/OPACITY: they draw
  // nothing of their own, so there is nothing to size or fade.
  stopwatch: [
    SCALE,
    OPACITY,
    { key: 'format', label: 'Format', control: 'select', options: ['auto', 'mmss', 'hmmss'] },
    { key: 'show_millis', label: 'Show hundredths', control: 'switch' },
    { key: 'start_key', label: 'Start / stop', control: 'keybind' },
    { key: 'reset_key', label: 'Reset', control: 'keybind' },
  ],
  fov: [
    // Exactly vanilla's own slider range, which is the whole argument for this mod being
    // classed `safe` — see schema/mods/fov.json.
    { key: 'fov', label: 'Field of view', control: 'slider', min: 30, max: 110, step: 1, format: 'plain' },
    { key: 'lock_sprint', label: 'Lock while sprinting', control: 'switch' },
    { key: 'lock_bow', label: 'Lock while drawing a bow', control: 'switch' },
  ],
  toggle_sneak: [
    { key: 'mode', label: 'Mode', control: 'select', options: ['toggle', 'hold'] },
    { key: 'keybind', label: 'Keybind', control: 'keybind' },
  ],
  overlay: [
    { key: 'hide_fire', label: 'Hide fire overlay', control: 'switch' },
    { key: 'view_bobbing', label: 'View bobbing', control: 'select', options: ['vanilla', 'minimal', 'off'] },
    { key: 'hide_own_armor', label: 'Hide own armour', control: 'switch' },
    { key: 'hide_stuck_arrows', label: 'Hide stuck arrows', control: 'switch' },
    { key: 'hide_pumpkin', label: 'Hide pumpkin blur', control: 'switch' },
  ],
  // Roster §3.2's medium PvP set. Two of the three are deliberate bundles — `freelook` absorbs
  // Snaplook, `damage_tint` absorbs Hurt cam — argued in their schema `$comment`s.
  freelook: [
    { key: 'keybind', label: 'Keybind', control: 'keybind' },
    { key: 'mode', label: 'Mode', control: 'select', options: ['hold', 'toggle'] },
    { key: 'perspective', label: 'View', control: 'select', options: ['third_back', 'third_front', 'free'] },
    { key: 'snap_back', label: 'Snap back on release', control: 'switch' },
  ],
  hit_color: [
    { key: 'color', label: 'Colour', control: 'color' },
    { key: 'own_hits_only', label: 'Only hits you land', control: 'switch' },
    // 1 is exactly vanilla's own hurt-overlay alpha, never more — that ceiling is what keeps
    // this mod `safe` rather than `grey`. See schema/mods/hit_color.json.
    { key: 'intensity', label: 'Intensity', control: 'slider', min: 0, max: 1, step: 0.05, format: 'percent' },
  ],
  damage_tint: [
    { key: 'threshold', label: 'Show below', control: 'slider', min: 1, max: 20, step: 1, format: 'plain' },
    { key: 'strength', label: 'Strength', control: 'slider', min: 0, max: 1, step: 0.05, format: 'percent' },
    { key: 'camera_shake', label: 'Hurt camera', control: 'select', options: ['vanilla', 'reduced', 'off'] },
  ],
  // The 1.7 pair. `old_animations` is `safe` and is animation only; `old_input` is the
  // registry's fourth `grey` mod because each of its switches changes what the client *does* on
  // an input, and therefore what the server receives.
  old_animations: [
    { key: 'block_hit', label: 'Block hit', control: 'select', options: ['vanilla', 'one_seven'] },
    { key: 'swing_during_delay', label: 'Swing during delay', control: 'switch' },
  ],
  old_input: [
    { key: 'use_while_digging', label: 'Use item while mining', control: 'switch' },
    { key: 'dig_while_using', label: 'Mine while using an item', control: 'switch' },
    { key: 'no_miss_delay', label: 'No delay after a miss', control: 'switch' },
  ],
  hitboxes: [
    { key: 'line_width', label: 'Line width', control: 'slider', min: 0.5, max: 5, step: 0.5, format: 'plain' },
    { key: 'color', label: 'Colour', control: 'color' },
    { key: 'show_eye_line', label: 'Show eye line', control: 'switch' },
  ],
  zoom: [
    { key: 'key', label: 'Keybind', control: 'keybind' },
    { key: 'fov_divisor', label: 'Amount', control: 'slider', min: 1.1, max: 10, step: 0.1, format: 'multiplier' },
    { key: 'sensitivity', label: 'Zoom sensitivity', control: 'slider', min: 0.2, max: 1, step: 0.05, format: 'percent' },
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
  // Everything below trails in registry order, because the frame predates it. `direction` and
  // `watermark` landed after the Figma was drawn, then Wave 2's six readouts, then Wave 4's
  // five — thirteen mods that this array simply did not have, and nothing said so: it is
  // `readonly ModId[]`, so an incomplete list is not a type error the way `SETTING_SPECS`
  // above is, and the launcher's grid just drew fewer mods than the client ships. That is the
  // failure mode `docs/mod-roster.md` §9's audit classifies as *silent*, in the application it
  // already caught one in. The assertion under this array is the fix; the list is the symptom.
  'direction',
  'watermark',
  'combo',
  'hit_trade',
  'cps_graph',
  'clock',
  'scoreboard',
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
];

/**
 * A mod in the registry but missing from {@link MOD_GRID_ORDER} is simply not drawn — no error,
 * no gap, just a launcher showing fewer mods than the client has. The type cannot catch it (the
 * array is `ModId[]` whether or not it is complete), so this does, at import time, in every
 * build.
 *
 * Deliberately the same shape as `packages/ingame/src/mods/order.ts`'s assertion, because it is
 * the same bug in the other application — and this half is where it actually happened: the
 * overlay's order has thrown since it was written, while this one silently lost thirteen mods.
 */
const ungridded = MOD_IDS.filter((id) => !MOD_GRID_ORDER.includes(id));
if (ungridded.length > 0) {
  throw new Error(
    `local/registry.ts: MOD_GRID_ORDER is missing ${ungridded.join(', ')} — ` +
      'a mod absent from the grid order is never drawn.',
  );
}

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
