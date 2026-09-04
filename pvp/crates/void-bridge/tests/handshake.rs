//! A tiny mod-shaped client: connect, handshake, receive `init`, send `state`, and check
//! the bus saw it. This is the whole §6.9 contract, exercised without a JVM.

use std::time::Duration;

use futures_util::{SinkExt, StreamExt};
use serde_json::{json, Value};
use tokio::net::TcpStream;
use tokio_tungstenite::tungstenite::Message;
use tokio_tungstenite::{MaybeTlsStream, WebSocketStream};
use void_bridge::{BridgeServer, InitPayload, JavaToRust, RustToJava, StaticInit, PROTOCOL_VERSION};
use void_loadout::{defaults, GlobalSettings, LoadoutId, ModId, StatePatch};

type Client = WebSocketStream<MaybeTlsStream<TcpStream>>;

/// Anything that hangs is a bug, not a slow machine: everything here is loopback.
const PATIENCE: Duration = Duration::from_secs(5);

fn payload() -> InitPayload {
    let library = defaults::default_library();
    InitPayload {
        loadout: library[0].clone(),
        loadouts: library.clone(),
        settings: GlobalSettings::factory(),
    }
}

async fn start() -> BridgeServer {
    BridgeServer::bind(StaticInit(payload())).await.expect("bind")
}

async fn connect(server: &BridgeServer) -> Client {
    let (ws, _) = tokio_tungstenite::connect_async(server.url()).await.expect("connect");
    ws
}

async fn send_json(ws: &mut Client, value: Value) {
    ws.send(Message::Text(value.to_string().into())).await.expect("send");
}

/// The next text frame, or `None` if the server closed the socket instead.
async fn next_text(ws: &mut Client) -> Option<Value> {
    loop {
        match tokio::time::timeout(PATIENCE, ws.next()).await.expect("no frame in time") {
            Some(Ok(Message::Text(t))) => {
                return Some(serde_json::from_str(&t).expect("server sends JSON"))
            }
            Some(Ok(Message::Close(_))) | None => return None,
            Some(Ok(_)) => continue,
            Some(Err(_)) => return None,
        }
    }
}

fn hello(token: &str, v: u32) -> Value {
    json!({ "t": "hello", "v": v, "mc": "1.8.9", "mod": "0.1.0", "token": token })
}

#[tokio::test]
async fn handshake_then_state_reaches_the_bus() {
    let server = start().await;
    assert_ne!(server.port(), 0, "the OS assigns a real port");
    assert_eq!(server.token().len(), 64);

    let mut bus = server.subscribe();
    let mut ws = connect(&server).await;
    send_json(&mut ws, hello(server.token(), PROTOCOL_VERSION)).await;

    // The launcher answers `hello` with the whole world of state.
    let init = next_text(&mut ws).await.expect("init");
    assert_eq!(init["t"], "init");
    assert_eq!(init["v"], PROTOCOL_VERSION);
    assert_eq!(init["loadout"]["id"], "sword-pvp");
    assert_eq!(init["loadouts"].as_array().unwrap().len(), 3);
    // Whole loadouts, not summaries: the mod switches to any of them without asking.
    assert!(init["loadouts"][1]["mods"].is_object(), "init.loadouts carries full loadouts");
    assert!(init["loadouts"][1]["hud"].is_array(), "init.loadouts carries full loadouts");
    assert_eq!(init["settings"]["menu_key"], "RSHIFT");
    // It must parse as the real type, not just look right.
    let parsed: RustToJava = serde_json::from_value(init).expect("init is a RustToJava");
    assert_eq!(parsed.tag(), "init");

    // `hello` itself is published, so the launcher can log the mod build.
    let first = tokio::time::timeout(PATIENCE, bus.recv()).await.unwrap().unwrap();
    assert!(matches!(first, JavaToRust::Hello { .. }));

    send_json(
        &mut ws,
        json!({
            "t": "state",
            "loadout": "sword-pvp",
            "patch": { "mods.fullbright.on": true }
        }),
    )
    .await;

    let msg = tokio::time::timeout(PATIENCE, bus.recv()).await.unwrap().unwrap();
    match msg {
        JavaToRust::State { loadout, patch } => {
            assert_eq!(loadout, LoadoutId::new("sword-pvp").unwrap());
            assert_eq!(patch.entries()["mods.fullbright.on"], Value::Bool(true));
        }
        other => panic!("expected state, got {}", other.tag()),
    }
}

#[tokio::test]
async fn outbound_messages_reach_the_connected_mod() {
    let server = start().await;
    let mut ws = connect(&server).await;
    send_json(&mut ws, hello(server.token(), PROTOCOL_VERSION)).await;
    next_text(&mut ws).await.expect("init");

    let bedwars = defaults::bedwars();
    assert_eq!(server.send(&RustToJava::Loadout { loadout: Box::new(bedwars) }).unwrap(), 1);

    let switched = next_text(&mut ws).await.expect("loadout");
    assert_eq!(switched["t"], "loadout");
    assert_eq!(switched["loadout"]["id"], "bedwars");
}

