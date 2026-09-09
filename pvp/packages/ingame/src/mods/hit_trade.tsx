/**
 * Trade counter — what you landed against what you took, this session.
 *
 * The tile prints the pair whatever `style` says, and the reason is the tile's job rather than
 * a preference. `mods/types.ts`: a thumbnail answers *which mod is this*, at 145px, in a grid
 * of siblings. `ratio` is `2.5` — a bare figure with a decimal point, which at that size is
 * indistinguishable from Momentum's speed, Saturation's level or a scale multiplier — and
 * `dealt` is `47`, which could belong to any counting mod in the registry. `47 / 19` is only
 * ever one thing. The page below is where the three styles are chosen and shown.
 *
 * Live off the store, with the widget's own fixture standing in before the first reading. The
 * grid is reachable from the main menu, where "no session yet" is the state the tile is most
 * often looked at in, so a fixture is the common case here rather than the fallback.
 *
 * Monochrome (§1). The chip mutes the second figure and this does not, because a tile has no
 * room to build a hierarchy out of two figures and one slash: at 145px the muted half would
 * read as a rendering fault rather than as a subject and its comparison.
 */

import { defineMod } from './types';
import { HudHitTrade } from '@/hud/hit_trade';
import { useVoidStore } from '@/store/store';

/** The widget's own fixture, used before a reading lands — see `hud/hit_trade.tsx`. */
const FALLBACK_HITS = { dealt: 47, taken: 19 };

function HitTradeThumbnail(): React.ReactElement {
  const hits = useVoidStore((s) => s.hits) ?? FALLBACK_HITS;
  return (
    <span className="tart tart--stack">
      <span className="tart__big tnum">{`${hits.dealt} / ${hits.taken}`}</span>
      <span className="tart__unit">TRADE</span>
    </span>
  );
}

export default defineMod({
  id: 'hit_trade',
  Thumbnail: HitTradeThumbnail,
  Preview: HudHitTrade,
  // **Under the chip mods' 3, and the width is why.** Those enlarge a two- or three-glyph
  // figure; this one enlarges two figures, a slash and a unit, and a mod with nine settings
  // gets the grouped structure — two property columns — which leaves the preview frame the
  // narrower half of the page. At 3 the `traded` style runs past it, which would clip the
  // default style on the page that exists to show it. 2.4 is what holds it, and it is the
  // number `server_address` arrived at for the same reason.
  previewZoom: 2.4,
});
