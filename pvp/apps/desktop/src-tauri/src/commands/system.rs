//! `system_info` · `java_status` · `open_data_dir`

use crate::adapters::java;
use crate::error::Error;
use crate::models::{JavaStatus, SystemInfo};
use crate::state::AppState;

pub fn system_info(_state: &AppState) -> Result<SystemInfo, Error> {
    use sysinfo::System;

    let mut sys = System::new();
    sys.refresh_memory();
    sys.refresh_cpu_all();

    let ram_total_mb = sys.total_memory() / (1024 * 1024);
    let ram_available_mb = sys.available_memory() / (1024 * 1024);
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
        ram_available_mb,
        recommended_ram_mb: recommended_ram_mb(ram_total_mb),
        app_version: env!("CARGO_PKG_VERSION").to_string(),
    })
}

/// What the RAM slider starts at.
///
/// 1.8.9 with a HUD mod is comfortable at 4 GB and gains nothing above 8 — past that
/// the extra heap just makes G1 pauses longer, which is the opposite of what a PVP
/// client wants. So: a quarter of the machine, clamped to [2 GB, 8 GB], and never more
/// than half of what is installed on a small machine.
pub fn recommended_ram_mb(total_mb: u64) -> u32 {
    let quarter = (total_mb / 4) as u32;
    let half = (total_mb / 2) as u32;
    quarter.clamp(2048, 8192).min(half.max(1024))
}

pub fn java_status(state: &AppState) -> Result<JavaStatus, Error> {
    let settings = state.store.lock().unwrap().settings();
    Ok(java::detect(
        &state.data_dir,
        settings.java_path.as_deref(),
        settings.java_auto,
    ))
}

/// The path "Open data folder" in Settings reveals. Opening it is the shell's job
/// (`tauri-plugin-opener`); resolving it is ours.
pub fn data_dir(state: &AppState) -> Result<String, Error> {
    Ok(state.data_dir.display().to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::scratch_state;

    #[test]
    fn system_info_is_populated() {
        let state = scratch_state();
        let info = system_info(&state).unwrap();
        assert!(!info.arch.is_empty());
        assert!(info.cpu_cores >= 1);
        assert!(info.ram_total_mb > 0);
        assert_eq!(info.app_version, env!("CARGO_PKG_VERSION"));
    }

    #[test]
    fn the_ram_recommendation_stays_inside_the_useful_band() {
        assert_eq!(recommended_ram_mb(2048), 1024); // tiny machine: half of it
        assert_eq!(recommended_ram_mb(8192), 2048); // 8 GB: floor
        assert_eq!(recommended_ram_mb(16384), 4096); // 16 GB: a quarter
        assert_eq!(recommended_ram_mb(65536), 8192); // 64 GB: ceiling, not 16 GB
    }

    #[test]
    fn the_data_dir_is_the_one_the_store_was_opened_on() {
        let state = scratch_state();
        assert_eq!(data_dir(&state).unwrap(), state.data_dir.display().to_string());
    }
}
