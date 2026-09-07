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
import { connectBridge } from '@/bridge/connect';
import { resetDerivedState, useVoidStore } from '@/store/store';
import { App } from '@/App';
import {
  MODS_HINT_GRID,
  MODS_HINT_OPEN,
  OPEN_COLUMNS,
  firstVisibleColumn,
  gridRows,
  solveGrid,
} from '@/menu/ModsScreen';
import { IN_GAME_VIEW } from './setup';
import { anchorShortLabel, modProperties, propertyStructure } from '@/menu/ModSettingsScreen';
import { LOADOUTS_FOOTER } from '@/menu/LoadoutsScreen';
import { PARTY_FOOTER } from '@/menu/PartyScreen';
import { EDITOR_HINT } from '@/menu/HudEditorScreen';

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
    inspector: 'open',
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

describe('Mods screen — layout × inspector', () => {
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
    expect(screen.getByText('VOID')).toBeTruthy();
    for (const label of ['All', 'HUD', 'PvP', 'Visual', 'Utility']) {
      expect(screen.getByRole('tab', { name: label })).toBeTruthy();
    }
    expect(screen.getByLabelText('Close')).toBeTruthy();
    expect(screen.getByText(MODS_HINT_OPEN, verbatim)).toBeTruthy();
    set(() => useVoidStore.getState().setInspector('closed'));
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

  it('offers the two controls as two independent controls, not one mode switcher', () => {
    render(<App />);
    // Two radios for `layout`, one pressed button for `inspector`. A mode switcher
    // would be a single group of three.
    expect(screen.getByRole('radio', { name: 'Grid' })).toBeTruthy();
    expect(screen.getByRole('radio', { name: 'List' })).toBeTruthy();
    const properties = screen.getByRole('button', { name: 'Properties' });
    expect(properties.getAttribute('aria-pressed')).toBe('true');
  });

  it('reaches all four states, and the two controls never move each other', () => {
    render(<App />);
    const store = () => useVoidStore.getState();
    const seen = new Set<string>();
    for (const layout of ['grid', 'list'] as const) {
      for (const inspector of ['open', 'closed'] as const) {
        set(() => store().setLayout(layout));
        set(() => store().setInspector(inspector));
        expect(store().layout).toBe(layout);
        expect(store().inspector).toBe(inspector);
        seen.add(`${layout}/${inspector}`);
      }
    }
    expect(seen.size).toBe(4);
  });

  it('draws all twelve tiles, in the shape that fills the panel', () => {
    const { container } = render(<App />);
    expect(container.querySelectorAll('[data-mod-id]')).toHaveLength(12);
    expect(screen.getByText('FPS display')).toBeTruthy();
    expect(screen.getByText('Toggle sprint')).toBeTruthy();
    // Twelve mods, eight columns allowed: two rows of six, not three rows of four inside a
    // panel wide enough for eight.
    const rows = [...container.querySelectorAll('.mods-row')];
    expect(rows).toHaveLength(2);
    for (const row of rows) {
      expect(row.querySelectorAll('[data-mod-id]')).toHaveLength(6);
    }
  });

  it('contracts by clipping, not by re-flowing: same tiles, same rows, same order', () => {
    const { container } = render(<App />);
    const shape = () =>
      [...container.querySelectorAll('.mods-row')].map((row) =>
        [...row.querySelectorAll('[data-mod-id]')].map((el) => el.getAttribute('data-mod-id')),
      );
    const before = shape();
    set(() => useVoidStore.getState().setInspector('closed'));
    // §7's no-reflow rule is withdrawn and the grid does now contract — but it contracts by
    // narrowing the box that clips it. The twelve tiles stay mounted in the same two rows in
    // the same order, so the three columns that survive keep the positions they already had,
    // and the movement is one width transition rather than twelve tiles finding new homes.
    expect(shape()).toEqual(before);
    expect(container.querySelectorAll('[data-mod-id]')).toHaveLength(12);
    expect(OPEN_COLUMNS).toBe(3);
  });

  it('shapes the grid from the registry, never from the filter', () => {
    const { container } = render(<App />);
    const overlay = () => container.querySelector('.overlay') as HTMLElement;
    expect(overlay().className).toContain('overlay--grid');
    expect(overlay().className).toContain('overlay--open');
    // Twelve mods: six across, two down — the shape that fills the panel, solved from the
    // registry's count and the window and handed to the CSS as lengths.
    const twelve = solveGrid(12, IN_GAME_VIEW.width, IN_GAME_VIEW.height);
    expect(twelve.columns).toBe(6);
    expect(twelve.rows).toBe(2);
    expect(overlay().style.getPropertyValue('--panel-cols')).toBe('6');
    expect(overlay().style.getPropertyValue('--panel-rows')).toBe('2');
    expect(overlay().style.getPropertyValue('--panel-w')).toBe(`${twelve.panelW}px`);
    expect(overlay().style.getPropertyValue('--panel-h')).toBe(`${twelve.panelH}px`);
    expect(overlay().style.getPropertyValue('--tile-w')).toBe(`${twelve.tileW}px`);
    // Nothing to scroll at twelve, so nothing is held back from the tiles for a scrollbar.
    expect(overlay().style.getPropertyValue('--grid-gutter')).toBe('0px');
    expect(overlay().className).not.toContain('overlay--scrolls');

    set(() => useVoidStore.getState().setInspector('closed'));
    expect(overlay().className).toContain('overlay--closed');
    expect(overlay().className).not.toContain('overlay--open');

    set(() => useVoidStore.getState().setLayout('list'));
    expect(overlay().className).toContain('overlay--list');

    // A filter matching two mods must not resize the tiles in the window it is filtering.
    set(() => useVoidStore.getState().setLayout('grid'));
    set(() => useVoidStore.getState().setModFilter('VISUAL'));
    expect(container.querySelectorAll('.mods-row')).toHaveLength(1);
    expect(overlay().style.getPropertyValue('--panel-cols')).toBe('6');
    expect(overlay().style.getPropertyValue('--tile-w')).toBe(`${twelve.tileW}px`);
  });

  it('walks the grid the way it reads: Left/Right within a row, Up/Down a whole row', () => {
    // Row-major, so the arrow keys had to change with the fill order. Twelve mods are six
    // across: `fps` is index 0, `zoom` index 5 (end of row one), `fullbright` index 6 (start of
    // row two), `coordinates` index 11 (the last).
    const { container } = render(<App />);
    const overlay = container.querySelector('.overlay') as HTMLElement;
    const at = () => useVoidStore.getState().selectedMod;
    const press = (key: string) => fireEvent.keyDown(overlay, { key });

    set(() => useVoidStore.getState().selectMod('fps'));
    press('ArrowRight');
    expect(at()).toBe('keystrokes');
    press('ArrowDown');
    expect(at()).toBe('hitboxes'); // index 1 + 6
    press('ArrowUp');
    expect(at()).toBe('keystrokes');

    // A row boundary is a step, not a wall: the grid is one sequence laid out in rows.
    set(() => useVoidStore.getState().selectMod('zoom'));
    press('ArrowRight');
    expect(at()).toBe('fullbright');

    // Both ends clamp rather than wrapping.
    set(() => useVoidStore.getState().selectMod('fps'));
    press('ArrowLeft');
    expect(at()).toBe('fps');
    press('ArrowUp');
    expect(at()).toBe('fps');
    set(() => useVoidStore.getState().selectMod('coordinates'));
    press('ArrowRight');
    expect(at()).toBe('coordinates');
    press('ArrowDown');
    expect(at()).toBe('coordinates');

    // The list is one column of rows: Down is the next mod, and Left/Right have nowhere to go.
    set(() => useVoidStore.getState().setLayout('list'));
    set(() => useVoidStore.getState().selectMod('fps'));
    press('ArrowDown');
    expect(at()).toBe('keystrokes');
    press('ArrowRight');
    expect(at()).toBe('keystrokes');
    press('ArrowUp');
    expect(at()).toBe('fps');
  });

  it('slides the grid only when the mod picked is not already among the surviving three', () => {
    const { container } = render(<App />);
    const first = () =>
      (container.querySelector('.overlay') as HTMLElement).style.getPropertyValue('--grid-first');
    // Row-major in six columns: `fps` is index 0 so column 1, `ping` is index 10 so column 5,
    // `coordinates` is index 11 so column 6 of 6. The rule §7 used to give for free: the grid
    // does not move for a mod already on screen, moves the least it can for one that is not,
    // and goes back to the left when the panel shuts.
    set(() => useVoidStore.getState().selectMod('fps'));
    expect(first()).toBe('0');
    set(() => useVoidStore.getState().selectMod('ping'));
    expect(first()).toBe('2');
    set(() => useVoidStore.getState().selectMod('coordinates'));
    expect(first()).toBe('3');
    set(() => useVoidStore.getState().setInspector('closed'));
    expect(first()).toBe('0');
  });

  it('firstVisibleColumn brings a column in as the last of three, and never overshoots', () => {
    // Six columns, three showing.
    expect(firstVisibleColumn(6, 0, true)).toBe(0);
    expect(firstVisibleColumn(6, 2, true)).toBe(0);
    expect(firstVisibleColumn(6, 3, true)).toBe(1);
    expect(firstVisibleColumn(6, 5, true)).toBe(3);
    // Never past the end, never at all with the panel shut, and safe with no selection.
    expect(firstVisibleColumn(3, 2, true)).toBe(0);
    expect(firstVisibleColumn(6, 5, false)).toBe(0);
    expect(firstVisibleColumn(6, -1, true)).toBe(0);
  });

  it('filters by tab and by search in either layout', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setModFilter('VISUAL'));
    const ids = [...container.querySelectorAll('[data-mod-id]')].map((el) =>
      el.getAttribute('data-mod-id'),
    );
    expect(ids.sort()).toEqual(['crosshair', 'fullbright']);

    set(() => useVoidStore.getState().setModFilter('all'));
    set(() => useVoidStore.getState().setLayout('list'));
    set(() => useVoidStore.getState().setModSearch('keystro'));
    expect(container.querySelectorAll('[data-mod-id]')).toHaveLength(1);
  });

  it('gives the list a description column, and an em-dash where one does not apply', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setLayout('list'));
    // Scoped to the column header: the inspector is open beside the list and it has
    // a `Keybind` of its own, which is exactly the duplication the list is for.
    const head = container.querySelector('.modlist__head') as HTMLElement;
    for (const column of ['Name', 'Description', 'Category', 'Keybind', 'Position', 'Scale']) {
      expect(within(head).getByText(column)).toBeTruthy();
    }

    const rows = (id: string) =>
      container.querySelector(`.modrow[data-mod-id="${id}"]`) as HTMLElement;
    // Fullbright is a gameplay mod: no keybind, no place on the HUD, no scale.
    const fullbright = rows('fullbright');
    expect(fullbright.querySelectorAll('.modrow__empty')).toHaveLength(3);
    expect(
      within(fullbright).getByText('Raises gamma so caves and shadows are fully lit.'),
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

  it('selecting a mod opens the inspector rather than navigating', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setInspector('closed'));
    fireEvent.click(screen.getByRole('button', { name: 'Fullbright' }));
    expect(useVoidStore.getState().inspector).toBe('open');
    expect(useVoidStore.getState().selectedMod).toBe('fullbright');
    // Still the Mods screen: nothing routed anywhere.
    expect(useVoidStore.getState().route.name).toBe('mods');
    expect(container.querySelector('.inspector--open')).not.toBeNull();
  });
});

