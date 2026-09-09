/**
 * Combo counter — consecutive hits landed, and how much of the chain is left to run.
 *
 * In its own module rather than in `widgets.tsx` for the reason `watermark.tsx` set the
 * precedent for and `mods/art.tsx` states as a principle: everything a *single* mod draws
 * lives in that mod's own file, so the shared file stops growing when the roster does. This
 * one earns the separation twice over — it is the only HUD widget that owns a clock.
 *
 * ## Three things this widget owns that the store deliberately does not
 *
 * **The timeout.** `store.ts` keeps `combo` (the count, a fact) and `comboAt` (when it last
 * advanced) and expires neither, because `combo.reset_ms` is a *setting* and a sensor that
 * timed out on its own would bake one reader's policy into the wire. So the comparison of
 * `now - comboAt` against `reset_ms` happens here, once, in the one place that knows what the
 * player asked for.
 *
 * **Hiding at zero, unconditionally.** A chip reading `0 combo` between fights is noise on a
 * screen whose whole job is the fight. There is no `hide_at_zero` setting and there must not
 * be one — `schema/mods/combo.json`'s `$comment` is the argument, and the short form is that
 * "the widget is not there" is the widget's own idea of when it has something to say rather
 * than a preference, and a switch whose entire content is absence has no preview to move.
 *
 * **The repaint.** Nothing pushes an update when a window elapses: `comboAt` is a fixed
 * number and the store will never touch it again, so a chip left on screen would sit there
 * showing a dead combo until the next hit landed. See {@link FADE_STEPS} for the timer.
 *
 * ## The depletion, and why the chip draws it
 *
 * `ComboChip` takes a `remaining` fraction — 0..1, how much of the window is left — and draws
 * it as a rule that shortens under the figure. The drawing belongs there with the rest of the
 * chip's geometry; what belongs *here* is the clock, because the fraction is the only thing on
 * this HUD that changes without anything pushing it.
 *
 * It is also what makes `reset_ms` visible. A timeout has no drawn form in a still frame, so
 * without the rule the setting would move nothing on its own settings page, and the only way
 * past `test/preview.test.tsx` would have been an entry in `NOT_IN_THE_PREVIEW` — the list its
 * own comment calls the one place that gate quietly erodes, and whose three retired entries
 * are all cases where "it cannot be drawn" turned out to mean "nobody had drawn it".
 *
 * The rule marks a live value without using colour, so quiet-cell §1 is untouched.
 */

import { memo, useEffect, useReducer } from 'react';

import { ComboChip } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/**
 * How many bands the window is drawn in — and therefore how many times a live combo repaints.
 *
 * **This number is the repaint budget, so it is chosen as one.** A depleting rule wants to
 * move smoothly, and smooth means a loop: `store.ts` opens by saying there is no animation
 * frame loop anywhere in this bundle and that the 20 Hz `tick` push is the clock, and a
 * private 20 Hz interval for one chip would be that rule broken quietly. Four bands is the
 * cheapest thing that still reads as depletion rather than as a chip that blinks out:
 *
 *   · at the 3000 ms default, one repaint every 750 ms — 1.3 Hz, of one chip;
 *   · at the 10000 ms maximum, one every 2.5 s;
 *   · at the 500 ms minimum, one every 125 ms — 8 Hz for half a second, the worst case, and
 *     only for a player who has asked for a window that short.
 *
 * And **zero between fights**: the timer is armed only while a combo is live, and the last
 * band's edge is the end of the window itself, so the timer that steps the fade is the same
 * one that hides the chip. There is no interval anywhere in this file.
 */
const FADE_STEPS = 4;

/** A plausible chain, for a preview opened by a player who is not in a fight. */
const SAMPLE_COMBO = 7;

