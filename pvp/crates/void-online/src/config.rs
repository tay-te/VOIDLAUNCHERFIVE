//! Where the backend lives, how to prove who the player is, and the identity itself.

use std::fmt;

/// The Mojang session server the join-mode handshake talks to.
pub const DEFAULT_MOJANG_SESSION_BASE: &str = "https://sessionserver.mojang.com";

/// How the launcher proves the player's Minecraft identity to `auth-minecraft`.
///
/// See `docs/online-contract.md`, Part 2, *Auth*. [`AuthMode::Join`] is what ships: the
/// Minecraft access token only ever goes to Mojang. [`AuthMode::Token`] hands the token to
/// our own edge function instead and exists as an operational fallback.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum AuthMode {
    /// The vanilla-server handshake: challenge → Mojang `session/minecraft/join` → the
    /// function asks Mojang `hasJoined`. The token never reaches VOID's servers.
    #[default]
    Join,
    /// Send the Minecraft access token to `auth-minecraft`, which checks it against the
    /// Minecraft profile API. A full-power credential leaves the machine, so only use it
    /// when the join handshake is unavailable.
    Token,
}

/// Where VOID's Supabase project is and how to sign in to it.
///
/// Build it with [`OnlineConfig::from_env`] (what the launcher does) or
/// [`OnlineConfig::new`]. Neither value is secret: the anon key is public by design and every
/// table is behind row-level security.
///
/// The two `#[doc(hidden)]` fields exist so tests can point the Mojang and Realtime legs at
/// local fakes. Struct literals therefore need `..OnlineConfig::default()`.
#[derive(Clone, PartialEq, Eq)]
pub struct OnlineConfig {
    /// The project URL, e.g. `https://abcd.supabase.co`, without a trailing slash.
    pub url: String,
    /// The project's anon (publishable) key, sent as `apikey` on every request.
    pub anon_key: String,
    /// How to prove the Minecraft identity.
    pub auth_mode: AuthMode,
    /// Base URL of the Mojang session server. Tests point it at a mock.
    #[doc(hidden)]
    pub mojang_session_base: String,
    /// Full Realtime websocket URL, overriding the one derived from `url`. Tests only.
    #[doc(hidden)]
    pub realtime_url: Option<String>,
}

impl Default for OnlineConfig {
    fn default() -> Self {
        OnlineConfig {
            url: String::new(),
            anon_key: String::new(),
            auth_mode: AuthMode::Join,
            mojang_session_base: DEFAULT_MOJANG_SESSION_BASE.to_string(),
            realtime_url: None,
        }
    }
}

impl fmt::Debug for OnlineConfig {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        // The anon key is public, but a habit of never printing keys is cheaper than
        // remembering which ones are safe.
        f.debug_struct("OnlineConfig")
            .field("url", &self.url)
            .field("anon_key", &"<redacted>")
            .field("auth_mode", &self.auth_mode)
            .finish_non_exhaustive()
    }
}

impl OnlineConfig {
    /// A config for `url` and `anon_key`, signing in with [`AuthMode::Join`].
    ///
    /// A trailing `/` on `url` is dropped so paths can be appended blindly.
    pub fn new(url: impl Into<String>, anon_key: impl Into<String>) -> Self {
        OnlineConfig {
            url: url.into().trim().trim_end_matches('/').to_string(),
            anon_key: anon_key.into().trim().to_string(),
            ..OnlineConfig::default()
        }
    }

    /// The config this build was made with, or `None` when it has none.
    ///
    /// `VOID_SUPABASE_URL` and `VOID_SUPABASE_ANON_KEY` are read **at build time** with
    /// `option_env!`, so a release launcher carries its project with it. When **both** are
    /// also set in the runtime environment, the runtime pair wins: that is how a developer
    /// points a built launcher at a local `supabase start`. One runtime variable on its own is
    /// ignored rather than mixed with the other's build-time value, because a URL from one
    /// project and a key from another fails in confusing ways.
    ///
    /// `None` (disabled mode) when neither pair is complete and non-empty.
    pub fn from_env() -> Option<OnlineConfig> {
        resolve(
            (option_env!("VOID_SUPABASE_URL"), option_env!("VOID_SUPABASE_ANON_KEY")),
            (std::env::var("VOID_SUPABASE_URL").ok(), std::env::var("VOID_SUPABASE_ANON_KEY").ok()),
        )
    }

