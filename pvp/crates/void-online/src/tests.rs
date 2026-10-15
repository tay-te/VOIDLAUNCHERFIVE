//! End-to-end tests of [`Online`] against the HTTP mock and the fake Phoenix server. No
//! network: Supabase, Mojang and Realtime are all on 127.0.0.1.

use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Duration;

use serde_json::{json, Value};
use tokio::sync::broadcast;

use crate::auth::now_unix;
use crate::online::Timings;
use crate::realtime::tests::{fast, FakePhoenix};
use crate::testutil::{Mock, Reply};
use crate::*;

const ME: &str = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
const ME_UNDASHED: &str = "069a79f444e94726a5befca90e38aaf5";
const OTHER: &str = "853c80ef-3c37-49fd-aa49-938b674adae6";
const OTHER_UNDASHED: &str = "853c80ef3c3749fdaa49938b674adae6";
const THIRD: &str = "00000000-0000-4000-8000-000000000003";

const AUTH: &str = "/functions/v1/auth-minecraft";
const MOJANG: &str = "/mojang/session/minecraft/join";
const VERIFY: &str = "/auth/v1/verify";
const TOKEN: &str = "/auth/v1/token";
const HEARTBEAT: &str = "/rest/v1/rpc/presence_heartbeat";
const OFFLINE: &str = "/rest/v1/rpc/presence_offline";
const MY: &str = "/rest/v1/rpc/my_cosmetics";
const EMOTE: &str = "/rest/v1/rpc/emote";
const FRIENDS: &str = "/functions/v1/friends";
const LOOKUP: &str = "/functions/v1/cosmetics-lookup";
const EQUIP: &str = "/functions/v1/equip";
const CHECKOUT: &str = "/functions/v1/checkout";

fn timings() -> Timings {
    Timings {
        heartbeat: Duration::from_secs(60),
        friends_poll: Duration::from_secs(60),
        refresh_lead: Duration::from_secs(60),
        debounce: Duration::from_millis(20),
        entitlement_poll: Duration::from_millis(50),
        entitlement_window: Duration::from_secs(5),
        reauth_base: Duration::from_millis(50),
        reauth_cap: Duration::from_millis(200),
        offline_timeout: Duration::from_secs(1),
        realtime: fast(),
    }
}

fn session(at: &str, rt: &str) -> Value {
    json!({
        "access_token": at, "refresh_token": rt, "expires_in": 3600, "expires_at": now_unix() + 3600,
        "token_type": "bearer", "user": { "id": ME },
    })
}

fn my_cosmetics(owned: &[&str]) -> Value {
    json!({ "owned": owned, "equipped": { "uuid": ME, "cape": null, "back": null, "head": null, "face": null,
        "halo": null, "trail": null, "badge": "void-badge-founder", "emotes": [] } })
}

fn friends_list() -> Value {
    json!({ "friends": [ { "uuid": OTHER, "name": "Moss", "presence": { "state": "online", "server": "mc.hypixel.net", "last_seen": 1 } } ],
            "incoming": [], "outgoing": [] })
}

fn standard_routes(m: &Mock) {
    m.on(AUTH, |r| match r.body["mode"].as_str() {
        Some("challenge") => Reply::ok(json!({ "server_id": "ab".repeat(20), "ts": 1760527200, "nonce": "n".repeat(32) })),
        Some("join" | "token") => Reply::ok(json!({
            "player": { "uuid": ME, "name": "Notch" }, "token_hash": "th", "verification_type": "magiclink",
        })),
        _ => Reply::json(400, json!({ "error": "bad_mode", "message": "Bad mode." })),
    });
    m.on(MOJANG, |_| Reply::empty(204));
    m.on(VERIFY, |_| Reply::ok(session("at1", "rt1")));
    m.on(TOKEN, |_| Reply::ok(session("at2", "rt2")));
    m.on(HEARTBEAT, |r| {
        let server = r.body["server"].as_str().map(str::to_string);
        Reply::ok(json!({ "topic": server.as_ref().map(|_| "server:abc"), "server": server }))
    });
    m.on(OFFLINE, |_| Reply::ok(Value::Null));
    m.on(MY, |_| Reply::ok(my_cosmetics(&["void-badge-founder"])));
    m.on(FRIENDS, |_| Reply::ok(friends_list()));
    m.on(LOOKUP, |_| Reply::ok(json!({ "sets": [] })));
}

