/**
 * Crosshair — the vanilla crosshair pass, replaced.
 *
 * The second thumbnail that is the real widget rather than a picture of one, and for the same
 * reason as the watermark: it is small enough that the real thing fits, and its whole identity
 * is a shape the settings change. It used to be a fixed 7 x 7 bitmap of a plus, so a player who
 * had chosen `circle` or `t_shape` saw a cross on the tile.
 *
 * **Monochrome, like the other twelve.** `crosshair.color` is not passed: no thumbnail takes a
 * colour (`mods/art.tsx`), and every other tile keeps that — the FPS tile does not show
 * `fps.color` and the potion tile does not show effect colours. The page, which is where the
 * colour is chosen, does show it.
 */

import { defineMod } from './types';
import type { ThumbnailProps } from './types';
import { Crosshair, asCrosshairStyle } from '@/ui';
import { HudCrosshair } from '@/hud/widgets';
import { useModSettings } from '@/store/store';

function CrosshairThumbnail({ scale = 1 }: ThumbnailProps): React.ReactElement {
  const settings = useModSettings('crosshair');
  return (
    <span className="tart tart--stack">
      <Crosshair
        shape={asCrosshairStyle(settings.style)}
        size={Number(settings.size ?? 5)}
        thickness={Number(settings.thickness ?? 1)}
        gap={Number(settings.gap ?? 2)}
        outline={false}
        centerDot={settings.center_dot === true}
        unit={Math.max(2, Math.round(3 * scale))}
      />
    </span>
  );
}

export default defineMod({
  id: 'crosshair',
  Thumbnail: CrosshairThumbnail,
  Preview: HudCrosshair,
  // No `previewZoom`: the crosshair carries its own multiplier (`unit`), because every length
  // in it is arithmetic rather than layout, and multiplying the arithmetic keeps the 1px
  // outline a 1px outline.
});
