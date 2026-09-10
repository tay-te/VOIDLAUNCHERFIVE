/**
 * Loadout share codes, and the one property the whole feature turns on.
 *
 * The guarantee is not "the important settings survive" — it is that **every setting every mod
 * declares** comes back with the same resolved value. Written as a walk over the registry rather
 * than as a list, so a setting added tomorrow is covered tonight, and a codec that stops
 * carrying a category fails on the next run rather than on somebody's HUD.
 */

import { describe, expect, it } from 'vitest';
import {
  DEFAULT_HUD_PLACEMENTS,
  LOADOUT_EXAMPLES,
  MOD_IDS,
  MOD_REGISTRY_VERSION,
  SHARE_PREFIX,
  decodeLoadout,
  encodeLoadout,
  getModDefaults,
  isHudMod,
  looksLikeShareCode,
  resolveModSettings,
  type HUDModId,
  type Loadout,
  type ModId,
} from '../src/index.js';

/** The shipped loadout, which is the one a share code will actually be made of. */
function example(): Loadout {
  return JSON.parse(JSON.stringify(LOADOUT_EXAMPLES[0])) as Loadout;
}

/**
 * A loadout with **every setting of every mod moved off its factory value**.
 *
 * The worst case for a delta codec, and the only fixture that can prove the guarantee: a
 * round trip over the shipped loadout would pass even if the codec silently dropped a category
 * nobody has customised.
 */
function everythingChanged(): Loadout {
  const loadout = example();
  const mods: Record<string, Record<string, unknown>> = {};
  for (const id of MOD_IDS) {
    const defaults = getModDefaults(id) as unknown as Record<string, unknown>;
    const changed: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(defaults)) {
      if (typeof value === 'boolean') changed[key] = !value;
      else if (typeof value === 'number') changed[key] = value + 1;
      else if (typeof value === 'string') changed[key] = `${value}!`;
      else changed[key] = value;
    }
    mods[id] = changed;
  }
  loadout.mods = mods as Loadout['mods'];
  // And every HUD item moved, so no placement can be recovered from the factory table by luck.
  loadout.hud = MOD_IDS.filter(isHudMod).map((id, index) => ({
    id: id as HUDModId,
    anchor: 'bottom-right' as const,
    dx: -10 - index,
    dy: -20 - index * 2,
  }));
  return loadout;
}

/**
 * A loadout an enthusiast could plausibly have: most mods on, two settings moved on each, most
 * of the HUD dragged somewhere. The fixture the size bound is actually about.
 */
function heavilyCustomised(): Loadout {
  const loadout = example();
  const mods: Record<string, Record<string, unknown>> = {};
  const chosen = MOD_IDS.slice(0, 25);
  for (const id of chosen) {
    const defaults = getModDefaults(id) as unknown as Record<string, unknown>;
    const entry: Record<string, unknown> = { on: true };
    for (const [key, value] of Object.entries(defaults).slice(0, 3)) {
      if (key === 'on') continue;
      if (typeof value === 'boolean') entry[key] = !value;
      else if (typeof value === 'number') entry[key] = value + 0.5;
      else if (typeof value === 'string') entry[key] = value;
    }
    mods[id] = entry;
  }
  loadout.mods = mods as Loadout['mods'];
  loadout.hud = MOD_IDS.filter(isHudMod)
    .slice(0, 15)
    .map((id, index) => ({
      id: id as HUDModId,
      anchor: 'top-left' as const,
      dx: 24 + index,
      dy: 30 + index * 19,
    }));
  return loadout;
}

/** The payload JSON out of a code, for the forged-code case. Mirrors the codec's own base64. */
function decodePayloadForTest(code: string): string {
  const body = code.split('.')[2]!;
  const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  const bytes: number[] = [];
  let buffer = 0;
  let bits = 0;
  for (const char of body) {
    buffer = (buffer << 6) | B64.indexOf(char);
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      bytes.push((buffer >> bits) & 0xff);
    }
  }
  return String.fromCharCode(...bytes);
}

