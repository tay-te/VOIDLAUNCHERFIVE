//! `loadouts_list` · `loadouts_get` · `loadouts_active` · `loadouts_create` ·
//! `loadouts_update` · `loadouts_delete` · `loadouts_switch`
//!
//! Thin over `void_loadout::Store`. The one place that is more than a forward is
//! [`update`], because the Mods screen sends a single mod at a time and the store takes
//! complete, validated settings objects.

use serde_json::{Map, Value};

use void_loadout::{Loadout, LoadoutId, LoadoutSummary, ModId};

use crate::error::Error;
use crate::models::LoadoutPatch;
use crate::state::AppState;

fn parse_id(id: &str) -> Result<LoadoutId, Error> {
    LoadoutId::new(id).ok_or_else(|| Error::BadLoadoutId(id.to_string()))
}

pub fn list(state: &AppState) -> Result<Vec<LoadoutSummary>, Error> {
    Ok(state.store.summaries()?)
}

pub fn get(state: &AppState, id: &str) -> Result<Loadout, Error> {
    Ok(state.store.load(&parse_id(id)?)?)
}

/// The loadout the Play screen's hero names.
pub fn active(state: &AppState) -> Result<Loadout, Error> {
    Ok(state.store.active()?)
}

pub fn create(state: &AppState, name: &str, icon: &str) -> Result<Loadout, Error> {
    let id = LoadoutId::slugify(name).ok_or_else(|| Error::BadLoadoutId(name.to_string()))?;
    if state.store.load(&id).is_ok() {
        return Err(Error::DuplicateLoadout(id.to_string()));
    }
    // Every mod is omitted on purpose: an omitted mod falls back to its registry
    // defaults, which is what keeps a loadout valid when a thirteenth mod is added.
    // Materialising twelve blocks here would freeze it at today's defaults for ever.
    let loadout = Loadout::new(id, name.trim(), icon);
    state.store.create(&loadout)?;
    Ok(loadout)
}

/// Merge a patch into a loadout.
///
/// `patch.mods` is a raw object rather than `ModStates`: the Mods screen writes one
/// switch flip at a time, and a typed `ModStates` would deserialize the eleven absent
/// mods as "cleared". Each named mod is merged over its *effective* settings — registry
/// defaults included — and handed to `ModStates::set`, which validates it against that
/// mod's settings sub-schema before it can reach disk.
pub fn update(state: &AppState, id: &str, patch: LoadoutPatch) -> Result<Loadout, Error> {
    let id = parse_id(id)?;
    let mut loadout = state.store.load(&id)?;

    if let Some(name) = patch.name {
        loadout.name = name;
    }
    if let Some(icon) = patch.icon {
        loadout.icon = icon;
    }
    if let Some(server) = patch.server {
        loadout.server = server;
    }
    if let Some(hud) = patch.hud {
        loadout.hud = hud;
    }
    if let Some(mods) = patch.mods {
        for (key, value) in mods {
            let mod_id = ModId::parse(&key)
                .ok_or_else(|| Error::Other(format!("`{key}` is not one of the 12 mods.")))?;
            let Value::Object(incoming) = value else {
                return Err(Error::Other(format!("`{key}` must be an object of settings.")));
            };
            let mut merged: Map<String, Value> = loadout.mods.effective(mod_id);
            for (k, v) in incoming {
                merged.insert(k, v);
            }
            loadout.mods.set(mod_id, merged)?;
        }
    }

    // Catches what the schema cannot: a duplicate HUD entry, an offset off the screen.
    loadout.validate()?;
    state.store.save(&loadout)?;
    Ok(loadout)
}

pub fn delete(state: &AppState, id: &str) -> Result<Vec<LoadoutSummary>, Error> {
    let id = parse_id(id)?;
    if state.store.list_ids()?.len() <= 1 {
        return Err(Error::LastLoadout);
    }
    state.store.delete(&id)?;
    Ok(state.store.summaries()?)
}

/// Switch the active loadout, and hot-swap it in a running game (§8.2).
pub fn switch(state: &AppState, id: &str) -> Result<Loadout, Error> {
    let id = parse_id(id)?;
    state.store.set_active(&id)?;
    let loadout = state.store.load(&id)?;
    state.game.lock().unwrap().push_loadout(&loadout);
    Ok(loadout)
}

