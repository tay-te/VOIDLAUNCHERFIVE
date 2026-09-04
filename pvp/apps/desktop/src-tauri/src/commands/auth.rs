//! `auth_login` · `auth_logout` · `auth_current` · `auth_offline`
//!
//! All four are `void_core::auth` underneath. The device flow is split in two on
//! purpose: `login` returns the moment Microsoft hands back a user code, because that
//! code is what the player has to go and type, and `login_poll` runs on afterwards
//! pushing `auth:status` until there is a session or a failure.

use void_core::auth::{self, Auth, Session, TokenStore};

use crate::error::Error;
use crate::events::{emit, Emitter, AUTH_STATUS};
use crate::models::{Account, AuthStatus, DeviceCode};
use crate::state::AppState;

/// Ask Microsoft for a device code.
///
/// Needs an Azure application id (`$VOID_MS_CLIENT_ID` or `config.json`); `void-core`
/// deliberately compiles none in, since an Azure app is per-publisher and would be a
/// credential in the repository. The resulting error names the file to put it in.
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
/// Runs after the command has returned. Every stage is announced, because the chain is
/// four network hops (Microsoft → Xbox Live → XSTS → Minecraft services) and a silent
/// twenty seconds reads as a hang.
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

    // Save the refresh token before the rest of the chain: if Xbox is having a bad day,
    // the next start can still sign in silently rather than sending the player back to
    // the browser.
    if let Some(refresh) = &tokens.refresh_token {
        if let Err(e) = TokenStore::new(&state.paths).save(refresh) {
            tracing::warn!(error = %e, "could not store the refresh token");
        }
    }

    emit(
        emitter,
        AUTH_STATUS,
        &AuthStatus::Xbox { message: "Authenticating with Xbox Live…".into() },
    );
    emit(
        emitter,
        AUTH_STATUS,
        &AuthStatus::Minecraft { message: "Fetching your Minecraft profile…".into() },
    );
    let session = auth.minecraft_session(&tokens.access_token).await?;
    auth::save_profile(&state.paths, &session)?;
    Ok(session)
}

/// Sign in from the stored refresh token, with no player interaction.
///
/// Called once at startup. `Ok(None)` is "nobody is signed in", not a failure; a
/// *failed* refresh is also not fatal — the cached profile is still shown so the dock
/// has a name, and the launch guard is what stops a stale session reaching the JVM.
pub async fn restore(state: &AppState) -> Result<Option<Account>, Error> {
    let config = state.config()?;
    if let Ok(client_id) = config.ms_client_id(&state.paths) {
        match auth::sign_in_silently(&state.http, &state.paths, &client_id).await {
            Ok(Some(session)) => {
                let account = Account::from(&session);
                *state.session.lock().unwrap() = Some(session);
                return Ok(Some(account));
            }
            Ok(None) => {}
            Err(e) => tracing::warn!(error = %e, "silent sign-in failed; falling back to the cached profile"),
        }
    }

    // No client id, no stored token, or a refresh that failed: an offline profile
    // cached by a previous run still signs the player in for real, since an offline
    // session needs no network at all.
    match auth::load_profile(&state.paths)? {
        Some(session) if session.is_offline() => {
            let account = Account::from(&session);
            *state.session.lock().unwrap() = Some(session);
            Ok(Some(account))
        }
        _ => Ok(None),
    }
}

pub fn logout(state: &AppState) -> Result<(), Error> {
    *state.session.lock().unwrap() = None;
    if let Err(e) = TokenStore::new(&state.paths).clear() {
        tracing::warn!(error = %e, "could not clear the stored refresh token");
    }
    auth::clear_profile(&state.paths)?;
    Ok(())
}

pub fn current(state: &AppState) -> Result<Option<Account>, Error> {
    Ok(state.session.lock().unwrap().as_ref().map(Account::from))
}

/// Sign in without Microsoft. Works on offline-mode servers only, and needs no network.
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

        // A new launcher process finds the cached profile.
        let restarted = crate::state::AppState::new(void_core::Paths::at(state.paths.root()))
            .unwrap();
        let restored =
            tokio::runtime::Runtime::new().unwrap().block_on(restore(&restarted)).unwrap();
        assert_eq!(restored.unwrap().name, "Searge");

        logout(&state).unwrap();
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
    }

    #[tokio::test]
    async fn sign_in_without_a_client_id_fails_with_the_file_to_fix() {
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
