//! `settings_get` · `settings_set`
//!
//! Three stores, one screen. See [`crate::models::Settings`] for why the split exists;
//! this file is where it is joined and taken apart again.
//!
//! Writing a global that the game cares about also pushes it to a running session, so
//! rebinding the menu key mid-game takes effect without a relaunch.

use void_core::Config;
use void_loadout::{GlobalSettings, Keybind};

use crate::error::Error;
use crate::models::{
    Settings, SettingsPatch, EXTRA_DISCORD_RPC, EXTRA_HIDE_TO_TRAY, EXTRA_UPDATE_CHANNEL,
};
use crate::state::AppState;

/// Default for the launcher-only preference: hide to the tray after a successful spawn
/// (§5). On, because that is what the architecture describes; off is one click away for
/// anyone on two monitors.
const DEFAULT_HIDE_TO_TRAY: bool = true;
const DEFAULT_UPDATE_CHANNEL: &str = "stable";
const DEFAULT_DISCORD_RPC: bool = true;

/// The smallest window `--width`/`--height` accept; below it 1.8.9's menus overlap.
const MIN_GAME_SIZE: (u32, u32) = (640, 480);

pub fn get(state: &AppState) -> Result<Settings, Error> {
    let globals = state.store.settings()?;
    let config = state.config()?;
    Ok(join(&globals, &config, &state.paths, state.store.active_id()?.to_string()))
}

/// The launcher's update channel (`stable` / `beta`), for the updater and the mod
/// release channel.
pub fn update_channel(state: &AppState) -> String {
    state
        .store
        .settings()
        .ok()
        .and_then(|g| g.extra.get(EXTRA_UPDATE_CHANNEL).and_then(|v| v.as_str()).map(str::to_string))
        .unwrap_or_else(|| DEFAULT_UPDATE_CHANNEL.to_string())
}

/// Whether Discord Rich Presence is on.
pub fn discord_rpc(globals: &GlobalSettings) -> bool {
    globals.extra.get(EXTRA_DISCORD_RPC).and_then(|v| v.as_bool()).unwrap_or(DEFAULT_DISCORD_RPC)
}

fn join(
    globals: &GlobalSettings,
    config: &Config,
    paths: &void_core::Paths,
    active_loadout: String,
) -> Settings {
    Settings {
        menu_key: globals.menu_key().as_str().to_string(),
        cycle_loadout_key: globals.cycle_loadout_key().as_str().to_string(),
        theme: globals.theme().to_string(),
        ui_scale: globals.ui_scale(),
        hud_editor_grid: globals.hud_editor_grid(),
        hide_to_tray_on_launch: globals
            .extra
            .get(EXTRA_HIDE_TO_TRAY)
            .and_then(|v| v.as_bool())
            .unwrap_or(DEFAULT_HIDE_TO_TRAY),
        update_channel: globals
            .extra
            .get(EXTRA_UPDATE_CHANNEL)
            .and_then(|v| v.as_str())
            .unwrap_or(DEFAULT_UPDATE_CHANNEL)
            .to_string(),
        discord_rpc: discord_rpc(globals),
        java_auto: config.java_path.is_none(),
        java_path: config.java_path.as_ref().map(|p| p.display().to_string()),
        ram_mb: config.max_memory_mb,
        jvm_args: void_core::join_args(&config.jvm_args),
        game_width: config.game_width,
        game_height: config.game_height,
        fullscreen: config.fullscreen,
        game_dir: config.game_dir.as_ref().map(|p| p.display().to_string()),
        game_dir_effective: config.paths_for(paths).game_dir().display().to_string(),
        mod_jar: config.mod_jar_override().map(|p| p.display().to_string()),
        active_loadout,
    }
}

