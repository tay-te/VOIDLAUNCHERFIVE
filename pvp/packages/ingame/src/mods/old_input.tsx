/**
 * Old input — the ticks 1.8 takes off you, and the three switches that give them back.
 *
 * One of the mods that draw into the **world**, where there is no HTML to reuse — so the preview
 * is a diagram, and `menu/gameplay-previews.tsx` opens with why that is a different promise and
 * what it still guarantees: the numbers are the game's own even though the drawing is not. Here
 * the number is `attackCooldown = 10`, the miss cooldown the click row is scaled to, shared as
 * one constant with `old_animations` so the two mods cannot draw the same window at two lengths.
 *
 * Every setting is a guard 1.8 added and 1.7 did not have, which makes the drawing's subject
 * *time you do not get*: two bars that may not overlap, and a click row with holes under it. The
 * two interlocks are mirrors, so they share one timeline and each opens one end of it; the whiff
 * gets a pair of its own, because mining and attacking are the same button and one row cannot
 * honestly be both. `InputPreview` carries the full argument.
 *
 * **The mod ships off with all three switches off, so the factory drawing is 1.8 with three
 * gates closed.** That is not a picture of a mod doing nothing — the holes and the gap are the
 * mod's subject, and the player is being shown what they currently pay before they are asked
 * whether to stop paying it. There is no value of any switch that makes the picture quieter.
 *
 * **This is the registry's fourth `grey` mod, and the art does not mention it.** The badge is
 * `crates/void-loadout`'s job and the classification is argued at length in the schema; a
 * diagram that editorialised would be doing a worse version of both. What the drawing does
 * instead is refuse to make the switches look free: `no_miss_delay` is drawn as marks appearing
 * in a row named for what leaves the client, and the reading says how many more swing packets
 * that is on the miss shown. A player pricing the trade has the number, from the picture.
 *
 * The thumbnail is **the same component at tile density**, through `dense`. Not a convenience:
 * the grid used to draw its own 7x7 bitmap with the mod's name captioned under it, so a player
 * who opened this mod went from a blocky glyph to a diagram and had to work out that they were
 * the same mod. A picture that needs its own name printed underneath is a picture that is not
 * working. At tile density the whiff pair is dropped — four labelled rows come to ~126px in a
 * well that can be 123 — and the two interlock bars are the tile, which is the pair that carries
 * the mod's name: two inputs 1.8 will not let be live at once.
 *
 * No `previewZoom`: the rows are authored at page size, not at HUD size.
 */

import { defineMod } from './types';
import { InputPreview } from '@/menu/gameplay-previews';

function InputPreviewThumbnail(): React.ReactElement {
  return <InputPreview dense className="tart" />;
}

export default defineMod({
  id: 'old_input',
  Thumbnail: InputPreviewThumbnail,
  Preview: InputPreview,
});
