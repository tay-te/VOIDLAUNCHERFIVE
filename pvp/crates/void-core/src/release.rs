//! The `void-client` release channel: a signed manifest naming one mod JAR per platform.
//!
//! The launcher never runs a mod JAR it cannot prove VOID published. Every release
//! publishes `mod-manifest.json` next to the installers, on a fixed per-channel GitHub
//! release (`pvp-stable`, `pvp-beta`) so the URL never moves:
//!
//! ```text
//! https://github.com/tay-te/VOIDLAUNCHERFIVE/releases/download/pvp-stable/mod-manifest.json
//! ```
//!
//! The file is an envelope — `{ "payload": "<manifest JSON as a string>", "signature":
//! "<base64 ed25519>" }` — so the bytes that were signed are exactly the bytes that are
//! parsed, with no canonicalisation to get wrong. The signature is checked against
//! [`PUBLIC_KEY`], which is compiled in (`keys/mod-manifest.pub`); the private half lives
//! outside the repository and in the release workflow's `VOID_MOD_MANIFEST_KEY` secret.
//!
//! The manifest names, per platform, the JAR's URL, SHA-256 and size, plus the bridge
//! protocol version the mod speaks and the oldest launcher that can drive it. A manifest
//! this launcher cannot use (newer protocol, newer `min_launcher_version`) is not
//! adopted: the last compatible one stays in force and the caller is told an update is
//! needed.
//!
//! **Offline.** The last verified envelope is kept under `release/<channel>/` and the
//! JARs under `mods-cache/<sha256>/`, so a warm launch with no network re-verifies the
//! cached envelope and the cached JAR and carries on. Nothing unverified is ever used
//! from the cache either: the envelope is re-checked against the key on every read, and
//! the JAR against the manifest's SHA-256.
//!
//! **Developers** bypass all of this with `$VOID_MOD_JAR` or `config.mod_jar`
//! ([`crate::Config::mod_jar_override`]); `$VOID_MOD_MANIFEST_URL` points the channel at
//! a different manifest (still signature-checked) for testing a release before it ships.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use base64::Engine as _;
use serde::{Deserialize, Serialize};
use tokio::sync::mpsc;

use crate::download::{self, Progress};
use crate::error::{Error, Result};
use crate::install::ModPlatform;
use crate::paths::Paths;

/// The release key's public half, base64 of the raw 32-byte ed25519 key.
pub const PUBLIC_KEY: &str = include_str!("../keys/mod-manifest.pub");

/// Where channel releases live. The repository's own `/releases/latest` belongs to the
/// older Electron launcher's `v*` tags, which is why the PvP client uses fixed
/// per-channel tags instead of "latest".
pub const RELEASES_BASE: &str = "https://github.com/tay-te/VOIDLAUNCHERFIVE/releases/download";

/// The manifest's file name, on the release and in the cache.
pub const MANIFEST_FILE: &str = "mod-manifest.json";

/// Overrides the manifest URL (testing a release candidate). Still signature-checked.
pub const MANIFEST_URL_ENV: &str = "VOID_MOD_MANIFEST_URL";

/// The manifest format this launcher reads.
pub const MANIFEST_SCHEMA: u32 = 1;

/// How long the manifest fetch may take before the launch falls back to the cache. Short
/// on purpose: this sits on the Launch button's critical path.
const FETCH_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(8);

/// How many content-addressed JARs to keep in `mods-cache/`: the current one and the one
/// before it, so a rollback is offline-safe and the cache does not grow forever.
const KEEP_CACHED_JARS: usize = 2;

/// A release channel. `beta` sees prereleases and every stable release too.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Channel {
    /// Stable releases only.
    Stable,
    /// Prereleases as well.
    Beta,
}

impl Channel {
    /// Reads the launcher's `update_channel` setting; anything unknown is stable.
    pub fn parse(s: &str) -> Channel {
        if s.trim().eq_ignore_ascii_case("beta") {
            Channel::Beta
        } else {
            Channel::Stable
        }
    }

