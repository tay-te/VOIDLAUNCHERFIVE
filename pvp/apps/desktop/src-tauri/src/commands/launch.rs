//! `prepare` · `launch` · `game_kill` · `game_log_tail`
//!
//! `prepare` is idempotent by construction: `void_core::install::prepare` verifies every
//! file's SHA-1 against the manifest and skips what is already on disk or in the
//! hash-addressed cache, so pressing Launch twice costs a manifest fetch and nothing
//! else. That is what lets the button call it unconditionally instead of maintaining an
//! "is it installed" flag that can go stale.

use std::sync::{Arc, Mutex};

use void_core::manifest::RuleContext;
use void_core::{install, java};

use crate::adapters::game::{self, LaunchOptionsSeed, LaunchRequest};
use crate::adapters::progress::{self, PrepareProgressSink};
use crate::error::Error;
use crate::events::Emitter;
use crate::models::{LaunchReport, PrepareReport};
use crate::state::AppState;

/// Download the game, Legacy Fabric, Java 8 and the mod JAR for a loadout.
pub async fn prepare(
    state: &AppState,
    emitter: Arc<dyn Emitter>,
    loadout_id: &str,
) -> Result<PrepareReport, Error> {
    // Fail before any progress event so the bar never appears for a loadout that is
    // already gone.
    let loadout = super::loadouts::get(state, loadout_id)?;
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
    let (tx, drained) = progress::channel(emitter.clone(), sink.clone());
    let java = java::ensure_java8(&state.http, &state.paths, Some(tx)).await;
    let _ = drained.await;
    let java = java?;

    // TODO(integrate): §12.3 — download the signed `void-client` JAR from the release
    // channel. There is no release channel and no signing key yet (§16.5), so the mod
    // JAR is whatever `config.json` points at; `launch` installs it into `mods/`.
    // `void-core` owns the fetch when the channel exists.
    if config.mod_jar.is_some() {
        sink.lock().unwrap().step(emitter.as_ref(), "mod", Some("void-client".into()));
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
    })
}

/// Start the bridge server, spawn the JVM, and stream `game:*` / `bridge:*` events.
/// Minecraft's own default port, which is what a host with no `:port` means.
const DEFAULT_PORT: u16 = 25565;

/// A join target from the two optional arguments, or an error naming the host that failed.
///
/// **Refused rather than dropped.** A player who pressed Join on a server and got the title
/// screen would conclude the button does not work; one who is told the address is not usable
/// can fix it. Silently launching without the argument is the worse of the two, and it is the
/// one that happens by default if this returns `Option`.
fn join_target(
    server: Option<&str>,
    port: Option<u16>,
) -> Result<Option<void_core::launch::JoinTarget>, Error> {
    let Some(host) = server else { return Ok(None) };
    // A host carrying its own `:port` is what a player pastes, so it is split here rather than
    // demanded of the caller. Only a trailing numeric segment counts: an IPv6 literal is all
    // colons and must not be cut in half.
    let (host, port) = match port {
        Some(port) => (host, port),
        None => match host.rsplit_once(':') {
            Some((left, right)) if right.parse::<u16>().is_ok() && !left.contains(':') => {
                (left, right.parse::<u16>().unwrap_or(DEFAULT_PORT))
            }
            _ => (host, DEFAULT_PORT),
        },
    };
    void_core::launch::JoinTarget::new(host, port).map(Some).ok_or_else(|| {
        Error::Other(format!("`{host}` is not a server address VOID can connect to."))
    })
}

pub async fn launch(
    state: &AppState,
    emitter: Arc<dyn Emitter>,
    loadout_id: &str,
    server: Option<&str>,
    port: Option<u16>,
) -> Result<LaunchReport, Error> {
    let loadout = super::loadouts::get(state, loadout_id)?;
    // Before anything expensive: a bad address should not cost a Java probe and an install
    // check first, and the same ordering argument `prepare` opens with applies here.
    let join = join_target(server, port)?;

    let session = state.session.lock().unwrap().clone().ok_or(Error::NotSignedIn)?;
    let config = state.config()?;

    // A profile from a previous `prepare`, so pressing Launch on a warm install does not
    // touch the network at all (§10: "warm launch ≤ 3 s to MC window").
    let ctx = RuleContext::host()?;
    let profile = match install::cached_profile(&state.paths) {
        Some(profile) => profile,
        None => install::prepare(&state.http, &state.paths, &ctx, None, None).await?,
    };

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
            paths: state.paths.clone(),
            store: state.store.clone(),
            loadout,
            options: LaunchOptionsSeed {
                session,
                java: java.path,
                max_memory_mb: config.max_memory_mb,
                extra_jvm_args: config.jvm_args.clone(),
                mod_jar: config.mod_jar.clone(),
                join,
            },
        },
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
        let err = launch(&state, rec, &id, None, None).await.unwrap_err();
        assert!(matches!(err, Error::NotSignedIn));
        assert!(err.to_string().starts_with("Not signed in."));
    }

    #[tokio::test]
    async fn launching_an_unknown_loadout_is_refused_before_the_account_check() {
        let state = scratch_state();
        let rec: Arc<dyn Emitter> = Arc::new(Recorder::default());
        let err = launch(&state, rec, "nope", None, None).await.unwrap_err();
        assert!(!matches!(err, Error::NotSignedIn));
    }

    #[test]
    fn a_pasted_address_carrying_its_own_port_is_split_rather_than_refused() {
        // What a player pastes out of a server list. Only a trailing *numeric* segment is a
        // port: an IPv6 literal is all colons and must not be cut in half, which is what the
        // `!left.contains(':')` guard is for.
        let target = join_target(Some("na.minemen.club:25566"), None).unwrap().unwrap();
        assert_eq!(target.host(), "na.minemen.club");
        assert_eq!(target.port(), 25566);

        let plain = join_target(Some("mc.hypixel.net"), None).unwrap().unwrap();
        assert_eq!(plain.port(), DEFAULT_PORT);

        let six = join_target(Some("::1"), None).unwrap().unwrap();
        assert_eq!(six.host(), "::1");
        assert_eq!(six.port(), DEFAULT_PORT);

        // An explicit port wins over one in the string, because the caller that passes one has
        // a field the player filled in.
        let explicit = join_target(Some("mc.hypixel.net"), Some(25599)).unwrap().unwrap();
        assert_eq!(explicit.port(), 25599);

        assert!(join_target(None, None).unwrap().is_none());
    }

    #[test]
    fn a_bad_address_is_an_error_rather_than_a_launch_without_one() {
        // Refused, not dropped. A player who pressed Join and got the title screen would
        // conclude the button does not work; one who is told the address is unusable can fix it.
        let err = join_target(Some("-Xmx1G"), None).unwrap_err();
        assert!(err.to_string().contains("not a server address"));
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
