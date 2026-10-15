/**
 * Friends v1 — the list, online / on a server, requests in and out (Figma v1 Ship, rule 7).
 *
 * Backed by the online backend through Rust (`friends_*` commands, `online:friends` events;
 * docs/online-contract.md). Every call answers with the whole updated list, and the backend
 * pushes changes — a request arriving, a friend coming online or changing server — so the
 * drawer and Home's "friends on this server" stay live without polling here.
 *
 * Presence is what the backend can know: online while the friend's launcher is open, the
 * server they are on (from their game), and when they were last seen. Not the game mode.
 *
 * Offline (offline account, no backend in this build, not signed in) the last list the server
 * sent is shown from `localStorage`, and sending a request explains why it cannot.
 */

import { create } from 'zustand';

import type { FriendsList } from '../local/protocol';
import { errorText, invoke } from '../local/tauri';

export const FRIENDS_STORAGE_KEY = 'void.friends.v1';

/** What a friend is doing, as far as presence can tell. */
export interface Presence {
  /** `online` when the client is open; `offline` otherwise. */
  state: 'online' | 'offline';
  /** The server they are on, if any — a host, e.g. `mc.hypixel.net`. */
  server?: string;
  /** The game they are in on it, if the server says (`Duels`, `Bedwars`). Not sent yet. */
  game?: string;
  /** In a lobby rather than a match. Not sent yet. */
  lobby?: boolean;
  /** `Date.now()` when last seen, for offline friends. */
  lastSeen?: number;
}

export interface Friend {
  /** Dashed Minecraft UUID; absent only in hand-built test data. */
  uuid?: string;
  name: string;
  presence: Presence;
}

export interface FriendsData {
  friends: Friend[];
  /** Requests other players sent you, by name. */
  incoming: string[];
  /** Requests you sent that nobody has answered, by name. */
  outgoing: string[];
  /** Lower-cased name → UUID for everyone in the three lists, so actions can address them. */
  ids: Record<string, string>;
}

/** Minecraft usernames: 3–16 letters, digits and underscores (rule 7). */
export const USERNAME_RE = /^[A-Za-z0-9_]{3,16}$/;

export const USERNAME_HELP = 'Add someone by their Minecraft username.';
export const USERNAME_ERROR = 'Usernames are 3–16 letters, numbers or underscores.';
export const OFFLINE_ERROR = 'Sign in with a Microsoft account to add friends.';

const EMPTY: FriendsData = { friends: [], incoming: [], outgoing: [], ids: {} };

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
      ids: parsed.ids && typeof parsed.ids === 'object' ? parsed.ids : {},
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

/** The backend's list in the drawer's shape. */
export function fromServer(list: FriendsList): FriendsData {
  const ids: Record<string, string> = {};
  for (const p of [...list.friends, ...list.incoming, ...list.outgoing]) ids[p.name.toLowerCase()] = p.uuid;
  return {
    friends: list.friends.map((f) => ({
      uuid: f.uuid,
      name: f.name,
      presence: {
        state: f.presence.state,
        ...(f.presence.server ? { server: f.presence.server } : {}),
        ...(typeof f.presence.last_seen === 'number' ? { lastSeen: f.presence.last_seen } : {}),
      },
    })),
    incoming: list.incoming.map((r) => r.name),
    outgoing: list.outgoing.map((r) => r.name),
    ids,
  };
}

const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();

interface FriendsState extends FriendsData {
  /** True while the backend is answering. */
  online: boolean;
  error: string | null;

  /** Show the offline copy at once, then ask the backend. */
  hydrate: () => void;
  refresh: () => Promise<void>;
  /** Take a `friends_*` answer or an `online:friends` push. */
  apply: (list: FriendsList) => void;
  /** Go back to the offline copy (signed out). */
  goOffline: () => void;
  /** Send a request. Resolves to the error sentence to show, or `null` on success. */
  request: (name: string) => Promise<string | null>;
  accept: (name: string) => Promise<void>;
  decline: (name: string) => Promise<void>;
  cancel: (name: string) => Promise<void>;
  remove: (name: string) => Promise<void>;
  dismissError: () => void;
}

type ByUuid = 'friends_accept' | 'friends_decline' | 'friends_cancel' | 'friends_remove';

export const useFriends = create<FriendsState>((set, get) => {
  const act = async (cmd: ByUuid, name: string): Promise<void> => {
    const uuid = get().ids[name.toLowerCase()] ?? get().friends.find((f) => same(f.name, name))?.uuid;
    if (!get().online || !uuid) {
      set({ error: OFFLINE_ERROR });
      return;
    }
    try {
      get().apply(await invoke(cmd, { uuid }));
    } catch (e) {
      set({ error: errorText(e) });
    }
  };

  return {
    ...EMPTY,
    online: false,
    error: null,

    hydrate: () => {
      set(load());
      void get().refresh();
    },

    refresh: async () => {
      try {
        get().apply(await invoke('friends_list'));
      } catch {
        set({ online: false });
      }
    },

    apply: (list) => {
      const data = fromServer(list);
      save(data);
      set({ ...data, online: true, error: null });
    },

    goOffline: () => set({ online: false }),

    request: async (input) => {
      const name = input.trim();
      if (!USERNAME_RE.test(name)) return USERNAME_ERROR;
      const { friends, outgoing } = get();
      const friend = friends.find((f) => same(f.name, name));
      if (friend) return `You're already friends with ${friend.name}.`;
      const sent = outgoing.find((n) => same(n, name));
      if (sent) return `You already sent ${sent} a request.`;
      if (!get().online) return OFFLINE_ERROR;
      try {
        // Sending one back to someone who asked first is accepting theirs; the backend does that.
        get().apply(await invoke('friends_request', { name }));
        return null;
      } catch (e) {
        return errorText(e);
      }
    },

    accept: (name) => act('friends_accept', name),
    decline: (name) => act('friends_decline', name),
    cancel: (name) => act('friends_cancel', name),
    remove: (name) => act('friends_remove', name),
    dismissError: () => set({ error: null }),
  };
});

/** Online first, in the order the list holds them. */
export const onlineFriends = (friends: readonly Friend[]): Friend[] =>
  friends.filter((f) => f.presence.state === 'online');

export const offlineFriends = (friends: readonly Friend[]): Friend[] =>
  friends.filter((f) => f.presence.state !== 'online');

/** The host part of a server address, lower-cased, without the default port. */
const hostOf = (addr: string): string => addr.trim().toLowerCase().replace(/\.$/, '').replace(/:25565$/, '');

/** Friends on a given server right now — Home's "MOSS AND TAVI ARE ON HYPIXEL". */
export const friendsOn = (friends: readonly Friend[], host: string | null): Friend[] =>
  host
    ? friends.filter(
        (f) => f.presence.state === 'online' && f.presence.server && hostOf(f.presence.server) === hostOf(host),
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
