//! `loadouts_list` · `loadouts_get` · `loadouts_create` · `loadouts_update` ·
//! `loadouts_delete` · `loadouts_switch`

use crate::error::Error;
use crate::models::{Loadout, LoadoutPatch, LoadoutSummary};
use crate::state::AppState;

pub fn list(state: &AppState) -> Result<Vec<LoadoutSummary>, Error> {
    Ok(state.store.lock().unwrap().list())
}

pub fn get(state: &AppState, id: &str) -> Result<Loadout, Error> {
    state.store.lock().unwrap().get(id)
}

/// The loadout the Play screen's hero names.
pub fn active(state: &AppState) -> Result<Loadout, Error> {
    state.store.lock().unwrap().active()
}

pub fn create(state: &AppState, name: &str, icon: &str) -> Result<Loadout, Error> {
    state.store.lock().unwrap().create(name, icon)
}

/// Merge a patch into a loadout. The Mods screen calls this on every switch flip and
/// slider release, so it must be cheap and must not require the whole loadout back.
pub fn update(state: &AppState, id: &str, patch: LoadoutPatch) -> Result<Loadout, Error> {
    state.store.lock().unwrap().update(id, patch)
}

pub fn delete(state: &AppState, id: &str) -> Result<Vec<LoadoutSummary>, Error> {
    let mut store = state.store.lock().unwrap();
    store.delete(id)?;
    Ok(store.list())
}

/// Switch the active loadout. While the game is running this is also what the tray's
/// "Switch loadout ▸" submenu calls; the caller is responsible for pushing the
/// resulting `loadout` message down the bridge (§8.2).
pub fn switch(state: &AppState, id: &str) -> Result<Loadout, Error> {
    state.store.lock().unwrap().switch(id)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::ModState;
    use crate::state::scratch_state;
    use std::collections::BTreeMap;

    #[test]
    fn a_fresh_library_lists_the_two_seeded_loadouts() {
        let state = scratch_state();
        let summaries = list(&state).unwrap();
        assert_eq!(summaries.len(), 2);
        assert_eq!(summaries[0].id, "sword-pvp");
        assert_eq!(active(&state).unwrap().id, "sword-pvp");
    }

    #[test]
    fn a_new_loadout_omits_every_mod_so_registry_defaults_apply() {
        let state = scratch_state();
        let created = create(&state, "Crystal PvP", "sword").unwrap();
        assert_eq!(created.id, "crystal-pvp");
        assert!(
            created.mods.is_empty(),
            "omitted mods fall back to mods.json defaults — materialising them here \
             would silently freeze a new loadout at today's defaults"
        );
        assert_eq!(created.mc, "1.8.9");
    }

    #[test]
    fn creating_a_duplicate_is_refused() {
        let state = scratch_state();
        assert!(create(&state, "Sword PvP", "sword").is_err());
    }

    #[test]
    fn switching_moves_the_active_pointer() {
        let state = scratch_state();
        switch(&state, "bedwars").unwrap();
        assert_eq!(active(&state).unwrap().id, "bedwars");
        assert!(switch(&state, "nope").is_err());
    }

    #[test]
    fn a_single_mod_patch_leaves_the_rest_alone() {
        let state = scratch_state();
        let mut mods = BTreeMap::new();
        mods.insert("hitboxes".into(), ModState { on: true, settings: Default::default() });
        let updated = update(&state, "sword-pvp", LoadoutPatch { mods: Some(mods), ..Default::default() }).unwrap();
        assert!(updated.mods["hitboxes"].on);
        assert!(updated.mods["keystrokes"].on);
        assert_eq!(updated.hud.len(), 6);
    }

    #[test]
    fn deleting_returns_the_library_that_is_left() {
        let state = scratch_state();
        let rest = delete(&state, "bedwars").unwrap();
        assert_eq!(rest.len(), 1);
        assert!(get(&state, "bedwars").is_err());
    }
}
