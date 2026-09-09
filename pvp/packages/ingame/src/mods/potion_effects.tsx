/**
 * Potion effects — what is applied, and how long is left of it.
 */

import { defineMod } from './types';
import { PotionsThumb } from './art';
import { HudPotionEffects } from '@/hud/widgets';

export default defineMod({
  id: 'potion_effects',
  Thumbnail: PotionsThumb,
  Preview: HudPotionEffects,
  // A list widget carries more text per row, so it reaches the frame's width sooner than a chip.
  previewZoom: 1.8,
});
