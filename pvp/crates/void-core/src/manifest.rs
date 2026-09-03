//! Mojang and Legacy Fabric manifests, and the merged launch profile.
//!
//! Two sources, one result:
//!
//! 1. Mojang's `version_manifest_v2` names every version and where its version JSON is.
//!    The 1.8.9 version JSON lists libraries (with OS rules and natives classifiers),
//!    the asset index and the client jar.
//! 2. Legacy Fabric's meta server publishes a *profile* JSON per loader build, in the
//!    same format, with `inheritsFrom: "1.8.9"`. Mainline Fabric has no 1.8.9 (§15).
//!
//! [`resolve_profile`] merges them into a [`LaunchProfile`]: what to download, what goes
//! on the classpath, which jars hold natives, and the argument template. Everything in
//! here is pure — the network lives in the `fetch_*` functions — so the merge, the rule
//! evaluation and the natives selection are all unit-testable against a fixture.

use std::collections::BTreeMap;
use std::path::PathBuf;

use serde::{Deserialize, Serialize};

use crate::error::{Error, Result};

/// Mojang's version manifest, v2 (carries `sha1` per version, unlike v1).
pub const VERSION_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

/// Legacy Fabric's meta server. Fabric proper has no 1.8.9.
pub const LEGACY_FABRIC_META: &str = "https://meta.legacyfabric.net/v2";

/// Where Mojang serves asset objects from.
pub const RESOURCES_BASE: &str = "https://resources.download.minecraft.net";

/// The Minecraft version this client targets.
pub const MC_VERSION: &str = "1.8.9";

// ---------------------------------------------------------------------------
// platform
// ---------------------------------------------------------------------------

/// The three OS names Mojang's rules and natives classifiers use.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Os {
    /// Microsoft Windows.
    Windows,
    /// macOS — `osx` in every Mojang manifest.
    Osx,
    /// Linux.
    Linux,
}

impl Os {
    /// The OS this build is running on.
    pub fn host() -> Result<Os> {
        match std::env::consts::OS {
            "windows" => Ok(Os::Windows),
            "macos" => Ok(Os::Osx),
            "linux" => Ok(Os::Linux),
            other => Err(Error::UnsupportedPlatform(other.to_string())),
        }
    }

    /// The name used in manifests.
    pub fn key(self) -> &'static str {
        match self {
            Os::Windows => "windows",
            Os::Osx => "osx",
            Os::Linux => "linux",
        }
    }
}

/// The host facts a rule is evaluated against.
#[derive(Debug, Clone)]
pub struct RuleContext {
    /// Which OS.
    pub os: Os,
    /// Mojang's architecture name: `x86`, `x86_64` or `arm64`.
    pub arch: String,
    /// Feature flags such as `is_demo_user`; 1.8.9 uses none, but the format allows them.
    pub features: BTreeMap<String, bool>,
}

impl RuleContext {
    /// The rule context for the machine this is running on.
    ///
    /// Apple Silicon reports `arm64` here, but 1.8.9 is launched on an **x64** JVM under
    /// Rosetta because LWJGL 2 has no arm64 natives (§13); [`RuleContext::natives_bits`]
    /// is what the classifier substitution uses, and it follows the JVM, not the CPU.
    pub fn host() -> Result<Self> {
        Ok(Self {
            os: Os::host()?,
            arch: match std::env::consts::ARCH {
                "x86" => "x86",
                "aarch64" => "arm64",
                _ => "x86_64",
            }
            .to_string(),
            features: BTreeMap::new(),
        })
    }

    /// A context for an explicit OS and architecture; used by the tests.
    pub fn new(os: Os, arch: &str) -> Self {
        Self { os, arch: arch.to_string(), features: BTreeMap::new() }
    }

    /// What `${arch}` expands to in a natives classifier: always `64` for us, because
    /// 1.8.9 runs on a 64-bit JVM on every platform we ship.
    pub fn natives_bits(&self) -> &'static str {
        if self.arch == "x86" {
            "32"
        } else {
            "64"
        }
    }
}

// ---------------------------------------------------------------------------
// manifest types
// ---------------------------------------------------------------------------

