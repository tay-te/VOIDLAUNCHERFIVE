/**
 * GUI scale — the hotbar and inventories sized apart from Minecraft's GUI scale.
 *
 * A 1080p screen with the hotbar and an open inventory at their real GUI-pixel sizes times the
 * step. A step of 0 follows Minecraft and is drawn as an outline at the 1080p auto scale.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { GuiScalePreview } from '@/menu/visual-previews';

function GuiScalePreviewThumbnail(): React.ReactElement {
  return <GuiScalePreview dense className="tart" />;
}

export default defineMod({
  id: 'gui_scale',
  Thumbnail: GuiScalePreviewThumbnail,
  Preview: GuiScalePreview,
});
