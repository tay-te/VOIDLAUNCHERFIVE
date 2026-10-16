/**
 * The cosmetics half of the bridge: the `cosmetics` and `emote` channels and the four calls
 * behind the in-game wardrobe. The fake has to refuse exactly what Java refuses — an item the
 * account does not own, an id the catalog does not know, an item for another slot — or the
 * wardrobe gets built against a bridge that says yes to everything.
 */

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv, { type AnySchema, type ValidateFunction } from 'ajv';
import { describe, expect, it } from 'vitest';

import {
  COSMETIC_CATALOG,
  FAKE_EMOTE_MS,
  VOID_CALLS,
  VOID_EVENTS,
  createFakeVoid,
  installVoidShim,
} from '../src/index.js';
import type { CosmeticId, CosmeticsPayload, FakeVoid, VoidEnvelope } from '../src/index.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const schemaDir = path.resolve(here, '../../../schema');

const ajv = new Ajv({ allErrors: true, strict: false });
const docs: Record<string, { definitions: Record<string, { enum?: string[] }> }> = {};
for (const name of ['mods', 'loadout', 'protocol', 'bridge']) {
  const doc = JSON.parse(readFileSync(path.join(schemaDir, `${name}.json`), 'utf8'));
  docs[name] = doc;
  ajv.addSchema(doc as AnySchema);
}
const validateEnvelope = ajv.getSchema('https://schema.void.dev/pvp/bridge.json') as ValidateFunction;

function expectValidEnvelope(envelope: VoidEnvelope): void {
  const ok = validateEnvelope(envelope);
  expect(ok, `${JSON.stringify(envelope)}: ${ajv.errorsText(validateEnvelope.errors)}`).toBe(true);
}

