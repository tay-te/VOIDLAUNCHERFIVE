/**
 * CPS graph — the shape of your clicking, not just the rate.
 *
 * The tile draws the graph itself, at the grid's cell size, and that is the one thing it could
 * draw: this mod's whole claim is that the *shape* is the reading, so a tile showing a figure
 * would be a picture of the CPS counter sitting two tiles away. `mods/types.ts` puts
 * identification first, and a row of columns is the only mark in this grid that is one.
 *
 * **Always twenty columns, whatever `window_s` says.** The tile well is 145px at seven across and
 * as little as 123 at eight; sixty columns inside that is under two pixels each, which reads as a
 * texture rather than as a rate — the same trade `gameplay-previews.tsx` records for its cell rows,
 * where eight 12px cells read as a texture and seven 14px cells read as a scale. The page below is
 * where the window is chosen and shown at its real width.
 *
 * Live off the store, with the widget's own fixture standing in before any history exists. The
 * grid is reachable from the main menu, where "no session yet" is the state the tile is most often
 * looked at in, so the fixture is the common case here rather than the fallback.
 *
 * Monochrome (§1).
 */

import { defineMod } from './types';
import { CpsGraphChip } from '@/ui';
import { HudCpsGraph } from '@/hud/cps_graph';
import { modSettings, useVoidStore } from '@/store/store';

/** What the tile holds without becoming a texture — see the note above. */
const TILE_COLUMNS = 20;

/** A hand that opens fast and fades, for a grid opened before anything has been clicked. */
const FALLBACK: readonly number[] = [
  0, 2, 6, 9, 11, 12, 12, 11, 12, 10, 11, 9, 10, 8, 9, 7, 8, 6, 7, 5,
];

function CpsGraphThumbnail(): React.ReactElement {
  const history = useVoidStore((s) => s.clickHistory);
  const mode = useVoidStore((s) => {
    const value = modSettings(s.loadout, 'cps_graph').mode;
    return value === 'right' || value === 'both' ? value : 'left';
  });
  const series =
    history.length > 0
      ? history.map((e) => (mode === 'left' ? e.l : mode === 'right' ? e.r : e.l + e.r))
      : FALLBACK;
  return (
    <span className="tart tart--graph">
      <CpsGraphChip series={series} columns={TILE_COLUMNS} className="tart__graph" />
    </span>
  );
}

export default defineMod({
  id: 'cps_graph',
  Thumbnail: CpsGraphThumbnail,
  Preview: HudCpsGraph,
  // Under the chip mods' 3. The graph is 88px wide before any enlargement and a mod with nine
  // settings gets the grouped structure — two property columns — so the preview frame is the
  // narrower half of the page. 2.4 is what holds sixty columns inside it, and it is the number
  // `server_address` and `hit_trade` arrived at for the same reason.
  previewZoom: 2.4,
});
