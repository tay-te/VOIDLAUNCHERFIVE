//! Keeping the loadout store in step with what the mod reports.
//!
//! Java is authoritative for live state and tells Rust afterwards (§6.1); this is the
//! "afterwards". [`StoreInit`] answers `hello` with whatever is on disk right now, and
//! [`pump`] folds every inbound message back into the store, so the launcher's library
//! reflects what the player did in game.

use std::sync::Arc;

use void_bridge::{BridgeServer, InitPayload, InitSource, JavaToRust};
use void_loadout::{GlobalSettings, LoadoutId, Store};

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
        let loadouts = self.store.summaries().unwrap_or_else(|e| {
            tracing::error!(error = %e, "cannot read the library");
            vec![loadout.summary()]
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

            JavaToRust::Unknown => {}
        }
    }
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
    use void_loadout::{Anchor, HudItem, HudModId, ModId, StatePatch};

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
    fn a_state_patch_from_the_game_lands_on_disk() {
        let (_d, store) = store();
        let id = LoadoutId::new("sword-pvp").unwrap();
        let mut patch = StatePatch::new();
        patch.insert(ModId::Fullbright, "on", true);

        apply_state(&store, &id, &patch).unwrap();
        assert!(store.load(&id).unwrap().mods.is_on(ModId::Fullbright));
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
}
