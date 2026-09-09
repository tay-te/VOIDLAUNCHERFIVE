/**
 * A render smoke test per screen, against the real `createFakeVoid()`.
 *
 * There are no devtools in game, so "it mounted and drew the frame's copy" is
 * the cheapest signal that a screen has not silently broken. Each case asserts
 * the verbatim text the frame carries — footer hints included, because that copy
 * is part of the design contract.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';

/**
 * The frames' footer hints are spaced with runs of three spaces, and
 * testing-library collapses whitespace by default. Assert the copy verbatim:
 * the spacing is part of the design contract.
 */
const verbatim = { collapseWhitespace: false as const, trim: false as const };

/** Store writes from outside a React event have to be flushed like React ones. */
function set(mutate: () => void) {
  act(() => {
    mutate();
  });
}
import { connectBridge, keepsEscape } from '@/bridge/connect';
import { isModOn, resetDerivedState, useVoidStore } from '@/store/store';
import { App } from '@/App';
import { MODS_HINT_GRID, MODS_HINT_PAGE, gridRows, solveGrid } from '@/menu/ModsScreen';
import { MOD_ORDER } from '@/registry';
import { IN_GAME_VIEW } from './setup';
import { anchorShortLabel, modProperties, propertyStructure } from '@/menu/ModSettingsScreen';
import { isEscape } from '@/menu/keys';
import { LOADOUTS_FOOTER } from '@/menu/LoadoutsScreen';
import { PARTY_FOOTER } from '@/menu/PartyScreen';
import { EDITOR_HINT, editorHint } from '@/menu/HudEditorScreen';
import { DEFAULT_HUD } from '@/store/hud-geometry';
import { HUD_MOD_IDS } from '@void/protocol';
import { PALETTE_HINT } from '@/palette/QuickPalette';

/**
 * A pointer event jsdom will actually carry coordinates on.
 *
 * jsdom has no `PointerEvent`, and testing-library's `fireEvent.pointerDown` falls back to a
 * bare `Event` — which silently drops `clientX`/`clientY`, so a drag test written the obvious way
 * measures a gesture from `undefined` and asserts `NaN`. A `MouseEvent` with the pointer event's
 * type is what React's synthetic system listens for anyway, and it carries the coordinates.
 */
function pointer(type: 'pointerdown' | 'pointermove' | 'pointerup', x: number, y: number) {
  return new MouseEvent(type, { bubbles: true, clientX: x, clientY: y, button: 0 });
}

/** A fixed `getBoundingClientRect`, for the geometry jsdom has no layout to produce. */
function stubRect(el: HTMLElement, left: number, top: number, width: number, height: number) {
  vi.spyOn(el, 'getBoundingClientRect').mockImplementation(
    () =>
      ({
        left,
        top,
        width,
        height,
        right: left + width,
        bottom: top + height,
        x: left,
        y: top,
        toJSON: () => ({}),
      }) as DOMRect,
  );
}

let dispose: () => void;

beforeEach(() => {
  resetDerivedState();
  // The store is a module singleton; reset the UI slice so cases do not leak
  // a filter or a route into each other.
  useVoidStore.setState({
    route: { name: 'mods' },
    selectedMod: 'keystrokes',
    paletteOpen: false,
    modSearch: '',
    modFilter: 'all',
    layout: 'grid',
    session: null,
    editorTarget: 'keystrokes',
    editorSnap: true,
    editorGrid: false,
  });
  ({ dispose } = connectBridge({ forceFake: true, runFakeClock: false }));
});

afterEach(() => {
  cleanup();
  dispose();
});

