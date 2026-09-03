/**
 * The six glyphs the launcher needs and `@void/ui`'s icon set does not have.
 *
 * This is **not** a second icon set. Everything the design's component inventory names
 * comes from `Icon` in `@void/ui` — including all twelve `MOD_ICONS` and every glyph
 * in the dock, the panels and the palette. What is left over is launcher-only by
 * construction:
 *
 * - **minimise / maximise / close-window** — a frameless Tauri window draws its own
 *   window controls. The in-game overlay has no window.
 * - **terminal** — the JVM log drawer. There is no JVM log inside the JVM.
 * - **trash** — removing a server from the launcher's favourites list.
 * - **cosmetics / servers** — the two launcher nav marks. `@void/ui` carries the nav
 *   items the overlay shares (`play`, `layers`, `users`); the overlay has no
 *   Cosmetics or Servers screen, so those two marks never reached the package.
 *
 * They follow the same drawing contract as `@void/ui`'s set — one 24 × 24 grid,
 * uniform 1.6px stroke, round caps, no gradients or masks — so they are swappable for
 * a sprite sheet the same way (`setIconRenderer`) if the overlay ever needs them.
 */

import type { SVGProps } from 'react';

/** Props for {@link Glyph}, mirroring `@void/ui`'s `IconProps`. */
export interface GlyphProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
  /** Edge length in pixels. */
  size?: number;
  /** Stroke width in the icon's own 24-unit space. */
  strokeWidth?: number;
}

/** Every launcher-only glyph. */
export const GLYPH_NAMES = [
  'terminal',
  'trash',
  'minimise',
  'maximise',
  'window-close',
  'cosmetics',
  'servers',
] as const;

/** One launcher-only glyph name. */
export type GlyphName = (typeof GLYPH_NAMES)[number];

const PATHS: Record<GlyphName, string[]> = {
  terminal: ['M5 7l5 5-5 5', 'M13 17h6'],
  trash: ['M4 7h16', 'M9 7V5h6v2', 'M6 7l1 13h10l1-13', 'M10 11v6', 'M14 11v6'],
  minimise: ['M5 12h14'],
  maximise: ['M6 5h12a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z'],
  'window-close': ['M18 6 6 18', 'M6 6l12 12'],
  // The Figma nav marks: a circle bisected by a rule, and a two-column dot cluster.
  cosmetics: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z', 'M4 12h16'],
  servers: [
    'M5.5 9.2h.01',
    'M5.5 15.2h.01',
    'M12.5 6.2h.01',
    'M12.5 12h.01',
    'M12.5 17.8h.01',
  ],
};

/** Which glyphs draw as dots rather than strokes — round caps at a wider width. */
const DOTTED = new Set<GlyphName>(['servers']);

function make(name: GlyphName) {
  return function Glyph({ size = 16, strokeWidth, ...rest }: GlyphProps): React.ReactElement {
    return (
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={strokeWidth ?? (DOTTED.has(name) ? 3.2 : 1.6)}
        strokeLinecap="round"
        aria-hidden="true"
        focusable="false"
        {...rest}
      >
        {PATHS[name].map((d) => (
          <path key={d} d={d} />
        ))}
      </svg>
    );
  };
}

export const TerminalGlyph = make('terminal');
export const TrashGlyph = make('trash');
export const MinimiseGlyph = make('minimise');
export const MaximiseGlyph = make('maximise');
export const WindowCloseGlyph = make('window-close');
export const CosmeticsGlyph = make('cosmetics');
export const ServersGlyph = make('servers');
