//! The "download everything" step, joining [`crate::manifest`] and [`crate::download`].

use std::path::PathBuf;

use tokio::sync::mpsc;

use crate::download::{Downloader, Progress};
use crate::error::Result;
use crate::manifest::{self, LaunchProfile, RuleContext};
use crate::paths::Paths;

/// The one directory a resolved profile is cached in, for both the write and the read.
///
/// [`prepare`] writes `<versions>/<key>/<profile_id>.profile.json` and [`cached_profile`]
/// reads the same directory. They used to derive the key separately — `prepare` from
/// `profile.version_id`, callers of `cached_profile` from a literal `"1.8.9"` — and a
/// divergence there is silent: nothing fails, `launch` just re-resolves the manifests
/// from the network on every start instead of hitting the cache. One helper, so they
/// cannot drift; `profile_cache_dir_for` is the same function keyed off a resolved
/// profile, and `profiles_are_cached_where_they_are_looked_for` is the guard.
fn profile_cache_dir(paths: &Paths) -> PathBuf {
    paths.version_dir(manifest::MC_VERSION)
}

/// Where a resolved profile's JSON belongs. Must equal [`profile_cache_dir`].
///
/// The version *directory* is keyed on the vanilla version (`1.8.9`) because that is
/// also where the client jar lives; the merged Legacy Fabric id (`fabric-loader-…`) is
/// the *file* name inside it, so several loader versions can be cached side by side.
fn profile_cache_dir_for(paths: &Paths, profile: &LaunchProfile) -> PathBuf {
    paths.version_dir(&profile.version_id)
}

/// Which per-OS `void-client` mod JAR a launch needs.
///
/// The mod JAR embeds the Ultralight natives, and they are 21 MB **per platform** —
/// 77 MB for all three, against the ~25 MB PVP_ARCHITECTURE §13 budgeted (measured in
/// `mod/native/README.md`). So the mod is published one JAR per platform and the
/// launcher picks the one that matches the JVM it is about to spawn, which costs nothing
/// because it already resolves downloads per launch and already knows the target OS
/// (§12.3, §13).
///
/// **Which JVM, not which CPU.** On Apple Silicon 1.8.9 runs on an x64 JVM under Rosetta
/// (LWJGL 2 has no arm64 natives, §13), and the Ultralight binding is loaded *into that
/// JVM*, so an arm64 Mac takes [`ModPlatform::MacosX64`]. That is the whole reason this
/// is derived from the OS the JVM will run as, not from `std::env::consts::ARCH`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ModPlatform {
    /// `windows-x64`.
    WindowsX64,
    /// `macos-x64` — also what Apple Silicon gets, because the JVM is x64 (§13).
    MacosX64,
    /// `macos-arm64`. Not selected automatically today; built for a future arm64 JVM.
    MacosArm64,
    /// `linux-x64`. Built for the binding's own tests; not a shipping platform (§15).
    LinuxX64,
}

impl ModPlatform {
    /// Every platform the binding is built for, in `mod/native/README.md` order.
    pub const ALL: [ModPlatform; 4] = [
        ModPlatform::WindowsX64,
        ModPlatform::MacosX64,
        ModPlatform::MacosArm64,
        ModPlatform::LinuxX64,
    ];

