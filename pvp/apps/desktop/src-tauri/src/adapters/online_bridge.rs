//! The mod ⇄ `void-online` relay for one play session (docs/online-contract.md, "Bridge").
//!
//! The mod never talks HTTP; Rust owns network and auth. So for the life of a game this task
//! sits between the bridge and the online client:
//!
//! | from the mod                 | does                                                        |
//! |------------------------------|-------------------------------------------------------------|
//! | `hello`                      | sends `cosmetics` with `self` (or `null` offline) and every cached set for players on this server |
//! | `server {host,connected}`    | heartbeats the server, joins its channel; leaving leaves it |
//! | `cosmetics_query {uuids}`    | hands the UUIDs to the batcher; results come back as `cosmetics` deltas |
//! | `emote {id}`                 | the `emote` RPC, which broadcasts it to the server          |
//! | `equip {slot,id}`            | persists it; on refusal re-sends `self`, reverting the mod  |
//!
//! | from `void-online`           | sends the mod                                               |
//! |------------------------------|-------------------------------------------------------------|
//! | `SelfCosmetics`              | `cosmetics {self, players: []}`                             |
//! | `Cosmetics(sets)`            | `cosmetics {self, players: sets}`                           |
//! | `Emote`                      | `emote {uuid, id, at}`                                      |
//! | `Status` leaving online      | `cosmetics {self: null, players: []}`                       |
//!
//! It holds a clone of the bridge server, so it must end with the game or it would keep the
//! listener alive: it watches the session's `running` flag and exits (leaving the server
//! channel) once the game has closed.

use std::collections::HashSet;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::broadcast::error::RecvError;
use void_bridge::{BridgeServer, JavaToRust, RustToJava};
use void_online::{normalize_server, EquippedSet, Online, OnlineEvent, OnlineStatus};

/// How often the relay checks whether the game is still running.
const LIVENESS_POLL: Duration = Duration::from_millis(500);

fn self_set(online: &Online) -> Option<EquippedSet> {
    online.cached_my_cosmetics().map(|c| c.equipped)
}

fn send(bridge: &BridgeServer, msg: &RustToJava) {
    if let Err(e) = bridge.send(msg) {
        tracing::debug!(error = %e, t = msg.tag(), "could not send to the mod");
    }
}

/// Spawn the relay. Returns at once; the task ends after the game does.
pub fn spawn(bridge: BridgeServer, online: Online, running: Arc<AtomicBool>) -> tokio::task::JoinHandle<()> {
    tokio::spawn(run(bridge, online, running))
}

async fn run(bridge: BridgeServer, online: Online, running: Arc<AtomicBool>) {
    let mut from_mod = bridge.subscribe();
    let mut from_online = online.subscribe();
    // The players the mod has asked about, so `hello` after a reconnect can replay them.
    let mut seen: HashSet<String> = HashSet::new();
    let mut tick = tokio::time::interval(LIVENESS_POLL);
    tick.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);

    loop {
        tokio::select! {
            msg = from_mod.recv() => match msg {
                Ok(msg) => handle_mod(&bridge, &online, &mut seen, msg).await,
                Err(RecvError::Lagged(n)) => tracing::warn!(dropped = n, "online relay fell behind the mod"),
                Err(RecvError::Closed) => break,
            },
            event = from_online.recv() => match event {
                Ok(event) => handle_online(&bridge, &online, event),
                Err(RecvError::Lagged(n)) => tracing::warn!(dropped = n, "online relay fell behind void-online"),
                Err(RecvError::Closed) => break,
            },
            _ = tick.tick() => {
                if !running.load(Ordering::SeqCst) {
                    break;
                }
            }
        }
    }

    // The game is gone: so is the player from that server's channel.
    if let Err(e) = online.set_server(None).await {
        tracing::debug!(error = %e, "could not leave the server channel");
    }
}

async fn handle_mod(bridge: &BridgeServer, online: &Online, seen: &mut HashSet<String>, msg: JavaToRust) {
    match msg {
        JavaToRust::Hello { .. } => {
            let players: Vec<EquippedSet> = seen.iter().filter_map(|u| online.cached_cosmetics(u)).collect();
            send(bridge, &RustToJava::Cosmetics { self_set: self_set(online), players });
        }
        JavaToRust::Server { host, connected, port } => {
            let address = (connected && !host.trim().is_empty()).then(|| normalize_server(&host, port));
            if !connected {
                seen.clear();
            }
            if let Err(e) = online.set_server(address).await {
                tracing::info!(error = %e, "could not share presence for this server");
            }
        }
        JavaToRust::CosmeticsQuery { uuids } => {
            // Answer at once from the cache, then let the batcher fetch what is missing or
            // stale; it emits only new or changed sets, which arrive as further deltas.
            let cached: Vec<EquippedSet> = uuids.iter().filter_map(|u| online.cached_cosmetics(u)).collect();
            seen.extend(uuids.iter().cloned());
            if !cached.is_empty() {
                send(bridge, &RustToJava::Cosmetics { self_set: self_set(online), players: cached });
            }
            online.request_cosmetics(uuids);
        }
        JavaToRust::Emote { id } => {
            if let Err(e) = online.emote(&id).await {
                tracing::info!(error = %e, %id, "emote not shared");
            }
        }
        JavaToRust::Equip { slot, id } => {
            if let Err(e) = online.equip(slot, id).await {
                tracing::info!(error = %e, %slot, "equip refused; reverting the mod");
                // Java applied it optimistically: the stored set puts it back.
                send(bridge, &RustToJava::Cosmetics { self_set: self_set(online), players: vec![] });
            }
            // On success `void-online` emits `SelfCosmetics`, which echoes the stored set.
        }
        // Loadout state, HUD, globals, session stats and hotkeys are `sync::pump`'s and
        // the window forwarder's.
        _ => {}
    }
}

