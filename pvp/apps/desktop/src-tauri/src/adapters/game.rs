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
//! 5. `adapters::online_bridge` — relays cosmetics, emotes and server presence between
//!    the mod and `void-online` for as long as the game runs.
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
use crate::presence::{Activity, Presence};

/// How many lines the log drawer can scroll back through. 2,000 × ~120 chars ≈ 240 KB —
/// the right order for "show me why it crashed" without holding a session of chat spam.
const LOG_CAPACITY: usize = 2000;

/// How often the launch task checks whether Force quit was pressed. `Child::wait` is
/// cancel-safe, so this is a plain poll rather than a channel; 200 ms is imperceptible
/// on a button that is already asking "are you sure".
const KILL_POLL: Duration = Duration::from_millis(200);

/// How long the mod gets to call back before the log says it never did.
///
/// The mod connects from `onInitializeClient`, which Fabric runs early in `startGame` —
/// before the window, let alone a world. So this is not a race with a slow machine; it is
/// generous enough for a cold JVM plus Mixin plus a spinning disk and still far short of
/// the point where a player would have concluded the launcher is broken.
const BRIDGE_HANDSHAKE_GRACE: Duration = Duration::from_secs(45);

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
    pub last_session: Arc<Mutex<Option<SessionSummary>>>,
}

/// The last `session` summary the mod sent — what `game:closed` reports.
#[derive(Debug, Clone, PartialEq)]
pub struct SessionSummary {
    pub played_ms: u64,
    pub fps_avg: f64,
    pub ping_avg: Option<f64>,
    pub server: Option<String>,
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
    /// With the configured game directory applied.
    pub paths: Paths,
    pub store: Store,
    pub loadout: Loadout,
    /// Discord Rich Presence: playing on the server the mod reports, idle on exit.
    pub presence: Presence,
    pub options: LaunchOptionsSeed,
    /// The online client; disabled in a build without a backend, which the relay handles.
    pub online: void_online::Online,
}

/// The half of `LaunchOptions` the launcher decides; the port and token come from the
/// bridge, which does not exist yet when the command builds this.
pub struct LaunchOptionsSeed {
    pub session: void_core::auth::Session,
    pub java: std::path::PathBuf,
    pub max_memory_mb: u32,
    pub extra_jvm_args: Vec<String>,
    /// Window size / fullscreen game arguments.
    pub extra_game_args: Vec<String>,
    pub mod_jar: Option<std::path::PathBuf>,
    /// Where `mod_jar` came from, for the launch log.
    pub mod_source: void_core::release::ModSource,
    /// The server Launch joins (`host` or `host:port`), or `None` for the title screen.
    pub join_server: Option<String>,
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

    // 3. The UI's own subscription to the same bus, which also drives Discord presence.
    let since_ms = now_ms() as i64;
    let playing = PresenceFeed {
        presence: req.presence.clone(),
        loadout: req.loadout.name.clone(),
        since_ms,
    };
    spawn_bridge_forwarder(bridge.subscribe(), emitter.clone(), game.clone(), Some(playing));

    // Cloned before the server moves into `GameState`, for the handshake watchdog below.
    // Cheap: `BridgeServer` is an `Arc` inside, and the clone is dropped when that task
    // ends, well before the session does.
    let bridge_watch = bridge.clone();

    // Kept because `mod_jar` moves into `LaunchOptions` below, and the log line that
    // reports which jar the game is running has to say whether one was installed.
    let configured_jar = req.options.mod_jar.clone();
    let mod_source = req.options.mod_source.clone();
    let join_target = req.options.join_server.clone();
    let spawned_at = SystemTime::now();

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
            extra_game_args: req.options.extra_game_args,
            join_server: req.options.join_server,
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

    // Cosmetics and emotes: mod ⇄ void-online, until the game exits.
    if let Some(bridge) = game.lock().unwrap().bridge.clone() {
        crate::adapters::online_bridge::spawn(bridge, req.online.clone(), running.clone());
    }

    push(&log, emitter.as_ref(), "stdout", format!("[void] bridge on ws://127.0.0.1:{port}"));
    for line in mod_jar_report(&req.paths, configured_jar.as_deref(), &mod_source) {
        push(&log, emitter.as_ref(), line.stream, line.text);
    }
    push(&log, emitter.as_ref(), "stdout", format!("[void] {}", args.join(" ")));