    /// The `<os>-<arch>` key, which is both the natives directory inside the JAR
    /// (`natives/<key>/`) and the JAR's own classifier.
    pub fn key(self) -> &'static str {
        match self {
            ModPlatform::WindowsX64 => "windows-x64",
            ModPlatform::MacosX64 => "macos-x64",
            ModPlatform::MacosArm64 => "macos-arm64",
            ModPlatform::LinuxX64 => "linux-x64",
        }
    }

    /// Parses a `<os>-<arch>` key.
    pub fn parse(s: &str) -> Option<Self> {
        ModPlatform::ALL.into_iter().find(|p| p.key() == s)
    }

    /// The JAR file name for a mod version: `void-client-<version>-<os>-<arch>.jar`.
    ///
    /// The version-less form `void-client-<os>-<arch>.jar` names the same artifact on a
    /// release channel that already carries the version in its path.
    pub fn jar_name(self, mod_version: &str) -> String {
        format!("void-client-{mod_version}-{}.jar", self.key())
    }

    /// The platform whose JAR this host's *JVM* needs.
    pub fn for_host(ctx: &RuleContext) -> Result<Self> {
        Ok(match ctx.os {
            manifest::Os::Windows => ModPlatform::WindowsX64,
            // Rosetta: the game's JVM is x64 even on an arm64 Mac (§13).
            manifest::Os::Osx => ModPlatform::MacosX64,
            manifest::Os::Linux => ModPlatform::LinuxX64,
        })
    }
}

/// Resolves 1.8.9 + Legacy Fabric and downloads everything a launch needs.
///
/// Two passes, because the asset list only exists once the asset index has been fetched:
/// libraries, natives, the client jar and the index first, then every asset object.
///
/// **Offline.** When the manifests cannot be fetched (no network, Mojang or Legacy
/// Fabric down) a profile cached by an earlier `prepare` for the same loader is used
/// instead, and the downloads then find everything already on disk. A cold install with
/// no network still fails, naming the network error.
///
/// **Warm.** After a run that fetched and hashed every file, a stamp records the profile
/// hash; the next run with the same profile checks sizes instead of re-hashing ~3,000
/// files (see [`Downloader::trust_existing_sizes`]).
///
/// The mod JAR is the release channel's business ([`crate::release::ensure_mod_jar`]),
/// keyed off the same [`ModPlatform`].
pub async fn prepare(
    client: &reqwest::Client,
    paths: &Paths,
    ctx: &RuleContext,
    loader_version: Option<&str>,
    progress: Option<mpsc::Sender<Progress>>,
) -> Result<LaunchProfile> {
    let platform = ModPlatform::for_host(ctx)?;
    prepare_for(client, paths, ctx, platform, loader_version, progress).await
}

/// [`prepare`] for an explicit mod platform, rather than this host's.
///
/// Cross-preparing an installation for another OS is what the `--platform` flag of
/// `void-pvp prepare` exposes; it is also how a per-OS JAR is verified without three
/// machines.
pub async fn prepare_for(
    client: &reqwest::Client,
    paths: &Paths,
    ctx: &RuleContext,
    platform: ModPlatform,
    loader_version: Option<&str>,
    progress: Option<mpsc::Sender<Progress>>,
) -> Result<LaunchProfile> {
    paths.ensure()?;

    tracing::info!(platform = platform.key(), "mod JAR platform for this installation");

    let loader = loader_version.unwrap_or(manifest::LEGACY_FABRIC_LOADER);
    let profile = match manifest::resolve_1_8_9(client, ctx, Some(loader)).await {
        Ok(profile) => profile,
        Err(e) if e.is_network() => match cached_profile_for(paths, loader) {
            Some(cached) => {
                tracing::warn!(error = %e, profile = %cached.profile_id, "manifests unreachable; using the cached profile");
                cached
            }
            None => return Err(e),
        },
        Err(e) => return Err(e),
    };
    tracing::info!(
        libraries = profile.libraries.len(),
        natives = profile.natives.len(),
        main_class = %profile.main_class,
        "resolved {}",
        profile.profile_id
    );

    let warm = read_stamp(paths).as_deref() == Some(profile.hash().as_str());
    let downloader = Downloader::new(client.clone(), paths).trust_existing_sizes(warm);
    downloader.fetch_all(&profile.files(), progress.clone()).await?;

    let index_path = paths.root().join(&profile.asset_index.relative_path);
    let assets = manifest::read_asset_index(&index_path)?;
    let asset_files = assets.files();
    tracing::info!(objects = asset_files.len(), "fetching assets");
    downloader.fetch_all(&asset_files, progress).await?;

    save_profile(paths, &profile)?;
    if !warm {
        write_stamp(paths, &profile.hash());
    }

    Ok(profile)
}

