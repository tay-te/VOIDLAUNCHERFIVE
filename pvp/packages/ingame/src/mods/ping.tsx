/**
 * Ping display — round-trip time to the current server.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudPing } from '@/hud/widgets';
import { useVoidStore } from '@/store/store';

function PingThumbnail(): React.ReactElement {
  const ping = useVoidStore((s) => s.ping);
  // The bridge sends -1 for "no server", which is not a ping and must not be printed as one.
  return <Readout value={String(ping > 0 ? ping : 22)} unit="MS" />;
}

export default defineMod({
  id: 'ping',
  Thumbnail: PingThumbnail,
  Preview: HudPing,
  // Two, not the chip mods' shared 3. The preview is a band of three chips (one per threshold
  // band, `HudPing`), each carrying the host and — with `show_average` / `show_jitter` on — two
  // trailing asides; at 3x the widest of them ran past both edges of the frame and the figure
  // the settings are about was the part clipped off.
  previewZoom: 2,
});
