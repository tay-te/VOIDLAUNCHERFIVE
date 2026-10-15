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
import { IMPORT_ERRORS, nextLoadoutName } from '@/menu/LoadoutsScreen';
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
  it('is one click from the islands: the strip has a Loadouts pill naming the active loadout', () => {
    const { container } = render(<App />);
    const pill = container.querySelector('.isl-pill--loadout') as HTMLElement;
    expect(pill.textContent).toContain('Loadouts');
    expect(pill.textContent).toContain(useVoidStore.getState().loadout!.name);
    fireEvent.click(pill);
    expect(useVoidStore.getState().route.name).toBe('loadouts');
    // Escape is "up one level", and the level above the Loadouts screen is the islands.
    expect(keepsEscape()).toBe(true);
  });

  it('New loadout appends a card at the defaults and opens its name for editing', () => {
    toLoadouts();
    const { container } = render(<App />);
    const before = library().length;
    fireEvent.click(screen.getByRole('button', { name: /New loadout/ }));
    expect(library()).toHaveLength(before + 1);
    expect(library()[before]!.name).toBe('New loadout');
    const field = screen.getByLabelText('Loadout name') as HTMLInputElement;
    fireEvent.change(field, { target: { value: 'Bridge practice' } });
    fireEvent.keyDown(field, { key: 'Enter' });
    expect(library()[before]!.name).toBe('Bridge practice');
    expect(fake().getLoadouts()[before]!.name).toBe('Bridge practice');
    expect(container.querySelectorAll('.v-loadoutcard')).toHaveLength(before + 1);
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
    const first = library()[0]!;
    const card = () => container.querySelectorAll('.v-loadoutcard')[0] as HTMLElement;

    fireEvent.click(card().querySelector('.loadout-actions button:nth-child(2)')!);
    expect(library()).toHaveLength(count + 1);
    expect(library()[count]!.name).toBe(`${first.name} copy`);

    fireEvent.click(screen.getAllByRole('button', { name: 'Copy code' })[0]!);
    expect(fake().getClipboard()).toMatch(/^VOID1\./);
    expect(useVoidStore.getState().toasts.at(-1)?.title).toBe('Share code copied');

    const del = () => card().querySelector('.loadout-actions button:nth-child(4)') as HTMLButtonElement;
    fireEvent.click(del());
    expect(del().textContent).toBe('Delete?');
    expect(library()).toHaveLength(count + 1);
    fireEvent.click(del());
    expect(library()).toHaveLength(count);
    expect(library().some((l) => l.id === first.id)).toBe(false);
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
    const pin = screen.getByRole('button', { name: /Pin to quick panel|Unpin from quick panel/ });
    // The factory eight are full, so a ninth cannot be pinned until one is removed.
    expect((pin as HTMLButtonElement).disabled).toBe(true);
    set(() => useVoidStore.getState().toggleQuickPin('zoom'));
    expect(quickPins(useVoidStore.getState().loadout)).not.toContain('zoom');
    fireEvent.click(screen.getByRole('button', { name: 'Pin to quick panel' }));
    const pins = quickPins(useVoidStore.getState().loadout);
    expect(pins.at(-1)).toBe('ping');
    expect(fake().getLoadout().quick).toEqual([...pins]);
    unmount();

    set(() => {
      useVoidStore.getState().applyMenu(true);
      useVoidStore.getState().setContext('match');
    });
    render(<App />);
    const names = [...document.querySelectorAll('.isl-pin__name')].map((n) => n.textContent);
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

  it('is drawn on the Settings page with the clash count, and the cycle key can be rebound or switched off', () => {
    set(() => useVoidStore.getState().setRoute({ name: 'settings' }));
    const { container } = render(<App />);
    expect(container.querySelector('.oset__binds .oset__cap')?.textContent).toMatch(/bound|clash/);
    expect(container.querySelectorAll('.oset__bind').length).toBeGreaterThan(2);
    fireEvent.click(screen.getByRole('button', { name: 'Off' }));
    expect(useVoidStore.getState().globals.cycleLoadoutKey).toBe('NONE');
    expect(container.querySelector('#nothing')).toBeNull();
  });
});
