/**
 * Cartridge potion effects: a 220px module of rows — the effect's pixel sprite, its name and
 * countdown, and a 12-segment stepped timer of the time left (Figma B10, top right).
 *
 * An effect inside `blink_below_s` takes warn segments, and on the odd seconds dims to .6 —
 * the blink, driven by the tick's own once-a-second re-publish, with no timer here.
 */

import { memo } from 'react';

import { cx } from '@/ui';
import { Sprite, hasSprite } from '@/cart/Sprite';
import { HudModule, Meter, litFor } from './parts';

export interface CartEffectRow {
  /** 1.8.9 status effect id — picks the `fx<id>` sprite. */
  id: number;
  /** `Speed II`. */
  name: string;
  /** `1:24`; undefined when `show_duration` is off. */
  time?: string;
  /** Time left as a fraction of the effect's full duration, 0..1. */
  fraction: number;
  /** Inside `blink_below_s`: warn segments. */
  ending: boolean;
  /** The blink's dim beat. */
  dim: boolean;
  /** Fallback swatch colour when the sheet has no sprite for this id. */
  color: string;
}

export interface CartEffectsProps {
  rows: readonly CartEffectRow[];
  showLabel?: boolean;
}

function EffectIcon({ id, color }: { id: number; color: string }) {
  const name = `fx${id}`;
  if (hasSprite(name)) return <Sprite name={name} size={28} className="ch-fx__icon" />;
  return (
    <span className="ch-bed ch-fx__icon">
      <span className="ch-fx__swatch" style={{ background: color }} />
    </span>
  );
}

export const CartEffects = memo(function CartEffects({ rows, showLabel = true }: CartEffectsProps) {
  return (
    <HudModule label={showLabel ? 'EFFECTS' : undefined} className="ch-fx">
      <div className="ch-fx__rows">
        {rows.map((row, index) => (
          <div
            key={index}
            className={cx(
              'ch-fx__row',
              row.ending && 'ch-fx__row--ending',
              row.dim && 'ch-fx__row--dim',
              row.time === undefined && 'ch-fx__row--bare',
            )}
            data-fx={row.id}
          >
            <EffectIcon id={row.id} color={row.color} />
            <div className="ch-fx__body">
              <div className="ch-fx__line">
                <span className="ch-fx__name">{row.name}</span>
                {row.time === undefined ? null : <span className="ch-fx__time">{row.time}</span>}
              </div>
              {row.time === undefined ? null : (
                <Meter
                  n={12}
                  on={litFor(row.fraction, 12)}
                  w={9}
                  h={4}
                  gap={2}
                  color={row.ending ? 'var(--c-warn)' : 'rgba(236, 238, 241, 0.75)'}
                />
              )}
            </div>
          </div>
        ))}
      </div>
    </HudModule>
  );
});
