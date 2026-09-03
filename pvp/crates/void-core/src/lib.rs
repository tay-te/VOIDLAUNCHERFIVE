//! Auth, manifests, Java runtime and JVM spawn for the VOID PVP launcher.
//!
//! Owns everything between "the player clicks Play" and "the Minecraft window is up",
//! per PVP_ARCHITECTURE.md §12 — a port of VOID's `main.ts` with the Node removed:
//!
//! 1. Microsoft OAuth → Xbox Live → XSTS → Minecraft token, with the refresh token
//!    kept in the OS keychain.
//! 2. Resolving the 1.8.9 and Legacy Fabric manifests, then downloading assets and
//!    libraries in parallel with SHA-1 verification and a hash cache.
//! 3. Downloading and signature-checking the `void-client` JAR, which embeds the
//!    in-game UI bundle and the Ultralight natives for the host OS.
//! 4. Locating a Java 8 runtime or fetching one from Adoptium.
//! 5. Building JVM args — including `-Dvoid.port` and `-Dvoid.token` for the mod's WS
//!    client (§6.9) — caching them by manifest hash, spawning, and draining stdout into
//!    a ring buffer for the launcher's log view.
//!
//! **This crate has no Tauri dependency and must never gain one** (§4). That is what
//! makes `void-pvp launch --loadout sword` possible and lets the launch path be tested
//! without a webview.
//!
//! Stub: no implementation yet. Owned by the `core` agent (see `CONTRACTS.md`).
