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
  previewZoom: 3,
});
