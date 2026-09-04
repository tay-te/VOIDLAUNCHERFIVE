/**
 * Overlay-side view of the mod registry.
 *
 * Identity, kind, **category**, safety class, label, description and factory defaults
 * all come from `@void/protocol`'s `MOD_REGISTRY`, which is generated from
 * `pvp/schema/mods.json` — that file is owned by **core** and is the single source of
 * truth (CONTRACTS.md). Nothing is redeclared here.
 *
 * This module used to carry three overrides, all now folded into the schema:
 *
 *   · `category` — the Mods panel's filter taxonomy. `mods.json` carries it per mod as
 *     `hud | pvp | visual | utility`; {@link MOD_CATEGORY} is only the uppercase tag
 *     the tile prints, derived from it.
 *   · `label` — the frames read "FPS display", "CPS counter", "Ping display"; the
 *     registry now says exactly that, so `modLabel` is a straight lookup.
 *
 * What is still local is the one thing the schema genuinely does not carry: the
 * **reading order of the tile grid** on frame 244:538, which is a layout decision, not
 * a property of a mod.
 *
 * Icons come from `@void/ui`'s `MOD_ICONS`, which is that package's resolution of the
 * same 12 ids.
 */

import {
  MOD_CATEGORIES,
  MOD_FILTER_TABS,
  MOD_IDS,
  MOD_REGISTRY,
  getCategoryLabel,
  getModCategory,
  type ModCategory as SchemaModCategory,
  type ModId,
} from '@/bridge/protocol';

/** The uppercase tag a tile prints, e.g. `HUD`. */
export type ModCategory = 'HUD' | 'PVP' | 'VISUAL' | 'UTILITY';

const TAGS: Record<SchemaModCategory, ModCategory> = {
  hud: 'HUD',
  pvp: 'PVP',
  visual: 'VISUAL',
  utility: 'UTILITY',
};

/**
 * Filter-tab taxonomy, read out of `mods.json` rather than transcribed from the frame.
 *
 * The values are the uppercase tags because that is what the tile and the mod-settings
 * subtitle print; the filter compares against the same table, so the two cannot drift.
 */
export const MOD_CATEGORY: Record<ModId, ModCategory> = Object.fromEntries(
  MOD_IDS.map((id) => [id, TAGS[getModCategory(id)]]),
) as Record<ModId, ModCategory>;

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

/** The name the Mods panel prints for a mod. Panel copy lives in `mods.json`. */
export function modLabel(id: ModId): string {
  return MOD_REGISTRY[id].label;
}

/**
 * Tab set of the Mods panel, frame 244:538. `all` is the no-filter tab; the rest are
 * the categories `mods.json` declares, tagged to match {@link MOD_CATEGORY}.
 */
export const FILTER_TABS: ReadonlyArray<{ id: string; label: string }> = MOD_FILTER_TABS.map(
  (tab) => ({
    id: tab.id === 'all' ? 'all' : TAGS[tab.id as SchemaModCategory],
    label: tab.label,
  }),
);

/** Every category tag, in tab order — the values {@link MOD_CATEGORY} can take. */
export const MOD_CATEGORY_TAGS: readonly ModCategory[] = MOD_CATEGORIES.map((c) => TAGS[c]);

/** The tab label for a tag, e.g. `PVP` → `PvP`. */
export function categoryLabel(tag: ModCategory): string {
  const category = MOD_CATEGORIES.find((c) => TAGS[c] === tag);
  return category ? getCategoryLabel(category) : tag;
}

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
  /** `keystrokes.corner_radius`, 0–20 px in `keystrokes_settings`. */
  corner_radius: { min: 0, max: 20, step: 1, unit: 'px' },
};

/** Enum options per settings key, transcribed from mods.json. */
export const SETTING_ENUMS: Record<string, readonly string[]> = {
  'cps.mode': ['left', 'right', 'both'],
  'coordinates.layout': ['stacked', 'inline'],
  'armor_status.orientation': ['horizontal', 'vertical'],
  'toggle_sprint.mode': ['toggle', 'hold'],
  'crosshair.style': ['default', 'cross', 'dot', 'circle', 't_shape', 'none'],
  // `keystrokes.key_color` and `keystrokes.pressed_color` are enums in mods.json too,
  // but deliberately absent here: this table drives the *generic* chip row, and the
  // Mod settings frame draws those two as colour swatches instead. Listing them would
  // render each one twice.
};
