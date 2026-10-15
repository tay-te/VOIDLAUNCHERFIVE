//! [`Online`]: the handle the launcher holds, and everything that runs behind it.
//!
//! # Shape
//!
//! `Online` is a cheap `Arc` clone. Behind it:
//!
//! - **State** under a `std::sync::Mutex`, never held across an `.await`: the status, the
//!   server the game is on, and while signed in a *session context* (player, identity,
//!   Supabase session, cached self cosmetics and friends, the Realtime handle, and the abort
//!   handles of every background task).
//! - **Generations.** Each sign-in gets a new generation number, and every background task
//!   and late HTTP answer checks it before writing anything. Aborting the tasks on sign-out is
//!   the main mechanism; the generation check is what makes a response that was already in
//!   flight harmless.
//! - **Background tasks** hold a `Weak` to the internals and upgrade it per iteration, so
//!   dropping the last `Online` also winds them down.
//!
//! # Request policy
//!
//! Every call carries `apikey`; authenticated ones add `Authorization: Bearer`. A `429` with
//! `Retry-After` ≤ 5 s is retried once after that delay; a longer one is returned. A `401` on
//! an authenticated call forces one token refresh and one retry; refreshes are serialised and
//! a caller holding a token that someone else already replaced skips straight to the retry.
//! If the refresh grant itself fails, the whole sign-in exchange is re-run with the stored
//! [`McIdentity`].

use std::future::Future;
use std::pin::Pin;
use std::sync::{Arc, Mutex, Weak};
use std::time::Duration;

use serde::de::DeserializeOwned;
use serde::Deserialize;
use serde_json::{json, Value};
use tokio::sync::{broadcast, mpsc, watch, Notify};
use tokio::task::AbortHandle;
use tokio::time::Instant;
use void_bridge::cosmetics::{is_item_id, normalize_uuid, MAX_EMOTES};
use void_bridge::{EquippedSet, Slot};

use crate::auth::{mojang_message, now_unix, Challenge, Grant, Session};
use crate::batcher;
use crate::cache::CosmeticsCache;
use crate::config::{AuthMode, McIdentity, OnlineConfig};
use crate::error::api_error;
use crate::realtime::{backoff, Realtime, RealtimeTimings, RtEvent};
use crate::types::{CheckoutSession, FriendsList, MyCosmetics, OnlineEvent, OnlineStatus, Player};
use crate::{normalize_server, Error};

const AUTH_MINECRAFT: &str = "/functions/v1/auth-minecraft";
const VERIFY: &str = "/auth/v1/verify";
const REFRESH: &str = "/auth/v1/token?grant_type=refresh_token";
const COSMETICS_LOOKUP: &str = "/functions/v1/cosmetics-lookup";
const EQUIP: &str = "/functions/v1/equip";
const CHECKOUT: &str = "/functions/v1/checkout";
const FRIENDS: &str = "/functions/v1/friends";
const RPC_MY_COSMETICS: &str = "/rest/v1/rpc/my_cosmetics";
const RPC_HEARTBEAT: &str = "/rest/v1/rpc/presence_heartbeat";
const RPC_OFFLINE: &str = "/rest/v1/rpc/presence_offline";
const RPC_EMOTE: &str = "/rest/v1/rpc/emote";

/// Every interval the crate keeps, so tests can run the same code at millisecond scale.
#[derive(Debug, Clone, Copy)]
pub(crate) struct Timings {
    pub heartbeat: Duration,
    pub friends_poll: Duration,
    pub refresh_lead: Duration,
    pub debounce: Duration,
    pub entitlement_poll: Duration,
    pub entitlement_window: Duration,
    pub reauth_base: Duration,
    pub reauth_cap: Duration,
    pub offline_timeout: Duration,
    pub realtime: RealtimeTimings,
}

impl Default for Timings {
    fn default() -> Self {
        Timings {
            heartbeat: Duration::from_secs(30),
            friends_poll: Duration::from_secs(60),
            refresh_lead: Duration::from_secs(60),
            debounce: batcher::DEBOUNCE,
            entitlement_poll: Duration::from_secs(5),
            entitlement_window: Duration::from_secs(10 * 60),
            reauth_base: Duration::from_secs(5),
            reauth_cap: Duration::from_secs(5 * 60),
            offline_timeout: Duration::from_secs(3),
            realtime: RealtimeTimings::default(),
        }
    }
}

/// The launcher's handle on VOID's online services. Clone it freely; every clone is the same
/// client.
///
/// Constructed disabled (no backend in this build), it answers every call with
/// [`Error::Disabled`] and never touches the network. Constructed with a config it starts
/// signed out; [`Online::sign_in`] brings it online.
#[derive(Clone)]
pub struct Online(Arc<Inner>);

impl std::fmt::Debug for Online {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Online").field("status", &self.status()).finish_non_exhaustive()
    }
}

struct Inner {
    config: Option<OnlineConfig>,
    http: reqwest::Client,
    events: broadcast::Sender<OnlineEvent>,
    state: Mutex<State>,
    cache: Arc<Mutex<CosmeticsCache>>,
    /// Serialises sign-in and sign-out.
    lifecycle: tokio::sync::Mutex<()>,
    /// Serialises token refreshes.
    refresh_lock: tokio::sync::Mutex<()>,
    /// Serialises heartbeats, so a slow one can never land after a newer server change.
    heartbeat_lock: tokio::sync::Mutex<()>,
    /// Woken whenever `my_cosmetics` lands, for checkout watches.
    entitlements: Arc<Notify>,
    timings: Timings,
}

