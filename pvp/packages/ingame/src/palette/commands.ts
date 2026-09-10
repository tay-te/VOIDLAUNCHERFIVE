/**
 * Everything the quick palette can do. Built fresh from store state on each render,
 * so a row's `currently on` is always the value the mod actually holds.
 *
 * **What Enter does, and why it is not a toggle.**
 *
 * Every result has a primary action on `↵` and a click, and a mod's primary action
 * **opens its properties panel — it does not change anything.** Three reasons, in the
 * order they matter:
 *
 *  · *Consistency.* Opening a mod's page is exactly what clicking its tile does. Search must
 *    not invent a second verb for a noun the rest of the overlay already has a verb for.
 *  · *Feedback.* A result is on screen for a fraction of a second. "Clicking doesn't
 *    take me anywhere" was never a lost click — the toggle fired, on one tile out of
 *    seventeen, and the palette closed over it.
 *    A state change with no visible confirmation is indistinguishable from nothing
 *    happening, and adding the navigation *after* the toggle only made it less clear
 *    which of the two Enter had meant.
 *  · *Reversibility.* Navigating is free to undo. Toggling changes the loadout,
 *    possibly mid-match.
 *
 * Toggling stays available, deliberately and explicitly, on {@link Command.alt} —
 * `⌘↵` / `Ctrl-↵` — which flips the switch **without closing the palette**, so several
 * things can be flipped and each one seen to happen on the row that did it.
 *
 * The rule that decides which rows get which: **a row named for a mod opens that mod;
 * a row named for an action performs that action.** `Fullbright` opens Fullbright.
 * `Turn on Fullbright in Bedwars` does what it says — its title *is* the state change,
 * so there is nothing for Enter to be ambiguous about, and it still ends on the panel
 * so the change is visible. That is why those rows keep a state-changing primary and
 * are not a contradiction of the paragraph above.
 */

import { isModOn, modsOnCount, type VoidState } from '@/store/store';
import type { ModId } from '@/bridge/protocol';
import { MOD_CATEGORY, MOD_ORDER, modLabel } from '@/registry';
import { MOD_ICONS, type IconName } from '@/ui';
import type { Rankable } from './fuzzy';

export interface Command extends Rankable {
  id: string;
  title: string;
  /** Plain-text subtitle. */
  sub?: string;
  /**
   * The tail of the subtitle carrying the mod's live value — `currently ` + `on`.
   *
   * It used to be the *preview* of a change Enter was about to make (`currently off
   * →  on`). Enter no longer makes that change, so the tail is now a readout of what
   * the mod holds, which is the one thing §1 says may be emphasised at all.
   */
  subAccent?: string;
  icon: IconName;
  /** Trailing kbd chips, e.g. `['↵']`. */
  kbd?: string[];
  /** Which caption the row sits under. */
  section: 'actions' | 'also';
  weight?: number;
  /**
   * The primary action: `↵`, and a click on the row. Closes the palette, because it
   * has taken the player somewhere they can see. See the note at the top of the file
   * for why a mod's primary is "open", not "toggle".
   */
  run(store: VoidState): void;
  /**
   * The secondary: `⌘↵` / `Ctrl-↵`. This is where a state change lives, and the palette
   * **stays open** afterwards so the row can show the new value on the next frame.
   * Absent on rows with nothing to toggle, in which case the modified Enter does nothing.
   */
  alt?(store: VoidState): void;
}

