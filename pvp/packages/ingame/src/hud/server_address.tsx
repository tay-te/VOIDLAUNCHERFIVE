/**
 * Server address — the host you are actually connected to, on a chip of its own.
 *
 * ## Why it is here rather than in `widgets.tsx`
 *
 * The same reason `watermark.tsx` is: everything a *single* mod draws lives in that mod's own
 * module, so `widgets.tsx` stops growing when the roster does (`mods/art.tsx` states the
 * principle for the tile art, and it is the same principle). What is left in `widgets.tsx` is
 * the shape every HUD widget shares — {@link HudWidgetProps} — which this imports.
 *
 * ## Formatting is the widget's job, not the chip's
 *
 * `ServerAddressChip` takes an address that is **already formatted**, so `style` is resolved
 * here: `short` runs the host through `shortHost()` from `./format` — the one host formatter
 * this package has, and the same one `ping.show_host` prints its suffix with — and `full`
 * passes the host through exactly as it was connected to. Writing a second shortener beside
 * that one is how the standalone chip and the ping suffix would come to disagree about what
 * `mc.hypixel.net` is called.
 *
 * ## Not connected is behaviour, not a setting
 *
 * There is no `hide_offline`, and `schema/mods/server_address.json`'s `$comment` argues it out:
 * the `server` event carries an empty `host` on disconnect, so singleplayer and the main menu
 * are states this mod can *see*, and it draws nothing in them **unconditionally**. A switch
 * whose entire content is absence is not one of the things `design/quiet-cell-system.md` §1
 * calls customisation (position, scale, opacity, density, what is shown, format), and it would
 * have no preview to move — so it would need an exemption from the one list
 * `test/preview.test.tsx` says is where that gate can quietly erode.
 *
 * The rule is written here rather than left to the chip's own empty-host guard, because "the
 * widget is absent when there is no server" is this mod's behaviour and should not be an
 * accident of what a component in another package happens to do with `''`.
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are **not** read here: `HudSlot`
 * applies the whole shared chrome block around the widget, and `PreviewZoom` applies the same
 * block in the same place on the mod page (`hud/chrome.ts`). Reading them here would apply
 * them twice.
 */

import { memo } from 'react';

import { ServerAddressChip } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import { shortHost } from './format';
import type { HudWidgetProps } from './widgets';

/**
 * The address a preview stands on when there is no server — and it is load-bearing.
 *
 * `style` is this mod's **only** setting, so it alone carries the preview gate: if the short
 * and full forms of the fixture were the same string, `test/preview.test.tsx` would find the
 * drawing unchanged and the mod would ship with a control nobody can see working.
 *
 * `shortHost()` takes the second-to-last dot-separated label and capitalises it, so this host
 * reads `Hypixel` short and `mc.hypixel.net` full — different in length and in content, which
 * is the whole demonstration the setting is asking for. It is also the frames' own fixture and
 * the one `HudPing` already stands its `show_host` suffix on, so the two chips agree about
 * which server the page is pretending to be on.
 *
 * A bare IP would have been the wrong choice for exactly the reason the schema recommends
 * `full` for one: `shortHost('192.168.1.20')` is `1`, so the short form would be a preview of
 * a formatter failing rather than of a setting working.
 */
const SAMPLE_HOST = 'mc.hypixel.net';

/** What `server_address.style` can be, per the schema. */
type AddressStyle = 'short' | 'full';

export const HudServerAddress = memo(function HudServerAddress({
  variant,
  sample,
}: HudWidgetProps) {
  // Two primitive selectors rather than one on `s.server`: the object is replaced whole by
  // `applyServer`, and subscribing to the fields keeps this chip out of any repaint that did
  // not change what it draws.
  const liveHost = useVoidStore((s) => s.server.host);
  const connected = useVoidStore((s) => s.server.connected);
  // Narrowed in the selector, so the subscription stays primitive and a stale registry value
  // still draws the default form rather than nothing.
  const style: AddressStyle = useVoidStore((s) =>
    modSettings(s.loadout, 'server_address').style === 'full' ? 'full' : 'short',
  );
  // The page is reachable from the main menu and from singleplayer, where there is no address
  // at all — which on the HUD is the case above (draw nothing) and on a settings page is the
  // §15 failure `sample` exists to prevent: an empty box on the page whose only job is showing
  // you the mod. A real connection always wins, so a player setting this up mid-match sees
  // their own server.
  const host = connected && liveHost.trim() !== '' ? liveHost : sample ? SAMPLE_HOST : '';
  if (host === '') return null;
  // No `color`: the registry gives this mod `style` and the shared chrome block, and nothing
  // else. The chip accepts one; passing an invented value would be ink no player can choose.
  return <ServerAddressChip variant={variant} host={style === 'full' ? host : shortHost(host)} />;
});
