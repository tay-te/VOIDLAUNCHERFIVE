//! The running game: the bridge server, the JVM, the log ring and the session summary.
//!
//! This is the one place the launcher orchestrates all three core crates at once, and
//! the order matters:
//!
//! 1. `void_bridge::BridgeServer::bind` — first, because the port and token it mints are
//!    JVM arguments. Its `InitSource` is `void_core::sync::StoreInit`, which reads the
//!    library on every `hello` rather than caching a snapshot, so a mod reconnecting
//!    after a tray switch gets the loadout that is active *now*.
//! 2. `void_core::sync::pump` — folds `state`, `hud` and `session` back into the store.
//!    Java is authoritative for live state and tells Rust afterwards (§6.1); this is the
//!    afterwards. It runs on its own task with its own `Store`.
//! 3. A second subscriber on the same bus forwards every message to the webview as
//!    `bridge:state` / `bridge:session` / `bridge:server`. Two independent broadcast
//!    receivers, so the UI falling behind cannot stall persistence.
//! 4. `void_core::launch::launch` — spawns the JVM.
//!
//! The bridge server is kept alive in `GameState` for the whole session: dropping the
//! last clone stops the listener, and the mod reconnects with backoff (§6.9), so an
//! early drop would look like a flapping link.

use std::collections::VecDeque;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use void_bridge::{BridgeServer, JavaToRust, RustToJava};
use void_core::launch::{self, LaunchOptions, Stream};
use void_core::manifest::LaunchProfile;
use void_core::Paths;
use void_loadout::{Loadout, Store};

use crate::error::Error;
use crate::events::{
    emit, Emitter, BRIDGE_SERVER, BRIDGE_SESSION, BRIDGE_STATE, GAME_CLOSED, GAME_LOG, GAME_STARTED,
};
use crate::models::{LaunchReport, LogLine, SessionStats};

/// How many lines the log drawer can scroll back through. 2,000 × ~120 chars ≈ 240 KB —
/// the right order for "show me why it crashed" without holding a session of chat spam.
const LOG_CAPACITY: usize = 2000;

/// How often the launch task checks whether Force quit was pressed. `Child::wait` is
/// cancel-safe, so this is a plain poll rather than a channel; 200 ms is imperceptible
/// on a button that is already asking "are you sure".
const KILL_POLL: Duration = Duration::from_millis(200);

#[derive(Default)]
pub struct GameState {
    pub running: Arc<AtomicBool>,
    pub kill_requested: Arc<AtomicBool>,
    pub log: Arc<Mutex<VecDeque<LogLine>>>,
    pub pid: Option<u32>,
    pub loadout: Option<String>,
    /// Kept alive for the session; see the module note.
    pub bridge: Option<BridgeServer>,
    /// Last `session` summary the mod sent, so `game:closed` carries real numbers.
    pub last_session: Arc<Mutex<Option<(u64, f64, Option<String>)>>>,
}

impl GameState {
    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }

    pub fn bridge_port(&self) -> Option<u16> {
        self.bridge.as_ref().map(|b| b.port())
    }

    pub fn tail(&self, lines: usize) -> Vec<String> {
        let log = self.log.lock().unwrap();
        log.iter().rev().take(lines).rev().map(|l| l.line.clone()).collect()
    }

    /// Push a loadout switch to a running game (§8.2 — the tray must hot-swap
    /// mid-session). A no-op when nothing is connected, which is not an error: the mod
    /// gets the current loadout in `init` when it next connects.
    pub fn push_loadout(&self, loadout: &Loadout) {
        if let Some(bridge) = &self.bridge {
            let msg = RustToJava::Loadout { loadout: Box::new(loadout.clone()) };
            if let Err(e) = bridge.send(&msg) {
                tracing::warn!(error = %e, "could not push the loadout to the game");
            }
        }
    }

    /// Push changed global settings to a running game.
    pub fn push_settings(&self, settings: &void_loadout::GlobalSettings) {
        if let Some(bridge) = &self.bridge {
            let msg = RustToJava::Settings { settings: settings.clone() };
            if let Err(e) = bridge.send(&msg) {
                tracing::warn!(error = %e, "could not push settings to the game");
            }
        }
    }
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

/// Everything `launch` needs that the command layer resolves for it.
pub struct LaunchRequest {
    pub profile: LaunchProfile,
    pub paths: Paths,
    pub store: Store,
    pub loadout: Loadout,
    pub options: LaunchOptionsSeed,
}

/// The half of `LaunchOptions` the launcher decides; the port and token come from the
/// bridge, which does not exist yet when the command builds this.
pub struct LaunchOptionsSeed {
    pub session: void_core::auth::Session,
    pub java: std::path::PathBuf,
    pub max_memory_mb: u32,
    pub extra_jvm_args: Vec<String>,
    pub mod_jar: Option<std::path::PathBuf>,
}