struct State {
    status: OnlineStatus,
    current_server: Option<String>,
    next_generation: u64,
    session: Option<SessionCtx>,
}

struct SessionCtx {
    generation: u64,
    player: Player,
    identity: McIdentity,
    auth: Session,
    my_cosmetics: Option<MyCosmetics>,
    friends: Option<FriendsList>,
    server_topic: Option<String>,
    token_tx: watch::Sender<String>,
    batch_tx: mpsc::UnboundedSender<Vec<String>>,
    /// Dropping it closes the socket.
    realtime: Option<Realtime>,
    tasks: Vec<AbortHandle>,
}

impl Drop for SessionCtx {
    fn drop(&mut self) {
        for t in &self.tasks {
            t.abort();
        }
    }
}

#[derive(Deserialize)]
struct HeartbeatReply {
    #[serde(default)]
    topic: Option<String>,
}

impl Online {
    /// A client for `config`, or a disabled one for `None`. `http` is shared with the rest of
    /// the launcher; give it a sensible timeout.
    pub fn new(config: Option<OnlineConfig>, http: reqwest::Client) -> Online {
        Online::with_timings(config, http, Timings::default())
    }

    pub(crate) fn with_timings(config: Option<OnlineConfig>, http: reqwest::Client, timings: Timings) -> Online {
        let status = if config.is_some() { OnlineStatus::SignedOut } else { OnlineStatus::Disabled };
        let (events, _) = broadcast::channel(256);
        Online(Arc::new(Inner {
            config,
            http,
            events,
            state: Mutex::new(State { status, current_server: None, next_generation: 1, session: None }),
            cache: Arc::new(Mutex::new(CosmeticsCache::default())),
            lifecycle: tokio::sync::Mutex::new(()),
            refresh_lock: tokio::sync::Mutex::new(()),
            heartbeat_lock: tokio::sync::Mutex::new(()),
            entitlements: Arc::new(Notify::new()),
            timings,
        }))
    }

    /// A client with no backend: every call is [`Error::Disabled`].
    pub fn disabled() -> Online {
        Online::new(None, reqwest::Client::new())
    }

    /// Whether this build has a backend at all.
    pub fn is_enabled(&self) -> bool {
        self.0.config.is_some()
    }

    /// Where things stand right now.
    pub fn status(&self) -> OnlineStatus {
        self.0.state.lock().unwrap().status.clone()
    }

    /// The signed-in player.
    pub fn player(&self) -> Option<Player> {
        self.0.state.lock().unwrap().session.as_ref().map(|s| s.player.clone())
    }

    /// Everything that happens from now on. Slow receivers lag (see
    /// [`broadcast::error::RecvError::Lagged`]) rather than block the client; on a lag,
    /// re-read [`Online::status`] and the caches.
    pub fn subscribe(&self) -> broadcast::Receiver<OnlineEvent> {
        self.0.events.subscribe()
    }

    /// Sign in as `id`, replacing any current session.
    ///
    /// Runs the exchange for the configured [`AuthMode`] (by default: `auth-minecraft`
    /// challenge → Mojang `session/minecraft/join` → `auth-minecraft` join), redeems the grant
    /// at `/auth/v1/verify`, and then starts the session's background work:
    ///
    /// - token refresh a minute before expiry, falling back to a fresh exchange with `id`
    ///   (with backoff) when the refresh grant fails;
    /// - a presence heartbeat every 30 s, carrying [`Online::current_server`];
    /// - the Realtime socket, joined to `user:<uuid>`;
    /// - a friends poll every 60 s, emitting [`OnlineEvent::Friends`] only on change;
    /// - the cosmetics batcher;
    /// - one `my_cosmetics` fetch, emitting [`OnlineEvent::SelfCosmetics`].
    ///
    /// Emits `Status(Connecting)`, then `Status(Online)` or `Status(Error)`.
    pub async fn sign_in(&self, id: McIdentity) -> Result<Player, Error> {
        let inner = &self.0;
        inner.config()?;
        let Some(uuid) = normalize_uuid(&id.uuid) else {
            return Err(Error::Invalid("That Minecraft account has a malformed UUID.".into()));
        };
        let id = McIdentity { uuid, ..id };
        let _life = inner.lifecycle.lock().await;
        inner.stop_session(true).await;
        inner.set_status(OnlineStatus::Connecting);
        match inner.exchange(&id).await {
            Ok((player, session)) => {
                inner.start_session(player.clone(), id, session);
                inner.set_status(OnlineStatus::Online { uuid: player.uuid.clone(), name: player.name.clone() });
                Ok(player)
            }
            Err(e) => {
                inner.set_status(OnlineStatus::Error { message: e.to_string() });
                Err(e)
            }
        }
    }

    /// Sign out: a best-effort `presence_offline` (so friends see it now rather than in
    /// 90 s), stop every background task, close the socket, forget the session and the
    /// caches. Emits `Status(SignedOut)`. The current server is kept, so a later sign-in
    /// announces it straight away.
    pub async fn sign_out(&self) {
        let inner = &self.0;
        if inner.config.is_none() {
            return;
        }
        let _life = inner.lifecycle.lock().await;
        inner.stop_session(true).await;
        inner.set_status(OnlineStatus::SignedOut);
    }

