//! `auth_login` · `auth_logout` · `auth_current` · `auth_offline` · `auth_accounts` ·
//! `auth_switch` · `auth_remove`
//!
//! All of it is `void_core::auth` underneath. The device flow is split in two on
//! purpose: `login` returns the moment Microsoft hands back a user code, because that
//! code is what the player has to go and type, and `login_poll` runs on afterwards
//! pushing `auth:status` until there is a session or a failure.
//!
//! **Several accounts.** `accounts.json` lists every account the launcher has signed in
//! (no tokens), each Microsoft account's refresh token lives in its own keychain entry,
//! and exactly one is active. Signing in adds an account and makes it active; switching
//! makes another active and refreshes its token; removing forgets its row and token.
//! `AppState::session` always holds the active account's session.

use void_core::auth::{self, Accounts, Auth, Session, Stage};

use crate::error::Error;
use crate::events::{emit, Emitter, AUTH_STATUS};
use crate::models::{Account, AccountList, AuthStatus, DeviceCode};
use crate::state::AppState;

/// Refresh the Minecraft access token when it has less than this left. A launch can
/// last hours, but the game only uses the token when joining a server, and a session
/// shorter than this margin would be refreshed on the next launch anyway.
const REFRESH_MARGIN_SECS: u64 = 30 * 60;

/// Ask Microsoft for a device code.
///
/// Needs an Azure application id: compiled in at build time (`VOID_MS_CLIENT_ID`), or
/// `ms_client_id` in `config.json`, or `$VOID_MS_CLIENT_ID` at run time. Without one the
/// error names the file to put it in.
pub async fn login(state: &AppState) -> Result<(DeviceCode, auth::DeviceCode), Error> {
    let client_id = state.config()?.ms_client_id(&state.paths)?;
    let auth = Auth::new(state.http.clone(), client_id);
    let code = auth.start_device_code().await?;
    Ok((
        DeviceCode {
            user_code: code.user_code.clone(),
            verification_uri: code.verification_uri.clone(),
            expires_in_s: code.expires_in,
            interval_s: code.interval,
            message: code.message.clone(),
        },
        code,
    ))
}

/// Poll until the player finishes in the browser, then exchange the token chain.
///
/// Runs after the command has returned. Every stage is announced as it *starts*,
/// because the chain is four network hops (Microsoft → Xbox Live → XSTS → Minecraft
/// services) and a silent twenty seconds reads as a hang.
pub async fn login_poll(state: &AppState, emitter: &dyn Emitter, code: auth::DeviceCode) {
    let result = complete(state, emitter, code).await;
    match result {
        Ok(session) => {
            let account = Account::from(&session);
            *state.session.lock().unwrap() = Some(session);
            emit(emitter, AUTH_STATUS, &AuthStatus::Complete { account });
        }
        Err(e) => {
            emit(emitter, AUTH_STATUS, &AuthStatus::Failed { message: e.to_string() });
        }
    }
}

async fn complete(
    state: &AppState,
    emitter: &dyn Emitter,
    code: auth::DeviceCode,
) -> Result<Session, Error> {
    let client_id = state.config()?.ms_client_id(&state.paths)?;
    let auth = Auth::new(state.http.clone(), client_id);

    emit(
        emitter,
        AUTH_STATUS,
        &AuthStatus::Pending { message: "Waiting for you to finish signing in…".into() },
    );
    let tokens = auth.poll_for_token(&code).await?;

    let session = auth
        .minecraft_session_with(&tokens.access_token, |stage| {
            let status = match stage {
                Stage::XboxLive => {
                    AuthStatus::Xbox { message: "Authenticating with Xbox Live…".into() }
                }
                Stage::Minecraft => {
                    AuthStatus::Minecraft { message: "Fetching your Minecraft profile…".into() }
                }
            };
            emit(emitter, AUTH_STATUS, &status);
        })
        .await?;

    // The refresh token is keyed by the account's UUID, which only exists once the chain
    // has finished — so it is stored here rather than straight after the poll.
    auth::store_login(&state.paths, &session, tokens.refresh_token.as_deref())?;
    Ok(session)
}

/// Sign the active account in at startup, with no player interaction.
///
/// `Ok(None)` is "nobody is signed in", not a failure. A Microsoft account whose refresh
/// fails (offline, Microsoft down) is still restored — with an expired token — so the
/// player can launch singleplayer with no network; `launch` tries the refresh again.
pub async fn restore(state: &AppState) -> Result<Option<Account>, Error> {
    let accounts = Accounts::load(&state.paths)?;
    let Some(active) = accounts.active_account().cloned() else {
        return Ok(None);
    };
    let session = activate(state, &active).await;
    let account = Account::from(&session);
    *state.session.lock().unwrap() = Some(session);
    Ok(Some(account))
}

