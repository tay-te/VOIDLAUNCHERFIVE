/** Reach display — PLACEHOLDER art, replaced in the depth wave's HUD pass. */

import { defineMod } from './types';
import { Readout } from './art';
import { HudReach } from '@/hud/reach';

function ReachThumbnail(): React.ReactElement {
  return <Readout value="2.87" unit="BLOCKS" />;
}

export default defineMod({
  id: 'reach',
  Thumbnail: ReachThumbnail,
  Preview: HudReach,
  previewZoom: 3,
});
