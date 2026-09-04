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
use crate::models::{Settings, SettingsPatch, EXTRA_HIDE_TO_TRAY, EXTRA_UPDATE_CHANNEL};
use crate::state::AppState;

/// Default for the launcher-only preference: hide to the tray after a successful spawn
/// (§5). On, because that is what the architecture describes; off is one click away for
/// anyone on two monitors.
const DEFAULT_HIDE_TO_TRAY: bool = true;
const DEFAULT_UPDATE_CHANNEL: &str = "stable";

pub fn get(state: &AppState) -> Result<Settings, Error> {
    let globals = state.store.settings()?;
    let config = state.config()?;
    Ok(join(&globals, &config, state.store.active_id()?.to_string()))
}

fn join(globals: &GlobalSettings, config: &Config, active_loadout: String) -> Settings {
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
        java_auto: config.java_path.is_none(),
        java_path: config.java_path.as_ref().map(|p| p.display().to_string()),
        ram_mb: config.max_memory_mb,
        mod_jar: config.mod_jar.as_ref().map(|p| p.display().to_string()),
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
        globals.extra.insert(EXTRA_UPDATE_CHANNEL.into(), channel.into());
        globals_changed = true;
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

    Ok(join(&globals, &config, state.store.active_id()?.to_string()))
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
    fn turning_java_auto_back_on_forgets_the_configured_path() {
        let state = scratch_state();
        set(&state, SettingsPatch { java_path: Some(Some("/x/java".into())), ..Default::default() })
            .unwrap();
        let s = set(&state, SettingsPatch { java_auto: Some(true), ..Default::default() }).unwrap();
        assert!(s.java_auto);
        assert_eq!(s.java_path, None);
    }
}
