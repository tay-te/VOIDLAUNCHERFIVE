/**
 * FOV changer — the field of view, drawn as the angle it is.
 *
 * One of the mods that draw into the **world**, where there is no HTML to reuse — so the
 * preview is a diagram, and `menu/gameplay-previews.tsx` opens with why that is a different
 * promise and what it still guarantees: the numbers are the game's own even though the drawing
 * is not. Here they are the two multipliers `EntityPlayerSP#getFOVModifier` applies, 1.15 for a
 * sprint and 0.85 for a drawn bow, which is what `lock_sprint` and `lock_bow` suppress.
 *
 * The thumbnail is **the same component at tile density**, through `dense`. Not a convenience:
 * the grid used to draw its own 7x7 bitmap with the mod's name captioned under it, so a player
 * who opened this mod went from a blocky glyph to a diagram and had to work out that they were
 * the same mod. A picture that needs its own name printed underneath is a picture that is not
 * working.
 *
 * No `previewZoom`. The fan is authored at page size — a 400px half-disc against the frame's
 * ~300px of height — rather than at HUD size, so there is nothing to magnify; `previewZoom` is
 * for a widget drawn for a 1458px view and read on a panel, which no diagram is.
 */

import { defineMod } from './types';
import { FovPreview } from '@/menu/gameplay-previews';

function FovPreviewThumbnail(): React.ReactElement {
  return <FovPreview dense className="tart" />;
}

export default defineMod({
  id: 'fov',
  Thumbnail: FovPreviewThumbnail,
  Preview: FovPreview,
});
