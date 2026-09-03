//! The loadout model: schema types, persistence, diff.
//!
//! A loadout is a template — "a named snapshot of which mods are on, their settings and
//! HUD layout" (PVP_ARCHITECTURE.md §8) — and it is fully hot-swappable.
//!
//! Responsibilities:
//!
//! - Rust types for `schema/loadout.json` and the mod registry in `schema/mods.json`:
//!   the closed set of 12 mod ids, each mod's `kind` and `hypixel_safe` class, its
//!   settings and its factory defaults.
//! - Persistence of the user's loadout library, and migration when the registry version
//!   changes. A loadout that omits a mod falls back to that mod's registry defaults,
//!   which is what keeps old loadouts valid as mods are added.
//! - Diffing a changed loadout into the flat dotted-path patch that `void-bridge`
//!   sends and receives as `state` (`{"mods.fullbright.on": true}`), and applying an
//!   inbound patch. Flat, not nested, so concurrent edits to sibling settings merge.
//! - HUD layout as anchor + `dx`/`dy` + `scale`, never absolute pixels, so a layout
//!   survives GUI-scale, resolution and fullscreen changes (§8.1). At most one entry
//!   per mod id — an invariant enforced here, since JSON Schema cannot express it.
//! - Accumulating `session` telemetry into each loadout's `played_ms` and `fps_avg`.
//! - Deriving the HYPIXEL-READY badge: true when every *enabled* mod is `safe` (§11).
//!
//! Note the ownership split of §2: while the game is running, **Java** is authoritative
//! for live state and tells Rust afterwards. This crate is the store of record between
//! sessions, not the referee during one.
//!
//! Stub: no implementation yet. Owned by the `core` agent (see `CONTRACTS.md`).
