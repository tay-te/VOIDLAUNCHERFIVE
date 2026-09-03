//! Stand-ins for the parts of `void-core`, `void-bridge` and `void-loadout` the
//! launcher needs but that are still doc-comment stubs (see `CONTRACTS.md`: those
//! directories belong to the `core` agent and this one must not write to them).
//!
//! Every module in here is either
//!
//! - **real and staying** — `slp` (Minecraft server ping) and `java` (detection) are
//!   launcher concerns with no core dependency; or
//! - **a shaped stand-in** — `auth`, `prepare`, `game`, `store`. Each carries a
//!   `TODO(integrate)` header naming the crate that takes it over. Their *signatures*
//!   are the contract the `commands/` layer is written against, so the swap is a body
//!   replacement, not an API change, and nothing in `src/` moves.
//!
//! Nothing in this directory depends on Tauri: `cargo check --no-default-features`
//! compiles all of it, which is how CI without webkit2gtk still covers the launch
//! pipeline.

pub mod auth;
pub mod game;
pub mod java;
pub mod prepare;
pub mod slp;
pub mod store;
