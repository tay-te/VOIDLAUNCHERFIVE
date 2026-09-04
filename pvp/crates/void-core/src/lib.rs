//! Auth, manifests, Java runtime and JVM spawn for the VOID PVP launcher.
//!
//! Owns everything between "the player clicks Play" and "the Minecraft window is up",
//! per PVP_ARCHITECTURE.md §12 — a port of VOID's `main.ts` with the Node removed:
//!
//! 1. [`auth`] — Microsoft device-code OAuth → Xbox Live → XSTS → Minecraft services →
//!    profile, with the refresh token in the OS keychain, plus an offline mode.
//! 2. [`manifest`] — Mojang's `version_manifest_v2`, the 1.8.9 version JSON and the
//!    Legacy Fabric loader profile, merged into one [`LaunchProfile`].
//! 3. [`download`] — 16-way parallel fetches with SHA-1 verification and a
//!    hash-addressed cache, reporting progress over a channel.
//! 4. [`java`] — find a Java 8 runtime, or fetch Temurin 8 from Adoptium.
//! 5. [`launch`] — classpath, extracted LWJGL 2 natives, JVM args (including
//!    `-Dvoid.port` and `-Dvoid.token` for the mod's WS client, §6.9), spawn, and the
//!    game's output streamed line by line.
//!
//! **This crate has no Tauri dependency and must never gain one** (§4). That is what
//! makes `void-pvp launch --loadout sword-pvp` possible and lets the launch path be
//! tested without a webview. The `void-pvp` binary in `src/bin/` is that CLI.
//!
//! ```no_run
//! use void_core::{auth::Session, install, java, launch, manifest::RuleContext, Paths};
//!
//! # async fn run() -> Result<(), void_core::Error> {
//! let paths = Paths::new()?;
//! let http = reqwest::Client::new();
//! let ctx = RuleContext::host()?;
//!
//! let profile = install::prepare(&http, &paths, &ctx, None, None).await?;
//! let java = java::ensure_java8(&http, &paths, None).await?;
//!
//! let mut game = launch::launch(&profile, &paths, &launch::LaunchOptions {
//!     session: Session::offline("Tester"),
//!     java: java.path,
//!     max_memory_mb: 2048,
//!     extra_jvm_args: vec![],
//!     bridge_port: 51234,
//!     bridge_token: "…".into(),
//!     mod_jar: None,
//! })
//! .await?;
//! println!("exited with {}", game.wait().await?);
//! # Ok(()) }
//! ```

#![forbid(unsafe_code)]
#![warn(missing_docs)]

pub mod archive;
pub mod auth;
mod config;
pub mod download;
mod error;
pub mod install;
pub mod java;
pub mod launch;
pub mod manifest;
mod paths;
pub mod sync;

pub use config::{Config, CLIENT_ID_ENV, DEFAULT_MAX_MEMORY_MB};
pub use error::{Error, Result};
pub use manifest::{LaunchProfile, Os, RuleContext};
pub use paths::Paths;
