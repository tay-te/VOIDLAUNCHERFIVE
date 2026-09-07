/**
 * The mods grid's arithmetic, at every count it can be asked for.
 *
 * This file exists because of a bug that survived a night of testing: between **seventeen and
 * twenty-one mods** the grid laid out for a panel taller than the window, the engine clamped
 * the panel, and the bottom row and the last column were cut off with no way to scroll them
 * back. Twelve was fine and twenty-four was fine — the registry ships twelve and the fixture
 * everyone ran was twenty-four — so nothing anybody looked at was wrong.
 *
 * Measured in Chrome at the in-game view size (1458 x 820 CSS), before the fix:
 *
 * ```
 *  count  cols x rows   tile         panel h        clipped
 *  12     6 x 2         195   x 247  635            —
 *  13-14  7 x 2         165.4 x 217  575.9          —
 *  15-16  8 x 2         143.3 x 195  531.5          —
 *  17-18  6 x 3         195   x 247  894  -> 796    97px of row 3, 11px of column 6
 *  19-21  7 x 3         165.4 x 217  805  -> 796     8px of row 3, 11px of column 7
 *  22-24  8 x 3         143.3 x 195  738.8          —
 *  25-26  7 x 4         165.4 x 217  1023 -> 796   238px of row 4, 11px of column 7
 * ```
 *
 * The column count running 6, 7, 8, **6**, 7, 8 is the whole defect: seventeen mods got *wider*
 * tiles than sixteen, and three rows of them did not fit. So the properties this file asserts
 * are the ones that make that shape impossible — the panel fits the window, the grid fills the
 * panel, and neither the tile nor the column count ever moves backwards as the count climbs —
 * checked across the fixture's entire range rather than at the two counts that happened to work.
 */

import { describe, expect, it } from 'vitest';

import { GEOMETRY, OPEN_COLUMNS, solveGrid, gridRows, visibleWhenContracted } from '@/menu/ModsScreen';
import type { ModId } from '@/bridge/protocol';

/** The view the in-game overlay gets on a 16:9 client, measured. See `test/setup.ts`. */
const VIEW = { w: 1458, h: 820 };

/** The narrow-aspect canvas: `VoidClient` never gives the view less than 1300 x 820 CSS. */
const NARROW = { w: 1300, h: 820 };

/** Every count the registry and the dev fixture between them can produce. */
const ALL_COUNTS = Array.from({ length: 53 }, (_, i) => i + 12); // 12 … 64

const ids = (n: number): ModId[] =>
  Array.from({ length: n }, (_, i) => `m${i}` as unknown as ModId);

/** What the panel is allowed to be, from the CSS the solve is mirroring. */
const maxPanelH = (viewH: number) => viewH - GEOMETRY.insetY;
const maxPanelW = (viewW: number) => Math.min(GEOMETRY.maxPanelW, viewW - GEOMETRY.insetX);

describe('solveGrid — the panel always fits the window', () => {
  it.each(ALL_COUNTS)('at %i mods, in the in-game view', (count) => {
    const shape = solveGrid(count, VIEW.w, VIEW.h);
    expect(shape.panelH).toBeLessThanOrEqual(maxPanelH(VIEW.h));
    expect(shape.panelW).toBeLessThanOrEqual(maxPanelW(VIEW.w));
  });

  it.each(ALL_COUNTS)('at %i mods, on the narrow-aspect canvas', (count) => {
    const shape = solveGrid(count, NARROW.w, NARROW.h);
    // 1300 - 48 is 1252, narrower than the 1278 the panel wants. Before the fix the tile was
    // still sized for 1278 and 26px of the last column ran off the panel's own clip.
    expect(shape.panelW).toBe(1252);
    expect(shape.panelH).toBeLessThanOrEqual(maxPanelH(NARROW.h));
    expect(shape.gridW).toBeLessThanOrEqual(shape.panelW - 2 * GEOMETRY.edge);
  });
});

