/** Potion counter — PLACEHOLDER art, replaced in the depth wave's HUD pass. */

import { defineMod } from './types';
import { Readout } from './art';
import { HudPotionCounter } from '@/hud/potion_counter';

function PotionCounterThumbnail(): React.ReactElement {
  return <Readout value="7" unit="POTS" />;
}

export default defineMod({
  id: 'potion_counter',
  Thumbnail: PotionCounterThumbnail,
  Preview: HudPotionCounter,
  previewZoom: 3,
});
