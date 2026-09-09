/**
 * Overlay — Mods. The full-bleed in-game menu, and the shell the mod page lives in.
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
 * That matters beyond looks. The panel is **one wide, centred box of a fixed size** — it does
 * not resize between its states, and it does not resize with the registry either. What changes
 * is what is inside it. The frames hold twenty-four mods in eight columns and the registry
 * holds fourteen, so a grid declared as three rows left five columns filling most of 1277 and a
 * third of the panel empty. The grid is shaped by the registry instead — two rows of seven,
 * tiles sized from the panel — inside a box that is 1278 x 796 at every count.
 * See {@link solveGrid}, which owns every length in it, and {@link GEOMETRY.maxPanelH} for why
 * the height is pinned rather than taken from the rows.
 *
 * There is no screen title and no search field. Both were removed in the design
 * work ("Remove the mods header. Center the navbar over the top of the window.
 * Remove the search bar."): the tabs say where you are, and ⌘K already searches —
 * a second, always-visible field for thirteen items was chrome for its own sake.
 * `modSearch` survives in the store because the palette writes it.
 *
 * ## One control, and a page
 *
 * ```
 * layout     grid | list      how items lay out
 * ```
 *
 * There used to be a second — `inspector: open | closed`, whether the properties showed beside
 * the items — and selecting a mod contracted the grid from six columns to three while they
 * came out of the right edge. **That is gone, and it is a removal rather than a new mode
 * bolted beside the old one.** The reason is the card: it carries the mod's switch, so the
 * grid is the working surface and most interactions never need the properties at all.
 * Contracting the grid to keep a panel visible optimised the rare case at the cost of the
 * common one. So:
 *
 *   · the grid is always full-panel and never contracts — the form it already had with the
 *     inspector shut, which is why this removes a state rather than inventing one;
 *   · selecting a mod goes to {@link ModPage}, the whole panel, one mod;
 *   · going back returns to the grid, unchanged.
 *
 * Gone with it: `OPEN_COLUMNS`, `firstVisibleColumn` (which slid the grid so the tile you
 * clicked survived the contraction), `visibleWhenContracted` (which corrected the status line
 * for the tiles the panel was standing on), `.mods-view`'s width transition and the clip it
 * depended on, and the bar's properties toggle — there is no panel to toggle, which also
 * relieves a bar that was carrying too much.
 *
 * **The panel box is the same on both routes** — and now on all six, since Settings, Loadouts,
 * Party and the HUD editor are drawn in it too. `solveGrid` answers with the same box whatever
 * the route and whatever the count, and each page is laid out inside it, so navigating changes
 * no length on this element. That is the property the contraction was reaching for ("the thing
 * you clicked must not move") and could only half keep; pinning the height is what finally made
 * it true of the *other* five screens, whose size was until now set by how many mods the grid
 * they are not had.
 *
 * ## At any mod count
 *
 * The registry is fourteen and generated from the schema, so this lays out for fourteen — but
 * nothing here is written as thirteen, and the dev fixture (`src/dev/fake-mods.ts`) reaches
 * sixty-four. {@link solveGrid} takes the count and the window and answers with the fewest
 * columns whose rows still fit: fourteen give **two rows of seven** at 165 wide (the last row
 * short, which row-major fill allows), fifteen give **three rows of seven** at the same width,
 * twenty-four give **three rows of eight** at 143 — exactly what the frames draw. The panel is
 * the same box through all of it; what the count moves is the grid inside it.
 *
 * The watermark is what took it from twelve to thirteen, and therefore from six columns to
 * seven and the tile from 195 to 165. That is the solve working, not a layout to re-tune: the
 * one length that had to move with it is the label, because `VOID watermark` no longer fitted
 * a 165 tile and the registry now says `Watermark`.
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
import {
  BackGlyph,
  CloseGlyph,
  GridGlyph,
  ListGlyph,
  MoveGlyph,
  SearchGlyph,
  SettingsGlyph,
  VoidMark,
} from './cell-art';
import { TilePreview } from './TilePreview';
import { SETTINGS_HINT, SettingsScreen } from './SettingsScreen';
import { ModPage } from './ModPage';
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
  /**
   * The tallest the panel ever is — and, on the in-game canvas, the only height it ever has.
   *
   * **The panel is a fixed box now.** It used to be exactly as tall as its own contents
   * (`panelH = gridH + chrome`), which meant the menu's size was a function of how many mods
   * were in the registry: fourteen laid out two rows and gave a 557-tall panel, and the
   * fifteenth would have flipped the grid to three rows and grown the panel to 786 — the menu
   * changing shape because somebody shipped a mod. Width was never allowed to do that
   * ({@link maxPanelW} has been a constant throughout); height only did because nothing had
   * pinned it. This is the missing half of that symmetry.
   *
   * It is also what every other route wanted. The panel box is shared — the grid, a mod's
   * page, Settings, Loadouts, Party and the HUD editor are all drawn in it — so a
   * content-derived height meant the *grid's* mod count silently set the height of five
   * screens that have nothing to do with it.
   *
   * **Why 796 and not the 786 three rows strictly need.** 796 is the whole canvas:
   * `VoidClient.DESIGN_HEIGHT` is 820 and the fit scale pins the logical view to exactly that
   * height on every aspect ratio, less {@link insetY}. Choosing it rather than 786 keeps the
   * solve's *column* choice bit-for-bit what it was — the budget below stays 686 — so the
   * shape at all 53 counts `test/grid-geometry.test.ts` covers is unchanged by this. 786
   * would have cut the budget to 676, and three rows of seven need 676.29: fifteen through
   * twenty-one mods would have silently fallen through to eight columns over a third of a
   * pixel. A constant that is one rounding away from changing the layout is not a constant.
   */
  maxPanelH: 796,
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
  /**
   * Fixed, then clamped to the window — the same two steps `panelW` takes, in the same order.
   * The clamp only ever binds in a window shorter than the design canvas; in game the view is
   * exactly 820 CSS tall, so this is flatly `maxPanelH`.
   */
  const panelH = Math.max(g.chrome + 1, Math.min(g.maxPanelH, viewH - g.insetY));
  const contentW = panelW - 2 * g.edge;
  /**
   * The height the rows may take: the panel, less its chrome.
   *
   * Taken from `panelH` and not from the window, which matters in the one case they differ —
   * a window taller than the design canvas, which the `?debug` harness's 1600 x 980 board is.
   * Solving against the window there would size the grid for height the panel does not have,
   * and that is the exact shape of the bug this whole file exists for: a length CSS clamped
   * after the arithmetic had already spent it.
   */
  const budget = panelH - g.chrome;

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
      return { ...shape, panelW, panelH, gutter: 0, scrolls: false };
    }
  }

  const shape = shapeAt(widest, g.gutter);
  return { ...shape, panelW, panelH, gutter: g.gutter, scrolls: true };
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