/// `versions/1.8.9/verified.stamp`: the hash of the last profile whose every file was
/// fetched or hashed and found correct.
fn stamp_file(paths: &Paths) -> PathBuf {
    profile_cache_dir(paths).join("verified.stamp")
}

fn read_stamp(paths: &Paths) -> Option<String> {
    std::fs::read_to_string(stamp_file(paths)).ok().map(|s| s.trim().to_string())
}

fn write_stamp(paths: &Paths, hash: &str) {
    if let Err(e) = std::fs::write(stamp_file(paths), hash) {
        tracing::warn!(error = %e, "could not write the verified-install stamp");
    }
}

/// Writes the resolved profile next to the client jar, so a later run can see what was
/// installed without asking the network. Split out of [`prepare`] so the location is
/// stated in exactly one place, next to the read.
fn save_profile(paths: &Paths, profile: &LaunchProfile) -> Result<()> {
    let dir = profile_cache_dir_for(paths, profile);
    std::fs::create_dir_all(&dir).map_err(|e| crate::Error::io(&dir, e))?;
    let file = dir.join(format!("{}.profile.json", profile.profile_id));
    if let Ok(text) = serde_json::to_string_pretty(profile) {
        let _ = std::fs::write(&file, text);
    }
    Ok(())
}

/// Reads a profile written by a previous [`prepare`], so `launch` can skip the network.
///
/// Takes no version argument on purpose: the key is [`profile_cache_dir`], the same
/// helper [`save_profile`] uses, because a caller passing its own idea of the key is how
/// the cache silently stopped being hit.
pub fn cached_profile(paths: &Paths) -> Option<LaunchProfile> {
    let dir = profile_cache_dir(paths);
    let entries = std::fs::read_dir(dir).ok()?;
    let mut candidates: Vec<_> = entries
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.to_string_lossy().ends_with(".profile.json"))
        .collect();
    candidates.sort();
    for path in candidates.iter().rev() {
        if let Ok(text) = std::fs::read_to_string(path) {
            if let Ok(profile) = serde_json::from_str::<LaunchProfile>(&text) {
                return Some(profile);
            }
        }
    }
    None
}