    /// The signed-in player's owned items and equipped set (RPC `my_cosmetics`). Caches the
    /// answer and always emits [`OnlineEvent::SelfCosmetics`].
    pub async fn my_cosmetics(&self) -> Result<MyCosmetics, Error> {
        let gen = self.0.current_generation()?;
        self.0.fetch_my_cosmetics(gen, true).await
    }

    /// The last [`Online::my_cosmetics`] answer, kept current by equips.
    pub fn cached_my_cosmetics(&self) -> Option<MyCosmetics> {
        self.0.state.lock().unwrap().session.as_ref().and_then(|s| s.my_cosmetics.clone())
    }

    /// Put `id` in `slot`, or empty it with `None` (edge function `equip`). Returns the new
    /// set, updates the cached self cosmetics and emits [`OnlineEvent::SelfCosmetics`].
    pub async fn equip(&self, slot: Slot, id: Option<String>) -> Result<EquippedSet, Error> {
        let gen = self.0.current_generation()?;
        if let Some(id) = &id {
            check_item(id)?;
        }
        self.0.equip_with(gen, json!({ "slot": slot.as_str(), "id": id })).await
    }

    /// Replace the emote wheel, in wheel order, at most [`MAX_EMOTES`].
    pub async fn set_emotes(&self, ids: Vec<String>) -> Result<EquippedSet, Error> {
        let gen = self.0.current_generation()?;
        if ids.len() > MAX_EMOTES {
            return Err(Error::Invalid(format!("The emote wheel holds {MAX_EMOTES} emotes at most.")));
        }
        for id in &ids {
            check_item(id)?;
        }
        self.0.equip_with(gen, json!({ "emotes": ids })).await
    }

    /// Recolour the item in `slot` with `#rrggbb`, or clear the tint with `None`. Only items
    /// flagged `tintable` accept one (`400 not_tintable`).
    pub async fn set_tint(&self, slot: Slot, color: Option<String>) -> Result<EquippedSet, Error> {
        let gen = self.0.current_generation()?;
        let color = match color {
            None => None,
            Some(c) => {
                let c = c.trim().to_ascii_lowercase();
                let hex = c.strip_prefix('#').unwrap_or_default();
                if hex.len() != 6 || !hex.bytes().all(|b| b.is_ascii_hexdigit()) {
                    return Err(Error::Invalid("A tint is a colour like #9f8bff.".into()));
                }
                Some(c)
            }
        };
        self.0.equip_with(gen, json!({ "tints": { slot.as_str(): color } })).await
    }

    /// Ask for other players' cosmetics, for the mod's `cosmetics_query`. Fire and forget:
    /// answers arrive as [`OnlineEvent::Cosmetics`], carrying only sets that are new or
    /// changed.
    ///
    /// UUIDs are normalised and invalid ones dropped; players answered within the last 5
    /// minutes (including "not a VOID player") are skipped; the rest are collected for 150 ms
    /// and fetched 100 at a time. A no-op when signed out or disabled.
    pub fn request_cosmetics<I: IntoIterator<Item = String>>(&self, uuids: I) {
        self.0.request_cosmetics(uuids);
    }

    /// The last set seen for `uuid` (any form), even if older than the TTL. `None` for
    /// unknown players and for known non-VOID players.
    pub fn cached_cosmetics(&self, uuid: &str) -> Option<EquippedSet> {
        let uuid = normalize_uuid(uuid)?;
        self.0.cache.lock().unwrap().peek(&uuid).cloned()
    }

    /// Look players up now, bypassing the batcher but not the cache: fresh answers come from
    /// it, the rest are fetched (100 at a time) and cached. Returns the sets of those who are
    /// VOID players. Emits nothing.
    pub async fn lookup_cosmetics(&self, uuids: &[String]) -> Result<Vec<EquippedSet>, Error> {
        self.0.current_generation()?;
        let mut wanted: Vec<String> = uuids.iter().filter_map(|u| normalize_uuid(u)).collect();
        wanted.sort();
        wanted.dedup();
        let now = Instant::now();
        let mut out = Vec::new();
        let mut missing = Vec::new();
        {
            let mut cache = self.0.cache.lock().unwrap();
            for u in wanted {
                match cache.get_fresh(&u, now) {
                    Some(Some(set)) => out.push(set),
                    Some(None) => {}
                    None => missing.push(u),
                }
            }
        }
        for chunk in missing.chunks(batcher::CHUNK) {
            let found = self.0.fetch_cosmetics(chunk.to_vec()).await?;
            let mut cache = self.0.cache.lock().unwrap();
            cache.absorb(chunk, &found, Instant::now());
            out.extend(chunk.iter().filter_map(|u| cache.peek(u).cloned()));
        }
        Ok(out)
    }

