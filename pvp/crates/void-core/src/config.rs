//! `~/.void-pvp/config.json` — the launcher's own settings, distinct from the global
//! settings in `void-loadout` (which the *mod* also sees) and from a loadout (which
//! changes how the game plays, §8.3).

use std::path::PathBuf;

use serde::{Deserialize, Serialize};

use crate::error::{Error, Result};
use crate::paths::Paths;

/// Environment variable holding the Azure application (client) id.
///
/// Read twice: at **compile time** (`option_env!`), so a release build can carry the
/// publisher's id without it ever being in the repository, and at run time, so a
/// developer can point a build at a different Azure app without rebuilding.
pub const CLIENT_ID_ENV: &str = "VOID_MS_CLIENT_ID";

/// The Azure client id baked in at build time, if the build was given one.
///
/// `VOID_MS_CLIENT_ID=<id> pnpm tauri build` (or the release workflow's secret of the same
/// name) sets it. An Azure application id is not a secret in the cryptographic sense —
/// every installed copy carries it — but it is per-publisher, so it is injected rather
/// than committed.
pub const BUILT_IN_CLIENT_ID: Option<&str> = option_env!("VOID_MS_CLIENT_ID");

/// Environment variable naming a local `void-client` JAR to install instead of the
/// release channel's — the developer override. Wins over `config.mod_jar`.
pub const MOD_JAR_ENV: &str = "VOID_MOD_JAR";

/// Default heap for 1.8.9. Two gigabytes is what every PVP client ships with.
pub const DEFAULT_MAX_MEMORY_MB: u32 = 2048;

/// Launcher settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Config {
    /// Azure application (client) id used for the Microsoft device-code flow.
    ///
    /// Overrides the id the build was compiled with ([`BUILT_IN_CLIENT_ID`]);
    /// `$VOID_MS_CLIENT_ID` at run time overrides both. See [`Config::ms_client_id`].
    pub ms_client_id: Option<String>,
    /// Path to a `java` executable to use instead of the detected or fetched one.
    pub java_path: Option<PathBuf>,
    /// Maximum JVM heap in megabytes.
    pub max_memory_mb: u32,
    /// Extra JVM arguments, appended after the ones VOID builds.
    pub jvm_args: Vec<String>,
    /// **Developer override**: a local `void-client` JAR to install instead of the one
    /// the signed release channel names. Not shown in the launcher's Settings; set by
    /// `scripts/launcher.ps1 -Build` or by hand. `$VOID_MOD_JAR` wins over it.
    pub mod_jar: Option<PathBuf>,
    /// Game window width in pixels (`--width`), or the game's default when unset.
    pub game_width: Option<u32>,
    /// Game window height in pixels (`--height`), or the game's default when unset.
    pub game_height: Option<u32>,
    /// Start the game fullscreen (`--fullscreen`).
    pub fullscreen: bool,
    /// Where the game directory lives (saves, `options.txt`, `mods/`, `logs/`), when
    /// not the default `<data dir>/game`.
    pub game_dir: Option<PathBuf>,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            ms_client_id: None,
            java_path: None,
            max_memory_mb: DEFAULT_MAX_MEMORY_MB,
            jvm_args: Vec::new(),
            mod_jar: None,
            game_width: None,
            game_height: None,
            fullscreen: false,
            game_dir: None,
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

    /// The Azure client id, most specific first: `$VOID_MS_CLIENT_ID` at run time, then
    /// `config.json`, then the id compiled into this build ([`BUILT_IN_CLIENT_ID`]).
    pub fn ms_client_id(&self, paths: &Paths) -> Result<String> {
        std::env::var(CLIENT_ID_ENV)
            .ok()
            .filter(|s| !s.trim().is_empty())
            .or_else(|| self.ms_client_id.clone().filter(|s| !s.trim().is_empty()))
            .or_else(|| BUILT_IN_CLIENT_ID.map(str::to_string).filter(|s| !s.trim().is_empty()))
            .map(|s| s.trim().to_string())
            .ok_or_else(|| Error::MissingClientId(paths.config_file()))
    }

    /// The developer's local mod JAR, if one is set: `$VOID_MOD_JAR`, then
    /// `config.mod_jar`. `None` means "use the signed release channel".
    pub fn mod_jar_override(&self) -> Option<PathBuf> {
        std::env::var_os(MOD_JAR_ENV)
            .map(PathBuf::from)
            .filter(|p| !p.as_os_str().is_empty())
            .or_else(|| self.mod_jar.clone().filter(|p| !p.as_os_str().is_empty()))
    }

    /// The game arguments for the window: `--width`/`--height` and `--fullscreen`.
    pub fn window_args(&self) -> Vec<String> {
        let mut out = Vec::new();
        if let (Some(w), Some(h)) = (self.game_width, self.game_height) {
            if w > 0 && h > 0 {
                out.extend(["--width".into(), w.to_string(), "--height".into(), h.to_string()]);
            }
        }
        if self.fullscreen {
            out.push("--fullscreen".into());
        }
        out
    }

    /// The installation's paths with this config's game directory applied.
    pub fn paths_for(&self, paths: &Paths) -> Paths {
        paths.clone().with_game_dir(self.game_dir.clone())
    }
}

