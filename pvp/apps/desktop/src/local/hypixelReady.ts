/**
 * §11: **"HYPIXEL-READY" is on when every *enabled* mod is `safe`.**
 *
 * The predicate itself lives in `@void/protocol` (`hypixelReady`), generated against the
 * registry, so the launcher and the in-game bundle can never disagree about the badge.
 * What this module adds is the *copy* around it: which mods disqualified the loadout,
 * and the sentence the Play screen and the Mods footer print — that is launcher wording,
 * not contract.
 */

import { hypixelReady as isReady, MOD_REGISTRY } from '@void/protocol';
import type { Loadout, ModId } from './protocol';
import { MOD_GRID_ORDER, getModLabel, isOn } from './registry';

export interface HypixelReadiness {
  ready: boolean;
  /** Enabled mods classified `grey`, in grid order. Empty when `ready`. */
  greyMods: ModId[];
  /** What the eyebrow pill says. */
  label: string;
  /** Longer form, for a tooltip or the Mods footer. */
  detail: string;
}

export function hypixelReady(loadout: Pick<Loadout, 'mods'>): HypixelReadiness {
  const ready = isReady(loadout);
  const greyMods = MOD_GRID_ORDER.filter(
    (id) => isOn(loadout, id) && MOD_REGISTRY[id].hypixel_safe === 'grey',
  );

  if (ready) {
    return {
      ready,
      greyMods,
      label: 'HYPIXEL-READY',
      detail: 'Every enabled mod is client-side and Watchdog-safe.',
    };
  }

  const names = greyMods.map(getModLabel);
  const one = names.length === 1;
  return {
    ready,
    greyMods,
    label: 'REVIEW MODS',
    detail: `${listSentence(names)} ${one ? 'is' : 'are'} not Hypixel-safe. Turn ${
      one ? 'it' : 'them'
    } off before joining ranked.`,
  };
}

/** `a`, `a and b`, `a, b and c`. */
function listSentence(items: string[]): string {
  if (items.length <= 1) return items[0] ?? '';
  return `${items.slice(0, -1).join(', ')} and ${items[items.length - 1]}`;
}
