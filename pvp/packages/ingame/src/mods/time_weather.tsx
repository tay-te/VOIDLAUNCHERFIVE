/**
 * Time & weather — a fixed time of day and no rain, on your screen only.
 *
 * The sky: the sun or moon on its arc at the held time, ghost suns when the time follows the
 * server, and rain over the dome when the weather is left on.
 *
 * One of the mods that draw into the **world** — so the preview is a diagram
 * (`menu/visual-previews.tsx`, which opens with what that still promises), and the thumbnail is
 * **the same component at tile density**, through `dense`, exactly as `overlay.tsx` does it.
 */

import { defineMod } from './types';
import { TimeWeatherPreview } from '@/menu/visual-previews';

function TimeWeatherPreviewThumbnail(): React.ReactElement {
  return <TimeWeatherPreview dense className="tart" />;
}

export default defineMod({
  id: 'time_weather',
  Thumbnail: TimeWeatherPreviewThumbnail,
  Preview: TimeWeatherPreview,
});
