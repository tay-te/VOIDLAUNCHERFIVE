/**
 * The words on a mod page: every row explained, and nothing explained at length.
 *
 * ## Two failures, and they pull in opposite directions
 *
 * **Nothing said.** `SETTING_HINTS` was seven entries, so seven rows out of a hundred and eighty
 * had a second line and the rest had a title alone. `Block hit`, `Window ms`, `Fov divisor` and
 * six different `Style` rows are titles that mean something only to whoever wrote the schema,
 * and the schema's own descriptions are two-paragraph arguments written for whoever writes the
 * mixin. A page where some rows explain themselves also *implies* the rest are self-evident,
 * which is the wrong claim to make silently.
 *
 * **Too much said.** The gameplay previews' readings had grown into two- and three-sentence
 * paragraphs — `damage_tint` printed forty-four words under a vignette that was already drawn
 * above them. That is not more helpful than a title, it is less: it is read on a page opened
 * mid-session with a game behind it, and the rows below it are what does not get read.
 *
 * Neither failure has a natural gate. Both are the kind of thing that passes review as "it
 * renders", accumulates one row at a time, and is only visible when somebody opens the client
 * and tries to use it. So both are asserted here, against the registry rather than against a
 * list — a mod that gains a setting fails this file until the setting has a line, and a line
 * that grows into a paragraph fails it too.
 *
 * ## What the numbers are
 *
 * They are the width the design already has, not a preference. A hint sits under a row title in
 * the same column the title uses, so a hint longer than a title wraps to two lines and the row
 * grows — {@link HINT_MAX} is a little over the longest title the client draws. A reading is one
 * line under a diagram in a 380px frame at 12px, which holds roughly {@link READING_MAX} per
 * part and {@link READING_LINE_MAX} across the whole `·`-separated line before it takes a third
 * row of the layout.
 */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { MOD_IDS, MOD_REGISTRY, type ModId } from '@void/protocol';
import { SETTING_HINTS, settingHint } from '@/menu/settings-format';
import { SPATIAL_KEYS } from '@/menu/ModSettingsScreen';

/** Longest a row hint may be. One clause, about a title's width. */
const HINT_MAX = 42;

/** Longest one fact in a diagram's reading may be. */
const READING_MAX = 42;

/** Longest a whole reading may be, separators included. */
const READING_LINE_MAX = 100;

/** Most facts one reading may carry before it stops being a line and starts being a list. */
const READING_PARTS_MAX = 4;