    emit(
        emitter.as_ref(),
        GAME_STARTED,
        &serde_json::json!({ "pid": pid, "loadout": loadout_id, "bridge_port": port }),
    );
    // Until the mod reports a server, show the one Launch was asked to join.
    req.presence.set(Activity::Playing {
        loadout: req.loadout.name.clone(),
        server: join_target.as_deref().map(host_only),
        since_ms,
    });
    let presence = req.presence.clone();
    let crash_paths = req.paths.clone();

    // The one failure on this path that has no other symptom. If no mod ever calls back —
    // because `mods/` holds no `void-client` JAR, or an old one, or one whose natives are
    // for another platform — every other signal still says healthy: `game:started` fired,
    // the process is alive, the log scrolls, Minecraft comes up. The game is simply
    // vanilla, and the launcher's own loadouts, HUD and menu do nothing. Nothing in the
    // launcher says so today, so say it here, in the log the drawer already shows.
    {
        let log = log.clone();
        let emitter = emitter.clone();
        let running = running.clone();
        tokio::spawn(async move {
            tokio::time::sleep(BRIDGE_HANDSHAKE_GRACE).await;
            if running.load(Ordering::SeqCst) && bridge_watch.client_count() == 0 {
                push(
                    &log,
                    emitter.as_ref(),
                    "stderr",
                    format!(
                        "[void] nothing has connected to the bridge on 127.0.0.1:{port} after \
                         {}s — the game is running without the VOID client. Check that a \
                         void-client JAR for this platform is in the mods directory, and \
                         that nothing else in mods/ is stopping Fabric from loading it.",
                        BRIDGE_HANDSHAKE_GRACE.as_secs()
                    ),
                );
            }
        });
    }

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

        let SessionSummary { played_ms, fps_avg, ping_avg, server } =
            last_session.lock().unwrap().clone().unwrap_or(SessionSummary {
                played_ms: started.elapsed().as_millis() as u64,
                fps_avg: 0.0,
                ping_avg: None,
                server: None,
            });

        let crash_tail = (exit_code != 0).then(|| {
            let log = log.lock().unwrap();
            log.iter().rev().take(40).rev().map(|l| l.line.clone()).collect::<Vec<_>>()
        });
        let crash_report = if exit_code != 0 {
            find_crash_report(&crash_paths, spawned_at).map(|p| p.display().to_string())
        } else {
            None
        };
        presence.set(Activity::Idle);

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
                ping_avg,
                server,
                crash_tail,
                crash_report,
            },
        );
    });

    Ok(LaunchReport { pid, bridge_port: port, loadout: req.loadout.id.to_string() })
}

/// What the forwarder needs to keep Discord presence on the server the mod reports.
struct PresenceFeed {
    presence: Presence,
    loadout: String,
    since_ms: i64,
}

/// `mc.hypixel.net:25565` → `mc.hypixel.net`: presence shows a name, not a socket.
fn host_only(target: &str) -> String {
    let t = target.trim();
    match t.rsplit_once(':') {
        Some((host, port)) if !host.contains(':') && port.parse::<u16>().is_ok() => host.to_string(),
        _ => t.to_string(),
    }
}

/// The newest crash report or JVM fatal-error log written since `since`.
///
/// Minecraft writes `crash-reports/crash-<date>-client.txt` for a Java-level crash; the
/// JVM writes `hs_err_pid<pid>.log` in the working directory (the game directory) when
/// it dies natively — a driver crash, an Ultralight fault. Either is what a player
/// should attach to a bug report.
pub fn find_crash_report(paths: &Paths, since: SystemTime) -> Option<std::path::PathBuf> {
    let mut candidates: Vec<(SystemTime, std::path::PathBuf)> = Vec::new();
    let mut scan = |dir: std::path::PathBuf, keep: &dyn Fn(&str) -> bool| {
        if let Ok(entries) = std::fs::read_dir(dir) {
            for path in entries.flatten().map(|e| e.path()) {
                let Some(name) = path.file_name().and_then(|n| n.to_str()) else { continue };
                if !keep(name) {
                    continue;
                }
                if let Ok(modified) = std::fs::metadata(&path).and_then(|m| m.modified()) {
                    if modified >= since {
                        candidates.push((modified, path));
                    }
                }
            }
        }
    };
    scan(paths.crash_reports_dir(), &|n| n.starts_with("crash-") && n.ends_with(".txt"));
    scan(paths.game_dir(), &|n| n.starts_with("hs_err_pid") && n.ends_with(".log"));
    candidates.sort();
    candidates.pop().map(|(_, p)| p)
}

