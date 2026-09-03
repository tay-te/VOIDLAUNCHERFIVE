//! The DTOs that cross the `invoke` boundary.
//!
//! These mirror `pvp/schema/loadout.json` and the launcher-side half of
//! `pvp/schema/protocol.json#/definitions/global_settings`.
//!
//! TODO(integrate): `Loadout`, `LoadoutSummary`, `HudItem`, `LoadoutStats` and
//! `ModState` belong to `void-loadout` (CONTRACTS.md: `core` owns the loadout model).
//! They are declared here only because that crate is still a doc-comment stub. When it
//! exports them, delete this module's loadout half and `pub use void_loadout::…`
//! instead — the JSON shape is identical, so the TypeScript side does not move.

use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

// ---------------------------------------------------------------- loadouts

/// Per-mod state. `on` plus an open bag of settings.
///
/// Deliberately not a 12-variant enum: `mods.json` is the closed registry and the
/// launcher has no business re-declaring each mod's settings sub-schema. The UI reads
/// the registry and renders the controls; Rust only stores and forwards. Unknown keys
/// survive a round trip, which is what keeps a loadout written by a newer build
/// readable by an older one.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ModState {
    #[serde(default)]
    pub on: bool,
    #[serde(flatten, default)]
    pub settings: BTreeMap<String, serde_json::Value>,
}

/// Screen anchor a HUD item is pinned to (`loadout.json#/definitions/anchor`).
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub enum Anchor {
    TopLeft,
    Top,
    TopRight,
    Left,
    Center,
    Right,
    BottomLeft,
    Bottom,
    BottomRight,
}

/// Placement of one HUD mod: anchor + offset + scale, never absolute pixels (§8.1).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HudItem {
    pub id: String,
    pub anchor: Anchor,
    pub dx: f64,
    pub dy: f64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scale: Option<f64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct LoadoutStats {
    #[serde(default)]
    pub played_ms: u64,
    #[serde(default)]
    pub fps_avg: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Loadout {
    pub id: String,
    pub name: String,
    pub icon: String,
    #[serde(default)]
    pub server: Option<String>,
    #[serde(default = "default_mc")]
    pub mc: String,
    #[serde(default)]
    pub mods: BTreeMap<String, ModState>,
    #[serde(default)]
    pub hud: Vec<HudItem>,
    #[serde(default)]
    pub stats: LoadoutStats,
}

fn default_mc() -> String {
    "1.8.9".into()
}

/// The reduced form the tray submenu and the loadout picker list.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LoadoutSummary {
    pub id: String,
    pub name: String,
    pub icon: String,
    #[serde(default)]
    pub server: Option<String>,
    #[serde(default)]
    pub stats: LoadoutStats,
}

impl From<&Loadout> for LoadoutSummary {
    fn from(l: &Loadout) -> Self {
        LoadoutSummary {
            id: l.id.clone(),
            name: l.name.clone(),
            icon: l.icon.clone(),
            server: l.server.clone(),
            stats: l.stats.clone(),
        }
    }
}

/// The patch shape `loadouts_update` accepts. Every field optional: the UI sends only
/// what the player touched, which is what lets the Mods screen write one switch flip
/// without shipping the whole loadout back.
#[derive(Debug, Clone, Deserialize, Default)]
pub struct LoadoutPatch {
    pub name: Option<String>,
    pub icon: Option<String>,
    pub server: Option<Option<String>>,
    pub mods: Option<BTreeMap<String, ModState>>,
    pub hud: Option<Vec<HudItem>>,
}

// ---------------------------------------------------------------- settings

/// Global (non-loadout) settings — §8.3.
///
/// The first five fields are the ones `protocol.json#/definitions/global_settings`
/// forwards to the mod in `init`; the rest are launcher-only and never cross the WS.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    // --- forwarded to the game ---
    pub menu_key: String,
    pub cycle_loadout_key: String,
    pub theme: String,
    pub ui_scale: f64,
    pub hud_editor_grid: u32,
    // --- launcher only ---
    pub java_auto: bool,
    pub java_path: Option<String>,
    pub ram_mb: u32,
    pub hide_to_tray_on_launch: bool,
    pub update_channel: String,
    pub active_loadout: String,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            menu_key: "RSHIFT".into(),
            cycle_loadout_key: "L".into(),
            theme: "void-dark".into(),
            ui_scale: 1.0,
            hud_editor_grid: 4,
            java_auto: true,
            java_path: None,
            ram_mb: 4096,
            hide_to_tray_on_launch: true,
            update_channel: "stable".into(),
            active_loadout: "sword-pvp".into(),
        }
    }
}