function makeFake(): FakeVoid {
  return createFakeVoid({
    seed: 3,
    attachKeyboard: false,
    session: { name: 'Notch', uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5', kind: 'microsoft' },
  });
}

describe('the surface lists', () => {
  it('VOID_EVENTS and VOID_CALLS are bridge.json\'s enums, in order', () => {
    expect([...VOID_EVENTS]).toEqual(docs.bridge!.definitions.event_name!.enum);
    expect([...VOID_CALLS]).toEqual(docs.bridge!.definitions.call_name!.enum);
  });

  it('void-shim.js closes over the same twelve channels and twelve calls', () => {
    // The shim's lists are closed and a missing name fails in silence, so the shipped copy is
    // read back here rather than trusted.
    const shim = readFileSync(
      path.resolve(here, '../../../mod/src/main/resources/assets/void/shim/void-shim.js'),
      'utf8',
    );
    const list = (name: string): string[] => {
      const match = new RegExp(`var ${name} = \\[([^\\]]*)\\]`).exec(shim);
      expect(match, `void-shim.js has no ${name} list`).toBeTruthy();
      return [...match![1]!.matchAll(/'([A-Za-z]+)'/g)].map((m) => m[1]!);
    };
    expect(list('EVENTS')).toEqual([...VOID_EVENTS]);
    expect(list('CALLS')).toEqual([...VOID_CALLS]);
  });
});

describe('createFakeVoid — cosmetics', () => {
  it('pushes a legal cosmetics payload on first paint, free items included', () => {
    const fake = makeFake();
    const seen: CosmeticsPayload[] = [];
    fake.on('cosmetics', (p) => {
      expectValidEnvelope({ e: 'cosmetics', payload: p });
      seen.push(p);
    });
    fake.emitInitialState();
    expect(seen).toHaveLength(1);
    const payload = seen[0]!;
    expect(payload.self.uuid).toBe('069a79f4-44e9-4726-a5be-fca90e38aaf5');
    expect(payload.owned).toContain('origin-cape');
    expect(payload.owned).toContain('void-badge-player');
    expect(payload.owned).not.toContain('atelier-cape');
    // Catalog order, like `CosmeticState.ownedIds`.
    const order = COSMETIC_CATALOG.map((i) => i.id as string);
    expect([...payload.owned].sort((a, b) => order.indexOf(a) - order.indexOf(b))).toEqual(
      payload.owned,
    );
    // Every slot present, as `EquippedSet.toJson` writes it.
    for (const slot of ['cape', 'back', 'head', 'face', 'halo', 'trail', 'badge']) {
      expect(slot in payload.self, slot).toBe(true);
    }
  });

  it('equipCosmetic wears what is owned and refuses the rest, answering what is worn', () => {
    const fake = makeFake();
    expect(fake.equipCosmetic('cape', 'bespoke-cape')).toBe('bespoke-cape');
    // Not owned: refused, and the answer is the cape still worn.
    expect(fake.equipCosmetic('cape', 'atelier-cape')).toBe('bespoke-cape');
    // Owned but for another slot: refused.
    expect(fake.equipCosmetic('cape', 'aureole-halo')).toBe('bespoke-cape');
    // Unknown to the catalog: refused.
    expect(fake.equipCosmetic('cape', 'future-cape' as CosmeticId)).toBe('bespoke-cape');
    // Free items are owned by every account.
    expect(fake.equipCosmetic('cape', 'origin-cape')).toBe('origin-cape');
    // Null takes the slot off.
    expect(fake.equipCosmetic('halo', 'aureole-halo')).toBe('aureole-halo');
    expect(fake.equipCosmetic('halo', null)).toBeNull();
    expect(fake.getCosmetics().self).toMatchObject({ cape: 'origin-cape', halo: null });
  });

  it('a page call pushes no cosmetics event — the return is the notification', () => {
    const fake = makeFake();
    const pushes: unknown[] = [];
    fake.on('cosmetics', (p) => pushes.push(p));
    fake.equipCosmetic('cape', 'bespoke-cape');
    fake.setCosmeticTint('cape', '#123456');
    fake.setEmoteWheel(['emote-wave']);
    expect(pushes).toEqual([]);
    // …but the launcher changing it under the game does.
    fake.applyCosmetics({ owned: ['atelier-cape'] });
    expect(pushes).toHaveLength(1);
    expectValidEnvelope({ e: 'cosmetics', payload: pushes[0] as CosmeticsPayload });
  });

  it('setCosmeticTint sticks only to a tintable item, lower case', () => {
    const fake = makeFake();
    // The default cape, regent-cape, is not tintable: refused, no tint.
    expect(fake.setCosmeticTint('cape', '#9F8BFF')).toBeNull();
    fake.equipCosmetic('cape', 'bespoke-cape');
    expect(fake.setCosmeticTint('cape', '#9F8BFF')).toBe('#9f8bff');
    expect(fake.setCosmeticTint('cape', 'purple')).toBe('#9f8bff');
    expect(fake.getCosmetics().self.tints).toEqual({ cape: '#9f8bff' });
    expect(fake.setCosmeticTint('cape', null)).toBeNull();
    expect(fake.getCosmetics().self.tints).toBeUndefined();
  });

  it('setEmoteWheel keeps owned emotes, once each, at most eight', () => {
    const fake = makeFake();
    expect(
      fake.setEmoteWheel([
        'emote-wave',
        'emote-wave',
        'emote-bow', // not owned
        'gilt-trail', // not an emote
        'emote-sit',
      ] as CosmeticId[]),
    ).toEqual(['emote-wave', 'emote-sit']);
    const all = COSMETIC_CATALOG.filter((i) => i.kind === 'emote').map((i) => i.id);
    fake.applyCosmetics({ owned: all });
    expect(fake.setEmoteWheel([...all, ...all]).length).toBeLessThanOrEqual(8);
  });

  it('playEmote answers true, pushes no start, and pushes the end when the clip runs out', () => {
    const fake = makeFake();
    const seen: unknown[] = [];
    fake.on('emote', (p) => {
      expectValidEnvelope({ e: 'emote', payload: p });
      seen.push(p);
    });
    expect(fake.playEmote('emote-bow')).toBe(false); // not owned
    expect(fake.playEmote('emote-wave')).toBe(true);
    expect(fake.getEmote()).toBe('emote-wave');
    expect(seen).toEqual([]);
    fake.advance(FAKE_EMOTE_MS + 100);
    expect(seen).toEqual([{ id: null }]);
    expect(fake.getEmote()).toBeNull();
  });

  it('an emote Java starts on its own pushes its start; a looping one holds until cancelled', () => {
    const fake = makeFake();
    const seen: unknown[] = [];
    fake.on('emote', (p) => seen.push(p));
    expect(fake.startEmote('emote-sit')).toBe(true);
    fake.advance(FAKE_EMOTE_MS * 3);
    expect(seen).toEqual([{ id: 'emote-sit' }]);
    fake.cancelEmote();
    expect(seen).toEqual([{ id: 'emote-sit' }, { id: null }]);
  });

  it('applyCosmetics reads unknown and misplaced ids as nothing worn', () => {
    const fake = makeFake();
    fake.applyCosmetics({
      self: {
        uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5',
        cape: 'future-cape',
        back: null,
        face: null,
        trail: null,
        badge: null,
        halo: 'regent-cape',
        head: 'atelier-fedora',
        emotes: ['emote-wave'],
        tints: { head: '#ABCDEF' },
      },
    });
    expect(fake.getCosmetics().self).toMatchObject({
      cape: null,
      halo: null,
      head: 'atelier-fedora',
      emotes: ['emote-wave'],
      tints: { head: '#abcdef' },
    });
  });

  it('records every cosmetic call as a legal bridge.json envelope', () => {
    const fake = makeFake();
    fake.equipCosmetic('cape', 'bespoke-cape');
    fake.setCosmeticTint('cape', '#9f8bff');
    fake.setEmoteWheel(['emote-wave', 'emote-clap']);
    fake.playEmote('emote-wave');
    expect(fake.openStore('bespoke-cape')).toBe(true);
    const calls = fake.getCalls();
    expect(calls.map((c) => c.c)).toEqual([
      'equipCosmetic',
      'setCosmeticTint',
      'setEmoteWheel',
      'playEmote',
      'openStore',
    ]);
    for (const call of calls) expectValidEnvelope(call);
  });
});

describe('installVoidShim — cosmetics', () => {
  it('routes the two channels and forwards the four calls', () => {
    const sent: unknown[] = [];
    const answers: Record<string, unknown> = {
      equipCosmetic: 'origin-cape',
      setCosmeticTint: null,
      setEmoteWheel: ['emote-wave'],
      playEmote: true,
      openStore: true,
    };
    const native = (json: string): string => {
      const envelope = JSON.parse(json) as { c: string };
      sent.push(envelope);
      return JSON.stringify({ c: envelope.c, returns: answers[envelope.c] });
    };
    const bridge = installVoidShim({ native, target: null });

    const cosmetics: unknown[] = [];
    const emotes: unknown[] = [];
    bridge.on('cosmetics', (p) => cosmetics.push(p));
    bridge.on('emote', (p) => emotes.push(p));
    const payload: CosmeticsPayload = {
      self: {
        uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5',
        cape: null,
        back: null,
        head: null,
        face: null,
        halo: null,
        trail: null,
        badge: null,
        emotes: [],
      },
      owned: ['origin-cape'],
      catalog_version: 1,
    };
    bridge.__emit({ e: 'cosmetics', payload });
    bridge.__emit({ e: 'emote', payload: { id: null } });
    expect(cosmetics).toEqual([payload]);
    expect(emotes).toEqual([{ id: null }]);

    // The page binds to Java's answer — a refusal answers what is still worn.
    expect(bridge.equipCosmetic('cape', 'atelier-cape')).toBe('origin-cape');
    // Null is a real answer here (no tint), not "no host".
    expect(bridge.setCosmeticTint('cape', '#123456')).toBeNull();
    expect(bridge.setEmoteWheel(['emote-wave', 'emote-bow'])).toEqual(['emote-wave']);
    expect(bridge.playEmote('emote-wave')).toBe(true);
    expect(bridge.openStore('bespoke-cape')).toBe(true);
    expect(sent).toEqual([
      { c: 'equipCosmetic', params: ['cape', 'atelier-cape'] },
      { c: 'setCosmeticTint', params: ['cape', '#123456'] },
      { c: 'setEmoteWheel', params: [['emote-wave', 'emote-bow']] },
      { c: 'playEmote', params: ['emote-wave'] },
      { c: 'openStore', params: ['bespoke-cape'] },
    ]);
  });

  it('with a host that does not answer, nothing plays', () => {
    const bridge = installVoidShim({ native: () => '', target: null });
    expect(bridge.playEmote('emote-wave')).toBe(false);
    // No launcher was reached, so the page must not say the Store opened.
    expect(bridge.openStore('bespoke-cape')).toBe(false);
    expect(bridge.equipCosmetic('cape', 'origin-cape')).toBe('origin-cape');
  });
});
