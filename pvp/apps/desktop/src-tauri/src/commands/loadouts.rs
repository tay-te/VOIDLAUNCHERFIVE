//! `loadouts_list` · `loadouts_get` · `loadouts_active` · `loadouts_create` ·
//! `loadouts_update` · `loadouts_delete` · `loadouts_switch` · `loadouts_export` ·
//! `loadouts_import`
//!
//! Thin over `void_loadout::Store`. The one place that is more than a forward is
//! [`update`], because the Mods screen sends a single mod at a time and the store takes
//! complete, validated settings objects.
//!
//! Every command that changes the library — create, update, delete, import — ends in
//! [`push_library`], so a running game sees the change without a relaunch. A switch
//! travels as its own `loadout` message instead, as it always has.

use serde_json::{Map, Value};

use void_loadout::{Loadout, LoadoutId, LoadoutSummary, ModId};

use crate::error::Error;
use crate::models::{ImportedLoadout, LoadoutPatch};
use crate::state::AppState;

fn parse_id(id: &str) -> Result<LoadoutId, Error> {
    LoadoutId::new(id).ok_or_else(|| Error::BadLoadoutId(id.to_string()))
}

/// Send the library, as it now stands on disk, to a running game. Called after the
/// change has been written, so a failure here is logged rather than returned: the
/// player's edit did land, and the mod gets the library again in its next `init`.
fn push_library(state: &AppState) {
    match state.store.list() {
        Ok(loadouts) => state.game.lock().unwrap().push_library(&loadouts),
        Err(e) => tracing::warn!(error = %e, "could not read the library to push it"),
    }
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
    push_library(state);
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
    push_library(state);
    Ok(loadout)
}

pub fn delete(state: &AppState, id: &str) -> Result<Vec<LoadoutSummary>, Error> {
    let id = parse_id(id)?;
    if state.store.list_ids()?.len() <= 1 {
        return Err(Error::LastLoadout);
    }
    state.store.delete(&id)?;
    push_library(state);
    Ok(state.store.summaries()?)
}

/// A loadout's share code — the `VOID1.…` string a player pastes to a friend.
pub fn export(state: &AppState, id: &str) -> Result<String, Error> {
    state.store.export_share(&parse_id(id)?).map_err(Error::from_loadout)
}

/// Add the loadout a share code carries to the end of the library, under a fresh id.
///
/// Does not switch to it: an import is "keep this", and the player picks it like any
/// other loadout. A refused code comes back as [`Error::Share`], whose message is the
/// sentence the import field shows under itself.
pub fn import(state: &AppState, code: &str) -> Result<ImportedLoadout, Error> {
    let (loadout, dropped) = state.store.import_share(code).map_err(Error::from_loadout)?;
    push_library(state);
    Ok(ImportedLoadout { loadout, dropped })
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

        // A mod outside the closed 12.
        let mut unknown = Map::new();
        unknown.insert("teleport".into(), json!({ "on": true }));
        assert!(update(&state, &id, LoadoutPatch { mods: Some(unknown), ..Default::default() })
            .is_err());

        // A setting of the wrong type. `ModStates::set` runs the value through the mod's
        // settings sub-schema, so this never reaches disk.
        let mut wrong_type = Map::new();
        wrong_type.insert("zoom".into(), json!({ "fov_divisor": "fast" }));
        assert!(update(&state, &id, LoadoutPatch { mods: Some(wrong_type), ..Default::default() })
            .is_err());

        // A mod body that is not an object at all.
        let mut not_object = Map::new();
        not_object.insert("zoom".into(), json!(true));
        assert!(update(&state, &id, LoadoutPatch { mods: Some(not_object), ..Default::default() })
            .is_err());

        // …and none of the three left a mark.
        assert_eq!(active(&state).unwrap(), get(&state, &id).unwrap());
    }

    #[test]
    fn a_hud_layout_with_a_duplicate_entry_is_refused() {
        use void_loadout::{Anchor, HudItem};
        let state = scratch_state();
        let id = active(&state).unwrap().id.to_string();
        let dup = vec![
            HudItem::new(void_loadout::HudModId::Fps, Anchor::TopLeft, 20.0, 20.0),
            HudItem::new(void_loadout::HudModId::Fps, Anchor::TopRight, -20.0, 20.0),
        ];
        // `Loadout::validate` enforces the one-item-per-mod invariant JSON Schema cannot.
        assert!(update(&state, &id, LoadoutPatch { hud: Some(dup), ..Default::default() }).is_err());
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

    #[test]
    fn an_exported_code_imports_as_a_new_loadout_with_a_fresh_id() {
        let state = scratch_state();
        let source = active(&state).unwrap();
        let before = list(&state).unwrap().len();

        let code = export(&state, source.id.as_str()).unwrap();
        assert!(code.starts_with("VOID"), "{code}");

        let imported = import(&state, &code).unwrap();
        assert_ne!(imported.loadout.id, source.id, "an import never overwrites");
        assert_eq!(imported.loadout.name, source.name);
        assert_eq!(imported.loadout.hud, source.hud);
        assert_eq!(imported.dropped, 0);

        // Appended to the library, stored, and not switched to.
        let after = list(&state).unwrap();
        assert_eq!(after.len(), before + 1);
        assert_eq!(after.last().unwrap().id, imported.loadout.id);
        assert_eq!(get(&state, imported.loadout.id.as_str()).unwrap(), imported.loadout);
        assert_eq!(active(&state).unwrap().id, source.id);

        // A second import of the same code gets yet another id.
        let again = import(&state, &code).unwrap();
        assert_ne!(again.loadout.id, imported.loadout.id);
    }

    #[test]
    fn a_bad_code_is_refused_with_a_sentence_and_nothing_is_stored() {
        let state = scratch_state();
        let before = list(&state).unwrap().len();

        let empty = import(&state, "   ").unwrap_err();
        assert!(matches!(empty, Error::Share(void_loadout::ShareError::Empty)));
        assert_eq!(empty.to_string(), "Nothing to import.");

        let junk = import(&state, "hello there").unwrap_err();
        assert!(matches!(junk, Error::Share(void_loadout::ShareError::Format)));
        assert_eq!(junk.to_string(), "That is not a VOID loadout code.");

        // One character changed in transit: the checksum (or the data) no longer holds.
        let code = export(&state, active(&state).unwrap().id.as_str()).unwrap();
        let mut damaged: Vec<char> = code.chars().collect();
        let mid = damaged.len() / 2;
        damaged[mid] = if damaged[mid] == 'A' { 'B' } else { 'A' };
        let damaged: String = damaged.into_iter().collect();
        assert!(matches!(import(&state, &damaged), Err(Error::Share(_))));

        assert_eq!(list(&state).unwrap().len(), before);
        assert!(matches!(export(&state, "nope"), Err(Error::Loadout(_))));
    }

    #[test]
    fn an_import_into_a_full_library_is_refused() {
        let state = scratch_state();
        let code = export(&state, active(&state).unwrap().id.as_str()).unwrap();
        let mut n = list(&state).unwrap().len();
        while n < void_loadout::store::MAX_LIBRARY {
            create(&state, &format!("Filler {n}"), "sword").unwrap();
            n += 1;
        }
        let full = import(&state, &code).unwrap_err();
        assert!(matches!(full, Error::Share(void_loadout::ShareError::Full)));
        assert_eq!(full.to_string(), "The library is full.");
        assert_eq!(list(&state).unwrap().len(), void_loadout::store::MAX_LIBRARY);
    }
}
