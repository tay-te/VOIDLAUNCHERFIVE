/**
 * Knockback meter — how far the last hit sent you.
 *
 * The tile prints the figure with `blocks` under it, which is the same shape Reach display's tile
 * draws — and the two sit apart in the grid for that reason rather than beside each other: two
 * tiles reading `3.14 blocks` and `3.40 blocks` next to one another would be told apart only by
 * their labels, which is the confusion the `sword` run in `mods/order.ts` is arranged to avoid.
 *
 * Live off the store, with the widget's own fixture standing in before the first hit — the grid
 * is reachable from the main menu, where "nothing has hit me yet" is the state the tile is most
 * often looked at in.
 *
 * Monochrome (§1): `warn_above` ships off, and a tile drawing the warn ink would be showing a
 * state nobody configured, on a page where nothing has happened.
 */

import { defineMod } from './types';
import { HudKnockback } from '@/hud/knockback';
import { useVoidStore } from '@/store/store';

/** The widget's own fixture, used before a hit lands — see `hud/knockback.tsx`. */
const FALLBACK_BLOCKS = 3.4;

function KnockbackThumbnail(): React.ReactElement {
  const blocks = useVoidStore((s) => s.knockback) ?? FALLBACK_BLOCKS;
  return (
    <span className="tart tart--stack">
      <span className="tart__big tnum">{blocks.toFixed(2)}</span>
      <span className="tart__unit">BLOCKS</span>
    </span>
  );
}

export default defineMod({
  id: 'knockback',
  Thumbnail: KnockbackThumbnail,
  Preview: HudKnockback,
  // Reach display's number, so Reach display's zoom. Two settings puts it in the flat structure
  // (§8), which leaves the preview frame the wider half of the page, and `3.40 blocks` sits in
  // it at 3 the same way `3.14 blocks` does.
  previewZoom: 3,
});
