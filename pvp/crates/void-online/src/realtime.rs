//! Supabase Realtime: Phoenix channels over one websocket.
//!
//! One [`Realtime`] owns one socket and any number of channels on it. The rest of the crate
//! talks to it through a command channel ([`Realtime::join`], [`Realtime::leave`]) and a
//! [`tokio::sync::watch`] of the current access token, and hears back through
//! [`RtEvent`]s. Nothing here knows what a channel *means*; `online.rs` decides that.
//!
//! # Wire format (Phoenix v1 JSON, `vsn=1.0.0`)
//!
//! Every frame is `{"topic","event","payload","ref","join_ref"}`. Topics on the wire carry a
//! `realtime:` prefix that this module adds and strips, so callers say `user:<uuid>`.
//!
//! - **join** `phx_join` with `{"config":{"broadcast":{"ack":false,"self":false},
//!   "presence":{"key":<uuid>,"enabled":<tracking?>},"postgres_changes":[],"private":true},
//!   "access_token":<jwt>}`. Every VOID channel is private, so the token is what
//!   `realtime.messages` RLS checks.
//! - **heartbeat** on topic `phoenix` every 25 s. Two unanswered in a row and the socket is
//!   presumed dead (a half-open TCP connection never errors on its own) and replaced.
//! - **presence track** `presence` with `{"type":"presence","event":"track","payload":<meta>}`,
//!   sent after every successful join of a channel that tracks, so a reconnect re-tracks.
//! - **token refresh** `access_token` with `{"access_token":..}` on every joined channel
//!   whenever the watch changes. Without it the server closes channels when the JWT expires.
//! - incoming `phx_reply` (join ok/error, heartbeat ack), `presence_state` (the full set),
//!   `presence_diff` (`joins`/`leaves`), `broadcast` (`{"type":"broadcast","event",
//!   "payload",..}`, extra keys like `meta` ignored), `phx_error`/`phx_close` (the server
//!   dropped the channel: rejoin), `system` (logged).
//!
//! # Failure handling
//!
//! - The socket drops, errors, or misses heartbeats → reconnect with exponential backoff and
//!   jitter, 1 s doubling to a 30 s cap, reset once a connection has carried a message.
//!   Every channel is rejoined with the token current *at that moment*, so a reconnect after
//!   a refresh never presents an expired JWT.
//! - A join is refused or a channel is closed under us → that channel alone rejoins on its
//!   own backoff; the socket and the other channels are untouched.
//!
//! # TLS
//!
//! `wss://` goes through `tokio-rustls` with aws-lc-rs and `rustls-platform-verifier`, the
//! same stack reqwest uses in this tree, so the websocket trusts exactly what HTTP trusts and
//! no second crypto backend is linked. tokio-tungstenite itself is built without TLS
//! features and handed the already-encrypted stream.

use std::collections::{BTreeMap, BTreeSet};
use std::sync::{Arc, OnceLock};
use std::time::Duration;

use futures_util::{SinkExt, StreamExt};
use rand::Rng;
use serde_json::{json, Value};
use tokio::io::{AsyncRead, AsyncWrite};
use tokio::net::TcpStream;
use tokio::sync::{mpsc, watch};
use tokio::task::JoinHandle;
use tokio::time::Instant;
use tokio_rustls::rustls::{self, pki_types::ServerName, ClientConfig};
use tokio_tungstenite::tungstenite::Message;
use tokio_tungstenite::WebSocketStream;

use crate::Error;

/// The knobs, so tests can run the same code at millisecond scale.
#[derive(Debug, Clone, Copy)]
pub(crate) struct RealtimeTimings {
    pub heartbeat: Duration,
    pub backoff_base: Duration,
    pub backoff_cap: Duration,
    pub connect_timeout: Duration,
}

impl Default for RealtimeTimings {
    fn default() -> Self {
        RealtimeTimings {
            heartbeat: Duration::from_secs(25),
            backoff_base: Duration::from_secs(1),
            backoff_cap: Duration::from_secs(30),
            connect_timeout: Duration::from_secs(10),
        }
    }
}

/// What the socket tells the rest of the crate.
#[derive(Debug, Clone, PartialEq)]
pub(crate) enum RtEvent {
    /// A channel's join was accepted (initially or after a rejoin).
    Joined { topic: String },
    /// A broadcast arrived: `event` and its inner `payload`.
    Broadcast { topic: String, event: String, payload: Value },
    /// A channel's presence set changed; `keys` is the whole current set, sorted.
    Presence { topic: String, keys: Vec<String> },
}

enum Cmd {
    Join { topic: String, presence: Option<Value> },
    Leave { topic: String },
}