struct Rig {
    mock: Mock,
    fake: FakePhoenix,
    online: Online,
    events: Events,
}

/// The event stream with a backlog: `wait_for` takes the oldest matching event and keeps the
/// ones it skipped, so tests need not guess the order background tasks finish in.
struct Events {
    rx: broadcast::Receiver<OnlineEvent>,
    backlog: Vec<OnlineEvent>,
}

async fn rig(mode: AuthMode) -> Rig {
    let mock = Mock::start().await;
    standard_routes(&mock);
    let fake = FakePhoenix::start().await;
    let config = OnlineConfig {
        url: mock.url.clone(),
        anon_key: "anon".into(),
        auth_mode: mode,
        mojang_session_base: format!("{}/mojang", mock.url),
        realtime_url: Some(fake.url.clone()),
    };
    let online = Online::with_timings(Some(config), reqwest::Client::new(), timings());
    let events = Events { rx: online.subscribe(), backlog: Vec::new() };
    Rig { mock, fake, online, events }
}

fn identity() -> McIdentity {
    McIdentity { access_token: "mc-token".into(), uuid: ME_UNDASHED.into(), name: "Notch".into() }
}

async fn signed_in() -> Rig {
    let mut r = rig(AuthMode::Join).await;
    r.online.sign_in(identity()).await.unwrap();
    wait_for(&mut r.events, |e| matches!(e, OnlineEvent::SelfCosmetics(_))).await;
    r
}

async fn wait_for(events: &mut Events, pred: impl Fn(&OnlineEvent) -> bool) -> OnlineEvent {
    if let Some(i) = events.backlog.iter().position(&pred) {
        return events.backlog.remove(i);
    }
    let deadline = tokio::time::Instant::now() + Duration::from_secs(5);
    loop {
        match tokio::time::timeout_at(deadline, events.rx.recv()).await {
            Ok(Ok(e)) if pred(&e) => return e,
            Ok(Ok(e)) => events.backlog.push(e),
            Ok(Err(broadcast::error::RecvError::Lagged(_))) => continue,
            other => panic!("no matching event: {other:?}"),
        }
    }
}

async fn eventually(what: &str, f: impl Fn() -> bool) {
    let deadline = tokio::time::Instant::now() + Duration::from_secs(5);
    while !f() {
        assert!(tokio::time::Instant::now() < deadline, "timed out waiting for {what}");
        tokio::time::sleep(Duration::from_millis(10)).await;
    }
}

// --- auth ------------------------------------------------------------------------------------

#[tokio::test]
async fn join_mode_runs_challenge_mojang_join_and_verify_in_order() {
    let mut r = rig(AuthMode::Join).await;
    let player = r.online.sign_in(identity()).await.unwrap();
    assert_eq!(player, Player { uuid: ME.into(), name: "Notch".into() });
    assert_eq!(r.online.player(), Some(player));
    assert_eq!(r.online.status(), OnlineStatus::Online { uuid: ME.into(), name: "Notch".into() });

    let reqs = r.mock.requests();
    let paths: Vec<&str> = reqs.iter().take(4).map(|q| q.path.as_str()).collect();
    assert!(reqs.iter().all(|q| q.method == "POST"));
    assert_eq!(paths, [AUTH, MOJANG, AUTH, VERIFY]);
    assert_eq!(reqs[0].body, json!({ "mode": "challenge" }));
    assert_eq!(
        reqs[1].body,
        json!({ "accessToken": "mc-token", "selectedProfile": ME_UNDASHED, "serverId": "ab".repeat(20) })
    );
    assert!(reqs[1].headers.get("apikey").is_none(), "the anon key is not Mojang's business");
    assert_eq!(reqs[2].body, json!({ "mode": "join", "name": "Notch", "ts": 1760527200, "nonce": "n".repeat(32) }));
    assert_eq!(reqs[3].body, json!({ "type": "magiclink", "token_hash": "th" }));
    for q in [&reqs[0], &reqs[2], &reqs[3]] {
        assert_eq!(q.headers.get("apikey").map(String::as_str), Some("anon"));
        assert_eq!(q.bearer(), None);
    }
    assert!(
        reqs.iter().filter(|q| q.path != MOJANG).all(|q| !q.body.to_string().contains("mc-token")),
        "the Minecraft token only ever goes to Mojang"
    );

    assert_eq!(
        wait_for(&mut r.events, |e| matches!(e, OnlineEvent::Status(_))).await,
        OnlineEvent::Status(OnlineStatus::Connecting)
    );
    assert_eq!(
        wait_for(&mut r.events, |e| matches!(e, OnlineEvent::Status(_))).await,
        OnlineEvent::Status(OnlineStatus::Online { uuid: ME.into(), name: "Notch".into() })
    );
    wait_for(&mut r.events, |e| matches!(e, OnlineEvent::SelfCosmetics(m) if m.owned == ["void-badge-founder"])).await;
    assert!(r.online.cached_my_cosmetics().is_some());
    wait_for(&mut r.events, |e| matches!(e, OnlineEvent::Friends(f) if f.friends.len() == 1)).await;

    eventually("a heartbeat", || r.mock.count(HEARTBEAT) >= 1).await;
    let hb = &r.mock.to(HEARTBEAT)[0];
    assert_eq!(hb.body, json!({ "server": null }));
    assert_eq!(hb.bearer(), Some("at1"));

    let topic = format!("realtime:user:{ME}");
    let (_, join) = r.fake.expect("user channel join", |_, v| v["event"] == "phx_join" && v["topic"] == topic.as_str()).await;
    assert_eq!(join["payload"]["access_token"], "at1");
}

