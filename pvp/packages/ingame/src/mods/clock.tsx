/**
 * Clock — the real-world time.
 *
 * The tile prints the same reading the chip does, in the same format the player picked, at the
 * grid's figure tier. `Readout` is the right shape here where it was the wrong one for the server
 * address: a time *is* a quantity of digits, it is short, and `21:41` over `TIME` is the family's
 * own composition with nothing bent to fit.
 *
 * **Live, and the only tile that is live off nothing.** Every other thumbnail either reads the
 * store or draws a fixture; this one reads `Date`, which is correct in the grid for exactly the
 * reason it is correct on the HUD — the machine knows what time it is whether or not a game is
 * running. It does not repaint on its own: a tile is looked at for a second or two and a grid of
 * thirty-two that ticked would be thirty-two full-panel damage rectangles a minute, which is what
 * `hud/second-edge.ts`'s budget is written to avoid. It is right when the grid opens, which is
 * when it is read.
 *
 * Monochrome (§1), like every tile — this mod has no `color` setting to read even if it were
 * allowed to.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudClock, formatClock } from '@/hud/clock';
import { modSettings, useVoidStore } from '@/store/store';

function ClockThumbnail(): React.ReactElement {
  const format = useVoidStore((s) => (modSettings(s.loadout, 'clock').format === 'h12' ? 'h12' : 'h24'));
  const now = new Date();
  // Never the seconds, whatever the loadout says. A tile is identification at 145px and a third
  // field costs a glyph tier; the page below is where the format is chosen and shown in full.
  const reading = formatClock(
    { hours: now.getHours(), minutes: now.getMinutes(), seconds: now.getSeconds() },
    format,
    false,
  );
  return <Readout value={reading.value} unit={reading.unit ?? 'TIME'} />;
}

export default defineMod({
  id: 'clock',
  Thumbnail: ClockThumbnail,
  Preview: HudClock,
  // The chip mods share 3 — the same object at the same size over the game. `h12` with seconds
  // is the widest form this draws (`9:41:07 PM`) and it holds at 3 in the preview frame, so the
  // narrower `server_address` figure is not needed here.
  previewZoom: 3,
});
