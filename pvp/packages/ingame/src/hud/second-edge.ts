/**
 * A repaint on each second boundary, for the readouts whose figure changes without anything
 * pushing it.
 *
 * ## Why this exists as a shared thing now
 *
 * `store.ts` opens by stating that the 20 Hz `tick` push is this bundle's only clock and that
 * there is no animation-frame loop anywhere in it. Every readout so far honours that by being
 * *driven*: the sensor pushes a figure and the chip draws it. Three do not, and they are the
 * three that own time — the stopwatch, and the two wall-clock readouts added beside it. Nothing
 * will ever push "it is now the next second".
 *
 * `hud/stopwatch.tsx` worked that out first and its answer is the one here: **one timeout, armed
 * for the instant the drawn figure next changes, re-armed from the clock when it fires.** Not an
 * interval — an interval drifts and keeps firing when nobody is looking — and not a frame loop,
 * which is the rule the store states. The edges of a clock are known exactly, so the cheap shape
 * is to sleep until the next one rather than to wake up and ask.
 *
 * It is lifted out of `stopwatch.tsx` rather than copied because the stopwatch's version carries
 * two things a wall clock does not have — a virtual origin (`startedAt - baseMs`, so the figure's
 * own edges line up rather than the last keypress's) and a hundredths mode. A wall clock's origin
 * is the epoch and its step is a second. Copying would have been a second implementation of the
 * one piece of this bundle that is allowed to wake itself up, which is exactly the piece where a
 * second implementation is worth avoiding.
 *
 * ## The repaint budget
 *
 * **One repaint per second, per mounted readout, and zero the rest of the time.** Both of the
 * conditions the stopwatch established are kept:
 *
 *   · **Behind an open menu, nothing is armed.** `applyTick` already holds every live readout
 *     while a panel covers it, for a measured reason — Ultralight reports damage as one bounding
 *     rectangle, so a chip ticking at the screen edge drags that rectangle across the panel in
 *     the middle at ~50 ms a repaint. A self-driven chip that kept ticking there would be that
 *     cost with none of the store's protection. Closing the menu re-runs the effect, which
 *     re-reads the real clock, so the reading is correct the instant it is visible again.
 *   · **A preview arms nothing.** The mod page draws a fixture, and a fixture has no next edge.
 *
 * @param active whether the reading is live and visible. `false` arms no timer at all.
 * @param stepMs the period of the drawn figure. 1000 for a clock showing seconds; 60000 for one
 *   showing only minutes, which is the case that makes this a parameter — a chip drawing `14:32`
 *   has fifty-nine seconds in every minute where a repaint would change no pixel.
 */

import { useEffect, useReducer } from 'react';

/** One second. */
export const SECOND_MS = 1000;

/** One minute — the step for a reading whose smallest field is minutes. */
export const MINUTE_MS = 60_000;

export function useSecondEdge(active: boolean, stepMs: number = SECOND_MS): void {
  const [, repaint] = useReducer((n: number) => n + 1, 0);

  useEffect(() => {
    if (!active) return;
    const step = Math.max(1, stepMs);
    let timer = 0;
    // Waking early is safe by construction: `arm` recomputes the next edge from the current
    // clock, so a timer that fires a millisecond short lands on the same edge again and re-arms
    // for what is left of it rather than skipping a value.
    const arm = () => {
      const now = Date.now();
      const next = (Math.floor(now / step) + 1) * step;
      timer = window.setTimeout(
        () => {
          repaint();
          arm();
        },
        Math.max(1, next - now),
      );
    };
    arm();
    return () => window.clearTimeout(timer);
  }, [active, stepMs]);
}