#[tokio::test]
async fn token_mode_sends_the_token_to_auth_minecraft_and_skips_mojang() {
    let r = rig(AuthMode::Token).await;
    r.online.sign_in(identity()).await.unwrap();
    let reqs = r.mock.requests();
    assert_eq!(reqs[0].path, AUTH);
    assert_eq!(reqs[0].body, json!({ "mode": "token", "access_token": "mc-token" }));
    assert_eq!(reqs[1].path, VERIFY);
    assert_eq!(r.mock.count(MOJANG), 0);
}

#[tokio::test]
async fn a_mojang_refusal_is_a_sentence_and_an_error_status() {
    let r = rig(AuthMode::Join).await;
    r.mock.on(MOJANG, |_| {
        Reply::json(403, json!({ "error": "InsufficientPrivilegesException", "errorMessage": "Invalid" }))
    });
    let err = r.online.sign_in(identity()).await.unwrap_err();
    let Error::Mojang(message) = &err else { panic!("{err:?}") };
    assert!(message.contains("multiplayer"));
    assert_eq!(r.online.status(), OnlineStatus::Error { message: message.clone() });
    assert_eq!(r.mock.count(VERIFY), 0);
    assert_eq!(r.online.player(), None);
}

#[tokio::test]
async fn a_session_for_another_player_is_refused() {
    let r = rig(AuthMode::Join).await;
    r.mock.on(AUTH, |q| match q.body["mode"].as_str() {
        Some("challenge") => Reply::ok(json!({ "server_id": "x", "ts": 1, "nonce": "n" })),
        _ => Reply::ok(json!({ "player": { "uuid": OTHER, "name": "Moss" }, "token_hash": "th", "verification_type": "magiclink" })),
    });
    assert!(matches!(r.online.sign_in(identity()).await, Err(Error::Unexpected(_))));
    assert_eq!(r.mock.count(VERIFY), 0);
}

#[tokio::test]
async fn a_401_forces_one_refresh_then_retries() {
    let mut r = signed_in().await;
    r.mock.on(MY, |q| {
        if q.bearer() == Some("at1") {
            Reply::json(401, json!({ "code": "PGRST301", "message": "JWT expired", "details": null, "hint": null }))
        } else {
            Reply::ok(my_cosmetics(&["void-badge-founder"]))
        }
    });
    r.online.my_cosmetics().await.unwrap();
    let refreshes = r.mock.to(TOKEN);
    assert_eq!(refreshes.len(), 1);
    assert_eq!(refreshes[0].query, "grant_type=refresh_token");
    assert_eq!(refreshes[0].body, json!({ "refresh_token": "rt1" }));
    assert_eq!(r.mock.to(MY).last().unwrap().bearer(), Some("at2"));

    // The socket hears about the new token, by push or by a rejoin carrying it.
    r.fake
        .expect("the new token on the socket", |_, v| {
            (v["event"] == "access_token" && v["payload"]["access_token"] == "at2")
                || (v["event"] == "phx_join" && v["payload"]["access_token"] == "at2")
        })
        .await;
    let _ = &mut r.events;
}

