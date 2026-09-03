/**
 * The fake bridge is what every UI surface develops against, so it has to behave like
 * the real one: emit the five channels with on-model payloads, answer the six calls
 * with the value actually applied, and be reproducible under a seed.
 */

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv, { type AnySchema, type ValidateFunction } from 'ajv';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createFakeVoid, installVoidShim } from '../src/index.js';
import type {
  FakeVoid,
  KeysPayload,
  Loadout,
  TickPayload,
  VoidEnvelope,
} from '../src/index.js';

const schemaDir = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../../schema',
);

const ajv = new Ajv({ allErrors: true, strict: false });
for (const name of ['mods', 'loadout', 'protocol', 'bridge']) {
  ajv.addSchema(
    JSON.parse(readFileSync(path.join(schemaDir, `${name}.json`), 'utf8')) as AnySchema,
  );
}
const validateEnvelope = ajv.getSchema(
  'https://schema.void.dev/pvp/bridge.json',
) as ValidateFunction;

/** Assert an envelope is legal per bridge.json. */
function expectValidEnvelope(envelope: VoidEnvelope): void {
  const ok = validateEnvelope(envelope);
  expect(ok, `${JSON.stringify(envelope)}: ${ajv.errorsText(validateEnvelope.errors)}`).toBe(
    true,
  );
}

function makeFake(seed = 7): FakeVoid {
  return createFakeVoid({ seed, attachKeyboard: false });
}

describe('createFakeVoid — events', () => {
  let fake: FakeVoid;
  beforeEach(() => {
    fake = makeFake();
  });

  it('pushes the opening world of state on demand, in hello order', () => {
    const seen: string[] = [];
    fake.on('loadout', () => seen.push('loadout'));
    fake.on('server', () => seen.push('server'));
    fake.on('menu', () => seen.push('menu'));
    expect(seen).toEqual([]);
    fake.emitInitialState();
    expect(seen).toEqual(['loadout', 'server', 'menu']);
  });

  it('emits exactly 20 ticks per simulated second', () => {
    const ticks: TickPayload[] = [];
    fake.on('tick', (t) => ticks.push(t));
    fake.advance(1000);
    expect(ticks).toHaveLength(20);
  });

  it('keeps fps in 130–160 and ping in 40–50 across a long run', () => {
    const ticks: TickPayload[] = [];
    fake.on('tick', (t) => ticks.push(t));
    fake.advance(30_000);
    expect(ticks.length).toBe(600);
    for (const tick of ticks) {
      expect(tick.fps).toBeGreaterThanOrEqual(130);
      expect(tick.fps).toBeLessThanOrEqual(160);
      expect(tick.ping).toBeGreaterThanOrEqual(40);
      expect(tick.ping).toBeLessThanOrEqual(50);
    }
  });

  it('emits tick payloads that validate against bridge.json', () => {
    const ticks: TickPayload[] = [];
    fake.on('tick', (t) => ticks.push(t));
    fake.advance(5_000);
    for (const payload of ticks) expectValidEnvelope({ e: 'tick', payload });
  });

  it('drifts the player position rather than teleporting it', () => {
    const positions: Array<{ x: number; z: number }> = [];
    fake.on('tick', (t) => {
      if (t.pos) positions.push({ x: t.pos.x, z: t.pos.z });
    });
    fake.advance(10_000);
    expect(positions.length).toBeGreaterThan(100);
    for (let i = 1; i < positions.length; i += 1) {
      const dx = Math.abs(positions[i]!.x - positions[i - 1]!.x);
      const dz = Math.abs(positions[i]!.z - positions[i - 1]!.z);
      expect(dx).toBeLessThanOrEqual(0.3);
      expect(dz).toBeLessThanOrEqual(0.3);
    }
    // …and it actually moves.
    const first = positions[0]!;
    const last = positions[positions.length - 1]!;
    expect(Math.hypot(last.x - first.x, last.z - first.z)).toBeGreaterThan(1);
  });

  it('reports armor durability that only ever decreases', () => {
    const helmets: number[] = [];
    fake.on('tick', (t) => {
      const helmet = t.armor?.find((slot) => slot.slot === 'helmet');
      if (helmet?.damage !== undefined) helmets.push(helmet.damage);
    });
    fake.advance(30_000);
    expect(helmets.length).toBeGreaterThan(0);
    for (let i = 1; i < helmets.length; i += 1) {
      expect(helmets[i]!).toBeGreaterThanOrEqual(helmets[i - 1]!);
    }
  });

  it('carries two potion effects whose timers count down', () => {
    const frames: Array<number[]> = [];
    fake.on('tick', (t) => {
      if (t.fx) frames.push(t.fx.map((e) => e.duration_ms));
    });
    fake.advance(5_000);
    expect(frames.length).toBeGreaterThan(1);
    for (const frame of frames) expect(frame).toHaveLength(2);
    expect(frames[frames.length - 1]![0]!).toBeLessThan(frames[0]![0]!);
  });

  it('pushes keys edge-triggered — every push differs from the one before', () => {
    const pushes: KeysPayload[] = [];
    fake.on('keys', (k) => pushes.push(k));
    fake.advance(20_000);
    expect(pushes.length).toBeGreaterThan(10);
    for (let i = 1; i < pushes.length; i += 1) {
      expect(pushes[i]).not.toEqual(pushes[i - 1]);
    }
    for (const push of pushes) {
      expectValidEnvelope({ e: 'keys', payload: push });
      for (const value of Object.values(push)) expect([0, 1]).toContain(value);
    }
  });

  it('is deterministic for a given seed', () => {
    const run = (seed: number): string => {
      const f = createFakeVoid({ seed, attachKeyboard: false });
      const log: unknown[] = [];
      f.on('tick', (t) => log.push(t));
      f.on('keys', (k) => log.push(k));
      f.advance(5_000);
      return JSON.stringify(log);
    };
    expect(run(1234)).toBe(run(1234));
    expect(run(1234)).not.toBe(run(4321));
  });
});