/// The id after the active one, wrapping — what the in-game **L** key does, exposed so
/// the tray and the command palette cycle the same way.
pub fn next(state: &AppState) -> Result<Loadout, Error> {
    let current = state.store.active_id()?;
    let next = state.store.next_after(&current)?;
    switch(state, next.as_str())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::scratch_state;
    use serde_json::json;

    #[test]
    fn a_fresh_library_is_seeded_and_has_an_active_loadout() {
        let state = scratch_state();
        let summaries = list(&state).unwrap();
        assert!(!summaries.is_empty());
        let active = active(&state).unwrap();
        assert!(summaries.iter().any(|s| s.id == active.id));
    }

    #[test]
    fn a_new_loadout_omits_every_mod_so_registry_defaults_apply() {
        let state = scratch_state();
        let created = create(&state, "Crystal PvP", "sword").unwrap();
        assert_eq!(created.id.as_str(), "crystal-pvp");
        assert!(created.mods.present().is_empty());
        assert_eq!(created.mc, void_loadout::DEFAULT_MC);
        // …and it still reports sensible enabled mods, from the registry.
        assert!(!created.enabled_mods().is_empty());
    }

    #[test]
    fn creating_a_duplicate_or_an_unsluggable_name_is_refused() {
        let state = scratch_state();
        create(&state, "Crystal PvP", "sword").unwrap();
        assert!(matches!(
            create(&state, "crystal pvp", "sword"),
            Err(Error::DuplicateLoadout(_))
        ));
        assert!(matches!(create(&state, "!!!", "sword"), Err(Error::BadLoadoutId(_))));
    }

    #[test]
    fn a_single_mod_patch_leaves_every_other_mod_alone() {
        let state = scratch_state();
        let before = active(&state).unwrap();
        let id = before.id.to_string();
        let present_before = before.mods.present().len();

        let mut mods = Map::new();
        mods.insert("hitboxes".into(), json!({ "on": true }));
        let after =
            update(&state, &id, LoadoutPatch { mods: Some(mods), ..Default::default() }).unwrap();

        assert!(after.mods.is_on(ModId::Hitboxes));
        assert_eq!(after.hud.len(), before.hud.len());
        // Only the touched mod became explicit; nothing else was cleared.
        assert!(after.mods.present().len() >= present_before);
        for id in before.mods.present() {
            assert!(after.mods.present().contains(&id), "{id} was dropped");
        }
    }

    #[test]
    fn a_partial_mod_patch_is_merged_over_the_registry_defaults() {
        let state = scratch_state();
        let id = active(&state).unwrap().id.to_string();
        let mut mods = Map::new();
        // Only `gamma`; `on` must survive from the defaults rather than vanishing.
        mods.insert("fullbright".into(), json!({ "gamma": 12.0 }));
        let after =
            update(&state, &id, LoadoutPatch { mods: Some(mods), ..Default::default() }).unwrap();
        let effective = after.mods.effective(ModId::Fullbright);
        assert_eq!(effective["gamma"], json!(12.0));
        assert!(effective.contains_key("on"));
    }

    #[test]
    fn a_setting_the_schema_rejects_never_reaches_disk() {
        let state = scratch_state();
        let id = active(&state).unwrap().id.to_string();
        let mut mods = Map::new();
        mods.insert("zoom".into(), json!({ "fov_divisor": 999.0 }));
        assert!(update(&state, &id, LoadoutPatch { mods: Some(mods), ..Default::default() }).is_err());

        let mut unknown = Map::new();
        unknown.insert("teleport".into(), json!({ "on": true }));
        assert!(update(&state, &id, LoadoutPatch { mods: Some(unknown), ..Default::default() })
            .is_err());
    }

    #[test]
    fn switching_moves_the_active_pointer_and_an_unknown_id_is_refused() {
        let state = scratch_state();
        let ids: Vec<String> = list(&state).unwrap().into_iter().map(|s| s.id.to_string()).collect();
        let target = ids.last().unwrap();
        switch(&state, target).unwrap();
        assert_eq!(active(&state).unwrap().id.to_string(), *target);
        assert!(switch(&state, "nope").is_err());
        assert!(matches!(switch(&state, "Not An Id"), Err(Error::BadLoadoutId(_))));
    }

    #[test]
    fn cycling_wraps_around_the_library() {
        let state = scratch_state();
        let count = list(&state).unwrap().len();
        let first = active(&state).unwrap().id;
        for _ in 0..count {
            next(&state).unwrap();
        }
        assert_eq!(active(&state).unwrap().id, first);
    }

    #[test]
    fn deleting_returns_what_is_left_and_the_last_one_is_protected() {
        let state = scratch_state();
        let mut ids: Vec<String> =
            list(&state).unwrap().into_iter().map(|s| s.id.to_string()).collect();
        while ids.len() > 1 {
            let victim = ids.pop().unwrap();
            let rest = delete(&state, &victim).unwrap();
            assert_eq!(rest.len(), ids.len());
        }
        assert!(matches!(delete(&state, &ids[0]), Err(Error::LastLoadout)));
    }
}