#[tokio::test]
async fn a_second_401_is_returned_not_looped() {
    let r = signed_in().await;
    r.mock.on(EMOTE, |_| Reply::json(401, json!({ "code": "PT401", "message": "signed_out", "details": "Sign in to VOID." })));
    let err = r.online.emote("emote-wave").await.unwrap_err();
    assert_eq!(err.status(), Some(401));
    assert_eq!(err.to_string(), "Sign in to VOID.");
    assert_eq!(r.mock.count(EMOTE), 2);
    assert_eq!(r.mock.count(TOKEN), 1);
}

#[tokio::test]
async fn a_failed_refresh_reruns_the_exchange() {
    let r = signed_in().await;
    r.mock.on(TOKEN, |_| Reply::json(400, json!({ "error": "invalid_grant", "error_description": "Refresh Token Not Found" })));
    r.mock.on(VERIFY, |_| Reply::ok(session("at3", "rt3")));
    let calls = Arc::new(AtomicUsize::new(0));
    let c = calls.clone();
    r.mock.on(EMOTE, move |q| {
        c.fetch_add(1, Ordering::SeqCst);
        if q.bearer() == Some("at3") { Reply::ok(json!({ "at": 7 })) } else { Reply::empty(401) }
    });
    assert_eq!(r.online.emote("emote-wave").await.unwrap(), 7);
    assert_eq!(r.mock.count(MOJANG), 2, "signed in again through Mojang");
}

// --- request policy --------------------------------------------------------------------------

#[tokio::test]
async fn retry_after_is_honoured_once_when_short_and_returned_when_long() {
    let r = signed_in().await;
    let first = Arc::new(AtomicBool::new(true));
    let f = first.clone();
    r.mock.on(EMOTE, move |_| {
        if f.swap(false, Ordering::SeqCst) {
            Reply::json(429, json!({ "code": "PT429", "message": "too_fast", "details": "One emote a second." }))
                .header("retry-after", "0")
        } else {
            Reply::ok(json!({ "at": 1760527200000u64 }))
        }
    });
    assert_eq!(r.online.emote("emote-wave").await.unwrap(), 1760527200000);
    assert_eq!(r.mock.count(EMOTE), 2);

    r.mock.on(EMOTE, |_| {
        Reply::json(429, json!({ "code": "PT429", "message": "too_fast", "details": "One emote a second." }))
            .header("retry-after", "30")
    });
    let err = r.online.emote("emote-wave").await.unwrap_err();
    assert_eq!((err.status(), err.code()), (Some(429), Some("too_fast")));
    assert_eq!(err.to_string(), "One emote a second.");
    assert_eq!(r.mock.count(EMOTE), 3, "no retry for a long Retry-After");
}

// --- modes -----------------------------------------------------------------------------------

#[tokio::test]
async fn disabled_mode_answers_disabled_and_touches_nothing() {
    let online = Online::disabled();
    assert!(!online.is_enabled());
    assert_eq!(online.status(), OnlineStatus::Disabled);
    assert!(matches!(online.sign_in(identity()).await, Err(Error::Disabled)));
    assert!(matches!(online.my_cosmetics().await, Err(Error::Disabled)));
    assert!(matches!(online.equip(Slot::Cape, None).await, Err(Error::Disabled)));
    assert!(matches!(online.set_emotes(vec![]).await, Err(Error::Disabled)));
    assert!(matches!(online.set_tint(Slot::Cape, None).await, Err(Error::Disabled)));
    assert!(matches!(online.lookup_cosmetics(&[OTHER.into()]).await, Err(Error::Disabled)));
    assert!(matches!(online.checkout("void-cape-rift").await, Err(Error::Disabled)));
    assert!(matches!(online.friends().await, Err(Error::Disabled)));
    assert!(matches!(online.friend_request("tavi").await, Err(Error::Disabled)));
    assert!(matches!(online.friend_accept(OTHER).await, Err(Error::Disabled)));
    assert!(matches!(online.set_server(Some("mc.hypixel.net".into())).await, Err(Error::Disabled)));
    assert!(matches!(online.emote("emote-wave").await, Err(Error::Disabled)));
    online.request_cosmetics([OTHER.to_string()]);
    online.sign_out().await;
    assert_eq!(online.status(), OnlineStatus::Disabled);
    assert_eq!(online.current_server(), None);
    assert_eq!(online.cached_cosmetics(OTHER), None);
}

