/**
 * Memory — JVM heap in use, against the ceiling the launcher gave the game.
 *
 * Its own module rather than another arm of `widgets.tsx`, following `watermark.tsx`: everything
 * a *single* mod draws lives with that mod, so the shared file stops growing when the roster
 * does.
 *
 * The one thing worth knowing about the source, though it changes nothing here: `memory` is
 * **rate-limited hard** at the sensor (`bridge.json`), because heap moves constantly, nobody
 * reads it twenty times a second, and it is the single field most able to undo the coalescing
 * the tick payload exists for. That is a property of the wire, not of this widget — the widget
 * draws whatever the last reading was and does no smoothing, because a smoothed heap figure
 * would be a number the player could not quote in a bug report. It is also the reason `show_bar`
 * ships off: a bar fed by a coarse field steps rather than sweeps, and a bar that jumps reads as
 * broken. The default is the schema's; this file only honours it.
 */

import { memo } from 'react';

import { MemoryChip, type MemoryStyle } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. */
export const MEMORY_ID = 'memory';

/**
 * A working heap on a 4 GiB allocation, for a page opened before the first reading lands.
 *
 * `maxMb` is 4096 because that is what a launcher actually hands 1.8.9 — the schema's own
 * worked example is `1400/4096 MB` — and because a ceiling that is not a power of two reads as
 * a measurement rather than as the `-Xmx` a player chose.
 *
 * `usedMb` is 1462 and every digit of it is doing work, because this fixture has to make three
 * *mutually different* pictures rather than merely a non-empty one:
 *
 *   · `used` → `1462 MB`, `used_of_max` → `1462 / 4096 MB`, `percent` → `36%`. Three readings
 *     nobody could mistake for each other. A tidier pair — 1024 of 4096 — would have printed
 *     `25%`, a number so obviously the quarter mark that the percent style reads as a
 *     restatement of the fraction instead of as a third way of saying it.
 *   · 36% is also the only band where `show_bar` is worth looking at: a bar at 3% reads as
 *     empty and one at 97% as full, and both read as a broken bar rather than as a level. Just
 *     over a third is unmistakably partial, so turning the bar on changes the chip into
 *     something a player can see the point of.
 *
 * And it is an honest reading: a mid-match 1.8.9 heap on a 4 G allocation sits around here,
 * which is the state the mod is for — comfortable, with the headroom visible.
 */
const SAMPLE_MEMORY = { usedMb: 1462, maxMb: 4096 };

/** A stored `style` narrowed to what the chip draws; anything else is the factory default. */
function asMemoryStyle(value: unknown): MemoryStyle {
  return value === 'used' || value === 'percent' ? value : 'used_of_max';
}

export const HudMemory = memo(function HudMemory({ variant, sample }: HudWidgetProps) {
  // The object, not two selectors: `applyTick` patches `memory` only when a field actually
  // moved, so this reference is stable across every tick that carried no heap reading — which,
  // given how hard the field is rate-limited, is most of them.
  const live = useVoidStore((s) => s.memory);
  const style = useVoidStore((s) => asMemoryStyle(modSettings(s.loadout, MEMORY_ID).style));
  const showBar = useVoidStore((s) => modSettings(s.loadout, MEMORY_ID).show_bar === true);
  const showLabel = useVoidStore((s) => modSettings(s.loadout, MEMORY_ID).show_label !== false);
  // `null` is "no reading", not an empty heap — a JVM using zero bytes is not a state that
  // exists, so drawing `0 / 4096 MB` would be inventing one. Nothing on the HUD; the fixture on
  // the mod page, where an empty box is the §15 failure. Live always wins.
  const heap = live ?? (sample ? SAMPLE_MEMORY : null);
  if (heap === null) return null;
  return (
    <MemoryChip
      variant={variant}
      usedMb={heap.usedMb}
      maxMb={heap.maxMb}
      style={style}
      showBar={showBar}
      showLabel={showLabel}
    />
  );
});
