//! Microsoft device-code OAuth → Xbox Live → XSTS → Minecraft services → profile.
//!
//! The device-code flow is the right one for a desktop launcher: no embedded browser, no
//! redirect URI to register, no loopback listener — the player opens a URL and types a
//! code. The refresh token goes in the OS keychain; if that is unavailable (a headless
//! box, a locked keychain, a Linux install with no keyring daemon) it falls back to a
//! file in the data directory, with a warning, rather than making sign-in impossible.
//!
//! **The Azure client id is configuration, never a constant** — see [`crate::Config`].
//!
//! [`Session::offline`] short-circuits the whole thing so the launcher can be exercised
//! end to end without an account; an offline session cannot join online-mode servers,
//! which is a server-side rule and nothing this crate can or should work around.

use std::time::{Duration, Instant};

use serde::{Deserialize, Serialize};

use crate::error::{Error, Result};
use crate::paths::Paths;

/// The consumers tenant: personal Microsoft accounts, which is what Minecraft uses.
const DEVICE_CODE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

/// The scopes Minecraft sign-in needs. `offline_access` is what yields a refresh token.
const SCOPE: &str = "XboxLive.signin offline_access";

/// Keychain service name.
const KEYRING_SERVICE: &str = "dev.void.pvp";
/// Keychain entry name.
const KEYRING_ACCOUNT: &str = "microsoft-refresh-token";

/// A signed-in (or offline) player, ready to be turned into JVM arguments.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Session {
    /// In-game name.
    pub username: String,
    /// Player UUID, without hyphens, as the game expects on the command line.
    pub uuid: String,
    /// Minecraft services access token, or `0` for an offline session.
    pub access_token: String,
    /// `msa` for a real account, `legacy` for offline.
    pub user_type: String,
    /// Xbox user id, when known.
    #[serde(default)]
    pub xuid: Option<String>,
}

impl Session {
    /// An offline session, for testing the launcher without an account.
    ///
    /// The UUID is derived the way the vanilla server does it for offline mode — an
    /// MD5-based v3 UUID over `OfflinePlayer:<name>` — so the same name keeps the same
    /// identity across launches and matches singleplayer worlds.
    pub fn offline(username: impl Into<String>) -> Self {
        let username = username.into();
        Self {
            uuid: offline_uuid(&username),
            username,
            access_token: "0".to_string(),
            user_type: "legacy".to_string(),
            xuid: None,
        }
    }

    /// Whether this session has no real Minecraft token behind it.
    pub fn is_offline(&self) -> bool {
        self.user_type == "legacy"
    }
}

/// `UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`: MD5, version 3, RFC 4122 variant.
pub fn offline_uuid(username: &str) -> String {
    use md5::{Digest, Md5};
    let mut digest = Md5::digest(format!("OfflinePlayer:{username}").as_bytes());
    digest[6] = (digest[6] & 0x0f) | 0x30; // version 3
    digest[8] = (digest[8] & 0x3f) | 0x80; // RFC 4122 variant
    hex::encode(digest)
}

/// What the player has to do to finish signing in.
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct DeviceCode {
    /// The opaque code this launcher polls with.
    pub device_code: String,
    /// The short code the player types.
    pub user_code: String,
    /// The URL the player opens.
    pub verification_uri: String,
    /// Seconds before the code expires.
    pub expires_in: u64,
    /// Seconds to wait between polls.
    #[serde(default = "default_interval")]
    pub interval: u64,
    /// Microsoft's own human-readable instruction.
    #[serde(default)]
    pub message: Option<String>,
}

fn default_interval() -> u64 {
    5
}

#[derive(Debug, Deserialize)]
struct MsTokenResponse {
    access_token: String,
    #[serde(default)]
    refresh_token: Option<String>,
}

#[derive(Debug, Deserialize)]
struct MsErrorResponse {
    error: String,
    #[serde(default)]
    error_description: Option<String>,
}

#[derive(Debug, Deserialize)]
struct XboxResponse {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    display_claims: XboxDisplayClaims,
}