/// Handle to the socket task. Dropping it closes the socket and ends the task.
pub(crate) struct Realtime {
    cmd: mpsc::UnboundedSender<Cmd>,
    _task: JoinHandle<()>,
}

impl Realtime {
    /// Start the socket task. `key` is the presence key (the player's UUID) on every channel.
    pub(crate) fn spawn(
        url: String,
        key: String,
        token: watch::Receiver<String>,
        events: mpsc::UnboundedSender<RtEvent>,
        timings: RealtimeTimings,
    ) -> Realtime {
        let (cmd, rx) = mpsc::unbounded_channel();
        let task = Task { url, key, token, events, timings, rx, channels: BTreeMap::new(), next_ref: 0 };
        Realtime { cmd, _task: tokio::spawn(task.run()) }
    }

    /// Join `topic` (no `realtime:` prefix), tracking `presence` on it when given. Joining a
    /// topic already joined replaces its presence meta and rejoins.
    pub(crate) fn join(&self, topic: &str, presence: Option<Value>) {
        let _ = self.cmd.send(Cmd::Join { topic: topic.to_string(), presence });
    }

    /// Leave `topic`. Its events stop at once, whatever the server does with the `phx_leave`.
    pub(crate) fn leave(&self, topic: &str) {
        let _ = self.cmd.send(Cmd::Leave { topic: topic.to_string() });
    }
}

/// `base · 2^(attempt-1)` capped, then a uniform pick from its upper half so a fleet of
/// launchers that lost the same server does not come back in lockstep.
pub(crate) fn backoff(attempt: u32, base: Duration, cap: Duration) -> Duration {
    let exp = base.saturating_mul(1u32 << attempt.saturating_sub(1).min(16)).min(cap);
    let lo = exp / 2;
    let span = exp.saturating_sub(lo).as_millis() as u64;
    lo + Duration::from_millis(if span == 0 { 0 } else { rand::rng().random_range(0..=span) })
}

struct Channel {
    presence: Option<Value>,
    join_ref: Option<String>,
    joined: bool,
    attempts: u32,
    rejoin_at: Option<Instant>,
    /// presence key → metas
    members: BTreeMap<String, Vec<Value>>,
}

impl Channel {
    fn new(presence: Option<Value>) -> Self {
        Channel { presence, join_ref: None, joined: false, attempts: 0, rejoin_at: None, members: BTreeMap::new() }
    }
}

trait Io: AsyncRead + AsyncWrite + Unpin + Send {}
impl<T: AsyncRead + AsyncWrite + Unpin + Send> Io for T {}
type Ws = WebSocketStream<Box<dyn Io>>;

/// Why a connected session ended.
enum Ended {
    /// The handle is gone: stop for good.
    Exit,
    /// The socket died. `healthy` if it carried at least one message first.
    Dropped { healthy: bool },
}

struct Task {
    url: String,
    key: String,
    token: watch::Receiver<String>,
    events: mpsc::UnboundedSender<RtEvent>,
    timings: RealtimeTimings,
    rx: mpsc::UnboundedReceiver<Cmd>,
    channels: BTreeMap<String, Channel>,
    next_ref: u64,
}

impl Task {
    async fn run(mut self) {
        let mut attempt = 0u32;
        loop {
            let connecting = connect(self.url.clone(), self.timings.connect_timeout);
            tokio::pin!(connecting);
            let result = loop {
                tokio::select! {
                    r = &mut connecting => break r,
                    cmd = self.rx.recv() => match cmd {
                        Some(cmd) => self.apply_offline(cmd),
                        None => return,
                    },
                }
            };
            match result {
                Ok(ws) => match self.session(ws).await {
                    Ended::Exit => return,
                    Ended::Dropped { healthy } => {
                        tracing::info!("realtime socket closed; reconnecting");
                        if healthy {
                            attempt = 0;
                        }
                    }
                },
                Err(e) => tracing::warn!(error = ?e, "realtime connect failed"),
            }
            attempt = attempt.saturating_add(1);
            let delay = backoff(attempt, self.timings.backoff_base, self.timings.backoff_cap);
            let wake = tokio::time::sleep(delay);
            tokio::pin!(wake);
            loop {
                tokio::select! {
                    _ = &mut wake => break,
                    cmd = self.rx.recv() => match cmd {
                        Some(cmd) => self.apply_offline(cmd),
                        None => return,
                    },
                }
            }
        }
    }

    /// Record a command while there is no socket; the next connect acts on it.
    fn apply_offline(&mut self, cmd: Cmd) {
        match cmd {
            Cmd::Join { topic, presence } => {
                self.channels.insert(topic, Channel::new(presence));
            }
            Cmd::Leave { topic } => {
                self.channels.remove(&topic);
            }
        }
    }

