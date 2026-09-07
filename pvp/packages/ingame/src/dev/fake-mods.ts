/**
 * DEV ONLY — pads the page's *view* of the mod registry with synthetic mods, so the
 * Mods panel can be looked at with more tiles in it than the registry ships.
 *
 * ## Why this is here and not in the schema
 *
 * The real registry is generated: `pvp/schema/mods.json` → `@void/protocol`'s
 * `MOD_REGISTRY`, and the same ids cross into Java (`LiveState`, the per-mod settings
 * map, the loadout model, `bridge.json`). Adding a mod to see what the grid does at a
 * higher count would mean a schema change plus the matching Java, and would leave test
 * fixtures in a shipping artefact. Everything the layout needs is on *this* side of the
 * bridge — the grid derives its shape from `MOD_ORDER.length` (`ModsScreen`, `solveGrid`
 * / `gridRows`) — so the padding belongs here, off by default, and removable by deleting
 * this file, its four call sites (`registry.ts`, `store.ts`, `TilePreview.tsx`, `App.tsx`) and
 * the `define` in `vite.config.ts`.
 *
 * It is not merely inert in a release build, it is **absent**: see {@link ENABLED}.
 *
 * ## Switching it on
 *
 * ```sh
 * VOID_UI_FAKEMODS=24 pnpm --filter @void/ingame build   # in game: baked at build time
 * ```
 *
 * The number is the **total** the panel should show, not the number of fakes: 24 pads
 * the twelve real mods with twelve synthetic ones, 17 pads with five, and anything ≤ 12
 * is a no-op (this only ever adds; it never hides a real mod). The in-game page is a
 * built artefact loaded off the JAR classpath with no query string and no environment,
 * so the value is read at build time through a `define` in `vite.config.ts` — which is
 * no extra friction, because the page has to be rebuilt for the mod to see any change
 * anyway.
 *
 * In the browser harness (`pnpm dev`) there *is* a query string, so `?fake=24` does the
 * same thing without a rebuild and wins over the build-time value.
 *
 * ## What a fake mod is made of
 *
 * Enough to exercise the layout honestly, and nothing more:
 *
 *  - a **category** drawn from the real four, so `--hue` and the category tag render and
 *    the filter tabs have something to filter;
 *  - a **preview** that is one of the twelve real ones, cycled — `TilePreview` proxies a
 *    fake id to a real one rather than inventing art, because the point is what the grid
 *    looks like full, and new art would be a design decision smuggled in as a fixture;
 *  - a **settings shape** cycled through four sizes, so the properties panel is pushed
 *    through all three of §8's structures (1 → sentence, 2–4 → flat, 5+ → two groups)
 *    instead of twelve clones of one of them;
 *  - **enablement** that varies, so the disabled-tile treatment is visible in the grid.
 *
 * ## How the padding reaches the rest of the bundle
 *
 * By injection into the three tables the page reads a mod out of — `MOD_REGISTRY`,
 * `MOD_IDS` (both `@void/protocol`) and `MOD_ICONS` (`@void/ui`) — at import time. That
 * is deliberate: every consumer of a mod id in this bundle and in `@void/ui` goes
 * through one of those three, so a fake mod is a mod everywhere without a single
 * `if (fake)` in a component. Nothing is written back to either package's source.
 *
 * The one place that cannot work through is the **bridge**: Java's `setModSetting`
 * returns `null` for an id `ModRegistry.isMod` does not know (`LiveState.java`), and the
 * store binds to what Java returns, so a fake mod's toggle would appear stuck. `store.ts`
 * therefore writes fake mods' settings locally and never calls out. That is the whole
 * seam between this file and the real system.
 */

import { MOD_REGISTRY, MOD_IDS, type ModCategory, type ModId } from '@/bridge/protocol';
import { MOD_ICONS } from '@/ui';

/**
 * The build-time value of `VOID_UI_FAKEMODS`, substituted by `vite.config.ts`.
 *
 * Read through `typeof` so the module still loads under a config that does not define it.
 */
declare const __VOID_UI_FAKEMODS__: string | undefined;

/** That value, folded to a string the bundler can compare against a literal. */
const BUILD_TOTAL: string =
  typeof __VOID_UI_FAKEMODS__ === 'undefined' ? '' : __VOID_UI_FAKEMODS__;

/** Every fake id starts with this, so one is recognisable in the DOM and in a log. */
const FAKE_PREFIX = 'fake_';

/** The most tiles this will ever build, however large the request. */
const MAX_TOTAL = 64;

/* -------------------------------------------------------------------------- */
/* The fixtures                                                               */
/* -------------------------------------------------------------------------- */

/**
 * Names and categories, in the order they are used.
 *
 * Plausible on purpose: a grid full of `Fake 07` tells you nothing about how the type
 * sits in a tile or where the name ellipsises, which is half of what the higher counts
 * are being looked at for. They are still marked — every id is `fake_*`, every
 * description says so, and the page shows a `fake ×n` badge whenever any are present.
 */
