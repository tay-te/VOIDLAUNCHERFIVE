//! Keeping the loadout store in step with what the mod reports.
//!
//! Java is authoritative for live state and tells Rust afterwards (§6.1); this is the
//! "afterwards". [`StoreInit`] answers `hello` with whatever is on disk right now, and
//! [`pump`] folds every inbound message back into the store, so the launcher's library
//! reflects what the player did in game.

use std::sync::Arc;

use void_bridge::{BridgeServer, HotkeyId, InitPayload, InitSource, JavaToRust, RustToJava};
use void_loadout::{GlobalPatch, GlobalSettings, Loadout, LoadoutId, Store};

/// Answers `init` from the on-disk library, freshly read on every connect.
///
/// Reading per connect rather than caching at bind time is what makes a reconnect after
/// a launcher-side switch deliver the *current* loadout.
#[derive(Debug, Clone)]
pub struct StoreInit {
    store: Arc<Store>,
}

impl StoreInit {
    /// Wraps a store.
    pub fn new(store: Store) -> Self {
        Self { store: Arc::new(store) }
    }
}

impl InitSource for StoreInit {
    fn init(&self) -> InitPayload {
        let loadout = self.store.active().unwrap_or_else(|e| {
            tracing::error!(error = %e, "cannot read the active loadout; sending the built-in default");
            void_loadout::defaults::sword_pvp()
        });
        // The whole library, in full: `init.loadouts` carries complete loadouts so the
        // mod can hot-swap to any of them in under a frame without asking (§8.2), and so
        // the in-game Loadouts screen has something to list.
        let loadouts = self.store.list().unwrap_or_else(|e| {
            tracing::error!(error = %e, "cannot read the library");
            vec![loadout.clone()]
        });
        let settings = self.store.settings().unwrap_or_else(|e| {
            tracing::error!(error = %e, "cannot read settings");
            GlobalSettings::factory()
        });
        InitPayload { loadout, loadouts, settings }
    }
}

/// Folds inbound messages into the store until the bridge closes.
///
/// Errors are logged, never propagated: a bad frame from the game must not take the
/// launcher's sync loop down with it.
pub async fn pump(server: BridgeServer, store: Store) {
    let mut bus = server.subscribe();
    // `session.played_ms` is cumulative for the game session, but the store accumulates
    // deltas, so the running total is tracked per connection and reset on `hello`.
    let mut reported_ms: u64 = 0;

    loop {
        let msg = match bus.recv().await {
            Ok(msg) => msg,
            Err(tokio::sync::broadcast::error::RecvError::Lagged(n)) => {
                tracing::warn!(dropped = n, "loadout sync fell behind");
                continue;
            }
            Err(tokio::sync::broadcast::error::RecvError::Closed) => break,
        };

        match msg {
            JavaToRust::Hello { mc, mod_version, .. } => {
                reported_ms = 0;
                tracing::info!(%mc, %mod_version, "mod connected");
            }

            JavaToRust::State { loadout, patch } => {
                if let Err(e) = apply_state(&store, &loadout, &patch) {
                    tracing::error!(error = %e, %loadout, "could not apply a state patch");
                }
            }

            JavaToRust::Hud { loadout, items } => {
                if let Err(e) = apply_hud(&store, &loadout, items) {
                    tracing::error!(error = %e, %loadout, "could not apply a hud layout");
                }
            }

            JavaToRust::Globals { patch } => {
                if let Err(e) = apply_globals(&store, &patch) {
                    tracing::error!(error = %e, "could not apply a global settings patch");
                }
            }

            JavaToRust::Session { fps_avg, played_ms, loadout, .. } => {
                let delta = played_ms.saturating_sub(reported_ms);
                reported_ms = played_ms;
                let target = loadout.or_else(|| store.active_id().ok());
                if let Some(id) = target {
                    if let Err(e) = store.record_session(&id, delta, fps_avg) {
                        tracing::error!(error = %e, %id, "could not record session stats");
                    }
                }
            }

            JavaToRust::Server { host, connected, port } => {
                tracing::info!(%host, connected, ?port, "server presence");
            }

            // A notification, never a request: the mod has already done the thing. The
            // L cycle moved the active loadout, so the pointer on disk has to follow or
            // the tray and the next launch would disagree with the running game.
            JavaToRust::Hotkey { id } => match id {
                HotkeyId::LoadoutNext => {
                    if let Err(e) = advance_active(&store) {
                        tracing::error!(error = %e, "could not follow the in-game loadout cycle");
                    }
                }
                HotkeyId::Overlay => tracing::debug!("the overlay was toggled in game"),
            },

            // The in-game Loadouts screen. Java has already done each of these; if the store
            // cannot follow, the game is sent the library as it is on disk so the two agree.
            JavaToRust::LoadoutSave { loadout } => {
                if let Err(e) = apply_loadout_save(&store, &loadout) {
                    tracing::error!(error = %e, id = %loadout.id, "could not store a loadout from the game");
                    resync_library(&server, &store);
                }
            }
            JavaToRust::LoadoutDelete { id } => {
                if let Err(e) = apply_loadout_delete(&store, &id) {
                    tracing::error!(error = %e, %id, "could not delete a loadout the game deleted");
                    resync_library(&server, &store);
                }
            }
            JavaToRust::LoadoutSelect { id } => {
                if let Err(e) = store.set_active(&id) {
                    tracing::error!(error = %e, %id, "could not follow an in-game loadout switch");
                }
            }

            JavaToRust::Unknown => {}
        }
    }
}