pub async fn launch(
    game: Arc<Mutex<GameState>>,
    emitter: Arc<dyn Emitter>,
    req: LaunchRequest,
) -> Result<LaunchReport, Error> {
    {
        let g = game.lock().unwrap();
        if g.is_running() {
            return Err(Error::AlreadyRunning);
        }
    }

    let loadout_id = req.loadout.id.to_string();

    // 1. The bridge, first: its port and token are JVM arguments.
    let bridge =
        BridgeServer::bind(void_core::sync::StoreInit::new(req.store.clone())).await?;
    let port = bridge.port();
    let token = bridge.token().to_string();

    // 2. Persistence: every inbound message folded back into the store.
    tokio::spawn(void_core::sync::pump(bridge.clone(), req.store.clone()));

    // 3. The UI's own subscription to the same bus.
    spawn_bridge_forwarder(bridge.subscribe(), emitter.clone(), game.clone());

    // 4. The JVM.
    let mut process = launch::launch(
        &req.profile,
        &req.paths,
        &LaunchOptions {
            session: req.options.session,
            java: req.options.java,
            max_memory_mb: req.options.max_memory_mb,
            extra_jvm_args: req.options.extra_jvm_args,
            bridge_port: port,
            bridge_token: token,
            mod_jar: req.options.mod_jar,
        },
    )
    .await?;

    let pid = process.pid().unwrap_or(0);
    let args = process.args.clone();

    let (running, kill_requested, log, last_session) = {
        let mut g = game.lock().unwrap();
        g.running.store(true, Ordering::SeqCst);
        g.kill_requested.store(false, Ordering::SeqCst);
        g.pid = Some(pid);
        g.loadout = Some(loadout_id.clone());
        g.bridge = Some(bridge);
        g.log.lock().unwrap().clear();
        *g.last_session.lock().unwrap() = None;
        (g.running.clone(), g.kill_requested.clone(), g.log.clone(), g.last_session.clone())
    };

    push(&log, emitter.as_ref(), "stdout", format!("[void] bridge on ws://127.0.0.1:{port}"));
    push(&log, emitter.as_ref(), "stdout", format!("[void] {}", args.join(" ")));

    emit(
        emitter.as_ref(),
        GAME_STARTED,
        &serde_json::json!({ "pid": pid, "loadout": loadout_id, "bridge_port": port }),
    );

    // Drain the game's output on its own task. Taking the receiver out of the process
    // leaves the process free to be `wait`ed and `kill`ed below without a split borrow.
    let (dummy_tx, dummy_rx) = tokio::sync::mpsc::channel::<launch::LogLine>(1);
    drop(dummy_tx);
    let mut logs = std::mem::replace(&mut process.logs, dummy_rx);
    {
        let log = log.clone();
        let emitter = emitter.clone();
        tokio::spawn(async move {
            while let Some(line) = logs.recv().await {
                let stream = match line.stream {
                    Stream::Stdout => "stdout",
                    Stream::Stderr => "stderr",
                };
                push(&log, emitter.as_ref(), stream, line.text);
            }
        });
    }

    let started = Instant::now();
    let game_for_task = game.clone();
    tokio::spawn(async move {
        let exit_code = loop {
            if kill_requested.swap(false, Ordering::SeqCst) {
                tracing::info!("force quit requested");
                let _ = process.kill().await;
            }
            // Both branches borrow disjoint values, and `Child::wait` is cancel-safe, so
            // the poll costs nothing and the kill above gets its turn between rounds.
            tokio::select! {
                res = process.wait() => break res.unwrap_or(-1),
                _ = tokio::time::sleep(KILL_POLL) => {}
            }
        };

        running.store(false, Ordering::SeqCst);

        let (played_ms, fps_avg, server) = last_session
            .lock()
            .unwrap()
            .clone()
            .unwrap_or((started.elapsed().as_millis() as u64, 0.0, None));

        let crash_tail = (exit_code != 0).then(|| {
            let log = log.lock().unwrap();
            log.iter().rev().take(40).rev().map(|l| l.line.clone()).collect::<Vec<_>>()
        });

        {
            let mut g = game_for_task.lock().unwrap();
            g.pid = None;
            // Dropping the last clone stops the listener; the session is over, so this
            // is the right moment rather than an early one.
            g.bridge = None;
        }

        emit(
            emitter.as_ref(),
            GAME_CLOSED,
            &SessionStats {
                code: exit_code,
                loadout: loadout_id,
                played_ms,
                fps_avg,
                server,
                crash_tail,
            },
        );
    });

    Ok(LaunchReport { pid, bridge_port: port, loadout: req.loadout.id.to_string() })
}

