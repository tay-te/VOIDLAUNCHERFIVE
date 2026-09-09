/**
 * Sprint reset — the share of your recent hits that had a sprint behind them.
 *
 * The tile prints the percentage whatever `style` says, and that is the tile's job rather than
 * a preference. `mods/types.ts`: a thumbnail answers *which mod is this*, at 145px, in a grid
 * of siblings. `15 / 20` is a pair, and this grid already has one mod whose whole identity is a
 * pair — Trade counter's `47 / 19` — so drawing the ratio style here would make two tiles that
 * have to be told apart by their labels. A percentage with `SPRINT` under it is only ever one
 * thing, and it is also the default the mod ships with.
 *
 * Live off the store, with the widget's own fixture standing in before the first hit. The grid
 * is reachable from the main menu, where "no session yet" is the state the tile is most often
 * looked at in, so a fixture is the common case here rather than the fallback.
 *
 * Monochrome (§1) — `warn_below` ships off, and a tile that drew the warn ink would be showing
 * a state nobody configured, on a page where nothing has happened.
 */

import { defineMod } from './types';
import { HudSprintReset } from '@/hud/sprint_reset';
import { useVoidStore } from '@/store/store';

/** The widget's own fixture, used before a hit lands — see `hud/sprint_reset.tsx`. */
const FALLBACK_RATE = 85;

function SprintResetThumbnail(): React.ReactElement {
  // The default window, not the whole history: the tile has to agree with the chip a page away,
  // and the chip's reading is over `window`. Taken here rather than read from the loadout for
  // the same reason the fixture is a constant — a thumbnail states what the mod is, and a tile
  // that moved when a setting did would be answering a question the grid is not asking.
  const history = useVoidStore((s) => s.sprintHistory);
  const recent = history.slice(-20);
  const rate =
    recent.length > 0
      ? Math.round((recent.filter(Boolean).length / recent.length) * 100)
      : FALLBACK_RATE;
  return (
    <span className="tart tart--stack">
      <span className="tart__big tnum">{`${rate}%`}</span>
      <span className="tart__unit">SPRINT</span>
    </span>
  );
}

export default defineMod({
  id: 'sprint_reset',
  Thumbnail: SprintResetThumbnail,
  Preview: HudSprintReset,
  // The chip mods' 3, not Trade counter's 2.4. That one was narrowed because its default style
  // is two figures and a slash and ran past the frame; this mod's default is `75% SPRINT`,
  // which is the same width as `12 CPS` or `47 COMBO`, and `ratio` is the narrow style here
  // rather than the wide one. Ten settings still puts it in the grouped structure, so the frame
  // is the narrower half of the page — 3 was checked against `15 / 20 SPRINT`, the widest thing
  // it can print, and holds.
  previewZoom: 3,
});