    /// The Realtime websocket URL: `https` → `wss`, `http` → `ws`, then
    /// `/realtime/v1/websocket?apikey=<anon>&vsn=1.0.0`.
    pub(crate) fn realtime_websocket_url(&self) -> String {
        if let Some(url) = &self.realtime_url {
            return url.clone();
        }
        let base = if let Some(rest) = self.url.strip_prefix("https://") {
            format!("wss://{rest}")
        } else if let Some(rest) = self.url.strip_prefix("http://") {
            format!("ws://{rest}")
        } else {
            self.url.clone()
        };
        format!("{base}/realtime/v1/websocket?apikey={}&vsn=1.0.0", self.anon_key)
    }
}

/// [`OnlineConfig::from_env`] with the environment passed in, so it can be tested.
pub(crate) fn resolve(
    build: (Option<&str>, Option<&str>),
    runtime: (Option<String>, Option<String>),
) -> Option<OnlineConfig> {
    fn pair(url: Option<&str>, key: Option<&str>) -> Option<OnlineConfig> {
        let url = url.map(str::trim).filter(|s| !s.is_empty())?;
        let key = key.map(str::trim).filter(|s| !s.is_empty())?;
        Some(OnlineConfig::new(url, key))
    }
    pair(runtime.0.as_deref(), runtime.1.as_deref()).or_else(|| pair(build.0, build.1))
}

/// A signed-in Minecraft account, as `void-core`'s Microsoft sign-in produced it.
///
/// Kept in memory for the whole session: when a Supabase refresh fails the crate re-runs the
/// sign-in exchange with it. Its `Debug` never prints the token.
#[derive(Clone, PartialEq, Eq)]
pub struct McIdentity {
    /// The Minecraft services access token. Goes to Mojang only (in [`AuthMode::Join`]).
    pub access_token: String,
    /// The profile UUID, dashed or undashed.
    pub uuid: String,
    /// The profile name.
    pub name: String,
}

impl fmt::Debug for McIdentity {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("McIdentity")
            .field("access_token", &"<redacted>")
            .field("uuid", &self.uuid)
            .field("name", &self.name)
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn runtime_pair_overrides_build_pair_only_when_complete() {
        let build = (Some("https://built.supabase.co"), Some("built-key"));
        let c = resolve(build, (None, None)).unwrap();
        assert_eq!(c.url, "https://built.supabase.co");

        let c = resolve(build, (Some("http://127.0.0.1:54321/".into()), Some("dev".into()))).unwrap();
        assert_eq!(c.url, "http://127.0.0.1:54321");
        assert_eq!(c.anon_key, "dev");

        // One runtime variable alone never mixes with the build-time other half.
        let c = resolve(build, (Some("http://127.0.0.1:54321".into()), None)).unwrap();
        assert_eq!(c.url, "https://built.supabase.co");
        let c = resolve(build, (Some("http://x".into()), Some("  ".into()))).unwrap();
        assert_eq!(c.anon_key, "built-key");
    }

    #[test]
    fn missing_or_empty_means_disabled() {
        assert!(resolve((None, None), (None, None)).is_none());
        assert!(resolve((Some("https://x"), Some("")), (None, None)).is_none());
        assert!(resolve((Some(""), Some("k")), (Some("".into()), Some("k".into()))).is_none());
    }

    #[test]
    fn realtime_url_follows_the_scheme() {
        let c = OnlineConfig::new("https://abcd.supabase.co/", "anon");
        assert_eq!(
            c.realtime_websocket_url(),
            "wss://abcd.supabase.co/realtime/v1/websocket?apikey=anon&vsn=1.0.0"
        );
        let c = OnlineConfig::new("http://127.0.0.1:54321", "k");
        assert_eq!(c.realtime_websocket_url(), "ws://127.0.0.1:54321/realtime/v1/websocket?apikey=k&vsn=1.0.0");
    }

    #[test]
    fn debug_never_prints_secrets() {
        let id = McIdentity { access_token: "eyJsecret".into(), uuid: "u".into(), name: "n".into() };
        assert!(!format!("{id:?}").contains("eyJsecret"));
        let c = OnlineConfig::new("https://x", "anon-key-value");
        assert!(!format!("{c:?}").contains("anon-key-value"));
    }
}
