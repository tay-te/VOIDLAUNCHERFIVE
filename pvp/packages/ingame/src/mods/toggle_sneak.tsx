/**
 * Toggle sneak — what the key does, and what the crouch does because of it.
 *
 * One of the mods that draw into the **world**, where there is no HTML to reuse — so the
 * preview is a diagram, and `menu/gameplay-previews.tsx` opens with why that is a different
 * promise and what it still guarantees: the numbers are the game's own even though the drawing
 * is not.
 *
 * The diagram is deliberately its sibling's instrument. This mod and Toggle sprint are the same
 * `KeyBinding` override on a different key (the schema says so in as many words), so they are
 * drawn with the same two-row timeline; what separates them on the grid is that sneak's run
 * ends at a second tap rather than leaving the row, and that its key row is labelled with the
 * mod's own keycap. The bind is the entire reason this is a mod and not a boolean, so the bind
 * is on the drawing.
 *
 * The thumbnail is **the same component at tile density**, through `dense`. Not a convenience:
 * the grid used to draw its own 7x7 bitmap with the mod's name captioned under it, so a player
 * who opened this mod went from a blocky glyph to a diagram and had to work out that they were
 * the same mod. A picture that needs its own name printed underneath is a picture that is not
 * working.
 */

import { defineMod } from './types';
import { SneakPreview } from '@/menu/gameplay-previews';

function SneakPreviewThumbnail(): React.ReactElement {
  return <SneakPreview dense className="tart" />;
}

export default defineMod({
  id: 'toggle_sneak',
  Thumbnail: SneakPreviewThumbnail,
  Preview: SneakPreview,
});
