/**
 * Shiny pots — a strong glint on every potion in the hotbar.
 *
 * A hotbar of pots, each crossed by `glint_passes` shimmer bands. The page draws the glint's ink,
 * vanilla's purple or each pot's own (`tint_glint`), because that colour is the setting; the
 * tile stays monochrome.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { ShinyPotsPreview } from '@/menu/visual-previews';

function ShinyPotsPreviewThumbnail(): React.ReactElement {
  return <ShinyPotsPreview dense className="tart" />;
}

export default defineMod({
  id: 'shiny_pots',
  Thumbnail: ShinyPotsPreviewThumbnail,
  Preview: ShinyPotsPreview,
});
