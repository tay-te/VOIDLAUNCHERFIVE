/**
 * Reach display — the distance of the last landed hit.
 *
 * The chip mods' `Readout`, live off the store. It reads `mode`, because a tile printing the last
 * hit beside a chip printing the mean of ten would be two pictures of one mod disagreeing, and
 * says which with its caption (`BLOCKS` or `AVG BLOCKS`). It does not read `decimals`: the tile
 * always shows two, the precision the measurement has, for the same reason the momentum tile
 * ignores its own — the figure is the whole tile.
 *
 * `reach` is a grey mod (`hypixel_safe: grey`); nothing about that changes what it draws, and the
 * grid's safety badge is the place that says so.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudReach } from '@/hud/reach';
import { modSettings, useVoidStore } from '@/store/store';

/** The preview's own last hit — see `hud/reach.tsx` for why that figure. */
const FALLBACK_BLOCKS = 2.87;

function ReachThumbnail(): React.ReactElement {
  const average = useVoidStore((s) => modSettings(s.loadout, 'reach').mode === 'average');
  const blocks = useVoidStore((s) => (s.reach ? (average ? s.reach.average : s.reach.last) : null));
  return (
    <Readout value={(blocks ?? FALLBACK_BLOCKS).toFixed(2)} unit={average ? 'AVG BLOCKS' : 'BLOCKS'} />
  );
}

export default defineMod({
  id: 'reach',
  Thumbnail: ReachThumbnail,
  Preview: HudReach,
  // The chip mods share 3: the same object at the same size over the game.
  previewZoom: 3,
});
