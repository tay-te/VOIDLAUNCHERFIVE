/**
 * Presets — per-mod defaults (quiet-cell-system §6).
 *
 * "Preset" is the settled word for *this* and nothing else: a named starting point for
 * one mod's settings. A named bundle of mods is a **Loadout**; the player's account is
 * their **Profile**.
 *
 * There is no preset in the schema, and there should not be one: a preset is a UI
 * convenience over values the registry already owns. So the three are derived from each
 * mod's factory defaults rather than transcribed per mod — twelve hand-written tables
 * would drift from `schema/mods.json` the first time a default moved.
 *
 *   Default      the registry's own defaults, untouched.
 *   Compact      smaller and quieter: 0.75 scale, 0.7 opacity, every optional readout
 *                off but the first.
 *   Tournament   maximum legibility and no motion: full scale and opacity, every
 *                readout on, smoothing and cinematic easing off.
 */

import type { ModId } from './protocol';
import { defaultsFor, settingsFor } from './registry';

export type PresetId = 'default' | 'compact' | 'tournament';

export interface Preset {
  id: PresetId;
  label: string;
}

export const PRESETS: readonly Preset[] = [
  { id: 'default', label: 'Default' },
  { id: 'compact', label: 'Compact' },
  { id: 'tournament', label: 'Tournament' },
];

/** Switches that add a readout rather than change behaviour. */
function isOptionalReadout(key: string): boolean {
  return key.startsWith('show_');
}

/** Switches that add motion — off in Tournament, where a frame is a frame. */
const MOTION_KEYS = new Set(['smooth', 'cinematic', 'dynamic']);

/**
 * The settings a preset writes for one mod, including `on` (which the preset leaves at
 * whatever the defaults say, so applying a preset never silently enables a mod).
 */
export function presetSettings(id: ModId, preset: PresetId): Record<string, unknown> {
  const defaults = defaultsFor(id);
  if (preset === 'default') return defaults;

  const next = { ...defaults };
  let seenReadout = false;

  for (const spec of settingsFor(id)) {
    if (spec.key === 'scale') {
      next.scale = preset === 'compact' ? 0.75 : 1;
    } else if (spec.key === 'opacity') {
      next.opacity = preset === 'compact' ? 0.7 : 1;
    } else if (spec.control === 'switch' && isOptionalReadout(spec.key)) {
      if (preset === 'compact') {
        next[spec.key] = !seenReadout;
        seenReadout = true;
      } else {
        next[spec.key] = true;
      }
    } else if (spec.control === 'switch' && MOTION_KEYS.has(spec.key) && preset === 'tournament') {
      next[spec.key] = false;
    }
  }

  return next;
}

/**
 * Which preset a set of values currently matches, or `null` for a hand-tuned mod.
 *
 * `on` is excluded: turning a mod off does not take it off its preset.
 */
export function matchingPreset(id: ModId, values: Record<string, unknown>): PresetId | null {
  for (const preset of PRESETS) {
    const candidate = presetSettings(id, preset.id);
    const keys = settingsFor(id).map((spec) => spec.key);
    if (keys.every((key) => sameValue(candidate[key], values[key]))) return preset.id;
  }
  return null;
}

/** Numbers compare with a tolerance because a meter writes a rounded step. */
function sameValue(a: unknown, b: unknown): boolean {
  if (typeof a === 'number' && typeof b === 'number') return Math.abs(a - b) < 1e-4;
  return a === b;
}
