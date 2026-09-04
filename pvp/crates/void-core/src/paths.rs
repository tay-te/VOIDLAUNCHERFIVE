//! Where `void-core` keeps everything on disk.
//!
//! ```text
//! ~/.void-pvp/
//!   config.json            client id, Java path, RAM — the launcher's own settings
//!   credentials.json       refresh token, only when the OS keychain is unavailable
//!   profile.json           last signed-in profile, so `whoami` needs no network
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
//! Root is `$VOID_PVP_HOME` when set, else `~/.void-pvp` — the same root
//! [`void_loadout::Store`] uses, so one override moves the whole installation.

use std::path::{Path, PathBuf};

use crate::error::{Error, Result};

/// Resolved locations for one VOID installation.
#[derive(Debug, Clone)]
pub struct Paths {
    root: PathBuf,
}

impl Paths {
    /// Uses `$VOID_PVP_HOME`, else `~/.void-pvp`.
    pub fn new() -> Result<Self> {
        Ok(Self { root: void_loadout::Store::default_root()? })
    }

    /// Uses an explicit root.
    pub fn at(root: impl Into<PathBuf>) -> Self {
        Self { root: root.into() }
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

    /// Cached profile of the last signed-in account.
    pub fn profile_file(&self) -> PathBuf {
        self.root.join("profile.json")
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
        self.root.join("game")
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