describe('HUD layer', () => {
  it('mounts with the menu closed and draws the loadout’s widgets', () => {
    set(() => useVoidStore.getState().applyMenu(false));
    set(() => useVoidStore.getState().applyTick({ fps: 142, ping: 42 }));
    const { container } = render(<App />);
    const hud = container.querySelector('.hud-layer');
    expect(hud).not.toBeNull();
    // Scoped to the HUD. The menu layer is mounted hidden during the warm-up (App.tsx), and the
    // FPS mod's tile preview shows the same live number, so an unscoped query matches twice.
    expect(within(hud as HTMLElement).getByText('142')).toBeTruthy();
    expect(within(hud as HTMLElement).getByText('fps')).toBeTruthy();
  });

  it('warms the menu up hidden, so the first open is not the engine’s first look at it', () => {
    set(() => useVoidStore.getState().applyMenu(false));
    const { container } = render(<App />);
    const menu = container.querySelector('.menu-layer');
    expect(menu).not.toBeNull();
    expect(menu!.className).toContain('menu-layer--hidden');
    expect(menu!.getAttribute('aria-hidden')).toBe('true');
  });

  it('ends the warm-up on a timer even when no loadout ever arrives', () => {
    vi.useFakeTimers();
    try {
      set(() => useVoidStore.getState().applyMenu(false));
      // A client with no launcher attached: `onInit` is the only thing that pushes a loadout,
      // so this stays null for the life of the process. The warm-up used to wait for it before
      // starting its clock, which meant the hidden layer never came down — and the one frame it
      // painted stayed welded over the game for as long as the client ran.
      set(() => useVoidStore.setState({ loadout: null }));
      const { container } = render(<App />);
      expect(container.querySelector('.menu-layer')!.className).toContain('menu-layer--hidden');

      act(() => {
        vi.advanceTimersByTime(1500);
      });
      expect(container.querySelector('.menu-layer')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('fades the menu in on open and out on close, and then lets the tree go', () => {
    vi.useFakeTimers();
    try {
      set(() => useVoidStore.getState().applyMenu(true));
      const { container } = render(<App />);
      const layer = () => container.querySelector('.menu-layer');
      expect(layer()!.className).toContain('menu-layer--entering');
      expect(layer()!.className).not.toContain('menu-layer--hidden');

      set(() => useVoidStore.getState().applyMenu(false));
      // Still mounted and still drawn: the close animation needs a tree to run on, and a layer
      // that vanished on the frame it was told to would leave the last painted frame holding a
      // menu the host has already stopped compositing over.
      expect(layer()).not.toBeNull();
      expect(layer()!.className).toContain('menu-layer--exiting');
      expect(layer()!.getAttribute('aria-hidden')).toBe('true');

      // …and then it goes. Not `visibility: hidden` forever — that is what welded the
      // properties pane over the game (App.tsx, useMenuWarmup).
      act(() => {
        vi.advanceTimersByTime(400);
      });
      expect(container.querySelector('.menu-layer')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('is not the editor layer, so its widgets stay inert — §6.3', () => {
    const { container } = render(<App />);
    expect(container.querySelector('.hud-layer')).not.toBeNull();
    expect(container.querySelector('.hud-layer--editor')).toBeNull();
  });

  it('draws only the HUD mods the loadout both enables and places', () => {
    const { container } = render(<App />);
    const drawn = [...container.querySelectorAll('[data-hud-id]')].map((el) =>
      el.getAttribute('data-hud-id'),
    );
    const loadout = useVoidStore.getState().loadout!;
    for (const id of drawn) {
      expect(loadout.hud.some((h) => h.id === id)).toBe(true);
    }
  });
});

describe('Mods screen — the grid, and the page one click away', () => {
  beforeEach(() => {
    set(() => useVoidStore.getState().applyMenu(true));
  });

  it('is the full-bleed shell: mark, centred tabs, tools, hint — no title, no search', () => {
    const { container } = render(<App />);
    // The menu *is* the window. No panel, and nothing wrapping it in one.
    expect(container.querySelector('.overlay')).not.toBeNull();
    expect(container.querySelector('.v-panel')).toBeNull();
    expect(container.querySelector('.panel-wrap')).toBeNull();
    // The two elements the design work removed by name: "Remove the mods header
    // … Remove the search bar." ⌘K is the search.
    expect(screen.queryByRole('heading', { name: 'Mods' })).toBeNull();
    expect(screen.queryByRole('searchbox')).toBeNull();
    expect(container.querySelector('.v-searchbar')).toBeNull();
    // What the frame does have.
    // Scoped to the bar's own wordmark: the watermark mod draws the same four letters on
    // the HUD behind the panel, which is the mark doing its job rather than a duplicate.
    expect(container.querySelector('.vmark__word')!.textContent).toBe('VOID');
    for (const label of ['All', 'HUD', 'PvP', 'Visual', 'Utility']) {
      expect(screen.getByRole('tab', { name: label })).toBeTruthy();
    }
    expect(screen.getByLabelText('Close')).toBeTruthy();
    expect(screen.getByText(MODS_HINT_GRID, verbatim)).toBeTruthy();
  });

  it('gives every tile a data preview rather than an icon', () => {
    const { container } = render(<App />);
    // Frame `289:1611`: `142 / FPS`, the keycap cluster, `6.2 / CPS`, `X Y Z`.
    for (const id of ['fps', 'keystrokes', 'cps', 'coordinates']) {
      const tile = container.querySelector(`.modcell[data-mod-id="${id}"]`)!;
      expect(tile.querySelector('.modcell__preview')).not.toBeNull();
      expect(tile.querySelector('.modcell__preview')!.childElementCount).toBeGreaterThan(0);
    }
    // Scoped to the tiles: the HUD layer behind draws its own `FPS` / `CPS` chips.
    const unit = (id: string) =>
      container.querySelector(`.modcell[data-mod-id="${id}"] .tart__unit`)!.textContent;
    expect(unit('fps')).toBe('FPS');
    expect(unit('cps')).toBe('CPS');
    // The keybind is chipped into the preview only for a mod that has one bound.
    // Zoom is on `C` in the fixture; FPS display takes no keybind at all, and
    // Keystrokes takes one but has none set — neither gets a chip, because the
    // absence is the information (§1).
    expect(
      container.querySelector('.modcell[data-mod-id="zoom"] .modcell__kbd')!.textContent,
    ).toBe('C');
    expect(container.querySelector('.modcell[data-mod-id="fps"] .modcell__kbd')).toBeNull();
  });

  it('keeps the grid/list switcher and has no properties toggle left to offer', () => {
    render(<App />);
    // The layout control survives untouched: how you like to read the list is still yours.
    expect(screen.getByRole('radio', { name: 'Grid' })).toBeTruthy();
    expect(screen.getByRole('radio', { name: 'List' })).toBeTruthy();
    // The inspector toggle is gone with the inspector. A button that shows and hides a panel
    // that does not exist is the "new mode bolted beside the old" failure this change is
    // specifically not allowed to be.
    expect(screen.queryByRole('button', { name: 'Properties' })).toBeNull();
  });

  it('draws a tile per mod, in the shape that fills the panel', () => {
    const { container } = render(<App />);
    expect(container.querySelectorAll('[data-mod-id]')).toHaveLength(MOD_ORDER.length);
    expect(screen.getByText('FPS display')).toBeTruthy();
    expect(screen.getByText('Toggle sprint')).toBeTruthy();

    // The shape is solved from the registry's count, so the assertion is the *rule* rather than
    // the numbers it happened to produce. It read `2 rows of 7 and 6` at thirteen mods and had
    // to be rewritten at fourteen — a restatement of `solveGrid`'s output, which is the thing
    // under test, so it could only ever be a second copy of it.
    //
    // What actually has to hold: the fewest columns whose rows still fit, filled row-major, so
    // every row is full except possibly the last. A short last row is what row-major allows and
    // column-major could not (see `solveGrid`).
    const solved = solveGrid(MOD_ORDER.length, IN_GAME_VIEW.width, IN_GAME_VIEW.height);
    const rows = [...container.querySelectorAll('.mods-row')];
    expect(rows).toHaveLength(solved.rows);
    expect(rows.map((row) => row.querySelectorAll('[data-mod-id]').length)).toEqual(
      gridRows([...MOD_ORDER], solved.columns).map((row) => row.length),
    );
    for (const row of rows.slice(0, -1)) {
      expect(row.querySelectorAll('[data-mod-id]')).toHaveLength(solved.columns);
    }
  });

  it('comes back from a page as the same grid: same tiles, same rows, same order', () => {
    const { container } = render(<App />);
    const shape = () =>
      [...container.querySelectorAll('.mods-row')].map((row) =>
        [...row.querySelectorAll('[data-mod-id]')].map((el) => el.getAttribute('data-mod-id')),
      );
    const before = shape();
    // What the old contraction test was protecting: the grid must not rearrange itself around
    // the properties. It used to keep that by clipping rather than reflowing, at the price of
    // showing three columns of six. Now the grid simply never changes — it unmounts while a
    // page has the panel and comes back identical, laid out from the same solve.
    set(() => useVoidStore.getState().openMod('ping'));
    expect(container.querySelector('.mods-grid')).toBeNull();
    set(() => useVoidStore.getState().closeMod());
    expect(shape()).toEqual(before);
    expect(container.querySelectorAll('[data-mod-id]')).toHaveLength(MOD_ORDER.length);
  });

  it('shapes the grid from the registry, never from the filter', () => {
    const { container } = render(<App />);
    const overlay = () => container.querySelector('.overlay') as HTMLElement;
    expect(overlay().className).toContain('overlay--grid');
    // Twenty-nine mods: seven across, five down — the shape that fills the panel, solved from
    // the registry's count and the window and handed to the CSS as lengths. Six across at
    // twelve, seven when the watermark landed, eight at Wave 2's readouts, and seven again now
    // that the cap is seven: past twenty-one no column count fits, so the width stopped being
    // "how do I avoid a scrollbar" and became "how big is the tile in the rows you can see".
    // Every one of those moves is `solveGrid` doing its job rather than a layout that had to be
    // re-guessed, which is why this is rewritten each time rather than derived away.
    const solved = solveGrid(MOD_ORDER.length, IN_GAME_VIEW.width, IN_GAME_VIEW.height);
    expect(solved.columns).toBe(7);
    expect(solved.rows).toBe(5);
    expect(overlay().style.getPropertyValue('--panel-cols')).toBe('7');
    expect(overlay().style.getPropertyValue('--panel-rows')).toBe('5');
    expect(overlay().style.getPropertyValue('--panel-w')).toBe(`${solved.panelW}px`);
    expect(overlay().style.getPropertyValue('--panel-h')).toBe(`${solved.panelH}px`);
    expect(overlay().style.getPropertyValue('--tile-w')).toBe(`${solved.tileW}px`);
    // **Twenty-two is where it flips now.** Three full rows of seven is exactly the panel; the
    // fourth overflows it, so a gutter is held back for the scrollbar and the grid gains
    // `overlay--scrolls`. Twenty-nine is well past that, and the panel is deliberately sized to
    // show three of the five rows rather than to hold all of them: the panel is a fixed box
    // rather than the size of the registry, and "does it scroll yet" is the question a fixed box
    // exists to answer. It is answered here, once, in a test with the count in it.
    expect(solved.scrolls).toBe(true);
    expect(overlay().style.getPropertyValue('--grid-gutter')).not.toBe('0px');
    expect(overlay().className).toContain('overlay--scrolls');

    // The panel's box is the *panel's*, not the grid's: it is solved the same way and written
    // to the same custom properties on a mod's page, so navigating moves no length on the
    // shell. This is what the old contraction was reaching for and could only half keep.
    const lengths = () =>
      ['--panel-w', '--panel-h', '--tile-w', '--panel-cols'].map((name) =>
        overlay().style.getPropertyValue(name),
      );
    const before = lengths();
    set(() => useVoidStore.getState().openMod('crosshair'));
    expect(overlay().className).toContain('overlay--page');
    expect(lengths()).toEqual(before);
    set(() => useVoidStore.getState().closeMod());
    expect(overlay().className).not.toContain('overlay--page');

    set(() => useVoidStore.getState().setLayout('list'));
    expect(overlay().className).toContain('overlay--list');

    // A filter matching two mods must not resize the tiles in the window it is filtering.
    set(() => useVoidStore.getState().setLayout('grid'));
    set(() => useVoidStore.getState().setModFilter('VISUAL'));
    expect(container.querySelectorAll('.mods-row')).toHaveLength(1);
    expect(overlay().style.getPropertyValue('--panel-cols')).toBe('7');
    expect(overlay().style.getPropertyValue('--tile-w')).toBe(`${solved.tileW}px`);
  });

  it('walks the grid the way it reads: Left/Right within a row, Up/Down a whole row', () => {
    // Row-major, so the arrow keys had to change with the fill order. Twenty-nine mods are seven
    // across: `fps` is index 0, `toggle_sneak` index 6 (end of row one), `old_animations` index 7
    // (start of row two), `watermark` index 28 (the last, alone on a fifth row).
    const { container } = render(<App />);
    const overlay = container.querySelector('.overlay') as HTMLElement;
    const at = () => useVoidStore.getState().selectedMod;
    const press = (key: string) => fireEvent.keyDown(overlay, { key });

    set(() => useVoidStore.getState().selectMod('fps'));
    press('ArrowRight');
    expect(at()).toBe('memory');
    press('ArrowDown');
    expect(at()).toBe('old_input'); // index 1 + 7
    press('ArrowUp');
    expect(at()).toBe('memory');

    // A row boundary is a step, not a wall: the grid is one sequence laid out in rows.
    set(() => useVoidStore.getState().selectMod('old_animations'));
    press('ArrowRight');
    expect(at()).toBe('old_input');

    // Both ends clamp rather than wrapping.
    set(() => useVoidStore.getState().selectMod('fps'));
    press('ArrowLeft');
    expect(at()).toBe('fps');
    press('ArrowUp');
    expect(at()).toBe('fps');
    set(() => useVoidStore.getState().selectMod('watermark'));
    press('ArrowRight');
    expect(at()).toBe('watermark');
    press('ArrowDown');
    expect(at()).toBe('watermark');

    // The list is one column of rows: Down is the next mod, and Left/Right have nowhere to go.
    set(() => useVoidStore.getState().setLayout('list'));
    set(() => useVoidStore.getState().selectMod('fps'));
    press('ArrowDown');
    expect(at()).toBe('memory');
    press('ArrowRight');
    expect(at()).toBe('memory');
    press('ArrowUp');
    expect(at()).toBe('fps');
  });

  /**
   * The grid does not move, at all, for any selection.
   *
   * Two tests used to live here: one that the grid slid so the tile you clicked survived the
   * contraction, and one for `firstVisibleColumn`'s arithmetic. Both existed only because
   * selecting a mod narrowed the grid to three columns. Nothing narrows, so the property they
   * were protecting — the thing you clicked does not move out from under you — is now
   * structural, and this is what it looks like as an assertion.
   */
  it('never moves a tile in response to a selection', () => {
    const { container } = render(<App />);
    const overlay = container.querySelector('.overlay') as HTMLElement;
    const order = () =>
      [...container.querySelectorAll('[data-mod-id]')].map((el) => el.getAttribute('data-mod-id'));
    const before = order();
    // `ping` is index 10 and `coordinates` index 11 — the far right of the grid, which is
    // exactly where the contraction used to have to slide from.
    for (const id of ['ping', 'coordinates', 'fps'] as const) {
      set(() => useVoidStore.getState().selectMod(id));
      expect(order()).toEqual(before);
    }
    // And no offset is written to the shell for the CSS to slide on.
    expect(overlay.style.getPropertyValue('--grid-first')).toBe('');
  });

  it('filters by tab and by search in either layout', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setModFilter('VISUAL'));
    const ids = [...container.querySelectorAll('[data-mod-id]')].map((el) =>
      el.getAttribute('data-mod-id'),
    );
    // The watermark is Visual too — it is a mark drawn over the game, not a readout. `overlay`
    // joined them in Wave 4: it is the mod whose whole subject is what the game draws over you.
    expect(ids.sort()).toEqual(['crosshair', 'fullbright', 'overlay', 'watermark']);

    set(() => useVoidStore.getState().setModFilter('all'));
    set(() => useVoidStore.getState().setLayout('list'));
    set(() => useVoidStore.getState().setModSearch('keystro'));
    expect(container.querySelectorAll('[data-mod-id]')).toHaveLength(1);
  });

  it('gives the list a description column, and an em-dash where one does not apply', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setLayout('list'));
    // Scoped to the column header, which is also where the row's own cells repeat these
    // words. The list now keeps every column it was drawn with: `Position`, `Scale` and
    // `Category` used to be dropped whenever the properties panel was open beside it, and
    // nothing is beside it any more.
    const head = container.querySelector('.modlist__head') as HTMLElement;
    for (const column of ['Name', 'Description', 'Category', 'Keybind', 'Position', 'Scale']) {
      expect(within(head).getByText(column)).toBeTruthy();
    }

    const rows = (id: string) =>
      container.querySelector(`.modrow[data-mod-id="${id}"]`) as HTMLElement;
    // Crosshair is a gameplay mod with no keybind of its own: no key, no place on the HUD, no
    // scale. It replaces Fullbright here, which was the example until Fullbright gained a
    // `keybind` — a gameplay mod with a toggle key now fills that column like any HUD mod, and
    // an em-dash in it would be the wrong assertion rather than a failing one.
    const crosshair = rows('crosshair');
    expect(crosshair.querySelectorAll('.modrow__empty')).toHaveLength(3);
    // And the mod that moved: three empties became two, because the key is real.
    expect(rows('fullbright').querySelectorAll('.modrow__empty')).toHaveLength(2);
    expect(
      within(rows('fullbright')).getByText('Raises gamma so caves and shadows are fully lit.'),
    ).toBeTruthy();
    // Keystrokes has all four, so nothing is missing from its row.
    expect(rows('keystrokes').querySelectorAll('.modrow__empty')).toHaveLength(0);
  });

  it('prints the placement as the frames\u2019 two-letter tag, not the sentence form', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setLayout('list'));
    const cell = (id: string) =>
      container
        .querySelector(`.modrow[data-mod-id="${id}"] .modrow__pos`)!
        .textContent!.trim();
    // The column is 61px at this frame's proportions: `Bottom left` wraps out of a
    // row whose height is fixed, and `BL` is what the frame prints anyway.
    expect(cell('keystrokes')).toBe('BL');
    expect(cell('fps')).toBe('TL');
    // The long form survives where it is read rather than scanned.
    expect(anchorShortLabel('bottom-right')).toBe('BR');
    expect(anchorShortLabel('center')).toBe('C');
  });

  /**
   * The card's body and the card's switch do different things.
   *
   * jsdom can only check that the two handlers are wired to different acts — it dispatches to
   * whatever element the test names, so it can never see the failure that actually happens
   * here, which is one element sitting over another and swallowing its clicks. That has gone
   * wrong in both directions (`.modcell__select` used to cover the switch, so nothing could be
   * toggled at all) and it is checked in game. What this pins is the intent.
   */
  it('opens the mod from the card body, and only toggles from the card switch', () => {
    const { container } = render(<App />);
    const store = () => useVoidStore.getState();

    const before = isModOn(store().loadout, 'fullbright');
    fireEvent.click(screen.getByLabelText('Fullbright enabled'));
    // Flipped, and still on the grid.
    expect(isModOn(store().loadout, 'fullbright')).toBe(!before);
    expect(store().route).toEqual({ name: 'mods' });
    expect(container.querySelector('.mods-grid')).not.toBeNull();

    // The body navigates, and changes nothing.
    const flipped = isModOn(store().loadout, 'fullbright');
    fireEvent.click(screen.getByRole('button', { name: 'Fullbright' }));
    expect(store().route).toEqual({ name: 'mod', id: 'fullbright' });
    expect(store().selectedMod).toBe('fullbright');
    expect(isModOn(store().loadout, 'fullbright')).toBe(flipped);
    expect(container.querySelector('.modpage')).not.toBeNull();
    expect(container.querySelector('.mods-grid')).toBeNull();
  });

  it('gives the page a context bar and two ways back, and no way to filter a grid it is not showing', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().openMod('keystrokes'));

    // The nav tabs and the layout switcher are controls for a grid that is not on screen: a
    // tab press here would silently re-filter what you are about to come back to.
    expect(screen.queryByRole('tab', { name: 'Visual' })).toBeNull();
    expect(screen.queryByRole('radio', { name: 'List' })).toBeNull();
    // What is true on every screen stays.
    // Scoped to the bar's own wordmark: the watermark mod draws the same four letters on
    // the HUD behind the panel, which is the mark doing its job rather than a duplicate.
    expect(container.querySelector('.vmark__word')!.textContent).toBe('VOID');
    expect(screen.getByLabelText('Close')).toBeTruthy();
    expect(screen.getByLabelText('Search')).toBeTruthy();
    expect(screen.getByText(MODS_HINT_PAGE, verbatim)).toBeTruthy();

    // Two visible affordances, both of which land back on the grid unchanged. (Escape is
    // deliberately not a third — see `ModPage.tsx`: in game the page never receives it.)
    fireEvent.click(screen.getByRole('button', { name: /Mods/ }));
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    expect(container.querySelector('.mods-grid')).not.toBeNull();

    set(() => useVoidStore.getState().openMod('keystrokes'));
    fireEvent.click(screen.getByText('Done'));
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    expect(container.querySelector('.modpage')).toBeNull();
  });

  /**
   * Escape means "up one level", and three copies of that table have to agree.
   *
   * `keepsEscape()` is the copy Java polls (it decides whether the key is forwarded to the page
   * at all); `MenuLayer`'s handler is the copy that acts; the `esc` cap in the bar is the copy
   * the player reads. This checks the first two against each other at every level, because the
   * failure when they drift is silent in one direction and catastrophic in the other — Escape
   * closing the whole menu out of a properties page.
   */
  it('walks Escape up one level, and says so to the host', () => {
    const { container } = render(<App />);
    const layer = container.querySelector('.menu-layer') as HTMLElement;
    // `keyCode`, and no `key` at all — the shape the in-game engine actually delivers. Measured:
    // Escape arrives as `key: "Unidentified", code: "", which: 27`, so a handler that matched on
    // `key` alone was dead in the product while passing every test here. See `menu/keys.ts`.
    const esc = () => fireEvent.keyDown(window, { keyCode: 27 });

    // The grid: the page does not want it, so Java closes the menu. Nothing here to assert on
    // the page side beyond the answer it gives the host — the close is Java's.
    expect(keepsEscape()).toBe(false);

    // A mod's page: the page wants it, and it goes back to the grid rather than closing.
    set(() => useVoidStore.getState().openMod('zoom'));
    expect(keepsEscape()).toBe(true);
    set(() => esc());
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    expect(useVoidStore.getState().menuOpen).toBe(true);

    // The HUD editor: same rule, same level above.
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    expect(keepsEscape()).toBe(true);
    set(() => esc());
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });

    // The palette: it wants Escape too — its query field owns the keyboard, so the answer is
    // already true before the route is consulted — and it closes itself on its own handler,
    // which stops the event before this one sees it. The route is untouched either way.
    set(() => useVoidStore.getState().setPaletteOpen(true));
    expect(keepsEscape()).toBe(true);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    set(() => fireEvent.keyDown(input, { keyCode: 27 }));
    expect(useVoidStore.getState().paletteOpen).toBe(false);
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    expect(useVoidStore.getState().menuOpen).toBe(true);
    expect(layer).not.toBeNull();
  });

  it('puts the esc cap on the control Escape actually presses, and never on two', () => {
    const { container } = render(<App />);
    const caps = () => [...container.querySelectorAll('.okbd')].map((el) => el.textContent);

    // On the grid Escape closes, so the cap is on the close button.
    expect(caps()).toEqual(['esc']);
    expect(container.querySelector('.obtn--close .okbd')).not.toBeNull();
    expect(screen.getByLabelText('Close').getAttribute('aria-keyshortcuts')).toBe('Escape');

    // On a page Escape goes back, so the cap moves to `‹ Mods` — and the close, which Escape no
    // longer presses, loses both the cap and the shortcut it would have been claiming.
    set(() => useVoidStore.getState().openMod('zoom'));
    expect(caps()).toEqual(['esc']);
    expect(container.querySelector('.obar__back .okbd')).not.toBeNull();
    expect(container.querySelector('.obtn--close .okbd')).toBeNull();
    expect(screen.getByLabelText('Close').getAttribute('aria-keyshortcuts')).toBeNull();
  });

  /**
   * The bar's right-hand cluster, after the profile chip was removed.
   *
   * Two things are asserted because two things were asked for. The chip is **gone** — it was the
   * widest object in a bar the user has called crowded three times, and what it spent that width
   * on was identity that cannot be changed from inside a match. And search now sits **directly
   * beside close**, which is where the user put it.
   */
  it('leaves settings, search and close in the bar, in that order', () => {
    const { container } = render(<App />);
    // Even with a session pushed there is no chip anywhere: it is not conditional, it is gone.
    set(() =>
      useVoidStore.setState({
        session: { name: 'Notch', uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5', kind: 'microsoft' },
      }),
    );
    expect(container.querySelector('.oprofile')).toBeNull();

    const tools = container.querySelector('.obar__tools') as HTMLElement;
    const order = [...tools.children].map((el) => el.getAttribute('aria-label'));
    expect(order).toEqual(['Settings', 'Search', 'Close']);

    // The same three, in the same order, on an inner page — where the chip used to be
    // conditionally dropped, which was a fourth arrangement of the same corner.
    set(() => useVoidStore.getState().openMod('cps'));
    const pageTools = container.querySelector('.obar--page .obar__tools') as HTMLElement;
    expect([...pageTools.children].map((el) => el.getAttribute('aria-label'))).toEqual([
      'Settings',
      'Search',
      'Close',
    ]);
  });

  /**
   * The gear, and the page it opens.
   *
   * The chip's objection was that it did nothing; the answer is not a quieter chip but a control
   * with a destination, and every row of that destination writes something real.
   */
  it('opens Settings from the gear, and closes it from the same button', () => {
    const { container } = render(<App />);
    fireEvent.click(screen.getByLabelText('Settings'));
    expect(useVoidStore.getState().route).toEqual({ name: 'settings' });
    expect(container.querySelector('.oset')).not.toBeNull();

    // Marked as on while you are on it, and pressing it again is the way back — a control that
    // points at the page you are already on would be the chip's defect again.
    const gear = () => screen.getByLabelText('Settings');
    expect(gear().className).toContain('obtn--on');
    fireEvent.click(gear());
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
  });

  it('gives Settings the account the chip used to carry, read-only', () => {
    const { container } = render(<App />);
    set(() =>
      useVoidStore.setState({
        session: { name: 'Notch', uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5', kind: 'microsoft' },
      }),
    );
    set(() => useVoidStore.getState().setRoute({ name: 'settings' }));

    expect(container.querySelector('.oset__name')!.textContent).toBe('Notch');
    expect(container.querySelector('.oset__kind')!.textContent).toBe('Microsoft account');
    // Monochrome, per §1: no hue is derived from the uuid.
    expect(container.querySelector('.oset__initial')!.textContent).toBe('N');
    expect(container.querySelector('.oset__initial')!.getAttribute('style')).toBeNull();
    // Read, never pressed. The account is settled for the life of the process.
    expect(container.querySelector('.oset__player')!.querySelector('button')).toBeNull();
  });

  /**
   * Every row on the page writes something, which is the whole reason the gear replaced a chip.
   *
   * `ui_scale` and `menu_key` are the two that needed a contract change to be true at all — they
   * live in `LiveState`, and until `bridge.json` gained the `settings` channel and the
   * `setGlobal` call the page could neither read nor write either.
   */
  it('writes the globals it shows', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setRoute({ name: 'settings' }));

    // The head prints the live menu key, so a write that did not land is visible in the copy.
    expect(container.querySelector('.oset__meta')!.textContent).toContain('R-SHIFT OPENS');

    // A push from Java replaces the globals wholesale.
    set(() => useVoidStore.getState().applyGlobals({ menu_key: 'F1', ui_scale: 2 }));
    expect(useVoidStore.getState().globals).toEqual({
      menuKey: 'F1',
      uiScale: 2,
      theme: 'void-dark',
      // Absent from the push, so it keeps the factory value — a partial push must not blank a
      // setting, which is what the `?? current` in `applyGlobals` is for.
      hudEditorGrid: 4,
    });
    expect(container.querySelector('.oset__meta')!.textContent).toContain('F1 OPENS');
  });

  /**
   * `ui_scale` is plumbed and deliberately not offered.
   *
   * The store still tracks it and `setGlobal` still writes it, because the launcher does. What
   * must not come back is a *control* for it on this page: it resizes the surface it lives on,
   * and in game that ran away to the 3.0 clamp in fourteen seconds — see `SettingsScreen.tsx`.
   */
  it('offers no UI-scale control, while keeping the value live', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setRoute({ name: 'settings' }));
    expect(container.querySelector('.oset')!.textContent).not.toMatch(/scale/i);
    expect(container.querySelectorAll('.oset .meter')).toHaveLength(0);

    // …but the path is intact, so the launcher's write still lands.
    set(() => useVoidStore.getState().applyGlobals({ ui_scale: 1.5 }));
    expect(useVoidStore.getState().globals.uiScale).toBe(1.5);
  });

  /**
   * The bar's own way in.
   *
   * "We need a HUD editor" was a discoverability bug, not a missing feature: the editor already
   * existed and was reachable only from a row inside Settings and from the quick palette, which
   * is to say only by someone who already knew it was there. It is on the grid's bar now — the
   * screen every open lands on — and on the **left**, where this bar already puts places, rather
   * than as a fourth icon in a right-hand cluster of three that has been called crowded.
   */
  it('the mods bar carries a way straight into the HUD editor', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    const { container } = render(<App />);
    const dest = container.querySelector('.obar__dest') as HTMLElement;
    expect(dest).not.toBeNull();
    expect(dest.textContent).toContain('HUD layout');
    // From the sprite sheet, never inline SVG — ultralight-notes.md §7.
    expect(dest.querySelector('.oicon--move')).not.toBeNull();

    // Not in the tool cluster: that group is `settings · search · close` and stays three.
    const tools = container.querySelector('.obar__tools') as HTMLElement;
    expect(tools.querySelectorAll('button')).toHaveLength(3);
    expect(tools.querySelector('.obar__dest')).toBeNull();

    fireEvent.click(dest);
    expect(useVoidStore.getState().route).toEqual({ name: 'hud-editor' });
  });

  it('carries the watermark switch and the way to the layout editor', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setRoute({ name: 'settings' }));

    // The mod's own switch, not a second flag beside it.
    const before = isModOn(useVoidStore.getState().loadout, 'watermark');
    fireEvent.click(screen.getByLabelText('VOID watermark enabled'));
    expect(isModOn(useVoidStore.getState().loadout, 'watermark')).toBe(!before);
    // The preview says which of the two states it is in rather than disappearing.
    expect(container.querySelector('.oset__mark')!.className).toContain(
      before ? 'oset__mark--off' : 'oset__mark',
    );

    // The HUD editor has been reachable only from the quick palette, i.e. only by someone who
    // already knew it was there.
    fireEvent.click(screen.getByText('Open the layout editor'));
    expect(useVoidStore.getState().route).toEqual({ name: 'hud-editor' });
  });

  /** Escape from Settings is one level up, not out — the same rule every inner page follows. */
  it('treats Settings as one level above the grid', () => {
    render(<App />);
    set(() => useVoidStore.getState().setRoute({ name: 'settings' }));
    // Java asks this a frame ahead to decide whether to forward the key at all.
    expect(keepsEscape()).toBe(true);
    fireEvent.keyDown(window, { key: 'Unidentified', which: 27, keyCode: 27 });
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    expect(useVoidStore.getState().menuOpen).toBe(true);
  });

  it('does not close the whole menu on the way back', () => {
    render(<App />);
    set(() => useVoidStore.getState().openMod('zoom'));
    fireEvent.click(screen.getByText('Done'));
    // The overlay interrupts a live match; a step back must cost one step, not a reopen.
    expect(useVoidStore.getState().menuOpen).toBe(true);
  });
});

