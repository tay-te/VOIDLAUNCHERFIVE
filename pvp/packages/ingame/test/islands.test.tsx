/**
 * The Right-Shift menu — Figma "★ v1.1 · Iterations": `261:50520` (lobby, the final shell with
 * the original centred mod cards) and `229:47020` (in a match, the quick panel), under v1 Ship
 * rule 9.
 *
 * Run against the real fake bridge, like every other screen test, so `switchLoadout`,
 * `setModSetting` and `closeMenu` are the actual call shapes of `bridge.json`.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';

import { connectBridge, getVoid } from '@/bridge/connect';
import { COMBAT_WINDOW_MS, isModOn, resetDerivedState, useVoidStore } from '@/store/store';
import { App } from '@/App';
import { QUICK_MODS, filterMods } from '@/menu/IslandMenu';
import { MOD_REGISTRY } from '@/bridge/protocol';

function set(mutate: () => void) {
  act(() => {
    mutate();
  });
}

const key = (k: string, extra: KeyboardEventInit = {}) =>
  set(() => {
    fireEvent.keyDown(window, { key: k, ...extra });
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

describe('the lobby menu — the final shell', () => {
  it('opens on the islands: mark, tabs, close, the centred Mods panel and the lobby strip', () => {
    open();
    const { container } = render(<App />);
    expect(useVoidStore.getState().context).toBe('lobby');
    expect(container.querySelector('.island--mark')?.textContent).toContain('VOID');
    expect([...container.querySelectorAll('.isl-tab')].map((t) => t.textContent)).toEqual([
      'Mods',
      'Friends',
      'Cosmetics',
    ]);
    expect(container.querySelector('.island--close')?.textContent).toContain('R-Shift');
    expect(container.querySelector('.island--strip')?.textContent).toContain('HUD layout');
    // The world is dimmed behind the lobby menu, and only behind the lobby menu.
    expect(container.querySelector('.menu-layer__dim')).not.toBeNull();
  });

  it('draws the original cards — one live-preview tile per registry mod, five to a row', () => {
    open();
    const { container } = render(<App />);
    const total = Object.keys(MOD_REGISTRY).length;
    expect(container.querySelectorAll('.isl-mods__grid .modcell')).toHaveLength(total);
    const rows = [...container.querySelectorAll('.isl-mods__row')];
    expect(rows.slice(0, -1).every((r) => r.children.length === 5)).toBe(true);
    // A tile is the grid's own: its preview, its name, its switch.
    const fps = container.querySelector('.isl-mods__grid [data-mod-id="fps"]')!;
    expect(fps.querySelector('.modcell__preview')).not.toBeNull();
    expect(fps.querySelector('[role="switch"]')).not.toBeNull();
  });

  it('counts each category the way the registry does, and filters by it', () => {
    open();
    const { container } = render(<App />);
    const counts = [...container.querySelectorAll('.isl-cats__tab')].map((t) => t.textContent);
    expect(counts).toEqual(['All29', 'HUD11', 'PvP11', 'Visual4', 'Utility3']);
    fireEvent.click(screen.getByRole('tab', { name: /Visual/ }));
    expect(container.querySelectorAll('.isl-mods__grid .modcell')).toHaveLength(4);
  });

  it('searches from the button and from `/`, across names and descriptions', () => {
    open();
    const { container } = render(<App />);
    key('/');
    const field = screen.getByLabelText('Search mods') as HTMLInputElement;
    fireEvent.change(field, { target: { value: 'zoom' } });
    expect([...container.querySelectorAll('.isl-mods__grid .modcell__name')].map((n) => n.textContent)).toEqual(['Zoom']);
    fireEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(container.querySelectorAll('.isl-mods__grid .modcell')).toHaveLength(Object.keys(MOD_REGISTRY).length);
    expect(filterMods('all', 'sprint')).toContain('toggle_sprint');
  });

  it('walks the tiles with the arrows, toggles with Space and opens with Enter', () => {
    open();
    render(<App />);
    set(() => useVoidStore.getState().selectMod('fps'));
    const before = isModOn(useVoidStore.getState().loadout, 'fps');
    key(' ');
    expect(isModOn(useVoidStore.getState().loadout, 'fps')).toBe(!before);
    key('ArrowRight');
    const next = useVoidStore.getState().selectedMod;
    expect(next).not.toBe('fps');
    key('Enter');
    expect(useVoidStore.getState().route).toEqual({ name: 'mod', id: next });
    // …and back up to the panel it came from.
    set(() => useVoidStore.getState().closeMod());
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
  });

  it('opens a mod from the card body and toggles it only from the card switch', () => {
    open();
    const { container } = render(<App />);
    const tile = container.querySelector('.isl-mods__grid [data-mod-id="zoom"]')!;
    const before = isModOn(useVoidStore.getState().loadout, 'zoom');
    fireEvent.click(tile.querySelector('[role="switch"]')!);
    expect(isModOn(useVoidStore.getState().loadout, 'zoom')).toBe(!before);
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    fireEvent.click(tile.querySelector('.modcell__select')!);
    expect(useVoidStore.getState().route).toEqual({ name: 'mod', id: 'zoom' });
  });

  it('Tab walks Mods → Friends → Cosmetics, which say they are not connected rather than invent a roster', () => {
    open();
    const { container } = render(<App />);
    key('Tab');
    expect(container.querySelector('.isl-soon__title')?.textContent).toBe('Friends');
    expect(container.textContent).toContain('Not connected yet');
    key('Tab');
    expect(container.querySelector('.isl-soon__title')?.textContent).toBe('Cosmetics');
    key('Tab');
    expect(container.querySelector('.isl-mods__grid')).not.toBeNull();
    key('Tab', { shiftKey: true });
    expect(container.querySelector('.isl-soon__title')?.textContent).toBe('Cosmetics');
  });

  it('the strip opens the HUD editor, which Escape returns from', () => {
    open();
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: /HUD layout/ }));
    expect(useVoidStore.getState().route).toEqual({ name: 'hud-editor' });
    set(() => {
      fireEvent.keyDown(window, { keyCode: 27 });
    });
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    expect(useVoidStore.getState().menuOpen).toBe(true);
  });
});

describe('in a match — rule 9', () => {
  /** Put the player in a fight: on a server, a hit just landed. */
  function fight() {
    const s = useVoidStore.getState();
    s.applyServer({ host: 'mc.hypixel.net', connected: true });
    s.applyTick({ hits: { dealt: 10, taken: 3 } }); // baseline
    s.applyTick({ hits: { dealt: 11, taken: 3 } }); // a hit lands
  }

  it('opens the quick panel only — eight numbered toggles, no Mods panel, no dim, no palette', () => {
    set(fight);
    open();
    const { container } = render(<App />);
    expect(useVoidStore.getState().context).toBe('match');
    expect(container.querySelector('.island--quick')?.textContent).toContain('Closes when you take a hit');
    expect(container.querySelectorAll('.isl-pin')).toHaveLength(QUICK_MODS.length);
    expect(QUICK_MODS).toHaveLength(8);
    expect(container.querySelector('.island--panel')).toBeNull();
    expect(container.querySelector('.island--strip')).toBeNull();
    expect(container.querySelector('.menu-layer__dim')).toBeNull();
    key('k', { ctrlKey: true });
    expect(container.querySelector('.palette-layer')).toBeNull();
  });

  it('1–8 flip the pins without the mouse', () => {
    set(fight);
    open();
    render(<App />);
    const id = QUICK_MODS[6]!;
    const before = isModOn(useVoidStore.getState().loadout, id);
    key('7');
    expect(isModOn(useVoidStore.getState().loadout, id)).toBe(!before);
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

  it('a fight in singleplayer counts too, and no fight at all is the lobby', () => {
    set(() => {
      const s = useVoidStore.getState();
      s.applyServer({ host: '', connected: false });
      s.applyMenu(true);
    });
    expect(useVoidStore.getState().context).toBe('lobby');
    set(() => {
      const s = useVoidStore.getState();
      s.applyMenu(false);
      s.applyTick({ hits: { dealt: 0, taken: 0 } });
      s.applyTick({ hits: { dealt: 1, taken: 0 } });
      s.applyMenu(true);
    });
    expect(useVoidStore.getState().context).toBe('match');
  });
});
