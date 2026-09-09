/**
 * Sprint reset — how many of your recent hits landed with a sprint behind them.
 *
 * Its own module rather than another arm of `widgets.tsx`, following `hit_trade.tsx` and the
 * six Wave 2 readouts: everything a *single* mod draws lives with that mod, so the shared file
 * stops growing when the roster does.
 *
 * ## What it reads, and why the store hands over a sequence rather than a rate
 *
 * `store.ts` keeps one boolean per landed hit — was there a sprint behind it — filled from the
 * deltas of `hits.dealt` and `hits.sprint_dealt`. It deliberately does not keep a percentage,
 * because a percentage is a percentage *over something*, and what it is over is `window`, which
 * is a mod setting. `bridge.json`'s `hits` refuses to carry a combo for the same reason one
 * layer down, and the sentence there covers this one: a mod setting in a sensor is how a sensor
 * starts needing to know about mods.
 *
 * So the window is applied inside `SprintResetChip`, from the array, once — which is also what
 * makes the `ratio` style honest twelve hits into a session, where the denominator is 12 rather
 * than the 20 that was asked for.
 *
 * ## Why it owns no clock
 *
 * The window is counted in hits, so nothing about this reading changes between them: the chip
 * repaints when a hit lands and at no other time. A window in seconds would have needed a timer
 * *and* would have been the wrong unit — a rate over the last ten seconds is a rate over however
 * many hits happened to land in them, which is none in a chase and a dozen in a corner, and the
 * figure would move on the fight's shape rather than on the player's.
 *
 * ## Nothing before the first hit
 *
 * An empty history is not a rate of zero. Zero is a player whose every reset is failing, which
 * is the most useful thing this mod can say; no hits at all is a player who has not swung, and
 * one figure cannot mean both. The chip returns null, and the mod page stands on
 * {@link SAMPLE_HISTORY} — `design/rendering-invariants.md` §15's rule that a page whose whole
 * job is showing you the mod may not be the thing that renders empty.
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are **not** read here. `HudSlot`
 * applies the whole shared chrome block around the widget and `PreviewZoom` applies the same
 * block in the same place on the mod page (`hud/chrome.ts`), so reading them here would apply
 * them twice.
 */

import { memo } from 'react';

import { SprintResetChip, type SprintResetStyle } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. */
export const SPRINT_RESET_ID = 'sprint_reset';

/**
 * Twenty-eight recent hits, for a page opened before the first one lands.
 *
 * Longer than the default window of 20 on purpose: the fixture has to survive a player who has
 * moved the window, and one exactly 20 long would make every window from 20 to 40 read
 * identically — so `window` would look like a setting that does nothing above its default,
 * which is the class of defect `test/preview.test.tsx` exists to catch.
 *
 * The pattern is a run of successes with three misses clustered late, which puts the last 20 at
 * 17/20 (85%) and the last 10 at 8/10 (80%) — different figures, so moving the window visibly
 * moves the reading, and neither is a round number that reads as a placeholder.
 *
 * It is also an honest reading. A player who resets well but not perfectly lands about here,
 * and the clustering is real: missed resets come in runs, because what breaks them is a fight
 * that has stopped giving you the half-second between swings, not bad luck per swing.
 */
const SAMPLE_HISTORY: readonly boolean[] = [
  true, true, true, true, true, true, true, true,
  true, true, false, true, true, true, true, true,
  true, true, true, false, true, true, true, true,
  false, true, true, true,
];

/** A stored `style` narrowed to what the chip draws; anything else is the factory default. */
function asSprintStyle(value: unknown): SprintResetStyle {
  return value === 'ratio' ? value : 'percent';
}

/** A stored `window` narrowed to the schema's range; anything else is the factory default. */
function asWindow(value: unknown): number {
  return typeof value === 'number' && value >= 5 && value <= 40 ? Math.round(value) : 20;
}

/** A stored `warn_below` narrowed to a share; anything else is off. */
function asWarnBelow(value: unknown): number {
  return typeof value === 'number' && value > 0 && value <= 1 ? value : 0;
}

export const HudSprintReset = memo(function HudSprintReset({ variant, sample }: HudWidgetProps) {
  // The array, not a derived figure: `applyTick` replaces `sprintHistory` only on the ticks a
  // hit actually landed, so this reference is stable across every tick between hits — which is
  // most of them, and is the whole reason this widget costs nothing while nothing is happening.
  const live = useVoidStore((s) => s.sprintHistory);
  const window = useVoidStore((s) => asWindow(modSettings(s.loadout, SPRINT_RESET_ID).window));
  const style = useVoidStore((s) => asSprintStyle(modSettings(s.loadout, SPRINT_RESET_ID).style));
  const showBar = useVoidStore((s) => modSettings(s.loadout, SPRINT_RESET_ID).show_bar === true);
  const showLabel = useVoidStore(
    (s) => modSettings(s.loadout, SPRINT_RESET_ID).show_label !== false,
  );
  const warnBelow = useVoidStore((s) =>
    asWarnBelow(modSettings(s.loadout, SPRINT_RESET_ID).warn_below),
  );
  const history = live.length > 0 ? live : sample ? SAMPLE_HISTORY : live;
  if (history.length === 0) return null;
  return (
    <SprintResetChip
      variant={variant}
      history={history}
      window={window}
      style={style}
      showBar={showBar}
      showLabel={showLabel}
      warnBelow={warnBelow}
    />
  );
});
