//! Everything the commands share, and where it lives on disk.
//!
//! No Tauri types: `AppState` is built and exercised by the tests as well as managed
//! by the app, which is what keeps `--no-default-features` honest.

use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use crate::adapters::game::Game;
use crate::adapters::store::Store;
use crate::error::Error;
use crate::models::Account;

pub struct AppState {
    pub data_dir: PathBuf,
    pub store: Mutex<Store>,
    pub account: Mutex<Option<Account>>,
    pub game: Arc<Mutex<Game>>,
}

impl AppState {
    pub fn new(data_dir: PathBuf) -> Result<Self, Error> {
        let store = Store::open(&data_dir)?;
        Ok(AppState {
            data_dir,
            store: Mutex::new(store),
            account: Mutex::new(None),
            game: Arc::new(Mutex::new(Game::default())),
        })
    }

    /// `<platform data dir>/void-pvp`, overridable with `VOID_PVP_DATA_DIR` so a dev
    /// build can be pointed at a scratch library without touching the real one.
    pub fn default_data_dir() -> PathBuf {
        if let Some(dir) = std::env::var_os("VOID_PVP_DATA_DIR") {
            return PathBuf::from(dir);
        }
        dirs::data_dir()
            .unwrap_or_else(std::env::temp_dir)
            .join("void-pvp")
    }
}

#[cfg(test)]
pub fn scratch_state() -> AppState {
    let dir = std::env::temp_dir().join(format!(
        "void-state-{}-{}",
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos()
    ));
    AppState::new(dir).unwrap()
}