describe('The mod page — contract §8', () => {
  beforeEach(() => {
    set(() => useVoidStore.getState().applyMenu(true));
  });

  /** Every case here is one click into a mod, which is the only way the properties exist now. */
  const open = (id: Parameters<ReturnType<typeof useVoidStore.getState>['openMod']>[0]) =>
    set(() => useVoidStore.getState().openMod(id));

  it('never lists a spatial property — placement and size are on the preview', () => {
    render(<App />);
    open('keystrokes');
    // Frame `289:5523` captions the preview with what the two things on it do.
    expect(screen.getByText(/Click a slot to place/)).toBeTruthy();
    // `Scale` is the size property, so it is the drag handle, not a row.
    expect(screen.getByRole('button', { name: 'Scale' })).toBeTruthy();
    // …and placement is four labelled corner slots, printing the frame's own tags.
    const slot = screen.getByRole('button', { name: 'Bottom left' });
    expect(slot.textContent).toBe('BL');
    for (const property of modProperties('keystrokes', { scale: 1, opacity: 1, on: true })) {
      expect(property.key).not.toBe('scale');
    }
  });

  it('gives 5+ properties two captioned groups, and 2–4 a flat list with no labels', () => {
    const { container } = render(<App />);
    open('keystrokes');
    expect(container.querySelector('[data-structure="grouped"]')).not.toBeNull();
    // Two groups (§8), side by side beside the preview. They are captioned now, which the
    // inspector's two columns were not: `289:3024`'s argument for bare columns — the column is
    // the grouping — stops holding once the columns stand next to a page-sized preview, and
    // `289:5523`'s narrow panels caption theirs for exactly that reason.
    expect(container.querySelectorAll('.mprops__group')).toHaveLength(2);
    expect([...container.querySelectorAll('.mprops__cap')].map((el) => el.textContent)).toEqual([
      'Appearance',
      'Behaviour',
    ]);
    expect(screen.getByText('Show mouse buttons')).toBeTruthy();
    expect(screen.getByText('LMB and RMB under the arrows')).toBeTruthy();

    // 2–4 is one column and no caption: a group of one is the section label the flat
    // structure exists to avoid.
    //
    // A **gameplay** mod, and necessarily so now. This was `cps`, which had four; the shared HUD
    // chrome block gave every `kind: hud` mod four more, so all eight are grouped and the flat
    // branch belongs to the mods that have no chrome. That is the block working as intended
    // rather than a regression — §8's rule is "structure follows property count", and the count
    // genuinely moved — but it does mean this branch is covered by the gameplay mods alone.
    //
    // It was `toggle_sprint` until `mode` was removed for being provably a no-op, which left
    // that mod one property and moved it down a structure. `zoom`'s four carry the case now —
    // not `hitboxes`, which has six and is grouped.
    open('zoom');
    expect(container.querySelector('[data-structure="flat"]')).not.toBeNull();
    expect(container.querySelectorAll('.mprops__group')).toHaveLength(0);
    expect(container.querySelector('.mprops__cap')).toBeNull();
  });

  it('gives Toggle sprint the sentence structure now that only its keybind is left', () => {
    const { container } = render(<App />);
    open('toggle_sprint');
    // A shipped mod reaches §8's sentence structure again. The neighbouring case notes that
    // none did after Fullbright gained a toggle key, and that its coverage had to move to the
    // `?fake=` fixture; removing `toggle_sprint.mode` handed the shape back to a real mod.
    //
    // Worth asserting rather than routing around, because it is the *visible* half of that
    // removal: `mode` was a setting that could not mean anything (`KeyBinding.setKeyPressed`
    // writes the same field the sprint tests read, so `hold` had no implementation but "write
    // nothing"), and taking it out does not merely delete a row — it changes what the page is.
    expect(container.querySelector('[data-structure="sentence"]')).not.toBeNull();
    expect(container.querySelectorAll('.mprops__sentence')).toHaveLength(1);
    // §8's own rule, and the reason the shape exists: a list of one is not a list, so the one
    // setting is said rather than drawn as a labelled row.
    expect(container.querySelector('.mprops__sentence')?.textContent).toContain('Keybind');
  });

  it('gives Fullbright a flat list now that it has a toggle key as well as a gamma', () => {
    const { container } = render(<App />);
    open('fullbright');
    // **This was the sentence test.** Fullbright was the registry's only one-property mod, and
    // `gamma` alone is what the §8 sentence structure was demonstrated on. It gained
    // `keybind` — brightness is the mod you most want on a key — and two properties is a flat
    // list, by the same derived rule that gave it a sentence before.
    //
    // The sentence structure is not gone and is not untested: no *shipped* mod reaches it now,
    // so its coverage moved to `fake-mods-injection.test.tsx`, whose fixture exists precisely
    // to hold one of every §8 shape. Asserting it here on a mod that no longer has one
    // property would have meant either faking the count or deleting the case.
    expect(container.querySelector('[data-structure="flat"]')).not.toBeNull();
    expect(container.querySelectorAll('.mprop')).toHaveLength(2);
    expect(container.querySelector('.mprops__cap')).toBeNull();
  });

  it('draws the mod itself on every page, never a paragraph in an empty box', () => {
    const { container } = render(<App />);
    // The inspector showed the mod's description centred in the preview for every mod but
    // Keystrokes — at page size, a caption pretending to be a picture. Every page now draws
    // either the real HUD component or, for the four mods that draw into the world, a diagram
    // fed by the game's own numbers (`gameplay-previews.tsx`). Cell art stayed behind on the
    // grid tile, where "a small picture of what the mod draws" is the right object.
    //
    // `test/preview.test.tsx` is the gate that every setting of every one of them moves the
    // drawing; this case is the shape check.
    for (const [id, selector] of [
      ['fps', '.v-hudchip'],
      ['keystrokes', '.v-keystrokes'],
      ['cps', '.v-hudchip'],
      ['ping', '.v-pingband'],
      ['coordinates', '.v-coordschip'],
      ['armor_status', '.v-armorlist'],
      ['potion_effects', '.v-potionlist'],
      ['watermark', '.wmark'],
      ['crosshair', '.v-crosshair'],
      ['toggle_sprint', '.gprev'],
      ['fullbright', '.gprev'],
      ['hitboxes', '.gprev'],
      ['zoom', '.gprev'],
    ] as const) {
      open(id);
      const stage = container.querySelector('.preview__stage') as HTMLElement;
      expect(stage.querySelector('.preview__zoom'), id).not.toBeNull();
      expect(stage.querySelector(selector), id).not.toBeNull();
      expect(stage.querySelector('.preview__blurb'), id).toBeNull();
      // Cell art and the live preview are alternatives, never both.
      expect(stage.querySelector('.tart'), id).toBeNull();
    }
  });

  it('draws the four world mods with the page’s own diagram on the tile too', () => {
    // Two slots worn to different fractions, so a durability bar that ignored `damage` comes
    // back as four identical widths and this case says so.
    act(() => {
      useVoidStore.setState({
        armor: [
          { slot: 'helmet', item: 'diamond_helmet', damage: 132, max_damage: 363, count: 1 },
          { slot: 'boots', item: 'diamond_boots', damage: 88, max_damage: 429, count: 1 },
        ],
      });
    });
    const { container } = render(<App />);
    // WAS: "keeps the tile's cell art on the grid", pinning a 9px `.cart__cell` on the
    // Fullbright tile. That was a real invariant for the nine mods whose tile is a small
    // picture of a widget — and the wrong one for these four, which had no widget and so got a
    // 7 x 7 bitmap of a sun, a square and an arrow with `BRIGHT`, `HITBOX` and `SPRINT`
    // captioned underneath. Nine tiles showed what the mod draws and three showed an idea of
    // it, and the caption was the tell: a picture that needs its own name printed under it is
    // not working.
    //
    // The property that replaced it is stronger, because it is the one that stops the drift
    // coming back: the tile and the page draw **the same component**, at two densities
    // (`gameplay-previews.tsx`, `dense`). A tile and a page cannot disagree about what a mod
    // looks like if there is only one drawing.
    for (const id of ['fullbright', 'hitboxes', 'toggle_sprint', 'zoom'] as const) {
      const tile = container.querySelector(`.modcell[data-mod-id="${id}"]`) as HTMLElement;
      const art = tile.querySelector('.modcell__preview .gprev');
      expect(art, id).not.toBeNull();
      expect(art!.classList.contains('gprev--tile'), id).toBe(true);
      // Still `.tart`: the grid's own contract is that every tile root carries it
      // (`fake-mods-injection.test.tsx`), and a diagram at tile density is tile art.
      expect(art!.classList.contains('tart'), id).toBe(true);
      // No caption. The tile's name is printed 20px below by `ModsScreen`, and the old art
      // printed it twice.
      expect(tile.querySelector('.tart__unit--wide'), id).toBeNull();
      expect(tile.querySelector('.modcell__preview .cart'), id).toBeNull();
      // The page's *sentence* does not come with it — a tile has no room to be read to.
      expect(tile.querySelector('.gprev__reading'), id).toBeNull();
    }
    // But a row's axis label does come with it, and this is the correction worth pinning.
    //
    // The tile used to drop those labels, on a rule that banned "any text that is not a value".
    // That rule was written to kill `BRIGHT` and `SPRINT` captions and it did, but it was too
    // broad: `World` / `Seen` and `Key` / `Sprint` are the diagram's own axis, not the mod's name
    // repeated. Without them the gamma ramp and the sprint timeline are both two rows of seven
    // grey cells, indistinguishable from each other and from a rendering fault — and fullbright
    // ships *off*, so its two rows are identical by design, which made the honest "this mod is
    // doing nothing" look like breakage. A user asked why they looked like that.
    for (const [id, axis] of [
      ['fullbright', ['World', 'Seen']],
      ['toggle_sprint', ['Key', 'Sprint']],
    ] as const) {
      const tile = container.querySelector(`.modcell[data-mod-id="${id}"]`) as HTMLElement;
      const labels = [...tile.querySelectorAll('.gprev__rowlabel')].map((n) => n.textContent);
      expect(labels, id).toEqual([...axis]);
    }
    // The zoom tile keeps exactly one keycap — the grid draws the mod's keybind itself, so the
    // diagram's own `hold C` cap would be the second one on the same 145px square.
    const zoom = container.querySelector('.modcell[data-mod-id="zoom"]') as HTMLElement;
    expect(zoom.querySelectorAll('.modcell__kbd')).toHaveLength(1);
    expect(zoom.querySelector('.gprev__kbd')).toBeNull();
    // The armour tile draws the two parts of an `ArmorList` row that survive at tile size — a
    // swatch per slot and its durability — off the real payload, rather than the pixel-art arch
    // it was. Four slots always, and the fill is the fraction the widget fills.
    const armour = container.querySelector('.modcell[data-mod-id="armor_status"]') as HTMLElement;
    expect(armour.querySelectorAll('.tart__slot')).toHaveLength(4);
    expect(armour.querySelector('.cart')).toBeNull();
    const wear = [...armour.querySelectorAll<HTMLElement>('.tart__wearfill')].map(
      (el) => el.style.width,
    );
    expect(wear).toHaveLength(4);
    // Not all four the same: the fixture is deliberately worn unevenly, and a bar that ignored
    // `damage` would come back as four identical widths — which is what the old art did.
    expect(new Set(wear).size).toBeGreaterThan(1);
    // No tile is *drawn as a bitmap* any more. The one `.cart` left on the grid is the
    // watermark's, and it is not an exception: that tile draws the real `Watermark`, whose
    // ring genuinely is a 7 x 7 cell mark in the game as well. `CellArt` stays the right
    // primitive for a mark; it stopped being how a preview is made.
    const bitmapped = [...container.querySelectorAll('.modcell')].filter(
      (tile) => tile.querySelector('.modcell__preview .cart') !== null,
    );
    expect(bitmapped.map((tile) => tile.getAttribute('data-mod-id'))).toEqual(['watermark']);
  });

  it('never offers tabs', () => {
    const { container } = render(<App />);
    for (const id of ['keystrokes', 'cps', 'fullbright', 'crosshair'] as const) {
      open(id);
      expect(container.querySelector('.modpage [role="tablist"]')).toBeNull();
    }
  });

  it('maps a count to §8’s table', () => {
    expect(propertyStructure(0)).toBe('sentence');
    expect(propertyStructure(1)).toBe('sentence');
    expect(propertyStructure(2)).toBe('flat');
    expect(propertyStructure(4)).toBe('flat');
    expect(propertyStructure(5)).toBe('grouped');
  });

  it('paints a mod’s live value in its own category hue, not the accent', () => {
    const { container } = render(<App />);
    // Fullbright is Visual, so ice — never the default violet.
    open('fullbright');
    const page = container.querySelector('.modpage') as HTMLElement;
    expect(page.style.getPropertyValue('--hue')).toBe('var(--hue-visual)');
    expect((container.querySelector('.mprops') as HTMLElement).style.getPropertyValue('--hue')).toBe(
      'var(--hue-visual)',
    );
    // …and a PvP mod is coral, off the same one declaration.
    open('toggle_sprint');
    expect(
      (container.querySelector('.modpage') as HTMLElement).style.getPropertyValue('--hue'),
    ).toBe('var(--hue-pvp)');
    // Every tile carries its own, so the grid is hued too.
    set(() => useVoidStore.getState().closeMod());
    const tile = container.querySelector('.modcell[data-mod-id="crosshair"]') as HTMLElement;
    expect(tile.style.getPropertyValue('--hue')).toBe('var(--hue-visual)');
  });

  it('heads the page with the mod and foots it with Reset / Done', () => {
    const { container } = render(<App />);
    open('keystrokes');
    expect(container.querySelector('.modpage__title')!.textContent).toBe('Keystrokes');
    // `HUD  ·  ENABLED  ·  R-SHIFT`, the frame's meta line.
    expect(container.querySelector('.modpage__meta')!.textContent).toMatch(/^HUD {3}·/);
    // The mod's own sentence, which until now only the list layout could show.
    expect(container.querySelector('.modpage__desc')!.textContent).toBeTruthy();
    // `SELECTED MOD` was an eyebrow for a panel standing beside a grid. On a page whose whole
    // subject is this mod it labels nothing the title does not already say.
    expect(screen.queryByText('Selected mod')).toBeNull();
    // Enablement is the head switch, not a property row — frame `289:5523`, §8. There is
    // exactly one on the page, because the grid it used to duplicate is not mounted.
    expect(
      container.querySelector('.modpage__head [aria-label="Keystrokes enabled"]'),
    ).not.toBeNull();
    expect(container.querySelectorAll('[aria-label="Keystrokes enabled"]')).toHaveLength(1);
    expect(container.querySelector('.modpage .mprop__label')!.textContent).not.toBe('Enabled');
    expect(screen.getByText('Reset to default')).toBeTruthy();
    expect(screen.getByText('Done')).toBeTruthy();
    // The old `Edit position` button is gone: placement is on the preview now.
    expect(screen.queryByText('Edit position')).toBeNull();

    open('fullbright');
    // A gameplay mod has no placement, so it gets no corner slots at all.
    expect(container.querySelector('.preview__slot')).toBeNull();
  });
});

