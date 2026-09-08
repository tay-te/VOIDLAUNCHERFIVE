/**
 * The mod page's preview must move when a setting moves.
 *
 * ## Why this test walks the domain
 *
 * A preview that ignores its settings is the worst failure this page has, and it is invisible
 * to every other kind of test: it renders, it looks right in a screenshot, and it is only wrong
 * *relative to a change nobody made in the test*. It is the exact shape of
 * `design/rendering-invariants.md` §15 — "a lookup whose miss produces silence must be gated by
 * a test that enumerates the domain, not by a test of the case somebody thought of".
 *
 * So this enumerates. For every mod that claims a live widget, and for every setting that mod
 * has, it renders the preview, writes a different legal value, and asserts the **drawing**
 * changed. Not the caption — the caption already printed `scale` and `opacity` while the
 * picture sat still, which is how those two went unnoticed for as long as they did — so the
 * comparison is scoped to `.preview__stage`.
 *
 * ## The two lists
 *
 * {@link LIVE} and {@link NOT_IN_THE_PREVIEW} are both **total and explicit**, which is the
 * point. A mod moves into `LIVE` when its preview becomes the real widget; from that moment
 * every one of its settings has to prove itself here or be written down as one that cannot,
 * with the reason. Neither list can be satisfied by accident.
 */

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { act, cleanup, render } from '@testing-library/react';