#[tokio::test]
async fn signed_out_calls_fail_without_a_request() {
    let r = rig(AuthMode::Join).await;
    assert_eq!(r.online.status(), OnlineStatus::SignedOut);
    assert!(matches!(r.online.my_cosmetics().await, Err(Error::SignedOut)));
    assert!(matches!(r.online.friends().await, Err(Error::SignedOut)));
    assert!(matches!(r.online.checkout("void-cape-rift").await, Err(Error::SignedOut)));
    assert!(matches!(r.online.emote("emote-wave").await, Err(Error::SignedOut)));
    assert!(matches!(r.online.lookup_cosmetics(&[OTHER.into()]).await, Err(Error::SignedOut)));
    r.online.request_cosmetics([OTHER.to_string()]);
    // Remembered for later, not sent.
    r.online.set_server(Some("Mc.Hypixel.net:25565".into())).await.unwrap();
    assert_eq!(r.online.current_server().as_deref(), Some("mc.hypixel.net"));
    tokio::time::sleep(Duration::from_millis(50)).await;
    assert!(r.mock.requests().is_empty());
}

#[tokio::test]
async fn sign_out_announces_offline_and_forgets_the_session() {
    let mut r = signed_in().await;
    r.online.sign_out().await;
    assert_eq!(r.mock.count(OFFLINE), 1);
    assert_eq!(r.mock.to(OFFLINE)[0].bearer(), Some("at1"));
    assert_eq!(r.online.status(), OnlineStatus::SignedOut);
    wait_for(&mut r.events, |e| *e == OnlineEvent::Status(OnlineStatus::SignedOut)).await;
    assert_eq!(r.online.player(), None);
    assert_eq!(r.online.cached_my_cosmetics(), None);
    assert_eq!(r.online.cached_cosmetics(ME), None);
    assert!(matches!(r.online.my_cosmetics().await, Err(Error::SignedOut)));
}

// --- cosmetics -------------------------------------------------------------------------------

#[tokio::test]
async fn equips_update_the_cached_self_and_emit() {
    let mut r = signed_in().await;
    r.mock.on(EQUIP, |q| {
        let mut set = my_cosmetics(&[])["equipped"].clone();
        if let Some(slot) = q.body["slot"].as_str() {
            set[slot] = q.body["id"].clone();
        }
        if let Some(emotes) = q.body.get("emotes") {
            set["emotes"] = emotes.clone();
        }
        if let Some(tints) = q.body.get("tints").and_then(Value::as_object) {
            // The server deletes a cleared tint rather than storing null.
            let kept: serde_json::Map<String, Value> =
                tints.iter().filter(|(_, v)| !v.is_null()).map(|(k, v)| (k.clone(), v.clone())).collect();
            set["tints"] = Value::Object(kept);
        }
        Reply::ok(json!({ "equipped": set }))
    });

    let set = r.online.equip(Slot::Cape, Some("void-cape-rift".into())).await.unwrap();
    assert_eq!(set.cape.as_deref(), Some("void-cape-rift"));
    assert_eq!(r.mock.to(EQUIP)[0].body, json!({ "slot": "cape", "id": "void-cape-rift" }));
    wait_for(&mut r.events, |e| matches!(e, OnlineEvent::SelfCosmetics(m) if m.equipped.cape.as_deref() == Some("void-cape-rift"))).await;
    assert_eq!(r.online.cached_my_cosmetics().unwrap().equipped.cape.as_deref(), Some("void-cape-rift"));
    assert_eq!(r.online.cached_cosmetics(ME_UNDASHED).unwrap().cape.as_deref(), Some("void-cape-rift"));

    r.online.equip(Slot::Cape, None).await.unwrap();
    assert_eq!(r.mock.to(EQUIP)[1].body, json!({ "slot": "cape", "id": null }));

    r.online.set_emotes(vec!["emote-wave".into()]).await.unwrap();
    assert_eq!(r.mock.to(EQUIP)[2].body, json!({ "emotes": ["emote-wave"] }));
    let nine = (0..9).map(|i| format!("emote-{i}")).collect();
    assert!(matches!(r.online.set_emotes(nine).await, Err(Error::Invalid(_))));

    r.online.set_tint(Slot::Cape, Some("#9F8BFF".into())).await.unwrap();
    assert_eq!(r.mock.to(EQUIP)[3].body, json!({ "tints": { "cape": "#9f8bff" } }));
    r.online.set_tint(Slot::Cape, None).await.unwrap();
    assert_eq!(r.mock.to(EQUIP)[4].body, json!({ "tints": { "cape": null } }));
    assert!(matches!(r.online.set_tint(Slot::Cape, Some("purple".into())).await, Err(Error::Invalid(_))));
    assert!(matches!(r.online.equip(Slot::Cape, Some("Not An Id".into())).await, Err(Error::Invalid(_))));
    assert_eq!(r.mock.count(EQUIP), 5, "invalid input never reaches the server");

    r.mock.on(EQUIP, |_| Reply::json(403, json!({ "error": "not_owned", "message": "You don't own that cape yet." })));
    let err = r.online.equip(Slot::Cape, Some("void-cape-rift".into())).await.unwrap_err();
    assert_eq!(err.code(), Some("not_owned"));
    assert_eq!(err.to_string(), "You don't own that cape yet.");
}

