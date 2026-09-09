/**
 * Momentum — how fast you are actually travelling across the ground.
 *
 * Its own module rather than another arm of `widgets.tsx`, following `watermark.tsx`: everything
 * a *single* mod draws lives with that mod, so the shared file stops growing when the roster
 * does (`mods/art.tsx`'s header states the principle for the tiles; it is the same one here).
 *
 * Structurally this is `HudFps`: subscribe to the narrowest slice of store the chip needs, read
 * the three settings as primitives, hand them to the presentational chip in `@void/ui`. Two
 * things about the reading are worth knowing before changing anything here:
 *
 *   · **`speed` is horizontal, on purpose.** `bridge.json` says why — falling is not momentum a
 *     player is steering, and folding the vertical component in would spike the figure on every
 *     drop and off every jump, which is exactly when the readout is least useful. So this widget
 *     does no arithmetic on it: there is no second component to combine, and a chip that showed
 *     one would be showing a different quantity than the one the mod is named for.
 *   · **The conversion is the chip's.** `MomentumChip` takes `speed` in blocks per second in
 *     both units and multiplies by 3.6 itself, so `unit` goes through as the setting's own value
 *     and the number this file passes is always the wire's. Converting here as well would apply
 *     3.6 twice, and converting here *instead* would leave the chip's `unit` label disagreeing
 *     with its figure — the chip is one place, and the label and the arithmetic belong together.
 */

import { memo } from 'react';

import { MomentumChip, type MomentumUnit } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. */
export const MOMENTUM_ID = 'momentum';

/**
 * Vanilla 1.8 sprint on flat ground, to 2 dp — the precision the wire carries.
 *
 * **A number a player recognises, which a round one would not be.** 5.61 b/s *is* sprinting:
 * a movement player reads it and knows immediately whether the chip is telling the truth, the
 * way `142` on the FPS tile is a frame rate rather than a placeholder. Walking (4.317) would
 * have done the same job; sprinting is the state a player is in when they care about this
 * readout at all, and it is the faster of the two figures the mod page is trying to sell.
 *
 * It is also chosen so both settings have something to bite on, which is the fixture's other
 * job (`HudCps`'s click bursts, `SAMPLE_ARMOR`'s four durabilities):
 *
 *   · `decimals` — 5.61 at 2 dp, `5.6` at 1, `6` at 0. A figure whose fractional part was zero
 *     would leave that slider inert on the page, and `decimals` is the one setting here a
 *     player is likely to change (a chip that flickers every tick is the reason it exists).
 *   · `unit` — 5.61 bps is 20.20 km/h, which is roughly what a sprinting player quotes at
 *     another player. The two readings differ by 3.6x, so the switch is unmissable.
 *
 * **A ground speed, not an airborne one**, because the field is horizontal: a fixture taken
 * mid-fall would be a number this sensor never sends.
 */
const SAMPLE_SPEED = 5.61;

/** A stored `unit` narrowed to what the chip draws; anything else is the factory default. */
function asMomentumUnit(value: unknown): MomentumUnit {
  return value === 'kmh' ? 'kmh' : 'bps';
}

export const HudMomentum = memo(function HudMomentum({ variant, sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.speed);
  const unit = useVoidStore((s) => asMomentumUnit(modSettings(s.loadout, MOMENTUM_ID).unit));
  // Clamped rather than passed through: the chip formats with `toFixed`, which throws outside
  // 0-100, and a HUD widget that throws takes the whole overlay with it. The registry already
  // bounds this to 0-2; this is the belt for a host running a stale one.
  const decimals = useVoidStore((s) => {
    const stored = Number(modSettings(s.loadout, MOMENTUM_ID).decimals ?? 2);
    return Number.isFinite(stored) ? Math.min(2, Math.max(0, Math.round(stored))) : 2;
  });
  const showLabel = useVoidStore(
    (s) => modSettings(s.loadout, MOMENTUM_ID).show_label !== false,
  );
  // `null` is "no reading", not zero — a player standing still sends 0 and that is a figure
  // worth drawing; a sensor that has not sent yet is not. On the HUD the right answer to no
  // reading is nothing at all; on the mod page it is the §15 failure (an empty box on the one
  // page whose job is showing you the mod), which is what `sample` is for. Live always wins,
  // so a player in a match sets the mod up against their own speed.
  const speed = live ?? (sample ? SAMPLE_SPEED : null);
  if (speed === null) return null;
  return (
    <MomentumChip
      variant={variant}
      speed={speed}
      unit={unit}
      decimals={decimals}
      showLabel={showLabel}
    />
  );
});