    /// `stable` or `beta`.
    pub fn key(self) -> &'static str {
        match self {
            Channel::Stable => "stable",
            Channel::Beta => "beta",
        }
    }

    /// The fixed GitHub release tag that carries this channel's current files.
    pub fn tag(self) -> &'static str {
        match self {
            Channel::Stable => "pvp-stable",
            Channel::Beta => "pvp-beta",
        }
    }

    /// Where this channel's manifest is fetched from.
    pub fn manifest_url(self) -> String {
        std::env::var(MANIFEST_URL_ENV)
            .ok()
            .filter(|u| !u.trim().is_empty())
            .unwrap_or_else(|| format!("{RELEASES_BASE}/{}/{MANIFEST_FILE}", self.tag()))
    }
}

/// The signed envelope, exactly as published.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct SignedManifest {
    /// The manifest JSON, as a string: the signed bytes are its UTF-8.
    pub payload: String,
    /// Base64 ed25519 signature over `payload`.
    pub signature: String,
}

/// What a release publishes for the mod.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ModManifest {
    /// Format version; this launcher reads [`MANIFEST_SCHEMA`].
    pub schema: u32,
    /// `stable` or `beta`.
    pub channel: String,
    /// The `void-client` version, e.g. `0.2.0`.
    pub mod_version: String,
    /// The bridge protocol the mod speaks (`void_bridge::PROTOCOL_VERSION`).
    pub protocol_version: u32,
    /// The oldest launcher that can drive this mod.
    pub min_launcher_version: String,
    /// When the release was built, RFC 3339.
    #[serde(default)]
    pub published_at: String,
    /// One JAR per platform key (`windows-x64`, `macos-x64`, ...).
    pub jars: BTreeMap<String, ModJar>,
}

/// One platform's JAR.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ModJar {
    /// File name, `void-client-<version>-<platform>.jar`.
    pub name: String,
    /// Download URL.
    pub url: String,
    /// Lower-case hex SHA-256.
    pub sha256: String,
    /// Size in bytes.
    pub size: u64,
}

fn verifying_key() -> Result<ed25519_dalek::VerifyingKey> {
    let raw = base64::engine::general_purpose::STANDARD
        .decode(PUBLIC_KEY.trim())
        .map_err(|e| Error::Signature(format!("the compiled-in public key is not base64: {e}")))?;
    let bytes: [u8; 32] = raw
        .try_into()
        .map_err(|_| Error::Signature("the compiled-in public key is not 32 bytes".into()))?;
    ed25519_dalek::VerifyingKey::from_bytes(&bytes)
        .map_err(|e| Error::Signature(format!("the compiled-in public key is invalid: {e}")))
}

impl SignedManifest {
    /// Parses an envelope from bytes.
    pub fn parse(bytes: &[u8]) -> Result<Self> {
        serde_json::from_slice(bytes).map_err(|e| Error::json("mod-manifest.json", e))
    }

    /// Checks the signature against [`PUBLIC_KEY`] and parses the payload.
    pub fn verify(&self) -> Result<ModManifest> {
        self.verify_with(&verifying_key()?)
    }

    /// Checks the signature against an explicit key; used by the tests.
    pub fn verify_with(&self, key: &ed25519_dalek::VerifyingKey) -> Result<ModManifest> {
        let sig = base64::engine::general_purpose::STANDARD
            .decode(self.signature.trim())
            .map_err(|e| Error::Signature(format!("signature is not base64: {e}")))?;
        let sig = ed25519_dalek::Signature::from_slice(&sig)
            .map_err(|e| Error::Signature(format!("malformed signature: {e}")))?;
        key.verify_strict(self.payload.as_bytes(), &sig)
            .map_err(|_| Error::Signature("the signature does not match the VOID release key".into()))?;
        let manifest: ModManifest = serde_json::from_str(&self.payload)
            .map_err(|e| Error::json("mod-manifest.json payload", e))?;
        if manifest.schema != MANIFEST_SCHEMA {
            return Err(Error::Signature(format!(
                "manifest schema {} is not one this launcher reads ({MANIFEST_SCHEMA})",
                manifest.schema
            )));
        }
        for jar in manifest.jars.values() {
            if jar.sha256.len() != 64 || !jar.sha256.chars().all(|c| c.is_ascii_hexdigit()) {
                return Err(Error::Signature(format!("{} has no valid sha256", jar.name)));
            }
            // The name becomes a file name under mods/; nothing that can climb out of it.
            if jar.name.is_empty()
                || jar.name.contains(['/', '\\'])
                || jar.name.contains("..")
                || !jar.name.ends_with(".jar")
            {
                return Err(Error::Signature(format!("`{}` is not a safe JAR name", jar.name)));
            }
        }
        Ok(manifest)
    }
}

