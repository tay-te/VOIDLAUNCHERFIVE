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

import { GEOMETRY, solveGrid, gridRows } from '@/menu/ModsScreen';
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
    // The canvas leaves 1252 here (1300 less the inset), and the panel asks for 1150, so the
    // width the solve returns is its own. It was the other way round at 1278: the canvas
    // clamped the panel, the tile was still sized for 1278, and 26px of the last column ran
    // off the panel's own clip. Asserted as the smaller of the two rather than as 1150, so
    // that a panel wider than this canvas is caught here rather than at the clip again.
    expect(shape.panelW).toBe(maxPanelW(NARROW.w));
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

  /**
   * The property the whole panel is now built on, and the reason this replaced an assertion
   * that the panel was *exactly its contents*.
   *
   * A content-derived height made the menu's size a function of the registry: fourteen mods
   * gave a 557-tall panel and the fifteenth would have grown it to 786, so shipping a mod
   * resized five screens that have nothing to do with the grid. It is one box now, and the
   * count only moves what is drawn inside it.
   *
   * Asserted across the fixture's whole range rather than at a count or two, because "the
   * panel is the same at every count" is precisely the kind of claim that is true at the two
   * counts anybody runs and false in between — which is the defect this file was opened for.
   */
  it.each(ALL_COUNTS)('at %i mods the panel is the same fixed box', (count) => {
    const shape = solveGrid(count, VIEW.w, VIEW.h);
    expect(shape.panelH).toBe(GEOMETRY.maxPanelH);
    expect(shape.panelW).toBe(maxPanelW(VIEW.w));
  });

  it('scrolls exactly when the rows outgrow the fixed panel, and never clips instead', () => {
    for (const count of ALL_COUNTS) {
      const shape = solveGrid(count, VIEW.w, VIEW.h);
      const budget = GEOMETRY.maxPanelH - GEOMETRY.chrome;
      // The two have to agree in both directions: a grid taller than the body that does not
      // say so is the original bug (rows under the fold, unreachable), and one that says so
      // when it fits is a scrollbar eating a column for nothing.
      expect(shape.scrolls).toBe(shape.gridH > budget);
      expect(shape.gutter).toBe(shape.scrolls ? GEOMETRY.gutter : 0);
    }
  });
});

describe('solveGrid — the counts that used to break', () => {
  /**
   * The three tiles the 1150-wide panel can produce, to the fraction. Written out because the
   * table below is only readable if the numbers in it have names.
   */
  const TILE = { six: 1042 / 6, seven: 1030 / 7, eight: 127.25 };

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
    16: [8, 2, TILE.eight],
    // 17 to 21 are the bug: they used to ask for three rows of the widest tile, which did not
    // fit, and got clipped. What they fit is whatever the body of the day allows — three rows
    // of seven while the panel was 796 tall, eight columns now that it is 716 and the body is
    // 606. Which of the two it is has never been the property worth asserting; that the rows
    // fit the box the panel actually has is, and that is the line below the table.
    17: [8, 3, TILE.eight],
    18: [8, 3, TILE.eight],
    19: [8, 3, TILE.eight],
    20: [8, 3, TILE.eight],
    21: [8, 3, TILE.eight],
    24: [8, 3, TILE.eight],
  };

  for (const [count, [columns, rows, tileW]] of Object.entries(expected)) {
    it(`lays ${count} mods out as ${columns} x ${rows}`, () => {
      const shape = solveGrid(Number(count), VIEW.w, VIEW.h);
      expect(shape.columns).toBe(columns);
      expect(shape.rows).toBe(rows);
      expect(shape.tileW).toBeCloseTo(tileW, 4);
      // The rows fit the fixed body; the panel itself is the same box either way, so what is
      // worth asserting here is the fit, not the height.
      expect(gridH(rows, tileW)).toBeLessThanOrEqual(GEOMETRY.maxPanelH - GEOMETRY.chrome);
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
    // The grids these counts produce, which is what "unchanged" means now that the panel
    // holding them is a constant.
    expect(solveGrid(12, VIEW.w, VIEW.h).gridH).toBeCloseTo(463.333, 3);
    expect(solveGrid(24, VIEW.w, VIEW.h).gridH).toBe(561.75);
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
    expect(shape.panelH).toBe(GEOMETRY.maxPanelH);
    expect(shape.scrolls).toBe(true);
  });

  it('takes the whole window rather than overflowing it when the view is tiny', () => {
    const shape = solveGrid(24, 900, 400);
    expect(shape.panelW).toBeLessThanOrEqual(900 - GEOMETRY.insetX);
    expect(shape.panelH).toBeLessThanOrEqual(400 - GEOMETRY.insetY);
    expect(shape.tileW).toBeGreaterThan(0);
  });
});

