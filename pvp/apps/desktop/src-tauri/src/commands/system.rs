//! `system_info` · `java_status` · `open_data_dir` · `open_path`

use std::path::{Path, PathBuf};

use void_core::java;

use crate::error::Error;
use crate::models::{JavaStatus, SystemInfo};
use crate::state::AppState;

pub fn system_info(state: &AppState) -> Result<SystemInfo, Error> {
    use sysinfo::System;

    let mut sys = System::new();
    sys.refresh_memory();
    sys.refresh_cpu_all();

    let ram_total_mb = sys.total_memory() / (1024 * 1024);
    let cpu = sys
        .cpus()
        .first()
        .map(|c| c.brand().trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| "unknown CPU".into());

    Ok(SystemInfo {
        os: System::name().unwrap_or_else(|| std::env::consts::OS.to_string()),
        os_version: System::os_version().unwrap_or_default(),
        arch: std::env::consts::ARCH.to_string(),
        cpu,
        cpu_cores: sys.cpus().len(),
        ram_total_mb,
        ram_available_mb: sys.available_memory() / (1024 * 1024),
        recommended_ram_mb: recommended_ram_mb(ram_total_mb),
        app_version: env!("CARGO_PKG_VERSION").to_string(),
        data_dir: state.paths.root().display().to_string(),
    })
}

/// What the RAM slider starts at.
///
/// 1.8.9 with a HUD mod is comfortable at 4 GB and gains nothing above 8 — past that
/// the extra heap only makes G1 pauses longer, which is the opposite of what a PVP
/// client wants. So: a quarter of the machine, clamped to [2 GB, 8 GB], and never more
/// than half of what is installed on a small machine.
pub fn recommended_ram_mb(total_mb: u64) -> u32 {
    let quarter = (total_mb / 4) as u32;
    let half = (total_mb / 2) as u32;
    quarter.clamp(2048, 8192).min(half.max(1024))
}

/// What the Settings screen says about Java.
///
/// `found` means "usable for 1.8.9", not merely "a JVM exists": Minecraft 1.8.9 will not
/// start on a modern runtime — LWJGL 2 and the Legacy Fabric loader both refuse — so a
/// Java 21 on PATH is a miss, and the screen says which version it found instead.
pub fn java_status(state: &AppState) -> Result<JavaStatus, Error> {
    let configured = state.config()?.java_path;

    let (install, source) = match &configured {
        Some(path) => (java::probe(path), "configured"),
        None => match java::detect_java8(&state.paths) {
            // `detect_java8` looks in the installation's own `java/` first, then the
            // host; the path tells the two apart for the label.
            Some(found) => {
                let bundled = found.path.starts_with(state.paths.java_dir());
                (Some(found), if bundled { "bundled" } else { "system" })
            }
            None => (None, "missing"),
        },
    };

    Ok(match install {
        Some(install) => JavaStatus {
            found: install.is_java8(),
            path: Some(install.path.display().to_string()),
            version: Some(install.version.clone()),
            major: Some(install.major),
            source: if install.is_java8() {
                source.to_string()
            } else {
                format!("{source}-wrong-version")
            },
        },
        None => JavaStatus {
            found: false,
            path: configured.map(|p| p.display().to_string()),
            version: None,
            major: None,
            source: "missing".into(),
        },
    })
}

/// The path "Open data folder" reveals. Opening it is the shell's job
/// (`tauri-plugin-opener`); resolving it is ours.
pub fn data_dir(state: &AppState) -> Result<String, Error> {
    Ok(state.paths.root().display().to_string())
}