#[tokio::test]
async fn a_bad_token_is_closed_without_init() {
    let server = start().await;
    let mut ws = connect(&server).await;
    send_json(&mut ws, hello("0000000000000000", PROTOCOL_VERSION)).await;
    assert!(next_text(&mut ws).await.is_none(), "no state may leak to an unauthenticated peer");
    assert_eq!(server.client_count(), 0);
}

#[tokio::test]
async fn a_version_mismatch_is_closed_without_init() {
    let server = start().await;
    let mut ws = connect(&server).await;
    send_json(&mut ws, hello(server.token(), PROTOCOL_VERSION + 1)).await;
    assert!(next_text(&mut ws).await.is_none(), "a v mismatch must not proceed");
}

#[tokio::test]
async fn a_first_frame_that_is_not_hello_is_closed() {
    let server = start().await;
    let mut ws = connect(&server).await;
    send_json(&mut ws, json!({ "t": "session", "fps_avg": 142, "played_ms": 1000 })).await;
    assert!(next_text(&mut ws).await.is_none());
}

#[tokio::test]
async fn a_reconnect_replaces_the_previous_client() {
    let server = start().await;

    let mut first = connect(&server).await;
    send_json(&mut first, hello(server.token(), PROTOCOL_VERSION)).await;
    next_text(&mut first).await.expect("init");

    let mut second = connect(&server).await;
    send_json(&mut second, hello(server.token(), PROTOCOL_VERSION)).await;
    next_text(&mut second).await.expect("init for the reconnect");

    // The old socket is closed by the server, not left to linger.
    assert!(next_text(&mut first).await.is_none(), "the first client should be replaced");

    // And the new one still works.
    let mut bus = server.subscribe();
    send_json(&mut second, json!({ "t": "server", "host": "mc.hypixel.net", "connected": true }))
        .await;
    let msg = tokio::time::timeout(PATIENCE, bus.recv()).await.unwrap().unwrap();
    assert!(matches!(msg, JavaToRust::Server { connected: true, .. }));
}

#[tokio::test]
async fn unknown_messages_and_junk_frames_do_not_drop_the_link() {
    let server = start().await;
    let mut bus = server.subscribe();
    let mut ws = connect(&server).await;
    send_json(&mut ws, hello(server.token(), PROTOCOL_VERSION)).await;
    next_text(&mut ws).await.expect("init");
    let _ = tokio::time::timeout(PATIENCE, bus.recv()).await.unwrap().unwrap(); // hello

    // Forward compatibility (§7): an unknown `t`, then unparseable text, then a real
    // message that must still arrive.
    send_json(&mut ws, json!({ "t": "telemetry_v2", "whatever": 1 })).await;
    ws.send(Message::Text("not json".into())).await.unwrap();
    send_json(&mut ws, json!({ "t": "session", "fps_avg": 142.0, "played_ms": 60000 })).await;

    let unknown = tokio::time::timeout(PATIENCE, bus.recv()).await.unwrap().unwrap();
    assert_eq!(unknown, JavaToRust::Unknown);
    let session = tokio::time::timeout(PATIENCE, bus.recv()).await.unwrap().unwrap();
    assert!(matches!(session, JavaToRust::Session { played_ms: 60000, .. }));
}

#[tokio::test]
async fn hud_and_patch_payloads_survive_the_trip_as_typed_values() {
    let server = start().await;
    let mut bus = server.subscribe();
    let mut ws = connect(&server).await;
    send_json(&mut ws, hello(server.token(), PROTOCOL_VERSION)).await;
    next_text(&mut ws).await.expect("init");
    let _ = tokio::time::timeout(PATIENCE, bus.recv()).await.unwrap().unwrap(); // hello

    send_json(
        &mut ws,
        json!({
            "t": "hud",
            "loadout": "sword-pvp",
            "items": [{ "id": "keystrokes", "anchor": "bottom-left", "dx": 32, "dy": -40, "scale": 1 }]
        }),
    )
    .await;
    match tokio::time::timeout(PATIENCE, bus.recv()).await.unwrap().unwrap() {
        JavaToRust::Hud { items, .. } => {
            assert_eq!(items.len(), 1);
            assert_eq!(items[0].dx, 32.0);
            assert_eq!(items[0].effective_scale(), 1.0);
        }
        other => panic!("expected hud, got {}", other.tag()),
    }

    // A patch built in Rust is the same shape the mod sends.
    let mut patch = StatePatch::new();
    patch.insert(ModId::Zoom, "key", "V");
    assert_eq!(
        serde_json::to_value(&patch).unwrap(),
        json!({ "mods.zoom.key": "V" })
    );
}
