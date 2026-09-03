//! The "download everything" step, joining [`crate::manifest`] and [`crate::download`].

use tokio::sync::mpsc;

use crate::download::{Downloader, Progress};
use crate::error::Result;
use crate::manifest::{self, LaunchProfile, RuleContext};
use crate::paths::Paths;

/// Resolves 1.8.9 + Legacy Fabric and downloads everything a launch needs.
///
/// Two passes, because the asset list only exists once the asset index has been fetched:
/// libraries, natives, the client jar and the index first, then every asset object.
pub async fn prepare(
    client: &reqwest::Client,
    paths: &Paths,
    ctx: &RuleContext,
    loader_version: Option<&str>,
    progress: Option<mpsc::Sender<Progress>>,
) -> Result<LaunchProfile> {
    paths.ensure()?;

    let profile = manifest::resolve_1_8_9(client, ctx, loader_version).await?;
    tracing::info!(
        libraries = profile.libraries.len(),
        natives = profile.natives.len(),
        main_class = %profile.main_class,
        "resolved {}",
        profile.profile_id
    );

    let downloader = Downloader::new(client.clone(), paths);
    downloader.fetch_all(&profile.files(), progress.clone()).await?;

    let index_path = paths.root().join(&profile.asset_index.relative_path);
    let assets = manifest::read_asset_index(&index_path)?;
    let asset_files = assets.files();
    tracing::info!(objects = asset_files.len(), "fetching assets");
    downloader.fetch_all(&asset_files, progress).await?;

    // The version JSON is kept next to the client jar so a later run can see what was
    // installed without asking the network.
    let version_dir = paths.version_dir(&profile.version_id);
    std::fs::create_dir_all(&version_dir)
        .map_err(|e| crate::Error::io(&version_dir, e))?;
    let profile_file = version_dir.join(format!("{}.profile.json", profile.profile_id));
    if let Ok(text) = serde_json::to_string_pretty(&profile) {
        let _ = std::fs::write(&profile_file, text);
    }

    Ok(profile)
}

/// Reads a profile written by a previous [`prepare`], so `launch` can skip the network.
pub fn cached_profile(paths: &Paths, version_id: &str) -> Option<LaunchProfile> {
    let dir = paths.version_dir(version_id);
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
