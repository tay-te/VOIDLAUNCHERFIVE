//! `~/.void-pvp/config.json` — the launcher's own settings, distinct from the global
//! settings in `void-loadout` (which the *mod* also sees) and from a loadout (which
//! changes how the game plays, §8.3).

use std::path::PathBuf;

use serde::{Deserialize, Serialize};

use crate::error::{Error, Result};
use crate::paths::Paths;

/// Environment variable holding the Azure application (client) id.
pub const CLIENT_ID_ENV: &str = "VOID_MS_CLIENT_ID";

/// Default heap for 1.8.9. Two gigabytes is what every PVP client ships with.
pub const DEFAULT_MAX_MEMORY_MB: u32 = 2048;

/// Launcher settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Config {
    /// Azure application (client) id used for the Microsoft device-code flow.
    ///
    /// Deliberately not compiled in: an Azure application is per-publisher, has to be
    /// registered with the device-code flow enabled and approved for the Minecraft API,
    /// and would be a credential in the repository. `$VOID_MS_CLIENT_ID` overrides this.
    pub ms_client_id: Option<String>,
    /// Path to a `java` executable to use instead of the detected or fetched one.
    pub java_path: Option<PathBuf>,
    /// Maximum JVM heap in megabytes.
    pub max_memory_mb: u32,
    /// Extra JVM arguments, appended after the ones VOID builds.
    pub jvm_args: Vec<String>,
    /// Path to the `void-client` mod JAR to copy into the mods directory.
    pub mod_jar: Option<PathBuf>,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            ms_client_id: None,
            java_path: None,
            max_memory_mb: DEFAULT_MAX_MEMORY_MB,
            jvm_args: Vec::new(),
            mod_jar: None,
        }
    }
}

impl Config {
    /// Reads `config.json`, returning defaults when it is not there.
    pub fn load(paths: &Paths) -> Result<Self> {
        let file = paths.config_file();
        if !file.exists() {
            return Ok(Self::default());
        }
        let text = std::fs::read_to_string(&file).map_err(|e| Error::io(&file, e))?;
        serde_json::from_str(&text).map_err(|e| Error::json(file.display().to_string(), e))
    }

    /// Writes `config.json`.
    pub fn save(&self, paths: &Paths) -> Result<()> {
        let file = paths.config_file();
        if let Some(dir) = file.parent() {
            std::fs::create_dir_all(dir).map_err(|e| Error::io(dir, e))?;
        }
        let text = serde_json::to_string_pretty(self)
            .map_err(|e| Error::json(file.display().to_string(), e))?;
        std::fs::write(&file, text).map_err(|e| Error::io(&file, e))
    }

    /// The Azure client id: `$VOID_MS_CLIENT_ID` first, then `config.json`.
    pub fn ms_client_id(&self, paths: &Paths) -> Result<String> {
        std::env::var(CLIENT_ID_ENV)
            .ok()
            .filter(|s| !s.trim().is_empty())
            .or_else(|| self.ms_client_id.clone().filter(|s| !s.trim().is_empty()))
            .ok_or_else(|| Error::MissingClientId(paths.config_file()))
    }
}
