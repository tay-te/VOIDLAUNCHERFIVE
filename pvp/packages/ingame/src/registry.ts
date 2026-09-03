/**
 * Overlay-side view of the mod registry.
 *
 * Identity, kind, safety class, description and factory defaults all come from
 * `@void/protocol`'s `MOD_REGISTRY`, which is generated from
 * `pvp/schema/mods.json` — that file is owned by **core** and is the single
 * source of truth (CONTRACTS.md). Nothing is redeclared here.
 *
 * What this module adds is presentation the schema does not carry:
 *
 *   · `category` — the Mods panel's filter taxonomy (All / HUD / PvP / Visual /
 *     Utility). `mods.json` classifies by `kind` (hud | gameplay), which is a
 *     data-direction split, not the product one the frame tabs across. Values
 *     read off frame 244:538 tile by tile.
 *   · `order` — the reading order of the 3 × 4 tile grid on that frame.
 *   · `label` — the frames read "FPS display", "CPS counter" and "Ping display"
 *     where `mods.json` says "FPS", "CPS", "Ping". Panel copy, so the frames
 *     win; flagged for reconciliation in schema/mods.json.
 *
 * Icons come from `@void/ui`'s `MOD_ICONS`, which is that package's resolution
 * of the same 12 ids.
 */

import { MOD_REGISTRY, type ModId } from '@/bridge/protocol';

export type ModCategory = 'HUD' | 'PVP' | 'VISUAL' | 'UTILITY';

/** Filter-tab taxonomy, per frame 244:538. */
export const MOD_CATEGORY: Record<ModId, ModCategory> = {
  fps: 'HUD',
  keystrokes: 'HUD',
  cps: 'HUD',
  toggle_sprint: 'PVP',
  crosshair: 'VISUAL',
  zoom: 'UTILITY',
  fullbright: 'VISUAL',
  hitboxes: 'PVP',
  armor_status: 'HUD',
  potion_effects: 'HUD',
  ping: 'HUD',
  coordinates: 'HUD',
};

/** Reading order of the tile grid on frame 244:538, left to right, top to bottom. */
export const MOD_ORDER: ModId[] = [
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

/** Panel copy where it differs from the registry's `label`. */
const LABEL_OVERRIDES: Partial<Record<ModId, string>> = {
  fps: 'FPS display',
  cps: 'CPS counter',
  ping: 'Ping display',
};

/** The name the Mods panel prints for a mod. */
export function modLabel(id: ModId): string {
  return LABEL_OVERRIDES[id] ?? MOD_REGISTRY[id].label;
}

/** Tab set of the Mods panel, verbatim from frame 244:538. */
export const FILTER_TABS = [
  { id: 'all', label: 'All' },
  { id: 'HUD', label: 'HUD' },
  { id: 'PVP', label: 'PvP' },
  { id: 'VISUAL', label: 'Visual' },
  { id: 'UTILITY', label: 'Utility' },
] as const;

/**
 * Numeric ranges for the settings controls, transcribed from the `<id>_settings`
 * sub-schemas of mods.json. The controls clamp to these; Java clamps again and
 * returns the value it stored, which is what the control binds to
 * (bridge.json, `setModSetting_returns`).
 */
export const SETTING_RANGES: Record<
  string,
  { min: number; max: number; step: number; unit?: string }
> = {
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
  /**
   * SCHEMA GAP: the Mod settings frame (244:834) draws a `Corner radius` slider
   * for Keystrokes, but `keystrokes_settings` in mods.json has no such key. It
   * is written through `setModSetting` like any other setting — Java clamps
   * rather than throws — and is flagged for reconciliation.
   */
  corner_radius: { min: 0, max: 20, step: 1, unit: 'px' },
};

/** Enum options per settings key, transcribed from mods.json. */
export const SETTING_ENUMS: Record<string, readonly string[]> = {
  'cps.mode': ['left', 'right', 'both'],
  'coordinates.layout': ['stacked', 'inline'],
  'armor_status.orientation': ['horizontal', 'vertical'],
  'toggle_sprint.mode': ['toggle', 'hold'],
  'crosshair.style': ['default', 'cross', 'dot', 'circle', 't_shape', 'none'],
};
