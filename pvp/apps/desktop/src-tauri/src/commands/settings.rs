//! `settings_get` · `settings_set`

use crate::error::Error;
use crate::models::{Settings, SettingsPatch};
use crate::state::AppState;

pub fn get(state: &AppState) -> Result<Settings, Error> {
    Ok(state.store.lock().unwrap().settings())
}

pub fn set(state: &AppState, patch: SettingsPatch) -> Result<Settings, Error> {
    state.store.lock().unwrap().set_settings(patch)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::scratch_state;

    #[test]
    fn defaults_match_the_protocol_schema() {
        let state = scratch_state();
        let s = get(&state).unwrap();
        assert_eq!(s.menu_key, "RSHIFT");
        assert_eq!(s.cycle_loadout_key, "L");
        assert_eq!(s.theme, "void-dark");
        assert_eq!(s.ui_scale, 1.0);
        assert_eq!(s.hud_editor_grid, 4);
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
        // untouched
        assert_eq!(s.cycle_loadout_key, "L");
        assert!(s.hide_to_tray_on_launch);
    }

    #[test]
    fn settings_survive_a_restart() {
        let state = scratch_state();
        set(&state, SettingsPatch { ram_mb: Some(8192), ..Default::default() }).unwrap();
        let reopened = crate::state::AppState::new(state.data_dir.clone()).unwrap();
        assert_eq!(get(&reopened).unwrap().ram_mb, 8192);
    }
}