/// Mojang's `version_manifest_v2.json`.
#[derive(Debug, Clone, Deserialize)]
pub struct VersionManifest {
    /// The latest release and snapshot ids.
    #[serde(default)]
    pub latest: BTreeMap<String, String>,
    /// Every published version.
    pub versions: Vec<VersionEntry>,
}

impl VersionManifest {
    /// Finds a version by id.
    pub fn find(&self, id: &str) -> Option<&VersionEntry> {
        self.versions.iter().find(|v| v.id == id)
    }
}

/// One row of the version manifest.
#[derive(Debug, Clone, Deserialize)]
pub struct VersionEntry {
    /// Version id, e.g. `1.8.9`.
    pub id: String,
    /// `release`, `snapshot`, `old_beta`, `old_alpha`.
    #[serde(rename = "type")]
    pub kind: String,
    /// Where the version JSON lives.
    pub url: String,
    /// SHA-1 of the version JSON.
    #[serde(default)]
    pub sha1: Option<String>,
}

/// A version JSON — Mojang's, or a Legacy Fabric profile in the same format.
#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionJson {
    /// Version id.
    #[serde(default)]
    pub id: String,
    /// The version this one extends, for loader profiles.
    #[serde(default)]
    pub inherits_from: Option<String>,
    /// Entry point class.
    #[serde(default)]
    pub main_class: Option<String>,
    /// Asset index id, e.g. `1.8`.
    #[serde(default)]
    pub assets: Option<String>,
    /// Where to fetch the asset index.
    #[serde(default)]
    pub asset_index: Option<AssetIndexRef>,
    /// Client and server jars.
    #[serde(default)]
    pub downloads: BTreeMap<String, Artifact>,
    /// Libraries, in classpath order.
    #[serde(default)]
    pub libraries: Vec<Library>,
    /// The pre-1.13 flat argument template.
    #[serde(default)]
    pub minecraft_arguments: Option<String>,
    /// The 1.13+ structured arguments; Legacy Fabric profiles use this form.
    #[serde(default)]
    pub arguments: Option<Arguments>,
}

/// The 1.13+ argument form.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct Arguments {
    /// Game arguments; strings, or objects with rules we do not need for 1.8.9.
    #[serde(default)]
    pub game: Vec<serde_json::Value>,
    /// JVM arguments, same shape.
    #[serde(default)]
    pub jvm: Vec<serde_json::Value>,
}

impl Arguments {
    /// The plain string arguments, dropping the conditional objects.
    pub fn plain(values: &[serde_json::Value]) -> Vec<String> {
        values.iter().filter_map(|v| v.as_str().map(str::to_string)).collect()
    }
}

/// Reference to an asset index.
#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndexRef {
    /// Index id, e.g. `1.8`.
    pub id: String,
    /// SHA-1 of the index JSON.
    #[serde(default)]
    pub sha1: Option<String>,
    /// Size of the index JSON.
    #[serde(default)]
    pub size: Option<u64>,
    /// Where to fetch it.
    pub url: String,
}

/// A downloadable file named in a manifest.
#[derive(Debug, Clone, Deserialize)]
pub struct Artifact {
    /// Maven-relative path, for libraries.
    #[serde(default)]
    pub path: Option<String>,
    /// Expected SHA-1.
    #[serde(default)]
    pub sha1: Option<String>,
    /// Expected size in bytes.
    #[serde(default)]
    pub size: Option<u64>,
    /// Where to fetch it.
    pub url: String,
}

/// One library entry.
#[derive(Debug, Clone, Deserialize)]
pub struct Library {
    /// Maven coordinates, `group:artifact:version[:classifier]`.
    pub name: String,
    /// Mojang-style download descriptors.
    #[serde(default)]
    pub downloads: Option<LibraryDownloads>,
    /// Natives classifier per OS, e.g. `{"windows": "natives-windows"}`.
    #[serde(default)]
    pub natives: Option<BTreeMap<String, String>>,
    /// Rules deciding whether this library applies to the host.
    #[serde(default)]
    pub rules: Option<Vec<Rule>>,
    /// What to leave out when extracting a natives jar.
    #[serde(default)]
    pub extract: Option<Extract>,
    /// Maven repository base, used by Legacy Fabric libraries that carry no `downloads`.
    #[serde(default)]
    pub url: Option<String>,
}