/// Forward the mod's messages to the webview, and remember the last `session` summary
/// so `game:closed` can carry real numbers rather than wall-clock ones.
fn spawn_bridge_forwarder(
    mut bus: tokio::sync::broadcast::Receiver<JavaToRust>,
    emitter: Arc<dyn Emitter>,
    game: Arc<Mutex<GameState>>,
) {
    tokio::spawn(async move {
        loop {
            match bus.recv().await {
                Ok(JavaToRust::State { loadout, patch }) => emit(
                    emitter.as_ref(),
                    BRIDGE_STATE,
                    &serde_json::json!({ "t": "state", "loadout": loadout, "patch": patch }),
                ),
                Ok(JavaToRust::Session { fps_avg, played_ms, server, loadout }) => {
                    if let Ok(mut slot) = game.lock().map(|g| g.last_session.clone()) {
                        *slot.lock().unwrap() = Some((played_ms, fps_avg, server.clone()));
                    }
                    emit(
                        emitter.as_ref(),
                        BRIDGE_SESSION,
                        &serde_json::json!({
                            "t": "session", "fps_avg": fps_avg, "played_ms": played_ms,
                            "server": server, "loadout": loadout,
                        }),
                    );
                }
                Ok(JavaToRust::Server { host, connected, port }) => emit(
                    emitter.as_ref(),
                    BRIDGE_SERVER,
                    &serde_json::json!({
                        "t": "server", "host": host, "connected": connected, "port": port
                    }),
                ),
                // `hello`, `hud` and unknown tags are the store's business, not the
                // launcher UI's — `sync::pump` has its own subscription for those.
                Ok(_) => {}
                Err(tokio::sync::broadcast::error::RecvError::Lagged(n)) => {
                    tracing::warn!(dropped = n, "bridge forwarder fell behind");
                }
                Err(tokio::sync::broadcast::error::RecvError::Closed) => break,
            }
        }
    });
}

fn push(log: &Arc<Mutex<VecDeque<LogLine>>>, emitter: &dyn Emitter, stream: &'static str, line: String) {
    let entry = LogLine { stream, line, ts_ms: now_ms() };
    {
        let mut buf = log.lock().unwrap();
        if buf.len() == LOG_CAPACITY {
            buf.pop_front();
        }
        buf.push_back(entry.clone());
    }
    emit(emitter, GAME_LOG, &entry);
}

/// Force quit. The launch task notices within [`KILL_POLL`].
pub fn kill(game: &GameState) -> Result<(), Error> {
    if !game.is_running() {
        return Err(Error::NotRunning);
    }
    game.kill_requested.store(true, Ordering::SeqCst);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::events::test_support::Recorder;

    #[test]
    fn the_log_ring_is_capped_and_tails_from_the_end() {
        let state = GameState::default();
        let rec = Recorder::default();
        for i in 0..(LOG_CAPACITY + 50) {
            push(&state.log, &rec, "stdout", format!("line {i}"));
        }
        assert_eq!(state.log.lock().unwrap().len(), LOG_CAPACITY);
        assert_eq!(state.tail(1), vec![format!("line {}", LOG_CAPACITY + 49)]);
        assert_eq!(state.tail(3).len(), 3);
    }

    #[test]
    fn killing_a_stopped_game_is_an_error_rather_than_a_silent_no_op() {
        let state = GameState::default();
        assert!(matches!(kill(&state), Err(Error::NotRunning)));
        assert!(!state.kill_requested.load(Ordering::SeqCst));
    }

    #[test]
    fn kill_sets_the_flag_the_launch_task_polls() {
        let state = GameState::default();
        state.running.store(true, Ordering::SeqCst);
        kill(&state).unwrap();
        assert!(state.kill_requested.load(Ordering::SeqCst));
    }

    #[test]
    fn pushing_to_a_game_that_is_not_running_is_a_no_op() {
        let state = GameState::default();
        // No bridge, no panic: the mod gets everything in `init` when it connects.
        state.push_loadout(&void_loadout::defaults::sword_pvp());
        state.push_settings(&void_loadout::GlobalSettings::factory());
    }

    #[tokio::test]
    async fn the_forwarder_translates_only_the_three_ui_facing_messages() {
        let (tx, _) = tokio::sync::broadcast::channel(16);
        let rec = Recorder::default();
        let game = Arc::new(Mutex::new(GameState::default()));
        spawn_bridge_forwarder(tx.subscribe(), Arc::new(rec.clone()), game.clone());

        tx.send(JavaToRust::Hello {
            v: 1,
            mc: "1.8.9".into(),
            mod_version: "0.1.0".into(),
            token: "t".into(),
        })
        .unwrap();
        tx.send(JavaToRust::Server {
            host: "mc.hypixel.net".into(),
            connected: true,
            port: None,
        })
        .unwrap();
        tx.send(JavaToRust::Session {
            fps_avg: 142.0,
            played_ms: 60_000,
            server: Some("mc.hypixel.net".into()),
            loadout: None,
        })
        .unwrap();

        for _ in 0..50 {
            if rec.names().len() >= 2 {
                break;
            }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }

        let names = rec.names();
        assert!(names.contains(&BRIDGE_SERVER.to_string()));
        assert!(names.contains(&BRIDGE_SESSION.to_string()));
        // `hello` is the store's business, not the UI's.
        assert_eq!(names.len(), 2, "{names:?}");

        // The session summary is remembered for `game:closed`.
        let slot = game.lock().unwrap().last_session.clone();
        let remembered = slot.lock().unwrap().clone();
        assert_eq!(remembered, Some((60_000, 142.0, Some("mc.hypixel.net".into()))));
    }
}