/// The session for an account: a fresh token for a Microsoft account when the network
/// and the refresh token allow, a token-less one otherwise.
async fn activate(state: &AppState, account: &auth::StoredAccount) -> Session {
    if account.is_offline() {
        return account.session();
    }
    match state.config().and_then(|c| c.ms_client_id(&state.paths).map_err(Error::from)) {
        Ok(client_id) => {
            match auth::sign_in_account(&state.http, &state.paths, &client_id, &account.uuid).await {
                Ok(Some(session)) => return session,
                Ok(None) => tracing::warn!(account = %account.username, "no stored refresh token"),
                Err(e) => tracing::warn!(error = %e, account = %account.username, "silent sign-in failed; keeping the account without a fresh token"),
            }
        }
        Err(e) => tracing::warn!(error = %e, "no Microsoft client id; cannot refresh"),
    }
    account.session()
}

/// The active session, with its access token refreshed if it expires within
/// [`REFRESH_MARGIN_SECS`]. Called by `launch` right before the JVM is spawned.
///
/// A refresh that fails is not fatal: singleplayer and offline-mode servers do not need
/// a valid token, so the launch goes ahead with a warning in the log — only joining an
/// online-mode server would be refused, by the server, with its own message.
pub async fn fresh_session(state: &AppState) -> Result<Session, Error> {
    let session = state.session.lock().unwrap().clone().ok_or(Error::NotSignedIn)?;
    if !session.expires_within(REFRESH_MARGIN_SECS) {
        return Ok(session);
    }
    let client_id = match state.config()?.ms_client_id(&state.paths) {
        Ok(id) => id,
        Err(e) => {
            tracing::warn!(error = %e, "cannot refresh the Minecraft token without a client id");
            return Ok(session);
        }
    };
    match auth::sign_in_account(&state.http, &state.paths, &client_id, &session.uuid).await {
        Ok(Some(fresh)) => {
            tracing::info!(account = %fresh.username, "refreshed the Minecraft access token before launch");
            *state.session.lock().unwrap() = Some(fresh.clone());
            Ok(fresh)
        }
        Ok(None) => {
            tracing::warn!("no stored refresh token; launching with the current token");
            Ok(session)
        }
        Err(e) => {
            tracing::warn!(error = %e, "could not refresh the Minecraft token; online-mode servers may refuse the login");
            Ok(session)
        }
    }
}

/// Forget the active account (the profile menu's *Sign out*). Another account, if any,
/// becomes active.
pub async fn logout(state: &AppState) -> Result<Option<Account>, Error> {
    let active = state.session.lock().unwrap().as_ref().map(|s| s.uuid.clone());
    match active {
        Some(uuid) => remove(state, &uuid).await.map(|list| list.active_account()),
        None => Ok(None),
    }
}

pub fn current(state: &AppState) -> Result<Option<Account>, Error> {
    Ok(state.session.lock().unwrap().as_ref().map(Account::from))
}

/// Every account, and which is active.
pub fn accounts(state: &AppState) -> Result<AccountList, Error> {
    Ok(AccountList::from(&Accounts::load(&state.paths)?))
}

/// Make another account active.
pub async fn switch(state: &AppState, uuid: &str) -> Result<Account, Error> {
    if state.game.lock().unwrap().is_running() {
        return Err(Error::Other("Close Minecraft before switching accounts.".into()));
    }
    let mut accounts = Accounts::load(&state.paths)?;
    if !accounts.set_active(uuid) {
        return Err(Error::Other("That account is not signed in on this launcher.".into()));
    }
    accounts.save(&state.paths)?;
    let stored = accounts.active_account().cloned().expect("just set active");
    let session = activate(state, &stored).await;
    let account = Account::from(&session);
    *state.session.lock().unwrap() = Some(session);
    Ok(account)
}

/// Forget an account: its row and its refresh token.
pub async fn remove(state: &AppState, uuid: &str) -> Result<AccountList, Error> {
    let was_active = state.session.lock().unwrap().as_ref().is_some_and(|s| s.uuid == uuid);
    if was_active && state.game.lock().unwrap().is_running() {
        return Err(Error::Other("Close Minecraft before signing this account out.".into()));
    }
    let accounts = auth::forget_account(&state.paths, uuid)?;
    if was_active {
        let next = match accounts.active_account().cloned() {
            Some(next) => Some(activate(state, &next).await),
            None => None,
        };
        *state.session.lock().unwrap() = next;
    }
    Ok(AccountList::from(&accounts))
}