    /// Start a Stripe Checkout for `item` (edge function `checkout`) and return the page to
    /// open in the system browser.
    ///
    /// Also starts watching for the entitlement: `my_cosmetics` every 5 s for up to 10
    /// minutes, stopping as soon as `item` is owned. A Realtime `entitlements` broadcast on
    /// the user channel triggers an immediate refresh, so usually the purchase shows up within
    /// a second of Stripe's webhook. Each change emits [`OnlineEvent::SelfCosmetics`].
    ///
    /// The returned URL is checked to be `https`; anything else is [`Error::Unexpected`].
    pub async fn checkout(&self, item: &str) -> Result<CheckoutSession, Error> {
        let inner = &self.0;
        let gen = inner.current_generation()?;
        check_item(item)?;
        let session: CheckoutSession = inner.call(CHECKOUT, &json!({ "item": item }), true).await?;
        match reqwest::Url::parse(&session.url) {
            Ok(u) if u.scheme() == "https" => {}
            Ok(u) => return Err(Error::Unexpected(format!("checkout url has scheme {:?}", u.scheme()))),
            Err(e) => return Err(Error::Unexpected(format!("checkout url does not parse: {e}"))),
        }
        let watch = tokio::spawn(watch_entitlement(
            Arc::downgrade(&self.0),
            inner.entitlements.clone(),
            gen,
            item.to_string(),
        ));
        inner.adopt_task(gen, watch.abort_handle());
        Ok(session)
    }

    /// The friends list (`friends {"action":"list"}`). Emits [`OnlineEvent::Friends`].
    pub async fn friends(&self) -> Result<FriendsList, Error> {
        let gen = self.0.current_generation()?;
        self.0.friends_action(gen, json!({ "action": "list" })).await
    }

    /// Send a friend request to the player called `name`. Emits [`OnlineEvent::Friends`].
    pub async fn friend_request(&self, name: &str) -> Result<FriendsList, Error> {
        let gen = self.0.current_generation()?;
        let name = name.trim();
        if name.is_empty() {
            return Err(Error::Invalid("Type the Minecraft name of the player to add.".into()));
        }
        self.0.friends_action(gen, json!({ "action": "request", "name": name })).await
    }

    /// Accept `uuid`'s request. Emits [`OnlineEvent::Friends`].
    pub async fn friend_accept(&self, uuid: &str) -> Result<FriendsList, Error> {
        self.friend_uuid_action("accept", uuid).await
    }

    /// Decline `uuid`'s request. Emits [`OnlineEvent::Friends`].
    pub async fn friend_decline(&self, uuid: &str) -> Result<FriendsList, Error> {
        self.friend_uuid_action("decline", uuid).await
    }

    /// Withdraw the request sent to `uuid`. Emits [`OnlineEvent::Friends`].
    pub async fn friend_cancel(&self, uuid: &str) -> Result<FriendsList, Error> {
        self.friend_uuid_action("cancel", uuid).await
    }

    /// Unfriend `uuid`. Emits [`OnlineEvent::Friends`].
    pub async fn friend_remove(&self, uuid: &str) -> Result<FriendsList, Error> {
        self.friend_uuid_action("remove", uuid).await
    }

    async fn friend_uuid_action(&self, action: &str, uuid: &str) -> Result<FriendsList, Error> {
        let gen = self.0.current_generation()?;
        let uuid = normalize_uuid(uuid).ok_or_else(|| Error::Invalid("That isn't a valid player id.".into()))?;
        self.0.friends_action(gen, json!({ "action": action, "uuid": uuid })).await
    }

    /// The game joined `address` (`host` or `host:port`), or left with `None`.
    ///
    /// The address is normalised with [`normalize_server`]. When it changes, the old
    /// `server:*` channel is left at once (emitting an empty [`OnlineEvent::ServerPlayers`]
    /// for it); then a heartbeat announces the new server and the topic it returns is joined,
    /// tracking presence `{uuid, name}`.
    ///
    /// Signed out, the address is only remembered, for the first heartbeat after sign-in.
    pub async fn set_server(&self, address: Option<String>) -> Result<(), Error> {
        let inner = &self.0;
        inner.config()?;
        let server = address.map(|a| normalize_server(&a, None)).filter(|s| !s.is_empty());
        let changed = {
            let mut st = inner.state.lock().unwrap();
            let changed = st.current_server != server;
            st.current_server = server;
            changed
        };
        let Ok(gen) = inner.current_generation() else { return Ok(()) };
        if changed {
            inner.sync_server_channel(gen, None);
        }
        inner.heartbeat(gen).await
    }

    /// The normalised address [`Online::set_server`] last set.
    pub fn current_server(&self) -> Option<String> {
        self.0.state.lock().unwrap().current_server.clone()
    }

    /// Play emote `id` for everyone on the current server (RPC `emote`). Returns the Unix ms
    /// the backend stamped it with. `409 no_server` when not on one, `429 too_fast` faster
    /// than once a second.
    pub async fn emote(&self, id: &str) -> Result<u64, Error> {
        self.0.current_generation()?;
        check_item(id)?;
        #[derive(Deserialize)]
        struct At {
            at: u64,
        }
        let at: At = self.0.call(RPC_EMOTE, &json!({ "id": id }), true).await?;
        Ok(at.at)
    }
}

fn check_item(id: &str) -> Result<(), Error> {
    if is_item_id(id) {
        Ok(())
    } else {
        Err(Error::Invalid(format!("{id:?} isn't a cosmetic item id.")))
    }
}

type BoxFuture<'a, T> = Pin<Box<dyn Future<Output = T> + Send + 'a>>;

impl Inner {
    fn config(&self) -> Result<&OnlineConfig, Error> {
        self.config.as_ref().ok_or(Error::Disabled)
    }

    fn current_generation(&self) -> Result<u64, Error> {
        self.config()?;
        self.state.lock().unwrap().session.as_ref().map(|s| s.generation).ok_or(Error::SignedOut)
    }

