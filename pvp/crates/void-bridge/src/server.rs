//! The WebSocket server on `127.0.0.1`.

use std::net::{Ipv4Addr, SocketAddr};
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Duration;

use futures_util::{SinkExt, StreamExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::{broadcast, watch};
use tokio::task::JoinHandle;
use tokio_tungstenite::tungstenite::protocol::frame::coding::CloseCode;
use tokio_tungstenite::tungstenite::protocol::CloseFrame;
use tokio_tungstenite::tungstenite::Message;

use crate::protocol::{InitSource, JavaToRust, RustToJava, PROTOCOL_VERSION};
use crate::Error;

/// How long the mod has to send `hello` after the socket opens.
const HANDSHAKE_TIMEOUT: Duration = Duration::from_secs(15);

/// Capacity of the inbound and outbound broadcast buses.
///
/// Generous, because a slow subscriber that lags loses messages: `state` deltas are
/// small and bursty when a player rattles through the mods panel.
const BUS_CAPACITY: usize = 256;

/// Number of random bytes in a session token; rendered as `2 * N` hex characters.
const TOKEN_BYTES: usize = 32;

/// Why a connection was refused during the handshake.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Rejection {
    /// The first frame was not a `hello`.
    NotHello,
    /// `hello.token` did not match the token for this spawn.
    BadToken,
    /// `hello.v` was not [`PROTOCOL_VERSION`].
    VersionMismatch,
    /// The mod did not send `hello` within [`HANDSHAKE_TIMEOUT`].
    Timeout,
    /// The socket closed or produced a frame that is not text JSON.
    Malformed,
}

impl Rejection {
    fn close_reason(self) -> &'static str {
        match self {
            Rejection::NotHello => "first frame must be hello",
            Rejection::BadToken => "bad session token",
            Rejection::VersionMismatch => "protocol version mismatch",
            Rejection::Timeout => "handshake timed out",
            Rejection::Malformed => "malformed handshake",
        }
    }
}

struct Inner {
    port: u16,
    token: String,
    inbound: broadcast::Sender<JavaToRust>,
    outbound: broadcast::Sender<Arc<str>>,
    clients: Arc<AtomicUsize>,
    accept: JoinHandle<()>,
}

impl Drop for Inner {
    fn drop(&mut self) {
        self.accept.abort();
    }
}

/// The localhost WebSocket server the mod connects back to (§6.9).
///
/// Bound to `127.0.0.1` on an OS-assigned port, with a fresh session token per spawn.
/// `void-core` passes both to the JVM as `-Dvoid.port` and `-Dvoid.token`.
///
/// Cloning is cheap and shares one server; the listener stops when the last clone is
/// dropped.
#[derive(Clone)]
pub struct BridgeServer {
    inner: Arc<Inner>,
}

impl std::fmt::Debug for BridgeServer {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("BridgeServer")
            .field("port", &self.inner.port)
            .field("clients", &self.client_count())
            .finish_non_exhaustive()
    }
}

impl BridgeServer {
    /// Binds `127.0.0.1:0` — an OS-assigned port — and starts accepting.
    pub async fn bind(init: impl InitSource) -> Result<Self, Error> {
        Self::bind_on(SocketAddr::from((Ipv4Addr::LOCALHOST, 0)), init).await
    }

    /// Binds an explicit address. Refuses anything that is not loopback: this link is
    /// authenticated by a token passed on a command line, and must never be reachable
    /// from another machine.
    pub async fn bind_on(addr: SocketAddr, init: impl InitSource) -> Result<Self, Error> {
        if !addr.ip().is_loopback() {
            return Err(Error::NotLoopback(addr));
        }
        let listener = TcpListener::bind(addr).await?;
        let port = listener.local_addr()?.port();
        let token = random_token();

        let (inbound, _) = broadcast::channel(BUS_CAPACITY);
        let (outbound, _) = broadcast::channel::<Arc<str>>(BUS_CAPACITY);
        let clients = Arc::new(AtomicUsize::new(0));

        let ctx = Arc::new(AcceptCtx {
            token: token.clone(),
            init: Box::new(init),
            inbound: inbound.clone(),
            outbound: outbound.clone(),
            clients: clients.clone(),
            generation: AtomicU64::new(0),
            current: watch::channel(0u64).0,
        });

        let accept = tokio::spawn(accept_loop(listener, ctx));
        tracing::info!(port, "void-bridge listening on 127.0.0.1");

        Ok(Self { inner: Arc::new(Inner { port, token, inbound, outbound, clients, accept }) })
    }

