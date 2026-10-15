/**
 * Nametags — your own tag in third person, and every tag's plate, size and shadow.
 *
 * Two players, you and somebody else, each with the plate the settings give them. The plate is
 * black at the stored alpha on a grey ground, because that is what the game draws.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { NametagsPreview } from '@/menu/visual-previews';

function NametagsPreviewThumbnail(): React.ReactElement {
  return <NametagsPreview dense className="tart" />;
}

export default defineMod({
  id: 'nametags',
  Thumbnail: NametagsPreviewThumbnail,
  Preview: NametagsPreview,
});