    fn is_current(&self, gen: u64) -> bool {
        self.state.lock().unwrap().session.as_ref().is_some_and(|s| s.generation == gen)
    }

    fn emit(&self, event: OnlineEvent) {
        let _ = self.events.send(event);
    }

    fn set_status(&self, status: OnlineStatus) {
        let changed = {
            let mut st = self.state.lock().unwrap();
            let changed = st.status != status;
            st.status = status.clone();
            changed
        };
        if changed {
            self.emit(OnlineEvent::Status(status));
        }
    }

    /// Run `f` on the session context if it is still generation `gen`.
    fn with_session<R>(&self, gen: u64, f: impl FnOnce(&mut SessionCtx) -> R) -> Option<R> {
        let mut st = self.state.lock().unwrap();
        st.session.as_mut().filter(|s| s.generation == gen).map(f)
    }

    fn adopt_task(&self, gen: u64, handle: AbortHandle) {
        let abort = handle.clone();
        if self.with_session(gen, |s| s.tasks.push(handle)).is_none() {
            abort.abort();
        }
    }

    // --- HTTP ------------------------------------------------------------------------------

    /// POST `body` to `path` on the project, applying the request policy in the module docs.
    async fn call<T: DeserializeOwned>(&self, path: &str, body: &Value, authed: bool) -> Result<T, Error> {
        let config = self.config()?;
        let url = format!("{}{}", config.url, path);
        let (mut retried_429, mut retried_401) = (false, false);
        loop {
            let token = if authed {
                let st = self.state.lock().unwrap();
                Some(st.session.as_ref().map(|s| s.auth.access_token.clone()).ok_or(Error::SignedOut)?)
            } else {
                None
            };
            let mut req = self.http.post(&url).header("apikey", &config.anon_key).json(body);
            if let Some(t) = &token {
                req = req.bearer_auth(t);
            }
            let resp = req.send().await?;
            let status = resp.status().as_u16();
            if resp.status().is_success() {
                let bytes = resp.bytes().await?;
                if bytes.iter().all(u8::is_ascii_whitespace) {
                    return Ok(serde_json::from_slice(b"null")?);
                }
                return Ok(serde_json::from_slice(&bytes)?);
            }
            let retry_after = resp
                .headers()
                .get(reqwest::header::RETRY_AFTER)
                .and_then(|v| v.to_str().ok())
                .and_then(|s| s.trim().parse::<u64>().ok());
            let bytes = resp.bytes().await.unwrap_or_default();
            if status == 429 && !retried_429 {
                if let Some(secs) = retry_after.filter(|s| *s <= 5) {
                    retried_429 = true;
                    tokio::time::sleep(Duration::from_secs(secs)).await;
                    continue;
                }
            }
            if status == 401 && authed && !retried_401 {
                retried_401 = true;
                self.refresh(token).await?;
                continue;
            }
            return Err(api_error(status, &bytes));
        }
    }

    // --- auth ------------------------------------------------------------------------------

    /// Prove `id` and redeem the grant: a fresh Supabase session for that player.
    async fn exchange(&self, id: &McIdentity) -> Result<(Player, Session), Error> {
        let config = self.config()?;
        let grant: Grant = match config.auth_mode {
            AuthMode::Join => {
                let ch: Challenge = self.call(AUTH_MINECRAFT, &json!({ "mode": "challenge" }), false).await?;
                self.mojang_join(config, id, &ch.server_id).await?;
                let body = json!({ "mode": "join", "name": id.name, "ts": ch.ts, "nonce": ch.nonce });
                self.call(AUTH_MINECRAFT, &body, false).await?
            }
            AuthMode::Token => {
                self.call(AUTH_MINECRAFT, &json!({ "mode": "token", "access_token": id.access_token }), false).await?
            }
        };
        let uuid = normalize_uuid(&grant.player.uuid)
            .ok_or_else(|| Error::Unexpected(format!("auth-minecraft returned uuid {:?}", grant.player.uuid)))?;
        if Some(&uuid) != normalize_uuid(&id.uuid).as_ref() {
            return Err(Error::Unexpected("auth-minecraft signed in a different player".into()));
        }
        let body = json!({ "type": grant.verification_type, "token_hash": grant.token_hash });
        let session: Session = self.call(VERIFY, &body, false).await?;
        Ok((Player { uuid, name: grant.player.name }, session.normalised()))
    }

    /// Mojang `session/minecraft/join`: tell Mojang this account is joining `server_id`.
    /// The only place the Minecraft access token is sent, and only to Mojang.
    async fn mojang_join(&self, config: &OnlineConfig, id: &McIdentity, server_id: &str) -> Result<(), Error> {
        let url = format!("{}/session/minecraft/join", config.mojang_session_base.trim_end_matches('/'));
        let body = json!({
            "accessToken": id.access_token,
            "selectedProfile": id.uuid.replace('-', ""),
            "serverId": server_id,
        });
        let resp = self.http.post(url).json(&body).send().await?;
        if resp.status().is_success() {
            return Ok(());
        }
        let status = resp.status().as_u16();
        let bytes = resp.bytes().await.unwrap_or_default();
        Err(Error::Mojang(mojang_message(status, &bytes)))
    }

