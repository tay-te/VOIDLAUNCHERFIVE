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
 * That matters beyond looks. The panel is **one fixed, wide, centred size** and
 * never resizes; what changes is how the space inside it is divided. The frames
 * hold twenty-four mods in eight columns and the registry holds twelve, so a grid
 * declared as three rows left four columns filling 674 of 1277 and half the panel
 * empty. The grid is now shaped by the registry instead — two rows of six, tiles
 * sized from the panel — and it fills the panel edge to edge. See `overlay.css`,
 * "the shell".
 *
 * There is no screen title and no search field. Both were removed in the design
 * work ("Remove the mods header. Center the navbar over the top of the window.
 * Remove the search bar."): the tabs say where you are, and ⌘K already searches —
 * a second, always-visible field for twelve items was chrome for its own sake.
 * `modSearch` survives in the store because the palette writes it.
 *
 * ## The two controls, and why the grid fills column-major
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
 * ## At twenty-four mods
 *
 * The registry is twelve and generated from the schema, so this lays out for twelve
 * — but nothing here is written as twelve. {@link GRID_ROWS} is the fewest rows that
 * keep the registry inside {@link GRID_COLUMNS}, and the tile is sized from the
 * panel and the column count, so twenty-four mods give **three rows of eight** —
 * exactly what the frames draw — with 143-wide tiles in the same 1278-wide panel.
 * The panel gets taller (three rows of a 179-tall tile plus the chrome is 690, still
 * inside the 820 CSS the in-game canvas gives) and nothing else changes: the grid
 * still fills it, and opening the properties still contracts it to three columns.
 */

import { useCallback, useMemo } from 'react';
import { isModOn, modsOnCount, useModSettings, useVoidStore } from '@/store/store';
import { FILTER_TABS, MOD_CATEGORY, MOD_ORDER, hueStyle, modLabel } from '@/registry';
import { MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { FilterTabs, Toggle, cx } from '@/ui';
import { CloseGlyph, GridGlyph, InspectorGlyph, ListGlyph, VoidMark } from './cell-art';
import { TilePreview } from './TilePreview';
import { ModInspector } from './ModPane';
import { ModList } from './ModList';
import { keybindLabel } from './settings-format';

/** The widest the grid ever gets, in columns. The frames draw eight (`289:1611`). */
export const GRID_COLUMNS = 8;

/**
 * Rows in a grid column — derived, not declared.
 *
 * The panel is a fixed width and the grid has to fill it, so the shape of the grid is decided
 * by how many mods there are: the fewest rows that keep the whole registry inside
 * {@link GRID_COLUMNS}. Twelve mods give two rows of six; twenty-four give three rows of eight,
 * which is exactly what the frames draw. Declaring `3` here is what left the registry's twelve
 * as four columns in a panel wide enough for eight.
 */
export const GRID_ROWS = Math.max(1, Math.ceil(MOD_ORDER.length / GRID_COLUMNS));

/** Columns that stay visible while the properties are open. */
export const OPEN_COLUMNS = 3;

/**
 * How many columns the grid is built to hold — the registry's own count, not the filtered one.
 *
 * The tile is sized from the panel and this number (`overlay.css`, "the shell"), so it decides
 * how big a tile is. It deliberately ignores the current filter: `Visual` matches two mods, and
 * tiles that grew to a third of the window and back every time a tab was clicked would be
 * unusable. The grid is laid out for the whole registry and a filter simply leaves space in it.
 */
export const PANEL_COLUMNS = Math.min(
  GRID_COLUMNS,
  Math.max(1, Math.ceil(MOD_ORDER.length / GRID_ROWS)),
);

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
 * The ids split into columns, filled top-to-bottom.
 *
 * Column-major, so contracting the grid takes columns off the right rather than
 * re-flowing every tile into a new position: column 1 holds the same mods whether
 * three columns are showing or six.
 */
export function gridColumns(ids: ModId[]): ModId[][] {
  const columns: ModId[][] = [];
  for (let i = 0; i < ids.length && columns.length < GRID_COLUMNS; i += GRID_ROWS) {
    columns.push(ids.slice(i, i + GRID_ROWS));
  }
  return columns;
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
  total,
  enabled,
  layout,
  open,
  hidden,
}: {
  shown: number;
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
        <span className="ostatus__left">
          {`Showing ${Math.min(shown, OPEN_COLUMNS * GRID_ROWS)} of ${total}`}
        </span>
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

  const ids = useMemo(() => visibleMods(filter, search), [filter, search]);
  const columns = useMemo(() => (layout === 'grid' ? gridColumns(ids) : []), [ids, layout]);
  const shift = useMemo(
    () =>
      firstVisibleColumn(
        columns.length,
        columns.findIndex((column) => column.includes(selected)),
        inspector === 'open',
      ),
    [columns, selected, inspector],
  );

  const onKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      // The grid reads down a column and then across, so Down/Up walk within a
      // column and Left/Right jump a whole column. The list is one column of rows.
      const across = useVoidStore.getState().layout === 'grid' ? GRID_ROWS : 0;
      const delta =
        e.key === 'ArrowDown'
          ? 1
          : e.key === 'ArrowUp'
            ? -1
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
    [ids, selected, selectMod, toggleMod],
  );

  const open = inspector === 'open';

  return (
    // The panel is one fixed size; the tile is sized from it and the registry's column count
    // (overlay.css, "the shell"). The state classes are still on the shell because the status
    // line and the grid clip both read them, but nothing here changes the panel's own box.
    <div
      className={cx(
        'overlay',
        `overlay--${layout}`,
        open ? 'overlay--open' : 'overlay--closed',
      )}
      style={
        {
          ['--panel-cols' as string]: `${PANEL_COLUMNS}`,
          ['--panel-rows' as string]: `${GRID_ROWS}`,
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
              {columns.map((column, index) => (
                <div key={index} className="mods-col" data-column={index}>
                  {column.map((id) => (
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
        total={MOD_ORDER.length}
        enabled={enabled}
        layout={layout}
        open={open}
        hidden={layout === 'grid' && columns.length > OPEN_COLUMNS}
      />

      <div className="ohint">{open ? MODS_HINT_OPEN : MODS_HINT_GRID}</div>
    </div>
  );
}
