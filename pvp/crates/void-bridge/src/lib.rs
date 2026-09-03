//! The localhost WebSocket server the Minecraft mod connects back to.
//!
//! Per PVP_ARCHITECTURE.md §2 and §7 this link carries **state and telemetry summaries,
//! never frames**. Telemetry that gets drawn never leaves the JVM: it goes Mixin → JS
//! bridge in the same process. What crosses here is loadout state, HUD layout, session
//! stats and server presence.
//!
//! Responsibilities:
//!
//! - Bind `ws://127.0.0.1:<port>` on an ephemeral port and mint a per-spawn session
//!   token; `void-core` passes both to the JVM as `-Dvoid.port` and `-Dvoid.token`.
//! - Reject any connection whose `hello` carries the wrong token.
//! - Serde types for the eight messages of `schema/protocol.json`, tagged on `t`:
//!   `hello`, `state`, `hud`, `session`, `server` inbound; `init`, `loadout`,
//!   `settings` outbound.
//! - Version gate: `v` is `1` on `hello` and `init`. A mismatch means mod and launcher
//!   were not shipped together — refuse to launch and prompt for an update (§7).
//! - Forward compatibility: unknown `t` values and unknown fields are ignored, never
//!   an error. Do not `deny_unknown_fields`.
//! - Survive the mod disappearing (game closed or crashed) and the mod reconnecting
//!   with backoff, replaying the state it accumulated while offline (§6.1).
//!
//! Stub: no implementation yet. Owned by the `core` agent (see `CONTRACTS.md`).
