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
import { isModOn, modSettings, resetDerivedState, useVoidStore } from '@/store/store';
import { App } from '@/App';
import { MOD_ORDER } from '@/registry';
import { IN_GAME_VIEW } from './setup';
import { modProperties, propertyStructure } from '@/menu/ModSettingsScreen';
import { isEscape } from '@/menu/keys';
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
    // The Cartridge module's label tab (`show_label`), where the v1 chip trailed a unit.
    expect(within(hud as HTMLElement).getByText('FPS')).toBeTruthy();
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


describe('The settings bench — every property, drawn', () => {
  beforeEach(() => {
    set(() => useVoidStore.getState().applyMenu(true));
  });

  const open = (id: Parameters<ReturnType<typeof useVoidStore.getState>['openMod']>[0]) =>
    set(() => useVoidStore.getState().openMod(id));

  it('never lists a spatial property as a row — size is the Look column’s Scale meter', () => {
    const { container } = render(<App />);
    open('keystrokes');
    for (const property of modProperties('keystrokes', { scale: 1, opacity: 1, on: true })) {
      expect(property.key).not.toBe('scale');
    }
    const labels = [...container.querySelectorAll('.c-meterblk .c-label')].map((n) => n.textContent);
    expect(labels).toContain('Scale');
  });

  it('draws a control for every property of every mod, and only in the bench', () => {
    const { container } = render(<App />);
    for (const id of MOD_ORDER) {
      open(id);
      const bench = container.querySelector('.c-bench') as HTMLElement;
      expect(bench, id).not.toBeNull();
      const props = modProperties(id, modSettings(useVoidStore.getState().loadout, id));
      const drawn =
        bench.querySelectorAll('.c-row2').length +
        bench.querySelectorAll('.c-meterblk').length +
        bench.querySelectorAll('.c-optblk').length +
        bench.querySelectorAll('.c-swblk').length +
        bench.querySelectorAll('.c-flagblk').length +
        bench.querySelectorAll('.c-cap').length;
      const settings = modSettings(useVoidStore.getState().loadout, id);
      const size = 'scale' in settings || 'size' in settings ? 1 : 0;
      expect(drawn, id).toBe(props.length + size);
      // Never tabs (§8 still holds).
      expect(bench.querySelector('[role="tablist"]'), id).toBeNull();
    }
  });

  it('draws the mod itself on every bench, never a paragraph in an empty box', () => {
    const { container } = render(<App />);
    for (const [id, selector] of [
      ['fps', '.ch-fps'],
      ['keystrokes', '.ch-keys'],
      ['cps', '.ch-cps'],
      ['ping', '.ch-band'],
      ['coordinates', '.ch-coords'],
      ['armor_status', '.ch-armor'],
      ['potion_effects', '.ch-fx'],
      ['watermark', '.wmark'],
      ['crosshair', '.v-crosshair'],
      ['toggle_sprint', '.gprev'],
      ['fullbright', '.gprev'],
      ['hitboxes', '.gprev'],
      ['zoom', '.gprev'],
    ] as const) {
      open(id);
      const stage = container.querySelector('.c-prev__stage') as HTMLElement;
      expect(stage.querySelector('.c-prev__zoom'), id).not.toBeNull();
      expect(stage.querySelector(selector), id).not.toBeNull();
    }
  });

  it('maps a count to §8’s table', () => {
    expect(propertyStructure(0)).toBe('sentence');
    expect(propertyStructure(1)).toBe('sentence');
    expect(propertyStructure(2)).toBe('flat');
    expect(propertyStructure(4)).toBe('flat');
    expect(propertyStructure(5)).toBe('grouped');
  });

  it('takes the mod’s category hue for its live marks, never the accent', () => {
    const { container } = render(<App />);
    open('fullbright');
    expect(container.querySelector('.c-bench')!.classList.contains('c-cat-visual')).toBe(true);
    open('toggle_sprint');
    expect(container.querySelector('.c-bench')!.classList.contains('c-cat-pvp')).toBe(true);
    expect(container.querySelector('.c-cart[data-mod-id="crosshair"]')!.classList.contains('c-cat-visual')).toBe(true);
  });

  it('heads the bench with the mod, one switch and Esc, and foots it with Move / Reset / Pin', () => {
    const { container } = render(<App />);
    open('keystrokes');
    const head = container.querySelector('.c-bench__head') as HTMLElement;
    expect(head.querySelector('.c-title')!.textContent).toBe('Keystrokes');
    expect(head.textContent).toMatch(/HUD · /);
    expect(head.querySelectorAll('[role="switch"]')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Close settings' })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Move in HUD layout/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reset' })).toBeTruthy();
    // A gameplay mod has no placement, so no Move button.
    open('fullbright');
    expect(screen.queryByRole('button', { name: /Move in HUD layout/ })).toBeNull();
    expect(keepsEscape()).toBe(true);
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


describe('Loadouts tab — the cartridge case', () => {
  it('draws a cartridge per loadout, the rack and the footer keys', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'loadouts' }));
    const { container } = render(<App />);
    expect(container.querySelector('.c-panel--loadouts .c-h1')!.textContent).toBe('Loadouts');
    expect(container.querySelectorAll('.c-lcart')).toHaveLength(useVoidStore.getState().library.length);
    expect(container.querySelectorAll('.c-lcart.is-active')).toHaveLength(1);
    expect(container.querySelector('.c-lcart.is-active')!.textContent).toContain('in use');
    expect(container.querySelector('.c-rack__title')!.textContent).toContain('rack');
    expect(container.querySelectorAll('.c-rack__row')).toHaveLength(4);
    expect(container.querySelector('.c-foot')!.textContent).toContain('Cartridges hold mods, settings and HUD layout');
  });

  it('L switches to the next loadout from the tab', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'loadouts' }));
    render(<App />);
    const before = useVoidStore.getState().loadout!.id;
    act(() => {
      fireEvent.keyDown(window, { key: 'l' });
    });
    expect(useVoidStore.getState().loadout!.id).not.toBe(before);
  });
});