/// Mojang-style download descriptors for a library.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct LibraryDownloads {
    /// The main jar.
    #[serde(default)]
    pub artifact: Option<Artifact>,
    /// Natives jars, keyed by classifier.
    #[serde(default)]
    pub classifiers: BTreeMap<String, Artifact>,
}

/// Extraction rules for a natives jar.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct Extract {
    /// Path prefixes to skip; always includes `META-INF/`.
    #[serde(default)]
    pub exclude: Vec<String>,
}

/// Whether a rule allows or disallows.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum RuleAction {
    /// The entry applies.
    Allow,
    /// The entry does not apply.
    Disallow,
}

/// One rule on a library or an argument.
#[derive(Debug, Clone, Deserialize)]
pub struct Rule {
    /// Allow or disallow when this rule matches.
    pub action: RuleAction,
    /// OS constraint.
    #[serde(default)]
    pub os: Option<OsRule>,
    /// Feature-flag constraint.
    #[serde(default)]
    pub features: Option<BTreeMap<String, bool>>,
}

/// The OS clause of a rule.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct OsRule {
    /// OS name: `windows`, `osx`, `linux`.
    #[serde(default)]
    pub name: Option<String>,
    /// A regular expression over the OS version.
    #[serde(default)]
    pub version: Option<String>,
    /// Architecture, e.g. `x86`.
    #[serde(default)]
    pub arch: Option<String>,
}

/// Whether a rule's clauses all match the host.
fn rule_matches(rule: &Rule, ctx: &RuleContext) -> bool {
    if let Some(os) = &rule.os {
        if let Some(name) = &os.name {
            if name != ctx.os.key() {
                return false;
            }
        }
        if let Some(arch) = &os.arch {
            if arch != &ctx.arch {
                return false;
            }
        }
        // `os.version` is a regex over the OS version string. 1.8.9 uses it on no
        // library and no argument (it appears only in the 1.13+ `jvm` block, which we do
        // not consume), so it is treated as matching rather than pulling in a regex
        // engine. Revisit if a version other than 1.8.9 is ever targeted.
        if os.version.is_some() {
            tracing::debug!(
                pattern = os.version.as_deref().unwrap_or_default(),
                "ignoring an os.version rule clause"
            );
        }
    }
    if let Some(features) = &rule.features {
        for (key, wanted) in features {
            if ctx.features.get(key).copied().unwrap_or(false) != *wanted {
                return false;
            }
        }
    }
    true
}

/// Mojang's rule algorithm: no rules means allowed; otherwise the last matching rule
/// wins, and an entry with rules none of which match is *not* allowed.
pub fn rules_allow(rules: Option<&Vec<Rule>>, ctx: &RuleContext) -> bool {
    let Some(rules) = rules else { return true };
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for rule in rules {
        if rule_matches(rule, ctx) {
            allowed = rule.action == RuleAction::Allow;
        }
    }
    allowed
}

// ---------------------------------------------------------------------------
// maven
// ---------------------------------------------------------------------------

/// Turns `group:artifact:version[:classifier][@ext]` into its Maven repository path.
///
/// ```
/// # use void_core::manifest::maven_to_path;
/// assert_eq!(
///     maven_to_path("org.lwjgl.lwjgl:lwjgl-platform:2.9.4:natives-windows").unwrap(),
///     "org/lwjgl/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-windows.jar"
/// );
/// ```
pub fn maven_to_path(name: &str) -> Result<String> {
    let (coords, ext) = match name.split_once('@') {
        Some((c, e)) => (c, e),
        None => (name, "jar"),
    };
    let parts: Vec<&str> = coords.split(':').collect();
    if parts.len() < 3 || parts.iter().any(|p| p.is_empty()) {
        return Err(Error::Manifest(format!("`{name}` is not Maven coordinates")));
    }
    let (group, artifact, version) = (parts[0], parts[1], parts[2]);
    let classifier = parts.get(3).map(|c| format!("-{c}")).unwrap_or_default();
    Ok(format!(
        "{}/{artifact}/{version}/{artifact}-{version}{classifier}.{ext}",
        group.replace('.', "/")
    ))
}

