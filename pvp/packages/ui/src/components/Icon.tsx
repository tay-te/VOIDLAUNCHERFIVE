import type { SVGProps } from 'react';

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
  'footprints',
  'cube',
  'sparkle',
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
  footprints: [
    'M5 16c0-2 .8-3 .8-4.5C5.8 9.6 5 9 5 7a2 2 0 0 1 4 0c0 2-.8 2.6-.8 4.5C8.2 13 9 14 9 16a2 2 0 0 1-4 0Z',
    'M15 20c0-2 .8-3 .8-4.5 0-1.9-.8-2.5-.8-4.5a2 2 0 0 1 4 0c0 2-.8 2.6-.8 4.5 0 1.5.8 2.5.8 4.5a2 2 0 0 1-4 0Z',
  ],
  cube: ['M21 8 12 3 3 8v8l9 5 9-5V8Z', 'M3 8l9 5 9-5'],
  sparkle: ['M12 3l1.8 4.7L18.5 9.5 13.8 11.3 12 16l-1.8-4.7L5.5 9.5 10.2 7.7 12 3Z', 'M18.5 16l.9 2.2 2.1.8-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.8.9-2.2Z'],
};

/** Icons drawn as a solid shape rather than a stroke. */
const FILLED = new Set<IconName>(['play', 'heart', 'star', 'shield', 'sparkle']);

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

/** The icon each of the 12 mods shows in its tile and its settings pane. */
export const MOD_ICONS = {
  fps: 'gauge',
  keystrokes: 'keyboard',
  cps: 'cursor-click',
  ping: 'wifi',
  coordinates: 'compass',
  armor_status: 'shield',
  potion_effects: 'flask',
  toggle_sprint: 'footprints',
  fullbright: 'sun',
  hitboxes: 'cube',
  zoom: 'zoom',
  crosshair: 'crosshair',
} as const satisfies Record<string, IconName>;

/**
 * Resolve a loadout's `icon` field against the shared icon set. `loadout.json` says the
 * value is "resolved by the UI against the shared icon set in packages/ui; not a file
 * path" — this is that resolution. Unknown names fall back to `box`.
 */
export function resolveLoadoutIcon(icon: string): IconName {
  return (ICON_NAMES as readonly string[]).includes(icon) ? (icon as IconName) : 'box';
}