describe('createFakeVoid — calls', () => {
  let fake: FakeVoid;
  beforeEach(() => {
    fake = makeFake();
  });

  it('holds the three default loadouts', () => {
    expect(fake.getLoadouts().map((l) => l.id)).toEqual(['sword-pvp', 'bedwars', 'uhc']);
    expect(fake.getLoadout().id).toBe('sword-pvp');
  });

  it('setGameplay writes the state and returns what was applied', () => {
    expect(fake.setGameplay('fullbright', true)).toBe(true);
    expect(fake.getLoadout().mods.fullbright?.on).toBe(true);
    expect(fake.setGameplay('fullbright', false)).toBe(false);
    expect(fake.getLoadout().mods.fullbright?.on).toBe(false);
  });

  it('setHud snaps to the grid, clamps scale and returns the stored item', () => {
    const stored = fake.setHud('keystrokes', {
      anchor: 'bottom-left',
      dx: 33,
      dy: -41,
      scale: 9,
    });
    expect(stored).toEqual({
      id: 'keystrokes',
      anchor: 'bottom-left',
      dx: 32,
      dy: -40,
      scale: 4,
    });
    expect(fake.getLoadout().hud.find((i) => i.id === 'keystrokes')).toEqual(stored);
  });

  it('setHud appends a HUD item the loadout did not have', () => {
    const before = fake.getLoadout().hud.length;
    fake.setHud('coordinates', { anchor: 'top-left', dx: 20, dy: 56 });
    expect(fake.getLoadout().hud).toHaveLength(before + 1);
  });

  it('setModSetting clamps to the range in mods.json and returns the stored value', () => {
    expect(fake.setModSetting('keystrokes', 'opacity', 1.8)).toBe(1);
    expect(fake.setModSetting('keystrokes', 'opacity', -3)).toBe(0);
    expect(fake.setModSetting('keystrokes', 'scale', 0.1)).toBe(0.25);
    expect(fake.setModSetting('cps', 'window_ms', 99_999)).toBe(5000);
    expect(fake.getLoadout().mods.keystrokes?.opacity).toBe(0);
  });

  it('setModSetting passes non-numeric values through untouched', () => {
    expect(fake.setModSetting('zoom', 'key', 'V')).toBe('V');
    expect(fake.getLoadout().mods.zoom?.key).toBe('V');
  });

  it('switchLoadout applies a known id and emits loadout', () => {
    const seen: Loadout[] = [];
    fake.on('loadout', (l) => seen.push(l));
    expect(fake.switchLoadout('bedwars')).toBe(true);
    expect(fake.getLoadout().id).toBe('bedwars');
    expect(seen.at(-1)?.id).toBe('bedwars');
  });

  it('switchLoadout returns false and changes nothing for an unknown id', () => {
    expect(fake.switchLoadout('does-not-exist')).toBe(false);
    expect(fake.getLoadout().id).toBe('sword-pvp');
  });

  it('closeMenu emits menu:false and returns null', () => {
    fake.setMenuOpen(true);
    const seen: boolean[] = [];
    fake.on('menu', (open) => seen.push(open));
    expect(fake.closeMenu()).toBeNull();
    expect(seen).toEqual([false]);
    expect(fake.isMenuOpen()).toBe(false);
  });

  it('openKeybindCapture resolves with the captured key', async () => {
    const promise = fake.openKeybindCapture('zoom');
    expect(fake.isCapturingKeybind()).toBe(true);
    fake.resolveKeybindCapture('V');
    await expect(promise).resolves.toBe('V');
    expect(fake.isCapturingKeybind()).toBe(false);
  });

  it('openKeybindCapture resolves null when the player cancels', async () => {
    const promise = fake.openKeybindCapture('zoom');
    fake.resolveKeybindCapture(null);
    await expect(promise).resolves.toBeNull();
  });

  it('records every call as a bridge.json call envelope', () => {
    fake.setGameplay('zoom', true);
    fake.closeMenu();
    fake.switchLoadout('uhc');
    for (const call of fake.getCalls()) expectValidEnvelope(call);
    expect(fake.getCalls().map((c) => c.c)).toEqual([
      'setGameplay',
      'closeMenu',
      'switchLoadout',
    ]);
  });

  it('__hasFocus tracks the menu channel', () => {
    expect(fake.__hasFocus()).toBe(false);
    fake.setMenuOpen(true);
    expect(fake.__hasFocus()).toBe(true);
  });

  it('__emit routes an event envelope to its subscribers', () => {
    const seen: boolean[] = [];
    fake.on('menu', (open) => seen.push(open));
    fake.__emit({ e: 'menu', payload: true });
    fake.__emit(JSON.stringify({ e: 'menu', payload: false }));
    expect(seen).toEqual([true, false]);
  });

  it('off and the unsubscribe returned by on both stop delivery', () => {
    const a = vi.fn();
    const b = vi.fn();
    const stopA = fake.on('menu', a);
    fake.on('menu', b);
    stopA();
    fake.off('menu', b);
    fake.__emit({ e: 'menu', payload: true });
    expect(a).not.toHaveBeenCalled();
    expect(b).not.toHaveBeenCalled();
  });
});

