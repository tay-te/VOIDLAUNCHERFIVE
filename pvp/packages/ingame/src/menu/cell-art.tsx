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

/** The close X: a 7 × 7 bitmap. The frame draws 3px cells, 21 × 21 inside a 36px button. */
const CROSS = ['#.....#', '.#...#.', '..#.#..', '...#...', '..#.#..', '.#...#.', '#.....#'];

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
 * The close button's X.
 *
 * 2px cells, not the frame's 3. The buttons in the in-game bar are 24 square against the
 * board's 36 — the bar is 46 tall where the board's is 104 — and the glyphs had been left at
 * the board's size while the boxes around them were shrunk by hand, twice. A 21 x 21 X inside
 * a 24px button leaves 1.5px of air; the frame leaves 7.5. At two thirds it is 14 x 14 with
 * 5px, which is the frame's proportion.
 */
export function CloseGlyph(): React.ReactElement {
  return <CellArt rows={CROSS} size={2} />;
}

/** The magnifier: a 5 × 5 ring, and a handle running off its bottom-right corner. */
const LENS = ['.###..', '#...#.', '#...#.', '#...#.', '.###..', '....#.', '.....#'];

/**
 * The search button's magnifier.
 *
 * 2px cells, so it is 12 × 14 inside the same 24px button the close and the
 * inspector toggle use — the close's X is 14 × 14, and the two read as the same
 * weight because they are drawn out of the same atom at the same size.
 *
 * Cell art rather than an icon, for the reason at the top of this file: `@void/ui`
 * has a perfectly good `search` glyph and it is a stroked SVG path, which is the
 * class of thing ultralight-notes.md §7 lists as the usual casualty. The palette's
 * own query row does use that icon, and it is fine there — but the bar's other
 * three marks are cells, and one icon among them would be the odd one out
 * whether or not the engine drew it.
 */
export function SearchGlyph(): React.ReactElement {
  return <CellArt rows={LENS} size={2} />;
}

/** Grid view: a 2 × 2 block of cells. 2px for the same reason as {@link CloseGlyph}. */
export function GridGlyph(): React.ReactElement {
  return <CellArt rows={['##.##', '##.##', '.....', '##.##', '##.##']} size={2} />;
}

/** List view: three bars. The frame draws 20 × 4 rules, not cells. */
export function ListGlyph(): React.ReactElement {
  return (
    <span className="vglyph-bars" aria-hidden="true">
      <span />
      <span />
      <span />
    </span>
  );
}

/** Inspector toggle: the two-pane mark, a wide pane and a narrow one. */
export function InspectorGlyph(): React.ReactElement {
  return (
    <span className="vglyph-panes" aria-hidden="true">
      <span className="vglyph-panes__wide" />
      <span className="vglyph-panes__narrow" />
    </span>
  );
}
