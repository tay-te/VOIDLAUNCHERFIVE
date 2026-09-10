/**
 * Server favourites and their live pings.
 *
 * The Play screen's "12 ms to Hypixel" and the Servers screen's ping chips are the
 * same number from the same place: `server_ping`, a real Minecraft SLP handshake.
 *
 * The list is Rust's as of 2026-09-10 — `void_loadout`'s `ServerBook`, persisted as
 * `servers.json` beside the loadouts. It used to be a `localStorage` array, which
 * `docs/launcher-roster.md` §2 scored as a bug with a TODO on it: a desktop app keeping its own
 * data where a cleared cache takes it.
 *
 * **The playtime on each row is not written from here.** It arrives from the running game — the
 * mod reports which host it is on and how long it has been playing, and `void_core::sync::pump`
 * folds that in. These actions carry only what the *player* says: starred, renamed, forgotten.
 * That split is why each command answers with the whole list and the store takes it as truth
 * rather than patching locally: the book is trimmed and re-sorted on write, and a local guess
 * would drift the first time either happened.
 *
 * A per-server default loadout (§16.3) was the obvious next thing to hang here and was **cut on
 * 2026-09-10**: loadouts are ordinary loadouts. `ServerRecord` stays deliberately thin because of
 * it — every field on it is something the player or the game actually said.
 */

import { create } from 'zustand';

import type { PingResult, ServerRecord } from '../local/protocol';

export type { ServerRecord };
import { errorText, invoke } from '../local/tauri';



export interface PingState {
  status: 'idle' | 'pinging' | 'ok' | 'error';
  result?: PingResult;
  error?: string;
  /** Rolling history for the detail pane's sparkline. Newest last, max 12. */
  history: number[];
}

interface ServersState {
  servers: ServerRecord[];
  pings: Record<string, PingState>;
  selected: string | null;
  error: string | null;
  loading: boolean;

  hydrate: () => Promise<void>;
  select: (host: string) => void;
  add: (input: string) => Promise<void>;
  remove: (host: string) => Promise<void>;
  toggleFavourite: (host: string) => Promise<void>;
  ping: (host: string) => Promise<void>;
  pingAll: () => Promise<void>;
}

/** Derive a display name from a hostname: `mc.hypixel.net` → `Hypixel`. */
export function nameForHost(host: string): string {
  const bare = host.split(':')[0] ?? host;
  const parts = bare.split('.').filter(Boolean);
  const core = parts.length >= 2 ? parts[parts.length - 2] : parts[0];
  if (!core) return host;
  return core.charAt(0).toUpperCase() + core.slice(1);
}

/**
 * `2h 41m`, `41m`, or `—` for nothing.
 *
 * An em dash and not `0m`, because they are different states: a server you starred and have
 * never joined has no playtime, and a server you joined and quit has almost none. Only one of
 * those is worth a figure.
 */
export function playedTime(ms: number): string {
  if (ms < 60_000) return '—';
  const minutes = Math.round(ms / 60_000);
  const hours = Math.floor(minutes / 60);
  return hours === 0 ? `${minutes}m` : `${hours}h ${String(minutes % 60).padStart(2, '0')}m`;
}

/**
 * How long ago, coarsely — `today`, `3d`, `2w`. `—` when never.
 *
 * Coarse on purpose: the question this answers is "is this somewhere I still play", and a
 * timestamp to the minute invites reading it as a log. Anything inside a day is `today`, because
 * a player who has played today knows when.
 */
export function lastPlayed(atMs: number, now = Date.now()): string {
  if (atMs <= 0) return '—';
  const days = Math.floor((now - atMs) / 86_400_000);
  if (days <= 0) return 'today';
  if (days === 1) return 'yesterday';
  if (days < 14) return `${days}d`;
  if (days < 60) return `${Math.floor(days / 7)}w`;
  return `${Math.floor(days / 30)}mo`;
}

/**
 * Where the player last actually was, or `null` when they have not been anywhere.
 *
 * **Derived, never stored.** The obvious shape is a `last_server` field somewhere, and it would
 * be a second copy of a fact the book already holds — one that has to be written on every
 * connect, and that is wrong the moment a player forgets the server it points at. The list is
 * already sorted most-recently-played first, so this is a `find`.
 *
 * `joins > 0` and not `favourite`: starring a server is a bookmark, and resuming somewhere you
 * have never been is not resuming. A launcher that offered to continue on a server the player
 * had only ever starred would be guessing, and calling it memory.
 */
export function resumeTarget(servers: readonly ServerRecord[]): ServerRecord | null {
  return servers.find((entry) => entry.joins > 0 && entry.last_played_ms > 0) ?? null;
}

/** The label to draw: the player's, or the one derived from the host. */
export function serverName(entry: ServerRecord): string {
  const given = entry.name?.trim();
  return given && given.length > 0 ? given : nameForHost(entry.host);
}

export const useServers = create<ServersState>((set, get) => ({
  servers: [],
  pings: {},
  selected: null,
  error: null,
  loading: true,

  hydrate: async () => {
    try {
      const servers = await invoke('servers_list');
      // Selection follows the list only when it has to. A hydrate that ran while the player was
      // reading a row would otherwise move them off it — and hydrate runs again after every
      // session, when the newly played server has jumped to the top.
      const selected = get().selected;
      const keep = selected !== null && servers.some((s) => s.host === selected);
      set({
        servers,
        loading: false,
        error: null,
        selected: keep ? selected : (servers[0]?.host ?? null),
      });
    } catch (e) {
      set({ error: errorText(e), loading: false });
    }
  },

  select: (host) => set({ selected: host }),

  add: async (input) => {
    const host = input.trim().toLowerCase();
    if (!host) return;
    if (get().servers.some((s) => s.host === host)) {
      set({ selected: host });
      return;
    }
    try {
      // Adding a server *is* starring it: there is one list, and a row exists because it was
      // played on or starred. `servers.rs` says why two lists would be worse.
      const servers = await invoke('servers_favourite', { host, favourite: true });
      set({ servers, selected: host, error: null });
      void get().ping(host);
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  remove: async (host) => {
    try {
      const servers = await invoke('servers_forget', { host });
      const selected = get().selected === host ? (servers[0]?.host ?? null) : get().selected;
      set({ servers, selected, error: null });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  toggleFavourite: async (host) => {
    const current = get().servers.find((s) => s.host === host);
    try {
      const servers = await invoke('servers_favourite', {
        host,
        favourite: !(current?.favourite ?? false),
      });
      set({ servers, error: null });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  ping: async (host) => {
    const prev = get().pings[host];
    set({
      pings: {
        ...get().pings,
        [host]: { status: 'pinging', history: prev?.history ?? [] },
      },
    });
    try {
      const result = await invoke('server_ping', { host });
      const history = [...(prev?.history ?? []), result.latency_ms].slice(-12);
      set({ pings: { ...get().pings, [host]: { status: 'ok', result, history } } });
    } catch (e) {
      set({
        pings: {
          ...get().pings,
          [host]: { status: 'error', error: errorText(e), history: prev?.history ?? [] },
        },
      });
    }
  },

  pingAll: async () => {
    await Promise.all(get().servers.map((s) => get().ping(s.host)));
  },
}));

/** §-consistent thresholds: `--ok-ink` under ~100 ms, `--warn-ink` above. */
export function pingTone(ms: number): 'ok' | 'warn' | 'bad' {
  if (ms < 100) return 'ok';
  if (ms < 200) return 'warn';
  return 'bad';
}
