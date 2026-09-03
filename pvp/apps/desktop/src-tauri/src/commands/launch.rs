//! `prepare` · `launch` · `game_kill` · `game_log_tail`

use std::sync::Arc;

use crate::adapters::{game, prepare};
use crate::error::Error;
use crate::events::Emitter;
use crate::models::{LaunchReport, PrepareReport};
use crate::state::AppState;

/// Download everything the loadout needs: game, Legacy Fabric, Java 8, mod JAR.
///
/// Emits `prepare:progress` throughout. Idempotent by design — running it twice must
/// be a no-op the second time, which is what makes "Launch" safe to press repeatedly.
pub async fn prepare(
    state: &AppState,
    emitter: &dyn Emitter,
    loadout_id: &str,
) -> Result<PrepareReport, Error> {
    // Fail before the progress bar appears if the loadout is gone.
    let loadout = state.store.lock().unwrap().get(loadout_id)?;

    let (settings, data_dir) = {
        let store = state.store.lock().unwrap();
        (store.settings(), state.data_dir.clone())
    };

    let java = crate::adapters::java::detect(
        &data_dir,
        settings.java_path.as_deref(),
        settings.java_auto,
    );
    let java_path = java.path.clone().unwrap_or_else(|| "java".into());

    prepare::run(emitter, &loadout.id, java_path).await
}

/// Start the bridge server, spawn the JVM, and stream `game:*` / `bridge:*` events.
pub async fn launch(
    state: &AppState,
    emitter: Arc<dyn Emitter>,
    loadout_id: &str,
) -> Result<LaunchReport, Error> {
    let (loadout, settings) = {
        let store = state.store.lock().unwrap();
        (store.get(loadout_id)?, store.settings())
    };

    if state.account.lock().unwrap().is_none() {
        return Err(Error::NotSignedIn);
    }

    let java = crate::adapters::java::detect(
        &state.data_dir,
        settings.java_path.as_deref(),
        settings.java_auto,
    );
    if !java.found {
        return Err(Error::Java(match java.version {
            Some(v) => format!("found Java {v}, but 1.8.9 needs Java 8"),
            None => "no Java runtime on this machine".into(),
        }));
    }

    game::launch(
        state.game.clone(),
        emitter,
        loadout.id,
        settings.ram_mb,
        java.path.unwrap_or_else(|| "java".into()),
    )
    .await
}

pub fn kill(state: &AppState) -> Result<(), Error> {
    game::kill(&state.game.lock().unwrap())
}

pub fn is_running(state: &AppState) -> bool {
    state.game.lock().unwrap().is_running()
}

/// The last `lines` of JVM output, for the log drawer when it opens mid-session.
pub fn log_tail(state: &AppState, lines: usize) -> Result<Vec<String>, Error> {
    Ok(state.game.lock().unwrap().tail(lines.min(2000)))
}

/// Fold a `session` telemetry summary into the loadout's stats. Called when the bridge
/// forwards `session`, and again on `game:closed`.
pub fn record_session(state: &AppState, loadout_id: &str, played_ms: u64, fps_avg: f64) {
    state
        .store
        .lock()
        .unwrap()
        .record_session(loadout_id, played_ms, fps_avg);
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::events::test_support::Recorder;
    use crate::state::scratch_state;

    #[tokio::test]
    async fn prepare_refuses_an_unknown_loadout_before_showing_progress() {
        let state = scratch_state();
        let rec = Recorder::default();
        let err = prepare(&state, &rec, "does-not-exist").await.unwrap_err();
        assert!(matches!(err, Error::UnknownLoadout(_)));
        assert!(rec.names().is_empty(), "no progress event should have been emitted");
    }

    #[tokio::test]
    async fn launching_without_an_account_is_refused() {
        let state = scratch_state();
        let rec: Arc<dyn Emitter> = Arc::new(Recorder::default());
        let err = launch(&state, rec, "sword-pvp").await.unwrap_err();
        assert!(matches!(err, Error::NotSignedIn));
        assert_eq!(err.to_string(), "Not signed in. Sign in with your Microsoft account to launch.");
    }

    #[tokio::test]
    async fn launching_an_unknown_loadout_is_refused_before_the_account_check() {
        let state = scratch_state();
        let rec: Arc<dyn Emitter> = Arc::new(Recorder::default());
        assert!(matches!(
            launch(&state, rec, "nope").await.unwrap_err(),
            Error::UnknownLoadout(_)
        ));
    }

    #[test]
    fn killing_an_idle_game_is_an_error() {
        let state = scratch_state();
        assert!(!is_running(&state));
        assert!(kill(&state).is_err());
    }

    #[test]
    fn session_stats_land_on_the_loadout() {
        let state = scratch_state();
        record_session(&state, "bedwars", 60_000, 120.0);
        let l = crate::commands::loadouts::get(&state, "bedwars").unwrap();
        assert_eq!(l.stats.played_ms, 60_000);
        assert!((l.stats.fps_avg - 120.0).abs() < 1e-9);
    }
}
