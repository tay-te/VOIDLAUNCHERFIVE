/**
 * Reach display — how far away the last landed hit was, or the last ten on average.
 *
 * Its own module, per `watermark.tsx` and `mods/art.tsx`. A thin adapter over `ReachChip`.
 *
 * ## The reading
 *
 * `tick.reach` is measured on the client when the attack is sent — eye to the point the
 * crosshair ray met the target's hitbox — and arrives only on the tick a new hit lands
 * (`bridge.json`). The store keeps the last ten keyed by the sensor's `n` (`hud/reach-stats.ts`),
 * so a reading delivered twice is one hit, and publishes both figures; `mode` picks which one this
 * chip prints, per render, so flipping the setting never waits for the next hit.
 *
 * ## `hide_after_ms`
 *
 * A reach figure is only interesting while hits are being traded, so the chip can take itself off
 * the screen a while after the last one. Same shape as the combo's `reset_ms`, and the same
 * machinery (`hud/combo.tsx` argues it at length): the window runs out as a hairline under the
 * figure, the widget repaints only at the four band edges, and one timeout — not an interval —
 * is armed for the next edge. Nothing is armed for the preview, behind the menu, or at 0, which
 * keeps the chip on screen and draws no rule.
 */

import { memo, useEffect, useReducer } from 'react';

import { ReachChip } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/** Bands the hide window is drawn in — the combo's four, for the same paint budget. */
const FADE_STEPS = 4;

/**
 * Ten hits for a preview opened before the first one — the lobby case.
 *
 * **Chosen so every setting has something to bite on.** The last hit is 2.874, so `decimals`
 * prints `3`, `2.9` and `2.87` — three different strings — and the mean of the ten is 2.791,
 * so `mode` visibly changes the figure (`2.87` against `2.79`) rather than only the aside.
 * A legitimate spread for 1.8, all inside vanilla's 3.0 survival reach.
 */
const SAMPLE_HITS = [2.61, 2.94, 2.72, 2.88, 2.55, 2.97, 2.79, 2.83, 2.68, 2.874];
const SAMPLE_LAST = SAMPLE_HITS[SAMPLE_HITS.length - 1]!;
const SAMPLE_AVERAGE = SAMPLE_HITS.reduce((a, b) => a + b, 0) / SAMPLE_HITS.length;

/** How long ago the preview's last hit landed, capped at a fraction of the window. */
const SAMPLE_AGE_MS = 1900;
const SAMPLE_AGE_FRACTION = 0.6;

function remainingFraction(age: number, windowMs: number): number {
  if (windowMs <= 0) return 1;
  return Math.max(0, Math.min(1, (windowMs - age) / windowMs));
}

function fadeStep(remaining: number): number {
  return Math.ceil(remaining * FADE_STEPS);
}

export const HudReach = memo(function HudReach({ variant, sample, className }: HudWidgetProps) {
  const live = useVoidStore((s) => s.reach);
  const average = useVoidStore((s) => modSettings(s.loadout, 'reach').mode === 'average');
  const showLabel = useVoidStore((s) => modSettings(s.loadout, 'reach').show_label !== false);
  const decimals = useVoidStore((s) => {
    const stored = Number(modSettings(s.loadout, 'reach').decimals ?? 2);
    return Number.isFinite(stored) ? Math.min(2, Math.max(0, Math.round(stored))) : 2;
  });
  const hideAfter = useVoidStore((s) =>
    Math.max(0, Number(modSettings(s.loadout, 'reach').hide_after_ms ?? 0)),
  );
  const menuOpen = useVoidStore((s) => s.menuOpen);
  const [, repaint] = useReducer((n: number) => n + 1, 0);
  const at = live?.at ?? 0;

  // One timeout to the next band edge; see the header and `hud/combo.tsx`.
  useEffect(() => {
    if (sample || menuOpen || live === null || hideAfter <= 0) return;
    let timer = 0;
    const arm = () => {
      const step = fadeStep(remainingFraction(Date.now() - at, hideAfter));
      if (step <= 0) return;
      const edge = at + hideAfter * (1 - (step - 1) / FADE_STEPS);
      timer = window.setTimeout(
        () => {
          repaint();
          arm();
        },
        Math.max(1, edge - Date.now()),
      );
    };
    arm();
    return () => window.clearTimeout(timer);
  }, [sample, menuOpen, live, at, hideAfter]);

  const reading = live ?? (sample ? { last: SAMPLE_LAST, average: SAMPLE_AVERAGE } : null);
  if (reading === null) return null;
  let remaining: number | undefined;
  if (hideAfter > 0) {
    const age =
      live === null ? Math.min(SAMPLE_AGE_MS, hideAfter * SAMPLE_AGE_FRACTION) : Date.now() - at;
    remaining = remainingFraction(age, hideAfter);
    // Lapsed: off the screen until the next hit. Behaviour the setting asked for, not a fault.
    if (remaining <= 0) return null;
  }
  return (
    <ReachChip
      variant={variant}
      className={className}
      blocks={average ? reading.average : reading.last}
      decimals={decimals}
      showLabel={showLabel}
      average={average}
      remaining={remaining}
    />
  );
});