describe('Friends tab', () => {
  it('says it is not connected, and who is signed in', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().applySession({ name: 'Steve', uuid: '00000000-0000-0000-0000-000000000000', kind: 'microsoft' } as never));
    set(() => useVoidStore.getState().setRoute({ name: 'friends' }));
    const { container } = render(<App />);
    expect(container.querySelector('.c-soon')!.textContent).toContain('Not connected yet');
    expect(container.querySelector('.c-soon__who')!.textContent).toContain('Signed in as Steve');
  });
});

describe('HUD layout editor — frame 244:1722', () => {
  it('renders the toolbar island, the hint and no panel', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    const { container } = render(<App />);
    expect(screen.getByText('HUD layout')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Snap' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Grid' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reset' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Save' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Rulers' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Undo' })).toBeTruthy();
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
    const dialog = within(screen.getByRole('dialog', { name: 'Quick palette' }));
    for (const word of ['move', 'open', 'toggle', 'close']) {
      expect(dialog.getByText(word)).toBeTruthy();
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
    set(() => useVoidStore.getState().setRoute({ name: 'friends' }));
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
    set(() => useVoidStore.getState().setRoute({ name: 'friends' }));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'fullb' } });
    const before = isModOn(useVoidStore.getState().loadout, 'fullbright');
    set(() => fireEvent.keyDown(input, { key: 'Enter', ...modifier }));
    let after = useVoidStore.getState();
    expect(isModOn(after.loadout, 'fullbright')).toBe(!before);
    expect(after.paletteOpen).toBe(true);
    expect(after.route.name).toBe('friends');
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
    set(() => useVoidStore.getState().setRoute({ name: 'friends' }));
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
    expect(after.route.name).toBe('friends');
  });

  /** A row with nothing to toggle does nothing at all on ⌘↵ — it does not fall back to the primary. */
  it('leaves a result with no secondary alone on ⌘+Enter', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'friends' }));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'edit hud layout' } });
    expect(container.querySelector('.v-palette__title')?.textContent).toBe('Edit HUD layout');
    set(() => fireEvent.keyDown(input, { key: 'Enter', metaKey: true }));
    const after = useVoidStore.getState();
    expect(after.paletteOpen).toBe(true);
    expect(after.route.name).toBe('friends');
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

  /** ⌘K / Ctrl-K opens it over the shelves; the shelves' own search field is the other door. */
  it('opens from Ctrl-K over the Mods tab', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    const { container } = render(<App />);
    expect(container.querySelector('.palette-layer')).toBeNull();
    act(() => {
      fireEvent.keyDown(window, { key: 'k', ctrlKey: true });
    });
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

  it('is a cartridge on the Visual shelf too, with its own sprite', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.setState({ menuOpen: true, route: { name: 'mods' }, modFilter: 'visual' }));
    const cart = container.querySelector('.c-cart[data-mod-id="watermark"]') as HTMLElement;
    expect(cart).not.toBeNull();
    expect(cart.querySelector('.c-sprite[data-sprite="watermark"]')).not.toBeNull();
    expect(cart.querySelector('.c-cart__name')!.textContent).toBe('Watermark');
  });
});
