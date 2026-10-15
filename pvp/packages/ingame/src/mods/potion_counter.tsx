/**
 * Potion counter — heals left, as a figure over its noun.
 *
 * The chip mods' `Readout`: a count and what it counts. Live off the store (`counts`), and it
 * reads `track` so the tile and the chip agree on *what* is being counted — `POTS`, `SOUP`, or
 * both figures with `POTS · SOUP` under them. Monochrome: `low_threshold` turns the HUD's figure
 * amber over the game world, and §1 keeps that off the menu's own surface.
 *
 * `??` rather than `||`, like every count in this client: 0 heals is the reading that matters.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudPotionCounter } from '@/hud/potion_counter';
import { modSettings, useVoidStore } from '@/store/store';

function PotionCounterThumbnail(): React.ReactElement {
  const pots = useVoidStore((s) => s.counts?.health_potion ?? null);
  const soup = useVoidStore((s) => s.counts?.soup ?? null);
  const track = useVoidStore((s) => modSettings(s.loadout, 'potion_counter').track);
  if (track === 'soup') return <Readout value={String(soup ?? 5)} unit="SOUP" />;
  if (track === 'both') {
    return <Readout value={`${pots ?? 7} | ${soup ?? 5}`} unit="POTS · SOUP" />;
  }
  return <Readout value={String(pots ?? 7)} unit="POTS" />;
}

export default defineMod({
  id: 'potion_counter',
  Thumbnail: PotionCounterThumbnail,
  Preview: HudPotionCounter,
  // The chip mods share 3: the same object at the same size over the game.
  previewZoom: 3,
});
