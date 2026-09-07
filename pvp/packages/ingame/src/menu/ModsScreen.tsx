/**
 * Overlay — Mods. The full-bleed in-game menu.
 *
 * ## The shell
 *
 * Frames `289:1611` (grid), `289:3024` (grid + properties) and `289:2445` (list) on
 * the "Source of truth — In-game Client" page are **not a card floating over the
 * game** — they are the window. VOID mark hard left, the filter tabs centred across
 * the top, the view controls and the close hard right, one dotted cell rule under
 * the bar, then content from edge inset to edge inset, then a status line and the
 * keyboard hint at the bottom left.
 *
 * That matters beyond looks. The panel is **one wide, centred box** and does not resize
 * between the two states; what changes is how the space inside it is divided. The frames
 * hold twenty-four mods in eight columns and the registry holds twelve, so a grid
 * declared as three rows left four columns filling 674 of 1277 and half the panel
 * empty. The grid is shaped by the registry instead — two rows of six, tiles
 * sized from the panel — and it fills the panel edge to edge. See {@link solveGrid},
 * which now owns every length in it.
 *
 * There is no screen title and no search field. Both were removed in the design
 * work ("Remove the mods header. Center the navbar over the top of the window.
 * Remove the search bar."): the tabs say where you are, and ⌘K already searches —
 * a second, always-visible field for twelve items was chrome for its own sake.
 * `modSearch` survives in the store because the palette writes it.
 *
 * ## The two controls
 *
 * ```
 * layout     grid | list      how items lay out
 * inspector  open | closed    whether properties show beside
 * ```
 *
 * Four states, not three modes (contract §7). **§7's no-reflow rule is withdrawn:**
 * it said closing the inspector must not lay the first three columns out again, and
 * the layout that honoured it is the one that left half the panel empty. Selecting a
 * mod now contracts the grid from six columns to three while the properties come out
 * of the panel's right edge into the width it gave up — one movement, same duration
 * and curve for both halves (`--menu-move-*` in overlay.css).
 *
 * What §7 was *protecting* is kept by other means. Its point was that the thing you
 * clicked must not move out from under you, and with six columns collapsing to three
 * it easily could — so {@link firstVisibleColumn} slides the grid, on the same
 * curve and in the same movement, until the selected mod's column is one of the
 * three that survive. Only which three you are looking at can change, and only when
 * the mod you picked is not already among them.
 *
 * ## At any mod count
 *
 * The registry is twelve and generated from the schema, so this lays out for twelve — but
 * nothing here is written as twelve, and the dev fixture (`src/dev/fake-mods.ts`) reaches
 * sixty-four. {@link solveGrid} takes the count and the window and answers with the fewest
 * columns whose rows still fit: twelve give **two rows of six** at 195 wide, twenty-four give
 * **three rows of eight** at 143 — exactly what the frames draw — and the panel is as tall as
 * whatever that comes to. Nothing else changes with the count: the grid fills the panel, and
 * opening the properties contracts it to three columns.
 *
 * Between seventeen and twenty-one it did not. The old derivation took the rows first and the
 * columns from them, which at seventeen mods meant **six** columns and three rows — larger
 * tiles than at sixteen, and a panel 894 tall in a window with 796. The engine clamped it, the
 * third row went under the fold, a scrollbar appeared and ate eleven pixels of the sixth
 * column, and because the grid clips horizontally there was no way to scroll them back. Twelve
 * was fine and twenty-four was fine, which is why testing at twenty-four never saw it. The
 * arithmetic is in {@link solveGrid} now, and in `test/grid-geometry.test.ts`, because a
 * layout bug that only shows in one range of counts comes back otherwise.
 */