fn handle_online(bridge: &BridgeServer, online: &Online, event: OnlineEvent) {
    match event {
        OnlineEvent::SelfCosmetics(mine) => {
            send(bridge, &RustToJava::Cosmetics { self_set: Some(mine.equipped), players: vec![] });
        }
        OnlineEvent::Cosmetics(sets) if !sets.is_empty() => {
            send(bridge, &RustToJava::Cosmetics { self_set: self_set(online), players: sets });
        }
        OnlineEvent::Emote { uuid, id, at } => send(bridge, &RustToJava::Emote { uuid, id, at }),
        OnlineEvent::Status(status) if !matches!(status, OnlineStatus::Online { .. } | OnlineStatus::Connecting) => {
            send(bridge, &RustToJava::Cosmetics { self_set: None, players: vec![] });
        }
        _ => {}
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use futures_util::{SinkExt, StreamExt};
    use tokio_tungstenite::tungstenite::Message;
    use void_bridge::{InitPayload, StaticInit, PROTOCOL_VERSION};

    async fn bridge() -> BridgeServer {
        let loadout = void_loadout::defaults::sword_pvp();
        BridgeServer::bind(StaticInit(InitPayload {
            loadouts: vec![loadout.clone()],
            loadout,
            settings: void_loadout::GlobalSettings::factory(),
        }))
        .await
        .unwrap()
    }

    /// Connect as the mod, say hello, and return the socket after `init`.
    async fn connect(server: &BridgeServer) -> tokio_tungstenite::WebSocketStream<tokio_tungstenite::MaybeTlsStream<tokio::net::TcpStream>> {
        let (mut ws, _) = tokio_tungstenite::connect_async(format!("ws://127.0.0.1:{}", server.port())).await.unwrap();
        let hello = serde_json::json!({"t": "hello", "v": PROTOCOL_VERSION, "mc": "1.8.9", "mod": "0.1.0", "token": server.token()});
        ws.send(Message::Text(hello.to_string().into())).await.unwrap();
        let init = next_json(&mut ws).await;
        assert_eq!(init["t"], "init");
        ws
    }

    async fn next_json(
        ws: &mut tokio_tungstenite::WebSocketStream<tokio_tungstenite::MaybeTlsStream<tokio::net::TcpStream>>,
    ) -> serde_json::Value {
        loop {
            let msg = tokio::time::timeout(Duration::from_secs(5), ws.next()).await.expect("a frame in time").unwrap().unwrap();
            if let Message::Text(t) = msg {
                return serde_json::from_str(&t).unwrap();
            }
        }
    }

    #[tokio::test]
    async fn offline_the_mod_is_told_there_is_no_online_self_and_queries_are_harmless() {
        let server = bridge().await;
        let running = Arc::new(AtomicBool::new(true));
        let relay = spawn(server.clone(), Online::disabled(), running.clone());
        let mut ws = connect(&server).await;

        let first = next_json(&mut ws).await;
        assert_eq!(first, serde_json::json!({"t": "cosmetics", "self": null, "players": []}));

        // Nothing comes back for a query or an emote when there is no backend, and the link
        // survives both — and an equip is reverted with the (absent) stored set.
        for frame in [
            serde_json::json!({"t": "cosmetics_query", "uuids": ["069a79f4-44e9-4726-a5be-fca90e38aaf5"]}),
            serde_json::json!({"t": "emote", "id": "emote-wave"}),
            serde_json::json!({"t": "server", "host": "mc.hypixel.net", "connected": true}),
            serde_json::json!({"t": "equip", "slot": "cape", "id": "void-cape-rift"}),
        ] {
            ws.send(Message::Text(frame.to_string().into())).await.unwrap();
        }
        let revert = next_json(&mut ws).await;
        assert_eq!(revert, serde_json::json!({"t": "cosmetics", "self": null, "players": []}));

        // The game closes: the relay lets go of the bridge.
        running.store(false, Ordering::SeqCst);
        tokio::time::timeout(Duration::from_secs(3), relay).await.expect("relay ends with the game").unwrap();
    }
}
