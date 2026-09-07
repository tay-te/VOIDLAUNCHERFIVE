//! The launcher-attached path with the JVM spawn taken out.
//!
//! Everything the launcher does around the game is here and is the real thing: a real
//! [`BridgeServer`] over a real [`Store`], `StoreInit` answering `hello`, and
//! `void_core::sync::pump` folding what the mod sends back onto disk. What is *not* here
//! is `void_core::launch::launch` — no JVM is spawned — so a Java client can be pointed
//! at the printed port and token by hand.
//!
//! That is the seam the whole product path rests on and the one thing nothing exercised:
//! every test on either side stands up its own half and simulates the other. Here both
//! halves are real and only the process boundary is arranged by hand.
//!
//! ```text
//! cargo run --example bridge_harness --no-default-features -- /tmp/void-harness
//! READY <port> <token> <root>
//! ```
//!
//! Then, on stdin, one command per line:
//!
//! ```text
//! clients            how many mods are past the handshake
//! dump <loadout-id>  the loadout file as it is on disk right now
//! active             the active pointer
//! push <loadout-id>  send `loadout` (the tray's mid-session switch)
//! settings           send `settings`
//! quit
//! ```

use std::io::{BufRead, Write};

use void_bridge::{BridgeServer, RustToJava};
use void_core::sync::{pump, StoreInit};
use void_loadout::{LoadoutId, Store};

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_env("VOID_LOG")
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info")),
        )
        .init();

    let root = std::env::args().nth(1).expect("usage: bridge_harness <root>");
    let store = Store::at(&root);
    store.init().expect("seed the library");

    let server = BridgeServer::bind(StoreInit::new(store.clone())).await.expect("bind");
    println!("READY {} {} {}", server.port(), server.token(), root);
    let _ = std::io::stdout().flush();

    tokio::spawn(pump(server.clone(), store.clone()));

    // stdin is blocking; keep it off the runtime.
    let (tx, mut rx) = tokio::sync::mpsc::channel::<String>(16);
    std::thread::spawn(move || {
        for line in std::io::stdin().lock().lines().map_while(Result::ok) {
            if tx.blocking_send(line).is_err() {
                break;
            }
        }
    });

    while let Some(line) = rx.recv().await {
        let mut parts = line.trim().split_whitespace();
        let cmd = parts.next().unwrap_or("");
        let arg = parts.next().unwrap_or("");
        match cmd {
            "" => {}
            "clients" => println!("CLIENTS {}", server.client_count()),
            "active" => match store.active_id() {
                Ok(id) => println!("ACTIVE {id}"),
                Err(e) => println!("ERR {e}"),
            },
            "dump" => match LoadoutId::new(arg).ok_or_else(|| "bad id".to_string()).and_then(|id| {
                store.load(&id).map_err(|e| e.to_string())
            }) {
                Ok(l) => println!("DUMP {}", serde_json::to_string(&l).unwrap()),
                Err(e) => println!("ERR {e}"),
            },
            "settings" => match store.settings() {
                Ok(s) => {
                    let n = server.send(&RustToJava::Settings { settings: s }).unwrap_or(0);
                    println!("SENT settings -> {n}");
                }
                Err(e) => println!("ERR {e}"),
            },
            "push" => match LoadoutId::new(arg).ok_or_else(|| "bad id".to_string()).and_then(|id| {
                store.load(&id).map_err(|e| e.to_string())
            }) {
                Ok(l) => {
                    let n = server
                        .send(&RustToJava::Loadout { loadout: Box::new(l) })
                        .unwrap_or(0);
                    println!("SENT loadout {arg} -> {n}");
                }
                Err(e) => println!("ERR {e}"),
            },
            "quit" => break,
            other => println!("ERR unknown command {other}"),
        }
        let _ = std::io::stdout().flush();
    }
}