#[derive(Debug, Deserialize)]
struct XboxDisplayClaims {
    xui: Vec<XboxUserInfo>,
}

#[derive(Debug, Deserialize)]
struct XboxUserInfo {
    uhs: String,
    #[serde(default)]
    xid: Option<String>,
}

#[derive(Debug, Deserialize)]
struct McTokenResponse {
    access_token: String,
}

#[derive(Debug, Deserialize)]
struct McProfile {
    id: String,
    name: String,
}

/// The whole sign-in chain, hanging off one HTTP client.
#[derive(Debug, Clone)]
pub struct Auth {
    client: reqwest::Client,
    client_id: String,
}

impl Auth {
    /// Builds an authenticator for an Azure application.
    pub fn new(client: reqwest::Client, client_id: impl Into<String>) -> Self {
        Self { client, client_id: client_id.into() }
    }

    /// Step 1: asks Microsoft for a device code for the player to enter.
    pub async fn start_device_code(&self) -> Result<DeviceCode> {
        let resp = self
            .client
            .post(DEVICE_CODE_URL)
            .form(&[("client_id", self.client_id.as_str()), ("scope", SCOPE)])
            .send()
            .await?;
        let status = resp.status();
        let text = resp.text().await?;
        if !status.is_success() {
            return Err(Error::Auth(format!("device code request failed ({status}): {text}")));
        }
        serde_json::from_str(&text).map_err(|e| Error::json("device code response", e))
    }

    /// Step 2: polls until the player finishes in the browser, or the code expires.
    pub async fn poll_for_token(&self, code: &DeviceCode) -> Result<MicrosoftTokens> {
        let deadline = Instant::now() + Duration::from_secs(code.expires_in);
        let mut interval = Duration::from_secs(code.interval.max(1));

        loop {
            if Instant::now() >= deadline {
                return Err(Error::Auth("the device code expired before sign-in finished".into()));
            }
            tokio::time::sleep(interval).await;

            let resp = self
                .client
                .post(TOKEN_URL)
                .form(&[
                    ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                    ("client_id", self.client_id.as_str()),
                    ("device_code", code.device_code.as_str()),
                ])
                .send()
                .await?;
            let status = resp.status();
            let text = resp.text().await?;

            if status.is_success() {
                let tokens: MsTokenResponse = serde_json::from_str(&text)
                    .map_err(|e| Error::json("token response", e))?;
                return Ok(MicrosoftTokens {
                    access_token: tokens.access_token,
                    refresh_token: tokens.refresh_token,
                });
            }

            let err: MsErrorResponse = serde_json::from_str(&text)
                .map_err(|e| Error::json("token error response", e))?;
            match err.error.as_str() {
                // Expected while the player is still typing.
                "authorization_pending" => continue,
                "slow_down" => interval += Duration::from_secs(5),
                "expired_token" | "authorization_declined" | "bad_verification_code" => {
                    return Err(Error::Auth(
                        err.error_description.unwrap_or(err.error).to_string(),
                    ))
                }
                other => {
                    return Err(Error::Auth(format!(
                        "{other}: {}",
                        err.error_description.unwrap_or_default()
                    )))
                }
            }
        }
    }

    /// Exchanges a stored refresh token for a fresh access token.
    pub async fn refresh(&self, refresh_token: &str) -> Result<MicrosoftTokens> {
        let resp = self
            .client
            .post(TOKEN_URL)
            .form(&[
                ("grant_type", "refresh_token"),
                ("client_id", self.client_id.as_str()),
                ("refresh_token", refresh_token),
                ("scope", SCOPE),
            ])
            .send()
            .await?;
        let status = resp.status();
        let text = resp.text().await?;
        if !status.is_success() {
            return Err(Error::Auth(format!("refresh failed ({status}): {text}")));
        }
        let tokens: MsTokenResponse =
            serde_json::from_str(&text).map_err(|e| Error::json("refresh response", e))?;
        Ok(MicrosoftTokens {
            access_token: tokens.access_token,
            // Microsoft rotates refresh tokens; keep the old one if none came back.
            refresh_token: tokens.refresh_token.or_else(|| Some(refresh_token.to_string())),
        })
    }