    fn make_ref(&mut self) -> String {
        self.next_ref += 1;
        self.next_ref.to_string()
    }

    async fn send(ws: &mut Ws, frame: Value) -> Result<(), ()> {
        ws.send(Message::text(frame.to_string())).await.map_err(|e| tracing::debug!(error = %e, "realtime send failed"))
    }

    async fn send_join(&mut self, ws: &mut Ws, topic: &str) -> Result<(), ()> {
        let r = self.make_ref();
        let token = self.token.borrow().clone();
        let Some(ch) = self.channels.get_mut(topic) else { return Ok(()) };
        ch.join_ref = Some(r.clone());
        ch.joined = false;
        ch.rejoin_at = None;
        let frame = json!({
            "topic": format!("realtime:{topic}"),
            "event": "phx_join",
            "payload": {
                "config": {
                    "broadcast": { "ack": false, "self": false },
                    "presence": { "key": self.key, "enabled": ch.presence.is_some() },
                    "postgres_changes": [],
                    "private": true,
                },
                "access_token": token,
            },
            "ref": r,
            "join_ref": r,
        });
        Self::send(ws, frame).await
    }

    async fn session(&mut self, mut ws: Ws) -> Ended {
        // Mark the current token seen: it goes out in the joins below, so a `changed()` for
        // it would only push it twice.
        self.token.borrow_and_update();
        let topics: Vec<String> = self.channels.keys().cloned().collect();
        for topic in &topics {
            if let Some(ch) = self.channels.get_mut(topic) {
                ch.members.clear();
            }
            if self.send_join(&mut ws, topic).await.is_err() {
                return Ended::Dropped { healthy: false };
            }
        }

        let period = self.timings.heartbeat;
        let mut heartbeat = tokio::time::interval_at(Instant::now() + period, period);
        heartbeat.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
        let mut unanswered = 0u32;
        let mut healthy = false;
        let far = Instant::now() + Duration::from_secs(86_400 * 365);

        loop {
            let next_rejoin = self.channels.values().filter_map(|c| c.rejoin_at).min();
            let step: Result<(), ()> = tokio::select! {
                _ = heartbeat.tick() => {
                    if unanswered >= 2 {
                        tracing::warn!("realtime heartbeats unanswered; replacing the socket");
                        return Ended::Dropped { healthy };
                    }
                    unanswered += 1;
                    let r = self.make_ref();
                    Self::send(&mut ws, json!({"topic":"phoenix","event":"heartbeat","payload":{},"ref":r})).await
                }
                cmd = self.rx.recv() => match cmd {
                    None => {
                        let _ = ws.close(None).await;
                        return Ended::Exit;
                    }
                    Some(Cmd::Join { topic, presence }) => {
                        self.channels.insert(topic.clone(), Channel::new(presence));
                        self.send_join(&mut ws, &topic).await
                    }
                    Some(Cmd::Leave { topic }) => match self.channels.remove(&topic) {
                        Some(Channel { join_ref: Some(jr), .. }) => {
                            let r = self.make_ref();
                            Self::send(&mut ws, json!({
                                "topic": format!("realtime:{topic}"), "event": "phx_leave",
                                "payload": {}, "ref": r, "join_ref": jr,
                            })).await
                        }
                        _ => Ok(()),
                    },
                },
                changed = self.token.changed() => match changed {
                    Err(_) => {
                        let _ = ws.close(None).await;
                        return Ended::Exit;
                    }
                    Ok(()) => self.push_token(&mut ws).await,
                },
                _ = tokio::time::sleep_until(next_rejoin.unwrap_or(far)), if next_rejoin.is_some() => {
                    let now = Instant::now();
                    let due: Vec<String> = self.channels.iter()
                        .filter(|(_, c)| c.rejoin_at.is_some_and(|t| t <= now))
                        .map(|(t, _)| t.clone())
                        .collect();
                    let mut r = Ok(());
                    for topic in due {
                        r = r.and(self.send_join(&mut ws, &topic).await);
                    }
                    r
                }
                frame = ws.next() => match frame {
                    Some(Ok(Message::Text(text))) => {
                        healthy = true;
                        self.handle(&mut ws, text.as_str(), &mut unanswered).await
                    }
                    Some(Ok(Message::Close(_))) | None => return Ended::Dropped { healthy },
                    Some(Ok(_)) => Ok(()), // ping/pong (tungstenite answers pings), binary
                    Some(Err(e)) => {
                        tracing::debug!(error = %e, "realtime read failed");
                        return Ended::Dropped { healthy };
                    }
                },
            };
            if step.is_err() {
                return Ended::Dropped { healthy };
            }
        }
    }

