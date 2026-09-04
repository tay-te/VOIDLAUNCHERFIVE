/**
 * Fixtures for the browser preview and the store tests.
 *
 * Where the Figma quotes a value verbatim ("Searge", "Lvl 42", "Sword PvP",
 * "24,118 online", "42 ms") this file uses that value, so `pnpm dev:web` is directly
 * comparable against `design/screens/*.png`.
 */

import type { Account, Loadout, Settings, SystemInfo } from '../local/protocol';

export const MOCK_ACCOUNT: Account = {
  // The vanilla offline-mode UUID for this name, hyphen-free, exactly as
  // `void_core::auth::offline_uuid` produces it.
  uuid: '3bce967243f03d12a71c7fcdc529a9b1',
  name: 'Searge',
  kind: 'offline',
  skin_url: null,
};

export const SWORD_PVP: Loadout = {
  id: 'sword-pvp',
  name: 'Sword PvP',
  icon: 'sword',
  server: 'hypixel',
  mc: '1.8.9',
  mods: {
    fps: { on: true, scale: 1, opacity: 1, color: '#FFFFFF', show_label: true },
    keystrokes: {
      on: true,
      scale: 1,
      opacity: 0.85,
      keybind: 'NONE',
      show_mouse: true,
      show_spacebar: true,
      show_cps: true,
    },
    cps: { on: true, mode: 'left', window_ms: 1000 },
    ping: { on: false, good_ms: 60, bad_ms: 150 },
    coordinates: { on: false },
    armor_status: { on: true, orientation: 'horizontal', show_durability: true, show_held_item: true },
    potion_effects: { on: true },
    toggle_sprint: { on: true, mode: 'toggle', show_status: true },
    fullbright: { on: false, gamma: 10 },
    hitboxes: { on: false },
    zoom: { on: true, key: 'C', fov_divisor: 4, smooth: true },
    crosshair: { on: true, style: 'cross', size: 5, gap: 2, color: '#FFFFFFFF', outline: true },
  },
  hud: [
    { id: 'keystrokes', anchor: 'bottom-left', dx: 32, dy: -40, scale: 1 },
    { id: 'cps', anchor: 'bottom-left', dx: 32, dy: -8, scale: 1 },
    { id: 'fps', anchor: 'top-left', dx: 20, dy: 20 },
    { id: 'ping', anchor: 'top-left', dx: 20, dy: 38 },
    { id: 'armor_status', anchor: 'right', dx: -20, dy: 0 },
    { id: 'potion_effects', anchor: 'top-right', dx: -20, dy: 20 },
  ],
  stats: { played_ms: 15_600_000, fps_avg: 142 },
};

export const BEDWARS: Loadout = {
  id: 'bedwars',
  name: 'Bedwars',
  icon: 'bed',
  server: null,
  mc: '1.8.9',
  mods: {
    keystrokes: { on: true },
    cps: { on: true, mode: 'both' },
    toggle_sprint: { on: true },
    zoom: { on: true, key: 'V' },
  },
  hud: [
    { id: 'keystrokes', anchor: 'bottom-left', dx: 24, dy: -24 },
    { id: 'cps', anchor: 'bottom', dx: 0, dy: -60, scale: 0.75 },
  ],
  stats: { played_ms: 4_200_000, fps_avg: 138 },
};

export const MOCK_SETTINGS: Settings = {
  menu_key: 'RSHIFT',
  cycle_loadout_key: 'L',
  theme: 'void-dark',
  ui_scale: 1,
  hud_editor_grid: 4,
  hide_to_tray_on_launch: true,
  update_channel: 'stable',
  java_auto: true,
  java_path: null,
  // `void_core::DEFAULT_MAX_MEMORY_MB` — two gigabytes is what every PVP client ships.
  ram_mb: 2048,
  mod_jar: null,
  active_loadout: 'sword-pvp',
};

export const MOCK_SYSTEM: SystemInfo = {
  os: 'macOS',
  os_version: '15.1',
  arch: 'aarch64',
  cpu: 'Apple M2 Pro',
  cpu_cores: 12,
  ram_total_mb: 32_768,
  ram_available_mb: 18_400,
  recommended_ram_mb: 8192,
  app_version: '0.1.0',
  data_dir: '~/.void-pvp',
};

/** The Servers screen's favourites, with the pings the Figma shows. */
export const MOCK_SERVERS = [
  { host: 'mc.hypixel.net', name: 'Hypixel', online: 24_118, latency_ms: 42 },
  { host: 'na.minemen.club', name: 'Minemen Club NA', online: 1_204, latency_ms: 68 },
  { host: 'play.cubecraft.net', name: 'CubeCraft', online: 8_412, latency_ms: 51 },
  { host: 'eu.minemen.club', name: 'Minemen Club EU', online: 987, latency_ms: 112 },
  { host: 'pvp.land', name: 'PvP Land', online: 612, latency_ms: 74 },
] as const;

/** The log lines the drawer shows in the browser preview. */
export const MOCK_LOG_SCRIPT: readonly string[] = [
  '[Client thread/INFO]: Setting user: Searge',
  '[Client thread/INFO]: LWJGL Version: 2.9.4',
  '[void-client/INFO]: connected to launcher bridge',
  '[void-client/INFO]: applied loadout sword-pvp',
  '[Client thread/INFO]: Connecting to mc.hypixel.net, 25565',
  '[void-client/INFO]: session summary sent',
];
