//! Where `void-core` keeps everything on disk.
//!
//! ```text
//! ~/.void-pvp/
//!   config.json            client id, Java path, RAM — the launcher's own settings
//!   accounts.json          every signed-in account (no tokens) and which one is active
//!   credentials.json       refresh tokens, only when the OS keychain is unavailable
//!   profile.json           pre-multi-account profile cache, migrated into accounts.json
//!   release/<channel>/     the last verified void-client release manifest, for offline
//!   mods-cache/<sha256>/   void-client JARs by content hash, installed into game/mods/
//!   natives-cache/         Ultralight natives the mod extracted, keyed by content hash
//!   logs/                  the launcher's own rolling log (launcher.log.YYYY-MM-DD)
//!   loadouts/ active.json settings.json     (owned by void-loadout)
//!   versions/<id>/<id>.json   version manifests, vanilla and merged
//!   libraries/…            Maven layout, exactly as Mojang and Legacy Fabric publish it
//!   assets/indexes,objects Mojang asset layout
//!   natives/<id>/          LWJGL 2 natives extracted for this version
//!   java/<name>/           Adoptium runtimes we fetched
//!   cache/objects/ab/<sha1>  hash-addressed download cache
//!   cache/args/<hash>.json   cached JVM argument lists
//!   game/                  the game directory: saves, options.txt, mods/, logs
//! ```
//!
//! The game directory can be moved (Settings → Game directory, `config.game_dir`); every
//! other path stays under the root, so changing it never orphans a download.
//!
//! Root is `$VOID_PVP_HOME` when set, else `~/.void-pvp` — the same root
//! [`void_loadout::Store`] uses, so one override moves the whole installation.

use std::path::{Path, PathBuf};

use crate::error::{Error, Result};

/// Resolved locations for one VOID installation.
#[derive(Debug, Clone)]
pub struct Paths {
    root: PathBuf,
    game_dir: Option<PathBuf>,
}

impl Paths {
    /// Uses `$VOID_PVP_HOME`, else `~/.void-pvp`.
    pub fn new() -> Result<Self> {
        Ok(Self { root: void_loadout::Store::default_root()?, game_dir: None })
    }

    /// Uses an explicit root.
    pub fn at(root: impl Into<PathBuf>) -> Self {
        Self { root: root.into(), game_dir: None }
    }

    /// The same installation with the game directory moved somewhere else.
    ///
    /// `None` keeps the default `<root>/game`. Only the game directory moves — saves,
    /// `options.txt`, `mods/`, `logs/`, `crash-reports/`; libraries, assets and Java stay
    /// shared under the root.
    pub fn with_game_dir(mut self, game_dir: Option<PathBuf>) -> Self {
        self.game_dir = game_dir.filter(|p| !p.as_os_str().is_empty());
        self
    }

    /// The installation root.
    pub fn root(&self) -> &Path {
        &self.root
    }

    /// The launcher's own settings file.
    pub fn config_file(&self) -> PathBuf {
        self.root.join("config.json")
    }

    /// Fallback credential file, used only when the OS keychain is unavailable.
    pub fn credentials_file(&self) -> PathBuf {
        self.root.join("credentials.json")
    }

    /// Cached profile of the last signed-in account (pre-multi-account; migrated).
    pub fn profile_file(&self) -> PathBuf {
        self.root.join("profile.json")
    }

    /// Every account the launcher knows, and which one is active. Never holds a token.
    pub fn accounts_file(&self) -> PathBuf {
        self.root.join("accounts.json")
    }

    /// Where the last verified release manifest for a channel is kept.
    pub fn release_dir(&self, channel: &str) -> PathBuf {
        self.root.join("release").join(channel)
    }

    /// Content-addressed `void-client` JARs: `mods-cache/<sha256>/<name>.jar`.
    pub fn mod_cache_dir(&self) -> PathBuf {
        self.root.join("mods-cache")
    }

    /// Where the mod extracts Ultralight's natives (passed as `-Dvoid.nativesCache`).
    pub fn ultralight_cache_dir(&self) -> PathBuf {
        self.root.join("natives-cache")
    }

    /// The launcher's own log files.
    pub fn logs_dir(&self) -> PathBuf {
        self.root.join("logs")
    }

    /// Version manifests, one directory per version id.
    pub fn version_dir(&self, id: &str) -> PathBuf {
        self.root.join("versions").join(id)
    }

    /// The client jar for a version.
    pub fn client_jar(&self, id: &str) -> PathBuf {
        self.version_dir(id).join(format!("{id}.jar"))
    }

    /// Maven-layout library root.
    pub fn libraries_dir(&self) -> PathBuf {
        self.root.join("libraries")
    }

    /// Asset root, holding `indexes/` and `objects/`.
    pub fn assets_dir(&self) -> PathBuf {
        self.root.join("assets")
    }

    /// Where LWJGL 2 natives are extracted for a version.
    pub fn natives_dir(&self, id: &str) -> PathBuf {
        self.root.join("natives").join(id)
    }

    /// Where fetched Java runtimes live.
    pub fn java_dir(&self) -> PathBuf {
        self.root.join("java")
    }

    /// Hash-addressed download cache.
    pub fn cache_objects_dir(&self) -> PathBuf {
        self.root.join("cache").join("objects")
    }

    /// Cached JVM argument lists, keyed by profile hash.
    pub fn args_cache_dir(&self) -> PathBuf {
        self.root.join("cache").join("args")
    }

    /// The game directory: what Minecraft sees as `.minecraft`.
    pub fn game_dir(&self) -> PathBuf {
        self.game_dir.clone().unwrap_or_else(|| self.root.join("game"))
    }

    /// The game's own `logs/latest.log`.
    pub fn latest_log(&self) -> PathBuf {
        self.game_dir().join("logs").join("latest.log")
    }

    /// The game's `crash-reports/` directory.
    pub fn crash_reports_dir(&self) -> PathBuf {
        self.game_dir().join("crash-reports")
    }

    /// Where the `void-client` mod JAR goes.
    pub fn mods_dir(&self) -> PathBuf {
        self.game_dir().join("mods")
    }

    /// Creates the directories a launch needs.
    pub fn ensure(&self) -> Result<()> {
        for dir in [
            self.root.clone(),
            self.libraries_dir(),
            self.assets_dir().join("indexes"),
            self.assets_dir().join("objects"),
            self.cache_objects_dir(),
            self.args_cache_dir(),
            self.java_dir(),
            self.mods_dir(),
        ] {
            std::fs::create_dir_all(&dir).map_err(|e| Error::io(&dir, e))?;
        }
        Ok(())
    }
}
