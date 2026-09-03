/**
 * Pure formatting helpers for the HUD readouts. No React, no bridge.
 */

import type { ArmorSlot, PotionEffect } from '@/bridge/protocol';

/** MC 1.8.9 yaw: 0 faces +Z (south) and grows toward −X (west). */
const COMPASS = ['S', 'SW', 'W', 'NW', 'N', 'NE', 'E', 'SE'] as const;

export function cardinal(yaw: number): string {
  const index = ((Math.round(yaw / 45) % 8) + 8) % 8;
  return COMPASS[index]!;
}

/** `1:24` — the potion timer format of frame 244:1791. */
export function duration(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

/** 0-based amplifier to a Roman numeral: 0 → "", 1 → "II", 2 → "III". */
export function roman(amplifier: number): string {
  if (amplifier <= 0) return '';
  const numerals = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X'];
  return numerals[Math.min(amplifier, numerals.length - 1)] ?? String(amplifier + 1);
}

interface PotionMeta {
  label: string;
  color: string;
}

/** 1.8.9 potion ids → display label and the swatch colour used in the frames. */
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

export interface ArmorRow {
  slot: ArmorSlot['slot'];
  label: string;
  remaining: number;
  max: number;
  /** 0..1 */
  ratio: number;
  /** Frame 244:1800 draws under half in --warn, the rest in --ok. */
  tone: 'ok' | 'warn';
}

/** Turn one `armor` entry into the row the ArmorList draws. Empty slots drop. */
export function armorRow(slot: ArmorSlot): ArmorRow | null {
  if (!slot.item) return null;
  const max = slot.max_damage ?? 0;
  const remaining = Math.max(0, max - (slot.damage ?? 0));
  const ratio = max > 0 ? remaining / max : 1;
  return {
    slot: slot.slot,
    label: SLOT_LABELS[slot.slot],
    remaining,
    max,
    ratio,
    tone: ratio < 0.5 ? 'warn' : 'ok',
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