/// Stores a loadout the game created or changed: a new id is appended to the library
/// order, a known one overwritten. Stats are Rust's to keep — the game reports play time
/// through `session` — so a stored loadout's stats survive the save.
fn apply_loadout_save(store: &Store, loadout: &Loadout) -> Result<(), void_loadout::Error> {
    let mut incoming = loadout.clone();
    if let Ok(existing) = store.load(&incoming.id) {
        incoming.stats = existing.stats;
    }
    store.upsert(&incoming)?;
    tracing::info!(id = %incoming.id, "loadout stored from the game");
    Ok(())
}

/// Deletes a loadout the game deleted, never the last one.
fn apply_loadout_delete(store: &Store, id: &LoadoutId) -> Result<(), void_loadout::Error> {
    if store.list_ids()?.len() <= 1 {
        return Err(void_loadout::Error::Invalid("the last loadout cannot be deleted".into()));
    }
    store.delete(id)?;
    tracing::info!(%id, "loadout deleted from the game");
    Ok(())
}

/// Sends the game the library as it is on disk.
fn resync_library(server: &BridgeServer, store: &Store) {
    match store.list() {
        Ok(loadouts) if !loadouts.is_empty() => {
            if let Err(e) = server.send(&RustToJava::Library { loadouts }) {
                tracing::warn!(error = %e, "could not resync the library to the game");
            }
        }
        Ok(_) => {}
        Err(e) => tracing::error!(error = %e, "cannot read the library to resync it"),
    }
}

/// Moves the stored active pointer one step, mirroring the mod's L-key cycle.
///
/// The mod cycles in the order it received in `init.loadouts`, which is the store's own
/// library order, so walking `next_after` here lands on the same loadout.
fn advance_active(store: &Store) -> Result<(), void_loadout::Error> {
    let current = store.active_id()?;
    let next = store.next_after(&current)?;
    if next != current {
        store.set_active(&next)?;
        tracing::info!(%next, "active loadout followed the in-game L cycle");
    }
    Ok(())
}

fn apply_state(
    store: &Store,
    id: &LoadoutId,
    patch: &void_loadout::StatePatch,
) -> Result<(), void_loadout::Error> {
    let mut loadout = store.load(id)?;
    let changes = loadout.apply_patch(patch)?;
    if changes.is_empty() {
        return Ok(());
    }
    store.save(&loadout)?;
    tracing::info!(%id, changes = changes.len(), "loadout state updated from the game");
    Ok(())
}