import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { isModOn, modsOnCount, useModSettings, useVoidStore } from '@/store/store';
import { FILTER_TABS, MOD_CATEGORY, MOD_ORDER, hueStyle, modLabel } from '@/registry';
import { MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { FilterTabs, Toggle, cx } from '@/ui';
import { CloseGlyph, GridGlyph, InspectorGlyph, ListGlyph, SearchGlyph, VoidMark } from './cell-art';
import { TilePreview } from './TilePreview';
import { ModInspector } from './ModPane';
import { ModList } from './ModList';
import { keybindLabel } from './settings-format';

/* -------------------------------------------------------------------------- */
/* The geometry                                                               */
/* -------------------------------------------------------------------------- */

/**
 * Every length the panel is built out of, in CSS pixels, stated once.
 *
 * These used to live in `overlay.css` as a chain of `calc()`, with only the row and column
 * counts coming from here. That split is what let the panel ask for a height the window did
 * not have: CSS could derive `--panel-h` from the tile but had no way to *check* it against
 * `max-height`, so at seventeen mods it computed 894 into a box the engine then clamped to 796
 * and cut the third row and eleven pixels of the sixth column off, unrecoverably — the grid
 * clips horizontally, so nothing could scroll them back.
 *
 * So the whole solve is here now (see {@link solveGrid}) and `overlay.css` reads the answers
 * off the element as custom properties. Two things follow. The arithmetic is unit-testable,
 * which is the only way a layout that breaks in one range of counts stops coming back. And the
 * window is consulted, which CSS could only do through `max-*` — after the fact.
 */
export const GEOMETRY = {
  /** Content inset, left and right, inside the panel. `--edge`. */
  edge: 24,
  /** Between tiles, on both axes. `--tile-gap`. */
  gap: 12,
  /** The tile's foot under its square preview well: `--tile-h` is `--tile-w` plus this. */
  foot: 52,
  /**
   * Bar 46 + rule 1 + body inset 16 + status 24 + hint 23. `--chrome-h`.
   *
   * Every pixel taken off the chrome is one the rows may have, and the solve spends it: at
   * seventeen mods a 129 chrome left 667 for the rows, seven columns of a 165.4 tile needed
   * 676.3, and the shape fell through to eight columns of 143.25. At 110 the budget is 686 and
   * the seven-column solve fits, so the same seventeen mods lay out three rows of a bigger tile.
   * That is the intent, not a side effect — `solveGrid` picks the *largest* tile that fits.
   */
  chrome: 110,
  /** The widest the panel ever is. Six columns of 195 plus the insets. */
  maxPanelW: 1278,
  /** Left over each side of the panel: `max-width: calc(100% - 48px)`. */
  insetX: 48,
  /** Left over above and below: `max-height: calc(100% - 24px)`. */
  insetY: 24,
  /** Below this the panel stops shrinking and the window clips it instead. */
  minPanelW: 480,
  /**
   * Held back on the right for a scrollbar, and only when the grid actually scrolls.
   *
   * Twelve rather than the six `::-webkit-scrollbar` asks for, because the three engines this
   * page runs through do not agree: Ultralight honours the `::-webkit-scrollbar` width (6),
   * Chrome lets the standard `scrollbar-width: thin` win instead and takes 11 — measured — and
   * macOS overlay scrollbars take none at all. Twelve covers all three. Reserving too much
   * costs a sliver of tile width in a state only the dev fixture reaches; reserving too little
   * clips the last column with no way to scroll it back, which is the bug this is here for.
   */
  gutter: 12,
  /** The widest the grid ever gets, in columns. The frames draw eight (`289:1611`). */
  maxColumns: 8,
} as const;

/**
 * The canvas the in-game view is guaranteed at least, in CSS pixels.
 *
 * `VoidClient.DESIGN_WIDTH/HEIGHT` are 1300 x 820 and the fit scale is
 * `min(fbWidth / 1300, fbHeight / 820)`, so whichever axis binds, the logical view is never
 * smaller than this. Used only as the fallback when there is no window to measure.
 */
export const DESIGN_CANVAS = { width: 1300, height: 820 } as const;

/** Columns that stay visible while the properties are open. */
export const OPEN_COLUMNS = 3;

/** What {@link solveGrid} works out: the panel, the tile, and the shape of the grid inside it. */
export interface GridShape {
  /** Columns the grid is built for — never more than `GEOMETRY.maxColumns`. */
  columns: number;
  /** Rows those columns need to hold every mod. */
  rows: number;
  tileW: number;
  tileH: number;
  panelW: number;
  panelH: number;
  /** The tiles and their gaps across: what `.mods-view` clips. */
  gridW: number;
  /** The height the rows need. Greater than the body's only when {@link scrolls}. */
  gridH: number;
  /** Width held back on the right for a scrollbar. Zero unless the grid scrolls. */
  gutter: number;
  /** Whether the rows are taller than the panel can be, so the grid has to scroll. */
  scrolls: boolean;
}

/**
 * The shape of the grid at a given mod count in a given window.
 *
 * **The rule: the fewest columns — so the largest tiles — whose rows still fit the height the
 * window leaves.** Everything else falls out of that. The tile is the content width divided by
 * the column count, so the grid fills the panel edge to edge by construction; the tile's height
 * is its width plus the foot, so the rows' height follows from the column count too; and the
 * panel is exactly as tall as the rows plus the chrome.
 *
 * ### Why not the shape it had
 *
 * It used to derive rows first — `ceil(count / 8)` — and then take the column count from the
 * rows, `ceil(count / rows)`. That balances the two axes, and it is why the column count ran
 * 6, 7, 8, **6**, 7, 8 as the count climbed: at seventeen mods it dropped back to six columns
 * *and* added a third row, so the tile jumped from 143 wide to 195 and the panel from 531 tall
 * to 894 — taller than the 796 the in-game canvas has. Twelve was fine and twenty-four was
 * fine, which is exactly why a night of testing at twenty-four never saw it.
 *
 * The balanced split also cannot be rescued, because it is *coupled to the fill order*. Filling
 * column-major forces `columns = ceil(count / rows)`, and no whole number of rows gives
 * seventeen mods eight columns: three rows give six, two give nine. Seventeen mods simply
 * cannot be laid out inside 667px of body height while filling the width column-major. Filling
 * row-major decouples them — pick eight columns, get three rows of 8/8/1 — which is why
 * {@link gridRows} replaced the old `gridColumns`.
 *
 * ### When nothing fits
 *
 * Past twenty-four mods (the fixture goes to sixty-four) no column count fits, and then the
 * grid takes the *most* columns, since the smallest tile is the one that puts the most on
 * screen, holds {@link GEOMETRY.gutter} back for the scrollbar and scrolls the rest. That is
 * the one state where the panel is not the size of its contents, and it is flagged as
 * {@link GridShape.scrolls} rather than left to be discovered as a clip.
 */
export function solveGrid(count: number, viewW: number, viewH: number): GridShape {
  const g = GEOMETRY;
  const n = Math.max(1, Math.floor(count));

  const panelW = Math.min(g.maxPanelW, Math.max(g.minPanelW, viewW - g.insetX));
  const maxPanelH = Math.max(g.chrome + 1, viewH - g.insetY);
  const contentW = panelW - 2 * g.edge;
  /** The height the rows may take: the tallest the panel may be, less its chrome. */
  const budget = maxPanelH - g.chrome;

  /** The shape `columns` columns produce, given how much width is held back for a scrollbar. */
  const shapeAt = (columns: number, gutter: number) => {
    const rows = Math.ceil(n / columns);
    const tileW = (contentW - gutter - (columns - 1) * g.gap) / columns;
    const tileH = tileW + g.foot;
    return { columns, rows, tileW, tileH, gridW: contentW - gutter, gridH: rows * tileH + (rows - 1) * g.gap };
  };

  // Fewest columns first, so the answer is the largest tile that fits rather than merely one
  // that does. Never more columns than there are mods: a ninth column for eight mods is a hole.
  const widest = Math.min(g.maxColumns, n);
  for (let columns = 1; columns <= widest; columns += 1) {
    const shape = shapeAt(columns, 0);
    if (shape.gridH <= budget) {
      return { ...shape, panelW, panelH: shape.gridH + g.chrome, gutter: 0, scrolls: false };
    }
  }

  const shape = shapeAt(widest, g.gutter);
  return { ...shape, panelW, panelH: maxPanelH, gutter: g.gutter, scrolls: true };
}

/** The view size to solve for before anything has been measured. */
function windowBox(): { width: number; height: number } {
  if (typeof window === 'undefined') return { ...DESIGN_CANVAS };
  return {
    width: window.innerWidth || DESIGN_CANVAS.width,
    height: window.innerHeight || DESIGN_CANVAS.height,
  };
}

/**
 * The box the panel is laid out in, re-read when it changes, with a ref to put on the panel.
 *
 * **The panel's own containing block, not `window.innerHeight`.** `.overlay` is absolutely
 * positioned inside `.menu-layer`, so that is what its `max-width: calc(100% - 48px)` and
 * `max-height: calc(100% - 24px)` resolve against — and a solve that measured something else
 * could size the grid for one box while the engine clamped it to another, which is a
 * different spelling of the bug this replaced. Reading the containing block makes the two
 * agree by construction. It is also the more trustworthy number in Ultralight: `clientHeight`
 * is the engine reporting a box it has just laid out, where `innerHeight` is a window
 * property that has to be right about device scale as well.
 *
 * In game the two are the same anyway — `.void-app` is `100%` of the view — so the window is a
 * sound first guess for the render before the ref exists, and the only value jsdom can give
 * (it lays nothing out, so `clientHeight` is always 0 there).
 *
 * Re-measured on `resize`, which in game is `UiHost.ensure` giving the view a new size after a
 * framebuffer or GUI-scale change; WebCore dispatches it the same way a browser does. If an
 * engine ever did not, the shape would still be right for the size the view had when the menu
 * was opened, because `MenuLayer` unmounts on the close fade's own `animationend` (`App.tsx`)
 * and every open measures again. An event listener, never a poll: §9 bans the frame loop and
 * an idle menu has to cost zero paints.
 */
function useHostBox(): [
  { width: number; height: number; settled: boolean },
  React.RefObject<HTMLDivElement | null>,
] {
  const ref = useRef<HTMLDivElement | null>(null);
  const [box, setBox] = useState(() => ({ ...windowBox(), phase: 'guess' as Phase }));
  useLayoutEffect(() => {
    const measure = () => {
      const host = ref.current?.parentElement;
      const next =
        host && host.clientWidth > 0 && host.clientHeight > 0
          ? { width: host.clientWidth, height: host.clientHeight }
          : windowBox();
      setBox((prev) =>
        // Same size, same object: a resize that does not change the box must not re-render the
        // grid, and `resize` fires for plenty that does not. The first measurement always
        // lands, though, even when it agrees with the guess, because it is also what moves the
        // panel out of `guess`.
        prev.phase !== 'guess' && prev.width === next.width && prev.height === next.height
          ? prev
          : { ...next, phase: prev.phase === 'guess' ? 'measured' : prev.phase },
      );
    };
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, []);

  // The correction has to land *before* the transitions come back, not with them. A transition
  // starts when a property's value differs across a style recalc and the after-change style
  // has a duration, so re-enabling them in the same recalc as the corrected width is exactly
  // how you get the animation this is here to avoid. Both passes are layout effects, so all of
  // it happens before the first paint — no timer, and no `requestAnimationFrame`, which §9 bans.
  useLayoutEffect(() => {
    if (box.phase !== 'measured') return;
    // The read is the point, not the value. React commits the corrected width and the class
    // removal one after the other, and the engine would collapse both into a single style
    // recalc — at which the transition is back and the width has changed, which is precisely
    // the animation being avoided. Forcing the recalc here splits them: the engine decides
    // about the width while transitions are still off, and the class comes away on its own.
    void ref.current?.offsetWidth;
    setBox((prev) => ({ ...prev, phase: 'settled' }));
  }, [box.phase]);

  return [{ width: box.width, height: box.height, settled: box.phase === 'settled' }, ref];
}

/** guess → measured → settled. See {@link useHostBox}; `settled` is when transitions resume. */
type Phase = 'guess' | 'measured' | 'settled';

/** Bottom-left hint, inspector closed — frame `289:1611`. */
export const MODS_HINT_GRID = 'R-Shift closes   ·   click a tile to edit it   ·   ⌘K search';
/** Bottom-left hint, inspector open — frames `289:3024` and `289:2445`. */
export const MODS_HINT_OPEN =
  'Close the panel to return to the full grid   ·   R-Shift closes';
/** @deprecated Kept as the closed-state name; the frames give the hint two states. */
export const MODS_FOOTER = MODS_HINT_GRID;

/** Tiles matching the current filter tab and search query, in reading order. */
export function visibleMods(filter: string, query: string): ModId[] {
  const q = query.trim().toLowerCase();
  return MOD_ORDER.filter((id) => {
    if (filter !== 'all' && MOD_CATEGORY[id] !== filter) return false;
    if (!q) return true;
    return (
      modLabel(id).toLowerCase().includes(q) ||
      MOD_REGISTRY[id].description.toLowerCase().includes(q) ||
      id.includes(q)
    );
  });
}

/**
 * The ids split into rows, filled left-to-right.
 *
 * **Row-major, where this used to be column-major**, and the reason is arithmetic rather than
 * taste. Filling column-major forces `columns = ceil(count / rows)`: the column count is not a
 * free choice, it is whatever the row count leaves. That is what made the tile size lurch —
 * seventeen mods can be three rows of six or two rows of nine, never three rows of eight, so
 * the layout had to take six columns of 195-wide tiles and a panel 894 tall in a window with
 * 796. Row-major lets {@link solveGrid} pick the column count outright and take the row count
 * from it, which is the whole fix; see that function.
 *
 * The contraction is unaffected, which is what made this safe to change. The grid narrows to
 * three columns by *clipping* — `.mods-view` transitions its width, the tiles never move — and
 * a box that clips the first three columns of a row of eight clips them just as well as it
 * clipped three whole columns. What changes is only which mods survive it: the first three of
 * every row rather than the first three columns, so the contracted grid shows a slice across
 * the whole registry instead of its opening run.
 *
 * The reading order is the frames' own, too — `MOD_ORDER` is documented as left to right, top
 * to bottom (`registry.ts`), which is what this now draws and column-major did not.
 */
export function gridRows(ids: ModId[], columns: number): ModId[][] {
  const width = Math.max(1, columns);
  const rows: ModId[][] = [];
  for (let i = 0; i < ids.length; i += width) rows.push(ids.slice(i, i + width));
  return rows;
}

/**
 * How many tiles are actually on screen with the properties beside the grid.
 *
 * The contracted grid shows columns `[first, first + OPEN_COLUMNS)` of every row, and the last
 * row is usually short, so this is not `OPEN_COLUMNS * rows`. The status line says what the
 * grid is showing, and a line that says nine when six are visible is worse than no line.
 */
export function visibleWhenContracted(rows: ModId[][], first: number): number {
  return rows.reduce(
    (total, row) => total + Math.max(0, Math.min(row.length, first + OPEN_COLUMNS) - first),
    0,
  );
}

/* -------------------------------------------------------------------------- */
/* The tile                                                                   */
/* -------------------------------------------------------------------------- */

function Tile({ id, selected }: { id: ModId; selected: boolean }) {
  const settings = useModSettings(id);
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const selectMod = useVoidStore((s) => s.selectMod);
  const toggleMod = useVoidStore((s) => s.toggleMod);

  const keybindKey = 'keybind' in settings ? 'keybind' : 'key' in settings ? 'key' : null;
  const keybind = keybindKey ? keybindLabel(settings[keybindKey] ?? null) : null;

  return (
    <div
      className={cx('modcell', selected && 'modcell--selected', !on && 'modcell--off')}
      data-mod-id={id}
      // The mod's own hue, so the selection dot and everything else accented in
      // this subtree reads `var(--hue, var(--accent))` (contract §1).
      style={hueStyle(id)}
      draggable
    >
      <button
        type="button"
        className="modcell__select"
        aria-pressed={selected}
        aria-label={modLabel(id)}
        onClick={() => selectMod(id)}
      />
      <span className="modcell__preview">
        {/* The frames chip the keybind into the preview's top-left corner, and only
            for a mod that has one — the absence is information (§1). */}
        {keybind !== null && keybind !== 'None' ? (
          <span className="modcell__kbd">{keybind}</span>
        ) : null}
        <TilePreview id={id} />
      </span>
      {/* One of the three things §1 lets take the hue. */}
      <span className="modcell__dot" />
      <span className="modcell__foot">
        <span className="modcell__names">
          <span className="modcell__name">{modLabel(id)}</span>
          <span className="modcell__cat">{MOD_CATEGORY[id]}</span>
        </span>
        {/* Toggling deliberately does NOT move the selection. Flipping a switch is
            direct manipulation of that mod; selection is navigation, and the tile
            body still does it. Coupled, one toggle repainted the outgoing tile, the
            incoming tile and the whole properties panel as one damage rectangle. */}
        <Toggle
          checked={on}
          size="s"
          label={`${modLabel(id)} enabled`}
          onChange={(next) => toggleMod(id, next)}
        />
      </span>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Menu chrome — the bar of §7                                                */
/* -------------------------------------------------------------------------- */

function LayoutControl() {
  const layout = useVoidStore((s) => s.layout);
  const setLayout = useVoidStore((s) => s.setLayout);
  return (
    <div className="viewseg" role="radiogroup" aria-label="Layout">
      {(['grid', 'list'] as const).map((value) => (
        <button
          key={value}
          type="button"
          role="radio"
          aria-checked={layout === value}
          aria-label={value === 'grid' ? 'Grid' : 'List'}
          className={cx('viewseg__seg', layout === value && 'viewseg__seg--on')}
          onClick={() => setLayout(value)}
        >
          {value === 'grid' ? <GridGlyph /> : <ListGlyph />}
        </button>
      ))}
    </div>
  );
}

/**
 * The magnifier: first of the tools on the right of the bar.
 *
 * It opens the quick palette — the same surface ⌘K opens, and the same call
 * `MenuLayer`'s shortcut makes. The hint line under the grid has advertised
 * `⌘K search` all along; this is that shortcut with a face on it, for a player
 * who has never read the hint line.
 *
 * Opens rather than toggles, which is not a shortcut taken: the palette's own dim
 * covers the whole layer, so the button cannot be clicked while it is up, and
 * there is no reachable state where "toggle" and "open" differ. Nothing marks it
 * as on for the same reason — a pressed state nobody can ever see is a lie about
 * the control.
 */
function SearchControl() {
  const setPaletteOpen = useVoidStore((s) => s.setPaletteOpen);
  return (
    <button
      type="button"
      className="obtn"
      aria-label="Search"
      aria-keyshortcuts="Meta+K"
      onClick={() => setPaletteOpen(true)}
    >
      <SearchGlyph />
    </button>
  );
}

function InspectorControl() {
  const inspector = useVoidStore((s) => s.inspector);
  const toggleInspector = useVoidStore((s) => s.toggleInspector);
  return (
    <button
      type="button"
      className={cx('obtn', inspector === 'open' && 'obtn--on')}
      aria-label="Properties"
      aria-pressed={inspector === 'open'}
      onClick={toggleInspector}
    >
      <InspectorGlyph />
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* The status line under the content                                          */
/* -------------------------------------------------------------------------- */

function StatusLine({
  shown,
  visible,
  total,
  enabled,
  layout,
  open,
  hidden,
}: {
  shown: number;
  /** Tiles the contracted grid actually has on screen. Only read when {@link hidden}. */
  visible: number;
  total: number;
  enabled: number;
  layout: 'grid' | 'list';
  open: boolean;
  /** Whether the panel is covering columns the grid would otherwise show. */
  hidden: boolean;
}): React.ReactElement {
  if (layout === 'list') {
    return (
      <div className="ostatus ostatus--ruled">
        <span className="ostatus__left">{`Showing ${shown} of ${total}`}</span>
        <span className="ostatus__right">
          {/* One cell per mod, filled for the enabled ones. Monochrome: a count is
              historical data, not a live value (§1). */}
          <span className="ostatus__cells" aria-hidden="true">
            {Array.from({ length: total }, (_, i) => (
              <span key={i} className={cx('ostatus__cell', i < enabled && 'ostatus__cell--on')} />
            ))}
          </span>
          <span>{`${enabled} enabled`}</span>
        </span>
      </div>
    );
  }
  if (open && hidden) {
    // Clipped to the grid column: with the panel beside it, the line belongs to the grid and
    // says what the grid is actually showing. It used to say "scroll for more", which was true
    // of the frame's twenty-four mods in a 980-tall board and false here — the panel is now
    // sized so three rows fit exactly, and what the player cannot see is the column the
    // properties are standing on, not something below the fold.
    return (
      <div className="ostatus ostatus--grid">
        <span className="ostatus__left">{`Showing ${Math.min(shown, visible)} of ${total}`}</span>
      </div>
    );
  }
  return (
    <div className="ostatus">
      <span className="ostatus__left">{`${enabled} of ${total} enabled`}</span>
      <span className="ostatus__right">Changes apply immediately</span>
    </div>
  );
}

/* -------------------------------------------------------------------------- */

/**
 * Which column the contracted grid starts at, so the mod you just clicked is one of the three
 * still on screen.
 *
 * §7 used to forbid the grid reflowing at all, which made this unnecessary and the layout
 * half-empty. Now the grid contracts from six columns to three, and without this, selecting a
 * mod in column five would hide the tile you had just clicked behind the panel that opened
 * because you clicked it. It moves by the least it can: a selection already inside the first
 * three does not move the grid at all, and anything further right is brought in as the *last*
 * of the three rather than centred, so the movement is small and always in one direction.
 *
 * Returned as a column index rather than applied as a scroll, for two reasons. A scroller
 * cannot be scrolled before there is room — `scrollLeft` is clamped to `scrollWidth -
 * clientWidth`, which is zero until the contraction has actually narrowed the box, so setting
 * it on selection lands on nothing and setting it on a timer is a second movement bolted to the
 * end of the first. As a `margin-left` in the same units as the columns it slides with the
 * contraction, on the same curve, and there is nothing to measure. A transform would do it too
 * and is the thing not to reach for: it hands the engine a composited layer full of text, which
 * is exactly the defect the nav tabs demonstrated.
 */
export function firstVisibleColumn(
  columnCount: number,
  selectedColumn: number,
  open: boolean,
): number {
  if (!open || selectedColumn < 0) return 0;
  const last = Math.max(0, columnCount - OPEN_COLUMNS);
  return Math.min(Math.max(0, selectedColumn - (OPEN_COLUMNS - 1)), last);
}

export function ModsScreen() {
  const search = useVoidStore((s) => s.modSearch);
  const filter = useVoidStore((s) => s.modFilter);
  const setFilter = useVoidStore((s) => s.setModFilter);
  const selected = useVoidStore((s) => s.selectedMod);
  const selectMod = useVoidStore((s) => s.selectMod);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const layout = useVoidStore((s) => s.layout);
  const inspector = useVoidStore((s) => s.inspector);
  const enabled = useVoidStore((s) => modsOnCount(s.loadout));

  // The grid is shaped by the *registry's* count and the window, never by the filter: `Visual`
  // matches two mods, and tiles that grew to a third of the window and back every time a tab
  // was clicked would be unusable. A filter leaves space in the grid rather than resizing it.
  const [host, panelRef] = useHostBox();
  const shape = useMemo(
    () => solveGrid(MOD_ORDER.length, host.width, host.height),
    [host.width, host.height],
  );

  const ids = useMemo(() => visibleMods(filter, search), [filter, search]);
  const rows = useMemo(
    () => (layout === 'grid' ? gridRows(ids, shape.columns) : []),
    [ids, layout, shape.columns],
  );
  const selectedColumn = useMemo(() => {
    const at = ids.indexOf(selected);
    return at < 0 ? -1 : at % shape.columns;
  }, [ids, selected, shape.columns]);
  const filledColumns = rows.length > 0 ? Math.max(...rows.map((row) => row.length)) : 0;
  const shift = firstVisibleColumn(filledColumns, selectedColumn, inspector === 'open');

  const onKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      // The grid reads across a row and then down, so Left/Right walk within a row and Up/Down
      // jump a whole row. The list is one column of rows, where a row is one step either way.
      const down = useVoidStore.getState().layout === 'grid' ? shape.columns : 1;
      const across = useVoidStore.getState().layout === 'grid' ? 1 : 0;
      const delta =
        e.key === 'ArrowDown'
          ? down
          : e.key === 'ArrowUp'
            ? -down
            : e.key === 'ArrowRight'
              ? across
              : e.key === 'ArrowLeft'
                ? -across
                : 0;
      if (delta !== 0) {
        if (ids.length === 0) return;
        e.preventDefault();
        const at = ids.indexOf(selected);
        const next = at < 0 ? 0 : Math.min(ids.length - 1, Math.max(0, at + delta));
        selectMod(ids[next]!);
        return;
      }
      if (e.key === 'Enter' && ids.includes(selected)) {
        e.preventDefault();
        toggleMod(selected, !isModOn(useVoidStore.getState().loadout, selected));
      }
    },
    [ids, selected, selectMod, toggleMod, shape.columns],
  );

  const open = inspector === 'open';

  return (
    // The panel's whole geometry is solved here and handed to `overlay.css` as lengths (see
    // `solveGrid`); nothing below re-derives it. The state classes stay on the shell because
    // the status line and the grid clip both read them, but they do not change the panel's box:
    // opening the properties divides the space inside it, it does not resize it.
    <div
      ref={panelRef}
      className={cx(
        'overlay',
        `overlay--${layout}`,
        open ? 'overlay--open' : 'overlay--closed',
        shape.scrolls && 'overlay--scrolls',
        // Until the panel's own box has been measured the shape is a guess from the window,
        // and correcting a guess is not a movement anyone asked to see. See `.overlay--sizing`.
        !host.settled && 'overlay--sizing',
      )}
      style={
        {
          ['--panel-cols' as string]: `${shape.columns}`,
          ['--panel-rows' as string]: `${shape.rows}`,
          ['--panel-w' as string]: `${shape.panelW}px`,
          ['--panel-h' as string]: `${shape.panelH}px`,
          ['--tile-w' as string]: `${shape.tileW}px`,
          // Zero unless the rows are taller than the panel can be. Held back on the right so
          // the scrollbar cannot eat the last column, which the grid clips and cannot scroll.
          ['--grid-gutter' as string]: `${shape.gutter}px`,
          // Which column the contracted grid starts at; the slide itself is CSS, in the same
          // units and on the same curve as the contraction. See `firstVisibleColumn`.
          ['--grid-first' as string]: `${shift}`,
        } as React.CSSProperties
      }
      onKeyDown={onKeyDown}
    >
      <div className="obar">
        <VoidMark />
        {/* Centred on the window, not between its neighbours: the mark and the
            controls are different widths and a nav that drifts with them is not
            centred, it is merely between things. */}
        <FilterTabs
          className="obar__nav"
          tabs={FILTER_TABS}
          value={filter}
          onChange={setFilter}
          label="Mod filter"
        />
        <div className="obar__tools">
          <SearchControl />
          <LayoutControl />
          <InspectorControl />
          <button type="button" className="obtn obtn--close" aria-label="Close" onClick={closeMenu}>
            <CloseGlyph />
          </button>
        </div>
      </div>

      {/* The dotted cell rule under the bar, full content width. */}
      <div className="orule" aria-hidden="true" />

      <div
        className={cx(
          'mods-body',
          `mods-body--${layout}`,
          open ? 'mods-body--open' : 'mods-body--closed',
        )}
      >
        <div className="mods-view">
          {ids.length === 0 ? (
            <div className="mods-empty">No mod matches “{search}”.</div>
          ) : layout === 'grid' ? (
            <div className="mods-grid">
              {rows.map((row, index) => (
                <div key={index} className="mods-row" data-row={index}>
                  {row.map((id) => (
                    <Tile key={id} id={id} selected={id === selected} />
                  ))}
                </div>
              ))}
            </div>
          ) : (
            <ModList ids={ids} selected={selected} />
          )}
        </div>

        {/* Always mounted: the panel slides out rather than disappearing, and a
            component that unmounts has nothing to transition. */}
        <ModInspector id={selected} open={open} />
      </div>

      <StatusLine
        shown={ids.length}
        visible={visibleWhenContracted(rows, shift)}
        total={MOD_ORDER.length}
        enabled={enabled}
        layout={layout}
        open={open}
        hidden={layout === 'grid' && filledColumns > OPEN_COLUMNS}
      />

      <div className="ohint">{open ? MODS_HINT_OPEN : MODS_HINT_GRID}</div>
    </div>
  );
}