/// Makes a manifest id safe to use as a directory and file name.
///
/// Legacy Fabric publishes ids like `1.8.9-Legacy Fabric-0.16.0`; that string becomes a
/// natives directory and an argument-cache key, so the spaces have to go.
pub fn sanitize_id(id: &str) -> String {
    id.chars()
        .map(|c| if c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-') { c } else { '-' })
        .collect()
}

/// The natives classifier this library uses on this host, if any.
pub fn natives_classifier(lib: &Library, ctx: &RuleContext) -> Option<String> {
    let natives = lib.natives.as_ref()?;
    let raw = natives.get(ctx.os.key())?;
    Some(raw.replace("${arch}", ctx.natives_bits()))
}

// ---------------------------------------------------------------------------
// the resolved profile
// ---------------------------------------------------------------------------

/// One file to fetch: where it goes (relative to the installation root), where it comes
/// from, and what it must hash to.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct FileSpec {
    /// Destination, relative to [`crate::Paths::root`].
    pub relative_path: PathBuf,
    /// Source URL.
    pub url: String,
    /// Expected SHA-1, when the manifest publishes one.
    pub sha1: Option<String>,
    /// Expected size in bytes.
    pub size: Option<u64>,
}

/// A library resolved for this host.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ResolvedLibrary {
    /// Maven coordinates as written in the manifest.
    pub name: String,
    /// The file itself.
    pub file: FileSpec,
    /// Whether this jar holds natives to extract rather than classpath entries.
    pub natives: bool,
    /// Paths to skip when extracting, for a natives jar.
    pub exclude: Vec<String>,
}

impl ResolvedLibrary {
    /// `group:artifact` — the identity used to de-duplicate the classpath, so a loader's
    /// newer copy of a library wins over the vanilla one instead of both being present.
    pub fn coordinate_key(&self) -> String {
        let mut parts = self.name.split(':');
        format!("{}:{}", parts.next().unwrap_or(""), parts.next().unwrap_or(""))
    }
}

/// Everything a launch needs, merged from vanilla and loader manifests.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct LaunchProfile {
    /// The Minecraft version, e.g. `1.8.9`. This is what `${version_name}` becomes.
    pub version_id: String,
    /// The merged profile's own id, e.g. `1.8.9-legacyfabric-0.16.0`.
    pub profile_id: String,
    /// Entry point class.
    pub main_class: String,
    /// Asset index id, e.g. `1.8`.
    pub assets_index: String,
    /// The `${...}` game-argument template.
    pub minecraft_arguments: String,
    /// Extra JVM arguments the loader profile asked for.
    pub jvm_arguments: Vec<String>,
    /// Classpath entries, loader first, de-duplicated by `group:artifact`.
    pub libraries: Vec<ResolvedLibrary>,
    /// Natives jars to extract into the natives directory.
    pub natives: Vec<ResolvedLibrary>,
    /// The client jar.
    pub client: FileSpec,
    /// The asset index JSON.
    pub asset_index: FileSpec,
}

impl LaunchProfile {
    /// Every file the profile itself needs, excluding assets.
    pub fn files(&self) -> Vec<FileSpec> {
        let mut out = vec![self.client.clone(), self.asset_index.clone()];
        out.extend(self.libraries.iter().map(|l| l.file.clone()));
        out.extend(self.natives.iter().map(|l| l.file.clone()));
        out
    }

    /// A stable hash of everything that affects the JVM argument list.
    ///
    /// The launch argument cache is keyed on this (§12): the arguments only change when
    /// the profile does, and the per-spawn parts (port, token, session) are substituted
    /// afterwards.
    pub fn hash(&self) -> String {
        use sha1::{Digest, Sha1};
        let mut hasher = Sha1::new();
        hasher.update(serde_json::to_vec(self).unwrap_or_default());
        hex::encode(hasher.finalize())
    }
}