/// Why a verified manifest cannot be used by this launcher.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Incompatible {
    /// The mod needs a newer launcher.
    LauncherTooOld {
        /// What the manifest asks for.
        required: String,
    },
    /// The mod speaks a bridge protocol this launcher does not.
    Protocol {
        /// The manifest's protocol version.
        manifest: u32,
        /// This launcher's.
        launcher: u32,
    },
    /// The release has no JAR for this platform.
    NoJar(String),
}

impl ModManifest {
    /// Whether this launcher, at `launcher_version` speaking `protocol`, can use this
    /// manifest's JAR for `platform`.
    pub fn check(
        &self,
        launcher_version: &str,
        protocol: u32,
        platform: ModPlatform,
    ) -> std::result::Result<&ModJar, Incompatible> {
        if version_less(launcher_version, &self.min_launcher_version) {
            return Err(Incompatible::LauncherTooOld { required: self.min_launcher_version.clone() });
        }
        if self.protocol_version != protocol {
            return Err(Incompatible::Protocol { manifest: self.protocol_version, launcher: protocol });
        }
        self.jars.get(platform.key()).ok_or_else(|| Incompatible::NoJar(platform.key().to_string()))
    }
}

/// `a < b` by semantic version, with anything unparsable treated as not-less (so a
/// malformed version never locks a player out; the signature already vouches for it).
fn version_less(a: &str, b: &str) -> bool {
    match (semver::Version::parse(a.trim()), semver::Version::parse(b.trim())) {
        (Ok(a), Ok(b)) => a < b,
        _ => false,
    }
}

/// Where a resolved JAR came from.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ModSource {
    /// A manifest fetched just now.
    Fresh,
    /// The cached manifest, because the network was unavailable (or the fresh one was
    /// unusable — see [`ResolvedMod::notice`]).
    Cached,
    /// `$VOID_MOD_JAR` / `config.mod_jar`.
    DevOverride,
}

/// The `void-client` JAR a launch will install.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ResolvedMod {
    /// The verified JAR on disk (in `mods-cache/`, or the developer's path).
    pub jar: PathBuf,
    /// The mod version, or `dev` for an override.
    pub version: String,
    /// Where it came from.
    pub source: ModSource,
    /// Set when a newer release exists that this launcher cannot use yet, or the fresh
    /// manifest was rejected; worth showing the player.
    pub notice: Option<String>,
}

fn cached_envelope_path(paths: &Paths, channel: Channel) -> PathBuf {
    paths.release_dir(channel.key()).join(MANIFEST_FILE)
}

/// Reads and re-verifies the cached envelope for a channel.
pub fn cached_manifest(paths: &Paths, channel: Channel) -> Option<ModManifest> {
    cached_manifest_with(paths, channel, &verifying_key().ok()?)
}

fn cached_manifest_with(
    paths: &Paths,
    channel: Channel,
    key: &ed25519_dalek::VerifyingKey,
) -> Option<ModManifest> {
    let file = cached_envelope_path(paths, channel);
    let bytes = std::fs::read(&file).ok()?;
    match SignedManifest::parse(&bytes).and_then(|s| s.verify_with(key)) {
        Ok(m) => Some(m),
        Err(e) => {
            tracing::warn!(path = %file.display(), error = %e, "ignoring a cached release manifest");
            None
        }
    }
}

fn save_envelope(paths: &Paths, channel: Channel, bytes: &[u8]) -> Result<()> {
    let file = cached_envelope_path(paths, channel);
    let dir = file.parent().expect("release dir has a parent");
    std::fs::create_dir_all(dir).map_err(|e| Error::io(dir, e))?;
    let tmp = dir.join(format!(".{MANIFEST_FILE}.{}.tmp", std::process::id()));
    std::fs::write(&tmp, bytes).map_err(|e| Error::io(&tmp, e))?;
    std::fs::rename(&tmp, &file).map_err(|e| {
        let _ = std::fs::remove_file(&tmp);
        Error::io(&file, e)
    })
}

/// The cache path for a JAR: `mods-cache/<sha256>/<name>`.
pub fn cached_jar_path(paths: &Paths, jar: &ModJar) -> PathBuf {
    paths.mod_cache_dir().join(jar.sha256.to_ascii_lowercase()).join(&jar.name)
}