/** Bottom-left hint on the grid and the list — frame `289:1611`. */
export const MODS_HINT_GRID = 'R-Shift closes   ·   click a tile to edit it   ·   ⌘K search';
/**
 * Bottom-left hint on a mod's page.
 *
 * It names the way back rather than describing the state, because the state is now obvious —
 * you are looking at one mod — and the question the hint can actually answer is "how do I get
 * out". Both routes out are named; Escape is not, because in game it never reaches the page
 * (see the note in `ModPage.tsx`) and it closes the whole menu, which R-Shift already covers.
 */
export const MODS_HINT_PAGE =
  'Done or ‹ Mods returns to the grid   ·   R-Shift closes';
/** @deprecated Kept as the grid-state name. */
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
 * The reading order is the frames' own, too — `MOD_ORDER` is documented as left to right, top
 * to bottom (`registry.ts`), which is what this now draws and column-major did not.
 */
export function gridRows(ids: ModId[], columns: number): ModId[][] {
  const width = Math.max(1, columns);
  const rows: ModId[][] = [];
  for (let i = 0; i < ids.length; i += width) rows.push(ids.slice(i, i + width));
  return rows;
}

/* -------------------------------------------------------------------------- */
/* The tile                                                                   */
/* -------------------------------------------------------------------------- */

/**
 * One tile.
 *
 * **The card body and the card's switch do different things, and the structure is what makes
 * that true rather than a guard in a handler.** `.modcell__select` is an absolutely positioned
 * `<button>` stretched behind the contents; the switch is a *sibling* of it, lifted above it by
 * `.modcell__foot`'s stacking level. So a click on the switch lands on the switch and there is
 * no ancestor for it to bubble to — the tile is not a button wrapping its own controls, which
 * is the shape that would make the toggle navigate.
 *
 * This has been wrong in both directions. It was first written with the selector over the
 * *whole* tile, switch included, and mods could not be toggled at all. Making the body open a
 * page is the mirror-image risk: the failure would now be a click on the switch that flips the
 * mod and then leaves the grid. Neither is visible in jsdom, where every click dispatches
 * cleanly to whatever element the test names — both were found in game, and both are checked
 * there.
 */