fn library_file(lib: &Library, classifier: Option<&str>) -> Result<FileSpec> {
    let coords = match classifier {
        Some(c) => format!("{}:{c}", lib.name),
        None => lib.name.clone(),
    };
    let maven = maven_to_path(&coords)?;
    let relative_path = PathBuf::from("libraries").join(&maven);

    // Mojang publishes `downloads`; Legacy Fabric publishes a repository `url` and
    // expects the Maven path to be appended to it.
    let artifact = lib.downloads.as_ref().and_then(|d| match classifier {
        Some(c) => d.classifiers.get(c),
        None => d.artifact.as_ref(),
    });
    if let Some(a) = artifact {
        return Ok(FileSpec {
            relative_path,
            url: a.url.clone(),
            sha1: a.sha1.clone(),
            size: a.size,
        });
    }
    let base = lib.url.clone().ok_or_else(|| {
        Error::Manifest(format!("library `{}` has neither downloads nor a repository url", lib.name))
    })?;
    Ok(FileSpec {
        relative_path,
        url: format!("{}/{maven}", base.trim_end_matches('/')),
        sha1: None,
        size: None,
    })
}

/// Merges a vanilla version JSON with an optional loader profile into a [`LaunchProfile`].
///
/// Loader libraries come first on the classpath and win ties by `group:artifact`, which
/// is how Legacy Fabric's newer ASM shadows the one 1.8.9 ships. `minecraftArguments`
/// comes from whichever manifest defines it, loader first.
pub fn resolve_profile(
    vanilla: &VersionJson,
    loader: Option<&VersionJson>,
    ctx: &RuleContext,
) -> Result<LaunchProfile> {
    let version_id = if vanilla.id.is_empty() { MC_VERSION.to_string() } else { vanilla.id.clone() };

    let main_class = loader
        .and_then(|l| l.main_class.clone())
        .or_else(|| vanilla.main_class.clone())
        .ok_or_else(|| Error::Manifest("no mainClass in either manifest".into()))?;

    let asset_index_ref = vanilla
        .asset_index
        .as_ref()
        .ok_or_else(|| Error::Manifest(format!("{version_id} has no assetIndex")))?;
    let assets_index =
        vanilla.assets.clone().unwrap_or_else(|| asset_index_ref.id.clone());

    let minecraft_arguments = loader
        .and_then(|l| l.minecraft_arguments.clone())
        .or_else(|| vanilla.minecraft_arguments.clone())
        .or_else(|| {
            let args = loader
                .and_then(|l| l.arguments.as_ref())
                .or(vanilla.arguments.as_ref())?;
            Some(Arguments::plain(&args.game).join(" "))
        })
        .ok_or_else(|| Error::Manifest("no game arguments in either manifest".into()))?;

    let mut jvm_arguments = Vec::new();
    for src in [loader.and_then(|l| l.arguments.as_ref()), vanilla.arguments.as_ref()]
        .into_iter()
        .flatten()
    {
        for arg in Arguments::plain(&src.jvm) {
            if !jvm_arguments.contains(&arg) {
                jvm_arguments.push(arg);
            }
        }
    }

    let mut libraries: Vec<ResolvedLibrary> = Vec::new();
    let mut natives: Vec<ResolvedLibrary> = Vec::new();
    let mut seen: Vec<String> = Vec::new();
    let mut seen_natives: Vec<String> = Vec::new();

    let ordered = loader
        .map(|l| l.libraries.iter())
        .unwrap_or_else(|| [].iter())
        .chain(vanilla.libraries.iter());

    for lib in ordered {
        if !rules_allow(lib.rules.as_ref(), ctx) {
            tracing::debug!(library = %lib.name, "skipped by rules");
            continue;
        }
        let classifier = natives_classifier(lib, ctx);

        // A library with a `natives` map contributes its classifier jar; on 1.8.9 the
        // LWJGL entries also carry a plain artifact, which stays on the classpath.
        if let Some(classifier) = classifier {
            match library_file(lib, Some(&classifier)) {
                Ok(file) => {
                    let mut exclude =
                        lib.extract.clone().unwrap_or_default().exclude;
                    if !exclude.iter().any(|e| e.starts_with("META-INF")) {
                        exclude.push("META-INF/".to_string());
                    }
                    let resolved = ResolvedLibrary {
                        name: format!("{}:{classifier}", lib.name),
                        file,
                        natives: true,
                        exclude,
                    };
                    // Legacy Fabric ships a patched `org.lwjgl.lwjgl:lwjgl-platform`
                    // alongside the vanilla one. Both would unpack into the same
                    // directory and the second would win, so natives are de-duplicated
                    // by coordinate exactly as the classpath is: loader first.
                    let key = resolved.coordinate_key();
                    if seen_natives.contains(&key) {
                        tracing::debug!(library = %lib.name, "natives shadowed by an earlier copy");
                    } else {
                        seen_natives.push(key);
                        natives.push(resolved);
                    }
                }
                Err(e) => tracing::warn!(library = %lib.name, error = %e, "no natives jar"),
            }
        }

        // A library that declares `natives` but publishes no explicit artifact is a
        // natives-only entry: `org.lwjgl.lwjgl:lwjgl-platform` has classifier jars in
        // Maven and no plain jar at all, so asking for one is a guaranteed 404.
        let has_artifact = match lib.downloads.as_ref() {
            Some(d) => d.artifact.is_some(),
            None => lib.url.is_some() && lib.natives.is_none(),
        };
        if !has_artifact {
            continue;
        }
        let resolved = ResolvedLibrary {
            name: lib.name.clone(),
            file: library_file(lib, None)?,
            natives: false,
            exclude: Vec::new(),
        };
        let key = resolved.coordinate_key();
        if seen.contains(&key) {
            tracing::debug!(library = %lib.name, "shadowed by an earlier copy");
            continue;
        }
        seen.push(key);
        libraries.push(resolved);
    }

    let client_artifact = vanilla
        .downloads
        .get("client")
        .ok_or_else(|| Error::Manifest(format!("{version_id} has no client download")))?;

    Ok(LaunchProfile {
        profile_id: sanitize_id(&match loader {
            Some(l) if !l.id.is_empty() => l.id.clone(),
            Some(_) => format!("{version_id}-legacyfabric"),
            None => version_id.clone(),
        }),
        client: FileSpec {
            relative_path: PathBuf::from("versions")
                .join(&version_id)
                .join(format!("{version_id}.jar")),
            url: client_artifact.url.clone(),
            sha1: client_artifact.sha1.clone(),
            size: client_artifact.size,
        },
        asset_index: FileSpec {
            relative_path: PathBuf::from("assets")
                .join("indexes")
                .join(format!("{assets_index}.json")),
            url: asset_index_ref.url.clone(),
            sha1: asset_index_ref.sha1.clone(),
            size: asset_index_ref.size,
        },
        version_id,
        main_class,
        assets_index,
        minecraft_arguments,
        jvm_arguments,
        libraries,
        natives,
    })
}