/// Partial update from `settings_set`.
#[derive(Debug, Clone, Deserialize, Default)]
pub struct SettingsPatch {
    pub menu_key: Option<String>,
    pub cycle_loadout_key: Option<String>,
    pub theme: Option<String>,
    pub ui_scale: Option<f64>,
    pub hud_editor_grid: Option<u32>,
    pub java_auto: Option<bool>,
    pub java_path: Option<Option<String>>,
    pub ram_mb: Option<u32>,
    pub hide_to_tray_on_launch: Option<bool>,
    pub update_channel: Option<String>,
}

// ---------------------------------------------------------------- account

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum AccountKind {
    Microsoft,
    Offline,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Account {
    pub uuid: String,
    pub name: String,
    pub kind: AccountKind,
    /// Level shown next to the name in the dock. Cosmetic; not a Mojang concept.
    #[serde(default)]
    pub level: u32,
    #[serde(default)]
    pub skin_url: Option<String>,
}

/// The immediate return of `auth_login`: what to show the player *before* the device
/// flow completes. Completion arrives as `auth:status` events.
#[derive(Debug, Clone, Serialize)]
pub struct DeviceCode {
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in_s: u64,
    pub interval_s: u64,
}

/// Payload of the `auth:status` event.
#[derive(Debug, Clone, Serialize)]
#[serde(tag = "stage", rename_all = "snake_case")]
pub enum AuthStatus {
    Pending { message: String },
    Xbox { message: String },
    Minecraft { message: String },
    Complete { account: Account },
    Failed { message: String },
}

// ---------------------------------------------------------------- system

#[derive(Debug, Clone, Serialize)]
pub struct SystemInfo {
    pub os: String,
    pub os_version: String,
    pub arch: String,
    pub cpu: String,
    pub cpu_cores: usize,
    pub ram_total_mb: u64,
    pub ram_available_mb: u64,
    /// What we would pick for `-Xmx` if the player never touches the slider.
    pub recommended_ram_mb: u32,
    pub app_version: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct JavaStatus {
    pub found: bool,
    pub path: Option<String>,
    pub version: Option<String>,
    /// `bundled` | `system` | `configured` | `missing`
    pub source: String,
}

// ---------------------------------------------------------------- prepare / launch

/// Payload of `prepare:progress`.
#[derive(Debug, Clone, Serialize)]
pub struct PrepareProgress {
    /// `manifest` | `libraries` | `assets` | `fabric` | `java` | `mod` | `done`
    pub step: String,
    pub done: u64,
    pub total: u64,
    pub bytes_per_sec: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
}

/// Result of `prepare`.
#[derive(Debug, Clone, Serialize)]
pub struct PrepareReport {
    pub loadout: String,
    pub bytes_downloaded: u64,
    pub duration_ms: u64,
    pub java_path: String,
    pub from_cache: bool,
}

/// Result of `launch`.
#[derive(Debug, Clone, Serialize)]
pub struct LaunchReport {
    pub pid: u32,
    /// The localhost port `void-bridge` bound for this spawn (`-Dvoid.port`).
    pub bridge_port: u16,
    pub loadout: String,
}

/// Payload of `game:closed`, and the session summary the Play screen shows when the
/// window comes back from the tray.
#[derive(Debug, Clone, Serialize)]
pub struct SessionStats {
    pub code: i32,
    pub loadout: String,
    pub played_ms: u64,
    pub fps_avg: f64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub server: Option<String>,
    /// Present when the JVM exited non-zero: the tail of the log, for the error surface.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub crash_tail: Option<Vec<String>>,
}

/// Payload of `game:log`.
#[derive(Debug, Clone, Serialize)]
pub struct LogLine {
    pub stream: &'static str,
    pub line: String,
    pub ts_ms: u64,
}

// ---------------------------------------------------------------- servers

/// Result of `server_ping` — a real Minecraft SLP handshake, not an ICMP ping.
#[derive(Debug, Clone, Serialize)]
pub struct PingResult {
    pub host: String,
    pub port: u16,
    pub latency_ms: u32,
    pub online: u64,
    pub max: u64,
    pub version: String,
    pub motd: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub favicon: Option<String>,
}
