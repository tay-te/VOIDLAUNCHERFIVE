/**
 * Frame limiter — lower frame rates where nobody is looking.
 *
 * One joined row per situation, each filled to its cap on a 240 fps scale, against a reference
 * row for playing, which is never capped.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { FrameLimiterPreview } from '@/menu/visual-previews';

function FrameLimiterPreviewThumbnail(): React.ReactElement {
  return <FrameLimiterPreview dense className="tart" />;
}

export default defineMod({
  id: 'frame_limiter',
  Thumbnail: FrameLimiterPreviewThumbnail,
  Preview: FrameLimiterPreview,
});
