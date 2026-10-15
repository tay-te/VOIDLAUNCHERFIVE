/**
 * Item counter — how many of an item are left.
 *
 * The tile is the chip mods' own shape, `Readout`, because this reading genuinely is a figure
 * and a unit: the count, over the word for what is being counted.
 *
 * **The caption is what `track` counts, and that word is doing work.** `HELD` for the stack in
 * the hand (the default), `ALL HELD` for every stack of the held item, and the item's own name —
 * `PEARLS`, `WOOL`, `ARROWS` — for the inventory-wide values. A tile that said `ITEMS` whatever the
 * setting would leave a player guessing which of three different readings the chip is giving, at
 * the moment they are deciding whether it is the one they want. The tile may read the mod's own
 * settings (`mods/types.ts`); it may not read a colour, and this reads none.
 *
 * Live off the store, and `??` rather than `||`: a count of 0 is a real reading — the last block
 * placed, the last pearl thrown — and only `null` (an empty hand, or no `counts` push yet) falls
 * back to the fixture. That is the same distinction the store keeps and the widget draws.
 *
 * The `x` prefix is not drawn here. It is the chip's `show_label`, a HUD-legibility mark for a
 * figure sitting next to a CPS rate in the same corner; on the tile the caption underneath
 * already says what the number is, and printing both would be saying it twice.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudItemCounter } from '@/hud/item_counter';
import { modSettings, useVoidStore, type ItemCounts } from '@/store/store';

/** The caption per `track` value. Uppercase, like every other tile's unit. */
const CAPTION: Readonly<Record<string, string>> = {
  held_stack: 'HELD',
  held_total: 'ALL HELD',
  ender_pearl: 'PEARLS',
  golden_apple: 'GAPPLES',
  arrow: 'ARROWS',
  snowball: 'SNOWBALLS',
  egg: 'EGGS',
  cobweb: 'COBWEBS',
  wool: 'WOOL',
  tnt: 'TNT',
  fire_charge: 'FIREBALLS',
  iron_ingot: 'IRON',
  gold_ingot: 'GOLD',
  diamond: 'DIAMONDS',
  emerald: 'EMERALDS',
};

function ItemCounterThumbnail(): React.ReactElement {
  const track = useVoidStore((s) => String(modSettings(s.loadout, 'item_counter').track ?? 'held_stack'));
  const held = useVoidStore((s) =>
    track === 'held_stack' ? s.heldCount : (s.counts?.[track as keyof ItemCounts] ?? null),
  );
  return <Readout value={String(held ?? 12)} unit={CAPTION[track] ?? 'HELD'} />;
}

export default defineMod({
  id: 'item_counter',
  Thumbnail: ItemCounterThumbnail,
  Preview: HudItemCounter,
  // The chip mods' number. `x12` is the same object as `142 fps` or `22 ms` — a short figure on
  // a chip — and a set of chips previewed at different magnifications would say they were not.
  previewZoom: 3,
});
