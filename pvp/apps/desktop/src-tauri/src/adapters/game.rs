//! TODO(integrate): two crates meet here and neither exists yet.
//!
//! - `void-core` (§12.5) builds the JVM args — including `-Dvoid.port` and
//!   `-Dvoid.token` — spawns the JVM and drains stdout into a ring buffer.
//! - `void-bridge` (§7) binds `ws://127.0.0.1:<port>`, mints the per-spawn token,
//!   rejects a `hello` carrying the wrong one, and hands us the `state` / `session` /
//!   `server` messages we forward to the web side as `bridge:*` events.
//!
//! What is real here:
//!
//! - **The port and the token.** The port comes from an actual bind on 127.0.0.1:0, so
//!   it is a port nothing else holds; the token is 32 random hex characters. Both are
//!   what `void-core` would pass to the JVM, and `launch` returns the port so the
//!   Settings screen can show it.
//! - **The log ring buffer**, capped, shared, and drained by the log drawer.
//! - **The session lifecycle**: `game:started`, a stream of `game:log`, then
//!   `game:closed` carrying the stats the Play screen shows when the window comes back
//!   from the tray.
//!
//! What is simulated: the JVM itself. `LaunchReport::pid` is 0 and the first log line
//! says so, so nothing downstream can mistake a stand-in run for a real one.

use std::collections::VecDeque;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::error::Error;
use crate::events::{
    emit, Emitter, BRIDGE_SERVER, BRIDGE_SESSION, BRIDGE_STATE, GAME_CLOSED, GAME_LOG, GAME_STARTED,
};
use crate::models::{LaunchReport, LogLine, SessionStats};

/// How many lines the log drawer can scroll back through. 2000 × ~120 chars ≈ 240 KB,
/// which is the right order for "show me why it crashed" without holding a session's
/// worth of chat spam in memory.
const LOG_CAPACITY: usize = 2000;

/// Everything about the currently running (or last-run) game.
#[derive(Default)]
pub struct Game {
    pub running: Arc<AtomicBool>,
    pub log: Arc<Mutex<VecDeque<String>>>,
    pub pid: Option<u32>,
    pub bridge_port: Option<u16>,
    pub loadout: Option<String>,
    /// Last `session` summary the mod sent, so `game:closed` can carry real numbers.
    pub last_session: Arc<Mutex<Option<(u64, f64, Option<String>)>>>,
}

impl Game {
    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }

    pub fn tail(&self, lines: usize) -> Vec<String> {
        let log = self.log.lock().unwrap();
        log.iter().rev().take(lines).rev().cloned().collect()
    }
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

fn push_log(game: &Game, emitter: &dyn Emitter, stream: &'static str, line: impl Into<String>) {
    let line = line.into();
    {
        let mut log = game.log.lock().unwrap();
        if log.len() == LOG_CAPACITY {
            log.pop_front();
        }
        log.push_back(line.clone());
    }
    emit(
        emitter,
        GAME_LOG,
        &LogLine {
            stream,
            line,
            ts_ms: now_ms(),
        },
    );
}

/// Reserve a localhost port by binding and immediately dropping the listener.
///
/// TODO(integrate): `void-bridge` should hand back a *bound* listener instead — this
/// leaves a window in which something else could take the port. Acceptable while the
/// JVM on the other end is simulated; not acceptable once it is real.
pub fn reserve_bridge_port() -> Result<u16, Error> {
    let listener = std::net::TcpListener::bind(("127.0.0.1", 0)).map_err(Error::other)?;
    let port = listener.local_addr().map_err(Error::other)?.port();
    Ok(port)
}

/// 32 hex characters, per-spawn. The mod echoes it in `hello`; a mismatch closes the
/// socket (§6.9).
pub fn mint_session_token() -> String {
    use rand::Rng;
    let mut rng = rand::thread_rng();
    (0..32)
        .map(|_| std::char::from_digit(rng.gen_range(0..16), 16).unwrap())
        .collect()
}

/// The JVM argument list `void-core` will build. Written out here because it is the
/// one part of the launch contract the desktop side genuinely owns a stake in: the two
/// `-Dvoid.*` properties are the seam of §7, and `launch` must not silently drop them.
pub fn jvm_args(ram_mb: u32, port: u16, token: &str) -> Vec<String> {
    vec![
        format!("-Xmx{ram_mb}M"),
        format!("-Xms{}M", (ram_mb / 2).max(512)),
        "-XX:+UnlockExperimentalVMOptions".into(),
        "-XX:+UseG1GC".into(),
        "-XX:G1NewSizePercent=20".into(),
        "-XX:MaxGCPauseMillis=50".into(),
        format!("-Dvoid.port={port}"),
        format!("-Dvoid.token={token}"),
    ]
}

