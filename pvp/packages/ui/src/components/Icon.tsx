import type { SVGProps } from 'react';

import { MOD_ICON_NAMES, type ModId } from '@void/protocol';

/**
 * The shared icon set.
 *
 * ## Why this is a registry and not a folder of components
 *
 * `design/ultralight-notes.md` §7 marks inline SVG **[risky]** in the overlay:
 * Ultralight's SVG support is partial, and strokes, `stroke-linejoin` and non-scaling
 * strokes are the usual casualties. The recommendation is to ship the overlay icon set
 * as a PNG sprite sheet and keep the SVGs for the launcher.
 *
 * So `Icon` resolves its glyph through {@link setIconRenderer}: by default it draws the
 * bundled SVG paths, and the in-game bundle can swap in a sprite-sheet renderer
 * (`background-position` offsets on a `<span>`) without touching a single call site.
 * The paths below are deliberately conservative — plain `d` strings, uniform 1.6px
 * stroke, round caps, no `stroke-linejoin: miter` corners, no gradients, no masks.
 *
 * @example
 * ```tsx
 * <Icon name="sword" size={14} />
 * ```
 */

/** Every icon this package draws. */
export const ICON_NAMES = [
  'play',
  'settings',
  'chevron-down',
  'chevron-right',
  'arrow-left',
  'search',
  'close',
  'check',
  'plus',
  'move',
  'layers',
  'reset',
  'users',
  'star',
  'eye',
  'sword',
  'box',
  'bed',
  'heart',
  'gauge',
  'keyboard',
  'cursor-click',
  'crosshair',
  'zoom',
  'sun',
  'shield',
  'flask',
  'wifi',
  'compass',
  'bolt',
  'cube',
  'sparkle',
  'watermark',
  'clock',
] as const;

/** One icon name. */
export type IconName = (typeof ICON_NAMES)[number];

/**
 * Path data per icon, on a 24 × 24 grid. Values are `d` attributes for a single
 * `<path>` (or several, separated into an array) drawn with `stroke="currentColor"`.
 */
