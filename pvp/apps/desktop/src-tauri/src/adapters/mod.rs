//! The thin layer between `void-core` / `void-bridge` / `void-loadout` and the command
//! surface.
//!
//! There are no stand-ins left in here. What remains is work the launcher genuinely
//! owns and the core crates deliberately do not:
//!
//! - [`game`] orchestrates all three crates for one play session — bind the bridge,
//!   start the store pump, spawn the JVM, fan the output out as events.
//! - [`progress`] translates `void_core::download::Progress`, which counts files, into
//!   the byte-driven `prepare:progress` the launch button renders.
//! - [`slp`] is the Minecraft server-list ping behind "12 ms to Hypixel". It has no
//!   `void-core` dependency because it is not part of launching a game.
//!
//! Nothing here depends on Tauri: `cargo check --no-default-features` compiles all of
//! it, which is how CI without webkit2gtk still covers the launch pipeline.

pub mod game;
pub mod progress;
pub mod slp;