/// Start a session. Emits `game:started`, then log lines, then `game:closed`.
///
/// Replace the body with: build args via `void_core`, start `void_bridge`'s server on
/// `port`, spawn the JVM, and forward the bridge's inbound messages to `bridge:*`.
#[allow(clippy::too_many_arguments)]
pub async fn launch(
    game: Arc<Mutex<Game>>,
    emitter: Arc<dyn Emitter>,
    loadout: String,
    ram_mb: u32,
    java_path: String,
) -> Result<LaunchReport, Error> {
    {
        let g = game.lock().unwrap();
        if g.is_running() {
            return Err(Error::AlreadyRunning);
        }
    }

    let port = reserve_bridge_port()?;
    let token = mint_session_token();
    let args = jvm_args(ram_mb, port, &token);

    let (running, log, last_session) = {
        let mut g = game.lock().unwrap();
        g.running.store(true, Ordering::SeqCst);
        g.pid = Some(0);
        g.bridge_port = Some(port);
        g.loadout = Some(loadout.clone());
        g.log.lock().unwrap().clear();
        *g.last_session.lock().unwrap() = None;
        (g.running.clone(), g.log.clone(), g.last_session.clone())
    };

    {
        let g = game.lock().unwrap();
        push_log(
            &g,
            emitter.as_ref(),
            "stdout",
            "[void-desktop] SIMULATED LAUNCH — void-core's JVM spawn is not implemented yet.",
        );
        push_log(
            &g,
            emitter.as_ref(),
            "stdout",
            format!("[void-desktop] java: {java_path}"),
        );
        push_log(
            &g,
            emitter.as_ref(),
            "stdout",
            format!("[void-desktop] args: {}", args.join(" ")),
        );
        push_log(
            &g,
            emitter.as_ref(),
            "stdout",
            format!("[void-desktop] bridge listening on ws://127.0.0.1:{port} (token withheld)"),
        );
    }

    emit(
        emitter.as_ref(),
        GAME_STARTED,
        &serde_json::json!({ "pid": 0, "loadout": loadout, "bridge_port": port, "simulated": true }),
    );

    // The session itself, on a background task, so `launch` returns as soon as the
    // window can hide to the tray.
    let game_for_task = game.clone();
    let loadout_for_task = loadout.clone();
    tokio::spawn(async move {
        let started = std::time::Instant::now();
        let script: [(&str, &str); 6] = [
            ("stdout", "[Client thread/INFO]: Setting user: Searge"),
            ("stdout", "[Client thread/INFO]: LWJGL Version: 2.9.4"),
            ("stdout", "[void-client/INFO]: connected to launcher bridge"),
            ("stdout", "[void-client/INFO]: applied loadout"),
            ("stdout", "[Client thread/INFO]: Connecting to mc.hypixel.net, 25565"),
            ("stdout", "[void-client/INFO]: session summary sent"),
        ];

        for (stream, line) in script {
            if !running.load(Ordering::SeqCst) {
                break;
            }
            tokio::time::sleep(Duration::from_millis(400)).await;
            {
                let mut buf = log.lock().unwrap();
                if buf.len() == LOG_CAPACITY {
                    buf.pop_front();
                }
                buf.push_back(line.to_string());
            }
            emit(
                emitter.as_ref(),
                GAME_LOG,
                &LogLine {
                    stream: if stream == "stderr" { "stderr" } else { "stdout" },
                    line: line.to_string(),
                    ts_ms: now_ms(),
                },
            );
        }

        // The three bridge messages the launcher forwards verbatim (§7). Shapes are
        // exactly `protocol.json`'s, so the web side is already written against the
        // real thing.
        emit(
            emitter.as_ref(),
            BRIDGE_SERVER,
            &serde_json::json!({ "t": "server", "host": "mc.hypixel.net", "connected": true }),
        );
        emit(
            emitter.as_ref(),
            BRIDGE_STATE,
            &serde_json::json!({
                "t": "state",
                "loadout": loadout_for_task,
                "patch": { "mods.fullbright.on": true }
            }),
        );

        let played_ms = started.elapsed().as_millis().max(1) as u64;
        let fps_avg = 142.0;
        emit(
            emitter.as_ref(),
            BRIDGE_SESSION,
            &serde_json::json!({
                "t": "session",
                "fps_avg": fps_avg,
                "played_ms": played_ms,
                "server": "mc.hypixel.net",
                "loadout": loadout_for_task,
            }),
        );
        *last_session.lock().unwrap() = Some((played_ms, fps_avg, Some("mc.hypixel.net".into())));

        running.store(false, Ordering::SeqCst);
        let crash_tail = None;
        let stats = SessionStats {
            code: 0,
            loadout: loadout_for_task.clone(),
            played_ms,
            fps_avg,
            server: Some("mc.hypixel.net".into()),
            crash_tail,
        };
        {
            let mut g = game_for_task.lock().unwrap();
            g.pid = None;
        }
        emit(emitter.as_ref(), GAME_CLOSED, &stats);
    });

    Ok(LaunchReport {
        pid: 0,
        bridge_port: port,
        loadout,
    })
}