#[tokio::test]
async fn requested_cosmetics_are_batched_cached_and_emitted_once() {
    let mut r = signed_in().await;
    r.mock.on(LOOKUP, |q| {
        let sets: Vec<Value> = q.body["uuids"]
            .as_array()
            .unwrap()
            .iter()
            .filter(|u| *u == OTHER)
            .map(|u| json!({ "uuid": u, "cape": "void-cape-rift", "emotes": [] }))
            .collect();
        Reply::ok(json!({ "sets": sets }))
    });

    r.online.request_cosmetics([OTHER_UNDASHED.to_string(), "garbage".to_string()]);
    r.online.request_cosmetics([THIRD.to_string(), OTHER.to_string()]);
    let e = wait_for(&mut r.events, |e| matches!(e, OnlineEvent::Cosmetics(_))).await;
    let OnlineEvent::Cosmetics(sets) = e else { unreachable!() };
    assert_eq!(sets.len(), 1);
    assert_eq!(sets[0].uuid, OTHER);
    assert_eq!(sets[0].cape.as_deref(), Some("void-cape-rift"));
    let lookups = r.mock.to(LOOKUP);
    assert_eq!(lookups.len(), 1, "one request for both calls");
    assert_eq!(lookups[0].body, json!({ "uuids": [THIRD, OTHER] }));
    assert_eq!(lookups[0].bearer(), Some("at1"));

    // Both answers, hit and miss, are fresh: nothing more is asked.
    r.online.request_cosmetics([OTHER.to_string(), THIRD.to_string()]);
    tokio::time::sleep(Duration::from_millis(100)).await;
    assert_eq!(r.mock.count(LOOKUP), 1);
    assert_eq!(r.online.cached_cosmetics(OTHER_UNDASHED).unwrap().cape.as_deref(), Some("void-cape-rift"));
    assert_eq!(r.online.cached_cosmetics(THIRD), None);

    let direct = r.online.lookup_cosmetics(&[OTHER.into(), THIRD.into()]).await.unwrap();
    assert_eq!(direct.len(), 1);
    assert_eq!(r.mock.count(LOOKUP), 1, "served from the cache");
}