    /// Replace the session's tokens. `stale` is the access token the caller saw rejected: if
    /// it has already been replaced, there is nothing to do. Boxed because `call` and
    /// `refresh` reach each other.
    fn refresh(&self, stale: Option<String>) -> BoxFuture<'_, Result<(), Error>> {
        Box::pin(async move {
            let _guard = self.refresh_lock.lock().await;
            let (gen, current, refresh_token, identity) = {
                let st = self.state.lock().unwrap();
                let s = st.session.as_ref().ok_or(Error::SignedOut)?;
                (s.generation, s.auth.access_token.clone(), s.auth.refresh_token.clone(), s.identity.clone())
            };
            if stale.is_some_and(|stale| stale != current) {
                return Ok(());
            }
            let session = match self.call::<Session>(REFRESH, &json!({ "refresh_token": refresh_token }), false).await {
                Ok(s) => s.normalised(),
                Err(e) => {
                    tracing::warn!(error = %e, "session refresh failed; signing in again");
                    self.exchange(&identity).await?.1
                }
            };
            let installed = self.with_session(gen, |s| {
                s.token_tx.send_replace(session.access_token.clone());
                s.auth = session;
                s.player.clone()
            });
            if let Some(player) = installed {
                if matches!(self.state.lock().unwrap().status, OnlineStatus::Error { .. }) {
                    self.set_status(OnlineStatus::Online { uuid: player.uuid, name: player.name });
                }
            }
            Ok(())
        })
    }

    // --- session lifecycle -----------------------------------------------------------------

    fn start_session(self: &Arc<Self>, player: Player, identity: McIdentity, auth: Session) {
        let config = self.config.as_ref().expect("signed in without a config");
        let (token_tx, token_rx) = watch::channel(auth.access_token.clone());
        let (batch_tx, batch_rx) = mpsc::unbounded_channel();
        let (rt_tx, rt_rx) = mpsc::unbounded_channel();
        let realtime =
            Realtime::spawn(config.realtime_websocket_url(), player.uuid.clone(), token_rx, rt_tx, self.timings.realtime);
        realtime.join(&format!("user:{}", player.uuid), None);

        // The context goes in first, so tasks that start running immediately find their
        // generation current.
        let gen = {
            let mut st = self.state.lock().unwrap();
            let gen = st.next_generation;
            st.next_generation += 1;
            st.session = Some(SessionCtx {
                generation: gen,
                player,
                identity,
                auth,
                my_cosmetics: None,
                friends: None,
                server_topic: None,
                token_tx,
                batch_tx,
                realtime: Some(realtime),
                tasks: Vec::new(),
            });
            gen
        };

        let weak = Arc::downgrade(self);
        let batch_fetch = {
            let weak = weak.clone();
            move |uuids: Vec<String>| {
                let weak = weak.clone();
                async move {
                    let inner = weak.upgrade().ok_or(Error::SignedOut)?;
                    inner.fetch_cosmetics(uuids).await
                }
            }
        };
        let batch_emit = {
            let weak = weak.clone();
            move |sets: Vec<EquippedSet>| {
                if let Some(inner) = weak.upgrade().filter(|i| i.is_current(gen)) {
                    inner.emit(OnlineEvent::Cosmetics(sets));
                }
            }
        };
        let tasks = [
            tokio::spawn(refresh_loop(weak.clone(), gen)).abort_handle(),
            tokio::spawn(heartbeat_loop(weak.clone(), gen)).abort_handle(),
            tokio::spawn(friends_loop(weak.clone(), gen)).abort_handle(),
            tokio::spawn(dispatch(weak.clone(), gen, rt_rx)).abort_handle(),
            tokio::spawn(batcher::run(batch_rx, self.timings.debounce, self.cache.clone(), batch_fetch, batch_emit))
                .abort_handle(),
            tokio::spawn({
                let weak = weak.clone();
                async move {
                    if let Some(inner) = weak.upgrade() {
                        if let Err(e) = inner.fetch_my_cosmetics(gen, true).await {
                            tracing::warn!(error = %e, "initial my_cosmetics failed");
                        }
                    }
                }
            })
            .abort_handle(),
        ];
        for t in tasks {
            self.adopt_task(gen, t);
        }
    }

    /// End the current session, if any: optionally tell the backend we are offline, then
    /// abort everything and forget it.
    async fn stop_session(&self, announce_offline: bool) {
        if announce_offline && self.current_generation().is_ok() {
            let body = json!({});
            let offline = self.call::<Value>(RPC_OFFLINE, &body, true);
            match tokio::time::timeout(self.timings.offline_timeout, offline).await {
                Ok(Ok(_)) => {}
                Ok(Err(e)) => tracing::debug!(error = %e, "presence_offline failed"),
                Err(_) => tracing::debug!("presence_offline timed out"),
            }
        }
        let ctx = self.state.lock().unwrap().session.take();
        drop(ctx); // aborts the tasks and closes the socket
        self.cache.lock().unwrap().clear();
    }

    // --- presence --------------------------------------------------------------------------

    async fn heartbeat(&self, gen: u64) -> Result<(), Error> {
        let _guard = self.heartbeat_lock.lock().await;
        let server = self.state.lock().unwrap().current_server.clone();
        let reply: HeartbeatReply = self.call(RPC_HEARTBEAT, &json!({ "server": server }), true).await?;
        // A `set_server` that landed while this was in flight has already left the old channel
        // and is queued behind this lock with the new address; following this (now stale)
        // answer would only rejoin the channel it just left.
        if self.state.lock().unwrap().current_server == server {
            self.sync_server_channel(gen, reply.topic);
        }
        Ok(())
    }

    /// Make the joined `server:*` channel be `topic`.
    fn sync_server_channel(&self, gen: u64, topic: Option<String>) {
        let left = self.with_session(gen, |s| {
            if s.server_topic == topic {
                return None;
            }
            let old = std::mem::replace(&mut s.server_topic, topic.clone());
            if let Some(rt) = &s.realtime {
                if let Some(old) = &old {
                    rt.leave(old);
                }
                if let Some(new) = &topic {
                    rt.join(new, Some(json!({ "uuid": s.player.uuid, "name": s.player.name })));
                }
            }
            old
        });
        if let Some(Some(old)) = left {
            self.emit(OnlineEvent::ServerPlayers { topic: old, uuids: Vec::new() });
        }
    }

    // --- cosmetics -------------------------------------------------------------------------

    fn request_cosmetics<I: IntoIterator<Item = String>>(&self, uuids: I) {
        let Some(tx) = self.state.lock().unwrap().session.as_ref().map(|s| s.batch_tx.clone()) else {
            return;
        };
        let now = Instant::now();
        let wanted: Vec<String> = {
            let mut cache = self.cache.lock().unwrap();
            uuids.into_iter().filter_map(|u| normalize_uuid(&u)).filter(|u| !cache.is_fresh(u, now)).collect()
        };
        if !wanted.is_empty() {
            let _ = tx.send(wanted);
        }
    }

    async fn fetch_cosmetics(&self, uuids: Vec<String>) -> Result<Vec<EquippedSet>, Error> {
        #[derive(Deserialize)]
        struct Sets {
            sets: Vec<EquippedSet>,
        }
        let sets: Sets = self.call(COSMETICS_LOOKUP, &json!({ "uuids": uuids }), true).await?;
        Ok(sets.sets)
    }

    /// Fetch `my_cosmetics`, cache it, and emit it (`always`, or only when it changed).
    async fn fetch_my_cosmetics(&self, gen: u64, always: bool) -> Result<MyCosmetics, Error> {
        let mine: MyCosmetics = self.call(RPC_MY_COSMETICS, &json!({}), true).await?;
        let stored = self.with_session(gen, |s| {
            let changed = s.my_cosmetics.as_ref() != Some(&mine);
            s.my_cosmetics = Some(mine.clone());
            (changed, s.player.uuid.clone())
        });
        if let Some((changed, uuid)) = stored {
            self.cache.lock().unwrap().insert(&uuid, Some(mine.equipped.clone()), Instant::now());
            if always || changed {
                self.emit(OnlineEvent::SelfCosmetics(mine.clone()));
            }
            self.entitlements.notify_waiters();
        }
        Ok(mine)
    }

    async fn equip_with(self: &Arc<Self>, gen: u64, body: Value) -> Result<EquippedSet, Error> {
        #[derive(Deserialize)]
        struct Equipped {
            equipped: EquippedSet,
        }
        let r: Equipped = self.call(EQUIP, &body, true).await?;
        let set = r.equipped;
        let updated = self.with_session(gen, |s| {
            let uuid = s.player.uuid.clone();
            let mine = s.my_cosmetics.as_mut().map(|m| {
                m.equipped = set.clone();
                m.clone()
            });
            (uuid, mine)
        });
        match updated {
            Some((uuid, mine)) => {
                self.cache.lock().unwrap().insert(&uuid, Some(set.clone()), Instant::now());
                match mine {
                    Some(mine) => self.emit(OnlineEvent::SelfCosmetics(mine)),
                    None => {
                        // Nothing cached to update yet: fetch the whole picture instead.
                        let inner = self.clone();
                        let t = tokio::spawn(async move {
                            let _ = inner.fetch_my_cosmetics(gen, true).await;
                        });
                        self.adopt_task(gen, t.abort_handle());
                    }
                }
            }
            None => return Err(Error::SignedOut),
        }
        Ok(set)
    }

    // --- friends ---------------------------------------------------------------------------

    async fn friends_action(&self, gen: u64, body: Value) -> Result<FriendsList, Error> {
        let list: FriendsList = self.call(FRIENDS, &body, true).await?;
        if self.with_session(gen, |s| s.friends = Some(list.clone())).is_some() {
            self.emit(OnlineEvent::Friends(list.clone()));
        }
        Ok(list)
    }

    /// The poll: emit only when the list differs from the last one seen.
    async fn friends_poll(&self, gen: u64) -> Result<(), Error> {
        let list: FriendsList = self.call(FRIENDS, &json!({ "action": "list" }), true).await?;
        let changed = self.with_session(gen, |s| {
            let changed = s.friends.as_ref() != Some(&list);
            s.friends = Some(list.clone());
            changed
        });
        if changed == Some(true) {
            self.emit(OnlineEvent::Friends(list));
        }
        Ok(())
    }

    // --- realtime --------------------------------------------------------------------------

    fn on_realtime(self: &Arc<Self>, gen: u64, event: RtEvent) {
        let Some((me, server_topic)) = self.with_session(gen, |s| (s.player.uuid.clone(), s.server_topic.clone()))
        else {
            return;
        };
        let user_topic = format!("user:{me}");
        match event {
            RtEvent::Broadcast { topic, event, .. } if topic == user_topic => match event.as_str() {
                "entitlements" => {
                    let inner = self.clone();
                    let t = tokio::spawn(async move {
                        if let Err(e) = inner.fetch_my_cosmetics(gen, false).await {
                            tracing::warn!(error = %e, "my_cosmetics refresh after a purchase failed");
                        }
                    });
                    self.adopt_task(gen, t.abort_handle());
                }
                "friends" => {
                    let inner = self.clone();
                    let t = tokio::spawn(async move {
                        if let Err(e) = inner.friends_action(gen, json!({ "action": "list" })).await {
                            tracing::warn!(error = %e, "friends refresh after a broadcast failed");
                        }
                    });
                    self.adopt_task(gen, t.abort_handle());
                }
                other => tracing::debug!(event = other, "unknown user-channel broadcast"),
            },
            RtEvent::Broadcast { topic, event, payload } if Some(&topic) == server_topic.as_ref() && event == "emote" => {
                let uuid = payload.get("uuid").and_then(Value::as_str).and_then(normalize_uuid);
                let id = payload.get("id").and_then(Value::as_str);
                let at = payload.get("at").and_then(Value::as_u64);
                match (uuid, id, at) {
                    (Some(uuid), Some(id), Some(at)) if uuid != me => {
                        self.emit(OnlineEvent::Emote { uuid, id: id.to_string(), at });
                    }
                    (Some(_), Some(_), Some(_)) => {} // our own, echoed
                    _ => tracing::debug!(%payload, "malformed emote broadcast"),
                }
            }
            RtEvent::Presence { topic, keys } if Some(&topic) == server_topic.as_ref() => {
                let mut uuids: Vec<String> =
                    keys.iter().filter_map(|k| normalize_uuid(k)).filter(|u| *u != me).collect();
                uuids.sort();
                uuids.dedup();
                self.emit(OnlineEvent::ServerPlayers { topic, uuids: uuids.clone() });
                self.request_cosmetics(uuids);
            }
            RtEvent::Joined { topic } => tracing::debug!(topic, "realtime channel joined"),
            _ => {}
        }
    }
}

