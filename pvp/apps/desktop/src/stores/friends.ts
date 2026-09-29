/**
 * Friends and friend requests — v1's whole social surface.
 *
 * v1 is deliberately small: a list, who is online, and requests in both directions.
 * No party, no chat, no presence beyond online / in a match. Those need a relay and
 * moderation, and they are v1.1.
 *
 * TODO(integrate): there is no friends backend yet. Every action below mutates local
 * state so the drawer is fully usable in the preview; each one is the single place a
 * Supabase call replaces the `set`. The store's shape is the contract the drawer and
 * the navbar read, and it does not change when the backend lands.
 */

import { create } from 'zustand';

export interface Friend {
  id: string;
  name: string;
  online: boolean;
  /** What they are doing, or when they were last seen. Printed as the row's second line. */
  activity: string;
}

export interface FriendRequest {
  id: string;
  name: string;
  direction: 'incoming' | 'outgoing';
}

/** Minecraft's own username rule: 3–16 of letters, digits and underscore. */
const USERNAME = /^[A-Za-z0-9_]{3,16}$/;

/** Why `sendRequest` refused, or `null` when it went through. */
export type RequestError = 'invalid' | 'self' | 'friend' | 'pending';

export const REQUEST_ERROR_TEXT: Record<RequestError, string> = {
  invalid: 'Usernames are 3–16 letters, numbers or underscores.',
  self: 'That is you.',
  friend: 'Already on your list.',
  pending: 'A request is already waiting.',
};

interface FriendsState {
  friends: Friend[];
  requests: FriendRequest[];

  /** Send a request by Minecraft username. `self` is the signed-in name, if any. */
  sendRequest: (name: string, self?: string) => RequestError | null;
  accept: (id: string) => void;
  /** Decline an incoming request or cancel an outgoing one. */
  dismiss: (id: string) => void;
  remove: (id: string) => void;
}

/** The frames' own roster, until the backend supplies a real one. */
const SEED_FRIENDS: Friend[] = [
  { id: 'f-moss', name: 'Moss', online: true, activity: 'Duels · Hypixel' },
  { id: 'f-rin', name: 'Rin_', online: true, activity: 'In the lobby' },
  { id: 'f-tavi', name: 'tavi', online: true, activity: 'Bedwars · Hypixel' },
  { id: 'f-juno', name: 'Juno', online: true, activity: 'Online' },
  { id: 'f-otto', name: 'Otto', online: false, activity: 'Last seen 4 h ago' },
  { id: 'f-pilot', name: 'pilot_ash', online: false, activity: 'Last seen yesterday' },
];

const SEED_REQUESTS: FriendRequest[] = [
  { id: 'r-marrow', name: 'marrow', direction: 'incoming' },
];

let seq = 0;
const nextId = (prefix: string): string => `${prefix}-${Date.now().toString(36)}-${seq++}`;

const same = (a: string, b: string): boolean => a.toLowerCase() === b.toLowerCase();

export const useFriends = create<FriendsState>((set, get) => ({
  friends: SEED_FRIENDS,
  requests: SEED_REQUESTS,

  sendRequest: (raw, self) => {
    const name = raw.trim();
    if (!USERNAME.test(name)) return 'invalid';
    if (self && same(name, self)) return 'self';
    const { friends, requests } = get();
    if (friends.some((f) => same(f.name, name))) return 'friend';

    // Sending to someone who already asked you is an accept, not a second request.
    const incoming = requests.find((r) => r.direction === 'incoming' && same(r.name, name));
    if (incoming) {
      get().accept(incoming.id);
      return null;
    }
    if (requests.some((r) => same(r.name, name))) return 'pending';

    set({ requests: [...requests, { id: nextId('r'), name, direction: 'outgoing' }] });
    return null;
  },

  accept: (id) => {
    const request = get().requests.find((r) => r.id === id && r.direction === 'incoming');
    if (!request) return;
    set((s) => ({
      requests: s.requests.filter((r) => r.id !== id),
      friends: [
        ...s.friends,
        { id: nextId('f'), name: request.name, online: false, activity: 'Just added' },
      ],
    }));
  },

  dismiss: (id) => set((s) => ({ requests: s.requests.filter((r) => r.id !== id) })),

  remove: (id) => set((s) => ({ friends: s.friends.filter((f) => f.id !== id) })),
}));

/** Online first, then alphabetical — the order both the navbar stack and the drawer use. */
export function sortFriends(friends: readonly Friend[]): Friend[] {
  return [...friends].sort(
    (a, b) => Number(b.online) - Number(a.online) || a.name.localeCompare(b.name),
  );
}
