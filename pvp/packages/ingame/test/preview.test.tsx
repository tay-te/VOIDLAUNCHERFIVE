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
  'direction',
  'armor_status',
  'potion_effects',
  'watermark',
  'crosshair',
  'toggle_sprint',
  'fullbright',
  'hitboxes',
  'zoom',
  'combo',
  'hit_trade',
  'clock',
  'cps_graph',
  'saturation',
  'momentum',
  'memory',
  'server_address',
  'item_counter',
  'stopwatch',
  'fov',
  'toggle_sneak',
  'overlay',
  'freelook',
  'hit_color',
  'damage_tint',
  'old_animations',
  'old_input',
];

/**
 * Settings a live preview provably cannot show, and why.
 *
 * **This list is the one place this gate can quietly erode**, so the bar for an entry is that
 * the setting is *structurally* undrawable in a still frame — not that drawing it was hard, and
 * not that nobody had thought of a way. It started at five and three of them did not survive
 * being asked that question properly:
 *
 *   · `zoom.key` — "a keybind has no drawn form" was simply wrong. It has an obvious one, and
 *     the key is half of what the zoom diagram is saying: hold *this*, see *that*. It is drawn.
 *   · `zoom.smooth` — "a transition, and this preview does not animate" confused *animating* a
 *     transition with *depicting* one. A still frame can draw the sizes the view passes
 *     through, which is what the ghost frames now do.
 *   · `cps.window_ms` — "nothing is clicking on a settings page" was true and beside the point.
 *     A fixture can click, and once the fixture bursts the way people actually click, the
 *     window changes the figure exactly as it does in game.
 *
 * Two remain, and both are about the *shape* of the thing rather than the effort:
 */
const NOT_IN_THE_PREVIEW: Record<string, string> = {
  // This key toggles the widget's **visibility**, and it is not one of the keys the widget
  // draws. The only faithful picture of it firing is the widget not being there, and a preview
  // whose content is an empty box is the §15 failure the rest of this file exists to prevent.
  // The `KeybindChip` in the row is the feedback, and the page's meta line prints it again.
  'keystrokes.keybind': 'it makes the widget absent, and an absent widget is not a preview',
  // The same setting on three more mods, and the same answer, for a reason worth stating once:
  // a toggle key's whole content is *the mod not being there*. On `keystrokes` that is an empty
  // box; on these three it is the game drawn normally, which is a picture of no mod at all. The
  // `KeybindChip` in the row is the feedback, and the page's meta line prints the key again.
  'fullbright.keybind': 'it turns the mod off, and the game without the mod is not its preview',
  'hitboxes.keybind': 'it turns the mod off, and the game without the mod is not its preview',
  'toggle_sprint.keybind': 'it turns the mod off, and the game without the mod is not its preview',
  // Camera damping: the mouse moves and the view follows late. The whole content of the
  // setting is the lag between two things, and a still frame has one instant in it.
  //
  // Worth stating plainly, because the cheap way out was available and is the erosion this
  // list guards against: the diagram already prints a sentence, and making that sentence
  // mention `cinematic` would have satisfied this gate without drawing anything. A caption is
  // not a preview, and passing by editing text would have made the gate worth less than the
  // exemption it replaced.
  'zoom.cinematic': 'the lag between two motions, and a still frame holds one instant',
  // A stopwatch's two keys ask for a *change* — a clock starting, or a reading dropping to
  // zero — and a change is two frames. This is a HUD mod, so the preview is the identical
  // widget `HudLayer` places and there is no diagram to draw a keycap on, which is what
  // rescued `zoom.key`. The only thing either key could move in a still frame is the fixture,
  // and a fixture that moved when a *binding* changed would be drawing a press nobody made.
  // The `KeybindChip` in the row is the feedback, and the page's meta line prints the key.
  'stopwatch.start_key': 'it asks the clock to change state, and a still frame holds one instant',
  'stopwatch.reset_key': 'it asks the reading to drop to zero, and a still frame holds one instant',
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
  // A keybind's domain is key names, which no table here carries. `zoom.key` is drawn on the
  // FOV diagram, so it has to be exercised like anything else.
  if (key === 'key' || key === 'keybind') return current === 'V' ? 'C' : 'V';
  if (key === 'key_color') return current === 'sky' ? 'teal' : 'sky';
  if (key === 'pressed_color') return current === 'sky' ? 'warn' : 'sky';
  // Every `hex_color`, not only the one named `color`. Written as a suffix for the same reason
  // `kindOf` is: `hitboxes.eye_line_color` is the second of these, and matching on the exact
  // name would have failed it here as "no alternative value could be built" — which reads like
  // a broken setting rather than a table that had not heard of it.
  if (key === 'color' || key.endsWith('_color')) {
    return current === '#FF9E7A' ? '#7ADFFF' : '#FF9E7A';
  }
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

/**
 * Settings that only draw once another setting is on, and what to turn on to see them.
 *
 * A **prerequisite, not an exemption.** `hitboxes.eye_line_color` inks the look ray, and the
 * ray ships off (`show_eye_line` defaults to false) — so at the factory settings this test
 * changes the colour of something that is not on screen and reads it as inert. That is a true
 * observation about the fixture and a false one about the setting.
 *
 * The alternative was an entry in {@link NOT_IN_THE_PREVIEW}, and it would have been the first
 * dishonest one there: the other three are settings a still frame genuinely cannot hold, where
 * this one draws perfectly well the moment the thing it colours exists. Stating the dependency
 * keeps the gate strict — the setting still has to move the drawing, it is just asked in a
 * state where it has something to move.
 */
const REQUIRES: Record<string, Record<string, SettingValue>> = {
  'hitboxes.eye_line_color': { show_eye_line: true },
};

describe('the live preview', () => {
  it('claims exactly the mods whose preview is the real widget', () => {
    expect([...LIVE_WIDGET_IDS].sort()).toEqual([...LIVE].sort());
  });

  it.each(LIVE)('%s — every setting changes the drawing', (id) => {
    const inert: string[] = [];
    const unreachable: string[] = [];

    for (const key of settingKeys(id)) {
      if (NOT_IN_THE_PREVIEW[`${id}.${key}`]) continue;
      // Whatever this key needs on screen before it can move anything. Set before the mount,
      // and put back after it, so the next key is exercised from the factory state like the
      // rest.
      const required = REQUIRES[`${id}.${key}`] ?? {};
      const restore: Record<string, SettingValue> = {};
      for (const [dep, value] of Object.entries(required)) {
        restore[dep] = modSettings(useVoidStore.getState().loadout, id)[dep] as SettingValue;
        act(() => {
          useVoidStore.getState().setSetting(id, dep, value);
        });
      }
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
      const putBack = () => {
        for (const [dep, value] of Object.entries(restore)) {
          act(() => {
            useVoidStore.getState().setSetting(id, dep, value);
          });
        }
      };
      if (next === null) {
        // No second legal value could be constructed — which means this test is not actually
        // exercising the key, and saying so is better than passing.
        unreachable.push(key);
        unmount();
        putBack();
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
      putBack();
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
