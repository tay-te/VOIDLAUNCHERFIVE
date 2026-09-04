/**
 * The contract types, in one import.
 *
 * Everything the schemas define comes from `@void/protocol`, which generates it from
 * `pvp/schema/*.json`. Nothing in `src/` re-declares a schema type, and nothing imports
 * `@void/protocol` directly — going through this module means the launcher-only shapes
 * below sit next to the shared ones instead of being mistaken for them.
 *
 * The launcher-only half is the DTOs `src-tauri/src/models.rs` declares: things that
 * exist between the window and Rust and never reach the game. They are transcribed by
 * hand because there is no schema for them — the schemas describe the Rust ⇄ Java seam,
 * not the Rust ⇄ webview one.
 */

// ------------------------------------------------------- from the schemas
export type {
  Loadout,
  LoadoutId,
  LoadoutStats,
  ModId,
  ModStates,
  ModKind,
  HypixelSafetyClass,
  HUDItem,
  HUDModId,
  HUDAnchor,
  HUDLayout,
  Keybind,
  ProtocolVersion,
  JavaToRust,
} from '@void/protocol';

import type {
  JavaToRust as JavaToRustMessage,
  Loadout,
  LoadoutId,
  LoadoutStats,
  ModId,
  ModStates,
} from '@void/protocol';

/**
 * What `loadouts_list` / `loadouts_delete` return — `void_loadout::LoadoutSummary`,
 * field for field.
 *
 * It sits on the launcher-only side of this file because it is a launcher-only shape:
 * the bridge sends the mod whole loadouts (`init.loadouts`, `loadouts`), never
 * summaries, so `schema/loadout.json` has no reason to describe one and
 * `@void/protocol` does not generate it.
 */
export interface LoadoutSummary {
  id: LoadoutId;
  name: string;
  icon: string;
  server?: string | null;
  stats?: LoadoutStats;
}

/** One mod's settings, as an open bag — the union of all twelve settings shapes. */
export type ModSettings = NonNullable<ModStates[ModId]> & Record<string, unknown>;

/** What `loadouts_update` accepts: only what the player touched. */
export interface LoadoutPatch {
  name?: string;
  icon?: string;
  server?: string | null;
  /** One mod at a time; Rust merges each over the registry defaults and validates it. */
  mods?: Partial<Record<ModId, Record<string, unknown>>>;
  hud?: Loadout['hud'];
}

// -------------------------------------------------------- launcher only

/**
 * The settings screen's view, joined from three stores by `settings_get`:
 * `settings.json` (crosses to the game), its `extra` map (launcher preferences) and
 * `config.json` (launcher only). See `src-tauri/src/models.rs`.
 */
export interface Settings {
  menu_key: string;
  cycle_loadout_key: string;
  theme: string;
  ui_scale: number;
  hud_editor_grid: number;
  hide_to_tray_on_launch: boolean;
  update_channel: string;
  java_auto: boolean;
  java_path: string | null;
  ram_mb: number;
  mod_jar: string | null;
  active_loadout: string;
}

export type SettingsPatch = Partial<Omit<Settings, 'active_loadout'>>;

/** The account as the dock renders it. Never carries an access token. */
export interface Account {
  uuid: string;
  name: string;
  kind: 'microsoft' | 'offline';
  skin_url: string | null;
}

export interface DeviceCode {
  user_code: string;
  verification_uri: string;
  expires_in_s: number;
  interval_s: number;
  message: string | null;
}

export type AuthStatus =
  | { stage: 'pending'; message: string }
  | { stage: 'xbox'; message: string }
  | { stage: 'minecraft'; message: string }
  | { stage: 'complete'; account: Account }
  | { stage: 'failed'; message: string };

export interface SystemInfo {
  os: string;
  os_version: string;
  arch: string;
  cpu: string;
  cpu_cores: number;
  ram_total_mb: number;
  ram_available_mb: number;
  recommended_ram_mb: number;
  app_version: string;
  data_dir: string;
}

export interface JavaStatus {
  /** True only for Java **8**: 1.8.9 will not start on anything newer. */
  found: boolean;
  path: string | null;
  version: string | null;
  major: number | null;
  source: string;
}

export type PrepareStep = 'manifest' | 'libraries' | 'assets' | 'java' | 'mod' | 'done';

export interface PrepareProgress {
  step: PrepareStep;
  done: number;
  total: number;
  bytes_per_sec: number;
  detail?: string;
}

export interface PrepareReport {
  loadout: string;
  version_id: string;
  files: number;
  downloaded_bytes: number;
  duration_ms: number;
  java_path: string;
  java_version: string;
}

export interface LaunchReport {
  pid: number;
  bridge_port: number;
  loadout: string;
}

export interface SessionStats {
  code: number;
  loadout: string;
  played_ms: number;
  fps_avg: number;
  server?: string;
  crash_tail?: string[];
}

export interface LogLine {
  stream: 'stdout' | 'stderr';
  line: string;
  ts_ms: number;
}

export interface PingResult {
  host: string;
  port: number;
  latency_ms: number;
  online: number;
  max: number;
  version: string;
  motd: string;
  favicon?: string;
}

export interface UpdateInfo {
  available: boolean;
  current_version: string;
  version: string | null;
  notes: string | null;
  date: string | null;
  error: string | null;
}

// ------------------------------- bridge messages (§7), forwarded verbatim

/**
 * The three `protocol.json` messages `adapters::game`'s forwarder republishes to the
 * window as `bridge:state` / `bridge:session` / `bridge:server`.
 *
 * They are **aliases**, not transcriptions: these are schema types, so they come from
 * `@void/protocol` like every other one. The launcher re-names them only because
 * `StateJavaToRust` reads oddly at a `listen('bridge:state', …)` call site. The other
 * three `JavaToRust` variants (`hello`, `hud`, `hotkey`) never reach the window —
 * Rust answers `hello`, persists `hud` and acts on `hotkey` itself.
 */
export type BridgeState = Extract<JavaToRustMessage, { t: 'state' }>;
export type BridgeSession = Extract<JavaToRustMessage, { t: 'session' }>;
export type BridgeServer = Extract<JavaToRustMessage, { t: 'server' }>;
