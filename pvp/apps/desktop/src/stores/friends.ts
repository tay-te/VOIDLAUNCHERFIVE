/**
 * Friends v1 — the list, online / in a match, requests in and out (Figma v1 Ship, rule 7).
 *
 * TODO(integrate): §16.2 — there is no friends backend yet, so there is no presence and no
 * one on the other end of a request. Until there is, the list lives in `localStorage` and a
 * real install starts empty: requests you send sit under *Sent*, and nobody is ever online.
 * The browser preview's mock (`mocks/tauri.ts`) seeds the frame's own roster so every
 * drawer state can be reviewed — it is the only place those names exist, and it cannot
 * reach a Tauri build (the alias is the whole mechanism; see `vite.config.ts`).
 *
 * When the backend lands, `hydrate` becomes a fetch plus a presence subscription and the
 * five actions become calls; nothing that reads this store changes.
 */

import { create } from 'zustand';

export const FRIENDS_STORAGE_KEY = 'void.friends.v1';

/** What a friend is doing, as far as presence can tell. */
export interface Presence {
  /** `online` when the client is open; `offline` otherwise. */
  state: 'online' | 'offline';
  /** The server they are on, if any — a host, e.g. `mc.hypixel.net`. */
  server?: string;
  /** The game they are in on it, if the server says (`Duels`, `Bedwars`). */
  game?: string;
  /** In a lobby rather than a match. */
  lobby?: boolean;
  /** `Date.now()` when last seen, for offline friends. */
  lastSeen?: number;
}

export interface Friend {
  name: string;
  presence: Presence;
}

export interface FriendsData {
  friends: Friend[];
  /** Requests other players sent you. */
  incoming: string[];
  /** Requests you sent that nobody has answered. */
  outgoing: string[];
}

/** Minecraft usernames: 3–16 letters, digits and underscores (rule 7). */
export const USERNAME_RE = /^[A-Za-z0-9_]{3,16}$/;

export const USERNAME_HELP = 'Add someone by their Minecraft username.';
export const USERNAME_ERROR = 'Usernames are 3–16 letters, numbers or underscores.';

const EMPTY: FriendsData = { friends: [], incoming: [], outgoing: [] };

function load(): FriendsData {
  if (typeof localStorage === 'undefined') return EMPTY;
  try {
    const raw = localStorage.getItem(FRIENDS_STORAGE_KEY);
    if (!raw) return EMPTY;
    const parsed = JSON.parse(raw) as Partial<FriendsData>;
    return {
      friends: Array.isArray(parsed.friends) ? parsed.friends : [],
      incoming: Array.isArray(parsed.incoming) ? parsed.incoming : [],
      outgoing: Array.isArray(parsed.outgoing) ? parsed.outgoing : [],
    };
  } catch {
    return EMPTY;
  }
}

function save(data: FriendsData): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(FRIENDS_STORAGE_KEY, JSON.stringify(data));
  } catch {
    /* the list is still right in memory */
  }
}

const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();

interface FriendsState extends FriendsData {
  hydrate: () => void;
  /** Send a request. Returns the error sentence to show, or `null` on success. */
  request: (name: string) => string | null;
  accept: (name: string) => void;
  decline: (name: string) => void;
  cancel: (name: string) => void;
  remove: (name: string) => void;
}

export const useFriends = create<FriendsState>((set, get) => {
  const commit = (next: Partial<FriendsData>) => {
    const data: FriendsData = {
      friends: next.friends ?? get().friends,
      incoming: next.incoming ?? get().incoming,
      outgoing: next.outgoing ?? get().outgoing,
    };
    save(data);
    set(data);
  };

  return {
    ...EMPTY,

    hydrate: () => set(load()),

    request: (input) => {
      const name = input.trim();
      if (!USERNAME_RE.test(name)) return USERNAME_ERROR;
      const { friends, incoming, outgoing } = get();
      const friend = friends.find((f) => same(f.name, name));
      if (friend) return `You're already friends with ${friend.name}.`;
      if (outgoing.some((n) => same(n, name))) return `You already sent ${name} a request.`;
      // They asked first: sending one back is accepting theirs.
      const theirs = incoming.find((n) => same(n, name));
      if (theirs) {
        get().accept(theirs);
        return null;
      }
      commit({ outgoing: [...outgoing, name] });
      return null;
    },

    accept: (name) => {
      const { friends, incoming } = get();
      if (!incoming.some((n) => same(n, name))) return;
      commit({
        incoming: incoming.filter((n) => !same(n, name)),
        // No presence without a backend: a new friend is offline until it says otherwise.
        friends: [...friends, { name, presence: { state: 'offline' } }],
      });
    },

    decline: (name) => commit({ incoming: get().incoming.filter((n) => !same(n, name)) }),
    cancel: (name) => commit({ outgoing: get().outgoing.filter((n) => !same(n, name)) }),
    remove: (name) => commit({ friends: get().friends.filter((f) => !same(f.name, name)) }),
  };
});

/** Online first, in the order the list holds them. */
export const onlineFriends = (friends: readonly Friend[]): Friend[] =>
  friends.filter((f) => f.presence.state === 'online');

export const offlineFriends = (friends: readonly Friend[]): Friend[] =>
  friends.filter((f) => f.presence.state !== 'online');

/** Friends on a given server right now — Home's "MOSS AND TAVI ARE ON HYPIXEL". */
export const friendsOn = (friends: readonly Friend[], host: string | null): Friend[] =>
  host
    ? friends.filter(
        (f) => f.presence.state === 'online' && f.presence.server && same(f.presence.server, host),
      )
    : [];

/** `4 h ago`, `yesterday`, `3 days ago`. */
export function lastSeenText(at: number | undefined, now = Date.now()): string {
  if (!at) return 'Offline';
  const mins = Math.max(0, Math.round((now - at) / 60_000));
  if (mins < 2) return 'Last seen just now';
  if (mins < 60) return `Last seen ${mins} min ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `Last seen ${hours} h ago`;
  const days = Math.round(hours / 24);
  if (days === 1) return 'Last seen yesterday';
  return `Last seen ${days} days ago`;
}
