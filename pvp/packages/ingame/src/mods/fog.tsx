/**
 * Fog — distance, water and lava fog, each vanilla, reduced or off.
 *
 * Three joined rows of what you can see from your eye to the edge of the view; where a row fades
 * is where its fog is.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { FogPreview } from '@/menu/visual-previews';

function FogPreviewThumbnail(): React.ReactElement {
  return <FogPreview dense className="tart" />;
}

export default defineMod({
  id: 'fog',
  Thumbnail: FogPreviewThumbnail,
  Preview: FogPreview,
});
