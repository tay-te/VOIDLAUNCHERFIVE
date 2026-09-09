/**
 * Momentum — horizontal ground speed, in blocks per second or km/h.
 *
 * The tile draws the real speed off the store, so it is the number the HUD would be drawing,
 * and it reads `unit` because a tile printing `BPS` beside a chip printing `KM/H` would be two
 * pictures of one mod disagreeing. It does not read `decimals`: at 145px the figure is the
 * whole tile, and a tile pinned to 0 dp by a setting would sit still while the player moves,
 * which is the one thing a live tile is for.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudMomentum } from '@/hud/momentum';
import { modSettings, useVoidStore } from '@/store/store';

/** Vanilla 1.8 sprint, the widget's own fixture — see `hud/momentum.tsx` for why that number. */
const FALLBACK_BPS = 5.61;

function MomentumThumbnail(): React.ReactElement {
  const speed = useVoidStore((s) => s.speed);
  const kmh = useVoidStore((s) => modSettings(s.loadout, 'momentum').unit === 'kmh');
  // `null` is "no reading", and it is the common case here — the tick is suppressed while the
  // menu is up, so a grid opened before the sensor ever sent one has nothing to show.
  const bps = speed ?? FALLBACK_BPS;
  // Uppercase like every other tile's unit (`FPS`, `CPS`, `MS`). `.tart__unit` upper-cases in
  // CSS regardless; writing it as it is drawn keeps the set greppable.
  return <Readout value={(kmh ? bps * 3.6 : bps).toFixed(1)} unit={kmh ? 'KM/H' : 'BPS'} />;
}

export default defineMod({
  id: 'momentum',
  Thumbnail: MomentumThumbnail,
  Preview: HudMomentum,
  // The chip mods share 3, and share it deliberately: they are the same object at the same size
  // over the game, and previewing them at four different magnifications would say they were not.
  previewZoom: 3,
});