pub fn set(state: &AppState, patch: SettingsPatch) -> Result<Settings, Error> {
    let mut globals = state.store.settings()?;
    let mut config = state.config()?;
    let mut globals_changed = false;
    let mut config_changed = false;

    // --- globals that cross to the game -------------------------------------------
    if let Some(key) = patch.menu_key {
        globals.menu_key = Some(parse_keybind(&key)?);
        globals_changed = true;
    }
    if let Some(key) = patch.cycle_loadout_key {
        globals.cycle_loadout_key = Some(parse_keybind(&key)?);
        globals_changed = true;
    }
    if let Some(theme) = patch.theme {
        globals.theme = Some(theme);
        globals_changed = true;
    }
    if let Some(scale) = patch.ui_scale {
        globals.ui_scale = Some(scale.clamp(0.5, 3.0));
        globals_changed = true;
    }
    if let Some(grid) = patch.hud_editor_grid {
        globals.hud_editor_grid = Some(grid.clamp(0, 64));
        globals_changed = true;
    }

    // --- launcher preferences, riding in `extra` ----------------------------------
    if let Some(hide) = patch.hide_to_tray_on_launch {
        globals.extra.insert(EXTRA_HIDE_TO_TRAY.into(), hide.into());
        globals_changed = true;
    }
    if let Some(channel) = patch.update_channel {
        let channel = if channel == "beta" { "beta" } else { "stable" };
        globals.extra.insert(EXTRA_UPDATE_CHANNEL.into(), channel.into());
        globals_changed = true;
    }
    if let Some(on) = patch.discord_rpc {
        globals.extra.insert(EXTRA_DISCORD_RPC.into(), on.into());
        globals_changed = true;
        state.presence.set_enabled(on);
    }

    // --- config.json, launcher only -----------------------------------------------
    if let Some(auto) = patch.java_auto {
        if auto {
            config.java_path = None;
            config_changed = true;
        }
    }
    if let Some(path) = patch.java_path {
        config.java_path = path.filter(|p| !p.trim().is_empty()).map(Into::into);
        config_changed = true;
    }
    if let Some(ram) = patch.ram_mb {
        config.max_memory_mb = ram.clamp(1024, 32768);
        config_changed = true;
    }
    if let Some(line) = patch.jvm_args {
        let args = void_core::split_args(&line);
        // -Xmx/-Xms belong to the Memory slider: a second -Xmx later on the command line
        // silently wins, and the slider would then be lying.
        if let Some(bad) = args.iter().find(|a| a.starts_with("-Xmx") || a.starts_with("-Xms")) {
            return Err(Error::Other(format!(
                "`{bad}` is set by the Memory slider; remove it from the JVM arguments."
            )));
        }
        if let Some(bad) = args.iter().find(|a| a.starts_with("-Dvoid.")) {
            return Err(Error::Other(format!("`{bad}` is reserved for the launcher.")));
        }
        config.jvm_args = args;
        config_changed = true;
    }
    if let Some(width) = patch.game_width {
        config.game_width = width.filter(|w| *w > 0).map(|w| w.clamp(MIN_GAME_SIZE.0, 16384));
        config_changed = true;
    }
    if let Some(height) = patch.game_height {
        config.game_height = height.filter(|h| *h > 0).map(|h| h.clamp(MIN_GAME_SIZE.1, 16384));
        config_changed = true;
    }
    if let Some(full) = patch.fullscreen {
        config.fullscreen = full;
        config_changed = true;
    }
    if let Some(dir) = patch.game_dir {
        let dir = dir.map(|d| d.trim().to_string()).filter(|d| !d.is_empty());
        if let Some(d) = &dir {
            let path = std::path::Path::new(d);
            if !path.is_absolute() {
                return Err(Error::Other("The game directory must be a full path.".into()));
            }
            std::fs::create_dir_all(path)
                .map_err(|e| Error::Other(format!("Could not use {d} as the game directory: {e}")))?;
        }
        config.game_dir = dir.map(Into::into);
        config_changed = true;
    }
    if let Some(jar) = patch.mod_jar {
        config.mod_jar = jar.filter(|p| !p.trim().is_empty()).map(Into::into);
        config_changed = true;
    }

    if globals_changed {
        state.store.save_settings(&globals)?;
        // A running game holds the old copy; §7's `settings` message is how it learns.
        state.game.lock().unwrap().push_settings(&globals);
    }
    if config_changed {
        config.save(&state.paths)?;
    }

    Ok(join(&globals, &config, &state.paths, state.store.active_id()?.to_string()))
}

