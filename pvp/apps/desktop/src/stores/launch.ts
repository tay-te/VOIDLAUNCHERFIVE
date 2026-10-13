/**
 * The launch state machine, the game log ring, the last session and the last crash.
 *
 * The four phases are what the play bar renders:
 *
 * ```text
 *   idle ──prepare()──▶ preparing ──▶ launching ──game:started──▶ running
 *     ▲                     │              │                        │
 *     └───── error ─────────┴──────────────┴──── game:closed ───────┘
 * ```
 *
 * `preparing` and `launching` are separate because they fail differently: preparing
 * fails on the network, launching fails on Java, the account or the protocol version.
 * Collapsing them would make the error surface guess.
 *
 * A non-zero exit is not an `error` (nothing the launcher did failed): it is a `crash`,
 * which Home draws as its own card with *View log* and *Launch again* (Figma `81:5671`).
 */

import { create } from 'zustand';

import type { LogLine, PrepareProgress, SessionStats } from '../local/protocol';
import { errorText, invoke, listen } from '../local/tauri';

export type LaunchPhase = 'idle' | 'preparing' | 'launching' | 'running';

/** How many lines the drawer keeps. Matches the Rust ring buffer. */
const LOG_CAPACITY = 2000;

const LAST_SESSION_KEY = 'void.lastSession.v1';

/**
 * The most recent finished session, as Home's *Last session* column draws it.
 *
 * Rule 6 — only what the game reported: the played time, the average fps and the server
 * come from the mod's `session` summary (via `game:closed`); `ping_avg` only when the mod
 * sent one; `ended_at` is the launcher's own clock.
 */
export interface LastSession {
  played_ms: number;
  fps_avg: number;
  ping_avg?: number;
  server?: string;
  loadout: string;
  ended_at: number;
}

/** A game that exited non-zero, until the player dismisses it or launches again. */
export interface Crash {
  code: number;
  played_ms: number;
  server?: string;
  tail: string[];
}

function loadLastSession(): LastSession | null {
  if (typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(LAST_SESSION_KEY);
    return raw ? (JSON.parse(raw) as LastSession) : null;
  } catch {
    return null;
  }
}

function saveLastSession(session: LastSession): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(LAST_SESSION_KEY, JSON.stringify(session));
  } catch {
    /* storage is a convenience here, never a requirement */
  }
}

interface LaunchState {
  phase: LaunchPhase;
  progress: PrepareProgress | null;
  log: LogLine[];
  bridgePort: number | null;
  error: string | null;
  /** `Date.now()` at `game:started` — the play bar's "12 min" counts from here. */
  startedAt: number | null;
  /** The server this launch was asked to join; `null` for the title screen. */
  launchedServer: string | null;
  /** The previous session, persisted across launcher restarts. */
  lastSession: LastSession | null;
  /** Set by a non-zero exit; Home shows the crash card while it is. */
  crash: Crash | null;
  /** Live presence from `bridge:server`. */
  server: { host: string; connected: boolean } | null;

  start: (loadoutId: string, server: string | null) => Promise<void>;
  kill: () => Promise<void>;
  dismissError: () => void;
  dismissCrash: () => void;
  clearLog: () => void;
}

export const useLaunch = create<LaunchState>((set, get) => ({
  phase: 'idle',
  progress: null,
  log: [],
  bridgePort: null,
  error: null,
  startedAt: null,
  launchedServer: null,
  lastSession: loadLastSession(),
  crash: null,
  server: null,

  start: async (loadoutId, server) => {
    if (get().phase !== 'idle') return;
    set({ error: null, crash: null, progress: null, log: [], launchedServer: server });

    set({ phase: 'preparing' });
    try {
      await invoke('prepare', { loadoutId });
    } catch (e) {
      set({ phase: 'idle', error: errorText(e), progress: null });
      return;
    }

    set({ phase: 'launching' });
    try {
      const report = await invoke('launch', { loadoutId, server });
      set({ bridgePort: report.bridge_port });
      // `phase: 'running'` is set by the `game:started` event, not here — the window
      // hides to the tray on that event, and the two must not disagree.
    } catch (e) {
      set({ phase: 'idle', error: errorText(e) });
    }
  },

  kill: async () => {
    try {
      await invoke('game_kill');
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  dismissError: () => set({ error: null }),
  dismissCrash: () => set({ crash: null }),
  clearLog: () => set({ log: [] }),
}));

/** Exit codes that mean "the player asked it to stop", not "it crashed". */
const STOPPED_CODES = new Set([0, 130, 143]);

export async function wireLaunchEvents(): Promise<() => void> {
  const unlisteners = await Promise.all([
    listen('prepare:progress', (progress) => {
      useLaunch.setState({ progress });
    }),

    listen('game:log', (line) => {
      const log = useLaunch.getState().log;
      const next = log.length >= LOG_CAPACITY ? log.slice(log.length - LOG_CAPACITY + 1) : log.slice();
      next.push(line);
      useLaunch.setState({ log: next });
    }),

    listen('game:started', (payload) => {
      useLaunch.setState({
        phase: 'running',
        bridgePort: payload.bridge_port,
        progress: null,
        startedAt: Date.now(),
      });
    }),

    listen('game:closed', (stats: SessionStats) => {
      const state = useLaunch.getState();
      const server = stats.server ?? state.launchedServer ?? undefined;
      // A session that never reported (killed during start-up) is not a session.
      const lastSession: LastSession | null =
        stats.played_ms > 0
          ? {
              played_ms: stats.played_ms,
              fps_avg: stats.fps_avg,
              ...(stats.ping_avg !== undefined ? { ping_avg: stats.ping_avg } : {}),
              ...(server ? { server } : {}),
              loadout: stats.loadout,
              ended_at: Date.now(),
            }
          : null;
      if (lastSession) saveLastSession(lastSession);

      useLaunch.setState({
        phase: 'idle',
        server: null,
        startedAt: null,
        lastSession: lastSession ?? state.lastSession,
        crash: STOPPED_CODES.has(stats.code)
          ? null
          : {
              code: stats.code,
              played_ms: stats.played_ms,
              ...(server ? { server } : {}),
              tail: stats.crash_tail ?? [],
            },
      });
    }),

    listen('bridge:server', (msg) => {
      useLaunch.setState({ server: { host: msg.host, connected: msg.connected } });
    }),
  ]);

  return () => unlisteners.forEach((u) => u());
}

/** Human-readable step label for the progress row. */
export function stepLabel(step: PrepareProgress['step']): string {
  switch (step) {
    case 'manifest':
      return 'Resolving 1.8.9';
    case 'libraries':
      return 'Libraries';
    case 'assets':
      return 'Assets';
    case 'java':
      return 'Java 8';
    case 'mod':
      return 'VOID client';
    case 'done':
      return 'Ready';
  }
}

export function formatBytes(bytes: number): string {
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return unit === 0 ? `${Math.round(value)} B` : `${value.toFixed(1)} ${units[unit]}`;
}

/** `12 min`, `1 h 4 min`, `48 s` — the play bar's and Home's duration form. */
export function formatDuration(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000));
  if (s < 60) return `${s} s`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const rest = m % 60;
  return rest === 0 ? `${h} h` : `${h} h ${rest} min`;
}
