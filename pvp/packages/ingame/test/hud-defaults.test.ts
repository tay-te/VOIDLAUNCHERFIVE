/**
 * The factory HUD layout exists twice, and this is what keeps the copies equal.
 *
 * `DEFAULT_HUD` in `src/store/hud-geometry.ts` is what the editor's `Reset layout` writes;
 * `Loadout.DEFAULT_HUD` in `mod/src/main/java/dev/voidpvp/client/state/Loadout.java` is what
 * seeds a brand-new loadout. If they disagree, **Reset stops being an undo and becomes a move** —
 * the client starts in one layout and the button that claims to restore it puts the widgets
 * somewhere else, silently, for everyone.
 *
 * Two hand-maintained tables in two languages have no compiler between them, so this reads the
 * Java source and compares. Rendering-invariants §15: a check that walks the whole domain is the
 * one that catches the *next* mod, so this enumerates `HUD_MOD_IDS` rather than the rows anyone
 * thought to write down — a mod added to the registry and to neither table fails here.
 */

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

import { HUD_MOD_IDS } from '@void/protocol';
import { DEFAULT_HUD } from '@/store/hud-geometry';

// From `packages/ingame`, which is vitest's root here — `import.meta.url` is rewritten to an
// http URL by the jsdom environment, so it cannot be the anchor.
const JAVA = resolve(
  process.cwd(),
  '../../mod/src/main/java/dev/voidpvp/client/state/Loadout.java',
);

/** The rows of `Loadout.DEFAULT_HUD`, as `{ id: { anchor, dx, dy } }`. */
function javaDefaults(): Record<string, { anchor: string; dx: number; dy: number }> {
  const source = readFileSync(JAVA, 'utf8');
  const table = /private static final Object\[\]\[\] DEFAULT_HUD = \{([\s\S]*?)\n {4}\};/.exec(
    source,
  );
  if (!table) throw new Error('Loadout.java no longer declares `Object[][] DEFAULT_HUD`');
  const row =
    /\{"(\w+)",\s*"([\w-]+)",\s*Integer\.valueOf\((-?\d+)\),\s*Integer\.valueOf\((-?\d+)\)\}/g;
  const out: Record<string, { anchor: string; dx: number; dy: number }> = {};
  for (const [, id, anchor, dx, dy] of table[1]!.matchAll(row)) {
    out[id!] = { anchor: anchor!, dx: Number(dx), dy: Number(dy) };
  }
  return out;
}

describe('the factory HUD layout, in both languages', () => {
  it('places every HUD mod — no mod is left for a lookup to miss', () => {
    const java = javaDefaults();
    for (const id of HUD_MOD_IDS) {
      expect(DEFAULT_HUD[id], `${id} is missing from the page's DEFAULT_HUD`).toBeDefined();
      expect(java[id], `${id} is missing from Loadout.DEFAULT_HUD`).toBeDefined();
    }
  });

  it('agrees with Java entry for entry, so Reset is an undo and not a move', () => {
    const java = javaDefaults();
    for (const id of HUD_MOD_IDS) {
      expect(java[id], `${id}`).toEqual(DEFAULT_HUD[id]);
    }
  });

  it('places nothing Java does not, and nothing the registry does not know', () => {
    const java = javaDefaults();
    const ids = new Set<string>(HUD_MOD_IDS);
    expect(Object.keys(java).sort()).toEqual([...ids].sort());
    expect(Object.keys(DEFAULT_HUD).sort()).toEqual([...ids].sort());
  });
});
