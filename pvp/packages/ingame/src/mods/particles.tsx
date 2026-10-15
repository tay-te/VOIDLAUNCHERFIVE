/**
 * Particle limiter — the cap, the view cone, and thinner bursts.
 *
 * A plan view of the view cone: particles outside it are ghosts while `frustum_cull` is on, and
 * the block-break burst and explosion cloud keep the fraction of their pieces the settings say.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { ParticlesPreview } from '@/menu/visual-previews';

function ParticlesPreviewThumbnail(): React.ReactElement {
  return <ParticlesPreview dense className="tart" />;
}

export default defineMod({
  id: 'particles',
  Thumbnail: ParticlesPreviewThumbnail,
  Preview: ParticlesPreview,
});
