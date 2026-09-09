/**
 * Combo counter — consecutive hits landed without being hit back.
 *
 * The tile is the count over its noun, live off the store like the other readout tiles. It
 * does **not** apply the mod's timeout: a tile is identification, the grid is only ever seen
 * with the menu up — where `applyTick` is held and the count is a frozen last value anyway —
 * and a tile that emptied itself mid-look would make the art jump inside a grid that never
 * moves. The chip over the game is where the window is enforced, and it is the widget on the
 * page below that draws it.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudCombo } from '@/hud/combo';
import { useVoidStore } from '@/store/store';

function ComboThumbnail(): React.ReactElement {
  const combo = useVoidStore((s) => s.combo);
  return <Readout value={String(combo || 7)} unit="COMBO" />;
}

export default defineMod({
  id: 'combo',
  Thumbnail: ComboThumbnail,
  Preview: HudCombo,
  // A chip, so the 3 the other chip mods use — they are the same object at the same size over
  // the game, and a set of chips at different magnifications would say they were not.
  previewZoom: 3,
});