describe('solveGrid — the grid fills the panel it is given', () => {
  it.each(ALL_COUNTS)('at %i mods the columns and their gaps span the content width', (count) => {
    const shape = solveGrid(count, VIEW.w, VIEW.h);
    const content = shape.panelW - 2 * GEOMETRY.edge;
    const spanned =
      shape.columns * shape.tileW + (shape.columns - 1) * GEOMETRY.gap + shape.gutter;
    expect(spanned).toBeCloseTo(content, 6);
    expect(shape.gridW).toBeCloseTo(content - shape.gutter, 6);
  });

  it.each(ALL_COUNTS)('at %i mods the rows hold every mod and none is empty', (count) => {
    const shape = solveGrid(count, VIEW.w, VIEW.h);
    expect(shape.columns * shape.rows).toBeGreaterThanOrEqual(count);
    // One fewer row would not hold them: no row is there for nothing.
    expect(shape.columns * (shape.rows - 1)).toBeLessThan(count);
    expect(shape.columns).toBeLessThanOrEqual(GEOMETRY.maxColumns);
    expect(shape.tileH).toBeCloseTo(shape.tileW + GEOMETRY.foot, 6);
  });

  it.each(ALL_COUNTS)('at %i mods the panel is exactly the rows plus the chrome', (count) => {
    const shape = solveGrid(count, VIEW.w, VIEW.h);
    if (shape.scrolls) {
      // The one state where it is not: nothing fits, so the panel takes the whole window and
      // the rows scroll inside it.
      expect(shape.panelH).toBe(maxPanelH(VIEW.h));
      expect(shape.gridH).toBeGreaterThan(shape.panelH - GEOMETRY.chrome);
    } else {
      expect(shape.panelH).toBeCloseTo(shape.gridH + GEOMETRY.chrome, 6);
    }
  });
});

describe('solveGrid — the counts that used to break', () => {
  /**
   * The three tiles the 1278-wide panel can produce, to the fraction. Written out because the
   * table below is only readable if the numbers in it have names.
   */
  const TILE = { six: 195, seven: 1158 / 7, eight: 143.25 };

  /** Rows of `tileH`, plus the 12px gaps between them. */
  const gridH = (rows: number, tileW: number) => rows * (tileW + GEOMETRY.foot) + (rows - 1) * 12;

  /**
   * The chrome is asserted, not read past.
   *
   * `panelH` below is derived from `GEOMETRY.chrome` so that shortening the footer does not
   * invalidate the *shapes*, which are what this file is actually guarding. But the budget the
   * shapes come out of **is** the chrome, so a change to it has to be a deliberate edit here
   * too: at 129 the seven-column solve did not fit at seventeen mods and the answer was eight
   * columns; at 110 it does, and the answer is seven columns of a bigger tile.
   */
  it('is built on the stated chrome height', () => {
    expect(GEOMETRY.chrome).toBe(110);
  });

  /** cols, rows, tile width — the shape the fix produces. */
  const expected: Record<number, [number, number, number]> = {
    12: [6, 2, TILE.six],
    13: [7, 2, TILE.seven],
    16: [7, 3, TILE.seven],
    // 17 to 21 are the bug: they used to ask for three rows of a 195 tile, which did not fit,
    // and got clipped. They fit three rows of the 165.4 tile, and have since the solve started
    // checking. (They were eight columns of 143.25 while the chrome was 129 — the seven-column
    // grid missed the budget by nine pixels, which is exactly what the shorter footer freed.)
    17: [7, 3, TILE.seven],
    18: [7, 3, TILE.seven],
    19: [7, 3, TILE.seven],
    20: [7, 3, TILE.seven],
    21: [7, 3, TILE.seven],
    24: [8, 3, TILE.eight],
  };

  for (const [count, [columns, rows, tileW]] of Object.entries(expected)) {
    it(`lays ${count} mods out as ${columns} x ${rows}`, () => {
      const shape = solveGrid(Number(count), VIEW.w, VIEW.h);
      expect(shape.columns).toBe(columns);
      expect(shape.rows).toBe(rows);
      expect(shape.tileW).toBeCloseTo(tileW, 4);
      expect(shape.panelH).toBeCloseTo(gridH(rows, tileW) + GEOMETRY.chrome, 4);
      expect(shape.scrolls).toBe(false);
      expect(shape.gutter).toBe(0);
    });
  }

  it('is unchanged at the counts that already worked', () => {
    // The whole point of the regression range: 12 and 22-24 were fine and must stay that way,
    // or the fix has traded one set of broken counts for another.
    for (const count of [12, 13, 14, 15, 16, 22, 23, 24]) {
      const shape = solveGrid(count, VIEW.w, VIEW.h);
      expect(shape.scrolls).toBe(false);
      expect(shape.panelH).toBeLessThanOrEqual(maxPanelH(VIEW.h));
    }
    expect(solveGrid(12, VIEW.w, VIEW.h).panelH).toBe(506 + GEOMETRY.chrome);
    expect(solveGrid(24, VIEW.w, VIEW.h).panelH).toBe(609.75 + GEOMETRY.chrome);
  });

  it('never gives more mods a bigger tile or fewer columns', () => {
    // The defect in one sentence: seventeen mods got 195-wide tiles where sixteen got 143.25.
    // Monotonicity is what makes that shape unreachable at any count, not just the ones above.
    let previous = solveGrid(12, VIEW.w, VIEW.h);
    for (const count of ALL_COUNTS.slice(1)) {
      const shape = solveGrid(count, VIEW.w, VIEW.h);
      expect(shape.columns).toBeGreaterThanOrEqual(previous.columns);
      expect(shape.tileW).toBeLessThanOrEqual(previous.tileW + 1e-9);
      previous = shape;
    }
  });
});

