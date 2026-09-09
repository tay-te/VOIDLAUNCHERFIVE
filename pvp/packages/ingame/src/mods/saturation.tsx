/**
 * Saturation — the hidden half of the hunger bar.
 *
 * The tile is the figure over its unit, live off the store, so a player who is looking at the
 * grid sees their own reading. One decimal regardless of the mod's `decimals` setting: a tile
 * answers "which mod is this" and the second decimal is a detail the page is for. Monochrome,
 * like every tile — and this mod has no colour to leak anyway.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudSaturation } from '@/hud/saturation';
import { useVoidStore } from '@/store/store';

function SaturationThumbnail(): React.ReactElement {
  const saturation = useVoidStore((s) => s.saturation);
  // `null` is "no reading", not zero (`store.ts`), and a tile of `0.0` would be a picture of
  // the one state this mod exists to warn about, shown to a player who is not in it.
  return <Readout value={(saturation ?? 13.7).toFixed(1)} unit="SAT" />;
}

export default defineMod({
  id: 'saturation',
  Thumbnail: SaturationThumbnail,
  Preview: HudSaturation,
  // A chip, so the same 3 the other chip mods use.
  previewZoom: 3,
});
