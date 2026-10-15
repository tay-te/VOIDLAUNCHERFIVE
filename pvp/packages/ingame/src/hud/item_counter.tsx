/**
 * Item counter — how many of an item are left: the held stack, every stack of the held item, or a
 * named item across the whole inventory.
 *
 * ## What it counts is the `track` setting
 *
 * - `held_stack` (the default, and what the chip has always shown) reads `held_count`: the stack
 *   in your hand. "How many blocks are left" while bridging, "how many gapples" while eating.
 * - `held_total` reads `counts.held_total`: every stack in the inventory matching the held item,
 *   same item and same variant, so white wool is not red wool.
 * - Every other value — `ender_pearl`, `golden_apple`, `arrow`, `wool` and the rest — reads that
 *   key of `counts`, the inventory-wide sensor, **whatever you are holding**. That is the Lunar
 *   and Badlion meaning of an item counter: tell it pearls and it counts pearls while you hold a
 *   sword. Healing potions and soup are deliberately not on that list; they are the Potion
 *   counter's, which knows what it is counting and so can warn at a sensible default.
 *
 * **A named item at 0 draws `0`.** You have run out, and that is the reading the chip exists for.
 * Only a missing reading — no `counts` push yet, or `held_total` with an empty hand — draws
 * nothing.
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
import { modSettings, useVoidStore, type ItemCounts } from '@/store/store';
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

/**
 * The same stand-in for the inventory-wide `track` values, one figure per item.
 *
 * Different figures on purpose: `track` is a setting like any other and the preview has to move
 * when it moves, so a fixture that said `12` for every item would leave the control looking dead.
 * Each is a believable stash for that item and every one sits strictly inside `low_threshold`'s
 * 0..64 range for the reason {@link SAMPLE_HELD_COUNT} gives. `held_total` is the held stack's 12
 * plus two more stacks of it.
 */
const SAMPLE_COUNTS: Readonly<Partial<Record<keyof ItemCounts, number>>> = {
  held_total: 44,
  ender_pearl: 6,
  golden_apple: 9,
  arrow: 58,
  snowball: 32,
  egg: 16,
  cobweb: 4,
  wool: 56,
  tnt: 3,
  fire_charge: 5,
  iron_ingot: 27,
  gold_ingot: 11,
  diamond: 8,
  emerald: 2,
};

/** A stored `track`, as the `counts` key it reads — or null for the held stack. */
function countsKey(track: unknown): Exclude<keyof ItemCounts, 'health_potion' | 'soup'> | null {
  if (typeof track !== 'string' || track === 'held_stack') return null;
  if (track === 'health_potion' || track === 'soup') return null;
  return track as Exclude<keyof ItemCounts, 'health_potion' | 'soup'>;
}

export const HudItemCounter = memo(function HudItemCounter({ variant, sample }: HudWidgetProps) {
  const key = useVoidStore((s) => countsKey(modSettings(s.loadout, 'item_counter').track));
  // One subscription, and a primitive: the held stack, or the one key of `counts` being tracked,
  // so a pickup of something else does not re-render this chip.
  const live = useVoidStore((s) =>
    key === null ? s.heldCount : (s.counts?.[key] ?? null),
  );
  const showLabel = useVoidStore(
    (s) => modSettings(s.loadout, 'item_counter').show_label !== false,
  );
  const lowThreshold = useVoidStore((s) =>
    Number(modSettings(s.loadout, 'item_counter').low_threshold ?? 0),
  );
  // `??`, never `||`: a count of 0 is a reading and must draw as one. Only `null` — the empty
  // hand, or no `counts` push yet — falls through to the fixture, and only on the settings page.
  const fixture = key === null ? SAMPLE_HELD_COUNT : (SAMPLE_COUNTS[key] ?? SAMPLE_HELD_COUNT);
  const count = live ?? (sample ? fixture : null);
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