/// Whether a cached JAR is present and has the published size and SHA-256.
fn cached_jar_ok(path: &Path, jar: &ModJar) -> bool {
    match std::fs::metadata(path) {
        Ok(m) if m.is_file() && m.len() == jar.size => {}
        _ => return false,
    }
    match download::sha256_file(path) {
        Ok(actual) if actual.eq_ignore_ascii_case(&jar.sha256) => true,
        Ok(_) => {
            tracing::warn!(path = %path.display(), "cached void-client JAR is corrupt; refetching");
            let _ = std::fs::remove_file(path);
            false
        }
        Err(_) => false,
    }
}

async fn fetch_envelope(client: &reqwest::Client, url: &str) -> Result<Vec<u8>> {
    let resp = client.get(url).timeout(FETCH_TIMEOUT).send().await?;
    let status = resp.status();
    if !status.is_success() {
        return Err(Error::HttpStatus { url: url.to_string(), status: status.as_u16(), detail: None });
    }
    Ok(resp.bytes().await?.to_vec())
}

/// Resolves the `void-client` JAR for `platform` from the channel, fetching what is
/// missing, and returns the verified file in `mods-cache/`.
///
/// The order, and why:
///
/// 1. Fetch the channel's envelope (8 s timeout). Verified and usable → cache it.
/// 2. Fetched but unusable (bad signature, launcher too old, protocol mismatch) → keep
///    the cached one if it is usable, with a [`ResolvedMod::notice`]; otherwise fail
///    with the reason, because there is nothing safe to run.
/// 3. Not fetched (offline) → the cached envelope, re-verified.
/// 4. Download the JAR into `mods-cache/<sha256>/` unless a verified copy is there.
pub async fn ensure_mod_jar(
    client: &reqwest::Client,
    paths: &Paths,
    platform: ModPlatform,
    channel: Channel,
    launcher_version: &str,
    protocol: u32,
    progress: Option<mpsc::Sender<Progress>>,
) -> Result<ResolvedMod> {
    let target = Target { platform, channel, launcher_version, protocol };
    ensure_from(client, paths, &target, &channel.manifest_url(), &verifying_key()?, progress).await
}

/// What a resolution is for; bundled so the internals stay readable.
#[derive(Clone, Copy)]
struct Target<'a> {
    platform: ModPlatform,
    channel: Channel,
    launcher_version: &'a str,
    protocol: u32,
}

async fn ensure_from(
    client: &reqwest::Client,
    paths: &Paths,
    target: &Target<'_>,
    url: &str,
    key: &ed25519_dalek::VerifyingKey,
    progress: Option<mpsc::Sender<Progress>>,
) -> Result<ResolvedMod> {
    let Target { platform, channel, launcher_version, protocol } = *target;
    let url = url.to_string();
    let cached = cached_manifest_with(paths, channel, key);
    let usable = |m: &ModManifest| m.check(launcher_version, protocol, platform).is_ok();

    let (manifest, source, notice) = match fetch_envelope(client, &url).await {
        Ok(bytes) => match SignedManifest::parse(&bytes).and_then(|s| s.verify_with(key)) {
            Ok(fresh) => match fresh.check(launcher_version, protocol, platform) {
                Ok(_) => {
                    save_envelope(paths, channel, &bytes)?;
                    (fresh, ModSource::Fresh, None)
                }
                Err(why) => {
                    let notice = describe(&why, launcher_version, &fresh);
                    match cached.filter(usable) {
                        Some(old) => (old, ModSource::Cached, Some(notice)),
                        None => return Err(incompatible_error(why, launcher_version)),
                    }
                }
            },
            Err(e) => {
                tracing::warn!(%url, error = %e, "rejected the published release manifest");
                match cached.filter(usable) {
                    Some(old) => (old, ModSource::Cached, Some(e.to_string())),
                    None => return Err(e),
                }
            }
        },
        Err(e) if e.is_network() || matches!(e, Error::HttpStatus { status: 404, .. }) => {
            tracing::info!(%url, error = %e, "release channel unreachable; using the cached manifest");
            match cached {
                Some(old) => match old.check(launcher_version, protocol, platform) {
                    Ok(_) => (old, ModSource::Cached, None),
                    Err(why) => return Err(incompatible_error(why, launcher_version)),
                },
                None => {
                    return Err(Error::ModUnavailable(format!(
                        "the release channel could not be reached ({e}) and nothing is cached \
                         yet. Connect to the internet for the first launch."
                    )))
                }
            }
        }
        Err(e) => return Err(e),
    };

    let jar = manifest
        .jars
        .get(platform.key())
        .ok_or_else(|| Error::ModUnavailable(format!("no JAR for {}", platform.key())))?
        .clone();
    let path = cached_jar_path(paths, &jar);
    if cached_jar_ok(&path, &jar) {
        if let Some(tx) = &progress {
            let _ = tx.send(Progress::Started { files: 1, bytes: jar.size }).await;
            let _ = tx
                .send(Progress::Finished { path: path.clone(), bytes: jar.size, cached: true, done: 1, total: 1 })
                .await;
        }
    } else {
        download::fetch_sha256(client, &jar.url, &path, &jar.sha256, Some(jar.size), progress.as_ref())
            .await
            .map_err(|e| match e {
                e if e.is_network() => Error::ModUnavailable(format!(
                    "could not download {} ({e}). Check your connection and try again.",
                    jar.name
                )),
                other => other,
            })?;
    }
    prune_cache(paths, &path);
    tracing::info!(version = %manifest.mod_version, jar = %path.display(), ?source, "void-client resolved");
    let _ = channel;

    Ok(ResolvedMod { jar: path, version: manifest.mod_version.clone(), source, notice })
}