    async fn push_token(&mut self, ws: &mut Ws) -> Result<(), ()> {
        let token = self.token.borrow_and_update().clone();
        let joined: Vec<(String, String)> = self
            .channels
            .iter()
            .filter(|(_, c)| c.joined)
            .filter_map(|(t, c)| c.join_ref.clone().map(|jr| (t.clone(), jr)))
            .collect();
        for (topic, jr) in joined {
            let r = self.make_ref();
            let frame = json!({
                "topic": format!("realtime:{topic}"), "event": "access_token",
                "payload": { "access_token": token }, "ref": r, "join_ref": jr,
            });
            Self::send(ws, frame).await?;
        }
        Ok(())
    }

    fn schedule_rejoin(&mut self, topic: &str) {
        let (base, cap) = (self.timings.backoff_base, self.timings.backoff_cap);
        if let Some(ch) = self.channels.get_mut(topic) {
            ch.joined = false;
            ch.join_ref = None;
            ch.attempts = ch.attempts.saturating_add(1);
            ch.rejoin_at = Some(Instant::now() + backoff(ch.attempts, base, cap));
        }
    }

    fn emit_presence(&self, topic: &str) {
        if let Some(ch) = self.channels.get(topic) {
            let keys = ch.members.keys().cloned().collect();
            let _ = self.events.send(RtEvent::Presence { topic: topic.to_string(), keys });
        }
    }

    async fn handle(&mut self, ws: &mut Ws, text: &str, unanswered: &mut u32) -> Result<(), ()> {
        let Ok(frame) = serde_json::from_str::<Value>(text) else {
            tracing::debug!("realtime: unparseable frame ignored");
            return Ok(());
        };
        let wire_topic = frame.get("topic").and_then(Value::as_str).unwrap_or_default();
        let event = frame.get("event").and_then(Value::as_str).unwrap_or_default();
        let payload = frame.get("payload").cloned().unwrap_or(Value::Null);
        let reference = ref_string(frame.get("ref"));

        if wire_topic == "phoenix" {
            if event == "phx_reply" {
                *unanswered = 0;
            }
            return Ok(());
        }
        let topic = wire_topic.strip_prefix("realtime:").unwrap_or(wire_topic).to_string();
        let Some(ch) = self.channels.get_mut(&topic) else {
            return Ok(()); // a channel we already left
        };

        match event {
            "phx_reply" => {
                if reference.is_none() || reference != ch.join_ref {
                    return Ok(()); // a reply to a track or token push
                }
                if payload.get("status").and_then(Value::as_str) == Some("ok") {
                    ch.joined = true;
                    ch.attempts = 0;
                    let track = ch.presence.clone().zip(ch.join_ref.clone());
                    let _ = self.events.send(RtEvent::Joined { topic: topic.clone() });
                    if let Some((meta, jr)) = track {
                        let r = self.make_ref();
                        let frame = json!({
                            "topic": wire_topic, "event": "presence",
                            "payload": { "type": "presence", "event": "track", "payload": meta },
                            "ref": r, "join_ref": jr,
                        });
                        return Self::send(ws, frame).await;
                    }
                } else {
                    let response = payload.get("response").cloned().unwrap_or_default();
                    tracing::warn!(topic, %response, "realtime join refused");
                    self.schedule_rejoin(&topic);
                }
            }
            "phx_error" | "phx_close" => {
                tracing::info!(topic, event, "realtime channel closed by the server; rejoining");
                ch.members.clear();
                self.emit_presence(&topic);
                self.schedule_rejoin(&topic);
            }
            "presence_state" => {
                ch.members.clear();
                if let Some(map) = payload.as_object() {
                    for (key, v) in map {
                        ch.members.insert(key.clone(), metas(v));
                    }
                }
                self.emit_presence(&topic);
            }
            "presence_diff" => {
                if let Some(joins) = payload.get("joins").and_then(Value::as_object) {
                    for (key, v) in joins {
                        let entry = ch.members.entry(key.clone()).or_default();
                        for meta in metas(v) {
                            let r = meta.get("phx_ref").cloned();
                            if r.is_none() || !entry.iter().any(|m| m.get("phx_ref") == r.as_ref()) {
                                entry.push(meta);
                            }
                        }
                    }
                }
                if let Some(leaves) = payload.get("leaves").and_then(Value::as_object) {
                    for (key, v) in leaves {
                        let gone: BTreeSet<String> =
                            metas(v).iter().filter_map(|m| ref_string(m.get("phx_ref"))).collect();
                        let empty = match ch.members.get_mut(key) {
                            Some(cur) if !gone.is_empty() => {
                                cur.retain(|m| !ref_string(m.get("phx_ref")).is_some_and(|r| gone.contains(&r)));
                                cur.is_empty()
                            }
                            Some(_) => true,
                            None => false,
                        };
                        if empty {
                            ch.members.remove(key);
                        }
                    }
                }
                self.emit_presence(&topic);
            }
            "broadcast" => {
                let inner_event = payload.get("event").and_then(Value::as_str).unwrap_or_default().to_string();
                let inner = payload.get("payload").cloned().unwrap_or(Value::Null);
                let _ = self.events.send(RtEvent::Broadcast { topic, event: inner_event, payload: inner });
            }
            "system" => {
                let status = payload.get("status").and_then(Value::as_str).unwrap_or_default();
                if status == "error" {
                    tracing::warn!(topic, message = %payload, "realtime system message");
                } else {
                    tracing::debug!(topic, message = %payload, "realtime system message");
                }
            }
            other => tracing::trace!(topic, event = other, "realtime: event ignored"),
        }
        Ok(())
    }
}

