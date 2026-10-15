//! `prepare` · `launch` · `game_kill` · `game_log_tail`
//!
//! `prepare` is idempotent by construction: `void_core::install::prepare` verifies every
//! file against the manifest and skips what is already on disk or in the hash-addressed
//! cache, so pressing Launch twice costs a manifest fetch and nothing else. That is what
//! lets the button call it unconditionally instead of maintaining an "is it installed"
//! flag that can go stale.
//!
//! **Offline.** Every network step falls back to what an earlier run cached: the game
//! manifests (`install::prepare`), the signed mod manifest and JAR
//! (`release::ensure_mod_jar`), and an installed Java. A warm launch therefore works with
//! no internet at all; a cold one says which download it could not make.

use std::sync::{Arc, Mutex};

use void_core::install::ModPlatform;
use void_core::manifest::RuleContext;
use void_core::release::{self, Channel, ModSource, ResolvedMod};
use void_core::{install, java};

use crate::adapters::game::{self, LaunchOptionsSeed, LaunchRequest};
use crate::adapters::progress::{self, PrepareProgressSink};
use crate::error::Error;
use crate::events::Emitter;
use crate::models::{LaunchReport, PrepareReport};
use crate::state::AppState;

/// This launcher's version, which the mod manifest's `min_launcher_version` is checked
/// against.
const LAUNCHER_VERSION: &str = env!("CARGO_PKG_VERSION");

/// The mod JAR for this launch: the developer override when set, else the release
/// channel's verified copy — fetched (`network = true`) or from the cache only.
async fn resolve_mod(
    state: &AppState,
    platform: ModPlatform,
    network: bool,
    progress: Option<tokio::sync::mpsc::Sender<void_core::download::Progress>>,
) -> Result<ResolvedMod, Error> {
    if let Some(jar) = state.config()?.mod_jar_override() {
        return Ok(release::dev_override(jar)?);
    }
    let channel = Channel::parse(&super::settings::update_channel(state));
    let protocol = void_bridge::PROTOCOL_VERSION;
    if !network {
        if let Some(cached) =
            release::resolve_cached(&state.paths, platform, channel, LAUNCHER_VERSION, protocol)
        {
            return Ok(cached);
        }
    }
    Ok(release::ensure_mod_jar(
        &state.http,
        &state.paths,
        platform,
        channel,
        LAUNCHER_VERSION,
        protocol,
        progress,
    )
    .await?)
}

/// Download the game, Legacy Fabric, Java 8 and the mod JAR for a loadout.
pub async fn prepare(
    state: &AppState,
    emitter: Arc<dyn Emitter>,
    loadout_id: &str,
) -> Result<PrepareReport, Error> {
    // Fail before any progress event so the bar never appears for a loadout that is
    // already gone.
    let loadout = super::loadouts::get(state, loadout_id)?;
    state.presence.set(crate::presence::Activity::Launching { loadout: loadout.name.clone() });
    let report = prepare_loadout(state, emitter, loadout).await;
    if report.is_err() {
        state.presence.set(crate::presence::Activity::Idle);
    }
    report
}

async fn prepare_loadout(
    state: &AppState,
    emitter: Arc<dyn Emitter>,
    loadout: void_loadout::Loadout,
) -> Result<PrepareReport, Error> {
    let config = state.config()?;
    let ctx = RuleContext::host()?;

    let sink = Arc::new(Mutex::new(PrepareProgressSink::new()));
    sink.lock().unwrap().step(emitter.as_ref(), "manifest", Some("1.8.9 + Legacy Fabric".into()));

    // One channel for the whole game install. `install::prepare` runs two download
    // passes through it — libraries and the client jar, then assets — because the asset
    // list only exists once the index has been fetched.
    let (tx, drained) = progress::channel(emitter.clone(), sink.clone());
    let profile = install::prepare(&state.http, &state.paths, &ctx, None, Some(tx)).await;
    // The sender is dropped by `prepare`; waiting on the drain keeps the event order
    // matching the work order rather than interleaving with the Java phase.
    let _ = drained.await;
    let profile = profile?;

    sink.lock().unwrap().step(emitter.as_ref(), "java", Some("Java 8".into()));
    let java = match &config.java_path {
        // A configured JVM is the player's choice; never download over it.
        Some(path) => java::probe(path).ok_or_else(|| {
            Error::Other(format!("{} is not a Java runtime. Change it in Settings.", path.display()))
        })?,
        None => {
            let (tx, drained) = progress::channel(emitter.clone(), sink.clone());
            let java = java::ensure_java8(&state.http, &state.paths, Some(tx)).await;
            let _ = drained.await;
            java?
        }
    };

    // The signed `void-client` JAR from the release channel (§12.3), or the developer
    // override. Cached by content hash, so a warm prepare verifies and moves on.
    sink.lock().unwrap().step(emitter.as_ref(), "mod", Some("VOID client".into()));
    let (tx, drained) = progress::channel(emitter.clone(), sink.clone());
    let resolved = resolve_mod(state, ModPlatform::for_host(&ctx)?, true, Some(tx)).await;
    let _ = drained.await;
    let resolved = resolved?;
    if let Some(notice) = &resolved.notice {
        tracing::warn!(%notice, "void-client release channel");
    }

    let mut sink = sink.lock().unwrap();
    sink.finish(emitter.as_ref());

    Ok(PrepareReport {
        loadout: loadout.id.to_string(),
        version_id: profile.version_id.clone(),
        files: sink.files(),
        downloaded_bytes: sink.downloaded_bytes(),
        duration_ms: sink.elapsed_ms(),
        java_path: java.path.display().to_string(),
        java_version: java.version,
        mod_version: resolved.version.clone(),
        mod_source: match resolved.source {
            ModSource::Fresh => "fresh",
            ModSource::Cached => "cached",
            ModSource::DevOverride => "dev",
        },
        notice: resolved.notice,
    })
}

