//! Discord Rich Presence, from the launcher.
//!
//! The launcher already knows everything presence needs — which loadout launched, when
//! the game started, and which server the mod says it joined (the bridge's `server`
//! message) — so presence lives here rather than in the mod: no second IPC client inside
//! the JVM, and it keeps working while the launcher sits in the tray.
//!
//! - In the launcher: *In the launcher*.
//! - Launching: *Launching Minecraft 1.8.9* · loadout.
//! - In game: *Playing on mc.hypixel.net* (or *In the menus*) · loadout, with elapsed time.
//!
//! Discord's IPC is a blocking local socket, so it runs on its own thread fed by a
//! channel; every call from the rest of the launcher is a non-blocking send. Discord not
//! running is the normal case and is retried quietly every [`RETRY`].
//!
//! **Needs a Discord application id** — created once in the Discord developer portal,
//! with a Rich Presence art asset named `void`. Like the Azure client id it is injected,
//! not committed: `VOID_DISCORD_APP_ID` at build time (`option_env!`) or at run time.
//! Without one presence is simply off.

use std::sync::mpsc;
use std::time::Duration;

/// Environment variable holding the Discord application id.
pub const DISCORD_APP_ID_ENV: &str = "VOID_DISCORD_APP_ID";

/// The id compiled into this build, if any.
pub const BUILT_IN_DISCORD_APP_ID: Option<&str> = option_env!("VOID_DISCORD_APP_ID");

/// How often a disconnected client tries Discord again.
const RETRY: Duration = Duration::from_secs(20);

/// The art asset key uploaded to the Discord application.
const LARGE_IMAGE: &str = "void";

/// What the player is doing, as presence shows it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Activity {
    /// The launcher is open (or in the tray) and no game is running.
    Idle,
    /// Prepare/launch is in progress.
    Launching { loadout: String },
    /// The game is running. `server` is the host the mod reports; `None` in menus or
    /// singleplayer. `since_ms` is the Unix time the game started.
    Playing { loadout: String, server: Option<String>, since_ms: i64 },
}

/// The `details`, `state` and start time Discord shows for an activity.
pub fn describe(activity: &Activity) -> (String, Option<String>, Option<i64>) {
    match activity {
        Activity::Idle => ("In the launcher".into(), None, None),
        Activity::Launching { loadout } => {
            ("Launching Minecraft 1.8.9".into(), Some(format!("Loadout: {loadout}")), None)
        }
        Activity::Playing { loadout, server, since_ms } => (
            match server {
                Some(host) => format!("Playing on {host}"),
                None => "In the menus".into(),
            },
            Some(format!("Loadout: {loadout}")),
            Some(*since_ms),
        ),
    }
}

enum Msg {
    Set(Activity),
    Enabled(bool),
}

/// A handle to the presence thread. Cheap to clone; every method is a channel send.
#[derive(Clone)]
pub struct Presence {
    tx: Option<mpsc::Sender<Msg>>,
}

impl std::fmt::Debug for Presence {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Presence").field("running", &self.tx.is_some()).finish()
    }
}

impl Presence {
    /// A presence that does nothing — the tests, and builds with no application id.
    pub fn disabled() -> Self {
        Presence { tx: None }
    }

    /// The application id to use: `$VOID_DISCORD_APP_ID`, else the built-in one.
    pub fn app_id() -> Option<String> {
        std::env::var(DISCORD_APP_ID_ENV)
            .ok()
            .or_else(|| BUILT_IN_DISCORD_APP_ID.map(str::to_string))
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
    }

    /// Starts the presence thread. Returns [`Presence::disabled`] without an app id.
    pub fn start(app_id: Option<String>, enabled: bool) -> Self {
        let Some(app_id) = app_id else {
            tracing::info!("no Discord application id; Rich Presence is off");
            return Self::disabled();
        };
        let (tx, rx) = mpsc::channel();
        let spawned = std::thread::Builder::new()
            .name("discord-presence".into())
            .spawn(move || run(app_id, enabled, rx));
        match spawned {
            Ok(_) => Presence { tx: Some(tx) },
            Err(e) => {
                tracing::warn!(error = %e, "could not start the Discord presence thread");
                Self::disabled()
            }
        }
    }

