/**
 * Overlay — the player's own view, with things taken out of it.
 *
 * One of the mods that draw into the **world**, where there is no HTML to reuse — so the
 * preview is a diagram, and `menu/gameplay-previews.tsx` opens with why that is a different
 * promise and what it still guarantees: the numbers are the game's own even though the drawing
 * is not. This one has no numbers at all, only five suppressions, so what the diagram owes the
 * player is that each of the five is *visibly* a thing that was on their screen.
 *
 * One scene rather than five marks. Five little icons in a row would be a legend — it would say
 * that there are five settings without saying what any of them costs you — and the honest
 * subject of five suppressions is the single view they all act on.
 *
 * The thumbnail is **the same component at tile density**, through `dense`. Not a convenience:
 * the grid used to draw its own 7x7 bitmap with the mod's name captioned under it, so a player
 * who opened this mod went from a blocky glyph to a diagram and had to work out that they were
 * the same mod. A picture that needs its own name printed underneath is a picture that is not
 * working.
 *
 * No `previewZoom`: the scene is authored at the size the frame gives it, not at HUD size.
 */

import { defineMod } from './types';
import { OverlayPreview } from '@/menu/gameplay-previews';

function OverlayPreviewThumbnail(): React.ReactElement {
  return <OverlayPreview dense className="tart" />;
}

export default defineMod({
  id: 'overlay',
  Thumbnail: OverlayPreviewThumbnail,
  Preview: OverlayPreview,
});
