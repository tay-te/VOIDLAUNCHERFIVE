/**
 * Trade counter — hits landed against hits taken, over the whole session.
 *
 * Its own module rather than another arm of `widgets.tsx`, following `watermark.tsx` and the
 * six Wave 2 readouts: everything a *single* mod draws lives with that mod, so the shared file
 * stops growing when the roster does.
 *
 * ## What it reads, and why it owns no clock
 *
 * `hits.dealt` and `hits.taken` have been on the tick payload since the combo counter shipped,
 * and until now only the combo read them — so the client already knew how a session was going
 * and had nowhere to say so. This mod is that reading and nothing more: `store.ts` keeps the
 * pair as the sensor sent it, and there is no arithmetic here that the pair does not already
 * contain.
 *
 * **Deliberately no window.** A fight is a count with a timeout policy on it, and this registry
 * has exactly one mod that owns that policy — `combo`, whose `reset_ms` is drawn as a depleting
 * rule because a timeout has no still form. Two mods owning one timeout is the shape
 * `toggle_sneak` was split out of `toggle_sprint` to remove, so this mod answers the question
 * Combo cannot (*over the session, am I ahead*) and arms no timer at all. The practical
 * consequence is worth stating: this chip repaints when a hit lands and at no other time,
 * where the combo chip repaints four times per window while a chain is live.
 *
 * ## Nothing before the first reading
 *
 * `hits` is `null` until the sensor sends a pair, and null is "no reading", not "no hits" —
 * `0 / 0 TRADE` is a chip claiming a fact about a session it has not seen yet. So the HUD draws
 * nothing, and the mod page stands on {@link SAMPLE_HITS}, which is the §15 rule the six Wave 2
 * readouts already follow: an empty box is the one thing a page whose whole job is showing you
 * the mod may not be.
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are **not** read here. `HudSlot`
 * applies the whole shared chrome block around the widget and `PreviewZoom` applies the same
 * block in the same place on the mod page (`hud/chrome.ts`), so reading them here would apply
 * them twice.
 */

import { memo } from 'react';

import { TradeChip, type TradeStyle } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. */
export const HIT_TRADE_ID = 'hit_trade';

/**
 * A session in progress, for a page opened before the first reading lands.
 *
 * Every digit is doing work, because this fixture has to make all three styles *mutually
 * different* rather than merely non-empty:
 *
 *   · `traded` → `47 / 19`, `ratio` → `2.5`, `dealt` → `47`. Three readings nobody could
 *     mistake for one another. A round pair — 40 and 20 — would have printed `2.0`, which
 *     reads as a placeholder rather than as a measurement.
 *   · 47 of 66 is 71%, which is the only band where `show_bar` is worth looking at: a bar at
 *     5% reads as empty and one at 95% as full, and both read as a broken bar rather than as a
 *     level. Comfortably-but-not-absurdly ahead is a share a player can see the point of.
 *   · `taken` is non-zero on purpose. At zero the ratio has nothing to divide by and the chip
 *     honestly falls back to the pair — correct behaviour, and a fixture that triggered it
 *     would make `style` look like a setting with two values instead of three, and
 *     `test/preview.test.tsx` would be proving something weaker than it says it is.
 *
 * It is also an honest reading: two thirds of a Bedwars game against players who fight back
 * lands about here, which is the state the mod is for.
 */
const SAMPLE_HITS = { dealt: 47, taken: 19 };

/** A stored `style` narrowed to what the chip draws; anything else is the factory default. */
function asTradeStyle(value: unknown): TradeStyle {
  return value === 'ratio' || value === 'dealt' ? value : 'traded';
}

export const HudHitTrade = memo(function HudHitTrade({ variant, sample }: HudWidgetProps) {
  // The object, not two selectors: `applyTick` replaces `hits` only when a counter actually
  // moved, so this reference is stable across every tick between hits — which is most of them.
  const live = useVoidStore((s) => s.hits);
  const style = useVoidStore((s) => asTradeStyle(modSettings(s.loadout, HIT_TRADE_ID).style));
  const showBar = useVoidStore((s) => modSettings(s.loadout, HIT_TRADE_ID).show_bar === true);
  const showLabel = useVoidStore((s) => modSettings(s.loadout, HIT_TRADE_ID).show_label !== false);
  const hits = live ?? (sample ? SAMPLE_HITS : null);
  if (hits === null) return null;
  return (
    <TradeChip
      variant={variant}
      dealt={hits.dealt}
      taken={hits.taken}
      style={style}
      showBar={showBar}
      showLabel={showLabel}
    />
  );
});