/**
 * How far into its window the fixture's combo is: 1.9 s after the hit, or 60% of the way
 * through, whichever comes **first**.
 *
 * Both halves are load-bearing, and this is the `HudCps` click-burst argument in a different
 * shape — the fixture has to be chosen so the setting has something to bite on.
 *
 * *Why an absolute age at all.* `reset_ms` is only visible as the difference between two
 * windows measured from the same instant. A fixture pinned to a fraction of the window would
 * deplete to exactly the same point at every setting, and the slider would read as dead for
 * precisely the reason it is not. At 1.9 s the 3000 ms default is two bands down and the
 * 10000 ms maximum is still at full weight, which is the trade the player is making.
 *
 * *Why a fraction as well.* An absolute 1.9 s is **past** every window under 1900 ms, and a
 * lapsed fixture draws nothing — the §15 failure the `sample` prop exists to prevent, handed
 * to any player who drags `reset_ms` down towards its 500 ms minimum. Capping the age at 60%
 * of the window keeps the chip on screen and mid-depletion at every legal setting; above
 * ~3.2 s the absolute age takes over and the windows start to separate.
 *
 * *Why the count can be live but the age cannot.* A player who opens this page mid-fight sees
 * their own number, like every other fixture here. The **age** is always the fixture's,
 * because behind an open menu the clock is the one thing that keeps running: a real `comboAt`
 * would deplete while the page sat open and leave the player configuring an empty box.
 */
const SAMPLE_AGE_MS = 1900;
const SAMPLE_AGE_FRACTION = 0.6;

/** Fraction of the reset window still to run, 0..1. Zero means the chain has lapsed. */
function remainingFraction(age: number, resetMs: number): number {
  if (resetMs <= 0) return 0;
  return Math.max(0, Math.min(1, (resetMs - age) / resetMs));
}

/** Which band `remaining` sits in: {@link FADE_STEPS} while fresh, 0 once the window is out. */
function fadeStep(remaining: number): number {
  return Math.ceil(remaining * FADE_STEPS);
}

export const HudCombo = memo(function HudCombo({ variant, sample, className }: HudWidgetProps) {
  const liveCombo = useVoidStore((s) => s.combo);
  const comboAt = useVoidStore((s) => s.comboAt);
  // Selected as primitives, like `HudCps`: `modSettings` builds a fresh object every call and
  // zustand compares snapshots with `Object.is`, so a selector returning the object would hand
  // React a new value on every render.
  const resetMs = useVoidStore((s) => Number(modSettings(s.loadout, 'combo').reset_ms ?? 3000));
  const showLabel = useVoidStore((s) => modSettings(s.loadout, 'combo').show_label !== false);
  const menuOpen = useVoidStore((s) => s.menuOpen);
  const [, repaint] = useReducer((n: number) => n + 1, 0);

  /**
   * One timeout, armed for the moment the next band actually begins.
   *
   * Not an interval: the edges are known exactly, so the cheap shape is to sleep until the
   * next one and re-arm from the clock rather than to wake up and ask. Three states arm
   * nothing at all, which is the whole point of the shape —
   *
   *   · `sample` — the preview's age is a constant, so it has no edges to wait for;
   *   · `menuOpen` — the HUD is behind a panel and nobody is reading it, and `applyTick` is
   *     already held for the same reason. Closing the menu re-runs this effect, which re-arms
   *     from the real clock, so the chip is correct the instant it is visible again;
   *   · no live combo — nothing to expire.
   */
  useEffect(() => {
    if (sample || menuOpen || liveCombo <= 0) return;
    let timer = 0;
    const arm = () => {
      const step = fadeStep(remainingFraction(Date.now() - comboAt, resetMs));
      // Already lapsed: the render below draws nothing and there is no further edge.
      if (step <= 0) return;
      // The edge where the next band begins. At the last band that edge is the end of the
      // window, so this is also the repaint that takes the chip off the screen.
      const edge = comboAt + resetMs * (1 - (step - 1) / FADE_STEPS);
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
  }, [sample, menuOpen, liveCombo, comboAt, resetMs]);

  const combo = sample ? (liveCombo > 0 ? liveCombo : SAMPLE_COMBO) : liveCombo;
  const age = sample
    ? Math.min(SAMPLE_AGE_MS, resetMs * SAMPLE_AGE_FRACTION)
    : Date.now() - comboAt;
  const remaining = combo > 0 ? remainingFraction(age, resetMs) : 0;
  // Zero or lapsed: the chip is not drawn at all. Behaviour, not a setting — combo.json.
  if (remaining <= 0) return null;
  return (
    <ComboChip
      variant={variant}
      className={className}
      combo={combo}
      showLabel={showLabel}
      remaining={remaining}
    />
  );
});
