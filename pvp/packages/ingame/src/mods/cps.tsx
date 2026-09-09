/**
 * CPS counter — clicks per second over a sliding window.
 *
 * The tile draws whichever hand is clicking faster, which is the one figure that fits; the
 * widget draws `mode` properly on the page, where both hands have room.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudCps } from '@/hud/widgets';
import { useVoidStore } from '@/store/store';

function CpsThumbnail(): React.ReactElement {
  const left = useVoidStore((s) => s.cpsLeft);
  const right = useVoidStore((s) => s.cpsRight);
  return <Readout value={(Math.max(left, right) || 6.2).toFixed(1)} unit="CPS" />;
}

export default defineMod({
  id: 'cps',
  Thumbnail: CpsThumbnail,
  Preview: HudCps,
  previewZoom: 3,
});