// --- background loops ------------------------------------------------------------------------

async fn refresh_loop(weak: Weak<Inner>, gen: u64) {
    let mut failures = 0u32;
    loop {
        let wait = {
            let Some(inner) = weak.upgrade() else { return };
            let Some(expires_at) = inner.with_session(gen, |s| s.auth.expires_at()) else { return };
            if failures == 0 {
                let remaining = Duration::from_secs(expires_at.saturating_sub(now_unix()));
                remaining.saturating_sub(inner.timings.refresh_lead).max(Duration::from_secs(5))
            } else {
                backoff(failures, inner.timings.reauth_base, inner.timings.reauth_cap)
            }
        };
        tokio::time::sleep(wait).await;
        let Some(inner) = weak.upgrade().filter(|i| i.is_current(gen)) else { return };
        match inner.refresh(None).await {
            Ok(()) => failures = 0,
            Err(e) => {
                failures = failures.saturating_add(1);
                tracing::warn!(error = %e, failures, "could not renew the VOID session");
                if inner.is_current(gen) {
                    inner.set_status(OnlineStatus::Error { message: e.to_string() });
                }
            }
        }
    }
}

async fn heartbeat_loop(weak: Weak<Inner>, gen: u64) {
    loop {
        let period = {
            let Some(inner) = weak.upgrade().filter(|i| i.is_current(gen)) else { return };
            if let Err(e) = inner.heartbeat(gen).await {
                tracing::debug!(error = %e, "presence heartbeat failed");
            }
            inner.timings.heartbeat
        };
        tokio::time::sleep(period).await;
    }
}

