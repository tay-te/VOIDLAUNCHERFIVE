//! `online_status` · `cosmetics_state` · `cosmetics_equip` · `cosmetics_set_emotes` ·
//! `store_checkout` · `friends_*`
//!
//! Thin over `void_online::Online` (docs/online-contract.md). Rust owns the network and the
//! auth: the webview never sees a token, and the mod never talks HTTP. This module also
//! signs the backend in and out alongside the Microsoft account, and turns `OnlineEvent`s
//! into the `online:*` events the window listens on.

use std::sync::Arc;

use void_online::{
    CheckoutSession, EquippedSet, FriendsList, McIdentity, Online, OnlineEvent, OnlineStatus, Slot,
};

use crate::error::Error;
use crate::events::{emit, Emitter, ONLINE_COSMETICS, ONLINE_FRIENDS, ONLINE_STATUS};
use crate::models::CosmeticsState;
use crate::state::AppState;

pub fn status(state: &AppState) -> OnlineStatus {
    state.online.status()
}

fn is_online(online: &Online) -> bool {
    matches!(online.status(), OnlineStatus::Online { .. })
}

/// What the Store shows. Offline is not an error: the window falls back to its local copy.
pub async fn cosmetics_state(state: &AppState) -> Result<CosmeticsState, Error> {
    if !is_online(&state.online) {
        return Ok(CosmeticsState::offline());
    }
    Ok(CosmeticsState::from(state.online.my_cosmetics().await?))
}

pub async fn equip(state: &AppState, slot: &str, id: Option<String>) -> Result<EquippedSet, Error> {
    let slot = Slot::parse(slot).ok_or_else(|| Error::Other(format!("`{slot}` is not a cosmetic slot.")))?;
    Ok(state.online.equip(slot, id).await?)
}

pub async fn set_emotes(state: &AppState, ids: Vec<String>) -> Result<EquippedSet, Error> {
    Ok(state.online.set_emotes(ids).await?)
}

/// A Stripe Checkout Session for `item`. The caller opens `url` in the system browser;
/// `void-online` then watches for the entitlement and pushes it as `online:cosmetics`.
pub async fn checkout(state: &AppState, item: &str) -> Result<CheckoutSession, Error> {
    let session = state.online.checkout(item).await?;
    if !session.url.starts_with("https://") {
        return Err(Error::Other("Checkout returned an address that is not secure; nothing was opened.".into()));
    }
    Ok(session)
}

pub async fn friends(state: &AppState) -> Result<FriendsList, Error> {
    Ok(state.online.friends().await?)
}

pub async fn friend_request(state: &AppState, name: &str) -> Result<FriendsList, Error> {
    Ok(state.online.friend_request(name).await?)
}

pub async fn friend_accept(state: &AppState, uuid: &str) -> Result<FriendsList, Error> {
    Ok(state.online.friend_accept(uuid).await?)
}

pub async fn friend_decline(state: &AppState, uuid: &str) -> Result<FriendsList, Error> {
    Ok(state.online.friend_decline(uuid).await?)
}

pub async fn friend_cancel(state: &AppState, uuid: &str) -> Result<FriendsList, Error> {
    Ok(state.online.friend_cancel(uuid).await?)
}

pub async fn friend_remove(state: &AppState, uuid: &str) -> Result<FriendsList, Error> {
    Ok(state.online.friend_remove(uuid).await?)
}

/// Sign the backend in with the current Microsoft session. A no-op for an offline account,
/// for nobody signed in, and in a build without a backend. Failure is logged and reported
/// as `online:status` by `void-online` itself; the launcher keeps working offline.
pub async fn sign_in(state: &AppState) {
    if !state.online.is_enabled() {
        return;
    }
    let session = state.session.lock().unwrap().clone();
    let Some(session) = session.filter(|s| !s.is_offline()) else {
        return;
    };
    let identity = McIdentity {
        access_token: session.access_token,
        uuid: session.uuid,
        name: session.username,
    };
    if let Err(e) = state.online.sign_in(identity).await {
        tracing::warn!(error = %e, "could not sign in to the online backend; online features stay off");
    }
}

pub async fn sign_out(state: &AppState) {
    state.online.sign_out().await;
}

/// Forward `void-online`'s events to the window for the life of the app. A future rather
/// than a spawn so the caller picks the runtime (Tauri's, or a test's).
pub fn forwarder(online: Online, emitter: Arc<dyn Emitter>) -> impl std::future::Future<Output = ()> + Send + 'static {
    let mut events = online.subscribe();
    async move {
        loop {
            match events.recv().await {
                Ok(OnlineEvent::Status(status)) => {
                    let online_now = matches!(status, OnlineStatus::Online { .. });
                    emit(emitter.as_ref(), ONLINE_STATUS, &status);
                    if !online_now && !matches!(status, OnlineStatus::Connecting) {
                        emit(emitter.as_ref(), ONLINE_COSMETICS, &CosmeticsState::offline());
                    }
                }
                Ok(OnlineEvent::SelfCosmetics(mine)) => {
                    emit(emitter.as_ref(), ONLINE_COSMETICS, &CosmeticsState::from(mine));
                }
                Ok(OnlineEvent::Friends(list)) => emit(emitter.as_ref(), ONLINE_FRIENDS, &list),
                // Other players' sets, emotes and server presence are the game's business;
                // `adapters::online_bridge` forwards those over the bridge.
                Ok(_) => {}
                Err(tokio::sync::broadcast::error::RecvError::Lagged(n)) => {
                    tracing::warn!(dropped = n, "online event forwarder fell behind");
                }
                Err(tokio::sync::broadcast::error::RecvError::Closed) => break,
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::scratch_state;

    #[tokio::test]
    async fn a_build_without_a_backend_is_offline_not_broken() {
        let state = scratch_state();
        assert!(matches!(status(&state), OnlineStatus::Disabled));
        let cosmetics = cosmetics_state(&state).await.unwrap();
        assert!(!cosmetics.online);
        assert!(cosmetics.equipped.is_none());
        // Signing in is a quiet no-op, with or without an account.
        sign_in(&state).await;
        crate::commands::auth::offline(&state, "Searge").unwrap();
        sign_in(&state).await;
        assert!(matches!(status(&state), OnlineStatus::Disabled));
    }

    #[tokio::test]
    async fn calls_that_need_the_backend_fail_with_a_sentence() {
        let state = scratch_state();
        for err in [
            friends(&state).await.unwrap_err().to_string(),
            checkout(&state, "void-cape-rift").await.unwrap_err().to_string(),
            equip(&state, "cape", None).await.unwrap_err().to_string(),
        ] {
            assert!(err.ends_with('.'), "{err}");
            assert!(!err.contains("Error"), "{err}");
        }
        assert!(equip(&state, "aura", None).await.unwrap_err().to_string().contains("not a cosmetic slot"));
    }

    #[tokio::test]
    async fn a_disabled_client_sends_the_window_nothing() {
        use crate::events::test_support::Recorder;
        let online = Online::disabled();
        let rec = Recorder::default();
        tokio::spawn(forwarder(online.clone(), Arc::new(rec.clone())));
        // A disabled client never emits anything for the window.
        tokio::time::sleep(std::time::Duration::from_millis(20)).await;
        assert!(rec.names().is_empty());
    }
}