// ---------------------------------------------------------------------------
// assets
// ---------------------------------------------------------------------------

/// A parsed asset index.
#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndex {
    /// Every asset, keyed by its virtual path.
    pub objects: BTreeMap<String, AssetObject>,
}

/// One asset object.
#[derive(Debug, Clone, Deserialize)]
pub struct AssetObject {
    /// SHA-1 of the object, which is also its storage name.
    pub hash: String,
    /// Size in bytes.
    #[serde(default)]
    pub size: Option<u64>,
}

impl AssetIndex {
    /// Every asset as a file to fetch, in `assets/objects/<first two hex>/<hash>`.
    pub fn files(&self) -> Vec<FileSpec> {
        let mut out: Vec<FileSpec> = self
            .objects
            .values()
            .map(|o| {
                let prefix = &o.hash[..2.min(o.hash.len())];
                FileSpec {
                    relative_path: PathBuf::from("assets")
                        .join("objects")
                        .join(prefix)
                        .join(&o.hash),
                    url: format!("{RESOURCES_BASE}/{prefix}/{}", o.hash),
                    sha1: Some(o.hash.clone()),
                    size: o.size,
                }
            })
            .collect();
        out.sort_by(|a, b| a.relative_path.cmp(&b.relative_path));
        out.dedup_by(|a, b| a.relative_path == b.relative_path);
        out
    }
}

// ---------------------------------------------------------------------------
// network
// ---------------------------------------------------------------------------

