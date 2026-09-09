/**
 * Item counter — the size of the stack in your hand.
 *
 * The tile is the chip mods' own shape, `Readout`, because this reading genuinely is a figure
 * and a unit: the count, over the word for what is being counted.
 *
 * **The caption is `HELD`, and that word is doing work.** The mod's name is borrowed from
 * Lunar's and Badlion's, which count a chosen item across the whole inventory; this one counts
 * the hand, because `held_count` is the sensor that exists (`hud/item_counter.tsx` and the
 * schema both say so at length). A tile captioned `ITEMS` would promise the inventory-wide mod
 * at the exact moment a player is deciding whether to turn this one on, which is the one place
 * the overstatement would cost something.
 *
 * Live off the store, and `??` rather than `||`: a held stack of 0 is a real reading — the last
 * block placed — and only `null`, an empty hand, falls back to the fixture. That is the same
 * distinction the store keeps and the same figure the widget's preview stands on.
 *
 * The `x` prefix is not drawn here. It is the chip's `show_label`, a HUD-legibility mark for a
 * figure sitting next to a CPS rate in the same corner; on the tile the caption underneath
 * already says what the number is, and printing both would be saying it twice.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudItemCounter } from '@/hud/item_counter';
import { useVoidStore } from '@/store/store';

function ItemCounterThumbnail(): React.ReactElement {
  const held = useVoidStore((s) => s.heldCount);
  return <Readout value={String(held ?? 12)} unit="HELD" />;
}

export default defineMod({
  id: 'item_counter',
  Thumbnail: ItemCounterThumbnail,
  Preview: HudItemCounter,
  // The chip mods' number. `x12` is the same object as `142 fps` or `22 ms` — a short figure on
  // a chip — and a set of chips previewed at different magnifications would say they were not.
  previewZoom: 3,
});
