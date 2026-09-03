/**
 * §11: **"HYPIXEL-READY" is on when every *enabled* mod is `safe`.**
 *
 * The two `grey` mods are Fullbright and Hitboxes. A disabled grey mod does not count
 * — that is the whole point of the badge: it answers "is what I am about to launch
 * with going to get me banned", not "does this client contain risky features".
 *
 * Lives on its own because the Play screen's status pill, the Mods screen's tile
 * warnings and (later) the tray tooltip all need the same answer, and because it is
 * the one piece of anti-cheat policy in the launcher — it should be findable.
 */

import type { ModId, ModStates } from './protocol';
import { MOD_IDS, MOD_REGISTRY, isOn } from './registry';

export interface HypixelReadiness {
  ready: boolean;
  /** Enabled mods classified `grey`, in grid order. Empty when `ready`. */
  greyMods: ModId[];
  /** What the eyebrow pill says: `HYPIXEL-READY` or `REVIEW MODS`. */
  label: string;
  /** Longer form for a tooltip / the Mods footer. */
  detail: string;
}

export function hypixelReady(mods: ModStates): HypixelReadiness {
  const greyMods = MOD_IDS.filter(
    (id) => isOn(mods, id) && MOD_REGISTRY[id].hypixel_safe === 'grey',
  );
  const ready = greyMods.length === 0;

  if (ready) {
    return {
      ready,
      greyMods,
      label: 'HYPIXEL-READY',
      detail: 'Every enabled mod is client-side and Watchdog-safe.',
    };
  }

  const names = greyMods.map((id) => MOD_REGISTRY[id].label);
  return {
    ready,
    greyMods,
    label: 'REVIEW MODS',
    detail: `${listSentence(names)} ${names.length === 1 ? 'is' : 'are'} not Hypixel-safe. Turn ${
      names.length === 1 ? 'it' : 'them'
    } off before joining.`,
  };
}

/** `a`, `a and b`, `a, b and c`. */
function listSentence(items: string[]): string {
  if (items.length <= 1) return items[0] ?? '';
  return `${items.slice(0, -1).join(', ')} and ${items[items.length - 1]}`;
}
