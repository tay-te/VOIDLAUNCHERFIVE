/**
 * Potion counter — PLACEHOLDER, replaced in the depth wave's HUD pass.
 */

import { memo } from 'react';

import { ItemCounterChip } from '@/ui';
import type { HudWidgetProps } from './widgets';

export const HudPotionCounter = memo(function HudPotionCounter({ variant }: HudWidgetProps) {
  return <ItemCounterChip variant={variant} count={7} showLabel lowThreshold={0} />;
});
