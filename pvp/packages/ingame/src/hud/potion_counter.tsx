/**
 * Potion counter — heals left: splash potions of Healing, bowls of soup, or both.
 *
 * Its own module, per `watermark.tsx` and `mods/art.tsx`: what one mod draws lives in that mod's
 * file. A thin adapter, like every other widget — which store field feeds which prop, and which
 * setting gates it. The drawing is `PotionCounterChip`'s, in `@void/ui`.
 *
 * ## Where the figure comes from
 *
 * `counts.health_potion` and `counts.soup`, the inventory-wide sensor (`bridge.json`): every
 * matching item across the 36 main slots, hotbar included, armour and the cursor excluded. Not the
 * held stack — a heal count is the number a player checks *between* heals, while holding a sword,
 * which is exactly when a held-stack reading would say nothing.
 *
 * **0 is a reading, not an absence.** The store keeps `counts: null` until the first push, and a
 * key present at 0 is the last heal gone — the single most important thing this chip ever says,
 * and drawn in the warn ink whenever `low_threshold` is on. Before the first push the chip draws
 * nothing in game, the way every widget with no reading does.
 *
 * `scale`, `opacity` and the chrome block are the slot's (`hud/chrome.ts`), not read here.
 */

import { memo } from 'react';

import { HudModule } from './cart';

/** One figure: a count and, when labelled, the noun it counts. */
interface PotionCount {
  count: number;
  unit?: string;
}
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/** Which inventory figure each `track` value reads, in draw order. */
const TRACKED: Record<'health_potion' | 'soup' | 'both', ReadonlyArray<'health_potion' | 'soup'>> = {
  health_potion: ['health_potion'],
  soup: ['soup'],
  both: ['health_potion', 'soup'],
};

/** The noun after each figure. `pots` is what players call them; `soup` is its own plural. */
const UNIT: Record<'health_potion' | 'soup', string> = { health_potion: 'pots', soup: 'soup' };

/**
 * A preview opened with no counts yet — the lobby case, and the common one on this page.
 *
 * **Chosen against the gate, like `SAMPLE_HELD_COUNT`.** `low_threshold` is exercised at its
 * factory 3 and at its 64 ceiling, so a fixture figure must sit strictly inside: above 3 so the
 * resting ink is what the factory setting shows, at or below 64 so the far end of the range turns
 * it amber. Seven pots and five soup is also a believable half-spent hotbar.
 */
const TAB: Record<'health_potion' | 'soup' | 'both', string> = {
  health_potion: 'POTS',
  soup: 'SOUP',
  both: 'HEALS',
};

const SAMPLE: Record<'health_potion' | 'soup', number> = { health_potion: 7, soup: 5 };

function asTrack(value: unknown): keyof typeof TRACKED {
  return value === 'soup' || value === 'both' ? value : 'health_potion';
}

export const HudPotionCounter = memo(function HudPotionCounter({
  sample,
  className,
}: HudWidgetProps) {
  const pots = useVoidStore((s) => s.counts?.health_potion ?? null);
  const soup = useVoidStore((s) => s.counts?.soup ?? null);
  const track = useVoidStore((s) => asTrack(modSettings(s.loadout, 'potion_counter').track));
  const showLabel = useVoidStore(
    (s) => modSettings(s.loadout, 'potion_counter').show_label !== false,
  );
  const lowThreshold = useVoidStore((s) =>
    Number(modSettings(s.loadout, 'potion_counter').low_threshold ?? 3),
  );
  const live = { health_potion: pots, soup };
  const keys = TRACKED[track];
  // `both` always draws its units: two bare figures side by side cannot be told apart, which is
  // the schema's own reason for overriding `show_label` there.
  const units = showLabel || keys.length > 1;
  const figures: PotionCount[] = [];
  for (const key of keys) {
    // `??`, never `||`: 0 is the reading that matters most.
    const count = live[key] ?? (sample ? SAMPLE[key] : null);
    if (count === null) continue;
    figures.push(units ? { count, unit: UNIT[key] } : { count });
  }
  if (figures.length === 0) return null;
  // Two figures side by side for `both`, each with its noun; a figure at or under the threshold
  // takes the warn amber on its own — eight pots and two soups is one reserve and one nearly gone.
  return (
    <HudModule
      label={showLabel ? TAB[track] : undefined}
      className={className ? `ch-pots ${className}` : 'ch-pots'}
    >
      <div className="ch-row">
        {figures.map((figure, index) => (
          <span key={index} className="ch-figure">
            <span
              className={
                lowThreshold > 0 && figure.count <= lowThreshold ? 'ch-num ch-warn' : 'ch-num'
              }
            >
              {figure.count}
            </span>
            {figure.unit ? <span className="ch-unit">{figure.unit}</span> : null}
          </span>
        ))}
      </div>
    </HudModule>
  );
});
