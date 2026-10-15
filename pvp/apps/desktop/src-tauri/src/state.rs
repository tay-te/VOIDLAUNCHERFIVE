//! Everything the commands share.
//!
//! No Tauri types: `AppState` is built and exercised by the tests as well as managed by
//! the app, which is what keeps `--no-default-features` honest.

use std::sync::{Arc, Mutex};

use void_core::auth::Session;
use void_core::{Config, Paths};
use void_loadout::Store;
use void_online::{Online, OnlineConfig};

use crate::adapters::game::GameState;
use crate::error::Error;
use crate::presence::Presence;

pub struct AppState {
    /// Where `void-core` keeps the installation: `$VOID_PVP_HOME` or `~/.void-pvp`.
    pub paths: Paths,
    /// The loadout library. Cheap to clone; the bridge's `StoreInit` gets its own.
    pub store: Store,
    /// One HTTP client for the process: connection pooling across manifests, assets and
    /// the Adoptium tarball is most of what makes a cold install tolerable.
    pub http: reqwest::Client,
    /// The signed-in session, with its access token. Never crosses to the webview.
    pub session: Mutex<Option<Session>>,
    pub game: Arc<Mutex<GameState>>,
    /// Discord Rich Presence. Disabled here; `lib.rs` starts the real one, so tests and
    /// the CLI-less command layer never open a Discord socket.
    pub presence: Presence,
    /// The online backend (docs/online-contract.md). Disabled when this build has no
    /// `VOID_SUPABASE_URL` / `VOID_SUPABASE_ANON_KEY`; every call then says so.
    pub online: Online,
}

impl AppState {
    pub fn new(paths: Paths) -> Result<Self, Error> {
        let store = Store::at(paths.root());
        // Seeds the three starter loadouts on first run; a no-op afterwards.
        store.init().map_err(Error::from)?;

        let http = reqwest::Client::builder()
            .user_agent(concat!("void-pvp-launcher/", env!("CARGO_PKG_VERSION")))
            .build()
            .unwrap_or_default();
        // Tests never reach a backend, whatever the developer's environment says.
        let online = if cfg!(test) { Online::disabled() } else { Online::new(OnlineConfig::from_env(), http.clone()) };
        Ok(AppState {
            paths,
            store,
            http,
            session: Mutex::new(None),
            game: Arc::new(Mutex::new(GameState::default())),
            presence: Presence::disabled(),
            online,
        })
    }

    pub fn open_default() -> Result<Self, Error> {
        Self::new(Paths::new()?)
    }

    /// `config.json`, re-read on each use: it is small, and a stale copy in memory is
    /// how a launcher ends up spawning with the RAM the player just changed away from.
    pub fn config(&self) -> Result<Config, Error> {
        Config::load(&self.paths).map_err(Error::from)
    }

    /// The installation's paths with the configured game directory applied — what a
    /// launch, the mods folder and the game's logs use.
    pub fn game_paths(&self) -> Result<Paths, Error> {
        Ok(self.config()?.paths_for(&self.paths))
    }
}

#[cfg(test)]
pub fn scratch_state() -> AppState {
    let dir = std::env::temp_dir().join(format!(
        "void-desktop-{}-{}",
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos()
    ));
    std::fs::create_dir_all(&dir).unwrap();
    AppState::new(Paths::at(dir)).unwrap()
}
