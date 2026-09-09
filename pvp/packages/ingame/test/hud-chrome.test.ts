/**
 * The HUD chrome block draws — specifically, `padding` changes a length and not only a class.
 *
 * ## Why this file exists at all
 *
 * `padding` shipped inert. Every step wrote a real value, the store held it, Java echoed it back,
 * the mod page put a selected chip under it, and the widget on screen never changed size — the
 * classes set `padding` on the *slot* `HudSlot` wraps the widget in, while the widget carried its
 * own inset underneath, and at the shipped default (`background: none`) that slot is transparent.
 * A control that moves an invisible edge is `design/rendering-invariants.md` §15's silent failure
 * with a settings row on top of it.
 *
 * **`preview.test.tsx` passed it, and could not have failed it.** That gate renders the preview,
 * writes a different value and diffs the markup — and the markup did change, because the class
 * name on the box changed. A class that matches a rule which draws nothing is, in `innerHTML`,
 * identical to one that draws. Its own doc comment warns that `NOT_IN_THE_PREVIEW` is where the
 * gate erodes; this was the same erosion in the comparison itself, one level up.
 *
 * ## Why this reads the stylesheet instead of rendering
 *
 * The honest assertion is "the resolved inset differs between steps", and jsdom cannot make it:
 * it does not resolve `var()`, so `getComputedStyle` on a rendered chip returns the literal
 * `var(--pad-hud-chip)` at every step. Rendering would produce a test that passes whatever the
 * rules say, which is the failure again.
 *
 * So the rules are the subject. Two claims, and between them they close the loop the class name
 * left open:
 *
 *   1. every step in the schema's enum has a rule, and the rules set *lengths* that differ;
 *   2. every HUD surface reads its density from one of those variables rather than writing its
 *      own — because a widget with a hard-coded inset is a widget this setting cannot reach, and
 *      that is exactly what three of them were.
 */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { MOD_REGISTRY, SETTING_OPTIONS, type ModId } from '@void/protocol';
import { hudChrome } from '@/hud/chrome';

const here = dirname(fileURLToPath(import.meta.url));
const overlayCss = readFileSync(resolve(here, '../src/styles/overlay.css'), 'utf8');
const hudCss = readFileSync(resolve(here, '../../ui/src/styles/06-hud.css'), 'utf8');

/** The three variables a density step is allowed to move. */
const DENSITY_VARS = ['--pad-hud-chip', '--pad-hud-panel', '--gap-hud-keys'] as const;

/** The variable the `border` switch moves. */
const BORDER_VAR = '--border-hud';

/**
 * The declarations of one rule, as `name: value`.
 *
 * A hand-rolled reader rather than a CSS parser: this file is checking two specific rules in two
 * specific stylesheets, and a dependency that can parse all of CSS is a dependency the in-game
 * bundle would then have to keep out of itself.
 */
function ruleBody(css: string, selector: string): string {
  // The selector as it is written, at the start of a line — the stylesheet is authored, not
  // minified, and every rule here stands on its own.
  const at = css.indexOf(`\n${selector} {`);
  if (at < 0) return '';
  const open = css.indexOf('{', at);
  const close = css.indexOf('}', open);
  return css.slice(open + 1, close);
}

function declaration(css: string, selector: string, property: string): string | null {
  const body = ruleBody(css, selector);
  const match = new RegExp(`(?:^|;|\\n)\\s*${property}\\s*:\\s*([^;\\n]+)`).exec(body);
  return match ? match[1]!.trim() : null;
}

/** The `padding` steps, from the registry rather than from a copy of it. */
const PADDING_STEPS: readonly string[] = SETTING_OPTIONS['fps.padding'] ?? [];

describe('the padding steps resolve to lengths', () => {
  it('has a step to test — the shared block still carries `padding`', () => {
    expect(PADDING_STEPS.length).toBeGreaterThanOrEqual(3);
  });

  it.each(PADDING_STEPS)('`%s` sets every density variable', (step) => {
    const selector = `.hud-chrome--pad-${step}`;
    expect(ruleBody(overlayCss, selector), `${selector} has no rule`).not.toBe('');
    for (const name of DENSITY_VARS) {
      expect(declaration(overlayCss, selector, name), `${selector} does not set ${name}`).not.toBe(
        null,
      );
    }
  });

  /**
   * The claim the shipped bug would have failed. Two steps that resolve to the same inset are two
   * steps a player cannot tell apart, which is the difference between a scale and a list of
   * words — and *all* of them resolving to nothing is what the slot-padding version did.
   */
  it.each(DENSITY_VARS)('%s takes a different value at every step', (name) => {
    const values = PADDING_STEPS.map((step) =>
      declaration(overlayCss, `.hud-chrome--pad-${step}`, name),
    );
    expect(new Set(values).size).toBe(PADDING_STEPS.length);
  });

  /**
   * `hudChrome` builds the class name, so a step the stylesheet has no rule for is a step that
   * silently falls back to whatever `.hud-chrome` itself says. Asserted through the real function
   * rather than by string-building the class here, because the spelling is the thing being
   * checked.
   */
  it.each(PADDING_STEPS)('`%s` survives `hudChrome` rather than falling back', (step) => {
    expect(hudChrome({ padding: step })).toContain(`hud-chrome--pad-${step}`);
  });

  /** An unknown value is the factory default, the way `ModRegistry.clamp` treats it. */
  it('falls back to the schema default on a value the registry does not have', () => {
    expect(hudChrome({ padding: 'enormous' })).toContain('hud-chrome--pad-normal');
  });
});

