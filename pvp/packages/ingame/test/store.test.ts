/**
 * Store reducers against the real fake bridge from `@void/protocol` — the same
 * object the `?debug` harness runs, so these tests exercise the actual call and
 * event shapes of `bridge.json`, not a hand-written mock.
 */

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { connectBridge } from '@/bridge/connect';
import { hudItem, isModOn, modSettings, resetDerivedState, useVoidStore } from '@/store/store';
import type { KeysPayload } from '@/bridge/protocol';

let dispose: () => void;

function keys(patch: Partial<KeysPayload>): KeysPayload {
  return { w: 0, a: 0, s: 0, d: 0, lmb: 0, rmb: 0, space: 0, shift: 0, ...patch };
}

beforeEach(() => {
  resetDerivedState();
  useVoidStore.setState({
    loadout: null,
    library: [],
    keys: keys({}),
    fps: 0,
    fpsLow: 0,
    ping: -1,
    pos: null,
    armor: [],
    fx: [],
    cpsLeft: 0,
    cpsRight: 0,
    menuOpen: false,
    route: { name: 'mods' },
    paletteOpen: false,
  });
  // No timer: every test drives the clock itself.
  ({ dispose } = connectBridge({ forceFake: true, runFakeClock: false }));
});

afterEach(() => dispose());

describe('bridge ingestion', () => {
  it('receives the active loadout and the library on connect', () => {
    const state = useVoidStore.getState();
    expect(state.loadout).not.toBeNull();
    expect(state.library.length).toBeGreaterThan(1);
  });

  it('replaces the loadout wholesale on a switch', () => {
    const other = useVoidStore.getState().library.find(
      (l) => l.id !== useVoidStore.getState().loadout?.id,
    )!;
    useVoidStore.getState().switchLoadout(other.id);
    expect(useVoidStore.getState().loadout?.id).toBe(other.id);
  });

  it('treats an absent tick field as unchanged', () => {
    const apply = useVoidStore.getState().applyTick;
    apply({ fps: 142, ping: 38 });
    apply({ fps: 120 });
    expect(useVoidStore.getState().fps).toBe(120);
    expect(useVoidStore.getState().ping).toBe(38);
  });

  it('keeps the previous armour list on ticks that omit it', () => {
    const apply = useVoidStore.getState().applyTick;
    apply({ armor: [{ slot: 'helmet', item: 'diamond_helmet', damage: 0, max_damage: 363 }] });
    apply({ fps: 100 });
    expect(useVoidStore.getState().armor).toHaveLength(1);
  });

  it('resets to Mods and closes the palette when the menu opens', () => {
    useVoidStore.setState({ route: { name: 'party' }, paletteOpen: true });
    useVoidStore.getState().applyMenu(true);
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    expect(useVoidStore.getState().paletteOpen).toBe(false);
    expect(useVoidStore.getState().menuOpen).toBe(true);
  });
});

describe('CPS derivation through the store', () => {
  it('counts one click per rising edge of lmb', () => {
    const apply = useVoidStore.getState().applyKeys;
    apply(keys({ lmb: 1 }));
    apply(keys({ lmb: 0 }));
    apply(keys({ lmb: 1 }));
    expect(useVoidStore.getState().cpsLeft).toBe(2);
  });

  it('does not count a held button', () => {
    const apply = useVoidStore.getState().applyKeys;
    apply(keys({ lmb: 1 }));
    apply(keys({ lmb: 1, w: 1 }));
    apply(keys({ lmb: 1, w: 0 }));
    expect(useVoidStore.getState().cpsLeft).toBe(1);
  });

  it('counts the two buttons separately', () => {
    const apply = useVoidStore.getState().applyKeys;
    apply(keys({ lmb: 1 }));
    apply(keys({ lmb: 0, rmb: 1 }));
    expect(useVoidStore.getState().cpsLeft).toBe(1);
    expect(useVoidStore.getState().cpsRight).toBe(1);
  });
});

describe('bridge calls', () => {
  it('routes a gameplay mod through setGameplay and stores what Java applied', () => {
    useVoidStore.getState().toggleMod('fullbright', true);
    expect(isModOn(useVoidStore.getState().loadout, 'fullbright')).toBe(true);
  });

  it('routes a HUD mod through setModSetting', () => {
    useVoidStore.getState().toggleMod('coordinates', true);
    expect(isModOn(useVoidStore.getState().loadout, 'coordinates')).toBe(true);
  });

  it('binds a setting to the value Java stored, not the one it sent', () => {
    // `scale` is bounded to [0.25, 4] by mods.json; the fake clamps like Java.
    useVoidStore.getState().setSetting('keystrokes', 'scale', 99);
    expect(modSettings(useVoidStore.getState().loadout, 'keystrokes').scale).toBe(4);
  });

  it('writes a HUD placement back through setHud and keeps what came back', () => {
    useVoidStore.getState().commitHud('keystrokes', 'bottom-right', -40, -40, 1.25);
    const item = hudItem(useVoidStore.getState().loadout, 'keystrokes')!;
    expect(item.anchor).toBe('bottom-right');
    expect(item.scale).toBe(1.25);
  });

  it('clamps an out-of-range placement before it reaches the bridge', () => {
    useVoidStore.getState().commitHud('fps', 'top-left', 99999, -99999, 99);
    const item = hudItem(useVoidStore.getState().loadout, 'fps')!;
    expect(item.dx).toBeLessThanOrEqual(4096);
    expect(item.dy).toBeGreaterThanOrEqual(-4096);
    expect(item.scale).toBeLessThanOrEqual(4);
  });

  it('resets a mod to its registry defaults without touching `on`', () => {
    const before = isModOn(useVoidStore.getState().loadout, 'keystrokes');
    useVoidStore.getState().setSetting('keystrokes', 'opacity', 0.2);
    useVoidStore.getState().resetMod('keystrokes');
    expect(modSettings(useVoidStore.getState().loadout, 'keystrokes').opacity).toBe(0.85);
    expect(isModOn(useVoidStore.getState().loadout, 'keystrokes')).toBe(before);
  });
});

describe('window.void', () => {
  it('is installed on the window', () => {
    expect(typeof window.void?.on).toBe('function');
  });

  it('__hasFocus is false with nothing focused', () => {
    expect(window.void!.__hasFocus()).toBe(false);
  });

  it('__hasFocus is true while a text input has focus', () => {
    const input = document.createElement('input');
    document.body.append(input);
    input.focus();
    expect(window.void!.__hasFocus()).toBe(true);
    input.remove();
  });

  it('__hasFocus stays false for a checkbox — Escape must still close', () => {
    const box = document.createElement('input');
    box.type = 'checkbox';
    document.body.append(box);
    box.focus();
    expect(window.void!.__hasFocus()).toBe(false);
    box.remove();
  });
});