/// Sign in without Microsoft. Works on offline-mode servers only, and needs no network.
/// Adds the name as an account and makes it active.
pub fn offline(state: &AppState, name: &str) -> Result<Account, Error> {
    let name = name.trim();
    let valid = (1..=16).contains(&name.len())
        && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_');
    if !valid {
        return Err(Error::Other(
            "A Minecraft name is 1–16 characters: letters, digits and underscores.".into(),
        ));
    }
    let session = Session::offline(name);
    auth::save_profile(&state.paths, &session)?;
    let account = Account::from(&session);
    *state.session.lock().unwrap() = Some(session);
    Ok(account)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::scratch_state;

    fn block<F: std::future::Future>(f: F) -> F::Output {
        tokio::runtime::Runtime::new().unwrap().block_on(f)
    }

    #[test]
    fn offline_sign_in_sticks_survives_a_restart_and_clears_on_sign_out() {
        let state = scratch_state();
        assert!(current(&state).unwrap().is_none());

        let account = offline(&state, "Searge").unwrap();
        assert_eq!(account.name, "Searge");
        assert_eq!(account.kind, "offline");
        // The vanilla offline-mode UUID, so the identity matches singleplayer worlds.
        assert_eq!(account.uuid, void_core::auth::offline_uuid("Searge"));
        assert_eq!(current(&state).unwrap().unwrap().uuid, account.uuid);

        // A new launcher process finds the account.
        let restarted = crate::state::AppState::new(void_core::Paths::at(state.paths.root()))
            .unwrap();
        let restored = block(restore(&restarted)).unwrap();
        assert_eq!(restored.unwrap().name, "Searge");

        block(logout(&state)).unwrap();
        assert!(current(&state).unwrap().is_none());
    }

    #[test]
    fn several_accounts_switch_and_remove() {
        let state = scratch_state();
        let a = offline(&state, "Alpha").unwrap();
        let b = offline(&state, "Bravo").unwrap();
        let list = accounts(&state).unwrap();
        assert_eq!(list.accounts.len(), 2);
        assert_eq!(list.active.as_deref(), Some(b.uuid.as_str()), "the newest sign-in is active");

        let switched = block(switch(&state, &a.uuid)).unwrap();
        assert_eq!(switched.name, "Alpha");
        assert_eq!(current(&state).unwrap().unwrap().name, "Alpha");
        assert!(block(switch(&state, "not-an-account")).is_err());

        // Removing the active account activates the remaining one.
        let left = block(remove(&state, &a.uuid)).unwrap();
        assert_eq!(left.accounts.len(), 1);
        assert_eq!(current(&state).unwrap().unwrap().name, "Bravo");

        let none = block(remove(&state, &b.uuid)).unwrap();
        assert!(none.accounts.is_empty());
        assert!(current(&state).unwrap().is_none());
    }

    #[test]
    fn a_bad_name_signs_nobody_in() {
        let state = scratch_state();
        for bad in ["", "a name with spaces", "waytoolongminecraftname", "hy-phen"] {
            assert!(offline(&state, bad).is_err(), "{bad} should be rejected");
        }
        assert!(current(&state).unwrap().is_none());
    }

    #[test]
    fn an_account_never_carries_an_access_token() {
        let state = scratch_state();
        offline(&state, "Searge").unwrap();
        let json = serde_json::to_string(&current(&state).unwrap().unwrap()).unwrap();
        assert!(!json.contains("access_token"), "{json}");
        let json = serde_json::to_string(&accounts(&state).unwrap()).unwrap();
        assert!(!json.contains("access_token"), "{json}");
    }

    #[test]
    fn an_offline_session_is_never_refreshed_before_launch() {
        let state = scratch_state();
        offline(&state, "Searge").unwrap();
        let s = block(fresh_session(&state)).unwrap();
        assert_eq!(s.access_token, "0");
    }

    #[test]
    fn launching_with_nobody_signed_in_is_refused() {
        let state = scratch_state();
        assert!(matches!(block(fresh_session(&state)), Err(Error::NotSignedIn)));
    }

    #[tokio::test]
    async fn sign_in_without_a_client_id_fails_with_the_file_to_fix() {
        if void_core::BUILT_IN_CLIENT_ID.is_some() || std::env::var(void_core::CLIENT_ID_ENV).is_ok() {
            return; // this build carries an id; there is nothing to fix
        }
        let state = scratch_state();
        let err = login(&state).await.unwrap_err().to_string();
        assert!(err.contains("config.json"), "{err}");
    }

    #[tokio::test]
    async fn restore_on_a_fresh_installation_is_nobody_rather_than_an_error() {
        let state = scratch_state();
        assert!(restore(&state).await.unwrap().is_none());
    }
}
