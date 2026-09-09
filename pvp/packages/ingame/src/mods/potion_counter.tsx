/**
 * Potion counter — how many of one potion you are carrying.
 *
 * `Readout` is the family's shape and fits without bending: a count over a unit, `6` over `HEALS`.
 * The unit is the mod's own word rather than the effect's — a tile is identification, and `HEALS`
 * says what this counts where `INSTANT HEALTH` would say what the effect does to you, which is the
 * effects list's subject two tiles away.
 *
 * Live off the store, with the widget's fixture standing in before the first reading. The grid is
 * reachable from the main menu, where "no inventory yet" is the state the tile is most often
 * looked at in.
 *
 * Monochrome (§1). The chip carries the effect's swatch over live game pixels, where a colour
 * identifies a value; the grid stays quiet.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudPotionCounter } from '@/hud/potion_counter';
import { modSettings, useVoidStore } from '@/store/store';

/** The four the schema offers, as 1.8.9's numeric potion ids — the widget's table, one place up. */
const EFFECT_IDS: Record<string, number> = {
  healing: 6,
  speed: 1,
  strength: 5,
  fire_resistance: 12,
};

const UNITS: Record<string, string> = {
  healing: 'HEALS',
  speed: 'SPEED',
  strength: 'STR',
  fire_resistance: 'FIRE RES',
  any: 'POTS',
};

/** The widget's own fixture, reduced to what a tile needs — see `hud/potion_counter.tsx`. */
const FALLBACK = 6;

function PotionCounterThumbnail(): React.ReactElement {
  const inventory = useVoidStore((s) => s.inventory);
  const effect = useVoidStore((s) => {
    const value = modSettings(s.loadout, 'potion_counter').effect;
    return typeof value === 'string' && value in UNITS ? value : 'healing';
  });
  const splashOnly = useVoidStore(
    (s) => modSettings(s.loadout, 'potion_counter').splash_only !== false,
  );
  const wanted = EFFECT_IDS[effect];
  const count =
    inventory === null
      ? FALLBACK
      : inventory.reduce((total, entry) => {
          if (entry.effect === undefined) return total;
          if (wanted !== undefined && entry.effect !== wanted) return total;
          if (splashOnly && entry.splash !== true) return total;
          return total + entry.count;
        }, 0);
  return <Readout value={String(count)} unit={UNITS[effect] ?? 'POTS'} />;
}

export default defineMod({
  id: 'potion_counter',
  Thumbnail: PotionCounterThumbnail,
  Preview: HudPotionCounter,
  // The chip mods share 3 — the same object at the same size over the game.
  previewZoom: 3,
});
