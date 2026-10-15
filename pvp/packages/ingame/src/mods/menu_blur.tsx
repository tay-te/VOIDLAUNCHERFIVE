/**
 * Menu blur — the world behind an inventory, softened and dimmed.
 *
 * An inventory over the world, with the world run through `blur_radius` passes of a box blur and
 * dimmed to `menu_dim` of vanilla's wash; the row under it is the fade as the screen opens.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { MenuBlurPreview } from '@/menu/visual-previews';

function MenuBlurPreviewThumbnail(): React.ReactElement {
  return <MenuBlurPreview dense className="tart" />;
}

export default defineMod({
  id: 'menu_blur',
  Thumbnail: MenuBlurPreviewThumbnail,
  Preview: MenuBlurPreview,
});
