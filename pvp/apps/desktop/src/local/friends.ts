/**
 * Friends — the type, and the reason there are none.
 *
 * **This file used to export five people.** `marrow`, `pilot_ash`, `nine`, `doorframe` and
 * `kestrel`, three of them online, taken from `Launcher-Friends.png` so the screen would render
 * the frame faithfully while §16.2 stayed open. That was a reasonable thing to do while the
 * screen was a design artefact, and it stopped being reasonable the moment the launcher shipped:
 * the Play dock printed **"3 online"** on the main screen with no caveat anywhere near it, and
 * the Friends screen listed a social graph that does not exist under a footer explaining that it
 * does not exist.
 *
 * There is no friends backend, so there are no friends. The screens keep their layout — the
 * component structure is the design, and it is ready the day a `friends_list` command exists —
 * and they render the empty state that is true today.
 *
 * **Not gated behind the browser preview**, which was the tempting middle path. `IS_TAURI`'s own
 * note rules it out in as many words: "Never for data: the mock answers every command, which is
 * the point." Fabricated data belongs in `mocks/tauri.ts`, answering a command — and there is no
 * command to answer, which is precisely the thing this file was papering over.
 */

/** One friend, as a `friends_list` command would return them. */
export interface Friend {
  name: string;
  status: string;
  action: 'Join' | 'Invite' | 'Message';
  online: boolean;
}

/**
 * Everyone on the player's friends list.
 *
 * Empty, and it will stay empty until §16.2 is answered and a command backs it —
 * `docs/launcher-roster.md` §4 has the dependency order and what the decision actually costs.
 */
export const FRIENDS: readonly Friend[] = [];

/** How many are online. Zero, for as long as there is nowhere to ask. */
export const onlineCount = (): number => FRIENDS.filter((f) => f.online).length;