/// Forward the mod's messages to the webview, and remember the last `session` summary
/// so `game:closed` can carry real numbers rather than wall-clock ones.
fn spawn_bridge_forwarder(
    mut bus: tokio::sync::broadcast::Receiver<JavaToRust>,
    emitter: Arc<dyn Emitter>,
    game: Arc<Mutex<GameState>>,
    presence: Option<PresenceFeed>,
) {
    tokio::spawn(async move {
        loop {
            match bus.recv().await {
                // Not a `bridge:*` event — the store owns `hello` (see below) — but the one
                // line that says the link came up at all, next to the "[void] bridge on
                // ws://..." line the spawn already wrote. Without it the log shows the
                // launcher offering a socket and never says whether anything took it.
                Ok(JavaToRust::Hello { ref mc, ref mod_version, .. }) => {
                    if let Ok(log) = game.lock().map(|g| g.log.clone()) {
                        push(
                            &log,
                            emitter.as_ref(),
                            "stdout",
                            format!("[void] mod connected: Minecraft {mc}, void-client {mod_version}"),
                        );
                    }
                }
                Ok(JavaToRust::State { loadout, patch }) => emit(
                    emitter.as_ref(),
                    BRIDGE_STATE,
                    &serde_json::json!({ "t": "state", "loadout": loadout, "patch": patch }),
                ),
                Ok(JavaToRust::Session { fps_avg, played_ms, ping_avg, server, loadout }) => {
                    if let Ok(slot) = game.lock().map(|g| g.last_session.clone()) {
                        *slot.lock().unwrap() =
                            Some(SessionSummary { played_ms, fps_avg, ping_avg, server: server.clone() });
                    }
                    emit(
                        emitter.as_ref(),
                        BRIDGE_SESSION,
                        &serde_json::json!({
                            "t": "session", "fps_avg": fps_avg, "played_ms": played_ms,
                            "ping_avg": ping_avg, "server": server, "loadout": loadout,
                        }),
                    );
                }
                Ok(JavaToRust::Server { host, connected, port }) => {
                    if let Some(feed) = &presence {
                        feed.presence.set(Activity::Playing {
                            loadout: feed.loadout.clone(),
                            server: connected.then(|| host_only(&host)),
                            since_ms: feed.since_ms,
                        });
                    }
                    emit(
                        emitter.as_ref(),
                        BRIDGE_SERVER,
                        &serde_json::json!({
                            "t": "server", "host": host, "connected": connected, "port": port
                        }),
                    )
                }
                // `hud` and unknown tags are the store's business, not the launcher UI's —
                // `sync::pump` has its own subscription for those. So is `hello`; the arm
                // above writes a log line and deliberately emits no `bridge:*` event.
                Ok(_) => {}
                Err(tokio::sync::broadcast::error::RecvError::Lagged(n)) => {
                    tracing::warn!(dropped = n, "bridge forwarder fell behind");
                }
                Err(tokio::sync::broadcast::error::RecvError::Closed) => break,
            }
        }
    });
}

/// One line of the mod-jar report, with the stream it belongs on.
struct ReportLine {
    stream: &'static str,
    text: String,
}

/// Where an installed JAR came from, in words.
fn describe_source(source: &void_core::release::ModSource) -> &'static str {
    match source {
        void_core::release::ModSource::Fresh => "the release channel",
        void_core::release::ModSource::Cached => "the release channel (cached, verified)",
        void_core::release::ModSource::DevOverride => "the developer override",
    }
}

