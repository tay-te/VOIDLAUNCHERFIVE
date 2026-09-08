/**
 * Direction — which way you are facing, as its own placeable readout.
 *
 * The first mod of Wave 2 (`docs/mod-roster.md` §7), and picked to go first for a reason that
 * is about the architecture rather than the mod: `pos.yaw` is already on the `tick` payload for
 * Coordinates, so this needed **no Java sensor, no protocol change and no actuator** — which
 * made it the honest test of whether `docs/adding-a-mod.md` actually holds end to end.
 *
 * `coordinates.show_direction` is not replaced by this and is not a duplicate of it. That one
 * is the *inline* form, a suffix on the coordinate rows for a player who wants one chip; this
 * is the standalone one, placed and sized on its own, for a player who wants the facing legible
 * across the screen and does not care where they are. Both are on both competitors' rosters.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudDirection } from '@/hud/widgets';
import { useVoidStore } from '@/store/store';
import { yawIndex } from '@/ui';

/**
 * The compass point over its angle.
 *
 * Live off the store, like the other readout tiles — a player looking at the grid sees the way
 * they are actually facing, which is the fastest possible way to understand what the mod is.
 * The frame's own fixture yaw stands in before the first `tick`.
 *
 * Monochrome, and it draws the `letter` style regardless of the mod's own `style` setting: a
 * tile is identification, and `Southwest` or `+X +Z` at 145px is a line of text rather than a
 * readout. The page draws the real thing, at the real setting.
 */
function DirectionThumbnail(): React.ReactElement {
  const pos = useVoidStore((s) => s.pos);
  const yaw = pos?.yaw ?? 45;
  const degrees = Math.round((((yaw % 360) + 360) % 360));
  return <Readout value={yawIndex(yaw)} unit={`${degrees}°`} />;
}

export default defineMod({
  id: 'direction',
  Thumbnail: DirectionThumbnail,
  Preview: HudDirection,
  // A chip, so the same 3 the other four chips use — they are the same object and a set of
  // chips at different magnifications would say they were not.
  previewZoom: 3,
});