fn parse_keybind(value: &str) -> Result<Keybind, Error> {
    Keybind::new(value).ok_or_else(|| {
        Error::Other(format!(
            "`{value}` is not a key Minecraft 1.8.9 knows. Press a key to rebind instead."
        ))
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::scratch_state;

    #[test]
    fn defaults_match_the_schema_and_the_core_config() {
        let state = scratch_state();
        let s = get(&state).unwrap();
        assert_eq!(s.menu_key, "RSHIFT");
        assert_eq!(s.cycle_loadout_key, "L");
        assert_eq!(s.theme, "void-dark");
        assert_eq!(s.ui_scale, 1.0);
        assert_eq!(s.hud_editor_grid, 4);
        assert_eq!(s.ram_mb, void_core::DEFAULT_MAX_MEMORY_MB);
        assert!(s.java_auto);
        assert!(s.hide_to_tray_on_launch);
        assert!(!s.active_loadout.is_empty());
    }

    #[test]
    fn a_patch_touches_only_what_it_names_and_clamps_the_rest() {
        let state = scratch_state();
        let s = set(
            &state,
            SettingsPatch {
                ram_mb: Some(999_999),
                ui_scale: Some(9.0),
                menu_key: Some("F6".into()),
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(s.ram_mb, 32768);
        assert_eq!(s.ui_scale, 3.0);
        assert_eq!(s.menu_key, "F6");
        assert_eq!(s.cycle_loadout_key, "L");
        assert!(s.hide_to_tray_on_launch);
    }

    #[test]
    fn a_key_the_game_does_not_know_is_refused() {
        let state = scratch_state();
        let err = set(&state, SettingsPatch { menu_key: Some("Ctrl+Q".into()), ..Default::default() })
            .unwrap_err()
            .to_string();
        assert!(err.contains("Ctrl+Q"), "{err}");
        // …and nothing was written.
        assert_eq!(get(&state).unwrap().menu_key, "RSHIFT");
    }

    #[test]
    fn each_half_lands_in_its_own_file_and_survives_a_restart() {
        let state = scratch_state();
        set(
            &state,
            SettingsPatch {
                ram_mb: Some(8192),
                theme: Some("void-dark".into()),
                hide_to_tray_on_launch: Some(false),
                java_auto: Some(false),
                java_path: Some(Some("/opt/java8/bin/java".into())),
                ..Default::default()
            },
        )
        .unwrap();

        let root = state.paths.root().to_path_buf();
        let config_text = std::fs::read_to_string(root.join("config.json")).unwrap();
        let settings_text = std::fs::read_to_string(root.join("settings.json")).unwrap();
        assert!(config_text.contains("8192"), "ram belongs to config.json");
        assert!(config_text.contains("/opt/java8/bin/java"));
        assert!(settings_text.contains(EXTRA_HIDE_TO_TRAY), "the preference rides in `extra`");
        assert!(!settings_text.contains("8192"), "ram must not cross to the mod");

        let reopened =
            crate::state::AppState::new(void_core::Paths::at(&root)).unwrap();
        let s = get(&reopened).unwrap();
        assert_eq!(s.ram_mb, 8192);
        assert!(!s.hide_to_tray_on_launch);
        assert!(!s.java_auto);
        assert_eq!(s.java_path.as_deref(), Some("/opt/java8/bin/java"));
    }

    #[test]
    fn jvm_arguments_round_trip_and_refuse_what_belongs_elsewhere() {
        let state = scratch_state();
        let s = set(
            &state,
            SettingsPatch { jvm_args: Some(r#"-XX:+UseG1GC -Dname="a b""#.into()), ..Default::default() },
        )
        .unwrap();
        assert_eq!(s.jvm_args, r#"-XX:+UseG1GC "-Dname=a b""#);
        assert_eq!(state.config().unwrap().jvm_args, ["-XX:+UseG1GC", "-Dname=a b"]);

        let err = set(&state, SettingsPatch { jvm_args: Some("-Xmx8G".into()), ..Default::default() })
            .unwrap_err()
            .to_string();
        assert!(err.contains("Memory"), "{err}");
        assert!(set(&state, SettingsPatch { jvm_args: Some("-Dvoid.port=1".into()), ..Default::default() }).is_err());
    }

    #[test]
    fn window_size_fullscreen_and_game_dir_persist() {
        let state = scratch_state();
        let dir = state.paths.root().join("elsewhere");
        let s = set(
            &state,
            SettingsPatch {
                game_width: Some(Some(100)),
                game_height: Some(Some(1080)),
                fullscreen: Some(true),
                game_dir: Some(Some(dir.display().to_string())),
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(s.game_width, Some(640), "clamped to the smallest usable window");
        assert_eq!(s.game_height, Some(1080));
        assert!(s.fullscreen);
        assert_eq!(s.game_dir_effective, dir.display().to_string());
        assert!(dir.is_dir(), "the directory is created when chosen");

        let s = set(&state, SettingsPatch { game_dir: Some(None), game_width: Some(None), ..Default::default() })
            .unwrap();
        assert_eq!(s.game_dir, None);
        assert_eq!(s.game_width, None);
        assert!(s.game_dir_effective.ends_with("game"));
        assert!(set(&state, SettingsPatch { game_dir: Some(Some("relative/dir".into())), ..Default::default() }).is_err());
    }

    #[test]
    fn discord_presence_defaults_on_and_the_channel_is_one_of_two() {
        let state = scratch_state();
        assert!(get(&state).unwrap().discord_rpc);
        let s = set(
            &state,
            SettingsPatch { discord_rpc: Some(false), update_channel: Some("nightly".into()), ..Default::default() },
        )
        .unwrap();
        assert!(!s.discord_rpc);
        assert_eq!(s.update_channel, "stable");
        assert_eq!(update_channel(&state), "stable");
    }

    #[test]
    fn turning_java_auto_back_on_forgets_the_configured_path() {
        let state = scratch_state();
        set(&state, SettingsPatch { java_path: Some(Some("/x/java".into())), ..Default::default() })
            .unwrap();
        let s = set(&state, SettingsPatch { java_auto: Some(true), ..Default::default() }).unwrap();
        assert!(s.java_auto);
        assert_eq!(s.java_path, None);
    }
}
