/**
 * `MOD_ICON_NAMES` — the generated table against the source it is generated from.
 *
 * The compiler already proves most of this: `generated/icons.ts` ends
 * `as const satisfies Record<ModId, string>`, so a missing mod is a type error here and a
 * name that cannot be *drawn* is a type error in `@void/ui`, which is the only package that
 * knows what a drawing is. What a type cannot prove is that the committed output still
 * matches `schema/mods/<id>.json` — `pnpm gen` is a step somebody runs, and the file it
 * writes is committed. So this reads the schema off disk, the way `schema-examples.test.ts`
 * does, and compares.
 *
 * It matters more than the usual staleness check because the miss is silent in both
 * directions: a stale table draws the *previous* glyph, which is a plausible icon in the
 * wrong place — `design/rendering-invariants.md` §15's whole subject.
 */

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { MOD_ICON_NAMES, MOD_IDS, MOD_REGISTRY, MOD_REGISTRY_DOCUMENT } from '../src/index.js';
import type { ModId } from '../src/index.js';

const schemaDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../schema');

/** `mod_entry.icon`'s own pattern, as `mods.json` states it. */
const ICON_PATTERN = /^[a-z][a-z0-9-]*$/;

interface ModsDocument {
  definitions: { mod_entry: { required: string[]; properties: { icon: { pattern: string } } } };
  examples: { mods: Record<string, { icon?: string }> }[];
}

const modsSchema = JSON.parse(
  readFileSync(path.join(schemaDir, 'mods.json'), 'utf8'),
) as ModsDocument;

describe('MOD_ICON_NAMES', () => {
  it('names an icon for every mod, and only for mods', () => {
    expect(Object.keys(MOD_ICON_NAMES)).toEqual([...MOD_IDS]);
  });

  it('is the registry entry it was lifted from, mod for mod', () => {
    for (const id of MOD_IDS) {
      expect(MOD_ICON_NAMES[id], id).toBe(MOD_REGISTRY[id].icon);
      expect(MOD_ICON_NAMES[id], id).toBe(MOD_REGISTRY_DOCUMENT.mods[id].icon);
    }
  });

  it('still matches the schema on disk — the gate on a stale `pnpm gen`', () => {
    const shipped = modsSchema.examples[0]!.mods;
    for (const id of MOD_IDS) {
      expect(shipped[id]?.icon, id).toBe(MOD_ICON_NAMES[id]);
    }
    // …and the other direction, so a mod added to the schema without regenerating fails here
    // rather than at the call site that indexes a table with no row for it.
    expect(Object.keys(shipped).sort()).toEqual([...MOD_IDS].sort());
  });

  it('is a required, pattern-constrained field of `mod_entry`', () => {
    // `icon` being *required* is what makes the generator's completeness check meaningful:
    // an optional field would let a mod ship with no icon and no error anywhere.
    expect(modsSchema.definitions.mod_entry.required).toContain('icon');
    expect(modsSchema.definitions.mod_entry.properties.icon.pattern).toBe(ICON_PATTERN.source);
    for (const id of MOD_IDS) {
      expect(MOD_ICON_NAMES[id], id).toMatch(ICON_PATTERN);
    }
  });

  it('keeps each value at its literal type', () => {
    // Not a runtime assertion so much as a compiled one: these only typecheck because
    // `generated/icons.ts` is `as const`. Widen it and `@void/ui`'s single
    // `satisfies Record<ModId, IconName>` stops proving anything, because `string` is
    // assignable nowhere near `IconName`. The `@ts-expect-error` is the load-bearing half.
    const gauge: 'gauge' = MOD_ICON_NAMES.fps;
    expect(gauge).toBe('gauge');
    // @ts-expect-error — `fps` is `'gauge'`, not any string: the literal type is the contract.
    const wrong: 'keyboard' = MOD_ICON_NAMES.fps;
    expect(wrong).toBe('gauge');
  });

  it('is indexable by a `ModId` that is only known at runtime', () => {
    // What every consumer actually does. Fails to compile if the table is keyed by anything
    // narrower than the full `ModId` union.
    const lookup = (id: ModId): string => MOD_ICON_NAMES[id];
    expect(lookup('watermark')).toBe('watermark');
  });
});
