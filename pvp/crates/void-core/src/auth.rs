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
/// The single keychain entry used before multiple accounts; migrated on first read.
const LEGACY_KEYRING_ACCOUNT: &str = "microsoft-refresh-token";

/// The keychain entry for one account's refresh token.
fn keyring_account(uuid: &str) -> String {
    format!("refresh-token:{uuid}")
}

/// Windows' Credential Manager caps a secret at 2,560 bytes (1,280 UTF-16 units), and a
/// consumer Microsoft refresh token can come close. Tokens longer than this are split
/// across `<entry>#0`, `<entry>#1`, ... with the base entry holding `chunks:<n>`.
const KEYRING_CHUNK: usize = 1000;

/// Seconds since the Unix epoch.
fn now_secs() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

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
    /// When `access_token` stops working, as Unix seconds. Minecraft services tokens
    /// last 24 hours; `None` means unknown, which is treated as already expired.
    #[serde(default)]
    pub expires_at: Option<u64>,
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
            expires_at: None,
        }
    }

    /// Whether this session has no real Minecraft token behind it.
    pub fn is_offline(&self) -> bool {
        self.user_type == "legacy"
    }

    /// Whether a Microsoft session's access token expires within `secs` (or already
    /// has, or its expiry is unknown). Always false for an offline session, which has no
    /// token to expire.
    pub fn expires_within(&self, secs: u64) -> bool {
        if self.is_offline() {
            return false;
        }
        match self.expires_at {
            Some(at) => now_secs().saturating_add(secs) >= at,
            None => true,
        }
    }
}

