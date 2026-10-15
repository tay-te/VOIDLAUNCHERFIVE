//! The crate's error type and the two backend error shapes it is parsed from.
//!
//! Every [`Error`] displays as a sentence a player can read, because the launcher shows them
//! as-is. The machine-readable part ([`Error::code`], [`Error::status`]) is there for the code
//! that wants to branch, and `Debug` / [`std::error::Error::source`] carry the detail for logs.

use serde_json::Value;

/// Anything that can go wrong in `void-online`.
#[derive(Debug, thiserror::Error)]
#[non_exhaustive]
pub enum Error {
    /// This build has no Supabase project configured. Nothing touched the network.
    #[error("Online features aren't available in this build.")]
    Disabled,

    /// The call needs a signed-in player and there is none. Nothing touched the network.
    #[error("Sign in with a Microsoft account to use online features.")]
    SignedOut,

    /// The backend refused the request. `code` is the machine code (`not_owned`,
    /// `already_friends`, ...), `message` the backend's own player-readable sentence.
    #[error("{message}")]
    Api {
        /// The HTTP status.
        status: u16,
        /// The machine code, from `error` (edge functions) or `message` (PostgREST RPCs).
        code: String,
        /// The sentence to show the player.
        message: String,
    },

    /// Mojang refused the join handshake. The string is already a sentence.
    #[error("{0}")]
    Mojang(String),

    /// The request never got an answer: DNS, TLS, a dropped connection, a timeout.
    #[error("Couldn't reach VOID's servers. Check your connection and try again.")]
    Http(#[from] reqwest::Error),

    /// The answer did not parse.
    #[error("VOID's servers sent a reply this launcher doesn't understand. Try updating the launcher.")]
    Json(#[from] serde_json::Error),

    /// The live-updates socket failed. Only surfaced by the parts that set it up; a dropped
    /// socket on its own reconnects quietly.
    #[error("Live updates are unavailable right now: {0}")]
    Realtime(String),

    /// The caller passed something that cannot be right (a malformed UUID, item id or colour).
    /// The string is a sentence. Nothing touched the network.
    #[error("{0}")]
    Invalid(String),

    /// The backend answered successfully with something that breaks the contract (a checkout
    /// URL that is not `https`, a session for a different player). The detail is for logs.
    #[error("VOID's servers sent an unexpected reply. Try again in a moment.")]
    Unexpected(String),
}

impl Error {
    /// The backend's machine code, for [`Error::Api`].
    pub fn code(&self) -> Option<&str> {
        match self {
            Error::Api { code, .. } => Some(code),
            _ => None,
        }
    }

    /// The HTTP status, for [`Error::Api`] and for transport errors that carry one.
    pub fn status(&self) -> Option<u16> {
        match self {
            Error::Api { status, .. } => Some(*status),
            Error::Http(e) => e.status().map(|s| s.as_u16()),
            _ => None,
        }
    }
}

/// The fallback sentence when a failure body carries none we can use.
pub(crate) fn generic_message(status: u16) -> &'static str {
    match status {
        401 => "Your VOID session has expired. Sign in again.",
        403 => "VOID's servers refused that request.",
        404 => "VOID's servers couldn't find that.",
        429 => "Slow down a little and try again in a moment.",
        500..=599 => "VOID's servers are having trouble. Try again in a moment.",
        _ => "Something went wrong talking to VOID's servers. Try again in a moment.",
    }
}

/// Turn a failed response body into [`Error::Api`].
///
/// The shapes, in the order they are tried:
///
/// - **Edge functions**: `{"error":"<code>","message":"<sentence>"}`.
/// - **PostgREST, raised by our SQL**: `{"code":"PT403","message":"<code>","details":"<sentence>"}`.
///   `raise_api` puts the machine code in `message` and the sentence in `details`, and the
///   `PT<status>` errcode is what makes PostgREST answer with that status.
/// - **PostgREST, its own** (`PGRST202` unknown function, `42501` permission denied, ...):
///   `message` is an English developer sentence, not something for a player, so the code is
///   kept and the sentence becomes the generic one for the status.
/// - **GoTrue** (`/auth/v1/*`): `{"error_code":"..","msg":".."}` or the OAuth-style
///   `{"error":"invalid_grant","error_description":".."}`.
///
/// Anything else (an HTML gateway page, an empty body) maps to `http_<status>` with the
/// generic sentence.
pub(crate) fn api_error(status: u16, body: &[u8]) -> Error {
    let generic = || generic_message(status).to_string();
    let v: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return Error::Api { status, code: format!("http_{status}"), message: generic() },
    };
    let s = |k: &str| v.get(k).and_then(Value::as_str).map(str::to_string).filter(|s| !s.is_empty());