    /// The OS-assigned port, for `-Dvoid.port`.
    pub fn port(&self) -> u16 {
        self.inner.port
    }

    /// The session token for this spawn, for `-Dvoid.token`.
    pub fn token(&self) -> &str {
        &self.inner.token
    }

    /// The URL the mod connects to.
    pub fn url(&self) -> String {
        format!("ws://127.0.0.1:{}", self.inner.port)
    }

    /// Subscribes to inbound messages. Every authenticated frame the mod sends is
    /// broadcast here, including frames with an unknown `t`, which arrive as
    /// [`JavaToRust::Unknown`].
    pub fn subscribe(&self) -> broadcast::Receiver<JavaToRust> {
        self.inner.inbound.subscribe()
    }

    /// Sends a message to the connected mod, returning the number of receivers it
    /// reached — `0` means no mod is connected, which is not an error: the game may not
    /// be running, and everything it needs is re-sent in `init` when it connects.
    pub fn send(&self, msg: &RustToJava) -> Result<usize, Error> {
        let text: Arc<str> = Arc::from(serde_json::to_string(msg)?.as_str());
        Ok(self.inner.outbound.send(text).unwrap_or(0))
    }

    /// How many mods are connected and past the handshake. At most one, except for the
    /// instant during which a reconnecting client replaces its predecessor.
    pub fn client_count(&self) -> usize {
        self.inner.clients.load(Ordering::SeqCst)
    }
}

struct AcceptCtx {
    token: String,
    init: Box<dyn InitSource>,
    inbound: broadcast::Sender<JavaToRust>,
    outbound: broadcast::Sender<Arc<str>>,
    clients: Arc<AtomicUsize>,
    /// Monotonic id handed to each authenticated connection.
    generation: AtomicU64,
    /// The generation that owns the link; an older connection sees this change and
    /// closes, which is how a reconnect replaces the previous client (§6.9).
    current: watch::Sender<u64>,
}

async fn accept_loop(listener: TcpListener, ctx: Arc<AcceptCtx>) {
    loop {
        match listener.accept().await {
            Ok((stream, peer)) => {
                let ctx = ctx.clone();
                tokio::spawn(async move {
                    if let Err(e) = serve(stream, ctx).await {
                        tracing::debug!(%peer, error = %e, "void-bridge connection ended");
                    }
                });
            }
            Err(e) => {
                tracing::warn!(error = %e, "void-bridge accept failed");
                tokio::time::sleep(Duration::from_millis(50)).await;
            }
        }
    }
}

