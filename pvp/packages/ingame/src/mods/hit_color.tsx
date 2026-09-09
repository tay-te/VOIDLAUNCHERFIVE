/**
 * Hit colour — the flash on the entity you hit, and on the one you did not.
 *
 * One of the mods that draw into the **world**, where there is no HTML to reuse — so the
 * preview is a diagram, and `menu/gameplay-previews.tsx` opens with why that is a different
 * promise and what it still guarantees: the numbers are the game's own even though the drawing
 * is not. The number here is a ceiling rather than a quantity — `intensity` is a fraction of
 * vanilla's own hurt-overlay alpha, so 1 is exactly what the game already draws and there is
 * nothing above it, which is the whole of `schema/mods/hit_color.json`'s §11 argument. The
 * diagram's bar fills to an end it cannot pass.
 *
 * **This is the file's one licensed colour, and only on the page.** §1's accent rule stops
 * where a mark is drawn over the game world — that is the clause `hitboxes.color` already
 * lives under — and a hurt overlay is exactly such a mark, so the page draws the ink the player
 * chose. The **tile does not**: §1's other half is "any colour on the menu's own surface, the
 * mod tiles included", the page is where a colour is chosen and therefore where it is shown,
 * and the grid is chrome. The thumbnail is the same component reading the same setting and
 * declining to paint with it, exactly as the hitbox and crosshair tiles do.
 *
 * The thumbnail is **the same component at tile density**, through `dense`. Not a convenience:
 * the grid used to draw its own 7x7 bitmap with the mod's name captioned under it, so a player
 * who opened this mod went from a blocky glyph to a diagram and had to work out that they were
 * the same mod. A picture that needs its own name printed underneath is a picture that is not
 * working.
 *
 * No `previewZoom`: the figures are authored at the size the frame gives them, not at HUD size.
 */

import { defineMod } from './types';
import { HitColorPreview } from '@/menu/gameplay-previews';

function HitColorPreviewThumbnail(): React.ReactElement {
  return <HitColorPreview dense className="tart" />;
}

export default defineMod({
  id: 'hit_color',
  Thumbnail: HitColorPreviewThumbnail,
  Preview: HitColorPreview,
});