/**
 * `isEscape` — the reason a key match here cannot be `e.key === 'Escape'`.
 *
 * The in-game engine derives `KeyboardEvent.key` from the character the host sends, and Escape
 * has none: measured in game it arrives as `key: "Unidentified", code: "", which: 27`. Every
 * shape below is one this bundle really sees — the browser's, jsdom's, and the engine's.
 */
describe('isEscape', () => {
  it('accepts the name, the code and the legacy `which`', () => {
    expect(isEscape({ key: 'Escape' })).toBe(true);
    expect(isEscape({ key: 'Escape', keyCode: 27 })).toBe(true);
    // The one that matters: no usable name, only the number.
    expect(isEscape({ key: 'Unidentified', which: 27 })).toBe(true);
    expect(isEscape({ key: 'Unidentified', keyCode: 27 })).toBe(true);
  });

  it('is not fooled by anything else', () => {
    expect(isEscape({ key: 'Enter', keyCode: 13 })).toBe(false);
    expect(isEscape({ key: 'ArrowDown', keyCode: 40 })).toBe(false);
    expect(isEscape({ key: 'Unidentified' })).toBe(false);
    expect(isEscape({ key: 'e' })).toBe(false);
  });
});

describe('gridRows', () => {
  const ids = (n: number) =>
    Array.from({ length: n }, (_, i) => `m${i}`) as never as Parameters<typeof gridRows>[0];

  it('fills left to right, the order MOD_ORDER is written in', () => {
    const rows = gridRows(ids(12), 6);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toEqual(['m0', 'm1', 'm2', 'm3', 'm4', 'm5']);
    expect(rows[1]).toEqual(['m6', 'm7', 'm8', 'm9', 'm10', 'm11']);
  });

  it('never drops a mod — the old column-major fill capped at eight columns and could', () => {
    expect(gridRows(ids(40), 8).flat()).toHaveLength(40);
  });

  it('is shaped by the registry and the window: twelve mods are six across and two down', () => {
    // The full sweep, including the 17-21 range that used to break, is in
    // `test/grid-geometry.test.ts`.
    const shape = solveGrid(12, IN_GAME_VIEW.width, IN_GAME_VIEW.height);
    expect(shape.columns).toBe(6);
    expect(shape.rows).toBe(2);
  });
});

