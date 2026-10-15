//! One error type for the launcher, mapped to a plain `String` at the command boundary
//! because that is what the TypeScript side sees.
//!
//! `Display` is written for a player, not for a log line: these strings land in the
//! launch-error banner. Machine detail goes to `tracing`.

use std::fmt;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("Not signed in. Sign in with a Microsoft account, or use Play offline.")]
    NotSignedIn,

    #[error("`{0}` is not a valid loadout id: use lower-case letters, digits and single hyphens.")]
    BadLoadoutId(String),

    #[error("A loadout called `{0}` already exists.")]
    DuplicateLoadout(String),

    #[error("The last loadout cannot be deleted.")]
    LastLoadout,

    #[error("Minecraft is already running. Close it, or use Force quit first.")]
    AlreadyRunning,

    #[error("Minecraft is not running.")]
    NotRunning,

    #[error("Could not reach {host}: {reason}")]
    Ping { host: String, reason: String },

    /// Everything `void-core` reports: auth, manifests, downloads, Java, spawn. Its own
    /// `Display` is already written for a person, so it is passed through rather than
    /// re-worded — re-wording it here would mean two places to fix a bad message.
    #[error("{0}")]
    Core(#[from] void_core::Error),

    #[error("{0}")]
    Loadout(#[from] void_loadout::Error),

    /// A share code was refused. `ShareError`'s own `Display` is the short phrase the
    /// in-game toast uses; here it is made a sentence, like every other launcher error.
    #[error("{}", share_sentence(*.0))]
    Share(void_loadout::ShareError),

    #[error("The launcher could not talk to the game: {0}")]
    Bridge(#[from] void_bridge::Error),

    #[error("Could not read or write launcher data: {0}")]
    Storage(String),

    #[error("{0}")]
    Other(String),
}

impl Error {
    pub fn other(e: impl fmt::Display) -> Self {
        Error::Other(e.to_string())
    }

    /// Lifts a refused share code out of `void_loadout::Error`, so the player reads the
    /// reason as a sentence rather than the crate's lower-case phrase.
    pub fn from_loadout(e: void_loadout::Error) -> Self {
        match e {
            void_loadout::Error::Share(share) => Error::Share(share),
            other => Error::Loadout(other),
        }
    }
}

/// `that code is damaged — copy it again` → `That code is damaged — copy it again.`
fn share_sentence(e: void_loadout::ShareError) -> String {
    let phrase = e.to_string();
    let mut chars = phrase.chars();
    match chars.next() {
        Some(first) => format!("{}{}.", first.to_uppercase(), chars.as_str()),
        None => String::new(),
    }
}

impl From<std::io::Error> for Error {
    fn from(e: std::io::Error) -> Self {
        Error::Storage(e.to_string())
    }
}

impl From<serde_json::Error> for Error {
    fn from(e: serde_json::Error) -> Self {
        Error::Storage(format!("malformed JSON: {e}"))
    }
}

/// What every command returns. `String` rather than `Error` because Tauri serialises the
/// error variant straight into the JS rejection value.
pub type CmdResult<T> = Result<T, String>;

/// Log the technical error, hand the player the readable one.
pub fn map_err<T>(r: Result<T, Error>) -> CmdResult<T> {
    r.map_err(|e| {
        tracing::warn!(error = %e, "command failed");
        e.to_string()
    })
}
