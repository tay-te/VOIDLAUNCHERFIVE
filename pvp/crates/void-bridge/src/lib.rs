//! The localhost WebSocket server the Minecraft mod connects back to.
//!
//! Per PVP_ARCHITECTURE.md §2 and §7 this link carries **state and telemetry summaries,
//! never frames**. Telemetry that gets drawn never leaves the JVM: it goes Mixin → JS
//! bridge in the same process. What crosses here is loadout state, HUD layout, session
//! stats and server presence.
//!
//! - [`BridgeServer`] binds `ws://127.0.0.1:<port>` on an OS-assigned port and mints a
//!   session token; `void-core` passes both to the JVM as `-Dvoid.port` and
//!   `-Dvoid.token`.
//! - The first frame must be `hello` with a matching token and `v == 1`; anything else
//!   is closed with a policy close frame ([`Rejection`]).
//! - Inbound messages fan out on a [`tokio::sync::broadcast`] bus
//!   ([`BridgeServer::subscribe`]); outbound go through [`BridgeServer::send`].
//! - A reconnect is normal: a new `hello` takes over the link and the previous
//!   connection closes itself, so the mod's backoff loop (§6.9) needs no cooperation.
//!
//! ```no_run
//! use void_bridge::{BridgeServer, InitPayload, StaticInit};
//! use void_loadout::{defaults, GlobalSettings};
//!
//! # async fn run() -> Result<(), Box<dyn std::error::Error>> {
//! let loadout = defaults::sword_pvp();
//! let server = BridgeServer::bind(StaticInit(InitPayload {
//!     loadouts: vec![loadout.summary()],
//!     loadout,
//!     settings: GlobalSettings::factory(),
//! }))
//! .await?;
//!
//! println!("-Dvoid.port={} -Dvoid.token={}", server.port(), server.token());
//! let mut bus = server.subscribe();
//! while let Ok(msg) = bus.recv().await {
//!     println!("mod said {}", msg.tag());
//! }
//! # Ok(()) }
//! ```

#![forbid(unsafe_code)]
#![warn(missing_docs)]

mod protocol;
mod server;

pub use protocol::{
    InitPayload, InitSource, JavaToRust, RustToJava, StaticInit, PROTOCOL_VERSION,
};
pub use server::{BridgeServer, Rejection};

// The wire types below are owned by `void-loadout` but are part of this crate's public
// surface, so they are re-exported: a consumer never needs both crates in its manifest.
pub use void_loadout::{GlobalSettings, HudItem, Loadout, LoadoutId, LoadoutSummary, StatePatch};

/// Anything that can go wrong in `void-bridge`.
#[derive(Debug, thiserror::Error)]
#[non_exhaustive]
pub enum Error {
    /// The listener or a socket failed.
    #[error(transparent)]
    Io(#[from] std::io::Error),

    /// A message could not be serialized or parsed.
    #[error(transparent)]
    Json(#[from] serde_json::Error),

    /// The WebSocket layer failed.
    #[error(transparent)]
    WebSocket(#[from] tokio_tungstenite::tungstenite::Error),

    /// A connection was refused during the handshake.
    #[error("handshake refused: {0:?}")]
    Rejected(Rejection),

    /// A bind address outside loopback was requested.
    #[error("{0} is not a loopback address; the bridge must not be reachable off-box")]
    NotLoopback(std::net::SocketAddr),
}
