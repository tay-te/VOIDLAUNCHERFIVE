/**
 * Clear glass — glass drawn as clear air.
 *
 * Three windows onto the same figure — a glass block, a pane, stained glass. Drawn glass sits over
 * the figure; cleared glass leaves only a ghost of its edge.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { ClearGlassPreview } from '@/menu/visual-previews';

function ClearGlassPreviewThumbnail(): React.ReactElement {
  return <ClearGlassPreview dense className="tart" />;
}

export default defineMod({
  id: 'clear_glass',
  Thumbnail: ClearGlassPreviewThumbnail,
  Preview: ClearGlassPreview,
});