    /// Steps 3-6: Xbox Live, XSTS, Minecraft services, then the profile.
    pub async fn minecraft_session(&self, ms_access_token: &str) -> Result<Session> {
        // Xbox Live user token.
        let xbl: XboxResponse = self
            .post_json(
                XBL_URL,
                &serde_json::json!({
                    "Properties": {
                        "AuthMethod": "RPS",
                        "SiteName": "user.auth.xboxlive.com",
                        "RpsTicket": format!("d={ms_access_token}"),
                    },
                    "RelyingParty": "http://auth.xboxlive.com",
                    "TokenType": "JWT",
                }),
                None,
            )
            .await?;

        // XSTS token for the Minecraft relying party.
        let xsts: XboxResponse = self
            .post_json(
                XSTS_URL,
                &serde_json::json!({
                    "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl.token] },
                    "RelyingParty": "rp://api.minecraftservices.com/",
                    "TokenType": "JWT",
                }),
                None,
            )
            .await?;

        let user_info = xsts
            .display_claims
            .xui
            .first()
            .ok_or_else(|| Error::Auth("XSTS returned no user hash".into()))?;

        // Minecraft services token.
        let mc: McTokenResponse = self
            .post_json(
                MC_LOGIN_URL,
                &serde_json::json!({
                    "identityToken": format!("XBL3.0 x={};{}", user_info.uhs, xsts.token),
                }),
                None,
            )
            .await?;

        // Profile.
        let resp = self
            .client
            .get(MC_PROFILE_URL)
            .bearer_auth(&mc.access_token)
            .send()
            .await?;
        let status = resp.status();
        let text = resp.text().await?;
        if status == reqwest::StatusCode::NOT_FOUND {
            return Err(Error::Auth(
                "this Microsoft account does not own Minecraft: Java Edition".into(),
            ));
        }
        if !status.is_success() {
            return Err(Error::Auth(format!("profile lookup failed ({status}): {text}")));
        }
        let profile: McProfile =
            serde_json::from_str(&text).map_err(|e| Error::json("minecraft profile", e))?;

        Ok(Session {
            username: profile.name,
            uuid: profile.id,
            access_token: mc.access_token,
            user_type: "msa".to_string(),
            xuid: user_info.xid.clone(),
        })
    }

    async fn post_json<T: serde::de::DeserializeOwned>(
        &self,
        url: &str,
        body: &serde_json::Value,
        bearer: Option<&str>,
    ) -> Result<T> {
        let mut req = self.client.post(url).json(body).header("Accept", "application/json");
        if let Some(token) = bearer {
            req = req.bearer_auth(token);
        }
        let resp = req.send().await?;
        let status = resp.status();
        let text = resp.text().await?;
        if !status.is_success() {
            // Xbox reports the useful failures in an `XErr` code.
            let hint = serde_json::from_str::<serde_json::Value>(&text)
                .ok()
                .and_then(|v| v.get("XErr").and_then(|x| x.as_i64()))
                .and_then(xerr_hint);
            return Err(Error::Auth(match hint {
                Some(h) => format!("{url} failed ({status}): {h}"),
                None => format!("{url} failed ({status}): {text}"),
            }));
        }
        serde_json::from_str(&text).map_err(|e| Error::json(url.to_string(), e))
    }
}

fn xerr_hint(code: i64) -> Option<&'static str> {
    Some(match code {
        2148916233 => "this Microsoft account has no Xbox profile; create one at xbox.com first",
        2148916235 => "Xbox Live is not available in this account's country",
        2148916236 | 2148916237 => "this account needs adult verification",
        2148916238 => "this is a child account and must be added to a Family before signing in",
        _ => return None,
    })
}

/// A Microsoft access token plus the refresh token that renews it.
#[derive(Debug, Clone)]
pub struct MicrosoftTokens {
    /// Short-lived access token, exchanged for an Xbox Live token.
    pub access_token: String,
    /// Long-lived refresh token, kept in the keychain.
    pub refresh_token: Option<String>,
}

