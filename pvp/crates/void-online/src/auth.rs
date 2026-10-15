//! The shapes of the sign-in exchange, and Mojang's refusals turned into sentences.
//!
//! The flow itself lives on `Inner` in `online.rs` (it needs the request helper); this module
//! holds what it reads and writes. See `docs/online-contract.md`, Part 2, *Auth*.

use std::fmt;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::Deserialize;
use serde_json::Value;

use crate::types::Player;

/// `auth-minecraft {"mode":"challenge"}` → the server id to hand Mojang.
#[derive(Debug, Deserialize)]
pub(crate) struct Challenge {
    pub server_id: String,
    /// Echoed back verbatim in the `join` step; kept as JSON so its exact form survives.
    pub ts: Value,
    pub nonce: String,
}

/// What `auth-minecraft` answers once it believes the identity: a one-time login to redeem.
#[derive(Deserialize)]
pub(crate) struct Grant {
    pub player: Player,
    pub token_hash: String,
    pub verification_type: String,
}

impl fmt::Debug for Grant {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Grant")
            .field("player", &self.player)
            .field("token_hash", &"<redacted>")
            .field("verification_type", &self.verification_type)
            .finish()
    }
}

/// A Supabase session, from `/auth/v1/verify` or a refresh. Memory only, never persisted.
#[derive(Clone, Deserialize)]
pub(crate) struct Session {
    pub access_token: String,
    pub refresh_token: String,
    #[serde(default)]
    pub expires_in: Option<u64>,
    /// Unix seconds. Filled from `expires_in` by [`Session::normalised`] when GoTrue omits it.
    #[serde(default)]
    pub expires_at: Option<u64>,
}

impl fmt::Debug for Session {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Session")
            .field("access_token", &"<redacted>")
            .field("refresh_token", &"<redacted>")
            .field("expires_at", &self.expires_at)
            .finish()
    }
}

impl Session {
    /// Pin `expires_at`, defaulting to an hour (GoTrue's default JWT lifetime) if neither field
    /// came back.
    pub(crate) fn normalised(mut self) -> Session {
        if self.expires_at.is_none() {
            self.expires_at = Some(now_unix() + self.expires_in.unwrap_or(3600));
        }
        self
    }

    pub(crate) fn expires_at(&self) -> u64 {
        self.expires_at.unwrap_or(0)
    }
}

pub(crate) fn now_unix() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

/// Mojang's `session/minecraft/join` failed with `status` and `body`: say why in words.
///
/// Mojang answers `403` with a Java exception name in `error`: `InsufficientPrivilegesException`
/// (multiplayer turned off in the Xbox/Microsoft family settings, or a child account),
/// `UserBannedException` (multiplayer-banned), `ForbiddenOperationException` (the access token
/// is no longer valid). An unknown 403 is treated as the privileges case, which is by far the
/// most common.
pub(crate) fn mojang_message(status: u16, body: &[u8]) -> String {
    let error = serde_json::from_slice::<Value>(body)
        .ok()
        .and_then(|v| v.get("error").and_then(Value::as_str).map(str::to_string))
        .unwrap_or_default();
    match (status, error.as_str()) {
        (_, "UserBannedException") => {
            "This Minecraft account is banned from online play, so VOID's online features can't verify it.".into()
        }
        (401, _) | (_, "ForbiddenOperationException") => {
            "Your Minecraft session has expired. Sign in with Microsoft again.".into()
        }
        (403, _) | (_, "InsufficientPrivilegesException") => "Minecraft says this account can't play online: multiplayer is \
             disabled or restricted in its Microsoft or Xbox privacy settings. Turn multiplayer on and sign in again."
            .into(),
        (429, _) => "Mojang is limiting sign-ins right now. Wait a minute and try again.".into(),
        (500..=599, _) => "Mojang's session servers are having trouble. Try again in a moment.".into(),
        _ => format!("Mojang couldn't verify your Minecraft account (HTTP {status}). Try again in a moment."),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn mojang_refusals_read_as_sentences() {
        let m = mojang_message(403, br#"{"error":"InsufficientPrivilegesException","errorMessage":"Invalid"}"#);
        assert!(m.contains("multiplayer"), "{m}");
        assert!(mojang_message(403, b"").contains("multiplayer"));
        assert!(mojang_message(403, br#"{"error":"ForbiddenOperationException"}"#).contains("expired"));
        assert!(mojang_message(403, br#"{"error":"UserBannedException"}"#).contains("banned"));
        assert!(mojang_message(503, b"").contains("trouble"));
    }

    #[test]
    fn sessions_pin_their_expiry_and_hide_tokens() {
        let s: Session =
            serde_json::from_str(r#"{"access_token":"a.b.c","refresh_token":"r","expires_in":3600}"#).unwrap();
        let s = s.normalised();
        assert!(s.expires_at() >= now_unix() + 3590);
        let dbg = format!("{s:?}");
        assert!(!dbg.contains("a.b.c") && !dbg.contains("\"r\""));
    }
}