/// The JAR the last [`ensure_mod_jar`] left verified in the cache, with no network.
///
/// What `launch` uses when `prepare` already ran: re-verifies the cached envelope and the
/// cached JAR, and answers `None` if either is missing or wrong.
pub fn resolve_cached(
    paths: &Paths,
    platform: ModPlatform,
    channel: Channel,
    launcher_version: &str,
    protocol: u32,
) -> Option<ResolvedMod> {
    let manifest = cached_manifest(paths, channel)?;
    let jar = manifest.check(launcher_version, protocol, platform).ok()?;
    let path = cached_jar_path(paths, jar);
    cached_jar_ok(&path, jar).then(|| ResolvedMod {
        jar: path,
        version: manifest.mod_version.clone(),
        source: ModSource::Cached,
        notice: None,
    })
}

/// The developer override as a [`ResolvedMod`], when one is set and exists.
pub fn dev_override(jar: PathBuf) -> Result<ResolvedMod> {
    if !jar.is_file() {
        return Err(Error::ModUnavailable(format!(
            "the developer override {} does not exist (unset VOID_MOD_JAR / config.mod_jar \
             to use the release channel)",
            jar.display()
        )));
    }
    Ok(ResolvedMod { jar, version: "dev".into(), source: ModSource::DevOverride, notice: None })
}

/// Keeps the newest [`KEEP_CACHED_JARS`] entries of `mods-cache/`, always including
/// `current`.
fn prune_cache(paths: &Paths, current: &Path) {
    let root = paths.mod_cache_dir();
    let Ok(entries) = std::fs::read_dir(&root) else { return };
    let keep_dir = current.parent().map(Path::to_path_buf);
    let mut dirs: Vec<(PathBuf, Option<std::time::SystemTime>)> = entries
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.is_dir() && Some(p) != keep_dir.as_ref())
        .map(|p| {
            let modified = std::fs::metadata(&p).and_then(|m| m.modified()).ok();
            (p, modified)
        })
        .collect();
    dirs.sort_by_key(|(_, m)| std::cmp::Reverse(*m));
    for (dir, _) in dirs.into_iter().skip(KEEP_CACHED_JARS.saturating_sub(1)) {
        let _ = std::fs::remove_dir_all(dir);
    }
}

fn describe(why: &Incompatible, launcher_version: &str, fresh: &ModManifest) -> String {
    match why {
        Incompatible::LauncherTooOld { required } => format!(
            "VOID client {} needs launcher {required} (this is {launcher_version}); restart to update.",
            fresh.mod_version
        ),
        Incompatible::Protocol { manifest, launcher } => format!(
            "VOID client {} speaks protocol {manifest}, this launcher speaks {launcher}; restart to update.",
            fresh.mod_version
        ),
        Incompatible::NoJar(p) => format!("VOID client {} has no build for {p} yet.", fresh.mod_version),
    }
}