describe('solveGrid — the panel is a fixed box', () => {
  /**
   * The change that motivated the pin, stated as the two counts either side of it.
   *
   * Sixteen mods lay out two rows and seventeen lay out three. Under the old solve a row
   * appearing was also a 229px change in the height of the menu — and of Settings, Loadouts,
   * Party and the HUD editor, which share the box and have no rows at all.
   *
   * (It was fourteen and fifteen that straddled the change while the body was 686. The pair
   * moved when the panel came down to 716; the property does not depend on which pair it is,
   * only on there being one.)
   */
  it('does not change height when the row count changes', () => {
    const two = solveGrid(16, VIEW.w, VIEW.h);
    const three = solveGrid(17, VIEW.w, VIEW.h);
    expect(two.rows).toBe(2);
    expect(three.rows).toBe(3);
    expect(two.panelH).toBe(three.panelH);
    expect(two.panelW).toBe(three.panelW);
  });

  /**
   * The clamp that used to be CSS's, done before the arithmetic instead of after it.
   *
   * The `?debug` harness draws a fixed 1600 x 980 board, which is taller than the in-game
   * canvas. Solving the grid against the *window* there would size three rows of 195 tiles
   * (765px) for a panel that is only 796 tall — 686 of body — and CSS would clamp the box
   * afterwards with no way to tell the grid. That is the original defect's exact shape, in
   * the one window where it can still be reached, so the budget comes from the panel.
   */
  it('solves against the panel, not against a window taller than it', () => {
    const tall = solveGrid(14, 1600, 980);
    const game = solveGrid(14, VIEW.w, VIEW.h);
    expect(tall.panelH).toBe(GEOMETRY.maxPanelH);
    expect(tall.rows).toBe(game.rows);
    expect(tall.columns).toBe(game.columns);
    expect(tall.gridH).toBeLessThanOrEqual(GEOMETRY.maxPanelH - GEOMETRY.chrome);
  });

  /** A window shorter than the panel still wins: the clamp binds downwards, as the CSS says. */
  it('gives up the fixed height rather than overflow a short window', () => {
    const shape = solveGrid(14, VIEW.w, 600);
    expect(shape.panelH).toBe(600 - GEOMETRY.insetY);
    expect(shape.panelH).toBeLessThan(GEOMETRY.maxPanelH);
  });

  /**
   * The height is a chosen number, so the question it has to answer is how near it stands to a
   * shape it does not intend.
   *
   * The boundary either side of 716 is the seven-column three-row solve: 621.4 of rows against
   * a 606 body. Fifteen pixels of clearance, where 796's body of 686 cleared the same solve's
   * 676.29 by 9.7 — so the box is further from the edge than the one it replaces, and a
   * rounding in `chrome`, `foot` or `gap` cannot flip the shape out from under it.
   */
  it('stands clear of the shape its body height is nearest', () => {
    expect(GEOMETRY.maxPanelH).toBe(716);
    const body = GEOMETRY.maxPanelH - GEOMETRY.chrome;
    const contentW = GEOMETRY.maxPanelW - 2 * GEOMETRY.edge;
    const sevenWide = (contentW - 6 * GEOMETRY.gap) / 7;
    const threeRows = 3 * (sevenWide + GEOMETRY.foot) + 2 * GEOMETRY.gap;
    expect(threeRows - body).toBeGreaterThan(9.7);
    // And the shape the body does hold, at the same count that used to take the other one.
    expect(solveGrid(15, VIEW.w, VIEW.h).columns).toBe(8);
    expect(solveGrid(15, VIEW.w, VIEW.h).gridH).toBeLessThanOrEqual(body);
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

/**
 * The grid shows everything the filter matched — there is no second, smaller number.
 *
 * `visibleWhenContracted` used to exist because with the properties open the grid was clipped
 * to three columns, so "showing 12 of 12" was a lie and the status line had to count the cells
 * the panel was not standing on (which is not `3 × rows`: the last row is usually short). The
 * grid is never clipped now, so the two numbers that mattered are the ones `solveGrid` already
 * produces — every mod is laid out, and every laid-out mod is inside the box.
 */
describe('every mod the filter matched is on screen', () => {
  it('lays out every id, and the rows are exactly as wide as the box that holds them', () => {
    for (const count of [12, 17, 24, 25, 64]) {
      const shape = solveGrid(count, VIEW.w, VIEW.h);
      const rows = gridRows(ids(count), shape.columns);
      expect(rows.flat()).toHaveLength(count);
      // The widest row is the column count, and the box is that many tiles plus their gaps —
      // which is `gridW`. Nothing is clipped horizontally at any count.
      const widest = Math.max(...rows.map((row) => row.length));
      const needed = widest * shape.tileW + (widest - 1) * GEOMETRY.gap;
      expect(needed).toBeLessThanOrEqual(shape.gridW + 0.001);
    }
  });
});