async fn serve(stream: TcpStream, ctx: Arc<AcceptCtx>) -> Result<(), Error> {
    let _ = stream.set_nodelay(true);
    let mut ws = tokio_tungstenite::accept_async(stream).await?;

    // --- handshake -------------------------------------------------------
    let hello = match tokio::time::timeout(HANDSHAKE_TIMEOUT, next_message(&mut ws)).await {
        Err(_) => Err(Rejection::Timeout),
        Ok(Ok(Some(msg))) => Ok(msg),
        Ok(_) => Err(Rejection::Malformed),
    };
    let verdict = hello.and_then(|msg| match msg {
        JavaToRust::Hello { v, token, .. } if v != PROTOCOL_VERSION => {
            let _ = token;
            Err(Rejection::VersionMismatch)
        }
        JavaToRust::Hello { token, .. } if !constant_time_eq(&token, &ctx.token) => {
            Err(Rejection::BadToken)
        }
        JavaToRust::Hello { mc, mod_version, v, token } => {
            Ok(JavaToRust::Hello { mc, mod_version, v, token })
        }
        _ => Err(Rejection::NotHello),
    });

    let hello = match verdict {
        Ok(hello) => hello,
        Err(reason) => {
            tracing::warn!(?reason, "void-bridge refused a connection");
            let _ = ws
                .send(Message::Close(Some(CloseFrame {
                    code: CloseCode::Policy,
                    reason: reason.close_reason().into(),
                })))
                .await;
            let _ = ws.close(None).await;
            return Err(Error::Rejected(reason));
        }
    };
    if let JavaToRust::Hello { mc, mod_version, .. } = &hello {
        tracing::info!(mc, mod_version, "void-bridge handshake ok");
    }

    // This connection now owns the link; any older one closes itself.
    let generation = ctx.generation.fetch_add(1, Ordering::SeqCst) + 1;
    let _ = ctx.current.send(generation);
    let mut current = ctx.current.subscribe();

    // Subscribe before `init` goes out, so nothing sent while we are still writing the
    // handshake reply is missed.
    let mut outbound = ctx.outbound.subscribe();
    ctx.clients.fetch_add(1, Ordering::SeqCst);
    let _guard = ClientGuard(ctx.clients.clone());

    let _ = ctx.inbound.send(hello);
    let init: RustToJava = ctx.init.init().into();
    ws.send(Message::Text(serde_json::to_string(&init)?.into())).await?;

    // --- pump ------------------------------------------------------------
    loop {
        tokio::select! {
            incoming = next_message(&mut ws) => match incoming? {
                Some(msg) => {
                    if let JavaToRust::Unknown = msg {
                        tracing::debug!("void-bridge ignoring a message with an unknown `t`");
                    }
                    let _ = ctx.inbound.send(msg);
                }
                None => break,
            },
            out = outbound.recv() => match out {
                Ok(text) => ws.send(Message::Text(text.as_ref().into())).await?,
                Err(broadcast::error::RecvError::Lagged(n)) => {
                    tracing::warn!(dropped = n, "void-bridge outbound bus lagged");
                }
                Err(broadcast::error::RecvError::Closed) => break,
            },
            changed = current.changed() => {
                if changed.is_err() || *current.borrow_and_update() != generation {
                    tracing::info!("void-bridge client replaced by a reconnect");
                    let _ = ws.send(Message::Close(Some(CloseFrame {
                        code: CloseCode::Normal,
                        reason: "replaced by a new hello".into(),
                    }))).await;
                    break;
                }
            }
        }
    }
    let _ = ws.close(None).await;
    Ok(())
}

struct ClientGuard(Arc<AtomicUsize>);

impl Drop for ClientGuard {
    fn drop(&mut self) {
        self.0.fetch_sub(1, Ordering::SeqCst);
    }
}

/// Reads the next protocol message, skipping frames that carry no protocol content.
///
/// `Ok(None)` means the peer closed. A text frame that is not valid JSON is skipped with
/// a warning rather than dropping the connection: §7 says a receiver ignores what it
/// does not understand.
async fn next_message(
    ws: &mut tokio_tungstenite::WebSocketStream<TcpStream>,
) -> Result<Option<JavaToRust>, Error> {
    while let Some(frame) = ws.next().await {
        match frame? {
            Message::Text(text) => match serde_json::from_str::<JavaToRust>(&text) {
                Ok(msg) => return Ok(Some(msg)),
                Err(e) => tracing::warn!(error = %e, "void-bridge ignoring unparseable frame"),
            },
            Message::Binary(_) => {
                tracing::warn!("void-bridge ignoring a binary frame; the protocol is JSON text");
            }
            Message::Close(_) => return Ok(None),
            Message::Ping(_) | Message::Pong(_) | Message::Frame(_) => {}
        }
    }
    Ok(None)
}

/// A fresh session token: [`TOKEN_BYTES`] random bytes, lower-case hex.
fn random_token() -> String {
    use rand::RngCore;
    let mut bytes = [0u8; TOKEN_BYTES];
    rand::rng().fill_bytes(&mut bytes);
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

/// Compares two tokens without an early exit on the first differing byte.
fn constant_time_eq(a: &str, b: &str) -> bool {
    if a.len() != b.len() {
        return false;
    }
    a.bytes().zip(b.bytes()).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tokens_are_hex_and_long_enough_for_the_schema() {
        let t = random_token();
        assert_eq!(t.len(), TOKEN_BYTES * 2);
        assert!(t.len() >= 16 && t.len() <= 256, "protocol.json bounds token length");
        assert!(t.bytes().all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase()));
        assert_ne!(t, random_token());
    }

    #[test]
    fn token_comparison_is_length_and_content_sensitive() {
        assert!(constant_time_eq("abc", "abc"));
        assert!(!constant_time_eq("abc", "abd"));
        assert!(!constant_time_eq("abc", "abcd"));
    }
}
