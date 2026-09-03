//! One error type for the whole launcher, mapped to a plain `String` at the command
//! boundary because that is what the TypeScript side sees.
//!
//! Every `#[tauri::command]` returns `Result<T, String>`; the string is the
//! user-facing sentence rendered in the launch-error surface, so `Display` here is
//! written for a player, not for a log line. Machine detail goes to `tracing`.

use std::fmt;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("Not signed in. Sign in with your Microsoft account to launch.")]
    NotSignedIn,

    #[error("No loadout called `{0}`.")]
    UnknownLoadout(String),

    #[error("A loadout called `{0}` already exists.")]
    DuplicateLoadout(String),

    #[error("`{0}` is not a valid loadout id: use lower-case letters, digits and single hyphens.")]
    BadLoadoutId(String),

    #[error("The last loadout cannot be deleted.")]
    LastLoadout,

    #[error("Minecraft is already running. Close it or use Force quit first.")]
    AlreadyRunning,

    #[error("Minecraft is not running.")]
    NotRunning,

    #[error("Could not reach {host}: {reason}")]
    Ping { host: String, reason: String },

    #[error("Java 8 was not found and could not be downloaded: {0}")]
    Java(String),

    #[error("Preparation failed at `{step}`: {reason}")]
    Prepare { step: String, reason: String },

    #[error("Sign-in failed: {0}")]
    Auth(String),

    #[error("Could not read or write launcher data: {0}")]
    Storage(String),

    #[error("{0}")]
    Other(String),
}

impl Error {
    pub fn other(e: impl fmt::Display) -> Self {
        Error::Other(e.to_string())
    }

    pub fn storage(e: impl fmt::Display) -> Self {
        Error::Storage(e.to_string())
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

/// What every command returns. `String` rather than `Error` because Tauri serialises
/// the error variant straight into the JS rejection value.
pub type CmdResult<T> = Result<T, String>;

/// Log the technical error, hand the player the readable one.
pub fn map_err<T>(r: Result<T, Error>) -> CmdResult<T> {
    r.map_err(|e| {
        tracing::warn!(error = %e, "command failed");
        e.to_string()
    })
}
