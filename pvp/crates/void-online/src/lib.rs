//! The VOID PvP launcher's online half: a Supabase client for sign-in, cosmetics, friends,
//! presence and emotes.
//!
//! The contract this implements is `docs/online-contract.md`; read Part 2 for the endpoints,
//! the error shapes and the Realtime topology. The short version:
//!
//! - **Sign-in** proves the player's Minecraft identity the way a vanilla server does
//!   (`auth-minecraft` challenge → Mojang `session/minecraft/join` → `auth-minecraft` join),
//!   so the Minecraft access token never reaches VOID's servers. The function answers with a
//!   one-time login that this crate redeems for an ordinary Supabase session, which lives in
//!   memory only and is refreshed a minute before it expires.
//! - **Cosmetics** for the signed-in player come from RPC `my_cosmetics` and change through
//!   edge function `equip`; other players' come from `cosmetics-lookup`, through a debounced
//!   batcher and an LRU that also remembers who is *not* a VOID player.
//! - **Presence**: a heartbeat every 30 s says the launcher is up and which server the game is
//!   on; the backend answers with that server's Realtime topic, which this crate joins to hear
//!   emotes and see which other VOID players are there.
//! - **Realtime**: one Phoenix websocket carrying `user:<uuid>` (purchases landing, friend
//!   changes) and the current `server:<hash>`. See *Realtime* below.
//! - **Disabled mode**: a build without `VOID_SUPABASE_URL` / `VOID_SUPABASE_ANON_KEY` gets an
//!   [`Online`] that answers [`Error::Disabled`] to everything and never opens a socket.
//!
//! Nothing here depends on Tauri. The desktop app owns an [`Online`], forwards its
//! [`OnlineEvent`]s to the webview and the mod bridge, and calls its methods from IPC
//! commands.
//!
//! ```no_run
//! use void_online::{McIdentity, Online, OnlineConfig, OnlineEvent, Slot};
//!
//! # async fn run() -> Result<(), void_online::Error> {
//! let online = Online::new(OnlineConfig::from_env(), reqwest::Client::new());
//! let mut events = online.subscribe();
//!
//! // `void-core`'s Microsoft sign-in produced these.
//! let player = online
//!     .sign_in(McIdentity { access_token: "<mc token>".into(), uuid: "069a79f444e94726a5befca90e38aaf5".into(), name: "Notch".into() })
//!     .await?;
//! println!("signed in as {}", player.name);
//!
//! online.set_server(Some("mc.hypixel.net".into())).await?;
//! online.equip(Slot::Cape, Some("void-cape-rift".into())).await?;
//! online.request_cosmetics(["853c80ef3c3749fdaa49938b674adae6".to_string()]);
//!
//! while let Ok(event) = events.recv().await {
//!     match event {
//!         OnlineEvent::Cosmetics(sets) => println!("{} players' cosmetics changed", sets.len()),
//!         OnlineEvent::Emote { uuid, id, .. } => println!("{uuid} played {id}"),
//!         other => println!("{other:?}"),
//!     }
//! }
//! # Ok(()) }
//! ```
//!
//! # Realtime
//!
//! `wss://<project>/realtime/v1/websocket?apikey=<anon>&vsn=1.0.0`, Phoenix v1 JSON. Every
//! channel is private, joined with the current access token, and re-sent the token whenever it
//! is refreshed. Heartbeats every 25 s; two unanswered and the socket is replaced. Reconnects
//! back off exponentially with jitter from 1 s to 30 s and rejoin every channel with the token
//! current at that moment, re-tracking presence. TLS is rustls with aws-lc-rs and the platform
//! verifier, the same stack reqwest uses here, so the process carries one crypto backend.

#![forbid(unsafe_code)]
#![warn(missing_docs)]

mod auth;
mod batcher;
mod cache;
mod config;
mod error;
mod online;
mod realtime;
mod types;

#[cfg(test)]
mod testutil;
#[cfg(test)]
mod tests;

pub use config::{AuthMode, McIdentity, OnlineConfig, DEFAULT_MOJANG_SESSION_BASE};
pub use error::Error;
pub use online::Online;
pub use types::{
    CheckoutSession, Friend, FriendPresence, FriendRequest, FriendsList, MyCosmetics, OnlineEvent, OnlineStatus,
    Player,
};
pub use void_bridge::{EquippedSet, Slot};

/// Normalise a Minecraft server address the one way the whole system does (SQL
/// `public.normalize_server` is its twin): trimmed, lowercased, trailing dots dropped, the
/// default port `25565` dropped.
///
/// `port` may also arrive inside `host` (`"Mc.Hypixel.net:25565"`) when `port` is `None`; a
/// bracketed IPv6 literal keeps its brackets. Different hostnames stay different servers:
/// `mc.hypixel.net` and `hypixel.net` are not merged.
///
/// ```
/// use void_online::normalize_server;
/// assert_eq!(normalize_server(" Mc.Hypixel.NET. ", Some(25565)), "mc.hypixel.net");
/// assert_eq!(normalize_server("play.example.org:25566", None), "play.example.org:25566");
/// ```
pub fn normalize_server(host: &str, port: Option<u16>) -> String {
    let mut host = host.trim().to_lowercase();
    let mut port = port;
    if port.is_none() {
        let split = if host.starts_with('[') {
            // "[v6]:port"
            host.rsplit_once("]:").map(|(h, p)| (format!("{h}]"), p.to_string()))
        } else if host.matches(':').count() == 1 {
            host.split_once(':').map(|(h, p)| (h.to_string(), p.to_string()))
        } else {
            None
        };
        if let Some((h, p)) = split {
            if let Ok(p) = p.trim().parse::<u16>() {
                host = h.trim().to_string();
                port = Some(p);
            }
        }
    }
    let host = host.trim_end_matches('.');
    match port {
        None | Some(25565) => host.to_string(),
        Some(p) => format!("{host}:{p}"),
    }
}

#[cfg(test)]
mod normalize_tests {
    use super::normalize_server;

    #[test]
    fn servers_normalise_one_way() {
        assert_eq!(normalize_server("mc.hypixel.net", None), "mc.hypixel.net");
        assert_eq!(normalize_server("  MC.Hypixel.Net.  ", None), "mc.hypixel.net");
        assert_eq!(normalize_server("mc.hypixel.net", Some(25565)), "mc.hypixel.net");
        assert_eq!(normalize_server("mc.hypixel.net:25565", None), "mc.hypixel.net");
        assert_eq!(normalize_server("mc.hypixel.net.:25565", None), "mc.hypixel.net");
        assert_eq!(normalize_server("Play.Example.org", Some(25566)), "play.example.org:25566");
        assert_eq!(normalize_server("play.example.org:25566", None), "play.example.org:25566");
        assert_eq!(normalize_server("hypixel.net", None), "hypixel.net", "not merged with mc.");
        assert_eq!(normalize_server("10.0.0.5:19132", None), "10.0.0.5:19132");
        assert_eq!(normalize_server("[::1]:25565", None), "[::1]");
        assert_eq!(normalize_server("[::1]:25570", None), "[::1]:25570");
        assert_eq!(normalize_server("example.org:notaport", None), "example.org:notaport");
    }
}
