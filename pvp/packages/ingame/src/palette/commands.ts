/**
 * Everything the quick palette can run. Built fresh from store state on each
 * open, so the "currently off → on" previews are always true.
 */

import { isModOn, modsOnCount, type VoidState } from '@/store/store';
import { MOD_ORDER, MOD_REGISTRY } from '@/registry';
import type { IconName } from '@/icons/Icon';
import type { Rankable } from './fuzzy';

export interface Command extends Rankable {
  id: string;
  title: string;
  /** Plain-text subtitle. */
  sub?: string;
  /**
   * The tail of the subtitle rendered in `--accent-ink`, as the frame's
   * `currently off  →  on` is.
   */
  subAccent?: string;
  icon: IconName;
  /** Trailing kbd chips, e.g. `['↵']` or `['⌘', '↵']`. */
  kbd?: string[];
  /** Which caption the row sits under. */
  section: 'actions' | 'also';
  weight?: number;
  run(store: VoidState): void;
  /** ⌘↵ opens the action's settings instead of running it. */
  settings?(store: VoidState): void;
}

export function buildCommands(store: VoidState): Command[] {
  const commands: Command[] = [];
  const { loadout, library } = store;

  for (const id of MOD_ORDER) {
    const entry = MOD_REGISTRY[id];
    const on = isModOn(loadout, id);
    commands.push({
      id: `toggle:${id}`,
      title: `Toggle ${entry.label}`,
      sub: `${label(entry.category)}  ·  currently ${on ? 'on' : 'off'}  →  `,
      subAccent: on ? 'off' : 'on',
      icon: entry.icon as IconName,
      kbd: ['↵'],
      section: 'actions',
      weight: 6,
      run: (s) => s.toggleMod(id, !isModOn(s.loadout, id)),
      settings: (s) => s.setRoute({ name: 'mod-settings', mod: id }),
    });
    commands.push({
      id: `settings:${id}`,
      title: `${entry.label} settings`,
      sub: 'Open in the mod menu',
      icon: 'settings',
      kbd: ['⌘', '↵'],
      section: 'actions',
      weight: 2,
      run: (s) => s.setRoute({ name: 'mod-settings', mod: id }),
    });
  }

  // "Turn on in Bedwars loadout" — the third row of the frame.
  for (const other of library) {
    if (other.id === loadout?.id) continue;
    for (const id of MOD_ORDER) {
      if (isModOn(other, id)) continue;
      commands.push({
        id: `enable-in:${other.id}:${id}`,
        title: `Turn on in ${other.name} loadout`,
        sub: `${MOD_REGISTRY[id].label} is off in that loadout`,
        icon: 'layers',
        section: 'actions',
        weight: 1,
        run: (s) => {
          s.switchLoadout(other.id);
          s.toggleMod(id, true);
        },
      });
    }
  }

  for (const other of library) {
    if (other.id === loadout?.id) continue;
    commands.push({
      id: `switch:${other.id}`,
      title: `Switch to ${other.name}`,
      sub: `${modsOnCount(other)} mods on  ·  ${other.server ?? 'any server'}`,
      icon: 'sword',
      kbd: ['↵'],
      section: 'actions',
      weight: 5,
      run: (s) => s.switchLoadout(other.id),
    });
  }

  commands.push(
    {
      id: 'open:loadouts',
      title: 'Loadouts',
      sub: 'Compare and switch loadouts',
      icon: 'layers',
      section: 'also',
      weight: 3,
      run: (s) => s.setRoute({ name: 'loadouts' }),
    },
    {
      id: 'open:party',
      title: 'Party',
      sub: 'Party and queue',
      icon: 'users',
      section: 'also',
      weight: 3,
      run: (s) => s.setRoute({ name: 'party' }),
    },
    {
      id: 'open:mods',
      title: 'Mods',
      sub: 'Browse and toggle mods',
      icon: 'box',
      section: 'also',
      weight: 3,
      run: (s) => s.setRoute({ name: 'mods' }),
    },
    {
      id: 'open:hud-editor',
      title: 'Edit HUD layout',
      sub: 'Drag widgets over the game',
      icon: 'move',
      section: 'also',
      weight: 3,
      run: (s) => s.setRoute({ name: 'hud-editor' }),
    },
    {
      id: 'close',
      title: 'Close menu',
      sub: 'Back to the game  ·  R-Shift',
      icon: 'x',
      kbd: ['esc'],
      section: 'also',
      run: (s) => s.closeMenu(),
    },
  );

  return commands;
}

function label(category: string): string {
  return category.charAt(0) + category.slice(1).toLowerCase();
}
