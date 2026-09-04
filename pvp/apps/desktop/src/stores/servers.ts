/**
 * Server favourites and their live pings.
 *
 * The Play screen's "12 ms to Hypixel" and the Servers screen's ping chips are the
 * same number from the same place: `server_ping`, a real Minecraft SLP handshake.
 *
 * TODO(integrate): the favourites list is persisted in `localStorage` rather than by
 * Rust. `schema/loadout.json` has a `server` slug but there is no server-profile
 * schema yet (open question §16.3 — "Rust-side ping; server-bound default loadouts?").
 * When one lands, move this to `servers_list` / `servers_add` / `servers_remove`
 * commands and delete the storage code; the store's shape does not change.
 */

import { create } from 'zustand';

import type { PingResult } from '../local/protocol';
import { errorText, invoke } from '../local/tauri';

export interface ServerEntry {
  host: string;
  name: string;
  favourite: boolean;
}

export interface PingState {
  status: 'idle' | 'pinging' | 'ok' | 'error';
  result?: PingResult;
  error?: string;
  /** Rolling history for the detail pane's sparkline. Newest last, max 12. */
  history: number[];
}

const STORAGE_KEY = 'void.servers.v1';

/** The Figma's favourites (`244:324`), used until the player edits the list. */
const DEFAULT_SERVERS: ServerEntry[] = [
  { host: 'mc.hypixel.net', name: 'Hypixel', favourite: true },
  { host: 'na.minemen.club', name: 'Minemen Club NA', favourite: true },
  { host: 'play.cubecraft.net', name: 'CubeCraft', favourite: true },
  { host: 'eu.minemen.club', name: 'Minemen Club EU', favourite: true },
  { host: 'pvp.land', name: 'PvP Land', favourite: true },
];

function load(): ServerEntry[] {
  if (typeof localStorage === 'undefined') return DEFAULT_SERVERS;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_SERVERS;
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.length > 0 ? (parsed as ServerEntry[]) : DEFAULT_SERVERS;
  } catch {
    return DEFAULT_SERVERS;
  }
}

function save(servers: ServerEntry[]): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(servers));
  } catch {
    /* a full or blocked storage must not take the screen down */
  }
}

interface ServersState {
  servers: ServerEntry[];
  pings: Record<string, PingState>;
  selected: string | null;

  select: (host: string) => void;
  add: (input: string) => void;
  remove: (host: string) => void;
  toggleFavourite: (host: string) => void;
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

export const useServers = create<ServersState>((set, get) => ({
  servers: load(),
  pings: {},
  selected: load()[0]?.host ?? null,

  select: (host) => set({ selected: host }),

  add: (input) => {
    const host = input.trim().toLowerCase();
    if (!host) return;
    if (get().servers.some((s) => s.host === host)) {
      set({ selected: host });
      return;
    }
    const servers = [...get().servers, { host, name: nameForHost(host), favourite: true }];
    save(servers);
    set({ servers, selected: host });
    void get().ping(host);
  },

  remove: (host) => {
    const servers = get().servers.filter((s) => s.host !== host);
    save(servers);
    const selected = get().selected === host ? (servers[0]?.host ?? null) : get().selected;
    set({ servers, selected });
  },

  toggleFavourite: (host) => {
    const servers = get().servers.map((s) =>
      s.host === host ? { ...s, favourite: !s.favourite } : s,
    );
    save(servers);
    set({ servers });
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