/// Where the refresh token lives: the OS keychain, or a file when that fails.
#[derive(Debug, Clone)]
pub struct TokenStore {
    fallback: std::path::PathBuf,
}

#[derive(Debug, Serialize, Deserialize)]
struct StoredCredentials {
    refresh_token: String,
}

impl TokenStore {
    /// A token store for an installation.
    pub fn new(paths: &Paths) -> Self {
        Self { fallback: paths.credentials_file() }
    }

    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    fn entry() -> Option<keyring::Entry> {
        match keyring::Entry::new(KEYRING_SERVICE, KEYRING_ACCOUNT) {
            Ok(entry) => Some(entry),
            Err(e) => {
                tracing::warn!(error = %e, "no OS keychain available");
                None
            }
        }
    }

    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    fn entry() -> Option<keyring::Entry> {
        None
    }

    /// Stores a refresh token, preferring the OS keychain.
    pub fn save(&self, refresh_token: &str) -> Result<()> {
        if let Some(entry) = Self::entry() {
            match entry.set_password(refresh_token) {
                Ok(()) => {
                    // Belt and braces: drop any stale plaintext copy.
                    let _ = std::fs::remove_file(&self.fallback);
                    return Ok(());
                }
                Err(e) => tracing::warn!(
                    error = %e,
                    path = %self.fallback.display(),
                    "could not write to the OS keychain; falling back to a file"
                ),
            }
        }
        self.save_to_file(refresh_token)
    }

    fn save_to_file(&self, refresh_token: &str) -> Result<()> {
        if let Some(dir) = self.fallback.parent() {
            std::fs::create_dir_all(dir).map_err(|e| Error::io(dir, e))?;
        }
        let text = serde_json::to_string(&StoredCredentials {
            refresh_token: refresh_token.to_string(),
        })
        .map_err(|e| Error::json("credentials", e))?;
        std::fs::write(&self.fallback, text).map_err(|e| Error::io(&self.fallback, e))?;
        restrict_permissions(&self.fallback);
        tracing::warn!(
            path = %self.fallback.display(),
            "refresh token stored in a plain file because the OS keychain was unavailable"
        );
        Ok(())
    }

    /// Reads the stored refresh token, if there is one.
    pub fn load(&self) -> Result<Option<String>> {
        if let Some(entry) = Self::entry() {
            match entry.get_password() {
                Ok(token) => return Ok(Some(token)),
                Err(keyring::Error::NoEntry) => {}
                Err(e) => tracing::warn!(error = %e, "could not read the OS keychain"),
            }
        }
        if !self.fallback.exists() {
            return Ok(None);
        }
        let text =
            std::fs::read_to_string(&self.fallback).map_err(|e| Error::io(&self.fallback, e))?;
        let stored: StoredCredentials = serde_json::from_str(&text)
            .map_err(|e| Error::json(self.fallback.display().to_string(), e))?;
        Ok(Some(stored.refresh_token))
    }

    /// Forgets the stored refresh token, in both places.
    pub fn clear(&self) -> Result<()> {
        if let Some(entry) = Self::entry() {
            match entry.delete_credential() {
                Ok(()) | Err(keyring::Error::NoEntry) => {}
                Err(e) => tracing::warn!(error = %e, "could not clear the OS keychain"),
            }
        }
        if self.fallback.exists() {
            std::fs::remove_file(&self.fallback).map_err(|e| Error::io(&self.fallback, e))?;
        }
        Ok(())
    }
}

#[cfg(unix)]
fn restrict_permissions(path: &std::path::Path) {
    use std::os::unix::fs::PermissionsExt;
    if let Ok(meta) = std::fs::metadata(path) {
        let mut perms = meta.permissions();
        perms.set_mode(0o600);
        let _ = std::fs::set_permissions(path, perms);
    }
}

#[cfg(not(unix))]
fn restrict_permissions(_path: &std::path::Path) {}