describe('createFakeVoid — keyboard', () => {
  it('toggles the menu on Right Shift keydown', () => {
    const target = new EventTarget();
    const fake = createFakeVoid({ seed: 3, keyboardTarget: target });
    const seen: boolean[] = [];
    fake.on('menu', (open) => seen.push(open));

    const press = (code: string): void => {
      const event = new Event('keydown') as Event & { code: string; repeat: boolean };
      event.code = code;
      event.repeat = false;
      target.dispatchEvent(event);
    };

    press('ShiftRight');
    press('KeyW');
    press('ShiftRight');
    expect(seen).toEqual([true, false]);
    fake.destroy();
  });

  it('feeds a pending keybind capture from the next key press', async () => {
    const target = new EventTarget();
    const fake = createFakeVoid({ seed: 3, keyboardTarget: target });
    const promise = fake.openKeybindCapture('zoom');
    const event = new Event('keydown') as Event & { code: string; repeat: boolean };
    event.code = 'KeyV';
    event.repeat = false;
    target.dispatchEvent(event);
    await expect(promise).resolves.toBe('V');
    fake.destroy();
  });
});

describe('installVoidShim', () => {
  /** A stand-in for the Java host: answers call envelopes, echoes what it applied. */
  function nativeHost(overrides: Record<string, unknown> = {}) {
    const seen: unknown[] = [];
    const native = (json: string): string => {
      const envelope = JSON.parse(json) as { c: string; params: unknown[] };
      seen.push(envelope);
      if (envelope.c in overrides) {
        return JSON.stringify({ c: envelope.c, returns: overrides[envelope.c] });
      }
      if (envelope.c === 'openKeybindCapture') return ''; // deferred
      if (envelope.c === 'closeMenu') return JSON.stringify({ c: 'closeMenu', returns: null });
      return JSON.stringify({ c: envelope.c, returns: envelope.params[1] ?? true });
    };
    return { native, seen };
  }

  it('builds a bridge on top of __void_native and returns applied state', () => {
    const host = nativeHost({ setGameplay: false });
    const bridge = installVoidShim({ native: host.native, target: null });
    // The host refused the change; the UI must show what was applied, not what it asked.
    expect(bridge.setGameplay('fullbright', true)).toBe(false);
    expect(host.seen[0]).toEqual({ c: 'setGameplay', params: ['fullbright', true] });
  });

  it('installs itself as target.void', () => {
    const host = nativeHost();
    const target: Record<string, unknown> = {};
    const bridge = installVoidShim({ native: host.native, target });
    expect(target['void']).toBe(bridge);
  });

  it('routes emitted events to handlers and tracks focus', () => {
    const host = nativeHost();
    const bridge = installVoidShim({ native: host.native, target: null });
    const seen: boolean[] = [];
    bridge.on('menu', (open) => seen.push(open));
    bridge.__emit(JSON.stringify({ e: 'menu', payload: true }));
    expect(seen).toEqual([true]);
    expect(bridge.__hasFocus()).toBe(true);
  });

  it('resolves a deferred openKeybindCapture through a call-result envelope', async () => {
    const host = nativeHost();
    const bridge = installVoidShim({ native: host.native, target: null });
    const promise = bridge.openKeybindCapture('zoom');
    bridge.__emit({ c: 'openKeybindCapture', returns: 'V' });
    await expect(promise).resolves.toBe('V');
  });

  it('survives a throwing handler without breaking the push loop', () => {
    const host = nativeHost();
    const onError = vi.fn();
    const bridge = installVoidShim({ native: host.native, target: null, onError });
    const after = vi.fn();
    bridge.on('menu', () => {
      throw new Error('boom');
    });
    bridge.on('menu', after);
    bridge.__emit({ e: 'menu', payload: true });
    expect(onError).toHaveBeenCalledOnce();
    expect(after).toHaveBeenCalledWith(true);
  });

  it('refuses to install without a native entry point', () => {
    expect(() => installVoidShim({ native: undefined, target: null })).toThrow(
      /__void_native/,
    );
  });

  it('agrees with the fake bridge on the shape of the object it builds', () => {
    const host = nativeHost();
    const shim = installVoidShim({ native: host.native, target: null });
    const fake = makeFake();
    const surface = (o: object): string[] =>
      Object.keys(o)
        .filter((k) => typeof (o as Record<string, unknown>)[k] === 'function')
        .sort();
    // Every method of the real shim must exist on the fake, or the harness diverges.
    for (const key of surface(shim)) expect(surface(fake)).toContain(key);
  });
});
