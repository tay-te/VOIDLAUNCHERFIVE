/**
 * Memory — JVM heap in use, against the launcher's ceiling.
 *
 * The tile draws the live heap in megabytes, whatever `style` says. `percent` is the narrowest
 * form on the HUD and the worst one here: `36` over a unit is a number that could belong to any
 * mod in the grid, where a four-digit figure over `MB` is only ever one thing. The tile's job is
 * identification (`mods/types.ts`), and the page below it is where the three styles are chosen
 * and shown.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudMemory } from '@/hud/memory';
import { useVoidStore } from '@/store/store';

/** The widget's own fixture, used before a reading lands — see `hud/memory.tsx`. */
const FALLBACK_USED_MB = 1462;

function MemoryThumbnail(): React.ReactElement {
  const heap = useVoidStore((s) => s.memory);
  return <Readout value={String(Math.round(heap?.usedMb ?? FALLBACK_USED_MB))} unit="MB" />;
}

export default defineMod({
  id: 'memory',
  Thumbnail: MemoryThumbnail,
  Preview: HudMemory,
  // The chip mods share 3 — the same object at the same size over the game.
  previewZoom: 3,
});
