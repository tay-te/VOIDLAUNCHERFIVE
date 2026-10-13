/**
 * The play bar's Server field: the favourites, the one Launch will join, and their pings.
 *
 * Figma v1 Ship, rule 2 — the play bar holds only what decides what launches. Launch
 * joins the chosen server; **Title screen** (`target: null`) launches without joining.
 * Rule 6 — only show data we have: every ping and player count on screen is a real
 * Minecraft SLP handshake through `server_ping`, never a made-up number.
 *
 * TODO(integrate): the favourites list and the choice are persisted in `localStorage`
 * rather than by Rust. `schema/loadout.json` has a `server` slug but there is no
 * per-server schema yet (open question §16.3). When one lands, move this to
 * `servers_list` / `servers_add` / `servers_remove` commands and delete the storage
 * code; the store's shape does not change.
 */

import { create } from 'zustand';

import type { PingResult } from '../local/protocol';
import { errorText, invoke } from '../local/tauri';

export interface ServerEntry {
  host: string;
  name: string;
}

export interface PingState {
  status: 'idle' | 'pinging' | 'ok' | 'error';
  result?: PingResult;
  error?: string;
  /** When the last answer (or failure) landed, for the "retrying in" countdown. */
  at?: number;
}

const STORAGE_KEY = 'void.servers.v2';

/** The favourites the server menu draws (`66:391`), used until the player edits the list. */
export const DEFAULT_SERVERS: readonly ServerEntry[] = [
  { host: 'mc.hypixel.net', name: 'Hypixel' },
  { host: 'na.minemen.club', name: 'Minemen Club NA' },
  { host: 'play.cubecraft.net', name: 'CubeCraft' },
  { host: 'pvp.land', name: 'PvP Land' },
];

interface Persisted {
  servers: ServerEntry[];
  target: string | null;
}

function load(): Persisted {
  const fallback: Persisted = { servers: [...DEFAULT_SERVERS], target: DEFAULT_SERVERS[0]!.host };
  if (typeof localStorage === 'undefined') return fallback;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw) as Partial<Persisted>;
    const servers = Array.isArray(parsed.servers) && parsed.servers.length > 0 ? parsed.servers : fallback.servers;
    const target =
      parsed.target === null
        ? null
        : servers.some((s) => s.host === parsed.target)
          ? (parsed.target as string)
          : (servers[0]?.host ?? null);
    return { servers, target };
  } catch {
    return fallback;
  }
}

function save(state: Persisted): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    /* a full or blocked storage must not take the screen down */
  }
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
 * A server address the way the vanilla multiplayer screen accepts one: a hostname or an
 * IP, an optional `:port`. Returns the normalised address, or `null` if it is not one.
 */
export function normaliseHost(input: string): string | null {
  const host = input.trim().toLowerCase();
  if (!host || host.length > 253) return null;
  return /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*(:\d{1,5})?$/.test(host)
    ? host
    : null;
}

interface ServersState {
  servers: ServerEntry[];
  /** The host Launch joins, or `null` for the title screen. */
  target: string | null;
  pings: Record<string, PingState>;

  choose: (host: string | null) => void;
  /** Add a favourite and make it the target. Returns false for an address that is not one. */
  add: (input: string, name?: string) => boolean;
  remove: (host: string) => void;
  ping: (host: string) => Promise<void>;
  pingAll: () => Promise<void>;
}

const initial = load();

export const useServers = create<ServersState>((set, get) => ({
  servers: initial.servers,
  target: initial.target,
  pings: {},

  choose: (target) => {
    set({ target });
    save({ servers: get().servers, target });
    if (target) void get().ping(target);
  },

  add: (input, name) => {
    const host = normaliseHost(input);
    if (!host) return false;
    const exists = get().servers.some((s) => s.host === host);
    const servers = exists
      ? get().servers
      : [...get().servers, { host, name: name?.trim() || nameForHost(host) }];
    set({ servers, target: host });
    save({ servers, target: host });
    void get().ping(host);
    return true;
  },

  remove: (host) => {
    const servers = get().servers.filter((s) => s.host !== host);
    const target = get().target === host ? (servers[0]?.host ?? null) : get().target;
    set({ servers, target });
    save({ servers, target });
  },

  ping: async (host) => {
    const prev = get().pings[host];
    set({ pings: { ...get().pings, [host]: { ...prev, status: 'pinging' } } });
    try {
      const result = await invoke('server_ping', { host });
      set({ pings: { ...get().pings, [host]: { status: 'ok', result, at: Date.now() } } });
    } catch (e) {
      set({
        pings: {
          ...get().pings,
          [host]: { status: 'error', error: errorText(e), at: Date.now() },
        },
      });
    }
  },

  pingAll: async () => {
    await Promise.all(get().servers.map((s) => get().ping(s.host)));
  },
}));

/** The favourite Launch will join, or `null` when the target is the title screen. */
export function useTargetServer(): ServerEntry | null {
  const target = useServers((s) => s.target);
  const servers = useServers((s) => s.servers);
  if (!target) return null;
  return servers.find((s) => s.host === target) ?? { host: target, name: nameForHost(target) };
}

/** §-consistent thresholds: `--ok` under ~60 ms, muted above (the menu draws 71 ms grey). */
export function pingTone(ms: number): 'ok' | 'muted' {
  return ms < 60 ? 'ok' : 'muted';
}