/// Which hop of the Microsoft → Minecraft chain is about to run, for progress messages.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Stage {
    /// Xbox Live user token, then XSTS.
    XboxLive,
    /// Minecraft services login and the profile lookup.
    Minecraft,
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
    /// Seconds; 86,400 in practice.
    #[serde(default)]
    expires_in: Option<u64>,
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
        self.minecraft_session_with(ms_access_token, |_| {}).await
    }

    /// [`Auth::minecraft_session`], announcing each [`Stage`] just *before* it runs, so
    /// a status line never claims a hop that has not started (or already finished).
    pub async fn minecraft_session_with(
        &self,
        ms_access_token: &str,
        on_stage: impl Fn(Stage),
    ) -> Result<Session> {
        on_stage(Stage::XboxLive);
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

        on_stage(Stage::Minecraft);
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
            expires_at: Some(now_secs() + mc.expires_in.unwrap_or(86_400)),
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

/// Where refresh tokens live: the OS keychain, one entry per account, or a file in the
/// data directory when the keychain is unavailable.
///
/// The fallback file is a last resort and is treated as one: it is written only after
/// the keychain refused, it is restricted to the current user (mode 0600 on Unix, an ACL
/// granting only the current user on Windows), and it is deleted as soon as the keychain
/// accepts a write again.
#[derive(Debug, Clone)]
pub struct TokenStore {
    fallback: std::path::PathBuf,
    /// Tests turn the keychain off so they never touch the developer's real one.
    use_keyring: bool,
}

/// The fallback file: every account's refresh token by UUID. `refresh_token` is the
/// single-account format, read once and migrated.
#[derive(Debug, Default, Serialize, Deserialize)]
struct StoredCredentials {
    #[serde(default)]
    tokens: std::collections::BTreeMap<String, String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    refresh_token: Option<String>,
}

impl TokenStore {
    /// A token store for an installation.
    pub fn new(paths: &Paths) -> Self {
        Self { fallback: paths.credentials_file(), use_keyring: true }
    }

    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    fn entry(&self, name: &str) -> Option<keyring::Entry> {
        if !self.use_keyring {
            return None;
        }
        match keyring::Entry::new(KEYRING_SERVICE, name) {
            Ok(entry) => Some(entry),
            Err(e) => {
                tracing::warn!(error = %e, "no OS keychain available");
                None
            }
        }
    }

    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    fn entry(&self, _name: &str) -> Option<keyring::Entry> {
        None
    }

    /// Writes a secret to the keychain, chunking it past [`KEYRING_CHUNK`].
    fn keyring_set(&self, name: &str, secret: &str) -> std::result::Result<(), String> {
        let base = self.entry(name).ok_or("no keychain")?;
        if secret.len() <= KEYRING_CHUNK {
            self.keyring_clear_chunks(name);
            return base.set_password(secret).map_err(|e| e.to_string());
        }
        let chunks: Vec<&str> = secret
            .as_bytes()
            .chunks(KEYRING_CHUNK)
            .map(|c| std::str::from_utf8(c).unwrap_or_default())
            .collect();
        if chunks.iter().any(|c| c.is_empty()) {
            return Err("token is not ASCII".into());
        }
        for (i, chunk) in chunks.iter().enumerate() {
            let entry = self.entry(&format!("{name}#{i}")).ok_or("no keychain")?;
            entry.set_password(chunk).map_err(|e| e.to_string())?;
        }
        base.set_password(&format!("chunks:{}", chunks.len())).map_err(|e| e.to_string())
    }

    /// Reads a secret from the keychain: `Ok(None)` for no entry, `Err` when the
    /// keychain itself failed.
    fn keyring_get(&self, name: &str) -> std::result::Result<Option<String>, String> {
        let Some(base) = self.entry(name) else { return Err("no keychain".into()) };
        let head = match base.get_password() {
            Ok(v) => v,
            Err(keyring::Error::NoEntry) => return Ok(None),
            Err(e) => return Err(e.to_string()),
        };
        let Some(n) = head.strip_prefix("chunks:").and_then(|n| n.parse::<usize>().ok()) else {
            return Ok(Some(head));
        };
        let mut out = String::new();
        for i in 0..n {
            let entry = self.entry(&format!("{name}#{i}")).ok_or("no keychain")?;
            out.push_str(&entry.get_password().map_err(|e| e.to_string())?);
        }
        Ok(Some(out))
    }

    fn keyring_clear_chunks(&self, name: &str) {
        for i in 0..16 {
            match self.entry(&format!("{name}#{i}")).map(|e| e.delete_credential()) {
                Some(Ok(())) => {}
                _ => break,
            }
        }
    }

    fn keyring_delete(&self, name: &str) {
        if let Some(entry) = self.entry(name) {
            match entry.delete_credential() {
                Ok(()) | Err(keyring::Error::NoEntry) => {}
                Err(e) => tracing::warn!(error = %e, "could not clear the OS keychain"),
            }
        }
        self.keyring_clear_chunks(name);
    }

    fn read_file(&self) -> Result<StoredCredentials> {
        if !self.fallback.exists() {
            return Ok(StoredCredentials::default());
        }
        let text =
            std::fs::read_to_string(&self.fallback).map_err(|e| Error::io(&self.fallback, e))?;
        serde_json::from_str(&text).map_err(|e| Error::json(self.fallback.display().to_string(), e))
    }

    fn write_file(&self, creds: &StoredCredentials) -> Result<()> {
        if creds.tokens.is_empty() && creds.refresh_token.is_none() {
            if self.fallback.exists() {
                std::fs::remove_file(&self.fallback).map_err(|e| Error::io(&self.fallback, e))?;
            }
            return Ok(());
        }
        if let Some(dir) = self.fallback.parent() {
            std::fs::create_dir_all(dir).map_err(|e| Error::io(dir, e))?;
        }
        let text = serde_json::to_string(creds).map_err(|e| Error::json("credentials", e))?;
        // Created empty and locked down *before* the secret goes in, so there is no
        // window in which the token sits in a world-readable file.
        std::fs::write(&self.fallback, b"{}").map_err(|e| Error::io(&self.fallback, e))?;
        restrict_permissions(&self.fallback);
        std::fs::write(&self.fallback, text).map_err(|e| Error::io(&self.fallback, e))?;
        Ok(())
    }

    /// Stores an account's refresh token, preferring the OS keychain.
    pub fn save(&self, uuid: &str, refresh_token: &str) -> Result<()> {
        match self.keyring_set(&keyring_account(uuid), refresh_token) {
            Ok(()) => {
                // Belt and braces: drop any plaintext copy of this account's token.
                let mut creds = self.read_file().unwrap_or_default();
                if creds.tokens.remove(uuid).is_some() {
                    self.write_file(&creds)?;
                }
                return Ok(());
            }
            Err(e) if self.use_keyring => tracing::warn!(
                error = %e,
                path = %self.fallback.display(),
                "could not write to the OS keychain; falling back to a file"
            ),
            Err(_) => {}
        }
        self.save_to_file(uuid, refresh_token)
    }

    fn save_to_file(&self, uuid: &str, refresh_token: &str) -> Result<()> {
        let mut creds = self.read_file().unwrap_or_default();
        creds.tokens.insert(uuid.to_string(), refresh_token.to_string());
        self.write_file(&creds)?;
        tracing::warn!(
            path = %self.fallback.display(),
            "refresh token stored in a user-only file because the OS keychain was unavailable"
        );
        Ok(())
    }

    /// Reads an account's stored refresh token, if there is one.
    pub fn load(&self, uuid: &str) -> Result<Option<String>> {
        match self.keyring_get(&keyring_account(uuid)) {
            Ok(Some(token)) => return Ok(Some(token)),
            Ok(None) => {}
            Err(e) if self.use_keyring => tracing::warn!(error = %e, "could not read the OS keychain"),
            Err(_) => {}
        }
        Ok(self.read_file()?.tokens.get(uuid).cloned())
    }

    /// Forgets an account's refresh token, in both places.
    pub fn clear(&self, uuid: &str) -> Result<()> {
        self.keyring_delete(&keyring_account(uuid));
        let mut creds = self.read_file().unwrap_or_default();
        if creds.tokens.remove(uuid).is_some() {
            self.write_file(&creds)?;
        }
        Ok(())
    }

    /// Moves the single-account token (keychain `microsoft-refresh-token`, or the old
    /// file format) onto `uuid`. Returns whether there was one.
    pub fn migrate_legacy(&self, uuid: &str) -> Result<bool> {
        let mut token = self.keyring_get(LEGACY_KEYRING_ACCOUNT).unwrap_or_default();
        if token.is_none() {
            token = self.read_file().unwrap_or_default().refresh_token;
        }
        let Some(token) = token else { return Ok(false) };
        self.save(uuid, &token)?;
        self.keyring_delete(LEGACY_KEYRING_ACCOUNT);
        // Re-read: `save` may have just written this account's token into the file.
        let mut creds = self.read_file().unwrap_or_default();
        if creds.refresh_token.take().is_some() {
            self.write_file(&creds)?;
        }
        Ok(true)
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

/// Windows: drop inherited ACEs and grant only the current user, with `icacls` — the
/// one ACL tool every Windows has, and no `unsafe` in this crate. Failure is logged, not
/// fatal: the file is still in the user's own profile directory.
#[cfg(windows)]
fn restrict_permissions(path: &std::path::Path) {
    use std::os::windows::process::CommandExt as _;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    let user = match (std::env::var("USERDOMAIN"), std::env::var("USERNAME")) {
        (Ok(d), Ok(u)) if !d.is_empty() => format!("{d}\\{u}"),
        (_, Ok(u)) => u,
        _ => {
            tracing::warn!("no USERNAME; cannot restrict the credentials file");
            return;
        }
    };
    let status = std::process::Command::new("icacls")
        .arg(path)
        .args(["/inheritance:r", "/grant:r"])
        .arg(format!("{user}:F"))
        .creation_flags(CREATE_NO_WINDOW)
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .status();
    match status {
        Ok(s) if s.success() => {}
        other => tracing::warn!(?other, path = %path.display(), "icacls could not restrict the credentials file"),
    }
}

#[cfg(not(any(unix, windows)))]
fn restrict_permissions(_path: &std::path::Path) {}

// ---------------------------------------------------------------------------
// accounts
// ---------------------------------------------------------------------------

/// One account the launcher remembers. Never holds a token.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct StoredAccount {
    /// Player UUID, no hyphens.
    pub uuid: String,
    /// In-game name, as of the last sign-in.
    pub username: String,
    /// `msa` or `legacy`.
    pub user_type: String,
    /// Xbox user id, when known.
    #[serde(default)]
    pub xuid: Option<String>,
}

impl StoredAccount {
    /// The account as a session with no usable token: an offline session for an offline
    /// account, and for a Microsoft account one whose token is already expired, so the
    /// launch path refreshes it before use.
    pub fn session(&self) -> Session {
        if self.user_type == "legacy" {
            return Session::offline(self.username.clone());
        }
        Session {
            username: self.username.clone(),
            uuid: self.uuid.clone(),
            access_token: "0".into(),
            user_type: self.user_type.clone(),
            xuid: self.xuid.clone(),
            expires_at: Some(0),
        }
    }

    /// Whether this is an offline account.
    pub fn is_offline(&self) -> bool {
        self.user_type == "legacy"
    }
}

impl From<&Session> for StoredAccount {
    fn from(s: &Session) -> Self {
        StoredAccount {
            uuid: s.uuid.clone(),
            username: s.username.clone(),
            user_type: s.user_type.clone(),
            xuid: s.xuid.clone(),
        }
    }
}

/// `accounts.json`: every account, and which is active.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct Accounts {
    /// UUID of the active account.
    #[serde(default)]
    pub active: Option<String>,
    /// In the order they were added.
    #[serde(default)]
    pub accounts: Vec<StoredAccount>,
}

impl Accounts {
    /// Reads `accounts.json`, migrating the single-account `profile.json` (and its
    /// keychain entry) the first time.
    pub fn load(paths: &Paths) -> Result<Self> {
        let file = paths.accounts_file();
        if file.exists() {
            let text = std::fs::read_to_string(&file).map_err(|e| Error::io(&file, e))?;
            return serde_json::from_str(&text).map_err(|e| Error::json(file.display().to_string(), e));
        }
        let legacy = paths.profile_file();
        if !legacy.exists() {
            return Ok(Self::default());
        }
        let text = std::fs::read_to_string(&legacy).map_err(|e| Error::io(&legacy, e))?;
        let session: Session =
            serde_json::from_str(&text).map_err(|e| Error::json(legacy.display().to_string(), e))?;
        let mut accounts = Self::default();
        accounts.upsert(&session, true);
        if !session.is_offline() {
            if let Err(e) = TokenStore::new(paths).migrate_legacy(&session.uuid) {
                tracing::warn!(error = %e, "could not migrate the stored refresh token");
            }
        }
        accounts.save(paths)?;
        let _ = std::fs::remove_file(&legacy);
        tracing::info!(account = %session.username, "migrated profile.json to accounts.json");
        Ok(accounts)
    }

    /// Writes `accounts.json`.
    pub fn save(&self, paths: &Paths) -> Result<()> {
        let file = paths.accounts_file();
        if let Some(dir) = file.parent() {
            std::fs::create_dir_all(dir).map_err(|e| Error::io(dir, e))?;
        }
        let text = serde_json::to_string_pretty(self).map_err(|e| Error::json("accounts", e))?;
        std::fs::write(&file, text).map_err(|e| Error::io(&file, e))
    }

    /// The active account.
    pub fn active_account(&self) -> Option<&StoredAccount> {
        let uuid = self.active.as_deref()?;
        self.accounts.iter().find(|a| a.uuid == uuid)
    }

    /// Adds or refreshes an account (by UUID), optionally making it active.
    pub fn upsert(&mut self, session: &Session, make_active: bool) {
        let stored = StoredAccount::from(session);
        match self.accounts.iter_mut().find(|a| a.uuid == stored.uuid) {
            Some(existing) => *existing = stored,
            None => self.accounts.push(stored),
        }
        if make_active || self.active.is_none() {
            self.active = Some(session.uuid.clone());
        }
    }

    /// Removes an account; the next one becomes active if it was. Returns whether it was
    /// there.
    pub fn remove(&mut self, uuid: &str) -> bool {
        let before = self.accounts.len();
        self.accounts.retain(|a| a.uuid != uuid);
        if self.active.as_deref() == Some(uuid) {
            self.active = self.accounts.first().map(|a| a.uuid.clone());
        }
        self.accounts.len() != before
    }

    /// Makes an account active. Returns false if it is not known.
    pub fn set_active(&mut self, uuid: &str) -> bool {
        if self.accounts.iter().any(|a| a.uuid == uuid) {
            self.active = Some(uuid.to_string());
            true
        } else {
            false
        }
    }
}

/// Records a completed sign-in: the refresh token in the keychain under the account's
/// UUID, and the account as the active one.
pub fn store_login(paths: &Paths, session: &Session, refresh_token: Option<&str>) -> Result<()> {
    if let Some(token) = refresh_token {
        TokenStore::new(paths).save(&session.uuid, token)?;
    }
    let mut accounts = Accounts::load(paths)?;
    accounts.upsert(session, true);
    accounts.save(paths)
}

/// Forgets an account entirely: its refresh token and its row.
pub fn forget_account(paths: &Paths, uuid: &str) -> Result<Accounts> {
    if let Err(e) = TokenStore::new(paths).clear(uuid) {
        tracing::warn!(error = %e, "could not clear the stored refresh token");
    }
    let mut accounts = Accounts::load(paths)?;
    accounts.remove(uuid);
    accounts.save(paths)?;
    Ok(accounts)
}

/// Makes `session` the active account (adding it if new). The single-account name for
/// [`Accounts::upsert`]; the access token is never written.
pub fn save_profile(paths: &Paths, session: &Session) -> Result<()> {
    let mut accounts = Accounts::load(paths)?;
    accounts.upsert(session, true);
    accounts.save(paths)
}

/// The active account as a token-less session, if there is one.
pub fn load_profile(paths: &Paths) -> Result<Option<Session>> {
    Ok(Accounts::load(paths)?.active_account().map(StoredAccount::session))
}

/// Forgets the active account (its row and its refresh token).
pub fn clear_profile(paths: &Paths) -> Result<()> {
    if let Some(uuid) = Accounts::load(paths)?.active.clone() {
        forget_account(paths, &uuid)?;
    }
    Ok(())
}

/// Signs the **active** account in from its stored refresh token, with no player
/// interaction. `Ok(None)` when there is no active Microsoft account or no stored token.
pub async fn sign_in_silently(
    client: &reqwest::Client,
    paths: &Paths,
    client_id: &str,
) -> Result<Option<Session>> {
    let Some(active) = Accounts::load(paths)?.active_account().cloned() else {
        return Ok(None);
    };
    if active.is_offline() {
        return Ok(None);
    }
    sign_in_account(client, paths, client_id, &active.uuid).await
}

/// Signs one account in from its stored refresh token: refresh, rotate the stored
/// token, and walk the Xbox → Minecraft chain for a fresh 24-hour access token. Does not
/// change which account is active.
pub async fn sign_in_account(
    client: &reqwest::Client,
    paths: &Paths,
    client_id: &str,
    uuid: &str,
) -> Result<Option<Session>> {
    let store = TokenStore::new(paths);
    let Some(refresh_token) = store.load(uuid)? else {
        return Ok(None);
    };
    let auth = Auth::new(client.clone(), client_id);
    let tokens = auth.refresh(&refresh_token).await?;
    if let Some(new_refresh) = &tokens.refresh_token {
        store.save(uuid, new_refresh)?;
    }
    let session = auth.minecraft_session(&tokens.access_token).await?;
    let mut accounts = Accounts::load(paths)?;
    accounts.upsert(&session, false);
    accounts.save(paths)?;
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
    fn the_account_list_never_holds_an_access_token() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        let mut session = Session::offline("Tester");
        session.access_token = "secret".into();
        save_profile(&paths, &session).unwrap();

        let text = std::fs::read_to_string(paths.accounts_file()).unwrap();
        assert!(!text.contains("secret"));
        assert_eq!(load_profile(&paths).unwrap().unwrap().username, "Tester");

        clear_profile(&paths).unwrap();
        assert_eq!(load_profile(&paths).unwrap(), None);
    }

    fn file_store(dir: &std::path::Path) -> TokenStore {
        TokenStore { fallback: dir.join("credentials.json"), use_keyring: false }
    }

    #[test]
    fn the_token_store_falls_back_to_a_file_keyed_by_account() {
        let dir = tempfile::tempdir().unwrap();
        let store = file_store(dir.path());
        store.save("aaaa", "refresh-a").unwrap();
        store.save("bbbb", "refresh-b").unwrap();
        assert_eq!(store.load("aaaa").unwrap().as_deref(), Some("refresh-a"));
        assert_eq!(store.load("bbbb").unwrap().as_deref(), Some("refresh-b"));
        assert_eq!(store.load("cccc").unwrap(), None);
        store.clear("aaaa").unwrap();
        assert_eq!(store.load("aaaa").unwrap(), None);
        store.clear("bbbb").unwrap();
        assert!(!dir.path().join("credentials.json").exists(), "an empty fallback file is deleted");
    }

    #[test]
    fn the_single_account_file_token_migrates_onto_its_account() {
        let dir = tempfile::tempdir().unwrap();
        let store = file_store(dir.path());
        std::fs::write(dir.path().join("credentials.json"), r#"{"refresh_token":"old"}"#).unwrap();
        assert!(store.migrate_legacy("aaaa").unwrap());
        assert_eq!(store.load("aaaa").unwrap().as_deref(), Some("old"));
        let text = std::fs::read_to_string(dir.path().join("credentials.json")).unwrap();
        assert!(!text.contains("refresh_token"), "{text}");
    }

    #[cfg(windows)]
    #[test]
    fn the_fallback_file_is_restricted_to_the_current_user_on_windows() {
        let dir = tempfile::tempdir().unwrap();
        let store = file_store(dir.path());
        store.save("aaaa", "refresh-a").unwrap();
        let out = std::process::Command::new("icacls")
            .arg(dir.path().join("credentials.json"))
            .output()
            .unwrap();
        let acl = String::from_utf8_lossy(&out.stdout);
        let user = std::env::var("USERNAME").unwrap();
        assert!(acl.contains(&user), "{acl}");
        assert!(!acl.contains("BUILTIN\\Users"), "inherited ACEs are removed: {acl}");
        assert!(!acl.contains("(I)"), "no inherited entries remain: {acl}");
    }

    #[test]
    fn accounts_add_switch_and_remove() {
        let mut accounts = Accounts::default();
        let a = Session::offline("Alpha");
        let mut b = Session::offline("Bravo");
        b.user_type = "msa".into();
        accounts.upsert(&a, false);
        assert_eq!(accounts.active.as_deref(), Some(a.uuid.as_str()), "the first account is active");
        accounts.upsert(&b, true);
        assert_eq!(accounts.active_account().unwrap().username, "Bravo");
        assert!(accounts.set_active(&a.uuid));
        assert!(!accounts.set_active("nobody"));
        assert!(accounts.remove(&a.uuid));
        assert_eq!(accounts.active_account().unwrap().username, "Bravo", "removing the active one activates the next");
        // A Microsoft account restored without the network carries an expired token.
        let s = accounts.active_account().unwrap().session();
        assert!(s.expires_within(0));
    }

    #[test]
    fn profile_json_migrates_into_accounts_json() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        std::fs::write(
            paths.profile_file(),
            r#"{"username":"Searge","uuid":"0123","access_token":"","user_type":"legacy"}"#,
        )
        .unwrap();
        let accounts = Accounts::load(&paths).unwrap();
        assert_eq!(accounts.active_account().unwrap().username, "Searge");
        assert!(paths.accounts_file().exists());
        assert!(!paths.profile_file().exists());
    }

    #[test]
    fn token_expiry_is_judged_with_a_margin() {
        let mut s = Session::offline("x");
        assert!(!s.expires_within(3600), "offline sessions never expire");
        s.user_type = "msa".into();
        s.expires_at = None;
        assert!(s.expires_within(0), "unknown expiry counts as expired");
        s.expires_at = Some(now_secs() + 7200);
        assert!(!s.expires_within(1800));
        assert!(s.expires_within(7200 + 60));
    }
}