#[tokio::test]
async fn checkout_validates_the_url_and_watches_for_the_entitlement() {
    let mut r = signed_in().await;
    let purchased = Arc::new(AtomicBool::new(false));
    let p = purchased.clone();
    r.mock.on(MY, move |_| {
        if p.load(Ordering::SeqCst) {
            Reply::ok(my_cosmetics(&["void-badge-founder", "void-cape-rift"]))
        } else {
            Reply::ok(my_cosmetics(&["void-badge-founder"]))
        }
    });
    r.mock.on(CHECKOUT, |q| {
        assert_eq!(q.body, json!({ "item": "void-cape-rift" }));
        Reply::ok(json!({ "url": "https://checkout.stripe.com/c/pay/cs_test_1", "session_id": "cs_test_1" }))
    });
    let before = r.mock.count(MY);
    let s = r.online.checkout("void-cape-rift").await.unwrap();
    assert_eq!(s, CheckoutSession { url: "https://checkout.stripe.com/c/pay/cs_test_1".into(), session_id: "cs_test_1".into() });

    eventually("polling", || r.mock.count(MY) >= before + 2).await;
    purchased.store(true, Ordering::SeqCst);
    wait_for(&mut r.events, |e| matches!(e, OnlineEvent::SelfCosmetics(m) if m.owned.iter().any(|i| i == "void-cape-rift"))).await;
    tokio::time::sleep(Duration::from_millis(100)).await;
    let settled = r.mock.count(MY);
    tokio::time::sleep(Duration::from_millis(250)).await;
    assert_eq!(r.mock.count(MY), settled, "the watch stops once the item is owned");

    r.mock.on(CHECKOUT, |_| Reply::ok(json!({ "url": "http://checkout.example/c/cs_x", "session_id": "cs_x" })));
    assert!(matches!(r.online.checkout("void-cape-rift").await, Err(Error::Unexpected(_))));
    r.mock.on(CHECKOUT, |_| Reply::json(409, json!({ "error": "already_owned", "message": "You already own that." })));
    assert_eq!(r.online.checkout("void-cape-rift").await.unwrap_err().code(), Some("already_owned"));
    assert!(matches!(r.online.checkout("Not An Id").await, Err(Error::Invalid(_))));
}

// --- friends ---------------------------------------------------------------------------------

#[tokio::test]
async fn friends_calls_send_their_action_and_emit_the_list() {
    let mut r = signed_in().await;
    wait_for(&mut r.events, |e| matches!(e, OnlineEvent::Friends(_))).await; // the first poll
    let list = r.online.friend_request("  tavi ").await.unwrap();
    assert_eq!(list.friends[0].name, "Moss");
    wait_for(&mut r.events, |e| matches!(e, OnlineEvent::Friends(_))).await;

    r.online.friend_accept(OTHER_UNDASHED).await.unwrap();
    r.online.friend_decline(OTHER).await.unwrap();
    r.online.friend_cancel(OTHER).await.unwrap();
    r.online.friend_remove(OTHER).await.unwrap();
    r.online.friends().await.unwrap();
    let bodies: Vec<Value> = r.mock.to(FRIENDS).into_iter().skip(1).map(|q| q.body).collect();
    assert_eq!(
        bodies,
        vec![
            json!({ "action": "request", "name": "tavi" }),
            json!({ "action": "accept", "uuid": OTHER }),
            json!({ "action": "decline", "uuid": OTHER }),
            json!({ "action": "cancel", "uuid": OTHER }),
            json!({ "action": "remove", "uuid": OTHER }),
            json!({ "action": "list" }),
        ]
    );

    assert!(matches!(r.online.friend_remove("nope").await, Err(Error::Invalid(_))));
    assert!(matches!(r.online.friend_request("   ").await, Err(Error::Invalid(_))));
    assert_eq!(r.mock.count(FRIENDS), 7);

    r.mock.on(FRIENDS, |_| Reply::json(409, json!({ "error": "already_friends", "message": "You're already friends." })));
    let err = r.online.friend_request("Moss").await.unwrap_err();
    assert_eq!((err.code(), err.to_string().as_str()), (Some("already_friends"), "You're already friends."));
}

// --- presence and realtime -------------------------------------------------------------------

