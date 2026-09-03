//! Errors from loading, validating and storing loadouts.

use std::path::PathBuf;

use crate::loadout::LoadoutId;
use crate::mods::{HudModId, ModId};

/// Anything that can go wrong in `void-loadout`.
#[derive(Debug, thiserror::Error)]
#[non_exhaustive]
pub enum Error {
    /// A file could not be read, written or renamed.
    #[error("{path}: {source}")]
    Io {
        /// The file being touched.
        path: PathBuf,
        /// The underlying error.
        #[source]
        source: std::io::Error,
    },

    /// A file on disk is not the JSON this crate expects.
    #[error("{path}: {source}")]
    Json {
        /// The file being parsed.
        path: PathBuf,
        /// The underlying error.
        #[source]
        source: serde_json::Error,
    },

    /// A settings object failed its mod's sub-schema in `mods.json`.
    #[error("invalid settings for mod `{mod_id}`: {source}")]
    InvalidSettings {
        /// Which mod's settings were rejected.
        mod_id: ModId,
        /// The underlying error.
        #[source]
        source: serde_json::Error,
    },

    /// An invariant JSON Schema cannot express was violated.
    #[error("invalid loadout: {0}")]
    Invalid(String),

    /// Two HUD items position the same mod. `hud_layout` allows at most one each.
    #[error("hud layout has more than one entry for `{0}`")]
    DuplicateHudItem(HudModId),

    /// A `state` patch path named something outside the closed set of 12 mods.
    #[error("`{0}` is not one of the 12 mod ids")]
    UnknownMod(String),

    /// A `state` patch path was not of the form `mods.<mod_id>.<setting>`.
    #[error("`{0}` is not a `mods.<mod_id>.<setting>` path")]
    BadPatchPath(String),

    /// No loadout with that id is in the library.
    #[error("no loadout `{0}` in the library")]
    NotFound(LoadoutId),

    /// A loadout with that id is already in the library.
    #[error("loadout `{0}` already exists")]
    AlreadyExists(LoadoutId),

    /// The home directory could not be determined, so `~/.void-pvp` has no meaning.
    #[error("cannot determine the home directory; set VOID_PVP_HOME")]
    NoHome,
}