/// Splits a JVM-arguments line the way a shell would for the common cases: whitespace
/// separates, and single or double quotes keep a value with spaces together
/// (`-Dname="a b"` becomes `-Dname=a b`). No escapes, no variables — this is a settings
/// field, not a shell.
pub fn split_args(line: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut current = String::new();
    let mut quote: Option<char> = None;
    let mut started = false;
    for c in line.chars() {
        match quote {
            Some(q) if c == q => quote = None,
            Some(_) => current.push(c),
            None if c == '"' || c == '\'' => {
                quote = Some(c);
                started = true;
            }
            None if c.is_whitespace() => {
                if started {
                    out.push(std::mem::take(&mut current));
                    started = false;
                }
            }
            None => {
                current.push(c);
                started = true;
            }
        }
    }
    if started {
        out.push(current);
    }
    out
}

/// The inverse of [`split_args`], for showing a stored list back in one field.
pub fn join_args(args: &[String]) -> String {
    args.iter()
        .map(|a| if a.chars().any(char::is_whitespace) { format!("\"{a}\"") } else { a.clone() })
        .collect::<Vec<_>>()
        .join(" ")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn jvm_argument_lines_split_like_a_shell_for_the_common_cases() {
        assert_eq!(split_args("  -XX:+UseG1GC   -Dx=1 "), ["-XX:+UseG1GC", "-Dx=1"]);
        assert_eq!(split_args(r#"-Dname="a b" -Dq='c d'"#), ["-Dname=a b", "-Dq=c d"]);
        assert!(split_args("   ").is_empty());
        let args = vec!["-Dname=a b".to_string(), "-Xss2m".to_string()];
        assert_eq!(split_args(&join_args(&args)), args);
    }

    #[test]
    fn window_arguments_need_both_dimensions() {
        let mut c = Config { game_width: Some(1280), ..Config::default() };
        assert!(c.window_args().is_empty(), "a width alone is ignored");
        c.game_height = Some(720);
        assert_eq!(c.window_args(), ["--width", "1280", "--height", "720"]);
        c.fullscreen = true;
        assert_eq!(c.window_args().last().map(String::as_str), Some("--fullscreen"));
    }

    #[test]
    fn an_old_config_file_still_loads_with_the_new_fields_defaulted() {
        let c: Config = serde_json::from_str(r#"{"max_memory_mb": 4096}"#).unwrap();
        assert_eq!(c.max_memory_mb, 4096);
        assert!(!c.fullscreen);
        assert_eq!(c.game_dir, None);
    }
}
