/**
 * Block outline — the box vanilla draws round the block you are looking at.
 *
 * One drawing at two densities, like every other world mod: `BlockOutlinePreview` is the tile and
 * the page through `dense`. It reads the live settings at both, so a player who has set their
 * outline to white at four pixels sees that in the grid — which for a mod whose whole subject is
 * "what does this stroke look like" is the fastest possible identification.
 *
 * The tile is the one place in the grid drawing a *chosen* colour, and it is on the same terms
 * `hitboxes` and `crosshair` already are: §1 keeps colour off the menu's own surface and not off
 * a mark drawn over the world, and the ink here is the ink of that mark.
 */

import { defineMod } from './types';
import { BlockOutlinePreview } from '@/menu/gameplay-previews';

function BlockOutlineThumbnail(): React.ReactElement {
  return <BlockOutlinePreview dense className="tart" />;
}

export default defineMod({
  id: 'block_outline',
  Thumbnail: BlockOutlineThumbnail,
  Preview: BlockOutlinePreview,
});
