//! `auth_login` · `auth_logout` · `auth_current` · `auth_offline`

use crate::adapters::auth;
use crate::error::Error;
use crate::events::Emitter;
use crate::models::{Account, DeviceCode};
use crate::state::AppState;

/// Start the Microsoft device flow.
///
/// Returns the user code and verification URL **immediately** — that is the whole
/// point of the device flow, and the Settings screen shows both while the exchange
/// runs. Completion arrives as `auth:status` events.
pub fn login(_state: &AppState) -> Result<DeviceCode, Error> {
    auth::begin_device_flow()
}

/// The half of `login` that runs after the command has returned.
pub async fn login_poll(emitter: &dyn Emitter) {
    auth::run_device_flow(emitter).await;
}

pub fn logout(state: &AppState) -> Result<(), Error> {
    *state.account.lock().unwrap() = None;
    // TODO(integrate): void-core also drops the refresh token from the OS keychain
    // (§12.1). Signing out here only clears the in-memory session.
    Ok(())
}

pub fn current(state: &AppState) -> Result<Option<Account>, Error> {
    Ok(state.account.lock().unwrap().clone())
}

/// Sign in without Microsoft. Real, and the path that works today.
pub fn offline(state: &AppState, name: &str) -> Result<Account, Error> {
    let account = auth::offline_account(name)?;
    *state.account.lock().unwrap() = Some(account.clone());
    Ok(account)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::AccountKind;
    use crate::state::scratch_state;

    #[test]
    fn offline_sign_in_sticks_and_sign_out_clears_it() {
        let state = scratch_state();
        assert!(current(&state).unwrap().is_none());

        let account = offline(&state, "Searge").unwrap();
        assert_eq!(account.name, "Searge");
        assert_eq!(account.kind, AccountKind::Offline);
        assert_eq!(current(&state).unwrap().unwrap().uuid, account.uuid);

        logout(&state).unwrap();
        assert!(current(&state).unwrap().is_none());
    }

    #[test]
    fn a_bad_name_does_not_sign_anyone_in() {
        let state = scratch_state();
        assert!(offline(&state, "not a name").is_err());
        assert!(current(&state).unwrap().is_none());
    }

    #[test]
    fn login_returns_something_to_show_the_player_at_once() {
        let state = scratch_state();
        let code = login(&state).unwrap();
        assert!(!code.user_code.is_empty());
        assert!(code.verification_uri.starts_with("https://"));
        assert!(code.interval_s >= 1);
    }
}