/// What `open_path` may reveal. A closed set, so the webview can never ask the shell to
/// open an arbitrary path.
///
/// - `launcher_log` — the newest launcher log file (else the logs folder)
/// - `game_log` — the game's `logs/latest.log` (else the game directory)
/// - `crash_reports` — the game's `crash-reports/` folder
/// - `crash_report` — one crash report, named by `path`, which must be inside
///   `crash-reports/` or be an `hs_err_pid*.log` in the game directory
/// - `game_dir` — the game directory
pub fn open_target(state: &AppState, which: &str, path: Option<&str>) -> Result<PathBuf, Error> {
    let game = state.game_paths()?;
    let found = match which {
        "launcher_log" => crate::logging::latest_file(&state.paths.logs_dir())
            .unwrap_or_else(|| state.paths.logs_dir()),
        "game_log" => {
            let latest = game.latest_log();
            if latest.is_file() { latest } else { game.game_dir() }
        }
        "crash_reports" => {
            let dir = game.crash_reports_dir();
            if dir.is_dir() { dir } else { game.game_dir() }
        }
        "crash_report" => {
            let wanted = PathBuf::from(path.ok_or_else(|| Error::Other("No crash report named.".into()))?);
            let canon = wanted
                .canonicalize()
                .map_err(|e| Error::Other(format!("The crash report is gone: {e}")))?;
            let in_reports = game
                .crash_reports_dir()
                .canonicalize()
                .is_ok_and(|dir| canon.starts_with(dir));
            let is_hs_err = canon.parent().map(Path::to_path_buf)
                == game.game_dir().canonicalize().ok()
                && canon
                    .file_name()
                    .and_then(|n| n.to_str())
                    .is_some_and(|n| n.starts_with("hs_err_pid") && n.ends_with(".log"));
            if !(in_reports || is_hs_err) {
                return Err(Error::Other("That is not a crash report.".into()));
            }
            canon
        }
        "game_dir" => game.game_dir(),
        other => return Err(Error::Other(format!("Nothing to open called `{other}`."))),
    };
    if !found.exists() {
        std::fs::create_dir_all(&found).map_err(|e| Error::Storage(e.to_string()))?;
    }
    Ok(found)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::scratch_state;

    #[test]
    fn system_info_is_populated_and_names_the_data_dir() {
        let state = scratch_state();
        let info = system_info(&state).unwrap();
        assert!(!info.arch.is_empty());
        assert!(info.cpu_cores >= 1);
        assert!(info.ram_total_mb > 0);
        assert_eq!(info.app_version, env!("CARGO_PKG_VERSION"));
        assert_eq!(info.data_dir, state.paths.root().display().to_string());
    }

    #[test]
    fn the_ram_recommendation_stays_inside_the_useful_band() {
        assert_eq!(recommended_ram_mb(2048), 1024); // tiny machine: half of it
        assert_eq!(recommended_ram_mb(8192), 2048); // 8 GB: the floor
        assert_eq!(recommended_ram_mb(16384), 4096); // 16 GB: a quarter
        assert_eq!(recommended_ram_mb(65536), 8192); // 64 GB: the ceiling, not 16 GB
    }

    #[test]
    fn a_configured_path_that_is_not_a_jvm_reports_missing_rather_than_lying() {
        let state = scratch_state();
        crate::commands::settings::set(
            &state,
            crate::models::SettingsPatch {
                java_path: Some(Some("/definitely/not/java".into())),
                ..Default::default()
            },
        )
        .unwrap();
        let status = java_status(&state).unwrap();
        assert!(!status.found);
        assert_eq!(status.source, "missing");
        assert_eq!(status.path.as_deref(), Some("/definitely/not/java"));
    }

    #[test]
    fn open_path_reveals_only_the_named_places() {
        let state = scratch_state();
        let game = state.game_paths().unwrap();
        std::fs::create_dir_all(game.game_dir()).unwrap();

        // Fall back to the folder when the file is not there yet.
        assert_eq!(open_target(&state, "game_log", None).unwrap(), game.game_dir());
        assert!(open_target(&state, "launcher_log", None).unwrap().starts_with(state.paths.logs_dir()));
        assert!(open_target(&state, "nonsense", None).is_err());

        // A crash report must actually be one.
        let reports = game.crash_reports_dir();
        std::fs::create_dir_all(&reports).unwrap();
        let report = reports.join("crash-1-client.txt");
        std::fs::write(&report, "x").unwrap();
        let opened = open_target(&state, "crash_report", Some(&report.display().to_string())).unwrap();
        assert!(opened.ends_with("crash-1-client.txt"));
        let elsewhere = state.paths.config_file();
        std::fs::write(&elsewhere, "{}").unwrap();
        assert!(open_target(&state, "crash_report", Some(&elsewhere.display().to_string())).is_err());
    }

    #[test]
    fn java_status_on_a_bare_installation_answers_rather_than_panicking() {
        let state = scratch_state();
        let status = java_status(&state).unwrap();
        // On a runner with a JDK this is `system` or `system-wrong-version`; on one
        // without, `missing`. All three are answers, and `found` is only ever true for 8.
        assert!(!status.source.is_empty());
        if status.found {
            assert_eq!(status.major, Some(8));
        }
    }
}