describe('Properties panel — contract §8', () => {
  beforeEach(() => {
    set(() => useVoidStore.getState().applyMenu(true));
  });

  it('never lists a spatial property — placement and size are on the preview', () => {
    render(<App />);
    set(() => useVoidStore.getState().selectMod('keystrokes'));
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

  it('gives 5+ properties two light groups, and 2–4 a flat list with no labels', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().selectMod('keystrokes'));
    expect(container.querySelector('[data-structure="grouped"]')).not.toBeNull();
    // Two groups (§8), drawn as the frame's two columns. No section labels: at this
    // width the column is the grouping, and `289:3024` carries no caption over it.
    expect(container.querySelectorAll('.mprops__group')).toHaveLength(2);
    expect(container.querySelector('.mprops__cap')).toBeNull();
    expect(screen.getByText('Show mouse buttons')).toBeTruthy();
    expect(screen.getByText('LMB and RMB under the arrows')).toBeTruthy();

    set(() => useVoidStore.getState().selectMod('cps'));
    expect(container.querySelector('[data-structure="flat"]')).not.toBeNull();
    expect(container.querySelectorAll('.mprops__group')).toHaveLength(0);
  });

  it('gives a single property no list at all — a sentence and a bigger preview', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().selectMod('fullbright'));
    expect(container.querySelector('[data-structure="sentence"]')).not.toBeNull();
    expect(container.querySelector('.preview--large')).not.toBeNull();
    expect(container.querySelector('.mprop')).toBeNull();
    expect(screen.getByText(/Gamma sits at/)).toBeTruthy();
  });

  it('never offers tabs', () => {
    const { container } = render(<App />);
    for (const id of ['keystrokes', 'cps', 'fullbright', 'crosshair'] as const) {
      set(() => useVoidStore.getState().selectMod(id));
      expect(container.querySelector('.inspector [role="tablist"]')).toBeNull();
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
    set(() => useVoidStore.getState().selectMod('fullbright'));
    const panel = container.querySelector('.mprops') as HTMLElement;
    expect(panel.style.getPropertyValue('--hue')).toBe('var(--hue-visual)');
    // …and a PvP mod is coral, off the same one declaration.
    set(() => useVoidStore.getState().selectMod('toggle_sprint'));
    expect((container.querySelector('.mprops') as HTMLElement).style.getPropertyValue('--hue')).toBe(
      'var(--hue-pvp)',
    );
    // Every tile carries its own, so the grid is hued too.
    const tile = container.querySelector('.modcell[data-mod-id="crosshair"]') as HTMLElement;
    expect(tile.style.getPropertyValue('--hue')).toBe('var(--hue-visual)');
  });

  it('heads the panel the way the frame does, and foots it with Reset / Done', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().selectMod('keystrokes'));
    expect(screen.getByText('Selected mod')).toBeTruthy();
    expect(container.querySelector('.inspector__title')!.textContent).toBe('Keystrokes');
    // `HUD  ·  ENABLED  ·  R-SHIFT`, the frame's meta line.
    expect(container.querySelector('.inspector__meta')!.textContent).toMatch(/^HUD {3}·/);
    // Enablement is the head switch, not a property row — frame `289:5523`, §8.
    // (The tile carries one too, which is why this is scoped to the panel head.)
    expect(
      container.querySelector('.inspector__head [aria-label="Keystrokes enabled"]'),
    ).not.toBeNull();
    expect(container.querySelector('.inspector .mprop__label')!.textContent).not.toBe('Enabled');
    expect(screen.getByText('Reset to default')).toBeTruthy();
    expect(screen.getByText('Done')).toBeTruthy();
    // The old `Edit position` button is gone: placement is on the preview now.
    expect(screen.queryByText('Edit position')).toBeNull();

    set(() => useVoidStore.getState().selectMod('fullbright'));
    // A gameplay mod has no placement, so it gets no corner slots at all.
    expect(container.querySelector('.preview__slot')).toBeNull();
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
    expect(screen.getByText(EDITOR_HINT, verbatim)).toBeTruthy();
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
});

describe('Quick palette — frame 244:1900', () => {
  it('renders the input, both captions and the footer hints', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    render(<App />);
    expect(screen.getByRole('dialog', { name: 'Quick palette' })).toBeTruthy();
    expect(screen.getByText('Actions')).toBeTruthy();
    for (const word of ['move', 'run', 'settings', 'close']) {
      expect(screen.getByText(word)).toBeTruthy();
    }
  });

  it('reproduces the frame’s result order for `fullb`', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'fullb' } });
    const titles = [...container.querySelectorAll('.v-palette__title')].map((el) => el.textContent);
    expect(titles[0]).toBe('Toggle Fullbright');
    expect(titles[1]).toBe('Fullbright settings');
  });
});
