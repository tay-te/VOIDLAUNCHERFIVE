//! The DTOs that cross the `invoke` boundary.
//!
//! Almost nothing is declared here. The loadout model, the mod registry and the global
//! settings belong to `void-loadout` (CONTRACTS.md gives `core` those directories), and
//! they are re-exported rather than re-described: `loadouts_get` returns
//! `void_loadout::Loadout` verbatim, so the JSON the web side parses *is*
//! `schema/loadout.json` with no launcher-side translation layer to drift.
//!
//! What is declared here is the handful of shapes that exist only in the launcher: the
//! account as the dock renders it, host machine facts, prepare/launch progress, and the
//! result of a server ping.

use serde::{Deserialize, Serialize};

pub use void_loadout::{
    Anchor, GlobalSettings, HudItem, Loadout, LoadoutId, LoadoutStats, LoadoutSummary, ModStates,
};

// ---------------------------------------------------------------- settings

/// The settings screen's view of the world.
///
/// Three stores stand behind it, and the split is deliberate (§8.3):
///
/// - `menu_key` … `hud_editor_grid` are `void_loadout::GlobalSettings` — persisted in
///   `settings.json` **and** sent to the mod in `init`, because they change how the
///   game behaves.
/// - `java_auto` … `game_dir` are `void_core::Config` — `config.json`, launcher-only,
///   never on the wire.
/// - `hide_to_tray_on_launch`, `update_channel` and `discord_rpc` are launcher
///   preferences with no home in either, so they ride in `GlobalSettings`'s open `extra`
///   map, which the schema explicitly allows (`additionalProperties: true`).
#[derive(Debug, Clone, Serialize)]
pub struct Settings {
    // GlobalSettings — crosses to the game
    pub menu_key: String,
    pub cycle_loadout_key: String,
    pub theme: String,
    pub ui_scale: f64,
    pub hud_editor_grid: i64,
    // GlobalSettings.extra — launcher preferences
    pub hide_to_tray_on_launch: bool,
    pub update_channel: String,
    /// Show what you are playing in Discord. Default on.
    pub discord_rpc: bool,
    // void_core::Config — launcher only
    pub java_auto: bool,
    pub java_path: Option<String>,
    pub ram_mb: u32,
    /// Extra JVM arguments, as one line (`void_core::join_args`).
    pub jvm_args: String,
    /// Game window size; both or neither.
    pub game_width: Option<u32>,
    pub game_height: Option<u32>,
    pub fullscreen: bool,
    /// The configured game directory, or `None` for the default.
    pub game_dir: Option<String>,
    /// Where the game directory is right now (the default when `game_dir` is unset).
    pub game_dir_effective: String,
    /// The **developer override** JAR (`$VOID_MOD_JAR` / `config.mod_jar`), read-only
    /// here: shown so a dev build says it is not running the release client. `None` in
    /// every normal install, which uses the signed release channel.
    pub mod_jar: Option<String>,
    // derived
    pub active_loadout: String,
}

/// Keys the launcher stores inside `GlobalSettings::extra`.
pub const EXTRA_HIDE_TO_TRAY: &str = "hide_to_tray_on_launch";
pub const EXTRA_UPDATE_CHANNEL: &str = "update_channel";
pub const EXTRA_DISCORD_RPC: &str = "discord_rpc";

/// A partial update. Every field optional: the settings screen writes one row at a time.
#[derive(Debug, Clone, Deserialize, Default)]
pub struct SettingsPatch {
    pub menu_key: Option<String>,
    pub cycle_loadout_key: Option<String>,
    pub theme: Option<String>,
    pub ui_scale: Option<f64>,
    pub hud_editor_grid: Option<i64>,
    pub hide_to_tray_on_launch: Option<bool>,
    pub update_channel: Option<String>,
    pub discord_rpc: Option<bool>,
    pub java_auto: Option<bool>,
    /// `Some(None)` clears the configured path; `None` leaves it alone.
    pub java_path: Option<Option<String>>,
    pub ram_mb: Option<u32>,
    /// One line, split like a shell would (`void_core::split_args`).
    pub jvm_args: Option<String>,
    /// `Some(None)` clears it (the game's default size).
    pub game_width: Option<Option<u32>>,
    pub game_height: Option<Option<u32>>,
    pub fullscreen: Option<bool>,
    /// `Some(None)` returns to the default game directory.
    pub game_dir: Option<Option<String>>,
    /// Developer override; not offered in the Settings screen.
    pub mod_jar: Option<Option<String>>,
}

/// What `loadouts_update` accepts — only what the player touched.
///
/// `mods` is an untyped object rather than `ModStates` on purpose: the Mods screen
/// sends one mod at a time, and `ModStates` would deserialize the eleven absent keys as
/// "cleared". The command merges it key by key and lets `ModStates::set` validate each
/// against that mod's settings sub-schema.
#[derive(Debug, Clone, Deserialize, Default)]
pub struct LoadoutPatch {
    pub name: Option<String>,
    pub icon: Option<String>,
    pub server: Option<Option<String>>,
    pub mods: Option<serde_json::Map<String, serde_json::Value>>,
    pub hud: Option<Vec<HudItem>>,
}