/// Caches the last signed-in profile so `whoami` needs no network.
pub fn save_profile(paths: &Paths, session: &Session) -> Result<()> {
    let file = paths.profile_file();
    if let Some(dir) = file.parent() {
        std::fs::create_dir_all(dir).map_err(|e| Error::io(dir, e))?;
    }
    // The access token is deliberately not written: it lives for 24 hours and is cheap
    // to re-mint from the refresh token.
    let public = Session { access_token: String::new(), ..session.clone() };
    let text = serde_json::to_string_pretty(&public).map_err(|e| Error::json("profile", e))?;
    std::fs::write(&file, text).map_err(|e| Error::io(&file, e))
}

/// Reads the cached profile, if there is one.
pub fn load_profile(paths: &Paths) -> Result<Option<Session>> {
    let file = paths.profile_file();
    if !file.exists() {
        return Ok(None);
    }
    let text = std::fs::read_to_string(&file).map_err(|e| Error::io(&file, e))?;
    serde_json::from_str(&text)
        .map(Some)
        .map_err(|e| Error::json(file.display().to_string(), e))
}

/// Forgets the cached profile.
pub fn clear_profile(paths: &Paths) -> Result<()> {
    let file = paths.profile_file();
    if file.exists() {
        std::fs::remove_file(&file).map_err(|e| Error::io(&file, e))?;
    }
    Ok(())
}

/// Signs in from the stored refresh token, without any player interaction.
///
/// Returns `Ok(None)` when there is no stored token, which is the "not signed in" case
/// rather than an error.
pub async fn sign_in_silently(
    client: &reqwest::Client,
    paths: &Paths,
    client_id: &str,
) -> Result<Option<Session>> {
    let store = TokenStore::new(paths);
    let Some(refresh_token) = store.load()? else {
        return Ok(None);
    };
    let auth = Auth::new(client.clone(), client_id);
    let tokens = auth.refresh(&refresh_token).await?;
    if let Some(new_refresh) = &tokens.refresh_token {
        store.save(new_refresh)?;
    }
    let session = auth.minecraft_session(&tokens.access_token).await?;
    save_profile(paths, &session)?;
    Ok(Some(session))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn offline_uuids_are_stable_v3_uuids() {
        let uuid = offline_uuid("Notch");
        assert_eq!(uuid.len(), 32);
        assert_eq!(uuid, offline_uuid("Notch"));
        assert_ne!(uuid, offline_uuid("jeb_"));

        // version nibble is 3, variant is 10xx.
        let bytes = hex::decode(&uuid).unwrap();
        assert_eq!(bytes[6] >> 4, 3);
        assert_eq!(bytes[8] >> 6, 0b10);
    }

    #[test]
    fn an_offline_session_is_marked_legacy_and_carries_no_token() {
        let s = Session::offline("Tester");
        assert!(s.is_offline());
        assert_eq!(s.user_type, "legacy");
        assert_eq!(s.access_token, "0");
        assert_eq!(s.username, "Tester");
    }

    #[test]
    fn xbox_error_codes_become_actionable_messages() {
        assert!(xerr_hint(2148916233).unwrap().contains("Xbox profile"));
        assert!(xerr_hint(2148916238).unwrap().contains("child account"));
        assert_eq!(xerr_hint(1), None);
    }

    #[test]
    fn the_profile_cache_never_holds_an_access_token() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        let mut session = Session::offline("Tester");
        session.access_token = "secret".into();
        save_profile(&paths, &session).unwrap();

        let text = std::fs::read_to_string(paths.profile_file()).unwrap();
        assert!(!text.contains("secret"));
        assert_eq!(load_profile(&paths).unwrap().unwrap().username, "Tester");

        clear_profile(&paths).unwrap();
        assert_eq!(load_profile(&paths).unwrap(), None);
    }

    #[test]
    fn the_token_store_falls_back_to_a_file() {
        let dir = tempfile::tempdir().unwrap();
        let store = TokenStore { fallback: dir.path().join("credentials.json") };
        store.save_to_file("refresh-me").unwrap();
        assert_eq!(store.load().unwrap().as_deref(), Some("refresh-me"));
        store.clear().unwrap();
        assert!(!dir.path().join("credentials.json").exists());
    }
}