const FIXTURES: ReadonlyArray<{ name: string; category: ModCategory }> = [
  { name: 'Reach display', category: 'pvp' },
  { name: 'Combo counter', category: 'pvp' },
  { name: 'Chat bubbles', category: 'visual' },
  { name: 'Item tracker', category: 'utility' },
  { name: 'Scoreboard', category: 'hud' },
  { name: 'Compass bar', category: 'hud' },
  { name: 'Bed defence', category: 'pvp' },
  { name: 'Block outline', category: 'visual' },
  { name: 'Health tags', category: 'pvp' },
  { name: 'Team glow', category: 'visual' },
  { name: 'Nick hider', category: 'utility' },
  { name: 'Autotext', category: 'utility' },
  { name: 'Arrow trail', category: 'visual' },
  { name: 'Motion blur', category: 'visual' },
  { name: 'Server clock', category: 'hud' },
  { name: 'Ping graph', category: 'hud' },
  { name: 'Sprint bar', category: 'pvp' },
  { name: 'Waypoints', category: 'utility' },
  { name: 'Damage tint', category: 'visual' },
  { name: 'Freelook', category: 'utility' },
];

/**
 * The four settings shapes, cycled. Between them they cover every structure §8 has and
 * every control kind the properties panel can draw.
 *
 * Counted the way `modProperties` counts: `on` is enablement rather than a property, and
 * `scale` is spatial and lives on the preview as a drag handle, so neither is in the
 * total. In order: **1** property (sentence, and the preview goes large), **3** (flat),
 * **4** (flat, at the top of its range), **7** (two groups — three appearance rows
 * against four behaviour rows, which is the split the two-column layout is for).
 */
const SHAPES: ReadonlyArray<Record<string, boolean | number | string>> = [
  { on: true, scale: 1, opacity: 0.9 },
  { on: true, scale: 1, opacity: 1, color: '#FFFFFF', show_label: true },
  { on: true, opacity: 1, decimals: 1, keybind: 'NONE', show_label: false },
  {
    on: true,
    scale: 1,
    opacity: 0.85,
    corner_radius: 6,
    color: '#7ADFFF',
    keybind: 'V',
    mode: 'toggle',
    show_label: true,
    show_cps: false,
  },
];

/** Which shapes carry a `mode` enum, and what it offers. Merged into `SETTING_ENUMS`. */
const MODE_OPTIONS = ['toggle', 'hold'] as const;

/** Three description lengths, so the list layout's description column has to truncate. */
const BLURBS = [
  'Not a real mod — a synthetic entry for looking at the panel at a higher count.',
  'Not a real mod. Synthetic fixture.',
  'Not a real mod — synthetic fixture, standing in for a mod this size of registry would have.',
];

/**
 * Real ids whose preview art a fake mod borrows, cycled.
 *
 * Deliberately the whole set rather than one repeated: the grid reading as real is
 * mostly the previews being different from each other, and that is exactly the property
 * a run at twenty-four tiles is being asked about.
 */
const ART_SOURCES: readonly ModId[] = [
  'fps',
  'keystrokes',
  'cps',
  'crosshair',
  'coordinates',
  'armor_status',
  'zoom',
  'potion_effects',
  'fullbright',
  'ping',
  'hitboxes',
  'toggle_sprint',
];

/** Keybinds handed out to the fakes that take one. LWJGL 2 names, as `mods.json` uses. */
const KEYS = ['V', 'B', 'N', 'G', 'H', 'J'];

/** Icons a fake mod borrows, cycled. Names from `@void/ui`'s own set. */
const ICON_SOURCES = ['gauge', 'sword', 'eye', 'star', 'layers', 'box', 'heart', 'bed'];

/* -------------------------------------------------------------------------- */
/* The plan                                                                   */
/* -------------------------------------------------------------------------- */

/** One synthetic registry row, before it is injected. */
export interface FakeMod {
  id: string;
  label: string;
  category: ModCategory;
  description: string;
  icon: string;
  /** The real mod whose tile preview this one borrows. */
  art: ModId;
  defaults: Record<string, boolean | number | string>;
  /** `SETTING_ENUMS` entries this mod needs, already keyed `<id>.<key>`. */
  enums: Record<string, readonly string[]>;
}

/**
 * The fakes needed to bring the registry up to `total` mods.
 *
 * Pure and exported so it can be unit-tested without the injection running; the
 * injection is the only thing with a side effect.
 */
