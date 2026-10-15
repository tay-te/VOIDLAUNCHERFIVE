/**
 * The Right-Shift menu — Cartridge (Figma 559:25183, section B 623:20): the shell, the shelves,
 * search, the settings bench and keybind capture, and rule 9's quick panel in a match.
 *
 * Run against the real fake bridge, like every other screen test, so `switchLoadout`,
 * `setModSetting` and `closeMenu` are the actual call shapes of `bridge.json`.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';

import { connectBridge, getVoid, keepsEscape } from '@/bridge/connect';
import { COMBAT_WINDOW_MS, FACTORY_QUICK, isModOn, modSettings, quickPins, resetDerivedState, useVoidStore } from '@/store/store';
import { App } from '@/App';
import { MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { SHELF_MODS, SHELVES, shelfPreview } from '@/cart/shelves';
import { searchMods, searchSettings, layoutRows } from '@/cart/ModsTab';
import { conflictFor, VANILLA_BINDS } from '@/cart/keys';
import { solveScale } from '@/cart/scale';
import { panelGeometry } from '@/cart/CartMenu';
import { cartValueKey } from '@/cart/values';
import { hasSprite } from '@/cart/Sprite';
import { devGo } from '@/cart/dev';

function set(mutate: () => void) {
  act(() => {
    mutate();
  });
}

const key = (k: string, extra: KeyboardEventInit = {}) =>
  set(() => {
    fireEvent.keyDown(window, { key: k, ...extra });
  });
const escape = () =>
  set(() => {
    fireEvent.keyDown(window, { keyCode: 27 });
  });

let dispose: () => void;

beforeEach(() => {
  resetDerivedState();
  useVoidStore.setState({
    route: { name: 'mods' },
    back: 'mods',
    paletteOpen: false,
    context: 'lobby',
    selectedMod: 'keystrokes',
    modSearch: '',
    modFilter: 'all',
    toasts: [],
    server: { host: '', connected: false },
  });
  ({ dispose } = connectBridge({ forceFake: true, runFakeClock: false }));
});

afterEach(() => {
  cleanup();
  dispose();
  vi.useRealTimers();
});

const open = () => set(() => useVoidStore.getState().applyMenu(true));
const total = Object.keys(MOD_REGISTRY).length;

describe('the shell', () => {
  it('opens on the tab island, the centred Mods panel and the status strip, over a dim', () => {
    open();
    const { container } = render(<App />);
    expect(useVoidStore.getState().context).toBe('lobby');
    const tabs = [...container.querySelectorAll('.c-island--tabs .c-tab:not(.c-tab--close)')].map((t) => t.textContent);
    expect(tabs).toEqual(['1Mods', '2Loadouts', '3Keybinds', '4Friends', '5Cosmetics']);
    expect(container.querySelector('.c-brand')?.textContent).toContain('VOID');
    expect(container.querySelector('.c-tab--close')?.textContent).toContain('R-Shift');
    expect(container.querySelector('.c-island--strip')?.textContent).toContain('HUD layout');
    expect(container.querySelector('.c-panel--mods')).not.toBeNull();
    expect(container.querySelector('.menu-layer__dim')).not.toBeNull();
  });

  it('1–5 jump to a section; Escape walks tabs 2–5 back to Mods, then closes', () => {
    open();
    const { container } = render(<App />);
    key('3');
    expect(useVoidStore.getState().route.name).toBe('keybinds');
    expect(container.querySelector('.c-panel--keybinds')).not.toBeNull();
    key('4');
    expect(container.querySelector('.c-panel--friends')?.textContent).toContain('Not connected yet');
    key('5');
    expect(container.querySelector('.c-panel--cosmetics')?.textContent).toContain('Not connected yet');
    expect(keepsEscape()).toBe(true);
    escape();
    expect(useVoidStore.getState().route.name).toBe('mods');
    expect(keepsEscape()).toBe(false);
    const close = vi.spyOn(getVoid(), 'closeMenu');
    escape();
    expect(close).toHaveBeenCalledTimes(1);
  });

  it('the strip and H open the HUD editor, which Escape returns from to the same tab', () => {
    open();
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: /HUD layout/ }));
    expect(useVoidStore.getState().route).toEqual({ name: 'hud-editor' });
    escape();
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    key('2');
    key('h');
    expect(useVoidStore.getState().route).toEqual({ name: 'hud-editor' });
    escape();
    expect(useVoidStore.getState().route).toEqual({ name: 'loadouts' });
    expect(useVoidStore.getState().menuOpen).toBe(true);
  });

  it('is laid out once at 1920 × 1080 and zoomed to the window, with a compact 720p form', () => {
    expect(solveScale(1920, 1080)).toMatchObject({ u: 1, width: 1920, height: 1080, compact: false });
    expect(solveScale(2560, 1440)).toMatchObject({ u: 1.333, compact: false });
    const small = solveScale(1280, 720);
    expect(small.u).toBe(0.75);
    expect(small.compact).toBe(true);
    // The panel always fits the layer, and the bench takes the right when a mod is open.
    for (const s of [solveScale(1280, 720), solveScale(1920, 1080), solveScale(2560, 1440)]) {
      const g = panelGeometry(s, true);
      expect(g.panel.left).toBeGreaterThanOrEqual(0);
      expect(g.bench!.left + g.bench!.width).toBeLessThanOrEqual(s.width);
      expect(g.panel.top + g.panel.height).toBeLessThanOrEqual(s.height);
    }
    expect(panelGeometry(solveScale(1920, 1080), false).panel).toEqual({ left: 370, top: 150, width: 1180, height: 784 });
  });
});

describe('the shelves', () => {
  it('puts every registry mod on exactly one category shelf, and has a sprite for each', () => {
    const shelved = Object.values(SHELVES).flat();
    expect(new Set(shelved).size).toBe(shelved.length);
    expect(shelved.sort()).toEqual([...SHELF_MODS].sort());
    expect(SHELF_MODS).toHaveLength(total);
    for (const id of SHELF_MODS) expect(hasSprite(id), id).toBe(true);
  });

  it('draws five cartridges and a `+N more` per long shelf, six when the shelf fits', () => {
    open();
    const { container } = render(<App />);
    const shelves = [...container.querySelectorAll('.c-shelf')];
    expect(shelves).toHaveLength(4);
    for (const [i, cat] of (['hud', 'pvp', 'visual', 'utility'] as const).entries()) {
      const { shown, more } = shelfPreview(SHELVES[cat]);
      expect(shelves[i]!.querySelectorAll('.c-cart[data-mod-id]')).toHaveLength(shown.length);
      expect(shelves[i]!.querySelector('.c-cart--more')?.textContent ?? '').toBe(more ? `+${more}more` : '');
    }
  });

  it('a cartridge carries its key, LED, sprite, name, live value, settings button and switch', () => {
    open();
    const { container } = render(<App />);
    const fps = container.querySelector('.c-cart[data-mod-id="fps"]')!;
    expect(fps.querySelector('.c-sprite[data-sprite="fps"]')).not.toBeNull();
    expect(fps.querySelector('.c-cart__name')?.textContent).toBe('FPS display');
    expect(fps.querySelector('.c-led')).not.toBeNull();
    expect(fps.querySelector('[role="switch"]')).not.toBeNull();
    expect(fps.querySelector('.c-cart__gear')).not.toBeNull();
    // An off mod reads `off`, dims its art and darkens its LED.
    set(() => useVoidStore.getState().toggleMod('fps', false));
    expect(fps.classList.contains('is-off')).toBe(true);
    expect(fps.querySelector('.c-cart__val')?.textContent).toBe('off');
    expect(fps.querySelector('.c-led--on')).toBeNull();
    // A bound key is printed as a keycap.
    set(() => useVoidStore.getState().setSetting('zoom', 'key', 'C'));
    set(() => useVoidStore.getState().setModFilter('utility'));
    expect(container.querySelector('.c-cart[data-mod-id="zoom"] .c-cart__strip .c-kc')?.textContent).toBe('C');
  });

  it('marks grey mods on Hypixel instead of hiding them', () => {
    open();
    set(() => useVoidStore.getState().applyServer({ host: 'mc.hypixel.net', connected: true }));
    set(() => useVoidStore.getState().setModFilter('pvp'));
    const { container } = render(<App />);
    const grey = (Object.keys(MOD_REGISTRY) as ModId[]).filter((id) => MOD_REGISTRY[id].hypixel_safe === 'grey' && SHELVES.pvp.includes(id));
    expect(grey.length).toBeGreaterThan(0);
    for (const id of grey) {
      expect(container.querySelector(`[data-mod-id="${id}"] .c-cart__val`)?.textContent).toContain('Grey on Hypixel');
    }
  });

  it('counts each shelf the way the registry does, and a chip or View shelf shows one shelf', () => {
    open();
    const { container } = render(<App />);
    const chips = [...container.querySelectorAll('.c-chips .c-chip')].map((c) => c.textContent);
    expect(chips.slice(0, 5)).toEqual([
      `All${total}`,
      `HUD${SHELVES.hud.length}`,
      `PvP${SHELVES.pvp.length}`,
      `Visual${SHELVES.visual.length}`,
      `Utility${SHELVES.utility.length}`,
    ]);
    fireEvent.click(screen.getByRole('tab', { name: /Visual/ }));
    expect(container.querySelectorAll('.c-cart[data-mod-id]')).toHaveLength(SHELVES.visual.length);
    escape();
    expect(useVoidStore.getState().modFilter).toBe('all');
    fireEvent.click(container.querySelectorAll('.c-shead__view')[1]!);
    expect(useVoidStore.getState().modFilter).toBe('pvp');
    fireEvent.click(screen.getByRole('tab', { name: /All/ }));
    fireEvent.click(container.querySelector('.c-cart--more')!);
    expect(useVoidStore.getState().modFilter).toBe('hud');
  });

  it('opens a mod from the card body or its settings button, and toggles only from the switch', () => {
    open();
    const { container } = render(<App />);
    const zoomCard = () => container.querySelector('[data-mod-id="zoom"]')!;
    const before = isModOn(useVoidStore.getState().loadout, 'zoom');
    fireEvent.click(zoomCard().querySelector('[role="switch"]')!);
    expect(isModOn(useVoidStore.getState().loadout, 'zoom')).toBe(!before);
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    fireEvent.click(zoomCard().querySelector('.c-cart__hit')!);
    expect(useVoidStore.getState().route).toEqual({ name: 'mod', id: 'zoom' });
    expect(container.querySelector('.c-bench')).not.toBeNull();
    expect(zoomCard().classList.contains('is-sel')).toBe(true);
    escape();
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    fireEvent.click(zoomCard().querySelector('.c-cart__gear')!);
    expect(useVoidStore.getState().route).toEqual({ name: 'mod', id: 'zoom' });
  });

  it('walks with the arrows, toggles with Space, opens with E or Enter, jumps shelves with Tab, pins with P', () => {
    open();
    render(<App />);
    set(() => useVoidStore.getState().selectMod('fps'));
    const before = isModOn(useVoidStore.getState().loadout, 'fps');
    key(' ');
    expect(isModOn(useVoidStore.getState().loadout, 'fps')).toBe(!before);
    key('ArrowRight');
    expect(useVoidStore.getState().selectedMod).toBe(SHELVES.hud[1]);
    key('ArrowDown');
    expect(useVoidStore.getState().selectedMod).toBe(SHELVES.pvp[1]);
    key('Tab');
    expect(useVoidStore.getState().selectedMod).toBe(SHELVES.visual[0]);
    key('Tab', { shiftKey: true });
    expect(useVoidStore.getState().selectedMod).toBe(SHELVES.pvp[0]);
    key('e');
    expect(useVoidStore.getState().route).toEqual({ name: 'mod', id: SHELVES.pvp[0] });
    escape();
    key('Enter');
    expect(useVoidStore.getState().route).toEqual({ name: 'mod', id: SHELVES.pvp[0] });
    escape();
    // P pins — the factory eight are full, so it says so, and pins once there is room.
    const id = SHELVES.pvp[0]!;
    key('p');
    if (!FACTORY_QUICK.includes(id)) expect(useVoidStore.getState().toasts.at(-1)?.title).toContain('full');
    set(() => useVoidStore.getState().setQuickPins([]));
    key('p');
    expect(quickPins(useVoidStore.getState().loadout)).toEqual([id]);
  });

  it('shows an empty Pinned shelf that explains itself', () => {
    open();
    set(() => useVoidStore.getState().setQuickPins([]));
    set(() => useVoidStore.getState().setModFilter('pinned'));
    const { container } = render(<App />);
    expect(container.querySelector('.c-empty')?.textContent).toContain('This shelf is empty');
    fireEvent.click(screen.getByRole('button', { name: 'Browse all shelves' }));
    expect(useVoidStore.getState().modFilter).toBe('all');
  });
});

describe('search', () => {
  it('ranks name starts first and searches every setting', () => {
    expect(searchMods('zoom')[0]).toBe('zoom');
    expect(searchMods('co').slice(0, 2)).toEqual(expect.arrayContaining(['coordinates', 'combo']));
    expect(searchMods('sprint')).toContain('toggle_sprint');
    const loadout = useVoidStore.getState().loadout;
    const hits = searchSettings('colo', (id) => modSettings(loadout, id));
    expect(hits.length).toBeGreaterThan(0);
    expect(hits.every((h) => h.label.toLowerCase().includes('colo'))).toBe(true);
    expect(layoutRows('all', 'zoom', [])[0]!.slots[0]).toBe('zoom');
  });

  it('opens from `/`, marks the match, lists settings, and Escape clears it before closing', () => {
    open();
    const { container } = render(<App />);
    key('/');
    const field = screen.getByLabelText('Search mods and settings') as HTMLInputElement;
    expect(document.activeElement).toBe(field);
    fireEvent.change(field, { target: { value: 'zoom' } });
    expect([...container.querySelectorAll('.c-cart__name')].map((n) => n.textContent)).toContain('Zoom');
    expect(container.querySelector('.c-cart[data-mod-id="zoom"] .c-hl')?.textContent?.toLowerCase()).toBe('zoom');
    fireEvent.change(field, { target: { value: 'colo' } });
    expect(container.querySelectorAll('.c-srow').length).toBeGreaterThan(0);
    // First Escape leaves the field, the next clears the search, the next closes.
    escape();
    expect(useVoidStore.getState().modSearch).toBe('colo');
    expect(keepsEscape()).toBe(true);
    escape();
    expect(useVoidStore.getState().modSearch).toBe('');
    expect(container.querySelectorAll('.c-shelf')).toHaveLength(4);
  });
});

describe('the settings bench', () => {
  it('draws the real widget over a scene with a Day / Night / Nether / Water switch', () => {
    open();
    set(() => useVoidStore.getState().openMod('keystrokes'));
    const { container } = render(<App />);
    const prev = container.querySelector('.c-prev')!;
    expect(prev.getAttribute('data-biome')).toBe('day');
    expect(prev.querySelector('.c-prev__zoom')?.children.length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('radio', { name: 'Nether' }));
    expect(container.querySelector('.c-prev')!.getAttribute('data-biome')).toBe('nether');
  });

  it('offers a control for every property the mod has, and writes what it sets', () => {
    open();
    set(() => useVoidStore.getState().openMod('crosshair'));
    const { container } = render(<App />);
    const text = container.querySelector('.c-bench')!.textContent!;
    for (const label of ['Size', 'Thickness', 'Gap', 'Style', 'Colour', 'Outline']) expect(text).toContain(label);
    fireEvent.click(screen.getByRole('radio', { name: 'Circle' }));
    expect(modSettings(useVoidStore.getState().loadout, 'crosshair').style).toBe('circle');
    fireEvent.click(screen.getByRole('radio', { name: 'Coral' }));
    expect(String(modSettings(useVoidStore.getState().loadout, 'crosshair').color).slice(0, 7)).toBe('#FF9E7A');
    // A meter cell writes its value through the store.
    const cells = container.querySelectorAll('.c-meterblk')[0]!.querySelectorAll('.meter__cell');
    fireEvent.click(cells[cells.length - 1]!);
    expect(Number(modSettings(useVoidStore.getState().loadout, 'crosshair').size)).toBeGreaterThan(5);
    fireEvent.click(screen.getByRole('button', { name: 'Reset' }));
    expect(modSettings(useVoidStore.getState().loadout, 'crosshair').style).toBe(MOD_REGISTRY.crosshair.defaults.style);
  });

  it('a mod with nothing but a key says what it does beside the capture block', () => {
    open();
    set(() => useVoidStore.getState().openMod('toggle_sprint'));
    const { container } = render(<App />);
    expect(container.querySelector('.c-about')?.textContent).toBe(MOD_REGISTRY.toggle_sprint.description);
    expect(screen.getByRole('button', { name: 'Rebind' })).toBeTruthy();
  });

  it('captures a key, warns about a vanilla default, and stores only on Use', async () => {
    open();
    set(() => useVoidStore.getState().openMod('toggle_sprint'));
    let resolve: (key: string | null) => void = () => undefined;
    vi.spyOn(useVoidStore.getState(), 'captureKeybind');
    useVoidStore.setState({ captureKeybind: () => new Promise((r) => (resolve = r as never)) as never });
    const { container } = render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Rebind' }));
    expect(container.querySelector('.c-cap--listen')?.textContent).toContain('Listening');
    await act(async () => {
      resolve('Q');
    });
    expect(screen.getByRole('alert').textContent).toContain('drops your held item');
    expect(modSettings(useVoidStore.getState().loadout, 'toggle_sprint').keybind).not.toBe('Q');
    fireEvent.click(screen.getByRole('button', { name: 'Use Q' }));
    expect(modSettings(useVoidStore.getState().loadout, 'toggle_sprint').keybind).toBe('Q');
    // A key nothing else uses is stored at once.
    fireEvent.click(screen.getByRole('button', { name: 'Rebind' }));
    await act(async () => {
      resolve('J');
    });
    expect(modSettings(useVoidStore.getState().loadout, 'toggle_sprint').keybind).toBe('J');
  });

  it('knows which keys conflict', () => {
    expect(conflictFor('Q', [], 'x')).toMatchObject({ kind: 'vanilla', label: VANILLA_BINDS.Q!.label });
    expect(conflictFor('J', [], 'x')).toBeNull();
    const rows = [{ id: 'a', label: 'Zoom', key: 'C', keyLabel: 'C', mod: 'zoom', setting: 'key', live: true, clashes: [] }] as never;
    expect(conflictFor('C', rows, 'b')).toMatchObject({ kind: 'void', label: 'Zoom' });
    expect(conflictFor('C', rows, 'a')).toBeNull();
  });

  it('sends a HUD mod to the HUD layout with it selected', () => {
    open();
    set(() => useVoidStore.getState().openMod('cps'));
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: /Move in HUD layout/ }));
    expect(useVoidStore.getState().route).toEqual({ name: 'hud-editor' });
    expect(useVoidStore.getState().editorTarget).toBe('cps');
  });
});

describe('live values', () => {
  it('prints a reading for HUD mods and a summary for gameplay mods', () => {
    const s = useVoidStore.getState();
    set(() => useVoidStore.setState({ fps: 142, cpsLeft: 11, cpsRight: 4 }));
    expect(cartValueKey(useVoidStore.getState(), 'fps')).toBe('142\u0000fps');
    expect(cartValueKey(useVoidStore.getState(), 'cps')).toBe('11 · 4\u0000cps');
    set(() => s.setSetting('zoom', 'fov_divisor', 4));
    expect(cartValueKey(useVoidStore.getState(), 'zoom')).toBe('4.0×');
    for (const id of SHELF_MODS) expect(cartValueKey(useVoidStore.getState(), id), id).not.toBe('');
  });
});

describe('the dev hook', () => {
  it('drives only reachable UI state', () => {
    open();
    expect(devGo('search:co')).toBe('ok:search:co');
    expect(useVoidStore.getState().modSearch).toBe('co');
    expect(devGo('settings:keystrokes')).toBe('ok:settings:keystrokes');
    expect(useVoidStore.getState().route).toEqual({ name: 'mod', id: 'keystrokes' });
    expect(devGo('loadouts')).toBe('ok:loadouts');
    expect(devGo('nope')).toMatch(/^error/);
  });
});

describe('in a match — rule 9', () => {
  function fight() {
    const s = useVoidStore.getState();
    s.applyServer({ host: 'mc.hypixel.net', connected: true });
    s.applyTick({ hits: { dealt: 10, taken: 3 } });
    s.applyTick({ hits: { dealt: 11, taken: 3 } });
  }

  it('opens the quick panel only — eight numbered cartridges, no Mods panel, no dim, no palette', () => {
    set(fight);
    open();
    const { container } = render(<App />);
    expect(useVoidStore.getState().context).toBe('match');
    expect(container.querySelector('.c-quick')?.textContent).toContain('Closes when you take a hit');
    expect(container.querySelectorAll('.c-pin')).toHaveLength(FACTORY_QUICK.length);
    expect(container.querySelector('.c-panel')).toBeNull();
    expect(container.querySelector('.c-island--strip')).toBeNull();
    expect(container.querySelector('.menu-layer__dim')).toBeNull();
    key('k', { ctrlKey: true });
    expect(container.querySelector('.palette-layer')).toBeNull();
  });

  it('1–8 flip the pins without the mouse', () => {
    set(fight);
    open();
    render(<App />);
    const id = FACTORY_QUICK[6]!;
    const before = isModOn(useVoidStore.getState().loadout, id);
    key('7');
    expect(isModOn(useVoidStore.getState().loadout, id)).toBe(!before);
    expect(useVoidStore.getState().route.name).toBe('mods');
  });

  it('closes itself the moment the player is hit', () => {
    set(fight);
    open();
    render(<App />);
    const close = vi.spyOn(getVoid(), 'closeMenu');
    set(() => useVoidStore.getState().applyTick({ hits: { dealt: 11, taken: 4 } }));
    expect(close).toHaveBeenCalledTimes(1);
  });

  it('the lobby menu does not close on a hit', () => {
    set(() => useVoidStore.getState().applyServer({ host: 'mc.hypixel.net', connected: true }));
    set(() => useVoidStore.getState().applyTick({ hits: { dealt: 0, taken: 0 } }));
    open();
    render(<App />);
    expect(useVoidStore.getState().context).toBe('lobby');
    const close = vi.spyOn(getVoid(), 'closeMenu');
    set(() => useVoidStore.getState().applyTick({ hits: { dealt: 0, taken: 1 } }));
    expect(close).not.toHaveBeenCalled();
  });

  it('a fight that ended long enough ago is the lobby again', () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000_000);
    set(fight);
    vi.setSystemTime(1_000_000 + COMBAT_WINDOW_MS + 1);
    open();
    expect(useVoidStore.getState().context).toBe('lobby');
  });
});
