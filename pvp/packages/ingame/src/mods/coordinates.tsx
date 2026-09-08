/**
 * Coordinates — player position, and the direction they are facing.
 *
 * The tile is the three axes as live lines. The frame's fixture position stands in before the
 * first `tick`, so the tile never draws three zeroes at a player standing somewhere else.
 */

import { defineMod } from './types';
import { Lines } from './art';
import { HudCoordinates } from '@/hud/widgets';
import { useVoidStore } from '@/store/store';

function CoordinatesThumbnail(): React.ReactElement {
  const pos = useVoidStore((s) => s.pos);
  return (
    <Lines
      labelClass="tart__axis"
      valueClass="tart__value"
      rows={[
        ['X', String(Math.round(pos?.x ?? 128))],
        ['Y', String(Math.round(pos?.y ?? 64))],
        ['Z', String(Math.round(pos?.z ?? -302))],
      ]}
    />
  );
}

export default defineMod({
  id: 'coordinates',
  Thumbnail: CoordinatesThumbnail,
  Preview: HudCoordinates,
  previewZoom: 3,
});
