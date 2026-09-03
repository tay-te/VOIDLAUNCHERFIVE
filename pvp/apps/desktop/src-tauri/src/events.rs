//! The event names the web side listens on, and the one trait the adapters need in
//! order to emit them.
//!
//! Adapters take `&dyn Emitter` rather than a `tauri::AppHandle` so that everything
//! below `commands/` compiles with `--no-default-features` — that is what lets CI
//! without webkit2gtk type-check the launch pipeline.

/// `auth:status` — device-flow progress, then the account or the failure.
pub const AUTH_STATUS: &str = "auth:status";
/// `prepare:progress` — `{ step, done, total, bytes_per_sec }`.
pub const PREPARE_PROGRESS: &str = "prepare:progress";
/// `game:log` — one line of JVM stdout/stderr.
pub const GAME_LOG: &str = "game:log";
/// `game:started` — the JVM is up; the window hides to the tray after this.
pub const GAME_STARTED: &str = "game:started";
/// `game:closed` — `{ code, … }` plus the session stats the Play screen shows.
pub const GAME_CLOSED: &str = "game:closed";
/// `bridge:state` — forwarded `state` message from the mod (loadout patch).
pub const BRIDGE_STATE: &str = "bridge:state";
/// `bridge:session` — forwarded `session` telemetry summary.
pub const BRIDGE_SESSION: &str = "bridge:session";
/// `bridge:server` — forwarded `server` presence message.
pub const BRIDGE_SERVER: &str = "bridge:server";
/// `loadout:switched` — the tray or another window changed the active loadout.
pub const LOADOUT_SWITCHED: &str = "loadout:switched";

/// Anything that can push an event at the web side.
///
/// One method, JSON-shaped, because the adapters do not need to know what a Tauri
/// window is. `AppHandle` implements this in `lib.rs`; the tests use a recorder.
pub trait Emitter: Send + Sync + 'static {
    fn emit_json(&self, event: &str, payload: serde_json::Value);
}

impl Emitter for () {
    fn emit_json(&self, _event: &str, _payload: serde_json::Value) {}
}

/// Convenience for adapters: serialise and emit, logging rather than failing when the
/// payload cannot be serialised (it never can't, but a panic here would take the
/// launch down).
pub fn emit<T: serde::Serialize>(emitter: &dyn Emitter, event: &str, payload: &T) {
    match serde_json::to_value(payload) {
        Ok(v) => emitter.emit_json(event, v),
        Err(e) => tracing::error!(%event, %e, "could not serialise event payload"),
    }
}

#[cfg(test)]
pub mod test_support {
    use super::*;
    use std::sync::{Arc, Mutex};

    /// Records every emitted event so tests can assert on the sequence.
    #[derive(Clone, Default)]
    pub struct Recorder(pub Arc<Mutex<Vec<(String, serde_json::Value)>>>);

    impl Recorder {
        pub fn names(&self) -> Vec<String> {
            self.0.lock().unwrap().iter().map(|(n, _)| n.clone()).collect()
        }
        pub fn payloads(&self, name: &str) -> Vec<serde_json::Value> {
            self.0
                .lock()
                .unwrap()
                .iter()
                .filter(|(n, _)| n == name)
                .map(|(_, v)| v.clone())
                .collect()
        }
    }

    impl Emitter for Recorder {
        fn emit_json(&self, event: &str, payload: serde_json::Value) {
            self.0.lock().unwrap().push((event.to_string(), payload));
        }
    }
}
