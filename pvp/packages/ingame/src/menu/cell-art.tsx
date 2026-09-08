/**
 * Cell art — the one drawing primitive the overlay chrome and the tile previews
 * share.
 *
 * Every mark in the in-game frames (`289:1611`, `289:3024`, `289:2445` on the
 * "Source of truth — In-game Client" page) is built from the §3 cell: a small
 * square with a 30% radius. The VOID ring is sixteen 4px cells, the close button
 * is an X of 3px cells, the crosshair preview is a plus of 9px cells. Nothing is
 * an icon font and nothing is an SVG — ultralight-notes.md §7 lists inline SVG as
 * [risky] (partial support, strokes are the usual casualty), and a bitmap of divs
 * is the one drawing method that cannot fail.
 *
 * A glyph is written as a list of equal-length strings, `#` for a cell and any
 * other character for a gap, which keeps the shape readable in the source:
 *
 * ```tsx
 * <CellArt size={9} rows={['..#..', '..#..', '##.##', '..#..', '..#..']} />
 * ```
 *
 * Rows are flex rows of fixed-size squares, so there is no grid and no absolute
 * positioning to keep in sync. Cells are monochrome by contract: `--hue` marks a
 * live value or a selection, never preview art (§1, the accent rule).
 */

import { cx } from '@/ui';

/** Props for {@link CellArt}. */
export interface CellArtProps {
  /** One string per row; `#` draws a cell, anything else leaves a gap. */
  rows: readonly string[];
  /** Cell edge in px. The step is the same, so cells touch and the radius separates them. */
  size?: number;
  /** `on` is the .40 fill, `off` the .08 one, `dim` a quieter .16. */
  tone?: 'on' | 'off' | 'dim';
  className?: string;
}

/**
 * @example
 * ```tsx
 * <CellArt rows={['#####', '#...#', '#####']} size={9} />
 * ```
 */
export function CellArt({ rows, size = 9, tone = 'on', className }: CellArtProps): React.ReactElement {
  return (
    <span className={cx('cart', `cart--${tone}`, className)} aria-hidden="true">
      {rows.map((row, y) => (
        <span className="cart__row" key={y} style={{ height: `${size}px` }}>
          {[...row].map((ch, x) => (
            <span
              key={x}
              className={cx('cart__cell', ch === '#' && 'cart__cell--on')}
              style={{ width: `${size}px`, height: `${size}px` }}
            />
          ))}
        </span>
      ))}
    </span>
  );
}

/* -------------------------------------------------------------------------- */
/* Chrome glyphs — the marks the top bar is made of                           */
/* -------------------------------------------------------------------------- */

/** The VOID ring: a 7 × 7 bitmap of 4px cells, 28 × 28 in the frame. */
const RING = ['..###..', '.#...#.', '#.....#', '#.....#', '#.....#', '.#...#.', '..###..'];

/** The VOID mark, top left of every in-game frame. */
export function VoidMark(): React.ReactElement {
  return (
    <span className="vmark">
      <CellArt rows={RING} size={4} className="vmark__ring" />
      <span className="vmark__word">VOID</span>
    </span>
  );
}


/**
 * The bar's icons, drawn from one PNG sprite.
 *
 * **Cell art lost this argument.** The VOID mark is still a bitmap of §3 cells and always will
 * be — it is a wordmark built out of the design's own atom, not an icon — but the four controls
 * beside it were cells too, and at 14px a magnifier made of 2px squares reads as a smudge
 * rather than as a magnifier. The user asked for "an actual search icon from lucide", and the
 * shapes here are Lucide's own geometry.
 *
 * They are **pixels, not SVG**, and that is not a compromise made to save effort:
 * `design/ultralight-notes.md` rates inline `<svg>` [risky] — "strokes, `stroke-linejoin` and
 * non-scaling strokes are the usual casualties" — and every Lucide icon is a stroke. The same
 * line prescribes exactly this: a sprite sheet at the density in use, with `background-position`
 * offsets. `scripts/build-icons.py` bakes it, and the geometry lives there in Lucide's own 24×24
 * units so the sprite can be regenerated rather than being a binary nobody can edit.
 *
 * One `<span>` per icon, positioned by class; the size and the offsets are `overlay.css`'s.
 */
function Icon({
  name,
}: {
  name: 'search' | 'close' | 'grid' | 'list' | 'back' | 'settings' | 'move';
}) {
  return <span className={`oicon oicon--${name}`} aria-hidden="true" />;
}

/**
 * The four-way arrow — lucide `move`, and the HUD layout editor's mark everywhere it appears:
 * the bar's way in, the editor toolbar's own mode label, and the quick palette's row. One glyph
 * for one destination, so the three read as the same place rather than three features.
 */
export function MoveGlyph(): React.ReactElement {
  return <Icon name="move" />;
}

/**
 * The bar's gear — lucide `settings`, simplified to eight teeth (see the script for why the
 * twelve-tooth path is not transcribed).
 *
 * It replaced the profile chip, which is a trade rather than an addition: the chip was the
 * widest object in the bar and what it spent that width on was identity — which in game is
 * settled, because you cannot switch accounts mid-match. A gear opens something.
 */
export function SettingsGlyph(): React.ReactElement {
  return <Icon name="settings" />;
}

/** The search button's magnifier — lucide `search`. */
export function SearchGlyph(): React.ReactElement {
  return <Icon name="search" />;
}

/** Grid view — lucide `layout-grid`. */
export function GridGlyph(): React.ReactElement {
  return <Icon name="grid" />;
}

/** List view — lucide `menu`. */
export function ListGlyph(): React.ReactElement {
  return <Icon name="list" />;
}

/** The close button's X — lucide `x`. */
export function CloseGlyph(): React.ReactElement {
  return <Icon name="close" />;
}

/**
 * The back mark on the mod page's bar — lucide `chevron-left`.
 *
 * There is no glyph for "properties" any more: the two-pane mark went with the inspector toggle
 * it was drawn for, because a page has nothing to toggle.
 */
export function BackGlyph(): React.ReactElement {
  return <Icon name="back" />;
}