function Tile({ id, selected }: { id: ModId; selected: boolean }) {
  const settings = useModSettings(id);
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const openMod = useVoidStore((s) => s.openMod);
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
        onClick={() => openMod(id)}
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
        {/* Toggling deliberately does NOT navigate, and does not move the selection either.
            Flipping a switch is direct manipulation of that mod and it belongs on the grid,
            where you can flip the next one; going to the page is a different intention and the
            tile body is what carries it. This is the whole reason the grid is the working
            surface, and therefore the reason the grid gets the panel to itself. */}
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

/**
 * `‹ Mods` — the way back out of a mod's page.
 *
 * It replaces the properties toggle in the bar, which is a straight trade: that button existed
 * to show and hide a panel that no longer exists, and this one exists because a page needs a
 * visible way back. The bar therefore carries no more than it did, and on the page it carries
 * considerably less.
 *
 * **Labelled, not just a chevron.** It is the only affordance in the overlay whose meaning
 * depends on where you came from, and "Mods" is where it goes — a bare arrow beside a VOID
 * mark reads as decoration. It is also the reason the bar's centre is empty on the page rather
 * than repeating the mod's name: the name is already the largest thing on the screen, an inch
 * below.
 */
function BackControl() {
  const closeMod = useVoidStore((s) => s.closeMod);
  return (
    <button type="button" className="obar__back" aria-keyshortcuts="Escape" onClick={closeMod}>
      <BackGlyph />
      <span>Mods</span>
      {/* The cap sits on the control Escape drives, and Escape from a page goes *back*. */}
      <EscCap />
    </button>
  );
}

/**
 * The `esc` key cap.
 *
 * **It marks whatever Escape does from where you are**, which is the whole reason it can be
 * drawn at all. Escape means "up one level" (`bridge/connect.ts`, `keepsEscape`), so on the grid
 * it sits on the close button — the level above the grid is the game — and on a mod's page it
 * sits on `‹ Mods`, because that is the level above a page. One cap, never two, and never on a
 * control the key does not actually press.
 *
 * A cap and not a word: `esc` is a key, and the overlay already draws keys this way — the
 * keybind chipped into a tile's preview (`.modcell__kbd`) and the palette's own footer are the
 * same object. Same geometry either side, so the two placements read as one thing moving.
 */
function EscCap(): React.ReactElement {
  return (
    <span className="okbd" aria-hidden="true">
      esc
    </span>
  );
}

/**
 * `HUD LAYOUT` — the way into the layout editor, on the bar.
 *
 * **This is the fix for "we need a HUD editor", which is a discoverability bug rather than a
 * missing feature.** The editor already existed and already did everything asked of it, and the
 * user still asked for one to be built: it was reachable only from a row inside Settings and from
 * ⌘K, and neither is somewhere you find a thing you do not already know about.
 *
 * **On the left, not in the tool cluster.** The bar's right-hand group is `settings · search ·
 * close` — things that *act on this window*, in ascending order of consequence — and it has been
 * called crowded three times; a fourth icon in it is the mistake that has already been made once
 * here. The editor is not a tool, it is a **place**, and this bar already says that places live
 * on the left: `‹ MODS` sits there on every inner page. So it takes the empty third between the
 * mark and the centred filter tabs — about 350 px that has never held anything — and it is
 * labelled rather than an icon alone, for the same reason `‹ Mods` is: a bare glyph beside a VOID
 * wordmark reads as decoration.
 *
 * Only on the grid bar. Inside a mod's page the left is the way back out, which is one meaning
 * per side; the grid is where every open lands (`applyMenu` resets the route), so this is on
 * screen every single time the menu is opened.
 *
 * The Settings row and the ⌘K command both stay. They cost nothing, and someone who learned one
 * of them should not lose it.
 */
function HudLayoutControl() {
  const setRoute = useVoidStore((s) => s.setRoute);
  return (
    <button
      type="button"
      className="obar__dest"
      onClick={() => setRoute({ name: 'hud-editor' })}
    >
      <MoveGlyph />
      <span>HUD layout</span>
    </button>
  );
}

/**
 * The gear — Settings.
 *
 * **It is what the profile chip was.** The chip carried an avatar, a name and an account kind:
 * the widest object in a bar the user has called crowded three times, spending that width on
 * identity the player already has and cannot change from inside a match. In the launcher the
 * same chip earns its slot because it is the way into Settings; here it led to a Dashboard that
 * was mostly regions with no data behind them.
 *
 * A gear leads somewhere with something in it — the menu key, the UI scale, the watermark and
 * the HUD layout, four controls that all do something — and it is 24px wide instead of 116.
 *
 * Left of the search deliberately: the right-hand cluster now reads **settings, search, close**,
 * and search sits directly beside close because that is where the user asked for it. Which puts
 * the three in ascending order of consequence, left to right, which is the order they should be
 * in anyway.
 */
function SettingsControl() {
  const setRoute = useVoidStore((s) => s.setRoute);
  const open = useVoidStore((s) => s.route.name === 'settings');
  return (
    <button
      type="button"
      className={cx('obtn', open && 'obtn--on')}
      aria-label="Settings"
      aria-pressed={open}
      onClick={() => setRoute(open ? { name: 'mods' } : { name: 'settings' })}
    >
      <SettingsGlyph />
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* The status line under the content                                          */
/* -------------------------------------------------------------------------- */

function StatusLine({
  shown,
  total,
  enabled,
  layout,
}: {
  shown: number;
  total: number;
  enabled: number;
  layout: 'grid' | 'list';
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
  // No third form. There used to be one — the line clipped to the grid's own column, counting
  // only the tiles the properties panel was not standing on — and it went with the panel. The
  // grid always shows everything the filter matched now, so the count has nothing to correct
  // for. `shown` still differs from `total` under a filter, which is what the list form says.
  return (
    <div className="ostatus">
      <span className="ostatus__left">{`${enabled} of ${total} enabled`}</span>
      <span className="ostatus__right">Changes apply immediately</span>
    </div>
  );
}

/* -------------------------------------------------------------------------- */

/**
 * The shell, and whichever of its two bodies the route asks for.
 *
 * One component owns both because the panel box is one box: `solveGrid` sizes it from the
 * registry and the window, and the mod page is laid out inside whatever that comes to. A
 * separate top-level screen would have to solve the same geometry a second time and could
 * disagree with it — and the moment it did, navigating would resize the window under the
 * pointer, which is the thing the old contraction was trying not to do.
 */
export function ModsScreen() {
  const search = useVoidStore((s) => s.modSearch);
  const filter = useVoidStore((s) => s.modFilter);
  const setFilter = useVoidStore((s) => s.setModFilter);
  const selected = useVoidStore((s) => s.selectedMod);
  const selectMod = useVoidStore((s) => s.selectMod);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const layout = useVoidStore((s) => s.layout);
  const enabled = useVoidStore((s) => modsOnCount(s.loadout));
  // The route, not `selectedMod`: what is on screen is decided by one value, and the id the
  // page draws travels with it (`store.ts`, {@link Route}).
  const page = useVoidStore((s) => (s.route.name === 'mod' ? s.route.id : null));
  const settings = useVoidStore((s) => s.route.name === 'settings');
  // Every route but the grid is a place you went *from* the grid, so every one of them wears the
  // context bar and offers the same way back. One shape, not one per destination.
  const inner = page !== null || settings;

  // The grid is shaped by the *registry's* count and the window, never by the filter: `Visual`
  // matches two mods, and tiles that grew to a third of the window and back every time a tab
  // was clicked would be unusable. A filter leaves space in the grid rather than resizing it.
  //
  // It is solved on the page too, and deliberately: these are the panel's lengths, not the
  // grid's, and holding them still across the navigation is what keeps the box from moving.
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
      // Enter still toggles rather than opening, and it is worth saying why now that the
      // arrows no longer open anything either. The keyboard's job on the grid is the grid's
      // job — walk the tiles, flip the ones you came for — and the page is one click away for
      // the one you want to tune. Making Enter navigate would leave the keyboard with no way
      // to toggle at all without tabbing into a switch.
      if (e.key === 'Enter' && ids.includes(selected)) {
        e.preventDefault();
        toggleMod(selected, !isModOn(useVoidStore.getState().loadout, selected));
      }
    },
    [ids, selected, selectMod, toggleMod, shape.columns],
  );

  return (
    // The panel's whole geometry is solved here and handed to `overlay.css` as lengths (see
    // `solveGrid`); nothing below re-derives it, and nothing about it changes with the route.
    <div
      ref={panelRef}
      className={cx(
        'overlay',
        `overlay--${layout}`,
        inner && 'overlay--page',
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
        } as React.CSSProperties
      }
      onKeyDown={onKeyDown}
    >
      {/* Two bars, one shell. Inside a mod the filter tabs and the layout switcher are
          controls for a grid that is not on screen — a tab press would silently re-filter the
          thing you are about to come back to — so the bar becomes a context bar: where you
          came from, and the two things that are true everywhere (search, close). It is also
          the calmest this bar has ever been, which is worth something on its own. */}
      {!inner ? (
        <div className="obar">
          <VoidMark />
          {/* The one destination this bar offers besides the grid itself. Left of the centred
              nav and nowhere near the tool cluster — see `HudLayoutControl`. */}
          <HudLayoutControl />
          {/* One centred group: which mods, and how they are drawn. Both answer a question
              about the same thing, so they travel together — see `.obar__view`, which is also
              where the note on why the bar was re-grouped rather than re-sized lives. */}
          <div className="obar__view">
            <FilterTabs
              className="obar__nav"
              tabs={FILTER_TABS}
              value={filter}
              onChange={setFilter}
              label="Mod filter"
            />
            <LayoutControl />
          </div>
          {/* Settings, search, close — and search is beside close because the user asked for
              it there. Nothing else is in this cluster: what used to make it four was a profile
              chip, and it is gone (see `SettingsControl`). */}
          <div className="obar__tools">
            <SettingsControl />
            <SearchControl />
            {/* From the grid, Escape closes the menu — so the cap belongs here, and only here. */}
            <button
              type="button"
              className="obtn obtn--close obtn--esc"
              aria-label="Close"
              aria-keyshortcuts="Escape"
              onClick={closeMenu}
            >
              <CloseGlyph />
              <EscCap />
            </button>
          </div>
        </div>
      ) : (
        <div className="obar obar--page">
          <VoidMark />
          <BackControl />
          {/* The same three, in the same order, on every inner page. `SettingsControl` marks
              itself as on when the Settings page is the one you are looking at, and pressing it
              there goes back to the grid — a control that points at the page you are on is a
              control that does nothing, and this is the cheapest way for it not to be that. */}
          <div className="obar__tools">
            <SettingsControl />
            <SearchControl />
            {/* No cap: from a page Escape goes back, and the cap is on `‹ Mods` where it does. */}
            <button
              type="button"
              className="obtn obtn--close"
              aria-label="Close"
              onClick={closeMenu}
            >
              <CloseGlyph />
            </button>
          </div>
        </div>
      )}

      {/* The dotted cell rule under the bar, full content width. */}
      <div className="orule" aria-hidden="true" />

      <div className={cx('mods-body', inner ? 'mods-body--page' : `mods-body--${layout}`)}>
        {settings ? (
          <SettingsScreen />
        ) : page !== null ? (
          <ModPage id={page} />
        ) : (
          <ModsView ids={ids} rows={rows} layout={layout} selected={selected} search={search} />
        )}
      </div>

      {/* The mod page carries its own foot — `Reset to default` and `Done` — where the grid
          carries a status line, so only one of the two is ever mounted. */}
      {!inner ? (
        <StatusLine
          shown={ids.length}
          total={MOD_ORDER.length}
          enabled={enabled}
          layout={layout}
        />
      ) : null}

      <div className="ohint">
        {settings ? SETTINGS_HINT : page !== null ? MODS_HINT_PAGE : MODS_HINT_GRID}
      </div>
    </div>
  );
}

/**
 * The items — the grid or the list — and the half of the navigation that belongs to them.
 *
 * Its own component so that it **unmounts** when a mod's page takes the panel, and mounts fresh
 * when you come back. Keeping it mounted underneath was the other option and it is the one this
 * codebase has learnt not to take: a hidden subtree that still paints is exactly the warm-up
 * ghost (`overlay.css`, `.menu-layer--hidden`), and a grid of thirteen tiles left laid out
 * behind a page is thirteen tiles the engine can be asked to repaint for nothing.
 *
 * The remount is also what makes the entrance work without a timer: `--enter` is on from the
 * first frame and comes off on the animation's own `animationend`, so the class is never
 * standing on an element that is not animating.
 */
function ModsView({
  ids,
  rows,
  layout,
  selected,
  search,
}: {
  ids: ModId[];
  rows: ModId[][];
  layout: 'grid' | 'list';
  selected: ModId;
  search: string;
}): React.ReactElement {
  const [entering, setEntering] = useState(true);
  return (
    <div
      className={cx('mods-view', entering && 'mods-view--enter')}
      onAnimationEnd={(event) => {
        if (event.target === event.currentTarget && event.animationName === 'void-page-in') {
          setEntering(false);
        }
      }}
    >
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
  );
}