/**
 * `border` draws an edge, and draws it on the box it is the edge of.
 *
 * It shipped invisible for a subtler reason than `padding` did. The rule was real and the
 * property is one Ultralight fully supports — but it was an inset shadow on the *slot*, and the
 * slot is behind the widget, so the edge was painted under the chip's own ground and came
 * through 55% of an opaque black. The mod page could not show it either, because the preview
 * draws `variant="editor"` and that variant carried a hard `1px solid var(--border-dock)` of its
 * own: the switch was toggling an invisible edge underneath a permanent one.
 *
 * Both halves are asserted, because fixing either alone leaves the setting broken.
 */
describe('the border switch draws an edge, on the widget', () => {
  it('sets the variable rather than drawing on the slot', () => {
    expect(declaration(overlayCss, '.hud-chrome--border', BORDER_VAR)).not.toBe(null);
    // The old rule. A box-shadow here is an edge behind the widget again.
    expect(declaration(overlayCss, '.hud-chrome--border', 'box-shadow')).toBe(null);
  });

  it.each([
    ['.v-hudchip', 'box-shadow'],
    ['.v-potionlist', 'border'],
    ['.v-armorlist', 'border'],
  ] as const)('%s draws its edge from the variable', (selector, property) => {
    expect(declaration(hudCss, selector, property)).toContain(`var(${BORDER_VAR}`);
  });

  /**
   * The editor variant may not carry an edge of its own — that is what made the preview lie.
   * The HUD layout editor still outlines every chip; it says so on its own layer, where the
   * claim is true.
   */
  it('leaves the editor variant without a hard edge', () => {
    expect(declaration(hudCss, '.v-hudchip--editor', 'border')).toBe(null);
    expect(declaration(overlayCss, '.hud-layer--editor .hud-chrome', BORDER_VAR)).not.toBe(null);
  });

  /**
   * The two panels have always been drawn with an edge, so their factory `border` has to be on
   * or this fix strips it from every loadout on disk. `shared_overrides` is where that is said.
   */
  it.each(['armor_status', 'potion_effects'] as const)('%s still ships with its edge', (id) => {
    const defaults = MOD_REGISTRY[id].defaults as unknown as Record<string, unknown>;
    expect(defaults.border).toBe(true);
  });

  /** And nothing else changed its mind about the factory default while that was being done. */
  it('leaves every other HUD mod defaulting to no edge', () => {
    const on = (Object.keys(MOD_REGISTRY) as ModId[]).filter((id) => {
      const defaults = MOD_REGISTRY[id].defaults as unknown as Record<string, unknown>;
      return MOD_REGISTRY[id].kind === 'hud' && defaults.border === true;
    });
    expect(on.sort()).toEqual(['armor_status', 'potion_effects']);
  });
});

/**
 * Every HUD surface has to read its density from the block, or the block cannot reach it.
 *
 * These four rules are the ones that had a number written into them, and three of the four are
 * why the setting looked broken on the mods that use them: the armour panel, the potion panel and
 * the editor chip the mod page's own preview draws.
 */
describe('the widgets read their density from the block', () => {
  const READS: ReadonlyArray<readonly [string, string, string]> = [
    ['.v-hudchip', 'padding', '--pad-hud-chip'],
    ['.v-hudchip--editor', 'padding', '--pad-hud-chip'],
    ['.v-potionlist', 'padding', '--pad-hud-panel'],
    ['.v-armorlist', 'padding', '--pad-hud-panel'],
    ['.v-keystrokes', 'gap', '--gap-hud-keys'],
    ['.v-keystrokes__row', 'gap', '--gap-hud-keys'],
  ];

  it.each(READS)('%s takes its %s from %s', (selector, property, variable) => {
    const value = declaration(hudCss, selector, property);
    expect(value, `${selector} has no ${property}`).not.toBe(null);
    expect(value).toContain(`var(${variable}`);
  });
});

/**
 * The block is shared, so every HUD mod carries it — and the point of `_shared.json` is that this
 * cannot be true of eight of nine.
 */
describe('the block reaches every HUD mod', () => {
  const hudMods = (Object.keys(MOD_REGISTRY) as ModId[]).filter(
    (id) => MOD_REGISTRY[id].kind === 'hud',
  );

  it('is not an empty claim', () => {
    expect(hudMods.length).toBeGreaterThan(8);
  });

  it.each(hudMods)('%s has the full chrome block', (id) => {
    const defaults = MOD_REGISTRY[id].defaults as unknown as Record<string, unknown>;
    expect(Object.keys(defaults)).toEqual(
      expect.arrayContaining(['scale', 'opacity', 'background', 'border', 'padding']),
    );
  });
});