/// Folds a `globals` patch into `settings.json`.
///
/// Read-modify-write rather than overwrite, for the same reason the message is a delta:
/// the file may hold globals the mod knows nothing about, and this must not be the thing
/// that eats them. `apply_patch` refuses a bad patch whole, so a rejected frame leaves
/// the file exactly as it was.
fn apply_globals(store: &Store, patch: &GlobalPatch) -> Result<(), void_loadout::Error> {
    if patch.is_empty() {
        return Ok(());
    }
    let mut settings = store.settings()?;
    let before = settings.clone();
    settings.apply_patch(patch)?;
    if settings == before {
        return Ok(());
    }
    store.save_settings(&settings)?;
    tracing::info!(keys = patch.len(), "global settings updated from the game");
    Ok(())
}

fn apply_hud(
    store: &Store,
    id: &LoadoutId,
    items: Vec<void_loadout::HudItem>,
) -> Result<(), void_loadout::Error> {
    let mut loadout = store.load(id)?;
    loadout.hud = items;
    // `validate` is what enforces the one-item-per-mod invariant the schema cannot.
    loadout.validate()?;
    store.save(&loadout)?;
    tracing::info!(%id, items = loadout.hud.len(), "hud layout updated from the game");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use void_loadout::{Anchor, GlobalPatch, HudItem, HudModId, ModId, StatePatch};

    fn store() -> (tempfile::TempDir, Store) {
        let dir = tempfile::tempdir().unwrap();
        let store = Store::at(dir.path());
        store.init().unwrap();
        (dir, store)
    }

    #[test]
    fn init_is_answered_from_the_live_store() {
        let (_d, store) = store();
        let source = StoreInit::new(store.clone());
        assert_eq!(source.init().loadout.id.as_str(), "sword-pvp");

        // A launcher-side switch is visible to the next connect, not the previous one.
        store.set_active(&LoadoutId::new("uhc").unwrap()).unwrap();
        assert_eq!(source.init().loadout.id.as_str(), "uhc");
        assert_eq!(source.init().loadouts.len(), 3);
    }

    #[test]
    fn init_carries_whole_loadouts_not_summaries() {
        let (_d, store) = store();
        let payload = StoreInit::new(store).init();
        assert_eq!(payload.loadouts.len(), 3);
        // The mod hot-swaps straight out of this list (§8.2), so every entry must be
        // applyable on its own — mod states and all.
        for l in &payload.loadouts {
            assert!(!l.name.is_empty());
            assert!(l.mods.effective(ModId::Keystrokes).contains_key("on"));
        }
        assert!(payload.loadouts.iter().any(|l| l.id == payload.loadout.id));
    }

    #[test]
    fn the_l_key_hotkey_moves_the_stored_active_pointer() {
        let (_d, store) = store();
        let first = store.active_id().unwrap();
        advance_active(&store).unwrap();
        let second = store.active_id().unwrap();
        assert_ne!(first, second, "the L cycle must be visible to the tray and the next launch");
        // Three defaults, so cycling three times comes back round.
        advance_active(&store).unwrap();
        advance_active(&store).unwrap();
        assert_eq!(store.active_id().unwrap(), first);
    }

    #[test]
    fn a_state_patch_from_the_game_lands_on_disk() {
        let (_d, store) = store();
        let id = LoadoutId::new("sword-pvp").unwrap();
        let mut patch = StatePatch::new();
        patch.insert(ModId::Fullbright, "on", true);

        apply_state(&store, &id, &patch).unwrap();
        assert!(store.load(&id).unwrap().mods.is_on(ModId::Fullbright));
    }

    #[test]
    fn a_global_written_in_game_lands_on_disk() {
        let (_d, store) = store();
        let mut patch = GlobalPatch::new();
        patch.insert("hud_editor_grid", 8);

        apply_globals(&store, &patch).unwrap();
        assert_eq!(store.settings().unwrap().hud_editor_grid(), 8);
    }

    #[test]
    fn a_global_patch_leaves_the_globals_it_does_not_name_alone() {
        // The whole point of the delta: `settings.json` may hold a global the mod's
        // five-field GlobalSettings cannot model, and an in-game toggle must not eat it.
        let (_d, store) = store();
        let mut seeded = store.settings().unwrap();
        seeded.extra.insert("chat_opacity".into(), serde_json::json!(0.5));
        seeded.theme = Some("void-light".into());
        store.save_settings(&seeded).unwrap();

        let mut patch = GlobalPatch::new();
        patch.insert("hud_editor_grid", 0);
        apply_globals(&store, &patch).unwrap();

        let after = store.settings().unwrap();
        assert_eq!(after.hud_editor_grid(), 0);
        assert_eq!(after.theme(), "void-light");
        assert_eq!(after.extra.get("chat_opacity"), Some(&serde_json::json!(0.5)));
    }

    #[test]
    fn a_bad_global_patch_leaves_the_file_untouched() {
        let (_d, store) = store();
        let before = store.settings().unwrap();
        let mut patch = GlobalPatch::new();
        patch.insert("hud_editor_grid", "eight");

        assert!(apply_globals(&store, &patch).is_err());
        assert_eq!(store.settings().unwrap(), before);
    }

    #[test]
    fn a_hud_layout_from_the_game_replaces_the_stored_one() {
        let (_d, store) = store();
        let id = LoadoutId::new("sword-pvp").unwrap();
        apply_hud(
            &store,
            &id,
            vec![HudItem::new(HudModId::Fps, Anchor::BottomRight, -8.0, -8.0)],
        )
        .unwrap();

        let hud = store.load(&id).unwrap().hud;
        assert_eq!(hud.len(), 1, "the message carries the whole layout, not a delta");
        assert_eq!(hud[0].anchor, Anchor::BottomRight);
    }

    #[test]
    fn a_duplicated_hud_item_is_refused_rather_than_stored() {
        let (_d, store) = store();
        let id = LoadoutId::new("sword-pvp").unwrap();
        let before = store.load(&id).unwrap().hud;
        let result = apply_hud(
            &store,
            &id,
            vec![
                HudItem::new(HudModId::Fps, Anchor::TopLeft, 0.0, 0.0),
                HudItem::new(HudModId::Fps, Anchor::Top, 0.0, 0.0),
            ],
        );
        assert!(result.is_err());
        assert_eq!(store.load(&id).unwrap().hud, before);
    }

    #[test]
    fn an_in_game_save_creates_then_overwrites_and_keeps_stats() {
        let (_d, store) = store();
        let mut fresh = Loadout::new(LoadoutId::new("bridge").unwrap(), "Bridge", "sword");
        apply_loadout_save(&store, &fresh).unwrap();
        assert_eq!(store.list().unwrap().last().unwrap().id.as_str(), "bridge");

        store.record_session(&fresh.id, 60_000, 120.0).unwrap();
        fresh.name = "Bridging".into();
        apply_loadout_save(&store, &fresh).unwrap();
        let stored = store.load(&fresh.id).unwrap();
        assert_eq!(stored.name, "Bridging");
        assert_eq!(stored.stats.and_then(|s| s.played_ms), Some(60_000), "Rust owns stats");
    }

    #[test]
    fn an_in_game_delete_never_takes_the_last_loadout() {
        let (_d, store) = store();
        for id in ["sword-pvp", "bedwars"] {
            apply_loadout_delete(&store, &LoadoutId::new(id).unwrap()).unwrap();
        }
        assert!(apply_loadout_delete(&store, &LoadoutId::new("uhc").unwrap()).is_err());
        assert_eq!(store.list_ids().unwrap().len(), 1);
    }
}