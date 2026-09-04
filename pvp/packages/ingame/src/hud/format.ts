/**
 * Formatting the shared package does not cover: the potion colour table, the
 * armour-row derivation, and the `played` figure on a loadout card.
 *
 * `formatPotionTime` / `formatAmplifier` come from `@void/ui`, and
 * `cardinalFromYaw` from `@void/protocol`; none of them is re-implemented here.
 */

import type { ArmorSlot, PotionEffect } from '@/bridge/protocol';

interface PotionMeta {
  label: string;
  color: string;
}

/**
 * 1.8.9 potion ids → display label and swatch colour. The two the HUD-layout
 * frame draws — Speed `#7aebb5` and Strength `#d9a93a` — are the frame's own
 * values; the rest follow the same hue logic.
 */
const POTIONS: Record<number, PotionMeta> = {
  1: { label: 'Speed', color: '#7aebb5' },
  2: { label: 'Slowness', color: '#8b93a1' },
  3: { label: 'Haste', color: '#d9a93a' },
  4: { label: 'Mining fatigue', color: '#4a5058' },
  5: { label: 'Strength', color: '#d9a93a' },
  6: { label: 'Instant health', color: '#f04c56' },
  7: { label: 'Instant damage', color: '#8b2f38' },
  8: { label: 'Jump boost', color: '#7bebb5' },
  9: { label: 'Nausea', color: '#4a3a6b' },
  10: { label: 'Regeneration', color: '#e88bd0' },
  11: { label: 'Resistance', color: '#4d87cd' },
  12: { label: 'Fire resistance', color: '#e08a3a' },
  13: { label: 'Water breathing', color: '#5fc9d9' },
  14: { label: 'Invisibility', color: '#b8bec6' },
  15: { label: 'Blindness', color: '#20232a' },
  16: { label: 'Night vision', color: '#2f4fb8' },
  17: { label: 'Hunger', color: '#7a6234' },
  18: { label: 'Weakness', color: '#5a6068' },
  19: { label: 'Poison', color: '#5aa02c' },
  20: { label: 'Wither', color: '#3a3238' },
  21: { label: 'Health boost', color: '#f0736c' },
  22: { label: 'Absorption', color: '#d9a93a' },
  23: { label: 'Saturation', color: '#f04c56' },
};

/** Label + swatch for one effect. Falls back to the unlocalised name. */
export function potionMeta(fx: PotionEffect): PotionMeta {
  const known = POTIONS[fx.id];
  if (known) return known;
  const tail = (fx.name ?? '').split('.').pop() ?? `Effect ${fx.id}`;
  return { label: tail.replace(/([a-z])([A-Z])/g, '$1 $2'), color: '#8b93a1' };
}

const SLOT_LABELS: Record<ArmorSlot['slot'], string> = {
  helmet: 'Helmet',
  chestplate: 'Chestplate',
  leggings: 'Leggings',
  boots: 'Boots',
  held: 'Held',
};

export interface DurabilityRow {
  slot: ArmorSlot['slot'];
  label: string;
  remaining: number;
  max: number;
}

/**
 * Turn one `armor` entry into the row `ArmorList` draws. Empty slots drop out —
 * the frame lists only the pieces the player is wearing.
 */
export function armorRow(slot: ArmorSlot): DurabilityRow | null {
  if (!slot.item) return null;
  const max = slot.max_damage ?? 0;
  return {
    slot: slot.slot,
    label: SLOT_LABELS[slot.slot],
    remaining: Math.max(0, max - (slot.damage ?? 0)),
    max,
  };
}

/** `4h 20m`, `48m` — the "played" stat on a loadout card. */
export function playedTime(ms: number): string {
  const minutes = Math.round(ms / 60000);
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest}m`;
  return `${hours}h ${String(rest).padStart(2, '0')}m`;
}

/** `mc.hypixel.net` reads as `Hypixel` in the frames. */
export function shortHost(host: string): string {
  const parts = host.split('.').filter(Boolean);
  const core = parts.length >= 2 ? parts[parts.length - 2]! : (parts[0] ?? host);
  return core.charAt(0).toUpperCase() + core.slice(1);
}