describe('Loadouts screen — frame 244:1130', () => {
  it('renders the definition line, every card and the footer hint', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'loadouts' }));
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Loadouts' })).toBeTruthy();
    expect(
      screen.getByText('A loadout is which mods are on, their settings and HUD layout.'),
    ).toBeTruthy();
    expect(screen.getByText('New loadout')).toBeTruthy();
    expect(screen.getByText(LOADOUTS_FOOTER, verbatim)).toBeTruthy();
    expect(screen.getAllByText('Includes').length).toBeGreaterThan(0);
  });

  it('marks the active loadout and offers a switch on the others', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'loadouts' }));
    const { container } = render(<App />);
    expect(container.querySelectorAll('.v-loadoutcard--active')).toHaveLength(1);
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0);
    expect(screen.getAllByText(/^Switch to /).length).toBeGreaterThan(0);
  });
});

describe('Party screen — frame 244:1426', () => {
  it('renders the members, the queue pane and the footer hint', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'party' }));
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Party' })).toBeTruthy();
    expect(screen.getByText('In your party')).toBeTruthy();
    expect(screen.getByText('Searge')).toBeTruthy();
    expect(screen.getByText('Leader')).toBeTruthy();
    expect(screen.getByText('Ready')).toBeTruthy();
    expect(screen.getByText('Bedwars 4v4')).toBeTruthy();
    expect(screen.getByText('Queue with party')).toBeTruthy();
    expect(screen.getByText(PARTY_FOOTER, verbatim)).toBeTruthy();
  });
});