/// The cached profile for one loader build, if a previous `prepare` saved one.
pub fn cached_profile_for(paths: &Paths, loader_version: &str) -> Option<LaunchProfile> {
    let dir = profile_cache_dir(paths);
    let entries = std::fs::read_dir(dir).ok()?;
    let needle = format!("-{loader_version}-");
    entries
        .flatten()
        .map(|e| e.path())
        .filter(|p| {
            p.file_name()
                .and_then(|n| n.to_str())
                .is_some_and(|n| n.ends_with(".profile.json") && n.contains(&needle))
        })
        .find_map(|p| serde_json::from_str::<LaunchProfile>(&std::fs::read_to_string(p).ok()?).ok())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::manifest::FileSpec;

    fn profile(version_id: &str, profile_id: &str) -> LaunchProfile {
        LaunchProfile {
            version_id: version_id.to_string(),
            profile_id: profile_id.to_string(),
            main_class: "net.fabricmc.loader.impl.launch.knot.KnotClient".into(),
            assets_index: "1.8".into(),
            minecraft_arguments: String::new(),
            jvm_arguments: Vec::new(),
            libraries: Vec::new(),
            natives: Vec::new(),
            client: FileSpec {
                relative_path: PathBuf::from("versions/1.8.9/1.8.9.jar"),
                url: String::new(),
                sha1: None,
                size: None,
            },
            asset_index: FileSpec {
                relative_path: PathBuf::from("assets/indexes/1.8.json"),
                url: String::new(),
                sha1: None,
                size: None,
            },
        }
    }

    /// The regression this exists for: `prepare` wrote the cache under one key and
    /// `cached_profile` read another, so `launch` re-resolved the manifests from the
    /// network on every start and nothing ever failed loudly.
    #[test]
    fn profiles_are_cached_where_they_are_looked_for() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        let written = profile(manifest::MC_VERSION, "fabric-loader-0.19.3-1.8.9");

        assert_eq!(
            profile_cache_dir_for(&paths, &written),
            profile_cache_dir(&paths),
            "the write key and the read key must be the same directory",
        );

        save_profile(&paths, &written).unwrap();
        let read = cached_profile(&paths).expect("a saved profile is found again");
        assert_eq!(read.profile_id, written.profile_id);
        assert_eq!(read.version_id, written.version_id);
    }

    /// The merged Legacy Fabric id names the *file*, never the directory, so two loader
    /// versions coexist and the newest-sorting one wins.
    #[test]
    fn several_loader_versions_share_one_version_directory() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        save_profile(&paths, &profile(manifest::MC_VERSION, "fabric-loader-0.16.14-1.8.9")).unwrap();
        save_profile(&paths, &profile(manifest::MC_VERSION, "fabric-loader-0.19.3-1.8.9")).unwrap();

        let found = cached_profile(&paths).expect("one of them is found");
        assert_eq!(found.profile_id, "fabric-loader-0.19.3-1.8.9");
    }

    #[test]
    fn the_offline_fallback_finds_the_profile_for_the_pinned_loader_only() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        save_profile(&paths, &profile(manifest::MC_VERSION, "fabric-loader-0.16.14-1.8.9")).unwrap();
        save_profile(&paths, &profile(manifest::MC_VERSION, "fabric-loader-0.19.3-1.8.9")).unwrap();
        assert_eq!(
            cached_profile_for(&paths, "0.16.14").unwrap().profile_id,
            "fabric-loader-0.16.14-1.8.9"
        );
        assert!(cached_profile_for(&paths, "0.15.0").is_none());
    }

    #[test]
    fn the_verified_stamp_round_trips() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        assert_eq!(read_stamp(&paths), None);
        std::fs::create_dir_all(profile_cache_dir(&paths)).unwrap();
        write_stamp(&paths, "abc");
        assert_eq!(read_stamp(&paths).as_deref(), Some("abc"));
    }

    #[test]
    fn an_empty_installation_has_no_cached_profile() {
        let dir = tempfile::tempdir().unwrap();
        assert!(cached_profile(&Paths::at(dir.path())).is_none());
    }

    #[test]
    fn mod_platform_keys_round_trip_and_name_a_jar() {
        for p in ModPlatform::ALL {
            assert_eq!(ModPlatform::parse(p.key()), Some(p));
            assert!(p.jar_name("0.1.0").starts_with("void-client-0.1.0-"));
            assert!(p.jar_name("0.1.0").ends_with(&format!("{}.jar", p.key())));
        }
        assert!(ModPlatform::parse("plan9-riscv").is_none());
    }

    /// §13: 1.8.9 runs on an x64 JVM under Rosetta, and Ultralight is loaded into that
    /// JVM — so an arm64 Mac takes the *x64* mod JAR. Getting this backwards would ship
    /// a JAR whose natives will not load.
    #[test]
    fn apple_silicon_takes_the_x64_mod_jar() {
        use crate::manifest::Os;
        assert_eq!(
            ModPlatform::for_host(&RuleContext::new(Os::Osx, "arm64")).unwrap(),
            ModPlatform::MacosX64,
        );
        assert_eq!(
            ModPlatform::for_host(&RuleContext::new(Os::Osx, "x86_64")).unwrap(),
            ModPlatform::MacosX64,
        );
        assert_eq!(
            ModPlatform::for_host(&RuleContext::new(Os::Windows, "x86_64")).unwrap(),
            ModPlatform::WindowsX64,
        );
    }
}