const PATHS: Record<IconName, string[]> = {
  play: ['M8 5.5 19 12 8 18.5Z'],
  settings: [
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
    'M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1Z',
  ],
  'chevron-down': ['M6 9.5 12 15.5 18 9.5'],
  'chevron-right': ['M9.5 6 15.5 12 9.5 18'],
  'arrow-left': ['M19 12H5', 'M11 18 5 12l6-6'],
  search: ['M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z', 'M21 21l-4.3-4.3'],
  close: ['M18 6 6 18', 'M6 6l12 12'],
  check: ['M20 6 9 17l-5-5'],
  plus: ['M12 5v14', 'M5 12h14'],
  move: ['M12 3v18', 'M3 12h18', 'M9 6l3-3 3 3', 'M9 18l3 3 3-3', 'M6 9l-3 3 3 3', 'M18 9l3 3-3 3'],
  layers: ['M12 3 3 8l9 5 9-5-9-5Z', 'M3 14l9 5 9-5'],
  reset: ['M3 12a9 9 0 1 0 3-6.7', 'M3 4v5h5'],
  users: [
    'M16 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2',
    'M9 10a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
    'M22 20v-2a4 4 0 0 0-3-3.9',
    'M16 2.1a4 4 0 0 1 0 7.8',
  ],
  star: ['M12 3.5l2.7 5.5 6 .9-4.3 4.2 1 6-5.4-2.8-5.4 2.8 1-6L3.3 9.9l6-.9L12 3.5Z'],
  eye: ['M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z'],
  sword: ['M14.5 17.5 3 6V3h3l11.5 11.5', 'M13 19l6-6', 'M16 16l4 4', 'M19 21l2-2'],
  box: ['M21 8 12 3 3 8v8l9 5 9-5V8Z', 'M3 8l9 5 9-5', 'M12 13v8'],
  bed: ['M2 8v11', 'M2 12h18a2 2 0 0 1 2 2v5', 'M2 17h20', 'M6.5 12V9.5a1 1 0 0 1 1-1H11a1 1 0 0 1 1 1V12'],
  heart: ['M20.8 7.6a4.9 4.9 0 0 0-8.8-2.2A4.9 4.9 0 0 0 3.2 7.6c0 5.1 8.8 11 8.8 11s8.8-5.9 8.8-11Z'],
  gauge: ['M12 20a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z', 'M12 12 15.5 8.5'],
  keyboard: [
    'M20 5H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2Z',
    'M6 9h.01',
    'M10 9h.01',
    'M14 9h.01',
    'M18 9h.01',
    'M7 13h10',
  ],
  'cursor-click': ['M9 4v3', 'M4 9h3', 'M6.5 5.5 8.5 7.5', 'M12 11l8 3-3.4 1.4L15 19l-3-8Z'],
  crosshair: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z', 'M12 2v4', 'M12 18v4', 'M2 12h4', 'M18 12h4'],
  zoom: ['M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z', 'M21 21l-4.3-4.3', 'M8 11h6', 'M11 8v6'],
  sun: [
    'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z',
    'M12 2v2',
    'M12 20v2',
    'M4.9 4.9l1.4 1.4',
    'M17.7 17.7l1.4 1.4',
    'M2 12h2',
    'M20 12h2',
    'M4.9 19.1l1.4-1.4',
    'M17.7 6.3l1.4-1.4',
  ],
  shield: ['M12 21s8-4 8-10V5.5L12 3 4 5.5V11c0 6 8 10 8 10Z'],
  flask: ['M9 3h6', 'M10 3v6L4.5 18A2 2 0 0 0 6.2 21h11.6a2 2 0 0 0 1.7-3L14 9V3', 'M7 15h10'],
  wifi: ['M5 12.5a10 10 0 0 1 14 0', 'M8.5 16a5 5 0 0 1 7 0', 'M12 19.5h.01', 'M1.5 9a15 15 0 0 1 21 0'],
  compass: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z', 'M15.5 8.5 13.5 13.5 8.5 15.5 10.5 10.5Z'],
  // Not Lucide's `footprints`, which this was. Two soles are four marks in 24 units and none
  // of them can clear its neighbour at the sizes this set is drawn: the in-game sprite
  // measured them closing into hollow rings with dots over them at 16px, which is why that
  // sheet had been drawing a double chevron under the name `footprints` for months. The name
  // and the drawing agree again — see `ingame/scripts/build-icons.py`, and
  // `schema/mods/toggle_sprint.json`, which is where the name is chosen now.
  bolt: ['M14.8 2.8 6.2 13.2h5.2L9.2 21.2 17.8 10.8h-5.2L14.8 2.8Z'],
  cube: ['M21 8 12 3 3 8v8l9 5 9-5V8Z', 'M3 8l9 5 9-5'],
  sparkle: ['M12 3l1.8 4.7L18.5 9.5 13.8 11.3 12 16l-1.8-4.7L5.5 9.5 10.2 7.7 12 3Z', 'M18.5 16l.9 2.2 2.1.8-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.8.9-2.2Z'],
  // Not a Lucide glyph. A mark placed in the corner of a screen, which is what the watermark
  // mod is. The mark itself is cell art rather than an icon — the tile preview draws the real
  // widget (`ingame/src/menu/TilePreview.tsx`) — but the mod still needs a glyph for the
  // *rows*, where every sibling has one and a hole reads as a missing asset. That glyph was
  // `sparkle` until the icon pass, and it was wrong twice: `sparkle` means "AI / magic /
  // enhance" everywhere else in this product — it is the palette's default and the Cosmetics
  // mark — and it is the one **filled** glyph among twelve stroked mod icons, so the quietest
  // thing the client draws carried the heaviest mark in the list column.
  // `ingame/scripts/build-icons.py`, `watermark`, draws the same reasoning at 48px; the name
  // is chosen in `schema/mods/watermark.json` now.
  watermark: ['M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z', 'M7.4 15a2.1 2.1 0 1 0 0-4.2 2.1 2.1 0 0 0 0 4.2Z'],
  // A **stopwatch**, not a wall clock, and the difference is the whole reason this glyph is
  // three marks rather than two. A ring with two hands in it is a wall clock; at 16px the two
  // hands fall inside one 2px band and it is a ring with a smudge in it, which is a worse
  // `crosshair`. A stopwatch is a ring with a *crown on top of it* and one sweep hand, and the
  // crown is the mark that carries the meaning at every size — it survives to 13px as a nub on
  // the case where a second hand does not survive to 16.
  //
  // So: case, crown, hand. Three ideas, and the sweep hand sits at 1 o'clock rather than at 12
  // so it does not read as the crown continued through the case.
  //
  // The case is r 7.1 rather than the 8-9 the other rings here take, because the crown has to
  // fit above it inside the same box: case bottom to crown tip is 17.2 units, which is the
  // height a full-size ring would have had on its own. The form comes out taller than it is
  // wide, which is what a stopwatch is.
  //
  // The coordinates are shared with `ingame/scripts/build-icons.py`'s `clock` cell on purpose,
  // so the launcher's SVG and the overlay's sprite are provably the same drawing at two stroke
  // weights rather than two drawings that happen to share a name — the failure `toggle_sprint`
  // shipped for months.
  clock: [
    'M12 20.5a7.1 7.1 0 1 0 0-14.2 7.1 7.1 0 0 0 0 14.2Z',
    'M12 6.3V3.3',
    'M12 13.4 14.7 8.7',
  ],
};