describe('HUD layout editor — frame 244:1722', () => {
  it('renders the toolbar, the hint bar and no panel', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    const { container } = render(<App />);
    expect(screen.getByText('HUD layout')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Snap' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Grid' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reset' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Done' })).toBeTruthy();
    // `toStartWith`, not equality: the hint gains a tail naming the widgets whose mods are off,
    // and the fixture has some. `editorHint` is what that tail is tested through, below.
    expect(container.querySelector('.editor__hint')!.textContent).toContain(EDITOR_HINT);
    expect(container.querySelector('.v-panel')).toBeNull();
  });

  it('makes the HUD widgets interactive, unlike the HUD layer proper', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    const { container } = render(<App />);
    expect(container.querySelector('.hud-layer--editor')).not.toBeNull();
  });

  it('Snap is on by default, as the frame draws it', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    render(<App />);
    expect(screen.getByRole('button', { name: 'Snap' }).getAttribute('aria-pressed')).toBe('true');
  });

  /**
   * The structural regression gate for the bug that made the editor useless.
   *
   * The drag handlers used to hang off a `<div style={{display:'contents'}}>` wrapped around each
   * widget, and the gesture measured `e.currentTarget.getBoundingClientRect()` — which for a
   * `display: contents` element is `0,0 0x0`, in this engine as in every other. Every drag
   * therefore started from a widget the editor believed was at the layer origin with no size, and
   * grabbing a chip teleported it.
   *
   * jsdom cannot catch that by measuring — it has no layout, so **every** rect there is zero,
   * which is exactly why this shipped. So the assertion is structural instead: the handler is on
   * the element that carries the placement, and no box in the editor is `display: contents`.
   */
  it('hangs the drag off the box that has geometry, not a display:contents wrapper', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    const { container } = render(<App />);
    const layer = container.querySelector('.hud-layer--editor') as HTMLElement;

    for (const el of layer.querySelectorAll<HTMLElement>('*')) {
      expect(el.style.display, `${el.className} must not be display:contents`).not.toBe('contents');
    }

    // The handler is on `.hud-item` itself: a pointerdown there selects the widget.
    set(() => useVoidStore.getState().setEditorTarget('fps'));
    const cps = layer.querySelector('[data-hud-id="cps"]') as HTMLElement;
    expect(cps).not.toBeNull();
    fireEvent(cps, pointer('pointerdown', 10, 10));
    expect(useVoidStore.getState().editorTarget).toBe('cps');
  });

  /**
   * The gesture's arithmetic, with the geometry jsdom cannot supply stubbed in.
   *
   * Numbers chosen so every stage is visible in the result: the drop is snapped to the 8px grid,
   * lands in the middle third of both axes so the anchor is re-picked as `center`, and the offsets
   * are measured from the widget's *centre* because that is what a `center` anchor means.
   */
  it('a drag moves the widget to where it was dropped, and stores it against a fresh anchor', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    const { container } = render(<App />);
    const root = container.querySelector('.editor') as HTMLElement;
    const item = container.querySelector('[data-hud-id="keystrokes"]') as HTMLElement;
    stubRect(root, 0, 0, 1458, 820);
    stubRect(item, 31, 536, 130, 175);

    fireEvent(item, pointer('pointerdown', 95, 601));
    act(() => void window.dispatchEvent(pointer('pointermove', 595, 300)));
    act(() => void window.dispatchEvent(pointer('pointerup', 595, 300)));

    const stored = useVoidStore.getState().loadout!.hud.find((h) => h.id === 'keystrokes')!;
    // Grabbed 64 in from the widget's left and 65 down from its top; dropped at (595, 300), so
    // the box's top-left wants (531, 235) and the 8px grid takes it to (528, 232). Its centre
    // (593, 319.5) is the middle third of a 1458x820 viewport on both axes, so the anchor is
    // re-picked as `center` and the offsets are measured from there. `dy` is -90 before the
    // bridge's own 4px snap and -88 after it, which is the point of binding to the return value.
    expect(stored.anchor).toBe('center');
    expect(stored.dx).toBe(-136);
    expect(stored.dy).toBe(-88);
  });

  /** A gesture is one `setHud`, not one per move — §9, and the idle-paint budget of §4. */
  it('writes the placement once per gesture, on the drop', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    const { container } = render(<App />);
    const root = container.querySelector('.editor') as HTMLElement;
    const item = container.querySelector('[data-hud-id="keystrokes"]') as HTMLElement;
    stubRect(root, 0, 0, 1458, 820);
    stubRect(item, 31, 536, 130, 175);

    const commits = vi.spyOn(useVoidStore.getState(), 'commitHud');
    fireEvent(item, pointer('pointerdown', 95, 601));
    for (const x of [200, 300, 400, 500, 595]) {
      act(() => void window.dispatchEvent(pointer('pointermove', x, 300)));
    }
    expect(commits).not.toHaveBeenCalled();
    act(() => void window.dispatchEvent(pointer('pointerup', 595, 300)));
    expect(commits).toHaveBeenCalledTimes(1);
    commits.mockRestore();
  });

  /**
   * `Reset layout` is an **undo**, which means landing on the factory numbers exactly.
   *
   * `setHud` snaps every drop against `hud_editor_grid`, and the factory placements are not on
   * that grid — `keystrokes` is `31, -109`, which at the default grid of 4 comes back as
   * `32, -108`. Restoring a layout is not a placement gesture, so `resetHud` stands the grid down
   * for the duration; without that, the button that claims to restore the layout moves it.
   */
  it('Reset restores every widget to the factory placement, to the pixel', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    render(<App />);
    set(() => useVoidStore.getState().commitHud('fps', 'bottom-right', -200, -200, 2));

    fireEvent.click(screen.getByRole('button', { name: 'Reset' }));

    const hud = useVoidStore.getState().loadout!.hud;
    for (const id of HUD_MOD_IDS) {
      const item = hud.find((h) => h.id === id);
      expect(item, `${id} was not placed by Reset`).toBeDefined();
      expect({ anchor: item!.anchor, dx: item!.dx, dy: item!.dy }, id).toEqual(DEFAULT_HUD[id]);
      expect(item!.scale, id).toBe(1);
    }
  });

  /**
   * `Snap` is a control for `hud_editor_grid`, not a second opinion about it.
   *
   * Java re-snaps every drop against that global (`LiveState.setHud`), so a toggle that only
   * changed the page was overruled on every commit: `Snap: off` still quantised to 4px, and the
   * control looked like it did something and did not.
   */
  it('Snap writes the grid Java actually snaps against', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: 'Snap' }));
    expect(useVoidStore.getState().editorSnap).toBe(false);
    expect(useVoidStore.getState().globals.hudEditorGrid).toBe(0);
    // With the grid down, an odd offset survives the round trip unchanged.
    set(() => useVoidStore.getState().commitHud('fps', 'top-left', 37, 41, 1));
    expect(useVoidStore.getState().loadout!.hud.find((h) => h.id === 'fps')).toMatchObject({
      dx: 37,
      dy: 41,
    });

    fireEvent.click(screen.getByRole('button', { name: 'Snap' }));
    expect(useVoidStore.getState().globals.hudEditorGrid).toBe(8);
    set(() => useVoidStore.getState().commitHud('fps', 'top-left', 37, 41, 1));
    expect(useVoidStore.getState().loadout!.hud.find((h) => h.id === 'fps')).toMatchObject({
      dx: 40,
      dy: 40,
    });
  });

  /**
   * An enabled mod the loadout has forgotten to place draws nothing on the HUD and says nothing
   * about it — the fourth row of rendering-invariants §15's table. In the editor that absence is
   * fatal rather than cosmetic: the one screen whose job is placing widgets would be the one
   * screen that could not reach this one.
   */
  it('draws an enabled-but-unplaced widget at its factory position, so it can be rescued', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    const loadout = useVoidStore.getState().loadout!;
    set(() =>
      useVoidStore.setState({
        loadout: { ...loadout, hud: loadout.hud.filter((h) => h.id !== 'fps') },
      }),
    );
    // Gone from the HUD proper, exactly as it is today…
    const plain = render(<App />);
    expect(plain.container.querySelector('.hud-layer [data-hud-id="fps"]')).toBeNull();
    cleanup();

    // …and present in the editor, where it can be dragged and thereby given a real entry.
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    const { container } = render(<App />);
    expect(container.querySelector('.hud-layer--editor [data-hud-id="fps"]')).not.toBeNull();
  });

  /** The hint names what is not on screen, rather than letting it be a silent absence. */
  it('says how many widgets are switched off', () => {
    expect(editorHint(0)).toBe(EDITOR_HINT);
    expect(editorHint(1)).toContain('1 more is switched off');
    expect(editorHint(3)).toContain('3 more are switched off');
  });

  /**
   * A corner grip scales, because the modifier the frame printed cannot exist here.
   *
   * `SelectionFrame` has always drawn four grips and always offered `onHandlePointerDown`; the
   * editor never used either, and asked for `⌥`+drag instead. The grips are the affordance that
   * survives an engine with no modifier state on a mouse event — and the better one anyway,
   * because they are visible and they are where a person reaches.
   */
  it('a corner grip scales the widget about its anchor', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    set(() => useVoidStore.getState().setEditorTarget('keystrokes'));
    const { container } = render(<App />);
    const root = container.querySelector('.editor') as HTMLElement;
    const item = container.querySelector('[data-hud-id="keystrokes"]') as HTMLElement;
    stubRect(root, 0, 0, 1458, 820);
    stubRect(item, 31, 536, 130, 175);

    const before = useVoidStore.getState().loadout!.hud.find((h) => h.id === 'keystrokes')!;
    const grip = container.querySelector('.v-selection__handle--se') as HTMLElement;
    expect(grip).not.toBeNull();

    // Pull the south-east grip out along both axes: `reach` is the widget's mean dimension
    // (152.5), so 152 of diagonal travel is very nearly one whole doubling.
    fireEvent(grip, pointer('pointerdown', 200, 700));
    act(() => void window.dispatchEvent(pointer('pointermove', 276, 776)));
    act(() => void window.dispatchEvent(pointer('pointerup', 276, 776)));

    const after = useVoidStore.getState().loadout!.hud.find((h) => h.id === 'keystrokes')!;
    expect(after.scale).toBeGreaterThan(1.5);
    expect(after.scale).toBeLessThanOrEqual(4);
    // Scaling is not a move: the placement it is anchored by does not change.
    expect(after.anchor).toBe(before.anchor);
    expect(after.dx).toBe(before.dx);
    expect(after.dy).toBe(before.dy);
  });

  /**
   * Arrows nudge — the only way to place a widget on a chosen pixel.
   *
   * Input reaches the page coalesced at the game's frame rate, so a pointer cannot reliably land
   * on one. There is deliberately no modifier variant: chorded modifiers are not something this
   * host has been shown to deliver, and `Snap` already switches the step between 1px and a whole
   * grid cell with a control that is on screen.
   */
  it('arrow keys nudge the selected widget by the grid, or by a pixel with Snap off', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    set(() => useVoidStore.getState().setEditorTarget('fps'));
    const { container } = render(<App />);
    const item = () => useVoidStore.getState().loadout!.hud.find((h) => h.id === 'fps')!;
    const before = item();
    stubRect(container.querySelector('.editor') as HTMLElement, 0, 0, 1458, 820);
    // Stubbed where the loadout actually puts it — a nudge works off where the widget *is*,
    // measured, exactly as a drag does, so the two cannot disagree about the same widget.
    stubRect(
      container.querySelector('[data-hud-id="fps"]') as HTMLElement,
      before.dx,
      before.dy,
      100,
      40,
    );

    // Snap on: a press is a whole grid cell.
    act(() => void window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' })));
    expect(item().dx).toBe(before.dx + 8);
    expect(item().dy).toBe(before.dy);
  });

  /**
   * The frame prints `⌥ drag to scale`, and it was never possible.
   *
   * `WebView.fireMouseEvent(type, x, y, button)` has no modifier parameter, so `altKey` on a
   * pointer event in game is a compile-time false — measured, on every event of a real drag. The
   * hint must not promise it, because a hint naming a gesture the engine cannot deliver sends the
   * player looking for a fault in their own hands.
   */
  it('promises no modifier gesture the host cannot deliver', () => {
    expect(EDITOR_HINT).not.toContain('⌥');
    expect(EDITOR_HINT).not.toMatch(/alt|shift/i);
    expect(EDITOR_HINT).toContain('Corners scale');
  });
});

