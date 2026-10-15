/**
 * The loadout library in game, notices, quick pins and the keybind overview — the depth wave.
 *
 * Run against the real fake bridge (`createFakeVoid`), which implements the library calls with
 * `LiveState`'s rules, so these exercise `bridge.json`'s actual call shapes.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';

import { connectBridge, getVoid, keepsEscape } from '@/bridge/connect';
import {
  FACTORY_QUICK,
  TOAST_MS,
  quickPins,
  resetDerivedState,
  useVoidStore,
  type GlobalsView,
} from '@/store/store';
import { App } from '@/App';
import { IMPORT_ERRORS, nextLoadoutName } from '@/cart/LoadoutsTab';
import { keybindOverview } from '@/menu/keybind-overview';
import type { FakeVoid, Loadout } from '@/bridge/protocol';

function set(mutate: () => void) {
  act(() => {
    mutate();
  });
}

let dispose: () => void;

beforeEach(() => {
  resetDerivedState();
  useVoidStore.setState({
    route: { name: 'mods' },
    back: 'mods',
    paletteOpen: false,
    context: 'lobby',
    selectedMod: 'keystrokes',
    toasts: [],
    server: { host: '', connected: false },
  });
  ({ dispose } = connectBridge({ forceFake: true, runFakeClock: false }));
  set(() => useVoidStore.getState().applyMenu(true));
});

afterEach(() => {
  cleanup();
  dispose();
  vi.useRealTimers();
});

const fake = () => getVoid() as FakeVoid;
const library = () => useVoidStore.getState().library;
const toLoadouts = () => set(() => useVoidStore.getState().setRoute({ name: 'loadouts' }));

describe('the Loadouts screen manages the library', () => {
  it('is tab 2 of the menu, and the status strip names the active loadout', () => {
    const { container } = render(<App />);
    expect(container.querySelector('.c-strip__count')?.textContent).toContain(useVoidStore.getState().loadout!.name);
    fireEvent.click(screen.getByRole('button', { name: /Loadouts/ }));
    expect(useVoidStore.getState().route.name).toBe('loadouts');
    // Escape is "up one level", and the level above tabs 2–5 is the Mods tab.
    expect(keepsEscape()).toBe(true);
    // …and the 2 key gets there too.
    set(() => useVoidStore.getState().setRoute({ name: 'mods' }));
    set(() => {
      fireEvent.keyDown(window, { key: '2' });
    });
    expect(useVoidStore.getState().route.name).toBe('loadouts');
  });

  it('New loadout appends a card at the defaults and opens its name for editing', () => {
    toLoadouts();
    const { container } = render(<App />);
    const before = library().length;
    fireEvent.click(screen.getAllByRole('button', { name: /New loadout/ })[0]!);
    expect(library()).toHaveLength(before + 1);
    expect(library()[before]!.name).toBe('New loadout');
    const field = screen.getByLabelText('Loadout name') as HTMLInputElement;
    fireEvent.change(field, { target: { value: 'Bridge practice' } });
    fireEvent.keyDown(field, { key: 'Enter' });
    expect(library()[before]!.name).toBe('Bridge practice');
    expect(fake().getLoadouts()[before]!.name).toBe('Bridge practice');
    expect(container.querySelectorAll('.c-lcart')).toHaveLength(before + 1);
  });

  it('names new loadouts New loadout, New loadout 2, …', () => {
    const named = (n: string) => ({ name: n }) as Loadout;
    expect(nextLoadoutName([named('A')])).toBe('New loadout');
    expect(nextLoadoutName([named('New loadout'), named('New loadout 2')])).toBe('New loadout 3');
  });

  it('duplicates, copies a code, and deletes in two presses — never the last one', () => {
    toLoadouts();
    const { container } = render(<App />);
    const count = library().length;
    const first = useVoidStore.getState().loadout!;
    // The rack's actions act on the focused cartridge — the one in use until another is clicked.
    fireEvent.click(screen.getByRole('button', { name: 'Duplicate' }));
    expect(library()).toHaveLength(count + 1);
    expect(library()[count]!.name).toBe(`${first.name} copy`);

    fireEvent.click(screen.getByRole('button', { name: 'Copy code' }));
    expect(fake().getClipboard()).toMatch(/^VOID1\./);
    expect(useVoidStore.getState().toasts.at(-1)?.title).toBe('Share code copied');

    // Focus the copy, then delete it in two presses.
    const copy = library()[count]!;
    fireEvent.mouseDown(container.querySelector(`[data-loadout-id="${copy.id}"]`)!);
    const del = () => screen.getByRole('button', { name: /^Delete\??$/ }) as HTMLButtonElement;
    fireEvent.click(del());
    expect(del().textContent).toBe('Delete?');
    expect(library()).toHaveLength(count + 1);
    fireEvent.click(del());
    expect(library()).toHaveLength(count);
    expect(library().some((l) => l.id === copy.id)).toBe(false);
  });

  it('never deletes the last loadout, and Insert switches to a focused one', () => {
    toLoadouts();
    const { container } = render(<App />);
    const others = library().filter((l) => l.id !== useVoidStore.getState().loadout!.id);
    expect(others.length).toBeGreaterThan(0);
    fireEvent.mouseDown(container.querySelector(`[data-loadout-id="${others[0]!.id}"]`)!);
    fireEvent.click(screen.getByRole('button', { name: /Insert/ }));
    expect(useVoidStore.getState().loadout!.id).toBe(others[0]!.id);
    expect(container.querySelector('.c-lcart.is-active')?.getAttribute('data-loadout-id')).toBe(others[0]!.id);
  });

  it('imports a pasted code under a fresh id, and says why a bad one is refused', () => {
    const code = fake().exportLoadout(library()[0]!.id)!;
    toLoadouts();
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: /Import code/ }));
    const field = screen.getByLabelText('Share code') as HTMLInputElement;
    fireEvent.change(field, { target: { value: 'nonsense' } });
    fireEvent.click(screen.getByRole('button', { name: 'Import' }));
    expect(screen.getByRole('alert').textContent).toBe(IMPORT_ERRORS.format);

    const count = library().length;
    fireEvent.change(field, { target: { value: code } });
    fireEvent.click(screen.getByRole('button', { name: 'Import' }));
    expect(library()).toHaveLength(count + 1);
    expect(new Set(library().map((l) => l.id)).size).toBe(count + 1);
    expect(useVoidStore.getState().toasts.at(-1)?.detail).toBe('Loadout imported');
  });

  it('Paste reads the clipboard through Java and imports it', () => {
    fake().setClipboard(fake().exportLoadout(library()[0]!.id)!);
    toLoadouts();
    render(<App />);
    const count = library().length;
    fireEvent.click(screen.getByRole('button', { name: /Import code/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Paste' }));
    expect(library()).toHaveLength(count + 1);
  });
});

describe('notices', () => {
  it('a toast Java pushes is drawn, over the HUD with the menu closed, and leaves on its own', () => {
    vi.useFakeTimers();
    set(() => useVoidStore.getState().applyMenu(false));
    const { container } = render(<App />);
    set(() => fake().emitToast({ kind: 'loadout', title: 'Bedwars', detail: 'Loadout switched' }));
    expect(container.querySelector('.vtoast')?.textContent).toContain('Bedwars');
    expect(container.querySelector('.vtoast')?.textContent).toContain('Loadout switched');
    act(() => {
      vi.advanceTimersByTime(TOAST_MS + 10);
    });
    expect(container.querySelector('.vtoast')).toBeNull();
  });

  it('keeps the newest three when a burst arrives', () => {
    const s = useVoidStore.getState();
    set(() => {
      for (let i = 0; i < 5; i++) s.pushToast({ kind: 'info', title: `n${i}` });
    });
    expect(useVoidStore.getState().toasts.map((t) => t.title)).toEqual(['n2', 'n3', 'n4']);
  });
});

describe('quick pins live on the loadout', () => {
  it('a loadout that never pinned shows the factory eight', () => {
    expect(quickPins(null)).toEqual(FACTORY_QUICK);
    expect(quickPins({ quick: ['zoom', 'cps'] })).toEqual(['zoom', 'cps']);
  });

  it('the mod page pins and unpins, and the quick panel shows the loadout’s own pins', () => {
    set(() => useVoidStore.getState().openMod('ping'));
    const { container, unmount } = render(<App />);
    const pin = screen.getByRole('button', { name: /^(Pin|Unpin)/ });
    // The factory eight are full, so a ninth cannot be pinned until one is removed.
    expect((pin as HTMLButtonElement).disabled).toBe(true);
    set(() => useVoidStore.getState().toggleQuickPin('zoom'));
    expect(quickPins(useVoidStore.getState().loadout)).not.toContain('zoom');
    fireEvent.click(screen.getByRole('button', { name: /^Pin/ }));
    const pins = quickPins(useVoidStore.getState().loadout);
    expect(pins.at(-1)).toBe('ping');
    expect(fake().getLoadout().quick).toEqual([...pins]);
    unmount();

    set(() => {
      useVoidStore.getState().applyMenu(true);
      useVoidStore.getState().setContext('match');
    });
    render(<App />);
    const names = [...document.querySelectorAll('.c-pin__name')].map((n) => n.textContent);
    expect(names).toHaveLength(pins.length);
    expect(names.at(-1)).toBe('Ping display');
    expect(container).toBeTruthy();
  });
});

describe('the keybind overview', () => {
  const globals: GlobalsView = { menuKey: 'RSHIFT', cycleLoadoutKey: 'L', uiScale: 1, hudEditorGrid: 4 };

  it('lists the client keys and every bound mod key, and marks a shared key on both rows', () => {
    const loadout = {
      ...useVoidStore.getState().loadout!,
      mods: { ...useVoidStore.getState().loadout!.mods, fullbright: { on: true, keybind: 'L' } },
    } as Loadout;
    const rows = keybindOverview(loadout, globals);
    expect(rows[0]!.label).toBe('Open this menu');
    const cycle = rows.find((r) => r.id === 'client:cycle')!;
    const bright = rows.find((r) => r.id === 'fullbright.keybind')!;
    expect(cycle.clashes).toEqual([bright.label]);
    expect(bright.clashes).toEqual(['Cycle loadouts']);
    expect(rows.every((r) => r.key !== 'NONE')).toBe(true);
    // A mod that is off is still listed: its toggle key turns it on.
    expect(rows.find((r) => r.id === 'zoom.key')).toBeTruthy();
  });

  it('is drawn on the Keybinds tab with the conflict count, and the cycle key can be rebound or switched off', () => {
    set(() => useVoidStore.getState().setRoute({ name: 'keybinds' }));
    const { container } = render(<App />);
    expect(container.querySelector('.c-kconf')?.textContent).toMatch(/Conflicts\d+/);
    expect(container.querySelectorAll('.c-kbind').length).toBeGreaterThan(2);
    expect(container.querySelector('[data-key="RSHIFT"]')?.classList.contains('is-void')).toBe(true);
    fireEvent.click(container.querySelector('[data-key="L"]')!);
    expect(container.querySelector('.c-kdetail')?.textContent).toContain('Cycle loadouts');
    fireEvent.click(screen.getByRole('button', { name: 'Unbind' }));
    expect(useVoidStore.getState().globals.cycleLoadoutKey).toBe('NONE');
  });

  it('counts a VOID key on a vanilla default as a conflict', () => {
    set(() => useVoidStore.getState().setSetting('zoom', 'key', 'Q'));
    set(() => useVoidStore.getState().setRoute({ name: 'keybinds' }));
    const { container } = render(<App />);
    expect(container.querySelector('[data-key="Q"]')?.classList.contains('is-conflict')).toBe(true);
    expect(Number(container.querySelector('.c-kconf__n')?.textContent)).toBeGreaterThan(0);
  });
});