fn incompatible_error(why: Incompatible, launcher_version: &str) -> Error {
    match why {
        Incompatible::LauncherTooOld { required } => {
            Error::LauncherTooOld { required, current: launcher_version.to_string() }
        }
        Incompatible::Protocol { manifest, launcher } => Error::ModUnavailable(format!(
            "the published VOID client speaks bridge protocol {manifest} and this launcher \
             speaks {launcher}. Restart the launcher to update it."
        )),
        Incompatible::NoJar(p) => Error::ModUnavailable(format!("no VOID client build for {p}")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::{Signer as _, SigningKey};

    fn key() -> SigningKey {
        SigningKey::from_bytes(&[7u8; 32])
    }

    fn manifest() -> ModManifest {
        let mut jars = BTreeMap::new();
        jars.insert(
            "windows-x64".to_string(),
            ModJar {
                name: "void-client-0.2.0-windows-x64.jar".into(),
                url: "https://example.invalid/void-client-0.2.0-windows-x64.jar".into(),
                sha256: "ab".repeat(32),
                size: 3,
            },
        );
        ModManifest {
            schema: MANIFEST_SCHEMA,
            channel: "stable".into(),
            mod_version: "0.2.0".into(),
            protocol_version: 2,
            min_launcher_version: "0.1.0".into(),
            published_at: "2026-10-15T00:00:00Z".into(),
            jars,
        }
    }

    fn sign(m: &ModManifest, k: &SigningKey) -> SignedManifest {
        let payload = serde_json::to_string(m).unwrap();
        let sig = k.sign(payload.as_bytes());
        SignedManifest {
            payload,
            signature: base64::engine::general_purpose::STANDARD.encode(sig.to_bytes()),
        }
    }

    #[test]
    fn the_compiled_in_key_is_a_valid_ed25519_key() {
        verifying_key().expect("keys/mod-manifest.pub must decode to a 32-byte ed25519 key");
    }

    #[test]
    fn a_correctly_signed_manifest_verifies_and_parses() {
        let k = key();
        let signed = sign(&manifest(), &k);
        assert_eq!(signed.verify_with(&k.verifying_key()).unwrap(), manifest());
    }

    #[test]
    fn a_tampered_payload_or_a_foreign_key_is_rejected() {
        let k = key();
        let mut signed = sign(&manifest(), &k);
        signed.payload = signed.payload.replace("0.2.0", "6.6.6");
        assert!(matches!(signed.verify_with(&k.verifying_key()), Err(Error::Signature(_))));

        let other = SigningKey::from_bytes(&[9u8; 32]);
        let signed = sign(&manifest(), &other);
        assert!(matches!(signed.verify_with(&k.verifying_key()), Err(Error::Signature(_))));
        // ...and nothing signed by a test key passes the real release key.
        assert!(signed.verify().is_err());
    }

    #[test]
    fn a_jar_name_that_could_escape_mods_is_rejected_even_when_signed() {
        let k = key();
        let mut m = manifest();
        m.jars.get_mut("windows-x64").unwrap().name = "../../evil.jar".into();
        assert!(sign(&m, &k).verify_with(&k.verifying_key()).is_err());
    }

    #[test]
    fn compatibility_covers_launcher_version_protocol_and_platform() {
        let m = manifest();
        assert!(m.check("0.1.0", 2, ModPlatform::WindowsX64).is_ok());
        assert_eq!(
            m.check("0.1.0", 3, ModPlatform::WindowsX64).unwrap_err(),
            Incompatible::Protocol { manifest: 2, launcher: 3 }
        );
        assert_eq!(
            m.check("0.1.0", 2, ModPlatform::MacosX64).unwrap_err(),
            Incompatible::NoJar("macos-x64".into())
        );
        let newer = ModManifest { min_launcher_version: "0.3.0".into(), ..manifest() };
        assert!(matches!(
            newer.check("0.2.9", 2, ModPlatform::WindowsX64),
            Err(Incompatible::LauncherTooOld { .. })
        ));
        assert!(newer.check("0.3.0", 2, ModPlatform::WindowsX64).is_ok());
        assert!(newer.check("0.3.0-beta.1", 2, ModPlatform::WindowsX64).is_err(), "a prerelease sorts before");
    }

    #[test]
    fn channels_map_to_fixed_release_tags() {
        assert_eq!(Channel::parse("beta"), Channel::Beta);
        assert_eq!(Channel::parse("BETA "), Channel::Beta);
        assert_eq!(Channel::parse("nightly"), Channel::Stable);
        assert_eq!(Channel::Beta.tag(), "pvp-beta");
        assert_eq!(Channel::Stable.tag(), "pvp-stable");
    }

    #[test]
    fn a_cached_jar_is_trusted_only_at_the_published_size_and_hash() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("x.jar");
        std::fs::write(&file, b"abc").unwrap();
        let mut jar = ModJar {
            name: "x.jar".into(),
            url: String::new(),
            sha256: "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".into(),
            size: 3,
        };
        assert!(cached_jar_ok(&file, &jar));
        jar.size = 4;
        assert!(!cached_jar_ok(&file, &jar));
        jar.size = 3;
        jar.sha256 = "00".repeat(32);
        assert!(!cached_jar_ok(&file, &jar));
        assert!(!file.exists(), "a corrupt cached JAR is deleted so it is refetched");
    }

    #[test]
    fn the_cache_keeps_the_current_jar_and_one_more() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        let root = paths.mod_cache_dir();
        for name in ["a", "b", "c"] {
            std::fs::create_dir_all(root.join(name)).unwrap();
            std::thread::sleep(std::time::Duration::from_millis(20));
        }
        prune_cache(&paths, &root.join("a").join("x.jar"));
        let mut left: Vec<_> = std::fs::read_dir(&root)
            .unwrap()
            .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
            .collect();
        left.sort();
        assert_eq!(left, ["a", "c"], "current (a) plus the newest other (c)");
    }

    /// Nothing listens on the discard port, so this is "offline" without a network.
    const UNREACHABLE: &str = "http://127.0.0.1:9/mod-manifest.json";

    fn target() -> Target<'static> {
        Target { platform: ModPlatform::WindowsX64, channel: Channel::Stable, launcher_version: "0.1.0", protocol: 2 }
    }

    #[tokio::test]
    async fn offline_with_nothing_cached_is_a_clear_error_not_a_hang() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        let k = key();
        let err = ensure_from(&reqwest::Client::new(), &paths, &target(), UNREACHABLE, &k.verifying_key(), None)
            .await
            .unwrap_err();
        assert!(matches!(err, Error::ModUnavailable(_)), "{err}");
    }

    #[tokio::test]
    async fn offline_a_cached_signed_manifest_and_jar_launch_with_no_network() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        let k = key();

        // What a previous online prepare left behind: the envelope and the JAR.
        let mut m = manifest();
        let body = b"PK-not-really-a-jar";
        {
            use sha2::{Digest as _, Sha256};
            let jar = m.jars.get_mut("windows-x64").unwrap();
            jar.sha256 = hex::encode(Sha256::digest(body));
            jar.size = body.len() as u64;
        }
        let signed = sign(&m, &k);
        save_envelope(&paths, Channel::Stable, &serde_json::to_vec(&signed).unwrap()).unwrap();
        let jar_path = cached_jar_path(&paths, &m.jars["windows-x64"]);
        std::fs::create_dir_all(jar_path.parent().unwrap()).unwrap();
        std::fs::write(&jar_path, body).unwrap();

        let resolved =
            ensure_from(&reqwest::Client::new(), &paths, &target(), UNREACHABLE, &k.verifying_key(), None)
                .await
                .unwrap();
        assert_eq!(resolved.source, ModSource::Cached);
        assert_eq!(resolved.version, "0.2.0");
        assert_eq!(resolved.jar, jar_path);

        // A launcher too old for the cached manifest is refused with the update message.
        let old = Target { launcher_version: "0.0.1", ..target() };
        let m2 = ModManifest { min_launcher_version: "0.1.0".into(), ..m.clone() };
        save_envelope(&paths, Channel::Stable, &serde_json::to_vec(&sign(&m2, &k)).unwrap()).unwrap();
        let err = ensure_from(&reqwest::Client::new(), &paths, &old, UNREACHABLE, &k.verifying_key(), None)
            .await
            .unwrap_err();
        assert!(matches!(err, Error::LauncherTooOld { .. }), "{err}");

        // A tampered cache is not trusted at all.
        std::fs::write(cached_envelope_path(&paths, Channel::Stable), br#"{"payload":"{}","signature":"AAAA"}"#)
            .unwrap();
        assert!(cached_manifest_with(&paths, Channel::Stable, &k.verifying_key()).is_none());
    }
}
