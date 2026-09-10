//! Keeping the loadout store in step with what the mod reports.
//!
//! Java is authoritative for live state and tells Rust afterwards (§6.1); this is the
//! "afterwards". [`StoreInit`] answers `hello` with whatever is on disk right now, and
//! [`pump`] folds every inbound message back into the store, so the launcher's library
//! reflects what the player did in game.

use std::sync::Arc;

use void_bridge::{BridgeServer, HotkeyId, InitPayload, InitSource, JavaToRust};
use void_loadout::{GlobalPatch, GlobalSettings, LoadoutId, Store};

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

/// Wall clock in unix milliseconds, for the server book's `last_played_ms`.
///
/// Wall clock and not a monotonic instant, deliberately: this figure is shown to a player as
/// "last played", so it has to survive the process it was written in. A clock that steps
/// backwards makes one record sort oddly, which is a cosmetic wrong answer; a monotonic reading
/// persisted across restarts is meaningless, which is not.
fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

/// Which server a slice of playtime belongs to, if any.
///
/// A plain function rather than three lines inside the pump, for the reason the mod's own sensors
/// give: the loop cannot be unit-tested and the rule can, and this rule has a case that is easy
/// to get wrong in a way nobody notices — time spent in the main menu.
///
/// `reported` is `session.server`, which the mod sets to the host it is on and to `None` in the
/// menu or a singleplayer world. `tracked` is the last connect edge from the `server` message.
/// The report wins when it has a value because it is the more recent of the two; the edge covers
/// the case where a report lands mid-connect with nothing in it.
///
/// **Returning `None` is the important half.** Menu time is real playtime for the loadout and is
/// not time on any server, so it must not be filed under the last one — a launcher that did that
/// would tell a player they had hours on a server they left before dinner.
fn server_for_report(reported: Option<String>, tracked: &Option<String>) -> Option<String> {
    reported
        .or_else(|| tracked.clone())
        .map(|host| host.trim().to_string())
        .filter(|host| !host.is_empty())
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
    // Where the player is right now, from the `server` message's own connect/disconnect edges.
    //
    // Tracked here rather than read off each `session` report, because the two disagree in the
    // case that matters: a report can arrive with `server: null` while the player is mid-connect,
    // and attributing that minute to nowhere would quietly lose it. The edge is the fact; the
    // report's own field is a snapshot of it. `session.server` is still preferred when it has a
    // value, because it is the more recent of the two.
    let mut current_host: Option<String> = None;

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
                current_host = None;
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

            JavaToRust::Session { fps_avg, played_ms, server, loadout } => {
                let delta = played_ms.saturating_sub(reported_ms);
                reported_ms = played_ms;
                let target = loadout.or_else(|| store.active_id().ok());
                if let Some(id) = target {
                    if let Err(e) = store.record_session(&id, delta, fps_avg) {
                        tracing::error!(error = %e, %id, "could not record session stats");
                    }
                }

                // The same slice of time, attributed to where it was spent. Only when there is
                // somewhere: time in the main menu or a singleplayer world is real playtime for
                // the loadout above and is not time on any server, and a launcher that filed it
                // under the last one would tell a player they had played hours on a server they
                // left before dinner.
                if let Some(host) = server_for_report(server, &current_host) {
                    if let Err(e) =
                        store.update_servers(|book| book.record_session(&host, delta, now_ms()))
                    {
                        tracing::error!(error = %e, %host, "could not record server playtime");
                    }
                }
            }

            JavaToRust::Server { host, connected, port } => {
                tracing::info!(%host, connected, ?port, "server presence");
                // A connection is its own fact, separate from any playtime that follows it — a
                // player who alt-F4s in the queue has still joined. `servers.rs` splits the two
                // for that reason.
                if connected && !host.is_empty() {
                    if let Err(e) =
                        store.update_servers(|book| book.record_join(&host, now_ms()))
                    {
                        tracing::error!(error = %e, %host, "could not record a server join");
                    }
                    current_host = Some(host);
                } else {
                    current_host = None;
                }
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

            JavaToRust::Unknown => {}
        }
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
    fn menu_time_is_not_filed_under_the_last_server() {
        // The case worth a test: `session.server` is null in the main menu and in singleplayer,
        // and the connect edge has already been cleared by the `server` message that said so. A
        // launcher that fell back further would credit a server the player left.
        assert_eq!(server_for_report(None, &None), None);
        assert_eq!(server_for_report(Some(String::new()), &None), None);
        assert_eq!(server_for_report(Some("   ".into()), &None), None);
    }

    #[test]
    fn the_report_wins_over_the_edge_and_the_edge_covers_the_gap() {
        // The report is the more recent of the two, so a player who hopped servers between
        // reports is credited to where they ended up.
        assert_eq!(
            server_for_report(Some("na.minemen.club".into()), &Some("mc.hypixel.net".into())),
            Some("na.minemen.club".into())
        );
        // And a report that lands mid-connect with nothing in it still knows where it is.
        assert_eq!(
            server_for_report(None, &Some("mc.hypixel.net".into())),
            Some("mc.hypixel.net".into())
        );
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
}