/// What the game is actually about to run, said out loud at every launch.
///
/// The failure this exists for has no other symptom. A `void-client` jar left in `mods/`
/// by an earlier run connects to the bridge, answers `hello` and behaves like a healthy
/// client while executing code from days ago — so `game:started` fires, the process is
/// alive, the handshake watchdog stays quiet, and every measurement taken against it is
/// of the wrong binary. The launcher cannot tell what the jar *should* contain, so the
/// only defence is to name the file and its age and let a person notice.
///
/// Three cases, and the middle one is the bug that produced this function:
///
/// - a JAR resolved (release channel or developer override): the install step ran and
///   overwrote `mods/`, so the jar is current by construction. One `stdout` line naming
///   the file and where it came from.
/// - nothing resolved, a jar present: nothing was installed. **stderr**, because the jar
///   is of unknown provenance and unknown age, and it will run anyway.
/// - nothing resolved, nothing present: the game will be vanilla. The 45-second watchdog
///   below would eventually say so; this says it immediately and explains why.
fn mod_jar_report(
    paths: &Paths,
    configured: Option<&std::path::Path>,
    source: &void_core::release::ModSource,
) -> Vec<ReportLine> {
    let found = launch::installed_mod_jars(paths);
    let mut out = Vec::new();
    match (configured, found.first()) {
        (Some(_), Some(jar)) => out.push(ReportLine {
            stream: "stdout",
            text: format!(
                "[void] mod jar: {} ({}), installed this launch from {} ({})",
                jar.name(),
                describe_age(jar.age()),
                describe_source(source),
                configured.map(|c| c.display().to_string()).unwrap_or_default()
            ),
        }),
        // Installed, then vanished — a jar removed between the copy and this read. Odd
        // enough to be worth a line rather than an empty report.
        (Some(source), None) => out.push(ReportLine {
            stream: "stderr",
            text: format!(
                "[void] mod jar: installed {} but mods/ now holds no void-client jar",
                source.display()
            ),
        }),
        (None, Some(jar)) => out.push(ReportLine {
            stream: "stderr",
            text: format!(
                "[void] mod jar: no VOID client was resolved, so nothing was installed this \
                 launch. The game will run {} ({}), left in mods/ by an earlier run — it may \
                 be older than the launcher.",
                jar.name(),
                describe_age(jar.age())
            ),
        }),
        (None, None) => out.push(ReportLine {
            stream: "stderr",
            text: "[void] mod jar: no VOID client was resolved and mods/ holds no void-client \
                   jar. The game will run vanilla: no loadouts, no HUD, no menu."
                .into(),
        }),
    }
    // More than one is the state `install_mod_jar` sweeps away, so seeing it here means
    // the sweep did not run — which is exactly the `mod_jar`-unset case. Fabric picks one
    // of them and does not say which.
    if found.len() > 1 {
        out.push(ReportLine {
            stream: "stderr",
            text: format!(
                "[void] mods/ holds {} void-client jars ({}); Fabric will load one of them \
                 and will not say which",
                found.len(),
                found.iter().map(|j| j.name().into_owned()).collect::<Vec<_>>().join(", ")
            ),
        });
    }
    out
}

