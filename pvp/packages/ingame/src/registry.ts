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
 * same 13 ids.
 */

import type { CSSProperties } from 'react';

import {
  SETTING_BOUNDS,
  SETTING_OPTIONS,
  MOD_CATEGORIES,
  MOD_FILTER_TABS,
  MOD_IDS,
  MOD_REGISTRY,
  getCategoryLabel,
  getModCategory,
  type ModCategory as SchemaModCategory,
  type ModId,
} from '@/bridge/protocol';
// DEV ONLY, and imported first on purpose: the module injects its rows into `MOD_REGISTRY`
// and `MOD_IDS` as a side effect of being loaded, and `MOD_CATEGORY` below is built from
// `MOD_IDS`. Empty and inert unless `VOID_UI_FAKEMODS` / `?fake=` asked for tiles.
import { FAKE_SETTING_ENUMS } from '@/dev/fake-mods';

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

/**
 * Reading order of the tile grid — re-exported, not defined.
 *
 * It lives in `mods/order.ts` beside the art it orders, so that "add a mod" is one directory
 * rather than a hunt: `mods/<id>.tsx` for the pictures, `mods/order.ts` for where it reads.
 * That module also asserts the array is complete over the registry, which this file never did.
 */
export { MOD_ORDER } from '@/mods/order';

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
 * The **presentation** half of a numeric setting: its step, and the unit it prints in.
 *
 * The bounds are not here. They are `SETTING_BOUNDS` in `@void/protocol`, generated from each
 * `<id>_settings` sub-schema, because a bound is a fact about what Java will clamp to and this
 * table used to copy all fourteen of them by hand under a comment that said "transcribed from
 * the `<id>_settings` sub-schemas of mods.json". A copy of a contract drifts from it, and the
 * drift is silent — a slider clamping to the wrong end looks exactly like a slider.
 *
 * What genuinely belongs here is what the schema has no opinion about: how far one nudge moves,
 * and what glyph the figure is printed with. `warn_below` is read as a percentage and 5% is what
 * a player thinks in; 0.01 would give the meter 100 cells for a decision with about five useful
 * answers. That is a UI judgement, and it is the only kind of thing left in this table.
 */
const SETTING_STEPS: Record<string, { step: number; unit?: string }> = {
  scale: { step: 0.05, unit: '×' },
  opacity: { step: 0.01, unit: '%' },
  warn_below: { step: 0.05, unit: '%' },
  window_ms: { step: 50, unit: 'ms' },
  reset_ms: { step: 100, unit: 'ms' },
  fov: { step: 1, unit: '°' },
  good_ms: { step: 5, unit: 'ms' },
  bad_ms: { step: 5, unit: 'ms' },
  decimals: { step: 1 },
  gamma: { step: 0.5 },
  line_width: { step: 0.5 },
  fov_divisor: { step: 0.1, unit: '×' },
  size: { step: 1, unit: 'px' },
  thickness: { step: 1, unit: 'px' },
  gap: { step: 1, unit: 'px' },
  corner_radius: { step: 1, unit: 'px' },
};

/**
 * Numeric ranges for the settings controls — bounds from the schema, step and unit from above.
 *
 * The controls clamp to these; Java clamps again and returns the value it stored, which is what
 * the control binds to (`bridge.json`, `setModSetting_returns`).
 *
 * A property with a bound and no step entry gets a step of 1 rather than being absent, because
 * absent means *no control* — `kindOf` falls through to the enum branch and draws a chip row
 * with nothing in it. A new numeric setting should therefore render as a coarse slider until
 * someone chooses its step, not as an empty row.
 */
export const SETTING_RANGES: Record<
  string,
  { min: number; max: number; step: number; unit?: string }
> = Object.fromEntries(
  Object.entries(SETTING_BOUNDS).map(([key, bounds]) => {
    const shown: { step: number; unit?: string } | undefined = SETTING_STEPS[key];
    return [key, { ...bounds, step: shown?.step ?? 1, ...(shown?.unit ? { unit: shown.unit } : {}) }];
  }),
);

/**
 * The two enums the Mod settings frame draws as colour **swatches** rather than as the generic
 * chip row. Listing them in `SETTING_ENUMS` would render each one twice.
 */
const SWATCH_ENUMS = new Set(['keystrokes.key_color', 'keystrokes.pressed_color']);

/**
 * Enum options per `<mod>.<key>` — from the schema, with two deliberate removals.
 *
 * Missing an entry is not a missing *label*, it is a missing **control**: `PropertyControl`'s
 * default branch draws `PositionChips` over `SETTING_ENUMS[id.key] ?? []`, and an empty array
 * renders a row with a label and nothing beside it. That shipped on `watermark.style`, and it
 * is only visible in game — jsdom renders the empty chip row exactly as happily as a full one.
 * Deriving the table from `mods.json` is what makes it unrepresentable rather than tested for.
 */
export const SETTING_ENUMS: Record<string, readonly string[]> = {
  // Empty unless the dev padding is on; spread first so a real key always wins.
  ...FAKE_SETTING_ENUMS,
  ...Object.fromEntries(
    Object.entries(SETTING_OPTIONS).filter(([key]) => !SWATCH_ENUMS.has(key)),
  ),
};


/* -------------------------------------------------------------------------- */
/* Category hues                                                              */
/* -------------------------------------------------------------------------- */

/**
 * The hue a category paints with (quiet-cell contract §1).
 *
 * A mod's live value, selection dot, drag handles and current meter cell take **its
 * own** category hue, never the global accent — so a Visual mod's readout is ice and
 * a PvP mod's is coral. The mechanism is one custom property set on the mod's root
 * element; every accent-consuming rule underneath reads `var(--hue, var(--accent))`,
 * so nothing in a mod-scoped component ever names `--accent` directly and the fallback
 * still covers a subtree that has no mod.
 */
export const CATEGORY_HUE: Record<ModCategory, string> = {
  HUD: 'var(--hue-hud)',
  PVP: 'var(--hue-pvp)',
  VISUAL: 'var(--hue-visual)',
  UTILITY: 'var(--hue-utility)',
};

/**
 * The inline style that scopes a subtree to a mod's hue.
 *
 * Inline rather than a class per category because the value is a token reference,
 * not a rule: one declaration on the root beats four `.mod--visual` variants of every
 * accent rule underneath, and it is the only place the category is read at all.
 */
export function hueStyle(id: ModId): CSSProperties {
  return { ['--hue' as string]: CATEGORY_HUE[MOD_CATEGORY[id]] } as CSSProperties;
}
