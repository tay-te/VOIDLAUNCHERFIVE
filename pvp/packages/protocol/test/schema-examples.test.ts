/**
 * Every `examples` entry of every schema must both (a) validate against its own schema
 * and (b) type-check against the generated TypeScript. (b) is enforced at compile time
 * by `src/generated/examples.ts`, which annotates each array with its generated type;
 * if the generator ever drifts from the schema, `pnpm typecheck` fails before this file
 * runs. What is left for runtime is (a), plus the cross-checks JSON Schema cannot state.
 */

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv, { type AnySchema } from 'ajv';
import { describe, expect, it } from 'vitest';

import {
  BRIDGE_EXAMPLES,
  GAMEPLAY_MOD_IDS,
  HUD_MOD_IDS,
  LOADOUT_EXAMPLES,
  MODS_EXAMPLES,
  MOD_IDS,
  MOD_REGISTRY,
  MOD_REGISTRY_DOCUMENT,
  PROTOCOL_EXAMPLES,
  SCHEMA_EXAMPLES,
  hypixelReady,
  isGameplayMod,
  isHudMod,
} from '../src/index.js';
import type { Loadout, ModId } from '../src/index.js';

const schemaDir = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../../schema',
);

const DOCS = ['mods', 'loadout', 'protocol', 'bridge'] as const;

const ajv = new Ajv({ allErrors: true, strict: false });
for (const name of DOCS) {
  const raw = JSON.parse(
    readFileSync(path.join(schemaDir, `${name}.json`), 'utf8'),
  ) as AnySchema;
  ajv.addSchema(raw);
}

const validatorFor = (name: (typeof DOCS)[number]) => {
  const validate = ajv.getSchema(`https://schema.void.dev/pvp/${name}.json`);
  if (!validate) throw new Error(`schema ${name} did not register`);
  return validate;
};

describe('schema examples', () => {
  it.each(DOCS)('every %s.json example validates against its own schema', (name) => {
    const validate = validatorFor(name);
    const examples = SCHEMA_EXAMPLES[name];
    expect(examples.length).toBeGreaterThan(0);
    for (const [index, example] of examples.entries()) {
      const ok = validate(example);
      expect(
        ok,
        `${name}.json examples[${index}] failed: ${ajv.errorsText(validate.errors)}`,
      ).toBe(true);
    }
  });

  it('exposes the same example counts the schema files carry', () => {
    expect(MODS_EXAMPLES).toHaveLength(1);
    expect(LOADOUT_EXAMPLES).toHaveLength(2);
    expect(PROTOCOL_EXAMPLES.length).toBeGreaterThanOrEqual(8);
    expect(BRIDGE_EXAMPLES.length).toBeGreaterThanOrEqual(20);
  });

  it('covers all seven events and all six calls in the bridge examples', () => {
    const events = new Set(
      BRIDGE_EXAMPLES.filter((e): e is Extract<typeof e, { e: string }> => 'e' in e).map(
        (e) => e.e,
      ),
    );
    expect([...events].sort()).toEqual([
      'keys',
      'loadout',
      'loadouts',
      'menu',
      'server',
      'setting',
      'tick',
    ]);

    const calls = new Set(
      BRIDGE_EXAMPLES.filter((e): e is Extract<typeof e, { c: string }> => 'c' in e).map(
        (e) => e.c,
      ),
    );
    expect([...calls].sort()).toEqual([
      'closeMenu',
      'openKeybindCapture',
      'setGameplay',
      'setHud',
      'setModSetting',
      'switchLoadout',
    ]);
  });
});

describe('mod registry', () => {
  it('carries exactly the 12 ids of the mod_id enum', () => {
    expect(Object.keys(MOD_REGISTRY_DOCUMENT.mods).sort()).toEqual([...MOD_IDS].sort());
  });

  it('agrees with hud_mod_id / gameplay_mod_id on every entry kind', () => {
    for (const id of MOD_IDS) {
      const entry = MOD_REGISTRY[id];
      expect(isHudMod(id), `${id} kind=${entry.kind}`).toBe(entry.kind === 'hud');
      expect(isGameplayMod(id), `${id} kind=${entry.kind}`).toBe(entry.kind === 'gameplay');
    }
    expect([...HUD_MOD_IDS, ...GAMEPLAY_MOD_IDS].sort()).toEqual([...MOD_IDS].sort());
  });

  it('validates every entry defaults against that mod settings sub-schema', () => {
    for (const id of MOD_IDS) {
      const validate = ajv.getSchema(
        `https://schema.void.dev/pvp/mods.json#/definitions/${id}_settings`,
      );
      expect(validate, `no settings schema for ${id}`).toBeTruthy();
      const ok = validate!(MOD_REGISTRY[id].defaults);
      expect(ok, `${id} defaults failed: ${ajv.errorsText(validate!.errors)}`).toBe(true);
    }
  });

  it('classifies exactly fullbright and hitboxes as grey', () => {
    const grey = MOD_IDS.filter((id) => MOD_REGISTRY[id].hypixel_safe === 'grey');
    expect(grey).toEqual<ModId[]>(['fullbright', 'hitboxes']);
  });
});

describe('hypixelReady', () => {
  const swordPvp = LOADOUT_EXAMPLES[0] as Loadout;

  it('is true for the Sword PvP example — every enabled mod is safe', () => {
    expect(hypixelReady(swordPvp)).toBe(true);
  });

  it('is false once a grey mod is switched on', () => {
    const withFullbright: Loadout = {
      ...swordPvp,
      mods: { ...swordPvp.mods, fullbright: { on: true, gamma: 10 } },
    };
    expect(hypixelReady(withFullbright)).toBe(false);
  });

  it('ignores grey mods that are present but off', () => {
    expect(swordPvp.mods.hitboxes?.on).toBe(false);
    expect(hypixelReady(swordPvp)).toBe(true);
  });

  it('falls back to registry defaults for mods the loadout omits', () => {
    // Bedwars omits fullbright and hitboxes entirely; both default to off.
    const bedwars = LOADOUT_EXAMPLES[1] as Loadout;
    expect(bedwars.mods.fullbright).toBeUndefined();
    expect(hypixelReady(bedwars)).toBe(true);
  });
});
