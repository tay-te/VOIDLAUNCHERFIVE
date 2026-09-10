/**
 * Knockback meter — how far the last hit sent you.
 *
 * ## Why this is a meter and not a trainer
 *
 * `docs/mod-roster.md` §3.2 #6 asked for jump-timing feedback and 1.8.9 would not support one:
 * its knockback halves your existing velocity, subtracts a fixed impulse along the attacker's
 * axis and assigns your vertical velocity a constant, clamped — with no jump term anywhere in
 * it. `schema/mods/knockback.json` quotes the method. Scoring timing against a rule the client
 * has not established would be teaching a guess, so this reports the outcome and lets the figure
 * teach the mechanic.
 *
 * ## It draws `ReachChip`, and that is deliberate
 *
 * Both mods are one distance in blocks with an optional warn threshold, down to the unit. A
 * `KnockbackChip` would have been that component copied under a different name. What separates
 * the two mods is what the number means and when it moves, and both of those live in the sensors
 * — `KnockbackTally` and `ReachTally` — not in the drawing.
 *
 * ## Nothing before the first hit
 *
 * `knockback` is null until something has connected, and null is not zero: **zero is a hit that
 * did not move you**, which is a real and interesting reading, and no reading at all is a session
 * in which nothing has hit you. One figure cannot mean both, so the chip draws nothing in the
 * second case and the mod page stands on {@link SAMPLE_BLOCKS}.
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are **not** read here: `HudSlot`
 * applies the whole shared chrome block around the widget and `PreviewZoom` applies the same
 * block in the same place on the mod page (`hud/chrome.ts`).
 */

import { memo } from 'react';

import { ReachChip } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. */
export const KNOCKBACK_ID = 'knockback';

/**
 * A hit that moved you 3.4 blocks, for a page opened before one lands.
 *
 * Chosen against the arithmetic rather than picked to look tidy: 1.8.9's impulse is 0.4 blocks
 * per tick and horizontal drag takes it under a tenth of that inside the ten-tick window, so an
 * ordinary hit with a little of your own movement in it lands near three and a half. A round 4.0
 * would read as a placeholder; 0.5 would make `warn_above` look like a setting with nothing to
 * cross.
 */
const SAMPLE_BLOCKS = 3.4;

/** A stored `warn_above` narrowed to the schema's range; anything else is off. */
function asWarnAbove(value: unknown): number {
  return typeof value === 'number' && value > 0 && value <= 6 ? value : 0;
}

export const HudKnockback = memo(function HudKnockback({ variant, sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.knockback);
  const showLabel = useVoidStore((s) => modSettings(s.loadout, KNOCKBACK_ID).show_label !== false);
  const warnAbove = useVoidStore((s) =>
    asWarnAbove(modSettings(s.loadout, KNOCKBACK_ID).warn_above),
  );

  const chip = (blocks: number, key?: number) => (
    <ReachChip
      key={key}
      variant={variant}
      blocks={blocks}
      showLabel={showLabel}
      warnAbove={warnAbove}
    />
  );

  // Two chips once a threshold is set, straddling it — `hud/reach.tsx` makes the same move for
  // the same setting name and carries the full argument. In short: one figure cannot show you
  // where a boundary is, because 3.40 stays plain however far the slider is dragged above it,
  // and a control that visibly does nothing reads as a control that does nothing.
  //
  // With the threshold off there is nothing to straddle and the preview is one honest reading.
  if (sample && live === null) {
    if (warnAbove <= 0) return chip(SAMPLE_BLOCKS);
    return (
      <div className="v-pingband">
        {[
          Math.max(0, Math.round((warnAbove - 0.4) * 100) / 100),
          Math.round((warnAbove + 0.4) * 100) / 100,
        ].map((blocks, i) => chip(blocks, i))}
      </div>
    );
  }

  const blocks = live ?? (sample ? SAMPLE_BLOCKS : null);
  if (blocks === null) return null;
  return chip(blocks);
});