/// A jar's age in the coarsest unit that is still informative.
fn describe_age(age: Option<Duration>) -> String {
    let Some(age) = age else {
        return "age unknown".into();
    };
    let secs = age.as_secs();
    match secs {
        0..=90 => "just built".into(),
        91..=5399 => format!("{} min old", secs / 60),
        5400..=86_399 => format!("{} h old", secs / 3600),
        _ => format!("{} days old", secs / 86_400),
    }
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

    /// A `Paths` rooted at a scratch directory holding the named jars.
    fn paths_with(names: &[&str]) -> (std::path::PathBuf, Paths) {
        let root = std::env::temp_dir()
            .join(format!("void-modjar-{}-{:?}", std::process::id(), std::thread::current().id()));
        let _ = std::fs::remove_dir_all(&root);
        let paths = Paths::at(&root);
        let mods = paths.mods_dir();
        std::fs::create_dir_all(&mods).unwrap();
        for n in names {
            std::fs::write(mods.join(n), b"x").unwrap();
        }
        (root, paths)
    }

    #[test]
    fn the_launch_report_names_the_jar_the_game_will_actually_run() {
        let (root, paths) = paths_with(&["void-client-0.1.0-macos-x64.jar"]);
        let source = std::path::PathBuf::from("/build/libs/void-client-0.1.0-macos-x64.jar");

        let release = void_core::release::ModSource::Cached;
        let installed = mod_jar_report(&paths, Some(&source), &release);
        assert_eq!(installed.len(), 1);
        assert_eq!(installed[0].stream, "stdout", "an installed jar is not a warning");
        assert!(installed[0].text.contains("void-client-0.1.0-macos-x64.jar"));
        assert!(installed[0].text.contains("installed this launch"));
        assert!(installed[0].text.contains("release channel"));

        // The case that ran a three-day-old client for three days. Nothing was installed,
        // Fabric loads what is already there, and every other signal says healthy — so this
        // line is the only thing that says which binary is running.
        let skipped = mod_jar_report(&paths, None, &release);
        assert_eq!(skipped[0].stream, "stderr", "an uninstalled jar of unknown age is a warning");
        assert!(skipped[0].text.contains("nothing was installed"));
        assert!(skipped[0].text.contains("void-client-0.1.0-macos-x64.jar"));

        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn the_launch_report_says_when_the_game_will_be_vanilla_or_ambiguous() {
        let (root, paths) = paths_with(&[]);
        let none = mod_jar_report(&paths, None, &void_core::release::ModSource::Fresh);
        assert_eq!(none[0].stream, "stderr");
        assert!(none[0].text.contains("run vanilla"));
        let _ = std::fs::remove_dir_all(&root);

        // Two jars is the state `install_mod_jar`'s sweep exists to prevent, so seeing it
        // means the sweep did not run — and Fabric does not say which one it picked.
        let (root2, paths2) = paths_with(&[
            "void-client-0.1.0-macos-x64.jar",
            "void-client-0.1.0-macos-arm64.jar",
        ]);
        let two = mod_jar_report(&paths2, None, &void_core::release::ModSource::Fresh);
        assert_eq!(two.len(), 2, "the ambiguity gets a line of its own");
        assert!(two[1].text.contains("2 void-client jars"));
        let _ = std::fs::remove_dir_all(&root2);
    }

    #[test]
    fn the_crash_report_is_the_newest_one_written_this_session() {
        let (root, paths) = paths_with(&[]);
        let before = SystemTime::now() - Duration::from_secs(5);
        let reports = paths.crash_reports_dir();
        std::fs::create_dir_all(&reports).unwrap();
        assert_eq!(find_crash_report(&paths, before), None);
        std::fs::write(reports.join("crash-2026-10-15_10.00.00-client.txt"), "x").unwrap();
        std::thread::sleep(Duration::from_millis(30));
        std::fs::write(paths.game_dir().join("hs_err_pid42.log"), "x").unwrap();
        let found = find_crash_report(&paths, before).unwrap();
        assert!(found.ends_with("hs_err_pid42.log"), "{}", found.display());
        // Nothing written after `since` means nothing to show.
        assert_eq!(find_crash_report(&paths, SystemTime::now() + Duration::from_secs(60)), None);
        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn presence_shows_a_host_not_a_socket() {
        assert_eq!(host_only("mc.hypixel.net:25565"), "mc.hypixel.net");
        assert_eq!(host_only(" play.example "), "play.example");
        assert_eq!(host_only("[::1]:25565"), "[::1]:25565");
    }

    #[test]
    fn an_age_is_described_in_the_coarsest_useful_unit() {
        assert_eq!(describe_age(None), "age unknown");
        assert_eq!(describe_age(Some(Duration::from_secs(3))), "just built");
        assert_eq!(describe_age(Some(Duration::from_secs(600))), "10 min old");
        assert_eq!(describe_age(Some(Duration::from_secs(7200))), "2 h old");
        assert_eq!(describe_age(Some(Duration::from_secs(3 * 86_400))), "3 days old");
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
        spawn_bridge_forwarder(tx.subscribe(), Arc::new(rec.clone()), game.clone(), None);

        tx.send(JavaToRust::Hello {
            // Never a literal: `hello.v` is whatever `void_bridge` currently speaks, and
            // the forwarder has to keep ignoring it across a protocol bump.
            v: void_bridge::PROTOCOL_VERSION,
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
            ping_avg: Some(22.0),
            server: Some("mc.hypixel.net".into()),
            loadout: None,
        })
        .unwrap();

        for _ in 0..50 {
            if rec.names().len() >= 3 {
                break;
            }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }

        let names = rec.names();
        assert!(names.contains(&BRIDGE_SERVER.to_string()));
        assert!(names.contains(&BRIDGE_SESSION.to_string()));
        // `hello` is the store's business, not the UI's: no `bridge:*` event comes of it.
        // It does write one line into the game log, which is how the drawer shows that
        // something actually took the socket the launcher opened.
        let bridge_events = names.iter().filter(|n| n.starts_with("bridge:")).count();
        assert_eq!(bridge_events, 2, "{names:?}");
        assert_eq!(names.iter().filter(|n| *n == GAME_LOG).count(), 1, "{names:?}");
        assert!(
            game.lock()
                .unwrap()
                .tail(1)
                .first()
                .is_some_and(|l| l.contains("mod connected") && l.contains("0.1.0")),
            "the handshake should be visible in the log drawer"
        );

        // The session summary is remembered for `game:closed`.
        let slot = game.lock().unwrap().last_session.clone();
        let remembered = slot.lock().unwrap().clone();
        assert_eq!(
            remembered,
            Some(SessionSummary {
                played_ms: 60_000,
                fps_avg: 142.0,
                ping_avg: Some(22.0),
                server: Some("mc.hypixel.net".into()),
            })
        );
    }
}
