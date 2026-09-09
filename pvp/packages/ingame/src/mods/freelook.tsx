/**
 * Freelook — one player, one camera, and the ring the camera never leaves.
 *
 * One of the mods that draw into the **world**, where there is no HTML to reuse — so the
 * preview is a diagram, and `menu/gameplay-previews.tsx` opens with why that is a different
 * promise and what it still guarantees: the numbers are the game's own even though the drawing
 * is not. Here the number that matters is a *non*-number — the camera's distance from the pivot,
 * which is vanilla's, is not a setting, and is drawn as a radius that nothing can change.
 *
 * The drawing is a plan view rather than a first-person frame, and that is the mod's own
 * schema talking. `perspective: free` means an orbit about vanilla's third-person pivot with
 * zero translation; a camera that can leave the body is a freecam, and a freecam is scouting,
 * which `schema/mods/freelook.json` puts on §6.1's "never, under any framing" list. A reader
 * forms their idea of what a setting does from the picture, so the picture may not be able to
 * show the wrong thing — and a mark tied to the middle of a fixed ring cannot.
 *
 * Seen from above is also the only view that has both halves of the sentence on it: the body's
 * facing and the camera's bearing. In first person the body is off screen by definition.
 *
 * The thumbnail is **the same component at tile density**, through `dense`. Not a convenience:
 * the grid used to draw its own 7x7 bitmap with the mod's name captioned under it, so a player
 * who opened this mod went from a blocky glyph to a diagram and had to work out that they were
 * the same mod. A picture that needs its own name printed underneath is a picture that is not
 * working. At tile density the two timeline rows are dropped and the orbit is the whole tile —
 * a 123px well holds one diagram, and the one that identifies this mod is the camera going
 * round the player.
 *
 * No `previewZoom`. The orbit is authored at page size — a 200px ring in a frame with room for
 * two cell rows under it — not at HUD size, so there is nothing to magnify; `previewZoom` is
 * for a widget drawn for a 1458px view and read on a panel, which no diagram is.
 */

import { defineMod } from './types';
import { FreelookPreview } from '@/menu/gameplay-previews';

function FreelookPreviewThumbnail(): React.ReactElement {
  return <FreelookPreview dense className="tart" />;
}

export default defineMod({
  id: 'freelook',
  Thumbnail: FreelookPreviewThumbnail,
  Preview: FreelookPreview,
});
