//! `system_info` · `java_status` · `open_data_dir`

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
