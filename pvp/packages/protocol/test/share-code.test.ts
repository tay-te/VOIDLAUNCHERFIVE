/**
 * Loadout share codes in TypeScript: the fake bridge's codec must read the codes the real
 * codecs (Rust `void_loadout::share`, Java `ShareCode`) make, and its own codes must be ones
 * they can read — which, for stored DEFLATE blocks, any inflater can.
 */

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateRawSync, inflateRawSync } from 'node:zlib';
import { describe, expect, it } from 'vitest';

import {
  createFakeVoid,
  crc32,
  decodeShareCode,
  encodeShareCode,
  inflateRaw,
  resolveModSettings,
  type Loadout,
} from '../src/index.js';

const schemaDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../schema');
const fixtures = JSON.parse(readFileSync(path.join(schemaDir, 'share-code.fixtures.json'), 'utf8')) as {
  codes: {
    made_by: string;
    code: string;
    expect: { name: string; mods: Record<string, Record<string, unknown>>; hud_items: number; quick?: string[] };
  }[];
};

function raw(json: string): string {
  const bytes = Buffer.from(json, 'utf8');
  const body = deflateRawSync(bytes).toString('base64url');
  return `VOID1.${body}.${crc32(new Uint8Array(bytes)).toString(16).padStart(8, '0')}`;
}

describe('share codes', () => {
  it('decodes every fixture code, whoever made it, to what it says', () => {
    expect(fixtures.codes.map((c) => c.made_by)).toEqual(expect.arrayContaining(['rust', 'java']));
    for (const c of fixtures.codes) {
      const d = decodeShareCode(c.code);
      expect(d.ok, c.made_by).toBe(true);
      if (!d.ok) continue;
      expect(d.dropped, c.made_by).toBe(0);
      expect(d.loadout.name).toBe(c.expect.name);
      for (const [id, settings] of Object.entries(c.expect.mods)) {
        const effective = resolveModSettings(d.loadout, id as never) as Record<string, unknown>;
        for (const [key, value] of Object.entries(settings)) {
          expect(effective[key], `${c.made_by} ${id}.${key}`).toEqual(value);
        }
      }
      expect(d.loadout.hud).toHaveLength(c.expect.hud_items);
      if (c.expect.quick) expect(d.loadout.quick).toEqual(c.expect.quick);
    }
  });

  it('writes codes zlib (and so Rust and Java) can read, and reads its own', () => {
    const loadout: Loadout = {
      id: 'x',
      name: 'Bridge practice',
      icon: 'bed',
      server: null,
      mc: '1.8.9',
      mods: { zoom: { on: true, key: 'V', fov_divisor: 6 }, cps: { on: true, mode: 'both' } } as Loadout['mods'],
      hud: [{ id: 'cps', anchor: 'bottom-left', dx: 175, dy: -108 }],
      quick: ['toggle_sprint', 'zoom'],
    };
    const code = encodeShareCode(loadout);
    expect(code).toMatch(/^VOID1\.[A-Za-z0-9_-]+\.[0-9a-f]{8}$/);
    const body = code.split('.')[1]!;
    const json = JSON.parse(inflateRawSync(Buffer.from(body, 'base64url')).toString('utf8'));
    expect(json.mods).toEqual({ zoom: { key: 'V', fov_divisor: 6 }, cps: { mode: 'both' } });
    const back = decodeShareCode(code);
    expect(back.ok && back.loadout.quick).toEqual(['toggle_sprint', 'zoom']);
  });

  it('inflates compressed streams (fixed and dynamic Huffman), not just stored ones', () => {
    const text = JSON.stringify({ a: 'x'.repeat(400), b: Array.from({ length: 200 }, (_, i) => i) });
    const out = inflateRaw(new Uint8Array(deflateRawSync(Buffer.from(text), { level: 9 })));
    expect(out && Buffer.from(out).toString('utf8')).toBe(text);
    const tiny = inflateRaw(new Uint8Array(deflateRawSync(Buffer.from('hi'))));
    expect(tiny && Buffer.from(tiny).toString('utf8')).toBe('hi');
  });

  it('is strict about structure', () => {
    expect(decodeShareCode('  ')).toEqual({ ok: false, error: 'empty' });
    expect(decodeShareCode('hello')).toEqual({ ok: false, error: 'format' });
    expect(decodeShareCode('VOID9.abc.00000000')).toEqual({ ok: false, error: 'version' });
    const good = fixtures.codes[0]!.code;
    const bad = good.slice(0, -1) + (good.endsWith('0') ? '1' : '0');
    expect(decodeShareCode(bad)).toEqual({ ok: false, error: 'checksum' });
    expect(decodeShareCode(raw('[1,2,3]'))).toEqual({ ok: false, error: 'invalid' });
  });

  it('is lenient about content, with the same count as Rust and Java', () => {
    const json =
      '{"name":"  From the future  ","icon":"bed","mc":"1.8.9","reg":999,' +
      '"mods":{"zoom":{"fov_divisor":6,"warp_drive":true},"teleport":{"on":true},' +
      '"fps":{"show_label":"maybe"},"cps":{"window_ms":99999}},' +
      '"hud":[{"id":"fps","anchor":"top-left","dx":4,"dy":4},{"id":"fps","anchor":"top","dx":0,"dy":0},' +
      '{"id":"radar","anchor":"top","dx":0,"dy":0}],' +
      '"quick":["zoom","zoom","teleport","fullbright"]}';
    const d = decodeShareCode(raw(json));
    expect(d.ok).toBe(true);
    if (!d.ok) return;
    expect(d.dropped).toBe(8);
    expect(d.loadout.name).toBe('From the future');
    expect(d.loadout.hud).toHaveLength(1);
    expect(d.loadout.quick).toEqual(['zoom', 'fullbright']);
  });
});

describe('the fake bridge library calls', () => {
  it('create, rename, duplicate, export, import and delete, as LiveState does', () => {
    const v = createFakeVoid({ attachKeyboard: false });
    const pushes: string[] = [];
    v.on('loadouts', () => pushes.push('loadouts'));
    const first = v.getLoadouts()[0]!;

    const created = v.createLoadout(`  ${first.name} `);
    expect(created?.id).toBe(`${first.id}-2`);
    expect(v.renameLoadout(created!.id, ' Mine ')).toBe('Mine');
    const copy = v.duplicateLoadout(created!.id);
    expect(copy?.name).toBe('Mine copy');

    const code = v.exportLoadout(first.id)!;
    const imported = v.importLoadout(code);
    expect(imported.ok).toBe(true);
    expect(v.importLoadout('nope')).toEqual({ ok: false, error: 'format' });

    expect(v.deleteLoadout(first.id)).toBe(true);
    expect(v.getLoadout().id).not.toBe(first.id);
    expect(v.setQuickPins(['zoom', 'zoom', 'cps'] as never)).toEqual(['zoom', 'cps']);
    expect(pushes.length).toBe(5);
    v.destroy();
  });
});