describe('Quick palette — frame 244:1900', () => {
  it('renders the input, both captions and the footer hints', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    render(<App />);
    expect(screen.getByRole('dialog', { name: 'Quick palette' })).toBeTruthy();
    expect(screen.getByText('Actions')).toBeTruthy();
    for (const word of ['move', 'open', 'toggle', 'close']) {
      expect(screen.getByText(word)).toBeTruthy();
    }
  });

  /** The two Enter verbs are not guessable from the chips, so they are also written out. */
  it('spells out what ↵ and ⌘↵ do', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const hint = container.querySelector('.palette-hint');
    expect(hint?.textContent).toBe(PALETTE_HINT);
    expect(PALETTE_HINT).toContain('↵ opens');
    expect(PALETTE_HINT).toContain('⌘↵ toggles');
    expect(PALETTE_HINT).toContain('without closing');
  });

  /**
   * `fullb` answers with Fullbright, once.
   *
   * It used to answer with `Toggle Fullbright` and `Fullbright settings` — two rows whose
   * Enter now does exactly the same thing, so the second one is gone and the title is the
   * mod's own name.
   */
  it('answers `fullb` with the mod itself, once', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'fullb' } });
    const titles = [...container.querySelectorAll('.v-palette__title')].map((el) => el.textContent);
    expect(titles[0]).toBe('Fullbright');
    expect(titles.filter((t) => t?.includes('Fullbright') && !t.includes('Turn on'))).toHaveLength(
      1,
    );
  });

  /**
   * ↑↓ moves the selection, and the moved-to row is the one that is marked.
   *
   * This passed in jsdom while being completely dead in game, which is the only reason it is
   * worth spelling out here: the handler was right, and Java was appending U+F701 — AppKit's
   * `NSDownArrowFunctionKey` — to the query on every press as if it were typed text, so the
   * query changed, `useEffect([query])` reset the cursor, and the selection never moved.
   * `KeyNames.isTypedText` is the fix and `ActuatorsTest` is where it is guarded; this only
   * holds up the page's half.
   */
  it('moves the selection with the arrow keys', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const layer = container.querySelector('.palette-layer') as HTMLElement;
    const selected = () =>
      [...container.querySelectorAll('.v-palette__row')].findIndex((row) =>
        row.classList.contains('v-palette__row--selected'),
      );
    expect(selected()).toBe(0);
    fireEvent.keyDown(layer, { key: 'ArrowDown' });
    expect(selected()).toBe(1);
    fireEvent.keyDown(layer, { key: 'ArrowDown' });
    expect(selected()).toBe(2);
    fireEvent.keyDown(layer, { key: 'ArrowUp' });
    expect(selected()).toBe(1);
    // Clamped at both ends rather than wrapping.
    for (let i = 0; i < 10; i += 1) fireEvent.keyDown(layer, { key: 'ArrowUp' });
    expect(selected()).toBe(0);
    const rows = container.querySelectorAll('.v-palette__row').length;
    for (let i = 0; i < rows + 5; i += 1) fireEvent.keyDown(layer, { key: 'ArrowDown' });
    expect(selected()).toBe(rows - 1);
  });

  /**
   * The query row draws one caret, and it is the engine's.
   *
   * `@void/ui`'s `PaletteInput` renders a blinking accent bar next to the real `<input>` and
   * defaults it on — right for a still, two cursors for a live field. Measured in game:
   * Ultralight paints and blinks a caret in a focused input by itself, at the real text
   * position, so the engine's is the one that can be right. What jsdom cannot check is the
   * other half of that fix — the decorative bar was 22px tall and in flow, so it was holding
   * `.v-palette__query` open, and removing it collapsed the column and clipped the engine's
   * caret and the placeholder away. That lives in `09-palette.css` as a stated `min-height`.
   */
  it('draws no decorative caret beside the real field', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    expect(container.querySelectorAll('.v-palette__query input')).toHaveLength(1);
    expect(container.querySelectorAll('.v-palette__caret')).toHaveLength(0);
  });

  /**
   * The primary action navigates and changes nothing.
   *
   * "Clicking on an entry when searching doesn't actually take me anywhere" was not the click
   * being lost — it reached `run()` in game, measured. It was that the top of the list was all
   * toggles, and a toggle flipped a switch on one of seventeen tiles and closed the palette
   * over it. The answer is not "toggle *and* navigate", which leaves the player unable to tell
   * which of the two Enter meant: a mod's result now opens the mod, exactly as clicking its
   * tile does (§7), and the loadout is left alone.
   */
  it.each([
    ['a click on the row', (row: HTMLElement) => fireEvent.click(row)],
    // Enter with the row focused: `PaletteResult` runs it, and the palette's own Enter must not
    // run it a second time.
    ['Enter on the row', (row: HTMLElement) => fireEvent.keyDown(row, { key: 'Enter' })],
    // Enter with the field focused, which is where the keyboard actually is.
    [
      'Enter in the query',
      (row: HTMLElement) =>
        fireEvent.keyDown(row.closest('.palette-layer')!.querySelector('input')!, { key: 'Enter' }),
    ],
  ])('the top result with %s opens the mod and changes nothing', (_name, activate) => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'party' }));
    set(() => useVoidStore.getState().selectMod('zoom'));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'fullb' } });
    const row = container.querySelector('.v-palette__row') as HTMLElement;
    expect(row.textContent).toContain('Fullbright');
    const before = isModOn(useVoidStore.getState().loadout, 'fullbright');
    set(() => activate(row));
    const after = useVoidStore.getState();
    expect(after.paletteOpen).toBe(false);
    // Search now navigates to the mod's page — the same place its tile goes, which is the
    // whole reason the palette's primary is "open" and not "toggle".
    expect(after.route).toEqual({ name: 'mod', id: 'fullbright' });
    expect(after.selectedMod).toBe('fullbright');
    expect(isModOn(after.loadout, 'fullbright')).toBe(before);
  });

  /**
   * ⌘↵ / Ctrl-↵ is the toggle, and it leaves the palette up.
   *
   * Staying open is the point, not an implementation detail: it is the only way the change is
   * ever seen — the row below the cursor rewrites its own `currently off` to `currently on` on
   * the next frame — and it is what makes flipping three things in a row possible.
   */
  it.each([
    ['⌘', { metaKey: true }],
    ['Ctrl', { ctrlKey: true }],
  ])('%s+Enter toggles in place, without closing or navigating', (_name, modifier) => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'party' }));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'fullb' } });
    const before = isModOn(useVoidStore.getState().loadout, 'fullbright');
    set(() => fireEvent.keyDown(input, { key: 'Enter', ...modifier }));
    let after = useVoidStore.getState();
    expect(isModOn(after.loadout, 'fullbright')).toBe(!before);
    expect(after.paletteOpen).toBe(true);
    expect(after.route.name).toBe('party');
    // The row it happened on says so, and a second press flips it back.
    expect(container.querySelector('.v-palette__row')?.textContent).toContain(
      before ? 'currently off' : 'currently on',
    );
    set(() => fireEvent.keyDown(input, { key: 'Enter', ...modifier }));
    after = useVoidStore.getState();
    expect(isModOn(after.loadout, 'fullbright')).toBe(before);
    expect(after.paletteOpen).toBe(true);
  });

  /**
   * A modified Enter reaches the palette even from a focused row.
   *
   * `PaletteResult` handles Enter itself and calls `onSelect`, which carries no modifier — so
   * without the modifier test in `@void/ui` the secondary would silently become the primary on
   * the one path where a row, not the field, has the keyboard.
   */
  it('gives ⌘+Enter on a focused row the secondary, not the primary', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'party' }));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'fullb' } });
    const row = container.querySelector('.v-palette__row') as HTMLElement;
    const before = isModOn(useVoidStore.getState().loadout, 'fullbright');
    set(() => fireEvent.keyDown(row, { key: 'Enter', metaKey: true }));
    const after = useVoidStore.getState();
    expect(isModOn(after.loadout, 'fullbright')).toBe(!before);
    expect(after.paletteOpen).toBe(true);
    expect(after.route.name).toBe('party');
  });

  /** A row with nothing to toggle does nothing at all on ⌘↵ — it does not fall back to the primary. */
  it('leaves a result with no secondary alone on ⌘+Enter', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'party' }));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'edit hud layout' } });
    expect(container.querySelector('.v-palette__title')?.textContent).toBe('Edit HUD layout');
    set(() => fireEvent.keyDown(input, { key: 'Enter', metaKey: true }));
    const after = useVoidStore.getState();
    expect(after.paletteOpen).toBe(true);
    expect(after.route.name).toBe('party');
  });

  /**
   * No two rows may read the same.
   *
   * `load` used to answer with four rows of `Turn on in UHC loadout`, one per mod that happened
   * to be off in it — same title, same score, and the mod only in a subtitle.
   */
  it('never shows the same title twice', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    for (const query of ['', 'load', 'loadout', 'turn', 'on', 'in', 'set', 'e']) {
      fireEvent.change(input, { target: { value: query } });
      const titles = [...container.querySelectorAll('.v-palette__title')].map((el) =>
        el.textContent,
      );
      expect(new Set(titles).size, `duplicate row title for “${query}”`).toBe(titles.length);
    }
  });

  /** Escape closes the palette and leaves the menu up; §6.3 gives the second one to the menu. */
  it('closes on Escape without closing the menu', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const layer = container.querySelector('.palette-layer') as HTMLElement;
    fireEvent.keyDown(layer, { key: 'Escape' });
    expect(useVoidStore.getState().paletteOpen).toBe(false);
    expect(useVoidStore.getState().menuOpen).toBe(true);
  });

  /** The magnifier in the bar is the shortcut with a face on it. */
  it('opens from the search button in the top bar', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    const { container } = render(<App />);
    expect(container.querySelector('.palette-layer')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(useVoidStore.getState().paletteOpen).toBe(true);
    expect(container.querySelector('.palette-layer')).not.toBeNull();
  });
});