export function buildCommands(store: VoidState): Command[] {
  const commands: Command[] = [];
  const { loadout, library } = store;

  // One row per mod, titled with the mod's own name.
  //
  // Not `Toggle Fullbright`: the row does not toggle any more, and a title that names a verb
  // the row does not do is worse than no title. The name alone also reads as what it is — a
  // thing you can go to, like `Loadouts` and `Party` below — which is the whole reframe, and
  // it quietly fixes `Toggle Toggle sprint`, a row that existed because a mod called
  // "Toggle sprint" was being prefixed with the same word.
  //
  // The old second row per mod (`Fullbright settings`, "Open the properties panel") is gone:
  // that is now precisely what this row's Enter does, and two rows doing the same thing is the
  // duplicate-title problem in a different shape. It also gives the three-row ACTIONS list back
  // to three different mods.
  for (const id of MOD_ORDER) {
    const on = isModOn(loadout, id);
    commands.push({
      id: `mod:${id}`,
      title: modLabel(id),
      sub: `${label(MOD_CATEGORY[id])}  ·  currently `,
      subAccent: on ? 'on' : 'off',
      icon: MOD_ICONS[id],
      kbd: ['↵'],
      section: 'actions',
      weight: 6,
      run: (s) => openProperties(s, id),
      // Read the value off the store at the moment of the press, not off the `on` captured
      // when this list was built: with the palette staying open, a second ⌘↵ on the same row
      // has to see what the first one did.
      alt: (s) => s.toggleMod(id, !isModOn(s.loadout, id)),
    });
  }

  // "Turn on Fullbright in Bedwars" — the third row of the frame, with the mod in the title.
  //
  // The frame's copy was `Turn on in Bedwars loadout`, with the mod named only in the subtitle.
  // One row of that reads fine; the set does not. There is one of these per (loadout, mod-that-
  // is-off) pair — thirty-four at seventeen mods and three loadouts — and every one of them had
  // the *same title*, so any query touching `loadout` scored them all identically and the
  // palette answered `load` with four rows of `Turn on in UHC loadout`. Worse, the query a
  // player actually types is the mod's name, and the mod's name was the one thing the ranked
  // text did not contain: it matched through `sub` at the 0.4 discount or not at all.
  //
  // Naming the mod fixes the ranking and the ambiguity with the same words.
  for (const other of library) {
    if (other.id === loadout?.id) continue;
    for (const id of MOD_ORDER) {
      if (isModOn(other, id)) continue;
      commands.push({
        id: `enable-in:${other.id}:${id}`,
        title: `Turn on ${modLabel(id)} in ${other.name}`,
        // The loadout is named in the title, so the subtitle does not say `loadout` again —
        // and not only because it reads better. `sub` is ranked too, at a discount, so a word
        // repeated across thirty-four rows is a word that answers `load` with thirty-four rows.
        sub: `${label(MOD_CATEGORY[id])}  ·  currently off`,
        icon: 'layers',
        section: 'actions',
        weight: 1,
        // Switches loadout, turns the mod on, and shows it: three state changes at once is
        // exactly the case where closing on a grid that has silently rearranged itself tells
        // the player nothing.
        run: (s) => {
          s.switchLoadout(other.id);
          s.toggleMod(id, true);
          openProperties(s, id);
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
      // Onto the Loadouts screen, where which one is active is the thing the screen is about.
      // Switching from the Mods grid does change every tile, but a grid of seventeen switches
      // settling into a different pattern is not an answer to "did that work".
      run: (s) => {
        s.switchLoadout(other.id);
        s.setRoute({ name: 'loadouts' });
      },
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
      id: 'open:review',
      title: 'Fight review',
      sub: 'Your last fights, second by second',
      icon: 'sword',
      section: 'also',
      // A weight above the other two, and it is the one route on this list with a reason to be
      // ranked: Loadouts and Party are places you go, and this is a thing that just happened.
      // A player types `f` here within seconds of the fight it is about.
      weight: 4,
      run: (s) => s.setRoute({ name: 'review' }),
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
      icon: 'close',
      kbd: ['esc'],
      section: 'also',
      run: (s) => s.closeMenu(),
    },
  );

  return commands;
}

/**
 * Go to a mod's page.
 *
 * One call, and the same one the tile body makes: `openMod` writes the route and the grid's
 * selection together, so the palette cannot land somewhere a click could not. It used to have
 * to set the route to `mods` first and then select — two writes, and a `setRoute` that was only
 * there because the destination was a panel on another screen rather than a place of its own.
 */
function openProperties(store: VoidState, id: ModId): void {
  store.openMod(id);
}

function label(category: string): string {
  return category.charAt(0) + category.slice(1).toLowerCase();
}