/// What `loadouts_import` returns: the loadout as stored, under its fresh id, and how
/// many settings, mods, HUD items and pins the code carried that this build could not use.
#[derive(Debug, Clone, Serialize)]
pub struct ImportedLoadout {
    pub loadout: Loadout,
    pub dropped: usize,
}

// ---------------------------------------------------------------- account

/// The account as the dock and the settings screen render it.
///
/// `void_core::auth::Session` carries a live access token; this does not. Nothing that
/// crosses to the webview should be able to leak a Minecraft token into a devtools
/// console or a screenshot.
#[derive(Debug, Clone, Serialize)]
pub struct Account {
    pub uuid: String,
    pub name: String,
    /// `microsoft` or `offline`.
    pub kind: &'static str,
    pub skin_url: Option<String>,
}

impl From<&void_core::auth::Session> for Account {
    fn from(s: &void_core::auth::Session) -> Self {
        Account {
            uuid: s.uuid.clone(),
            name: s.username.clone(),
            kind: if s.is_offline() { "offline" } else { "microsoft" },
            skin_url: None,
        }
    }
}

impl From<&void_core::auth::StoredAccount> for Account {
    fn from(s: &void_core::auth::StoredAccount) -> Self {
        Account {
            uuid: s.uuid.clone(),
            name: s.username.clone(),
            kind: if s.is_offline() { "offline" } else { "microsoft" },
            skin_url: None,
        }
    }
}

/// What `auth_accounts` / `auth_remove` return: every account and the active one.
#[derive(Debug, Clone, Serialize)]
pub struct AccountList {
    pub active: Option<String>,
    pub accounts: Vec<Account>,
}

impl AccountList {
    pub fn active_account(&self) -> Option<Account> {
        let uuid = self.active.as_deref()?;
        self.accounts.iter().find(|a| a.uuid == uuid).cloned()
    }
}

impl From<&void_core::auth::Accounts> for AccountList {
    fn from(a: &void_core::auth::Accounts) -> Self {
        AccountList {
            active: a.active.clone(),
            accounts: a.accounts.iter().map(Account::from).collect(),
        }
    }
}

/// The immediate return of `auth_login`: what to show the player *before* the device
/// flow completes. Completion arrives as `auth:status` events.
#[derive(Debug, Clone, Serialize)]
pub struct DeviceCode {
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in_s: u64,
    pub interval_s: u64,
    /// Microsoft's own instruction sentence, when it sent one.
    pub message: Option<String>,
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
    /// What the RAM slider starts at when the player has never moved it.
    pub recommended_ram_mb: u32,
    pub app_version: String,
    /// `~/.void-pvp`, or `$VOID_PVP_HOME`.
    pub data_dir: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct JavaStatus {
    /// True only for a Java **8** runtime: 1.8.9 will not start on anything newer.
    pub found: bool,
    pub path: Option<String>,
    pub version: Option<String>,
    pub major: Option<u32>,
    /// `configured` | `bundled` | `system` | `missing`
    pub source: String,
}

// ---------------------------------------------------------- prepare / launch

/// Payload of `prepare:progress`.
#[derive(Debug, Clone, Serialize)]
pub struct PrepareProgress {
    /// `manifest` | `libraries` | `assets` | `java` | `mod` | `done`
    pub step: String,
    pub done: u64,
    pub total: u64,
    pub bytes_per_sec: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct PrepareReport {
    pub loadout: String,
    pub version_id: String,
    pub files: u64,
    pub downloaded_bytes: u64,
    pub duration_ms: u64,
    pub java_path: String,
    pub java_version: String,
    /// The `void-client` version that will be installed, or `dev` for an override.
    pub mod_version: String,
    /// `fresh` | `cached` | `dev`.
    pub mod_source: &'static str,
    /// A newer client exists that this launcher cannot run yet, and similar notes.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub notice: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct LaunchReport {
    pub pid: u32,
    /// The port `void-bridge` bound for this spawn (`-Dvoid.port`).
    pub bridge_port: u16,
    pub loadout: String,
}

/// Payload of `game:closed`, and the summary the Play screen shows when the window
/// comes back from the tray.
#[derive(Debug, Clone, Serialize)]
pub struct SessionStats {
    pub code: i32,
    pub loadout: String,
    pub played_ms: u64,
    pub fps_avg: f64,
    /// Mean ping over the session, when the mod measured one on a server.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ping_avg: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub server: Option<String>,
    /// The last lines of output when the JVM exited non-zero.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub crash_tail: Option<Vec<String>>,
    /// The crash report (or JVM `hs_err` file) this session wrote, when it wrote one.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub crash_report: Option<String>,
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

// ------------------------------------------------------------------ online

/// `cosmetics_state` / `online:cosmetics`: what the player owns and wears, from the backend.
/// `online: false` (and `equipped: null`) when there is no online session; the window then
/// shows its offline copy.
#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct CosmeticsState {
    pub online: bool,
    pub owned: Vec<String>,
    pub equipped: Option<void_online::EquippedSet>,
}

impl CosmeticsState {
    pub fn offline() -> Self {
        CosmeticsState { online: false, owned: Vec::new(), equipped: None }
    }
}

impl From<void_online::MyCosmetics> for CosmeticsState {
    fn from(c: void_online::MyCosmetics) -> Self {
        CosmeticsState { online: true, owned: c.owned, equipped: Some(c.equipped) }
    }
}