/** Icons drawn as a solid shape rather than a stroke. */
const FILLED = new Set<IconName>(['play', 'heart', 'star', 'shield', 'sparkle', 'watermark']);

/** Props for {@link Icon}. */
export interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name' | 'width' | 'height'> {
  /** Which glyph to draw. */
  name: IconName;
  /** Edge length in pixels. The design uses 13, 14, 16 and 22. */
  size?: number;
  /** Stroke width, in the icon's own 24-unit space. */
  strokeWidth?: number;
}

/** A renderer that can stand in for the bundled SVGs — e.g. a PNG sprite sheet. */
export type IconRenderer = (props: IconProps) => React.ReactElement | null;

let renderer: IconRenderer | null = null;

/**
 * Replace how every {@link Icon} draws itself, process-wide.
 *
 * The in-game bundle should call this once at boot with a sprite-sheet renderer if
 * Ultralight's partial SVG support turns out to be a problem on the target build —
 * see §7 of `design/ultralight-notes.md`. Pass `null` to go back to the SVGs.
 */
export function setIconRenderer(next: IconRenderer | null): void {
  renderer = next;
}

/** The current icon renderer, or null when the bundled SVGs are in use. */
export function getIconRenderer(): IconRenderer | null {
  return renderer;
}

/** A single icon, sized in pixels and inheriting `currentColor`. */
export function Icon(props: IconProps): React.ReactElement | null {
  if (renderer) return renderer(props);
  const { name, size = 16, strokeWidth = 1.6, ...rest } = props;
  const filled = FILLED.has(name);
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled ? 'currentColor' : 'none'}
      stroke={filled ? 'none' : 'currentColor'}
      strokeWidth={filled ? undefined : strokeWidth}
      strokeLinecap={filled ? undefined : 'round'}
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {PATHS[name].map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}

/**
 * The icon each of the 13 mods shows in its row, its palette result and its page head.
 *
 * **Derived from the schema, not written here.** `mod_entry.icon` in `pvp/schema/mods/<id>.json`
 * is where a mod names its glyph, `@void/protocol` generates {@link MOD_ICON_NAMES} out of the
 * shipped registry, and this is that table with one thing added: the promise that every value
 * is an {@link IconName} this package can actually draw. A mod added to the schema arrives here
 * on its own — this file used to need a row per mod, and forgetting one was a mod whose list
 * row and palette result had a hole where every sibling has a mark.
 *
 * ## Why the `satisfies` is the point
 *
 * `IconName` is a closed union and {@link Icon} indexes `PATHS` with it, so a name with no
 * drawing is `undefined.map` — a throw in the launcher, and in the overlay a sprite cell that
 * does not exist. The schema constrains `icon` to a *pattern*, which cannot know what this
 * package draws, so the check has to happen where both facts are in scope. That is here, and
 * it is one line: `MOD_ICON_NAMES` carries each value at its literal type, so
 * `satisfies Record<ModId, IconName>` proves, at `pnpm typecheck`, both that every mod has an
 * icon and that every icon has a drawing. `design/rendering-invariants.md` §15 asks for the
 * check that walks the domain rather than the case somebody thought of; a total type is that
 * check with no test to run.
 *
 * ## Why it is a copy
 *
 * The spread is not incidental. `packages/ingame/src/dev/fake-mods.ts` pads the page's view of
 * the registry under `?fake=`/`VOID_UI_FAKEMODS` by writing rows into this object at import
 * time, so it has to stay an ordinary extensible object — never frozen, and never a getter over
 * the generated const. Copying also keeps that padding out of `@void/protocol`'s constant,
 * which is the contract and should read the same in a dev build as in a release one.
 */
export const MOD_ICONS = { ...MOD_ICON_NAMES } satisfies Record<ModId, IconName>;

/**
 * Resolve a loadout's `icon` field against the shared icon set. `loadout.json` says the
 * value is "resolved by the UI against the shared icon set in packages/ui; not a file
 * path" — this is that resolution. Unknown names fall back to `box`.
 */
export function resolveLoadoutIcon(icon: string): IconName {
  return (ICON_NAMES as readonly string[]).includes(icon) ? (icon as IconName) : 'box';
}
