/**
 * Reach display — the distance of the last landed hit.
 *
 * `Readout` is the family's own shape and is right here without bending: a distance is a figure
 * with a unit, and `3.14` over `BLOCKS` is only ever one thing in this grid.
 *
 * Live off the store, with the widget's fixture standing in before anything has been hit — which
 * in a grid reachable from the main menu is the state the tile is most often looked at in. The
 * fixture is a constant for the reason the widget's own note gives: a tile whose figure moved as
 * the crosshair moved would be the reach *indicator* `docs/mod-roster.md` §6.1 forbids, drawn at
 * 145px in a menu.
 *
 * Monochrome (§1). The chip turns amber past `warn_above`, over the game where that marks a live
 * value; the grid stays quiet.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudReach } from '@/hud/reach';
import { useVoidStore } from '@/store/store';

/** The widget's own fixture, used before anything has been hit — see `hud/reach.tsx`. */
const FALLBACK_BLOCKS = 3.14;

function ReachThumbnail(): React.ReactElement {
  const blocks = useVoidStore((s) => s.reach) ?? FALLBACK_BLOCKS;
  return <Readout value={blocks.toFixed(2)} unit="BLOCKS" />;
}

export default defineMod({
  id: 'reach',
  Thumbnail: ReachThumbnail,
  Preview: HudReach,
  // The chip mods share 3 — the same object at the same size over the game.
  previewZoom: 3,
});