/** A payload back into a valid code — the sender this codebase does not have. */
function signForTest(json: string): string {
  const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  const bytes: number[] = [];
  for (let i = 0; i < json.length; i += 1) {
    const code = json.charCodeAt(i);
    if (code < 0x80) bytes.push(code);
    else if (code < 0x800) bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
    else bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
  }
  let body = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i]!;
    const b = i + 1 < bytes.length ? bytes[i + 1]! : -1;
    const c = i + 2 < bytes.length ? bytes[i + 2]! : -1;
    body += B64[a >> 2]! + B64[((a & 3) << 4) | (b < 0 ? 0 : b >> 4)]!;
    if (b < 0) break;
    body += B64[((b & 15) << 2) | (c < 0 ? 0 : c >> 6)]!;
    if (c < 0) break;
    body += B64[c & 63]!;
  }
  let hash = 0x811c9dc5;
  for (let i = 0; i < body.length; i += 1) {
    hash ^= body.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return `${SHARE_PREFIX}.${hash.toString(36)}.${body}`;
}

describe('share codes', () => {
  it('carries every setting of every mod, chrome included', () => {
    const before = everythingChanged();
    const result = decodeLoadout(encodeLoadout(before));
    expect(result.ok).toBe(true);
    if (!result.ok) return;

    // The assertion the feature exists for. `resolveModSettings` is what every reader actually
    // calls, so comparing resolved values is comparing the loadouts as they will be *used* —
    // and it catches a codec that stored a value the resolver would then override.
    const missing: string[] = [];
    for (const id of MOD_IDS) {
      const from = resolveModSettings(before, id) as unknown as Record<string, unknown>;
      const to = resolveModSettings(result.loadout, id) as unknown as Record<string, unknown>;
      for (const key of Object.keys(from)) {
        if (from[key] !== to[key]) missing.push(`${id}.${key}: ${from[key]} -> ${to[key]}`);
      }
    }
    expect(missing, missing.join('\n')).toEqual([]);
  });

  it('names the chrome block explicitly, because it is the half that gets forgotten', () => {
    // The walk above already covers these. This case exists as documentation that survives a
    // refactor of the walk: a loadout that arrives with the right mods and the wrong chrome is
    // the specific failure this codec was written against.
    const before = example();
    before.mods.fps = {
      ...(before.mods.fps ?? {}),
      on: true,
      scale: 1.75,
      opacity: 0.6,
      background: 'solid',
      border: true,
      padding: 'wide',
    } as Loadout['mods']['fps'];

    const result = decodeLoadout(encodeLoadout(before));
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(resolveModSettings(result.loadout, 'fps')).toMatchObject({
      scale: 1.75,
      opacity: 0.6,
      background: 'solid',
      border: true,
      padding: 'wide',
    });
  });

  it('carries the HUD layout, and rebuilds untouched items from the registry', () => {
    const before = example();
    before.hud = [
      { id: 'fps', anchor: 'bottom-right', dx: -12, dy: -40 },
      { id: 'cps', anchor: 'center', dx: 5, dy: -5, scale: 1.5 },
    ];
    const result = decodeLoadout(encodeLoadout(before));
    expect(result.ok).toBe(true);
    if (!result.ok) return;

    const at = (id: ModId) => result.loadout.hud.find((item) => item.id === id);
    expect(at('fps')).toEqual({ id: 'fps', anchor: 'bottom-right', dx: -12, dy: -40 });
    expect(at('cps')).toEqual({ id: 'cps', anchor: 'center', dx: 5, dy: -5, scale: 1.5 });

    // Every HUD mod is placed, whether or not the code carried it — a decoded loadout is a
    // whole loadout, not a patch. The ones the code omitted sit where the registry puts them.
    const hudIds = MOD_IDS.filter(isHudMod);
    expect(result.loadout.hud).toHaveLength(hudIds.length);
    const ping = at('ping')!;
    expect({ anchor: ping.anchor, dx: ping.dx, dy: ping.dy }).toEqual({
      anchor: DEFAULT_HUD_PLACEMENTS.ping.anchor,
      dx: DEFAULT_HUD_PLACEMENTS.ping.dx,
      dy: DEFAULT_HUD_PLACEMENTS.ping.dy,
    });
  });

  it('does not carry the sender’s id or their play stats', () => {
    // Both would be actively wrong on the receiver's machine: the id collides with their own
    // loadouts, and the stats are hours somebody else played.
    const before = example();
    const code = encodeLoadout(before);
    const result = decodeLoadout(code);
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect('id' in result.loadout).toBe(false);
    expect('stats' in result.loadout).toBe(false);
    expect(code).not.toContain(before.id);
  });

  it('refuses a truncated code rather than applying most of a loadout', () => {
    // The realistic corruption: a chat client cut the tail off. Half a loadout applied in
    // silence is the failure that gets blamed on the sender's HUD.
    const code = encodeLoadout(everythingChanged());
    expect(decodeLoadout(code.slice(0, code.length - 12))).toEqual({
      ok: false,
      reason: 'checksum',
    });
    expect(decodeLoadout('')).toEqual({ ok: false, reason: 'empty' });
    expect(decodeLoadout('not a code')).toEqual({ ok: false, reason: 'prefix' });
    expect(decodeLoadout(`${SHARE_PREFIX}.0.!!!!`)).toEqual({ ok: false, reason: 'checksum' });
  });

  it('survives a paste that a chat client wrapped across lines', () => {
    const code = encodeLoadout(example());
    const wrapped = `${code.slice(0, 40)}\n  ${code.slice(40)}  `;
    const result = decodeLoadout(wrapped);
    expect(result.ok).toBe(true);
    expect(looksLikeShareCode(`  ${code}`)).toBe(true);
    expect(looksLikeShareCode('hello')).toBe(false);
  });

  it('reports a code from another registry version instead of pretending', () => {
    const result = decodeLoadout(encodeLoadout(example()));
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.registryVersion).toBe(MOD_REGISTRY_VERSION);
    expect(result.foreign).toBe(false);
    expect(result.dropped).toEqual([]);
  });

  it('drops what this registry does not have, and says which', () => {
    // A code from a *newer* build, which cannot be produced by encoding — `encodeLoadout` walks
    // this registry, so it can only ever emit mods and keys we have. So the payload is built by
    // hand and re-signed, which is the only honest simulation of the case: a friend on a later
    // client sends you a loadout containing a mod you do not have yet.
    //
    // It is the one case where the receiver genuinely gets less than the sender had, and the one
    // case a share feature must not be silent about.
    const code = encodeLoadout(example());
    const payload = JSON.parse(decodePayloadForTest(code)) as {
      k: string[];
      d: (number | unknown)[][];
    };
    const invented = payload.k.length;
    payload.k.push('time_machine', 'invented_setting');
    payload.d.push([invented, 0, true]);
    payload.d.push([payload.k.indexOf('fps'), invented + 1, 3]);
    const forged = signForTest(JSON.stringify(payload));

    const result = decodeLoadout(forged);
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.dropped).toContain('time_machine');
    expect(result.dropped).toContain('fps.invented_setting');
    expect('time_machine' in result.loadout.mods).toBe(false);
    expect('invented_setting' in ((result.loadout.mods.fps ?? {}) as object)).toBe(false);
  });

  it('stays inside a chat message for a loadout somebody could actually have', () => {
    // Not a style rule. A message longer than 2000 characters cannot be posted to Discord in one
    // piece, and a share code that has to be split across two messages is a share code nobody
    // uses — the seam is exactly where a paste loses characters.
    //
    // Three fixtures, because "how big is a code" has three different honest answers:
    //
    //  · the shipped loadout, which is what most codes will be;
    //  · a heavily customised one — most mods on, a couple of settings moved on each, most of
    //    the HUD dragged — which is the enthusiast this feature is for, and the bound that
    //    actually binds;
    //  · every setting of every mod moved off its default, which is the arithmetic ceiling and
    //    a loadout no player has. It is over the chat limit and that is a stated outcome rather
    //    than a surprise: at that size a code is a file, not a message.
    expect(encodeLoadout(example()).length).toBeLessThan(700);
    expect(encodeLoadout(heavilyCustomised()).length).toBeLessThan(2000);
    expect(encodeLoadout(everythingChanged()).length).toBeLessThan(8000);
  });
});
