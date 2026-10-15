/**
 * The factory HUD layout used to exist twice, by hand, and this file was what kept the copies
 * equal. It no longer exists twice: `default_placement` on each `kind: hud` entry of
 * `schema/mods.json` is the one declaration, and both tables are generated from it —
 * `@void/protocol`'s `DEFAULT_HUD_PLACEMENTS`, which `DEFAULT_HUD` re-exports, and the
 * `place(...)` block of `mod/.../state/ModRegistry.java`, which `Loadout.defaults` seeds a new
 * loadout from.
 *
 * **So this file still has a job, and it is a different one.** Both generated tables are
 * *committed*, because neither Gradle nor Vite may run a Node script; committed means they can
 * be stale, and stale is silent — a `ModRegistry.java` a schema edit left behind still
 * compiles, still ships, and simply places the widgets somewhere else. `--check` on the two
 * generators is the CI gate for that, but it only runs where someone runs it. This is the same
 * assertion expressed against *meaning* rather than bytes, in the test suite, where it also
 * catches the other direction: a generated file edited by hand.
 *
 * What is at stake has not changed. If the page's table and Java's disagree, **`Reset layout`
 * stops being an undo and becomes a move** — the client starts in one layout and the button
 * that claims to restore it puts every widget somewhere else, silently, for everyone. That is
 * why the check is worth keeping after the duplication is gone.
 *
 * Rendering-invariants §15: a check that walks the whole domain is the one that catches the
 * *next* mod, so every assertion here enumerates `MOD_IDS` or `HUD_MOD_IDS` rather than the
 * rows anyone thought to write down.
 */

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

import { HUD_MOD_IDS, MOD_IDS, MOD_REGISTRY } from '@void/protocol';
import { DEFAULT_HUD } from '@/store/hud-geometry';

// From `packages/ingame`, which is vitest's root here — `import.meta.url` is rewritten to an
// http URL by the jsdom environment, so it cannot be the anchor.
const JAVA = resolve(
  process.cwd(),
  '../../mod/src/main/java/dev/voidpvp/client/state/ModRegistry.java',
);

type Placement = { anchor: string; dx: number; dy: number };

/**
 * The `place("fps", "top-left", 23, 23);` rows of the committed `ModRegistry.java`.
 *
 * Parsed out of the Java source rather than run, because there is no JVM here — and because
 * the *committed bytes* are the thing at risk. What the game ships is this file, not the
 * schema it was generated from.
 */
function javaPlacements(): Record<string, Placement> {
  const source = readFileSync(JAVA, 'utf8');
  const row = /^\s*place\("(\w+)",\s*"([\w-]+)",\s*(-?[\d.]+),\s*(-?[\d.]+)\);/gm;
  const out: Record<string, Placement> = {};
  for (const [, id, anchor, dx, dy] of source.matchAll(row)) {
    out[id!] = { anchor: anchor!, dx: Number(dx), dy: Number(dy) };
  }
  if (Object.keys(out).length === 0) {
    throw new Error('ModRegistry.java no longer declares a place(...) table');
  }
  return out;
}

/** The schema's own copy: `default_placement` off the shipped registry entry. */
const schemaPlacements = (): Record<string, Placement> =>
  Object.fromEntries(
    MOD_IDS.map((id) => [id, MOD_REGISTRY[id].default_placement]).filter(
      ([, place]) => place !== undefined,
    ),
  ) as Record<string, Placement>;

describe('the factory HUD layout, from the schema to both tables', () => {
  it('is declared for every HUD mod and for no gameplay mod', () => {
    // The `<id>_entry` narrowings make this a schema error rather than a silent default; this
    // asserts the registry the applications actually read has come out that way.
    const placed = schemaPlacements();
    for (const id of MOD_IDS) {
      const isHud = (HUD_MOD_IDS as readonly string[]).includes(id);
      expect(
        placed[id] !== undefined,
        isHud
          ? `${id} is kind hud and has no default_placement — it would have nowhere to go`
          : `${id} is kind gameplay and carries a default_placement — it draws nothing`,
      ).toBe(isHud);
    }
  });

  it('reaches the page unchanged — DEFAULT_HUD is the schema, entry for entry', () => {
    const placed = schemaPlacements();
    for (const id of HUD_MOD_IDS) {
      expect(DEFAULT_HUD[id], `${id}`).toEqual(placed[id]);
    }
    expect(Object.keys(DEFAULT_HUD).sort()).toEqual([...HUD_MOD_IDS].sort());
  });

  it('reaches Java unchanged, so Reset is an undo and not a move', () => {
    // The committed ModRegistry.java against the schema it is generated from. A stale Java
    // registry is the one failure that survives every other check in this suite: the page would
    // reset to one layout and a fresh loadout would start in another.
    const java = javaPlacements();
    const placed = schemaPlacements();
    for (const id of HUD_MOD_IDS) {
      expect(java[id], `${id} — run \`node scripts/gen-java-registry.mjs\``).toEqual(placed[id]);
    }
    expect(
      Object.keys(java).sort(),
      'ModRegistry.java places something the registry does not know, or misses one it does',
    ).toEqual([...HUD_MOD_IDS].sort());
  });

  it('lays the top row out side by side and the column under it, never overlapping', () => {
    // Cartridge (Figma 630:2): FPS, CPS and Ping are 56 px modules in one row at the top left,
    // and the column starts under that row — the mark first, then coordinates and direction.
    // Written as orderings and gaps rather than numbers, so moving the whole layout stays legal
    // and stacking two modules on each other does not.
    const { fps, cps, ping, watermark, coordinates, direction } = DEFAULT_HUD;
    for (const p of [fps, cps, ping, watermark, coordinates, direction]) expect(p.anchor).toBe('top-left');
    expect(cps.dy).toBe(fps.dy);
    expect(ping.dy).toBe(fps.dy);
    expect(cps.dx - fps.dx, 'CPS clears the 96 px FPS module').toBeGreaterThanOrEqual(96);
    expect(ping.dx - cps.dx, 'Ping clears the 128 px CPS module').toBeGreaterThanOrEqual(128);
    expect(watermark.dy - fps.dy, 'the mark goes under the 56 px row, not over it').toBeGreaterThanOrEqual(56);
    expect(coordinates.dy, 'coordinates take the row after the mark').toBeGreaterThan(watermark.dy);
    expect(direction.dy - coordinates.dy, 'direction takes the next 56 px row').toBeGreaterThanOrEqual(56);
  });
});
