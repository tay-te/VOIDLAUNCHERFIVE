/**
 * Entity culling — skips what is behind walls or too far away to matter.
 *
 * A plan view: the camera, a wall, and the players and chests on either side of it. Behind the
 * wall they drop to ghosts while occlusion is on; the ring is `always_draw_within`, and the four
 * rows under it are the distance limits on a 0-64 block scale.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { CullingPreview } from '@/menu/visual-previews';

function CullingPreviewThumbnail(): React.ReactElement {
  return <CullingPreview dense className="tart" />;
}

export default defineMod({
  id: 'culling',
  Thumbnail: CullingPreviewThumbnail,
  Preview: CullingPreview,
});