export function planFakeMods(total: number, realCount: number): FakeMod[] {
  const wanted = Math.min(Math.floor(total), MAX_TOTAL) - realCount;
  if (!Number.isFinite(wanted) || wanted <= 0) return [];

  return Array.from({ length: wanted }, (_, i) => {
    const fixture = FIXTURES[i % FIXTURES.length]!;
    // Past the fixture list the name gains a run number rather than repeating, so two
    // tiles are never the same tile.
    const run = Math.floor(i / FIXTURES.length);
    const label = run === 0 ? fixture.name : `${fixture.name} ${run + 1}`;
    const id = `${FAKE_PREFIX}${label.toLowerCase().replace(/[^a-z0-9]+/g, '_')}`;
    const shape = SHAPES[i % SHAPES.length]!;
    const defaults: Record<string, boolean | number | string> = {
      ...shape,
      // Two on, one off, repeating: the disabled treatment (dimmed name, muted preview)
      // has to be visible in the grid without anyone clicking anything.
      on: i % 3 !== 2,
      // Half the mods that take a keybind have not been given one, which is a different
      // thing from having no keybind at all — the tile chips one and the list prints
      // `None` for the other.
      ...('keybind' in shape ? { keybind: i % 2 === 0 ? 'NONE' : KEYS[i % KEYS.length]! } : {}),
    };
    return {
      id,
      label,
      category: fixture.category,
      description: BLURBS[i % BLURBS.length]!,
      icon: ICON_SOURCES[i % ICON_SOURCES.length]!,
      art: ART_SOURCES[i % ART_SOURCES.length]!,
      defaults,
      enums: 'mode' in shape ? { [`${id}.mode`]: MODE_OPTIONS } : {},
    };
  });
}

/* -------------------------------------------------------------------------- */
/* Reading the switch                                                         */
/* -------------------------------------------------------------------------- */

/**
 * The requested total: `?fake=<n>` first, then the build-time `VOID_UI_FAKEMODS`.
 *
 * The query string wins because it is the one that can change without a rebuild, so in
 * the harness it is the more specific answer.
 */
export function requestedTotal(): number {
  const fromQuery =
    typeof location === 'undefined'
      ? null
      : new URLSearchParams(location.search).get('fake');
  const raw = fromQuery ?? BUILD_TOTAL;
  const value = Number.parseInt(String(raw), 10);
  return Number.isFinite(value) ? value : 0;
}

/**
 * Whether this build carries the fixtures **at all**.
 *
 * Both halves are constants the bundler substitutes, so a release build evaluates
 * `false || false` and Rollup drops this module's whole payload — the fixture table, the
 * planner, the injection. Checked: with the variable unset, no fixture name survives into
 * `index.html`; with it set, they do. Without this gate the strings shipped, inert but
 * present, which is not the same thing as "nothing in a release build".
 *
 * `import.meta.env.DEV` is what keeps `?fake=` working in the browser harness, where there is
 * no build step to bake a value into.
 */
const ENABLED: boolean = import.meta.env.DEV || BUILD_TOTAL !== '';

/* -------------------------------------------------------------------------- */
/* The injection                                                              */
/* -------------------------------------------------------------------------- */

const injected = ENABLED ? planFakeMods(requestedTotal(), MOD_IDS.length) : [];

/** Ids of the injected mods, in the order they were built. Empty when the switch is off. */
export const FAKE_MOD_ORDER: ModId[] = injected.map((mod) => mod.id as unknown as ModId);

/** How many fake mods are in play. The page badges this so no screenshot is ambiguous. */
export const FAKE_MOD_COUNT = injected.length;

const FAKE_IDS = new Set<string>(injected.map((mod) => mod.id));

/** `SETTING_ENUMS` entries the fakes need. Spread into the real table by `registry.ts`. */
export const FAKE_SETTING_ENUMS: Record<string, readonly string[]> = Object.assign(
  {},
  ...injected.map((mod) => mod.enums),
);

const ART_BY_ID = new Map<string, ModId>(injected.map((mod) => [mod.id, mod.art]));

/**
 * True when this id is one of ours.
 *
 * Cheap when the switch is off: the set is empty and the size test short-circuits before
 * the hash. That matters — it is on the path of every toggle and every settings write.
 */
export function isFakeMod(id: string): boolean {
  return FAKE_IDS.size > 0 && FAKE_IDS.has(id);
}

/**
 * The real mod whose tile preview a fake borrows, or null for a real id.
 * `TilePreview` renders itself again with this, so no fake art exists anywhere.
 */
export function fakeArtSource(id: string): ModId | null {
  return ART_BY_ID.get(id) ?? null;
}

if (injected.length > 0) {
  const registry = MOD_REGISTRY as unknown as Record<string, unknown>;
  const ids = MOD_IDS as unknown as string[];
  const icons = MOD_ICONS as unknown as Record<string, string>;

  for (const mod of injected) {
    registry[mod.id] = {
      id: mod.id,
      // `hud` rather than `gameplay` only because `kind` picks which bridge call a toggle
      // makes, and a fake never makes one — see the store's guard.
      kind: 'hud',
      category: mod.category,
      // `safe`, so padding the registry cannot flip a loadout's HYPIXEL-READY badge and
      // send someone looking for a regression that is a fixture.
      hypixel_safe: 'safe',
      label: mod.label,
      description: mod.description,
      source: 'dev fixture (VOID_UI_FAKEMODS)',
      defaults: mod.defaults,
    };
    ids.push(mod.id);
    icons[mod.id] = mod.icon;
  }

  // Loud on purpose. A build with fixtures in it must never be mistaken for a real one.
  console.warn(
    `[void] VOID_UI_FAKEMODS: ${injected.length} synthetic mods injected ` +
      `(${ids.length} total). DEV BUILD — not for release.`,
  );
}
