/**
 * The one place the app talks to Rust.
 *
 * `invoke` and `listen` come from `@tauri-apps/api`, which `vite.config.ts` aliases to
 * `src/mocks/tauri.ts` whenever we are not inside Tauri. That means:
 *
 * - no `if (window.__TAURI__)` branches anywhere in the app;
 * - `pnpm dev:web` renders every screen in a plain browser;
 * - the mock and the real backend are held to the same `Cmd` map below, so a command
 *   that exists in one and not the other is a type error, not a runtime surprise.
 *
 * The command names match `src-tauri/src/ipc.rs` exactly.
 */

import { invoke as rawInvoke } from '@tauri-apps/api/core';
import { listen as rawListen } from '@tauri-apps/api/event';

import type {
  Account,
  DeviceCode,
  JavaStatus,
  LaunchReport,
  Loadout,
  LoadoutPatch,
  LoadoutSummary,
  PingResult,
  PrepareReport,
  Settings,
  SettingsPatch,
  SystemInfo,
  UpdateInfo,
} from './protocol';

/** Every command, its argument object and its return type. */
export interface Cmd {
  auth_login: [Record<string, never>, DeviceCode];
  auth_logout: [Record<string, never>, void];
  auth_current: [Record<string, never>, Account | null];
  auth_offline: [{ name: string }, Account];

  prepare: [{ loadoutId: string }, PrepareReport];
  launch: [{ loadoutId: string }, LaunchReport];
  game_kill: [Record<string, never>, void];
  game_log_tail: [{ lines?: number }, string[]];

  loadouts_list: [Record<string, never>, LoadoutSummary[]];
  loadouts_get: [{ id: string }, Loadout];
  loadouts_active: [Record<string, never>, Loadout];
  loadouts_create: [{ name: string; icon?: string }, Loadout];
  loadouts_update: [{ id: string; patch: LoadoutPatch }, Loadout];
  loadouts_delete: [{ id: string }, LoadoutSummary[]];
  loadouts_switch: [{ id: string }, Loadout];

  settings_get: [Record<string, never>, Settings];
  settings_set: [{ patch: SettingsPatch }, Settings];

  system_info: [Record<string, never>, SystemInfo];
  java_status: [Record<string, never>, JavaStatus];
  server_ping: [{ host: string }, PingResult];
  open_data_dir: [Record<string, never>, string];
  updater_check: [Record<string, never>, UpdateInfo];

  window_minimize: [Record<string, never>, void];
  window_toggle_maximize: [Record<string, never>, void];
  window_close: [Record<string, never>, void];
}

export type CmdName = keyof Cmd;

/**
 * Typed `invoke`. Rejects with the `String` the Rust side produced — already a
 * player-readable sentence, because `error.rs` writes `Display` for a player.
 */
export async function invoke<K extends CmdName>(
  cmd: K,
  ...args: Cmd[K][0] extends Record<string, never> ? [] : [Cmd[K][0]]
): Promise<Cmd[K][1]> {
  return rawInvoke(cmd, (args[0] ?? {}) as Record<string, unknown>) as Promise<Cmd[K][1]>;
}

/** Every event the Rust side emits, and its payload. */
export interface Evt {
  'auth:status': import('./protocol').AuthStatus;
  'prepare:progress': import('./protocol').PrepareProgress;
  'game:log': import('./protocol').LogLine;
  'game:started': { pid: number; loadout: string; bridge_port: number };
  'game:closed': import('./protocol').SessionStats;
  'bridge:state': import('./protocol').BridgeState;
  'bridge:session': import('./protocol').BridgeSession;
  'bridge:server': import('./protocol').BridgeServer;
  'loadout:switched': Loadout;
  'update:available': UpdateInfo;
}

export type EvtName = keyof Evt;

/** Typed `listen`. Resolves to the unlisten function. */
export function listen<K extends EvtName>(
  event: K,
  handler: (payload: Evt[K]) => void,
): Promise<() => void> {
  return rawListen(event, (e: { payload: unknown }) => handler(e.payload as Evt[K]));
}

/**
 * True when the app is running inside Tauri rather than a browser.
 *
 * Used only for the handful of things that genuinely differ — the window controls in
 * the top nav, and the "this is the browser preview" hint. Never for data: the mock
 * answers every command, which is the point.
 */
export const IS_TAURI: boolean =
  typeof window !== 'undefined' && '__TAURI_INTERNALS__' in (window as object);

/** Format a rejected `invoke` for the error surfaces. */
export function errorText(e: unknown): string {
  if (typeof e === 'string') return e;
  if (e instanceof Error) return e.message;
  return 'Something went wrong.';
}
