/**
 * TODO(integrate): these types belong to `@void/protocol`, which generates them from
 * `pvp/schema/*.json` with `json-schema-to-typescript`. That package currently ships
 * `src/generated/*` but has no built `dist` and no `src/index.ts`, so importing it
 * would break `pnpm typecheck` here.
 *
 * When it does export them, delete the type bodies below and re-export instead:
 *
 * ```ts
 * export type { Loadout, LoadoutSummary, HudItem, Anchor, ModId, GlobalSettings }
 *   from '@void/protocol';
 * ```
 *
 * Everything in `src/` imports from this module and never from `@void/protocol`
 * directly, so that swap is a one-file change. The shapes below are transcribed from
 * the schemas and match what `src-tauri/src/models.rs` serialises — the two halves of
 * the same contract, kept side by side deliberately.
 */

// ------------------------------------------------------------------ mod registry

/** The closed set of 12 mod ids (`mods.json#/definitions/mod_id`). */
export type ModId =
  | 'fps'
  | 'keystrokes'
  | 'cps'
  | 'ping'
  | 'coordinates'
  | 'armor_status'
  | 'potion_effects'
  | 'toggle_sprint'
  | 'fullbright'
  | 'hitboxes'
  | 'zoom'
  | 'crosshair';

/** HUD mods draw; gameplay mods mutate a documented client-side option. */
export type ModKind = 'hud' | 'gameplay';

/** §11: the badge is on only when every *enabled* mod is `safe`. */
export type HypixelSafe = 'safe' | 'grey';

/** One mod's live state: `on` plus an open bag of settings. */
export type ModState = { on: boolean } & Record<string, unknown>;

export type ModStates = Partial<Record<ModId, ModState>>;

// ----------------------------------------------------------------------- loadout

export type Anchor =
  | 'top-left'
  | 'top'
  | 'top-right'
  | 'left'
  | 'center'
  | 'right'
  | 'bottom-left'
  | 'bottom'
  | 'bottom-right';

export interface HudItem {
  id: ModId;
  anchor: Anchor;
  dx: number;
  dy: number;
  scale?: number;
}

export interface LoadoutStats {
  played_ms: number;
  fps_avg: number;
}

export interface Loadout {
  id: string;
  name: string;
  icon: string;
  server: string | null;
  mc: string;
  mods: ModStates;
  hud: HudItem[];
  stats: LoadoutStats;
}

export interface LoadoutSummary {
  id: string;
  name: string;
  icon: string;
  server: string | null;
  stats: LoadoutStats;
}

/** What `loadouts_update` accepts — only what the player touched. */
export interface LoadoutPatch {
  name?: string;
  icon?: string;
  server?: string | null;
  mods?: ModStates;
  hud?: HudItem[];
}

// ---------------------------------------------------------------------- settings

/**
 * `protocol.json#/definitions/global_settings` plus the launcher-only fields.
 * The first five cross to the game in `init`; the rest never leave Rust.
 */
export interface Settings {
  menu_key: string;
  cycle_loadout_key: string;
  theme: string;
  ui_scale: number;
  hud_editor_grid: number;
  java_auto: boolean;
  java_path: string | null;
  ram_mb: number;
  hide_to_tray_on_launch: boolean;
  update_channel: string;
  active_loadout: string;
}

export type SettingsPatch = Partial<Omit<Settings, 'active_loadout'>>;

// ----------------------------------------------------------------------- account

export interface Account {
  uuid: string;
  name: string;
  kind: 'microsoft' | 'offline';
  level: number;
  skin_url: string | null;
}

export interface DeviceCode {
  user_code: string;
  verification_uri: string;
  expires_in_s: number;
  interval_s: number;
}

export type AuthStatus =
  | { stage: 'pending'; message: string }
  | { stage: 'xbox'; message: string }
  | { stage: 'minecraft'; message: string }
  | { stage: 'complete'; account: Account }
  | { stage: 'failed'; message: string };

// ------------------------------------------------------------ system / prepare

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
}

export interface JavaStatus {
  found: boolean;
  path: string | null;
  version: string | null;
  source: string;
}

export type PrepareStep =
  | 'manifest'
  | 'libraries'
  | 'assets'
  | 'fabric'
  | 'java'
  | 'mod'
  | 'done';

export interface PrepareProgress {
  step: PrepareStep;
  done: number;
  total: number;
  bytes_per_sec: number;
  detail?: string;
}

export interface PrepareReport {
  loadout: string;
  bytes_downloaded: number;
  duration_ms: number;
  java_path: string;
  from_cache: boolean;
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

// -------------------------------------------------- bridge messages (§7, forwarded)

/** `bridge:state` — a flat map of `mods.<mod>.<setting>` to its new value. */
export interface BridgeState {
  t: 'state';
  loadout: string;
  patch: Record<string, boolean | number | string | null>;
}

/** `bridge:session` — telemetry summary, every 60 s and on exit. */
export interface BridgeSession {
  t: 'session';
  fps_avg: number;
  played_ms: number;
  server?: string | null;
  loadout?: string;
}

/** `bridge:server` — presence, on connect and disconnect. */
export interface BridgeServer {
  t: 'server';
  host: string;
  connected: boolean;
  port?: number;
}
