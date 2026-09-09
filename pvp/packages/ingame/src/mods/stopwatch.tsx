/**
 * Stopwatch — a manual timer, started and zeroed from the keyboard.
 *
 * The tile is the chip mods' own shape, `Readout`: the reading over a caption naming what it
 * is. A clock reading is exactly what that shape is for — the figure is `tnum` and every field
 * of a clock is a digit — and the caption carries the identification, which is the tile's whole
 * job (`mods/types.ts`).
 *
 * **The caption is `ELAPSED` rather than `TIMER`.** A unit says what a number *counts*, the way
 * `FPS`, `MS` and `HELD` do on the neighbouring tiles; `TIMER` would name the mod again, which
 * the label under the tile already does. It also draws the distinction this mod is most likely
 * to be mistaken for at a glance in a grid: a countdown and an elapsed time are the same three
 * fields, and only one of them is what this is.
 *
 * **Always `auto`, whatever the loadout says**, for `server_address`'s reason: the tile is 123px
 * of width, `auto` is by construction the narrowest of the three formats, and `hmmss` at a 30px
 * figure tier would be printing `0:04:07` into a well that does not hold it. The page below is
 * where a format is chosen and shown, on the real chip.
 *
 * **The live reading when there is one.** Like every readout tile, this shows the player's own
 * value where a real one exists — a clock they have started and left running reads on the tile
 * as it reads on the HUD. A never-started clock falls back to the widget's own fixture rather
 * than drawing `0:00`, because the grid is reached from the main menu, so the untouched clock
 * is the state this tile is most often looked at in, and `0:00` is a picture of a mod that does
 * nothing. Nothing repaints it behind the open menu: the only writer of the run state is a key
 * press, and the tile subscribes to that state rather than to a clock, so an idle menu still
 * paints nothing (§4).
 *
 * Monochrome, like every tile (§1) — this mod has no colour setting to read even if it were
 * allowed to.
 */

import { useSyncExternalStore } from 'react';

import { formatElapsed } from '@/ui';
import { defineMod } from './types';
import { Readout } from './art';
import { HudStopwatch, elapsedOf, stopwatchRun, subscribeRun } from '@/hud/stopwatch';

/** The widget's own fixture, drawn when the clock has never run — see `hud/stopwatch.tsx`. */
const FALLBACK_ELAPSED_MS = 247_620;

function StopwatchThumbnail(): React.ReactElement {
  const run = useSyncExternalStore(subscribeRun, stopwatchRun);
  const elapsed = elapsedOf(run, Date.now());
  // `> 0`, not `?? `: a stopped clock at zero is indistinguishable from one that was never
  // started, and both are the state the fixture exists for. A running clock in its first second
  // reads `0:00` for that second and then climbs, which is correct and self-explaining.
  const reading = formatElapsed(elapsed > 0 ? elapsed : FALLBACK_ELAPSED_MS);
  return <Readout value={reading} unit="ELAPSED" />;
}

export default defineMod({
  id: 'stopwatch',
  Thumbnail: StopwatchThumbnail,
  Preview: HudStopwatch,
  // The chip mods' number. `4:07` is the same object at the same size over the game as `142 fps`
  // or `22 ms`, and a set of chips at different magnifications would say they were not.
  previewZoom: 3,
});
