/**
 * CPS graph — the shape of your clicking over the last few seconds.
 *
 * ## What it adds over the CPS counter
 *
 * A rate tells you what your hand is doing now, and a fight is not now. The number every player
 * quotes is their peak, and the thing that decides a fight is whether the rate *held* through it:
 * a hand that opens at 12 and is at 7 by the end has a different problem from one that sat at 9
 * throughout, and the CPS counter draws both of those identically because at any instant it is
 * showing one figure.
 *
 * ## Where the data comes from, and what is not kept
 *
 * The same click edges the CPS counter derives from — `keys.lmb` and `keys.rmb`, in JS, with no
 * Java sensor, which `mods.json` has recorded about CPS since it shipped. The store keeps **one
 * figure per whole second** rather than a longer ring of timestamps: sixty seconds of a fast hand
 * is several hundred timestamps and this drawing has exactly one column per second, so the ring
 * stays sized for counting and the summary is what is retained.
 *
 * A column is a second of wall clock, so two adjacent columns never share a click, and a gap —
 * the menu was open, the game was paused — is filled with the seconds that actually elapsed
 * rather than closed up. A graph that compressed a quiet spell would be drawing a fight that did
 * not happen.
 *
 * ## The repaint
 *
 * Once a second, which is the column rate, through `hud/second-edge.ts` — and nothing at all
 * behind an open menu or in a preview. The store publishes a new `clickHistory` on the same
 * boundary, so the timer here is what makes an *idle* graph advance: without it a player who
 * stopped clicking would see their last second of history sit at the right-hand edge until they
 * clicked again, which is a graph lying about when it is.
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are **not** read here: `HudSlot`
 * applies the whole shared chrome block around the widget, and `PreviewZoom` applies the same
 * block in the same place on the mod page (`hud/chrome.ts`).
 */

import { memo } from 'react';

import { CpsGraphChip } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import { SECOND_MS, useSecondEdge } from './second-edge';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. */
export const CPS_GRAPH_ID = 'cps_graph';

/** Which hand a stored `mode` means; anything else is the factory default. */
function asMode(value: unknown): 'left' | 'right' | 'both' {
  return value === 'right' || value === 'both' ? value : 'left';
}

/**
 * A fight, for a page opened outside one.
 *
 * Twenty seconds with a shape rather than a level, because a flat fixture would make this mod
 * look like a wider CPS counter — the whole claim is that the shape is the reading, and a
 * preview has to make that claim rather than assert it. It opens quiet, climbs into a burst,
 * holds, and fades: the exact story the mod exists to show, and the one a fixed ceiling makes
 * legible (`CpsGraphChip`'s `CPS_CEILING` says why the scale does not fit the data).
 *
 * The two hands differ throughout, which is what keeps `mode` honest: with one series copied to
 * both, `left`, `right` and `both` would draw the same picture at three heights and
 * `test/preview.test.tsx` would be proving something weaker than it says. The right hand is the
 * sparser one, which is also true of how the buttons are actually used.
 */
const SAMPLE_HISTORY: readonly { l: number; r: number }[] = [
  { l: 0, r: 0 },
  { l: 2, r: 0 },
  { l: 6, r: 1 },
  { l: 9, r: 0 },
  { l: 11, r: 2 },
  { l: 12, r: 0 },
  { l: 12, r: 0 },
  { l: 11, r: 3 },
  { l: 12, r: 1 },
  { l: 10, r: 0 },
  { l: 11, r: 0 },
  { l: 9, r: 2 },
  { l: 10, r: 0 },
  { l: 8, r: 0 },
  { l: 9, r: 1 },
  { l: 7, r: 0 },
  { l: 8, r: 0 },
  { l: 6, r: 2 },
  { l: 7, r: 0 },
  { l: 5, r: 0 },
];

export const HudCpsGraph = memo(function HudCpsGraph({ variant, sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.clickHistory);
  const mode = useVoidStore((s) => asMode(modSettings(s.loadout, CPS_GRAPH_ID).mode));
  const columns = useVoidStore((s) => Number(modSettings(s.loadout, CPS_GRAPH_ID).window_s ?? 20));
  const showFigure = useVoidStore(
    (s) => modSettings(s.loadout, CPS_GRAPH_ID).show_figure !== false,
  );
  const menuOpen = useVoidStore((s) => s.menuOpen);

  // The store publishes a new history on each second boundary, but only while clicks are
  // arriving to change it. This is what advances an *idle* graph — without it the last second a
  // player clicked would sit at the right-hand edge until they clicked again.
  useSecondEdge(!sample && !menuOpen, SECOND_MS);

  // The fixture is a fallback, not an override: a player configuring this mid-match sees their
  // own hand. It only stands in before there is any history at all, which off a server is the
  // whole session.
  const history = live.length > 0 ? live : sample ? SAMPLE_HISTORY : live;
  const series = history.map((entry) =>
    mode === 'left' ? entry.l : mode === 'right' ? entry.r : entry.l + entry.r,
  );
  return (
    <CpsGraphChip
      variant={variant}
      series={series}
      columns={columns}
      figure={showFigure ? (series[series.length - 1] ?? 0) : undefined}
    />
  );
});