/// Start the bridge server, spawn the JVM, and stream `game:*` / `bridge:*` events.
///
/// `server` is the play bar's choice (Figma v1 Ship, rule 2): Launch joins it, and `None`
/// — "Title screen" — launches without joining.
pub async fn launch(
    state: &AppState,
    emitter: Arc<dyn Emitter>,
    loadout_id: &str,
    server: Option<&str>,
) -> Result<LaunchReport, Error> {
    let loadout = super::loadouts::get(state, loadout_id)?;

    if state.session.lock().unwrap().is_none() {
        return Err(Error::NotSignedIn);
    }
    if state.game.lock().unwrap().is_running() {
        return Err(Error::AlreadyRunning);
    }
    // Minecraft access tokens last 24 hours; a launcher left open for a day would
    // otherwise hand the game a dead one, and online-mode servers would refuse the join.
    let session = super::auth::fresh_session(state).await?;
    let config = state.config()?;

    // A profile from a previous `prepare`, so pressing Launch on a warm install does not
    // touch the network at all (§10: "warm launch ≤ 3 s to MC window").
    let ctx = RuleContext::host()?;
    let profile = match install::cached_profile_for(&state.paths, void_core::manifest::LEGACY_FABRIC_LOADER) {
        Some(profile) => profile,
        None => install::prepare(&state.http, &state.paths, &ctx, None, None).await?,
    };
    let mod_jar = resolve_mod(state, ModPlatform::for_host(&ctx)?, false, None).await?;

    let java = match config.java_path.clone() {
        Some(path) => java::probe(&path).ok_or_else(|| {
            Error::Other(format!(
                "{} is not a Java runtime. Change it in Settings, or turn \
                 \"Find Java automatically\" back on.",
                path.display()
            ))
        })?,
        None => java::ensure_java8(&state.http, &state.paths, None).await?,
    };
    if !java.is_java8() {
        return Err(Error::Other(format!(
            "Found Java {}, but Minecraft 1.8.9 needs Java 8.",
            java.version
        )));
    }

    game::launch(
        state.game.clone(),
        emitter,
        LaunchRequest {
            profile,
            paths: config.paths_for(&state.paths),
            store: state.store.clone(),
            loadout,
            presence: state.presence.clone(),
            options: LaunchOptionsSeed {
                session,
                java: java.path,
                max_memory_mb: config.max_memory_mb,
                extra_jvm_args: config.jvm_args.clone(),
                extra_game_args: config.window_args(),
                mod_jar: Some(mod_jar.jar),
                mod_source: mod_jar.source,
                join_server: server.map(str::trim).filter(|s| !s.is_empty()).map(str::to_string),
            },
        },
    )
    .await
    .inspect_err(|_| state.presence.set(crate::presence::Activity::Idle))
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::events::test_support::Recorder;
    use crate::state::scratch_state;

    #[tokio::test]
    async fn prepare_refuses_an_unknown_loadout_before_showing_progress() {
        let state = scratch_state();
        let rec = Recorder::default();
        let emitter: Arc<dyn Emitter> = Arc::new(rec.clone());
        let err = prepare(&state, emitter, "does-not-exist").await.unwrap_err();
        assert!(matches!(err, Error::BadLoadoutId(_) | Error::Loadout(_)));
        assert!(rec.names().is_empty(), "no progress event should have been emitted");
    }

    #[tokio::test]
    async fn launching_without_an_account_is_refused_with_a_readable_sentence() {
        let state = scratch_state();
        let id = super::super::loadouts::active(&state).unwrap().id.to_string();
        let rec: Arc<dyn Emitter> = Arc::new(Recorder::default());
        let err = launch(&state, rec, &id, None).await.unwrap_err();
        assert!(matches!(err, Error::NotSignedIn));
        assert!(err.to_string().starts_with("Not signed in."));
    }

    #[tokio::test]
    async fn launching_an_unknown_loadout_is_refused_before_the_account_check() {
        let state = scratch_state();
        let rec: Arc<dyn Emitter> = Arc::new(Recorder::default());
        let err = launch(&state, rec, "nope", Some("mc.hypixel.net")).await.unwrap_err();
        assert!(!matches!(err, Error::NotSignedIn));
    }

    #[test]
    fn killing_an_idle_game_is_an_error_and_the_log_is_empty() {
        let state = scratch_state();
        assert!(!is_running(&state));
        assert!(matches!(kill(&state), Err(Error::NotRunning)));
        assert!(log_tail(&state, 100).unwrap().is_empty());
    }

    #[test]
    fn the_log_tail_is_bounded_however_much_is_asked_for() {
        let state = scratch_state();
        assert!(log_tail(&state, usize::MAX).unwrap().len() <= 2000);
    }
}