import { connectBridge } from '@/bridge/connect';
import { modSettings, resetDerivedState, useVoidStore, type SettingValue } from '@/store/store';
import { MOD_IDS, MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { SETTING_ENUMS, SETTING_RANGES } from '@/registry';
import { LIVE_WIDGET_IDS, ModPreview } from '@/menu/ModSettingsScreen';

let dispose: () => void;

beforeEach(() => {
  resetDerivedState();
  ({ dispose } = connectBridge({ forceFake: true, runFakeClock: false }));
});

afterEach(() => {
  cleanup();
  dispose();
});

/**
 * The mods whose preview is the real HUD widget.
 *
 * Stated here as well as in the source so that moving a mod is a change to a test, not only a
 * change to a table — the whole value of this file is that the list cannot grow quietly.
 */
const LIVE: readonly ModId[] = [
  'fps',
  'keystrokes',
  'cps',
  'ping',
  'coordinates',
  'armor_status',
  'potion_effects',
  'watermark',
  'crosshair',
  'toggle_sprint',
  'fullbright',
  'hitboxes',
  'zoom',
];

/**
 * Settings a live preview provably cannot show, and why.
 *
 * Short on purpose. Every entry is a small admission that a control on the page does nothing
 * you can see, so each one has to justify itself; "it was hard" is not a reason that belongs
 * here. Both of these are keys whose whole effect is outside the panel.
 */
const NOT_IN_THE_PREVIEW: Record<string, string> = {
  // A hotkey has no appearance. The chip beside it *is* its feedback.
  'keystrokes.keybind': 'a keybind has no drawn form',
  'zoom.key': 'a keybind has no drawn form',
  // Both are temporal: `smooth` eases the transition into the zoom, `cinematic` damps the
  // camera while it is held. A preview that does not animate cannot show either, and one that
  // did would spend the §4 budget (0 paints/s on an idle open menu) to say "this is smooth".
  'zoom.smooth': 'a transition, and this preview does not animate',
  'zoom.cinematic': 'camera damping, and this preview does not animate',
  // The window over which clicks are averaged. It changes how fast the figure responds, not
  // what the figure is, and the preview's clicks are a fixture rather than a stream.
  'cps.window_ms': 'a response time, not a value — nothing is clicking on a settings page',
};

/** A legal value for `key` that differs from `current`. */
function otherValue(id: ModId, key: string, current: SettingValue): SettingValue | null {
  if (typeof current === 'boolean') return !current;
  const options = SETTING_ENUMS[`${id}.${key}`];
  if (options) return options.find((option) => option !== current) ?? null;
  const range = SETTING_RANGES[key];
  if (range && typeof current === 'number') return current === range.max ? range.min : range.max;
  // `hex_color`, and the two keycap swatch enums, which are deliberately outside
  // `SETTING_ENUMS` because the page draws them as swatches rather than as chips.
  if (key === 'color') return current === '#FF9E7A' ? '#7ADFFF' : '#FF9E7A';
  if (key === 'key_color') return current === 'sky' ? 'teal' : 'sky';
  if (key === 'pressed_color') return current === 'sky' ? 'warn' : 'sky';
  return null;
}

/**
 * The drawing, as one comparable string.
 *
 * `innerHTML` on its own is **not** the drawing, and the reason is a trap of exactly the kind
 * this file exists to catch: jsdom implements `zoom` on the CSSOM but does not serialise it
 * into the `style` attribute. `<div style={{ zoom: 2, opacity: .5 }} />` comes back as
 * `<div style="opacity: 0.5;">` with `el.style.zoom === '2'` — so a test that compares markup
 * is blind to the one property `design/rendering-invariants.md` §10a *requires* the preview to
 * scale with. `scale` read as inert on both live mods until this function read it off the CSSOM.
 */
function drawing(root: Element | null): string {
  if (root === null) return '';
  const zooms = Array.from(root.querySelectorAll<HTMLElement>('*'))
    .map((el) => el.style.zoom)
    .join('|');
  return `${root.innerHTML}::${zooms}`;
}

/** Settings of `id`, excluding `on` — enablement is the page header, not a property. */
function settingKeys(id: ModId): string[] {
  const defaults = MOD_REGISTRY[id].defaults as unknown as Record<string, SettingValue>;
  return Object.keys(defaults).filter((key) => key !== 'on');
}

describe('the live preview', () => {
  it('claims exactly the mods whose preview is the real widget', () => {
    expect([...LIVE_WIDGET_IDS].sort()).toEqual([...LIVE].sort());
  });

  it.each(LIVE)('%s — every setting changes the drawing', (id) => {
    const inert: string[] = [];
    const unreachable: string[] = [];

    for (const key of settingKeys(id)) {
      if (NOT_IN_THE_PREVIEW[`${id}.${key}`]) continue;
      // A fresh mount per key, so a value left behind by the previous one cannot mask this one.
      const { container, unmount } = render(<ModPreview id={id} />);
      const stage = () => drawing(container.querySelector('.preview__stage'));
      const before = stage();
      // The *resolved* value — registry defaults under whatever the loadout stores. Reading
      // `loadout.mods[id][key]` directly returns `undefined` for every key the loadout has not
      // overridden, which is most of them, and an `undefined` "current" makes every comparison
      // below vacuous while the test still passes.
      const current = modSettings(useVoidStore.getState().loadout, id)[key] as SettingValue;
      const next = otherValue(id, key, current);
      if (next === null) {
        // No second legal value could be constructed — which means this test is not actually
        // exercising the key, and saying so is better than passing.
        unreachable.push(key);
        unmount();
        continue;
      }
      act(() => {
        useVoidStore.getState().setSetting(id, key, next);
      });
      if (stage() === before) inert.push(key);
      act(() => {
        useVoidStore.getState().setSetting(id, key, current);
      });
      unmount();
    }

    expect(unreachable, `${id}: no alternative value could be built for these`).toEqual([]);
    expect(
      inert,
      `${id}: these settings are written by a control and drawn by nothing. Either draw them, ` +
        'or add them to NOT_IN_THE_PREVIEW with the reason.',
    ).toEqual([]);
  });

  it('draws something for every mod, live widget or not', () => {
    for (const id of MOD_IDS as readonly ModId[]) {
      const { container, unmount } = render(<ModPreview id={id} />);
      const stage = container.querySelector('.preview__stage');
      // An empty stage is the one failure that survives being looked at (§15). The widgets that
      // draw a list off `tick` return `null` when it is empty, which is right on the HUD and
      // fatal here — `sample` is what stops it, and this is the assertion that keeps it wired.
      expect(stage, id).not.toBeNull();
      expect(stage!.innerHTML.length, `${id} drew an empty preview`).toBeGreaterThan(0);
      unmount();
    }
  });

  it('shows the sample armour when the player is wearing nothing', () => {
    // The lobby case, and the common one: the page is reachable with no armour on, and before
    // `sample` the preview for a five-row mod was a blank rectangle.
    act(() => {
      useVoidStore.setState({ armor: [] });
    });
    const { container } = render(<ModPreview id="armor_status" />);
    expect(container.querySelector('.v-armorlist')).not.toBeNull();
    expect(container.querySelectorAll('.v-armorlist__row').length).toBe(5);
  });

  it('applies scale and opacity around the widget, as the HUD slot does', () => {
    const { container, rerender } = render(<ModPreview id="keystrokes" />);
    const zoom = () => container.querySelector('.preview__zoom') as HTMLElement;
    const before = zoom().style.zoom;
    act(() => {
      useVoidStore.getState().setSetting('keystrokes', 'scale', 2);
    });
    rerender(<ModPreview id="keystrokes" />);
    expect(zoom().style.zoom).not.toBe(before);
    // `zoom`, never `transform` — rendering-invariants §10a. A scaled `LMB` renders `LNB`.
    expect(zoom().style.transform).toBe('');
  });
});
