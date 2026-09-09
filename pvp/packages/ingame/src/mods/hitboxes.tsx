/**
 * Hitboxes — entity bounding boxes, at the mod's own stroke and ink.
 *
 * One of the four mods that draw into the **world**, where there is no HTML to reuse — so the
 * preview is a diagram, and `menu/gameplay-previews.tsx` opens with why that is a different
 * promise and what it still guarantees: the numbers are the game's own even though the drawing
 * is not.
 *
 * The thumbnail is **the same component at tile density**, through `dense`. Not a convenience:
 * the grid used to draw its own 7x7 bitmap with the mod's name captioned under it, so a player
 * who opened this mod went from a blocky glyph to a diagram and had to work out that they were
 * the same mod. A picture that needs its own name printed underneath is a picture that is not
 * working.
 */

import { defineMod } from './types';
import { HitboxPreview } from '@/menu/gameplay-previews';

function HitboxPreviewThumbnail(): React.ReactElement {
  return <HitboxPreview dense className="tart" />;
}

export default defineMod({
  id: 'hitboxes',
  Thumbnail: HitboxPreviewThumbnail,
  Preview: HitboxPreview,
});