    // GoTrue's `{"error":"invalid_grant","error_description":..}` also has `error`, so it must
    // be told apart from the functions' shape by the `error_description` key.
    if let (Some(code), Some(_)) = (s("error"), s("error_description")) {
        return Error::Api { status, code, message: generic() };
    }
    if let Some(code) = s("error") {
        return Error::Api { status, code, message: s("message").unwrap_or_else(generic) };
    }
    if let Some(pg_code) = s("code") {
        if pg_code.starts_with("PT") {
            return Error::Api {
                status,
                code: s("message").unwrap_or(pg_code),
                message: s("details").unwrap_or_else(generic),
            };
        }
        return Error::Api { status, code: pg_code, message: generic() };
    }
    if let Some(code) = s("error_code") {
        return Error::Api { status, code, message: s("msg").unwrap_or_else(generic) };
    }
    Error::Api { status, code: format!("http_{status}"), message: generic() }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn api(e: Error) -> (u16, String, String) {
        match e {
            Error::Api { status, code, message } => (status, code, message),
            other => panic!("not Api: {other:?}"),
        }
    }

    #[test]
    fn edge_function_errors_keep_their_sentence() {
        let (s, c, m) = api(api_error(403, br#"{"error":"not_owned","message":"You don't own that cape."}"#));
        assert_eq!((s, c.as_str(), m.as_str()), (403, "not_owned", "You don't own that cape."));
    }

    #[test]
    fn postgrest_pt_errors_map_message_to_code_and_details_to_sentence() {
        let body = br#"{"code":"PT409","message":"no_server","details":"Join a server first.","hint":null}"#;
        let e = api_error(409, body);
        assert_eq!(e.code(), Some("no_server"));
        assert_eq!(e.status(), Some(409));
        assert_eq!(e.to_string(), "Join a server first.");
    }

    #[test]
    fn postgrest_own_errors_get_a_generic_sentence() {
        let body = br#"{"code":"PGRST202","message":"Could not find the function public.nope","details":null,"hint":"..."}"#;
        let (s, c, m) = api(api_error(404, body));
        assert_eq!((s, c.as_str()), (404, "PGRST202"));
        assert_eq!(m, generic_message(404));
    }

    #[test]
    fn gotrue_errors_parse() {
        let (_, c, m) = api(api_error(403, br#"{"code":403,"error_code":"otp_expired","msg":"Token has expired or is invalid"}"#));
        assert_eq!((c.as_str(), m.as_str()), ("otp_expired", "Token has expired or is invalid"));
        let (_, c, m) = api(api_error(400, br#"{"error":"invalid_grant","error_description":"Refresh Token Not Found"}"#));
        assert_eq!(c, "invalid_grant");
        assert_eq!(m, generic_message(400));
    }

    #[test]
    fn junk_bodies_still_give_a_sentence() {
        let (_, c, m) = api(api_error(502, b"<html>Bad Gateway</html>"));
        assert_eq!(c, "http_502");
        assert_eq!(m, generic_message(502));
        let (_, c, _) = api(api_error(500, b"{}"));
        assert_eq!(c, "http_500");
    }

    #[test]
    fn fixed_sentences() {
        assert_eq!(Error::Disabled.to_string(), "Online features aren't available in this build.");
        assert_eq!(Error::SignedOut.to_string(), "Sign in with a Microsoft account to use online features.");
    }
}