async fn friends_loop(weak: Weak<Inner>, gen: u64) {
    loop {
        let period = {
            let Some(inner) = weak.upgrade().filter(|i| i.is_current(gen)) else { return };
            if let Err(e) = inner.friends_poll(gen).await {
                tracing::debug!(error = %e, "friends poll failed");
            }
            inner.timings.friends_poll
        };
        tokio::time::sleep(period).await;
    }
}

async fn dispatch(weak: Weak<Inner>, gen: u64, mut rx: mpsc::UnboundedReceiver<RtEvent>) {
    while let Some(event) = rx.recv().await {
        let Some(inner) = weak.upgrade().filter(|i| i.is_current(gen)) else { return };
        inner.on_realtime(gen, event);
    }
}

async fn watch_entitlement(weak: Weak<Inner>, notify: Arc<Notify>, gen: u64, item: String) {
    let (poll, window) = match weak.upgrade() {
        Some(inner) => (inner.timings.entitlement_poll, inner.timings.entitlement_window),
        None => return,
    };
    let deadline = Instant::now() + window;
    loop {
        {
            let Some(inner) = weak.upgrade().filter(|i| i.is_current(gen)) else { return };
            let owned = inner.with_session(gen, |s| s.my_cosmetics.as_ref().is_some_and(|m| m.owned.contains(&item)));
            if owned == Some(true) {
                tracing::info!(item, "purchase landed");
                return;
            }
        }
        if Instant::now() >= deadline {
            tracing::info!(item, "stopped watching for a purchase");
            return;
        }
        tokio::select! {
            _ = tokio::time::sleep(poll) => {
                let Some(inner) = weak.upgrade() else { return };
                if let Err(e) = inner.fetch_my_cosmetics(gen, false).await {
                    tracing::debug!(error = %e, "entitlement poll failed");
                }
            }
            _ = notify.notified() => {}
        }
    }
}
