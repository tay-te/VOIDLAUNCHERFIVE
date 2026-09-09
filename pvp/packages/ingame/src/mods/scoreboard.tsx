/**
 * Scoreboard — the server's sidebar, where you want it or not at all.
 *
 * One drawing at two densities, like the other thirteen world mods: `ScoreboardPreview` is both
 * the tile and the page, through `dense`. That is the rule `gameplay-previews.tsx` opens with,
 * and it is load-bearing here rather than a convenience — this mod's whole subject is *where a
 * thing sits on a screen*, so a tile that showed a symbol instead of a screen would be showing
 * the one thing the mod is not about.
 *
 * The tile therefore reads the live settings too: a player who has moved their scoreboard up and
 * shrunk it sees that arrangement in the grid, at 118px, which is the fastest possible answer to
 * "is this the mod I already configured".
 */

import { defineMod } from './types';
import { ScoreboardPreview } from '@/menu/gameplay-previews';

/**
 * `className="tart"` is the grid's own art hook, the way the other thirteen diagram thumbnails
 * take it. `test/fake-mods-injection.test.tsx` asserts every tile in the grid carries one — a
 * tile without it is the id-as-caption fallback, which is the failure `mods/types.ts` exists to
 * make unrepresentable.
 */
function ScoreboardThumbnail(): React.ReactElement {
  return <ScoreboardPreview dense className="tart" />;
}

export default defineMod({
  id: 'scoreboard',
  Thumbnail: ScoreboardThumbnail,
  Preview: ScoreboardPreview,
});