/** A single- or back-quoted run. */
const STRING = /'([^'\\]*(?:\\.[^'\\]*)*)'|`([^`\\]*(?:\\.[^`\\]*)*)`/g;

/**
 * The facts inside one reading, with every interpolation stood in for by a three-digit number.
 *
 * `${Math.round(held)}°` is a value the test cannot know, and its *width* is what this file is
 * measuring — so it is replaced by a plausible one rather than dropped, which would let a
 * reading pass on the strength of the numbers it does not print.
 */
function factsIn(block: string): string[] {
  return [...block.matchAll(STRING)].map((m) =>
    (m[1] ?? m[2] ?? '').replace(/\$\{[^}]*\}/g, '000'),
  );
}

/**
 * One reading's array literal, split into its top-level parts.
 *
 * Strings are blanked before the commas are counted, because `'Tap on, tap off'` is one fact
 * carrying a comma and counting punctuation first makes it two.
 */
function partsOf(block: string): string[] {
  const body = block.slice(1, -1);
  const bare = body.replace(STRING, (m) => '\u0000'.repeat(m.length));
  const parts: string[] = [];
  let depth = 0;
  let from = 0;
  for (let i = 0; i < bare.length; i += 1) {
    const ch = bare[i]!;
    if ('([{'.includes(ch)) depth += 1;
    else if (')]}'.includes(ch)) depth -= 1;
    else if (ch === ',' && depth === 0) {
      parts.push(body.slice(from, i));
      from = i + 1;
    }
  }
  parts.push(body.slice(from));
  return parts.filter((part) => part.trim() !== '');
}

/** The keys a mod page draws as rows: everything but enablement and the spatial pair. */
function rowKeys(id: ModId): string[] {
  const defaults = MOD_REGISTRY[id].defaults as unknown as Record<string, unknown>;
  return Object.keys(defaults).filter((key) => key !== 'on' && !SPATIAL_KEYS.has(key));
}

describe('every settings row explains itself', () => {
  const rows = (MOD_IDS as readonly ModId[]).flatMap((id) =>
    rowKeys(id).map((key) => [id, key] as const),
  );

  it('is walking the real registry, not a handful of rows', () => {
    expect(rows.length).toBeGreaterThan(100);
  });

  it.each(rows)('%s.%s has a hint', (id, key) => {
    expect(settingHint(id, key), `no hint for ${id}.${key}`).toBeTruthy();
  });
});

describe('a hint is one short clause', () => {
  const entries = Object.entries(SETTING_HINTS);

  it.each(entries)('%s is at most %i characters', (key, hint) => {
    expect(hint.length, `${key}: "${hint}" is ${hint.length}`).toBeLessThanOrEqual(HINT_MAX);
  });

  /**
   * Sentence punctuation is the tell. A hint that needs a full stop in the middle of it is two
   * hints, and a hint that ends in one has stopped being a label — neither is caught by length
   * alone, because the paragraph always starts as two short sentences.
   */
  it.each(entries)('%s is not written as a sentence', (key, hint) => {
    expect(hint, `${key}: "${hint}"`).not.toMatch(/\.\s|[.;]$/);
  });

  /** Sentence case, like every other label on the page. */
  it.each(entries)('%s starts with a capital', (key, hint) => {
    expect(hint.charAt(0), `${key}: "${hint}"`).toBe(hint.charAt(0).toUpperCase());
  });
});

/**
 * The gameplay diagrams' readings.
 *
 * Read out of the source rather than by rendering, because the assertion is about every branch —
 * `damage_tint` has three states of `camera_shake` and `freelook` three of `perspective`, and a
 * rendered preview shows one of each. A regression here is always a branch somebody added, so
 * the domain that matters is the literals in the file, not the ones the fixture happens to reach.
 */
describe('a diagram reading is facts, not prose', () => {
  const here = dirname(fileURLToPath(import.meta.url));
  const text = readFileSync(resolve(here, '../src/menu/gameplay-previews.tsx'), 'utf8');

  /**
   * Every `<Reading parts={[ ... ]} />` in the file, as its literal array body.
   *
   * A regex rather than a parse: the shape is one JSX element with one prop, written the same
   * way twelve times, and the alternative is a TypeScript compiler in a copy test.
   */
  const blocks = [...text.matchAll(/<Reading\s+parts=\{(\[[\s\S]*?\])\}\s*\/>/g)].map((m) => m[1]!);

  it('found every diagram', () => {
    // Twelve mods draw into the world rather than onto the page and each carries one reading.
    expect(blocks.length).toBeGreaterThanOrEqual(12);
  });

  /** Every quoted run inside a reading, template literals included. */
  const literals = blocks.flatMap(factsIn);

  it('has facts to check', () => {
    expect(literals.length).toBeGreaterThan(24);
  });

  it.each(literals.map((l) => [l]))('"%s" is short enough to be a fact', (literal) => {
    expect(literal.length).toBeLessThanOrEqual(READING_MAX);
  });

  it.each(literals.map((l) => [l]))('"%s" is not a sentence', (literal) => {
    expect(literal).not.toMatch(/\.\s|[.;]$/);
  });

  it.each(blocks.map((b, i) => [i, b] as const))(
    'reading %i carries at most four facts',
    (_i, block) => {
      expect(partsOf(block).length).toBeLessThanOrEqual(READING_PARTS_MAX);
    },
  );

  /**
   * The longest line this reading can actually produce.
   *
   * Per part, not per literal: a part is often a ternary over two or three branches, and only
   * one of them is ever on screen. Summing every literal in the array would fail a reading that
   * is short in all of its states purely for having states, which would push branches back into
   * one long string — the opposite of the change this file exists to hold.
   */
  it.each(blocks.map((b, i) => [i, b] as const))('reading %i fits on a line or two', (_i, block) => {
    const worst = partsOf(block).reduce((total, part) => {
      const longest = factsIn(part).reduce((max, fact) => Math.max(max, fact.length), 0);
      // `·` with three spaces either side, as `Reading` joins them.
      return total + longest + 7;
    }, 0);
    expect(worst).toBeLessThanOrEqual(READING_LINE_MAX);
  });
});
