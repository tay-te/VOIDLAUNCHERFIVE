/**
 * Reach display — PLACEHOLDER, replaced in the depth wave's HUD pass.
 */

import { memo } from 'react';

import { ItemCounterChip } from '@/ui';
import type { HudWidgetProps } from './widgets';

export const HudReach = memo(function HudReach({ variant }: HudWidgetProps) {
  return <ItemCounterChip variant={variant} count={3} showLabel={false} lowThreshold={0} />;
});