/// One Legacy Fabric loader build for a game version.
#[derive(Debug, Clone, Deserialize)]
pub struct LoaderEntry {
    /// The loader itself.
    pub loader: MetaArtifact,
    /// The intermediary mappings the loader is paired with.
    pub intermediary: MetaArtifact,
}

/// A Maven artifact named by the Legacy Fabric meta server.
#[derive(Debug, Clone, Deserialize)]
pub struct MetaArtifact {
    /// Maven coordinates.
    pub maven: String,
    /// Version string.
    pub version: String,
    /// Whether this build is marked stable.
    #[serde(default)]
    pub stable: bool,
}

async fn get_json<T: serde::de::DeserializeOwned>(
    client: &reqwest::Client,
    url: &str,
) -> Result<T> {
    let resp = client.get(url).send().await?;
    let status = resp.status();
    if !status.is_success() {
        let detail = resp.text().await.ok().map(|t| t.chars().take(300).collect());
        return Err(Error::HttpStatus { url: url.to_string(), status: status.as_u16(), detail });
    }
    let text = resp.text().await?;
    serde_json::from_str(&text).map_err(|e| Error::json(url.to_string(), e))
}

/// Fetches Mojang's version manifest.
pub async fn fetch_version_manifest(client: &reqwest::Client) -> Result<VersionManifest> {
    get_json(client, VERSION_MANIFEST_URL).await
}

/// Fetches one version JSON by manifest entry.
pub async fn fetch_version_json(
    client: &reqwest::Client,
    entry: &VersionEntry,
) -> Result<VersionJson> {
    get_json(client, &entry.url).await
}

/// Lists Legacy Fabric loader builds for a game version, newest first.
pub async fn fetch_loader_versions(
    client: &reqwest::Client,
    game_version: &str,
) -> Result<Vec<LoaderEntry>> {
    get_json(client, &format!("{LEGACY_FABRIC_META}/versions/loader/{game_version}")).await
}

/// Fetches the Legacy Fabric launcher profile for one loader build.
pub async fn fetch_loader_profile(
    client: &reqwest::Client,
    game_version: &str,
    loader_version: &str,
) -> Result<VersionJson> {
    get_json(
        client,
        &format!("{LEGACY_FABRIC_META}/versions/loader/{game_version}/{loader_version}/profile/json"),
    )
    .await
}

/// Resolves 1.8.9 plus the newest stable Legacy Fabric loader into a launch profile.
///
/// `loader_version` pins a specific build; `None` takes the newest stable one, falling
/// back to the newest of any stability if the meta server lists none as stable.
pub async fn resolve_1_8_9(
    client: &reqwest::Client,
    ctx: &RuleContext,
    loader_version: Option<&str>,
) -> Result<LaunchProfile> {
    let manifest = fetch_version_manifest(client).await?;
    let entry = manifest
        .find(MC_VERSION)
        .ok_or_else(|| Error::Manifest(format!("{MC_VERSION} is not in the version manifest")))?;
    let vanilla = fetch_version_json(client, entry).await?;

    let loaders = fetch_loader_versions(client, MC_VERSION).await?;
    let chosen = match loader_version {
        Some(v) => loaders
            .iter()
            .find(|l| l.loader.version == v)
            .ok_or_else(|| Error::Manifest(format!("no Legacy Fabric loader {v} for {MC_VERSION}")))?,
        None => loaders
            .iter()
            .find(|l| l.loader.stable)
            .or_else(|| loaders.first())
            .ok_or_else(|| {
                Error::Manifest(format!("Legacy Fabric lists no loader for {MC_VERSION}"))
            })?,
    };
    tracing::info!(loader = %chosen.loader.version, intermediary = %chosen.intermediary.version, "legacy fabric");
    let profile = fetch_loader_profile(client, MC_VERSION, &chosen.loader.version).await?;
    resolve_profile(&vanilla, Some(&profile), ctx)
}

/// Fetches and parses an asset index that has already been downloaded to disk.
pub fn read_asset_index(path: &std::path::Path) -> Result<AssetIndex> {
    let text = std::fs::read_to_string(path).map_err(|e| Error::io(path, e))?;
    serde_json::from_str(&text).map_err(|e| Error::json(path.display().to_string(), e))
}