fn metas(v: &Value) -> Vec<Value> {
    v.get("metas").and_then(Value::as_array).cloned().unwrap_or_default()
}

/// Phoenix refs are strings, but tolerate numbers.
fn ref_string(v: Option<&Value>) -> Option<String> {
    match v? {
        Value::String(s) => Some(s.clone()),
        Value::Number(n) => Some(n.to_string()),
        _ => None,
    }
}

fn tls_config() -> Result<Arc<ClientConfig>, Error> {
    use rustls_platform_verifier::BuilderVerifierExt;
    static CONFIG: OnceLock<Result<Arc<ClientConfig>, String>> = OnceLock::new();
    CONFIG
        .get_or_init(|| {
            let provider = Arc::new(rustls::crypto::aws_lc_rs::default_provider());
            let config = ClientConfig::builder_with_provider(provider)
                .with_safe_default_protocol_versions()
                .map_err(|e| e.to_string())?
                .with_platform_verifier()
                .map_err(|e| e.to_string())?
                .with_no_client_auth();
            Ok(Arc::new(config))
        })
        .clone()
        .map_err(Error::Realtime)
}

async fn connect(url: String, timeout: Duration) -> Result<Ws, Error> {
    let attempt = async {
        let parsed = reqwest::Url::parse(&url).map_err(|e| Error::Realtime(format!("bad url: {e}")))?;
        let host = parsed.host_str().ok_or_else(|| Error::Realtime("url has no host".into()))?;
        let host = host.trim_start_matches('[').trim_end_matches(']').to_string();
        let port = parsed.port_or_known_default().ok_or_else(|| Error::Realtime("url has no port".into()))?;
        let tcp = TcpStream::connect((host.as_str(), port)).await.map_err(|e| Error::Realtime(e.to_string()))?;
        let _ = tcp.set_nodelay(true);
        let io: Box<dyn Io> = match parsed.scheme() {
            "ws" => Box::new(tcp),
            "wss" => {
                let name = ServerName::try_from(host).map_err(|e| Error::Realtime(e.to_string()))?;
                let tls = tokio_rustls::TlsConnector::from(tls_config()?)
                    .connect(name, tcp)
                    .await
                    .map_err(|e| Error::Realtime(e.to_string()))?;
                Box::new(tls)
            }
            other => return Err(Error::Realtime(format!("unsupported scheme {other}"))),
        };
        let (ws, _) =
            tokio_tungstenite::client_async(url, io).await.map_err(|e| Error::Realtime(e.to_string()))?;
        Ok(ws)
    };
    tokio::time::timeout(timeout, attempt).await.map_err(|_| Error::Realtime("connect timed out".into()))?
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
    use std::sync::Mutex;
    use tokio::net::TcpListener;

    pub(crate) enum Ctl {
        Send(Value),
        Drop,
    }

    /// A fake Supabase Realtime: accepts websockets, answers joins and heartbeats when told
    /// to, records every frame, and can push frames or drop the connection.
    pub(crate) struct FakePhoenix {
        pub url: String,
        pub frames: mpsc::UnboundedReceiver<(usize, Value)>,
        /// Frames `expect` skipped, oldest first, so tests need not guess arrival order.
        backlog: Vec<(usize, Value)>,
        current: Arc<Mutex<Option<mpsc::UnboundedSender<Ctl>>>>,
        pub answer_heartbeats: Arc<AtomicBool>,
        /// Refuse this many joins before accepting.
        pub refuse_joins: Arc<AtomicUsize>,
        pub connections: Arc<AtomicUsize>,
    }

    impl FakePhoenix {
        pub(crate) async fn start() -> FakePhoenix {
            let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
            let url = format!("ws://{}/realtime/v1/websocket?apikey=anon&vsn=1.0.0", listener.local_addr().unwrap());
            let (ftx, frames) = mpsc::unbounded_channel();
            let current: Arc<Mutex<Option<mpsc::UnboundedSender<Ctl>>>> = Arc::new(Mutex::new(None));
            let answer_heartbeats = Arc::new(AtomicBool::new(true));
            let refuse_joins = Arc::new(AtomicUsize::new(0));
            let connections = Arc::new(AtomicUsize::new(0));
            let (cur, hb, refuse, conns) =
                (current.clone(), answer_heartbeats.clone(), refuse_joins.clone(), connections.clone());
            tokio::spawn(async move {
                while let Ok((tcp, _)) = listener.accept().await {
                    let Ok(mut ws) = tokio_tungstenite::accept_async(tcp).await else { continue };
                    let id = conns.fetch_add(1, Ordering::SeqCst) + 1;
                    let (ctl_tx, mut ctl_rx) = mpsc::unbounded_channel();
                    *cur.lock().unwrap() = Some(ctl_tx);
                    let (ftx, hb, refuse) = (ftx.clone(), hb.clone(), refuse.clone());
                    tokio::spawn(async move {
                        loop {
                            tokio::select! {
                                frame = ws.next() => {
                                    let Some(Ok(Message::Text(t))) = frame else { return };
                                    let v: Value = serde_json::from_str(t.as_str()).unwrap();
                                    let event = v["event"].as_str().unwrap_or_default().to_string();
                                    let reply = |status: &str| json!({
                                        "topic": v["topic"], "event": "phx_reply", "ref": v["ref"],
                                        "join_ref": v["join_ref"],
                                        "payload": {"status": status, "response": {}},
                                    });
                                    let answer = match event.as_str() {
                                        "heartbeat" if hb.load(Ordering::SeqCst) => Some(reply("ok")),
                                        "phx_join" => {
                                            let n = refuse.load(Ordering::SeqCst);
                                            let refuse_this = n > 0
                                                && refuse.compare_exchange(n, n - 1, Ordering::SeqCst, Ordering::SeqCst).is_ok();
                                            Some(reply(if refuse_this { "error" } else { "ok" }))
                                        }
                                        _ => None,
                                    };
                                    let _ = ftx.send((id, v));
                                    if let Some(a) = answer {
                                        if ws.send(Message::text(a.to_string())).await.is_err() { return }
                                    }
                                }
                                ctl = ctl_rx.recv() => match ctl {
                                    Some(Ctl::Send(v)) => { let _ = ws.send(Message::text(v.to_string())).await; }
                                    Some(Ctl::Drop) | None => return, // dropped without a close frame
                                }
                            }
                        }
                    });
                }
            });
            FakePhoenix { url, frames, backlog: Vec::new(), current, answer_heartbeats, refuse_joins, connections }
        }

        pub(crate) fn push(&self, v: Value) {
            if let Some(tx) = self.current.lock().unwrap().as_ref() {
                let _ = tx.send(Ctl::Send(v));
            }
        }

        pub(crate) fn drop_connection(&self) {
            if let Some(tx) = self.current.lock().unwrap().take() {
                let _ = tx.send(Ctl::Drop);
            }
        }

        /// The oldest recorded frame matching `pred` that no earlier `expect` took; frames
        /// that do not match are kept for later calls. Panics after 5 s.
        pub(crate) async fn expect(&mut self, what: &str, pred: impl Fn(usize, &Value) -> bool) -> (usize, Value) {
            if let Some(i) = self.backlog.iter().position(|(id, v)| pred(*id, v)) {
                return self.backlog.remove(i);
            }
            let deadline = Instant::now() + Duration::from_secs(5);
            loop {
                match tokio::time::timeout_at(deadline, self.frames.recv()).await {
                    Ok(Some((id, v))) if pred(id, &v) => return (id, v),
                    Ok(Some(other)) => self.backlog.push(other),
                    _ => panic!("timed out waiting for {what}"),
                }
            }
        }
    }

    pub(crate) fn fast() -> RealtimeTimings {
        RealtimeTimings {
            heartbeat: Duration::from_millis(150),
            backoff_base: Duration::from_millis(40),
            backoff_cap: Duration::from_millis(200),
            connect_timeout: Duration::from_secs(2),
        }
    }

    pub(crate) async fn next_event(
        rx: &mut mpsc::UnboundedReceiver<RtEvent>,
        pred: impl Fn(&RtEvent) -> bool,
    ) -> RtEvent {
        let deadline = Instant::now() + Duration::from_secs(5);
        loop {
            match tokio::time::timeout_at(deadline, rx.recv()).await {
                Ok(Some(e)) if pred(&e) => return e,
                Ok(Some(_)) => continue,
                _ => panic!("timed out waiting for a realtime event"),
            }
        }
    }

    fn is(event: &'static str, topic: &'static str) -> impl Fn(usize, &Value) -> bool {
        move |_, v| v["event"] == event && v["topic"] == topic
    }

    const ME: &str = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    struct Rig {
        fake: FakePhoenix,
        rt: Realtime,
        token: watch::Sender<String>,
        events: mpsc::UnboundedReceiver<RtEvent>,
    }

    async fn rig() -> Rig {
        let fake = FakePhoenix::start().await;
        let (token, token_rx) = watch::channel("t1".to_string());
        let (etx, events) = mpsc::unbounded_channel();
        let rt = Realtime::spawn(fake.url.clone(), ME.into(), token_rx, etx, fast());
        Rig { fake, rt, token, events }
    }

    #[tokio::test]
    async fn joins_privately_tracks_presence_and_heartbeats() {
        let mut r = rig().await;
        r.rt.join("user:me", None);
        r.rt.join("server:abc", Some(json!({"uuid": ME, "name": "Notch"})));

        let (_, j) = r.fake.expect("user join", is("phx_join", "realtime:user:me")).await;
        assert_eq!(j["payload"]["access_token"], "t1");
        assert_eq!(j["payload"]["config"]["private"], true);
        assert_eq!(j["payload"]["config"]["presence"], json!({"key": ME, "enabled": false}));
        assert_eq!(j["payload"]["config"]["broadcast"], json!({"ack": false, "self": false}));
        assert_eq!(j["ref"], j["join_ref"]);

        let (_, j) = r.fake.expect("server join", is("phx_join", "realtime:server:abc")).await;
        assert_eq!(j["payload"]["config"]["presence"]["enabled"], true);
        let (_, t) = r.fake.expect("track", is("presence", "realtime:server:abc")).await;
        assert_eq!(t["payload"], json!({"type":"presence","event":"track","payload":{"uuid": ME, "name": "Notch"}}));
        assert_eq!(t["join_ref"], j["join_ref"]);

        let (_, hb) = r.fake.expect("heartbeat", is("heartbeat", "phoenix")).await;
        assert_eq!(hb["payload"], json!({}));
        assert!(hb["ref"].is_string());
        next_event(&mut r.events, |e| matches!(e, RtEvent::Joined { topic } if topic == "server:abc")).await;
    }

    #[tokio::test]
    async fn presence_state_and_diff_maintain_the_set() {
        let mut r = rig().await;
        r.rt.join("server:abc", Some(json!({"uuid": ME})));
        r.fake.expect("track", is("presence", "realtime:server:abc")).await;

        r.fake.push(json!({"topic":"realtime:server:abc","event":"presence_state","ref":null,"payload":{
            "b": {"metas":[{"phx_ref":"r1","uuid":"b"}]},
            ME: {"metas":[{"phx_ref":"r0","uuid":ME}]},
        }}));
        let e = next_event(&mut r.events, |e| matches!(e, RtEvent::Presence { .. })).await;
        assert_eq!(e, RtEvent::Presence { topic: "server:abc".into(), keys: vec![ME.into(), "b".into()] });

        // A second tab of `b` joins, then its first leaves: `b` stays.
        r.fake.push(json!({"topic":"realtime:server:abc","event":"presence_diff","payload":{
            "joins": {"b": {"metas":[{"phx_ref":"r2"}]}, "c": {"metas":[{"phx_ref":"r3"}]}},
            "leaves": {"b": {"metas":[{"phx_ref":"r1"}]}},
        }}));
        let e = next_event(&mut r.events, |e| matches!(e, RtEvent::Presence { .. })).await;
        assert_eq!(e, RtEvent::Presence { topic: "server:abc".into(), keys: vec![ME.into(), "b".into(), "c".into()] });

        r.fake.push(json!({"topic":"realtime:server:abc","event":"presence_diff","payload":{
            "joins": {}, "leaves": {"b": {"metas":[{"phx_ref":"r2"}]}},
        }}));
        let e = next_event(&mut r.events, |e| matches!(e, RtEvent::Presence { .. })).await;
        assert_eq!(e, RtEvent::Presence { topic: "server:abc".into(), keys: vec![ME.into(), "c".into()] });
    }

    #[tokio::test]
    async fn broadcasts_surface_with_extra_fields_tolerated() {
        let mut r = rig().await;
        r.rt.join("server:abc", None);
        next_event(&mut r.events, |e| matches!(e, RtEvent::Joined { .. })).await;
        r.fake.push(json!({"topic":"realtime:server:abc","event":"broadcast","ref":null,"payload":{
            "type":"broadcast","event":"emote","id":"x","meta":{"id":"y"},
            "payload":{"uuid":"b","id":"emote-wave","at":1760527200000u64},
        }}));
        let e = next_event(&mut r.events, |e| matches!(e, RtEvent::Broadcast { .. })).await;
        assert_eq!(
            e,
            RtEvent::Broadcast {
                topic: "server:abc".into(),
                event: "emote".into(),
                payload: json!({"uuid":"b","id":"emote-wave","at":1760527200000u64}),
            }
        );
    }

    #[tokio::test]
    async fn a_token_refresh_is_pushed_on_every_joined_channel() {
        let mut r = rig().await;
        r.rt.join("user:me", None);
        r.rt.join("server:abc", None);
        for _ in 0..2 {
            next_event(&mut r.events, |e| matches!(e, RtEvent::Joined { .. })).await;
        }
        r.token.send("t2".into()).unwrap();
        for topic in ["realtime:server:abc", "realtime:user:me"] {
            let (_, f) = r.fake.expect("access_token", move |_, v| v["event"] == "access_token" && v["topic"] == topic).await;
            assert_eq!(f["payload"], json!({"access_token": "t2"}));
        }
    }

    #[tokio::test]
    async fn a_dropped_socket_reconnects_and_rejoins_with_the_current_token() {
        let mut r = rig().await;
        r.rt.join("user:me", None);
        r.rt.join("server:abc", Some(json!({"uuid": ME})));
        r.fake.expect("track", is("presence", "realtime:server:abc")).await;

        r.token.send("t2".into()).unwrap();
        r.fake.drop_connection();

        let (id, j) = r.fake.expect("rejoin", |id, v| id == 2 && v["topic"] == "realtime:server:abc" && v["event"] == "phx_join").await;
        assert_eq!(id, 2);
        assert_eq!(j["payload"]["access_token"], "t2");
        r.fake.expect("rejoin user", |id, v| id == 2 && v["topic"] == "realtime:user:me" && v["event"] == "phx_join").await;
        r.fake.expect("re-track", |id, v| id == 2 && v["event"] == "presence").await;
        assert_eq!(r.fake.connections.load(Ordering::SeqCst), 2);
    }

    #[tokio::test]
    async fn unanswered_heartbeats_replace_the_socket() {
        let mut r = rig().await;
        r.fake.answer_heartbeats.store(false, Ordering::SeqCst);
        r.rt.join("user:me", None);
        r.fake.expect("first join", |id, v| id == 1 && v["event"] == "phx_join").await;
        r.fake.expect("second connection", |id, v| id == 2 && v["event"] == "phx_join").await;
    }

    #[tokio::test]
    async fn a_refused_join_backs_off_and_rejoins_on_the_same_socket() {
        let mut r = rig().await;
        r.fake.refuse_joins.store(1, Ordering::SeqCst);
        r.rt.join("user:me", None);
        r.fake.expect("refused join", is("phx_join", "realtime:user:me")).await;
        let (id, _) = r.fake.expect("retried join", is("phx_join", "realtime:user:me")).await;
        assert_eq!(id, 1, "same socket");
        next_event(&mut r.events, |e| matches!(e, RtEvent::Joined { .. })).await;
    }

    #[tokio::test]
    async fn leaving_sends_phx_leave_and_silences_the_topic() {
        let mut r = rig().await;
        r.rt.join("server:abc", None);
        next_event(&mut r.events, |e| matches!(e, RtEvent::Joined { .. })).await;
        r.rt.leave("server:abc");
        r.fake.expect("leave", is("phx_leave", "realtime:server:abc")).await;
        r.fake.push(json!({"topic":"realtime:server:abc","event":"broadcast","payload":{"event":"emote","payload":{}}}));
        r.rt.join("user:me", None);
        // The next event is the user join, not the stale broadcast.
        let e = next_event(&mut r.events, |_| true).await;
        assert_eq!(e, RtEvent::Joined { topic: "user:me".into() });
    }

    #[test]
    fn the_tls_config_builds_on_aws_lc_rs_with_the_platform_verifier() {
        // No handshake (that needs the network); this catches a crypto-provider mix-up or a
        // platform verifier that cannot initialise, which would otherwise only show up as a
        // launcher that never gets live updates.
        let config = tls_config().unwrap();
        assert!(Arc::ptr_eq(&config, &tls_config().unwrap()), "built once");
    }

    #[test]
    fn backoff_doubles_to_the_cap_with_jitter_in_the_upper_half() {
        let (base, cap) = (Duration::from_secs(1), Duration::from_secs(30));
        for (attempt, full) in [(1u32, 1u64), (2, 2), (3, 4), (5, 16), (6, 30), (40, 30)] {
            for _ in 0..20 {
                let d = backoff(attempt, base, cap);
                assert!(d >= Duration::from_millis(full * 500) && d <= Duration::from_secs(full), "{attempt}: {d:?}");
            }
        }
    }
}
