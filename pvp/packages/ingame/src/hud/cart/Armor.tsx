/**
 * Cartridge armour: one row per piece — pixel sprite, an 8-segment durability meter, the
 * remaining figure right-aligned — in a 196px module (Figma B10, bottom right). Below
 * `warn_below` the meter and the figure take the warn amber.
 *
 * `horizontal` lays the same pieces side by side as narrow cells (sprite over figure over a
 * compressed meter); `vertical` is the design's stacked list.
 */

import { memo } from 'react';

import { cx } from '@/ui';
import { Sprite, hasSprite, type SpriteName } from '@/cart/Sprite';
import { HudModule, Meter, litFor } from './parts';

export interface CartArmorRow {
  slot: 'helmet' | 'chestplate' | 'leggings' | 'boots' | 'held';
  /** Minecraft item id, e.g. `diamond_chestplate`. */
  item: string | null;
  /** `Helmet` — drawn only when durability is off, where the sprite alone would be mute. */
  label: string;
  /** `Chest` — the narrow form, for the side-by-side cells. */
  short?: string;
  remaining: number;
  /** 0 = takes no damage. */
  max: number;
}

export interface CartArmorProps {
  rows: readonly CartArmorRow[];
  orientation: 'horizontal' | 'vertical';
  showDurability: boolean;
  warnBelow: number;
  showLabel?: boolean;
}

const MATERIAL: Record<string, string> = {
  leather: 'leather',
  chainmail: 'chainmail',
  iron: 'iron',
  golden: 'gold',
  gold: 'gold',
  diamond: 'diamond',
  wooden: 'wood',
  wood: 'wood',
  stone: 'stone',
};

/** Held items whose sprite name is not `<material>_<kind>`. */
const HELD: Record<string, string> = {
  bow: 'bow',
  fishing_rod: 'rod',
  ender_pearl: 'pearl',
  golden_apple: 'gapple',
  cooked_beef: 'steak',
  potion: 'potion',
  splash_potion: 'potion',
  wool: 'wool',
};

/**
 * The sprite for one worn or held item, or null for "draw the empty bed".
 *
 * Computed names go through `hasSprite`, so a sheet that lacks one (a new material, a modded
 * item) falls back rather than drawing cell 0: worn armour falls back to the diamond piece of
 * the same slot, a held item to nothing.
 */
export function armorSprite(slot: CartArmorRow['slot'], item: string | null): SpriteName | null {
  if (!item) return null;
  const id = item.replace(/^minecraft:/, '');
  if (slot === 'held') {
    const direct = HELD[id];
    if (direct && hasSprite(direct)) return direct;
    const sword = /^([a-z]+)_sword$/.exec(id);
    if (sword) {
      const name = `sword_${MATERIAL[sword[1]!] ?? sword[1]}`;
      if (hasSprite(name)) return name;
    }
    return hasSprite(id) ? id : null;
  }
  const material = MATERIAL[id.split('_')[0] ?? ''] ?? 'diamond';
  const name = `armor_${slot}_${material}`;
  if (hasSprite(name)) return name;
  const fallback = `armor_${slot}_diamond`;
  return hasSprite(fallback) ? fallback : null;
}

function Icon({ slot, item, size }: { slot: CartArmorRow['slot']; item: string | null; size: number }) {
  const name = armorSprite(slot, item);
  return name ? (
    <Sprite name={name} size={size} className="ch-armor__icon" />
  ) : (
    <span className="ch-bed ch-armor__icon" style={{ width: size, height: size }} data-item={item ?? ''} />
  );
}

export const CartArmor = memo(function CartArmor({
  rows,
  orientation,
  showDurability,
  warnBelow,
  showLabel = true,
}: CartArmorProps) {
  const horizontal = orientation === 'horizontal';
  return (
    <HudModule
      label={showLabel ? 'ARMOR' : undefined}
      className={cx('ch-armor', horizontal ? 'ch-armor--h' : 'ch-armor--v', !showDurability && 'ch-armor--bare')}
    >
      <div className="ch-armor__rows">
        {rows.map((row, index) => {
          const breakable = row.max > 0;
          const fraction = breakable ? Math.max(0, Math.min(1, row.remaining / row.max)) : 1;
          const warn = breakable && fraction < warnBelow;
          const color = warn ? 'var(--c-warn)' : 'var(--c-ink)';
          const durability = showDurability && breakable;
          return (
            <div key={index} className={cx('ch-armor__row', warn && 'ch-armor__row--warn')} data-slot={row.slot}>
              <Icon slot={row.slot} item={row.item} size={24} />
              {durability ? (
                horizontal ? (
                  <>
                    <span className={cx('ch-armor__n', warn && 'ch-warn')}>{row.remaining}</span>
                    <Meter n={8} on={litFor(fraction, 8)} w={3} h={5} gap={1} color={color} />
                  </>
                ) : (
                  <>
                    <Meter n={8} on={litFor(fraction, 8)} w={9} h={10} gap={2} color={color} />
                    <span className={cx('ch-armor__n', warn && 'ch-warn')}>{row.remaining}</span>
                  </>
                )
              ) : (
                <span className="ch-armor__label">{horizontal ? (row.short ?? row.label) : row.label}</span>
              )}
            </div>
          );
        })}
      </div>
    </HudModule>
  );
});