#[tokio::test]
async fn servers_presence_and_emotes_flow_through_realtime() {
    let mut r = signed_in().await;
    let user_topic = format!("realtime:user:{ME}");
    r.fake.expect("user join", |_, v| v["event"] == "phx_join" && v["topic"] == user_topic.as_str()).await;

    r.online.set_server(Some(" Mc.Hypixel.net.:25565 ".into())).await.unwrap();
    assert_eq!(r.online.current_server().as_deref(), Some("mc.hypixel.net"));
    assert_eq!(r.mock.to(HEARTBEAT).last().unwrap().body, json!({ "server": "mc.hypixel.net" }));
    r.fake.expect("server join", |_, v| v["event"] == "phx_join" && v["topic"] == "realtime:server:abc").await;
    let (_, track) = r.fake.expect("track", |_, v| v["event"] == "presence").await;
    assert_eq!(track["payload"]["payload"], json!({ "uuid": ME, "name": "Notch" }));

    // Presence: everyone but us, and their cosmetics get prefetched.
    r.fake.push(json!({ "topic": "realtime:server:abc", "event": "presence_state", "ref": null, "payload": {
        ME: { "metas": [ { "phx_ref": "a", "uuid": ME } ] },
        OTHER: { "metas": [ { "phx_ref": "b", "uuid": OTHER } ] },
    }}));
    let e = wait_for(&mut r.events, |e| matches!(e, OnlineEvent::ServerPlayers { .. })).await;
    assert_eq!(e, OnlineEvent::ServerPlayers { topic: "server:abc".into(), uuids: vec![OTHER.into()] });
    eventually("a prefetch", || r.mock.to(LOOKUP).iter().any(|q| q.body == json!({ "uuids": [OTHER] }))).await;

    // Emotes: our own echo is ignored, others surface.
    for (who, id) in [(ME, "emote-self"), (OTHER, "emote-wave")] {
        r.fake.push(json!({ "topic": "realtime:server:abc", "event": "broadcast", "ref": null, "payload": {
            "type": "broadcast", "event": "emote", "payload": { "uuid": who, "id": id, "at": 42 } }}));
    }
    let e = wait_for(&mut r.events, |e| matches!(e, OnlineEvent::Emote { .. })).await;
    assert_eq!(e, OnlineEvent::Emote { uuid: OTHER.into(), id: "emote-wave".into(), at: 42 });

    // User-channel broadcasts trigger refreshes.
    let before = r.mock.count(MY);
    r.fake.push(json!({ "topic": user_topic, "event": "broadcast", "payload": {
        "type": "broadcast", "event": "entitlements", "payload": { "item": "void-cape-rift", "source": "stripe" } }}));
    eventually("an entitlement refresh", || r.mock.count(MY) > before).await;
    let before = r.mock.count(FRIENDS);
    r.fake.push(json!({ "topic": user_topic, "event": "broadcast", "payload": {
        "type": "broadcast", "event": "friends", "payload": { "kind": "request", "uuid": OTHER } }}));
    eventually("a friends refresh", || r.mock.count(FRIENDS) > before).await;
    wait_for(&mut r.events, |e| matches!(e, OnlineEvent::Friends(_))).await;

    // The emote RPC.
    r.mock.on(EMOTE, |q| {
        assert_eq!(q.body, json!({ "id": "emote-wave" }));
        Reply::ok(json!({ "at": 1760527200000u64 }))
    });
    assert_eq!(r.online.emote("emote-wave").await.unwrap(), 1760527200000);

    // Leaving: the channel goes, listeners hear an empty set, the heartbeat says null.
    r.online.set_server(None).await.unwrap();
    r.fake.expect("leave", |_, v| v["event"] == "phx_leave" && v["topic"] == "realtime:server:abc").await;
    let e = wait_for(&mut r.events, |e| matches!(e, OnlineEvent::ServerPlayers { .. })).await;
    assert_eq!(e, OnlineEvent::ServerPlayers { topic: "server:abc".into(), uuids: vec![] });
    assert_eq!(r.mock.to(HEARTBEAT).last().unwrap().body, json!({ "server": null }));
    assert_eq!(r.online.current_server(), None);
}

#[tokio::test]
async fn a_server_remembered_while_signed_out_is_announced_at_sign_in() {
    let r = rig(AuthMode::Join).await;
    r.online.set_server(Some("play.example.org:25566".into())).await.unwrap();
    r.online.sign_in(identity()).await.unwrap();
    eventually("a heartbeat", || r.mock.count(HEARTBEAT) >= 1).await;
    assert_eq!(r.mock.to(HEARTBEAT)[0].body, json!({ "server": "play.example.org:25566" }));
}
