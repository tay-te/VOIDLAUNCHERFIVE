/**
 * Damage tint — your own screen at low health, and what a hit does to it.
 *
 * One of the mods that draw into the **world**, where there is no HTML to reuse — so the
 * preview is a diagram, and `menu/gameplay-previews.tsx` opens with why that is a different
 * promise and what it still guarantees: the numbers are the game's own even though the drawing
 * is not. Here they are vanilla's 0-20 half-heart scale, which the marker sits on, and the
 * fourteen degrees `EntityRenderer#hurtCameraEffect` rolls the view by, which the ghost horizon
 * leans at.
 *
 * The frame is `OverlayPreview`'s frame, reused whole. Both mods' subject is the player's own
 * view, and drawing one thing two ways would ask a player to learn a private idiom per mod.
 * What is in it is entirely different — overlay's screen is full of things being taken out of
 * it, and this one is empty except for what is happening to its edges and its horizon — so the
 * two tiles cannot be confused for each other.
 *
 * **The vignette is drawn grey, and in game it is red.** The schema is emphatic that red is the
 * right colour there and that this mod owns none of the wave's colour settings precisely so
 * `hit_color` can default away from red. On the menu's own surface §1 is absolute, and the one
 * exception this wave takes is `hit_color`'s, where the colour *is* the setting; here it is a
 * constant. So the diagram keeps the vignette's shape — the edges closing in — which is the part
 * `threshold` and `strength` actually move, and the reading under it names the colour.
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
import { DamageTintPreview } from '@/menu/gameplay-previews';

function DamageTintPreviewThumbnail(): React.ReactElement {
  return <DamageTintPreview dense className="tart" />;
}

export default defineMod({
  id: 'damage_tint',
  Thumbnail: DamageTintPreviewThumbnail,
  Preview: DamageTintPreview,
});
