/**
 * Potion counter — how many potions of one effect you are carrying.
 *
 * ## Why this needed a sensor when `item_counter` did not
 *
 * `held_count` sees the hand and nothing else, which is why the item counter counted the held
 * stack and said so in its own description. Pot PvP is `docs/mod-roster.md` §3.1 #4's "first-class
 * 1.8.9 mode", and what you plan around is what is in the inventory, not what happens to be in
 * your hand at the moment you glance down. `bridge.json`'s `inventory` is that sensor, and its
 * changelog entry records the part that took the thought: **every potion in 1.8.9 is
 * `minecraft:potion`**, so an inventory keyed by item id would have told a player they had eight
 * heals when three of them were water bottles.
 *
 * ## What "matching" means here
 *
 * An entry matches when its `effect` is the one the player asked for and — with `splash_only` on,
 * which is the default — when it is throwable. Both halves are the sensor's facts; this file only
 * decides which of them the setting wants. `any` matches every entry that *has* an effect, so a
 * water bottle is never counted under any value: the sensor reports no effect for it, so there is
 * nothing to match. That is a property of the item rather than a filter applied here.
 *
 * ## It draws at zero, and that is the one place it disagrees with `item_counter`
 *
 * Every other counter on this HUD disappears when it has nothing to count, because a chip reading
 * `0` between fights is noise. **`0 heals` is not noise, it is the single most important thing
 * this readout can say.** A mod that vanished at exactly the moment the answer became bad would
 * be answering the question by leaving, and a player would read the empty space as the mod being
 * off. So the chip stays; what goes away is the mod, when you turn it off.
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are **not** read here: `HudSlot`
 * applies the shared chrome block around the widget, and `PreviewZoom` applies it in the same
 * place on the mod page (`hud/chrome.ts`).
 */

import { memo } from 'react';

import { PotionCountChip } from '@/ui';
import { type InventoryEntry } from '@/bridge/protocol';
import { modSettings, useVoidStore } from '@/store/store';
import { potionMeta } from './format';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. */
export const POTION_COUNTER_ID = 'potion_counter';

/**
 * The four effects the schema offers, as the numeric potion ids the wire carries.
 *
 * The ids are 1.8.9's own and are already the key of `hud/format.ts`'s `POTIONS` table, which is
 * what the `fx` array's labels and swatches come from — so this maps a setting onto an existing
 * table rather than introducing a second one. `any` is absent on purpose: it is not an effect,
 * it is the absence of the filter, and giving it a fake id would put it in the same space as the
 * real ones.
 */
const EFFECT_IDS: Record<string, number> = {
  healing: 6,
  speed: 1,
  strength: 5,
  fire_resistance: 12,
};

/**
 * The unit each choice prints — what a player calls the potion, not what the game calls the
 * effect.
 *
 * `heals` rather than `Instant health`, because the chip is read mid-fight and `6 heals` is the
 * sentence a player would say out loud. The `POTIONS` table's labels are for the effects *list*,
 * where the subject is the effect on you rather than the bottle in your bag.
 */
const UNITS: Record<string, string> = {
  healing: 'heals',
  speed: 'speed',
  strength: 'str',
  fire_resistance: 'fire res',
  any: 'pots',
};

/** A stored `effect` narrowed to what this draws; anything else is the factory default. */
function asEffect(value: unknown): string {
  return typeof value === 'string' && (value in UNITS) ? value : 'healing';
}

/**
 * A mid-game inventory, for a page opened outside one.
 *
 * Six splash heals and two drinkables, which is the state `splash_only` exists to distinguish:
 * with it on the chip reads 6, with it off 8, so the switch has something to bite on rather than
 * two identical pictures. The two speed potions make `effect` move as well — a fixture with one
 * kind of potion in it would leave four of the five choices drawing the same zero.
 */
const SAMPLE: readonly InventoryEntry[] = [
  { item: 'minecraft:potion', count: 6, effect: 6, splash: true },
  { item: 'minecraft:potion', count: 2, effect: 6, splash: false },
  { item: 'minecraft:potion', count: 2, effect: 1, splash: true },
  { item: 'minecraft:ender_pearl', count: 16 },
];

export const HudPotionCounter = memo(function HudPotionCounter({ variant, sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.inventory);
  const effect = useVoidStore((s) => asEffect(modSettings(s.loadout, POTION_COUNTER_ID).effect));
  const splashOnly = useVoidStore(
    (s) => modSettings(s.loadout, POTION_COUNTER_ID).splash_only !== false,
  );
  const showLabel = useVoidStore(
    (s) => modSettings(s.loadout, POTION_COUNTER_ID).show_label !== false,
  );

  // `null` is "the sensor has not said", which is not the same as an empty inventory — the chip
  // draws nothing for the first and `0` for the second, because only one of those is a
  // measurement. The fixture stands in for the first, on the settings page only.
  const entries = live ?? (sample ? SAMPLE : null);
  if (entries === null) return null;

  const wanted = EFFECT_IDS[effect];
  const count = entries.reduce((total, entry) => {
    if (entry.effect === undefined) return total;
    if (wanted !== undefined && entry.effect !== wanted) return total;
    if (splashOnly && entry.splash !== true) return total;
    return total + entry.count;
  }, 0);

  return (
    <PotionCountChip
      variant={variant}
      count={count}
      unit={showLabel ? UNITS[effect] : undefined}
      // The effect's own swatch, from the table the effects list already draws from. `any` has no
      // one colour, so it has no pip — an arbitrary one would be a colour that marks nothing,
      // which is what §1's accent rule is about.
      color={wanted === undefined ? undefined : potionMeta({ id: wanted } as never).color}
    />
  );
});
