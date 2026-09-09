/**
 * Old animations — a swing that survives a raised sword, and the free swing that never changed.
 *
 * One of the mods that draw into the **world**, where there is no HTML to reuse — so the preview
 * is a diagram, and `menu/gameplay-previews.tsx` opens with why that is a different promise and
 * what it still guarantees: the numbers are the game's own even though the drawing is not. Here
 * they are `MinecraftClient.doAttack`'s ten-tick miss cooldown, which the click timeline is
 * scaled to, and the fact that the 1.7 block-hit is the *ordinary* swing arc applied on top of
 * the guard pose rather than a motion of its own — one float argument, which the schema quotes.
 *
 * **The left stage of the diagram reads no setting, and it is the point of the diagram.** The
 * roster ranks this mod first of everything and describes it as reverting "swing, block-hit and
 * item-use animation"; disassembled, the swing is identical in the two versions down to
 * `-0.71999997f`, and what players remember as "the 1.7 swing" is the held-item render pipeline,
 * which is a renderer rewrite and not a setting. A player enabling a mod called Old animations
 * expects their swing to change. It will not. Drawing the free swing in full, unchanged by the
 * only setting there is, is the settings page saying so in the one place a player actually
 * reads — and it is simultaneously the reference that makes the frozen 1.8 block-hit beside it
 * legible as *frozen* rather than merely as an arm.
 *
 * `swing_during_delay` is the diagram's other job and the harder one. It plays a local arm swing
 * for clicks 1.8 has already eaten and gives none of them back, so a drawing that only got
 * livelier would tell a player the opposite of the truth. The timeline separates what you did
 * from what the client did from what left the client, and the last of those three rows is
 * identical whichever way the switch is thrown. `old_input.no_miss_delay` draws the same row and
 * moves it; that pair of drawings is the difference between a `safe` mod and a `grey` one.
 *
 * The thumbnail is **the same component at tile density**, through `dense`. Not a convenience:
 * the grid used to draw its own 7x7 bitmap with the mod's name captioned under it, so a player
 * who opened this mod went from a blocky glyph to a diagram and had to work out that they were
 * the same mod. A picture that needs its own name printed underneath is a picture that is not
 * working. At tile density the click timeline is dropped and the two stages are the whole tile —
 * a 123px well holds one diagram, and the one that identifies this mod is an arm moving through
 * a raised sword.
 *
 * No `previewZoom`: the stages are authored at page size, not at HUD size, so there is nothing
 * to magnify.
 */

import { defineMod } from './types';
import { AnimationPreview } from '@/menu/gameplay-previews';

function AnimationPreviewThumbnail(): React.ReactElement {
  return <AnimationPreview dense className="tart" />;
}

export default defineMod({
  id: 'old_animations',
  Thumbnail: AnimationPreviewThumbnail,
  Preview: AnimationPreview,
});