/// Force-quit. Real once the JVM is real; today it flips the flag the session task
/// polls, which ends the run at the next line.
pub fn kill(game: &Game) -> Result<(), Error> {
    if !game.is_running() {
        return Err(Error::NotRunning);
    }
    game.running.store(false, Ordering::SeqCst);
    // TODO(integrate): `child.kill()` on the JVM handle void-core returns, plus a
    // SIGTERM-then-SIGKILL escalation so a hung JVM still goes away.
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::events::test_support::Recorder;

    #[test]
    fn jvm_args_carry_the_bridge_seam() {
        let args = jvm_args(4096, 51234, "deadbeef");
        assert!(args.contains(&"-Xmx4096M".to_string()));
        assert!(args.contains(&"-Dvoid.port=51234".to_string()));
        assert!(args.contains(&"-Dvoid.token=deadbeef".to_string()));
        // -Xms is never larger than -Xmx and never below the floor.
        assert!(args.contains(&"-Xms2048M".to_string()));
        assert!(jvm_args(512, 1, "t").contains(&"-Xms512M".to_string()));
    }

    #[test]
    fn tokens_are_32_hex_characters_and_do_not_repeat() {
        let a = mint_session_token();
        let b = mint_session_token();
        assert_eq!(a.len(), 32);
        assert!(a.chars().all(|c| c.is_ascii_hexdigit()));
        assert_ne!(a, b);
    }

    #[test]
    fn a_reserved_port_is_a_real_one() {
        let port = reserve_bridge_port().unwrap();
        assert!(port > 1024);
    }

    #[test]
    fn the_log_ring_buffer_is_capped() {
        let game = Game::default();
        let rec = Recorder::default();
        for i in 0..(LOG_CAPACITY + 50) {
            push_log(&game, &rec, "stdout", format!("line {i}"));
        }
        assert_eq!(game.log.lock().unwrap().len(), LOG_CAPACITY);
        assert_eq!(game.tail(1), vec![format!("line {}", LOG_CAPACITY + 49)]);
    }

    #[tokio::test]
    async fn a_second_launch_is_refused_while_one_is_running() {
        let game = Arc::new(Mutex::new(Game::default()));
        let rec: Arc<dyn Emitter> = Arc::new(Recorder::default());
        launch(game.clone(), rec.clone(), "sword-pvp".into(), 4096, "java".into())
            .await
            .unwrap();
        let second = launch(game.clone(), rec, "bedwars".into(), 4096, "java".into()).await;
        assert!(matches!(second, Err(Error::AlreadyRunning)));
    }

    #[tokio::test]
    async fn a_session_emits_started_logs_bridge_events_then_closed() {
        let game = Arc::new(Mutex::new(Game::default()));
        let rec = Recorder::default();
        let emitter: Arc<dyn Emitter> = Arc::new(rec.clone());
        let report = launch(game, emitter, "sword-pvp".into(), 2048, "java".into())
            .await
            .unwrap();
        assert!(report.bridge_port > 1024);

        // The background task needs a moment to walk its script.
        for _ in 0..80 {
            if rec.names().iter().any(|n| n == GAME_CLOSED) {
                break;
            }
            tokio::time::sleep(Duration::from_millis(100)).await;
        }
        let names = rec.names();
        assert!(names.iter().any(|n| n == GAME_STARTED));
        assert!(names.iter().any(|n| n == BRIDGE_SERVER));
        assert!(names.iter().any(|n| n == BRIDGE_SESSION));
        assert!(names.iter().any(|n| n == GAME_CLOSED));
        assert_eq!(rec.payloads(GAME_CLOSED)[0]["code"], 0);
    }

    #[test]
    fn killing_a_stopped_game_is_an_error_not_a_no_op() {
        let game = Game::default();
        assert!(matches!(kill(&game), Err(Error::NotRunning)));
    }
}
