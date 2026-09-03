/**
 * The launch state machine, the game log ring and the last session's stats.
 *
 * The four phases are what the Launch button renders:
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
 */

import { create } from 'zustand';

import type { LogLine, PrepareProgress, SessionStats } from '../local/protocol';
import { errorText, invoke, listen } from '../local/tauri';

export type LaunchPhase = 'idle' | 'preparing' | 'launching' | 'running';

/** How many lines the drawer keeps. Matches the Rust ring buffer. */
const LOG_CAPACITY = 2000;

interface LaunchState {
  phase: LaunchPhase;
  progress: PrepareProgress | null;
  log: LogLine[];
  bridgePort: number | null;
  /** True when the running game is the desktop stand-in rather than a real JVM. */
  simulated: boolean;
  error: string | null;
  lastSession: SessionStats | null;
  /** Live presence from `bridge:server`, shown on the Play screen. */
  server: { host: string; connected: boolean } | null;

  start: (loadoutId: string) => Promise<void>;
  kill: () => Promise<void>;
  dismissError: () => void;
  dismissSession: () => void;
  clearLog: () => void;
}

export const useLaunch = create<LaunchState>((set, get) => ({
  phase: 'idle',
  progress: null,
  log: [],
  bridgePort: null,
  simulated: false,
  error: null,
  lastSession: null,
  server: null,

  start: async (loadoutId) => {
    if (get().phase !== 'idle') return;
    set({ error: null, lastSession: null, progress: null, log: [] });

    set({ phase: 'preparing' });
    try {
      await invoke('prepare', { loadoutId });
    } catch (e) {
      set({ phase: 'idle', error: errorText(e), progress: null });
      return;
    }

    set({ phase: 'launching' });
    try {
      const report = await invoke('launch', { loadoutId });
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
  dismissSession: () => set({ lastSession: null }),
  clearLog: () => set({ log: [] }),
}));

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
        simulated: payload.simulated === true,
        progress: null,
      });
    }),

    listen('game:closed', (stats: SessionStats) => {
      useLaunch.setState({
        phase: 'idle',
        lastSession: stats,
        server: null,
        // A non-zero exit is a crash, and the tail of the log is the only thing that
        // explains it — surface it rather than letting the drawer stay closed.
        ...(stats.code !== 0
          ? { error: `Minecraft exited with code ${stats.code}. Open the log for the last lines.` }
          : {}),
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
      return 'Resolving manifests';
    case 'libraries':
      return 'Downloading libraries';
    case 'assets':
      return 'Downloading assets';
    case 'fabric':
      return 'Installing Legacy Fabric';
    case 'java':
      return 'Fetching Java 8';
    case 'mod':
      return 'Downloading VOID client';
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
