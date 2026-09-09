/**
 * Saturation — the hidden half of the hunger bar, as a number you can actually read.
 *
 * In its own module rather than in `widgets.tsx`, per `watermark.tsx` and the principle
 * stated at the top of `mods/art.tsx`: what a single mod draws lives in that mod's own file.
 *
 * A thin adapter, like every other widget: which store field feeds which prop, and which of
 * the mod's settings gates it. The drawing, the 0-20 clamp and the bar's geometry are all
 * `SaturationChip`'s, in `@void/ui`.
 *
 * **`null` is not zero.** `store.ts` keeps this reading as `number | null` and says so
 * explicitly — an unread saturation and a saturation of 0 are different states and only one of
 * them should draw a number. Zero is the cliff the mod exists to show (regeneration runs while
 * saturation is above it and stops the instant it is not), so `0.0 sat` is the single most
 * important thing this chip ever says and must not be what it says before the first tick.
 */

import { memo } from 'react';

import { SaturationChip, type SaturationStyle } from '@/ui';
import { useModSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/**
 * A plausible reading, for a preview opened before the first `tick` carries one.
 *
 * **Deliberately not a round number, and deliberately not near either end.** `decimals` is
 * the setting this fixture exists for: at `13.0` the control would move a digit that is
 * always a zero, and 1 and 2 would draw `13.0` and `13.00` — a change nobody reads as a
 * change. `13.72` prints `14`, `13.7` and `13.72`, so all three legal values of a setting
 * whose whole content is the last digit are visibly different. (Two decimals is not an
 * invention of this mod's: `combo.json` and `SETTING_BOUNDS` explain that `decimals` is keyed
 * by property *name* across every mod and `coordinates.decimals` already reaches 2, so the
 * generator would throw rather than hand this mod a 0-1 slider. It renders.)
 *
 * The figure is also mid-range on purpose: `style: bar` draws it as a fill, and a fixture at
 * 19 or at 0.5 would make that mode look full or empty rather than like a level. 13.72 of 20
 * is a bar a bit over two thirds along — which, as it happens, is roughly where a steak
 * leaves you.
 */
const SAMPLE_SATURATION = 13.72;

/** A stored `style` narrowed to what the chip draws; anything else is the factory default. */
function asSaturationStyle(value: unknown): SaturationStyle {
  return value === 'bar' || value === 'both' ? value : 'number';
}

export const HudSaturation = memo(function HudSaturation({
  variant,
  sample,
  className,
}: HudWidgetProps) {
  const live = useVoidStore((s) => s.saturation);
  const settings = useModSettings('saturation');
  // The live reading always wins, so a player who opens this page in a match configures the
  // mod against their own saturation; the fixture is only ever the fallback.
  const value = live ?? (sample ? SAMPLE_SATURATION : null);
  if (value === null) return null;
  return (
    <SaturationChip
      variant={variant}
      className={className}
      saturation={value}
      style={asSaturationStyle(settings.style)}
      decimals={Number(settings.decimals ?? 1)}
      showLabel={settings.show_label !== false}
    />
  );
});
