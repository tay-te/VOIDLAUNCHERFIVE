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

    /// Everything `void-core` reports: auth, manifests, downloads, Java, spawn.
    ///
    /// **This used to say its `Display` was "already written for a person", and that was not
    /// true.** Those messages are written for the CLI, whose reader is running `void-pvp` and
    /// wants the path with the errno on it. The same text reached this banner, whose reader is a
    /// player deciding whether to press Play again — and got
    /// `/home/…/libraries/x.jar: sha1 mismatch (expected a1b2…, got c3d4…)`.
    ///
    /// Re-wording every variant here would have been the two-places-to-fix problem the old
    /// comment was right about. So the technical sentence is still passed through and
    /// `void_core::Error::advice` supplies a second one saying what to do — one message, both
    /// audiences, one place to fix either. See [`map_err`].
    #[error("{0}")]
    Core(#[from] void_core::Error),

    #[error("{0}")]
    Loadout(#[from] void_loadout::Error),

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
///
/// The advice sentence is appended rather than substituted: an error banner that said only
/// "Check your connection and try again" would be unactionable for whoever the player asks for
/// help, and one that said only `https://…/1.8.9.json returned 503` is unactionable for the
/// player. Both is one line longer than either and useful to both.
pub fn map_err<T>(r: Result<T, Error>) -> CmdResult<T> {
    r.map_err(|e| {
        tracing::warn!(error = %e, "command failed");
        match &e {
            Error::Core(core) => match core.advice() {
                Some(advice) => format!("{e} — {advice}"),
                None => e.to_string(),
            },
            _ => e.to_string(),
        }
    })
}