/**
 * The VOID watermark — the thirteenth mod, and the one that is a *mark* rather than a readout.
 *
 * The whole argument for making it a mod is that it then gets `on`, `scale`, `opacity` and a
 * draggable placement without any of them being written for it. These assert the two halves of
 * that: it goes through `HudLayer`'s ordinary path like every other HUD mod, and its one
 * bespoke setting — `style` — actually changes what is drawn.
 */
describe('the VOID watermark', () => {
  beforeEach(() => {
    useVoidStore.setState({ menuOpen: false, route: { name: 'mods' } });
  });

  it('draws on the HUD like any other HUD mod, and stops when switched off', () => {
    const { container } = render(<App />);
    const mark = () => container.querySelector('.hud-layer [data-hud-id="watermark"] .wmark');
    expect(mark()).not.toBeNull();

    // Off is the mod's own switch, not a flag beside it.
    set(() => useVoidStore.getState().toggleMod('watermark', false));
    expect(container.querySelector('[data-hud-id="watermark"]')).toBeNull();
    set(() => useVoidStore.getState().toggleMod('watermark', true));
    expect(mark()).not.toBeNull();
  });

  it('takes its placement, scale and opacity from the loadout, like every other widget', () => {
    const { container } = render(<App />);
    const slot = () => container.querySelector('[data-hud-id="watermark"]') as HTMLElement;
    // `HudLayer` folds the mod's own `opacity` into the slot and its `scale` into the inner zoom
    // box. The widget reads neither — doing it in both places would apply them twice.
    set(() => useVoidStore.getState().setSetting('watermark', 'opacity', 0.5));
    // Dimmed by the menu is a separate multiplier; the menu is shut here.
    expect(slot().style.opacity).toBe('0.5');
    set(() => useVoidStore.getState().commitHud('watermark', 'bottom-right', -30, -30, 2));
    // `zoom` on the inner box, never `scale()` in the transform: scaled text is corrupt in this
    // engine, measured (see `placementStyle`).
    expect(slot().style.transform).not.toContain('scale');
    expect((slot().querySelector('.hud-item__zoom') as HTMLElement).style.zoom).toBe('2');
    expect(slot().style.right).toBe('0px');
  });

  it('changes what it draws with `style`, which is the only setting written for it', () => {
    const { container } = render(<App />);
    const mark = () => container.querySelector('.hud-layer .wmark') as HTMLElement;
    const word = () => container.querySelector('.hud-layer .wmark__word');
    const ring = () => container.querySelector('.hud-layer .wmark .cart');

    // `full` is the ring and the wordmark — the same object the bar draws, at HUD size.
    set(() => useVoidStore.getState().setSetting('watermark', 'style', 'full'));
    expect(mark().className).toContain('wmark--full');
    expect(ring()).not.toBeNull();
    expect(word()).not.toBeNull();

    set(() => useVoidStore.getState().setSetting('watermark', 'style', 'mark'));
    expect(ring()).not.toBeNull();
    expect(word()).toBeNull();

    set(() => useVoidStore.getState().setSetting('watermark', 'style', 'word'));
    expect(ring()).toBeNull();
    expect(word()!.textContent).toBe('VOID');

    // An unknown value from a host with a newer schema falls back rather than drawing nothing —
    // an absence is the failure mode this codebase keeps shipping by accident.
    set(() => useVoidStore.getState().setSetting('watermark', 'style', 'nonsense'));
    expect(mark().className).toContain('wmark--full');
  });

  it('is a mod in the grid too, with the mark itself as its tile preview', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.setState({ menuOpen: true, route: { name: 'mods' } }));
    const tile = container.querySelector('[data-mod-id="watermark"]') as HTMLElement;
    expect(tile).not.toBeNull();
    // Not an icon: the tile draws what the mod draws, which for this one is the real widget.
    expect(tile.querySelector('.tart .wmark')).not.toBeNull();
    // The label had to be shortened when thirteen mods took the grid to seven columns — at 165px
    // `VOID watermark` truncated to `VOID waterm…`, which is worse than a shorter true name.
    expect(tile.querySelector('.modcell__name')!.textContent).toBe('Watermark');
  });
});