describe('solveGrid — past what the window can hold', () => {
  it('scrolls rather than clipping, and holds a gutter back for the scrollbar', () => {
    // Twenty-four is the most that fits three rows of eight; twenty-five needs a fourth.
    expect(solveGrid(24, VIEW.w, VIEW.h).scrolls).toBe(false);
    const shape = solveGrid(25, VIEW.w, VIEW.h);
    expect(shape.scrolls).toBe(true);
    expect(shape.columns).toBe(GEOMETRY.maxColumns);
    expect(shape.gutter).toBe(GEOMETRY.gutter);
    // The rows fit inside the box even after a scrollbar has taken the gutter out of it, which
    // is the part that was wrong: an 11px-wide scrollbar used to cut the last column, and the
    // grid clips horizontally so nothing could bring it back.
    const clientW = shape.panelW - 2 * GEOMETRY.edge - shape.gutter;
    expect(shape.gridW).toBeLessThanOrEqual(clientW);
  });

  it('still fits the panel to the window at the fixture ceiling', () => {
    const shape = solveGrid(64, VIEW.w, VIEW.h);
    expect(shape.columns).toBe(8);
    expect(shape.rows).toBe(8);
    expect(shape.panelH).toBe(maxPanelH(VIEW.h));
    expect(shape.scrolls).toBe(true);
  });

  it('takes the whole window rather than overflowing it when the view is tiny', () => {
    const shape = solveGrid(24, 900, 400);
    expect(shape.panelW).toBeLessThanOrEqual(900 - GEOMETRY.insetX);
    expect(shape.panelH).toBeLessThanOrEqual(400 - GEOMETRY.insetY);
    expect(shape.tileW).toBeGreaterThan(0);
  });
});

describe('gridRows — the fill order', () => {
  it('fills left to right, which is the order MOD_ORDER is written in', () => {
    expect(gridRows(ids(17), 8).map((row) => row.length)).toEqual([8, 8, 1]);
    expect(gridRows(ids(12), 6).map((row) => row.map(String))).toEqual([
      ['m0', 'm1', 'm2', 'm3', 'm4', 'm5'],
      ['m6', 'm7', 'm8', 'm9', 'm10', 'm11'],
    ]);
  });

  it('holds every id, at every count and every column width', () => {
    for (const count of ALL_COUNTS) {
      const { columns } = solveGrid(count, VIEW.w, VIEW.h);
      const rows = gridRows(ids(count), columns);
      expect(rows.flat()).toHaveLength(count);
      expect(Math.max(...rows.map((r) => r.length))).toBeLessThanOrEqual(columns);
    }
  });

  it('drops nothing — the old column-major fill capped at eight columns and could', () => {
    // `gridColumns` stopped at `GRID_COLUMNS`, so a shape wanting nine columns silently lost a
    // mod. Row-major has no such cap: the column count is chosen, not fallen into.
    expect(gridRows(ids(64), 8).flat()).toHaveLength(64);
    expect(gridRows(ids(5), 8)).toEqual([ids(5)]);
    expect(gridRows(ids(3), 0).flat()).toHaveLength(3);
  });
});

describe('visibleWhenContracted', () => {
  it('counts the cells the three surviving columns actually hold', () => {
    // 17 mods in 8 columns is 8 / 8 / 1: the first three columns hold 3, 3 and 1, not nine.
    expect(visibleWhenContracted(gridRows(ids(17), 8), 0)).toBe(7);
    // Slid to the right, the short last row contributes nothing.
    expect(visibleWhenContracted(gridRows(ids(17), 8), 5)).toBe(6);
    // Twelve in six columns: two full rows, three each.
    expect(visibleWhenContracted(gridRows(ids(12), 6), 0)).toBe(2 * OPEN_COLUMNS);
    expect(visibleWhenContracted([], 0)).toBe(0);
  });
});
