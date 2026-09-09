/**
 * Nametags — the floating names, smaller, thinner, nearer or gone.
 *
 * One drawing at two densities, like every other world mod. It reads the live settings at both,
 * so a player who has shrunk their nametags and dropped the plate sees that in the grid — which
 * for a mod whose subject is "how much screen does a name take" is the whole identification.
 *
 * The tile keeps both tags. It is tempting to drop the far one at 118px, and it would be wrong
 * for the reason `gameplay-previews.tsx` records about `dense`: the far tag is what `max_distance`
 * moves, and a tile that dropped it would be a tile where a quarter of the mod's settings do
 * nothing. "One drawing, two densities" assumes the drawing degrades — dropping the half that
 * carries a setting is not degrading, it is a different drawing.
 */

import { defineMod } from './types';
import { NametagsPreview } from '@/menu/gameplay-previews';

function NametagsThumbnail(): React.ReactElement {
  return <NametagsPreview dense className="tart" />;
}

export default defineMod({
  id: 'nametags',
  Thumbnail: NametagsThumbnail,
  Preview: NametagsPreview,
});
