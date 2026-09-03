//! The loadout model: schema types, persistence, diff.
//!
//! A loadout is a template — "a named snapshot of which mods are on, their settings and
//! HUD layout" (PVP_ARCHITECTURE.md §8) — and it is fully hot-swappable.
//!
//! This crate is the Rust side of three of the four schemas in `schema/`:
//!
//! - [`mods`] is `schema/mods.json`: the closed set of 12 mod ids, each mod's [`Kind`]
//!   and [`HypixelSafe`] class, its typed settings and its factory defaults. The schema
//!   document itself is compiled in with `include_str!`, so the registry cannot drift
//!   from the contract without a test failing.
//! - [`loadout`] is `schema/loadout.json`: the [`Loadout`] type the store persists, that
//!   Rust sends to Java in `init` and `loadout`, and that Java pushes to the UI.
//! - [`settings`] is `protocol.json#/definitions/global_settings`, which lives here
//!   because it is persisted as well as sent.
//!
//! On top of them:
//!
//! - [`diff`] turns two loadouts into the flat dotted-path changes the `state` message
//!   carries, and applies an inbound patch ([`apply_patch`]).
//! - [`store`] is `~/.void-pvp/`: one JSON file per loadout plus `active.json` and
//!   `settings.json`, written atomically.
//! - [`defaults`] is the three loadouts created on first run.
//!
//! Note the ownership split of §2: while the game is running, **Java** is authoritative
//! for live state and tells Rust afterwards. This crate is the store of record between
//! sessions, not the referee during one.
//!
//! ```
//! use void_loadout::{apply_patch, defaults, hypixel_ready, ModId, StatePatch};
//!
//! let mut loadout = defaults::sword_pvp();
//! assert!(hypixel_ready(&loadout));
//!
//! // What the mod sends after the player toggles fullbright in game.
//! let mut patch = StatePatch::new();
//! patch.insert(ModId::Fullbright, "on", true);
//! apply_patch(&mut loadout, &patch).unwrap();
//!
//! assert!(!hypixel_ready(&loadout)); // fullbright is `grey` (§11)
//! ```

#![forbid(unsafe_code)]
#![warn(missing_docs)]

pub mod defaults;
pub mod diff;
mod error;
pub mod keybind;
pub mod loadout;
pub mod mods;
pub mod settings;
pub mod store;

pub use error::Error;
pub use diff::{apply_patch, diff, diff_split, Change, Diff, StatePatch};
pub use keybind::{HexColor, Keybind};
pub use loadout::{
    hypixel_ready, Anchor, HudItem, HudLayout, Loadout, LoadoutId, LoadoutStats, LoadoutSummary,
    ModStates, DEFAULT_MC,
};
pub use mods::{
    defaults_json, registry, Category, GameplayModId, HudModId, HypixelSafe, Kind, ModId, ModInfo,
    Registry,
};
pub use settings::GlobalSettings;
pub use store::Store;
