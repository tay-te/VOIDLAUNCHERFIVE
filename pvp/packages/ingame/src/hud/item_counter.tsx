/**
 * Item counter — how many of the item **in your hand** are left.
 *
 * ## Say what it counts, because the name is borrowed
 *
 * Lunar's and Badlion's item counters track a *chosen* item across the whole inventory: tell it
 * pearls and it counts pearls whether or not you are holding any. This one counts the held
 * stack, because `held_count` on the tick payload is the sensor that exists. It answers "how
 * many blocks are left" while you are bridging with them and "how many gapples" while you are
 * holding one; it does not answer "how many pearls do I have" while you are holding a sword.
 * An inventory-wide counter needs a slot-scanning sensor and a way to pick the watched item,
 * and when those exist they are a superset of this mod rather than a rewrite of it
 * (`schema/mods/item_counter.json` carries the same paragraph, and it is the contract).
 *
 * Nothing here — and nothing on the tile — may imply otherwise.
 *
 * ## An empty hand is not a count of zero
 *
 * `store.ts` keeps `heldCount: null` for an empty hand precisely so that it cannot be confused
 * with a stack that has arrived at 0, which is a real reading. This widget draws **nothing** on
 * `null`, unconditionally, and there is no `hide_empty` setting to make that a preference: the
 * schema's `$comment` argues it out, and it is the rule `widgets.tsx` already states — a widget
 * with nothing to say says nothing, the way `HudCoordinates`, `HudPotionEffects` and
 * `HudArmorStatus` all do.
 *
 * The obvious objection is that an empty hand is momentary — a stack is thrown, and it
 * un-happens a tick later — so a chip that removes itself would strobe and drag its neighbours
 * about. It does not: `placementStyle` gives every HUD slot `position: absolute` from its own
 * anchor and offsets, so there is no flow for an absent widget to reflow, and nothing else on
 * the HUD moves when this one stops drawing.
 *
 * `ItemCounterChip` still draws an em dash for a `null` count rather than returning null, and
 * that is deliberately not redundancy to delete: `@void/ui` is shared, and the chip is drawn
 * outside this widget path — the desktop launcher's mod cards — where it has no widget to make
 * the decision for it. From the HUD, the chip's empty path never fires.
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are not read here; the slot applies
 * the shared chrome block around the widget, and reading them here would apply them twice
 * (`hud/chrome.ts`).
 */

import { memo } from 'react';

import { ItemCounterChip } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/**
 * A stack for a preview opened with an empty hand — which, on a page reachable from a lobby,
 * is the common case and not the odd one.
 *
 * **Chosen against the gate, and it is the figure that makes `low_threshold` visible.**
 * `test/preview.test.tsx` moves a numeric setting between the ends of its range, so this mod's
 * threshold is exercised at 0 and at 64. A fixture has to sit strictly inside that pair to show
 * the setting doing anything: at or below 0 there is nothing to warn about (0 disables the warn
 * treatment outright), and above 64 — which no stack reaches — the threshold could never catch
 * it. 12 is above 0, so at the factory default the chip draws in its resting ink; it is at or
 * below 64, so at the other end of the range the figure takes the warn treatment and the
 * drawing changes. It is also the schema's own `x12` example, so the page shows the number the
 * documentation talks about.
 *
 * `show_label` needs no help from the fixture — it puts the `x` in front of whatever figure is
 * there — but a fixture is what stops that switch from being demonstrated on an empty hand,
 * where the chip drops the prefix and the switch would read as dead.
 */
const SAMPLE_HELD_COUNT = 12;

export const HudItemCounter = memo(function HudItemCounter({ variant, sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.heldCount);
  const showLabel = useVoidStore(
    (s) => modSettings(s.loadout, 'item_counter').show_label !== false,
  );
  const lowThreshold = useVoidStore((s) =>
    Number(modSettings(s.loadout, 'item_counter').low_threshold ?? 0),
  );
  // `??`, never `||`: a held stack of 0 is a reading and must draw as one. Only `null` — the
  // empty hand — falls through to the fixture, and only on the settings page.
  const count = live ?? (sample ? SAMPLE_HELD_COUNT : null);
  if (count === null) return null;
  // No `color`: the registry gives this mod `show_label`, `low_threshold` and the shared chrome
  // block. The chip accepts one, and `lowThreshold` would outrank it anyway.
  return (
    <ItemCounterChip
      variant={variant}
      count={count}
      showLabel={showLabel}
      lowThreshold={lowThreshold}
    />
  );
});
