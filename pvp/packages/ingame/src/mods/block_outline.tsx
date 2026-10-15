/**
 * Block outline — the mark on the block you are looking at.
 *
 * Three blocks in a row, the middle one targeted, at the mod's own style, width and colours.
 * **The page draws the player's ink** — the outline is a mark over the game world, the clause
 * hitboxes and hit colour live under — and the tile does not: it reads the same settings and
 * declines to paint with them, as the hitbox tile does.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { BlockOutlinePreview } from '@/menu/visual-previews';

function BlockOutlinePreviewThumbnail(): React.ReactElement {
  return <BlockOutlinePreview dense className="tart" />;
}

export default defineMod({
  id: 'block_outline',
  Thumbnail: BlockOutlinePreviewThumbnail,
  Preview: BlockOutlinePreview,
});