    /// Show an activity.
    pub fn set(&self, activity: Activity) {
        if let Some(tx) = &self.tx {
            let _ = tx.send(Msg::Set(activity));
        }
    }

    /// Turn presence on or off (the Settings toggle). Off clears it in Discord at once.
    pub fn set_enabled(&self, enabled: bool) {
        if let Some(tx) = &self.tx {
            let _ = tx.send(Msg::Enabled(enabled));
        }
    }
}

fn run(app_id: String, mut enabled: bool, rx: mpsc::Receiver<Msg>) {
    use discord_rich_presence::{activity, DiscordIpc, DiscordIpcClient};

    let mut current = Activity::Idle;
    let mut client: Option<DiscordIpcClient> = None;
    let mut dirty = true;

    loop {
        match rx.recv_timeout(RETRY) {
            Ok(Msg::Set(a)) => {
                dirty |= a != current;
                current = a;
            }
            Ok(Msg::Enabled(on)) => {
                if enabled && !on {
                    if let Some(mut c) = client.take() {
                        let _ = c.clear_activity();
                        let _ = c.close();
                    }
                }
                dirty |= on != enabled;
                enabled = on;
            }
            Err(mpsc::RecvTimeoutError::Timeout) => {}
            // Every handle dropped: the launcher is exiting.
            Err(mpsc::RecvTimeoutError::Disconnected) => break,
        }
        if !enabled || (!dirty && client.is_some()) {
            continue;
        }
        if client.is_none() {
            let mut c = DiscordIpcClient::new(&app_id);
            match c.connect() {
                Ok(()) => {
                    tracing::info!("connected to Discord for Rich Presence");
                    client = Some(c);
                    dirty = true;
                }
                Err(e) => {
                    tracing::debug!(error = %e, "Discord is not reachable; will retry");
                    continue;
                }
            }
        }
        let (details, state, start) = describe(&current);
        let mut payload = activity::Activity::new()
            .details(details)
            .activity_type(activity::ActivityType::Playing)
            .assets(activity::Assets::new().large_image(LARGE_IMAGE).large_text("VOID PvP"));
        if let Some(state) = state {
            payload = payload.state(state);
        }
        if let Some(start) = start {
            payload = payload.timestamps(activity::Timestamps::new().start(start));
        }
        if let Some(c) = client.as_mut() {
            match c.set_activity(payload) {
                Ok(()) => dirty = false,
                Err(e) => {
                    tracing::debug!(error = %e, "Discord connection lost; will reconnect");
                    client = None;
                }
            }
        }
    }
    if let Some(mut c) = client {
        let _ = c.clear_activity();
        let _ = c.close();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn each_activity_reads_the_way_the_spec_says() {
        assert_eq!(describe(&Activity::Idle).0, "In the launcher");

        let (d, s, t) = describe(&Activity::Launching { loadout: "Sword PvP".into() });
        assert_eq!(d, "Launching Minecraft 1.8.9");
        assert_eq!(s.as_deref(), Some("Loadout: Sword PvP"));
        assert_eq!(t, None);

        let (d, s, t) = describe(&Activity::Playing {
            loadout: "Bedwars".into(),
            server: Some("mc.hypixel.net".into()),
            since_ms: 42,
        });
        assert_eq!(d, "Playing on mc.hypixel.net");
        assert_eq!(s.as_deref(), Some("Loadout: Bedwars"));
        assert_eq!(t, Some(42));

        let (d, ..) = describe(&Activity::Playing { loadout: "x".into(), server: None, since_ms: 0 });
        assert_eq!(d, "In the menus");
    }

    #[test]
    fn a_disabled_presence_swallows_everything() {
        let p = Presence::disabled();
        p.set(Activity::Idle);
        p.set_enabled(false);
    }
}
