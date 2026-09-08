/**
 * The VOID mark, drawn over the game.
 *
 * The one thumbnail that is literally the widget. Every other tile is a *picture* of what the
 * mod draws, redrawn at tile size; the mark is small enough that the real thing fits, and
 * drawing an impression of a mark instead of the mark would be absurd. It reads the same
 * `style` setting the HUD does, so switching to `word` changes the tile too.
 */

import { defineMod } from './types';
import { cells } from './art';
import { Watermark, HudWatermark, watermarkStyle } from '@/hud/watermark';
import { useModSettings } from '@/store/store';
import type { ThumbnailProps } from './types';

function WatermarkThumbnail({ scale = 1 }: ThumbnailProps): React.ReactElement {
  const settings = useModSettings('watermark');
  return (
    <span className="tart tart--stack">
      <Watermark
        style={watermarkStyle(settings.style)}
        cell={cells(3, scale)}
        className="tart__mark"
      />
    </span>
  );
}

export default defineMod({
  id: 'watermark',
  Thumbnail: WatermarkThumbnail,
  Preview: HudWatermark,
  // The mark is drawn at 3px cells to sit under the fps chip without out-ranking it
  // (`hud/watermark.tsx`); it needs the most enlargement of anything here to be looked *at*.
  previewZoom: 3.4,
});
