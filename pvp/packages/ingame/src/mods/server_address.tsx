/**
 * Server address — the host you are connected to.
 *
 * **The one chip mod whose reading is not a figure, and the tile says so.** `Readout` is the
 * shape of every other chip's tile — one big number over its unit — and it is the wrong shape
 * here twice over: a hostname is not a quantity, so the 30px `tnum` figure tier would be
 * printing letters in a slot built for digits, and it does not fit. `mc.hypixel.net` at that
 * tier is nearly 200px inside a 123px well.
 *
 * So the tile keeps the family's composition — the reading, with a caption under it — and drops
 * a tier: the host is drawn at `.tart__value`, which is the same 14px primary ink the
 * coordinates tile prints its axes at. The caption names what the string *is*, because that is
 * the honest analogue of `FPS` or `MS` under a figure: a unit says what a number counts, and
 * `SERVER` says what this address names.
 *
 * **Always the short form**, whatever the loadout says. The tile is 123px of width and the
 * short form is by construction the one word players say out loud, so it fits; `full` exists
 * for widths a tile does not have, and the page's preview is the real chip and shows whichever
 * the player picked. Live off the store, with the frames' own host standing in when there is no
 * connection — the tile is drawn in a grid a player reaches from the main menu, so "no server"
 * is the state it is most often looked at in.
 *
 * Monochrome, like every tile (§1): this mod has no `color` setting to read even if it were
 * allowed to.
 */

import { defineMod } from './types';
import { HudServerAddress } from '@/hud/server_address';
import { shortHost } from '@/hud/format';
import { useVoidStore } from '@/store/store';

function ServerAddressThumbnail(): React.ReactElement {
  const host = useVoidStore((s) => s.server.host);
  return (
    <span className="tart tart--stack">
      <span className="tart__value">{shortHost(host.trim() === '' ? 'mc.hypixel.net' : host)}</span>
      <span className="tart__unit">SERVER</span>
    </span>
  );
}

export default defineMod({
  id: 'server_address',
  Thumbnail: ServerAddressThumbnail,
  Preview: HudServerAddress,
  // **Less than the chip mods' 3, and the content is why.** Those four enlarge a two- or
  // three-glyph figure; this one enlarges a whole address, and a mod with six properties gets
  // the grouped structure — two 300px property columns — which leaves the preview frame ~570px
  // to hold it. At 3 a twenty-character host runs past that and is clipped, which would clip
  // exactly the setting the page is there to demonstrate: `full` is for reading the whole
  // address. 2.4 is the most enlargement that still holds one.
  previewZoom: 2.4,
});
